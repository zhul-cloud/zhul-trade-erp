import {
  CopyOutlined,
  DeleteOutlined,
  DownloadOutlined,
  ExportOutlined,
  FireOutlined,
  PlusOutlined,
  RollbackOutlined,
  SendOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Checkbox,
  Input,
  InputNumber,
  Modal,
  Radio,
  Segmented,
  Select,
  Skeleton,
  Tooltip,
} from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import {
  Card,
  ConditionPill,
  CustomerBrief,
  excludeTax,
  LeadTimeText,
  LevelPill,
  PageTitle,
  Pill,
  TaxHint,
  useLifecycles,
  useQuoteDicts,
  useTaxRates,
  useWide,
} from '../shared/components';
import {
  CHANNEL_OPTIONS,
  CHANNEL_SUPPLIER,
  CHANNEL_TAOBAO,
  channelLabel,
  DIFFICULTY_OPTIONS,
  formatMinutes,
  LIFECYCLE_DISCONTINUED,
  LIFECYCLE_UNKNOWN,
  RETURN_REASONS,
  searchLinks,
} from '../shared/constants';
import ImportModal from '../shared/ImportModal';
import {
  type ItemQuotes,
  type MyQuote,
  type MyTask,
  type MyTaskItem,
  myTaskApi,
  type QuoteEntry,
  readBizError,
} from '../shared/service';
import SupplierSelect from './SupplierSelect';

interface DraftEntry extends QuoteEntry {
  key: string;
}

interface ItemState {
  drafts: DraftEntry[];
  noStock: boolean;
  noStockNote: string;
  /** 生产状态（字典 inquiry_lifecycle 码值），采购询价时核实后可改 */
  lifecycle: number;
  replacementModel: string;
  /** 编辑器里的内容来自已提交的回价（修改回价）；没改动时不随提交重新提交 */
  submittedBase: boolean;
  dirty: boolean;
}

let seq = 0;
const newEntry = (): DraftEntry => ({
  key: `q${++seq}`,
  channel: CHANNEL_TAOBAO,
  itemCondition: 1,
  unitPrice: null,
});

/** 含税报价没选税率时按 13%（字典 inquiry_tax_rate 的默认值） */
const DEFAULT_TAX_RATE = 13;

const ENTRY_COLUMNS =
  '36px 100px minmax(140px, 1.2fr) 190px 130px 110px minmax(100px, 1fr) 32px';
/** 兼职采购没有「推荐」列：推荐由采购负责人审核时选定 */
const ENTRY_COLUMNS_NO_REC =
  '100px minmax(140px, 1.2fr) 190px 130px 110px minmax(100px, 1fr) 32px';

/** 已提交的回价：已提交，或兼职提交后待审核 */
const isSubmitted = (q: MyQuote) => q.status === 2 || q.status === 3;

const REVIEW_PILL: Record<
  number,
  { label: string; tone: 'accent' | 'green' | 'orange' }
> = {
  1: { label: '待审核', tone: 'accent' },
  2: { label: '已通过', tone: 'green' },
  3: { label: '被退回', tone: 'orange' },
};

const entryError = (e: DraftEntry) => {
  if (e.unitPrice == null) return '请填写单价';
  if (e.channel === CHANNEL_SUPPLIER && !e.supplierId) return '请选择供应商';
  if (e.unitPrice < 0) return '单价不能为负';
  return '';
};

