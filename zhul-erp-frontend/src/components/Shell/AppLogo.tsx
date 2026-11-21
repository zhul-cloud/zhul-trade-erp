import { useModel } from '@umijs/max';
import { Tooltip } from 'antd';
import { Circle, CircleDot } from 'lucide-react';
import React, { useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { DefaultLogo } from './BrandLogo';

const ellipsis: React.CSSProperties = {
  display: 'block',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  whiteSpace: 'nowrap',
};

/**
 * 收起 / 固定展开侧栏的圆钮（照参考站 PrimeDash 的 tonal 圆钮）：32px 圆形，底色是图标色的 12%，
 * 悬停加深到 16%，明暗模式通用。固定展开时是带点的圆圈，收起后浮出时是空心圆（点它固定展开）
 */
const SiderToggle: React.FC<{
  pinned: boolean;
  onClick: (e: React.MouseEvent) => void;
}> = ({ pinned, onClick }) => {
  const { palette } = useAppTheme();
  const [hover, setHover] = useState(false);
  const label = pinned ? '收起侧边栏' : '固定展开侧边栏';
  const Icon = pinned ? CircleDot : Circle;
  return (
    <Tooltip title={label} placement="bottom">
      <button
        type="button"
        aria-label={label}
        onClick={onClick}
        onMouseEnter={() => setHover(true)}
        onMouseLeave={() => setHover(false)}
        style={{
          width: 32,
          height: 32,
          flex: 'none',
          display: 'grid',
          placeItems: 'center',
          padding: 0,
          border: 0,
          borderRadius: '50%',
          color: palette.sub,
          background: `color-mix(in srgb, currentColor ${hover ? 16 : 12}%, transparent)`,
          cursor: 'pointer',
          transition: 'background 0.28s cubic-bezier(0.4, 0, 0.2, 1)',
        }}
      >
        <Icon size={20} strokeWidth={2} />
      </button>
    </Tooltip>
  );
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
        // 撑满 logo 行，切换按钮贴在最右（照参考站）
        flex: 1,
        minWidth: 0,
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
      <SiderToggle pinned={!peeking} onClick={toggle} />
    </div>
  );
};
