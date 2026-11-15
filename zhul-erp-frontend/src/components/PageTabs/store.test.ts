import { top } from './model';
import { getTabsState, initTabs, resetTabs, tabsActions } from './store';

const mockLoc = { pathname: '/customer/list', search: '', hash: '' };
const mockListeners: ((u: {
  action: string;
  location: typeof mockLoc;
}) => void)[] = [];
const mockPush = jest.fn((url: string) => {
  mockLoc.pathname = url;
  for (const l of mockListeners)
    l({ action: 'PUSH', location: { ...mockLoc } });
});
const mockReplace = jest.fn((url: string) => {
  mockLoc.pathname = url;
  for (const l of mockListeners)
    l({ action: 'REPLACE', location: { ...mockLoc } });
});

jest.mock('@umijs/max', () => ({
  history: {
    get location() {
      return mockLoc;
    },
    listen: (fn: (u: unknown) => void) => {
      mockListeners.push(fn);
      return () => mockListeners.splice(mockListeners.indexOf(fn), 1);
    },
    push: (url: string) => mockPush(url),
    replace: (url: string) => mockReplace(url),
  },
}));

const LIST = '/customer/list';
const stack = () =>
  getTabsState().tabs.find((t) => t.menuPath === LIST)?.stack ?? [];

const start = (url: string) => {
  resetTabs();
  mockLoc.pathname = url;
  initTabs({ userKey: 'u1', menuOf: () => LIST, canOpen: () => true });
  mockPush.mockClear();
  mockReplace.mockClear();
};

afterEach(() => resetTabs());

/** audit UI-058：保存、删除后离开当前页要退栈，不能再压一层列表 */
describe('tabsActions.backOr', () => {
  it('有上一页时退回上一页，栈不变深', () => {
    start(LIST);
    mockPush(`${LIST}/5`);
    mockPush(`${LIST}/5/edit`);
    expect(stack()).toHaveLength(3);
    mockPush.mockClear();

    tabsActions.backOr(LIST);
    expect(stack().map((e) => e.url)).toEqual([LIST, `${LIST}/5`]);
    expect(mockPush).toHaveBeenCalledWith(`${LIST}/5`);
    expect(top(getTabsState().tabs[0]).url).toBe(`${LIST}/5`);
  });

  it('直接打开的页面没有上一页时用兜底地址替换当前页，不留下刚离开的页面', () => {
    start(`${LIST}/5`);
    expect(stack()).toHaveLength(1);
    tabsActions.backOr(LIST);
    expect(mockPush).not.toHaveBeenCalled();
    expect(mockReplace).toHaveBeenCalledWith(LIST);
    expect(stack().map((e) => e.url)).toEqual([LIST]);
  });
});
