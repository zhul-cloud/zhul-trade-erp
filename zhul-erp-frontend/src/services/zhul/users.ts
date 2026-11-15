import { request } from '@umijs/max';

export interface UserOption {
  id: number;
  name: string;
}

/** 负责人等下拉用的用户选项：只有 ID 和姓名，登录即可用（audit SEC-019） */
export async function getUserOptions(params?: {
  name?: string;
  status?: number;
  limit?: number;
}): Promise<UserOption[]> {
  const res = await request('/api/v1/system/users/options', {
    method: 'GET',
    params,
  });
  return res.data ?? [];
}
