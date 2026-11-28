-- 工作台按行排列（UI-108，原型 I1）：调整业务负责人内置模板的组件顺序，与原型一致。
-- 第一行团队业绩，第二行要跟进与报价转化（同为 M 档才能并排），第三行整行业务员对比，最后整行商机统计（高度随内容）。
-- 原顺序里商机统计夹在要跟进与业务员对比之间，按「高度档不同就换行」会让要跟进、报价转化各自补满整行。
-- 每个组件占几栏、属于哪个高度档由前端注册表 layout.ts 决定，size 仍表示整行或半行。
-- 回退：把 widgets 改回 V1.3.5 的内容即可，不涉及其他数据。

UPDATE `workbench_template`
SET `widgets` = '[{"key":"team-performance","size":"full"},{"key":"follow-ups","size":"half"},{"key":"quote-conversion","size":"half"},{"key":"sales-compare","size":"full"},{"key":"opportunity-stats","size":"full"}]',
    `update_time` = NOW()
WHERE `code` = 'sales_lead';
