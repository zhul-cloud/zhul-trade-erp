import { DownloadOutlined, SearchOutlined } from '@ant-design/icons';
import { Link } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, DatePicker, Form, Input, Select, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { SignedMoney } from '@/pages/finance/costingParts';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { WarehousePageTitle } from '../components';
import { FilterCard, ModelCell, useWarehouses } from '../inventoryParts';
import { type LedgerRow, stockApi } from '../inventoryService';
import { readBizError } from '../service';

interface Filters {
  month: Dayjs;
  warehouseId?: number;
  keyword?: string;
}

/** 仓库管理 → 库存台账（spec warehouse/inventory「库存台账」，原型 I3、L1）：期末金额 = 期初 + 入库 − 出库 + 成本调整 */
const LedgerPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const warehouses = useWarehouses();
  const [form] = Form.useForm();
  const initial = (): Filters => ({ month: dayjs() });
  const [filters, setFilters] = useState<Filters>(initial);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<LedgerRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState<string>();
  const [period, setPeriod] =
    useState<Awaited<ReturnType<typeof stockApi.ledgerPeriod>>>();

  const query = {
    month: filters.month.format('YYYY-MM'),
    warehouseId: filters.warehouseId,
    keyword: filters.keyword?.trim() || undefined,
  };
  const queryKey = JSON.stringify(query);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const q = JSON.parse(queryKey);
      const [res, st] = await Promise.all([
        stockApi.ledger({ ...q, page, pageSize }),
        stockApi.ledgerPeriod(q.month),
      ]);
      setRows(res.records);
      setPeriod(st);
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

  const from = filters.month.startOf('month');
  /** 数量链到对应的流水：期初看本月之前，本月入库、出库与期末看本月 */
  const movesLink = (r: LedgerRow, v: number, before?: boolean) => {
    if (!v) return <span style={{ color: palette.mute }}>0</span>;
    const range = before
      ? `to=${from.subtract(1, 'day').format('YYYY-MM-DD')}&from=2000-01-01`
      : `from=${from.format('YYYY-MM-DD')}&to=${from.endOf('month').format('YYYY-MM-DD')}`;
    const wh = filters.warehouseId ? `&warehouseId=${filters.warehouseId}` : '';
    return (
      <Link
        to={`/warehouse/moves?productId=${r.productId}&model=${encodeURIComponent(r.model)}&${range}${wh}`}
      >
        {v}
      </Link>
    );
  };
  const money = (v: number) => formatAmount(v ?? 0, 'CNY');

  const columns: TableColumnsType<LedgerRow> = [
    {
      title: '品牌 · 型号',
      dataIndex: 'model',
      width: 220,
      fixed: 'left',
      render: (_, r) => (
        <ModelCell model={r.model} brand={r.brand} category={r.category} />
      ),
    },
    {
      title: '期初数量',
      dataIndex: 'openingQty',
      width: 90,
      render: (v: number, r) => movesLink(r, v, true),
    },
    {
      title: '期初金额',
      dataIndex: 'openingAmount',
      width: 120,
      render: money,
    },
    {
      title: '本月入库',
      dataIndex: 'inQty',
      width: 90,
      render: (v: number, r) => movesLink(r, v),
    },
    { title: '入库金额', dataIndex: 'inAmount', width: 120, render: money },
    {
      title: '本月出库',
      dataIndex: 'outQty',
      width: 90,
      render: (v: number, r) => movesLink(r, v),
    },
    { title: '出库金额', dataIndex: 'outAmount', width: 120, render: money },
    {
      title: '成本调整',
      dataIndex: 'adjustAmount',
      width: 110,
      render: (v: number) => <SignedMoney value={v} />,
    },
    {
      title: '期末数量',
      dataIndex: 'closingQty',
      width: 90,
      render: (v: number) => <b>{v}</b>,
    },
    {
      title: '期末金额',
      dataIndex: 'closingAmount',
      width: 120,
      render: money,
    },
  ];

  const sum = (k: keyof LedgerRow) =>
    rows.reduce((s, r) => s + Number(r[k] ?? 0), 0);

  return (
    <div>
      <WarehousePageTitle />
      <FilterCard
        form={form}
        initialValues={initial()}
        onSearch={(v) => {
          setFilters(v as unknown as Filters);
          setPage(1);
        }}
        onReset={() => {
          setFilters(initial());
          setPage(1);
        }}
      >
        <Form.Item name="month" label="月份" style={{ marginBottom: 0 }}>
          <DatePicker
            picker="month"
            allowClear={false}
            style={{ width: '100%' }}
            disabledDate={(d) => d.isAfter(dayjs(), 'month')}
          />
        </Form.Item>
        <Form.Item name="warehouseId" label="仓库" style={{ marginBottom: 0 }}>
          <Select
            allowClear
            placeholder="全部仓库"
            options={warehouses.map((w) => ({ value: w.id, label: w.name }))}
          />
        </Form.Item>
        <Form.Item
          name="keyword"
          label="关键词"
          style={{ marginBottom: 0, gridColumn: 'span 2' }}
        >
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="型号、品牌"
          />
        </Form.Item>
      </FilterCard>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Card style={{ padding: 16 }}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: 12,
              gap: 12,
              flexWrap: 'wrap',
            }}
          >
            {period?.status === 1 ? (
              <span style={{ color: palette.sub, fontSize: 13 }}>
                <Pill tone="green">已结账</Pill> {period.closedByName}{' '}
                {formatDateTime(period.closedAt).slice(0, 16)}{' '}
                结账，显示结账时的快照；之后的成本调整记在当前未结账月份
              </span>
            ) : (
              <span style={{ color: palette.sub, fontSize: 13 }}>
                按单据的业务日期归月；期末金额 = 期初 + 入库 − 出库 + 成本调整
                {filters.warehouseId ? '' : '（全部仓库不含仓库之间的调拨）'}
                ；点数量查看对应流水
                {period && (
                  <>
                    {' '}
                    <Pill tone="orange">{period.month} 未结账</Pill>
                  </>
                )}
              </span>
            )}
            <Button
              icon={<DownloadOutlined />}
              loading={exporting}
              disabled={total === 0}
              onClick={async () => {
                setExporting(true);
                try {
                  await stockApi.exportLedger(query);
                } catch (e) {
                  message.error(readBizError(e).message);
                } finally {
                  setExporting(false);
                }
              }}
            >
              导出 Excel
            </Button>
          </div>
          <Table<LedgerRow>
            rowKey={(r) => r.productId}
            size="middle"
            loading={loading}
            columns={columns}
            dataSource={rows}
            scroll={{ x: tableScrollX(columns) }}
            locale={{
              emptyText: filters.warehouseId
                ? `${warehouses.find((w) => w.id === filters.warehouseId)?.name ?? '该仓库'} 在 ${query.month} 没有库存往来`
                : '这个月没有库存',
            }}
            summary={() =>
              rows.length > 0 ? (
                <Table.Summary.Row>
                  <Table.Summary.Cell index={0}>
                    <b>{total > rows.length ? '本页合计' : '合计'}</b>
                  </Table.Summary.Cell>
                  {(
                    [
                      ['openingQty', false],
                      ['openingAmount', true],
                      ['inQty', false],
                      ['inAmount', true],
                      ['outQty', false],
                      ['outAmount', true],
                      ['adjustAmount', true],
                      ['closingQty', false],
                      ['closingAmount', true],
                    ] as const
                  ).map(([k, isMoney], i) => (
                    <Table.Summary.Cell key={k} index={i + 1}>
                      <b>
                        {k === 'adjustAmount' ? (
                          <SignedMoney value={sum(k)} />
                        ) : isMoney ? (
                          money(sum(k))
                        ) : (
                          sum(k)
                        )}
                      </b>
                    </Table.Summary.Cell>
                  ))}
                </Table.Summary.Row>
              ) : null
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
        </Card>
      )}
    </div>
  );
};

export default LedgerPage;
