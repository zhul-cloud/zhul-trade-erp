import { request } from '@umijs/max';
import { fileUrl } from '@/utils/fileLink';

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
  /** 1-询盘附件、2-铭牌图片 */
  kind?: number;
  /** 铭牌图片对应的型号 ID，0 为未对应 */
  inquiryItemId?: number;
}

/** 询价任务能看到的铭牌图片：任务内型号对应的，以及同一询盘单未对应型号的 */
export interface TaskNameplates {
  items: { itemId: number; files: InquiryAttachment[] }[];
  unmatched: InquiryAttachment[];
}

export interface CustomerInquiry {
  id: number;
  inquiryCode: string;
  /** 历史迁移的询盘单（只读） */
  migrated?: boolean;
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
  /** 第一个型号；未解析、解析中、解析失败时为空 */
  firstModel?: string;
  /** 品牌、一级品类，按型号数从多到少 */
  brands?: string[];
  categories?: string[];
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
  /** 是否包邮 */
  freeShipping?: boolean;
  /** 回价附件，有「查看货源信息」权限时才有 */
  attachments?: QuoteFile[];
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
  /** 关联的品类（细分或一级），关联不上时为空 */
  categoryId?: number | null;
  /** 分组键：品牌 + 一级品类（没关联品类时按品类文字），与询价任务拆分一致 */
  groupKey: string;
  originalModel: string;
  confirmedModel: string;
  /** 订货号（物料号、料号），辅助识别；多个用英文逗号分隔 */
  orderCode?: string;
  confidence: number;
  correctionNote: string;
  quantity: number;
  unit: string;
  description: string;
  /** 英文描述（给客户看的单据用） */
  descriptionEn?: string;
  lifecycle: number;
  replacementModel: string;
  difficulty: number;
  priceSource: number;
  quoteStatus: number;
  sourcingTaskId?: number;
  taskCode?: string;
  selectedQuote?: PriceRecord;
  noStockNote?: string;
  /** 已报给客户（出现在已发送及之后的报价单中） */
  quoted?: boolean;
  /** 采购回填的真实型号 */
  actualModel?: string;
  /** 建档状态：0-未处理、1-已建档、2-候选中、3-待回填真实型号 */
  archiveStatus?: number;
  archiveStatusName?: string;
  /** 已建档时关联的商品 */
  productId?: number | null;
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
  /** 待业务处理：距自动转给采购的分钟数 */
  salesRemainingMinutes?: number;
  /** 当前账号是询盘负责人且任务待业务处理 */
  canTransfer?: boolean;
  /** 可以转采购且能打开我的询价 */
  canClaim?: boolean;
  /** 业务员自询 */
  selfSourcing?: boolean;
}

export interface InquiryDetail {
  inquiry: CustomerInquiry;
  rawContent: string;
  parseError: string;
  remark: string;
  attachments: InquiryAttachment[];
  /** 铭牌图片（含追加的），inquiryItemId 为对应的型号 */
  nameplates: InquiryAttachment[];
  items: InquiryItem[];
  tasks: TaskBrief[];
  /** 未完成的追加型号 */
  openAppend?: InquiryAppend | null;
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
  /** 关联的品类：AI 解析或从下拉选中时有值；手填文字时为空，确认时由后端按文字匹配 */
  categoryId?: number | null;
  originalModel?: string;
  confirmedModel: string;
  /** 订货号（物料号、料号），辅助识别；多个用英文逗号分隔 */
  orderCode?: string;
  confidence: number;
  correctionNote?: string;
  quantity: number;
  unit?: string;
  description?: string;
  /** 英文描述（给客户看的单据用） */
  descriptionEn?: string;
  lifecycle: number;
  replacementModel?: string;
  difficulty: number;
  inquiryScript?: string;
  searchKeywords?: string[];
  match?: PriceMatch;
  /** 这一行对应的铭牌图片（附件 ID） */
  nameplateIds?: number[];
}

/** 未完成的追加型号：1-解析中、2-待确认、3-解析失败 */
export interface InquiryAppend {
  id: number;
  status: number;
  /** 1-AI、2-手动 */
  parseMode: number;
  parseError?: string;
  rawContent?: string;
  createTime?: string;
}

export interface Draft {
  inquiry: CustomerInquiry;
  /** 追加型号的批次 ID；首次确认时为空 */
  appendId?: number;
  rawContent: string;
  attachments: InquiryAttachment[];
  /** 这次录入或追加的铭牌图片 */
  nameplates: InquiryAttachment[];
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
  /** 铭牌图片：只收 JPG、PNG，采购能看到 */
  nameplates?: { fileKey: string; fileName: string }[];
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
  /** 所属业务员（询盘单负责人） */
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
  /** 业务员自询的回价人 */
  selfSourcedBy?: string;
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
  /** 当前页签的任务总数 */
  total: number;
  /** 当前页签按品牌的汇总，覆盖全部页 */
  brands: BrandGroup[];
  /** 当前页的任务 */
  tasks: BoardTask[];
  purchasers: Purchaser[];
}

