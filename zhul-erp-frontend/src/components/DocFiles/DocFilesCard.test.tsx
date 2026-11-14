import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { PaneContext } from '@/components/PageTabs/hooks';
import { DocFilesCard } from './index';

jest.mock('@umijs/max', () => ({
  request: jest.fn(),
  history: { push: jest.fn() },
}));
jest.mock('@/components/PageTabs/store', () => ({
  tabsActions: { setDirty: jest.fn() },
}));

const { tabsActions } = jest.requireMock('@/components/PageTabs/store') as {
  tabsActions: { setDirty: jest.Mock };
};
const h = React.createElement;

const last = () => {
  const calls = tabsActions.setDirty.mock.calls;
  return calls.length ? calls[calls.length - 1][1] : undefined;
};

const mount = (onRemark: (r: string) => Promise<unknown>) =>
  render(
    h(
      App,
      null,
      h(
        PaneContext.Provider,
        { value: { entryKey: 'e1', active: true } },
        h(DocFilesCard, {
          remark: '原备注',
          editable: true,
          onRemark,
          onChanged: () => {},
        }),
      ),
    ),
  );

const edit = (text: string) => {
  const open = screen.queryByRole('button', { name: '编辑备注' });
  if (open) fireEvent.click(open);
  fireEvent.change(screen.getByRole('textbox', { name: '备注' }), {
    target: { value: text },
  });
};

beforeEach(() => tabsActions.setDirty.mockClear());

/** audit UI-052：附件卡里改了备注还没保存，登记到所属标签；保存成功或取消后撤销 */
it('备注改了未保存时登记未保存，保存成功后撤销', async () => {
  const onRemark = jest.fn().mockResolvedValue(undefined);
  mount(onRemark);
  edit('原备注');
  expect(last()).not.toBe(true);
  edit('新备注');
  expect(last()).toBe(true);
  fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(onRemark).toHaveBeenCalledWith('新备注'));
  await waitFor(() => expect(last()).toBe(false));
});

it('保存失败时仍保持未保存，取消编辑后撤销', async () => {
  const onRemark = jest.fn().mockRejectedValue(new Error('网络断开'));
  mount(onRemark);
  fireEvent.click(screen.getByRole('button', { name: '编辑备注' }));
  fireEvent.change(screen.getByRole('textbox', { name: '备注' }), {
    target: { value: '新备注' },
  });
  fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(onRemark).toHaveBeenCalled());
  expect(screen.getByRole('textbox', { name: '备注' })).toBeTruthy();
  expect(last()).toBe(true);
  fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
  expect(last()).toBe(false);
});
