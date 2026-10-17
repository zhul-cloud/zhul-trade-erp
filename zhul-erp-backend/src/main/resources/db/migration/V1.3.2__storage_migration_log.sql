-- 历史文件迁移记录（unified-oss-storage）：迁移把本地文件上传到 OSS 后，业务记录改成新文件键，
-- 这里留下「旧文件键 → 新文件键」与新文件大小，清理步骤据此逐个核对后才删除本地文件。

CREATE TABLE `storage_migration_log` (
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `source_table` varchar(64) NOT NULL DEFAULT '' COMMENT '来源表，如 biz_attachment、payment_receipt',
    `record_id`   bigint(20)   NOT NULL DEFAULT 0 COMMENT '来源记录ID',
    `item_index`  int(11)      NOT NULL DEFAULT 0 COMMENT '同一条记录内的序号（收款水单数组下标，其余为 0）',
    `public_read` tinyint(2)   NOT NULL DEFAULT 0 COMMENT '是否公开文件（0-私有、1-公开）',
    `old_key`     varchar(255) NOT NULL DEFAULT '' COMMENT '迁移前的本地文件键（相对私有或公开目录）',
    `new_key`     varchar(255) NOT NULL DEFAULT '' COMMENT '迁移后的 OSS 文件键',
    `new_size`    bigint(20)   NOT NULL DEFAULT 0 COMMENT '迁移后的文件大小（字节，图片为压缩后）',
    `status`      tinyint(2)   NOT NULL DEFAULT 1 COMMENT '状态（1-已迁移、2-本地已清理）',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_record` (`source_table`, `record_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史文件迁移记录';
