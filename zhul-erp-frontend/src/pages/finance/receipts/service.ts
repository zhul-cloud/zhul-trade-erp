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
  /** 其中还没结完汇的到账（含估算部分） */
  openNetAmountCny?: number;
}

export interface ReceiptRecordQuery {
  keyword?: string;
  dateFrom?: string;
  dateTo?: string;
  paymentMethod?: string;
  channel?: number;
  currencyCode?: string;
  /** 结汇状态：1-未结汇、2-部分结汇、3-已结汇 */
  settleStatus?: number;
  includeVoid?: boolean;
  page: number;
  pageSize: number;
}

/** 财务管理 → 收款单：未认领到账与收款记录 */
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
