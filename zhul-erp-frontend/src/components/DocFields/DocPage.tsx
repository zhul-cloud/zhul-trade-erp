import { RightOutlined } from '@ant-design/icons';
import { Link } from '@umijs/max';
import React from 'react';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import type { Tone } from '@/pages/inquiry/shared/constants';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';

/**
 * 单据详情页的公共部件（单据页统一规范，售后退货单据化起使用）：
 * 基本信息网格、关联单据卡片、操作日志卡片、列表的「型号 · 数量」单元格。
 */

export const SectionTitle: React.FC<{
  children: React.ReactNode;
  extra?: React.ReactNode;
}> = ({ children, extra }) => {
  const { palette } = useAppTheme();
  return (
    <div style={{ display: 'flex', alignItems: 'center', marginBottom: 14 }}>
      <b style={{ color: palette.ink, fontSize: 15 }}>{children}</b>
      <span style={{ flex: 1 }} />
      {extra}
    </div>
  );
};

/** 基本信息：标签在上、值在下，自动换行成多列 */
export const InfoGrid: React.FC<{
  title?: string;
  items: { label: string; value?: React.ReactNode; hidden?: boolean }[];
}> = ({ title = '基本信息', items }) => {
  const { palette } = useAppTheme();
  return (
    <Card style={{ padding: 20, marginBottom: 16 }}>
      <SectionTitle>{title}</SectionTitle>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(170px, 1fr))',
          gap: '16px 24px',
        }}
      >
        {items
          .filter((i) => !i.hidden)
          .map((i) => (
            <div key={i.label} style={{ minWidth: 0 }}>
              <div style={{ color: palette.mute, fontSize: 12 }}>{i.label}</div>
              <div
                style={{
                  color: palette.ink,
                  marginTop: 4,
                  overflowWrap: 'anywhere',
                }}
              >
                {i.value === undefined || i.value === null || i.value === ''
                  ? '—'
                  : i.value}
              </div>
            </div>
          ))}
      </div>
    </Card>
  );
};

export interface RelatedDoc {
  label: string;
  no?: string;
  href?: string;
  statusName?: string;
  tone?: Tone;
  /** 状态后面的补充，如金额 */
  extra?: React.ReactNode;
}

/** 关联单据：每行「类型 单号 状态 >」，点击打开单据详情 */
export const RelatedDocsCard: React.FC<{
  docs: RelatedDoc[];
  title?: React.ReactNode;
}> = ({ docs, title = '关联单据' }) => {
  const { palette } = useAppTheme();
  const list = docs.filter((d) => d.no);
  return (
    <Card style={{ padding: 20, marginBottom: 16 }}>
      <SectionTitle>{title}</SectionTitle>
      {list.length === 0 ? (
        <div style={{ color: palette.mute, fontSize: 13 }}>暂无</div>
      ) : (
        <div style={{ display: 'grid', gap: 8 }}>
          {list.map((d) => {
            const key = `${d.label}-${d.no}`;
            const row = (
              <div
                key={key}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  padding: '10px 12px',
                  borderRadius: 10,
                  background: palette.inset,
                  fontSize: 13,
                }}
              >
                <span style={{ color: palette.sub, width: 72, flexShrink: 0 }}>
                  {d.label}
                </span>
                <span
                  style={{
                    color: d.href ? palette.link : palette.ink,
                    fontWeight: 600,
                    whiteSpace: 'nowrap',
                  }}
                >
                  {d.no}
                </span>
                <span style={{ flex: 1 }} />
                {d.extra}
                {d.statusName && (
                  <Pill tone={d.tone ?? 'gray'}>{d.statusName}</Pill>
                )}
                {d.href && (
                  <RightOutlined
                    style={{ color: palette.mute, fontSize: 11 }}
                  />
                )}
              </div>
            );
            return d.href ? (
              <Link key={key} to={d.href}>
                {row}
              </Link>
            ) : (
              row
            );
          })}
        </div>
      )}
    </Card>
  );
};

/** 操作日志：最新在上 */
export const DocLogCard: React.FC<{
  logs?: {
    id: number;
    action: string;
    content?: string;
    operatorName?: string;
    createTime: string;
  }[];
}> = ({ logs }) => {
  const { palette } = useAppTheme();
  return (
    <Card style={{ padding: 20, marginBottom: 16 }}>
      <SectionTitle>操作日志</SectionTitle>
      {!logs?.length ? (
        <div style={{ color: palette.mute, fontSize: 13 }}>暂无</div>
      ) : (
        <div style={{ display: 'grid', gap: 14 }}>
          {logs.map((l) => (
            <div key={l.id} style={{ display: 'flex', gap: 10 }}>
              <span
                style={{
                  width: 7,
                  height: 7,
                  borderRadius: 4,
                  marginTop: 7,
                  flexShrink: 0,
                  background: palette.link,
                }}
              />
              <div style={{ minWidth: 0 }}>
                <div
                  style={{
                    color: palette.ink,
                    fontSize: 13,
                    overflowWrap: 'anywhere',
                  }}
                >
                  <b>{l.action}</b>
                  {l.content ? `：${l.content}` : ''}
                </div>
                <div
                  style={{ color: palette.mute, fontSize: 12, marginTop: 2 }}
                >
                  {[l.operatorName, formatDateTime(l.createTime).slice(5, 16)]
                    .filter(Boolean)
                    .join(' · ')}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </Card>
  );
};

/** 列表「型号 · 数量」：第一个型号，下面一行「共 N 个型号 · M 件」 */
export const ModelQtyCell: React.FC<{
  items?: { model: string; quantity: number }[];
  summary?: string;
  totalQty?: number;
}> = ({ items, summary, totalQty }) => {
  const { palette } = useAppTheme();
  const first = items?.[0]?.model ?? summary?.split(' × ')[0];
  const count = items?.length ?? (summary ? summary.split('、').length : 0);
  const qty =
    totalQty ?? items?.reduce((s, i) => s + (i.quantity ?? 0), 0) ?? 0;
  if (!first) return <span style={{ color: palette.mute }}>—</span>;
  return (
    <div style={{ minWidth: 0 }}>
      <div
        title={summary}
        style={{
          fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
          fontWeight: 600,
          color: palette.ink,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {first}
      </div>
      <div style={{ color: palette.mute, fontSize: 12 }}>
        {count > 1 ? `共 ${count} 个型号 · ` : ''}
        {qty} 件
      </div>
    </div>
  );
};
