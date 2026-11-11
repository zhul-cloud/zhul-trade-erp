import { render, screen } from '@testing-library/react';
import React from 'react';
import { MemoryRouter, useLocation } from 'react-router';
import { useTabActive } from './hooks';
import { Pane } from './index';

jest.mock('@umijs/max', () => ({}));

const h = React.createElement;

function Probe() {
  const { pathname, search } = useLocation();
  const active = useTabActive();
  return h(
    'span',
    { 'data-testid': 'probe' },
    `${pathname}${search}|${active ? '可见' : '隐藏'}`,
  );
}

/** 在地址 url 下渲染一个缓存了 cachedPath 页面的页面区 */
const mount = (url: string, cachedPath: string, active: boolean) => {
  const [pathname, search = ''] = cachedPath.split('?');
  const cached = {
    element: h(Probe),
    loc: {
      location: {
        pathname,
        search: search ? `?${search}` : '',
        hash: '',
        state: null,
        key: 'k',
      },
      navigationType: 'PUSH',
    },
  };
  render(
    h(
      MemoryRouter,
      { initialEntries: [url] },
      h(Pane, {
        entryKey: 'e1',
        menuPath: '/sales/orders',
        active,
        cached,
      } as any),
    ),
  );
  return screen.getByTestId('probe');
};

/** spec system/navigation「菜单标签页」：隐藏标签里的页面保留，地址固定为它自己的地址 */
it('隐藏的页面读到的仍是它自己的地址，并且不显示', () => {
  const probe = mount('/purchase/orders', '/sales/orders?status=2', false);
  expect(probe.textContent).toBe('/sales/orders?status=2|隐藏');
  expect(probe.parentElement?.style.display).toBe('none');
});

it('当前页面正常显示', () => {
  const probe = mount('/sales/orders/12', '/sales/orders/12', true);
  expect(probe.textContent).toBe('/sales/orders/12|可见');
  expect(probe.parentElement?.style.display).toBe('');
});
