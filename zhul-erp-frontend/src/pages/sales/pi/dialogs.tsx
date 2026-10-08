import {
  CheckCircleOutlined,
  DownloadOutlined,
  FileExcelOutlined,
  FileImageOutlined,
  FilePdfOutlined,
  InboxOutlined,
  InfoCircleOutlined,
  LoadingOutlined,
  SendOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import type { UploadFile } from 'antd';
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
  Upload,
} from 'antd';
import dayjs from 'dayjs';
import React, { useEffect, useMemo, useState } from 'react';
import { searchCustomers } from '@/services/zhul/masterdata';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { CHANNEL, KIND, Pill, partyAddress } from '../components';
import {
  type BankOption,
  bankOptionsApi,
  type CandidateQuotation,
  type Party,
  type PartyOption,
  type Pi,
  piApi,
  type ReceiptOwner,
  type ReceiptResult,
  readBizError,
} from '../service';
import { ActualCnyInput, PaymentMethodSelect } from './receiptDialogs';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const label: React.CSSProperties = { fontSize: 13, marginBottom: 6 };

// ---------------------------------------------------------------- 导出

type Format = 'xlsx' | 'pdf' | 'jpg';
const CHANNEL_OF = {
  xlsx: CHANNEL.EXCEL,
  pdf: CHANNEL.PDF,
  jpg: CHANNEL.IMAGE,
} as const;

