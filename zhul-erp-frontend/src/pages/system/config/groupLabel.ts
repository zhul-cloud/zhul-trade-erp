/**
 * 配置分组：一律存小写英文代码，页面统一显示为 2 个字的中文名，不带「设置」后缀。
 * 内置配置的分组 = 配置键第一段的模块代码；sys. 开头的系统保留键按用途归组（如 appearance）。
 * 新增分组时在这里补中文名；对照表里没有的代码原样显示。
 */
const GROUP_LABELS: Record<string, string> = {
  inquiry: '询价',
  quotation: '报价',
  document: '单据',
  sales: '销售',
  purchase: '采购',
  product: '商品',
  finance: '财务',
  appearance: '外观',
  basic: '基础',
  security: '安全',
  mail: '邮件',
  custom: '其他',
};

export const groupLabel = (group: string): string =>
  GROUP_LABELS[group] ?? group;

/** 新增自定义配置可选的分组代码；外观只由平台维护内置项，不在这里出现 */
export const GROUP_OPTIONS = ['basic', 'security', 'mail', 'custom'];