/** 品牌分组汇总（覆盖全部页），用于分页后的组头 */
export interface BrandGroup {
  brand: string;
  total: number;
  urgent: number;
  timeout: number;
  /** 待分配：最久已等分钟数；待认领：最早自动转给采购的剩余分钟数 */
  minutes?: number;
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
  /** 所属业务员（询盘单负责人） */
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
  /** 任务更新时间与本人最近一次保存回价时间的较晚者 */
  updateTime?: string;
  shared: boolean;
  /** 业务员自询 */
  selfSourcing?: boolean;
  /** 最近一次退回 */
  returnReasonLabel?: string;
  returnNote?: string;
  returnedByName?: string;
}

/** 接手人看到的上一轮记录；兼职看不到价格 */
export interface PreviousQuote {
  quotedByName?: string;
  noStock: boolean;
  channel: number;
  shopName?: string;
  note?: string;
  unitPriceCny?: number;
  currencyCode?: string;
}

/** 询盘负责人待认领的任务 */
export interface SalesPendingTask {
  id: number;
  taskCode: string;
  customerInquiryId: number;
  inquiryCode: string;
  level?: number;
  customerName?: string;
  quoteDeadline?: string;
  urgent: boolean;
  brand: string;
  category: string;
  itemCount: number;
  remainingMinutes: number;
}

/** 待认领：当前页任务 + 全部页的品牌汇总 */
export interface SalesPending {
  total: number;
  brands: BrandGroup[];
  tasks: SalesPendingTask[];
}

/** 按型号退回前的预览 */
export interface ReturnPreview {
  pendingReview: boolean;
  rows: {
    itemId: number;
    model: string;
    quantity: number;
    quoteStatus: number;
    must: boolean;
    locked: boolean;
    lockedQuotationNo?: string;
    checkedChannels: string;
    unitPriceCny?: number;
    currencyCode?: string;
  }[];
  drafts: { quotationNo: string; models: string[] }[];
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
  /** 是否包邮，默认包邮 */
  freeShipping?: boolean;
  /** 回价附件（截图、报价单） */
  attachments?: QuoteFile[];
  /** 1-草稿、2-已提交、3-待审核、4-已作废 */
  status: number;
  /** 作废原因（status=4）或退回原因（被退回的草稿） */
  reviewNote?: string;
  quotedAt?: string;
}

export interface MyTaskItem {
  id: number;
  model: string;
  /** 订货号（物料号、料号），辅助识别 */
  orderCode?: string;
  originalModel: string;
  quantity: number;
  unit: string;
  description: string;
  /** 英文描述（给客户看的单据用） */
  descriptionEn?: string;
  lifecycle: number;
  replacementModel: string;
  difficulty: number;
  inquiryScript: string;
  searchKeywords: string[];
  quotes: MyQuote[];
  quoteStatus?: number;
  /** 上一轮记录 */
  previous?: PreviousQuote[];
  /** 历史询价摘要：DEAL 最近成交、QUOTE 最近询价，没有时为空 */
  historyKind?: 'DEAL' | 'QUOTE';
  historyPrice?: number;
  historyDate?: string;
  /** 兼职回价的审核状态：1-待审核、2-已通过、3-被退回 */
  /** 已报给客户（出现在已发送及之后的报价单中），回价只读 */
  locked?: boolean;
  reviewStatus?: number;
  reviewNote?: string;
  reviewedByName?: string;
  reviewedAt?: string;
  /** 采购回填的真实型号 */
  actualModel?: string;
  /** 建档状态：0-未处理、1-已建档、2-候选中、3-待回填真实型号 */
  archiveStatus?: number;
  archiveStatusName?: string;
  /** 已建档时关联的商品 */
  productId?: number | null;
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
  /** 是否包邮，不传按包邮 */
  freeShipping?: boolean;
  /** 附件 ID：新上传的或这条回价原有的 */
  attachmentIds?: number[];
}

/** 回价附件（图片或 PDF） */
export interface QuoteFile {
  id: number;
  kind: number;
  fileName: string;
  fileSize: number;
  contentType: string;
}

/** 型号历史询价明细 */
export interface ItemHistoryDetail {
  deals: { price: number; date: string; poNo: string; supplierName?: string }[];
  quotes: {
    price: number;
    date: string;
    channelName?: string;
    shopName?: string;
    conditionName?: string;
    leadTimeName?: string;
    freeShipping: boolean;
  }[];
}

