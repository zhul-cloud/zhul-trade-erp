import { request } from '@umijs/max';
import type { DocLog } from '@/pages/sales/after-sales/service';
import { PATHS as SALES_PATHS } from '@/pages/sales/components';
import type { Attachment } from '@/pages/warehouse/service';

/** 采购退货单（spec purchase/purchase-return） */
const PR = '/api/v1/purchase/returns';
const quiet = { skipErrorHandler: true } as const;
const get = <T>(url: string) =>
  request<{ data: T }>(url, { method: 'GET', ...quiet }).then((r) => r.data);
const send = <T>(method: 'POST' | 'PUT', url: string, data?: unknown) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);

/** 1-待寄出、2-已寄出、3-已完成、4-已作废 */
export const PR_STATUS = { PENDING: 1, SHIPPED: 2, DONE: 3, VOID: 4 };
/** 1-售后退货、2-到货不良·退货换货、3-到货不良·退货不补、4-多发退回 */
export const PR_SOURCE = {
  AFTER_SALES: 1,
  DEFECT_EXCHANGE: 2,
  DEFECT_RETURN: 3,
  OVER_RETURN: 4,
};
export const PR_SOURCE_OPTIONS = [
  { value: 1, label: '售后退货' },
  { value: 2, label: '到货不良 · 退货换货' },
  { value: 3, label: '到货不良 · 退货不补' },
  { value: 4, label: '多发退回' },
];

export interface PurchaseReturnItem {
  id: number;
  model: string;
  brand?: string;
  category?: string;
  quantity: number;
  soItemId?: number;
  poItemId?: number;
  receiptItemId?: number;
  discrepancyId?: number;
  discrepancyName?: string;
}

export interface PurchaseReturn {
  id: number;
  returnNo: string;
  supplierId: number;
  supplierName?: string;
  sourceType: number;
  sourceTypeName: string;
  /** 售后退货是退货入库单，到货差异是采购入库单 */
  sourceId?: number;
  sourceNo?: string;
  poId?: number;
  poNo?: string;
  afterSalesId?: number;
  asNo?: string;
  soId?: number;
  soNo?: string;
  customerName?: string;
  ownerId?: number;
  ownerName?: string;
  status: number;
  statusName: string;
  shipDate?: string;
  carrier?: string;
  trackingNo?: string;
  freightCny?: number;
  payerId?: number;
  payerName?: string;
  signedDate?: string;
  currencyCode?: string;
  exchangeRate?: number;
  refundAmount?: number;
  refundCny?: number;
  settleNote?: string;
  remark?: string;
  voidReason?: string;
  migrated?: boolean;
  itemSummary?: string;
  totalQty?: number;
  brands?: string[];
  categories?: string[];
  items: PurchaseReturnItem[];
  attachments?: Attachment[];
  freightProofs?: Attachment[];
  logs?: DocLog[];
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface PurchaseReturnQuery {
  page: number;
  pageSize: number;
  status?: number;
  supplierId?: number;
  sourceType?: number;
  ownerId?: number;
  keyword?: string;
}

export interface ShipBody {
  shipDate: string;
  carrier: string;
  trackingNo: string;
  freightCny?: number;
  payerId?: number;
  companyPaid?: boolean;
  proofIds?: number[];
}

export interface CompleteBody {
  signedDate: string;
  refundAmount?: number;
  settleNote?: string;
}

export const purchaseReturnApi = {
  page: (q: PurchaseReturnQuery) =>
    send<{ total: number; records: PurchaseReturn[] }>('POST', `${PR}/page`, q),
  counts: () => get<{ pending: number; shipped: number }>(`${PR}/counts`),
  detail: (id: number) => get<PurchaseReturn>(`${PR}/${id}`),
  ofOrder: (poId: number) => get<PurchaseReturn[]>(`${PR}/of-order/${poId}`),
  quantities: (id: number, items: { itemId: number; quantity: number }[]) =>
    send<PurchaseReturn>('PUT', `${PR}/${id}/quantities`, { items }),
  ship: (id: number, body: ShipBody) =>
    send<PurchaseReturn>('POST', `${PR}/${id}/ship`, body),
  complete: (id: number, body: CompleteBody) =>
    send<PurchaseReturn>('POST', `${PR}/${id}/complete`, body),
  reopen: (id: number, reason: string) =>
    send<PurchaseReturn>('POST', `${PR}/${id}/reopen`, { reason }),
  voidReturn: (id: number, reason: string) =>
    send<PurchaseReturn>('POST', `${PR}/${id}/void`, { reason }),
  remark: (id: number, remark: string) =>
    send<PurchaseReturn>('PUT', `${PR}/${id}/remark`, { remark }),
  attach: (id: number, ids: number[]) =>
    send<PurchaseReturn>('POST', `${PR}/${id}/attachments`, { ids }),
  removeAttachment: (id: number, attachmentId: number) =>
    request<{ data: PurchaseReturn }>(
      `${PR}/${id}/attachments/${attachmentId}`,
      {
        method: 'DELETE',
        ...quiet,
      },
    ).then((r) => r.data),
};

/** 来源单号：售后退货是退货入库单，到货差异是采购入库单 */
export const prSourceRef = (p: PurchaseReturn) => ({
  no: p.sourceNo,
  href: p.sourceId
    ? p.sourceType === PR_SOURCE.AFTER_SALES
      ? SALES_PATHS.returnReceipt(p.sourceId)
      : SALES_PATHS.receipt(p.sourceId)
    : undefined,
});
