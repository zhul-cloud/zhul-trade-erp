import {
  ArrowDownRight,
  ArrowUpRight,
  ChevronRight,
  TriangleAlert,
} from 'lucide-react';
import React, { useEffect, useRef, useState } from 'react';
import FitText from '@/components/FitText';
import { useAppTheme } from '@/theme/AppTheme';
import { useDeltaColor } from '@/theme/delta';
import {
  CARD_SHADOW,
  CARD_SHADOW_HOVER,
  METRIC_FAMILY,
  type MetricFamily,
} from '@/theme/palette';

/** 数字卡高 144 = 内边距 40 + 名称行 38 + 8 + 数值 30 + 4 + 第三行 24（公共组件原型 G1） */
export const METRIC_CARD_HEIGHT = 144;

/** 较上期：value 为变化量（正为涨），text 为不带正负号的数（如「9.6%」「0.8 个百分点」），符号按 value 加 */
export interface MetricDelta {
  value: number;
  text: string;
  /** 默认「较上月」 */
  suffix?: string;
}

/** 涨跌胶囊：方向箭头 + 数字在胶囊里，「较上月」在外面；颜色按个人设置的涨跌色，零为中性 */
export const DeltaPill: React.FC<MetricDelta> = ({
  value,
  text,
  suffix = '较上月',
}) => {
  const { palette } = useAppTheme();
  const { of } = useDeltaColor();
  const color = of(value);
  const soft =
    color === palette.red
      ? palette.redSoft
      : color === palette.green
        ? palette.greenSoft
        : palette.neutralSoft;
  const Icon = value > 0 ? ArrowUpRight : value < 0 ? ArrowDownRight : null;
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
      <span
        className="num"
        style={{
          height: 20,
          display: 'inline-flex',
          alignItems: 'center',
          gap: 2,
          padding: '0 8px',
          borderRadius: 10,
          background: soft,
          color,
          fontSize: 12,
          fontWeight: 600,
        }}
      >
        {Icon && <Icon size={12} aria-hidden />}
        {value > 0 ? '+' : value < 0 ? '−' : ''}
        {text}
      </span>
      <span style={{ fontSize: 12, color: palette.mute }}>{suffix}</span>
    </span>
  );
};

/** 告警标签：状态浅底 + 警告三角 + 文字（数字卡第三行、字段级提醒） */
export const AlertLabel: React.FC<{
  tone: 'error' | 'warning';
  children: React.ReactNode;
}> = ({ tone, children }) => {
  const { palette } = useAppTheme();
  const [color, soft] =
    tone === 'error'
      ? [palette.red, palette.redSoft]
      : [palette.orange, palette.orangeSoft];
  return (
    <span
      className="num"
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        maxWidth: '100%',
        padding: '2px 8px',
        borderRadius: 4,
        background: soft,
        color,
        fontSize: 12,
        whiteSpace: 'nowrap',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
      }}
    >
      <TriangleAlert size={12} aria-hidden style={{ flex: 'none' }} />
      {children}
    </span>
  );
};

/**
 * 数字卡（公共组件，规范「组件标准 · 数字卡」，ui-redesign C3、D1–D3）：浮起白卡（浅色阴影、深色边框），高 144；
 * 左上 38 图标块按指标家族着色、告警时改状态色，名称 14 次要色（可点时带右箭头）；数值 24/600，币种代码弱化；
 * 第三行三选一：告警标签、涨跌胶囊、说明。可点的卡悬停加深阴影，不位移。
 */
