import { Link } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Input, Segmented, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerCell } from '@/components/DocFields';
import { ModelQtyCell } from '@/components/DocFields/DocPage';
import SourceNos, { useUrlFilter } from '@/components/DocFields/SourceNos';
import { TagList } from '@/pages/inquiry/customer-inquiry/ModelCells';
import { Card, Pill } from '@/pages/inquiry/shared/components';
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
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { WarehousePageTitle } from '../components';

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
        <ModelQtyCell summary={r.itemSummary} totalQty={r.totalQty} />
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
      width: 80,
      fixed: 'right',
      render: (_, r) => <Link to={PATHS.returnReceipt(r.id)}>查看</Link>,
    },
  ];

  return (
    <div>
      <WarehousePageTitle />
      <Card style={{ padding: 16 }}>
        <div
          style={{
            display: 'flex',
            gap: 12,
            flexWrap: 'wrap',
            marginBottom: 12,
          }}
        >
          <Segmented<number | 'all'>
            value={status}
            onChange={(v) => {
              setStatus(v);
              setPage(1);
            }}
            options={[
              {
                value: RT_STATUS.PENDING,
                label: `待入库 ${counts?.pending ?? ''}`,
              },
              {
                value: RT_STATUS.RECEIVED,
                label: `待处理 ${counts?.received ?? ''}`,
              },
              { value: RT_STATUS.DONE, label: '已处理' },
              { value: RT_STATUS.VOID, label: '已作废' },
              { value: 'all', label: '全部' },
            ]}
          />
          <Input.Search
            allowClear
            placeholder="单号、售后单号、订单号、型号、快递单号"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onSearch={(v) => {
              setQuery(v.trim());
              setPage(1);
            }}
            style={{ width: 320 }}
            aria-label="关键词"
          />
        </div>
        <Table<ReturnReceipt>
          rowKey="id"
          size="middle"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: tableScrollX(columns) }}
          locale={{
            emptyText:
              status === RT_STATUS.PENDING
                ? '没有待入库的退货；业务员确认销售退货单后会出现在这里'
                : '没有符合条件的退货入库单',
          }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPage(p);
              setPageSize(s);
            },
          }}
        />
      </Card>
    </div>
  );
};

export default ReturnsPage;
