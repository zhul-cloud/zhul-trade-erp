import { render, screen } from '@testing-library/react';
import React from 'react';
import { ErrorHint } from './EmptyHint';

describe('ErrorHint 按提示分辨状态', () => {
  it('网络、服务出错：加载失败 + 重试', () => {
    render(<ErrorHint message="网络连接失败" onRetry={() => {}} />);
    expect(screen.getByRole('button', { name: /重\s?试/ })).toBeTruthy();
  });

  it.each([
    '收款单不存在',
    '这个商品没有发品任务',
    '该期间还没结账',
    '没有权限查看',
  ])('「%s」不给重试', (message) => {
    render(<ErrorHint message={message} onRetry={() => {}} />);
    expect(screen.queryByRole('button', { name: /重\s?试/ })).toBeNull();
    expect(screen.getByText(message)).toBeTruthy();
  });
});