export const MetricCard: React.FC<{
  label: string;
  /** 数值；金额传数字串（已按两位小数格式化）并传 currency */
  value: React.ReactNode;
  currency?: string;
  icon: React.ReactNode;
  family: MetricFamily;
  delta?: MetricDelta;
  alert?: { tone: 'error' | 'warning'; text: React.ReactNode };
  hint?: React.ReactNode;
  /** 0–1 的比例：第三行右侧画一条细进度 */
  bar?: number;
  onClick?: () => void;
}> = ({
  label,
  value,
  currency,
  icon,
  family,
  delta,
  alert,
  hint,
  bar,
  onClick,
}) => {
  const { palette, mode } = useAppTheme();
  const [hover, setHover] = useState(false);
  const light = mode === 'light';
  // 卡宽不到 180（窄屏、栏少）时用紧凑版：内边距 16、图标块 32、名称 13，卡高不变
  const ref = useRef<HTMLElement | null>(null);
  const [compact, setCompact] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (!el || typeof ResizeObserver === 'undefined') return undefined;
    const ro = new ResizeObserver(() => setCompact(el.clientWidth < 180));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  const block = compact ? 32 : 38;
  const [iconColor, iconSoft] = alert
    ? alert.tone === 'error'
      ? [palette.red, palette.redSoft]
      : [palette.orange, palette.orangeSoft]
    : METRIC_FAMILY[mode][family];
  const body = (
    <>
      <span style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <span
          aria-hidden
          style={{
            width: block,
            height: block,
            flex: 'none',
            borderRadius: 8,
            display: 'grid',
            placeItems: 'center',
            background: iconSoft,
            color: iconColor,
          }}
        >
          {icon}
        </span>
        <span
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: hover ? 4 : 2,
            minWidth: 0,
            fontSize: compact ? 13 : 14,
            color: palette.mute,
            transition: 'gap .2s',
          }}
        >
          <span
            title={label}
            style={{
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {label}
          </span>
          {onClick && (
            <ChevronRight
              size={14}
              aria-hidden
              style={{
                flex: 'none',
                color: hover ? palette.ink : palette.mute,
              }}
            />
          )}
        </span>
      </span>
      {/* 卡片被拉高（和旁边的图表卡等高）时，多出的高度留在名称和数值之间，数值和对比贴底（R21 方案 B，D59） */}
      <span aria-hidden style={{ flex: 1, minHeight: 8 }} />
      {/* 卡片变窄时数值自动缩小字号，不折行、不截断 */}
      <FitText
        size={24}
        min={16}
        style={{
          fontWeight: 600,
          lineHeight: '30px',
          color: palette.ink,
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {currency && (
          <span
            style={{
              fontSize: '0.58em',
              color: palette.mute,
              marginInlineEnd: 4,
            }}
          >
            {currency}
          </span>
        )}
        {value}
      </FitText>
      <span
        style={{
          marginTop: 4,
          height: 24,
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          minWidth: 0,
        }}
      >
        {alert ? (
          <AlertLabel tone={alert.tone}>{alert.text}</AlertLabel>
        ) : delta ? (
          <DeltaPill {...delta} />
        ) : hint ? (
          <span
            className="num"
            style={{
              fontSize: 12,
              color: palette.mute,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {hint}
          </span>
        ) : null}
        {bar !== undefined && (
          <span
            aria-hidden
            style={{
              flex: 1,
              height: 4,
              borderRadius: 2,
              background: palette.neutralSoft,
              overflow: 'hidden',
            }}
          >
            <span
              style={{
                display: 'block',
                height: '100%',
                width: `${Math.max(0, Math.min(1, bar)) * 100}%`,
                borderRadius: 2,
                // 进度跟主题色（图表色），不按指标家族固定蓝
                background: palette.chart,
              }}
            />
          </span>
        )}
      </span>
    </>
  );
  const style: React.CSSProperties = {
    height: METRIC_CARD_HEIGHT,
    minWidth: 0,
    display: 'flex',
    flexDirection: 'column',
    padding: compact ? 16 : 20,
    borderRadius: 10,
    // 深色没有阴影：悬停时底色、边框提亮一档（原型 G1）
    background: !light && hover ? palette.hover : palette.card,
    border: light
      ? 'none'
      : `1px solid ${hover ? palette.elevatedLine : palette.hairline}`,
    boxShadow: light ? (hover ? CARD_SHADOW_HOVER : CARD_SHADOW) : 'none',
    transition: 'box-shadow .2s, background .2s, border-color .2s',
    textAlign: 'left',
    font: 'inherit',
  };
  return onClick ? (
    <button
      ref={(el) => {
        ref.current = el;
      }}
      type="button"
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{ ...style, cursor: 'pointer' }}
    >
      {body}
    </button>
  ) : (
    <div
      ref={(el) => {
        ref.current = el;
      }}
      style={style}
    >
      {body}
    </div>
  );
};

/**
 * 一组卡片每排几张：每排最多 cols 张，放不下（每张不到 min）时减少，并按行均分（6 张放不下一排时排成 3+3，
 * 不剩单张一行）。MetricGrid 和销售统计漏斗共用；返回挂到网格容器上的 ref 和每排张数。
 * ref 是回调形式：容器晚于组件出现（如数据加载后才渲染）时也能挂上尺寸监听。
 */
export const useBalancedCols = (
  cols: number,
  count: number,
  min = 150,
  gap = 16,
) => {
  const [el, ref] = useState<HTMLDivElement | null>(null);
  const [fit, setFit] = useState(cols);
  useEffect(() => {
    if (!el || typeof ResizeObserver === 'undefined') return undefined;
    const ro = new ResizeObserver(() =>
      setFit(
        Math.max(
          1,
          Math.min(cols, Math.floor((el.clientWidth + gap) / (min + gap))),
        ),
      ),
    );
    ro.observe(el);
    return () => ro.disconnect();
  }, [el, cols, min, gap]);
  const perRow = Math.min(fit, cols);
  // 按行均分：先算要几行，再平均每行张数
  const balanced =
    count > perRow ? Math.ceil(count / Math.ceil(count / perRow)) : perRow;
  return { ref, balanced };
};

/**
 * 一组数字卡：透明分组（不再外包白卡），每排固定 cols 张、卡间距 16，同一行的组件高度不因换行而变；
 * 只有放不下（每张不到 min，默认 150）时才减少每排张数，并按行均分（`useBalancedCols`）
 * （窄屏单列模式下高度随内容）。
 */
export const MetricGrid: React.FC<{
  cols: number;
  /** 每张的最小宽度，默认 150 */
  min?: number;
  /** 卡间距，默认 16 */
  gap?: number;
  style?: React.CSSProperties;
  children: React.ReactNode;
}> = ({ cols, min = 150, gap = 16, style, children }) => {
  const count = React.Children.toArray(children).length;
  const { ref, balanced } = useBalancedCols(cols, count, min, gap);
  return (
    <div
      ref={ref}
      style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${balanced}, minmax(0, 1fr))`,
        gap,
        ...style,
      }}
    >
      {children}
    </div>
  );
};
