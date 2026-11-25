import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import dayjs from 'dayjs';
import React from 'react';
import { setToken } from '@/utils/tokenStore';
import { CountryText } from './CountryText';
import { EmptyState, listEmpty, renderEmpty, tableEmpty } from './EmptyState';
import { SelectionCount } from './ListLayout';
import { RowActions, rowActionsWidth } from './RowActions';
import { rowAlert } from './RowAlert';
import { formatShortTime, UserName } from './UserName';

const mockRequest = jest.fn();
jest.mock('@umijs/max', () => ({
  request: (...args: unknown[]) => mockRequest(...args),
  useLocation: () => ({ pathname: '/' }),
  useModel: () => ({ initialState: {} }),
}));

jest.mock('@/pages/product/components/useCountries', () => {
  const list = [
    { code: 'IN', nameEn: 'India', nameZh: '印度' },
    { code: 'DE', nameEn: 'Germany', nameZh: '德国' },
  ];
  return {
    useCountries: () => ({
      find: (v?: string) =>
        v
          ? list.find(
              (c) =>
                c.nameEn.toLowerCase() === v.toLowerCase() ||
                c.code.toLowerCase() === v.toLowerCase() ||
                c.nameZh === v,
            )
          : undefined,
    }),
  };
});

const h = React.createElement;

describe('国家', () => {
  it('国旗加中文名，英文名、代码、中文名都认', () => {
    const { container } = render(
      h(
        'div',
        null,
        h(CountryText, { value: 'India' }),
        h(CountryText, { value: 'de' }),
      ),
    );
    expect(screen.getByText('印度')).toBeTruthy();
    expect(screen.getByText('德国')).toBeTruthy();
    expect(container.querySelector('img[src="/flags/in.svg"]')).toBeTruthy();
    expect(container.querySelector('img[src="/flags/de.svg"]')).toBeTruthy();
  });

  it('清单里没有的原样显示、不画国旗，空值显示破折号', () => {
    const { container } = render(
      h(
        'div',
        null,
        h(CountryText, { value: 'Atlantis' }),
        h(CountryText, { value: '' }),
      ),
    );
    expect(screen.getByText('Atlantis')).toBeTruthy();
    expect(container.querySelector('img')).toBeNull();
    expect(screen.getByText('—')).toBeTruthy();
  });
});

describe('空状态', () => {
  it('四种情况各有默认文字，可带下一步', () => {
    render(
      h(
        'div',
        null,
        h(EmptyState, {
          kind: 'noResult',
          action: h('button', null, '清空筛选'),
        }),
        h(EmptyState, { kind: 'error' }),
        h(EmptyState, {
          kind: 'forbidden',
          description: '需要「应收管理」菜单权限',
        }),
      ),
    );
    expect(screen.getByText('没有符合条件的结果')).toBeTruthy();
    expect(screen.getByText('清空筛选')).toBeTruthy();
    expect(screen.getByText('加载失败')).toBeTruthy();
    expect(screen.getByText('需要「应收管理」菜单权限')).toBeTruthy();
  });

  it('全站默认：表格用完整空状态，下拉只占一行', () => {
    render(h('div', null, renderEmpty('Table'), renderEmpty('Select')));
    expect(screen.getAllByText('暂无数据')).toHaveLength(2);
  });
});

describe('操作列', () => {
  it('最多两个图标，不适用的留空位；危险项排到「更多」末尾', async () => {
    const onMore = jest.fn();
    const onView = jest.fn();
    const { container } = render(
      h(
        App,
        null,
        h(RowActions, {
          inline: [
            false,
            { key: 'view', label: '查看', icon: 'V', onClick: onView },
            { key: 'x', label: '第三个', icon: 'X', onClick: jest.fn() },
          ],
          more: [
            { key: 'del', label: '删除', danger: true },
            { key: 'copy', label: '复制' },
            false,
          ],
          onMore,
        }),
      ),
    );
    expect(screen.queryByLabelText('第三个')).toBeNull();
    expect(container.querySelector('span[aria-hidden]')).toBeTruthy();
    fireEvent.click(screen.getByLabelText('查看'));
    expect(onView).toHaveBeenCalled();
    fireEvent.click(screen.getByLabelText('更多'));
    const items = await screen.findAllByRole('menuitem');
    expect(items.map((i) => i.textContent)).toEqual(['复制', '删除']);
    fireEvent.click(items[1]);
    expect(onMore).toHaveBeenCalledWith('del');
  });

  it('置灰的操作不触发，悬停名称带原因', () => {
    const onClick = jest.fn();
    render(
      h(RowActions, {
        inline: [
          {
            key: 'e',
            label: '编辑',
            icon: 'E',
            onClick,
            disabledReason: '已结算',
          },
        ],
      }),
    );
    fireEvent.click(screen.getByLabelText('编辑：已结算'));
    expect(onClick).not.toHaveBeenCalled();
  });

  it('列宽按图标位数算', () => {
    // 原型：3 位时操作列宽 136
    expect(rowActionsWidth(3)).toBe(136);
    expect(rowActionsWidth(1)).toBe(56);
  });
});

