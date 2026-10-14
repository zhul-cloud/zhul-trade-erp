import { request } from '@umijs/max';
import type { ReceiptRow } from '@/pages/sales/service';

const FX = '/api/v1/finance/fx-settlements';
const quiet = { skipErrorHandler: true } as const;

const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(url: string, data?: unknown) =>
  request<{ data: T }>(url, { method: 'POST', data, ...quiet }).then(
    (r) => r.data,
  );

export interface FxSettlement {
  id: number;
  settleNo: string;
  currencyCode: string;
  settleDate: string;
  amount: number;
  amountCny: number;
  exchangeRate: number;
  bankAccountId?: number;
  bankAccountName?: string;
  remark?: string;
  migrated: boolean;
  /** 1-有效、2-已作废 */
  status: number;
  voidReason?: string;
  voidedBy?: string;
  voidedAt?: string;
  allocationCount: number;
  /** 该币种最新的一张有效结汇单：只有它能作废 */
  latest: boolean;
  allocations?: { amount: number; amountCny: number; receipt: ReceiptRow }[];
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface FxPending {
  currencyCode: string;
  balance: number;
  estimateRate: number;
  latestSettleNo?: string;
  latestSettleDate?: string;
  estimateCny: number;
  receipts: ReceiptRow[];
}

export interface FxQuery {
  page: number;
  pageSize: number;
  currencyCode?: string;
  dateFrom?: string;
  dateTo?: string;
  includeVoid?: boolean;
}

export const fxApi = {
  page: (q: FxQuery) => get<{ total: number; records: FxSettlement[] }>(FX, q),
  pending: () => get<FxPending[]>(`${FX}/pending`),
  detail: (id: number) => get<FxSettlement>(`${FX}/${id}`),
  create: (body: {
    settleDate: string;
    currencyCode: string;
    amount: number;
    amountCny: number;
    bankAccountId: number;
    remark?: string;
    idempotencyKey: string;
  }) => send<FxSettlement>(FX, body),
  voidSettlement: (id: number, reason: string) =>
    send<FxSettlement>(`${FX}/${id}/void`, { reason }),
};
