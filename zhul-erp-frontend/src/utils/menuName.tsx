import { Link, useLocation, useModel } from '@umijs/max';
import React, { useMemo } from 'react';

/**
 * 页面标题、面包屑、工作台入口的名称都取「菜单管理」的菜单树（登录时取到的 initialState.menuTree），
 * 不在页面代码里另写一份：在菜单管理改名后刷新页面，侧边栏、标题、面包屑一起变。
 * 菜单按部门分组后页面地址保留原样（如汇率设置 /system/exchange-rate 在「业务设置」下），所以分组不能从地址推出来，
 * 按菜单树里的上下级取。
 */
interface MenuNode {
  name?: string;
  path?: string;
  type?: number;
  isHidden?: number;
  status?: number;
  children?: MenuNode[];
}

export interface MenuHit {
  /** 页面菜单的名称，如「收款管理」 */
  name: string;
  /** 所属分组的名称（面包屑第一级），如「财务管理」；工作台这类一级页面为空 */
  group: string;
}

/**
 * 页面地址所属的菜单：地址与菜单相同，或是菜单地址的下级（详情、新建、编辑页），取地址最长的一个。
 * 只认页面菜单（不含分组与按钮），隐藏或停用的菜单不算。
 */
export function findMenu(
  tree: MenuNode[],
  pathname: string,
): MenuHit | undefined {
  let best: { hit: MenuHit; len: number } | undefined;
  const walk = (nodes: MenuNode[], group: string) => {
    for (const n of nodes) {
      if (n.type === 3 || n.isHidden === 1 || n.status === 0) continue;
      const p = n.path;
      if (
        n.type === 2 &&
        p &&
        (pathname === p || pathname.startsWith(`${p}/`)) &&
        p.length > (best?.len ?? 0)
      ) {
        best = { hit: { name: n.name ?? '', group }, len: p.length };
      }
      if (n.children?.length) {
        walk(n.children, n.type === 1 ? (n.name ?? '') : group);
      }
    }
  };
  walk(tree, '');
  return best?.hit;
}

/** 某个地址（默认当前页面）所属的菜单名称与分组；菜单树里没有时为 undefined */
export const useMenu = (path?: string): MenuHit | undefined => {
  const { pathname } = useLocation();
  const { initialState } = useModel('@@initialState');
  const tree = initialState?.menuTree;
  const target = path ?? pathname;
  return useMemo(() => findMenu(tree ?? [], target), [tree, target]);
};

/** 某个地址（默认当前页面）的菜单名称，用在标题文字里，如「营销任务 · 型号」 */
export const MenuName: React.FC<{ path?: string }> = ({ path }) => (
  <>{useMenu(path)?.name}</>
);

/** 指向某个列表页的链接，文字为该页的菜单名称（详情页面包屑返回列表、工作台入口用） */
export const MenuLink: React.FC<{ to: string; children?: React.ReactNode }> = ({
  to,
  children,
}) => {
  const menu = useMenu(to);
  return (
    <Link to={to}>
      {menu?.name}
      {children}
    </Link>
  );
};
