import { request } from '@umijs/max';
import type { PageResult } from '../service';

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
  categoryId?: number | null;
  categoryName?: string;
  categoryText?: string;
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
}

const quiet = { skipErrorHandler: true } as const;
const send = <T>(method: string, url: string, data?: unknown) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);
const C = '/api/v1/product/candidates';

export const candidateApi = {
  page: (q: {
    page: number;
    pageSize: number;
    status: number;
    keyword?: string;
    level?: number;
  }) => send<PageResult<Candidate>>('POST', `${C}/page`, q),
  counts: () => send<{ pending: number }>('GET', `${C}/counts`),
  detail: (id: number) => send<Candidate>('GET', `${C}/${id}`),
  approve: (id: number, body: ApproveBody) =>
    send<Candidate>('POST', `${C}/${id}/approve`, body),
  batchApprove: (ids: number[]) =>
    send<{
      approved: number;
      skipped: { id: number; label: string; reason: string }[];
    }>('POST', `${C}/batch-approve`, { ids }),
  merge: (id: number, productId: number) =>
    send<Candidate>('POST', `${C}/${id}/merge`, { productId }),
  reject: (id: number, reason: number, note?: string) =>
    send<Candidate>('POST', `${C}/${id}/reject`, { reason, note }),
  reopen: (id: number) => send<Candidate>('POST', `${C}/${id}/reopen`),
  /** 上传商品文件导入候选：预览不落库，确认时服务端重新解析 */
  importPreview: (files: ImportFile[]) =>
    send<ImportPreview[]>('POST', `${C}/import/preview`, { files }),
  importConfirm: (files: ImportFile[]) =>
    send<ImportResult>('POST', `${C}/import/confirm`, { files }),
};

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
