import { request } from '@umijs/max';

export interface UserItem {
  id: number;
  name: string;
  username: string;
  phone: string;
  email: string;
  nickname: string;
  avatarUrl: string;
  deptId: number;
  deptName: string;
  positionId: number;
  positionName: string;
  roleCode: string;
  roleName: string;
  status: number;
  createTime: string;
}

export interface UserPageResult {
  total: number;
  records: UserItem[];
}

export interface UserStats {
  total: number;
  enabled: number;
  disabled: number;
  newThisMonth: number;
}

export async function getUserList(params: {
  current?: number;
  pageSize?: number;
  name?: string;
  phone?: string;
  status?: number;
}): Promise<{ data: UserPageResult }> {
  const res = await request('/api/v1/system/users', {
    method: 'GET',
    params: {
      page: params.current,
      pageSize: params.pageSize,
      name: params.name,
      phone: params.phone,
      status: params.status,
    },
  });
  return { data: res.data };
}

export async function getUserStats(): Promise<{ data: UserStats }> {
  const res = await request('/api/v1/system/users/stats', { method: 'GET' });
  return { data: res.data };
}

export async function updateUserStatus(
  userId: number,
  status: number,
): Promise<void> {
  return request(`/api/v1/system/users/${userId}/status`, {
    method: 'PUT',
    params: { status },
  });
}

export async function createUser(data: {
  name: string;
  username: string;
  phone: string;
  email?: string;
  deptId?: number;
  positionId?: number;
  roleCode?: string;
  password: string;
  status?: number;
}): Promise<void> {
  return request('/api/v1/system/users', { method: 'POST', data });
}

export async function updateUser(
  userId: number,
  data: Partial<UserItem>,
): Promise<void> {
  return request(`/api/v1/system/users/${userId}`, { method: 'PUT', data });
}

export async function deleteUser(userId: number): Promise<void> {
  return request(`/api/v1/system/users/${userId}`, { method: 'DELETE' });
}

export async function resetPassword(
  userId: number,
  newPassword: string,
): Promise<void> {
  return request(`/api/v1/system/users/${userId}/password`, {
    method: 'PUT',
    params: { newPassword },
  });
}

export async function getDeptOptions(): Promise<
  { label: string; value: number }[]
> {
  const res = await request('/api/v1/system/depts', { method: 'GET' });
  return (res.data || []).map((d: { name: string; id: number }) => ({
    label: d.name,
    value: d.id,
  }));
}

export async function getRoleOptions(): Promise<
  { label: string; value: string }[]
> {
  const res = await request('/api/v1/system/roles/all', { method: 'GET' });
  return (res.data || []).map((r: { name: string; code: string }) => ({
    label: r.name,
    value: r.code,
  }));
}

export async function getPositionOptions(): Promise<
  { label: string; value: number }[]
> {
  const res = await request('/api/v1/system/positions/all', {
    method: 'GET',
  });
  return (res.data || []).map((p: { name: string; id: number }) => ({
    label: p.name,
    value: p.id,
  }));
}
