import { createContext, useContext, useEffect, useRef, useState } from 'react';
import { tabsActions } from './store';

/** 页面所在的标签与栈条目；不在标签页框架里（如登录页）时为默认值 */
export interface PaneInfo {
  entryKey?: string;
  menuPath?: string;
  /** 页面当前可见（所在标签是当前标签，且是栈顶） */
  active: boolean;
}

export const PaneContext = createContext<PaneInfo>({ active: true });

/**
 * 登记页面标题：返回入口显示「返回 {title}」，标签显示 tabLabel（没有时用 title）。
 * 例：usePageTitle(`销售订单 ${o.soNo}`, o.soNo)。没加载完时传 undefined。
 */
export const usePageTitle = (title?: string, tabLabel?: string) => {
  const { entryKey } = useContext(PaneContext);
  useEffect(() => {
    if (entryKey && title) tabsActions.setTitle(entryKey, title, tabLabel);
  }, [entryKey, title, tabLabel]);
};

/** 每个页面里登记了未保存修改的地方（页面表单、附件卡备注等），任一处未保存，这一页就算未保存 */
const dirtySources = new Map<string, Set<object>>();

/**
 * 登记未保存修改：点返回离开这页、刷新或关闭所在标签时先确认；超过上限自动关标签时跳过。
 * 同一页面可以有多处登记，互不覆盖。
 */
export const useTabDirty = (dirty: boolean) => {
  const { entryKey } = useContext(PaneContext);
  const [self] = useState(() => ({}));
  useEffect(() => {
    if (!entryKey || !dirty) return undefined;
    const sources = dirtySources.get(entryKey) ?? new Set<object>();
    dirtySources.set(entryKey, sources);
    sources.add(self);
    tabsActions.setDirty(entryKey, true);
    return () => {
      sources.delete(self);
      if (sources.size > 0) return;
      dirtySources.delete(entryKey);
      tabsActions.setDirty(entryKey, false);
    };
  }, [entryKey, dirty, self]);
};

/** 页面当前是否可见；后台标签里的轮询据此暂停 */
export const useTabActive = () => useContext(PaneContext).active;

/**
 * 页面从隐藏回到可见时执行一次（首次显示不执行），如从详情返回保活的列表后重新取数；
 * 页面没有卸载，筛选、分页与滚动位置都保留
 */
export const useOnReactivate = (fn: () => void) => {
  const active = useTabActive();
  const was = useRef(active);
  const latest = useRef(fn);
  latest.current = fn;
  useEffect(() => {
    if (active && !was.current) latest.current();
    was.current = active;
  }, [active]);
};
