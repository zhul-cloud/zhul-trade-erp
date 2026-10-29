import { request } from '@umijs/max';
import { type Appearance, DEFAULT_APPEARANCE } from '@/utils/appearance';

export type AppearanceInfo = Appearance;

/** 外观（平台统一，免登录可读）；接口失败或字段为空时用默认值 */
export const getAppearance = (): Promise<AppearanceInfo> =>
  request('/api/v1/public/appearance', {
    method: 'GET',
    skipErrorHandler: true,
  })
    .then((res) => {
      const data: Partial<AppearanceInfo> = res.data ?? {};
      return {
        siteName: data.siteName || DEFAULT_APPEARANCE.siteName,
        siteSubtitle: data.siteSubtitle || DEFAULT_APPEARANCE.siteSubtitle,
        loginFooter: data.loginFooter || DEFAULT_APPEARANCE.loginFooter,
        logoUrl: data.logoUrl || undefined,
        loginBackgroundUrl: data.loginBackgroundUrl || undefined,
      };
    })
    .catch(() => DEFAULT_APPEARANCE);
