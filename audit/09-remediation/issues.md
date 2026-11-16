# 问题状态台账

本文件是问题当前状态的唯一台账；[总入口](../README.md)保留审查基线与原发现索引，[整改流程](README.md)定义状态和证据门槛，[任务模板](tasks/README.md)承载具体过程与结果。

2026-11-01 初始化：以下为原审查基线的全部永久 ID，初始优先级沿用审查结论，尚未认领；本次建账未实施或重新验证产品修复。后续按最新源码、任务记录及证据逐行更新，不用基线统计推断当前进度。

## 业务

| ID / 原专题 | 简短标题 | 优先级 | 状态 | 负责人 | 任务记录 | 最后更新 |
| --- | --- | --- | --- | --- | --- | --- |
| [BUS-001](../01-business/01-客户与商机.md) | 已有手动销售订单的客户仍可删除 | P2 | 已关闭 | Claude | [2026-11-11-BUS-001](tasks/2026-11-11-BUS-001-business-batch-h.md) | 2026-11-14 |
| [BUS-002](../01-business/02-销售与财务.md) | 按订单读取退款绕过租户与订单数据范围 | P1 | 已关闭 | Claude | [2026-11-03-SEC-011](tasks/2026-11-03-SEC-011-tenant-isolation.md) | 2026-11-04 |
| [BUS-003](../01-business/02-销售与财务.md) | PI 取消重转订单后已退款不占用新订单额度 | P1 | 已关闭 | Claude | [2026-11-04-BUS-003](tasks/2026-11-04-BUS-003-funds-batch-d.md) | 2026-11-14 |
| [BUS-004](../01-business/02-销售与财务.md) | 已生成应付的采购订单换币后，应付保留旧币种 | P1 | 已关闭 | Claude | [2026-11-04-BUS-003](tasks/2026-11-04-BUS-003-funds-batch-d.md) | 2026-11-14 |
| [BUS-005](../01-business/03-采购仓储与物流.md) | 订单重转只接回采购行，已发/入库记录仍属于旧订单行 | P1 | 已关闭 | Claude | [2026-11-04-BUS-005](tasks/2026-11-04-BUS-005-fulfillment-batch-e.md) | 2026-11-14 |
| [BUS-006](../01-business/03-采购仓储与物流.md) | 编辑发货通知把旧数量释放两次，允许超量出库 | P1 | 已关闭 | Claude | [2026-11-04-BUS-005](tasks/2026-11-04-BUS-005-fulfillment-batch-e.md) | 2026-11-14 |
| [BUS-007](../01-business/03-采购仓储与物流.md) | 并发新建货代账单可重复纳入同一出运单并生成两笔应付 | P1 | 已关闭 | Claude | [2026-11-04-BUS-005](tasks/2026-11-04-BUS-005-fulfillment-batch-e.md) | 2026-11-14 |
| [BUS-008](../01-business/02-销售与财务.md) | 作废部分结汇后恢复了旧已结汇额，分摊与到账余额不一致 | P1 | 已关闭 | Claude | [2026-11-04-BUS-003](tasks/2026-11-04-BUS-003-funds-batch-d.md) | 2026-11-14 |
| [BUS-009](../01-business/02-销售与财务.md) | 欠款已抵扣另一应付后，作废原付款仍保留目标“已付” | P1 | 已关闭 | Claude | [2026-11-04-BUS-003](tasks/2026-11-04-BUS-003-funds-batch-d.md) | 2026-11-14 |
| [BUS-010](../01-business/03-采购仓储与物流.md) | 已有打包出库时仍允许冲销原采购入库 | P1 | 已关闭 | Claude | [2026-11-04-BUS-005](tasks/2026-11-04-BUS-005-fulfillment-batch-e.md) | 2026-11-14 |
| [BUS-011](../01-business/04-业务边界与接口契约补查.md) | 报销提交未与费用来源互斥，可将刚作废的费用纳入待付款报销单 | P2 | 已关闭 | Claude | [2026-11-04-BUS-003](tasks/2026-11-04-BUS-003-funds-batch-d.md) | 2026-11-14 |
| [BUS-012](../01-business/04-业务边界与接口契约补查.md) | 其他收入编辑与作废交错，旧编辑实体可把已作废记录恢复有效 | P2 | 已关闭 | Claude | [2026-11-04-BUS-003](tasks/2026-11-04-BUS-003-funds-batch-d.md) | 2026-11-14 |
| [BUS-013](../01-business/05-全量业务代码审查.md) | 商机基本信息编辑可并发覆盖阶段及结束信息 | P2 | 已关闭 | Claude | [2026-11-11-BUS-001](tasks/2026-11-11-BUS-001-business-batch-h.md) | 2026-11-14 |
| [BUS-014](../01-business/05-全量业务代码审查.md) | 普通询价分配缺少原子状态检查，同一待分配任务可被同时分给两人 | P2 | 已关闭 | Claude | [2026-11-11-BUS-001](tasks/2026-11-11-BUS-001-business-batch-h.md) | 2026-11-14 |
| [BUS-015](../01-business/05-全量业务代码审查.md) | Excel 导入回价绕过已报客户型号的只读锁 | P2 | 已关闭 | Claude | [2026-11-11-BUS-001](tasks/2026-11-11-BUS-001-business-batch-h.md) | 2026-11-14 |
| [BUS-016](../01-business/05-全量业务代码审查.md) | 同一采购需求在采购单多行时，批量改量重复占用剩余数量 | P2 | 已关闭 | Claude | [2026-11-04-BUS-005](tasks/2026-11-04-BUS-005-fulfillment-batch-e.md) | 2026-11-14 |
| [BUS-017](../01-business/05-全量业务代码审查.md) | 工作台在途与逾期统计仅计算第一页 100 条 | P2 | 已关闭 | Claude | [2026-11-11-BUS-001](tasks/2026-11-11-BUS-001-business-batch-h.md) | 2026-11-14 |
| [BUS-018](../01-business/05-全量业务代码审查.md) | 同一直发货可被并发加入不同出运单，后写覆盖归属 | P2 | 已关闭 | Claude | [2026-11-04-BUS-005](tasks/2026-11-04-BUS-005-fulfillment-batch-e.md) | 2026-11-14 |
| [BUS-019](../01-business/05-全量业务代码审查.md) | 外币欠款抵扣未转移成本人民币基数，订单可按零采购成本结算 | P2 | 已关闭 | Claude | [2026-11-04-BUS-003](tasks/2026-11-04-BUS-003-funds-batch-d.md) | 2026-11-14 |
| [BUS-020](../07-security/03-全量平台与商品代码审查.md) | 普通部门维护绕过事业部非嵌套约束 | P2 | 已关闭 | Claude | [2026-11-11-BUS-001](tasks/2026-11-11-BUS-001-business-batch-h.md) | 2026-11-14 |
| [BUS-021](../01-business/05-全量业务代码审查.md) | 兼容关系说明的主表与中文说明表读写不一致 | P2 | 已关闭 | Claude | [2026-11-11-BUS-001](tasks/2026-11-11-BUS-001-business-batch-h.md) | 2026-11-14 |
| [BUS-022](../01-business/05-全量业务代码审查.md) | 候选批量审核在行锁后继续使用待审核旧快照 | P2 | 已关闭 | Claude | [2026-11-11-BUS-001](tasks/2026-11-11-BUS-001-business-batch-h.md) | 2026-11-14 |
| [BUS-023](tasks/2026-11-03-UI-038-feedback-batch-bugs.md) | 应付的收款方不随供应商改名刷新 | P2 | 已关闭 | Claude | [2026-11-03-UI-038](tasks/2026-11-03-UI-038-feedback-batch-bugs.md) | 2026-11-14 |
| [BUS-024](tasks/2026-11-03-UI-038-feedback-batch-bugs.md) | PI 导出的贸易术语地点与业务员显示中文 | P2 | 已关闭 | Claude | [2026-11-03-UI-038](tasks/2026-11-03-UI-038-feedback-batch-bugs.md) | 2026-11-14 |
| [BUS-025](tasks/2026-11-03-BUS-025-develop-merge-review.md) | 客户合并不加锁，反向并发合并会把两个客户都删掉 | P2 | 已关闭 | Claude | [2026-11-03-BUS-025](tasks/2026-11-03-BUS-025-develop-merge-review.md) | 2026-11-04 |
| [BUS-026](tasks/2026-11-03-BUS-025-develop-merge-review.md) | 「先采购后收款」筛选把租户全部未收款 PI 的 ID 拼进 IN 列表 | P3 | 已关闭 | Claude | [2026-11-12-UI-007](tasks/2026-11-12-UI-007-batch-l.md) | 2026-11-14 |
| [BUS-027](tasks/2026-11-04-BUS-005-fulfillment-batch-e.md) | 订单还有在仓库的出库单时仍可取消，重转后已发出的货会被重复算成可出库 | P2 | 已关闭 | Claude | [2026-11-04-BUS-005](tasks/2026-11-04-BUS-005-fulfillment-batch-e.md) | 2026-11-14 |
| [BUS-028](tasks/2026-11-05-BUS-028-user-feedback-defects.md) | 报价按毛利率定价填 100% 时售价变成成本的 1 万倍 | P2 | 已关闭 | Claude | [2026-11-05-BUS-028](tasks/2026-11-05-BUS-028-user-feedback-defects.md) | 2026-11-14 |
| [BUS-029](tasks/2026-11-05-BUS-028-user-feedback-defects.md) | 采购员未指定的需求，非「全部」数据权限的供应链主管看不到、无法统一分配 | P2 | 已关闭 | Claude | [2026-11-05-BUS-028](tasks/2026-11-05-BUS-028-user-feedback-defects.md) | 2026-11-14 |
| [BUS-030](tasks/2026-11-05-BUS-028-user-feedback-defects.md) | 询价任务改派后，接手人看不到原采购已找到的报价 | P2 | 已关闭 | Claude | [2026-11-05-BUS-028](tasks/2026-11-05-BUS-028-user-feedback-defects.md) | 2026-11-14 |
| [BUS-031](tasks/2026-11-09-BUS-031-quoted-items-task-stuck.md) | 剩余型号已直接报给客户后，询价任务既不能退回也无法完结 | P2 | 已关闭 | Claude | [2026-11-09-BUS-031](tasks/2026-11-09-BUS-031-quoted-items-task-stuck.md) | 2026-11-14 |
| [BUS-032](tasks/2026-11-09-BUS-032-export-currency-symbol.md) | 卢布单据导出后金额仍显示美元符号 | P2 | 已关闭 | Claude | [2026-11-09-BUS-032](tasks/2026-11-09-BUS-032-export-currency-symbol.md) | 2026-11-14 |
| [BUS-033](tasks/2026-11-10-BUS-033-followup-reconcile-duplicate.md) | 单据换了客户后跟进对象核对失败，新客户看不到该单据跟进 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-034](tasks/2026-11-11-BUS-034-product-media-feedback.md) | 商品档案上传苹果格式的 mp4 视频被判为「内容与扩展名不一致」而拒绝 | P2 | 已关闭 | Claude | [2026-11-11-BUS-034](tasks/2026-11-11-BUS-034-product-media-feedback.md) | 2026-11-11 |
| [BUS-035](tasks/2026-11-11-BUS-001-business-batch-h.md) | 事务内加锁后按 ID 重读，MyBatis 一级缓存返回加锁前的旧对象 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-036](tasks/2026-11-13-BUS-036-inquiry-attachment-jpeg.md) | 询盘附件不能上传 .jpeg 图片（WhatsApp 下载的图片） | P2 | 已关闭 | Claude | [2026-11-13-BUS-036](tasks/2026-11-13-BUS-036-inquiry-attachment-jpeg.md) | 2026-11-14 |
| [BUS-037](tasks/2026-11-14-DEP-001-p1-upgrades.md) | 询价结果导入时一份内容损坏的 Excel 让整批预览报「系统错误」 | P2 | 已关闭 | Claude | [2026-11-14-DEP-001](tasks/2026-11-14-DEP-001-p1-upgrades.md) | 2026-11-14 |
| [BUS-038](tasks/2026-11-14-SEC-005-post-remediation-review.md) | 退款申请执行缺少请求验证，负退款金额可绕过服务校验 | P1 | 已关闭 | Claude | [2026-11-14-BUS-038](tasks/2026-11-14-BUS-038-review-fixes.md) | 2026-11-15 |
| [BUS-039](tasks/2026-11-15-SEC-017-proactive-sweep.md) | 多收退回客户不校验实际转出人民币为正，汇兑收益可被记大 | P1 | 已关闭 | Claude | [2026-11-15-SEC-017](tasks/2026-11-15-SEC-017-proactive-sweep.md) | 2026-11-15 |
| [BUS-040](tasks/2026-11-15-SEC-017-proactive-sweep.md) | 到账与订单费用先判断正数后舍入，可登记 0 元单据 | P3 | 已关闭 | Claude | [2026-11-15-SEC-017](tasks/2026-11-15-SEC-017-proactive-sweep.md) | 2026-11-15 |
| [BUS-041](tasks/2026-11-15-SEC-017-proactive-sweep.md) | 手动、补发、迁移订单 pi_id 为 0 被当成同一张 PI，互相扣退款与利润 | P1 | 已关闭 | Claude | [2026-11-15-SEC-017](tasks/2026-11-15-SEC-017-proactive-sweep.md) | 2026-11-15 |
| [BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 作废余额提现后，释放流水被当成新的余额批次，客户可用余额翻倍，可超额提现或抵扣，余额变成负数 | P1 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-043](tasks/2026-11-15-BUS-042-incremental-audit.md) | 售后单有未取消的补发订单时仍可撤销，0 元补发订单和采购需求继续执行，售后单变只读无法再结案 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-044](tasks/2026-11-15-BUS-042-incremental-audit.md) | 取消销售订单不检查进行中的售后单、销售退货、退款申请、费用订单和暂存货物（删除时都会挡），取消或 PI 重转后，售后单仍能对已取消订单补发，退款申请撤不回也执行不了 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-045](tasks/2026-11-15-BUS-042-incremental-audit.md) | 并发「转商机」会给同一客户建出两条进行中商机，商机编号也可能相同 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-046](tasks/2026-11-15-BUS-042-incremental-audit.md) | 上线补齐自动赢单的迁移按「客户曾经有过收款」批量赢单：老客户在最后一次收款之后新开的、还没有任何收款的商机也被标成赢单 | P2 | 暂缓 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-047](tasks/2026-11-15-BUS-042-incremental-audit.md) | 合并客户时搬迁字段来源会撞唯一键：保留客户已标「已问、等回复」或清空过的字段，被合并客户有同字段来源时合并报系统错误、无法完成 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-048](tasks/2026-11-15-BUS-042-incremental-audit.md) | 客户合并不改挂跟进记录、年度计划和背调报告，被合并客户的跟进历史与背调在保留客户下看不到；照搬过来的「背调报告」来源打开时报不存在 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-049](tasks/2026-11-15-BUS-042-incremental-audit.md) | 订单部分取消或改费用后，收款侧剩余金额和待确认工作台仍按 PI 原合计算，手续费差额登记不了、已付清的 PI 继续挂在待处理里 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-050](tasks/2026-11-15-BUS-042-incremental-audit.md) | 采购退货单的退款按创建时冻结的币种计入，采购订单改币种后应付冲减与成本冲减币种不一致 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-051](tasks/2026-11-15-BUS-042-incremental-audit.md) | 「全部品类」专项打开「增减型号」只列出未分类型号，无法再往专项里加其他品类的型号 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-052](tasks/2026-11-15-BUS-042-incremental-audit.md) | 售后单「处理」弹窗按打开时的快照整单提交，并发时会覆盖他人刚改的原因、方案、负责人和下一步 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-053](tasks/2026-11-15-BUS-042-incremental-audit.md) | 客户合并把两个客户的商机都改挂到保留客户，违反「一个客户最多一条商机」：自动赢单只处理其中一条，另一条永远停在进行中 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-054](tasks/2026-11-15-BUS-042-incremental-audit.md) | 供应链需求信号把售后补发订单算成「成交」，补发的问题型号被抬高需求等级和优先级 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-055](tasks/2026-11-15-BUS-042-incremental-audit.md) | 删除商机不触发跟进对象同步，已删除的商机在当天剩余时间里仍留在商机成交看板和今日待办 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-056](tasks/2026-11-15-BUS-042-incremental-audit.md) | 费用单删除分摊后仍提交隐藏的关联售后单，保存一直报「售后单不属于分摊的订单」 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-057](tasks/2026-11-15-BUS-042-incremental-audit.md) | 手工调整余额在启用事业部时不校验必选事业部，录入的旧余额落到无事业部账户 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-058](tasks/2026-11-15-BUS-042-incremental-audit.md) | 多收原路退回改金额时用浮点估算实际退出人民币，可比后端 HALF_UP 账面价值少 0.01 并多记汇兑损益 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-059](tasks/2026-11-15-BUS-042-incremental-audit.md) | 铭牌图片按位置编号交给 AI，确认前删掉一张铭牌后，AI 结果里的编号整体错位，型号会挂上别的铭牌图 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-060](tasks/2026-11-15-BUS-042-incremental-audit.md) | 把疑似重复组标为「不是重复」后，同一组客户会立刻换个成组原因（同邮箱或同电话）再次出现 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-061](tasks/2026-11-15-BUS-042-incremental-audit.md) | 采纳背调建议时把匹配到的已有联系人整体按新增规则校验，没有姓名的存量联系人会让整批采纳失败 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-062](tasks/2026-11-15-BUS-042-incremental-audit.md) | 客户表单新建或修改画像相关字段后，不重算档案完整度、不更新字段来源，列表的完整度和建档筛选、来源提示都是旧的 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-063](tasks/2026-11-15-BUS-042-incremental-audit.md) | 画像「按字段修改」实际用 updateById 把整行客户写回，与转移负责人、调整等级、主联系人同步等并发时会用旧值覆盖别人的修改 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-064](tasks/2026-11-15-BUS-042-incremental-audit.md) | 经营等级 RFM 把售后补发订单和费用订单算作成交订单，抬高最近成交天数、订单数和金额的评分 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-065](tasks/2026-11-15-BUS-042-incremental-audit.md) | 候选 AI 解析品类不按租户行业限制，可把其他行业的品类写进候选并建档 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-066](tasks/2026-11-15-BUS-042-incremental-audit.md) | 按筛选发起 AI 细分总是取同一批前 200 个，判断不出的和不能维护的会反复占位 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-067](tasks/2026-11-15-BUS-042-incremental-audit.md) | 改选细分品类用列表快照整条回写商品，可能覆盖别人刚改的名称、简介、生命周期 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-068](tasks/2026-11-15-BUS-042-incremental-audit.md) | 售后补发订单能修改费用、能部分取消，之后收款状态从「无需收款」被改成「未付款」 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-069](tasks/2026-11-15-BUS-042-incremental-audit.md) | 费用订单可以通过「修改订单费用」删光全部费用行，变成 0 元订单且永远不会自动完成 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-070](tasks/2026-11-15-BUS-042-incremental-audit.md) | 「毛利为负」快捷筛选按 PI 预估净利润过滤，和列表「毛利」列（实际成本口径）不是同一个数，手动订单永远筛不出来 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-071](tasks/2026-11-15-BUS-042-incremental-audit.md) | 付款时登记优惠不锁采购订单，用无锁读到的旧实体整行回写，可能覆盖并发保存的修改 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-072](tasks/2026-11-15-BUS-042-incremental-audit.md) | 冲销采购入库单不处理部分取消时转入的闲置库存，留下实际不存在的闲置库存，订单已入库数被扣成负数 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-073](tasks/2026-11-15-BUS-042-incremental-audit.md) | 兼职采购的回价被审核退回后，每日补登把它当成「回价已撤回」写进供应链时间线 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-074](tasks/2026-11-15-BUS-042-incremental-audit.md) | 合并需求对象时，被合并方的覆盖渠道被丢弃、专项型号被移出，与注释写的「并到保留方」不符 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-075](tasks/2026-11-15-BUS-042-incremental-audit.md) | 每日重算不加锁读取全部需求对象后整行回写，会覆盖期间用户改的优先级、覆盖状态和下一步 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-076](tasks/2026-11-15-BUS-042-incremental-audit.md) | 记一次处理对每个所选型号都全量读取一遍租户的采购订单行，型号多时请求可能超时 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-077](tasks/2026-11-15-BUS-042-incremental-audit.md) | 报价行供应提示和发送确认不跟随需求对象合并，被合并的型号报价时没有货源提示 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [BUS-079](tasks/2026-11-15-BUS-042-incremental-audit.md) | 退货入库验收与作废销售退货的加锁顺序相反，并发时可能死锁回滚 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-16 |
| [BUS-080](tasks/2026-11-15-BUS-042-incremental-audit.md) | 经营看板「应收未收」仍按 PI 合计减已到账，订单部分取消或补费用后口径不一致（BUS-049 同类） | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-16 |
| [BUS-081](tasks/2026-11-15-BUS-042-incremental-audit.md) | 经营看板「应收未收」只算 PI，收款记在订单上的手动订单、费用订单没有计入（收款工作台已计入） | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-16 |
| [BUS-082](tasks/2026-11-16-BUS-082-feedback-1116.md) | CI 导出：寄件方（Shipper）电话、邮箱、联系人为空；收货人地址只有国家；H.S Code 为空；缺信息时仍能导出 | P2 | 已发布待验收 | Claude | [2026-11-16-BUS-082](tasks/2026-11-16-BUS-082-feedback-1116.md) | 2026-11-16 |
| [BUS-083](tasks/2026-11-16-BUS-082-feedback-1116.md) | PL 导出：收货人邮箱、WhatsApp 不显示，地址只有国家；缺信息时仍能导出 | P2 | 已发布待验收 | Claude | [2026-11-16-BUS-082](tasks/2026-11-16-BUS-082-feedback-1116.md) | 2026-11-16 |
| [BUS-084](tasks/2026-11-16-BUS-084-batch2-review.md) | 采购登记「直发货代」可以选其他客户的指定货代，后端只校验货代启用，下拉也列出全部货代 | P2 | 已发布待验收 | Claude | [2026-11-16-BUS-084](tasks/2026-11-16-BUS-084-batch2-review.md) | 2026-11-16 |
| [BUS-085](tasks/2026-11-16-BUS-084-batch2-review.md) | 费用单收款方选「货代」时，只有费用管理菜单的财务取货代列表返回 403，下拉为空选不到货代 | P2 | 已发布待验收 | Claude | [2026-11-16-BUS-084](tasks/2026-11-16-BUS-084-batch2-review.md) | 2026-11-16 |
| [BUS-086](tasks/2026-11-16-BUS-084-batch2-review.md) | 公海客户认领前仍可经接口新建手动销售订单、提交询盘（只有前端客户选择器排除了公海客户） | P3 | 已发布待验收 | Claude | [2026-11-16-BUS-084](tasks/2026-11-16-BUS-084-batch2-review.md) | 2026-11-16 |

