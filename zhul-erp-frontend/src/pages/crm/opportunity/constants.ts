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

/** 输单原因：按 SOP 卡点字典排列，加无货源、客户取消、其他（与后端 OpportunityConstants.LOST_REASONS 一致） */
export const LOST_REASONS = [
  { value: 11, label: '价格' },
  { value: 16, label: '技术' },
  { value: 17, label: '付款' },
  { value: 12, label: '交期' },
  { value: 18, label: '信任' },
  { value: 20, label: '预算' },
  { value: 15, label: '选择了竞争对手' },
  { value: 21, label: '内部审批' },
  { value: 22, label: '项目延期' },
  { value: 23, label: '无回复' },
  { value: 13, label: '无货源' },
  { value: 14, label: '客户取消' },
  { value: 19, label: '其他' },
];

/** 输单后转入培育的默认天数（与系统参数 sales.followup.nurture.default-days 默认值一致） */
export const NURTURE_DEFAULT_DAYS = 60;

/** 需求附件：图片与 Excel，单个 ≤10MB，每条商机 ≤10 个 */
export const ATTACHMENT_ACCEPT =
  '.jpg,.jpeg,.png,.xlsx,.xls,.csv,image/jpeg,image/png';
export const ATTACHMENT_MAX_BYTES = 10 * 1024 * 1024;
export const MAX_ATTACHMENTS = 10;

export const LIST_PATH = '/crm/opportunities';

/** 来源渠道「阿里巴巴国际站」才有阿里等级 */
export const CHANNEL_ALIBABA = 1;
export const ALI_LEVEL_OPTIONS = ['L0', 'L1', 'L1+', 'L2', 'L3', 'L4'].map(
  (v) => ({ value: v, label: v }),
);
