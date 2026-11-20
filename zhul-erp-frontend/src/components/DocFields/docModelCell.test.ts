import { render, screen } from '@testing-library/react';
import React from 'react';
import DocModelCell from './DocModelCell';

/** spec product/document-product-link「单据上的商品标记」：候选商品标「候选」，型号不链到商品档案 */
let mockAccess: Record<string, boolean> = {};
jest.mock('@umijs/max', () => ({
  Link: ({ to, children }: { to: string; children?: unknown }) =>
    require('react').createElement('a', { href: to }, children),
  useAccess: () => mockAccess,
}));
jest.mock('@/pages/operations/media/ViewMediaLink', () => () => null);

it('候选商品：标「候选」，有审核权限时链到候选池，型号不链商品档案', () => {
  mockAccess = { productList: true, 'product:candidate:review': true };
  render(
    React.createElement(DocModelCell, {
      model: '6ES7214-0XB0',
      productId: 900,
      candidate: true,
    }),
  );
  const tag = screen.getByText('候选');
  expect(tag.closest('a')?.getAttribute('href')).toBe(
    '/product/candidates?keyword=900',
  );
  expect(screen.getByText('6ES7214-0XB0').closest('a')).toBeNull();
});

it('已建档商品：型号链到商品档案，没有「候选」', () => {
  mockAccess = { productList: true };
  render(
    React.createElement(DocModelCell, {
      model: '6ES7214-0XB0',
      productId: 900,
    }),
  );
  expect(screen.queryByText('候选')).toBeNull();
  expect(
    screen.getByText('6ES7214-0XB0').closest('a')?.getAttribute('href'),
  ).toBe('/product/products/900');
});
