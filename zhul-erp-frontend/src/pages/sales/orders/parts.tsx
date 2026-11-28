import React from 'react';
import type { Tone } from '@/pages/inquiry/shared/constants';
import { useAppTheme } from '@/theme/AppTheme';
import { seriesFor } from '@/theme/palette';
import { Pill, RECEIPT_META } from '../components';

/**
 * 订单状态（进度）的颜色（status-colors.md）：待采购、已取消为中性，进行中的各步为信息，已完成为成功；
 * 字典里新加的步骤也是进行中的一步，用信息色。
 */
const PROGRESS_TONE: Record<string, Tone> = {
  PENDING_PURCHASE: 'gray',
  COMPLETED: 'green',
  CANCELLED: 'mute',
};

export const ProgressPill: React.FC<{ code: string; name?: string }> = ({
  code,
  name,
}) => <Pill tone={PROGRESS_TONE[code] ?? 'info'}>{name ?? code}</Pill>;

/** 收款进度条：已到账（毛额）÷ 合计，最多显示 100%，多收时标注 */
export const ReceiptProgress: React.FC<{
  received: number;
  total: number;
  status?: number;
  width?: number | string;
  /** 卡头已显示收款状态时不再重复（详情页收款卡，ui-redesign R8-2） */
  hideStatus?: boolean;
}> = ({ received, total, status, width = 110, hideStatus }) => {
  const { palette } = useAppTheme();
  const ratio = total > 0 ? received / total : 0;
  const pct = Math.min(100, Math.round(ratio * 100));
  const meta = status ? RECEIPT_META[status] : undefined;
  // 进度只用主题色（D48），收齐、逾期等状态由下面的状态标签表达，不再换绿、橙
  const color = palette.chart;
  return (
    // 进度条一行定高 16、与下面的状态标签不留间距：合计 40，列表行高落在两行的 60–64（D21）
    <div>
      <div
        style={{ display: 'flex', alignItems: 'center', gap: 8, height: 16 }}
      >
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
      <div style={{ display: 'flex', gap: 6 }}>
        {meta && !hideStatus && <Pill tone={meta.tone}>{meta.label}</Pill>}
        {ratio > 1 && <Pill tone="orange">多收</Pill>}
      </div>
    </div>
  );
};

// ---------------------------------------------------------------- 货物状态

/**
 * 已出运 / 已交货代 / 在仓 / 在途 / 待发货 / 待采购 的颜色：6 段相邻，用图表系列色（status-colors.md「货物进度条」，
 * 2026-11-20 用户确认），不随主题色变化；2026-11-24 换成参考模板原色并按含义重排（R14）
 */
export const useGoodsColors = () => {
  const { mode, palette } = useAppTheme();
  const s = seriesFor(mode);
  // 按含义取色（R14）：已出运绿、交货代青、在仓蓝、在途紫、待发货警告橙（还没发）、待采购中性灰
  return {
    shipped: s[4],
    handed: s[2],
    inWarehouse: s[0],
    inTransit: s[3],
    pendingShip: palette.orangeMain,
    pendingPurchase: s[5],
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
  /** 撑满所在弹性容器的剩余宽度（列表单元格里与件数并排时用，件数变长进度条自动变短，不折行） */
  fill?: boolean;
}> = ({ parts, total, width = 160, fill }) => {
  const { palette } = useAppTheme();
  const colors = useGoodsColors();
  const keys = Object.keys(GOODS_LABELS) as GoodsKey[];
  return (
    <div
      role="img"
      aria-label={keys.map((k) => `${GOODS_LABELS[k]} ${parts[k]}`).join('、')}
      style={{
        display: 'flex',
        ...(fill ? { flex: '1 1 0', minWidth: 40 } : { width }),
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

/** 货物各段件数（列表「货物」列悬停卡）：圆点颜色同进度条，为 0 的段不显示 */
export const GoodsLegend: React.FC<{
  parts: Record<GoodsKey, number>;
  total: number;
}> = ({ parts, total }) => {
  const { palette } = useAppTheme();
  const colors = useGoodsColors();
  return (
    <div style={{ display: 'grid', gap: 6, minWidth: 180, fontSize: 12 }}>
      <div style={{ fontWeight: 600, color: palette.ink }}>货物 {total} 件</div>
      {(Object.keys(GOODS_LABELS) as GoodsKey[])
        .filter((k) => parts[k] > 0)
        .map((k) => (
          <div
            key={k}
            style={{ display: 'flex', alignItems: 'center', gap: 6 }}
          >
            <span
              aria-hidden
              style={{
                width: 6,
                height: 6,
                borderRadius: 3,
                background: colors[k],
              }}
            />
            <span style={{ flex: 1, color: palette.sub }}>
              {GOODS_LABELS[k]}
            </span>
            <span
              style={{ color: palette.ink, fontVariantNumeric: 'tabular-nums' }}
            >
              {parts[k]} 件
            </span>
          </div>
        ))}
    </div>
  );
};