describe('人名与时间', () => {
  it('系统写入显示「系统」，账号换成姓名，查不到显示账号；同一轮只查一次', async () => {
    mockRequest.mockResolvedValue({ data: { wangmin: '王敏' } });
    render(
      h(
        'div',
        null,
        h('span', null, h(UserName, { value: 'sys' })),
        h('span', null, h(UserName, { value: 'wangmin' })),
        h('span', null, h(UserName, { value: 'wangmin' })),
        h('span', null, h(UserName, { value: 'ghost@example.com' })),
      ),
    );
    expect(screen.getByText('系统')).toBeTruthy();
    await act(async () => {
      await new Promise((r) => setTimeout(r, 0));
    });
    expect(screen.getAllByText('王敏')).toHaveLength(2);
    expect(screen.getByText('ghost@example.com')).toBeTruthy();
    expect(mockRequest).toHaveBeenCalledTimes(1);
    expect(mockRequest.mock.calls[0][1].params.usernames).toBe(
      'wangmin,ghost@example.com',
    );
  });

  it('请求失败先显示账号、下次挂载再查；换登录后清空缓存重新查', async () => {
    mockRequest.mockReset();
    mockRequest.mockRejectedValueOnce(new Error('503'));
    const first = render(h('span', null, h(UserName, { value: 'zhaolei' })));
    expect(await screen.findByText('zhaolei')).toBeTruthy();
    first.unmount();
    mockRequest.mockResolvedValue({ data: { zhaolei: '赵磊' } });
    render(h('span', null, h(UserName, { value: 'zhaolei' })));
    expect(await screen.findByText('赵磊')).toBeTruthy();
    expect(mockRequest).toHaveBeenCalledTimes(2);
    mockRequest.mockResolvedValue({ data: { zhaolei: '赵磊（新租户）' } });
    act(() => {
      setToken('t', false);
    });
    expect(await screen.findByText('赵磊（新租户）')).toBeTruthy();
  });

  it('短时间：当年写月日时分，跨年写日期', () => {
    const y = dayjs().year();
    expect(formatShortTime(`${y}-11-21 10:20:30`)).toBe('11-21 10:20');
    expect(formatShortTime('2020-11-21 10:20:30')).toBe('2020-11-21');
    expect(formatShortTime(null)).toBe('—');
  });
});

describe('行内提醒', () => {
  it('有提醒才加色条样式，原有的行属性一起返回', () => {
    expect(rowAlert('error')).toEqual({ className: 'zhul-row-alert-error' });
    expect(rowAlert(false)).toEqual({});
    const onClick = jest.fn();
    expect(rowAlert('warning', { onClick, className: 'x' })).toEqual({
      onClick,
      className: 'x zhul-row-alert-warning',
    });
    expect(rowAlert(null, { onClick })).toEqual({ onClick });
  });
});

describe('列表空状态', () => {
  it('加载中不显示文字；出错给重试；有筛选给清空筛选；否则用首次为空的说明', () => {
    const onRetry = jest.fn();
    const onClear = jest.fn();
    const { container, rerender } = render(
      h('div', null, listEmpty({ loading: true })),
    );
    expect(container.textContent).toBe('');
    rerender(h('div', null, listEmpty({ error: true, onRetry })));
    fireEvent.click(screen.getByRole('button', { name: /重\s*试/ }));
    expect(onRetry).toHaveBeenCalled();
    expect(screen.getByRole('alert')).toBeTruthy();
    rerender(
      h('div', null, listEmpty({ filtered: true, noun: '订单', onClear })),
    );
    expect(screen.getByText('没有符合条件的订单')).toBeTruthy();
    fireEvent.click(screen.getByText('清空筛选'));
    expect(onClear).toHaveBeenCalled();
    rerender(h('div', null, listEmpty({ empty: { title: '还没有订单' } })));
    expect(screen.getByText('还没有订单')).toBeTruthy();
  });
});

describe('表格空状态 tableEmpty', () => {
  it('加载中不显示；一句话按「，」「；」拆成标题与说明；提到筛选的为无结果', () => {
    const { container, rerender } = render(
      h('div', null, tableEmpty('没有符合条件的订单，换个筛选条件试试', true)),
    );
    expect(container.textContent).toBe('');
    rerender(
      h('div', null, tableEmpty('没有符合条件的订单，换个筛选条件试试', false)),
    );
    expect(screen.getByText('没有符合条件的订单')).toBeTruthy();
    expect(screen.getByText('换个筛选条件试试')).toBeTruthy();
    const custom = h('span', null, '自定义空状态');
    rerender(h('div', null, tableEmpty(custom, { spinning: false })));
    expect(screen.getByText('自定义空状态')).toBeTruthy();
  });
});

describe('SelectionCount', () => {
  it('显示已选数量与量词，点「取消选择」清空', () => {
    const onClear = jest.fn();
    render(
      h(SelectionCount, {
        count: 3,
        unit: '张',
        extra: h('span', null, '客户 A'),
        onClear,
      }),
    );
    expect(screen.getByText('3')).toBeTruthy();
    expect(screen.getByText('客户 A')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: '取消选择' }));
    expect(onClear).toHaveBeenCalledTimes(1);
  });
});
