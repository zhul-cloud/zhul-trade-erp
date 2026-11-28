import { Info } from 'lucide-react';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';

/**
 * 说明框：卡片、弹窗里的一段说明或待处理提示，中性浅底（卡内凹格）、无边框，左侧一个图标。
 * 主题色浅底只表示「选中」，说明文字不用它（ui-redesign D42，lint:ui `primary-soft`）。
 * - tone="neutral"（默认）：普通说明，灰色图标；
 * - tone="action"：需要用户处理的提示（如「N 条待确认」），图标用主题色，右侧可放操作（extra）。
 * 需要警告、报错时用 antd Alert（规范「提示条」），不用这个组件。
 */
export const Callout: React.FC<{
  tone?: 'neutral' | 'action';
  /** 默认信息图标；传 null 不显示 */
  icon?: React.ReactNode;
  /** 右侧操作，如按钮、勾选框 */
  extra?: React.ReactNode;
  style?: React.CSSProperties;
  /** 状态类提示传 status，读屏会播报 */
  role?: 'status';
  children: React.ReactNode;
}> = ({ tone = 'neutral', icon, extra, style, role, children }) => {
  const { palette } = useAppTheme();
  const mark = icon === undefined ? <Info size="1em" /> : icon;
  return (
    <div
      role={role}
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 10,
        padding: '10px 14px',
        borderRadius: 10,
        background: palette.inset,
        color: palette.sub,
        fontSize: 13,
        lineHeight: 1.6,
        ...style,
      }}
    >
      {mark && (
        <span
          style={{
            display: 'inline-flex',
            flex: 'none',
            marginTop: '0.3em',
            color: tone === 'action' ? palette.link : palette.mute,
          }}
        >
          {mark}
        </span>
      )}
      <div style={{ flex: 1, minWidth: 0 }}>{children}</div>
      {extra && (
        <div style={{ flex: 'none', alignSelf: 'center' }}>{extra}</div>
      )}
    </div>
  );
};

export default Callout;
