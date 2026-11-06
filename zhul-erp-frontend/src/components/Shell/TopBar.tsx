import {
  BankOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';
import { useModel } from '@umijs/max';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';

/**
 * 内容区顶部的 60 高透明顶栏：左侧是侧边栏收起按钮和当前登录账号所属租户，右侧放主题切换、文档、版本、语言。
 * ProLayout 的 side 布局桌面端不渲染 Header，所以顶栏放在 childrenRender 里，吸顶。
 */
export const TopBar: React.FC<{ children?: React.ReactNode }> = ({
  children,
}) => {
  const { palette } = useAppTheme();
  const { initialState, setInitialState } = useModel('@@initialState');
  const tenantName = initialState?.currentUser?.tenantName;
  const collapsed = !!initialState?.collapsed;

  return (
    <div
      style={{
        position: 'sticky',
        top: 0,
        zIndex: 20,
        height: 60,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'flex-start',
        gap: 4,
        // 抵消内容区 28 的内边距，让顶栏通栏贴边
        margin: '-28px -28px 20px',
        padding: '0 28px',
        color: palette.sub,
        background: palette.canvas,
        borderBottom: `1px solid ${palette.hairline}`,
      }}
    >
      <button
        type="button"
        aria-label={collapsed ? '展开侧边栏' : '收起侧边栏'}
        title={collapsed ? '展开侧边栏' : '收起侧边栏'}
        onClick={() =>
          setInitialState((s) => ({ ...s, collapsed: !collapsed }))
        }
        style={{
          width: 32,
          height: 32,
          marginLeft: -6,
          marginRight: 6,
          border: 0,
          borderRadius: 8,
          display: 'grid',
          placeItems: 'center',
          fontSize: 16,
          color: palette.sub,
          background: 'transparent',
          cursor: 'pointer',
        }}
      >
        {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
      </button>
      {tenantName && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
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
      <span style={{ flex: 1 }} />
      {children}
    </div>
  );
};
