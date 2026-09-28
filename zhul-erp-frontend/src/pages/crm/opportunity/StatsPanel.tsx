import { DatePicker, Segmented, Skeleton, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { Card } from './components';
import {
  type OpportunityStats,
  opportunityApi,
  readBizError,
  type StatRow,
} from './service';

type Preset = 'today' | 'yesterday' | 'week' | 'month' | 'custom';
type GroupBy = 'channel' | 'owner' | 'date';

const PRESETS: { value: Preset; label: string }[] = [
  { value: 'today', label: '今天' },
  { value: 'yesterday', label: '昨天' },
  { value: 'week', label: '近 7 天' },
  { value: 'month', label: '本月' },
  { value: 'custom', label: '自定义' },
];

const rangeOf = (p: Preset): [Dayjs, Dayjs] => {
  const today = dayjs().startOf('day');
  switch (p) {
    case 'today':
      return [today, today];
    case 'yesterday':
      return [today.subtract(1, 'day'), today.subtract(1, 'day')];
    case 'month':
      return [today.startOf('month'), today];
    default:
      return [today.subtract(6, 'day'), today];
  }
};

/** 有效率：一位小数；新增为 0 时显示「—」 */
export const rate = (valid: number, total: number) =>
  total ? `${((valid / total) * 100).toFixed(1)}%` : '—';

const StatsPanel: React.FC = () => {
  const { palette } = useAppTheme();
  const [preset, setPreset] = useState<Preset>('week');
  const [range, setRange] = useState<[Dayjs, Dayjs]>(rangeOf('week'));
  const [groupBy, setGroupBy] = useState<GroupBy>('channel');
  const [data, setData] = useState<OpportunityStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      setData(
        await opportunityApi.stats(
          range[0].format('YYYY-MM-DD'),
          range[1].format('YYYY-MM-DD'),
          groupBy,
        ),
      );
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [range, groupBy]);

  const sum = data?.summary;
  const kpis: {
    label: string;
    value: number | undefined;
    color: string;
    hint?: string;
  }[] = [
    { label: '新增商机', value: sum?.total, color: palette.ink },
    {
      label: '无效',
      value: sum?.invalid,
      color: palette.mute,
      hint: sum?.total ? `占 ${rate(sum.invalid, sum.total)}` : undefined,
    },
    {
      label: '有效（进入过 S3）',
      value: sum?.valid,
      color: palette.link,
      hint: sum ? `有效率 ${rate(sum.valid, sum.total)}` : undefined,
    },
    { label: '赢单', value: sum?.won, color: palette.green },
    { label: '输单', value: sum?.lost, color: palette.red },
  ];
  const firstCol =
    groupBy === 'channel'
      ? '来源渠道'
      : groupBy === 'owner'
        ? '业务员'
        : '日期';
  const max = Math.max(1, ...(data?.rows ?? []).map((r) => r.total));

  return (
    <div style={{ display: 'grid', gap: 20 }}>
      <div
        style={{
          display: 'flex',
          gap: 16,
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        <Segmented<Preset>
          value={preset}
          options={PRESETS}
          onChange={(v) => {
            setPreset(v);
            if (v !== 'custom') setRange(rangeOf(v));
          }}
        />
        <DatePicker.RangePicker
          value={range}
          allowClear={false}
          disabledDate={(d) => d.isAfter(dayjs(), 'day')}
          onChange={(v) => {
            if (v?.[0] && v[1]) {
              setPreset('custom');
              setRange([v[0], v[1]]);
            }
          }}
        />
        <span style={{ flex: 1 }} />
        <span style={{ color: palette.mute }}>分组</span>
        <Segmented<GroupBy>
          value={groupBy}
          onChange={setGroupBy}
          options={[
            { value: 'channel', label: '按渠道' },
            { value: 'owner', label: '按业务员' },
            { value: 'date', label: '按日期' },
          ]}
        />
      </div>

      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(5, 1fr)',
              gap: 16,
            }}
          >
            {kpis.map((k) => (
              <Card key={k.label} style={{ padding: 20 }}>
                <div style={{ color: palette.mute }}>{k.label}</div>
                {loading ? (
                  <Skeleton
                    active
                    paragraph={false}
                    style={{ marginTop: 12 }}
                  />
                ) : (
                  <div
                    className="num"
                    style={{
                      fontSize: 30,
                      fontWeight: 700,
                      color: k.color,
                      marginTop: 8,
                    }}
                  >
                    {k.value ?? 0}
                  </div>
                )}
                <div
                  style={{ fontSize: 12, color: palette.mute, minHeight: 18 }}
                >
                  {k.hint}
                </div>
              </Card>
            ))}
          </div>

          {groupBy !== 'date' && (
            <Card
              title={
                groupBy === 'channel' ? '各渠道新增构成' : '各业务员新增构成'
              }
              extra={
                <div
                  style={{
                    display: 'flex',
                    gap: 16,
                    fontSize: 12,
                    color: palette.mute,
                  }}
                >
                  {[
                    ['有效', palette.link],
                    ['跟进中（未到 S3）', palette.accentLine],
                    ['无效', palette.control],
                  ].map(([n, c]) => (
                    <span
                      key={n}
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 6,
                      }}
                    >
                      <span
                        style={{
                          width: 10,
                          height: 10,
                          borderRadius: 3,
                          background: c,
                        }}
                      />
                      {n}
                    </span>
                  ))}
                </div>
              }
            >
              {loading ? (
                <Skeleton active />
              ) : !data?.rows.length ? (
                <div style={{ color: palette.mute, padding: '12px 0' }}>
                  这段时间没有新增商机。
                </div>
              ) : (
                <div style={{ display: 'grid', gap: 12 }}>
                  {data.rows.map((r) => {
                    const doing = Math.max(0, r.total - r.valid - r.invalid);
                    const seg = (n: number, color: string) =>
                      n > 0 && (
                        <span
                          style={{
                            width: `${(n / max) * 100}%`,
                            background: color,
                            height: '100%',
                          }}
                        />
                      );
                    return (
                      <div
                        key={r.key}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 16,
                        }}
                      >
                        <span style={{ width: 120, color: palette.sub }}>
                          {r.label}
                        </span>
                        <div
                          style={{
                            flex: 1,
                            display: 'flex',
                            gap: 2,
                            height: 22,
                            borderRadius: 6,
                            overflow: 'hidden',
                          }}
                          title={`有效 ${r.valid}，跟进中 ${doing}，无效 ${r.invalid}`}
                        >
                          {seg(r.valid, palette.link)}
                          {seg(doing, palette.accentLine)}
                          {seg(r.invalid, palette.control)}
                        </div>
                        <span
                          className="num"
                          style={{
                            width: 40,
                            textAlign: 'right',
                            fontWeight: 700,
                            color: palette.ink,
                          }}
                        >
                          {r.total}
                        </span>
                      </div>
                    );
                  })}
                </div>
              )}
            </Card>
          )}

          <Table<StatRow>
            rowKey="key"
            loading={loading}
            pagination={false}
            dataSource={data?.rows ?? []}
            locale={{ emptyText: '这段时间没有新增商机' }}
            columns={[
              { title: firstCol, dataIndex: 'label' },
              { title: '新增', dataIndex: 'total', align: 'right' },
              { title: '无效', dataIndex: 'invalid', align: 'right' },
              { title: '有效', dataIndex: 'valid', align: 'right' },
              {
                title: '有效率',
                key: 'rate',
                align: 'right',
                render: (_, r) => (
                  <span style={{ color: palette.link }}>
                    {rate(r.valid, r.total)}
                  </span>
                ),
              },
              { title: '赢单', dataIndex: 'won', align: 'right' },
              { title: '输单', dataIndex: 'lost', align: 'right' },
            ]}
            summary={() =>
              sum && data?.rows.length ? (
                <Table.Summary.Row style={{ fontWeight: 600 }}>
                  <Table.Summary.Cell index={0}>合计</Table.Summary.Cell>
                  <Table.Summary.Cell index={1} align="right">
                    {sum.total}
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={2} align="right">
                    {sum.invalid}
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={3} align="right">
                    {sum.valid}
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={4} align="right">
                    <span style={{ color: palette.link }}>
                      {rate(sum.valid, sum.total)}
                    </span>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={5} align="right">
                    {sum.won}
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={6} align="right">
                    {sum.lost}
                  </Table.Summary.Cell>
                </Table.Summary.Row>
              ) : null
            }
          />
          <div style={{ fontSize: 12, color: palette.mute }}>
            统计按首次接触日期归属；有效 = 曾进入 S3
            及以后（之后输单仍计入有效）；有效率 = 有效 ÷
            新增。数据范围随你的数据权限。
          </div>
        </>
      )}
    </div>
  );
};

export default StatsPanel;
