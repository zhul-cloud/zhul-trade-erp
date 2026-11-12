import { renderHook, waitFor } from '@testing-library/react';
import { request } from '@umijs/max';
import { updateDictItem } from '@/pages/system/dict/service';
import { useDictOptions } from './dict';

jest.mock('@umijs/max', () => ({ request: jest.fn() }));

const item = (status: number) => ({
  data: [{ itemValue: '7', itemName: '阿里巴巴国际站', status }],
});

/** UI-008：在字典管理改了字典项，之后打开的业务页取到新值，停用项不再可选 */
it('字典改动后业务下拉不再用旧缓存', async () => {
  const req = request as jest.Mock;
  req.mockResolvedValue(item(1));
  const first = renderHook(() => useDictOptions('it_ui008_channel'));
  await waitFor(() => expect(first.result.current.options).toHaveLength(1));
  first.unmount();

  // 字典管理里停用这个字典项
  req.mockResolvedValue(undefined);
  await updateDictItem(1, {
    dictTypeId: 1,
    itemName: '阿里巴巴国际站',
    itemValue: '7',
    status: 0,
  } as never);
  req.mockResolvedValue(item(0));

  const again = renderHook(() => useDictOptions('it_ui008_channel'));
  await waitFor(() =>
    expect(again.result.current.labelOf(7)).toBe('阿里巴巴国际站'),
  );
  expect(again.result.current.options).toHaveLength(0);
});
