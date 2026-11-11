import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { brandApi, categoryApi } from '../service';
import { BatchEditModal } from './index';
import { candidateApi } from './service';

/** spec product/product-candidate「批量修改品牌与品类」：两项都没选时不能提交，只提交选了的那一项 */
jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  brandApi: { options: jest.fn() },
  categoryApi: { options: jest.fn() },
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  candidateApi: { batchEdit: jest.fn() },
}));

it('两项都没选时确定不可用，选了品牌只提交品牌', async () => {
  (brandApi.options as jest.Mock).mockResolvedValue([
    { id: 7, brandName: 'Others' },
    { id: 8, brandName: 'Schneider' },
  ]);
  (categoryApi.options as jest.Mock).mockResolvedValue([]);
  (candidateApi.batchEdit as jest.Mock).mockResolvedValue({
    updated: 2,
    skipped: 0,
  });
  const onDone = jest.fn();
  render(
    React.createElement(
      App,
      null,
      React.createElement(BatchEditModal, {
        open: true,
        ids: [1, 2],
        onClose: () => undefined,
        onDone,
      }),
    ),
  );
  const ok = await screen.findByRole('button', { name: /确\s*定/ });
  expect((ok as HTMLButtonElement).disabled).toBe(true);

  fireEvent.mouseDown(screen.getByLabelText('批量修改品牌'));
  fireEvent.click(await screen.findByTitle('Schneider'));
  await waitFor(() => expect((ok as HTMLButtonElement).disabled).toBe(false));
  fireEvent.click(ok);
  await waitFor(() => expect(onDone).toHaveBeenCalled());
  expect(candidateApi.batchEdit).toHaveBeenCalledWith([1, 2], 8, undefined);
});
