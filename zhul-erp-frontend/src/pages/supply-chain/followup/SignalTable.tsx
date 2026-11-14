import { Button, Table, type TableColumnsType } from 'antd';
import dayjs from 'dayjs';
import React from 'react';
import { ellipsisRender } from '@/components/EllipsisText';
import { useAppTheme } from '@/theme/AppTheme';
import { Pill } from '../components';
import { type Signal, SRC, SRC_NAMES } from '../service';

const SRC_TONE: Record<
  number,
  'accent' | 'green' | 'red' | 'orange' | 'cyan' | 'gray'
> = {
  1: 'accent',
  2: 'green',
  3: 'red',
  4: 'orange',
  5: 'cyan',
  6: 'gray',
};

/** 原始内容：搜索词「平台 ·“词”· 出现 N 次」，同行调研写什么就是什么，询盘类「原始型号 · 数量」 */
const rawText = (s: Signal) => {
  if (s.sourceType === SRC.SEARCH)
    return `${s.platform ?? ''} · “${s.content ?? ''}” · 出现 ${s.occurCount ?? 0} 次`;
  if (s.sourceType === SRC.RESEARCH)
    return `${s.platform ? `${s.platform}：` : ''}${s.content ?? ''}`;
  return (
    [s.brandRaw, s.modelRaw].filter(Boolean).join(' ') +
    (s.quantity ? ` · 数量 ${s.quantity}` : '') +
    (s.reasonText ? ` · ${s.reasonText}` : '')
  );
};

/** 待归类信号（需求情报 → 待归类） */
export const SignalTable: React.FC<{
  rows: Signal[];
  total: number;
  page: number;
  loading: boolean;
  picked: number[];
  canEdit: boolean;
  onPick: (ids: number[]) => void;
  onPage: (p: number) => void;
  onClassify: (s: Signal[]) => void;
  onIgnore: (s: Signal[]) => void;
}> = ({
  rows,
  total,
  page,
  loading,
  picked,
  canEdit,
  onPick,
  onPage,
  onClassify,
  onIgnore,
}) => {
  const { palette } = useAppTheme();
  const columns: TableColumnsType<Signal> = [
    {
      title: '来源',
      dataIndex: 'sourceType',
      width: 120,
      render: (v: number) => (
        <Pill tone={SRC_TONE[v] ?? 'gray'}>{SRC_NAMES[v]}</Pill>
      ),
    },
    {
      title: '原始内容',
      key: 'raw',
      width: 380,
      render: (_, s) => ellipsisRender(rawText(s)),
    },
    {
      title: '识别到的品牌 · 型号',
      key: 'hit',
      width: 220,
      render: (_, s) => (
        <div style={{ display: 'grid', minWidth: 0 }}>
          {s.brandId ? (
            <span>{s.brandName}</span>
          ) : s.brandRaw ? (
            <span style={{ color: palette.orange }}>
              品牌未识别「{s.brandRaw}」
            </span>
          ) : (
            <span>未选品牌</span>
          )}
          <span
            title={s.modelRaw}
            style={{
              fontSize: 12,
              color: palette.mute,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {s.modelRaw || '没有型号'}
          </span>
        </div>
      ),
    },
    {
      title: '出现时间',
      dataIndex: 'occurredAt',
      width: 120,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD'),
    },
    {
      title: '来源单号',
      dataIndex: 'sourceDocNo',
      width: 170,
      render: (v?: string) => v || '—',
    },
    {
      title: '操作',
      key: 'op',
      width: 120,
      fixed: 'right',
      render: (_, s) =>
        canEdit && (
          <>
            <Button type="link" size="small" onClick={() => onClassify([s])}>
              归类
            </Button>
            <Button type="link" size="small" onClick={() => onIgnore([s])}>
              忽略
            </Button>
          </>
        ),
    },
  ];
  return (
    <Table<Signal>
      rowKey="id"
      size="middle"
      loading={loading}
      columns={columns}
      dataSource={rows}
      tableLayout="fixed"
      scroll={{ x: 1150 }}
      rowSelection={
        canEdit
          ? { selectedRowKeys: picked, onChange: (k) => onPick(k as number[]) }
          : undefined
      }
      pagination={{
        current: page,
        pageSize: 20,
        total,
        showSizeChanger: false,
        showTotal: (t) => `共 ${t} 条`,
        onChange: onPage,
      }}
    />
  );
};
