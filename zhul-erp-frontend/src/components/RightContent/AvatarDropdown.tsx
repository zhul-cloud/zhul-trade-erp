import { ExclamationCircleOutlined, LogoutOutlined } from '@ant-design/icons';
import { history, useModel } from '@umijs/max';
import type { MenuProps } from 'antd';
import { App, Spin } from 'antd';
import React, { startTransition } from 'react';
import HeaderDropdown from '../HeaderDropdown';

type GlobalHeaderRightProps = {
  children?: React.ReactNode;
};

export const AvatarDropdown: React.FC<GlobalHeaderRightProps> = ({
  children,
}) => {
  const { modal } = App.useApp();
  const { initialState, setInitialState } = useModel('@@initialState');

  const loginOut = async () => {
    try {
      await import('@/services/zhul/auth').then((m) => m.logout());
    } finally {
      localStorage.removeItem('zhul_user');
      history.replace('/login');
    }
  };

  const confirmLogout = () => {
    modal.confirm({
      title: '退出登录',
      icon: <ExclamationCircleOutlined style={{ color: '#faad14' }} />,
      content: '确定要退出登录吗？退出后需重新输入账号密码。',
      okText: '确定退出',
      okType: 'danger',
      cancelText: '取消',
      width: 400,
      onOk: () => {
        startTransition(() => {
          setInitialState((s) => ({ ...s, currentUser: undefined }));
        });
        loginOut();
      },
    });
  };

  const onMenuClick: MenuProps['onClick'] = (event) => {
    const { key } = event;
    if (key === 'logout') {
      confirmLogout();
      return;
    }
    if (key === 'theme') {
      setInitialState((s) => ({ ...s, settingDrawerOpen: true }));
      return;
    }
    history.push(`/account/${key}`);
  };

  if (!initialState) {
    return <Spin size="small" />;
  }

  const { currentUser } = initialState;

  if (!currentUser) {
    return <Spin size="small" />;
  }

  const menuItems: MenuProps['items'] = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
    },
  ];

  return (
    <HeaderDropdown
      placement="bottomRight"
      menu={{
        selectedKeys: [],
        onClick: onMenuClick,
        items: menuItems,
      }}
      arrow
    >
      {children}
    </HeaderDropdown>
  );
};
