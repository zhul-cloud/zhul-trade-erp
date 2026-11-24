import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Input, Segmented, Select, Table } from 'antd';
import { Search } from 'lucide-react';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { CustomerCell } from '@/components/DocFields';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
  QuickFilters,
  SummaryBar,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { Pill } from '@/pages/crm/opportunity/components';
import { PATHS } from '@/pages/sales/components';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import {
  type CustomerGroup,
  type DueFilter,
  filterRows,
  groupByCustomer,
  type ReceivableFilters,
} from './receivables';
import {
  type ReceivableOverview,
  type ReceivableRow,
  receivableApi,
} from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const cny = (v?: number) => formatAmount(v ?? 0, 'CNY').replace('CNY ', '¥');

const DUE_OPTIONS: { value: DueFilter; label: string }[] = [
  { value: 'OVERDUE', label: '已逾期' },
  { value: 'WEEK', label: '7 天内到期' },
  { value: 'MONTH', label: '本月到期' },
  { value: 'NONE', label: '未约定付款日' },
];

/** 到期状态：逾期 N 天 / N 天后到期（7 天内）/ 今天到期 / 未到期 / 未约定 */
const DueState: React.FC<{ r?: ReceivableRow }> = ({ r }) => {
  if (!r?.dueDate) return <Pill tone="cyan">未约定</Pill>;
  if (r.overdueDays != null)
    return <Pill tone="red">逾期 {r.overdueDays} 天</Pill>;
  if (r.dueInDays === 0) return <Pill tone="orange">今天到期</Pill>;
  if (r.dueInDays != null && r.dueInDays <= 7)
    return <Pill tone="orange">{r.dueInDays} 天后到期</Pill>;
  return <Pill tone="green">未到期</Pill>;
};

