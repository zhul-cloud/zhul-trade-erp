import { useAccess } from '@umijs/max';

/**
 * 判断是否有某个按钮权限
 * 在函数组件中使用：const { hasPerm } = usePermission()
 */
export function usePermission() {
  const access = useAccess();
  const hasPerm = (permCode: string): boolean => {
    return !!(access as Record<string, unknown>)[permCode];
  };
  return { hasPerm, access };
}
