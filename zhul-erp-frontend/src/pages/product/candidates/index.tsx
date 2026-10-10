import {
  CheckOutlined,
  CloseOutlined,
  MergeCellsOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  Drawer,
  Input,
  Modal,
  Radio,
  Segmented,
  Select,
  Skeleton,
  Table,
} from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { LIFECYCLE_OPTIONS } from '../constants';
import {
  type BrandOption,
  brandApi,
  type CategoryOption,
  categoryApi,
  type ProductOption,
  productApi,
  type SeriesOption,
  seriesApi,
} from '../service';
import {
  type ApproveBody,
  type Candidate,
  candidateApi,
  readBizError,
} from './service';

type Tab = 1 | 2 | 3 | 4;

/** 可信度：三个点，颜色越暖越可信 */
export const LevelDots: React.FC<{ level: number; name: string }> = ({
  level,
  name,
}) => {
  const { palette } = useAppTheme();
  const color =
    level >= 3 ? palette.green : level === 2 ? palette.link : palette.orange;
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
      <span style={{ display: 'inline-flex', gap: 3 }}>
        {[1, 2, 3].map((i) => (
          <span
            key={i}
            style={{
              width: 8,
              height: 8,
              borderRadius: 4,
              background: i <= level ? color : palette.hairline,
            }}
          />
        ))}
      </span>
      <span style={{ fontSize: 12, color }}>{name}</span>
    </span>
  );
};

const Heat: React.FC<{ c: Candidate }> = ({ c }) => {
  const { palette } = useAppTheme();
  return (
    <div>
      <b style={{ color: palette.ink }}>询盘 {c.inquiryCount} 次</b>
      <div
        style={{
          fontSize: 12,
          color: c.dealCount ? palette.green : palette.mute,
        }}
      >
        {c.dealCount ? `成交 ${c.dealCount} 次` : '未成交'}
      </div>
    </div>
  );
};

const SOURCE_TONE: Record<number, 'green' | 'accent' | 'violet' | 'gray'> = {
  1: 'gray',
  2: 'violet',
  3: 'accent',
  4: 'green',
};

/** 列表里的来源：类型 + 询盘号 / 订单号，最近 3 条 */
const SourceCell: React.FC<{ c: Candidate }> = ({ c }) => {
  const { palette } = useAppTheme();
  const list = c.sources ?? [];
  if (!list.length && !c.otherSourceCount) {
    return <span style={{ color: palette.mute }}>—</span>;
  }
  const more = (c.mineSourceCount ?? list.length) - list.length;
  return (
    <div style={{ display: 'grid', gap: 3, fontSize: 12 }}>
      {list.map((s, i) => (
        <span
          // biome-ignore lint/suspicious/noArrayIndexKey: 来源没有对外的 id
          key={i}
          style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
        >
          <Pill tone={SOURCE_TONE[s.sourceType] ?? 'gray'}>
            {s.sourceTypeName}
          </Pill>
          {s.soId ? (
            <Link
              to={`/sales/orders/${s.soId}`}
              onClick={(e) => e.stopPropagation()}
            >
              {s.soNo}
            </Link>
          ) : s.customerInquiryId ? (
            <Link
              to={`/inquiry/customer-inquiries/${s.customerInquiryId}`}
              onClick={(e) => e.stopPropagation()}
            >
              {s.inquiryCode}
            </Link>
          ) : null}
        </span>
      ))}
      {(more > 0 || !!c.otherSourceCount) && (
        <span style={{ color: palette.mute }}>
          {more > 0 ? `还有 ${more} 条` : ''}
          {more > 0 && c.otherSourceCount ? ' · ' : ''}
          {c.otherSourceCount ? `其他公司 ${c.otherSourceCount} 次` : ''}
        </span>
      )}
    </div>
  );
};

// ---------------------------------------------------------------- 审核抽屉

const Label: React.FC<{ children: React.ReactNode; required?: boolean }> = ({
  children,
  required,
}) => {
  const { palette } = useAppTheme();
  return (
    <div style={{ color: palette.sub, marginBottom: 6 }}>
      {children}
      {required && <span style={{ color: palette.red }}> *</span>}
    </div>
  );
};

