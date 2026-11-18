import type {
  AiConfig,
  AiConfigPayload,
  AiConnection,
  AiMode,
  AiModelList,
  AiProvider,
  AiSettingsPayload,
  FailoverReason,
} from './service';

/** AI 设置表单的取值；apiKey 为本次新填写的 Key（空表示不修改） */
export interface AiFormValues {
  mode?: AiConfigPayload['mode'];
  provider?: AiConfigPayload['provider'];
  apiKey?: string;
  parseModel?: string;
  translateModel?: string;
  parseEffort?: string;
  translateEffort?: string;
  maxWebSearches?: number;
}

export type ModelField = 'parse' | 'translate';

/** 思考强度；未填写时后端按 medium 处理，界面上显示为「中」 */
export const DEFAULT_EFFORT = 'medium';
export const EFFORT_OPTIONS = [
  { value: 'low', label: '低' },
  { value: 'medium', label: '中' },
  { value: 'high', label: '高' },
  { value: 'xhigh', label: '很高' },
  { value: 'max', label: '最高' },
];

/** 表单值 → 接口入参：去掉首尾空格；Key 没填就不带，后端沿用已保存的 Key */
export function toPayload(values: AiFormValues): AiConfigPayload {
  const text = (v?: string) => (v ?? '').trim();
  const payload: AiConfigPayload = {
    mode: values.mode ?? 'api',
    provider: values.provider ?? 'claude',
    parseModel: text(values.parseModel),
    translateModel: text(values.translateModel),
    parseEffort: values.parseEffort ?? '',
    translateEffort: values.translateEffort ?? '',
    maxWebSearches: values.maxWebSearches ?? 20,
  };
  const key = text(values.apiKey);
  if (key) payload.apiKey = key;
  return payload;
}

/**
 * 测试结果属于哪一份配置：方式、服务商、模型、思考强度、搜索次数、本次填写的 Key 任一变化，
 * 之前的测试结果就不再适用；迟到的回包也按它核对归属（UI-019）
 */
export const testFingerprint = (v: AiFormValues) =>
  JSON.stringify(toPayload(v));

/** 模型列表属于哪一份配置：方式、服务商、本次填写的 Key（UI-020） */
export const modelsFingerprint = (v: AiFormValues) => {
  const p = toPayload(v);
  return JSON.stringify([p.mode, p.provider, p.apiKey ?? '']);
};

export const PROVIDERS: AiProvider[] = ['claude', 'openai'];

/** 连接方式的标识，如 api:claude */
export const connKey = (c: { mode: AiMode; provider: AiProvider }) =>
  `${c.mode}:${c.provider}`;

const PROVIDER_NAME: Record<AiProvider, string> = {
  claude: 'Claude',
  openai: 'OpenAI',
};

/** 短名，用于顶部顺序：Claude API、OpenAI 订阅 */
export const shortLabel = (c: { mode: AiMode; provider: AiProvider }) =>
  `${PROVIDER_NAME[c.provider]} ${c.mode === 'api' ? 'API' : '订阅'}`;

/** 全名：Claude API Key、OpenAI 平台订阅 */
export const fullLabel = (c: { mode: AiMode; provider: AiProvider }) =>
  `${PROVIDER_NAME[c.provider]} ${c.mode === 'api' ? 'API Key' : '平台订阅'}`;

/** 暂停与切换原因 */
export const REASON_LABEL: Record<FailoverReason, string> = {
  auth: '认证失败',
  quota: '额度用完',
  rate_limit: '请求过多',
  provider_error: '服务商故障',
  timeout: '调用超时',
  not_logged_in: '订阅未登录',
  usage_limit: '达到用量上限',
};

export const reasonLabel = (r?: string | null) =>
  (r && REASON_LABEL[r as FailoverReason]) || '不可用';

/** 名称后接中文：英文结尾留一个空格（Claude API Key 认证失败），中文结尾直接相连（平台订阅不可用） */
const then = (label: string, text: string) =>
  `${label}${/[A-Za-z0-9]$/.test(label) ? ' ' : ''}${text}`;

/** 北京时间的时刻：2026-11-17T14:32:00 → 14:32 */
export const hm = (t?: string | null) => (t ? t.slice(11, 16) : '');

