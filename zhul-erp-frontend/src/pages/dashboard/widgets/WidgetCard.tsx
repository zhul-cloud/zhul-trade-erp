import { Button, Skeleton } from 'antd';
import { ChevronRight, RotateCw } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { EmptyState } from '@/components/EmptyState';
import FitText from '@/components/FitText';
import { CardTitle, HelpTip, Panel } from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { readBizError } from '@/pages/crm/opportunity/service';
import { useAppTheme } from '@/theme/AppTheme';
import { CHART_SERIES } from '@/theme/palette';
import { workbenchApi } from '../service';

/**
 * 图标底块的色调（规范「图标底块」：12% 浅底配同色图标）。primary 为主题色；blue 是固定的蓝（图表系列色第一个），
 * 不随主题色变化——默认琥珀主题下主题色与警告色相近，「蓝」要和它们分得开（工作台原型 D1）。
 * 后端待办传的 tone 沿用 blue / green / orange / red / violet。
 */
export type Tone =
  | 'primary'
  | 'blue'
  | 'green'
  | 'orange'
  | 'red'
  | 'violet'
  | 'gray';

const BLUE = CHART_SERIES[0];

/** 色调 → [主色, 浅底] */
export const useTone = () => {
  const { palette, mode } = useAppTheme();
  return (tone: Tone): [string, string] =>
    (
      ({
        primary: [palette.link, palette.primarySoft],
        // 浅底：浅色 12%、深色 16%，同其他语义色
        blue: [BLUE, `${BLUE}${mode === 'light' ? '1F' : '29'}`],
        green: [palette.green, palette.greenSoft],
        orange: [palette.orange, palette.orangeSoft],
        red: [palette.red, palette.redSoft],
        violet: [palette.info, palette.infoSoft],
        gray: [palette.sub, palette.neutralSoft],
      }) as Record<Tone, [string, string]>
    )[tone];
};

/** 错误、警告色只在告警时用：数量为 0 时改中性（规范「图标底块」） */
export const alertTone = (tone: Tone, count?: number | null) =>
  (tone === 'red' || tone === 'orange') && !count ? 'gray' : tone;

export const IconChip: React.FC<{
  icon: React.ReactNode;
  tone: Tone;
  size?: number;
  round?: boolean;
}> = ({ icon, tone, size = 32, round }) => {
  const [color, soft] = useTone()(tone);
  return (
    <span
      style={{
        width: size,
        height: size,
        borderRadius: round ? '50%' : 8,
        background: soft,
        color,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        flex: 'none',
      }}
    >
      {icon}
    </span>
  );
};

/** 看板组件的数据：自己加载，失败只影响自己 */
export function useWidgetData<T>(key: string) {
  const [data, setData] = useState<T>();
  const [error, setError] = useState('');
  const load = useCallback(async () => {
    setError('');
    setData(undefined);
    try {
      setData(await workbenchApi.widget<T>(key));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [key]);
  useEffect(() => {
    load();
  }, [load]);
  // 从其他页面返回时重新取数（audit UI-055）
  useOnReactivate(load);
  return { data, error, reload: load };
}

/**
 * 看板组件的外框（工作台原型 H1、ui-redesign D33）：标题行在卡外的页面底上（16/600，旁边是统计期间和口径帮助图标，
 * 右侧为说明或链接），下面是白卡；同一行的组件从同一条线开始、到同一条线结束（外框撑满行高）。
 * bare 为一组数字卡：不套白卡，每张数字卡自己浮起。加载中骨架、出错可重试，只影响本组件。
 */
export const WidgetCard: React.FC<{
  title: string;
  /** 统计期间，标题旁显示，如「本月」「截至今天」 */
  period?: string;
  /** 口径说明，放进帮助图标 */
  help?: string;
  /** 标题行右侧的说明（如「共 16 件」「全部正常 · 截至 16:40」），dot 为前面的状态圆点颜色 */
  note?: React.ReactNode;
  dot?: string;
  extra?: React.ReactNode;
  loading?: boolean;
  error?: string;
  onRetry?: () => void;
  /** 不套白卡（数字卡组） */
  bare?: boolean;
  /** 白卡左右内边距收窄到 12（列表行自带内边距时） */
  inset?: boolean;
  children?: React.ReactNode;
}> = ({
  title,
  period,
  help,
  note,
  dot,
  extra,
  loading,
  error,
  onRetry,
  bare,
  inset,
  children,
}) => {
  const { palette } = useAppTheme();
  const head = (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        minHeight: 24,
        flexWrap: 'wrap',
      }}
    >
      <CardTitle>{title}</CardTitle>
      {period && (
        <span style={{ fontSize: 13, color: palette.mute }}>{period}</span>
      )}
      {help && <HelpTip title={help} />}
      <span style={{ flex: 1 }} />
      {note && (
        <span
          className="num"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
            fontSize: 12,
            color: palette.mute,
          }}
        >
          {dot && (
            <span
              aria-hidden
              style={{
                width: 6,
                height: 6,
                borderRadius: 3,
                background: dot,
              }}
            />
          )}
          {note}
        </span>
      )}
      {extra}
    </div>
  );
  const status = error ? (
    <EmptyState
      kind="error"
      description={`${error}；其他卡片不受影响`}
      action={
        <Button size="small" icon={<RotateCw size={14} />} onClick={onRetry}>
          重试
        </Button>
      }
    />
  ) : loading ? (
    <Skeleton active paragraph={{ rows: 3 }} />
  ) : null;
  return (
    <section
      aria-label={title}
      style={{
        height: '100%',
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
      }}
    >
      {head}
      {bare && !status ? (
        <div style={{ flex: 1, minHeight: 0 }}>{children}</div>
      ) : (
        <Panel
          as="div"
          style={{
            flex: 1,
            minHeight: 0,
            display: 'flex',
            flexDirection: 'column',
            padding: inset && !status ? '20px 12px' : 20,
          }}
        >
          {status ?? children}
        </Panel>
      )}
    </section>
  );
};

