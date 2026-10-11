-- 商品候选导入（openspec add-candidate-import）：导入来源记录来源文件名

ALTER TABLE `product_candidate_source`
    ADD COLUMN `file_name` varchar(255) NOT NULL DEFAULT '' COMMENT '导入来源的文件名（来源类型 6-导入）' AFTER `so_id`;
