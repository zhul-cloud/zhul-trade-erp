import { useAntdConfigSetter } from '@umijs/max';
import React, { useEffect, useMemo, useRef } from 'react';
import { buildAntdTheme } from './antd';
import {
  PALETTE,
  type Palette,
  PRIMARY_GRADIENT,
  type ThemeMode,
} from './palette';
import { toggleThemeMode, useThemeMode } from './store';

/** 当前的主题模式与令牌，以及切换函数 */
export function useAppTheme(): {
  mode: ThemeMode;
  palette: Palette;
  toggle: () => void;
} {
  const mode = useThemeMode();
  return { mode, palette: PALETTE[mode], toggle: toggleThemeMode };
}

/** 全站共用的样式：主按钮渐变、焦点环、等宽数字、减少动效 */
const globalCss = (p: Palette) => `
  :root { ${Object.entries(p)
    .map(([k, v]) => `--z-${k}: ${v};`)
    .join(' ')} }
  body { background: ${p.canvas}; }
  .ant-btn-primary:not(:disabled):not(.ant-btn-dangerous):not(.ant-btn-background-ghost) {
    background: ${PRIMARY_GRADIENT};
    border: none;
    box-shadow: 0 8px 26px rgba(59, 130, 246, 0.35);
  }
  a:focus-visible, button:focus-visible, [role='button']:focus-visible, [role='switch']:focus-visible,
  [role='radio']:focus-visible, [tabindex]:focus-visible, .ant-btn:focus-visible, .ant-switch:focus-visible {
    outline: 2px solid ${p.link};
    outline-offset: 2px;
  }
  .ant-select:focus-within, .ant-picker:focus-within, .ant-input-affix-wrapper:focus-within,
  .ant-input-number:focus-within, .ant-table-thead th[tabindex]:focus-visible {
    outline: 2px solid ${p.link};
    outline-offset: 2px;
  }
  .ant-input-clear-icon, .ant-select-clear { padding: 6px; margin: -6px; box-sizing: content-box; }
  /* 主色 #2563EB 直接当文字放在深色卡片上对比度不够，这几处文字改用链接色 */
  .ant-pro-query-filter-collapse-button,
  .ant-tabs .ant-tabs-tab.ant-tabs-tab-active .ant-tabs-tab-btn { color: ${p.link}; }
  /* 状态标签统一用语义色 + 浅底，浅色下 antd 预设色的对比度不够 */
  html body .ant-tag.ant-tag-success, html body .ant-tag.ant-tag-green { color: ${p.green} !important; background: ${p.greenSoft} !important; border-color: transparent !important; }
  html body .ant-tag.ant-tag-warning, html body .ant-tag.ant-tag-orange { color: ${p.orange} !important; background: ${p.orangeSoft} !important; border-color: transparent !important; }
  html body .ant-tag.ant-tag-error, html body .ant-tag.ant-tag-red { color: ${p.red} !important; background: ${p.redSoft} !important; border-color: transparent !important; }
  html body .ant-tag.ant-tag-processing, html body .ant-tag.ant-tag-blue { color: ${p.link} !important; background: ${p.accentSoft} !important; border-color: transparent !important; }
  .num { font-variant-numeric: tabular-nums; }
  .zhul-skip {
    position: absolute; left: 16px; top: 12px; z-index: 1100; padding: 8px 16px; border-radius: 10px;
    background: ${p.card}; color: ${p.link}; border: 2px solid ${p.link};
    clip-path: inset(50%); width: 1px; height: 1px; overflow: hidden; white-space: nowrap;
  }
  .zhul-skip:focus { clip-path: none; width: auto; height: auto; overflow: visible; }
  @media (prefers-reduced-motion: reduce) {
    *, *::before, *::after { transition: none !important; animation: none !important; scroll-behavior: auto !important; }
  }
`;

/**
 * 把主题同步给 Ant Design（经 umi 的 antd 插件，保证 App、弹窗、消息提示也拿到同一份主题），
 * 并注入全站样式。挂在 innerProvider 上，所有页面（含没有外壳的向导页）都在它里面。
 */
export const AppThemeSync: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const setAntdConfig = useAntdConfigSetter();
  const { mode, palette } = useAppTheme();
  const css = useMemo(() => globalCss(palette), [palette]);

  // setter 的引用会随 antd 配置变化，放进依赖会形成「设置配置 → 换引用 → 再设置」的死循环，所以用 ref 只在 mode 变化时触发
  const setterRef = useRef(setAntdConfig);
  setterRef.current = setAntdConfig;

  useEffect(() => {
    setterRef.current({ theme: buildAntdTheme(mode) });
    document.documentElement.dataset.theme = mode;
  }, [mode]);

  return (
    <>
      <style>{css}</style>
      {children}
    </>
  );
};
