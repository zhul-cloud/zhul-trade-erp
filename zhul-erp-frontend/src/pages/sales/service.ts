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
  /** 1-草稿、2-已发送、3-已转订单、4-已作废 */
  status: number;
  statusName: string;
  /** 1-未付款、2-待到账、3-部分到账、4-已到账 */
  receiptStatus: number;
  receiptStatusName: string;
  receivedAmount: number;
  feeDiffAmount: number;
  remainingAmount: number;
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
  ownerId?: number;
  createdFrom?: string;
  createdTo?: string;
  page: number;
  pageSize: number;
}

export interface ReceiptDeskRow {
  piId: number;
  piNo: string;
  piStatus: number;
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
  piId: number;
  piNo?: string;
  piVersionNo: number;
  ownerId: number;
  ownerName?: string;
  cancelReason?: string;
  createTime: string;
}

export interface Order {
  id: number;
  soNo: string;
  piId: number;
  piNo?: string;
  piVersionNo: number;
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
  items: PiItem[];
  fees: PiFee[];
  receipts: Receipt[];
  createTime: string;
}

export interface OrderQuery {
  keyword?: string;
  status?: number;
  receiptStatus?: number;
  ownerId?: number;
  createdFrom?: string;
  createdTo?: string;
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
  remove: (id: number) => send<void>('DELETE', `${PI}/${id}`),
  exportFile: (
    id: number,
    format: 'xlsx' | 'pdf' | 'jpg',
    fallback: string,
    version?: number,
  ) => download(`${PI}/${id}/export`, { format, version }, fallback),
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
  ) => {
    const form = new FormData();
    for (const f of files) form.append('files', f);
    form.append('amount', String(amount));
    form.append('paidDate', paidDate);
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
  confirmReceipt: (
    id: number,
    body: {
      amount: number;
      receiptDate: string;
      bankAccountId: number;
      slipId?: number;
      feeDiff?: boolean;
      note?: string;
    },
  ) => send<Pi>('POST', `${PI}/${id}/receipts`, body),
  voidReceipt: (id: number, receiptId: number, reason: string) =>
    send<Pi>('POST', `${PI}/${id}/receipts/${receiptId}/void`, { reason }),
  receiptSettings: () =>
    get<{ feeTolerance: number }>(`${PI}/receipt-settings`),
  convert: (id: number) => send<Order>('POST', `${PI}/${id}/convert`),
  /** 财务管理 → 到账登记 */
  receiptDesk: (q: ReceiptDeskQuery) =>
    send<{ total: number; records: ReceiptDeskRow[] }>(
      'POST',
      `${PI}/receipt-desk`,
      q,
    ),
};

export const orderApi = {
  page: (q: OrderQuery) =>
    send<{ total: number; records: OrderListItem[] }>('POST', `${SO}/page`, q),
  detail: (id: number) => get<Order>(`${SO}/${id}`),
  cancel: (id: number, reason: string) =>
    send<Order>('POST', `${SO}/${id}/cancel`, { reason }),
};

export const chainApi = {
  get: (type: 'inquiry' | 'quotation' | 'pi' | 'order', id: number) =>
    get<Chain>('/api/v1/sales/chain', { type, id }),
};

export const bankOptionsApi = {
  options: () => get<BankOption[]>('/api/v1/system/bank-accounts/options'),
};
