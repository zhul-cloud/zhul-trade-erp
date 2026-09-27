import { getToken } from '@/utils/tokenStore';

/**
 * 当前账号是否是平台账号（登录令牌里的 tenantId 为 0）。
 * 仅用于决定界面上显示哪些内容（写按钮、档案完整度），真正的权限判断在服务端：
 * 服务端对所有写接口都会再校验一次，前端判断错了也不会越权。
 */
export function isPlatformAccount(): boolean {
  const token = getToken();
  if (!token) return false;
  try {
    const payload = token.split('.')[1];
    const json = decodeURIComponent(
      atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
        .split('')
        .map((c) => `%${c.charCodeAt(0).toString(16).padStart(2, '0')}`)
        .join(''),
    );
    return JSON.parse(json).tenantId === 0;
  } catch {
    return false;
  }
}
