import { request } from '@umijs/max';

export { readBizError } from '@/pages/crm/opportunity/service';

// ---------------------------------------------------------------- 类型

export interface Party {
  partyId?: number | null;
  name: string;
  country?: string;
  state?: string;
  city?: string;
  postcode?: string;
  address?: string;
  taxId?: string;
  contact?: string;
  phone?: string;
  email?: string;
}

export interface BankSnapshot {
  id: number;
  currencyCode: string;
  bankName: string;
  accountName: string;
  accountNo: string;
  swiftCode?: string;
  country?: string;
  bankAddress?: string;
  bankCode?: string;
  branchCode?: string;
}

export interface PiItem {
  id: number;
  lineNo: number;
  quotationId: number;
  quotationNo?: string;
  quotationItemId: number;
  customerInquiryId: number;
  inquiryCode?: string;
  inquiryItemId: number;
  model: string;
  brand: string;
  category?: string;
  description?: string;
  itemCondition: number;
  conditionName?: string;
  leadTime: number;
  leadTimeName?: string;
  warranty: string;
  quantity: number;
  quotedPrice?: number;
  unitPrice: number;
  unitPriceCny: number;
  amount: number;
  amountCny: number;
  costPrice?: number | null;
  floorMargin?: number | null;
  marginRate?: number | null;
  belowFloor: boolean;
  netProfit?: number | null;
  netProfitCny?: number | null;
  hsCode?: string;
  originCountry?: string;
  remark?: string;
}

export interface PiFee {
  feeName: string;
  amount: number;
  amountCny?: number;
  remark?: string;
}

export interface PiVersion {
  versionNo: number;
  /** 1-编辑中、2-已发送、3-已放弃 */
  status: number;
  buyer?: Party | null;
  consignee?: Party | null;
  deliveryTime: string;
  paymentTerm: string;
  incoterm: string;
  incotermPlace: string;
  portOfShipment: string;
  remark: string;
  /** 有效期至 */
  validUntil?: string;
  bankAccount?: BankSnapshot | null;
  /** 0-无、1-按百分比、2-按金额 */
  discountType: number;
  discountValue: number;
  discountAmount: number;
  itemAmount: number;
  feeAmount: number;
  totalAmount: number;
  totalAmountCny: number;
  netProfit?: number | null;
  netProfitCny?: number | null;
  marginRate?: number | null;
  sentAt?: string;
  items: PiItem[];
  fees: PiFee[];
}

export interface SlipFile {
  fileKey: string;
  fileName: string;
}

export interface Receipt {
  id: number;
  /** 1-水单、2-到账 */
  kind: number;
  amount: number;
  amountCny: number;
  feeDiff: number;
  paymentMethod?: string;
  paymentMethodName?: string;
  /** 1-线下、2-线上 */
  channel?: number;
  platformOrderNo?: string;
  platformFee?: number;
  /** 实收（原币）、实收人民币与汇率（来源 1-系统汇率、2-实际入账），仅到账 */
  netAmount?: number;
  netAmountCny?: number;
  exchangeRate?: number;
  rateSource?: number;
  payer?: string;
  claimedByName?: string;
  claimedAt?: string;
  /** 当前用户能否作废 */
  voidable?: boolean;
  receiptDate?: string;
  bankAccountId?: number;
  bankAccountName?: string;
  slipId?: number;
  files: SlipFile[];
  note?: string;
  /** 1-有效、2-已作废 */
  status: number;
  voidReason?: string;
  operatorName?: string;
  matched: boolean;
  createTime: string;
}

/** 收款管理里的一笔到账（未认领到账、可认领列表、收款记录） */
export interface ReceiptRow {
  id: number;
  piId?: number | null;
  piNo?: string;
  /** 手动创建的订单的收款 */
  soId?: number;
  soNo?: string;
  customerName?: string;
  currencyCode: string;
  amount: number;
  platformFee: number;
  feeDiff: number;
  netAmount: number;
  exchangeRate: number;
  rateSource: number;
  netAmountCny: number;
  paymentMethod: string;
  paymentMethodName: string;
  channel: number;
  platformOrderNo?: string;
  payer?: string;
  receiptDate?: string;
  bankAccountName?: string;
  note?: string;
  status: number;
  voidReason?: string;
  operatorName?: string;
  claimedByName?: string;
  claimedAt?: string;
  createTime: string;
}