/** 财务管理 → 应收管理：有效销售订单里还没收齐的款（原型 V1、V2），只读 */
const ReceivablesPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [data, setData] = useState<ReceivableOverview>();
  const [loading, setLoading] = useState(true);
  const [view, setView] = useState<'customer' | 'order'>('customer');
  const [filters, setFilters] = useState<ReceivableFilters>({});
  /** 搜索区里还没点「查询」的条件；到期由快捷筛选立即生效 */
  const [draft, setDraft] = useState<ReceivableFilters>({});
  const apply = () => setFilters(draft);
  const [pageSize, setPageSize] = usePageSize(20);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await receivableApi.overview());
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    load();
  }, [load]);
  useOnReactivate(load);

  const rows = useMemo(
    () => filterRows(data?.rows ?? [], filters),
    [data, filters],
  );
  const groups = useMemo(() => groupByCustomer(rows), [rows]);
  const owners = useMemo(() => {
    const m = new Map<number, string>();
    for (const r of data?.rows ?? []) m.set(r.ownerId, r.ownerName ?? '—');
    return [...m].map(([value, label]) => ({ value, label }));
  }, [data]);
  const currencies = useMemo(
    () =>
      [...new Set((data?.rows ?? []).map((r) => r.currencyCode))].map((c) => ({
        value: c,
        label: c,
      })),
    [data],
  );
  const filteredCny = rows.reduce((s, r) => s + Number(r.remainingCny), 0);

  const s = data?.stats;
  /** 原统计卡片：应收未收为汇总行；已逾期、7 天内、本月到期为快捷筛选（互斥，再点取消） */
  const QUICK: DueFilter[] = ['OVERDUE', 'WEEK', 'MONTH'];
  const quickValue: string[] =
    filters.due && QUICK.includes(filters.due) ? [filters.due] : [];

  const orderLink = (r: ReceivableRow) => (
    <button
      type="button"
      className="zhul-link"
      style={num}
      onClick={() => history.push(PATHS.order(r.orderId))}
    >
      {r.soNo}
    </button>
  );
  const piLink = (r: ReceivableRow) =>
    r.piId && r.piNo ? (
      <button
        type="button"
        className="zhul-link"
        style={num}
        onClick={() => history.push(PATHS.pi(r.piId as number))}
      >
        {r.piNo}
      </button>
    ) : (
      <span style={{ color: palette.mute }}>—</span>
    );
  const money = (v: number, c: string, strong = false) => (
    <span style={{ ...num, fontWeight: strong ? 700 : 400 }}>
      {formatAmount(v, c)}
    </span>
  );
  const customer = (r: {
    customerName?: string;
    customerCountry?: string;
    customerContact?: string;
  }) => (
    <CustomerCell
      name={r.customerName}
      country={r.customerCountry}
      contact={r.customerContact}
    />
  );

  const orderColumns: TableColumnsType<ReceivableRow> = [
    {
      title: '订单号',
      key: 'soNo',
      width: 150,
      render: (_, r) => orderLink(r),
    },
    {
      title: '客户',
      key: 'customer',
      width: 220,
      render: (_, r) => customer(r),
    },
    { title: '来源单号', key: 'piNo', width: 150, render: (_, r) => piLink(r) },
    { title: '业务员', dataIndex: 'ownerName', width: 90 },
    {
      title: '订单金额',
      key: 'total',
      width: 140,
      align: 'right',
      render: (_, r) => money(r.totalAmount, r.currencyCode),
    },
    {
      title: '已收',
      key: 'received',
      width: 130,
      align: 'right',
      render: (_, r) => money(r.receivedAmount, r.currencyCode),
    },
    {
      title: '未收',
      key: 'remaining',
      width: 140,
      align: 'right',
      render: (_, r) => money(r.remainingAmount, r.currencyCode, true),
    },
    {
      title: '折人民币',
      key: 'cny',
      width: 130,
      align: 'right',
      render: (_, r) => <span style={num}>{cny(r.remainingCny)}</span>,
    },
    {
      title: '约定付款日',
      dataIndex: 'dueDate',
      width: 110,
      render: (v?: string) => <span style={num}>{v ?? '—'}</span>,
    },
    {
      title: '状态',
      key: 'state',
      width: 110,
      render: (_, r) => <DueState r={r} />,
    },
  ];

  /** 客户下展开的订单：客户已在上一层，不再重复客户与业务员 */
  const nestedColumns = orderColumns.filter(
    (c) =>
      c.key !== 'customer' &&
      c.key !== 'cny' &&
      !('dataIndex' in c && c.dataIndex === 'ownerName'),
  );
  nestedColumns.splice(2, 0, {
    title: '销售日期',
    dataIndex: 'salesDate',
    width: 110,
    render: (v?: string) => <span style={num}>{v ?? '—'}</span>,
  });

  const customerColumns: TableColumnsType<CustomerGroup> = [
    {
      title: '客户',
      key: 'customer',
      width: 240,
      render: (_, g) => customer(g),
    },
    {
      title: '业务员',
      key: 'owners',
      width: 100,
      render: (_, g) => g.owners.join('、') || '—',
    },
    {
      title: '订单',
      key: 'orders',
      width: 70,
      align: 'right',
      render: (_, g) => <span style={num}>{g.orders.length}</span>,
    },
    {
      title: '未收（原币）',
      key: 'remaining',
      width: 160,
      align: 'right',
      render: (_, g) => (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {g.remaining.map((x) => (
            <span key={x.currency}>{money(x.amount, x.currency, true)}</span>
          ))}
        </div>
      ),
    },
    {
      title: '折人民币',
      key: 'cny',
      width: 130,
      align: 'right',
      render: (_, g) => <span style={num}>{cny(g.remainingCny)}</span>,
    },
    {
      title: '其中逾期',
      key: 'overdue',
      width: 120,
      align: 'right',
      render: (_, g) =>
        g.overdueCny > 0 ? (
          <span style={{ ...num, color: palette.red }}>
            {cny(g.overdueCny)}
          </span>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '最早到期',
      key: 'earliest',
      width: 130,
      render: (_, g) => (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 4,
            alignItems: 'flex-start',
          }}
        >
          <DueState r={g.earliest} />
          {g.earliest?.dueDate && (
            <span style={{ ...num, fontSize: 12, color: palette.mute }}>
              {g.earliest.dueDate}
            </span>
          )}
        </div>
      ),
    },
  ];

  const footer =
    view === 'customer'
      ? `共 ${groups.length} 个客户 · 合计 ${cny(filteredCny)}`
      : `共 ${rows.length} 张订单 · 合计 ${cny(filteredCny)}`;

  return (
    <div>
      <PageHeader />
      <SummaryBar
        items={[
          {
            key: 'total',
            label: '应收未收',
            // 本页金额都写成 ¥（develop 原样），汇总行跟着用
            value: cny(s?.totalCny),
            sub: `${s?.customers ?? 0} 个客户 · ${s?.orders ?? 0} 张订单`,
          },
        ]}
      />
      <FilterCard
        onSearch={apply}
        onReset={() => {
          setDraft({});
          setFilters({});
        }}
        moreCount={filters.due && !QUICK.includes(filters.due) ? 1 : 0}
        more={
          <FilterField label="到期">
            <Select
              allowClear
              placeholder="全部"
              aria-label="到期"
              options={DUE_OPTIONS}
              value={draft.due}
              onChange={(v) => setDraft({ ...draft, due: v })}
            />
          </FilterField>
        }
      >
        <FilterField label="关键词" span={2}>
          <Input
            allowClear
            prefix={<Search size={16} />}
            placeholder="客户、订单编号、PI 编号"
            aria-label="关键词"
            value={draft.keyword}
            onChange={(e) => setDraft({ ...draft, keyword: e.target.value })}
            onPressEnter={apply}
          />
        </FilterField>
        <FilterField label="业务员">
          <Select
            allowClear
            placeholder="全部"
            aria-label="业务员"
            options={owners}
            value={draft.ownerId}
            onChange={(v) => setDraft({ ...draft, ownerId: v })}
          />
        </FilterField>
        <FilterField label="币种">
          <Select
            allowClear
            placeholder="全部"
            aria-label="币种"
            options={currencies}
            value={draft.currency}
            onChange={(v) => setDraft({ ...draft, currency: v })}
          />
        </FilterField>
      </FilterCard>
      <MainCard
        left={footer}
        right={
          <Segmented
            value={view}
            onChange={(v) => setView(v as 'customer' | 'order')}
            options={[
              { value: 'customer', label: '按客户' },
              { value: 'order', label: '按订单' },
            ]}
          />
        }
        quick={
          <QuickFilters
            options={[
              {
                value: 'OVERDUE',
                label: '已逾期',
                count: `${cny(s?.overdueCny)} · ${s?.overdueOrders ?? 0} 张`,
                danger: (s?.overdueOrders ?? 0) > 0,
              },
              {
                value: 'WEEK',
                label: '7 天内到期',
                count: `${cny(s?.weekCny)} · ${s?.weekOrders ?? 0} 张`,
              },
              {
                value: 'MONTH',
                label: '本月到期',
                count: `${cny(s?.monthCny)} · ${s?.monthOrders ?? 0} 张`,
              },
            ]}
            value={quickValue}
            onChange={(v) => {
              const next = v.find((x) => !quickValue.includes(x)) as
                | DueFilter
                | undefined;
              // 快捷筛选立即生效，同步到搜索区的「到期」
              setFilters((f) => ({ ...f, due: next }));
              setDraft((d) => ({ ...d, due: next }));
            }}
          />
        }
      >
        {view === 'customer' ? (
          <Table<CustomerGroup>
            rowKey="customerId"
            loading={loading}
            columns={customerColumns}
            dataSource={groups}
            scroll={{ x: tableScrollX(customerColumns) }}
            expandable={{
              expandedRowRender: (g) => (
                <Table<ReceivableRow>
                  rowKey="orderId"
                  size="small"
                  columns={nestedColumns}
                  dataSource={g.orders}
                  pagination={false}
                  scroll={{ x: tableScrollX(nestedColumns) }}
                />
              ),
            }}
            locale={{ emptyText: tableEmpty('没有未收的款', loading) }}
            pagination={{
              ...PAGER_STYLE,
              pageSize,
              showTotal: () => footer,
              onShowSizeChange: (_, n) => setPageSize(n),
            }}
          />
        ) : (
          <Table<ReceivableRow>
            rowKey="orderId"
            loading={loading}
            columns={orderColumns}
            dataSource={rows}
            scroll={{ x: tableScrollX(orderColumns) }}
            locale={{ emptyText: tableEmpty('没有未收的款', loading) }}
            pagination={{
              ...PAGER_STYLE,
              pageSize,
              showTotal: () => footer,
              onShowSizeChange: (_, n) => setPageSize(n),
            }}
          />
        )}
      </MainCard>
    </div>
  );
};

export default ReceivablesPage;
