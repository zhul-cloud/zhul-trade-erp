import { request } from '@umijs/max';

/** 按月份（询盘单口径） */
export interface MonthRow {
  key: string;
  label: string;
  total: number;
  completed: number;
  unquotable: number;
  inProgress: number;
  completedRate?: number | null;
  unquotableRate?: number | null;
}

/** 按采购员（型号口径） */
export interface PurchaserRow {
  purchaserId: number;
  purchaserName: string;
  items: number;
  priced: number;
  noStock: number;
  pending: number;
  pricedRate?: number | null;
}

export interface SourcingStats {
  months: MonthRow[];
  total: MonthRow;
  purchasers: PurchaserRow[];
}

export const sourcingStatsApi = {
  stats: (params: { dateFrom: string; dateTo: string }) =>
    request<{ data: SourcingStats }>('/api/v1/inquiry/sourcing-stats', {
      method: 'GET',
      params,
      skipErrorHandler: true,
    }).then((r) => r.data),
};
