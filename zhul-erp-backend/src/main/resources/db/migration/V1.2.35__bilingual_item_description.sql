-- ===========================
-- 型号描述中英文两份：系统内显示中文（description），发给客户的单据用英文（description_en）
-- 见 openspec/changes/add-bilingual-item-description
-- ===========================

ALTER TABLE `inquiry_item`
    ADD COLUMN `description_en` varchar(300) NOT NULL DEFAULT '' COMMENT '英文描述（给客户看：报价单、PI 导出与文字报价）' AFTER `description`;

ALTER TABLE `quotation_item`
    ADD COLUMN `description_en` varchar(300) NOT NULL DEFAULT '' COMMENT '英文描述（文字报价与导出用；为空时退回中文描述）' AFTER `description`;

ALTER TABLE `proforma_invoice_item`
    ADD COLUMN `description_en` varchar(300) NOT NULL DEFAULT '' COMMENT '英文描述（PI 导出与预览用；为空时退回中文描述）' AFTER `description`;

ALTER TABLE `sales_order_item`
    ADD COLUMN `description_en` varchar(300) NOT NULL DEFAULT '' COMMENT '英文描述（留存，取自 PI 行）' AFTER `description`;
