import { initial } from './index';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
  useAccess: () => ({}),
}));

/** UI-015：从销售统计下钻带来的业务员、事业部直接作为订单列表的筛选条件 */
it('从地址读取销售日期、业务员与事业部', () => {
  window.history.pushState(
    {},
    '',
    '/sales/orders?dateFrom=2026-11-01&dateTo=2026-11-30&ownerId=5&businessUnitId=7',
  );
  const f = initial();
  expect(f.range?.[0]?.format('YYYY-MM-DD')).toBe('2026-11-01');
  expect(f.range?.[1]?.format('YYYY-MM-DD')).toBe('2026-11-30');
  expect(f.ownerId).toBe(5);
  expect(f.businessUnitId).toBe(7);
});

it('地址没有带事业部时不加筛选', () => {
  window.history.pushState({}, '', '/sales/orders');
  expect(initial().businessUnitId).toBeUndefined();
});
