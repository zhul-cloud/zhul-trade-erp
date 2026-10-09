import { request } from '@umijs/max';

export { readBizError } from '@/pages/crm/opportunity/service';

// ---------------------------------------------------------------- 类型

/** 付款条件的一期：trigger 1-下单后、2-发货前、3-入库后 */
export interface PaymentTerm {
  percent: number;
  trigger: number;
  days?: number;
}

/** 采购对象：老供应商（supplierId），或线上店铺（channel + shopName） */
export interface CounterpartyValue {
  supplierId?: number;
  supplierName?: string;
  channel?: number;
  shopName?: string;
}

export interface PoRef {
  id: number;
  poNo?: string;
  status: number;
  /** 老供应商名称，或「淘宝 · 店铺名」 */
  supplierName?: string;
  shop?: boolean;
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
  category?: string;
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
  createTime: string;
  createBy?: string;
  updateTime: string;
  updateBy?: string;
}

export interface RequirementOrder {
  soId: number;
  soNo?: string;
  salesDate?: string;
  customerName?: string;
  customerCountry?: string;
  customerType?: number;
  stockType?: number;
  /** 1-PI 转成、2-手动创建 */
  source?: number;
  ownerName?: string;
  orderedCount: number;
  partialCount: number;
  draftCount: number;
  pendingCount: number;
  updateTime: string;
  lines: Requirement[];
}

export interface RequirementQuery {
  page: number;
  pageSize: number;
  keyword?: string;
  purchaserId?: number;
  /** 只看我负责的 */
  mine?: boolean;
  /** 向谁买：supplier-老供应商、shop-线上店铺、none-还没定 */
  sourceType?: 'supplier' | 'shop' | 'none';
  supplierId?: number;
  /** open-还没全部下单、need-需要生成采购单、all-全部 */
  view?: 'open' | 'need' | 'all';
  stockType?: number;
}

export interface RequirementStats {
  open: number;
  inDraft: number;
  need: number;
  unplanned: number;
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
  /** 老供应商名称，或「淘宝 · 店铺名」 */
  supplierName?: string;
  shop?: boolean;
  channel?: number;
  shopName?: string;
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
  createBy?: string;
  updateTime: string;
  updateBy?: string;
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
  category?: string;
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
  /** 老供应商名称，或「淘宝 · 店铺名」 */
  supplierName?: string;
  /** 采购对象为线上店铺：不显示合同，可以转为供应商 */
  shop?: boolean;
  channel?: number;
  shopName?: string;
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
  orders: (q: RequirementQuery) =>
    send<{ total: number; records: RequirementOrder[] }>(
      'POST',
      `${REQ}/orders/page`,
      q,
    ),
  stats: (mine: boolean) => get<RequirementStats>(`${REQ}/stats`, { mine }),
  split: (
    id: number,
    body: { quantity: number; purchaserId?: number } & CounterpartyValue,
  ) => send<void>('POST', `${REQ}/${id}/split`, body),
  source: (ids: number[], cp: CounterpartyValue) =>
    send<void>('POST', `${REQ}/source`, { ids, ...cp }),
  assign: (ids: number[], purchaserId?: number | null) =>
    send<void>('POST', `${REQ}/assign`, { ids, purchaserId }),
  preview: (ids: number[]) =>
    get<{ groups: GenerateGroup[] }>(`${REQ}/generate-preview`, {
      ids: ids.join(','),
    }),
  generate: (groups: (CounterpartyValue & { requirementIds: number[] })[]) =>
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
  move: (id: number, itemIds: number[], cp: CounterpartyValue) =>
    send<number>('POST', `${PO}/${id}/move`, { itemIds, ...cp }),
  convertShop: (id: number) =>
    send<PurchaseOrder>('POST', `${PO}/${id}/convert-shop`),
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
