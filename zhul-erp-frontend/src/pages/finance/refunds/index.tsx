import { PlusOutlined } from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Badge,
  Button,
  Checkbox,
  DatePicker,
  Input,
  Modal,
  Select,
  Table,
  Tabs,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Search } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerCell } from '@/components/DocFields';
import SourceNos from '@/components/DocFields/SourceNos';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
  SummaryBar,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { MigratedPill, Pill } from '@/pages/inquiry/shared/components';
import { auditColumns } from '@/pages/purchase/components';
import { orderApi, readBizError } from '@/pages/sales/service';
import { DOC_PATHS } from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { recentRange } from '@/utils/dateRange';
import { useDictTexts } from '@/utils/dict';
import { formatAmount } from '@/utils/format';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import RefundModal from './RefundModal';
import RequestsTab from './RequestsTab';
import {
  DICT_REFUND_REASON,
  REFUND_METHODS,
  REFUND_TO_CREDIT,
  REFUND_TYPE_WITHDRAW,
  type Refund,
  type RefundTotals,
  refundApi,
  refundRequestApi,
} from './service';

const METHOD_FILTERS = [
  ...REFUND_METHODS,
  { value: 4, label: '其他转出方式（支付宝等）' },
];

// 金额不折行（audit UI-041）
const num: React.CSSProperties = {
  fontVariantNumeric: 'tabular-nums',
  whiteSpace: 'nowrap',
};

/** 帮助文字：help-texts.md「退款管理」，关键词范围以 search-scope.md 为准 */
export const HELP = {
  page: '退给客户的钱：售后退款冲减订单毛利，经营统计按退款月份单列；余额提现由余额管理生成，不影响利润',
  keyword: '可搜：退款单号、订单编号、客户名称',
  withdraw: '由余额管理生成，不影响利润',
} as const;

/** 从其他单据跳转来时地址带 keyword（退款单号） */
const keywordFromUrl = () =>
  new URLSearchParams(window.location.search).get('keyword') ?? '';

/** 日期范围：默认最近 3 个月（清空看全部）；经营统计跳转时用 URL 带的日期；按单号跳转时不限日期 */
const rangeFromUrl = (): [Dayjs, Dayjs] | null => {
  const p = new URLSearchParams(window.location.search);
  const from = p.get('dateFrom');
  const to = p.get('dateTo');
  if (from && to) return [dayjs(from), dayjs(to)];
  return keywordFromUrl() ? null : recentRange();
};

/** 先选订单再登记退款 */
const PickOrderModal: React.FC<{
  open: boolean;
  onClose: () => void;
  onPick: (soId: number) => void;
}> = ({ open, onClose, onPick }) => {
  const [options, setOptions] = useState<{ value: number; label: string }[]>(
    [],
  );
  const [soId, setSoId] = useState<number>();
  const search = (keyword: string) =>
    orderApi
      .page({ keyword, page: 1, pageSize: 20 })
      .then((res) =>
        setOptions(
          res.records
            .filter((o) => o.status === 1)
            .map((o) => ({
              value: o.id,
              label: `${o.soNo} · ${o.customerName ?? ''} · ${formatAmount(o.totalAmount, o.currencyCode)}`,
            })),
        ),
      )
      .catch(() => setOptions([]));
  useEffect(() => {
    if (open) {
      setSoId(undefined);
      search('');
    }
  }, [open]);
  return (
    <Modal
      open={open}
      title="选择要退款的订单"
      okText="下一步"
      cancelText="取消"
      okButtonProps={{ disabled: !soId }}
      onOk={() => soId && onPick(soId)}
      onCancel={onClose}
      destroyOnHidden
    >
      <Select
        style={{ width: '100%' }}
        placeholder="按订单号或客户搜索"
        value={soId}
        onChange={setSoId}
        options={options}
        showSearch={{ filterOption: false, onSearch: search }}
      />
    </Modal>
  );
};

