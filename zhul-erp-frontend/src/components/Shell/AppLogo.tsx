import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { LOGO_GRADIENT } from '@/theme/palette';

/** 侧栏顶部：蓝 → 靛 → 青渐变方块 + 产品名；侧栏收起时只留方块 */
export const AppLogo: React.FC<{ collapsed?: boolean }> = ({ collapsed }) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '4px 4px 4px 2px',
      }}
    >
      <span
        aria-hidden="true"
        style={{
          width: 32,
          height: 32,
          borderRadius: 10,
          background: LOGO_GRADIENT,
          color: '#FFFFFF',
          fontWeight: 800,
          fontSize: 16,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          flex: 'none',
        }}
      >
        烛
      </span>
      {!collapsed && (
        <span
          style={{
            fontSize: 18,
            fontWeight: 700,
            color: palette.ink,
            whiteSpace: 'nowrap',
          }}
        >
          烛龙ERP
        </span>
      )}
    </div>
  );
};
