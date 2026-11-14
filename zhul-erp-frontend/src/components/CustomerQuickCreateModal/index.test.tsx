import { fireEvent, render, screen } from '@testing-library/react';
import { request } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import CustomerQuickCreateModal from './index';

jest.mock('@umijs/max', () => ({
  request: jest.fn(),
  useModel: () => ({ initialState: {} }),
}));

let mockUnits: Record<string, unknown>;
jest.mock('@/utils/businessUnit', () => ({
  ...jest.requireActual('@/utils/businessUnit'),
  useBusinessUnits: () => [mockUnits, () => undefined],
}));

const base = {
  enabled: true,
  units: [
    { id: 7, name: '工控事业部' },
    { id: 8, name: '阿里国际站' },
  ],
  minSharePercent: 20,
  statsUnitIds: [],
  statsTotal: true,
};

const show = () =>
  render(
    React.createElement(
      App,
      null,
      React.createElement(CustomerQuickCreateModal, {
        open: true,
        onOpenChange: () => undefined,
        onCreated: () => undefined,
      }),
    ),
  );

beforeEach(() => {
  (request as jest.Mock).mockReset().mockResolvedValue({ data: [] });
});

/** UI-028：公共部门用户在多事业部公司建客户，要能选所属事业部，否则后端拒绝 */
describe('快速新建客户的所属事业部（UI-028）', () => {
  it('本人不属于事业部且有多个事业部时必须选择', async () => {
    mockUnits = { ...base };
    show();
    expect(
      await screen.findByRole('combobox', { name: '所属事业部' }),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /创\s?建/ }));
    expect(await screen.findByText('请选择所属事业部')).toBeTruthy();
    expect(request).not.toHaveBeenCalledWith(
      '/api/v1/masterdata/customers',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('本人属于事业部时不显示（归入本人事业部）', async () => {
    mockUnits = { ...base, myUnitId: 7 };
    show();
    await screen.findByText('客户名称（英文）');
    expect(screen.queryByText('所属事业部')).toBeNull();
  });

  it('只有一个事业部或未启用事业部时不显示', async () => {
    mockUnits = { ...base, units: [base.units[0]] };
    const { unmount } = show();
    await screen.findByText('客户名称（英文）');
    expect(screen.queryByText('所属事业部')).toBeNull();
    unmount();
    mockUnits = { ...base, enabled: false };
    show();
    await screen.findByText('客户名称（英文）');
    expect(screen.queryByText('所属事业部')).toBeNull();
  });
});
