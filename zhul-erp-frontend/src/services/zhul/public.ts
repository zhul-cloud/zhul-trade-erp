import { request } from '@umijs/max';

export interface AppearanceInfo {
  siteName: string;
  logoUrl?: string;
  loginBackgroundUrl?: string;
  loginFooter?: string;
}

export const getAppearance = (): Promise<AppearanceInfo> =>
  request('/api/v1/public/appearance', {
    method: 'GET',
    skipErrorHandler: true,
  })
    .then((res) => res.data ?? { siteName: '烛龙ERP' })
    .catch(() => ({ siteName: '烛龙ERP' }));
