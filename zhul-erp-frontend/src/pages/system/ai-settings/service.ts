import { request } from '@umijs/max';

/** 业务设置 → AI 设置：当前租户的 AI 配置（按连接方式分别保存），见 openspec ai-platform/tenant-ai-settings */

export type AiMode = 'api' | 'subscription';
export type AiProvider = 'claude' | 'openai';

/** 一种连接方式的设置；Key 只有是否已填写与脱敏值 */
export interface AiProfile {
  /** 是否保存过这一种的设置 */
  saved: boolean;
  hasKey: boolean;
  apiKeyMasked: string;
  parseModel: string;
  translateModel: string;
  parseEffort: string;
  translateEffort: string;
  maxWebSearches: number;
}

/** 当前选择 + 各连接方式的设置 */
export interface AiConfig {
  /** 是否保存过当前选择 */
  configured: boolean;
  mode: AiMode;
  provider: AiProvider;
  platform: boolean;
  /** 能否使用平台订阅（平台始终可以；其他公司看套餐授权） */
  subscriptionAllowed: boolean;
  /** 本公司 API Key 设置：provider → 设置 */
  apiProfiles: Record<AiProvider, AiProfile>;
  /** 平台订阅设置：只在能使用平台订阅时返回 */
  subscriptionProfiles?: Record<AiProvider, AiProfile>;
  /** provider → { parse, translate } */
  defaultModels: Record<string, { parse: string; translate: string }>;
  /** 当前选择的最后修改时间与修改人，未配置时为空 */
  updateTime?: string | null;
  updateBy?: string | null;
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

export const saveAiConfig = (data: AiConfigPayload): Promise<void> =>
  request(AI_BASE, { method: 'PUT', data });

export const testAiConfig = (data: AiConfigPayload): Promise<AiTestResult> =>
  request(`${AI_BASE}/test`, { method: 'POST', data }).then((r) => r.data);

export const getAiModels = (data: AiConfigPayload): Promise<AiModelList> =>
  request(`${AI_BASE}/models`, { method: 'POST', data }).then((r) => r.data);

export const getAiSubscriptionStatus = (): Promise<AiSubscriptionStatus> =>
  request(`${AI_BASE}/subscription-status`, { method: 'GET' }).then(
    (r) => r.data,
  );
