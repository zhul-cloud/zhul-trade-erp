import { SearchOutlined } from '@ant-design/icons';
import { history, Link, useAccess, useLocation } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Input, Segmented, Select, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { ModelQtyCell } from '@/components/DocFields/DocPage';
import SourceNos, { useUrlFilter } from '@/components/DocFields/SourceNos';
import { ellipsisRender } from '@/components/EllipsisText';
import { TagList } from '@/pages/inquiry/customer-inquiry/ModelCells';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { PATHS as SALES_PATHS } from '@/pages/sales/components';
import {
  ArrivalCell,
  Card,
  DiffStatusPill,
  DiffTypePill,
  DOC_PATHS,
  distinctOf,
  ShipStatusPill,
  sub,
  WAREHOUSE_PATHS,
  WarehousePageTitle,
} from '@/pages/warehouse/components';
import {
  type Discrepancy,
  discrepancyApi,
  readBizError,
  type Shipment,
  shipmentApi,
} from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import HandleDiscrepancyModal from './HandleDiscrepancyModal';

type Tab = 'shipments' | 'diffs';

const SupplierShipments: React.FC = () => {
  const { search } = useLocation();
  const [tab, setTab] = useState<Tab>(
    new URLSearchParams(search).get('tab') === 'diffs' ? 'diffs' : 'shipments',
  );
  const [pending, setPending] = useState<number>();
  const refreshCount = useCallback(() => {
    discrepancyApi
      .counts()
      .then((c) => setPending(c.pending))
      .catch(() => setPending(undefined));
  }, []);
  useEffect(refreshCount, [refreshCount]);

  return (
    <div>
      <WarehousePageTitle description="登记供应商发出的货；仓库验收后发现的少发、不良、多发在这里处理。" />
      <Segmented<Tab>
        value={tab}
        onChange={setTab}
        style={{ marginBottom: 16 }}
        options={[
          { value: 'shipments', label: '发货记录' },
          {
            value: 'diffs',
            label: pending ? `到货差异 · 待处理 ${pending}` : '到货差异',
          },
        ]}
      />
      {tab === 'shipments' ? (
        <ShipmentsTab />
      ) : (
        <DiffsTab onChanged={refreshCount} />
      )}
    </div>
  );
};

// ---------------------------------------------------------------- 发货记录

