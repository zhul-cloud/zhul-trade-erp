import { request } from '@umijs/max';
import type { ReceiptRow } from '@/pages/sales/service';
import type { Attachment, DocLog, DocRef } from '@/pages/warehouse/service';

const FX = '/api/v1/finance/fx-settlements';
const quiet = { skipErrorHandler: true } as const;

const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(url: string, data?: unknown) =>
  request<{ data: T }>(url, { method: 'POST', data, ...quiet }).then(
    (r) => r.data,
  );

export interface FxSettlement {
  id: number;
  settleNo: string;
  /** 所属事业部 */
  businessUnitId?: number;
  currencyCode: string;
  settleDate: string;
  amount: number;
  amountCny: number;
  exchangeRate: number;
  bankAccountId?: number;
  bankAccountName?: string;
  remark?: string;
  migrated: boolean;
  /** 1-有效、2-已作废 */
  status: number;
  voidReason?: string;
  voidedBy?: string;
  voidedAt?: string;
  allocationCount: number;
  /** 该币种最新的一张有效结汇单：只有它能作废 */
  latest: boolean;
  allocations?: { amount: number; amountCny: number; receipt: ReceiptRow }[];
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
  /** 以下只在详情返回（change finance-ap-fx-docs） */
  files?: Attachment[];
  related?: DocRef[];
  logs?: DocLog[];
  editable?: boolean;
  /** 不是最新一张有效结汇单时，要先作废的那一张 */
  blockingNo?: string;
}

export interface FxPending {
  /** 所属事业部 */
  businessUnitId?: number;
  currencyCode: string;
  balance: number;
  estimateRate: number;
  latestSettleNo?: string;
  latestSettleDate?: string;
  estimateCny: number;
  receipts: ReceiptRow[];
}

export interface FxQuery {
  page: number;
  pageSize: number;
  businessUnitId?: number;
  currencyCode?: string;
  dateFrom?: string;
  dateTo?: string;
  includeVoid?: boolean;
}

export const fxApi = {
  page: (q: FxQuery) => get<{ total: number; records: FxSettlement[] }>(FX, q),
  pending: () => get<FxPending[]>(`${FX}/pending`),
  detail: (id: number) => get<FxSettlement>(`${FX}/${id}`),
  create: (body: {
    settleDate: string;
    currencyCode: string;
    amount: number;
    amountCny: number;
    bankAccountId: number;
    remark?: string;
    idempotencyKey: string;
    businessUnitId?: number;
  }) => send<FxSettlement>(FX, body),
  voidSettlement: (id: number, reason: string) =>
    send<FxSettlement>(`${FX}/${id}/void`, { reason }),
  remark: (id: number, remark: string) =>
    send<FxSettlement>(`${FX}/${id}/remark`, { remark }),
  attach: (id: number, ids: number[]) =>
    send<FxSettlement>(`${FX}/${id}/attachments`, { ids }),
  removeAttachment: (id: number, attachmentId: number) =>
    request<{ data: FxSettlement }>(`${FX}/${id}/attachments/${attachmentId}`, {
      method: 'DELETE',
      ...quiet,
    }).then((r) => r.data),
};
