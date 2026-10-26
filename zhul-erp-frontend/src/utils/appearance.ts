/**
 * 外观设置（系统参数 → 外观设置，平台统一）：系统名称、副标题、Logo、登录页背景与底部文字。
 * 默认值与后端迁移 V1.3.14 写入的一致，接口失败时使用。
 */
export interface Appearance {
  siteName: string;
  siteSubtitle: string;
  logoUrl?: string;
  loginBackgroundUrl?: string;
  loginFooter: string;
}

export const DEFAULT_APPEARANCE: Appearance = {
  siteName: '烛龙ERP',
  siteSubtitle: '烛龙外贸业财一体化中台',
  loginFooter: '© 2026 烛龙科技',
};

/** 外观设置里的图片项：允许清空，清空后恢复默认 Logo / 默认登录页背景 */
export const APPEARANCE_IMAGE_KEYS = ['sys.site.logo', 'sys.login.background'];

/** 默认 Logo 方块里的字：系统名称的第一个字（按字符取，不会把生僻字或表情拆成半个），名称为空时用「烛」 */
export const logoChar = (siteName?: string): string =>
  Array.from((siteName ?? '').trim())[0] ?? '烛';

/** 默认 Logo 的比例：圆角、字号相对边长（侧栏 32px 时圆角 10、字号 16） */
export const LOGO_RADIUS_RATIO = 10 / 32;
export const LOGO_FONT_RATIO = 0.5;
/** 与 theme/palette 的 LOGO_GRADIENT 同一组颜色，canvas 画浏览器图标时用 */
export const LOGO_GRADIENT_STOPS = ['#3B82F6', '#6366F1', '#22D3EE'];