const ShipmentsTab: React.FC = () => {
  const { palette } = useAppTheme();
  const url = useUrlFilter();
  const [keyword, setKeyword] = useState(url.keyword);
  const [status, setStatus] = useState<number>();
  const [query, setQuery] = useState<{ keyword?: string; status?: number }>(
    url.keyword ? { keyword: url.keyword } : {},
  );
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<Shipment[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const res = await shipmentApi.page({ ...query, page, pageSize });
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [query, page, pageSize]);
  useEffect(() => {
    load();
  }, [load]);

  const columns: TableColumnsType<Shipment> = [
    {
      title: '发货单号 · 发货日期',
      dataIndex: 'sdNo',
      width: 170,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <Link to={DOC_PATHS.shipment(r.id)}>{v}</Link>
          {sub(palette.mute, r.shipDate ?? '—')}
        </div>
      ),
    },
    {
      title: '采购对象',
      dataIndex: 'supplierName',
      width: 150,
      render: (v: string, r) => (
        <div>
          <b style={{ color: palette.ink }}>{v || '—'}</b>
          {sub(palette.mute, r.shop ? '平台店铺' : '供应商')}
        </div>
      ),
    },
    {
      title: '型号 · 数量',
      key: 'items',
      width: 200,
      render: (_, r) => (
        <ModelQtyCell items={r.items} totalQty={r.totalQuantity} />
      ),
    },
    {
      title: '品牌',
      key: 'brands',
      width: 120,
      render: (_, r) => (
        <TagList values={distinctOf(r.items, 'brand')} label="品牌" />
      ),
    },
    {
      title: '品类',
      key: 'categories',
      width: 130,
      render: (_, r) => (
        <TagList values={distinctOf(r.items, 'category')} label="品类" />
      ),
    },
    {
      title: '快递 · 单号',
      key: 'tracking',
      width: 180,
      render: (_, r) => (
        <div>
          <b style={{ color: palette.ink }}>{r.carrier || '—'}</b>
          {r.trackingNo && sub(palette.mute, r.trackingNo)}
        </div>
      ),
    },
    {
      title: '预计到货',
      dataIndex: 'expectedArrivalDate',
      width: 110,
      render: (v: string | undefined, r) => (
        <ArrivalCell date={v} overdue={r.arrivalOverdue} />
      ),
    },
    {
      title: '登记来源',
      dataIndex: 'sourceName',
      width: 120,
      render: (v: string, r) => (
        <div>
          <span
            style={{ color: r.source === 2 ? palette.orange : palette.sub }}
          >
            {v}
          </span>
          {r.directForwarderName &&
            sub(palette.violet, `直发 ${r.directForwarderName}`)}
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (v: number, r) => (
        <ShipStatusPill value={v}>{r.statusName}</ShipStatusPill>
      ),
    },
    {
      title: '来源单号',
      dataIndex: 'poNo',
      width: 160,
      render: (v: string, r) => (
        <SourceNos refs={[{ no: v, href: WAREHOUSE_PATHS.order(r.poId) }]} />
      ),
    },
    {
      title: '采购员',
      dataIndex: 'purchaserName',
      width: 90,
      render: ellipsisRender,
    },
    ...auditColumns<Shipment>(),
    {
      title: '操作',
      key: 'actions',
      width: 80,
      fixed: 'right',
      render: (_, r) => <Link to={DOC_PATHS.shipment(r.id)}>查看</Link>,
    },
  ];

  return (
    <>
      <Card style={{ marginBottom: 16, padding: 20 }}>
        <div
          style={{
            display: 'flex',
            gap: 12,
            flexWrap: 'wrap',
            alignItems: 'center',
          }}
        >
          <Input
            allowClear
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={() => {
              setQuery({ keyword: keyword.trim() || undefined, status });
              setPage(1);
            }}
            placeholder="发货单号、采购订单号、快递单号、型号、采购对象"
            style={{ width: 360 }}
            aria-label="关键词"
          />
          <Select
            allowClear
            placeholder="全部状态"
            value={status}
            onChange={setStatus}
            style={{ width: 140 }}
            options={[
              { value: 1, label: '在途' },
              { value: 2, label: '已入库' },
              { value: 3, label: '已作废' },
            ]}
            aria-label="状态"
          />
          <span style={{ flex: 1 }} />
          <Button
            type="primary"
            onClick={() => {
              setQuery({ keyword: keyword.trim() || undefined, status });
              setPage(1);
            }}
          >
            查询
          </Button>
        </div>
      </Card>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Table<Shipment>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: tableScrollX(columns) }}
          locale={{
            emptyText: '还没有发货记录；在采购订单详情点「登记发货」',
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
      )}
    </>
  );
};

// ---------------------------------------------------------------- 到货差异

