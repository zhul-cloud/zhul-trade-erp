import { render, screen } from '@testing-library/react';
import React from 'react';
import { ParseProgressCard, UnparsedAlert } from './ParseProgress';

describe('大附件解析的进度与未解析提示', () => {
  const states = (container: HTMLElement) =>
    Array.from(container.querySelectorAll('[data-stage]')).map(
      (el) =>
        `${el.getAttribute('data-stage')}:${el.getAttribute('data-state')}`,
    );

  it('显示已解析批数与四个阶段', () => {
    const { container } = render(
      <ParseProgressCard done={5} total={12} stage="extract" files={2} />,
    );
    expect(screen.getByText('已解析 5 / 12 批')).toBeTruthy();
    expect(states(container)).toEqual([
      'read:done',
      'extract:active',
      'merge:wait',
      'verify:wait',
    ]);
    expect(screen.getByText('2 个文件')).toBeTruthy();
  });

  it('读取附件阶段还没切批，不显示批数', () => {
    const { container } = render(
      <ParseProgressCard done={0} total={0} stage="read" files={1} />,
    );
    expect(screen.getByText('正在读取附件')).toBeTruthy();
    expect(states(container)[0]).toBe('read:active');
  });

  it('联网核验时前面三个阶段都已完成', () => {
    const { container } = render(
      <ParseProgressCard done={14} total={21} stage="verify" files={2} />,
    );
    expect(states(container)).toEqual([
      'read:done',
      'extract:done',
      'merge:done',
      'verify:active',
    ]);
  });

  it('旧版编排服务不报阶段时按提取型号显示', () => {
    const { container } = render(
      <ParseProgressCard done={1} total={4} files={1} />,
    );
    expect(states(container)[1]).toBe('extract:active');
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
