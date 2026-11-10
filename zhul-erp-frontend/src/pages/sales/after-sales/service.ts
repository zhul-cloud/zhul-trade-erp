import { request } from '@umijs/max';
import type { Attachment } from '@/pages/warehouse/service';

const AS = '/api/v1/sales/after-sales';
const RT = '/api/v1/warehouse/returns';
const quiet = { skipErrorHandler: true } as const;
const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(method: 'POST' | 'PUT', url: string, data?: unknown) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);

export const DICT_LEVEL = 'after_sales_level';
export const DICT_REASON = 'after_sales_reason';
export const DICT_SOLUTION = 'after_sales_solution';

export const AS_STATUS = { OPEN: 1, WAITING: 2, CLOSED: 3, CANCELLED: 4 };
export const RT_STATUS = { PENDING: 1, RECEIVED: 2, DONE: 3, VOID: 4 };
export const DISP = { SUPPLIER: 1, HOLD: 2, SCRAP: 3 };

export type Tone =
  | 'gray'
  | 'cyan'
  | 'accent'
  | 'violet'
  | 'orange'
  | 'green'
  | 'red'
  | 'mute';

export const LEVEL_TONE: Record<string, Tone> = {
  PENDING: 'gray',
  S1: 'red',
  S2: 'orange',
  S3: 'accent',
  S4: 'gray',
  S5: 'cyan',
};
export const AS_STATUS_TONE: Record<number, Tone> = {
  1: 'accent',
  2: 'violet',
  3: 'green',
  4: 'mute',
};
export const RT_STATUS_TONE: Record<number, Tone> = {
  1: 'orange',
  2: 'violet',
  3: 'green',
  4: 'mute',
};

/** 等级短名：S1–S5 直接显示代码，待确认显示中文 */
export const levelShort = (code?: string, name?: string) =>
  code && code !== 'PENDING' ? code : (name ?? '待确认');

export interface AfterSalesItem {
  id: number;
  soItemId?: number | null;
  model: string;
  brand?: string;
  quantity: number;
  shippedQty?: number | null;
}

export interface AfterSalesLog {
  id: number;
  logType: string;
  logTypeName: string;
  content: string;
  /** 已告知客户（算首次响应 / 更新客户） */
  customerNotified?: boolean;
  operatorName?: string;
  createTime: string;
  attachments?: Attachment[];
}

