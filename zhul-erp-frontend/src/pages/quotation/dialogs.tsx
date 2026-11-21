import {
  CheckCircleOutlined,
  CopyOutlined,
  DownloadOutlined,
  FileExcelOutlined,
  FileImageOutlined,
  FilePdfOutlined,
  InfoCircleOutlined,
  LoadingOutlined,
  SendOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { request } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  Checkbox,
  Input,
  Modal,
  Radio,
  Select,
  Skeleton,
} from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { useQuoteDicts } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { liveRequests } from '@/utils/liveRequests';
import { CHANNEL, STATUS } from './components';
import { PickItemsPanel } from './NewQuotationModal';
import { type Quotation, quotationApi, readBizError } from './service';
import {
  DEFAULT_TEXT_OPTIONS,
  type LeadChoice,
  leadChoices,
  type TextOptions,
} from './textOptions';

const CHANNEL_OF = {
  xlsx: CHANNEL.EXCEL,
  pdf: CHANNEL.PDF,
  jpg: CHANNEL.IMAGE,
} as const;

// ---------------------------------------------------------------- 文字报价

/** 正在查看默认版本（修改中的，没有时为当前版本），且报价单为草稿或已发送：可以标为已发送 / 记一次发送 */
const markable = (q: Quotation) =>
  (q.status === 1 || q.status === 2) &&
  q.versionNo === (q.editingVersionNo ?? q.currentVersionNo);

/** 这次标记会发送一个新内容（草稿第一次发送，或发送修改中的新版本） */
const sendsVersion = (q: Quotation) => q.status === 1 || !!q.editingVersionNo;

