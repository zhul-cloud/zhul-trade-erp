import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { OrderFilesCard } from './OrderFiles';
import { orderApi } from '../service';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/sales/orders/1', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  orderApi: { remark: jest.fn() },
}));

const mount = (onChanged: () => void) =>
  render(
    React.createElement(
      App,
      null,
      React.createElement(OrderFilesCard, {
        order: { id: 1, remark: 'ORIGINAL', attachments: [] } as never,
        canEdit: true,
        onChanged,
      }),
    ),
  );

const editAndSave = async (text: string) => {
  fireEvent.click(screen.getByText('编辑'));
  fireEvent.change(screen.getByLabelText('备注'), { target: { value: text } });
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  });
};

/** UI-033：保存失败时留在编辑状态、草稿还在；成功才退出 */
it('备注保存失败保留草稿，成功才退出编辑', async () => {
  const onChanged = jest.fn();
  (orderApi.remark as jest.Mock).mockRejectedValueOnce(new Error('network'));
  mount(onChanged);
  await editAndSave('UNSAVED DRAFT');
  expect((screen.getByLabelText('备注') as HTMLTextAreaElement).value).toBe(
    'UNSAVED DRAFT',
  );
  expect(onChanged).not.toHaveBeenCalled();

  (orderApi.remark as jest.Mock).mockResolvedValueOnce(undefined);
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  });
  expect(screen.queryByLabelText('备注')).toBeNull();
  expect(onChanged).toHaveBeenCalledTimes(1);
});
