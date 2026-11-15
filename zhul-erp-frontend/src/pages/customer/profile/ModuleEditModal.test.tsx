import { fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { ContactModal } from './ContactsCard';
import ModuleEditModal from './ModuleEditModal';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  // 字典、时区等接口不返回
  request: jest.fn(() => new Promise(() => undefined)),
}));
jest.mock('@/pages/product/components/useCountries', () => ({
  useCountries: () => ({
    codeOf: () => 'AU',
    zhOf: () => '澳大利亚',
    labelOf: () => '澳大利亚',
    options: [],
  }),
}));

const h = React.createElement;

const profile = () => ({
  customerId: 7,
  name: 'Pacific Controls',
  country: 'AU',
  city: '',
  timezone: '',
  website: 'old.example.com',
  linkedin: '',
  currentBrands: [],
  protocols: [],
  voltages: [],
  certifications: [],
  derived: { topBrands: [] },
  completeness: { missing: [] },
});

/** audit UI-069：弹窗打开期间档案刷新（背调完成、向导重新加载），不覆盖正在编辑的表单 */
it('档案编辑弹窗：打开期间档案刷新不重置已改的字段', () => {
  const modal = (p: ReturnType<typeof profile>) =>
    h(
      App,
      null,
      h(ModuleEditModal, {
        open: true,
        module: 1,
        profile: p as never,
        onClose: jest.fn(),
        onDone: jest.fn(),
      }),
    );
  const view = render(modal(profile()));
  const input = screen.getByPlaceholderText('example.com') as HTMLInputElement;
  expect(input.value).toBe('old.example.com');
  fireEvent.change(input, { target: { value: 'new.example.com' } });
  view.rerender(modal(profile()));
  expect(
    (screen.getByPlaceholderText('example.com') as HTMLInputElement).value,
  ).toBe('new.example.com');
});

/** audit UI-069：联系人弹窗打开期间档案刷新（联系人对象换了新引用），不覆盖正在编辑的表单 */
it('联系人弹窗：打开期间档案刷新不重置已改的字段', () => {
  const contact = () => ({ id: 5, name: 'Tom Lee', primary: true });
  const modal = (c: ReturnType<typeof contact>) =>
    h(
      App,
      null,
      h(ContactModal, {
        open: true,
        customerId: 7,
        customerName: 'Pacific Controls',
        contact: c as never,
        first: false,
        onClose: jest.fn(),
        onDone: jest.fn(),
      }),
    );
  const view = render(modal(contact()));
  const input = screen.getByDisplayValue('Tom Lee') as HTMLInputElement;
  fireEvent.change(input, { target: { value: 'Tommy Lee' } });
  view.rerender(modal(contact()));
  expect(screen.getByDisplayValue('Tommy Lee')).toBeTruthy();
});
