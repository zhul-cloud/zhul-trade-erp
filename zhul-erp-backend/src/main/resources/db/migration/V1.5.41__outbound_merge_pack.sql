-- 销售出库合并打包（openspec outbound-merge-pack）
-- 同一客户、同一货代的几张销售出库单合并打包：组成合并打包组，共用箱子，一个箱子可以混装不同订单的货。
-- 合并时箱子的 outbound_id 记组内第一张单（只为兼容），取箱以 pack_id 为准。

CREATE TABLE `outbound_pack` (
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `customer_id`  bigint       NOT NULL DEFAULT 0  COMMENT '客户ID，关联customer.id',
    `forwarder_id` bigint       NOT NULL DEFAULT 0  COMMENT '货代ID，关联supplier.id',
    `status`       tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（1-有效、2-已拆开）',
    `packed_by`    bigint       NOT NULL DEFAULT 0  COMMENT '打包人，user_basic.id',
    `packed_at`    datetime     NULL                COMMENT '打包时间',
    `split_reason` varchar(200) NOT NULL DEFAULT '' COMMENT '拆开原因',
    `split_by`     bigint       NOT NULL DEFAULT 0  COMMENT '拆开人，user_basic.id',
    `split_at`     datetime     NULL                COMMENT '拆开时间',
    `deleted_at`   datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`    varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`    varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='销售出库合并打包组';

ALTER TABLE `outbound_order`
    ADD COLUMN `pack_id` bigint NULL COMMENT '所属合并打包组，关联outbound_pack.id；NULL 为单独打包' AFTER `logistics_id`,
    ADD KEY `idx_outbound_order_pack_id` (`pack_id`);

ALTER TABLE `outbound_box`
    ADD COLUMN `pack_id` bigint NULL COMMENT '所属合并打包组，关联outbound_pack.id；NULL 为单独打包的箱子' AFTER `outbound_id`,
    ADD KEY `idx_outbound_box_pack_id` (`pack_id`);

-- 回退（先确认没有有效的合并打包组：SELECT COUNT(*) FROM outbound_pack WHERE status = 1 AND deleted_at IS NULL 为 0；
-- 否则组内各单会各自显示同一批箱子）：
-- ALTER TABLE `outbound_box` DROP KEY `idx_outbound_box_pack_id`, DROP COLUMN `pack_id`;
-- ALTER TABLE `outbound_order` DROP KEY `idx_outbound_order_pack_id`, DROP COLUMN `pack_id`;
-- DROP TABLE `outbound_pack`;
-- DELETE FROM `flyway_schema_history` WHERE `version` = '1.5.41';
