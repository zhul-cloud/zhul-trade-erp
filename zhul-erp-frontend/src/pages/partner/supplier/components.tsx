import { Link } from '@umijs/max';
import { Breadcrumb } from 'antd';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import {
  labelOf,
  SUPPLIER_TYPE_OPTIONS,
  SUPPLIER_TYPE_TONE,
  type SupplierTypeTone,
} from './constants';

export const LIST_PATH = '/partner/suppliers';

/** 供应商类型胶囊；0（存量数据、询盘内联创建）显示「未设置」 */
export const SupplierTypePill: React.FC<{ value?: number }> = ({ value }) => {
  const { palette } = useAppTheme();
  const colors: Record<SupplierTypeTone, { fg: string; bg: string }> = {
    accent: { fg: palette.link, bg: palette.accentSoft },
    cyan: { fg: palette.cyan, bg: palette.inset },
    orange: { fg: palette.orange, bg: palette.orangeSoft },
    green: { fg: palette.green, bg: palette.greenSoft },
    gray: { fg: palette.sub, bg: palette.inset },
  };
  const c = colors[SUPPLIER_TYPE_TONE[value ?? 0] ?? 'gray'];
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        height: 24,
        padding: '0 10px',
        borderRadius: 12,
        fontSize: 12,
        fontWeight: 600,
        color: c.fg,
        background: c.bg,
        whiteSpace: 'nowrap',
      }}
    >
      {labelOf(SUPPLIER_TYPE_OPTIONS, value) ?? '未设置'}
    </span>
  );
};

/** 状态胶囊（详情页用；列表里是开关） */
export const StatusPill: React.FC<{ status?: number }> = ({ status }) => {
  const { palette } = useAppTheme();
  const enabled = status === 1;
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        height: 24,
        padding: '0 10px',
        borderRadius: 12,
        fontSize: 12,
        fontWeight: 600,
        color: enabled ? palette.green : palette.sub,
        background: enabled ? palette.greenSoft : palette.inset,
      }}
    >
      {enabled ? '启用' : '禁用'}
    </span>
  );
};

/** 面包屑 + 标题 + 右侧操作 */
export const PageTitle: React.FC<{
  title: string;
  current?: string;
  description?: string;
  actions?: React.ReactNode;
}> = ({ title, current, description, actions }) => {
  const { palette } = useAppTheme();
  const items = [
    { title: '客商管理' },
    current
      ? { title: <Link to={LIST_PATH}>供应商管理</Link> }
      : { title: '供应商管理' },
    ...(current ? [{ title: current }] : []),
  ];
  return (
    <header
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-end',
        gap: 16,
        marginBottom: 20,
        flexWrap: 'wrap',
      }}
    >
      <div>
        <Breadcrumb items={items} style={{ fontSize: 13 }} />
        <h1
          id="supplier-main"
          tabIndex={-1}
          style={{
            fontSize: 28,
            fontWeight: 700,
            margin: '8px 0 0',
            color: palette.ink,
          }}
        >
          {title}
        </h1>
        {description && (
          <p style={{ margin: '6px 0 0', color: palette.sub, fontSize: 14 }}>
            {description}
          </p>
        )}
      </div>
      {actions && (
        <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
          {actions}
        </div>
      )}
    </header>
  );
};

/** 分组卡片：图标 + 标题，下方是内容 */
export const SectionCard: React.FC<{
  icon: React.ReactNode;
  title: string;
  children: React.ReactNode;
}> = ({ icon, title, children }) => {
  const { palette } = useAppTheme();
  const id = `supplier-section-${title}`;
  return (
    <section
      aria-labelledby={id}
      style={{
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
        borderRadius: 16,
        padding: 24,
        marginBottom: 20,
      }}
    >
      <h2
        id={id}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          margin: '0 0 20px',
          fontSize: 16,
          fontWeight: 600,
          color: palette.ink,
        }}
      >
        <span style={{ color: palette.link, fontSize: 15 }}>{icon}</span>
        {title}
      </h2>
      {children}
    </section>
  );
};
