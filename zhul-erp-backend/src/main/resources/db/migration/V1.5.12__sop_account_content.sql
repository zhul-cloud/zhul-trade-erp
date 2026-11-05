-- 客户跟进第三期（openspec change customer-followup-account）：客户经营 G4–G7 作战卡（平台数据 tenant_id = 0）
-- 内容整理自《Fouwell 客户经营与复购 SOP v1.0》：阶段目标、BAU 清单、Stage Gate、完整示例。
-- G1 客户分层、G2 客户档案、G3 采购规律识别由系统自动完成，结果显示在作战卡顶部，不导入为阶段。
-- 老客户新需求按已定口径走询盘（不建商机），G5 的清单与条件按此改写。客户经营不按停留天数判断卡住，ref_days 为 0。

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 5, 'G4', '需求挖掘', '通过有价值的沟通发现真实新需求', '["回顾上次订单、项目和未解决事项", "选好这次的触达理由", "基于客户业务提出具体问题", "询问未来项目、备件和采购计划", "记录型号、数量、时间等需求信号", "没有需求时记下原因和下一次触达日期"]', '有需求：已建询盘；没有需求：已设合理的下一次触达日期', '记跟进选「客户有新需求」进入 G5，选「聊了新品类」进入 G6', '{"default": "围绕上次的项目问未来 1–2 个月的计划"}', '示例：不要只说 Hi friend, any inquiry? 可以基于上次的 PLC 项目问：Are you planning any maintenance or expansion in the next 1–2 months? I can help check stock early if needed.', 0, 1, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 5 AND s.`stage_code` = 'G4');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 5, 'G5', '复购机会', '快速把复购需求转成正式询盘，进入商机成交', '["为这次需求建新询盘，不塞进旧单据", "复用地址、沟通偏好等历史信息", "重新确认完整型号和数量", "重新确认产品状态和交期要求", "不直接沿用历史价格和库存", "进入商机成交后按报价、PI 推进"]', '新询盘已建立，负责人、下一步明确', '建了询盘后由商机成交跟进，客户经营提醒暂停；询盘结束后回到 G4（战略客户回到 G7）', '{"default": "建询盘，进入商机成交"}', '示例：老客户说「再要 10 台上次同型号」。可以复用地址和沟通偏好，但仍要确认完整型号、数量、交期和产品状态，再建新询盘。', 0, 2, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 5 AND s.`stage_code` = 'G5');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 5, 'G6', '交叉销售', '基于客户真实业务扩大相关品类覆盖', '["从客户设备和项目识别关联品类", "只选 1–2 个最相关的方向", "确认客户是否负责相关采购", "用问题验证真实需求，先验证再推荐", "记录客户现有品牌和供应方式", "暂不相关时记下原因，停止本轮推荐"]', '客户确认新采购方向并已建询盘；或确认暂不相关，回到需求挖掘', '记跟进选「客户有新需求」进入 G5；选「当前没有需求 · 暂不相关」回到 G4', '{"default": "确认客户是否也负责相关品类的采购"}', '示例：长期买 PLC 的客户不一定买所有工控产品。先确认他是否也负责 HMI、驱动备件；确认后再围绕实际品牌和项目展开。', 0, 3, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 5 AND s.`stage_code` = 'G6');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 5, 'G7', '长期合作', '把单次交易关系升级为可持续的合作机制', '["建立季度回顾节奏，填写年度计划", "了解年度项目和采购计划", "扩展采购、工程、管理等多个联系人", "讨论库存、交期和项目支持机制", "定期复盘赢单、丢单和履约问题", "设好下一周期的目标和下一步"]', '已形成稳定的合作节奏和多点关系；有明确的下一周期增长方向', '设为战略客户后进入 G7；每 90 天提醒季度回顾', '{"default": "季度回顾：项目、重点品牌和采购计划"}', '示例：长期客户管理不是「经常聊天」，而是让客户有自动化采购需求时优先想到我们，在品类、供应、交付和项目计划上形成稳定合作。', 0, 4, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 5 AND s.`stage_code` = 'G7');

-- 回退：DELETE FROM `sop_stage` WHERE `board` = 5;
