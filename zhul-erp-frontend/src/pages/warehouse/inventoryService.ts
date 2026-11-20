import { request } from '@umijs/max';
import type { Attachment, Audit, DocLog, PageResult } from './service';

/** 库存（openspec inventory-foundation）：库存查询、流水、台账、仓库档案、其他出库的接口 */

// ---------------------------------------------------------------- 常量

export const WH_OWN = 1;
export const WH_FORWARDER = 2;
export const GOOD = 1;
export const DEFECTIVE = 2;

/** 操作类型：入库 / 出库 / 调入 / 调出 / 转入 / 转出 / 预占 / 释放 */
export const OP_OPTIONS = [
  { value: 1, label: '入库' },
  { value: 2, label: '出库' },
  { value: 3, label: '调入' },
  { value: 4, label: '调出' },
  { value: 5, label: '转入' },
  { value: 6, label: '转出' },
  { value: 7, label: '预占' },
  { value: 8, label: '释放' },
];

export const BIZ_OPTIONS = [
  { value: 'PURCHASE_IN', label: '采购入库' },
  { value: 'PURCHASE_IN_REVERSE', label: '冲销采购入库' },
  { value: 'DISCOUNT_ACCEPT', label: '折价接收' },
  { value: 'DISCOUNT_ACCEPT_UNDO', label: '撤销折价接收' },
  { value: 'HANDOVER', label: '交货代' },
  { value: 'HANDOVER_UNDO', label: '撤销交货代' },
  { value: 'SALES_OUT', label: '销售出库' },
  { value: 'SALES_OUT_UNDO', label: '撤销销售出库' },
  { value: 'RETURN_IN', label: '退货入库' },
  { value: 'RETURN_IN_UNDO', label: '撤回退货入库' },
  { value: 'PURCHASE_RETURN_OUT', label: '采购退货出库' },
  { value: 'OTHER_OUT', label: '其他出库' },
  { value: 'OTHER_OUT_VOID', label: '作废其他出库' },
  { value: 'SYSTEM_IN', label: '系统外入库' },
  { value: 'MERGE', label: '商品合并' },
  { value: 'OPENING', label: '期初' },
];

/** 其他出库类型：报废、转样品只能从公司仓库；货代丢失、货代损坏只能从货代仓 */
export const OO_TYPES = [
  { value: 1, label: '报废', hint: '无法销售，直接报废', own: true },
  { value: 2, label: '转样品', hint: '给业务员或客户做样品', own: true },
  { value: 3, label: '货代丢失', hint: '货代仓盘点少了', own: false },
  { value: 4, label: '货代损坏', hint: '货代仓里的货损坏', own: false },
];
export const OO_TYPE_TONE: Record<number, 'red' | 'accent' | 'orange'> = {
  1: 'red',
  2: 'accent',
  3: 'orange',
  4: 'orange',
};
export const ooPath = (id: number) => `/warehouse/other-outbounds/${id}`;
export const OO_DONE = 1;
export const OO_VOID = 2;

/** 单据类型 → 详情页地址（流水的来源单号） */
export const docHref = (docType?: string, docId?: number) => {
  if (!docId) return undefined;
  const base: Record<string, string> = {
    GR: '/warehouse/receipts',
    OB: '/warehouse/outbounds',
    RR: '/warehouse/returns',
    PR: '/purchase/returns',
    OO: '/warehouse/other-outbounds',
    SH: '/logistics/shipments',
    SO: '/sales/orders',
  };
  return docType && base[docType] ? `${base[docType]}/${docId}` : undefined;
};

/** 批次来源 → 来源单据详情页（采购入库单、退货入库单） */
export const batchHref = (sourceType?: number, sourceId?: number) =>
  !sourceId
    ? undefined
    : sourceType === 1
      ? `/warehouse/receipts/${sourceId}`
      : sourceType === 2
        ? `/warehouse/returns/${sourceId}`
        : undefined;

// ---------------------------------------------------------------- 类型

