import { request } from '@umijs/max';
import type { Attachment, Audit, PageResult } from '@/pages/warehouse/service';

export { readBizError } from '@/pages/crm/opportunity/service';

// ---------------------------------------------------------------- 类型

export interface Forwarder {
  id: number;
  name: string;
  volumeDivisor?: number;
}

export interface NoticeForm {
  soId: number;
  soNo: string;
  customerName?: string;
  lines: {
    soItemId: number;
    model: string;
    brand?: string;
    quantity: number;
    /** 有采购需求（系统外采购为 false） */
    tracked: boolean;
    received?: number;
    occupied: number;
    /** 可出库数量（修改时含本单原数量） */
    available: number;
    current: number;
    inTransit?: number;
    pendingShip?: number;
    earliestArrival?: string;
  }[];
}

export interface OutboundBox {
  id: number;
  boxNo: number;
  length: number;
  width: number;
  height: number;
  grossWeight: number;
  netWeight?: number | null;
  chargeable: number;
  items: { outboundItemId: number; model: string; quantity: number }[];
}

export interface Outbound extends Audit {
  id: number;
  obNo: string;
  soId: number;
  soNo?: string;
  customerId: number;
  customerName?: string;
  ownerName?: string;
  forwarderId: number;
  forwarderName?: string;
  /** 1-发货通知、2-直发货代 */
  source: number;
  sourceName: string;
  /** 1-待打包、2-已打包、3-已交货代、4-已撤回 */
  status: number;
  statusName: string;
  note?: string;
  withdrawReason?: string;
  totalQuantity: number;
  items: {
    id: number;
    soItemId: number;
    model: string;
    brand?: string;
    quantity: number;
  }[];
  boxes: OutboundBox[];
  boxCount: number;
  chargeableWeight: number;
  courier?: {
    id: number;
    carrier: string;
    trackingNo?: string;
    sentDate?: string;
    freight: number;
    payerName?: string;
    share?: number;
    outboundCount: number;
  };
  logisticsId?: number;
  shNo?: string;
  packedByName?: string;
  packedAt?: string;
}

export interface BoxInput {
  length: number | null;
  width: number | null;
  height: number | null;
  grossWeight: number | null;
  netWeight?: number | null;
  items: { key: number; quantity: number | null }[];
}

export interface DirectRef {
  id: number;
  sdNo: string;
  poId: number;
  poNo?: string;
  supplierName?: string;
  customerId?: number;
  customerName?: string;
  soNos: string[];
  carrier?: string;
  trackingNo?: string;
  shipDate?: string;
  expectedArrivalDate?: string;
  confirmed: boolean;
  outboundId?: number;
  items: {
    shipmentItemId: number;
    model: string;
    brand?: string;
    quantity: number;
  }[];
}

export interface PendingGroup {
  customerId: number;
  customerName?: string;
  forwarderId: number;
  forwarderName?: string;
  outbounds: Outbound[];
  directs: DirectRef[];
}

export interface Logistics extends Audit {
  id: number;
  shNo: string;
  customerId: number;
  customerName?: string;
  forwarderId: number;
  forwarderName?: string;
  volumeDivisor: number;
  ownerName?: string;
  /** 1-待出运、2-已出运、3-已作废 */
  status: number;
  statusName: string;
  carrier?: string;
  waybillNo?: string;
  shippedDate?: string;
  freight?: number | null;
  reconciled: boolean;
  voidReason?: string;
  note?: string;
  outboundCount: number;
  orders: { id: number; soNo?: string }[];
  boxCount: number;
  chargeableWeight: number;
  outbounds?: Outbound[];
  boxFreight?: Record<number, number>;
  boxChargeable?: Record<number, number>;
  directs?: DirectRef[];
  docGroups?: {
    id: number;
    ciNo: string;
    plNo: string;
    soNos: string[];
    paymentRef?: string;
  }[];
  faceSheets?: Attachment[];
}

export interface Statement extends Audit {
  id: number;
  forwarderId: number;
  forwarderName?: string;
  period: string;
  /** 1-草稿、2-已确认 */
  status: number;
  statusName: string;
  shipmentCount: number;
  ourTotal: number;
  statementTotal: number;
  diffTotal: number;
  diffCount: number;
  confirmedByName?: string;
  confirmedAt?: string;
  lines?: {
    id: number;
    logisticsId: number;
    shNo?: string;
    shippedDate?: string;
    carrier?: string;
    waybillNo?: string;
    customerName?: string;
    ourFreight: number;
    statementAmount: number;
    diff: number;
    note?: string;
  }[];
}

// ---------------------------------------------------------------- 请求

const quiet = { skipErrorHandler: true } as const;
const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(method: string, url: string, data?: unknown) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);

/** 带登录态下载文件并保存 */
export const download = async (url: string, params?: object) => {
  const res = (await request<Blob>(url, {
    method: 'GET',
    params,
    responseType: 'blob',
    getResponse: true,
    ...quiet,
  })) as unknown as {
    data: Blob;
    headers: Record<string, string | undefined>;
  };
  if (res.data.type.includes('json')) {
    const body = JSON.parse(await res.data.text()) as { message?: string };
    throw new Error(body.message || '下载失败，请稍后重试');
  }
  const disposition = res.headers?.['content-disposition'] ?? '';
  const match = /filename\*=UTF-8''([^;]+)/.exec(disposition);
  const name = match ? decodeURIComponent(match[1]) : 'download';
  const href = window.URL.createObjectURL(res.data);
  const a = document.createElement('a');
  a.href = href;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.setTimeout(() => window.URL.revokeObjectURL(href), 1000);
};