export const ExportModal: React.FC<{
  pi?: Pi;
  open: boolean;
  onClose: () => void;
  onExported: (channel: number) => void;
}> = ({ pi, open, onClose, onExported }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [format, setFormat] = useState<Format>('pdf');
  const [available, setAvailable] = useState<boolean>();
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    piApi
      .converterStatus()
      .then((r) => {
        setAvailable(r.available);
        setFormat(r.available ? 'pdf' : 'xlsx');
      })
      .catch(() => setAvailable(false));
  }, [open]);

  const options: {
    key: Format;
    icon: React.ReactNode;
    title: string;
    desc: string;
  }[] = [
    {
      key: 'xlsx',
      icon: <FileExcelOutlined />,
      title: 'Excel',
      desc: '保留抬头、Logo、公章与公式，下载后可以自己修改',
    },
    {
      key: 'pdf',
      icon: <FilePdfOutlined />,
      title: 'PDF',
      desc: 'A4，已压缩，适合邮件或聊天发送',
    },
    {
      key: 'jpg',
      icon: <FileImageOutlined />,
      title: '图片（JPG）',
      desc: '多页纵向拼成一张，宽 1600 像素',
    },
  ];

  const run = async () => {
    if (!pi) return;
    setBusy(true);
    try {
      await piApi.exportFile(
        pi.id,
        format,
        `${pi.piNo}.${format}`,
        pi.version.versionNo,
      );
      onExported(CHANNEL_OF[format]);
    } catch (e) {
      message.error((e as Error).message || readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={560}
      title="导出 PI"
      footer={[
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button
          key="ok"
          type="primary"
          icon={<DownloadOutlined />}
          loading={busy}
          onClick={run}
        >
          {format === 'xlsx' ? '下载 Excel' : '导出'}
        </Button>,
      ]}
    >
      {available === false && (
        <Alert
          type="warning"
          showIcon
          icon={<WarningOutlined />}
          title="PDF / 图片暂时无法生成，可以先下载 Excel"
          style={{ marginBottom: 12 }}
        />
      )}
      <Radio.Group
        value={format}
        onChange={(e) => setFormat(e.target.value)}
        style={{ display: 'grid', gap: 10, width: '100%' }}
      >
        {options.map((o) => {
          const disabled = o.key !== 'xlsx' && available === false;
          const active = format === o.key;
          return (
            <Radio
              key={o.key}
              value={o.key}
              disabled={disabled}
              style={{
                margin: 0,
                padding: '12px 16px',
                borderRadius: 12,
                width: '100%',
                background: active ? palette.accentSoft : palette.inset,
                border: `1px solid ${active ? palette.accentLine : palette.hairline}`,
              }}
            >
              <span
                style={{
                  display: 'inline-flex',
                  gap: 12,
                  alignItems: 'center',
                }}
              >
                <span
                  style={{
                    fontSize: 18,
                    color: disabled ? palette.mute : palette.link,
                  }}
                >
                  {o.icon}
                </span>
                <span>
                  <span
                    style={{
                      display: 'block',
                      fontWeight: 600,
                      color: disabled ? palette.mute : palette.ink,
                    }}
                  >
                    {o.title}
                  </span>
                  <span style={{ fontSize: 12, color: palette.mute }}>
                    {disabled ? '暂时不可用' : o.desc}
                  </span>
                </span>
              </span>
            </Radio>
          );
        })}
      </Radio.Group>
      {busy && format !== 'xlsx' && (
        <div style={{ marginTop: 12, color: palette.link, fontSize: 13 }}>
          <LoadingOutlined /> 正在生成 {format === 'pdf' ? 'PDF' : '图片'}
          …（首次约 3–10 秒）
        </div>
      )}
      <div style={{ marginTop: 12, fontSize: 12, color: palette.mute }}>
        导出文件不含采购成本价、毛利率、净利润，也不显示版本号；银行信息取 PI
        上选的收款账户。
      </div>
    </Modal>
  );
};

/** 文件下载后询问是否标为已发送 */
export const SentPromptModal: React.FC<{
  pi?: Pi;
  channel?: number;
  onClose: () => void;
  onConfirm: (channel: number) => void;
}> = ({ pi, channel, onClose, onConfirm }) => {
  const { palette } = useAppTheme();
  const editing = !!pi?.editable;
  const revising = editing && (pi?.currentVersionNo ?? 0) > 0;
  return (
    <Modal
      open={channel != null && !!pi && pi.status !== 4}
      onCancel={onClose}
      width={480}
      title={
        <span>
          <CheckCircleOutlined
            style={{ color: palette.green, marginRight: 8 }}
          />
          文件已下载
        </span>
      }
      footer={[
        <Button key="later" onClick={onClose}>
          稍后再说
        </Button>,
        <Button
          key="ok"
          type="primary"
          icon={<SendOutlined />}
          onClick={() => channel != null && onConfirm(channel)}
        >
          {editing
            ? revising
              ? `发送 Rev.${pi?.editingVersionNo}`
              : '标为已发送'
            : '记一次发送'}
        </Button>,
      ]}
    >
      {editing ? (
        <p style={{ color: palette.sub }}>
          {revising
            ? `已经把修改后的 PI 发给客户了吗？发送后 Rev.${pi?.editingVersionNo} 成为当前有效版本，内容锁定。`
            : '已经把 PI 发给客户了吗？标为「已发送」后内容锁定；之后客户要改，点「修改」出新版本，PI 编号不变。'}
        </p>
      ) : (
        <p style={{ color: palette.sub }}>
          这张 PI 已发送过，要再记一次发送吗？
        </p>
      )}
    </Modal>
  );
};

// ---------------------------------------------------------------- 买方 / 收货人

export const PartyModal: React.FC<{
  piId: number;
  /** 3-发票抬头（买方）、1-收货人 */
  type: 1 | 3;
  open: boolean;
  value?: Party | null;
  /** PI 的客户：买方默认从这个客户的档案里选 */
  customer: { id: number; name: string };
  /** 当前买方：收货人可一键设为「同买方」 */
  buyer?: Party | null;
  onClose: () => void;
  onOk: (party: Party | null, saveToCustomer: boolean) => void;
}> = ({ piId, type, open, value, customer, buyer, onClose, onOk }) => {
  const { palette } = useAppTheme();
  const [options, setOptions] = useState<PartyOption[]>();
  const [mode, setMode] = useState<'pick' | 'edit'>('pick');
  /** 选中项在 options 中的下标（客户注册信息没有 partyId） */
  const [selected, setSelected] = useState<number | null>();
  const [form, setForm] = useState<Party>({ name: '' });
  const [save, setSave] = useState(false);
  const [customerId, setCustomerId] = useState(customer.id);
  const [customerOptions, setCustomerOptions] = useState<
    { value: number; label: string }[]
  >([{ value: customer.id, label: customer.name }]);
  const title = type === 3 ? '买方（发票抬头 Bill To）' : '收货人（Consignee）';

  useEffect(() => {
    if (open) setCustomerId(customer.id);
  }, [open, customer.id]);

  useEffect(() => {
    if (!open) return;
    setSave(false);
    setOptions(undefined);
    setForm(value ? { ...value } : { name: '' });
    const otherCustomer = customerId !== customer.id;
    piApi
      .parties(piId, otherCustomer ? customerId : undefined)
      .then((list) => {
        const own = list.filter((p) => p.partyType === type);
        setOptions(own);
        const hit = value?.partyId
          ? own.findIndex((o) => o.partyId === value.partyId)
          : -1;
        setSelected(hit >= 0 ? hit : otherCustomer && own.length ? 0 : null);
        setMode(
          own.length === 0 || (!otherCustomer && value && !value.partyId)
            ? 'edit'
            : 'pick',
        );
      })
      .catch(() => {
        setOptions([]);
        setMode('edit');
      });
  }, [open, piId, type, value, customerId, customer.id]);

  const findCustomers = (keyword: string) =>
    searchCustomers(keyword)
      .then((list) =>
        setCustomerOptions(
          list.map((c) => ({
            value: c.id,
            label: [c.displayName || c.name, c.country]
              .filter(Boolean)
              .join(' · '),
          })),
        ),
      )
      .catch(() => undefined);

  const submit = () => {
    if (mode === 'pick') {
      const p = selected == null ? undefined : options?.[selected];
      if (!p) return;
      // 客户注册信息不是单证主体，按「本次新填」处理，可勾选存到客户档案
      onOk({ ...p, partyId: p.registration ? null : p.partyId }, false);
    } else {
      onOk({ ...form, partyId: null }, save);
    }
  };

  const field = (
    key: keyof Party,
    text: string,
    placeholder?: string,
    span = 1,
  ) => (
    <div style={{ gridColumn: `span ${span}` }}>
      <div style={{ ...label, color: palette.sub }}>{text}</div>
      <Input
        value={(form[key] as string) ?? ''}
        placeholder={placeholder}
        onChange={(e) => setForm({ ...form, [key]: e.target.value })}
        aria-label={text}
      />
    </div>
  );

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={680}
      title={`选择${title}`}
      footer={[
        type === 1 && (
          <Button
            key="clear"
            style={{ float: 'left' }}
            onClick={() => onOk(null, false)}
          >
            不显示收货人
          </Button>
        ),
        type === 1 && buyer && (
          <Button
            key="same"
            style={{ float: 'left' }}
            onClick={() => onOk({ ...buyer, partyId: null }, false)}
          >
            同买方
          </Button>
        ),
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button
          key="ok"
          type="primary"
          disabled={mode === 'pick' ? selected == null : !form.name?.trim()}
          onClick={submit}
        >
          确定
        </Button>,
      ]}
    >
      {type === 3 && (
        <div style={{ marginBottom: 12 }}>
          <div style={{ ...label, color: palette.sub }}>
            客户（可选自己负责的其他客户，如母公司付款）
          </div>
          <Select
            showSearch={{ filterOption: false, onSearch: findCustomers }}
            style={{ width: '100%' }}
            value={customerId}
            options={customerOptions}
            onFocus={() => customerOptions.length <= 1 && findCustomers('')}
            onChange={(v) => setCustomerId(v)}
            aria-label="买方客户"
          />
        </div>
      )}
      <Radio.Group
        value={mode}
        onChange={(e) => setMode(e.target.value)}
        optionType="button"
        options={[
          {
            value: 'pick',
            label: '从客户档案选',
            disabled: options?.length === 0,
          },
          { value: 'edit', label: '直接填写' },
        ]}
        style={{ marginBottom: 16 }}
      />
      {!options ? (
        <Skeleton active paragraph={{ rows: 4 }} />
      ) : mode === 'pick' ? (
        <div
          style={{ display: 'grid', gap: 8, maxHeight: 420, overflow: 'auto' }}
        >
          {options.map((o, idx) => {
            const active = selected === idx;
            return (
              <button
                type="button"
                key={o.partyId ?? `reg-${idx}`}
                onClick={() => setSelected(idx)}
                style={{
                  all: 'unset',
                  cursor: 'pointer',
                  padding: '12px 14px',
                  borderRadius: 12,
                  background: active ? palette.accentSoft : palette.inset,
                  border: `1px solid ${active ? palette.accentLine : palette.hairline}`,
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    gap: 8,
                    alignItems: 'center',
                    fontWeight: 600,
                    color: palette.ink,
                  }}
                >
                  {o.name}
                  {o.isDefault && <Pill tone="accent">默认</Pill>}
                  {o.registration && <Pill tone="gray">客户注册信息</Pill>}
                </div>
                <div style={{ fontSize: 12, color: palette.sub, marginTop: 2 }}>
                  {partyAddress(o)}
                </div>
                <div style={{ fontSize: 12, color: palette.mute }}>
                  {[
                    o.taxId && `TAX ID ${o.taxId}`,
                    o.contact && `Attn: ${o.contact}`,
                    o.phone,
                  ]
                    .filter(Boolean)
                    .join(' · ')}
                </div>
              </button>
            );
          })}
        </div>
      ) : (
        <>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
              gap: 12,
            }}
          >
            {field(
              'name',
              '公司名称（英文）',
              'ACROBOT TECHNOLOGIES PRIVATE LIMITED',
              2,
            )}
            {field(
              'address',
              '详细地址',
              'SHED NO 107, KRISHNA INDUSTRIAL PARK',
              2,
            )}
            {field('city', '城市')}
            {field('country', '国家')}
            {field('taxId', '税号')}
            {field('contact', '联系人')}
            {field('phone', '电话')}
            {field('email', '邮箱')}
          </div>
          <Checkbox
            checked={save}
            onChange={(e) => setSave(e.target.checked)}
            style={{ marginTop: 16 }}
          >
            保存到客户档案（作为{type === 3 ? '发票抬头' : '收货人'}，下次开 PI
            可以直接选）
          </Checkbox>
        </>
      )}
    </Modal>
  );
};

