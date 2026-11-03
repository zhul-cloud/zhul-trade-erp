-- 单据导出文件名开头的公司英文名：「公司英文名 - 单据名 - 客户名称 - 单据编号」
-- 平台模板为空（文件名不带公司名），租户在系统参数里改后生成租户自己的值。
INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, 'document.company.name-en', '公司英文名', '', 'STRING', 1, 'document', '报价单、PI、CI、装箱单导出文件名开头的公司英文名；留空时文件名不带公司名', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `tenant_id` = 0 AND `config_key` = 'document.company.name-en' AND `deleted_at` IS NULL);

-- 福州福唯科技有限公司（租户 1000）填好自己的英文名；其他环境没有这个租户时不插入
INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT t.`id`, 'document.company.name-en', '公司英文名', 'Fuzhou Fouwell Technology Co., Ltd', 'STRING', 1, 'document', '报价单、PI、CI、装箱单导出文件名开头的公司英文名；留空时文件名不带公司名', 'sys', 'sys'
FROM `tenant` t
WHERE t.`id` = 1000 AND t.`name` = '福州福唯科技有限公司'
  AND NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `tenant_id` = 1000 AND `config_key` = 'document.company.name-en' AND `deleted_at` IS NULL);

-- 回退：
-- DELETE FROM `sys_config` WHERE `config_key` = 'document.company.name-en';
