import { request } from '@umijs/max';

/** 业务设置 → AI 设置：当前租户的 AI 编排配置，见 openspec ai-platform/tenant-ai-settings */

export type AiMode = 'api' | 'subscription';
export type AiProvider = 'claude' | 'openai';

export interface AiConfig {
  configured: boolean;
  mode: AiMode;
  provider: AiProvider;
  hasKey: boolean;
  apiKeyMasked: string;
  parseModel: string;
  translateModel: string;
  parseEffort: string;
  translateEffort: string;
  maxWebSearches: number;
  /** 只有平台租户能选订阅方式 */
  subscriptionAllowed: boolean;
  /** provider → { parse, translate } */
  defaultModels: Record<string, { parse: string; translate: string }>;
  /** 最后修改时间与修改人，未配置时为空 */
  updateTime?: string | null;
  updateBy?: string | null;
}

/** 保存、测试连接、获取模型列表共用；apiKey 留空表示沿用已保存的 Key */
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
  codexLoggedIn: boolean;
  claudeLoggedIn: boolean;
  codexLoginCommand: string;
  claudeLoginCommand: string;
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
