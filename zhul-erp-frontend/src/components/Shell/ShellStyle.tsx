import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { CARD_SHADOW, POPUP_SHADOW } from '@/theme/palette';

/**
 * 外壳样式（外壳原型 F1、F1b）：ProLayout 的侧栏做成悬浮卡片，菜单项照参考站。
 * - 侧栏卡片宽 244、左上下边距 16、圆角 10；浅色阴影不加边框，深色加边框不用阴影；
 * - 菜单项高 40、圆角 8、字号 16、图标 20；展开的父级为中性浅底加主题色字；
 * - 子项前面一个空心小圆点，选中的子项为主题色浅底、主题色字、实心小圆点；
 * - 收起后为 64 宽图标栏，当前所在的菜单组为主题色浅底，悬停弹出子菜单。
 */
export const ShellStyle: React.FC = () => {
  const { palette: p, mode } = useAppTheme();
  const light = mode === 'light';
  const css = `
  .ant-pro-sider.ant-layout-sider { background: transparent !important; border-inline-end: 0 !important; }
  .ant-pro-layout .ant-pro-sider > .ant-layout-sider-children {
    margin: 16px 0 16px 16px; height: calc(100% - 32px) !important; box-sizing: border-box;
    padding: 16px 14px; border-radius: 10px; overflow: hidden; background: ${p.sidebar};
    box-shadow: ${light ? CARD_SHADOW : 'none'}; border: ${light ? 'none' : `1px solid ${p.hairline}`};
    border-inline-end: ${light ? '0' : `1px solid ${p.hairline}`}; margin-inline-end: 0;
  }
  .ant-pro-layout .ant-pro-sider.ant-layout-sider-collapsed > .ant-layout-sider-children { padding: 16px 0; }
  .ant-pro-sider .ant-pro-sider-logo { padding: 0 0 14px !important; height: auto !important; cursor: default; border: 0 !important; }
  .ant-pro-sider.ant-layout-sider-collapsed .ant-pro-sider-logo { justify-content: center; }
  .ant-pro-sider .ant-menu { background: transparent !important; border-inline-end: 0 !important; }
  .ant-pro-sider .ant-menu-item, .ant-pro-sider .ant-menu-submenu-title {
    height: 40px !important; line-height: 40px !important; margin: 2px 0 !important; width: 100% !important;
    border-radius: 8px !important; font-size: 16px; color: ${p.sub};
  }
  .ant-pro-sider .ant-menu-item .anticon, .ant-pro-sider .ant-menu-submenu-title .anticon { font-size: 20px; }
  .ant-pro-sider .ant-menu-submenu-open > .ant-menu-submenu-title,
  .ant-pro-sider .ant-menu-submenu-selected > .ant-menu-submenu-title { color: ${p.link} !important; }
  .ant-pro-sider .ant-menu-submenu-open > .ant-menu-submenu-title { background: ${p.navOn} !important; }
  .ant-pro-sider .ant-menu-item-selected { background: ${p.primarySoft} !important; color: ${p.link} !important; }
  /* 小圆点挂在 ProLayout 的标题容器（flex）上，与文字垂直居中 */
  .ant-pro-sider .ant-menu-sub .ant-menu-item [class*='-item-title']::before {
    content: ''; flex: none; box-sizing: border-box; width: 8px; height: 8px; margin-inline-end: 12px; border-radius: 50%; border: 1.5px solid ${p.faint};
  }
  .ant-pro-sider .ant-menu-sub .ant-menu-item-selected [class*='-item-title']::before {
    border-color: ${p.link}; background: ${p.link}; box-shadow: inset 0 0 0 1.5px ${p.primarySoft};
  }
  .ant-pro-sider.ant-layout-sider-collapsed .ant-menu-item,
  .ant-pro-sider.ant-layout-sider-collapsed .ant-menu-submenu-title {
    width: 40px !important; margin: 4px auto !important; padding: 0 !important; display: flex; justify-content: center; align-items: center;
  }
  .ant-pro-sider.ant-layout-sider-collapsed .ant-menu-submenu-selected > .ant-menu-submenu-title { background: ${p.primarySoft} !important; }
  /* 收起时只留图标：菜单组的展开箭头（app.tsx 的 expandIcon）不显示，否则挤偏图标、伸出 40 宽的按钮被截一半 */
  .ant-pro-sider.ant-layout-sider-collapsed .ant-menu-submenu-title > svg,
  .ant-pro-sider.ant-layout-sider-collapsed .ant-menu-submenu-arrow { display: none !important; }
  /* 浮出展开：ProLayout 按展开渲染，占位仍是收起时的宽度，侧栏浮在内容上 */
  [data-sider-peek='true'] > div:first-child {
    width: 80px !important; min-width: 80px !important; max-width: 80px !important; flex: 0 0 80px !important;
  }
  [data-sider-peek='true'] > .ant-pro-sider { z-index: 120 !important; }
  .ant-pro-layout [data-sider-peek='true'] > .ant-pro-sider > .ant-layout-sider-children {
    box-shadow: ${light ? POPUP_SHADOW : 'none'}; border: ${light ? 'none' : `1px solid ${p.elevatedLine}`};
  }
  /* 图标栏：菜单图标 20，与展开时一致 */
  .ant-pro-sider.ant-layout-sider-collapsed .ant-menu-item [class*='-item-icon'],
  .ant-pro-sider.ant-layout-sider-collapsed .ant-menu-submenu-title [class*='-item-icon'] {
    width: 20px !important; height: 20px !important; font-size: 20px !important; line-height: 20px !important;
  }
  .ant-pro-sider.ant-layout-sider-collapsed .anticon { font-size: 20px !important; }
  /* 收起后悬停弹出的子菜单（挂在 body 下）：弹出层样式，子项照展开时 */
  .ant-menu-submenu-popup { backdrop-filter: none !important; background: transparent !important; }
  .ant-menu-submenu-popup > .ant-menu {
    padding: 6px !important; border-radius: 8px !important; background: ${p.elevated} !important;
    box-shadow: ${light ? POPUP_SHADOW : 'none'} !important;
  }
  .ant-menu-submenu-popup .ant-menu-item {
    height: 36px !important; line-height: 36px !important; margin: 0 !important; width: 100% !important;
    border-radius: 6px !important; color: ${p.sub};
  }
  .ant-menu-submenu-popup .ant-menu-item-selected { background: ${p.primarySoft} !important; color: ${p.link} !important; }
  .ant-menu-submenu-popup .ant-menu-item [class*='-item-title']::before {
    content: ''; flex: none; box-sizing: border-box; width: 8px; height: 8px; margin-inline-end: 10px; border-radius: 50%; border: 1.5px solid ${p.faint};
  }
  .ant-menu-submenu-popup .ant-menu-item-selected [class*='-item-title']::before {
    border-color: ${p.link}; background: ${p.link}; box-shadow: inset 0 0 0 1.5px ${p.primarySoft};
  }
  `;
  return <style>{css}</style>;
};
