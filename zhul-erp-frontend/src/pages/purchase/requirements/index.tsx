import { history, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  Alert,
  App,
  Button,
  Input,
  InputNumber,
  Modal,
  Pagination,
  Popover,
  Radio,
  Segmented,
  Select,
  Skeleton,
  Table,
} from 'antd';
import {
  ChevronDown,
  ChevronRight,
  Clock,
  FileText,
  Search,
  ShoppingCart,
  Split,
  Store,
  Trash2,
  UserRoundPlus,
} from 'lucide-react';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useColumnSettings } from '@/components/ColumnSettings';
import { CopyText } from '@/components/CopyText';
import { CountryText } from '@/components/CountryText';
import { CustomerCell } from '@/components/DocFields';
import { DescRemarkCell } from '@/components/DocFields/ItemRemark';
import EllipsisText, { ellipsisRender } from '@/components/EllipsisText';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
  Panel,
  QuickFilters,
  type Scope,
  ScopeSwitch,
  SummaryBar,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { rowAlert } from '@/components/RowAlert';
import { CustomerTypePill } from '@/pages/inquiry/shared/components';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { StockPill, useUserOptions } from '@/pages/sales/orders/dialogs';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import {
  auditColumns,
  CounterpartyPicker,
  counterpartyReady,
  counterpartyTitle,
  PATHS,
  Pill,
  ReqStatusPill,
  SourceCell,
  sub,
} from '../components';
import {
  type CounterpartyValue,
  type GenerateGroup,
  type Requirement,
  type RequirementOrder,
  type RequirementQuery,
  type RequirementStats,
  readBizError,
  requirementApi,
} from '../service';

type View = 'order' | 'model';

interface Filters {
  keyword?: string;
  status: 'open' | 'all';
  sourceType?: 'supplier' | 'shop' | 'none';
  stockType?: number;
  purchaserId?: number;
  /** 只看超时未下单（点顶部统计卡切换） */
  overdue?: boolean;
}

/** 超时多久：不到 1 小时为「刚超时」，满 2 天按天 */
const overdueText = (h: number) =>
  h < 1
    ? '刚超时'
    : h < 48
      ? `超时 ${h} 小时`
      : `超时 ${Math.floor(h / 24)} 天`;

/** 帮助文字：help-texts.md「采购需求」，口径取自 PurchaseRequirementServiceImpl.stats() */
export const HELP = {
  page: '渠道已定的型号会自动排进草稿采购订单；点「去下单」打开对应的草稿，填价、确认下单',
  keyword: '可搜：销售订单编号、客户名称或简称、型号、描述',
  source: '「还没定」：没有回价，要选老供应商或填平台和店铺',
  inDraft: '还有没下单的型号里，已排进草稿采购订单的个数',
  unassigned:
    '还没排进采购订单、也没指定采购员的型号；指派后会出现在采购员的列表里',
  drafts: '谈好价后去草稿里填单价、确认下单；点数字打开采购订单列表',
} as const;

/** 快捷筛选：超时未下单 */
const QUICK_OVERDUE = 'overdue';

/** 下拉选项后面带数量（stat-cards.md：状态并入下拉） */
const withCount = (label: string, n?: number) =>
  n ? `${label} · ${n}` : label;

const OPEN = ['pending', 'draft', 'partial'];
const isOpen = (r: Requirement) =>
  OPEN.includes(r.status) && r.orderedQty < r.quantity;

