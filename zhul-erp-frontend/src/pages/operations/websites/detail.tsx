import {
  ApiOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined,
  ImportOutlined,
  LoadingOutlined,
  LockOutlined,
  PlusOutlined,
  RightOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import { history, useAccess, useModel, useParams } from '@umijs/max';
import {
  App,
  Button,
  Input,
  InputNumber,
  Segmented,
  Select,
  Skeleton,
  Switch,
} from 'antd';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  usePageTitle,
  useTabActive,
  useUnsavedGuard,
} from '@/components/PageTabs/hooks';
import {
  Card,
  PageTitle,
  Pill,
  useWide,
} from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import {
  type BrandOption,
  brandApi,
  type CategoryOption,
  categoryApi,
} from '@/pages/product/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { CategoryMapModal } from './CategoryMapModal';
import { MONO, ModePill, StatusPill, TakeoverPill } from './components';
import {
  type CategoryMapRow,
  type CheckItem,
  LANGUAGES,
  languageLabel,
  liveUrl,
  MODE_AUTO,
  MODE_COPY,
  PERM_EDIT,
  readBizError,
  type Secret,
  type Site,
  type SiteForm,
  type SiteJob,
  siteApi,
  TAKEOVER_PENDING,
  unmappedRows,
} from './service';

const emptyForm = (): SiteForm => ({
  name: '',
  url: 'https://',
  languages: [{ code: 'en', prefix: '' }],
  pathTemplate: '/products/{slug}/',
  publishMode: MODE_AUTO,
  repoUrl: '',
  repoBranch: 'main',
  deployHost: '',
  deployPort: 22,
  deployUser: '',
  deployPath: '',
  productScope: { brandIds: [], categoryIds: [] },
  nonGenuineBrandIds: [],
  priceFactor: 1.05,
  needPurge: false,
  purgeNote: '',
  repoToken: '',
  deployKey: '',
  indexNowKey: '',
  clearIndexNowKey: false,
});

const toForm = (s: Site): SiteForm => ({
  name: s.name,
  url: s.url,
  languages: s.languages,
  pathTemplate: s.pathTemplate,
  publishMode: s.publishMode,
  repoUrl: s.repoUrl,
  repoBranch: s.repoBranch,
  deployHost: s.deployHost,
  deployPort: s.deployPort,
  deployUser: s.deployUser,
  deployPath: s.deployPath,
  productScope: {
    brandIds: s.productScope?.brandIds ?? [],
    categoryIds: s.productScope?.categoryIds ?? [],
  },
  nonGenuineBrandIds: s.nonGenuineBrandIds ?? [],
  priceFactor: s.priceFactor,
  needPurge: s.needPurge,
  purgeNote: s.purgeNote ?? '',
  repoToken: '',
  deployKey: '',
  indexNowKey: '',
  clearIndexNowKey: false,
});

// ================================================================ 小组件

const Section: React.FC<{
  title: React.ReactNode;
  extra?: React.ReactNode;
  children: React.ReactNode;
}> = ({ title, extra, children }) => (
  <Card style={{ padding: 20, marginBottom: 16 }}>
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: 14,
      }}
    >
      <h3 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>{title}</h3>
      {extra}
    </div>
    {children}
  </Card>
);

const Field: React.FC<{
  label: React.ReactNode;
  hint?: React.ReactNode;
  style?: React.CSSProperties;
  children: React.ReactNode;
}> = ({ label, hint, style, children }) => {
  const { palette } = useAppTheme();
  return (
    <div style={{ minWidth: 0, ...style }}>
      <div style={{ fontSize: 13, color: palette.sub, marginBottom: 6 }}>
        {label}
      </div>
      {children}
      {hint && (
        <div style={{ fontSize: 12, color: palette.mute, marginTop: 6 }}>
          {hint}
        </div>
      )}
    </div>
  );
};

const Row: React.FC<{ cols: string; children: React.ReactNode }> = ({
  cols,
  children,
}) => (
  <div
    style={{
      display: 'grid',
      gridTemplateColumns: cols,
      gap: 14,
      marginBottom: 14,
    }}
  >
    {children}
  </div>
);

