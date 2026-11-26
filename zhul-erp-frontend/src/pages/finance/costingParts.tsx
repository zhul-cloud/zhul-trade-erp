import React from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { useDeltaColor } from '@/theme/delta';
import { formatAmount } from '@/utils/format';
import { CA_REASONS } from './costingService';

/** 带正负号的人民币金额：按个人涨跌色设置着色（增加为涨色、减少为跌色，2026-11-26 用户确认，待定 E），0 显示「—」 */
export const SignedMoney: React.FC<{ value?: number; yuan?: boolean }> = ({
  value,
  yuan,
}) => {
  const { palette } = useAppTheme();
  const { of: deltaOf } = useDeltaColor();
  const v = Number(value ?? 0);
  if (v === 0) return <span style={{ color: palette.mute }}>—</span>;
  const text = formatAmount(Math.abs(v), 'CNY').replace(/^CNY\s/, '');
  return (
    <span
      style={{
        color: deltaOf(v),
        fontVariantNumeric: 'tabular-nums',
      }}
    >
      {yuan ? 'CNY ' : ''}
      {v > 0 ? '+' : '−'}
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
