import { history, Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, Input, Select, Table } from 'antd';
import { Eye, Inbox, PackageCheck, Search } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { ModelQtyCell } from '@/components/DocFields/DocPage';
import SourceNos, { useUrlFilter } from '@/components/DocFields/SourceNos';
import { ellipsisRender } from '@/components/EllipsisText';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { TagList } from '@/pages/inquiry/customer-inquiry/ModelCells';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import {
  ArrivalCell,
  DOC_PATHS,
  distinctOf,
  ReceiptStatusPill,
  sub,
  WAREHOUSE_PATHS,
} from '../components';
import {
  type Receipt,
  readBizError,
  receiptApi,
  type Shipment,
} from '../service';
import { AcceptDrawer, DirectReceiveDrawer } from './dialogs';

type Tab = 'pending' | 'received';

const Receipts: React.FC = () => {
  const { palette } = useAppTheme();
  const access = useAccess();
  const canEdit = !!access['warehouse:receipt:create'];
  const url = useUrlFilter();
  const [tab, setTab] = useState<Tab>(
    url.tab === 'received' ? 'received' : 'pending',
  );
  const [pending, setPending] = useState<number>();
  const [keyword, setKeyword] = useState(url.keyword);
  const [query, setQuery] = useState(url.keyword || undefined);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [ships, setShips] = useState<Shipment[]>([]);
  const [receipts, setReceipts] = useState<Receipt[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [accepting, setAccepting] = useState<number>();
  const [direct, setDirect] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const q = { keyword: query, page, pageSize };
      if (tab === 'pending') {
        const res = await receiptApi.pending(q);
        setShips(res.records);
        setTotal(res.total);
      } else {
        const res = await receiptApi.page(q);
        setReceipts(res.records);
        setTotal(res.total);
      }
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [tab, query, page, pageSize]);

  const refreshCount = useCallback(() => {
    receiptApi
      .counts()
      .then((c) => setPending(c.pending))
      .catch(() => setPending(undefined));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);
  useEffect(refreshCount, [refreshCount]);

  const afterReceive = (receiptId: number) => {
    setAccepting(undefined);
    setDirect(false);
    load();
    refreshCount();
    history.push(DOC_PATHS.receipt(receiptId));
  };

  const shipColumns: TableColumnsType<Shipment> = [
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
      title: '预计到货',
      dataIndex: 'expectedArrivalDate',
      width: 110,
      render: (v: string | undefined, r) => (
        <ArrivalCell date={v} overdue={r.arrivalOverdue} />
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
      title: '型号 · 数量',
      key: 'items',
      width: 200,
      render: (_, r) => (
        <ModelQtyCell
          items={r.items}
          totalQty={r.totalQuantity}
          docLabel={`${r.sdNo} · ${r.supplierName ?? ''}`}
          onOpenDetail={() => history.push(DOC_PATHS.shipment(r.id))}
        />
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
      title: '登记来源',
      dataIndex: 'sourceName',
      width: 110,
      render: (v: string, r) => (
        <span style={{ color: r.source === 2 ? palette.orange : palette.sub }}>
          {v}
        </span>
      ),
    },
    {
      title: '来源单号',
      dataIndex: 'poNo',
      width: 150,
      render: (v: string | undefined, r) => (
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
      width: rowActionsWidth(2),
      fixed: 'right',
      render: (_, r) => (
        <RowActions
          inline={[
            canEdit && {
              key: 'accept',
              label: '验收入库',
              icon: <PackageCheck size={18} />,
              onClick: () => setAccepting(r.id),
            },
            {
              key: 'view',
              label: '查看',
              icon: <Eye size={18} />,
              onClick: () => history.push(DOC_PATHS.shipment(r.id)),
            },
          ]}
        />
      ),
    },
  ];

  const receiptColumns: TableColumnsType<Receipt> = [
    {
      title: '入库单号 · 收货日期',
      dataIndex: 'grNo',
      width: 170,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <Link to={DOC_PATHS.receipt(r.id)}>{v}</Link>
          {sub(palette.mute, r.receivedDate ?? '—')}
        </div>
      ),
    },
    {
      title: '采购对象',
      dataIndex: 'supplierName',
      width: 150,
      render: (v?: string) => <b style={{ color: palette.ink }}>{v || '—'}</b>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: number, r) => (
        <ReceiptStatusPill value={v}>{r.statusName}</ReceiptStatusPill>
      ),
    },
    {
      title: '合格 · 不良 · 差异',
      key: 'qty',
      width: 140,
      render: (_, r) => (
        <span>
          <span style={{ color: palette.green }}>{r.qualifiedQty}</span>
          {' · '}
          <span style={{ color: r.defectiveQty ? palette.red : palette.mute }}>
            {r.defectiveQty}
          </span>
          {' · '}
          <span
            style={{
              color: r.discrepancyCount ? palette.orange : palette.mute,
            }}
          >
            {r.discrepancyCount}
          </span>
        </span>
      ),
    },
    {
      title: '型号 · 数量',
      key: 'items',
      width: 200,
      render: (_, r) => (
        <ModelQtyCell
          items={r.items?.map((i) => ({
            model: i.model,
            quantity: i.receivedQty,
            brand: i.brand,
            category: i.category,
          }))}
          totalQty={r.receivedQty}
          docLabel={`${r.grNo} · ${r.supplierName ?? ''}`}
          onOpenDetail={() => history.push(DOC_PATHS.receipt(r.id))}
        />
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
      title: '来源单号',
      key: 'source',
      width: 160,
      render: (_, r) => (
        <SourceNos
          refs={[
            { no: r.sdNo, href: DOC_PATHS.shipment(r.shipmentId) },
            { no: r.poNo, href: WAREHOUSE_PATHS.order(r.poId) },
          ]}
        />
      ),
    },
    {
      title: '收货人',
      dataIndex: 'receivedByName',
      width: 90,
      render: ellipsisRender,
    },
    ...auditColumns<Receipt>(),
    {
      title: '操作',
      key: 'actions',
      width: rowActionsWidth(1),
      fixed: 'right',
      render: (_, r) => (
        <RowActions
          inline={[
            {
              key: 'view',
              label: '查看',
              icon: <Eye size={18} />,
              onClick: () => history.push(DOC_PATHS.receipt(r.id)),
            },
          ]}
        />
      ),
    },
  ];

  const apply = () => {
    setQuery(keyword.trim() || undefined);
    setPage(1);
  };
  const changeTab = (t: Tab) => {
    setTab(t);
    setPage(1);
  };
  const pagination = {
    ...PAGER_STYLE,
    current: page,
    pageSize,
    total,
    onChange: (p: number, s: number) => {
      setPage(p);
      setPageSize(s);
    },
  };

  return (
    <div>
      <PageHeader
        help="按发货单验收：只记录实收、合格、不良，差异由采购员处理。没有发货单的货点「直接收货」，系统补一条发货记录并标「仓库补登」"
        extra={
          canEdit && (
            <Button
              type="primary"
              icon={<Inbox size="1em" />}
              onClick={() => setDirect(true)}
            >
              直接收货
            </Button>
          )
        }
      />
      <FilterCard
        onSearch={apply}
        onReset={() => {
          setKeyword('');
          setQuery(undefined);
          changeTab('pending');
        }}
      >
        <FilterField
          label="关键词"
          span={2}
          help={
            tab === 'pending'
              ? '可搜：发货单号、快递单号；采购订单编号、店铺、供应商、采购订单型号'
              : '可搜：采购入库单号；发货单号、快递单号；采购订单编号、店铺、供应商、采购订单型号'
          }
        >
          <Input
            allowClear
            prefix={<Search size="1em" />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={apply}
            placeholder={
              tab === 'pending'
                ? '发货单号、快递单号、采购订单编号'
                : '采购入库单号、发货单号、快递单号'
            }
            aria-label="关键词"
          />
        </FilterField>
        <FilterField label="状态">
          <Select<Tab>
            value={tab}
            onChange={changeTab}
            options={[
              {
                value: 'pending',
                label: pending ? `待收货 · ${pending}` : '待收货',
              },
              { value: 'received', label: '已入库' },
            ]}
            aria-label="状态"
          />
        </FilterField>
      </FilterCard>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <MainCard
          left={tab === 'pending' ? `待收货 ${total} 张` : `已入库 ${total} 张`}
        >
          {tab === 'pending' ? (
            <Table<Shipment>
              rowKey="id"
              columns={shipColumns}
              dataSource={ships}
              loading={loading}
              scroll={{ x: tableScrollX(shipColumns) }}
              locale={{
                emptyText: tableEmpty(
                  query
                    ? '没有匹配的在途发货单；货到了但没有发货单，点「直接收货」'
                    : '没有待收货的发货单',
                  loading,
                ),
              }}
              pagination={pagination}
            />
          ) : (
            <Table<Receipt>
              rowKey="id"
              columns={receiptColumns}
              dataSource={receipts}
              loading={loading}
              scroll={{ x: tableScrollX(receiptColumns) }}
              locale={{ emptyText: tableEmpty('还没有采购入库单', loading) }}
              pagination={pagination}
            />
          )}
        </MainCard>
      )}
      <AcceptDrawer
        shipmentId={accepting}
        onClose={() => setAccepting(undefined)}
        onDone={(d) => afterReceive(d.receipt.id)}
      />
      <DirectReceiveDrawer
        open={direct}
        onClose={() => setDirect(false)}
        onDone={(d) => afterReceive(d.receipt.id)}
      />
    </div>
  );
};

export default Receipts;
