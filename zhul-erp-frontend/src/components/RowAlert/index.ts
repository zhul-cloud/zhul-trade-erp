/**
 * 行内提醒（公共组件，ui-redesign C8、D7、S5）：需要关注的行在最左侧画 3px 色条（样式在 AppTheme），
 * 原因用状态标签写在对应单元格里，不再整行铺底色。用法：`onRow={(r) => rowAlert(r.overdue ? 'error' : undefined)}`。
 */
export type RowAlertTone = 'error' | 'warning';

export const rowAlert = (tone?: RowAlertTone | false | null) =>
  tone ? { className: `zhul-row-alert-${tone}` } : {};