/** 已配置的凭据：锁 + 末四位 */
const SecretTag: React.FC<{ secret?: Secret; cleared?: boolean }> = ({
  secret,
  cleared,
}) => {
  if (cleared) return <Pill tone="orange">保存后删除</Pill>;
  if (!secret?.configured) return <Pill tone="mute">未配置</Pill>;
  return (
    <Pill tone="green">
      <LockOutlined /> 已配置 ····{secret.last4}
    </Pill>
  );
};

/** 凭据输入：不回显，留空表示不改 */
const SecretInput: React.FC<{
  secret?: Secret;
  value: string;
  onChange: (v: string) => void;
  multiline?: boolean;
  disabled?: boolean;
  cleared?: boolean;
  label: string;
}> = ({ secret, value, onChange, multiline, disabled, cleared, label }) => {
  const placeholder = secret?.configured ? '留空表示不改' : '填写后加密保存';
  const tag = <SecretTag secret={secret} cleared={cleared} />;
  if (multiline) {
    return (
      <div style={{ position: 'relative' }}>
        <Input.TextArea
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          autoSize={{ minRows: 1, maxRows: 6 }}
          disabled={disabled}
          spellCheck={false}
          autoComplete="off"
          style={{ fontFamily: MONO, paddingRight: 150 }}
          aria-label={label}
        />
        <span style={{ position: 'absolute', right: 8, top: 6 }}>{tag}</span>
      </div>
    );
  }
  return (
    <Input.Password
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      disabled={disabled}
      autoComplete="new-password"
      visibilityToggle={false}
      suffix={tag}
      style={{ fontFamily: MONO }}
      aria-label={label}
    />
  );
};

// ================================================================ 右侧卡片

const CheckCard: React.FC<{ site: Site; canEdit: boolean }> = ({
  site,
  canEdit,
}) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const { initialState } = useModel('@@initialState');
  const [items, setItems] = useState<CheckItem[]>();
  const [at, setAt] = useState<string>();
  const [running, setRunning] = useState(false);
  const run = async () => {
    setRunning(true);
    try {
      setItems(await siteApi.check(site.id));
      setAt(
        `${formatDateTime(new Date())} · ${initialState?.currentUser?.name ?? ''}`,
      );
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setRunning(false);
    }
  };
  return (
    <Section
      title="检测连接"
      extra={
        canEdit && (
          <Button icon={<ApiOutlined />} loading={running} onClick={run}>
            检测连接
          </Button>
        )
      }
    >
      <div style={{ fontSize: 12, color: palette.mute, marginBottom: 12 }}>
        只检查，不改站点和线上内容；保存后的配置才会被检测
      </div>
      {items ? (
        <div style={{ display: 'grid', gap: 10 }}>
          {items.map((i) => (
            <div
              key={i.key}
              style={{
                display: 'flex',
                gap: 10,
                padding: '10px 12px',
                borderRadius: 10,
                border: `1px solid ${palette.hairline}`,
              }}
            >
              {i.ok ? (
                <CheckCircleOutlined style={{ color: palette.green }} />
              ) : (
                <CloseCircleOutlined style={{ color: palette.red }} />
              )}
              <div style={{ minWidth: 0 }}>
                <b>{i.name}</b>
                <div
                  style={{
                    fontSize: 12,
                    color: i.ok ? palette.sub : palette.red,
                    wordBreak: 'break-all',
                  }}
                >
                  {i.ok ? '通过' : ''}
                  {i.ok && i.message ? ' · ' : ''}
                  {i.message}
                </div>
              </div>
            </div>
          ))}
          <div style={{ fontSize: 12, color: palette.mute }}>{at}</div>
        </div>
      ) : (
        <div
          style={{
            padding: '18px 0',
            textAlign: 'center',
            color: palette.mute,
            fontSize: 13,
          }}
        >
          检查代码仓库、适配脚本、部署服务器和网址四项
        </div>
      )}
    </Section>
  );
};

