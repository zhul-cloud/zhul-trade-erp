import { categoryApi } from '../service';
import { loadAllTopCategories } from './new';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
  useModel: () => ({ initialState: {} }),
  useSearchParams: () => [new URLSearchParams(), jest.fn()],
}));

jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  categoryApi: { page: jest.fn() },
}));

/** UI-009：启用的一级品类超过 100 个时，向导也能拿到第 101 个及之后的 */
it('一级品类超过一页时全部取回', async () => {
  const all = Array.from({ length: 230 }, (_, i) => ({
    id: i + 1,
    categoryName: `C${i + 1}`,
  }));
  (categoryApi.page as jest.Mock).mockImplementation(
    (q: { page: number; pageSize: number }) =>
      Promise.resolve({
        total: all.length,
        records: all.slice((q.page - 1) * q.pageSize, q.page * q.pageSize),
      }),
  );
  const got = await loadAllTopCategories();
  expect(got).toHaveLength(230);
  expect(got[229].categoryName).toBe('C230');
  expect(categoryApi.page).toHaveBeenCalledTimes(3);
});
