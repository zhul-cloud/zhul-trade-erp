-- 售后补发订单（OpenSpec 变更 after-sales-replacement）
-- 销售订单新增来源 3-售后补发，记录售后单与原订单；型号行加申报单价（只用于 CI/PL）；收款状态新增 5-无需收款。
-- 回退 SQL 在文件末尾的注释中（回退前先把 source = 3 的订单取消或处理掉）。

ALTER TABLE `sales_order`
    MODIFY COLUMN `source` tinyint(2) NOT NULL DEFAULT 1 COMMENT '来源（1-PI 转成、2-手动创建、3-售后补发）',
    MODIFY COLUMN `receipt_status` tinyint(2) NOT NULL DEFAULT 1 COMMENT '收款状态（手动订单与补发订单用；1-未付款、2-待到账、3-部分到账、4-已到账、5-无需收款）',
    ADD COLUMN `after_sales_id` bigint NOT NULL DEFAULT 0 COMMENT '售后补发：来源售后单ID，关联after_sales.id；其他订单为0' AFTER `pi_version_no`,
    ADD COLUMN `original_so_id` bigint NOT NULL DEFAULT 0 COMMENT '售后补发：原订单ID（成本计入该订单），关联sales_order.id；其他订单为0' AFTER `after_sales_id`,
    ADD INDEX `idx_so_after_sales` (`after_sales_id`),
    ADD INDEX `idx_so_original` (`original_so_id`);

ALTER TABLE `sales_order_item`
    ADD COLUMN `declared_price` decimal(18,2) NULL COMMENT '申报单价（原币，仅售后补发订单有值，CI/PL 按此出金额）' AFTER `unit_price`;

-- 回退：
-- ALTER TABLE `sales_order_item` DROP COLUMN `declared_price`;
-- ALTER TABLE `sales_order` DROP INDEX `idx_so_original`, DROP INDEX `idx_so_after_sales`, DROP COLUMN `original_so_id`, DROP COLUMN `after_sales_id`;