export interface Pi {
  id: number;
  piNo: string;
  customerId: number;
  customerName: string;
  customerCountry?: string;
  ownerId: number;
  ownerName?: string;
  currencyCode: string;
  exchangeRate: number;
  rateTime?: string;
  /** 1-草稿、2-已发送、3-已转订单、4-已作废、5-已关闭 */
  status: number;
  statusName: string;
  /** 1-未付款、2-待到账、3-部分到账、4-已到账 */
  receiptStatus: number;
  receiptStatusName: string;
  /** 有效期至（当前有效版本）；已发送、未付款且过了有效期为已过期 */
  validUntil?: string;
  expired?: boolean;
  expiredDays?: number | null;
  /** 已关闭时的原因、说明、关闭人与时间 */
  closeReason?: string;
  closeReasonName?: string;
  closeNote?: string;
  closedAt?: string;
  closedByName?: string;
  /** 关闭接口返回的提示 */
  notices?: string[];
  receivedAmount: number;
  feeDiffAmount: number;
  remainingAmount: number;
  /** 有效到账的平台手续费合计与实收人民币合计 */
  platformFeeAmount?: number;
  netAmountCny?: number;
  currentVersionNo: number;
  editingVersionNo?: number | null;
  editable: boolean;
  version: PiVersion;
  versions: {
    versionNo: number;
    status: number;
    totalAmount: number;
    sentAt?: string;
    createTime: string;
  }[];
  sendLogs: {
    versionNo: number;
    channel: number;
    channelName: string;
    sentByName?: string;
    sentAt: string;
  }[];
  receipts: Receipt[];
  order?: { id: number; soNo: string; status: number; createTime: string };
  quotations: { id: number; quotationNo: string; status: number }[];
  hasBankAccount: boolean;
  createTime: string;
}

export interface PiListItem {
  id: number;
  piNo: string;
  customerId: number;
  customerName: string;
  /** 客户国家 */
  customerCountry?: string;
  /** 1-新客户、2-老客户 */
  customerType?: number;
  /** 全部型号数量之和 */
  totalQuantity?: number;
  itemCount: number;
  currencyCode: string;
  totalAmount: number;
  receiptStatus: number;
  receiptStatusName: string;
  status: number;
  statusName: string;
  currentVersionNo: number;
  revising: boolean;
  quotationNos: string[];
  orderId?: number;
  soNo?: string;
  ownerId: number;
  ownerName?: string;
  createTime: string;
  sentAt?: string;
  validUntil?: string;
  expired?: boolean;
  expiredDays?: number | null;
  closeReasonName?: string | null;
}

export interface OverduePis {
  count: number;
  totals: { currencyCode: string; amount: number }[];
  top: PiListItem[];
}

export interface PiStats {
  draftCount: number;
  slipOnlyCount: number;
  monthReceivedUsd: number;
  monthReceivedCny: number;
  monthOrderCount: number;
  monthOrderUnpaid: number;
}

export interface PiQuery {
  keyword?: string;
  status?: number;
  receiptStatus?: number;
  /** 只看已过期未收款 */
  expiredUnpaid?: boolean;
  ownerId?: number;
  createdFrom?: string;
  createdTo?: string;
  page: number;
  pageSize: number;
}

export interface ReceiptDeskRow {
  /** 手动创建的订单没有 PI，收款记在订单上（orderId） */
  piId?: number;
  piNo?: string;
  piStatus?: number;
  customerId: number;
  customerName: string;
  ownerId: number;
  ownerName?: string;
  currencyCode: string;
  totalAmount: number;
  receivedAmount: number;
  feeDiffAmount: number;
  remainingAmount: number;
  receiptStatus: number;
  receiptStatusName: string;
  orderId?: number;
  soNo?: string;
  pendingSlips: Receipt[];
  earliestSlipDate?: string;
}

