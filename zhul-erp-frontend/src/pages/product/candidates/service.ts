import { request } from '@umijs/max';
import type { PageResult, ProductOption } from '../service';

export { readBizError } from '../service';

export interface CandidateSource {
  sourceType: number;
  sourceTypeName: string;
  customerInquiryId?: number;
  inquiryCode?: string;
  originalModel?: string;
  soId?: number;
  soNo?: string;
  /** 导入来源：文件名与导入人 */
  fileName?: string;
  createBy?: string;
  createTime?: string;
}

export interface Candidate {
  id: number;
  brandId?: number | null;
  brandName?: string;
  brandText: string;
  mpnRaw: string;
  /** 订货号（物料号、料号），建档时带到商品 */
  orderCode?: string;
  categoryId?: number | null;
  categoryName?: string;
  categoryText?: string;
  /** 品类是否 AI 建议（0-否、1-是） */
  aiCategory?: number;
  /** 品牌 AI 解析结果（0-未解析、1-匹配到已有品牌、2-未识别） */
  aiBrand?: number;
  /** AI 给出的品牌名（未识别时供审核参考） */
  aiBrandName?: string;
  productName?: string;
  description?: string;
  descriptionEn?: string;
  /** 1-待审核、2-已建档、3-已并入、4-已驳回 */
  status: number;
  statusName: string;
  /** 1-询盘出现、2-采购问到有货、3-已成交 */
  level: number;
  levelName: string;
  inquiryCount: number;
  dealCount: number;
  firstSeenAt?: string;
  lastSeenAt?: string;
  productId?: number | null;
  productLabel?: string;
  /** 候选商品被各类单据引用的条数（单据类型 → 条数），待审核时才有 */
  references?: Record<string, number>;
  /** 已被销售订单及之后的单据引用：不能驳回，只能建档或并入 */
  usedByOrders?: boolean;
  rejectReason?: number;
  rejectReasonName?: string;
  rejectNote?: string;
  reviewedByName?: string;
  reviewedAt?: string;
  /** 建议的生命周期（商品口径 1-6）与依据：取来源询盘型号里采购最近核实的生产状态 */
  suggestedLifecycle?: number;
  suggestedLifecycleSource?: string;
  /** 回填自哪个询盘原文 */
  originalModel?: string;
  /** 列表为最近 3 条，详情为全部；只含本公司（平台账号为全部） */
  sources?: CandidateSource[];
  /** 本公司来源条数 */
  mineSourceCount?: number;
  otherSourceCount?: number;
  createTime: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface ApproveBody {
  brandMode?: 'EXISTING' | 'ALIAS' | 'NEW';
  brandId?: number;
  brandName?: string;
  categoryId?: number;
  seriesId?: number | null;
  newSeriesName?: string;
  mpnRaw?: string;
  mpnDisplay?: string;
  productName?: string;
  shortDescription?: string;
  lifecycleStatus?: number;
  lifecycleSource?: string;
  /** 改型号后与已建档商品重复时，确认并入 */
  confirmMerge?: boolean;
}

/** 通过建档时会并入已有商品：CANDIDATE_WILL_MERGE 的 detail */
export interface WillMerge {
  targetId: number;
  targetLabel: string;
  references: Record<string, number>;
}

/** 待关联清单的一组：同品牌原文 + 归一化型号的未关联商品的单据明细 */
export interface UnlinkedGroup {
  key: string;
  brand: string;
  model: string;
  docs: Record<string, number>;
  lineCount: number;
  lastSeenAt?: string;
  suggestion?: ProductOption;
}

const quiet = { skipErrorHandler: true } as const;
const send = <T>(method: string, url: string, data?: unknown) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);
const C = '/api/v1/product/candidates';

export type CandidateFilter = {
  keyword?: string;
  level?: number;
  /** 1 品牌已确认、2 待确认品牌 */
  brandState?: number;
  /** 1 已选品类、2 未选品类 */
  categoryState?: number;
};

