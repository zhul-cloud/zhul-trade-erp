import { Link } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Input, Segmented, Select, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerCell } from '@/components/DocFields';
import { ModelQtyCell } from '@/components/DocFields/DocPage';
import SourceNos, { useUrlFilter } from '@/components/DocFields/SourceNos';
import { TagList } from '@/pages/inquiry/customer-inquiry/ModelCells';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import { auditColumns } from '@/pages/purchase/components';
import {
  HANDLING,
  RQ_STATUS_TONE,
  type SalesReturn,
  salesReturnApi,
  TH_STATUS,
  TH_STATUS_TONE,
} from '@/pages/sales/after-sales/service';
import { searchCustomers } from '@/services/zhul/masterdata';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { PATHS, SalesPageTitle } from '../components';
import { readBizError } from '../service';

/** 销售管理 → 销售退货（spec sales/after-sales）：售后单生成的销售退货单，确认后生成退货入库单 */
const SalesReturnsPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const url = useUrlFilter();
  const [status, setStatus] = useState<number | 'all'>(
    url.keyword ? 'all' : TH_STATUS.CONFIRMING,
  );
  const [handling, setHandling] = useState<number>();
  const [customerId, setCustomerId] = useState<number>();
  const [customerOptions, setCustomerOptions] = useState<
    { value: number; label: string }[]
  >([]);
  const [keyword, setKeyword] = useState(url.keyword);
  const [query, setQuery] = useState(url.keyword);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<SalesReturn[]>([]);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState<{
    confirming: number;
    pending: number;
  }>();
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [res, c] = await Promise.all([
        salesReturnApi.page({
          page,
          pageSize,
          status: status === 'all' ? undefined : status,
          handling,
          customerId,
          keyword: query || undefined,
        }),
        salesReturnApi.counts(),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setCounts(c);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, status, handling, customerId, query, message]);
  useEffect(() => {
    load();
  }, [load]);

  const searchCust = async (kw: string) => {
    try {
      const res = await searchCustomers(kw);
      setCustomerOptions(
        res.map((c) => ({ value: c.id, label: c.displayName ?? c.name })),
      );
    } catch {
      setCustomerOptions([]);
    }
  };

  const sub = (t?: React.ReactNode) =>
    t ? <div style={{ fontSize: 12, color: palette.mute }}>{t}</div> : null;

  const columns: TableColumnsType<SalesReturn> = [
    {
      title: '退货单号 · 退货日期',
      dataIndex: 'returnNo',
      width: 170,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <Link to={PATHS.salesReturn(r.id)}>{v}</Link>
          {sub(r.createTime?.slice(0, 10))}
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
      title: '处理方式',
      dataIndex: 'handlingName',
      width: 100,
      render: (v: string, r) => (
        <div>
          {v}
          {r.refundFirst ? sub('先退款') : null}
        </div>
      ),
    },
    {
      title: '入库',
      key: 'receipt',
      width: 150,
      render: (_, r) =>
        r.receipt ? (
          <div>
            <div style={{ fontSize: 12, color: palette.sub }}>
              {r.receipt.statusName}
              {r.receivedQty != null
                ? ` · 实收 ${r.receivedQty} / 合格 ${r.qualifiedQty ?? 0}`
                : ''}
            </div>
            <Link to={PATHS.returnReceipt(r.receipt.id)}>{r.receipt.no}</Link>
          </div>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '退款',
      key: 'refund',
      width: 140,
      render: (_, r) => {
        const q = r.refunds?.[0];
        if (!q) return <span style={{ color: palette.mute }}>—</span>;
        return (
          <div style={{ display: 'grid', gap: 2, justifyItems: 'start' }}>
            <b>{formatAmount(q.amount, q.currencyCode)}</b>
            <Pill tone={RQ_STATUS_TONE[q.status] ?? 'gray'}>
              {q.statusName}
            </Pill>
          </div>
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'statusName',
      width: 90,
      render: (v: string, r) => (
        <Pill tone={TH_STATUS_TONE[r.status] ?? 'gray'}>{v}</Pill>
      ),
    },
    {
      title: '来源单号',
      key: 'source',
      width: 150,
      render: (_, r) => (
        <SourceNos
          refs={[{ no: r.asNo, href: PATHS.afterSales(r.afterSalesId) }]}
        />
      ),
    },
    { title: '负责人', dataIndex: 'ownerName', width: 100 },
    ...auditColumns<SalesReturn>(),
    {
      title: '操作',
      key: 'op',
      width: 80,
      fixed: 'right',
      render: (_, r) => <Link to={PATHS.salesReturn(r.id)}>查看</Link>,
    },
  ];

  return (
    <div>
      <SalesPageTitle />
      <Card style={{ padding: 16 }}>
        <Segmented<number | 'all'>
          value={status}
          onChange={(v) => {
            setStatus(v);
            setPage(1);
          }}
          options={[
            {
              value: TH_STATUS.CONFIRMING,
              label: `待确认 ${counts?.confirming ?? ''}`,
            },
            {
              value: TH_STATUS.PENDING,
              label: `待入库 ${counts?.pending ?? ''}`,
            },
            { value: TH_STATUS.RECEIVED, label: '已入库' },
            { value: TH_STATUS.VOID, label: '已作废' },
            { value: 'all', label: '全部' },
          ]}
          style={{ marginBottom: 12 }}
        />
        <div
          style={{
            display: 'flex',
            gap: 12,
            flexWrap: 'wrap',
            marginBottom: 12,
          }}
        >
          <Select<number>
            allowClear
            showSearch={{ filterOption: false, onSearch: searchCust }}
            placeholder="客户"
            value={customerId}
            options={customerOptions}
            onFocus={() => customerOptions.length === 0 && searchCust('')}
            onChange={(v) => {
              setCustomerId(v);
              setPage(1);
            }}
            style={{ width: 200 }}
            aria-label="客户"
          />
          <Select<number>
            allowClear
            placeholder="处理方式"
            value={handling}
            options={[
              { value: HANDLING.RETURN, label: '仅退货' },
              { value: HANDLING.REFUND, label: '退货退款' },
            ]}
            onChange={(v) => {
              setHandling(v);
              setPage(1);
            }}
            style={{ width: 160 }}
            aria-label="处理方式"
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
        <Table<SalesReturn>
          rowKey="id"
          size="middle"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: tableScrollX(columns) }}
          locale={{
            emptyText:
              status === TH_STATUS.CONFIRMING
                ? '没有待确认的退货；在售后单选择「仅退货」或「退货退款」后会出现在这里'
                : '没有符合条件的销售退货单',
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

export default SalesReturnsPage;
