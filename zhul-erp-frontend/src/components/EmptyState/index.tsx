import { CircleAlert, Inbox, Lock, SearchX } from 'lucide-react';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';

/** 空（首次使用）、无结果（筛选后）、错误、无权限；加载中用骨架屏，不用这个组件 */
export type EmptyKind = 'empty' | 'noResult' | 'error' | 'forbidden';

const DEFAULTS: Record<EmptyKind, [React.ElementType, string]> = {
  empty: [Inbox, '暂无数据'],
  noResult: [SearchX, '没有符合条件的结果'],
  error: [CircleAlert, '加载失败'],
  forbidden: [Lock, '没有查看权限'],
};

/**
 * 数据视图的空状态（公共组件，规范「说明文字 · 数据视图的六种状态」，ui-redesign C4、S8）：
 * 居中的图标、一句话、可选的说明和下一步按钮。无结果时 action 给「清空筛选」，错误时给「重试」，
 * 无权限时 description 写明需要的权限。地方小（下拉、卡片里的小列表）用 compact，只占一行。
 * 全站表格没写 emptyText 时也用它（AppTheme 的 renderEmpty）。
 */
export const EmptyState: React.FC<{
  kind?: EmptyKind;
  title?: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
  compact?: boolean;
}> = ({ kind = 'empty', title, description, action, compact }) => {
  const { palette } = useAppTheme();
  const [Icon, text] = DEFAULTS[kind];
  const color = kind === 'error' ? palette.red : palette.mute;
  if (compact) {
    return (
      <div
        role="status"
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 6,
          padding: '12px 8px',
          fontSize: 13,
          color: palette.mute,
        }}
      >
        <Icon size={16} aria-hidden style={{ color, flex: 'none' }} />
        <span>{title ?? text}</span>
        {action}
      </div>
    );
  }
  return (
    <div
      role="status"
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 8,
        padding: '40px 16px',
        textAlign: 'center',
      }}
    >
      <span
        aria-hidden
        style={{
          width: 48,
          height: 48,
          borderRadius: '50%',
          display: 'grid',
          placeItems: 'center',
          marginBottom: 4,
          background: kind === 'error' ? palette.redSoft : palette.neutralSoft,
          color,
        }}
      >
        <Icon size={24} />
      </span>
      <span style={{ fontSize: 14, fontWeight: 600, color: palette.ink }}>
        {title ?? text}
      </span>
      {description && (
        <span style={{ fontSize: 13, color: palette.sub, maxWidth: 360 }}>
          {description}
        </span>
      )}
      {action && <div style={{ marginTop: 8 }}>{action}</div>}
    </div>
  );
};

/** 给 ConfigProvider 的 renderEmpty：表格、列表用完整空状态，下拉等小地方用一行 */
export const renderEmpty = (componentName?: string) =>
  componentName === 'Table' || componentName === 'List' ? (
    <EmptyState />
  ) : (
    <EmptyState compact />
  );
