import type { ProLayoutProps } from '@ant-design/pro-components';
import { PALETTE, type ThemeMode } from './palette';

/**
 * 外壳（侧栏、顶栏、内容区）的样式令牌：侧栏 240 宽，顶栏 60 高且透明，
 * 当前菜单项用强调色浅底加链接色文字，内容区内边距 28。
 * 深色时用 ProLayout 的 realDark，让它自己的组件也走深色分支。
 */
export function buildShellSettings(mode: ThemeMode): Partial<ProLayoutProps> {
  const p = PALETTE[mode];
  return {
    navTheme: mode === 'dark' ? 'realDark' : 'light',
    colorPrimary: '#2563EB',
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
        colorBgMenuItemSelected: p.accentSoft,
      },
      sider: {
        colorMenuBackground: p.sidebar,
        colorMenuItemDivider: p.hairline,
        colorBgMenuItemHover: p.hover,
        colorBgMenuItemSelected: p.accentSoft,
        colorTextMenu: p.sub,
        colorTextMenuSecondary: p.mute,
        colorTextMenuTitle: p.ink,
        colorTextMenuActive: p.ink,
        colorTextMenuItemHover: p.ink,
        colorTextMenuSelected: p.link,
        colorTextSubMenuSelected: p.link,
        colorBgCollapsedButton: p.card,
        colorTextCollapsedButton: p.sub,
        paddingInlineLayoutMenu: 12,
        paddingBlockLayoutMenu: 12,
      },
      pageContainer: {
        colorBgPageContainer: 'transparent',
        paddingInlinePageContainerContent: 28,
        paddingBlockPageContainerContent: 28,
      },
    },
  };
}
