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
  /** 回填自哪个询盘原文 */
  originalModel?: string;
  sources?: CandidateSource[];
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
};
