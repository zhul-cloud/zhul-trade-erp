import { history, Link } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Checkbox, DatePicker, Input, Popover, Select, Table } from 'antd';
import type { Dayjs } from 'dayjs';
import { Eye, Search } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { useColumnSettings } from '@/components/ColumnSettings';
import { CopyText } from '@/components/CopyText';
import SourceNos from '@/components/DocFields/SourceNos';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
  SummaryBar,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { Pill } from '@/pages/inquiry/shared/components';
import { auditColumns } from '@/pages/purchase/components';
import { CURRENCIES } from '@/pages/quotation/components';
import { readBizError } from '@/pages/sales/service';
import { DOC_PATHS } from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { recentRange } from '@/utils/dateRange';
import { formatAmount } from '@/utils/format';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { PAY_CHANNEL_NAMES } from '../payables/payQr';
import {
  METHOD,
  type Payment,
  type PaymentQuery,
  type PaymentStats,
  paymentApi,
} from '../payables/service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

interface Filters {
  keyword: string;
  range: [Dayjs, Dayjs] | null;
  method?: number;
  advanceUser: string;
  currencyCode?: string;
  kind?: number;
  showVoid: boolean;
}

/** 帮助文字：help-texts.md「付款管理」，关键词范围以 search-scope.md 为准 */
export const HELP = {
  page: '公司对公、私对私由财务登记，员工垫付由付款的员工本人登记（进入待报销）；供应商退款也在这里。登记错了由登记人或财务在详情里作废，应付随之恢复',
  reimburse: '不计入付款合计',
} as const;

const initial = (): Filters => ({
  keyword: '',
  // 默认最近 3 个月，清空日期看全部
  range: recentRange(),
  advanceUser: '',
  showVoid: false,
});

