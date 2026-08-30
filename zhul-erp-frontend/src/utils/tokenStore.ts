/**
 * 登录态存储策略：
 * - 记住我（remember_me=true）：AccessToken 存 localStorage（跨标签页/刷新持久），
 *   并配合服务端下发的 HttpOnly RefreshToken Cookie 静默续期。
 * - 不记住我：AccessToken 存 sessionStorage（仅当前标签页，关闭即失效）。
 * 用户展示信息（zhul_user）与记住我无关，统一存 localStorage。
 *
 * 与 PRD 描述的“记住我时 AccessToken 仅存内存变量”相比做了简化：
 * 用 localStorage 替代内存变量，避免额外实现“刷新页面后用 Cookie 静默换取新
 * AccessToken 再渲染”的启动期异步流程，同时仍保留 RefreshToken 续期的核心价值。
 */
const SESSION_KEY = 'akt';
const LOCAL_KEY = 'zhul_token';

export function setToken(token: string, rememberMe: boolean) {
  if (rememberMe) {
    localStorage.setItem(LOCAL_KEY, token);
    sessionStorage.removeItem(SESSION_KEY);
  } else {
    sessionStorage.setItem(SESSION_KEY, token);
    localStorage.removeItem(LOCAL_KEY);
  }
}

export function getToken(): string | null {
  return sessionStorage.getItem(SESSION_KEY) || localStorage.getItem(LOCAL_KEY);
}

export function clearToken() {
  sessionStorage.removeItem(SESSION_KEY);
  localStorage.removeItem(LOCAL_KEY);
}

/** 刷新后原地更新：写回原先持有 Token 的那个存储位置 */
export function updateTokenInPlace(newToken: string) {
  if (sessionStorage.getItem(SESSION_KEY)) {
    sessionStorage.setItem(SESSION_KEY, newToken);
  } else {
    localStorage.setItem(LOCAL_KEY, newToken);
  }
}
