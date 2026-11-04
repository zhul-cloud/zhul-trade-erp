/** 没写宽度的列按这个宽度计入表格总宽 */
const DEFAULT_COLUMN_WIDTH = 120;

type Column = {
  width?: number | string;
  hidden?: boolean;
  hideInTable?: boolean;
  children?: Column[];
};

/**
 * 表格横向滚动宽度：各列宽度之和。
 * 宽度随列定义走，改了某列宽度不用再改写死的总宽，总宽小于列宽之和时各列会被挤窄；
 * 不用 'max-content'：那样表格按内容排版，列宽不生效，长文本不会省略（audit UI-041）
 */
export const tableScrollX = (columns?: readonly Column[]): number =>
  (columns ?? []).reduce((sum, c) => {
    if (c.hidden || c.hideInTable) return sum;
    if (c.children?.length) return sum + tableScrollX(c.children);
    return sum + (typeof c.width === 'number' ? c.width : DEFAULT_COLUMN_WIDTH);
  }, 0);
