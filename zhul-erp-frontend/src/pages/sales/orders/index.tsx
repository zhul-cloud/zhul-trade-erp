import {
  ClockCircleOutlined,
  FileDoneOutlined,
  PlusOutlined,
  SearchOutlined,
  WalletOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, DatePicker, Form, Input, Select, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerCell } from '@/components/DocFields';
import SourceNos from '@/components/DocFields/SourceNos';
import EllipsisText, { ellipsisRender } from '@/components/EllipsisText';
import {
  ModalTitle,
  PagedModelsModal,
  TagList,
} from '@/pages/inquiry/customer-inquiry/ModelCells';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { CURRENCIES } from '@/pages/quotation/components';
import { getUserList } from '@/pages/system/user/service';
import { useAppTheme } from '@/theme/AppTheme';
import { recentRange } from '@/utils/dateRange';
import { formatAmount } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import {
  Card,
  MigratedPill,
  orderSourceRefs,
  PATHS,
  Pill,
  RECEIPT_META,
  SalesPageTitle,
} from '../components';
import {
  allOrderModels,
  type OrderListItem,
  type OrderQuery,
  type OrderStats,
  orderApi,
  readBizError,
} from '../service';
import {
  CreateOrderDrawer,
  NewOrderDrawer,
  STOCK,
  StockPill,
  useUserOptions,
} from './dialogs';
import { GoodsBar, goodsText, ProgressPill, ReceiptProgress } from './parts';

interface Filters {
  keyword?: string;
  range?: [Dayjs, Dayjs] | null;
  progressCode?: string;
  stockType?: number;
  customerType?: number;
  currencyCode?: string;
  purchaserId?: number;
  ownerId?: number;
  receiptStatus?: number;
  /** 1-PI 转成、2-手动创建、3-售后补发、4-费用订单 */
  source?: number;
}

/** 收款状态筛选里的「先采购后收款未收款」 */
const PAY_LATER_OPEN = -1;

/** 默认最近 3 个月（清空日期看全部）；从销售统计跳转时用 URL 带的销售日期 */
const initial = (): Filters => {
  const p = new URLSearchParams(window.location.search);
  const from = p.get('dateFrom');
  const to = p.get('dateTo');
  const owner = Number(p.get('ownerId'));
  return {
    range: from && to ? [dayjs(from), dayjs(to)] : recentRange(),
    ownerId: owner > 0 ? owner : undefined,
  };
};

