/** 码值与后端 SupplierConstants、V1.2.8 迁移注释一致；0 为"未设置"（存量数据、询盘内联创建） */
export const SUPPLIER_TYPE_OPTIONS = [
  { value: 1, label: '生产商' },
  { value: 2, label: '经销商' },
  { value: 3, label: '服务商' },
  { value: 4, label: '代理商' },
  { value: 5, label: '其他' },
];

export const INDUSTRY_OPTIONS = [
  { value: 1, label: '制造业' },
  { value: 2, label: '原材料' },
  { value: 3, label: '信息技术' },
  { value: 4, label: '物流运输' },
  { value: 5, label: '金融服务' },
  { value: 6, label: '其他' },
];

/** 类型标签配色，按原型：生产商蓝、经销商青、服务商橙、代理商绿，其他和未设置灰 */
export type SupplierTypeTone = 'accent' | 'cyan' | 'orange' | 'green' | 'gray';
export const SUPPLIER_TYPE_TONE: Record<number, SupplierTypeTone> = {
  1: 'accent',
  2: 'cyan',
  3: 'orange',
  4: 'green',
  5: 'gray',
};

export const labelOf = (
  options: { value: number; label: string }[],
  value?: number,
): string | undefined => options.find((o) => o.value === value)?.label;

export const CREDIT_CODE_PATTERN = /^[0-9A-Za-z]{18}$/;
/** 收款账号：仅数字，8 到 30 位（与后端一致） */
export const ACCOUNT_NO_PATTERN = /^\d{8,30}$/;
export const PHONE_PATTERN = /^1\d{10}$/;
export const ID_NO_PATTERN = /^\d{17}[\dXx]$/;

/** 收款账户类型，码值与 V1.2.11 迁移注释一致 */
export const ACCOUNT_CORPORATE = 1;
export const ACCOUNT_PERSONAL = 2;
export const MAX_ACCOUNTS = 10;

/** 附件类型，码值与 V1.2.11 迁移注释一致；按此顺序分行展示 */
export const ATTACHMENT_CATEGORIES = [
  { value: 1, label: '营业执照' },
  { value: 2, label: '开户许可证' },
  { value: 3, label: '资质证书' },
  { value: 4, label: '合同' },
  { value: 5, label: '其他' },
];
export const BUSINESS_LICENSE = 1;
export const MAX_ATTACHMENTS = 20;
export const ATTACHMENT_MAX_BYTES = 10 * 1024 * 1024;
export const ATTACHMENT_ACCEPT =
  '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png';

export const DISABLE_CONFIRM_TEXT =
  '禁用后该供应商将无法在新的询价、采购单中被选择，已有单据不受影响。';
