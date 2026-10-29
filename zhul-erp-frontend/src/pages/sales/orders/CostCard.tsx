import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Popover, Table } from 'antd';
import React from 'react';
import type {
  CostLine,
  CostSource,
  OrderCost,
} from '@/pages/finance/profit-stats/service';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

const SOURCE_PATH: Record<string, (id: number) => string | undefined> = {
  PO: (id) => `/purchase/orders/${id}`,
  SHIPMENT: (id) => `/logistics/shipments/${id}`,
  EXPENSE: () => '/finance/expenses',
  COURIER: () => undefined,
};

/** 一项成本：为 0 显示「—」；有来源单据时悬停展开，点编号跳到单据 */
const Amount: React.FC<{
  value: number;
  sources?: CostSource[];
  dim?: boolean;
  negative?: boolean;
}> = ({ value, sources, dim, negative }) => {
  const { palette } = useAppTheme();
  if (!value) return <span style={{ color: palette.mute }}>—</span>;
  const text = (
    <span
      style={{
        ...num,
        color: negative ? palette.green : dim ? palette.mute : palette.ink,
        cursor: sources?.length ? 'pointer' : undefined,
        borderBottom: sources?.length
          ? `1px dashed ${palette.hairline}`
          : undefined,
      }}
    >
      {negative ? '−' : ''}
      {formatAmount(value, 'CNY')}
    </span>
  );
  if (!sources?.length) return text;
  return (
    <Popover
      content={
        <div style={{ display: 'grid', gap: 6, maxWidth: 360 }}>
          {sources.map((s) => {
            const path = SOURCE_PATH[s.type]?.(s.id);
            return (
              <div key={`${s.type}-${s.id}`} style={{ fontSize: 12 }}>
                {path ? (
                  <a onClick={() => history.push(path)}>{s.no}</a>
                ) : (
                  <span>{s.no}</span>
                )}
                {s.amount != null && (
                  <span style={{ marginLeft: 8 }}>
                    {formatAmount(s.amount, s.type === 'PO' ? '' : 'CNY')}
                  </span>
                )}
                {s.note && (
                  <span style={{ marginLeft: 8, color: palette.mute }}>
                    {s.note}
                  </span>
                )}
              </div>
            );
          })}
        </div>
      }
    >
      {text}
    </Popover>
  );
};

