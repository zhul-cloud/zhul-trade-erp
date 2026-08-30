import { request } from '@umijs/max';

export interface DeptItem {
  id: number;
  pid: number;
  code: string;
  name: string;
  allName: string;
  leaderId: number;
  leaderName?: string;
  phone: string;
  level: number;
  sort: number;
  status: number;
  remark: string;
  createTime: string;
  updateTime: string;
  children?: DeptItem[];
}

export interface DeptDeleteCheck {
  blocked: boolean;
  childCount: number;
  userCount: number;
  sampleUserNames: string[];
}

export async function getDeptList(): Promise<DeptItem[]> {
  const res = await request('/api/v1/system/depts', { method: 'GET' });
  return res.data ?? [];
}

export async function getDeptTree(): Promise<DeptItem[]> {
  const res = await request('/api/v1/system/depts/tree', { method: 'GET' });
  return res.data ?? [];
}

export const getDeptDeleteCheck = (id: number): Promise<DeptDeleteCheck> =>
  request(`/api/v1/system/depts/${id}/delete-check`, { method: 'GET' }).then(
    (r) => r.data,
  );

export interface SaveDeptPayload {
  name: string;
  allName?: string;
  pid?: number;
  leaderId?: number;
  phone?: string;
  remark?: string;
  sort?: number;
  status?: number;
}

export async function createDept(data: SaveDeptPayload): Promise<void> {
  return request('/api/v1/system/depts', { method: 'POST', data });
}

export async function updateDept(
  id: number,
  data: SaveDeptPayload,
): Promise<void> {
  return request(`/api/v1/system/depts/${id}`, { method: 'PUT', data });
}

export async function updateDeptStatus(
  id: number,
  status: number,
): Promise<void> {
  return request(`/api/v1/system/depts/${id}/status`, {
    method: 'PUT',
    data: { status },
  });
}

export async function deleteDept(id: number): Promise<void> {
  return request(`/api/v1/system/depts/${id}`, { method: 'DELETE' });
}
