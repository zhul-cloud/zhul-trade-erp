-- 福州福唯科技有限公司：组织架构初始化（部门、岗位、角色与菜单权限）
--
-- 用途：新环境（生产）一次性初始化基础数据；在已有数据的环境里执行也安全，可重复执行：
--   部门按「上级 + 名称」、岗位按编码、角色按名称匹配，已有的更新备注等字段，没有的新建；
--   不改用户、不改部门负责人，不删除任何已有的部门、岗位、角色。
-- 注意：
--   1. 先在平台「租户列表」创建租户「福州福唯科技有限公司」，再执行本脚本；租户名称不同时改下面的 @tenant_name。
--   2. 本脚本列出的 9 个角色，菜单权限会重置为本脚本的预设（在角色管理里手工调过的会被覆盖）。
--   3. 两个事业部会被标记为独立核算；公司第一次有事业部时，已有单据全部归入阿里国际站事业部（与在部门管理里标记的效果相同）。
--   4. 执行后在「用户管理」给每个人选部门、岗位和角色。
-- 菜单权限按开发库里已调好的角色整理；菜单编号由数据库迁移写入，各环境一致。

SET NAMES utf8mb4;
SET @tenant_name = '福州福唯科技有限公司';
SET @tid = (SELECT `id` FROM `tenant` WHERE `name` = @tenant_name ORDER BY `id` LIMIT 1);
-- 租户不存在时 @tid 为 NULL，下面第一条写入会因 tenant_id 不能为空而报错，不会写入任何数据
SELECT @tid AS `tenant_id`, @tenant_name AS `tenant_name`;

START TRANSACTION;

-- 执行前是否已有事业部（决定是否把已有单据归入第一个事业部）
SET @had_bu = (SELECT COUNT(*) FROM `department` WHERE `tenant_id` = @tid AND `is_business_unit` = 1 AND `deleted_at` IS NULL);

-- ================================================================ 部门

