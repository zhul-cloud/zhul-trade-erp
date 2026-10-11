import { request } from '@umijs/max';
import type { Attachment } from '@/pages/warehouse/service';

const AP = '/api/v1/finance/payables';
const PY = '/api/v1/finance/payments';
const quiet = { skipErrorHandler: true } as const;

const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(url: string, data?: unknown) =>
  request<{ data: T }>(url, { method: 'POST', data, ...quiet }).then(
    (r) => r.data,
  );

/** 应付单与期次状态 */
export const PAY_STATUS = {
  UNPAID: 1,
  PARTIAL: 2,
  PAID: 3,
  CLOSED: 4,
} as const;
/** 收款方类型 */
export const PAYEE = {
  SUPPLIER: 1,
  SHOP: 2,
  FORWARDER: 3,
  OTHER: 4,
  EMPLOYEE: 5,
} as const;
/** 来源类型 */
export const SOURCE = { PO: 1, STATEMENT: 2, EXPENSE: 3 } as const;
/** 付款方式 */
export const METHOD = { COMPANY: 1, ADVANCE: 2 } as const;

/** OVERDUE 已逾期、SOON 7 天内到期、NORMAL 未到期、WAITING 待入库 */
export type DueState = 'OVERDUE' | 'SOON' | 'NORMAL' | 'WAITING';

export interface FileRef {
  id: number;
  fileName: string;
  kind: number;
}

export interface Installment {
  seq: number;
  ratio: number;
  triggerType: number;
  offsetDays?: number;
  triggerName: string;
  amount: number;
  dueDate?: string;
  paidAmount: number;
  status: number;
  dueState?: DueState;
  dueDays?: number;
}

export interface PaymentLine {
  paymentId: number;
  paymentNo: string;
  /** 1-付款、2-供应商退款 */
  kind: number;
  /** 0-实付核销、1-欠款抵扣转入、2-退款冲减、3-欠款抵扣转出 */
  relation: number;
  method: number;
  accountName?: string;
  advanceUser?: string;
  payDate: string;
  amount: number;
  amountCny: number;
  otherPayableNo?: string;
  voided: boolean;
  createBy?: string;
  attachments: FileRef[];
}

export interface Payable {
  id: number;
  payableNo: string;
  payeeType: number;
  supplierId?: number;
  shopPlatform?: string;
  shopName?: string;
  payeeName: string;
  sourceType: number;
  sourceId: number;
  sourceNo: string;
  currencyCode: string;
  amount: number;
  exchangeRate: number;
  amountCny: number;
  paidAmount: number;
  paidCny: number;
  unpaidAmount: number;
  creditAmount: number;
  exchangeDiff?: number;
  status: number;
  nextDueDate?: string;
  dueState?: DueState;
  dueDays?: number;
  paymentTerms?: string;
  installments: Installment[];
  payments: PaymentLine[];
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface PayableStats {
  monthDue: number;
  monthDueCount: number;
  overdue: number;
  overdueCount: number;
  soon: number;
  soonCount: number;
  credit: number;
  creditCount: number;
}

export interface PayableQuery {
  page: number;
  pageSize: number;
  keyword?: string;
  payeeType?: number;
  status?: number;
  /** OVERDUE、SOON、WAITING、MONTH */
  due?: string;
  hasCredit?: boolean;
  currencyCode?: string;
  showClosed?: boolean;
}

export interface PayeeOption {
  key: string;
  /** 该收款方的任一张应付单，用来取同一收款方的未付应付单 */
  anchorId: number;
  payeeType: number;
  supplierId?: number;
  shopPlatform?: string;
  shopName?: string;
  payeeName: string;
  currencyCode: string;
  unpaid: number;
  credit: number;
}

export interface Payment {
  id: number;
  paymentNo: string;
  kind: number;
  payeeType: number;
  payeeName: string;
  method: number;
  bankAccountId?: number;
  accountName?: string;
  advanceUser?: string;
  reimburseStatus: number;
  payDate: string;
  currencyCode: string;
  amount: number;
  amountCny: number;
  actualRate: number;
  creditUsed: number;
  remark?: string;
  voided: boolean;
  voidBy?: string;
  voidReason?: string;
  voidAt?: string;
  allocations: {
    payableId: number;
    payableNo: string;
    sourceNo: string;
    amount: number;
    fromCredit: number;
    creditPayableNo?: string;
  }[];
  attachments: FileRef[];
  canVoid: boolean;
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface PaymentQuery {
  page: number;
  pageSize: number;
  keyword?: string;
  dateFrom?: string;
  dateTo?: string;
  method?: number;
  advanceUser?: string;
  currencyCode?: string;
  kind?: number;
  showVoid?: boolean;
}

export interface PaymentStats {
  total: number;
  company: number;
  advance: number;
  /** 报销付款：公司付给员工，不计入合计 */
  reimburse: number;
  count: number;
}

export interface RegisterPaymentBody {
  method: number;
  bankAccountId?: number;
  payDate: string;
  amount: number;
  amountCny?: number;
  remark?: string;
  attachmentIds: number[];
  allocations: { payableId: number; amount: number }[];
  credits?: { payableId: number; amount: number }[];
  idempotencyKey: string;
}

export interface RefundBody {
  payableId: number;
  amount: number;
  amountCny?: number;
  refundDate: string;
  bankAccountId: number;
  attachmentIds: number[];
  remark?: string;
  idempotencyKey: string;
}

/** 财务管理 → 应付管理、付款记录 */
export const payableApi = {
  page: (q: PayableQuery) => get<{ total: number; records: Payable[] }>(AP, q),
  stats: () => get<PayableStats>(`${AP}/stats`),
  detail: (id: number) => get<Payable>(`${AP}/${id}`),
  /** 采购单、对账单的付款面板；还没有应付单时为空 */
  bySource: (sourceType: number, sourceId: number) =>
    get<Payable | null>(`${AP}/by-source`, { sourceType, sourceId }),
  payees: (keyword?: string) => get<PayeeOption[]>(`${AP}/payees`, { keyword }),
  /** 与这张应付单同一收款方、同一币种，有未付或欠款的应付单 */
  open: (id: number) => get<Payable[]>(`${AP}/${id}/open`),
};

export const paymentApi = {
  page: (q: PaymentQuery) => get<{ total: number; records: Payment[] }>(PY, q),
  stats: (q: Omit<PaymentQuery, 'page' | 'pageSize'>) =>
    get<PaymentStats>(`${PY}/stats`, q),
  register: (body: RegisterPaymentBody) => send<Payment>(PY, body),
  refund: (body: RefundBody) => send<Payment>(`${PY}/refunds`, body),
  voidPayment: (id: number, reason: string) =>
    send<Payment>(`${PY}/${id}/void`, { reason }),
};

export type { Attachment };
