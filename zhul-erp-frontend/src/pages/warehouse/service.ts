import { request } from '@umijs/max';

export { readBizError } from '@/pages/crm/opportunity/service';

// ---------------------------------------------------------------- 类型

export interface PageResult<T> {
  total: number;
  records: T[];
}

export interface Audit {
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

/** 业务附件：预览与下载走 GET /api/v1/attachments/{id}（需登录） */
export interface Attachment {
  id: number;
  /** 1-图片、2-视频 */
  kind: number;
  fileName: string;
  fileSize: number;
  contentType: string;
  uploadedByName?: string;
  createTime?: string;
}

export type OwnerType = 'SHIPMENT' | 'RECEIPT' | 'SHOOT';

export interface ShipmentItem {
  id: number;
  poItemId: number;
  model: string;
  brand?: string;
  category?: string;
  quantity: number;
}

export interface Shipment extends Audit {
  id: number;
  sdNo: string;
  poId: number;
  poNo?: string;
  supplierName?: string;
  shop?: boolean;
  carrier?: string;
  trackingNo?: string;
  shipDate?: string;
  expectedArrivalDate?: string;
  /** 在途且已过预计到货日期 */
  arrivalOverdue?: boolean;
  itemCount: number;
  totalQuantity: number;
  items: ShipmentItem[];
  attachmentCount: number;
  /** 1-采购员登记、2-仓库补登 */
  source: number;
  sourceName: string;
  /** 1-在途、2-已入库、3-已作废 */
  status: number;
  statusName: string;
  voidReason?: string;
  note?: string;
  purchaserId?: number;
  purchaserName?: string;
  receiptId?: number;
  grNo?: string;
}

export interface ShipmentDetail {
  shipment: Shipment;
  attachments: Attachment[];
  editable: boolean;
}

export interface ShipmentForm {
  poId: number;
  poNo?: string;
  supplierName?: string;
  shipmentId?: number;
  carrier?: string;
  trackingNo?: string;
  shipDate?: string;
  expectedArrivalDate?: string;
  note?: string;
  attachments: Attachment[];
  lines: {
    poItemId: number;
    model: string;
    brand?: string;
    category?: string;
    orderedQty: number;
    shippedQty: number;
    maxQuantity: number;
    quantity: number;
  }[];
}

export interface SaveShipmentBody {
  poId?: number;
  carrier?: string;
  trackingNo?: string;
  shipDate: string;
  /** 为空时后端按快递时效估算 */
  expectedArrivalDate?: string | null;
  note?: string;
  items: { poItemId: number; quantity: number }[];
  attachmentIds: number[];
}

export interface ShipmentQuery {
  page: number;
  pageSize: number;
  keyword?: string;
  status?: number;
}

export interface Discrepancy extends Audit {
  id: number;
  receiptId: number;
  grNo?: string;
  receivedDate?: string;
  poId: number;
  poNo?: string;
  supplierName?: string;
  currencyCode?: string;
  model: string;
  brand?: string;
  category?: string;
  /** 1-少发、2-不良、3-多发 */
  type: number;
  typeName: string;
  quantity: number;
  /** 1-待处理、2-已处理 */
  status: number;
  statusName: string;
  resolution: number;
  resolutionName?: string;
  discountAmount?: number | null;
  returnCarrier?: string;
  returnTrackingNo?: string;
  returnFreight?: number | null;
  freeOfCharge: boolean;
  note?: string;
  purchaserId?: number;
  purchaserName?: string;
  handledByName?: string;
  handledAt?: string;
  holdId?: number;
}

export interface HandleDiscrepancyBody {
  resolution: number;
  note?: string;
  discountAmount?: number | null;
  returnCarrier?: string;
  returnTrackingNo?: string;
  returnFreight?: number | null;
  freeOfCharge?: boolean;
  locationNote?: string;
}

export interface Receipt extends Audit {
  id: number;
  grNo: string;
  receivedDate?: string;
  shipmentId: number;
  sdNo?: string;
  poId: number;
  poNo?: string;
  supplierName?: string;
  itemCount: number;
  receivedQty: number;
  qualifiedQty: number;
  defectiveQty: number;
  discrepancyCount: number;
  receivedByName?: string;
  /** 1-有效、2-已冲销 */
  status: number;
  statusName: string;
  reverseReason?: string;
  note?: string;
}

export interface ReceiptDetail {
  receipt: Receipt;
  items: {
    id: number;
    model: string;
    brand?: string;
    category?: string;
    shippedQty: number;
    receivedQty: number;
    qualifiedQty: number;
    defectiveQty: number;
    note?: string;
  }[];
  discrepancies: Discrepancy[];
  attachments: Attachment[];
  shipmentAttachments: Attachment[];
  reversible: boolean;
}

export interface QtyLine {
  receivedQty: number;
  qualifiedQty: number;
  defectiveQty: number;
  note?: string;
}

export interface ReceivableOrder {
  poId: number;
  poNo?: string;
  supplierName?: string;
  purchaserName?: string;
  orderDate?: string;
  lines: {
    poItemId: number;
    model: string;
    brand?: string;
    category?: string;
    orderedQty: number;
    unshippedQty: number;
  }[];
}

export interface Hold extends Audit {
  id: number;
  model: string;
  brand?: string;
  category?: string;
  quantity: number;
  costPrice: number;
  locationNote?: string;
  /** 1-暂存中、2-已退回、3-已报废、4-已转样品 */
  status: number;
  statusName: string;
  receiptId: number;
  grNo?: string;
  poId: number;
  poNo?: string;
  supplierName?: string;
  returnCarrier?: string;
  returnTrackingNo?: string;
  returnFreight?: number | null;
  handledByName?: string;
  handledAt?: string;
  handleNote?: string;
}

export interface ShootTask extends Audit {
  id: number;
  model: string;
  brand?: string;
  category?: string;
  receiptId: number;
  grNo?: string;
  soId?: number;
  soNo?: string;
  /** 1-待拍摄、2-已完成、3-已跳过 */
  status: number;
  statusName: string;
  unboxingCount: number;
  inspectionCount: number;
  photoCount: number;
  reusable: boolean;
  reusableShotAt?: string;
  reusedFromTaskId?: number;
  skipReason?: string;
  shooterName?: string;
  completedAt?: string;
}

export interface ShootMedia {
  id: number;
  attachmentId: number;
  /** 1-拆箱视频、2-验货视频、3-实物图 */
  mediaType: number;
  kind: number;
  fileName: string;
  fileSize: number;
  contentType: string;
  createTime?: string;
}

export interface ShootDetail {
  task: ShootTask;
  media: ShootMedia[];
  sameModel: ShootMedia[];
}

// ---------------------------------------------------------------- 请求

const quiet = { skipErrorHandler: true } as const;
const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(method: string, url: string, data?: unknown) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);

