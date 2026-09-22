import { request } from '@umijs/max';

export type ConfigType = 'STRING' | 'INTEGER' | 'BOOLEAN' | 'JSON' | 'URL';

export interface ConfigItem {
  id: number;
  configKey: string;
  configName: string;
  configValue: string;
  configType: ConfigType;
  isBuiltin: number;
  isEncrypted: number;
  configGroup: string;
  remark: string;
  createBy: string;
  createTime: string;
  updateBy: string;
  updateTime: string;
}

export interface ConfigGroupCount {
  group: string;
  count: number;
}

export async function getConfigList(params: {
  current?: number;
  pageSize?: number;
  keyword?: string;
  group?: string;
}): Promise<{ data: ConfigItem[]; total: number; success: boolean }> {
  const res = await request('/api/v1/system/configs', {
    method: 'GET',
    params: {
      page: params.current,
      pageSize: params.pageSize,
      keyword: params.keyword,
      group: params.group,
    },
  });
  return {
    data: res.data?.records ?? [],
    total: res.data?.total ?? 0,
    success: true,
  };
}

export const getConfigGroupCounts = (): Promise<ConfigGroupCount[]> =>
  request('/api/v1/system/configs/group-counts', { method: 'GET' }).then(
    (r) => r.data ?? [],
  );

export interface SaveConfigPayload {
  configKey: string;
  configName: string;
  configValue: string;
  configType: ConfigType;
  configGroup: string;
  isEncrypted?: number;
  remark?: string;
}

export async function createConfig(data: SaveConfigPayload): Promise<void> {
  return request('/api/v1/system/configs', { method: 'POST', data });
}

export async function updateConfigValue(
  id: number,
  value: string,
): Promise<void> {
  return request(`/api/v1/system/configs/${id}`, {
    method: 'PUT',
    data: { value },
  });
}

export async function deleteConfig(id: number): Promise<void> {
  return request(`/api/v1/system/configs/${id}`, { method: 'DELETE' });
}

export async function uploadConfigImage(file: File): Promise<string> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await request('/api/v1/system/configs/upload-image', {
    method: 'POST',
    data: formData,
    requestType: 'form',
  });
  return res.data?.url;
}