const RequirementList: React.FC = () => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const access = useAccess();
  const users = useUserOptions();
  const canSplit = !!access['purchase:requirement:split'];
  const canRemove = !!access['purchase:requirement:delete'];
  const canAssign = !!access['purchase:requirement:assign'];
  const canCreate = !!access['purchase:order:create'];
  // 采购负责人（有指派权限）默认看全部，采购员默认看自己负责的
  const [view, setView] = useState<View>('order');
  const [scope, setScope] = useState<Scope>(canAssign ? 'all' : 'mine');
  const [filters, setFilters] = useState<Filters>({ status: 'open' });
  const [draftFilters, setDraftFilters] = useState<Filters>({ status: 'open' });
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [orders, setOrders] = useState<RequirementOrder[]>([]);
  const [rows, setRows] = useState<Requirement[]>([]);
  const [total, setTotal] = useState(0);
  const [stats, setStats] = useState<RequirementStats>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [collapsed, setCollapsed] = useState<Set<number>>(new Set());
  const [selected, setSelected] = useState<Map<number, Requirement>>(new Map());
  const [splitting, setSplitting] = useState<Requirement>();
  const [removing, setRemoving] = useState<Requirement>();
  const [assigning, setAssigning] = useState<Requirement[]>();
  const [sourcing, setSourcing] = useState<Requirement[]>();
  const [generating, setGenerating] = useState<Requirement[]>();

  const query = useCallback(
    (): RequirementQuery => ({
      keyword: filters.keyword?.trim() || undefined,
      view: filters.status,
      sourceType: filters.sourceType,
      stockType: filters.stockType,
      purchaserId: filters.purchaserId,
      overdue: filters.overdue || undefined,
      mine: scope === 'mine',
      page,
      pageSize,
    }),
    [filters, scope, page, pageSize],
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      if (view === 'order') {
        const res = await requirementApi.orders(query());
        setOrders(res.records);
        setTotal(res.total);
      } else {
        const res = await requirementApi.page(query());
        setRows(res.records);
        setTotal(res.total);
      }
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [view, query]);

  const loadStats = useCallback(() => {
    requirementApi
      .stats(scope === 'mine')
      .then(setStats)
      .catch(() => setStats(undefined));
  }, [scope]);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);
  useEffect(loadStats, [loadStats]);

  const refresh = () => {
    setSelected(new Map());
    load();
    loadStats();
  };

  const reset = (patch: () => void) => {
    patch();
    setPage(1);
    setSelected(new Map());
  };

  const toggleSelect = (keys: React.Key[], scopeRows: Requirement[]) =>
    setSelected((prev) => {
      const next = new Map(prev);
      for (const r of scopeRows) next.delete(r.id);
      for (const r of scopeRows) if (keys.includes(r.id)) next.set(r.id, r);
      return next;
    });

  /** 一张采购订单：单号，或草稿的「去下单 → 供应商」 */
  const poLink = (p: Requirement['purchaseOrders'][number]) => (
    <React.Fragment key={p.id}>
      <button
        type="button"
        className={p.poNo ? 'zhul-link' : 'zhul-link zhul-link-action'}
        style={{ verticalAlign: 'top' }}
        onClick={() => history.push(PATHS.order(p.id))}
      >
        {p.poNo ?? '去下单 →'}
      </button>
      {!p.poNo && (
        <span style={{ color: palette.mute }} title={p.supplierName}>
          {' '}
          {p.supplierName}
        </span>
      )}
    </React.Fragment>
  );

  // 两行（D21）：状态标签，下面一行第一张采购订单，多张时「等 N 张」悬停看全部；删除数量接在后面；
  // 第二行单行省略，供应商名再长也不折行
  const statusCell = (r: Requirement) => {
    const [first, ...rest] = r.purchaseOrders;
    const removed = r.removedQty
      ? `删除 ${r.removedQty}${r.removeReason ? ` · ${r.removeReason}` : ''}`
      : undefined;
    const tail = [
      first && !first.poNo ? first.supplierName : undefined,
      removed,
    ]
      .filter(Boolean)
      .join(' · ');
    return (
      <div style={{ minWidth: 0 }}>
        <div style={{ display: 'flex' }}>
          <ReqStatusPill status={r.status} name={r.statusName} />
        </div>
        {(first || removed) && (
          // 单号、「等 N 张」不收缩，始终露出；只有供应商名、删除说明放不下时省略（悬停看全文）
          <div
            style={{
              display: 'flex',
              alignItems: 'baseline',
              gap: 4,
              minWidth: 0,
              fontSize: 12,
              lineHeight: '18px',
              whiteSpace: 'nowrap',
            }}
          >
            {first && (
              <button
                type="button"
                className={
                  first.poNo ? 'zhul-link' : 'zhul-link zhul-link-action'
                }
                style={{ flex: 'none' }}
                onClick={() => history.push(PATHS.order(first.id))}
              >
                {first.poNo ?? '去下单 →'}
              </button>
            )}
            {rest.length > 0 && (
              <Popover
                title={`采购订单（${r.purchaseOrders.length}）`}
                content={
                  <div style={{ display: 'grid', gap: 4, fontSize: 12 }}>
                    {r.purchaseOrders.map((p) => (
                      <div key={p.id}>{poLink(p)}</div>
                    ))}
                  </div>
                }
              >
                <span
                  style={{
                    flex: 'none',
                    color: palette.sub,
                    cursor: 'default',
                  }}
                >
                  等 {r.purchaseOrders.length} 张
                </span>
              </Popover>
            )}
            {tail && (
              <span
                title={tail}
                style={{
                  minWidth: 0,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  color: palette.mute,
                }}
              >
                {tail}
              </span>
            )}
          </div>
        )}
      </div>
    );
  };

  // 超时的行：首格左侧红色色条，原因看单号旁的「已超时」标签，不再整行铺粉底（ui-redesign D7、S5）
  const overdueRow = (r: Requirement) =>
    rowAlert(r.overdueHours != null ? 'error' : undefined);

  const lineColumns: TableColumnsType<Requirement> = [
    {
      fixed: 'left',
      title: '型号 · 品牌 · 品类',
      dataIndex: 'model',
      width: 230,
      // 两行（2026-11-24 用户选定方案 C）：型号＋超时（红色文字，不用标签），品牌 · 品类；备注挪到描述列
      render: (v: string, r) => (
        <div style={{ minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ minWidth: 0, fontWeight: 600 }}>
              <EllipsisText text={v} />
            </span>
            {r.overdueHours != null && (
              <span
                style={{
                  flex: 'none',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 3,
                  fontSize: 12,
                  fontWeight: 600,
                  color: palette.red,
                }}
              >
                <Clock size={12} aria-hidden />
                {overdueText(r.overdueHours)}
              </span>
            )}
          </div>
          <EllipsisText
            text={[r.brand, r.category].filter(Boolean).join(' · ') || '—'}
            style={{ fontSize: 12, lineHeight: '18px', color: palette.mute }}
          />
        </div>
      ),
    },
    {
      title: '描述',
      key: 'description',
      width: 240,
      render: (_, r) => (
        <DescRemarkCell description={r.description} remark={r.itemRemark} />
      ),
    },
    {
      title: '数量',
      align: 'right' as const,
      dataIndex: 'quantity',
      width: 100,
      render: (v: number, r) => (
        <div>
          <div>{v}</div>
          {sub(
            palette.mute,
            r.orderedQty > 0
              ? `已下单 ${r.orderedQty}`
              : r.draftQty > 0
                ? `草稿中 ${r.draftQty}`
                : '已下单 0',
          )}
        </div>
      ),
    },
    {
      title: '目标价',
      dataIndex: 'targetPrice',
      width: 120,
      align: 'right',
      render: (v?: number | null) =>
        v == null ? (
          <span style={{ fontSize: 12, color: palette.mute }}>没有目标价</span>
        ) : (
          <span style={{ fontVariantNumeric: 'tabular-nums' }}>
            {formatAmount(v, 'CNY')}
          </span>
        ),
    },
    {
      title: '向谁买',
      key: 'source',
      width: 200,
      render: (_, r) => (
        <SourceCell
          supplierName={r.suggestedSupplierName}
          channelName={r.suggestedChannelName}
          shopName={r.suggestedShopName}
        />
      ),
    },
    {
      title: '采购员',
      dataIndex: 'purchaserName',
      width: 90,
      render: (v?: string) =>
        v ? (
          ellipsisRender(v)
        ) : (
          <span style={{ color: palette.orange }}>未指定</span>
        ),
    },
    {
      title: '状态',
      key: 'status',
      width: 170,
      render: (_, r) => statusCell(r),
    },
    ...auditColumns<Requirement>(),
    {
      title: '操作',
      key: 'actions',
      // 选渠道、指派放行内，拆分、删除收进「更多」（ui-redesign S6：原来四个文字链接会折行）
      width: rowActionsWidth(canSplit || canRemove ? 3 : 2),
      fixed: 'right',
      render: (_, r) => {
        const open = isOpen(r);
        const planned = !!(r.suggestedSupplierName || r.suggestedShopName);
        return (
          <RowActions
            inline={[
              canCreate &&
                open && {
                  key: 'source',
                  label: planned ? '换渠道' : '选渠道',
                  // 还没定渠道的行图标用警告色，一眼看出要先选
                  icon: (
                    <Store
                      size={18}
                      color={planned ? undefined : palette.orange}
                    />
                  ),
                  onClick: () => setSourcing([r]),
                },
              canAssign &&
                open && {
                  key: 'assign',
                  label: '指派',
                  icon: <UserRoundPlus size={18} />,
                  onClick: () => setAssigning([r]),
                },
            ]}
            more={[
              canSplit &&
                open && {
                  key: 'split',
                  label: '拆分',
                  icon: <Split size={16} />,
                },
              canRemove &&
                open && {
                  key: 'remove',
                  label: '删除',
                  icon: <Trash2 size={16} />,
                  danger: true,
                },
            ]}
            onMore={(key) => {
              if (key === 'split') setSplitting(r);
              if (key === 'remove') setRemoving(r);
            }}
          />
        );
      },
    },
  ];
  const linesSet = useColumnSettings('purchase-requirements', lineColumns, {
    hidden: ['描述', '采购员'],
  });
  // 平铺视图：型号前多「来源单号」「客户」两列，列设置单独记；默认收起客户、目标价，1366 宽不横向滚动
  // （客户在按订单分组的视图里看得到，两列都能从「列」里打开）
  const flatColumns: TableColumnsType<Requirement> = [
    {
      title: '来源单号',
      dataIndex: 'soNo',
      width: 160,
      fixed: 'left',
      render: (v: string, r) => (
        <CopyText text={v}>
          <button
            type="button"
            className="zhul-link"
            onClick={() => history.push(PATHS.salesOrder(r.soId))}
          >
            {v}
          </button>
        </CopyText>
      ),
    },
    {
      title: '客户',
      key: 'customer',
      width: 200,
      render: (_, r) => (
        <CustomerCell name={r.customerName} country={r.customerCountry} />
      ),
    },
    // 平铺视图里型号前还有来源单号、客户两列：只固定来源单号，型号列不再固定
    ...lineColumns.map((c) =>
      c.fixed === 'left' ? { ...c, fixed: undefined } : c,
    ),
  ];
  const flatSet = useColumnSettings('purchase-requirements-flat', flatColumns, {
    hidden: ['客户', '描述', '目标价', '采购员'],
  });

  const rowSelection = (scopeRows: Requirement[]) =>
    canCreate || canAssign
      ? {
          selectedRowKeys: scopeRows
            .filter((r) => selected.has(r.id))
            .map((r) => r.id),
          getCheckboxProps: (r: Requirement) => ({ disabled: !isOpen(r) }),
          onChange: (keys: React.Key[]) => toggleSelect(keys, scopeRows),
        }
      : undefined;

  const toggleOrder = (soId: number) =>
    setCollapsed((s) => {
      const n = new Set(s);
      if (n.has(soId)) n.delete(soId);
      else n.add(soId);
      return n;
    });

  const orderHead = (o: RequirementOrder) => {
    const open = !collapsed.has(o.soId);
    return (
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 14,
          padding: '12px 16px',
          flexWrap: 'wrap',
        }}
      >
        <Button
          type="text"
          size="small"
          icon={open ? <ChevronDown size="1em" /> : <ChevronRight size="1em" />}
          aria-label={open ? `收起 ${o.soNo}` : `展开 ${o.soNo}`}
          onClick={() => toggleOrder(o.soId)}
        />
        <div style={{ minWidth: 170 }}>
          <button
            type="button"
            className="zhul-link zhul-link-doc"
            onClick={() => history.push(PATHS.salesOrder(o.soId))}
          >
            {o.soNo}
          </button>
          {sub(
            palette.mute,
            `销售 ${o.salesDate ?? '—'} · ${o.stockType === 2 ? '期货' : '现货'}${o.source === 2 ? ' · 手动创建' : ''}`,
          )}
        </div>
        <div style={{ minWidth: 220 }}>
          <b style={{ color: palette.ink }}>{o.customerName}</b>{' '}
          {o.customerType ? <CustomerTypePill type={o.customerType} /> : null}
          {sub(
            palette.mute,
            o.customerCountry ? <CountryText value={o.customerCountry} /> : '',
          )}
        </div>
        {o.overdueCount > 0 && (
          <Pill tone="red">
            <Clock size="1em" /> {o.overdueCount} 个超时
          </Pill>
        )}
        <span style={{ flex: 1 }} />
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {o.orderedCount > 0 && (
            <Pill tone="green">已下单 {o.orderedCount}</Pill>
          )}
          {o.partialCount > 0 && (
            <Pill tone="info">部分下单 {o.partialCount}</Pill>
          )}
          {o.draftCount > 0 && <Pill tone="orange">草稿中 {o.draftCount}</Pill>}
          {o.pendingCount > 0 && (
            <Pill tone="gray">未安排 {o.pendingCount}</Pill>
          )}
        </div>
        <span style={{ fontSize: 12, color: palette.mute }}>
          业务员 {o.ownerName ?? '—'}
        </span>
      </div>
    );
  };

  const selectedList = [...selected.values()];
  const empty =
    !loading && (view === 'order' ? orders.length === 0 : rows.length === 0);

  const searchNow = () => reset(() => setFilters(draftFilters));
  /** 下拉、快捷筛选切换后立即查询（doc-list-unify 状态下拉的写法） */
  const applyNow = (patch: Partial<Filters>) =>
    reset(() => {
      setDraftFilters((f) => ({ ...f, ...patch }));
      setFilters((f) => ({ ...f, ...patch }));
    });

  return (
    <div>
      <PageHeader
        help={HELP.page}
        extra={
          <ScopeSwitch
            value={scope}
            onChange={(v) => reset(() => setScope(v))}
          />
        }
      />
      {stats && (
        <SummaryBar
          items={[
            {
              key: 'inDraft',
              label: '已排入草稿',
              help: HELP.inDraft,
              value: stats.inDraft,
            },
            ...(scope === 'all'
              ? [
                  {
                    key: 'unassigned',
                    label: '未指定采购员',
                    help: HELP.unassigned,
                    value: stats.unassigned,
                  },
                ]
              : []),
            {
              key: 'drafts',
              label: '草稿采购订单',
              help: HELP.drafts,
              value: (
                <button
                  type="button"
                  className="zhul-link"
                  onClick={() => history.push(PATHS.orders)}
                >
                  {stats.drafts}
                </button>
              ),
            },
          ]}
        />
      )}
      <FilterCard
        onSearch={searchNow}
        onReset={() =>
          reset(() => {
            setDraftFilters({ status: 'open' });
            setFilters({ status: 'open' });
          })
        }
        minColumn={140}
        moreCount={
          [
            scope === 'all' ? filters.purchaserId : undefined,
            filters.stockType,
          ].filter((v) => v != null).length
        }
        more={
          <>
            {scope === 'all' && (
              <FilterField label="采购员">
                <Select
                  allowClear
                  placeholder="全部"
                  value={draftFilters.purchaserId}
                  options={users}
                  showSearch={{ optionFilterProp: 'label' }}
                  onChange={(v) =>
                    setDraftFilters((f) => ({ ...f, purchaserId: v }))
                  }
                  aria-label="采购员"
                />
              </FilterField>
            )}
            <FilterField label="现货 / 期货">
              <Select
                allowClear
                placeholder="全部"
                value={draftFilters.stockType}
                onChange={(v) =>
                  setDraftFilters((f) => ({ ...f, stockType: v }))
                }
                options={[
                  { value: 1, label: '现货' },
                  { value: 2, label: '期货' },
                ]}
                aria-label="现货 / 期货"
              />
            </FilterField>
          </>
        }
      >
        <FilterField label="关键词" span={2} help={HELP.keyword}>
          <Input
            allowClear
            prefix={<Search size={16} />}
            placeholder="销售订单编号、客户、型号"
            value={draftFilters.keyword}
            onChange={(e) =>
              setDraftFilters((f) => ({ ...f, keyword: e.target.value }))
            }
            onPressEnter={searchNow}
            aria-label="关键词"
          />
        </FilterField>
        <FilterField label="状态">
          <Select
            value={draftFilters.status}
            onChange={(v) => applyNow({ status: v })}
            options={[
              { value: 'open', label: withCount('还有没下单的', stats?.open) },
              { value: 'all', label: '全部状态' },
            ]}
            aria-label="状态"
          />
        </FilterField>
        <FilterField label="向谁买" help={HELP.source}>
          <Select
            allowClear
            placeholder="全部"
            value={draftFilters.sourceType}
            onChange={(v) => applyNow({ sourceType: v })}
            options={[
              { value: 'supplier', label: '老供应商' },
              { value: 'shop', label: '线上店铺' },
              { value: 'none', label: withCount('还没定', stats?.unplanned) },
            ]}
            aria-label="向谁买"
          />
        </FilterField>
      </FilterCard>

      <MainCard
        settings={view === 'order' ? linesSet.settings : flatSet.settings}
        left={
          selectedList.length > 0 ? (
            <>
              <b style={{ color: palette.ink }}>
                已选 {selectedList.length} 个型号
              </b>
              <span>
                · 来自 {new Set(selectedList.map((r) => r.soId)).size} 张订单
              </span>
            </>
          ) : view === 'order' ? (
            `共 ${total} 张订单`
          ) : (
            `共 ${total} 条`
          )
        }
        right={
          selectedList.length > 0 ? (
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <Button onClick={() => setSelected(new Map())}>取消选择</Button>
              {canAssign && (
                <Button onClick={() => setAssigning(selectedList)}>
                  指派采购员
                </Button>
              )}
              {canCreate && (
                <Button onClick={() => setSourcing(selectedList)}>
                  选渠道
                </Button>
              )}
              {canCreate && (
                <Button
                  type="primary"
                  icon={<ShoppingCart size="1em" />}
                  onClick={() => setGenerating(selectedList)}
                >
                  生成采购订单
                </Button>
              )}
            </div>
          ) : (
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              {view === 'order' && (
                <>
                  <Button type="link" onClick={() => setCollapsed(new Set())}>
                    展开全部
                  </Button>
                  <Button
                    type="link"
                    onClick={() =>
                      setCollapsed(new Set(orders.map((o) => o.soId)))
                    }
                  >
                    折叠全部
                  </Button>
                </>
              )}
              <Segmented
                value={view}
                onChange={(v) => reset(() => setView(v as View))}
                options={[
                  { value: 'order', label: '按订单' },
                  { value: 'model', label: '按型号' },
                ]}
              />
            </div>
          )
        }
        quick={
          <QuickFilters
            options={[
              {
                value: QUICK_OVERDUE,
                label: '超时未下单',
                count: stats?.overdue,
                danger: (stats?.overdue ?? 0) > 0,
              },
            ]}
            value={filters.overdue ? [QUICK_OVERDUE] : []}
            onChange={(v) =>
              applyNow({ overdue: v.includes(QUICK_OVERDUE) || undefined })
            }
          />
        }
      >
        {error ? (
          <ErrorHint message={error} onRetry={load} />
        ) : empty ? (
          <div>
            <EmptyHint
              title={
                scope === 'mine'
                  ? '你负责的型号都已下单'
                  : '没有符合条件的采购需求'
              }
              description={
                scope === 'mine'
                  ? '新的订单生成后，你负责的型号会自动出现在这里'
                  : '换个筛选条件试试'
              }
            />
          </div>
        ) : view === 'order' ? (
          loading && orders.length === 0 ? (
            <Skeleton active />
          ) : null
        ) : (
          <Table<Requirement>
            rowKey="id"
            columns={flatSet.columns}
            dataSource={rows}
            locale={{
              emptyText: tableEmpty('没有符合条件的采购需求', loading),
            }}
            loading={loading}
            scroll={{ x: tableScrollX(flatSet.columns) }}
            rowSelection={rowSelection(rows)}
            onRow={overdueRow}
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
        )}
      </MainCard>

      {/* 按订单：每张订单一张白卡，与主卡同级、浮在页面底上；卡头白底加分隔线，表格收进卡内留 16（D39，对比图 R5 的 B3） */}
      {view === 'order' && !error && !empty && orders.length > 0 && (
        <div style={{ display: 'grid', gap: 16, marginTop: 16 }}>
          {orders.map((o) => {
            const open = !collapsed.has(o.soId);
            return (
              <Panel key={o.soId} style={{ overflow: 'hidden' }}>
                {orderHead(o)}
                {o.salesRemark && (
                  <div
                    style={{
                      display: 'flex',
                      gap: 10,
                      alignItems: 'baseline',
                      padding: '10px 16px 10px 56px',
                      background: palette.inset,
                      borderTop: `1px solid ${palette.hairline}`,
                      borderBottom: `1px solid ${palette.hairline}`,
                    }}
                  >
                    <b style={{ color: palette.sub, whiteSpace: 'nowrap' }}>
                      <FileText size="1em" /> 销售备注
                    </b>
                    <span
                      style={{ color: palette.ink, whiteSpace: 'pre-wrap' }}
                    >
                      {o.salesRemark}
                    </span>
                  </div>
                )}
                {open && (
                  <div
                    style={{
                      padding: 16,
                      borderTop: o.salesRemark
                        ? undefined
                        : `1px solid ${palette.hairline}`,
                    }}
                  >
                    <Table<Requirement>
                      rowKey="id"
                      columns={linesSet.columns}
                      dataSource={o.lines}
                      pagination={false}
                      scroll={{ x: tableScrollX(linesSet.columns) }}
                      rowSelection={rowSelection(o.lines)}
                      onRow={overdueRow}
                    />
                  </div>
                )}
              </Panel>
            );
          })}
          {/* 统一分页（每页条数、页码、跳页），总数已在主卡左上角（ui-redesign P8） */}
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Pagination
              {...PAGER_STYLE}
              current={page}
              pageSize={pageSize}
              total={total}
              hideOnSinglePage={total <= 10}
              onChange={(p, sz) => {
                setPage(p);
                setPageSize(sz);
              }}
            />
          </div>
        </div>
      )}

      <SourceModal
        targets={sourcing}
        onClose={() => setSourcing(undefined)}
        onDone={() => {
          setSourcing(undefined);
          message.success('已选定渠道，排入草稿采购订单');
          refresh();
        }}
      />
      <SplitModal
        target={splitting}
        users={users}
        onClose={() => setSplitting(undefined)}
        onDone={() => {
          setSplitting(undefined);
          message.success('已拆分');
          refresh();
        }}
      />
      <RemoveModal
        target={removing}
        onClose={() => setRemoving(undefined)}
        onDone={() => {
          setRemoving(undefined);
          message.success('已删除');
          refresh();
        }}
      />
      <AssignModal
        targets={assigning}
        users={users}
        onClose={() => setAssigning(undefined)}
        onDone={() => {
          setAssigning(undefined);
          message.success('已指派');
          refresh();
        }}
      />
      <GenerateModal
        targets={generating}
        onClose={() => setGenerating(undefined)}
        onDone={(ids) => {
          setGenerating(undefined);
          message.success(`已生成或追加到 ${ids.length} 张草稿采购订单`);
          if (ids.length === 1) {
            history.push(PATHS.order(ids[0]));
          } else {
            refresh();
          }
        }}
      />
    </div>
  );
};

