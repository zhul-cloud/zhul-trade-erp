import React from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { CA_REASONS } from './costingService';

/** 带正负号的人民币金额：增加为绿色、减少为红色、0 显示「—」 */
export const SignedMoney: React.FC<{ value?: number; yuan?: boolean }> = ({
  value,
  yuan,
}) => {
  const { palette } = useAppTheme();
  const v = Number(value ?? 0);
  if (v === 0) return <span style={{ color: palette.mute }}>—</span>;
  const text = formatAmount(Math.abs(v), 'CNY').replace(/^CNY\s/, '');
  return (
    <span
      style={{
        color: v > 0 ? palette.green : palette.red,
        fontVariantNumeric: 'tabular-nums',
      }}
    >
      {v > 0 ? '+' : '−'}
      {yuan ? '¥' : ''}
      {text}
    </span>
  );
};

/** 成本调整的触发原因 */
export const ReasonPill: React.FC<{ reason: number; name?: string }> = ({
  reason,
  name,
}) => {
  const r = CA_REASONS.find((x) => x.value === reason);
  return <Pill tone={r?.tone ?? 'gray'}>{name ?? r?.label ?? '—'}</Pill>;
};
