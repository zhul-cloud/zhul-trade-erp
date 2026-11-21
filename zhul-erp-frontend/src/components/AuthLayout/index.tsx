import React, { useEffect, useState } from 'react';
import { ThemeToggle } from '@/components/Shell';
import {
  DefaultLogo,
  SiteFavicon,
  useAppearance,
} from '@/components/Shell/BrandLogo';
import { useAppTheme } from '@/theme/AppTheme';
import { CARD_SHADOW } from '@/theme/palette';

interface AuthLayoutProps {
  children: React.ReactNode;
  cardWidth?: number;
}

const AuthLayout: React.FC<AuthLayoutProps> = ({
  children,
  cardWidth = 400,
}) => {
  const appearance = useAppearance();
  const [bgLoaded, setBgLoaded] = useState(false);
  const { mode, palette: p } = useAppTheme();

  useEffect(() => {
    // 自定义背景图可能是占位值（如种子数据里的 /bg.jpg）而实际文件并不存在；
    // CSS background 加载失败时不会像 <img onError> 那样自动回退，
    // 因此这里预加载探测一次，确认真的能显示再切换背景，否则用页面底色。
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

  // 后台配置了可用的背景图就用图（叠一层页面底色保证文字对比度），否则用页面底色
  const bg =
    bgLoaded && appearance?.loginBackgroundUrl
      ? `linear-gradient(${p.canvas}b3, ${p.canvas}b3), url(${appearance.loginBackgroundUrl}) center / cover no-repeat`
      : p.canvas;

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
      <SiteFavicon
        siteName={appearance.siteName}
        logoUrl={appearance.logoUrl}
      />
      <div style={{ textAlign: 'center', marginBottom: 24 }}>
        {appearance.logoUrl ? (
          <img
            src={appearance.logoUrl}
            alt={appearance.siteName}
            style={{ width: 120, height: 40, objectFit: 'contain' }}
          />
        ) : (
          <DefaultLogo siteName={appearance.siteName} size={48} />
        )}
        <div
          style={{
            fontSize: 12,
            color: p.sub,
            marginTop: 8,
          }}
        >
          {appearance.siteSubtitle}
        </div>
      </div>

      <div
        style={{
          width: cardWidth,
          maxWidth: '100%',
          background: p.card,
          border: mode === 'dark' ? `1px solid ${p.hairline}` : 'none',
          borderRadius: 10,
          padding: 32,
          boxShadow: mode === 'dark' ? 'none' : CARD_SHADOW,
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
        {appearance.loginFooter}　版本 {__APP_VERSION__}
      </div>
    </div>
  );
};

export default AuthLayout;
