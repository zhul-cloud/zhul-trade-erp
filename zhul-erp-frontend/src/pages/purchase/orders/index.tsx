import { history } from '@umijs/max';
import type { TableColumnsType, TableProps } from 'antd';
import { DatePicker, Form, Input, Select, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Search } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import SourceNos from '@/components/DocFields/SourceNos';
import EllipsisText, { ellipsisRender } from '@/components/EllipsisText';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
  QuickFilters,
  SummaryBar,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { PayStatusPill } from '@/pages/finance/payables/components';
import { PAY_STATUS } from '@/pages/finance/payables/service';
import { TagList } from '@/pages/inquiry/customer-inquiry/ModelCells';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { StockPill, useUserOptions } from '@/pages/sales/orders/dialogs';
import { useAppTheme } from '@/theme/AppTheme';
import { recentRange } from '@/utils/dateRange';
import { formatAmount } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import {
  auditColumns,
  BargainText,
  MigratedPill,
  PATHS,
  Pill,
  PO_STATUS,
  PoStatusPill,
  SHIP_PROGRESS,
  ShipProgressPill,
  SourceCell,
  SupplierPicker,
  sub,
} from '../components';
import {
  type PoListItem,
  type PoQuery,
  type PoStats,
  poApi,
  readBizError,
} from '../service';

interface Filters {
  keyword?: string;
  status?: number;
  shipProgress?: string;
  payStatus?: string;
  invoiceStatus?: string;
  supplierId?: number;
  purchaserId?: number;
  stockType?: number;
  range?: [Dayjs, Dayjs] | null;
}

type Sort = { field?: string; order?: 'ascend' | 'descend' };

/** 帮助文字：help-texts.md「采购订单」，口径取自 PurchaseOrderServiceImpl.stats() */
export const HELP = {
  page: '向供应商下的单：记录实际采购价、开票、付款条件与供应商合同；砍价 = 目标价 − 人民币采购价，开票成本不计入砍价',
  keyword: '可搜：采购订单编号、店铺名称、供应商名称或简称、销售订单编号、型号',
  orderDate: '默认最近 3 个月；草稿还没有下单日期，按创建时间算',
  bargain: '本月已下单的采购订单砍价合计；砍价率按目标金额加权',
  pendingShip: '已下单、供应商还有没发的（未发货和部分发货）',
  staleDrafts: '放了 3 天以上还没确认下单的草稿',
  orderCancelled:
    '来源销售订单已取消、采购订单已下单的；请决定取消采购订单或保留货物',
} as const;

/** 快捷筛选：逾期未发 */
const QUICK_OVERDUE = 'overdue';

/** 下拉选项后面带数量（stat-cards.md：状态并入下拉） */
const withCount = (label: string, n?: number) =>
  n ? `${label} · ${n}` : label;

