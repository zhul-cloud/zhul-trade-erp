import { isAuthPath, normalizePath } from './tokenStore';

describe('normalizePath', () => {
  it('去掉末尾斜杠，根路径保持不变', () => {
    expect(normalizePath('/login/')).toBe('/login');
    expect(normalizePath('/login')).toBe('/login');
    expect(normalizePath('/forget-password/step1//')).toBe(
      '/forget-password/step1',
    );
    expect(normalizePath('/')).toBe('/');
    expect(normalizePath('//')).toBe('/');
  });
});

describe('isAuthPath', () => {
  it('带不带末尾斜杠的登录、找回密码页都算登录前页面', () => {
    for (const p of [
      '/login',
      '/login/',
      '/forget-password/step1',
      '/forget-password/step1/',
      '/user/register/',
    ]) {
      expect(isAuthPath(p)).toBe(true);
    }
  });

  it('业务页面不算', () => {
    for (const p of ['/dashboard', '/dashboard/', '/', '/login-history']) {
      expect(isAuthPath(p)).toBe(false);
    }
  });
});
