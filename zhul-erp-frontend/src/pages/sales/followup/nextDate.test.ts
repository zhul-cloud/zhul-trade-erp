import dayjs from 'dayjs';
import {
  adjustSuggest,
  dateShortcuts,
  nurtureCandidate,
  quickDate,
  suggestOf,
} from './nextDate';
import { type FollowupItem, SITUATION } from './service';

const TODAY = dayjs('2026-11-05');
const item = (p: Partial<FollowupItem>) =>
  ({ id: 1, board: 2, stageCode: 'S4', stageGroup: 2, ...p }) as FollowupItem;

describe('suggestOf', () => {
  it('取最早的推荐日期与理由', () => {
    const s = suggestOf(
      [
        item({ suggestDate: '2026-11-12', suggestReason: '报价后第 14 天' }),
        item({ suggestDate: '2026-11-08', suggestReason: '报价后第 7 天' }),
      ],
      TODAY,
    );
    expect(s.date.format('YYYY-MM-DD')).toBe('2026-11-08');
    expect(s.reason).toBe('报价后第 7 天');
  });
  it('没有推荐日期时 3 天后', () => {
    expect(suggestOf([item({})], TODAY).date.format('YYYY-MM-DD')).toBe(
      '2026-11-08',
    );
  });
});

describe('adjustSuggest', () => {
  const base = { date: dayjs('2026-11-19'), reason: '报价后第 14 天' };
  it('在谈条件：明天', () => {
    const s = adjustSuggest(base, SITUATION.NEGOTIATING, TODAY);
    expect(s.date.format('YYYY-MM-DD')).toBe('2026-11-06');
  });
  it('没回复：最晚 3 天后', () => {
    expect(
      adjustSuggest(base, SITUATION.NO_REPLY, TODAY).date.format('YYYY-MM-DD'),
    ).toBe('2026-11-08');
    const near = { date: dayjs('2026-11-07'), reason: '报价后第 7 天' };
    expect(
      adjustSuggest(near, SITUATION.NO_REPLY, TODAY).date.format('YYYY-MM-DD'),
    ).toBe('2026-11-07');
  });
  it('只有反馈：不变', () => {
    expect(adjustSuggest(base, SITUATION.FEEDBACK, TODAY)).toBe(base);
  });
});

describe('quickDate', () => {
  const s = { date: dayjs('2026-11-19'), reason: '' };
  it('按结果修正', () => {
    expect(quickDate('NORMAL', s, TODAY).format('YYYY-MM-DD')).toBe(
      '2026-11-19',
    );
    expect(quickDate('TOMORROW', s, TODAY).format('YYYY-MM-DD')).toBe(
      '2026-11-06',
    );
    expect(quickDate('NO_REPLY', s, TODAY).format('YYYY-MM-DD')).toBe(
      '2026-11-08',
    );
  });
});

describe('dateShortcuts / nurtureCandidate', () => {
  it('含长期选项', () => {
    expect(dateShortcuts(TODAY).map((d) => d.label)).toEqual([
      '今天',
      '明天',
      '3 天后',
      '下周',
      '2 周后',
      '1 个月后',
      '3 个月后',
    ]);
  });
  it('报价后的单据选 30 天以后才提示转培育', () => {
    const q = item({});
    expect(nurtureCandidate([q], TODAY.add(29, 'day'), TODAY)).toBeUndefined();
    expect(nurtureCandidate([q], TODAY.add(30, 'day'), TODAY)).toBe(q);
    expect(
      nurtureCandidate(
        [item({ stageGroup: 3, stageCode: 'S7' })],
        TODAY.add(90, 'day'),
        TODAY,
      ),
    ).toBeUndefined();
  });
});
