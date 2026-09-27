import React from 'react';
import { LIFECYCLE_META } from '../constants';
import { useProductTheme } from '../theme';

type Tone = 'green' | 'orange' | 'red' | 'gray' | 'accent';

/** 胶囊标签：颜色只用来表达状态（绿=正常、橙=注意、红=停产/风险、灰=未知/无、蓝=强调） */
export const Pill: React.FC<{
  tone: Tone;
  children: React.ReactNode;
  title?: string;
}> = ({ tone, children, title }) => {
  const { palette } = useProductTheme();
  const map: Record<Tone, { fg: string; bg: string }> = {
    green: { fg: palette.green, bg: palette.greenSoft },
    orange: { fg: palette.orange, bg: palette.orangeSoft },
    red: { fg: palette.red, bg: palette.redSoft },
    gray: { fg: palette.sub, bg: palette.inset },
    accent: { fg: palette.link, bg: palette.accentSoft },
  };
  const c = map[tone];
  return (
    <span
      title={title}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        height: 24,
        padding: '0 10px',
        borderRadius: 12,
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

export const LifecyclePill: React.FC<{ value: number }> = ({ value }) => {
  const meta = LIFECYCLE_META[value] ?? LIFECYCLE_META[6];
  return <Pill tone={meta.tone}>{meta.text}</Pill>;
};

/** 品牌字母标：没有 Logo 时用品牌名首字母 + 品牌色 */
export const BrandMark: React.FC<{
  name?: string;
  color?: string;
  size?: number;
}> = ({ name = '?', color, size = 28 }) => {
  const { palette } = useProductTheme();
  return (
    <span
      aria-hidden="true"
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: size,
        height: size,
        borderRadius: size / 3,
        background: color || palette.accentSoft,
        color: color ? '#FFFFFF' : palette.link,
        fontWeight: 700,
        fontSize: Math.round(size * 0.46),
        flex: 'none',
      }}
    >
      {name.trim().charAt(0).toUpperCase()}
    </span>
  );
};
