import { render, screen } from '@testing-library/react';
import React from 'react';
import { BlockView, previousHint } from './BlockWidget';

jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }));

/** 总经理工作台「客户 · 本月成交额」：客户列显示名称、国家 / 地区、主联系人 */
it('表格第一列是客户时按客户列显示', () => {
  render(
    React.createElement(BlockView, {
      block: {
        type: 'table',
        title: '客户 · 本月成交额',
        table: {
          columns: [
            { title: '客户', align: 'l' },
            { title: '成交额', align: 'r', format: 'money' },
            { title: '订单', align: 'r', format: 'count' },
          ],
          rows: [['Pacific Controls', 1200, 2]],
          customers: [
            { name: 'Pacific Controls', country: 'Australia', contact: 'Tom' },
          ],
        },
      },
    }),
  );
  expect(screen.getByText('Pacific Controls')).toBeTruthy();
  // 本分支的客户列（销售订单原型 A1）：国家 / 地区与主联系人在名称下面一行
  // 国家用公共组件 CountryText（国旗 + 国名），和联系人在同一行
  expect(screen.getByText('Australia').closest('div')?.textContent).toBe(
    'Australia · Tom',
  );
});

describe('数字卡第三行：算不出较上月时写上月（D59）', () => {
  it('有变化时不写上月；上月为 0 写上月的数；上月没有数据时写明；不比较的指标不写', () => {
    expect(
      previousHint({
        label: '毛利',
        compared: true,
        delta: 12.4,
        previous: 100,
      }),
    ).toBeUndefined();
    expect(
      previousHint({
        label: '实收回款',
        format: 'money',
        compared: true,
        delta: null,
        previous: 0,
      }),
    ).toBe('上月 CNY 0.00');
    expect(
      previousHint({
        label: '毛利率',
        format: 'percent',
        compared: true,
        previous: null,
      }),
    ).toBe('上月没有可比数据');
    expect(previousHint({ label: '待发货', value: 3 })).toBeUndefined();
  });
});
