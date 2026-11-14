-- V1.5.34：供应链管理第一期——需求情报与供应覆盖（OpenSpec supply-chain-phase1-demand-coverage）
-- 1. 六张新表：需求信号、需求对象、覆盖渠道、处理记录、专项、专项型号
-- 2. sop_stage 加「质量检查项」列，导入供应链作战地图 25 个节点的作战卡（板块 6，阶段编码加 SC- 前缀，避免与客户跟进的 G4、G5 重名）
-- 3. 一级菜单「供应链管理」（排在采购管理之后）与两个页面、四个按钮权限；系统设置「供应证据有效天数」
-- 4. 加进含「采购订单」菜单的套餐，授给内置租户管理员，其他角色由管理员授权
-- 操作日志沿用 document_log（单据类型 SC_DEMAND、SC_GAP_PROJECT），不另建表。
-- 回退见 sql/build/rollback/V1.5.34__supply_chain_phase1_rollback.sql

CREATE TABLE `sc_demand_signal` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `source_type`      tinyint(2)    NOT NULL DEFAULT 1  COMMENT '来源类型（1-询盘、2-销售订单、3-询价无货、4-报价未成交、5-搜索关键词、6-同行调研）',
    `source_id`        bigint        NULL                COMMENT '来源行ID（询盘型号行、销售订单行、询价记录、报价单行）；人工录入为 NULL',
    `source_doc_id`    bigint        NOT NULL DEFAULT 0  COMMENT '来源单据ID（询盘单、销售订单、询价任务、报价单），0 为无',
    `source_doc_no`    varchar(32)   NOT NULL DEFAULT '' COMMENT '来源单号（显示用快照）',
    `demand_id`        bigint        NOT NULL DEFAULT 0  COMMENT '需求对象ID，关联sc_demand.id，0 为待归类',
    `brand_id`         bigint        NOT NULL DEFAULT 0  COMMENT '识别到的品牌ID，关联product_brand.id，0 为未识别',
    `brand_raw`        varchar(64)   NOT NULL DEFAULT '' COMMENT '品牌原文',
    `category_id`      bigint        NOT NULL DEFAULT 0  COMMENT '二级品类ID，关联product_category.id，0 为未知',
    `model_raw`        varchar(128)  NOT NULL DEFAULT '' COMMENT '型号原文',
    `model_key`        varchar(128)  NOT NULL DEFAULT '' COMMENT '归一化型号（全角转半角、小写、只留字母数字），空为没有型号',
    `quantity`         int(11)       NOT NULL DEFAULT 0  COMMENT '数量，0 为未知',
    `reason_code`      varchar(32)   NOT NULL DEFAULT '' COMMENT '无货原因或未成交原因编码',
    `reason_text`      varchar(200)  NOT NULL DEFAULT '' COMMENT '无货原因或未成交原因说明',
    `content`          varchar(500)  NOT NULL DEFAULT '' COMMENT '原始内容（关键词、调研内容等）',
    `platform`         varchar(32)   NOT NULL DEFAULT '' COMMENT '搜索关键词的平台；同行调研为同行名称',
    `occur_count`      int(11)       NOT NULL DEFAULT 1  COMMENT '出现次数（搜索关键词可大于 1）',
    `period_start`     date          NULL                COMMENT '统计期开始（搜索关键词）',
    `period_end`       date          NULL                COMMENT '统计期结束（搜索关键词）',
    `link_url`         varchar(500)  NOT NULL DEFAULT '' COMMENT '链接（选填）',
    `occurred_at`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '出现时间（来源单据的业务时间）',
    `source_cancelled` tinyint(1)    NOT NULL DEFAULT 0  COMMENT '来源已取消（0-否、1-是），是则不计入次数',
    `operator_id`      bigint        NOT NULL DEFAULT 0  COMMENT '录入人，关联user_basic.id，0 为系统汇入',
    `ignore_reason`    varchar(200)  NOT NULL DEFAULT '' COMMENT '忽略原因',
    `deleted_at`       datetime      NULL                COMMENT '忽略时间（软删除），NULL表示未忽略',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_source` (`tenant_id`, `source_type`, `source_id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_demand_id` (`demand_id`, `occurred_at`),
    KEY `idx_brand_id` (`brand_id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_source_doc_id` (`source_doc_id`),
    KEY `idx_occurred_at` (`occurred_at`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应链需求信号';

CREATE TABLE `sc_demand` (
    `id`                  bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`           int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `brand_id`            bigint        NOT NULL DEFAULT 0  COMMENT '品牌ID，关联product_brand.id',
    `category_id`         bigint        NOT NULL DEFAULT 0  COMMENT '二级品类ID，关联product_category.id，0 为未知',
    `model_key`           varchar(128)  NOT NULL DEFAULT '' COMMENT '归一化型号；空为「品牌 + 品类」级需求',
    `model`               varchar(128)  NOT NULL DEFAULT '' COMMENT '显示用型号（首次出现的写法）',
    `scope_key`           varchar(140)  NOT NULL DEFAULT '' COMMENT '唯一键：有型号为 m:归一化型号，没有型号为 c:二级品类ID',
    `status`              tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-观察中、2-已进入覆盖矩阵、3-暂不覆盖）',
    `pause_reason`        varchar(200)  NOT NULL DEFAULT '' COMMENT '暂不覆盖原因',
    `demand_level`        tinyint(2)    NOT NULL DEFAULT 1  COMMENT '需求等级（1-观察、2-重复、3-已验证、4-商业），系统推出',
    `suggested_priority`  tinyint(2)    NOT NULL DEFAULT 1  COMMENT '系统建议优先级（1-低、2-中、3-高）',
    `priority_rule`       varchar(64)   NOT NULL DEFAULT '' COMMENT '建议优先级命中的规则',
    `priority`            tinyint(2)    NOT NULL DEFAULT 1  COMMENT '优先级（1-低、2-中、3-高）',
    `priority_manual`     tinyint(1)    NOT NULL DEFAULT 0  COMMENT '优先级是否人工改过（0-否、1-是），是则系统不覆盖',
    `priority_reason`     varchar(200)  NOT NULL DEFAULT '' COMMENT '人工改优先级的理由',
    `cnt_inquiry`         int(11)       NOT NULL DEFAULT 0  COMMENT '近 90 天询盘次数（每天重算）',
    `cnt_order`           int(11)       NOT NULL DEFAULT 0  COMMENT '近 90 天成交次数',
    `cnt_stockout`        int(11)       NOT NULL DEFAULT 0  COMMENT '近 90 天询价无货次数',
    `cnt_lost`            int(11)       NOT NULL DEFAULT 0  COMMENT '近 90 天未成交次数',
    `cnt_lost_supply`     int(11)       NOT NULL DEFAULT 0  COMMENT '近 90 天因交期/物流类原因未成交次数',
    `cnt_search`          int(11)       NOT NULL DEFAULT 0  COMMENT '近 90 天搜索关键词次数',
    `cnt_research`        int(11)       NOT NULL DEFAULT 0  COMMENT '近 90 天同行调研次数',
    `last_signal_at`      datetime      NULL                COMMENT '最近一条信号的出现时间',
    `coverage_status`     tinyint(2)    NOT NULL DEFAULT 0  COMMENT '覆盖状态（0-未进入矩阵、1-无渠道、2-有线索未验证、3-需确认、4-可直接报价）',
    `suggested_coverage`  tinyint(2)    NOT NULL DEFAULT 0  COMMENT '建议覆盖状态，取值同 coverage_status',
    `coverage_pending`    tinyint(1)    NOT NULL DEFAULT 0  COMMENT '覆盖状态待确认（0-否、1-是）',
    `coverage_basis`      varchar(300)  NOT NULL DEFAULT '' COMMENT '建议状态的依据（显示用）',
    `coverage_note`       varchar(200)  NOT NULL DEFAULT '' COMMENT '最近一次修改覆盖状态的说明',
    `evidence_type`       tinyint(2)    NOT NULL DEFAULT 0  COMMENT '最近证据类型（0-无、1-询价记录、2-采购订单行）',
    `evidence_id`         bigint        NOT NULL DEFAULT 0  COMMENT '最近证据ID',
    `evidence_date`       date          NULL                COMMENT '最近证据日期（询价日期、下单日期）',
    `entered_matrix_at`   datetime      NULL                COMMENT '进入覆盖矩阵时间',
    `owner_id`            bigint        NOT NULL DEFAULT 0  COMMENT '负责人，关联user_basic.id，0 为没有',
    `next_action`         varchar(200)  NOT NULL DEFAULT '' COMMENT '下一步行动',
    `next_action_date`    date          NULL                COMMENT '下一步日期',
    `next_action_source`  tinyint(2)    NOT NULL DEFAULT 0  COMMENT '下一步来源（0-无、1-人工、2-系统提醒）',
    `reminder_type`       tinyint(2)    NOT NULL DEFAULT 0  COMMENT '系统提醒类型（0-无、1-询价无货、2-证据过期、3-新进入矩阵待确认、4-没有下一步）',
    `defer_count`         int(11)       NOT NULL DEFAULT 0  COMMENT '当前待办连续延后次数，3 次及以上为多次延后',
    `merged_into_id`      bigint        NOT NULL DEFAULT 0  COMMENT '合并到的需求对象ID，0 为未合并',
    `deleted_at`          datetime      NULL                COMMENT '软删除时间（合并后），NULL表示未删除',
    `create_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`           varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`           varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_demand` (`tenant_id`, `brand_id`, `scope_key`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`),
    KEY `idx_brand_category` (`brand_id`, `category_id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_coverage_status` (`coverage_status`),
    KEY `idx_owner_id` (`owner_id`, `next_action_date`),
    KEY `idx_evidence_id` (`evidence_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_update_time` (`update_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应链需求对象';

CREATE TABLE `sc_coverage_channel` (
    `id`          bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `demand_id`   bigint        NOT NULL DEFAULT 0  COMMENT '需求对象ID，关联sc_demand.id',
    `supplier_id` bigint        NOT NULL DEFAULT 0  COMMENT '供应商ID，关联supplier.id',
    `role`        tinyint(2)    NOT NULL DEFAULT 2  COMMENT '渠道角色（1-主渠道、2-备选渠道）',
    `sort_order`  int(11)       NOT NULL DEFAULT 0  COMMENT '排序',
    `deleted_at`  datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_demand_id` (`demand_id`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应链覆盖渠道（主渠道 1 个、备选最多 5 个）';

CREATE TABLE `sc_followup_log` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `record_type`      tinyint(2)    NOT NULL DEFAULT 1  COMMENT '记录类型（1-人工处理、2-系统登记）',
    `source_type`      tinyint(2)    NOT NULL DEFAULT 0  COMMENT '系统登记来源（0-人工、1-询价回价、2-询价无货、3-撤回回价）',
    `source_id`        bigint        NULL                COMMENT '系统登记的来源ID（询价记录ID）；人工处理为 NULL',
    `source_doc_no`    varchar(32)   NOT NULL DEFAULT '' COMMENT '来源单号（询价任务编号）',
    `content`          varchar(1000) NOT NULL DEFAULT '' COMMENT '做了什么 / 系统登记内容',
    `demand_ids`       json          NULL                COMMENT '涉及的需求对象ID（数组）',
    `status_changes`   json          NULL                COMMENT '覆盖状态变化（数组：demandId、from、to；未能修改的带 skipped）',
    `gap_project_id`   bigint        NOT NULL DEFAULT 0  COMMENT '所属专项ID，关联sc_gap_project.id，0 为无',
    `next_action`      varchar(200)  NOT NULL DEFAULT '' COMMENT '下一步行动',
    `next_action_date` date          NULL                COMMENT '下一步日期',
    `operator_id`      bigint        NOT NULL DEFAULT 0  COMMENT '处理人，关联user_basic.id，0 为系统',
    `idempotency_key`  varchar(64)   NULL                COMMENT '幂等键，防重复提交；系统登记为 NULL',
    `deleted_at`       datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_source` (`tenant_id`, `source_type`, `source_id`),
    UNIQUE KEY `uk_idempotency` (`tenant_id`, `idempotency_key`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_gap_project_id` (`gap_project_id`),
    KEY `idx_operator_id` (`operator_id`, `create_time`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应链处理记录';

CREATE TABLE `sc_gap_project` (
    `id`           bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `name`         varchar(50)   NOT NULL DEFAULT '' COMMENT '专项名称，默认「品牌 · 品类」',
    `brand_id`     bigint        NOT NULL DEFAULT 0  COMMENT '品牌ID，关联product_brand.id',
    `category_id`  bigint        NOT NULL DEFAULT 0  COMMENT '二级品类ID，关联product_category.id，0 为该品牌全部品类',
    `goal`         varchar(200)  NOT NULL DEFAULT '' COMMENT '目标',
    `owner_id`     bigint        NOT NULL DEFAULT 0  COMMENT '负责人，关联user_basic.id',
    `due_date`     date          NOT NULL            COMMENT '截止日期',
    `status`       tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-进行中、2-已完成、3-已取消）',
    `active_scope` varchar(48)   NULL                COMMENT '进行中时为「品牌ID:品类ID」，结束后置 NULL，保证同一范围只有一个进行中的专项',
    `result_note`  varchar(300)  NOT NULL DEFAULT '' COMMENT '结果说明或取消原因',
    `closed_at`    datetime      NULL                COMMENT '结束时间',
    `closed_by`    bigint        NOT NULL DEFAULT 0  COMMENT '结束人，关联user_basic.id',
    `deleted_at`   datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`    varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`    varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_active_scope` (`tenant_id`, `active_scope`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`),
    KEY `idx_brand_category` (`brand_id`, `category_id`),
    KEY `idx_owner_id` (`owner_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应链专项';

CREATE TABLE `sc_gap_project_item` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
    `project_id`  bigint      NOT NULL DEFAULT 0  COMMENT '专项ID，关联sc_gap_project.id',
    `demand_id`   bigint      NOT NULL DEFAULT 0  COMMENT '需求对象ID，关联sc_demand.id',
    `deleted_at`  datetime    NULL                COMMENT '移出时间（软删除），NULL表示在专项内',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_project_id` (`project_id`),
    KEY `idx_demand_id` (`demand_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应链专项包含的型号';

-- ---------------------------------------------------------------- 作战卡
ALTER TABLE `sop_stage`
    MODIFY COLUMN `board` tinyint(2) NOT NULL DEFAULT 2 COMMENT '板块（1-主动开发、2-商机成交、3-订单履约、4-售后评价、5-客户经营、6-供应链作战地图）',
    ADD COLUMN `quality_checks` json NULL COMMENT '怎样算做好（质量检查项，字符串数组）' AFTER `checklist`;

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-C1', '多源入口', '完成多源入口：询价 / 订单 / 评价', '["接入询价、订单、评价、平台搜索、独立站搜索、同行调研、售后和丢单数据", "记录来源、时间、原始内容和关联客户/平台", "标记品牌、品类、型号、场景和需求方向"]', '["来源可追溯", "原始内容未被改写", "不同来源没有重复计算"]', '至少一个有效来源；品牌或品类已识别；原始证据已归档', '', 1, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-C1');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-C2', '搜索信号', '完成搜索信号：平台 / 独立站关键词', '["收集平台关键词、独立站站内词和无结果搜索词", "统计频次、趋势和最近出现时间", "合并同义词并剔除无业务价值词", "区分搜索兴趣与已验证采购需求"]', '["关键词与平台、时间可追溯", "同义词归并规则一致", "没有把搜索量直接当订单"]', '关键词已标准化；频次和趋势明确；进入需求池', '', 2, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-C2');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-C3', '市场调研', '完成市场调研：同行 / 品牌 / 品类', '["研究同行网站、店铺、目录和主推品牌", "记录同行覆盖的品牌、品类、替代品和缺货信息", "形成可验证的市场机会假设"]', '["事实与推测分开", "同行信息有来源", "没有把同行销售表现直接当客户需求"]', '形成品牌/品类机会清单；至少有一个可验证需求假设', '', 3, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-C3');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-C4', '需求归一化', '完成需求归一化：标准化 / 去重 / 验证', '["统一品牌名、品类层级、系列、型号、后缀和版本", "合并重复需求并保留来源数量", "识别应用场景和可替代关系"]', '["型号后缀和版本不丢失", "原装、翻新、替代型号分开", "同一产品不会重复建档"]', '需求对象唯一；品牌、品类、型号、场景完整；重复记录已处理', '自动汇入的信号能识别品牌和型号时自动归类；识别不到的在这里人工归类、批量归类或忽略', 4, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-C4');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-C5', '优先级判断', '完成优先级判断：频次 / 价值 / 可信度', '["计算频次、客户价值、成交影响、毛利潜力和复购潜力", "标记供应风险和竞争压力", "形成高、中、低优先级及不确定性说明"]', '["评分有依据", "样本少标注不确定", "不只按搜索量排序"]', '优先级已确认；评分依据可追溯；高优先级需求进入供应作战地图', '系统每天按近 90 天的次数给建议优先级；高、中优先级自动进入覆盖矩阵；人工改过的以人工为准', 5, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-C5');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-M1', '品牌盘点', '完成品牌盘点：重点品牌清单', '["从需求池提取高频品牌、客户常购品牌和战略品牌", "记录品牌需求证据、来源数量和最近时间", "识别同品牌替代及停产风险"]', '["品牌名称统一", "优先级有客户和市场依据", "停产或替代判断有证据"]', '重点品牌清单完成；每个品牌有需求证据', '', 6, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-M1');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-M2', '品类分层', '完成品类分层：一级 / 二级 / 系列', '["建立一级品类、二级品类、系列和应用场景", "识别客户关联采购品类", "处理跨品牌同类产品的归类规则"]', '["分类符合工控行业习惯", "品牌、系列、品类不混层", "关联品类有业务依据"]', '品类树完成；重点品类与需求关联', '', 7, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-M2');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-M3', '型号归类', '完成型号归类：统一型号 / 后缀 / 版本', '["统一完整型号、后缀、版本和停产替代型号", "识别可替代关系和不可替代差异", "定义可报价型号边界"]', '["后缀和版本不能丢失", "替代型号与原型号分开", "状态信息可追溯"]', '型号标准化完成；可报价型号边界明确', '', 8, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-M3');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-M4', '覆盖矩阵', '完成覆盖矩阵：已覆盖 / 弱覆盖 / 缺口', '["填写主供应商、备选供应商、货源状态、成本、交期和最近验证时间", "标记A/B/C/D覆盖状态", "识别单一渠道和无渠道缺口"]', '["供应商数量不等于有效覆盖", "证据必须在有效期内", "主备渠道清楚"]', '重点型号覆盖状态完成；缺口可定位', '证据过期后系统自动降为「需确认」，并提醒重新询价', 9, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-M4');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-M5', '开发排序', '完成开发排序：按成交影响排计划', '["按需求频次、成交影响、复购潜力和供应风险排序", "建立品牌/品类/型号缺口任务", "指定负责人、预算、目标渠道和截止日期"]', '["优先级可解释", "资源不平均分配", "任务有明确完成标准"]', '开发计划已排期；负责人和日期明确', '', 10, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-M5');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-V1', '渠道搜寻', '完成渠道搜寻：生产商 / 代理商 / 经销商', '["搜索生产商、授权代理、区域代理、专业经销商和库存商", "记录渠道类型、来源和联系人", "为重点型号寻找主渠道与备选渠道"]', '["渠道来源真实", "不只依赖搜索排名", "联系人和渠道类型准确"]', '找到符合品牌/品类要求的潜在渠道；进入资格审查', '找到主营该品牌的供应商后，系统建议改为「有线索未验证」', 11, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-V1');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-V2', '资格审查', '完成资格审查：主体 / 品牌 / 品类', '["核验主体、经营年限、品牌关系、主营品类和联系人", "确认付款、开票、物流和合规条件", "记录资格风险"]', '["主体与报价主体一致", "品牌能力有证据", "风险信息完整"]', '通过基础资格审查；可验证范围明确', '', 12, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-V2');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-V3', '货源验证', '完成货源验证：原装 / 状态 / 证据', '["核验库存照片、铭牌、序列号、包装和产品状态", "区分原装、翻新、拆机、期货和替代品", "核对货源型号与需求型号"]', '["货源证据与报价型号一致", "不能用旧照片替代当前货源", "状态描述准确"]', '货源状态明确；真实性风险可接受', '有了有效期内的询价记录或采购订单，系统建议改为「可直接报价」', 13, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-V3');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-V4', '条件验证', '完成条件验证：成本 / 交期 / 响应', '["核验采购成本、币种、MOQ、有效期、交期和响应速度", "确认物流、付款和价格变动条件", "记录供应承诺与风险"]', '["成本和交期有时效", "原币种保留", "口头承诺与书面条件一致"]', '成本、交期、有效期和风险完整；可用于报价', '有了有效期内的询价记录或采购订单，系统建议改为「可直接报价」', 14, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-V4');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-V5', '能力分级', '完成能力分级：A / B / C / D', '["按货源、价格、交期、质量、响应和履约历史评定A/B/C/D", "按品牌或品类分别评定能力", "决定主力、备选、观察或淘汰"]', '["分级基于历史证据", "一次报价不能直接评A", "不同品牌能力分开评价"]', '供应商等级和可用范围明确；是否进入供应池已决定', '', 15, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-V5');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-Q1', '询价响应', '完成询价响应：主渠道 / 备选渠道', '["同时询问主渠道和备选渠道", "传递完整品牌、型号、后缀、数量和交期", "记录供应商回复时间和有效期"]', '["询价信息完整", "没有只问主体型号", "响应时间可追踪"]', '取得主渠道或备选渠道有效回复；关键证据已归档', '询价任务回价后系统自动登记处理记录，并改为「可直接报价」', 16, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-Q1');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-Q2', '成本确认', '完成成本确认：采购成本 / 币种 / 有效期', '["确认采购成本、币种、税费、运费、MOQ和付款条件", "记录成本有效期和变动风险", "核对历史成本差异"]', '["成本可追溯", "币种明确", "没有沿用历史价格"]', '有效成本已确认；报价基础完整', '询价任务回价后系统自动登记处理记录，并改为「可直接报价」', 17, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-Q2');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-Q3', '交期确认', '完成交期确认：库存 / 交期 / 承诺', '["确认现货、可出货时间、生产周期和物流时间", "区分库存交期与预计交期", "记录交期承诺和延误风险"]', '["交期依据明确", "库存交期和预计交期分开", "承诺有供应商依据"]', '交期和风险已确认；可承诺范围明确', '询价任务回价后系统自动登记处理记录，并改为「可直接报价」', 18, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-Q3');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-Q4', '采购履约', '完成采购履约：下单 / 跟进 / 到货', '["采购下单并取得供应商书面确认", "跟进关键交期节点和到货计划", "核对型号、数量、状态和随货资料"]', '["采购条件与客户承诺一致", "关键节点有记录", "型号数量零错漏"]', '供应商已确认订单条件；采购进入可跟踪状态', '', 19, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-Q4');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-Q5', '异常处理', '完成异常处理：延期 / 缺货 / 客诉', '["处理延期、缺货、错型、质量、价格变化和供应商失联", "评估对客户报价、订单和交期的影响", "制定替代渠道、退款或重采方案"]', '["先事实后责任", "方案有截止日", "客户影响已评估"]', '异常已关闭；或已有客户认可的替代方案', '', 20, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-Q5');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-G1', '供应商评分', '完成供应商评分：交期 / 质量 / 响应', '["统计响应、报价、交期、质量、异常和履约数据", "按品牌/品类计算表现", "形成评分、问题项和改进建议"]', '["评分基于订单和报价事实", "不只看价格或关系", "样本量和周期明确"]', '评分完成；等级和问题项明确', '', 21, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-G1');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-G2', '赢丢单复盘', '完成赢丢单复盘：供应原因识别', '["分析价格、交期、货源、真实性和替代能力对赢丢单的影响", "关联客户需求、报价和供应商记录", "形成供应改进任务"]', '["赢丢单原因有证据", "不把所有丢单归因于价格", "供应原因与销售原因分开"]', '供应原因已明确；改进任务已建立', '', 22, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-G2');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-G3', '履约复盘', '完成履约复盘：交期 / 质量 / 客诉', '["对比承诺与实际交期、到货质量、错型、客诉和成本偏差", "量化异常影响", "识别重复发生的供应问题"]', '["承诺与实际可追溯", "异常影响可量化", "问题供应商可定位"]', '履约问题已归因；供应商和流程改进项明确', '', 23, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-G3');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-G4', '能力升级', '完成能力升级：主力 / 备选 / 淘汰', '["将稳定渠道升级为主力", "将高风险渠道降级并限制使用范围", "建立主力与备选渠道结构"]', '["升降级有阈值", "不同品牌/品类分开评价", "结果能影响后续报价"]', '主力、备选、限制使用和淘汰状态明确；关键证据已归档', '', 24, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-G4');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`quality_checks`,`gate_text`,`auto_hint`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 6, 'SC-G5', '能力补全', '完成能力补全：新品牌 / 新品类', '["根据新品牌、新品类、复购、丢单和缺货补充供应能力", "更新覆盖矩阵和开发排序", "建立下一轮验证计划"]', '["补全计划与需求地图关联", "不为增加供应商而开发", "目标和截止日期明确"]', '新缺口进入需求池或开发计划；形成下一轮循环', '', 25, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 6 AND s.`stage_code` = 'SC-G5');

