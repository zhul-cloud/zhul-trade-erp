import { history, useAccess, useSearchParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { DatePicker, Form, Input, Select, Table, Tag } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Search } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatMoney } from '@/utils/format';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import {
  AgeText,
  ConditionPill,
  LeadTimeText,
  SupplierText,
  TaxHint,
  useQuoteDicts,
} from '../shared/components';
import { CHANNEL_OPTIONS, PATHS } from '../shared/constants';
import QuoteExtras from '../shared/QuoteExtras';
import {
  type PriceHistoryGroup,
  priceHistoryApi,
  readBizError,
} from '../shared/service';

interface Filters {
  model?: string;
  brand?: string;
  itemCondition?: number;
  channel?: number;
  stock?: 'priced' | 'none';
  range?: [Dayjs, Dayjs];
  /** 一级品类：从无货分析点品类跳过来时带上 */
  categoryId?: number;
  categoryName?: string;
}

/** 从链接带的筛选（无货分析点数字跳过来）：noStock=1、brand、categoryId + categoryName、dateFrom、dateTo */
const fromSearch = (search: URLSearchParams): Filters => {
  const from = search.get('dateFrom');
  const to = search.get('dateTo');
  const category = Number(search.get('categoryId'));
  return {
    brand: search.get('brand') ?? undefined,
    stock: search.get('noStock') === '1' ? 'none' : undefined,
    range: from && to ? [dayjs(from), dayjs(to)] : undefined,
    categoryId: category > 0 ? category : undefined,
    categoryName: search.get('categoryName') ?? undefined,
  };
};

/** 帮助文字：help-texts.md「询价记录」 */
export const HELP = {
  page: '同一型号以前问到的价格都在这里，询价和报价前先查一查',
  model: '同品牌、型号写法不同（大小写、空格、连字符）也会归到一起',
} as const;

