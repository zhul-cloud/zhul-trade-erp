import { Button, Table, type TableColumnsType } from 'antd';
import dayjs from 'dayjs';
import React from 'react';
import { ellipsisRender } from '@/components/EllipsisText';
import { auditColumns } from '@/components/UserName';
import { useAppTheme } from '@/theme/AppTheme';
import { PAGER_STYLE } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
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
  pageSize: number;
  loading: boolean;
  picked: number[];
  canEdit: boolean;
  onPick: (ids: number[]) => void;
  /** 翻页或改每页条数；改每页条数时回到第 1 页 */
  onPage: (p: number, size: number) => void;
  onClassify: (s: Signal[]) => void;
  onIgnore: (s: Signal[]) => void;
}> = ({
  rows,
  total,
  page,
  pageSize,
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
    // 列表固定的四列（audit UI-093）
    ...auditColumns<Signal>(),
    {
      // lint-ui-ignore row-actions: 供应链跟进工作队列，保留按钮
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
      scroll={{ x: tableScrollX(columns) }}
      rowSelection={
        canEdit
          ? { selectedRowKeys: picked, onChange: (k) => onPick(k as number[]) }
          : undefined
      }
      pagination={{
        ...PAGER_STYLE,
        current: page,
        pageSize,
        total,
        showTotal: (t) => `共 ${t} 条`,
        onChange: (p, s) => onPage(s === pageSize ? p : 1, s),
      }}
    />
  );
};
