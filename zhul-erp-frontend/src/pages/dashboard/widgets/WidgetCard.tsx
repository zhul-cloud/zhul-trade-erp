import { ReloadOutlined } from '@ant-design/icons';
import { Button, Skeleton } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { readBizError } from '@/pages/crm/opportunity/service';
import { Card } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { workbenchApi } from '../service';

export type Tone = 'blue' | 'green' | 'orange' | 'red' | 'violet' | 'gray';

/** 色调 → [主色, 浅底] */
export const useTone = () => {
  const { palette } = useAppTheme();
  return (tone: Tone): [string, string] =>
    (
      ({
        blue: [palette.link, palette.accentSoft],
        green: [palette.green, palette.greenSoft],
        orange: [palette.orange, palette.orangeSoft],
        red: [palette.red, palette.redSoft],
        violet: [palette.violet, palette.violetSoft],
        gray: [palette.faint, palette.hover],
      }) as Record<Tone, [string, string]>
    )[tone];
};

export const IconChip: React.FC<{
  icon: React.ReactNode;
  tone: Tone;
  size?: number;
}> = ({ icon, tone, size = 32 }) => {
  const [color, soft] = useTone()(tone);
  return (
    <span
      style={{
        width: size,
        height: size,
        borderRadius: 10,
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
  return { data, error, reload: load };
}

/** 看板组件的外框：标题行（图标、标题、说明、右侧操作），加载中骨架、出错可重试 */
export const WidgetCard: React.FC<{
  icon: React.ReactNode;
  tone: Tone;
  title: string;
  sub?: string;
  extra?: React.ReactNode;
  loading?: boolean;
  error?: string;
  onRetry?: () => void;
  children?: React.ReactNode;
}> = ({ icon, tone, title, sub, extra, loading, error, onRetry, children }) => {
  const { palette } = useAppTheme();
  return (
    <Card style={{ padding: 20, height: '100%' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          marginBottom: 16,
          flexWrap: 'wrap',
        }}
      >
        <IconChip icon={icon} tone={tone} />
        <b style={{ fontSize: 16, color: palette.ink }}>{title}</b>
        {sub && (
          <span style={{ fontSize: 12, color: palette.mute }}>{sub}</span>
        )}
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
        children
      )}
    </Card>
  );
};

/** 数字小块：标题、数值、一行说明 */
export const Metric: React.FC<{
  label: string;
  value: React.ReactNode;
  hint?: React.ReactNode;
  hintColor?: string;
  onClick?: () => void;
}> = ({ label, value, hint, hintColor, onClick }) => {
  const { palette } = useAppTheme();
  const body = (
    <>
      <div style={{ fontSize: 13, color: palette.sub }}>{label}</div>
      <div
        style={{
          fontSize: 26,
          fontWeight: 700,
          color: palette.ink,
          fontVariantNumeric: 'tabular-nums',
          margin: '6px 0 4px',
          whiteSpace: 'nowrap',
        }}
      >
        {value}
      </div>
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
    borderRadius: 12,
    background: palette.inset,
    textAlign: 'left',
    border: 'none',
  };
  return onClick ? (
    <button
      type="button"
      onClick={onClick}
      style={{ ...style, cursor: 'pointer' }}
    >
      {body}
    </button>
  ) : (
    <div style={style}>{body}</div>
  );
};

/** 待办行：图标、名称、说明、数量，点击进入对应列表 */
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
  const [color] = useTone()(tone);
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        width: '100%',
        padding: '12px 14px',
        borderRadius: 12,
        border: 'none',
        background: palette.inset,
        cursor: 'pointer',
        textAlign: 'left',
      }}
    >
      <IconChip icon={icon} tone={tone} size={30} />
      <span style={{ flex: 1, minWidth: 0 }}>
        <span style={{ display: 'block', fontWeight: 600, color: palette.ink }}>
          {title}
        </span>
        <span style={{ display: 'block', fontSize: 12, color: palette.mute }}>
          {desc}
        </span>
      </span>
      <span
        style={{
          fontSize: 22,
          fontWeight: 700,
          color: count > 0 && alert ? color : palette.ink,
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {count}
      </span>
    </button>
  );
};