const DiffsTab: React.FC<{ onChanged: () => void }> = ({ onChanged }) => {
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const access = useAccess();
  const canHandle = !!access['purchase:discrepancy:handle'];
  const [keyword, setKeyword] = useState('');
  const [type, setType] = useState<number>();
  const [status, setStatus] = useState<number | undefined>(1);
  const [query, setQuery] = useState<{
    keyword?: string;
    type?: number;
    status?: number;
  }>({
    status: 1,
  });
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20, 'pending');
  const [rows, setRows] = useState<Discrepancy[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [handling, setHandling] = useState<Discrepancy>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const res = await discrepancyApi.page({ ...query, page, pageSize });
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [query, page, pageSize]);
  useEffect(() => {
    load();
  }, [load]);

  const apply = () => {
    setQuery({ keyword: keyword.trim() || undefined, type, status });
    setPage(1);
  };

  const resolutionDetail = (r: Discrepancy) => {
    const parts: string[] = [];
    if (r.discountAmount != null)
      parts.push(
        `折价 ${formatAmount(r.discountAmount, r.currencyCode ?? 'CNY')}`,
      );
    const t = [r.returnCarrier, r.returnTrackingNo].filter(Boolean).join(' ');
    if (t) parts.push(`退货 ${t}`);
    if (r.returnFreight != null)
      parts.push(`运费 ${formatAmount(r.returnFreight, 'CNY')}`);
    if (r.resolution === 7)
      parts.push(
        r.freeOfCharge ? '供应商白送，成本 CNY 0.00' : '按采购价计成本',
      );
    if (r.note) parts.push(r.note);
    return parts.join('；');
  };

  const columns: TableColumnsType<Discrepancy> = [
    {
      title: '采购入库单 · 日期',
      dataIndex: 'grNo',
      width: 160,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <Link to={DOC_PATHS.receipt(r.receiptId)}>{v}</Link>
          {sub(palette.mute, r.receivedDate ?? '—')}
        </div>
      ),
    },
    {
      title: '供应商',
      dataIndex: 'supplierName',
      width: 150,
      render: (v: string) => <b style={{ color: palette.ink }}>{v || '—'}</b>,
    },
    {
      title: '型号',
      dataIndex: 'model',
      width: 190,
      render: (v: string, r) => (
        <div>
          <b style={{ color: palette.ink }}>{v}</b>
          {sub(
            palette.mute,
            [r.brand, r.category].filter(Boolean).join(' · ') || '—',
          )}
        </div>
      ),
    },
    {
      title: '差异',
      dataIndex: 'type',
      width: 100,
      render: (v: number, r) => (
        <DiffTypePill value={v}>
          {r.typeName} {r.quantity}
        </DiffTypePill>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number, r) => (
        <DiffStatusPill value={v}>{r.statusName}</DiffStatusPill>
      ),
    },
    {
      title: '处理方式',
      dataIndex: 'resolutionName',
      width: 240,
      render: (v: string | undefined, r) =>
        v ? (
          <div>
            <b style={{ color: palette.ink }}>{v}</b>
            {resolutionDetail(r) && sub(palette.mute, resolutionDetail(r))}
            {r.purchaseReturnId && r.purchaseReturnNo && (
              <Link to={SALES_PATHS.purchaseReturn(r.purchaseReturnId)}>
                {r.purchaseReturnNo}
              </Link>
            )}
          </div>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '来源单号',
      dataIndex: 'poNo',
      width: 160,
      render: (v: string, r: { poId: number }) => (
        <button
          type="button"
          className="zhul-link"
          onClick={() => history.push(WAREHOUSE_PATHS.order(r.poId))}
        >
          {v}
        </button>
      ),
    },
    {
      title: '采购员',
      dataIndex: 'purchaserName',
      width: 90,
      render: ellipsisRender,
    },
    ...auditColumns<Discrepancy>(),
    {
      title: '操作',
      key: 'actions',
      width: 100,
      fixed: 'right',
      render: (_, r) =>
        !canHandle ? null : r.status === 1 ? (
          <button
            type="button"
            className="zhul-link"
            onClick={() => setHandling(r)}
          >
            处理
          </button>
        ) : (
          <button
            type="button"
            className="zhul-link"
            onClick={() =>
              modal.confirm({
                title: `重新打开「${r.model} ${r.typeName} ${r.quantity}」？`,
                content: `撤销「${r.resolutionName}」：采购订单数量、闲置库存与订单进度恢复到处理前，差异回到待处理。`,
                okText: '重新打开',
                onOk: async () => {
                  try {
                    await discrepancyApi.reopen(r.id);
                    message.success('已重新打开');
                    load();
                    onChanged();
                  } catch (e) {
                    message.error(readBizError(e).message);
                  }
                },
              })
            }
          >
            重新打开
          </button>
        ),
    },
  ];

  return (
    <>
      <Card style={{ marginBottom: 16, padding: 20 }}>
        <div
          style={{
            display: 'flex',
            gap: 12,
            flexWrap: 'wrap',
            alignItems: 'center',
          }}
        >
          <Select
            allowClear
            placeholder="全部类型"
            value={type}
            onChange={setType}
            style={{ width: 130 }}
            options={[
              { value: 1, label: '少发' },
              { value: 2, label: '不良' },
              { value: 3, label: '多发' },
            ]}
            aria-label="差异类型"
          />
          <Select
            allowClear
            placeholder="全部状态"
            value={status}
            onChange={setStatus}
            style={{ width: 130 }}
            options={[
              { value: 1, label: '待处理' },
              { value: 2, label: '已处理' },
            ]}
            aria-label="状态"
          />
          <Input
            allowClear
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={apply}
            placeholder="采购入库单、采购订单、型号、采购对象"
            style={{ width: 300 }}
            aria-label="关键词"
          />
          <span style={{ flex: 1 }} />
          <Button type="primary" onClick={apply}>
            查询
          </Button>
        </div>
      </Card>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Table<Discrepancy>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: tableScrollX(columns) }}
          locale={{
            emptyText:
              query.status === 1
                ? '没有待处理的到货差异'
                : '没有符合条件的到货差异',
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
      )}
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
        仓库只记录实收、合格、不良；怎么处理由采购员和供应商沟通后决定。
      </div>
      <HandleDiscrepancyModal
        target={handling}
        onClose={() => setHandling(undefined)}
        onDone={() => {
          setHandling(undefined);
          load();
          onChanged();
        }}
      />
    </>
  );
};

export default SupplierShipments;
