import { PlusOutlined, WalletOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  Alert,
  App,
  Button,
  Checkbox,
  DatePicker,
  Drawer,
  Empty,
  Input,
  InputNumber,
  Modal,
  Segmented,
  Select,
  Space,
  Spin,
  Table,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import type { Tone } from '@/pages/inquiry/shared/constants';
import {
  type BankOption,
  bankOptionsApi,
  readBizError,
} from '@/pages/sales/service';
import { AttachmentWall, usePreview } from '@/pages/warehouse/components';
import type { Attachment } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import {
  type DueState,
  METHOD,
  PAY_STATUS,
  PAYEE,
  type Payable,
  type PayeeOption,
  type PaymentLine,
  payableApi,
  paymentApi,
} from './service';

export const ACCEPT_PROOF = 'image/jpeg,image/png,image/webp,application/pdf';

const STATUS_META: Record<number, { label: string; tone: Tone }> = {
  [PAY_STATUS.UNPAID]: { label: '未付', tone: 'gray' },
  [PAY_STATUS.PARTIAL]: { label: '部分付款', tone: 'accent' },
  [PAY_STATUS.PAID]: { label: '已付清', tone: 'green' },
  [PAY_STATUS.CLOSED]: { label: '已关闭', tone: 'mute' },
};

export const PayStatusPill: React.FC<{ status?: number }> = ({ status }) => {
  const m = status ? STATUS_META[status] : undefined;
  return m ? <Pill tone={m.tone}>{m.label}</Pill> : <span>—</span>;
};

const PAYEE_META: Record<number, { label: string; tone: Tone }> = {
  [PAYEE.SUPPLIER]: { label: '供应商', tone: 'accent' },
  [PAYEE.SHOP]: { label: '线上店铺', tone: 'violet' },
  [PAYEE.FORWARDER]: { label: '货代', tone: 'orange' },
};

export const PayeeTypePill: React.FC<{ type: number }> = ({ type }) => {
  const m = PAYEE_META[type];
  return m ? <Pill tone={m.tone}>{m.label}</Pill> : null;
};

/** 到期标记：已逾期 N 天 / N 天内到期 / 待入库 / 日期 */
export const DueTag: React.FC<{
  date?: string;
  state?: DueState;
  days?: number;
}> = ({ date, state, days }) => {
  const { palette } = useAppTheme();
  if (state === 'WAITING') return <Pill tone="gray">待入库</Pill>;
  if (!date) return <span style={{ color: palette.mute }}>—</span>;
  const d = dayjs(date).format('MM-DD');
  if (state === 'OVERDUE') {
    return (
      <Pill tone="red">
        已逾期 {days} 天 · {d}
      </Pill>
    );
  }
  if (state === 'SOON') {
    return (
      <Pill tone="orange">
        {days === 0 ? '今天到期' : `${days} 天内到期`} · {d}
      </Pill>
    );
  }
  return <span style={{ fontVariantNumeric: 'tabular-nums' }}>{d}</span>;
};

const newKey = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;

const round2 = (n: number) => Math.round(n * 100) / 100;

/** 按顺序把金额填到各应付单（不超过未付），返回 应付单 → 金额 */
const autoFill = (rows: Payable[], total: number) => {
  const out: Record<number, number> = {};
  let left = round2(total);
  for (const r of rows) {
    if (r.status === PAY_STATUS.CLOSED) continue;
    const take = round2(Math.min(left, r.unpaidAmount));
    if (take > 0) out[r.id] = take;
    left = round2(left - take);
  }
  return out;
};

/**
 * 登记付款：选收款方（或从入口带出）→ 列出同一收款方、同一币种有未付或欠款的应付单 → 按到期先后自动分配，可以改。
 * 公司对公需要「付款登记」权限，员工垫付需要「垫付登记」权限（或付款登记）。
 */
export const RegisterPaymentDrawer: React.FC<{
  open: boolean;
  /** 从应付单、采购单、对账单打开时带出 */
  anchorId?: number;
  /** 默认付款方式 */
  defaultMethod?: number;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, anchorId, defaultMethod, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canCompany = !!access['finance:payment:register'];
  const canAdvance = !!access['finance:payment:advance'] || canCompany;
  const [payees, setPayees] = useState<PayeeOption[]>([]);
  const [anchor, setAnchor] = useState<number>();
  const [rows, setRows] = useState<Payable[]>([]);
  const [loading, setLoading] = useState(false);
  const [method, setMethod] = useState<number>(METHOD.COMPANY);
  const [accounts, setAccounts] = useState<BankOption[]>([]);
  const [accountId, setAccountId] = useState<number>();
  const [payDate, setPayDate] = useState<Dayjs>(dayjs());
  const [amount, setAmount] = useState<number | null>(null);
  const [amountCny, setAmountCny] = useState<number | null>(null);
  const [alloc, setAlloc] = useState<Record<number, number>>({});
  const [credits, setCredits] = useState<Record<number, number>>({});
  const [files, setFiles] = useState<Attachment[]>([]);
  const [remark, setRemark] = useState('');
  const [key, setKey] = useState(newKey);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setKey(newKey());
    setAnchor(anchorId);
    setMethod(defaultMethod ?? (canCompany ? METHOD.COMPANY : METHOD.ADVANCE));
    setPayDate(dayjs());
    setAmount(null);
    setAmountCny(null);
    setAlloc({});
    setCredits({});
    setFiles([]);
    setRemark('');
    setRows([]);
    if (!anchorId) {
      payableApi
        .payees()
        .then(setPayees)
        .catch((e) => message.error(readBizError(e).message));
    }
    bankOptionsApi
      .options()
      .then((list) => setAccounts(list.filter((a) => a.enabled)))
      .catch(() => setAccounts([]));
  }, [open, anchorId, defaultMethod, canCompany, message]);

  const load = useCallback(
    async (id: number) => {
      setLoading(true);
      try {
        const list = await payableApi.open(id);
        setRows(list);
        const due = list
          .filter((r) => r.status !== PAY_STATUS.CLOSED)
          .reduce((s, r) => s + r.unpaidAmount, 0);
        const own = list.find((r) => r.id === id);
        const first = round2(
          own && own.unpaidAmount > 0 ? own.unpaidAmount : due,
        );
        setAmount(first);
        setAlloc(
          own && own.unpaidAmount > 0
            ? { [own.id]: own.unpaidAmount }
            : autoFill(list, first),
        );
      } catch (e) {
        message.error(readBizError(e).message);
      } finally {
        setLoading(false);
      }
    },
    [message],
  );

  useEffect(() => {
    if (open && anchor) load(anchor);
  }, [open, anchor, load]);

  const currency = rows[0]?.currencyCode ?? 'CNY';
  const foreign = currency !== 'CNY';
  const creditRows = rows.filter((r) => r.creditAmount > 0);
  const creditTotal = round2(
    Object.values(credits).reduce((s, v) => s + (v || 0), 0),
  );
  const allocTotal = round2(
    Object.values(alloc).reduce((s, v) => s + (v || 0), 0),
  );
  const target = round2((amount ?? 0) + creditTotal);
  const balanced = allocTotal === target && target > 0;
  const currencyAccounts = useMemo(
    () => accounts.filter((a) => a.currencyCode === currency),
    [accounts, currency],
  );

  useEffect(() => {
    if (method === METHOD.COMPANY && !accountId && currencyAccounts.length) {
      setAccountId(
        (currencyAccounts.find((a) => a.isDefault) ?? currencyAccounts[0]).id,
      );
    }
  }, [method, accountId, currencyAccounts]);

  const refill = (next: number, nextCredits = credits) => {
    const extra = Object.values(nextCredits).reduce((s, v) => s + (v || 0), 0);
    setAlloc(autoFill(rows, round2(next + extra)));
  };

  const submit = async () => {
    if (!balanced) {
      message.error('核销合计须等于付款金额加欠款抵扣');
      return;
    }
    if (files.length === 0) {
      message.error('请上传付款截图');
      return;
    }
    if (method === METHOD.COMPANY && !accountId) {
      message.error('请选择付款账户');
      return;
    }
    setSaving(true);
    try {
      await paymentApi.register({
        method,
        bankAccountId: method === METHOD.COMPANY ? accountId : undefined,
        payDate: payDate.format('YYYY-MM-DD'),
        amount: amount ?? 0,
        amountCny: foreign ? (amountCny ?? undefined) : undefined,
        remark: remark.trim() || undefined,
        attachmentIds: files.map((f) => f.id),
        allocations: Object.entries(alloc)
          .filter(([, v]) => v > 0)
          .map(([k, v]) => ({ payableId: Number(k), amount: v })),
        credits: Object.entries(credits)
          .filter(([, v]) => v > 0)
          .map(([k, v]) => ({ payableId: Number(k), amount: v })),
        idempotencyKey: key,
      });
      message.success('付款已登记');
      onDone();
      onClose();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const columns: TableColumnsType<Payable> = [
    {
      title: '应付单 · 来源',
      key: 'no',
      render: (_, r) => (
        <div>
          <div style={{ fontWeight: 600 }}>{r.payableNo}</div>
          <div style={{ fontSize: 12, color: palette.mute }}>{r.sourceNo}</div>
        </div>
      ),
    },
    {
      title: '期次',
      key: 'inst',
      render: (_, r) => {
        const open = r.installments.filter((i) => i.amount > i.paidAmount);
        return open.length
          ? open
              .map(
                (i) =>
                  `${r.installments.length > 1 ? `第 ${i.seq} 期 · ` : ''}${i.ratio}% ${i.triggerName}`,
              )
              .join('；')
          : '—';
      },
    },
    {
      title: '到期',
      key: 'due',
      width: 150,
      render: (_, r) => (
        <DueTag date={r.nextDueDate} state={r.dueState} days={r.dueDays} />
      ),
    },
    {
      title: '未付',
      dataIndex: 'unpaidAmount',
      width: 120,
      align: 'right',
      render: (v: number) => formatAmount(v, currency),
    },
    {
      title: '本次核销',
      key: 'alloc',
      width: 150,
      render: (_, r) =>
        r.status === PAY_STATUS.CLOSED || r.unpaidAmount <= 0 ? (
          <span style={{ color: palette.mute }}>—</span>
        ) : (
          <InputNumber<number>
            aria-label={`核销 ${r.payableNo}`}
            min={0}
            max={r.unpaidAmount}
            precision={2}
            style={{ width: '100%' }}
            value={alloc[r.id] ?? null}
            onChange={(v) => setAlloc((m) => ({ ...m, [r.id]: v ?? 0 }))}
          />
        ),
    },
  ];

  return (
    <Drawer
      open={open}
      onClose={onClose}
      size="min(820px, 96vw)"
      destroyOnHidden
      title="登记付款"
      footer={
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{ color: balanced ? palette.green : palette.orange }}>
            核销合计 {formatAmount(allocTotal, currency)}
            <span style={{ color: palette.mute }}>
              {' '}
              = 付款 {formatAmount(amount ?? 0, currency)} + 抵扣{' '}
              {formatAmount(creditTotal, currency)}
            </span>
          </span>
          <span style={{ flex: 1 }} />
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            loading={saving}
            disabled={rows.length === 0}
            onClick={submit}
          >
            确认登记
          </Button>
        </div>
      }
    >
      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        {!anchorId && (
          <Field label="收款方" required>
            <Select
              showSearch={{ optionFilterProp: 'label' }}
              placeholder="选择有未付应付或欠款的收款方"
              style={{ width: '100%' }}
              value={payees.find((p) => p.anchorId === anchor)?.key}
              options={payees.map((p) => ({
                value: p.key,
                label: `${p.payeeName}（${p.currencyCode}）未付 ${formatAmount(p.unpaid, p.currencyCode)}${p.credit > 0 ? `，欠款 ${formatAmount(p.credit, p.currencyCode)}` : ''}`,
              }))}
              onChange={(k) =>
                setAnchor(payees.find((p) => p.key === k)?.anchorId)
              }
            />
          </Field>
        )}
        {rows.length > 0 && (
          <div style={{ fontSize: 13, color: palette.sub }}>
            收款方 <b style={{ color: palette.ink }}>{rows[0].payeeName}</b>
            {' · '}
            <PayeeTypePill type={rows[0].payeeType} /> · 币种 {currency}
          </div>
        )}
        <Field label="付款方式" required>
          <Segmented<number>
            value={method}
            onChange={setMethod}
            options={[
              {
                label: '公司对公',
                value: METHOD.COMPANY,
                disabled: !canCompany,
              },
              {
                label: '员工垫付',
                value: METHOD.ADVANCE,
                disabled: !canAdvance,
              },
            ]}
          />
          {method === METHOD.ADVANCE && (
            <div style={{ fontSize: 12, color: palette.mute, marginTop: 6 }}>
              记在你名下，进入你的待报销
            </div>
          )}
        </Field>
        <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap' }}>
          {method === METHOD.COMPANY && (
            <Field label="付款账户" required flex>
              <Select
                style={{ width: '100%' }}
                value={accountId}
                onChange={setAccountId}
                placeholder={`选择 ${currency} 账户`}
                options={currencyAccounts.map((a) => ({
                  value: a.id,
                  label: `${a.bankName} · ${a.accountName} ${a.accountNoMasked}`,
                }))}
                notFoundContent={`没有 ${currency} 账户，请先在「收款账户」里添加`}
              />
            </Field>
          )}
          <Field label="付款日期" required width={180}>
            <DatePicker
              value={payDate}
              onChange={(d) => d && setPayDate(d)}
              disabledDate={(d) => d.isAfter(dayjs(), 'day')}
              allowClear={false}
              style={{ width: '100%' }}
            />
          </Field>
        </div>
        <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap' }}>
          <Field label={`付款金额（${currency}）`} required flex>
            <InputNumber<number>
              min={0}
              precision={2}
              style={{ width: '100%' }}
              value={amount}
              onChange={(v) => {
                setAmount(v);
                refill(v ?? 0);
              }}
            />
          </Field>
          {foreign && (
            <Field label="实付人民币" required flex>
              <InputNumber<number>
                min={0}
                precision={2}
                style={{ width: '100%' }}
                value={amountCny}
                onChange={setAmountCny}
                suffix={
                  amount && amountCny
                    ? `汇率 ${(amountCny / amount).toFixed(6)}`
                    : undefined
                }
              />
            </Field>
          )}
        </div>
        {creditRows.length > 0 && (
          <Alert
            type="info"
            showIcon
            title={`这家收款方有欠款 ${formatAmount(
              creditRows.reduce((s, r) => s + r.creditAmount, 0),
              currency,
            )}，可以抵扣`}
            description={
              <Space orientation="vertical" style={{ width: '100%' }}>
                {creditRows.map((r) => (
                  <div
                    key={r.id}
                    style={{ display: 'flex', alignItems: 'center', gap: 10 }}
                  >
                    <Checkbox
                      checked={!!credits[r.id]}
                      onChange={(e) => {
                        const next = {
                          ...credits,
                          [r.id]: e.target.checked ? r.creditAmount : 0,
                        };
                        setCredits(next);
                        refill(amount ?? 0, next);
                      }}
                    >
                      {r.payableNo}（{r.sourceNo}）欠款{' '}
                      {formatAmount(r.creditAmount, currency)}
                    </Checkbox>
                    {!!credits[r.id] && (
                      <InputNumber<number>
                        aria-label={`抵扣 ${r.payableNo}`}
                        min={0}
                        max={r.creditAmount}
                        precision={2}
                        value={credits[r.id]}
                        onChange={(v) => {
                          const next = { ...credits, [r.id]: v ?? 0 };
                          setCredits(next);
                          refill(amount ?? 0, next);
                        }}
                      />
                    )}
                  </div>
                ))}
              </Space>
            }
          />
        )}
        <div>
          <div style={{ fontWeight: 600, color: palette.ink, marginBottom: 8 }}>
            核销到应付单
            <span
              style={{
                fontWeight: 400,
                fontSize: 12,
                color: palette.mute,
                marginLeft: 8,
              }}
            >
              按到期先后自动分配，可以修改
            </span>
          </div>
          <Table<Payable>
            rowKey="id"
            size="small"
            loading={loading}
            columns={columns}
            dataSource={rows.filter((r) => r.unpaidAmount > 0)}
            pagination={false}
            locale={{
              emptyText: anchor ? '没有未付的应付单' : '先选择收款方',
            }}
          />
        </div>
        <Field label="付款截图" required>
          <AttachmentWall
            ownerType="PAYMENT"
            value={files}
            onChange={setFiles}
            max={10}
            accept={ACCEPT_PROOF}
          />
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 6 }}>
            银行回单、支付宝或微信截图，图片或 PDF
          </div>
        </Field>
        <Field label="说明">
          <Input.TextArea
            value={remark}
            onChange={(e) => setRemark(e.target.value)}
            maxLength={500}
            autoSize={{ minRows: 2, maxRows: 4 }}
          />
        </Field>
      </Space>
    </Drawer>
  );
};

