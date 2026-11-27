import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Select, Table } from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  HelpTip,
  MainCard,
  PageHeader,
  SummaryBar,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { Pill } from '@/pages/inquiry/shared/components';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { useDeltaColor } from '@/theme/delta';
import { StatsUnitSwitch, useBusinessUnits } from '@/utils/businessUnit';
import { formatAmount } from '@/utils/format';
import {
  type OperatingReport,
  type OperatingRow,
  operatingApi,
} from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const cny = (v?: number | null) => formatAmount(v ?? 0, 'CNY');
const rateText = (v?: number | null) => (v == null ? '—' : `${v}%`);

/** 财务管理 → 经营统计：按月的销售毛利、退款、其他收入、运营成本与预计盈亏（只读） */
const OperatingReportPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const { of: deltaOf } = useDeltaColor();
  const thisYear = dayjs().year();
  const [year, setYear] = useState(thisYear);
  const [data, setData] = useState<OperatingReport>();
  const [loading, setLoading] = useState(true);
  const [bu] = useBusinessUnits();
  /** 查看的事业部：空为全公司（没有合计权限的由后端默认为本事业部） */
  const [unit, setUnit] = useState<number>();
  const viewUnit = data?.businessUnitId;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await operatingApi.year(year, unit));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [year, unit, message]);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  /** 某月的日期范围（全年合计为整年） */
  const rangeOf = (r: OperatingRow) => {
    const start =
      r.month === 0
        ? dayjs(`${year}-01-01`)
        : dayjs(`${year}-${String(r.month).padStart(2, '0')}-01`);
    const end = r.month === 0 ? start.endOf('year') : start.endOf('month');
    return {
      dateFrom: start.format('YYYY-MM-DD'),
      dateTo: end.format('YYYY-MM-DD'),
    };
  };
  const go = (path: string, r: OperatingRow, extra = '') => {
    const { dateFrom, dateTo } = rangeOf(r);
    const unitParam =
      viewUnit != null && !extra.includes('businessUnitId')
        ? `&businessUnitId=${viewUnit}`
        : '';
    history.push(
      `${path}?dateFrom=${dateFrom}&dateTo=${dateTo}${extra}${unitParam}`,
    );
  };
  /** 表格里金额为 0 一律写灰色「—」（同一张表统一，会计报表写法；汇总行的大数字照常写 0，复审 P16） */
  const dash = <span style={{ color: palette.mute }}>—</span>;
  const link = (
    value: number,
    onClick: () => void,
    color?: string,
    bold?: boolean,
  ) =>
    value ? (
      <button
        type="button"
        className="zhul-link"
        onClick={onClick}
        style={{
          ...num,
          color: color ?? palette.ink,
          fontWeight: bold ? 600 : 400,
        }}
      >
        {cny(value)}
      </button>
    ) : (
      dash
    );

  const columns: TableColumnsType<OperatingRow> = [
    {
      title: '月份',
      dataIndex: 'label',
      width: 90,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <b style={{ color: palette.ink }}>{v}</b>
          {r.unsettledCount > 0 && (
            <div>
              <Pill tone="orange">未结算 {r.unsettledCount}</Pill>
            </div>
          )}
        </div>
      ),
    },
    {
      title: '销售收入',
      key: 'revenue',
      width: 140,
      align: 'right',
      render: (_, r) =>
        link(r.revenue, () => go('/finance/profit-stats', r), undefined, true),
    },
    {
      title: '销售成本',
      dataIndex: 'cost',
      width: 140,
      align: 'right',
      render: (v: number) => (v ? <span style={num}>{cny(v)}</span> : dash),
    },
    {
      title: '销售毛利',
      key: 'profit',
      width: 130,
      align: 'right',
      render: (_, r) =>
        link(r.profit, () => go('/finance/profit-stats', r), deltaOf(r.profit)),
    },
    {
      title: '毛利率',
      dataIndex: 'profitRate',
      width: 80,
      align: 'right',
      render: rateText,
    },
    {
      title: '退款',
      key: 'refund',
      width: 110,
      align: 'right',
      render: (_, r) => (
        <span
          title={
            r.refund
              ? `售后退款 ${cny(r.refund)}：退出现金 ${cny(r.refund - (r.refundToCredit ?? 0))}，转客户余额 ${cny(r.refundToCredit ?? 0)}；余额提现不计入`
              : undefined
          }
        >
          {link(r.refund, () => go('/finance/refunds', r))}
        </span>
      ),
    },
    {
      title: '其他收入',
      key: 'other',
      width: 110,
      align: 'right',
      render: (_, r) =>
        link(r.otherIncome, () => go('/finance/other-incomes', r)),
    },
    ...(bu.enabled
      ? [
          {
            title: '直接费用',
            key: 'direct',
            width: 120,
            align: 'right' as const,
            render: (_: unknown, r: OperatingRow) =>
              link(r.opexDirectTotal, () =>
                go('/finance/expenses', r, '&linked=false'),
              ),
          },
          ...(viewUnit != null
            ? [
                {
                  title: '分摊比例',
                  key: 'share',
                  width: 90,
                  align: 'right' as const,
                  render: (_: unknown, r: OperatingRow) =>
                    r.sharePercent == null ? (
                      <span style={{ color: palette.mute }}>—</span>
                    ) : (
                      <span style={num}>
                        {(Number(r.sharePercent) * 100).toFixed(2)}%
                      </span>
                    ),
                },
              ]
            : []),
          {
            title: viewUnit == null ? '公共费用' : '分摊的公共成本',
            key: 'shared',
            width: 130,
            align: 'right' as const,
            render: (_: unknown, r: OperatingRow) =>
              link(r.opexSharedTotal, () =>
                go('/finance/expenses', r, '&linked=false&businessUnitId=0'),
              ),
          },
        ]
      : (data?.categories ?? []).map((c) => ({
          title: c.name,
          key: `opex-${c.code}`,
          width: 105,
          align: 'right' as const,
          render: (_: unknown, r: OperatingRow) =>
            link(r.opex[c.code] ?? 0, () =>
              go('/finance/expenses', r, `&category=${c.code}&linked=false`),
            ),
        }))),
    {
      title: '运营成本',
      dataIndex: 'opexTotal',
      width: 130,
      align: 'right',
      render: (v: number) => (v ? <b style={num}>{cny(v)}</b> : dash),
    },
    {
      title: '预计盈亏',
      dataIndex: 'net',
      width: 140,
      align: 'right',
      render: (v: number) =>
        v ? <b style={{ ...num, color: deltaOf(v) }}>{cny(v)}</b> : dash,
    },
    {
      title: '净利率',
      dataIndex: 'netRate',
      width: 80,
      align: 'right',
      render: rateText,
    },
  ];

  const total = data?.total;

  return (
    <div>
      <PageHeader
        help={
          bu.enabled
            ? `预计盈亏 = 销售毛利 − 退款 + 其他收入 − 直接费用 − 分摊的公共成本。直接费用是归属本事业部的费用单；公共费用每月按销售额比例分给各事业部（每个事业部至少 ${bu.minSharePercent}%），各事业部之和等于全公司`
            : '预计盈亏 = 销售毛利 − 退款 + 其他收入 − 运营成本。这里的毛利不扣退款（按销售日期归月），退款按退款月份单列；运营成本为不关联订单的费用单，年费等按分摊后的月份计'
        }
      />
      {/* 统计条件选了就生效 */}
      <FilterCard>
        <FilterField label="年份">
          <Select
            value={year}
            onChange={setYear}
            aria-label="年份"
            options={[0, 1, 2, 3].map((d) => ({
              value: thisYear - d,
              label: `${thisYear - d} 年`,
            }))}
          />
        </FilterField>
        {bu.enabled && (
          <FilterField label="事业部">
            <StatsUnitSwitch info={bu} value={unit} onChange={setUnit} />
          </FilterField>
        )}
      </FilterCard>
      {total && (
        <SummaryBar
          items={[
            {
              key: 'revenue',
              label: '销售收入',
              value: total.revenue,
              currency: 'CNY',
              sub: `${total.orderCount} 单`,
            },
            {
              key: 'profit',
              label: '销售毛利',
              value: (
                <span style={{ color: deltaOf(total.profit) }}>
                  {cny(total.profit)}
                </span>
              ),
              sub: `毛利率 ${rateText(total.profitRate)}`,
            },
            {
              key: 'refund',
              label: '退款与其他收入',
              value: total.refund ? `−${cny(total.refund)}` : cny(0),
              sub: `其他收入 ${cny(total.otherIncome)}`,
            },
            {
              key: 'opex',
              label: '运营成本',
              value: total.opexTotal,
              currency: 'CNY',
              sub: bu.enabled
                ? `直接 ${cny(total.opexDirectTotal)} · ${viewUnit == null ? '公共' : '分摊'} ${cny(total.opexSharedTotal)}`
                : undefined,
            },
            {
              key: 'net',
              label: '预计盈亏',
              value: (
                <span style={{ color: deltaOf(total.net) }}>
                  {cny(total.net)}
                </span>
              ),
              sub: `净利率 ${rateText(total.netRate)}`,
            },
          ]}
        />
      )}
      <MainCard
        left={
          <>
            <span>按月份</span>
            <HelpTip title="点数字打开来源：收入与毛利 → 利润统计；退款、其他收入、运营成本 → 对应列表" />
          </>
        }
      >
        <Table<OperatingRow>
          rowKey="month"
          loading={loading}
          columns={columns}
          dataSource={data?.rows ?? []}
          pagination={false}
          expandable={
            bu.enabled
              ? {
                  expandedRowRender: (r) => (
                    <Table
                      size="small"
                      rowKey="code"
                      pagination={false}
                      style={{ maxWidth: 560 }}
                      dataSource={(data?.categories ?? []).map((c) => ({
                        code: c.code,
                        name: c.name,
                        direct: r.opexDirect[c.code] ?? 0,
                        shared: r.opexShared[c.code] ?? 0,
                      }))}
                      columns={[
                        { title: '类别', dataIndex: 'name', width: 160 },
                        {
                          title: '直接费用',
                          dataIndex: 'direct',
                          align: 'right',
                          render: (v: number) => (
                            <span style={num}>{v ? cny(v) : '—'}</span>
                          ),
                        },
                        {
                          title:
                            viewUnit == null ? '公共费用' : '分摊的公共成本',
                          dataIndex: 'shared',
                          align: 'right',
                          render: (v: number) => (
                            <span style={num}>{v ? cny(v) : '—'}</span>
                          ),
                        },
                      ]}
                    />
                  ),
                }
              : undefined
          }
          scroll={{
            x: bu.enabled ? 1700 : 1500 + (data?.categories.length ?? 0) * 105,
          }}
          locale={{ emptyText: tableEmpty('这一年还没有数据', loading) }}
          summary={() =>
            total ? (
              <Table.Summary fixed>
                <Table.Summary.Row>
                  {/* 有事业部时多一列展开按钮 */}
                  {bu.enabled && <Table.Summary.Cell index={0} />}
                  {columns.map((c, i) => {
                    const key = String(
                      c.key ?? (c as { dataIndex?: string }).dataIndex,
                    );
                    const render = c.render as
                      | ((
                          v: unknown,
                          r: OperatingRow,
                          i: number,
                        ) => React.ReactNode)
                      | undefined;
                    const dataIndex = (c as { dataIndex?: keyof OperatingRow })
                      .dataIndex;
                    return (
                      <Table.Summary.Cell
                        key={key}
                        index={bu.enabled ? i + 1 : i}
                        align={(c as { align?: 'left' | 'right' }).align}
                      >
                        {render
                          ? render(
                              dataIndex ? total[dataIndex] : undefined,
                              total,
                              0,
                            )
                          : null}
                      </Table.Summary.Cell>
                    );
                  })}
                </Table.Summary.Row>
              </Table.Summary>
            ) : null
          }
        />
      </MainCard>
    </div>
  );
};

export default OperatingReportPage;
