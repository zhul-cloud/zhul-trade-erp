import { request } from '@umijs/max';

export interface CustomerItem {
  id: number;
  /** 客户编码，快速创建时由系统自动生成 */
  customerCode?: string;
  name: string;
  nameCn?: string;
  country?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  status?: number;
  createTime?: string;
}

export interface SupplierItem {
  id: number;
  /** 供应商编码，询盘内联创建时由系统自动生成 */
  supplierCode?: string;
  name: string;
  shortName?: string;
  country?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  status?: number;
  createTime?: string;
}

/** 快速创建客户：英文名称与国家（系统国家清单英文名）必填，其余取默认值 */
export interface SaveCustomerPayload {
  name: string;
  country: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
}

export interface SaveSupplierPayload {
  name: string;
  country?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  /** 主营品牌名称（可为别名或清单外的新名称），均按该品牌全部品类保存 */
  productScopes?: { brandName: string; categoryIds: number[] }[];
  force?: boolean;
}

export interface CreateResult<T> {
  duplicate: boolean;
  existingSupplier?: T;
  createdSupplier?: T;
}

export async function searchCustomers(keyword?: string): Promise<CustomerItem[]> {
  const res = await request('/api/v1/masterdata/customers', {
    method: 'GET',
    params: { keyword },
  });
  return res.data ?? [];
}

/** 只返回引用信息（编码、名称、国家、状态），供询盘等模块回显，不按数据权限过滤 */
export async function getCustomer(id: number): Promise<CustomerItem> {
  const res = await request(`/api/v1/masterdata/customers/${id}`, { method: 'GET' });
  return res.data;
}

/**
 * 快速创建客户。名称 + 国家与已有客户重复时接口返回 CUSTOMER_DUPLICATE，由调用方处理
 * （不弹全局错误提示）；错误对象的 info.data.detail 里有 existingId、selectable、ownerName。
 */
export async function createCustomer(data: SaveCustomerPayload): Promise<CustomerItem> {
  const res = await request('/api/v1/masterdata/customers', {
    method: 'POST',
    data,
    skipErrorHandler: true,
  });
  return res.data;
}

export async function searchSuppliers(keyword?: string): Promise<SupplierItem[]> {
  const res = await request('/api/v1/masterdata/suppliers', {
    method: 'GET',
    params: { keyword },
  });
  return res.data ?? [];
}

export async function getSupplier(id: number): Promise<SupplierItem> {
  const res = await request(`/api/v1/masterdata/suppliers/${id}`, { method: 'GET' });
  return res.data;
}

export async function createSupplier(
  data: SaveSupplierPayload,
): Promise<CreateResult<SupplierItem>> {
  const res = await request('/api/v1/masterdata/suppliers', { method: 'POST', data });
  return res.data;
}

export async function createSupplierFromChannel(
  channelName: string,
  force = false,
): Promise<CreateResult<SupplierItem>> {
  const res = await request('/api/v1/masterdata/suppliers/from-channel', {
    method: 'POST',
    data: { channelName, force },
  });
  return res.data;
}
