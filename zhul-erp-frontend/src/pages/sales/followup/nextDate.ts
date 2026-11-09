import dayjs, { type Dayjs } from 'dayjs';
import {
  BOARD_SALES,
  type FollowupItem,
  GROUP,
  type QuickResult,
  SITUATION,
} from './service';

/** 没回复时最晚隔几天再跟 */
export const NO_REPLY_MAX_DAYS = 3;
/** 报价后的单据选这么多天以后：提示可以直接转培育 */
export const NURTURE_HINT_DAYS = 30;
/** 没有推荐日期时的默认间隔 */
const FALLBACK_DAYS = 3;

export interface Suggest {
  date: Dayjs;
  reason: string;
}

/** 几张单据一起记：取各单据推荐日期中最早的一个 */
export const suggestOf = (items: FollowupItem[], today: Dayjs): Suggest => {
  let best: Suggest | undefined;
  for (const i of items) {
    if (!i.suggestDate) continue;
    const d = dayjs(i.suggestDate).startOf('day');
    if (!best || d.isBefore(best.date))
      best = { date: d, reason: i.suggestReason ?? '' };
  }
  return (
    best ?? {
      date: today.add(FALLBACK_DAYS, 'day'),
      reason: `${FALLBACK_DAYS} 天后`,
    }
  );
};

/** 客户情况修正：在谈条件改为明天；没回复取推荐日期与 3 天后中较早的一个 */
export const adjustSuggest = (
  s: Suggest,
  situation: number,
  today: Dayjs,
): Suggest => {
  if (situation & SITUATION.NEGOTIATING)
    return { date: today.add(1, 'day'), reason: '在谈条件，明天再跟' };
  if (situation & SITUATION.NO_REPLY) {
    const cap = today.add(NO_REPLY_MAX_DAYS, 'day');
    return s.date.isAfter(cap)
      ? { date: cap, reason: `没回复，${NO_REPLY_MAX_DAYS} 天后再提醒` }
      : { date: s.date, reason: `没回复，${s.reason}` };
  }
  return s;
};

/** 快速跟进结果的日期：与后端 FollowupQuickService.nextDate 一致 */
export const quickDate = (
  rule: QuickResult['dateRule'],
  s: Suggest,
  today: Dayjs,
): Dayjs => {
  if (rule === 'TOMORROW') return today.add(1, 'day');
  if (rule === 'NO_REPLY') {
    const cap = today.add(NO_REPLY_MAX_DAYS, 'day');
    return s.date.isAfter(cap) ? cap : s.date;
  }
  return s.date;
};

/** 「什么时候」的快捷选项 */
export const dateShortcuts = (today: Dayjs) => [
  { label: '今天', value: today },
  { label: '明天', value: today.add(1, 'day') },
  { label: '3 天后', value: today.add(3, 'day') },
  { label: '下周', value: today.add((8 - today.day()) % 7 || 7, 'day') },
  { label: '2 周后', value: today.add(14, 'day') },
  { label: '1 个月后', value: today.add(1, 'month') },
  { label: '3 个月后', value: today.add(3, 'month') },
];

/** 选的日期在 30 天及以后、且有报价后的单据：可以直接转培育的那张单据 */
export const nurtureCandidate = (
  items: FollowupItem[],
  date: Dayjs | null,
  today: Dayjs,
) =>
  date && date.diff(today, 'day') >= NURTURE_HINT_DAYS
    ? items.find(
        (i) => i.board === BOARD_SALES && i.stageGroup === GROUP.AFTER_QUOTE,
      )
    : undefined;
