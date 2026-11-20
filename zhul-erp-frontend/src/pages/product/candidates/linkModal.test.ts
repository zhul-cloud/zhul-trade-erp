import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { candidateApi } from './service';
import { LinkModal } from './UnlinkedPanel';

/** spec product/document-product-link「历史单据回填与待关联清单」：默认选「可能是」的商品；也可以填写真实型号 */
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  candidateApi: { linkUnlinked: jest.fn() },
}));

const group = {
  key: 'siemens|6es7214',
  brand: 'Siemens',
  model: '6ES7 214',
  docs: { 采购订单: 2 },
  lineCount: 2,
  suggestion: {
    id: 11,
    mpnDisplay: '6ES7214',
    brandName: 'Siemens',
    categoryName: 'PLC',
    productName: '',
  },
};

const renderModal = (onDone = jest.fn()) =>
  render(
    React.createElement(
      App,
      null,
      React.createElement(LinkModal, {
        group,
        onClose: () => undefined,
        onDone,
      }),
    ),
  );

beforeEach(() => {
  (candidateApi.linkUnlinked as jest.Mock).mockReset().mockResolvedValue(2);
});

it('默认选「可能是」的商品，直接关联', async () => {
  const onDone = jest.fn();
  renderModal(onDone);
  fireEvent.click(await screen.findByRole('button', { name: /关\s*联/ }));
  await waitFor(() => expect(onDone).toHaveBeenCalled());
  expect(candidateApi.linkUnlinked).toHaveBeenCalledWith({
    key: 'siemens|6es7214',
    productId: 11,
  });
});

it('填写真实型号：没填时不能提交，填了按品牌和型号提交', async () => {
  const onDone = jest.fn();
  renderModal(onDone);
  fireEvent.click(await screen.findByLabelText('填写真实型号'));
  const ok = screen.getByRole('button', { name: /关\s*联/ });
  expect((ok as HTMLButtonElement).disabled).toBe(true);
  fireEvent.change(screen.getByLabelText('真实型号'), {
    target: { value: '6ES7214-1AG40-0XB0' },
  });
  fireEvent.click(ok);
  await waitFor(() => expect(onDone).toHaveBeenCalled());
  expect(candidateApi.linkUnlinked).toHaveBeenCalledWith({
    key: 'siemens|6es7214',
    brand: 'Siemens',
    model: '6ES7214-1AG40-0XB0',
  });
});
