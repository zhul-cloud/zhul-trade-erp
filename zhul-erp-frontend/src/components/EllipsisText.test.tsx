import { act, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import EllipsisText from './EllipsisText';

/** audit UI-041：长文本单行省略，只有真被截断时悬停才显示全文 */
const mount = (text: string, cut: boolean) => {
  render(React.createElement(EllipsisText, { text }));
  const el = screen.getByText(text);
  Object.defineProperty(el, 'clientWidth', { configurable: true, value: 100 });
  Object.defineProperty(el, 'scrollWidth', {
    configurable: true,
    value: cut ? 180 : 100,
  });
  return el;
};

const hover = async (el: HTMLElement) => {
  await act(async () => {
    fireEvent.mouseEnter(el);
    await new Promise((r) => setTimeout(r, 200));
  });
};

it('被截断时悬停显示全文', async () => {
  const el = mount('阿里巴巴国际站 · L1+', true);
  await hover(el);
  expect(document.querySelector('.ant-tooltip')?.textContent).toBe(
    '阿里巴巴国际站 · L1+',
  );
});

it('没被截断时不弹提示', async () => {
  const el = mount('独立站', false);
  await hover(el);
  expect(document.querySelector('.ant-tooltip')).toBeNull();
});

it('单行省略样式', () => {
  const el = mount('很长的说明文字', false);
  expect(el.style.whiteSpace).toBe('nowrap');
  expect(el.style.textOverflow).toBe('ellipsis');
  expect(el.style.overflow).toBe('hidden');
});

it('没有内容时显示破折号', () => {
  render(React.createElement(EllipsisText, { text: '' }));
  expect(screen.getByText('—')).toBeTruthy();
});
