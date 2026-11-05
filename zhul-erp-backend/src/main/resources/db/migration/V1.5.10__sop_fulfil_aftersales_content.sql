-- 客户跟进第二期（openspec change customer-followup-fulfillment）：订单履约 F1–F6、售后评价 A1–A6 作战卡与履约 / 售后卡点（平台数据 tenant_id = 0）
-- 内容整理自《Fouwell 订单履约 SOP v1.0》《Fouwell 售后与评价 SOP v1.0》：阶段目标、BAU 清单、Stage Gate、完整示例。
-- SOP 的 F7 客户签收并入 A1；A7 案例沉淀放在售后单结案时选填，不导入。A3/A4 名称按售后单状态改为「问题处理」「待客户确认」。

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 3, 'F1', '订单确认', '把销售承诺完整、无歧义地转换成履约要求', '["确认实际到账金额 / 币种 / 付款主体", "核对最终品牌 / 完整型号 / 数量", "核对最终单价 / 总额 / 交期", "核对运输 / 质保 / 收货信息", "汇总客户特殊要求与承诺", "建立唯一履约订单记录"]', '履约订单资料完整且复核通过；可以安全向供应商下采购单', '系统按采购单、入库、出库、出运自动推进，不用手动改阶段', '{"created": "确认订单要点，告诉客户预计交期", "default": "等采购下单"}', '示例：客户支付 Siemens 6ES7515-2AM02-0AB0 ×10。业务员先核对全新原装、DHL、交期、质保、地址及铭牌照片要求，并完成第二人复核；不是到账后直接把型号发给采购。', 2, 1, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 3 AND s.`stage_code` = 'F1');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 3, 'F2', '采购下单', '准确锁定货源、采购条件和可兑现交期', '["选择合适供应商", "下单前重新确认真实库存", "确认采购成本与币种", "确认产品状态 / 质保", "确认可交日期", "采购单写明完整型号 / 数量 / 特殊要求"]', '采购订单已正式下达并获供应商确认；预计到仓日期与风险已明确', '型号全部下到已下单的采购单后自动进入 F3 供应跟进', '{"ordered": "拿到供应商交期后告诉客户", "default": "等采购下单"}', '示例：供应商原报价有10台现货，下单前复核发现只剩6台。正确做法是先找第二货源/分批方案并确认新交期，而不是先买6台再告诉客户。', 3, 2, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 3 AND s.`stage_code` = 'F2');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 3, 'F3', '供应跟进', '持续掌握真实供应进度，提前暴露延期', '["记录供应商承诺交期", "建立关键检查日期", "到节点前主动确认进度", "更新预计到仓日期", "评估是否影响客户承诺"]', '货物按计划到仓或已有批准的异常方案；最新ETA和下一动作明确', '全部合格入库后自动进入 F4 到货验收', '{"ordered": "拿到供应商交期后告诉客户", "exception": "告诉客户原因、新日期和我们在做什么", "default": "等供应商发货"}', '示例：供应商9月22日告知原9月25日到仓将延至9月30日。当天评估客户影响、寻找更快方案并更新ETA；不能等客户催交期才解释。', 7, 3, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 3 AND s.`stage_code` = 'F3');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 3, 'F4', '到货验收', '确保发给客户的是正确、合格且有证据的产品', '["核对品牌", "核对完整型号 / 后缀 / 版本", "核对数量", "核对产品状态", "检查铭牌 / 序列号", "检查外观 / 配件 / 包装"]', '验收通过，或异常已批准处理', '建了销售出库单后自动进入 F5 包装发货', '{"shot": "把实拍照片发客户确认后再发货", "exception": "告诉客户原因、新日期和我们在做什么", "default": "等仓库验收和出库"}', '示例：到仓产品主体型号一致但后缀不同。即使功能看似相同，也必须隔离并核实差异，必要时取得客户书面确认后才能放行。', 3, 4, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 3 AND s.`stage_code` = 'F4');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 3, 'F5', '包装发货', '安全、准确地把正确货物交给正确承运商', '["最终确认收货人 / 电话 / 地址 / 邮编", "确认物流方式", "按产品风险和客户要求包装", "核对外箱标签 / PO号等要求", "准备发票 / 装箱单 / 申报资料", "发货前拍摄包装和标签照片"]', '货物已交承运商且Tracking有效', '出运单登记出运后自动进入 F6 物流跟踪', '{"shot": "把实拍照片发客户确认后再发货", "exception": "告诉客户原因、新日期和我们在做什么", "default": "等打包交货代"}', '示例：发货前再核对一次收货人电话和邮编，拍下外箱标签和包装照片；拿到运单号当天把运单号和照片一起发给客户，并说明预计送达日。', 3, 5, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 3 AND s.`stage_code` = 'F5');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 3, 'F6', '物流跟踪', '主动掌握运输节点和异常，不等客户来问', '["检查承运商已揽收", "跟踪离境 / 到港 / 清关 / 派送节点", "影响到达时间时主动更新客户", "记录物流异常与处理", "保存签收状态 / 证明", "设置客户签收确认动作"]', '已出运，预计送达日已明确；到了预计送达日安排向客户确认真实收货', '到了预计送达日 → 转入「售后评价 · 签收确认」', '{"shipped": "把运单号和发货照片发给客户", "exception": "告诉客户原因、新日期和我们在做什么", "default": "在途，等预计送达日"}', '示例：DHL显示清关异常。当天确认是缺文件、税费还是收件人问题，并明确告诉客户需要配合什么，持续跟踪到放行。', 10, 6, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 3 AND s.`stage_code` = 'F6');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 4, 'A1', '签收确认', '确认客户真实收到并识别表面交付异常', '["主动确认客户实际收到", "确认数量是否正确", "确认包装 / 外观是否正常", "记录实际签收日期", "记录客户初步反馈", "设置使用确认日期"]', '客户确认实际收到；无表面异常或异常已进入Case', '记跟进选「已收到，没问题」进入 A2；选「有问题」建售后单进入 A3', '{"default": "问客户是否收到，数量外观对不对"}', '示例：物流显示周一签收。周二主动确认客户是否实际收到、数量和外箱是否正常；若正常则设置合适的使用确认日期，而不是立即要求五星评价。', 3, 7, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 4 AND s.`stage_code` = 'A1');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 4, 'A2', '使用确认', '确认产品是否正常使用，并主动提供必要支持', '["根据产品 / 项目选择合适触达时间", "询问实际安装 / 使用情况", "了解项目 / 调试进度", "记录客户使用反馈"]', '客户确认正常使用，或问题已明确进入A3；新采购需求已单独创建商机', '记跟进选「正常使用」进入 A5；选「有问题」建售后单进入 A3', '{"default": "问装机是否顺利，有没有参数问题"}', '示例：客户说设备已安装但工程师下周才调试。记录下周后的确认日期；若客户同时说还需要4台HMI，则立即创建新商机，不把复购需求留在售后记录里。', 10, 8, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 4 AND s.`stage_code` = 'A2');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 4, 'A3', '问题处理', '把客户描述转化成可诊断的事实、证据和问题分类', '["明确问题现象和影响", "确认完整型号 / 后缀 / 版本", "收集铭牌 / 序列号", "判断问题类别", "判断是否影响生产 / 项目", "指定内部确认负责人"]', '问题事实与证据基本完整；可以进入解决方案设计', '按售后单自动同步：改为「待客户确认」进入 A4，结案进入 A5', '{"default": "按售后单的下一步处理"}', '示例：客户说“HMI不能用”。先确认完整型号、Loader界面、旧屏状态、程序来源和现场照片，把“不能用”变成可诊断问题，再交技术确认。', 7, 9, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 4 AND s.`stage_code` = 'A3');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 4, 'A4', '待客户确认', '形成可执行方案并持续推进到客户确认解决', '["完成内部技术 / 采购 / 物流确认", "明确根因或最可能原因", "形成解决方案A", "明确费用 / 责任 / 时间", "向客户清楚解释方案", "指定负责人和完成时间"]', '客户确认问题已解决或接受最终方案；根因 / 费用 / 经验已记录', '售后单结案后自动进入 A5 满意度确认', '{"default": "按售后单的下一步处理"}', '示例：确认新HMI需要导入项目后，给客户明确导入步骤和所需文件，并跟到客户实际验证正常；不能只发一个说明后就关闭Case。', 5, 10, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 4 AND s.`stage_code` = 'A4');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 4, 'A5', '满意度确认', '确认客户对产品、交付和问题处理的真实体验', '["确认最终使用结果", "询问客户整体体验", "获取可执行改进建议", "记录交期 / 包装 / 沟通反馈", "记录技术支持反馈", "识别明确满意信号"]', '当前没有未解决售后问题；客户有明确满意信号或改进项已闭环', '选「满意」并邀请评价进入 A6；选「一般 / 不满意」填原因后售后结束', '{"default": "问整体体验和下次可以改进的地方"}', '示例：客户明确说 Everything works well. 这是可进入评价邀请的信号；若产品正常但包装破损，则先处理包装反馈。', 5, 11, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 4 AND s.`stage_code` = 'A5');

