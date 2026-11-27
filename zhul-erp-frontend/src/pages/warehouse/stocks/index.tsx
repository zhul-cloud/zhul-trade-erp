import { history, useAccess, useLocation } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Form, Input, Select, Table } from 'antd';
import { PackageMinus, ScrollText, Search, Undo2 } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { useColumnSettings } from '@/components/ColumnSettings';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterField,
  HeadHelp,
  MainCard,
  PageHeader,
  QuickFilters,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime, formatMoney } from '@/utils/format';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import {
  BatchLink,
  FilterCard,
  ModelCell,
  StockStatusPill,
  useWarehouses,
  WarehouseCell,
  type WarehouseFilter,
  warehouseFilterOptions,
  warehouseQuery,
} from '../inventoryParts';
import {
  type StockBatch,
  type StockRow,
  type StockSummary,
  stockApi,
  WH_OWN,
} from '../inventoryService';
import { readBizError } from '../service';
import {
  OtherOutboundModal,
  ReturnToSupplierModal,
  type StockTarget,
} from './dialogs';

interface Filters {
  keyword?: string;
  warehouse: WarehouseFilter;
  /** has：只看有库存（默认）；available：只看可用；defective：只看有不良 */
  quick?: 'available' | 'defective';
}

const targetOf = (r: StockRow): StockTarget => ({
  productId: r.productId,
  model: r.model,
  brand: r.brand,
  warehouseId: r.warehouseId,
  warehouseName: r.warehouseName,
  warehouseType: r.warehouseType,
});

/** 展开行：该仓库该商品的批次（原型 I1） */
const BatchRows: React.FC<{
  row: StockRow;
  version: number;
  onOutbound: (t: StockTarget) => void;
  onReturn: (t: StockTarget) => void;
}> = ({ row, version, onOutbound, onReturn }) => {
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const [list, setList] = useState<StockBatch[]>();
  const [error, setError] = useState<string>();
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    setError(undefined);
    stockApi
      .batches(row.warehouseId, row.productId)
      .then(setList)
      .catch((e) => setError(readBizError(e).message));
  }, [row.warehouseId, row.productId, version, retry]);
  const canOut = !!access['inventory:other-outbound:edit'];
  const canReturn =
    !!access['inventory:return-to-supplier'] && row.warehouseType === WH_OWN;
  const columns: TableColumnsType<StockBatch> = [
    {
      title: '批次号',
      dataIndex: 'batchNo',
      width: 180,
      render: (_, b) => (
        <BatchLink
          batchNo={b.batchNo}
          sourceType={b.sourceType}
          sourceId={b.sourceId}
        />
      ),
    },
    {
      title: '入库日期',
      dataIndex: 'inDate',
      width: 110,
      render: (v?: string) => v ?? '—',
    },
    {
      title: '成色',
      dataIndex: 'grade',
      width: 100,
      render: (v?: string) => v || '—',
    },
    {
      title: '状态',
      dataIndex: 'stockStatus',
      width: 70,
      render: (v: number, b) => (
        <StockStatusPill status={v} name={b.stockStatusName} />
      ),
    },
    { title: '结存', align: 'right' as const, dataIndex: 'onHand', width: 70 },
    {
      title: '预占给',
      key: 'res',
      width: 260,
      render: (_, b) => (
        <div style={{ display: 'grid', gap: 2 }}>
          {b.reservations.map((x) => (
            <span key={`${x.targetType}:${x.targetId}`}>
              {x.docNo || '—'}
              <span style={{ color: palette.mute }}>
                {' '}
                · {x.targetType === 1 ? '订单预占' : '退货待寄出'} {x.quantity}
              </span>
            </span>
          ))}
          {b.free > 0 && (
            <span style={{ color: palette.mute }}>
              可用{b.stockStatus === 2 ? '（不良）' : ''} {b.free}
            </span>
          )}
        </div>
      ),
    },
    {
      title: '单价（CNY）',
      dataIndex: 'unitPriceCny',
      width: 110,
      render: (v: number) => Number(v).toFixed(6),
    },
    {
      title: '金额（CNY）',
      align: 'right',
      dataIndex: 'amountCny',
      width: 110,
      render: (v: number) => formatMoney(v),
    },
    {
      title: '操作',
      key: 'op',
      width: rowActionsWidth(2),
      render: (_, b) =>
        b.free > 0 ? (
          <RowActions
            inline={[
              canOut && {
                key: 'out',
                label: '其他出库',
                icon: <PackageMinus size={18} />,
                onClick: () => onOutbound(targetOf(row)),
              },
              canReturn && {
                key: 'return',
                label: '退回供应商',
                icon: <Undo2 size={18} />,
                onClick: () => onReturn(targetOf(row)),
              },
            ]}
          />
        ) : null,
    },
  ];
  if (error)
    return (
      <ErrorHint page message={error} onRetry={() => setRetry((n) => n + 1)} />
    );
  return (
    <Table<StockBatch>
      rowKey={(b) => `${b.batchId}:${b.stockStatus}`}
      size="small"
      loading={!list}
      columns={columns}
      dataSource={list}
      pagination={false}
      scroll={{ x: tableScrollX(columns) }}
    />
  );
};

