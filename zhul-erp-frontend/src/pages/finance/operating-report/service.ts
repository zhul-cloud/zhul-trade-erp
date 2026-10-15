import { request } from '@umijs/max';

export interface OperatingRow {
  /** 1–12；全年合计为 0 */
  month: number;
  label: string;
  orderCount: number;
  unsettledCount: number;
  revenue: number;
  cost: number;
  profit: number;
  profitRate?: number | null;
  refund: number;
  otherIncome: number;
  opex: Record<string, number>;
  opexTotal: number;
  net: number;
  netRate?: number | null;
}

export interface OperatingReport {
  year: number;
  categories: { code: string; name: string }[];
  rows: OperatingRow[];
  total: OperatingRow;
}

export const operatingApi = {
  year: (year: number) =>
    request<{ data: OperatingReport }>('/api/v1/finance/operating-report', {
      method: 'GET',
      params: { year },
      skipErrorHandler: true,
    }).then((r) => r.data),
};
