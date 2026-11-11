import { Link } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Input, Segmented, Select, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { ModelQtyCell } from '@/components/DocFields/DocPage';
import SourceNos, { useUrlFilter } from '@/components/DocFields/SourceNos';
import { TagList } from '@/pages/inquiry/customer-inquiry/ModelCells';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import { PR_STATUS_TONE } from '@/pages/sales/after-sales/service';
import { PATHS as SALES_PATHS } from '@/pages/sales/components';
import { useUserOptions } from '@/pages/sales/orders/dialogs';
import { readBizError } from '@/pages/sales/service';
import { searchSuppliers } from '@/services/zhul/masterdata';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { auditColumns, PATHS, PurchasePageTitle } from '../components';
import {
  PR_SOURCE_OPTIONS,
  PR_STATUS,
  type PurchaseReturn,
  prSourceRef,
  purchaseReturnApi,
} from './service';

/** 采购管理 → 采购退货（spec purchase/purchase-return） */
const PurchaseReturnsPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const url = useUrlFilter();
  const users = useUserOptions();
  const [status, setStatus] = useState<number | 'all'>(
    url.keyword ? 'all' : PR_STATUS.PENDING,
  );
  const [supplierId, setSupplierId] = useState<number>();
  const [supplierOptions, setSupplierOptions] = useState<
    { value: number; label: string }[]
  >([]);
  const [sourceType, setSourceType] = useState<number>();
  const [ownerId, setOwnerId] = useState<number>();
  const [keyword, setKeyword] = useState(url.keyword);
  const [query, setQuery] = useState(url.keyword);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<PurchaseReturn[]>([]);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState<{ pending: number; shipped: number }>();
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [res, c] = await Promise.all([
        purchaseReturnApi.page({
          page,
          pageSize,
          status: status === 'all' ? undefined : status,
          supplierId,
          sourceType,
          ownerId,
          keyword: query || undefined,
        }),
        purchaseReturnApi.counts(),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setCounts(c);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, status, supplierId, sourceType, ownerId, query, message]);
  useEffect(() => {
    load();
  }, [load]);

  const searchSup = async (kw: string) => {
    try {
      const res = await searchSuppliers(kw);
      setSupplierOptions(
        res.map((s: { id: number; name: string }) => ({
          value: s.id,
          label: s.name,
        })),
      );
    } catch {
      setSupplierOptions([]);
    }
  };

  const sub = (t?: React.ReactNode) =>
    t ? <div style={{ fontSize: 12, color: palette.mute }}>{t}</div> : null;
  const dash = <span style={{ color: palette.mute }}>—</span>;

  const columns: TableColumnsType<PurchaseReturn> = [
    {
      title: '采购退货单号 · 日期',
      dataIndex: 'returnNo',
      width: 170,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <Link to={SALES_PATHS.purchaseReturn(r.id)}>{v}</Link>
          {sub(r.createTime?.slice(0, 10))}
        </div>
      ),
    },
    { title: '供应商', dataIndex: 'supplierName', width: 160 },
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
    { title: '来源类型', dataIndex: 'sourceTypeName', width: 150 },
    {
      title: '采购订单',
      dataIndex: 'poNo',
      width: 160,
      render: (v: string | undefined, r) =>
        v && r.poId ? <Link to={PATHS.order(r.poId)}>{v}</Link> : dash,
    },
    {
      title: '快递',
      key: 'express',
      width: 150,
      render: (_, r) =>
        r.carrier ? (
          <div>
            {r.carrier}
            {sub(r.trackingNo)}
          </div>
        ) : (
          dash
        ),
    },
    {
      title: '运费',
      dataIndex: 'freightCny',
      width: 100,
      render: (v?: number) =>
        Number(v) > 0 ? formatAmount(v ?? 0, 'CNY') : dash,
    },
    {
      title: '退款或抵扣',
      dataIndex: 'refundAmount',
      width: 120,
      render: (v: number | undefined, r) =>
        r.status === PR_STATUS.DONE
          ? formatAmount(v ?? 0, r.currencyCode ?? 'CNY')
          : dash,
    },
    {
      title: '状态',
      dataIndex: 'statusName',
      width: 90,
      render: (v: string, r) => (
        <Pill tone={PR_STATUS_TONE[r.status] ?? 'gray'}>{v}</Pill>
      ),
    },
    {
      title: '来源单号',
      key: 'source',
      width: 160,
      render: (_, r) => <SourceNos refs={[prSourceRef(r)]} />,
    },
    { title: '负责人', dataIndex: 'ownerName', width: 100 },
    ...auditColumns<PurchaseReturn>(),
    {
      title: '操作',
      key: 'op',
      width: 80,
      fixed: 'right',
      render: (_, r) => <Link to={SALES_PATHS.purchaseReturn(r.id)}>查看</Link>,
    },
  ];

  return (
    <div>
      <PurchasePageTitle />
      <Card style={{ padding: 16 }}>
        <Segmented<number | 'all'>
          value={status}
          onChange={(v) => {
            setStatus(v);
            setPage(1);
          }}
          options={[
            {
              value: PR_STATUS.PENDING,
              label: `待寄出 ${counts?.pending ?? ''}`,
            },
            {
              value: PR_STATUS.SHIPPED,
              label: `已寄出 ${counts?.shipped ?? ''}`,
            },
            { value: PR_STATUS.DONE, label: '已完成' },
            { value: PR_STATUS.VOID, label: '已作废' },
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
            showSearch={{ filterOption: false, onSearch: searchSup }}
            placeholder="供应商"
            value={supplierId}
            options={supplierOptions}
            onFocus={() => supplierOptions.length === 0 && searchSup('')}
            onChange={(v) => {
              setSupplierId(v);
              setPage(1);
            }}
            style={{ width: 180 }}
            aria-label="供应商"
          />
          <Select<number>
            allowClear
            placeholder="来源类型"
            value={sourceType}
            options={PR_SOURCE_OPTIONS}
            onChange={(v) => {
              setSourceType(v);
              setPage(1);
            }}
            style={{ width: 180 }}
            aria-label="来源类型"
          />
          <Select<number>
            allowClear
            showSearch={{ optionFilterProp: 'label' }}
            placeholder="负责人"
            value={ownerId}
            options={users}
            onChange={(v) => {
              setOwnerId(v);
              setPage(1);
            }}
            style={{ width: 150 }}
            aria-label="负责人"
          />
          <Input.Search
            allowClear
            placeholder="单号、型号、快递单号"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onSearch={(v) => {
              setQuery(v.trim());
              setPage(1);
            }}
            style={{ width: 280 }}
            aria-label="关键词"
          />
        </div>
        <Table<PurchaseReturn>
          rowKey="id"
          size="middle"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: tableScrollX(columns) }}
          locale={{
            emptyText:
              status === PR_STATUS.PENDING
                ? '没有待寄出的采购退货；退货入库去向选「退回供应商」或到货差异选择退货后会出现在这里'
                : '没有符合条件的采购退货单',
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

export default PurchaseReturnsPage;
