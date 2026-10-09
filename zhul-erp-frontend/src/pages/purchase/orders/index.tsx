import {
  CarOutlined,
  FallOutlined,
  FileTextOutlined,
  SearchOutlined,
  ShoppingCartOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import type { TableColumnsType, TableProps } from 'antd';
import { Button, DatePicker, Form, Input, Select, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { useUserOptions } from '@/pages/sales/orders/dialogs';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import {
  auditColumns,
  BargainText,
  Card,
  PATHS,
  Pill,
  PO_STATUS,
  PoStatusPill,
  PurchasePageTitle,
  SHIP_PROGRESS,
  ShipProgressPill,
  SourceCell,
  StatCard,
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
  supplierId?: number;
  purchaserId?: number;
  range?: [Dayjs, Dayjs] | null;
}

type Sort = { field?: string; order?: 'ascend' | 'descend' };

const PurchaseOrderList: React.FC = () => {
  const { palette } = useAppTheme();
  const users = useUserOptions();
  const [form] = Form.useForm<Filters>();
  const [filters, setFilters] = useState<Filters>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
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
      supplierId: filters.supplierId,
      purchaserId: filters.purchaserId,
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
    !filters.supplierId &&
    !filters.purchaserId &&
    !filters.range;

  const columns: TableColumnsType<PoListItem> = [
    {
      title: '采购单 · 下单日期',
      dataIndex: 'poNo',
      width: 190,
      fixed: 'left',
      render: (v: string | undefined, r) => (
        <div>
          <a onClick={() => history.push(PATHS.order(r.id))}>{v ?? '草稿'}</a>
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
      title: '型号数',
      dataIndex: 'itemCount',
      width: 90,
      sorter: true,
      sortOrder: sortOf('itemCount'),
      render: (v: number) => `${v} 个型号`,
    },
    {
      title: '总数量',
      dataIndex: 'totalQuantity',
      width: 90,
      sorter: true,
      sortOrder: sortOf('totalQuantity'),
      render: (v: number) => `${v} 件`,
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
      title: '来源订单',
      key: 'orders',
      width: 180,
      render: (_, r) => (
        <div>
          {r.orders.map((o) => (
            <div key={o.id}>
              <a onClick={() => history.push(PATHS.salesOrder(o.id))}>
                {o.soNo}
              </a>
            </div>
          ))}
          {r.orderCancelledCount > 0 && <Pill tone="red">来源订单已取消</Pill>}
        </div>
      ),
    },
    { title: '采购员', dataIndex: 'purchaserName', width: 90 },
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
        <a onClick={() => history.push(PATHS.order(r.id))}>
          {r.status === PO_STATUS.DRAFT ? '编辑' : '查看'}
        </a>
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

  return (
    <div>
      <PurchasePageTitle
        crumbs={['采购单']}
        title="采购单"
        description="向供应商下的单：记录实际采购价、付款条件与供应商合同；砍价 = 目标价 − 不含税采购价。"
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
            icon={<ShoppingCartOutlined />}
            color={palette.link}
            label="本月已下单"
            value={formatAmount(stats.monthOrderedCny, 'CNY')}
            hint={`共 ${stats.monthOrderedCount} 张采购单`}
          />
          <StatCard
            icon={<FallOutlined />}
            color={palette.green}
            label="本月砍价合计"
            value={formatAmount(stats.monthBargain, 'CNY')}
            hint={
              stats.monthBargainRate == null
                ? '本月还没有可比较的采购单'
                : `整体砍价率 ${stats.monthBargainRate.toFixed(2)}%（按目标金额加权）`
            }
            hintColor={palette.green}
          />
          <button
            type="button"
            onClick={() =>
              applyFilters({
                status: undefined,
                shipProgress: stats.overdueShip > 0 ? 'OVERDUE' : 'UNSHIPPED',
              })
            }
            aria-label="筛出要催发货的采购单"
            style={{
              all: 'unset',
              cursor: 'pointer',
              display: 'block',
            }}
          >
            <StatCard
              icon={<CarOutlined />}
              color={stats.overdueShip > 0 ? palette.orange : palette.link}
              label="待发货"
              value={`${stats.pendingShip} 张`}
              hint={
                stats.overdueShip > 0
                  ? `其中 ${stats.overdueShip} 张已过预计发货日期，点击筛出来催货`
                  : '已下单、供应商还有没发的'
              }
              hintColor={stats.overdueShip > 0 ? palette.orange : undefined}
            />
          </button>
          <StatCard
            icon={<FileTextOutlined />}
            color={palette.violet}
            label="草稿"
            value={stats.drafts}
            hint={
              stats.staleDrafts > 0
                ? `${stats.staleDrafts} 张已放超过 3 天`
                : '确认下单后订单型号才会变为「已下单」'
            }
            hintColor={stats.staleDrafts > 0 ? palette.orange : undefined}
          />
          <StatCard
            icon={<WarningOutlined />}
            color={stats.orderCancelled > 0 ? palette.red : palette.mute}
            label="来源订单已取消"
            value={stats.orderCancelled}
            hint={
              stats.orderCancelled > 0
                ? '请决定取消采购单或保留货物'
                : '没有需要处理的采购单'
            }
            hintColor={stats.orderCancelled > 0 ? palette.red : undefined}
          />
        </div>
      )}

      <Card style={{ marginBottom: 16, padding: 20 }}>
        <Form form={form} layout="vertical" onFinish={applyFilters}>
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
                placeholder="采购单编号、供应商或店铺、型号、订单编号"
              />
            </Form.Item>
            <Form.Item name="status" label="状态" style={{ marginBottom: 0 }}>
              <Select
                allowClear
                placeholder="全部"
                options={[
                  { value: PO_STATUS.DRAFT, label: '草稿' },
                  { value: PO_STATUS.ORDERED, label: '已下单' },
                  { value: PO_STATUS.CANCELLED, label: '已取消' },
                ]}
              />
            </Form.Item>
            <Form.Item
              name="shipProgress"
              label="发货进度"
              style={{ marginBottom: 0 }}
            >
              <Select allowClear placeholder="全部" options={SHIP_PROGRESS} />
            </Form.Item>
            <Form.Item
              name="supplierId"
              label="供应商"
              style={{ marginBottom: 0 }}
            >
              <SupplierPicker
                placeholder="全部"
                onChange={(v) => form.setFieldValue('supplierId', v)}
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
              name="range"
              label="下单日期"
              style={{ marginBottom: 0, gridColumn: 'span 2' }}
            >
              <DatePicker.RangePicker style={{ width: '100%' }} />
            </Form.Item>
            <div
              style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}
            >
              <Button
                onClick={() => {
                  form.resetFields();
                  applyFilters({});
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
            title="还没有采购单"
            description="销售订单生成后会自动排入草稿采购单，也可以在采购需求里勾选生成"
            actionText="去采购需求"
            onAction={() => history.push(PATHS.requirements)}
          />
        </Card>
      ) : (
        <Table<PoListItem>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: 2180 }}
          onChange={onTableChange}
          locale={{ emptyText: '没有符合条件的采购单，换个筛选条件试试' }}
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
    </div>
  );
};

export default PurchaseOrderList;
