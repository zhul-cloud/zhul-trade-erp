import { request } from '@umijs/max';

export { readBizError } from '@/pages/crm/opportunity/service';

// ---------------------------------------------------------------- 类型

export interface UploadedFile {
  fileKey: string;
  fileName: string;
  contentType: string;
  fileSize: number;
}

export interface InquiryAttachment {
  id: number;
  fileName: string;
  contentType: string;
  fileSize: number;
}

export interface CustomerInquiry {
  id: number;
  inquiryCode: string;
  customerId: number;
  customerName: string;
  customerCountry: string;
  customerType: number;
  source: number;
  inquiryDate: string;
  quoteDeadline: string;
  urgent: boolean;
  /** 询盘等级（字典 inquiry_level 码值，越小越优先） */
  level: number;
  status: number;
  parseMode: number;
  totalItemCount: number;
  /** 所有型号数量之和（不区分单位）；待确认时为 AI 识别结果 */
  totalQuantity: number;
  pricedItemCount: number;
  taskCount: number;
  timeoutTaskCount: number;
  pendingVerifyCount: number;
  needsReview: boolean;
  ownerId?: number;
  ownerName?: string;
  opportunityId?: number;
  createTime: string;
  updateTime: string;
}

export interface PriceRecord {
  id: number;
  brand: string;
  model: string;
  /** 询价平台、店铺：没有「查看货源信息」权限时为空 */
  channel?: number | null;
  shopName?: string | null;
  noStock: boolean;
  currencyCode: string;
  unitPrice?: number;
  unitPriceCny?: number;
  taxIncluded: boolean;
  /** 税率百分比；含税时 unitPrice 为含税价，unitPriceCny 为换算后的不含税价 */
  taxRate?: number;
  itemCondition: number;
  /** 货期码值，见字典 inquiry_lead_time；0 为未填 */
  leadTime: number;
  note: string;
  /** 该采购对该型号的推荐报价 */
  recommended?: boolean;
  quotedBy: number;
  quotedByName?: string;
  quotedAt?: string;
  daysAgo?: number;
  customerInquiryId?: number;
  customerInquiryCode?: string;
}

export interface InquiryItem {
  id: number;
  lineNo: number;
  brand: string;
  brandKey: string;
  category: string;
  originalModel: string;
  confirmedModel: string;
  confidence: number;
  correctionNote: string;
  quantity: number;
  unit: string;
  description: string;
  lifecycle: number;
  replacementModel: string;
  difficulty: number;
  priceSource: number;
  quoteStatus: number;
  sourcingTaskId?: number;
  taskCode?: string;
  selectedQuote?: PriceRecord;
  noStockNote?: string;
}

export interface TaskBrief {
  id: number;
  taskCode: string;
  brand: string;
  category: string;
  itemCount: number;
  pricedCount: number;
  status: number;
  timeout: boolean;
  firstAssignedAt?: string;
  assigneeNames: string[];
  returnReason: number;
  returnNote: string;
}

export interface InquiryDetail {
  inquiry: CustomerInquiry;
  rawContent: string;
  parseError: string;
  remark: string;
  attachments: InquiryAttachment[];
  items: InquiryItem[];
  tasks: TaskBrief[];
  opportunityCode?: string;
  opportunityStageName?: string;
}

export interface PriceMatch {
  /** 品牌匹配键（按品牌及别名识别），拆分预览按它分组 */
  brandKey: string;
  sameBrand: PriceRecord[];
  otherBrands: PriceRecord[];
  defaultQuoteId?: number;
}

export interface DraftRow {
  brand: string;
  category: string;
  originalModel?: string;
  confirmedModel: string;
  confidence: number;
  correctionNote?: string;
  quantity: number;
  unit?: string;
  description?: string;
  lifecycle: number;
  replacementModel?: string;
  difficulty: number;
  inquiryScript?: string;
  searchKeywords?: string[];
  match?: PriceMatch;
}

export interface Draft {
  inquiry: CustomerInquiry;
  rawContent: string;
  attachments: InquiryAttachment[];
  rows: DraftRow[];
}

export interface ConfirmRow extends Omit<DraftRow, 'match'> {
  reuseQuoteId?: number | null;
}

