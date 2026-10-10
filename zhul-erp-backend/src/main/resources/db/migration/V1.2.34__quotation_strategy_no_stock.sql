-- ===========================
-- 报价策略与无货展示：无货行的替代型号快照；报价策略「按金额分层毛利」的默认档位
-- 见 openspec/changes/add-quotation-pricing-strategy
-- ===========================

ALTER TABLE `quotation_item`
    ADD COLUMN `replacement_model` varchar(128) NOT NULL DEFAULT '' COMMENT '替代型号（无货行：询价时标停产记录的替代型号，可改）' AFTER `no_stock`;

-- 平台默认档位：按采购成本价（CNY）取毛利率，maxCost 为空表示以上全部
INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, 'quotation.strategy.cost-tiers', '报价策略：按金额分层毛利', '[{"maxCost":300,"marginRate":35},{"maxCost":3000,"marginRate":20},{"maxCost":null,"marginRate":12}]',
       'JSON', 1, 'quotation', '报价单「报价策略 · 按金额分层毛利」的默认档位；在报价策略抽屉里「保存为默认」后写入租户自己的一份', 'sys', 'sys'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `tenant_id` = 0 AND `config_key` = 'quotation.strategy.cost-tiers');