-- 福州福唯科技有限公司
INSERT INTO `department` (`tenant_id`, `pid`, `code`, `name`, `all_name`, `level`, `remark`, `sort`, `status`, `create_by`, `update_by`)
SELECT @tid, 0, '', '福州福唯科技有限公司', '福州福唯科技有限公司', 1, '公司主体。按「管理统一、财务独立」组织：总经办统一管理，共享中台为各事业部提供服务，各渠道事业部独立核算利润。', 0, 1, 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `department` WHERE `tenant_id` = @tid AND `pid` = 0 AND `name` = '福州福唯科技有限公司' AND `deleted_at` IS NULL);
SET @d_ROOT = (SELECT `id` FROM `department` WHERE `tenant_id` = @tid AND `pid` = 0 AND `name` = '福州福唯科技有限公司' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `department` SET `code` = IF(`code` = '', CONCAT('DP', `id`), `code`), `all_name` = IF('福州福唯科技有限公司' = '', `all_name`, '福州福唯科技有限公司'),
  `remark` = '公司主体。按「管理统一、财务独立」组织：总经办统一管理，共享中台为各事业部提供服务，各渠道事业部独立核算利润。', `sort` = 0, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = @d_ROOT;

-- 总经办
INSERT INTO `department` (`tenant_id`, `pid`, `code`, `name`, `all_name`, `level`, `remark`, `sort`, `status`, `create_by`, `update_by`)
SELECT @tid, @d_ROOT, '', '总经办', '', 2, '公司战略与经营决策：设立或调整事业部、任命合伙人、制定公共费用分摊规则，主持每月经营会。不独立核算，费用记为公共。', 0, 1, 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_ROOT AND `name` = '总经办' AND `deleted_at` IS NULL);
SET @d_GMO = (SELECT `id` FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_ROOT AND `name` = '总经办' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `department` SET `code` = IF(`code` = '', CONCAT('DP', `id`), `code`), `all_name` = IF('' = '', `all_name`, ''),
  `remark` = '公司战略与经营决策：设立或调整事业部、任命合伙人、制定公共费用分摊规则，主持每月经营会。不独立核算，费用记为公共。', `sort` = 0, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = @d_GMO;

-- 共享中台
INSERT INTO `department` (`tenant_id`, `pid`, `code`, `name`, `all_name`, `level`, `remark`, `sort`, `status`, `create_by`, `update_by`)
SELECT @tid, @d_ROOT, '', '共享中台', '', 2, '为各事业部提供运营、供应链、财务人事、IT 服务，不独立核算。费用记为公共，每月按销售额比例分摊给各事业部，每个事业部至少承担最低分摊比例。', 1, 1, 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_ROOT AND `name` = '共享中台' AND `deleted_at` IS NULL);
SET @d_SHARED = (SELECT `id` FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_ROOT AND `name` = '共享中台' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `department` SET `code` = IF(`code` = '', CONCAT('DP', `id`), `code`), `all_name` = IF('' = '', `all_name`, ''),
  `remark` = '为各事业部提供运营、供应链、财务人事、IT 服务，不独立核算。费用记为公共，每月按销售额比例分摊给各事业部，每个事业部至少承担最低分摊比例。', `sort` = 1, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = @d_SHARED;

-- 运营部门
INSERT INTO `department` (`tenant_id`, `pid`, `code`, `name`, `all_name`, `level`, `remark`, `sort`, `status`, `create_by`, `update_by`)
SELECT @tid, @d_SHARED, '', '运营部门', '', 3, '各平台店铺运营（阿里国际站、中国制造网）、商品发布与素材、SEO、独立站与社媒，对流量质量和获客成本负责。平台年费、推广费等可直接归属的费用计入对应事业部。', 1, 1, 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_SHARED AND `name` = '运营部门' AND `deleted_at` IS NULL);
SET @d_OPSD = (SELECT `id` FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_SHARED AND `name` = '运营部门' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `department` SET `code` = IF(`code` = '', CONCAT('DP', `id`), `code`), `all_name` = IF('' = '', `all_name`, ''),
  `remark` = '各平台店铺运营（阿里国际站、中国制造网）、商品发布与素材、SEO、独立站与社媒，对流量质量和获客成本负责。平台年费、推广费等可直接归属的费用计入对应事业部。', `sort` = 1, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = @d_OPSD;

-- 供应链部门
INSERT INTO `department` (`tenant_id`, `pid`, `code`, `name`, `all_name`, `level`, `remark`, `sort`, `status`, `create_by`, `update_by`)
SELECT @tid, @d_SHARED, '', '供应链部门', '', 3, '供应商准入与评估，PLC、HMI、伺服电机、伺服驱动器等品类价格体系，采购与履约规范，询价分配与兼职采购管理，到货验收、质检与仓储。', 2, 1, 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_SHARED AND `name` = '供应链部门' AND `deleted_at` IS NULL);
SET @d_SCD = (SELECT `id` FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_SHARED AND `name` = '供应链部门' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `department` SET `code` = IF(`code` = '', CONCAT('DP', `id`), `code`), `all_name` = IF('' = '', `all_name`, ''),
  `remark` = '供应商准入与评估，PLC、HMI、伺服电机、伺服驱动器等品类价格体系，采购与履约规范，询价分配与兼职采购管理，到货验收、质检与仓储。', `sort` = 2, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = @d_SCD;

-- 综合部门
INSERT INTO `department` (`tenant_id`, `pid`, `code`, `name`, `all_name`, `level`, `remark`, `sort`, `status`, `create_by`, `update_by`)
SELECT @tid, @d_SHARED, '', '综合部门', '', 3, '财务（记账、收付款、结汇、各事业部利润表与分红测算）、出纳、人事（招聘、入职培训、薪酬绩效）与行政。', 3, 1, 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_SHARED AND `name` = '综合部门' AND `deleted_at` IS NULL);
SET @d_ADM = (SELECT `id` FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_SHARED AND `name` = '综合部门' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `department` SET `code` = IF(`code` = '', CONCAT('DP', `id`), `code`), `all_name` = IF('' = '', `all_name`, ''),
  `remark` = '财务（记账、收付款、结汇、各事业部利润表与分红测算）、出纳、人事（招聘、入职培训、薪酬绩效）与行政。', `sort` = 3, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = @d_ADM;

-- IT部门
INSERT INTO `department` (`tenant_id`, `pid`, `code`, `name`, `all_name`, `level`, `remark`, `sort`, `status`, `create_by`, `update_by`)
SELECT @tid, @d_SHARED, '', 'IT部门', '', 3, 'ERP、CRM 等系统的建设与运维，账号与权限管理，经营数据报表。', 4, 1, 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_SHARED AND `name` = 'IT部门' AND `deleted_at` IS NULL);
SET @d_ITD = (SELECT `id` FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_SHARED AND `name` = 'IT部门' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `department` SET `code` = IF(`code` = '', CONCAT('DP', `id`), `code`), `all_name` = IF('' = '', `all_name`, ''),
  `remark` = 'ERP、CRM 等系统的建设与运维，账号与权限管理，经营数据报表。', `sort` = 4, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = @d_ITD;

-- 阿里国际站事业部
INSERT INTO `department` (`tenant_id`, `pid`, `code`, `name`, `all_name`, `level`, `remark`, `sort`, `status`, `create_by`, `update_by`)
SELECT @tid, @d_ROOT, '', '阿里国际站事业部', '阿里国际站事业部', 2, '阿里国际站渠道团队，独立核算收入、成本、费用和利润，合伙人按本事业部利润分红（出资与分红见合伙协议）。2026 年 10 月起核算，此前的单据全部归入本事业部。', 2, 1, 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_ROOT AND `name` = '阿里国际站事业部' AND `deleted_at` IS NULL);
SET @d_ALI = (SELECT `id` FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_ROOT AND `name` = '阿里国际站事业部' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `department` SET `code` = IF(`code` = '', CONCAT('DP', `id`), `code`), `all_name` = IF('阿里国际站事业部' = '', `all_name`, '阿里国际站事业部'),
  `remark` = '阿里国际站渠道团队，独立核算收入、成本、费用和利润，合伙人按本事业部利润分红（出资与分红见合伙协议）。2026 年 10 月起核算，此前的单据全部归入本事业部。', `sort` = 2, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = @d_ALI;

-- 业务部
INSERT INTO `department` (`tenant_id`, `pid`, `code`, `name`, `all_name`, `level`, `remark`, `sort`, `status`, `create_by`, `update_by`)
SELECT @tid, @d_ALI, '', '业务部', '', 3, '阿里国际站询盘接待、报价、PI、订单跟进与客户维护。客户按首次接触归本事业部，以后从其他渠道来的询盘仍归本事业部。', 0, 1, 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_ALI AND `name` = '业务部' AND `deleted_at` IS NULL);
SET @d_ALI_S = (SELECT `id` FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_ALI AND `name` = '业务部' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `department` SET `code` = IF(`code` = '', CONCAT('DP', `id`), `code`), `all_name` = IF('' = '', `all_name`, ''),
  `remark` = '阿里国际站询盘接待、报价、PI、订单跟进与客户维护。客户按首次接触归本事业部，以后从其他渠道来的询盘仍归本事业部。', `sort` = 0, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = @d_ALI_S;

-- 采购部
INSERT INTO `department` (`tenant_id`, `pid`, `code`, `name`, `all_name`, `level`, `remark`, `sort`, `status`, `create_by`, `update_by`)
SELECT @tid, @d_ALI, '', '采购部', '', 3, '本事业部订单的询价、下采购单、跟单与发货登记；供应商与价格体系按供应链部门的规范执行。', 1, 1, 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_ALI AND `name` = '采购部' AND `deleted_at` IS NULL);
SET @d_ALI_P = (SELECT `id` FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_ALI AND `name` = '采购部' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `department` SET `code` = IF(`code` = '', CONCAT('DP', `id`), `code`), `all_name` = IF('' = '', `all_name`, ''),
  `remark` = '本事业部订单的询价、下采购单、跟单与发货登记；供应商与价格体系按供应链部门的规范执行。', `sort` = 1, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = @d_ALI_P;

-- 中国制造网事业部
INSERT INTO `department` (`tenant_id`, `pid`, `code`, `name`, `all_name`, `level`, `remark`, `sort`, `status`, `create_by`, `update_by`)
SELECT @tid, @d_ROOT, '', '中国制造网事业部', '中国制造网事业部', 2, '中国制造网渠道团队，独立核算收入、成本、费用和利润，合伙人按本事业部利润分红（出资与分红见合伙协议）。2026 年 11 月起核算并参与公共费用分摊。', 3, 1, 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_ROOT AND `name` = '中国制造网事业部' AND `deleted_at` IS NULL);
SET @d_MIC = (SELECT `id` FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_ROOT AND `name` = '中国制造网事业部' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `department` SET `code` = IF(`code` = '', CONCAT('DP', `id`), `code`), `all_name` = IF('中国制造网事业部' = '', `all_name`, '中国制造网事业部'),
  `remark` = '中国制造网渠道团队，独立核算收入、成本、费用和利润，合伙人按本事业部利润分红（出资与分红见合伙协议）。2026 年 11 月起核算并参与公共费用分摊。', `sort` = 3, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = @d_MIC;

-- 业务部
INSERT INTO `department` (`tenant_id`, `pid`, `code`, `name`, `all_name`, `level`, `remark`, `sort`, `status`, `create_by`, `update_by`)
SELECT @tid, @d_MIC, '', '业务部', '', 3, '中国制造网询盘接待、报价、PI、订单跟进与客户维护。客户按首次接触归本事业部，以后从其他渠道来的询盘仍归本事业部。', 0, 1, 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_MIC AND `name` = '业务部' AND `deleted_at` IS NULL);
SET @d_MIC_S = (SELECT `id` FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_MIC AND `name` = '业务部' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `department` SET `code` = IF(`code` = '', CONCAT('DP', `id`), `code`), `all_name` = IF('' = '', `all_name`, ''),
  `remark` = '中国制造网询盘接待、报价、PI、订单跟进与客户维护。客户按首次接触归本事业部，以后从其他渠道来的询盘仍归本事业部。', `sort` = 0, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = @d_MIC_S;

-- 采购部
INSERT INTO `department` (`tenant_id`, `pid`, `code`, `name`, `all_name`, `level`, `remark`, `sort`, `status`, `create_by`, `update_by`)
SELECT @tid, @d_MIC, '', '采购部', '', 3, '本事业部订单的询价、下采购单、跟单与发货登记；供应商与价格体系按供应链部门的规范执行。单量少时由供应链部门代管。', 1, 1, 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_MIC AND `name` = '采购部' AND `deleted_at` IS NULL);
SET @d_MIC_P = (SELECT `id` FROM `department` WHERE `tenant_id` = @tid AND `pid` = @d_MIC AND `name` = '采购部' AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);
UPDATE `department` SET `code` = IF(`code` = '', CONCAT('DP', `id`), `code`), `all_name` = IF('' = '', `all_name`, ''),
  `remark` = '本事业部订单的询价、下采购单、跟单与发货登记；供应商与价格体系按供应链部门的规范执行。单量少时由供应链部门代管。', `sort` = 1, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = @d_MIC_P;

