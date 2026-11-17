import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { request } from '@umijs/max';
import { App } from 'antd';
import dayjs from 'dayjs';
import React from 'react';
import { AiCell, AiStartModal, aiBusy, aiWorking, elapsed } from './AiGenerate';

/** spec product/product-content「AI 生成资料与营销内容」：状态显示、重复发起拦截、批量确认 */

jest.mock('@umijs/max', () => ({ request: jest.fn() }));

const mocked = request as unknown as jest.Mock;
const job = (status: number, over: Record<string, unknown> = {}) =>
  ({
    id: 7,
    status,
    factsImported: false,
    marketingImported: false,
    ...over,
  }) as never;
const task = (productId: number, mpn: string, ai?: unknown) =>
  ({ productId, mpn, brandName: 'Siemens', status: 1, ai }) as never;

describe('AI 生成状态', () => {
  it('排队中、生成中、待确认不能再次发起；有排队或生成中时刷新', () => {
    expect([1, 2, 3, 4, 5, 6].map((s) => aiBusy(job(s)))).toEqual([
      true,
      true,
      true,
      false,
      false,
      false,
    ]);
    expect(aiBusy(undefined)).toBe(false);
    expect(aiWorking([task(1, 'A', job(3)), task(2, 'B', job(2))])).toBe(true);
    expect(aiWorking([task(1, 'A', job(3))])).toBe(false);
  });

  it('已用时长', () => {
    const now = dayjs('2026-11-17T10:10:14');
    expect(elapsed('2026-11-17T10:08:00', now)).toBe('2 分 14 秒');
    expect(elapsed('2026-11-17T10:10:00', now)).toBe('14 秒');
    expect(elapsed('2026-11-17T09:00:00', now)).toBe('1 小时 10 分');
    expect(elapsed(undefined, now)).toBe('');
  });

  it('列表单元格：待确认可查看结果，失败可重新生成，生成中可取消', () => {
    const onView = jest.fn();
    const onRetry = jest.fn();
    const onCancel = jest.fn();
    const props = { canEdit: true, onView, onRetry, onCancel };
    const { rerender } = render(
      React.createElement(AiCell, { ...props, job: job(3) }),
    );
    fireEvent.click(screen.getByRole('button', { name: '查看结果' }));
    expect(onView).toHaveBeenCalled();
    rerender(
      React.createElement(AiCell, {
        ...props,
        job: job(5, { failReason: 'AI 连接方式都不可用' }),
      }),
    );
    expect(screen.getByText('AI 连接方式都不可用')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: '重新生成' }));
    expect(onRetry).toHaveBeenCalled();
    rerender(
      React.createElement(AiCell, {
        ...props,
        job: job(1, { queueAhead: 2 }),
      }),
    );
    expect(screen.getByText(/前面还有 2 个/)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: '取消' }));
    expect(onCancel).toHaveBeenCalled();
    rerender(
      React.createElement(AiCell, {
        ...props,
        canEdit: false,
        job: job(2, { startedAt: dayjs().subtract(3, 'minute').format() }),
      }),
    );
    expect(screen.queryByRole('button', { name: '取消' })).toBeNull();
  });
});

describe('AiStartModal', () => {
  it('跳过正在生成的商品，只提交可以生成的', async () => {
    mocked.mockResolvedValue({ data: { created: 2, skipped: [] } });
    const onStarted = jest.fn();
    render(
      React.createElement(
        App,
        null,
        React.createElement(AiStartModal, {
          rows: [
            task(1, 'LXM32AD30N4'),
            task(2, 'DOP-107EG', job(3)),
            task(3, 'E3Z-D61', job(5)),
          ],
          onClose: jest.fn(),
          onStarted,
        }),
      ),
    );
    expect(screen.getByText('AI 生成 2 个商品')).toBeTruthy();
    expect(screen.getByText('上次失败，重新生成')).toBeTruthy();
    expect(screen.getByText(/DOP-107EG 正在生成或有待确认的结果/)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /开始生成（2）/ }));
    await waitFor(() => expect(onStarted).toHaveBeenCalled());
    expect(mocked.mock.calls[0][1].data).toEqual({ productIds: [1, 3] });
  });
});
