import { useLocation, useModel, useOutlet } from '@umijs/max';
import { Dropdown } from 'antd';
import { ArrowLeft, ChevronDown, House, X } from 'lucide-react';
import React, {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { UNSAFE_LocationContext, useNavigationType } from 'react-router';
import { useConfirmDanger } from '@/components/ConfirmDanger';
import { useAppTheme } from '@/theme/AppTheme';
import { edgeShadow } from '@/theme/palette';
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

/**
 * 让第 index 个标签完整露出的滚动位置（ui-redesign S7、R22 方案 B）：左端总从一个完整标签开始，
 * 不停在标签中间。已经完整露出时返回 null，不滚动。tabs 为各标签相对标签条的左边距和宽度
 */
export const revealScroll = (
  view: { scrollLeft: number; width: number },
  tabs: { left: number; width: number }[],
  index: number,
): number | null => {
  const t = tabs[index];
  if (!t) return null;
  if (t.left < view.scrollLeft) return t.left;
  const right = t.left + t.width;
  if (right <= view.scrollLeft + view.width + 1) return null;
  const start = tabs.find((x) => right - x.left <= view.width);
  return start ? start.left : t.left;
};

/**
 * 标签条末尾补的留白：让滚到最右端时左端也正好是一个完整标签的起点（浏览器滚到底时停在最大滚动量，不一定是吸附点）。
 * 放得下时不补
 */
export const endPad = (
  width: number,
  tabs: { left: number; width: number }[],
): number => {
  const last = tabs[tabs.length - 1];
  if (!last) return 0;
  const end = last.left + last.width;
  if (end <= width) return 0;
  const start = tabs.find((x) => end - x.left <= width);
  return start ? Math.max(0, width - (end - start.left)) : 0;
};

/** 标签之间的间距 */
const TAB_GAP = 4;

/** 滚轮在标签栏上换成左右翻动：每累计这么多滚动量翻一个标签（鼠标滚轮一格约 100） */
const WHEEL_STEP = 50;

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
 * 放不下时横向滚动并吸附到整个标签，哪一侧还有标签那一侧显示边缘阴影，滚轮可左右翻动（S7、R22 方案 B）；
 * 末尾「全部标签」按钮显示没露出来的数量，下拉列出全部标签。
 */
export const TabBar: React.FC<{ tree: MenuTree }> = ({ tree }) => {
  const s = useTabsState();
  const { palette, mode } = useAppTheme();
  const confirmDanger = useConfirmDanger();
  const refs = useRef(new Map<string, HTMLDivElement>());
  const strip = useRef<HTMLDivElement>(null);
  const spacer = useRef<HTMLDivElement>(null);
  const [hidden, setHidden] = useState(0);
  // 哪一侧还有标签：那一侧边缘显示内阴影
  const [edges, setEdges] = useState({ left: false, right: false });

  /** 各标签相对标签条的位置（标签条是它们的定位父级） */
  const tabBoxes = useCallback(
    () =>
      s.tabs.map((t) => {
        const el = refs.current.get(t.menuPath);
        return { left: el?.offsetLeft ?? 0, width: el?.offsetWidth ?? 0 };
      }),
    [s.tabs],
  );

  /** 末尾留白直接改样式：要在滚动到当前标签之前生效，不走一轮渲染 */
  const fitPad = useCallback(() => {
    const el = strip.current;
    if (!el || !spacer.current) return;
    spacer.current.style.width = `${endPad(el.clientWidth, tabBoxes())}px`;
  }, [tabBoxes]);

  const measure = useCallback(() => {
    const el = strip.current;
    const box = el?.getBoundingClientRect();
    if (!el || !box) return;
    setHidden(
      hiddenCount(
        box,
        [...refs.current.values()].map((x) => x.getBoundingClientRect()),
      ),
    );
    const left = el.scrollLeft > 1;
    const right = el.scrollLeft + el.clientWidth < el.scrollWidth - 1;
    setEdges((old) =>
      old.left === left && old.right === right ? old : { left, right },
    );
  }, []);

  useEffect(() => {
    fitPad();
    const el = strip.current;
    const index = s.tabs.findIndex((t) => t.menuPath === s.active);
    if (el && index >= 0) {
      const to = revealScroll(
        { scrollLeft: el.scrollLeft, width: el.clientWidth },
        tabBoxes(),
        index,
      );
      if (to !== null) el.scrollLeft = to;
    }
    measure();
  }, [s.active, s.tabs, measure, tabBoxes, fitPad]);

  // 鼠标滚轮（纵向）在标签栏上换成左右翻动，一次一个标签；触控板横向滑动走浏览器原生滚动
  useEffect(() => {
    const el = strip.current;
    if (!el) return undefined;
    let acc = 0;
    const onWheel = (e: WheelEvent) => {
      if (Math.abs(e.deltaY) <= Math.abs(e.deltaX)) return;
      if (el.scrollWidth <= el.clientWidth) return;
      e.preventDefault();
      acc += e.deltaY;
      if (Math.abs(acc) < WHEEL_STEP) return;
      const dir = acc > 0 ? 1 : -1;
      acc = 0;
      const boxes = tabBoxes();
      const first = boxes.findIndex((b) => b.left >= el.scrollLeft - 1);
      const next = boxes[Math.max(0, (first < 0 ? 0 : first) + dir)];
      if (next) el.scrollLeft = next.left;
    };
    el.addEventListener('wheel', onWheel, { passive: false });
    return () => el.removeEventListener('wheel', onWheel);
  }, [tabBoxes]);

  useEffect(() => {
    const el = strip.current;
    if (!el || typeof ResizeObserver === 'undefined') return undefined;
    const ro = new ResizeObserver(() => {
      fitPad();
      measure();
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, [measure, fitPad]);

  /** 关掉的标签里有未保存修改时先确认 */
  const confirmClose = (closing: Tab[], run: () => void) => {
    const dirty = closing.filter(isDirty);
    if (dirty.length === 0) {
      run();
      return;
    }
    confirmDanger({
      title: '关闭这个标签？',
      content: `${dirty.map((t) => tabLabel(tree, t)).join('、')} 有未保存的修改，关闭后这些修改会丢失。`,
      okText: '不保存，关闭',
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
    confirmDanger({
      title: '刷新这个页面？',
      content: `${tabLabel(tree, t)} 有未保存的修改，刷新后这些修改会丢失。`,
      okText: '不保存，刷新',
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
        style={{ flex: 1, minWidth: 0, height: '100%', position: 'relative' }}
      >
        <div
          ref={strip}
          role="tablist"
          aria-label="已打开的页面"
          onScroll={measure}
          style={{
            position: 'relative',
            width: '100%',
            height: '100%',
            display: 'flex',
            gap: TAB_GAP,
            overflowX: 'auto',
            scrollbarWidth: 'none',
            // 滚动停下时吸附到整个标签，左端不露半个标签（S7、R22 方案 B）
            scrollSnapType: 'x mandatory',
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
                    scrollSnapAlign: 'start',
                    height: '100%',
                    maxWidth: 240,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 4,
                    padding: t.pinned ? '0 16px' : '0 8px 0 16px',
                    cursor: on ? 'default' : 'pointer',
                    fontSize: 14,
                    // 中文只用 400/600：500 在 Windows 微软雅黑里显示成常规，和同一标签里的英文数字粗细不一（S11）
                    fontWeight: 400,
                    color: on ? palette.link : palette.sub,
                    boxSizing: 'border-box',
                    borderBottom: `2px solid ${on ? palette.link : 'transparent'}`,
                    paddingTop: 2,
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
          {/* 末尾留白（见 endPad）；负边距抵掉它前面的间距，放得下时不占位 */}
          <div
            ref={spacer}
            aria-hidden
            style={{ flex: 'none', marginInlineStart: -TAB_GAP }}
          />
        </div>
        {(['left', 'right'] as const).map(
          (side) =>
            edges[side] && (
              <span
                key={side}
                aria-hidden
                style={{
                  position: 'absolute',
                  top: 0,
                  bottom: 0,
                  [side]: 0,
                  width: 16,
                  pointerEvents: 'none',
                  boxShadow: edgeShadow(side, mode === 'light'),
                }}
              />
            ),
        )}
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
      {hidden > 0 && (
        <span
          aria-hidden
          style={{
            flex: 'none',
            width: 1,
            height: 24,
            background: palette.hairline,
          }}
        />
      )}
    </div>
  );
};

const BackBar: React.FC<{ tree: MenuTree }> = ({ tree }) => {
  const s = useTabsState();
  const { palette } = useAppTheme();
  const confirmDanger = useConfirmDanger();
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
          confirmDanger({
            title: '有未保存的修改',
            content: '现在离开，这些修改会丢失。',
            okText: '离开',
            cancelText: '留下',
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
          background: palette.primarySoft, // lint-ui-ignore primary-soft: 返回条，规范「标签页与返回」指定主题色浅底胶囊
          color: palette.link,
          fontSize: 13,
          fontWeight: 600,
          cursor: 'pointer',
        }}
      >
        <ArrowLeft size="1em" style={{ fontSize: 12 }} />
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
  const confirmDanger = useConfirmDanger();
  useEffect(() => {
    setLeaveConfirm((leave) =>
      confirmDanger({
        title: '有未保存的修改',
        content: '现在离开，这些修改会丢失。',
        okText: '离开',
        cancelText: '留下',
        onOk: leave,
      }),
    );
    return () => setLeaveConfirm(undefined);
  }, [confirmDanger]);

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
