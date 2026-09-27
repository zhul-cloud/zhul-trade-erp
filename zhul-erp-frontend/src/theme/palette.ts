/**
 * 全站设计令牌（方案 D：信任蓝 + 中性灰，深色默认、浅色并存）。
 * 取值见 .claude/context/ui-design-patterns.md「V3 方案 D」一节，页面里需要直接取色时用它，
 * 不要散落十六进制。
 */
export type ThemeMode = 'dark' | 'light';

export const PALETTE = {
  dark: {
    canvas: '#0B1220',
    sidebar: '#0A101D',
    card: '#111A2C',
    inset: '#0D1524',
    hairline: '#1E293B',
    control: '#64748B',
    ink: '#F8FAFC',
    sub: '#CBD5E1',
    mute: '#94A3B8',
    link: '#60A5FA',
    accentSoft: '#182B4C',
    accentLine: '#234681',
    hover: '#16233A',
    green: '#4ADE80',
    greenSoft: '#183236',
    orange: '#FB923C',
    orangeSoft: '#2D282E',
    red: '#F87171',
    redSoft: '#2D2434',
    violet: '#A78BFA',
    violetSoft: '#2A2450',
    faint: '#64748B',
    cyan: '#22D3EE',
  },
  light: {
    canvas: '#F8FAFC',
    sidebar: '#FFFFFF',
    card: '#FFFFFF',
    inset: '#F1F5F9',
    hairline: '#E2E8F0',
    control: '#8492A6',
    ink: '#0F172A',
    sub: '#475569',
    mute: '#64748B',
    link: '#1D4ED8',
    accentSoft: '#DBEAFE',
    accentLine: '#93C5FD',
    hover: '#F1F5F9',
    green: '#15803D',
    greenSoft: '#DCFCE7',
    orange: '#C2410C',
    orangeSoft: '#FFEDD5',
    red: '#B91C1C',
    redSoft: '#FEE2E2',
    violet: '#6D28D9',
    violetSoft: '#EDE9FE',
    faint: '#94A3B8',
    cyan: '#0E7490',
  },
} as const;

export type Palette = (typeof PALETTE)[ThemeMode];

export const PRIMARY_GRADIENT = 'linear-gradient(135deg, #2563EB, #4F46E5)';
export const ACCENT_GRADIENT =
  'linear-gradient(90deg, #BFDBFE, #60A5FA, #22D3EE)';
export const LOGO_GRADIENT =
  'linear-gradient(135deg, #3B82F6, #6366F1, #22D3EE)';
