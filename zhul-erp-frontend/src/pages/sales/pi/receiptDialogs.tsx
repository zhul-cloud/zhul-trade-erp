import { InfoCircleOutlined, WarningOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Checkbox,
  DatePicker,
  Empty,
  Input,
  InputNumber,
  Modal,
  Radio,
  Select,
  Skeleton,
} from 'antd';
import dayjs from 'dayjs';
import React, { useEffect, useMemo, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { usePaymentMethods } from '@/utils/dict';
import { formatAmount } from '@/utils/format';
import { KIND } from '../components';
import {
  type ReceiptOwner,
  type ReceiptResult,
  type ReceiptRow,
  readBizError,
} from '../service';

const label: React.CSSProperties = { fontSize: 13, marginBottom: 6 };
const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

/** 付款方式下拉：online 为 true 时只列线上项，false 只列线下项；value 为空时取默认项 */
export const PaymentMethodSelect: React.FC<{
  online: boolean;
  value?: string;
  onChange: (code: string) => void;
}> = ({ online, value, onChange }) => {
  const methods = usePaymentMethods(online);
  useEffect(() => {
    if (!value && methods.length > 0) {
      onChange((methods.find((m) => m.isDefault) ?? methods[0]).code);
    }
  }, [value, methods, onChange]);
  return (
    <Select
      style={{ width: '100%' }}
      value={value}
      onChange={onChange}
      options={methods.map((m) => ({ value: m.code, label: m.name }))}
      aria-label="付款方式"
    />
  );
};

/** 实收人民币：默认按系统汇率折算；勾选「已结汇」后填实际入账人民币，显示反算的实际汇率 */
export const ActualCnyInput: React.FC<{
  net: number;
  currency: string;
  systemRate?: number;
  value: number | null;
  onChange: (v: number | null) => void;
}> = ({ net, currency, systemRate, value, onChange }) => {
  const { palette } = useAppTheme();
  const [on, setOn] = useState(value != null);
  const rate = value != null && net > 0 ? value / net : undefined;
  return (
    <div style={{ marginTop: 12 }}>
      <Checkbox
        checked={on}
        onChange={(e) => {
          setOn(e.target.checked);
          if (!e.target.checked) onChange(null);
        }}
      >
        已结汇，填实际入账人民币
      </Checkbox>
      {on ? (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
            gap: 12,
            marginTop: 8,
          }}
        >
          <InputNumber
            style={{ width: '100%' }}
            prefix="CNY"
            min={0.01}
            precision={2}
            value={value}
            onChange={onChange}
            aria-label="实际入账人民币"
          />
          <div
            style={{ fontSize: 13, color: palette.sub, alignSelf: 'center' }}
          >
            实际汇率{' '}
            <b style={{ ...num, color: palette.ink }}>
              {rate ? rate.toFixed(6) : '—'}
            </b>
            {systemRate ? (
              <span style={{ color: palette.mute, marginLeft: 8 }}>
                系统汇率 {Number(systemRate).toFixed(6)}
              </span>
            ) : null}
          </div>
        </div>
      ) : (
        net > 0 &&
        systemRate && (
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
            实收 {formatAmount(net, currency)} ≈{' '}
            {formatAmount(Math.round(net * systemRate * 100) / 100, 'CNY')}
            （系统汇率 {Number(systemRate).toFixed(6)}）
          </div>
        )
      )}
    </div>
  );
};

// ---------------------------------------------------------------- 登记平台收款

