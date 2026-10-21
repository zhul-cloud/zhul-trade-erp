-- 统一文件存储（unified-oss-storage）：各附件表记录存储方式；视频记录转码处理状态；
-- 商品图片与视频另存文件键，便于迁移、删除时定位存储中的文件（外部链接为空）。

ALTER TABLE `biz_attachment`
    ADD COLUMN `processing_status` tinyint(2) NOT NULL DEFAULT 0 COMMENT '处理状态（0-无需处理、1-转码中、2-已完成、3-失败）' AFTER `content_type`,
    ADD KEY `idx_processing_status` (`processing_status`);

ALTER TABLE `product_media`
    ADD COLUMN `storage` varchar(8) NOT NULL DEFAULT 'LOCAL' COMMENT '存储方式（LOCAL-服务器目录、OSS-对象存储）；外部链接不涉及' AFTER `storage_type`,
    ADD COLUMN `file_key` varchar(255) NOT NULL DEFAULT '' COMMENT '存储中的文件键（平台上传时有值，外部链接为空）' AFTER `storage`,
    ADD COLUMN `processing_status` tinyint(2) NOT NULL DEFAULT 0 COMMENT '处理状态（0-无需处理、1-转码中、2-已完成、3-失败）' AFTER `file_size`,
    ADD KEY `idx_processing_status` (`processing_status`);

ALTER TABLE `customer_inquiry_attachment`
    ADD COLUMN `storage` varchar(8) NOT NULL DEFAULT 'LOCAL' COMMENT '存储方式（LOCAL-服务器目录、OSS-对象存储）' AFTER `file_key`;

ALTER TABLE `opportunity_attachment`
    ADD COLUMN `storage` varchar(8) NOT NULL DEFAULT 'LOCAL' COMMENT '存储方式（LOCAL-服务器目录、OSS-对象存储）' AFTER `file_key`;

ALTER TABLE `supplier_attachment`
    ADD COLUMN `storage` varchar(8) NOT NULL DEFAULT 'LOCAL' COMMENT '存储方式（LOCAL-服务器目录、OSS-对象存储）' AFTER `file_key`;

ALTER TABLE `purchase_order_attachment`
    ADD COLUMN `storage` varchar(8) NOT NULL DEFAULT 'LOCAL' COMMENT '存储方式（LOCAL-服务器目录、OSS-对象存储）' AFTER `file_key`;

ALTER TABLE `document_template_version`
    ADD COLUMN `storage` varchar(8) NOT NULL DEFAULT 'LOCAL' COMMENT '存储方式（LOCAL-服务器目录、OSS-对象存储）' AFTER `file_key`;

ALTER TABLE `sourcing_import`
    ADD COLUMN `storage` varchar(8) NOT NULL DEFAULT 'LOCAL' COMMENT '存储方式（LOCAL-服务器目录、OSS-对象存储）' AFTER `file_key`;

-- 平台上传的商品图片与视频：从站内地址推出文件键（/uploads/ 之后的部分）
UPDATE `product_media` SET `file_key` = SUBSTRING(`file_url`, 10)
WHERE `storage_type` = 1 AND `file_url` LIKE '/uploads/%';
