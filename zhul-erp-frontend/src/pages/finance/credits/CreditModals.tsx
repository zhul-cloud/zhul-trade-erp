import {
  CalculatorOutlined,
  PaperClipOutlined,
  SendOutlined,
  UserOutlined,
} from '@ant-design/icons';
import {
  App,
  AutoComplete,
  DatePicker,
  Input,
  InputNumber,
  Modal,
  Select,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import { customerApi } from '@/pages/customer/service';
import { readBizError } from '@/pages/sales/service';
import { AttachmentWall } from '@/pages/warehouse/components';
import type { Attachment } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { useBusinessUnits } from '@/utils/businessUnit';
import { formatAmount } from '@/utils/format';
import { ACCEPT_PROOF } from '../payables/components';
import { type CreditAccount, creditApi, WITHDRAW_METHODS } from './service';

const round2 = (v: number) => Math.round(v * 100) / 100;

/** 字段标题：必填带红星 */
export const FieldLabel: React.FC<{
  text: string;
  required?: boolean;
}> = ({ text, required }) => {
  const { palette } = useAppTheme();
  return (
    <div style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}>
      {required && <span style={{ color: palette.red }}>* </span>}
      {text}
    </div>
  );
};

/** 分组标题：图标 + 名称 */
export const Section: React.FC<{ icon: React.ReactNode; title: string }> = ({
  icon,
  title,
}) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        fontWeight: 600,
        color: palette.ink,
      }}
    >
      <span style={{ color: palette.link }}>{icon}</span>
      {title}
    </div>
  );
};

/** 实际转出与账面价值的差额：正数为汇兑收益，负数为汇兑损失 */
export const FxDiff: React.FC<{ value?: number; actual?: number | null }> = ({
  value,
  actual,
}) => {
  const { palette } = useAppTheme();
  if (value == null || actual == null) return null;
  const diff = round2(value - actual);
  const gain = diff >= 0;
  return (
    <div
      style={{
        padding: '12px 14px',
        borderRadius: 12,
        background: gain ? palette.greenSoft : palette.orangeSoft,
        fontSize: 12,
        color: palette.sub,
        display: 'grid',
        gap: 4,
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <span>账面价值（按转入时锁定）</span>
        <span>{formatAmount(value, 'CNY')}</span>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <span>实际转出</span>
        <span>{formatAmount(actual, 'CNY')}</span>
      </div>
      <div
        style={{
          fontWeight: 600,
          fontSize: 13,
          color: gain ? palette.green : palette.orange,
        }}
      >
        {diff === 0
          ? '没有汇兑差额'
          : `汇兑${gain ? '收益' : '损失'} ${formatAmount(Math.abs(diff), 'CNY')}，将自动记入「其他收入 · 汇兑损益」`}
      </div>
    </div>
  );
};

/** 多收原路退回平台：日期、实际退出人民币、平台退款截图（平台名称与订单号由后端按原收款记录） */
export const PlatformRefundFields: React.FC<{
  value: PayoutValue;
  onChange: (v: PayoutValue) => void;
  bookValue?: number;
}> = ({ value, onChange, bookValue }) => (
  <>
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
        gap: 12,
      }}
    >
      <div>
        <FieldLabel text="退回日期" required />
        <DatePicker
          value={value.date}
          allowClear={false}
          disabledDate={(d) => d.isAfter(dayjs(), 'day')}
          onChange={(d) => d && onChange({ ...value, date: d })}
          style={{ width: '100%' }}
        />
      </div>
      <div>
        <FieldLabel text="实际退出人民币" required />
        <InputNumber<number>
          min={0.01}
          precision={2}
          prefix="¥"
          value={value.actualCny}
          onChange={(v) => onChange({ ...value, actualCny: v })}
          style={{ width: '100%' }}
          aria-label="实际退出人民币"
        />
      </div>
    </div>
    <FxDiff value={bookValue} actual={value.actualCny} />
    <div>
      <FieldLabel text="凭证（平台退款截图）" required />
      <AttachmentWall
        ownerType="REFUND"
        value={value.files}
        onChange={(files) => onChange({ ...value, files })}
        max={10}
        accept={ACCEPT_PROOF}
      />
    </div>
  </>
);