/** 我的询价任务：只看分配给自己的任务，逐个型号录入各渠道的询价结果 */
const MyTasksPage: React.FC = () => {
  const wide = useWide();
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const { conditionOptions, leadTimeOptions } = useQuoteDicts();
  const { taxRateOptions } = useTaxRates();
  const { lifecycleOptions } = useLifecycles();
  const [done, setDone] = useState(false);
  const [tasks, setTasks] = useState<MyTask[] | null>(null);
  // 从兼职工作台点进来时带 ?task=任务ID，直接打开该任务
  const [activeId, setActiveId] = useState<number | undefined>(() => {
    const id = Number(new URLSearchParams(window.location.search).get('task'));
    return Number.isInteger(id) && id > 0 ? id : undefined;
  });
  const [detail, setDetail] = useState<{
    task: MyTask;
    items: MyTaskItem[];
    reviewRequired?: boolean;
  } | null>(null);
  const [state, setState] = useState<Record<number, ItemState>>({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [returnOpen, setReturnOpen] = useState(false);
  const [returnReason, setReturnReason] = useState(1);
  const [returnNote, setReturnNote] = useState('');
  const [importOpen, setImportOpen] = useState(false);
  const [counts, setCounts] = useState<{ pending: number; done: number }>({
    pending: 0,
    done: 0,
  });

  const loadList = useCallback(async () => {
    setError('');
    try {
      const [pending, finished] = await Promise.all([
        myTaskApi.list(false),
        myTaskApi.list(true),
      ]);
      setCounts({ pending: pending.length, done: finished.length });
      const list = done ? finished : pending;
      setTasks(list);
      setActiveId((prev) =>
        prev && list.some((t) => t.id === prev) ? prev : list[0]?.id,
      );
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [done]);

  const loadDetail = useCallback(async (id?: number) => {
    if (!id) {
      setDetail(null);
      return;
    }
    try {
      const d = await myTaskApi.detail(id);
      setDetail(d);
      const next: Record<number, ItemState> = {};
      for (const it of d.items) {
        // 有草稿先接着草稿改；没有草稿、但已提交过且还能修改时，把已提交的回价载入编辑器（修改回价）
        const hasDraft = it.quotes.some((q) => q.status === 1);
        const base =
          !hasDraft && d.task.editable !== false && !it.locked
            ? it.quotes.filter(isSubmitted)
            : it.quotes.filter((q) => q.status === 1);
        next[it.id] = {
          drafts: base
            .filter((q) => !q.noStock)
            .map((q) => ({
              key: `q${++seq}`,
              channel: q.channel,
              shopName: q.shopName,
              supplierId: q.supplierId,
              unitPrice: q.unitPrice ?? null,
              taxIncluded: q.taxIncluded,
              taxRate: q.taxIncluded ? Number(q.taxRate) : undefined,
              itemCondition: q.itemCondition || undefined,
              leadTime: q.leadTime,
              note: q.note,
              recommended: q.recommended,
            })),
          noStock: base.some((q) => q.noStock),
          noStockNote: base.find((q) => q.noStock)?.note ?? '',
          lifecycle: it.lifecycle || LIFECYCLE_UNKNOWN,
          replacementModel: it.replacementModel ?? '',
          submittedBase: !hasDraft && base.length > 0,
          dirty: false,
        };
        if (
          next[it.id].drafts.length === 0 &&
          !next[it.id].noStock &&
          !it.quotes.some(isSubmitted)
        ) {
          next[it.id].drafts = [newEntry()];
        }
      }
      setState(next);
    } catch (e) {
      message.error(readBizError(e).message);
    }
  }, []);

  useEffect(() => {
    loadList();
  }, [loadList]);

  useEffect(() => {
    loadDetail(activeId);
  }, [activeId, loadDetail]);

  const patchItem = (id: number, p: Partial<ItemState>) =>
    setState((prev) => ({ ...prev, [id]: { ...prev[id], ...p, dirty: true } }));
  const patchEntry = (id: number, key: string, p: Partial<DraftEntry>) =>
    setState((prev) => ({
      ...prev,
      [id]: {
        ...prev[id],
        dirty: true,
        drafts: prev[id].drafts.map((d) =>
          d.key === key ? { ...d, ...p } : d,
        ),
      },
    }));

  /** 本次要保存的型号：有改动、且填了报价或标了无货 */
  const collect = (
    submit: boolean,
  ): { items: ItemQuotes[]; invalid: string } => {
    const items: ItemQuotes[] = [];
    let invalid = '';
    for (const it of detail?.items ?? []) {
      const s = state[it.id];
      // 已报给客户的型号回价只读，不随本次保存提交
      if (!s || it.locked) continue;
      const hasDraft =
        s.noStock || s.drafts.some((d) => d.unitPrice != null || d.shopName);
      // 保存草稿只带改过的型号；提交时连同之前存过的草稿一起提交。
      // 已提交过的型号（修改回价）只在改动后随「提交」重新提交，不存草稿
      if (s.submittedBase && (!s.dirty || !submit)) continue;
      if (!s.dirty && !(submit && hasDraft)) continue;
      const lifecycle =
        s.lifecycle !== (it.lifecycle || LIFECYCLE_UNKNOWN) ||
        (s.lifecycle === LIFECYCLE_DISCONTINUED &&
          s.replacementModel !== (it.replacementModel ?? ''))
          ? {
              lifecycle: s.lifecycle,
              replacementModel:
                s.lifecycle === LIFECYCLE_DISCONTINUED
                  ? s.replacementModel.trim()
                  : '',
            }
          : {};
      if (s.noStock) {
        items.push({
          itemId: it.id,
          noStock: true,
          noStockNote: s.noStockNote,
          ...lifecycle,
        });
        continue;
      }
      const filled = s.drafts.filter((d) => d.unitPrice != null || d.shopName);
      const bad = filled.find((d) => entryError(d));
      if (bad && !invalid) invalid = `${it.model}：${entryError(bad)}`;
      items.push({
        itemId: it.id,
        quotes: filled.map(({ key: _k, ...q }) => q),
        ...lifecycle,
      });
    }
    return { items, invalid };
  };

  const save = async (submit: boolean) => {
    if (!detail) return;
    const { items, invalid } = collect(submit);
    if (invalid) {
      message.error(invalid);
      return;
    }
    // 提交时只改了生产状态的型号也一并带上，不影响回价进度
    const effective = submit
      ? items.filter(
          (i) => i.noStock || (i.quotes?.length ?? 0) > 0 || i.lifecycle,
        )
      : items;
    if (effective.length === 0) {
      const editedSubmitted = Object.values(state).some(
        (x) => x.submittedBase && x.dirty,
      );
      message.info(
        !submit && editedSubmitted
          ? '已提交过的型号改动后，点「提交回价」才会生效'
          : submit
            ? '还没有填写任何价格或无货'
            : '没有需要保存的改动',
      );
      return;
    }
    setBusy(true);
    try {
      await myTaskApi.save(detail.task.id, effective, submit);
      message.success(
        !submit
          ? '草稿已保存'
          : detail.reviewRequired
            ? `已提交 ${effective.length} 个型号，等采购负责人审核后业务员就能看到`
            : `已提交 ${effective.length} 个型号的回价`,
      );
      await Promise.all([loadDetail(detail.task.id), loadList()]);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const copy = (text: string) => {
    navigator.clipboard
      .writeText(text)
      .then(() => message.success('话术已复制'))
      .catch(() => message.error('复制失败，请手动选择复制'));
  };

  const editable = detail?.task.editable !== false;
  const review = !!detail?.reviewRequired;
  const columns = review ? ENTRY_COLUMNS_NO_REC : ENTRY_COLUMNS;

  if (error) return <ErrorHint message={error} onRetry={loadList} />;

  return (
    <div>
      <PageTitle
        crumbs={['我的询价任务']}
        title="我的询价任务"
        description="逐个型号问价，填好提交即可；也可以下载询价包在 Excel 里填，填完上传回来。"
        actions={
          <>
            <Button
              icon={<DownloadOutlined />}
              disabled={!activeId}
              onClick={() =>
                activeId &&
                myTaskApi
                  .downloadPackage([activeId])
                  .catch((e) => message.error(readBizError(e).message))
              }
            >
              下载询价包
            </Button>
            {tasks && tasks.length > 1 && !done && (
              <Button
                onClick={() =>
                  myTaskApi
                    .downloadPackage(tasks.map((t) => t.id))
                    .catch((e) => message.error(readBizError(e).message))
                }
              >
                全部下载
              </Button>
            )}
            <Button
              type="primary"
              ghost
              icon={<UploadOutlined />}
              onClick={() => setImportOpen(true)}
            >
              导入询价结果
            </Button>
          </>
        }
      />
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: wide ? '340px minmax(0, 1fr)' : 'minmax(0, 1fr)',
          gap: 20,
          alignItems: 'start',
        }}
      >
        <div style={{ display: 'grid', gap: 10 }}>
          <Segmented
            value={done ? 'done' : 'pending'}
            onChange={(v) => setDone(v === 'done')}
            options={[
              { value: 'pending', label: `待处理 ${counts.pending}` },
              { value: 'done', label: `已回价 ${counts.done}` },
            ]}
          />
          {!tasks ? (
            <Skeleton active />
          ) : tasks.length === 0 ? (
            <Card>
              <EmptyHint
                title={done ? '还没有回价完成的任务' : '没有待处理的任务'}
                description={
                  done ? undefined : '采购负责人分配给你的任务会出现在这里。'
                }
              />
            </Card>
          ) : (
            tasks.map((t) => (
              <button
                type="button"
                key={t.id}
                onClick={() => setActiveId(t.id)}
                style={{
                  textAlign: 'left',
                  cursor: 'pointer',
                  padding: 16,
                  borderRadius: 16,
                  background:
                    t.id === activeId ? palette.accentSoft : palette.card,
                  border: `1px solid ${t.id === activeId ? palette.accentLine : palette.hairline}`,
                  display: 'grid',
                  gap: 6,
                }}
              >
                <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span style={{ color: palette.link, fontWeight: 600 }}>
                    {t.taskCode}
                  </span>
                  <LevelPill value={t.level} />
                  {t.urgent && <Pill tone="red">紧急</Pill>}
                  {t.timeout && (
                    <span style={{ marginLeft: 'auto' }}>
                      <Pill tone="red">超时</Pill>
                    </span>
                  )}
                </span>
                <span style={{ color: palette.ink }}>
                  {t.brand}
                  {t.category ? ` · ${t.category}` : ''}
                </span>
                <CustomerBrief
                  customerType={t.customerType}
                  customerName={t.customerName}
                />
                <span
                  style={{ display: 'flex', fontSize: 12, color: palette.mute }}
                >
                  已填 {t.filledCount}/{t.itemCount}
                  <span
                    style={{
                      marginLeft: 'auto',
                      color: t.timeout ? palette.red : palette.mute,
                    }}
                  >
                    {t.shared ? '比价任务 · ' : ''}
                    {t.remainingMinutes == null
                      ? ''
                      : t.remainingMinutes < 0
                        ? `已超时 ${formatMinutes(t.remainingMinutes)}`
                        : `还剩 ${formatMinutes(t.remainingMinutes)}`}
                  </span>
                </span>
              </button>
            ))
          )}
        </div>

        {!detail ? (
          tasks && tasks.length > 0 ? (
            <Skeleton active paragraph={{ rows: 10 }} />
          ) : null
        ) : (
          <div
            style={{
              display: 'grid',
              // 列宽固定为容器宽度，报价表在卡片内横向滚动，不撑开整页
              gridTemplateColumns: 'minmax(0, 1fr)',
              gap: 16,
              minWidth: 0,
            }}
          >
            <Card style={{ padding: 20 }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  flexWrap: 'wrap',
                }}
              >
                <span
                  style={{ fontSize: 17, fontWeight: 700, color: palette.ink }}
                >
                  {detail.task.taskCode} · {detail.task.brand}
                  {detail.task.category ? ` · ${detail.task.category}` : ''}
                </span>
                <LevelPill value={detail.task.level} />
                {detail.task.urgent && (
                  <Pill tone="red">
                    <FireOutlined /> 紧急
                  </Pill>
                )}
                <CustomerBrief
                  customerType={detail.task.customerType}
                  customerName={detail.task.customerName}
                />
                <span style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
                  {!done && (
                    <Button
                      size="small"
                      icon={<RollbackOutlined />}
                      onClick={() => {
                        setReturnReason(1);
                        setReturnNote('');
                        setReturnOpen(true);
                      }}
                    >
                      退回
                    </Button>
                  )}
                  {editable && !done && (
                    <Button
                      size="small"
                      loading={busy}
                      onClick={() => save(false)}
                    >
                      保存草稿
                    </Button>
                  )}
                  {editable && (
                    <Button
                      size="small"
                      type="primary"
                      icon={<SendOutlined />}
                      loading={busy}
                      onClick={() => save(true)}
                    >
                      {done ? '保存修改' : '提交回价'}
                    </Button>
                  )}
                </span>
              </div>
              <div style={{ color: palette.mute, fontSize: 12, marginTop: 8 }}>
                业务员 {detail.task.salesName || '—'}
                {detail.task.quoteDeadline
                  ? ` · 报价截止 ${detail.task.quoteDeadline}`
                  : ''}{' '}
                · 已填 {detail.task.filledCount}/{detail.task.itemCount} ·
                {!editable
                  ? '回价只能查看：询盘已取消，或型号都已报给客户'
                  : review
                    ? '可以部分提交，提交的型号进入待审核，采购负责人审核通过后业务员才能看到；审核通过后仍可修改，修改后重新审核'
                    : '可以部分提交，已提交的型号会立刻计入回价；型号报给客户前都可以修改，修改会留痕'}
              </div>
            </Card>

            {!editable && (
              <Alert
                type="info"
                showIcon
                title="回价只能查看：询盘已取消，或型号都已报给客户"
              />
            )}
            {detail.items.map((it) => {
              const s = state[it.id];
              if (!s) return null;
              // 型号已报给客户后只读，同一任务里其他型号仍可修改
              const itemEditable = editable && !it.locked;
              const submitted = it.quotes.filter(isSubmitted);
              const voided = it.quotes.filter((q) => q.status === 4);
              const rv = it.reviewStatus
                ? REVIEW_PILL[it.reviewStatus]
                : undefined;
              const diff = DIFFICULTY_OPTIONS.find(
                (d) => d.value === it.difficulty,
              )?.label;
              return (
                <Card key={it.id} style={{ padding: 20 }}>
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 10,
                      flexWrap: 'wrap',
                    }}
                  >
                    <span
                      style={{
                        fontSize: 16,
                        fontWeight: 700,
                        color: palette.ink,
                      }}
                    >
                      {it.model}
                    </span>
                    <span style={{ color: palette.sub }}>
                      {it.quantity} {it.unit}
                    </span>
                    {it.locked && (
                      <Pill tone="violet">已报给客户，回价只读</Pill>
                    )}
                    <span
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 6,
                        color: palette.sub,
                        fontSize: 12,
                      }}
                    >
                      生产状态
                      <Select
                        size="small"
                        aria-label="生产状态"
                        style={{ width: 84 }}
                        value={s.lifecycle}
                        disabled={!itemEditable}
                        options={lifecycleOptions}
                        onChange={(v) => patchItem(it.id, { lifecycle: v })}
                      />
                      {s.lifecycle === LIFECYCLE_DISCONTINUED && (
                        <Input
                          size="small"
                          style={{ width: 160 }}
                          placeholder="替代型号（选填）"
                          maxLength={128}
                          value={s.replacementModel}
                          onChange={(e) =>
                            patchItem(it.id, {
                              replacementModel: e.target.value,
                            })
                          }
                        />
                      )}
                    </span>
                    {it.difficulty > 0 && (
                      <span style={{ color: palette.mute, fontSize: 12 }}>
                        难度 {diff}
                      </span>
                    )}
                    {rv && (
                      <Pill tone={rv.tone} dot>
                        {rv.label}
                      </Pill>
                    )}
                    <Checkbox
                      style={{ marginLeft: 'auto' }}
                      checked={s.noStock}
                      onChange={(e) =>
                        patchItem(it.id, { noStock: e.target.checked })
                      }
                    >
                      无货
                    </Checkbox>
                  </div>
                  {it.reviewStatus === 3 && (
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 10,
                        marginTop: 12,
                        padding: '10px 14px',
                        borderRadius: 10,
                        background: palette.orangeSoft,
                        color: palette.orange,
                        fontSize: 13,
                      }}
                    >
                      <RollbackOutlined />
                      <span style={{ flex: 1 }}>被退回：{it.reviewNote}</span>
                      <span style={{ color: palette.mute, fontSize: 12 }}>
                        {it.reviewedByName}
                        {it.reviewedAt
                          ? ` · ${dayjs(it.reviewedAt).format('MM-DD HH:mm')}`
                          : ''}
                      </span>
                    </div>
                  )}
                  {it.reviewStatus === 1 && (
                    <div
                      style={{
                        color: palette.mute,
                        fontSize: 12,
                        marginTop: 6,
                      }}
                    >
                      已提交，等待采购负责人审核；审核前业务员看不到。仍可修改，修改后重新提交。
                    </div>
                  )}
                  {it.reviewStatus === 2 && (
                    <div
                      style={{
                        color: palette.mute,
                        fontSize: 12,
                        marginTop: 6,
                      }}
                    >
                      {it.reviewedByName ? `${it.reviewedByName} ` : ''}
                      审核通过，业务员已能看到。修改后重新进入审核，审核通过前业务员仍看到当前价格。
                    </div>
                  )}
                  {it.description && (
                    <div
                      style={{
                        color: palette.mute,
                        fontSize: 12,
                        marginTop: 6,
                      }}
                    >
                      {it.description}
                    </div>
                  )}
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 10,
                      padding: '10px 12px',
                      borderRadius: 10,
                      background: palette.inset,
                      margin: '12px 0',
                    }}
                  >
                    <span style={{ color: palette.sub, fontSize: 13, flex: 1 }}>
                      {it.inquiryScript}
                    </span>
                    <Button
                      size="small"
                      icon={<CopyOutlined />}
                      onClick={() => copy(it.inquiryScript)}
                    >
                      复制话术
                    </Button>
                  </div>
                  <div
                    style={{
                      display: 'flex',
                      gap: 8,
                      flexWrap: 'wrap',
                      alignItems: 'center',
                      marginBottom: 12,
                    }}
                  >
                    <span style={{ color: palette.mute, fontSize: 12 }}>
                      货源直达
                    </span>
                    {it.searchKeywords.slice(0, 3).flatMap((kw, i) =>
                      searchLinks(kw).map((l) => (
                        <a
                          key={`${kw}-${l.platform}`}
                          href={l.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 4,
                            padding: '2px 10px',
                            borderRadius: 12,
                            background: palette.accentSoft,
                            fontSize: 12,
                          }}
                        >
                          <ExportOutlined /> {l.platform} ·{' '}
                          {i === 0 ? '精确型号' : kw}
                        </a>
                      )),
                    )}
                  </div>

                  {submitted.length > 0 &&
                    (!itemEditable || !s.submittedBase) && (
                      <div
                        style={{ display: 'grid', gap: 6, marginBottom: 12 }}
                      >
                        <span style={{ color: palette.mute, fontSize: 12 }}>
                          已提交
                        </span>
                        {submitted.map((q) => (
                          <div
                            key={q.id}
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: 12,
                              padding: '6px 12px',
                              borderRadius: 10,
                              background: palette.inset,
                              fontSize: 13,
                            }}
                          >
                            {q.noStock ? (
                              <span style={{ color: palette.mute }}>
                                无货{q.note ? ` · ${q.note}` : ''}
                              </span>
                            ) : (
                              <>
                                <b style={{ color: palette.ink, width: 90 }}>
                                  {formatAmount(q.unitPrice)}
                                </b>
                                <TaxHint
                                  taxIncluded={q.taxIncluded}
                                  taxRate={q.taxRate}
                                  unitPrice={q.unitPrice}
                                />
                                <ConditionPill value={q.itemCondition} />
                                <span>
                                  {channelLabel(q.channel)} ·{' '}
                                  {q.shopName || '—'}
                                </span>
                                <span style={{ color: palette.sub }}>
                                  <LeadTimeText
                                    value={q.leadTime}
                                    fallback=""
                                  />
                                </span>
                                {q.recommended && (
                                  <Pill tone="green">
                                    {review ? '负责人选为推荐' : '推荐'}
                                  </Pill>
                                )}
                              </>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  {voided.length > 0 && (
                    <div style={{ display: 'grid', gap: 4, marginBottom: 12 }}>
                      {voided.map((q) => (
                        <span
                          key={q.id}
                          style={{ color: palette.mute, fontSize: 12 }}
                        >
                          <Pill tone="red">已作废</Pill>{' '}
                          {formatAmount(q.unitPrice)} {channelLabel(q.channel)}{' '}
                          · {q.shopName || '—'}
                          {q.reviewNote ? `：${q.reviewNote}` : ''}
                        </span>
                      ))}
                    </div>
                  )}

                  {!itemEditable ? null : s.noStock ? (
                    <Input
                      placeholder="无货说明，如：1688、淘宝、闲鱼都没有现货，代理商要 8 周"
                      value={s.noStockNote}
                      maxLength={300}
                      onChange={(e) =>
                        patchItem(it.id, { noStockNote: e.target.value })
                      }
                    />
                  ) : (
                    <div
                      style={{
                        display: 'grid',
                        gap: 8,
                        overflowX: 'auto',
                        // 列宽固定，窄屏时在卡片内横向滚动，不撑开整页
                        gridTemplateColumns: 'minmax(900px, 1fr)',
                      }}
                    >
                      {s.drafts.length > 0 && (
                        <div
                          style={{
                            display: 'grid',
                            gridTemplateColumns: columns,
                            gap: 8,
                            color: palette.mute,
                            fontSize: 12,
                          }}
                        >
                          {!review && (
                            <Tooltip title="报给业务员的价格：每个型号标一条；不标时按全新原装里最低价自动推荐">
                              <span>推荐</span>
                            </Tooltip>
                          )}
                          <span>渠道</span>
                          <span>店铺 / 供应商</span>
                          <Tooltip title="默认不含税；店家只给含税价时勾「含税」并选税率，系统按不含税价比价">
                            <span>单价（CNY）</span>
                          </Tooltip>
                          <span>货况</span>
                          <span>货期</span>
                          <span>备注</span>
                          <span />
                        </div>
                      )}
                      {s.drafts.map((d) => {
                        const err =
                          d.unitPrice != null || d.shopName
                            ? entryError(d)
                            : '';
                        return (
                          <div
                            key={d.key}
                            style={{
                              display: 'grid',
                              gridTemplateColumns: columns,
                              gap: 8,
                              alignItems: 'start',
                            }}
                          >
                            {!review && (
                              <Radio
                                aria-label="设为推荐报价"
                                checked={!!d.recommended}
                                style={{ marginTop: 4 }}
                                onClick={() =>
                                  patchItem(it.id, {
                                    drafts: s.drafts.map((x) => ({
                                      ...x,
                                      recommended:
                                        x.key === d.key
                                          ? !d.recommended
                                          : false,
                                    })),
                                  })
                                }
                              />
                            )}
                            <Select
                              size="small"
                              value={d.channel}
                              options={CHANNEL_OPTIONS}
                              onChange={(v) =>
                                patchEntry(it.id, d.key, {
                                  channel: v,
                                  // 换渠道时清掉供应商，避免把供应商名当店铺名带走
                                  supplierId: undefined,
                                  shopName:
                                    v === CHANNEL_SUPPLIER ||
                                    d.channel === CHANNEL_SUPPLIER
                                      ? ''
                                      : d.shopName,
                                })
                              }
                            />
                            {d.channel === CHANNEL_SUPPLIER ? (
                              <SupplierSelect
                                value={d.supplierId}
                                label={d.shopName}
                                status={
                                  (d.unitPrice != null || d.shopName) &&
                                  !d.supplierId
                                    ? 'error'
                                    : undefined
                                }
                                onChange={(supplierId, name) =>
                                  patchEntry(it.id, d.key, {
                                    supplierId,
                                    shopName: name,
                                  })
                                }
                              />
                            ) : (
                              <Input
                                size="small"
                                placeholder="店铺名称"
                                value={d.shopName}
                                maxLength={128}
                                onChange={(e) =>
                                  patchEntry(it.id, d.key, {
                                    shopName: e.target.value,
                                  })
                                }
                              />
                            )}
                            <span style={{ display: 'grid', gap: 2 }}>
                              <InputNumber
                                size="small"
                                precision={2}
                                placeholder="如 18.50"
                                value={d.unitPrice}
                                status={err ? 'error' : undefined}
                                style={{ width: '100%' }}
                                onChange={(v) =>
                                  patchEntry(it.id, d.key, { unitPrice: v })
                                }
                              />
                              <span
                                style={{
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: 6,
                                  fontSize: 12,
                                }}
                              >
                                <Checkbox
                                  checked={!!d.taxIncluded}
                                  onChange={(e) =>
                                    patchEntry(it.id, d.key, {
                                      taxIncluded: e.target.checked,
                                      taxRate: e.target.checked
                                        ? (d.taxRate ?? DEFAULT_TAX_RATE)
                                        : undefined,
                                    })
                                  }
                                >
                                  <span
                                    style={{
                                      fontSize: 12,
                                      whiteSpace: 'nowrap',
                                    }}
                                  >
                                    含税
                                  </span>
                                </Checkbox>
                                {d.taxIncluded && (
                                  <Select
                                    size="small"
                                    aria-label="税率"
                                    style={{ width: 70 }}
                                    value={d.taxRate ?? DEFAULT_TAX_RATE}
                                    options={taxRateOptions}
                                    onChange={(v) =>
                                      patchEntry(it.id, d.key, { taxRate: v })
                                    }
                                  />
                                )}
                              </span>
                              {d.taxIncluded && d.unitPrice != null && (
                                <span
                                  style={{ color: palette.mute, fontSize: 12 }}
                                >
                                  不含税{' '}
                                  {formatAmount(
                                    excludeTax(
                                      d.unitPrice,
                                      d.taxRate ?? DEFAULT_TAX_RATE,
                                    ),
                                  )}
                                  ，按此价比价
                                </span>
                              )}
                              {err && (
                                <span
                                  style={{ color: palette.red, fontSize: 11 }}
                                >
                                  {err}
                                </span>
                              )}
                            </span>
                            <Select
                              size="small"
                              value={d.itemCondition}
                              options={conditionOptions}
                              onChange={(v) =>
                                patchEntry(it.id, d.key, { itemCondition: v })
                              }
                            />
                            <Select
                              size="small"
                              allowClear
                              placeholder="选择货期"
                              value={d.leadTime || undefined}
                              options={leadTimeOptions}
                              onChange={(v) =>
                                patchEntry(it.id, d.key, { leadTime: v ?? 0 })
                              }
                            />
                            <Input
                              size="small"
                              placeholder="可选"
                              value={d.note}
                              maxLength={300}
                              onChange={(e) =>
                                patchEntry(it.id, d.key, {
                                  note: e.target.value,
                                })
                              }
                            />
                            <Button
                              size="small"
                              type="text"
                              aria-label="删除这条报价"
                              icon={<DeleteOutlined />}
                              onClick={() =>
                                patchItem(it.id, {
                                  drafts: s.drafts.filter(
                                    (x) => x.key !== d.key,
                                  ),
                                })
                              }
                            />
                          </div>
                        );
                      })}
                      <a
                        onClick={() =>
                          patchItem(it.id, {
                            drafts: [...s.drafts, newEntry()],
                          })
                        }
                      >
                        <PlusOutlined /> 再加一条报价
                      </a>
                      {!review &&
                        s.drafts.length > 1 &&
                        !s.drafts.some((x) => x.recommended) && (
                          <span style={{ color: palette.mute, fontSize: 12 }}>
                            没有标推荐时，按全新原装里最低价自动推荐
                          </span>
                        )}
                    </div>
                  )}
                </Card>
              );
            })}
          </div>
        )}
      </div>

      <Modal
        open={returnOpen}
        title="退回任务"
        okText="退回"
        cancelText="取消"
        confirmLoading={busy}
        onCancel={() => setReturnOpen(false)}
        onOk={() => {
          if (!detail) return;
          modal.confirm({
            title: '确认退回？',
            content:
              '退回后任务回到待分配池，你将看不到它；已提交的价格会保留。',
            okText: '退回',
            cancelText: '再想想',
            onOk: async () => {
              setBusy(true);
              try {
                await myTaskApi.returnTask(
                  detail.task.id,
                  returnReason,
                  returnNote,
                );
                message.success(
                  returnReason === 1
                    ? '已退回，并提醒业务员核实型号'
                    : '已退回',
                );
                setReturnOpen(false);
                setActiveId(undefined);
                await loadList();
              } catch (e) {
                message.error(readBizError(e).message);
              } finally {
                setBusy(false);
              }
            },
          });
        }}
      >
        <Radio.Group
          value={returnReason}
          onChange={(e) => setReturnReason(e.target.value)}
          options={RETURN_REASONS}
          optionType="button"
        />
        <Input.TextArea
          style={{ marginTop: 12 }}
          rows={3}
          maxLength={300}
          placeholder="说明一下原因，如：查不到这个型号，像是少写了后缀"
          value={returnNote}
          onChange={(e) => setReturnNote(e.target.value)}
        />
        <div style={{ color: palette.mute, fontSize: 12, marginTop: 8 }}>
          选「型号存疑」会提醒业务员核实。
        </div>
      </Modal>

      <ImportModal
        open={importOpen}
        preview={myTaskApi.importPreview}
        confirm={myTaskApi.importConfirm}
        onClose={() => setImportOpen(false)}
        onDone={() => {
          setImportOpen(false);
          loadList();
          loadDetail(activeId);
        }}
      />
    </div>
  );
};

export default MyTasksPage;