export const TextQuoteModal: React.FC<{
  quotation?: Quotation;
  open: boolean;
  onClose: () => void;
  onMarkSent: (channel: number) => void;
}> = ({ quotation, open, onClose, onMarkSent }) => {
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const { conditionOptions, leadTimeOptions } = useQuoteDicts();
  const [text, setText] = useState('');
  const [version, setVersion] = useState<number>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [copied, setCopied] = useState(false);
  const [options, setOptions] = useState<TextOptions>(DEFAULT_TEXT_OPTIONS);
  /** 预览框里手改过：改选下拉前先确认 */
  const [edited, setEdited] = useState(false);

  // 关闭时恢复默认，下次打开为全新原装、按采购货期顺延
  useEffect(() => {
    if (!open) setOptions(DEFAULT_TEXT_OPTIONS);
  }, [open]);

  useEffect(() => {
    if (!open || !quotation) return undefined;
    const { live, cancel } = liveRequests();
    setLoading(true);
    setError(undefined);
    setCopied(false);
    live(quotationApi.text(quotation.id, options, quotation.versionNo))
      .then((r) => {
        setText(r.text);
        setEdited(false);
        setVersion(r.templateVersionNo);
      })
      .catch((e) => setError(readBizError(e).message))
      .finally(() => setLoading(false));
    return cancel;
  }, [open, quotation, options]);

  const choose = (next: Partial<TextOptions>) => {
    const apply = () => setOptions((o) => ({ ...o, ...next }));
    if (!edited) {
      apply();
      return;
    }
    modal.confirm({
      title: '重新生成会覆盖你改过的文字',
      content: '你在预览框里手改过文字，改选后会按新选择重新生成。',
      okText: '重新生成',
      cancelText: '取消',
      onOk: apply,
    });
  };

  const copy = () => {
    navigator.clipboard
      .writeText(text)
      .then(() => {
        setCopied(true);
        message.success('已复制，可以粘贴给客户了');
      })
      .catch(() => message.error('复制失败，请手动选择文字复制'));
  };

  const canMarkSent = !!quotation && markable(quotation);
  // 草稿或修改中的新版本：标为已发送；已发送的当前版本：记一次发送
  const firstSend = !!quotation && sendsVersion(quotation);

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={720}
      title={`文字报价 · ${quotation?.quotationNo ?? ''}`}
      footer={[
        <Button key="close" onClick={onClose}>
          关闭
        </Button>,
        <Button
          key="copy"
          type="primary"
          icon={<CopyOutlined />}
          disabled={!text}
          onClick={copy}
        >
          复制
        </Button>,
      ]}
    >
      <Alert
        type="info"
        showIcon
        title="复制后直接粘贴到 WhatsApp、阿里巴巴国际站等聊天窗口；这里的修改只影响本次发送，不改模版。"
        style={{ marginBottom: 12 }}
      />
      <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <span style={{ fontSize: 12, color: palette.mute }}>报价货况</span>
          <Select
            aria-label="报价货况"
            style={{ width: 200 }}
            value={options.condition}
            options={conditionOptions}
            onChange={(v: number) => choose({ condition: v })}
          />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <span style={{ fontSize: 12, color: palette.mute }}>报价货期</span>
          <Select<LeadChoice>
            aria-label="报价货期"
            style={{ width: 220 }}
            value={options.leadTime}
            options={leadChoices(leadTimeOptions)}
            onChange={(v) => choose({ leadTime: v })}
          />
        </div>
      </div>
      <div
        style={{
          margin: '8px 0 12px',
          fontSize: 12,
          color: palette.mute,
        }}
      >
        改选后重新生成下面的文字，只影响这次发送，不改报价单。按采购货期顺延：每行在采购报的货期上往后挪一档。
      </div>
      {loading ? (
        <Skeleton active />
      ) : error ? (
        <Alert type="error" showIcon title={error} />
      ) : (
        <Input.TextArea
          value={text}
          onChange={(e) => {
            setText(e.target.value);
            setEdited(true);
          }}
          autoSize={{ minRows: 5, maxRows: 16 }}
          style={{
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
            fontSize: 13,
          }}
          aria-label="文字报价内容"
        />
      )}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          margin: '10px 0 0',
          fontSize: 12,
          color: palette.mute,
        }}
      >
        <span>使用模版：文字报价 V{version ?? '—'}（默认）</span>
        <span>
          单价去掉末尾 0 · 货况货期取字典英文名 · 不含采购成本价与毛利率
        </span>
      </div>
      {copied && canMarkSent && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            marginTop: 12,
            padding: '10px 14px',
            borderRadius: 10,
            background: palette.greenSoft,
            color: palette.green,
          }}
        >
          <CheckCircleOutlined /> 已复制，可以粘贴给客户了
          <span style={{ marginLeft: 'auto', color: palette.sub }}>
            {firstSend ? '已经发给客户了？' : '再发了一次？'}
          </span>
          <button
            type="button"
            className="zhul-link"
            onClick={() => onMarkSent(CHANNEL.TEXT)}
          >
            {firstSend ? '标为已发送' : '记一次发送'}
          </button>
        </div>
      )}
    </Modal>
  );
};

/**
 * 列表「文字报价」：取报价单默认查看的版本（与详情页同一接口），打开同一个弹窗；
 * 标为已发送与详情页一样，有供应提示时先确认供应。发送后回调刷新列表。
 */
