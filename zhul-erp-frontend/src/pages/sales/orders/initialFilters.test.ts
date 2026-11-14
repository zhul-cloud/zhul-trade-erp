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

/** UI-030：工作台「订单待出运」只按订单状态筛，不限日期 */
it('地址带订单状态时选中该状态、清空日期', () => {
  window.history.pushState({}, '', '/sales/orders?progressCode=RECEIVED');
  const f = initial();
  expect(f.progressCode).toBe('RECEIVED');
  expect(f.range).toBeNull();
  expect(f.lossOnly).toBeUndefined();
});

/** UI-030：经营看板「毛利为负的订单」选中快捷筛选，销售日期为本月 */
it('地址带 loss=1 时选中毛利为负，用地址里的销售日期', () => {
  window.history.pushState(
    {},
    '',
    '/sales/orders?loss=1&dateFrom=2026-11-01&dateTo=2026-11-14',
  );
  const f = initial();
  expect(f.lossOnly).toBe(true);
  expect(f.range?.[0]?.format('YYYY-MM-DD')).toBe('2026-11-01');
  expect(f.range?.[1]?.format('YYYY-MM-DD')).toBe('2026-11-14');
});

it('普通进入仍默认最近 3 个月、不选快捷筛选', () => {
  window.history.pushState({}, '', '/sales/orders');
  const f = initial();
  expect(f.range).toBeTruthy();
  expect(f.lossOnly).toBeUndefined();
  expect(f.progressCode).toBeUndefined();
});