/** 订单成本明细：按型号行列出各项成本、行收入与行毛利（spec finance/order-cost） */
const OrderCostCard: React.FC<{ cost: OrderCost }> = ({ cost }) => {
  const { palette } = useAppTheme();
  const money = (v: number, src?: CostSource[]) => (
    <Amount value={v} sources={src} />
  );
  const columns: TableColumnsType<CostLine> = [
    {
      title: '型号',
      key: 'model',
      width: 180,
      fixed: 'left',
      render: (_, l) => (
        <div>
          <b style={{ color: palette.ink }}>{l.model}</b> × {l.quantity}
          {l.unorderedQty > 0 && (
            <div style={{ fontSize: 12, color: palette.orange }}>
              {l.unorderedQty} 个没下采购订单
              {l.missingCostPrice ? '，没有采购成本价' : '，按采购成本价估算'}
            </div>
          )}
          {l.unreceivedQty > 0 && (
            <div style={{ fontSize: 12, color: palette.orange }}>
              {l.unreceivedQty} 个没入库
            </div>
          )}
        </div>
      ),
    },
    {
      title: '采购货款',
      key: 'purchase',
      width: 110,
      align: 'right',
      render: (_, l) => (
        <Amount
          value={l.purchase}
          sources={l.sources.purchase}
          dim={!l.purchaseSettled}
        />
      ),
    },
    {
      title: '开票成本',
      dataIndex: 'invoice',
      width: 90,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '采购费用',
      dataIndex: 'poFee',
      width: 90,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '质量索赔',
      dataIndex: 'claim',
      width: 90,
      align: 'right',
      render: (v: number) => <Amount value={v} negative />,
    },
    {
      title: '退货运费',
      dataIndex: 'returnFreight',
      width: 90,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '国内运费',
      key: 'dom',
      width: 90,
      align: 'right',
      render: (_, l) => money(l.domestic, l.sources.domestic),
    },
    {
      title: '国际运费',
      key: 'intl',
      width: 100,
      align: 'right',
      render: (_, l) => (
        <Amount
          value={l.international}
          sources={l.sources.international}
          dim={!l.internationalSettled}
        />
      ),
    },
    {
      title: '费用单',
      key: 'expense',
      width: 90,
      align: 'right',
      render: (_, l) => money(l.expense, l.sources.expense),
    },
    {
      title: '收款手续费',
      dataIndex: 'receiptFee',
      width: 100,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '实际成本',
      dataIndex: 'cost',
      width: 110,
      align: 'right',
      render: (v: number) => (
        <b style={{ ...num, color: palette.ink }}>{formatAmount(v, 'CNY')}</b>
      ),
    },
    {
      title: '退款',
      dataIndex: 'refund',
      width: 100,
      align: 'right',
      render: (v: number) =>
        v ? (
          <span style={{ ...num, color: palette.orange }}>
            −{formatAmount(v, 'CNY')}
          </span>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '行收入',
      dataIndex: 'revenue',
      width: 110,
      align: 'right',
      render: (v: number) => <span style={num}>{formatAmount(v, 'CNY')}</span>,
    },
    {
      title: '行毛利',
      key: 'profit',
      width: 140,
      align: 'right',
      render: (_, l) => (
        <b
          style={{ ...num, color: l.profit >= 0 ? palette.green : palette.red }}
        >
          {formatAmount(l.profit, 'CNY')}
          {l.profitRate != null && (
            <span style={{ fontWeight: 400, fontSize: 12 }}>
              {' '}
              · {l.profitRate}%
            </span>
          )}
        </b>
      ),
    },
  ];
  if (cost.historySnapshot) {
    return <SnapshotCostCard cost={cost} />;
  }
  return (
    <Card style={{ padding: 20, marginBottom: 16 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          flexWrap: 'wrap',
          marginBottom: 12,
        }}
      >
        <b style={{ fontSize: 16, color: palette.ink }}>成本明细</b>
        {cost.settled ? (
          <Pill tone="green">已结算</Pill>
        ) : (
          <Pill tone="orange">未结算</Pill>
        )}
        {!cost.settled && (
          <span style={{ fontSize: 12, color: palette.orange }}>
            还差：{cost.pending.join(' · ')}
          </span>
        )}
        <span style={{ flex: 1 }} />
        <span style={{ fontSize: 12, color: palette.mute }}>
          浅色为还没确定的数字；虚线数字可以悬停查看来源单据
        </span>
      </div>
      <Table<CostLine>
        rowKey="soItemId"
        size="small"
        columns={columns}
        dataSource={cost.lines}
        pagination={false}
        scroll={{ x: 1600 }}
        summary={() => (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0} colSpan={10}>
              <b>合计</b>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={10} align="right">
              <b style={num}>{formatAmount(cost.cost, 'CNY')}</b>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={11} align="right">
              {cost.refund ? (
                <span style={{ ...num, color: palette.orange }}>
                  −{formatAmount(cost.refund, 'CNY')}
                </span>
              ) : (
                <span style={{ color: palette.mute }}>—</span>
              )}
            </Table.Summary.Cell>
            <Table.Summary.Cell index={12} align="right">
              <span style={num}>{formatAmount(cost.revenue, 'CNY')}</span>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={13} align="right">
              <b
                style={{
                  ...num,
                  color: cost.profit >= 0 ? palette.green : palette.red,
                }}
              >
                {formatAmount(cost.profit, 'CNY')}
                {cost.profitRate != null ? ` · ${cost.profitRate}%` : ''}
              </b>
            </Table.Summary.Cell>
          </Table.Summary.Row>
        )}
      />
      {cost.refunds.length > 0 && (
        <div style={{ marginTop: 12, display: 'grid', gap: 6 }}>
          {cost.refunds.map((r) => (
            <div
              key={r.id}
              style={{
                display: 'flex',
                gap: 10,
                alignItems: 'center',
                flexWrap: 'wrap',
                fontSize: 13,
              }}
            >
              <b style={{ color: palette.ink }}>退款</b>
              <a onClick={() => history.push('/finance/refunds')}>
                {r.refundNo}
              </a>
              <span style={{ color: palette.sub }}>
                {r.refundDate} · {r.reasonName}
              </span>
              <span style={{ flex: 1 }} />
              <span style={{ ...num, color: palette.orange }}>
                {formatAmount(r.amount, r.currencyCode)} →{' '}
                {formatAmount(r.amountCny, 'CNY')}
              </span>
            </div>
          ))}
        </div>
      )}
    </Card>
  );
};

