import { TrendingDown, TrendingUp } from 'lucide-react';
import React, { useMemo } from 'react';
import { useAppTheme } from './AppTheme';
import type { Palette } from './palette';
import { type DeltaPref, useDeltaPref } from './store';

/**
 * 涨跌颜色（spec interface-style「涨跌颜色可选」）：只按方向着色，上涨与盈利同色、下跌与亏损同色；
 * 每人在主题设置里选涨红跌绿（默认）或涨绿跌红。逾期、低于红线等告警不用它，始终为错误色。
 * 零值和空值不算涨也不算跌，用正文色。
 */
export function deltaColors(p: Palette, pref: DeltaPref) {
  const up = pref === 'redUp' ? p.red : p.green;
  const down = pref === 'redUp' ? p.green : p.red;
  return {
    up,
    down,
    /** 按数值正负取色：正为涨色，负为跌色，零或空为正文色 */
    of: (v: number | null | undefined) =>
      v == null || v === 0 ? p.sub : v > 0 ? up : down,
  };
}

export const useDeltaColor = () => {
  const { palette } = useAppTheme();
  const pref = useDeltaPref();
  return useMemo(() => deltaColors(palette, pref), [palette, pref]);
};

/** 「较上期」这类变化：按方向着色并配上下箭头；没有变化时只显示文字 */
export const DeltaText: React.FC<{
  value: number | null | undefined;
  children?: React.ReactNode;
}> = ({ value, children }) => {
  const { of } = useDeltaColor();
  const Icon =
    value && value > 0 ? TrendingUp : value && value < 0 ? TrendingDown : null;
  return (
    <span
      className="num"
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        color: of(value),
      }}
    >
      {Icon && <Icon size={14} aria-hidden />}
      {children}
    </span>
  );
};
