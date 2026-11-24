import { renderHook, waitFor } from '@testing-library/react';
import { brandApi, categoryApi } from '@/pages/product/service';
import { useOptions } from './components';

jest.mock('@umijs/max', () => ({
  request: jest.fn(),
  useModel: () => ({ initialState: {} }),
}));
jest.mock('@/pages/product/service', () => ({
  brandApi: { options: jest.fn() },
  categoryApi: { tree: jest.fn() },
}));

const brands = brandApi as unknown as { options: jest.Mock };
const cats = categoryApi as unknown as { tree: jest.Mock };

beforeEach(() => {
  jest.clearAllMocks();
  cats.tree.mockResolvedValue([]);
});

/** UI-092：品牌、品类选项首次取数失败不缓存空结果，再进页面重取；过一段时间重新取，新建的能看到 */
it('UI-092：取数失败不缓存，下次进页面重取', async () => {
  brands.options
    .mockRejectedValueOnce(new Error('500'))
    .mockResolvedValue([{ id: 1, brandName: '品牌甲' }]);
  const first = renderHook(() => useOptions());
  await waitFor(() => expect(brands.options).toHaveBeenCalledTimes(1));
  await waitFor(() => expect(first.result.current.brands).toEqual([]));
  first.unmount();
  const second = renderHook(() => useOptions());
  await waitFor(() =>
    expect(second.result.current.brands).toEqual([
      { value: 1, label: '品牌甲' },
    ]),
  );
  expect(brands.options).toHaveBeenCalledTimes(2);

  // 缓存 5 分钟内复用，过期后重取
  const now = Date.now();
  const spy = jest.spyOn(Date, 'now').mockReturnValue(now + 60 * 1000);
  renderHook(() => useOptions());
  expect(brands.options).toHaveBeenCalledTimes(2);
  spy.mockReturnValue(now + 6 * 60 * 1000);
  brands.options.mockResolvedValue([
    { id: 1, brandName: '品牌甲' },
    { id: 2, brandName: '新品牌' },
  ]);
  const later = renderHook(() => useOptions());
  await waitFor(() => expect(later.result.current.brands).toHaveLength(2));
  spy.mockRestore();
});