-- ---------------------------------------------------------------- 系统设置
INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `is_encrypted`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, 'supply.evidence-valid-days', '供应证据有效天数', '30', 'INTEGER', 1, 0, 'supply', '询价记录、采购订单作为「可直接报价」证据的有效天数，按询价日期、下单日期算，距今不超过这么多天的有效（30 天时第 30 天有效、第 31 天过期）', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `tenant_id` = 0 AND `config_key` = 'supply.evidence-valid-days' AND `deleted_at` IS NULL);

-- ---------------------------------------------------------------- 菜单与按钮
-- 一级菜单排在采购管理（3）之后：原来排序号 ≥ 4 的一级菜单后移一位（系统管理、租户管理除外）
UPDATE `resource` SET `sort` = `sort` + 1
WHERE `pid` = 0 AND `sort` >= 4 AND `id` NOT IN (100002, 100003);

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 110235 AS id, 0 AS pid, 'RS1110235' AS code, '供应链管理' AS name, 1 AS type, 4 AS sort, 'deploymentUnit' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/supply-chain' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 110236, 110235, 'RS2110236', '供应链跟进', 2, 1, 'schedule', '', '', '', '/supply-chain/followup', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110237, 110235, 'RS2110237', '覆盖矩阵', 2, 2, 'appstore', '', '', '', '/supply-chain/coverage', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110238, 110236, 'RS3110238', '维护供应链跟进', 3, 1, '', '', '', '', '', 'supply:followup:edit', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110239, 110236, 'RS3110239', '查看供应链团队视图', 3, 2, '', '', '', '', '', 'supply:followup:team', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110240, 110236, 'RS3110240', '维护专项', 3, 3, '', '', '', '', '', 'supply:project:edit', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110241, 110237, 'RS3110241', '维护覆盖矩阵', 3, 1, '', '', '', '', '', 'supply:coverage:edit', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.`id`);

-- 加进含「采购订单」菜单的套餐（套餐只存页面与按钮，不存分组）
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(
        JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`,
        '$', 110235), '$', 110236), '$', 110237), '$', 110238), '$', 110239), '$', 110240), '$', 110241)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND JSON_CONTAINS(`menu_ids`, '100088') AND NOT JSON_CONTAINS(`menu_ids`, '110235');

-- 授给内置租户管理员
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, v.`id`, v.`code`
FROM `role` r
JOIN (SELECT 110235 AS id, 'RS1110235' AS code UNION ALL SELECT 110236, 'RS2110236' UNION ALL SELECT 110237, 'RS2110237'
      UNION ALL SELECT 110238, 'RS3110238' UNION ALL SELECT 110239, 'RS3110239' UNION ALL SELECT 110240, 'RS3110240'
      UNION ALL SELECT 110241, 'RS3110241') v
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = v.`id`);
