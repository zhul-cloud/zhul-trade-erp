import { request } from '@umijs/max';
import type { Attachment, DocLog, DocRef } from '@/pages/warehouse/service';
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
  /** 历史迁移的单据（只读） */
  migrated?: boolean;
  id: number;
  incomeNo: string;
  incomeDate: string;
  /** 所属事业部；为空表示「公共」 */
  businessUnitId?: number;
  category: string;
  categoryName?: string;
  amount: number;
  remark: string;
  /** 1-有效、2-已作废 */
  status: number;
  voidReason?: string;
  /** 来源：空-手动登记、REFUND-余额提现自动生成的汇兑损益（不能修改、作废） */
  sourceType?: string;
  sourceId?: number;
  attachments: FileRef[];
  /** 以下只在详情返回（change finance-expense-docs） */
  files?: Attachment[];
  related?: DocRef[];
  logs?: DocLog[];
  editable?: boolean;
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface OtherIncomeQuery {
  page: number;
  pageSize: number;
  /** 所属事业部筛选：0 表示「公共」 */
  businessUnitId?: number;
  category?: string;
  dateFrom?: string;
  dateTo?: string;
  showVoid?: boolean;
}

export interface SaveOtherIncomeBody {
  incomeDate: string;
  /** 所属事业部；为空表示「公共」 */
  businessUnitId?: number;
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
  detail: (id: number) => get<OtherIncome>(`${BASE}/${id}`),
  voidIncome: (id: number, reason: string) =>
    send<OtherIncome>('POST', `${BASE}/${id}/void`, { reason }),
  remark: (id: number, remark: string) =>
    send<OtherIncome>('POST', `${BASE}/${id}/remark`, { remark }),
  attach: (id: number, ids: number[]) =>
    send<OtherIncome>('POST', `${BASE}/${id}/attachments`, { ids }),
  removeAttachment: (id: number, attachmentId: number) =>
    send<OtherIncome>('DELETE', `${BASE}/${id}/attachments/${attachmentId}`),
};
