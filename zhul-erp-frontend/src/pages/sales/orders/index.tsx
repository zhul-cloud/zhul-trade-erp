import { history, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  DatePicker,
  Form,
  Input,
  Popover,
  Select,
  Table,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { CopyPlus, Eye, Plus, Search, TriangleAlert } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { useColumnSettings } from '@/components/ColumnSettings';
import { CopyText } from '@/components/CopyText';
import { CustomerCell } from '@/components/DocFields';
import SourceNos from '@/components/DocFields/SourceNos';
import EllipsisText, { ellipsisRender } from '@/components/EllipsisText';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  HeadHelp,
  MainCard,
  PageHeader,
  QuickFilters,
  SummaryBar,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import {
  ModalTitle,
  PagedModelsModal,
  TagList,
} from '@/pages/inquiry/customer-inquiry/ModelCells';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { CURRENCIES } from '@/pages/quotation/components';
import { getUserOptions } from '@/services/zhul/users';
import { useAppTheme } from '@/theme/AppTheme';
import { useDeltaColor } from '@/theme/delta';
import { MONO_FONT } from '@/theme/fonts';
import { useBusinessUnits } from '@/utils/businessUnit';
import { recentRange } from '@/utils/dateRange';
import { formatAmount } from '@/utils/format';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import {
  MigratedPill,
  orderSourceRefs,
  PATHS,
  Pill,
  RECEIPT_META,
} from '../components';
import {
  allOrderModels,
  type OrderListItem,
  type OrderQuery,
  type OrderStats,
  orderApi,
  type ReorderDraft,
  readBizError,
} from '../service';
import {
  CreateOrderDrawer,
  NewOrderDrawer,
  ReorderPickModal,
  STOCK,
  useUserOptions,
} from './dialogs';
import {
  GoodsBar,
  GoodsLegend,
  ProgressPill,
  ReceiptProgress,
  useGoodsColors,
} from './parts';

interface Filters {
  keyword?: string;
  range?: [Dayjs, Dayjs] | null;
  progressCode?: string;
  stockType?: number;
  customerType?: number;
  currencyCode?: string;
  purchaserId?: number;
  ownerId?: number;
  /** 所属事业部 */
  businessUnitId?: number;
  receiptStatus?: number;
  /** 1-PI 转成、2-手动创建、3-售后补发、4-费用订单 */
  source?: number;
  /** 只看毛利为负（快捷筛选；经营看板「毛利为负的订单」带过来） */
  lossOnly?: boolean;
}

/** 收款状态筛选里的「先采购后收款未收款」 */
const PAY_LATER_OPEN = -1;

/** 快捷筛选「毛利为负」 */
const QUICK_LOSS = 'loss';

/** 收在「更多筛选」里的条件 */
const MORE_KEYS = [
  'source',
  'stockType',
  'customerType',
  'currencyCode',
  'ownerId',
  'businessUnitId',
  'purchaserId',
] as const;

/** 帮助文字：help-texts.md「销售订单列表」，关键词范围以 search-scope.md 为准 */
export const HELP = {
  keyword:
    '可搜：订单编号、PI 编号（不带前缀也能搜到）、客户名称或简称、型号。包含匹配，整段文字一起匹配',
  salesDate: '默认最近 3 个月，清空后看全部',
  profit: '毛利 = 收入（毛额 × 结汇汇率）− 实际成本',
  receipt: '收款进度 = 已到账（毛额）÷ 合计',
  progress: '订单状态取所有型号中最靠前的进度',
  inProgress: '未完成的有效订单；费用订单只计入合计，不分阶段',
  monthCount: '按销售日期统计本月的有效订单，不含售后补发',
  unassigned: '进行中的订单里，有型号还没指定采购员的订单数',
} as const;

/**
 * 默认最近 3 个月（清空日期看全部）；从销售统计跳转时用 URL 带的销售日期、业务员、事业部。
 * 从工作台待办进来：订单状态（progressCode）不限日期；毛利为负（loss=1）带本月销售日期，与待办数字同口径。
 */
export const initial = (): Filters => {
  const p = new URLSearchParams(window.location.search);
  const from = p.get('dateFrom');
  const to = p.get('dateTo');
  const owner = Number(p.get('ownerId'));
  const unit = Number(p.get('businessUnitId'));
  const progressCode = p.get('progressCode') || undefined;
  const lossOnly = p.get('loss') === '1' || undefined;
  return {
    range:
      from && to
        ? [dayjs(from), dayjs(to)]
        : progressCode || lossOnly
          ? null
          : recentRange(),
    ownerId: owner > 0 ? owner : undefined,
    businessUnitId: unit > 0 ? unit : undefined,
    progressCode,
    lossOnly,
  };
};

const OrderList: React.FC = () => {
  const { palette } = useAppTheme();
  const { of: deltaOf } = useDeltaColor();
  const goodsColors = useGoodsColors();
  const { message } = App.useApp();
  const access = useAccess();
  const [form] = Form.useForm<Filters>();
  const [filters, setFilters] = useState<Filters>(initial);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [sort, setSort] = useState<{
    field?: string;
    order?: 'ascend' | 'descend';
  }>({});
  const [rows, setRows] = useState<OrderListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [stats, setStats] = useState<OrderStats>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [createOpen, setCreateOpen] = useState(false);
  /** 「再下一单」的原订单；为空时是普通的手动建单 */
  // 再下一单：先选型号（reorderPick 为原订单 ID），选好后带着草稿打开手动建单
  const [reorderPick, setReorderPick] = useState<number>();
  const [reorder, setReorder] = useState<ReorderDraft>();
  const [newOpen, setNewOpen] = useState(false);
  const [viewing, setViewing] = useState<OrderListItem>();
  const canFromPi = !!access.salesPi;
  const canManual = !!access['sales:order:create'];
  const users = useUserOptions();
  const [bu] = useBusinessUnits();
  const [owners, setOwners] = useState<{ value: number; label: string }[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    const q: OrderQuery = {
      keyword: filters.keyword?.trim() || undefined,
      salesFrom: filters.range?.[0]?.format('YYYY-MM-DD'),
      salesTo: filters.range?.[1]?.format('YYYY-MM-DD'),
      progressCode: filters.progressCode,
      stockType: filters.stockType,
      customerType: filters.customerType,
      currencyCode: filters.currencyCode,
      purchaserId: filters.purchaserId,
      ownerId: filters.ownerId,
      businessUnitId: filters.businessUnitId,
      receiptStatus:
        filters.receiptStatus === PAY_LATER_OPEN
          ? undefined
          : filters.receiptStatus,
      payLaterOpen: filters.receiptStatus === PAY_LATER_OPEN || undefined,
      lossOnly: filters.lossOnly || undefined,
      source: filters.source,
      sortField: sort.order ? sort.field : undefined,
      sortOrder: sort.order ?? undefined,
      page,
      pageSize,
    };
    try {
      const res = await orderApi.page(q);
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
    orderApi
      .stats()
      .then(setStats)
      .catch(() => setStats(undefined));
    getUserOptions()
      .then((res) =>
        setOwners(res.map((u) => ({ value: u.id, label: u.name }))),
      )
      .catch(() => setOwners([]));
  }, []);

  const noFilter =
    !filters.keyword &&
    !filters.progressCode &&
    !filters.stockType &&
    !filters.customerType &&
    !filters.currencyCode &&
    !filters.purchaserId &&
    !filters.ownerId &&
    !filters.businessUnitId &&
    !filters.receiptStatus &&
    !filters.source &&
    !filters.lossOnly &&
    !filters.range;

  const sub = (text: React.ReactNode, color: string = palette.mute) => (
    <div style={{ fontSize: 12, color }}>{text}</div>
  );

  const columns: TableColumnsType<OrderListItem> = [
    {
      title: '订单编号 · 销售日期',
      dataIndex: 'soNo',
      width: 170,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <CopyText text={v}>
            <button
              type="button"
              className="zhul-link"
              onClick={() => history.push(PATHS.order(r.id))}
            >
              {v}
            </button>
          </CopyText>
          {r.migrated && (
            <div style={{ marginTop: 4 }}>
              <MigratedPill migrated />
            </div>
          )}
          {r.source === 3 && (
            <div style={{ marginTop: 4 }}>
              <Pill tone="gray">售后补发</Pill>
            </div>
          )}
          {r.source === 4 && (
            <div style={{ marginTop: 4 }}>
              <Pill tone="orange">费用订单</Pill>
            </div>
          )}
          {/* 现货 / 期货是属性，并进日期这一行，不再单占一列（D40，列顺序方案 B） */}
          {sub(
            [
              r.salesDate ?? '—',
              r.stockType != null &&
                (r.stockType === STOCK.FUTURES ? '期货' : '现货'),
            ]
              .filter(Boolean)
              .join(' · '),
          )}
          {r.originalSoNo && sub(`原订单 ${r.originalSoNo}`)}
          {(r.feeOrderCount ?? 0) > 0 && (
            <div style={{ fontSize: 12, color: palette.orange }}>
              费用订单 {r.feeOrderCount}
            </div>
          )}
        </div>
      ),
    },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 190,
      render: (v: string, r) => (
        <CustomerCell
          name={v}
          country={r.customerCountry}
          contact={r.customerContact}
          type={r.customerType}
        />
      ),
    },
    {
      title: <HeadHelp title="订单状态" help={HELP.progress} />,
      dataIndex: 'progressCode',
      width: 110,
      render: (v: string, r) => (
        <div>
          <ProgressPill code={v} name={r.progressName} />
          {r.cancelReason && sub(r.cancelReason)}
        </div>
      ),
    },
    {
      title: '合计',
      dataIndex: 'totalAmount',
      width: 140,
      align: 'right',
      render: (v: number, r) => (
        <b style={{ fontVariantNumeric: 'tabular-nums' }}>
          {formatAmount(v, r.currencyCode)}
        </b>
      ),
    },
    {
      title: <HeadHelp title="收款进度" help={HELP.receipt} />,
      key: 'receipt',
      width: 150,
      render: (_, r) => (
        <div>
          <ReceiptProgress
            received={r.receivedAmount ?? 0}
            total={r.totalAmount}
            status={r.receiptStatus}
          />
          {r.payLater && (
            <span style={{ display: 'inline-block', marginTop: 4 }}>
              <Pill tone="orange">先采购后收款</Pill>
            </span>
          )}
        </div>
      ),
    },
    {
      title: '货物',
      key: 'goods',
      width: 180,
      render: (_, r) => {
        const g = r.goods;
        if (!g || g.total === 0)
          return (
            <span style={{ fontSize: 12, color: palette.mute }}>
              {r.status === 2 ? '—' : '系统外采购'}
            </span>
          );
        // 两行（2026-11-24 用户选定对比图方案 B，D21 行高）：第一行进度条 +「已出运 / 总件数」，悬停看各段件数；
        // 第二行一句提醒。提示文字前的小圆点用所指那一段的颜色；「还有型号没下采购订单」用警告色文字加图标，不用标签（标签 24 高会撑高行）
        const hint =
          g.shipped >= g.total
            ? { text: '全部出运', color: goodsColors.shipped }
            : g.shipped + g.handed >= g.total
              ? { text: '全部交货代，等出运', color: goodsColors.handed }
              : g.received >= g.total
                ? {
                    text: '货已到齐，可以发货通知',
                    color: goodsColors.inWarehouse,
                  }
                : g.inTransit > 0 && g.earliestArrival
                  ? {
                      text: `在途最早 ${dayjs(g.earliestArrival).format('MM-DD')} 到`,
                      color: goodsColors.inTransit,
                    }
                  : undefined;
        return (
          <div style={{ display: 'grid', justifyItems: 'start' }}>
            <Popover content={<GoodsLegend parts={g} total={g.total} />}>
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 8,
                  height: 22,
                  fontSize: 12,
                  color: palette.mute,
                  fontVariantNumeric: 'tabular-nums',
                  cursor: 'default',
                }}
              >
                <GoodsBar parts={g} total={g.total} width={130} />
                {g.shipped}/{g.total} 件
              </span>
            </Popover>
            {hint ? (
              // 文字用次要灰，前面的小圆点用所指那一段的颜色（图表系列色当文字对比度不足 4.5:1）
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 6,
                  fontSize: 12,
                  color: palette.sub,
                }}
              >
                <span
                  aria-hidden
                  style={{
                    width: 6,
                    height: 6,
                    flex: 'none',
                    borderRadius: 3,
                    background: hint.color,
                  }}
                />
                {hint.text}
              </span>
            ) : (
              g.pendingPurchase > 0 && (
                <span
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 4,
                    fontSize: 12,
                    fontWeight: 600,
                    color: palette.orange,
                  }}
                >
                  <TriangleAlert size={12} aria-hidden />
                  还有型号没下采购订单
                </span>
              )
            )}
          </div>
        );
      },
    },
    {
      title: '型号 · 数量',
      key: 'qty',
      width: 190,
      render: (_, r) => (
        <div>
          <EllipsisText text={r.firstModel} style={{ fontFamily: MONO_FONT }} />
          {sub(
            r.itemCount > 1 ? (
              <>
                共 {r.itemCount} 个型号 · {r.totalQuantity ?? 0} 件 ·{' '}
                <button
                  type="button"
                  className="zhul-link"
                  onClick={() => setViewing(r)}
                >
                  查看
                </button>
              </>
            ) : (
              `${r.totalQuantity ?? 0} 件`
            ),
          )}
        </div>
      ),
    },
    {
      title: '品牌',
      key: 'brands',
      width: 170,
      render: (_, r) => <TagList values={r.brands} label="品牌" />,
    },
    {
      title: '品类',
      key: 'categories',
      width: 170,
      render: (_, r) => <TagList values={r.categories} label="品类" />,
    },
    {
      title: <HeadHelp title="毛利" help={HELP.profit} />,
      key: 'profit',
      width: 150,
      align: 'right',
      sorter: true,
      // 排序提示只挂在排序图标上，不和表头的帮助提示同时弹出
      showSorterTooltip: { target: 'sorter-icon' },
      sortOrder: sort.field === 'profit' ? sort.order : null,
      render: (_, r) =>
        r.profit == null ? (
          <span style={{ color: palette.mute }}>—</span>
        ) : (
          <div>
            <b
              style={{
                fontVariantNumeric: 'tabular-nums',
                color: deltaOf(r.profit),
              }}
            >
              {formatAmount(r.profit, 'CNY')}
            </b>
            {sub(
              `${r.profitRate != null ? `${r.profitRate}% · ` : ''}${r.profitSettled ? '已结算' : '未结算'}`,
            )}
          </div>
        ),
    },
    {
      title: '采购员',
      key: 'purchasers',
      width: 140,
      render: (_, r) => (
        <div>
          {r.purchaserNames.length > 0 ? r.purchaserNames.join('、') : null}
          {r.unassignedCount > 0 && (
            <div style={{ marginTop: 4 }}>
              <Pill tone="orange">
                <TriangleAlert size={12} aria-hidden />
                {r.purchaserNames.length > 0
                  ? `${r.unassignedCount} 个型号未指定`
                  : '未指定'}
              </Pill>
            </div>
          )}
        </div>
      ),
    },
    {
      title: '来源单号',
      key: 'source',
      width: 200,
      render: (_, r) => (
        <SourceNos refs={orderSourceRefs(r)} empty="手动创建" />
      ),
    },
    {
      title: '业务员',
      dataIndex: 'ownerName',
      width: 90,
      render: ellipsisRender,
    },
    ...auditColumns<OrderListItem>(),
    {
      title: '操作',
      key: 'actions',
      width: rowActionsWidth(canManual ? 2 : 1),
      fixed: 'right',
      render: (_, r) => (
        <RowActions
          inline={[
            {
              key: 'view',
              label: '查看',
              icon: <Eye size={18} />,
              onClick: () => history.push(PATHS.order(r.id)),
            },
            // 再下一单：复制这张订单的内容新建手动订单；费用订单没有型号，不提供
            canManual &&
              r.source !== 4 && {
                key: 'reorder',
                label: '再下一单',
                icon: <CopyPlus size={18} />,
                onClick: () => setReorderPick(r.id),
              },
          ]}
        />
      ),
    },
  ];
  const cols = useColumnSettings('sales-orders', columns, {
    hidden: [
      'qty',
      'brands',
      'categories',
      'profit',
      'purchasers',
      'source',
      'ownerName',
    ],
  });

  const applyFilters = (f: Filters) => {
    form.setFieldsValue({ ...f });
    setFilters(f);
    setPage(1);
  };

  /** 按表单当前的条件查询（快捷筛选不在表单里，沿用当前选择） */
  const search = () =>
    applyFilters({ ...form.getFieldsValue(), lossOnly: filters.lossOnly });

  const moreCount = MORE_KEYS.filter(
    (k) => filters[k] != null && filters[k] !== '',
  ).length;

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

  return (
    <div>
      <PageHeader
        extra={
          (canFromPi || canManual) && (
            <Button
              type="primary"
              icon={<Plus size={18} />}
              onClick={() => setNewOpen(true)}
            >
              新建销售订单
            </Button>
          )
        }
      />

      {stats && (
        <SummaryBar
          items={[
            {
              key: 'net',
              label: '本月实收人民币',
              value: stats.monthNetCny,
              currency: 'CNY',
              sub: `线上 ${formatAmount(stats.monthOnlineCny, 'CNY')} · 线下 ${formatAmount(stats.monthOfflineCny, 'CNY')}`,
            },
            {
              key: 'progress',
              label: '进行中',
              help: HELP.inProgress,
              value: stats.inProgressCount,
              // 各阶段数量在「订单状态」下拉里也能看到，宽度不够时可以隐藏
              sub: stats.progressCounts.length
                ? stats.progressCounts
                    .map((c) => `${c.name} ${c.count}`)
                    .join(' · ')
                : undefined,
              subHideable: true,
            },
            {
              key: 'month',
              label: '本月订单',
              help: HELP.monthCount,
              value: stats.monthCount,
            },
            {
              key: 'unassigned',
              label: '未指定采购员',
              help: HELP.unassigned,
              value: stats.unassignedCount,
            },
          ]}
        />
      )}

      <Form form={form} initialValues={initial()} component={false}>
        <FilterCard
          onSearch={search}
          onReset={() => {
            form.resetFields();
            applyFilters(initial());
          }}
          moreCount={moreCount}
          // 列宽 110：1366 宽时关键词、状态、日期、收款状态四项排一行（原型 A3）
          minColumn={110}
          more={
            <>
              {select('source', '来源', [
                { value: 1, label: 'PI 生成' },
                { value: 2, label: '手动建单' },
                { value: 3, label: '售后补发' },
                { value: 4, label: '费用订单' },
              ])}
              {select('stockType', '现货 / 期货', [
                { value: STOCK.SPOT, label: '现货' },
                { value: STOCK.FUTURES, label: '期货' },
              ])}
              {select('customerType', '新 / 老客户', [
                { value: 1, label: '新客户' },
                { value: 2, label: '老客户' },
              ])}
              {select(
                'currencyCode',
                '币种',
                CURRENCIES.map((c) => ({ value: c, label: c })),
              )}
              {select('ownerId', '业务员', owners, {
                showSearch: { optionFilterProp: 'label' },
              })}
              {bu.enabled &&
                select(
                  'businessUnitId',
                  '事业部',
                  bu.units.map((u) => ({ value: u.id, label: u.name })),
                )}
              {select('purchaserId', '采购员', users, {
                showSearch: { optionFilterProp: 'label' },
              })}
            </>
          }
        >
          <FilterField label="关键词" span={2} help={HELP.keyword}>
            <Form.Item name="keyword" noStyle>
              <Input
                allowClear
                prefix={<Search size={16} />}
                placeholder="订单编号、客户、型号"
                aria-label="关键词"
                onPressEnter={search}
              />
            </Form.Item>
          </FilterField>
          {select(
            'progressCode',
            '订单状态',
            [
              ...(stats?.progressCounts ?? []).map((c) => ({
                value: c.code,
                label: c.count ? `${c.name} · ${c.count}` : c.name,
              })),
              { value: 'COMPLETED', label: '已完成' },
              { value: 'CANCELLED', label: '已取消' },
            ],
            // 切换状态立即查询（doc-list-unify 状态下拉的写法）
            { onChange: () => search(), listHeight: 320 },
          )}
          <FilterField
            label="销售日期"
            span={2}
            help={HELP.salesDate}
            htmlFor="so-sales-date"
          >
            <Form.Item name="range" noStyle>
              <DatePicker.RangePicker
                id={{ start: 'so-sales-date' }}
                style={{ width: '100%' }}
              />
            </Form.Item>
          </FilterField>
          {select('receiptStatus', '收款状态', [
            ...Object.entries(RECEIPT_META).map(([k, v]) => ({
              value: Number(k),
              label: v.label,
            })),
            { value: PAY_LATER_OPEN, label: '先采购后收款未收款' },
          ])}
        </FilterCard>
      </Form>

      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : !loading && rows.length === 0 && noFilter ? (
        <MainCard>
          <EmptyHint
            title="还没有销售订单"
            description="客户付款后按 PI 创建订单（也可以在 PI 上点「转成订单」），或手动创建订单"
            actionText={canFromPi || canManual ? '新建销售订单' : undefined}
            onAction={() => setNewOpen(true)}
          />
        </MainCard>
      ) : (
        <MainCard
          count={total}
          loading={loading}
          settings={cols.settings}
          quick={
            <QuickFilters
              options={[{ value: QUICK_LOSS, label: '毛利为负' }]}
              value={filters.lossOnly ? [QUICK_LOSS] : []}
              onChange={(v) => {
                setFilters((f) => ({
                  ...f,
                  lossOnly: v.includes(QUICK_LOSS) || undefined,
                }));
                setPage(1);
              }}
            />
          }
        >
          <Table<OrderListItem>
            rowKey="id"
            columns={cols.columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: tableScrollX(cols.columns) }}
            locale={{
              emptyText: tableEmpty(
                '没有符合条件的订单，换个筛选条件试试',
                loading,
              ),
            }}
            onChange={(_, __, sorter, extra) => {
              // 翻页也会触发 onChange：只处理排序，否则会把页码重置回 1
              if (extra.action !== 'sort') return;
              const s = Array.isArray(sorter) ? sorter[0] : sorter;
              setSort({
                field: s?.columnKey as string | undefined,
                order: s?.order ?? undefined,
              });
              setPage(1);
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
      <PagedModelsModal
        openId={viewing?.id}
        showPrice
        title={
          <ModalTitle
            count={viewing?.itemCount}
            sub={`${viewing?.soNo ?? ''} · ${viewing?.customerName ?? ''}`}
          />
        }
        note="按订单明细的顺序"
        fetchPage={(p, s) => orderApi.models(viewing?.id as number, p, s)}
        fetchAll={() => allOrderModels(viewing?.id as number)}
        onOpenDetail={() => viewing && history.push(PATHS.order(viewing.id))}
        onClose={() => setViewing(undefined)}
      />
      <NewOrderDrawer
        open={newOpen}
        canFromPi={canFromPi}
        canManual={canManual}
        onClose={() => setNewOpen(false)}
        onManual={() => {
          setNewOpen(false);
          setReorder(undefined);
          setCreateOpen(true);
        }}
        onDone={(order) => {
          setNewOpen(false);
          message.success(`已生成销售订单 ${order.soNo}`);
          history.push(PATHS.order(order.id));
        }}
      />
      <ReorderPickModal
        soId={reorderPick}
        onCancel={() => setReorderPick(undefined)}
        onNext={(draft) => {
          setReorderPick(undefined);
          setReorder(draft);
          setCreateOpen(true);
        }}
      />
      <CreateOrderDrawer
        open={createOpen}
        reorder={reorder}
        onClose={() => setCreateOpen(false)}
        onDone={(order) => {
          setCreateOpen(false);
          history.push(PATHS.order(order.id));
        }}
      />
    </div>
  );
};

export default OrderList;
