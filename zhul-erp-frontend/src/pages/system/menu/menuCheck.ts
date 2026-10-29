import type { MenuItem } from './service';

/**
 * 权限树按「严格模式」勾选，自己维护父子联动：勾选时连同全部下级和上级一起勾上，
 * 取消时连同全部下级一起取消，没有任何下级的目录也一并取消。
 * 不用 antd 自带的联动，是因为它在回显时会把「目录已授权」理解成「目录下全部授权」，
 * 再保存一次就把没勾的子菜单也存进去了；也不会把半选的上级目录存下来。
 * 角色权限、租户套餐共用。
 */
export type MenuIndex = {
  parentOf: Map<number, number>;
  childrenOf: Map<number, number[]>;
  typeOf: Map<number, number>;
};

export const indexMenuTree = (list: MenuItem[]): MenuIndex => {
  const index: MenuIndex = {
    parentOf: new Map(),
    childrenOf: new Map(),
    typeOf: new Map(),
  };
  const walk = (nodes: MenuItem[], pid: number) =>
    nodes.forEach((n) => {
      index.parentOf.set(n.id, pid);
      index.typeOf.set(n.id, n.type);
      index.childrenOf.set(
        n.id,
        (n.children ?? []).map((c) => c.id),
      );
      if (n.children) walk(n.children, n.id);
    });
  walk(list, 0);
  return index;
};

const descendantsOf = (index: MenuIndex, id: number): number[] => {
  const result: number[] = [];
  const walk = (cur: number) =>
    (index.childrenOf.get(cur) ?? []).forEach((c) => {
      result.push(c);
      walk(c);
    });
  walk(id);
  return result;
};

const ancestorsOf = (index: MenuIndex, id: number): number[] => {
  const result: number[] = [];
  let cur = index.parentOf.get(id);
  while (cur) {
    result.push(cur);
    cur = index.parentOf.get(cur);
  }
  return result;
};

/** 已勾选但还有下级没勾的，显示为半选 */
export const splitChecked = (index: MenuIndex, keys: number[]) => {
  const set = new Set(keys);
  const checked: number[] = [];
  const halfChecked: number[] = [];
  keys.forEach((k) => {
    if (descendantsOf(index, k).every((d) => set.has(d))) checked.push(k);
    else halfChecked.push(k);
  });
  return { checked, halfChecked };
};

export const toggleMenuKey = (
  index: MenuIndex,
  keys: number[],
  id: number,
  check: boolean,
): number[] => {
  const set = new Set(keys);
  if (check) {
    for (const k of [
      id,
      ...descendantsOf(index, id),
      ...ancestorsOf(index, id),
    ]) {
      set.add(k);
    }
  } else {
    for (const k of [id, ...descendantsOf(index, id)]) {
      set.delete(k);
    }
    // 目录下一个都不剩时目录也取消；菜单下的按钮全取消时菜单本身保留（只读访问）
    ancestorsOf(index, id).forEach((a) => {
      if (
        index.typeOf.get(a) === 1 &&
        !descendantsOf(index, a).some((d) => set.has(d))
      ) {
        set.delete(a);
      }
    });
  }
  return [...set];
};
