/**
 * 页面路径 → 侧边栏分组名（面包屑第一级）。菜单按部门分组后，页面路径保留原样（书签与接口权限不变），
 * 所以分组不能从路径前缀直接推出来，统一在这里对照；与后端 resource 表的分组保持一致。
 */
const GROUPS: [string, string][] = [
  // 只读统计统一归「统计分析」，要排在各业务路径前缀之前
  ['/sales/stats', '统计分析'],
  ['/inquiry/sourcing-stats', '统计分析'],
  ['/purchase/bargain-stats', '统计分析'],
  ['/finance/profit-stats', '统计分析'],
  ['/finance/operating-report', '统计分析'],
  ['/crm/opportunity-stats', '统计分析'],
  // 商品候选、资料任务是运营人员处理的任务，归运营管理
  ['/product/candidates', '运营管理'],
  ['/product/fact-tasks', '运营管理'],
  ['/finance/', '财务管理'],
  ['/quotation/pricing', '业务设置'],
  ['/system/exchange-rate', '业务设置'],
  ['/system/bank-account', '业务设置'],
  ['/system/document-template', '业务设置'],
  ['/system/document-numbering', '业务设置'],
  ['/system/transit-times', '业务设置'],
  ['/inquiry/sourcing-board', '采购管理'],
  ['/inquiry/my-tasks', '采购管理'],
  ['/inquiry/price-history', '采购管理'],
  ['/supplier/', '采购管理'],
  ['/purchase/', '采购管理'],
  ['/warehouse/', '仓库管理'],
  ['/logistics/', '物流管理'],
  ['/crm/', '销售管理'],
  ['/customer/', '销售管理'],
  ['/inquiry/', '销售管理'],
  ['/quotation/', '销售管理'],
  ['/sales/', '销售管理'],
  ['/product/', '商品管理'],
  ['/operations/', '运营管理'],
  ['/system/', '系统管理'],
  ['/tenant/', '租户管理'],
];

export const menuGroupOf = (pathname: string): string => {
  const path = pathname.endsWith('/') ? pathname : `${pathname}/`;
  const hit = GROUPS.find(([prefix]) =>
    path.startsWith(prefix.endsWith('/') ? prefix : `${prefix}/`),
  );
  return hit?.[1] ?? '';
};
