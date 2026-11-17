import { request } from '@umijs/max';

/** 业务设置 → AI 设置：当前租户的连接方式（顺序、开关、各自的设置），见 openspec ai-platform/tenant-ai-settings 与 ai-connection-failover */

export type AiMode = 'api' | 'subscription';
export type AiProvider = 'claude' | 'openai';

export type ConnectionState =
  | 'ready'
  | 'no_key'
  | 'logged_in'
  | 'logged_out'
  | 'available'
  | 'unavailable'
  | 'unknown';

/** 切换与暂停的原因分类码，见 openspec ai-connection-failover */
export type FailoverReason =
  | 'auth'
  | 'quota'
  | 'rate_limit'
  | 'provider_error'
  | 'timeout'
  | 'not_logged_in'
  | 'usage_limit';

/** 一张连接方式卡片：顺序、开关、状态与这一种的设置；Key 只有是否已填写与脱敏值 */
export interface AiConnection {
  mode: AiMode;
  provider: AiProvider;
  /** 在整体顺序中的位置，从 1 开始（API Key 组在前） */
  order: number;
  enabled: boolean;
  /** ready / no_key（API）；logged_in / logged_out（平台查看订阅）；available / unavailable（租户查看订阅）；unknown */
  state: ConnectionState;
  /** 暂停原因，没有暂停为空 */
  pausedReason?: FailoverReason | null;
  /** 暂停到何时（北京时间） */
  pausedUntil?: string | null;
  /** 是否保存过这一种的设置 */
  saved: boolean;
  hasKey: boolean;
  apiKeyMasked: string;
  parseModel: string;
  translateModel: string;
  parseEffort: string;
  translateEffort: string;
  maxWebSearches: number;
  /** 模型设置只读（租户查看平台订阅） */
  settingsReadonly: boolean;
}

/** 当前租户的 AI 配置：按使用顺序排好的连接方式（API Key 组在前、平台订阅组在后） */
export interface AiConfig {
  platform: boolean;
  /** 能否使用平台订阅（平台始终可以；其他公司看套餐授权）；不能时没有订阅卡片 */
  subscriptionAllowed: boolean;
  connections: AiConnection[];
  /** provider → { parse, translate } */
  defaultModels: Record<string, { parse: string; translate: string }>;
}

/** 保存、测试连接、获取模型列表共用：mode + provider 是选中的连接方式，其余是这一种的设置；apiKey 留空沿用该服务商已保存的 Key */
export interface AiConfigPayload {
  mode: AiMode;
  provider: AiProvider;
  apiKey?: string;
  parseModel: string;
  translateModel: string;
  parseEffort: string;
  translateEffort: string;
  maxWebSearches: number;
}

/** 底部保存条一次保存的全部修改：连接方式的顺序与开关，以及改过设置的连接方式 */
export interface AiSettingsPayload {
  order: { mode: AiMode; provider: AiProvider; enabled: boolean }[];
  profiles: AiConfigPayload[];
}

export interface AiTestResult {
  ok: boolean;
  items: { name: string; ok: boolean; message: string }[];
}

export interface AiModelList {
  models: { id: string; defaultModel: boolean }[];
  /** provider：来自服务商或账号；defaults：只有默认模型 */
  source: 'provider' | 'defaults';
  error?: string | null;
}

export interface AiSubscriptionStatus {
  /** provider → 是否可用；查询失败为 null（未知） */
  available: Record<AiProvider, boolean | null>;
  /** 仅平台：provider → logged_in / logged_out / unknown */
  states?: Record<AiProvider, 'logged_in' | 'logged_out' | 'unknown'>;
  /** 仅平台：provider → 容器内登录命令 */
  loginCommands?: Record<AiProvider, string>;
}

const AI_BASE = '/api/v1/system/ai-config';

export const getAiConfig = (): Promise<AiConfig> =>
  request(AI_BASE, { method: 'GET' }).then((r) => r.data);

export const saveAiConfig = (data: AiSettingsPayload): Promise<void> =>
  request(AI_BASE, { method: 'PUT', data });

export const testAiConfig = (data: AiConfigPayload): Promise<AiTestResult> =>
  request(`${AI_BASE}/test`, { method: 'POST', data }).then((r) => r.data);

export const getAiModels = (data: AiConfigPayload): Promise<AiModelList> =>
  request(`${AI_BASE}/models`, { method: 'POST', data }).then((r) => r.data);

export const getAiSubscriptionStatus = (): Promise<AiSubscriptionStatus> =>
  request(`${AI_BASE}/subscription-status`, { method: 'GET' }).then(
    (r) => r.data,
  );