export interface StockRow {
  productId: number;
  model: string;
  brand?: string;
  category?: string;
  warehouseId: number;
  warehouseName: string;
  warehouseType: number;
  good: number;
  defective: number;
  orderReserved: number;
  returnReserved: number;
  available: number;
  /** 在途：只有公司仓库有 */
  inTransit?: number;
  amountCny: number;
  lastMoveTime?: string;
}

export interface StockSummary {
  amountCny: number;
  productCount: number;
  warehouseCount: number;
  available: number;
  defective: number;
  returnPending: number;
}

export interface StockBatch {
  batchId: number;
  batchNo: string;
  sourceType: number;
  sourceTypeName?: string;
  sourceId?: number;
  inDate?: string;
  grade?: string;
  stockStatus: number;
  stockStatusName: string;
  onHand: number;
  reserved: number;
  free: number;
  unitPriceCny: number;
  amountCny: number;
  poId?: number;
  poNo?: string;
  supplierId?: number;
  supplierName?: string;
  reservations: {
    targetType: number;
    targetId: number;
    docNo: string;
    quantity: number;
  }[];
}

export interface StockQuery {
  keyword?: string;
  warehouseId?: number;
  /** own：公司仓库；forwarders：全部货代仓 */
  warehouseScope?: 'own' | 'forwarders';
  onlyAvailable?: boolean;
  onlyDefective?: boolean;
  page: number;
  pageSize: number;
}

export interface InvMove {
  id: number;
  moveTime: string;
  warehouseId: number;
  warehouseName: string;
  productId: number;
  model: string;
  brand?: string;
  category?: string;
  batchId: number;
  batchNo: string;
  stockStatus: number;
  stockStatusName: string;
  opType: number;
  opName: string;
  quantity: number;
  unitPriceCny: number;
  amountCny: number;
  bizType: string;
  bizName: string;
  bizLabel?: string;
  docType?: string;
  docId?: number;
  docNo?: string;
  targetType?: number;
  targetId?: number;
  targetNo?: string;
  reversal: boolean;
  operatorName?: string;
}

export interface MoveQuery {
  dateFrom?: string;
  dateTo?: string;
  warehouseId?: number;
  productId?: number;
  opType?: number;
  bizType?: string;
  keyword?: string;
  includeReservation?: boolean;
  page: number;
  pageSize: number;
}

export interface LedgerRow {
  productId: number;
  model: string;
  brand?: string;
  category?: string;
  openingQty: number;
  openingAmount: number;
  inQty: number;
  inAmount: number;
  outQty: number;
  outAmount: number;
  closingQty: number;
  closingAmount: number;
}

export interface LedgerQuery {
  month: string;
  warehouseId?: number;
  keyword?: string;
  page: number;
  pageSize: number;
}

export interface InvWarehouse extends Audit {
  id: number;
  code: string;
  name: string;
  type: number;
  typeName: string;
  forwarderId?: number;
  onHand: number;
  status: number;
  statusName: string;
}

export interface OtherOutboundItem {
  id: number;
  productId: number;
  model: string;
  brand?: string;
  category?: string;
  batchId: number;
  batchNo: string;
  stockStatus: number;
  stockStatusName: string;
  quantity: number;
  unitPriceCny: number;
  amountCny: number;
}

export interface OtherOutbound extends Audit {
  id: number;
  outboundNo: string;
  outboundType: number;
  outboundTypeName: string;
  warehouseId: number;
  warehouseName: string;
  outboundDate: string;
  reason: string;
  status: number;
  statusName: string;
  amountCny: number;
  sourceDocType?: string;
  sourceDocId?: number;
  sourceDocNo?: string;
  voidable: boolean;
  voidReason?: string;
  remark?: string;
  firstModel?: string;
  firstBrand?: string;
  firstCategory?: string;
  modelCount: number;
  totalQuantity: number;
  items?: OtherOutboundItem[];
  attachments?: Attachment[];
  moves?: InvMove[];
  logs?: DocLog[];
}

export interface OtherOutboundQuery {
  keyword?: string;
  outboundType?: number;
  warehouseId?: number;
  status?: number;
  page: number;
  pageSize: number;
}

