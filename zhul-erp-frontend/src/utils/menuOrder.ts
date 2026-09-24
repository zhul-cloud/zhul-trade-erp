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
      path: n.path as string,
      name: n.name ?? '',
      icon: resolveMenuIcon(n.lightIcon),
      children: n.children?.length
        ? toProLayoutMenu(n.children, allowedPaths)
        : undefined,
    }));
}
