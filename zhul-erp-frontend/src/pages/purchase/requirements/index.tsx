import {
  ClockCircleOutlined,
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
  Form,
  Input,
  InputNumber,
  Modal,
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
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { StockPill, useUserOptions } from '@/pages/sales/orders/dialogs';
import { createSupplierFromChannel } from '@/services/zhul/masterdata';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import {
  Card,
  PATHS,
  PurchasePageTitle,
  ReqStatusPill,
  StatCard,
  SupplierPicker,
  sub,
} from '../components';
import {
  type GenerateGroup,
  type Requirement,
  type RequirementQuery,
  type RequirementStats,
  readBizError,
  requirementApi,
} from '../service';

interface Filters {
  keyword?: string;
  purchaserId?: number;
  supplierId?: number;
  view: 'open' | 'need' | 'all';
  stockType?: number;
}

const initial = (): Filters => ({ view: 'open' });

const RequirementList: React.FC = () => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const access = useAccess();
  const users = useUserOptions();
  const [form] = Form.useForm<Filters>();
  const [filters, setFilters] = useState<Filters>(initial);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<Requirement[]>([]);
  const [total, setTotal] = useState(0);
  const [stats, setStats] = useState<RequirementStats>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [selected, setSelected] = useState<Requirement[]>([]);
  const [splitting, setSplitting] = useState<Requirement>();
  const [assigning, setAssigning] = useState<Requirement[]>();
  const [generating, setGenerating] = useState<Requirement[]>();
  const canSplit = !!access['purchase:requirement:split'];
  const canAssign = !!access['purchase:requirement:assign'];
  const canCreate = !!access['purchase:order:create'];

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    const q: RequirementQuery = {
      keyword: filters.keyword?.trim() || undefined,
      purchaserId: filters.purchaserId,
      supplierId: filters.supplierId,
      view: filters.view,
      stockType: filters.stockType,
      page,
      pageSize,
    };
    try {
      const res = await requirementApi.page(q);
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [filters, page, pageSize]);

  const loadStats = useCallback(() => {
    requirementApi
      .stats()
      .then(setStats)
      .catch(() => setStats(undefined));
  }, []);

  useEffect(() => {
    load();
  }, [load]);
  useEffect(loadStats, [loadStats]);

  const refresh = () => {
    setSelected([]);
    load();
    loadStats();
  };

  const applyFilters = (f: Filters) => {
    form.setFieldsValue({ ...f });
    setFilters({ ...initial(), ...f });
    setPage(1);
    setSelected([]);
  };

  const supplierCell = (r: Requirement) => {
    if (r.suggestedSupplierName) {
      return <div>{r.suggestedSupplierName}</div>;
    }
    if (r.suggestedShopName) {
      return (
        <div>
          <div>
            {r.suggestedChannelName ? `${r.suggestedChannelName} · ` : ''}
            {r.suggestedShopName}
          </div>
          {sub(palette.orange, '没有关联供应商')}
        </div>
      );
    }
    return <span style={{ color: palette.mute }}>没有建议供应商</span>;
  };

  const columns: TableColumnsType<Requirement> = [
    {
      title: '来源订单',
      dataIndex: 'soNo',
      width: 220,
      render: (v: string, r) => (
        <div>
          <a onClick={() => history.push(PATHS.salesOrder(r.soId))}>{v}</a>
          {sub(
            palette.mute,
            [r.salesDate, r.customerName, r.customerCountry]
              .filter(Boolean)
              .join(' · '),
          )}
        </div>
      ),
    },
    {
      title: '型号 · 品牌',
      dataIndex: 'model',
      width: 200,
      render: (v: string, r) => (
        <div>
          <b>{v}</b>
          {r.brand && sub(palette.mute, r.brand)}
        </div>
      ),
    },
    {
      title: '需求数量',
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
      title: '可下单',
      dataIndex: 'availableQty',
      width: 80,
      render: (v: number) => (
        <b style={{ color: v > 0 ? palette.ink : palette.mute }}>{v}</b>
      ),
    },
    {
      title: '目标价（不含税）',
      dataIndex: 'targetPrice',
      width: 140,
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
      title: '建议供应商',
      key: 'supplier',
      width: 180,
      render: (_, r) => supplierCell(r),
    },
    {
      title: '采购员',
      dataIndex: 'purchaserName',
      width: 90,
      render: (v?: string) =>
        v ?? <span style={{ color: palette.orange }}>未指定</span>,
    },
    {
      title: '所在采购单',
      key: 'pos',
      width: 170,
      render: (_, r) =>
        r.purchaseOrders.length === 0 ? (
          <span style={{ color: palette.mute }}>—</span>
        ) : (
          r.purchaseOrders.map((p) => (
            <div key={p.id}>
              <a onClick={() => history.push(PATHS.order(p.id))}>
                {p.poNo ?? `${p.purchaserName ?? ''} → ${p.supplierName ?? ''}`}
              </a>
              {sub(
                palette.mute,
                `${p.poNo ? '已下单' : '草稿采购单'} · ${p.quantity} 个`,
              )}
            </div>
          ))
        ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 150,
      render: (_, r) => (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          <ReqStatusPill status={r.status} name={r.statusName} />
          {r.stockType && <StockPill type={r.stockType} />}
        </div>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 110,
      fixed: 'right',
      render: (_, r) => {
        const open = ['pending', 'draft', 'partial'].includes(r.status);
        return (
          <div style={{ display: 'flex', gap: 10 }}>
            {canSplit && open && <a onClick={() => setSplitting(r)}>拆分</a>}
            {canAssign && open && <a onClick={() => setAssigning([r])}>指派</a>}
          </div>
        );
      },
    },
  ];

  const selectable = (r: Requirement) => r.availableQty > 0;
  const groupsHint = useMemo(() => {
    const keys = new Set(
      selected.map(
        (r) =>
          r.suggestedSupplierName ??
          (r.suggestedShopName ? `店铺 ${r.suggestedShopName}` : '无'),
      ),
    );
    return `· 按建议供应商分为 ${keys.size} 组 · 来自 ${new Set(selected.map((r) => r.soId)).size} 张订单`;
  }, [selected]);

  return (
    <div>
      <PurchasePageTitle
        crumbs={['采购需求']}
        title="采购需求"
        description="订单生成时，有采购员和建议供应商的需求会自动排入草稿采购单；其余的在这里勾选生成。"
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
            hint={`其中 ${stats.inDraft} 条已排入草稿`}
          />
          <StatCard
            icon={<ShoppingCartOutlined />}
            color={stats.need > 0 ? palette.orange : palette.mute}
            label="需要生成采购单"
            value={stats.need}
            hint="只有店铺名称、没有建议供应商或没有采购员"
            hintColor={stats.need > 0 ? palette.orange : undefined}
          />
          <StatCard
            icon={<UserDeleteOutlined />}
            color={stats.unassigned > 0 ? palette.orange : palette.mute}
            label="未指定采购员"
            value={stats.unassigned}
            hint="生成采购单时归到生成的人名下"
          />
          <StatCard
            icon={<FileTextOutlined />}
            color={palette.violet}
            label="草稿采购单"
            value={
              <a onClick={() => history.push(PATHS.orders)}>{stats.drafts}</a>
            }
            hint="确认下单后订单型号才会变为「已下单」"
          />
        </div>
      )}

      <Card style={{ marginBottom: 16, padding: 20 }}>
        <Form
          form={form}
          layout="vertical"
          initialValues={initial()}
          onFinish={applyFilters}
        >
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
              gap: 12,
              alignItems: 'end',
            }}
          >
            <Form.Item
              name="keyword"
              label="关键词"
              style={{ marginBottom: 0, gridColumn: 'span 2' }}
            >
              <Input
                allowClear
                prefix={<SearchOutlined />}
                placeholder="订单编号、型号、客户"
              />
            </Form.Item>
            <Form.Item
              name="purchaserId"
              label="采购员"
              style={{ marginBottom: 0 }}
            >
              <Select
                allowClear
                placeholder="全部"
                options={users}
                showSearch={{ optionFilterProp: 'label' }}
              />
            </Form.Item>
            <Form.Item
              name="supplierId"
              label="建议供应商"
              style={{ marginBottom: 0 }}
            >
              <SupplierPicker
                placeholder="全部"
                onChange={(v) => form.setFieldValue('supplierId', v)}
              />
            </Form.Item>
            <Form.Item name="view" label="状态" style={{ marginBottom: 0 }}>
              <Select
                options={[
                  { value: 'open', label: '还没全部下单' },
                  { value: 'need', label: '需要生成采购单' },
                  { value: 'all', label: '全部（含已下单、已关闭）' },
                ]}
              />
            </Form.Item>
            <Form.Item
              name="stockType"
              label="现货 / 期货"
              style={{ marginBottom: 0 }}
            >
              <Select
                allowClear
                placeholder="全部"
                options={[
                  { value: 1, label: '现货' },
                  { value: 2, label: '期货' },
                ]}
              />
            </Form.Item>
            <div
              style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}
            >
              <Button
                onClick={() => {
                  form.resetFields();
                  applyFilters(initial());
                }}
              >
                重置
              </Button>
              <Button type="primary" htmlType="submit">
                查询
              </Button>
            </div>
          </div>
        </Form>
      </Card>

      {selected.length > 0 && (
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
          <b style={{ color: palette.ink }}>已选 {selected.length} 条需求</b>
          <span style={{ color: palette.sub }}>{groupsHint}</span>
          <span style={{ flex: 1 }} />
          <Button onClick={() => setSelected([])}>取消选择</Button>
          {canAssign && (
            <Button onClick={() => setAssigning(selected)}>指派采购员</Button>
          )}
          {canCreate && (
            <Button
              type="primary"
              icon={<ShoppingCartOutlined />}
              onClick={() => setGenerating(selected)}
            >
              生成采购单
            </Button>
          )}
        </div>
      )}

      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : !loading &&
        rows.length === 0 &&
        filters.view === 'open' &&
        !filters.keyword ? (
        <Card>
          <EmptyHint
            title="没有待采购的需求"
            description="销售订单生成后，型号会自动出现在这里"
          />
        </Card>
      ) : (
        <Table<Requirement>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: 1560 }}
          locale={{ emptyText: '没有符合条件的需求，换个筛选条件试试' }}
          rowSelection={
            canCreate || canAssign
              ? {
                  selectedRowKeys: selected.map((r) => r.id),
                  getCheckboxProps: (r) => ({
                    disabled: !selectable(r),
                    title: selectable(r)
                      ? undefined
                      : '已排入采购单，要换供应商请到草稿采购单里「改到其他供应商」',
                  }),
                  onChange: (_, list) => setSelected(list),
                }
              : undefined
          }
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
        默认列出还没全部下单的需求（含草稿中），按订单销售日期从早到晚；已排入草稿的不能勾选，要换供应商请到草稿采购单里「改到其他供应商」。
      </div>

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
  const [supplierId, setSupplierId] = useState<number>();
  const [supplierName, setSupplierName] = useState('');
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
      setSupplierId(undefined);
      setSupplierName('');
    }
  }, [target]);

  const submit = async () => {
    if (!target || !qty) return;
    setBusy(true);
    try {
      await requirementApi.split(target.id, {
        quantity: qty,
        purchaserId,
        supplierId,
      });
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const keepSupplier =
    target?.suggestedSupplierName ??
    (target?.suggestedShopName
      ? `店铺 ${target.suggestedShopName}`
      : '没有建议供应商');
  return (
    <Modal
      open={!!target}
      title="拆分采购需求"
      width={620}
      onCancel={onClose}
      okText="拆分"
      okButtonProps={{ disabled: !qty || qty > max, loading: busy }}
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
                  gridTemplateColumns: '1fr 1fr 1.4fr',
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
                <div>
                  <div
                    style={{
                      fontSize: 12,
                      color: palette.sub,
                      marginBottom: 6,
                    }}
                  >
                    建议供应商
                  </div>
                  <SupplierPicker
                    style={{ width: '100%' }}
                    placeholder={`沿用：${keepSupplier}`}
                    value={supplierId}
                    label={supplierName}
                    onChange={(v, name) => {
                      setSupplierId(v);
                      setSupplierName(name);
                    }}
                  />
                </div>
              </div>
              {qty ? (
                <div
                  style={{
                    padding: '10px 14px',
                    borderRadius: 10,
                    background: palette.inset,
                    display: 'grid',
                    gap: 4,
                  }}
                >
                  <div>
                    原需求 <b>{target.quantity - qty} 个</b>
                    <span style={{ color: palette.mute }}>
                      {' '}
                      · {keepSupplier}
                    </span>
                  </div>
                  <div>
                    新需求 <b style={{ color: palette.link }}>{qty} 个</b>
                    <span style={{ color: palette.mute }}>
                      {' '}
                      · {supplierName || keepSupplier}
                    </span>
                  </div>
                </div>
              ) : null}
              <div style={{ fontSize: 12, color: palette.mute }}>
                一个型号要向两家供应商买、或交给两位采购员时拆分；拆出的数量会从草稿采购单里扣出，新需求有供应商时自动排入对应草稿。已下单的数量不能拆。
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
              : `已选 ${targets.length} 条需求`}
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
            只影响还没全部下单的部分：草稿采购单上的型号会移到新采购员对同一供应商的草稿；已下单的仍归原采购单。
          </div>
        </div>
      )}
    </Modal>
  );
};

