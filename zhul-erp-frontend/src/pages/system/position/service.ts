import { request } from '@umijs/max';

export interface PositionItem {
  id: number;
  code: string;
  name: string;
  sort: number;
  status: number;
  remark: string;
  createBy: string;
  createTime: string;
  updateBy: string;
  updateTime: string;
}

export interface PositionDeleteCheck {
  blocked: boolean;
  userCount: number;
  sampleUserNames: string[];
}

export async function getPositionList(params: {
  current?: number;
  pageSize?: number;
  name?: string;
  status?: number;
}): Promise<{ data: PositionItem[]; total: number; success: boolean }> {
  const res = await request('/api/v1/system/positions', {
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

export const getPositionDeleteCheck = (
  id: number,
): Promise<PositionDeleteCheck> =>
  request(`/api/v1/system/positions/${id}/delete-check`, {
    method: 'GET',
  }).then((r) => r.data);

export interface SavePositionPayload {
  code: string;
  name: string;
  sort?: number;
  status?: number;
  remark?: string;
}

export async function createPosition(data: SavePositionPayload): Promise<void> {
  return request('/api/v1/system/positions', { method: 'POST', data });
}

export async function updatePosition(
  id: number,
  data: SavePositionPayload,
): Promise<void> {
  return request(`/api/v1/system/positions/${id}`, { method: 'PUT', data });
}

export async function updatePositionStatus(
  id: number,
  status: number,
): Promise<void> {
  return request(`/api/v1/system/positions/${id}/status`, {
    method: 'PUT',
    data: { status },
  });
}

export async function deletePosition(id: number): Promise<void> {
  return request(`/api/v1/system/positions/${id}`, { method: 'DELETE' });
}
