import React from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import type { Tone } from '@/pages/inquiry/shared/constants';

export { Card, Pill };

export const STATUS = {
  DRAFT: 1,
  SENT: 2,
  WON: 3,
  LOST: 4,
  VOID: 5,
  PARTIAL: 6,
} as const;

export const STATUS_META: Record<number, { label: string; tone: Tone }> = {
  1: { label: '草稿', tone: 'gray' },
  2: { label: '已发送', tone: 'accent' },
  3: { label: '已成交', tone: 'green' },
  4: { label: '未成交', tone: 'orange' },
  5: { label: '已作废', tone: 'mute' },
  6: { label: '部分成交', tone: 'green' },
};

export const CHANNEL = { TEXT: 1, EXCEL: 2, PDF: 3, IMAGE: 4 } as const;

export const CURRENCIES = ['USD', 'EUR', 'GBP', 'JPY', 'CNY'];

export const PATHS = {
  list: '/quotation/quotations',
  detail: (id: number) => `/quotation/quotations/${id}`,
  pricing: '/quotation/pricing',
};

export const QuotationStatusPill: React.FC<{ status: number }> = ({
  status,
}) => {
  const m = STATUS_META[status] ?? { label: '未知', tone: 'gray' as const };
  return (
    <Pill tone={m.tone} dot>
      {m.label}
    </Pill>
  );
};

/** 面包屑「报价中心 / …」 */
export const QuotationPageTitle: React.FC<
  Omit<React.ComponentProps<typeof PageTitle>, 'root'>
> = (props) => <PageTitle root="报价中心" {...props} />;