// ---------------------------------------------------------------- 收款账户

export const useBankOptions = (currency?: string) => {
  const [list, setList] = useState<BankOption[]>([]);
  useEffect(() => {
    bankOptionsApi
      .options()
      .then(setList)
      .catch(() => setList([]));
  }, []);
  return useMemo(
    () => list.filter((b) => !currency || b.currencyCode === currency),
    [list, currency],
  );
};

// ---------------------------------------------------------------- 上传水单

const SLIP_TYPES = ['image/jpeg', 'image/png', 'application/pdf'];

export const SlipModal = <T extends ReceiptResult>({
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
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [files, setFiles] = useState<UploadFile[]>([]);
  const [amount, setAmount] = useState<number | null>(null);
  const [date, setDate] = useState<dayjs.Dayjs | null>(dayjs());
  const [note, setNote] = useState('');
  const [method, setMethod] = useState<string>();
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (open) {
      setFiles([]);
      setAmount(null);
      setDate(dayjs());
      setNote('');
      setMethod(undefined);
    }
  }, [open]);

  const submit = async () => {
    if (!pi || !amount || !date) return;
    setBusy(true);
    try {
      const res = await pi.uploadSlip(
        files.map((f) => f.originFileObj as File),
        amount,
        date.format('YYYY-MM-DD'),
        note.trim() || undefined,
        method,
      );
      message.success('水单已上传，等财务确认到账');
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
      width={560}
      title="上传付款水单"
      footer={[
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button
          key="ok"
          type="primary"
          loading={busy}
          disabled={files.length === 0 || !amount || amount <= 0 || !date}
          onClick={submit}
        >
          上传
        </Button>,
      ]}
    >
      <div style={{ fontSize: 13, color: palette.sub, marginBottom: 12 }}>
        水单只表示客户说已经付款，不计入到账金额；财务确认到账后才算收到钱。有水单即可转成销售订单。
      </div>
      <Upload.Dragger
        multiple
        fileList={files}
        accept=".jpg,.jpeg,.png,.pdf"
        beforeUpload={(f) => {
          if (!SLIP_TYPES.includes(f.type)) {
            message.error('水单只支持 JPG、PNG、PDF');
            return Upload.LIST_IGNORE;
          }
          if (f.size > 10 * 1024 * 1024) {
            message.error('单个文件不能超过 10MB');
            return Upload.LIST_IGNORE;
          }
          return false;
        }}
        onChange={({ fileList }) => setFiles(fileList.slice(0, 5))}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">把客户发来的水单截图或 PDF 拖到这里</p>
        <p className="ant-upload-hint">
          JPG、PNG、PDF，单个不超过 10MB，最多 5 个
        </p>
      </Upload.Dragger>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
          gap: 12,
          marginTop: 16,
        }}
      >
        <div>
          <div style={{ ...label, color: palette.sub }}>付款金额</div>
          <InputNumber
            style={{ width: '100%' }}
            prefix={pi?.currencyCode}
            min={0.01}
            precision={2}
            value={amount}
            onChange={setAmount}
            aria-label="付款金额"
          />
          {pi && (
            <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
              合计 {formatAmount(pi.totalAmount, pi.currencyCode)}
            </div>
          )}
        </div>
        <div>
          <div style={{ ...label, color: palette.sub }}>付款日期</div>
          <DatePicker
            style={{ width: '100%' }}
            value={date}
            onChange={setDate}
            aria-label="付款日期"
          />
        </div>
      </div>
      <div style={{ marginTop: 12 }}>
        <div style={{ ...label, color: palette.sub }}>付款方式</div>
        <PaymentMethodSelect
          online={false}
          value={method}
          onChange={setMethod}
        />
      </div>
      <div style={{ marginTop: 12 }}>
        <div style={{ ...label, color: palette.sub }}>说明（选填）</div>
        <Input
          value={note}
          maxLength={300}
          placeholder="如 定金 50%、尾款"
          onChange={(e) => setNote(e.target.value)}
        />
      </div>
    </Modal>
  );
};

