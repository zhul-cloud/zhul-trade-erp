-- 客户跟进第四期（openspec change customer-followup-develop）：主动开发 D1–D7 作战卡（平台数据 tenant_id = 0）
-- 内容整理自《Fouwell 客户主动开发 SOP v1.0》：阶段目标、BAU 清单、Stage Gate、完整示例；SOP 的「D1 目标市场」由业务员在「为什么值得开发」里写明，不单独成为阶段。
-- D5 的 default_actions 里 d4 / d9 / d16 是触达序列各节点的建议（与 sales.followup.develop.sequence-days 对应）。

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 1, 'D1', '搜客户', '找到值得开发的目标账户，每个账户都知道为什么值得开发', '["记录公司、官网、国家、行业", "写下为什么值得开发：官网、产品线、维修服务等匹配证据", "查找品牌、设备或进口线索", "确认不是已有客户，避免重复开发", "做背调或评潜力星级，判断值不值得开发"]', '账户基本符合目标市场，信息与匹配证据足够进入资格判断', '评了潜力星级或发起背调后自动进入 D2；资格合格且有联系人时直接往后推', '{"default": "做背调，判断值不值得开发"}', '示例：不是「找德国客户」，而是锁定德国机械制造、MRO 企业，并记录官网中的 PLC 维修、Siemens/ABB 服务等证据后才进入资格判断。', 3, 1, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 1 AND s.`stage_code` = 'D1');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 1, 'D2', '资格判断', '把时间投入到真正可能购买工控产品的账户', '["验证公司真实运营：官网、地图、公开资料", "判断是否使用、销售或维护工控产品", "判断与我们品牌、品类的匹配度", "寻找采购可能的证据", "事实和推测分开记录", "无效账户及时停止"]', 'Qualified Account：潜力 ≥3 星或背调建议重点 / 正常跟进', '背调完成或潜力评到 3 星及以上自动进入 D3；资格不足时停在 D2，由你决定停止或培育', '{"default": "资格不足，建议停止或培育"}', '示例：只做住宅电气安装的公司即使名字有 Automation 也不应高优先级；有 PLC 维修、备件和工厂服务页面的公司更符合。', 3, 2, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 1 AND s.`stage_code` = 'D2');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 1, 'D3', '找联系人', '找到最可能参与自动化采购的人和合适触达渠道', '["优先寻找采购、工程、维护或老板", "记录姓名、职位和公开商务联系方式", "记录联系方式来源", "建立主要和备选联系人", "选择首选触达渠道"]', '至少找到 1 个有邮箱、WhatsApp 或电话的高相关联系人', '在客户档案加了有联系方式的联系人后自动进入 D4', '{"default": "找采购、维修或技术负责人"}', '示例：目标是 MRO 公司时优先找 Purchasing Manager、Maintenance Manager 或 Owner，而不是因为 Sales Manager 邮箱容易找到就联系销售岗位。', 5, 3, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 1 AND s.`stage_code` = 'D3');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 1, 'D4', '首次触达', '用高度相关的短信息获得注意和回复', '["根据客户背景写个性化开场", "简短说明自己和公司", "提到与客户相关的品牌或场景", "给出 1 个核心价值点", "提一个容易回答的问题，确认对方是否负责相关采购", "控制消息长度，不发大段公司介绍"]', '首封已发出，序列开始', '记跟进选「已发出触达」后进入 D5，按第 4、9、16 天提醒再跟', '{"default": "发首封开发信"}', '写法：身份 + 价值 + 低摩擦问题。
Hi Mark, I saw your team supports Siemens/ABB automation maintenance. I''m Kevin from Fouwell in China; we source original PLC/HMI/VFD and hard-to-find spares. Are you the right person for automation spare-parts purchasing?', 3, 4, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 1 AND s.`stage_code` = 'D4');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 1, 'D5', '多轮跟进', '通过不同价值角度持续获得互动，而不是重复催回复', '["按序列执行跟进，每次换一个价值角度", "提供库存、案例、品牌、交期等新信息", "合理尝试其他沟通渠道", "确认是否找对联系人，请求转介绍给正确角色", "明确拒绝立即停止", "序列发完仍没回复：转培育或停止开发"]', '获得回复、转介绍或有效新信息', '记跟进选「客户回复了」进入 D6；序列发完仍没回复时建议转培育或停止开发', '{"default": "换一个新价值点再跟一次", "d4": "第 4 天：分享紧缺或停产备件的供应能力（换新价值）", "d9": "第 9 天：发现货清单或替代型号对比（换新价值）", "d16": "第 16 天：发收尾邮件，问是否应该联系其他负责人"}', '示例：第二次不要只问 Did you see my email? 可以分享紧缺、停产 Siemens 备件的供应能力，验证客户是否负责 legacy PLC spares。', 16, 5, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 1 AND s.`stage_code` = 'D5');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 1, 'D6', '建立连接', '从陌生联系人变成理解其业务和采购范围的有效关系', '["确认联系人真实角色", "了解常购品牌、品类", "了解备件、项目、维护场景", "了解主要采购痛点和当前供应方式", "提供一次有用的专业价值", "暂无需求时不强行报价"]', '已确认潜在采购方向，或客户出现可验证的采购信号', '记跟进选「出现采购信号」进入 D7', '{"default": "了解常购品牌和检修周期，提供一次有用的专业价值"}', '示例：客户回复 Yes, I handle the spares. 接着了解：Which brands do you mainly maintain, and do you have a regular shutdown schedule? 不要一次性审问大量问题。', 14, 6, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 1 AND s.`stage_code` = 'D6');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 1, 'D7', '需求发现', '把主动开发中的真实需求及时转换成正式商机', '["判断是否出现真实采购需求", "获取型号、品类、数量、时间等关键线索", "记录客户原始需求", "转为商机，来源为主动开发", "转商机后按商机成交 SOP 推进，不在开发阶段独立报价"]', '新商机已建立，负责人、下一步明确', '点「转为商机」建商机（S1 新商机），开发记录结束', '{"default": "转商机"}', '示例：客户提出 6pcs Siemens 备件需求，下月采购 → 记录型号、数量和时间，点「转为商机」进入商机成交。不要因为客户说 Nice to know 就建商机。', 3, 7, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 1 AND s.`stage_code` = 'D7');

-- 回退：
-- DELETE FROM `sop_stage` WHERE `tenant_id` = 0 AND `board` = 1;
-- DELETE FROM `flyway_schema_history` WHERE `version` = '1.5.14';
