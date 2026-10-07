import { SearchOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, DatePicker, Form, Input, Select, Table } from 'antd';
import type { Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerCell } from '@/components/DocFields';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { getUserList } from '@/pages/system/user/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import {
  Card,
  ORDER_META,
  OrderStatusPill,
  PATHS,
  RECEIPT_META,
  ReceiptStatusPill,
  SalesPageTitle,
} from '../components';
import {
  type OrderListItem,
  type OrderQuery,
  orderApi,
  readBizError,
} from '../service';

interface Filters {
  keyword?: string;
  status?: number;
  receiptStatus?: number;
  ownerId?: number;
  range?: [Dayjs, Dayjs];
}

const OrderList: React.FC = () => {
  const { palette } = useAppTheme();
  const [form] = Form.useForm<Filters>();
  const [filters, setFilters] = useState<Filters>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<OrderListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [owners, setOwners] = useState<{ value: number; label: string }[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    const q: OrderQuery = {
      keyword: filters.keyword?.trim() || undefined,
      status: filters.status,
      receiptStatus: filters.receiptStatus,
      ownerId: filters.ownerId,
      createdFrom: filters.range?.[0]?.format('YYYY-MM-DD'),
      createdTo: filters.range?.[1]?.format('YYYY-MM-DD'),
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
    !filters.status &&
    !filters.receiptStatus &&
    !filters.ownerId &&
    !filters.range;

  const columns: TableColumnsType<OrderListItem> = [
    {
      title: '订单编号',
      dataIndex: 'soNo',
      width: 160,
      fixed: 'left',
      render: (v: string, r) => (
        <a onClick={() => history.push(PATHS.order(r.id))}>{v}</a>
      ),
    },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 260,
      render: (v: string, r) => (
        <CustomerCell
          name={v}
          country={r.customerCountry}
          type={r.customerType}
        />
      ),
    },
    { title: '型号数', dataIndex: 'itemCount', width: 80, align: 'right' },
    {
      title: '总数量',
      dataIndex: 'totalQuantity',
      width: 90,
      align: 'right',
      render: (v?: number) => v ?? '—',
    },
    {
      title: '币种 · 合计',
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
      title: '收款状态',
      dataIndex: 'receiptStatus',
      width: 160,
      render: (v: number | undefined, r) => (
        <div>
          <ReceiptStatusPill status={v} />
          {r.receivedAmount != null && r.receivedAmount > 0 && (
            <div
              style={{
                fontSize: 12,
                color: palette.mute,
                fontVariantNumeric: 'tabular-nums',
              }}
            >
              已到账 {formatAmount(r.receivedAmount, r.currencyCode)}
            </div>
          )}
        </div>
      ),
    },
    {
      title: '订单状态',
      dataIndex: 'status',
      width: 180,
      render: (v: number, r) => (
        <div>
          <OrderStatusPill status={v} />
          {r.cancelReason && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              {r.cancelReason}
            </div>
          )}
        </div>
      ),
    },
    {
      title: '来源 PI',
      dataIndex: 'piNo',
      width: 190,
      render: (v: string | undefined, r) =>
        v ? (
          <a onClick={() => history.push(PATHS.pi(r.piId))}>
            {v}
            {r.piVersionNo > 1 ? ` · Rev.${r.piVersionNo}` : ''}
          </a>
        ) : (
          '—'
        ),
    },
    { title: '业务员', dataIndex: 'ownerName', width: 100 },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
      render: (v: string) => formatDateTime(v),
    },
    {
      title: '操作',
      key: 'actions',
      width: 80,
      fixed: 'right',
      render: (_, r) => (
        <a onClick={() => history.push(PATHS.order(r.id))}>查看</a>
      ),
    },
  ];

  return (
    <div>
      <SalesPageTitle
        crumbs={['销售订单']}
        title="销售订单"
        description="客户付款后由 PI 转成；订单创建后不能修改，需要变更时取消订单、在 PI 上出新版本后重新转。"
      />

      <Card style={{ marginBottom: 16, padding: 20 }}>
        <Form
          form={form}
          layout="vertical"
          onFinish={(v) => {
            setFilters(v);
            setPage(1);
          }}
        >
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '2fr 1fr 1fr 1fr 1.6fr auto',
              gap: 12,
              alignItems: 'end',
            }}
          >
            <Form.Item
              name="keyword"
              label="关键词"
              style={{ marginBottom: 0 }}
            >
              <Input
                allowClear
                prefix={<SearchOutlined />}
                placeholder="订单编号、PI 编号、客户、型号"
              />
            </Form.Item>
            <Form.Item
              name="status"
              label="订单状态"
              style={{ marginBottom: 0 }}
            >
              <Select
                allowClear
                placeholder="全部"
                options={Object.entries(ORDER_META).map(([k, v]) => ({
                  value: Number(k),
                  label: v.label,
                }))}
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
              name="range"
              label="创建日期"
              style={{ marginBottom: 0 }}
            >
              <DatePicker.RangePicker style={{ width: '100%' }} />
            </Form.Item>
            <div style={{ display: 'flex', gap: 8 }}>
              <Button
                onClick={() => {
                  form.resetFields();
                  setFilters({});
                  setPage(1);
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
            description="客户付款后，在 PI 上点「转成订单」生成；有水单或已到账即可转"
            actionText="去 PI 列表"
            onAction={() => history.push(PATHS.piList)}
          />
        </Card>
      ) : (
        <Table<OrderListItem>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: 1530 }}
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
    </div>
  );
};

export default OrderList;