-- 事业部标记与开始核算月份
UPDATE `department` SET `is_business_unit` = 1, `bu_start_month` = '2026-10-01', `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = @d_ALI;
UPDATE `department` SET `is_business_unit` = 1, `bu_start_month` = '2026-11-01', `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = @d_MIC;

-- 公司第一次有事业部：已有单据全部归入阿里国际站事业部（不改单据的更新时间，与部门管理里标记的逻辑一致）
UPDATE `customer` SET `business_unit_id` = @d_ALI WHERE `tenant_id` = @tid AND `business_unit_id` IS NULL AND @had_bu = 0;
UPDATE `opportunity` SET `business_unit_id` = @d_ALI WHERE `tenant_id` = @tid AND `business_unit_id` IS NULL AND @had_bu = 0;
UPDATE `customer_inquiry` SET `business_unit_id` = @d_ALI WHERE `tenant_id` = @tid AND `business_unit_id` IS NULL AND @had_bu = 0;
UPDATE `quotation` SET `business_unit_id` = @d_ALI WHERE `tenant_id` = @tid AND `business_unit_id` IS NULL AND @had_bu = 0;
UPDATE `proforma_invoice` SET `business_unit_id` = @d_ALI WHERE `tenant_id` = @tid AND `business_unit_id` IS NULL AND @had_bu = 0;
UPDATE `sales_order` SET `business_unit_id` = @d_ALI WHERE `tenant_id` = @tid AND `business_unit_id` IS NULL AND @had_bu = 0;
UPDATE `payment_receipt` SET `business_unit_id` = @d_ALI WHERE `tenant_id` = @tid AND `business_unit_id` IS NULL AND @had_bu = 0;
UPDATE `tenant_bank_account` SET `business_unit_id` = @d_ALI WHERE `tenant_id` = @tid AND `business_unit_id` IS NULL AND @had_bu = 0;
UPDATE `fin_fx_settlement` SET `business_unit_id` = @d_ALI WHERE `tenant_id` = @tid AND `business_unit_id` IS NULL AND @had_bu = 0;
UPDATE `fin_expense` SET `business_unit_id` = @d_ALI WHERE `tenant_id` = @tid AND `business_unit_id` IS NULL AND @had_bu = 0;
UPDATE `fin_other_income` SET `business_unit_id` = @d_ALI WHERE `tenant_id` = @tid AND `business_unit_id` IS NULL AND @had_bu = 0;

-- ================================================================ 岗位

INSERT INTO `position` (`tenant_id`, `code`, `name`, `sort`, `status`, `remark`, `create_by`, `update_by`)
SELECT @tid, 'GW0001', '总经理', 1, 1, '总经办。公司战略与经营决策，统筹各事业部与共享中台。对应角色：总经理。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `position` WHERE `tenant_id` = @tid AND `code` = 'GW0001' AND `deleted_at` IS NULL);
UPDATE `position` SET `name` = '总经理', `sort` = 1, `remark` = '总经办。公司战略与经营决策，统筹各事业部与共享中台。对应角色：总经理。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = 'GW0001' AND `deleted_at` IS NULL;