/** 最近一次导入、比对的说明 */
const jobText = (j: SiteJob | undefined, site: Site) => {
  if (!j) return undefined;
  if (j.status === 1)
    return j.jobType === 'import' ? '正在读取站点商品…' : '正在比对…';
  if (j.status === 3) return `失败：${j.error || '没有返回原因'}`;
  if (j.jobType === 'import') {
    const matched = j.result?.matched?.length ?? 0;
    const unmatched = j.result?.unmatched?.length ?? 0;
    if (j.status === 2)
      return `读取完成：匹配 ${matched} 个、未匹配 ${unmatched} 个，等待确认导入`;
    return `${site.summary?.importedCount ?? matched} 个商品（未匹配 ${unmatched} 个）`;
  }
  const same = j.result?.same ?? 0;
  const diffs = j.result?.differences?.length ?? 0;
  if (j.status === 4) return `${same} 一致 · ${diffs} 条差异已确认`;
  return `${same} 一致 · ${j.pending ?? 0} 条差异未确认`;
};

const TakeoverCard: React.FC<{
  site: Site;
  canEdit: boolean;
  onChanged: (s?: Site) => void;
}> = ({ site, canEdit, onChanged }) => {
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const active = useTabActive();
  const [imp, setImp] = useState<SiteJob>();
  const [cmp, setCmp] = useState<SiteJob>();
  const [acting, setActing] = useState<string>();
  const pendingTakeover = site.takeoverStatus === TAKEOVER_PENDING;

  const load = useCallback(async () => {
    try {
      const jobs = await siteApi.jobs(site.id);
      const lastImp = jobs.find((j) => j.jobType === 'import');
      const lastCmp = jobs.find((j) => j.jobType === 'compare');
      // 列表不带结果，数字从详情里取
      const [i, c] = await Promise.all([
        lastImp && lastImp.status !== 1 && lastImp.status !== 3
          ? siteApi.job(lastImp.id)
          : lastImp,
        lastCmp && lastCmp.status !== 1 && lastCmp.status !== 3
          ? siteApi.job(lastCmp.id)
          : lastCmp,
      ]);
      setImp(i);
      setCmp(c);
    } catch {
      // 读不到最近任务时卡片只显示按钮
    }
  }, [site.id]);
  useEffect(() => {
    load();
  }, [load]);

  const running = imp?.status === 1 || cmp?.status === 1;
  useEffect(() => {
    if (!running || !active) return undefined;
    const t = window.setInterval(load, 5000);
    return () => window.clearInterval(t);
  }, [running, active, load]);

  const start = async (kind: 'import' | 'compare') => {
    setActing(kind);
    try {
      await (kind === 'import'
        ? siteApi.startImport(site.id)
        : siteApi.startCompare(site.id));
      message.success(kind === 'import' ? '已开始读取站点商品' : '已开始比对');
      load();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setActing(undefined);
    }
  };

  const takeover = () =>
    modal.confirm({
      title: '开始以 ERP 为准？',
      content:
        '之后这个站点的商品内容都从 ERP 发布，不能再导入、比对，也不能退回「接管中」。',
      okText: '开始以 ERP 为准',
      onOk: async () => {
        try {
          onChanged(await siteApi.takeover(site.id));
          message.success('已开始以 ERP 为准');
        } catch (e) {
          message.error(readBizError(e).message);
          throw e;
        }
      },
    });

  const line = (label: string, text?: string, warn?: boolean) => (
    <div style={{ display: 'flex', gap: 12, fontSize: 13, marginBottom: 8 }}>
      <span style={{ color: palette.mute, width: 72, flexShrink: 0 }}>
        {label}
      </span>
      <span style={{ color: warn ? palette.orange : palette.ink }}>
        {text ?? '还没有'}
      </span>
    </div>
  );

  return (
    <Section title="接管" extra={<TakeoverPill site={site} />}>
      {pendingTakeover ? (
        <>
          <div style={{ fontSize: 12, color: palette.mute, marginBottom: 12 }}>
            开始以 ERP 为准之前不能从 ERP 发布
          </div>
          {line('已导入', jobText(imp, site), imp?.status === 3)}
          {line(
            '一致性比对',
            jobText(cmp, site),
            cmp?.status === 3 || (cmp?.status === 2 && !!cmp.pending),
          )}
          {canEdit && (
            <div
              style={{
                display: 'flex',
                gap: 8,
                flexWrap: 'wrap',
                marginTop: 14,
              }}
            >
              <Button
                icon={<ImportOutlined />}
                loading={acting === 'import'}
                disabled={running}
                onClick={() => start('import')}
              >
                导入现有商品
              </Button>
              <Button
                icon={<SwapOutlined />}
                loading={acting === 'compare'}
                disabled={running}
                onClick={() => start('compare')}
              >
                一致性比对
              </Button>
              <Button type="primary" disabled={running} onClick={takeover}>
                开始以 ERP 为准
              </Button>
            </div>
          )}
          {running && (
            <div style={{ fontSize: 12, color: palette.mute, marginTop: 10 }}>
              <LoadingOutlined /> 执行中，完成后自动刷新
            </div>
          )}
        </>
      ) : (
        <div style={{ fontSize: 13, color: palette.sub }}>
          {site.takeoverBy} 于 {formatDateTime(site.takeoverAt)} 开始以 ERP 为准
        </div>
      )}
    </Section>
  );
};

