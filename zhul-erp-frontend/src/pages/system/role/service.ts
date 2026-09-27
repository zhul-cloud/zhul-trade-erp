import { request } from '@umijs/max';

export interface RoleItem {
  id: number;
  code: string;
  name: string;
  permissionScope: number;
  status: number;
  isBuiltIn: number;
  userCount: number;
  remark: string;
  createBy: string;
  createTime: string;
  updateBy: string;
  updateTime: string;
}

export interface RoleStats {
  total: number;
  enabledCount: number;
  builtInCount: number;
  customCount: number;
  deptCoverage: number;
  enabledRate: number;
}

export interface RoleDeleteCheck {
  blocked: boolean;
  builtIn: boolean;
  userCount: number;
  sampleUserNames: string[];
}

export async function getRoleList(params: {
  current?: number;
  pageSize?: number;
  name?: string;
  code?: string;
  status?: number;
}): Promise<{ data: RoleItem[]; total: number; success: boolean }> {
  const res = await request('/api/v1/system/roles', {
    method: 'GET',
    params: {
      page: params.current,
      pageSize: params.pageSize,
      name: params.name,
      code: params.code,
      status: params.status,
    },
  });
  return {
    data: res.data?.records ?? [],
    total: res.data?.total ?? 0,
    success: true,
  };
}

export const getRoleStats = (): Promise<RoleStats> =>
  request('/api/v1/system/roles/stats', { method: 'GET' }).then((r) => r.data);

export const getRoleDeleteCheck = (roleId: number): Promise<RoleDeleteCheck> =>
  request(`/api/v1/system/roles/${roleId}/delete-check`, {
    method: 'GET',
  }).then((r) => r.data);

export async function deleteRole(id: number): Promise<void> {
  return request(`/api/v1/system/roles/${id}`, { method: 'DELETE' });
}

export interface SaveRolePayload {
  name: string;
  permissionScope?: number;
  status?: number;
  remark?: string;
  deptIds?: number[];
}

export async function createRole(data: SaveRolePayload): Promise<void> {
  return request('/api/v1/system/roles', { method: 'POST', data });
}

export async function updateRole(
  roleId: number,
  data: SaveRolePayload,
): Promise<void> {
  return request(`/api/v1/system/roles/${roleId}`, { method: 'PUT', data });
}

export const getRoleDeptIds = (roleCode: string): Promise<number[]> =>
  request(`/api/v1/system/roles/${roleCode}/depts`, { method: 'GET' }).then(
    (r) => r.data || [],
  );
