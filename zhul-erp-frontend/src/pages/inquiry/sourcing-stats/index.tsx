import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, DatePicker, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle } from '@/pages/inquiry/shared/components';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import {
  type MonthRow,
  type PurchaserRow,
  type SourcingStats,
  sourcingStatsApi,
} from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const rateText = (v?: number | null) => (v == null ? '—' : `${v}%`);

/** 采购管理 → 询价统计：按月份（询盘单）与按采购员（型号）的询价完成情况（只读） */
const SourcingStatsPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [range, setRange] = useState<[Dayjs, Dayjs]>([
    dayjs().startOf('year'),
    dayjs(),
  ]);
  const [data, setData] = useState<SourcingStats>();
  const [loading, setLoading] = useState(true);

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
          <a style={num} onClick={() => open(r)}>
            {r.total}
          </a>
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
      title: '采购员',
      dataIndex: 'purchaserName',
      width: 120,
      render: (v: string) => <b style={{ color: palette.ink }}>{v}</b>,
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
      title: '有价率',
      dataIndex: 'pricedRate',
      width: 80,
      align: 'right',
      render: (v: number | null) => (
        <span style={{ ...num, color: palette.green }}>{rateText(v)}</span>
      ),
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
      <PageTitle description="按月份按询盘单统计：没有待回价型号且至少一个有价为询价完成，全部无货为无法报价，还有待回价的为询价中。按采购员按分配给他的型号统计。" />
      <div style={{ marginBottom: 16 }}>
        <DatePicker.RangePicker
          value={range}
          allowClear={false}
          onChange={(v) => v?.[0] && v[1] && setRange([v[0], v[1]])}
        />
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
      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
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
            scroll={{ x: 650 }}
            locale={{ emptyText: '期间内没有进入询价的询盘单' }}
            summary={() =>
              total && total.total > 0 ? (
                <Table.Summary.Row>
                  <Table.Summary.Cell index={0}>
                    <b>合计</b>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={1} align="right">
                    <a style={num} onClick={() => open(total)}>
                      {total.total}
                    </a>
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
        <Card style={{ flex: '1 1 480px', minWidth: 0 }}>
          <div
            style={{ fontWeight: 700, color: palette.ink, marginBottom: 12 }}
          >
            按采购员（型号）
          </div>
          <Table<PurchaserRow>
            rowKey="purchaserId"
            size="small"
            loading={loading}
            columns={purchaserColumns}
            dataSource={data?.purchasers ?? []}
            pagination={false}
            scroll={{ x: 500 }}
            locale={{ emptyText: '期间内没有分配出去的型号' }}
          />
        </Card>
      </div>
    </div>
  );
};

export default SourcingStatsPage;
