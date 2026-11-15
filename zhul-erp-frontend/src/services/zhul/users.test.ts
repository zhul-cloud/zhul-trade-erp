import { request } from '@umijs/max';
import { getUserOptions } from './users';

jest.mock('@umijs/max', () => ({ request: jest.fn() }));

/** audit SEC-019：负责人下拉用只含 ID、姓名的选项接口，不再取完整用户列表 */
it('负责人下拉走用户选项接口并透传筛选', async () => {
  (request as jest.Mock).mockResolvedValue({ data: [{ id: 1, name: '张三' }] });
  await expect(getUserOptions({ name: '张', status: 1, limit: 10 })).resolves.toEqual([{ id: 1, name: '张三' }]);
  expect((request as jest.Mock).mock.calls[0][0]).toBe('/api/v1/system/users/options');
  expect((request as jest.Mock).mock.calls[0][1].params).toEqual({ name: '张', status: 1, limit: 10 });
  (request as jest.Mock).mockResolvedValue({});
  await expect(getUserOptions()).resolves.toEqual([]);
});
