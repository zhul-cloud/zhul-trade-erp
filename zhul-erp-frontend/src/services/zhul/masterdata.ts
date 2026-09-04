import { request } from '@umijs/max';

export interface CustomerItem {
  id: number;
  name: string;
  country?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  status?: number;
  createTime?: string;
}

export interface SupplierItem {
  id: number;
  name: string;
  country?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  mainBrands?: string;
  status?: number;
  createTime?: string;
}

export interface SaveCustomerPayload {
  name: string;
  country?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  force?: boolean;
}

export interface SaveSupplierPayload {
  name: string;
  country?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  mainBrands?: string;
  force?: boolean;
}

export interface CreateResult<T> {
  duplicate: boolean;
  existingCustomer?: T;
  createdCustomer?: T;
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

export async function getCustomer(id: number): Promise<CustomerItem> {
  const res = await request(`/api/v1/masterdata/customers/${id}`, { method: 'GET' });
  return res.data;
}

export async function createCustomer(
  data: SaveCustomerPayload,
): Promise<CreateResult<CustomerItem>> {
  const res = await request('/api/v1/masterdata/customers', { method: 'POST', data });
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
