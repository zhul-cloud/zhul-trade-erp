import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Segmented, Select, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import StatsRangeFilter, { presetRange } from '@/components/StatsRangeFilter';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { useUserOptions } from '@/pages/sales/orders/dialogs';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { StatsUnitSwitch, useBusinessUnits } from '@/utils/businessUnit';
import { formatAmount } from '@/utils/format';
import {
  type CustomerRow,
  type Rate,
  type SalesStats,
  type Split,
  type StatsRow,
  type StatsView,
  salesStatsApi,
} from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const cny = (v?: number | null) => formatAmount(v ?? 0, 'CNY');
const rateText = (v?: Rate) => (v == null ? '—' : `${v}%`);

/** 销售管理 → 销售统计：漏斗、转化率、复购率与收入毛利，新老客户拆分（只读） */
const SalesStatsPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const users = useUserOptions();
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() =>
    presetRange('month'),
  );
  const [bu] = useBusinessUnits();
  const [unit, setUnit] = useState<number>();
  const [ownerId, setOwnerId] = useState<number>();
  const [view, setView] = useState<StatsView>('MONTH');
  const [data, setData] = useState<SalesStats>();
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(
        await salesStatsApi.stats({
          dateFrom: range[0].format('YYYY-MM-DD'),
          dateTo: range[1].format('YYYY-MM-DD'),
          ownerId,
          view,
          businessUnitId: unit,
        }),
      );
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [range, unit, ownerId, view, message]);

  useEffect(() => {
    load();
  }, [load]);

  /** 行对应的日期范围：月份行为该月（不超出筛选范围），其余为整个筛选范围 */
  const rangeOf = (r: StatsRow) => {
    if (view !== 'MONTH' || !r.key) return range;
    const start = dayjs(`${r.key}-01`);
    return [
      start.isBefore(range[0]) ? range[0] : start,
      start.endOf('month').isAfter(range[1]) ? range[1] : start.endOf('month'),
    ];
  };
  const go = (path: string, r: StatsRow) => {
    const [from, to] = rangeOf(r);
    history.push(
      `${path}?dateFrom=${from.format('YYYY-MM-DD')}&dateTo=${to.format('YYYY-MM-DD')}`,
    );
  };

  const sub = (t: React.ReactNode) => (
    <div style={{ fontSize: 12, color: palette.mute }}>{t}</div>
  );
  /** 合计 + 新/老 */
  const split = (s: Split, money = false) => {
    const f = (v: number) => (money ? cny(v) : v);
    return (
      <div style={num}>
        <div>{s.all ? f(s.all) : '—'}</div>
        {s.all > 0 && sub(`${f(s.fresh)} / ${f(s.old)}`)}
      </div>
    );
  };
  const rate = (all: Rate, fresh?: Rate, old?: Rate) => (
    <div style={num}>
      <div>{rateText(all)}</div>
      {(fresh !== undefined || old !== undefined) &&
        all != null &&
        sub(
          `${rateText(fresh)}${old !== undefined ? ` / ${rateText(old)}` : ''}`,
        )}
    </div>
  );

  const columns: TableColumnsType<StatsRow> = [
    {
      title: view === 'OWNER' ? '业务员' : '月份',
      dataIndex: 'label',
      width: 110,
      fixed: 'left',
      render: (v: string) => <b style={{ color: palette.ink }}>{v}</b>,
    },
    {
      title: '商机',
      dataIndex: 'opportunities',
      width: 70,
      align: 'right',
      render: (v: number) => <span style={num}>{v || '—'}</span>,
    },
    {
      title: '询盘 新/老',
      key: 'inquiries',
      width: 100,
      align: 'right',
      render: (_, r) => split(r.inquiries),
    },
    {
      title: '询盘转化 新',
      key: 'inquiryRate',
      width: 100,
      align: 'right',
      render: (_, r) => rate(r.inquiryRate, r.inquiryRateNew),
    },
    {
      title: '报价 新/老',
      key: 'quotations',
      width: 100,
      align: 'right',
      render: (_, r) => split(r.quotations),
    },
    {
      title: '意向 PI',
      key: 'intents',
      width: 130,
      align: 'right',
      render: (_, r) => (
        <div style={num}>
          <div>{r.intents.all || '—'}</div>
          {r.intents.all > 0 && sub(cny(r.intentAmount.all))}
        </div>
      ),
    },
    {
      title: '待付款',
      key: 'pending',
      width: 130,
      align: 'right',
      render: (_, r) => (
        <div style={num}>
          <div style={{ color: r.pending.all ? palette.orange : undefined }}>
            {r.pending.all || '—'}
          </div>
          {r.pending.all > 0 && sub(cny(r.pendingAmount.all))}
        </div>
      ),
    },
    {
      title: '订单 新/老',
      key: 'orders',
      width: 100,
      align: 'right',
      render: (_, r) =>
        r.orders.all ? (
          <a style={num} onClick={() => go('/sales/orders', r)}>
            <div>{r.orders.all}</div>
            {sub(`${r.orders.fresh} / ${r.orders.old}`)}
          </a>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '订单转化 新/老',
      key: 'orderRate',
      width: 130,
      align: 'right',
      render: (_, r) => rate(r.orderRate, r.orderRateNew, r.orderRateOld),
    },
    {
      title: '整体转化',
      key: 'overallRate',
      width: 90,
      align: 'right',
      render: (_, r) => rate(r.overallRate),
    },
    {
      title: '成交客户 新/老',
      key: 'customers',
      width: 120,
      align: 'right',
      render: (_, r) => split(r.customers),
    },
    {
      title: '平均笔单价 新/老',
      key: 'avgPrice',
      width: 190,
      align: 'right',
      render: (_, r) => (
        <div style={num}>
          <div>{r.avgPrice == null ? '—' : cny(r.avgPrice)}</div>
          {r.avgPrice != null &&
            sub(
              `${r.avgPriceNew == null ? '—' : cny(r.avgPriceNew)} / ${r.avgPriceOld == null ? '—' : cny(r.avgPriceOld)}`,
            )}
        </div>
      ),
    },
    {
      title: '订单复购',
      key: 'orderRepeatRate',
      width: 90,
      align: 'right',
      render: (_, r) => rate(r.orderRepeatRate),
    },
    {
      title: '客户复购',
      key: 'customerRepeatRate',
      width: 90,
      align: 'right',
      render: (_, r) => rate(r.customerRepeatRate),
    },
    {
      title: '销售收入 新/老',
      key: 'revenue',
      width: 200,
      align: 'right',
      render: (_, r) =>
        r.revenue.all ? (
          <a style={num} onClick={() => go('/finance/profit-stats', r)}>
            <div style={{ fontWeight: 700 }}>{cny(r.revenue.all)}</div>
            {sub(`${cny(r.revenue.fresh)} / ${cny(r.revenue.old)}`)}
          </a>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '毛利',
      key: 'profit',
      width: 140,
      align: 'right',
      render: (_, r) =>
        r.revenue.all ? (
          <a
            style={{ ...num, color: palette.green }}
            onClick={() => go('/finance/profit-stats', r)}
          >
            {cny(r.profit.all)}
          </a>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '毛利率 新/老',
      key: 'profitRate',
      width: 130,
      align: 'right',
      render: (_, r) => rate(r.profitRate, r.profitRateNew, r.profitRateOld),
    },
  ];

  const customerColumns: TableColumnsType<CustomerRow> = [
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 260,
      render: (v: string, r) => (
        <div>
          <b style={{ color: palette.ink }}>{v}</b>
          {sub(r.country ?? '—')}
        </div>
      ),
    },
    {
      title: '新 / 老客户',
      dataIndex: 'customerType',
      width: 110,
      render: (v: number) =>
        v === 2 ? (
          <Pill tone="accent">老客户</Pill>
        ) : (
          <Pill tone="green">新客户</Pill>
        ),
    },
    {
      title: '订单数',
      dataIndex: 'orders',
      width: 90,
      align: 'right',
      render: (v: number) => <span style={num}>{v}</span>,
    },
    {
      title: '销售收入',
      dataIndex: 'revenue',
      width: 150,
      align: 'right',
      render: (v: number) => <b style={num}>{cny(v)}</b>,
    },
    {
      title: '毛利',
      dataIndex: 'profit',
      width: 140,
      align: 'right',
      render: (v: number) => (
        <span style={{ ...num, color: palette.green }}>{cny(v)}</span>
      ),
    },
    {
      title: '毛利率',
      dataIndex: 'profitRate',
      width: 90,
      align: 'right',
      render: rateText,
    },
  ];

  const total = data?.total;
  const step = (label: string, value: number, note?: string) => (
    <div style={{ flex: '1 1 120px', minWidth: 120 }}>
      <div style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}>
        {label}
      </div>
      <div
        style={{
          ...num,
          height: 40,
          display: 'flex',
          alignItems: 'center',
          padding: '0 14px',
          borderRadius: 8,
          background: '#2563EB',
          color: '#fff',
          fontSize: 18,
          fontWeight: 800,
        }}
      >
        {value}
      </div>
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
        {note ?? ' '}
      </div>
    </div>
  );

  return (
    <div>
      <PageTitle description="各项按各自单据的日期统计：商机按创建日期、询盘按询盘日期、报价与 PI 按发送日期、订单按销售日期，所以转化率是同期数量相除。收入与毛利与利润统计一致（收入扣退款）。" />
      <div
        style={{
          display: 'flex',
          gap: 10,
          flexWrap: 'wrap',
          marginBottom: 16,
          alignItems: 'center',
        }}
      >
        <StatsRangeFilter value={range} onChange={setRange} />
        <StatsUnitSwitch info={bu} value={unit} onChange={setUnit} />
        <Select
          allowClear
          placeholder="业务员"
          style={{ width: 140 }}
          value={ownerId}
          onChange={setOwnerId}
          options={users}
          showSearch={{ optionFilterProp: 'label' }}
        />
        <Segmented<StatsView>
          value={view}
          onChange={setView}
          options={[
            { label: '按月份', value: 'MONTH' },
            { label: '按业务员', value: 'OWNER' },
            { label: '按客户', value: 'CUSTOMER' },
          ]}
        />
      </div>
      {total && (
        <Card style={{ marginBottom: 16 }}>
          <div
            style={{ fontWeight: 700, color: palette.ink, marginBottom: 12 }}
          >
            漏斗（所选期间）
          </div>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            {step('商机', total.opportunities)}
            {step(
              '询盘',
              total.inquiries.all,
              `询盘转化 ${rateText(total.inquiryRate)}`,
            )}
            {step('报价', total.quotations.all)}
            {step('意向 PI', total.intents.all, cny(total.intentAmount.all))}
            {step('待付款', total.pending.all, cny(total.pendingAmount.all))}
            {step(
              '成交订单',
              total.orders.all,
              `订单转化 ${rateText(total.orderRate)}`,
            )}
          </div>
        </Card>
      )}
      <Card>
        {view === 'CUSTOMER' ? (
          <Table<CustomerRow>
            rowKey="customerId"
            loading={loading}
            columns={customerColumns}
            dataSource={data?.customers ?? []}
            scroll={{ x: 900 }}
            pagination={{ pageSize: 50, showTotal: (t) => `共 ${t} 个客户` }}
            locale={{ emptyText: '期间内没有成交客户' }}
          />
        ) : (
          <Table<StatsRow>
            rowKey="key"
            loading={loading}
            columns={columns}
            dataSource={data?.rows ?? []}
            pagination={false}
            scroll={{ x: 2100 }}
            locale={{ emptyText: '期间内没有数据' }}
            summary={() =>
              total ? (
                <Table.Summary fixed>
                  <Table.Summary.Row>
                    {columns.map((c, i) => {
                      const render = c.render as (
                        v: unknown,
                        r: StatsRow,
                        i: number,
                      ) => React.ReactNode;
                      const key = String(
                        c.key ?? (c as { dataIndex?: string }).dataIndex,
                      );
                      const dataIndex = (c as { dataIndex?: keyof StatsRow })
                        .dataIndex;
                      return (
                        <Table.Summary.Cell
                          key={key}
                          index={i}
                          align={(c as { align?: 'left' | 'right' }).align}
                        >
                          {render(
                            dataIndex ? total[dataIndex] : undefined,
                            total,
                            0,
                          )}
                        </Table.Summary.Cell>
                      );
                    })}
                  </Table.Summary.Row>
                </Table.Summary>
              ) : null
            }
          />
        )}
        <div style={{ fontSize: 12, color: palette.mute, marginTop: 10 }}>
          新/老 = 新客户 / 老客户。报价与 PI
          的新老客户按发送前该客户有没有订单判断。点收入或毛利打开利润统计，点订单数打开订单列表。
        </div>
      </Card>
    </div>
  );
};

export default SalesStatsPage;
