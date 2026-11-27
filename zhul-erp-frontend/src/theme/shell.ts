import type { ProLayoutProps } from '@ant-design/pro-components';
import { buildPalette, type PresetKey, type ThemeMode } from './palette';

/**
 * 外壳（侧栏、内容区）的样式令牌：侧栏为悬浮卡片（外层透明，卡片样式见 components/Shell/ShellStyle），
 * 当前菜单项用主题色浅底加链接色文字，内容区左右内边距 16（顶栏自带上方 16 的间距）。
 * 深色时用 ProLayout 的 realDark，让它自己的组件也走深色分支。
 */
export function buildShellSettings(
  mode: ThemeMode,
  preset?: PresetKey,
): Partial<ProLayoutProps> {
  const p = buildPalette(mode, preset);
  return {
    navTheme: mode === 'dark' ? 'realDark' : 'light',
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
