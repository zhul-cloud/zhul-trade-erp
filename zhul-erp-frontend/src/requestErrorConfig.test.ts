import { message } from 'antd';
import { errorConfig } from './requestErrorConfig';

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

type Tuple = [unknown, (error: unknown) => unknown];
const onRejected = (
  errorConfig.responseInterceptors?.[0] as unknown as Tuple
)[1];
const handle = (e: unknown) =>
  errorConfig.errorConfig?.errorHandler?.(e as any, {} as any);

const caught = (fn: () => unknown) => {
  try {
    fn();
  } catch (e) {
    return e as any;
  }
  throw new Error('应当抛出错误');
};

describe('非 2xx 响应统一显示后端的中文原因', () => {
  beforeEach(() => jest.clearAllMocks());

  it('带后端统一格式的错误转成业务错误，保留原始响应', () => {
    const response = {
      status: 405,
      data: { code: 405, message: '接口不支持 POST 请求，请使用 PUT' },
    };
    const e = caught(() =>
      onRejected({ message: 'Request failed with status code 405', response }),
    );
    expect(e.name).toBe('BizError');
    expect(e.message).toBe('接口不支持 POST 请求，请使用 PUT');
    expect(e.response.status).toBe(405);
    handle(e);
    expect(message.error).toHaveBeenCalledWith(
      '接口不支持 POST 请求，请使用 PUT',
    );
  });

  it('响应体不是后端格式时原样抛出，提示里给出中文与状态码', () => {
    const original = {
      message: 'Request failed with status code 502',
      response: { status: 502, data: '<html>Bad Gateway</html>' },
    };
    const e = caught(() => onRejected(original));
    expect(e).toBe(original);
    handle(e);
    expect(message.error).toHaveBeenCalledWith(
      '请求失败（HTTP 502），请稍后重试',
    );
  });

  it('文件下载失败（响应体是 Blob）不做转换', () => {
    const original = { response: { status: 404, data: new Blob(['x']) } };
    expect(caught(() => onRejected(original))).toBe(original);
  });

  it('没有响应时提示中文', () => {
    handle({ request: {} });
    expect(message.error).toHaveBeenCalledWith('服务器没有响应，请稍后重试');
  });
});