export interface ReceiptDeskQuery {
  keyword?: string;
  receiptStatus?: number;
  all?: boolean;
  page: number;
  pageSize: number;
}

export interface CandidateLine {
  quotationItemId: number;
  lineNo: number;
  model: string;
  brand: string;
  category?: string;
  quantity: number;
  unitPrice: number;
  amount: number;
  won: boolean;
  inPiNo?: string;
}

export interface CandidateQuotation {
  quotationId: number;
  quotationNo: string;
  status: number;
  statusName: string;
  currencyCode: string;
  totalAmount: number;
  sentAt?: string;
  items: CandidateLine[];
  fees: PiFee[];
}

export interface CandidateCustomer {
  customerId: number;
  customerName: string;
  country?: string;
  quotationCount: number;
}

export interface SavePi {
  buyer: Party | null;
  consignee: Party | null;
  saveBuyerToCustomer?: boolean;
  saveConsigneeToCustomer?: boolean;
  deliveryTime: string;
  paymentTerm: string;
  incoterm: string;
  incotermPlace: string;
  portOfShipment: string;
  remark: string;
  validUntil?: string;
  bankAccountId?: number | null;
  discountType: number;
  discountValue: number | null;
  items: {
    id: number;
    description?: string;
    leadTime?: number;
    warranty?: string;
    quantity: number;
    unitPrice: number | null;
    hsCode?: string;
    originCountry?: string;
    remark?: string;
  }[];
  fees: { feeName: string; amount: number; remark?: string }[];
}

export interface PreviewResult {
  pages?: string[];
  updatedAt?: string;
  superseded?: boolean;
  unavailable?: boolean;
  message?: string;
}

export interface OrderListItem {
  id: number;
  soNo: string;
  /** 1-PI 转成、2-手动创建 */
  source: number;
  salesDate?: string;
  /** 1-现货、2-期货 */
  stockType: number;
  /** 订单状态：进度码（含 COMPLETED），已取消为 CANCELLED */
  progressCode: string;
  progressName: string;
  purchaserNames: string[];
  unassignedCount: number;
  customerId: number;
  customerName: string;
  /** 客户国家 */
  customerCountry?: string;
  /** 1-新客户、2-老客户 */
  customerType?: number;
  /** 全部型号数量之和 */
  totalQuantity?: number;
  itemCount: number;
  currencyCode: string;
  totalAmount: number;
  receiptStatus?: number;
  receiptStatusName?: string;
  receivedAmount?: number;
  /** 1-有效、2-已取消 */
  status: number;
  statusName: string;
  piId?: number;
  piNo?: string;
  piVersionNo?: number;
  ownerId: number;
  ownerName?: string;
  cancelReason?: string;
  createTime: string;
}

export interface OrderItem extends PiItem {
  /** 1-现货、2-期货 */
  stockType: number;
  progressCode: string;
  progressName: string;
  purchaserId?: number | null;
  purchaserName?: string | null;
}

export interface OrderStep {
  code: string;
  name: string;
  enabled: boolean;
}

export interface Order {
  id: number;
  soNo: string;
  /** 1-PI 转成、2-手动创建 */
  source: number;
  salesDate?: string;
  /** 1-新客户、2-老客户 */
  customerType?: number;
  /** 1-现货、2-期货 */
  stockType: number;
  progressCode: string;
  progressName: string;
  completedAt?: string;
  /** 有效且未完成：可以改跟单信息 */
  trackable: boolean;
  /** 所有型号到达最后一步：可以确认客户收货 */
  completable: boolean;
  steps: OrderStep[];
  purchasers: {
    userId?: number | null;
    name?: string | null;
    itemCount: number;
  }[];
  methodTotals: {
    paymentMethod: string;
    paymentMethodName: string;
    channel: number;
    currencyCode: string;
    amount: number;
  }[];
  margin: {
    salesAmountCny: number;
    receivedCny: number;
    costCny: number;
    profitCny: number;
    estimated: boolean;
    missingCostCount: number;
  };
  piId?: number;
  piNo?: string;
  piVersionNo?: number;
  piStatus?: number;
  customerId: number;
  customerName: string;
  ownerId: number;
  ownerName?: string;
  currencyCode: string;
  exchangeRate: number;
  buyer?: Party | null;
  consignee?: Party | null;
  deliveryTime: string;
  paymentTerm: string;
  incoterm: string;
  incotermPlace: string;
  portOfShipment: string;
  remark: string;
  itemAmount: number;
  feeAmount: number;
  discountAmount: number;
  totalAmount: number;
  totalAmountCny: number;
  netProfit?: number | null;
  netProfitCny?: number | null;
  marginRate?: number | null;
  status: number;
  statusName: string;
  cancelReason?: string;
  cancelledByName?: string;
  cancelledAt?: string;
  receiptStatus?: number;
  receiptStatusName?: string;
  receivedAmount?: number;
  feeDiffAmount?: number;
  remainingAmount?: number;
  items: OrderItem[];
  fees: PiFee[];
  receipts: Receipt[];
  createTime: string;
}

