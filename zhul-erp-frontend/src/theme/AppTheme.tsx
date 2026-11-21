import { useAntdConfigSetter } from '@umijs/max';
import { theme as antdTheme } from 'antd';
import React, { useEffect, useMemo, useRef } from 'react';
import { buildAntdTheme } from './antd';
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
  const tag = (fg: string, bg: string) =>
    `color: ${fg} !important; background: ${bg} !important; border-color: transparent !important;`;
  return `
  :root { ${Object.entries(p)
    .map(([k, v]) => `--z-${k}: ${v};`)
    .join(' ')} }
  body { background: ${p.canvas}; }
  /* 深色模式下让滚动条等浏览器原生控件也用深色 */
  :root[data-theme='dark'] { color-scheme: dark; }
  a:focus-visible, button:focus-visible, [role='button']:focus-visible, [role='switch']:focus-visible,
  [role='radio']:focus-visible, [tabindex]:focus-visible, .ant-btn:focus-visible, .ant-switch:focus-visible { ${focus} }
  .ant-select-focused, .ant-picker-focused, .ant-input-affix-wrapper-focused, .ant-input:focus-visible,
  .ant-input-number-focused, .ant-table-thead th[tabindex]:focus-visible { ${focus} }
  .ant-input-clear-icon, .ant-select-clear { padding: 6px; margin: -6px; box-sizing: content-box; }
  /* 深色下主题原色当文字对比度不够，这几处文字改用链接色 */
  .ant-pro-query-filter-collapse-button,
  .ant-tabs .ant-tabs-tab.ant-tabs-tab-active .ant-tabs-tab-btn { color: ${p.link}; }
  /* 状态标签：语义色 12% 浅底 + 语义色字，12px，高 24，圆角 4；antd 的颜色名归到语义色，不带颜色的为中性（见 antd.ts 的 Tag 令牌） */
  .ant-tag { font-size: 12px; line-height: 22px; border-radius: 4px; padding-inline: 8px; }
  html body .ant-tag.ant-tag-success, html body .ant-tag.ant-tag-green, html body .ant-tag.ant-tag-lime { ${tag(p.green, p.greenSoft)} }
  html body .ant-tag.ant-tag-warning, html body .ant-tag.ant-tag-orange, html body .ant-tag.ant-tag-gold, html body .ant-tag.ant-tag-volcano { ${tag(p.orange, p.orangeSoft)} }
  html body .ant-tag.ant-tag-error, html body .ant-tag.ant-tag-red, html body .ant-tag.ant-tag-magenta { ${tag(p.red, p.redSoft)} }
  html body .ant-tag.ant-tag-processing, html body .ant-tag.ant-tag-blue, html body .ant-tag.ant-tag-cyan, html body .ant-tag.ant-tag-geekblue, html body .ant-tag.ant-tag-purple { ${tag(p.info, p.infoSoft)} }
  /* 卡片：浅色为阴影、不加边框，深色为边框、不用阴影；卡片里再套的卡片不重复加阴影 */
  ${
    light
      ? `.ant-card { border-color: transparent; box-shadow: ${CARD_SHADOW}; }
  .ant-card .ant-card { border-color: ${p.hairline}; box-shadow: none; }`
      : `.ant-card { border-color: ${p.hairline}; box-shadow: none; }`
  }
  /* 深色弹出层：#222222 底加 1px #3A3A3A 边，和下面的卡片分开；提示气泡同样加边 */
  ${
    light
      ? ''
      : `.ant-dropdown-menu, .ant-popover-inner, .ant-select-dropdown, .ant-cascader-dropdown,
  .ant-picker-dropdown .ant-picker-panel-container, .ant-tooltip-inner { border: 1px solid ${p.elevatedLine}; }`
  }
  .num { font-variant-numeric: tabular-nums; white-space: nowrap; }
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
  .zhul-skip {
    position: absolute; left: 16px; top: 12px; z-index: 1100; padding: 8px 16px; border-radius: 8px;
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
    setterRef.current({ theme: buildAntdTheme(mode, preset) });
    document.documentElement.dataset.theme = mode;
  }, [mode, preset]);

  return (
    <>
      <style>{css}</style>
      {children}
    </>
  );
};