## 前端

| ID / 原专题 | 简短标题 | 优先级 | 状态 | 负责人 | 任务记录 | 最后更新 |
| --- | --- | --- | --- | --- | --- | --- |
| [UI-001](../02-frontend/01-接口重放与附件上传.md) | 多选上传的附件被后完成的上传覆盖 | P1 | 已关闭 | Claude | [2026-11-04-UI-001](tasks/2026-11-04-UI-001-frontend-p1-batch-g.md) | 2026-11-14 |
| [UI-002](../02-frontend/01-接口重放与附件上传.md) | 令牌刷新后的请求重放绕过业务错误校验 | P1 | 已关闭 | Claude | [2026-11-04-UI-001](tasks/2026-11-04-UI-001-frontend-p1-batch-g.md) | 2026-11-14 |
| [UI-003](../02-frontend/02-表单草稿与页面切换.md) | 我的询价切换任务直接丢失未保存回价 | P1 | 已关闭 | Claude | [2026-11-04-UI-001](tasks/2026-11-04-UI-001-frontend-p1-batch-g.md) | 2026-11-14 |
| [UI-004](../02-frontend/02-表单草稿与页面切换.md) | 未保存保护遗漏标签右键刷新，草稿被直接重建丢失 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-005](../02-frontend/02-表单草稿与页面切换.md) | 商品向导宣称自动保存，但补充资料离开即丢失 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-006](../02-frontend/03-错误恢复字典与列表一致性.md) | 错误页“重试”不能恢复成功页面 | P2 | 已关闭 | Claude | [2026-11-12-UI-004](tasks/2026-11-12-UI-004-batch-k.md) | 2026-11-14 |
| [UI-007](../02-frontend/03-错误恢复字典与列表一致性.md) | 租户和套餐列表缺少三项固定审计字段 | P3 | 已关闭 | Claude | [2026-11-12-UI-007](tasks/2026-11-12-UI-007-batch-l.md) | 2026-11-14 |
| [UI-008](../02-frontend/03-错误恢复字典与列表一致性.md) | 字典修改后业务下拉仍使用页面生命周期内旧缓存 | P2 | 已关闭 | Claude | [2026-11-12-UI-004](tasks/2026-11-12-UI-004-batch-k.md) | 2026-11-14 |
| [UI-009](../02-frontend/03-错误恢复字典与列表一致性.md) | 商品新建向导只加载前 100 个一级品类 | P2 | 已关闭 | Claude | [2026-11-12-UI-004](tasks/2026-11-12-UI-004-batch-k.md) | 2026-11-14 |
| [UI-010](../05-code-and-docs/02-前端冗余与约定.md) | 菜单标题解析仍按旧 status 规则，和当前侧栏约定冲突 | P2 | 已关闭 | Claude | [2026-11-12-UI-004](tasks/2026-11-12-UI-004-batch-k.md) | 2026-11-14 |
| [UI-011](../05-code-and-docs/02-前端冗余与约定.md) | 旧工作台静态演示数据与当前动态看板并存 | P3 | 已关闭 | Claude | [2026-11-12-UI-007](tasks/2026-11-12-UI-007-batch-l.md) | 2026-11-14 |
| [UI-012](../02-frontend/02-表单草稿与页面切换.md) | 角色列表启停重置数据权限并清空自定义部门 | P1 | 已关闭 | Claude | [2026-11-03-UI-012](tasks/2026-11-03-UI-012-role-permission-guard.md) | 2026-11-04 |
| [UI-013](../02-frontend/02-表单草稿与页面切换.md) | 角色 scope0 的显示、回填与后端语义不一致 | P2 | 已关闭 | Claude | [2026-11-03-UI-012](tasks/2026-11-03-UI-012-role-permission-guard.md) | 2026-11-04 |
| [UI-014](../02-frontend/02-表单草稿与页面切换.md) | 角色权限尚未加载就能保存，可能清空或串用上一角色权限 | P1 | 已关闭 | Claude | [2026-11-03-UI-012](tasks/2026-11-03-UI-012-role-permission-guard.md) | 2026-11-04 |
| [UI-015](../02-frontend/03-错误恢复字典与列表一致性.md) | 销售统计下钻丢失业务员/事业部维度，列表与统计数对不上 | P2 | 已关闭 | Claude | [2026-11-12-UI-004](tasks/2026-11-12-UI-004-batch-k.md) | 2026-11-14 |
| [UI-016](../02-frontend/03-错误恢复字典与列表一致性.md) | 客户当地时间的跨日时差被折回，早晚方向显示错误 | P2 | 已关闭 | Claude | [2026-11-12-UI-004](tasks/2026-11-12-UI-004-batch-k.md) | 2026-11-14 |
| [UI-017](../02-frontend/05-供应链与财务交互补查.md) | 关闭后旧详情回包覆盖新出库单，完成打包提交旧单 | P2 | 已关闭 | Claude | [2026-11-11-UI-017](tasks/2026-11-11-UI-017-frontend-batch-i.md) | 2026-11-14 |
| [UI-018](../02-frontend/05-供应链与财务交互补查.md) | 事业部信息晚到会清空正在填写的费用草稿 | P2 | 已关闭 | Claude | [2026-11-11-UI-017](tasks/2026-11-11-UI-017-frontend-batch-i.md) | 2026-11-14 |
| [UI-019](../02-frontend/06-营销主数据与通用交互补查.md) | AI 测试结果未绑定配置，修改模型或切换服务商后仍显示旧的通过状态 | P2 | 已关闭 | Claude | [2026-11-12-OPS-001](tasks/2026-11-12-OPS-001-batch-j.md) | 2026-11-14 |
| [UI-020](../02-frontend/06-营销主数据与通用交互补查.md) | 迟到的模型列表缓存被用于另一服务商 | P2 | 已关闭 | Claude | [2026-11-12-OPS-001](tasks/2026-11-12-OPS-001-batch-j.md) | 2026-11-14 |
| [UI-021](../02-frontend/06-营销主数据与通用交互补查.md) | 取消候选导入后迟到预览复活，界面可确认却提交空文件 | P2 | 已关闭 | Claude | [2026-11-12-OPS-001](tasks/2026-11-12-OPS-001-batch-j.md) | 2026-11-14 |
| [UI-022](../02-frontend/06-营销主数据与通用交互补查.md) | 列表操作只绑定鼠标点击，键盘 Tab 跳过修改/作废等入口 | P2 | 已关闭 | Claude | [2026-11-12-UI-022](tasks/2026-11-12-UI-022-batch-m.md) | 2026-11-14 |
| [UI-023](../02-frontend/07-全量前端代码与页面审查.md) | 顶栏“使用文档”仍指向未注册的 welcome 页 | P2 | 已关闭 | Claude | [2026-11-12-OPS-001](tasks/2026-11-12-OPS-001-batch-j.md) | 2026-11-14 |
| [UI-024](../02-frontend/07-全量前端代码与页面审查.md) | 用户列表的部门筛选没有传给后端 | P2 | 已关闭 | Claude | [2026-11-11-UI-017](tasks/2026-11-11-UI-017-frontend-batch-i.md) | 2026-11-14 |
| [UI-025](../02-frontend/07-全量前端代码与页面审查.md) | 编辑字典类型漏传必填编码，正常保存被校验拒绝 | P2 | 已关闭 | Claude | [2026-11-11-UI-017](tasks/2026-11-11-UI-017-frontend-batch-i.md) | 2026-11-14 |
| [UI-026](../02-frontend/07-全量前端代码与页面审查.md) | 表单标签重复关联背景控件，部分自绘财务字段没有可访问名称 | P2 | 已关闭 | Claude | [2026-11-12-UI-022](tasks/2026-11-12-UI-022-batch-m.md) | 2026-11-14 |
| [UI-027](../02-frontend/07-全量前端代码与页面审查.md) | 账户再次局部编辑会丢掉本轮新填的身份证，卡片仍显示已填写 | P2 | 已关闭 | Claude | [2026-11-11-UI-017](tasks/2026-11-11-UI-017-frontend-batch-i.md) | 2026-11-14 |
| [UI-028](../02-frontend/07-全量前端代码与页面审查.md) | 多事业部公司的公共部门用户无法在询盘中快速新建客户 | P2 | 已关闭 | Claude | [2026-11-12-UI-022](tasks/2026-11-12-UI-022-batch-m.md) | 2026-11-14 |
| [UI-029](../02-frontend/07-全量前端代码与页面审查.md) | 商品关键词查询保留已隐藏的勾选项，批量操作仍作用于旧商品 | P2 | 已关闭 | Claude | [2026-11-12-OPS-001](tasks/2026-11-12-OPS-001-batch-j.md) | 2026-11-14 |
| [UI-030](../02-frontend/07-全量前端代码与页面审查.md) | 工作台待办数字进入普通列表，丢失待办状态口径 | P2 | 已关闭 | Claude | [2026-11-12-UI-022](tasks/2026-11-12-UI-022-batch-m.md) | 2026-11-14 |
| [UI-031](../02-frontend/07-全量前端代码与页面审查.md) | 关闭“显示已作废”未归位分页，现有有效记录被展示为空 | P2 | 已关闭 | Claude | [2026-11-11-UI-017](tasks/2026-11-11-UI-017-frontend-batch-i.md) | 2026-11-14 |
| [UI-032](../02-frontend/07-全量前端代码与页面审查.md) | 询盘列表翻页事件又被排序处理器重置为第一页 | P2 | 已关闭 | Claude | [2026-11-11-UI-017](tasks/2026-11-11-UI-017-frontend-batch-i.md) | 2026-11-14 |
| [UI-033](../02-frontend/07-全量前端代码与页面审查.md) | 订单备注保存失败仍退出编辑，丢失待重试草稿 | P2 | 已关闭 | Claude | [2026-11-11-UI-017](tasks/2026-11-11-UI-017-frontend-batch-i.md) | 2026-11-14 |
| [UI-034](../02-frontend/07-全量前端代码与页面审查.md) | 首次多图上传将主图状态回填为重置条件，后续失败文件失去重试入口 | P2 | 已关闭 | Claude | [2026-11-12-OPS-001](tasks/2026-11-12-OPS-001-batch-j.md) | 2026-11-14 |
| [UI-035](../02-frontend/07-全量前端代码与页面审查.md) | 物流出运页在窄屏下整页横溢出，创建入口与详情卡片越出可视区 | P3 | 已关闭 | Claude | [2026-11-12-UI-007](tasks/2026-11-12-UI-007-batch-l.md) | 2026-11-14 |
| [UI-036](../02-frontend/07-全量前端代码与页面审查.md) | 三处人民币运费输入仍用货币符号，偏离统一币种代码规范 | P3 | 已关闭 | Claude | [2026-11-12-UI-007](tasks/2026-11-12-UI-007-batch-l.md) | 2026-11-14 |
| [UI-037](../02-frontend/02-表单草稿与页面切换.md) | 编辑角色的部门数据晚返回时，关闭后又弹出编辑抽屉 | P3 | 已关闭 | Claude | [2026-11-03-UI-037](tasks/2026-11-03-UI-037-role-edit-race.md) | 2026-11-04 |
| [UI-038](tasks/2026-11-03-UI-038-feedback-batch-bugs.md) | 询盘管理、销售订单列表点第 2 页无效 | P2 | 已关闭 | Claude | [2026-11-03-UI-038](tasks/2026-11-03-UI-038-feedback-batch-bugs.md) | 2026-11-14 |
| [UI-039](tasks/2026-11-03-UI-038-feedback-batch-bugs.md) | 新建询盘时客户名称过长把「来源」挤出对话框 | P3 | 已关闭 | Claude | [2026-11-03-UI-038](tasks/2026-11-03-UI-038-feedback-batch-bugs.md) | 2026-11-14 |
| [UI-040](tasks/2026-11-03-UI-038-feedback-batch-bugs.md) | 登录过期后页面先提示加载失败再跳登录页 | P2 | 已关闭 | Claude | [2026-11-03-UI-038](tasks/2026-11-03-UI-038-feedback-batch-bugs.md) | 2026-11-14 |
| [UI-041](../02-frontend/07-全量前端代码与页面审查.md) | 表格与列表在电脑低分辨率下布局失效 | P2 | 已关闭 | Claude | [2026-11-04-UI-041](tasks/2026-11-04-UI-041-low-res-layout.md) | 2026-11-14 |
| [UI-042](tasks/2026-11-05-BUS-028-user-feedback-defects.md) | 单据导出图片模糊，且下方大片空白 | P2 | 已关闭 | Claude | [2026-11-05-BUS-028](tasks/2026-11-05-BUS-028-user-feedback-defects.md) | 2026-11-14 |
| [UI-043](tasks/2026-11-05-UI-043-inquiry-stats-filters.md) | 询盘管理顶部统计不随搜索条件变化，与列表对不上 | P2 | 已关闭 | Claude / Codex | [2026-11-05-UI-043](tasks/2026-11-05-UI-043-inquiry-stats-filters.md) | 2026-11-14 |
| [UI-044](tasks/2026-11-09-UI-044-user-feedback-1109.md) | 报价单 / PI 预览模糊、PDF 页边和底部大片空白 | P2 | 已关闭 | Claude | [2026-11-09-UI-044](tasks/2026-11-09-UI-044-user-feedback-1109.md) | 2026-11-14 |
| [UI-045](tasks/2026-11-09-UI-044-user-feedback-1109.md) | 列表「型号 · 数量」列型号过长时盖住相邻的品牌列 | P3 | 已关闭 | Claude | [2026-11-09-UI-044](tasks/2026-11-09-UI-044-user-feedback-1109.md) | 2026-11-14 |
| [UI-046](tasks/2026-11-11-UI-046-fee-order-detail-crash.md) | 费用订单详情页打开即显示「页面出现错误」 | P1 | 已关闭 | Claude | [2026-11-11-UI-046](tasks/2026-11-11-UI-046-fee-order-detail-crash.md) | 2026-11-11 |
| [UI-047](tasks/2026-11-11-UI-047-user-feedback-1111.md) | 列多的列表滚动后，表格固定列盖住顶部栏与标签栏 | P2 | 已关闭 | Claude | [2026-11-11-UI-047](tasks/2026-11-11-UI-047-user-feedback-1111.md) | 2026-11-11 |
| [UI-048](tasks/2026-11-11-UI-047-user-feedback-1111.md) | 来源单号与客户 / 供应商合在一列，不符合单据页统一规范 | P3 | 已关闭 | Claude | [2026-11-11-UI-047](tasks/2026-11-11-UI-047-user-feedback-1111.md) | 2026-11-11 |
| [UI-049](tasks/2026-11-11-UI-049-notification-content-wrap.md) | 通知抽屉内容在日期、型号中间断行 | P3 | 已关闭 | Claude | [2026-11-11-UI-049](tasks/2026-11-11-UI-049-notification-content-wrap.md) | 2026-11-11 |
| [UI-050](tasks/2026-11-11-BUS-034-product-media-feedback.md) | 商品档案的图片点开不能预览 | P3 | 已关闭 | Claude | [2026-11-11-BUS-034](tasks/2026-11-11-BUS-034-product-media-feedback.md) | 2026-11-11 |
| [UI-051](tasks/2026-11-12-UI-051-source-no-column.md) | 售后、退款、发货跟踪、采购入库列表的来源单号仍与客户 / 订单 / 供应商合在一列 | P3 | 已关闭 | Claude | [2026-11-12-UI-051](tasks/2026-11-12-UI-051-source-no-column.md) | 2026-11-14 |
| [UI-052](tasks/2026-11-14-SEC-005-post-remediation-review.md) | 公共单据附件卡的备注草稿未登记未保存状态 | P2 | 已关闭 | Claude | [2026-11-14-BUS-038](tasks/2026-11-14-BUS-038-review-fixes.md) | 2026-11-15 |
| [UI-053](tasks/2026-11-14-SEC-005-post-remediation-review.md) | 销售退货详情改状态后返回保活列表，状态和计数仍旧 | P2 | 已关闭 | Claude | [2026-11-14-BUS-038](tasks/2026-11-14-BUS-038-review-fixes.md) | 2026-11-15 |
| [UI-054](tasks/2026-11-14-SEC-005-post-remediation-review.md) | 退款弹窗旧订单响应迟到，覆盖新申请的人民币换算 | P2 | 已关闭 | Claude | [2026-11-14-BUS-038](tasks/2026-11-14-BUS-038-review-fixes.md) | 2026-11-15 |
| [UI-055](tasks/2026-11-15-SEC-017-proactive-sweep.md) | 保活页面从其他页面返回后不刷新，列表与详情显示旧数据 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-056](tasks/2026-11-15-SEC-017-proactive-sweep.md) | 多个弹窗切换对象时未丢弃迟到响应，登记付款可分配到上一张应付 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-057](tasks/2026-11-15-SEC-017-proactive-sweep.md) | 销售订单备注卡、采购订单草稿编辑、客户与供应商表单未登记未保存 | P2 | 已关闭 | Claude | [2026-11-15-SEC-017](tasks/2026-11-15-SEC-017-proactive-sweep.md) | 2026-11-15 |
| [UI-058](tasks/2026-11-15-SEC-018-codegraph-sweep.md) | 客户、供应商页面离开时压栈或在地址带 from，返回条指向已离开的页面 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-059](tasks/2026-11-15-SEC-018-codegraph-sweep.md) | 不在菜单里的表单与迁移页没有登记标题，标签显示「上一页」或「××详情」 | P3 | 已关闭 | Claude | [2026-11-15-SEC-018](tasks/2026-11-15-SEC-018-codegraph-sweep.md) | 2026-11-15 |
| [UI-060](tasks/2026-11-15-BUS-042-incremental-audit.md) | 档案补全切换客户时，向导与档案的迟到响应互相错配，可能把编辑写到另一个客户，联系人项被静默丢弃 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-061](tasks/2026-11-15-BUS-042-incremental-audit.md) | 收款单列表翻页总被排序回调重置到第 1 页，第 2 页及以后的收款单无法查看 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-062](tasks/2026-11-15-BUS-042-incremental-audit.md) | 批量 AI 解析跑完会关掉正在填写的审核抽屉，审核中的修改直接丢失 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-063](tasks/2026-11-15-BUS-042-incremental-audit.md) | 订单成本卡新增「售后退货冲减」列后，合计行跨列没改，实际成本、退款、收入、毛利的合计全部错位一列 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-064](tasks/2026-11-15-BUS-042-incremental-audit.md) | 客户跟进 AI 整理用的是旧闭包，整理期间手工填写的「下一步」和日期被 AI 建议覆盖 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-065](tasks/2026-11-15-BUS-042-incremental-audit.md) | 售后单和销售退货详情返回刷新时，正在填写的结案、处理、补发、确认退货弹窗内容被重置 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-066](tasks/2026-11-15-BUS-042-incremental-audit.md) | 售后单详情的跟进记录草稿（文字和已上传附件）没有登记未保存，关闭或刷新标签直接丢失 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-067](tasks/2026-11-15-BUS-042-incremental-audit.md) | 售后管理列表缺少 spec 要求的「客户」「原因」筛选 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-068](tasks/2026-11-15-BUS-042-incremental-audit.md) | 输单弹窗把「60 天后提醒重新激活」写死，租户改了培育默认天数后提示和实际激活日期不一致 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-069](tasks/2026-11-15-BUS-042-incremental-audit.md) | 档案补全向导每次刷新都清空未保存的点选，正在编辑的档案弹窗和联系人弹窗也被重置 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-070](tasks/2026-11-15-BUS-042-incremental-audit.md) | 档案补全首次加载失败后点「重试」，页面永远停在错误提示 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-071](tasks/2026-11-15-BUS-042-incremental-audit.md) | 在 RFM 明细或「再背调一次？」弹窗里点链接跳页后，弹窗仍盖在新页面上 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-072](tasks/2026-11-15-BUS-042-incremental-audit.md) | 跟进时间线切换筛选或加载更多时没有丢弃迟到响应，列表会混入其他筛选的记录 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-073](tasks/2026-11-15-BUS-042-incremental-audit.md) | 客户名称为空时，详情页标签和返回条只显示「客户 」，余额流水抽屉的客户名为空 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-074](tasks/2026-11-15-BUS-042-incremental-audit.md) | 批量付款切换付款方式后保留已隐藏的对方账户，整批被后端拒绝，且界面无法清除 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-075](tasks/2026-11-15-BUS-042-incremental-audit.md) | 批量付款弹窗里外币应付的逐笔未付金额被标成 CNY | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-076](tasks/2026-11-15-BUS-042-incremental-audit.md) | 应付列表勾选的应付不随列表刷新，单张付款后再批量付款仍按旧未付额默认并被拒 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-077](tasks/2026-11-15-BUS-042-incremental-audit.md) | 退款申请驳回失败时没有任何提示，弹窗停在原处 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-078](tasks/2026-11-15-BUS-042-incremental-audit.md) | 余额月度核对切换月份或币种时不丢弃迟到响应，旧币种金额会按新币种标注显示 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-079](tasks/2026-11-15-BUS-042-incremental-audit.md) | 「去报价」弹窗承诺未回价型号「询价任务照常进行、回价后可出新版本」，实际报价单一发送，型号就锁定、任务自动完结，采购无法再回价 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-080](tasks/2026-11-15-BUS-042-incremental-audit.md) | 批量生成报价单后，用抽屉右上角关闭而不点「完成」，报价单列表不刷新，看不到刚生成的报价单 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-081](tasks/2026-11-15-BUS-042-incremental-audit.md) | 工作台「售后要更新客户」只统计本人负责的单据，点进去的列表却按数据范围显示全部，主管看到的数字与列表对不上 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-082](tasks/2026-11-15-BUS-042-incremental-audit.md) | 通知抽屉切换「待处理 / 全部」时不丢弃迟到响应，旧标签的数据可能覆盖当前列表 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-083](tasks/2026-11-15-BUS-042-incremental-audit.md) | AI 任务跑完后按提交时的旧筛选刷新，列表被换成旧条件的数据 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-084](tasks/2026-11-15-BUS-042-incremental-audit.md) | 轮询 AI 任务时一次网络错误就放弃跟踪，跑完的结果不提示、列表不刷新 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-085](tasks/2026-11-15-BUS-042-incremental-audit.md) | 删除订单后用 history.replace 跳到订单列表，不走 tabsActions.backOr，标签栈留下重复的列表页 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-086](tasks/2026-11-15-BUS-042-incremental-audit.md) | 供应链跟进换筛选后仍保留旧勾选，「延后」会把看不见的型号一起延后 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-087](tasks/2026-11-15-BUS-042-incremental-audit.md) | 供应商开发板块会沿用看不见的二级品类筛选，清单被悄悄过滤 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-088](tasks/2026-11-15-BUS-042-incremental-audit.md) | 「去询价」「去查看」用原生 href，会整页重载，「去询价」带的型号关键词也不生效 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-089](tasks/2026-11-15-BUS-042-incremental-audit.md) | 团队视图和覆盖矩阵下钻时丢失统计口径（90 天、未指定负责人、负责人、优先级、无货），列表与统计数对不上 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-090](tasks/2026-11-15-BUS-042-incremental-audit.md) | 覆盖矩阵和型号清单切换条件时不丢弃迟到响应，型号清单在非第一页改筛选会发出两次请求互相覆盖 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-091](tasks/2026-11-15-BUS-042-incremental-audit.md) | 今日待办接口失败时显示「今天的都处理完了」，概览或团队接口失败时一直显示骨架屏 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-092](tasks/2026-11-15-BUS-042-incremental-audit.md) | 品牌和品类选项用模块级缓存，从不失效，首次加载失败后整段会话下拉一直为空 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-093](tasks/2026-11-15-BUS-042-incremental-audit.md) | 待归类信号列表缺少四个固定审计列，每页条数写死；型号清单和专项表格用 max-content 横向滚动 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [UI-094](tasks/2026-11-15-BUS-042-incremental-audit.md) | 需求对象合并弹窗只说信号会并入，没有说明覆盖渠道和专项型号也会并到保留方 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-16 |
| [UI-095](tasks/2026-11-16-UI-095-ship-notice-forwarder.md) | 发货通知有多家货代时不默认选中，「发通知」置灰却不说原因；货代下拉还混着历史迁移的「未登记货代」占位 | P2 | 已发布待验收 | Claude | [2026-11-16-UI-095](tasks/2026-11-16-UI-095-ship-notice-forwarder.md) | 2026-11-16 |
| [UI-096](tasks/2026-11-16-BUS-082-feedback-1116.md) | 出运管理「待出运」按客户分组时只显示客户名称，没有按统一客户列显示国家 / 地区与主联系人 | P3 | 已发布待验收 | Claude | [2026-11-16-BUS-082](tasks/2026-11-16-BUS-082-feedback-1116.md) | 2026-11-16 |
| [UI-097](tasks/2026-11-16-BUS-082-feedback-1116.md) | 客户跟进页面的客户没有按统一客户列（名称、国家 / 地区、主联系人三行）显示 | P3 | 已发布待验收 | Claude | [2026-11-16-BUS-082](tasks/2026-11-16-BUS-082-feedback-1116.md) | 2026-11-16 |
| [UI-098](tasks/2026-11-16-BUS-082-feedback-1116.md) | 采购订单列表只显示型号数，没有按单据页统一规范显示品牌、品类、型号 | P3 | 已发布待验收 | Claude | [2026-11-16-BUS-082](tasks/2026-11-16-BUS-082-feedback-1116.md) | 2026-11-16 |
| [UI-099](tasks/2026-11-16-BUS-082-feedback-1116.md) | CI 导出的 DESCRIPTION 列不随内容自动调整，公司印章歪斜、模糊 | P3 | 已发布待验收 | Claude | [2026-11-16-BUS-082](tasks/2026-11-16-BUS-082-feedback-1116.md) | 2026-11-16 |
| [UI-100](tasks/2026-11-16-UI-100-po-confirm-ship-date.md) | 采购订单草稿在页面上填了预计发货日期（未保存）后点「确认下单」，弹窗里的预计发货日期仍为空，要重新选 | P3 | 已发布待验收 | Claude | [2026-11-16-UI-100](tasks/2026-11-16-UI-100-po-confirm-ship-date.md) | 2026-11-16 |

