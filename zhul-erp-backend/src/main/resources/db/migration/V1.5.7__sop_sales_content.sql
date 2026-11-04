-- 客户跟进（openspec change customer-followup）：商机成交 SOP 作战卡与卡点字典（平台数据 tenant_id = 0）
-- 内容整理自《Fouwell 商机成交 SOP v1.0》：阶段目标、BAU 清单、Stage Gate、完整示例、字典 Blocker。
-- 租户以后可以按 (tenant_id, board, stage_code) / (tenant_id, code) 插入自己的版本覆盖平台内容。

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 2, 'S1', '新商机', '判断客户是否值得跟进，用第一次高质量回复建立专业印象，争取客户有效回复。', '["看客户和公司基本信息，判断询盘是否真实", "提取品牌、型号、数量，找出缺的关键信息", "人工首次回复，不发群发模板", "提 1–2 个容易回复的问题", "写下一步和日期"]', '客户有效回复，并带来推进商机的新信息', '登记商机后在 S1；客户回复后手动推进到 S2', '{"default": "首次回复客户，确认型号版本、产品状态和交货国家"}', '客户：Need 6ES7515-2AM02-0AB0, 10pcs, please quote.
合格首回：Hi Ahmed, thanks for your inquiry. We can check the Siemens 6ES7515-2AM02-0AB0 for 10pcs. To make sure I quote the correct supply, may I confirm whether you require brand-new original Siemens and your delivery country/city? I''ll check stock and lead time and update you today.', 1, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 2 AND s.`stage_code` = 'S1');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 2, 'S2', '需求确认', '搞清客户到底要什么、什么时候买，以及哪些因素影响成交。', '["品牌、完整型号、数量、产品状态", "目的国家、交期要求、采购时间", "必要时补问技术参数、客户角色、项目场景", "不重复问客户已经给过的信息"]', '需求达到可询价程度，且值得投入报价资源', '创建询盘后自动推进到 S3', '{"default": "补齐型号、数量、状态、交期，确认采购时间"}', '客户回复 New & Original, delivery to Dubai, need within 2 weeks.
已拿到影响报价的关键条件，可以补问：Is this for an urgent replacement or a planned project? 交期关键时立即同步采购确认真实库存。', 2, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 2 AND s.`stage_code` = 'S2');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 2, 'S3', '有效商机', '把客户需求转换成可以安全、准确、有利润报价的供应方案。', '["给采购准确的询价信息", "核实库存、交期、产品状态、质保", "核算成本、目标售价和最低可接受价", "识别停产、替代、版本等供应风险", "报价复核"]', '产品、数量、成本、交期、状态、售价、运输、风险全部明确，报价已复核', '生成报价单后自动推进到 S4', '{"default": "跟进采购报价，核实库存和交期后出报价"}', '采购确认 10pcs 现货，成本 USD 690/pc，DHL 4–6 天，New & Original。核算平台费、运费与目标毛利后报 USD 850/pc，有效期 7 天，保留谈判底线。', 3, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 2 AND s.`stage_code` = 'S3');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 2, 'S4', '已报价', '不是把价格发出去，而是让客户理解报价的价值并拿到有效反馈。', '["确认客户收到报价", "报价写清品牌、型号、数量、单价、总价", "写清产品状态、交期、质保、运输、付款、有效期", "根据客户关注点表达价值，不只扔一个价格", "主动问客户的反馈"]', '拿到有效报价反馈：价格、交期、技术、付款、信任、预算、竞争对手、审批、项目延期等', '记跟进勾选「客户给了反馈」自动推进到 S5', '{"d1": "确认客户收到报价，问价格、交期或产品状态哪一点最需要调整", "d3": "针对客户关注点补充价值：货源、交期、质保或实拍", "d7": "问客户的目标价或决策时间，给出明确的下一步", "d14": "发收尾邮件，说明报价有效期，问是否暂缓采购"}', '报价：10pcs × USD 850, New & Original, stock available, DHL 4–6 days, 12-month warranty, quote valid 7 days.
跟进：Hi Ahmed, just checking whether the price, delivery time or product condition is the main point you''d like us to improve. If you have a target level, I can check what is workable for 10pcs.', 4, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 2 AND s.`stage_code` = 'S4');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 2, 'S5', '报价反馈', '找到真正阻止客户购买的原因，而不是机械催单。', '["记录客户真实反馈，选出主要卡点", "针对卡点确定解决动作", "价格：拿到目标价、判断差距", "交期：问客户要求日期，找更快方案", "信任：实拍、铭牌、测试、交易保障"]', '客户开始讨论目标价、折扣、最终数量、付款、交期、运输、质保等实质成交条件', '记跟进勾选「客户在谈条件」自动推进到 S6', '{"default": "针对卡点给出方案，拿到客户的目标价或决策时间"}', '客户：Your price is high, another supplier offered USD 760.
错误：OK, I can do 750.
正确：Thanks for sharing. Is USD 760 for the same New & Original condition and similar delivery? If 10pcs is confirmed, what target level would allow you to proceed? I''ll recheck our best workable option.', 5, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 2 AND s.`stage_code` = 'S5');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 2, 'S6', '商务谈判', '找出双方成交条件的差距，设计双方都能接受的成交方案。', '["拿到目标价、最终数量、要求交期、付款和运输条件", "清楚内部成本、最低售价和最大可让折扣", "降价尽量交换条件：数量、付款、交期联动", "不承诺做不到的条件，主动推动明确决定"]', '客户出现明确购买信号：要 PI、要创建信保订单、要付款信息、给了 PO 或确认订单', '发送 PI 后自动推进到 S7', '{"default": "给出成交方案（价格与数量、付款、交期联动），请客户确认下单"}', '客户 Target USD 780，确认 10pcs，内部底线 USD 790。
方案：USD 790/pc for 10pcs, payment this week, DHL freight as quoted.
客户回复：OK, please send PI. → 进入 S7。', 6, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 2 AND s.`stage_code` = 'S6');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 2, 'S7', '成交推进', '把客户的「我要买」变成真正付款。', '["最终确认公司、联系人、型号、数量、单价、总价", "交期、运输、付款、质保，以及替代或版本等风险书面确认", "PI 第二人复核后发送，附付款指引", "问清客户预计哪天付款", "客户说会付款后仍持续跟进，主动解决付款卡点"]', '实际约定款项到账', '收款到账后系统自动赢单', '{"pi": "按客户说的付款日期确认付款进度，没付的问清审批、银行或付款卡点"}', '客户要求 PI。再次确认 6ES7515-2AM02-0AB0 ×10pcs、USD 790/pc、DHL、New & Original、交期，第二人复核 PI 后发送。
客户说 Friday pay → 记录预计付款日为周五；周五没到账就查审批、银行或付款卡点。实际到账后系统自动赢单。', 7, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 2 AND s.`stage_code` = 'S7');

