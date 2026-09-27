-- V1.2.11：供应商微信、收款账户（对公 / 对私）、附件。
-- 见 openspec/changes/enrich-supplier-settlement-attachments/。
ALTER TABLE `supplier`
    ADD COLUMN `wechat` varchar(64) NOT NULL DEFAULT '' COMMENT '微信（微信号或绑定手机号）' AFTER `contact_email`,
    MODIFY COLUMN `bank_name`    varchar(100) NOT NULL DEFAULT '' COMMENT '开户银行（已停用：由supplier_bank_account取代，V1.2.11起不再写入）',
    MODIFY COLUMN `bank_account` varchar(30)  NOT NULL DEFAULT '' COMMENT '银行账号（已停用：由supplier_bank_account取代，V1.2.11起不再写入）';

CREATE TABLE `supplier_bank_account`
(
    `id`           bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `supplier_id`  bigint(20)   NOT NULL DEFAULT 0  COMMENT '供应商ID，关联supplier.id',
    `account_type` tinyint(2)   NOT NULL DEFAULT 1  COMMENT '账户类型（1-对公、2-对私）',
    `account_name` varchar(100) NOT NULL DEFAULT '' COMMENT '户名；对私为收款人姓名',
    `bank_name`    varchar(100) NOT NULL DEFAULT '' COMMENT '开户银行（含支行）',
    `account_no`   varchar(30)  NOT NULL DEFAULT '' COMMENT '账号（仅数字；列表、详情、导出脱敏展示）',
    `payee_phone`  varchar(20)  NOT NULL DEFAULT '' COMMENT '收款人手机号（对私）',
    `payee_id_no`  varchar(128) NOT NULL DEFAULT '' COMMENT '收款人身份证号（对私），AES密文，任何接口只返回脱敏值',
    `is_default`   tinyint(1)   NOT NULL DEFAULT 0  COMMENT '是否默认收款账户（0-否、1-是），每个供应商恰好一个',
    `sort_order`   int(11)      NOT NULL DEFAULT 0  COMMENT '排序（录入顺序）',
    `deleted_at`   datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`    varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`    varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_supplier` (`tenant_id`, `supplier_id`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '供应商收款账户表';

CREATE TABLE `supplier_attachment`
(
    `id`           bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `supplier_id`  bigint(20)   NOT NULL DEFAULT 0  COMMENT '供应商ID，关联supplier.id',
    `category`     tinyint(2)   NOT NULL DEFAULT 5  COMMENT '附件类型（1-营业执照、2-开户许可证、3-资质证书、4-合同、5-其他）',
    `file_name`    varchar(200) NOT NULL DEFAULT '' COMMENT '原文件名',
    `file_key`     varchar(200) NOT NULL DEFAULT '' COMMENT '私有存储目录下的相对路径（不对外公开，经下载接口访问）',
    `file_size`    bigint(20)   NOT NULL DEFAULT 0  COMMENT '文件大小（字节）',
    `content_type` varchar(64)  NOT NULL DEFAULT '' COMMENT '文件类型（application/pdf、image/jpeg、image/png）',
    `deleted_at`   datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（上传时间）',
    `create_by`    varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人（上传人）',
    `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`    varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_supplier` (`tenant_id`, `supplier_id`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_category` (`category`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '供应商附件表';

-- 存量银行账号迁移为一个对公默认账户（户名取供应商名称）；已有账户的供应商跳过
INSERT INTO `supplier_bank_account` (`tenant_id`, `supplier_id`, `account_type`, `account_name`, `bank_name`, `account_no`, `is_default`, `sort_order`)
SELECT s.`tenant_id`, s.`id`, 1, s.`name`, s.`bank_name`, s.`bank_account`, 1, 0
FROM `supplier` s
WHERE s.`bank_account` <> '' AND s.`deleted_at` IS NULL
  AND NOT EXISTS (SELECT 1 FROM `supplier_bank_account` a WHERE a.`supplier_id` = s.`id`);
