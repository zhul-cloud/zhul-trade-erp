import { history, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Badge,
  Button,
  Checkbox,
  DatePicker,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Eye, Plus } from 'lucide-react';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useColumnSettings } from '@/components/ColumnSettings';
import { EmptyState } from '@/components/EmptyState';
import {
  CardTitle,
  FilterCard,
  FilterField,
  HelpTip,
  MainCard,
  PageHeader,
  SummaryBar,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { Pill } from '@/pages/inquiry/shared/components';
import { auditColumns } from '@/pages/purchase/components';
import {
  type BankOption,
  bankOptionsApi,
  type ReceiptRow,
  readBizError,
} from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import {
  UnitPill,
  UnitSelect,
  unitFilterOptions,
  useBusinessUnits,
} from '@/utils/businessUnit';
import { recentRange } from '@/utils/dateRange';
import { formatAmount } from '@/utils/format';
import { liveRequests } from '@/utils/liveRequests';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { settleCny, settleRate } from './calc';
import { ReceiptWho, SettlePill } from './components';
import { type FxPending, type FxSettlement, fxApi } from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const round2 = (v: number) => Math.round((v + Number.EPSILON) * 100) / 100;
const newKey = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
const unsettled = (r: ReceiptRow) =>
  round2(Number(r.netAmount) - Number(r.settledAmount ?? 0));

/** 登记结汇单：照银行结汇流水填写，按先进先出预览分摊 */
const RegisterModal: React.FC<{
  open: boolean;
  pending: FxPending[];
  onClose: () => void;
  onDone: () => void;
}> = ({ open, pending, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [currency, setCurrency] = useState<string>();
  const [amount, setAmount] = useState<number | null>(null);
  const [rate, setRate] = useState<number | null>(null);
  const [fee, setFee] = useState<number | null>(null);
  const [cny, setCny] = useState<number | null>(null);
  /** 人民币按银行入账手动改过：不再随金额、汇率自动重算，直到点「按汇率重算」或再改金额 / 汇率 */
  const [manual, setManual] = useState(false);
  const [accounts, setAccounts] = useState<BankOption[]>([]);
  const [accountId, setAccountId] = useState<number>();
  const [remark, setRemark] = useState('');
  const [key, setKey] = useState(newKey);
  const [saving, setSaving] = useState(false);
  const [bu] = useBusinessUnits();
  const [unit, setUnit] = useState<number>();
  const [allAccounts, setAllAccounts] = useState<BankOption[]>([]);
  // 公司有事业部时每个事业部一个先进先出池子，只看所选事业部的待结汇与人民币账户
  const pools = bu.enabled
    ? pending.filter((p) => p.businessUnitId === unit)
    : pending;

  const pickUnit = (u: number | undefined, list = allAccounts) => {
    setUnit(u);
    const ps = bu.enabled
      ? pending.filter((p) => p.businessUnitId === u)
      : pending;
    setCurrency(ps[0]?.currencyCode);
    const ok = list.filter(
      (a) =>
        a.currencyCode === 'CNY' && (!bu.enabled || a.businessUnitId === u),
    );
    setAccounts(ok);
    setAccountId((ok.find((a) => a.isDefault) ?? ok[0])?.id);
  };

  useEffect(() => {
    if (!open) return undefined;
    const { live, cancel } = liveRequests();
    setDate(dayjs());
    setAmount(null);
    setRate(null);
    setFee(null);
    setCny(null);
    setManual(false);
    setRemark('');
    setKey(newKey());
    live(bankOptionsApi.options())
      .then((list) => {
        setAllAccounts(list);
        pickUnit(pending[0]?.businessUnitId, list);
      })
      .catch(() => setAccounts([]));
    return cancel;
  }, [open, pending]);

  const pool = pools.find((p) => p.currencyCode === currency);
  /** 改金额、手续费或汇率时按「(金额 − 手续费) × 汇率」重算人民币 */
  const recalc = (a: number | null, r: number | null, f = fee) => {
    setManual(false);
    const net = a ? round2(a - (f ?? 0)) : 0;
    setCny(net > 0 && r ? settleCny(net, r) : null);
  };
  const preview = useMemo(() => {
    let left = amount ?? 0;
    return (pool?.receipts ?? []).map((r) => {
      const take = Math.min(left, unsettled(r));
      left = round2(left - take);
      const after =
        take <= 0 ? r.settleStatus : round2(take) >= unsettled(r) ? 3 : 2;
      return { r, take: round2(take), after };
    });
  }, [pool, amount]);

  const submit = async () => {
    if (!currency || !amount || !rate || !cny || !accountId) {
      message.error('请填写币种、结汇金额、结汇汇率、实际入账人民币与入账账户');
      return;
    }
    setSaving(true);
    try {
      const s = await fxApi.create({
        settleDate: date.format('YYYY-MM-DD'),
        currencyCode: currency,
        amount,
        amountCny: cny,
        feeAmount: fee ?? undefined,
        bankAccountId: accountId,
        remark: remark.trim() || undefined,
        idempotencyKey: key,
        businessUnitId: bu.enabled ? unit : undefined,
      });
      message.success(`已登记结汇单 ${s.settleNo}`);
      onDone();
      onClose();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const label = (t: string) => (
    <div style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}>
      <span style={{ color: palette.red }}>* </span>
      {t}
    </div>
  );

  return (
    <Modal
      open={open}
      title="登记结汇单"
      width={900}
      okText="确认结汇"
      cancelText="取消"
      footer={pending.length === 0 ? null : undefined}
      confirmLoading={saving}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
    >
      {pending.length === 0 ? (
        <EmptyState title="没有待结汇的外币到账" />
      ) : (
        <Space orientation="vertical" size={14} style={{ width: '100%' }}>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
              gap: 12,
            }}
          >
            <div>
              {label('结汇日期')}
              <DatePicker
                value={date}
                allowClear={false}
                disabledDate={(d) => d.isAfter(dayjs(), 'day')}
                onChange={(d) => d && setDate(d)}
                style={{ width: '100%' }}
              />
            </div>
            {bu.enabled && (
              <div>
                {label('所属事业部')}
                <UnitSelect
                  info={bu}
                  value={unit}
                  onChange={(u) => pickUnit(u)}
                  style={{ width: '100%' }}
                />
              </div>
            )}
            <div>
              {label('币种')}
              <Select
                value={currency}
                onChange={setCurrency}
                style={{ width: '100%' }}
                options={pools.map((p) => ({
                  value: p.currencyCode,
                  label: p.currencyCode,
                }))}
              />
            </div>
            <div>
              {label('结汇金额')}
              <InputNumber<number>
                min={0.01}
                max={pool?.balance}
                precision={2}
                value={amount}
                onChange={(v) => {
                  setAmount(v);
                  recalc(v, rate);
                }}
                prefix={currency}
                style={{ width: '100%' }}
                aria-label="结汇金额"
              />
            </div>
            <div>
              <div
                style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}
              >
                手续费
              </div>
              <InputNumber<number>
                min={0}
                max={amount ?? undefined}
                precision={2}
                value={fee}
                placeholder="银行从结汇金额里扣的"
                onChange={(v) => {
                  setFee(v);
                  recalc(amount, rate, v);
                }}
                prefix={currency}
                style={{ width: '100%' }}
                aria-label="手续费"
              />
            </div>
            <div>
              {label('结汇汇率')}
              <InputNumber<number>
                min={0.000001}
                precision={6}
                value={rate}
                onChange={(v) => {
                  setRate(v);
                  recalc(amount, v);
                }}
                style={{ width: '100%' }}
                aria-label="结汇汇率"
              />
            </div>
            <div>
              {label('实际入账人民币')}
              <InputNumber<number>
                min={0.01}
                precision={2}
                value={cny}
                onChange={(v) => {
                  setCny(v);
                  setManual(true);
                }}
                prefix="CNY"
                style={{ width: '100%' }}
                aria-label="实际入账人民币"
              />
              {manual && amount && cny ? (
                <div
                  style={{ fontSize: 12, color: palette.orange, marginTop: 4 }}
                >
                  已按银行入账修改，实际汇率{' '}
                  {settleRate(round2(amount - (fee ?? 0)), cny).toFixed(6)}
                  {rate ? (
                    <Button
                      type="link"
                      size="small"
                      style={{ padding: '0 0 0 6px', height: 'auto' }}
                      onClick={() => recalc(amount, rate)}
                    >
                      按汇率重算
                    </Button>
                  ) : null}
                </div>
              ) : amount && rate ? (
                <div
                  style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}
                >
                  自动计算，与银行入账不一致时可直接修改
                </div>
              ) : null}
            </div>
          </div>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <div style={{ flex: '1 1 320px' }}>
              {label('入账账户')}
              <Select
                value={accountId}
                onChange={setAccountId}
                style={{ width: '100%' }}
                options={accounts.map((a) => ({
                  value: a.id,
                  label: `${a.bankName} · ${a.accountName} ${a.accountNoMasked}${a.accountType === 2 ? '（对私）' : ''}`,
                }))}
              />
            </div>
            <div style={{ flex: '1 1 320px' }}>
              <div
                style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}
              >
                说明
              </div>
              <Input
                value={remark}
                maxLength={500}
                placeholder="如 10 月第 2 批"
                onChange={(e) => setRemark(e.target.value)}
              />
            </div>
          </div>
          <div style={{ color: palette.sub }}>
            <span>
              {bu.enabled
                ? `${bu.units.find((u) => u.id === unit)?.name ?? ''}可结汇的 `
                : ''}
              {currency} 待结汇余额{' '}
              {formatAmount(pool?.balance ?? 0, currency ?? 'USD')}
              {amount
                ? ` → 结后 ${formatAmount(round2((pool?.balance ?? 0) - amount), currency ?? 'USD')}`
                : ''}
            </span>
          </div>
          <div style={{ fontSize: 12, color: palette.mute }}>
            按到账日期先进先出分摊，一笔到账可以分次结完；还没结完的按这次的汇率估算。
          </div>
          <Table
            rowKey={(x) => x.r.id}
            size="small"
            pagination={false}
            dataSource={preview}
            scroll={{ y: 280 }}
            columns={[
              {
                title: '到账日期',
                key: 'd',
                width: 100,
                render: (_, x) => x.r.receiptDate,
              },
              {
                title: 'PI',
                key: 'pi',
                width: 180,
                render: (_, x) => <ReceiptWho r={x.r} />,
              },
              {
                title: '待结汇',
                key: 'left',
                width: 130,
                align: 'right',
                render: (_, x) =>
                  formatAmount(unsettled(x.r), x.r.currencyCode),
              },
              {
                title: '本次结汇',
                key: 'take',
                width: 130,
                align: 'right',
                render: (_, x) =>
                  x.take > 0 ? (
                    <b style={num}>{formatAmount(x.take, x.r.currencyCode)}</b>
                  ) : (
                    '—'
                  ),
              },
              {
                title: '结后',
                key: 'after',
                width: 100,
                render: (_, x) => <SettlePill status={x.after} />,
              },
            ]}
          />
        </Space>
      )}
    </Modal>
  );
};

