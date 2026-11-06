import { DatePicker, Select, Table, type TableColumnsType } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { Card, Pill } from '../shared/components';
import {
  type BoardHistoryRow,
  boardApi,
  readBizError,
} from '../shared/service';

const STATES = [
  { value: 1, label: '待分配' },
  { value: 2, label: '询价中' },
  { value: 3, label: '已回价' },
  { value: 4, label: '已报价' },
  { value: 5, label: '已关闭' },
];
const STATE_TONE: Record<
  number,
  'orange' | 'accent' | 'gray' | 'green' | 'mute'
> = {
  1: 'orange',
  2: 'accent',
  3: 'gray',
  4: 'green',
  5: 'mute',
};

const defaultRange = (): [Dayjs, Dayjs] => [
  dayjs().subtract(29, 'day'),
  dayjs(),
];

/**
 * 询价分配「全部」页签：全部询价任务（含已报价、已关闭），按更新时间倒序分页。
 * 关键词与采购员来自页签上方的搜索栏（spec inquiry/sourcing-task「全部询价任务」）。
 */
const AllTasksTable: React.FC<{
  keyword?: string;
  purchaserId?: number;
  onOpen: (taskId: number) => void;
}> = ({ keyword, purchaserId, onOpen }) => {
  const { palette } = useAppTheme();
  const [state, setState] = useState<number>();
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(defaultRange);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20, 'all');
  const [rows, setRows] = useState<BoardHistoryRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // 搜索条件变化时回到第一页
  useEffect(() => setPage(1), [keyword, purchaserId, state, range]);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await boardApi.history({
        keyword: keyword || undefined,
        purchaserId,
        state,
        dateFrom: range?.[0]?.format('YYYY-MM-DD'),
        dateTo: range?.[1]?.format('YYYY-MM-DD'),
        page,
        pageSize,
      });
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [keyword, purchaserId, state, range, page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  const columns: TableColumnsType<BoardHistoryRow> = [
    {
      title: '任务编号',
      dataIndex: 'taskCode',
      width: 150,
      render: (v: string, r) => (
        <span style={{ fontWeight: 600 }}>
          {v}
          {r.urgent && (
            <span style={{ marginLeft: 6 }}>
              <Pill tone="red">紧急</Pill>
            </span>
          )}
        </span>
      ),
    },
    { title: '询盘单号', dataIndex: 'inquiryCode', width: 150 },
    {
      title: '品牌 · 型号数',
      key: 'brand',
      width: 150,
      render: (_, r) => `${r.brand} · ${r.itemCount}`,
    },
    {
      title: '采购员',
      key: 'purchasers',
      width: 150,
      render: (_, r) =>
        r.purchaserNames.length ? r.purchaserNames.join('、') : '—',
    },
    {
      title: '状态',
      key: 'state',
      width: 130,
      render: (_, r) => (
        <span style={{ display: 'inline-flex', gap: 6 }}>
          <Pill tone={STATE_TONE[r.state] ?? 'gray'}>{r.stateName}</Pill>
          {r.state === 1 && r.salesHandling && (
            <span style={{ fontSize: 12, color: palette.mute }}>
              业务员处理中
            </span>
          )}
        </span>
      ),
    },
    {
      title: '回价时间',
      dataIndex: 'completedAt',
      width: 170,
      render: (v?: string) => (v ? formatDateTime(v) : '—'),
    },
    ...auditColumns<BoardHistoryRow>(),
  ];

  return (
    <Card style={{ padding: 16 }}>
      <div
        style={{
          display: 'flex',
          gap: 12,
          flexWrap: 'wrap',
          marginBottom: 12,
          alignItems: 'center',
        }}
      >
        <DatePicker.RangePicker
          value={range}
          onChange={(v) =>
            setRange(v?.[0] && v[1] ? [v[0], v[1]] : defaultRange())
          }
          allowClear={false}
          aria-label="任务创建日期"
        />
        <Select
          allowClear
          placeholder="状态：全部"
          style={{ width: 150 }}
          value={state}
          onChange={setState}
          options={STATES}
          aria-label="状态"
        />
        <span style={{ fontSize: 12, color: palette.mute }}>
          按任务创建日期查询，默认最近 30 天；点一行查看任务详情
        </span>
      </div>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Table<BoardHistoryRow>
          rowKey="id"
          size="middle"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 'max-content' }}
          onRow={(r) => ({
            onClick: () => onOpen(r.id),
            style: { cursor: 'pointer' },
          })}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              if (s !== pageSize) {
                setPageSize(s);
                setPage(1);
              } else {
                setPage(p);
              }
            },
          }}
        />
      )}
    </Card>
  );
};

export default AllTasksTable;