const ReviewDrawer: React.FC<{
  id?: number;
  canReview: boolean;
  onClose: () => void;
  onDone: () => void;
}> = ({ id, canReview, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [c, setC] = useState<Candidate>();
  const [error, setError] = useState<string>();
  const [brands, setBrands] = useState<BrandOption[]>([]);
  const [categories, setCategories] = useState<CategoryOption[]>([]);
  const [series, setSeries] = useState<SeriesOption[]>([]);
  const [form, setForm] = useState<ApproveBody>({});
  const [seriesSearch, setSeriesSearch] = useState('');
  const [busy, setBusy] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [merging, setMerging] = useState(false);

  const load = useCallback(() => {
    if (id === undefined) return;
    setC(undefined);
    setError(undefined);
    Promise.all([
      candidateApi.detail(id),
      brandApi.options(),
      categoryApi.options(1),
    ])
      .then(([d, b, cats]) => {
        setC(d);
        setBrands(b);
        setCategories(cats.filter((x) => !x.parentId));
        setForm({
          brandMode: d.brandId ? 'EXISTING' : 'NEW',
          brandId: d.brandId ?? undefined,
          brandName: d.brandId ? undefined : d.brandText,
          categoryId: d.categoryId ?? undefined,
          mpnRaw: d.mpnRaw,
          mpnDisplay: d.mpnRaw,
          // 商品名称、简短描述默认用中文描述（系统内中文）
          productName: d.description || d.productName || '',
          shortDescription: d.description || d.descriptionEn || '',
          lifecycleStatus: d.suggestedLifecycle ?? 6,
          lifecycleSource: d.suggestedLifecycleSource ?? '',
        });
      })
      .catch((e) => setError(readBizError(e).message));
  }, [id]);
  useEffect(load, [load]);

  const brandId = form.brandMode === 'NEW' ? undefined : form.brandId;
  useEffect(() => {
    if (!brandId) {
      setSeries([]);
      return;
    }
    seriesApi
      .options(brandId)
      .then(setSeries)
      .catch(() => setSeries([]));
  }, [brandId]);

  const pending = c?.status === 1;
  const editable = pending && canReview;
  const patch = (p: Partial<ApproveBody>) => setForm((f) => ({ ...f, ...p }));
  const finalProblem = !c
    ? '加载中'
    : form.brandMode === 'NEW' && !form.brandName?.trim()
      ? '请填写品牌名称'
      : form.brandMode !== 'NEW' && !form.brandId
        ? '请选择品牌'
        : !form.categoryId
          ? '请选择品类'
          : !form.lifecycleStatus
            ? '请选择生命周期'
            : (form.lifecycleStatus === 4 || form.lifecycleStatus === 5) &&
                !form.lifecycleSource?.trim()
              ? '已停产、停产无替代要写依据'
              : undefined;

  const run = async (fn: () => Promise<unknown>, ok: string) => {
    setBusy(true);
    try {
      await fn();
      message.success(ok);
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const seriesOptions = [
    ...series.map((s) => ({ value: s.id, label: s.seriesName })),
    ...(seriesSearch.trim() &&
    !series.some(
      (s) => s.seriesName.toLowerCase() === seriesSearch.trim().toLowerCase(),
    )
      ? [{ value: -1, label: `新建系列「${seriesSearch.trim()}」` }]
      : []),
  ];

  return (
    <Drawer
      open={id !== undefined}
      onClose={onClose}
      size="min(880px, 96vw)"
      destroyOnHidden
      title={
        c ? (
          <span
            style={{ display: 'inline-flex', alignItems: 'center', gap: 10 }}
          >
            {pending ? '审核候选' : '候选'} · {c.brandName || c.brandText} ·{' '}
            {c.mpnRaw}
            <LevelDots level={c.level} name={c.levelName} />
          </span>
        ) : (
          '候选'
        )
      }
      footer={
        c?.status === 4 && canReview ? (
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button
              loading={busy}
              onClick={() =>
                run(() => candidateApi.reopen(c.id), '已重新打开，回到待审核')
              }
            >
              重新打开
            </Button>
          </div>
        ) : (
          editable && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Button
                icon={<CloseOutlined />}
                onClick={() => setRejecting(true)}
              >
                驳回
              </Button>
              <Button
                icon={<MergeCellsOutlined />}
                onClick={() => setMerging(true)}
              >
                并入已有商品
              </Button>
              <span
                style={{
                  flex: 1,
                  fontSize: 12,
                  color: palette.orange,
                  textAlign: 'right',
                }}
              >
                {finalProblem && c ? finalProblem : ''}
              </span>
              <Button onClick={onClose}>取消</Button>
              <Button
                type="primary"
                icon={<CheckOutlined />}
                loading={busy}
                disabled={!!finalProblem}
                onClick={() =>
                  c &&
                  run(
                    () =>
                      candidateApi.approve(c.id, {
                        ...form,
                        seriesId:
                          form.seriesId && form.seriesId > 0
                            ? form.seriesId
                            : undefined,
                      }),
                    '已建档',
                  )
                }
              >
                通过建档
              </Button>
            </div>
          )
        )
      }
    >
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : !c ? (
        <Skeleton active />
      ) : (
        <div style={{ display: 'grid', gap: 16 }}>
          {!pending && (
            <div
              style={{
                padding: '10px 14px',
                borderRadius: 10,
                background: palette.inset,
                color: palette.sub,
              }}
            >
              {c.statusName}
              {c.productId && (
                <>
                  {' · 商品 '}
                  <Link to={`/product/products/${c.productId}`}>
                    {c.productLabel}
                  </Link>
                </>
              )}
              {c.rejectReasonName &&
                ` · ${c.rejectReasonName}${c.rejectNote ? `：${c.rejectNote}` : ''}`}
              {c.reviewedByName &&
                ` · ${c.reviewedByName} 于 ${formatDateTime(c.reviewedAt)}`}
            </div>
          )}
          {editable && (
            <>
              <div>
                <Label required>品牌</Label>
                {c.brandId ? (
                  <Select
                    value={form.brandId}
                    showSearch={{ optionFilterProp: 'label' }}
                    options={brands.map((b) => ({
                      value: b.id,
                      label: b.brandName,
                    }))}
                    onChange={(v) =>
                      patch({
                        brandMode: 'EXISTING',
                        brandId: v,
                        seriesId: undefined,
                      })
                    }
                    style={{ width: '100%' }}
                    aria-label="品牌"
                  />
                ) : (
                  <div
                    style={{
                      padding: 14,
                      borderRadius: 10,
                      border: `1px solid ${palette.hairline}`,
                      display: 'grid',
                      gap: 10,
                    }}
                  >
                    <div style={{ color: palette.sub }}>
                      <Pill tone="orange">
                        待确认品牌：{c.brandText || '（空）'}
                      </Pill>{' '}
                      品牌库里没有，也不是任何品牌的别名
                    </div>
                    <Radio.Group
                      value={form.brandMode}
                      onChange={(e) =>
                        patch({
                          brandMode: e.target.value,
                          brandId: undefined,
                          brandName:
                            e.target.value === 'NEW' ? c.brandText : undefined,
                          seriesId: undefined,
                        })
                      }
                      options={[
                        { value: 'ALIAS', label: '设为已有品牌的别名' },
                        { value: 'NEW', label: '新建品牌' },
                      ]}
                    />
                    {form.brandMode === 'ALIAS' ? (
                      <Select
                        value={form.brandId}
                        showSearch={{ optionFilterProp: 'label' }}
                        placeholder="选择品牌"
                        options={brands.map((b) => ({
                          value: b.id,
                          label: b.brandName,
                        }))}
                        onChange={(v) => patch({ brandId: v })}
                        aria-label="别名所属品牌"
                      />
                    ) : (
                      <>
                        <Input
                          value={form.brandName}
                          maxLength={64}
                          onChange={(e) => patch({ brandName: e.target.value })}
                          aria-label="新品牌名称"
                        />
                        <span style={{ fontSize: 12, color: palette.mute }}>
                          名称改了的话，原文「{c.brandText}」自动成为别名
                        </span>
                      </>
                    )}
                  </div>
                )}
              </div>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr',
                  gap: 12,
                }}
              >
                <div>
                  <Label required>原始型号</Label>
                  <Input
                    value={form.mpnRaw}
                    maxLength={128}
                    onChange={(e) => patch({ mpnRaw: e.target.value })}
                    aria-label="原始型号"
                  />
                </div>
                <div>
                  <Label>展示型号</Label>
                  <Input
                    value={form.mpnDisplay}
                    maxLength={128}
                    onChange={(e) => patch({ mpnDisplay: e.target.value })}
                    aria-label="展示型号"
                  />
                </div>
              </div>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr',
                  gap: 12,
                }}
              >
                <div>
                  <Label required>品类</Label>
                  <Select
                    value={form.categoryId}
                    showSearch={{ optionFilterProp: 'label' }}
                    placeholder="只能从品类里选"
                    options={categories.map((x) => ({
                      value: x.id,
                      label: x.categoryNameZh
                        ? `${x.categoryNameZh}（${x.categoryName}）`
                        : x.categoryName,
                    }))}
                    onChange={(v) => patch({ categoryId: v })}
                    style={{ width: '100%' }}
                    aria-label="品类"
                  />
                  <div
                    style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}
                  >
                    {c.categoryText
                      ? `询盘里写的是「${c.categoryText}」`
                      : '询盘没有写品类'}
                  </div>
                </div>
                <div>
                  <Label>系列</Label>
                  <Select
                    value={
                      form.newSeriesName ? -1 : (form.seriesId ?? undefined)
                    }
                    allowClear
                    showSearch={{
                      optionFilterProp: 'label',
                      onSearch: setSeriesSearch,
                    }}
                    placeholder={
                      brandId ? '可选；输入名称可新建' : '新品牌建好后再选系列'
                    }
                    disabled={!brandId && form.brandMode !== 'NEW'}
                    options={seriesOptions}
                    onChange={(v?: number) =>
                      v === -1
                        ? patch({
                            seriesId: undefined,
                            newSeriesName: seriesSearch.trim(),
                          })
                        : patch({ seriesId: v, newSeriesName: undefined })
                    }
                    style={{ width: '100%' }}
                    aria-label="系列"
                  />
                  {form.newSeriesName && (
                    <div
                      style={{
                        fontSize: 12,
                        color: palette.link,
                        marginTop: 4,
                      }}
                    >
                      通过时新建系列「{form.newSeriesName}」
                    </div>
                  )}
                </div>
              </div>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr',
                  gap: 12,
                }}
              >
                <div>
                  <Label required>生命周期</Label>
                  <Select
                    value={form.lifecycleStatus}
                    options={LIFECYCLE_OPTIONS}
                    onChange={(v) => patch({ lifecycleStatus: v })}
                    style={{ width: '100%' }}
                    aria-label="生命周期"
                  />
                  <div
                    style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}
                  >
                    按采购询价时核实的生产状态自动填写
                  </div>
                </div>
                {(form.lifecycleStatus === 4 || form.lifecycleStatus === 5) && (
                  <div>
                    <Label required>生命周期依据</Label>
                    <Input
                      value={form.lifecycleSource}
                      maxLength={255}
                      onChange={(e) =>
                        patch({ lifecycleSource: e.target.value })
                      }
                      aria-label="生命周期依据"
                    />
                  </div>
                )}
              </div>
              <div>
                <Label>商品名称</Label>
                <Input
                  value={form.productName}
                  maxLength={255}
                  onChange={(e) => patch({ productName: e.target.value })}
                  aria-label="商品名称"
                />
              </div>
              <div>
                <Label>简短描述</Label>
                <Input.TextArea
                  value={form.shortDescription}
                  rows={2}
                  maxLength={500}
                  onChange={(e) => patch({ shortDescription: e.target.value })}
                  aria-label="简短描述"
                />
                <div
                  style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}
                >
                  取询盘里 AI 生成的中文描述，可改
                </div>
              </div>
            </>
          )}
          <div>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                marginBottom: 8,
              }}
            >
              <b style={{ color: palette.ink }}>来源</b>
              <span style={{ fontSize: 12, color: palette.mute }}>
                {c.sources?.length ?? 0} 条
                {c.otherSourceCount
                  ? ` · 其他公司 ${c.otherSourceCount} 次`
                  : ''}
              </span>
              <span style={{ flex: 1 }} />
              <Heat c={c} />
            </div>
            <div style={{ display: 'grid', gap: 6 }}>
              {(c.sources ?? []).map((s, i) => (
                <div
                  // biome-ignore lint/suspicious/noArrayIndexKey: 来源没有对外的 id
                  key={i}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    padding: '8px 12px',
                    borderRadius: 8,
                    background: palette.inset,
                    fontSize: 13,
                  }}
                >
                  <Pill
                    tone={
                      s.sourceType === 4
                        ? 'green'
                        : s.sourceType === 3
                          ? 'accent'
                          : s.sourceType === 2
                            ? 'violet'
                            : 'gray'
                    }
                  >
                    {s.sourceTypeName}
                  </Pill>
                  {s.customerInquiryId && (
                    <Link
                      to={`/inquiry/customer-inquiries/${s.customerInquiryId}`}
                    >
                      {s.inquiryCode}
                    </Link>
                  )}
                  {s.originalModel && s.originalModel !== c.mpnRaw && (
                    <span style={{ color: palette.sub }}>
                      「{s.originalModel}」
                    </span>
                  )}
                  {s.soId && (
                    <Link to={`/sales/orders/${s.soId}`}>{s.soNo}</Link>
                  )}
                  <span style={{ flex: 1 }} />
                  <span style={{ fontSize: 12, color: palette.mute }}>
                    {formatDateTime(s.createTime)}
                  </span>
                </div>
              ))}
            </div>
          </div>
          {editable && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              通过后在商品库新建这个商品，所有来源的询盘型号自动关联；商品库已有相同型号时改为并入。
            </div>
          )}
        </div>
      )}
      {c && (
        <RejectModal
          open={rejecting}
          label={c.mpnRaw}
          onClose={() => setRejecting(false)}
          onSubmit={(reason, note) => {
            setRejecting(false);
            run(() => candidateApi.reject(c.id, reason, note), '已驳回');
          }}
        />
      )}
      {c && (
        <MergeModal
          open={merging}
          candidate={c}
          onClose={() => setMerging(false)}
          onSubmit={(productId) => {
            setMerging(false);
            run(() => candidateApi.merge(c.id, productId), '已并入');
          }}
        />
      )}
    </Drawer>
  );
};