/** 采购对象转成接口参数 */
const toCounterparty = (v: CounterpartyValue): CounterpartyValue =>
  v.supplierId
    ? { supplierId: v.supplierId }
    : { channel: v.channel, shopName: v.shopName?.trim() };

// ---------------------------------------------------------------- 选定渠道

const SourceModal: React.FC<{
  targets?: Requirement[];
  onClose: () => void;
  onDone: () => void;
}> = ({ targets, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [value, setValue] = useState<CounterpartyValue>({});
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (targets) setValue({});
  }, [targets]);
  const submit = async () => {
    if (!targets) return;
    setBusy(true);
    try {
      await requirementApi.source(
        targets.map((t) => t.id),
        toCounterparty(value),
      );
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal
      open={!!targets}
      title="选定采购渠道"
      width={600}
      onCancel={onClose}
      okText="确定，排入草稿"
      okButtonProps={{ disabled: !counterpartyReady(value), loading: busy }}
      onOk={submit}
    >
      {targets && (
        <div style={{ display: 'grid', gap: 14 }}>
          <div style={{ color: palette.ink, fontWeight: 600 }}>
            {targets.length === 1
              ? `${targets[0].model}${targets[0].brand ? ` · ${targets[0].brand}` : ''} × ${targets[0].quantity}（${targets[0].soNo}）`
              : `已选 ${targets.length} 个型号`}
          </div>
          <CounterpartyPicker value={value} onChange={setValue} />
          <div style={{ fontSize: 12, color: palette.mute }}>
            选定后排入「
            {counterpartyReady(value)
              ? counterpartyTitle(value)
              : '所选采购对象'}
            」的草稿采购订单（没有时新建）；已在草稿里的型号会改过去，已填的单价保留。没有采购员的型号会归到你名下。
          </div>
        </div>
      )}
    </Modal>
  );
};

