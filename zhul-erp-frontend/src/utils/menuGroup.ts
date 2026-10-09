/**
 * 页面路径 → 侧边栏分组名（面包屑第一级）。菜单按部门分组后，页面路径保留原样（书签与接口权限不变），
 * 所以分组不能从路径前缀直接推出来，统一在这里对照；与后端 resource 表的分组保持一致。
 */
const GROUPS: [string, string][] = [
  ['/finance/', '财务管理'],
  ['/quotation/pricing', '业务设置'],
  ['/system/exchange-rate', '业务设置'],
  ['/system/bank-account', '业务设置'],
  ['/system/document-template', '业务设置'],
  ['/system/document-numbering', '业务设置'],
  ['/inquiry/sourcing-board', '采购管理'],
  ['/inquiry/my-tasks', '采购管理'],
  ['/inquiry/part-time-board', '采购管理'],
  ['/inquiry/price-history', '采购管理'],
  ['/supplier/', '采购管理'],
  ['/purchase/', '采购管理'],
  ['/crm/', '业务管理'],
  ['/customer/', '业务管理'],
  ['/inquiry/', '业务管理'],
  ['/quotation/', '业务管理'],
  ['/sales/', '业务管理'],
  ['/product/', '商品资料'],
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
