import { history } from '@umijs/max';
import { Skeleton } from 'antd';
import React, { useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import type { Tone } from '@/pages/inquiry/shared/constants';
import { useAppTheme } from '@/theme/AppTheme';
import { type Chain, type ChainNode, chainApi } from './service';

export { Card, Pill };

export const PI_STATUS = {
  DRAFT: 1,
  SENT: 2,
  CONVERTED: 3,
  VOID: 4,
} as const;

export const PI_STATUS_META: Record<number, { label: string; tone: Tone }> = {
  1: { label: '草稿', tone: 'gray' },
  2: { label: '已发送', tone: 'accent' },
  3: { label: '已转订单', tone: 'green' },
  4: { label: '已作废', tone: 'mute' },
};

export const RECEIPT_STATUS = {
  NONE: 1,
  SLIP_ONLY: 2,
  PARTIAL: 3,
  PAID: 4,
} as const;

export const RECEIPT_META: Record<number, { label: string; tone: Tone }> = {
  1: { label: '未付款', tone: 'gray' },
  2: { label: '待到账', tone: 'orange' },
  3: { label: '部分到账', tone: 'accent' },
  4: { label: '已到账', tone: 'green' },
};

export const ORDER_META: Record<number, { label: string; tone: Tone }> = {
  1: { label: '有效', tone: 'green' },
  2: { label: '已取消', tone: 'mute' },
};

export const KIND = { SLIP: 1, RECEIPT: 2 } as const;
export const DISCOUNT = { NONE: 0, PERCENT: 1, AMOUNT: 2 } as const;
export const CHANNEL = { EXCEL: 2, PDF: 3, IMAGE: 4 } as const;

export const PATHS = {
  piList: '/sales/pi',
  pi: (id: number) => `/sales/pi/${id}`,
  orderList: '/sales/orders',
  order: (id: number) => `/sales/orders/${id}`,
  quotation: (id: number) => `/quotation/quotations/${id}`,
  inquiry: (id: number) => `/inquiry/customer-inquiries/${id}`,
};

const StatusPill: React.FC<{
  meta: Record<number, { label: string; tone: Tone }>;
  status?: number;
}> = ({ meta, status }) => {
  const m = (status != null && meta[status]) || {
    label: '—',
    tone: 'gray' as const,
  };
  return (
    <Pill tone={m.tone} dot>
      {m.label}
    </Pill>
  );
};

export const PiStatusPill: React.FC<{ status: number }> = ({ status }) => (
  <StatusPill meta={PI_STATUS_META} status={status} />
);

export const ReceiptStatusPill: React.FC<{ status?: number }> = ({
  status,
}) => <StatusPill meta={RECEIPT_META} status={status} />;

export const OrderStatusPill: React.FC<{ status: number }> = ({ status }) => (
  <StatusPill meta={ORDER_META} status={status} />
);

/** 面包屑「业务管理 / …」（按页面路径取分组） */
export const SalesPageTitle: React.FC<
  Omit<React.ComponentProps<typeof PageTitle>, 'root'>
> = (props) => <PageTitle {...props} />;

/** 买方 / 收货人的一行地址 */
export const partyAddress = (p?: {
  address?: string;
  city?: string;
  state?: string;
  postcode?: string;
  country?: string;
}) => {
  if (!p) return '';
  const parts = [p.address];
  for (const x of [p.city, p.state, p.postcode, p.country]) {
    if (x && !(p.address ?? '').toLowerCase().includes(x.toLowerCase())) {
      parts.push(x);
    }
  }
  return parts.filter(Boolean).join(', ');
};

// ---------------------------------------------------------------- 来源 / 去向链路

const STAGES: {
  key: keyof Chain;
  label: string;
  path: (id: number) => string;
}[] = [
  { key: 'inquiries', label: '客户询盘', path: PATHS.inquiry },
  { key: 'quotations', label: '报价单', path: PATHS.quotation },
  { key: 'pis', label: 'PI', path: PATHS.pi },
  { key: 'orders', label: '销售订单', path: PATHS.order },
];

/** 客户询盘 → 报价单 → PI → 销售订单：当前单据高亮，其余可点击跳转 */
export const ChainCard: React.FC<{
  type: 'inquiry' | 'quotation' | 'pi' | 'order';
  id: number;
  /** 变化时重新加载（如转订单、取消订单之后） */
  reloadKey?: unknown;
}> = ({ type, id, reloadKey }) => {
  const { palette } = useAppTheme();
  const [chain, setChain] = useState<Chain>();
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
    chainApi
      .get(type, id)
      .then(setChain)
      .catch(() => setFailed(true));
  }, [type, id, reloadKey]);

  if (failed) return null;
  const node = (n: ChainNode, path: (id: number) => string) => (
    <button
      type="button"
      key={n.id}
      onClick={() => !n.current && history.push(path(n.id))}
      style={{
        all: 'unset',
        cursor: n.current ? 'default' : 'pointer',
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        padding: '4px 10px',
        borderRadius: 8,
        fontSize: 13,
        background: n.current ? palette.accentSoft : palette.inset,
        border: `1px solid ${n.current ? palette.accentLine : palette.hairline}`,
        color: n.current ? palette.ink : palette.link,
        fontWeight: n.current ? 600 : 400,
      }}
    >
      <span style={{ fontVariantNumeric: 'tabular-nums' }}>{n.no}</span>
      <span style={{ fontSize: 12, color: palette.mute }}>{n.statusName}</span>
    </button>
  );
  return (
    <Card style={{ padding: '14px 20px', marginBottom: 16 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 10,
        }}
      >
        <b style={{ color: palette.ink }}>来源 / 去向</b>
        <span style={{ fontSize: 12, color: palette.mute }}>
          型号从询盘到订单经过的单据，点击可跳转
        </span>
      </div>
      {!chain ? (
        <Skeleton active paragraph={{ rows: 1 }} title={false} />
      ) : (
        <div
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          {STAGES.map((s, i) => (
            <React.Fragment key={s.key}>
              {i > 0 && (
                <span style={{ color: palette.mute, paddingTop: 22 }}>→</span>
              )}
              <div style={{ display: 'grid', gap: 6, minWidth: 120 }}>
                <span style={{ fontSize: 12, color: palette.sub }}>
                  {s.label}
                </span>
                {chain[s.key].length === 0 ? (
                  <span
                    style={{
                      fontSize: 13,
                      color: palette.mute,
                      padding: '4px 0',
                    }}
                  >
                    —
                  </span>
                ) : (
                  chain[s.key].map((n) => node(n, s.path))
                )}
              </div>
            </React.Fragment>
          ))}
        </div>
      )}
    </Card>
  );
};