// ---------------------------------------------------------------- 删除（openspec purchase-requirement-delete）

const REMOVE_REASONS = [
  { value: 'stock', label: '仓库有库存' },
  { value: 'outside', label: '在系统外采购' },
  { value: 'other', label: '其他' },
] as const;
type RemoveReason = (typeof REMOVE_REASONS)[number]['value'];

export const RemoveModal: React.FC<{
  target?: Requirement;
  onClose: () => void;
  onDone: () => void;
}> = ({ target, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [reason, setReason] = useState<RemoveReason>('stock');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (target) {
      setReason('stock');
      setNote('');
    }
  }, [target]);

  const ordered = target?.orderedQty ?? 0;
  const n = target ? target.quantity - ordered : 0;
  const partial = ordered > 0;
  const needNote = reason === 'other' && !note.trim();

  const submit = async () => {
    if (!target) return;
    if (needNote) {
      message.error('请填写删除说明');
      return;
    }
    setBusy(true);
    try {
      await requirementApi.remove(target.id, {
        reason,
        note: note.trim(),
      });
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const stat = (label: string, value: number, danger?: boolean) => (
    <div>
      <div style={{ fontSize: 12, color: palette.mute }}>{label}</div>
      <b style={{ fontSize: 16, color: danger ? palette.red : palette.ink }}>
        {value}
      </b>
    </div>
  );

  return (
    <Modal
      open={!!target}
      title={partial ? '删除没下单的数量' : '删除采购需求'}
      width={560}
      onCancel={onClose}
      okText={partial ? `删除 ${n} 个` : '删除'}
      okButtonProps={{ danger: true, loading: busy, disabled: n <= 0 }}
      onOk={submit}
    >
      {target && (
        <div style={{ display: 'grid', gap: 14 }}>
          {sub(
            palette.mute,
            partial
              ? `已下单的 ${ordered} 个不受影响，只删还没下单的 ${n} 个`
              : '删除后这个型号不再需要采购，数量从采购需求里去掉',
          )}
          <div
            style={{
              padding: '10px 14px',
              borderRadius: 10,
              background: palette.inset,
            }}
          >
            <b style={{ color: palette.ink }}>
              {target.model}
              {target.brand ? ` · ${target.brand}` : ''}
              {target.category ? ` · ${target.category}` : ''}
            </b>
            {sub(
              palette.mute,
              [
                target.soNo,
                target.customerName,
                target.purchaserName && `采购员 ${target.purchaserName}`,
              ]
                .filter(Boolean)
                .join(' · '),
            )}
          </div>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(4, 1fr)',
              gap: 12,
              padding: '10px 14px',
              borderRadius: 10,
              border: `1px solid ${palette.hairline}`,
            }}
          >
            {stat('需求数量', target.quantity)}
            {partial
              ? stat('已下单', ordered)
              : stat('草稿中', target.draftQty)}
            {partial ? stat('可下单', n) : stat('已下单', ordered)}
            {stat('删除', n, true)}
          </div>
          {target.draftQty > 0 && (
            <Alert
              type="warning"
              showIcon
              title="草稿采购订单上的这个型号一并移除；草稿没有其他型号时自动删除。"
            />
          )}
          <div>
            <div style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}>
              删除原因 <span style={{ color: palette.red }}>*</span>
            </div>
            <Radio.Group
              optionType="button"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              options={REMOVE_REASONS.map((x) => ({ ...x }))}
              aria-label="删除原因"
            />
            <Input.TextArea
              style={{ marginTop: 8 }}
              rows={2}
              maxLength={180}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder={
                reason === 'other' ? '请填写删除说明（必填）' : '说明（选填）'
              }
              aria-label="删除说明"
            />
          </div>
          <Alert
            type="info"
            showIcon
            title={
              partial
                ? `删除后需求数量变为 ${ordered}，状态为「已下单」；销售订单上这 ${n} 个记为「不需采购」。`
                : '客户不要这个型号了，请在销售订单上用「部分取消」。'
            }
          />
        </div>
      )}
    </Modal>
  );
};

