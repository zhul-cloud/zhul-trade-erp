import {
  AppstoreOutlined,
  FileTextOutlined,
  MailOutlined,
  ShoppingCartOutlined,
} from '@ant-design/icons';
import { history, useAccess, useModel } from '@umijs/max';
import { Button, Segmented, Skeleton } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { PageHeader } from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { readBizError } from '@/pages/crm/opportunity/service';
import { Card, useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { MenuName } from '@/utils/menuName';
import { BoardContext, layoutRows, rowMinHeight } from './layout';
import { type Workbench, workbenchApi } from './service';
import { WIDGETS } from './widgets';

/** 没有卡片时的常用入口，同样按权限过滤；按钮文字取菜单名称 */
const SHORTCUTS: {
  access: string;
  path: string;
  icon: React.ReactNode;
}[] = [
  {
    access: 'inquiryCustomerInquiry',
    path: '/inquiry/customer-inquiries',
    icon: <MailOutlined />,
  },
  {
    access: 'quotationList',
    path: '/quotation/quotations',
    icon: <FileTextOutlined />,
  },
  {
    access: 'inquiryMyTasks',
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

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  const board = bench?.boards.find((b) => b.code === code) ?? bench?.boards[0];
  const widgets = (board?.widgets ?? []).filter((w) => WIDGETS[w.key]);
  const keys = new Set(widgets.map((w) => w.key));

  return (
    <div>
      <PageHeader
        title={name ? `${greeting()}，${name}` : undefined}
        extra={
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
                borderRadius: 8,
                background: palette.inset,
                color: palette.mute,
                fontSize: 13,
              }}
            >
              {board.name}看板正在建设中，先显示通用卡片。
            </div>
          )}
          {/* 按行排列、同一行等高（规范「工作台」，ui-redesign D18）；行与行之间 24，同行组件之间 16 */}
          <BoardContext.Provider value={keys}>
            <div style={{ display: 'grid', gap: 24 }}>
              {layoutRows(widgets).map((row) => (
                <div
                  key={row.map((w) => w.key).join(',')}
                  style={{
                    display: 'grid',
                    gridTemplateColumns: wide
                      ? 'repeat(12, minmax(0, 1fr))'
                      : 'minmax(0, 1fr)',
                    gap: wide ? 16 : 24,
                    alignItems: 'stretch',
                    minHeight: wide ? rowMinHeight(row) : undefined,
                  }}
                >
                  {row.map((w) => (
                    <div
                      key={w.key}
                      style={{
                        minWidth: 0,
                        gridColumn: wide ? `span ${w.span}` : undefined,
                      }}
                    >
                      {React.createElement(WIDGETS[w.key])}
                    </div>
                  ))}
                </div>
              ))}
            </div>
          </BoardContext.Provider>
        </>
      ) : (
        <Card style={{ padding: '48px 24px', textAlign: 'center' }}>
          <span
            style={{
              width: 48,
              height: 48,
              borderRadius: 14,
              background: palette.primarySoft,
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
                  <MenuName path={s.path} />
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
