import { request } from '@umijs/max';
import type { FileRef } from '../payables/service';

const BASE = '/api/v1/finance/other-incomes';
const quiet = { skipErrorHandler: true } as const;
const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(method: string, url: string, data?: unknown) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);

export const DICT_INCOME_CATEGORY = 'other_income_category';

export interface OtherIncome {
  id: number;
  incomeNo: string;
  incomeDate: string;
  category: string;
  categoryName?: string;
  amount: number;
  remark: string;
  /** 1-有效、2-已作废 */
  status: number;
  voidReason?: string;
  attachments: FileRef[];
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface OtherIncomeQuery {
  page: number;
  pageSize: number;
  category?: string;
  dateFrom?: string;
  dateTo?: string;
  showVoid?: boolean;
}

export interface SaveOtherIncomeBody {
  incomeDate: string;
  category: string;
  amount: number;
  remark: string;
  attachmentIds: number[];
}

export const incomeApi = {
  page: (q: OtherIncomeQuery) =>
    get<{ total: number; records: OtherIncome[] }>(BASE, q),
  total: (q: Omit<OtherIncomeQuery, 'page' | 'pageSize'>) =>
    get<number>(`${BASE}/total`, q),
  create: (b: SaveOtherIncomeBody) => send<OtherIncome>('POST', BASE, b),
  update: (id: number, b: SaveOtherIncomeBody) =>
    send<OtherIncome>('PUT', `${BASE}/${id}`, b),
  voidIncome: (id: number, reason: string) =>
    send<OtherIncome>('POST', `${BASE}/${id}/void`, { reason }),
};
