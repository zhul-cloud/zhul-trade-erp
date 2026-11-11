import { createContext, useContext, useEffect } from 'react';
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

/** 登记未保存修改：点返回离开这页、关闭所在标签时先确认；超过上限自动关标签时跳过 */
export const useTabDirty = (dirty: boolean) => {
  const { entryKey } = useContext(PaneContext);
  useEffect(() => {
    if (!entryKey) return undefined;
    tabsActions.setDirty(entryKey, dirty);
    return () => tabsActions.setDirty(entryKey, false);
  }, [entryKey, dirty]);
};

/** 页面当前是否可见；后台标签里的轮询据此暂停 */
export const useTabActive = () => useContext(PaneContext).active;
