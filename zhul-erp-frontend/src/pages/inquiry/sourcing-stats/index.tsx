import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Segmented, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import StatsRangeFilter, { presetRange } from '@/components/StatsRangeFilter';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { tableScrollX } from '@/utils/tableScroll';
import {
  type MonthRow,
  type PurchaserRow,
  type SourcingStats,
  sourcingStatsApi,
  type WorkloadRow,
} from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const rateText = (v?: number | null) => (v == null ? '—' : `${v}%`);

/** 采购管理 → 询价统计：按月份（询盘单、询价工作量）与按人员（型号）的询价情况（只读） */
const SourcingStatsPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() =>
    presetRange('month'),
  );
  const [data, setData] = useState<SourcingStats>();
  const [loading, setLoading] = useState(true);
  const [view, setView] = useState<'month' | 'person'>('month');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(
        await sourcingStatsApi.stats({
          dateFrom: range[0].format('YYYY-MM-DD'),
          dateTo: range[1].format('YYYY-MM-DD'),
        }),
      );
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [range, message]);

  useEffect(() => {
    load();
  }, [load]);

  /** 打开该月（合计为整个范围）的询盘单列表 */
  const open = (r: MonthRow) => {
    let [from, to] = range;
    if (r.key) {
      const start = dayjs(`${r.key}-01`);
      from = start.isBefore(range[0]) ? range[0] : start;
      to = start.endOf('month').isAfter(range[1])
        ? range[1]
        : start.endOf('month');
    }
    history.push(
      `/inquiry/customer-inquiries?dateFrom=${from.format('YYYY-MM-DD')}&dateTo=${to.format('YYYY-MM-DD')}`,
    );
  };
  const n = (v: number, color?: string) => (
    <span style={{ ...num, color: v ? color : palette.mute }}>{v || '—'}</span>
  );

  const monthColumns: TableColumnsType<MonthRow> = [
    {
      title: '月份',
      dataIndex: 'label',
      width: 110,
      render: (v: string) => <b style={{ color: palette.ink }}>{v}</b>,
    },
    {
      title: '询价总数',
      key: 'total',
      width: 90,
      align: 'right',
      render: (_, r) =>
        r.total ? (
          <button
            type="button"
            className="zhul-link"
            style={num}
            onClick={() => open(r)}
          >
            {r.total}
          </button>
        ) : (
          n(0)
        ),
    },
    {
      title: '询价完成',
      dataIndex: 'completed',
      width: 90,
      align: 'right',
      render: (v: number) => n(v),
    },
    {
      title: '无法报价',
      dataIndex: 'unquotable',
      width: 90,
      align: 'right',
      render: (v: number) => n(v),
    },
    {
      title: '询价中',
      dataIndex: 'inProgress',
      width: 80,
      align: 'right',
      render: (v: number) => n(v, palette.orange),
    },
    {
      title: '完成率',
      dataIndex: 'completedRate',
      width: 90,
      align: 'right',
      render: (v: number | null) => (
        <span style={{ ...num, color: palette.green }}>{rateText(v)}</span>
      ),
    },
    {
      title: '无法报价率',
      dataIndex: 'unquotableRate',
      width: 100,
      align: 'right',
      render: (v: number | null) => <span style={num}>{rateText(v)}</span>,
    },
  ];

  const purchaserColumns: TableColumnsType<PurchaserRow> = [
    {
      title: '人员',
      dataIndex: 'purchaserName',
      width: 170,
      render: (v: string, r) => (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <b style={{ color: palette.ink }}>{v}</b>
          {r.role === 'SALES' ? (
            <Pill tone="violet">业务员</Pill>
          ) : r.role === 'PART_TIME' ? (
            <Pill tone="cyan">兼职</Pill>
          ) : (
            <Pill tone="accent">采购</Pill>
          )}
        </span>
      ),
    },
    {
      title: '型号数',
      dataIndex: 'items',
      width: 80,
      align: 'right',
      render: (v: number) => n(v),
    },
    {
      title: '有价',
      dataIndex: 'priced',
      width: 70,
      align: 'right',
      render: (v: number) => n(v),
    },
    {
      title: '无货',
      dataIndex: 'noStock',
      width: 70,
      align: 'right',
      render: (v: number) => n(v),
    },
    {
      title: '待回价',
      dataIndex: 'pending',
      width: 80,
      align: 'right',
      render: (v: number) => n(v, palette.orange),
    },
    {
      title: '转出',
      dataIndex: 'transferred',
      width: 70,
      align: 'right',
      render: (v: number) => n(v),
    },
    {
      title: '有价率',
      dataIndex: 'pricedRate',
      width: 80,
      align: 'right',
      render: (v: number | null) => (
        <span style={{ ...num, color: palette.green }}>{rateText(v)}</span>
      ),
    },
  ];

  const workloadColumns: TableColumnsType<WorkloadRow> = [
    {
      title: '月份',
      dataIndex: 'label',
      width: 110,
      render: (v: string) => <b style={{ color: palette.ink }}>{v}</b>,
    },
    {
      title: '业务员自询',
      dataIndex: 'selfItems',
      width: 110,
      align: 'right',
      render: (v: number) => n(v),
    },
    {
      title: '采购询价',
      dataIndex: 'purchaseItems',
      width: 110,
      align: 'right',
      render: (v: number) => n(v),
    },
    {
      title: '自询占比',
      dataIndex: 'selfRate',
      width: 100,
      align: 'right',
      render: (v: number | null) => <span style={num}>{rateText(v)}</span>,
    },
  ];

  const total = data?.total;
  const stat = (
    label: string,
    value: string,
    color?: string,
    note?: string,
  ) => (
    <div
      style={{
        flex: '1 1 180px',
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
      {note && (
        <div style={{ fontSize: 12, color: palette.mute, marginTop: 2 }}>
          {note}
        </div>
      )}
    </div>
  );

  return (
    <div>
      <PageTitle description="按月份按询盘单统计：没有待回价型号且至少一个有价为询价完成，全部无货为无法报价，还有待回价的为询价中。按人员按分配给他的型号统计。" />
      <div style={{ marginBottom: 16 }}>
        <StatsRangeFilter value={range} onChange={setRange} />
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
          {stat('询价总数', String(total.total), undefined, '询盘单')}
          {stat('询价完成率', rateText(total.completedRate), palette.green)}
          {stat('无法报价率', rateText(total.unquotableRate), palette.orange)}
          {stat(
            '询价中',
            String(total.inProgress),
            undefined,
            '还有待回价的型号',
          )}
        </div>
      )}
      <Segmented
        style={{ marginBottom: 16 }}
        value={view}
        onChange={setView}
        options={[
          { value: 'month', label: '按月份' },
          { value: 'person', label: '按人员' },
        ]}
      />
      {view === 'month' && (
        <Card style={{ marginBottom: 16 }}>
          <div
            style={{ fontWeight: 700, color: palette.ink, marginBottom: 12 }}
          >
            询价工作量（型号）
          </div>
          <Table<WorkloadRow>
            rowKey="key"
            size="small"
            loading={loading}
            columns={workloadColumns}
            dataSource={data?.workload ?? []}
            pagination={false}
            locale={{ emptyText: '期间内没有分配或认领的型号' }}
            summary={() => {
              const w = data?.workloadTotal;
              return w && w.selfItems + w.purchaseItems > 0 ? (
                <Table.Summary.Row>
                  <Table.Summary.Cell index={0}>
                    <b>合计</b>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={1} align="right">
                    {n(w.selfItems)}
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={2} align="right">
                    {n(w.purchaseItems)}
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={3} align="right">
                    {rateText(w.selfRate)}
                  </Table.Summary.Cell>
                </Table.Summary.Row>
              ) : null;
            }}
          />
          <div style={{ color: palette.mute, fontSize: 12, marginTop: 8 }}>
            月份按询盘日期归属。型号转交后，业务员和采购各算一次，所以合计可能大于实际型号数。
          </div>
        </Card>
      )}
      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        {view === 'month' && (
          <Card style={{ flex: '1 1 560px', minWidth: 0 }}>
            <div
              style={{ fontWeight: 700, color: palette.ink, marginBottom: 12 }}
            >
              按月份（询盘单）
            </div>
            <Table<MonthRow>
              rowKey="key"
              size="small"
              loading={loading}
              columns={monthColumns}
              dataSource={data?.months ?? []}
              pagination={false}
              scroll={{ x: tableScrollX(monthColumns) }}
              locale={{ emptyText: '期间内没有进入询价的询盘单' }}
              summary={() =>
                total && total.total > 0 ? (
                  <Table.Summary.Row>
                    <Table.Summary.Cell index={0}>
                      <b>合计</b>
                    </Table.Summary.Cell>
                    <Table.Summary.Cell index={1} align="right">
                      <button
                        type="button"
                        className="zhul-link"
                        style={num}
                        onClick={() => open(total)}
                      >
                        {total.total}
                      </button>
                    </Table.Summary.Cell>
                    <Table.Summary.Cell index={2} align="right">
                      {n(total.completed)}
                    </Table.Summary.Cell>
                    <Table.Summary.Cell index={3} align="right">
                      {n(total.unquotable)}
                    </Table.Summary.Cell>
                    <Table.Summary.Cell index={4} align="right">
                      {n(total.inProgress, palette.orange)}
                    </Table.Summary.Cell>
                    <Table.Summary.Cell index={5} align="right">
                      <span style={{ ...num, color: palette.green }}>
                        {rateText(total.completedRate)}
                      </span>
                    </Table.Summary.Cell>
                    <Table.Summary.Cell index={6} align="right">
                      {rateText(total.unquotableRate)}
                    </Table.Summary.Cell>
                  </Table.Summary.Row>
                ) : null
              }
            />
          </Card>
        )}
        {view === 'person' && (
          <Card style={{ flex: '1 1 480px', minWidth: 0 }}>
            <div
              style={{ fontWeight: 700, color: palette.ink, marginBottom: 12 }}
            >
              按人员（型号）
            </div>
            <Table<PurchaserRow>
              rowKey="purchaserId"
              size="small"
              loading={loading}
              columns={purchaserColumns}
              dataSource={data?.purchasers ?? []}
              pagination={false}
              scroll={{ x: tableScrollX(purchaserColumns) }}
              locale={{ emptyText: '期间内没有分配出去的型号' }}
            />
            <div style={{ color: palette.mute, fontSize: 12, marginTop: 8 }}>
              选一个月就是这个月每人的工作量。转出是这个人交给别人继续找的型号，不计入他的有价、无货、待回价；有价率
              = 有价 ÷（型号数 − 转出）。
            </div>
          </Card>
        )}
      </div>
    </div>
  );
};

export default SourcingStatsPage;
