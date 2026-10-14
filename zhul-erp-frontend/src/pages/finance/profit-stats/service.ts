import { request } from '@umijs/max';

const quiet = { skipErrorHandler: true } as const;
const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );

export interface CostSource {
  /** PO 采购订单、COURIER 快递单、SHIPMENT 出运单、EXPENSE 费用单 */
  type: string;
  id: number;
  no?: string;
  amount?: number;
  note?: string;
}

export interface CostLine {
  soItemId: number;
  model: string;
  brand?: string;
  quantity: number;
  purchase: number;
  purchaseSettled: boolean;
  invoice: number;
  poFee: number;
  claim: number;
  returnFreight: number;
  domestic: number;
  international: number;
  internationalSettled: boolean;
  expense: number;
  receiptFee: number;
  cost: number;
  revenue: number;
  refund: number;
  profit: number;
  profitRate?: number | null;
  unreceivedQty: number;
  unorderedQty: number;
  missingCostPrice: boolean;
  sources: Record<string, CostSource[]>;
}

/** 订单实际成本与毛利（spec finance/order-cost），金额为人民币 */
export interface OrderCost {
  soId: number;
  salesAmountCny: number;
  revenue: number;
  cost: number;
  receiptFee: number;
  /** 有效退款单的人民币合计，从收入中扣减 */
  refund: number;
  refunds: {
    id: number;
    refundNo: string;
    refundDate: string;
    reasonName?: string;
    currencyCode: string;
    amount: number;
    amountCny: number;
  }[];
  /** 毛利 = 收入 − 退款 − 实际成本 */
  profit: number;
  profitRate?: number | null;
  settled: boolean;
  pending: string[];
  missingCostCount: number;
  lines: CostLine[];
}

export interface ProfitTotals {
  orderCount: number;
  customerCount: number;
  unsettledCount: number;
  salesAmountCny: number;
  revenue: number;
  cost: number;
  profit: number;
  profitRate?: number | null;
  newOrderCount: number;
  oldOrderCount: number;
  newRevenue: number;
  oldRevenue: number;
  newCost: number;
  oldCost: number;
  newProfit: number;
  oldProfit: number;
  onlineRevenue: number;
  offlineRevenue: number;
  unreceivedRevenue: number;
}

export interface ProfitOrderRow {
  soId: number;
  soNo: string;
  customerName?: string;
  customerType?: number;
  salesDate: string;
  ownerName?: string;
  revenue: number;
  cost: number;
  profit: number;
  profitRate?: number | null;
  settled: boolean;
  pending: string[];
}

export interface ProfitGroup {
  key: string;
  label: string;
  country?: string;
  totals: ProfitTotals;
  orders: ProfitOrderRow[];
}

export interface ProfitQuery {
  dateFrom?: string;
  dateTo?: string;
  ownerId?: number;
  customerType?: number;
  settledOnly?: boolean;
  groupBy?: 'MONTH' | 'OWNER' | 'CUSTOMER';
}

export const profitApi = {
  stats: (q: ProfitQuery) =>
    get<{ summary: ProfitTotals; groups: ProfitGroup[] }>(
      '/api/v1/finance/profit-stats',
      q,
    ),
};