INSERT INTO `sop_stage` (`tenant_id`,`board`,`stage_code`,`name`,`goal`,`checklist`,`gate_text`,`auto_hint`,`default_actions`,`example_dialog`,`ref_days`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 4, 'A6', '邀请评价', '在客户明确满意时，自然邀请真实评价', '["判断当前是否适合邀请评价", "选择合适平台 / 入口", "发出自然、具体的评价邀请", "明确真实体验最重要", "提供必要评价入口", "记录评价状态"]', '评价已完成，或已完成一次合理邀请；评价状态已记录', '记为已评价或婉拒，或提醒两次没评，售后结束', '{"default": "看客户有没有留评价，没有就轻提醒一次"}', '示例：客户说 Works well now, thanks for the quick replacement. 可以回复：Glad to hear it works well. If you have a minute, a short review on our Alibaba store about your real experience would mean a lot to our team. Here is the link. 不送礼、不要求五星，客户婉拒就结束。', 14, 12, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_stage` s WHERE s.`tenant_id` = 0 AND s.`board` = 4 AND s.`stage_code` = 'A6');

INSERT INTO `sop_blocker` (`tenant_id`,`board`,`code`,`name`,`lost_reason`,`playbook`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 3, 'SUPPLY_DELAY', '采购延期', 0, '["当天问清供应商新的发货日期和原因", "评估是否影响对客户承诺的交期，必要时找第二货源或分批发", "主动告诉客户新日期和我们在做什么，不等客户来问"]', 100, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_blocker` s WHERE s.`tenant_id` = 0 AND s.`code` = 'SUPPLY_DELAY');

