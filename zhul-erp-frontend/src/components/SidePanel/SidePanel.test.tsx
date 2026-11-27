import { act, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { useSidePanel } from './index';

const h = React.createElement;

const Demo: React.FC = () => {
  const panel = useSidePanel('test_side_panel', '作战卡');
  return h('div', null, panel.button, panel.panel(h('p', null, '面板内容')));
};

describe('可收起的右侧面板（D74）', () => {
  beforeEach(() => localStorage.clear());

  it('默认收起；点按钮打开并记住，下次打开页面仍是打开的', () => {
    const { unmount } = render(h(Demo));
    expect(screen.queryByText('面板内容')).toBeNull();
    const btn = screen.getByRole('button', { name: '作战卡' });
    expect(btn.getAttribute('aria-pressed')).toBe('false');
    act(() => {
      fireEvent.click(btn);
    });
    expect(screen.getByText('面板内容')).toBeTruthy();
    expect(localStorage.getItem('test_side_panel')).toBe('1');
    unmount();
    render(h(Demo));
    expect(screen.getByText('面板内容')).toBeTruthy();
  });

  it('再点一次按钮收起，并记住收起', () => {
    localStorage.setItem('test_side_panel', '1');
    render(h(Demo));
    act(() => {
      fireEvent.click(screen.getByRole('button', { name: '作战卡' }));
    });
    expect(localStorage.getItem('test_side_panel')).toBe('0');
  });
});
