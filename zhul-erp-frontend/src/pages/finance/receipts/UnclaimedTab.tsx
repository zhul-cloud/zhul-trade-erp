import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  DatePicker,
  Input,
  InputNumber,
  Modal,
  Select,
  Table,
} from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { CURRENCIES } from '@/pages/quotation/components';
import { Card, PATHS, Pill } from '@/pages/sales/components';
import { ReasonModal, useBankOptions } from '@/pages/sales/pi/dialogs';
import {
  ActualCnyInput,
  PaymentMethodSelect,
} from '@/pages/sales/pi/receiptDialogs';
import { type ReceiptRow, readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { ledgerApi } from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const label: React.CSSProperties = { fontSize: 13, marginBottom: 6 };

/** 付款方式与线上 / 线下 */
export const MethodCell: React.FC<{ r: ReceiptRow }> = ({ r }) => (
  <span style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
    {r.paymentMethodName}
    <Pill tone={r.channel === 2 ? 'violet' : 'gray'}>
      {r.channel === 2 ? '线上' : '线下'}
    </Pill>
  </span>
);

/** 实收人民币与汇率来源 */
export const NetCnyCell: React.FC<{ r: ReceiptRow }> = ({ r }) => {
  const { palette } = useAppTheme();
  return (
    <div>
      <b style={{ ...num, color: palette.ink }}>
        {formatAmount(r.netAmountCny, 'CNY')}
      </b>
      <div
        style={{
          fontSize: 12,
          color: r.rateSource === 2 ? palette.green : palette.mute,
        }}
      >
        {r.rateSource === 2 ? '实际入账' : '系统汇率'}{' '}
        {Number(r.exchangeRate).toFixed(6)}
      </div>
    </div>
  );
};

const RegisterModal: React.FC<{
  open: boolean;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [currency, setCurrency] = useState('USD');
  const [amount, setAmount] = useState<number | null>(null);
  const [date, setDate] = useState<dayjs.Dayjs | null>(dayjs());
  const [bankId, setBankId] = useState<number>();
  const [method, setMethod] = useState<string>();
  const [payer, setPayer] = useState('');
  const [actual, setActual] = useState<number | null>(null);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const banks = useBankOptions(currency);

  useEffect(() => {
    if (!open) return;
    setAmount(null);
    setDate(dayjs());
    setMethod(undefined);
    setPayer('');
    setActual(null);
    setNote('');
  }, [open]);

  useEffect(() => {
    if (!bankId || !banks.some((b) => b.id === bankId)) {
      setBankId(banks.find((b) => b.isDefault)?.id ?? banks[0]?.id);
    }
  }, [banks, bankId]);

  const submit = async () => {
    if (!amount || !date || !bankId) return;
    setBusy(true);
    try {
      await ledgerApi.registerUnclaimed({
        currencyCode: currency,
        amount,
        receiptDate: date.format('YYYY-MM-DD'),
        bankAccountId: bankId,
        paymentMethod: method,
        payer: payer.trim() || undefined,
        actualAmountCny: actual ?? undefined,
        note: note.trim() || undefined,
      });
      message.success('已登记，业务员可以在自己的 PI 上认领');
      onDone();
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
      width={600}
      title="登记未认领到账"
      okText="登记"
      confirmLoading={busy}
      okButtonProps={{ disabled: !amount || amount <= 0 || !date || !bankId }}
      onOk={submit}
    >
      <div style={{ fontSize: 13, color: palette.sub, marginBottom: 12 }}>
        钱到了公司账户，但还不知道是哪张 PI 的：先登记，业务员在自己的 PI
        上认领。
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
          gap: 12,
        }}
      >
        <div>
          <div style={{ ...label, color: palette.sub }}>币种</div>
          <Select
            style={{ width: '100%' }}
            value={currency}
            onChange={setCurrency}
            options={CURRENCIES.map((c) => ({ value: c, label: c }))}
            aria-label="币种"
          />
        </div>
        <div>
          <div style={{ ...label, color: palette.sub }}>到账金额</div>
          <InputNumber
            style={{ width: '100%' }}
            prefix={currency}
            min={0.01}
            precision={2}
            value={amount}
            onChange={setAmount}
            aria-label="到账金额"
          />
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
          <div style={{ ...label, color: palette.sub }}>收款账户</div>
          <Select
            style={{ width: '100%' }}
            value={bankId}
            onChange={setBankId}
            placeholder={
              banks.length === 0
                ? `还没有 ${currency} 收款账户`
                : '选择收款账户'
            }
            options={banks.map((b) => ({
              value: b.id,
              label: `${b.bankName} ${b.accountNoMasked}`,
            }))}
            aria-label="收款账户"
          />
        </div>
        <div>
          <div style={{ ...label, color: palette.sub }}>付款方式</div>
          <PaymentMethodSelect
            online={false}
            value={method}
            onChange={setMethod}
          />
        </div>
        <div>
          <div style={{ ...label, color: palette.sub }}>
            付款人（银行流水上的名称）
          </div>
          <Input
            value={payer}
            maxLength={128}
            onChange={(e) => setPayer(e.target.value)}
            aria-label="付款人"
          />
        </div>
      </div>
      <ActualCnyInput
        net={amount ?? 0}
        currency={currency}
        value={actual}
        onChange={setActual}
      />
      <div style={{ marginTop: 12 }}>
        <div style={{ ...label, color: palette.sub }}>说明（选填）</div>
        <Input
          value={note}
          maxLength={300}
          placeholder="如 群里已通知"
          onChange={(e) => setNote(e.target.value)}
        />
      </div>
    </Modal>
  );
};

