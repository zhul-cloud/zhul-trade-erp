-- 商品资料与营销内容拆分（openspec split-product-facts-marketing）：资料阶段平台级共享，营销阶段按公司

CREATE TABLE IF NOT EXISTS `product_fact_task` (
    `id`             bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID（平台级，固定 0）',
    `product_id`     bigint      NOT NULL            COMMENT '商品ID',
    `imported_at`    datetime    NULL                COMMENT '最近一次确认资料包的时间',
    `imported_by`    varchar(32) NOT NULL DEFAULT '' COMMENT '最近一次确认资料包的人',
    `verified_at`    datetime    NULL                COMMENT '资料核对时间；为空或早于导入时间表示资料待核',
    `verified_by`    varchar(32) NOT NULL DEFAULT '' COMMENT '资料核对人',
    `downloaded_at`  datetime    NULL                COMMENT '最近下载资料包时间（任何公司下载都更新）',
    `deleted_at`     datetime    NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fact_task_product` (`product_id`),
    KEY `idx_fact_task_tenant` (`tenant_id`),
    KEY `idx_fact_task_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '商品资料任务（平台级，每个商品一行；没有记录的商品视为资料待补）';

ALTER TABLE `product_content_task`
    MODIFY COLUMN `status` tinyint(2) NOT NULL DEFAULT 1 COMMENT '已停用：任务阶段改为按资料任务与营销写入情况查询时计算',
    MODIFY COLUMN `zh_at` datetime NULL COMMENT '已停用：中文属于商品资料，见 product_fact_task',
    MODIFY COLUMN `zh_by` varchar(32) NOT NULL DEFAULT '' COMMENT '已停用：中文属于商品资料，见 product_fact_task',
    MODIFY COLUMN `en_at` datetime NULL COMMENT '英文营销内容最近写入时间',
    MODIFY COLUMN `en_by` varchar(32) NOT NULL DEFAULT '' COMMENT '英文营销内容最近写入人',
    MODIFY COLUMN `ru_at` datetime NULL COMMENT '俄文营销内容最近写入时间',
    MODIFY COLUMN `ru_by` varchar(32) NOT NULL DEFAULT '' COMMENT '俄文营销内容最近写入人',
    MODIFY COLUMN `downloaded_at` datetime NULL COMMENT '本公司最近下载营销包时间',
    COMMENT = '商品内容任务（按公司，记录营销阶段；没有记录的商品视为营销未写入）';
