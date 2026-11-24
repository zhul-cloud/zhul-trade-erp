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
  /** 主题色作为图表第一个系列时，去掉色相最接近的那个系列色（CHART_SERIES 的序号） */
  chartDrop: number;
  /** 原色实底上的文字：白字对比度不足 3:1 时用深色（琥珀 1.93:1，深色字 7.4:1；ui-redesign D24） */
  onPrimary?: string;
}

export const PRESETS: Record<PresetKey, Preset> = {
  amber: {
    name: '琥珀',
    primary: '#FDA92D',
    lightSoft: '#FFF5E6',
    darkLink: '#FDA92D',
    darkSoft: '#3E311D',
    chartDrop: 1,
    onPrimary: '#212B36',
  },
  teal: {
    name: '青绿',
    primary: '#0D9394',
    lightSoft: '#E2F2F2',
    darkLink: '#2FA2A3',
    darkSoft: '#182D2E',
    chartDrop: 2,
  },
  sky: {
    name: '天蓝',
    primary: '#078DEE',
    lightSoft: '#E1F1FD',
    darkLink: '#2098F0',
    darkSoft: '#172C3C',
    chartDrop: 0,
  },
  purple: {
    name: '紫',
    primary: '#7635DC',
    lightSoft: '#EFE7FB',
    darkLink: '#A77EE9',
    darkSoft: '#291E39',
    chartDrop: 3,
  },
  royal: {
    name: '宝蓝',
    primary: '#2065D1',
    lightSoft: '#E4EDF9',
    darkLink: '#5E94E6',
    darkSoft: '#1B2637',
    chartDrop: 0,
  },
  red: {
    name: '红',
    primary: '#FF3030',
    lightSoft: '#FFE6E6',
    darkLink: '#FF5959',
    darkSoft: '#3F1E1E',
    chartDrop: 1,
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
  typeof v === 'string' && Object.hasOwn(PRESETS, v);

/** 状态色原色（浅色、深色相同）：标签底、进度条、圆点、角标、图标底块等填充用 */
const STATUS_MAIN = {
  green: '#22C55E',
  orange: '#FFAB00',
  red: '#FF5630',
  info: '#00B8D9',
} as const;

/**
 * 中性灰阶与状态色。
 * - green、orange、red、info 是文字色：浅色下原色当文字对比度只有 1.75–3.17，取同色系深一档（2026-11-21 用户决定）；深色下用原色。
 *   2026-11-24 用户决定照参考模板（Minimal / PrimeDash）：浅色文字用模板的 dark 档（绿 #118D57、橙 #B76E00、红 #B71D18、信息 #006C9C），
 *   浅底为原色 16%（对比图 R14 方案 C）。绿、橙标签字对比度约 3.7:1，低于 4.5:1，用户看过后接受（压到 4.5:1 的颜色发暗发土）。
 * - 各自的 Soft 为原色叠在卡片色上：浅色、深色都是 16%（照参考模板）。
 */
const BASE = {
  light: {
    /**
     * 表面分层（ui-design-patterns「层级与表面」，D38）：页面底最深 → 卡内浅灰（head、inset、hover）居中 → 白卡最亮。
     * 卡内浅灰不得等于或深于页面底，否则贴到卡边时像露出页面；参照 Ant Design 页面 #f5f5f5、卡片 #fff、表头 #fafafa
     */
    canvas: '#F4F6F8',
    sidebar: '#FFFFFF',
    card: '#FFFFFF',
    /** 下拉菜单、气泡卡片 */
    elevated: '#FFFFFF',
    elevatedLine: '#E5E8EB',
    /** 中性浅底：指标格、分组块、凹底（比页面底浅） */
    inset: '#F7F8FA',
    /** 表头底色（比页面底浅） */
    head: '#F9FAFB',
    /** 直接放在页面底上的控件轨道（分段控件）：比页面底深一档 */
    track: '#E9ECEF',
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
    green: '#118D57',
    greenSoft: '#DCF6E5',
    orange: '#B76E00',
    orangeSoft: '#FFF2D6',
    red: '#B71D18',
    redSoft: '#FFE4DE',
    info: '#006C9C',
    infoSoft: '#D6F4F9',
    neutralSoft: '#ECEEF0',
    /** 中性标签文字：在中性浅底上不低于 4.5:1 */
    neutral: '#56636F',
  },
  dark: {
    canvas: '#0A0A0A',
    sidebar: '#1A1A1A',
    card: '#1A1A1A',
    elevated: '#222222',
    elevatedLine: '#3A3A3A',
    inset: '#232323',
    head: '#222222',
    track: '#262626',
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
    red: '#FF6A47',
    redSoft: '#3F241E',
    info: '#00B8D9',
    infoSoft: '#163339',
    neutralSoft: '#2F2F2F',
    neutral: '#A0A0A0',
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

/**
 * 图表第一个系列跟主题色走（照 PrimeDash）：浅色用原色，深色用深色链接色。
 * 琥珀原色在白底只有 1.93:1，用户 2026-11-22 看过对比图后仍选原色（深一档的琥珀显脏），
 * 用到它的图表都在旁边或悬停提示里写数值，不只靠颜色传达。
 */
const chartPrimary = (mode: ThemeMode, pr: Preset) =>
  mode === 'light' ? pr.primary : pr.darkLink;

/** 由主题模式和主题色生成整套令牌 */
export function buildPalette(
  mode: ThemeMode,
  preset: PresetKey = DEFAULT_PRESET,
) {
  const base = BASE[mode];
  const pr = isPresetKey(preset) ? PRESETS[preset] : PRESETS[DEFAULT_PRESET];
  const link = mode === 'light' ? pr.primary : pr.darkLink;
  return {
    ...base,
    greenMain: STATUS_MAIN.green,
    orangeMain: STATUS_MAIN.orange,
    redMain: STATUS_MAIN.red,
    infoMain: STATUS_MAIN.info,
    /** 主按钮底色 */
    primary: pr.primary,
    /** 主题色实底上的文字（主按钮、Logo 首字、实底标记） */
    onPrimary: pr.onPrimary ?? '#FFFFFF',
    /** 链接、单号、选中文字 */
    link,
    /** 选中浅底：选中的菜单子项、快捷筛选、当前标签 */
    primarySoft: mode === 'light' ? pr.lightSoft : pr.darkSoft,
    /** 选中描边：链接色 40% 叠在卡片上 */
    primaryLine: mix(link, base.card, 0.4),
    /** 图表第一个系列（柱、条、线），跟主题色走 */
    chart: chartPrimary(mode, pr),
    /**
     * 图表色的浅色：同一内容只分两类、两类没有好坏之分时（本期 / 往期、漏斗终点 / 其余步骤），
     * 另一类用它而不是灰色或另一种色相（2026-11-24 用户确认）。有风险的一类仍用状态色
     */
    chartSoft: mix(
      chartPrimary(mode, pr),
      base.card,
      mode === 'light' ? 0.32 : 0.4,
    ),
  };
}

export type Palette = ReturnType<typeof buildPalette>;

/**
 * 图表系列色（对比图 R14，2026-11-24）：照参考模板用原色——蓝、橙红、青、紫、绿，第 6 个中性灰。
 * 顺序经 dataviz 调色板校验（色盲可分、饱和度、正常视力相邻可分全部通过）；原来为了深浅底都 ≥3:1 压成的中间调
 * （#2F9E99、#B87A14、#2E9A62）饱和度不足、相邻难分，显得发暗。原色对白底不到 3:1 的（青、绿），旁边都有文字或图例。
 * 深色另配同色相、亮度落在深色校验范围内的一组（CHART_SERIES_DARK）。
 */
export const CHART_SERIES = [
  '#1877F2',
  '#FF5630',
  '#00B8D9',
  '#8E33FF',
  '#22C55E',
  '#919EAB',
] as const;
export const CHART_SERIES_DARK = [
  '#1877F2',
  '#E5482A',
  '#00A3C2',
  '#8E33FF',
  '#1EAE53',
  '#919EAB',
] as const;
/** 按明暗取系列色 */
export const seriesFor = (mode: ThemeMode) =>
  mode === 'light' ? CHART_SERIES : CHART_SERIES_DARK;

/**
 * 主题色作为第一个系列时的系列色：去掉色相最接近的一个，其余顺延。
 * 第一个系列在深色下用深色链接色（原色在近黑底上可能不到 3:1，如紫 2.75）。
 */
export const chartSeries = (
  mode: ThemeMode = 'light',
  preset: PresetKey = DEFAULT_PRESET,
): string[] => {
  const pr = isPresetKey(preset) ? PRESETS[preset] : PRESETS[DEFAULT_PRESET];
  return [
    chartPrimary(mode, pr),
    ...seriesFor(mode).filter((_, i) => i !== pr.chartDrop),
  ];
};

/**
 * 指标家族色（数字卡左上图标块，ui-redesign D2）：按指标的含义着色，不跟主题色——
 * 蓝成交、紫利润、青收款、灰支出；红橙只在告警时出现（用状态色）。[图标色, 浅底]
 */
export type MetricFamily = 'blue' | 'purple' | 'cyan' | 'gray';
export const METRIC_FAMILY: Record<
  ThemeMode,
  Record<MetricFamily, [string, string]>
> = {
  // 参考模板原色，浅底 16%（R14）；原来的「青」#1F7A76 近乎墨绿
  light: {
    blue: ['#1877F2', '#DAE9FD'],
    purple: ['#8E33FF', '#EDDEFF'],
    cyan: ['#00B8D9', '#D6F4F9'],
    gray: ['#637381', '#ECEEF0'],
  },
  dark: {
    blue: ['#1877F2', '#1A293D'],
    purple: ['#8E33FF', '#2D1E3F'],
    cyan: ['#00B8D9', '#163339'],
    gray: ['#A0A0A0', '#2F2F2F'],
  },
};

/** 浅色卡片、侧栏、顶栏的柔和扩散阴影（ui-redesign D30）；深色不用阴影 */
export const CARD_SHADOW =
  '0 0 2px rgba(145,158,171,.2), 0 12px 24px -4px rgba(145,158,171,.12)';

/** 可点击卡片悬停时加深阴影，不位移（ui-redesign D3） */
export const CARD_SHADOW_HOVER =
  '0 0 2px rgba(145,158,171,.2), 0 16px 32px -4px rgba(145,158,171,.2)';

/** 主按钮的浮起阴影（贴边的薄阴影，按钮小，用扩散阴影会糊） */
export const BUTTON_SHADOW =
  '0 3px 1px -2px rgba(145,158,171,.2), 0 2px 2px rgba(145,158,171,.14), 0 1px 5px rgba(145,158,171,.12)';

/** 浅色下拉菜单、气泡卡片的阴影 */
export const POPUP_SHADOW = '0 8px 16px rgba(145,158,171,.24)';
