/** 来源渠道，与客户来源渠道同一套码值 */
export const CHANNEL_OPTIONS = [
  '阿里巴巴国际站',
  '中国制造网',
  '独立站',
  '展会',
  '社交媒体',
  '老客户转介绍',
  '主动开发',
  '其他',
].map((label, i) => ({ value: i + 1, label }));

export const channelLabel = (v?: number) =>
  CHANNEL_OPTIONS.find((o) => o.value === v)?.label ?? '未设置';

/** 阶段类别 */
export const CATEGORY_ACTIVE = 1;
export const CATEGORY_WON = 2;
export const CATEGORY_LOST = 3;
export const CATEGORY_INVALID = 4;

export const INVALID_REASONS = [
  { value: 1, label: '同行套价' },
  { value: 2, label: '垃圾询盘' },
  { value: 3, label: '联系不上' },
  { value: 4, label: '需求不符' },
  { value: 9, label: '其他' },
];

export const LOST_REASONS = [
  { value: 11, label: '价格' },
  { value: 12, label: '交期' },
  { value: 13, label: '无货源' },
  { value: 14, label: '客户取消' },
  { value: 15, label: '选择了竞争对手' },
  { value: 19, label: '其他' },
];

/** 需求附件：图片与 Excel，单个 ≤10MB，每条商机 ≤10 个 */
export const ATTACHMENT_ACCEPT =
  '.jpg,.jpeg,.png,.xlsx,.xls,.csv,image/jpeg,image/png';
export const ATTACHMENT_MAX_BYTES = 10 * 1024 * 1024;
export const MAX_ATTACHMENTS = 10;

export const LIST_PATH = '/crm/opportunities';
