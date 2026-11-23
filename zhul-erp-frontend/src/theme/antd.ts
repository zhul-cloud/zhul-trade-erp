import { theme as antdTheme, type ThemeConfig } from 'antd';
import { SANS_FONT } from './fonts';
import {
  BUTTON_SHADOW,
  buildPalette,
  CARD_SHADOW,
  POPUP_SHADOW,
  type PresetKey,
  type ThemeMode,
} from './palette';

/** 对话框的深阴影（照参考站 Dialog，elevation 24） */
const DIALOG_SHADOW =
  '0 11px 15px -7px rgba(145,158,171,.2), 0 24px 38px 3px rgba(145,158,171,.14), 0 9px 46px 8px rgba(145,158,171,.12)';

/** 由主题模式和主题色生成 Ant Design 的主题配置（令牌见 ui-design-patterns.md「设计令牌」「组件标准」） */
export function buildAntdTheme(
  mode: ThemeMode,
  preset?: PresetKey,
): ThemeConfig {
  const p = buildPalette(mode, preset);
  const light = mode === 'light';
  return {
    algorithm: light ? antdTheme.defaultAlgorithm : antdTheme.darkAlgorithm,
    token: {
      colorPrimary: p.primary,
      colorLink: p.link,
      // 语义色：填充用原色，文字用文字色（浅色下深一档）
      colorInfo: p.infoMain,
      colorSuccess: p.greenMain,
      colorWarning: p.orangeMain,
      colorError: p.redMain,
      colorInfoText: p.info,
      colorSuccessText: p.green,
      colorWarningText: p.orange,
      colorErrorText: p.red,
      colorBgLayout: p.canvas,
      colorBgContainer: p.card,
      colorBgElevated: p.elevated,
      colorBgSpotlight: light ? p.ink : '#000000',
      colorBorder: p.control,
      colorBorderSecondary: p.hairline,
      colorSplit: p.hairline,
      colorText: p.ink,
      colorTextSecondary: p.mute,
      colorTextTertiary: p.mute,
      colorTextQuaternary: p.faint,
      colorTextDescription: p.mute,
      colorTextPlaceholder: p.faint,
      colorTextDisabled: p.faint,
      colorFillAlter: p.head,
      colorBgMask: light ? 'rgba(33,43,54,.5)' : 'rgba(0,0,0,.7)',
      // 圆角：按钮、输入框、对话框 8；卡片 10；状态标签 4
      borderRadius: 8,
      borderRadiusLG: 10,
      borderRadiusSM: 4,
      // 按钮高 36；输入类控件在下面单独设为 40
      controlHeight: 36,
      boxShadow: light ? CARD_SHADOW : 'none',
      boxShadowSecondary: light ? POPUP_SHADOW : 'none',
      boxShadowTertiary: light ? CARD_SHADOW : 'none',
      fontFamily: SANS_FONT,
    },
    components: {
      Button: {
        colorError: p.red,
        colorErrorHover: p.redMain,
        // 中文只用 400/600（Windows 微软雅黑没有 500）；琥珀主题主按钮用深色字
        fontWeight: 400,
        primaryColor: p.onPrimary,
        primaryShadow: light ? BUTTON_SHADOW : 'none',
        dangerShadow: 'none',
        defaultShadow: 'none',
      },
      Input: { controlHeight: 40 },
      InputNumber: { controlHeight: 40 },
      Select: { controlHeight: 40 },
      DatePicker: { controlHeight: 40 },
      Cascader: { controlHeight: 40 },
      TreeSelect: { controlHeight: 40 },
      Mentions: { controlHeight: 40 },
      // 字阶：卡片、弹窗标题 16/600（ui-redesign D26）
      Card: { headerFontSize: 16 },
      // 表单校验提示、危险按钮直接拿 colorError / colorWarning 当文字色：换成文字色（浅色下深一档）
      Form: { colorError: p.red, colorWarning: p.orange },
      Modal: {
        titleFontSize: 16,
        contentBg: p.card,
        headerBg: p.card,
        footerBg: p.card,
        boxShadow: light ? DIALOG_SHADOW : 'none',
      },
      // 表格：表头 48、13 号次要色；单行 48（ui-redesign D21）
      Table: {
        headerBg: p.head,
        headerColor: p.mute,
        cellFontSize: 14,
        cellPaddingBlock: 13,
        cellPaddingInline: 12,
        headerSplitColor: 'transparent',
        rowHoverBg: p.hover,
        borderColor: p.hairline,
      },
      // 状态标签：12px，圆角 4，中性为中性浅底配次要文字色；语义色见全局样式
      Tag: { defaultBg: p.neutralSoft, defaultColor: p.neutral },
      Tooltip: { colorTextLightSolid: '#FFFFFF' },
      Menu: {
        darkItemBg: p.sidebar,
        darkSubMenuItemBg: p.sidebar,
        darkItemColor: p.sub,
        darkItemHoverBg: p.hover,
        darkItemHoverColor: p.ink,
        darkItemSelectedBg: p.primarySoft,
        darkItemSelectedColor: p.link,
        itemSelectedBg: p.primarySoft,
        itemSelectedColor: p.link,
      },
      // 交互目标不小于 24×24：开关 44×24
      Switch: { trackHeight: 24, handleSize: 20, trackMinWidth: 44 },
    },
  };
}
