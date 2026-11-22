import { MoonOutlined, SunOutlined } from '@ant-design/icons';
import { Button, Tooltip } from 'antd';
import React from 'react';
import { SkipLink } from '@/components/Shell/SkipLink';
import { useAppTheme } from '@/theme/AppTheme';

export type { ThemeMode } from '@/theme/palette';
// 商品页早先自带一套主题；全站主题升级后，主题由 src/theme 统一提供，这里只保留商品页沿用的名字，
// 避免改动每一个页面的导入。

/** 当前主题（模式、令牌、切换函数），与全站一致 */
export const useProductTheme = useAppTheme;

/**
 * 商品页容器：提供"跳到主要内容"链接。整页模式（新建向导，没有外壳）占满视口。
 * 颜色、主按钮、焦点环等都来自全站主题，这里不再重复。
 */
export const ProductThemeProvider: React.FC<{
  children: React.ReactNode;
  fullPage?: boolean;
}> = ({ children, fullPage = false }) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        position: 'relative',
        minHeight: fullPage ? '100vh' : undefined,
        background: fullPage ? palette.canvas : undefined,
        color: palette.ink,
      }}
    >
      {/* 外壳已提供「跳到主要内容」，只在没有外壳的整页向导里自己放一个 */}
      {fullPage && <SkipLink target="product-main" />}
      {children}
    </div>
  );
};

/** 没有外壳的页面（向导）自己放的主题切换按钮 */
export const InlineThemeToggle: React.FC = () => {
  const { mode, toggle } = useAppTheme();
  const label = mode === 'dark' ? '切换到浅色主题' : '切换到深色主题';
  return (
    <Tooltip title={label}>
      <Button
        aria-label={label}
        icon={mode === 'dark' ? <SunOutlined /> : <MoonOutlined />}
        onClick={toggle}
      />
    </Tooltip>
  );
};