/** 财务管理 → 付款单：公司对公与员工垫付的付款、供应商退款；登记人或财务可以作废 */
const PaymentRecords: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [draft, setDraft] = useState<Filters>(initial);
  const [filters, setFilters] = useState<Filters>(initial);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<Payment[]>([]);
  const [total, setTotal] = useState(0);
  const [stats, setStats] = useState<PaymentStats>();
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    const q: Omit<PaymentQuery, 'page' | 'pageSize'> = {
      keyword: filters.keyword.trim() || undefined,
      dateFrom: filters.range?.[0].format('YYYY-MM-DD'),
      dateTo: filters.range?.[1].format('YYYY-MM-DD'),
      method: filters.method,
      advanceUser: filters.advanceUser.trim() || undefined,
      currencyCode: filters.currencyCode,
      kind: filters.kind,
      showVoid: filters.showVoid || undefined,
    };
    try {
      const [res, s] = await Promise.all([
        paymentApi.page({ ...q, page, pageSize }),
        paymentApi.stats(q),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setStats(s);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [filters, page, pageSize, message]);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  const apply = (next: Partial<Filters>) => {
    const f = { ...draft, ...next };
    setDraft(f);
    setFilters(f);
    setPage(1);
  };

  const columns: TableColumnsType<Payment> = [
    {
      title: '付款单号',
      dataIndex: 'paymentNo',
      width: 150,
      fixed: 'left',
      render: (v: string, r) => (
        <CopyText text={v}>
          <button
            type="button"
            className="zhul-link"
            style={{
              whiteSpace: 'nowrap',
              textDecoration: r.voided ? 'line-through' : undefined,
            }}
            onClick={() => history.push(DOC_PATHS.payment(r.id))}
          >
            {v}
          </button>
        </CopyText>
      ),
    },
    {
      title: '收款方',
      key: 'payee',
      width: 240,
      render: (_, r) => (
        <div>
          <div>{r.payeeName}</div>
          {r.payeeAccount && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              {r.payeeAccount}
            </div>
          )}
        </div>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 140,
      render: (_, r) =>
        r.voided ? (
          <div>
            <Pill tone="mute">已作废</Pill>
            <div style={{ fontSize: 12, color: palette.mute }}>
              {r.voidReason}
            </div>
          </div>
        ) : (
          <Pill tone="green">有效</Pill>
        ),
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 130,
      align: 'right',
      render: (v: number, r) => (
        <span style={num}>
          {formatAmount(v, r.currencyCode)}
          {r.creditUsed > 0 && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              另抵扣 {formatAmount(r.creditUsed, r.currencyCode)}
            </div>
          )}
        </span>
      ),
    },
    {
      title: '实付人民币',
      dataIndex: 'amountCny',
      width: 130,
      align: 'right',
      render: (v: number, r) => (
        <span style={num}>
          {formatAmount(v, 'CNY')}
          {r.currencyCode !== 'CNY' && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              汇率 {r.actualRate}
            </div>
          )}
        </span>
      ),
    },
    { title: '付款日期', dataIndex: 'payDate', width: 110 },
    {
      title: '核销的应付单',
      key: 'alloc',
      width: 300,
      // 多张时只显示第一张，第二行「等 N 张」悬停列出全部（与来源单号同一写法），行高不随张数增长（D21）
      render: (_, r) => {
        const line = (a: (typeof r.allocations)[number]) => (
          <div
            key={`${a.payableId}-${a.fromCredit}-${a.creditPayableNo ?? ''}`}
            style={{
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            <Link to={DOC_PATHS.payable(a.payableId)}>{a.payableNo}</Link>{' '}
            <span style={{ color: palette.mute }}>{a.sourceNo}</span>{' '}
            {formatAmount(a.amount, r.currencyCode)}
            {a.fromCredit === 1 && (
              <span style={{ color: palette.orange }}> 抵扣</span>
            )}
          </div>
        );
        const [first, ...rest] = r.allocations;
        if (!first) return <span style={{ color: palette.mute }}>—</span>;
        return (
          <div style={{ fontSize: 12 }}>
            {line(first)}
            {rest.length > 0 && (
              <Popover
                title={`核销的应付单（${r.allocations.length}）`}
                content={
                  <div
                    style={{ display: 'flex', flexDirection: 'column', gap: 4 }}
                  >
                    {r.allocations.map(line)}
                  </div>
                }
              >
                <span style={{ color: palette.sub, cursor: 'default' }}>
                  等 {r.allocations.length} 张
                </span>
              </Popover>
            )}
          </div>
        );
      },
    },
    {
      title: '类型',
      dataIndex: 'kind',
      width: 110,
      render: (v: number) =>
        v === 2 ? (
          <Pill tone="orange">退款</Pill>
        ) : v === 3 ? (
          <Pill tone="cyan">报销付款</Pill>
        ) : (
          <Pill tone="gray">付款</Pill>
        ),
    },
    {
      title: '付款方式',
      key: 'method',
      width: 110,
      render: (_, r) =>
        r.kind === 2 ? (
          '—'
        ) : r.method === METHOD.ADVANCE ? (
          <Pill tone="gray">员工垫付</Pill>
        ) : r.method === METHOD.PRIVATE ? (
          <Pill tone="orange">
            私对私
            {r.payChannel && r.payChannel !== 1
              ? ` · ${PAY_CHANNEL_NAMES[r.payChannel]}`
              : ''}
          </Pill>
        ) : (
          <Pill tone="gray">公司对公</Pill>
        ),
    },
    {
      title: '账户 / 垫付人',
      key: 'who',
      width: 220,
      ellipsis: true,
      render: (_, r) =>
        (r.method === METHOD.ADVANCE && r.kind === 1
          ? r.advanceUser
          : r.method === METHOD.PRIVATE
            ? r.payerAccount
            : r.accountName) || '—',
    },
    {
      title: '来源单号',
      key: 'source',
      width: 160,
      render: (_, r) => (
        <SourceNos
          refs={[
            ...new Set(r.allocations.map((a) => a.sourceNo).filter(Boolean)),
          ].map((no) => ({ no }))}
        />
      ),
    },
    ...auditColumns<Payment>(),
    {
      title: '操作',
      key: 'op',
      width: rowActionsWidth(1),
      fixed: 'right',
      render: (_, r) => (
        <RowActions
          inline={[
            {
              key: 'view',
              label: '查看',
              icon: <Eye size={18} />,
              onClick: () => history.push(DOC_PATHS.payment(r.id)),
            },
          ]}
        />
      ),
    },
  ];
  const columnsSet = useColumnSettings('finance-payments', columns, {
    hidden: ['核销的应付单', '类型', '付款方式', '账户 / 垫付人', '来源单号'],
  });

  return (
    <div>
      <PageHeader help={HELP.page} />
      <SummaryBar
        items={[
          {
            key: 'total',
            label: '付款合计',
            value: stats?.total ?? 0,
            currency: 'CNY',
          },
          {
            key: 'company',
            label: '公司对公',
            value: stats?.company ?? 0,
            currency: 'CNY',
          },
          {
            key: 'advance',
            label: '员工垫付',
            value: stats?.advance ?? 0,
            currency: 'CNY',
          },
          {
            key: 'personal',
            label: '私对私',
            value: stats?.personal ?? 0,
            currency: 'CNY',
          },
          {
            key: 'reimburse',
            label: '报销付款',
            help: HELP.reimburse,
            value: stats?.reimburse ?? 0,
            currency: 'CNY',
          },
        ]}
      />
      <FilterCard
        onSearch={() => apply({})}
        onReset={() => {
          const f = initial();
          setDraft(f);
          setFilters(f);
          setPage(1);
        }}
        moreCount={
          [
            filters.advanceUser.trim() || undefined,
            filters.kind,
            filters.currencyCode,
            filters.showVoid || undefined,
          ].filter((v) => v != null).length
        }
        more={
          <>
            <FilterField label="垫付人账号">
              <Input
                allowClear
                placeholder="账号"
                aria-label="垫付人账号"
                value={draft.advanceUser}
                onChange={(e) =>
                  setDraft({ ...draft, advanceUser: e.target.value })
                }
                onPressEnter={() => apply({})}
              />
            </FilterField>
            <FilterField label="类型">
              <Select
                allowClear
                placeholder="全部"
                aria-label="类型"
                value={draft.kind}
                onChange={(v) => setDraft({ ...draft, kind: v })}
                options={[
                  { value: 1, label: '付款' },
                  { value: 2, label: '退款' },
                  { value: 3, label: '报销付款' },
                ]}
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
            <FilterField label="已作废">
              <Checkbox
                checked={draft.showVoid}
                onChange={(e) =>
                  setDraft({ ...draft, showVoid: e.target.checked })
                }
              >
                一并显示
              </Checkbox>
            </FilterField>
          </>
        }
      >
        <FilterField label="关键词" span={2}>
          <Input
            allowClear
            prefix={<Search size={16} />}
            placeholder="付款单号、收款方"
            aria-label="关键词"
            value={draft.keyword}
            onChange={(e) => setDraft({ ...draft, keyword: e.target.value })}
            onPressEnter={() => apply({})}
          />
        </FilterField>
        <FilterField label="付款日期" span={2}>
          <DatePicker.RangePicker
            style={{ width: '100%' }}
            aria-label="付款日期"
            value={draft.range}
            onChange={(v) =>
              setDraft({
                ...draft,
                range: v?.[0] && v[1] ? [v[0], v[1]] : null,
              })
            }
          />
        </FilterField>
        <FilterField label="付款方式">
          <Select
            allowClear
            placeholder="全部"
            aria-label="付款方式"
            value={draft.method}
            onChange={(v) => setDraft({ ...draft, method: v })}
            options={[
              { value: METHOD.COMPANY, label: '公司对公' },
              { value: METHOD.ADVANCE, label: '员工垫付' },
              { value: METHOD.PRIVATE, label: '私对私' },
            ]}
          />
        </FilterField>
      </FilterCard>
      <MainCard settings={columnsSet.settings} count={total} loading={loading}>
        <Table<Payment>
          rowKey="id"
          loading={loading}
          columns={columnsSet.columns}
          dataSource={rows}
          locale={{ emptyText: tableEmpty('没有符合条件的付款单', loading) }}
          scroll={{ x: tableScrollX(columnsSet.columns) }}
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

export default PaymentRecords;
