import { request } from '@umijs/max';
import type { Refund } from '../refunds/service';

const BASE = '/api/v1/finance/credits';
const quiet = { skipErrorHandler: true } as const;
const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const post = <T>(url: string, data?: unknown) =>
  request<{ data: T }>(url, { method: 'POST', data, ...quiet }).then(
    (r) => r.data,
  );

/** 余额账户（客户 + 币种 + 事业部） */
export interface CreditAccount {
  customerId: number;
  customerName?: string;
  customerContact?: string;
  customerCountry?: string;
  currencyCode: string;
  businessUnitId?: number;
  businessUnitName?: string;
  available: number;
  availableCny: number;
  /** 还没转订单的 PI 上的抵扣（已从可用余额扣除） */
  occupied: number;
  occupiers: {
    sourceType: string;
    sourceId: number;
    sourceNo: string;
    amount: number;
  }[];
  collection: boolean;
  lastTime?: string;
  lastTypeName?: string;
  lastSourceNo?: string;
}

export interface CreditEntry {
  id: number;
  entryType: number;
  typeName: string;
  amount: number;
  amountCny: number;
  sourceType: string;
  sourceId?: number;
  sourceNo: string;
  remark?: string;
  createTime: string;
  createBy?: string;
}

export interface CurrencyTotal {
  currencyCode: string;
  amount: number;
  amountCny: number;
  count: number;
}

export interface CreditSummary {
  currencies: CurrencyTotal[];
  occupied: CurrencyTotal[];
  monthWithdrawCny: number;
  monthWithdrawCount: number;
  monthFxGain: number;
}

export interface Pair {
  amount: number;
  amountCny: number;
}

export interface ReconRow {
  customerId?: number;
  customerName: string;
  businessUnitId?: number;
  businessUnitName?: string;
  opening: Pair;
  refundIn: Pair;
  collectionIn: Pair;
  overpaidIn: Pair;
  adjust: Pair;
  deduct: Pair;
  withdraw: Pair;
  closing: Pair;
}

export interface Recon {
  month: string;
  currencyCode: string;
  rows: ReconRow[];
  total: ReconRow;
  balanced: boolean;
}

export interface Overpaid {
  ownerType: 'PI' | 'SO';
  ownerId: number;
  ownerNo: string;
  soId?: number;
  soNo?: string;
  customerId: number;
  customerName?: string;
  customerContact?: string;
  currencyCode: string;
  businessUnitId?: number;
  total: number;
  received: number;
  overpaid: number;
  pending: number;
  rate: number;
  pendingCny: number;
  salesName?: string;
  /** 原平台收款；不为空时退回客户只能原路退回平台 */
  platformReceipts?: {
    platform: string;
    platformOrderNo?: string;
    amount: number;
    receiptDate: string;
  }[];
}

export interface CreditQuery {
  page: number;
  pageSize: number;
  keyword?: string;
  currencyCode?: string;
  businessUnitId?: number;
  showAll?: boolean;
}

export interface WithdrawBody {
  customerId: number;
  currencyCode: string;
  businessUnitId?: number;
  amount: number;
  withdrawDate: string;
  /** 2-银行汇款、4-其他（methodName） */
  method: number;
  methodName?: string;
  payeeAccount?: string;
  actualCny: number;
  attachmentIds: number[];
  remark?: string;
}

/** 转出方式：银行汇款或其他（支付宝、微信等） */
export const WITHDRAW_METHODS = [
  { value: '银行汇款', method: 2 },
  { value: '支付宝', method: 4 },
  { value: '微信', method: 4 },
  { value: 'PayPal', method: 4 },
];

/** 流水类型 → 胶囊颜色 */
export const ENTRY_TONES: Record<
  number,
  'green' | 'orange' | 'cyan' | 'violet' | 'gray' | 'red' | 'accent'
> = {
  1: 'green',
  2: 'violet',
  3: 'green',
  4: 'orange',
  5: 'cyan',
  6: 'accent',
  7: 'gray',
  8: 'red',
};

export const creditApi = {
  page: (q: CreditQuery) =>
    get<{ total: number; records: CreditAccount[] }>(BASE, q),
  summary: () => get<CreditSummary>(`${BASE}/summary`),
  ofCustomer: (customerId: number) =>
    get<CreditAccount[]>(`${BASE}/customer/${customerId}`),
  entries: (
    customerId: number,
    currencyCode: string,
    businessUnitId?: number,
  ) =>
    get<CreditEntry[]>(`${BASE}/entries`, {
      customerId,
      currencyCode,
      businessUnitId,
    }),
  reconciliation: (
    month: string,
    currencyCode: string,
    businessUnitId?: number,
  ) =>
    get<Recon>(`${BASE}/reconciliation`, {
      month,
      currencyCode,
      businessUnitId,
    }),
  /** 下载月度核对表（带登录态） */
  exportRecon: async (
    month: string,
    currencyCode: string,
    businessUnitId?: number,
  ) => {
    const res = (await request<Blob>(`${BASE}/reconciliation/export`, {
      method: 'GET',
      params: { month, currencyCode, businessUnitId },
      responseType: 'blob',
      getResponse: true,
      ...quiet,
    })) as unknown as { data: Blob };
    if (res.data.type.includes('json')) {
      const body = JSON.parse(await res.data.text()) as { message?: string };
      throw new Error(body.message || '导出失败，请稍后重试');
    }
    const url = window.URL.createObjectURL(res.data);
    const a = document.createElement('a');
    a.href = url;
    a.download = `客户余额核对_${month}_${currencyCode}.xlsx`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.setTimeout(() => window.URL.revokeObjectURL(url), 1000);
  },
  adjust: (body: {
    customerId: number;
    currencyCode: string;
    businessUnitId?: number;
    amount: number;
    amountCny?: number;
    reason: string;
    attachmentIds: number[];
  }) => post<CreditEntry[]>(`${BASE}/adjust`, body),
  withdraw: (body: WithdrawBody) => post<Refund>(`${BASE}/withdraw`, body),
  overpaid: () => get<Overpaid[]>(`${BASE}/overpaid`),
  processOverpaid: (
    body: {
      ownerType: string;
      ownerId: number;
      amount: number;
      mode: 'CREDIT' | 'REFUND';
    } & Partial<
      Pick<
        WithdrawBody,
        | 'withdrawDate'
        | 'method'
        | 'methodName'
        | 'payeeAccount'
        | 'actualCny'
        | 'attachmentIds'
        | 'remark'
      >
    >,
  ) => post<Overpaid[]>(`${BASE}/overpaid`, body),
};
