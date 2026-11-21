/**
 * 全站设计令牌：中性灰阶加 6 种主题色预设（照 PrimeDash），浅色、深色各一套。
 * 取值见 .claude/context/ui-design-patterns.md「设计令牌」与 openspec/changes/ui-redesign/design.md 第 2 节；
 * 页面里需要直接取色时用 useAppTheme().palette，不要散落十六进制。
 */
export type ThemeMode = 'dark' | 'light';

export type PresetKey = 'amber' | 'teal' | 'sky' | 'purple' | 'royal' | 'red';

interface Preset {
  /** 主题设置里显示的名字 */
  name: string;
  /** 原色：主按钮底色、浅色下的链接与选中文字、默认 Logo */
  primary: string;
  /** 浅色选中浅底：原色 12% 叠在白色上 */
  lightSoft: string;
  /** 深色链接与选中文字：在卡片、选中底、父级菜单底上都不低于 4.5:1 */
  darkLink: string;
  /** 深色选中底：原色 16% 叠在 #1A1A1A 上 */
  darkSoft: string;
  /** 主题色作为图表第一个系列时，去掉色相最接近的这个系列色 */
  chartDrop: string;
}

export const PRESETS: Record<PresetKey, Preset> = {
  amber: {
    name: '琥珀',
    primary: '#FDA92D',
    lightSoft: '#FFF5E6',
    darkLink: '#FDA92D',
    darkSoft: '#3E311D',
    chartDrop: '#B87A14',
  },
  teal: {
    name: '青绿',
    primary: '#0D9394',
    lightSoft: '#E2F2F2',
    darkLink: '#2FA2A3',
    darkSoft: '#182D2E',
    chartDrop: '#2F9E99',
  },
  sky: {
    name: '天蓝',
    primary: '#078DEE',
    lightSoft: '#E1F1FD',
    darkLink: '#2098F0',
    darkSoft: '#172C3C',
    chartDrop: '#3B82F6',
  },
  purple: {
    name: '紫',
    primary: '#7635DC',
    lightSoft: '#EFE7FB',
    darkLink: '#A77EE9',
    darkSoft: '#291E39',
    chartDrop: '#3B82F6',
  },
  royal: {
    name: '宝蓝',
    primary: '#2065D1',
    lightSoft: '#E4EDF9',
    darkLink: '#5E94E6',
    darkSoft: '#1B2637',
    chartDrop: '#3B82F6',
  },
  red: {
    name: '红',
    primary: '#FF3030',
    lightSoft: '#FFE6E6',
    darkLink: '#FF5959',
    darkSoft: '#3F1E1E',
    chartDrop: '#E8664A',
  },
};

/** 主题设置里的排列顺序 */
export const PRESET_ORDER: PresetKey[] = [
  'amber',
  'teal',
  'sky',
  'purple',
  'royal',
  'red',
];

export const DEFAULT_PRESET: PresetKey = 'amber';

export const isPresetKey = (v: unknown): v is PresetKey =>
  typeof v === 'string' && v in PRESETS;

/** 中性灰阶与状态色；状态色浅色、深色相同，浅底按各自的卡片色叠出 */
const BASE = {
  light: {
    canvas: '#F9FAFB',
    sidebar: '#FFFFFF',
    card: '#FFFFFF',
    /** 下拉菜单、气泡卡片 */
    elevated: '#FFFFFF',
    elevatedLine: '#E5E8EB',
    /** 中性浅底：指标格、分组块、凹底 */
    inset: '#F1F3F5',
    /** 表头底色 */
    head: '#F4F6F8',
    /** 展开的父级菜单底 */
    navOn: '#EDEEEF',
    hover: '#F6F7F8',
    hairline: '#E5E8EB',
    /** 输入框边框 */
    control: '#D3D5D8',
    /** 主文字 */
    ink: '#212B36',
    /** 正文 */
    sub: '#3E4750',
    /** 次要文字 */
    mute: '#637381',
    /** 只用于禁用和占位，不作正文 */
    faint: '#919EAB',
    green: '#22C55E',
    greenSoft: '#E5F8EC',
    orange: '#FFAB00',
    orangeSoft: '#FFF5E0',
    red: '#FF5630',
    redSoft: '#FFEBE6',
    info: '#00B8D9',
    infoSoft: '#E0F6FA',
    neutralSoft: '#ECEEF0',
  },
  dark: {
    canvas: '#0A0A0A',
    sidebar: '#1A1A1A',
    card: '#1A1A1A',
    elevated: '#222222',
    elevatedLine: '#3A3A3A',
    inset: '#232323',
    head: '#222222',
    navOn: '#2C2C2C',
    hover: '#222222',
    hairline: '#2A2A2A',
    control: '#4A4A4A',
    ink: '#F5F5F5',
    sub: '#E0E0E0',
    mute: '#A0A0A0',
    faint: '#6E6E6E',
    green: '#22C55E',
    greenSoft: '#1B3525',
    orange: '#FFAB00',
    orangeSoft: '#3F3116',
    red: '#FF5630',
    redSoft: '#3F241E',
    info: '#00B8D9',
    infoSoft: '#163339',
    neutralSoft: '#2F2F2F',
  },
} as const;

/** 前景色按 alpha 叠在背景色上，结果取整到 #RRGGBB */
export function mix(fg: string, bg: string, alpha: number): string {
  const ch = (hex: string, i: number) =>
    parseInt(hex.slice(1 + i * 2, 3 + i * 2), 16);
  const out = [0, 1, 2].map((i) =>
    Math.round(ch(fg, i) * alpha + ch(bg, i) * (1 - alpha))
      .toString(16)
      .padStart(2, '0'),
  );
  return `#${out.join('')}`.toUpperCase();
}

/** 由主题模式和主题色生成整套令牌 */
export function buildPalette(
  mode: ThemeMode,
  preset: PresetKey = DEFAULT_PRESET,
) {
  const base = BASE[mode];
  const pr = PRESETS[preset] ?? PRESETS[DEFAULT_PRESET];
  const link = mode === 'light' ? pr.primary : pr.darkLink;
  return {
    ...base,
    /** 主按钮底色（白字） */
    primary: pr.primary,
    /** 链接、单号、选中文字 */
    link,
    /** 选中浅底：选中的菜单子项、快捷筛选、当前标签 */
    primarySoft: mode === 'light' ? pr.lightSoft : pr.darkSoft,
    /** 选中描边：主题色 40% 叠在卡片上 */
    primaryLine: mix(link, base.card, 0.4),
  };
}

export type Palette = ReturnType<typeof buildPalette>;

/** 图表系列色：深浅底上都不低于 3:1 */
export const CHART_SERIES = [
  '#3B82F6',
  '#2F9E99',
  '#B87A14',
  '#E8664A',
  '#2E9A62',
  '#7A8794',
] as const;

/** 主题色作为第一个系列时的系列色：去掉色相最接近的一个，其余顺延 */
export const chartSeries = (preset: PresetKey = DEFAULT_PRESET): string[] => [
  PRESETS[preset].primary,
  ...CHART_SERIES.filter((c) => c !== PRESETS[preset].chartDrop),
];

/** 浅色卡片、侧栏、顶栏、主按钮的三层柔和阴影；深色不用阴影 */
export const CARD_SHADOW =
  '0 3px 1px -2px rgba(145,158,171,.2), 0 2px 2px rgba(145,158,171,.14), 0 1px 5px rgba(145,158,171,.12)';

/** 浅色下拉菜单、气泡卡片的阴影 */
export const POPUP_SHADOW = '0 8px 16px rgba(145,158,171,.24)';
