import {
  AppstoreOutlined,
  FileTextOutlined,
  MailOutlined,
  ShoppingCartOutlined,
} from '@ant-design/icons';
import { history, useAccess, useModel } from '@umijs/max';
import { Button } from 'antd';
import React from 'react';
import { Card, PageTitle } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import OpportunityCard from './OpportunityCard';

/**
 * 工作台卡片：每张卡片声明需要的菜单权限，没有权限不显示；卡片自己加载数据、失败只影响自己。
 * 后续卡片（待报价的询盘、待分配的询价、待确认的水单、本月成交）按同样方式加入。
 */
const CARDS: { key: string; access: string; render: () => React.ReactNode }[] =
  [
    {
      key: 'opportunity',
      access: 'crmOpportunityStats',
      render: () => <OpportunityCard />,
    },
  ];

/** 没有卡片时的常用入口，同样按权限过滤 */
const SHORTCUTS: {
  access: string;
  label: string;
  path: string;
  icon: React.ReactNode;
}[] = [
  {
    access: 'inquiryCustomerInquiry',
    label: '客户询盘',
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

const Dashboard: React.FC = () => {
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const { initialState } = useModel('@@initialState');
  const name = initialState?.currentUser?.name;
  const cards = CARDS.filter((c) => access[c.access]);
  const shortcuts = SHORTCUTS.filter((s) => access[s.access]);

  return (
    <div>
      <PageTitle
        root="工作台"
        crumbs={[]}
        title={name ? `${greeting()}，${name}` : '工作台'}
        description="按你的权限显示相关的业务数据。"
      />
      {cards.length > 0 ? (
        <div style={{ display: 'grid', gap: 16 }}>
          {cards.map((c) => (
            <React.Fragment key={c.key}>{c.render()}</React.Fragment>
          ))}
        </div>
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