// ---------------------------------------------------------------- 拆分

const SplitModal: React.FC<{
  target?: Requirement;
  users: { value: number; label: string }[];
  onClose: () => void;
  onDone: () => void;
}> = ({ target, users, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [qty, setQty] = useState<number | null>(null);
  const [purchaserId, setPurchaserId] = useState<number>();
  const [changeSource, setChangeSource] = useState(false);
  const [source, setSource] = useState<CounterpartyValue>({});
  const [busy, setBusy] = useState(false);
  const max = target
    ? target.orderedQty > 0
      ? target.quantity - target.orderedQty
      : target.quantity - 1
    : 0;

  useEffect(() => {
    if (target) {
      setQty(null);
      setPurchaserId(target.purchaserId ?? undefined);
      setChangeSource(false);
      setSource({});
    }
  }, [target]);

  const keepSource =
    target?.suggestedSupplierName ??
    (target?.suggestedShopName
      ? `${target.suggestedChannelName ?? ''} · ${target.suggestedShopName}`
      : '还没定');

  const submit = async () => {
    if (!target || !qty) return;
    setBusy(true);
    try {
      await requirementApi.split(target.id, {
        quantity: qty,
        purchaserId,
        ...(changeSource && counterpartyReady(source)
          ? toCounterparty(source)
          : {}),
      });
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open={!!target}
      title="拆分采购需求"
      width={620}
      onCancel={onClose}
      okText="拆分"
      okButtonProps={{
        disabled:
          !qty || qty > max || (changeSource && !counterpartyReady(source)),
        loading: busy,
      }}
      onOk={submit}
    >
      {target && (
        <div style={{ display: 'grid', gap: 14 }}>
          <div
            style={{
              padding: '10px 14px',
              borderRadius: 10,
              background: palette.inset,
            }}
          >
            <b style={{ color: palette.ink }}>
              {target.model}
              {target.brand ? ` · ${target.brand}` : ''}
            </b>
            {sub(
              palette.mute,
              `来自 ${target.soNo} · 需求 ${target.quantity} 个 · 已下单 ${target.orderedQty} · 目标价 ${
                target.targetPrice == null
                  ? '无'
                  : formatAmount(target.targetPrice, 'CNY')
              }`,
            )}
          </div>
          {max <= 0 ? (
            <Alert
              type="info"
              showIcon
              title="这条需求已经全部下单，不能拆分"
            />
          ) : (
            <>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr',
                  gap: 12,
                }}
              >
                <div>
                  <div
                    style={{
                      fontSize: 12,
                      color: palette.sub,
                      marginBottom: 6,
                    }}
                  >
                    拆出数量
                  </div>
                  <InputNumber
                    min={1}
                    max={max}
                    precision={0}
                    value={qty}
                    onChange={setQty}
                    style={{ width: '100%' }}
                    aria-label="拆出数量"
                  />
                  {sub(palette.mute, `最多能拆出 ${max} 个`)}
                </div>
                <div>
                  <div
                    style={{
                      fontSize: 12,
                      color: palette.sub,
                      marginBottom: 6,
                    }}
                  >
                    采购员
                  </div>
                  <Select
                    style={{ width: '100%' }}
                    value={purchaserId}
                    options={users}
                    onChange={setPurchaserId}
                    showSearch={{ optionFilterProp: 'label' }}
                    aria-label="采购员"
                  />
                </div>
              </div>
              <div>
                <div
                  style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}
                >
                  新需求向谁买：
                  <button
                    type="button"
                    className="zhul-link"
                    onClick={() => setChangeSource((v) => !v)}
                  >
                    {changeSource
                      ? '沿用原来的'
                      : `沿用「${keepSource}」，点这里改`}
                  </button>
                </div>
                {changeSource && (
                  <CounterpartyPicker value={source} onChange={setSource} />
                )}
              </div>
              <div style={{ fontSize: 12, color: palette.mute }}>
                一个型号要向两家买、或交给两位采购员时拆分；拆出的数量会从草稿采购订单里扣出，新需求渠道已定时自动排入对应草稿。已下单的数量不能拆。
              </div>
            </>
          )}
        </div>
      )}
    </Modal>
  );
};

