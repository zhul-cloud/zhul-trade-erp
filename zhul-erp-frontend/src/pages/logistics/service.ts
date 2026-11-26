import { request } from '@umijs/max';
import type {
  SupplierAccount,
  SupplierFormValues,
} from '@/pages/supplier/service';
import type {
  Attachment,
  AttachmentModels,
  Audit,
  DocLog,
  DocRef,
  PageResult,
} from '@/pages/warehouse/service';

export { readBizError } from '@/pages/crm/opportunity/service';

// ---------------------------------------------------------------- 类型

export interface Forwarder {
  id: number;
  name: string;
  volumeDivisor?: number;
  /** 这家货代是当前客户的指定货代（按客户取列表时），排在前面 */
  customerOwned?: boolean;
  /** 归属客户：为空是公司货代 */
  customerId?: number | null;
  contactName?: string;
  /** 交接方式：1-送到货代、2-上门取件（客户用自己的快递账号约快递上门，openspec customer-courier-pickup） */
  handoverMode?: number;
  courierCarrier?: string | null;
  courierAccount?: string | null;
}

/** 货代管理列表的一行（spec logistics/forwarder） */
export interface ForwarderRow {
  id: number;
  code: string;
  name: string;
  shortName?: string;
  /** 归属客户：空为公司货代 */
  customerId?: number | null;
  customerName?: string | null;
  handoverMode?: number;
  courierCarrier?: string | null;
  courierAccount?: string | null;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  volumeDivisor?: number;
  accountCount: number;
  /** 默认收款账户：开户行 ****后四位 */
  defaultAccount?: string | null;
  /** 0-停用、1-启用 */
  status: number;
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface ForwarderQuery {
  page: number;
  pageSize: number;
  keyword?: string;
  /** company-公司货代、customer-客户指定 */
  owner?: 'company' | 'customer';
  customerId?: number;
  status?: number;
}

/** 交接方式：1-送到货代、2-上门取件 */
export const HANDOVER = { DELIVER: 1, PICKUP: 2 } as const;

/** 「上门取件 · 承运商 · 账号」，与后端 LogisticsSupport.pickupLabel 一致 */
export const pickupText = (carrier?: string | null, account?: string | null) =>
  ['上门取件', carrier?.trim(), account?.trim()].filter(Boolean).join(' · ');

/** 承运商候选（登记出运、上门取件的货代共用） */
export const CARRIERS = ['DHL', 'FedEx', 'UPS', 'TNT', 'EMS'];

export interface SaveForwarderBody {
  name: string;
  shortName?: string;
  customerId?: number | null;
  /** 1-送到货代、2-上门取件；只有客户指定货代能选上门取件 */
  handoverMode?: number;
  courierCarrier?: string;
  courierAccount?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  address?: string;
  volumeDivisor?: number;
  accounts?: SupplierFormValues['accounts'];
  remark?: string;
  status?: number;
}

/** 编辑取数（与供应商编辑取数同结构，收款账号为明文） */
export interface ForwarderForm extends Omit<SaveForwarderBody, 'accounts'> {
  id: number;
  supplierCode: string;
  customerName?: string | null;
  status: number;
  accounts?: SupplierAccount[];
}

export interface NoticeForm {
  soId: number;
  soNo: string;
  customerId?: number;
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
  items: {
    outboundItemId: number;
    /** 箱内这一行所属的销售出库单（合并打包的箱子会有多张） */
    outboundId?: number;
    obNo?: string;
    model: string;
    quantity: number;
  }[];
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
  /** 货代为上门取件时「上门取件 · 承运商 · 账号」（openspec customer-courier-pickup） */
  pickupLabel?: string | null;
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
    /** 关联的商品与是否候选商品（还没审核建档） */
    productId?: number | null;
    productCandidate?: boolean;
    brand?: string;
    category?: string;
    quantity: number;
    /** 已装箱数量 */
    packedQty?: number;
  }[];
  boxes: OutboundBox[];
  /** 详情返回：单据附件（与打包照片分开）、关联单据、操作日志 */
  attachments?: Attachment[];
  related?: DocRef[];
  logs?: DocLog[];
  /** 打包发货的照片（详情返回） */
  packingAttachments?: Attachment[];
  /** 还没有营销实拍的型号（详情返回，只提醒） */
  missingMarketing?: string[];
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
  /** 合并打包组；同组的其他单（箱子、箱数、计费重按整组） */
  packId?: number | null;
  packMates?: { id: number; obNo: string; soNo?: string; status: number }[];
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
  customerCountry?: string;
  customerContact?: string;
  /** 1-新客户、2-老客户 */
  customerType?: number;
  forwarderId: number;
  forwarderName?: string;
  outbounds: Outbound[];
  directs: DirectRef[];
}