const SHIP = '/api/v1/purchase/shipments';
const DIFF = '/api/v1/purchase/discrepancies';
const GR = '/api/v1/warehouse/receipts';
const HOLD = '/api/v1/warehouse/holds';
const SHOOT = '/api/v1/warehouse/shoots';
const FILE = '/api/v1/attachments';

export const attachmentApi = {
  upload: (ownerType: OwnerType, file: File) => {
    const data = new FormData();
    data.append('file', file);
    return request<{ data: Attachment }>(FILE, {
      method: 'POST',
      params: { ownerType },
      data,
      requestType: 'form',
      ...quiet,
    }).then((r) => r.data);
  },
  /** 带登录态取文件，返回本地地址（用完 revoke） */
  blobUrl: async (id: number) => {
    const blob: Blob = await request(`${FILE}/${id}`, {
      method: 'GET',
      responseType: 'blob',
      ...quiet,
    });
    return URL.createObjectURL(blob);
  },
};

export interface ArrivalEstimate {
  date?: string;
  days: number;
  /** 估算依据，如「顺丰 · 上海 → 福州 2 天」 */
  basis: string;
}

export interface TransitTime extends Audit {
  id: number;
  carrier: string;
  /** 空表示该快递公司的默认天数 */
  originProvince: string;
  days: number;
  remark?: string;
}

const TRANSIT = '/api/v1/system/transit-times';

export const transitApi = {
  list: (q: { carrier?: string; province?: string }) =>
    get<TransitTime[]>(TRANSIT, q),
  create: (body: Omit<TransitTime, 'id'>) =>
    send<TransitTime>('POST', TRANSIT, body),
  update: (id: number, body: Omit<TransitTime, 'id'>) =>
    send<TransitTime>('PUT', `${TRANSIT}/${id}`, body),
  remove: (id: number) => send<void>('DELETE', `${TRANSIT}/${id}`),
  defaultDays: () => get<number>(`${TRANSIT}/default-days`),
  setDefaultDays: (days: number) =>
    send<number>('PUT', `${TRANSIT}/default-days`, { days }),
};

