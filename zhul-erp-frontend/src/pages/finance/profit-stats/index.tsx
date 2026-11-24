import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Segmented, Select, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { TriangleAlert } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerCell } from '@/components/DocFields';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  HelpTip,
  MainCard,
  PageHeader,
  QuickFilters,
  SummaryBar,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import StatsRangeFilter, { presetRange } from '@/components/StatsRangeFilter';
import { Pill } from '@/pages/inquiry/shared/components';
import { useUserOptions } from '@/pages/sales/orders/dialogs';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { useDeltaColor } from '@/theme/delta';
import { StatsUnitSwitch, useBusinessUnits } from '@/utils/businessUnit';
import { formatAmount } from '@/utils/format';
import { tableScrollX } from '@/utils/tableScroll';
import {
  type ProfitGroup,
  type ProfitOrderRow,
  type ProfitQuery,
  type ProfitTotals,
  profitApi,
} from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const nowrap: React.CSSProperties = { whiteSpace: 'nowrap' };
const cny = (v?: number | null) => formatAmount(v ?? 0, 'CNY');
const rateText = (v?: number | null) => (v == null ? '—' : `${v}%`);

/** 帮助文字：help-texts.md「利润统计」 */
export const HELP = {
  page: '按销售日期汇总订单收入、实际成本与毛利；收入按毛额 × 结汇汇率，平台手续费算成本，口径与订单的成本明细一致',
  unsettled: '收款没结汇、没入库或运费没对账',
  table: '毛利率 = 毛利合计 ÷ 收入合计；按客户时按收入从高到低；点一行展开订单',
} as const;