## AI 可靠性

| ID / 原专题 | 简短标题 | 优先级 | 状态 | 负责人 | 任务记录 | 最后更新 |
| --- | --- | --- | --- | --- | --- | --- |
| [AI-001](../04-ai-and-operations/01-AI任务可靠性与诊断.md) | 受理、回调与超时扫描无条件更新，终态可回退且与询盘状态分叉 | P1 | 已关闭 | Claude | [2026-11-04-AI-001](tasks/2026-11-04-AI-001-task-reliability-batch-f.md) | 2026-11-14 |
| [AI-002](../04-ai-and-operations/01-AI任务可靠性与诊断.md) | 结果回调一次失败后被吞掉，已完成 AI 结果没有可靠投递路径 | P1 | 已关闭 | Claude | [2026-11-04-AI-001](tasks/2026-11-04-AI-001-task-reliability-batch-f.md) | 2026-11-14 |
| [AI-003](../04-ai-and-operations/02-AI附件入口与权限.md) | 普通认证用户可通过通用 AI 入口指定 URL / 本地附件路径 | P1 | 已关闭 | Claude | [2026-11-03-SEC-001](tasks/2026-11-03-SEC-001-session-ai-input-hotfix.md) | 2026-11-04 |
| [AI-004](../04-ai-and-operations/01-AI任务可靠性与诊断.md) | 串行诊断的总超时预算超过 Java 的 30 秒请求期限 | P2 | 已关闭 | Claude | [2026-11-04-AI-001](tasks/2026-11-04-AI-001-task-reliability-batch-f.md) | 2026-11-14 |
| [AI-005](../04-ai-and-operations/01-AI任务可靠性与诊断.md) | 整任务期限遗漏附件下载，慢流可超时后继续运行 | P1 | 已关闭 | Claude | [2026-11-15-SEC-017](tasks/2026-11-15-SEC-017-proactive-sweep.md) | 2026-11-15 |
| [AI-006](../04-ai-and-operations/03-全量运行脚本与配置审查.md) | 未连接到模型服务商时仍把API Key标为有效 | P2 | 已关闭 | Claude | [2026-11-04-AI-001](tasks/2026-11-04-AI-001-task-reliability-batch-f.md) | 2026-11-14 |
| [AI-007](../04-ai-and-operations/01-AI任务可靠性与诊断.md) | 个人订阅方式同时只能执行一个任务，其他任务立即失败 | P2 | 已关闭 | Claude | [2026-11-06-AI-007](tasks/2026-11-06-AI-007-subscription-concurrency.md) | 2026-11-14 |
| [AI-008](../04-ai-and-operations/01-AI任务可靠性与诊断.md) | 编排服务拒绝请求时不读请求体，Windows 下客户端偶发连接被中止 | P3 | 已关闭 | Claude | [2026-11-06-AI-008](tasks/2026-11-06-AI-008-token-reject-drain.md) | 2026-11-14 |
| [AI-009](tasks/2026-11-14-SEC-005-post-remediation-review.md) | 大附件读取和预处理阶段缺少心跳，未到总期限先被判失败 | P2 | 已关闭 | Claude | [2026-11-14-BUS-038](tasks/2026-11-14-BUS-038-review-fixes.md) | 2026-11-15 |
| [AI-010](tasks/2026-11-14-SEC-005-post-remediation-review.md) | 分批解析失败时未汇总工作线程用量，失败回调漏记消耗 | P2 | 已关闭 | Claude | [2026-11-14-BUS-038](tasks/2026-11-14-BUS-038-review-fixes.md) | 2026-11-15 |
| [AI-011](tasks/2026-11-15-SEC-018-codegraph-sweep.md) | AI 回调信任模型回传的 key，不核对是否属于本次提交 | P3 | 已关闭 | Claude | [2026-11-15-SEC-018](tasks/2026-11-15-SEC-018-codegraph-sweep.md) | 2026-11-15 |
| [AI-012](tasks/2026-11-15-BUS-042-incremental-audit.md) | 跟进整理的阶段答案字段在发给模型的 Schema 里不能为空，原文没提到也会被迫给出答案 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [AI-013](tasks/2026-11-15-BUS-042-incremental-audit.md) | 大询盘核验按原型号对回结果，同一批里原型号相同的第二条既不采用核验结果也不标待核实 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [AI-014](tasks/2026-11-16-AI-014-translation-requester.md) | 当前用户按用户名找不到用户档案时，查不到自己提交的翻译结果（生成英文描述、客户名 AI 翻译），提示任务不存在 | P3 | 已发布待验收 | Claude | [2026-11-16-AI-014](tasks/2026-11-16-AI-014-translation-requester.md) | 2026-11-16 |

