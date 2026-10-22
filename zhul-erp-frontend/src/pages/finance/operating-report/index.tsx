import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Select, Table } from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
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
  const thisYear = dayjs().year();
  const [year, setYear] = useState(thisYear);
  const [data, setData] = useState<OperatingReport>();
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await operatingApi.year(year));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [year, message]);

  useEffect(() => {
    load();
  }, [load]);

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
    history.push(`${path}?dateFrom=${dateFrom}&dateTo=${dateTo}${extra}`);
  };
  const link = (
    value: number,
    onClick: () => void,
    color?: string,
    bold?: boolean,
  ) =>
    value ? (
      <a
        onClick={onClick}
        style={{
          ...num,
          color: color ?? palette.ink,
          fontWeight: bold ? 700 : 400,
        }}
      >
        {cny(value)}
      </a>
    ) : (
      <span style={{ color: palette.mute }}>—</span>
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
      render: (v: number) => <span style={num}>{cny(v)}</span>,
    },
    {
      title: '销售毛利',
      key: 'profit',
      width: 130,
      align: 'right',
      render: (_, r) =>
        link(r.profit, () => go('/finance/profit-stats', r), palette.green),
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
      render: (_, r) =>
        link(r.refund, () => go('/finance/refunds', r), palette.orange),
    },
    {
      title: '其他收入',
      key: 'other',
      width: 110,
      align: 'right',
      render: (_, r) =>
        link(r.otherIncome, () => go('/finance/other-incomes', r)),
    },
    ...(data?.categories ?? []).map((c) => ({
      title: c.name,
      key: `opex-${c.code}`,
      width: 105,
      align: 'right' as const,
      render: (_: unknown, r: OperatingRow) =>
        link(r.opex[c.code] ?? 0, () =>
          go('/finance/expenses', r, `&category=${c.code}&linked=false`),
        ),
    })),
    {
      title: '运营成本',
      dataIndex: 'opexTotal',
      width: 130,
      align: 'right',
      render: (v: number) => <b style={num}>{cny(v)}</b>,
    },
    {
      title: '预计盈亏',
      dataIndex: 'net',
      width: 140,
      align: 'right',
      render: (v: number) => (
        <b style={{ ...num, color: v >= 0 ? palette.green : palette.red }}>
          {cny(v)}
        </b>
      ),
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

  return (
    <div>
      <PageTitle description="预计盈亏 = 销售毛利 − 退款 + 其他收入 − 运营成本。这里的毛利不扣退款（按销售日期归月），退款按退款月份单列；运营成本为不关联订单的费用单，年费等按分摊后的月份计。" />
      <div
        style={{
          display: 'flex',
          gap: 10,
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <Select
          value={year}
          onChange={setYear}
          style={{ width: 120 }}
          options={[0, 1, 2, 3].map((d) => ({
            value: thisYear - d,
            label: `${thisYear - d} 年`,
          }))}
        />
        <span style={{ fontSize: 12, color: palette.mute }}>
          点数字打开来源：收入与毛利 → 利润统计；退款、其他收入、运营成本 →
          对应列表
        </span>
      </div>
      {total && (
        <div
          style={{
            display: 'flex',
            gap: 14,
            flexWrap: 'wrap',
            marginBottom: 16,
          }}
        >
          {stat('销售收入', cny(total.revenue), `${total.orderCount} 单`)}
          {stat(
            '销售毛利',
            cny(total.profit),
            `毛利率 ${rateText(total.profitRate)}`,
            palette.green,
          )}
          {stat(
            '退款与其他收入',
            total.refund ? `−${cny(total.refund)}` : cny(0),
            `其他收入 ${cny(total.otherIncome)}`,
            palette.orange,
          )}
          {stat('运营成本', cny(total.opexTotal))}
          {stat(
            '预计盈亏',
            cny(total.net),
            `净利率 ${rateText(total.netRate)}`,
            total.net >= 0 ? palette.green : palette.red,
          )}
        </div>
      )}
      <Card>
        <Table<OperatingRow>
          rowKey="month"
          loading={loading}
          columns={columns}
          dataSource={data?.rows ?? []}
          pagination={false}
          scroll={{ x: 1500 + (data?.categories.length ?? 0) * 105 }}
          locale={{ emptyText: '这一年还没有数据' }}
          summary={() =>
            total ? (
              <Table.Summary fixed>
                <Table.Summary.Row>
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
                        index={i}
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
      </Card>
    </div>
  );
};

export default OperatingReportPage;
