import { request } from '@umijs/max';
import type { FileRef } from '../payables/service';

const BASE = '/api/v1/finance/refunds';
const quiet = { skipErrorHandler: true } as const;
const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const post = <T>(url: string, data?: unknown) =>
  request<{ data: T }>(url, { method: 'POST', data, ...quiet }).then(
    (r) => r.data,
  );

export const DICT_REFUND_REASON = 'refund_reason';

/** 退款方式 */
export const REFUND_METHODS = [
  { value: 1, label: '原路退回平台' },
  { value: 2, label: '银行汇款' },
];

export interface Refund {
  id: number;
  refundNo: string;
  soId: number;
  soNo?: string;
  customerName?: string;
  refundDate: string;
  reason: string;
  reasonName?: string;
  currencyCode: string;
  amount: number;
  exchangeRate: number;
  amountCny: number;
  method: number;
  remark?: string;
  /** 1-有效、2-已作废 */
  status: number;
  voidReason?: string;
  attachments: FileRef[];
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface Refundable {
  soId: number;
  soNo: string;
  currencyCode: string;
  received: number;
  refunded: number;
  refundable: number;
  averageRate: number;
}

export interface RefundQuery {
  page: number;
  pageSize: number;
  keyword?: string;
  reason?: string;
  dateFrom?: string;
  dateTo?: string;
  showVoid?: boolean;
}

export const refundApi = {
  page: (q: RefundQuery) => get<{ total: number; records: Refund[] }>(BASE, q),
  total: (q: Omit<RefundQuery, 'page' | 'pageSize'>) =>
    get<number>(`${BASE}/total`, q),
  refundable: (soId: number) => get<Refundable>(`${BASE}/refundable/${soId}`),
  create: (body: {
    soId: number;
    refundDate: string;
    reason: string;
    amount: number;
    amountCny?: number;
    method: number;
    remark?: string;
    attachmentIds: number[];
  }) => post<Refund>(BASE, body),
  voidRefund: (id: number, reason: string) =>
    post<Refund>(`${BASE}/${id}/void`, { reason }),
};
