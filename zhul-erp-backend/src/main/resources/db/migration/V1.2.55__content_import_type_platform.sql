-- 内容上传记录（openspec content-upload-records）：记录包类型与营销平台，历史记录按语言回填

ALTER TABLE `product_content_import`
    ADD COLUMN `package_type` varchar(16) NOT NULL DEFAULT '' COMMENT '包类型（facts-资料包、marketing-营销包）' AFTER `product_id`,
    ADD COLUMN `platform` varchar(16) NOT NULL DEFAULT '' COMMENT '营销平台（alibaba、mic、site、social；资料包为空）' AFTER `package_type`;

UPDATE `product_content_import` SET `package_type` = 'facts' WHERE `package_type` = '' AND `lang` = 'zh';
UPDATE `product_content_import` SET `package_type` = 'marketing', `platform` = 'site'
WHERE `package_type` = '' AND `lang` IN ('en', 'ru');
