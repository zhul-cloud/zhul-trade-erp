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
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { CURRENCIES } from '@/pages/quotation/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { Card, PATHS, RECEIPT_META, SalesPageTitle } from '../components';
import {
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
  receiptStatus?: number;
}

const initial = (): Filters => ({
  range: [dayjs().startOf('month'), dayjs().endOf('month')],
});

const OrderList: React.FC = () => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const access = useAccess();
  const [form] = Form.useForm<Filters>();
  const [filters, setFilters] = useState<Filters>(initial);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<OrderListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [stats, setStats] = useState<OrderStats>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [createOpen, setCreateOpen] = useState(false);
  const [newOpen, setNewOpen] = useState(false);
  const canFromPi = !!access.salesPi;
  const canManual = !!access['sales:order:create'];
  const users = useUserOptions();

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
      receiptStatus: filters.receiptStatus,
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
  }, [filters, page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    orderApi
      .stats()
      .then(setStats)
      .catch(() => setStats(undefined));
  }, []);

  const noFilter =
    !filters.keyword &&
    !filters.progressCode &&
    !filters.stockType &&
    !filters.customerType &&
    !filters.currencyCode &&
    !filters.purchaserId &&
    !filters.receiptStatus &&
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
          {sub(r.salesDate ?? '—')}
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
      width: 110,
      render: (_, r) => (
        <div>
          <div>{r.itemCount} 个型号</div>
          {sub(`共 ${r.totalQuantity ?? 0} 件`)}
        </div>
      ),
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
      title: '收款进度',
      key: 'receipt',
      width: 180,
      render: (_, r) => (
        <ReceiptProgress
          received={r.receivedAmount ?? 0}
          total={r.totalAmount}
          status={r.receiptStatus}
        />
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
          g.received >= g.total
            ? { text: '货已到齐，可以安排交货代', color: palette.green }
            : g.inTransit > 0 && g.earliestArrival
              ? {
                  text: `在途最早 ${dayjs(g.earliestArrival).format('MM-DD')} 到`,
                  color: palette.link,
                }
              : g.pendingPurchase > 0
                ? { text: '还有型号没下采购单', color: palette.orange }
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
      title: '来源',
      dataIndex: 'piNo',
      width: 200,
      render: (v: string | undefined, r) =>
        r.piId ? (
          <a
            style={{ whiteSpace: 'nowrap' }}
            onClick={() => history.push(PATHS.pi(r.piId as number))}
          >
            {v}
            {(r.piVersionNo ?? 1) > 1 ? ` · Rev.${r.piVersionNo}` : ''}
          </a>
        ) : (
          <span style={{ color: palette.sub }}>手动创建</span>
        ),
    },
    { title: '业务员', dataIndex: 'ownerName', width: 90 },
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
        crumbs={['销售订单']}
        title="销售订单"
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
                options={Object.entries(RECEIPT_META).map(([k, v]) => ({
                  value: Number(k),
                  label: v.label,
                }))}
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
          scroll={{ x: 2400 }}
          locale={{ emptyText: '没有符合条件的订单，换个筛选条件试试' }}
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
        默认列出本月销售的订单，按销售日期倒序；收款进度 = 已到账（毛额）÷
        合计；订单状态取所有型号中最靠前的进度。
      </div>
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