/** 仓库管理 → 库存查询（spec warehouse/inventory「库存查询」，原型 I1）：按商品 × 仓库汇总，展开看批次 */
const StocksPage: React.FC = () => {
  const { palette } = useAppTheme();
  const location = useLocation();
  const url = new URLSearchParams(location.search);
  const warehouses = useWarehouses();
  const [form] = Form.useForm();
  const initial = (): Filters => ({
    keyword: url.get('keyword') ?? undefined,
    warehouse: url.get('warehouseId')
      ? Number(url.get('warehouseId'))
      : url.get('warehouse') === 'own'
        ? 'own'
        : 'all',
  });
  const [filters, setFilters] = useState<Filters>(initial);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<StockRow[]>([]);
  const [total, setTotal] = useState(0);
  const [summary, setSummary] = useState<StockSummary>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [expanded, setExpanded] = useState<React.Key[]>([]);
  const [version, setVersion] = useState(0);
  const [outbound, setOutbound] = useState<StockTarget>();
  const [toSupplier, setToSupplier] = useState<StockTarget>();

  const query = {
    keyword: filters.keyword?.trim() || undefined,
    ...warehouseQuery(filters.warehouse),
    onlyAvailable: filters.quick === 'available' || undefined,
    onlyDefective: filters.quick === 'defective' || undefined,
  };
  const queryKey = JSON.stringify(query);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const q = JSON.parse(queryKey);
      const [res, s] = await Promise.all([
        stockApi.page({ ...q, page, pageSize }),
        stockApi.summary(q),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setSummary(s);
      setVersion((v) => v + 1);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [queryKey, page, pageSize]);
  useEffect(() => {
    load();
  }, [load]);
  // 从其他页面返回时重新取数（audit UI-055）
  useOnReactivate(load);

  const rowKey = (r: StockRow) => `${r.productId}:${r.warehouseId}`;
  const num = (v: number | undefined, color?: string) =>
    v ? (
      <span style={{ color, fontVariantNumeric: 'tabular-nums' }}>{v}</span>
    ) : (
      <span style={{ color: palette.mute }}>0</span>
    );

  const columns: TableColumnsType<StockRow> = [
    {
      title: '品牌 · 型号',
      dataIndex: 'model',
      width: 220,
      fixed: 'left',
      render: (_, r) => (
        <ModelCell model={r.model} brand={r.brand} category={r.category} />
      ),
    },
    {
      title: '仓库',
      dataIndex: 'warehouseName',
      width: 140,
      render: (v: string, r) => (
        <WarehouseCell name={v} type={r.warehouseType} />
      ),
    },
    {
      title: '合格结存',
      dataIndex: 'good',
      width: 90,
      render: (v: number) => <b>{v}</b>,
    },
    {
      title: '不良',
      align: 'right' as const,
      dataIndex: 'defective',
      width: 70,
      render: (v: number) => num(v, palette.red),
    },
    {
      title: '订单预占',
      dataIndex: 'orderReserved',
      width: 90,
      render: (v: number) => num(v),
    },
    {
      title: '退货待寄出',
      dataIndex: 'returnReserved',
      width: 100,
      render: (v: number) => num(v, palette.orange),
    },
    {
      title: (
        <HeadHelp title="可用" help="没有预占给订单，可以报废、转样品或退回" />
      ),
      dataIndex: 'available',
      width: 90,
      render: (v: number) => num(v, palette.green),
    },
    {
      title: '在途',
      align: 'right' as const,
      dataIndex: 'inTransit',
      width: 70,
      render: (v?: number) =>
        v == null ? <span style={{ color: palette.mute }}>—</span> : num(v),
    },
    {
      title: '库存金额（CNY）',
      align: 'right',
      dataIndex: 'amountCny',
      width: 120,
      render: (v: number) => formatMoney(v),
    },
    {
      title: '最近变动',
      dataIndex: 'lastMoveTime',
      width: 150,
      render: (v?: string) => formatDateTime(v).slice(0, 16) || '—',
    },
    {
      title: '操作',
      key: 'op',
      width: rowActionsWidth(1),
      fixed: 'right',
      render: (_, r) => (
        <RowActions
          inline={[
            {
              key: 'moves',
              label: '流水',
              icon: <ScrollText size={18} />,
              onClick: () =>
                history.push(
                  `/warehouse/moves?productId=${r.productId}&warehouseId=${r.warehouseId}&model=${encodeURIComponent(r.model)}`,
                ),
            },
          ]}
        />
      ),
    },
  ];
  const columnsSet = useColumnSettings('warehouse-stocks', columns, {
    hidden: ['退货待寄出', '最近变动'],
  });

  const apply = (f: Filters) => {
    setFilters(f);
    setPage(1);
  };
  /** 帮助文字：help-texts.md「库存查询」 */
  const HELP = {
    page: '按商品 × 仓库汇总；展开看批次',
    returnPending: '已生成采购退货单、还没寄出的件数',
  };
  const quick = (k: 'available' | 'defective') =>
    apply({ ...filters, quick: filters.quick === k ? undefined : k });

  return (
    <div>
      <PageHeader
        help={HELP.page}
        summary={
          summary
            ? [
                {
                  key: 'amount',
                  label: '库存金额',
                  value: summary.amountCny ?? 0,
                  currency: 'CNY',
                  sub: `${summary.productCount ?? 0} 个商品 · ${summary.warehouseCount ?? 0} 个仓库`,
                },
                {
                  key: 'returnPending',
                  label: '退货待寄出',
                  help: HELP.returnPending,
                  value: `${summary.returnPending ?? 0} 件`,
                },
              ]
            : undefined
        }
      />
      <FilterCard
        form={form}
        initialValues={initial()}
        onSearch={(v) =>
          apply({ ...filters, ...(v as Partial<Filters>) } as Filters)
        }
        onReset={() => {
          history.replace('/warehouse/stocks');
          apply({ warehouse: 'all' });
        }}
      >
        <FilterField label="关键词" span={2}>
          <Form.Item name="keyword" noStyle>
            <Input
              aria-label="关键词"
              allowClear
              prefix={<Search size="1em" />}
              onPressEnter={() => form.submit()}
              placeholder="型号、品牌"
            />
          </Form.Item>
        </FilterField>
        <FilterField label="仓库">
          <Form.Item name="warehouse" noStyle>
            <Select
              aria-label="仓库"
              options={warehouseFilterOptions(warehouses)}
            />
          </Form.Item>
        </FilterField>
      </FilterCard>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <MainCard
          settings={columnsSet.settings}
          count={total}
          loading={loading}
          quick={
            <QuickFilters
              options={[
                {
                  value: 'available',
                  label: '只看可用',
                  count: summary?.available
                    ? `${summary.available} 件`
                    : undefined,
                },
                {
                  value: 'defective',
                  label: '只看有不良',
                  count: summary?.defective
                    ? `${summary.defective} 件`
                    : undefined,
                },
              ]}
              value={filters.quick ? [filters.quick] : []}
              onChange={(v) => {
                // 两个条件互斥，取刚点的那个
                const next = v.find((x) => x !== filters.quick) as
                  | 'available'
                  | 'defective'
                  | undefined;
                quick(next ?? (filters.quick as 'available' | 'defective'));
              }}
            />
          }
        >
          <Table<StockRow>
            rowKey={rowKey}
            loading={loading}
            columns={columnsSet.columns}
            dataSource={rows}
            scroll={{ x: tableScrollX(columnsSet.columns) }}
            expandable={{
              expandedRowKeys: expanded,
              onExpandedRowsChange: (keys) => setExpanded([...keys]),
              expandedRowRender: (r) => (
                <BatchRows
                  row={r}
                  version={version}
                  onOutbound={setOutbound}
                  onReturn={setToSupplier}
                />
              ),
            }}
            locale={{
              emptyText: tableEmpty(
                filters.keyword || filters.quick || filters.warehouse !== 'all'
                  ? '没有符合条件的库存'
                  : '还没有库存；采购入库、退货入库后会出现在这里',
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
      )}
      <OtherOutboundModal
        open={!!outbound}
        target={outbound}
        onClose={() => setOutbound(undefined)}
        onDone={() => {
          setOutbound(undefined);
          load();
        }}
      />
      <ReturnToSupplierModal
        target={toSupplier}
        onClose={() => setToSupplier(undefined)}
        onDone={() => {
          setToSupplier(undefined);
          load();
        }}
      />
    </div>
  );
};

export default StocksPage;
