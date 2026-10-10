import * as AntdIcons from '@ant-design/icons';
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
 * 「菜单管理」里 lightIcon 是自由文本（如 shopping / shopping-bag / fileText），
 * 不是受限枚举。这里按 antd 图标的命名规律（PascalCase + Outlined/Filled/TwoTone）
 * 去 @ant-design/icons 里找对应组件；找不到就不渲染图标，绝不把原始英文字符串
 * 直接显示出来——ProLayout 自带的 getIcon() 遇到普通字符串时就是这么处理的，
 * 这正是菜单名称前面冒出一截英文单词的原因。
 */
function resolveMenuIcon(raw?: string): React.ReactNode {
  if (!raw) return undefined;
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
