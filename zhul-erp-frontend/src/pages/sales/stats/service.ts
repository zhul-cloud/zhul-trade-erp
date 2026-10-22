import { request } from '@umijs/max';

/** 新客户与老客户拆分 */
export interface Split {
  all: number;
  fresh: number;
  old: number;
}

export type Rate = number | null | undefined;

/** 销售统计一行（spec sales/sales-stats），金额为人民币，比率为百分比 */
export interface StatsRow {
  key: string;
  label: string;
  opportunities: number;
  inquiries: Split;
  quotations: Split;
  intents: Split;
  intentAmount: Split;
  pending: Split;
  pendingAmount: Split;
  orders: Split;
  customers: Split;
  revenue: Split;
  profit: Split;
  profitRate?: Rate;
  profitRateNew?: Rate;
  profitRateOld?: Rate;
  inquiryRate?: Rate;
  inquiryRateNew?: Rate;
  orderRate?: Rate;
  orderRateNew?: Rate;
  orderRateOld?: Rate;
  overallRate?: Rate;
  avgPrice?: Rate;
  avgPriceNew?: Rate;
  avgPriceOld?: Rate;
  orderRepeatRate?: Rate;
  customerRepeatRate?: Rate;
}

export interface CustomerRow {
  customerId: number;
  customerName?: string;
  country?: string;
  customerType?: number;
  orders: number;
  revenue: number;
  profit: number;
  profitRate?: Rate;
}

export interface SalesStats {
  rows: StatsRow[];
  total: StatsRow;
  customers: CustomerRow[];
}

export type StatsView = 'MONTH' | 'OWNER' | 'CUSTOMER';

export const salesStatsApi = {
  stats: (params: {
    dateFrom: string;
    dateTo: string;
    ownerId?: number;
    view: StatsView;
    businessUnitId?: number;
  }) =>
    request<{ data: SalesStats }>('/api/v1/sales/stats', {
      method: 'GET',
      params,
      skipErrorHandler: true,
    }).then((r) => r.data),
};