const L = '/api/v1/logistics';

export const forwarderApi = {
  list: () => get<Forwarder[]>(`${L}/forwarders`),
};

const toBoxes = (boxes: BoxInput[], key: 'outboundItemId' | 'shipmentItemId') =>
  boxes.map((b) => ({
    length: b.length,
    width: b.width,
    height: b.height,
    grossWeight: b.grossWeight,
    netWeight: b.netWeight ?? null,
    items: b.items
      .filter((i) => (i.quantity ?? 0) > 0)
      .map((i) => ({ [key]: i.key, quantity: i.quantity })),
  }));

export const outboundApi = {
  noticeForm: (params: { soId?: number; outboundId?: number }) =>
    get<NoticeForm>(`${L}/outbounds/notice-form`, params),
  createNotice: (body: {
    soId: number;
    forwarderId: number;
    note?: string;
    items: { soItemId: number; quantity: number }[];
  }) => send<Outbound>('POST', `${L}/outbounds/notice`, body),
  updateNotice: (
    id: number,
    body: {
      forwarderId: number;
      note?: string;
      items: { soItemId: number; quantity: number }[];
    },
  ) => send<Outbound>('PUT', `${L}/outbounds/notice/${id}`, body),
  withdraw: (id: number, reason: string) =>
    send<Outbound>('POST', `${L}/outbounds/${id}/withdraw`, { reason }),
  byOrder: (soId: number) => get<Outbound[]>(`${L}/outbounds/by-order/${soId}`),
  page: (q: {
    page: number;
    pageSize: number;
    keyword?: string;
    status?: number;
  }) => send<PageResult<Outbound>>('POST', `${L}/outbounds/page`, q),
  counts: () =>
    get<{ pending: number; packed: number }>(`${L}/outbounds/counts`),
  detail: (id: number) => get<Outbound>(`${L}/outbounds/${id}`),
  pack: (id: number, boxes: BoxInput[]) =>
    send<Outbound>('PUT', `${L}/outbounds/${id}/pack`, {
      boxes: toBoxes(boxes, 'outboundItemId'),
    }),
  handOver: (body: {
    outboundIds: number[];
    carrier: string;
    trackingNo?: string;
    sentDate: string;
    freight: number;
  }) => send<Outbound[]>('POST', `${L}/outbounds/hand-over`, body),
  undoHandOver: (id: number, reason: string) =>
    send<Outbound>('POST', `${L}/outbounds/${id}/undo-hand-over`, { reason }),
};

const SH = `${L}/shipments`;

export const logisticsApi = {
  pending: () => get<PendingGroup[]>(`${SH}/pending`),
  page: (q: {
    page: number;
    pageSize: number;
    keyword?: string;
    status?: number;
    forwarderId?: number;
  }) => send<PageResult<Logistics>>('POST', `${SH}/page`, q),
  detail: (id: number) => get<Logistics>(`${SH}/${id}`),
  create: (body: {
    customerId: number;
    forwarderId: number;
    outboundIds: number[];
    directShipmentIds: number[];
  }) => send<Logistics>('POST', SH, body),
  updateItems: (
    id: number,
    body: { outboundIds: number[]; directShipmentIds: number[] },
  ) => send<Logistics>('PUT', `${SH}/${id}/items`, body),
  void: (id: number, reason: string) =>
    send<Logistics>('POST', `${SH}/${id}/void`, { reason }),
  confirmDirect: (
    id: number,
    shipmentId: number,
    lines: { shipmentItemId: number; quantity: number }[],
    boxes: BoxInput[],
  ) =>
    send<Logistics>('POST', `${SH}/${id}/directs/${shipmentId}/confirm`, {
      lines,
      boxes: toBoxes(boxes, 'shipmentItemId'),
    }),
  ship: (
    id: number,
    body: {
      carrier: string;
      waybillNo: string;
      shippedDate: string;
      freight: number;
      attachmentIds: number[];
    },
    update: boolean,
  ) =>
    send<Logistics>(
      update ? 'PUT' : 'POST',
      `${SH}/${id}/${update ? 'shipping' : 'ship'}`,
      body,
    ),
  generateDocs: (
    id: number,
    mode: 'MERGED' | 'PER_ORDER',
    paymentRef?: string,
  ) => send<Logistics>('POST', `${SH}/${id}/docs`, { mode, paymentRef }),
  exportDoc: (groupId: number, kind: 'ci' | 'pl', format: 'xlsx' | 'pdf') =>
    download(`${SH}/docs/${groupId}/export`, { kind, format }),
};

const ST = `${L}/statements`;

export const statementApi = {
  page: (q: { page: number; pageSize: number; forwarderId?: number }) =>
    get<PageResult<Statement>>(ST, q),
  detail: (id: number) => get<Statement>(`${ST}/${id}`),
  create: (forwarderId: number, period: string) =>
    send<Statement>('POST', ST, { forwarderId, period }),
  save: (
    id: number,
    lines: { id: number; statementAmount: number; note?: string }[],
  ) => send<Statement>('PUT', `${ST}/${id}`, { lines }),
  confirm: (
    id: number,
    lines: { id: number; statementAmount: number; note?: string }[],
  ) => send<Statement>('POST', `${ST}/${id}/confirm`, { lines }),
  remove: (id: number) => send<void>('DELETE', `${ST}/${id}`),
  export: (id: number) => download(`${ST}/${id}/export`),
};
