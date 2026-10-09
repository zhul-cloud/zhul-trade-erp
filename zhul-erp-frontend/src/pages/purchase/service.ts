import { request } from '@umijs/max';

export { readBizError } from '@/pages/crm/opportunity/service';

// ---------------------------------------------------------------- 类型

/** 付款条件的一期：trigger 1-下单后、2-发货前、3-入库后 */
export interface PaymentTerm {
  percent: number;
  trigger: number;
  days?: number;
}

export interface PoRef {
  id: number;
  poNo?: string;
  status: number;
  supplierName?: string;
  purchaserName?: string;
  quantity: number;
}

export type RequirementStatus =
  | 'pending'
  | 'draft'
  | 'partial'
  | 'ordered'
  | 'closed'
  | 'orderCancelled';

export interface Requirement {
  id: number;
  soId: number;
  soNo?: string;
  salesDate?: string;
  customerName?: string;
  customerCountry?: string;
  model: string;
  brand?: string;
  quantity: number;
  draftQty: number;
  orderedQty: number;
  availableQty: number;
  targetPrice?: number | null;
  purchaserId?: number | null;
  purchaserName?: string;
  suggestedSupplierId?: number | null;
  suggestedSupplierName?: string;
  suggestedChannel?: number;
  suggestedChannelName?: string;
  suggestedShopName?: string;
  stockType?: number;
  status: RequirementStatus;
  statusName: string;
  purchaseOrders: PoRef[];
}

export interface RequirementQuery {
  page: number;
  pageSize: number;
  keyword?: string;
  purchaserId?: number;
  supplierId?: number;
  /** open-还没全部下单、need-需要生成采购单、all-全部 */
  view?: 'open' | 'need' | 'all';
  stockType?: number;
}

export interface RequirementStats {
  open: number;
  inDraft: number;
  need: number;
  unassigned: number;
  drafts: number;
}

export interface GenerateGroup {
  key: string;
  title: string;
  supplierId?: number | null;
  supplierName?: string;
  channel?: number;
  shopName?: string;
  matchedByName?: boolean;
  lines: {
    requirementId: number;
    model: string;
    brand?: string;
    soNo?: string;
    availableQty: number;
    purchaserName?: string;
  }[];
}

export interface PoListItem {
  id: number;
  poNo?: string;
  status: number;
  statusName: string;
  orderDate?: string;
  createTime: string;
  staleDays?: number | null;
  supplierId: number;
  supplierName?: string;
  itemCount: number;
  totalQuantity: number;
  missingPriceCount: number;
  currencyCode: string;
  totalAmount: number;
  totalAmountCny: number;
  bargainAmount?: number | null;
  bargainRate?: number | null;
  paymentTermsText?: string;
  attachmentCount: number;
  orders: { id: number; soNo?: string }[];
  orderCancelledCount: number;
  purchaserId: number;
  purchaserName?: string;
  cancelReason?: string;
}

export interface PoQuery {
  page: number;
  pageSize: number;
  keyword?: string;
  status?: number;
  supplierId?: number;
  purchaserId?: number;
  orderFrom?: string;
  orderTo?: string;
  sortField?: string;
  sortOrder?: 'ascend' | 'descend';
}

export interface PoStats {
  monthOrderedCny: number;
  monthOrderedCount: number;
  monthBargain: number;
  monthBargainRate?: number | null;
  drafts: number;
  staleDrafts: number;
  orderCancelled: number;
}

export interface PoItem {
  id: number;
  requirementId: number;
  soId: number;
  soNo?: string;
  customerName?: string;
  customerCountry?: string;
  model: string;
  brand?: string;
  quantity: number;
  maxQuantity: number;
  unitPrice?: number | null;
  netPriceCny?: number | null;
  targetPrice?: number | null;
  amount: number;
  bargainAmount?: number | null;
  bargainRate?: number | null;
  orderCancelled: boolean;
}

export interface PurchaseOrder {
  id: number;
  poNo?: string;
  status: number;
  statusName: string;
  supplierId: number;
  supplierName?: string;
  purchaserId: number;
  purchaserName?: string;
  orderDate?: string;
  orderedAt?: string;
  createTime: string;
  currencyCode: string;
  exchangeRate: number;
  taxIncluded: boolean;
  taxRate: number;
  itemAmount: number;
  feeAmount: number;
  totalAmount: number;
  totalAmountCny: number;
  targetAmount: number;
  bargainAmount: number;
  bargainRate?: number | null;
  paymentTerms: PaymentTerm[];
  paymentTermsText?: string;
  contractNo?: string;
  contractAmount?: number | null;
  contractDiff?: number | null;
  cancelReason?: string;
  cancelledByName?: string;
  cancelledAt?: string;
  editable: boolean;
  items: PoItem[];
  fees: { id: number; feeName: string; amount: number }[];
  attachments: {
    id: number;
    fileName: string;
    fileSize: number;
    contentType: string;
    uploadedByName?: string;
    createTime: string;
  }[];
  logs: {
    action: string;
    content: string;
    operatorName?: string;
    createTime: string;
  }[];
}