## 身份与对象权限

| ID / 原专题 | 简短标题 | 优先级 | 状态 | 负责人 | 任务记录 | 最后更新 |
| --- | --- | --- | --- | --- | --- | --- |
| [SEC-001](../07-security/02-身份会话与对象权限.md) | 登录日志将在线访问凭据返回给同租户普通登录用户 | P1 | 已关闭 | Claude | [2026-11-03-SEC-001](tasks/2026-11-03-SEC-001-session-ai-input-hotfix.md) | 2026-11-04 |
| [SEC-002](../07-security/02-身份会话与对象权限.md) | 用户管理写操作缺少目标租户约束 | P1 | 已关闭 | Claude | [2026-11-03-SEC-011](tasks/2026-11-03-SEC-011-tenant-isolation.md) | 2026-11-04 |
| [SEC-003](../07-security/02-身份会话与对象权限.md) | 角色、部门、岗位及角色授权缺少对象归属校验 | P1 | 已关闭 | Claude | [2026-11-03-SEC-011](tasks/2026-11-03-SEC-011-tenant-isolation.md) | 2026-11-04 |
| [SEC-004](../07-security/02-身份会话与对象权限.md) | 禁用、删除或管理员重置密码未撤销已有会话 | P1 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [SEC-005](../07-security/02-身份会话与对象权限.md) | 新增附件类型的链接未继承所属单据数据范围 | P1 | 已关闭 | Claude | [2026-11-15-SEC-017](tasks/2026-11-15-SEC-017-proactive-sweep.md) | 2026-11-15 |
| [SEC-006](../07-security/02-身份会话与对象权限.md) | 登录幂等缓存先于身份验证，且未绑定账号 | P2 | 已关闭 | Claude | [2026-11-03-SEC-005](tasks/2026-11-03-SEC-005-object-guards.md) | 2026-11-04 |
| [SEC-007](../07-security/02-身份会话与对象权限.md) | 恢复租户会同时重新启用此前单独停用的用户 | P1 | 已关闭 | Claude | [2026-11-03-SEC-005](tasks/2026-11-03-SEC-005-object-guards.md) | 2026-11-04 |
| [SEC-008](../07-security/03-全量平台与商品代码审查.md) | 管理员重置密码将新密码放入URL查询串 | P1 | 已关闭 | Claude | [2026-11-03-SEC-001](tasks/2026-11-03-SEC-001-session-ai-input-hotfix.md) | 2026-11-04 |
| [SEC-009](../01-business/05-全量业务代码审查.md) | 供应商单条对象入口缺少租户归属隔离 | P1 | 已关闭 | Claude | [2026-11-03-SEC-011](tasks/2026-11-03-SEC-011-tenant-isolation.md) | 2026-11-04 |
| [SEC-010](../07-security/03-全量平台与商品代码审查.md) | 配置与字典按ID维护缺少目标归属校验 | P1 | 已关闭 | Claude | [2026-11-03-SEC-011](tasks/2026-11-03-SEC-011-tenant-isolation.md) | 2026-11-04 |
| [SEC-011](../07-security/02-身份会话与对象权限.md) | 没有自动租户过滤，租户隔离全靠逐条手写条件 | P1 | 已关闭 | Claude | [2026-11-03-SEC-011](tasks/2026-11-03-SEC-011-tenant-isolation.md) | 2026-11-04 |
| [SEC-012](../07-security/02-身份会话与对象权限.md) | 用户启停接口不校验状态值，可借它删除用户或写入非法状态 | P3 | 已关闭 | Claude | [2026-11-04-ACCEPT](tasks/2026-11-04-ACCEPT-local-v171.md) | 2026-11-14 |
| [SEC-013](tasks/2026-11-14-SEC-005-post-remediation-review.md) | 退款申请按 ID 详情、备注和附件维护缺少负责人数据范围 | P1 | 已关闭 | Claude | [2026-11-14-BUS-038](tasks/2026-11-14-BUS-038-review-fixes.md) | 2026-11-15 |
| [SEC-014](tasks/2026-11-14-SEC-005-post-remediation-review.md) | 本地直传完成校验后仍能重写，业务认领信任旧校验结果 | P2 | 已关闭 | Claude | [2026-11-14-BUS-038](tasks/2026-11-14-BUS-038-review-fixes.md) | 2026-11-15 |
| [SEC-015](tasks/2026-11-14-BUS-038-review-fixes.md) | 退货入库单按 ID 详情对销售侧菜单用户未按单据负责人限定 | P2 | 已关闭 | Claude | [2026-11-14-BUS-038](tasks/2026-11-14-BUS-038-review-fixes.md) | 2026-11-15 |
| [SEC-016](tasks/2026-11-14-BUS-038-review-fixes.md) | 挂接未挂附件不核对上传人，可按 ID 挂上别人刚上传的文件再读取 | P2 | 已关闭 | Claude | [2026-11-15-SEC-017](tasks/2026-11-15-SEC-017-proactive-sweep.md) | 2026-11-15 |
| [SEC-017](tasks/2026-11-15-SEC-017-proactive-sweep.md) | 应付按来源、采购退货按采购订单、售后单按订单列出时未校验关联单据范围 | P2 | 已关闭 | Claude | [2026-11-15-SEC-017](tasks/2026-11-15-SEC-017-proactive-sweep.md) | 2026-11-15 |
| [SEC-018](tasks/2026-11-15-SEC-018-codegraph-sweep.md) | 操作日志、系统参数、角色与菜单授权的读取接口只要求登录 | P2 | 已关闭 | Claude | [2026-11-15-SEC-018](tasks/2026-11-15-SEC-018-codegraph-sweep.md) | 2026-11-15 |
| [SEC-019](tasks/2026-11-15-SEC-018-codegraph-sweep.md) | 用户列表接口向所有登录用户返回同事的手机号和邮箱 | P3 | 已关闭 | Claude | [2026-11-15-SEC-018](tasks/2026-11-15-SEC-018-codegraph-sweep.md) | 2026-11-15 |
| [SEC-020](tasks/2026-11-15-BUS-042-incremental-audit.md) | 登记售后单不校验订单和客户的数据范围，本人范围的用户可对别人的订单发起售后，进而看到单价、发起退货退款和补发 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [SEC-021](tasks/2026-11-15-BUS-042-incremental-audit.md) | 背调报告导出 PDF 时，正文里的 Markdown 图片会保留 http(s) 外链，交给 LibreOffice 转换，服务器可能被诱导访问内网地址 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [SEC-022](tasks/2026-11-15-BUS-042-incremental-audit.md) | 采购退货单只按负责人限定数据范围，采购订单的采购员看不到也处理不了别人去向处理生成的采购退货单 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [SEC-023](tasks/2026-11-15-BUS-042-incremental-audit.md) | 没有「查看货源信息」权限的用户，可以在开发任务时间线里看到供应商库名称 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [SEC-024](tasks/2026-11-16-BUS-084-batch2-review.md) | 没有客户管理菜单的用户可以读取公海客户的完整详情（含联系方式），公海列表本身要求客户管理菜单 | P3 | 已发布待验收 | Claude | [2026-11-16-BUS-084](tasks/2026-11-16-BUS-084-batch2-review.md) | 2026-11-16 |