const PurchaseOrderList: React.FC = () => {
  const { palette } = useAppTheme();
  const users = useUserOptions();
  const [form] = Form.useForm<Filters>();
  // 下单日期默认最近 3 个月，清空日期看全部
  const [defaults] = useState<Filters>(() => ({ range: recentRange() }));
  const [filters, setFilters] = useState<Filters>(defaults);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [sort, setSort] = useState<Sort>({});
  const [rows, setRows] = useState<PoListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [stats, setStats] = useState<PoStats>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    const q: PoQuery = {
      keyword: filters.keyword?.trim() || undefined,
      status: filters.status,
      shipProgress: filters.shipProgress,
      payStatus: filters.payStatus,
      invoiceStatus: filters.invoiceStatus,
      supplierId: filters.supplierId,
      purchaserId: filters.purchaserId,
      stockType: filters.stockType,
      orderFrom: filters.range?.[0]?.format('YYYY-MM-DD'),
      orderTo: filters.range?.[1]?.format('YYYY-MM-DD'),
      sortField: sort.order ? sort.field : undefined,
      sortOrder: sort.order,
      page,
      pageSize,
    };
    try {
      const res = await poApi.page(q);
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [filters, page, pageSize, sort]);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  useEffect(() => {
    poApi
      .stats()
      .then(setStats)
      .catch(() => setStats(undefined));
  }, []);

  const sortOf = (field: string) => (sort.field === field ? sort.order : null);
  const noFilter =
    !filters.keyword &&
    !filters.status &&
    !filters.shipProgress &&
    !filters.payStatus &&
    !filters.invoiceStatus &&
    !filters.supplierId &&
    !filters.purchaserId &&
    !filters.stockType &&
    !filters.range;

  const columns: TableColumnsType<PoListItem> = [
    {
      title: '采购订单 · 下单日期',
      dataIndex: 'poNo',
      width: 190,
      fixed: 'left',
      render: (v: string | undefined, r) => (
        <div>
          <button
            type="button"
            className="zhul-link"
            onClick={() => history.push(PATHS.order(r.id))}
          >
            {v ?? '草稿'}
          </button>
          {r.migrated && (
            <div style={{ marginTop: 4 }}>
              <MigratedPill migrated />
            </div>
          )}
          {r.status === PO_STATUS.DRAFT
            ? sub(
                r.staleDays ? palette.orange : palette.mute,
                `${dayjs(r.createTime).format('MM-DD')} 创建${r.staleDays ? ` · 已放 ${r.staleDays} 天` : ''}`,
              )
            : sub(palette.mute, r.orderDate ?? '—')}
        </div>
      ),
    },
    {
      title: '现货 / 期货',
      dataIndex: 'stockType',
      width: 100,
      render: (v?: number) => <StockPill type={v} />,
    },
    {
      title: '采购对象',
      dataIndex: 'supplierName',
      width: 200,
      render: (v: string | undefined, r) =>
        r.shop ? (
          <SourceCell shopName={r.shopName} channelName={v?.split(' · ')[0]} />
        ) : (
          <SourceCell supplierName={v} />
        ),
    },
    {
      // 型号、品牌、品类按单据页统一规范显示（audit UI-098）
      title: '型号 · 数量',
      dataIndex: 'totalQuantity',
      width: 190,
      sorter: true,
      sortOrder: sortOf('totalQuantity'),
      render: (v: number, r) => (
        <div>
          <EllipsisText
            text={r.firstModel ?? '—'}
            style={{ fontFamily: 'monospace' }}
          />
          {sub(
            palette.mute,
            r.itemCount > 1 ? `共 ${r.itemCount} 个型号 · ${v} 件` : `${v} 件`,
          )}
        </div>
      ),
    },
    {
      title: '品牌',
      key: 'brands',
      width: 160,
      render: (_, r) => <TagList values={r.brands ?? []} label="品牌" />,
    },
    {
      title: '品类',
      key: 'categories',
      width: 160,
      render: (_, r) => <TagList values={r.categories ?? []} label="品类" />,
    },
    {
      title: '合计',
      dataIndex: 'totalAmount',
      width: 150,
      align: 'right',
      sorter: true,
      sortOrder: sortOf('totalAmount'),
      render: (v: number, r) =>
        r.missingPriceCount > 0 ? (
          <span style={{ color: palette.orange, fontSize: 12 }}>
            {r.missingPriceCount} 行未填单价
          </span>
        ) : (
          <b style={{ fontVariantNumeric: 'tabular-nums' }}>
            {formatAmount(v, r.currencyCode)}
          </b>
        ),
    },
    {
      title: '砍价',
      dataIndex: 'bargainAmount',
      width: 130,
      sorter: true,
      sortOrder: sortOf('bargainAmount'),
      render: (v: number | null | undefined, r) =>
        r.missingPriceCount > 0 && r.status === PO_STATUS.DRAFT ? (
          <span style={{ color: palette.mute }}>—</span>
        ) : (
          <BargainText amount={v} rate={r.bargainRate} />
        ),
    },
    {
      title: '付款条件',
      dataIndex: 'paymentTermsText',
      width: 170,
      render: (v?: string) =>
        v || <span style={{ color: palette.mute }}>未填</span>,
    },
    {
      title: '付款',
      key: 'pay',
      width: 170,
      render: (_, r) =>
        r.payStatus === undefined ? (
          <span style={{ color: palette.mute }}>—</span>
        ) : (
          <div>
            <PayStatusPill status={r.payStatus} />
            {r.payStatus === PAY_STATUS.PARTIAL && (
              <div style={{ fontSize: 12, color: palette.sub }}>
                {formatAmount(r.paidAmount, r.currencyCode)} /{' '}
                {formatAmount(r.payableAmount, r.currencyCode)}
              </div>
            )}
            {!!r.creditAmount && r.creditAmount > 0 && (
              <div style={{ fontSize: 12, color: palette.orange }}>
                欠款 {formatAmount(r.creditAmount, r.currencyCode)}
              </div>
            )}
          </div>
        ),
    },
    {
      title: '开票',
      key: 'invoice',
      width: 150,
      render: (_, r) =>
        !r.invoiceStatus ? (
          <span style={{ color: palette.mute }}>不开票</span>
        ) : (
          <div>
            {r.invoiceOverdueDays ? (
              <Pill tone="red">开票逾期 {r.invoiceOverdueDays} 天</Pill>
            ) : r.invoiceStatus === 2 ? (
              <Pill tone="green">已开票</Pill>
            ) : (
              <Pill tone="orange">待开票</Pill>
            )}
            <div style={{ fontSize: 12, color: palette.sub }}>
              {formatAmount(r.invoiceAmount, r.currencyCode)} ·{' '}
              {Number(r.taxPoint ?? 0)}%
            </div>
          </div>
        ),
    },
    {
      title: '预计发货日期',
      dataIndex: 'expectedShipDate',
      width: 130,
      render: (v: string | null | undefined, r) =>
        v ? (
          <div>
            <span style={{ color: r.overdueDays ? palette.orange : undefined }}>
              {v}
            </span>
            {r.overdueDays ? (
              <div>
                <Pill tone="orange">已过 {r.overdueDays} 天</Pill>
              </div>
            ) : null}
          </div>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '来源单号',
      key: 'orders',
      width: 180,
      render: (_, r) => (
        <div>
          <SourceNos
            refs={r.orders.map((o) => ({
              no: o.soNo,
              href: PATHS.salesOrder(o.id),
            }))}
          />
          {r.orderCancelledCount > 0 && <Pill tone="red">来源订单已取消</Pill>}
        </div>
      ),
    },
    {
      title: '采购员',
      dataIndex: 'purchaserName',
      width: 90,
      render: ellipsisRender,
    },
    {
      title: '状态 · 发货进度',
      dataIndex: 'status',
      width: 190,
      render: (v: number, r) => (
        <div>
          <span style={{ display: 'inline-flex', gap: 6 }}>
            <PoStatusPill status={v} />
            <ShipProgressPill code={r.shipProgress} name={r.shipProgressName} />
          </span>
          {r.cancelReason && sub(palette.mute, r.cancelReason)}
          {r.shipProgress === 'SHIPPED' &&
            sub(
              palette.mute,
              r.earliestArrival
                ? `在途，预计 ${dayjs(r.earliestArrival).format('MM-DD')} 到`
                : '在途',
            )}
          {(r.shipProgress === 'UNSHIPPED' || r.shipProgress === 'PARTIAL') &&
            sub(
              palette.mute,
              `已发 ${r.shippedQty ?? 0} / ${r.totalQty ?? 0} 件`,
            )}
        </div>
      ),
    },
    ...auditColumns<PoListItem>(),
    {
      title: '操作',
      key: 'actions',
      width: 70,
      fixed: 'right',
      render: (_, r) => (
        <button
          type="button"
          className="zhul-link"
          onClick={() => history.push(PATHS.order(r.id))}
        >
          {r.status === PO_STATUS.DRAFT ? '编辑' : '查看'}
        </button>
      ),
    },
  ];

  const onTableChange: TableProps<PoListItem>['onChange'] = (
    _p,
    _f,
    sorter,
    extra,
  ) => {
    if (extra.action !== 'sort') return;
    const s = Array.isArray(sorter) ? sorter[0] : sorter;
    setSort({
      field: s?.field as string | undefined,
      order: (s?.order ?? undefined) as Sort['order'],
    });
    setPage(1);
  };

  const applyFilters = (f: Filters) => {
    form.setFieldsValue({ ...f });
    setFilters(f);
    setPage(1);
  };

  /** 搜索条件里的下拉：标签在上，读屏名称与标签相同 */
  const select = (
    name: keyof Filters,
    label: string,
    options: { value: string | number; label: string }[],
    extra?: React.ComponentProps<typeof Select>,
  ) => (
    <FilterField label={label}>
      <Form.Item name={name} noStyle>
        <Select
          allowClear
          placeholder="全部"
          aria-label={label}
          options={options}
          {...extra}
        />
      </Form.Item>
    </FilterField>
  );
  const search = () => applyFilters(form.getFieldsValue());
  const moreCount = [
    filters.payStatus,
    filters.invoiceStatus,
    filters.supplierId,
    filters.purchaserId,
    filters.stockType,
  ].filter((v) => v != null && v !== '').length;

  return (
    <div>
      <PageHeader help={HELP.page} />
      {stats && (
        <SummaryBar
          items={[
            {
              key: 'ordered',
              label: '本月已下单',
              value: stats.monthOrderedCny,
              currency: 'CNY',
              sub: `${stats.monthOrderedCount} 张`,
            },
            {
              key: 'bargain',
              label: '本月砍价合计',
              help: HELP.bargain,
              value: stats.monthBargain,
              currency: 'CNY',
              sub:
                stats.monthBargainRate == null
                  ? undefined
                  : `砍价率 ${stats.monthBargainRate.toFixed(2)}%`,
            },
            {
              key: 'pending',
              label: '待发货',
              help: HELP.pendingShip,
              value: `${stats.pendingShip} 张`,
            },
            {
              key: 'stale',
              label: '久放草稿',
              help: HELP.staleDrafts,
              value: stats.staleDrafts,
            },
            {
              key: 'cancelled',
              label: '来源订单已取消',
              help: HELP.orderCancelled,
              value: stats.orderCancelled,
            },
          ]}
        />
      )}

      <Form form={form} initialValues={defaults} component={false}>
        <FilterCard
          onSearch={search}
          onReset={() => {
            form.resetFields();
            applyFilters(defaults);
          }}
          minColumn={130}
          moreCount={moreCount}
          more={
            <>
              {select('payStatus', '付款', [
                { value: 'UNPAID', label: '未付' },
                { value: 'PARTIAL', label: '部分付款' },
                { value: 'PAID', label: '已付清' },
                { value: 'CREDIT', label: '有欠款' },
              ])}
              {select('invoiceStatus', '开票', [
                { value: 'NONE', label: '不开票' },
                { value: 'PENDING', label: '待开票' },
                { value: 'DONE', label: '已开票' },
                { value: 'OVERDUE', label: '开票逾期' },
              ])}
              <FilterField label="供应商">
                <Form.Item name="supplierId" noStyle>
                  <SupplierPicker
                    placeholder="全部"
                    onChange={(v) => form.setFieldValue('supplierId', v)}
                  />
                </Form.Item>
              </FilterField>
              {select('purchaserId', '采购员', users, {
                showSearch: { optionFilterProp: 'label' },
              })}
              {select('stockType', '现货 / 期货', [
                { value: 1, label: '现货' },
                { value: 2, label: '期货' },
              ])}
            </>
          }
          quick={
            <QuickFilters
              options={[
                {
                  value: QUICK_OVERDUE,
                  label: withCount('逾期未发', stats?.overdueShip),
                },
              ]}
              value={filters.shipProgress === 'OVERDUE' ? [QUICK_OVERDUE] : []}
              onChange={(v) => {
                if (v.includes(QUICK_OVERDUE)) {
                  // 同原统计卡：筛逾期未发时只按这一个条件（统计不分日期），方便催货；
                  // 其他条件在搜索条件卡片里一并清空，显示的和实际查询一致
                  applyFilters({
                    keyword: undefined,
                    status: undefined,
                    payStatus: undefined,
                    invoiceStatus: undefined,
                    supplierId: undefined,
                    purchaserId: undefined,
                    stockType: undefined,
                    range: null,
                    shipProgress: 'OVERDUE',
                  });
                } else {
                  // 取消后回到默认视图（最近 3 个月）
                  form.resetFields();
                  applyFilters(defaults);
                }
              }}
            />
          }
        >
          <FilterField label="关键词" span={2} help={HELP.keyword}>
            <Form.Item name="keyword" noStyle>
              <Input
                allowClear
                prefix={<Search size={16} />}
                placeholder="采购订单编号、供应商或店铺、型号"
                aria-label="关键词"
                onPressEnter={search}
              />
            </Form.Item>
          </FilterField>
          {select(
            'status',
            '状态',
            [
              {
                value: PO_STATUS.DRAFT,
                label: withCount('草稿', stats?.drafts),
              },
              { value: PO_STATUS.ORDERED, label: '已下单' },
              { value: PO_STATUS.CANCELLED, label: '已取消' },
            ],
            // 切换状态立即查询（doc-list-unify 状态下拉的写法）
            { onChange: () => search() },
          )}
          <FilterField
            label="下单日期"
            span={2}
            help={HELP.orderDate}
            htmlFor="po-order-date"
          >
            <Form.Item name="range" noStyle>
              <DatePicker.RangePicker
                id={{ start: 'po-order-date' }}
                style={{ width: '100%' }}
              />
            </Form.Item>
          </FilterField>
          {select('shipProgress', '发货进度', SHIP_PROGRESS)}
        </FilterCard>
      </Form>

      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : !loading && rows.length === 0 && noFilter ? (
        <MainCard>
          <EmptyHint
            title="还没有采购订单"
            description="销售订单生成后会自动排入草稿采购订单，也可以在采购需求里勾选生成"
            actionText="去采购需求"
            onAction={() => history.push(PATHS.requirements)}
          />
        </MainCard>
      ) : (
        <MainCard left={`共 ${total} 条`}>
          <Table<PoListItem>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: tableScrollX(columns) }}
            onChange={onTableChange}
            locale={{ emptyText: '没有符合条件的采购订单，换个筛选条件试试' }}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
              onChange: (p, s) => {
                setPage(p);
                setPageSize(s);
              },
            }}
          />
        </MainCard>
      )}
    </div>
  );
};

export default PurchaseOrderList;