/** 转出方式与收款账户、实际转出人民币、凭证（转给客户与退回多收共用） */
export interface PayoutValue {
  date: Dayjs;
  methodName: string;
  payeeAccount: string;
  actualCny: number | null;
  files: Attachment[];
}

export const emptyPayout = (): PayoutValue => ({
  date: dayjs(),
  methodName: '支付宝',
  payeeAccount: '',
  actualCny: null,
  files: [],
});

export const payoutBody = (p: PayoutValue) => {
  const m = WITHDRAW_METHODS.find((x) => x.value === p.methodName);
  return {
    withdrawDate: p.date.format('YYYY-MM-DD'),
    method: m?.method ?? 4,
    methodName: m?.method === 2 ? undefined : p.methodName.trim(),
    payeeAccount: p.payeeAccount.trim() || undefined,
    actualCny: p.actualCny ?? 0,
    attachmentIds: p.files.map((f) => f.id),
  };
};

export const PayoutFields: React.FC<{
  value: PayoutValue;
  onChange: (v: PayoutValue) => void;
  /** 账面价值（人民币），用于实时显示汇兑差额 */
  bookValue?: number;
}> = ({ value, onChange, bookValue }) => (
  <>
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
        gap: 12,
      }}
    >
      <div>
        <FieldLabel text="转出日期" required />
        <DatePicker
          value={value.date}
          allowClear={false}
          disabledDate={(d) => d.isAfter(dayjs(), 'day')}
          onChange={(d) => d && onChange({ ...value, date: d })}
          style={{ width: '100%' }}
        />
      </div>
      <div>
        <FieldLabel text="转出方式" required />
        <AutoComplete
          value={value.methodName}
          options={WITHDRAW_METHODS.map((m) => ({ value: m.value }))}
          onChange={(v) => onChange({ ...value, methodName: v })}
          style={{ width: '100%' }}
          placeholder="支付宝、微信、银行汇款…"
        />
      </div>
      <div>
        <FieldLabel text="实际转出人民币" required />
        <InputNumber<number>
          min={0.01}
          precision={2}
          prefix="¥"
          value={value.actualCny}
          onChange={(v) => onChange({ ...value, actualCny: v })}
          style={{ width: '100%' }}
          aria-label="实际转出人民币"
        />
      </div>
      <div>
        <FieldLabel text="收款账户" />
        <Input
          value={value.payeeAccount}
          maxLength={128}
          placeholder="如客户支付宝账号"
          onChange={(e) => onChange({ ...value, payeeAccount: e.target.value })}
        />
      </div>
    </div>
    <FxDiff value={bookValue} actual={value.actualCny} />
    <div>
      <FieldLabel text="凭证（转账截图）" required />
      <AttachmentWall
        ownerType="REFUND"
        value={value.files}
        onChange={(files) => onChange({ ...value, files })}
        max={10}
        accept={ACCEPT_PROOF}
      />
    </div>
  </>
);