## 运行与迁移

| ID / 原专题 | 简短标题 | 优先级 | 状态 | 负责人 | 任务记录 | 最后更新 |
| --- | --- | --- | --- | --- | --- | --- |
| [OPS-001](../04-ai-and-operations/03-全量运行脚本与配置审查.md) | dev-up缺少--ai参数值时参数循环不前进 | P2 | 已关闭 | Claude | [2026-11-12-OPS-001](tasks/2026-11-12-OPS-001-batch-j.md) | 2026-11-14 |
| [OPS-002](../04-ai-and-operations/03-全量运行脚本与配置审查.md) | 等待服务失败后仍以成功退出并打印全部就绪 | P2 | 已关闭 | Claude | [2026-11-12-OPS-001](tasks/2026-11-12-OPS-001-batch-j.md) | 2026-11-14 |
| [OPS-003](../04-ai-and-operations/04-部署与数据迁移补查.md) | 公司供应商同名合并遗漏旧编码映射，采购/询价失去供应商关联 | P2 | 暂缓 | Claude | [2026-11-12-OPS-001](tasks/2026-11-12-OPS-001-batch-j.md) | 2026-11-12 |
| [OPS-004](../04-ai-and-operations/04-部署与数据迁移补查.md) | 非空非法其他收入金额被当作无收入，清洗仍标为可迁移 | P2 | 暂缓 | Claude | [2026-11-12-OPS-001](tasks/2026-11-12-OPS-001-batch-j.md) | 2026-11-12 |
| [OPS-005](../04-ai-and-operations/04-部署与数据迁移补查.md) | 规格键去重与原始键碰撞，生成SQL会跳过一条规格值 | P2 | 暂缓 | Claude | [2026-11-12-OPS-001](tasks/2026-11-12-OPS-001-batch-j.md) | 2026-11-12 |
| [OPS-006](../04-ai-and-operations/04-部署与数据迁移补查.md) | Claude子进程ENOENT未转为Promise拒绝，批次失败统计无法接住 | P3 | 暂缓 | Claude | [2026-11-12-OPS-001](tasks/2026-11-12-OPS-001-batch-j.md) | 2026-11-12 |
| [OPS-007](../04-ai-and-operations/04-部署与数据迁移补查.md) | 历史迁移末尾核对异常使批次缺失持久化终态 | P2 | 暂缓 | Claude | [2026-11-12-OPS-001](tasks/2026-11-12-OPS-001-batch-j.md) | 2026-11-12 |
| [OPS-008](tasks/2026-11-15-BUS-042-incremental-audit.md) | 询价任务超时流转的定时任务整段跑在平台范围内，租户拦截器失效，导致「询价任务待分配」通知收件人配置跨租户混用、可能漏发 | P2 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [OPS-009](tasks/2026-11-15-BUS-042-incremental-audit.md) | 客户跟进参数迁移的回退 SQL 用错了键名前缀，执行后系统参数删不掉 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |
| [OPS-010](tasks/2026-11-15-BUS-042-incremental-audit.md) | 「已回价」分页接口每次都逐条组装本人全部已回价任务再切片，点「加载更多」后按页数成倍查询，每次保存回价也会触发 | P3 | 已合并待发布 | Claude | [2026-11-15-BUS-042](tasks/2026-11-15-BUS-042-incremental-audit.md) | 2026-11-15 |

