import { InboxOutlined } from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { App, DatePicker, Segmented, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { Card } from '@/pages/inquiry/shared/components';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { tableScrollX } from '@/utils/tableScroll';
import {
  type BrandCategoryRow,
  type BrandCategoryStats,
  brandCategoryApi,
  type StatsPeriod,
} from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

const PERIODS: { value: StatsPeriod; label: string; prev: string }[] = [
  { value: 'DAY', label: '日', prev: '较前一天' },
  { value: 'WEEK', label: '周', prev: '较上周' },
  { value: 'MONTH', label: '月', prev: '较上月' },
  { value: 'QUARTER', label: '季度', prev: '较上季度' },
  { value: 'YEAR', label: '年', prev: '较上年' },
];
const PICKER = {
  DAY: 'date',
  WEEK: 'week',
  MONTH: 'month',
  QUARTER: 'quarter',
  YEAR: 'year',
} as const;
const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六'];

const periodText = (period: StatsPeriod, d: Dayjs) => {
  switch (period) {
    case 'DAY':
      return `${d.format('YYYY-MM-DD')}（周${WEEKDAYS[d.day()]}）`;
    case 'WEEK':
      return `${d.startOf('week').format('YYYY-MM-DD')} ~ ${d.endOf('week').format('MM-DD')}`;
    case 'QUARTER':
      return `${d.year()} 年第 ${Math.floor(d.month() / 3) + 1} 季度`;
    case 'YEAR':
      return `${d.year()} 年`;
    default:
      return d.format('YYYY-MM');
  }
};

/** 较上期：差值保留 digits 位，unit 为单位 */
const delta = (
  prefix: string,
  cur: number | null | undefined,
  prev: number | null | undefined,
  digits: number,
  unit = '',
) => {
  if (cur == null || prev == null) return undefined;
  const d = Number((cur - prev).toFixed(digits));
  if (d === 0) return `${prefix}持平`;
  return `${prefix} ${d > 0 ? '+' : '−'}${Math.abs(d).toFixed(digits)}${unit}`;
};

/**
 * 询价统计 → 品牌品类（spec inquiry/sourcing-stats「品牌品类统计」，原型 32-11-25反馈 W1–W3）：
 * 按日 / 周 / 月 / 季度 / 年选一期，看询价型号、有价、无货、待回价、有价率与平均回价时长，按品牌或一级品类列出
 */
const BrandCategoryPanel: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [period, setPeriod] = useState<StatsPeriod>('MONTH');
  const [day, setDay] = useState<Dayjs>(dayjs());
  const [by, setBy] = useState<'brand' | 'category'>('brand');
  const [data, setData] = useState<BrandCategoryStats>();
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(
        await brandCategoryApi.stats({
          period,
          date: day.format('YYYY-MM-DD'),
        }),
      );
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [period, day, message]);

  useEffect(() => {
    load();
  }, [load]);
  useOnReactivate(load);

  const meta = PERIODS.find((p) => p.value === period) ?? PERIODS[2];
  const unit = meta.label === '日' ? '天' : meta.label;
  const t = data?.totals;
  const p = data?.previous;

  const stat = (
    label: string,
    value: string,
    note?: string,
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
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 2 }}>
        {note ?? ' '}
      </div>
    </div>
  );

  const count = (v: number, color?: string) => (
    <span style={{ ...num, color: v ? (color ?? palette.ink) : palette.mute }}>
      {v || '—'}
    </span>
  );
  const rate = (v?: number | null) => (v == null ? '—' : `${v}%`);
  const hours = (v?: number | null) => (v == null ? '—' : `${v} 小时`);

  const columns: TableColumnsType<BrandCategoryRow> = [
    {
      title: by === 'brand' ? '品牌' : '品类',
      dataIndex: 'name',
      width: 200,
      render: (v: string, r) => (
        <b style={{ color: r.key ? palette.ink : palette.sub }}>{v}</b>
      ),
    },
    {
      title: '询价型号',
      dataIndex: 'items',
      width: 100,
      align: 'right',
      defaultSortOrder: 'descend',
      sorter: (a, b) => a.items - b.items,
      render: (v: number) => count(v),
    },
    {
      title: '有价',
      dataIndex: 'priced',
      width: 90,
      align: 'right',
      sorter: (a, b) => a.priced - b.priced,
      render: (v: number) => count(v),
    },
    {
      title: '无货',
      dataIndex: 'noStock',
      width: 90,
      align: 'right',
      sorter: (a, b) => a.noStock - b.noStock,
      render: (v: number) => (
        <b style={{ ...num, color: v ? palette.orange : palette.mute }}>
          {v || '—'}
        </b>
      ),
    },
    {
      title: '待回价',
      dataIndex: 'pending',
      width: 90,
      align: 'right',
      sorter: (a, b) => a.pending - b.pending,
      render: (v: number) => count(v, palette.orange),
    },
    {
      title: '有价率',
      dataIndex: 'pricedRate',
      width: 190,
      sorter: (a, b) => (a.pricedRate ?? -1) - (b.pricedRate ?? -1),
      render: (v?: number | null) => (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <span style={{ ...num, width: 64, textAlign: 'right' }}>
            {rate(v)}
          </span>
          <span
            style={{
              width: 90,
              height: 6,
              borderRadius: 3,
              background: palette.hairline,
              overflow: 'hidden',
            }}
          >
            <span
              style={{
                display: 'block',
                width: `${Math.min(100, v ?? 0)}%`,
                height: '100%',
                background: palette.green,
              }}
            />
          </span>
        </span>
      ),
    },
    {
      title: '平均回价时长',
      dataIndex: 'avgHours',
      width: 130,
      align: 'right',
      sorter: (a, b) => (a.avgHours ?? -1) - (b.avgHours ?? -1),
      render: (v?: number | null) => (
        <span style={{ ...num, color: v == null ? palette.mute : undefined }}>
          {hours(v)}
        </span>
      ),
    },
  ];

  const rows = (by === 'brand' ? data?.brands : data?.categories) ?? [];

  return (
    <div>
      <div
        style={{
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          justifyContent: 'flex-end',
          flexWrap: 'wrap',
          marginBottom: 16,
        }}
      >
        <Segmented
          value={period}
          onChange={(v) => setPeriod(v as StatsPeriod)}
          options={PERIODS.map(({ value, label }) => ({ value, label }))}
        />
        <DatePicker
          picker={PICKER[period]}
          value={day}
          allowClear={false}
          onChange={(d) => d && setDay(d)}
          format={(d) => periodText(period, d)}
          style={{ width: 240 }}
          aria-label="选择统计期间"
        />
      </div>
      <div
        style={{ display: 'flex', gap: 14, flexWrap: 'wrap', marginBottom: 16 }}
      >
        {stat(
          '询价型号',
          String(t?.items ?? 0),
          delta(meta.prev, t?.items, p?.items, 0),
        )}
        {stat(
          '有价',
          String(t?.priced ?? 0),
          `无货 ${t?.noStock ?? 0} · 待回价 ${t?.pending ?? 0}`,
          palette.green,
        )}
        {stat(
          '有价率',
          rate(t?.pricedRate),
          t?.pricedRate == null
            ? `这${unit}没有询价型号`
            : delta(meta.prev, t.pricedRate, p?.pricedRate, 2, ' 个百分点'),
        )}
        {stat(
          '平均回价时长',
          hours(t?.avgHours),
          t?.avgHours == null
            ? `这${unit}没有回价`
            : delta(meta.prev, t.avgHours, p?.avgHours, 1, ' 小时'),
        )}
      </div>
      <Card style={{ padding: 20 }}>
        <div style={{ marginBottom: 12 }}>
          <Segmented
            value={by}
            onChange={(v) => setBy(v as 'brand' | 'category')}
            options={[
              { value: 'brand', label: '品牌' },
              { value: 'category', label: '品类（一级）' },
            ]}
          />
        </div>
        <Table<BrandCategoryRow>
          key={by}
          rowKey="key"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rows}
          pagination={false}
          showSorterTooltip={false}
          scroll={{ x: tableScrollX(columns) }}
          locale={{
            emptyText: (
              <div style={{ padding: '32px 0' }}>
                <InboxOutlined style={{ fontSize: 28, color: palette.mute }} />
                <div style={{ marginTop: 8, color: palette.sub }}>
                  这{unit}没有进入询价的型号
                </div>
                <div style={{ fontSize: 12, color: palette.mute }}>
                  换一{unit === '天' ? '天' : '期'}看看，或切换统计周期
                </div>
              </div>
            ),
          }}
        />
        <div style={{ color: palette.mute, fontSize: 12, marginTop: 8 }}>
          按询盘日期归入当期；有价率 = 有价 ÷ 询价型号；平均回价时长 =
          任务首次分配到这个型号第一条回价，只算已回价的型号。
        </div>
      </Card>
    </div>
  );
};

export default BrandCategoryPanel;
