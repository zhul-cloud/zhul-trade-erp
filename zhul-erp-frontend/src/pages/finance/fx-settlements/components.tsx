import React from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import type { ReceiptRow } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';

export const SettlePill: React.FC<{ status?: number }> = ({ status }) =>
  status === 3 ? (
    <Pill tone="green">已结汇</Pill>
  ) : status === 2 ? (
    <Pill tone="orange">部分结汇</Pill>
  ) : status === 1 ? (
    <Pill tone="orange">未结汇</Pill>
  ) : null;

/** 到账的 PI 与客户：未认领的显示付款人 */
export const ReceiptWho: React.FC<{ r: ReceiptRow }> = ({ r }) => {
  const { palette } = useAppTheme();
  return (
    <div>
      {r.piNo ? (
        <span style={{ color: palette.link }}>{r.piNo}</span>
      ) : (
        <span style={{ color: palette.mute }}>未认领</span>
      )}
      <div style={{ fontSize: 12, color: palette.sub }}>
        {r.customerName ?? r.payer ?? ''}
      </div>
    </div>
  );
};
