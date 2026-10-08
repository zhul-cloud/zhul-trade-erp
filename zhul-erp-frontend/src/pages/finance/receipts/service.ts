import { request } from '@umijs/max';
import type { ReceiptRow } from '@/pages/sales/service';

const FIN = '/api/v1/finance/receipts';
const quiet = { skipErrorHandler: true } as const;

const unwrap = <T>(p: Promise<{ data: T }>) => p.then((r) => r.data);

export interface ReceiptMoney {
  currencyCode: string;
  amount: number;
}

export interface ReceiptSummary {
  /** 1-线下、2-线上、0-合计 */
  channel: number;
  count: number;
  amounts: ReceiptMoney[];
  fees: ReceiptMoney[];
  netAmountCny: number;
}

export interface ReceiptRecordQuery {
  keyword?: string;
  dateFrom?: string;
  dateTo?: string;
  paymentMethod?: string;
  channel?: number;
  currencyCode?: string;
  includeVoid?: boolean;
  page: number;
  pageSize: number;
}

/** 财务管理 → 收款管理：未认领到账与收款记录 */
export const ledgerApi = {
  unclaimed: (keyword?: string, currencyCode?: string) =>
    unwrap(
      request<{
        data: { unclaimed: ReceiptRow[]; recentClaimed: ReceiptRow[] };
      }>(`${FIN}/unclaimed`, { params: { keyword, currencyCode }, ...quiet }),
    ),
  registerUnclaimed: (body: {
    currencyCode: string;
    amount: number;
    receiptDate: string;
    bankAccountId: number;
    paymentMethod?: string;
    payer?: string;
    actualAmountCny?: number;
    note?: string;
  }) =>
    unwrap(
      request<{ data: ReceiptRow }>(`${FIN}/unclaimed`, {
        method: 'POST',
        data: body,
        ...quiet,
      }),
    ),
  unclaim: (id: number, reason: string) =>
    unwrap(
      request<{ data: null }>(`${FIN}/${id}/unclaim`, {
        method: 'POST',
        data: { reason },
        ...quiet,
      }),
    ),
  voidUnclaimed: (id: number, reason: string) =>
    unwrap(
      request<{ data: null }>(`${FIN}/${id}/void`, {
        method: 'POST',
        data: { reason },
        ...quiet,
      }),
    ),
  records: (q: ReceiptRecordQuery) =>
    unwrap(
      request<{
        data: {
          total: number;
          records: ReceiptRow[];
          summary: ReceiptSummary[];
        };
      }>(`${FIN}/records`, { method: 'POST', data: q, ...quiet }),
    ),
};
