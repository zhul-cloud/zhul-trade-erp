import { ReloadOutlined } from '@ant-design/icons';
import { Button, Skeleton } from 'antd';
import { ChevronRight } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import FitText from '@/components/FitText';
import { CardTitle, HelpTip } from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { readBizError } from '@/pages/crm/opportunity/service';
import { Card } from '@/pages/inquiry/shared/components';
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
 * 看板组件的外框（工作台原型 D1）：标题 18/500，旁边是统计期间（如「本月」）和口径帮助图标，右侧操作；
 * 加载中骨架、出错可重试
 */
export const WidgetCard: React.FC<{
  title: string;
  /** 统计期间，标题旁显示，如「本月」「截至今天」 */
  period?: string;
  /** 口径说明，放进帮助图标 */
  help?: string;
  extra?: React.ReactNode;
  loading?: boolean;
  error?: string;
  onRetry?: () => void;
  children?: React.ReactNode;
}> = ({ title, period, help, extra, loading, error, onRetry, children }) => {
  const { palette } = useAppTheme();
  return (
    // 卡片保持自身内容的高度：工作台按两列瀑布流排，不为对齐拉伸（规范「工作台」，取代 UI-041 的同行等高）
    <Card style={{ display: 'flex', flexDirection: 'column' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 16,
          flexWrap: 'wrap',
        }}
      >
        <CardTitle>{title}</CardTitle>
        {period && (
          <span style={{ fontSize: 13, color: palette.mute }}>{period}</span>
        )}
        {help && <HelpTip title={help} />}
        <span style={{ flex: 1 }} />
        {extra}
      </div>
      {error ? (
        <div style={{ textAlign: 'center', padding: '24px 0' }}>
          <div style={{ color: palette.ink, fontWeight: 600 }}>加载失败</div>
          <div
            style={{ color: palette.mute, fontSize: 12, margin: '6px 0 12px' }}
          >
            {error}；其他卡片不受影响
          </div>
          <Button size="small" icon={<ReloadOutlined />} onClick={onRetry}>
            重试
          </Button>
        </div>
      ) : loading ? (
        <Skeleton active paragraph={{ rows: 3 }} />
      ) : (
        <div
          style={{
            flex: 1,
            minHeight: 0,
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          {children}
        </div>
      )}
    </Card>
  );
};

/** 卡片右上角的「查看全部 >」一类链接 */
export const MoreLink: React.FC<{
  onClick: () => void;
  children: React.ReactNode;
}> = ({ onClick, children }) => (
  <button
    type="button"
    className="zhul-link"
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

/**
 * 待办行（原型 D1）：图标、名称、说明、数量与右箭头，行间细分隔线（last 为最后一行，不画下边线）；
 * 点击进入对应列表。悬停浅底；错误、警告色的图标在数量为 0 时为中性色。
 */
export const TodoItem: React.FC<{
  icon: React.ReactNode;
  tone: Tone;
  title: string;
  desc: string;
  count: number;
  alert?: boolean;
  last?: boolean;
  onClick: () => void;
}> = ({ icon, tone, title, desc, count, alert, last, onClick }) => {
  const { palette } = useAppTheme();
  const [hover, setHover] = useState(false);
  const shown = alertTone(tone, count);
  const [color] = useTone()(shown);
  return (
    // 分隔线画在外层，按钮本身不裁剪，键盘焦点框完整可见
    <div
      style={{
        padding: '4px 0',
        borderBottom: last ? undefined : `1px solid ${palette.hairline}`,
      }}
    >
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
          padding: '10px 8px',
          border: 'none',
          borderRadius: 8,
          background: hover ? palette.hover : 'transparent',
          transition: 'background .2s',
          cursor: 'pointer',
          textAlign: 'left',
        }}
      >
        <IconChip icon={icon} tone={shown} size={38} />
        <span style={{ flex: 1, minWidth: 0 }}>
          <span
            style={{ display: 'block', fontWeight: 600, color: palette.ink }}
          >
            {title}
          </span>
          <span style={{ display: 'block', fontSize: 12, color: palette.mute }}>
            {desc}
          </span>
        </span>
        <span
          style={{
            fontSize: 20,
            fontWeight: 700,
            color: count > 0 && alert ? color : palette.ink,
            fontVariantNumeric: 'tabular-nums',
          }}
        >
          {count}
        </span>
        <ChevronRight size={16} aria-hidden style={{ color: palette.mute }} />
      </button>
    </div>
  );
};
