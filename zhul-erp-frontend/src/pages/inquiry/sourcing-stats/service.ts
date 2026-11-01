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

/** 按人员（型号口径） */
export interface PurchaserRow {
  purchaserId: number;
  purchaserName: string;
  items: number;
  priced: number;
  noStock: number;
  pending: number;
  pricedRate?: number | null;
  /** 转出：交给别人继续找的型号，不计入有价、无货、待回价 */
  transferred: number;
  /** SALES-业务员、PURCHASER-采购、PART_TIME-兼职 */
  role: 'SALES' | 'PURCHASER' | 'PART_TIME';
}

/** 询价工作量（型号口径，月份按询盘日期） */
export interface WorkloadRow {
  key: string;
  label: string;
  selfItems: number;
  purchaseItems: number;
  selfRate?: number | null;
}

export interface SourcingStats {
  months: MonthRow[];
  total: MonthRow;
  purchasers: PurchaserRow[];
  workload: WorkloadRow[];
  workloadTotal?: WorkloadRow;
}

export const sourcingStatsApi = {
  stats: (params: { dateFrom: string; dateTo: string }) =>
    request<{ data: SourcingStats }>('/api/v1/inquiry/sourcing-stats', {
      method: 'GET',
      params,
      skipErrorHandler: true,
    }).then((r) => r.data),
};