export const candidateApi = {
  page: (
    q: CandidateFilter & { page: number; pageSize: number; status: number },
  ) => send<PageResult<Candidate>>('POST', `${C}/page`, q),
  /** 各页签条数，按同样的筛选条件统计 */
  counts: (q: CandidateFilter) =>
    send<{
      pending: number;
      archived: number;
      merged: number;
      rejected: number;
    }>('POST', `${C}/counts`, q),
  detail: (id: number) => send<Candidate>('GET', `${C}/${id}`),
  approve: (id: number, body: ApproveBody) =>
    send<Candidate>('POST', `${C}/${id}/approve`, body),
  batchApprove: (ids: number[]) =>
    send<{
      approved: number;
      skipped: { id: number; label: string; reason: string }[];
    }>('POST', `${C}/batch-approve`, { ids }),
  /** 批量修改品牌、品类：只传要改的那一项；已审核的跳过 */
  batchEdit: (ids: number[], brandId?: number, categoryId?: number) =>
    send<{ updated: number; skipped: number }>('POST', `${C}/batch-edit`, {
      ids,
      brandId,
      categoryId,
    }),
  /** 批量 AI 解析品类或品牌；全部跳过时不产生任务，taskId 为空 */
  aiFill: (mode: AiFillMode, ids: number[]) =>
    send<AiFill>('POST', `${C}/ai-fill`, { mode, ids }),
  aiFillStatus: (taskId: number) =>
    send<AiFill>('GET', `${C}/ai-fill/${taskId}`),
  merge: (id: number, productId: number, reason?: string) =>
    send<Candidate>('POST', `${C}/${id}/merge`, { productId, reason }),
  /** 候选商品被各类单据引用的条数 */
  references: (id: number) =>
    send<Record<string, number>>('GET', `${C}/${id}/references`),
  /** 待关联清单：还没关联商品的单据明细，按型号归并 */
  unlinked: (q: { keyword?: string; page: number; pageSize: number }) =>
    request<{ data: PageResult<UnlinkedGroup> }>(`${C}/unlinked`, {
      params: { keyword: q.keyword, page: q.page, page_size: q.pageSize },
      ...quiet,
    }).then((r) => r.data),
  /** 选择商品（productId）或填写真实型号（brand + model）后批量关联，返回关联的条数 */
  linkUnlinked: (body: {
    key: string;
    productId?: number;
    brand?: string;
    model?: string;
  }) => send<number>('POST', `${C}/unlinked/link`, body),
  reject: (id: number, reason: number, note?: string) =>
    send<Candidate>('POST', `${C}/${id}/reject`, { reason, note }),
  reopen: (id: number) => send<Candidate>('POST', `${C}/${id}/reopen`),
  /** 上传商品文件导入候选：预览不落库，确认时服务端重新解析 */
  importPreview: (files: ImportFile[]) =>
    send<ImportPreview[]>('POST', `${C}/import/preview`, { files }),
  importConfirm: (files: ImportFile[]) =>
    send<ImportResult>('POST', `${C}/import/confirm`, { files }),
};

export type AiFillMode = 'category' | 'brand';

/** 批量 AI 解析的进度与结果 */
export interface AiFill {
  taskId?: number | null;
  mode?: AiFillMode;
  /** 1-排队中、2-处理中、3-已完成、4-失败 */
  status?: number;
  total?: number;
  skipped?: number;
  filled?: number;
  matched?: number;
  unmatched?: number;
  unchanged?: number;
  errorMessage?: string;
}

export interface ImportFile {
  fileName: string;
  content: string;
}

/** NEW-新候选、IN_POOL-已在候选池、IN_PRODUCT-已在商品库、REJECTED-曾被驳回、DUPLICATE-重复、INVALID-不能导入 */
export type ImportResultCode =
  | 'NEW'
  | 'IN_POOL'
  | 'IN_PRODUCT'
  | 'REJECTED'
  | 'DUPLICATE'
  | 'INVALID';

export interface ImportPreview {
  fileName: string;
  brand?: string;
  /** 为空表示品牌未识别 */
  brandId?: number;
  brandName?: string;
  model?: string;
  categoryId?: number;
  categoryName?: string;
  productName?: string;
  description?: string;
  descriptionEn?: string;
  result: ImportResultCode;
  resultName: string;
  note?: string;
  candidateId?: number;
  productId?: number;
}

export interface ImportResult {
  created: number;
  appended: number;
  skipped: number;
  failed: number;
  items: {
    fileName: string;
    model?: string;
    outcome: 'CREATED' | 'APPENDED' | 'SKIPPED' | 'FAILED';
    message: string;
  }[];
}