/** 财务管理 → 利润统计：按销售日期汇总订单的收入、实际成本与毛利（只读） */
const ProfitStatsPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const { of: deltaOf } = useDeltaColor();
  const users = useUserOptions();
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => {
    // 经营统计跳转时 URL 带日期范围
    const p = new URLSearchParams(window.location.search);
    const from = p.get('dateFrom');
    const to = p.get('dateTo');
    return from && to ? [dayjs(from), dayjs(to)] : presetRange('month');
  });
  const [bu] = useBusinessUnits();
  const [unit, setUnit] = useState<number | undefined>(() => {
    const v = new URLSearchParams(window.location.search).get('businessUnitId');
    return v == null ? undefined : Number(v);
  });
  const [ownerId, setOwnerId] = useState<number | undefined>(() => {
    // 从销售统计下钻时 URL 带业务员（UI-015）
    const v = Number(
      new URLSearchParams(window.location.search).get('ownerId'),
    );
    return v > 0 ? v : undefined;
  });
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
        businessUnitId: unit,
      });
      setSummary(res.summary);
      setGroups(res.groups);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [range, unit, ownerId, customerType, settledOnly, groupBy, message]);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  const groupTitle =
    groupBy === 'OWNER' ? '业务员' : groupBy === 'CUSTOMER' ? '客户' : '月份';
  const columns: TableColumnsType<ProfitGroup> = [
    {
      title: groupTitle,
      key: 'label',
      width: groupBy === 'CUSTOMER' ? 260 : 200,
      fixed: 'left',
      render: (_, g) =>
        groupBy === 'CUSTOMER' ? (
          <CustomerCell
            name={g.label}
            country={g.country}
            type={g.customerType}
            contact={g.customerContact}
          />
        ) : (
          <b style={{ color: palette.ink }}>{g.label}</b>
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
        <div style={{ ...num, ...nowrap, color: palette.sub }}>
          <div>新 {cny(g.totals.newRevenue)}</div>
          <div>老 {cny(g.totals.oldRevenue)}</div>
        </div>
      ),
    },
    {
      title: '线上 / 线下收入',
      key: 'channel',
      width: 210,
      align: 'right',
      render: (_, g) => (
        <div style={{ ...num, ...nowrap, color: palette.sub }}>
          <div>线上 {cny(g.totals.onlineRevenue)}</div>
          <div>线下 {cny(g.totals.offlineRevenue)}</div>
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
              color: deltaOf(g.totals.profit),
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
        <button
          type="button"
          className="zhul-link"
          onClick={() => history.push(`/sales/orders/${r.soId}`)}
        >
          {v}
        </button>
      ),
    },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 240,
      render: (v: string | undefined, r) => (
        <CustomerCell
          name={v}
          country={r.customerCountry}
          type={r.customerType}
          contact={r.customerContact}
        />
      ),
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
        <b style={{ ...num, color: deltaOf(v) }}>{cny(v)}</b>
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
      <PageHeader help={HELP.page} />
      {/* 统计条件选了就生效 */}
      <FilterCard
        moreCount={customerType == null ? 0 : 1}
        more={
          <FilterField label="新 / 老客户">
            <Select
              allowClear
              placeholder="全部"
              aria-label="新 / 老客户"
              value={customerType}
              onChange={setCustomerType}
              options={[
                { value: 1, label: '新客户' },
                { value: 2, label: '老客户' },
              ]}
            />
          </FilterField>
        }
      >
        <FilterField label="统计期间" span="auto">
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <StatsRangeFilter value={range} onChange={setRange} />
          </div>
        </FilterField>
        {bu.enabled && (
          <FilterField label="事业部">
            <StatsUnitSwitch info={bu} value={unit} onChange={setUnit} />
          </FilterField>
        )}
        <FilterField label="业务员">
          <Select
            allowClear
            placeholder="全部"
            aria-label="业务员"
            value={ownerId}
            onChange={setOwnerId}
            options={users}
            showSearch={{ optionFilterProp: 'label' }}
          />
        </FilterField>
      </FilterCard>
      {summary && (
        <SummaryBar
          items={[
            {
              key: 'orders',
              label: '订单数',
              value: summary.orderCount,
              sub: `成交客户 ${summary.customerCount}`,
            },
            {
              key: 'revenue',
              label: '收入',
              value: summary.revenue,
              currency: 'CNY',
              sub: `线上 ${cny(summary.onlineRevenue)} · 线下 ${cny(summary.offlineRevenue)}`,
            },
            {
              key: 'cost',
              label: '实际成本',
              value: summary.cost,
              currency: 'CNY',
            },
            {
              key: 'profit',
              label: '毛利',
              value: (
                <span style={{ color: deltaOf(summary.profit) }}>
                  {cny(summary.profit)}
                </span>
              ),
              sub: `毛利率 ${rateText(summary.profitRate)}`,
            },
            {
              key: 'unsettled',
              label: '未结算',
              help: HELP.unsettled,
              value: summary.unsettledCount ? (
                <Pill tone="orange">
                  <TriangleAlert size="1em" /> {summary.unsettledCount} 单
                </Pill>
              ) : (
                '0 单'
              ),
            },
          ]}
        />
      )}
      <MainCard
        left={
          <>
            <span>分组</span>
            <HelpTip title={HELP.table} />
          </>
        }
        right={
          <Segmented<string>
            value={groupBy}
            onChange={(v) => setGroupBy(v as ProfitQuery['groupBy'])}
            options={[
              { label: '按月份', value: 'MONTH' },
              { label: '按业务员', value: 'OWNER' },
              { label: '按客户', value: 'CUSTOMER' },
            ]}
          />
        }
        quick={
          <QuickFilters
            options={[{ value: 'settled', label: '只看已结算' }]}
            value={settledOnly ? ['settled'] : []}
            onChange={(v) => setSettledOnly(v.length > 0)}
          />
        }
      >
        <Table<ProfitGroup>
          rowKey="key"
          loading={loading}
          columns={columns}
          dataSource={groups}
          pagination={false}
          scroll={{ x: tableScrollX(columns) }}
          locale={{ emptyText: tableEmpty('这段时间没有订单', loading) }}
          expandable={{
            expandRowByClick: true,
            expandedRowRender: (g) => (
              <Table<ProfitOrderRow>
                rowKey="soId"
                size="small"
                columns={orderColumns}
                dataSource={g.orders}
                pagination={{
                  pageSize: 10,
                  hideOnSinglePage: true,
                  showSizeChanger: false,
                  showTotal: (t) => `共 ${t} 条`,
                }}
                scroll={{ x: tableScrollX(orderColumns) }}
              />
            ),
          }}
        />
      </MainCard>
    </div>
  );
};

export default ProfitStatsPage;
