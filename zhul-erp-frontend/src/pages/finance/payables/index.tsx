import { PlusOutlined } from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Checkbox, Input, Select, Space, Table } from 'antd';
import dayjs from 'dayjs';
import { Banknote, Eye, Search, Undo2 } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import {
  FilterCard,
  FilterField,
  HelpTip,
  MainCard,
  PageHeader,
  QuickFilters,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { auditColumns } from '@/pages/purchase/components';
import { CURRENCIES } from '@/pages/quotation/components';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { BatchPayModal } from './BatchPayModal';
import { currencyTotals, groupPayables, MAX_BATCH_PAYABLES } from './batch';
import {
  DueTag,
  PayeeTypePill,
  PayStatusPill,
  RefundModal,
  RegisterPaymentDrawer,
} from './components';
import {
  PAY_STATUS,
  PAYEE,
  type Payable,
  type PayableQuery,
  type PayableStats,
  payableApi,
  sourcePath,
} from './service';

// 金额不折行（audit UI-041）
const num: React.CSSProperties = {
  fontVariantNumeric: 'tabular-nums',
  whiteSpace: 'nowrap',
};

interface Filters {
  keyword: string;
  payeeType?: number;
  status?: number;
  due?: string;
  currencyCode?: string;
  hasCredit: boolean;
  refundExpected: boolean;
  showClosed: boolean;
}

/** 帮助文字：help-texts.md「应付管理」 */
export const HELP = {
  page: '采购订单确认下单、运费账单确认后自动生成应付；付款按到期先后核销，多付的部分记为供应商欠款，可以抵扣下一笔或登记退款',
  list: '默认按更新时间倒序；按到期筛选时按到期日排序。已关闭且没有欠款的默认不显示',
} as const;

/** 到期类快捷筛选（原统计卡片，stat-cards.md：条件类 → 快捷筛选，按全部应付统计） */
const DUE_QUICK = ['MONTH', 'OVERDUE', 'SOON'];

/** 从其他单据跳转来时地址带 keyword（应付单号） */
const initial = (): Filters => ({
  keyword: new URLSearchParams(window.location.search).get('keyword') ?? '',
  hasCredit: false,
  refundExpected: false,
  showClosed: false,
});

/** 标记待退款的天数：当天为「今天」 */
export const refundDays = (at: string) => {
  const d = dayjs().startOf('day').diff(dayjs(at).startOf('day'), 'day');
  return d <= 0 ? '今天' : `${d} 天`;
};

/** 还能付款（可勾选批量付款）的应付：有未付且未付清、未关闭 */
const payableOpen = (r: Payable) =>
  r.unpaidAmount > 0 &&
  (r.status === PAY_STATUS.UNPAID || r.status === PAY_STATUS.PARTIAL);

/** 财务管理 → 应付单：采购订单、运费账单确认后自动生成；付款按到期先后核销 */
const PayableManagement: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canRegister = !!access['finance:payment:register'];
  const [draft, setDraft] = useState<Filters>(initial);
  const [filters, setFilters] = useState<Filters>(initial);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<Payable[]>([]);
  const [total, setTotal] = useState(0);
  const [stats, setStats] = useState<PayableStats>();
  const [loading, setLoading] = useState(true);
  const [payAnchor, setPayAnchor] = useState<number | 'pick'>();
  const [refund, setRefund] = useState<Payable>();
  const [picked, setPicked] = useState<Payable[]>([]);
  const [batchOpen, setBatchOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const q: PayableQuery = {
        page,
        pageSize,
        keyword: filters.keyword.trim() || undefined,
        payeeType: filters.payeeType,
        status: filters.status,
        due: filters.due,
        currencyCode: filters.currencyCode,
        hasCredit: filters.hasCredit || undefined,
        refundExpected: filters.refundExpected || undefined,
        showClosed: filters.showClosed || undefined,
      };
      const [res, s] = await Promise.all([
        payableApi.page(q),
        payableApi.stats(),
      ]);
      setRows(res.records);
      // 已选的应付换成刚取到的最新数据，已付清、已关闭的剔除，已选合计与批量付款默认金额按最新未付（audit UI-076）
      setPicked((cur) =>
        cur.flatMap((p) => {
          const fresh = res.records.find((r) => r.id === p.id);
          if (!fresh) return [p];
          return payableOpen(fresh) ? [fresh] : [];
        }),
      );
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

  const openDetail = (id: number) => history.push(`/finance/payables/${id}`);

  const apply = (next: Partial<Filters>) => {
    const f = { ...draft, ...next };
    setDraft(f);
    setFilters(f);
    setPage(1);
  };

  const quickValue = filters.hasCredit
    ? ['CREDIT']
    : filters.due && DUE_QUICK.includes(filters.due)
      ? [filters.due]
      : [];
  const onQuick = (v: string[]) => {
    // 四个条件互斥，取刚点的那个；再点一次取消
    const next = v.find((x) => !quickValue.includes(x));
    if (next === 'CREDIT') apply({ hasCredit: true, due: undefined });
    else if (next) apply({ due: next, hasCredit: false });
    else apply({ due: undefined, hasCredit: false });
  };
  const quickLabel = (name: string, amount?: number, n?: string) =>
    `${name} · ${formatAmount(amount ?? 0, 'CNY')}${n ? ` · ${n}` : ''}`;

  const columns: TableColumnsType<Payable> = [
    {
      title: '编号',
      dataIndex: 'payableNo',
      width: 140,
      fixed: 'left',
      render: (v: string, r) => (
        <button
          type="button"
          className="zhul-link"
          onClick={() => openDetail(r.id)}
        >
          {v}
        </button>
      ),
    },
    {
      title: '收款方',
      key: 'payee',
      width: 200,
      render: (_, r) => (
        <div>
          <div style={{ fontWeight: 600 }}>{r.payeeName || '—'}</div>
          <PayeeTypePill type={r.payeeType} />
        </div>
      ),
    },
    {
      title: '来源单号',
      key: 'source',
      width: 170,
      render: (_, r) => (
        <button
          type="button"
          className="zhul-link"
          onClick={() => history.push(sourcePath(r))}
        >
          {r.sourceNo}
        </button>
      ),
    },
    { title: '币种', dataIndex: 'currencyCode', width: 64 },
    {
      title: '应付',
      dataIndex: 'amount',
      width: 120,
      align: 'right',
      render: (v: number, r) => (
        <span style={num}>{formatAmount(v, r.currencyCode)}</span>
      ),
    },
    {
      title: '已付',
      dataIndex: 'paidAmount',
      width: 120,
      align: 'right',
      render: (v: number, r) => (
        <span style={num}>{formatAmount(v, r.currencyCode)}</span>
      ),
    },
    {
      title: '未付',
      dataIndex: 'unpaidAmount',
      width: 120,
      align: 'right',
      render: (v: number, r) => (
        <b style={num}>{formatAmount(v, r.currencyCode)}</b>
      ),
    },
    {
      title: '欠款',
      dataIndex: 'creditAmount',
      width: 120,
      align: 'right',
      render: (v: number, r) =>
        v > 0 ? (
          <div>
            <span style={{ ...num, color: palette.orange }}>
              {formatAmount(v, r.currencyCode)}
            </span>
            {r.refundExpectedAt && (
              <div style={{ fontSize: 12, color: palette.orange }}>
                待退款 · {refundDays(r.refundExpectedAt)}
              </div>
            )}
          </div>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '下一期到期',
      key: 'due',
      width: 160,
      render: (_, r) => (
        <DueTag date={r.nextDueDate} state={r.dueState} days={r.dueDays} />
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 150,
      render: (v: number, r) => (
        <Space size={6}>
          <PayStatusPill status={v} />
          {r.settledByMigration && (
            <span style={{ fontSize: 12, color: palette.mute }}>历史迁移</span>
          )}
        </Space>
      ),
    },
    ...auditColumns<Payable>(),
    {
      title: '操作',
      key: 'op',
      // 查看、付款放行内，退款收进「更多」（ui-redesign S6）
      width: rowActionsWidth(3),
      fixed: 'right',
      render: (_, r) => (
        <RowActions
          inline={[
            {
              key: 'view',
              label: '查看',
              icon: <Eye size={18} />,
              onClick: () => openDetail(r.id),
            },
            canRegister &&
              (r.status === PAY_STATUS.UNPAID ||
                r.status === PAY_STATUS.PARTIAL) && {
                key: 'pay',
                label: '付款',
                icon: <Banknote size={18} />,
                onClick: () => setPayAnchor(r.id),
              },
          ]}
          more={[
            canRegister &&
              r.creditAmount > 0 && {
                key: 'refund',
                label: '退款',
                icon: <Undo2 size={16} />,
              },
          ]}
          onMore={() => setRefund(r)}
        />
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        help={HELP.page}
        extra={
          canRegister && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setPayAnchor('pick')}
            >
              登记付款
            </Button>
          )
        }
      />
      <FilterCard
        onSearch={() => apply({})}
        onReset={() => {
          const f = { ...initial(), keyword: '' };
          setDraft(f);
          setFilters(f);
          setPage(1);
        }}
        moreCount={
          [
            filters.due && !DUE_QUICK.includes(filters.due)
              ? filters.due
              : undefined,
            filters.currencyCode,
            filters.refundExpected || undefined,
            filters.showClosed || undefined,
          ].filter((v) => v != null).length
        }
        more={
          <>
            <FilterField label="到期">
              <Select
                allowClear
                placeholder="全部"
                aria-label="到期"
                value={draft.due}
                onChange={(v) => setDraft({ ...draft, due: v })}
                options={[
                  { value: 'OVERDUE', label: '已逾期' },
                  { value: 'SOON', label: '7 天内到期' },
                  { value: 'MONTH', label: '本月到期' },
                  { value: 'WAITING', label: '待入库' },
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
            <FilterField label="待退款">
              <Checkbox
                checked={draft.refundExpected}
                onChange={(e) =>
                  setDraft({ ...draft, refundExpected: e.target.checked })
                }
              >
                只看待退款
              </Checkbox>
            </FilterField>
            <FilterField label="已关闭">
              <Checkbox
                checked={draft.showClosed}
                onChange={(e) =>
                  setDraft({ ...draft, showClosed: e.target.checked })
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
            placeholder="应付单号、收款方、来源单号"
            aria-label="关键词"
            value={draft.keyword}
            onChange={(e) => setDraft({ ...draft, keyword: e.target.value })}
            onPressEnter={() => apply({})}
          />
        </FilterField>
        <FilterField label="状态">
          <Select
            allowClear
            placeholder="全部"
            aria-label="状态"
            value={draft.status}
            // 状态下拉切换后立即查询
            onChange={(v) => apply({ status: v })}
            options={[
              { value: PAY_STATUS.UNPAID, label: '未付' },
              { value: PAY_STATUS.PARTIAL, label: '部分付款' },
              { value: PAY_STATUS.PAID, label: '已付清' },
              { value: PAY_STATUS.CLOSED, label: '已关闭' },
            ]}
          />
        </FilterField>
        <FilterField label="收款方类型">
          <Select
            allowClear
            placeholder="全部"
            aria-label="收款方类型"
            value={draft.payeeType}
            onChange={(v) => setDraft({ ...draft, payeeType: v })}
            options={[
              { value: PAYEE.SUPPLIER, label: '供应商' },
              { value: PAYEE.SHOP, label: '线上店铺' },
              { value: PAYEE.FORWARDER, label: '货代' },
            ]}
          />
        </FilterField>
      </FilterCard>
      <MainCard
        left={
          picked.length > 0 ? (
            <>
              <b style={{ color: palette.link }}>
                已选 {picked.length} 张 · {groupPayables(picked).length}{' '}
                家收款方
              </b>
              <span>{currencyTotals(picked)}</span>
              {picked.length > MAX_BATCH_PAYABLES && (
                <span style={{ color: palette.orange }}>
                  一次最多 {MAX_BATCH_PAYABLES} 张
                </span>
              )}
            </>
          ) : (
            <>
              {`共 ${total} 条`}
              <HelpTip title={HELP.list} />
            </>
          )
        }
        right={
          picked.length > 0 && (
            <>
              <Button onClick={() => setPicked([])}>清空</Button>
              <Button
                type="primary"
                disabled={picked.length > MAX_BATCH_PAYABLES}
                onClick={() => setBatchOpen(true)}
              >
                批量付款
              </Button>
            </>
          )
        }
        quick={
          <QuickFilters
            options={[
              {
                value: 'MONTH',
                label: quickLabel(
                  '本月到期未付',
                  stats?.monthDue,
                  `${stats?.monthDueCount ?? 0} 张`,
                ),
              },
              {
                value: 'OVERDUE',
                label: quickLabel(
                  '已逾期未付',
                  stats?.overdue,
                  `${stats?.overdueCount ?? 0} 张`,
                ),
              },
              {
                value: 'SOON',
                label: quickLabel(
                  '7 天内到期',
                  stats?.soon,
                  `${stats?.soonCount ?? 0} 张`,
                ),
              },
              {
                value: 'CREDIT',
                label: quickLabel(
                  '供应商欠款',
                  stats?.credit,
                  stats?.refundExpectedCount
                    ? `${stats.refundExpectedCount} 笔待退款`
                    : undefined,
                ),
              },
            ]}
            value={quickValue}
            onChange={onQuick}
          />
        }
      >
        <Table<Payable>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          rowSelection={
            canRegister
              ? {
                  selectedRowKeys: picked.map((r) => r.id),
                  preserveSelectedRowKeys: true,
                  getCheckboxProps: (r) => ({ disabled: !payableOpen(r) }),
                  onChange: (_, list) => setPicked(list.filter(Boolean)),
                }
              : undefined
          }
          scroll={{ x: tableScrollX(columns) }}
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
      <RegisterPaymentDrawer
        open={payAnchor !== undefined}
        anchorId={typeof payAnchor === 'number' ? payAnchor : undefined}
        onClose={() => setPayAnchor(undefined)}
        onDone={load}
      />
      <BatchPayModal
        open={batchOpen}
        rows={picked}
        onClose={() => setBatchOpen(false)}
        onDone={() => {
          setBatchOpen(false);
          setPicked([]);
          load();
        }}
      />
      <RefundModal
        payable={refund}
        onClose={() => setRefund(undefined)}
        onDone={load}
      />
    </div>
  );
};

export default PayableManagement;
