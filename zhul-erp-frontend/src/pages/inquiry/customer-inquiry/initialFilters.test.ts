import { initialFilters } from './index';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
  useAccess: () => ({}),
  useModel: () => ({ initialState: {} }),
}));

/** UI-030：工作台「待确认的询盘」「可以报价」只按状态筛，不限日期，与待办数字同口径 */
it('地址带状态时按状态筛、清空日期', () => {
  window.history.pushState({}, '', '/inquiry/customer-inquiries?status=6');
  expect(initialFilters()).toEqual({ status: 6 });
});

it('从询价统计带日期时仍用地址日期', () => {
  window.history.pushState(
    {},
    '',
    '/inquiry/customer-inquiries?dateFrom=2026-11-01&dateTo=2026-11-14',
  );
  const f = initialFilters();
  expect(f.range?.[0]?.format('YYYY-MM-DD')).toBe('2026-11-01');
  expect(f.status).toBeUndefined();
});

it('普通进入默认最近 3 个月', () => {
  window.history.pushState({}, '', '/inquiry/customer-inquiries');
  const f = initialFilters();
  expect(f.range).toBeTruthy();
  expect(f.status).toBeUndefined();
});
