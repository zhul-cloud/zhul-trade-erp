-- 外观配置的分组改为「系统」（openspec config-group-sys）：5 项配置的键都是 sys. 开头，
-- 按「内置配置的键以分组代码为第一段」归入 sys 分组（页面显示「系统」），不再用 appearance（外观）。
-- 外观配置只有平台级（tenant_id=0）一份，租户不可改，没有租户副本。

UPDATE `sys_config`
SET `config_group` = 'sys', `update_time` = NOW()
WHERE `tenant_id` = 0
  AND `config_group` = 'appearance'
  AND `config_key` IN ('sys.site.name', 'sys.site.subtitle', 'sys.site.logo', 'sys.login.background', 'sys.login.footer');
