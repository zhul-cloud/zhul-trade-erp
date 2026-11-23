import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Checkbox, DatePicker, Input, Select, Table } from 'antd';
import type { Dayjs } from 'dayjs';
import { Search } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerSub } from '@/components/DocFields';
import { ellipsisRender } from '@/components/EllipsisText';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  SummaryBar,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { auditColumns } from '@/pages/purchase/components';
import { CURRENCIES } from '@/pages/quotation/components';
import { PATHS, Pill } from '@/pages/sales/components';
import { type ReceiptRow, readBizError } from '@/pages/sales/service';
import { DOC_PATHS } from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import {
  UnitPill,
  unitFilterOptions,
  useBusinessUnits,
} from '@/utils/businessUnit';
import { recentRange } from '@/utils/dateRange';
import { usePaymentMethods } from '@/utils/dict';
import { formatAmount } from '@/utils/format';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { ledgerApi, type ReceiptMoney, type ReceiptSummary } from './service';
import { MethodCell, NetCnyCell } from './UnclaimedTab';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const moneyText = (list: ReceiptMoney[]) =>
  list.length
    ? list.map((m) => formatAmount(m.amount, m.currencyCode)).join('、')
    : '—';

interface Filters {
  keyword: string;
  range: [Dayjs, Dayjs] | null;
  paymentMethod?: string;
  channel?: number;
  currencyCode?: string;
  settleStatus?: number;
  includeVoid: boolean;
  unit?: number;
}

/** 帮助文字：help-texts.md「收款管理」，关键词范围以 search-scope.md 为准 */
export const RECORDS_HELP = {
  keyword:
    '可搜：收款单号、平台订单号、付款人、PI 编号、订单编号（只限手动订单和费用订单）、客户名称、简称或联系人',
  summary:
    '按当前筛选条件计算（只算有效到账），和列表逐笔相加一致；未认领的到账不在这里，见「未认领到账」',
} as const;

const initial = (): Filters => ({
  keyword: '',
  // 默认最近 3 个月，清空日期看全部
  range: recentRange(),
  includeVoid: false,
});