/** 询价记录：同一型号以前问到的价格，询价和报价前先查一查 */
const PriceHistoryPage: React.FC = () => {
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canSeeSupplier = !!access['inquiry:supplier:view'];
  const { conditionOptions } = useQuoteDicts();
  const [form] = Form.useForm<Filters>();
  const [params] = useSearchParams();
  const searchKey = params.toString();
  const [filters, setFilters] = useState<Filters>(() => fromSearch(params));
  // 查询时保留从链接带来的品类（品类不在表单里，用标签显示、可单独去掉）
  const search = () => {
    setPage(1);
    setFilters({
      ...form.getFieldsValue(),
      categoryId: filters.categoryId,
      categoryName: filters.categoryName,
    });
  };
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<PriceHistoryGroup[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await priceHistoryApi.page({
        model: filters.model?.trim() || undefined,
        brand: filters.brand?.trim() || undefined,
        itemCondition: filters.itemCondition,
        channel: filters.channel,
        noStock: filters.stock ? filters.stock === 'none' : undefined,
        categoryId: filters.categoryId,
        dateFrom: filters.range?.[0]?.format('YYYY-MM-DD'),
        dateTo: filters.range?.[1]?.format('YYYY-MM-DD'),
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
  }, [filters, page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  // 首次打开和标签页已打开时再从别处带筛选跳过来，都按链接填表单并重新筛（不用 initialValues，重置才回到空）
  useEffect(() => {
    if (!searchKey) return;
    const f = fromSearch(new URLSearchParams(searchKey));
    form.resetFields();
    form.setFieldsValue(f);
    setPage(1);
    setFilters(f);
  }, [searchKey]);

  const columns: TableColumnsType<PriceHistoryGroup> = [
    {
      title: '品牌 / 型号',
      render: (_, r) => (
        <span style={{ display: 'inline-flex', flexDirection: 'column' }}>
          <span style={{ color: palette.ink, fontWeight: 600 }}>{r.model}</span>
          <span style={{ color: palette.mute, fontSize: 12 }}>
            {r.brand}
            {r.category ? ` · ${r.category}` : ''}
          </span>
        </span>
      ),
    },
    {
      // 整列都是人民币：币种写在表头，单元格只写数字；最低价不是状态，不用绿色（UI-117）
      title: '最低价（CNY）',
      align: 'right',
      width: 140,
      render: (_, r) =>
        r.minPriceCny == null ? (
          <span style={{ color: palette.mute }}>无货</span>
        ) : (
          <b style={{ color: palette.ink }}>{formatMoney(r.minPriceCny)}</b>
        ),
    },
    {
      title: '最高价（CNY）',
      align: 'right',
      width: 140,
      render: (_, r) =>
        r.maxPriceCny == null ? '—' : formatMoney(r.maxPriceCny),
    },
    { title: '记录数', dataIndex: 'recordCount', width: 90, align: 'right' },
    {
      title: '最近询价',
      width: 190,
      render: (_, r) =>
        r.daysAgo == null ? (
          '—'
        ) : (
          <AgeText days={r.daysAgo} date={r.lastQuotedAt} />
        ),
    },
  ];

  const expanded = (g: PriceHistoryGroup) => (
    <div style={{ display: 'grid', gap: 2, padding: '0 12px' }}>
      {g.records.map((q) => (
        <div
          key={q.id}
          style={{
            display: 'grid',
            gridTemplateColumns: '110px 100px 1.4fr 100px 90px 170px 1fr',
            gap: 12,
            alignItems: 'center',
            padding: '8px 0',
            borderBottom: `1px solid ${palette.hairline}`,
          }}
        >
          <b style={{ color: q.noStock ? palette.mute : palette.ink }}>
            {q.noStock ? '无货' : formatAmount(q.unitPriceCny)}
          </b>
          <span>
            {q.noStock ? '' : <ConditionPill value={q.itemCondition} />}
          </span>
          <span>
            <SupplierText channel={q.channel} shopName={q.shopName} />
            {!q.noStock && (
              <span style={{ marginLeft: 6 }}>
                <TaxHint
                  taxIncluded={q.taxIncluded}
                  taxRate={q.taxRate}
                  unitPrice={q.unitPrice}
                />
                <QuoteExtras quote={q} />
              </span>
            )}
          </span>
          <span style={{ color: palette.sub }}>
            {q.noStock ? q.note : <LeadTimeText value={q.leadTime} />}
          </span>
          <span style={{ color: palette.sub }}>{q.quotedByName ?? '—'}</span>
          <AgeText days={q.daysAgo} date={q.quotedAt} />
          <span style={{ textAlign: 'right' }}>
            {q.customerInquiryId && (
              <button
                type="button"
                className="zhul-link"
                style={{ fontSize: 12 }}
                onClick={() =>
                  history.push(`${PATHS.inquiries}/${q.customerInquiryId}`)
                }
              >
                来自 {q.customerInquiryCode}
              </button>
            )}
          </span>
        </div>
      ))}
    </div>
  );

  return (
    <div>
      <PageHeader help={HELP.page} />
      <Form form={form} component={false}>
        <FilterCard
          onSearch={search}
          onReset={() => {
            form.resetFields();
            setPage(1);
            setFilters({});
          }}
          minColumn={130}
          moreCount={
            [
              canSeeSupplier ? filters.channel : undefined,
              filters.range,
            ].filter((v) => v != null).length
          }
          more={
            <>
              {canSeeSupplier && (
                <FilterField label="渠道">
                  <Form.Item name="channel" noStyle>
                    <Select
                      allowClear
                      placeholder="全部"
                      aria-label="渠道"
                      options={CHANNEL_OPTIONS}
                    />
                  </Form.Item>
                </FilterField>
              )}
              <FilterField label="询价日期" span={2} htmlFor="ph-date">
                <Form.Item name="range" noStyle>
                  <DatePicker.RangePicker
                    id={{ start: 'ph-date' }}
                    style={{ width: '100%' }}
                  />
                </Form.Item>
              </FilterField>
            </>
          }
        >
          <FilterField label="型号" span={2} help={HELP.model}>
            <Form.Item name="model" noStyle>
              <Input
                allowClear
                prefix={<Search size={16} />}
                placeholder="型号的一部分，如 HG-SN"
                aria-label="型号"
                onPressEnter={search}
              />
            </Form.Item>
          </FilterField>
          <FilterField label="品牌">
            <Form.Item name="brand" noStyle>
              <Input
                allowClear
                placeholder="如 Mitsubishi 或 三菱"
                aria-label="品牌"
                onPressEnter={search}
              />
            </Form.Item>
          </FilterField>
          <FilterField label="回价">
            <Form.Item name="stock" noStyle>
              <Select
                allowClear
                placeholder="全部"
                aria-label="回价"
                options={[
                  { value: 'priced', label: '有价' },
                  { value: 'none', label: '无货' },
                ]}
              />
            </Form.Item>
          </FilterField>
          <FilterField label="货况">
            <Form.Item name="itemCondition" noStyle>
              <Select
                allowClear
                placeholder="全部"
                aria-label="货况"
                options={conditionOptions}
              />
            </Form.Item>
          </FilterField>
        </FilterCard>
      </Form>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <MainCard
          count={total}
          unit="个型号"
          loading={loading}
          quick={
            filters.categoryId ? (
              <Tag
                closable
                onClose={() => {
                  setPage(1);
                  setFilters((f) => ({
                    ...f,
                    categoryId: undefined,
                    categoryName: undefined,
                  }));
                }}
              >
                品类：{filters.categoryName ?? filters.categoryId}
              </Tag>
            ) : undefined
          }
        >
          <Table<PriceHistoryGroup>
            rowKey={(r) => `${r.brandKey}|${r.modelKey}`}
            columns={columns}
            dataSource={rows}
            loading={loading}
            expandable={{ expandedRowRender: expanded }}
            locale={{
              emptyText: tableEmpty(
                filters.model || filters.brand
                  ? '没有找到这个型号的历史价格，需要询价'
                  : '还没有询价记录，采购提交回价后会出现在这里',
                loading,
              ),
            }}
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
        </MainCard>
      )}
    </div>
  );
};

export default PriceHistoryPage;
