import { request } from '@umijs/max';

export interface MenuItem {
  id: number;
  pid: number;
  code: string;
  name: string;
  type: number;
  sort: number;
  path: string;
  componentPath: string;
  permission: string;
  lightIcon: string;
  darkIcon: string;
  microApp: string;
  isExternal: number;
  isCache: number;
  isHidden: number;
  status: number;
  createBy: string;
  createTime: string;
  updateBy: string;
  updateTime: string;
  children?: MenuItem[];
}

export interface DeleteCheckResult {
  blocked: boolean;
  children: { name: string; type: number; buttonCount?: number }[];
  referencedRoles: string[];
}

export const getMenuTree = () =>
  request('/api/v1/system/menus', { method: 'GET' }).then((r) => r.data || []);

export const createMenu = (data: Partial<MenuItem>) =>
  request('/api/v1/system/menus', { method: 'POST', data });

export const updateMenu = (id: number, data: Partial<MenuItem>) =>
  request(`/api/v1/system/menus/${id}`, { method: 'PUT', data });

export const deleteMenu = (id: number) =>
  request(`/api/v1/system/menus/${id}`, { method: 'DELETE' });

export const getDeleteCheck = (id: number): Promise<DeleteCheckResult> =>
  request(`/api/v1/system/menus/${id}/delete-check`, { method: 'GET' }).then(
    (r) => r.data,
  );

export const updateMenuStatus = (id: number, status: number) =>
  request(`/api/v1/system/menus/${id}/status`, {
    method: 'PUT',
    data: { status },
  });

export const updateMenuSort = (id: number, sort: number) =>
  request(`/api/v1/system/menus/${id}/sort`, { method: 'PUT', data: { sort } });

export const getRoleMenuIds = (roleCode: string) =>
  request(`/api/v1/system/menus/role/${roleCode}`, { method: 'GET' }).then(
    (r) => r.data || [],
  );

export const assignRoleMenus = (roleCode: string, menuIds: number[]) =>
  request(`/api/v1/system/menus/role/${roleCode}`, {
    method: 'PUT',
    data: menuIds,
  });