INSERT INTO `position` (`tenant_id`, `code`, `name`, `sort`, `status`, `remark`, `create_by`, `update_by`)
SELECT @tid, 'GW0006', '事业部合伙人', 2, 1, '各事业部负责人，对本事业部营收和利润负责，按本事业部利润分红，可兼做业务或采购。对应角色：所在事业部的合伙人角色。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `position` WHERE `tenant_id` = @tid AND `code` = 'GW0006' AND `deleted_at` IS NULL);
UPDATE `position` SET `name` = '事业部合伙人', `sort` = 2, `remark` = '各事业部负责人，对本事业部营收和利润负责，按本事业部利润分红，可兼做业务或采购。对应角色：所在事业部的合伙人角色。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = 'GW0006' AND `deleted_at` IS NULL;

INSERT INTO `position` (`tenant_id`, `code`, `name`, `sort`, `status`, `remark`, `create_by`, `update_by`)
SELECT @tid, 'GW0002', '业务主管', 3, 1, '事业部业务部。带领业务员完成本事业部业绩，分配询盘、审核报价、培训新人（职级 M3–M4），业务员超过 5 人时设立。对应角色：所在事业部的合伙人角色，或按事业部新建只看本业务部的角色。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `position` WHERE `tenant_id` = @tid AND `code` = 'GW0002' AND `deleted_at` IS NULL);
UPDATE `position` SET `name` = '业务主管', `sort` = 3, `remark` = '事业部业务部。带领业务员完成本事业部业绩，分配询盘、审核报价、培训新人（职级 M3–M4），业务员超过 5 人时设立。对应角色：所在事业部的合伙人角色，或按事业部新建只看本业务部的角色。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = 'GW0002' AND `deleted_at` IS NULL;

INSERT INTO `position` (`tenant_id`, `code`, `name`, `sort`, `status`, `remark`, `create_by`, `update_by`)
SELECT @tid, 'GW0003', '业务员', 4, 1, '事业部业务部。询盘接待、报价、PI、订单跟进与客户维护（职级 P1–P4）。对应角色：业务员。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `position` WHERE `tenant_id` = @tid AND `code` = 'GW0003' AND `deleted_at` IS NULL);
UPDATE `position` SET `name` = '业务员', `sort` = 4, `remark` = '事业部业务部。询盘接待、报价、PI、订单跟进与客户维护（职级 P1–P4）。对应角色：业务员。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = 'GW0003' AND `deleted_at` IS NULL;

INSERT INTO `position` (`tenant_id`, `code`, `name`, `sort`, `status`, `remark`, `create_by`, `update_by`)
SELECT @tid, 'GW0017', '销售助理', 5, 1, '事业部业务部。协助业务员整理报价、单据与跟单，新人试用期岗位（职级 P0）。对应角色：业务员。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `position` WHERE `tenant_id` = @tid AND `code` = 'GW0017' AND `deleted_at` IS NULL);
UPDATE `position` SET `name` = '销售助理', `sort` = 5, `remark` = '事业部业务部。协助业务员整理报价、单据与跟单，新人试用期岗位（职级 P0）。对应角色：业务员。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = 'GW0017' AND `deleted_at` IS NULL;

INSERT INTO `position` (`tenant_id`, `code`, `name`, `sort`, `status`, `remark`, `create_by`, `update_by`)
SELECT @tid, 'GW0004', '采购主管', 6, 1, '事业部采购部。分配与审核本事业部的询价和采购订单。对应角色：所在事业部的合伙人角色，或由供应链主管兼任。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `position` WHERE `tenant_id` = @tid AND `code` = 'GW0004' AND `deleted_at` IS NULL);
UPDATE `position` SET `name` = '采购主管', `sort` = 6, `remark` = '事业部采购部。分配与审核本事业部的询价和采购订单。对应角色：所在事业部的合伙人角色，或由供应链主管兼任。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = 'GW0004' AND `deleted_at` IS NULL;

INSERT INTO `position` (`tenant_id`, `code`, `name`, `sort`, `status`, `remark`, `create_by`, `update_by`)
SELECT @tid, 'GW0005', '采购员', 7, 1, '事业部采购部。询价、下采购单、跟单与发货登记。对应角色：采购员。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `position` WHERE `tenant_id` = @tid AND `code` = 'GW0005' AND `deleted_at` IS NULL);
UPDATE `position` SET `name` = '采购员', `sort` = 7, `remark` = '事业部采购部。询价、下采购单、跟单与发货登记。对应角色：采购员。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = 'GW0005' AND `deleted_at` IS NULL;

INSERT INTO `position` (`tenant_id`, `code`, `name`, `sort`, `status`, `remark`, `create_by`, `update_by`)
SELECT @tid, 'GW0018', '兼职采购', 8, 1, '由供应链部门管理。只处理分配给自己的询价任务，下载询价包、导入询价结果。对应角色：兼职采购（系统内置）。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `position` WHERE `tenant_id` = @tid AND `code` = 'GW0018' AND `deleted_at` IS NULL);
UPDATE `position` SET `name` = '兼职采购', `sort` = 8, `remark` = '由供应链部门管理。只处理分配给自己的询价任务，下载询价包、导入询价结果。对应角色：兼职采购（系统内置）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = 'GW0018' AND `deleted_at` IS NULL;

INSERT INTO `position` (`tenant_id`, `code`, `name`, `sort`, `status`, `remark`, `create_by`, `update_by`)
SELECT @tid, 'GW0007', '运营主管', 9, 1, '共享中台运营部门。制定各平台运营策略，审核商品候选，对流量质量和获客成本负责。对应角色：运营主管。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `position` WHERE `tenant_id` = @tid AND `code` = 'GW0007' AND `deleted_at` IS NULL);
UPDATE `position` SET `name` = '运营主管', `sort` = 9, `remark` = '共享中台运营部门。制定各平台运营策略，审核商品候选，对流量质量和获客成本负责。对应角色：运营主管。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = 'GW0007' AND `deleted_at` IS NULL;

INSERT INTO `position` (`tenant_id`, `code`, `name`, `sort`, `status`, `remark`, `create_by`, `update_by`)
SELECT @tid, 'GW0008', '平台运营专员', 10, 1, '运营部门。负责某个平台店铺的日常运营：产品发布、关键词与推广、店铺数据分析。对应角色：运营专员。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `position` WHERE `tenant_id` = @tid AND `code` = 'GW0008' AND `deleted_at` IS NULL);
UPDATE `position` SET `name` = '平台运营专员', `sort` = 10, `remark` = '运营部门。负责某个平台店铺的日常运营：产品发布、关键词与推广、店铺数据分析。对应角色：运营专员。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = 'GW0008' AND `deleted_at` IS NULL;