export interface SavePoBody {
  supplierId?: number;
  currencyCode: string;
  taxIncluded: boolean;
  taxRate?: number;
  paymentTerms: PaymentTerm[];
  contractNo?: string;
  contractAmount?: number | null;
  items: { id: number; quantity: number; unitPrice?: number | null }[];
  fees: { feeName: string; amount: number }[];
}

// ---------------------------------------------------------------- 请求

const quiet = { skipErrorHandler: true } as const;
const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(
  method: string,
  url: string,
  data?: unknown,
  params?: object,
) =>
  request<{ data: T }>(url, { method, data, params, ...quiet }).then(
    (r) => r.data,
  );

const REQ = '/api/v1/purchase/requirements';
const PO = '/api/v1/purchase/orders';

export const requirementApi = {
  page: (q: RequirementQuery) =>
    send<{ total: number; records: Requirement[] }>('POST', `${REQ}/page`, q),
  stats: () => get<RequirementStats>(`${REQ}/stats`),
  split: (
    id: number,
    body: { quantity: number; purchaserId?: number; supplierId?: number },
  ) => send<void>('POST', `${REQ}/${id}/split`, body),
  assign: (ids: number[], purchaserId?: number | null) =>
    send<void>('POST', `${REQ}/assign`, { ids, purchaserId }),
  preview: (ids: number[]) =>
    get<{ groups: GenerateGroup[] }>(`${REQ}/generate-preview`, {
      ids: ids.join(','),
    }),
  generate: (groups: { supplierId: number; requirementIds: number[] }[]) =>
    send<number[]>('POST', `${REQ}/generate`, { groups }),
};

export const poApi = {
  page: (q: PoQuery) =>
    send<{ total: number; records: PoListItem[] }>('POST', `${PO}/page`, q),
  stats: () => get<PoStats>(`${PO}/stats`),
  detail: (id: number) => get<PurchaseOrder>(`${PO}/${id}`),
  save: (id: number, body: SavePoBody) =>
    send<PurchaseOrder>('PUT', `${PO}/${id}`, body),
  removeItems: (id: number, ids: number[]) =>
    send<PurchaseOrder | null>('DELETE', `${PO}/${id}/items`, undefined, {
      ids: ids.join(','),
    }),
  move: (id: number, itemIds: number[], supplierId: number) =>
    send<number>('POST', `${PO}/${id}/move`, { itemIds, supplierId }),
  confirm: (id: number, orderDate: string) =>
    send<PurchaseOrder>('POST', `${PO}/${id}/confirm`, { orderDate }),
  cancel: (id: number, reason: string) =>
    send<PurchaseOrder>('POST', `${PO}/${id}/cancel`, { reason }),
  remove: (id: number) => send<void>('DELETE', `${PO}/${id}`),
  upload: (id: number, file: File) => {
    const data = new FormData();
    data.append('file', file);
    return request<{ data: PurchaseOrder }>(`${PO}/${id}/attachments`, {
      method: 'POST',
      data,
      requestType: 'form',
      ...quiet,
    }).then((r) => r.data);
  },
  removeAttachment: (id: number, attachmentId: number) =>
    send<PurchaseOrder>('DELETE', `${PO}/${id}/attachments/${attachmentId}`),
  /** 预览或下载合同：带登录态取文件后在新窗口打开或保存 */
  openAttachment: async (
    id: number,
    attachmentId: number,
    fileName: string,
    inline: boolean,
  ) => {
    const blob: Blob = await request(
      `${PO}/${id}/attachments/${attachmentId}`,
      {
        method: 'GET',
        params: { inline },
        responseType: 'blob',
        ...quiet,
      },
    );
    const url = URL.createObjectURL(blob);
    if (inline) {
      window.open(url, '_blank', 'noopener');
    } else {
      const a = document.createElement('a');
      a.href = url;
      a.download = fileName;
      a.click();
    }
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  },
};
