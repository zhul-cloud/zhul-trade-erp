import {
  CheckCircleFilled,
  CloseOutlined,
  MoonOutlined,
  SunOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { history, useModel, useSearchParams } from '@umijs/max';
import { App, Button, Form, Input, Modal, Select } from 'antd';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { CompletenessBar } from '../components/CompletenessBar';
import { BrandMark, LifecyclePill, Pill } from '../components/Pills';
import {
  COMPLETENESS_MODULES,
  LIFECYCLE_OPTIONS,
  normalizeMpn,
} from '../constants';
import {
  type BrandOption,
  brandApi,
  type Category,
  categoryApi,
  type MediaItem,
  mediaApi,
  type Product,
  type ProductMatch,
  type ProductOption,
  productApi,
  readBizError,
  type SeriesOption,
  seriesApi,
} from '../service';
import { ProductThemeProvider, useProductTheme } from '../theme';
import { LinkModal, UploadModal } from './cards/MediaCard';

const STEPS = ['型号', '分类', '图片', '补充资料', '完成'];
const DRAFT_TTL_MS = 7 * 24 * 3600 * 1000;
const COMMON_BRANDS = 7;

interface Draft {
  brandId?: number;
  brandName?: string;
  mpn: string;
  categoryId?: number;
  seriesId?: number;
  lifecycleStatus: number;
  lifecycleSource: string;
}

const emptyDraft: Draft = { mpn: '', lifecycleStatus: 6, lifecycleSource: '' };

/** 草稿键带账号标识，同一浏览器换账号不会看到别人的草稿 */
const draftKey = (user?: string) =>
  `zhul_product_wizard_draft:${user ?? 'anonymous'}`;

const readDraft = (key: string): Draft | undefined => {
  try {
    const raw = JSON.parse(localStorage.getItem(key) ?? 'null');
    if (!raw || Date.now() - raw.savedAt > DRAFT_TTL_MS) {
      localStorage.removeItem(key);
      return undefined;
    }
    return raw.draft as Draft;
  } catch {
    return undefined;
  }
};

type DupState =
  | { kind: 'none' }
  | { kind: 'exists'; product: ProductOption }
  | { kind: 'existsByCreate'; id: number; display: string }
  | { kind: 'deleted'; id: number; display: string };

const WizardInner: React.FC = () => {
  const { message } = App.useApp();
  const { palette, mode, toggle } = useProductTheme();
  const { initialState } = useModel('@@initialState');
  const [search] = useSearchParams();
  const key = draftKey(initialState?.currentUser?.userid);

  const [step, setStep] = useState(1);
  const [draft, setDraft] = useState<Draft>({
    ...emptyDraft,
    mpn: search.get('mpn') ?? '',
  });
  const [resume, setResume] = useState<Draft>();
  const [product, setProduct] = useState<Product>();
  const [brands, setBrands] = useState<BrandOption[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [seriesList, setSeriesList] = useState<SeriesOption[]>([]);
  const [match, setMatch] = useState<ProductMatch>();
  const [similar, setSimilar] = useState<ProductOption[]>([]);
  const [checking, setChecking] = useState(false);
  const [dup, setDup] = useState<DupState>({ kind: 'none' });
  const [creating, setCreating] = useState(false);
  const [media, setMedia] = useState<MediaItem[]>([]);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [linkOpen, setLinkOpen] = useState(false);
  const [detail, setDetail] = useState<Product>();
  const [form] = Form.useForm<{
    productName?: string;
    specSummary?: string;
    shortDescription?: string;
  }>();
  const [savingInfo, setSavingInfo] = useState(false);
  const seq = useRef(0);

  const created = !!product;
  const normalized = normalizeMpn(draft.mpn);

  // 打开时：有草稿就询问是否继续
  useEffect(() => {
    const saved = readDraft(key);
    if (saved?.mpn && !search.get('mpn')) setResume(saved);
  }, [key, search]);

  // 第 1、2 步的输入自动保存在当前浏览器
  useEffect(() => {
    if (created) return;
    if (!draft.mpn && !draft.brandId) return;
    try {
      localStorage.setItem(key, JSON.stringify({ savedAt: Date.now(), draft }));
    } catch {
      /* 存不了就只在本页有效 */
    }
  }, [draft, created, key]);

  useEffect(() => {
    brandApi
      .options()
      .then(setBrands)
      .catch(() => {});
    categoryApi
      .page({ page: 1, pageSize: 100, status: 1 })
      .then((r) => setCategories(r.records))
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!draft.brandId) {
      setSeriesList([]);
      return;
    }
    seriesApi
      .options(draft.brandId)
      .then(setSeriesList)
      .catch(() => setSeriesList([]));
  }, [draft.brandId]);

  // 去重：输入停止 300ms 后，用归一化型号查同品牌下是否已有商品
  useEffect(() => {
    setDup({ kind: 'none' });
    setMatch(undefined);
    setSimilar([]);
    if (!draft.brandName || !normalized) return;
    const mine = ++seq.current;
    setChecking(true);
    const t = setTimeout(async () => {
      try {
        const [m, near] = await Promise.all([
          productApi.match(draft.brandName as string, draft.mpn),
          draft.brandId
            ? productApi.search(normalized.slice(0, 6), draft.brandId, 4)
            : Promise.resolve([]),
        ]);
        if (mine !== seq.current) return;
        setMatch(m);
        if (m.exact) setDup({ kind: 'exists', product: m.exact });
        setSimilar(near.filter((n) => n.id !== m.exact?.id).slice(0, 3));
      } catch {
        /* 预检失败不阻塞：创建时服务端仍会校验 */
      } finally {
        if (mine === seq.current) setChecking(false);
      }
    }, 300);
    return () => clearTimeout(t);
  }, [draft.brandId, draft.brandName, draft.mpn, normalized]);

  const patch = (p: Partial<Draft>) => setDraft((d) => ({ ...d, ...p }));

  const pickBrand = (b: BrandOption) =>
    patch({ brandId: b.id, brandName: b.brandName, seriesId: undefined });

  const step1Ready = !!draft.brandId && !!normalized && dup.kind === 'none';
  const step2Ready = !!draft.categoryId;

  const loadMedia = useCallback(
    async (id: number) => setMedia(await mediaApi.list(id)),
    [],
  );

  const createProduct = async () => {
    setCreating(true);
    try {
      const p = await productApi.createQuiet({
        brandId: draft.brandId,
        categoryId: draft.categoryId,
        seriesId: draft.seriesId ?? null,
        mpnRaw: draft.mpn.trim(),
        lifecycleStatus: draft.lifecycleStatus,
        lifecycleSource: draft.lifecycleSource,
      });
      try {
        localStorage.removeItem(key);
      } catch {
        /* 忽略 */
      }
      setProduct(p);
      setStep(3);
    } catch (e) {
      const err = readBizError(e);
      if (err.errorCode === 'PRODUCT_DUPLICATE') {
        // 并发或已被删除 / 已停用的同型号：回到第 1 步，给出对应提示
        const d = err.detail as
          | { existingId?: number; mpnDisplay?: string; deleted?: boolean }
          | undefined;
        setStep(1);
        setDup(
          d?.deleted
            ? {
                kind: 'deleted',
                id: d.existingId as number,
                display: d.mpnDisplay ?? draft.mpn,
              }
            : {
                kind: 'existsByCreate',
                id: d?.existingId as number,
                display: d?.mpnDisplay ?? draft.mpn,
              },
        );
      } else {
        message.error(err.message);
      }
    } finally {
      setCreating(false);
    }
  };

  const restoreDeleted = async (id: number) => {
    const p = await productApi.restore(id);
    message.success(`已恢复「${p.mpnDisplay}」`);
    history.push(`/product/products/${id}`);
  };

  const goInfoStep = async () => {
    if (product) await loadMedia(product.id);
    setStep(4);
  };

  const finish = async () => {
    if (!product) return;
    const v = form.getFieldsValue();
    const any = v.productName || v.specSummary || v.shortDescription;
    setSavingInfo(true);
    try {
      if (any) {
        await productApi.update(product.id, {
          brandId: product.brandId,
          categoryId: product.categoryId,
          seriesId: product.seriesId ?? null,
          mpnRaw: product.mpnRaw,
          lifecycleStatus: product.lifecycleStatus,
          lifecycleSource: product.lifecycleSource,
          productName: v.productName ?? '',
          specSummary: v.specSummary ?? '',
          shortDescription: v.shortDescription ?? '',
        });
      }
      setDetail(await productApi.get(product.id));
      setStep(5);
    } finally {
      setSavingInfo(false);
    }
  };

  const close = () => {
    const hasDraft = !created && (draft.mpn || draft.brandId);
    if (hasDraft) {
      Modal.confirm({
        title: '草稿已保存，确定离开？',
        content: '下次进入新建商品时可以继续。',
        okText: '离开',
        cancelText: '继续填写',
        onOk: () => history.push('/product/products'),
      });
    } else {
      history.push('/product/products');
    }
  };

  const restart = () => {
    setProduct(undefined);
    setDetail(undefined);
    setMedia([]);
    setDraft(emptyDraft);
    setDup({ kind: 'none' });
    form.resetFields();
    setStep(1);
  };

  const nextDisabled =
    step === 1 ? !step1Ready : step === 2 ? !step2Ready || creating : false;
  const category = categories.find((c) => c.id === draft.categoryId);
  const previewSeries = seriesList.find((s) => s.id === draft.seriesId);
  const shownBrands = useMemo(() => {
    const top = brands.slice(0, COMMON_BRANDS);
    const chosen = brands.find((b) => b.id === draft.brandId);
    return chosen && !top.some((b) => b.id === chosen.id)
      ? [...top.slice(0, COMMON_BRANDS - 1), chosen]
      : top;
  }, [brands, draft.brandId]);

  const bigChoice = (active: boolean): React.CSSProperties => ({
    border: `2px solid ${active ? palette.link : palette.control}`,
    background: active ? palette.accentSoft : palette.card,
    borderRadius: 16,
    padding: 16,
    cursor: 'pointer',
    color: palette.ink,
    textAlign: 'left',
    display: 'flex',
    alignItems: 'center',
    gap: 12,
    fontSize: 16,
    fontWeight: 600,
  });

  const question = (text: string, hint?: string) => (
    <div style={{ marginBottom: 24 }}>
      <h2
        id="wizard-question"
        tabIndex={-1}
        style={{ fontSize: 32, fontWeight: 700, margin: 0 }}
      >
        {text}
      </h2>
      {hint && (
        <p style={{ margin: '8px 0 0', color: palette.sub, fontSize: 14 }}>
          {hint}
        </p>
      )}
    </div>
  );

  // 每次换步骤，把焦点移到这一步的问题上，键盘用户不会丢失位置
  useEffect(() => {
    document.getElementById('wizard-question')?.focus();
  }, [step]);

  const dupCard =
    dup.kind === 'exists' ? (
      <div
        role="alert"
        style={{
          marginTop: 16,
          padding: 16,
          borderRadius: 14,
          background: palette.redSoft,
          border: `1px solid ${palette.red}`,
        }}
      >
        <b style={{ color: palette.red }}>已存在</b>：{dup.product.mpnDisplay} ·{' '}
        {dup.product.brandName}
        <div style={{ marginTop: 8 }}>
          <Button
            onClick={() => history.push(`/product/products/${dup.product.id}`)}
          >
            查看这个商品
          </Button>
        </div>
      </div>
    ) : dup.kind === 'existsByCreate' ? (
      <div
        role="alert"
        style={{
          marginTop: 16,
          padding: 16,
          borderRadius: 14,
          background: palette.redSoft,
          border: `1px solid ${palette.red}`,
        }}
      >
        <b style={{ color: palette.red }}>该型号已存在</b>：{dup.display}
        （可能刚被别人创建，或商品已停用）
        <div style={{ marginTop: 8 }}>
          <Button onClick={() => history.push(`/product/products/${dup.id}`)}>
            查看这个商品
          </Button>
        </div>
      </div>
    ) : dup.kind === 'deleted' ? (
      <div
        role="alert"
        style={{
          marginTop: 16,
          padding: 16,
          borderRadius: 14,
          background: palette.orangeSoft,
          border: `1px solid ${palette.orange}`,
        }}
      >
        <b style={{ color: palette.orange }}>曾被删除</b>：{dup.display}{' '}
        已经存在过，不能重新新建，但可以恢复原来的商品（保留全部资料）。
        <div style={{ marginTop: 8 }}>
          <Button type="primary" onClick={() => restoreDeleted(dup.id)}>
            恢复这个商品
          </Button>
        </div>
      </div>
    ) : null;

  const page = (
    <div
      style={{
        minHeight: '100vh',
        background: `radial-gradient(1200px 600px at 20% -10%, #0F2A5C 0%, #0B1638 45%, ${palette.canvas} 100%)`,
        paddingBottom: 96,
      }}
    >
      {/* 顶栏：关闭、步骤条、草稿提示 */}
      <header
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 16,
          padding: '16px 32px',
          borderBottom: `1px solid ${palette.hairline}`,
          background: '#0A101D',
          flexWrap: 'wrap',
        }}
      >
        <Button
          type="text"
          aria-label="关闭并退出新建"
          icon={<CloseOutlined />}
          onClick={close}
          style={{ color: '#F8FAFC' }}
        />
        <ol
          aria-label="新建进度"
          style={{
            display: 'flex',
            gap: 8,
            listStyle: 'none',
            margin: 0,
            padding: 0,
            flex: 1,
            minWidth: 320,
            justifyContent: 'center',
          }}
        >
          {STEPS.map((label, i) => {
            const n = i + 1;
            const state = n < step ? 'done' : n === step ? 'current' : 'todo';
            return (
              <li
                key={label}
                aria-current={state === 'current' ? 'step' : undefined}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  color: state === 'todo' ? '#94A3B8' : '#F8FAFC',
                  fontSize: 14,
                  fontWeight: state === 'current' ? 700 : 500,
                }}
              >
                <span
                  className="num"
                  style={{
                    width: 28,
                    height: 28,
                    borderRadius: 14,
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: state === 'done' ? '#2563EB' : 'transparent',
                    border:
                      state === 'current'
                        ? '2px solid #60A5FA'
                        : state === 'done'
                          ? 'none'
                          : '1px solid #64748B',
                    fontSize: 13,
                  }}
                >
                  {state === 'done' ? '✓' : n}
                </span>
                {label}
                {n < STEPS.length && (
                  <span
                    aria-hidden="true"
                    style={{ width: 24, height: 1, background: '#334155' }}
                  />
                )}
              </li>
            );
          })}
        </ol>
        <span style={{ color: '#94A3B8', fontSize: 13 }} role="status">
          {created ? '商品已创建 · 改动自动保存' : '草稿已自动保存'}
        </span>
        <Button
          aria-label={mode === 'dark' ? '切换到浅色主题' : '切换到深色主题'}
          icon={mode === 'dark' ? <SunOutlined /> : <MoonOutlined />}
          onClick={toggle}
        />
      </header>

      <main
        id="product-main"
        style={{
          display: 'flex',
          gap: 32,
          padding: '40px 32px',
          maxWidth: 1280,
          margin: '0 auto',
          flexWrap: 'wrap',
        }}
      >
        <div style={{ flex: '1 1 560px', minWidth: 0 }}>
          {step === 1 && (
            <>
              {question(
                '这是哪个品牌的什么型号？',
                '只有前 2 步是必须的，其余都可以以后再补。',
              )}
              <div
                role="radiogroup"
                aria-label="品牌"
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
                  gap: 12,
                  marginBottom: 16,
                }}
              >
                {shownBrands.map((b) => (
                  // biome-ignore lint/a11y/useSemanticElements: 自绘的卡片式单选，按 ARIA radio 模式实现（role、aria-checked、组内单选）
                  <button
                    type="button"
                    role="radio"
                    aria-checked={draft.brandId === b.id}
                    key={b.id}
                    style={bigChoice(draft.brandId === b.id)}
                    onClick={() => pickBrand(b)}
                  >
                    <BrandMark name={b.brandName} size={36} />
                    {b.brandName}
                  </button>
                ))}
              </div>
              {brands.length > COMMON_BRANDS && (
                <Select
                  allowClear
                  showSearch={{ optionFilterProp: 'label' }}
                  placeholder="更多品牌：输入名称搜索"
                  aria-label="更多品牌"
                  style={{ width: 280, marginBottom: 24 }}
                  value={undefined}
                  options={brands.map((b) => ({
                    value: b.id,
                    label: b.brandName,
                  }))}
                  onChange={(id) => {
                    const b = brands.find((x) => x.id === id);
                    if (b) pickBrand(b);
                  }}
                />
              )}
              <label
                htmlFor="mpn-input"
                style={{
                  display: 'block',
                  fontSize: 14,
                  marginBottom: 8,
                  color: palette.sub,
                }}
              >
                型号（可以带空格和连字符，如 6ES7 214-1BD23-0XB0）
              </label>
              <Input
                id="mpn-input"
                size="large"
                value={draft.mpn}
                onChange={(e) => patch({ mpn: e.target.value })}
                maxLength={128}
                status={
                  dup.kind === 'exists' || dup.kind === 'existsByCreate'
                    ? 'error'
                    : dup.kind === 'deleted'
                      ? 'warning'
                      : undefined
                }
                style={{ height: 60, fontSize: 20, borderRadius: 14 }}
                aria-describedby="mpn-feedback"
              />
              <div
                id="mpn-feedback"
                style={{ minHeight: 28, marginTop: 8, fontSize: 14 }}
                aria-live="polite"
              >
                {!draft.brandId && draft.mpn ? (
                  <span style={{ color: palette.orange }}>先选择一个品牌</span>
                ) : checking ? (
                  <span style={{ color: palette.sub }}>
                    正在检查库里有没有…
                  </span>
                ) : draft.mpn && !normalized ? (
                  <span style={{ color: palette.red }}>
                    型号至少包含一个字母或数字
                  </span>
                ) : draft.brandId &&
                  normalized &&
                  dup.kind === 'none' &&
                  match ? (
                  <span style={{ color: palette.green }}>
                    ✓ 可以新建 · 归一化后是 <b className="num">{normalized}</b>
                  </span>
                ) : null}
              </div>
              {dupCard}
              {dup.kind === 'none' && similar.length > 0 && (
                <div
                  style={{
                    marginTop: 16,
                    padding: 16,
                    borderRadius: 14,
                    background: palette.card,
                    border: `1px solid ${palette.hairline}`,
                  }}
                >
                  <div style={{ fontWeight: 600, marginBottom: 8 }}>
                    库里有相近的型号，确认不是同一个：
                  </div>
                  <ul
                    style={{ margin: 0, paddingLeft: 20, color: palette.sub }}
                  >
                    {similar.map((s) => (
                      <li key={s.id}>
                        <a
                          onClick={() =>
                            history.push(`/product/products/${s.id}`)
                          }
                        >
                          {s.mpnDisplay}
                        </a>{' '}
                        · {s.productName || s.categoryName}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </>
          )}

          {step === 2 && (
            <>
              {question(
                '它属于哪一类？',
                '选错了也没关系，创建后随时可以在档案里改。',
              )}
              <div
                role="radiogroup"
                aria-label="品类"
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
                  gap: 12,
                  marginBottom: 24,
                }}
              >
                {categories.map((c) => (
                  // biome-ignore lint/a11y/useSemanticElements: 自绘的卡片式单选，按 ARIA radio 模式实现（role、aria-checked、组内单选）
                  <button
                    type="button"
                    role="radio"
                    aria-checked={draft.categoryId === c.id}
                    key={c.id}
                    style={bigChoice(draft.categoryId === c.id)}
                    onClick={() => patch({ categoryId: c.id })}
                  >
                    <span style={{ flex: 1 }}>{c.categoryName}</span>
                    <span
                      className="num"
                      style={{ color: palette.mute, fontSize: 13 }}
                    >
                      {c.productCount}
                    </span>
                  </button>
                ))}
              </div>
              <div style={{ marginBottom: 24 }}>
                <div style={{ fontWeight: 600, marginBottom: 8 }}>
                  系列（可选）
                </div>
                <div
                  role="radiogroup"
                  aria-label="系列"
                  style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}
                >
                  {/* biome-ignore lint/a11y/useSemanticElements: 自绘的卡片式单选，按 ARIA radio 模式实现（role、aria-checked、组内单选） */}
                  <button
                    type="button"
                    role="radio"
                    aria-checked={!draft.seriesId}
                    onClick={() => patch({ seriesId: undefined })}
                    style={{
                      height: 36,
                      padding: '0 16px',
                      borderRadius: 18,
                      border: `1px solid ${palette.control}`,
                      background: !draft.seriesId ? '#2563EB' : 'transparent',
                      color: !draft.seriesId ? '#FFFFFF' : palette.ink,
                      cursor: 'pointer',
                    }}
                  >
                    不属于任何系列
                  </button>
                  {seriesList.map((s) => (
                    // biome-ignore lint/a11y/useSemanticElements: 自绘的卡片式单选，按 ARIA radio 模式实现（role、aria-checked、组内单选）
                    <button
                      type="button"
                      role="radio"
                      aria-checked={draft.seriesId === s.id}
                      key={s.id}
                      onClick={() => patch({ seriesId: s.id })}
                      style={{
                        height: 36,
                        padding: '0 16px',
                        borderRadius: 18,
                        border: `1px solid ${palette.control}`,
                        background:
                          draft.seriesId === s.id ? '#2563EB' : 'transparent',
                        color:
                          draft.seriesId === s.id ? '#FFFFFF' : palette.ink,
                        cursor: 'pointer',
                      }}
                    >
                      {s.seriesName}
                    </button>
                  ))}
                </div>
              </div>
              <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
                <div>
                  <label
                    htmlFor="lifecycle"
                    style={{
                      display: 'block',
                      fontWeight: 600,
                      marginBottom: 8,
                    }}
                  >
                    现在还在生产吗？
                  </label>
                  <Select
                    id="lifecycle"
                    style={{ width: 200 }}
                    value={draft.lifecycleStatus}
                    options={LIFECYCLE_OPTIONS}
                    onChange={(v) => patch({ lifecycleStatus: v })}
                  />
                  <div
                    style={{ fontSize: 12, color: palette.sub, marginTop: 4 }}
                  >
                    不确定就选「未知」
                  </div>
                </div>
                {(draft.lifecycleStatus === 4 ||
                  draft.lifecycleStatus === 5) && (
                  <div style={{ flex: 1, minWidth: 240 }}>
                    <label
                      htmlFor="lifecycle-source"
                      style={{
                        display: 'block',
                        fontWeight: 600,
                        marginBottom: 8,
                      }}
                    >
                      判断依据
                    </label>
                    <Input
                      id="lifecycle-source"
                      value={draft.lifecycleSource}
                      maxLength={128}
                      placeholder="如 厂商停产通知"
                      onChange={(e) =>
                        patch({ lifecycleSource: e.target.value })
                      }
                      status={
                        !draft.lifecycleSource.trim() ? 'error' : undefined
                      }
                    />
                    {!draft.lifecycleSource.trim() && (
                      <div
                        role="alert"
                        style={{
                          color: palette.red,
                          fontSize: 12,
                          marginTop: 4,
                        }}
                      >
                        停产类状态必须填写依据
                      </div>
                    )}
                  </div>
                )}
              </div>
            </>
          )}

          {step === 3 && product && (
            <>
              {question(
                '给它一张主图',
                '商品已经创建，图片可以现在传，也可以跳过。',
              )}
              <div
                style={{
                  padding: 32,
                  borderRadius: 16,
                  border: `2px dashed ${palette.link}`,
                  textAlign: 'center',
                  background: palette.card,
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    gap: 12,
                    justifyContent: 'center',
                    flexWrap: 'wrap',
                  }}
                >
                  <Button
                    size="large"
                    type="primary"
                    icon={<UploadOutlined />}
                    onClick={() => setUploadOpen(true)}
                  >
                    选择图片
                  </Button>
                  <Button size="large" onClick={() => setLinkOpen(true)}>
                    粘贴图片链接
                  </Button>
                </div>
                <p
                  style={{
                    color: palette.sub,
                    margin: '16px 0 0',
                    fontSize: 13,
                  }}
                >
                  支持 jpg、png、webp，不超过 5MB
                </p>
              </div>
              {media.length > 0 && (
                <ul
                  style={{
                    listStyle: 'none',
                    padding: 0,
                    margin: '24px 0 0',
                    display: 'flex',
                    gap: 12,
                    flexWrap: 'wrap',
                  }}
                >
                  {media.map((m) => (
                    <li key={m.id} style={{ width: 120, position: 'relative' }}>
                      {m.mediaType === 1 ? (
                        <img
                          src={m.fileUrl}
                          alt={m.title || '商品图片'}
                          style={{
                            width: 120,
                            height: 90,
                            objectFit: 'contain',
                            borderRadius: 10,
                            background: palette.inset,
                          }}
                        />
                      ) : (
                        <div
                          style={{
                            width: 120,
                            height: 90,
                            borderRadius: 10,
                            background: '#0B1220',
                          }}
                        />
                      )}
                      {m.isMain === 1 && (
                        <span style={{ position: 'absolute', top: 4, left: 4 }}>
                          <Pill tone="accent">主图</Pill>
                        </span>
                      )}
                    </li>
                  ))}
                </ul>
              )}
              <UploadModal
                productId={product.id}
                open={uploadOpen}
                defaultSetMain={!media.some((m) => m.isMain === 1)}
                onClose={() => setUploadOpen(false)}
                onDone={() => loadMedia(product.id)}
              />
              <LinkModal
                productId={product.id}
                open={linkOpen}
                onClose={() => setLinkOpen(false)}
                onDone={() => loadMedia(product.id)}
              />
            </>
          )}

          {step === 4 && (
            <>
              {question(
                '再补几句说明',
                '都是可选的。物流、海关、参考价可以在商品档案里慢慢补。',
              )}
              <Form form={form} layout="vertical">
                <Form.Item name="productName" label="产品名称">
                  <Input maxLength={128} placeholder="如 SITOP Power Supply" />
                </Form.Item>
                <Form.Item
                  name="specSummary"
                  label="一句话规格摘要"
                  extra="列表页展示，如 24V DC / 5A / 120W"
                >
                  <Input maxLength={300} />
                </Form.Item>
                <Form.Item
                  name="shortDescription"
                  label="简介"
                  extra="需要能追溯到官方资料，不要凭空扩写"
                >
                  <Input.TextArea rows={3} maxLength={500} showCount />
                </Form.Item>
              </Form>
              {['物流信息', '海关信息', '平台参考价'].map((t) => (
                <div
                  key={t}
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    padding: '14px 16px',
                    marginBottom: 8,
                    borderRadius: 12,
                    background: palette.card,
                    border: `1px solid ${palette.hairline}`,
                    color: palette.sub,
                  }}
                >
                  <span>{t}</span>
                  <span>稍后在商品档案里补充</span>
                </div>
              ))}
            </>
          )}

          {step === 5 && product && (
            <div>
              <div style={{ textAlign: 'center', marginBottom: 32 }}>
                <CheckCircleFilled
                  style={{ fontSize: 64, color: palette.green }}
                  aria-hidden="true"
                />
                <h2
                  id="wizard-question"
                  tabIndex={-1}
                  style={{ fontSize: 32, margin: '12px 0 4px' }}
                >
                  {product.mpnDisplay} 已创建
                </h2>
                <p style={{ color: palette.sub, margin: 0 }}>
                  {[product.brandName, product.categoryName]
                    .filter(Boolean)
                    .join(' · ')}
                </p>
              </div>
              {detail?.completeness && (
                <div style={{ maxWidth: 520, margin: '0 auto 32px' }}>
                  <div style={{ marginBottom: 8, fontWeight: 600 }}>
                    档案完整度
                  </div>
                  <CompletenessBar value={detail.completeness} size="full" />
                </div>
              )}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
                  gap: 12,
                  marginBottom: 32,
                }}
              >
                {[
                  'specifications',
                  'logistics',
                  'customs',
                  'referencePrice',
                ].map((k) => {
                  const m = COMPLETENESS_MODULES.find((x) => x.key === k);
                  if (!m) return null;
                  return (
                    <div
                      key={k}
                      style={{
                        padding: 16,
                        borderRadius: 14,
                        background: palette.card,
                        border: `1px solid ${palette.hairline}`,
                      }}
                    >
                      <div style={{ fontWeight: 600 }}>补 {m.label}</div>
                      <div
                        style={{
                          color: palette.sub,
                          fontSize: 13,
                          margin: '4px 0 12px',
                        }}
                      >
                        {m.benefit} · 约 {m.minutes} 分钟
                      </div>
                      <Button
                        onClick={() =>
                          history.push(
                            `/product/products/${product.id}?focus=${k}`,
                          )
                        }
                      >
                        去补充
                      </Button>
                    </div>
                  );
                })}
              </div>
              <div
                style={{ display: 'flex', gap: 12, justifyContent: 'center' }}
              >
                <Button
                  size="large"
                  type="primary"
                  onClick={() =>
                    history.push(`/product/products/${product.id}`)
                  }
                >
                  查看商品档案
                </Button>
                <Button size="large" onClick={restart}>
                  继续新建下一个
                </Button>
              </div>
            </div>
          )}
        </div>

        {/* 右侧：商品卡预览，随输入实时变化 */}
        {step < 5 && (
          <aside
            aria-label="商品卡预览"
            style={{ flex: '0 0 340px', maxWidth: '100%' }}
          >
            <div
              style={{
                position: 'sticky',
                top: 24,
                padding: 24,
                borderRadius: 16,
                background: palette.card,
                border: `1px solid ${palette.hairline}`,
              }}
            >
              <div
                style={{
                  fontFamily: "'IBM Plex Mono', monospace",
                  fontSize: 12,
                  letterSpacing: 2,
                  color: palette.link,
                  marginBottom: 12,
                }}
              >
                PREVIEW
              </div>
              <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                <BrandMark name={draft.brandName ?? '?'} size={44} />
                <div style={{ minWidth: 0 }}>
                  <div
                    style={{
                      fontSize: 20,
                      fontWeight: 700,
                      wordBreak: 'break-all',
                    }}
                  >
                    {draft.mpn.trim() || '型号'}
                  </div>
                  <div style={{ color: palette.sub, fontSize: 13 }}>
                    {[
                      draft.brandName,
                      category?.categoryName,
                      previewSeries?.seriesName,
                    ]
                      .filter(Boolean)
                      .join(' · ') || '品牌 · 品类'}
                  </div>
                </div>
              </div>
              <div style={{ marginTop: 16 }}>
                <LifecyclePill value={draft.lifecycleStatus} />
              </div>
              <p
                style={{ margin: '16px 0 0', color: palette.sub, fontSize: 13 }}
              >
                {step === 1
                  ? '只有前 2 步是必须的'
                  : step === 2
                    ? '选错了也没关系，创建后可以改'
                    : '商品已创建，可以随时离开'}
              </p>
            </div>
          </aside>
        )}
      </main>

      {/* 底部固定操作栏 */}
      {step < 5 && (
        <footer
          style={{
            position: 'fixed',
            left: 0,
            right: 0,
            bottom: 0,
            zIndex: 10,
            padding: '14px 32px',
            background: '#0A101D',
            borderTop: `1px solid ${palette.hairline}`,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 12,
          }}
        >
          <div>
            {step === 2 && <Button onClick={() => setStep(1)}>上一步</Button>}
            {step === 4 && <Button onClick={() => setStep(3)}>上一步</Button>}
            {step === 3 && (
              <Button
                onClick={() => setStep(4)}
                type="link"
                style={{ color: '#93C5FD' }}
              >
                跳过这一步
              </Button>
            )}
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            {step === 1 && (
              <Button
                type="primary"
                size="large"
                disabled={nextDisabled}
                onClick={() => setStep(2)}
              >
                下一步
              </Button>
            )}
            {step === 2 && (
              <Button
                type="primary"
                size="large"
                loading={creating}
                disabled={
                  nextDisabled ||
                  ((draft.lifecycleStatus === 4 ||
                    draft.lifecycleStatus === 5) &&
                    !draft.lifecycleSource.trim())
                }
                onClick={createProduct}
              >
                创建并继续：添加图片
              </Button>
            )}
            {step === 3 && (
              <Button type="primary" size="large" onClick={goInfoStep}>
                下一步
              </Button>
            )}
            {step === 4 && (
              <Button
                type="primary"
                size="large"
                loading={savingInfo}
                onClick={finish}
              >
                完成
              </Button>
            )}
          </div>
        </footer>
      )}

      <Modal
        open={!!resume}
        title="继续上次未完成的新建？"
        okText="继续"
        cancelText="丢弃草稿"
        closable={false}
        mask={{ closable: false }}
        onOk={() => {
          if (resume) setDraft(resume);
          setResume(undefined);
        }}
        onCancel={() => {
          try {
            localStorage.removeItem(key);
          } catch {
            /* 忽略 */
          }
          setResume(undefined);
        }}
      >
        {resume ? `${resume.brandName ?? ''} ${resume.mpn}` : ''}
      </Modal>
    </div>
  );

  return page;
};

const NewProductPage: React.FC = () => (
  <ProductThemeProvider fullPage>
    <WizardInner />
  </ProductThemeProvider>
);

export default NewProductPage;