/** 历史迁移订单的成本：取旧系统《成本核算》的快照，不随入库、付款、结汇、对账变化（spec finance/order-cost） */
const SnapshotCostCard: React.FC<{ cost: OrderCost }> = ({ cost }) => {
  const { palette } = useAppTheme();
  const s = cost.snapshot ?? {};
  const item = (label: string, value: React.ReactNode) => (
    <div key={label}>
      <div style={{ fontSize: 12, color: palette.sub, marginBottom: 4 }}>
        {label}
      </div>
      <div
        style={{
          fontSize: 14,
          color: palette.ink,
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {value}
      </div>
    </div>
  );
  const grid: React.CSSProperties = {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))',
    gap: 16,
    marginBottom: 16,
  };
  return (
    <Card style={{ padding: 20, marginBottom: 16 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          flexWrap: 'wrap',
          marginBottom: 16,
        }}
      >
        <b style={{ fontSize: 16, color: palette.ink }}>订单成本</b>
        <Pill tone="orange">历史迁移：按旧系统成本核算</Pill>
        {(s.sourceCodes || s.accountingMonth) && (
          <span style={{ fontSize: 12, color: palette.mute }}>
            {s.sourceCodes ? `来源核算单 ${s.sourceCodes}` : ''}
            {s.sourceCodes && s.accountingMonth ? ' · ' : ''}
            {s.accountingMonth ? `核算月份 ${s.accountingMonth}` : ''}
          </span>
        )}
      </div>
      <div style={grid}>
        {item('收入', formatAmount(cost.revenue, 'CNY'))}
        {item('实际成本', formatAmount(cost.cost, 'CNY'))}
        {item(
          '毛利',
          <span
            style={{ color: cost.profit >= 0 ? palette.green : palette.red }}
          >
            {formatAmount(cost.profit, 'CNY')}
          </span>,
        )}
        {item('毛利率', cost.profitRate != null ? `${cost.profitRate}%` : '—')}
        {item(
          '结算状态',
          cost.settled ? (
            <Pill tone="green">已结算</Pill>
          ) : (
            <Pill tone="orange">未结算</Pill>
          ),
        )}
      </div>
      <div style={grid}>
        {item('采购货款', formatAmount(s.purchaseAmount ?? 0, 'CNY'))}
        {item('运费', formatAmount(s.freight ?? 0, 'CNY'))}
        {item('附加费', formatAmount(s.surcharge ?? 0, 'CNY'))}
        {item('交易服务费', formatAmount(s.serviceFee ?? 0, 'CNY'))}
        {item('退款', formatAmount(cost.refund ?? 0, 'CNY'))}
      </div>
      <div style={{ fontSize: 12, color: palette.mute }}>
        迁移订单的收入、成本、毛利与结算状态取旧系统《成本核算》的值，不随入库、付款、结汇、对账变化。
      </div>
    </Card>
  );
};

export default OrderCostCard;
