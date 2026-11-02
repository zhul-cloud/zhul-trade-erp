import dayjs from 'dayjs';
import type { AfterSales } from './service';

/** 各等级时限（与后端 AfterSalesConstants.SLA_RULES 一致，按售后流程 SOP 固定） */
export const SLA_TEXT: Record<string, string> = {
  S1: 'S1：首次 1 小时，之后每 2 小时更新客户（自然时间）。',
  S2: 'S2：首次 2 个工作小时，之后每个工作日更新客户。',
  PENDING: '待确认按 S2：首次 2 个工作小时，之后每个工作日更新客户。',
  S3: 'S3：首次 1 个工作日，之后每 2 个工作日更新客户。',
  S4: 'S4：首次 1 个工作日，之后每 3 个工作日更新客户。',
  S5: 'S5：首次 1 个工作日，之后按手填的下次更新时间。',
};

export const WORK_HOURS_TEXT =
  '工作时间周一至周五 9:00–18:00，不考虑节假日；1 个工作日 = 9 个工作小时。';

/** 时长：x 天 y 小时 / x 小时 y 分 / x 分钟 */
export const spanText = (minutes: number) => {
  const m = Math.max(0, Math.round(minutes));
  if (m < 60) return `${m} 分钟`;
  const h = Math.floor(m / 60);
  if (h < 24) return m % 60 ? `${h} 小时 ${m % 60} 分` : `${h} 小时`;
  return h % 24 ? `${Math.floor(h / 24)} 天 ${h % 24} 小时` : `${h / 24} 天`;
};

/** 「已逾期 2 小时 15 分」/「还剩 40 分钟」 */
export const dueLeftText = (a: Pick<AfterSales, 'dueAt' | 'dueState'>) => {
  if (!a.dueAt) return '';
  const diff = dayjs(a.dueAt).diff(dayjs(), 'minute');
  return a.dueState === 'OVERDUE'
    ? `已逾期 ${spanText(-diff)}`
    : `还剩 ${spanText(diff)}`;
};

export const DUE_TONE = {
  OVERDUE: 'red',
  SOON: 'orange',
  NORMAL: 'gray',
} as const;
