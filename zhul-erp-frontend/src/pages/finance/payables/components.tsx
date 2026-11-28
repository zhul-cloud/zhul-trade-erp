import { Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  Alert,
  App,
  Button,
  Checkbox,
  DatePicker,
  Drawer,
  Input,
  InputNumber,
  Modal,
  Radio,
  Segmented,
  Select,
  Space,
  Spin,
  Table,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Copy, Plus, Wallet } from 'lucide-react';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { EmptyState, tableEmpty } from '@/components/EmptyState';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { UserName } from '@/components/UserName';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import type { Tone } from '@/pages/inquiry/shared/constants';
import { ReceiptProgress } from '@/pages/sales/orders/parts';
import {
  type BankOption,
  bankOptionsApi,
  readBizError,
} from '@/pages/sales/service';
import { AttachmentWall, usePreview } from '@/pages/warehouse/components';
import type { Attachment } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { attrTone } from '@/theme/palette';
import { formatAmount, formatMoney } from '@/utils/format';
import { liveRequests } from '@/utils/liveRequests';
import { DueNowHint, dueNowOf } from './dueNow';
import {
  MethodHint,
  methodMismatch,
  PAY_WAY_TONE,
  PayMethodTag,
  suggestMethod,
} from './payMethod';
import {
  ChannelIcon,
  PAY_CHANNEL,
  PAY_CHANNEL_NAMES,
  PayeeQrPanel,
} from './payQr';
import {
  type DueState,
  METHOD,
  PAY_STATUS,
  PAYEE,
  type Payable,
  type PayeeAccount,
  type PayeeOption,
  type PayeeQr,
  type PaymentLine,
  payableApi,
  paymentApi,
  SOURCE,
} from './service';

export const ACCEPT_PROOF = 'image/jpeg,image/png,image/webp,application/pdf';

const STATUS_META: Record<number, { label: string; tone: Tone }> = {
  [PAY_STATUS.UNPAID]: { label: '未付', tone: 'gray' },
  [PAY_STATUS.PARTIAL]: { label: '部分付款', tone: 'info' },
  [PAY_STATUS.PAID]: { label: '已付清', tone: 'green' },
  [PAY_STATUS.CLOSED]: { label: '已关闭', tone: 'mute' },
};

export const PayStatusPill: React.FC<{ status?: number }> = ({ status }) => {
  const m = status ? STATUS_META[status] : undefined;
  return m ? <Pill tone={m.tone}>{m.label}</Pill> : <span>—</span>;
};

const PAYEE_LABELS: Record<number, string> = {
  [PAYEE.SUPPLIER]: '供应商',
  [PAYEE.SHOP]: '线上店铺',
  [PAYEE.FORWARDER]: '货代',
  [PAYEE.OTHER]: '其他',
  [PAYEE.EMPLOYEE]: '员工',
};

/** 收款方类型的名称（列表第二行用文字：类型是属性，不用标签） */
export const payeeTypeLabel = (type: number) => PAYEE_LABELS[type];

