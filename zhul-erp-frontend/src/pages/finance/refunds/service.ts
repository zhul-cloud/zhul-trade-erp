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
  }) => post<Refund>(BASE, body),
  voidRefund: (id: number, reason: string) =>
    post<Refund>(`${BASE}/${id}/void`, { reason }),
};
