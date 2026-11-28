import { Drawer, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import {
  MODE_AUTO,
  type ReleaseBrief,
  readBizError,
  type Site,
  siteApi,
  TAKEOVER_DONE,
} from './service';

type Tone = React.ComponentProps<typeof Pill>['tone'];

/** 网址、单号、代码等用等宽字体 */
export const MONO = "'SFMono-Regular', Menlo, Consolas, monospace";

/** 发布单状态：1-排队中、2-执行中、3-已完成、4-失败、5-部署失败 */
export const RELEASE_TONE: Record<number, Tone> = {
  1: 'gray',
  2: 'cyan',
  3: 'green',
  4: 'red',
  5: 'orange',
};

export const ModePill: React.FC<{ mode: number }> = ({ mode }) =>
  mode === MODE_AUTO ? (
    <Pill tone="accent">自动发布</Pill>
  ) : (
    <Pill tone="gray">只复制</Pill>
  );

export const TakeoverPill: React.FC<{ site: Site }> = ({ site }) =>
  site.takeoverStatus === TAKEOVER_DONE ? (
    <Pill tone="green">以 ERP 为准</Pill>
  ) : (
    <Pill tone="orange">接管中</Pill>
  );

export const StatusPill: React.FC<{ status: number }> = ({ status }) =>
  status === 1 ? <Pill tone="green">启用</Pill> : <Pill tone="mute">停用</Pill>;

/** 站点的发布单记录 */
export const ReleasesDrawer: React.FC<{
  site?: Site;
  onClose: () => void;
}> = ({ site, onClose }) => {
  const { palette } = useAppTheme();
  const [rows, setRows] = useState<ReleaseBrief[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const siteId = site?.id;
  const load = useCallback(() => {
    if (!siteId) return;
    setLoading(true);
    setError(undefined);
    siteApi
      .releases(siteId)
      .then(setRows)
      .catch((e) => setError(readBizError(e).message))
      .finally(() => setLoading(false));
  }, [siteId]);
  useEffect(load, [load]);
  return (
    <Drawer
      open={!!site}
      onClose={onClose}
      size={720}
      title={site ? `发布记录 · ${site.name}` : '发布记录'}
      destroyOnHidden
    >
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Table<ReleaseBrief>
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={rows}
          pagination={false}
          locale={{ emptyText: '还没有发布过' }}
          columns={[
            {
              title: '发布单',
              dataIndex: 'releaseNo',
              render: (v: string) => (
                <span style={{ fontFamily: MONO }}>{v}</span>
              ),
            },
            {
              title: '状态',
              dataIndex: 'status',
              width: 110,
              render: (_, r) => (
                <Pill tone={RELEASE_TONE[r.status] ?? 'gray'}>
                  {r.statusName}
                </Pill>
              ),
            },
            { title: '商品数', dataIndex: 'itemCount', width: 80 },
            {
              title: '发起',
              key: 'by',
              width: 220,
              render: (_, r) => (
                <span style={{ color: palette.sub }}>
                  {r.createBy} · {formatDateTime(r.createTime)}
                </span>
              ),
            },
            {
              title: '失败原因',
              dataIndex: 'error',
              render: (v?: string) =>
                v ? <span style={{ color: palette.red }}>{v}</span> : '—',
            },
          ]}
        />
      )}
    </Drawer>
  );
};
