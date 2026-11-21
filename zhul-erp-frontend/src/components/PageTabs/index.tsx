import { ArrowLeftOutlined } from '@ant-design/icons';
import { useLocation, useModel, useOutlet } from '@umijs/max';
import { App, Dropdown } from 'antd';
import { ChevronDown, House, X } from 'lucide-react';
import React, {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { UNSAFE_LocationContext, useNavigationType } from 'react-router';
import { useAppTheme } from '@/theme/AppTheme';
import { findMenu } from '@/utils/menuName';
import { PaneContext } from './hooks';
import {
  activeTab,
  aliveEntries,
  isDirty,
  MAX_TABS,
  type Tab,
  type TabEntry,
  top,
} from './model';
import { initTabs, setLeaveConfirm, tabsActions, useTabsState } from './store';

type LocationCtx = React.ContextType<typeof UNSAFE_LocationContext>;

interface Cached {
  element: React.ReactNode;
  loc: LocationCtx;
}

type MenuTree = Parameters<typeof findMenu>[0];

const pathOf = (url: string) => url.split(/[?#]/)[0];

/** 条目的显示名称：页面登记的标题；没有时列表页用菜单名，其他页用「菜单名详情」 */
const entryTitle = (tree: MenuTree, e: TabEntry) => {
  if (e.title) return e.title;
  const path = pathOf(e.url);
  const menu = findMenu(tree, path);
  if (!menu) return '上一页';
  return menu.path === path ? menu.name : `${menu.name}详情`;
};

const tabLabel = (tree: MenuTree, t: Tab) => {
  const e = top(t);
  return e.tabLabel || entryTitle(tree, e);
};

/** 一个页面：隐藏时不卸载，地址固定为它自己的地址 */
export const Pane: React.FC<{
  entryKey: string;
  menuPath: string;
  active: boolean;
  cached: Cached;
}> = ({ entryKey, menuPath, active, cached }) => {
  const info = useMemo(
    () => ({ entryKey, menuPath, active }),
    [entryKey, menuPath, active],
  );
  return (
    <div style={active ? undefined : { display: 'none' }}>
      <PaneContext.Provider value={info}>
        <UNSAFE_LocationContext.Provider value={cached.loc}>
          {cached.element}
        </UNSAFE_LocationContext.Provider>
      </PaneContext.Provider>
    </div>
  );
};

/** 没有完整露出来的标签数：左右边缘超出可见区域 1px 以上就算 */
export const hiddenCount = (
  box: { left: number; right: number },
  items: { left: number; right: number }[],
) =>
  items.filter((r) => r.left < box.left - 1 || r.right > box.right + 1).length;

/** 未保存修改的小圆点：正文色，避开琥珀主题色 */
const DirtyDot: React.FC<{ color: string }> = ({ color }) => (
  <span
    role="img"
    aria-label="有未保存的修改"
    style={{
      flex: 'none',
      width: 6,
      height: 6,
      marginInlineStart: 4,
      borderRadius: '50%',
      background: color,
    }}
  />
);

/**
 * 顶栏里的标签栏（外壳原型 F1、F1d）：页签撑满顶栏高度、文字垂直居中，选中为主题色字加底部 2px 主题色线。
 * 放不下时超出部分截断，末尾「全部标签」按钮显示没露出来的数量，下拉列出全部标签。
 */
export const TabBar: React.FC<{ tree: MenuTree }> = ({ tree }) => {
  const s = useTabsState();
  const { palette } = useAppTheme();
  const { modal } = App.useApp();
  const refs = useRef(new Map<string, HTMLDivElement>());
  const strip = useRef<HTMLDivElement>(null);
  const [hidden, setHidden] = useState(0);

  const measure = useCallback(() => {
    const box = strip.current?.getBoundingClientRect();
    if (!box) return;
    setHidden(
      hiddenCount(
        box,
        [...refs.current.values()].map((el) => el.getBoundingClientRect()),
      ),
    );
  }, []);

  useEffect(() => {
    if (s.active)
      refs.current
        .get(s.active)
        ?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    measure();
  }, [s.active, s.tabs, measure]);

  useEffect(() => {
    const el = strip.current;
    if (!el || typeof ResizeObserver === 'undefined') return undefined;
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, [measure]);

  /** 关掉的标签里有未保存修改时先确认 */
  const confirmClose = (closing: Tab[], run: () => void) => {
    const dirty = closing.filter(isDirty);
    if (dirty.length === 0) {
      run();
      return;
    }
    modal.confirm({
      title: '关闭这个标签？',
      content: `${dirty.map((t) => tabLabel(tree, t)).join('、')} 有未保存的修改，关闭后这些修改会丢失。`,
      okText: '不保存，关闭',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: run,
    });
  };

  /** 刷新会重建标签当前显示的页面：它有未保存修改时同样先确认（audit UI-004） */
  const refreshTab = (t: Tab) => {
    const run = () => {
      if (t.menuPath !== s.active) tabsActions.switchTo(t.menuPath);
      tabsActions.refresh(t.menuPath);
    };
    if (!top(t).dirty) {
      run();
      return;
    }
    modal.confirm({
      title: '刷新这个页面？',
      content: `${tabLabel(tree, t)} 有未保存的修改，刷新后这些修改会丢失。`,
      okText: '不保存，刷新',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: run,
    });
  };

  const closeTab = (t: Tab) =>
    confirmClose([t], () => tabsActions.close(t.menuPath));
  const closeOthers = (t: Tab) =>
    confirmClose(
      s.tabs.filter((x) => !x.pinned && x.menuPath !== t.menuPath),
      () => tabsActions.closeOthers(t.menuPath),
    );
  const closeRight = (t: Tab) => {
    const idx = s.tabs.indexOf(t);
    confirmClose(
      s.tabs.filter((x, i) => i > idx && !x.pinned),
      () => tabsActions.closeRight(t.menuPath),
    );
  };

  return (
    <div
      style={{
        flex: 1,
        minWidth: 0,
        height: '100%',
        display: 'flex',
        alignItems: 'center',
        gap: 8,
      }}
    >
      <div
        ref={strip}
        role="tablist"
        aria-label="已打开的页面"
        onScroll={measure}
        style={{
          flex: 1,
          minWidth: 0,
          height: '100%',
          display: 'flex',
          gap: 4,
          overflowX: 'auto',
          scrollbarWidth: 'none',
        }}
      >
        {s.tabs.map((t, i) => {
          const on = t.menuPath === s.active;
          const label = tabLabel(tree, t);
          const items = [
            { key: 'refresh', label: '刷新' },
            ...(t.pinned ? [] : [{ key: 'close', label: '关闭' }]),
            { key: 'others', label: '关闭其他' },
            {
              key: 'right',
              label: '关闭右侧',
              disabled: i === s.tabs.length - 1,
            },
          ];
          return (
            <Dropdown
              key={t.menuPath}
              trigger={['contextMenu']}
              menu={{
                items,
                onClick: ({ key }) => {
                  if (key === 'refresh') refreshTab(t);
                  else if (key === 'close') closeTab(t);
                  else if (key === 'others') closeOthers(t);
                  else if (key === 'right') closeRight(t);
                },
              }}
            >
              <div
                ref={(el) => {
                  if (el) refs.current.set(t.menuPath, el);
                  else refs.current.delete(t.menuPath);
                }}
                role="tab"
                tabIndex={0}
                aria-selected={on}
                title={entryTitle(tree, top(t))}
                onClick={() => !on && tabsActions.switchTo(t.menuPath)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    if (!on) tabsActions.switchTo(t.menuPath);
                  }
                }}
                onMouseDown={(e) => {
                  // 中键关闭；阻止浏览器的中键滚动
                  if (e.button === 1) e.preventDefault();
                }}
                onAuxClick={(e) => {
                  if (e.button === 1 && !t.pinned) closeTab(t);
                }}
                style={{
                  flex: 'none',
                  height: '100%',
                  maxWidth: 240,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                  padding: t.pinned ? '0 16px' : '0 8px 0 16px',
                  cursor: on ? 'default' : 'pointer',
                  fontSize: 14,
                  fontWeight: 500,
                  color: on ? palette.link : palette.sub,
                  boxShadow: on ? `inset 0 -2px 0 ${palette.link}` : 'none',
                  userSelect: 'none',
                }}
              >
                {t.pinned && <House size={14} style={{ flex: 'none' }} />}
                <span
                  style={{
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {label}
                </span>
                {isDirty(t) && <DirtyDot color={palette.sub} />}
                {!t.pinned && (
                  <button
                    type="button"
                    aria-label={`关闭 ${label}`}
                    onClick={(e) => {
                      e.stopPropagation();
                      closeTab(t);
                    }}
                    style={{
                      flex: 'none',
                      width: 24,
                      height: 24,
                      display: 'grid',
                      placeItems: 'center',
                      padding: 0,
                      border: 0,
                      borderRadius: '50%',
                      background: 'transparent',
                      color: on ? palette.link : palette.mute,
                      cursor: 'pointer',
                    }}
                  >
                    <X size={14} />
                  </button>
                )}
              </div>
            </Dropdown>
          );
        })}
      </div>
      {hidden > 0 && (
        <Dropdown
          trigger={['click']}
          placement="bottomRight"
          menu={{
            selectedKeys: s.active ? [s.active] : [],
            items: [
              {
                type: 'group',
                label: `已打开 ${s.tabs.length} / ${MAX_TABS}，再打开会关掉最久没用的标签`,
                children: s.tabs.map((t) => ({
                  key: t.menuPath,
                  label: (
                    <span
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        maxWidth: 280,
                      }}
                    >
                      <span
                        style={{
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {tabLabel(tree, t)}
                      </span>
                      {isDirty(t) && <DirtyDot color={palette.sub} />}
                    </span>
                  ),
                })),
              },
            ],
            onClick: ({ key }) => tabsActions.switchTo(key),
          }}
        >
          <button
            type="button"
            aria-label={`全部标签，还有 ${hidden} 个没露出来`}
            className="num"
            style={{
              flex: 'none',
              height: 32,
              display: 'flex',
              alignItems: 'center',
              gap: 2,
              padding: '0 6px 0 10px',
              border: 0,
              borderRadius: 16,
              background: palette.navOn,
              color: palette.sub,
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            +{hidden}
            <ChevronDown size={16} />
          </button>
        </Dropdown>
      )}
    </div>
  );
};

const BackBar: React.FC<{ tree: MenuTree }> = ({ tree }) => {
  const s = useTabsState();
  const { palette } = useAppTheme();
  const { modal } = App.useApp();
  const cur = activeTab(s);
  if (!cur || cur.stack.length < 2) return null;
  const prev = cur.stack[cur.stack.length - 2];
  return (
    <div>
      <button
        type="button"
        onClick={() => {
          if (!top(cur).dirty) {
            tabsActions.back();
            return;
          }
          modal.confirm({
            title: '有未保存的修改',
            content: '现在离开，这些修改会丢失。',
            okText: '离开',
            cancelText: '留下',
            okButtonProps: { danger: true },
            onOk: () => tabsActions.back(),
          });
        }}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 6,
          marginBottom: 12,
          padding: '4px 12px',
          border: 0,
          borderRadius: 999,
          background: palette.primarySoft,
          color: palette.link,
          fontSize: 13,
          fontWeight: 600,
          cursor: 'pointer',
        }}
      >
        <ArrowLeftOutlined style={{ fontSize: 12 }} />
        返回 {entryTitle(tree, prev)}
      </button>
    </div>
  );
};

/**
 * 菜单标签页：标签栏 + 返回条 + 页面区。
 * 页面按「标签 → 页面栈条目」缓存，隐藏时不卸载；每个页面读到的地址固定为它自己的地址。
 */
const PageTabs: React.FC<{
  userKey: string;
  homePath?: string;
  canOpen: (menuPath: string) => boolean;
}> = ({ userKey, homePath, canOpen }) => {
  const { initialState } = useModel('@@initialState');
  const tree = (initialState?.menuTree ?? []) as MenuTree;
  const location = useLocation();
  const navigationType = useNavigationType();
  const outlet = useOutlet();
  const cache = useRef(new Map<string, Cached>());

  const menuOf = (url: string) => findMenu(tree, pathOf(url))?.path;
  // 首次渲染前同步初始化，避免页面先挂在标签外面、初始化后再重新挂载一次
  const [inited] = useState(() => {
    initTabs({ userKey, homePath, menuOf, canOpen });
    return userKey;
  });
  useEffect(() => {
    if (inited !== userKey) initTabs({ userKey, homePath, menuOf, canOpen });
    // 只在换账号时重新初始化
  }, [userKey]);

  // 浏览器后退离开未保存的页面时，与返回条一样先确认（audit UI-004#1）
  const { modal } = App.useApp();
  useEffect(() => {
    setLeaveConfirm((leave) =>
      modal.confirm({
        title: '有未保存的修改',
        content: '现在离开，这些修改会丢失。',
        okText: '离开',
        cancelText: '留下',
        okButtonProps: { danger: true },
        onOk: leave,
      }),
    );
    return () => setLeaveConfirm(undefined);
  }, [modal]);

  const s = useTabsState();
  const liveLoc = useMemo<LocationCtx>(
    () => ({ location, navigationType }),
    [location, navigationType],
  );
  const cur = activeTab(s);
  const curEntry = cur && top(cur);
  const url = `${location.pathname}${location.search}${location.hash}`;
  const matched = !!curEntry && curEntry.url === url;
  if (curEntry && matched) {
    cache.current.set(curEntry.key, { element: outlet, loc: liveLoc });
  }

  // 整个页面共用窗口滚动条：离开一个页面时记下位置，回到它时恢复；新页面从顶部开始
  const scrolls = useRef(new Map<string, number>());
  const shownKey = matched ? curEntry?.key : undefined;
  useEffect(() => {
    if (!shownKey) return undefined;
    const onScroll = () => scrolls.current.set(shownKey, window.scrollY);
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, [shownKey]);
  useLayoutEffect(() => {
    if (shownKey) window.scrollTo(0, scrolls.current.get(shownKey) ?? 0);
  }, [shownKey]);

  // 浏览器窗口标题跟随当前页面登记的标题；放到下一轮，盖过布局按路由设的菜单名
  const shownTitle = matched ? curEntry?.title : undefined;
  const siteName =
    initialState?.appearance?.siteName ?? initialState?.settings?.title;
  useEffect(() => {
    if (!shownTitle) return undefined;
    const timer = window.setTimeout(() => {
      document.title = siteName ? `${shownTitle} - ${siteName}` : shownTitle;
    });
    return () => window.clearTimeout(timer);
  }, [shownTitle, siteName, url]);

  const panes: React.ReactNode[] = [];
  const alive = new Set<string>();
  for (const t of s.tabs) {
    for (const e of aliveEntries(t)) {
      const c = cache.current.get(e.key);
      if (!c) continue;
      alive.add(e.key);
      panes.push(
        <Pane
          key={e.key}
          entryKey={e.key}
          menuPath={t.menuPath}
          active={matched && e === curEntry}
          cached={c}
        />,
      );
    }
  }
  for (const k of cache.current.keys()) {
    if (!alive.has(k)) {
      cache.current.delete(k);
      scrolls.current.delete(k);
    }
  }

  return (
    <>
      <BackBar tree={tree} />
      {panes}
      {/* 地址还没归到标签（理论上不会发生）时直接显示页面，不留空白 */}
      {!matched && outlet}
    </>
  );
};

export default PageTabs;