// ---------------------------------------------------------------- 生成采购单

interface GroupState extends GenerateGroup {
  chosenId?: number;
  chosenName?: string;
}

const GenerateModal: React.FC<{
  targets?: Requirement[];
  onClose: () => void;
  onDone: (poIds: number[]) => void;
}> = ({ targets, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [groups, setGroups] = useState<GroupState[]>();
  const [busy, setBusy] = useState(false);
  const [converting, setConverting] = useState<string>();
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
            chosenId: g.supplierId ?? undefined,
            chosenName: g.supplierName,
          })),
        ),
      )
      .catch((e) => {
        message.error(readBizError(e).message);
        closeRef.current();
      });
  }, [targets, message]);

  const set = (key: string, patch: Partial<GroupState>) =>
    setGroups((gs) => gs?.map((g) => (g.key === key ? { ...g, ...patch } : g)));

  const convert = async (g: GroupState) => {
    if (!g.shopName) return;
    setConverting(g.key);
    try {
      let res = await createSupplierFromChannel(g.shopName);
      if (res.duplicate && res.existingSupplier) {
        set(g.key, {
          chosenId: res.existingSupplier.id,
          chosenName: res.existingSupplier.name,
        });
        message.info(
          `已有同名供应商「${res.existingSupplier.name}」，直接使用`,
        );
        return;
      }
      if (!res.createdSupplier) {
        res = await createSupplierFromChannel(g.shopName, true);
      }
      const s = res.createdSupplier;
      if (s) {
        set(g.key, { chosenId: s.id, chosenName: s.name });
        message.success(`已把「${g.shopName}」转为供应商，其余资料可以以后补`);
      }
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setConverting(undefined);
    }
  };

  const ready = !!groups && groups.every((g) => g.chosenId);
  const drafts = new Set(groups?.map((g) => g.chosenId).filter(Boolean)).size;

  const submit = async () => {
    if (!groups) return;
    setBusy(true);
    try {
      const bySupplier = new Map<number, number[]>();
      for (const g of groups) {
        const list = bySupplier.get(g.chosenId as number) ?? [];
        list.push(...g.lines.map((l) => l.requirementId));
        bySupplier.set(g.chosenId as number, list);
      }
      const ids = await requirementApi.generate(
        [...bySupplier.entries()].map(([supplierId, requirementIds]) => ({
          supplierId,
          requirementIds,
        })),
      );
      onDone(ids);
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
            已选 {targets?.length} 条需求，按建议供应商分成 {groups.length}{' '}
            组；确认后每组生成或追加到你对该供应商的草稿采购单。
          </div>
          {groups.map((g) => (
            <div
              key={g.key}
              style={{
                padding: '12px 14px',
                borderRadius: 12,
                background: palette.inset,
                border: `1px solid ${g.chosenId ? palette.hairline : palette.orange}`,
                display: 'grid',
                gap: 8,
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  flexWrap: 'wrap',
                }}
              >
                <b style={{ color: palette.ink }}>{g.title}</b>
                <span style={{ fontSize: 12, color: palette.mute }}>
                  {g.lines.length} 条
                  {g.shopName && !g.supplierId && ' · 没有同名供应商'}
                  {g.matchedByName && ' · 已对应同名供应商'}
                </span>
                <span style={{ flex: 1 }} />
                {g.shopName && !g.chosenId && (
                  <Button
                    size="small"
                    type="primary"
                    loading={converting === g.key}
                    onClick={() => convert(g)}
                  >
                    转为供应商
                  </Button>
                )}
                <SupplierPicker
                  style={{ width: 240 }}
                  placeholder={g.supplierId ? '改选供应商' : '选择供应商'}
                  value={g.chosenId}
                  label={g.chosenName}
                  onChange={(v, name) =>
                    set(g.key, { chosenId: v, chosenName: name })
                  }
                />
              </div>
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
          <Alert
            type="info"
            showIcon
            title="没有采购员的需求，采购员会改为你；店铺「转为供应商」时预填店铺名称，其余资料可以以后补。"
          />
          {ready && (
            <div style={{ color: palette.ink }}>
              将生成或追加 <b>{drafts}</b> 张草稿采购单
            </div>
          )}
        </div>
      )}
    </Modal>
  );
};

export default RequirementList;