/** 某一种连接方式已保存的设置 → 表单值（Key 不回填；模型没填时预填该服务商的推荐模型） */
export function connFormValues(
  c: AiConnection,
  defaults: AiConfig['defaultModels'],
): AiFormValues {
  return {
    mode: c.mode,
    provider: c.provider,
    apiKey: '',
    parseModel: c.parseModel || defaultModel(defaults, c.provider, 'parse'),
    translateModel:
      c.translateModel || defaultModel(defaults, c.provider, 'translate'),
    parseEffort: c.parseEffort || undefined,
    translateEffort: c.translateEffort || undefined,
    maxWebSearches: c.maxWebSearches ?? 20,
  };
}

/** 页面上可调整的顺序与开关 */
export interface CardDraft {
  mode: AiMode;
  provider: AiProvider;
  enabled: boolean;
}

export const toCards = (config: AiConfig): CardDraft[] =>
  config.connections.map(({ mode, provider, enabled }) => ({
    mode,
    provider,
    enabled,
  }));

/** 顺序或开关是否与已保存的不同 */
export const orderChanged = (cards: CardDraft[], config: AiConfig) =>
  JSON.stringify(cards) !== JSON.stringify(toCards(config));

/** 拖动：把 active 移到 over 的位置；API Key 与平台订阅在一个列表里，可以互相穿插 */
export function moveCard(
  cards: CardDraft[],
  activeKey: string,
  overKey: string,
): CardDraft[] {
  const from = cards.findIndex((c) => connKey(c) === activeKey);
  const to = cards.findIndex((c) => connKey(c) === overKey);
  if (from < 0 || to < 0 || from === to) return cards;
  const next = [...cards];
  const [moved] = next.splice(from, 1);
  next.splice(to, 0, moved);
  return next;
}

/** 保存的入参：全部顺序与开关，加上改过设置的连接方式（租户查看的平台订阅不提交） */
export function toSavePayload(
  cards: CardDraft[],
  drafts: Record<string, AiFormValues>,
  config: AiConfig,
): AiSettingsPayload {
  const profiles = config.connections
    .filter((c) => !c.settingsReadonly)
    .filter((c) => {
      const d = drafts[connKey(c)];
      return d && isDirty(d, connFormValues(c, config.defaultModels));
    })
    .map((c) => toPayload(drafts[connKey(c)]));
  return {
    order: cards.map(({ mode, provider, enabled }) => ({
      mode,
      provider,
      enabled,
    })),
    profiles,
  };
}

/** 这张卡片现在能不能被新任务使用：开启、未暂停、已配置或已登录（平台查不到登录状态时照常使用保存过的订阅） */
export function usable(c: AiConnection, platform: boolean): boolean {
  if (!c.enabled || c.pausedReason) return false;
  if (c.state === 'unknown')
    return platform && c.mode === 'subscription' && c.saved;
  return (
    c.state === 'ready' || c.state === 'logged_in' || c.state === 'available'
  );
}

/** 卡片的状态标签 */
export function cardState(
  c: AiConnection,
  enabled: boolean,
): [string, 'green' | 'red' | 'orange' | 'gray'] {
  if (!enabled) return ['已关闭', 'gray'];
  if (c.pausedReason) return ['暂停中', 'orange'];
  const labels: Record<
    AiConnection['state'],
    [string, 'green' | 'red' | 'gray']
  > = {
    ready: ['已配置', 'green'],
    no_key: ['未配置', 'gray'],
    logged_in: ['已登录', 'green'],
    logged_out: ['未登录', 'red'],
    available: ['可用', 'green'],
    unavailable: ['不可用', 'red'],
    unknown: ['状态未知', 'gray'],
  };
  return labels[c.state];
}

export type SummaryTone = 'ok' | 'warn' | 'bad' | 'idle';

