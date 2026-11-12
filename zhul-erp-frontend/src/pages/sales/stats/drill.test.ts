import dayjs from 'dayjs';
import { drillQuery } from './index';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
  useAccess: () => ({}),
  useModel: () => ({ initialState: {} }),
}));

/** UI-015：下钻带上日期、业务员、事业部，目标页才能和统计数对上 */
it('下钻地址带上全部统计条件', () => {
  const q = new URLSearchParams(
    drillQuery(dayjs('2026-11-01'), dayjs('2026-11-30'), '1000009002', 7),
  );
  expect(q.get('dateFrom')).toBe('2026-11-01');
  expect(q.get('dateTo')).toBe('2026-11-30');
  expect(q.get('ownerId')).toBe('1000009002');
  expect(q.get('businessUnitId')).toBe('7');
  const plain = new URLSearchParams(
    drillQuery(dayjs('2026-11-01'), dayjs('2026-11-30')),
  );
  expect(plain.has('ownerId')).toBe(false);
  expect(plain.has('businessUnitId')).toBe(false);
});
