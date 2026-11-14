import { render } from '@testing-library/react';
import React from 'react';
import { PaneContext, useOnReactivate, useTabDirty } from './hooks';

jest.mock('./store', () => ({ tabsActions: { setDirty: jest.fn() } }));

const { tabsActions } = jest.requireMock('./store') as {
  tabsActions: { setDirty: jest.Mock };
};
const h = React.createElement;

const Dirty = ({ dirty }: { dirty: boolean }) => {
  useTabDirty(dirty);
  return null;
};

const Page = ({ form, remark }: { form: boolean; remark: boolean }) =>
  h(
    PaneContext.Provider,
    { value: { entryKey: 'e1', active: true } },
    h(Dirty, { dirty: form }),
    h(Dirty, { dirty: remark }),
  );

/** 最近一次登记给 e1 的状态 */
const last = () => {
  const calls = tabsActions.setDirty.mock.calls.filter(([k]) => k === 'e1');
  return calls.length ? calls[calls.length - 1][1] : undefined;
};

beforeEach(() => tabsActions.setDirty.mockClear());

/** audit UI-052：页面表单与附件卡备注各自登记，任一处未保存这一页就算未保存，互不覆盖 */
it('同一页面多处登记未保存，互不覆盖', () => {
  const view = render(h(Page, { form: true, remark: false }));
  expect(last()).toBe(true);
  view.rerender(h(Page, { form: true, remark: true }));
  view.rerender(h(Page, { form: true, remark: false }));
  expect(last()).toBe(true);
  view.rerender(h(Page, { form: false, remark: false }));
  expect(last()).toBe(false);
  view.rerender(h(Page, { form: false, remark: true }));
  expect(last()).toBe(true);
  view.unmount();
  expect(last()).toBe(false);
});

const Probe = ({ fn }: { fn: () => void }) => {
  useOnReactivate(fn);
  return null;
};

const Reactivate = ({ active, fn }: { active: boolean; fn: () => void }) =>
  h(PaneContext.Provider, { value: { active } }, h(Probe, { fn }));

/** audit UI-053：保活的列表从隐藏回到可见时重新取数，首次显示不重复取 */
it('页面从隐藏回到可见时执行一次', () => {
  const fn = jest.fn();
  const view = render(h(Reactivate, { active: true, fn }));
  expect(fn).not.toHaveBeenCalled();
  view.rerender(h(Reactivate, { active: false, fn }));
  expect(fn).not.toHaveBeenCalled();
  view.rerender(h(Reactivate, { active: true, fn }));
  expect(fn).toHaveBeenCalledTimes(1);
  view.rerender(h(Reactivate, { active: true, fn }));
  expect(fn).toHaveBeenCalledTimes(1);
});