const CategoryCard: React.FC<{
  site: Site;
  rows?: CategoryMapRow[];
  onOpen: () => void;
}> = ({ site, rows, onOpen }) => {
  const { palette } = useAppTheme();
  const missing = rows ? unmappedRows(rows) : [];
  const mappedTop = rows?.filter((r) => r.siteCategory).length ?? 0;
  const ownSubs =
    rows?.reduce(
      (n, r) => n + r.children.filter((c) => c.siteCategory).length,
      0,
    ) ?? 0;
  return (
    <Section
      title="品类对照"
      extra={
        <Button
          type="link"
          style={{ padding: 0 }}
          onClick={onOpen}
          aria-label={`设置${site.name}的品类对照`}
        >
          设置 <RightOutlined />
        </Button>
      }
    >
      {!rows ? (
        <Skeleton active paragraph={{ rows: 1 }} title={false} />
      ) : (
        <>
          <div style={{ fontSize: 13, color: palette.sub }}>
            已对照 {mappedTop} 个一级品类
            {ownSubs > 0 ? `，${ownSubs} 个二级品类单独设置` : ''}
          </div>
          {missing.slice(0, 3).map((m) => (
            <div
              key={m.name}
              style={{ fontSize: 13, color: palette.orange, marginTop: 8 }}
            >
              <ExclamationCircleOutlined /> 「{m.name}」还没有对照，{m.count}{' '}
              个商品不能发布
            </div>
          ))}
          {missing.length > 3 && (
            <div style={{ fontSize: 13, color: palette.orange, marginTop: 8 }}>
              还有 {missing.length - 3} 个品类没有对照
            </div>
          )}
        </>
      )}
    </Section>
  );
};

// ================================================================ 页面