export interface OrderStats {
  monthCount: number;
  inProgressCount: number;
  progressCounts: { code: string; name: string; count: number }[];
  monthNetCny: number;
  monthOnlineCny: number;
  monthOfflineCny: number;
  unassignedCount: number;
}

export interface OrderQuery {
  keyword?: string;
  status?: number;
  receiptStatus?: number;
  ownerId?: number;
  createdFrom?: string;
  createdTo?: string;
  salesFrom?: string;
  salesTo?: string;
  progressCode?: string;
  stockType?: number;
  customerType?: number;
  currencyCode?: string;
  purchaserId?: number;
  page: number;
  pageSize: number;
}

export interface ChainNode {
  id: number;
  no: string;
  status: number;
  statusName: string;
  current: boolean;
}

export interface Chain {
  inquiries: ChainNode[];
  quotations: ChainNode[];
  pis: ChainNode[];
  orders: ChainNode[];
}

/** 客户的发票抬头 / 收货人选项 */
export interface PartyOption extends Party {
  /** 1-收货人、3-发票抬头 */
  partyType: number;
  isDefault: boolean;
  /** 取自客户注册信息（不是单证主体） */
  registration?: boolean;
}

export interface BankOption {
  id: number;
  currencyCode: string;
  bankName: string;
  accountName: string;
  accountNo?: string;
  accountNoMasked: string;
  swiftCode?: string;
  isDefault: boolean;
  enabled: boolean;
}

// ---------------------------------------------------------------- 请求

const quiet = { skipErrorHandler: true } as const;
const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(method: string, url: string, data?: unknown) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);

export interface ConfirmReceiptBody {
  amount: number;
  receiptDate: string;
  bankAccountId: number;
  slipId?: number;
  feeDiff?: boolean;
  paymentMethod?: string;
  actualAmountCny?: number;
  note?: string;
}

export interface PlatformReceiptBody {
  paymentMethod: string;
  platformOrderNo: string;
  amount: number;
  platformFee?: number;
  receiptDate: string;
  actualAmountCny?: number;
  note?: string;
}

const PI = '/api/v1/sales/pis';
const SO = '/api/v1/sales/orders';

/** 下载接口返回的文件：从响应头取文件名后触发浏览器下载；出错时接口返回 JSON 提示 */
const download = async (url: string, params: object, fallback: string) => {
  const res = (await request<Blob>(url, {
    method: 'GET',
    params,
    responseType: 'blob',
    getResponse: true,
    ...quiet,
  })) as unknown as { data: Blob; headers: Record<string, string | undefined> };
  if (res.data.type.includes('json')) {
    const body = JSON.parse(await res.data.text()) as { message?: string };
    throw new Error(body.message || '下载失败，请稍后重试');
  }
  const disposition = res.headers?.['content-disposition'] ?? '';
  const match = /filename\*=UTF-8''([^;]+)/.exec(disposition);
  const name = match ? decodeURIComponent(match[1]) : fallback;
  const href = window.URL.createObjectURL(res.data);
  const a = document.createElement('a');
  a.href = href;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.setTimeout(() => window.URL.revokeObjectURL(href), 1000);
};

