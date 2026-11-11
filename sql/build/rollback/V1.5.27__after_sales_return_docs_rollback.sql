-- 售后退货单据化 V1.5.27 的反向 SQL（备用，只在需要回退该版本时手工执行，须与旧版本代码一起回退；执行后还需从 flyway_schema_history 删除 1.5.27 的记录）。
-- 迁移后新建的销售退货单、退货入库单、采购退货单回退后不再可见，回退前先导出核对：
--   SELECT `return_no`, `status` FROM `purchase_return` WHERE `migrated` = 0 AND `deleted_at` IS NULL;
UPDATE `sales_return` SET `status` = CASE WHEN `status` = 2 THEN 1 WHEN `status` = 3 THEN
    IFNULL((SELECT CASE x.`status` WHEN 2 THEN 2 WHEN 3 THEN 3 ELSE 2 END FROM `return_receipt` x WHERE x.`sales_return_id` = `sales_return`.`id` LIMIT 1), 2)
    WHEN `status` = 1 THEN 4 ELSE `status` END;
UPDATE `sales_return_item` i JOIN `return_receipt_item` x ON x.`return_item_id` = i.`id`
    SET i.`received_qty` = x.`received_qty`, i.`qualified_qty` = x.`qualified_qty`;
UPDATE `stock_hold` h JOIN `return_receipt` x ON x.`id` = h.`return_id` SET h.`return_id` = x.`sales_return_id` WHERE h.`source_type` = 3;
UPDATE `biz_attachment` f JOIN `return_receipt` x ON x.`id` = f.`owner_id` SET f.`owner_type` = 'SALES_RETURN', f.`owner_id` = x.`sales_return_id`
    WHERE f.`owner_type` = 'RETURN_RECEIPT';
UPDATE `fin_reimbursement_line` l JOIN `receiving_discrepancy` g ON g.`purchase_return_id` = l.`source_id` SET l.`source_id` = g.`id` WHERE l.`source_type` = 3;
UPDATE `biz_attachment` f JOIN `receiving_discrepancy` g ON g.`purchase_return_id` = f.`owner_id` SET f.`owner_id` = g.`id` WHERE f.`owner_type` = 'RETURN_PROOF';
-- （迁移后新建的采购退货单的运费、退款需先人工核对再回退）
DELETE FROM `role_resource` WHERE `resource_id` IN (110231, 110232, 110233);
UPDATE `tenant_package` SET `menu_ids` = (
    SELECT JSON_ARRAYAGG(x.`id`) FROM JSON_TABLE(`tenant_package`.`menu_ids`, '$[*]' COLUMNS (`id` INT PATH '$')) x
    WHERE x.`id` NOT IN (110231, 110232, 110233))
WHERE JSON_CONTAINS(`menu_ids`, '110231') OR JSON_CONTAINS(`menu_ids`, '110232') OR JSON_CONTAINS(`menu_ids`, '110233');
DELETE FROM `resource` WHERE `id` IN (110231, 110232, 110233);
UPDATE `resource` SET `sort` = 7 WHERE `id` = 100120; UPDATE `resource` SET `sort` = 8 WHERE `id` = 100062;
ALTER TABLE `fin_refund_request` DROP INDEX `idx_sales_return_id`, DROP COLUMN `sales_return_id`, DROP COLUMN `refund_first`;
ALTER TABLE `receiving_discrepancy` DROP INDEX `idx_purchase_return_id`, DROP COLUMN `purchase_return_id`;
ALTER TABLE `sales_return_disposition` DROP INDEX `idx_receipt_id`, DROP INDEX `idx_purchase_return_id`,
    DROP COLUMN `receipt_id`, DROP COLUMN `receipt_item_id`, DROP COLUMN `purchase_return_id`;
ALTER TABLE `sales_return` DROP COLUMN `handling`, DROP COLUMN `refund_first`, DROP COLUMN `confirmed_at`, DROP COLUMN `confirmed_by`;
DROP TABLE `fin_refund_request_item`; DROP TABLE `purchase_return_item`; DROP TABLE `purchase_return`;
DROP TABLE `return_receipt_item`; DROP TABLE `return_receipt`; DROP TABLE `document_log`;
DELETE FROM `document_sequence` WHERE `doc_type` IN ('RR', 'PR');
