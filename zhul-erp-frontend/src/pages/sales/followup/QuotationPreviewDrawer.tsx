import { history } from '@umijs/max';
import { Button, Drawer, Skeleton, Table, type TableColumnsType } from 'antd';
import React, { useEffect, useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { formatMargin } from '@/pages/quotation/calc';
import {
  type Quotation,
  type QuotationItem,
  quotationApi,
} from '@/pages/quotation/service';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { Pill } from './components';

/**
 * 跟进中预览报价单：客户收到的那一版的核心信息（spec crm/customer-followup「跟进中预览报价单」）。
 * 复用报价单详情接口，查看范围沿用报价单的规则。
 */
const QuotationPreviewDrawer: React.FC<{
  target?: { id: number; versionNo?: number };
  onClose: () => void;
}> = ({ target, onClose }) => {
  const { palette } = useAppTheme();
  const [q, setQ] = useState<Quotation>();
  const [error, setError] = useState('');

  const load = React.useCallback(async () => {
    if (!target) return;
    setQ(undefined);
    setError('');
    try {
      setQ(await quotationApi.detail(target.id, target.versionNo));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [target]);

  useEffect(() => {
    load();
  }, [load]);

  const cur = q?.currencyCode ?? 'USD';
  const columns: TableColumnsType<QuotationItem> = [
    {
      title: '型号',
      key: 'model',
      render: (_, i) => (
        <div>
          <div style={{ fontWeight: 600 }}>{i.model}</div>
          <div style={{ fontSize: 12, color: palette.mute }}>{i.brand}</div>
          {!i.noStockLine && i.replacementModel && (
            <div style={{ fontSize: 12, color: palette.orange }}>
              停产 · 替代 {i.replacementModel}
            </div>
          )}
        </div>
      ),
    },
    { title: '数量', dataIndex: 'quantity', width: 70, align: 'right' },
    {
      title: '单价',
      key: 'unitPrice',
      width: 120,
      align: 'right',
      render: (_, i) =>
        i.noStockLine ? (
          <span style={{ color: palette.mute }}>无货</span>
        ) : (
          formatAmount(i.unitPrice, cur)
        ),
    },
    {
      title: '小计',
      key: 'amount',
      width: 130,
      align: 'right',
      render: (_, i) => (i.noStockLine ? '—' : formatAmount(i.amount, cur)),
    },
  ];

  const line = (label: string, value: React.ReactNode, strong = false) => (
    <div
      key={label}
      style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}
    >
      <span style={{ color: palette.sub }}>{label}</span>
      <span style={{ fontWeight: strong ? 700 : 400 }}>{value}</span>
    </div>
  );

  return (
    <Drawer
      open={!!target}
      onClose={onClose}
      size="min(640px, 96vw)"
      destroyOnHidden
      title={
        q ? (
          <span
            style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}
          >
            {q.quotationNo}
            <Pill tone={q.sentAt ? 'green' : 'gray'}>{q.statusName}</Pill>
            <Pill tone="gray">第 {q.versionNo} 版</Pill>
          </span>
        ) : (
          '报价单'
        )
      }
      footer={
        q && (
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button
              onClick={() => history.push(`/quotation/quotations/${q.id}`)}
            >
              打开完整报价单
            </Button>
          </div>
        )
      }
    >
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : !q ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : (
        <div style={{ display: 'grid', gap: 16 }}>
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: '4px 18px',
              fontSize: 13,
              color: palette.sub,
            }}
          >
            <span>{q.customerName}</span>
            {q.sentAt && <span>{formatDateTime(q.sentAt)} 发出</span>}
            {q.validUntil && <span>有效期至 {q.validUntil}</span>}
            {q.incoterm && (
              <span>
                {q.incoterm}
                {q.incotermPlace ? ` ${q.incotermPlace}` : ''}
              </span>
            )}
          </div>
          <Table<QuotationItem>
            rowKey="id"
            size="small"
            columns={columns}
            dataSource={q.items}
            pagination={false}
          />
          <div
            style={{
              display: 'grid',
              gap: 6,
              padding: '12px 14px',
              borderRadius: 12,
              background: palette.inset,
              fontSize: 13,
            }}
          >
            {line('货款', formatAmount(q.itemAmount, cur))}
            {q.fees.map((f) => line(f.feeName, formatAmount(f.amount, cur)))}
            {line('合计', formatAmount(q.totalAmount, cur), true)}
            {q.netProfitCny != null &&
              line(
                '毛利（内部）',
                `${formatAmount(q.netProfitCny, 'CNY')}${
                  q.marginRate != null ? ` · ${formatMargin(q.marginRate)}` : ''
                }`,
              )}
          </div>
        </div>
      )}
    </Drawer>
  );
};

export default QuotationPreviewDrawer;