/** 在新窗口打开私有附件（水单） */
const openFile = async (url: string) => {
  const blob = await request<Blob>(url, {
    method: 'GET',
    responseType: 'blob',
    ...quiet,
  });
  if (blob.type.includes('json')) {
    const body = JSON.parse(await blob.text()) as { message?: string };
    throw new Error(body.message || '附件打开失败');
  }
  const href = window.URL.createObjectURL(blob);
  window.open(href, '_blank', 'noopener');
  window.setTimeout(() => window.URL.revokeObjectURL(href), 60_000);
};

export const piApi = {
  page: (q: PiQuery) =>
    send<{ total: number; records: PiListItem[] }>('POST', `${PI}/page`, q),
  stats: () => get<PiStats>(`${PI}/stats`),
  byQuotation: (quotationId: number) =>
    get<PiListItem[]>(`${PI}/by-quotation/${quotationId}`),
  candidateCustomers: (keyword?: string) =>
    get<CandidateCustomer[]>(`${PI}/candidates/customers`, { keyword }),
  candidateQuotations: (customerId: number) =>
    get<CandidateQuotation[]>(`${PI}/candidates/quotations`, { customerId }),
  candidateQuotation: (quotationId: number) =>
    get<CandidateQuotation>(`${PI}/candidates/quotations/${quotationId}`),
  create: (items: { quotationItemId: number; quantity?: number }[]) =>
    send<Pi>('POST', PI, { items }),
  /** customerId 不传时为 PI 的客户；传入时为数据范围内的其他客户（如母公司付款） */
  parties: (id: number, customerId?: number) =>
    get<PartyOption[]>(`${PI}/${id}/parties`, customerId ? { customerId } : {}),
  detail: (id: number, version?: number) =>
    get<Pi>(`${PI}/${id}`, version ? { version } : undefined),
  save: (id: number, body: SavePi) => send<Pi>('PUT', `${PI}/${id}`, body),
  addItems: (id: number, quotationItemIds: number[]) =>
    send<Pi>('POST', `${PI}/${id}/items`, {
      items: quotationItemIds.map((quotationItemId) => ({ quotationItemId })),
    }),
  markSent: (id: number, channel: number) =>
    send<Pi>('POST', `${PI}/${id}/sent`, { channel }),
  revise: (id: number) => send<Pi>('POST', `${PI}/${id}/revise`),
  abandon: (id: number) => send<Pi>('POST', `${PI}/${id}/abandon`),
  voidPi: (id: number) => send<Pi>('POST', `${PI}/${id}/void`),
  close: (
    id: number,
    body: { reason: string; note?: string; markQuotationLost: boolean },
  ) => send<Pi>('POST', `${PI}/${id}/close`, body),
  reopen: (id: number, validUntil?: string) =>
    send<Pi>('POST', `${PI}/${id}/reopen`, validUntil ? { validUntil } : {}),
  overdue: () => get<OverduePis>(`${PI}/overdue`),
  remove: (id: number) => send<void>('DELETE', `${PI}/${id}`),
  exportFile: (
    id: number,
    format: 'xlsx' | 'pdf' | 'jpg',
    fallback: string,
    version?: number,
  ) => download(`${PI}/${id}/export`, { format, version }, fallback),
  /** 议价测算表：测算汇率、试算的整单折扣（1-百分比、2-金额）；不传折扣时用版本当前折扣 */
  bargainExport: (
    id: number,
    params: {
      version?: number;
      rate: number;
      discountType?: number;
      discountValue?: number;
    },
    fallback: string,
  ) => download(`${PI}/${id}/bargain-export`, params, fallback),
  preview: (id: number, content: SavePi, signal?: AbortSignal) =>
    request<{ data: PreviewResult }>(`${PI}/${id}/preview`, {
      method: 'POST',
      data: content,
      signal,
      ...quiet,
    }).then((r) => r.data),
  converterStatus: () =>
    get<{ available: boolean }>('/api/v1/quotations/converter-status'),
  // 收款
  uploadSlip: (
    id: number,
    files: File[],
    amount: number,
    paidDate: string,
    note?: string,
    paymentMethod?: string,
  ) => {
    const form = new FormData();
    for (const f of files) form.append('files', f);
    form.append('amount', String(amount));
    form.append('paidDate', paidDate);
    if (paymentMethod) form.append('paymentMethod', paymentMethod);
    if (note) form.append('note', note);
    return request<{ data: Pi }>(`${PI}/${id}/slips`, {
      method: 'POST',
      data: form,
      requestType: 'form',
      ...quiet,
    }).then((r) => r.data);
  },
  deleteSlip: (id: number, slipId: number) =>
    send<Pi>('DELETE', `${PI}/${id}/slips/${slipId}`),
  openSlipFile: (id: number, slipId: number, index: number) =>
    openFile(`${PI}/${id}/slips/${slipId}/files/${index}`),
  confirmReceipt: (id: number, body: ConfirmReceiptBody) =>
    send<Pi>('POST', `${PI}/${id}/receipts`, body),
  platformReceipt: (id: number, body: PlatformReceiptBody) =>
    send<Pi>('POST', `${PI}/${id}/platform-receipts`, body),
  claimable: (id: number) =>
    get<ReceiptRow[]>(`${PI}/${id}/claimable-receipts`),
  claim: (id: number, receiptId: number, slipId?: number) =>
    send<Pi>('POST', `${PI}/${id}/claim`, { receiptId, slipId }),
  voidReceipt: (id: number, receiptId: number, reason: string) =>
    send<Pi>('POST', `${PI}/${id}/receipts/${receiptId}/void`, { reason }),
  receiptSettings: () =>
    get<{ feeTolerance: number }>(`${PI}/receipt-settings`),
  convert: (id: number, salesDate?: string) =>
    send<Order>('POST', `${PI}/${id}/convert`, { salesDate }),
  /** 财务管理 → 收款管理（待确认） */
  receiptDesk: (q: ReceiptDeskQuery) =>
    send<{ total: number; records: ReceiptDeskRow[] }>(
      'POST',
      `${PI}/receipt-desk`,
      q,
    ),
};

