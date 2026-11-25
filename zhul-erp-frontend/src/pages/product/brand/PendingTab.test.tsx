import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { brandApi, pendingBrandApi } from '../service';
import PendingTab from './PendingTab';

const h = React.createElement;

jest.mock('@umijs/max', () => ({ request: jest.fn() }));
jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  brandApi: { options: jest.fn() },
  pendingBrandApi: { linkAsAlias: jest.fn(), ignore: jest.fn() },
}));

const row = (name: string, extra: object) => ({
  pendingKey: name.toLowerCase(),
  name,
  supplierCount: 0,
  inquiryCount: 0,
  orderCount: 0,
  purchaseCount: 0,
  firstSeen: '2026-11-01T10:00:00',
  ...extra,
});

const PENDING = [
  row('Beckhof', {
    inquiryCount: 35,
    orderCount: 2,
    suggestKind: 'similar',
    suggestText: '相近：Beckhoff（拼写接近）',
    suggestBrandId: 7,
    suggestBrandName: 'Beckhoff',
  }),
  row('未知品牌', {
    inquiryCount: 21,
    suggestKind: 'notBrand',
    suggestText: '不是品牌名，建议标为不是品牌',
  }),
  row('Hengstler', { supplierCount: 2 }),
];

const mount = (reload = jest.fn(), onLinked = jest.fn()) =>
  render(
    h(
      App,
      null,
      h(PendingTab, {
        pending: PENDING as never,
        loading: false,
        reload,
        canAdd: true,
        onCreate: jest.fn(),
        onLinked,
      }),
    ),
  );

beforeEach(() => {
  jest.clearAllMocks();
  (brandApi.options as jest.Mock).mockResolvedValue([
    { id: 7, brandName: 'Beckhoff', aliases: [] },
  ]);
});

/** openspec brand-unify：来源筛选与搜索只在前端过滤 */
it('按来源筛选、按写法搜索', () => {
  mount();
  fireEvent.click(screen.getByText('供应商主营'));
  expect(screen.queryByText('Beckhof')).toBeNull();
  expect(screen.getByText('Hengstler')).toBeTruthy();
  fireEvent.click(screen.getByText('全部来源'));
  fireEvent.change(screen.getByPlaceholderText('搜索写法'), {
    target: { value: 'beck' },
  });
  expect(screen.getByText('Beckhof')).toBeTruthy();
  expect(screen.queryByText('Hengstler')).toBeNull();
});

it('点相近建议：预选建议品牌、列出影响，确认后统一', async () => {
  const reload = jest.fn();
  const onLinked = jest.fn();
  (pendingBrandApi.linkAsAlias as jest.Mock).mockResolvedValue(1);
  mount(reload, onLinked);

  await act(async () => {
    fireEvent.click(screen.getByText('相近：Beckhoff（拼写接近）'));
  });
  const dialog = await screen.findByRole('dialog');
  expect(within(dialog).getByText('把「Beckhof」设为别名')).toBeTruthy();
  expect(within(dialog).getByText('35 行')).toBeTruthy();
  await act(async () => {
    fireEvent.click(within(dialog).getByRole('button', { name: '设为别名' }));
  });
  expect(pendingBrandApi.linkAsAlias).toHaveBeenCalledWith(['beckhof'], 7);
  expect(reload).toHaveBeenCalled();
  expect(onLinked).toHaveBeenCalled();
});

it('不是品牌：确认后提交，单据保持原样', async () => {
  const reload = jest.fn();
  (pendingBrandApi.ignore as jest.Mock).mockResolvedValue(1);
  mount(reload);

  fireEvent.click(screen.getByText('不是品牌名，建议标为不是品牌'));
  const dialog = await screen.findByRole('dialog');
  expect(within(dialog).getByText('涉及的单据（保持原样）')).toBeTruthy();
  await act(async () => {
    fireEvent.click(
      within(dialog).getByRole('button', { name: '标为不是品牌' }),
    );
  });
  expect(pendingBrandApi.ignore).toHaveBeenCalledWith(['未知品牌']);
  expect(reload).toHaveBeenCalled();
});

it('没有勾选时不显示批量操作（批量操作随勾选出现）', () => {
  mount();
  expect(screen.queryByRole('button', { name: '批量设为别名' })).toBeNull();
  expect(screen.queryByRole('button', { name: '批量标为不是品牌' })).toBeNull();
});
