/**
 * 菜单标签页的状态（纯函数，便于测试）。
 * 每个菜单最多一个标签；标签里有一个页面栈：页面内跳转入栈，返回出栈。
 * 规则见 openspec system/navigation「菜单标签页」「跳转后可返回原页面」。
 */

export interface TabEntry {
  /** 条目标识，渲染时作为页面区的 key */
  key: string;
  /** pathname + search + hash */
  url: string;
  /** 页面登记的标题（如「销售订单 SO20261105003」），没有登记时为空 */
  title?: string;
  /** 标签上显示的短名称（如「SO20261105003」），没有时用 title */
  tabLabel?: string;
  /** 页面有未保存修改 */
  dirty?: boolean;
}

export interface Tab {
  /** 所属菜单的地址，同时作为标签标识 */
  menuPath: string;
  stack: TabEntry[];
  /** 最近一次被切到的序号，用于超过上限时找最久没用的标签 */
  usedAt: number;
  pinned?: boolean;
}

/** 标签里有页面存在未保存修改 */
export const isDirty = (t: Tab) => t.stack.some((e) => e.dirty);

export interface TabsState {
  tabs: Tab[];
  active?: string;
  /** 自增序号：usedAt 与条目 key 都从这里取 */
  seq: number;
}

export const MAX_TABS = 10;
/** 每个标签保留不卸载的页面数（栈顶往下） */
export const KEEP_ALIVE_DEPTH = 5;

export const emptyState = (): TabsState => ({ tabs: [], seq: 0 });

const tick = (s: TabsState) => s.seq + 1;

const newEntry = (seq: number, url: string): TabEntry => ({
  key: `e${seq}`,
  url,
});

export const top = (t: Tab) => t.stack[t.stack.length - 1];

export const activeTab = (s: TabsState) =>
  s.tabs.find((t) => t.menuPath === s.active);

const update = (s: TabsState, menuPath: string, fn: (t: Tab) => Tab) => ({
  ...s,
  tabs: s.tabs.map((t) => (t.menuPath === menuPath ? fn(t) : t)),
});

/** 超过上限时关掉最久没用的标签；固定、当前、有未保存修改的标签不关 */
export const evict = (s: TabsState): TabsState => {
  let tabs = s.tabs;
  while (tabs.length > MAX_TABS) {
    const candidates = tabs.filter(
      (t) => !t.pinned && !isDirty(t) && t.menuPath !== s.active,
    );
    if (candidates.length === 0) break;
    const oldest = candidates.reduce((a, b) => (a.usedAt <= b.usedAt ? a : b));
    tabs = tabs.filter((t) => t !== oldest);
  }
  return { ...s, tabs };
};

/** 确保固定的首页标签存在且在最左边 */
export const ensureHome = (s: TabsState, homePath?: string): TabsState => {
  if (!homePath) return s;
  const existing = s.tabs.find((t) => t.menuPath === homePath);
  const others = s.tabs.filter((t) => t.menuPath !== homePath);
  if (existing) {
    return { ...s, tabs: [{ ...existing, pinned: true }, ...others] };
  }
  const seq = tick(s);
  const home: Tab = {
    menuPath: homePath,
    stack: [newEntry(seq, homePath)],
    usedAt: 0,
    pinned: true,
  };
  return { ...s, seq, tabs: [home, ...others] };
};

/**
 * 点菜单：已有该菜单的标签就切过去（页面不变），否则新建标签。
 * 返回新状态与应该跳到的地址。
 */
export const openMenu = (
  s: TabsState,
  menuPath: string,
): { state: TabsState; url: string } => {
  const seq = tick(s);
  const existing = s.tabs.find((t) => t.menuPath === menuPath);
  if (existing) {
    const state = update({ ...s, seq, active: menuPath }, menuPath, (t) => ({
      ...t,
      usedAt: seq,
    }));
    return { state, url: top(existing).url };
  }
  const tab: Tab = {
    menuPath,
    stack: [newEntry(seq, menuPath)],
    usedAt: seq,
  };
  return {
    state: evict({ ...s, seq, active: menuPath, tabs: [...s.tabs, tab] }),
    url: menuPath,
  };
};

/** 切到已有标签（点标签栏），返回应该跳到的地址 */
export const switchTo = (
  s: TabsState,
  menuPath: string,
): { state: TabsState; url?: string } => {
  const tab = s.tabs.find((t) => t.menuPath === menuPath);
  if (!tab) return { state: s };
  const seq = tick(s);
  return {
    state: update({ ...s, seq, active: menuPath }, menuPath, (t) => ({
      ...t,
      usedAt: seq,
    })),
    url: top(tab).url,
  };
};

export type NavKind = 'push' | 'replace' | 'pop';

/**
 * 地址变化后更新状态。
 * - push：压入当前标签（没有当前标签时按菜单归属建标签）
 * - replace：替换当前标签栈顶
 * - pop（浏览器后退/前进）：正好是当前标签上一条就出栈；是别的标签的栈顶就切过去；否则按 push 处理
 * menuOf：地址所属的菜单地址（最长前缀匹配），没有时为 undefined
 */
