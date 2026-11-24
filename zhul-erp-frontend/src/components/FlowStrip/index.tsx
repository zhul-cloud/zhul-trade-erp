import { ChevronRight, Repeat } from 'lucide-react';
import React, { useState } from 'react';
import { CardTitle } from '@/components/ListLayout';
import { useBalancedCols } from '@/components/MetricCard';
import { useAppTheme } from '@/theme/AppTheme';
import { CARD_SHADOW, CARD_SHADOW_HOVER } from '@/theme/palette';

export interface FlowStripItem {
  key: string;
  name: string;
  icon: React.ReactNode;
  /** 第几步；今日待办这类入口卡不写 */
  step?: number;
  /** 数字；不传表示未上线，显示「—」和「即将上线」 */
  value?: React.ReactNode;
  /** 数字后的单位，如「位客户」 */
  unit?: string;
  /** 数字右侧的告警标签，如红色「逾期 3」 */
  badge?: React.ReactNode;
  /** 第三行：分组计数等，一行放不下省略 */
  sub?: string;
}

const GAP = 20;
const PAD = 12;
const TILE = 24;
/**
 * 卡片最小宽度：首行「图标 + 名称 + 第 n 步」放得下，名称不省略。
 * 内边距 2×12 + 图标 24 + 两处间距 2×6 + 「第 n 步」约 37 = 97，加名称每字 14
 */
const minWidth = (items: FlowStripItem[]) =>
  2 * PAD + TILE + 12 + 37 + 14 * Math.max(...items.map((i) => i.name.length));

const StripCard: React.FC<{
  it: FlowStripItem;
  active: boolean;
  /** 卡片左侧的间距里画箭头 */
  arrow: boolean;
  onSelect: () => void;
}> = ({ it, active, arrow, onSelect }) => {
  const { palette, mode } = useAppTheme();
  const [hover, setHover] = useState(false);
  const light = mode === 'light';
  const open = it.value !== undefined;
  return (
    <button
      type="button"
      disabled={!open}
      aria-pressed={open ? active : undefined}
      title={open ? undefined : '即将上线'}
      onClick={onSelect}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        position: 'relative',
        boxSizing: 'border-box',
        minWidth: 0,
        display: 'grid',
        alignContent: 'start',
        gap: 6,
        padding: PAD,
        border: light ? 0 : `1px solid ${palette.hairline}`,
        borderRadius: 10,
        background: palette.card,
        // 当前查看的卡：主题色内描边（内阴影），不填色；不占用 outline，键盘焦点框照常显示
        boxShadow:
          [
            active && `inset 0 0 0 2px ${palette.primary}`,
            light && (hover && open ? CARD_SHADOW_HOVER : CARD_SHADOW),
          ]
            .filter(Boolean)
            .join(', ') || 'none',
        cursor: open ? 'pointer' : 'not-allowed',
        textAlign: 'left',
        font: 'inherit',
        color: palette.ink,
        transition: 'box-shadow .2s',
      }}
    >
      {arrow && (
        <ChevronRight
          aria-hidden
          size={14}
          style={{
            position: 'absolute',
            left: -GAP / 2 - 7,
            top: '50%',
            transform: 'translateY(-50%)',
            color: palette.faint,
          }}
        />
      )}
      <span
        style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0 }}
      >
        <span
          aria-hidden
          style={{
            width: TILE,
            height: TILE,
            flex: 'none',
            borderRadius: 6,
            display: 'grid',
            placeItems: 'center',
            fontSize: 13,
            background: active ? palette.primarySoft : palette.neutralSoft,
            color: active ? palette.link : palette.sub,
          }}
        >
          {it.icon}
        </span>
        <span
          style={{
            fontSize: 14,
            fontWeight: 600,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {it.name}
        </span>
        {it.step !== undefined && (
          <span
            style={{
              marginLeft: 'auto',
              flex: 'none',
              fontSize: 12,
              color: palette.mute,
              whiteSpace: 'nowrap',
            }}
          >
            第 {it.step} 步
          </span>
        )}
      </span>
      <span
        // 卡片窄、标签多时标签换到下一行，不越出卡片
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'baseline',
          columnGap: 4,
          minWidth: 0,
        }}
      >
        <span
          className="num"
          style={{
            fontSize: 24,
            fontWeight: 700,
            lineHeight: '30px',
            color: open ? palette.ink : palette.faint,
          }}
        >
          {open ? it.value : '—'}
        </span>
        {open && it.unit && (
          <span
            style={{ fontSize: 12, color: palette.mute, whiteSpace: 'nowrap' }}
          >
            {it.unit}
          </span>
        )}
        {open && it.badge && (
          <span
            style={{ marginLeft: 'auto', flex: 'none', alignSelf: 'center' }}
          >
            {it.badge}
          </span>
        )}
      </span>
      <span
        title={open ? it.sub : undefined}
        style={{
          fontSize: 12,
          lineHeight: '18px',
          minHeight: 18,
          color: palette.mute,
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
        }}
      >
        {open ? it.sub : '即将上线'}
      </span>
    </button>
  );
};

/**
 * 流程卡片条（客户跟进、供应链跟进的顶部链路，对比图 R10 方案 B）：标题行右侧写回环（如「复购回到第 2 步」），
 * 下面一张入口卡（今日待办）加各步骤卡，等宽排一行；放不下时按行均分（与 MetricGrid、销售漏斗同一算法），不横向滚动。
 * 卡片都是白卡，步骤靠「第 n 步」、图标和箭头区分，不靠颜色；当前查看的卡主题色描边、图标块主题色浅底。
 */
export const FlowStrip: React.FC<{
  title: string;
  /** 标题行右侧的回环说明 */
  loop?: string;
  /** 入口卡（今日待办） */
  lead: FlowStripItem;
  steps: FlowStripItem[];
  active: string;
  onSelect: (key: string) => void;
}> = ({ title, loop, lead, steps, active, onSelect }) => {
  const { palette } = useAppTheme();
  const all = [lead, ...steps];
  const { ref, balanced } = useBalancedCols(
    all.length,
    all.length,
    minWidth(all),
    GAP,
  );
  return (
    <section style={{ marginBottom: 16 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 12,
        }}
      >
        <CardTitle>{title}</CardTitle>
        {loop && (
          <span
            style={{
              marginLeft: 'auto',
              display: 'inline-flex',
              alignItems: 'center',
              gap: 4,
              fontSize: 12,
              color: palette.mute,
            }}
          >
            <Repeat size={13} aria-hidden /> {loop}
          </span>
        )}
      </div>
      <div
        ref={ref}
        style={{
          display: 'grid',
          gridTemplateColumns: `repeat(${balanced}, minmax(0, 1fr))`,
          gap: GAP,
        }}
      >
        {all.map((it, i) => (
          <StripCard
            key={it.key}
            it={it}
            active={it.key === active}
            // 步骤之间画箭头：入口卡与第 1 步之间、每行第一张不画
            arrow={i >= 2 && i % balanced !== 0}
            onSelect={() => onSelect(it.key)}
          />
        ))}
      </div>
    </section>
  );
};
