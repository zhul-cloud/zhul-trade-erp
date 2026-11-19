import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { ItemRemarkLine, ItemRemarkText } from './ItemRemark';

/** openspec item-remark：型号备注的只读显示、下单后更新的标红与行内编辑 */
it('只读显示备注；采购单下单后更新时标红提醒核对', () => {
  const { rerender } = render(
    React.createElement(ItemRemarkText, { text: '线圈 DC24V' }),
  );
  expect(screen.getByText('线圈 DC24V')).toBeTruthy();
  rerender(
    React.createElement(ItemRemarkText, { text: '线圈 DC24V', alert: true }),
  );
  expect(
    screen.getByText(/备注已更新：线圈 DC24V（请与供应商核对）/),
  ).toBeTruthy();
  rerender(React.createElement(ItemRemarkText, { text: '' }));
  expect(screen.queryByText(/备注/)).toBeNull();
});

it('没有备注时可「+ 备注」，保存时去首尾空格；不能编辑时不显示入口', async () => {
  const onSave = jest.fn().mockResolvedValue(undefined);
  const { rerender } = render(
    React.createElement(
      App,
      null,
      React.createElement(ItemRemarkLine, { editable: true, onSave }),
    ),
  );
  fireEvent.click(screen.getByRole('button', { name: /备注/ }));
  fireEvent.change(screen.getByLabelText('型号备注'), {
    target: { value: '  要 AC220V 线圈 ' },
  });
  expect(screen.getByText(/不进发给客户、供应商的文件/)).toBeTruthy();
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  });
  expect(onSave).toHaveBeenCalledWith('要 AC220V 线圈');

  rerender(
    React.createElement(
      App,
      null,
      React.createElement(ItemRemarkLine, { editable: false, onSave }),
    ),
  );
  expect(screen.queryByRole('button', { name: /备注/ })).toBeNull();
});
