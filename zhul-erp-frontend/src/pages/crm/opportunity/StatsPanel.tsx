import { useSearchParams } from '@umijs/max';
import { Segmented, Table } from 'antd';
import type { Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import { tableEmpty } from '@/components/EmptyState';
import {
  CardTitle,
  FilterCard,
  FilterField,
  HelpTip,
  MainCard,
  SummaryBar,
} from '@/components/ListLayout';
import StatsRangeFilter, {
  presetRange,
  type RangePreset,
} from '@/components/StatsRangeFilter';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import {
  type OpportunityStats,
  opportunityApi,
  readBizError,
  type StatRow,
} from './service';

type GroupBy = 'channel' | 'owner' | 'date';

const PRESETS: RangePreset[] = [
  'today',
  'yesterday',
  'week',
  'month',
  'lastMonth',
  'halfYear',
];

/** 帮助文字：help-texts.md「商机统计」 */
const HELP = {
  valid: '曾进入 S3 及以后的商机，之后输单仍计入有效；有效率 = 有效 ÷ 新增',
  table: '按首次接触日期归属；数据范围随你的数据权限',
};

/** 有效率：一位小数；新增为 0 时显示「—」 */
export const rate = (valid: number, total: number) =>
  total ? `${((valid / total) * 100).toFixed(1)}%` : '—';

/**
 * 商机统计（统计页规则，ui-redesign P1）：统计期间放搜索条件区，五个数字放汇总行（不用大卡、不按好坏上色），
 * 口径放帮助图标；没有数据时只在表格里显示一个空状态，构成图不再单独写一句
 */
const StatsPanel: React.FC = () => {
  const { palette } = useAppTheme();
  // 从工作台商机统计卡片点「查看完整统计」时带 ?preset=，沿用卡片上选的区间
  const [search] = useSearchParams();
  const initial = PRESETS.find((p) => p === search.get('preset')) ?? 'month';
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() =>
    presetRange(initial),
  );
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
  const rows = data?.rows ?? [];
  const firstCol =
    groupBy === 'channel'
      ? '来源渠道'
      : groupBy === 'owner'
        ? '业务员'
        : '日期';
  const max = Math.max(1, ...rows.map((r) => r.total));
  const legend: [string, string][] = [
    ['有效', palette.chart],
    ['跟进中（未到 S3）', palette.primaryLine],
    ['无效', palette.control],
  ];

  return (
    <div>
      {/* 统计期间选了就生效 */}
      <FilterCard>
        <FilterField label="统计期间" span="auto">
          <StatsRangeFilter
            value={range}
            onChange={setRange}
            presets={PRESETS}
          />
        </FilterField>
      </FilterCard>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <>
          {sum && (
            <SummaryBar
              items={[
                { key: 'total', label: '新增商机', value: sum.total },
                {
                  key: 'invalid',
                  label: '无效',
                  value: sum.invalid,
                  sub: sum.total
                    ? `占 ${rate(sum.invalid, sum.total)}`
                    : undefined,
                },
                {
                  key: 'valid',
                  label: '有效',
                  help: HELP.valid,
                  value: sum.valid,
                  sub: `有效率 ${rate(sum.valid, sum.total)}`,
                },
                { key: 'won', label: '赢单', value: sum.won },
                { key: 'lost', label: '输单', value: sum.lost },
              ]}
            />
          )}
          {groupBy !== 'date' && rows.length > 0 && (
            // 两张主卡之间留卡片间距 16
            <div style={{ marginBottom: 16 }}>
              <MainCard
                left={
                  <CardTitle>
                    {groupBy === 'channel'
                      ? '各渠道新增构成'
                      : '各业务员新增构成'}
                  </CardTitle>
                }
                right={
                  <div
                    style={{
                      display: 'flex',
                      gap: 16,
                      fontSize: 12,
                      color: palette.mute,
                    }}
                  >
                    {legend.map(([n, c]) => (
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
                <div style={{ display: 'grid', gap: 12 }}>
                  {rows.map((r) => {
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
                          {seg(r.valid, palette.chart)}
                          {seg(doing, palette.primaryLine)}
                          {seg(r.invalid, palette.control)}
                        </div>
                        <span
                          className="num"
                          style={{
                            width: 40,
                            textAlign: 'right',
                            fontWeight: 600,
                            color: palette.ink,
                          }}
                        >
                          {r.total}
                        </span>
                      </div>
                    );
                  })}
                </div>
              </MainCard>
            </div>
          )}
          <MainCard
            left={
              <>
                <span>分组</span>
                <HelpTip title={HELP.table} />
              </>
            }
            right={
              <Segmented<GroupBy>
                value={groupBy}
                onChange={setGroupBy}
                options={[
                  { value: 'channel', label: '按渠道' },
                  { value: 'owner', label: '按业务员' },
                  { value: 'date', label: '按日期' },
                ]}
              />
            }
          >
            <Table<StatRow>
              rowKey="key"
              loading={loading}
              pagination={false}
              dataSource={rows}
              locale={{
                emptyText: tableEmpty('这段时间没有新增商机', loading),
              }}
              columns={[
                { title: firstCol, dataIndex: 'label' },
                { title: '新增', dataIndex: 'total', align: 'right' },
                { title: '无效', dataIndex: 'invalid', align: 'right' },
                { title: '有效', dataIndex: 'valid', align: 'right' },
                {
                  title: '有效率',
                  key: 'rate',
                  align: 'right',
                  render: (_, r) => rate(r.valid, r.total),
                },
                { title: '赢单', dataIndex: 'won', align: 'right' },
                { title: '输单', dataIndex: 'lost', align: 'right' },
              ]}
              summary={() =>
                sum && rows.length ? (
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
                      {rate(sum.valid, sum.total)}
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
          </MainCard>
        </>
      )}
    </div>
  );
};

export default StatsPanel;
