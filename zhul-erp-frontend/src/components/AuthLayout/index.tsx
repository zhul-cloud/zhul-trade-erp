import React, { useEffect, useState } from 'react';
import type { AppearanceInfo } from '@/services/zhul/public';
import { getAppearance } from '@/services/zhul/public';

const DEFAULT_LOGO = '/logo.svg';
const DEFAULT_GRADIENT = 'linear-gradient(135deg, #1890ff 0%, #0050b3 100%)';

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

  const bg =
    bgLoaded && appearance?.loginBackgroundUrl
      ? `url(${appearance.loginBackgroundUrl}) center / cover no-repeat`
      : DEFAULT_GRADIENT;

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
      }}
    >
      <div style={{ textAlign: 'center', marginBottom: 24 }}>
        <img
          src={appearance?.logoUrl || DEFAULT_LOGO}
          alt={appearance?.siteName || '烛龙ERP'}
          style={{ width: 120, height: 40, objectFit: 'contain' }}
          onError={(e) => {
            if (e.currentTarget.src.indexOf(DEFAULT_LOGO) === -1) {
              e.currentTarget.src = DEFAULT_LOGO;
            }
          }}
        />
        <div
          style={{
            fontSize: 12,
            color: 'rgba(255,255,255,0.85)',
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
          background: '#fff',
          borderRadius: 8,
          padding: 32,
          boxShadow: '0 4px 24px rgba(0,0,0,0.15)',
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
          color: 'rgba(255,255,255,0.5)',
          fontSize: 12,
        }}
      >
        {appearance?.loginFooter || '© 2026 烛龙科技'}　版本 1.0.0
      </div>
    </div>
  );
};

export default AuthLayout;
