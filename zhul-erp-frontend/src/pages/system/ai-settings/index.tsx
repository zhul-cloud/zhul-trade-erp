import {
  DndContext,
  type DragEndEvent,
  KeyboardSensor,
  PointerSensor,
  rectIntersection,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { useAccess } from '@umijs/max';
import {
  App,
  AutoComplete,
  Button,
  Col,
  Form,
  Input,
  InputNumber,
  Result,
  Row,
  Segmented,
  Skeleton,
  Spin,
  Switch,
  Typography,
} from 'antd';
import {
  Bot,
  ChevronRight,
  CircleAlert,
  CircleCheck,
  Clock,
  CornerDownLeft,
  ExternalLink,
  GripVertical,
  Info,
  KeyRound,
  Lock,
  RotateCw,
  TriangleAlert,
} from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { PageHeader } from '@/components/ListLayout';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import type { Palette } from '@/theme/palette';
import { POPUP_SHADOW } from '@/theme/palette';
import {
  type AiFormValues,
  type CardDraft,
  cardState,
  connFormValues,
  connKey,
  DEFAULT_EFFORT,
  defaultModel,
  EFFORT_OPTIONS,
  fullLabel,
  hm,
  isDirty,
  type ModelField,
  modelOptions,
  modelsFingerprint,
  moveCard,
  orderChanged,
  reasonLabel,
  type SummaryTone,
  shortLabel,
  summarize,
  testFingerprint,
  toCards,
  toPayload,
  toSavePayload,
  usable,
} from './aiConfig';
import { ClaudeLogo, OpenAILogo } from './brandLogos';
import {
  type AiConfig,
  type AiConnection,
  type AiModelList,
  type AiProvider,
  type AiTestResult,
  getAiConfig,
  getAiModels,
  getAiSubscriptionStatus,
  saveAiConfig,
  testAiConfig,
} from './service';

const PROVIDER_META: Record<
  AiProvider,
  {
    name: string;
    console: string;
    consoleName: string;
    Logo: React.FC<{ size?: number }>;
  }
> = {
  claude: {
    name: 'Claude',
    console: 'https://console.anthropic.com/settings/keys',
    consoleName: 'Anthropic',
    Logo: ClaudeLogo,
  },
  openai: {
    name: 'OpenAI',
    console: 'https://platform.openai.com/api-keys',
    consoleName: 'OpenAI',
    Logo: OpenAILogo,
  },
};

/** 思考强度：分段选择，空值显示为「中」 */
const EffortField: React.FC<{
  value?: string;
  onChange?: (v: string) => void;
  disabled?: boolean;
}> = ({ value, onChange, disabled }) => (
  <Segmented
    block
    disabled={disabled}
    options={EFFORT_OPTIONS}
    value={value || DEFAULT_EFFORT}
    onChange={(v) => onChange?.(v as string)}
  />
);

const Divider: React.FC<{ palette: Palette }> = ({ palette }) => (
  <div style={{ height: 1, background: palette.hairline, margin: '4px 0' }} />
);

const LogoBox: React.FC<{
  palette: Palette;
  provider: AiProvider;
  size: number;
  dim?: boolean;
}> = ({ palette, provider, size, dim }) => {
  const { Logo } = PROVIDER_META[provider];
  return (
    <span
      style={{
        display: 'grid',
        placeItems: 'center',
        flex: 'none',
        width: size,
        height: size,
        borderRadius: 8,
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
        color: palette.ink,
        opacity: dim ? 0.5 : 1,
      }}
    >
      <Logo size={Math.round(size * 0.6)} />
    </span>
  );
};

/** 字段标题，说明以小字跟在标题后面 */
const FieldLabel: React.FC<{ title: string; hint: React.ReactNode }> = ({
  title,
  hint,
}) => {
  const { palette } = useAppTheme();
  return (
    <span>
      {title}
      <span
        style={{
          marginLeft: 8,
          fontSize: 12,
          fontWeight: 400,
          color: palette.mute,
        }}
      >
        {hint}
      </span>
    </span>
  );
};

/** 一张连接方式卡片：拖动手柄、序号、品牌、状态、测试、启用开关；选中时下方放 Key 输入等内容 */
const ConnectionCard: React.FC<{
  conn: AiConnection;
  draft: CardDraft;
  index: number;
  selected: boolean;
  canEdit: boolean;
  draggable: boolean;
  testing: boolean;
  testDisabled: boolean;
  subtitle?: React.ReactNode;
  note?: React.ReactNode;
  onSelect: () => void;
  onToggle: (enabled: boolean) => void;
  onTest: () => void;
  children?: React.ReactNode;
}> = ({
  conn,
  draft,
  index,
  selected,
  canEdit,
  draggable,
  testing,
  testDisabled,
  subtitle,
  note,
  onSelect,
  onToggle,
  onTest,
  children,
}) => {
  const { palette } = useAppTheme();
  const id = connKey(conn);
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id, disabled: !draggable });
  const [stateLabel, stateTone] = cardState(conn, draft.enabled);
  const off = !draft.enabled;
  const name = PROVIDER_META[conn.provider].name;
  return (
    <div
      ref={setNodeRef}
      data-testid={`conn-${id}`}
      style={{
        transform: CSS.Transform.toString(transform),
        transition,
        position: 'relative',
        zIndex: isDragging ? 3 : undefined,
      }}
    >
      <div
        style={{
          borderRadius: 10,
          border: `${selected ? 1.5 : 1}px solid ${
            selected ? palette.link : palette.hairline
          }`,
          background: selected ? palette.primarySoft : palette.card,
          boxShadow: isDragging ? POPUP_SHADOW : 'none',
          transition: 'background 200ms ease-out, border-color 200ms ease-out',
        }}
      >
        {/* biome-ignore lint/a11y/useSemanticElements: 整张卡片可点选中，行内还有测试按钮、开关与拖动手柄，不能整体用原生 button 包裹 */}
        <div
          role="button"
          tabIndex={0}
          aria-pressed={selected}
          aria-label={`${fullLabel(conn)}，第 ${index + 1} 位`}
          onClick={onSelect}
          onKeyDown={(e) => {
            if (
              e.target === e.currentTarget &&
              (e.key === 'Enter' || e.key === ' ')
            ) {
              e.preventDefault();
              onSelect();
            }
          }}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            padding: '12px 14px 12px 8px',
            cursor: 'pointer',
          }}
        >
          <button
            type="button"
            ref={setActivatorNodeRef}
            aria-label={`拖动调整 ${fullLabel(conn)} 的顺序`}
            disabled={!draggable}
            onClick={(e) => e.stopPropagation()}
            {...(draggable ? attributes : {})}
            {...(draggable ? listeners : {})}
            style={{
              display: 'grid',
              placeItems: 'center',
              width: 20,
              height: 28,
              padding: 0,
              border: 'none',
              borderRadius: 4,
              background: 'transparent',
              color: palette.faint,
              cursor: draggable ? 'grab' : 'default',
              visibility: draggable ? 'visible' : 'hidden',
              touchAction: 'none',
            }}
          >
            <GripVertical size="1em" />
          </button>
          <span
            style={{
              display: 'grid',
              placeItems: 'center',
              flex: 'none',
              width: 22,
              height: 22,
              borderRadius: 11,
              fontSize: 12,
              fontWeight: 600,
              color: selected ? '#fff' : palette.sub,
              background: selected ? palette.link : palette.inset,
            }}
          >
            {index + 1}
          </span>
          <LogoBox
            palette={palette}
            provider={conn.provider}
            size={32}
            dim={off}
          />
          <span style={{ flex: 1, minWidth: 0 }}>
            <span
              style={{
                display: 'block',
                fontSize: 14,
                fontWeight: 600,
                color: off ? palette.mute : palette.ink,
              }}
            >
              {name} · {conn.mode === 'api' ? 'API Key' : '平台订阅'}
            </span>
            {subtitle && (
              <span
                style={{
                  display: 'block',
                  marginTop: 2,
                  fontSize: 12,
                  color: palette.mute,
                }}
              >
                {subtitle}
              </span>
            )}
          </span>
          <Pill tone={stateTone} dot>
            {stateLabel}
          </Pill>
          {canEdit && (
            <Button
              type="link"
              size="small"
              loading={testing}
              disabled={testDisabled || off}
              style={{ padding: 0 }}
              onClick={(e) => {
                e.stopPropagation();
                onTest();
              }}
            >
              测试
            </Button>
          )}
          <Switch
            checked={draft.enabled}
            disabled={!canEdit}
            aria-label={`${fullLabel(conn)} 是否参与使用`}
            onClick={(_, e) => e.stopPropagation()}
            onChange={onToggle}
          />
        </div>
        {children && (
          <div style={{ padding: '0 14px 14px 76px' }}>{children}</div>
        )}
      </div>
      {note}
    </div>
  );
};

