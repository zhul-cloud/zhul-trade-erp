import { request } from '@umijs/max';

export interface CustomerListItem {
  id: number;
  customerCode: string;
  name: string;
  nameCn: string;
  shortName: string;
  country: string;
  customerRole: number;
  customerGrade: number;
  sourceChannel: number;
  ownerId: number;
  ownerName?: string;
  contactName: string;
  contactPhone: string;
  contactEmail: string;
  status: number;
  createTime: string;
  createBy: string;
  updateTime: string;
  updateBy: string;
}

export interface CustomerParty {
  id?: number;
  partyType: number;
  companyName: string;
  country: string;
  state?: string;
  city?: string;
  postcode?: string;
  address: string;
  contactName?: string;
  phone?: string;
  email?: string;
  taxId?: string;
  destinationPort?: string;
  defaultParty?: boolean;
  remark?: string;
}

export interface CustomerDetail extends CustomerListItem {
  ownerDeptName?: string;
  industry: number;
  website: string;
  externalRef: string;
  state: string;
  city: string;
  postcode: string;
  address: string;
  taxId: string;
  timezone: string;
  contactTitle: string;
  whatsapp: string;
  otherIm: string;
  currency: string;
  incoterm: string;
  incotermPlace: string;
  paymentMethod: number;
  depositRatio?: number | null;
  paymentDays?: number | null;
  /** 信用额度（原币），币种见 creditCurrency */
  creditLimit?: number | null;
  creditCurrency: string;
  shippingMethod: number;
  destinationPort: string;
  remark: string;
  parties: CustomerParty[];
}

/** 提交给新增 / 更新接口的字段（选填文本清空时传空串） */
export interface CustomerPayload {
  customerCode?: string;
  ownerId?: number;
  name: string;
  nameCn?: string;
  shortName?: string;
  customerRole?: number;
  industry?: number;
  website?: string;
  customerGrade?: number;
  sourceChannel?: number;
  externalRef?: string;
  remark?: string;
  country: string;
  state?: string;
  city?: string;
  postcode?: string;
  address?: string;
  taxId?: string;
  timezone?: string;
  contactName?: string;
  contactTitle?: string;
  contactEmail?: string;
  contactPhone?: string;
  whatsapp?: string;
  otherIm?: string;
  currency?: string;
  incoterm?: string;
  incotermPlace?: string;
  paymentMethod?: number;
  depositRatio?: number | null;
  paymentDays?: number | null;
  /** 以字符串提交，避免金额经过浮点数 */
  creditLimit?: string | null;
  creditCurrency?: string;
  shippingMethod?: number;
  destinationPort?: string;
  status?: number;
  parties?: CustomerParty[];
}

export interface CustomerQuery {
  customerCode?: string;
  name?: string;
  country?: string;
  customerRole?: number;
  customerGrade?: number;
  sourceChannel?: number;
  ownerId?: number;
  status?: number;
}

export interface OwnerOption {
  id: number;
  name: string;
  deptName?: string;
}
export interface AssignableOwners {
  scope: 'ALL' | 'CUSTOM' | 'SELF' | 'NONE';
  owners: OwnerOption[];
}

export interface BizErrorInfo {
  errorCode?: string;
  detail?: Record<string, unknown>;
  message: string;
}

/** 读取接口错误：业务错误码在 info.data；参数校验失败（HTTP 400）的原因在 response.data.message */
export function readBizError(error: unknown): BizErrorInfo {
  const e = error as {
    message?: string;
    info?: { data?: { errorCode?: string; detail?: Record<string, unknown> } };
    response?: { data?: { message?: string } };
  };
  return {
    errorCode: e?.info?.data?.errorCode,
    detail: e?.info?.data?.detail,
    message: e?.response?.data?.message ?? e?.message ?? '操作失败，请稍后重试',
  };
}

const quiet = { skipErrorHandler: true } as const;
const BASE = '/api/v1/masterdata/customers';

export const customerApi = {
  page: (params: CustomerQuery & { page: number; pageSize: number }) =>
    request<{ data: { records: CustomerListItem[]; total: number } }>(
      `${BASE}/page`,
      {
        method: 'GET',
        params,
        ...quiet,
      },
    ).then((r) => r.data),

  detail: (id: number) =>
    request<{ data: CustomerDetail }>(`${BASE}/${id}/detail`, {
      method: 'GET',
      ...quiet,
    }).then((r) => r.data),

  create: (data: CustomerPayload) =>
    request<{ data: CustomerListItem }>(BASE, {
      method: 'POST',
      data,
      ...quiet,
    }).then((r) => r.data),

  update: (id: number, data: CustomerPayload) =>
    request(`${BASE}/${id}`, { method: 'PUT', data, ...quiet }),

  updateStatus: (id: number, status: number) =>
    request(`${BASE}/${id}/status`, { method: 'PUT', data: { status } }),

  remove: (id: number) =>
    request(`${BASE}/${id}`, { method: 'DELETE', ...quiet }),

  batchRemove: (ids: number[]) =>
    request<{ data: { deleted: number; referenced: number; missing: number } }>(
      `${BASE}/batch-delete`,
      {
        method: 'POST',
        data: { ids },
      },
    ).then((r) => r.data),

  transfer: (ids: number[], ownerId: number, reason?: string) =>
    request(`${BASE}/transfer`, {
      method: 'POST',
      data: { ids, ownerId, reason },
    }),

  assignableOwners: () =>
    request<{ data: AssignableOwners }>(`${BASE}/assignable-owners`, {
      method: 'GET',
    }).then((r) => r.data),

  /** 导出当前筛选结果为 xlsx 并触发下载 */
  async export(
    params: CustomerQuery,
  ): Promise<{ ok: true } | { ok: false; message: string }> {
    const res = await request(`${BASE}/export`, {
      method: 'GET',
      params,
      responseType: 'blob',
      getResponse: true,
      ...quiet,
    });
    const blob: Blob = res.data;
    if (blob.type.includes('json')) {
      try {
        const parsed = JSON.parse(await blob.text());
        return { ok: false, message: parsed.message || '导出失败，请稍后重试' };
      } catch {
        return { ok: false, message: '导出失败，请稍后重试' };
      }
    }
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `客户档案_${new Date().toISOString().slice(0, 10).replace(/-/g, '')}.xlsx`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    return { ok: true };
  },
};

// 国家 → IANA 时区（静态数据，整个页面生命周期只请求一次）
let tzCache: Promise<Record<string, string[]>> | null = null;
export const loadCountryTimezones = (): Promise<Record<string, string[]>> => {
  if (!tzCache) {
    tzCache = request<{ data: Record<string, string[]> }>(
      '/api/v1/masterdata/country-timezones',
      {
        method: 'GET',
      },
    )
      .then((r) => r.data)
      .catch((e) => {
        tzCache = null;
        throw e;
      });
  }
  return tzCache;
};
