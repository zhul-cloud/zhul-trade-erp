import { useLocation } from '@umijs/max';
import { useCallback, useState } from 'react';

const KEY_PREFIX = 'zhul:pageSize:';

const read = (key: string, fallback: number) => {
  try {
    const n = Number(window.localStorage.getItem(key));
    return Number.isInteger(n) && n > 0 && n <= 500 ? n : fallback;
  } catch {
    return fallback;
  }
};

/**
 * 分页列表的每页条数：按页面路径记在浏览器本地，进详情再返回、刷新后保持（列表页统一规范）。
 * 读写失败（隐私模式等）时退回默认值，不影响使用。
 */
export const usePageSize = (
  fallback: number,
  /** 同一页面有多个分页表格时区分 */
  scope = '',
): [number, (size: number) => void] => {
  const { pathname } = useLocation();
  const key = KEY_PREFIX + pathname + (scope ? `#${scope}` : '');
  const [size, setSize] = useState(() => read(key, fallback));
  const update = useCallback(
    (n: number) => {
      setSize(n);
      try {
        window.localStorage.setItem(key, String(n));
      } catch {
        // 存不下就只在本次生效
      }
    },
    [key],
  );
  return [size, update];
};

/** 给 Table / ProTable 的 pagination 用：受控的每页条数，切换后记住 */
export const usePagination = (fallback: number, scope = '') => {
  const [pageSize, setPageSize] = usePageSize(fallback, scope);
  return {
    pageSize,
    showSizeChanger: true,
    onChange: (_: number, size: number) => setPageSize(size),
  };
};
