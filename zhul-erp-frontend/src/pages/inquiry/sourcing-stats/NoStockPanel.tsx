import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, DatePicker, Segmented, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { Card } from '@/pages/inquiry/shared/components';
import { PATHS } from '@/pages/inquiry/shared/constants';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { tableScrollX } from '@/utils/tableScroll';
import { type NoStockGroup, type NoStockStats, noStockApi } from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const rateText = (v?: number | null) => (v == null ? '—' : `${v}%`);

/** 较上期：数量差或百分点差 */
const delta = (cur?: number | null, prev?: number | null, points = false) => {
  if (cur == null || prev == null) return undefined;
  const d = Math.round((cur - prev) * 10) / 10;
  const unit = points ? ' 个百分点' : '';
  return d === 0
    ? '与上期持平'
    : `较上期 ${d > 0 ? '+' : '−'}${Math.abs(d)}${unit}`;
};

/** 询价统计 → 无货分析：按周 / 按月看询价过的型号里无货的品牌、品类与走势（原型 V3） */
const NoStockPanel: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [period, setPeriod] = useState<'WEEK' | 'MONTH'>('WEEK');
  const [day, setDay] = useState<Dayjs>(dayjs());
  const [data, setData] = useState<NoStockStats>();
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(
        await noStockApi.stats({ period, date: day.format('YYYY-MM-DD') }),
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

  /** 点无货数：跳到询价记录，带上回价=无货、品牌或品类、这一期的日期 */
  const open = (g: NoStockGroup, by: 'brand' | 'category') => {
    if (!data) return;
    const q = new URLSearchParams({
      noStock: '1',
      dateFrom: data.dateFrom,
      dateTo: data.dateTo,
    });
    if (by === 'brand') q.set('brand', g.name);
    else if (g.categoryId) {
      q.set('categoryId', String(g.categoryId));
      q.set('categoryName', g.name);
    }
    history.push(`${PATHS.history}?${q.toString()}`);
  };

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

  const rateCell = (v?: number | null) => (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
      <span style={{ ...num, width: 48 }}>{rateText(v)}</span>
      <span
        style={{
          width: 80,
          height: 6,
          borderRadius: 3,
          background: palette.hairline,
          overflow: 'hidden',
        }}
      >
        <span
          style={{
            display: 'block',
            width: `${Math.min(100, ((v ?? 0) / 30) * 100)}%`,
            height: '100%',
            background: palette.orange,
          }}
        />
      </span>
    </span>
  );

  const columns = (
    by: 'brand' | 'category',
  ): TableColumnsType<NoStockGroup> => [
    {
      title: by === 'brand' ? '品牌' : '品类',
      dataIndex: 'name',
      width: 160,
      render: (v: string) => <b style={{ color: palette.ink }}>{v}</b>,
    },
    {
      title: '询价型号',
      dataIndex: 'models',
      width: 90,
      align: 'right',
      render: (v: number) => <span style={num}>{v}</span>,
    },
    {
      title: '无货',
      dataIndex: 'noStock',
      width: 70,
      align: 'right',
      render: (v: number, g) =>
        by === 'category' && !g.categoryId ? (
          <b style={{ ...num, color: palette.orange }}>{v}</b>
        ) : (
          <button
            type="button"
            className="zhul-link"
            style={{ ...num, fontWeight: 700, color: palette.orange }}
            onClick={() => open(g, by)}
          >
            {v}
          </button>
        ),
    },
    {
      title: '无货率',
      dataIndex: 'rate',
      width: 150,
      render: rateCell,
    },
    ...(by === 'brand'
      ? [
          {
            title: '主要原因',
            key: 'reasons',
            width: 200,
            render: (_: unknown, g: NoStockGroup) => (
              <span style={{ color: palette.sub, fontSize: 12 }}>
                {g.reasons
                  .slice(0, 2)
                  .map((r) => `${r.name} ${r.count}`)
                  .join(' · ')}
              </span>
            ),
          },
        ]
      : []),
  ];

  const maxRate = Math.max(1, ...(data?.trend ?? []).map((x) => x.rate ?? 0));
  const unit = period === 'WEEK' ? '周' : '月';

  const rankCard = (
    title: string,
    by: 'brand' | 'category',
    rows: NoStockGroup[],
  ) => {
    const cols = columns(by);
    return (
      <Card style={{ flex: '1 1 480px', minWidth: 0, padding: 20 }}>
        <div style={{ fontWeight: 700, color: palette.ink, marginBottom: 12 }}>
          {title}
        </div>
        <Table<NoStockGroup>
          rowKey="key"
          size="small"
          loading={loading}
          columns={cols}
          dataSource={rows}
          pagination={false}
          scroll={{ x: tableScrollX(cols), y: 400 }}
          locale={{ emptyText: `这${unit}没有无货的型号` }}
        />
      </Card>
    );
  };

  return (
    <div>
      <div
        style={{
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          justifyContent: 'flex-end',
          marginBottom: 16,
        }}
      >
        <Segmented
          value={period}
          onChange={(v) => setPeriod(v as 'WEEK' | 'MONTH')}
          options={[
            { value: 'WEEK', label: '按周' },
            { value: 'MONTH', label: '按月' },
          ]}
        />
        <DatePicker
          picker={period === 'WEEK' ? 'week' : 'month'}
          value={day}
          allowClear={false}
          onChange={(d) => d && setDay(d)}
          format={(d) =>
            period === 'WEEK'
              ? `${d.startOf('week').format('YYYY-MM-DD')} ~ ${d.endOf('week').format('MM-DD')}`
              : d.format('YYYY-MM')
          }
          style={{ width: 240 }}
          aria-label={period === 'WEEK' ? '选择周' : '选择月份'}
        />
      </div>
      <div
        style={{ display: 'flex', gap: 14, flexWrap: 'wrap', marginBottom: 16 }}
      >
        {stat('询价型号', String(t?.models ?? 0), delta(t?.models, p?.models))}
        {stat(
          '无货',
          String(t?.noStock ?? 0),
          delta(t?.noStock, p?.noStock),
          palette.orange,
        )}
        {stat('无货率', rateText(t?.rate), delta(t?.rate, p?.rate, true))}
        {stat(
          '涉及品牌',
          String(t?.brandCount ?? 0),
          t?.topBrand
            ? `无货最多：${t.topBrand} ${t.topBrandNoStock}`
            : undefined,
        )}
      </div>
      <div
        style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 16 }}
      >
        {rankCard('品牌', 'brand', data?.brands ?? [])}
        {rankCard('品类（一级）', 'category', data?.categories ?? [])}
      </div>
      <Card style={{ padding: 20 }}>
        <div style={{ fontWeight: 700, color: palette.ink, marginBottom: 16 }}>
          无货率走势
        </div>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-end',
            height: 160,
            padding: '0 12px',
          }}
        >
          {(data?.trend ?? []).map((x, i, all) => {
            const last = i === all.length - 1;
            return (
              <div
                key={x.dateFrom}
                title={`${x.dateFrom} ~ ${x.dateTo}：询价 ${x.models} 个型号，无货 ${x.noStock}`}
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  gap: 6,
                }}
              >
                <span
                  style={{
                    ...num,
                    fontSize: 12,
                    fontWeight: 600,
                    color: last ? palette.orange : palette.sub,
                  }}
                >
                  {rateText(x.rate)}
                </span>
                <span
                  style={{
                    width: 32,
                    height: Math.max(4, ((x.rate ?? 0) / maxRate) * 100),
                    borderRadius: 6,
                    background: last ? palette.orange : palette.hairline,
                  }}
                />
                <span style={{ ...num, fontSize: 11, color: palette.mute }}>
                  {x.label}
                </span>
              </div>
            );
          })}
        </div>
      </Card>
    </div>
  );
};

export default NoStockPanel;