INSERT INTO `position` (`tenant_id`, `code`, `name`, `sort`, `status`, `remark`, `create_by`, `update_by`)
SELECT @tid, 'GW0009', '内容运营专员', 11, 1, '运营部门。商品资料、营销内容、图片与视频素材，独立站与社媒内容。对应角色：运营专员。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `position` WHERE `tenant_id` = @tid AND `code` = 'GW0009' AND `deleted_at` IS NULL);
UPDATE `position` SET `name` = '内容运营专员', `sort` = 11, `remark` = '运营部门。商品资料、营销内容、图片与视频素材，独立站与社媒内容。对应角色：运营专员。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = 'GW0009' AND `deleted_at` IS NULL;

INSERT INTO `position` (`tenant_id`, `code`, `name`, `sort`, `status`, `remark`, `create_by`, `update_by`)
SELECT @tid, 'GW0010', '供应链主管', 12, 1, '共享中台供应链部门。供应商准入、品类价格体系、采购规范，跨事业部协调询价与兼职采购。对应角色：供应链主管。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `position` WHERE `tenant_id` = @tid AND `code` = 'GW0010' AND `deleted_at` IS NULL);
UPDATE `position` SET `name` = '供应链主管', `sort` = 12, `remark` = '共享中台供应链部门。供应商准入、品类价格体系、采购规范，跨事业部协调询价与兼职采购。对应角色：供应链主管。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = 'GW0010' AND `deleted_at` IS NULL;

INSERT INTO `position` (`tenant_id`, `code`, `name`, `sort`, `status`, `remark`, `create_by`, `update_by`)
SELECT @tid, 'GW0011', '供应链专员', 13, 1, '供应链部门。供应商开发与维护、品类价格数据整理。对应角色：采购员。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `position` WHERE `tenant_id` = @tid AND `code` = 'GW0011' AND `deleted_at` IS NULL);
UPDATE `position` SET `name` = '供应链专员', `sort` = 13, `remark` = '供应链部门。供应商开发与维护、品类价格数据整理。对应角色：采购员。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = 'GW0011' AND `deleted_at` IS NULL;

INSERT INTO `position` (`tenant_id`, `code`, `name`, `sort`, `status`, `remark`, `create_by`, `update_by`)
SELECT @tid, 'GW0012', '仓管专员', 14, 1, '供应链部门。到货验收入库、闲置库存处置、销售出库打包、实拍。对应角色：仓库（系统内置）。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `position` WHERE `tenant_id` = @tid AND `code` = 'GW0012' AND `deleted_at` IS NULL);
UPDATE `position` SET `name` = '仓管专员', `sort` = 14, `remark` = '供应链部门。到货验收入库、闲置库存处置、销售出库打包、实拍。对应角色：仓库（系统内置）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = 'GW0012' AND `deleted_at` IS NULL;

INSERT INTO `position` (`tenant_id`, `code`, `name`, `sort`, `status`, `remark`, `create_by`, `update_by`)
SELECT @tid, 'GW0013', '会计', 15, 1, '综合部门。记账、应收应付、费用与结汇，各事业部利润表与分红测算。对应角色：财务。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `position` WHERE `tenant_id` = @tid AND `code` = 'GW0013' AND `deleted_at` IS NULL);
UPDATE `position` SET `name` = '会计', `sort` = 15, `remark` = '综合部门。记账、应收应付、费用与结汇，各事业部利润表与分红测算。对应角色：财务。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = 'GW0013' AND `deleted_at` IS NULL;

INSERT INTO `position` (`tenant_id`, `code`, `name`, `sort`, `status`, `remark`, `create_by`, `update_by`)
SELECT @tid, 'GW0014', '出纳', 16, 1, '综合部门。登记到账、付款与报销付款，管理收款账户。对应角色：财务。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `position` WHERE `tenant_id` = @tid AND `code` = 'GW0014' AND `deleted_at` IS NULL);
UPDATE `position` SET `name` = '出纳', `sort` = 16, `remark` = '综合部门。登记到账、付款与报销付款，管理收款账户。对应角色：财务。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = 'GW0014' AND `deleted_at` IS NULL;

INSERT INTO `position` (`tenant_id`, `code`, `name`, `sort`, `status`, `remark`, `create_by`, `update_by`)
SELECT @tid, 'GW0015', '人事行政专员', 17, 1, '综合部门。招聘、入职培训、考勤薪酬与行政事务。对应角色：普通员工（系统内置）。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `position` WHERE `tenant_id` = @tid AND `code` = 'GW0015' AND `deleted_at` IS NULL);
UPDATE `position` SET `name` = '人事行政专员', `sort` = 17, `remark` = '综合部门。招聘、入职培训、考勤薪酬与行政事务。对应角色：普通员工（系统内置）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = 'GW0015' AND `deleted_at` IS NULL;

INSERT INTO `position` (`tenant_id`, `code`, `name`, `sort`, `status`, `remark`, `create_by`, `update_by`)
SELECT @tid, 'GW0016', 'IT负责人', 18, 1, 'IT部门。系统建设与运维、账号与权限管理、经营数据报表。对应角色：租户管理员（系统内置）。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `position` WHERE `tenant_id` = @tid AND `code` = 'GW0016' AND `deleted_at` IS NULL);
UPDATE `position` SET `name` = 'IT负责人', `sort` = 18, `remark` = 'IT部门。系统建设与运维、账号与权限管理、经营数据报表。对应角色：租户管理员（系统内置）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = 'GW0016' AND `deleted_at` IS NULL;

-- ================================================================ 角色
-- 新建角色的编码为 T + 租户ID + R + 序号（如 T1005R01），不会与系统自动生成的编码（R + 数字）冲突

