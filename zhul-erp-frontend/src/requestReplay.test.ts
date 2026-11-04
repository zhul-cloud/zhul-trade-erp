import axios from 'axios';
import { errorConfig } from './requestErrorConfig';

/** audit UI-002：令牌刷新后的重放请求与首次请求走同一套业务码校验 */
jest.mock('antd', () => ({
  message: { error: jest.fn(), warning: jest.fn() },
  notification: { open: jest.fn() },
}));
jest.mock('@umijs/max', () => ({
  getIntl: () => ({
    formatMessage: (d: { defaultMessage: string }) => d.defaultMessage,
  }),
}));
jest.mock('@/services/zhul/auth', () => ({ refreshToken: jest.fn() }));
jest.mock('axios', () => ({ __esModule: true, default: jest.fn() }));

const onFulfilled = (
  errorConfig.responseInterceptors?.[0] as unknown as [
    (r: unknown) => Promise<any>,
  ]
)[0];
const replay = axios as unknown as jest.Mock;
const { refreshToken } = jest.requireMock('@/services/zhul/auth');

const expired = () => ({
  config: { url: '/api/v1/finance/other-incomes/1', method: 'put' },
  data: { code: 401, message: '未登录或登录已过期' },
});
const settled = (p: Promise<unknown>, ms = 50) =>
  Promise.race([
    p.then(
      (v) => ({ state: 'resolved', v }),
      (e) => ({ state: 'rejected', e }),
    ),
    new Promise((r) => setTimeout(() => r({ state: 'pending' }), ms)),
  ]) as Promise<{ state: string; v?: any; e?: any }>;

describe('令牌刷新后的重放', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    window.history.pushState({}, '', '/finance/other-incomes');
    refreshToken.mockResolvedValue({
      code: 0,
      data: { accessToken: 'new-token' },
    });
  });

  it('重放返回业务错误（403）时按真实原因拒绝，不当成功', async () => {
    replay.mockResolvedValue({ data: { code: 403, message: '没有权限' } });
    const r = await settled(onFulfilled(expired()));
    expect(r.state).toBe('rejected');
    expect(r.e.name).toBe('BizError');
    expect(r.e.message).toBe('没有权限');
  });

  it('重放成功只返回一次结果，带上新令牌', async () => {
    const ok = { data: { code: 0, data: { id: 1 } } };
    replay.mockResolvedValue(ok);
    const r = await settled(onFulfilled(expired()));
    expect(r).toEqual({ state: 'resolved', v: ok });
    expect(replay).toHaveBeenCalledTimes(1);
    expect(replay.mock.calls[0][0].headers.Authorization).toBe(
      'Bearer new-token',
    );
  });

  it('重放遇到 HTTP 错误时保留原错误，不退出刚刷新的登录', async () => {
    const http500 = Object.assign(
      new Error('Request failed with status code 500'),
      {
        response: { status: 500, data: '<html>err</html>' },
      },
    );
    replay.mockRejectedValue(http500);
    const r = await settled(onFulfilled(expired()));
    expect(r).toEqual({ state: 'rejected', e: http500 });
    expect(window.location.pathname).toBe('/finance/other-incomes');
  });

  it('重放仍返回 401 时按登录过期处理，请求不再返回', async () => {
    replay.mockResolvedValue({ data: { code: 401, message: '未登录' } });
    const r = await settled(onFulfilled(expired()));
    expect(r.state).toBe('pending');
  });

  it('刷新失败时不执行调用方的成功回调', async () => {
    refreshToken.mockResolvedValue({ code: 401 });
    const r = await settled(onFulfilled(expired()));
    expect(r.state).not.toBe('resolved');
    expect(replay).not.toHaveBeenCalled();
  });

  it('并发的多个 401 只刷新一次', async () => {
    replay.mockResolvedValue({ data: { code: 0, data: null } });
    await Promise.all([onFulfilled(expired()), onFulfilled(expired())]);
    expect(refreshToken).toHaveBeenCalledTimes(1);
    expect(replay).toHaveBeenCalledTimes(2);
  });
});