const Field: React.FC<{
  label: string;
  required?: boolean;
  flex?: boolean;
  width?: number;
  children: React.ReactNode;
}> = ({ label, required, flex, width, children }) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        flex: flex ? 1 : undefined,
        width,
        minWidth: flex ? 200 : undefined,
      }}
    >
      <div style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}>
        {required && <span style={{ color: palette.red }}>* </span>}
        {label}
      </div>
      {children}
    </div>
  );
};

/** 供应商退款：冲减一张应付单的欠款，退到公司账户 */
export const RefundModal: React.FC<{
  payable?: Payable;
  onClose: () => void;
  onDone: () => void;
}> = ({ payable, onClose, onDone }) => {
  const { message } = App.useApp();
  const [accounts, setAccounts] = useState<BankOption[]>([]);
  const [accountId, setAccountId] = useState<number>();
  const [amount, setAmount] = useState<number | null>(null);
  const [amountCny, setAmountCny] = useState<number | null>(null);
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [files, setFiles] = useState<Attachment[]>([]);
  const [remark, setRemark] = useState('');
  const [key, setKey] = useState(newKey);
  const [saving, setSaving] = useState(false);
  const currency = payable?.currencyCode ?? 'CNY';

  useEffect(() => {
    if (!payable) return;
    setKey(newKey());
    setAmount(payable.creditAmount);
    setAmountCny(null);
    setDate(dayjs());
    setFiles([]);
    setRemark('');
    bankOptionsApi
      .options()
      .then((list) => {
        const ok = list.filter(
          (a) => a.enabled && a.currencyCode === payable.currencyCode,
        );
        setAccounts(ok);
        setAccountId((ok.find((a) => a.isDefault) ?? ok[0])?.id);
      })
      .catch(() => setAccounts([]));
  }, [payable]);

  const submit = async () => {
    if (!payable || !amount || !accountId || files.length === 0) {
      message.error('请填写退款金额、收款账户并上传截图');
      return;
    }
    setSaving(true);
    try {
      await paymentApi.refund({
        payableId: payable.id,
        amount,
        amountCny: currency !== 'CNY' ? (amountCny ?? undefined) : undefined,
        refundDate: date.format('YYYY-MM-DD'),
        bankAccountId: accountId,
        attachmentIds: files.map((f) => f.id),
        remark: remark.trim() || undefined,
        idempotencyKey: key,
      });
      message.success('退款已登记');
      onDone();
      onClose();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={!!payable}
      title={`登记供应商退款 · ${payable?.payeeName ?? ''}`}
      okText="确认登记"
      confirmLoading={saving}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
    >
      <Space orientation="vertical" size={14} style={{ width: '100%' }}>
        <Field
          label={`退款金额（${currency}，欠款 ${formatAmount(payable?.creditAmount, currency)}）`}
          required
        >
          <InputNumber<number>
            min={0.01}
            max={payable?.creditAmount}
            precision={2}
            style={{ width: '100%' }}
            value={amount}
            onChange={setAmount}
          />
        </Field>
        {currency !== 'CNY' && (
          <Field label="实收人民币" required>
            <InputNumber<number>
              min={0}
              precision={2}
              style={{ width: '100%' }}
              value={amountCny}
              onChange={setAmountCny}
            />
          </Field>
        )}
        <Field label="收款账户" required>
          <Select
            style={{ width: '100%' }}
            value={accountId}
            onChange={setAccountId}
            options={accounts.map((a) => ({
              value: a.id,
              label: `${a.bankName} · ${a.accountName} ${a.accountNoMasked}`,
            }))}
          />
        </Field>
        <Field label="退款日期" required>
          <DatePicker
            value={date}
            onChange={(d) => d && setDate(d)}
            disabledDate={(d) => d.isAfter(dayjs(), 'day')}
            allowClear={false}
          />
        </Field>
        <Field label="退款截图" required>
          <AttachmentWall
            ownerType="PAYMENT"
            value={files}
            onChange={setFiles}
            max={10}
            accept={ACCEPT_PROOF}
          />
        </Field>
        <Field label="说明">
          <Input
            value={remark}
            onChange={(e) => setRemark(e.target.value)}
            maxLength={500}
          />
        </Field>
      </Space>
    </Modal>
  );
};

const RELATION: Record<number, string> = {
  0: '付款',
  1: '欠款抵扣转入',
  2: '供应商退款',
  3: '欠款抵扣转出',
};

/** 付款截图：点击预览 */
export const ProofLinks: React.FC<{
  files: { id: number; fileName: string; kind: number }[];
}> = ({ files }) => {
  const preview = usePreview();
  if (files.length === 0) return <span>—</span>;
  return (
    <>
      <Space size={6} wrap>
        {files.map((f, i) => (
          <a key={f.id} onClick={() => preview.open(f)}>
            {files.length > 1 ? `截图 ${i + 1}` : '查看'}
          </a>
        ))}
      </Space>
      {preview.node}
    </>
  );
};

/** 应付单详情内容：金额汇总、付款计划、付款记录（应付管理详情与采购单、对账单付款面板共用） */
export const PayableBody: React.FC<{
  payable: Payable;
  showVoided?: boolean;
}> = ({ payable: p, showVoided }) => {
  const { palette } = useAppTheme();
  const c = p.currencyCode;
  const kv = (label: string, value: React.ReactNode) => (
    <div style={{ minWidth: 130 }}>
      <div style={{ fontSize: 12, color: palette.mute }}>{label}</div>
      <div
        style={{
          fontWeight: 600,
          color: palette.ink,
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {value}
      </div>
    </div>
  );
  const lines = p.payments.filter((l) => showVoided || !l.voided);
  return (
    <Space orientation="vertical" size={14} style={{ width: '100%' }}>
      <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
        {kv('应付金额', formatAmount(p.amount, c))}
        {kv('已付', formatAmount(p.paidAmount, c))}
        {kv('未付', formatAmount(p.unpaidAmount, c))}
        {kv(
          '供应商欠款',
          p.creditAmount > 0 ? (
            <span style={{ color: palette.orange }}>
              {formatAmount(p.creditAmount, c)}
            </span>
          ) : (
            '—'
          ),
        )}
        {p.paymentTerms && kv('付款条件', p.paymentTerms)}
        {c !== 'CNY' && kv('已付人民币', formatAmount(p.paidCny, 'CNY'))}
        {c !== 'CNY' &&
          !!p.exchangeDiff &&
          kv('汇兑差异', formatAmount(p.exchangeDiff, 'CNY'))}
      </div>
      <div>
        <div style={{ fontWeight: 600, color: palette.ink, marginBottom: 8 }}>
          付款计划
        </div>
        <Table
          rowKey="seq"
          size="small"
          pagination={false}
          dataSource={p.installments}
          columns={[
            {
              title: '期次',
              dataIndex: 'seq',
              width: 70,
              render: (v: number) => `第 ${v} 期`,
            },
            {
              title: '比例 · 时点',
              key: 't',
              render: (_, i) => `${i.ratio}% · ${i.triggerName}`,
            },
            {
              title: '金额',
              dataIndex: 'amount',
              align: 'right',
              render: (v: number) => formatAmount(v, c),
            },
            {
              title: '到期日',
              key: 'due',
              render: (_, i) => (
                <DueTag date={i.dueDate} state={i.dueState} days={i.dueDays} />
              ),
            },
            {
              title: '已付',
              dataIndex: 'paidAmount',
              align: 'right',
              render: (v: number) => formatAmount(v, c),
            },
            {
              title: '状态',
              dataIndex: 'status',
              width: 100,
              render: (v: number) => <PayStatusPill status={v} />,
            },
          ]}
        />
      </div>
      <div>
        <div style={{ fontWeight: 600, color: palette.ink, marginBottom: 8 }}>
          付款记录
        </div>
        {lines.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="还没有付款"
          />
        ) : (
          <Table<PaymentLine>
            rowKey={(l) =>
              `${l.paymentId}-${l.relation}-${l.otherPayableNo ?? ''}`
            }
            size="small"
            pagination={false}
            dataSource={lines}
            scroll={{ x: 820 }}
            columns={[
              { title: '编号', dataIndex: 'paymentNo', width: 150 },
              { title: '日期', dataIndex: 'payDate', width: 110 },
              {
                title: '类型',
                key: 'r',
                width: 130,
                render: (_, l) => (
                  <span>
                    {RELATION[l.relation]}
                    {l.otherPayableNo ? (
                      <span style={{ color: palette.mute }}>
                        {' '}
                        · {l.otherPayableNo}
                      </span>
                    ) : null}
                  </span>
                ),
              },
              {
                title: '付款方式 · 账户 / 垫付人',
                key: 'm',
                render: (_, l) =>
                  l.kind === 2 ? (
                    (l.accountName ?? '—')
                  ) : l.method === METHOD.ADVANCE ? (
                    <span>
                      <Pill tone="violet">员工垫付</Pill> {l.advanceUser}
                    </span>
                  ) : (
                    <span>
                      <Pill tone="accent">公司对公</Pill> {l.accountName}
                    </span>
                  ),
              },
              {
                title: '金额',
                dataIndex: 'amount',
                align: 'right',
                width: 130,
                render: (v: number, l) => (
                  <span
                    style={{
                      textDecoration: l.voided ? 'line-through' : undefined,
                    }}
                  >
                    {formatAmount(v, c)}
                  </span>
                ),
              },
              {
                title: '截图',
                key: 'f',
                width: 90,
                render: (_, l) => <ProofLinks files={l.attachments} />,
              },
              {
                title: '登记人',
                dataIndex: 'createBy',
                width: 100,
                render: (v: string, l) =>
                  l.voided ? <Pill tone="mute">已作废</Pill> : v,
              },
            ]}
          />
        )}
      </div>
    </Space>
  );
};

/** 采购单、货代对账单详情里的「付款」面板 */
export const PayablePanel: React.FC<{
  sourceType: number;
  sourceId: number;
  /** 采购单可以登记员工垫付 */
  allowAdvance?: boolean;
}> = ({ sourceType, sourceId, allowAdvance }) => {
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const [data, setData] = useState<Payable | null>();
  const [error, setError] = useState<string>();
  const [drawer, setDrawer] = useState<number>();

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setData(await payableApi.bySource(sourceType, sourceId));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [sourceType, sourceId]);

  useEffect(() => {
    load();
  }, [load]);

  if (error) {
    return (
      <Card style={{ marginBottom: 16 }}>
        <span style={{ color: palette.red }}>付款信息加载失败：{error}</span>{' '}
        <a onClick={load}>重试</a>
      </Card>
    );
  }
  if (data === undefined) {
    return (
      <Card style={{ marginBottom: 16 }}>
        <Spin size="small" />
      </Card>
    );
  }
  if (data === null) return null;
  const open =
    data.status === PAY_STATUS.UNPAID || data.status === PAY_STATUS.PARTIAL;
  return (
    <Card style={{ marginBottom: 16 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          marginBottom: 14,
          flexWrap: 'wrap',
        }}
      >
        <WalletOutlined style={{ color: palette.link }} />
        <b style={{ color: palette.ink, fontSize: 15 }}>付款</b>
        <PayStatusPill status={data.status} />
        <span style={{ fontSize: 12, color: palette.mute }}>
          {data.payableNo}
        </span>
        <span style={{ flex: 1 }} />
        {open && allowAdvance && access['finance:payment:advance'] && (
          <Button size="small" onClick={() => setDrawer(METHOD.ADVANCE)}>
            登记垫付
          </Button>
        )}
        {open && access['finance:payment:register'] && (
          <Button
            size="small"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setDrawer(METHOD.COMPANY)}
          >
            登记付款
          </Button>
        )}
      </div>
      <PayableBody payable={data} />
      <RegisterPaymentDrawer
        open={!!drawer}
        anchorId={data.id}
        defaultMethod={drawer}
        onClose={() => setDrawer(undefined)}
        onDone={load}
      />
    </Card>
  );
};
