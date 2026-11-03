import dayjs, { type Dayjs } from 'dayjs';

/** 单据列表日期筛选的默认范围：最近 3 个月（含今天）。清空日期即看全部 */
export const recentRange = (): [Dayjs, Dayjs] => [
  dayjs().subtract(3, 'month').startOf('day'),
  dayjs().startOf('day'),
];
