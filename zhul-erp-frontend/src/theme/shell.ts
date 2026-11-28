import type { ProLayoutProps } from '@ant-design/pro-components';
import { buildPalette, type PresetKey, type ThemeMode } from './palette';

/**
 * 外壳（侧栏、内容区）的样式令牌：侧栏为悬浮卡片（外层透明，卡片样式见 components/Shell/ShellStyle），
 * 当前菜单项用主题色浅底加链接色文字，内容区左右内边距 16（顶栏自带上方 16 的间距）。
 * 深色不再用 ProLayout 的 realDark（会再叠一次深色算法、压暗主题色，D71），外壳的深色全部来自这里的令牌和根部 antd 主题。
 */
export function buildShellSettings(
  mode: ThemeMode,
  preset?: PresetKey,
): Partial<ProLayoutProps> {
  const p = buildPalette(mode, preset);
  return {
    // 不用 realDark：它会在全站深色算法之后再叠一次 antd 深色算法，把主题色压暗（琥珀 #FDA92D → #DA9329，D71）。
    // 深浅色已由根部 antd 主题（theme/antd.ts）决定，外壳只取令牌
    navTheme: 'light',
    colorPrimary: p.primary,
    layout: 'side',
    fixedHeader: true,
    fixSiderbar: true,
    token: {
      bgLayout: p.canvas,
      header: {
        colorBgHeader: 'transparent',
        heightLayoutHeader: 60,
        colorHeaderTitle: p.ink,
        colorTextRightActionsItem: p.sub,
        colorTextMenu: p.sub,
        colorTextMenuSecondary: p.mute,
        colorTextMenuSelected: p.link,
        colorBgMenuItemSelected: p.primarySoft,
      },
      sider: {
        colorMenuBackground: 'transparent',
        colorMenuItemDivider: p.hairline,
        colorBgMenuItemHover: p.hover,
        colorBgMenuItemSelected: p.primarySoft,
        colorTextMenu: p.sub,
        colorTextMenuSecondary: p.mute,
        colorTextMenuTitle: p.ink,
        colorTextMenuActive: p.ink,
        colorTextMenuItemHover: p.ink,
        colorTextMenuSelected: p.link,
        colorTextSubMenuSelected: p.link,
        colorBgCollapsedButton: p.card,
        colorTextCollapsedButton: p.sub,
        paddingInlineLayoutMenu: 0,
        paddingBlockLayoutMenu: 0,
      },
      pageContainer: {
        colorBgPageContainer: 'transparent',
        paddingInlinePageContainerContent: 16,
        paddingBlockPageContainerContent: 0,
      },
    },
  };
}