/**
 * 业务设置 → AI 设置：连接方式（本公司 API Key、平台订阅）按使用顺序排成一个列表，任意拖动调整顺序，
 * 每张卡片可单独开关与测试；出问题的方式自动跳过并暂停。所有修改只通过底部保存条保存。
 * 见 openspec ai-connection-failover、ai-connection-free-order 与原型 AI设置.op「V5」。
 */
const AiSettingsPage: React.FC = () => {
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const access = useAccess();
  const canEdit = !!(access as Record<string, unknown>)[
    'system:ai-config:edit'
  ];
  const [form] = Form.useForm<AiFormValues>();
  const values = Form.useWatch([], { form, preserve: true }) as
    | AiFormValues
    | undefined;

  const [config, setConfig] = useState<AiConfig>();
  const [loadError, setLoadError] = useState(false);
  const [saving, setSaving] = useState(false);
  /** 顺序与开关（未保存的修改） */
  const [cards, setCards] = useState<CardDraft[]>([]);
  /** 各连接方式的设置（未保存的修改），按 connKey */
  const [drafts, setDrafts] = useState<Record<string, AiFormValues>>({});
  const [selected, setSelected] = useState<string>();
  const [editingKey, setEditingKey] = useState<string>();
  const [testingKey, setTestingKey] = useState<string>();
  /** 测试结果连同发起测试时的配置指纹；表单改了就不再显示 */
  const [tests, setTests] = useState<
    Record<string, { fp: string; result: AiTestResult }>
  >({});
  /** 模型列表按配置缓存；换了卡片或 Key，之前的列表和加载中状态不再适用 */
  const [modelCache, setModelCache] = useState<{
    fp: string;
    list: AiModelList;
  }>();
  const [loadingFp, setLoadingFp] = useState<string>();
  const [loginCommands, setLoginCommands] =
    useState<Record<AiProvider, string>>();

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );

  /** 平台查看未登录的订阅时，取容器内的登录命令 */
  const loadLoginCommands = useCallback(async (c: AiConfig) => {
    if (
      !c.platform ||
      !c.connections.some(
        (x) => x.mode === 'subscription' && x.state === 'logged_out',
      )
    ) {
      return;
    }
    try {
      setLoginCommands((await getAiSubscriptionStatus()).loginCommands);
    } catch {
      setLoginCommands(undefined);
    }
  }, []);

  /** 重新加载并丢弃未保存的修改（首次进入、保存后、撤销） */
  const load = useCallback(
    async (keepSelected?: string) => {
      setLoadError(false);
      try {
        const c = await getAiConfig();
        const nextDrafts: Record<string, AiFormValues> = {};
        for (const x of c.connections) {
          nextDrafts[connKey(x)] = connFormValues(x, c.defaultModels);
        }
        const keys = c.connections.map(connKey);
        const sel =
          keepSelected && keys.includes(keepSelected) ? keepSelected : keys[0];
        setConfig(c);
        setCards(toCards(c));
        setDrafts(nextDrafts);
        setSelected(sel);
        setEditingKey(undefined);
        setModelCache(undefined);
        if (sel) form.setFieldsValue(nextDrafts[sel]);
        loadLoginCommands(c);
      } catch {
        setLoadError(true);
      }
    },
    [form, loadLoginCommands],
  );

  /** 只刷新状态（如测试通过后解除暂停），保留未保存的修改 */
  const refreshStates = async () => {
    try {
      setConfig(await getAiConfig());
    } catch {
      // 请求层已提示
    }
  };

  useEffect(() => {
    load();
  }, [load]);

  const select = (key: string) => {
    if (key === selected) return;
    setSelected(key);
    setEditingKey(undefined);
    setModelCache(undefined);
    form.setFieldsValue(drafts[key]);
  };

  const currentModelsFp = values ? modelsFingerprint(values) : '';
  const models =
    modelCache && modelCache.fp === currentModelsFp
      ? modelCache.list
      : undefined;
  const modelsLoading = !!loadingFp && loadingFp === currentModelsFp;

  const fetchModels = async () => {
    const sent = form.getFieldsValue(true) as AiFormValues;
    const fp = modelsFingerprint(sent);
    if (!canEdit || models || loadingFp === fp) return;
    setLoadingFp(fp);
    try {
      setModelCache({ fp, list: await getAiModels(toPayload(sent)) });
    } catch {
      setModelCache({
        fp,
        list: {
          models: [],
          source: 'defaults',
          error: '获取模型列表失败，可直接输入模型名',
        },
      });
    } finally {
      setLoadingFp((cur) => (cur === fp ? undefined : cur));
    }
  };

  const runTest = async (key: string) => {
    const sent = drafts[key];
    if (!sent) return;
    const fp = testFingerprint(sent);
    setTestingKey(key);
    try {
      const r = await testAiConfig(toPayload(sent));
      setTests((t) => ({ ...t, [key]: { fp, result: r } }));
      if (r.ok) {
        message.success('连接正常');
        refreshStates();
      }
    } catch {
      // 请求层已提示错误原因
    } finally {
      setTestingKey(undefined);
    }
  };

  const doSave = async () => {
    if (!config) return;
    setSaving(true);
    try {
      await saveAiConfig(toSavePayload(cards, drafts, config));
      message.success('已保存，之后提交的 AI 任务按新的设置使用');
      setTests({});
      await load(selected);
    } finally {
      setSaving(false);
    }
  };

  const onSave = () => {
    if (!config) return;
    const typed = config.connections.filter(
      (c) => c.mode === 'api' && (drafts[connKey(c)]?.apiKey ?? '').trim(),
    );
    if (!typed.length) {
      doSave();
      return;
    }
    const replacing = typed.some((c) => c.hasKey);
    modal.confirm({
      title: replacing ? '确认更换 API Key？' : '确认保存 API Key？',
      content: `${typed.map(fullLabel).join('、')}：${
        replacing
          ? '保存后原来的 Key 不再使用，之后提交的 AI 任务都按新 Key 计费。'
          : '之后提交的 AI 任务会使用这个 Key，费用记在该 Key 所属的账号上。'
      }`,
      okText: '确认保存',
      cancelText: '取消',
      onOk: doSave,
    });
  };

  if (loadError) {
    return (
      <>
        <PageHeader title="AI 设置" />
        <Card>
          <Result
            status="warning"
            title="AI 设置加载失败"
            subTitle="请检查网络后重试"
            extra={
              <Button icon={<RotateCw size="1em" />} onClick={() => load()}>
                重试
              </Button>
            }
          />
        </Card>
      </>
    );
  }
  if (!config) {
    return (
      <>
        <PageHeader title="AI 设置" />
        <Card>
          <Skeleton active paragraph={{ rows: 2 }} />
        </Card>
        <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
          <Col xs={24} xl={12}>
            <Card>
              <Skeleton active paragraph={{ rows: 6 }} />
            </Card>
          </Col>
          <Col xs={24} xl={12}>
            <Card>
              <Skeleton active paragraph={{ rows: 6 }} />
            </Card>
          </Col>
        </Row>
      </>
    );
  }

  const connOf = (key: string) =>
    config.connections.find((c) => connKey(c) === key);
  const sel = selected ? connOf(selected) : undefined;
  const pendingOrder = orderChanged(cards, config);
  const changedProfiles = config.connections.filter(
    (c) =>
      !c.settingsReadonly &&
      drafts[connKey(c)] &&
      isDirty(drafts[connKey(c)], connFormValues(c, config.defaultModels)),
  );
  const dirty = pendingOrder || changedProfiles.length > 0;

  // —— 顶部状态：按已保存的顺序描述新任务怎样使用 ——
  const summary = summarize(config, pendingOrder);
  const TONE_ICON: Record<
    SummaryTone,
    { icon: React.ReactNode; fg: string; bg: string }
  > = {
    ok: {
      icon: <CircleCheck size="1em" />,
      fg: palette.green,
      bg: palette.greenSoft,
    },
    warn: {
      icon: <TriangleAlert size="1em" />,
      fg: palette.orange,
      bg: palette.orangeSoft,
    },
    bad: {
      icon: <CircleAlert size="1em" />,
      fg: palette.red,
      bg: palette.redSoft,
    },
    idle: { icon: <Bot size="1em" />, fg: palette.mute, bg: palette.inset },
  };
  const toneIcon = TONE_ICON[summary.tone];
  const chainColor = (c: AiConnection) => {
    if (usable(c, config.platform)) return palette.ink;
    if (c.enabled && c.pausedReason) return palette.orange;
    if (c.enabled && (c.state === 'logged_out' || c.state === 'unavailable'))
      return palette.red;
    return palette.faint;
  };

  const statusCard = (
    <Card style={{ marginBottom: 16 }}>
      <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
        <span
          style={{
            display: 'grid',
            placeItems: 'center',
            flex: 'none',
            width: 48,
            height: 48,
            borderRadius: 12,
            fontSize: 22,
            color: toneIcon.fg,
            background: toneIcon.bg,
          }}
        >
          {toneIcon.icon}
        </span>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 18, fontWeight: 600, color: palette.ink }}>
            {summary.title}
          </div>
          <div style={{ marginTop: 6, fontSize: 13, color: palette.mute }}>
            {summary.desc}
          </div>
          {config.connections.length > 0 && (
            <div
              data-testid="ai-chain"
              style={{
                display: 'flex',
                flexWrap: 'wrap',
                alignItems: 'center',
                gap: 8,
                marginTop: 12,
              }}
            >
              {config.connections.map((c, i) => {
                const dim = chainColor(c) === palette.faint;
                return (
                  <React.Fragment key={connKey(c)}>
                    {i > 0 && (
                      <ChevronRight
                        size="1em"
                        style={{ fontSize: 10, color: palette.faint }}
                      />
                    )}
                    <span
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 6,
                        height: 30,
                        padding: '0 12px',
                        borderRadius: 15,
                        fontSize: 13,
                        background: palette.inset,
                        color: chainColor(c),
                      }}
                    >
                      <span style={{ color: palette.mute, fontSize: 12 }}>
                        {i + 1}
                      </span>
                      <span
                        style={{
                          opacity: dim ? 0.5 : 1,
                          display: 'inline-flex',
                        }}
                      >
                        {React.createElement(PROVIDER_META[c.provider].Logo, {
                          size: 16,
                        })}
                      </span>
                      {shortLabel(c)}
                    </span>
                  </React.Fragment>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </Card>
  );

  // —— 左：连接方式（一个可拖动的列表） ——
  const testOf = (key: string) => {
    const t = tests[key];
    return t && drafts[key] && t.fp === testFingerprint(drafts[key])
      ? t.result
      : undefined;
  };

  const keyEditor = (c: AiConnection) => (
    <Form.Item
      name="apiKey"
      style={{ marginBottom: 0 }}
      extra={
        <span style={{ fontSize: 12 }}>
          还没有 Key？
          <a
            href={PROVIDER_META[c.provider].console}
            target="_blank"
            rel="noreferrer"
          >
            去 {PROVIDER_META[c.provider].consoleName} 控制台创建{' '}
            <ExternalLink size="1em" />
          </a>
        </span>
      }
    >
      {/* 普通文本框：Key 很长，粘贴后能看到内容便于核对；页面不回显已保存的 Key */}
      <Input
        autoComplete="off"
        spellCheck={false}
        autoFocus={editingKey === connKey(c)}
        prefix={<KeyRound size="1em" style={{ color: palette.faint }} />}
        placeholder={`粘贴 ${PROVIDER_META[c.provider].name} 的 API Key`}
        suffix={
          c.hasKey && (
            <Button
              type="link"
              size="small"
              style={{ padding: 0, height: 'auto' }}
              onClick={() => {
                form.setFieldValue('apiKey', '');
                setDrafts((d) => ({
                  ...d,
                  [connKey(c)]: { ...d[connKey(c)], apiKey: '' },
                }));
                setEditingKey(undefined);
              }}
            >
              取消
            </Button>
          )
        }
      />
    </Form.Item>
  );

  const renderCard = (draft: CardDraft) => {
    const key = connKey(draft);
    const c = connOf(key);
    if (!c) return null;
    const index = cards.findIndex((x) => connKey(x) === key);
    const isSel = key === selected;
    const typedKey = !!(drafts[key]?.apiKey ?? '').trim();
    const showKeyEditor =
      canEdit && isSel && c.mode === 'api' && (editingKey === key || !c.hasKey);
    const result = testOf(key);
    const failed =
      result && !result.ok ? result.items.filter((i) => !i.ok) : [];

    let subtitle: React.ReactNode;
    if (c.mode === 'api') {
      subtitle = c.hasKey ? (
        <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
          <span style={{ fontFamily: 'JetBrains Mono, monospace' }}>
            {c.apiKeyMasked}
          </span>
          {canEdit && isSel && !showKeyEditor && (
            <Button
              type="link"
              size="small"
              style={{ padding: 0, height: 'auto', fontSize: 12 }}
              onClick={(e) => {
                e.stopPropagation();
                setEditingKey(key);
              }}
            >
              更换
            </Button>
          )}
        </span>
      ) : (
        '填写 Key 后参与使用'
      );
    }

    const note =
      draft.enabled && c.pausedReason ? (
        <div
          style={{
            display: 'flex',
            justifyContent: 'center',
            gap: 6,
            padding: '6px 0 2px',
            fontSize: 12,
            color: palette.orange,
          }}
        >
          <Clock size="1em" style={{ marginTop: 3 }} />
          {`${reasonLabel(c.pausedReason)}，${hm(c.pausedUntil)} 自动恢复；${
            c.mode === 'api'
              ? c.pausedReason === 'auth'
                ? 'Key 可能已失效，建议更换'
                : '更换 Key 或测试通过后立即恢复'
              : '测试通过后立即恢复'
          }`}
        </div>
      ) : failed.length ? (
        <div
          style={{
            padding: '6px 0 2px 76px',
            fontSize: 12,
            color: palette.red,
          }}
        >
          {failed.map((i) => `${i.name}：${i.message}`).join('；')}
        </div>
      ) : null;

    return (
      <ConnectionCard
        key={key}
        conn={c}
        draft={draft}
        index={index}
        selected={isSel}
        canEdit={canEdit}
        draggable={canEdit && cards.length > 1}
        testing={testingKey === key}
        testDisabled={c.mode === 'api' && !c.hasKey && !typedKey}
        subtitle={subtitle}
        note={note}
        onSelect={() => select(key)}
        onToggle={(enabled) =>
          setCards((cs) =>
            cs.map((x) => (connKey(x) === key ? { ...x, enabled } : x)),
          )
        }
        onTest={() => runTest(key)}
      >
        {showKeyEditor ? (
          keyEditor(c)
        ) : config.platform &&
          isSel &&
          c.mode === 'subscription' &&
          c.state === 'logged_out' &&
          loginCommands ? (
          <div style={{ fontSize: 12, color: palette.mute }}>
            在服务器执行以下命令登录：
            <Typography.Paragraph
              code
              copyable
              style={{ margin: '6px 0 0', fontSize: 12 }}
            >
              {loginCommands[c.provider]}
            </Typography.Paragraph>
          </div>
        ) : null}
      </ConnectionCard>
    );
  };

  const onDragEnd = ({ active, over }: DragEndEvent) => {
    if (!over) return;
    setCards((cs) => moveCard(cs, String(active.id), String(over.id)));
  };

  const connectCard = (
    <Card style={{ height: '100%' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          marginBottom: 6,
        }}
      >
        <span style={{ fontSize: 16, fontWeight: 600, color: palette.ink }}>
          连接方式
        </span>
        {canEdit && (
          <span
            style={{ marginLeft: 'auto', fontSize: 12, color: palette.mute }}
          >
            拖动卡片调整顺序
          </span>
        )}
      </div>
      <div style={{ fontSize: 12, color: palette.mute, marginBottom: 14 }}>
        按顺序使用；排在前面的出问题时自动改用下一个，改用 API Key 时按该 Key
        计费
      </div>
      {/* 一个拖动区域：API Key 与平台订阅可以互相穿插；按重叠判断放置位置 */}
      <DndContext
        id="ai-connections"
        sensors={sensors}
        collisionDetection={rectIntersection}
        onDragEnd={onDragEnd}
      >
        <SortableContext
          items={cards.map(connKey)}
          strategy={verticalListSortingStrategy}
        >
          <div style={{ display: 'grid', gap: 10 }}>
            {cards.map(renderCard)}
          </div>
        </SortableContext>
      </DndContext>
    </Card>
  );

  // —— 右：选中卡片的模型与用量 ——
  const provider = sel?.provider ?? 'claude';
  const meta = PROVIDER_META[provider];
  const modelField = (field: ModelField, label: string, hint: string) => {
    const fallback = defaultModel(config.defaultModels, provider, field);
    return (
      <Form.Item
        name={field === 'parse' ? 'parseModel' : 'translateModel'}
        label={<FieldLabel title={label} hint={hint} />}
      >
        <AutoComplete
          allowClear
          placeholder={`可选择或输入模型名，推荐 ${fallback}`}
          options={modelOptions(models, fallback).map((o) => ({
            value: o.value,
            label: (
              <span
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  gap: 8,
                }}
              >
                {o.value}
                <span style={{ display: 'inline-flex', gap: 4 }}>
                  {o.tags.map((t) => (
                    <Pill key={t} tone={t === '推荐' ? 'accent' : 'gray'}>
                      {t}
                    </Pill>
                  ))}
                </span>
              </span>
            ),
          }))}
          onOpenChange={(open) => open && fetchModels()}
          notFoundContent={modelsLoading ? <Spin size="small" /> : null}
          popupRender={(menu) => (
            <>
              {models?.error ? (
                <Typography.Text
                  type="warning"
                  style={{ display: 'block', padding: '4px 12px' }}
                >
                  {models.error}
                </Typography.Text>
              ) : (
                !modelsLoading &&
                models && (
                  <Typography.Text
                    type="secondary"
                    style={{
                      display: 'block',
                      padding: '4px 12px',
                      fontSize: 12,
                    }}
                  >
                    {models.source === 'provider'
                      ? `${meta.consoleName} 返回的模型`
                      : sel?.mode === 'subscription'
                        ? '推荐模型'
                        : '填写 API Key 后可获取完整的模型列表'}
                  </Typography.Text>
                )
              )}
              {menu}
              <div
                style={{
                  padding: '6px 12px',
                  fontSize: 12,
                  color: palette.mute,
                  borderTop: `1px solid ${palette.hairline}`,
                }}
              >
                <CornerDownLeft size="1em" /> 列表里没有的模型可直接输入名称
              </div>
            </>
          )}
        />
      </Form.Item>
    );
  };

  const effortLabel = (v?: string) =>
    EFFORT_OPTIONS.find((o) => o.value === (v || DEFAULT_EFFORT))?.label ??
    '中';

  const pausedBanner = sel?.pausedReason &&
    cards.find((x) => connKey(x) === selected)?.enabled && (
      <div
        style={{
          display: 'flex',
          gap: 10,
          padding: '12px 14px',
          marginBottom: 16,
          borderRadius: 10,
          background: palette.orangeSoft,
          fontSize: 13,
          color: palette.sub,
        }}
      >
        <Clock size="1em" style={{ color: palette.orange, marginTop: 3 }} />
        <div>
          <div style={{ fontWeight: 600, color: palette.orange }}>
            暂停中 · {reasonLabel(sel.pausedReason)}
          </div>
          <div style={{ marginTop: 4 }}>
            新任务暂时跳过它，{hm(sel.pausedUntil)} 自动恢复；
            {sel.mode === 'api'
              ? '充值或更换 Key 后点「测试」可立即恢复。'
              : '登录或额度恢复后点「测试」可立即恢复。'}
          </div>
        </div>
      </div>
    );

  const modelsTitle = (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        marginBottom: 20,
      }}
    >
      <span style={{ fontSize: 16, fontWeight: 600, color: palette.ink }}>
        模型与用量{sel ? ` · ${fullLabel(sel)}` : ''}
      </span>
      {sel?.settingsReadonly && (
        <span style={{ marginLeft: 'auto' }}>
          <Pill tone="gray">
            <Lock size="1em" /> 由平台设置
          </Pill>
        </span>
      )}
      {!sel?.settingsReadonly && sel?.mode === 'subscription' && (
        <span style={{ fontSize: 12, color: palette.mute }}>
          获授权的公司共用
        </span>
      )}
    </div>
  );

  // 只读时没有表单项在监听，直接读这张卡片的设置
  const shown = (selected && drafts[selected]) || values;
  const readonlyModels = (
    <Card style={{ height: '100%' }}>
      {modelsTitle}
      {pausedBanner}
      {[
        ['解析模型', '解析询盘、拆分订单时使用', shown?.parseModel],
        ['解析思考强度', '', effortLabel(shown?.parseEffort)],
        ['翻译模型', '翻译型号描述、音译联系人名时使用', shown?.translateModel],
        ['翻译思考强度', '', effortLabel(shown?.translateEffort)],
        [
          '每次解析最多搜索',
          '每次调用的上限，大文件分批解析时每批各自计算',
          `${shown?.maxWebSearches ?? 20} 次`,
        ],
      ].map(([k, d, v], i) => (
        <React.Fragment key={k}>
          {(i === 2 || i === 4) && <Divider palette={palette} />}
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              padding: '10px 0',
            }}
          >
            <div>
              <div
                style={{ fontSize: 13, fontWeight: 600, color: palette.ink }}
              >
                {k}
              </div>
              {d && (
                <div
                  style={{ fontSize: 12, color: palette.mute, marginTop: 2 }}
                >
                  {d}
                </div>
              )}
            </div>
            <span style={{ fontSize: 14, fontWeight: 600, color: palette.ink }}>
              {v}
            </span>
          </div>
        </React.Fragment>
      ))}
    </Card>
  );

  const editableModels = (
    <Card style={{ height: '100%' }}>
      {modelsTitle}
      {pausedBanner}
      {modelField(
        'parse',
        '解析模型',
        '解析询盘、拆分订单时使用，建议选能力最强的',
      )}
      <Form.Item name="parseEffort" label="解析思考强度">
        <EffortField disabled={!canEdit} />
      </Form.Item>
      <Divider palette={palette} />
      {modelField(
        'translate',
        '翻译模型',
        '翻译型号描述、音译联系人名时使用，选快且便宜的即可',
      )}
      <Form.Item name="translateEffort" label="翻译思考强度">
        <EffortField disabled={!canEdit} />
      </Form.Item>
      <Divider palette={palette} />
      <div
        style={{
          display: 'flex',
          gap: 16,
          alignItems: 'center',
          justifyContent: 'space-between',
          marginTop: 16,
        }}
      >
        <div>
          <div style={{ fontSize: 13, fontWeight: 600, color: palette.ink }}>
            每次解析最多搜索
          </div>
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
            每次调用最多搜索几次；大文件分批解析时每批各自计算。越多越准、费用越高，可填{' '}
            <span style={{ whiteSpace: 'nowrap' }}>1–100</span>
          </div>
        </div>
        <Form.Item name="maxWebSearches" noStyle>
          <InputNumber
            mode="spinner"
            min={1}
            max={100}
            precision={0}
            style={{ width: 130, flex: 'none' }}
          />
        </Form.Item>
      </div>
    </Card>
  );

  // —— 底部保存条：只在有修改时出现，页面唯一的保存入口 ——
  const movedAt = cards.findIndex(
    (c, i) => connKey(c) !== connKey(config.connections[i] ?? c),
  );
  const saveHint =
    movedAt >= 0
      ? `连接方式顺序已调整：${fullLabel(cards[movedAt])} 排到第 ${movedAt + 1} 位，保存后新任务按新顺序使用`
      : '有修改未保存';
  const saveBar = canEdit && dirty && (
    <div
      style={{
        position: 'sticky',
        bottom: 16,
        zIndex: 4,
        marginTop: 16,
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: 12,
        padding: '12px 24px',
        borderRadius: 12,
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
        boxShadow: POPUP_SHADOW,
        animation: 'ai-save-bar-in 200ms ease-out',
      }}
    >
      <style>
        {
          '@keyframes ai-save-bar-in { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: none; } }'
        }
      </style>
      <span
        style={{
          display: 'inline-flex',
          gap: 8,
          alignItems: 'center',
          fontSize: 13,
          color: palette.ink,
        }}
      >
        <Info size="1em" style={{ color: palette.link }} />
        {saveHint}
      </span>
      <span style={{ display: 'inline-flex', gap: 8 }}>
        <Button disabled={saving} onClick={() => load(selected)}>
          取消
        </Button>
        <Button type="primary" loading={saving} onClick={onSave}>
          保存
        </Button>
      </span>
    </div>
  );

  return (
    <>
      <PageHeader
        title="AI 设置"
        help="询盘解析、型号描述翻译、联系人名音译使用的 AI 服务"
      />
      {statusCard}
      <Form
        form={form}
        layout="vertical"
        disabled={!canEdit}
        onValuesChange={() => {
          if (!selected) return;
          const all = form.getFieldsValue(true) as AiFormValues;
          setDrafts((d) => ({ ...d, [selected]: { ...d[selected], ...all } }));
        }}
      >
        <Row gutter={[16, 16]}>
          <Col xs={24} xl={12}>
            {connectCard}
          </Col>
          <Col xs={24} xl={12}>
            {sel?.settingsReadonly ? readonlyModels : editableModels}
          </Col>
        </Row>
        {saveBar}
      </Form>
    </>
  );
};

export default AiSettingsPage;
