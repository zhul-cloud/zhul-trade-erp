import { request } from '@umijs/max';
import { clearToken } from '@/utils/tokenStore';

export interface LoginParams {
  username: string;
  password: string;
  rememberMe?: boolean;
}

export interface LoginResult {
  accessToken: string;
  expiresIn: number;
  username: string;
  nickname: string;
  avatarUrl: string;
  isAdmin: boolean;
  tenantName: string;
}

export interface ApiResponse<T> {
  code: number;
  message: string;
  data?: T;
}

function genIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/** 登录 */
export async function login(
  params: LoginParams,
): Promise<ApiResponse<LoginResult>> {
  return request('/api/v1/auth/login', {
    method: 'POST',
    data: { ...params, idempotencyKey: genIdempotencyKey() },
    skipErrorHandler: true,
  });
}

/** 退出登录 */
export async function logout(): Promise<void> {
  try {
    await request('/api/v1/auth/logout', { method: 'POST' });
  } finally {
    clearToken();
    localStorage.removeItem('zhul_user');
  }
}

/** 刷新 AccessToken（依赖 HttpOnly RefreshToken Cookie） */
export async function refreshToken(): Promise<
  ApiResponse<{ accessToken: string; expiresIn: number }>
> {
  return request('/api/v1/auth/refresh', {
    method: 'POST',
    skipErrorHandler: true,
  });
}

/** 找回密码：发送验证码 */
export async function sendResetCode(params: {
  username: string;
  email: string;
}): Promise<ApiResponse<void>> {
  return request('/api/v1/auth/password/send-code', {
    method: 'POST',
    data: params,
    skipErrorHandler: true,
  });
}

/** 找回密码：校验验证码 */
export async function verifyResetCode(params: {
  username: string;
  code: string;
}): Promise<ApiResponse<{ verifyToken: string }>> {
  return request('/api/v1/auth/password/verify-code', {
    method: 'POST',
    data: params,
    skipErrorHandler: true,
  });
}

/** 找回密码：设置新密码 */
export async function resetPassword(params: {
  verifyToken: string;
  newPassword: string;
  confirmPassword: string;
}): Promise<ApiResponse<void>> {
  return request('/api/v1/auth/password/reset', {
    method: 'POST',
    data: params,
    skipErrorHandler: true,
  });
}