/** 粘贴报价识别出的一行（不落库，预览修改后填进草稿） */
export interface PasteRow {
  raw: string;
  itemId?: number | null;
  model?: string | null;
  unitPrice?: number | null;
  taxIncluded: boolean;
  taxRate?: number | null;
  itemCondition: number;
  leadTime: number;
  note: string;
  status: 'MATCHED' | 'UNMATCHED' | 'NO_PRICE';
  /** 型号已报给客户，回价不能再修改 */
  locked: boolean;
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
/** 撤回回价前的预览：会撤回的型号、已报给客户不能撤回的型号、用了这些价格的草稿报价单 */
export interface WithdrawPreview {
  models: string[];
  locked: { model: string; quotationNo: string }[];
  drafts: { quotationNo: string; models: string[] }[];
}

/** 新建询盘时可关联的商机（这个客户进行中的商机） */
export interface UpdatePayload {
  source: number;
  inquiryDate: string;
  quoteDeadline: string;
  urgent: boolean;
  opportunityId?: number;
  /** 保留的已有附件（含铭牌图片），不在里面的删除 */
  keepAttachmentIds: number[];
  attachments: { fileKey: string; fileName: string }[];
  /** 补传的铭牌图片：不解析，先未对应型号 */
  nameplates?: { fileKey: string; fileName: string }[];
  remark?: string;
}

export interface OpportunityOption {
  id: number;
  opportunityCode: string;
  stageCode: string;
  stageName: string;
  firstContactDate?: string;
  /** 需求摘要：作为原始内容带入询盘 */
  demandSummary?: string;
  attachments: { id: number; fileName: string; fileSize: number }[];
}

/** 引用询盘单、未取消或作废的下游单据；有任一条时询盘不能删除 */
export interface InquiryDownstream {
  kind: 'TASK' | 'QUOTATION' | 'PI' | 'ORDER';
  id: number;
  no: string;
  statusName: string;
}

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

/** 列表「查看」弹窗的一行型号 */
export interface InquiryModelLine {
  brand: string;
  model: string;
  category: string;
  quantity?: number;
  unit?: string;
}

export const inquiryApi = {
  transferTasks: (taskIds: number[]) =>
    send<void>(
      'POST',
      '/api/v1/inquiry/customer-inquiries/sourcing-tasks/transfer',
      {
        taskIds,
      },
    ),
  upload: (file: File) =>
    upload<UploadedFile>(`${INQ}/attachments`, 'file', [file]),
  opportunityOptions: (customerId: number) =>
    get<OpportunityOption[]>(`${INQ}/opportunity-options`, { customerId }),
  copyFromOpportunity: (opportunityId: number, attachmentId: number) =>
    send<UploadedFile>('POST', `${INQ}/attachments/from-opportunity`, {
      opportunityId,
      attachmentId,
    }),
  customerType: (customerId: number) =>
    get<{
      customerType: number;
      wonCount: number;
      lastWonDate?: string;
      /** 客户档案的来源渠道 */
      sourceChannel?: number;
    }>(`${INQ}/customer-type`, { customerId }),
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
  models: (id: number) => get<InquiryModelLine[]>(`${INQ}/${id}/models`),
  /** 附件的临时访问链接（10 分钟有效），可直接用于预览 */
  attachmentBlobUrl: (id: number, attachmentId: number) =>
    fileUrl(`${INQ}/${id}/attachments/${attachmentId}`),
  /** 附件接口地址（不含 /url），给预览组件取签名链接 */
  attachmentEndpoint: (id: number, attachmentId: number) =>
    `${INQ}/${id}/attachments/${attachmentId}`,
  /** 改铭牌图片对应的型号，0 为未对应 */
  linkNameplate: (id: number, attachmentId: number, inquiryItemId: number) =>
    send<void>('PUT', `${INQ}/${id}/nameplates/${attachmentId}`, {
      inquiryItemId,
    }),
  startParse: (id: number) =>
    send<CustomerInquiry>('POST', `${INQ}/${id}/start-parse`),
  retryParse: (id: number) =>
    send<CustomerInquiry>('POST', `${INQ}/${id}/retry-parse`),
  draft: (id: number) => get<Draft>(`${INQ}/${id}/draft`),
  confirm: (id: number, rows: ConfirmRow[]) =>
    send<void>('POST', `${INQ}/${id}/confirm`, { rows }),
  startAppend: (
    id: number,
    body: {
      rawContent?: string;
      attachments?: { fileKey: string; fileName: string }[];
      nameplates?: { fileKey: string; fileName: string }[];
      manual?: boolean;
    },
  ) => send<InquiryAppend>('POST', `${INQ}/${id}/appends`, body),
  retryAppend: (id: number, appendId: number) =>
    send<InquiryAppend>('POST', `${INQ}/${id}/appends/${appendId}/retry`),
  appendDraft: (id: number, appendId: number) =>
    get<Draft>(`${INQ}/${id}/appends/${appendId}/draft`),
  confirmAppend: (id: number, appendId: number, rows: ConfirmRow[]) =>
    send<void>('POST', `${INQ}/${id}/appends/${appendId}/confirm`, { rows }),
  discardAppend: (id: number, appendId: number) =>
    send<void>('POST', `${INQ}/${id}/appends/${appendId}/discard`),
  /** 修改询盘：客户、询盘内容与型号不在这里改；补传的附件只保存，不解析 */
  update: (id: number, data: UpdatePayload) =>
    send<void>('PUT', `${INQ}/${id}`, data),
  cancel: (id: number) => send<void>('PUT', `${INQ}/${id}/cancel`),
  downstream: (id: number) =>
    get<InquiryDownstream[]>(`${INQ}/${id}/downstream`),
  remove: (id: number) => send<void>('DELETE', `${INQ}/${id}`),
  updateLevel: (id: number, level: number) =>
    send<void>('PUT', `${INQ}/${id}/level`, { level }),
  reviewed: (id: number) => send<void>('POST', `${INQ}/${id}/reviewed`),
};

export const boardApi = {
  board: (status: number, page: number, pageSize: number) =>
    get<Board>(BOARD, { status, page, pageSize }),
  purchasers: () => get<Purchaser[]>(`${BOARD}/purchasers`),
  taskDetail: (id: number) => get<BoardTaskDetail>(`${BOARD}/tasks/${id}`),
  nameplates: (id: number) =>
    get<TaskNameplates>(`${BOARD}/tasks/${id}/nameplates`),
  nameplateEndpoint: (id: number, attachmentId: number) =>
    `${BOARD}/tasks/${id}/nameplates/${attachmentId}`,
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

export interface BoardTaskItem {
  id: number;
  model: string;
  originalModel: string;
  quantity: number;
  unit: string;
  description: string;
  /** 英文描述（给客户看的单据用） */
  descriptionEn?: string;
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
  /** 英文描述（给客户看的单据用） */
  descriptionEn?: string;
  quotedBy: number;
  quotedByName?: string;
  submittedAt?: string;
  quotes: PriceRecord[];
  /** 同品牌同型号询价记录最低价 */
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
  nameplates: (id: number) => get<TaskNameplates>(`${MY}/${id}/nameplates`),
  nameplateEndpoint: (id: number, attachmentId: number) =>
    `${MY}/${id}/nameplates/${attachmentId}`,
  list: (done: boolean, keyword?: string) =>
    get<MyTask[]>(MY, { done, keyword: keyword || undefined }),
  /** 已回价：分页 */
  donePage: (page: number, pageSize: number, keyword?: string) =>
    get<{ total: number; records: MyTask[] }>(`${MY}/done`, {
      page,
      pageSize,
      keyword: keyword || undefined,
    }),
  history: (id: number, itemId: number) =>
    get<ItemHistoryDetail>(`${MY}/${id}/items/${itemId}/history`),
  detail: (id: number) =>
    get<{ task: MyTask; items: MyTaskItem[]; reviewRequired?: boolean }>(
      `${MY}/${id}`,
    ),
  save: (id: number, items: ItemQuotes[], submit: boolean) =>
    send<void>('PUT', `${MY}/${id}/quotes`, { items, submit }),
  pastePreview: (id: number, text: string) =>
    send<{ rows: PasteRow[]; common: string }>(
      'POST',
      `${MY}/${id}/paste-preview`,
      { text },
    ),
  /** 采购回填真实型号：离开输入框即保存，不受「已报给客户」限制 */
  saveActualModel: (id: number, itemId: number, actualModel: string) =>
    send<{
      actualModel: string;
      archiveStatus: number;
      archiveStatusName?: string;
    }>('PUT', `${MY}/${id}/items/${itemId}/actual-model`, { actualModel }),
  returnTask: (id: number, reason: number, note?: string, itemIds?: number[]) =>
    send<void>('POST', `${MY}/${id}/return`, { reason, note, itemIds }),
  returnPreview: (id: number) =>
    get<ReturnPreview>(`${MY}/${id}/return-preview`),
  salesPending: (page: number, pageSize: number) =>
    get<SalesPending>(`${MY}/sales-pending`, { page, pageSize }),
  claim: (taskIds: number[]) => send<void>('POST', `${MY}/claim`, { taskIds }),
  withdrawPreview: (id: number) =>
    get<WithdrawPreview>(`${MY}/${id}/withdraw-preview`),
  withdraw: (id: number) => send<void>('POST', `${MY}/${id}/withdraw`),
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