export interface Logistics extends Audit {
  id: number;
  shNo: string;
  /** 历史迁移的出运单（只读） */
  migrated?: boolean;
  customerId: number;
  customerName?: string;
  /** 客户国家 / 地区、新老客户（1-新、2-老）、主联系人：列表客户列三行显示 */
  customerCountry?: string;
  customerType?: number;
  customerContact?: string;
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
  /** 交客户指定货代：运单号选填（未填显示「客户安排」），运费为送货运费，不进运费账单 */
  customerForwarder?: boolean;
  /** 上门取件：待出运按货代当前的交接方式，已出运按登记时的快照；pickupCarrier 为货代上的承运商 */
  pickup?: boolean;
  pickupLabel?: string | null;
  pickupCarrier?: string | null;
  voidReason?: string;
  note?: string;
  outboundCount: number;
  /** 所装销售出库单号（列表「来源单号」） */
  outboundNos?: string[];
  outboundIds?: number[];
  /** 列表：第一个型号与件数、件数合计、去重的品牌与品类 */
  firstModel?: string;
  firstQty?: number;
  totalQuantity?: number;
  brands?: string[];
  categories?: string[];
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
  /** 详情返回：单据附件（与面单分开）、关联单据、操作日志 */
  attachments?: Attachment[];
  related?: DocRef[];
  logs?: DocLog[];
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
  /** 付款：确认后生成货代应付；1-未付、2-部分付款、3-已付清 */
  payableId?: number;
  payStatus?: number;
  payableAmount?: number;
  paidAmount?: number;
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
  /** 可选货代：传客户时为公司货代 + 这个客户的指定货代（指定的在前），不传时为全部启用的货代 */
  list: (customerId?: number) =>
    get<Forwarder[]>(
      `${L}/forwarders`,
      customerId ? { customerId } : undefined,
    ),
  page: (q: ForwarderQuery) =>
    get<{ total: number; records: ForwarderRow[] }>(`${L}/forwarders/page`, q),
  counts: () =>
    get<{ all: number; company: number; customer: number }>(
      `${L}/forwarders/counts`,
    ),
  form: (id: number) => get<ForwarderForm>(`${L}/forwarders/${id}/form`),
  create: (body: SaveForwarderBody) =>
    send<number>('POST', `${L}/forwarders`, body),
  update: (id: number, body: SaveForwarderBody) =>
    send<void>('PUT', `${L}/forwarders/${id}`, body),
  setStatus: (id: number, status: number) =>
    /** 停用时货代仓还有货，返回提醒（「货代仓还有 N 件货」），不拦停用 */
    send<string | null>('PUT', `${L}/forwarders/${id}/status`, { status }),
  /** 客户详情「客户指定货代」 */
  ofCustomer: (customerId: number) =>
    get<ForwarderRow[]>(`${L}/forwarders/by-customer/${customerId}`),
  createForCustomer: (customerId: number, body: SaveForwarderBody) =>
    send<number>('POST', `${L}/forwarders/by-customer/${customerId}`, body),
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
    /** 不传状态时含已撤回（「全部」页签） */
    all?: boolean;
  }) => send<PageResult<Outbound>>('POST', `${L}/outbounds/page`, q),
  counts: () =>
    get<{ pending: number; packed: number }>(`${L}/outbounds/counts`),
  detail: (id: number) => get<Outbound>(`${L}/outbounds/${id}`),
  pack: (
    id: number,
    boxes: BoxInput[],
    photos?: { attachmentIds: number[]; attachmentModels: AttachmentModels[] },
  ) =>
    send<Outbound>('PUT', `${L}/outbounds/${id}/pack`, {
      boxes: toBoxes(boxes, 'outboundItemId'),
      ...photos,
    }),
  mergePack: (
    outboundIds: number[],
    boxes: BoxInput[],
    photos?: { attachmentIds: number[]; attachmentModels: AttachmentModels[] },
  ) =>
    send<Outbound[]>('POST', `${L}/outbounds/merge-pack`, {
      outboundIds,
      boxes: toBoxes(boxes, 'outboundItemId'),
      ...photos,
    }),
  split: (id: number, reason: string) =>
    send<Outbound>('POST', `${L}/outbounds/${id}/split`, { reason }),
  handOver: (body: {
    outboundIds: number[];
    carrier: string;
    trackingNo?: string;
    sentDate: string;
    freight: number;
  }) => send<Outbound[]>('POST', `${L}/outbounds/hand-over`, body),
  undoHandOver: (id: number, reason: string) =>
    send<Outbound>('POST', `${L}/outbounds/${id}/undo-hand-over`, { reason }),
  remark: (id: number, remark: string) =>
    send<Outbound>('POST', `${L}/outbounds/${id}/remark`, { remark }),
  attach: (id: number, ids: number[]) =>
    send<Outbound>('POST', `${L}/outbounds/${id}/attachments`, { ids }),
  removeAttachment: (id: number, attachmentId: number) =>
    send<Outbound>(
      'DELETE',
      `${L}/outbounds/${id}/attachments/${attachmentId}`,
    ),
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
  remark: (id: number, remark: string) =>
    send<Logistics>('POST', `${SH}/${id}/remark`, { remark }),
  attach: (id: number, ids: number[]) =>
    send<Logistics>('POST', `${SH}/${id}/attachments`, { ids }),
  removeAttachment: (id: number, attachmentId: number) =>
    send<Logistics>('DELETE', `${SH}/${id}/attachments/${attachmentId}`),
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
      /** 交客户的货代时可不填（送货运费为 0） */
      freight?: number;
      attachmentIds: number[];
      /** 交给谁：true-客户的货代；只在登记出运时传 */
      customerForwarder?: boolean;
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
