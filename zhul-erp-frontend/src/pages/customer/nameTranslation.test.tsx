import { act, renderHook } from '@testing-library/react';
import { request } from '@umijs/max';
import { useNameTranslation } from './nameTranslation';

jest.mock('@umijs/max', () => ({ request: jest.fn() }));

beforeEach(() => jest.useFakeTimers());
afterEach(() => jest.useRealTimers());

const run = async (polls: number) => {
  const { result } = renderHook(() => useNameTranslation());
  let out: unknown;
  await act(async () => {
    const p = result.current.translate('ABC Automation GmbH').then((r) => {
      out = r;
    });
    for (let i = 0; i < polls; i += 1) {
      await jest.advanceTimersByTimeAsync(1500);
    }
    await p;
  });
  return out;
};

/** 「AI 翻译」：提交后轮询，完成时返回译名（feedback-1116-batch1） */
it('完成时返回译名', async () => {
  (request as jest.Mock).mockImplementation(
    (_url: string, opts: { method: string }) =>
      Promise.resolve(
        opts.method === 'POST'
          ? { data: { taskId: 5, status: 1 } }
          : {
              data: {
                taskId: 5,
                status: 3,
                results: [{ key: 'preview', text: 'ABC 自动化有限公司' }],
              },
            },
      ),
  );
  expect(await run(1)).toEqual({ text: 'ABC 自动化有限公司' });
  expect((request as jest.Mock).mock.calls[0][1].data).toEqual({
    name: 'ABC Automation GmbH',
  });
});

it('20 秒还没结果提示超时', async () => {
  (request as jest.Mock).mockImplementation(
    (_url: string, opts: { method: string }) =>
      Promise.resolve({
        data: { taskId: 5, status: opts.method === 'POST' ? 1 : 2 },
      }),
  );
  expect(await run(14)).toEqual({ error: '翻译超时，请稍后再试' });
});

it('任务失败时带上原因', async () => {
  (request as jest.Mock).mockImplementation(
    (_url: string, opts: { method: string }) =>
      Promise.resolve({
        data:
          opts.method === 'POST'
            ? { taskId: 5, status: 1 }
            : { taskId: 5, status: 4, errorMessage: '额度不足' },
      }),
  );
  expect(await run(1)).toEqual({ error: '翻译失败：额度不足，请稍后再试' });
});
