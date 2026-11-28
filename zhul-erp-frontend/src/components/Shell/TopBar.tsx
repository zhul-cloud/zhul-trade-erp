import { useModel } from '@umijs/max';
import React from 'react';
import { TabBar } from '@/components/PageTabs';
import { useAppTheme } from '@/theme/AppTheme';
import { CARD_SHADOW } from '@/theme/palette';

type MenuTree = React.ComponentProps<typeof TabBar>['tree'];

/**
 * 顶栏（外壳原型 F1）：悬浮卡片，高 64，左侧是标签页，右侧放主题设置、语言、通知、头像（children）。
 * ProLayout 的 side 布局桌面端不渲染 Header，所以顶栏放在 childrenRender 里吸顶；
 * 上方 16 的间距用页面底色填满，滚动时内容不会从顶栏上方露出来。
 */
export const TopBar: React.FC<{ children?: React.ReactNode }> = ({
  children,
}) => {
  const { palette, mode } = useAppTheme();
  const { initialState } = useModel('@@initialState');
  const tree = (initialState?.menuTree ?? []) as MenuTree;
  const light = mode === 'light';

  return (
    <div
      style={{
        position: 'sticky',
        top: 0,
        zIndex: 20,
        paddingTop: 16,
        marginBottom: 16,
        background: palette.canvas,
      }}
    >
      <div
        style={{
          height: 64,
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '0 16px',
          borderRadius: 10,
          background: palette.card,
          boxShadow: light ? CARD_SHADOW : 'none',
          border: light ? 'none' : `1px solid ${palette.hairline}`,
        }}
      >
        {initialState?.currentUser ? (
          <TabBar tree={tree} />
        ) : (
          <span style={{ flex: 1 }} />
        )}
        <div
          style={{
            flex: 'none',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
          }}
        >
          {children}
        </div>
      </div>
    </div>
  );
};
