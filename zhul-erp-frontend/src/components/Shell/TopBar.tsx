import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';

/**
 * 内容区顶部的 60 高透明顶栏：右侧放主题切换、文档、版本、语言。
 * ProLayout 的 side 布局桌面端不渲染 Header，所以顶栏放在 childrenRender 里，吸顶。
 */
export const TopBar: React.FC<{ children?: React.ReactNode }> = ({
  children,
}) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        position: 'sticky',
        top: 0,
        zIndex: 20,
        height: 60,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'flex-end',
        gap: 4,
        // 抵消内容区 28 的内边距，让顶栏通栏贴边
        margin: '-28px -28px 20px',
        padding: '0 28px',
        color: palette.sub,
        background: palette.canvas,
        borderBottom: `1px solid ${palette.hairline}`,
      }}
    >
      {children}
    </div>
  );
};
