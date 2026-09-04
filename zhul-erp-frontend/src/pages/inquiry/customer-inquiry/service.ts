import { request } from '@umijs/max';

export interface CustomerInquiryItem {
  id: number;
  inquiryCode: string;
  customerId: number;
  source: number;
  rawContent?: string;
  rawAttachmentUrl?: string;
  inquiryDate?: string;
  expectedReplyDate?: string;
  status: number;
  totalOrderCount: number;
  totalItemCount: number;
  pendingVerifyCount: number;
  ownerId?: number;
  aiTaskId?: number;
  remark?: string;
  createTime: string;
}

export interface CustomerInquiryPageQuery {
  page?: number;
  pageSize?: number;
  inquiryCode?: string;
  customerName?: string;
  statusList?: number[];
  inquiryDateFrom?: string;
  inquiryDateTo?: string;
  ownerId?: number;
}

export interface AiParseItem {
  originalModel: string;
  confirmedModel: string;
  confidence: number;
  correctionNote?: string;
  description?: string;
  quantity?: number;
  unit?: string;
  remark?: string;
}

export interface AiParseGroup {
  brand: string;
  category: string;
  inquiryTemplate?: string;
  emailTemplateCn?: string;
  emailTemplateEn?: string;
  items: AiParseItem[];
}

export interface InquiryPreview {
  inquiry: CustomerInquiryItem;
  groups: AiParseGroup[];
  totalItemCount: number;
  confirmedCount: number;
  correctedCount: number;
  pendingVerifyCount: number;
  unrecognizedCount: number;
}

export interface SubmitCustomerInquiryPayload {
  customerId: number;
  source: number;
  rawContent?: string;
  rawAttachmentUrl?: string;
  inquiryDate?: string;
  expectedReplyDate?: string;
  remark?: string;
}

export interface ConfirmSplitItemPayload {
  originalModel: string;
  confirmedModel: string;
  confidence?: number;
  correctionNote?: string;
  description?: string;
  quantity?: number;
  unit?: string;
  deliveryRequirement?: string;
  remark?: string;
}

export interface ConfirmSplitGroupPayload {
  groupIndex: number;
  items: ConfirmSplitItemPayload[];
}

export async function submitCustomerInquiry(
  data: SubmitCustomerInquiryPayload,
): Promise<CustomerInquiryItem> {
  const res = await request('/api/v1/inquiry/customer-inquiries', {
    method: 'POST',
    data,
  });
  return res.data;
}

export async function pageCustomerInquiries(
  query: CustomerInquiryPageQuery,
): Promise<{ total: number; records: CustomerInquiryItem[] }> {
  const res = await request('/api/v1/inquiry/customer-inquiries/page', {
    method: 'POST',
    data: query,
  });
  return res.data ?? { total: 0, records: [] };
}

export async function getCustomerInquiry(
  id: number,
): Promise<CustomerInquiryItem> {
  const res = await request(`/api/v1/inquiry/customer-inquiries/${id}`, {
    method: 'GET',
  });
  return res.data;
}

export async function startAiParse(id: number): Promise<CustomerInquiryItem> {
  const res = await request(
    `/api/v1/inquiry/customer-inquiries/${id}/start-parse`,
    {
      method: 'POST',
    },
  );
  return res.data;
}

export async function retryParse(id: number): Promise<CustomerInquiryItem> {
  const res = await request(
    `/api/v1/inquiry/customer-inquiries/${id}/retry-parse`,
    {
      method: 'POST',
    },
  );
  return res.data;
}

export async function getInquiryPreview(id: number): Promise<InquiryPreview> {
  const res = await request(
    `/api/v1/inquiry/customer-inquiries/${id}/preview`,
    {
      method: 'GET',
    },
  );
  return res.data;
}

export async function confirmSplit(
  id: number,
  data: { groups: ConfirmSplitGroupPayload[] },
): Promise<void> {
  await request(`/api/v1/inquiry/customer-inquiries/${id}/confirm-split`, {
    method: 'POST',
    data,
  });
}

export async function cancelCustomerInquiry(id: number): Promise<void> {
  await request(`/api/v1/inquiry/customer-inquiries/${id}/cancel`, {
    method: 'PUT',
  });
}

export async function advanceCustomerInquiryStatus(
  id: number,
  targetStatus: number,
): Promise<void> {
  await request(`/api/v1/inquiry/customer-inquiries/${id}/status`, {
    method: 'PUT',
    data: { targetStatus },
  });
}

export interface RelatedInquiryOrderItem {
  id: number;
  inquiryCode: string;
  brand: string;
  category: string;
  itemCount: number;
  assigneeId?: number;
  status: number;
}

export async function listRelatedInquiryOrders(
  customerInquiryId: number,
): Promise<RelatedInquiryOrderItem[]> {
  const res = await request(
    `/api/v1/inquiry/customer-inquiries/${customerInquiryId}/inquiry-orders`,
    { method: 'GET' },
  );
  return res.data ?? [];
}