export const ListTextQuote: React.FC<{
  id?: number;
  onClose: () => void;
  onSent: () => void;
}> = ({ id, onClose, onSent }) => {
  const { message } = App.useApp();
  const [quotation, setQuotation] = useState<Quotation>();
  const [supplyChannel, setSupplyChannel] = useState<number>();

  useEffect(() => {
    setQuotation(undefined);
    setSupplyChannel(undefined);
    if (id == null) return undefined;
    const { live, cancel } = liveRequests();
    live(quotationApi.detail(id))
      .then(setQuotation)
      .catch((e) => {
        message.error(readBizError(e).message);
        onClose();
      });
    return cancel;
  }, [id]);

  const markSent = async (channel: number, supplyConfirmed = false) => {
    if (!quotation) return;
    if (!supplyConfirmed && supplyRisks(quotation).length > 0) {
      setSupplyChannel(channel);
      return;
    }
    try {
      await quotationApi.markSent(
        quotation.id,
        channel,
        supplyConfirmed || undefined,
      );
      message.success(
        quotation.status === STATUS.DRAFT
          ? '已标为已发送，询盘单变为「已报价」'
          : quotation.editingVersionNo
            ? `Rev.${quotation.editingVersionNo} 已发送，成为当前版本`
            : '已记一次发送',
      );
      onClose();
      onSent();
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  return (
    <>
      <TextQuoteModal
        quotation={quotation}
        open={!!quotation && supplyChannel == null}
        onClose={onClose}
        onMarkSent={markSent}
      />
      <SupplyConfirmModal
        quotation={quotation}
        channel={supplyChannel}
        onClose={() => setSupplyChannel(undefined)}
        onConfirm={(channel) => markSent(channel, true)}
      />
    </>
  );
};

// ---------------------------------------------------------------- 导出

type Format = 'xlsx' | 'pdf' | 'jpg';

export const ExportModal: React.FC<{
  quotation?: Quotation;
  open: boolean;
  onClose: () => void;
  onExported: (channel: number) => void;
}> = ({ quotation, open, onClose, onExported }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [format, setFormat] = useState<Format>('pdf');
  const [available, setAvailable] = useState<boolean>();
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    quotationApi
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
    size: string;
  }[] = [
    {
      key: 'xlsx',
      icon: <FileExcelOutlined />,
      title: 'Excel',
      desc: '保留抬头、Logo 与公式，下载后可以自己修改',
      size: '约 30 KB',
    },
    {
      key: 'pdf',
      icon: <FilePdfOutlined />,
      title: 'PDF',
      desc: 'A4，已压缩，适合邮件或聊天发送',
      size: '不超过 500 KB（30 行以内）',
    },
    {
      key: 'jpg',
      icon: <FileImageOutlined />,
      title: '图片（JPG）',
      desc: '多页纵向拼成一张，宽 1600 像素，已压缩',
      size: '不超过 800 KB（30 行以内）',
    },
  ];

  const run = async () => {
    if (!quotation) return;
    setBusy(true);
    try {
      await quotationApi.exportFile(
        quotation.id,
        format,
        `${quotation.quotationNo}.${format}`,
        quotation.versionNo,
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
      title="导出正式报价单"
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
                background: active ? palette.primarySoft : palette.inset,
                border: `1px solid ${active ? palette.primaryLine : palette.hairline}`,
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
                    {disabled ? '暂时不可用' : `${o.desc} · ${o.size}`}
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
        导出文件不含采购成本价、毛利率、净利润、采购渠道与店铺。
      </div>
    </Modal>
  );
};

/** 文件下载后询问是否标为已发送 */
// ---------------------------------------------------------------- 发送前确认供应

/** 这次发送要先确认供应的行（发送新内容时，有供应提示的行） */
export const supplyRisks = (q: Quotation) =>
  sendsVersion(q)
    ? q.items.filter((i) => (i.supplyHints ?? []).length > 0)
    : [];

export const SupplyConfirmModal: React.FC<{
  quotation?: Quotation;
  channel?: number;
  onClose: () => void;
  onConfirm: (channel: number) => Promise<void>;
}> = ({ quotation, channel, onClose, onConfirm }) => {
  const { palette } = useAppTheme();
  const [checked, setChecked] = useState(false);
  const [sending, setSending] = useState(false);
  const open = channel != null && !!quotation;
  const rows = quotation ? supplyRisks(quotation) : [];
  useEffect(() => {
    if (open) setChecked(false);
  }, [open]);
  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={600}
      title="发送前确认供应"
      destroyOnHidden
      footer={[
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button
          key="ok"
          type="primary"
          icon={<SendOutlined />}
          disabled={!checked}
          loading={sending}
          onClick={async () => {
            if (channel == null) return;
            setSending(true);
            try {
              await onConfirm(channel);
            } finally {
              setSending(false);
            }
          }}
        >
          确认并发送
        </Button>,
      ]}
    >
      <p style={{ color: palette.sub }}>
        以下 {rows.length}{' '}
        个型号的成本或货源需要确认。请先向供应商核实最新成本和交期，再发给客户。
      </p>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {rows.map((i) => (
          <div
            key={i.id}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 12,
              padding: '10px 14px',
              borderRadius: 12,
              background: palette.inset,
            }}
          >
            <span style={{ color: palette.ink, fontWeight: 600 }}>
              {i.model}
              <span
                style={{ color: palette.mute, fontWeight: 400, marginLeft: 8 }}
              >
                {i.brand}
              </span>
            </span>
            <span style={{ color: palette.orange, fontSize: 12 }}>
              {(i.supplyHints ?? []).join(' · ')}
            </span>
          </div>
        ))}
      </div>
      <div
        style={{
          marginTop: 14,
          padding: '10px 14px',
          borderRadius: 12,
          background: palette.primarySoft,
        }}
      >
        <Checkbox
          checked={checked}
          onChange={(e) => setChecked(e.target.checked)}
        >
          已向供应商确认这些型号的最新成本和交期
        </Checkbox>
      </div>
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
        确认人和时间会记到这些型号的供应链时间线上。
      </div>
    </Modal>
  );
};