export interface InquiryQuery {
  keyword?: string;
  statusList?: number[];
  source?: number;
  ownerId?: number;
  customerType?: number;
  inquiryDateFrom?: string;
  inquiryDateTo?: string;
  urgentOnly?: boolean;
  timeoutOnly?: boolean;
  level?: number;
  minItemCount?: number;
  maxItemCount?: number;
  minTotalQuantity?: number;
  maxTotalQuantity?: number;
  sortField?: 'level' | 'totalItemCount' | 'totalQuantity';
  sortOrder?: 'asc' | 'desc';
}

export interface SubmitPayload {
  /** 询盘等级（字典 inquiry_level 码值），默认 3-B */
  level?: number;
  customerId: number;
  source: number;
  inquiryDate?: string;
  quoteDeadline?: string;
  urgent?: boolean;
  rawContent?: string;
  attachments?: { fileKey: string; fileName: string }[];
  opportunityId?: number;
  remark?: string;
}

export interface Purchaser {
  id: number;
  name: string;
  partTime: boolean;
  activeTasks: number;
  timeoutTasks: number;
}

export interface BoardTask {
  id: number;
  taskCode: string;
  customerInquiryId: number;
  /** 所属业务员（客户询盘负责人） */
  salesId?: number;
  salesName?: string;
  /** 询盘等级（字典 inquiry_level 码值） */
  level?: number;
  /** 1-新客户、2-老客户 */
  customerType?: number;
  /** 兼职采购看不到，为空 */
  customerName?: string;
  quoteDeadline?: string;
  /** 回价是否还能修改（业务员报价后为 false） */
  editable?: boolean;
  brand: string;
  category: string;
  itemCount: number;
  pricedCount: number;
  urgent: boolean;
  status: number;
  timeout: boolean;
  waitingMinutes?: number;
  firstAssignedAt?: string;
  returnReason: number;
  returnReasonLabel?: string;
  returnNote?: string;
  returnedByName?: string;
  recommendedId?: number;
  recommendedName?: string;
  recommendReason?: string;
  assignees: Purchaser[];
  /** 待审核页签：有待审核回价的型号数、最早一批提交时间与已等待分钟数、提交的兼职采购 */
  reviewItemCount?: number;
  reviewSubmittedAt?: string;
  reviewWaitingMinutes?: number;
  reviewBuyerNames?: string[];
}

export interface Board {
  stats: {
    unassigned: number;
    longestWaitingMinutes: number;
    sourcing: number;
    multiAssigned: number;
    timeout: number;
    returned: number;
    returnedForDoubt: number;
    timeoutHours: number;
    urgentTimeoutHours: number;
    autoAssign: boolean;
    /** 已回价、业务员还没报价的任务数 */
    done?: number;
    /** 有兼职回价待审核的任务数、型号数，最早一批已等待分钟数 */
    pendingReview?: number;
    pendingReviewItems?: number;
    longestReviewMinutes?: number;
  };
  tasks: BoardTask[];
  purchasers: Purchaser[];
}

export interface RulePreview {
  taskId: number;
  taskCode: string;
  brand: string;
  category: string;
  assigneeId?: number;
  assigneeName?: string;
  basis: string;
}

export interface AssignRule {
  id: number;
  priority: number;
  matchType: number;
  matchValues: string[];
  assigneeId: number;
  assigneeName?: string;
  status: number;
  hits: number;
}

export interface MyTask {
  id: number;
  taskCode: string;
  /** 所属业务员（客户询盘负责人） */
  salesId?: number;
  salesName?: string;
  /** 询盘等级（字典 inquiry_level 码值） */
  level?: number;
  /** 1-新客户、2-老客户 */
  customerType?: number;
  /** 兼职采购看不到，为空 */
  customerName?: string;
  quoteDeadline?: string;
  /** 回价是否还能修改（业务员报价后为 false） */
  editable?: boolean;
  brand: string;
  category: string;
  itemCount: number;
  filledCount: number;
  status: number;
  urgent: boolean;
  timeout: boolean;
  remainingMinutes?: number;
  assignedAt?: string;
  shared: boolean;
}

export interface MyQuote {
  id: number;
  channel: number;
  shopName: string;
  supplierId?: number;
  unitPrice?: number;
  taxIncluded: boolean;
  /** 税率百分比；含税时 unitPrice 为含税价，unitPriceCny 为换算后的不含税价 */
  taxRate?: number;
  itemCondition: number;
  leadTime: number;
  note: string;
  recommended?: boolean;
  noStock: boolean;
  /** 1-草稿、2-已提交、3-待审核、4-已作废 */
  status: number;
  /** 作废原因（status=4）或退回原因（被退回的草稿） */
  reviewNote?: string;
  quotedAt?: string;
}

