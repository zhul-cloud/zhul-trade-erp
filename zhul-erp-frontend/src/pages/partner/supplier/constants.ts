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

export const SUPPLIER_CODE_PATTERN = /^[A-Za-z0-9]{1,20}$/;
export const CREDIT_CODE_PATTERN = /^[0-9A-Za-z]{18}$/;
export const BANK_ACCOUNT_PATTERN = /^\d{1,30}$/;

export const DISABLE_CONFIRM_TEXT =
  '禁用后该供应商将无法在新的询价、采购单中被选择，已有单据不受影响。';
