import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';

export type PillTone =
  | 'gray'
  | 'cyan'
  | 'accent'
  | 'violet'
  | 'orange'
  | 'green'
  | 'red'
  | 'mute'
  | 'info';

/**
 * 状态标签：语义色浅底 + 文字色，12px/600，高 24，圆角 4（界面规范「状态标签」）；提醒类在文字前放图标。
 * 全站只用这一个，各模块不再各写一份（曾有圆角 12 的胶囊版本混用）。
 */
export const Pill: React.FC<{
  tone: PillTone;
  /** 已不再画圆点（规范「状态标签」：浅底 + 文字），保留参数兼容旧调用 */
  dot?: boolean;
  /** 悬停提示 */
  title?: string;
  children: React.ReactNode;
}> = ({ tone, title, children }) => {
  const { palette } = useAppTheme();
  const colors: Record<PillTone, { fg: string; bg: string }> = {
    gray: { fg: palette.sub, bg: palette.neutralSoft },
    mute: { fg: palette.mute, bg: palette.neutralSoft },
    cyan: { fg: palette.info, bg: palette.inset },
    accent: { fg: palette.link, bg: palette.primarySoft },
    violet: { fg: palette.info, bg: palette.infoSoft },
    orange: { fg: palette.orange, bg: palette.orangeSoft },
    green: { fg: palette.green, bg: palette.greenSoft },
    red: { fg: palette.red, bg: palette.redSoft },
    info: { fg: palette.info, bg: palette.infoSoft },
  };
  const c = colors[tone];
  return (
    <span
      title={title}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        height: 24,
        padding: '0 8px',
        borderRadius: 4,
        fontSize: 12,
        fontWeight: 600,
        color: c.fg,
        background: c.bg,
        whiteSpace: 'nowrap',
      }}
    >
      {children}
    </span>
  );
};
