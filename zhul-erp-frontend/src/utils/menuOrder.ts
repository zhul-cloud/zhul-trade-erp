// lint-ui-ignore antd-icon: 后端菜单里存量的 antd 图标名，按名字兜底查找
import * as AntdIcons from '@ant-design/icons';
import type { LucideIcon } from 'lucide-react';
import * as L from 'lucide-react';
import React from 'react';

interface BackendMenuNode {
  path?: string;
  name?: string;
  type?: number;
  sort?: number;
  lightIcon?: string;
  isHidden?: number;
  children?: BackendMenuNode[];
}

export interface ProLayoutMenuItem {
  path: string;
  name: string;
  icon?: React.ReactNode;
  children?: ProLayoutMenuItem[];
}

const iconComponents = AntdIcons as unknown as Record<
  string,
  React.ComponentType | undefined
>;

function toPascalCase(raw: string): string {
  return raw
    .replace(/[-_\s]+(.)/g, (_, c: string) => c.toUpperCase())
    .replace(/^(.)/, (c) => c.toUpperCase());
}

/**
 * 菜单图标统一用 lucide 线性图标（外壳原型 F1；与参考站 PrimeDash 的 mdi outline 同一类：
 * 20px、线宽 2、造型简洁）。「菜单管理」里存的是 antd 图标名，这里按名称换成对应的 lucide 图标；
 * 键为去掉连字符、下划线后的小写名，兼容 file-text / fileText 两种写法。
 */
const LUCIDE_MENU_ICONS: Record<string, LucideIcon> = {
  // 顶级分组（侧栏、收起后的图标栏里看得到的）
  dashboard: L.LayoutDashboard,
  solution: L.Briefcase,
  shoppingcart: L.ShoppingCart,
  deploymentunit: L.Network,
  inbox: L.Warehouse,
  global: L.Truck,
  globe: L.Truck,
  wallet: L.Wallet,
  shopping: L.Package,
  fund: L.Megaphone,
  barchart: L.ChartColumn,
  control: L.SlidersHorizontal,
  setting: L.Settings,
  cluster: L.Building2,
  useradd: L.UserPlus,
  mail: L.Mail,
  filetext: L.FileText,
  accountbook: L.NotebookText,
  shop: L.Store,
  // 其余在用的名称
  apartment: L.Network,
  appstore: L.LayoutGrid,
  audit: L.FileCheck,
  bank: L.Landmark,
  bell: L.Bell,
  book: L.Book,
  camera: L.Camera,
  car: L.Car,
  carryout: L.CalendarCheck,
  clockcircle: L.Clock,
  container: L.Container,
  database: L.Database,
  export: L.ExternalLink,
  fall: L.TrendingDown,
  filedone: L.FileCheck,
  fileexcel: L.FileSpreadsheet,
  history: L.History,
  idcard: L.IdCard,
  import: L.Download,
  lifebuoy: L.LifeBuoy,
  linechart: L.ChartLine,
  login: L.LogIn,
  menu: L.Menu,
  moneycollect: L.HandCoins,
  number: L.Hash,
  packageopen: L.PackageOpen,
  percentage: L.Percent,
  picture: L.Image,
  piechart: L.ChartPie,
  profile: L.ClipboardList,
  robot: L.Bot,
  rollback: L.Undo2,
  schedule: L.CalendarClock,
  send: L.Send,
  swap: L.ArrowLeftRight,
  tag: L.Tag,
  team: L.Users,
  tool: L.Wrench,
  transaction: L.ArrowRightLeft,
  undo: L.Undo2,
  unorderedlist: L.List,
  upload: L.Upload,
  user: L.User,
};

/**
 * 「菜单管理」里 lightIcon 是自由文本（如 shopping / shopping-bag / fileText），不是受限枚举。
 * 先按上表换成 lucide 图标；表里没有的，再按 antd 图标的命名规律（PascalCase + Outlined/Filled/TwoTone）
 * 去 @ant-design/icons 里找；都找不到就不渲染图标，绝不把原始英文字符串直接显示出来——
 * ProLayout 自带的 getIcon() 遇到普通字符串时就是这么处理的，这正是菜单名称前面冒出一截英文单词的原因。
 */
function resolveMenuIcon(raw?: string): React.ReactNode {
  if (!raw) return undefined;
  const lucide = LUCIDE_MENU_ICONS[raw.replace(/[-_\s]/g, '').toLowerCase()];
  if (lucide) return React.createElement(lucide, { size: 20, strokeWidth: 2 });
  const pascal = toPascalCase(raw);
  const candidates = [
    pascal,
    `${pascal}Outlined`,
    `${pascal}Filled`,
    `${pascal}TwoTone`,
  ];
  for (const key of candidates) {
    const Icon = iconComponents[key];
    if (Icon) return React.createElement(Icon);
  }
  return undefined;
}

/**
 * 把「菜单管理」的资源树直接转成 ProLayout 认识的菜单数据：名称、图标、顺序、层级
 * 全部来自后端，不再从 routes.ts 派生再打补丁——避免像 applyMenuMeta 那样，把 umi
 * 给父路由自动生成的、本该隐藏的重定向占位节点也当成一条真实菜单项处理。
 * type=3（按钮）不会出现在侧边栏；is_hidden=1（菜单管理里手动隐藏）的节点整棵子树跳过；
 * allowedPaths 之外的节点也整棵子树跳过——用当前用户的有效权限（permissions）过滤，
 * 跟后端算出来的实际可访问范围保持一致。
 */
export function toProLayoutMenu(
  nodes: BackendMenuNode[],
  allowedPaths: Set<string>,
): ProLayoutMenuItem[] {
  return nodes
    .filter(
      (n) =>
        n.type !== 3 && n.isHidden !== 1 && n.path && allowedPaths.has(n.path),
    )
    .slice()
    .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
    .map((n) => ({
      path: n.type === 1 ? groupKey(n.path as string) : (n.path as string),
      name: n.name ?? '',
      icon: resolveMenuIcon(n.lightIcon),
      children: n.children?.length
        ? toProLayoutMenu(n.children, allowedPaths)
        : undefined,
    }));
}

/**
 * 分组（type=1）在 ProLayout 里用的 key。ProLayout 按「当前地址以菜单 path 开头」决定展开哪些分组，
 * 而菜单按部门分组后页面地址沿用原前缀（如汇率 /system/exchange-rate 在「业务设置」下），
 * 分组若用 /system 当 key，会把「系统管理」也一起展开；分组本身不是页面，换成不会和任何页面地址重叠的 key。
 */
export const groupKey = (path: string) => `/__group${path}`;

/** 菜单里排在最前的可访问页面（深度优先取第一个叶子），用作没有工作台权限时的首页 */
export function firstMenuPath(menu: ProLayoutMenuItem[]): string | undefined {
  for (const m of menu) {
    if (m.children?.length) {
      const p = firstMenuPath(m.children);
      if (p) return p;
    } else {
      return m.path;
    }
  }
  return undefined;
}
