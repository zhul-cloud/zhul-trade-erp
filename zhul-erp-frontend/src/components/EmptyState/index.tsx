import { Button } from 'antd';
import { Inbox, Lock, SearchX, TriangleAlert } from 'lucide-react';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';

/** 空（首次使用）、无结果（筛选后）、错误、无权限；加载中用骨架屏，不用这个组件 */
export type EmptyKind = 'empty' | 'noResult' | 'error' | 'forbidden';

const DEFAULTS: Record<EmptyKind, [React.ElementType, string]> = {
  empty: [Inbox, '暂无数据'],
  noResult: [SearchX, '没有符合条件的结果'],
  error: [TriangleAlert, '加载失败'],
  forbidden: [Lock, '没有查看权限'],
};

/**
 * 数据视图的空状态（公共组件，规范「说明文字 · 数据视图的六种状态」，ui-redesign C4、S8）：
 * 居中的图标、一句话、可选的说明和下一步按钮。无结果时 action 给「清空筛选」，错误时给「重试」，
 * 无权限时 description 写明需要的权限。地方小（下拉、卡片里的小列表）用 compact，只占一行。
 * 全站表格没写 emptyText 时也用它（AppTheme 的 renderEmpty）；列表页用 listEmpty 按加载、筛选、出错自动选。
 * 尺寸照公共组件原型 G2：图标圆 64、图标 28，标题 16/600，图标到标题 16、标题到说明 4、说明到按钮 16。
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
  const error = kind === 'error';
  const color = error ? palette.red : palette.mute;
  // 只有出错需要读屏立即播报；空、无结果每次出现都播报反而打扰
  const role = error ? 'alert' : undefined;
  if (compact) {
    return (
      <div
        role={role}
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
      role={role}
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        padding: '40px 16px',
        textAlign: 'center',
      }}
    >
      <span
        aria-hidden
        style={{
          width: 64,
          height: 64,
          borderRadius: '50%',
          display: 'grid',
          placeItems: 'center',
          marginBottom: 16,
          background: error ? palette.redSoft : palette.neutralSoft,
          color,
        }}
      >
        <Icon size={28} />
      </span>
      <span style={{ fontSize: 16, fontWeight: 600, color: palette.ink }}>
        {title ?? text}
      </span>
      {description && (
        <span
          style={{
            marginTop: 4,
            fontSize: 13,
            color: palette.mute,
            maxWidth: 360,
          }}
        >
          {description}
        </span>
      )}
      {action && <div style={{ marginTop: 16 }}>{action}</div>}
    </div>
  );
};

/**
 * 列表表格的空状态（给 Table 的 locale.emptyText）：
 * 加载中不显示（避免转圈下面闪「暂无数据」）；出错给「重试」；有筛选条件时为「没有符合条件的…」加「清空筛选」；
 * 否则为首次为空，empty 里写原因和下一步。
 */
export const listEmpty = (o: {
  loading?: boolean;
  error?: boolean;
  filtered?: boolean;
  onRetry?: () => void;
  onClear?: () => void;
  /** 无结果时的对象名，如「订单」→「没有符合条件的订单」 */
  noun?: string;
  empty?: {
    title?: React.ReactNode;
    description?: React.ReactNode;
    action?: React.ReactNode;
  };
}): React.ReactNode => {
  if (o.loading) return <div style={{ height: 160 }} />;
  if (o.error) {
    return (
      <EmptyState
        kind="error"
        description="网络或服务出错，筛选条件已保留"
        action={o.onRetry && <Button onClick={o.onRetry}>重试</Button>}
      />
    );
  }
  if (o.filtered) {
    return (
      <EmptyState
        kind="noResult"
        title={o.noun ? `没有符合条件的${o.noun}` : undefined}
        description="换个关键词，或清空筛选再看"
        action={o.onClear && <Button onClick={o.onClear}>清空筛选</Button>}
      />
    );
  }
  return <EmptyState {...o.empty} />;
};

/** 给 ConfigProvider 的 renderEmpty：表格、列表用完整空状态，下拉等小地方用一行 */
export const renderEmpty = (componentName?: string) =>
  componentName === 'Table' || componentName === 'List' ? (
    <EmptyState />
  ) : (
    <EmptyState compact />
  );

/**
 * 列表表格的空状态（Table 的 locale.emptyText）：加载中不显示（不在转圈下面闪「暂无数据」）；
 * 页面写的一句话按内容配图标——提到筛选、条件的为「无结果」，其余为「还没有数据」；页面自己画的空状态原样显示。
 */
export const tableEmpty = (
  content: React.ReactNode,
  loading?: boolean | { spinning?: boolean },
): React.ReactNode => {
  const busy = typeof loading === 'object' ? loading?.spinning : loading;
  if (busy) return <div style={{ height: 160 }} />;
  if (typeof content === 'string') {
    // 一句话里有「；」「，」时，前半句作标题、后半句作说明（如「没有符合条件的订单」+「换个筛选条件试试」）
    const cut = content.search(/[；，]/);
    const title = cut > 0 ? content.slice(0, cut) : content;
    const description = cut > 0 ? content.slice(cut + 1) : undefined;
    return (
      <EmptyState
        kind={
          /符合|筛选|条件|搜不到|没找到|没有找到/.test(content)
            ? 'noResult'
            : 'empty'
        }
        title={title}
        description={description}
      />
    );
  }
  return content;
};
