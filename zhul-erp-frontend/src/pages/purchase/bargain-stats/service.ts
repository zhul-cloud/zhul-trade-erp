import { request } from '@umijs/max';

const quiet = { skipErrorHandler: true } as const;

export interface BargainTotals {
  lineCount: number;
  purchaseAmount: number;
  targetAmount: number;
  bargainAmount: number;
  bargainRate?: number | null;
  noTargetAmount: number;
  claimAmount: number;
  invoiceCost: number;
}

export interface BargainLine {
  poId: number;
  poNo: string;
  orderDate: string;
  model: string;
  brand?: string;
  category?: string;
  supplierName?: string;
  purchaserName?: string;
  quantity: number;
  targetPrice?: number | null;
  netPriceCny?: number | null;
  bargainAmount?: number | null;
  bargainRate?: number | null;
  claimAmount: number;
}

export interface BargainGroup {
  key: string;
  label: string;
  totals: BargainTotals;
  lines: BargainLine[];
}

export type BargainGroupBy =
  | 'PURCHASER'
  | 'SUPPLIER'
  | 'BRAND'
  | 'CATEGORY'
  | 'MONTH';

export interface BargainQuery {
  dateFrom?: string;
  dateTo?: string;
  purchaserId?: number;
  supplierId?: number;
  brand?: string;
  category?: string;
  groupBy?: BargainGroupBy;
}

export const bargainApi = {
  stats: (q: BargainQuery) =>
    request<{ data: { summary: BargainTotals; groups: BargainGroup[] } }>(
      '/api/v1/purchase/bargain-stats',
      { method: 'GET', params: q, ...quiet },
    ).then((r) => r.data),
};
