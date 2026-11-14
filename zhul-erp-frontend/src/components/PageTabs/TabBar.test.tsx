import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { TabBar } from './index';
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

beforeAll(() => {
  // jsdom 没有滚动
  Element.prototype.scrollIntoView = jest.fn();
});

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