/** 收款管理 → 未认领到账：财务先登记、业务员在 PI 上认领；最近认领的可以取消认领 */
const UnclaimedTab: React.FC<{
  registerSignal: number;
  onCount?: (n: number) => void;
}> = ({ registerSignal, onCount }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [keyword, setKeyword] = useState('');
  const [currency, setCurrency] = useState<string>();
  const [data, setData] = useState<{
    unclaimed: ReceiptRow[];
    recentClaimed: ReceiptRow[];
  }>();
  const [loading, setLoading] = useState(true);
  const [registerOpen, setRegisterOpen] = useState(false);
  const [reason, setReason] = useState<{
    id: number;
    kind: 'void' | 'unclaim';
  }>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await ledgerApi.unclaimed(
        keyword.trim() || undefined,
        currency,
      );
      setData(res);
      if (!keyword.trim() && !currency) onCount?.(res.unclaimed.length);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [keyword, currency, message, onCount]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (registerSignal > 0) setRegisterOpen(true);
  }, [registerSignal]);

  const columns: TableColumnsType<ReceiptRow> = [
    { title: '到账日期', dataIndex: 'receiptDate', width: 110 },
    {
      title: '金额',
      key: 'amount',
      width: 130,
      align: 'right',
      render: (_, r) => (
        <b style={{ ...num, color: palette.ink }}>
          {formatAmount(r.amount, r.currencyCode)}
        </b>
      ),
    },
    {
      title: '付款人',
      dataIndex: 'payer',
      render: (v?: string) =>
        v || <span style={{ color: palette.mute }}>未填</span>,
    },
    {
      title: '付款方式',
      key: 'method',
      width: 150,
      render: (_, r) => <MethodCell r={r} />,
    },
    { title: '收款账户', dataIndex: 'bankAccountName', width: 200 },
    {
      title: '实收人民币',
      key: 'cny',
      width: 160,
      render: (_, r) => <NetCnyCell r={r} />,
    },
    {
      title: '说明',
      dataIndex: 'note',
      width: 160,
      ellipsis: true,
      render: (v?: string) => v || '—',
    },
    {
      title: '操作',
      key: 'op',
      width: 70,
      render: (_, r) => (
        <a
          style={{ color: palette.red }}
          onClick={() => setReason({ id: r.id, kind: 'void' })}
        >
          作废
        </a>
      ),
    },
  ];

  const claimedColumns: TableColumnsType<ReceiptRow> = [
    { title: '到账日期', dataIndex: 'receiptDate', width: 110 },
    {
      title: '金额',
      key: 'amount',
      width: 130,
      align: 'right',
      render: (_, r) => (
        <span style={num}>{formatAmount(r.amount, r.currencyCode)}</span>
      ),
    },
    { title: '付款人', dataIndex: 'payer', render: (v?: string) => v || '—' },
    {
      title: '认领到',
      key: 'pi',
      width: 220,
      render: (_, r) =>
        r.piId ? (
          <span>
            <a onClick={() => history.push(PATHS.pi(r.piId as number))}>
              {r.piNo}
            </a>
            <span style={{ marginLeft: 6, fontSize: 12, color: palette.mute }}>
              {r.customerName}
            </span>
          </span>
        ) : (
          '—'
        ),
    },
    {
      title: '认领人',
      key: 'by',
      width: 160,
      render: (_, r) =>
        `${r.claimedByName ?? ''} ${r.claimedAt ? dayjs(r.claimedAt).format('MM-DD HH:mm') : ''}`,
    },
    {
      title: '操作',
      key: 'op',
      width: 90,
      render: (_, r) => (
        <a
          style={{ color: palette.orange }}
          onClick={() => setReason({ id: r.id, kind: 'unclaim' })}
        >
          取消认领
        </a>
      ),
    },
  ];

  return (
    <div>
      <Card style={{ marginBottom: 16, padding: 16 }}>
        <div
          style={{
            display: 'flex',
            gap: 12,
            alignItems: 'center',
            flexWrap: 'wrap',
          }}
        >
          <Input
            allowClear
            style={{ width: 280 }}
            prefix={<SearchOutlined />}
            placeholder="付款人、金额、说明"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          <Select
            allowClear
            style={{ width: 140 }}
            placeholder="全部币种"
            value={currency}
            onChange={setCurrency}
            options={CURRENCIES.map((c) => ({ value: c, label: c }))}
          />
          <Button
            type="primary"
            icon={<PlusOutlined />}
            style={{ marginLeft: 'auto' }}
            onClick={() => setRegisterOpen(true)}
          >
            登记未认领到账
          </Button>
        </div>
      </Card>
      <Table<ReceiptRow>
        rowKey="id"
        columns={columns}
        dataSource={data?.unclaimed}
        loading={loading}
        pagination={false}
        scroll={{ x: 1100 }}
        locale={{ emptyText: '没有未认领的到账' }}
      />
      <div
        style={{ margin: '20px 0 10px', fontWeight: 700, color: palette.ink }}
      >
        最近认领
      </div>
      <Table<ReceiptRow>
        rowKey="id"
        columns={claimedColumns}
        dataSource={data?.recentClaimed}
        loading={loading}
        pagination={false}
        scroll={{ x: 900 }}
        locale={{ emptyText: '还没有认领记录' }}
      />
      <RegisterModal
        open={registerOpen}
        onClose={() => setRegisterOpen(false)}
        onDone={() => {
          setRegisterOpen(false);
          load();
        }}
      />
      <ReasonModal
        open={!!reason}
        title={reason?.kind === 'unclaim' ? '取消认领' : '作废未认领到账'}
        description={
          reason?.kind === 'unclaim'
            ? '到账回到未认领，原 PI 的已到账与收款状态随之重算。'
            : '作废后不再出现在未认领列表，记录保留。'
        }
        okText={reason?.kind === 'unclaim' ? '取消认领' : '作废'}
        onClose={() => setReason(undefined)}
        onSubmit={async (why) => {
          if (!reason) return;
          try {
            if (reason.kind === 'unclaim')
              await ledgerApi.unclaim(reason.id, why);
            else await ledgerApi.voidUnclaimed(reason.id, why);
            message.success(
              reason.kind === 'unclaim' ? '已取消认领' : '已作废',
            );
            setReason(undefined);
            load();
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
    </div>
  );
};

export default UnclaimedTab;
