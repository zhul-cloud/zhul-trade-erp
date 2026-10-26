import { useModel } from '@umijs/max';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { DefaultLogo } from './BrandLogo';

/** 侧栏顶部：外观里的 Logo（未上传时为默认 Logo）+ 系统名称；侧栏收起时只留 Logo */
export const AppLogo: React.FC<{ collapsed?: boolean }> = ({ collapsed }) => {
  const { palette } = useAppTheme();
  const { initialState } = useModel('@@initialState');
  const appearance = initialState?.appearance;
  if (!appearance) return null;
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '4px 4px 4px 2px',
      }}
    >
      {appearance.logoUrl ? (
        <img
          src={appearance.logoUrl}
          alt={appearance.siteName}
          style={{
            width: 32,
            height: 32,
            objectFit: 'contain',
            flex: 'none',
          }}
        />
      ) : (
        <DefaultLogo siteName={appearance.siteName} size={32} />
      )}
      {!collapsed && (
        <span
          style={{
            fontSize: 18,
            fontWeight: 700,
            color: palette.ink,
            whiteSpace: 'nowrap',
          }}
        >
          {appearance.siteName}
        </span>
      )}
    </div>
  );
};
