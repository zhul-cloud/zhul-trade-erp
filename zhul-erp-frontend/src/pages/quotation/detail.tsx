import {
  CheckCircleOutlined,
  CopyOutlined,
  DeleteOutlined,
  DownloadOutlined,
  DownOutlined,
  EditOutlined,
  EyeInvisibleOutlined,
  EyeOutlined,
  FileDoneOutlined,
  HistoryOutlined,
  LockOutlined,
  MessageOutlined,
  PlusOutlined,
  RollbackOutlined,
  SendOutlined,
  StopOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { history, useAccess, useParams, useSearchParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  Alert,
  App,
  Button,
  DatePicker,
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
import { type DiffRow, VersionPanel } from '@/components/VersionPanel';
import {
  ConditionPill,
  useQuoteDicts,
  useWide,
} from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { ChainCard, PATHS as SALES_PATHS } from '@/pages/sales/components';
import NewPiModal from '@/pages/sales/pi/NewPiModal';
import { type PiListItem, piApi } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { DICT_WARRANTY } from '@/utils/dict';
import { formatAmount, formatDateTime } from '@/utils/format';
import {
  calcLine,
  calcTotals,
  formatMargin,
  type LineResult,
  MODE_MARGIN,
  MODE_MARKUP,
  MODE_PRICE,
  round2,
} from './calc';
import {
  Card,
  CHANNEL,
  CURRENCIES,
  PATHS,
  Pill,
  QuotationPageTitle,
  QuotationStatusPill,
  STATUS,
} from './components';
import {
  AddItemsModal,
  ExportModal,
  LostReasonModal,
  SentPromptModal,
  TextQuoteModal,
} from './dialogs';
import {
  type PreviewResult,
  type Quotation,
  type QuotationItem,
  quotationApi,
  readBizError,
  type SaveQuotation,
  type SaveQuotationItem,
} from './service';

const PREVIEW_KEY = 'zhul_quotation_preview';

interface Draft {
  currencyCode: string;
  incoterm: string;
  incotermPlace: string;
  validUntil?: string;
  remark: string;
  items: SaveQuotationItem[];
  fees: { key: number; feeName: string; amount: number | null }[];
}

let feeSeq = 0;

const toDraft = (q: Quotation): Draft => ({
  currencyCode: q.currencyCode,
  incoterm: q.incoterm ?? '',
  incotermPlace: q.incotermPlace ?? '',
  validUntil: q.validUntil,
  remark: q.remark ?? '',
  items: q.items.map((i) => ({
    id: i.id,
    description: i.description ?? '',
    leadTime: i.leadTime,
    warranty: i.warranty,
    quantity: i.quantity,
    pricingMode: i.pricingMode,
    marginRate: i.marginRate ?? null,
    markupAmount: i.markupAmount ?? null,
    unitPrice: i.unitPrice,
  })),
  fees: q.fees.map((f) => ({
    key: ++feeSeq,
    feeName: f.feeName,
    amount: f.amount,
  })),
});

const toSave = (d: Draft): SaveQuotation => ({
  currencyCode: d.currencyCode,
  incoterm: d.incoterm,
  incotermPlace: d.incotermPlace,
  validUntil: d.validUntil,
  remark: d.remark,
  items: d.items,
  fees: d.fees
    .filter((f) => f.feeName.trim())
    .map((f) => ({ feeName: f.feeName.trim(), amount: f.amount ?? 0 })),
});

const readPreviewPref = () => {
  try {
    return window.localStorage.getItem(PREVIEW_KEY) !== 'off';
  } catch {
    return true;
  }
};

const savePreviewPref = (open: boolean) => {
  try {
    window.localStorage.setItem(PREVIEW_KEY, open ? 'on' : 'off');
  } catch {
    // 无痕模式等拿不到本地存储时只是不记住
  }
};

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

// ---------------------------------------------------------------- 预览面板

const PreviewPanel: React.FC<{
  state: PreviewResult & { loading?: boolean; error?: string };
  onCollapse: () => void;
}> = ({ state, onCollapse }) => {
  const { palette } = useAppTheme();
  return (
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
            color: state.loading ? palette.link : palette.green,
          }}
        >
          {state.loading
            ? '预览更新中…'
            : state.updatedAt
              ? `已更新 ${dayjs(state.updatedAt).format('HH:mm:ss')}`
              : ''}
        </span>
        <Button
          size="small"
          type="text"
          icon={<EyeInvisibleOutlined />}
          onClick={onCollapse}
        >
          收起预览
        </Button>
      </div>
      <div style={{ fontSize: 12, color: palette.mute, marginBottom: 12 }}>
        停止输入 1 秒后自动刷新，和导出的 PDF
        一致；不显示采购成本价、毛利率与净利润
      </div>
      {state.unavailable ? (
        <Alert
          type="warning"
          showIcon
          title={state.message ?? '预览暂时不可用，不影响编辑与导出 Excel'}
        />
      ) : state.error ? (
        <Alert type="error" showIcon title={state.error} />
      ) : !state.pages ? (
        <Skeleton.Node active style={{ width: '100%', height: 420 }} />
      ) : (
        <PreviewPages pages={state.pages} title="报价单预览" />
      )}
    </Card>
  );
};

// ---------------------------------------------------------------- 版本对比

