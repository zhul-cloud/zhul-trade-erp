import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, DatePicker, Input, Segmented, Select, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle } from '@/pages/inquiry/shared/components';
import { SupplierPicker } from '@/pages/purchase/components';
import { useUserOptions } from '@/pages/sales/orders/dialogs';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
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

/** 采购管理 → 砍价统计：已下单采购订单行的砍价汇总（只读） */
const BargainStatsPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const users = useUserOptions();
  const [range, setRange] = useState<[Dayjs, Dayjs]>([
    dayjs().startOf('year'),
    dayjs(),
  ]);
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

  const stat = (
    label: string,
    value: React.ReactNode,
    sub?: React.ReactNode,
    color?: string,
  ) => (
    <div
      style={{
        flex: '1 1 200px',
        padding: '16px 18px',
        borderRadius: 14,
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
      }}
    >
      <div style={{ fontSize: 13, color: palette.sub }}>{label}</div>
      <div
        style={{
          ...num,
          fontSize: 22,
          fontWeight: 800,
          color: color ?? palette.ink,
          marginTop: 4,
        }}
      >
        {value}
      </div>
      {sub && (
        <div style={{ fontSize: 12, color: palette.mute, marginTop: 2 }}>
          {sub}
        </div>
      )}
    </div>
  );

  const bargainCell = (v?: number | null) =>
    v == null ? (
      <span style={{ color: palette.mute }}>—</span>
    ) : (
      <b style={{ ...num, color: v < 0 ? palette.red : palette.green }}>
        {cny(v)}
      </b>
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
        <a onClick={() => history.push(`/purchase/orders/${r.poId}`)}>{v}</a>
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
      <PageTitle description="已下单、未取消的采购订单行；砍价 = (目标价 − 人民币单价) × 数量，砍价率按目标金额加权；质量索赔与开票成本单独列，不计入砍价。" />
      <div
        style={{
          display: 'flex',
          gap: 10,
          flexWrap: 'wrap',
          marginBottom: 16,
          alignItems: 'center',
        }}
      >
        <DatePicker.RangePicker
          value={range}
          allowClear={false}
          onChange={(v) => v?.[0] && v[1] && setRange([v[0], v[1]])}
        />
        <Select
          allowClear
          placeholder="采购员"
          style={{ width: 140 }}
          value={purchaserId}
          onChange={setPurchaserId}
          options={users}
          showSearch={{ optionFilterProp: 'label' }}
        />
        <SupplierPicker
          value={supplierId}
          placeholder="供应商"
          style={{ width: 200 }}
          onChange={(id) => setSupplierId(id)}
        />
        <Input
          allowClear
          placeholder="品牌"
          style={{ width: 140 }}
          value={brand}
          onChange={(e) => setBrand(e.target.value)}
          onPressEnter={() => setQuery({ brand, category })}
          onBlur={() => setQuery({ brand, category })}
        />
        <Input
          allowClear
          placeholder="品类"
          style={{ width: 140 }}
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          onPressEnter={() => setQuery({ brand, category })}
          onBlur={() => setQuery({ brand, category })}
        />
      </div>
      {summary && (
        <div
          style={{
            display: 'flex',
            gap: 14,
            flexWrap: 'wrap',
            marginBottom: 16,
          }}
        >
          {stat(
            '采购金额',
            cny(summary.purchaseAmount),
            summary.noTargetAmount
              ? `其中没有目标价 ${cny(summary.noTargetAmount)}`
              : `${summary.lineCount} 行`,
          )}
          {stat(
            '砍价金额',
            cny(summary.bargainAmount),
            `砍价率 ${rateText(summary.bargainRate)}`,
            summary.bargainAmount < 0 ? palette.red : palette.green,
          )}
          {stat('质量索赔', cny(summary.claimAmount), '不良折价，不计入砍价')}
          {stat('开票成本', cny(summary.invoiceCost), '开票税点，不计入砍价')}
        </div>
      )}
      <Card>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            marginBottom: 14,
            flexWrap: 'wrap',
          }}
        >
          <b style={{ color: palette.ink }}>分组</b>
          <Segmented<string>
            value={groupBy}
            onChange={(v) => setGroupBy(v as BargainGroupBy)}
            options={(Object.keys(GROUP_TITLES) as BargainGroupBy[]).map(
              (k) => ({ label: GROUP_TITLES[k], value: k }),
            )}
          />
          <span style={{ flex: 1 }} />
          <span style={{ fontSize: 12, color: palette.mute }}>
            点一行展开采购订单行
          </span>
        </div>
        <Table<BargainGroup>
          rowKey="key"
          loading={loading}
          columns={columns}
          dataSource={groups}
          pagination={false}
          scroll={{ x: 1100 }}
          locale={{ emptyText: '这段时间没有已下单的采购订单' }}
          expandable={{
            expandRowByClick: true,
            expandedRowRender: (g) => (
              <Table<BargainLine>
                rowKey={(r) => `${r.poId}-${r.model}-${r.quantity}`}
                size="small"
                columns={lineColumns}
                dataSource={g.lines}
                pagination={false}
                scroll={{ x: 1400 }}
              />
            ),
          }}
        />
      </Card>
    </div>
  );
};

export default BargainStatsPage;
