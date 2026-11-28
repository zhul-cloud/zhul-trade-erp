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
    // 深色算法会把主题色压暗一档（琥珀 #FDA92D → #DA9329），按钮、开关、分页都显得发灰；
    // 参考站深浅两种模式主按钮都是原色，这里在深色算法后把主题色钉回原色（用户 2026-11-27 指出深色下琥珀「特别暗」）
    algorithm: light
      ? antdTheme.defaultAlgorithm
      : [
          antdTheme.darkAlgorithm,
          (seed, map) => ({
            ...(map ?? antdTheme.darkAlgorithm(seed)),
            colorPrimary: p.primary,
          }),
        ],
    token: {
      colorPrimary: p.primary,
      colorLink: p.link,
      // 语义色：填充用原色，文字用文字色令牌（D53：原色，橙与撞链接色的保留深一档）
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
        // 浅色下危险实底按钮照参考模板：原色 #FF5630 白字（3.2:1，按钮字加粗且有图标或明确动作词，D53），悬停加深到 #D0301F
        colorErrorHover: light ? '#D0301F' : p.redMain,
        // 危险实底按钮的字：深色下底色是亮红（#FF6A47），白字只有 2.7:1，改深色字（6:1）
        dangerColor: light ? '#FFFFFF' : '#1A1A1A',
        // 中文只用 400/600（Windows 微软雅黑没有 500）；主按钮字统一白色（D70）
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
      // 表单校验提示直接拿 colorError / colorWarning 当文字色：统一走状态文字色令牌
      Form: { colorError: p.red, colorWarning: p.orange },
      Modal: {
        titleFontSize: 16,
        contentBg: p.card,
        headerBg: p.card,
        footerBg: p.card,
        boxShadow: light ? DIALOG_SHADOW : 'none',
      },
      // 分段控件：antd 默认轨道用页面底色，放在页面上就看不见；轨道比页面底深一档，选中块用卡片色（深色再亮一档）（D38）
      Segmented: {
        trackBg: p.track,
        itemSelectedBg: light ? p.card : p.elevatedLine,
      },
      // 表格：表头 48、13 号次要色；单行 48、两行 60–64（ui-redesign D21）。
      // 上下内边距 11：两行内容（22 + 19）为 63；单行和表头靠全局样式里的最小高度 48 补齐
      Table: {
        headerBg: p.head,
        headerColor: p.mute,
        cellFontSize: 14,
        cellPaddingBlock: 11,
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
      // 进度条只用主题色（D48）：没写 strokeColor 的默认色、到 100% 的完成色都用图表色，不变青、不变绿
      Progress: {
        defaultColor: p.chart,
        colorSuccess: p.chart,
        remainingColor: p.neutralSoft,
      },
    },
  };
}
