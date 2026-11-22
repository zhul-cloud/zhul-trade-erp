import { request } from '@umijs/max';

/** 应收管理（spec finance/accounts-receivable） */
export interface ReceivableRow {
  orderId: number;
  soNo: string;
  piId?: number | null;
  piNo?: string | null;
  salesDate?: string;
  customerId: number;
  customerName?: string;
  customerCountry?: string;
  customerContact?: string;
  ownerId: number;
  ownerName?: string;
  currencyCode: string;
  totalAmount: number;
  receivedAmount: number;
  remainingAmount: number;
  remainingCny: number;
  dueDate?: string | null;
  overdueDays?: number | null;
  dueInDays?: number | null;
}

export interface ReceivableStats {
  totalCny: number;
  customers: number;
  orders: number;
  overdueCny: number;
  overdueOrders: number;
  weekCny: number;
  weekOrders: number;
  monthCny: number;
  monthOrders: number;
}

export interface ReceivableOverview {
  stats: ReceivableStats;
  rows: ReceivableRow[];
}

export const receivableApi = {
  overview: () =>
    request<{ data: ReceivableOverview }>('/api/v1/finance/receivables', {
      method: 'GET',
      skipErrorHandler: true,
    }).then((r) => r.data),
};