-- 总经理：全部数据，90 项菜单权限
INSERT INTO `role` (`tenant_id`, `code`, `name`, `permission_scope`, `workbench_template`, `status`, `is_built_in`, `remark`, `create_by`, `update_by`)
SELECT @tid, CONCAT('T', @tid, 'R', LPAD(1, 2, '0')), '总经理', 1, 'boss', 1, 0, '总经办。全公司的业务、采购、财务与统计，统计页可切换全公司或任一事业部；不含系统管理（由租户管理员负责）。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE `tenant_id` = @tid AND `name` = '总经理');
SET @r_GM = (SELECT `code` FROM `role` WHERE `tenant_id` = @tid AND `name` = '总经理' ORDER BY `id` LIMIT 1);
UPDATE `role` SET `permission_scope` = 1, `workbench_template` = 'boss', `status` = 1, `remark` = '总经办。全公司的业务、采购、财务与统计，统计页可切换全公司或任一事业部；不含系统管理（由租户管理员负责）。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = @r_GM;
DELETE FROM `role_resource` WHERE `role_code` = @r_GM;
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`, `create_by`, `update_by`)
SELECT @r_GM, `id`, `code`, 'sys', 'sys' FROM `resource` WHERE `status` = 1 AND `id` IN (100001, 100004, 100031, 100032, 100033, 100034, 100051, 100053, 100055, 100056, 100061, 100062, 100071, 100073, 100074, 100078, 100079, 100081, 100082, 100083, 100084, 100085, 100087, 100088, 100089, 100096, 100097, 100099, 100100, 100101, 100102, 100103, 100104, 100105, 100106, 100107, 100108, 100109, 100110, 100111, 100112, 100113, 100114, 100115, 110101, 110102, 110103, 110111, 110112, 110113, 110121, 110122, 110123, 110131, 110132, 110133, 110141, 110142, 110143, 110144, 110145, 110146, 110151, 110152, 110153, 110154, 110155, 110161, 110162, 110174, 110179, 110180, 110183, 110184, 110185, 110186, 110187, 110189, 110190, 110191, 110192, 110197, 110199, 110204, 110205, 110206, 110207, 110208, 110209, 110210);
DELETE FROM `role_org` WHERE `role_code` = @r_GM;

-- 财务：全部数据，43 项菜单权限
INSERT INTO `role` (`tenant_id`, `code`, `name`, `permission_scope`, `workbench_template`, `status`, `is_built_in`, `remark`, `create_by`, `update_by`)
SELECT @tid, CONCAT('T', @tid, 'R', LPAD(2, 2, '0')), '财务', 1, 'general', 1, 0, '共享中台综合部门（会计、出纳）。收付款、结汇、费用、退款、其他收入，收款账户与汇率，全公司经营与利润统计。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE `tenant_id` = @tid AND `name` = '财务');
SET @r_FIN = (SELECT `code` FROM `role` WHERE `tenant_id` = @tid AND `name` = '财务' ORDER BY `id` LIMIT 1);
UPDATE `role` SET `permission_scope` = 1, `workbench_template` = 'general', `status` = 1, `remark` = '共享中台综合部门（会计、出纳）。收付款、结汇、费用、退款、其他收入，收款账户与汇率，全公司经营与利润统计。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = @r_FIN;
DELETE FROM `role_resource` WHERE `role_code` = @r_FIN;
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`, `create_by`, `update_by`)
SELECT @r_FIN, `id`, `code`, 'sys', 'sys' FROM `resource` WHERE `status` = 1 AND `id` IN (100001, 100004, 100031, 100032, 100033, 100034, 100061, 100075, 100078, 100079, 100080, 100081, 100082, 100083, 100084, 100085, 100088, 100096, 100098, 100103, 100104, 100105, 100106, 100107, 100109, 100110, 100111, 100112, 100113, 100115, 110177, 110180, 110181, 110183, 110184, 110200, 110204, 110205, 110206, 110207, 110208, 110209, 110210);
DELETE FROM `role_org` WHERE `role_code` = @r_FIN;

-- 运营主管：全部数据，33 项菜单权限
INSERT INTO `role` (`tenant_id`, `code`, `name`, `permission_scope`, `workbench_template`, `status`, `is_built_in`, `remark`, `create_by`, `update_by`)
SELECT @tid, CONCAT('T', @tid, 'R', LPAD(3, 2, '0')), '运营主管', 1, 'ops_lead', 1, 0, '共享中台运营部门负责人。商品档案、商品候选审核、商品资料与营销内容、素材、发品任务，商机与销售统计。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE `tenant_id` = @tid AND `name` = '运营主管');
SET @r_OPSL = (SELECT `code` FROM `role` WHERE `tenant_id` = @tid AND `name` = '运营主管' ORDER BY `id` LIMIT 1);
UPDATE `role` SET `permission_scope` = 1, `workbench_template` = 'ops_lead', `status` = 1, `remark` = '共享中台运营部门负责人。商品档案、商品候选审核、商品资料与营销内容、素材、发品任务，商机与销售统计。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = @r_OPSL;
DELETE FROM `role_resource` WHERE `role_code` = @r_OPSL;
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`, `create_by`, `update_by`)
SELECT @r_OPSL, `id`, `code`, 'sys', 'sys' FROM `resource` WHERE `status` = 1 AND `id` IN (100001, 100004, 100031, 100032, 100033, 100034, 100071, 100099, 100100, 100101, 100102, 100113, 100115, 100116, 100117, 110101, 110102, 110103, 110111, 110112, 110113, 110121, 110122, 110123, 110131, 110132, 110133, 110201, 110202, 110203, 110211, 110212, 110213);
DELETE FROM `role_org` WHERE `role_code` = @r_OPSL;

-- 运营专员：全部数据，25 项菜单权限
INSERT INTO `role` (`tenant_id`, `code`, `name`, `permission_scope`, `workbench_template`, `status`, `is_built_in`, `remark`, `create_by`, `update_by`)
SELECT @tid, CONCAT('T', @tid, 'R', LPAD(4, 2, '0')), '运营专员', 1, 'general', 1, 0, '共享中台运营部门成员。维护商品资料、营销内容与素材，处理发品任务；不能审核商品候选、删除商品。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE `tenant_id` = @tid AND `name` = '运营专员');
SET @r_OPS = (SELECT `code` FROM `role` WHERE `tenant_id` = @tid AND `name` = '运营专员' ORDER BY `id` LIMIT 1);
UPDATE `role` SET `permission_scope` = 1, `workbench_template` = 'general', `status` = 1, `remark` = '共享中台运营部门成员。维护商品资料、营销内容与素材，处理发品任务；不能审核商品候选、删除商品。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = @r_OPS;
DELETE FROM `role_resource` WHERE `role_code` = @r_OPS;
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`, `create_by`, `update_by`)
SELECT @r_OPS, `id`, `code`, 'sys', 'sys' FROM `resource` WHERE `status` = 1 AND `id` IN (100001, 100004, 100031, 100032, 100033, 100034, 100099, 100100, 100101, 100102, 100116, 100117, 110101, 110102, 110111, 110112, 110121, 110122, 110131, 110132, 110202, 110203, 110211, 110212, 110213);
DELETE FROM `role_org` WHERE `role_code` = @r_OPS;

