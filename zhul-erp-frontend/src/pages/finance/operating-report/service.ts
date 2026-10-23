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
  /** 直接费用（归属本事业部的费用单），按类别 */
  opexDirect: Record<string, number>;
  opexDirectTotal: number;
  /** 分摊的公共成本（全公司合计时为全部公共费用），按类别 */
  opexShared: Record<string, number>;
  opexSharedTotal: number;
  /** 本事业部当月的分摊比例（0.2 即 20%）；全公司合计与全年行为空 */
  sharePercent?: number | null;
  net: number;
  netRate?: number | null;
}

export interface OperatingReport {
  year: number;
  /** 查看的事业部；全公司合计为空 */
  businessUnitId?: number;
  categories: { code: string; name: string }[];
  rows: OperatingRow[];
  total: OperatingRow;
}

export const operatingApi = {
  year: (year: number, businessUnitId?: number) =>
    request<{ data: OperatingReport }>('/api/v1/finance/operating-report', {
      method: 'GET',
      params: { year, businessUnitId },
      skipErrorHandler: true,
    }).then((r) => r.data),
};