/** 两个版本按询盘型号明细配对：新增、删除，以及单价、数量、小计、货况货期、质保的变化 */
const diffQuotations = (base: Quotation, next: Quotation): DiffRow[] => {
  const cur = next.currencyCode;
  const rows: DiffRow[] = [];
  const baseItems = new Map(base.items.map((i) => [i.inquiryItemId, i]));
  const nextIds = new Set(next.items.map((i) => i.inquiryItemId));
  next.items.forEach((i, idx) => {
    const b = baseItems.get(i.inquiryItemId);
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
    const change = (label: string, before: string, after: string) => {
      if (before !== after)
        rows.push({ kind: '修改', label: `${tag} ${label}`, before, after });
    };
    change(
      '单价',
      formatAmount(b.unitPrice, cur),
      formatAmount(i.unitPrice, cur),
    );
    change('数量', String(b.quantity), String(i.quantity));
    change('小计', formatAmount(b.amount, cur), formatAmount(i.amount, cur));
    change(
      '货况货期',
      [b.conditionName, b.leadTimeName].filter(Boolean).join(' · ') || '—',
      [i.conditionName, i.leadTimeName].filter(Boolean).join(' · ') || '—',
    );
    change('质保', b.warranty || '—', i.warranty || '—');
  });
  base.items.forEach((b, idx) => {
    if (!nextIds.has(b.inquiryItemId))
      rows.push({
        kind: '删除',
        label: `第 ${idx + 1} 行 ${b.model}`,
        before: `${b.quantity} × ${formatAmount(b.unitPrice, cur)}`,
        after: '—',
      });
  });
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
  const term = (x: Quotation) =>
    [x.incoterm, x.incotermPlace].filter(Boolean).join(' ') || '—';
  for (const [label, before, after] of [
    ['贸易术语', term(base), term(next)],
    ['有效期至', base.validUntil || '—', next.validUntil || '—'],
    ['备注', base.remark || '—', next.remark || '—'],
  ]) {
    if (before !== after) rows.push({ kind: '修改', label, before, after });
  }
  return rows;
};

// ---------------------------------------------------------------- 页面