-- 供应链主管：全部数据，52 项菜单权限
INSERT INTO `role` (`tenant_id`, `code`, `name`, `permission_scope`, `workbench_template`, `status`, `is_built_in`, `remark`, `create_by`, `update_by`)
SELECT @tid, CONCAT('T', @tid, 'R', LPAD(5, 2, '0')), '供应链主管', 1, 'purchase_lead', 1, 0, '共享中台供应链部门负责人。全公司的采购需求、采购订单与发货，供应商库，询价分配与兼职回价审核。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE `tenant_id` = @tid AND `name` = '供应链主管');
SET @r_SCL = (SELECT `code` FROM `role` WHERE `tenant_id` = @tid AND `name` = '供应链主管' ORDER BY `id` LIMIT 1);
UPDATE `role` SET `permission_scope` = 1, `workbench_template` = 'purchase_lead', `status` = 1, `remark` = '共享中台供应链部门负责人。全公司的采购需求、采购订单与发货，供应商库，询价分配与兼职回价审核。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = @r_SCL;
DELETE FROM `role_resource` WHERE `role_code` = @r_SCL;
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`, `create_by`, `update_by`)
SELECT @r_SCL, `id`, `code`, 'sys', 'sys' FROM `resource` WHERE `status` = 1 AND `id` IN (100001, 100004, 100031, 100032, 100033, 100034, 100054, 100055, 100056, 100062, 100082, 100083, 100087, 100088, 100089, 100099, 100100, 100101, 100102, 100105, 100108, 100114, 100115, 110101, 110102, 110103, 110111, 110112, 110113, 110121, 110122, 110123, 110131, 110132, 110133, 110151, 110152, 110153, 110154, 110155, 110171, 110172, 110173, 110174, 110175, 110187, 110188, 110189, 110190, 110191, 110192, 110205);
DELETE FROM `role_org` WHERE `role_code` = @r_SCL;

-- 阿里国际站事业部合伙人：自定义（本事业部），82 项菜单权限
INSERT INTO `role` (`tenant_id`, `code`, `name`, `permission_scope`, `workbench_template`, `status`, `is_built_in`, `remark`, `create_by`, `update_by`)
SELECT @tid, CONCAT('T', @tid, 'R', LPAD(6, 2, '0')), '阿里国际站事业部合伙人', 2, 'sales_lead', 1, 0, '阿里国际站事业部负责人。只看本事业部的客户、订单、采购与经营、利润、销售统计；含业务与采购的全部操作。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE `tenant_id` = @tid AND `name` = '阿里国际站事业部合伙人');
SET @r_ALI = (SELECT `code` FROM `role` WHERE `tenant_id` = @tid AND `name` = '阿里国际站事业部合伙人' ORDER BY `id` LIMIT 1);
UPDATE `role` SET `permission_scope` = 2, `workbench_template` = 'sales_lead', `status` = 1, `remark` = '阿里国际站事业部负责人。只看本事业部的客户、订单、采购与经营、利润、销售统计；含业务与采购的全部操作。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = @r_ALI;
DELETE FROM `role_resource` WHERE `role_code` = @r_ALI;
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`, `create_by`, `update_by`)
SELECT @r_ALI, `id`, `code`, 'sys', 'sys' FROM `resource` WHERE `status` = 1 AND `id` IN (100001, 100004, 100031, 100032, 100033, 100034, 100051, 100053, 100054, 100055, 100056, 100061, 100062, 100071, 100073, 100074, 100078, 100079, 100081, 100082, 100083, 100084, 100087, 100088, 100089, 100096, 100097, 100099, 100100, 100101, 100102, 100105, 100108, 100109, 100112, 100113, 100114, 100115, 110101, 110102, 110103, 110111, 110112, 110113, 110121, 110122, 110123, 110131, 110132, 110133, 110141, 110142, 110143, 110144, 110145, 110146, 110151, 110152, 110153, 110154, 110155, 110161, 110162, 110171, 110172, 110173, 110174, 110175, 110179, 110183, 110184, 110185, 110186, 110187, 110188, 110189, 110190, 110191, 110192, 110197, 110199, 110205);
DELETE FROM `role_org` WHERE `role_code` = @r_ALI;
INSERT INTO `role_org` (`role_code`, `org_code`, `create_by`, `update_by`)
SELECT @r_ALI, `code`, 'sys', 'sys' FROM `department` WHERE `id` = @d_ALI;

