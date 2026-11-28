import { useAntdConfigSetter } from '@umijs/max';
import { theme as antdTheme } from 'antd';
import { CircleX } from 'lucide-react';
import React, { useEffect, useMemo, useRef } from 'react';
import { renderEmpty } from '@/components/EmptyState';
import { buildAntdTheme } from './antd';
import { SANS_FONT } from './fonts';
import {
  buildPalette,
  CARD_SHADOW,
  type Palette,
  type PresetKey,
  type ThemeMode,
} from './palette';
import { toggleThemeMode, useThemeMode, useThemePreset } from './store';

/** 当前的主题模式、主题色与令牌，以及切换函数 */
export function useAppTheme(): {
  mode: ThemeMode;
  preset: PresetKey;
  palette: Palette;
  toggle: () => void;
} {
  const mode = useThemeMode();
  const preset = useThemePreset();
  const palette = useMemo(() => buildPalette(mode, preset), [mode, preset]);
  return { mode, preset, palette, toggle: toggleThemeMode };
}

/** 日期、下拉的清除按钮图标：读屏读作「清除」 */
const CLEAR_ICON = <CircleX size="1em" aria-label="清除" />;

/** 焦点框外圈：浅色下主题色本身对白底不够 3:1（琥珀只有 1.93），外侧再加一圈 #637381 */
const FOCUS_OUTER = '#637381';