/** 收款方类型：属性标签，按 PAYEE_LABELS 的顺序取属性色，同列不重复（D75） */
export const PayeeTypePill: React.FC<{ type: number }> = ({ type }) => {
  const label = PAYEE_LABELS[type];
  const i = Object.keys(PAYEE_LABELS).indexOf(String(type));
  return label ? <Pill tone={attrTone(i)}>{label}</Pill> : null;
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

export const newKey = () =>
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

/** 账号每 4 位空一格，方便对着转账 */
const groupNo = (no: string) =>
  no.replace(/\s+/g, '').replace(/(.{4})(?=.)/g, '$1 ');

/** 对方收款账户：供应商资料里的收款账户，单选，可取消；账号完整显示、可复制 */
export const PayeeAccountPicker: React.FC<{
  method: number;
  accounts: PayeeAccount[];
  value?: number;
  onChange: (id?: number) => void;
  supplierId?: number;
}> = ({ method, accounts, value, onChange, supplierId }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const want =
    method === METHOD.ADVANCE ? 0 : method === METHOD.PRIVATE ? 2 : 1;
  const list = want ? accounts.filter((a) => a.accountType === want) : accounts;
  const others = accounts.length - list.length;
  const copy = (no: string) =>
    navigator.clipboard
      .writeText(no.replace(/\s+/g, ''))
      .then(() => message.success('已复制账号'))
      .catch(() => message.error('复制失败，请手动选择复制'));
  if (list.length === 0) {
    return (
      <div
        style={{
          padding: '10px 14px',
          borderRadius: 10,
          border: `1px dashed ${palette.hairline}`,
          fontSize: 13,
        }}
      >
        <span style={{ color: palette.orange }}>
          这个供应商还没有{want === 2 ? '对私' : want === 1 ? '对公' : ''}
          收款账户
        </span>
        {supplierId && (
          <Link
            to={`/supplier/list/${supplierId}`}
            target="_blank"
            style={{ marginLeft: 8 }}
          >
            去供应商资料添加
          </Link>
        )}
        <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
          可以先付款（不选账户），付款单上账户留空
        </div>
      </div>
    );
  }
  return (
    <div style={{ display: 'grid', gap: 8 }}>
      {list.map((a) => {
        const on = a.id === value;
        return (
          <Radio
            key={a.id}
            checked={on}
            onChange={() => onChange(a.id)}
            style={{
              display: 'flex',
              alignItems: 'flex-start',
              margin: 0,
              padding: '12px 14px',
              borderRadius: 10,
              border: `${on ? 1.5 : 1}px solid ${on ? palette.link : palette.hairline}`,
              background: on ? palette.inset : palette.card,
            }}
          >
            <div style={{ display: 'grid', gap: 4 }}>
              <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                <Pill
                  tone={
                    a.accountType === 2
                      ? PAY_WAY_TONE.private
                      : PAY_WAY_TONE.public
                  }
                >
                  {a.accountType === 2 ? '对私' : '对公'}
                </Pill>
                <b>{a.accountName}</b>
                {a.defaultAccount && <Pill tone="gray">默认</Pill>}
              </div>
              <div style={{ fontSize: 13, color: palette.sub }}>
                {a.bankName}
              </div>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                <span
                  style={{
                    fontFamily:
                      'ui-monospace, SFMono-Regular, Menlo, monospace',
                    fontWeight: 600,
                    fontSize: 14,
                  }}
                >
                  {groupNo(a.accountNo)}
                </span>
                <Button
                  type="link"
                  size="small"
                  icon={<Copy size="1em" />}
                  onClick={(e) => {
                    // 按钮在 Radio 的 label 里：不触发选中
                    e.preventDefault();
                    e.stopPropagation();
                    copy(a.accountNo);
                  }}
                >
                  复制
                </Button>
              </div>
              {a.payeePhoneMasked && (
                <div style={{ fontSize: 12, color: palette.mute }}>
                  手机 {a.payeePhoneMasked}
                </div>
              )}
            </div>
          </Radio>
        );
      })}
      {value && (
        <Button
          type="link"
          size="small"
          style={{ justifySelf: 'start', padding: 0 }}
          onClick={() => onChange(undefined)}
        >
          不选对方账户
        </Button>
      )}
      {others > 0 && (
        <div style={{ fontSize: 12, color: palette.mute }}>
          另有 {others} 个{want === 1 ? '对私' : '对公'}账户，切换到「
          {want === 1 ? '私对私' : '公司对公'}」查看
        </div>
      )}
    </div>
  );
};

/**
 * 登记付款：选收款方（或从入口带出）→ 列出同一收款方、同一币种有未付或欠款的应付单 → 按到期先后自动分配，可以改。
 * 公司对公需要「付款登记」权限，员工垫付需要「垫付登记」权限（或付款登记）。
 */
export const RegisterPaymentDrawer: React.FC<{
  open: boolean;
  /** 从应付单、采购订单、运费账单打开时带出 */
  anchorId?: number;
  /** 默认付款方式 */
  defaultMethod?: number;
  /** 从采购订单付款面板打开：付款少于未付时可以把剩下的登记为供应商优惠 */
  allowDiscount?: boolean;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, anchorId, defaultMethod, allowDiscount, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canCompany = !!access['finance:payment:register'];
  const canAdvance = !!access['finance:payment:advance'] || canCompany;
  const [payees, setPayees] = useState<PayeeOption[]>([]);
  const [anchor, setAnchor] = useState<number>();
  const [rows, setRows] = useState<Payable[]>([]);
  const [loading, setLoading] = useState(false);
  const [method, setMethodState] = useState<number>(METHOD.COMPANY);
  /** 财务手动选过付款方式后，不再按应付单约定预选 */
  const [methodTouched, setMethodTouched] = useState(false);
  const setMethod = (m: number) => {
    setMethodState(m);
    setMethodTouched(true);
  };
  const [accounts, setAccounts] = useState<BankOption[]>([]);
  const [accountId, setAccountId] = useState<number>();
  const [payeeAccounts, setPayeeAccounts] = useState<PayeeAccount[]>([]);
  const [payeeAccountId, setPayeeAccountId] = useState<number>();
  const [payChannel, setPayChannel] = useState<number>(PAY_CHANNEL.BANK);
  const [payeeQrs, setPayeeQrs] = useState<PayeeQr[]>([]);
  const [payDate, setPayDate] = useState<Dayjs>(dayjs());
  const [amount, setAmount] = useState<number | null>(null);
  const [amountCny, setAmountCny] = useState<number | null>(null);
  const [alloc, setAlloc] = useState<Record<number, number>>({});
  const [credits, setCredits] = useState<Record<number, number>>({});
  const [files, setFiles] = useState<Attachment[]>([]);
  const [remark, setRemark] = useState('');
  const [discountOn, setDiscountOn] = useState(false);
  const [discountName, setDiscountName] = useState('平台优惠');
  const [key, setKey] = useState(newKey);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setKey(newKey());
    setAnchor(anchorId);
    setMethodState(
      defaultMethod ?? (canCompany ? METHOD.COMPANY : METHOD.ADVANCE),
    );
    // 指定了员工垫付（采购订单「登记垫付」）就不再按约定预选
    setMethodTouched(defaultMethod === METHOD.ADVANCE);
    setPayChannel(PAY_CHANNEL.BANK);
    setPayDate(dayjs());
    setAmount(null);
    setAmountCny(null);
    setAlloc({});
    setCredits({});
    setFiles([]);
    setRemark('');
    setDiscountOn(false);
    setDiscountName('平台优惠');
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

  // 关闭或换了应付单后，上一次的取数迟到时丢弃，核销分配不能落到上一张应付单上（audit UI-056）
  const loadSeq = useRef(0);
  const load = useCallback(
    async (id: number) => {
      const seq = ++loadSeq.current;
      setLoading(true);
      try {
        const list = await payableApi.open(id);
        if (seq !== loadSeq.current) return;
        setRows(list);
        const due = list
          .filter((r) => r.status !== PAY_STATUS.CLOSED)
          .reduce((s, r) => s + r.unpaidAmount, 0);
        const own = list.find((r) => r.id === id);
        // 默认付本单的当期应付（按付款计划算出）；本单已付清时付同收款方全部未付
        const first = round2(own && own.unpaidAmount > 0 ? dueNowOf(own) : due);
        setAmount(first);
        setAlloc(
          own && own.unpaidAmount > 0
            ? { [own.id]: first }
            : autoFill(list, first),
        );
      } catch (e) {
        if (seq === loadSeq.current) message.error(readBizError(e).message);
      } finally {
        if (seq === loadSeq.current) setLoading(false);
      }
    },
    [message],
  );

  useEffect(() => {
    if (open && anchor) load(anchor);
    return () => {
      loadSeq.current += 1;
    };
  }, [open, anchor, load]);

  useEffect(() => {
    setPayeeAccounts([]);
    setPayeeAccountId(undefined);
    if (!open || !anchor) return undefined;
    const { live, cancel } = liveRequests();
    live(paymentApi.payeeAccounts(anchor))
      .then(setPayeeAccounts)
      .catch(() => setPayeeAccounts([]));
    return cancel;
  }, [open, anchor]);

  useEffect(() => {
    setPayeeQrs([]);
    if (!open || !anchor) return undefined;
    const { live, cancel } = liveRequests();
    live(paymentApi.payeeQr(anchor))
      .then(setPayeeQrs)
      .catch(() => setPayeeQrs([]));
    return cancel;
  }, [open, anchor]);

  // 按所选应付单约定的付款方式预选（openspec payable-payment-method）：只预选、不拦截
  const pickedMethods = rows
    .filter((r) => (alloc[r.id] ?? 0) > 0)
    .map((r) => r.payMethod);
  const suggestion = suggestMethod(pickedMethods);
  const mismatch = methodMismatch(method, pickedMethods);
  useEffect(() => {
    if (!open || methodTouched || suggestion.method === undefined) return;
    // 公司对公、私对私都要「付款登记」权限；只有垫付权限的采购员保持员工垫付
    if (canCompany) setMethodState(suggestion.method);
  }, [open, methodTouched, suggestion.method, canCompany]);

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
  // 只核销本采购订单、不用欠款抵扣、付款少于未付时，剩下的可以登记为优惠
  const own = rows.find((r) => r.id === anchor);
  const allocated = Object.entries(alloc).filter(([, v]) => v > 0);
  const discountAmount =
    allowDiscount &&
    own &&
    own.sourceType === SOURCE.PO &&
    creditTotal === 0 &&
    allocated.length === 1 &&
    Number(allocated[0][0]) === own.id
      ? round2(own.unpaidAmount - allocated[0][1])
      : 0;
  const withDiscount = discountOn && discountAmount > 0;
  const balanced = allocTotal === target && target > 0;
  // 公司对公选对公账户，私对私选对私账户（收款账户里的账户类型）；私对私再按付款渠道选：
  // 银行卡付款只能选银行账户，支付宝、微信付款可选同渠道余额账户或对私银行卡（audit BUS-096）
  const channel = method === METHOD.PRIVATE ? payChannel : PAY_CHANNEL.BANK;
  const wallet = channel !== PAY_CHANNEL.BANK;
  const currencyAccounts = useMemo(
    () =>
      accounts.filter((a) => {
        const own = a.payChannel ?? PAY_CHANNEL.BANK;
        return (
          a.currencyCode === currency &&
          (a.accountType === 2) === (method === METHOD.PRIVATE) &&
          (own === channel || (wallet && own === PAY_CHANNEL.BANK))
        );
      }),
    [accounts, currency, method, channel, wallet],
  );

  // 对方账户按付款方式过滤：换方式后原选择不在范围内时改选默认账户
  useEffect(() => {
    const want =
      method === METHOD.ADVANCE ? 0 : method === METHOD.PRIVATE ? 2 : 1;
    const list = want
      ? payeeAccounts.filter((a) => a.accountType === want)
      : payeeAccounts;
    setPayeeAccountId((cur) =>
      cur && list.some((a) => a.id === cur)
        ? cur
        : (list.find((a) => a.defaultAccount) ?? list[0])?.id,
    );
  }, [method, payeeAccounts]);

  useEffect(() => {
    if (method === METHOD.ADVANCE) return;
    if (accountId && currencyAccounts.some((a) => a.id === accountId)) return;
    setAccountId(
      (currencyAccounts.find((a) => a.isDefault) ?? currencyAccounts[0])?.id,
    );
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
    if (method !== METHOD.ADVANCE && !accountId) {
      message.error('请选择我方付款账户');
      return;
    }
    setSaving(true);
    try {
      await paymentApi.register({
        method,
        payChannel: method === METHOD.PRIVATE ? payChannel : undefined,
        bankAccountId: method !== METHOD.ADVANCE ? accountId : undefined,
        payeeAccountId: wallet ? undefined : payeeAccountId,
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
        discount: withDiscount
          ? { name: discountName.trim() || undefined }
          : undefined,
        idempotencyKey: key,
      });
      message.success(
        withDiscount
          ? `付款已登记，采购订单已记优惠 ${formatAmount(discountAmount, currency)}`
          : '付款已登记',
      );
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
      title: '应付单 · 来源单号',
      width: 200,
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
      width: 80,
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
      title: `未付（${currency}）`,
      dataIndex: 'unpaidAmount',
      width: 120,
      align: 'right',
      render: (v: number) => formatMoney(v),
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
              {
                label: '私对私',
                value: METHOD.PRIVATE,
                disabled: !canCompany,
              },
            ]}
          />
          {mismatch ? (
            <MethodHint tone="warn">{mismatch}</MethodHint>
          ) : suggestion.mixed ? (
            <MethodHint tone="warn">
              所选应付单付款方式不一致，请确认付款方式
            </MethodHint>
          ) : (
            !methodTouched &&
            suggestion.method !== undefined && (
              <MethodHint tone="note">
                按应付单约定「
                {suggestion.method === METHOD.COMPANY ? '对公' : '对私'}
                」预选，可以改
              </MethodHint>
            )
          )}
          {method === METHOD.PRIVATE && (
            <div style={{ fontSize: 12, color: palette.mute, marginTop: 6 }}>
              法人等个人账户付给对方个人账户，不进报销
            </div>
          )}
          {method === METHOD.ADVANCE && (
            <div style={{ fontSize: 12, color: palette.mute, marginTop: 6 }}>
              记在你名下，进入你的待报销
            </div>
          )}
        </Field>
        {method === METHOD.PRIVATE && (
          <Field label="付款渠道" required>
            <Segmented<number>
              value={payChannel}
              onChange={setPayChannel}
              options={[1, 2, 3].map((v) => ({
                value: v,
                label: (
                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 6,
                    }}
                  >
                    {v === PAY_CHANNEL.BANK ? (
                      <Wallet size="1em" />
                    ) : (
                      <ChannelIcon channel={v} size={14} />
                    )}
                    {PAY_CHANNEL_NAMES[v]}
                  </span>
                ),
              }))}
            />
          </Field>
        )}
        {wallet && rows.length > 0 && (
          <Field label="对方收款码">
            <PayeeQrPanel
              channel={channel}
              qrs={payeeQrs}
              payeeName={rows[0].payeeName}
              supplierId={rows[0].supplierId}
              amount={amount}
              currency={currency}
            />
          </Field>
        )}
        {!wallet &&
          rows.length > 0 &&
          (rows[0].payeeType === PAYEE.SUPPLIER ||
            rows[0].payeeType === PAYEE.FORWARDER) && (
            <Field label="对方收款账户">
              <div
                style={{
                  fontSize: 12,
                  color: palette.mute,
                  marginTop: -2,
                  marginBottom: 6,
                }}
              >
                选中的账户记在付款单上
              </div>
              <PayeeAccountPicker
                method={method}
                accounts={payeeAccounts}
                value={payeeAccountId}
                onChange={setPayeeAccountId}
                supplierId={rows[0].supplierId}
              />
            </Field>
          )}
        <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap' }}>
          {method !== METHOD.ADVANCE && (
            <Field
              label={
                method === METHOD.PRIVATE
                  ? `我方付款账户（对私${wallet ? ` · ${PAY_CHANNEL_NAMES[channel]}` : ''}）`
                  : '我方付款账户'
              }
              required
              flex
            >
              <Select
                style={{ width: '100%' }}
                value={accountId}
                onChange={setAccountId}
                placeholder={`选择 ${currency} 账户`}
                options={currencyAccounts.map((a) => ({
                  value: a.id,
                  label: `${a.bankName} · ${a.accountName} ${a.accountNoMasked}`,
                }))}
                notFoundContent={`没有${method === METHOD.PRIVATE ? `对私${wallet ? `银行卡或${PAY_CHANNEL_NAMES[channel]}` : ''}` : '对公'}的 ${currency} 账户，请先在「收款账户」里添加`}
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
            {own && own.unpaidAmount > 0 && (
              <DueNowHint
                amount={amount}
                due={dueNowOf(own)}
                unpaid={own.unpaidAmount}
                note={own.dueNowNote ?? ''}
                currency={currency}
                onChange={(v) => {
                  setAmount(v);
                  // 没用欠款抵扣时只核销本单，与打开时一致；用了抵扣按到期先后重新分配
                  if (creditTotal > 0) refill(v);
                  else setAlloc({ [own.id]: v });
                }}
              />
            )}
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
        {discountAmount > 0 && (
          <div
            style={{
              padding: '10px 14px',
              borderRadius: 12,
              background: discountOn ? palette.greenSoft : palette.inset,
              border: `1px solid ${discountOn ? palette.green : palette.hairline}`,
            }}
          >
            <Checkbox
              checked={discountOn}
              onChange={(e) => setDiscountOn(e.target.checked)}
            >
              <b>
                剩下的 {formatAmount(discountAmount, currency)} 是供应商优惠
              </b>
            </Checkbox>
            {discountOn && (
              <div style={{ marginTop: 8, display: 'grid', gap: 6 }}>
                <Input
                  value={discountName}
                  maxLength={50}
                  placeholder="优惠名称，如平台优惠"
                  onChange={(e) => setDiscountName(e.target.value)}
                  aria-label="优惠名称"
                  style={{ maxWidth: 320 }}
                />
                <span style={{ fontSize: 12, color: palette.sub }}>
                  保存后采购订单自动加一行优惠{' '}
                  {formatAmount(discountAmount, currency)}
                  ，合计与应付减少，这笔付款后应付「已付清」。记错了到采购订单上修改这行优惠。
                </span>
              </div>
            )}
          </div>
        )}
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
              emptyText: tableEmpty(
                anchor ? '没有未付的应付单' : '先选择收款方',
                loading,
                true,
              ),
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

export const Field: React.FC<{
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
          <button
            type="button"
            className="zhul-link"
            key={f.id}
            onClick={() => preview.open(f)}
          >
            {files.length > 1 ? `截图 ${i + 1}` : '查看'}
          </button>
        ))}
      </Space>
      {preview.node}
    </>
  );
};