export const navigated = (
  s: TabsState,
  url: string,
  kind: NavKind,
  menuOf: (url: string) => string | undefined,
): TabsState => {
  const cur = activeTab(s);
  if (cur && top(cur).url === url) return s;
  if (kind === 'pop' && cur) {
    const prev = cur.stack[cur.stack.length - 2];
    if (prev && prev.url === url) return back(s).state;
    const other = s.tabs.find((t) => top(t).url === url);
    if (other) return switchTo(s, other.menuPath).state;
  }
  if (!cur) {
    const menuPath = menuOf(url) ?? url;
    const opened = openMenu(s, menuPath).state;
    if (menuPath === url) return opened;
    const seq = tick(opened);
    return update({ ...opened, seq }, menuPath, (t) => ({
      ...t,
      stack: [newEntry(seq, url)],
    }));
  }
  const seq = tick(s);
  if (kind === 'replace') {
    return update({ ...s, seq }, cur.menuPath, (t) => ({
      ...t,
      stack: [...t.stack.slice(0, -1), { ...top(t), url }],
    }));
  }
  return update({ ...s, seq }, cur.menuPath, (t) => ({
    ...t,
    stack: [...t.stack, newEntry(seq, url)],
  }));
};

/**
 * 直接打开一个地址（刷新、粘贴链接、书签）：
 * 正好是某个标签的栈顶就切过去（刷新时就是当前标签）；否则归到地址所属菜单的标签，
 * 该菜单已有标签就在里面入栈，没有就新建，不压进上次停留的标签。
 */
export const landed = (
  s: TabsState,
  url: string,
  menuOf: (url: string) => string | undefined,
): TabsState => {
  const cur = activeTab(s);
  if (cur && top(cur).url === url) return s;
  const same = s.tabs.find((t) => top(t).url === url);
  if (same) return switchTo(s, same.menuPath).state;
  const menuPath = menuOf(url) ?? url;
  if (s.tabs.some((t) => t.menuPath === menuPath)) {
    return navigated(switchTo(s, menuPath).state, url, 'push', menuOf);
  }
  return navigated({ ...s, active: undefined }, url, 'push', menuOf);
};

/** 当前标签返回上一页：出栈，返回应该跳到的地址 */
export const back = (s: TabsState): { state: TabsState; url?: string } => {
  const cur = activeTab(s);
  if (!cur || cur.stack.length < 2) return { state: s };
  const stack = cur.stack.slice(0, -1);
  return {
    state: update(s, cur.menuPath, (t) => ({ ...t, stack })),
    url: stack[stack.length - 1].url,
  };
};

/** 关闭标签；关的是当前标签时切到右边一个（没有就左边一个） */
export const close = (
  s: TabsState,
  menuPath: string,
): { state: TabsState; url?: string } => {
  const idx = s.tabs.findIndex((t) => t.menuPath === menuPath);
  if (idx < 0 || s.tabs[idx].pinned) return { state: s };
  const tabs = s.tabs.filter((t) => t.menuPath !== menuPath);
  if (s.active !== menuPath) return { state: { ...s, tabs } };
  const next = tabs[idx] ?? tabs[idx - 1];
  if (!next) return { state: { ...s, tabs, active: undefined } };
  return switchTo({ ...s, tabs }, next.menuPath);
};

/** 关闭其他（保留固定标签与指定标签），切到指定标签 */
export const closeOthers = (s: TabsState, menuPath: string) =>
  switchTo(
    {
      ...s,
      tabs: s.tabs.filter((t) => t.pinned || t.menuPath === menuPath),
    },
    menuPath,
  );

/** 关闭右侧；当前标签被关掉时切到指定标签 */
export const closeRight = (s: TabsState, menuPath: string) => {
  const idx = s.tabs.findIndex((t) => t.menuPath === menuPath);
  const tabs = s.tabs.filter((t, i) => i <= idx || t.pinned);
  const state = { ...s, tabs };
  if (tabs.some((t) => t.menuPath === s.active)) return { state };
  return switchTo(state, menuPath);
};

/** 刷新：给当前条目换一个 key，页面重新加载 */
export const refresh = (s: TabsState, menuPath: string): TabsState => {
  const seq = tick(s);
  return update({ ...s, seq }, menuPath, (t) => ({
    ...t,
    stack: [...t.stack.slice(0, -1), { ...top(t), key: `e${seq}` }],
  }));
};

/** 页面登记标题 */
export const setTitle = (
  s: TabsState,
  entryKey: string,
  title: string,
  tabLabel?: string,
): TabsState => {
  let changed = false;
  const tabs = s.tabs.map((t) => {
    const i = t.stack.findIndex((e) => e.key === entryKey);
    if (i < 0) return t;
    const e = t.stack[i];
    if (e.title === title && e.tabLabel === tabLabel) return t;
    changed = true;
    const stack = [...t.stack];
    stack[i] = { ...e, title, tabLabel };
    return { ...t, stack };
  });
  return changed ? { ...s, tabs } : s;
};

export const setDirty = (
  s: TabsState,
  entryKey: string,
  dirty: boolean,
): TabsState => {
  let changed = false;
  const tabs = s.tabs.map((t) => {
    if (!t.stack.some((e) => e.key === entryKey && !!e.dirty !== dirty))
      return t;
    changed = true;
    return {
      ...t,
      stack: t.stack.map((e) => (e.key === entryKey ? { ...e, dirty } : e)),
    };
  });
  return changed ? { ...s, tabs } : s;
};

/** 恢复时只留有权限的菜单；当前标签不在了就落到首页 */
export const restore = (
  saved: TabsState | undefined,
  canOpen: (menuPath: string) => boolean,
): TabsState => {
  if (!saved || !Array.isArray(saved.tabs)) return emptyState();
  const tabs = saved.tabs
    .filter((t) => t && Array.isArray(t.stack) && t.stack.length > 0)
    .filter((t) => canOpen(t.menuPath))
    .map((t) => ({
      ...t,
      stack: t.stack.map((e) => ({ ...e, dirty: false })),
    }));
  const active = tabs.some((t) => t.menuPath === saved.active)
    ? saved.active
    : undefined;
  return { tabs, active, seq: Number(saved.seq) || 0 };
};
