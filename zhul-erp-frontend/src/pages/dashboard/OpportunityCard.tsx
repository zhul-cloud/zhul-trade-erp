import {
  BarChartOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExportOutlined,
  TrophyOutlined,
  UserAddOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Alert, Button, Segmented, Skeleton, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { rate } from '@/pages/crm/opportunity/StatsPanel';
import {
  type OpportunityStats,
  opportunityApi,
  readBizError,
  type StatRow,
} from '@/pages/crm/opportunity/service';
import { Card, Stat } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';

export type CardPreset = 'today' | 'week' | 'month';

const PRESETS: { value: CardPreset; label: string }[] = [
  { value: 'today', label: '今天' },
  { value: 'week', label: '最近 7 天' },
  { value: 'month', label: '本月' },
];

/** 与每日渠道统计页的同名区间一致，「查看完整统计」带着区间过去，数字对得上 */
const rangeOf = (p: CardPreset): [Dayjs, Dayjs] => {
  const today = dayjs().startOf('day');
  if (p === 'today') return [today, today];
  if (p === 'month') return [today.startOf('month'), today];
  return [today.subtract(6, 'day'), today];
};

const fetchStats = (range: [Dayjs, Dayjs]) =>
  opportunityApi.stats(
    range[0].format('YYYY-MM-DD'),
    range[1].format('YYYY-MM-DD'),
    'channel',
  );

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

/** 工作台 · 商机统计：按来源渠道看新增、有效、赢单（口径同每日渠道统计） */
const OpportunityCard: React.FC = () => {
  const { palette } = useAppTheme();
  const [preset, setPreset] = useState<CardPreset>('week');
  const [data, setData] = useState<OpportunityStats>();
  const [previous, setPrevious] = useState<StatRow>();
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setError('');
    setData(undefined);
    const range = rangeOf(preset);
    // 上一个同样长度的区间，用来显示新增的变化
    const days = range[1].diff(range[0], 'day') + 1;
    const prev: [Dayjs, Dayjs] = [
      range[0].subtract(days, 'day'),
      range[0].subtract(1, 'day'),
    ];
    try {
      const [cur, before] = await Promise.all([
        fetchStats(range),
        fetchStats(prev),
      ]);
      setData(cur);
      setPrevious(before.summary);
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [preset]);

  useEffect(() => {
    load();
  }, [load]);

  const sum = data?.summary;
  const delta = sum && previous ? sum.total - previous.total : undefined;
  const columns: TableColumnsType<StatRow> = [
    { title: '来源渠道', dataIndex: 'label' },
    {
      title: '新增',
      dataIndex: 'total',
      width: 90,
      align: 'right',
      render: (v: number) => <b style={num}>{v}</b>,
    },
    {
      title: '有效',
      dataIndex: 'valid',
      width: 90,
      align: 'right',
      render: (v: number) => <span style={num}>{v}</span>,
    },
    {
      title: '赢单',
      dataIndex: 'won',
      width: 90,
      align: 'right',
      render: (v: number) => (
        <span style={{ ...num, color: v ? palette.green : palette.mute }}>
          {v}
        </span>
      ),
    },
    {
      title: '有效率',
      key: 'rate',
      width: 100,
      align: 'right',
      render: (_, r) => <b style={num}>{rate(r.valid, r.total)}</b>,
    },
  ];

  return (
    <Card style={{ padding: 20 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          flexWrap: 'wrap',
          marginBottom: 16,
        }}
      >
        <span
          style={{
            width: 32,
            height: 32,
            borderRadius: 10,
            background: palette.accentSoft,
            color: palette.link,
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <BarChartOutlined />
        </span>
        <b style={{ fontSize: 16, color: palette.ink }}>商机统计</b>
        <span style={{ fontSize: 12, color: palette.mute }}>
          按首次接触日期，口径同每日渠道统计
        </span>
        <span style={{ marginLeft: 'auto' }} />
        <Segmented
          value={preset}
          onChange={(v) => setPreset(v as CardPreset)}
          options={PRESETS}
        />
        <Button
          icon={<ExportOutlined />}
          onClick={() =>
            history.push(`/crm/opportunity-stats?preset=${preset}`)
          }
        >
          查看完整统计
        </Button>
      </div>
      {error ? (
        <Alert
          type="error"
          showIcon
          title={`商机统计加载失败：${error}`}
          action={
            <Button size="small" onClick={load}>
              重试
            </Button>
          }
        />
      ) : !data || !sum ? (
        <Skeleton active paragraph={{ rows: 5 }} />
      ) : (
        <>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
              gap: 12,
              marginBottom: 16,
            }}
          >
            <Stat
              icon={<UserAddOutlined />}
              color={palette.link}
              soft={palette.accentSoft}
              label="新增"
              value={sum.total}
              hint={
                delta == null ? (
                  '—'
                ) : (
                  <span
                    style={{
                      color:
                        delta > 0
                          ? palette.green
                          : delta < 0
                            ? palette.orange
                            : palette.mute,
                    }}
                  >
                    较上一周期 {delta > 0 ? `+${delta}` : delta}
                  </span>
                )
              }
            />
            <Stat
              icon={<CheckCircleOutlined />}
              color={palette.green}
              soft={palette.greenSoft}
              label="有效"
              value={sum.valid}
              hint={`有效率 ${rate(sum.valid, sum.total)}`}
            />
            <Stat
              icon={<TrophyOutlined />}
              color={palette.orange}
              soft={palette.orangeSoft}
              label="赢单"
              value={sum.won}
              hint={`输单 ${sum.lost}`}
            />
            <Stat
              icon={<CloseCircleOutlined />}
              color={palette.mute}
              soft={palette.inset}
              label="无效"
              value={sum.invalid}
              hint={
                sum.total
                  ? `占新增 ${((sum.invalid / sum.total) * 100).toFixed(1)}%`
                  : '—'
              }
            />
          </div>
          <Table<StatRow>
            rowKey="label"
            size="middle"
            columns={columns}
            dataSource={data.rows}
            pagination={false}
            locale={{ emptyText: '这段时间还没有登记商机' }}
          />
        </>
      )}
    </Card>
  );
};

export default OpportunityCard;
