-- 报价单无货原因（spec quotation/quotation「报价单内容与定价」）：决定发给客户的文字与导出里写什么
ALTER TABLE `quotation_item`
    ADD COLUMN `no_stock_reason` tinyint(2) NOT NULL DEFAULT 0 COMMENT '无货原因（0-非无货行、1-暂时缺货、2-已停产、3-停产有替代型号、4-其他）' AFTER `replacement_model`,
    ADD COLUMN `no_stock_note` varchar(100) NOT NULL DEFAULT '' COMMENT '无货原因为「其他」时给客户看的英文说明' AFTER `no_stock_reason`;

-- 存量无货行补原因：有替代型号的为「停产有替代型号」，其余为「暂时缺货」，客户看到的内容与之前一致
UPDATE `quotation_item` SET `no_stock_reason` = IF(`replacement_model` <> '', 3, 1) WHERE `no_stock` = 1;

-- 回退：
-- ALTER TABLE `quotation_item` DROP COLUMN `no_stock_reason`, DROP COLUMN `no_stock_note`;