INSERT INTO `sop_blocker` (`tenant_id`,`board`,`code`,`name`,`lost_reason`,`playbook`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 3, 'DISCREPANCY', '到货差异', 0, '["先隔离差异货物，拍照留证据", "型号后缀或版本不同时，取得客户书面确认后才放行", "少发、不良等补发时，告诉客户新的发货日期"]', 101, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_blocker` s WHERE s.`tenant_id` = 0 AND s.`code` = 'DISCREPANCY');

INSERT INTO `sop_blocker` (`tenant_id`,`board`,`code`,`name`,`lost_reason`,`playbook`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 3, 'CUSTOMS', '清关延误', 0, '["先查清原因：缺文件、税费还是收件人问题", "明确告诉客户需要配合什么（税号、授权书、付税）", "持续跟到放行，影响到达时间时更新预计送达"]', 102, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_blocker` s WHERE s.`tenant_id` = 0 AND s.`code` = 'CUSTOMS');

INSERT INTO `sop_blocker` (`tenant_id`,`board`,`code`,`name`,`lost_reason`,`playbook`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 3, 'LOGISTICS', '物流异常', 0, '["出库撤回或运单异常时先确认货物现在在哪里", "告诉客户新的发货或送达日期", "客户说还没收到时，在记跟进里填新的预计送达日"]', 103, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_blocker` s WHERE s.`tenant_id` = 0 AND s.`code` = 'LOGISTICS');

INSERT INTO `sop_blocker` (`tenant_id`,`board`,`code`,`name`,`lost_reason`,`playbook`,`sort_order`,`status`,`create_by`,`update_by`)
SELECT 0, 4, 'REVIEW_INVITE', '怎么邀请不招人烦', 0, '["借客户自己的话：「很高兴你说 very satisfied」", "一句话说明评价对我们的意义", "不送礼、不要求好评，客户婉拒就结束"]', 104, 1, 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sop_blocker` s WHERE s.`tenant_id` = 0 AND s.`code` = 'REVIEW_INVITE');

-- 回退：DELETE FROM `sop_stage` WHERE `tenant_id` = 0 AND `board` IN (3, 4); DELETE FROM `sop_blocker` WHERE `tenant_id` = 0 AND `board` IN (3, 4);
