import { request } from '@umijs/max';

export interface InquiryOrderItem {
  id: number;
  inquiryCode: string;
  customerInquiryId?: number;
  customerId?: number;
  brand: string;
  category: string;
  itemCount: number;
  assigneeId?: number;
  status: number;
  inquiryTemplate?: string;
  emailTemplateCn?: string;
  emailTemplateEn?: string;
  aiTaskId?: number;
  remark?: string;
  createTime: string;
}

export interface InquiryOrderPageQuery {
  page?: number;
  pageSize?: number;
  inquiryCode?: string;
  brand?: string;
  category?: string;
  statusList?: number[];
  assigneeId?: number;
  mine?: boolean;
  manualOnly?: boolean;
}

export interface InquiryOrderItemDetail {
  id: number;
  itemCode: string;
  inquiryOrderId: number;
  brand: string;
  category: string;
  originalModel: string;
  confirmedModel: string;
  confidence: number;
  correctionNote?: string;
  description?: string;
  quantity: number;
  unit: string;
  deliveryRequirement?: string;
  remark?: string;
}

export interface InquiryOrderSupplierItem {
  id: number;
  inquiryOrderId: number;
  sourceType: number;
  supplierId?: number;
  supplierName?: string;
  channelPlatform?: number;
  channelName?: string;
  channelLink?: string;
  sentDate?: string;
  replyDeadline?: string;
  status: number;
  quoteFileUrl?: string;
  remark?: string;
  displayName: string;
}

export interface QuoteComparisonCell {
  inquiryOrderSupplierId: number;
  quoted: boolean;
  quotePriceCny?: number;
  currencyCode?: string;
  quotePriceOriginal?: number;
  supplierDelivery?: string;
  lowestPrice: boolean;
}

export interface QuoteComparisonRow {
  inquiryOrderItemId: number;
  confirmedModel: string;
  cellsBySupplierId: Record<number, QuoteComparisonCell>;
}

export interface QuoteComparison {
  columns: InquiryOrderSupplierItem[];
  rows: QuoteComparisonRow[];
}

export interface InquiryTemplate {
  inquiryTemplate?: string;
  emailTemplateCn?: string;
  emailTemplateEn?: string;
}

export interface ManualItemPayload {
  model: string;
  quantity?: number;
  unit?: string;
  remark?: string;
}

export interface CreateInquiryOrderManualPayload {
  customerId?: number;
  customerInquiryId?: number;
  brand: string;
  category: string;
  items: ManualItemPayload[];
}

export interface AddSupplierPayload {
  sourceType: number;
  supplierId?: number;
  channelPlatform?: number;
  channelName?: string;
  channelLink?: string;
  sentDate?: string;
  replyDeadline?: string;
}

export interface RecordQuotePayload {
  inquiryOrderItemId: number;
  inquiryOrderSupplierId: number;
  quotePriceOriginal?: number;
  currencyCode?: string;
  exchangeRate?: number;
  supplierDelivery?: string;
  quoteStatus: number;
}

export async function pageInquiryOrders(
  query: InquiryOrderPageQuery,
): Promise<{ total: number; records: InquiryOrderItem[] }> {
  const res = await request('/api/v1/inquiry/inquiry-orders/page', {
    method: 'POST',
    data: query,
  });
  return res.data ?? { total: 0, records: [] };
}

export async function getInquiryOrder(id: number): Promise<InquiryOrderItem> {
  const res = await request(`/api/v1/inquiry/inquiry-orders/${id}`, {
    method: 'GET',
  });
  return res.data;
}

export async function createInquiryOrderManual(
  data: CreateInquiryOrderManualPayload,
): Promise<InquiryOrderItem> {
  const res = await request('/api/v1/inquiry/inquiry-orders/manual', {
    method: 'POST',
    data,
  });
  return res.data;
}

export async function listInquiryOrderItems(
  id: number,
): Promise<InquiryOrderItemDetail[]> {
  const res = await request(`/api/v1/inquiry/inquiry-orders/${id}/items`, {
    method: 'GET',
  });
  return res.data ?? [];
}

export async function assignPurchaser(
  id: number,
  assigneeId: number,
): Promise<void> {
  await request(`/api/v1/inquiry/inquiry-orders/${id}/assign`, {
    method: 'PUT',
    data: { assigneeId },
  });
}

export async function addInquiryOrderSupplier(
  id: number,
  data: AddSupplierPayload,
): Promise<InquiryOrderSupplierItem> {
  const res = await request(`/api/v1/inquiry/inquiry-orders/${id}/suppliers`, {
    method: 'POST',
    data,
  });
  return res.data;
}

export async function listInquiryOrderSuppliers(
  id: number,
): Promise<InquiryOrderSupplierItem[]> {
  const res = await request(`/api/v1/inquiry/inquiry-orders/${id}/suppliers`, {
    method: 'GET',
  });
  return res.data ?? [];
}

export async function convertToFormalSupplier(
  inquiryOrderSupplierId: number,
  data: { force?: boolean; linkExistingSupplierId?: number },
): Promise<InquiryOrderSupplierItem> {
  const res = await request(
    `/api/v1/inquiry/inquiry-orders/suppliers/${inquiryOrderSupplierId}/convert-to-formal`,
    { method: 'PUT', data },
  );
  return res.data;
}

export async function recordQuote(
  id: number,
  data: RecordQuotePayload,
): Promise<void> {
  await request(`/api/v1/inquiry/inquiry-orders/${id}/quotes`, {
    method: 'POST',
    data,
  });
}

export async function getQuoteComparison(
  id: number,
): Promise<QuoteComparison> {
  const res = await request(
    `/api/v1/inquiry/inquiry-orders/${id}/quote-comparison`,
    { method: 'GET' },
  );
  return res.data ?? { columns: [], rows: [] };
}

export async function advanceInquiryOrderStatus(
  id: number,
  targetStatus: number,
): Promise<void> {
  await request(`/api/v1/inquiry/inquiry-orders/${id}/status`, {
    method: 'PUT',
    data: { targetStatus },
  });
}

export async function getInquiryOrderTemplates(
  id: number,
): Promise<InquiryTemplate> {
  const res = await request(`/api/v1/inquiry/inquiry-orders/${id}/templates`, {
    method: 'GET',
  });
  return res.data ?? {};
}
