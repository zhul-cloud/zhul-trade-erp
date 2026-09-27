import React, { useEffect, useState } from 'react';
import { ThemeToggle } from '@/components/Shell';
import type { AppearanceInfo } from '@/services/zhul/public';
import { getAppearance } from '@/services/zhul/public';
import { useAppTheme } from '@/theme/AppTheme';
import { LOGO_GRADIENT } from '@/theme/palette';

/** 极光底：蓝、靛、青三团柔光叠在主题底色上，深色下亮一些，浅色下淡一些 */
const aurora = (dark: boolean, canvas: string) => {
  const a = dark ? [0.32, 0.26, 0.16] : [0.16, 0.12, 0.1];
  return [
    `radial-gradient(60% 50% at 18% 8%, rgba(37, 99, 235, ${a[0]}), transparent)`,
    `radial-gradient(50% 42% at 88% 0%, rgba(99, 102, 241, ${a[1]}), transparent)`,
    `radial-gradient(46% 40% at 62% 100%, rgba(34, 211, 238, ${a[2]}), transparent)`,
    canvas,
  ].join(', ');
};

interface AuthLayoutProps {
  children: React.ReactNode;
  cardWidth?: number;
}

const AuthLayout: React.FC<AuthLayoutProps> = ({
  children,
  cardWidth = 400,
}) => {
  const [appearance, setAppearance] = useState<AppearanceInfo>();
  const [bgLoaded, setBgLoaded] = useState(false);
  const { mode, palette: p } = useAppTheme();

  useEffect(() => {
    getAppearance().then(setAppearance);
  }, []);

  useEffect(() => {
    // 自定义背景图可能是占位值（如种子数据里的 /bg.jpg）而实际文件并不存在；
    // CSS background 加载失败时不会像 <img onError> 那样自动回退，
    // 因此这里预加载探测一次，确认真的能显示再切换背景，否则保留渐变兜底。
    if (!appearance?.loginBackgroundUrl) {
      setBgLoaded(false);
      return;
    }
    let cancelled = false;
    const img = new Image();
    img.onload = () => {
      if (!cancelled) setBgLoaded(true);
    };
    img.onerror = () => {
      if (!cancelled) setBgLoaded(false);
    };
    img.src = appearance.loginBackgroundUrl;
    return () => {
      cancelled = true;
    };
  }, [appearance?.loginBackgroundUrl]);

  // 后台配置了可用的背景图就用图（叠一层主题底色保证文字对比度），否则用极光底
  const bg =
    bgLoaded && appearance?.loginBackgroundUrl
      ? `linear-gradient(${p.canvas}b3, ${p.canvas}b3), url(${appearance.loginBackgroundUrl}) center / cover no-repeat`
      : aurora(mode === 'dark', p.canvas);

  return (
    <div
      style={{
        minHeight: '100vh',
        width: '100%',
        background: bg,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '32px 16px 72px',
        boxSizing: 'border-box',
        position: 'relative',
      }}
    >
      <div style={{ position: 'absolute', top: 16, right: 24 }}>
        <ThemeToggle />
      </div>
      <div style={{ textAlign: 'center', marginBottom: 24 }}>
        {appearance?.logoUrl ? (
          <img
            src={appearance.logoUrl}
            alt={appearance.siteName || '烛龙ERP'}
            style={{ width: 120, height: 40, objectFit: 'contain' }}
          />
        ) : (
          <span
            role="img"
            aria-label={appearance?.siteName || '烛龙ERP'}
            style={{
              width: 48,
              height: 48,
              borderRadius: 14,
              background: LOGO_GRADIENT,
              color: '#FFFFFF',
              fontWeight: 800,
              fontSize: 24,
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            烛
          </span>
        )}
        <div
          style={{
            fontSize: 12,
            color: p.sub,
            marginTop: 8,
          }}
        >
          烛龙外贸业财一体化中台
        </div>
      </div>

      <div
        style={{
          width: cardWidth,
          maxWidth: '100%',
          background: p.card,
          border: `1px solid ${p.hairline}`,
          borderRadius: 16,
          padding: 32,
          boxShadow: '0 12px 40px rgba(2, 6, 23, 0.28)',
          boxSizing: 'border-box',
        }}
      >
        {children}
      </div>

      <div
        style={{
          position: 'fixed',
          bottom: 16,
          left: 0,
          right: 0,
          textAlign: 'center',
          color: p.mute,
          fontSize: 12,
        }}
      >
        {appearance?.loginFooter || '© 2026 烛龙科技'}　版本 1.1.0
      </div>
    </div>
  );
};

export default AuthLayout;