-- 中国制造网事业部合伙人：自定义（本事业部），82 项菜单权限
INSERT INTO `role` (`tenant_id`, `code`, `name`, `permission_scope`, `workbench_template`, `status`, `is_built_in`, `remark`, `create_by`, `update_by`)
SELECT @tid, CONCAT('T', @tid, 'R', LPAD(7, 2, '0')), '中国制造网事业部合伙人', 2, 'sales_lead', 1, 0, '中国制造网事业部负责人。只看本事业部的客户、订单、采购与经营、利润、销售统计；含业务与采购的全部操作。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE `tenant_id` = @tid AND `name` = '中国制造网事业部合伙人');
SET @r_MIC = (SELECT `code` FROM `role` WHERE `tenant_id` = @tid AND `name` = '中国制造网事业部合伙人' ORDER BY `id` LIMIT 1);
UPDATE `role` SET `permission_scope` = 2, `workbench_template` = 'sales_lead', `status` = 1, `remark` = '中国制造网事业部负责人。只看本事业部的客户、订单、采购与经营、利润、销售统计；含业务与采购的全部操作。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = @r_MIC;
DELETE FROM `role_resource` WHERE `role_code` = @r_MIC;
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`, `create_by`, `update_by`)
SELECT @r_MIC, `id`, `code`, 'sys', 'sys' FROM `resource` WHERE `status` = 1 AND `id` IN (100001, 100004, 100031, 100032, 100033, 100034, 100051, 100053, 100054, 100055, 100056, 100061, 100062, 100071, 100073, 100074, 100078, 100079, 100081, 100082, 100083, 100084, 100087, 100088, 100089, 100096, 100097, 100099, 100100, 100101, 100102, 100105, 100108, 100109, 100112, 100113, 100114, 100115, 110101, 110102, 110103, 110111, 110112, 110113, 110121, 110122, 110123, 110131, 110132, 110133, 110141, 110142, 110143, 110144, 110145, 110146, 110151, 110152, 110153, 110154, 110155, 110161, 110162, 110171, 110172, 110173, 110174, 110175, 110179, 110183, 110184, 110185, 110186, 110187, 110188, 110189, 110190, 110191, 110192, 110197, 110199, 110205);
DELETE FROM `role_org` WHERE `role_code` = @r_MIC;
INSERT INTO `role_org` (`role_code`, `org_code`, `create_by`, `update_by`)
SELECT @r_MIC, `code`, 'sys', 'sys' FROM `department` WHERE `id` = @d_MIC;

-- 业务员：仅本人，51 项菜单权限
INSERT INTO `role` (`tenant_id`, `code`, `name`, `permission_scope`, `workbench_template`, `status`, `is_built_in`, `remark`, `create_by`, `update_by`)
SELECT @tid, CONCAT('T', @tid, 'R', LPAD(8, 2, '0')), '业务员', 3, 'sales', 1, 0, '各事业部业务部（业务员、销售助理）。只看本人负责的客户、商机、询盘、报价、PI 和订单。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE `tenant_id` = @tid AND `name` = '业务员');
SET @r_SALES = (SELECT `code` FROM `role` WHERE `tenant_id` = @tid AND `name` = '业务员' ORDER BY `id` LIMIT 1);
UPDATE `role` SET `permission_scope` = 3, `workbench_template` = 'sales', `status` = 1, `remark` = '各事业部业务部（业务员、销售助理）。只看本人负责的客户、商机、询盘、报价、PI 和订单。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = @r_SALES;
DELETE FROM `role_resource` WHERE `role_code` = @r_SALES;
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`, `create_by`, `update_by`)
SELECT @r_SALES, `id`, `code`, 'sys', 'sys' FROM `resource` WHERE `status` = 1 AND `id` IN (100001, 100004, 100031, 100032, 100033, 100034, 100051, 100053, 100061, 100071, 100073, 100074, 100078, 100079, 100081, 100084, 100096, 100097, 100099, 100100, 100101, 100102, 100113, 100115, 110101, 110102, 110103, 110111, 110112, 110113, 110121, 110122, 110123, 110131, 110132, 110133, 110141, 110142, 110143, 110144, 110145, 110146, 110161, 110162, 110179, 110183, 110184, 110185, 110186, 110197, 110199);
DELETE FROM `role_org` WHERE `role_code` = @r_SALES;

-- 采购员：仅本人，43 项菜单权限
INSERT INTO `role` (`tenant_id`, `code`, `name`, `permission_scope`, `workbench_template`, `status`, `is_built_in`, `remark`, `create_by`, `update_by`)
SELECT @tid, CONCAT('T', @tid, 'R', LPAD(9, 2, '0')), '采购员', 3, 'purchaser', 1, 0, '各事业部采购部、供应链专员。只看分配给本人的询价、采购需求和采购订单。', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE `tenant_id` = @tid AND `name` = '采购员');
SET @r_BUYER = (SELECT `code` FROM `role` WHERE `tenant_id` = @tid AND `name` = '采购员' ORDER BY `id` LIMIT 1);
UPDATE `role` SET `permission_scope` = 3, `workbench_template` = 'purchaser', `status` = 1, `remark` = '各事业部采购部、供应链专员。只看分配给本人的询价、采购需求和采购订单。', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = @tid AND `code` = @r_BUYER;
DELETE FROM `role_resource` WHERE `role_code` = @r_BUYER;
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`, `create_by`, `update_by`)
SELECT @r_BUYER, `id`, `code`, 'sys', 'sys' FROM `resource` WHERE `status` = 1 AND `id` IN (100001, 100004, 100031, 100032, 100033, 100034, 100055, 100056, 100062, 100082, 100083, 100087, 100088, 100089, 100099, 100100, 100101, 100102, 100105, 110101, 110102, 110103, 110111, 110112, 110113, 110121, 110122, 110123, 110131, 110132, 110133, 110151, 110152, 110153, 110154, 110155, 110174, 110187, 110189, 110190, 110191, 110192, 110205);
DELETE FROM `role_org` WHERE `role_code` = @r_BUYER;

COMMIT;

-- ================================================================ 执行结果
SELECT d.`id`, d.`pid`, d.`code`, d.`name`, d.`is_business_unit`, d.`bu_start_month`, LEFT(d.`remark`, 30) AS `remark`
FROM `department` d WHERE d.`tenant_id` = @tid AND d.`deleted_at` IS NULL ORDER BY d.`level`, d.`pid`, d.`sort`, d.`id`;
SELECT `code`, `name`, `sort` FROM `position` WHERE `tenant_id` = @tid AND `deleted_at` IS NULL ORDER BY `sort`;
SELECT r.`code`, r.`name`, r.`permission_scope`, r.`workbench_template`,
  (SELECT COUNT(*) FROM `role_resource` rr WHERE rr.`role_code` = r.`code`) AS `menus`,
  (SELECT GROUP_CONCAT(o.`org_code`) FROM `role_org` o WHERE o.`role_code` = r.`code`) AS `orgs`
FROM `role` r WHERE r.`tenant_id` = @tid ORDER BY r.`id`;
