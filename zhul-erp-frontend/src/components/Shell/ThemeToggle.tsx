import { MoonOutlined, SunOutlined } from '@ant-design/icons';
import { Button, Tooltip } from 'antd';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';

/** 顶栏右侧的深浅色切换，全站生效并记住选择 */
export const ThemeToggle: React.FC = () => {
  const { mode, toggle } = useAppTheme();
  const label = mode === 'dark' ? '切换到浅色主题' : '切换到深色主题';
  return (
    <Tooltip title={label}>
      <Button
        type="text"
        aria-label={label}
        icon={mode === 'dark' ? <SunOutlined /> : <MoonOutlined />}
        onClick={toggle}
      />
    </Tooltip>
  );
};
