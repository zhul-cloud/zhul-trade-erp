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

/** 骨架行里各列灰条的宽度（按行交替，避免整齐得像真数据） */
const SKELETON_BARS = [
  ['72%', '48%', '56%', '64%'],
  ['60%', '40%', '44%', '52%'],
];

/**
 * 表格首次加载（表里还没有行）的骨架行（ui-redesign S17、对比图 R23 方案 B）：和真实行同高的灰条，
 * 数据回来时原位替换、页面不跳。300ms 后才显示，快的请求不闪；表格里有骨架时不再叠 antd 的转圈
 * （两条都在 global.less 的「列表加载」）。compact：详情、卡片里的小表格，两行 44 高
 */
export const TableSkeleton: React.FC<{ compact?: boolean }> = ({ compact }) => {
  const { palette } = useAppTheme();
  const rows = compact ? 2 : 5;
  const bar = (width: string, height = 12) => (
    <span
      className="zhul-skeleton-bar"
      style={{
        display: 'block',
        width,
        height,
        borderRadius: 4,
        background: palette.neutralSoft,
      }}
    />
  );
  return (
    <div
      className="zhul-table-skeleton"
      role="status"
      aria-label="加载中"
      style={{ textAlign: 'left' }}
    >
      {Array.from({ length: rows }, (_, i) => {
        const w = SKELETON_BARS[i % 2];
        return (
          <div
            // biome-ignore lint/suspicious/noArrayIndexKey: 固定行数的占位，没有数据标识
            key={i}
            style={{
              height: compact ? 44 : 60,
              display: 'grid',
              gridTemplateColumns: compact ? '1fr' : '3fr 3fr 2fr 2fr',
              gap: 24,
              alignItems: 'center',
              borderBottom:
                i < rows - 1 ? `1px solid ${palette.hairline}` : undefined,
            }}
          >
            {compact ? (
              bar(w[0])
            ) : (
              <>
                <span style={{ display: 'grid', gap: 8 }}>
                  {bar(w[0])}
                  {bar(w[1], 10)}
                </span>
                <span style={{ display: 'grid', gap: 8 }}>
                  {bar(w[2])}
                  {bar(w[3], 10)}
                </span>
                {bar(w[1])}
                <span style={{ display: 'grid', justifyItems: 'end' }}>
                  {bar(w[0])}
                </span>
              </>
            )}
          </div>
        );
      })}
    </div>
  );
};

/**
 * 列表表格的空状态（给 Table 的 locale.emptyText）：
 * 加载中显示骨架行（TableSkeleton，不在转圈下面闪「暂无数据」）；出错给「重试」；有筛选条件时为「没有符合条件的…」加「清空筛选」；
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
  if (o.loading) return <TableSkeleton />;
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
 * 列表表格的空状态（Table 的 locale.emptyText）：加载中显示骨架行（TableSkeleton，不在转圈下面闪「暂无数据」）；
 * 页面写的一句话按内容配图标——提到筛选、条件的为「无结果」，其余为「还没有数据」；页面自己画的空状态原样显示。
 * compact：详情、卡片、抽屉里的小表格用一行版（图标 + 一句话），不占 160 高。
 */
export const tableEmpty = (
  content: React.ReactNode,
  loading?: boolean | { spinning?: boolean },
  compact?: boolean,
): React.ReactNode => {
  const busy = typeof loading === 'object' ? loading?.spinning : loading;
  if (busy) return <TableSkeleton compact={compact} />;
  if (typeof content === 'string') {
    // 一句话里有「；」「，」时，前半句作标题、后半句作说明（如「没有符合条件的订单」+「换个筛选条件试试」）
    const cut = content.search(/[；，]/);
    const title = cut > 0 ? content.slice(0, cut) : content;
    const description = cut > 0 ? content.slice(cut + 1) : undefined;
    const kind = /符合|筛选|条件|搜不到|没找到|没有找到/.test(content)
      ? 'noResult'
      : 'empty';
    return compact ? (
      <EmptyState compact kind={kind} title={content} />
    ) : (
      <EmptyState kind={kind} title={title} description={description} />
    );
  }
  return content;
};
