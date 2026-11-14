import dayjs from 'dayjs';
import { deferOptions, nextMonday } from './DeferModal';

describe('延后日期选项', () => {
  it('明天、3 天后、下周一', () => {
    // 2026-11-13 是周五
    const opts = deferOptions(dayjs('2026-11-13'));
    expect(opts.map((o) => [o.label, o.date.format('YYYY-MM-DD')])).toEqual([
      ['明天', '2026-11-14'],
      ['3 天后', '2026-11-16'],
      ['下周一', '2026-11-16'],
    ]);
  });
  it('周日的下周一是明天，周一的下周一是 7 天后', () => {
    expect(nextMonday(dayjs('2026-11-15')).format('MM-DD')).toBe('11-16');
    expect(nextMonday(dayjs('2026-11-16')).format('MM-DD')).toBe('11-23');
  });
});
