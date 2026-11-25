import type { TableColumnsType } from 'antd';
import { App, DatePicker, Segmented, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  SummaryBar,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { DeltaText } from '@/theme/delta';
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

const rate = (v?: number | null) => (v == null ? '—' : `${v}%`);
const hours = (v?: number | null) => (v == null ? '—' : `${v} 小时`);

/** 较上期的差值（保留 digits 位）；持平或缺数据时为空 */
const diff = (
  cur: number | null | undefined,
  prev: number | null | undefined,
  digits: number,
) =>
  cur == null || prev == null ? null : Number((cur - prev).toFixed(digits));

/** 型号数、有价率：按方向着色并配上下箭头（ui-design-patterns「涨跌颜色」） */
const delta = (prefix: string, d: number | null, digits: number, unit = '') => {
  if (d == null) return undefined;
  if (d === 0) return `${prefix}持平`;
  return (
    <DeltaText value={d}>
      {prefix} {d > 0 ? '+' : '−'}
      {Math.abs(d).toFixed(digits)}
      {unit}
    </DeltaText>
  );
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
  // 回价时长变长是变差，不用涨跌色，只写文字
  const hoursDiff = diff(t?.avgHours, p?.avgHours, 1);

  const count = (v: number) => (
    <span style={{ ...num, color: v ? undefined : palette.mute }}>
      {v || '—'}
    </span>
  );

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
      render: count,
    },
    {
      title: '有价',
      dataIndex: 'priced',
      width: 90,
      align: 'right',
      sorter: (a, b) => a.priced - b.priced,
      render: count,
    },
    {
      title: '无货',
      dataIndex: 'noStock',
      width: 90,
      align: 'right',
      sorter: (a, b) => a.noStock - b.noStock,
      render: count,
    },
    {
      title: '待回价',
      dataIndex: 'pending',
      width: 90,
      align: 'right',
      sorter: (a, b) => a.pending - b.pending,
      render: count,
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
                background: palette.chart,
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
      {/* 统计条件选了就生效 */}
      <FilterCard>
        <FilterField label="周期" span={2}>
          <Segmented
            value={period}
            onChange={(v) => setPeriod(v as StatsPeriod)}
            options={PERIODS.map(({ value, label }) => ({ value, label }))}
          />
        </FilterField>
        <FilterField label="期间" span={2}>
          <DatePicker
            picker={PICKER[period]}
            value={day}
            allowClear={false}
            onChange={(d) => d && setDay(d)}
            format={(d) => periodText(period, d)}
            style={{ width: 240 }}
            aria-label="选择统计期间"
          />
        </FilterField>
      </FilterCard>
      <SummaryBar
        items={[
          {
            key: 'items',
            label: '询价型号',
            value: t?.items ?? 0,
            sub: delta(meta.prev, diff(t?.items, p?.items, 0), 0),
          },
          {
            key: 'priced',
            label: '有价',
            value: t?.priced ?? 0,
            sub: `无货 ${t?.noStock ?? 0} · 待回价 ${t?.pending ?? 0}`,
          },
          {
            key: 'rate',
            label: '有价率',
            help: '有价 ÷ 询价型号；型号按询盘日期归入当期',
            value: rate(t?.pricedRate),
            sub:
              t?.pricedRate == null
                ? `这${unit}没有询价型号`
                : delta(
                    meta.prev,
                    diff(t.pricedRate, p?.pricedRate, 2),
                    2,
                    ' 个百分点',
                  ),
          },
          {
            key: 'hours',
            label: '平均回价时长',
            help: '任务首次分配到这个型号第一条回价，只算已回价的型号',
            value: hours(t?.avgHours),
            sub:
              t?.avgHours == null
                ? `这${unit}没有回价`
                : hoursDiff == null
                  ? undefined
                  : hoursDiff === 0
                    ? `${meta.prev}持平`
                    : `${meta.prev} ${hoursDiff > 0 ? '+' : '−'}${Math.abs(hoursDiff).toFixed(1)} 小时`,
          },
        ]}
      />
      <MainCard
        left={
          <Segmented
            value={by}
            onChange={(v) => setBy(v as 'brand' | 'category')}
            options={[
              { value: 'brand', label: '品牌' },
              { value: 'category', label: '品类（一级）' },
            ]}
          />
        }
      >
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
            emptyText: tableEmpty(`这${unit}没有进入询价的型号`, loading),
          }}
        />
      </MainCard>
    </div>
  );
};

export default BrandCategoryPanel;
