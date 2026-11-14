import { quickFromUrl } from './index';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
  useAccess: () => ({}),
  useSearchParams: () => [new URLSearchParams()],
}));

/** UI-030：工作台「PI 待收款」「过期未收款 PI」进入列表时选中对应快捷筛选，不限创建日期 */
it('地址带 unpaid=1 时只看待收款', () => {
  expect(quickFromUrl(new URLSearchParams('unpaid=1'))).toEqual({
    unpaid: true,
  });
});

it('地址带 expired=1 时只看已过期未收款', () => {
  expect(quickFromUrl(new URLSearchParams('expired=1'))).toEqual({
    expiredUnpaid: true,
  });
});

it('没有带时用默认筛选', () => {
  expect(quickFromUrl(new URLSearchParams(''))).toBeUndefined();
});
