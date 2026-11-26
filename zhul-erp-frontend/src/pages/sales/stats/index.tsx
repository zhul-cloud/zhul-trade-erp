import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Segmented, Select, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  ChevronRight,
  CircleCheck,
  FileText,
  MessageSquare,
  Receipt,
  Target,
  Wallet,
} from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { tableEmpty } from '@/components/EmptyState';
import {
  CardTitle,
  FilterCard,
  FilterField,
  HelpTip,
  MainCard,
  PageHeader,
  Panel,
} from '@/components/ListLayout';
import { useBalancedCols } from '@/components/MetricCard';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import StatsRangeFilter, { presetRange } from '@/components/StatsRangeFilter';
import { Pill } from '@/pages/inquiry/shared/components';
import { useCountries } from '@/pages/product/components/useCountries';
import { useUserOptions } from '@/pages/sales/orders/dialogs';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { useDeltaColor } from '@/theme/delta';
import { StatsUnitSwitch, useBusinessUnits } from '@/utils/businessUnit';
import { formatAmount } from '@/utils/format';
import { usePagination } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import {
  type CustomerRow,
  type Rate,
  type SalesStats,
  type Split,
  type StatsRow,
  type StatsView,
  salesStatsApi,
} from './service';

/** 下钻带上当前统计的全部条件：日期、业务员（按业务员分组时取这一行的）、事业部（UI-015） */
export const drillQuery = (
  from: Dayjs,
  to: Dayjs,
  owner?: number | string,
  unit?: number,
) => {
  const q = new URLSearchParams({
    dateFrom: from.format('YYYY-MM-DD'),
    dateTo: to.format('YYYY-MM-DD'),
  });
  if (owner) q.set('ownerId', String(owner));
  if (unit != null) q.set('businessUnitId', String(unit));
  return q.toString();
};
const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const cny = (v?: number | null) => formatAmount(v ?? 0, 'CNY');
const rateText = (v?: Rate) => (v == null ? '—' : `${v}%`);

/** 帮助文字：help-texts.md「销售统计」 */
export const HELP = {
  page: '各项按各自单据的日期统计：商机按创建日期、询盘按询盘日期、报价与 PI 按发送日期、订单按销售日期，所以转化率是同期数量相除。收入与毛利与利润统计一致（收入扣退款）',
  table:
    '新/老 = 新客户 / 老客户。报价与 PI 的新老客户按发送前该客户有没有订单判断。点收入或毛利打开利润统计，点订单数打开订单列表',
} as const;