export interface MyTaskItem {
  id: number;
  model: string;
  originalModel: string;
  quantity: number;
  unit: string;
  description: string;
  lifecycle: number;
  replacementModel: string;
  difficulty: number;
  inquiryScript: string;
  searchKeywords: string[];
  quotes: MyQuote[];
  /** 兼职回价的审核状态：1-待审核、2-已通过、3-被退回 */
  reviewStatus?: number;
  reviewNote?: string;
  reviewedByName?: string;
  reviewedAt?: string;
}

export interface QuoteEntry {
  channel: number;
  shopName?: string;
  /** 渠道为供应商时必填 */
  supplierId?: number;
  unitPrice?: number | null;
  taxIncluded?: boolean;
  /** 含税时的税率（百分比整数），不传按 13 */
  taxRate?: number;
  itemCondition?: number;
  leadTime?: number;
  note?: string;
  /** 推荐报价，同一型号最多一条；都不标时按全新原装最低价自动推荐 */
  recommended?: boolean;
}

export interface ItemQuotes {
  itemId: number;
  noStock?: boolean;
  noStockNote?: string;
  quotes?: QuoteEntry[];
  /** 采购核实的生产状态（1-在产、2-停产、3-待查），不传表示不改 */
  lifecycle?: number;
  replacementModel?: string;
}

export interface ImportRow {
  position: string;
  itemId?: number;
  model: string;
  channel: number;
  shopName: string;
  rawPrice: string;
  unitPrice?: number;
  /** 单价里写了「含税」 */
  taxIncluded?: boolean;
  taxRate?: number;
  noStock: boolean;
  itemCondition: number;
  rawCondition: string;
  leadTime: number;
  rawLeadTime: string;
  note: string;
  problems: string[];
}

export interface ImportFile {
  fileName: string;
  fileKey?: string;
  rejectReason?: string;
  taskId?: number;
  taskCode?: string;
  brand?: string;
  category?: string;
  assigneeId?: number;
  assigneeName?: string;
  rows: ImportRow[];
}

export interface PriceHistoryGroup {
  brand: string;
  model: string;
  category?: string;
  brandKey: string;
  modelKey: string;
  minPriceCny?: number;
  maxPriceCny?: number;
  recordCount: number;
  lastQuotedAt?: string;
  daysAgo?: number;
  records: PriceRecord[];
}

// ---------------------------------------------------------------- 请求

const quiet = { skipErrorHandler: true } as const;
const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(method: string, url: string, data?: unknown) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);
const upload = <T>(url: string, field: string, files: File[]) => {
  const data = new FormData();
  for (const f of files) data.append(field, f);
  return request<{ data: T }>(url, {
    method: 'POST',
    data,
    requestType: 'form',
    ...quiet,
  }).then((r) => r.data);
};
const blob = (url: string, params: object) =>
  request<Blob>(url, {
    method: 'GET',
    params,
    responseType: 'blob',
    getResponse: true,
    ...quiet,
  });

const INQ = '/api/v1/inquiry/customer-inquiries';
const BOARD = '/api/v1/inquiry/sourcing-board';
const MY = '/api/v1/inquiry/my-tasks';

/** 下载接口返回的文件：从响应头取文件名后触发浏览器下载（request 基于 axios，getResponse 时返回完整响应） */
const saveBlob = async (p: Promise<unknown>, fallback: string) => {
  const res = (await p) as {
    data: Blob;
    headers: Record<string, string | undefined>;
  };
  // 出错时接口返回的是 JSON 提示而不是文件
  if (res.data.type.includes('json')) {
    const body = JSON.parse(await res.data.text()) as { message?: string };
    throw new Error(body.message || '下载失败，请稍后重试');
  }
  const disposition = res.headers?.['content-disposition'] ?? '';
  const match = /filename\*=UTF-8''([^;]+)/.exec(disposition);
  const name = match ? decodeURIComponent(match[1]) : fallback;
  const url = window.URL.createObjectURL(res.data);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.setTimeout(() => window.URL.revokeObjectURL(url), 1000);
};

