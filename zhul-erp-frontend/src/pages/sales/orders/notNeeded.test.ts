import { render, screen } from '@testing-library/react';
import React from 'react';
import { NotNeededNote } from './notNeeded';

/** spec sales/sales-order「不需采购的数量」：型号行进度下显示 */
it('有不需采购数量时显示数量与原因', () => {
  render(
    React.createElement(NotNeededNote, {
      qty: 40,
      reasons: ['仓库有库存：仓库有 100 个现货'],
    }),
  );
  expect(
    screen.getByText('不需采购 40（仓库有库存：仓库有 100 个现货）'),
  ).toBeTruthy();
});

it('没有删除过不显示', () => {
  const { container } = render(
    React.createElement(NotNeededNote, { qty: 0, reasons: [] }),
  );
  expect(container.textContent).toBe('');
});
