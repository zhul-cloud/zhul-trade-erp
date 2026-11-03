import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import {
  assignRoleMenus,
  getMenuTree,
  getRoleMenuIds,
} from '@/pages/system/menu/service';
import RolePage from './index';
import { getRoleList, updateRole } from './service';

jest.mock('@umijs/max', () => ({
  useAccess: () => ({ 'system:role:assign': true, 'system:role:edit': true }),
  useLocation: () => ({ pathname: '/system/role' }),
  useModel: () => ({ initialState: {} }),
  Link: () => null,
  request: jest.fn(),
}));

jest.mock('./service', () => ({
  getRoleList: jest.fn(),
  getRoleStats: jest
    .fn()
    .mockResolvedValue({ total: 2, enabled: 2, disabled: 0 }),
  getRoleDeleteCheck: jest.fn(),
  deleteRole: jest.fn(),
  createRole: jest.fn(),
  updateRole: jest.fn().mockResolvedValue(undefined),
  getRoleDeptIds: jest.fn().mockResolvedValue([]),
}));

jest.mock('@/pages/system/menu/service', () => ({
  getMenuTree: jest.fn(),
  getRoleMenuIds: jest.fn(),
  assignRoleMenus: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('@/pages/system/user/service', () => ({
  getDeptTreeOptions: jest.fn().mockResolvedValue([]),
}));

jest.mock('@/pages/dashboard/service', () => ({
  workbenchApi: { templates: jest.fn().mockResolvedValue([]) },
}));

const role = (
  id: number,
  code: string,
  name: string,
  permissionScope: number,
) => ({
  id,
  code,
  name,
  permissionScope,
  workbenchTemplate: 'general',
  status: 1,
  isBuiltIn: 0,
  userCount: 0,
  remark: '',
  createBy: 'admin',
  createTime: '2026-11-01T10:00:00',
  updateBy: 'admin',
  updateTime: '2026-11-01T10:00:00',
});

const menu = (id: number, name: string) => ({
  id,
  pid: 0,
  code: `M${id}`,
  name,
  type: 2,
  sort: id,
  path: `/m${id}`,
  componentPath: '',
  permission: '',
  lightIcon: '',
  darkIcon: '',
  microApp: '',
  isExternal: 0,
  isCache: 0,
  children: [],
});

/** 可手动完成的 Promise，模拟慢接口 */
function deferred<T>() {
  let resolve!: (v: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

const mocked = {
  getRoleList: getRoleList as jest.Mock,
  getMenuTree: getMenuTree as jest.Mock,
  getRoleMenuIds: getRoleMenuIds as jest.Mock,
  assignRoleMenus: assignRoleMenus as jest.Mock,
  updateRole: updateRole as jest.Mock,
};

const renderPage = () =>
  render(React.createElement(App, null, React.createElement(RolePage)));

const saveButton = () =>
  screen.getByRole('button', { name: '保存权限' }) as HTMLButtonElement;

describe('RolePage 分配权限（audit UI-014）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mocked.getRoleList.mockResolvedValue({
      data: [role(1, 'RA', '角色甲', 1), role(2, 'RB', '角色乙', 0)],
      total: 2,
      success: true,
    });
    mocked.getMenuTree.mockResolvedValue([
      menu(11, '菜单一'),
      menu(12, '菜单二'),
    ]);
  });

  it('当前角色权限加载完成前不能保存，完成后保存的是该角色的权限', async () => {
    const ids = deferred<number[]>();
    mocked.getRoleMenuIds.mockReturnValue(ids.promise);
    renderPage();
    fireEvent.click((await screen.findAllByText('分配权限'))[0]);

    expect(saveButton().disabled).toBe(true);
    fireEvent.click(saveButton());
    expect(mocked.assignRoleMenus).not.toHaveBeenCalled();

    ids.resolve([11]);
    await waitFor(() => expect(saveButton().disabled).toBe(false));
    fireEvent.click(saveButton());
    await waitFor(() =>
      expect(mocked.assignRoleMenus).toHaveBeenCalledWith('RA', [11]),
    );
  });

  it('先打开的角色晚返回时，不覆盖后打开角色的权限', async () => {
    const slowA = deferred<number[]>();
    mocked.getRoleMenuIds.mockImplementation((code: string) =>
      code === 'RA' ? slowA.promise : Promise.resolve([12]),
    );
    renderPage();
    fireEvent.click((await screen.findAllByText('分配权限'))[0]);
    fireEvent.click(screen.getByRole('button', { name: /取\s?消/ }));
    fireEvent.click((await screen.findAllByText('分配权限'))[1]);
    await waitFor(() => expect(saveButton().disabled).toBe(false));

    slowA.resolve([11]);
    await slowA.promise;
    fireEvent.click(saveButton());
    await waitFor(() =>
      expect(mocked.assignRoleMenus).toHaveBeenCalledWith('RB', [12]),
    );
  });
});

describe('RolePage 数据权限（audit UI-013）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mocked.getRoleList.mockResolvedValue({
      data: [role(2, 'RB', '角色乙', 0)],
      total: 1,
      success: true,
    });
  });

  it('旧值 0 按后端实际执行的「仅本人」显示', async () => {
    renderPage();
    expect(await screen.findByText('仅本人数据')).toBeTruthy();
    expect(screen.queryByText('无')).toBeNull();
  });
});