## 依赖

| ID / 原专题 | 简短标题 | 优先级 | 状态 | 负责人 | 任务记录 | 最后更新 |
| --- | --- | --- | --- | --- | --- | --- |
| [DEP-001](../03-dependencies/01-security-findings.md) | POI 5.2.5 读取上传 OOXML，命中已确认公告 | P1 | 已关闭 | Claude | [2026-11-14-DEP-001](tasks/2026-11-14-DEP-001-p1-upgrades.md) | 2026-11-14 |
| [DEP-002](../03-dependencies/01-security-findings.md) | Tomcat 10.1.20 的 multipart 解析存在已公开 DoS 修复缺口 | P1 | 已关闭 | Claude | [2026-11-14-DEP-001](tasks/2026-11-14-DEP-001-p1-upgrades.md) | 2026-11-14 |
| [DEP-003](../03-dependencies/01-security-findings.md) | axios 0.27.2 被业务代码使用，却只由 dev 链间接提供 | P1 | 已关闭 | Claude | [2026-11-14-DEP-001](tasks/2026-11-14-DEP-001-p1-upgrades.md) | 2026-11-14 |
| [DEP-004](../03-dependencies/01-security-findings.md) | DOMPurify 3.4.2 位于生产 Markdown/Mermaid 链，需按实际模式验证 | P2 | 暂缓 | Ezio（决定） | [2026-11-04-DEP-001](tasks/2026-11-04-DEP-001-dependency-deferred.md) | 2026-11-04 |
| [DEP-005](../03-dependencies/01-security-findings.md) | Umi 开发与构建链累计漏洞，按启用能力整改 | P2 | 暂缓 | Ezio（决定） | [2026-11-04-DEP-001](tasks/2026-11-04-DEP-001-dependency-deferred.md) | 2026-11-04 |
| [DEP-006](../03-dependencies/01-security-findings.md) | 后端传递依赖与 Spring 条件型公告需随 BOM 更新核验 | P2 | 暂缓 | Ezio（决定） | [2026-11-04-DEP-001](tasks/2026-11-04-DEP-001-dependency-deferred.md) | 2026-11-04 |
| [DEP-007](../03-dependencies/02-upgrade-and-compatibility.md) | Boot 3.2.5 基线滞后，应将 BOM 与生态组合升级 | P1 | 暂缓 | Ezio（决定） | [2026-11-04-DEP-001](tasks/2026-11-04-DEP-001-dependency-deferred.md) | 2026-11-04 |
| [DEP-008](../03-dependencies/02-upgrade-and-compatibility.md) | Node >=20 声明不满足锁定工具链 | P2 | 暂缓 | Ezio（决定） | [2026-11-04-DEP-001](tasks/2026-11-04-DEP-001-dependency-deferred.md) | 2026-11-04 |
| [DEP-009](../03-dependencies/02-upgrade-and-compatibility.md) | Umi 升级必须和 @utoo/pack 本地补丁同步验收 | P2 | 暂缓 | Ezio（决定） | [2026-11-04-DEP-001](tasks/2026-11-04-DEP-001-dependency-deferred.md) | 2026-11-04 |
| [DEP-010](../03-dependencies/02-upgrade-and-compatibility.md) | Python 顶层精确 pin 仍不足以重现传递依赖与二进制 | P2 | 暂缓 | Ezio（决定） | [2026-11-04-DEP-001](tasks/2026-11-04-DEP-001-dependency-deferred.md) | 2026-11-04 |
| [DEP-011](../03-dependencies/02-upgrade-and-compatibility.md) | 镜像与供应链构建输入未完整固定 | P2 | 暂缓 | Ezio（决定） | [2026-11-04-DEP-001](tasks/2026-11-04-DEP-001-dependency-deferred.md) | 2026-11-04 |