// ---------------------------------------------------------------- 登记到账

export const ConfirmReceiptModal = <T extends ReceiptResult>({
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
  const banks = useBankOptions(pi?.currencyCode);
  const [tolerance, setTolerance] = useState(50);
  const [amount, setAmount] = useState<number | null>(null);
  const [date, setDate] = useState<dayjs.Dayjs | null>(dayjs());
  const [bankId, setBankId] = useState<number>();
  const [slipId, setSlipId] = useState<number>();
  const [feeDiff, setFeeDiff] = useState(false);
  const [note, setNote] = useState('');
  const [method, setMethod] = useState<string>();
  const [actual, setActual] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);

  const slips = useMemo(
    () =>
      (pi?.receipts ?? []).filter(
        (r) => r.kind === KIND.SLIP && r.status === 1 && !r.matched,
      ),
    [pi],
  );

  useEffect(() => {
    if (!open) return;
    piApi
      .receiptSettings()
      .then((s) => setTolerance(Number(s.feeTolerance)))
      .catch(() => setTolerance(50));
    const slip = slips[0];
    setAmount(slip ? slip.amount : null);
    setSlipId(slip?.id);
    setDate(dayjs());
    setFeeDiff(false);
    setNote('');
    setMethod(slip?.paymentMethod);
    setActual(null);
  }, [open, slips]);

  useEffect(() => {
    if (!bankId || !banks.some((b) => b.id === bankId)) {
      setBankId(banks.find((b) => b.isDefault)?.id ?? banks[0]?.id);
    }
  }, [banks, bankId]);

  const remaining = pi?.remainingAmount ?? 0;
  const after = Math.round((remaining - (amount ?? 0)) * 100) / 100;
  const canFee = after > 0 && after <= tolerance;
  const cur = pi?.currencyCode ?? 'USD';

  const save = async () => {
    if (!pi || !amount || !date || !bankId) return;
    setBusy(true);
    try {
      const res = await pi.confirmReceipt({
        amount,
        receiptDate: date.format('YYYY-MM-DD'),
        bankAccountId: bankId,
        slipId,
        feeDiff: canFee && feeDiff,
        paymentMethod: method,
        actualAmountCny: actual ?? undefined,
        note: note.trim() || undefined,
      });
      message.success(`已登记到账，收款状态：${res.receiptStatusName}`);
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
        content: '到账金额超过剩余金额，确认继续登记吗？',
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
      title="登记到账"
      footer={[
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button
          key="ok"
          type="primary"
          loading={busy}
          disabled={!amount || amount <= 0 || !date || !bankId}
          onClick={submit}
        >
          登记
        </Button>,
      ]}
    >
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(3, 1fr)',
          gap: 12,
          padding: 14,
          borderRadius: 12,
          background: palette.inset,
          marginBottom: 16,
        }}
      >
        {[
          ['合计', pi?.totalAmount],
          ['已到账', pi?.receivedAmount],
          ['剩余', remaining],
        ].map(([k, v]) => (
          <div key={k as string}>
            <div style={{ fontSize: 12, color: palette.mute }}>{k}</div>
            <b
              style={{
                ...num,
                color: k === '剩余' ? palette.orange : palette.ink,
              }}
            >
              {formatAmount(v as number, cur)}
            </b>
          </div>
        ))}
      </div>
      {banks.length === 0 && (
        <Alert
          type="warning"
          showIcon
          title={`还没有 ${cur} 收款账户，请先在「系统管理 → 收款账户」中添加`}
          style={{ marginBottom: 12 }}
        />
      )}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
          gap: 12,
        }}
      >
        <div>
          <div style={{ ...label, color: palette.sub }}>
            到账金额（银行实收）
          </div>
          <InputNumber
            style={{ width: '100%' }}
            prefix={cur}
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
            options={banks.map((b) => ({
              value: b.id,
              label: `${b.bankName} ${b.accountNoMasked}`,
            }))}
            aria-label="收款账户"
          />
        </div>
        <div>
          <div style={{ ...label, color: palette.sub }}>对应水单（选填）</div>
          <Select
            style={{ width: '100%' }}
            allowClear
            value={slipId}
            onChange={(v) => {
              setSlipId(v);
              const s = slips.find((x) => x.id === v);
              if (s?.paymentMethod) setMethod(s.paymentMethod);
            }}
            placeholder={slips.length === 0 ? '没有待确认的水单' : '选择水单'}
            options={slips.map((s) => ({
              value: s.id,
              label: `${s.receiptDate ?? ''} ${formatAmount(s.amount, cur)}${s.note ? ` · ${s.note}` : ''}`,
            }))}
            aria-label="对应水单"
          />
        </div>
      </div>
      {amount != null && amount > 0 && (
        <div style={{ marginTop: 12, fontSize: 13 }}>
          {after < 0 ? (
            <span style={{ color: palette.orange }}>
              <WarningOutlined /> 多收 {formatAmount(-after, cur)}，将记为预收
            </span>
          ) : after === 0 ? (
            <span style={{ color: palette.green }}>本次到账后结清</span>
          ) : (
            <>
              <span style={{ color: palette.sub }}>
                本次到账后还差 {formatAmount(after, cur)}
              </span>
              {canFee ? (
                <Checkbox
                  checked={feeDiff}
                  onChange={(e) => setFeeDiff(e.target.checked)}
                  style={{ marginLeft: 12 }}
                >
                  差额记为银行中转手续费（视为结清）
                </Checkbox>
              ) : (
                <span
                  style={{ marginLeft: 12, color: palette.mute, fontSize: 12 }}
                >
                  <InfoCircleOutlined /> 差额不超过{' '}
                  {formatAmount(tolerance, cur)} 时可记为手续费
                </span>
              )}
            </>
          )}
        </div>
      )}
      <div style={{ marginTop: 12 }}>
        <div style={{ ...label, color: palette.sub }}>付款方式</div>
        <PaymentMethodSelect
          online={false}
          value={method}
          onChange={setMethod}
        />
      </div>
      <ActualCnyInput
        net={amount ?? 0}
        currency={cur}
        value={actual}
        onChange={setActual}
      />
      <div style={{ marginTop: 12 }}>
        <div style={{ ...label, color: palette.sub }}>说明（选填）</div>
        <Input
          value={note}
          maxLength={300}
          onChange={(e) => setNote(e.target.value)}
        />
      </div>
      <div style={{ marginTop: 12, fontSize: 12, color: palette.mute }}>
        没有填实际入账人民币时按登记时的系统汇率折算；到账记录保存后不能修改，登记错误时作废后重新登记。
      </div>
    </Modal>
  );
};

