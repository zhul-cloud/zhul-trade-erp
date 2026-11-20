import { RightOutlined } from '@ant-design/icons';
import { Link } from '@umijs/max';
import { Button, Modal, Table } from 'antd';
import React, { useState } from 'react';
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

export interface ModelQtyLine {
  model: string;
  quantity: number;
  brand?: string;
  category?: string;
}

/** 摘要「A × 2、B × 3」拆成型号行（退货类列表只返回摘要） */
const parseSummary = (summary?: string): ModelQtyLine[] =>
  (summary ?? '')
    .split('、')
    .map((p) => p.trim())
    .filter(Boolean)
    .map((p) => {
      const i = p.lastIndexOf(' × ');
      return i < 0
        ? { model: p, quantity: 0 }
        : { model: p.slice(0, i), quantity: Number(p.slice(i + 3)) || 0 };
    });

/**
 * 列表「型号 · 数量」：第一个型号，下面一行「共 N 个型号 · M 件 · 查看」；
 * 多个型号时点「查看」弹出全部型号（openspec doc-list-unify），可打开详情
 */
export const ModelQtyCell: React.FC<{
  items?: ModelQtyLine[];
  summary?: string;
  totalQty?: number;
  /** 弹窗标题里的单号 · 对方 */
  docLabel?: string;
  onOpenDetail?: () => void;
}> = ({ items, summary, totalQty, docLabel, onOpenDetail }) => {
  const { palette } = useAppTheme();
  const [open, setOpen] = useState(false);
  const lines = items ?? parseSummary(summary);
  const first = lines[0]?.model;
  const count = lines.length;
  const qty = totalQty ?? lines.reduce((s, i) => s + (i.quantity ?? 0), 0);
  if (!first) return <span style={{ color: palette.mute }}>—</span>;
  const showBrand = lines.some((l) => l.brand || l.category);
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
        {count > 1 && (
          <>
            {' · '}
            <button
              type="button"
              className="zhul-link"
              onClick={() => setOpen(true)}
            >
              查看
            </button>
          </>
        )}
      </div>
      {open && (
        <Modal
          open
          width={showBrand ? 640 : 480}
          title={
            <div>
              <div>
                {count} 个型号 · {qty} 件
              </div>
              {docLabel && (
                <div
                  style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}
                >
                  {docLabel}
                </div>
              )}
            </div>
          }
          onCancel={() => setOpen(false)}
          footer={
            <>
              {onOpenDetail && (
                <Button
                  onClick={() => {
                    setOpen(false);
                    onOpenDetail();
                  }}
                >
                  打开详情
                </Button>
              )}
              <Button onClick={() => setOpen(false)}>关闭</Button>
            </>
          }
        >
          <Table<ModelQtyLine & { no: number }>
            size="small"
            rowKey="no"
            pagination={false}
            scroll={{ y: 420 }}
            dataSource={lines.map((l, i) => ({ ...l, no: i + 1 }))}
            columns={[
              { title: '#', dataIndex: 'no', width: 48 },
              ...(showBrand
                ? [
                    {
                      title: '品牌',
                      dataIndex: 'brand',
                      width: 110,
                      render: (v?: string) => v || '—',
                    },
                    {
                      title: '品类',
                      dataIndex: 'category',
                      width: 100,
                      render: (v?: string) => v || '—',
                    },
                  ]
                : []),
              {
                title: '型号',
                dataIndex: 'model',
                render: (v: string) => (
                  <span
                    style={{
                      fontFamily:
                        'ui-monospace, SFMono-Regular, Menlo, monospace',
                      fontWeight: 600,
                    }}
                  >
                    {v}
                  </span>
                ),
              },
              {
                title: '数量',
                dataIndex: 'quantity',
                width: 80,
                align: 'right' as const,
              },
            ]}
          />
        </Modal>
      )}
    </div>
  );
};
