import { request } from '@umijs/max';

/** 客户背调（spec crm/customer-background） */

/** 来源（1-AI背调、2-上传） */
export const SOURCE_AI = 1;
/** 状态（1-背调中、2-已完成、3-失败） */
export const STATUS = { RUNNING: 1, DONE: 2, FAILED: 3 };
/** 跟进优先级（0-未出结论、1-立即重点跟进、2-正常跟进、3-低优先级、4-不建议跟进） */
export const PRIORITY_NAMES = [
  '未出结论',
  '立即重点跟进',
  '正常跟进',
  '低优先级',
  '不建议跟进',
];

export interface BackgroundReport {
  id: number;
  reportNo: string;
  customerId: number;
  source: number;
  status: number;
  failReason?: string;
  priority: number;
  score: number;
  mainConflict?: string;
  firstAction?: string;
  identityNote?: string;
  supplyNote?: string;
  keyFinding?: string;
  obstacle?: string;
  suggestionCount: number;
  requestedBy: number;
  requestedByName?: string;
  createTime: string;
  completedAt?: string;
  notice?: string;
}

export interface Suggestion {
  module: number;
  field: string;
  value: string;
  evidence?: string;
  sourceUrl?: string;
  contact?: { name?: string; email?: string; title?: string };
  state: 'pending' | 'adopted' | 'ignored';
  at?: string;
  label?: string;
  currentValue?: string;
  unavailableReason?: string;
  defaultChecked?: boolean;
  operatorName?: string;
}

export interface BackgroundReportDetail extends BackgroundReport {
  reportMarkdown?: string;
  /** 后端渲染好的正文（原始 HTML 已转义、链接已清理） */
  reportHtml?: string;
  suggestions: Suggestion[];
  model?: string;
  inputTokens?: number;
  outputTokens?: number;
  webSearches?: number;
  durationSeconds?: number;
  uploadFileName?: string;
  canDelete: boolean;
}

/** 24 小时内背调过时的提醒（errorCode=BACKGROUND_RECENT 的 detail） */
export interface RecentDetail {
  reportId: number;
  reportNo: string;
  hoursAgo: number;
  requestedByName: string;
  priority?: number;
  score?: number;
  inputTokens?: number;
  outputTokens?: number;
  webSearches?: number;
}

export const ERR_RECENT = 'BACKGROUND_RECENT';

const quiet = { skipErrorHandler: true } as const;
const base = (id: number) =>
  `/api/v1/masterdata/customers/${id}/background-reports`;

export const backgroundApi = {
  list: (id: number) =>
    request<{ data: BackgroundReport[] }>(base(id), quiet).then((r) => r.data),
  detail: (id: number, reportId: number) =>
    request<{ data: BackgroundReportDetail }>(
      `${base(id)}/${reportId}`,
      quiet,
    ).then((r) => r.data),
  research: (id: number, confirmed: boolean) =>
    request<{ data: BackgroundReport }>(`${base(id)}/research`, {
      method: 'POST',
      data: { confirmed },
      ...quiet,
    }).then((r) => r.data),
  upload: (id: number, file?: File, text?: string) => {
    const data = new FormData();
    if (file) data.append('file', file);
    if (text) data.append('text', text);
    return request<{ data: BackgroundReport }>(`${base(id)}/upload`, {
      method: 'POST',
      data,
      requestType: 'form',
      timeout: 120000,
      ...quiet,
    }).then((r) => r.data);
  },
  adopt: (id: number, reportId: number, indexes: number[]) =>
    request(`${base(id)}/${reportId}/suggestions/adopt`, {
      method: 'POST',
      data: { indexes },
      ...quiet,
    }),
  ignore: (id: number, reportId: number, indexes: number[]) =>
    request(`${base(id)}/${reportId}/suggestions/ignore`, {
      method: 'POST',
      data: { indexes },
      ...quiet,
    }),
  remove: (id: number, reportId: number) =>
    request(`${base(id)}/${reportId}`, { method: 'DELETE', ...quiet }),
  fileUrl: (id: number, reportId: number) =>
    request<{ data: { url: string } }>(
      `${base(id)}/${reportId}/file-url`,
      quiet,
    ).then((r) => r.data),
  /** 导出 PDF；失败时后端返回 JSON，读出其中的提示 */
  exportPdf: async (id: number, reportId: number, fileName: string) => {
    const res = await request(`${base(id)}/${reportId}/export`, {
      responseType: 'blob',
      getResponse: true,
      timeout: 120000,
      ...quiet,
    });
    const blob: Blob = res.data;
    if (blob.type.includes('json')) {
      const parsed = JSON.parse(await blob.text());
      throw new Error(parsed.message || '导出失败，请稍后重试');
    }
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  },
};

/** 报告详情路由 */
export const reportPath = (customerId: number, reportId: number) =>
  `/customer/list/${customerId}/background/${reportId}`;

/** 128000 → 128K */
export const kilo = (n?: number) =>
  n == null ? '—' : n >= 1000 ? `${Math.round(n / 1000)}K` : String(n);

/** 用时：192 → 3 分 12 秒 */
export const duration = (s?: number) =>
  s == null
    ? '—'
    : s >= 60
      ? `${Math.floor(s / 60)} 分 ${s % 60} 秒`
      : `${s} 秒`;

// ---------------------------------------------------------------- 档案补全

export interface CompletionRow {
  customerId: number;
  customerCode: string;
  name: string;
  country: string;
  bizGrade: number;
  potentialRating: number;
  done: number;
  missingCount: number;
  askedDays?: number;
  lastActivity?: string;
}

export interface CompletionItem {
  key: string;
  label: string;
  module: number;
  contact: boolean;
  sourceKey: string;
  question?: string;
  askedAt?: string;
  askedDays?: number;
  askedByName?: string;
}

export interface CompletionStep {
  /** 1-系统已经知道、2-AI 背调能查到、3-你自己就能判断、4-只能问客户 */
  step: number;
  name: string;
  items: CompletionItem[];
  autoFilled?: string[];
  countryProfileMissing?: boolean;
  report?: BackgroundReportDetail;
  copyAll?: string;
}

export interface CompletionWizard {
  customerId: number;
  customerCode: string;
  name: string;
  country: string;
  done: number;
  contactId?: number;
  contactName?: string;
  steps: CompletionStep[];
}

export const completionApi = {
  queue: (page: number, pageSize: number) =>
    request<{
      data: { total: number; todayDone: number; records: CompletionRow[] };
    }>('/api/v1/masterdata/customer-completion/queue', {
      params: { page, pageSize },
      ...quiet,
    }).then((r) => r.data),
  wizard: (id: number) =>
    request<{ data: CompletionWizard }>(
      `/api/v1/masterdata/customers/${id}/completion`,
      quiet,
    ).then((r) => r.data),
};