const RejectModal: React.FC<{
  open: boolean;
  label: string;
  onClose: () => void;
  onSubmit: (reason: number, note?: string) => void;
}> = ({ open, label, onClose, onSubmit }) => {
  const { palette } = useAppTheme();
  const [reason, setReason] = useState(1);
  const [note, setNote] = useState('');
  useEffect(() => {
    if (open) {
      setReason(1);
      setNote('');
    }
  }, [open]);
  return (
    <Modal
      open={open}
      title={`驳回候选 · ${label}`}
      okText="驳回"
      okButtonProps={{ danger: true, disabled: reason === 3 && !note.trim() }}
      onCancel={onClose}
      onOk={() => onSubmit(reason, note.trim() || undefined)}
      destroyOnHidden
    >
      <div style={{ color: palette.sub, marginBottom: 12 }}>
        驳回后这个写法以后再出现，只记录来源，不再进入待审核；可以在「已驳回」里重新打开。
      </div>
      <Radio.Group
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        options={[
          { value: 1, label: '不是型号（如描述、参数写法）' },
          { value: 2, label: '型号错误' },
          { value: 3, label: '其他（须说明）' },
        ]}
        style={{ display: 'grid', gap: 8 }}
      />
      {reason === 3 && (
        <Input.TextArea
          value={note}
          rows={2}
          maxLength={200}
          onChange={(e) => setNote(e.target.value)}
          style={{ marginTop: 12 }}
          aria-label="驳回说明"
        />
      )}
    </Modal>
  );
};

