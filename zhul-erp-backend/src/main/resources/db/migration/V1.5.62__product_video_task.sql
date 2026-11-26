-- 视频任务（openspec product-video-task）：
-- 1. 视频任务 product_video_task（一家公司一个商品一个任务）、素材 product_video_material、生成结果 product_video_output、操作记录 product_video_log；
-- 2. product_media 加 source_attachment_id，记录「设为官方素材」的来源附件；
-- 3. 平台设置 video.queue.window（夜间生成时段，默认 20:00-07:00）；
-- 4. 菜单「运营管理 → 视频任务」与按钮权限「视频任务上传」「视频任务审核」，排在营销任务之后、发品任务之前。
CREATE TABLE `product_video_task` (
    `id`                bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`         int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID',
    `product_id`        bigint(20)   NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id（共享商品库）',
    `brand`             varchar(100) NOT NULL DEFAULT '' COMMENT '品牌快照',
    `model`             varchar(150) NOT NULL DEFAULT '' COMMENT '型号快照',
    `asset_key`         varchar(255) NOT NULL DEFAULT '' COMMENT '型号归并键：小写（品牌 + 空格 + 型号），用于关联素材库',
    `status`            tinyint(2)   NOT NULL DEFAULT 1 COMMENT '状态（1-待生成、2-排队中、3-生成中、4-待审核、5-已通过、6-已退回、7-生成失败）',
    `script`            json         NULL COMMENT '分镜脚本：{"title":"…","shots":[{"attachmentId":1,"caption":"…"}]}',
    `script_source`     tinyint(2)   NOT NULL DEFAULT 0 COMMENT '脚本来源（0-无、1-AI、2-手工修改）',
    `script_status`     tinyint(2)   NOT NULL DEFAULT 0 COMMENT '脚本生成状态（0-未生成、1-生成中、2-已生成、3-生成失败）',
    `script_ai_task_id` bigint(20)   NOT NULL DEFAULT 0 COMMENT '最近一次生成脚本的AI任务ID，关联ai_task.id',
    `script_error`      varchar(300) NOT NULL DEFAULT '' COMMENT '脚本生成失败或不合格的原因',
    `queued_at`         datetime     NULL COMMENT '加入队列时间，队列按它先后派发',
    `next_try_at`       datetime     NULL COMMENT '生成服务不可用时下次重试的时间',
    `started_at`        datetime     NULL COMMENT '开始生成时间',
    `finished_at`       datetime     NULL COMMENT '生成结束时间',
    `fail_reason`       varchar(300) NOT NULL DEFAULT '' COMMENT '生成失败原因',
    `review_by`         varchar(32)  NOT NULL DEFAULT '' COMMENT '审核人',
    `review_time`       datetime     NULL COMMENT '审核时间',
    `reject_reason`     varchar(200) NOT NULL DEFAULT '' COMMENT '退回原因',
    `current_version`   int(11)      NOT NULL DEFAULT 0 COMMENT '最近一次生成的版本号，0表示还没生成过',
    `deleted_at`        datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`         varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`         varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_product` (`tenant_id`, `product_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_tenant_status` (`tenant_id`, `status`),
    KEY `idx_status_queued` (`status`, `queued_at`),
    KEY `idx_script_ai_task` (`script_ai_task_id`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频任务：一个商品的图片素材、分镜脚本与生成状态';

CREATE TABLE `product_video_material` (
    `id`            bigint(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)     NOT NULL DEFAULT 0 COMMENT '租户ID',
    `task_id`       bigint(20)  NOT NULL DEFAULT 0 COMMENT '视频任务ID，关联product_video_task.id',
    `attachment_id` bigint(20)  NOT NULL DEFAULT 0 COMMENT '图片附件ID，关联biz_attachment.id（归属类型 VIDEO_TASK）',
    `sort`          int(11)     NOT NULL DEFAULT 0 COMMENT '顺序，从小到大',
    `used`          tinyint(2)  NOT NULL DEFAULT 1 COMMENT '是否用于视频（0-不用于视频、1-选用）',
    `deleted_at`    datetime    NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_attachment_id` (`attachment_id`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频任务的图片素材：顺序与是否选用';

CREATE TABLE `product_video_output` (
    `id`                  bigint(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`           int(11)     NOT NULL DEFAULT 0 COMMENT '租户ID',
    `task_id`             bigint(20)  NOT NULL DEFAULT 0 COMMENT '视频任务ID，关联product_video_task.id',
    `version`             int(11)     NOT NULL DEFAULT 1 COMMENT '生成版本，每次生成加1',
    `aspect`              varchar(8)  NOT NULL DEFAULT '' COMMENT '画幅（9:16-竖屏、16:9-横屏）',
    `video_attachment_id` bigint(20)  NOT NULL DEFAULT 0 COMMENT '视频附件ID，关联biz_attachment.id（归属类型 VIDEO_OUTPUT）',
    `cover_attachment_id` bigint(20)  NOT NULL DEFAULT 0 COMMENT '封面附件ID，关联biz_attachment.id（归属类型 VIDEO_OUTPUT）',
    `duration_sec`        int(11)     NOT NULL DEFAULT 0 COMMENT '视频时长（秒）',
    `status`              tinyint(2)  NOT NULL DEFAULT 1 COMMENT '状态（1-待审核、2-已通过、3-已替换、4-已退回）',
    `deleted_at`          datetime    NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`         datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`           varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`         datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`           varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_version_aspect` (`task_id`, `version`, `aspect`),
    KEY `idx_tenant_status` (`tenant_id`, `status`),
    KEY `idx_video_attachment` (`video_attachment_id`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频任务的生成结果：每次生成竖屏、横屏各一行';

CREATE TABLE `product_video_log` (
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID',
    `task_id`     bigint(20)   NOT NULL DEFAULT 0 COMMENT '视频任务ID，关联product_video_task.id',
    `action`      varchar(16)  NOT NULL DEFAULT '' COMMENT '动作（QUEUE-加入队列、DEQUEUE-移出队列、START-开始生成、DONE-生成完成、FAIL-生成失败、APPROVE-通过、REJECT-退回）',
    `version`     int(11)      NOT NULL DEFAULT 0 COMMENT '相关的生成版本，0表示不涉及',
    `note`        varchar(300) NOT NULL DEFAULT '' COMMENT '说明：失败原因、退回原因等',
    `operator`    varchar(32)  NOT NULL DEFAULT '' COMMENT '操作人，系统动作为「系统」',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频任务的操作记录：生成与审核的历史';

ALTER TABLE `product_media`
    ADD COLUMN `source_attachment_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '来源附件ID：由本公司素材「设为官方素材」时为biz_attachment.id，其余为0' AFTER `source`,
    ADD KEY `idx_source_attachment` (`source_attachment_id`);

-- ---------------------------------------------------------------- 平台设置
INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `is_encrypted`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, 'video.queue.window', '视频夜间生成时段', '20:00-07:00', 'STRING', 1, 0, 'video', '只在这个时段内开始生成视频，可以跨零点（北京时间）；只有平台账号能修改', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `tenant_id` = 0 AND `config_key` = 'video.queue.window' AND `deleted_at` IS NULL);

-- ---------------------------------------------------------------- 菜单与权限
-- 发品任务、素材管理顺延，视频任务排在营销任务之后
UPDATE `resource` SET `sort` = 6, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100116 AND `sort` = 5;
UPDATE `resource` SET `sort` = 5, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100117 AND `sort` = 4;
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 100133, 100101, 'RS2100133', '视频任务', 2, 4, 'video-camera', '', '', '', '/operations/videos', '', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 100133);
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110256, 100133, 'RS3110256', '视频任务上传', 3, 1, '', '', '', '', '', 'operations:video:upload', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 110256);
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110257, 100133, 'RS3110257', '视频任务审核', 3, 2, '', '', '', '', '', 'operations:video:review', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 110257);
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100133)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND NOT JSON_CONTAINS(`menu_ids`, '100133');
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110256)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND NOT JSON_CONTAINS(`menu_ids`, '110256');
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110257)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND NOT JSON_CONTAINS(`menu_ids`, '110257');
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100101)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND NOT JSON_CONTAINS(`menu_ids`, '100101');
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100101, 100133, 110256, 110257)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);