/** 付款计划表（应付单详情与付款面板共用） */
export const InstallmentTable: React.FC<{ payable: Payable }> = ({
  payable: p,
}) => {
  const c = p.currencyCode;
  return (
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
          // 不写宽度：窄屏时这一列折行，金额列不折行（UI-117）
          title: '比例 · 时点',
          key: 't',
          render: (_, i) => `${i.ratio}% · ${i.triggerName}`,
        },
        {
          title: `金额（${c}）`,
          width: 130,
          dataIndex: 'amount',
          align: 'right',
          render: (v: number) => <span className="num">{formatMoney(v)}</span>,
        },
        {
          title: '到期日',
          width: 120,
          key: 'due',
          render: (_, i) => (
            <DueTag date={i.dueDate} state={i.dueState} days={i.dueDays} />
          ),
        },
        {
          title: `已付（${c}）`,
          width: 130,
          dataIndex: 'paidAmount',
          align: 'right',
          render: (v: number) => <span className="num">{formatMoney(v)}</span>,
        },
        {
          title: '状态',
          dataIndex: 'status',
          width: 100,
          render: (v: number) => <PayStatusPill status={v} />,
        },
      ]}
    />
  );
};

/** 与应付单有关的付款记录：核销、欠款抵扣转入转出、供应商退款 */
export const PaymentLineTable: React.FC<{
  payable: Payable;
  showVoided?: boolean;
}> = ({ payable: p, showVoided }) => {
  const { palette } = useAppTheme();
  const c = p.currencyCode;
  const lines = p.payments.filter((l) => showVoided || !l.voided);
  // 详情左栏的表不横向滚动（UI-117）：日期、登记人并到单号第二行，作废并到类型第二行，
  // 「付款方式 · 账户」不写宽度、吃掉剩余空间
  const sub = (node: React.ReactNode) => (
    <div style={{ fontSize: 12, color: palette.mute }}>{node}</div>
  );
  const paymentColumns: TableColumnsType<PaymentLine> = [
    {
      title: '付款单号 · 日期',
      dataIndex: 'paymentNo',
      width: 160,
      render: (v: string, l) => (
        <div>
          <Link
            to={`/finance/payments/${l.paymentId}`}
            className="zhul-link zhul-link-doc"
          >
            {v}
          </Link>
          {sub(
            <>
              {l.payDate} · <UserName value={l.createBy} />
            </>,
          )}
        </div>
      ),
    },
    {
      title: '类型',
      key: 'r',
      width: 120,
      render: (_, l) => (
        <div>
          {RELATION[l.relation]}
          {l.otherPayableNo && sub(l.otherPayableNo)}
          {l.voided && <Pill tone="mute">已作废</Pill>}
        </div>
      ),
    },
    {
      title: '付款方式 · 账户 / 垫付人',
      key: 'm',
      render: (_, l) => (
        <div>
          {l.kind === 2 ? (
            (l.accountName ?? '—')
          ) : l.method === METHOD.ADVANCE ? (
            <span>
              <Pill tone={PAY_WAY_TONE.advance}>员工垫付</Pill> {l.advanceUser}
            </span>
          ) : l.method === METHOD.PRIVATE ? (
            <span>
              <Pill tone={PAY_WAY_TONE.private}>私对私</Pill> {l.payerAccount}
            </span>
          ) : (
            <span>
              <Pill tone={PAY_WAY_TONE.public}>公司对公</Pill> {l.accountName}
            </span>
          )}
          {l.payeeAccount && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              付至 {l.payeeAccount}
            </div>
          )}
        </div>
      ),
    },
    {
      title: `金额（${c}）`,
      dataIndex: 'amount',
      align: 'right',
      width: 130,
      render: (v: number, l) => (
        <span
          style={{
            textDecoration: l.voided ? 'line-through' : undefined,
          }}
        >
          {formatMoney(v)}
        </span>
      ),
    },
    {
      title: '截图',
      key: 'f',
      width: 80,
      render: (_, l) => <ProofLinks files={l.attachments} />,
    },
  ];

  return lines.length === 0 ? (
    <EmptyState compact title="还没有付款" />
  ) : (
    <Table<PaymentLine>
      rowKey={(l) => `${l.paymentId}-${l.relation}-${l.otherPayableNo ?? ''}`}
      size="small"
      pagination={false}
      dataSource={lines}
      columns={paymentColumns}
    />
  );
};