/** 顶部状态：按已保存的顺序与状态描述新任务会怎样使用 */
export function summarize(
  config: AiConfig,
  pendingOrder: boolean,
): { tone: SummaryTone; title: string; desc: string } {
  const list = config.connections;
  const ok = list.filter((c) => usable(c, config.platform));
  const paused = list.filter((c) => c.enabled && c.pausedReason);
  const configured = list.some(
    (c) => c.enabled && (c.mode === 'subscription' || c.hasKey),
  );
  if (!ok.length) {
    if (!configured) {
      return {
        tone: 'idle',
        title: '还没有配置 AI',
        desc: '填写 API Key 后即可使用。',
      };
    }
    return {
      tone: 'bad',
      title: 'AI 暂不可用',
      desc: '询盘解析、翻译等 AI 操作会提示暂不可用。处理下面任意一张卡片即可恢复',
    };
  }
  if (paused.length) {
    const p = paused[0];
    return {
      tone: 'warn',
      title: `AI 可用 · ${paused.length} 个连接方式暂停中`,
      desc: `${then(fullLabel(p), reasonLabel(p.pausedReason))}，新任务先用 ${fullLabel(ok[0])}，${hm(p.pausedUntil)} 后恢复按原顺序`,
    };
  }
  if (pendingOrder) {
    return {
      tone: 'ok',
      title: 'AI 可用',
      desc: '顺序调整后还没保存，新任务仍按原顺序使用',
    };
  }
  const off = list.filter((c) => !c.enabled);
  if (off.length) {
    return {
      tone: 'ok',
      title: 'AI 可用',
      desc: `只使用 ${ok.map(fullLabel).join('、')}；${off.map(fullLabel).join('、')} 已关闭，不参与`,
    };
  }
  return {
    tone: 'ok',
    title: 'AI 可用',
    desc: '新任务按下面的顺序使用，出问题时自动换下一个',
  };
}

/** 当前服务商在该字段的默认模型 */
export function defaultModel(
  defaults: AiConfig['defaultModels'] | undefined,
  provider: string | undefined,
  field: ModelField,
): string {
  return defaults?.[provider ?? 'claude']?.[field] ?? '';
}

/**
 * 下拉选项：服务商默认模型标「推荐」、账号默认（Codex 订阅）标「账号默认」；
 * 推荐模型始终在列表中且排在最前，其余按名称排序。
 */
export function modelOptions(
  list: AiModelList | undefined,
  fieldDefault: string,
): { value: string; tags: string[] }[] {
  const ids = new Set((list?.models ?? []).map((m) => m.id));
  if (fieldDefault) ids.add(fieldDefault);
  const accountDefaults = new Set(
    (list?.models ?? []).filter((m) => m.defaultModel).map((m) => m.id),
  );
  return [...ids]
    .sort((a, b) =>
      a === fieldDefault ? -1 : b === fieldDefault ? 1 : a.localeCompare(b),
    )
    .map((id) => ({
      value: id,
      tags: [
        id === fieldDefault ? '推荐' : '',
        accountDefaults.has(id) ? '账号默认' : '',
      ].filter(Boolean),
    }));
}

/** 表单与已保存配置是否一致（新填了 Key 也算有修改），用于底部「有修改未保存」 */
export function isDirty(values: AiFormValues, saved: AiFormValues): boolean {
  const a = toPayload(values);
  const b = toPayload(saved);
  const effort = (v: string) => v || DEFAULT_EFFORT;
  return (
    !!a.apiKey ||
    a.mode !== b.mode ||
    a.provider !== b.provider ||
    a.parseModel !== b.parseModel ||
    a.translateModel !== b.translateModel ||
    effort(a.parseEffort) !== effort(b.parseEffort) ||
    effort(a.translateEffort) !== effort(b.translateEffort) ||
    a.maxWebSearches !== b.maxWebSearches
  );
}

/** 执行中的一次切换（原因只有分类码） */
export interface AiFailover {
  connection: string;
  reason: string;
  at?: number;
}

const labelOf = (connection: string) => {
  const [mode, provider] = connection.split(':');
  return provider === 'claude' || provider === 'openai'
    ? fullLabel({
        mode: mode === 'subscription' ? 'subscription' : 'api',
        provider,
      })
    : connection;
};

/** 自动切换的说明：Claude API Key 额度用完，改用 OpenAI API Key 完成 */
export function describeFailovers(
  used: string | null | undefined,
  failovers: AiFailover[],
): string {
  const failed = failovers
    .map((f) => then(labelOf(f.connection), reasonLabel(f.reason)))
    .join('、');
  return used ? `${failed}，改用 ${then(labelOf(used), '完成')}` : failed;
}
