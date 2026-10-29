-- 飞书历史数据迁移（openspec migrate-feishu-history）：迁移批次、新旧编号对照、订单历史核算快照，以及各业务表的「历史迁移」标记。
-- 迁移生成的单据 migrated = 1，只读（不能修改、作废、取消、删除）；商机重新打开后清除标记。可重复执行。

CREATE TABLE IF NOT EXISTS `migration_batch` (
  `id`           bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`    int           NOT NULL DEFAULT 0  COMMENT '目标租户ID',
  `batch_no`     varchar(32)   NOT NULL DEFAULT '' COMMENT '批次号（HM + 年月日时分秒）',
  `bundle_hash`  varchar(64)   NOT NULL DEFAULT '' COMMENT '清洗结果的版本哈希',
  `run_mode`     tinyint(2)    NOT NULL DEFAULT 1  COMMENT '运行方式（1-干跑、2-正式执行）',
  `status`       tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-运行中、2-成功、3-失败、4-已回滚）',
  `stage_stats`  text          NULL                COMMENT '各阶段、各类数据的新建、跳过、失败数量（JSON）',
  `error_text`   varchar(1000) NOT NULL DEFAULT '' COMMENT '失败原因',
  `started_at`   datetime      NULL                COMMENT '开始时间',
  `finished_at`  datetime      NULL                COMMENT '结束时间',
  `operator_id`  bigint        NOT NULL DEFAULT 0  COMMENT '执行人用户ID',
  `create_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `create_by`    varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
  `update_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_by`    varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史数据迁移批次';

CREATE TABLE IF NOT EXISTS `migration_id_map` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`     int          NOT NULL DEFAULT 0  COMMENT '目标租户ID',
  `batch_id`      bigint       NOT NULL DEFAULT 0  COMMENT '迁移批次ID，关联migration_batch.id',
  `source_table`  varchar(32)  NOT NULL DEFAULT '' COMMENT '源表（如 salesOrders、inquiries）',
  `source_code`   varchar(64)  NOT NULL DEFAULT '' COMMENT '旧编号（如 SO2026102300698；型号行为 旧编号#行号）',
  `source_hash`   varchar(32)  NOT NULL DEFAULT '' COMMENT '源记录内容哈希，重复执行时判断源数据是否变化',
  `target_table`  varchar(64)  NOT NULL DEFAULT '' COMMENT '目标表',
  `target_id`     bigint       NOT NULL DEFAULT 0  COMMENT '目标记录ID',
  `status`        tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（1-有效、2-已回滚）',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `create_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_target` (`tenant_id`, `source_table`, `source_code`, `target_table`, `target_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_batch_id` (`batch_id`),
  KEY `idx_target` (`target_table`, `target_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史迁移新旧编号对照';

CREATE TABLE IF NOT EXISTS `sales_order_cost_snapshot` (
  `id`                bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id`         int           NOT NULL DEFAULT 0  COMMENT '租户ID',
  `so_id`             bigint        NOT NULL DEFAULT 0  COMMENT '销售订单ID，关联sales_order.id',
  `currency_code`     char(3)       NOT NULL DEFAULT 'CNY' COMMENT '快照金额币种（旧系统成本核算均为人民币）',
  `revenue`           decimal(18,2) NOT NULL DEFAULT 0  COMMENT '销售收入（毛额，含已结汇与估算部分）',
  `refund`            decimal(18,2) NOT NULL DEFAULT 0  COMMENT '退款金额',
  `purchase_amount`   decimal(18,2) NOT NULL DEFAULT 0  COMMENT '采购货款',
  `freight`           decimal(18,2) NOT NULL DEFAULT 0  COMMENT '运费',
  `surcharge`         decimal(18,2) NOT NULL DEFAULT 0  COMMENT '附加费',
  `service_fee`       decimal(18,2) NOT NULL DEFAULT 0  COMMENT '交易服务费',
  `cost`              decimal(18,2) NOT NULL DEFAULT 0  COMMENT '实际成本合计（旧系统「销售成本」）',
  `gross_profit`      decimal(18,2) NOT NULL DEFAULT 0  COMMENT '毛利',
  `margin_rate`       decimal(7,2)  NULL                COMMENT '毛利率（%）= 毛利 ÷（收入 − 退款），收入为 0 时为空',
  `settled`           tinyint(2)    NOT NULL DEFAULT 0  COMMENT '结算状态（0-未结算、1-已结算）',
  `accounting_month`  char(7)       NOT NULL DEFAULT '' COMMENT '核算月份（YYYY-MM）',
  `source_codes`      varchar(200)  NOT NULL DEFAULT '' COMMENT '来源核算单旧编号（多张合并时逗号分隔）',
  `create_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `create_by`         varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
  `update_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_by`         varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
  `deleted_at`        datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_so_id` (`so_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史迁移订单的成本核算快照（迁移订单的收入、成本、毛利取此表，不按入库与对账推算）';

-- 各业务表的「历史迁移」标记（fin_fx_settlement 已有 migrated 字段，沿用）

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'opportunity' AND COLUMN_NAME = 'migrated');
SET @s := IF(@c = 0, 'ALTER TABLE `opportunity` ADD COLUMN `migrated` tinyint(2) NOT NULL DEFAULT 0 COMMENT ''历史迁移（0-否、1-是）；迁移数据只读''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'customer_inquiry' AND COLUMN_NAME = 'migrated');
SET @s := IF(@c = 0, 'ALTER TABLE `customer_inquiry` ADD COLUMN `migrated` tinyint(2) NOT NULL DEFAULT 0 COMMENT ''历史迁移（0-否、1-是）；迁移数据只读''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sourcing_task' AND COLUMN_NAME = 'migrated');
SET @s := IF(@c = 0, 'ALTER TABLE `sourcing_task` ADD COLUMN `migrated` tinyint(2) NOT NULL DEFAULT 0 COMMENT ''历史迁移（0-否、1-是）；迁移数据只读''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sourcing_quote' AND COLUMN_NAME = 'migrated');
SET @s := IF(@c = 0, 'ALTER TABLE `sourcing_quote` ADD COLUMN `migrated` tinyint(2) NOT NULL DEFAULT 0 COMMENT ''历史迁移（0-否、1-是）；迁移数据只读''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_order' AND COLUMN_NAME = 'migrated');
SET @s := IF(@c = 0, 'ALTER TABLE `sales_order` ADD COLUMN `migrated` tinyint(2) NOT NULL DEFAULT 0 COMMENT ''历史迁移（0-否、1-是）；迁移数据只读''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order' AND COLUMN_NAME = 'migrated');
SET @s := IF(@c = 0, 'ALTER TABLE `purchase_order` ADD COLUMN `migrated` tinyint(2) NOT NULL DEFAULT 0 COMMENT ''历史迁移（0-否、1-是）；迁移数据只读''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'supplier_shipment' AND COLUMN_NAME = 'migrated');
SET @s := IF(@c = 0, 'ALTER TABLE `supplier_shipment` ADD COLUMN `migrated` tinyint(2) NOT NULL DEFAULT 0 COMMENT ''历史迁移（0-否、1-是）；迁移数据只读''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logistics_shipment' AND COLUMN_NAME = 'migrated');
SET @s := IF(@c = 0, 'ALTER TABLE `logistics_shipment` ADD COLUMN `migrated` tinyint(2) NOT NULL DEFAULT 0 COMMENT ''历史迁移（0-否、1-是）；迁移数据只读''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payment_receipt' AND COLUMN_NAME = 'migrated');
SET @s := IF(@c = 0, 'ALTER TABLE `payment_receipt` ADD COLUMN `migrated` tinyint(2) NOT NULL DEFAULT 0 COMMENT ''历史迁移（0-否、1-是）；迁移数据只读''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_customer_refund' AND COLUMN_NAME = 'migrated');
SET @s := IF(@c = 0, 'ALTER TABLE `fin_customer_refund` ADD COLUMN `migrated` tinyint(2) NOT NULL DEFAULT 0 COMMENT ''历史迁移（0-否、1-是）；迁移数据只读''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_expense' AND COLUMN_NAME = 'migrated');
SET @s := IF(@c = 0, 'ALTER TABLE `fin_expense` ADD COLUMN `migrated` tinyint(2) NOT NULL DEFAULT 0 COMMENT ''历史迁移（0-否、1-是）；迁移数据只读''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_other_income' AND COLUMN_NAME = 'migrated');
SET @s := IF(@c = 0, 'ALTER TABLE `fin_other_income` ADD COLUMN `migrated` tinyint(2) NOT NULL DEFAULT 0 COMMENT ''历史迁移（0-否、1-是）；迁移数据只读''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_payment' AND COLUMN_NAME = 'migrated');
SET @s := IF(@c = 0, 'ALTER TABLE `fin_payment` ADD COLUMN `migrated` tinyint(2) NOT NULL DEFAULT 0 COMMENT ''历史迁移（0-否、1-是）；迁移数据只读''', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
