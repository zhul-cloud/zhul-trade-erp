/** 文字报价的「报价货况」「报价货期」（spec quotation/quotation「文字报价」）：只影响本次生成的文字 */

/** 报价货期「按采购货期顺延」 */
export const LEAD_SHIFT = 'shift';
/** 报价货期「按采购货期」：原样取采购回价的货期 */
export const LEAD_PURCHASE = 'purchase';
/** 货况字典「全新原装」 */
export const CONDITION_NEW = 1;

export type LeadChoice = number | typeof LEAD_SHIFT | typeof LEAD_PURCHASE;

export interface TextOptions {
  condition: number;
  leadTime: LeadChoice;
}

/** 每次打开弹窗都恢复默认 */
export const DEFAULT_TEXT_OPTIONS: TextOptions = {
  condition: CONDITION_NEW,
  leadTime: LEAD_SHIFT,
};

/** 文字报价接口的查询参数 */
export const textQuery = (o: TextOptions, version?: number) => ({
  ...(version ? { version } : {}),
  condition: o.condition,
  leadTime: String(o.leadTime),
});

/** 报价货期下拉：按采购货期顺延、按采购货期，其余为货期字典的启用项 */
export const leadChoices = (
  dict: { value: number; label: string }[],
): { value: LeadChoice; label: string }[] => [
  { value: LEAD_SHIFT, label: '按采购货期顺延' },
  { value: LEAD_PURCHASE, label: '按采购货期' },
  ...dict,
];
