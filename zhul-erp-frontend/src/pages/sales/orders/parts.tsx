import React from 'react';
import type { Tone } from '@/pages/inquiry/shared/constants';
import { useAppTheme } from '@/theme/AppTheme';
import { Pill, RECEIPT_META } from '../components';

/** 订单状态（进度）的颜色：内置码与初始步骤有固定颜色，字典里新加的步骤用主题色 */
const PROGRESS_TONE: Record<string, Tone> = {
  PENDING_PURCHASE: 'gray',
  ORDERED: 'accent',
  RECEIVED: 'violet',
  TO_FORWARDER: 'cyan',
  SHIPPED: 'orange',
  COMPLETED: 'green',
  CANCELLED: 'mute',
};

export const ProgressPill: React.FC<{ code: string; name?: string }> = ({
  code,
  name,
}) => <Pill tone={PROGRESS_TONE[code] ?? 'accent'}>{name ?? code}</Pill>;

/** 收款进度条：已到账（毛额）÷ 合计，最多显示 100%，多收时标注 */
export const ReceiptProgress: React.FC<{
  received: number;
  total: number;
  status?: number;
  width?: number;
}> = ({ received, total, status, width = 110 }) => {
  const { palette } = useAppTheme();
  const ratio = total > 0 ? received / total : 0;
  const pct = Math.min(100, Math.round(ratio * 100));
  const meta = status ? RECEIPT_META[status] : undefined;
  const color =
    status === 4 ? palette.green : status === 2 ? palette.orange : palette.link;
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <div
          role="progressbar"
          aria-valuenow={pct}
          aria-valuemin={0}
          aria-valuemax={100}
          style={{
            width,
            height: 6,
            borderRadius: 3,
            background: palette.hairline,
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              width: `${pct}%`,
              height: '100%',
              borderRadius: 3,
              background: color,
              transition: 'width .3s',
            }}
          />
        </div>
        <span
          style={{
            fontSize: 12,
            color: palette.sub,
            fontVariantNumeric: 'tabular-nums',
          }}
        >
          {pct}%
        </span>
      </div>
      <div style={{ marginTop: 4, display: 'flex', gap: 6 }}>
        {meta && <Pill tone={meta.tone}>{meta.label}</Pill>}
        {ratio > 1 && <Pill tone="orange">多收</Pill>}
      </div>
    </div>
  );
};

// ---------------------------------------------------------------- 货物状态

/** 已出运 / 已交货代 / 在仓 / 在途 / 待发货 / 待采购 的颜色 */
export const useGoodsColors = () => {
  const { palette } = useAppTheme();
  return {
    shipped: palette.green,
    handed: palette.cyan,
    inWarehouse: palette.violet,
    inTransit: palette.link,
    pendingShip: palette.orange,
    pendingPurchase: palette.faint,
  };
};

export const GOODS_LABELS = {
  shipped: '已出运',
  handed: '已交货代',
  inWarehouse: '在仓',
  inTransit: '在途',
  pendingShip: '待发货',
  pendingPurchase: '待采购',
} as const;

type GoodsKey = keyof typeof GOODS_LABELS;

/** 分段进度条：按件数显示货走到了哪一步 */
export const GoodsBar: React.FC<{
  parts: Record<GoodsKey, number>;
  total: number;
  width?: number;
}> = ({ parts, total, width = 160 }) => {
  const { palette } = useAppTheme();
  const colors = useGoodsColors();
  const keys = Object.keys(GOODS_LABELS) as GoodsKey[];
  return (
    <div
      role="img"
      aria-label={keys.map((k) => `${GOODS_LABELS[k]} ${parts[k]}`).join('、')}
      style={{
        display: 'flex',
        width,
        height: 6,
        borderRadius: 3,
        overflow: 'hidden',
        background: palette.hairline,
      }}
    >
      {total > 0 &&
        keys.map((k) =>
          parts[k] > 0 ? (
            <div
              key={k}
              style={{
                width: `${(parts[k] / total) * 100}%`,
                background: colors[k],
              }}
            />
          ) : null,
        )}
    </div>
  );
};

/** 「已出运 10 · 在仓 2 · 在途 3」（为 0 的段不显示） */
export const goodsText = (parts: Record<GoodsKey, number>) =>
  (Object.keys(GOODS_LABELS) as GoodsKey[])
    .filter((k) => parts[k] > 0)
    .map((k) => `${GOODS_LABELS[k]} ${parts[k]}`)
    .join(' · ');