// ---------------------------------------------------------------- 填原因（作废到账、取消订单）

export const ReasonModal: React.FC<{
  open: boolean;
  title: string;
  description?: React.ReactNode;
  placeholder?: string;
  okText: string;
  onClose: () => void;
  onSubmit: (reason: string) => Promise<void>;
}> = ({ open, title, description, placeholder, okText, onClose, onSubmit }) => {
  const { palette } = useAppTheme();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (open) setReason('');
  }, [open]);
  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={480}
      title={title}
      footer={[
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button
          key="ok"
          danger
          type="primary"
          loading={busy}
          disabled={!reason.trim()}
          onClick={async () => {
            setBusy(true);
            try {
              await onSubmit(reason.trim());
            } finally {
              setBusy(false);
            }
          }}
        >
          {okText}
        </Button>,
      ]}
    >
      {description && (
        <div style={{ color: palette.sub, marginBottom: 12 }}>
          {description}
        </div>
      )}
      <Input.TextArea
        rows={3}
        maxLength={200}
        showCount
        value={reason}
        placeholder={placeholder}
        onChange={(e) => setReason(e.target.value)}
        aria-label="原因"
      />
    </Modal>
  );
};

// ---------------------------------------------------------------- 追加型号

export const AddItemsModal: React.FC<{
  pi?: Pi;
  open: boolean;
  onClose: () => void;
  onAdded: (pi: Pi) => void;
}> = ({ pi, open, onClose, onAdded }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [quotations, setQuotations] = useState<CandidateQuotation[]>();
  const [picked, setPicked] = useState<Set<number>>(new Set());
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open || !pi) return;
    setPicked(new Set());
    setQuotations(undefined);
    piApi
      .candidateQuotations(pi.customerId)
      .then((list) =>
        setQuotations(list.filter((q) => q.currencyCode === pi.currencyCode)),
      )
      .catch(() => setQuotations([]));
  }, [open, pi]);

  const present = new Set(pi?.version.items.map((i) => i.quotationItemId));

  const add = async () => {
    if (!pi) return;
    setBusy(true);
    try {
      onAdded(await piApi.addItems(pi.id, [...picked]));
      message.success(`已添加 ${picked.size} 个型号`);
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
      width={760}
      title="从报价单添加型号"
      footer={[
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button
          key="ok"
          type="primary"
          loading={busy}
          disabled={picked.size === 0}
          onClick={add}
        >
          添加 {picked.size || ''} 个型号
        </Button>,
      ]}
    >
      <div style={{ fontSize: 13, color: palette.sub, marginBottom: 12 }}>
        只列出同一客户、币种为 {pi?.currencyCode} 的已发送 / 部分成交报价单。
      </div>
      {!quotations ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : quotations.length === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="没有可添加的报价单"
        />
      ) : (
        <div
          style={{ display: 'grid', gap: 12, maxHeight: 480, overflow: 'auto' }}
        >
          {quotations.map((q) => (
            <div key={q.quotationId}>
              <b style={{ color: palette.ink }}>{q.quotationNo}</b>
              <div style={{ display: 'grid', gap: 6, marginTop: 6 }}>
                {q.items.map((i) => {
                  const inThis = present.has(i.quotationItemId);
                  return (
                    <Checkbox
                      key={i.quotationItemId}
                      disabled={inThis}
                      checked={inThis || picked.has(i.quotationItemId)}
                      onChange={(e) => {
                        const next = new Set(picked);
                        if (e.target.checked) next.add(i.quotationItemId);
                        else next.delete(i.quotationItemId);
                        setPicked(next);
                      }}
                    >
                      <span style={{ fontWeight: 600 }}>{i.model}</span>
                      <span
                        style={{ color: palette.mute, marginLeft: 8, ...num }}
                      >
                        {i.quantity} ×{' '}
                        {formatAmount(i.unitPrice, q.currencyCode)}
                      </span>
                      {inThis && (
                        <span
                          style={{
                            marginLeft: 8,
                            fontSize: 12,
                            color: palette.mute,
                          }}
                        >
                          已在这张 PI 中
                        </span>
                      )}
                      {!inThis && i.inPiNo && (
                        <span style={{ marginLeft: 8 }}>
                          <Pill tone="orange">已在 {i.inPiNo} 中</Pill>
                        </span>
                      )}
                    </Checkbox>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      )}
    </Modal>
  );
};
