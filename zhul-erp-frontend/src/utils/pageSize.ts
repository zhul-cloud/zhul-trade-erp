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

/**
 * 列表分页（公共组件，规范「组件标准 · 分页」，ui-redesign D11、D12）：给 Table / ProTable 的 pagination 用。
 * 默认每页 20 条，可选 10/20/50/100，切换后按页面记住；页码出现省略号（页数多）时才显示「跳至 __ 页」（全局样式控制）。
 * 每页条数下拉显示「每页 20 条」；传入 total 时，不超过 10 条（只有一页，也用不上切换条数）不显示分页。
 * 总数写在主卡左上角，这里不再重复；没有主卡的子表才自己传 showTotal。
 */
/**
 * 分页的统一外观（规范「组件标准 · 分页」）：「每页 20 条」、页码多时显示跳页、分页里不重复总数。
 * 自己管理页码的列表写 `pagination={{ ...PAGER_STYLE, current, pageSize, total, onChange }}`，放在最前面，页面自己的设置可覆盖。
 */
export const PAGER_STYLE = {
  showSizeChanger: { prefix: '每页' },
  showQuickJumper: true,
  // 总数只写在主卡左上角：ProTable 没传 showTotal 时会自己加「第 1-20 条/总共 N 条」，这里明确不显示
  showTotal: () => null,
  locale: { items_per_page: '条' },
} as const;

export const usePagination = (fallback = 20, scope = '', total?: number) => {
  const [pageSize, setPageSize] = usePageSize(fallback, scope);
  return {
    ...PAGER_STYLE,
    pageSize,
    hideOnSinglePage: total !== undefined && total <= 10,
    onChange: (_: number, size: number) => setPageSize(size),
  };
};
