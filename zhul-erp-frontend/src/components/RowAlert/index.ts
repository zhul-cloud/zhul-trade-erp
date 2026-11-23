/**
 * 行内提醒（公共组件，ui-redesign C8、D7、S5）：需要关注的行在最左侧画 3px 色条（样式在 AppTheme），
 * 原因用状态标签写在对应单元格里，不再整行铺底色。用法：`onRow={(r) => rowAlert(r.overdue ? 'error' : undefined)}`。
 */
import type { HTMLAttributes } from 'react';

export type RowAlertTone = 'error' | 'warning';

export const rowAlert = (
  tone?: RowAlertTone | false | null,
  /** 行原有的 onRow 属性（点击等），一起返回 */
  props?: HTMLAttributes<HTMLElement>,
): HTMLAttributes<HTMLElement> => {
  if (!tone) return props ?? {};
  const cls = `zhul-row-alert-${tone}`;
  return {
    ...props,
    className: props?.className ? `${props.className} ${cls}` : cls,
  };
};
