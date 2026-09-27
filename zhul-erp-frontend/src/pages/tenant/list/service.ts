import { request } from '@umijs/max';

export interface TenantItem {
  id: number;
  code: string;
  name: string;
  packageId: number;
  packageName: string;
  contactName: string;
  contactPhone: string;
  contactEmail: string;
  status: number;
  expireTime: string;
  remark: string;
  createBy: string;
  createTime: string;
  updateBy: string;
  updateTime: string;
}

export interface PackageOption {
  id: number;
  name: string;
}

export interface SaveTenantPayload {
  name: string;
  packageId: number;
  contactName: string;
  contactPhone: string;
  contactEmail: string;
  expireDate: string;
  remark?: string;
}

export interface TenantCreateResult {
  tenant: TenantItem;
  adminEmail: string;
  tempPassword: string;
}

export interface ResetPasswordResult {
  adminEmail: string;
  tempPassword: string;
}

export interface RenewPayload {
  mode: 'DURATION' | 'DATE';
  months?: number;
  expireDate?: string;
}

export async function getTenantList(params: {
  current?: number;
  pageSize?: number;
  name?: string;
  status?: number;
  expireDateFrom?: string;
  expireDateTo?: string;
}): Promise<{ data: TenantItem[]; total: number; success: boolean }> {
  const res = await request('/api/v1/tenant/tenants', {
    method: 'GET',
    params: {
      page: params.current,
      pageSize: params.pageSize,
      name: params.name,
      status: params.status,
      expireDateFrom: params.expireDateFrom,
      expireDateTo: params.expireDateTo,
    },
  });
  return {
    data: res.data?.records ?? [],
    total: res.data?.total ?? 0,
    success: true,
  };
}

export async function getPackageOptions(): Promise<PackageOption[]> {
  const res = await request('/api/v1/tenant/packages/options', {
    method: 'GET',
  });
  return res.data ?? [];
}

export async function createTenant(
  data: SaveTenantPayload,
): Promise<TenantCreateResult> {
  const res = await request('/api/v1/tenant/tenants', { method: 'POST', data });
  return res.data;
}

export async function updateTenant(
  id: number,
  data: SaveTenantPayload,
): Promise<TenantItem> {
  const res = await request(`/api/v1/tenant/tenants/${id}`, {
    method: 'PUT',
    data,
  });
  return res.data;
}

export async function updateTenantStatus(
  id: number,
  status: number,
): Promise<void> {
  return request(`/api/v1/tenant/tenants/${id}/status`, {
    method: 'PUT',
    data: { status },
  });
}

export async function renewTenant(
  id: number,
  data: RenewPayload,
): Promise<TenantItem> {
  const res = await request(`/api/v1/tenant/tenants/${id}/renew`, {
    method: 'POST',
    data,
  });
  return res.data;
}

export async function resetTenantPassword(
  id: number,
): Promise<ResetPasswordResult> {
  const res = await request(`/api/v1/tenant/tenants/${id}/reset-password`, {
    method: 'POST',
  });
  return res.data;
}
