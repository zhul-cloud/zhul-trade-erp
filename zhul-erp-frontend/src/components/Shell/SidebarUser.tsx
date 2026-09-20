import { useModel } from '@umijs/max';
import { Avatar } from 'antd';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { AvatarDropdown } from '../RightContent/AvatarDropdown';

/** 侧栏底部的当前用户卡：点击出现"退出登录"；侧栏收起时只留头像 */
export const SidebarUser: React.FC<{ collapsed?: boolean }> = ({
  collapsed,
}) => {
  const { initialState } = useModel('@@initialState');
  const { palette } = useAppTheme();
  const user = initialState?.currentUser;
  if (!user) return null;
  const name = user.name || user.userid || '用户';
  return (
    <AvatarDropdown>
      <button
        type="button"
        aria-label={`当前用户 ${name}，打开菜单`}
        style={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          gap: collapsed ? 0 : 12,
          justifyContent: collapsed ? 'center' : 'flex-start',
          padding: collapsed ? 4 : 12,
          boxSizing: 'border-box',
          borderRadius: 14,
          border: `1px solid ${palette.hairline}`,
          background: palette.card,
          color: palette.ink,
          cursor: 'pointer',
          textAlign: 'left',
        }}
      >
        <Avatar
          src={user.avatar || undefined}
          style={{
            background: palette.accentSoft,
            color: palette.link,
            flex: 'none',
          }}
        >
          {name.charAt(0).toUpperCase()}
        </Avatar>
        {!collapsed && (
          <span style={{ minWidth: 0 }}>
            <span
              style={{
                display: 'block',
                fontWeight: 600,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {name}
            </span>
            <span
              style={{ display: 'block', fontSize: 12, color: palette.mute }}
            >
              {user.access === 'admin' ? '管理员' : '成员'}
            </span>
          </span>
        )}
      </button>
    </AvatarDropdown>
  );
};
