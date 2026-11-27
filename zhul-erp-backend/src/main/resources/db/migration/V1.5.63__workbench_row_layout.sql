-- 工作台按行排列（openspec ui-redesign D18、D19、D28）：调整老板、业务员两个内置模板的组件顺序。
-- 老板：第一行主角卡（近 12 个月成交额）与经营概览，第二行资金与风险提醒，第三行销售漏斗与排行，最后整行业务员对比（v1.49.0 新增，openspec boss-workbench-sales-compare）；
-- 业务员：要处理的事排第一行（我的待办、过期未收款的 PI），我的业绩整行，询价进度与我的商机一行。
-- 每个组件占几栏、属于哪个高度档由前端注册表决定，size 仍表示整行或半行。
-- 回退：把 widgets 改回 V1.3.5 的内容即可，不涉及其他数据。

UPDATE `workbench_template`
SET `widgets` = '[{"key":"revenue-trend","size":"half"},{"key":"biz-overview","size":"half"},{"key":"cash","size":"half"},{"key":"risk-alerts","size":"half"},{"key":"sales-funnel","size":"half"},{"key":"rankings","size":"half"},{"key":"sales-compare","size":"full"}]',
    `description` = '本月成交额、经营概览、资金、风险提醒',
    `update_time` = NOW()
WHERE `code` = 'boss';

UPDATE `workbench_template`
SET `widgets` = '[{"key":"my-todo","size":"half"},{"key":"overdue-pi","size":"half"},{"key":"my-performance","size":"full"},{"key":"quote-progress","size":"half"},{"key":"my-opportunities","size":"half"}]',
    `description` = '我的待办、过期未收款的 PI、我的业绩',
    `update_time` = NOW()
WHERE `code` = 'sales';
