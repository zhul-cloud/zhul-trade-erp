import { history } from '@umijs/max';
import { useSyncExternalStore } from 'react';
import * as M from './model';

/**
 * 标签页的全局状态。用同步的外部 store：操作先改状态再跳转，
 * 地址监听里看到「地址已是当前标签栈顶」就不再处理，不需要额外的跳转标记。
 */

const STORAGE_PREFIX = 'zhul_tabs_';

let state: M.TabsState = M.emptyState();
let storageKey: string | undefined;
let menuOf: (url: string) => string | undefined = () => undefined;
const listeners = new Set<() => void>();

const save = () => {
  if (!storageKey) return;
  try {
    // 只存地址与标题，不存页面内容
    sessionStorage.setItem(storageKey, JSON.stringify(state));
  } catch {
    // 隐私模式等写不进去时只是刷新后不能恢复，不影响使用
  }
};

const set = (next: M.TabsState) => {
  if (next === state) return;
  state = next;
  save();
  for (const l of listeners) l();
};

const currentUrl = () => {
  const { pathname, search, hash } = history.location;
  return `${pathname}${search}${hash}`;
};

const go = (url?: string) => {
  if (url && url !== currentUrl()) history.push(url);
};

let unlisten: (() => void) | undefined;

/**
 * 进入系统后初始化：按账号恢复上次的标签（浏览器刷新时），补上固定首页，再把当前地址归到标签里。
 * canOpen：当前账号能否打开某菜单。
 */
export const initTabs = (opts: {
  userKey: string;
  homePath?: string;
  menuOf: (url: string) => string | undefined;
  canOpen: (menuPath: string) => boolean;
}) => {
  menuOf = opts.menuOf;
  const key = `${STORAGE_PREFIX}${opts.userKey}`;
  if (storageKey !== key) {
    storageKey = key;
    let saved: M.TabsState | undefined;
    try {
      const raw = sessionStorage.getItem(key);
      saved = raw ? JSON.parse(raw) : undefined;
    } catch {
      saved = undefined;
    }
    state = M.restore(saved, opts.canOpen);
  }
  let next = M.ensureHome(state, opts.homePath);
  // 根地址会被重定向到首页，不单独占一个标签
  if (history.location.pathname !== '/')
    next = M.landed(next, currentUrl(), menuOf);
  if (!next.active && opts.homePath) next = { ...next, active: opts.homePath };
  set(next);
  unlisten?.();
  unlisten = history.listen(({ action, location }) => {
    if (!storageKey || location.pathname === '/') return;
    const url = `${location.pathname}${location.search}${location.hash}`;
    const kind: M.NavKind =
      action === 'POP' ? 'pop' : action === 'REPLACE' ? 'replace' : 'push';
    set(M.navigated(state, url, kind, menuOf));
  });
};

/** 退出登录：清空标签，不再监听 */
export const resetTabs = () => {
  unlisten?.();
  unlisten = undefined;
  try {
    for (let i = sessionStorage.length - 1; i >= 0; i -= 1) {
      const k = sessionStorage.key(i);
      if (k?.startsWith(STORAGE_PREFIX)) sessionStorage.removeItem(k);
    }
  } catch {
    // 忽略
  }
  storageKey = undefined;
  state = M.emptyState();
  for (const l of listeners) l();
};

const subscribe = (l: () => void) => {
  listeners.add(l);
  return () => listeners.delete(l);
};

export const useTabsState = () =>
  useSyncExternalStore(
    subscribe,
    () => state,
    () => state,
  );

export const getTabsState = () => state;

/** 菜单所属标签（未初始化时为 undefined） */
export const menuPathOf = (url: string) => menuOf(url);

export const tabsActions = {
  openMenu(menuPath: string) {
    if (!storageKey) {
      history.push(menuPath);
      return;
    }
    const r = M.openMenu(state, menuPath);
    set(r.state);
    go(r.url);
  },
  switchTo(menuPath: string) {
    const r = M.switchTo(state, menuPath);
    set(r.state);
    go(r.url);
  },
  back() {
    const r = M.back(state);
    set(r.state);
    go(r.url);
  },
  close(menuPath: string) {
    const r = M.close(state, menuPath);
    set(r.state);
    go(r.url);
  },
  closeOthers(menuPath: string) {
    const r = M.closeOthers(state, menuPath);
    set(r.state);
    go(r.url);
  },
  closeRight(menuPath: string) {
    const r = M.closeRight(state, menuPath);
    set(r.state);
    go(r.url);
  },
  refresh(menuPath: string) {
    set(M.refresh(state, menuPath));
  },
  setTitle(entryKey: string, title: string, tabLabel?: string) {
    set(M.setTitle(state, entryKey, title, tabLabel));
  },
  setDirty(entryKey: string, dirty: boolean) {
    set(M.setDirty(state, entryKey, dirty));
  },
};
