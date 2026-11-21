import { useModel } from '@umijs/max';
import { PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { DefaultLogo } from './BrandLogo';
import { IconButton } from './IconButton';

const ellipsis: React.CSSProperties = {
  display: 'block',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  whiteSpace: 'nowrap',
};

/**
 * 侧栏顶部：Logo（未上传时为默认 Logo）、站点名和公司名、收起按钮。
 * 外观全平台统一，公司名是唯一能看出当前所在公司的地方，所以常显。
 * 收起后只留 Logo，下面是展开按钮；悬停 Logo 显示站点名和公司名。
 */
export const AppLogo: React.FC<{ collapsed?: boolean }> = ({ collapsed }) => {
  const { palette } = useAppTheme();
  const { initialState, setInitialState } = useModel('@@initialState');
  const appearance = initialState?.appearance;
  if (!appearance) return null;
  const tenant = initialState?.currentUser?.tenantName;
  // 收起状态（initialState.collapsed）下浮出展开时，collapsed 属性为 false
  const peeking = !collapsed && !!initialState?.collapsed;
  // ProLayout 给侧栏头部绑了「点击回首页」，按钮点击不往上传
  const toggle = (e: React.MouseEvent) => {
    e.stopPropagation();
    setInitialState((s) => ({
      ...s,
      collapsed: !peeking,
      siderPeek: false,
    }));
  };
  const logo = appearance.logoUrl ? (
    <img
      src={appearance.logoUrl}
      alt={appearance.siteName}
      style={{ width: 30, height: 30, objectFit: 'contain', flex: 'none' }}
    />
  ) : (
    <DefaultLogo siteName={appearance.siteName} size={30} />
  );

  // 图标栏：只留 Logo 和一条分隔线；鼠标移上去整个侧栏浮出展开（SiderPeek）
  if (collapsed) {
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 14,
        }}
      >
        <span title={[appearance.siteName, tenant].filter(Boolean).join(' · ')}>
          {logo}
        </span>
        <span
          aria-hidden
          style={{ width: 32, height: 1, background: palette.hairline }}
        />
      </div>
    );
  }

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        // Logo 中心与下面的菜单图标列对齐
        paddingInlineStart: 11,
      }}
    >
      {logo}
      <div style={{ minWidth: 0, flex: 1 }}>
        <span
          title={appearance.siteName}
          style={{
            ...ellipsis,
            fontSize: 20,
            fontWeight: 700,
            lineHeight: 1.3,
            color: palette.link,
          }}
        >
          {appearance.siteName}
        </span>
        {tenant && (
          <span
            title={tenant}
            style={{ ...ellipsis, fontSize: 12, color: palette.mute }}
          >
            {tenant}
          </span>
        )}
      </div>
      <IconButton
        label={peeking ? '固定展开侧边栏' : '收起侧边栏'}
        icon={
          peeking ? <PanelLeftOpen size={18} /> : <PanelLeftClose size={18} />
        }
        size={32}
        onClick={toggle}
        style={{ color: palette.mute }}
      />
    </div>
  );
};