/** 全站共用的样式：焦点框、状态标签、卡片与弹出层、等宽数字、减少动效 */
const globalCss = (
  p: Palette,
  mode: ThemeMode,
  link: { hover: string; active: string },
) => {
  const light = mode === 'light';
  const focus = light
    ? `outline: 2px solid ${p.primary}; outline-offset: 0; box-shadow: 0 0 0 4px ${FOCUS_OUTER};`
    : `outline: 2px solid ${p.link}; outline-offset: 0;`;
  const focusImportant = focus.replace(/;/g, ' !important;');
  const focusInset = light
    ? `outline: 2px solid ${p.primary} !important; outline-offset: -2px !important; box-shadow: inset 0 0 0 4px ${FOCUS_OUTER} !important;`
    : `outline: 2px solid ${p.link} !important; outline-offset: -2px !important;`;
  const tag = (fg: string, bg: string) =>
    `color: ${fg} !important; background: ${bg} !important; border-color: transparent !important;`;
  return `
  :root { ${Object.entries(p)
    .map(([k, v]) => `--z-${k}: ${v};`)
    .join(' ')} }
  body { background: ${p.canvas}; font-family: ${SANS_FONT}; }
  /* 中文只用 400/600（检查表 #7）：<b>、<strong> 浏览器默认 700，ProForm 分组标题写死 bold，标题标签和 <dt> 在 antd 重置里是 500，这里兜底 */
  b, strong, h1, h2, h3, h4, h5, h6, .ant-pro-form-group-title { font-weight: 600; }
  dt { font-weight: 400; }
  /* 深色模式下让滚动条等浏览器原生控件也用深色 */
  :root[data-theme='dark'] { color-scheme: dark; }
  /* 焦点框：antd 自己的焦点样式优先级更高（如 .ant-btn:not(:disabled):focus-visible），这里统一提高优先级覆盖 */
  html body a:focus-visible, html body button:focus-visible, html body [role='button']:focus-visible,
  html body [role='switch']:focus-visible, html body [role='radio']:focus-visible,
  html body [tabindex]:not(.ant-menu):focus-visible, html body .ant-btn:focus-visible,
  html body .ant-switch:focus-visible, html body .ant-checkbox-input:focus-visible + .ant-checkbox-inner,
  html body .ant-radio-input:focus-visible + .ant-radio-inner { ${focusImportant} }
  /* 页签撑满顶栏、处在横向滚动容器里：焦点框向内画，不被裁掉。
     带 tabindex 的页签同时命中上面的 [tabindex] 规则，这里补 [tabindex] 使优先级相同、写在后面的生效 */
  html body [role='tab']:focus-visible, html body [role='tab'][tabindex]:focus-visible { ${focusInset} }
  html { scroll-padding-top: 96px; }
  /* 输入类控件获得焦点：鼠标点进去也会出现，不用按钮那套粗焦点框（外加灰圈显得重），
     改为边框换主题色 + 3px 主题色淡光晕。带图标、清除按钮的输入框画在外层 affix-wrapper 上，
     里面的 input 不再画，否则出现框中框 */
  html body .ant-select-focused, html body .ant-picker-focused, html body .ant-input-affix-wrapper-focused,
  html body .ant-input:not(.ant-input-affix-wrapper *):focus, html body .ant-input-number-focused,
  html body .ant-cascader-focused, html body .ant-mentions-focused {
    outline: none !important; border-color: ${light ? p.primary : p.link} !important;
    box-shadow: 0 0 0 3px color-mix(in srgb, ${light ? p.primary : p.link} 28%, transparent) !important;
  }
  .ant-table-thead th[tabindex]:focus-visible { ${focusImportant} }
  .ant-input-clear-icon, .ant-select-clear { padding: 6px; margin: -6px; box-sizing: content-box; }
  /* 深色下主题原色当文字对比度不够，这几处文字改用链接色 */
  .ant-pro-query-filter-collapse-button,
  .ant-tabs .ant-tabs-tab.ant-tabs-tab-active .ant-tabs-tab-btn { color: ${p.link}; font-weight: 600; }
  /* 页签：没选中的用次要文字色，选中 600 字重，选中的不会比没选中的浅（照 R27 原型，D72） */
  .ant-tabs .ant-tabs-tab .ant-tabs-tab-btn { color: ${p.sub}; }
  .ant-tabs .ant-tabs-tab:not(.ant-tabs-tab-active):not(.ant-tabs-tab-disabled):hover .ant-tabs-tab-btn { color: ${p.ink}; }
  /* 视图页签（D72，对比图 R31 方案 B）：页签栏是一条白卡；紧接着是搜索区时做它的卡头（搜索区找到它后加 data-attached） */
  .zhul-view-tabs > .ant-tabs-nav { margin: 0 0 16px; padding: 0 20px; background: ${p.card}; border-radius: 10px; ${mode === 'light' ? `box-shadow: ${CARD_SHADOW};` : `border: 1px solid ${p.hairline};`} }
  .zhul-view-tabs > .ant-tabs-nav::before { display: none; }
  .zhul-view-tabs[data-attached] > .ant-tabs-nav { margin-bottom: 0; border-radius: 10px 10px 0 0; clip-path: inset(-32px -32px 0 -32px); ${mode === 'light' ? '' : 'border-bottom: none;'} }
  .zhul-view-tabs[data-attached] > .ant-tabs-nav::before { display: block; border-bottom-color: ${p.hairline}; }
  .zhul-view-tabs > .ant-tabs-nav .ant-tabs-tab { padding: 13px 0; }
  /* 滚动条细条化（评审 S2）：Windows 默认是两端带箭头的粗灰条；滑块浅灰、轨道透明，深色主题随令牌变深 */
  * { scrollbar-width: thin; scrollbar-color: color-mix(in srgb, ${p.faint} 60%, transparent) transparent; }
  /* 状态标签：语义色 12% 浅底 + 语义色字，12px，高 24，圆角 4；antd 的颜色名归到语义色，不带颜色的为中性（见 antd.ts 的 Tag 令牌） */
  .ant-tag { font-size: 12px; line-height: 22px; border-radius: 4px; padding-inline: 8px; }
  html body .ant-tag.ant-tag-success, html body .ant-tag.ant-tag-green, html body .ant-tag.ant-tag-lime { ${tag(p.green, p.greenSoft)} }
  html body .ant-tag.ant-tag-warning, html body .ant-tag.ant-tag-orange, html body .ant-tag.ant-tag-gold, html body .ant-tag.ant-tag-volcano { ${tag(p.orange, p.orangeSoft)} }
  html body .ant-tag.ant-tag-error, html body .ant-tag.ant-tag-red, html body .ant-tag.ant-tag-magenta { ${tag(p.red, p.redSoft)} }
  html body .ant-tag.ant-tag-processing, html body .ant-tag.ant-tag-blue, html body .ant-tag.ant-tag-cyan, html body .ant-tag.ant-tag-geekblue, html body .ant-tag.ant-tag-purple { ${tag(p.info, p.infoSoft)} }
  /* 表头 13 号次要色、不加粗（中文只用 400/600，层次靠字号和颜色；ui-redesign D21、S11） */
  .ant-table-wrapper .ant-table-thead > tr > th { font-size: 13px; font-weight: 400; }
  /* 主题色实底上的勾：琥珀底上白勾看不清，与主按钮字同色 */
  .ant-checkbox-checked .ant-checkbox-inner::after { border-color: ${p.onPrimary}; }
  /* 分页（ui-redesign D12）：左侧每页条数，右侧圆形页码；当前页主题色实底、主按钮同色字（浅底与白底只差 1.07:1，看不出在哪页） */
  .ant-pagination { display: flex; flex-wrap: wrap; align-items: center; row-gap: 8px; }
  .ant-pagination .ant-pagination-options { order: -1; margin-inline: 0 auto; }
  .ant-pagination .ant-pagination-total-text:empty { display: none; }
  /* 只有一页且不超过 10 条时不显示分页（规范「组件标准 · 分页」）：页码只有 1、表格不到第 11 行，页面不用传总数。
     不能按「没有页码 2」判断：页数多时翻到后面，页码折成「1 … 8 9 10 … 18」也没有 2，分页会整个消失（UI-122） */
  .ant-table-wrapper:not(:has(.ant-table-tbody > tr:nth-child(11 of .ant-table-row))) .ant-table-pagination:not(:has(.ant-pagination-item:not(.ant-pagination-item-1))) { display: none; }
  .ant-pagination .ant-pagination-item, .ant-pagination .ant-pagination-prev, .ant-pagination .ant-pagination-next,
  .ant-pagination .ant-pagination-jump-prev, .ant-pagination .ant-pagination-jump-next {
    min-width: 32px; height: 32px; line-height: 30px; border-radius: 16px; border-color: transparent;
  }
  .ant-pagination .ant-pagination-item-active, .ant-pagination .ant-pagination-item-active:hover { background: ${p.primary}; border-color: transparent; font-weight: 600; }
  .ant-pagination .ant-pagination-item-active a, .ant-pagination .ant-pagination-item-active:hover a { color: ${p.onPrimary}; }
  /* 每页条数下拉、跳页输入框与页码同高 32 */
  .ant-pagination .ant-pagination-options .ant-select { height: 32px; }
  .ant-pagination .ant-pagination-options-quick-jumper input { height: 32px; }
  /* 页数不多（页码没有省略号）时不显示跳页 */
  .ant-pagination:not(:has(.ant-pagination-jump-next, .ant-pagination-jump-prev)) .ant-pagination-options-quick-jumper { display: none; }
  /* 行内提醒（rowAlert）：首个单元格左侧 3px 色条，不整行铺底色 */
  .ant-table-tbody > tr.zhul-row-alert-error > td:first-child { box-shadow: inset 3px 0 0 ${p.red}; }
  .ant-table-tbody > tr.zhul-row-alert-warning > td:first-child { box-shadow: inset 3px 0 0 ${p.orange}; }
  /* 悬停复制（CopyText，D27）：复制图标平时隐藏，悬停内容或键盘聚焦时出现；占位不变，出现时不挤动文字 */
  .zhul-copy { display: inline-flex; align-items: center; gap: 2px; max-width: 100%; }
  .zhul-copy-btn { all: unset; flex: none; display: inline-grid; place-items: center; width: 20px; height: 20px; border-radius: 4px; color: ${p.mute}; cursor: pointer; opacity: 0; transition: opacity .15s; }
  .zhul-copy:hover .zhul-copy-btn, .zhul-copy:focus-within .zhul-copy-btn { opacity: 1; }
  .zhul-copy-btn:hover { color: ${p.ink}; background: ${p.neutralSoft}; }
  .zhul-copy-btn:focus-visible { outline: 2px solid ${p.link}; outline-offset: 1px; }
  @media (hover: none) { .zhul-copy-btn { opacity: 1; } }
  /* 卡片：浅色为阴影、不加边框，深色为边框、不用阴影；卡片里再套的卡片不重复加阴影 */
  ${
    light
      ? `.ant-card { border-color: transparent; box-shadow: ${CARD_SHADOW}; }
  .ant-card .ant-card { border-color: ${p.hairline}; box-shadow: none; }`
      : `.ant-card { border-color: ${p.hairline}; box-shadow: none; }`
  }
  /* 页面自己画的卡片（zhul-card，UI-118）：与 Panel 同一套令牌——圆角 10，浅色阴影、深色描边；
     放在卡片、弹窗、抽屉里时不再加阴影，改细描边，不做成卡里套浮起的卡 */
  .zhul-card { background: ${p.card}; border-radius: 10px; ${light ? `box-shadow: ${CARD_SHADOW}; border: none;` : `border: 1px solid ${p.hairline};`} }
  .zhul-card .zhul-card, .ant-card .zhul-card, .ant-modal .zhul-card, .ant-drawer .zhul-card { box-shadow: none; border: 1px solid ${p.hairline}; }
  /* 矩阵、热力表的可点格子（覆盖矩阵，R38 方案 B）：白底，悬停浅灰，不整格铺色 */
  .zhul-matrix-cell { transition: background .15s; }
  .zhul-matrix-cell:hover, .zhul-matrix-cell:focus-visible { background: ${p.inset}; }
  /* 深色弹出层：#222222 底加 1px #3A3A3A 边，和下面的卡片分开；提示气泡同样加边 */
  ${
    light
      ? ''
      : `.ant-dropdown-menu, .ant-popover-container, .ant-select-dropdown, .ant-cascader-dropdown,
  .ant-picker-dropdown .ant-picker-panel-container, .ant-tooltip-container,
  .ant-menu-submenu-popup > .ant-menu { border: 1px solid ${p.elevatedLine}; }`
  }
  .num { font-variant-numeric: tabular-nums; white-space: nowrap; }
  /* 表格里的数字一律等宽（检查表 #8）：金额、数量、日期上下对齐；只影响数字字形，文字不变（UI-117） */
  .ant-table, .zhul-grid-table { font-variant-numeric: tabular-nums; }
  /* 浏览器自动填充（账号、密码）会给输入框加浅蓝底：改回主题的输入框底色与文字色，深色模式下不再出现亮色块 */
  input:-webkit-autofill, input:-webkit-autofill:hover, input:-webkit-autofill:focus, input:-webkit-autofill:active {
    -webkit-box-shadow: 0 0 0 1000px ${p.card} inset !important;
    -webkit-text-fill-color: ${p.ink} !important;
    caret-color: ${p.ink};
    transition: background-color 9999s ease-out 0s;
  }
  /* 看起来像链接的命令按钮：用原生 button，键盘 Tab、Enter、空格都能用（UI-022） */
  .zhul-link {
    padding: 0; margin: 0; border: 0; background: none; font: inherit; line-height: inherit;
    text-align: inherit; color: ${p.link}; cursor: pointer; transition: color 0.3s;
  }
  .zhul-link:hover { color: ${link.hover}; }
  .zhul-link:active { color: ${link.active}; }
  /* 表格里的单号、来源单号与「查看」等文字链接（ui-redesign D23）：平时墨色，悬停主题色加下划线；单号列（首列或勾选列后一列）600。
     表格不再满屏主题色，也不和警告标签撞色；按钮（删除等）不受影响 */
  /* 自己用 grid 画的表格（供应链跟进的分组表）根节点加 zhul-grid-table，同样适用 */
  .ant-table-tbody .zhul-link, .ant-table-tbody a:not(.ant-btn),
  .zhul-grid-table .zhul-link, .zhul-grid-table a:not(.ant-btn) { color: ${p.ink}; }
  .ant-table-tbody .zhul-link:hover, .ant-table-tbody a:not(.ant-btn):hover,
  .zhul-grid-table .zhul-link:hover, .zhul-grid-table a:not(.ant-btn):hover {
    color: ${link.hover}; text-decoration: underline; text-underline-offset: 3px;
  }
  .ant-table-tbody > tr > td:first-child .zhul-link, .ant-table-tbody > tr > td:first-child a:not(.ant-btn),
  .ant-table-tbody > tr > td.ant-table-selection-column + td .zhul-link,
  .ant-table-tbody > tr > td.ant-table-selection-column + td a:not(.ant-btn) { font-weight: 600; }
  /* D23 只管导航类链接（单号、查看）：表格里的动作文字（如「去下单 →」）加 zhul-link-action 保持链接色，一眼看出能点 */
  .ant-table-tbody .zhul-link.zhul-link-action { color: ${p.link}; }
  /* 表格外的单号（分组头、卡片标题）同样墨色 600，悬停主题色加下划线 */
  .zhul-link.zhul-link-doc { color: ${p.ink}; font-weight: 600; }
  .zhul-link.zhul-link-doc:hover { color: ${link.hover}; text-decoration: underline; text-underline-offset: 3px; }
  /* 关联单据卡的行（RelatedDocsCard）：单号同样墨色，悬停整行加深一档、单号变链接色加下划线 */
  .zhul-related-row > div { transition: background .15s; }
  .zhul-related-row:hover > div { background: ${p.neutralSoft} !important; }
  .zhul-related-row:hover .zhul-related-no { color: ${link.hover} !important; text-decoration: underline; text-underline-offset: 3px; }
  /* 表格行高（D21）：内边距 11 让两行内容落在 63；单行和表头用最小高度补到 48（单元格的 height 即最小高度）。
     small、middle 尺寸的表格用于弹窗和卡片内，不受影响 */
  .ant-table:not(.ant-table-small):not(.ant-table-middle) > .ant-table-container .ant-table-thead > tr > th,
  .ant-table:not(.ant-table-small):not(.ant-table-middle) > .ant-table-container .ant-table-tbody > tr.ant-table-row > td { height: 48px; }
  @media (max-width: 1399px) { .zhul-more-text { display: none; } }
  /* 图标统一用 lucide（规范「图标」）：和文字同行时与 antd 图标原来一样下沉 0.125em 居中；flex 布局里不受影响。
     Tailwind 基础样式把 svg 设成 display: block，放在普通文字里会独占一行（如水单附件「回形针 + 文件名」），这里改回行内。
     转圈（加载、同步中）加 zhul-spin */
  svg.lucide { display: inline-block; vertical-align: -0.125em; flex: none; }
  .zhul-spin { animation: zhul-spin 1s linear infinite; }
  @keyframes zhul-spin { to { transform: rotate(360deg); } }
  @media (prefers-reduced-motion: reduce) { .zhul-spin { animation: none; } }
  /* 主卡操作行里的快捷筛选：结果数后面直接是胶囊，不写「快捷筛选」 */
  .zhul-toolbar .zhul-quick-label { display: none; }
  /* 卡片里可点的明细行：悬停浅底（同待办行） */
  .zhul-row-hover { transition: background .2s; border-radius: 8px; }
  .zhul-row-hover:hover { background: ${p.hover} !important; }
  /* 卡片标题行右侧的链接：平时次要色，悬停才用链接色 */
  .zhul-link.zhul-link-quiet { color: ${p.mute}; }
  .zhul-link.zhul-link-quiet:hover { color: ${link.hover}; }
  .zhul-skip {
    position: absolute; left: 16px; top: 12px; z-index: 1100; padding: 8px 16px; border-radius: 8px;
    display: inline-flex; align-items: center; gap: 6px; font-weight: 600;
    background: ${p.card}; color: ${p.ink}; ${focus}
    clip-path: inset(50%); width: 1px; height: 1px; overflow: hidden; white-space: nowrap;
  }
  .zhul-skip:focus { clip-path: none; width: auto; height: auto; overflow: visible; }
  /* 只给读屏看的文字；原生单选框隐藏，焦点框画在后面的可见内容上 */
  .zhul-sr-only, .zhul-radio {
    position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0; border: 0;
    overflow: hidden; clip-path: inset(50%); white-space: nowrap; opacity: 0;
  }
  .zhul-radio:focus-visible + * { ${focus} }
  @media (prefers-reduced-motion: reduce) {
    *, *::before, *::after { transition: none !important; animation: none !important; scroll-behavior: auto !important; }
  }
`;
};