// ---------------------------------------------------------------- 指派

const AssignModal: React.FC<{
  targets?: Requirement[];
  users: { value: number; label: string }[];
  onClose: () => void;
  onDone: () => void;
}> = ({ targets, users, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [purchaserId, setPurchaserId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (targets) setPurchaserId(null);
  }, [targets]);
  const submit = async () => {
    if (!targets) return;
    setBusy(true);
    try {
      await requirementApi.assign(
        targets.map((t) => t.id),
        purchaserId,
      );
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal
      open={!!targets}
      title="指派采购员"
      width={480}
      onCancel={onClose}
      okText="指派"
      okButtonProps={{ loading: busy, disabled: purchaserId == null }}
      onOk={submit}
    >
      {targets && (
        <div style={{ display: 'grid', gap: 12 }}>
          <div style={{ color: palette.sub }}>
            {targets.length === 1
              ? `${targets[0].model}（${targets[0].soNo}）`
              : `已选 ${targets.length} 个型号`}
          </div>
          <Select
            placeholder="选择采购员"
            value={purchaserId ?? undefined}
            options={users}
            onChange={setPurchaserId}
            showSearch={{ optionFilterProp: 'label' }}
            aria-label="采购员"
          />
          <div style={{ fontSize: 12, color: palette.mute }}>
            只影响还没全部下单的部分：草稿采购订单上的型号会移到新采购员对同一采购对象的草稿；已下单的仍归原采购订单。
          </div>
        </div>
      )}
    </Modal>
  );
};

// ---------------------------------------------------------------- 生成采购订单

interface GroupState extends GenerateGroup {
  chosen: CounterpartyValue;
}

const keyOf = (v: CounterpartyValue) =>
  v.supplierId ? `s${v.supplierId}` : `c${v.channel}:${v.shopName?.trim()}`;

const GenerateModal: React.FC<{
  targets?: Requirement[];
  onClose: () => void;
  onDone: (poIds: number[]) => void;
}> = ({ targets, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [groups, setGroups] = useState<GroupState[]>();
  const [busy, setBusy] = useState(false);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;

  useEffect(() => {
    if (!targets) return;
    setGroups(undefined);
    requirementApi
      .preview(targets.map((t) => t.id))
      .then((v) =>
        setGroups(
          v.groups.map((g) => ({
            ...g,
            chosen: g.supplierId
              ? { supplierId: g.supplierId, supplierName: g.supplierName }
              : g.shopName
                ? { channel: g.channel, shopName: g.shopName }
                : {},
          })),
        ),
      )
      .catch((e) => {
        message.error(readBizError(e).message);
        closeRef.current();
      });
  }, [targets, message]);

  const ready = !!groups && groups.every((g) => counterpartyReady(g.chosen));
  const moved =
    groups?.flatMap((g) => g.lines).filter((l) => (l.draftQty ?? 0) > 0)
      .length ?? 0;
  const drafts = useMemo(
    () =>
      new Set(
        groups
          ?.filter((g) => counterpartyReady(g.chosen))
          .map((g) => `${keyOf(g.chosen)}:${g.stockType}`),
      ).size,
    [groups],
  );

  const submit = async () => {
    if (!groups) return;
    setBusy(true);
    try {
      const merged = new Map<
        string,
        CounterpartyValue & { requirementIds: number[] }
      >();
      for (const g of groups) {
        const k = keyOf(g.chosen);
        const cur = merged.get(k) ?? {
          ...toCounterparty(g.chosen),
          requirementIds: [],
        };
        cur.requirementIds.push(...g.lines.map((l) => l.requirementId));
        merged.set(k, cur);
      }
      onDone(await requirementApi.generate([...merged.values()]));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open={!!targets}
      title="生成采购订单"
      width={780}
      onCancel={onClose}
      okText="生成草稿"
      okButtonProps={{ disabled: !ready, loading: busy }}
      onOk={submit}
    >
      {!groups ? (
        <Skeleton active />
      ) : (
        <div style={{ display: 'grid', gap: 12 }}>
          <div style={{ color: palette.sub }}>
            已选 {targets?.length} 个型号，按建议渠道和现货 / 期货分成{' '}
            {groups.length} 组；确认后每组生成或追加到你对该采购对象、同一现货 /
            期货的草稿采购订单。
            {moved > 0 &&
              ` 其中 ${moved} 个型号原来已在草稿采购订单里，会挪到所选分组，原草稿空了自动作废。`}
          </div>
          {groups.map((g) => (
            <div
              key={g.key}
              style={{
                padding: '12px 14px',
                borderRadius: 12,
                background: palette.inset,
                border: `1px solid ${counterpartyReady(g.chosen) ? palette.hairline : palette.orange}`,
                display: 'grid',
                gap: 10,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <b style={{ color: palette.ink }}>{g.title}</b>
                <StockPill type={g.stockType} />
                <span style={{ fontSize: 12, color: palette.mute }}>
                  {g.lines.length} 个型号
                </span>
              </div>
              <CounterpartyPicker
                value={g.chosen}
                onChange={(v) =>
                  setGroups((gs) =>
                    gs?.map((x) => (x.key === g.key ? { ...x, chosen: v } : x)),
                  )
                }
              />
              {g.lines.map((l) => (
                <div
                  key={l.requirementId}
                  style={{
                    display: 'grid',
                    gridTemplateColumns: '1.4fr 1.2fr 0.6fr',
                    fontSize: 13,
                  }}
                >
                  <span>
                    <b>{l.model}</b>
                    {l.brand && (
                      <span style={{ color: palette.mute }}> · {l.brand}</span>
                    )}
                  </span>
                  <span style={{ color: palette.mute }}>
                    {l.soNo}
                    {l.purchaserName ? '' : ' · 采购员未指定'}
                  </span>
                  <span>
                    {l.availableQty} 个
                    {(l.draftQty ?? 0) > 0 && (
                      <span style={{ fontSize: 12, color: palette.mute }}>
                        {' '}
                        · 含草稿 {l.draftQty}
                      </span>
                    )}
                  </span>
                </div>
              ))}
            </div>
          ))}
          {ready && (
            <div style={{ color: palette.ink }}>
              将生成或追加 <b>{drafts}</b>{' '}
              张草稿采购订单；同一供应商的现货和期货分开下单，没有采购员的型号会归到你名下。
            </div>
          )}
        </div>
      )}
    </Modal>
  );
};

export default RequirementList;