const OrderList: React.FC = () => {
  const { palette } = useAppTheme();
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
  const [newOpen, setNewOpen] = useState(false);
  const [viewing, setViewing] = useState<OrderListItem>();
  const canFromPi = !!access.salesPi;
  const canManual = !!access['sales:order:create'];
  const users = useUserOptions();
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
      receiptStatus:
        filters.receiptStatus === PAY_LATER_OPEN
          ? undefined
          : filters.receiptStatus,
      payLaterOpen: filters.receiptStatus === PAY_LATER_OPEN || undefined,
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

  useEffect(() => {
    orderApi
      .stats()
      .then(setStats)
      .catch(() => setStats(undefined));
    getUserList({ current: 1, pageSize: 200 })
      .then((res) =>
        setOwners(
          res.data.records.map((u) => ({ value: u.id, label: u.name })),
        ),
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
    !filters.receiptStatus &&
    !filters.source &&
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
          <a onClick={() => history.push(PATHS.order(r.id))}>{v}</a>
          {r.migrated && (
            <div style={{ marginTop: 4 }}>
              <MigratedPill migrated />
            </div>
          )}
          {r.source === 3 && (
            <div style={{ marginTop: 4 }}>
              <Pill tone="violet">售后补发</Pill>
            </div>
          )}
          {r.source === 4 && (
            <div style={{ marginTop: 4 }}>
              <Pill tone="orange">费用订单</Pill>
            </div>
          )}
          {sub(r.salesDate ?? '—')}
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
      width: 240,
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
      title: '现货/期货',
      dataIndex: 'stockType',
      width: 100,
      render: (v: number) => <StockPill type={v} />,
    },
    {
      title: '型号 · 数量',
      key: 'qty',
      width: 190,
      render: (_, r) => (
        <div>
          <EllipsisText
            text={r.firstModel}
            style={{ fontFamily: 'monospace' }}
          />
          {sub(
            r.itemCount > 1 ? (
              <>
                共 {r.itemCount} 个型号 · {r.totalQuantity ?? 0} 件 ·{' '}
                <a onClick={() => setViewing(r)}>查看</a>
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
      title: '合计',
      dataIndex: 'totalAmount',
      width: 150,
      align: 'right',
      render: (v: number, r) => (
        <b style={{ fontVariantNumeric: 'tabular-nums' }}>
          {formatAmount(v, r.currencyCode)}
        </b>
      ),
    },
    {
      title: '毛利',
      key: 'profit',
      width: 150,
      align: 'right',
      sorter: true,
      sortOrder: sort.field === 'profit' ? sort.order : null,
      render: (_, r) =>
        r.profit == null ? (
          <span style={{ color: palette.mute }}>—</span>
        ) : (
          <div>
            <b
              style={{
                fontVariantNumeric: 'tabular-nums',
                color: r.profit >= 0 ? palette.green : palette.red,
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
      title: '收款进度',
      key: 'receipt',
      width: 180,
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
      title: '订单状态',
      dataIndex: 'progressCode',
      width: 130,
      render: (v: string, r) => (
        <div>
          <ProgressPill code={v} name={r.progressName} />
          {r.cancelReason && sub(r.cancelReason)}
        </div>
      ),
    },
    {
      title: '货物',
      key: 'goods',
      width: 230,
      render: (_, r) => {
        const g = r.goods;
        if (!g || g.total === 0)
          return (
            <span style={{ fontSize: 12, color: palette.mute }}>
              {r.status === 2 ? '—' : '系统外采购'}
            </span>
          );
        const hint =
          g.shipped >= g.total
            ? { text: '全部出运', color: palette.green }
            : g.shipped + g.handed >= g.total
              ? { text: '全部交货代，等出运', color: palette.cyan }
              : g.received >= g.total
                ? { text: '货已到齐，可以发货通知', color: palette.green }
                : g.inTransit > 0 && g.earliestArrival
                  ? {
                      text: `在途最早 ${dayjs(g.earliestArrival).format('MM-DD')} 到`,
                      color: palette.link,
                    }
                  : g.pendingPurchase > 0
                    ? { text: '还有型号没下采购订单', color: palette.orange }
                    : undefined;
        return (
          <div style={{ display: 'grid', gap: 4 }}>
            <GoodsBar parts={g} total={g.total} />
            <span style={{ fontSize: 12, color: palette.sub }}>
              {goodsText(g)} / {g.total} 件
            </span>
            {hint && sub(hint.text, hint.color)}
          </div>
        );
      },
    },
    {
      title: '采购员',
      key: 'purchasers',
      width: 140,
      render: (_, r) => (
        <div>
          {r.purchaserNames.length > 0 ? r.purchaserNames.join('、') : null}
          {r.unassignedCount > 0 && (
            <div style={{ fontSize: 12, color: palette.orange }}>
              {r.purchaserNames.length > 0
                ? `${r.unassignedCount} 个型号未指定`
                : '未指定'}
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
      width: 70,
      fixed: 'right',
      render: (_, r) => (
        <a onClick={() => history.push(PATHS.order(r.id))}>查看</a>
      ),
    },
  ];

  const stat = (
    icon: React.ReactNode,
    color: string,
    label: string,
    value: React.ReactNode,
    hint: React.ReactNode,
  ) => (
    <Card style={{ padding: 18 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ color, fontSize: 16 }}>{icon}</span>
        <span style={{ color: palette.sub, fontSize: 13 }}>{label}</span>
      </div>
      <div
        style={{
          fontSize: 24,
          fontWeight: 700,
          color: palette.ink,
          margin: '6px 0 2px',
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {value}
      </div>
      <div style={{ fontSize: 12, color: palette.mute }}>{hint}</div>
    </Card>
  );

  const applyFilters = (f: Filters) => {
    form.setFieldsValue({ ...f });
    setFilters(f);
    setPage(1);
  };

  return (
    <div>
      <SalesPageTitle
        description="客户付款后的订单：由 PI 转成或手动创建；按型号跟进采购与发货进度。"
        actions={
          (canFromPi || canManual) && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setNewOpen(true)}
            >
              新建销售订单
            </Button>
          )
        }
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
          {stat(
            <FileDoneOutlined />,
            palette.link,
            '本月订单',
            stats.monthCount,
            `按销售日期 · ${dayjs().month() + 1} 月`,
          )}
          {stat(
            <ClockCircleOutlined />,
            palette.orange,
            '进行中',
            stats.inProgressCount,
            stats.progressCounts.length
              ? stats.progressCounts
                  .map((c) => `${c.name} ${c.count}`)
                  .join(' · ')
              : '没有进行中的订单',
          )}
          {stat(
            <WalletOutlined />,
            palette.green,
            '本月实收人民币',
            formatAmount(stats.monthNetCny, 'CNY'),
            `线上 ${formatAmount(stats.monthOnlineCny, 'CNY')} · 线下 ${formatAmount(stats.monthOfflineCny, 'CNY')}`,
          )}
          {stat(
            <WarningOutlined />,
            stats.unassignedCount > 0 ? palette.red : palette.mute,
            '未指定采购员',
            stats.unassignedCount,
            stats.unassignedCount > 0
              ? '有型号没有采购员，点开订单指定'
              : '所有进行中的型号都有采购员',
          )}
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
              gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
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
                placeholder="订单编号、PI 编号、客户、型号"
              />
            </Form.Item>
            <Form.Item
              name="range"
              label="销售日期"
              style={{ marginBottom: 0, gridColumn: 'span 2' }}
            >
              <DatePicker.RangePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="progressCode"
              label="订单状态"
              style={{ marginBottom: 0 }}
            >
              <Select
                allowClear
                placeholder="全部"
                options={[
                  ...(stats?.progressCounts ?? []).map((c) => ({
                    value: c.code,
                    label: c.name,
                  })),
                  { value: 'COMPLETED', label: '已完成' },
                  { value: 'CANCELLED', label: '已取消' },
                ]}
              />
            </Form.Item>
            <Form.Item
              name="receiptStatus"
              label="收款状态"
              style={{ marginBottom: 0 }}
            >
              <Select
                allowClear
                placeholder="全部"
                options={[
                  ...Object.entries(RECEIPT_META).map(([k, v]) => ({
                    value: Number(k),
                    label: v.label,
                  })),
                  { value: PAY_LATER_OPEN, label: '先采购后收款未收款' },
                ]}
              />
            </Form.Item>
            <Form.Item name="source" label="来源" style={{ marginBottom: 0 }}>
              <Select
                allowClear
                placeholder="全部"
                options={[
                  { value: 1, label: 'PI 生成' },
                  { value: 2, label: '手动建单' },
                  { value: 3, label: '售后补发' },
                  { value: 4, label: '费用订单' },
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
                  { value: STOCK.SPOT, label: '现货' },
                  { value: STOCK.FUTURES, label: '期货' },
                ]}
              />
            </Form.Item>
            <Form.Item
              name="customerType"
              label="新 / 老客户"
              style={{ marginBottom: 0 }}
            >
              <Select
                allowClear
                placeholder="全部"
                options={[
                  { value: 1, label: '新客户' },
                  { value: 2, label: '老客户' },
                ]}
              />
            </Form.Item>
            <Form.Item
              name="currencyCode"
              label="币种"
              style={{ marginBottom: 0 }}
            >
              <Select
                allowClear
                placeholder="全部"
                options={CURRENCIES.map((c) => ({ value: c, label: c }))}
              />
            </Form.Item>
            <Form.Item
              name="ownerId"
              label="业务员"
              style={{ marginBottom: 0 }}
            >
              <Select
                allowClear
                placeholder="全部"
                options={owners}
                showSearch={{ optionFilterProp: 'label' }}
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

      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : !loading && rows.length === 0 && noFilter ? (
        <Card>
          <EmptyHint
            title="还没有销售订单"
            description="客户付款后按 PI 创建订单（也可以在 PI 上点「转成订单」），或手动创建订单"
            actionText={canFromPi || canManual ? '新建销售订单' : undefined}
            onAction={() => setNewOpen(true)}
          />
        </Card>
      ) : (
        <Table<OrderListItem>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: tableScrollX(columns) }}
          locale={{ emptyText: '没有符合条件的订单，换个筛选条件试试' }}
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
        默认列出最近 3
        个月销售的订单（清空销售日期看全部），按销售日期倒序；毛利 = 收入（毛额
        × 结汇汇率）− 实际成本，点表头可按毛利排序；收款进度 = 已到账（毛额）÷
        合计；订单状态取所有型号中最靠前的进度。
      </div>
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
          setCreateOpen(true);
        }}
        onDone={(order) => {
          setNewOpen(false);
          message.success(`已生成销售订单 ${order.soNo}`);
          history.push(PATHS.order(order.id));
        }}
      />
      <CreateOrderDrawer
        open={createOpen}
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