export const PlatformReceiptModal = <T extends ReceiptResult>({
  owner: pi,
  open,
  onClose,
  onDone,
}: {
  /** 收款归属：PI 或手动创建的订单 */
  owner?: ReceiptOwner<T>;
  open: boolean;
  onClose: () => void;
  onDone: (res: T) => void;
}) => {
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const [method, setMethod] = useState<string>();
  const [orderNo, setOrderNo] = useState('');
  const [amount, setAmount] = useState<number | null>(null);
  const [fee, setFee] = useState<number | null>(null);
  const [date, setDate] = useState<dayjs.Dayjs | null>(dayjs());
  const [actual, setActual] = useState<number | null>(null);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const cur = pi?.currencyCode ?? 'USD';

  useEffect(() => {
    if (!open) return;
    setOrderNo('');
    setAmount(pi?.remainingAmount ?? null);
    setFee(null);
    setDate(dayjs());
    setActual(null);
    setNote('');
  }, [open, pi]);

  const net = Math.round(((amount ?? 0) - (fee ?? 0)) * 100) / 100;
  const after =
    Math.round(((pi?.remainingAmount ?? 0) - (amount ?? 0)) * 100) / 100;
  const feeError = fee != null && amount != null && fee > amount;

  const save = async () => {
    if (!pi || !method || !amount || !date) return;
    setBusy(true);
    try {
      const res = await pi.platformReceipt({
        paymentMethod: method,
        platformOrderNo: orderNo.trim(),
        amount,
        platformFee: fee ?? 0,
        receiptDate: date.format('YYYY-MM-DD'),
        actualAmountCny: actual ?? undefined,
        note: note.trim() || undefined,
      });
      message.success(`已登记平台收款，收款状态：${res.receiptStatusName}`);
      onDone(res);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const submit = () => {
    if (after < 0) {
      modal.confirm({
        title: `多收 ${formatAmount(-after, cur)}，将记为预收`,
        content: '付款金额超过 PI 剩余金额，确认继续登记吗？',
        okText: '继续登记',
        onOk: save,
      });
    } else {
      save();
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={600}
      title="登记平台收款"
      footer={[
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button
          key="ok"
          type="primary"
          loading={busy}
          disabled={
            !method ||
            !orderNo.trim() ||
            !amount ||
            amount <= 0 ||
            !date ||
            feeError
          }
          onClick={submit}
        >
          登记
        </Button>,
      ]}
    >
      <div style={{ fontSize: 13, color: palette.sub, marginBottom: 12 }}>
        客户通过阿里巴巴信用保障、中国制造网等平台付款：不需要水单，登记即计入到账（按客户付款金额）。
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
          gap: 12,
        }}
      >
        <div>
          <div style={{ ...label, color: palette.sub }}>付款方式（线上）</div>
          <PaymentMethodSelect online value={method} onChange={setMethod} />
        </div>
        <div>
          <div style={{ ...label, color: palette.sub }}>平台订单号</div>
          <Input
            value={orderNo}
            maxLength={64}
            onChange={(e) => setOrderNo(e.target.value)}
            aria-label="平台订单号"
          />
        </div>
        <div>
          <div style={{ ...label, color: palette.sub }}>客户付款金额</div>
          <InputNumber
            style={{ width: '100%' }}
            prefix={cur}
            min={0.01}
            precision={2}
            value={amount}
            onChange={setAmount}
            aria-label="客户付款金额"
          />
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
            剩余 {formatAmount(pi?.remainingAmount ?? 0, cur)}
          </div>
        </div>
        <div>
          <div style={{ ...label, color: palette.sub }}>平台手续费</div>
          <InputNumber
            style={{ width: '100%' }}
            prefix={cur}
            min={0}
            precision={2}
            value={fee}
            status={feeError ? 'error' : undefined}
            onChange={setFee}
            aria-label="平台手续费"
          />
          {feeError && (
            <div style={{ fontSize: 12, color: palette.red, marginTop: 4 }}>
              平台手续费不能超过付款金额
            </div>
          )}
        </div>
        <div>
          <div style={{ ...label, color: palette.sub }}>到账日期</div>
          <DatePicker
            style={{ width: '100%' }}
            value={date}
            onChange={setDate}
            aria-label="到账日期"
          />
        </div>
        <div>
          <div style={{ ...label, color: palette.sub }}>实收</div>
          <b style={{ ...num, fontSize: 18, color: palette.ink }}>
            {formatAmount(Math.max(net, 0), cur)}
          </b>
        </div>
      </div>
      <ActualCnyInput
        net={net}
        currency={cur}
        systemRate={pi?.exchangeRate}
        value={actual}
        onChange={setActual}
      />
      {after < 0 && amount != null && (
        <div style={{ marginTop: 12, fontSize: 13, color: palette.orange }}>
          <WarningOutlined /> 多收 {formatAmount(-after, cur)}，将记为预收
        </div>
      )}
      <div style={{ marginTop: 12 }}>
        <div style={{ ...label, color: palette.sub }}>说明（选填）</div>
        <Input
          value={note}
          maxLength={300}
          onChange={(e) => setNote(e.target.value)}
        />
      </div>
    </Modal>
  );
};

// ---------------------------------------------------------------- 认领到账

export const ClaimReceiptModal = <T extends ReceiptResult>({
  owner: pi,
  open,
  onClose,
  onDone,
}: {
  /** 收款归属：PI 或手动创建的订单 */
  owner?: ReceiptOwner<T>;
  open: boolean;
  onClose: () => void;
  onDone: (res: T) => void;
}) => {
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const [rows, setRows] = useState<ReceiptRow[]>();
  const [selected, setSelected] = useState<number>();
  const [slipId, setSlipId] = useState<number>();
  const [busy, setBusy] = useState(false);
  const cur = pi?.currencyCode ?? 'USD';

  const slips = useMemo(
    () =>
      (pi?.receipts ?? []).filter(
        (r) => r.kind === KIND.SLIP && r.status === 1 && !r.matched,
      ),
    [pi],
  );

  useEffect(() => {
    if (!open || !pi) return;
    setRows(undefined);
    setSelected(undefined);
    setSlipId(undefined);
    pi.claimable()
      .then((list) => {
        setRows(list);
        setSelected(list[0]?.id);
      })
      .catch((e) => {
        setRows([]);
        message.error(readBizError(e).message);
      });
  }, [open, pi, message]);

  const picked = rows?.find((r) => r.id === selected);
  const after =
    Math.round(((pi?.remainingAmount ?? 0) - (picked?.amount ?? 0)) * 100) /
    100;

  const save = async () => {
    if (!pi || !selected) return;
    setBusy(true);
    try {
      const res = await pi.claim(selected, slipId);
      message.success(`已认领，收款状态：${res.receiptStatusName}`);
      onDone(res);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={620}
      title={`认领到账 · ${pi?.no ?? ''}`}
      footer={[
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button
          key="ok"
          type="primary"
          loading={busy}
          disabled={!selected}
          onClick={() =>
            after < 0
              ? modal.confirm({
                  title: `多收 ${formatAmount(-after, cur)}，将记为预收`,
                  content: '这笔到账超过 PI 剩余金额，确认认领吗？',
                  okText: '继续认领',
                  onOk: save,
                })
              : save()
          }
        >
          认领
        </Button>,
      ]}
    >
      <div style={{ fontSize: 13, color: palette.sub, marginBottom: 12 }}>
        财务先登记了这些到账，还没找到对应的 PI。选一笔认领到这张 PI（只列 {cur}
        ）：
      </div>
      {!rows ? (
        <Skeleton active paragraph={{ rows: 3 }} />
      ) : rows.length === 0 ? (
        <Empty description={`没有未认领的 ${cur} 到账`} />
      ) : (
        <Radio.Group
          value={selected}
          onChange={(e) => setSelected(e.target.value)}
          style={{ display: 'grid', gap: 8, width: '100%' }}
        >
          {rows.map((r) => (
            <Radio
              key={r.id}
              value={r.id}
              style={{
                padding: '10px 12px',
                borderRadius: 12,
                margin: 0,
                background:
                  selected === r.id ? palette.accentSoft : palette.inset,
                border: `1px solid ${selected === r.id ? palette.accentLine : palette.hairline}`,
              }}
            >
              <b style={{ ...num, color: palette.ink }}>
                {formatAmount(r.amount, r.currencyCode)}
              </b>
              <span
                style={{ marginLeft: 8, fontSize: 12, color: palette.mute }}
              >
                {r.receiptDate} 到账
              </span>
              <div style={{ fontSize: 12, color: palette.sub }}>
                {[
                  r.payer || '付款人未填',
                  r.bankAccountName,
                  r.paymentMethodName,
                  r.note,
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </div>
            </Radio>
          ))}
        </Radio.Group>
      )}
      {slips.length > 0 && rows && rows.length > 0 && (
        <div style={{ marginTop: 12 }}>
          <div style={{ ...label, color: palette.sub }}>对应的水单（选填）</div>
          <Select
            style={{ width: '100%' }}
            allowClear
            value={slipId}
            onChange={setSlipId}
            placeholder="选择水单"
            options={slips.map((s) => ({
              value: s.id,
              label: `${s.receiptDate ?? ''} ${formatAmount(s.amount, cur)}${s.note ? ` · ${s.note}` : ''}`,
            }))}
            aria-label="对应水单"
          />
        </div>
      )}
      {picked && after < 0 && (
        <Alert
          style={{ marginTop: 12 }}
          type="warning"
          showIcon
          title={`多收 ${formatAmount(-after, cur)}，将记为预收`}
        />
      )}
      <div style={{ marginTop: 12, fontSize: 12, color: palette.mute }}>
        <InfoCircleOutlined /> 认领后按正常到账计入
        PI；认领错了请总经理在「收款管理」取消认领。
      </div>
    </Modal>
  );
};
