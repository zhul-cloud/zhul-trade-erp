import type {
  AiConfig,
  AiConfigPayload,
  AiMode,
  AiModelList,
  AiProvider,
  AiSubscriptionStatus,
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

export const PROVIDERS: AiProvider[] = ['claude', 'openai'];

/** 某一种连接方式已保存的设置 → 表单值（Key 不回填；模型没填时预填该服务商的推荐模型） */
export function profileFormValues(
  config: AiConfig,
  mode: AiMode,
  provider: AiProvider,
): AiFormValues {
  const profile =
    mode === 'subscription'
      ? config.subscriptionProfiles?.[provider]
      : config.apiProfiles[provider];
  return {
    mode,
    provider,
    apiKey: '',
    parseModel:
      profile?.parseModel ||
      defaultModel(config.defaultModels, provider, 'parse'),
    translateModel:
      profile?.translateModel ||
      defaultModel(config.defaultModels, provider, 'translate'),
    parseEffort: profile?.parseEffort || undefined,
    translateEffort: profile?.translateEffort || undefined,
    maxWebSearches: profile?.maxWebSearches ?? 20,
  };
}

/** 已保存的当前选择 → 表单初始值 */
export function toFormValues(config: AiConfig): AiFormValues {
  return profileFormValues(config, config.mode, config.provider);
}

/**
 * 平台订阅某一家的状态：平台看登录状态（in / out），租户只看可用与否（avail / unavail）；
 * 查询失败或还没查到为 unknown。
 */
export type SubState = 'in' | 'out' | 'avail' | 'unavail' | 'unknown';
export function subscriptionState(
  status: AiSubscriptionStatus | undefined,
  provider: AiProvider,
  platform: boolean,
): SubState {
  const available = status?.available?.[provider];
  if (available === null || available === undefined) return 'unknown';
  if (platform) return available ? 'in' : 'out';
  return available ? 'avail' : 'unavail';
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

/** 测试连接的结果按「API Key / 解析模型 / 翻译模型」归位；没测到的项为 undefined */
export function testSlot(
  items: { name: string; ok: boolean; message: string }[],
  slot: 'API Key' | '解析模型' | '翻译模型',
) {
  return items.find((i) => i.name === slot || i.name.startsWith(`${slot} `));
}
