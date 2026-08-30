import { request } from '@umijs/max';

export interface DictTypeItem {
  id: number;
  dictType: string;
  dictName: string;
  isBuiltin: number;
  status: number;
  remark: string;
  createTime: string;
}

export interface DictTypeDeleteCheck {
  blocked: boolean;
  builtin: boolean;
  itemCount: number;
}

export interface DictItemRow {
  id: number;
  dictTypeId: number;
  dictType: string;
  itemCode: string;
  itemName: string;
  itemValue: string;
  cssClass: string;
  sortOrder: number;
  isDefault: number;
  status: number;
  remark: string;
  createTime: string;
}

export async function getDictTypeList(name?: string): Promise<DictTypeItem[]> {
  const res = await request('/api/v1/system/dict-types', {
    method: 'GET',
    params: { name },
  });
  return res.data ?? [];
}

export const getDictTypeDeleteCheck = (
  id: number,
): Promise<DictTypeDeleteCheck> =>
  request(`/api/v1/system/dict-types/${id}/delete-check`, {
    method: 'GET',
  }).then((r) => r.data);

export interface SaveDictTypePayload {
  dictType: string;
  dictName: string;
  status?: number;
  remark?: string;
}

export async function createDictType(data: SaveDictTypePayload): Promise<void> {
  return request('/api/v1/system/dict-types', { method: 'POST', data });
}

export async function updateDictType(
  id: number,
  data: SaveDictTypePayload,
): Promise<void> {
  return request(`/api/v1/system/dict-types/${id}`, { method: 'PUT', data });
}

export async function deleteDictType(id: number): Promise<void> {
  return request(`/api/v1/system/dict-types/${id}`, { method: 'DELETE' });
}

export async function getDictItemList(
  dictTypeId: number,
): Promise<DictItemRow[]> {
  const res = await request('/api/v1/system/dict-items', {
    method: 'GET',
    params: { dictTypeId },
  });
  return res.data ?? [];
}

export interface SaveDictItemPayload {
  dictTypeId: number;
  itemCode: string;
  itemName: string;
  itemValue: string;
  cssClass?: string;
  sortOrder?: number;
  isDefault?: number;
  status?: number;
  remark?: string;
}

export async function createDictItem(data: SaveDictItemPayload): Promise<void> {
  return request('/api/v1/system/dict-items', { method: 'POST', data });
}

export async function updateDictItem(
  id: number,
  data: SaveDictItemPayload,
): Promise<void> {
  return request(`/api/v1/system/dict-items/${id}`, { method: 'PUT', data });
}

export async function deleteDictItem(id: number): Promise<void> {
  return request(`/api/v1/system/dict-items/${id}`, { method: 'DELETE' });
}