## 文档与测试保障

| ID / 原专题 | 简短标题 | 优先级 | 状态 | 负责人 | 任务记录 | 最后更新 |
| --- | --- | --- | --- | --- | --- | --- |
| [DOC-001](../05-code-and-docs/01-文档与测试一致性.md) | 标为已确认的技术上下文与现行栈和时区规则冲突 | P2 | 已关闭 | Claude | [2026-11-12-UI-007](tasks/2026-11-12-UI-007-batch-l.md) | 2026-11-14 |
| [DOC-002](../05-code-and-docs/01-文档与测试一致性.md) | 部署注释与一次性迁移指引混入旧上线流程 | P3 | 已发布待验收 | Claude | [2026-11-12-UI-007](tasks/2026-11-12-UI-007-batch-l.md) | 2026-11-14 |
| [DOC-003](../05-code-and-docs/01-文档与测试一致性.md) | AI 任务注释仍描述未实现状态并引用已归档旧路径 | P3 | 已关闭 | Claude | [2026-11-12-UI-007](tasks/2026-11-12-UI-007-batch-l.md) | 2026-11-14 |
| [DOC-004](../05-code-and-docs/01-文档与测试一致性.md) | 种子迁移集成测试清空关联表但只恢复品类快照 | P2 | 已关闭 | Claude | [2026-11-03-DOC-005](tasks/2026-11-03-DOC-005-ci-pipeline.md) | 2026-11-04 |
| [DOC-005](../05-code-and-docs/01-文档与测试一致性.md) | 发布仓库缺少规范所要求的自动测试入口 | P2 | 已关闭 | Claude | [2026-11-03-DOC-005](tasks/2026-11-03-DOC-005-ci-pipeline.md) | 2026-11-04 |
| [DOC-006](../05-code-and-docs/01-文档与测试一致性.md) | 当前历史迁移规范与实现对入库、付款和源变化的约定不一致 | P2 | 暂缓 | Claude | [2026-11-12-OPS-001](tasks/2026-11-12-OPS-001-batch-j.md) | 2026-11-12 |
| [DOC-007](tasks/2026-11-03-SEC-011-tenant-isolation.md) | 售后时效测试两次取当前时间，跨秒时偶发失败 | P3 | 已关闭 | Claude | [2026-11-03-SEC-011](tasks/2026-11-03-SEC-011-tenant-isolation.md) | 2026-11-04 |
| [DOC-008](../05-code-and-docs/01-文档与测试一致性.md) | CI 后端测试失败被管道掩盖，失败用例已随版本合入 | P1 | 已关闭 | Claude | [2026-11-06-DOC-008](tasks/2026-11-06-DOC-008-ci-pipefail-speed.md) | 2026-11-06 |
