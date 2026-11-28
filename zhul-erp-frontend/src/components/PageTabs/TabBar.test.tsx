import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { endPad, revealScroll, TabBar } from './index';
import { top } from './model';
import { getTabsState, initTabs, resetTabs, tabsActions } from './store';

jest.mock('@umijs/max', () => ({
  history: {
    location: { pathname: '/quotation/quotations/7', search: '', hash: '' },
    listen: () => () => {},
    push: jest.fn(),
  },
}));

const h = React.createElement;
const MENU = '/quotation/quotations';

const entryKey = () => top(getTabsState().tabs[0]).key;

const openContextMenu = async () => {
  fireEvent.contextMenu(screen.getByRole('tab'));
  fireEvent.click(await screen.findByText('刷新'));
};

beforeEach(() => {
  resetTabs();
  initTabs({ userKey: 'u1', menuOf: () => MENU, canOpen: () => true });
  render(h(App, null, h(TabBar, { tree: [] })));
});

afterEach(() => resetTabs());

/** audit UI-004：标签右键刷新也走未保存确认，取消时页面不重建 */
it('有未保存修改时，刷新先确认；取消保留，确认后才重建', async () => {
  const before = entryKey();
  act(() => tabsActions.setDirty(before, true));

  await openContextMenu();
  expect((await screen.findAllByText('刷新这个页面？')).length).toBeGreaterThan(
    0,
  );
  fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
  expect(entryKey()).toBe(before);
  expect(top(getTabsState().tabs[0]).dirty).toBe(true);

  await openContextMenu();
  const ok = await screen.findAllByRole('button', { name: '不保存，刷新' });
  fireEvent.click(ok[ok.length - 1]);
  expect(entryKey()).not.toBe(before);
  expect(top(getTabsState().tabs[0]).dirty).toBe(false);
});

it('没有未保存修改时直接刷新，不弹确认', async () => {
  const before = entryKey();
  await openContextMenu();
  expect(entryKey()).not.toBe(before);
  expect(screen.queryByText('刷新这个页面？')).toBeNull();
});

/** 标签 0～9 依次宽 100、间距 0，视口宽 350（ui-redesign S7、R22 方案 B） */
const TABS = Array.from({ length: 10 }, (_, i) => ({
  left: i * 100,
  width: 100,
}));

describe('revealScroll：切换标签时左端从完整标签开始', () => {
  it('当前标签已完整露出时不滚动', () => {
    expect(revealScroll({ scrollLeft: 200, width: 350 }, TABS, 3)).toBeNull();
  });

  it('当前标签在右侧被挡住：滚到能完整露出它的最左一个整签起点', () => {
    // 标签 8 右端 900，视口 350：起点要 ≥ 550，最近的整签起点是 600
    expect(revealScroll({ scrollLeft: 0, width: 350 }, TABS, 8)).toBe(600);
  });

  it('当前标签在左侧被挡住（含只露半截）：滚到它的起点', () => {
    expect(revealScroll({ scrollLeft: 250, width: 350 }, TABS, 2)).toBe(200);
  });

  it('标签比视口还宽：从它自己的起点开始', () => {
    const wide = [
      { left: 0, width: 100 },
      { left: 100, width: 500 },
    ];
    expect(revealScroll({ scrollLeft: 0, width: 350 }, wide, 1)).toBe(100);
  });

  it('序号越界时不滚动', () => {
    expect(revealScroll({ scrollLeft: 0, width: 350 }, TABS, 12)).toBeNull();
  });
});

describe('endPad：末尾留白让滚到底时左端也是整签', () => {
  it('放得下时不补', () => {
    expect(endPad(1200, TABS)).toBe(0);
  });

  it('放不下时补到最后一屏从整签开始', () => {
    // 末端 1000、视口 350：最后一屏从 700 开始只占 300，补 50 才能滚到 700
    expect(endPad(350, TABS)).toBe(50);
  });

  it('最后一屏正好占满时不补', () => {
    expect(endPad(300, TABS)).toBe(0);
  });

  it('没有标签时不补', () => {
    expect(endPad(350, [])).toBe(0);
  });
});