export const SentPromptModal: React.FC<{
  quotation?: Quotation;
  channel?: number;
  onClose: () => void;
  onConfirm: (channel: number) => void;
}> = ({ quotation, channel, onClose, onConfirm }) => {
  const { palette } = useAppTheme();
  const draft = !!quotation && sendsVersion(quotation);
  const revision = !!quotation?.editingVersionNo;
  return (
    <Modal
      open={channel != null && !!quotation && markable(quotation)}
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
          {draft ? '标为已发送' : '记一次发送'}
        </Button>,
      ]}
    >
      {draft ? (
        <>
          <p style={{ color: palette.sub }}>
            {revision
              ? `已经把 Rev.${quotation?.editingVersionNo} 发给客户了吗？标为「已发送」后它成为当前版本，Rev.${quotation?.currentVersionNo} 只读保留。`
              : `已经把报价单发给客户了吗？标为「已发送」后内容会锁定，询盘单 ${quotation?.inquiryCodes.join('、')} 变为「已报价」。`}
          </p>
          <div style={{ fontSize: 12, color: palette.mute }}>
            <InfoCircleOutlined />{' '}
            之后要改价，点「修改（出新版本）」，编号不变。
          </div>
        </>
      ) : (
        <p style={{ color: palette.sub }}>
          这张报价单已发送过，要再记一次发送吗？
        </p>
      )}
    </Modal>
  );
};

// ---------------------------------------------------------------- 未成交

interface LostReason {
  itemCode: string;
  itemName: string;
  status: number;
}