const QuotationDetail: React.FC = () => {
  const { id: idParam } = useParams<{ id: string }>();
  const id = Number(idParam);
  const [search, setSearch] = useSearchParams();
  const viewVersion = search.get('version')
    ? Number(search.get('version'))
    : undefined;
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const wide = useWide();
  const { leadTimeOptions } = useQuoteDicts();
  const [q, setQ] = useState<Quotation>();
  const [draft, setDraft] = useState<Draft>();
  const [saved, setSaved] = useState<string>('');
  const [error, setError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(readPreviewPref);
  const [preview, setPreview] = useState<
    PreviewResult & { loading?: boolean; error?: string }
  >({});
  const [textOpen, setTextOpen] = useState(false);
  const [exportOpen, setExportOpen] = useState(false);
  const [sentPrompt, setSentPrompt] = useState<number>();
  const [lostOpen, setLostOpen] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [piOpen, setPiOpen] = useState(false);
  const [pis, setPis] = useState<PiListItem[]>([]);
  const [base, setBase] = useState<Quotation>();
  const access = useAccess();
  const previewAbort = useRef<AbortController | undefined>(undefined);

  const apply = useCallback((res: Quotation) => {
    setQ(res);
    const d = toDraft(res);
    setDraft(d);
    setSaved(JSON.stringify(toSave(d)));
  }, []);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      apply(await quotationApi.detail(id, viewVersion));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id, viewVersion, apply]);

  useEffect(() => {
    load();
  }, [load]);

  // 对比的基准：查看的版本之前最近的已发送版本
  const baseNo = useMemo(() => {
    if (!q) return undefined;
    const sent = q.versions
      .filter((x) => x.status === 2 && x.versionNo < q.versionNo)
      .map((x) => x.versionNo);
    return sent.length ? Math.max(...sent) : undefined;
  }, [q]);

  useEffect(() => {
    if (!q || !baseNo) {
      setBase(undefined);
      return;
    }
    quotationApi
      .detail(q.id, baseNo)
      .then(setBase)
      .catch(() => setBase(undefined));
  }, [q, baseNo]);

  // 已发送之后的报价单列出由它开出的 PI 与订单
  useEffect(() => {
    if (!q || q.status === STATUS.DRAFT) {
      setPis([]);
      return;
    }
    piApi
      .byQuotation(q.id)
      .then(setPis)
      .catch(() => setPis([]));
  }, [q]);

  const editable = !!q?.editable;
  const payload = useMemo(
    () => (draft ? JSON.stringify(toSave(draft)) : ''),
    [draft],
  );
  const dirty = editable && payload !== saved;

  useEffect(() => {
    if (!dirty) return undefined;
    const warn = (e: BeforeUnloadEvent) => {
      e.preventDefault();
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const itemById = useMemo(
    () => new Map(q?.items.map((i) => [i.id, i]) ?? []),
    [q],
  );
  const rate = q?.exchangeRate ?? 1;
  const currency = draft?.currencyCode ?? q?.currencyCode ?? 'USD';

  const results = useMemo(() => {
    const map = new Map<number, LineResult>();
    for (const d of draft?.items ?? []) {
      const src = itemById.get(d.id);
      map.set(
        d.id,
        calcLine({
          costPrice: src?.costPrice,
          quantity: d.quantity,
          rate,
          mode: d.pricingMode,
          marginRate: d.marginRate,
          markupAmount: d.markupAmount,
          unitPrice: d.unitPrice,
        }),
      );
    }
    return map;
  }, [draft, itemById, rate]);

  const totals = useMemo(
    () =>
      calcTotals(
        (draft?.items ?? []).map((d) => ({
          costPrice: itemById.get(d.id)?.costPrice,
          quantity: d.quantity,
          result: results.get(d.id) as LineResult,
        })),
        (draft?.fees ?? []).map((f) => f.amount ?? 0),
        rate,
      ),
    [draft, itemById, results, rate],
  );

  // 实时预览：停止输入 1 秒后请求，新请求发出时取消上一个
  // 依赖 payload（draft 的序列化）：内容不变时不重复请求
  useEffect(() => {
    if (!q || !draft || !previewOpen) return undefined;
    const invalid = [...results.values()].some((r) => r.error);
    if (invalid) return undefined;
    const t = window.setTimeout(async () => {
      previewAbort.current?.abort();
      const ctrl = new AbortController();
      previewAbort.current = ctrl;
      setPreview((p) => ({ ...p, loading: true }));
      try {
        const res = await quotationApi.preview(
          q.id,
          toSave(draft),
          ctrl.signal,
        );
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
  }, [payload, previewOpen, q?.id]);

  const updateItem = (itemId: number, patch: Partial<SaveQuotationItem>) =>
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
    for (const d of draft?.items ?? []) {
      const r = results.get(d.id);
      if (r?.error) return `${itemById.get(d.id)?.model ?? ''}：${r.error}`;
      if (!d.quantity || d.quantity < 1)
        return `${itemById.get(d.id)?.model ?? ''}：数量需要是正整数`;
    }
    return undefined;
  };

  const save = async (
    override?: Partial<Draft>,
  ): Promise<Quotation | undefined> => {
    if (!q || !draft) return undefined;
    const err = firstError();
    if (err) {
      message.error(err);
      return undefined;
    }
    setSaving(true);
    try {
      const res = await quotationApi.save(
        q.id,
        toSave({ ...draft, ...override }),
      );
      apply(res);
      return res;
    } catch (e) {
      message.error(readBizError(e).message);
      return undefined;
    } finally {
      setSaving(false);
    }
  };

  /** 文字报价、导出、标为已发送之前先保存未保存的修改 */
  const ensureSaved = async () => (dirty ? save() : q);

  const markSent = async (channel: number) => {
    const current = await ensureSaved();
    if (!current) return;
    try {
      const res = await quotationApi.markSent(current.id, channel);
      apply(res);
      setSentPrompt(undefined);
      setTextOpen(false);
      message.success(
        current.status === STATUS.DRAFT
          ? '已标为已发送，客户询盘变为「已报价」'
          : current.editingVersionNo
            ? `Rev.${current.editingVersionNo} 已发送，成为当前版本`
            : '已记一次发送',
      );
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const act = async (fn: () => Promise<Quotation>, done: string) => {
    try {
      const res = await fn();
      apply(res);
      message.success(done);
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const copyAsNew = async () => {
    try {
      const res = await quotationApi.copy(id);
      message.success(`已复制为新报价单 ${res.quotationNo}`);
      history.push(PATHS.detail(res.id));
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const recalc = async () => {
    const current = await ensureSaved();
    if (!current) return;
    act(() => quotationApi.recalcRate(current.id), '已按新汇率重算');
  };

  /** 切换查看的版本；不传时回到默认版本（修改中的，没有时为当前版本） */
  const showVersion = (v?: number) => {
    const next = new URLSearchParams(search);
    if (v) next.set('version', String(v));
    else next.delete('version');
    setSearch(next);
  };

  const revise = async () => {
    if (!q) return;
    if (q.activePiNo) {
      modal.confirm({
        title: '不能出新版本',
        content: `这张报价单已开出 PI ${q.activePiNo}，客户已经按 PI 在走付款。需要改价或改数量，请在 PI 上点「修改」出 PI 新版本；要另起一张报价单可以「复制为新报价单」。`,
        okText: '打开 PI',
        cancelText: '复制为新报价单',
        onOk: () => q.activePiId && history.push(SALES_PATHS.pi(q.activePiId)),
        onCancel: (close) => {
          if (typeof close === 'function') close();
          copyAsNew();
        },
      });
      return;
    }
    try {
      const res = await quotationApi.revise(q.id);
      showVersion();
      apply(res);
      message.success(`已生成 Rev.${res.versionNo}，改好后标为已发送`);
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!q || !draft) return <Skeleton active paragraph={{ rows: 12 }} />;

  const revising = editable && q.status === STATUS.SENT;
  const historical = q.versionNo !== (q.editingVersionNo ?? q.currentVersionNo);
  const compact = editable && previewOpen && wide;
  const removeItem = (itemId: number) => {
    if (draft.items.length <= 1) {
      message.warning('报价单至少保留一个型号；不需要的话可以删除整张草稿');
      return;
    }
    setDraft({ ...draft, items: draft.items.filter((i) => i.id !== itemId) });
  };

  // ---------------------------------------------------------------- 型号表格

  const pricingCell = (
    d: SaveQuotationItem,
    src: QuotationItem,
    r: LineResult,
  ) => {
    if (src.costPrice == null) {
      return (
        <span style={{ color: palette.mute, fontSize: 12 }}>
          {src.noStock ? '无货，直接填售价' : '没有采购成本价，直接填售价'}
        </span>
      );
    }
    const markupMode = d.pricingMode === MODE_MARKUP;
    const shownMargin =
      d.pricingMode === MODE_MARGIN ? d.marginRate : r.marginRate;
    const restorable =
      src.suggestedMargin != null &&
      (d.pricingMode !== MODE_MARGIN || d.marginRate !== src.suggestedMargin);
    const below =
      src.floorMargin != null &&
      r.marginRate != null &&
      r.marginRate < src.floorMargin;
    return (
      <div style={{ display: 'grid', gap: 4 }}>
        <Segmented
          size="small"
          value={markupMode ? 'markup' : 'margin'}
          onChange={(v) => {
            if (v === 'markup') {
              updateItem(d.id, {
                pricingMode: MODE_MARKUP,
                markupAmount: Math.max(
                  0,
                  round2(r.unitPriceCny - (src.costPrice ?? 0)),
                ),
              });
            } else {
              updateItem(d.id, {
                pricingMode: MODE_MARGIN,
                marginRate: r.marginRate ?? src.suggestedMargin ?? null,
              });
            }
          }}
          options={[
            { value: 'margin', label: '毛利率' },
            { value: 'markup', label: '加价' },
          ]}
        />
        {markupMode ? (
          <InputNumber
            size="small"
            prefix="+ CNY"
            min={0}
            precision={2}
            value={d.markupAmount}
            onChange={(v) =>
              updateItem(d.id, { pricingMode: MODE_MARKUP, markupAmount: v })
            }
            style={{ width: '100%' }}
            aria-label="加价金额"
          />
        ) : (
          <InputNumber
            size="small"
            suffix="%"
            min={0}
            max={99.99}
            precision={2}
            value={shownMargin}
            status={below || r.error ? 'error' : undefined}
            onChange={(v) =>
              updateItem(d.id, { pricingMode: MODE_MARGIN, marginRate: v })
            }
            style={{ width: '100%' }}
            aria-label="毛利率"
          />
        )}
        <span style={{ fontSize: 12, color: palette.mute }}>
          {markupMode
            ? `按加价 · 毛利率 ${formatMargin(r.marginRate)}`
            : `依据：${src.suggestBasis || '—'}`}
        </span>
        {restorable && (
          <a
            style={{ fontSize: 12 }}
            onClick={() =>
              updateItem(d.id, {
                pricingMode: MODE_MARGIN,
                marginRate: src.suggestedMargin ?? null,
              })
            }
          >
            恢复建议毛利率（{formatMargin(src.suggestedMargin)}）
          </a>
        )}
        {below && (
          <span style={{ fontSize: 12, color: palette.red }}>
            低于红线 {formatMargin(src.floorMargin)}
          </span>
        )}
        {r.error && (
          <span style={{ fontSize: 12, color: palette.red }}>{r.error}</span>
        )}
      </div>
    );
  };

  const modelCell = (src: QuotationItem) => (
    <div style={{ minWidth: 0 }}>
      <div style={{ fontWeight: 600, color: palette.ink }}>{src.model}</div>
      <div style={{ fontSize: 12, color: palette.mute }}>
        {[src.brand, src.category, src.inquiryCode].filter(Boolean).join(' · ')}
      </div>
      {src.hints.map((h) => (
        <span key={h} style={{ display: 'inline-block', marginTop: 4 }}>
          <Pill tone="orange">{h}</Pill>
        </span>
      ))}
    </div>
  );

  const editColumns: TableColumnsType<SaveQuotationItem> = [
    { title: '#', key: 'no', width: 44, render: (_, __, i) => i + 1 },
    {
      title: '型号 / 品牌 · 品类 · 来源',
      key: 'model',
      width: compact ? 170 : 240,
      render: (_, d) => modelCell(itemById.get(d.id) as QuotationItem),
    },
    ...(compact
      ? []
      : ([
          {
            title: '货况 · 货期',
            key: 'cond',
            width: 140,
            render: (_: unknown, d: SaveQuotationItem) => (
              <div style={{ display: 'grid', gap: 4 }}>
                <ConditionPill value={itemById.get(d.id)?.itemCondition} />
                <Select
                  size="small"
                  value={d.leadTime || undefined}
                  placeholder="货期"
                  options={leadTimeOptions}
                  onChange={(v) => updateItem(d.id, { leadTime: v ?? 0 })}
                  allowClear
                  aria-label="对客户的货期"
                />
              </div>
            ),
          },
          {
            title: '质保',
            key: 'warranty',
            width: 100,
            render: (_: unknown, d: SaveQuotationItem) => (
              <DictTextInput
                size="small"
                dictType={DICT_WARRANTY}
                value={d.warranty}
                onChange={(v) => updateItem(d.id, { warranty: v })}
                ariaLabel="质保"
              />
            ),
          },
        ] as TableColumnsType<SaveQuotationItem>)),
    {
      title: '数量',
      key: 'qty',
      width: compact ? 68 : 84,
      render: (_, d) => (
        <InputNumber
          size="small"
          min={1}
          precision={0}
          value={d.quantity}
          onChange={(v) => updateItem(d.id, { quantity: v ?? 0 })}
          style={{ width: '100%' }}
          aria-label="数量"
        />
      ),
    },
    {
      title: '采购成本价',
      key: 'cost',
      width: compact ? 116 : 130,
      align: 'right',
      render: (_, d) => {
        const src = itemById.get(d.id) as QuotationItem;
        const r = results.get(d.id) as LineResult;
        return src.costPrice == null ? (
          <span style={{ color: palette.mute }}>
            {src.noStock ? '无货' : '—'}
          </span>
        ) : (
          <div style={num}>
            {formatAmount(src.costPrice, 'CNY')}
            {currency !== 'CNY' && (
              <div style={{ fontSize: 12, color: palette.mute }}>
                {formatAmount(r.costPriceForeign, currency)}
              </div>
            )}
          </div>
        );
      },
    },
    {
      title: '定价方式',
      key: 'pricing',
      width: compact ? 150 : 190,
      render: (_, d) =>
        pricingCell(
          d,
          itemById.get(d.id) as QuotationItem,
          results.get(d.id) as LineResult,
        ),
    },
    {
      title: '售价',
      key: 'price',
      width: compact ? 132 : 150,
      render: (_, d) => {
        const r = results.get(d.id) as LineResult;
        const src = itemById.get(d.id) as QuotationItem;
        const below =
          src.floorMargin != null &&
          r.marginRate != null &&
          r.marginRate < src.floorMargin;
        return (
          <div style={{ display: 'grid', gap: 4 }}>
            <InputNumber
              size="small"
              prefix={currency}
              min={0}
              precision={2}
              value={d.pricingMode === MODE_PRICE ? d.unitPrice : r.unitPrice}
              status={
                below || (src.costPrice == null && !r.unitPrice)
                  ? 'error'
                  : undefined
              }
              onChange={(v) =>
                updateItem(d.id, { pricingMode: MODE_PRICE, unitPrice: v })
              }
              style={{ width: '100%' }}
              aria-label="外币售价"
            />
            {currency !== 'CNY' && (
              <span style={{ ...num, fontSize: 12, color: palette.mute }}>
                {formatAmount(r.unitPriceCny, 'CNY')}
              </span>
            )}
          </div>
        );
      },
    },
    {
      title: compact ? '小计 · 净利润' : '小计',
      key: 'amount',
      width: compact ? 124 : 130,
      align: 'right',
      render: (_, d) => {
        const r = results.get(d.id) as LineResult;
        return (
          <div style={num}>
            <b>{formatAmount(r.amount, currency)}</b>
            {compact && r.netProfit != null && (
              <div
                style={{
                  fontSize: 12,
                  color: r.netProfit < 0 ? palette.red : palette.green,
                }}
              >
                {formatAmount(r.netProfit, currency)}
              </div>
            )}
          </div>
        );
      },
    },
    {
      title: '净利润',
      key: 'profit',
      width: 130,
      align: 'right',
      hidden: compact,
      render: (_, d) => {
        const r = results.get(d.id) as LineResult;
        if (r.netProfit == null)
          return <span style={{ color: palette.mute }}>—</span>;
        const color = r.netProfit < 0 ? palette.red : palette.green;
        return (
          <div style={{ ...num, color }}>
            {formatAmount(r.netProfit, currency)}
            {currency !== 'CNY' && (
              <div style={{ fontSize: 12, color: palette.mute }}>
                {formatAmount(r.netProfitCny, 'CNY')}
              </div>
            )}
          </div>
        );
      },
    },
    {
      title: '',
      key: 'del',
      width: 44,
      render: (_, d) => (
        <Tooltip title="移出这张报价单">
          <Button
            size="small"
            type="text"
            icon={<DeleteOutlined />}
            aria-label="删除型号行"
            onClick={() => removeItem(d.id)}
          />
        </Tooltip>
      ),
    },
  ];

  const viewColumns: TableColumnsType<QuotationItem> = [
    { title: '#', key: 'no', width: 44, render: (_, __, i) => i + 1 },
    {
      title: '型号 / 品牌',
      key: 'model',
      width: 220,
      render: (_, r) => modelCell(r),
    },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    {
      title: '货况',
      key: 'cond',
      width: 110,
      render: (_, r) => <ConditionPill value={r.itemCondition} />,
    },
    { title: '货期', dataIndex: 'leadTimeName', width: 100 },
    { title: '质保', dataIndex: 'warranty', width: 90 },
    { title: '数量', dataIndex: 'quantity', width: 70, align: 'right' },
    {
      title: '单价',
      key: 'price',
      width: 120,
      align: 'right',
      render: (_, r) => (
        <span style={num}>{formatAmount(r.unitPrice, q.currencyCode)}</span>
      ),
    },
    {
      title: '小计',
      key: 'amount',
      width: 130,
      align: 'right',
      render: (_, r) => (
        <b style={num}>{formatAmount(r.amount, q.currencyCode)}</b>
      ),
    },
    ...((q.status === STATUS.WON || q.status === STATUS.PARTIAL
      ? [
          {
            title: '成交',
            key: 'won',
            width: 90,
            render: (_: unknown, r: QuotationItem) =>
              r.won ? (
                <Pill tone="green">已成交</Pill>
              ) : (
                <Pill tone="mute">未成交</Pill>
              ),
          },
        ]
      : []) as TableColumnsType<QuotationItem>),
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
      width: 130,
      align: 'right',
      render: (_, r) =>
        r.netProfit == null ? (
          '—'
        ) : (
          <span style={num}>{formatAmount(r.netProfit, q.currencyCode)}</span>
        ),
    },
  ];

  // ---------------------------------------------------------------- 顶部操作

  const sentMenu = {
    items: [
      { key: String(CHANNEL.TEXT), label: '通过文字发送' },
      { key: String(CHANNEL.PDF), label: '发送了 PDF' },
      { key: String(CHANNEL.IMAGE), label: '发送了图片' },
      { key: String(CHANNEL.EXCEL), label: '发送了 Excel' },
    ],
    onClick: ({ key }: { key: string }) => markSent(Number(key)),
  };

  const actions = editable ? (
    <>
      {wide && (
        <Button
          icon={previewOpen ? <EyeInvisibleOutlined /> : <EyeOutlined />}
          onClick={() => {
            setPreviewOpen(!previewOpen);
            savePreviewPref(!previewOpen);
          }}
        >
          {previewOpen ? '收起预览' : '显示预览'}
        </Button>
      )}
      {revising && (
        <Button
          icon={<RollbackOutlined />}
          onClick={() =>
            modal.confirm({
              title: `放弃 Rev.${q.editingVersionNo}？`,
              content: `放弃后回到 Rev.${q.currentVersionNo}，这次的修改不保留。`,
              okText: '放弃',
              okButtonProps: { danger: true },
              onOk: () =>
                act(() => quotationApi.abandon(q.id), '已放弃这个版本'),
            })
          }
        >
          放弃这个版本
        </Button>
      )}
      <Button
        icon={<MessageOutlined />}
        onClick={async () => (await ensureSaved()) && setTextOpen(true)}
      >
        文字报价
      </Button>
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
          {revising ? `发送 Rev.${q.editingVersionNo}` : '标为已发送'}{' '}
          <DownOutlined />
        </Button>
      </Dropdown>
    </>
  ) : (
    <>
      <Button icon={<MessageOutlined />} onClick={() => setTextOpen(true)}>
        文字报价
      </Button>
      <Button icon={<DownloadOutlined />} onClick={() => setExportOpen(true)}>
        导出
      </Button>
      <Button icon={<CopyOutlined />} onClick={copyAsNew}>
        复制为新报价单
      </Button>
      {historical && (
        <Button onClick={() => showVersion()}>
          回到 Rev.{q.editingVersionNo ?? q.currentVersionNo}
        </Button>
      )}
      {q.status === STATUS.SENT && !historical && (
        <Button icon={<EditOutlined />} onClick={revise}>
          修改（出新版本）
        </Button>
      )}
      {q.status === STATUS.SENT && !historical && (
        <>
          <Button
            icon={<StopOutlined />}
            onClick={() =>
              modal.confirm({
                title: '作废这张报价单？',
                content:
                  '作废后不能恢复；如果只是价格要调整，用「复制为新报价单」更合适。',
                okText: '作废',
                okButtonProps: { danger: true },
                onOk: () =>
                  act(() => quotationApi.voidQuotation(q.id), '报价单已作废'),
              })
            }
          >
            作废
          </Button>
          <Button danger onClick={() => setLostOpen(true)}>
            标为未成交
          </Button>
        </>
      )}
      {(q.status === STATUS.SENT || q.status === STATUS.PARTIAL) &&
        !historical &&
        access.salesPi && (
          <Button
            type="primary"
            icon={<FileDoneOutlined />}
            onClick={() => setPiOpen(true)}
          >
            {q.status === STATUS.PARTIAL ? '就未成交的型号开 PI' : '开 PI'}
          </Button>
        )}
    </>
  );

  // ---------------------------------------------------------------- 渲染

  const fees = draft.fees;
  const setFees = (next: Draft['fees']) => setDraft({ ...draft, fees: next });

  const header = (
    <Card style={{ marginBottom: 16, padding: 20 }}>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: compact
            ? '2fr 1fr 1.4fr'
            : '2.2fr 1fr 1.4fr 1.4fr 1fr',
          gap: 16,
        }}
      >
        <div>
          <div style={{ fontSize: 13, color: palette.sub, marginBottom: 6 }}>
            客户
          </div>
          <div
            style={{
              height: 32,
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              color: palette.ink,
              fontWeight: 600,
            }}
          >
            {q.customerName}
            {q.customerType === 2 && <Pill tone="violet">老客户</Pill>}
          </div>
        </div>
        <div>
          <div style={{ fontSize: 13, color: palette.sub, marginBottom: 6 }}>
            报价币种
          </div>
          <Select
            style={{ width: '100%' }}
            value={draft.currencyCode}
            disabled={revising}
            options={CURRENCIES.map((c) => ({ value: c, label: c }))}
            onChange={(v) => {
              // 换币种要取该币种的系统汇率：立即保存，由后端带回新汇率
              save({ currencyCode: v }).then(
                (r) =>
                  r && message.success(`已切换为 ${v}，汇率按系统汇率更新`),
              );
            }}
            aria-label="报价币种"
          />
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
            {revising
              ? '新版本不能改币种，要换币种请复制为新报价单'
              : '默认取客户币种'}
          </div>
        </div>
        <div>
          <div style={{ fontSize: 13, color: palette.sub, marginBottom: 6 }}>
            汇率（1 {currency} = CNY）
          </div>
          <Input
            value={Number(rate).toFixed(6)}
            readOnly
            prefix={<LockOutlined />}
            suffix={
              <span style={{ color: palette.mute, fontSize: 12 }}>
                系统汇率
              </span>
            }
            aria-label="汇率"
          />
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
            {q.rateTime
              ? `${formatDateTime(q.rateTime).slice(0, 16)} 更新 · `
              : ''}
            在系统管理中维护
          </div>
        </div>
        <div>
          <div style={{ fontSize: 13, color: palette.sub, marginBottom: 6 }}>
            贸易术语
          </div>
          <IncotermInput
            incoterm={draft.incoterm}
            place={draft.incotermPlace}
            customerCountry={q.customerCountry}
            onChange={(incoterm, incotermPlace) =>
              setDraft({ ...draft, incoterm, incotermPlace })
            }
          />
        </div>
        <div>
          <div style={{ fontSize: 13, color: palette.sub, marginBottom: 6 }}>
            有效期至
          </div>
          <DatePicker
            style={{ width: '100%' }}
            value={draft.validUntil ? dayjs(draft.validUntil) : null}
            onChange={(v) =>
              setDraft({
                ...draft,
                validUntil: v ? v.format('YYYY-MM-DD') : undefined,
              })
            }
            aria-label="有效期至"
          />
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
            默认 15 天
          </div>
        </div>
      </div>
      <div style={{ marginTop: 12 }}>
        <div style={{ fontSize: 13, color: palette.sub, marginBottom: 6 }}>
          备注（会显示在报价单上）
        </div>
        <Input
          value={draft.remark}
          maxLength={500}
          onChange={(e) => setDraft({ ...draft, remark: e.target.value })}
          aria-label="备注"
        />
      </div>
    </Card>
  );

  const banners = (
    <div style={{ display: 'grid', gap: 12, marginBottom: 16 }}>
      {historical && (
        <Alert
          type="info"
          showIcon
          title={`正在查看 Rev.${q.versionNo}（只读）${
            q.versions.find((x) => x.versionNo === q.versionNo)?.sentAt
              ? `，${formatDateTime(q.versions.find((x) => x.versionNo === q.versionNo)?.sentAt).slice(0, 16)} 发送`
              : ''
          }`}
        />
      )}
      {revising && (
        <Alert
          type="warning"
          showIcon
          title={`你正在修改已发送的报价单：发送前客户看到的仍是 Rev.${q.currentVersionNo}，询盘状态与开 PI 也按 Rev.${q.currentVersionNo}；标为已发送后 Rev.${q.editingVersionNo} 成为当前版本，编号不变，导出的报价单上不显示版本号`}
        />
      )}
      {q.versions.length > 1 && (
        <VersionPanel
          versions={q.versions}
          currentVersionNo={q.currentVersionNo}
          editingVersionNo={q.editingVersionNo}
          viewingVersionNo={q.versionNo}
          totalAmount={q.totalAmount}
          currency={q.currencyCode}
          base={base}
          diffs={base ? diffQuotations(base, q) : []}
          dirty={dirty}
          wide={wide}
          onSelect={showVersion}
        />
      )}
      {editable && q.systemRate != null && (
        <Alert
          type="warning"
          showIcon
          icon={<WarningOutlined />}
          title={`系统汇率已更新为 ${Number(q.systemRate).toFixed(6)}（原 ${Number(q.exchangeRate).toFixed(6)}），这张草稿仍按 ${Number(q.exchangeRate).toFixed(6)} 计算`}
          action={
            <Button size="small" onClick={recalc}>
              按新汇率重算
            </Button>
          }
        />
      )}
      {q.returningCustomer && (
        <Alert
          type="info"
          showIcon
          icon={<HistoryOutlined />}
          title={`老客户：SOP 建议参考历史成交毛利率（${
            q.returningCustomerMargins && q.returningCustomerMargins.length > 0
              ? `近 ${q.returningCustomerMargins.length} 次：${q.returningCustomerMargins.map((m) => formatMargin(m)).join('、')}`
              : '暂无成交记录'
          }），按品相给出的建议价仅供参考`}
        />
      )}
      {!editable && (
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
          }}
        >
          <LockOutlined />
          <b style={{ color: palette.ink }}>
            {historical
              ? `Rev.${q.versionNo} · 历史版本`
              : `${q.statusName} · 内容已锁定`}
          </b>
          {q.sendLogs.length > 0 && (
            <span>
              发送记录：
              {q.sendLogs
                .map(
                  (s) =>
                    `${dayjs(s.sentAt).format('MM-DD HH:mm')} ${s.channelName}${q.versions.length > 1 && s.versionNo ? `（Rev.${s.versionNo}）` : ''}`,
                )
                .join(' · ')}
            </span>
          )}
          {q.status === STATUS.LOST && q.lostReasonName && (
            <span style={{ color: palette.orange }}>
              未成交原因：{q.lostReasonName}
              {q.lostNote ? `（${q.lostNote}）` : ''}
            </span>
          )}
          <span style={{ marginLeft: 'auto', color: palette.mute }}>
            {q.status === STATUS.SENT && !q.activePiNo
              ? '需要改价？点「修改（出新版本）」，编号不变'
              : q.status === STATUS.SENT || q.status === STATUS.PARTIAL
                ? '客户付款后由 PI 转成订单，报价单会自动变为成交'
                : '需要改价？点「复制为新报价单」生成新草稿'}
          </span>
        </div>
      )}
    </div>
  );

  const itemsCard = (
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
          型号（{editable ? draft.items.length : q.items.length}）
        </b>
        <span style={{ fontSize: 12, color: palette.mute }}>
          {editable
            ? compact
              ? '预览展开时为紧凑列；货况货期、质保、描述点型号行前的展开按钮查看与编辑'
              : '每行可按毛利率或按加价定价，也可直接改外币售价；采购成本价、毛利率、净利润不会出现在发给客户的文件里'
            : '采购成本价、毛利率、净利润只在系统内显示'}
        </span>
        {editable && (
          <Button
            style={{ marginLeft: 'auto' }}
            icon={<PlusOutlined />}
            onClick={async () => (await ensureSaved()) && setAddOpen(true)}
          >
            从询盘添加型号
          </Button>
        )}
      </div>
      {editable ? (
        <Table<SaveQuotationItem>
          rowKey="id"
          size="middle"
          columns={editColumns}
          dataSource={draft.items}
          pagination={false}
          scroll={{ x: compact ? 900 : 1460 }}
          rowClassName={() => ''}
          onRow={(d) => {
            const src = itemById.get(d.id);
            const r = results.get(d.id);
            const below =
              src?.floorMargin != null &&
              r?.marginRate != null &&
              r.marginRate < src.floorMargin;
            return below ? { style: { background: palette.redSoft } } : {};
          }}
          expandable={{
            expandedRowRender: (d) => (
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: compact ? '2fr 1fr 1fr' : '1fr',
                  gap: 12,
                  padding: '4px 0',
                }}
              >
                <div>
                  <div
                    style={{
                      fontSize: 12,
                      color: palette.mute,
                      marginBottom: 4,
                    }}
                  >
                    描述（英文，显示在报价单上）
                  </div>
                  <Input
                    value={d.description}
                    maxLength={300}
                    onChange={(e) =>
                      updateItem(d.id, { description: e.target.value })
                    }
                    placeholder="如 INTERFACE MODULE ET200S"
                    aria-label="描述"
                  />
                </div>
                {compact && (
                  <>
                    <div>
                      <div
                        style={{
                          fontSize: 12,
                          color: palette.mute,
                          marginBottom: 4,
                        }}
                      >
                        货况：{itemById.get(d.id)?.conditionName || '—'} · 货期
                      </div>
                      <Select
                        style={{ width: '100%' }}
                        value={d.leadTime || undefined}
                        options={leadTimeOptions}
                        allowClear
                        onChange={(v) => updateItem(d.id, { leadTime: v ?? 0 })}
                        aria-label="对客户的货期"
                      />
                    </div>
                    <div>
                      <div
                        style={{
                          fontSize: 12,
                          color: palette.mute,
                          marginBottom: 4,
                        }}
                      >
                        质保
                      </div>
                      <DictTextInput
                        dictType={DICT_WARRANTY}
                        value={d.warranty}
                        onChange={(v) => updateItem(d.id, { warranty: v })}
                        ariaLabel="质保"
                      />
                    </div>
                  </>
                )}
              </div>
            ),
          }}
        />
      ) : (
        <Table<QuotationItem>
          rowKey="id"
          size="middle"
          columns={viewColumns}
          dataSource={q.items}
          pagination={false}
          scroll={{ x: 1300 }}
        />
      )}
    </div>
  );

  const feesCard = (
    <Card style={{ padding: 20, flex: 1 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 12,
        }}
      >
        <b style={{ color: palette.ink }}>费用</b>
        <span style={{ fontSize: 12, color: palette.mute }}>
          运费、手续费不计入毛利率与净利润
        </span>
        {editable && (
          <a
            style={{ marginLeft: 'auto' }}
            onClick={() =>
              setFees([
                ...fees,
                { key: ++feeSeq, feeName: 'Shipping Cost', amount: null },
              ])
            }
          >
            <PlusOutlined /> 加费用行
          </a>
        )}
      </div>
      {(editable
        ? fees
        : q.fees.map((f, i) => ({
            key: i,
            feeName: f.feeName,
            amount: f.amount,
          }))
      ).length === 0 && (
        <div style={{ color: palette.mute, fontSize: 13 }}>
          没有费用。需要另收运费或银行手续费时加一行，会单独列在报价单上
        </div>
      )}
      {editable
        ? fees.map((f) => (
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
                placeholder="费用名称，如 Shipping Cost、Bank Charge"
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
                style={{ width: 180 }}
                prefix={currency}
                min={0}
                precision={2}
                value={f.amount}
                onChange={(v) =>
                  setFees(
                    fees.map((x) =>
                      x.key === f.key ? { ...x, amount: v } : x,
                    ),
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
          ))
        : q.fees.map((f) => (
            <div
              key={f.feeName}
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                marginBottom: 6,
              }}
            >
              <span>{f.feeName}</span>
              <span style={num}>{formatAmount(f.amount, q.currencyCode)}</span>
            </div>
          ))}
    </Card>
  );

  const t = editable
    ? totals
    : {
        itemAmount: q.itemAmount,
        feeAmount: q.feeAmount,
        totalAmount: q.totalAmount,
        totalAmountCny: q.totalAmountCny,
        netProfit: q.netProfit ?? null,
        netProfitCny: q.netProfitCny ?? null,
        marginRate: q.marginRate ?? null,
      };
  const belowCount = editable
    ? draft.items.filter((d) => {
        const src = itemById.get(d.id);
        const r = results.get(d.id);
        return (
          src?.floorMargin != null &&
          r?.marginRate != null &&
          r.marginRate < src.floorMargin
        );
      }).length
    : q.items.filter((i) => i.belowFloor).length;
  const hintCount = q.items.filter((i) => i.hints.length > 0).length;

  const totalsCard = (
    <Card style={{ padding: 20, width: compact ? '100%' : 400 }}>
      <Row label="小计" value={formatAmount(t.itemAmount, currency)} />
      <Row label="费用" value={formatAmount(t.feeAmount, currency)} />
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
          {formatAmount(t.totalAmount, currency)}
        </b>
      </div>
      {currency !== 'CNY' && (
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
        label="合计净利润"
        value={
          t.netProfit == null
            ? '—'
            : `${formatAmount(t.netProfit, currency)}${currency !== 'CNY' ? ` / ${formatAmount(t.netProfitCny, 'CNY')}` : ''}`
        }
        color={
          t.netProfit != null && t.netProfit < 0 ? palette.red : palette.green
        }
      />
      <Row
        label="合计毛利率"
        value={formatMargin(t.marginRate)}
        color={palette.green}
      />
      {(belowCount > 0 || hintCount > 0) && (
        <div style={{ fontSize: 12, color: palette.orange, marginTop: 8 }}>
          {belowCount > 0 ? `${belowCount} 行低于红线` : ''}
          {belowCount > 0 && hintCount > 0 ? '，' : ''}
          {hintCount > 0 ? `${hintCount} 行建议主管核价` : ''}
          （只提示，不影响发送）
        </div>
      )}
    </Card>
  );

  const main = (
    <div style={{ minWidth: 0 }}>
      {banners}
      {editable && header}
      {itemsCard}
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
      {pis.length > 0 && (
        <Card style={{ padding: 20, marginTop: 16 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              marginBottom: 12,
            }}
          >
            <b style={{ color: palette.ink, fontSize: 16 }}>PI 与订单</b>
            <span style={{ fontSize: 12, color: palette.mute }}>
              由这张报价单开出的 PI；订单取消后报价单回到「已发送」
            </span>
          </div>
          <div style={{ display: 'grid', gap: 8 }}>
            {pis.map((p) => (
              <div
                key={p.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  flexWrap: 'wrap',
                  padding: '10px 14px',
                  borderRadius: 12,
                  background: palette.inset,
                  border: `1px solid ${palette.hairline}`,
                }}
              >
                <a onClick={() => history.push(SALES_PATHS.pi(p.id))}>
                  <b>{p.piNo}</b>
                </a>
                <Pill
                  tone={
                    p.status === 3
                      ? 'green'
                      : p.status === 4
                        ? 'mute'
                        : 'accent'
                  }
                  dot
                >
                  {p.statusName}
                </Pill>
                {p.status !== 1 && (
                  <Pill tone="gray">{p.receiptStatusName}</Pill>
                )}
                <span style={{ fontSize: 12, color: palette.mute }}>
                  {p.itemCount} 个型号 ·{' '}
                  {formatAmount(p.totalAmount, p.currencyCode)}
                </span>
                {p.soNo && p.orderId && (
                  <a
                    style={{ marginLeft: 'auto' }}
                    onClick={() =>
                      history.push(SALES_PATHS.order(p.orderId as number))
                    }
                  >
                    订单 {p.soNo}
                  </a>
                )}
              </div>
            ))}
          </div>
        </Card>
      )}
      {!editable && (
        <div style={{ marginTop: 16 }}>
          <ChainCard type="quotation" id={q.id} reloadKey={q.status} />
        </div>
      )}
    </div>
  );

  return (
    <div>
      <QuotationPageTitle
        crumbs={[
          <a key="list" onClick={() => history.push(PATHS.list)}>
            报价单
          </a>,
          q.quotationNo,
        ]}
        title={
          <>
            {q.quotationNo}
            <QuotationStatusPill status={q.status} />
            {q.editingVersionNo ? (
              <Pill tone="orange">正在修改 Rev.{q.editingVersionNo}</Pill>
            ) : q.currentVersionNo > 1 ? (
              <Pill tone="accent">Rev.{q.currentVersionNo}</Pill>
            ) : null}
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
            {q.customerName} · 来源询盘：{q.inquiryCodes.join('、') || '—'} ·{' '}
            {q.ownerName ?? '—'} {formatDateTime(q.createTime).slice(0, 16)}{' '}
            创建
            {q.copiedFromNo && (
              <span style={{ marginLeft: 8 }}>
                · 复制自{' '}
                <a
                  onClick={() =>
                    q.copiedFromId && history.push(PATHS.detail(q.copiedFromId))
                  }
                >
                  {q.copiedFromNo}
                </a>
              </span>
            )}
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
          <PreviewPanel
            state={preview}
            onCollapse={() => {
              setPreviewOpen(false);
              savePreviewPref(false);
            }}
          />
        </div>
      ) : (
        main
      )}

      <TextQuoteModal
        quotation={q}
        open={textOpen}
        onClose={() => setTextOpen(false)}
        onMarkSent={markSent}
      />
      <ExportModal
        quotation={q}
        open={exportOpen}
        onClose={() => setExportOpen(false)}
        onExported={(channel) => {
          setExportOpen(false);
          message.success('文件已下载');
          setSentPrompt(channel);
        }}
      />
      <SentPromptModal
        quotation={q}
        channel={sentPrompt}
        onClose={() => setSentPrompt(undefined)}
        onConfirm={markSent}
      />
      <LostReasonModal
        open={lostOpen}
        onClose={() => setLostOpen(false)}
        onSubmit={async (reason, note) => {
          const res = await quotationApi.markLost(q.id, reason, note);
          apply(res);
          setLostOpen(false);
          message.success('已标为未成交');
        }}
      />
      <NewPiModal
        open={piOpen}
        quotationId={q.id}
        onClose={() => setPiOpen(false)}
        onCreated={(pi) => {
          setPiOpen(false);
          history.push(SALES_PATHS.pi(pi.id));
        }}
      />
      <AddItemsModal
        quotation={q}
        open={addOpen}
        onClose={() => setAddOpen(false)}
        onAdded={(res) => {
          apply(res);
          setAddOpen(false);
        }}
      />
      {q.status === STATUS.WON && (
        <div style={{ marginTop: 16, color: palette.green, fontSize: 13 }}>
          <CheckCircleOutlined /> 已成交 ·{' '}
          {q.closedAt ? formatDateTime(q.closedAt) : ''}
        </div>
      )}
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

export default QuotationDetail;
