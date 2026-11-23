import { history, Link } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Input, Select, Table } from 'antd';
import { Eye, Search } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerCell } from '@/components/DocFields';
import { ModelQtyCell } from '@/components/DocFields/DocPage';
import SourceNos, { useUrlFilter } from '@/components/DocFields/SourceNos';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { IconButton } from '@/components/Shell';
import { TagList } from '@/pages/inquiry/customer-inquiry/ModelCells';
import { Pill } from '@/pages/inquiry/shared/components';
import { auditColumns } from '@/pages/purchase/components';
import {
  type ReturnReceipt,
  RT_STATUS,
  RT_STATUS_TONE,
  returnApi,
} from '@/pages/sales/after-sales/service';
import { PATHS } from '@/pages/sales/components';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';

/** 帮助文字：help-texts.md「退货入库列表」，关键词范围以 search-scope.md 为准 */
export const HELP = {
  page: '业务员确认销售退货单后生成；验收与处理去向在详情页',
  keyword:
    '可搜：退货入库单号、订单编号、退货单号、退货快递单号、售后单号、型号',
} as const;

/** 下拉选项后面带数量（stat-cards.md：状态并入下拉） */
const withCount = (label: string, n?: number) =>
  n ? `${label} · ${n}` : label;

/** 仓库管理 → 退货入库（spec warehouse/sales-return）：退货入库单列表，验收与处理去向在详情页 */
const ReturnsPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const url = useUrlFilter();
  const [status, setStatus] = useState<number | 'all'>(
    url.keyword ? 'all' : RT_STATUS.PENDING,
  );
  const [keyword, setKeyword] = useState(url.keyword);
  const [query, setQuery] = useState(url.keyword);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<ReturnReceipt[]>([]);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState<{ pending: number; received: number }>();
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [res, c] = await Promise.all([
        returnApi.page({
          page,
          pageSize,
          status: status === 'all' ? undefined : status,
          keyword: query || undefined,
        }),
        returnApi.counts(),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setCounts(c);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, status, query, message]);
  useEffect(() => {
    load();
  }, [load]);
  // 从其他页面返回时重新取数（audit UI-055）
  useOnReactivate(load);

  const sub = (t?: React.ReactNode) =>
    t ? <div style={{ fontSize: 12, color: palette.mute }}>{t}</div> : null;

  const columns: TableColumnsType<ReturnReceipt> = [
    {
      title: '入库单号 · 收货日期',
      dataIndex: 'receiptNo',
      width: 170,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <Link to={PATHS.returnReceipt(r.id)}>{v}</Link>
          {sub(r.receivedDate)}
        </div>
      ),
    },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 220,
      render: (v: string, r) => (
        <CustomerCell
          name={v}
          country={r.customerCountry}
          type={r.customerType}
          contact={r.customerContact}
        />
      ),
    },
    {
      title: '型号 · 数量',
      key: 'items',
      width: 200,
      render: (_, r) => (
        <ModelQtyCell
          summary={r.itemSummary}
          totalQty={r.totalQty}
          docLabel={`${r.receiptNo} · ${r.customerName ?? ''}`}
          onOpenDetail={() => history.push(PATHS.returnReceipt(r.id))}
        />
      ),
    },
    {
      title: '品牌',
      dataIndex: 'brands',
      width: 120,
      render: (v?: string[]) => <TagList values={v} label="品牌" />,
    },
    {
      title: '品类',
      dataIndex: 'categories',
      width: 130,
      render: (v?: string[]) => <TagList values={v} label="品类" />,
    },
    {
      title: '客户寄回',
      key: 'express',
      width: 150,
      render: (_, r) => (
        <div>
          {r.carrier || '—'}
          {sub(r.trackingNo)}
        </div>
      ),
    },
    {
      title: '实收 / 合格',
      key: 'qty',
      width: 100,
      render: (_, r) =>
        r.receivedQty == null ? (
          <span style={{ color: palette.mute }}>—</span>
        ) : (
          `${r.receivedQty} / ${r.qualifiedQty ?? 0}`
        ),
    },
    {
      title: '状态',
      dataIndex: 'statusName',
      width: 100,
      render: (v: string, r) => (
        <Pill tone={RT_STATUS_TONE[r.status] ?? 'gray'}>{v}</Pill>
      ),
    },
    {
      title: '来源单号',
      key: 'source',
      width: 160,
      render: (_, r) => (
        <SourceNos
          refs={[{ no: r.returnNo, href: PATHS.salesReturn(r.salesReturnId) }]}
        />
      ),
    },
    { title: '负责人', dataIndex: 'ownerName', width: 100 },
    ...auditColumns<ReturnReceipt>(),
    {
      title: '操作',
      key: 'op',
      width: 64,
      fixed: 'right',
      render: (_, r) => (
        <IconButton
          label="查看"
          size={32}
          icon={<Eye size={18} />}
          onClick={() => history.push(PATHS.returnReceipt(r.id))}
        />
      ),
    },
  ];

  const search = () => {
    setQuery(keyword.trim());
    setPage(1);
  };

  return (
    <div>
      <PageHeader help={HELP.page} />
      <FilterCard
        onSearch={search}
        onReset={() => {
          // 重置回默认视图：待入库
          setKeyword('');
          setQuery('');
          setStatus(RT_STATUS.PENDING);
          setPage(1);
        }}
        minColumn={130}
      >
        <FilterField label="关键词" span={2} help={HELP.keyword}>
          <Input
            allowClear
            prefix={<Search size={16} />}
            placeholder="入库单号、售后单号、型号"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={search}
            aria-label="关键词"
          />
        </FilterField>
        <FilterField label="状态">
          <Select<number>
            allowClear
            placeholder="全部"
            value={status === 'all' ? undefined : status}
            onChange={(v) => {
              // 切换状态立即查询（doc-list-unify 状态下拉的写法）
              setStatus(v ?? 'all');
              setPage(1);
            }}
            options={[
              {
                value: RT_STATUS.PENDING,
                label: withCount('待入库', counts?.pending),
              },
              {
                value: RT_STATUS.RECEIVED,
                label: withCount('待处理', counts?.received),
              },
              { value: RT_STATUS.DONE, label: '已处理' },
              { value: RT_STATUS.VOID, label: '已作废' },
            ]}
            aria-label="状态"
          />
        </FilterField>
      </FilterCard>
      <MainCard left={loading && !total ? undefined : `共 ${total} 条`}>
        <Table<ReturnReceipt>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: tableScrollX(columns) }}
          locale={{
            emptyText: tableEmpty(
              status === RT_STATUS.PENDING
                ? '没有待入库的退货；业务员确认销售退货单后会出现在这里'
                : '没有符合条件的退货入库单',
              loading,
            ),
          }}
          pagination={{
            ...PAGER_STYLE,
            current: page,
            pageSize,
            total,
            onChange: (p, s) => {
              setPage(p);
              setPageSize(s);
            },
          }}
        />
      </MainCard>
    </div>
  );
};

export default ReturnsPage;
