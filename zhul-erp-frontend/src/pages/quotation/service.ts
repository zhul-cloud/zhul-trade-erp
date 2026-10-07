import { request } from '@umijs/max';

export { readBizError } from '@/pages/crm/opportunity/service';

// ---------------------------------------------------------------- 类型

export interface QuoteInquiry {
  inquiryId: number;
  inquiryCode: string;
  customerId: number;
  customerName: string;
  customerType: number;
  inquiryDate: string;
  quoteDeadline: string;
  urgent: boolean;
  level: number;
  /** 5-询价中、6-可报价、7-已报价 */
  status: number;
  itemCount: number;
  pricedCount: number;
  draftQuotationNo?: string;
  /** 已有草稿时点击直接打开这张草稿 */
  draftQuotationId?: number;
}

export interface QuoteInquiryPage {
  total: number;
  records: QuoteInquiry[];
  readyCount: number;
  sourcingCount: number;
}

export interface PickCustomer {
  customerId: number;
  customerName: string;
  country?: string;
  inquiryCount: number;
}

export interface PickItem {
  itemId: number;
  lineNo: number;
  model: string;
  brand: string;
  category?: string;
  quantity: number;
  costPrice?: number | null;
  noStock: boolean;
  pickable: boolean;
  disabledReason?: string;
  quoted: boolean;
  draftQuotationNo?: string;
}

export interface PickInquiry extends QuoteInquiry {
  items: PickItem[];
}

export interface QuotationItem {
  id: number;
  lineNo: number;
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
  noStock: boolean;
  costPrice?: number | null;
  costPriceForeign?: number | null;
  /** 1-按毛利率、2-按加价、3-直接填外币售价 */
  pricingMode: number;
  marginRate?: number | null;
  markupAmount?: number | null;
  suggestedMargin?: number | null;
  suggestBasis?: string;
  floorMargin?: number | null;
  belowFloor: boolean;
  hints: string[];
  unitPrice: number;
  unitPriceCny: number;
  amount: number;
  amountCny: number;
  netProfit?: number | null;
  netProfitCny?: number | null;
  /** 已进入有效销售订单 */
  won?: boolean;
}

export interface QuotationFee {
  feeName: string;
  amount: number;
  amountCny?: number;
}

export interface SendLog {
  /** 发送的版本号 */
  versionNo?: number;
  channel: number;
  channelName: string;
  sentByName?: string;
  sentAt: string;
}

export interface Quotation {
  id: number;
  quotationNo: string;
  customerId: number;
  customerName: string;
  customerCountry?: string;
  customerType?: number;
  ownerId: number;
  ownerName?: string;
  currencyCode: string;
  exchangeRate: number;
  rateTime?: string;
  incoterm: string;
  incotermPlace: string;
  validUntil?: string;
  remark: string;
  status: number;
  statusName: string;
  lostReason?: string;
  lostReasonName?: string;
  lostNote?: string;
  copiedFromId?: number;
  copiedFromNo?: string;
  itemAmount: number;
  feeAmount: number;
  totalAmount: number;
  totalAmountCny: number;
  netProfit?: number | null;
  netProfitCny?: number | null;
  marginRate?: number | null;
  sentAt?: string;
  closedAt?: string;
  createTime: string;
  editable: boolean;
  /** 正在查看的版本号 */
  versionNo: number;
  /** 当前版本号（已发送报价单为当前有效版本） */
  currentVersionNo: number;
  /** 修改中的新版本号 */
  editingVersionNo?: number | null;
  /** 各版本（1-编辑中、2-已发送） */
  versions: QuotationVersionBrief[];
  /** 引用当前版本、未作废的 PI：有时不能出新版本 */
  activePiId?: number;
  activePiNo?: string;
  items: QuotationItem[];
  fees: QuotationFee[];
  sendLogs: SendLog[];
  inquiryCodes: string[];
  returningCustomer?: boolean;
  returningCustomerMargins?: number[];
  systemRate?: number;
  pendingItemCount?: number;
}

export interface QuotationVersionBrief {
  versionNo: number;
  status: number;
  totalAmount: number;
  sentAt?: string;
  createTime: string;
}

export interface QuotationListItem {
  id: number;
  quotationNo: string;
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
  marginRate?: number | null;
  status: number;
  statusName: string;
  lostReasonName?: string;
  inquiryCodes: string[];
  ownerId: number;
  ownerName?: string;
  createTime: string;
  sentAt?: string;
  currentVersionNo?: number;
  /** 修改中的新版本号 */
  editingVersionNo?: number | null;
}

export interface QuotationStats {
  draftCount: number;
  sentCount: number;
  oldestSentDays: number;
  monthWonCount: number;
  monthWonAmountCny: number;
  monthWonNetProfitCny: number;
  monthAvgMargin?: number | null;
  monthBelowFloorCount: number;
}

export interface QuotationQuery {
  keyword?: string;
  status?: number;
  currencyCode?: string;
  ownerId?: number;
  createdFrom?: string;
  createdTo?: string;
  page: number;
  pageSize: number;
}