/** 应付单详情内容：金额汇总、付款计划、付款单（应付单详情与采购订单、运费账单付款面板共用） */
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
        <InstallmentTable payable={p} />
      </div>
      <div>
        <div style={{ fontWeight: 600, color: palette.ink, marginBottom: 8 }}>
          付款单
        </div>
        <PaymentLineTable payable={p} showVoided={showVoided} />
      </div>
    </Space>
  );
};

/**
 * 付款面板的窄版（详情页右栏 340，照销售订单收款卡，ui-redesign D44、复审 P9）：
 * 已付大字 / 应付、进度条、剩余；付款计划、付款单改为两行一条的列表，金额右对齐。
 * 付款条件不在这里重复（详情页的订单信息里已有）
 */
const PayableCompactBody: React.FC<{ payable: Payable }> = ({ payable: p }) => {
  const { palette } = useAppTheme();
  const c = p.currencyCode;
  const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
  const lines = p.payments.filter((l) => !l.voided);
  const subTitle = (text: string) => (
    <div
      style={{
        fontSize: 13,
        fontWeight: 600,
        color: palette.ink,
        paddingBottom: 6,
        borderBottom: `1px solid ${palette.hairline}`,
      }}
    >
      {text}
    </div>
  );
  const row = (
    key: React.Key,
    left: React.ReactNode,
    right: React.ReactNode,
    second: React.ReactNode,
  ) => (
    <div
      key={key}
      style={{
        padding: '8px 0',
        borderBottom: `1px solid ${palette.hairline}`,
        display: 'grid',
        gap: 2,
      }}
    >
      <div style={{ display: 'flex', gap: 8, alignItems: 'baseline' }}>
        <span style={{ flex: 1, minWidth: 0, color: palette.ink }}>{left}</span>
        <b style={{ ...num, color: palette.ink }}>{right}</b>
      </div>
      <div
        style={{
          display: 'flex',
          gap: 8,
          alignItems: 'center',
          flexWrap: 'wrap',
          fontSize: 12,
          color: palette.mute,
        }}
      >
        {second}
      </div>
    </div>
  );
  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <div>
        <div
          style={{
            display: 'flex',
            alignItems: 'baseline',
            gap: 6,
            flexWrap: 'wrap',
            marginBottom: 8,
          }}
        >
          <span style={{ fontSize: 13, color: palette.sub }}>已付</span>
          <b style={{ ...num, fontSize: 20, color: palette.ink }}>
            {formatAmount(p.paidAmount, c)}
          </b>
          <span style={{ ...num, fontSize: 13, color: palette.mute }}>
            / {formatAmount(p.amount, c)}
          </span>
        </div>
        <ReceiptProgress
          received={p.paidAmount}
          total={p.amount}
          width="100%"
          hideStatus
        />
        <div style={{ ...num, fontSize: 12, color: palette.sub, marginTop: 4 }}>
          {p.unpaidAmount > 0 && `未付 ${formatAmount(p.unpaidAmount, c)}`}
          {p.creditAmount > 0 && (
            <span style={{ color: palette.orange }}>
              {p.unpaidAmount > 0 ? ' · ' : ''}供应商欠款{' '}
              {formatAmount(p.creditAmount, c)}
            </span>
          )}
          {c !== 'CNY' && ` · 已付人民币 ${formatAmount(p.paidCny, 'CNY')}`}
        </div>
      </div>
      {p.installments.length > 0 && (
        <div>
          {subTitle('付款计划')}
          {p.installments.map((i) =>
            row(
              i.seq,
              `第 ${i.seq} 期 · ${i.ratio}% · ${i.triggerName}`,
              formatAmount(i.amount, c),
              <>
                <DueTag date={i.dueDate} state={i.dueState} days={i.dueDays} />
                <span style={num}>已付 {formatAmount(i.paidAmount, c)}</span>
                <PayStatusPill status={i.status} />
              </>,
            ),
          )}
        </div>
      )}
      <div>
        {subTitle('付款单')}
        {lines.length === 0 ? (
          <div style={{ padding: '8px 0' }}>
            <EmptyState compact title="还没有付款" />
          </div>
        ) : (
          lines.map((l) =>
            row(
              `${l.paymentId}-${l.relation}-${l.otherPayableNo ?? ''}`,
              <>
                <Link
                  className="zhul-link zhul-link-doc"
                  to={`/finance/payments/${l.paymentId}`}
                >
                  {l.paymentNo}
                </Link>{' '}
                <span style={{ fontSize: 12, color: palette.mute }}>
                  {RELATION[l.relation]}
                </span>
              </>,
              formatAmount(l.amount, c),
              <>
                <span>{l.payDate}</span>
                <span>
                  {l.kind === 2
                    ? (l.accountName ?? '—')
                    : l.method === METHOD.ADVANCE
                      ? `员工垫付 ${l.advanceUser ?? ''}`
                      : l.method === METHOD.PRIVATE
                        ? `私对私 ${l.payerAccount ?? ''}`
                        : (l.accountName ?? '—')}
                </span>
                {l.attachments.length > 0 && (
                  <ProofLinks files={l.attachments} />
                )}
              </>,
            ),
          )
        )}
      </div>
    </div>
  );
};

