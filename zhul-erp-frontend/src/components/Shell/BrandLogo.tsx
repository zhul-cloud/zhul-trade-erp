import { useModel } from '@umijs/max';
import React, { useEffect } from 'react';
import { LOGO_GRADIENT } from '@/theme/palette';
import {
  DEFAULT_APPEARANCE,
  LOGO_FONT_RATIO,
  LOGO_GRADIENT_STOPS,
  LOGO_RADIUS_RATIO,
  logoChar,
} from '@/utils/appearance';

/** 默认 Logo：渐变圆角方块 + 白色的系统名称首字（未上传 Logo 时使用） */
export const DefaultLogo: React.FC<{ siteName?: string; size: number }> = ({
  siteName,
  size,
}) => (
  <span
    role="img"
    aria-label={siteName}
    style={{
      width: size,
      height: size,
      borderRadius: Math.round(size * LOGO_RADIUS_RATIO),
      background: LOGO_GRADIENT,
      color: '#FFFFFF',
      fontWeight: 800,
      fontSize: Math.round(size * LOGO_FONT_RATIO),
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      flex: 'none',
    }}
  >
    {logoChar(siteName)}
  </span>
);

/** 当前外观（getInitialState 加载，登录前页面同样可用） */
export const useAppearance = () =>
  useModel('@@initialState').initialState?.appearance ?? DEFAULT_APPEARANCE;

/** 不在 ProLayout 里的页面（登录、找回密码）用：浏览器标签标题「页面名称 - 系统名称」 */
export const usePageTitle = (page: string) =>
  `${page} - ${useAppearance().siteName}`;

/** 画默认 Logo 的浏览器图标（64×64 PNG data URL） */
const drawDefaultIcon = (siteName?: string): string | undefined => {
  const size = 64;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  if (!ctx) return undefined;
  const r = size * LOGO_RADIUS_RATIO;
  ctx.beginPath();
  ctx.moveTo(r, 0);
  ctx.arcTo(size, 0, size, size, r);
  ctx.arcTo(size, size, 0, size, r);
  ctx.arcTo(0, size, 0, 0, r);
  ctx.arcTo(0, 0, size, 0, r);
  ctx.closePath();
  const gradient = ctx.createLinearGradient(0, 0, size, size); // 135°：左上 → 右下
  LOGO_GRADIENT_STOPS.forEach((c, i, all) => {
    gradient.addColorStop(i / (all.length - 1), c);
  });
  ctx.fillStyle = gradient;
  ctx.fill();
  ctx.fillStyle = '#FFFFFF';
  ctx.font = `800 ${size * LOGO_FONT_RATIO}px "PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(logoChar(siteName), size / 2, size / 2 + size * 0.03);
  return canvas.toDataURL('image/png');
};

/**
 * 按外观更新浏览器标签图标：有 Logo 用 Logo，没有就用默认 Logo 现画一个。
 * index.html 里的静态 favicon（「烛」）只是脚本运行前的兜底。
 */
export const SiteFavicon: React.FC<{ siteName?: string; logoUrl?: string }> = ({
  siteName,
  logoUrl,
}) => {
  useEffect(() => {
    const href = logoUrl || drawDefaultIcon(siteName);
    if (!href) return;
    const links =
      document.querySelectorAll<HTMLLinkElement>('link[rel~="icon"]');
    if (links.length === 0) {
      const link = document.createElement('link');
      link.rel = 'icon';
      link.href = href;
      document.head.appendChild(link);
      return;
    }
    links.forEach((link) => {
      link.href = href;
    });
  }, [siteName, logoUrl]);
  return null;
};
