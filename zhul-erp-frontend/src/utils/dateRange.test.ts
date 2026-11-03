import dayjs from 'dayjs';
import { recentRange } from './dateRange';

describe('recentRange', () => {
  it('从 3 个月前到今天', () => {
    const [from, to] = recentRange();
    expect(to.format('YYYY-MM-DD')).toBe(dayjs().format('YYYY-MM-DD'));
    expect(from.format('YYYY-MM-DD')).toBe(
      dayjs().subtract(3, 'month').format('YYYY-MM-DD'),
    );
  });
});