const MergeModal: React.FC<{
  open: boolean;
  candidate: Candidate;
  onClose: () => void;
  onSubmit: (productId: number) => void;
}> = ({ open, candidate, onClose, onSubmit }) => {
  const { palette } = useAppTheme();
  const [options, setOptions] = useState<ProductOption[]>([]);
  const [value, setValue] = useState<number>();
  const search = useCallback(
    (kw: string) => {
      productApi
        .search(kw || candidate.mpnRaw, undefined, 20)
        .then(setOptions)
        .catch(() => setOptions([]));
    },
    [candidate.mpnRaw],
  );
  useEffect(() => {
    if (open) {
      setValue(undefined);
      search('');
    }
  }, [open, search]);
  return (
    <Modal
      open={open}
      title={`并入已有商品 · ${candidate.mpnRaw}`}
      okText="并入"
      okButtonProps={{ disabled: !value }}
      onCancel={onClose}
      onOk={() => value && onSubmit(value)}
      destroyOnHidden
    >
      <div style={{ color: palette.sub, marginBottom: 12 }}>
        适合写法不同的同一型号：并入后所有来源的询盘型号关联到所选商品。
      </div>
      <Select
        value={value}
        showSearch={{ filterOption: false, onSearch: search }}
        onChange={setValue}
        placeholder="搜索型号或名称"
        options={options.map((p) => ({
          value: p.id,
          label: `${p.brandName} · ${p.mpnDisplay}${p.productName ? ` · ${p.productName}` : ''}`,
        }))}
        style={{ width: '100%' }}
        aria-label="商品"
      />
    </Modal>
  );
};

