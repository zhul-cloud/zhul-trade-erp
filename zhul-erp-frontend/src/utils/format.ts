import dayjs from 'dayjs';

const numberFormatter = new Intl.NumberFormat('en-US');

/**
 * Format a number with thousand separators.
 * Replaces numeral(val).format('0,0')
 */
export const formatNumber = (val: number | string): string => {
  const parsed = Number(val);
  return Number.isFinite(parsed) ? numberFormatter.format(parsed) : '';
};

/**
 * Format a number as yuan currency string.
 * Replaces `¥ ${numeral(val).format('0,0')}`
 */
export const formatYuan = (val: number | string) => `¥ ${formatNumber(val)}`;

const amountFormatter = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/**
 * 金额统一显示为「币种代码 金额」：两位小数、千分位，如 CNY 4,120.00、USD -85.56；空值显示「—」。
 * 全系统（询盘、报价、以后的销售单 / 采购单 / PI）都用它，不再写 ¥ / $ 符号。
 */
export const formatAmount = (
  value: number | string | null | undefined,
  currency = 'CNY',
): string => {
  if (value === null || value === undefined || value === '') return '—';
  const n = Number(value);
  if (!Number.isFinite(n)) return '—';
  return `${currency} ${amountFormatter.format(n)}`;
};

/**
 * Format a datetime as yyyy-MM-dd HH:mm:ss.
 * Unified display format for create_time / update_time across all list pages.
 */
export const formatDateTime = (val?: string | number | Date | null): string =>
  val ? dayjs(val).format('YYYY-MM-DD HH:mm:ss') : '—';
