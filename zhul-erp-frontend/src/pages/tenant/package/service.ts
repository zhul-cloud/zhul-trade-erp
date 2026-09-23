import { request } from '@umijs/max';

export interface TenantPackageItem {
  id: number;
  name: string;
  description: string;
  menuIds: number[];
  menuCount: number;
  tenantCount: number;
  status: number;
  createBy: string;
  createTime: string;
  updateBy: string;
  updateTime: string;
}

export interface SaveTenantPackagePayload {
  name: string;
  description?: string;
  menuIds: number[];
}

export interface PackageDeleteCheckResult {
  blocked: boolean;
  tenantCount: number;
}

export async function getPackageList(params: {
  current?: number;
  pageSize?: number;
  name?: string;
  status?: number;
}): Promise<{ data: TenantPackageItem[]; total: number; success: boolean }> {
  const res = await request('/api/v1/tenant/packages', {
    method: 'GET',
    params: {
      page: params.current,
      pageSize: params.pageSize,
      name: params.name,
      status: params.status,
    },
  });
  return {
    data: res.data?.records ?? [],
    total: res.data?.total ?? 0,
    success: true,
  };
}

export const createPackage = (data: SaveTenantPackagePayload) =>
  request('/api/v1/tenant/packages', { method: 'POST', data });

export const updatePackage = (id: number, data: SaveTenantPackagePayload) =>
  request(`/api/v1/tenant/packages/${id}`, { method: 'PUT', data });

export const updatePackageStatus = (id: number, status: number) =>
  request(`/api/v1/tenant/packages/${id}/status`, {
    method: 'PUT',
    data: { status },
  });

export const getPackageDeleteCheck = (
  id: number,
): Promise<PackageDeleteCheckResult> =>
  request(`/api/v1/tenant/packages/${id}/delete-check`, {
    method: 'GET',
  }).then((r) => r.data);

export const deletePackage = (id: number) =>
  request(`/api/v1/tenant/packages/${id}`, { method: 'DELETE' });
