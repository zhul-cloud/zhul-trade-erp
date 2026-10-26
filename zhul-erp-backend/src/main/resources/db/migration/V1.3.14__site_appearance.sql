-- 外观设置（openspec site-appearance）：写入平台统一的内置外观配置（tenant_id=0，租户不可见、不可改）。
-- 系统设置 PRD 早已约定这些键，但此前从未写入，外观设置无法修改。默认值等于此前页面写死的内容，升级后外观不变。
-- 分组存代码 appearance（与其他内置配置一致用英文代码），系统参数页显示为「外观设置」。
-- 只补缺失的键，已存在的值保持不变，可重复执行。

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, k.config_key, k.config_name, k.config_value, k.config_type, 1, 'appearance', k.remark, 'sys', 'sys'
FROM (
    SELECT 'sys.site.name' AS config_key, '系统名称' AS config_name, '烛龙ERP' AS config_value, 'STRING' AS config_type,
           '显示在侧边栏与浏览器标签；未上传 Logo 时，默认 Logo 显示系统名称的第一个字' AS remark
    UNION ALL SELECT 'sys.site.subtitle', '系统副标题', '烛龙外贸业财一体化中台', 'STRING',
           '显示在登录页 Logo 下方'
    UNION ALL SELECT 'sys.site.logo', '系统 Logo', '', 'URL',
           '显示在登录页、侧边栏与浏览器标签，建议上传方形图片；留空显示默认 Logo'
    UNION ALL SELECT 'sys.login.background', '登录页背景', '', 'URL',
           '登录、注册、找回密码页面的背景图片；留空使用默认背景'
    UNION ALL SELECT 'sys.login.footer', '登录页底部文字', '© 2026 烛龙科技', 'STRING',
           '显示在登录页底部，如版权信息'
) k
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` c WHERE c.`tenant_id` = 0 AND c.`config_key` = k.config_key);
