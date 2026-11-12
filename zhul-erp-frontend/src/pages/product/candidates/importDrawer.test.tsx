import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { ImportDrawer } from './ImportDrawer';
import { candidateApi } from './service';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/product/candidates', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  candidateApi: { importPreview: jest.fn(), importConfirm: jest.fn() },
}));

const view = (open: boolean) =>
  React.createElement(
    App,
    null,
    React.createElement(ImportDrawer, {
      open,
      onClose: () => undefined,
      onDone: () => undefined,
    }),
  );

/** UI-021：解析中点取消，迟到的预览不能在重新打开后复活 */
it('取消后迟到的预览不复活', async () => {
  let resolve!: (v: unknown) => void;
  (candidateApi.importPreview as jest.Mock).mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  const { rerender } = render(view(true));
  const file = new File(['# A'], 'cancelled-A.md', { type: 'text/markdown' });
  Object.defineProperty(file, 'text', { value: () => Promise.resolve('# A') });
  const input = document.querySelector('input[type="file"]') as HTMLInputElement;
  await act(async () => {
    fireEvent.change(input, { target: { files: [file] } });
  });
  expect(candidateApi.importPreview).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
  rerender(view(false));
  rerender(view(true));
  await act(async () => {
    resolve([{ fileName: 'cancelled-A.md', model: 'A-1', result: 'NEW' }]);
  });
  expect(screen.queryByText('cancelled-A.md')).toBeNull();
  expect(screen.queryByRole('button', { name: /确认导入/ })).toBeNull();
});
