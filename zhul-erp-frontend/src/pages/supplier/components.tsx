import React from 'react';
import { Pill } from '@/components/Pill';
import { useAppTheme } from '@/theme/AppTheme';
import { attrTone } from '@/theme/palette';
import { labelOf, SUPPLIER_TYPE_OPTIONS } from './constants';

export const LIST_PATH = '/supplier/list';

/** 供应商类型；0（存量数据、询盘内联创建）显示「未设置」。类型是属性，用公共中性标签（ui-redesign S13） */
/** 供应商类型：取值固定，按选项顺序取属性色；「其他」「未设置」用灰（D75） */
export const SupplierTypePill: React.FC<{ value?: number }> = ({ value }) => {
  const i = SUPPLIER_TYPE_OPTIONS.findIndex((o) => o.value === value);
  const other = i < 0 || value === 5;
  return (
    <Pill tone={other ? 'gray' : attrTone(i)}>
      {labelOf(SUPPLIER_TYPE_OPTIONS, value) ?? '未设置'}
    </Pill>
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
        <span style={{ color: palette.link, fontSize: 16 }}>{icon}</span>
        {title}
      </h2>
      {children}
    </section>
  );
};