/** 财务管理 → 退款单：给客户的退款，冲减订单毛利 */
const RefundPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canEdit = !!access['finance:refund:edit'];
  const reasons = useDictTexts(DICT_REFUND_REASON);
  const [keyword, setKeyword] = useState(keywordFromUrl);
  const [query, setQuery] = useState(keywordFromUrl);
  const [reason, setReason] = useState<string>();
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(rangeFromUrl);
  const [showVoid, setShowVoid] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<Refund[]>([]);
  const [total, setTotal] = useState(0);
  const [refundType, setRefundType] = useState<number>(0);
  const [method, setMethod] = useState<number>();
  /** 搜索区里还没点「查询」的条件 */
  const [draft, setDraft] = useState(() => ({
    reason: undefined as string | undefined,
    range: rangeFromUrl(),
    showVoid: false,
    refundType: 0,
    method: undefined as number | undefined,
  }));
  const apply = () => {
    setQuery(keyword.trim());
    setReason(draft.reason);
    setRange(draft.range);
    setShowVoid(draft.showVoid);
    setRefundType(draft.refundType);
    setMethod(draft.method);
    setPage(1);
  };
  const reset = () => {
    const d = {
      reason: undefined,
      range: recentRange(),
      showVoid: false,
      refundType: 0,
      method: undefined,
    };
    setKeyword('');
    setDraft(d);
    setQuery('');
    setReason(undefined);
    setRange(d.range);
    setShowVoid(false);
    setRefundType(0);
    setMethod(undefined);
    setPage(1);
  };
  const [sum, setSum] = useState<RefundTotals>();
  const [loading, setLoading] = useState(true);
  const [picking, setPicking] = useState(false);
  const [soId, setSoId] = useState<number>();
  const [view, setView] = useState<'refunds' | 'requests'>(() =>
    new URLSearchParams(window.location.search).get('view') === 'requests'
      ? 'requests'
      : 'refunds',
  );
  const [pendingRequests, setPendingRequests] = useState(0);
  const loadPending = useCallback(() => {
    refundRequestApi
      .counts()
      .then((c) => setPendingRequests(c.pendingFinance + c.pendingApproval))
      .catch(() => setPendingRequests(0));
  }, []);
  useEffect(() => {
    loadPending();
  }, [loadPending]);

  const load = useCallback(async () => {
    setLoading(true);
    const q = {
      keyword: query || undefined,
      reason,
      dateFrom: range?.[0].format('YYYY-MM-DD'),
      dateTo: range?.[1].format('YYYY-MM-DD'),
      showVoid: showVoid || undefined,
      refundType: refundType || undefined,
      method,
    };
    try {
      const [res, t] = await Promise.all([
        refundApi.page({ ...q, page, pageSize }),
        refundApi.totals(q),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setSum(t);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [
    query,
    reason,
    range,
    showVoid,
    refundType,
    method,
    page,
    pageSize,
    message,
  ]);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  const columns: TableColumnsType<Refund> = [
    {
      title: '退款单号',
      dataIndex: 'refundNo',
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
          onClick={() => history.push(DOC_PATHS.refund(r.id))}
        >
          {v}
        </button>
      ),
    },
    {
      title: '类型',
      dataIndex: 'refundType',
      width: 110,
      render: (v: number) =>
        v === REFUND_TYPE_WITHDRAW ? (
          <Pill tone="cyan">余额提现</Pill>
        ) : (
          <Pill tone="red">售后退款</Pill>
        ),
    },
    { title: '退款日期', dataIndex: 'refundDate', width: 110 },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 180,
      render: (v?: string) => <CustomerCell name={v} />,
    },
    { title: '原因', dataIndex: 'reasonName', width: 120 },
    {
      title: '金额',
      key: 'amount',
      width: 130,
      align: 'right',
      render: (_, r) => (
        <span style={num}>{formatAmount(r.amount, r.currencyCode)}</span>
      ),
    },
    {
      title: '人民币',
      dataIndex: 'amountCny',
      width: 120,
      align: 'right',
      render: (v: number) => <b style={num}>{formatAmount(v, 'CNY')}</b>,
    },
    {
      title: '去向',
      dataIndex: 'method',
      width: 130,
      render: (v: number, r) =>
        v === REFUND_TO_CREDIT ? (
          <Pill tone="accent">转客户余额</Pill>
        ) : (
          <div>
            {r.methodName}
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
      dataIndex: 'status',
      width: 120,
      render: (v: number, r) =>
        v === 2 ? (
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
      title: '来源单号',
      key: 'source',
      width: 160,
      render: (_, r) => (
        <SourceNos
          refs={[
            r.refundRequestId
              ? {
                  no: r.requestNo,
                  href: `/finance/refunds/requests/${r.refundRequestId}`,
                }
              : {
                  no: r.soNo,
                  href: r.soId ? `/sales/orders/${r.soId}` : undefined,
                },
          ]}
        />
      ),
    },
    ...auditColumns<Refund>(),
    {
      title: '操作',
      key: 'op',
      width: 90,
      fixed: 'right',
      render: (_, r) => (
        <span style={{ display: 'inline-flex', gap: 8 }}>
          <button
            type="button"
            className="zhul-link"
            onClick={() => history.push(DOC_PATHS.refund(r.id))}
          >
            查看
          </button>
          {r.migrated && <MigratedPill migrated />}
        </span>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        help={HELP.page}
        extra={
          canEdit && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setPicking(true)}
            >
              登记退款
            </Button>
          )
        }
      />
      <Tabs
        activeKey={view}
        onChange={(k) => setView(k as 'refunds' | 'requests')}
        items={[
          { key: 'refunds', label: '退款单' },
          {
            key: 'requests',
            label: (
              <span
                style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
              >
                退款申请
                {pendingRequests ? (
                  <Badge count={pendingRequests} color="orange" />
                ) : null}
              </span>
            ),
          },
        ]}
      />
      {view === 'requests' ? (
        <RequestsTab
          onChanged={() => {
            load();
            loadPending();
          }}
        />
      ) : (
        <>
          <SummaryBar
            items={[
              {
                key: 'afterSales',
                label: '售后退款',
                value: sum?.afterSales ?? 0,
                currency: 'CNY',
                sub: `${sum?.afterSalesCount ?? 0} 笔 · 其中转客户余额 ${formatAmount(sum?.toCredit ?? 0, 'CNY')}`,
              },
              {
                key: 'withdraw',
                label: '余额提现',
                help: HELP.withdraw,
                value: sum?.withdraw ?? 0,
                currency: 'CNY',
                sub: `${sum?.withdrawCount ?? 0} 笔`,
              },
            ]}
          />
          <FilterCard
            onSearch={apply}
            onReset={reset}
            moreCount={
              [reason, method, showVoid || undefined].filter((v) => v != null)
                .length
            }
            more={
              <>
                <FilterField label="原因">
                  <Select
                    allowClear
                    placeholder="全部"
                    aria-label="原因"
                    value={draft.reason}
                    onChange={(v) => setDraft({ ...draft, reason: v })}
                    options={reasons.map((r) => ({
                      value: r.code,
                      label: r.name,
                    }))}
                  />
                </FilterField>
                <FilterField label="去向">
                  <Select
                    allowClear
                    placeholder="全部"
                    aria-label="去向"
                    value={draft.method}
                    onChange={(v) => setDraft({ ...draft, method: v })}
                    options={METHOD_FILTERS}
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
            <FilterField label="关键词" span={2} help={HELP.keyword}>
              <Input
                allowClear
                prefix={<Search size={16} />}
                placeholder="退款单号、订单编号、客户"
                aria-label="关键词"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                onPressEnter={apply}
              />
            </FilterField>
            <FilterField label="类型">
              <Select<number>
                aria-label="类型"
                value={draft.refundType}
                onChange={(v) => setDraft({ ...draft, refundType: v })}
                options={[
                  { value: 0, label: '全部' },
                  { value: 1, label: '售后退款' },
                  { value: REFUND_TYPE_WITHDRAW, label: '余额提现' },
                ]}
              />
            </FilterField>
            <FilterField label="退款日期" span={2}>
              <DatePicker.RangePicker
                style={{ width: '100%' }}
                aria-label="退款日期"
                value={draft.range}
                onChange={(v) =>
                  setDraft({
                    ...draft,
                    range: v?.[0] && v[1] ? [v[0], v[1]] : null,
                  })
                }
              />
            </FilterField>
          </FilterCard>
          <MainCard left={`共 ${total} 条`}>
            <Table<Refund>
              rowKey="id"
              loading={loading}
              columns={columns}
              dataSource={rows}
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
        </>
      )}
      <PickOrderModal
        open={picking}
        onClose={() => setPicking(false)}
        onPick={(id) => {
          setPicking(false);
          setSoId(id);
        }}
      />
      <RefundModal
        open={!!soId}
        soId={soId}
        onClose={() => setSoId(undefined)}
        onDone={load}
      />
    </div>
  );
};

export default RefundPage;
