import { request } from '@umijs/max';
import type { FileRef, Payment } from '../payables/service';

const BX = '/api/v1/finance/reimbursements';
const FE = '/api/v1/finance/expenses';
const quiet = { skipErrorHandler: true } as const;

const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(method: string, url: string, data?: unknown) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);

/** 待报销项来源 */
export const ITEM = {
  PAYMENT: 1,
  COURIER: 2,
  RETURN_FREIGHT: 3,
  EXPENSE: 4,
  REFUND: 5,
} as const;

export const ITEM_NAMES: Record<number, string> = {
  1: '采购货款',
  2: '国内快递',
  3: '退货运费',
  4: '费用单',
  5: '收到退款',
};

/** 报销单状态 */
export const BX_STATUS = { PENDING: 1, PAID: 2, WITHDRAWN: 3 } as const;

export interface AdvanceItem {
  sourceType: number;
  sourceId: number;
  sourceNo: string;
  occurDate?: string;
  description: string;
  amount: number;
  /** PAYMENT、COURIER_PROOF、RETURN_PROOF、EXPENSE */
  proofOwnerType: string;
  proofOwnerId: number;
  proofs: FileRef[];
  /** 截图可以在这里补传 */
  proofUploadable: boolean;
}

export interface RefundReminder {
  payableId: number;
  payableNo: string;
  payeeName: string;
  sourceNo: string;
  currencyCode: string;
  creditAmount: number;
  days: number;
}

export interface Reimbursement {
  id: number;
  reimburseNo: string;
  applicantId: number;
  applicantName: string;
  submittedAt: string;
  lineCount: number;
  totalAmount: number;
  status: number;
  withdrawReason?: string;
  payAccountId?: number;
  payAccountName?: string;
  /** 付款方式：1-公司对公、3-私对私 */
  payMethod?: number;
  /** 私对私时的付款个人账户 */
  payPayerAccount?: string;
  payDate?: string;
  paidBy?: string;
  paidAt?: string;
  payRemark?: string;
  paymentNo?: string;
  payProofs: FileRef[];
  lines: AdvanceItem[];
  canWithdraw: boolean;
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface ReimbursementQuery {
  page: number;
  pageSize: number;
  keyword?: string;
  status?: number;
  dateFrom?: string;
  dateTo?: string;
}

export const reimburseApi = {
  pending: () => get<AdvanceItem[]>(`${BX}/pending`),
  reminders: () => get<RefundReminder[]>(`${BX}/reminders`),
  addProof: (sourceType: number, sourceId: number, attachmentIds: number[]) =>
    send<AdvanceItem>('POST', `${BX}/proofs`, {
      sourceType,
      sourceId,
      attachmentIds,
    }),
  refundReceived: (body: {
    payableId: number;
    amount: number;
    amountCny?: number;
    receivedDate: string;
    attachmentIds: number[];
    remark?: string;
    idempotencyKey: string;
  }) => send<Payment>('POST', `${BX}/refunds`, body),
  submit: (items: { sourceType: number; sourceId: number }[]) =>
    send<Reimbursement>('POST', BX, { items }),
  page: (q: ReimbursementQuery) =>
    get<{ total: number; records: Reimbursement[] }>(BX, q),
  pendingTotal: () => get<number>(`${BX}/pending-total`),
  detail: (id: number) => get<Reimbursement>(`${BX}/${id}`),
  withdraw: (id: number, reason: string) =>
    send<Reimbursement>('POST', `${BX}/${id}/withdraw`, { reason }),
  pay: (
    id: number,
    body: {
      /** 1-公司对公（默认）、3-私对私 */
      method?: number;
      bankAccountId?: number;
      payerAccount?: string;
      payDate: string;
      attachmentIds: number[];
      remark?: string;
    },
  ) => send<Reimbursement>('POST', `${BX}/${id}/pay`, body),
  unpay: (id: number, reason: string) =>
    send<Reimbursement>('POST', `${BX}/${id}/unpay`, { reason }),
};

// ---------------------------------------------------------------- 费用单

export interface ExpenseAlloc {
  soId: number;
  soNo?: string;
  soItemId: number;
  model?: string;
  brand?: string;
  amount: number;
  amountCny?: number;
}

export interface Expense {
  /** 历史迁移的单据（只读） */
  migrated?: boolean;
  id: number;
  expenseNo: string;
  name: string;
  /** 所属事业部；为空表示「公共」 */
  businessUnitId?: number;
  category: string;
  categoryName?: string;
  amortizeMonths: number;
  /** 关联了订单型号；不关联的是运营成本 */
  linked?: boolean;
  occurDate: string;
  /** 1-供应商、2-线上店铺、3-货代、4-其他 */
  payeeType: number;
  supplierId?: number;
  shopPlatform?: string;
  shopName?: string;
  payeeName: string;
  currencyCode: string;
  amount: number;
  exchangeRate: number;
  amountCny: number;
  /** 1-公司对公、2-员工垫付 */
  method: number;
  advanceUserId?: number;
  advanceUserName?: string;
  remark?: string;
  /** 1-有效、2-已作废 */
  status: number;
  voidReason?: string;
  payableId?: number;
  payStatus?: number;
  paidAmount?: number;
  reimburseNo?: string;
  reimburseStatus?: number;
  locked: boolean;
  allocations: ExpenseAlloc[];
  attachments: FileRef[];
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface SoItemOption {
  soId: number;
  soNo: string;
  soItemId: number;
  lineNo: number;
  model: string;
  brand?: string;
  quantity: number;
}

export interface SaveExpenseBody {
  name: string;
  /** 所属事业部；为空表示「公共」 */
  businessUnitId?: number;
  category?: string;
  amortizeMonths?: number;
  occurDate: string;
  payeeType: number;
  supplierId?: number;
  shopChannel?: number;
  shopName?: string;
  payeeName?: string;
  currencyCode: string;
  amount: number;
  method: number;
  advanceUserId?: number;
  remark?: string;
  attachmentIds: number[];
  allocations: { soItemId: number; amount: number }[];
}

export interface ExpenseQuery {
  page: number;
  pageSize: number;
  keyword?: string;
  soNo?: string;
  dateFrom?: string;
  dateTo?: string;
  method?: number;
  /** 所属事业部筛选：0 表示「公共」 */
  businessUnitId?: number;
  category?: string;
  /** true 只看关联订单的；false 只看运营成本 */
  linked?: boolean;
  showVoid?: boolean;
}

export const expenseApi = {
  page: (q: ExpenseQuery) => get<{ total: number; records: Expense[] }>(FE, q),
  monthTotal: (q: Omit<ExpenseQuery, 'page' | 'pageSize'>) =>
    get<number>(`${FE}/month-total`, q),
  detail: (id: number) => get<Expense>(`${FE}/${id}`),
  soItems: (keyword?: string) =>
    get<SoItemOption[]>(`${FE}/so-items`, { keyword }),
  create: (body: SaveExpenseBody) => send<Expense>('POST', FE, body),
  update: (id: number, body: SaveExpenseBody) =>
    send<Expense>('PUT', `${FE}/${id}`, body),
  voidExpense: (id: number, reason: string) =>
    send<Expense>('POST', `${FE}/${id}/void`, { reason }),
};