/** 帮助文字：help-texts.md「结汇管理」 */
export const HELP = {
  page: '外币到账攒一批结汇；按到账日期先进先出分摊到每笔到账，还没结汇的按最近一次结汇汇率估算人民币',
  pending: '按先进先出顺序，下一次结汇从最上面开始',
} as const;

/** 财务管理 → 结汇单：外币到账攒一批结汇，按先进先出分摊到每笔到账 */
const FxSettlementPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canSettle = !!access['finance:fx:settle'];
  const [tab, setTab] = useState('list');
  const [pending, setPending] = useState<FxPending[]>([]);
  const [rows, setRows] = useState<FxSettlement[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [bu] = useBusinessUnits();
  const [unitFilter, setUnitFilter] = useState<number>();
  const [currency, setCurrency] = useState<string>();
  // 结汇日期默认最近 3 个月，清空看全部
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(recentRange);
  const [includeVoid, setIncludeVoid] = useState(false);
  /** 搜索区里还没点「查询」的条件 */
  const [draft, setDraft] = useState(() => ({
    unit: undefined as number | undefined,
    currency: undefined as string | undefined,
    range: recentRange() as [Dayjs, Dayjs] | null,
    includeVoid: false,
  }));
  const [loading, setLoading] = useState(true);
  const [registering, setRegistering] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [res, p] = await Promise.all([
        fxApi.page({
          page,
          pageSize,
          businessUnitId: unitFilter,
          currencyCode: currency,
          dateFrom: range?.[0].format('YYYY-MM-DD'),
          dateTo: range?.[1].format('YYYY-MM-DD'),
          includeVoid: includeVoid || undefined,
        }),
        fxApi.pending(),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setPending(p);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, unitFilter, currency, range, includeVoid, message]);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  const open = (id: number) => history.push(`/finance/fx-settlements/${id}`);

  const pendingCount = pending.reduce((s, p) => s + p.receipts.length, 0);

  const columns: TableColumnsType<FxSettlement> = [
    {
      title: '编号',
      dataIndex: 'settleNo',
      width: 150,
      fixed: 'left',
      render: (v: string, r) => (
        <button
          type="button"
          className="zhul-link"
          onClick={() => open(r.id)}
          style={{
            textDecoration: r.status === 2 ? 'line-through' : undefined,
          }}
        >
          {v}
        </button>
      ),
    },
    { title: '结汇日期', dataIndex: 'settleDate', width: 110 },
    ...(bu.enabled
      ? [
          {
            title: '所属事业部',
            key: 'unit',
            width: 180,
            render: (_: unknown, r: FxSettlement) => (
              <UnitPill info={bu} id={r.businessUnitId} />
            ),
          },
        ]
      : []),
    { title: '币种', dataIndex: 'currencyCode', width: 70 },
    {
      title: '结汇金额',
      dataIndex: 'amount',
      width: 130,
      align: 'right',
      render: (v: number, r) => (
        <span style={num}>{formatAmount(v, r.currencyCode)}</span>
      ),
    },
    {
      title: '手续费',
      dataIndex: 'feeAmount',
      width: 110,
      align: 'right',
      render: (v: number | undefined, r) =>
        Number(v ?? 0) > 0 ? (
          <span style={num}>{formatAmount(v, r.currencyCode)}</span>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '入账人民币',
      dataIndex: 'amountCny',
      width: 130,
      align: 'right',
      render: (v: number) => <b style={num}>{formatAmount(v, 'CNY')}</b>,
    },
    {
      title: '汇率',
      dataIndex: 'exchangeRate',
      width: 100,
      align: 'right',
      render: (v: number) => <span style={num}>{Number(v).toFixed(6)}</span>,
    },
    {
      title: '入账账户',
      dataIndex: 'bankAccountName',
      width: 180,
      ellipsis: true,
    },
    { title: '分摊笔数', dataIndex: 'allocationCount', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (v: number, r) =>
        v === 2 ? (
          <div>
            <Pill tone="mute">已作废</Pill>
            <div style={{ fontSize: 12, color: palette.mute }}>
              {r.voidReason}
            </div>
          </div>
        ) : r.migrated ? (
          <Pill tone="gray">迁移</Pill>
        ) : (
          <Pill tone="green">有效</Pill>
        ),
    },
    ...auditColumns<FxSettlement>(),
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
              onClick: () => open(r.id),
            },
          ]}
        />
      ),
    },
  ];
  const columnsSet = useColumnSettings('finance-fx-settlements', columns, {
    hidden: ['手续费', '分摊笔数'],
  });

  const pendingColumns: TableColumnsType<ReceiptRow> = [
    { title: '到账日期', dataIndex: 'receiptDate', width: 110 },
    {
      title: 'PI · 客户',
      key: 'pi',
      width: 220,
      render: (_, r) => <ReceiptWho r={r} />,
    },
    { title: '付款方式', dataIndex: 'paymentMethodName', width: 110 },
    {
      title: '实收',
      dataIndex: 'netAmount',
      width: 130,
      align: 'right',
      render: (v: number, r) => formatAmount(v, r.currencyCode),
    },
    {
      title: '已结汇',
      dataIndex: 'settledAmount',
      width: 120,
      align: 'right',
      render: (v: number | undefined, r) =>
        v ? (
          formatAmount(v, r.currencyCode)
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '待结汇',
      key: 'left',
      width: 130,
      align: 'right',
      render: (_, r) => (
        <b style={{ ...num, color: palette.orange }}>
          {formatAmount(unsettled(r), r.currencyCode)}
        </b>
      ),
    },
    {
      title: '估算人民币',
      dataIndex: 'netAmountCny',
      width: 130,
      align: 'right',
      render: (v: number) => (
        <span style={{ color: palette.sub }}>≈ {formatAmount(v, 'CNY')}</span>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        help={HELP.page}
        extra={
          canSettle && (
            <Button
              type="primary"
              icon={<Plus size="1em" />}
              onClick={() => setRegistering(true)}
            >
              登记结汇单
            </Button>
          )
        }
      />
      <Tabs
        activeKey={tab}
        onChange={setTab}
        items={[
          {
            key: 'list',
            label: '结汇单列表',
            children: (
              <>
                <FilterCard
                  onSearch={() => {
                    setUnitFilter(draft.unit);
                    setCurrency(draft.currency);
                    setRange(draft.range);
                    setIncludeVoid(draft.includeVoid);
                    setPage(1);
                  }}
                  onReset={() => {
                    const d = {
                      unit: undefined,
                      currency: undefined,
                      range: recentRange(),
                      includeVoid: false,
                    };
                    setDraft(d);
                    setUnitFilter(undefined);
                    setCurrency(undefined);
                    setRange(d.range);
                    setIncludeVoid(false);
                    setPage(1);
                  }}
                  moreCount={
                    [
                      bu.enabled ? unitFilter : undefined,
                      includeVoid || undefined,
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
                      <FilterField label="已作废">
                        <Checkbox
                          checked={draft.includeVoid}
                          onChange={(e) =>
                            setDraft({
                              ...draft,
                              includeVoid: e.target.checked,
                            })
                          }
                        >
                          一并显示
                        </Checkbox>
                      </FilterField>
                    </>
                  }
                >
                  <FilterField label="结汇日期" span={2}>
                    <DatePicker.RangePicker
                      style={{ width: '100%' }}
                      aria-label="结汇日期"
                      value={draft.range}
                      onChange={(v) =>
                        setDraft({
                          ...draft,
                          range: v?.[0] && v[1] ? [v[0], v[1]] : null,
                        })
                      }
                    />
                  </FilterField>
                  <FilterField label="币种">
                    <Select
                      allowClear
                      placeholder="全部"
                      aria-label="币种"
                      value={draft.currency}
                      onChange={(v) => setDraft({ ...draft, currency: v })}
                      options={['USD', 'EUR', 'GBP', 'RUB'].map((c) => ({
                        value: c,
                        label: c,
                      }))}
                    />
                  </FilterField>
                </FilterCard>
                <MainCard
                  settings={columnsSet.settings}
                  count={total}
                  loading={loading}
                >
                  <Table<FxSettlement>
                    rowKey="id"
                    loading={loading}
                    columns={columnsSet.columns}
                    dataSource={rows}
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
              </>
            ),
          },
          {
            key: 'pending',
            label: (
              <span
                style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
              >
                待结汇
                {pendingCount ? (
                  <Badge count={pendingCount} color="orange" />
                ) : null}
              </span>
            ),
            children:
              pending.length === 0 ? (
                <MainCard>
                  <EmptyState title="外币到账都已结汇" />
                </MainCard>
              ) : (
                <Space
                  orientation="vertical"
                  size={16}
                  style={{ width: '100%' }}
                >
                  {pending.map((p) => (
                    <div key={`${p.businessUnitId ?? ''}-${p.currencyCode}`}>
                      {/* 每个「事业部 + 币种」一组：原三张统计卡改为该组的汇总行，紧跟该组的到账表格 */}
                      <SummaryBar
                        items={[
                          {
                            key: 'balance',
                            label: `${bu.enabled ? `${bu.units.find((u) => u.id === p.businessUnitId)?.name ?? ''} · ` : ''}${p.currencyCode} 待结汇余额`,
                            value: p.balance,
                            currency: p.currencyCode,
                            sub: `${p.receipts.length} 笔`,
                          },
                          {
                            key: 'rate',
                            label: '估算汇率',
                            value: Number(p.estimateRate).toFixed(6),
                            sub: p.latestSettleNo
                              ? `最近一次结汇 ${p.latestSettleDate} ${p.latestSettleNo}`
                              : '还没有结汇单，按系统汇率',
                          },
                          {
                            key: 'cny',
                            label: '估算人民币',
                            value: `≈ ${formatAmount(p.estimateCny, 'CNY')}`,
                          },
                        ]}
                      />
                      <MainCard
                        left={
                          <>
                            <CardTitle>待结汇的到账</CardTitle>
                            <HelpTip title={HELP.pending} />
                          </>
                        }
                      >
                        <Table<ReceiptRow>
                          rowKey="id"
                          size="middle"
                          columns={pendingColumns}
                          dataSource={p.receipts}
                          pagination={false}
                          scroll={{ x: tableScrollX(pendingColumns) }}
                        />
                      </MainCard>
                    </div>
                  ))}
                </Space>
              ),
          },
        ]}
      />
      <RegisterModal
        open={registering}
        pending={pending}
        onClose={() => setRegistering(false)}
        onDone={load}
      />
    </div>
  );
};

export default FxSettlementPage;
