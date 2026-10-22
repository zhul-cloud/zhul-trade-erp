import {
  AppstoreOutlined,
  FileTextOutlined,
  MailOutlined,
  ShoppingCartOutlined,
} from '@ant-design/icons';
import { history, useAccess, useModel } from '@umijs/max';
import { Button, Segmented, Skeleton } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { readBizError } from '@/pages/crm/opportunity/service';
import { Card, PageTitle, useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { type Workbench, workbenchApi } from './service';
import { WIDGETS } from './widgets';

/** 没有卡片时的常用入口，同样按权限过滤 */
const SHORTCUTS: {
  access: string;
  label: string;
  path: string;
  icon: React.ReactNode;
}[] = [
  {
    access: 'inquiryCustomerInquiry',
    label: '询盘单',
    path: '/inquiry/customer-inquiries',
    icon: <MailOutlined />,
  },
  {
    access: 'quotationList',
    label: '报价单',
    path: '/quotation/quotations',
    icon: <FileTextOutlined />,
  },
  {
    access: 'inquiryMyTasks',
    label: '我的询价',
    path: '/inquiry/my-tasks',
    icon: <ShoppingCartOutlined />,
  },
];

const greeting = () => {
  const h = new Date().getHours();
  if (h < 6) return '夜深了';
  if (h < 12) return '早上好';
  if (h < 18) return '下午好';
  return '晚上好';
};

/** 记住上次选的看板（按账号，存在浏览器本地；读不到时用默认看板） */
const boardKey = (user?: string) => `zhul:workbench:${user ?? ''}`;
const readBoard = (user?: string) => {
  try {
    return localStorage.getItem(boardKey(user)) ?? undefined;
  } catch {
    return undefined;
  }
};
const saveBoard = (user: string | undefined, code: string) => {
  try {
    localStorage.setItem(boardKey(user), code);
  } catch {
    // 无痕模式等读写不了本地存储时只是不记住
  }
};

/**
 * 工作台：按角色的工作台模板显示岗位看板（spec system/workbench），一人对应多个看板时顶部切换，
 * 默认职级最高的；看板里的组件按编码从注册表取，各自加载数据、失败只影响自己。
 */
const Dashboard: React.FC = () => {
  const { palette } = useAppTheme();
  const wide = useWide();
  const access = useAccess() as Record<string, boolean>;
  const { initialState } = useModel('@@initialState');
  const name = initialState?.currentUser?.name;
  const user = initialState?.currentUser?.userid ?? name;
  const shortcuts = SHORTCUTS.filter((s) => access[s.access]);
  const [bench, setBench] = useState<Workbench>();
  const [error, setError] = useState('');
  const [code, setCode] = useState<string>();

  const load = useCallback(async () => {
    setError('');
    try {
      const b = await workbenchApi.current();
      setBench(b);
      const saved = readBoard(user);
      setCode(b.boards.some((x) => x.code === saved) ? saved : b.defaultCode);
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [user]);

  useEffect(() => {
    load();
  }, [load]);

  const board = bench?.boards.find((b) => b.code === code) ?? bench?.boards[0];
  const widgets = (board?.widgets ?? []).filter((w) => WIDGETS[w.key]);

  return (
    <div>
      <PageTitle
        root="工作台"
        crumbs={[]}
        title={name ? `${greeting()}，${name}` : '工作台'}
        description={
          board && board.code !== 'general' && !board.fallback
            ? `${board.name}看板：按你的数据范围显示，点击数字进入对应列表处理。`
            : '按你的权限显示相关的业务数据。'
        }
        actions={
          bench && bench.boards.length > 1 ? (
            <Segmented<string>
              value={board?.code}
              onChange={(c) => {
                setCode(c);
                saveBoard(user, c);
              }}
              options={bench.boards.map((b) => ({
                value: b.code,
                label: b.name,
              }))}
            />
          ) : undefined
        }
      />
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : !bench ? (
        <Card style={{ padding: 20 }}>
          <Skeleton active paragraph={{ rows: 6 }} />
        </Card>
      ) : widgets.length > 0 ? (
        <>
          {board?.fallback && (
            <div
              style={{
                marginBottom: 16,
                padding: '10px 14px',
                borderRadius: 12,
                background: palette.inset,
                color: palette.mute,
                fontSize: 13,
              }}
            >
              {board.name}看板正在建设中，先显示通用卡片。
            </div>
          )}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: wide
                ? 'repeat(2, minmax(0, 1fr))'
                : 'minmax(0, 1fr)',
              gap: 16,
              alignItems: 'start',
            }}
          >
            {widgets.map((w) => {
              const Widget = WIDGETS[w.key];
              return (
                <div
                  key={w.key}
                  style={
                    w.size === 'full' ? { gridColumn: '1 / -1' } : undefined
                  }
                >
                  <Widget />
                </div>
              );
            })}
          </div>
        </>
      ) : (
        <Card style={{ padding: '48px 24px', textAlign: 'center' }}>
          <span
            style={{
              width: 48,
              height: 48,
              borderRadius: 14,
              background: palette.accentSoft,
              color: palette.link,
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 22,
            }}
          >
            <AppstoreOutlined />
          </span>
          <div
            style={{
              marginTop: 12,
              fontSize: 16,
              fontWeight: 700,
              color: palette.ink,
            }}
          >
            工作台暂时没有你的卡片
          </div>
          <div style={{ marginTop: 8, color: palette.mute, fontSize: 13 }}>
            工作台按权限显示业务数据，你目前的权限还没有对应的卡片
            {shortcuts.length > 0 ? '；可以从下面的常用入口开始。' : '。'}
          </div>
          {shortcuts.length > 0 && (
            <div
              style={{
                display: 'flex',
                gap: 10,
                justifyContent: 'center',
                marginTop: 20,
              }}
            >
              {shortcuts.map((s) => (
                <Button
                  key={s.path}
                  icon={s.icon}
                  onClick={() => history.push(s.path)}
                >
                  {s.label}
                </Button>
              ))}
            </div>
          )}
        </Card>
      )}
    </div>
  );
};

export default Dashboard;
