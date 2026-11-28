import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { ATTR_TONE, type AttrTone } from '@/theme/palette';

export type PillTone =
  | 'gray'
  | 'cyan'
  | 'accent'
  | 'violet'
  | 'orange'
  | 'green'
  | 'red'
  | 'mute'
  | 'info'
  | 'selected'
  | AttrTone;

/**
 * 状态标签：语义色浅底 + 文字色，12px/600，高 24，圆角 4（界面规范「状态标签」）；提醒类在文字前放图标。
 * 全站只用这一个，各模块不再各写一份（曾有圆角 12 的胶囊版本混用）。
 * 主题色浅底只表示选中、当前（ui-redesign D42）：只有 `selected` 用它（如「当前客户」「已选 N 个」「当前步骤」）；
 * 旧的 `accent` 按 status-colors.md 归类规则并入信息色。
 * 属性标签（取值固定的几种）用属性色 blue / purple / magenta，同一列不同取值颜色不重复，「未定、没有值」用 gray；
 * 取值不固定的（行业、品类列表、素材标签、货代名称、版本号）用 gray（D75）。
 */
export const Pill: React.FC<{
  tone: PillTone;
  /** 已不再画圆点（规范「状态标签」：浅底 + 文字），保留参数兼容旧调用 */
  dot?: boolean;
  /** 悬停提示 */
  title?: string;
  children: React.ReactNode;
}> = ({ tone, title, children }) => {
  const { palette, mode } = useAppTheme();
  const attr = (t: AttrTone) => ({
    fg: ATTR_TONE[mode][t][0],
    bg: ATTR_TONE[mode][t][1],
  });
  const colors: Record<PillTone, { fg: string; bg: string }> = {
    blue: attr('blue'),
    purple: attr('purple'),
    magenta: attr('magenta'),
    indigo: attr('indigo'),
    plum: attr('plum'),
    brown: attr('brown'),
    olive: attr('olive'),
    gray: { fg: palette.sub, bg: palette.neutralSoft },
    mute: { fg: palette.mute, bg: palette.neutralSoft },
    cyan: { fg: palette.info, bg: palette.neutralSoft },
    accent: { fg: palette.info, bg: palette.infoSoft },
    selected: { fg: palette.link, bg: palette.primarySoft },
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
        // 顶对齐：按基线对齐时行盒会比 24 再高出几像素，「标签 + 一行小字」的单元格就超出两行行高（D21）
        verticalAlign: 'top',
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
        // 不超出所在单元格；里面放 EllipsisText 时长文字省略
        maxWidth: '100%',
      }}
    >
      {children}
    </span>
  );
};
