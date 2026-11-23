import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Input, Segmented, Select, Table } from 'antd';
import type { Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import {
  FilterCard,
  FilterField,
  HelpTip,
  MainCard,
  PageHeader,
  SummaryBar,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import StatsRangeFilter, { presetRange } from '@/components/StatsRangeFilter';
import { SupplierPicker } from '@/pages/purchase/components';
import { useUserOptions } from '@/pages/sales/orders/dialogs';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { useDeltaColor } from '@/theme/delta';
import { formatAmount } from '@/utils/format';
import { tableScrollX } from '@/utils/tableScroll';
import {
  type BargainGroup,
  type BargainGroupBy,
  type BargainLine,
  type BargainTotals,
  bargainApi,
} from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const cny = (v?: number | null) => formatAmount(v ?? 0, 'CNY');
const rateText = (v?: number | null) => (v == null ? '—' : `${v}%`);

const GROUP_TITLES: Record<BargainGroupBy, string> = {
  PURCHASER: '采购员',
  SUPPLIER: '供应商',
  BRAND: '品牌',
  CATEGORY: '品类',
  MONTH: '月份',
};

/** 帮助文字：help-texts.md「砍价统计」 */
export const HELP = {
  page: '已下单、未取消的采购订单行；砍价 = (目标价 − 人民币单价) × 数量，砍价率按目标金额加权；质量索赔与开票成本单独列，不计入砍价',
  bargain: '砍价 = (目标价 − 人民币单价) × 数量；砍价率按目标金额加权',
} as const;

/** 采购管理 → 砍价统计：已下单采购订单行的砍价汇总（只读） */
const BargainStatsPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const { of: deltaOf } = useDeltaColor();
  const users = useUserOptions();
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() =>
    presetRange('month'),
  );
  const [purchaserId, setPurchaserId] = useState<number>();
  const [supplierId, setSupplierId] = useState<number>();
  const [brand, setBrand] = useState('');
  const [category, setCategory] = useState('');
  const [query, setQuery] = useState({ brand: '', category: '' });
  const [groupBy, setGroupBy] = useState<BargainGroupBy>('PURCHASER');
  const [summary, setSummary] = useState<BargainTotals>();
  const [groups, setGroups] = useState<BargainGroup[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await bargainApi.stats({
        dateFrom: range[0].format('YYYY-MM-DD'),
        dateTo: range[1].format('YYYY-MM-DD'),
        purchaserId,
        supplierId,
        brand: query.brand.trim() || undefined,
        category: query.category.trim() || undefined,
        groupBy,
      });
      setSummary(res.summary);
      setGroups(res.groups);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [range, purchaserId, supplierId, query, groupBy, message]);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  const bargainCell = (v?: number | null) =>
    v == null ? (
      <span style={{ color: palette.mute }}>—</span>
    ) : (
      <b style={{ ...num, color: deltaOf(v) }}>{cny(v)}</b>
    );

  const columns: TableColumnsType<BargainGroup> = [
    {
      title: GROUP_TITLES[groupBy],
      dataIndex: 'label',
      width: 200,
      fixed: 'left',
      render: (v: string) => <b style={{ color: palette.ink }}>{v}</b>,
    },
    {
      title: '采购金额',
      key: 'purchase',
      width: 140,
      align: 'right',
      sorter: (a, b) => a.totals.purchaseAmount - b.totals.purchaseAmount,
      render: (_, g) => <span style={num}>{cny(g.totals.purchaseAmount)}</span>,
    },
    {
      title: '目标金额',
      key: 'target',
      width: 140,
      align: 'right',
      render: (_, g) => <span style={num}>{cny(g.totals.targetAmount)}</span>,
    },
    {
      title: '砍价金额',
      key: 'bargain',
      width: 130,
      align: 'right',
      sorter: (a, b) => a.totals.bargainAmount - b.totals.bargainAmount,
      render: (_, g) => bargainCell(g.totals.bargainAmount),
    },
    {
      title: '砍价率',
      key: 'rate',
      width: 90,
      align: 'right',
      sorter: (a, b) =>
        (a.totals.bargainRate ?? 0) - (b.totals.bargainRate ?? 0),
      render: (_, g) => rateText(g.totals.bargainRate),
    },
    {
      title: '没有目标价',
      key: 'noTarget',
      width: 120,
      align: 'right',
      render: (_, g) =>
        g.totals.noTargetAmount ? (
          <span style={{ ...num, color: palette.mute }}>
            {cny(g.totals.noTargetAmount)}
          </span>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '质量索赔',
      key: 'claim',
      width: 110,
      align: 'right',
      render: (_, g) =>
        g.totals.claimAmount ? (
          <span style={num}>{cny(g.totals.claimAmount)}</span>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '开票成本',
      key: 'invoice',
      width: 110,
      align: 'right',
      render: (_, g) =>
        g.totals.invoiceCost ? (
          <span style={num}>{cny(g.totals.invoiceCost)}</span>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
  ];

  const lineColumns: TableColumnsType<BargainLine> = [
    {
      title: '采购订单',
      dataIndex: 'poNo',
      width: 170,
      render: (v: string, r) => (
        <button
          type="button"
          className="zhul-link"
          onClick={() => history.push(`/purchase/orders/${r.poId}`)}
        >
          {v}
        </button>
      ),
    },
    { title: '下单日期', dataIndex: 'orderDate', width: 110 },
    {
      title: '型号',
      key: 'model',
      width: 220,
      render: (_, r) => (
        <div>
          {r.model}
          <div style={{ fontSize: 12, color: palette.mute }}>
            {[r.brand, r.category].filter(Boolean).join(' · ')}
          </div>
        </div>
      ),
    },
    { title: '供应商', dataIndex: 'supplierName', width: 180, ellipsis: true },
    { title: '采购员', dataIndex: 'purchaserName', width: 90 },
    { title: '数量', dataIndex: 'quantity', width: 70, align: 'right' },
    {
      title: '目标价',
      dataIndex: 'targetPrice',
      width: 110,
      align: 'right',
      render: (v?: number | null) =>
        v == null ? (
          <span style={{ fontSize: 12, color: palette.mute }}>没有目标价</span>
        ) : (
          cny(v)
        ),
    },
    {
      title: '人民币单价',
      dataIndex: 'netPriceCny',
      width: 110,
      align: 'right',
      render: (v?: number | null) => (v == null ? '—' : cny(v)),
    },
    {
      title: '砍价',
      dataIndex: 'bargainAmount',
      width: 120,
      align: 'right',
      render: bargainCell,
    },
    {
      title: '砍价率',
      dataIndex: 'bargainRate',
      width: 80,
      align: 'right',
      render: rateText,
    },
    {
      title: '质量索赔',
      dataIndex: 'claimAmount',
      width: 100,
      align: 'right',
      render: (v: number) =>
        v ? cny(v) : <span style={{ color: palette.mute }}>—</span>,
    },
  ];

  return (
    <div>
      <PageHeader help={HELP.page} />
      {/* 统计条件选了就生效；品牌、品类输入后回车或离开输入框生效 */}
      <FilterCard
        moreCount={
          [supplierId, query.category || undefined].filter((v) => v != null)
            .length
        }
        more={
          <>
            <FilterField label="供应商">
              <SupplierPicker
                value={supplierId}
                placeholder="全部"
                style={{ width: '100%' }}
                onChange={(id) => setSupplierId(id)}
              />
            </FilterField>
            <FilterField label="品类">
              <Input
                allowClear
                placeholder="全部"
                aria-label="品类"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                onPressEnter={() => setQuery({ brand, category })}
                onBlur={() => setQuery({ brand, category })}
              />
            </FilterField>
          </>
        }
      >
        <FilterField label="统计期间" span={2}>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <StatsRangeFilter value={range} onChange={setRange} />
          </div>
        </FilterField>
        <FilterField label="采购员">
          <Select
            allowClear
            placeholder="全部"
            aria-label="采购员"
            value={purchaserId}
            onChange={setPurchaserId}
            options={users}
            showSearch={{ optionFilterProp: 'label' }}
          />
        </FilterField>
        <FilterField label="品牌">
          <Input
            allowClear
            placeholder="全部"
            aria-label="品牌"
            value={brand}
            onChange={(e) => setBrand(e.target.value)}
            onPressEnter={() => setQuery({ brand, category })}
            onBlur={() => setQuery({ brand, category })}
          />
        </FilterField>
      </FilterCard>
      {summary && (
        <SummaryBar
          items={[
            {
              key: 'purchase',
              label: '采购金额',
              value: summary.purchaseAmount,
              currency: 'CNY',
              sub: summary.noTargetAmount
                ? `其中没有目标价 ${cny(summary.noTargetAmount)}`
                : `${summary.lineCount} 行`,
            },
            {
              key: 'bargain',
              label: '砍价金额',
              help: HELP.bargain,
              value: (
                <span style={{ color: deltaOf(summary.bargainAmount) }}>
                  {cny(summary.bargainAmount)}
                </span>
              ),
              sub: `砍价率 ${rateText(summary.bargainRate)}`,
            },
            {
              key: 'claim',
              label: '质量索赔',
              help: '不良折价，不计入砍价',
              value: summary.claimAmount,
              currency: 'CNY',
            },
            {
              key: 'invoice',
              label: '开票成本',
              help: '开票税点，不计入砍价',
              value: summary.invoiceCost,
              currency: 'CNY',
            },
          ]}
        />
      )}
      <MainCard
        left={
          <>
            <span>分组</span>
            <HelpTip title="点一行展开采购订单行" />
          </>
        }
        right={
          <Segmented<string>
            value={groupBy}
            onChange={(v) => setGroupBy(v as BargainGroupBy)}
            options={(Object.keys(GROUP_TITLES) as BargainGroupBy[]).map(
              (k) => ({ label: GROUP_TITLES[k], value: k }),
            )}
          />
        }
      >
        <Table<BargainGroup>
          rowKey="key"
          loading={loading}
          columns={columns}
          dataSource={groups}
          pagination={false}
          scroll={{ x: tableScrollX(columns) }}
          locale={{ emptyText: '这段时间没有已下单的采购订单' }}
          expandable={{
            expandRowByClick: true,
            expandedRowRender: (g) => (
              <Table<BargainLine>
                rowKey={(r) => `${r.poId}-${r.model}-${r.quantity}`}
                size="small"
                columns={lineColumns}
                dataSource={g.lines}
                pagination={{
                  defaultPageSize: 20,
                  hideOnSinglePage: true,
                  size: 'small',
                  showTotal: (total) => `共 ${total} 行`,
                }}
                scroll={{ x: tableScrollX(lineColumns) }}
              />
            ),
          }}
        />
      </MainCard>
    </div>
  );
};

export default BargainStatsPage;