export interface CreateOrderLine {
  model: string;
  brand?: string;
  quantity: number;
  unitPrice: number;
  costPrice?: number | null;
  purchaserId?: number | null;
  stockType?: number;
}

export const orderApi = {
  page: (q: OrderQuery) =>
    send<{ total: number; records: OrderListItem[] }>('POST', `${SO}/page`, q),
  stats: () => get<OrderStats>(`${SO}/stats`),
  detail: (id: number) => get<Order>(`${SO}/${id}`),
  cancel: (id: number, reason: string) =>
    send<Order>('POST', `${SO}/${id}/cancel`, { reason }),
  create: (body: {
    customerId: number;
    currencyCode: string;
    salesDate: string;
    items: CreateOrderLine[];
  }) => send<Order>('POST', SO, body),
  salesDate: (id: number, salesDate: string) =>
    send<Order>('POST', `${SO}/${id}/sales-date`, { salesDate }),
  progress: (
    id: number,
    itemIds: number[],
    progressCode: string,
    note?: string,
  ) =>
    send<Order>('POST', `${SO}/${id}/progress`, {
      itemIds,
      progressCode,
      note,
    }),
  purchaser: (id: number, itemIds: number[], purchaserId: number | null) =>
    send<Order>('POST', `${SO}/${id}/purchaser`, { itemIds, purchaserId }),
  stockType: (id: number, itemIds: number[], stockType: number) =>
    send<Order>('POST', `${SO}/${id}/stock-type`, { itemIds, stockType }),
  complete: (id: number) => send<Order>('POST', `${SO}/${id}/complete`),
  // 手动创建的订单：收款直接登记在订单上
  uploadSlip: (
    id: number,
    files: File[],
    amount: number,
    paidDate: string,
    note?: string,
    paymentMethod?: string,
  ) => {
    const form = new FormData();
    for (const f of files) form.append('files', f);
    form.append('amount', String(amount));
    form.append('paidDate', paidDate);
    if (paymentMethod) form.append('paymentMethod', paymentMethod);
    if (note) form.append('note', note);
    return request<{ data: Order }>(`${SO}/${id}/slips`, {
      method: 'POST',
      data: form,
      requestType: 'form',
      ...quiet,
    }).then((r) => r.data);
  },
  deleteSlip: (id: number, slipId: number) =>
    send<Order>('DELETE', `${SO}/${id}/slips/${slipId}`),
  openSlipFile: (id: number, slipId: number, index: number) =>
    openFile(`${SO}/${id}/slips/${slipId}/files/${index}`),
  confirmReceipt: (id: number, body: ConfirmReceiptBody) =>
    send<Order>('POST', `${SO}/${id}/receipts`, body),
  platformReceipt: (id: number, body: PlatformReceiptBody) =>
    send<Order>('POST', `${SO}/${id}/platform-receipts`, body),
  claimable: (id: number) =>
    get<ReceiptRow[]>(`${SO}/${id}/claimable-receipts`),
  claim: (id: number, receiptId: number, slipId?: number) =>
    send<Order>('POST', `${SO}/${id}/claim`, { receiptId, slipId }),
  voidReceipt: (id: number, receiptId: number, reason: string) =>
    send<Order>('POST', `${SO}/${id}/receipts/${receiptId}/void`, { reason }),
};

