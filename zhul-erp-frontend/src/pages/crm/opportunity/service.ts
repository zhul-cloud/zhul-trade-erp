import { request } from '@umijs/max';

export interface OpportunityStage {
  code: string;
  name: string;
  /** 1-进行中、2-赢单、3-输单、4-无效 */
  category: number;
  countsAsValid: boolean;
  sortOrder: number;
}

export interface OpportunityItem {
  id: number;
  opportunityCode: string;
  customerId: number;
  /** 客户展示名：有客户名称用名称，否则用联系人名称 */
  customerName: string;
  customerNameMissing: boolean;
  country: string;
  sourceChannel: number;
  firstContactDate: string;
  ownerId: number;
  ownerName?: string;
  stageCode: string;
  stageName: string;
  stageCategory: number;
  demandSummary: string;
  createTime: string;
  createBy: string;
  updateTime: string;
  updateBy: string;
}

export interface OpportunityAttachment {
  id: number;
  fileName: string;
  fileSize: number;
  contentType: string;
  createBy: string;
  createTime: string;
}

export interface StageLog {
  id: number;
  /** 1-登记、2-阶段变更、3-标记结束、4-重新打开 */
  action: number;
  fromStage: string;
  fromStageName: string;
  toStage: string;
  toStageName: string;
  reason: number;
  reasonLabel: string;
  note: string;
  operator: string;
  createTime: string;
}

export interface LinkedInquiry {
  id: number;
  inquiryCode: string;
  status: number;
  inquiryDate: string;
  totalOrderCount: number;
}

export interface OpportunityDetail extends OpportunityItem {
  customerCode: string;
  contactName: string;
  email: string;
  whatsapp: string;
  phone: string;
  website: string;
  reopenStageCode: string;
  closeReason: number;
  closeReasonLabel: string;
  closeNote: string;
  attachments: OpportunityAttachment[];
  stageLogs: StageLog[];
  inquiries: LinkedInquiry[];
}

export interface OpportunityQuery {
  keyword?: string;
  sourceChannel?: number;
  /** 具体阶段编码，或 ACTIVE（进行中）、CLOSED（已结束） */
  stage?: string;
  ownerId?: number;
  from?: string;
  to?: string;
}

export interface OpportunitySummary {
  todayNew: number;
  todayInvalid: number;
  weekNew: number;
  lastWeekNew: number;
  firstStageCount: number;
  firstStageStale: number;
  monthNew: number;
  monthValid: number;
}

export interface StatRow {
  key: string;
  label: string;
  total: number;
  invalid: number;
  valid: number;
  won: number;
  lost: number;
}

export interface OpportunityStats {
  groupBy: 'channel' | 'owner' | 'date';
  rows: StatRow[];
  summary: StatRow;
}

export interface UploadedFile {
  fileKey: string;
  fileName: string;
  fileSize: number;
  contentType: string;
}

/** 附件：带 id 为保留已有附件；不带 id 时 fileKey 为上传接口返回值 */
export interface AttachmentPayload {
  id?: number;
  fileName?: string;
  fileKey?: string;
}

export interface RegisterPayload {
  contactName: string;
  country: string;
  sourceChannel: number;
  firstContactDate: string;
  customerName?: string;
  email?: string;
  whatsapp?: string;
  phone?: string;
  website?: string;
  demandSummary?: string;
  attachments: AttachmentPayload[];
}

export interface UpdatePayload {
  sourceChannel: number;
  firstContactDate: string;
  demandSummary?: string;
  attachments?: AttachmentPayload[];
}

export interface BizErrorInfo {
  errorCode?: string;
  detail?: Record<string, unknown>;
  message: string;
}

/** 读取接口错误：业务错误码在 info.data；参数校验失败（HTTP 400）的原因在 response.data.message */
export function readBizError(error: unknown): BizErrorInfo {
  const e = error as {
    message?: string;
    info?: { data?: { errorCode?: string; detail?: Record<string, unknown> } };
    response?: { data?: { message?: string } };
  };
  return {
    errorCode: e?.info?.data?.errorCode,
    detail: e?.info?.data?.detail,
    message: e?.response?.data?.message ?? e?.message ?? '操作失败，请稍后重试',
  };
}

const quiet = { skipErrorHandler: true } as const;
const BASE = '/api/v1/crm/opportunities';
const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const post = <T>(url: string, data?: object) =>
  request<{ data: T }>(url, { method: 'POST', data, ...quiet }).then(
    (r) => r.data,
  );

// 阶段配置几乎不变，整个页面生命周期里只请求一次
let stageCache: Promise<OpportunityStage[]> | null = null;

export const opportunityApi = {
  stages: () => {
    if (!stageCache) {
      stageCache = get<OpportunityStage[]>(`${BASE}/stages`).catch((e) => {
        stageCache = null;
        throw e;
      });
    }
    return stageCache;
  },
  page: (params: OpportunityQuery & { page: number; pageSize: number }) =>
    get<{ records: OpportunityItem[]; total: number }>(`${BASE}/page`, params),
  summary: () => get<OpportunitySummary>(`${BASE}/summary`),
  stats: (from: string, to: string, groupBy: string) =>
    get<OpportunityStats>(`${BASE}/stats`, { from, to, groupBy }),
  detail: (id: number) => get<OpportunityDetail>(`${BASE}/${id}`),
  register: (data: RegisterPayload) => post<{ id: number }>(BASE, data),
  update: (id: number, data: UpdatePayload) =>
    request(`${BASE}/${id}`, { method: 'PUT', data, ...quiet }),
  changeStage: (id: number, toStage: string, note?: string) =>
    post<void>(`${BASE}/${id}/stage`, { toStage, note }),
  close: (id: number, result: string, reason?: number, note?: string) =>
    post<void>(`${BASE}/${id}/close`, { result, reason, note }),
  reopen: (id: number) => post<void>(`${BASE}/${id}/reopen`),
  upload: (file: File) => {
    const data = new FormData();
    data.append('file', file);
    return request<{ data: UploadedFile }>(`${BASE}/attachments`, {
      method: 'POST',
      data,
      requestType: 'form',
      ...quiet,
    }).then((r) => r.data);
  },
  /** 取附件文件（接口需要登录与权限，不能直接用链接），返回浏览器本地地址，用完需 revoke */
  attachmentBlobUrl: async (id: number, attachmentId: number) => {
    const blob: Blob = await request(
      `${BASE}/${id}/attachments/${attachmentId}`,
      {
        method: 'GET',
        params: { inline: true },
        responseType: 'blob',
        ...quiet,
      },
    );
    return window.URL.createObjectURL(blob);
  },
};
