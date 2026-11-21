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
  const toggle = () =>
    setInitialState((s) => ({ ...s, collapsed: !collapsed }));
  const logo = appearance.logoUrl ? (
    <img
      src={appearance.logoUrl}
      alt={appearance.siteName}
      style={{ width: 30, height: 30, objectFit: 'contain', flex: 'none' }}
    />
  ) : (
    <DefaultLogo siteName={appearance.siteName} size={30} />
  );

  if (collapsed) {
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 4,
        }}
      >
        <span title={[appearance.siteName, tenant].filter(Boolean).join(' · ')}>
          {logo}
        </span>
        <IconButton
          label="展开侧边栏"
          icon={<PanelLeftOpen size={18} />}
          size={40}
          onClick={toggle}
          style={{ color: palette.mute }}
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
        paddingInlineStart: 9,
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
        label="收起侧边栏"
        icon={<PanelLeftClose size={18} />}
        size={32}
        onClick={toggle}
        style={{ color: palette.mute }}
      />
    </div>
  );
};