/** 收款操作返回的详情（PI 或订单）都带收款状态 */
export type ReceiptResult = { receiptStatusName?: string };

/** 收款弹窗的操作对象：PI，或手动创建的订单（同一组接口，返回各自的详情） */
export interface ReceiptOwner<T> {
  no: string;
  currencyCode: string;
  exchangeRate: number;
  totalAmount: number;
  receivedAmount?: number;
  remainingAmount?: number;
  receipts: Receipt[];
  uploadSlip: (
    files: File[],
    amount: number,
    paidDate: string,
    note?: string,
    paymentMethod?: string,
  ) => Promise<T>;
  confirmReceipt: (body: ConfirmReceiptBody) => Promise<T>;
  platformReceipt: (body: PlatformReceiptBody) => Promise<T>;
  claimable: () => Promise<ReceiptRow[]>;
  claim: (receiptId: number, slipId?: number) => Promise<T>;
}

export const piReceiptOwner = (pi: Pi): ReceiptOwner<Pi> => ({
  no: pi.piNo,
  currencyCode: pi.currencyCode,
  exchangeRate: pi.exchangeRate,
  totalAmount: pi.version.totalAmount,
  receivedAmount: pi.receivedAmount,
  remainingAmount: pi.remainingAmount,
  receipts: pi.receipts,
  uploadSlip: (...a) => piApi.uploadSlip(pi.id, ...a),
  confirmReceipt: (body) => piApi.confirmReceipt(pi.id, body),
  platformReceipt: (body) => piApi.platformReceipt(pi.id, body),
  claimable: () => piApi.claimable(pi.id),
  claim: (receiptId, slipId) => piApi.claim(pi.id, receiptId, slipId),
});

export const orderReceiptOwner = (o: Order): ReceiptOwner<Order> => ({
  no: o.soNo,
  currencyCode: o.currencyCode,
  exchangeRate: o.exchangeRate,
  totalAmount: o.totalAmount,
  receivedAmount: o.receivedAmount,
  remainingAmount: o.remainingAmount,
  receipts: o.receipts,
  uploadSlip: (...a) => orderApi.uploadSlip(o.id, ...a),
  confirmReceipt: (body) => orderApi.confirmReceipt(o.id, body),
  platformReceipt: (body) => orderApi.platformReceipt(o.id, body),
  claimable: () => orderApi.claimable(o.id),
  claim: (receiptId, slipId) => orderApi.claim(o.id, receiptId, slipId),
});

export const chainApi = {
  get: (type: 'inquiry' | 'quotation' | 'pi' | 'order', id: number) =>
    get<Chain>('/api/v1/sales/chain', { type, id }),
};

export const bankOptionsApi = {
  options: () => get<BankOption[]>('/api/v1/system/bank-accounts/options'),
};