/** 转给客户：生成「余额提现」退款单，不影响任何订单的利润 */
export const WithdrawModal: React.FC<{
  account?: CreditAccount;
  open: boolean;
  onClose: () => void;
  onDone: () => void;
}> = ({ account, open, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [amount, setAmount] = useState<number | null>(null);
  const [payout, setPayout] = useState<PayoutValue>(emptyPayout);
  const [remark, setRemark] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open && account) {
      setAmount(account.available);
      setPayout(emptyPayout());
      setRemark('');
    }
  }, [open, account]);

  if (!account) return null;
  const cur = account.currencyCode;
  // 账面价值按可用余额的平均锁定价值估算；实际以先进先出为准
  const bookValue =
    amount && account.available > 0
      ? round2((account.availableCny * amount) / account.available)
      : undefined;
  const over = amount != null && amount > account.available;

  const submit = async () => {
    if (!amount || over || !payout.actualCny || payout.files.length === 0) {
      message.error('请填写转出金额、实际转出人民币并上传凭证');
      return;
    }
    setSaving(true);
    try {
      const r = await creditApi.withdraw({
        customerId: account.customerId,
        currencyCode: cur,
        businessUnitId: account.businessUnitId,
        amount,
        remark: remark.trim() || undefined,
        ...payoutBody(payout),
      });
      message.success(`已转给客户，生成 ${r.refundNo}`);
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
      open={open}
      title={
        <div>
          转给客户
          <div style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}>
            生成「余额提现」退款单，不影响任何订单的利润
          </div>
        </div>
      }
      width={560}
      okText="确认转出"
      cancelText="取消"
      confirmLoading={saving}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
    >
      <div style={{ display: 'grid', gap: 16 }}>
        <div
          style={{
            padding: '12px 14px',
            borderRadius: 12,
            background: palette.inset,
          }}
        >
          <div style={{ fontWeight: 600, color: palette.ink }}>
            {[account.customerName, cur, account.businessUnitName]
              .filter(Boolean)
              .join(' · ')}
          </div>
          <div style={{ fontSize: 12, color: palette.mute }}>
            可用余额 {formatAmount(account.available, cur)} · 人民币价值{' '}
            {formatAmount(account.availableCny, 'CNY')}
          </div>
        </div>
        <Section icon={<SendOutlined />} title="转出信息" />
        <div>
          <FieldLabel text={`转出金额（${cur}）`} required />
          <InputNumber<number>
            min={0.01}
            precision={2}
            value={amount}
            onChange={setAmount}
            status={over ? 'error' : undefined}
            style={{ width: '100%' }}
            aria-label="转出金额"
          />
          {over && (
            <div style={{ fontSize: 12, color: palette.red, marginTop: 4 }}>
              可用余额只有 {formatAmount(account.available, cur)}
              {account.occupied > 0 &&
                `，其余被 ${account.occupiers.map((o) => o.sourceNo).join('、')} 占用`}
            </div>
          )}
        </div>
        <PayoutFields
          value={payout}
          onChange={setPayout}
          bookValue={bookValue}
        />
        <Input
          placeholder="说明（选填）"
          maxLength={500}
          value={remark}
          onChange={(e) => setRemark(e.target.value)}
        />
      </div>
    </Modal>
  );
};

