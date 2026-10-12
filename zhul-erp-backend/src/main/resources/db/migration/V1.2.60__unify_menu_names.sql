-- 统一菜单命名（unify-menu-names）：只改显示名称，地址、权限码、编号前缀不变

-- 分组
UPDATE `resource` SET `name` = '销售管理', `update_time` = NOW() WHERE `id` = 100081;
UPDATE `resource` SET `name` = '物流管理', `update_time` = NOW() WHERE `id` = 100096;
UPDATE `resource` SET `name` = '商品管理', `update_time` = NOW() WHERE `id` = 100004;

-- 菜单
UPDATE `resource` SET `name` = '询盘单', `update_time` = NOW() WHERE `id` = 100051;
UPDATE `resource` SET `name` = '询价记录', `update_time` = NOW() WHERE `id` = 100056;
UPDATE `resource` SET `name` = '采购订单', `update_time` = NOW() WHERE `id` = 100088;
UPDATE `resource` SET `name` = '采购入库', `update_time` = NOW() WHERE `id` = 100091;
UPDATE `resource` SET `name` = '闲置库存', `update_time` = NOW() WHERE `id` = 100092;
UPDATE `resource` SET `name` = '实拍任务', `update_time` = NOW() WHERE `id` = 100093;
UPDATE `resource` SET `name` = '销售出库', `update_time` = NOW() WHERE `id` = 100095;
UPDATE `resource` SET `name` = '运费账单', `update_time` = NOW() WHERE `id` = 100098;
UPDATE `resource` SET `name` = '收款单', `update_time` = NOW() WHERE `id` = 100085;
UPDATE `resource` SET `name` = '应付单', `update_time` = NOW() WHERE `id` = 100103;
UPDATE `resource` SET `name` = '付款单', `update_time` = NOW() WHERE `id` = 100104;
UPDATE `resource` SET `name` = '单据模板', `update_time` = NOW() WHERE `id` = 100076;
UPDATE `resource` SET `name` = '编号规则', `update_time` = NOW() WHERE `id` = 100086;

-- 按钮
UPDATE `resource` SET `name` = '管理单据模板', `update_time` = NOW() WHERE `id` = 110178;
UPDATE `resource` SET `name` = '编辑编号规则', `update_time` = NOW() WHERE `id` = 110182;
UPDATE `resource` SET `name` = '新建采购订单', `update_time` = NOW() WHERE `id` = 110189;
UPDATE `resource` SET `name` = '取消采购订单', `update_time` = NOW() WHERE `id` = 110190;
UPDATE `resource` SET `name` = '处置闲置库存', `update_time` = NOW() WHERE `id` = 110194;
UPDATE `resource` SET `name` = '编辑运费账单', `update_time` = NOW() WHERE `id` = 110200;

-- 编号前缀的说明文字
UPDATE `sys_config`
SET `remark` = '2–4 位大写字母，只加在对外单据（报价单、PI、CI、PL、采购订单、借项 / 贷项通知单、运费账单）上，可为空',
    `update_time` = NOW()
WHERE `config_key` = 'document.number-prefix';
