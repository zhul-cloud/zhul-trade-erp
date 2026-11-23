import { request } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Tooltip } from 'antd';
import dayjs from 'dayjs';
import React, { useEffect, useSyncExternalStore } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { onAuthChange } from '@/utils/tokenStore';

/** 系统写入（定时任务、导入、回调）的创建人、更新人 */
const SYSTEM = new Set(['', 'sys', 'system', 'anonymousUser']);

/** 账号 → 姓名；null 为查过但查不到 */
const names = new Map<string, string | null>();
/** 本轮要查的、正在查的、上次没查成的（暂时显示账号，下次挂载再查） */
const pending = new Set<string>();
const inflight = new Set<string>();
const failed = new Set<string>();
const listeners = new Set<() => void>();
let version = 0;
let scheduled = false;
/** 换了登录（可能换了租户）后，旧请求的结果作废 */
let epoch = 0;

const notify = () => {
  version += 1;
  for (const l of listeners) l();
};

onAuthChange(() => {
  epoch += 1;
  names.clear();
  pending.clear();
  inflight.clear();
  failed.clear();
  notify();
});

/** 同一轮渲染里出现的账号攒在一起查一次（每次最多 200 个） */
const flush = async () => {
  scheduled = false;
  const mine = epoch;
  const all = Array.from(pending);
  pending.clear();
  for (const u of all) inflight.add(u);
  for (let i = 0; i < all.length; i += 200) {
    const batch = all.slice(i, i + 200);
    try {
      const res = await request('/api/v1/system/users/names', {
        method: 'GET',
        params: { usernames: batch.join(',') },
        skipErrorHandler: true,
      });
      if (mine !== epoch) return;
      const data: Record<string, string> = res?.data ?? {};
      for (const u of batch) {
        names.set(u, data[u] ?? null);
        failed.delete(u);
      }
    } catch {
      if (mine !== epoch) return;
      // 网络或服务出错：先显示账号，不记成「查不到」，下次挂载再查
      for (const u of batch) failed.add(u);
    }
    for (const u of batch) inflight.delete(u);
  }
  notify();
};

const want = (u: string) => {
  if (names.has(u) || pending.has(u) || inflight.has(u)) return;
  pending.add(u);
  if (!scheduled) {
    scheduled = true;
    setTimeout(flush, 0);
  }
};

const subscribe = (l: () => void) => {
  listeners.add(l);
  return () => {
    listeners.delete(l);
  };
};

/**
 * 人名（公共组件，ui-redesign C7、D10、S9）：创建人、更新人存的是登录账号，这里显示姓名；
 * 系统写入显示「系统」，查不到姓名时显示账号。
 */
export const useUserName = (username?: string | null): string => {
  const v = useSyncExternalStore(
    subscribe,
    () => version,
    () => version,
  );
  const u = (username ?? '').trim();
  const system = SYSTEM.has(u);
  // 缓存变了（换登录后清空）也要再登记一次；已查到、正在查的会直接跳过
  useEffect(() => {
    if (!system) want(u);
  }, [u, system, v]);
  if (system) return '系统';
  const name = names.get(u);
  if (name === undefined) return failed.has(u) ? u : '';
  return name ?? u;
};

export const UserName: React.FC<{ value?: string | null }> = ({ value }) => (
  <>{useUserName(value)}</>
);

/** 短时间：当年「11-21 10:20」，跨年「2025-11-21」 */
export const formatShortTime = (val?: string | null): string => {
  if (!val) return '—';
  const d = dayjs(val);
  return d.year() === dayjs().year()
    ? d.format('MM-DD HH:mm')
    : d.format('YYYY-MM-DD');
};

/** 「创建」「更新」单元格：人名在上（13 号正文色，「系统」次要色）、短时间在下，悬停看「姓名 · 完整时间」（同原型 G1） */
export const AuditCell: React.FC<{
  by?: string | null;
  at?: string | null;
}> = ({ by, at }) => {
  const { palette } = useAppTheme();
  const name = useUserName(by);
  return (
    <Tooltip
      title={
        name || at
          ? [name, at && formatDateTime(at)].filter(Boolean).join(' · ')
          : undefined
      }
    >
      <div style={{ lineHeight: '20px', minWidth: 0 }}>
        <div
          style={{
            fontSize: 13,
            color: name === '系统' ? palette.mute : palette.sub,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {name || ' '}
        </div>
        <div className="num" style={{ fontSize: 12, color: palette.mute }}>
          {formatShortTime(at)}
        </div>
      </div>
    </Tooltip>
  );
};

/**
 * 列表固定的「创建」「更新」两列（ui-redesign D25，规范「列表页统一规范」第 2 条）：
 * 紧邻业务字段之后、操作列之前；需要按时间排序的页面自己给列加 sorter。
 */
export const auditColumns = <
  T extends {
    createTime?: string | null;
    createBy?: string | null;
    updateTime?: string | null;
    updateBy?: string | null;
  },
>(): TableColumnsType<T> => [
  {
    title: '创建',
    key: 'create',
    width: 120,
    render: (_: unknown, r: T) => (
      <AuditCell by={r.createBy} at={r.createTime} />
    ),
  },
  {
    title: '更新',
    key: 'update',
    width: 120,
    render: (_: unknown, r: T) => (
      <AuditCell by={r.updateBy} at={r.updateTime} />
    ),
  },
];
