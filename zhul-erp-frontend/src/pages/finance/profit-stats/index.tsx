import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, DatePicker, Segmented, Select, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { useUserOptions } from '@/pages/sales/orders/dialogs';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import {
  type ProfitGroup,
  type ProfitOrderRow,
  type ProfitQuery,
  type ProfitTotals,
  profitApi,
} from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const cny = (v?: number | null) => formatAmount(v ?? 0, 'CNY');
const rateText = (v?: number | null) => (v == null ? '—' : `${v}%`);

/** 财务管理 → 利润统计：按销售日期汇总订单的收入、实际成本与毛利（只读） */
const ProfitStatsPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const users = useUserOptions();
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => {
    // 经营统计跳转时 URL 带日期范围
    const p = new URLSearchParams(window.location.search);
    const from = p.get('dateFrom');
    const to = p.get('dateTo');
    return from && to
      ? [dayjs(from), dayjs(to)]
      : [dayjs().startOf('year'), dayjs()];
  });
  const [ownerId, setOwnerId] = useState<number>();
  const [customerType, setCustomerType] = useState<number>();
  const [settledOnly, setSettledOnly] = useState(false);
  const [groupBy, setGroupBy] = useState<ProfitQuery['groupBy']>('MONTH');
  const [summary, setSummary] = useState<ProfitTotals>();
  const [groups, setGroups] = useState<ProfitGroup[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await profitApi.stats({
        dateFrom: range[0].format('YYYY-MM-DD'),
        dateTo: range[1].format('YYYY-MM-DD'),
        ownerId,
        customerType,
        settledOnly: settledOnly || undefined,
        groupBy,
      });
      setSummary(res.summary);
      setGroups(res.groups);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [range, ownerId, customerType, settledOnly, groupBy, message]);

  useEffect(() => {
    load();
  }, [load]);

  const stat = (
    label: string,
    value: React.ReactNode,
    sub?: React.ReactNode,
    color?: string,
  ) => (
    <div
      style={{
        flex: '1 1 200px',
        padding: '16px 18px',
        borderRadius: 14,
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
      }}
    >
      <div style={{ fontSize: 13, color: palette.sub }}>{label}</div>
      <div
        style={{
          ...num,
          fontSize: 22,
          fontWeight: 800,
          color: color ?? palette.ink,
          marginTop: 4,
        }}
      >
        {value}
      </div>
      {sub && (
        <div style={{ fontSize: 12, color: palette.mute, marginTop: 2 }}>
          {sub}
        </div>
      )}
    </div>
  );

  const groupTitle =
    groupBy === 'OWNER' ? '业务员' : groupBy === 'CUSTOMER' ? '客户' : '月份';
  const columns: TableColumnsType<ProfitGroup> = [
    {
      title: groupTitle,
      key: 'label',
      width: 200,
      fixed: 'left',
      render: (_, g) => (
        <div>
          <b style={{ color: palette.ink }}>{g.label}</b>
          {g.country && (
            <div style={{ fontSize: 12, color: palette.mute }}>{g.country}</div>
          )}
        </div>
      ),
    },
    {
      title: '订单数',
      key: 'count',
      width: 110,
      render: (_, g) => (
        <div>
          {g.totals.orderCount}
          <div style={{ fontSize: 12, color: palette.mute }}>
            新 {g.totals.newOrderCount} · 老 {g.totals.oldOrderCount}
          </div>
        </div>
      ),
    },
    {
      title: '收入',
      key: 'revenue',
      width: 150,
      align: 'right',
      sorter: (a, b) => a.totals.revenue - b.totals.revenue,
      render: (_, g) => <b style={num}>{cny(g.totals.revenue)}</b>,
    },
    {
      title: '新客户 / 老客户收入',
      key: 'newOld',
      width: 200,
      align: 'right',
      render: (_, g) => (
        <span style={{ ...num, color: palette.sub }}>
          {cny(g.totals.newRevenue)} / {cny(g.totals.oldRevenue)}
        </span>
      ),
    },
    {
      title: '线上 / 线下收入',
      key: 'channel',
      width: 210,
      align: 'right',
      render: (_, g) => (
        <div style={{ ...num, color: palette.sub }}>
          {cny(g.totals.onlineRevenue)} / {cny(g.totals.offlineRevenue)}
          {g.totals.unreceivedRevenue > 0 && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              未到账 {cny(g.totals.unreceivedRevenue)}
            </div>
          )}
        </div>
      ),
    },
    {
      title: '实际成本',
      key: 'cost',
      width: 140,
      align: 'right',
      render: (_, g) => <span style={num}>{cny(g.totals.cost)}</span>,
    },
    {
      title: '毛利',
      key: 'profit',
      width: 160,
      align: 'right',
      sorter: (a, b) => a.totals.profit - b.totals.profit,
      render: (_, g) => (
        <div>
          <b
            style={{
              ...num,
              color: g.totals.profit >= 0 ? palette.green : palette.red,
            }}
          >
            {cny(g.totals.profit)}
          </b>
          <div style={{ fontSize: 12, color: palette.mute }}>
            新 {cny(g.totals.newProfit)} · 老 {cny(g.totals.oldProfit)}
          </div>
        </div>
      ),
    },
    {
      title: '毛利率',
      key: 'rate',
      width: 90,
      align: 'right',
      sorter: (a, b) => (a.totals.profitRate ?? 0) - (b.totals.profitRate ?? 0),
      render: (_, g) => rateText(g.totals.profitRate),
    },
    {
      title: '未结算',
      key: 'unsettled',
      width: 80,
      render: (_, g) =>
        g.totals.unsettledCount ? (
          <Pill tone="orange">{g.totals.unsettledCount}</Pill>
        ) : (
          <span style={{ color: palette.mute }}>0</span>
        ),
    },
  ];

  const orderColumns: TableColumnsType<ProfitOrderRow> = [
    {
      title: '订单',
      dataIndex: 'soNo',
      width: 170,
      render: (v: string, r) => (
        <a onClick={() => history.push(`/sales/orders/${r.soId}`)}>{v}</a>
      ),
    },
    { title: '客户', dataIndex: 'customerName', width: 200, ellipsis: true },
    {
      title: '新 / 老',
      dataIndex: 'customerType',
      width: 70,
      render: (v?: number) => (v === 2 ? '老' : '新'),
    },
    { title: '业务员', dataIndex: 'ownerName', width: 90 },
    { title: '销售日期', dataIndex: 'salesDate', width: 110 },
    {
      title: '收入',
      dataIndex: 'revenue',
      width: 130,
      align: 'right',
      render: (v: number) => <span style={num}>{cny(v)}</span>,
    },
    {
      title: '实际成本',
      dataIndex: 'cost',
      width: 130,
      align: 'right',
      render: (v: number) => <span style={num}>{cny(v)}</span>,
    },
    {
      title: '毛利',
      dataIndex: 'profit',
      width: 120,
      align: 'right',
      render: (v: number) => (
        <b style={{ ...num, color: v >= 0 ? palette.green : palette.red }}>
          {cny(v)}
        </b>
      ),
    },
    {
      title: '毛利率',
      dataIndex: 'profitRate',
      width: 80,
      align: 'right',
      render: rateText,
    },
    {
      title: '结算',
      key: 'settled',
      width: 200,
      render: (_, r) =>
        r.settled ? (
          <Pill tone="green">已结算</Pill>
        ) : (
          <div>
            <Pill tone="orange">未结算</Pill>
            <div style={{ fontSize: 12, color: palette.mute }}>
              {r.pending.join(' · ')}
            </div>
          </div>
        ),
    },
  ];

  return (
    <div>
      <PageTitle
        crumbs={['利润统计']}
        title="利润统计"
        description="按销售日期汇总订单收入、实际成本与毛利；收入按毛额 × 结汇汇率，平台手续费算成本，口径与订单的成本明细一致。"
      />
      <div
        style={{
          display: 'flex',
          gap: 10,
          flexWrap: 'wrap',
          marginBottom: 16,
          alignItems: 'center',
        }}
      >
        <DatePicker.RangePicker
          value={range}
          allowClear={false}
          onChange={(v) => v?.[0] && v[1] && setRange([v[0], v[1]])}
        />
        <Select
          allowClear
          placeholder="业务员"
          style={{ width: 140 }}
          value={ownerId}
          onChange={setOwnerId}
          options={users}
          showSearch={{ optionFilterProp: 'label' }}
        />
        <Select
          allowClear
          placeholder="新 / 老客户"
          style={{ width: 130 }}
          value={customerType}
          onChange={setCustomerType}
          options={[
            { value: 1, label: '新客户' },
            { value: 2, label: '老客户' },
          ]}
        />
        <Segmented<string>
          value={settledOnly ? 'settled' : 'all'}
          onChange={(v) => setSettledOnly(v === 'settled')}
          options={[
            { label: '包含未结算', value: 'all' },
            { label: '只看已结算', value: 'settled' },
          ]}
        />
      </div>
      {summary && (
        <div
          style={{
            display: 'flex',
            gap: 14,
            flexWrap: 'wrap',
            marginBottom: 16,
          }}
        >
          {stat(
            '订单数',
            summary.orderCount,
            `成交客户 ${summary.customerCount}`,
          )}
          {stat(
            '收入',
            cny(summary.revenue),
            `线上 ${cny(summary.onlineRevenue)} · 线下 ${cny(summary.offlineRevenue)}`,
          )}
          {stat('实际成本', cny(summary.cost))}
          {stat(
            '毛利',
            cny(summary.profit),
            `毛利率 ${rateText(summary.profitRate)}`,
            summary.profit >= 0 ? palette.green : palette.red,
          )}
          {stat(
            '未结算',
            `${summary.unsettledCount} 单`,
            '收款没结汇、没入库或运费没对账',
            summary.unsettledCount ? palette.orange : undefined,
          )}
        </div>
      )}
      <Card>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            marginBottom: 14,
            flexWrap: 'wrap',
          }}
        >
          <b style={{ color: palette.ink }}>分组</b>
          <Segmented<string>
            value={groupBy}
            onChange={(v) => setGroupBy(v as ProfitQuery['groupBy'])}
            options={[
              { label: '按月份', value: 'MONTH' },
              { label: '按业务员', value: 'OWNER' },
              { label: '按客户', value: 'CUSTOMER' },
            ]}
          />
          <span style={{ flex: 1 }} />
          <span style={{ fontSize: 12, color: palette.mute }}>
            毛利率 = 毛利合计 ÷ 收入合计；点一行展开订单
          </span>
        </div>
        <Table<ProfitGroup>
          rowKey="key"
          loading={loading}
          columns={columns}
          dataSource={groups}
          pagination={false}
          scroll={{ x: 1400 }}
          locale={{ emptyText: '这段时间没有订单' }}
          expandable={{
            expandRowByClick: true,
            expandedRowRender: (g) => (
              <Table<ProfitOrderRow>
                rowKey="soId"
                size="small"
                columns={orderColumns}
                dataSource={g.orders}
                pagination={false}
                scroll={{ x: 1300 }}
              />
            ),
          }}
        />
      </Card>
    </div>
  );
};

export default ProfitStatsPage;
