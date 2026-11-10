import { render, screen } from '@testing-library/react';
import React from 'react';
import { ParseProgressCard, UnparsedAlert } from './ParseProgress';

describe('大附件解析的进度与未解析提示', () => {
  it('显示已解析批数', () => {
    render(<ParseProgressCard done={5} total={12} />);
    expect(screen.getByText('已解析 5 / 12 批')).toBeTruthy();
  });

  it('列出未解析的部分', () => {
    render(
      <UnparsedAlert
        parts={[
          { file: '清单.pdf', range: '第 15 页', reason: '页面损坏，无法读取' },
          {
            file: '',
            range: '《报价单.xlsx》第 1201–1400 行',
            reason: '解析失败',
          },
        ]}
      />,
    );
    expect(screen.getByText('2 处内容未能解析，请手动补充')).toBeTruthy();
    expect(
      screen.getByText('《清单.pdf》第 15 页：页面损坏，无法读取'),
    ).toBeTruthy();
    expect(
      screen.getByText('《报价单.xlsx》第 1201–1400 行：解析失败'),
    ).toBeTruthy();
  });

  it('没有未解析部分时不显示', () => {
    const { container } = render(<UnparsedAlert parts={[]} />);
    expect(container.textContent).toBe('');
  });
});