/** 采购订单、运费账单详情里的「付款」面板；compact 为详情页右栏的窄版 */
export const PayablePanel: React.FC<{
  sourceType: number;
  sourceId: number;
  /** 采购订单可以登记员工垫付 */
  allowAdvance?: boolean;
  /** 详情页右栏的窄版（PayableCompactBody） */
  compact?: boolean;
}> = ({ sourceType, sourceId, allowAdvance, compact }) => {
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

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  if (error) {
    return (
      <Card style={{ marginBottom: 16 }}>
        <span style={{ color: palette.red }}>付款信息加载失败：{error}</span>{' '}
        <button type="button" className="zhul-link" onClick={load}>
          重试
        </button>
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
        <Wallet size="1em" style={{ color: palette.link }} />
        <b style={{ color: palette.ink, fontSize: 16 }}>付款</b>
        <PayStatusPill status={data.status} />
        <PayMethodTag value={data.payMethod} />
        {access.payableDetail ? (
          <Link
            className="zhul-link zhul-link-doc"
            to={`/finance/payables/${data.id}`}
            style={{ fontSize: 12 }}
          >
            {data.payableNo}
          </Link>
        ) : (
          <span style={{ fontSize: 12, color: palette.mute }}>
            {data.payableNo}
          </span>
        )}
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
            icon={<Plus size="1em" />}
            onClick={() => setDrawer(-1)}
          >
            登记付款
          </Button>
        )}
      </div>
      {compact ? (
        <PayableCompactBody payable={data} />
      ) : (
        <PayableBody payable={data} />
      )}
      <RegisterPaymentDrawer
        open={!!drawer}
        anchorId={data.id}
        defaultMethod={drawer === -1 ? undefined : drawer}
        allowDiscount={data.sourceType === SOURCE.PO}
        onClose={() => setDrawer(undefined)}
        onDone={load}
      />
    </Card>
  );
};
