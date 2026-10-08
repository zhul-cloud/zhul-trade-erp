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
