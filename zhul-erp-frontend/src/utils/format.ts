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

/**
 * Format a datetime as yyyy-MM-dd HH:mm:ss.
 * Unified display format for create_time / update_time across all list pages.
 */
export const formatDateTime = (val?: string | number | Date | null): string =>
  val ? dayjs(val).format('YYYY-MM-DD HH:mm:ss') : '—';