/** 收款管理 → 收款单：全部到账，按筛选结果汇总线上 / 线下（报税统计用） */
const RecordsTab: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const methods = usePaymentMethods();
  const [bu] = useBusinessUnits();
  const [draft, setDraft] = useState<Filters>(initial);
  const [filters, setFilters] = useState<Filters>(initial);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20, 'records');
  const [rows, setRows] = useState<ReceiptRow[]>([]);
  const [total, setTotal] = useState(0);
  const [summary, setSummary] = useState<ReceiptSummary[]>([]);
  const [loading, setLoading] = useState(true);
  /** 默认按更新时间倒序，表头可切到到账日期 */
  const [byDate, setByDate] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await ledgerApi.records({
        keyword: filters.keyword.trim() || undefined,
        dateFrom: filters.range?.[0].format('YYYY-MM-DD'),
        dateTo: filters.range?.[1].format('YYYY-MM-DD'),
        paymentMethod: filters.paymentMethod,
        channel: filters.channel,
        currencyCode: filters.currencyCode,
        settleStatus: filters.settleStatus,
        includeVoid: filters.includeVoid || undefined,
        businessUnitId: filters.unit,
        sort: byDate ? 'receiptDate' : undefined,
        page,
        pageSize,
      });
      setRows(res.records);
      setTotal(res.total);
      setSummary(res.summary);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [filters, page, pageSize, byDate, message]);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  /** 线上、线下、合计三张卡改为页签内的汇总行（stat-cards.md），口径不变 */
  const summaryItem = (s: ReceiptSummary) => {
    const name = s.channel === 2 ? '线上' : s.channel === 1 ? '线下' : '合计';
    const gross = `毛额 ${moneyText(s.amounts)}${s.fees.length ? `；手续费 ${moneyText(s.fees)}` : ''}`;
    return {
      key: String(s.channel),
      label: name,
      value: s.netAmountCny,
      currency: 'CNY',
      help:
        s.channel === 0
          ? `实收人民币，${RECORDS_HELP.summary}。${gross}`
          : `实收人民币。${gross}`,
      sub: `${s.count} 笔${s.openNetAmountCny ? ` · 未结完 ${formatAmount(s.openNetAmountCny, 'CNY')}（含估算）` : ''}`,
    };
  };
  const apply = () => {
    setFilters(draft);
    setPage(1);
  };

  const open = (r: ReceiptRow) => history.push(DOC_PATHS.receiptDoc(r.id));
  const source: TableColumnsType<ReceiptRow>[number] = {
    title: '来源单号',
    key: 'pi',
    width: 180,
    render: (_, r) =>
      r.piId || r.soId ? (
        <button
          type="button"
          className="zhul-link"
          style={{ whiteSpace: 'nowrap' }}
          onClick={() =>
            history.push(
              r.piId ? PATHS.pi(r.piId) : PATHS.order(r.soId as number),
            )
          }
        >
          {r.piNo ?? r.soNo}
        </button>
      ) : (
        '—'
      ),
  };
  const columns: TableColumnsType<ReceiptRow> = [
    {
      title: '收款单号',
      dataIndex: 'receiptNo',
      width: 150,
      fixed: 'left',
      render: (v: string, r) => (
        <button
          type="button"
          className="zhul-link"
          style={{
            whiteSpace: 'nowrap',
            textDecoration: r.status === 2 ? 'line-through' : undefined,
          }}
          onClick={() => open(r)}
        >
          {v || '—'}
        </button>
      ),
    },
    {
      title: '到账日期',
      dataIndex: 'receiptDate',
      width: 120,
      sorter: true,
      sortOrder: byDate ? 'descend' : null,
      sortDirections: ['descend'],
    },
    {
      title: '客户',
      width: 180,
      dataIndex: 'customerName',
      ellipsis: true,
      render: (v: string, r: ReceiptRow) => (
        <div style={{ minWidth: 0 }}>
          {ellipsisRender(v)}
          {(r.customerCountry || r.customerContact) && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              <CustomerSub
                country={r.customerCountry}
                contact={r.customerContact}
              />
            </div>
          )}
        </div>
      ),
    },
    ...(bu.enabled
      ? [
          {
            title: '所属事业部',
            key: 'unit',
            width: 180,
            render: (_: unknown, r: ReceiptRow) => (
              <span
                style={{ display: 'inline-flex', gap: 6, flexWrap: 'wrap' }}
              >
                <UnitPill info={bu} id={r.businessUnitId} />
                {r.buMismatch && <Pill tone="orange">账户与订单不一致</Pill>}
              </span>
            ),
          },
        ]
      : []),
    {
      title: '付款方式',
      key: 'method',
      width: 190,
      render: (_, r) => (
        <div>
          <MethodCell r={r} />
          {r.platformOrderNo && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              订单号 {r.platformOrderNo}
            </div>
          )}
        </div>
      ),
    },
    {
      title: '到账金额',
      key: 'amount',
      width: 120,
      align: 'right',
      render: (_, r) => (
        <span
          style={{
            ...num,
            textDecoration: r.status === 2 ? 'line-through' : undefined,
          }}
        >
          {formatAmount(r.amount, r.currencyCode)}
        </span>
      ),
    },
    {
      title: '手续费',
      key: 'fee',
      width: 130,
      render: (_, r) =>
        r.platformFee > 0 ? (
          <span style={{ color: palette.orange }}>
            平台 {formatAmount(r.platformFee, r.currencyCode)}
          </span>
        ) : r.feeDiff > 0 ? (
          <span style={{ color: palette.orange }}>
            中转 {formatAmount(r.feeDiff, r.currencyCode)}
          </span>
        ) : (
          '—'
        ),
    },
    {
      title: '实收',
      key: 'net',
      width: 120,
      align: 'right',
      render: (_, r) => (
        <span style={num}>{formatAmount(r.netAmount, r.currencyCode)}</span>
      ),
    },
    {
      title: '实收人民币',
      key: 'cny',
      width: 170,
      render: (_, r) => <NetCnyCell r={r} />,
    },
    {
      title: '状态',
      key: 'status',
      width: 90,
      render: (_, r) =>
        r.status === 2 ? (
          <Pill tone="mute">已作废</Pill>
        ) : (
          <Pill tone="green">有效</Pill>
        ),
    },
    source,
    {
      title: '登记人',
      key: 'op',
      width: 150,
      render: (_, r) => (
        <div>
          {r.operatorName}
          {r.claimedByName && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              {r.claimedByName} 认领
            </div>
          )}
        </div>
      ),
    },
    ...auditColumns<ReceiptRow>(),
    {
      title: '操作',
      key: 'view',
      width: 70,
      fixed: 'right',
      render: (_, r) => (
        <button type="button" className="zhul-link" onClick={() => open(r)}>
          查看
        </button>
      ),
    },
  ];

  return (
    <div>
      {summary.length > 0 && <SummaryBar items={summary.map(summaryItem)} />}
      <FilterCard
        onSearch={apply}
        onReset={() => {
          setDraft(initial());
          setFilters(initial());
          setPage(1);
        }}
        moreCount={
          [
            bu.enabled ? filters.unit : undefined,
            filters.paymentMethod,
            filters.currencyCode,
            filters.settleStatus,
            filters.includeVoid || undefined,
          ].filter((v) => v != null).length
        }
        more={
          <>
            {bu.enabled && (
              <FilterField label="所属事业部">
                <Select
                  allowClear
                  placeholder="全部"
                  aria-label="所属事业部"
                  value={draft.unit}
                  onChange={(v) => setDraft({ ...draft, unit: v })}
                  options={unitFilterOptions(bu, false)}
                />
              </FilterField>
            )}
            <FilterField label="付款方式">
              <Select
                allowClear
                placeholder="全部"
                aria-label="付款方式"
                value={draft.paymentMethod}
                onChange={(v) => setDraft({ ...draft, paymentMethod: v })}
                options={methods.map((m) => ({ value: m.code, label: m.name }))}
              />
            </FilterField>
            <FilterField label="币种">
              <Select
                allowClear
                placeholder="全部"
                aria-label="币种"
                value={draft.currencyCode}
                onChange={(v) => setDraft({ ...draft, currencyCode: v })}
                options={CURRENCIES.map((c) => ({ value: c, label: c }))}
              />
            </FilterField>
            <FilterField label="结汇状态">
              <Select
                allowClear
                placeholder="全部"
                aria-label="结汇状态"
                value={draft.settleStatus}
                onChange={(v) => setDraft({ ...draft, settleStatus: v })}
                options={[
                  { value: 1, label: '未结汇' },
                  { value: 2, label: '部分结汇' },
                  { value: 3, label: '已结汇' },
                ]}
              />
            </FilterField>
            <FilterField label="已作废">
              <Checkbox
                checked={draft.includeVoid}
                onChange={(e) =>
                  setDraft({ ...draft, includeVoid: e.target.checked })
                }
              >
                一并显示
              </Checkbox>
            </FilterField>
          </>
        }
      >
        <FilterField label="关键词" span={2} help={RECORDS_HELP.keyword}>
          <Input
            allowClear
            prefix={<Search size={16} />}
            placeholder="收款单号、PI 编号、客户"
            aria-label="关键词"
            value={draft.keyword}
            onChange={(e) => setDraft({ ...draft, keyword: e.target.value })}
            onPressEnter={apply}
          />
        </FilterField>
        <FilterField label="到账日期" span={2}>
          <DatePicker.RangePicker
            style={{ width: '100%' }}
            value={draft.range}
            onChange={(v) =>
              setDraft({ ...draft, range: v as [Dayjs, Dayjs] | null })
            }
            aria-label="到账日期"
          />
        </FilterField>
        <FilterField label="线上 / 线下">
          <Select
            allowClear
            placeholder="全部"
            aria-label="线上 / 线下"
            value={draft.channel}
            onChange={(v) => setDraft({ ...draft, channel: v })}
            options={[
              { value: 2, label: '线上' },
              { value: 1, label: '线下' },
            ]}
          />
        </FilterField>
      </FilterCard>
      <MainCard left={`共 ${total} 条`}>
        <Table<ReceiptRow>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: tableScrollX(columns) }}
          locale={{ emptyText: tableEmpty('没有符合条件的收款单', loading) }}
          onChange={(_, __, sorter, extra) => {
            // 翻页也会触发 onChange，只在排序时回到第 1 页（audit UI-061）
            if (extra.action !== 'sort') return;
            const s = Array.isArray(sorter) ? sorter[0] : sorter;
            setByDate(s?.field === 'receiptDate' && !!s.order);
            setPage(1);
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
    </div>
  );
};

export default RecordsTab;