/**
 * 把主题同步给 Ant Design（经 umi 的 antd 插件，保证 App、弹窗、消息提示也拿到同一份主题），
 * 并注入全站样式。挂在 innerProvider 上，所有页面（含没有外壳的向导页）都在它里面。
 */
export const AppThemeSync: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const setAntdConfig = useAntdConfigSetter();
  const { mode, preset, palette } = useAppTheme();
  const css = useMemo(() => {
    const t = antdTheme.getDesignToken(buildAntdTheme(mode, preset));
    return globalCss(palette, mode, {
      hover: t.colorLinkHover,
      active: t.colorLinkActive,
    });
  }, [mode, preset, palette]);

  // setter 的引用会随 antd 配置变化，放进依赖会形成「设置配置 → 换引用 → 再设置」的死循环，所以用 ref 只在主题变化时触发
  const setterRef = useRef(setAntdConfig);
  setterRef.current = setAntdConfig;

  useEffect(() => {
    // 没写 emptyText 的表格、下拉也用统一的空状态（ui-redesign C4）；
    // 日期、下拉的清除按钮用 lucide 图标并读作「清除」（antd 自带的图标读屏会读成英文 close-circle）
    setterRef.current({
      theme: buildAntdTheme(mode, preset),
      renderEmpty,
      datePicker: { clearIcon: CLEAR_ICON },
      timePicker: { clearIcon: CLEAR_ICON },
      select: { clearIcon: CLEAR_ICON },
    });
    document.documentElement.dataset.theme = mode;
  }, [mode, preset]);

  return (
    <>
      <style>{css}</style>
      {children}
    </>
  );
};
