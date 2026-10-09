import {
  CaretDownOutlined,
  CaretRightOutlined,
  ClockCircleOutlined,
  ExclamationCircleOutlined,
  FileTextOutlined,
  SearchOutlined,
  ShoppingCartOutlined,
  UserDeleteOutlined,
} from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  Alert,
  App,
  Button,
  Input,
  InputNumber,
  Modal,
  Segmented,
  Select,
  Skeleton,
  Table,
} from 'antd';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { CustomerTypePill } from '@/pages/inquiry/shared/components';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { useUserOptions } from '@/pages/sales/orders/dialogs';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import {
  auditColumns,
  Card,
  CounterpartyPicker,
  counterpartyReady,
  counterpartyTitle,
  PATHS,
  Pill,
  PurchasePageTitle,
  ReqStatusPill,
  SourceCell,
  StatCard,
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
type Scope = 'mine' | 'all';

interface Filters {
  keyword?: string;
  status: 'open' | 'all';
  sourceType?: 'supplier' | 'shop' | 'none';
  stockType?: number;
  purchaserId?: number;
}

const OPEN = ['pending', 'draft', 'partial'];
const isOpen = (r: Requirement) =>
  OPEN.includes(r.status) && r.orderedQty < r.quantity;

const RequirementList: React.FC = () => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const access = useAccess();
  const users = useUserOptions();
  const canSplit = !!access['purchase:requirement:split'];
  const canAssign = !!access['purchase:requirement:assign'];
  const canCreate = !!access['purchase:order:create'];
  // 采购负责人（有指派权限）默认看全部，采购员默认看自己负责的
  const [view, setView] = useState<View>('order');
  const [scope, setScope] = useState<Scope>(canAssign ? 'all' : 'mine');
  const [filters, setFilters] = useState<Filters>({ status: 'open' });
  const [draftFilters, setDraftFilters] = useState<Filters>({ status: 'open' });
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [orders, setOrders] = useState<RequirementOrder[]>([]);
  const [rows, setRows] = useState<Requirement[]>([]);
  const [total, setTotal] = useState(0);
  const [stats, setStats] = useState<RequirementStats>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [collapsed, setCollapsed] = useState<Set<number>>(new Set());
  const [selected, setSelected] = useState<Map<number, Requirement>>(new Map());
  const [splitting, setSplitting] = useState<Requirement>();
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

  const statusCell = (r: Requirement) => (
    <div>
      <ReqStatusPill status={r.status} name={r.statusName} />
      {r.purchaseOrders.map((p) => (
        <div key={p.id} style={{ fontSize: 12 }}>
          <a onClick={() => history.push(PATHS.order(p.id))}>
            {p.poNo ?? '去下单 →'}
          </a>
          {!p.poNo && (
            <span style={{ color: palette.mute }}> {p.supplierName}</span>
          )}
        </div>
      ))}
    </div>
  );

  const lineColumns: TableColumnsType<Requirement> = [
    {
      title: '型号 · 品牌 · 品类',
      dataIndex: 'model',
      width: 230,
      render: (v: string, r) => (
        <div>
          <b>{v}</b>
          {sub(
            palette.mute,
            [r.brand, r.category].filter(Boolean).join(' · ') || '—',
          )}
        </div>
      ),
    },
    {
      title: '数量',
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
        v ?? <span style={{ color: palette.orange }}>未指定</span>,
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
      width: 150,
      fixed: 'right',
      render: (_, r) => {
        const open = isOpen(r);
        const planned = !!(r.suggestedSupplierName || r.suggestedShopName);
        return (
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            {canCreate && open && (
              <a
                style={planned ? undefined : { color: palette.orange }}
                onClick={() => setSourcing([r])}
              >
                {planned ? '换渠道' : '选渠道'}
              </a>
            )}
            {canAssign && open && <a onClick={() => setAssigning([r])}>指派</a>}
            {canSplit && open && <a onClick={() => setSplitting(r)}>拆分</a>}
          </div>
        );
      },
    },
  ];

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
          padding: '12px 18px',
          flexWrap: 'wrap',
          background: open ? palette.hover : undefined,
        }}
      >
        <Button
          type="text"
          size="small"
          icon={open ? <CaretDownOutlined /> : <CaretRightOutlined />}
          aria-label={open ? `收起 ${o.soNo}` : `展开 ${o.soNo}`}
          onClick={() => toggleOrder(o.soId)}
        />
        <div style={{ minWidth: 170 }}>
          <a
            style={{ fontWeight: 700 }}
            onClick={() => history.push(PATHS.salesOrder(o.soId))}
          >
            {o.soNo}
          </a>
          {sub(
            palette.mute,
            `销售 ${o.salesDate ?? '—'} · ${o.stockType === 2 ? '期货' : '现货'}${o.source === 2 ? ' · 手动创建' : ''}`,
          )}
        </div>
        <div style={{ minWidth: 220 }}>
          <b style={{ color: palette.ink }}>{o.customerName}</b>{' '}
          {o.customerType ? <CustomerTypePill type={o.customerType} /> : null}
          {sub(palette.mute, o.customerCountry ?? '')}
        </div>
        <span style={{ flex: 1 }} />
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {o.orderedCount > 0 && (
            <Pill tone="green">已下单 {o.orderedCount}</Pill>
          )}
          {o.partialCount > 0 && (
            <Pill tone="accent">部分下单 {o.partialCount}</Pill>
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
  const canGenerate =
    selectedList.length > 0 && selectedList.every((r) => r.availableQty > 0);
  const empty =
    !loading && (view === 'order' ? orders.length === 0 : rows.length === 0);

  return (
    <div>
      <PurchasePageTitle
        crumbs={['采购需求']}
        title="采购需求"
        description="先看哪些订单要采购，再看每单的型号向谁买、谁负责；渠道已定的型号已自动排进草稿采购单。"
      />

      {stats && (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
            gap: 16,
            marginBottom: 16,
          }}
        >
          <StatCard
            icon={<ClockCircleOutlined />}
            color={palette.link}
            label="还没全部下单"
            value={stats.open}
            hint={`其中 ${stats.inDraft} 个已排入草稿`}
          />
          <StatCard
            icon={<ExclamationCircleOutlined />}
            color={stats.unplanned > 0 ? palette.orange : palette.mute}
            label="还没定渠道"
            value={stats.unplanned}
            hint="没有回价，选老供应商或填平台 + 店铺"
            hintColor={stats.unplanned > 0 ? palette.orange : undefined}
          />
          <StatCard
            icon={<UserDeleteOutlined />}
            color={stats.unassigned > 0 ? palette.orange : palette.mute}
            label="未指定采购员"
            value={scope === 'mine' ? '—' : stats.unassigned}
            hint={
              scope === 'mine'
                ? '切到「全部」查看'
                : '指派后会出现在采购员的列表里'
            }
          />
          <StatCard
            icon={<FileTextOutlined />}
            color={palette.violet}
            label="草稿采购单"
            value={
              <a onClick={() => history.push(PATHS.orders)}>{stats.drafts}</a>
            }
            hint="谈好价后去草稿里填单价、确认下单"
          />
        </div>
      )}

      <Card style={{ marginBottom: 16, padding: 16 }}>
        <div
          style={{
            display: 'flex',
            gap: 12,
            alignItems: 'center',
            flexWrap: 'wrap',
          }}
        >
          <Segmented
            value={view}
            onChange={(v) => reset(() => setView(v as View))}
            options={[
              { value: 'order', label: '按订单' },
              { value: 'model', label: '按型号' },
            ]}
          />
          <Segmented
            value={scope}
            onChange={(v) => reset(() => setScope(v as Scope))}
            options={[
              { value: 'mine', label: '我负责的' },
              { value: 'all', label: '全部' },
            ]}
          />
          <Select
            style={{ width: 150 }}
            value={draftFilters.status}
            onChange={(v) => setDraftFilters((f) => ({ ...f, status: v }))}
            options={[
              { value: 'open', label: '还有没下单的' },
              { value: 'all', label: '全部状态' },
            ]}
            aria-label="状态"
          />
          <Input
            allowClear
            style={{ width: 240 }}
            prefix={<SearchOutlined />}
            placeholder="订单编号、客户、型号"
            value={draftFilters.keyword}
            onChange={(e) =>
              setDraftFilters((f) => ({ ...f, keyword: e.target.value }))
            }
            onPressEnter={() => reset(() => setFilters(draftFilters))}
            aria-label="关键词"
          />
          <Select
            allowClear
            style={{ width: 140 }}
            placeholder="向谁买"
            value={draftFilters.sourceType}
            onChange={(v) => setDraftFilters((f) => ({ ...f, sourceType: v }))}
            options={[
              { value: 'supplier', label: '老供应商' },
              { value: 'shop', label: '线上店铺' },
              { value: 'none', label: '还没定' },
            ]}
            aria-label="向谁买"
          />
          {scope === 'all' && (
            <Select
              allowClear
              style={{ width: 140 }}
              placeholder="采购员"
              value={draftFilters.purchaserId}
              options={users}
              showSearch={{ optionFilterProp: 'label' }}
              onChange={(v) =>
                setDraftFilters((f) => ({ ...f, purchaserId: v }))
              }
              aria-label="采购员"
            />
          )}
          <Select
            allowClear
            style={{ width: 120 }}
            placeholder="现货 / 期货"
            value={draftFilters.stockType}
            onChange={(v) => setDraftFilters((f) => ({ ...f, stockType: v }))}
            options={[
              { value: 1, label: '现货' },
              { value: 2, label: '期货' },
            ]}
            aria-label="现货 / 期货"
          />
          <span style={{ flex: 1 }} />
          {view === 'order' && (
            <>
              <Button type="link" onClick={() => setCollapsed(new Set())}>
                展开全部
              </Button>
              <Button
                type="link"
                onClick={() => setCollapsed(new Set(orders.map((o) => o.soId)))}
              >
                折叠全部
              </Button>
            </>
          )}
          <Button
            onClick={() =>
              reset(() => {
                setDraftFilters({ status: 'open' });
                setFilters({ status: 'open' });
              })
            }
          >
            重置
          </Button>
          <Button
            type="primary"
            onClick={() => reset(() => setFilters(draftFilters))}
          >
            查询
          </Button>
        </div>
      </Card>

      {selectedList.length > 0 && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            padding: '12px 18px',
            marginBottom: 12,
            borderRadius: 12,
            background: palette.accentSoft,
            border: `1px solid ${palette.accentLine}`,
            flexWrap: 'wrap',
          }}
        >
          <b style={{ color: palette.ink }}>
            已选 {selectedList.length} 个型号
          </b>
          <span style={{ color: palette.sub }}>
            · 来自 {new Set(selectedList.map((r) => r.soId)).size} 张订单
          </span>
          <span style={{ flex: 1 }} />
          <Button onClick={() => setSelected(new Map())}>取消选择</Button>
          {canAssign && (
            <Button onClick={() => setAssigning(selectedList)}>
              指派采购员
            </Button>
          )}
          {canCreate && (
            <Button onClick={() => setSourcing(selectedList)}>选渠道</Button>
          )}
          {canCreate && (
            <Button
              type="primary"
              icon={<ShoppingCartOutlined />}
              disabled={!canGenerate}
              title={canGenerate ? undefined : '已排入草稿的型号不用再生成'}
              onClick={() => setGenerating(selectedList)}
            >
              生成采购单
            </Button>
          )}
        </div>
      )}

      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : empty ? (
        <Card>
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
        </Card>
      ) : view === 'order' ? (
        loading && orders.length === 0 ? (
          <Skeleton active />
        ) : (
          <div style={{ display: 'grid', gap: 12 }}>
            {orders.map((o) => (
              <Card key={o.soId} style={{ padding: 0, overflow: 'hidden' }}>
                {orderHead(o)}
                {!collapsed.has(o.soId) && (
                  <div style={{ padding: '0 12px 12px' }}>
                    <Table<Requirement>
                      rowKey="id"
                      size="middle"
                      columns={lineColumns}
                      dataSource={o.lines}
                      pagination={false}
                      scroll={{ x: 1560 }}
                      rowSelection={rowSelection(o.lines)}
                    />
                  </div>
                )}
              </Card>
            ))}
            <div
              style={{
                display: 'flex',
                justifyContent: 'flex-end',
                gap: 12,
                alignItems: 'center',
              }}
            >
              <span style={{ color: palette.mute }}>共 {total} 张订单</span>
              <Button
                size="small"
                disabled={page <= 1}
                onClick={() => setPage(page - 1)}
              >
                上一页
              </Button>
              <span style={{ color: palette.sub }}>第 {page} 页</span>
              <Button
                size="small"
                disabled={page * pageSize >= total}
                onClick={() => setPage(page + 1)}
              >
                下一页
              </Button>
            </div>
          </div>
        )
      ) : (
        <Table<Requirement>
          rowKey="id"
          columns={[
            {
              title: '来源订单 · 客户',
              dataIndex: 'soNo',
              width: 210,
              fixed: 'left',
              render: (v: string, r) => (
                <div>
                  <a onClick={() => history.push(PATHS.salesOrder(r.soId))}>
                    {v}
                  </a>
                  {sub(
                    palette.mute,
                    [r.customerName, r.customerCountry]
                      .filter(Boolean)
                      .join(' · '),
                  )}
                </div>
              ),
            },
            ...lineColumns,
          ]}
          dataSource={rows}
          loading={loading}
          scroll={{ x: 1780 }}
          rowSelection={rowSelection(rows)}
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
      <div style={{ marginTop: 12, fontSize: 12, color: palette.mute }}>
        {view === 'order'
          ? '订单按其型号最近的更新时间倒序；「去下单」打开对应的草稿采购单填价、确认下单。'
          : '按更新时间倒序；跨订单找同一个型号时用。'}
      </div>

      <SourceModal
        targets={sourcing}
        onClose={() => setSourcing(undefined)}
        onDone={() => {
          setSourcing(undefined);
          message.success('已选定渠道，排入草稿采购单');
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
          message.success(`已生成或追加到 ${ids.length} 张草稿采购单`);
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
            」的草稿采购单（没有时新建）；已在草稿里的型号会改过去，已填的单价保留。没有采购员的型号会归到你名下。
          </div>
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
                  <a onClick={() => setChangeSource((v) => !v)}>
                    {changeSource
                      ? '沿用原来的'
                      : `沿用「${keepSource}」，点这里改`}
                  </a>
                </div>
                {changeSource && (
                  <CounterpartyPicker value={source} onChange={setSource} />
                )}
              </div>
              <div style={{ fontSize: 12, color: palette.mute }}>
                一个型号要向两家买、或交给两位采购员时拆分；拆出的数量会从草稿采购单里扣出，新需求渠道已定时自动排入对应草稿。已下单的数量不能拆。
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
            只影响还没全部下单的部分：草稿采购单上的型号会移到新采购员对同一采购对象的草稿；已下单的仍归原采购单。
          </div>
        </div>
      )}
    </Modal>
  );
};

// ---------------------------------------------------------------- 生成采购单

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
  const drafts = useMemo(
    () =>
      new Set(
        groups
          ?.filter((g) => counterpartyReady(g.chosen))
          .map((g) => keyOf(g.chosen)),
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
      title="生成采购单"
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
            已选 {targets?.length} 个型号，按建议渠道分成 {groups.length}{' '}
            组；确认后每组生成或追加到你对该采购对象的草稿采购单。
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
                  <span>{l.availableQty} 个</span>
                </div>
              ))}
            </div>
          ))}
          {ready && (
            <div style={{ color: palette.ink }}>
              将生成或追加 <b>{drafts}</b>{' '}
              张草稿采购单；没有采购员的型号会归到你名下。
            </div>
          )}
        </div>
      )}
    </Modal>
  );
};

export default RequirementList;
