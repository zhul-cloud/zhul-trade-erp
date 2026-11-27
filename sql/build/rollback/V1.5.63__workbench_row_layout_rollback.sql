-- 工作台模板 V1.5.63 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.63 的记录；前端同时回退到按行布局之前的版本）。
-- 回到执行 V1.5.63 之前的状态：老板模板 = V1.3.5 的组件顺序 + V1.5.61.8 追加的业务员对比（sales-compare，整行）；业务员模板 = V1.3.5 的内容。说明文字同样回到 V1.3.5。
UPDATE `workbench_template`
SET `widgets` = '[{"key":"biz-overview","size":"full"},{"key":"cash","size":"half"},{"key":"sales-funnel","size":"half"},{"key":"revenue-trend","size":"half"},{"key":"risk-alerts","size":"half"},{"key":"rankings","size":"full"},{"key":"sales-compare","size":"full"}]',
    `description` = '经营概览、资金、销售漏斗、风险提醒',
    `update_time` = NOW()
WHERE `code` = 'boss';

UPDATE `workbench_template`
SET `widgets` = '[{"key":"my-todo","size":"half"},{"key":"my-performance","size":"half"},{"key":"quote-progress","size":"half"},{"key":"overdue-pi","size":"half"},{"key":"my-opportunities","size":"half"}]',
    `description` = '我的待办、我的业绩、询价进度',
    `update_time` = NOW()
WHERE `code` = 'sales';
