import { history, useModel } from '@umijs/max';
import { App, Avatar, Dropdown, Tag, Tooltip } from 'antd';
import { LogOut } from 'lucide-react';
import React, { startTransition, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { POPUP_SHADOW } from '@/theme/palette';

const ellipsis: React.CSSProperties = {
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  whiteSpace: 'nowrap',
};

/**
 * 顶栏右端的头像：主题色浅底加首字，打开菜单时外加 2px 主题色圈。
 * 菜单头部为姓名、角色、公司名，下面是「退出登录」（先确认再退出）。原侧栏底部的用户卡并入这里。
 */
export const UserMenu: React.FC = () => {
  const { modal } = App.useApp();
  const { palette, mode } = useAppTheme();
  const { initialState, setInitialState } = useModel('@@initialState');
  const [open, setOpen] = useState(false);
  const user = initialState?.currentUser;
  if (!user) return null;
  const name = user.name || user.userid || '用户';
  const role = user.access === 'admin' ? '管理员' : '成员';
  const tenant = user.tenantName;
  const initial = name.charAt(0).toUpperCase();

  const logout = async () => {
    try {
      await import('@/services/zhul/auth').then((m) => m.logout());
    } finally {
      localStorage.removeItem('zhul_user');
      history.replace('/login');
    }
  };

  const confirmLogout = () =>
    modal.confirm({
      title: '退出登录',
      content: '确定要退出登录吗？退出后需重新输入账号密码。',
      okText: '确定退出',
      okType: 'danger', // lint-ui-ignore danger-confirm: 退出登录不是删除类操作，不用危险图标，只保留红色按钮
      cancelText: '取消',
      width: 400,
      onOk: () => {
        startTransition(() => {
          setInitialState((s) => ({ ...s, currentUser: undefined }));
        });
        logout();
      },
    });

  return (
    <Dropdown
      trigger={['click']}
      placement="bottomRight"
      open={open}
      onOpenChange={setOpen}
      menu={{
        items: [
          { key: 'logout', icon: <LogOut size={18} />, label: '退出登录' },
        ],
        onClick: ({ key }) => {
          if (key === 'logout') confirmLogout();
        },
      }}
      popupRender={(menu) => (
        <div
          style={{
            width: 260,
            padding: 6,
            borderRadius: 8,
            background: palette.elevated,
            boxShadow: mode === 'light' ? POPUP_SHADOW : 'none',
            border:
              mode === 'light' ? 'none' : `1px solid ${palette.elevatedLine}`,
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              padding: '10px 10px 12px',
              borderBottom: `1px solid ${palette.hairline}`,
              marginBottom: 6,
            }}
          >
            <Avatar
              size={40}
              src={user.avatar || undefined}
              style={{
                flex: 'none',
                background: palette.primarySoft, // lint-ui-ignore primary-soft: 头像，规范「头像：主题色浅底加首字」
                color: palette.link,
                fontWeight: 700,
              }}
            >
              {initial}
            </Avatar>
            <div style={{ minWidth: 0, flex: 1 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span
                  title={name}
                  style={{ ...ellipsis, fontWeight: 600, color: palette.ink }}
                >
                  {name}
                </span>
                <Tag style={{ marginInlineEnd: 0, flex: 'none' }}>{role}</Tag>
              </div>
              {tenant && (
                <div
                  title={tenant}
                  style={{
                    ...ellipsis,
                    marginTop: 4,
                    fontSize: 12,
                    color: palette.mute,
                  }}
                >
                  {tenant}
                </div>
              )}
            </div>
          </div>
          {React.cloneElement(menu as React.ReactElement<any>, {
            style: { boxShadow: 'none', border: 0, padding: 0 },
          })}
        </div>
      )}
    >
      <Tooltip title={name} placement="bottom" open={open ? false : undefined}>
        <button
          type="button"
          aria-label={`当前用户 ${name}，打开菜单`}
          aria-expanded={open}
          aria-haspopup="menu"
          style={{
            width: 40,
            height: 40,
            flex: 'none',
            display: 'grid',
            placeItems: 'center',
            padding: 0,
            borderRadius: '50%',
            border: `2px solid ${open ? palette.link : 'transparent'}`,
            background: 'transparent',
            cursor: 'pointer',
          }}
        >
          <Avatar
            size={36}
            src={user.avatar || undefined}
            style={{
              background: palette.primarySoft, // lint-ui-ignore primary-soft: 头像，规范「头像：主题色浅底加首字」
              color: palette.link,
              fontWeight: 700,
            }}
          >
            {initial}
          </Avatar>
        </button>
      </Tooltip>
    </Dropdown>
  );
};