const WebsiteDetailPage: React.FC = () => {
  const { id: idParam } = useParams<{ id: string }>();
  const isNew = idParam === 'new';
  const id = isNew ? undefined : Number(idParam);
  const access = useAccess() as Record<string, boolean>;
  const canEdit = !!access[PERM_EDIT];
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const wide = useWide();

  const [site, setSite] = useState<Site>();
  const [form, setForm] = useState<SiteForm>(emptyForm);
  const [base, setBase] = useState<SiteForm>(emptyForm);
  const [loading, setLoading] = useState(!isNew);
  const [error, setError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [brands, setBrands] = useState<BrandOption[]>([]);
  const [categories, setCategories] = useState<CategoryOption[]>([]);
  const [mapRows, setMapRows] = useState<CategoryMapRow[]>();
  const [mapOpen, setMapOpen] = useState(false);

  usePageTitle(isNew ? '新建站点' : site?.name);
  const dirty = useMemo(
    () => canEdit && JSON.stringify(form) !== JSON.stringify(base),
    [canEdit, form, base],
  );
  useUnsavedGuard(dirty);

  const apply = useCallback((s: Site) => {
    setSite(s);
    const f = toForm(s);
    setForm(f);
    setBase(f);
  }, []);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(undefined);
    try {
      apply(await siteApi.detail(id));
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [id, apply]);
  const loadMap = useCallback(async () => {
    if (!id) return;
    try {
      setMapRows(await siteApi.categoryMap(id));
    } catch {
      setMapRows([]);
    }
  }, [id]);
  useEffect(() => {
    load();
    loadMap();
  }, [load, loadMap]);
  useEffect(() => {
    brandApi
      .options()
      .then(setBrands)
      .catch(() => setBrands([]));
    categoryApi
      .options(0)
      .then(setCategories)
      .catch(() => setCategories([]));
  }, []);

  // 从列表点「接管」进来时定位到接管卡片
  useEffect(() => {
    if (site && window.location.hash === '#takeover') {
      document
        .getElementById('takeover')
        ?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [site]);

  const set = <K extends keyof SiteForm>(k: K, v: SiteForm[K]) =>
    setForm((f) => ({ ...f, [k]: v }));

  const brandOptions = useMemo(
    () => brands.map((b) => ({ value: b.id, label: b.brandName })),
    [brands],
  );
  const categoryOptions = useMemo(
    () =>
      categories.map((c) => ({
        value: c.id,
        label: c.categoryNameZh || c.categoryName,
      })),
    [categories],
  );

  const save = async () => {
    setSaving(true);
    try {
      if (isNew) {
        const s = await siteApi.create(form);
        setBase(form);
        message.success('站点已新建');
        history.replace(`/operations/websites/${s.id}`);
      } else if (id) {
        await siteApi.update(id, form);
        message.success('已保存');
        await load();
        loadMap();
      }
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const toggleStatus = (on: boolean) => {
    if (!site) return;
    const run = async () => {
      try {
        await siteApi.setStatus(site.id, on ? 1 : 0);
        await load();
        message.success(on ? '已启用' : '已停用');
      } catch (e) {
        message.error(readBizError(e).message);
      }
    };
    if (on) run();
    else
      modal.confirm({
        title: `停用「${site.name}」？`,
        content: '停用后不能发布到这个站点；已发布的商品留在站点上。',
        okText: '停用',
        okButtonProps: { danger: true },
        onOk: run,
      });
  };

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (loading) return <Skeleton active paragraph={{ rows: 12 }} />;

  const auto = form.publishMode === MODE_AUTO;
  const readOnly = !canEdit;
  const savedAuto = site?.publishMode === MODE_AUTO;
  const usedCodes = form.languages.map((l) => l.code);

  const left = (
    <div style={{ minWidth: 0 }}>
      <Section title="基本信息">
        <Row cols={isNew ? '1fr 1fr' : '1fr 1fr 140px'}>
          <Field label="名称">
            <Input
              value={form.name}
              onChange={(e) => set('name', e.target.value)}
              maxLength={64}
              disabled={readOnly}
              placeholder="如 Fouwell"
              aria-label="名称"
            />
          </Field>
          <Field label="网址">
            <Input
              value={form.url}
              onChange={(e) => set('url', e.target.value)}
              disabled={readOnly}
              placeholder="https://fouwell.com"
              style={{ fontFamily: MONO }}
              aria-label="网址"
            />
          </Field>
          {site && (
            <Field label="状态">
              <span
                style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}
              >
                <Switch
                  checked={site.status === 1}
                  disabled={readOnly}
                  onChange={toggleStatus}
                  aria-label="启用"
                />
                {site.status === 1 ? '启用' : '停用'}
              </span>
            </Field>
          )}
        </Row>
      </Section>

      <Section title="语言与链接">
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '110px 140px 1fr 32px',
            gap: '10px 14px',
            alignItems: 'center',
          }}
        >
          <span style={{ fontSize: 13, color: palette.sub }}>语言</span>
          <span style={{ fontSize: 13, color: palette.sub }}>地址前缀</span>
          <span style={{ fontSize: 13, color: palette.sub }}>上线链接示例</span>
          <span />
          {form.languages.map((l, i) => (
            <React.Fragment key={l.code}>
              <span
                style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}
              >
                <b>{languageLabel(l.code)}</b>
                {i === 0 && <Pill tone="accent">默认</Pill>}
              </span>
              <Input
                value={l.prefix}
                placeholder="（无）"
                disabled={readOnly}
                onChange={(e) =>
                  set(
                    'languages',
                    form.languages.map((x, j) =>
                      j === i ? { ...x, prefix: e.target.value } : x,
                    ),
                  )
                }
                style={{ fontFamily: MONO }}
                aria-label={`${languageLabel(l.code)}地址前缀`}
              />
              <span
                style={{
                  fontFamily: MONO,
                  fontSize: 12,
                  color: palette.sub,
                  wordBreak: 'break-all',
                }}
              >
                {/^https?:\/\/[^/\s]+/.test(form.url)
                  ? liveUrl(form.url, l.prefix, form.pathTemplate)
                  : '填好网址后显示'}
              </span>
              {i > 0 && !readOnly ? (
                <Button
                  type="text"
                  icon={<DeleteOutlined />}
                  aria-label={`删除${languageLabel(l.code)}`}
                  onClick={() =>
                    set(
                      'languages',
                      form.languages.filter((_, j) => j !== i),
                    )
                  }
                />
              ) : (
                <span />
              )}
            </React.Fragment>
          ))}
        </div>
        {!readOnly && form.languages.length < LANGUAGES.length && (
          <Select
            value={null}
            placeholder={
              <span>
                <PlusOutlined /> 添加语言
              </span>
            }
            variant="borderless"
            options={LANGUAGES.filter((l) => !usedCodes.includes(l.code)).map(
              (l) => ({ value: l.code, label: l.label }),
            )}
            onChange={(code: string) => {
              const lang = LANGUAGES.find((l) => l.code === code);
              if (lang)
                set('languages', [
                  ...form.languages,
                  { code, prefix: lang.prefix },
                ]);
            }}
            style={{ width: 140, marginTop: 10, color: palette.link }}
            aria-label="添加语言"
          />
        )}
        <div style={{ marginTop: 16 }}>
          <Field
            label="商品页地址模板"
            hint="完整链接 = 网址 + 语言前缀 + 模板；{slug} 为商品的链接别名，发布后锁定"
          >
            <Input
              value={form.pathTemplate}
              onChange={(e) => set('pathTemplate', e.target.value)}
              disabled={readOnly}
              style={{ fontFamily: MONO }}
              aria-label="商品页地址模板"
            />
          </Field>
        </div>
      </Section>

      <Section title="发布方式">
        <Segmented
          value={form.publishMode}
          disabled={readOnly}
          onChange={(v) => set('publishMode', v as number)}
          options={[
            { value: MODE_AUTO, label: '自动发布' },
            { value: MODE_COPY, label: '只复制' },
          ]}
          style={{ marginBottom: 16 }}
        />
        {auto ? (
          <>
            <div style={{ fontWeight: 600, margin: '4px 0 10px' }}>
              代码仓库
            </div>
            <Row cols="1fr 140px">
              <Field label="仓库地址">
                <Input
                  value={form.repoUrl}
                  onChange={(e) => set('repoUrl', e.target.value)}
                  disabled={readOnly}
                  placeholder="https://github.com/…/site"
                  style={{ fontFamily: MONO }}
                  aria-label="仓库地址"
                />
              </Field>
              <Field label="分支">
                <Input
                  value={form.repoBranch}
                  onChange={(e) => set('repoBranch', e.target.value)}
                  disabled={readOnly}
                  style={{ fontFamily: MONO }}
                  aria-label="分支"
                />
              </Field>
            </Row>
            <Row cols="1fr">
              <Field label="访问令牌">
                <SecretInput
                  label="访问令牌"
                  secret={site?.repoToken}
                  value={form.repoToken}
                  disabled={readOnly}
                  onChange={(v) => set('repoToken', v)}
                />
              </Field>
            </Row>
            <div style={{ fontWeight: 600, margin: '18px 0 10px' }}>
              部署（SSH 同步）
            </div>
            <Row cols="1fr 110px 200px">
              <Field label="主机">
                <Input
                  value={form.deployHost}
                  onChange={(e) => set('deployHost', e.target.value)}
                  disabled={readOnly}
                  style={{ fontFamily: MONO }}
                  aria-label="主机"
                />
              </Field>
              <Field label="端口">
                <InputNumber
                  value={form.deployPort}
                  onChange={(v) => set('deployPort', v)}
                  min={1}
                  max={65535}
                  precision={0}
                  disabled={readOnly}
                  style={{ width: '100%', fontFamily: MONO }}
                  aria-label="端口"
                />
              </Field>
              <Field label="用户">
                <Input
                  value={form.deployUser}
                  onChange={(e) => set('deployUser', e.target.value)}
                  disabled={readOnly}
                  style={{ fontFamily: MONO }}
                  aria-label="用户"
                />
              </Field>
            </Row>
            <Row cols="1fr 1fr">
              <Field label="目标目录">
                <Input
                  value={form.deployPath}
                  onChange={(e) => set('deployPath', e.target.value)}
                  disabled={readOnly}
                  placeholder="~/www/example.com/public_html"
                  style={{ fontFamily: MONO }}
                  aria-label="目标目录"
                />
              </Field>
              <Field label="私钥">
                <SecretInput
                  label="私钥"
                  multiline
                  secret={site?.deployKey}
                  value={form.deployKey}
                  disabled={readOnly}
                  onChange={(v) => set('deployKey', v)}
                />
              </Field>
            </Row>
            <Field
              label="IndexNow 密钥（选填，填了以后发布成功时向 Bing、Yandex 推送）"
              hint={
                !readOnly &&
                site?.indexNowKey.configured && (
                  <Button
                    type="link"
                    size="small"
                    style={{ padding: 0 }}
                    onClick={() =>
                      setForm((f) => ({
                        ...f,
                        indexNowKey: '',
                        clearIndexNowKey: !f.clearIndexNowKey,
                      }))
                    }
                  >
                    {form.clearIndexNowKey ? '保留已配置的密钥' : '删除密钥'}
                  </Button>
                )
              }
            >
              <SecretInput
                label="IndexNow 密钥"
                secret={site?.indexNowKey}
                value={form.indexNowKey}
                cleared={form.clearIndexNowKey}
                disabled={readOnly || form.clearIndexNowKey}
                onChange={(v) => set('indexNowKey', v)}
              />
            </Field>
          </>
        ) : (
          <div style={{ fontSize: 13, color: palette.mute }}>
            ERP 只生成内容，在发品任务里复制到站点后台，再手工标记已发布
          </div>
        )}
      </Section>

      <Section title="商品范围与品牌">
        <Row cols="1fr 1fr">
          <Field
            label="品牌"
            hint={`空表示本公司全部商品${
              site?.summary ? `（当前范围 ${site.summary.scopeCount} 个）` : ''
            }`}
          >
            <Select
              mode="multiple"
              allowClear
              value={form.productScope.brandIds}
              onChange={(v) =>
                set('productScope', { ...form.productScope, brandIds: v })
              }
              options={brandOptions}
              showSearch={{ optionFilterProp: 'label' }}
              placeholder="全部品牌"
              disabled={readOnly}
              maxTagCount="responsive"
              aria-label="品牌范围"
            />
          </Field>
          <Field label="品类（含下级）">
            <Select
              mode="multiple"
              allowClear
              value={form.productScope.categoryIds}
              onChange={(v) =>
                set('productScope', { ...form.productScope, categoryIds: v })
              }
              options={categoryOptions}
              showSearch={{ optionFilterProp: 'label' }}
              placeholder="全部品类"
              disabled={readOnly}
              maxTagCount="responsive"
              aria-label="品类范围"
            />
          </Field>
        </Row>
        <Field label="非正品品牌" hint="这些品牌的商品页面不出现「正品」类说法">
          <Select
            mode="multiple"
            allowClear
            value={form.nonGenuineBrandIds}
            onChange={(v) => set('nonGenuineBrandIds', v)}
            options={brandOptions}
            showSearch={{ optionFilterProp: 'label' }}
            placeholder="没有"
            disabled={readOnly}
            aria-label="非正品品牌"
          />
        </Field>
      </Section>

      <Section title="售价规则">
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '140px 140px 1fr',
            gap: 14,
            alignItems: 'end',
          }}
        >
          <Field label="系数">
            <InputNumber
              value={form.priceFactor}
              onChange={(v) => set('priceFactor', v)}
              min={0.5}
              max={10}
              step={0.01}
              precision={2}
              disabled={readOnly}
              style={{ width: '100%' }}
              aria-label="系数"
            />
          </Field>
          <Field label="币种">
            <Input value="美元 USD" disabled aria-label="币种" />
          </Field>
          <div style={{ fontSize: 12, color: palette.mute, paddingBottom: 6 }}>
            售价 = 近 180 天询价最低价 × 系数 ÷ 美元汇率，四舍五入保留 2
            位；手工填的站点售价优先，没有询价时用平台参考价，都没有显示为询价
          </div>
        </div>
      </Section>

      <Section title="发布后清缓存">
        <span
          style={{
            display: 'inline-flex',
            gap: 8,
            alignItems: 'center',
            marginBottom: 12,
          }}
        >
          <Switch
            checked={form.needPurge}
            onChange={(v) => set('needPurge', v)}
            disabled={readOnly}
            aria-label="需要人工清缓存"
          />
          需要人工清缓存
        </span>
        {form.needPurge && (
          <Field label="清缓存的操作说明（确认上线时显示）">
            <Input.TextArea
              value={form.purgeNote}
              onChange={(e) => set('purgeNote', e.target.value)}
              autoSize={{ minRows: 2, maxRows: 6 }}
              maxLength={500}
              disabled={readOnly}
              placeholder="如 登录 SiteGround → Speed → Caching → Flush Cache"
              aria-label="清缓存的操作说明"
            />
          </Field>
        )}
      </Section>

      {canEdit && (
        <div
          style={{
            position: 'sticky',
            bottom: 0,
            display: 'flex',
            justifyContent: 'flex-end',
            gap: 12,
            padding: '12px 0',
            background: palette.canvas,
          }}
        >
          <Button
            disabled={!dirty || saving}
            onClick={() =>
              isNew ? history.push('/operations/websites') : setForm(base)
            }
          >
            {isNew ? '取消' : '撤销修改'}
          </Button>
          <Button
            type="primary"
            loading={saving}
            disabled={!dirty}
            onClick={save}
          >
            {isNew ? '新建站点' : '保存'}
          </Button>
        </div>
      )}
    </div>
  );

  const right = site && (
    <div style={{ minWidth: 0 }}>
      {savedAuto && <CheckCard site={site} canEdit={canEdit} />}
      {savedAuto && (
        <div id="takeover">
          <TakeoverCard
            site={site}
            canEdit={canEdit}
            onChanged={() => load()}
          />
        </div>
      )}
      <CategoryCard
        site={site}
        rows={mapRows}
        onOpen={() => setMapOpen(true)}
      />
    </div>
  );

  return (
    <div>
      <PageTitle
        crumbs={['站点管理', isNew ? '新建站点' : (site?.name ?? '')]}
        title={isNew ? '新建站点' : site?.name}
        description={
          site && <span style={{ fontFamily: MONO }}>{site.url}</span>
        }
        actions={
          site && (
            <span style={{ display: 'inline-flex', gap: 8 }}>
              <ModePill mode={site.publishMode} />
              {site.publishMode === MODE_AUTO && <TakeoverPill site={site} />}
              <StatusPill status={site.status} />
            </span>
          )
        }
      />
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: wide && right ? 'minmax(0, 1fr) 380px' : '1fr',
          gap: 16,
          alignItems: 'start',
        }}
      >
        {left}
        {right}
      </div>
      {site && (
        <CategoryMapModal
          siteId={site.id}
          siteName={site.name}
          open={mapOpen}
          readOnly={readOnly}
          onClose={(saved) => {
            setMapOpen(false);
            if (saved) setMapRows(saved);
          }}
        />
      )}
    </div>
  );
};

export default WebsiteDetailPage;
