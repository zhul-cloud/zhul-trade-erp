import { act, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import SourceNos, { listHref } from './SourceNos';

jest.mock('@umijs/max', () => {
  const R = jest.requireActual('react');
  return {
    Link: ({ to, children }: any) =>
      R.createElement('a', { href: to }, children),
    useLocation: () => ({ search: '' }),
  };
});

// 测试转译不支持在类型里引用导入的名字，入参写成普通对象
const mount = (props: Record<string, unknown>) =>
  render(React.createElement(SourceNos, props as never)).container;

/** spec system/navigation「来源单号」 */
it('表头列出全部来源，用顿号分隔，可点开', () => {
  const c = mount({
    mode: 'inline',
    refs: [
      { no: 'QT001', href: '/quotations/1' },
      { no: 'QT002', href: '/quotations/2' },
    ],
  });
  expect(c.textContent).toBe('来源单号 QT001、QT002');
  expect(screen.getByText('QT002').getAttribute('href')).toBe('/quotations/2');
});

it('列表里一个来源只显示单号', () => {
  expect(
    mount({ refs: [{ no: 'PI001', href: '/sales/pi/1' }] }).textContent,
  ).toBe('PI001');
});

it('列表里多个来源显示「X 等 N 个」，悬停列出全部', async () => {
  const c = mount({
    refs: [{ no: 'SO1', href: '/a' }, { no: 'SO2' }, { no: 'SO3' }],
  });
  expect(c.textContent).toBe('SO1 等 3 个');
  await act(async () => {
    fireEvent.mouseEnter(screen.getByText('SO1').parentElement as HTMLElement);
    await new Promise((r) => setTimeout(r, 200));
  });
  expect(document.querySelector('.ant-popover')?.textContent).toContain(
    '来源单号（3）',
  );
  expect(screen.getAllByText('SO3').length).toBeGreaterThan(0);
});

it('没有来源时显示占位，如手动创建', () => {
  expect(
    mount({ refs: [{ no: undefined }], empty: '手动创建' }).textContent,
  ).toBe('手动创建');
});

it('没有详情页的单据链到列表并按单号筛选', () => {
  expect(listHref('/warehouse/receipts', 'GR001', 'received')).toBe(
    '/warehouse/receipts?keyword=GR001&tab=received',
  );
  expect(listHref('/purchase/shipments', 'SD 01')).toBe(
    '/purchase/shipments?keyword=SD+01',
  );
});
