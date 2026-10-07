import {
  BankOutlined,
  BranchesOutlined,
  DeleteOutlined,
  DownloadOutlined,
  DownOutlined,
  EditOutlined,
  EyeInvisibleOutlined,
  EyeOutlined,
  FileDoneOutlined,
  FileTextOutlined,
  LockOutlined,
  PaperClipOutlined,
  PlusOutlined,
  RollbackOutlined,
  SendOutlined,
  StopOutlined,
  UploadOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { history, useAccess, useParams, useSearchParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  Alert,
  App,
  Button,
  Dropdown,
  Input,
  InputNumber,
  Segmented,
  Select,
  Skeleton,
  Table,
  Tooltip,
} from 'antd';
import dayjs from 'dayjs';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  DictTextInput,
  IncotermInput,
  PreviewPages,
} from '@/components/DocFields';
import { useQuoteDicts, useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import type { LineResult } from '@/pages/quotation/calc';
import { formatMargin } from '@/pages/quotation/calc';
import { useAppTheme } from '@/theme/AppTheme';
import {
  DICT_PI_DELIVERY_TIME,
  DICT_PI_PAYMENT_TERM,
  DICT_PORT_OF_SHIPMENT,
  DICT_WARRANTY,
} from '@/utils/dict';
import { formatAmount, formatDateTime } from '@/utils/format';
import {
  Card,
  CHANNEL,
  ChainCard,
  DISCOUNT,
  KIND,
  PATHS,
  PI_STATUS,
  Pill,
  PiStatusPill,
  partyAddress,
  RECEIPT_STATUS,
  ReceiptStatusPill,
  SalesPageTitle,
} from '../components';
import {
  type Party,
  type Pi,
  type PiItem,
  type PiVersion,
  type PreviewResult,
  piApi,
  readBizError,
  type SavePi,
} from '../service';
import { calcPiLine, calcPiTotals } from './calc';
import {
  AddItemsModal,
  ConfirmReceiptModal,
  ExportModal,
  PartyModal,
  ReasonModal,
  SentPromptModal,
  SlipModal,
  useBankOptions,
} from './dialogs';

const PREVIEW_KEY = 'zhul_pi_preview';
const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

interface Draft {
  buyer: Party | null;
  consignee: Party | null;
  saveBuyerToCustomer: boolean;
  saveConsigneeToCustomer: boolean;
  deliveryTime: string;
  paymentTerm: string;
  incoterm: string;
  incotermPlace: string;
  portOfShipment: string;
  remark: string;
  bankAccountId: number | null;
  discountType: number;
  discountValue: number | null;
  items: SavePi['items'];
  fees: {
    key: number;
    feeName: string;
    amount: number | null;
    remark: string;
  }[];
}

let feeSeq = 0;

const toDraft = (v: PiVersion): Draft => ({
  buyer: v.buyer ?? null,
  consignee: v.consignee ?? null,
  saveBuyerToCustomer: false,
  saveConsigneeToCustomer: false,
  deliveryTime: v.deliveryTime ?? '',
  paymentTerm: v.paymentTerm ?? '',
  incoterm: v.incoterm ?? '',
  incotermPlace: v.incotermPlace ?? '',
  portOfShipment: v.portOfShipment ?? '',
  remark: v.remark ?? '',
  bankAccountId: v.bankAccount?.id ?? null,
  discountType: v.discountType ?? DISCOUNT.NONE,
  discountValue: v.discountType ? v.discountValue : null,
  items: v.items.map((i) => ({
    id: i.id,
    description: i.description ?? '',
    leadTime: i.leadTime,
    warranty: i.warranty,
    quantity: i.quantity,
    unitPrice: i.unitPrice,
    hsCode: i.hsCode ?? '',
    originCountry: i.originCountry ?? '',
    remark: i.remark ?? '',
  })),
  fees: v.fees.map((f) => ({
    key: ++feeSeq,
    feeName: f.feeName,
    amount: f.amount,
    remark: f.remark ?? '',
  })),
});

const toSave = (d: Draft): SavePi => ({
  buyer: d.buyer,
  consignee: d.consignee,
  saveBuyerToCustomer: d.saveBuyerToCustomer,
  saveConsigneeToCustomer: d.saveConsigneeToCustomer,
  deliveryTime: d.deliveryTime,
  paymentTerm: d.paymentTerm,
  incoterm: d.incoterm,
  incotermPlace: d.incotermPlace,
  portOfShipment: d.portOfShipment,
  remark: d.remark,
  bankAccountId: d.bankAccountId,
  discountType: d.discountType,
  discountValue: d.discountType === DISCOUNT.NONE ? 0 : d.discountValue,
  items: d.items,
  fees: d.fees
    .filter((f) => f.feeName.trim())
    .map((f) => ({
      feeName: f.feeName.trim(),
      amount: f.amount ?? 0,
      remark: f.remark.trim(),
    })),
});

const readPref = () => {
  try {
    return window.localStorage.getItem(PREVIEW_KEY) !== 'off';
  } catch {
    return true;
  }
};

const savePref = (open: boolean) => {
  try {
    window.localStorage.setItem(PREVIEW_KEY, open ? 'on' : 'off');
  } catch {
    // 无痕模式等拿不到本地存储时只是不记住
  }
};

// ---------------------------------------------------------------- 版本对比

interface DiffRow {
  kind: '新增' | '删除' | '修改';
  label: string;
  before: string;
  after: string;
}

const diffVersions = (
  base: PiVersion,
  next: PiVersion,
  cur: string,
): DiffRow[] => {
  const rows: DiffRow[] = [];
  const baseItems = new Map(base.items.map((i) => [i.quotationItemId, i]));
  const nextItems = new Map(next.items.map((i) => [i.quotationItemId, i]));
  next.items.forEach((i, idx) => {
    const b = baseItems.get(i.quotationItemId);
    const tag = `第 ${idx + 1} 行 ${i.model}`;
    if (!b) {
      rows.push({
        kind: '新增',
        label: tag,
        before: '—',
        after: `${i.quantity} × ${formatAmount(i.unitPrice, cur)}`,
      });
      return;
    }
    if (b.quantity !== i.quantity)
      rows.push({
        kind: '修改',
        label: `${tag} 数量`,
        before: String(b.quantity),
        after: String(i.quantity),
      });
    if (b.unitPrice !== i.unitPrice) {
      rows.push({
        kind: '修改',
        label: `${tag} 单价`,
        before: formatAmount(b.unitPrice, cur),
        after: formatAmount(i.unitPrice, cur),
      });
    }
  });
  for (const b of base.items) {
    if (!nextItems.has(b.quotationItemId)) {
      rows.push({
        kind: '删除',
        label: b.model,
        before: `${b.quantity} × ${formatAmount(b.unitPrice, cur)}`,
        after: '—',
      });
    }
  }
  const baseFees = new Map(base.fees.map((f) => [f.feeName, f.amount]));
  const nextFees = new Map(next.fees.map((f) => [f.feeName, f.amount]));
  for (const [name, amount] of nextFees) {
    const b = baseFees.get(name);
    if (b === undefined)
      rows.push({
        kind: '新增',
        label: name,
        before: '—',
        after: formatAmount(amount, cur),
      });
    else if (b !== amount)
      rows.push({
        kind: '修改',
        label: name,
        before: formatAmount(b, cur),
        after: formatAmount(amount, cur),
      });
  }
  for (const [name, amount] of baseFees) {
    if (!nextFees.has(name))
      rows.push({
        kind: '删除',
        label: name,
        before: formatAmount(amount, cur),
        after: '—',
      });
  }
  if (base.discountAmount !== next.discountAmount) {
    rows.push({
      kind: '修改',
      label: '整单折扣',
      before: formatAmount(-base.discountAmount, cur),
      after: formatAmount(-next.discountAmount, cur),
    });
  }
  for (const [key, text] of [
    ['buyer', '买方'],
    ['consignee', '收货人'],
  ] as const) {
    const b = base[key]?.name ?? '—';
    const n = next[key]?.name ?? '—';
    if (
      b !== n ||
      partyAddress(base[key] ?? undefined) !==
        partyAddress(next[key] ?? undefined)
    ) {
      rows.push({ kind: '修改', label: text, before: b, after: n });
    }
  }
  for (const [key, text] of [
    ['deliveryTime', '交期'],
    ['paymentTerm', '付款条件'],
    ['portOfShipment', '起运港'],
    ['remark', '备注'],
  ] as const) {
    if ((base[key] ?? '') !== (next[key] ?? ''))
      rows.push({
        kind: '修改',
        label: text,
        before: base[key] || '—',
        after: next[key] || '—',
      });
  }
  return rows;
};

// ---------------------------------------------------------------- 页面

const PiDetail: React.FC = () => {
  const { id: idParam } = useParams<{ id: string }>();
  const id = Number(idParam);
  const [search, setSearch] = useSearchParams();
  const viewVersion = search.get('version')
    ? Number(search.get('version'))
    : undefined;
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess();
  const wide = useWide();
  const { leadTimeOptions } = useQuoteDicts();
  const [pi, setPi] = useState<Pi>();
  const [draft, setDraft] = useState<Draft>();
  const [saved, setSaved] = useState('');
  const [error, setError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [base, setBase] = useState<PiVersion>();
  const [previewOpen, setPreviewOpen] = useState(readPref);
  const [preview, setPreview] = useState<
    PreviewResult & { loading?: boolean; error?: string }
  >({});
  const [exportOpen, setExportOpen] = useState(false);
  const [sentPrompt, setSentPrompt] = useState<number>();
  const [partyType, setPartyType] = useState<1 | 3>();
  const [slipOpen, setSlipOpen] = useState(false);
  const [receiptOpen, setReceiptOpen] = useState(false);
  const [voidReceipt, setVoidReceipt] = useState<number>();
  const [addOpen, setAddOpen] = useState(false);
  const previewAbort = useRef<AbortController | undefined>(undefined);

  const apply = useCallback((res: Pi) => {
    setPi(res);
    const d = toDraft(res.version);
    setDraft(d);
    setSaved(JSON.stringify(toSave(d)));
  }, []);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      apply(await piApi.detail(id, viewVersion));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id, viewVersion, apply]);

  useEffect(() => {
    load();
  }, [load]);

  // 对比的基准：修改中的版本对比当前有效版本；查看历史版本时对比它之前最近的已发送版本
  const baseNo = useMemo(() => {
    if (!pi) return undefined;
    const v = pi.version.versionNo;
    const sent = pi.versions
      .filter((x) => x.status === 2 && x.versionNo < v)
      .map((x) => x.versionNo);
    return sent.length ? Math.max(...sent) : undefined;
  }, [pi]);

  useEffect(() => {
    if (!pi || !baseNo) {
      setBase(undefined);
      return;
    }
    piApi
      .detail(pi.id, baseNo)
      .then((r) => setBase(r.version))
      .catch(() => setBase(undefined));
  }, [pi, baseNo]);

  const editable = !!pi?.editable;
  const payload = useMemo(
    () => (draft ? JSON.stringify(toSave(draft)) : ''),
    [draft],
  );
  const dirty = editable && payload !== saved;
  const cur = pi?.currencyCode ?? 'USD';
  const rate = pi?.exchangeRate ?? 1;
  const banks = useBankOptions(pi?.currencyCode);

  useEffect(() => {
    if (!dirty) return undefined;
    const warn = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const itemById = useMemo(
    () => new Map(pi?.version.items.map((i) => [i.id, i]) ?? []),
    [pi],
  );

  const results = useMemo(() => {
    const map = new Map<number, LineResult>();
    for (const d of draft?.items ?? []) {
      map.set(
        d.id,
        calcPiLine(
          {
            costPrice: itemById.get(d.id)?.costPrice,
            quantity: d.quantity,
            unitPrice: d.unitPrice,
          },
          rate,
        ),
      );
    }
    return map;
  }, [draft, itemById, rate]);

  const totals = useMemo(
    () =>
      calcPiTotals(
        (draft?.items ?? []).map((d) => ({
          line: {
            costPrice: itemById.get(d.id)?.costPrice,
            quantity: d.quantity,
            unitPrice: d.unitPrice,
          },
          result: results.get(d.id) as LineResult,
        })),
        (draft?.fees ?? []).map((f) => f.amount ?? 0),
        draft?.discountType ?? 0,
        draft?.discountValue,
        rate,
      ),
    [draft, itemById, results, rate],
  );

  // 实时预览：停止输入 1 秒后请求，新请求发出时取消上一个
  useEffect(() => {
    if (
      !pi ||
      !draft ||
      !editable ||
      !previewOpen ||
      totals.discountError ||
      !draft.buyer
    )
      return undefined;
    const t = window.setTimeout(async () => {
      previewAbort.current?.abort();
      const ctrl = new AbortController();
      previewAbort.current = ctrl;
      setPreview((p) => ({ ...p, loading: true }));
      try {
        const res = await piApi.preview(pi.id, toSave(draft), ctrl.signal);
        if (ctrl.signal.aborted || res.superseded) return;
        setPreview({ ...res, loading: false });
      } catch (e) {
        if (ctrl.signal.aborted) return;
        setPreview((p) => ({
          ...p,
          loading: false,
          error: readBizError(e).message,
        }));
      }
    }, 1000);
    return () => window.clearTimeout(t);
  }, [payload, previewOpen, pi?.id, editable]);

  const updateItem = (
    itemId: number,
    patch: Partial<SavePi['items'][number]>,
  ) =>
    setDraft((d) =>
      d
        ? {
            ...d,
            items: d.items.map((i) =>
              i.id === itemId ? { ...i, ...patch } : i,
            ),
          }
        : d,
    );

  const firstError = () => {
    if (!draft?.buyer?.name) return '请填写买方';
    for (const d of draft.items) {
      const model = itemById.get(d.id)?.model ?? '';
      if (!d.quantity || d.quantity < 1) return `${model}：数量需要是正整数`;
      if (!d.unitPrice || d.unitPrice <= 0) return `${model}：单价需要大于 0`;
    }
    return totals.discountError;
  };

  const save = async (): Promise<Pi | undefined> => {
    if (!pi || !draft) return undefined;
    const err = firstError();
    if (err) {
      message.error(err);
      return undefined;
    }
    setSaving(true);
    try {
      const res = await piApi.save(pi.id, toSave(draft));
      apply(res);
      return res;
    } catch (e) {
      message.error(readBizError(e).message);
      return undefined;
    } finally {
      setSaving(false);
    }
  };

  const ensureSaved = async () => (dirty ? save() : pi);

  const act = async (fn: () => Promise<Pi>, done: string) => {
    try {
      apply(await fn());
      message.success(done);
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const markSent = async (channel: number) => {
    const current = await ensureSaved();
    if (!current) return;
    try {
      const res = await piApi.markSent(current.id, channel);
      apply(res);
      setSentPrompt(undefined);
      message.success(
        current.editable
          ? `已发送，Rev.${res.currentVersionNo} 为当前有效版本`
          : '已记一次发送',
      );
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const showVersion = (v?: number) => {
    const next = new URLSearchParams(search);
    if (v) next.set('version', String(v));
    else next.delete('version');
    setSearch(next);
  };

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!pi || !draft) return <Skeleton active paragraph={{ rows: 12 }} />;

  const v = pi.version;
  const revising = editable && pi.currentVersionNo > 0;
  const historical =
    !!viewVersion &&
    viewVersion !== (pi.editingVersionNo ?? pi.currentVersionNo);
  const compact = editable && previewOpen && wide;
  const canConvert =
    pi.status === PI_STATUS.SENT &&
    !pi.editingVersionNo &&
    pi.receiptStatus !== RECEIPT_STATUS.NONE &&
    !historical;

  // ---------------------------------------------------------------- 操作

  const sentMenu = {
    items: [
      { key: String(CHANNEL.PDF), label: '发送了 PDF' },
      { key: String(CHANNEL.IMAGE), label: '发送了图片' },
      { key: String(CHANNEL.EXCEL), label: '发送了 Excel' },
    ],
    onClick: ({ key }: { key: string }) => markSent(Number(key)),
  };

  const convert = () =>
    modal.confirm({
      title: '转成销售订单？',
      content: (
        <div style={{ color: palette.sub }}>
          按当前有效版本 Rev.{pi.currentVersionNo}（
          {formatAmount(v.totalAmount, cur)}）生成订单，收款状态「
          {pi.receiptStatusName}」。 转成后 PI
          锁定；来源报价单按成交的型号变为「已成交」或「部分成交」，客户询盘变为「已成交」。订单创建后不能修改，只能取消。
        </div>
      ),
      okText: '转成订单',
      onOk: async () => {
        try {
          const order = await piApi.convert(pi.id);
          message.success(`已生成销售订单 ${order.soNo}`);
          history.push(PATHS.order(order.id));
        } catch (e) {
          message.error(readBizError(e).message);
        }
      },
    });

  const voidPi = () =>
    modal.confirm({
      title: '作废这张 PI？',
      content: '作废后不能恢复，水单记录保留；已有到账记录的 PI 不能作废。',
      okText: '作废',
      okButtonProps: { danger: true },
      onOk: () => act(() => piApi.voidPi(pi.id), 'PI 已作废'),
    });

  const removeDraft = () =>
    modal.confirm({
      title: '删除这张草稿 PI？',
      content: '删除后不能恢复，报价单里的型号可以重新开 PI。',
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await piApi.remove(pi.id);
          message.success('草稿已删除');
          history.push(PATHS.piList);
        } catch (e) {
          message.error(readBizError(e).message);
        }
      },
    });

  const previewToggle = wide && (
    <Button
      icon={previewOpen ? <EyeInvisibleOutlined /> : <EyeOutlined />}
      onClick={() => {
        setPreviewOpen(!previewOpen);
        savePref(!previewOpen);
      }}
    >
      {previewOpen ? '收起预览' : '显示预览'}
    </Button>
  );

  const actions = historical ? (
    <>
      <Button icon={<DownloadOutlined />} onClick={() => setExportOpen(true)}>
        导出 Rev.{v.versionNo}
      </Button>
      <Button type="primary" onClick={() => showVersion()}>
        回到当前版本
      </Button>
    </>
  ) : editable ? (
    <>
      {previewToggle}
      {revising ? (
        <Button
          icon={<RollbackOutlined />}
          onClick={() =>
            modal.confirm({
              title: `放弃 Rev.${pi.editingVersionNo}？`,
              content: `放弃后回到 Rev.${pi.currentVersionNo}，这次的修改不保留。`,
              okText: '放弃',
              okButtonProps: { danger: true },
              onOk: () => act(() => piApi.abandon(pi.id), '已放弃这个版本'),
            })
          }
        >
          放弃这个版本
        </Button>
      ) : (
        <Button icon={<DeleteOutlined />} onClick={removeDraft}>
          删除
        </Button>
      )}
      <Button
        icon={<DownloadOutlined />}
        onClick={async () => (await ensureSaved()) && setExportOpen(true)}
      >
        导出
      </Button>
      <Button
        loading={saving}
        disabled={!dirty}
        onClick={() => save().then((r) => r && message.success('已保存'))}
      >
        {dirty ? '保存' : '已保存'}
      </Button>
      <Dropdown menu={sentMenu} trigger={['click']}>
        <Button type="primary" icon={<SendOutlined />}>
          {revising ? `发送 Rev.${pi.editingVersionNo}` : '标为已发送'}{' '}
          <DownOutlined />
        </Button>
      </Dropdown>
    </>
  ) : (
    <>
      <Button icon={<DownloadOutlined />} onClick={() => setExportOpen(true)}>
        导出
      </Button>
      {pi.status === PI_STATUS.SENT && (
        <>
          {pi.editingVersionNo ? (
            <Button icon={<EditOutlined />} onClick={() => showVersion()}>
              继续修改 Rev.{pi.editingVersionNo}
            </Button>
          ) : (
            <Button
              icon={<EditOutlined />}
              onClick={() => act(() => piApi.revise(pi.id), '已生成新版本草稿')}
            >
              修改（出新版本）
            </Button>
          )}
          <Button icon={<StopOutlined />} onClick={voidPi}>
            作废
          </Button>
          <Tooltip title={canConvert ? '' : '有水单或到账后才能转成订单'}>
            <Button
              type="primary"
              icon={<FileDoneOutlined />}
              disabled={!canConvert}
              onClick={convert}
            >
              转成订单
            </Button>
          </Tooltip>
        </>
      )}
      {pi.status === PI_STATUS.CONVERTED && pi.order && (
        <Button
          type="primary"
          icon={<FileTextOutlined />}
          onClick={() => history.push(PATHS.order(pi.order?.id as number))}
        >
          查看订单 {pi.order.soNo}
        </Button>
      )}
    </>
  );

  // ---------------------------------------------------------------- 买方 / 收货人与表头

  /** 收货人与买方的名称、地址都相同即视为「同买方」 */
  const samePartyAs = (a: Party, b?: Party | null) =>
    !!b &&
    a.name === b.name &&
    (a.address ?? '') === (b.address ?? '') &&
    (a.country ?? '') === (b.country ?? '');

  const partyCard = (type: 1 | 3) => {
    const p = type === 3 ? draft.buyer : draft.consignee;
    const sameAsBuyer = type === 1 && !!p && samePartyAs(p, draft.buyer);
    const isNew = p && !p.partyId && !sameAsBuyer;
    const saveFlag =
      type === 3 ? draft.saveBuyerToCustomer : draft.saveConsigneeToCustomer;
    return (
      <div
        style={{
          padding: 14,
          borderRadius: 12,
          background: palette.inset,
          border: `1px solid ${palette.hairline}`,
          minWidth: 0,
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            marginBottom: 6,
          }}
        >
          <span style={{ fontSize: 13, color: palette.sub }}>
            {type === 3 ? '买方（发票抬头 Bill To）' : '收货人（Consignee）'}
          </span>
          {isNew && <Pill tone="orange">本次新填</Pill>}
          {sameAsBuyer && <Pill tone="gray">同买方</Pill>}
          {editable && (
            <a
              style={{ marginLeft: 'auto', fontSize: 13 }}
              onClick={() => setPartyType(type)}
            >
              {p ? '改选' : '选择'}
            </a>
          )}
        </div>
        {p ? (
          <>
            <div style={{ fontWeight: 600, color: palette.ink }}>{p.name}</div>
            <div style={{ fontSize: 12, color: palette.sub }}>
              {partyAddress(p)}
            </div>
            <div style={{ fontSize: 12, color: palette.mute }}>
              {[
                p.taxId && `TAX ID ${p.taxId}`,
                p.contact && `Attn: ${p.contact}`,
                p.phone,
                p.email,
              ]
                .filter(Boolean)
                .join(' · ')}
            </div>
            {editable && isNew && (
              <label
                style={{
                  display: 'inline-flex',
                  gap: 6,
                  marginTop: 6,
                  fontSize: 12,
                  color: palette.sub,
                  cursor: 'pointer',
                }}
              >
                <input
                  type="checkbox"
                  checked={saveFlag}
                  onChange={(e) =>
                    setDraft({
                      ...draft,
                      [type === 3
                        ? 'saveBuyerToCustomer'
                        : 'saveConsigneeToCustomer']: e.target.checked,
                    })
                  }
                />
                保存时存到客户档案
              </label>
            )}
          </>
        ) : (
          <div
            style={{
              color: type === 3 ? palette.red : palette.mute,
              fontSize: 13,
            }}
          >
            {type === 3 ? '还没有买方，请选择或填写' : '不显示收货人'}
          </div>
        )}
      </div>
    );
  };

  const field = (text: string, node: React.ReactNode, hint?: string) => (
    <div style={{ minWidth: 0 }}>
      <div style={{ fontSize: 13, color: palette.sub, marginBottom: 6 }}>
        {text}
      </div>
      {node}
      {hint && (
        <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
          {hint}
        </div>
      )}
    </div>
  );

  const header = (
    <Card style={{ marginBottom: 16, padding: 20 }}>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
          gap: 12,
          marginBottom: 16,
        }}
      >
        {partyCard(3)}
        {partyCard(1)}
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: compact ? '1fr 1fr' : '1.2fr 1.4fr 1.2fr 1fr',
          gap: 16,
        }}
      >
        {field(
          '交期',
          <DictTextInput
            dictType={DICT_PI_DELIVERY_TIME}
            value={draft.deliveryTime}
            placeholder="3-5 days after payment"
            onChange={(v) => setDraft({ ...draft, deliveryTime: v })}
            ariaLabel="交期"
          />,
          '新建时按型号中最长的货期带出',
        )}
        {field(
          '付款条件',
          <DictTextInput
            dictType={DICT_PI_PAYMENT_TERM}
            value={draft.paymentTerm}
            onChange={(v) => setDraft({ ...draft, paymentTerm: v })}
            ariaLabel="付款条件"
          />,
        )}
        {field(
          '贸易术语',
          <IncotermInput
            incoterm={draft.incoterm}
            place={draft.incotermPlace}
            customerCountry={pi.customerCountry}
            onChange={(incoterm, incotermPlace) =>
              setDraft({ ...draft, incoterm, incotermPlace })
            }
          />,
        )}
        {field(
          '起运港',
          <DictTextInput
            dictType={DICT_PORT_OF_SHIPMENT}
            value={draft.portOfShipment}
            placeholder="Hong Kong"
            onChange={(v) => setDraft({ ...draft, portOfShipment: v })}
            ariaLabel="起运港"
          />,
        )}
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: compact ? '1fr' : '2fr 1fr',
          gap: 16,
          marginTop: 16,
        }}
      >
        {field(
          '收款账户',
          <Select
            style={{ width: '100%' }}
            value={draft.bankAccountId ?? undefined}
            placeholder={
              banks.length === 0 ? `还没有 ${cur} 收款账户` : '选择收款账户'
            }
            allowClear
            status={!draft.bankAccountId ? 'warning' : undefined}
            options={banks.map((b) => ({
              value: b.id,
              label: `${b.currencyCode} · ${b.bankName} · ${b.accountNoMasked}${b.isDefault ? '（默认）' : ''}`,
            }))}
            onChange={(x) => setDraft({ ...draft, bankAccountId: x ?? null })}
            aria-label="收款账户"
          />,
          banks.length === 0
            ? `请先在「系统管理 → 收款账户」中添加 ${cur} 账户，没有收款账户不能发送`
            : `默认取 ${cur} 默认收款账户；发送后不受账户修改影响`,
        )}
        {field(
          '币种 · 汇率',
          <Input
            readOnly
            prefix={<LockOutlined />}
            value={`${cur} · ${Number(rate).toFixed(6)}`}
            suffix={
              <span style={{ fontSize: 12, color: palette.mute }}>
                开 PI 时的系统汇率
              </span>
            }
            aria-label="币种与汇率"
          />,
        )}
      </div>
      <div style={{ marginTop: 16 }}>
        {field(
          '备注（显示在 PI 上）',
          <Input
            value={draft.remark}
            maxLength={500}
            onChange={(e) => setDraft({ ...draft, remark: e.target.value })}
            aria-label="备注"
          />,
        )}
      </div>
    </Card>
  );

  // 只读时的买方 / 收货人 / 条款
  const readonlyHeader = (
    <Card style={{ marginBottom: 16, padding: 20 }}>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
          gap: 12,
          marginBottom: 12,
        }}
      >
        {partyCard(3)}
        {partyCard(1)}
      </div>
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: '6px 24px',
          fontSize: 13,
          color: palette.sub,
        }}
      >
        <span>交期：{v.deliveryTime || '—'}</span>
        <span>付款条件：{v.paymentTerm || '—'}</span>
        <span>
          贸易术语：
          {[v.incoterm, v.incotermPlace].filter(Boolean).join(' ') || '—'}
        </span>
        <span>起运港：{v.portOfShipment || '—'}</span>
        <span>
          收款账户：
          {v.bankAccount
            ? `${v.bankAccount.bankName} ****${v.bankAccount.accountNo.slice(-4)}`
            : '—'}
        </span>
        <span>
          汇率：{cur} {Number(rate).toFixed(6)}
        </span>
        {v.remark && <span>备注：{v.remark}</span>}
      </div>
    </Card>
  );

  // ---------------------------------------------------------------- 型号

  const modelCell = (src: PiItem) => (
    <div style={{ minWidth: 0 }}>
      <div style={{ fontWeight: 600, color: palette.ink }}>{src.model}</div>
      <div style={{ fontSize: 12, color: palette.mute }}>
        {[src.brand, src.category, src.quotationNo].filter(Boolean).join(' · ')}
      </div>
    </div>
  );

  const belowFloor = (src?: PiItem, r?: LineResult) =>
    src?.floorMargin != null &&
    r?.marginRate != null &&
    r.marginRate < src.floorMargin;

  const editColumns: TableColumnsType<SavePi['items'][number]> = [
    { title: '#', key: 'no', width: 44, render: (_, __, i) => i + 1 },
    {
      title: '型号 / 品牌 · 来源报价单',
      key: 'model',
      width: compact ? 180 : 250,
      render: (_, d) => modelCell(itemById.get(d.id) as PiItem),
    },
    {
      title: '数量',
      key: 'qty',
      width: 80,
      render: (_, d) => (
        <InputNumber
          size="small"
          min={1}
          precision={0}
          value={d.quantity}
          onChange={(x) => updateItem(d.id, { quantity: x ?? 0 })}
          style={{ width: '100%' }}
          aria-label="数量"
        />
      ),
    },
    {
      title: '单价',
      key: 'price',
      width: 150,
      render: (_, d) => {
        const src = itemById.get(d.id) as PiItem;
        const changed =
          src.quotedPrice != null &&
          d.unitPrice != null &&
          Math.abs(src.quotedPrice - d.unitPrice) > 0.001;
        return (
          <div style={{ display: 'grid', gap: 2 }}>
            <InputNumber
              size="small"
              prefix={cur}
              min={0}
              precision={2}
              value={d.unitPrice}
              status={changed ? 'warning' : undefined}
              onChange={(x) => updateItem(d.id, { unitPrice: x })}
              style={{ width: '100%' }}
              aria-label="单价"
            />
            {changed && (
              <span style={{ fontSize: 12, color: palette.orange }}>
                报价 {formatAmount(src.quotedPrice as number, cur)}
              </span>
            )}
          </div>
        );
      },
    },
    {
      title: '小计',
      key: 'amount',
      width: 120,
      align: 'right',
      render: (_, d) => (
        <b style={num}>
          {formatAmount((results.get(d.id) as LineResult).amount, cur)}
        </b>
      ),
    },
    {
      title: 'HS 编码 · 原产国',
      key: 'hs',
      width: 150,
      hidden: compact,
      render: (_, d) => (
        <div style={{ display: 'grid', gap: 4 }}>
          <Input
            size="small"
            value={d.hsCode}
            maxLength={16}
            placeholder="HS 编码"
            onChange={(e) => updateItem(d.id, { hsCode: e.target.value })}
            aria-label="HS 编码"
          />
          <Input
            size="small"
            value={d.originCountry}
            maxLength={64}
            placeholder="原产国"
            onChange={(e) =>
              updateItem(d.id, { originCountry: e.target.value })
            }
            aria-label="原产国"
          />
        </div>
      ),
    },
    {
      title: '采购成本',
      key: 'cost',
      width: 110,
      align: 'right',
      hidden: compact,
      render: (_, d) => {
        const c = itemById.get(d.id)?.costPrice;
        return c == null ? (
          <span style={{ color: palette.mute }}>—</span>
        ) : (
          <span style={num}>{formatAmount(c, 'CNY')}</span>
        );
      },
    },
    {
      title: '毛利率',
      key: 'margin',
      width: 100,
      align: 'right',
      render: (_, d) => {
        const src = itemById.get(d.id);
        const r = results.get(d.id);
        const below = belowFloor(src, r);
        return (
          <div style={{ ...num, color: below ? palette.red : palette.green }}>
            {formatMargin(r?.marginRate)}
            {below && (
              <div style={{ fontSize: 12 }}>
                低于红线 {formatMargin(src?.floorMargin)}
              </div>
            )}
          </div>
        );
      },
    },
    {
      title: '净利润',
      key: 'profit',
      width: 110,
      align: 'right',
      render: (_, d) => {
        const p = results.get(d.id)?.netProfit;
        return p == null ? (
          <span style={{ color: palette.mute }}>—</span>
        ) : (
          <span style={{ ...num, color: p < 0 ? palette.red : palette.green }}>
            {formatAmount(p, cur)}
          </span>
        );
      },
    },
    {
      title: '',
      key: 'del',
      width: 44,
      render: (_, d) => (
        <Tooltip title="移出这张 PI">
          <Button
            size="small"
            type="text"
            icon={<DeleteOutlined />}
            aria-label="删除型号行"
            onClick={() => {
              if (draft.items.length <= 1) {
                message.warning('PI 至少保留一个型号');
                return;
              }
              setDraft({
                ...draft,
                items: draft.items.filter((i) => i.id !== d.id),
              });
            }}
          />
        </Tooltip>
      ),
    },
  ];

  const viewColumns: TableColumnsType<PiItem> = [
    { title: '#', key: 'no', width: 44, render: (_, __, i) => i + 1 },
    {
      title: '型号 / 品牌 · 来源报价单',
      key: 'model',
      width: 260,
      render: (_, r) => modelCell(r),
    },
    { title: '数量', dataIndex: 'quantity', width: 70, align: 'right' },
    {
      title: '单价',
      key: 'price',
      width: 120,
      align: 'right',
      render: (_, r) => (
        <span style={num}>{formatAmount(r.unitPrice, cur)}</span>
      ),
    },
    {
      title: '小计',
      key: 'amount',
      width: 130,
      align: 'right',
      render: (_, r) => <b style={num}>{formatAmount(r.amount, cur)}</b>,
    },
    {
      title: 'HS 编码 · 原产国',
      key: 'hs',
      width: 160,
      render: (_, r) =>
        [r.hsCode, r.originCountry].filter(Boolean).join(' · ') || '—',
    },
    {
      title: '毛利率',
      key: 'margin',
      width: 90,
      align: 'right',
      render: (_, r) => (
        <span
          style={{ ...num, color: r.belowFloor ? palette.red : palette.sub }}
        >
          {formatMargin(r.marginRate)}
        </span>
      ),
    },
    {
      title: '净利润',
      key: 'profit',
      width: 120,
      align: 'right',
      render: (_, r) =>
        r.netProfit == null ? (
          '—'
        ) : (
          <span style={num}>{formatAmount(r.netProfit, cur)}</span>
        ),
    },
  ];

  const itemsBlock = (
    <div>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          margin: '0 0 10px',
        }}
      >
        <b style={{ fontSize: 16, color: palette.ink }}>
          型号（{editable ? draft.items.length : v.items.length}）
        </b>
        <span style={{ fontSize: 12, color: palette.mute }}>
          {editable
            ? '单价默认取报价单售价，改了会提示原报价；HS 编码、原产国取商品主数据；采购成本、毛利率、净利润不会出现在 PI 上'
            : '采购成本、毛利率、净利润只在系统内显示'}
        </span>
        {editable && (
          <Button
            style={{ marginLeft: 'auto' }}
            icon={<PlusOutlined />}
            onClick={async () => (await ensureSaved()) && setAddOpen(true)}
          >
            从报价单添加型号
          </Button>
        )}
      </div>
      {editable ? (
        <Table<SavePi['items'][number]>
          rowKey="id"
          size="middle"
          columns={editColumns}
          dataSource={draft.items}
          pagination={false}
          scroll={{ x: compact ? 800 : 1240 }}
          onRow={(d) =>
            belowFloor(itemById.get(d.id), results.get(d.id))
              ? { style: { background: palette.redSoft } }
              : {}
          }
          expandable={{
            expandedRowRender: (d) => (
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: compact ? '1fr 1fr' : '2fr 1fr 1fr 2fr',
                  gap: 12,
                }}
              >
                {field(
                  '描述（英文）',
                  <Input
                    value={d.description}
                    maxLength={300}
                    onChange={(e) =>
                      updateItem(d.id, { description: e.target.value })
                    }
                    aria-label="描述"
                  />,
                )}
                {field(
                  '货期',
                  <Select
                    style={{ width: '100%' }}
                    value={d.leadTime || undefined}
                    options={leadTimeOptions}
                    allowClear
                    onChange={(x) => updateItem(d.id, { leadTime: x ?? 0 })}
                    aria-label="货期"
                  />,
                )}
                {field(
                  '质保',
                  <DictTextInput
                    dictType={DICT_WARRANTY}
                    value={d.warranty}
                    onChange={(v) => updateItem(d.id, { warranty: v })}
                    ariaLabel="质保"
                  />,
                )}
                {field(
                  '备注（显示在 PI 上）',
                  <Input
                    value={d.remark}
                    maxLength={300}
                    onChange={(e) =>
                      updateItem(d.id, { remark: e.target.value })
                    }
                    aria-label="型号备注"
                  />,
                )}
                {compact && (
                  <>
                    {field(
                      'HS 编码',
                      <Input
                        value={d.hsCode}
                        maxLength={16}
                        onChange={(e) =>
                          updateItem(d.id, { hsCode: e.target.value })
                        }
                        aria-label="HS 编码"
                      />,
                    )}
                    {field(
                      '原产国',
                      <Input
                        value={d.originCountry}
                        maxLength={64}
                        onChange={(e) =>
                          updateItem(d.id, { originCountry: e.target.value })
                        }
                        aria-label="原产国"
                      />,
                    )}
                  </>
                )}
              </div>
            ),
          }}
        />
      ) : (
        <Table<PiItem>
          rowKey="id"
          size="middle"
          columns={viewColumns}
          dataSource={v.items}
          pagination={false}
          scroll={{ x: 1000 }}
          onRow={(r) =>
            r.belowFloor ? { style: { background: palette.redSoft } } : {}
          }
        />
      )}
    </div>
  );

  // ---------------------------------------------------------------- 费用、折扣与合计

  const fees = draft.fees;
  const setFees = (next: Draft['fees']) => setDraft({ ...draft, fees: next });

  const feesCard = editable ? (
    <Card style={{ padding: 20, flex: 1, minWidth: 0 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 12,
        }}
      >
        <b style={{ color: palette.ink }}>费用与折扣</b>
        <span style={{ fontSize: 12, color: palette.mute }}>
          运费、手续费不计入毛利率；折扣按小计计算
        </span>
        <a
          style={{ marginLeft: 'auto' }}
          onClick={() =>
            setFees([
              ...fees,
              { key: ++feeSeq, feeName: '', amount: null, remark: '' },
            ])
          }
        >
          <PlusOutlined /> 加费用行
        </a>
      </div>
      {fees.map((f) => (
        <div
          key={f.key}
          style={{
            display: 'flex',
            gap: 12,
            alignItems: 'center',
            marginBottom: 8,
          }}
        >
          <Input
            style={{ flex: 1 }}
            value={f.feeName}
            maxLength={64}
            placeholder="Shipping Cost、Bank Charge"
            onChange={(e) =>
              setFees(
                fees.map((x) =>
                  x.key === f.key ? { ...x, feeName: e.target.value } : x,
                ),
              )
            }
            aria-label="费用名称"
          />
          <InputNumber
            style={{ width: 160 }}
            prefix={cur}
            min={0}
            precision={2}
            value={f.amount}
            onChange={(x) =>
              setFees(
                fees.map((y) => (y.key === f.key ? { ...y, amount: x } : y)),
              )
            }
            aria-label="费用金额"
          />
          <Button
            type="text"
            icon={<DeleteOutlined />}
            aria-label="删除费用行"
            onClick={() => setFees(fees.filter((x) => x.key !== f.key))}
          />
        </div>
      ))}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          flexWrap: 'wrap',
          marginTop: 12,
          padding: '12px 14px',
          borderRadius: 12,
          background: palette.inset,
        }}
      >
        <b style={{ color: palette.ink }}>整单折扣</b>
        <Segmented
          size="small"
          value={draft.discountType}
          onChange={(x) =>
            setDraft({
              ...draft,
              discountType: Number(x),
              discountValue:
                Number(x) === DISCOUNT.NONE ? null : draft.discountValue,
            })
          }
          options={[
            { value: DISCOUNT.NONE, label: '不打折' },
            { value: DISCOUNT.PERCENT, label: '按百分比' },
            { value: DISCOUNT.AMOUNT, label: '按金额' },
          ]}
        />
        {draft.discountType !== DISCOUNT.NONE && (
          <InputNumber
            style={{ width: 140 }}
            min={0}
            precision={2}
            prefix={draft.discountType === DISCOUNT.AMOUNT ? cur : undefined}
            suffix={draft.discountType === DISCOUNT.PERCENT ? '%' : undefined}
            value={draft.discountValue}
            status={totals.discountError ? 'error' : undefined}
            onChange={(x) => setDraft({ ...draft, discountValue: x })}
            aria-label="折扣"
          />
        )}
        {totals.discountAmount > 0 && (
          <span style={{ ...num, marginLeft: 'auto', color: palette.orange }}>
            Discount {formatAmount(-totals.discountAmount, cur)}
          </span>
        )}
        {totals.discountError && (
          <span style={{ color: palette.red, fontSize: 12, width: '100%' }}>
            {totals.discountError}
          </span>
        )}
      </div>
    </Card>
  ) : (
    <Card style={{ padding: 20, flex: 1, minWidth: 0 }}>
      {v.fees.length === 0 && v.discountAmount === 0 && (
        <div style={{ color: palette.mute, fontSize: 13 }}>没有费用与折扣</div>
      )}
      {v.fees.map((f) => (
        <Row
          key={f.feeName}
          label={f.feeName}
          value={formatAmount(f.amount, cur)}
        />
      ))}
      {v.discountAmount > 0 && (
        <Row
          label={`Discount${v.discountType === DISCOUNT.PERCENT ? `（${v.discountValue}%）` : ''}`}
          value={formatAmount(-v.discountAmount, cur)}
          color={palette.orange}
        />
      )}
    </Card>
  );

  const t = editable
    ? totals
    : {
        itemAmount: v.itemAmount,
        feeAmount: v.feeAmount,
        discountAmount: v.discountAmount,
        totalAmount: v.totalAmount,
        totalAmountCny: v.totalAmountCny,
        netProfit: v.netProfit ?? null,
        netProfitCny: v.netProfitCny ?? null,
        marginRate: v.marginRate ?? null,
      };
  const belowCount = editable
    ? draft.items.filter((d) =>
        belowFloor(itemById.get(d.id), results.get(d.id)),
      ).length
    : v.items.filter((i) => i.belowFloor).length;

  const totalsCard = (
    <Card style={{ padding: 20, width: compact ? '100%' : 400 }}>
      <Row label="小计" value={formatAmount(t.itemAmount, cur)} />
      <Row label="费用" value={formatAmount(t.feeAmount, cur)} />
      {t.discountAmount > 0 && (
        <Row
          label="折扣"
          value={formatAmount(-t.discountAmount, cur)}
          color={palette.orange}
        />
      )}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'baseline',
          margin: '10px 0 2px',
        }}
      >
        <b style={{ fontSize: 16, color: palette.ink }}>合计</b>
        <b style={{ ...num, fontSize: 24, color: palette.ink }}>
          {formatAmount(t.totalAmount, cur)}
        </b>
      </div>
      {cur !== 'CNY' && (
        <div
          style={{
            ...num,
            textAlign: 'right',
            fontSize: 12,
            color: palette.mute,
          }}
        >
          ≈ {formatAmount(t.totalAmountCny, 'CNY')}（汇率{' '}
          {Number(rate).toFixed(6)}）
        </div>
      )}
      <div
        style={{ height: 1, background: palette.hairline, margin: '12px 0' }}
      />
      <Row
        label="合计净利润（折扣后）"
        value={
          t.netProfit == null
            ? '—'
            : `${formatAmount(t.netProfit, cur)}${cur !== 'CNY' ? ` / ${formatAmount(t.netProfitCny, 'CNY')}` : ''}`
        }
        color={
          t.netProfit != null && t.netProfit < 0 ? palette.red : palette.green
        }
      />
      <Row
        label="合计毛利率（折扣后）"
        value={formatMargin(t.marginRate)}
        color={
          t.marginRate != null && t.marginRate < 0 ? palette.red : palette.green
        }
      />
      {belowCount > 0 && (
        <div style={{ fontSize: 12, color: palette.orange, marginTop: 8 }}>
          {belowCount} 行低于红线（只提示，不影响发送）
        </div>
      )}
    </Card>
  );

  // ---------------------------------------------------------------- 版本

  const diffs =
    base && (editable || historical || v.versionNo > 1)
      ? diffVersions(base, v, cur)
      : [];
  const versionsCard = pi.versions.length > 1 && (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: wide ? 'minmax(280px, 1fr) minmax(0, 2fr)' : '1fr',
        gap: 16,
        marginBottom: 16,
      }}
    >
      <Card style={{ padding: 16 }}>
        <div style={{ fontSize: 13, color: palette.sub, marginBottom: 8 }}>
          版本
        </div>
        {[...pi.versions].reverse().map((x) => {
          const active = x.versionNo === v.versionNo;
          const tag =
            x.status === 1 ? (
              <Pill tone="orange">编辑中</Pill>
            ) : x.versionNo === pi.currentVersionNo ? (
              <Pill tone="green">当前有效</Pill>
            ) : x.status === 3 ? (
              <Pill tone="mute">已放弃</Pill>
            ) : null;
          return (
            <button
              type="button"
              key={x.versionNo}
              onClick={() =>
                showVersion(
                  x.versionNo === (pi.editingVersionNo ?? pi.currentVersionNo)
                    ? undefined
                    : x.versionNo,
                )
              }
              style={{
                all: 'unset',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                width: '100%',
                boxSizing: 'border-box',
                padding: '8px 10px',
                borderRadius: 10,
                background: active ? palette.accentSoft : 'transparent',
              }}
            >
              <b style={{ color: palette.ink }}>Rev.{x.versionNo}</b>
              {tag}
              <span
                style={{
                  ...num,
                  marginLeft: 'auto',
                  fontSize: 12,
                  color: palette.mute,
                }}
              >
                {x.sentAt
                  ? `${dayjs(x.sentAt).format('MM-DD HH:mm')} 发送 · `
                  : x.status === 1
                    ? '未发送 · '
                    : ''}
                {formatAmount(x.totalAmount, cur)}
              </span>
            </button>
          );
        })}
      </Card>
      <Card style={{ padding: 16 }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            marginBottom: 10,
          }}
        >
          <BranchesOutlined style={{ color: palette.link }} />
          <b style={{ color: palette.ink }}>
            {base
              ? `Rev.${base.versionNo} → Rev.${v.versionNo} 改了什么`
              : `Rev.${v.versionNo}`}
          </b>
          {editable && dirty && (
            <span style={{ fontSize: 12, color: palette.orange }}>
              按已保存的内容对比
            </span>
          )}
        </div>
        {!base ? (
          <div style={{ color: palette.mute, fontSize: 13 }}>
            第一个版本，没有可对比的上一版本
          </div>
        ) : diffs.length === 0 ? (
          <div style={{ color: palette.mute, fontSize: 13 }}>
            与 Rev.{base.versionNo} 内容相同
          </div>
        ) : (
          <div style={{ display: 'grid', gap: 6 }}>
            {diffs.map((d) => (
              <div
                key={`${d.kind}-${d.label}`}
                style={{
                  display: 'grid',
                  gridTemplateColumns:
                    '48px minmax(0, 1.4fr) minmax(0, 1fr) 16px minmax(0, 1fr)',
                  gap: 10,
                  alignItems: 'center',
                  padding: '8px 10px',
                  borderRadius: 10,
                  background: palette.inset,
                  fontSize: 13,
                }}
              >
                <Pill
                  tone={
                    d.kind === '新增'
                      ? 'green'
                      : d.kind === '删除'
                        ? 'red'
                        : 'orange'
                  }
                >
                  {d.kind}
                </Pill>
                <span style={{ color: palette.ink, fontWeight: 600 }}>
                  {d.label}
                </span>
                <span style={{ ...num, color: palette.mute }}>{d.before}</span>
                <span style={{ color: palette.mute }}>→</span>
                <span style={{ ...num, color: palette.orange }}>{d.after}</span>
              </div>
            ))}
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                padding: '8px 10px',
                fontSize: 13,
              }}
            >
              <b style={{ color: palette.ink }}>合计</b>
              <span style={num}>
                {formatAmount(base.totalAmount, cur)} →{' '}
                <b style={{ color: palette.orange }}>
                  {formatAmount(v.totalAmount, cur)}
                </b>
              </span>
            </div>
          </div>
        )}
      </Card>
    </div>
  );

  // ---------------------------------------------------------------- 收款

  const canSlip = access['sales:pi:receipt-slip'];
  const canConfirm = access['sales:pi:receipt-confirm'];
  const receivable =
    pi.status === PI_STATUS.SENT || pi.status === PI_STATUS.CONVERTED;

  const receiptsCard = pi.status !== PI_STATUS.DRAFT && (
    <Card style={{ padding: 20 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 12,
        }}
      >
        <b style={{ fontSize: 16, color: palette.ink }}>收款</b>
        <ReceiptStatusPill status={pi.receiptStatus} />
      </div>
      <div
        style={{
          padding: 14,
          borderRadius: 12,
          background: palette.inset,
          marginBottom: 12,
        }}
      >
        <Row
          label="PI 合计"
          value={formatAmount(
            pi.currentVersionNo
              ? (pi.versions.find((x) => x.versionNo === pi.currentVersionNo)
                  ?.totalAmount ?? 0)
              : 0,
            cur,
          )}
        />
        <Row
          label="已到账"
          value={formatAmount(pi.receivedAmount, cur)}
          color={palette.green}
        />
        <Row label="手续费差额" value={formatAmount(pi.feeDiffAmount, cur)} />
        <Row
          label="剩余"
          value={formatAmount(pi.remainingAmount, cur)}
          color={pi.remainingAmount > 0 ? palette.orange : palette.green}
        />
      </div>
      {receivable && (canSlip || canConfirm) && (
        <div style={{ display: 'flex', gap: 8, marginBottom: 6 }}>
          {canSlip && (
            <Button icon={<UploadOutlined />} onClick={() => setSlipOpen(true)}>
              上传水单
            </Button>
          )}
          {canConfirm && (
            <Button
              type="primary"
              icon={<BankOutlined />}
              onClick={() => setReceiptOpen(true)}
            >
              登记到账
            </Button>
          )}
        </div>
      )}
      <div style={{ fontSize: 12, color: palette.mute, marginBottom: 12 }}>
        业务员上传水单；登记到账需要财务权限
      </div>
      {pi.receipts.length === 0 ? (
        <div style={{ color: palette.mute, fontSize: 13 }}>
          还没有收款记录。客户付款后让客户发来水单并上传。
        </div>
      ) : (
        <div style={{ display: 'grid', gap: 8 }}>
          {[...pi.receipts].reverse().map((r) => {
            const slip = r.kind === KIND.SLIP;
            const voided = r.status === 2;
            return (
              <div
                key={r.id}
                style={{
                  padding: '10px 12px',
                  borderRadius: 12,
                  border: `1px solid ${palette.hairline}`,
                  opacity: voided ? 0.6 : 1,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  {slip ? (
                    <FileTextOutlined style={{ color: palette.link }} />
                  ) : (
                    <BankOutlined style={{ color: palette.green }} />
                  )}
                  <b style={{ color: palette.ink }}>{slip ? '水单' : '到账'}</b>
                  <b
                    style={{
                      ...num,
                      color: palette.ink,
                      textDecoration: voided ? 'line-through' : undefined,
                    }}
                  >
                    {formatAmount(r.amount, cur)}
                  </b>
                  {slip ? (
                    <Pill tone={r.matched ? 'green' : 'orange'}>
                      {r.matched ? '已到账' : '待到账'}
                    </Pill>
                  ) : voided ? (
                    <Pill tone="mute">已作废</Pill>
                  ) : null}
                  <span
                    style={{
                      marginLeft: 'auto',
                      fontSize: 12,
                      color: palette.mute,
                    }}
                  >
                    {r.receiptDate?.slice(5)}
                  </span>
                </div>
                <div style={{ fontSize: 12, color: palette.sub, marginTop: 4 }}>
                  {[
                    `${r.operatorName ?? ''}${slip ? '上传' : '登记'}`,
                    !slip && r.bankAccountName
                      ? `收款账户 ${r.bankAccountName}`
                      : '',
                    !slip && r.feeDiff > 0
                      ? `手续费差额 ${formatAmount(r.feeDiff, cur)}`
                      : '',
                    !slip && cur !== 'CNY'
                      ? `折合 ${formatAmount(r.amountCny, 'CNY')}`
                      : '',
                    r.note,
                    voided && r.voidReason ? `作废原因：${r.voidReason}` : '',
                  ]
                    .filter(Boolean)
                    .join(' · ')}
                </div>
                {slip && r.files.length > 0 && (
                  <div
                    style={{
                      display: 'flex',
                      flexWrap: 'wrap',
                      gap: 10,
                      marginTop: 4,
                    }}
                  >
                    {r.files.map((f, i) => (
                      <a
                        key={f.fileKey}
                        style={{ fontSize: 12 }}
                        onClick={() =>
                          piApi
                            .openSlipFile(pi.id, r.id, i)
                            .catch((e) => message.error((e as Error).message))
                        }
                      >
                        <PaperClipOutlined /> {f.fileName}
                      </a>
                    ))}
                  </div>
                )}
                {!voided &&
                  ((slip && canSlip && !r.matched) ||
                    (!slip && canConfirm)) && (
                    <div style={{ marginTop: 6 }}>
                      {slip ? (
                        <a
                          style={{ fontSize: 12, color: palette.red }}
                          onClick={() =>
                            modal.confirm({
                              title: '删除这张水单？',
                              okText: '删除',
                              okButtonProps: { danger: true },
                              onOk: () =>
                                act(
                                  () => piApi.deleteSlip(pi.id, r.id),
                                  '水单已删除',
                                ),
                            })
                          }
                        >
                          删除
                        </a>
                      ) : (
                        <a
                          style={{ fontSize: 12, color: palette.red }}
                          onClick={() => setVoidReceipt(r.id)}
                        >
                          作废
                        </a>
                      )}
                    </div>
                  )}
              </div>
            );
          })}
        </div>
      )}
    </Card>
  );

  // ---------------------------------------------------------------- 横幅与预览

  const banner = historical ? (
    <Alert
      type="info"
      showIcon
      style={{ marginBottom: 16 }}
      title={`正在查看 Rev.${v.versionNo}（只读）${v.sentAt ? `，${formatDateTime(v.sentAt).slice(0, 16)} 发送` : ''}`}
    />
  ) : revising ? (
    <Alert
      type="warning"
      showIcon
      icon={<BranchesOutlined />}
      style={{ marginBottom: 16 }}
      title={`你正在修改已发送的 PI：发送前客户看到的仍是 Rev.${pi.currentVersionNo}；发送后 Rev.${pi.editingVersionNo} 成为当前版本，PI 编号不变，导出的单据上不显示版本号`}
    />
  ) : !editable ? (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '12px 16px',
        borderRadius: 12,
        background: palette.inset,
        border: `1px solid ${palette.hairline}`,
        color: palette.sub,
        fontSize: 13,
        marginBottom: 16,
        flexWrap: 'wrap',
      }}
    >
      <LockOutlined />
      <b style={{ color: palette.ink }}>{pi.statusName} · 内容已锁定</b>
      {pi.sendLogs.length > 0 && (
        <span>
          发送记录：
          {pi.sendLogs
            .map(
              (s) =>
                `${dayjs(s.sentAt).format('MM-DD HH:mm')} ${s.channelName}`,
            )
            .join(' · ')}
        </span>
      )}
      <span style={{ marginLeft: 'auto', color: palette.mute }}>
        {pi.status === PI_STATUS.CONVERTED
          ? '已转成订单；需要修改请先取消订单'
          : pi.status === PI_STATUS.SENT
            ? '有水单或到账即可转成订单'
            : ''}
      </span>
    </div>
  ) : (
    !pi.hasBankAccount && (
      <Alert
        type="warning"
        showIcon
        icon={<WarningOutlined />}
        style={{ marginBottom: 16 }}
        title={`还没有 ${cur} 收款账户，PI 发送前需要选择收款账户（在「系统管理 → 收款账户」中维护）`}
      />
    )
  );

  const previewCard = (
    <Card style={{ position: 'sticky', top: 80, padding: 20 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 6,
        }}
      >
        <b style={{ color: palette.ink, fontSize: 16 }}>预览</b>
        <span
          style={{
            marginLeft: 'auto',
            fontSize: 12,
            color: preview.loading ? palette.link : palette.green,
          }}
        >
          {preview.loading
            ? '预览更新中…'
            : preview.updatedAt
              ? `已更新 ${dayjs(preview.updatedAt).format('HH:mm:ss')}`
              : ''}
        </span>
        <Button
          size="small"
          type="text"
          icon={<EyeInvisibleOutlined />}
          onClick={() => {
            setPreviewOpen(false);
            savePref(false);
          }}
        >
          收起预览
        </Button>
      </div>
      <div style={{ fontSize: 12, color: palette.mute, marginBottom: 12 }}>
        停止输入 1
        秒后自动刷新；不显示采购成本、毛利率与净利润；公章、签名来自模版
      </div>
      {!draft.buyer ? (
        <Alert type="info" showIcon title="填写买方后显示预览" />
      ) : preview.unavailable ? (
        <Alert
          type="warning"
          showIcon
          title={preview.message ?? '预览暂时不可用，不影响编辑与导出 Excel'}
        />
      ) : preview.error ? (
        <Alert type="error" showIcon title={preview.error} />
      ) : !preview.pages ? (
        <Skeleton.Node active style={{ width: '100%', height: 420 }} />
      ) : (
        <PreviewPages pages={preview.pages} title="PI 预览" />
      )}
    </Card>
  );

  const main = (
    <div style={{ minWidth: 0 }}>
      {banner}
      {versionsCard}
      {editable ? header : readonlyHeader}
      {itemsBlock}
      <div
        style={{
          display: 'flex',
          gap: 16,
          marginTop: 16,
          flexWrap: compact ? 'wrap' : 'nowrap',
          alignItems: 'flex-start',
        }}
      >
        {feesCard}
        {totalsCard}
      </div>
      <div style={{ marginTop: 16 }}>
        <ChainCard
          type="pi"
          id={pi.id}
          reloadKey={`${pi.status}-${pi.order?.id}`}
        />
      </div>
    </div>
  );

  return (
    <div>
      <SalesPageTitle
        crumbs={[
          <a key="list" onClick={() => history.push(PATHS.piList)}>
            PI
          </a>,
          pi.piNo,
        ]}
        title={
          <>
            {pi.piNo}
            <PiStatusPill status={pi.status} />
            {pi.status !== PI_STATUS.DRAFT && (
              <ReceiptStatusPill status={pi.receiptStatus} />
            )}
            {revising && (
              <Pill tone="orange">正在修改 Rev.{pi.editingVersionNo}</Pill>
            )}
            {dirty && (
              <span
                style={{ fontSize: 12, fontWeight: 400, color: palette.orange }}
              >
                有未保存的修改
              </span>
            )}
          </>
        }
        description={
          <>
            {pi.customerName} · 来源报价单：
            {pi.quotations.map((q, i) => (
              <React.Fragment key={q.id}>
                {i > 0 && '、'}
                <a onClick={() => history.push(PATHS.quotation(q.id))}>
                  {q.quotationNo}
                </a>
              </React.Fragment>
            ))}{' '}
            · {pi.ownerName ?? '—'} {formatDateTime(pi.createTime).slice(0, 16)}{' '}
            创建
          </>
        }
        actions={actions}
      />

      {compact ? (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'minmax(0, 2.3fr) minmax(340px, 1fr)',
            gap: 16,
            alignItems: 'start',
          }}
        >
          {main}
          {previewCard}
        </div>
      ) : pi.status !== PI_STATUS.DRAFT && !editable ? (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: wide
              ? 'minmax(0, 2.6fr) minmax(320px, 1fr)'
              : '1fr',
            gap: 16,
            alignItems: 'start',
          }}
        >
          {main}
          {receiptsCard}
        </div>
      ) : (
        <>
          {main}
          {pi.status !== PI_STATUS.DRAFT && (
            <div style={{ marginTop: 16 }}>{receiptsCard}</div>
          )}
        </>
      )}

      <ExportModal
        pi={pi}
        open={exportOpen}
        onClose={() => setExportOpen(false)}
        onExported={(channel) => {
          setExportOpen(false);
          message.success('文件已下载');
          if (!historical) setSentPrompt(channel);
        }}
      />
      <SentPromptModal
        pi={pi}
        channel={sentPrompt}
        onClose={() => setSentPrompt(undefined)}
        onConfirm={markSent}
      />
      {partyType && (
        <PartyModal
          piId={pi.id}
          type={partyType}
          open
          value={partyType === 3 ? draft.buyer : draft.consignee}
          customer={{ id: pi.customerId, name: pi.customerName }}
          buyer={draft.buyer}
          onClose={() => setPartyType(undefined)}
          onOk={(p, saveToCustomer) => {
            setDraft(
              partyType === 3
                ? { ...draft, buyer: p, saveBuyerToCustomer: saveToCustomer }
                : {
                    ...draft,
                    consignee: p,
                    saveConsigneeToCustomer: saveToCustomer,
                  },
            );
            setPartyType(undefined);
          }}
        />
      )}
      <SlipModal
        pi={pi}
        open={slipOpen}
        onClose={() => setSlipOpen(false)}
        onDone={(res) => {
          apply(res);
          setSlipOpen(false);
        }}
      />
      <ConfirmReceiptModal
        pi={pi}
        open={receiptOpen}
        onClose={() => setReceiptOpen(false)}
        onDone={(res) => {
          apply(res);
          setReceiptOpen(false);
        }}
      />
      <ReasonModal
        open={voidReceipt != null}
        title="作废这笔到账？"
        description="作废后不计入已到账，收款状态随之重算；记录保留并标为已作废。"
        placeholder="如 金额录错"
        okText="作废"
        onClose={() => setVoidReceipt(undefined)}
        onSubmit={async (reason) => {
          try {
            apply(
              await piApi.voidReceipt(pi.id, voidReceipt as number, reason),
            );
            setVoidReceipt(undefined);
            message.success('到账已作废');
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
      <AddItemsModal
        pi={pi}
        open={addOpen}
        onClose={() => setAddOpen(false)}
        onAdded={(res) => {
          apply(res);
          setAddOpen(false);
        }}
      />
    </div>
  );
};

const Row: React.FC<{
  label: string;
  value: React.ReactNode;
  color?: string;
}> = ({ label, value, color }) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        marginBottom: 6,
        fontSize: 13,
      }}
    >
      <span style={{ color: palette.sub }}>{label}</span>
      <span style={{ ...num, color: color ?? palette.ink, fontWeight: 600 }}>
        {value}
      </span>
    </div>
  );
};

export default PiDetail;
