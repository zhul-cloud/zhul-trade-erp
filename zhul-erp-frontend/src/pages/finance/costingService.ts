import { request } from '@umijs/max';
import {
  type CategoryLedger,
  downloadXlsx,
  type LedgerRow,
} from '@/pages/warehouse/inventoryService';
import type {
  Attachment,
  Audit,
  DocLog,
  PageResult,
} from '@/pages/warehouse/service';

/** 库存成本（openspec inventory-costing）：成本调整单、库存月结的接口 */

const quiet = { skipErrorHandler: true } as const;
const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(method: string, url: string, data?: unknown) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);

// ---------------------------------------------------------------- 成本调整

/** 触发原因 */
export const CA_REASONS = [
  { value: 1, label: '外币付款', tone: 'accent' },
  { value: 2, label: '作废付款', tone: 'gray' },
  { value: 3, label: '供应商退款', tone: 'cyan' },
  { value: 4, label: '采购订单费用', tone: 'orange' },
  { value: 5, label: '折价接收', tone: 'green' },
  { value: 6, label: '重新打开折价', tone: 'gray' },
  { value: 7, label: '每日核对', tone: 'gray' },
  { value: 8, label: '成本口径升级', tone: 'violet' },
] as const;

export const caPath = (id: number) => `/finance/cost-adjustments/${id}`;

export interface CostAdjustPart {
  id: number;
  /** 1-在库、2-已出运、3-其他出库 */
  part: number;
  partName: string;
  warehouseId?: number;
  warehouseName?: string;
  docType?: string;
  docId?: number;
  docNo?: string;
  soItemId?: number;
  soId?: number;
  soNo?: string;
  quantity: number;
  amountCny: number;
}

export interface CostAdjustBatch {
  batchId: number;
  batchNo: string;
  productId: number;
  model: string;
  brand?: string;
  category?: string;
  oldUnitCost: number;
  newUnitCost: number;
  diffGoods: number;
  diffInvoice: number;
  diffFee: number;
  diffClaim: number;
  quantity: number;
  amountCny: number;
  parts: CostAdjustPart[];
}

export interface CostAdjust extends Audit {
  id: number;
  adjustNo: string;
  adjustDate: string;
  periodMonth: string;
  reason: number;
  reasonName: string;
  sourceDocType?: string;
  sourceDocId?: number;
  sourceDocNo?: string;
  batchCount: number;
  firstModel?: string;
  stockAmount: number;
  cogsAmount: number;
  otherAmount: number;
  totalAmount: number;
  remark?: string;
  batches?: CostAdjustBatch[];
  attachments?: Attachment[];
  logs?: DocLog[];
}

export interface CostAdjustQuery {
  month?: string;
  reason?: number;
  keyword?: string;
  cogsOnly?: boolean;
  page?: number;
  pageSize?: number;
}

export interface CostAdjustSummary {
  month: string;
  stockAmount: number;
  cogsAmount: number;
  otherAmount: number;
  count: number;
  countByReason: Record<string, number>;
}

const CA = '/api/v1/finance/cost-adjustments';

export const costAdjustApi = {
  page: (q: CostAdjustQuery) =>
    send<PageResult<CostAdjust>>('POST', `${CA}/page`, q),
  summary: (month: string) =>
    get<CostAdjustSummary>(`${CA}/summary`, { month }),
  export: (q: Omit<CostAdjustQuery, 'page' | 'pageSize'>) =>
    downloadXlsx(
      `${CA}/export`,
      q,
      `成本调整${q.month ? `-${q.month}` : ''}.xlsx`,
    ),
  detail: (id: number) => get<CostAdjust>(`${CA}/${id}`),
  remark: (id: number, remark: string) =>
    send<CostAdjust>('PUT', `${CA}/${id}/remark`, { remark }),
  attach: (id: number, ids: number[]) =>
    send<CostAdjust>('POST', `${CA}/${id}/attachments`, { ids }),
  removeAttachment: (id: number, attachmentId: number) =>
    send<CostAdjust>('DELETE', `${CA}/${id}/attachments/${attachmentId}`),
};

// ---------------------------------------------------------------- 库存月结

export const PERIOD_CLOSED = 1;
export const periodPath = (month: string) => `/finance/periods/${month}`;

export interface Period {
  month: string;
  status: number;
  statusName: string;
  /** 期初 + 本月入库 − 本月出库 + 在库成本调整 = 期末（取自库存台账） */
  openingAmount: number;
  inAmount: number;
  outAmount: number;
  stockAdjustAmount: number;
  closingAmount: number;
  /** 参考，不进算式：本月销售成本、本月成本调整单合计及分项 */
  cogsAmount: number;
  adjustAmount: number;
  adjustCogsAmount?: number;
  adjustOtherAmount?: number;
  closedByName?: string;
  closedAt?: string;
  reopenedByName?: string;
  reopenedAt?: string;
  reopenReason?: string;
  closable: boolean;
  closeBlock?: string;
  reopenable: boolean;
  /** 详情才有：结账前的提醒 */
  reminders?: string[];
}

export interface PeriodCogs {
  /** 1-出运结转、2-成本调整 */
  kind: number;
  kindName: string;
  soId?: number;
  soNo: string;
  soItemId: number;
  productId: number;
  model: string;
  brand?: string;
  category?: string;
  docType: string;
  docId: number;
  docNo: string;
  bizDate: string;
  quantity: number;
  systemQty: number;
  amountGoods: number;
  amountInvoice: number;
  amountFee: number;
  amountClaim: number;
  amountCny: number;
}

const P = '/api/v1/finance/periods';

export const periodApi = {
  list: () => get<Period[]>(P),
  detail: (month: string) => get<Period>(`${P}/${month}`),
  ledger: (
    month: string,
    q: {
      warehouseId?: number;
      keyword?: string;
      page: number;
      pageSize: number;
    },
  ) => send<PageResult<LedgerRow>>('POST', `${P}/${month}/ledger`, q),
  /** 月份的类目账（全部仓库，已结账读快照） */
  categoryLedger: (month: string) =>
    get<CategoryLedger>(`${P}/${month}/category-ledger`),
  cogs: (
    month: string,
    q: { keyword?: string; page: number; pageSize: number },
  ) => get<PageResult<PeriodCogs>>(`${P}/${month}/cogs`, q),
  export: (month: string) =>
    downloadXlsx(`${P}/${month}/export`, {}, `库存月结-${month}.xlsx`),
  close: (month: string) => send<Period>('POST', `${P}/${month}/close`),
  reopen: (month: string, reason: string) =>
    send<Period>('POST', `${P}/${month}/reopen`, { reason }),
};
