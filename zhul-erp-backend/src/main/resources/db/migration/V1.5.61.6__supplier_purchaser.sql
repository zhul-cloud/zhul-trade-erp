-- 供应商所属采购员（openspec supplier-purchaser）：新增、编辑供应商时选择，只做登记，不参与采购需求、采购订单与数据权限。
ALTER TABLE `supplier`
    ADD COLUMN `purchaser_id` bigint(20) NULL COMMENT '所属采购员（用户 ID），为空表示未指定' AFTER `customer_id`,
    ADD KEY `idx_purchaser_id` (`purchaser_id`);
