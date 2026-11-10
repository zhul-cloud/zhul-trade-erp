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

/** 售后退款的去向 */
export const REFUND_METHODS = [
  { value: 1, label: '原路退回平台' },
  { value: 2, label: '银行汇款' },
  { value: 3, label: '转客户余额' },
];

/** 退款单类型：售后退款冲减订单利润，余额提现不影响利润 */
export const REFUND_TYPE_AFTER_SALES = 1;
export const REFUND_TYPE_WITHDRAW = 2;
export const REFUND_TO_CREDIT = 3;

export interface Refund {
  /** 历史迁移的单据（只读） */
  migrated?: boolean;
  id: number;
  refundNo: string;
  /** 1-售后退款、2-余额提现 */
  refundType: number;
  soId?: number;
  customerId?: number;
  soNo?: string;
  /** 关联售后单与来源退款申请 */
  afterSalesId?: number | null;
  asNo?: string;
  refundRequestId?: number | null;
  requestNo?: string;
  customerName?: string;
  refundDate: string;
  reason: string;
  reasonName?: string;
  currencyCode: string;
  amount: number;
  exchangeRate: number;
  amountCny: number;
  method: number;
  methodName?: string;
  payeeAccount?: string;
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
  /** 转客户余额的折算汇率：现金收款的加权到账汇率 */
  creditRate: number;
  /** 有平台收款：只能原路退回 */
  platformOnly: boolean;
}

export interface RefundTotals {
  afterSales: number;
  toCredit: number;
  afterSalesCount: number;
  withdraw: number;
  withdrawCount: number;
}

export interface RefundQuery {
  page: number;
  pageSize: number;
  keyword?: string;
  reason?: string;
  dateFrom?: string;
  dateTo?: string;
  showVoid?: boolean;
  refundType?: number;
  method?: number;
}

export const refundApi = {
  page: (q: RefundQuery) => get<{ total: number; records: Refund[] }>(BASE, q),
  total: (q: Omit<RefundQuery, 'page' | 'pageSize'>) =>
    get<number>(`${BASE}/total`, q),
  totals: (q: Omit<RefundQuery, 'page' | 'pageSize'>) =>
    get<RefundTotals>(`${BASE}/totals`, q),
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
    afterSalesId?: number;
  }) => post<Refund>(BASE, body),
  voidRefund: (id: number, reason: string) =>
    post<Refund>(`${BASE}/${id}/void`, { reason }),
};

// ---------------------------------------------------------------- 退款申请（spec finance/refund-request）

const RQ = '/api/v1/finance/refund-requests';

export const RQ_STATUS = {
  PENDING_APPROVAL: 1,
  PENDING_FINANCE: 2,
  EXECUTED: 3,
  REJECTED: 4,
  WITHDRAWN: 5,
} as const;

export interface RefundRequest {
  id: number;
  requestNo: string;
  afterSalesId?: number | null;
  asNo?: string;
  soId: number;
  soNo?: string;
  customerName?: string;
  customerContact?: string;
  currencyCode: string;
  amount: number;
  reason: string;
  reasonName?: string;
  method: number;
  methodName?: string;
  payeeInfo?: string;
  remark?: string;
  /** 1-待审批、2-待财务处理、3-已执行、4-已驳回、5-已撤回 */
  status: number;
  statusName: string;
  applicantName?: string;
  approverName?: string;
  approvedAt?: string;
  handlerName?: string;
  handledAt?: string;
  rejectReason?: string;
  refundId?: number | null;
  refundNo?: string;
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface RequestRefundable {
  soId: number;
  soNo: string;
  currencyCode: string;
  received: number;
  refunded: number;
  pending: number;
  available: number;
  platformOnly: boolean;
  approvalRequired: boolean;
}

export const refundRequestApi = {
  page: (q: {
    page: number;
    pageSize: number;
    status?: number;
    keyword?: string;
  }) => post<{ total: number; records: RefundRequest[] }>(`${RQ}/page`, q),
  counts: () =>
    get<{
      pendingApproval: number;
      pendingFinance: number;
      approvalRequired: number;
    }>(`${RQ}/counts`),
  refundable: (soId: number) =>
    get<RequestRefundable>(`${RQ}/refundable/${soId}`),
  ofOrder: (soId: number) => get<RefundRequest[]>(`${RQ}/order/${soId}`),
  apply: (body: {
    soId: number;
    afterSalesId?: number;
    amount: number;
    reason: string;
    method: number;
    payeeInfo?: string;
    remark?: string;
  }) => post<RefundRequest>(RQ, body),
  withdraw: (id: number) => post<RefundRequest>(`${RQ}/${id}/withdraw`, {}),
  approve: (id: number) => post<RefundRequest>(`${RQ}/${id}/approve`, {}),
  rejectApproval: (id: number, reason: string) =>
    post<RefundRequest>(`${RQ}/${id}/reject-approval`, { reason }),
  reject: (id: number, reason: string) =>
    post<RefundRequest>(`${RQ}/${id}/reject`, { reason }),
  execute: (
    id: number,
    body: {
      refundDate: string;
      reason: string;
      amount: number;
      amountCny?: number;
      method: number;
      remark?: string;
      attachmentIds: number[];
    },
  ) => post<RefundRequest>(`${RQ}/${id}/execute`, body),
};

/** 订单的售后单（退款单、费用单关联用） */
export const afterSalesOptions = (soId: number) =>
  get<{ id: number; asNo: string; statusName: string; symptom: string }[]>(
    `/api/v1/sales/after-sales/order/${soId}`,
  );