/** 卡片右上角的「查看全部 >」一类链接：次要色，悬停才着色（工作台原型 H1，同 D23 的单号） */
export const MoreLink: React.FC<{
  onClick: () => void;
  children: React.ReactNode;
}> = ({ onClick, children }) => (
  <button
    type="button"
    className="zhul-link zhul-link-quiet"
    onClick={onClick}
    style={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: 2,
      fontSize: 13,
    }}
  >
    {children}
    <ChevronRight size={14} aria-hidden />
  </button>
);

/** 数字小块（原型 D1）：带边框的小卡，名称（可带图标；能点时后面带箭头）、数值、一行说明 */
export const Metric: React.FC<{
  label: string;
  value: React.ReactNode;
  hint?: React.ReactNode;
  hintColor?: string;
  icon?: React.ReactNode;
  tone?: Tone;
  onClick?: () => void;
}> = ({ label, value, hint, hintColor, icon, tone = 'primary', onClick }) => {
  const { palette } = useAppTheme();
  const [hover, setHover] = useState(false);
  const body = (
    <>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          fontSize: 13,
          color: palette.sub,
        }}
      >
        {icon && <IconChip icon={icon} tone={tone} size={24} round />}
        {label}
        {onClick && (
          <ChevronRight size={14} aria-hidden style={{ color: palette.mute }} />
        )}
      </div>
      {/* 卡片变窄时自动缩小字号，金额不溢出、不截断 */}
      <FitText
        size={22}
        min={16}
        style={{
          fontWeight: 700,
          color: palette.ink,
          fontVariantNumeric: 'tabular-nums',
          margin: '8px 0 4px',
        }}
      >
        {value}
      </FitText>
      {hint !== undefined && (
        <div style={{ fontSize: 12, color: hintColor ?? palette.mute }}>
          {hint}
        </div>
      )}
    </>
  );
  const style: React.CSSProperties = {
    // 半行卡片放不下时两两换行，金额不被拆成两行
    flex: '1 1 190px',
    minWidth: 0,
    padding: 16,
    borderRadius: 10,
    background: onClick && hover ? palette.hover : palette.card,
    border: `1px solid ${palette.hairline}`,
    transition: 'background .2s',
    textAlign: 'left',
  };
  return onClick ? (
    <button
      type="button"
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{ ...style, cursor: 'pointer' }}
    >
      {body}
    </button>
  ) : (
    <div style={style}>{body}</div>
  );
};

const ONE_LINE: React.CSSProperties = {
  display: 'block',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  whiteSpace: 'nowrap',
};

/**
 * 待办行（原型 H3）：图标、名称、说明、数量与右箭头，行高 52；
 * 点击进入对应列表。悬停浅底；错误、警告色的图标在数量为 0 时为中性色。
 */
export const TodoItem: React.FC<{
  icon: React.ReactNode;
  tone: Tone;
  title: string;
  desc: string;
  count: number;
  alert?: boolean;
  onClick: () => void;
}> = ({ icon, tone, title, desc, count, alert, onClick }) => {
  const { palette } = useAppTheme();
  const [hover, setHover] = useState(false);
  const shown = alertTone(tone, count);
  const [color] = useTone()(shown);
  return (
    // 行高 52、不画分隔线，悬停浅底（工作台原型 H3：5 行正好放进 M 档的卡）
    <button
      type="button"
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        width: '100%',
        minHeight: 52,
        padding: '0 8px',
        border: 'none',
        borderRadius: 8,
        background: hover ? palette.hover : 'transparent',
        transition: 'background .2s',
        cursor: 'pointer',
        textAlign: 'left',
        font: 'inherit',
      }}
    >
      <IconChip icon={icon} tone={shown} size={38} />
      <span style={{ flex: 1, minWidth: 0 }}>
        <span style={{ ...ONE_LINE, fontWeight: 600, color: palette.ink }}>
          {title}
        </span>
        <span style={{ ...ONE_LINE, fontSize: 12, color: palette.mute }}>
          {desc}
        </span>
      </span>
      <span
        style={{
          fontSize: 20,
          fontWeight: 600,
          color: count > 0 && alert ? color : palette.ink,
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {count}
      </span>
      <ChevronRight size={16} aria-hidden style={{ color: palette.mute }} />
    </button>
  );
};
