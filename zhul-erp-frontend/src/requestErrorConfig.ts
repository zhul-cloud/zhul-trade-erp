import type { RequestOptions } from '@@/plugin-request/request';
import type { RequestConfig } from '@umijs/max';
import { getIntl } from '@umijs/max';
import { message, notification } from 'antd';
import axios from 'axios';
import { refreshToken as refreshTokenApi } from '@/services/zhul/auth';
import { clearToken, getToken, updateTokenInPlace } from '@/utils/tokenStore';

// 错误处理方案： 错误类型
enum ErrorShowType {
  SILENT = 0,
  WARN_MESSAGE = 1,
  ERROR_MESSAGE = 2,
  NOTIFICATION = 3,
  REDIRECT = 9,
}
// 与后端约定的响应数据格式：{ code: 0 表示成功, message, data }
interface ResponseStructure {
  code: number;
  message: string;
  data: unknown;
}

// errorThrower 挂在 Error 实例上的附加信息
interface BizErrorInfo {
  errorCode: number;
  errorMessage: string;
  showType: ErrorShowType;
  data: unknown;
}

function buildBizError(code: number, msg: string, data: unknown): Error {
  const error: any = new Error(msg);
  error.name = 'BizError';
  error.info = {
    errorCode: code,
    errorMessage: msg,
    showType: ErrorShowType.ERROR_MESSAGE,
    data,
  };
  return error;
}

// Token 刷新单飞：并发的多个 401 只触发一次 /auth/refresh 调用
let refreshingPromise: Promise<string | null> | null = null;

function getRefreshedAccessToken(): Promise<string | null> {
  if (!refreshingPromise) {
    refreshingPromise = refreshTokenApi()
      .then((res) => {
        if (res.code === 0 && res.data?.accessToken) {
          updateTokenInPlace(res.data.accessToken);
          return res.data.accessToken;
        }
        return null;
      })
      .catch(() => null)
      .finally(() => {
        refreshingPromise = null;
      });
  }
  return refreshingPromise;
}

function redirectToLoginExpired() {
  clearToken();
  localStorage.removeItem('zhul_user');
  const { pathname, search, hash } = window.location;
  window.location.href = `/login?redirect=${encodeURIComponent(pathname + search + hash)}&reason=token_expired`;
}

/**
 * @name 错误处理
 * pro 自带的错误处理， 可以在这里做自己的改动
 *
 * 注意：umi 生成的 .umi/plugin-request/request.ts 只在响应体 `success === false`
 * 时才会调用下面的 errorThrower（这是旧版 ant-design-pro 的响应契约）。本项目后端返回
 * `{ code, message, data }`（code !== 0 表示失败），不带 success 字段，因此 errorThrower
 * 实际不会被触发 —— 真正生效的失败拦截逻辑在下方 responseInterceptors 中实现。
 * errorThrower 仍保留、按新契约实现，避免未来该生成文件调整后再次踩坑。
 * @doc https://umijs.org/docs/max/request#配置
 */
export const errorConfig: RequestConfig = {
  // 错误处理： umi@3 的错误处理方案。
  errorConfig: {
    // 错误抛出（见上方说明，当前实际不会被 umi 生成的拦截器调用）
    errorThrower: (res) => {
      const { code, message: msg, data } = res as unknown as ResponseStructure;
      if (code !== 0) {
        throw buildBizError(code, msg, data);
      }
    },
    // 错误接收及处理
    errorHandler: (error: any, opts: any) => {
      if (opts?.skipErrorHandler) throw error;
      // 我们的 errorThrower 抛出的错误。
      if (error.name === 'BizError') {
        const errorInfo: BizErrorInfo | undefined = error.info;
        if (errorInfo) {
          const { errorMessage, errorCode } = errorInfo;
          switch (errorInfo.showType) {
            case ErrorShowType.SILENT:
              // do nothing
              break;
            case ErrorShowType.WARN_MESSAGE:
              message.warning(errorMessage);
              break;
            case ErrorShowType.ERROR_MESSAGE:
              message.error(errorMessage);
              break;
            case ErrorShowType.NOTIFICATION:
              notification.open({
                title: errorCode,
                description: errorMessage,
              });
              break;
            case ErrorShowType.REDIRECT:
              window.location.href = '/login';
              break;
            default:
              message.error(errorMessage);
          }
        }
      } else if (error.response) {
        // Axios 的错误
        // 请求成功发出且服务器也响应了状态码，但状态代码超出了 2xx 的范围
        message.error(`Response status:${error.response.status}`);
      } else if (typeof navigator !== 'undefined' && !navigator.onLine) {
        message.error(
          getIntl().formatMessage({
            id: 'app.request.offline',
            defaultMessage:
              'Network unavailable. Please check your connection and try again.',
          }),
        );
      } else if (error.request) {
        message.error('None response! Please retry.');
      } else {
        message.error('Request error, please retry.');
      }
    },
  },

  // 请求拦截器
  requestInterceptors: [
    (config: RequestOptions) => {
      const token = getToken();
      if (token) {
        return {
          ...config,
          headers: {
            ...config.headers,
            Authorization: `Bearer ${token}`,
          },
        };
      }
      return config;
    },
  ],

  // 响应拦截器：本项目后端始终以 HTTP 200 返回 { code, message, data }，
  // 业务失败通过 code !== 0 表达，因此在这里统一识别并抛出，
  // 使各页面的 try/catch 能拿到真实的后端错误信息。
  responseInterceptors: [
    async (response) => {
      // 非 JSON 响应（如 blob 文件下载）不做业务码校验
      const data = response.data as any;
      if (!data || typeof data.code !== 'number') {
        return response;
      }
      if (data.code === 401) {
        const url: string = (response.config as any)?.url || '';
        const isAuthEndpoint = [
          '/api/v1/auth/refresh',
          '/api/v1/auth/login',
          '/api/v1/auth/logout',
        ].some((p) => url.includes(p));
        // 未登录页/登录/登出/刷新接口自身返回 401 时不再尝试刷新，直接跳转登录页
        if (!isAuthEndpoint) {
          const newToken = await getRefreshedAccessToken();
          if (newToken) {
            try {
              return await axios({
                ...response.config,
                headers: {
                  ...response.config?.headers,
                  Authorization: `Bearer ${newToken}`,
                },
              });
            } catch {
              // 重放请求失败，走下方统一的过期跳转
            }
          }
        }
        redirectToLoginExpired();
        return response;
      }
      if (data.code !== 0) {
        throw buildBizError(data.code, data.message, data.data);
      }
      return response;
    },
  ],
};