/** 选未成交原因（报价单标为未成交、关闭 PI 共用）：原因按【】内分类分组，选「其他」时说明必填 */
export const LostReasonModal: React.FC<{
  open: boolean;
  onClose: () => void;
  onSubmit: (reason: string, note: string) => Promise<void>;
  title?: string;
  reasonLabel?: string;
  okText?: string;
  okDanger?: boolean;
  /** 原因上方的说明 */
  intro?: React.ReactNode;
  /** 说明下方的附加内容 */
  extra?: React.ReactNode;
}> = ({
  open,
  onClose,
  onSubmit,
  title = '标为未成交',
  reasonLabel = '未成交原因',
  okText = '确定',
  okDanger,
  intro,
  extra,
}) => {
  const { palette } = useAppTheme();
  const [reasons, setReasons] = useState<LostReason[]>([]);
  const [reason, setReason] = useState<string>();
  const [note, setNote] = useState('');
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    setReason(undefined);
    setNote('');
    setError(undefined);
    request<{ data: LostReason[] }>('/api/v1/system/dict-items', {
      params: { dictType: 'quotation_lost_reason' },
      skipErrorHandler: true,
    })
      .then((r) => setReasons((r.data ?? []).filter((i) => i.status === 1)))
      .catch(() => setReasons([]));
  }, [open]);

  /** 按名称里【】内的分类分组 */
  const groups = useMemo(() => {
    const map = new Map<string, { label: string; value: string }[]>();
    for (const r of reasons) {
      const m = /^【([^】]+)】(.*)$/.exec(r.itemName);
      const group = m ? m[1] : '其他';
      const label = m ? m[2] : r.itemName;
      if (!map.has(group)) map.set(group, []);
      map.get(group)?.push({ label, value: r.itemCode });
    }
    return [...map.entries()].map(([label, options]) => ({ label, options }));
  }, [reasons]);

  const other = reason === 'OTHER';
  const submit = async () => {
    if (!reason) {
      setError(`请选择${reasonLabel}`);
      return;
    }
    if (other && !note.trim()) {
      setError('选择「其他」时请填写说明');
      return;
    }
    setBusy(true);
    try {
      await onSubmit(reason, note.trim());
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={520}
      title={title}
      okText={okText}
      okButtonProps={{ danger: okDanger }}
      onOk={submit}
      confirmLoading={busy}
    >
      {intro && (
        <div style={{ marginBottom: 16, color: palette.sub, fontSize: 13 }}>
          {intro}
        </div>
      )}
      <div style={{ marginBottom: 6, color: palette.sub }}>
        {reasonLabel} <span style={{ color: palette.red }}>*</span>
      </div>
      <Select
        style={{ width: '100%' }}
        placeholder="选择原因"
        value={reason}
        onChange={(v) => {
          setReason(v);
          setError(undefined);
        }}
        options={groups}
        status={error && !reason ? 'error' : undefined}
      />
      <div style={{ margin: '16px 0 6px', color: palette.sub }}>
        说明
        {other ? <span style={{ color: palette.red }}> *</span> : '（选填）'}
      </div>
      <Input.TextArea
        value={note}
        onChange={(e) => {
          setNote(e.target.value);
          setError(undefined);
        }}
        maxLength={300}
        showCount
        autoSize={{ minRows: 3, maxRows: 6 }}
        placeholder={other ? '请说明具体原因' : '补充说明，方便以后复盘'}
        status={error && other && !note.trim() ? 'error' : undefined}
      />
      {extra}
      {error && (
        <div style={{ color: palette.red, marginTop: 8, fontSize: 13 }}>
          {error}
        </div>
      )}
    </Modal>
  );
};

// ---------------------------------------------------------------- 追加型号

export const AddItemsModal: React.FC<{
  quotation?: Quotation;
  open: boolean;
  onClose: () => void;
  onAdded: (q: Quotation) => void;
}> = ({ quotation, open, onClose, onAdded }) => {
  const { message } = App.useApp();
  const [items, setItems] = useState<Map<number, number>>(new Map());
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (open) setItems(new Map());
  }, [open]);
  if (!quotation) return null;
  const customer = {
    customerId: quotation.customerId,
    customerName: quotation.customerName,
    inquiryCount: 0,
  };
  const add = async () => {
    setBusy(true);
    try {
      const q = await quotationApi.addItems(quotation.id, [...items.keys()]);
      message.success(`已添加 ${items.size} 个型号`);
      onAdded(q);
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
      width={960}
      title="从询盘添加型号"
      okText={items.size ? `添加 ${items.size} 个型号` : '添加'}
      okButtonProps={{ disabled: items.size === 0 }}
      confirmLoading={busy}
      onOk={add}
      destroyOnHidden
      styles={{ body: { maxHeight: '65vh', overflow: 'auto' } }}
    >
      <PickItemsPanel
        customer={customer}
        onCustomer={() => undefined}
        selected={items}
        onChange={setItems}
        lockCustomer
      />
    </Modal>
  );
};