export interface PickLine {
  batchId: number;
  stockStatus: number;
  quantity: number;
}

// ---------------------------------------------------------------- 请求

const quiet = { skipErrorHandler: true } as const;
const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(method: string, url: string, data?: unknown) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);

const WH = '/api/v1/warehouse';
const OO = '/api/v1/warehouse/other-outbounds';

export const stockApi = {
  page: (q: StockQuery) =>
    send<PageResult<StockRow>>('POST', `${WH}/stocks/page`, q),
  summary: (q: Omit<StockQuery, 'page' | 'pageSize'>) =>
    send<StockSummary>('POST', `${WH}/stocks/summary`, q),
  batches: (warehouseId: number, productId: number) =>
    get<StockBatch[]>(`${WH}/stocks/batches`, { warehouseId, productId }),
  moves: (q: MoveQuery) =>
    send<PageResult<InvMove>>('POST', `${WH}/moves/page`, q),
  ledger: (q: LedgerQuery) =>
    send<PageResult<LedgerRow>>('POST', `${WH}/ledger/page`, q),
  /** 台账导出 Excel：出错时接口返回 JSON 提示 */
  exportLedger: async (q: Omit<LedgerQuery, 'page' | 'pageSize'>) => {
    const res = (await request<Blob>(`${WH}/ledger/export`, {
      method: 'POST',
      data: q,
      responseType: 'blob',
      getResponse: true,
      ...quiet,
    })) as unknown as {
      data: Blob;
      headers: Record<string, string | undefined>;
    };
    if (res.data.type.includes('json')) {
      const body = JSON.parse(await res.data.text()) as { message?: string };
      throw new Error(body.message || '导出失败，请稍后重试');
    }
    const disposition = res.headers?.['content-disposition'] ?? '';
    const match = /filename\*=UTF-8''([^;]+)/.exec(disposition);
    const href = window.URL.createObjectURL(res.data);
    const a = document.createElement('a');
    a.href = href;
    a.download = match
      ? decodeURIComponent(match[1])
      : `库存台账-${q.month}.xlsx`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.setTimeout(() => window.URL.revokeObjectURL(href), 1000);
  },
  /** 库存退回：生成采购退货单（来源「库存退回」） */
  returnToSupplier: (body: {
    supplierId: number;
    poId?: number;
    remark: string;
    lines: PickLine[];
  }) =>
    send<{ id: number; returnNo: string }>(
      'POST',
      '/api/v1/purchase/returns/from-stock',
      body,
    ),
};

export const warehouseApi = {
  list: () => get<InvWarehouse[]>(`${WH}/warehouses`),
  rename: (id: number, name: string) =>
    send<void>('PUT', `${WH}/warehouses/${id}`, { name }),
  mode: () => get<{ mode: string }>(`${WH}/warehouses/mode`),
  setMode: (mode: string) =>
    send<{ mode: string }>('PUT', `${WH}/warehouses/mode`, { mode }),
};

export const otherOutboundApi = {
  page: (q: OtherOutboundQuery) =>
    send<PageResult<OtherOutbound>>('POST', `${OO}/page`, q),
  detail: (id: number) => get<OtherOutbound>(`${OO}/${id}`),
  create: (body: {
    outboundType: number;
    warehouseId: number;
    outboundDate: string;
    reason: string;
    lines: PickLine[];
  }) => send<OtherOutbound>('POST', OO, body),
  void: (id: number, reason: string) =>
    send<OtherOutbound>('POST', `${OO}/${id}/void`, { reason }),
  remark: (id: number, remark: string) =>
    send<OtherOutbound>('PUT', `${OO}/${id}/remark`, { remark }),
  attach: (id: number, attachmentIds: number[]) =>
    send<OtherOutbound>('POST', `${OO}/${id}/attachments`, {
      ids: attachmentIds,
    }),
  removeAttachment: (id: number, attachmentId: number) =>
    send<OtherOutbound>('DELETE', `${OO}/${id}/attachments/${attachmentId}`),
};
