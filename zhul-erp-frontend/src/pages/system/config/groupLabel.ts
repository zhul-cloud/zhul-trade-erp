/**
 * 配置分组：一律存小写英文代码，页面统一显示为 4 个字的中文名（与菜单「统一 4 个字」一致）。
 * 内置配置的分组 = 配置键第一段的模块代码；sys. 开头的系统保留键按用途归组（如 appearance）。
 * 新增分组时在这里补中文名；对照表里没有的代码原样显示。
 */
const GROUP_LABELS: Record<string, string> = {
  inquiry: '询价设置',
  quotation: '报价设置',
  document: '单据设置',
  sales: '销售设置',
  purchase: '采购设置',
  product: '商品设置',
  finance: '财务设置',
  appearance: '外观设置',
  basic: '基础设置',
  security: '安全策略',
  mail: '邮件设置',
  custom: '自定义项',
};

export const groupLabel = (group: string): string =>
  GROUP_LABELS[group] ?? group;

/** 新增自定义配置可选的分组代码；外观设置只由平台维护内置项，不在这里出现 */
export const GROUP_OPTIONS = ['basic', 'security', 'mail', 'custom'];