// ---------------------------------------------------------------- 列表

const Candidates: React.FC = () => {
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const access = useAccess();
  const canReview = !!access['product:candidate:review'];
  const [tab, setTab] = useState<Tab>(1);
  const [pendingCount, setPendingCount] = useState<number>();
  const [keyword, setKeyword] = useState('');
  const [level, setLevel] = useState<number>();
  const [query, setQuery] = useState<{ keyword?: string; level?: number }>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<Candidate[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [selected, setSelected] = useState<Candidate[]>([]);
  const [viewing, setViewing] = useState<number>();
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const res = await candidateApi.page({
        page,
        pageSize,
        status: tab,
        ...query,
      });
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, tab, query]);

  const refreshCount = useCallback(() => {
    candidateApi
      .counts()
      .then((c) => setPendingCount(c.pending))
      .catch(() => setPendingCount(undefined));
  }, []);

  useEffect(() => {
    load();
  }, [load]);
  useEffect(refreshCount, [refreshCount]);

  const changed = () => {
    setViewing(undefined);
    setSelected([]);
    load();
    refreshCount();
  };

  const batch = async () => {
    setBusy(true);
    try {
      const r = await candidateApi.batchApprove(selected.map((c) => c.id));
      if (r.skipped.length) {
        modal.info({
          title: `已建档 ${r.approved} 个，跳过 ${r.skipped.length} 个`,
          content: (
            <div style={{ display: 'grid', gap: 4 }}>
              {r.skipped.map((s) => (
                <div key={s.id}>
                  {s.label || s.id}：{s.reason}
                </div>
              ))}
            </div>
          ),
        });
      } else {
        message.success(`已建档 ${r.approved} 个`);
      }
      changed();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const brandCell = (c: Candidate) =>
    c.brandId ? (
      <div>
        <b style={{ color: palette.ink }}>
          {c.brandName} · {c.mpnRaw}
        </b>
        <div style={{ fontSize: 12, color: palette.mute }}>
          {c.originalModel ? `回填自「${c.originalModel}」` : '品牌 ✓'}
        </div>
      </div>
    ) : (
      <div>
        <b style={{ color: palette.orange }}>
          待确认品牌：{c.brandText || '（空）'} · {c.mpnRaw}
        </b>
        <div style={{ fontSize: 12, color: palette.mute }}>品牌未识别</div>
      </div>
    );

  const columns: TableColumnsType<Candidate> = [
    {
      title: '品牌 · 型号',
      key: 'model',
      width: 280,
      fixed: 'left',
      render: (_, c) => brandCell(c),
    },
    {
      title: tab === 1 ? '品类（建议）' : '品类',
      key: 'category',
      width: 130,
      render: (_, c) =>
        c.categoryName ?? <span style={{ color: palette.mute }}>— 请选择</span>,
    },
    {
      title: '名称',
      dataIndex: 'productName',
      width: 240,
      render: (v?: string) => (
        <span style={{ fontSize: 12, color: palette.sub }}>{v || '—'}</span>
      ),
    },
    {
      title: '可信度',
      key: 'level',
      width: 140,
      render: (_, c) => <LevelDots level={c.level} name={c.levelName} />,
    },
    {
      title: '来源',
      key: 'sources',
      width: 230,
      render: (_, c) => <SourceCell c={c} />,
    },
    {
      title: '热度',
      key: 'heat',
      width: 100,
      render: (_, c) => <Heat c={c} />,
    },
    ...(tab === 1
      ? []
      : [
          {
            title: '结果',
            key: 'result',
            width: 200,
            render: (_: unknown, c: Candidate) => (
              <div>
                {c.productId ? (
                  <Link to={`/product/products/${c.productId}`}>
                    {c.productLabel}
                  </Link>
                ) : (
                  <span>
                    {c.rejectReasonName}
                    {c.rejectNote ? `：${c.rejectNote}` : ''}
                  </span>
                )}
                <div style={{ fontSize: 12, color: palette.mute }}>
                  {c.reviewedByName}
                </div>
              </div>
            ),
          },
        ]),
    ...auditColumns<Candidate>(),
    {
      title: '操作',
      key: 'actions',
      width: 90,
      fixed: 'right',
      render: (_, c) => (
        <a onClick={() => setViewing(c.id)}>
          {tab === 1 && canReview ? '审核' : '查看'}
        </a>
      ),
    },
  ];

  const apply = () => {
    setQuery({ keyword: keyword.trim() || undefined, level });
    setPage(1);
  };

  return (
    <div>
      <PageTitle
        crumbs={['商品候选']}
        title="商品候选"
        description="询盘、成交带进来的新型号先在这里审核，通过后才进入商品库；被问得多、成交过的优先审核。"
        actions={
          tab === 1 &&
          canReview && (
            <Button
              type="primary"
              icon={<CheckOutlined />}
              disabled={selected.length === 0}
              loading={busy}
              onClick={batch}
            >
              批量通过{selected.length ? ` · ${selected.length}` : ''}
            </Button>
          )
        }
      />
      <div
        style={{
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          flexWrap: 'wrap',
          marginBottom: 16,
        }}
      >
        <Segmented<Tab>
          value={tab}
          onChange={(t) => {
            setTab(t);
            setPage(1);
            setSelected([]);
          }}
          options={[
            {
              value: 1,
              label: pendingCount ? `待审核 · ${pendingCount}` : '待审核',
            },
            { value: 2, label: '已建档' },
            { value: 3, label: '已并入' },
            { value: 4, label: '已驳回' },
          ]}
        />
        <Input
          allowClear
          prefix={<SearchOutlined />}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onPressEnter={apply}
          placeholder="品牌、型号、名称"
          style={{ width: 260 }}
          aria-label="关键词"
        />
        <Select
          allowClear
          value={level}
          onChange={setLevel}
          placeholder="全部可信度"
          options={[
            { value: 3, label: '已成交' },
            { value: 2, label: '采购问到有货' },
            { value: 1, label: '询盘出现' },
          ]}
          style={{ width: 150 }}
          aria-label="可信度"
        />
        <span style={{ flex: 1 }} />
        <Button type="primary" onClick={apply}>
          查询
        </Button>
      </div>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Card style={{ padding: 0 }}>
          <Table<Candidate>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 1700 }}
            rowSelection={
              tab === 1 && canReview
                ? {
                    selectedRowKeys: selected.map((c) => c.id),
                    onChange: (_, rs) => setSelected(rs),
                  }
                : undefined
            }
            locale={{
              emptyText:
                tab === 1
                  ? '没有待审核的候选；确认询盘后，商品库里没有的型号会出现在这里'
                  : '没有记录',
            }}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
              showTotal: (t: number) => `共 ${t} 条`,
              onChange: (p: number, s: number) => {
                setPage(p);
                setPageSize(s);
              },
            }}
          />
        </Card>
      )}
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
        {tab === 1
          ? '按可信度从高到低、再按更新时间倒序；批量通过只处理品牌与品类都已确定的候选（生命周期按采购核实自动填写），其余跳过并提示'
          : '按可信度从高到低、再按更新时间倒序'}
      </div>
      <ReviewDrawer
        id={viewing}
        canReview={canReview}
        onClose={() => setViewing(undefined)}
        onDone={changed}
      />
    </div>
  );
};

export default Candidates;