/** 手工调整余额：录入旧系统余额或更正，调整后不能修改 */
export const AdjustModal: React.FC<{
  open: boolean;
  /** 从某个余额账户进入时带出客户、币种、事业部 */
  account?: CreditAccount;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, account, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [bu] = useBusinessUnits();
  const [customers, setCustomers] = useState<
    { value: number; label: string }[]
  >([]);
  const [customerId, setCustomerId] = useState<number>();
  const [currency, setCurrency] = useState('USD');
  const [unitId, setUnitId] = useState<number>();
  const [amount, setAmount] = useState<number | null>(null);
  const [cny, setCny] = useState<number | null>(null);
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);
  const [files, setFiles] = useState<Attachment[]>([]);
  const [saving, setSaving] = useState(false);

  const search = (keyword: string) =>
    customerApi
      .page({ name: keyword || undefined, page: 1, pageSize: 20 })
      .then((r) =>
        setCustomers(
          r.records.map((c) => ({
            value: c.id,
            label: c.displayName || c.name,
          })),
        ),
      )
      .catch(() => setCustomers([]));

  useEffect(() => {
    if (!open) return;
    setCustomerId(account?.customerId);
    setCustomers(
      account
        ? [{ value: account.customerId, label: account.customerName ?? '' }]
        : [],
    );
    setCurrency(account?.currencyCode ?? 'USD');
    setUnitId(account?.businessUnitId);
    setAmount(null);
    setCny(null);
    setReason('');
    setTouched(false);
    setFiles([]);
  }, [open, account]);

  const reasonMissing = touched && !reason.trim();
  const submit = async () => {
    setTouched(true);
    if (!customerId || !amount || !reason.trim()) {
      return;
    }
    if (amount > 0 && !cny) {
      message.error('请填写人民币价值');
      return;
    }
    setSaving(true);
    try {
      await creditApi.adjust({
        customerId,
        currencyCode: currency,
        businessUnitId: unitId,
        amount,
        amountCny: amount > 0 ? (cny ?? undefined) : undefined,
        reason: reason.trim(),
        attachmentIds: files.map((f) => f.id),
      });
      message.success('已调整余额');
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
      open={open}
      title={
        <div>
          手工调整余额
          <div style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}>
            用于录入旧系统遗留的余额或更正；调整后不能修改，录错请再录一笔反向调整
          </div>
        </div>
      }
      width={560}
      okText="确认调整"
      cancelText="取消"
      confirmLoading={saving}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
    >
      <div style={{ display: 'grid', gap: 16 }}>
        <Section icon={<UserOutlined />} title="余额账户" />
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: bu.enabled ? '1fr 1fr' : '1fr',
            gap: 12,
          }}
        >
          <div>
            <FieldLabel text="客户" required />
            <Select
              value={customerId}
              onChange={setCustomerId}
              options={customers}
              disabled={!!account}
              placeholder="搜索客户名称"
              style={{ width: '100%' }}
              showSearch={{ filterOption: false, onSearch: search }}
              onFocus={() => customers.length === 0 && search('')}
            />
          </div>
          {bu.enabled && (
            <div>
              <FieldLabel text="事业部" required />
              <Select
                value={unitId}
                onChange={setUnitId}
                disabled={!!account}
                options={bu.units.map((u) => ({ value: u.id, label: u.name }))}
                style={{ width: '100%' }}
              />
            </div>
          )}
        </div>
        <Section icon={<CalculatorOutlined />} title="调整金额" />
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '120px 1fr 1fr',
            gap: 12,
          }}
        >
          <div>
            <FieldLabel text="币种" required />
            <Select
              value={currency}
              onChange={setCurrency}
              disabled={!!account}
              options={['USD', 'EUR', 'GBP', 'RUB', 'CNY'].map((c) => ({
                value: c,
                label: c,
              }))}
              style={{ width: '100%' }}
            />
          </div>
          <div>
            <FieldLabel text="金额（负数为减少）" required />
            <InputNumber<number>
              precision={2}
              value={amount}
              onChange={setAmount}
              style={{ width: '100%' }}
              aria-label="调整金额"
            />
          </div>
          <div>
            <FieldLabel text="人民币价值" required={(amount ?? 0) > 0} />
            <InputNumber<number>
              min={0.01}
              precision={2}
              prefix="¥"
              value={cny}
              disabled={(amount ?? 0) < 0}
              onChange={setCny}
              style={{ width: '100%' }}
              aria-label="人民币价值"
            />
          </div>
        </div>
        <div style={{ fontSize: 12, color: palette.mute, marginTop: -8 }}>
          {(amount ?? 0) < 0
            ? '减少时按先转入的先用，人民币价值自动取锁定价值'
            : '按旧系统的账面价值填写'}
        </div>
        <div>
          <FieldLabel text="调整原因" required />
          <Input.TextArea
            value={reason}
            maxLength={500}
            rows={2}
            status={reasonMissing ? 'error' : undefined}
            placeholder="请说明为什么调整，如「旧 ERP 余额迁移」"
            onChange={(e) => setReason(e.target.value)}
          />
          {reasonMissing && (
            <div style={{ fontSize: 12, color: palette.red, marginTop: 4 }}>
              请填写调整原因
            </div>
          )}
        </div>
        <div>
          <Section icon={<PaperClipOutlined />} title="凭证（选填）" />
          <div style={{ marginTop: 8 }}>
            <AttachmentWall
              ownerType="CREDIT"
              value={files}
              onChange={setFiles}
              max={10}
              accept={ACCEPT_PROOF}
            />
          </div>
        </div>
      </div>
    </Modal>
  );
};