export interface SaveQuotationItem {
  id: number;
  description?: string;
  leadTime?: number;
  warranty?: string;
  quantity: number;
  pricingMode: number;
  marginRate?: number | null;
  markupAmount?: number | null;
  unitPrice?: number | null;
}

export interface SaveQuotation {
  currencyCode: string;
  incoterm?: string;
  incotermPlace?: string;
  validUntil?: string;
  remark?: string;
  items: SaveQuotationItem[];
  fees: { feeName: string; amount: number }[];
}

export interface PreviewResult {
  pages?: string[];
  updatedAt?: string;
  superseded?: boolean;
  unavailable?: boolean;
  message?: string;
}

export interface PricingStrategy {
  conditions: {
    itemCondition: number;
    conditionName: string;
    marginRate?: number | null;
    floorRate?: number | null;
    configured: boolean;
  }[];
  tiers: { maxCost: number; marginRate: number }[];
  hints: {
    discontinuedUrgent: boolean;
    premiumBrand: boolean;
    returningCustomer: boolean;
    toConfirm: boolean;
  };
  premiumBrands: string[];
}

// ---------------------------------------------------------------- 请求

const quiet = { skipErrorHandler: true } as const;
const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(method: string, url: string, data?: unknown) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);

const QT = '/api/v1/quotations';

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

export const quotationApi = {
  page: (q: QuotationQuery) =>
    send<{ total: number; records: QuotationListItem[] }>(
      'POST',
      `${QT}/page`,
      q,
    ),
  stats: () => get<QuotationStats>(`${QT}/stats`),
  byInquiry: (inquiryId: number) =>
    get<QuotationListItem[]>(`${QT}/by-inquiry/${inquiryId}`),
  /** version 不传时为修改中的版本，没有时为当前版本 */
  detail: (id: number, version?: number) =>
    get<Quotation>(`${QT}/${id}`, version ? { version } : {}),
  revise: (id: number) => send<Quotation>('POST', `${QT}/${id}/revise`),
  abandon: (id: number) => send<Quotation>('POST', `${QT}/${id}/abandon`),
  create: (body: { inquiryIds?: number[]; itemIds?: number[] }) =>
    send<Quotation>('POST', QT, body),
  save: (id: number, body: SaveQuotation) =>
    send<Quotation>('PUT', `${QT}/${id}`, body),
  addItems: (id: number, itemIds: number[]) =>
    send<Quotation>('POST', `${QT}/${id}/items`, { itemIds }),
  recalcRate: (id: number) =>
    send<Quotation>('POST', `${QT}/${id}/recalc-rate`),
  remove: (id: number) => send<void>('DELETE', `${QT}/${id}`),
  markSent: (id: number, channel: number) =>
    send<Quotation>('POST', `${QT}/${id}/send`, { channel }),
  copy: (id: number) => send<Quotation>('POST', `${QT}/${id}/copy`),
  markLost: (id: number, reason: string, note?: string) =>
    send<Quotation>('POST', `${QT}/${id}/lost`, { reason, note }),
  voidQuotation: (id: number) => send<Quotation>('POST', `${QT}/${id}/void`),
  text: (id: number, version?: number) =>
    get<{ text: string; templateVersionNo: number }>(
      `${QT}/${id}/text`,
      version ? { version } : {},
    ),
  exportFile: (
    id: number,
    format: 'xlsx' | 'pdf' | 'jpg',
    fallback: string,
    version?: number,
  ) =>
    download(
      `${QT}/${id}/export`,
      version ? { format, version } : { format },
      fallback,
    ),
  preview: (
    quotationId: number,
    content: SaveQuotation,
    signal?: AbortSignal,
  ) =>
    request<{ data: PreviewResult }>(`${QT}/preview`, {
      method: 'POST',
      data: { quotationId, content },
      signal,
      ...quiet,
    }).then((r) => r.data),
  converterStatus: () => get<{ available: boolean }>(`${QT}/converter-status`),
  // 新建时的候选
  quoteInquiries: (q: {
    keyword?: string;
    readyOnly?: boolean;
    dueToday?: boolean;
    page: number;
    pageSize: number;
  }) => send<QuoteInquiryPage>('POST', `${QT}/candidates/inquiries`, q),
  pickCustomers: (keyword?: string) =>
    get<PickCustomer[]>(`${QT}/candidates/customers`, { keyword }),
  pickInquiries: (q: {
    customerId: number;
    keyword?: string;
    readyOnly?: boolean;
    unquotedOnly?: boolean;
    page: number;
    pageSize: number;
  }) =>
    send<{ total: number; records: PickInquiry[] }>(
      'POST',
      `${QT}/candidates/items`,
      q,
    ),
};

export const pricingApi = {
  get: () => get<PricingStrategy>('/api/v1/quotation/pricing-strategy'),
  save: (
    body: Omit<PricingStrategy, 'conditions'> & {
      conditions: {
        itemCondition: number;
        marginRate?: number | null;
        floorRate?: number | null;
      }[];
    },
  ) => send<PricingStrategy>('PUT', '/api/v1/quotation/pricing-strategy', body),
};