INSERT INTO `sop_blocker` (`tenant_id`,`code`,`name`,`lost_reason`,`playbook`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 'PRICE', '价格', 11, '["先问清对方报价的条件：是不是同样全新原装、同样交期", "拿到客户的目标价，判断差距", "不裸降价：降价换数量、付款时间或交期", "用货源、质保、实拍说明价格差在哪里"]', 1, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_blocker` b WHERE b.`tenant_id` = 0 AND b.`code` = 'PRICE');

INSERT INTO `sop_blocker` (`tenant_id`,`code`,`name`,`lost_reason`,`playbook`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 'DELIVERY', '交期', 12, '["问清客户要求的最晚到货日期", "和采购确认能否加急或换货源", "给出分批发货或替代型号方案"]', 2, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_blocker` b WHERE b.`tenant_id` = 0 AND b.`code` = 'DELIVERY');

INSERT INTO `sop_blocker` (`tenant_id`,`code`,`name`,`lost_reason`,`playbook`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 'TECHNICAL', '技术', 16, '["问清具体的技术疑问", "给出规格书、兼容性说明或替代型号对比", "必要时请技术同事一起回复"]', 3, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_blocker` b WHERE b.`tenant_id` = 0 AND b.`code` = 'TECHNICAL');

INSERT INTO `sop_blocker` (`tenant_id`,`code`,`name`,`lost_reason`,`playbook`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 'PAYMENT', '付款', 17, '["确认客户期望的付款方式", "说明可选方式：T/T、信保订单、定金加尾款", "付款审批卡住时，问清卡在哪一步、谁在批"]', 4, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_blocker` b WHERE b.`tenant_id` = 0 AND b.`code` = 'PAYMENT');

INSERT INTO `sop_blocker` (`tenant_id`,`code`,`name`,`lost_reason`,`playbook`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 'TRUST', '信任', 18, '["发实拍、铭牌、测试视频", "说明交易保障：阿里信保、质保条款", "提供过往同类型号的成交案例"]', 5, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_blocker` b WHERE b.`tenant_id` = 0 AND b.`code` = 'TRUST');

INSERT INTO `sop_blocker` (`tenant_id`,`code`,`name`,`lost_reason`,`playbook`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 'BUDGET', '预算', 20, '["问清预算范围和预算周期", "给出性价比更高的替代方案或分批采购", "预算要到下个周期时，约好下次联系时间"]', 6, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_blocker` b WHERE b.`tenant_id` = 0 AND b.`code` = 'BUDGET');

INSERT INTO `sop_blocker` (`tenant_id`,`code`,`name`,`lost_reason`,`playbook`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 'COMPETITOR', '竞争对手', 15, '["问清竞争对手的报价条件（全新还是翻新、交期、质保）", "突出我们的差异：货源、交期、质保", "不和翻新件比价，讲清风险"]', 7, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_blocker` b WHERE b.`tenant_id` = 0 AND b.`code` = 'COMPETITOR');

INSERT INTO `sop_blocker` (`tenant_id`,`code`,`name`,`lost_reason`,`playbook`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 'APPROVAL', '内部审批', 21, '["问清审批流程和预计时间", "主动提供审批需要的资料：报价单、公司资料、资质", "约好审批后的确认时间"]', 8, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_blocker` b WHERE b.`tenant_id` = 0 AND b.`code` = 'APPROVAL');

INSERT INTO `sop_blocker` (`tenant_id`,`code`,`name`,`lost_reason`,`playbook`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 'PROJECT_DELAY', '项目延期', 22, '["问清项目新的时间点", "保留报价方案，约好重新确认的时间", "值得的转入培育，到期再激活"]', 9, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_blocker` b WHERE b.`tenant_id` = 0 AND b.`code` = 'PROJECT_DELAY');

INSERT INTO `sop_blocker` (`tenant_id`,`code`,`name`,`lost_reason`,`playbook`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 'NO_RESPONSE', '无回复', 23, '["换联系方式：WhatsApp、邮件、电话", "每次跟进带新信息或新问题，不只问 Any update?", "连续 3 次没回复，发收尾邮件并转入培育"]', 10, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_blocker` b WHERE b.`tenant_id` = 0 AND b.`code` = 'NO_RESPONSE');

-- 回退：
-- DELETE FROM `sop_blocker` WHERE `tenant_id` = 0;
-- DELETE FROM `sop_stage` WHERE `tenant_id` = 0 AND `board` = 2;