export interface AfterSales {
  id: number;
  asNo: string;
  customerId: number;
  customerName?: string;
  customerContact?: string;
  soId?: number | null;
  soNo?: string;
  currencyCode?: string;
  levelCode: string;
  levelName?: string;
  symptom: string;
  customerRequest?: string;
  receivedAt?: string;
  reasonCode: string;
  reasonName?: string;
  solutions: string[];
  solutionNames: string[];
  ownerId: number;
  ownerName?: string;
  nextAction?: string;
  nextUpdateAt?: string | null;
  /** 已过截止时间 */
  overdue: boolean;
  /** 处理时限（spec sales/after-sales「处理时限」） */
  respondedAt?: string | null;
  lastContactAt?: string | null;
  /** 按等级算出的截止时间（不含手填） */
  levelDueAt?: string | null;
  dueAt?: string | null;
  /** 0-无、1-首次响应、2-更新客户、3-手填时间 */
  dueType?: number;
  dueTypeName?: string | null;
  dueState?: DueState | null;
  /** 1-处理中、2-待客户确认、3-已结案、4-已撤销 */
  status: number;
  statusName: string;
  result?: string;
  /** 结案时选填的案例沉淀 */
  caseNote?: string;
  closedAt?: string;
  cancelReason?: string;
  itemSummary?: string;
  editable: boolean;
  items?: AfterSalesItem[];
  attachments?: Attachment[];
  logs?: AfterSalesLog[];
  returns?: {
    id: number;
    returnNo: string;
    status: number;
    statusName: string;
    summary: string;
  }[];
  requests?: {
    id: number;
    requestNo: string;
    status: number;
    statusName: string;
    currencyCode: string;
    amount: number;
  }[];
  refunds?: {
    id: number;
    refundNo: string;
    refundDate: string;
    currencyCode: string;
    amount: number;
    amountCny: number;
    methodName?: string;
  }[];
  expenses?: {
    id: number;
    expenseNo: string;
    name: string;
    amountCny: number;
  }[];
  replacements?: ReplacementBrief[];
  refundCny?: number;
  expenseCny?: number;
  returnCreditCny?: number;
  returnFreightCny?: number;
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface ReplacementBrief {
  soId: number;
  soNo: string;
  summary: string;
  currencyCode: string;
  declaredAmount: number;
  /** 1-有效、2-已取消 */
  status: number;
  progressCode: string;
  progressName: string;
  shipped: boolean;
  createBy?: string;
  createTime?: string;
}

export interface ReplacementLine {
  soItemId?: number;
  model?: string;
  brand?: string;
  quantity: number;
  declaredPrice?: number;
  purchaserId?: number;
}

export type DueState = 'OVERDUE' | 'SOON' | 'NORMAL';

export interface AfterSalesQuery {
  page: number;
  pageSize: number;
  status?: number;
  keyword?: string;
  levelCode?: string;
  ownerId?: number;
  /** 只看逾期 */
  overdue?: boolean;
  /** 已逾期或 1 小时内到期（工作台带过来） */
  dueSoon?: boolean;
}

export interface SaveAfterSales {
  customerId: number;
  soId?: number;
  items: {
    soItemId?: number;
    model?: string;
    brand?: string;
    quantity: number;
  }[];
  levelCode?: string;
  symptom: string;
  customerRequest?: string;
  receivedAt?: string;
  ownerId?: number;
  nextAction?: string;
  nextUpdateAt?: string;
  attachmentIds?: number[];
}

export interface PlanBody {
  levelCode?: string;
  levelNote?: string;
  reasonCode?: string;
  solutions?: string[];
  ownerId?: number;
  nextAction?: string;
  nextUpdateAt?: string | null;
}

export const afterSalesApi = {
  page: (q: AfterSalesQuery) =>
    send<{ total: number; records: AfterSales[] }>('POST', `${AS}/page`, q),
  counts: () =>
    get<{
      open: number;
      waiting: number;
      overdue: number;
      dueSoon: number;
      closedThisMonth: number;
    }>(`${AS}/counts`),
  ofOrder: (soId: number) => get<AfterSales[]>(`${AS}/order/${soId}`),
  owners: () => get<{ id: number; name: string }[]>(`${AS}/owners`),
  detail: (id: number) => get<AfterSales>(`${AS}/${id}`),
  returns: (id: number) => get<SalesReturn[]>(`${AS}/${id}/returns`),
  create: (body: SaveAfterSales) => send<AfterSales>('POST', AS, body),
  note: (
    id: number,
    content: string,
    attachmentIds: number[],
    customerNotified: boolean,
  ) =>
    send<AfterSales>('POST', `${AS}/${id}/notes`, {
      content,
      attachmentIds,
      customerNotified,
    }),
  plan: (id: number, body: PlanBody) =>
    send<AfterSales>('POST', `${AS}/${id}/plan`, body),
  status: (id: number, status: number) =>
    send<AfterSales>('POST', `${AS}/${id}/status`, { status }),
  close: (id: number, reasonCode: string, result: string, caseNote?: string) =>
    send<AfterSales>('POST', `${AS}/${id}/close`, {
      reasonCode,
      result,
      caseNote,
    }),
  cancel: (id: number, reason: string) =>
    send<AfterSales>('POST', `${AS}/${id}/cancel`, { reason }),
  replace: (id: number, items: ReplacementLine[], remark?: string) =>
    send<AfterSales>('POST', `${AS}/${id}/replacement`, { items, remark }),
};

// ---------------------------------------------------------------- 退货单（spec warehouse/sales-return）

export interface SalesReturnItem {
  id: number;
  soItemId: number;
  model: string;
  brand?: string;
  quantity: number;
  receivedQty?: number | null;
  qualifiedQty?: number | null;
  unitCost?: number | null;
  supplierId?: number | null;
  supplierName?: string;
  poNo?: string;
}

export interface SalesReturnDisposition {
  id: number;
  itemId: number;
  model?: string;
  disposition: number;
  dispositionName: string;
  quantity: number;
  supplierName?: string;
  trackingNo?: string;
  freightCny: number;
  supplierRefundCny: number;
  costCny: number;
  remark?: string;
  operatorName?: string;
  createTime?: string;
}

export interface SalesReturn {
  id: number;
  returnNo: string;
  afterSalesId: number;
  asNo?: string;
  soId: number;
  soNo?: string;
  customerName?: string;
  carrier?: string;
  trackingNo?: string;
  remark?: string;
  /** 1-待入库、2-待处理、3-已处理、4-已作废 */
  status: number;
  statusName: string;
  receivedDate?: string;
  receivedByName?: string;
  receiveNote?: string;
  voidReason?: string;
  itemSummary?: string;
  items: SalesReturnItem[];
  dispositions: SalesReturnDisposition[];
  attachments?: Attachment[];
  costCny?: number;
  freightCny?: number;
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface DisposeLine {
  itemId: number;
  disposition: number;
  quantity: number;
  supplierId?: number;
  trackingNo?: string;
  freightCny?: number;
  supplierRefundCny?: number;
  remark?: string;
}

export const returnApi = {
  page: (q: {
    page: number;
    pageSize: number;
    status?: number;
    keyword?: string;
  }) =>
    send<{ total: number; records: SalesReturn[] }>('POST', `${RT}/page`, q),
  counts: () => get<{ pending: number; received: number }>(`${RT}/counts`),
  detail: (id: number) => get<SalesReturn>(`${RT}/${id}`),
  create: (body: {
    afterSalesId: number;
    items: { soItemId: number; quantity: number }[];
    carrier?: string;
    trackingNo?: string;
    remark?: string;
  }) => send<SalesReturn>('POST', RT, body),
  update: (
    id: number,
    body: {
      items: { soItemId: number; quantity: number }[];
      carrier?: string;
      trackingNo?: string;
      remark?: string;
    },
  ) => send<SalesReturn>('PUT', `${RT}/${id}`, body),
  voidReturn: (id: number, reason: string) =>
    send<SalesReturn>('POST', `${RT}/${id}/void`, { reason }),
  receive: (
    id: number,
    body: {
      receivedDate: string;
      note?: string;
      items: { itemId: number; receivedQty: number; qualifiedQty: number }[];
      attachmentIds?: number[];
    },
  ) => send<SalesReturn>('POST', `${RT}/${id}/receive`, body),
  unreceive: (id: number, reason: string) =>
    send<SalesReturn>('POST', `${RT}/${id}/unreceive`, { reason }),
  dispose: (id: number, lines: DisposeLine[]) =>
    send<SalesReturn>('POST', `${RT}/${id}/dispose`, { lines }),
};
