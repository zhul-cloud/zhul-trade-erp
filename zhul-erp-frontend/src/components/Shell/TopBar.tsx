import { BankOutlined } from '@ant-design/icons';
import { useModel } from '@umijs/max';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';

/**
 * 内容区顶部的 60 高透明顶栏：左侧显示当前登录账号所属租户，右侧放主题切换、文档、版本、语言。
 * ProLayout 的 side 布局桌面端不渲染 Header，所以顶栏放在 childrenRender 里，吸顶。
 */
export const TopBar: React.FC<{ children?: React.ReactNode }> = ({
  children,
}) => {
  const { palette } = useAppTheme();
  const { initialState } = useModel('@@initialState');
  const tenantName = initialState?.currentUser?.tenantName;

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
      {tenantName && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            marginRight: 'auto',
            padding: '5px 12px',
            borderRadius: 8,
            fontSize: 13,
            color: palette.sub,
            background: palette.inset,
            border: `1px solid ${palette.hairline}`,
          }}
        >
          <BankOutlined style={{ color: palette.mute }} />
          <span>{tenantName}</span>
        </div>
      )}
      {children}
    </div>
  );
};
