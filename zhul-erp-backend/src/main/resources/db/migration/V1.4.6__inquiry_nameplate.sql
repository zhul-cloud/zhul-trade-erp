-- 询盘铭牌图片（openspec/changes/inquiry-nameplate-photos）：
-- 询盘附件区分「询盘附件」与「铭牌图片」，铭牌图片可以对应到型号，采购能看到铭牌图片。
-- 存量附件都是询盘附件（kind = 1），不做转换。

ALTER TABLE `customer_inquiry_attachment`
    ADD COLUMN `kind` tinyint(2) NOT NULL DEFAULT 1 COMMENT '类别（1-询盘附件、2-铭牌图片）' AFTER `append_id`,
    ADD COLUMN `inquiry_item_id` bigint NOT NULL DEFAULT 0 COMMENT '铭牌图片对应的型号，关联inquiry_item.id；0-未对应' AFTER `kind`,
    ADD KEY `idx_inquiry_item_id` (`inquiry_item_id`);

-- 回退（铭牌图片退化为普通询盘附件，文件不丢）：
-- ALTER TABLE `customer_inquiry_attachment` DROP INDEX `idx_inquiry_item_id`, DROP COLUMN `inquiry_item_id`, DROP COLUMN `kind`;