export const inquiryApi = {
  upload: (file: File) =>
    upload<UploadedFile>(`${INQ}/attachments`, 'file', [file]),
  copyFromOpportunity: (opportunityId: number, attachmentId: number) =>
    send<UploadedFile>('POST', `${INQ}/attachments/from-opportunity`, {
      opportunityId,
      attachmentId,
    }),
  customerType: (customerId: number) =>
    get<{ customerType: number; wonCount: number; lastWonDate?: string }>(
      `${INQ}/customer-type`,
      { customerId },
    ),
  priceMatch: (brand: string, model: string) =>
    get<PriceMatch>(`${INQ}/price-match`, { brand, model }),
  submit: (data: SubmitPayload) => send<CustomerInquiry>('POST', INQ, data),
  page: (query: InquiryQuery & { page: number; pageSize: number }) =>
    send<{ records: CustomerInquiry[]; total: number }>(
      'POST',
      `${INQ}/page`,
      query,
    ),
  detail: (id: number) => get<InquiryDetail>(`${INQ}/${id}`),
  attachmentBlobUrl: async (id: number, attachmentId: number) => {
    const res = (await blob(`${INQ}/${id}/attachments/${attachmentId}`, {
      inline: true,
    })) as { data: Blob };
    return window.URL.createObjectURL(res.data);
  },
  startParse: (id: number) =>
    send<CustomerInquiry>('POST', `${INQ}/${id}/start-parse`),
  retryParse: (id: number) =>
    send<CustomerInquiry>('POST', `${INQ}/${id}/retry-parse`),
  draft: (id: number) => get<Draft>(`${INQ}/${id}/draft`),
  confirm: (id: number, rows: ConfirmRow[]) =>
    send<void>('POST', `${INQ}/${id}/confirm`, { rows }),
  cancel: (id: number) => send<void>('PUT', `${INQ}/${id}/cancel`),
  updateLevel: (id: number, level: number) =>
    send<void>('PUT', `${INQ}/${id}/level`, { level }),
  reviewed: (id: number) => send<void>('POST', `${INQ}/${id}/reviewed`),
};

export const boardApi = {
  board: (status?: number) => get<Board>(BOARD, { status }),
  purchasers: () => get<Purchaser[]>(`${BOARD}/purchasers`),
  taskDetail: (id: number) => get<BoardTaskDetail>(`${BOARD}/tasks/${id}`),
  itemHistory: (itemId: number) =>
    get<ItemHistoryEntry[]>(`${BOARD}/items/${itemId}/history`),
  reviewDetail: (taskId: number) =>
    get<ReviewDetail>(`${BOARD}/reviews/${taskId}`),
  approveReview: (taskId: number, items: ReviewApproveItem[]) =>
    send<void>('POST', `${BOARD}/reviews/${taskId}/approve`, { items }),
  rejectReview: (
    taskId: number,
    items: { itemId: number; quotedBy: number }[],
    reason: string,
  ) =>
    send<void>('POST', `${BOARD}/reviews/${taskId}/reject`, { items, reason }),
  /** 指定采购成本价；quoteId 为 null 时恢复按推荐自动取 */
  setCostQuote: (itemId: number, quoteId: number | null) =>
    send<void>('PUT', `${BOARD}/items/${itemId}/cost-quote`, { quoteId }),
  assign: (taskIds: number[], assigneeId: number) =>
    send<void>('POST', `${BOARD}/assign`, { taskIds, assigneeId }),
  assignRecommended: (taskIds: number[]) =>
    send<void>('POST', `${BOARD}/assign-recommended`, { taskIds }),
  reassign: (taskId: number, assigneeId: number) =>
    send<void>('POST', `${BOARD}/tasks/${taskId}/reassign`, { assigneeId }),
  addAssignee: (taskId: number, assigneeId: number) =>
    send<void>('POST', `${BOARD}/tasks/${taskId}/assignees`, { assigneeId }),
  rulePreview: (taskIds: number[]) =>
    send<RulePreview[]>('POST', `${BOARD}/rule-preview`, { taskIds }),
  assignByRule: (taskIds: number[]) =>
    send<void>('POST', `${BOARD}/assign-by-rule`, { taskIds }),
  rules: () =>
    get<{ autoAssign: boolean; rules: AssignRule[] }>(`${BOARD}/rules`),
  createRule: (data: Partial<AssignRule>) =>
    send<void>('POST', `${BOARD}/rules`, data),
  updateRule: (id: number, data: Partial<AssignRule>) =>
    send<void>('PUT', `${BOARD}/rules/${id}`, data),
  deleteRule: (id: number) => send<void>('DELETE', `${BOARD}/rules/${id}`),
  reorderRules: (ids: number[]) =>
    send<void>('PUT', `${BOARD}/rules/order`, ids),
  setAutoAssign: (enabled: boolean) =>
    send<void>('PUT', `${BOARD}/rules/auto-assign`, { enabled }),
  downloadPackage: (taskIds: number[]) =>
    saveBlob(
      blob(`${BOARD}/package`, { taskIds: taskIds.join(',') }),
      '询价包.xlsx',
    ),
  importPreview: (files: File[]) =>
    upload<ImportFile[]>(`${BOARD}/import/preview`, 'files', files),
  importConfirm: (files: unknown[]) =>
    send<void>('POST', `${BOARD}/import/confirm`, { files }),
};

