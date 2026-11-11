import { request } from '@umijs/max';
import { getUserList } from './service';

jest.mock('@umijs/max', () => ({ request: jest.fn() }));

/** UI-024：用户列表选了部门，请求要带上 deptId；清空后不带 */
it('用户列表按部门筛选时请求带 deptId', async () => {
  (request as jest.Mock).mockResolvedValue({ data: { records: [], total: 0 } });
  await getUserList({ current: 2, pageSize: 20, deptId: 7 });
  expect((request as jest.Mock).mock.calls[0][1].params).toMatchObject({
    page: 2,
    pageSize: 20,
    deptId: 7,
  });
  await getUserList({ current: 1, pageSize: 20 });
  expect((request as jest.Mock).mock.calls[1][1].params.deptId).toBeUndefined();
});
