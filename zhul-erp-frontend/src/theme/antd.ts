import { theme as antdTheme, type ThemeConfig } from 'antd';
import { PALETTE, type ThemeMode } from './palette';

/** 由主题模式生成 Ant Design 的主题配置（令牌见 ui-design-patterns.md「Ant Design 落地」） */
export function buildAntdTheme(mode: ThemeMode): ThemeConfig {
  const p = PALETTE[mode];
  return {
    algorithm:
      mode === 'dark' ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: {
      colorPrimary: '#2563EB',
      colorLink: p.link,
      colorBgLayout: p.canvas,
      colorBgContainer: p.card,
      colorBgElevated: p.card,
      colorBorder: p.control,
      colorBorderSecondary: p.hairline,
      colorText: p.ink,
      colorTextSecondary: p.sub,
      colorTextTertiary: p.mute,
      colorTextQuaternary: p.mute,
      colorTextDescription: p.mute,
      colorTextPlaceholder: p.mute,
      colorSuccess: p.green,
      colorWarning: p.orange,
      colorError: p.red,
      borderRadius: 10,
      borderRadiusLG: 16,
      controlHeight: 36,
      fontFamily:
        "'Noto Sans SC', AlibabaSans, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
    },
    components: {
      Table: { headerBg: p.card, rowHoverBg: p.hover, borderColor: p.hairline },
      Card: { headerFontSize: 16 },
      // 深色菜单默认选中项是实心主色，这里改成强调色浅底 + 链接色文字
      Menu: {
        darkItemBg: p.sidebar,
        darkSubMenuItemBg: p.sidebar,
        darkItemColor: p.sub,
        darkItemHoverBg: p.hover,
        darkItemHoverColor: p.ink,
        darkItemSelectedBg: p.accentSoft,
        darkItemSelectedColor: p.link,
        itemSelectedBg: p.accentSoft,
        itemSelectedColor: p.link,
      },
      // 交互目标不小于 24×24：开关 44×24
      Switch: { trackHeight: 24, handleSize: 20, trackMinWidth: 44 },
    },
  };
}