export interface PartTimeBoard {
  pendingTasks: number;
  timeoutTasks: number;
  urgentTasks: number;
  monthQuotedItems: number;
  monthQuotes: number;
  monthNoStock: number;
  monthCompletedTasks: number;
  /** 本月平均回价用时（小时），本月没有提交为空 */
  monthAvgHours?: number | null;
  totalQuotedItems: number;
  trend: { date: string; items: number }[];
  todo: MyTask[];
}

export const partTimeBoardApi = {
  get: () => get<PartTimeBoard>('/api/v1/inquiry/part-time-board'),
};

export interface BoardTaskItem {
  id: number;
  model: string;
  originalModel: string;
  quantity: number;
  unit: string;
  description: string;
  lifecycle: number;
  replacementModel: string;
  difficulty: number;
  /** 1-待询价、2-已回价、3-无货 */
  quoteStatus: number;
  /** 当前采购成本价对应的询价记录 */
  selectedQuoteId?: number;
  /** 成本价是否由采购负责人手动指定 */
  costManual: boolean;
  quotes: PriceRecord[];
  /** 修改过的次数（修改回价 + 调整成本价），大于 0 时显示「修改记录」 */
  changeCount: number;
}

export interface ItemHistoryEntry {
  /** QUOTE-回价版本、COST-成本价调整、REVIEW-审核兼职回价 */
  type: 'QUOTE' | 'COST' | 'REVIEW';
  time: string;
  operatorName?: string;
  action: string;
  /** QUOTE：是否为该采购当前有效的版本 */
  current?: boolean;
  quotes?: PriceRecord[];
  note?: string;
}

export interface BoardTaskDetail {
  task: BoardTask;
  items: BoardTaskItem[];
}

/** 待审核的一个型号：某位兼职采购提交的一批回价 */
export interface ReviewItem {
  itemId: number;
  model: string;
  originalModel: string;
  quantity: number;
  unit: string;
  description: string;
  quotedBy: number;
  quotedByName?: string;
  submittedAt?: string;
  quotes: PriceRecord[];
  /** 同品牌同型号历史询价最低价 */
  historyLowest?: PriceRecord;
}

export interface ReviewDetail {
  task: BoardTask;
  items: ReviewItem[];
  /** 还没有任何回价的型号数 */
  unsubmittedCount: number;
}

export interface ReviewApproveItem {
  itemId: number;
  quotedBy: number;
  recommendedQuoteId?: number | null;
  voids: { quoteId: number; reason?: string }[];
}

export const myTaskApi = {
  list: (done: boolean) => get<MyTask[]>(MY, { done }),
  detail: (id: number) =>
    get<{ task: MyTask; items: MyTaskItem[]; reviewRequired?: boolean }>(
      `${MY}/${id}`,
    ),
  save: (id: number, items: ItemQuotes[], submit: boolean) =>
    send<void>('PUT', `${MY}/${id}/quotes`, { items, submit }),
  returnTask: (id: number, reason: number, note?: string) =>
    send<void>('POST', `${MY}/${id}/return`, { reason, note }),
  downloadPackage: (taskIds: number[]) =>
    saveBlob(
      blob(`${MY}/package`, { taskIds: taskIds.join(',') }),
      '询价包.xlsx',
    ),
  importPreview: (files: File[]) =>
    upload<ImportFile[]>(`${MY}/import/preview`, 'files', files),
  importConfirm: (files: unknown[]) =>
    send<void>('POST', `${MY}/import/confirm`, { files }),
};

export const priceHistoryApi = {
  page: (query: {
    model?: string;
    brand?: string;
    itemCondition?: number;
    channel?: number;
    dateFrom?: string;
    dateTo?: string;
    page: number;
    pageSize: number;
  }) =>
    send<{ records: PriceHistoryGroup[]; total: number }>(
      'POST',
      '/api/v1/inquiry/price-history/page',
      query,
    ),
};
