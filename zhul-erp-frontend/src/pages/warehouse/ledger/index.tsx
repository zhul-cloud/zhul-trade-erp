import { DownloadOutlined, SearchOutlined } from '@ant-design/icons';
import { history, Link, useLocation } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  DatePicker,
  Form,
  Input,
  Segmented,
  Select,
  Table,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import {
  FilterField,
  HelpTip,
  MainCard,
  PageHeader,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { SignedMoney } from '@/pages/finance/costingParts';
import { Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { categoryApi } from '@/pages/product/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { FilterCard, ModelCell, useWarehouses } from '../inventoryParts';
import {
  type CategoryLedger,
  type LedgerRow,
  stockApi,
} from '../inventoryService';
import { readBizError } from '../service';
import { CategoryLedgerTable } from './CategoryLedgerTable';

interface Filters {
  month: Dayjs;
  warehouseId?: number;
  keyword?: string;
  /** 一级品类，0 为未分类 */
  categoryId?: number;
}

type View = 'category' | 'detail';

/** 地址参数：view（类目账 / 明细账）、month、warehouseId、categoryId；从类目账、库存月结点品类进来时带上 */
const fromUrl = (search: string): { view: View; filters: Filters } => {
  const u = new URLSearchParams(search);
  const num = (k: string) => (u.get(k) ? Number(u.get(k)) : undefined);
  return {
    view: u.get('view') === 'category' ? 'category' : 'detail',
    filters: {
      month: u.get('month') ? dayjs(u.get('month')) : dayjs(),
      warehouseId: num('warehouseId'),
      categoryId: num('categoryId'),
    },
  };
};

/**
 * 仓库管理 → 库存台账（spec warehouse/inventory「库存台账」，原型 I3、L1，29-库存三级账）：
 * 类目账（按一级品类汇总，合计即总账）与明细账（按商品）两个视图；期末金额 = 期初 + 入库 − 出库 + 成本调整
 */
const LedgerPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const warehouses = useWarehouses();
  const location = useLocation();
  const [form] = Form.useForm();
  const initial = (): Filters => ({ month: dayjs() });
  const [view, setView] = useState<View>(() => fromUrl(location.search).view);
  const [filters, setFilters] = useState<Filters>(
    () => fromUrl(location.search).filters,
  );
  const [categories, setCategories] = useState<
    { value: number; label: string }[]
  >([]);
  const [catLedger, setCatLedger] = useState<CategoryLedger>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<LedgerRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState<string>();
  const [period, setPeriod] =
    useState<Awaited<ReturnType<typeof stockApi.ledgerPeriod>>>();

  // 其他页面带参数再次打开本页（标签页已开着）时，按新参数切换视图与筛选
  useEffect(() => {
    if (!location.search) return;
    const u = fromUrl(location.search);
    setView(u.view);
    setFilters(u.filters);
    form.setFieldsValue({ ...u.filters, keyword: undefined });
    setPage(1);
  }, [location.search, form]);

  useEffect(() => {
    categoryApi
      .options(1)
      .then((list) =>
        setCategories([
          ...list.map((c) => ({
            value: c.id,
            label: c.categoryNameZh || c.categoryName,
          })),
          { value: 0, label: '未分类' },
        ]),
      )
      .catch(() => setCategories([{ value: 0, label: '未分类' }]));
  }, []);

  const query = {
    month: filters.month.format('YYYY-MM'),
    warehouseId: filters.warehouseId,
    keyword: filters.keyword?.trim() || undefined,
    categoryId: filters.categoryId,
  };
  const queryKey = JSON.stringify(query);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const q = JSON.parse(queryKey);
      if (view === 'category') {
        const [res, st] = await Promise.all([
          stockApi.categoryLedger({
            month: q.month,
            warehouseId: q.warehouseId,
          }),
          stockApi.ledgerPeriod(q.month),
        ]);
        setCatLedger(res);
        setPeriod(st);
      } else {
        const [res, st] = await Promise.all([
          stockApi.ledger({ ...q, page, pageSize }),
          stockApi.ledgerPeriod(q.month),
        ]);
        setRows(res.records);
        setPeriod(st);
        setTotal(res.total);
      }
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [queryKey, page, pageSize, view]);

  /** 切换视图：记在地址里，返回本页时保持 */
  const switchView = (v: View, categoryId?: number) => {
    const next = {
      ...filters,
      categoryId: v === 'detail' ? categoryId : filters.categoryId,
    };
    setView(v);
    setFilters(next);
    form.setFieldsValue({ categoryId: next.categoryId });
    setPage(1);
    const u = new URLSearchParams({
      view: v,
      month: next.month.format('YYYY-MM'),
    });
    if (next.warehouseId) u.set('warehouseId', String(next.warehouseId));
    if (v === 'detail' && next.categoryId != null)
      u.set('categoryId', String(next.categoryId));
    history.replace(`${location.pathname}?${u.toString()}`);
  };
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
      <PageHeader
        help={
          view === 'category'
            ? '按单据的业务日期归月；点品类查看它的明细账'
            : `按单据的业务日期归月；期末金额 = 期初 + 入库 − 出库 + 成本调整${filters.warehouseId ? '' : '（全部仓库不含仓库之间的调拨）'}；点数量查看对应流水`
        }
        extra={
          <Segmented<View>
            value={view}
            onChange={(v) => switchView(v)}
            options={[
              { label: '类目账', value: 'category' },
              { label: '明细账', value: 'detail' },
            ]}
          />
        }
      />
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
        <FilterField label="月份">
          <Form.Item name="month" noStyle>
            <DatePicker
              aria-label="月份"
              picker="month"
              allowClear={false}
              style={{ width: '100%' }}
              disabledDate={(d) => d.isAfter(dayjs(), 'month')}
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
        {view === 'detail' && (
          <FilterField label="一级品类">
            <Form.Item name="categoryId" noStyle>
              <Select
                aria-label="一级品类"
                allowClear
                showSearch={{ optionFilterProp: 'label' }}
                placeholder="全部品类"
                options={categories}
              />
            </Form.Item>
          </FilterField>
        )}
        {view === 'detail' && (
          <FilterField label="关键词">
            <Form.Item name="keyword" noStyle>
              <Input
                aria-label="关键词"
                allowClear
                prefix={<SearchOutlined />}
                onPressEnter={() => form.submit()}
                placeholder="型号、品牌"
              />
            </Form.Item>
          </FilterField>
        )}
      </FilterCard>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <MainCard
          left={
            period?.status === 1 ? (
              <span
                style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
              >
                <Pill tone="green">已结账</Pill>
                {period.closedByName}{' '}
                {formatDateTime(period.closedAt).slice(0, 16)} 结账
                <HelpTip title="显示结账时的快照；之后的成本调整记在当前未结账月份" />
              </span>
            ) : (
              period && <Pill tone="orange">{period.month} 未结账</Pill>
            )
          }
          right={
            <Button
              type="text"
              icon={<DownloadOutlined />}
              style={{ color: palette.link }}
              loading={exporting}
              disabled={
                view === 'category' ? !catLedger?.rows.length : total === 0
              }
              onClick={async () => {
                setExporting(true);
                try {
                  await stockApi.exportLedger(
                    view === 'category'
                      ? {
                          month: query.month,
                          warehouseId: query.warehouseId,
                          view: 'category',
                        }
                      : query,
                  );
                } catch (e) {
                  message.error(readBizError(e).message);
                } finally {
                  setExporting(false);
                }
              }}
            >
              导出 Excel
            </Button>
          }
        >
          {view === 'category' ? (
            <CategoryLedgerTable
              data={catLedger}
              loading={loading}
              totalLabel="总账合计"
              onPick={(id) => switchView('detail', id)}
            />
          ) : (
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
                onChange: (p, s) => {
                  setPage(p);
                  setPageSize(s);
                },
              }}
            />
          )}
        </MainCard>
      )}
    </div>
  );
};

export default LedgerPage;
