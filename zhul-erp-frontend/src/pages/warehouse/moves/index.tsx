import { CloseOutlined, SearchOutlined } from '@ant-design/icons';
import { history, Link, useLocation } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, Checkbox, DatePicker, Form, Input, Select, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { FilterField, MainCard, PageHeader } from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import {
  bizText,
  FilterCard,
  ModelCell,
  SignedQty,
  StockStatusPill,
  useWarehouses,
} from '../inventoryParts';
import {
  BIZ_OPTIONS,
  docHref,
  type InvMove,
  OP_OPTIONS,
  stockApi,
} from '../inventoryService';
import { readBizError } from '../service';

const OP_TONE: Record<number, 'green' | 'red' | 'violet' | 'accent' | 'gray'> =
  {
    1: 'green',
    2: 'red',
    3: 'violet',
    4: 'violet',
    5: 'accent',
    6: 'accent',
    7: 'gray',
    8: 'gray',
  };

interface Filters {
  range: [Dayjs, Dayjs] | null;
  keyword?: string;
  warehouseId?: number;
  opType?: number;
  bizType?: string;
}

/** 仓库管理 → 库存流水（spec warehouse/inventory「库存流水」，原型 I2）：只增不改，作废、冲销生成反向流水 */
const MovesPage: React.FC = () => {
  const { palette } = useAppTheme();
  const location = useLocation();
  const url = new URLSearchParams(location.search);
  const urlProduct = url.get('productId')
    ? Number(url.get('productId'))
    : undefined;
  const urlModel = url.get('model') ?? '';
  const warehouses = useWarehouses();
  const [form] = Form.useForm();
  const initial = (): Filters => ({
    // 从库存查询点「流水」进来看这个商品的全部流水，不限时间
    range: url.get('from')
      ? [dayjs(url.get('from')), dayjs(url.get('to') ?? undefined)]
      : urlProduct
        ? null
        : [dayjs().startOf('month'), dayjs()],
    keyword: url.get('keyword') ?? undefined,
    warehouseId: url.get('warehouseId')
      ? Number(url.get('warehouseId'))
      : undefined,
  });
  const [filters, setFilters] = useState<Filters>(initial);
  const [productId, setProductId] = useState(urlProduct);
  const [withReservation, setWithReservation] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<InvMove[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  const query = {
    dateFrom: filters.range?.[0]?.format('YYYY-MM-DD'),
    dateTo: filters.range?.[1]?.format('YYYY-MM-DD'),
    keyword: filters.keyword?.trim() || undefined,
    warehouseId: filters.warehouseId,
    opType: filters.opType,
    bizType: filters.bizType,
    productId,
    includeReservation: withReservation || undefined,
  };
  const queryKey = JSON.stringify(query);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const res = await stockApi.moves({
        ...JSON.parse(queryKey),
        page,
        pageSize,
      });
      setRows(res.records);
      setTotal(res.total);
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

  const columns: TableColumnsType<InvMove> = [
    {
      title: '发生时间',
      dataIndex: 'moveTime',
      width: 150,
      fixed: 'left',
      render: (v: string) => formatDateTime(v).slice(0, 16),
    },
    {
      title: '业务类型',
      dataIndex: 'bizName',
      width: 150,
      render: (_: string, r) => (
        <b style={{ color: palette.ink }}>{bizText(r)}</b>
      ),
    },
    {
      title: '操作类型',
      dataIndex: 'opName',
      width: 90,
      render: (v: string, r) => (
        <Pill tone={OP_TONE[r.opType] ?? 'gray'}>{v}</Pill>
      ),
    },
    {
      title: '来源单号',
      dataIndex: 'docNo',
      width: 170,
      render: (v: string | undefined, r) => {
        const href = docHref(r.docType, r.docId);
        return (
          <div>
            {href ? <Link to={href}>{v}</Link> : v || '—'}
            {r.targetNo && (
              <div style={{ fontSize: 12, color: palette.mute }}>
                {r.targetType === 1 ? '订单' : '退货单'} {r.targetNo}
              </div>
            )}
          </div>
        );
      },
    },
    {
      title: '品牌 · 型号',
      dataIndex: 'model',
      width: 200,
      render: (_, r) => (
        <ModelCell model={r.model} brand={r.brand} category={r.category} />
      ),
    },
    { title: '仓库', dataIndex: 'warehouseName', width: 130 },
    { title: '批次', dataIndex: 'batchNo', width: 170 },
    {
      title: '状态',
      dataIndex: 'stockStatus',
      width: 70,
      render: (v: number, r) => (
        <StockStatusPill status={v} name={r.stockStatusName} />
      ),
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      width: 70,
      render: (v: number) => <SignedQty value={v} />,
    },
    {
      title: '单价（¥）',
      dataIndex: 'unitPriceCny',
      width: 110,
      render: (v: number) => Number(v ?? 0).toFixed(6),
    },
    {
      title: '金额（¥）',
      dataIndex: 'amountCny',
      width: 120,
      render: (v: number) => formatAmount(v, 'CNY'),
    },
    {
      title: '操作人',
      dataIndex: 'operatorName',
      width: 100,
      render: (v?: string) => v || '系统',
    },
  ];

  const apply = (f: Filters) => {
    setFilters(f);
    setPage(1);
  };

  return (
    <div>
      <PageHeader help="流水只增不改；作废、冲销会生成一笔反向流水" />
      <FilterCard
        form={form}
        initialValues={initial()}
        onSearch={(v) => apply(v as unknown as Filters)}
        onReset={() => {
          setProductId(undefined);
          history.replace('/warehouse/moves');
          apply({ range: [dayjs().startOf('month'), dayjs()] });
          form.setFieldsValue({ range: [dayjs().startOf('month'), dayjs()] });
        }}
        extra={
          productId ? (
            <div style={{ marginTop: 12 }}>
              <Button
                size="small"
                icon={<CloseOutlined />}
                iconPlacement="end"
                onClick={() => {
                  setProductId(undefined);
                  setPage(1);
                }}
              >
                只看 {urlModel || '这个商品'}
              </Button>
            </div>
          ) : null
        }
      >
        <FilterField label="时间" span={2} htmlFor="wh-move-range">
          <Form.Item name="range" noStyle>
            <DatePicker.RangePicker
              id={{ start: 'wh-move-range' }}
              style={{ width: '100%' }}
            />
          </Form.Item>
        </FilterField>
        <FilterField label="关键词" span={2}>
          <Form.Item name="keyword" noStyle>
            <Input
              aria-label="关键词"
              allowClear
              prefix={<SearchOutlined />}
              onPressEnter={() => form.submit()}
              placeholder="型号、单号、批次号"
            />
          </Form.Item>
        </FilterField>
        <FilterField label="仓库">
          <Form.Item name="warehouseId" noStyle>
            <Select
              aria-label="仓库"
              allowClear
              placeholder="全部仓库"
              options={warehouses.map((w) => ({ value: w.id, label: w.name }))}
            />
          </Form.Item>
        </FilterField>
        <FilterField label="操作类型">
          <Form.Item name="opType" noStyle>
            <Select
              aria-label="操作类型"
              allowClear
              placeholder="全部"
              options={OP_OPTIONS.filter((o) => withReservation || o.value < 7)}
            />
          </Form.Item>
        </FilterField>
        <FilterField label="业务类型">
          <Form.Item name="bizType" noStyle>
            <Select
              aria-label="业务类型"
              allowClear
              placeholder="全部"
              options={BIZ_OPTIONS}
            />
          </Form.Item>
        </FilterField>
      </FilterCard>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <MainCard
          left={`共 ${total} 条`}
          right={
            <Checkbox
              checked={withReservation}
              onChange={(e) => {
                setWithReservation(e.target.checked);
                setPage(1);
              }}
            >
              含预占变动
            </Checkbox>
          }
        >
          <Table<InvMove>
            rowKey="id"
            size="middle"
            loading={loading}
            columns={columns}
            dataSource={rows}
            scroll={{ x: tableScrollX(columns) }}
            locale={{ emptyText: '没有符合条件的流水' }}
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
        </MainCard>
      )}
    </div>
  );
};

export default MovesPage;