/** 销售管理 → 销售统计：漏斗、转化率、复购率与收入毛利，新老客户拆分（只读） */
const SalesStatsPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const { zhOf } = useCountries();
  const customerPager = usePagination(50, 'customer');
  const { of: deltaOf } = useDeltaColor();
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

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

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
      `${path}?${drillQuery(from, to, view === 'OWNER' && r.key ? r.key : ownerId, unit)}`,
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
      width: 130,
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
      width: 120,
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
          <div>{r.pending.all || '—'}</div>
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
          <button
            type="button"
            className="zhul-link"
            style={num}
            onClick={() => go('/sales/orders', r)}
          >
            <div>{r.orders.all}</div>
            {sub(`${r.orders.fresh} / ${r.orders.old}`)}
          </button>
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
      width: 140,
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
          <button
            type="button"
            className="zhul-link"
            style={num}
            onClick={() => go('/finance/profit-stats', r)}
          >
            <div style={{ fontWeight: 700 }}>{cny(r.revenue.all)}</div>
            {sub(`${cny(r.revenue.fresh)} / ${cny(r.revenue.old)}`)}
          </button>
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
          <button
            type="button"
            className="zhul-link"
            style={{ ...num, color: deltaOf(r.profit.all) }}
            onClick={() => go('/finance/profit-stats', r)}
          >
            {cny(r.profit.all)}
          </button>
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
          {sub(zhOf(r.country) || '—')}
        </div>
      ),
    },
    {
      title: '新 / 老客户',
      dataIndex: 'customerType',
      width: 110,
      render: (v: number) =>
        v === 2 ? (
          <Pill tone="gray">老客户</Pill>
        ) : (
          <Pill tone="gray">新客户</Pill>
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
        <span style={{ ...num, color: deltaOf(v) }}>{cny(v)}</span>
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
  // 漏斗 6 步：每排最多 6 张，放不下时按行均分（与 MetricGrid 同一算法），每行等宽，比例条可跨行比较
  const funnel = useBalancedCols(6, 6, 150, 24);
  const steps = total
    ? [
        {
          label: '商机',
          icon: <Target size="1em" />,
          value: total.opportunities,
        },
        {
          label: '询盘',
          icon: <MessageSquare size="1em" />,
          value: total.inquiries.all,
          note: `询盘转化 ${rateText(total.inquiryRate)}`,
        },
        {
          label: '报价',
          icon: <FileText size="1em" />,
          value: total.quotations.all,
        },
        {
          label: '意向 PI',
          icon: <Receipt size="1em" />,
          value: total.intents.all,
          note: cny(total.intentAmount.all),
        },
        {
          label: '待付款',
          icon: <Wallet size="1em" />,
          value: total.pending.all,
          note: cny(total.pendingAmount.all),
        },
        {
          label: '成交订单',
          icon: <CircleCheck size="1em" />,
          value: total.orders.all,
          note: `订单转化 ${rateText(total.orderRate)}`,
        },
      ]
    : [];
  const max = Math.max(0, ...steps.map((x) => x.value));
  /**
   * 漏斗一步：一张落在页面上的白卡（D41，对比图 R9）。步骤靠图标、数字和比例条区分，不靠颜色；
   * 只有终点「成交订单」的图标块和条用主题色（条用图表色 palette.chart），其余中性：漏斗是同一个量逐步递减，不是不同类别。
   * 箭头画在卡片左侧的间距里，每行第一张不画。
   * 不用公共 MetricCard：步骤之间要放箭头，终点要跟随主题色，而 MetricCard 的图标块按指标家族固定配色。
   */
  const step = (x: (typeof steps)[number], i: number) => {
    const last = i === steps.length - 1;
    return (
      <Panel
        key={x.label}
        style={{
          position: 'relative',
          minWidth: 0,
          padding: 16,
          display: 'grid',
          gap: 10,
        }}
      >
        {i % funnel.balanced !== 0 && (
          <ChevronRight
            aria-hidden
            size={16}
            style={{
              position: 'absolute',
              left: -20,
              top: '50%',
              transform: 'translateY(-50%)',
              color: palette.faint,
            }}
          />
        )}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span
            aria-hidden
            style={{
              width: 32,
              height: 32,
              flex: 'none',
              borderRadius: 8,
              display: 'grid',
              placeItems: 'center',
              fontSize: 16,
              background: last ? palette.primarySoft : palette.neutralSoft,
              color: last ? palette.link : palette.sub,
            }}
          >
            {x.icon}
          </span>
          <span style={{ fontSize: 13, color: palette.sub }}>{x.label}</span>
        </div>
        <div
          style={{ ...num, fontSize: 24, fontWeight: 700, color: palette.ink }}
        >
          {x.value}
        </div>
        <div
          aria-hidden
          style={{
            height: 6,
            borderRadius: 3,
            background: palette.neutralSoft,
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              width: `${max > 0 ? (x.value / max) * 100 : 0}%`,
              minWidth: x.value > 0 ? 6 : 0,
              height: '100%',
              borderRadius: 3,
              background: last ? palette.chart : palette.chartSoft,
            }}
          />
        </div>
        <div style={{ fontSize: 12, color: palette.mute, minHeight: 18 }}>
          {x.note ?? ''}
        </div>
      </Panel>
    );
  };

  return (
    <div>
      <PageHeader help={HELP.page} />
      {/* 统计条件选了就生效 */}
      <FilterCard>
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
      {total && (
        // 一组数字不外包卡片，标题直接落在页面底上（规范「层级不超过三层」）
        // 分组落在页面底上：上下离卡片 24，比卡片间距 16 大一档（S18）
        <section style={{ paddingTop: 8, marginBottom: 24 }}>
          <div style={{ marginBottom: 12 }}>
            <CardTitle>销售漏斗</CardTitle>
          </div>
          <div
            ref={funnel.ref}
            style={{
              display: 'grid',
              gridTemplateColumns: `repeat(${funnel.balanced}, minmax(0, 1fr))`,
              gap: 24,
            }}
          >
            {steps.map(step)}
          </div>
        </section>
      )}
      <MainCard
        left={
          <>
            <span>分组</span>
            <HelpTip title={HELP.table} />
          </>
        }
        right={
          <Segmented<StatsView>
            value={view}
            onChange={setView}
            options={[
              { label: '按月份', value: 'MONTH' },
              { label: '按业务员', value: 'OWNER' },
              { label: '按客户', value: 'CUSTOMER' },
            ]}
          />
        }
      >
        {view === 'CUSTOMER' ? (
          <Table<CustomerRow>
            rowKey="customerId"
            loading={loading}
            columns={customerColumns}
            dataSource={data?.customers ?? []}
            scroll={{ x: tableScrollX(customerColumns) }}
            // 主卡左上写的是「分组」，客户数写在分页里
            pagination={{
              ...customerPager,
              showTotal: (t) => `共 ${t} 个客户`,
            }}
            locale={{ emptyText: tableEmpty('期间内没有成交客户', loading) }}
          />
        ) : (
          <Table<StatsRow>
            rowKey="key"
            loading={loading}
            columns={columns}
            dataSource={data?.rows ?? []}
            pagination={false}
            scroll={{ x: tableScrollX(columns) }}
            locale={{ emptyText: tableEmpty('期间内没有数据', loading) }}
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
      </MainCard>
    </div>
  );
};

export default SalesStatsPage;
