import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { ActualModelLine } from './ActualModel';
import DocModelCell from './DocModelCell';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

jest.mock('@/pages/operations/media/ViewMediaLink', () => () => null);

const wrap = (node: unknown) =>
  render(React.createElement(App, null, node as never));

/** openspec actual-model-and-description：客户型号、真实型号已更新的提示与真实型号行内编辑 */
it('型号单元格：有客户型号时显示，已发出的单据显示真实型号已更新', () => {
  wrap(
    React.createElement(DocModelCell, {
      model: 'LC2D18B7',
      customerModel: 'LC2D-18B7',
      updatedTo: 'LC2D18B7C',
      updatedTail: '，请与客户确认',
    }),
  );
  expect(screen.getByText('客户型号：LC2D-18B7')).toBeTruthy();
  expect(screen.getByText('LC2D18B7C')).toBeTruthy();
  expect(screen.getByText(/请与客户确认/)).toBeTruthy();
});

it('型号单元格：没有客户型号和更新时只显示型号', () => {
  wrap(React.createElement(DocModelCell, { model: '6ES7214-1AG40-0XB0' }));
  expect(screen.queryByText(/客户型号/)).toBeNull();
  expect(screen.queryByText(/真实型号已更新/)).toBeNull();
});

it('真实型号行：点「+ 真实型号」行内编辑，点保存才提交，取消不提交', async () => {
  const onSave = jest.fn().mockResolvedValue(undefined);
  wrap(
    React.createElement(ActualModelLine, {
      editable: true,
      onSave,
    }),
  );
  fireEvent.click(screen.getByRole('button', { name: /真实型号/ }));
  const input = screen.getByPlaceholderText('确认后的准确型号');
  fireEvent.change(input, { target: { value: ' LC2D18B7C ' } });
  fireEvent.blur(input);
  expect(onSave).not.toHaveBeenCalled();
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  });
  expect(onSave).toHaveBeenCalledWith('LC2D18B7C');
});

it('真实型号行：已有真实型号时显示修改记录（账号换成姓名）；不可编辑时没有入口', async () => {
  // 修改人存的是登录账号，显示姓名，不露出邮箱（UI-117）
  (jest.requireMock('@umijs/max').request as jest.Mock).mockResolvedValue({
    data: { 'wanglei@example.com': '王磊' },
  });
  wrap(
    React.createElement(ActualModelLine, {
      actualModel: 'AX32-30-10-80',
      by: 'wanglei@example.com',
      at: '2026-11-18T10:20:00',
      editable: false,
      onSave: jest.fn(),
    }),
  );
  expect(screen.getByText(/真实型号：AX32-30-10-80/)).toBeTruthy();
  expect(await screen.findByText('王磊 · 11-18 10:20')).toBeTruthy();
  expect(screen.queryByText(/example\.com/)).toBeNull();
  expect(screen.queryByLabelText('修改真实型号')).toBeNull();
});