export const shipmentApi = {
  estimate: (poId: number, carrier: string, shipDate: string) =>
    get<ArrivalEstimate>(`${SHIP}/estimate`, { poId, carrier, shipDate }),
  page: (q: ShipmentQuery) =>
    send<PageResult<Shipment>>('POST', `${SHIP}/page`, q),
  detail: (id: number) => get<ShipmentDetail>(`${SHIP}/${id}`),
  form: (params: { poId?: number; shipmentId?: number }) =>
    get<ShipmentForm>(`${SHIP}/form`, params),
  create: (body: SaveShipmentBody) => send<ShipmentDetail>('POST', SHIP, body),
  update: (id: number, body: SaveShipmentBody) =>
    send<ShipmentDetail>('PUT', `${SHIP}/${id}`, body),
  void: (id: number, reason: string) =>
    send<ShipmentDetail>('POST', `${SHIP}/${id}/void`, { reason }),
};

export const discrepancyApi = {
  page: (q: {
    page: number;
    pageSize: number;
    keyword?: string;
    type?: number;
    status?: number;
  }) => send<PageResult<Discrepancy>>('POST', `${DIFF}/page`, q),
  counts: () => get<{ pending: number }>(`${DIFF}/counts`),
  evidence: (id: number) =>
    get<{ note?: string; attachments: Attachment[] }>(`${DIFF}/${id}/evidence`),
  handle: (id: number, body: HandleDiscrepancyBody) =>
    send<Discrepancy>('POST', `${DIFF}/${id}/handle`, body),
  reopen: (id: number) => send<Discrepancy>('POST', `${DIFF}/${id}/reopen`),
};

export const receiptApi = {
  pending: (q: ShipmentQuery) =>
    send<PageResult<Shipment>>('POST', `${GR}/pending`, q),
  counts: () => get<{ pending: number }>(`${GR}/counts`),
  shipment: (id: number) => get<ShipmentDetail>(`${GR}/shipments/${id}`),
  page: (q: { page: number; pageSize: number; keyword?: string }) =>
    send<PageResult<Receipt>>('POST', `${GR}/page`, q),
  detail: (id: number) => get<ReceiptDetail>(`${GR}/${id}`),
  accept: (body: {
    shipmentId: number;
    receivedDate: string;
    note?: string;
    items: (QtyLine & { shipmentItemId: number })[];
    attachmentIds: number[];
  }) => send<ReceiptDetail>('POST', GR, body),
  orders: (keyword?: string) =>
    get<ReceivableOrder[]>(`${GR}/orders`, { keyword }),
  direct: (body: {
    poId: number;
    receivedDate: string;
    note?: string;
    items: (QtyLine & { poItemId: number })[];
    attachmentIds: number[];
  }) => send<ReceiptDetail>('POST', `${GR}/direct`, body),
  reverse: (id: number, reason: string) =>
    send<ReceiptDetail>('POST', `${GR}/${id}/reverse`, { reason }),
};

export const holdApi = {
  page: (q: {
    page: number;
    pageSize: number;
    keyword?: string;
    status?: number;
  }) => send<PageResult<Hold>>('POST', `${HOLD}/page`, q),
  counts: () => get<{ pending: number }>(`${HOLD}/counts`),
  handle: (
    id: number,
    body: {
      status: number;
      note: string;
      returnCarrier?: string;
      returnTrackingNo?: string;
      returnFreight?: number | null;
    },
  ) => send<Hold>('POST', `${HOLD}/${id}/handle`, body),
};

export const shootApi = {
  page: (q: {
    page: number;
    pageSize: number;
    keyword?: string;
    status?: number;
  }) => send<PageResult<ShootTask>>('POST', `${SHOOT}/page`, q),
  counts: () => get<{ pending: number }>(`${SHOOT}/counts`),
  detail: (id: number) => get<ShootDetail>(`${SHOOT}/${id}`),
  addMedia: (id: number, attachmentId: number, mediaType: number) =>
    send<ShootDetail>('POST', `${SHOOT}/${id}/media`, {
      attachmentId,
      mediaType,
    }),
  removeMedia: (id: number, mediaId: number) =>
    send<ShootDetail>('DELETE', `${SHOOT}/${id}/media/${mediaId}`),
  reuse: (id: number) => send<ShootDetail>('POST', `${SHOOT}/${id}/reuse`),
  skip: (id: number, reason: string) =>
    send<ShootDetail>('POST', `${SHOOT}/${id}/skip`, { reason }),
};
