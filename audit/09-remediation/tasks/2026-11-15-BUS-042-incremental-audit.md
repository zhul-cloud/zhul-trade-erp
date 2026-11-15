# BUS-042 等：基线以来增量代码的全面审核（2026-11-15）

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（编排、复现与登记）；用户 2026-11-15 要求「做一次全面的审核」，选择增量范围、同意多代理并行、审出问题先登记再决定修复 |
| 关联 ID / 原专题 | 新增 79 个：BUS-042、AI-012、BUS-043、BUS-044、BUS-045、BUS-046、BUS-047、BUS-048、BUS-049、BUS-050、BUS-051、OPS-008、SEC-020、SEC-021、SEC-022、UI-060、UI-061、UI-062、UI-063、AI-013、BUS-052、BUS-053、BUS-054、BUS-055、BUS-056、BUS-057、BUS-058、BUS-059、BUS-060、BUS-061、BUS-062、BUS-063、BUS-064、BUS-065、BUS-066、BUS-067、BUS-068、BUS-069、BUS-070、BUS-071、BUS-072、BUS-073、BUS-074、BUS-075、BUS-076、BUS-077、OPS-009、OPS-010、SEC-023、UI-064、UI-065、UI-066、UI-067、UI-068、UI-069、UI-070、UI-071、UI-072、UI-073、UI-074、UI-075、UI-076、UI-077、UI-078、UI-079、UI-080、UI-081、UI-082、UI-083、UI-084、UI-085、UI-086、UI-087、UI-088、UI-089、UI-090、UI-091、UI-092、UI-093；修复不完整、重开：BUS-033、BUS-035、SEC-004、UI-004、UI-005、UI-055、UI-056、UI-058 |
| 目标与影响范围 | 11-01 审查基线 `09da3dc3` 到 develop `c3af0644` 之间新增或改动的业务代码：后端 `src/main`、前端 `src`、`scripts`、数据库迁移，约 1150 个文件、14.5 万行；不含已在基线审过且未改动的代码 |
| 起点 commit / 工作分支 | develop `c3af0644`（含 v1.25.0）/ 只改 `audit/`：`docs/audit-incremental-review` |
| OpenSpec | 缺陷登记，不新开提案 |
| PR / 目标分支 | 本记录随文档 PR 合入 develop；修复 PR 未创建 |
| 合并证据 | 未修复 |
| 目标版本 / 环境 | 待定 |

## 审核方法与覆盖

按业务域把前后端放在同一单元，共 14 个单元：销售订单与 PI、售后与销售退货（含客户跟进前端）、CRM、供应链后端、供应链前端、财务后端、财务前端、主数据后端、客户与供应商前端、询价与报价、采购仓储物流、平台与通用（系统、上传、附件、认证、租户、AI 任务、单据模板、框架与前端公共代码）、商品与 AI 编排服务、数据库迁移与运行脚本。

- **审查**：每个单元一个审核代理，用 `git diff 09da3dc3 c3af0644` 列出改动文件，新文件读全文、改动文件读 diff 与上下文，用 CodeGraph 追调用链；从四个角度找缺陷：资金 / 库存 / 状态机 / 并发，权限 / 租户 / 数据范围，前端交互规范，AI 与运维。报告前对照本台账查重。
- **反证复核**：每个单元一个复核代理，逐条打开代码尝试推翻（上游校验、事务、条件更新、权限、业务可达性），并判断与已有 ID 是否重复。
- **查漏**：一个代理专门找跨模块联动与单元边界外的改动；它的 4 条发现与第一轮 2 条「无法判断」再各由一个复核代理独立复核。
- **规模**：共 35 个代理（29 + 6），全部完成，无失败；报告 98 条，推翻 1 条，重复或修复不完整 17 条（含合并的 2 条），同一问题被两个单元重复报告 1 条，待业务确认 1 条。
- **本机复现**：P1（BUS-042）在真实 MySQL 上复现；其余为两次独立代码核对的静态确认，修复时须先补缺陷见证。审核全程只读，没有修改代码、数据库或启动服务。

新增问题：P1 1 个、P2 18 个、P3 60 个；按类别 AI 2、BUS 36、OPS 3、SEC 4、UI 34。

## 新增问题：P1 与 P2

### BUS-042 · P1 · 作废余额提现后，释放流水被当成新的余额批次，客户可用余额翻倍，可超额提现或抵扣，余额变成负数

- **位置**：`zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/CustomerRefundService.java:200-205`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/CustomerCreditLedger.java:124-147`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/CustomerCreditLedger.java:229-240`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/CustomerCreditLedger.java:81-95`
- **触发**：1）客户余额账户有一笔转入，例如代收转入 USD 291。2）余额管理里「转给客户」USD 291，生成余额提现退款单，余额变为 0。3）在退款管理作废这张提现退款单，余额显示恢复为 291。4）再「转给客户」USD 582，或者在新 PI 上抵扣余额 582。
- **应当**：作废提现后，只按消耗明细把原批次恢复，可用余额 = 291；再转出 582 应提示「可用余额只有 USD 291.00」。
- **实际**：voidRefund 对余额提现调用 `ledger.release(e, FinanceConstants.CREDIT_REVERSE, ...)`，类型传的是冲销（8），不是抵扣释放（5）。release 一边把消耗明细标为 released=1，让原批次的剩余恢复到 291；一边按传入类型 insert 了一条 +291 的流水。而 lots() 只排除 RELEASE：`accountWrapper(a).gt(amount, 0).ne(entryType, FinanceConstants.CREDIT_RELEASE)`，这条 +291、类型 8 的流水于是也被当作一个新批次。consume() 按批次剩余算可用额：`available = lots...remaining 求和`，结果是 582，而流水之和（列表显示的余额）只有 291。CustomerCreditService.withdraw 和 PI 抵扣都直接调用 ledger.consume，没有另按流水之和做校验，所以可以转出 582。转出后余额为 -291，余额提现退款单和汇兑损益也按虚增的价值生成。
- **影响**：客户余额可以被多转出或多抵扣一倍，属于真实的资金损失；余额核对表的期末出现负数，汇兑损益和订单收入都会失真。现有 CustomerCreditContractTest 只断言了作废后余额 = 291，没覆盖作废后再消费的情况，所以测试照样通过。
- **复核**：已逐段核对源码，结论成立。CustomerRefundService.voidRefund:204 对余额提现调用 ledger.release(e, FinanceConstants.CREDIT_REVERSE(=8), ...)。release(CustomerCreditLedger:126-147) 做了两件事：一是按消耗明细插入正数流水，类型用的是传入的 8，并把 related_entry_id 指向提现流水；二是把这些消耗明细标为 released=1，于是原批次的剩余恢复为 291。lots()(:229-231) 只用 .ne(entryType, CREDIT_RELEASE=5) 排除释放流水，类型 8、金额 +291 的这条流水因此被当成一个新批次，consume():91 算出的 available 等于两个批次剩余之和，即 582。而 balance()（流水之和）只有 291。withdraw(CustomerCreditService:478) 和 PI/手动订单抵扣都直接调用 ledger.consume，不另按 balance 校验；只有负数调整路径(:345) 用了 balance。其余三处 release 调用（CreditUsageService:99/111/165）传的都是 CREDIT_RELEASE，没有这个问题，只有提现作废这一
- **本机复现**：在 `CustomerCreditContractTest` 的代收转入余额场景末尾临时加断言（未提交）：余额 USD 291 时转给客户 291、作废这张提现退款单后，再转出 582 被接受（code=0），余额变为 −291；流水为 `entry_type=2 +291`、`6 −291`、`8 +291`、`6 −582`。原因是作废提现按冲销类型（8）写回 +291，`lots()` 只排除释放类型（5），这笔流水被当成新批次，同时原批次的消耗也被释放。该逻辑已在生产运行，作废过余额提现的客户可能已有虚增余额。
- **审核单元**：财务后端

### AI-012 · P2 · 跟进整理的阶段答案字段在发给模型的 Schema 里不能为空，原文没提到也会被迫给出答案

- **位置**：`scripts/ai-orchestrator/server.py:918`、`scripts/ai-orchestrator/server.py:462`、`scripts/ai-orchestrator/server.py:1008`、`scripts/ai-orchestrator/skills/followup-extract/SKILL.md:52`
- **触发**：订单履约 / 售后阶段（A1、A2、A5、A6）的跟进原文没有提到收货、使用、满意度或评价情况，用 API 方式（Claude 或 OpenAI）或 Codex 订阅做「AI 整理」。
- **应当**：skill 规定这些字段「原文看不出来就不输出」，模型应当能返回空值。
- **实际**：`receiptAnswer/useAnswer/satisfaction/reviewStatus` 定义为 `{"type": "integer", "enum": [...]}`；`_required_nullable_schema` 把它们改成必填、`type: ["integer","null"]`，但 enum 里没有 null。按 JSON Schema 语义，null 不满足 enum，严格结构化输出只能在 1/2/3（或评价状态值）里选一个，`stage_answers` 再把这个值当合法建议返回。
- **影响**：界面会出现原文没有依据的「已收到无问题」「满意」等建议答案，业务员确认时可能被误导，写入错误的履约或售后状态。
- **复核**：server.py:918-922 的 receiptAnswer/useAnswer/satisfaction/reviewStatus 是带 enum 的可选 integer。_required_nullable_schema(462-481) 只把 type 改成 [type,'null'] 并设为必填，enum 里没有加 null。按 JSON Schema 语义，enum 是独立约束，null 不满足。走这个变换的有三条路径：_call_claude(419)、_call_openai(504)，以及 provider=openai 的订阅（400，Codex）。严格结构化输出下，模型只能从枚举值里选一个，sanitize（约 997-1001）会把 1/2/3 当合法值保留，和 SKILL.md:52/68「原文看不出来就不输出」矛盾。本机没有 jsonschema 包，也没有调用模型，结论是静态确认；按规范语义证据充分。Claude 订阅用的是原 schema，不受影响。影响面覆盖大部分配置下的 A1/A2/A5/A6 阶段，属于功能错误。
- **证据**：静态确认：审核代理与反证复核代理分别核对源码；复现思路：只做了静态确认：`python -c` 打印 `_required_nullable_schema(FOLLOWUP_EXTRACT_SCHEMA)` 可见 enum 不含 null。要实测，需要用已登录的订阅或 API Key，拿一段与收货无关的 A1 阶段原文调用 followup-extract，观察 receiptAnswer 是否总有值（本次任务不允许调用模型，未执行）。
- **审核单元**：商品与 AI 编排

### BUS-043 · P2 · 售后单有未取消的补发订单时仍可撤销，0 元补发订单和采购需求继续执行，售后单变只读无法再结案

- **位置**：`zhul-erp-backend/src/main/java/com/zhul/erp/modules/aftersales/service/AfterSalesService.java:374`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/aftersales/service/AfterSalesService.java:381`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/aftersales/support/ReplacementOrders.java:190`、`openspec/specs/sales/after-sales/spec.md:56`
- **触发**：1）售后单关联一张已出运的订单，在详情点「补发」，生成补发订单 SO…（进度为已下单，未取消）；2）同一售后单点「撤销」并填写原因。
- **应当**：按 spec「结案与撤销」，有补发订单（取消的不算）时不能撤销，提示先取消补发订单。
- **实际**：cancel() 只检查了销售退货单、退款申请、费用单和退款单，没有检查补发订单：`if (returnsOf(id)...) ... if (requestsOf(id)...) ... if (!expensesOf(List.of(id)).isEmpty() ｜｜ !refundsOf(id).isEmpty())`，随后直接 `a.setStatus(AfterSalesConstants.AS_CANCELLED)`。ReplacementOrders 提供了 of() 和 unshipped()，但只在 close() 里用到（line 341）。
- **影响**：已撤销的售后单只读，补发订单却仍是有效订单，照常生成采购需求、发货。结案时「补发未出运不能结案」的校验被绕过，补发的成本挂在一张已撤销的售后单上，售后成本汇总和追溯都失真。
- **复核**：AfterSalesService.cancel()（约 381-397 行）只拦销售退货单、退款申请、费用单和退款单，之后直接 setStatus(AS_CANCELLED)，没有调用 replacementOrders.of() 或 unshipped()。补发订单检查只在 close() 里有（replacementOrders.unshipped）。spec 的「结案与撤销」写明「补发订单（取消的不算）」时不能撤销。前端 detail.tsx 在 active 状态下无条件显示「撤销」按钮，AfterSalesReplacementContractTest 也没有覆盖撤销售后单的场景，上游没有任何兜底。撤销之后，0 元补发订单和采购需求仍然有效，结案时的未出运检查被绕过。
- **证据**：静态确认：审核代理与反证复核代理分别核对源码；复现思路：本机造一张已出运的手动订单，按 AfterSalesReplacementContractTest 的方式：POST /api/v1/sales/after-sales 登记售后单 → POST /{id}/replacement 补发 1 个型号 → POST /{id}/cancel {"reason":"测试"}。应当被拒绝，实际返回 200、状态为已撤销，补发订单仍为进行中。目前是静态确认，未实际运行。
- **审核单元**：售后、销售退货与客户跟进前端

### BUS-044 · P2 · 取消销售订单不检查进行中的售后单、销售退货、退款申请、费用订单和暂存货物（删除时都会挡），取消或 PI 重转后，售后单仍能对已取消订单补发，退款申请撤不回也执行不了

- **位置**：`zhul-erp-backend/src/main/java/com/zhul/erp/modules/sales/service/impl/SalesOrderServiceImpl.java:648`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/sales/service/impl/SalesOrderServiceImpl.java:693`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/sales/support/OrderDeleteCheck.java:100`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/aftersales/support/ReplacementOrders.java:48`
- **触发**：订单 A 已出运（出库单已交货代），且有一张处理中的售后单，售后单下有「待财务处理」的退款申请。业务员取消订单 A，例如为了改 PI 后重转成订单 B。cancel 只拦未交货代的出库单和已完成状态，所以能取消成功。之后在这张售后单上继续操作。
- **应当**：取消订单前应像 OrderDeleteCheck 那样，列出并拦住未撤销的售后单、未作废的销售退货、未撤回的退款申请、有效的费用订单和暂存中的货物，或者把它们一并处理或改挂到新订单。否则这些单据会挂在已取消的订单上，无法继续处理。
- **实际**：cancel() 只调 logisticsQty.inWarehouseObNos，releaseLinks 只处理商品候选、采购需求、余额抵扣、PI 和报价成交，售后侧什么都没做。取消后：(1) ReplacementOrders.create 只判断 order.getDeletedAt() != null，没有判断订单状态，所以售后单仍能对已取消的 A 生成 0 元补发订单和采购需求，成本挂在已取消的原订单上，利润统计按 SO_ACTIVE 过滤，看不到这部分成本。(2) RefundRequestService.withdraw 调 visibleOrder，订单不是 SO_ACTIVE 时抛「订单已取消，不能申请退款」，申请人撤不回自己的申请；财务执行时 CustomerRefundService.requireOrder 也会拒绝（「订单已取消，不能登记退款」），只能由财务驳回。售后单结案又要求退款申请处理完，所以在财务驳回前一直结不了案。(3) PI 重转后，RequirementLifecycle.moveHandedOutbound 把已交货代的出库明细改挂到 B 的型号行。A 的已出运数变成 0，售后单再发起退货时 buildItems 算出可退数量为 0；退货退款又因为 refundable(A) 抛异常而做不了。
- **影响**：取消或重转订单后，原订单上的售后流程处理不下去，只能在新订单上重新登记售后，原记录和附件留在旧单上。补发成本落到已取消的订单，从利润里消失。待处理的退款申请卡在财务队列里，申请人自己撤不回。
- **复核**：核对 c3af0644 源码，结论如下。 1）SalesOrderServiceImpl.cancel（646-688 行）只拦三种情况：非 SO_ACTIVE、PROGRESS_COMPLETED、logisticsQty.inWarehouseObNos（仍在仓库的出库单，BUS-027）。它不查售后单、销售退货、退款申请、费用订单和暂存货物。releaseLinks 只处理商品候选、采购需求、余额抵扣、PI 和报价成交。相比之下，delete 走的 OrderDeleteCheck.check（100-130 行）会把以上几类单据都列为 blockers，删除和取消两条路的检查确实不一致。按 BUS-027 的注释和 sales-order 规格，已交货代、已出运的订单设计上允许取消后重转，所以「订单已出运并有售后时取消」在业务上是走得到的，不属于不可达场景。 2）补发：AfterSalesService.replace（360 行）先调 lockEditable，它只校验售后单状态；接着调 ReplacementOrders.create（48-52 行），它只判断 deletedAt，不看 SO_ACTIVE。售后单登记时（create 133 行）会拦已取消订单，补发却不拦，前端 after-sales/detail.tsx 的补发按钮也没有按订单状态禁用。spec f
- **证据**：静态确认：审核代理与反证复核代理分别核对源码；复现思路：本机：(1) 建 PI、收款、转订单 A，打包出库并交货代，让 A 有已出运数量；(2) 在 A 上登记售后单并申请退款（直接到「待财务处理」）；(3) 调取消订单接口，填原因，返回成功；(4) 调退款申请撤回接口，报「订单已取消，不能申请退款」；(5) 调售后补发接口，成功生成 original_so_id = A 的补发订单；(6) 重转 PI 得到订单 B 后，在该售后单上发起退货，报可退数量为 0。
- **审核单元**：查漏

### BUS-045 · P2 · 并发「转商机」会给同一客户建出两条进行中商机，商机编号也可能相同

- **位置**：`zhul-erp-backend/src/main/java/com/zhul/erp/modules/crm/followup/service/FollowupDevelopService.java:122`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/crm/followup/service/FollowupDevelopService.java:132`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/crm/followup/service/FollowupDevelopService.java:133`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/crm/followup/support/DevelopFollowupSync.java:132`
- **触发**：对同一个开发对象，两个请求几乎同时调用 POST /api/v1/followups/items/{id}/convert，例如两个标签页，或者负责人和主管同时点「转商机」。
- **应当**：一个客户最多只有一条进行中商机：后到的请求应看到「开发对象已结束」或 OPPORTUNITY_ALREADY_ACTIVE 并回滚。
- **实际**：convert 用普通 selectById 读开发记录（requireOpen），然后 developSync.close 做无条件的 `itemMapper.updateById(row)`。后到的事务会在行锁上等前一个提交，之后照样更新成功，不再检查 closed_at。createForCustomer 用普通 selectOne 查进行中商机，读的是事务开始时的快照，看不到前一个事务刚插入的商机，于是又执行一次 `opportunityMapper.insert(o)`。nextCode 同样基于快照取最大序号，opportunity 表在 opportunity_code 上只有普通索引、没有唯一键，所以两条商机可能拿到同一个编号。
- **影响**：同一客户有两条进行中商机：统计（新商机、有效率）多算一条；activeOf 和 activeOpportunity 只认 ID 最大的那条，另一条成为没人跟进的孤儿商机；还会写出两条「转为商机」系统记录。
- **复核**：FollowupDevelopService.convert（第123行起）里，requireOpen（第285行）用普通 selectById 检查 closed_at；DevelopFollowupSync.close（第132行）做无条件 updateById，没有带 closed_at IS NULL 条件，也没有 @Version。OpportunityServiceImpl.createForCustomer（第153行起）没有给客户加锁，只用普通 selectOne 检查进行中商机。在 InnoDB 默认的可重复读隔离级别下，后到的事务快照在第一次读时就已确定：它会在 updateById 处等前一个事务的行锁，拿到锁后照样更新成功，后续快照读看不到先到事务新插入的商机，于是插入第二条。nextCode（第775行）用快照取最大序号，opportunity 表在 opportunity_code 上只有普通索引 idx_opportunity_code，没有唯一约束，编号可能相同。同一文件的 changeStage、close 都用 lockedInScope 加锁，convert 没有。台账里没有对应 ID（BUS-013 是编辑覆盖阶段，不是同一问题）。只有两人或两个标签页同时转同一个开发对象才会触发，频率低，但会造成数据不一致。
- **证据**：静态确认：审核代理与反证复核代理分别核对源码；复现思路：静态确认。本机复现方式：在 createForCustomer 的 insert 前加断点（或在测试里用两个线程、CountDownLatch 卡住第一个事务），并发调用两次 convert，然后查 `SELECT id, opportunity_code FROM opportunity WHERE customer_id=? AND closed_at IS NULL`，会得到 2 行。
- **审核单元**：CRM

### BUS-046 · P2 · 上线补齐自动赢单的迁移按「客户曾经有过收款」批量赢单：老客户在最后一次收款之后新开的、还没有任何收款的商机也被标成赢单

- **位置**：`zhul-erp-backend/src/main/resources/db/migration/V1.5.8__opportunity_auto_win_backfill.sql:16`、`zhul-erp-backend/src/main/resources/db/migration/V1.5.8__opportunity_auto_win_backfill.sql:21`、`zhul-erp-backend/src/main/resources/db/migration/V1.5.8__opportunity_auto_win_backfill.sql:34`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/crm/followup/support/FollowupHooks.java:142`
- **触发**：老客户 C 的旧 PI 在 2026-06 已到账（received_amount > 0），旧商机当时已手动关闭。之后业务员为 C 的新需求又开了一条商机 O2，停在 S2，这笔新生意还没有任何收款。V1.5.8 上线执行时，O2 被选进 tmp_followup_auto_win：先补一条推进到 S7 的记录，再赢单。
- **应当**：规格 crm/opportunity「收款到账自动赢单」要求的是收款确认到账这个事件发生时，把客户当时进行中的商机赢单。补齐数据应当只覆盖这种情况：商机在某次收款确认之前就已存在且处于进行中，比如要求 o.create_time 早于该客户最近一次有效到账（payment_receipt kind=2、status=1）的时间。已作废 PI（status=4）上残留的已收金额也不应算数。
- **实际**：选取条件只看客户名下有没有任何一张 PI 已收过钱，不比较商机创建时间和收款时间，也不排除已作废的 PI：`AND EXISTS (SELECT 1 FROM proforma_invoice p WHERE p.tenant_id = o.tenant_id AND p.customer_id = o.customer_id AND p.deleted_at IS NULL AND p.received_amount > 0)`。随后对选中的商机统一执行 `SET o.stage_code = 'WON', o.closed_at = NOW(), o.reached_valid = 1 ...`，并写入「上线补齐：已有收款，自动赢单」的阶段记录。运行时 FollowupHooks.receiptConfirmed 只在收款到账那一刻调用 autoWin，两者的口径不一致。
- **影响**：有复购的老客户，上线时正在跟进的新商机被直接标为赢单，从客户跟进的「商机成交」板块消失，赢单率和转化统计虚高，业务员看不到这笔新生意的跟进提醒。阶段记录只增不改，要人工逐条找出来再重新打开。该迁移已随版本执行，属于存量数据错误，需要补一个数据修正。
- **复核**：V1.5.8__opportunity_auto_win_backfill.sql 第16-23行只按客户名下是否有任何未删 PI 的 received_amount>0 选商机（opportunity_stage.category=1，即进行中），不比较商机创建时间和收款时间，也不看 PI 状态；第34-45行统一改成 WON。运行时 FollowupHooks.receiptConfirmed（第141-151行）只在收款确认这一刻调用 OpportunityServiceImpl.autoWin（第646行起），规格 openspec/specs/crm/opportunity/spec.md 第200行「收款到账自动赢单」也是按事件定义的。触发场景可以走到：设计说明上线前 S7 商机靠手动赢单，旧商机结束后，规格「从主动开发转商机」允许给已有客户新开商机（同一时间只能有一条进行中），所以有复购的老客户会有一条在最后一次收款之后才建的进行中商机。迁移会把它标成赢单，同时补一条推进到 S7 的阶段记录。能削弱这条发现的只有一点：design.md 第205行写的缓解办法是给「已有确认收款、但商机仍未赢单」的客户补赢单，按字面读和 SQL 一致，但它针对的是「S7 商机原本靠手动赢单」，并没有说要把收款之后才开的新商机也标成赢单，所以按设计意图仍算范围扩大。这个迁移在提交 89b
- **证据**：静态确认：审核代理与反证复核代理分别核对源码；复现思路：只能静态确认（本次只读、不改数据库）。本机复现：给同一客户建一张 PI 并确认到账；之后再建一条 S2 商机（create_time 晚于到账时间）；在执行 V1.5.8 之前的库上运行该迁移，商机变为 WON。线上排查 SQL：查 opportunity_stage_log 中 note='上线补齐：已有收款，自动赢单' 的商机，关联 payment_receipt（kind=2、status=1、deleted_at IS NULL），按客户取 MAX(create_time)，筛出商机 create_time 大于该值的记录。
- **审核单元**：数据库迁移与脚本

### BUS-047 · P2 · 合并客户时搬迁字段来源会撞唯一键：保留客户已标「已问、等回复」或清空过的字段，被合并客户有同字段来源时合并报系统错误、无法完成

- **位置**：`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerprofile/service/CustomerMergeService.java:255-264`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerprofile/support/FieldSources.java:294-302`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerprofile/support/FieldSources.java:261-286`、`zhul-erp-backend/src/main/resources/db/migration/V1.4.7__customer_profile.sql（customer_field_source 的 UNIQUE KEY uk_customer_field (customer_id, field_key)）`
- **触发**：保留客户 A 的某个画像字段为空，但 customer_field_source 里已有该字段的行。常见来源有两种：补全向导对「只能问客户」的字段点了「已经问了」（markAsked 在值仍为空时就插入来源行），或者在画像里把该字段清空过（record 会写来源行）。被合并客户 B 的同一字段有值，而且有来源行（在画像里改过或采纳过背调）。这时执行「B 并入 A」。
- **应当**：A 的空字段用 B 的值补上，来源照搬，A 原有的「已问」行被覆盖或清掉，合并成功。
- **实际**：merge 先把 key 加进 filledKeys，再调用 sources.moveMissing(source, target, filledKeys)，执行的是 `UPDATE customer_field_source SET customer_id = A WHERE customer_id = B AND field_key IN (...)`。A 已有 (A, field_key) 行，违反 uk_customer_field，抛出 DuplicateKeyException，整个合并回滚，前端显示系统错误。代码在搬迁前没有删除或合并 A 已有的同键来源行。
- **影响**：疑似重复的客户往往两边都有人在补档案，这种情况下这两个客户永远合并不了，报错又没有说明原因。业务员只能先手工把 A 的该字段填上才能绕过去。
- **复核**：CustomerMergeService.merge（约 255-264 行）先把 target 为空、source 有值的键加入 filledKeys，再调 FieldSources.moveMissing，后者（FieldSources.java 约 100-108 行，报告引用的行号不准，但内容一致）直接执行 UPDATE customer_field_source SET customer_id=target WHERE customer_id=source AND field_key IN (...)，搬迁前没有删除或合并 target 已有的同键行。V1.4.7 中 UNIQUE KEY uk_customer_field(customer_id, field_key) 一直存在，V1.5.5 只加了 followup_id 列，没有改这个唯一键。target 字段为空但已有来源行，这在业务里可以走到：markAsked(asked=true) 在值为空时就插入一行 source=MANUAL；CustomerProfileService.update 把字段清空时，changed 包含该键，sources.record 也会写一行。两种情况下合并都会触发 DuplicateKeyException，整个事务回滚。没有上游校验或兜底。
- **证据**：静态确认：审核代理与反证复核代理分别核对源码；复现思路：在本机同一租户建客户 A、B（同负责人）。1）PUT /api/v1/masterdata/customers/{B}/profile，body {fields:["customsAbility"], customsAbility:<启用的字典码>}；2）POST /api/v1/masterdata/customers/{A}/profile/asked，body {keys:["customsAbility"], asked:true}；3）POST /api/v1/masterdata/customers/merge，body {targetId:A, sourceId:B}，返回系统错
- **审核单元**：主数据后端

### BUS-048 · P2 · 客户合并不改挂跟进记录、年度计划和背调报告，被合并客户的跟进历史与背调在保留客户下看不到；照搬过来的「背调报告」来源打开时报不存在

- **位置**：`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerprofile/service/CustomerMergeService.java:52-55`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerprofile/service/CustomerMergeService.java:239-264`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerbackground/service/CustomerBackgroundService.java:339-347`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerprofile/service/CustomerProfileService.java:432-446`
- **触发**：被合并客户 B 有业务员跟进记录（customer_followup）、战略客户年度计划（customer_annual_plan）或背调报告（customer_background_report），并且 B 有字段是从背调报告采纳的，而保留客户 A 的这个字段为空。执行「B 并入 A」后，在 A 的详情里查看跟进历史和背调，再把鼠标移到这个字段的来源上点开报告。
- **应当**：被合并客户的跟进历史、年度计划、背调报告随客户一起归到保留客户。至少照搬过来的「背调报告 BGxxx」来源要能打开。
- **实际**：DOCUMENTS 只列了商机、询盘、报价、PI、订单、出库、出运、售后、单证主体，没有包括 V1.5.5 及之后新增的 customer_followup、customer_annual_plan，也没有包括 customer_background_report。这些记录仍挂在已软删除的 B 上。FieldSources.moveMissing 把 report_id 指向 B 报告的来源行搬给了 A，A 的画像据此显示 reportNo。但打开报告要走 require(A, reportId)，其中 `!Objects.equals(r.getCustomerId(), c.getId())` 判断成立，于是抛出「背调报告不存在或已删除」。
- **影响**：合并后，被合并客户的跟进历史、年度计划、背调结论在两个客户下都看不到（B 已删除，A 名下没有）。字段来源的链接是坏的。
- **复核**：DOCUMENTS（CustomerMergeService 52-55 行）只包含商机、询盘、报价、PI、销售订单、出库、出运、售后、单证主体，不含 customer_followup（V1.5.5）、customer_annual_plan（V1.5.11）和 customer_background_report（V1.4.8），这三张表都有 customer_id 列，代码中也没有合并事件或其他地方改挂它们。FieldSources.moveMissing 会把带 report_id 或 followup_id 的来源行搬到 A；CustomerProfileService 组装来源时用 selectBatchIds 按 ID 取报告编号，不核对报告属于哪个客户，所以 A 上能显示 reportNo。前端 profile/ui.tsx:159 跳转到 /customer/list/{A}/background/{reportId}，CustomerBackgroundService.require（339-347 行）判断 r.customerId != A，抛出 NOT_FOUND。合并后 B 的跟进历史、年度计划和背调报告在界面上都看不到，属于功能错误，所以优先级上调到 P2。规格的合并清单早于这几张表出现，是规格的遗漏。
- **证据**：静态确认：审核代理与反证复核代理分别核对源码；复现思路：本机给 B 记一次跟进、发起或上传一份背调并采纳一个 A 为空的字段，然后 POST /api/v1/masterdata/customers/merge {targetId:A, sourceId:B}。查询 A 的跟进记录和 GET /customers/{A}/background-reports，都不包含 B 的数据。GET /customers/{A}/profile 的 sources 里该字段带 reportId，再 GET /customers/{A}/background-reports/{reportId}，返回「背调报告不存在或已删除」。
- **审核单元**：主数据后端

### BUS-049 · P2 · 订单部分取消或改费用后，收款侧剩余金额和待确认工作台仍按 PI 原合计算，手续费差额登记不了、已付清的 PI 继续挂在待处理里

- **位置**：`zhul-erp-backend/src/main/java/com/zhul/erp/modules/sales/service/impl/PaymentReceiptServiceImpl.java:906`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/sales/service/impl/PaymentReceiptServiceImpl.java:338`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/sales/service/impl/PaymentReceiptServiceImpl.java:795`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/sales/service/impl/PaymentReceiptServiceImpl.java:717`
- **触发**：场景 A（规格「PI 转成的订单出货时补运费」）：PI 合计 USD 2,000.00 已全额到账并转成订单，有修改订单费用权限的人加 Shipping Cost USD 120.00，订单合计变为 2,120.00，PI 收款状态变为部分到账。客户付 120，银行扣 5，财务在 PI 上确认到账 115 并勾选「差额记为手续费」。场景 B：PI 合计 1,000.00 只到账 750，订单部分取消后合计变为 750，PI 已按订单合计算成已到账。
- **应当**：本次变更新增了 PiStore.receivable()：有有效订单时取订单合计，PiSummary 和 ReceiptPosition 都改用它算状态与多收。确认到账的剩余金额、手续费差额判断、待确认工作台的筛选与剩余，以及 PI 详情的 remainingAmount，都应该按同一应收合计计算。
- **实际**：Owner.remaining() 仍是 `pi.getTotalAmount().subtract(pi.getReceivedAmount()).subtract(pi.getFeeDiffAmount())`。DeskRow.total() 返回 `pi.getTotalAmount()`，工作台按 `r.received().add(r.feeDiff()).compareTo(r.total()) < 0` 筛选。PiServiceImpl 第 855 行 `vo.setRemainingAmount(nz(pi.getTotalAmount())...)` 也是 PI 合计，前端 ConfirmReceiptModal 拿这个值显示「剩余」并计算 after。场景 A：after = 2000-2000-115 = -115，抛出「本次到账后没有剩余差额，不需要记为手续费」，PI 停在 2115/2120 的部分到账，无法结清。场景 B：PI 状态已是已到账，但工作台按 750 < 1000 把它列为待处理，剩余显示 250；PI 详情和确认弹窗也显示剩余 250。
- **影响**：规格里明确的两个场景（补运费、部分取消）中，财务无法记手续费差额，订单一直挂在部分到账；工作台和 PI 页面显示的剩余应收与收款状态矛盾，可能导致财务向客户多催款，或者重复登记。
- **复核**：PiSummary.refresh 用 store.receivable(pi) 算状态（PiSummary.java:49），PiStore.receivable 有有效订单时取订单合计（PiStore.java:90-93）。但收款侧还有三处用 PI 合计：PaymentReceiptServiceImpl.Owner.remaining()（906-908）、doConfirm 的 after/手续费判断（338-349）、DeskRow.total()（795-797）和工作台筛选与剩余（716-717、755）；PiServiceImpl:855 的 remainingAmount 也是 PI 合计，前端 ConfirmReceiptModal 拿它算 after/canFee（pi/dialogs.tsx:798-800）。updateFees 不改 PI 合计，对 PI 订单只调 piSummary.refresh(pi)（SalesOrderServiceImpl:576-577）。所以场景 A 中 after=2000-2000-115<0，后端报「没有剩余差额」，前端还会提示「多收 115 记为预收」；场景 B 中工作台和 PI 详情仍显示剩余 250。正式规格 payment-receipt「订单部分取消后的收款」要求收款进度按订单合计算，sales-orde
- **证据**：静态确认：审核代理与反证复核代理分别核对源码；复现思路：本机：按 PI 转单造一张已全额到账的 PI 订单，调 PUT /api/v1/sales/orders/{id}/fees 加一行费用，再在 PI 上 POST 确认到账，金额 = 新增费用 − 5，feeDiff=true，会返回「没有剩余差额」。另一条：调 POST /api/v1/sales/orders/{id}/partial-cancel 后，查 POST /api/v1/sales/pis/receipt-desk（all=false）和 PI 详情的 remainingAmount。
- **审核单元**：销售订单与 PI

### BUS-050 · P2 · 采购退货单的退款按创建时冻结的币种计入，采购订单改币种后应付冲减与成本冲减币种不一致

- **位置**：`zhul-erp-backend/src/main/java/com/zhul/erp/modules/purchase/purchasereturn/service/PurchaseReturnService.java:241`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/purchase/purchasereturn/service/PurchaseReturnService.java:357`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/PayableSyncService.java:91`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/PayableSyncService.java:396`
- **触发**：1）已下单的 USD 采购订单（汇率 7.1）还没有付款；2）售后退货去向选「退回供应商」，生成采购退货单（currencyCode=USD，exchangeRate=7.1）；3）采购员把采购订单币种改成 CNY 并保存。因为没有付款，hasMoneyMovement 为 false，保存放行；4）采购退货单完成，填退款或抵扣 100（界面后缀仍显示「USD · 采购订单币种」）。完成后再改币种也会出现同样问题。
- **应当**：采购退货单的退款或抵扣应与采购订单当前币种一致；或者采购订单已有采购退货单（待处理或已完成）时，禁止修改币种。
- **实际**：newReturn 按创建时的采购订单冻结币种和汇率：p.setCurrencyCode(po...getCurrencyCode()); p.setExchangeRate(po.getExchangeRate())。complete 时 refundCny = refundAmount × 冻结汇率。PayableSyncService.returnCreditOf 不看币种，直接累加 refundAmount，再从采购订单当前币种的 totalAmount 里扣减：nz(po.getTotalAmount()).subtract(discountOf(poId)).subtract(returnCreditOf(poId))。PurchaseOrderServiceImpl 换币前只检查 payables.hasMoneyMovement（付款、退款核销、欠款抵扣），不检查采购退货单。
- **影响**：应付按 CNY 100 冲减，订单成本却按 ¥710 冲减，同一笔供应商退款在应付和利润里差了一个汇率倍数。外币与人民币互换时，应付会被多冲或少冲，付款与对账都会出错。
- **复核**：已核对代码，问题成立。PurchaseReturnService.newReturn（约 241 行）在生成采购退货单时冻结采购订单当时的 currencyCode 和 exchangeRate；complete（约 357 行）按冻结汇率计算 refundCny，syncCosts 用 refundCny 冲减订单成本。PayableSyncService.returnCreditOf 只把 refundAmount 原值相加，不看币种，再从采购订单当前币种的 totalAmount 里扣减。syncPurchaseOrder 又会把应付币种改成采购订单的当前币种（91–103 行）。PurchaseOrderServiceImpl.save 换币前只检查 hasMoneyMovement，即 payment_allocation 里有没有付款、核销或抵扣（约 574 行），不检查采购退货单。前端币种 Select（detail.tsx 约 831 行）也没有按已有采购退货单禁用。没有找到上游拦截，触发路径可达。BUS-004 的修复只锁住了已有付款、抵扣、退款时改币种，这里是它没有覆盖到的采购退货冲减来源，属于同类缺口，不是原问题复发，所以判 confirmed 并标注相关 ID。要先下单、再经客户退货回到供应商，然后才改币种，实际很少发生，优先级维持 P2。
- **证据**：静态确认：审核代理与反证复核代理分别核对源码；复现思路：本机：建一张 USD 采购订单并确认下单（不付款），走售后退货入库并选「退回供应商」生成采购退货单；PUT 采购订单保存，把 currencyCode 改为 CNY；对采购退货单依次 POST /api/v1/purchase/returns/{id}/ship 和 /complete（refundAmount=100）；查应付单金额减少了 100（CNY），查去向行 cost_cny 为 710。目前只做了静态确认。
- **审核单元**：采购、仓储、物流

### BUS-051 · P2 · 「全部品类」专项打开「增减型号」只列出未分类型号，无法再往专项里加其他品类的型号

- **位置**：`zhul-erp-frontend/src/pages/supply-chain/dialogs.tsx:1367-1373`、`zhul-erp-frontend/src/pages/supply-chain/project/index.tsx:635-636`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/service/GapProjectService.java:107`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/service/CoverageMatrixService.java:101-111`
- **触发**：1. 建一个不选二级品类的专项。入口有两个：团队视图「按品牌」行点「建专项」（TeamView 只传 brandId），或在建专项弹窗里清空二级品类。2. 打开这个专项详情，点「增减型号」。
- **应当**：弹窗应列出该品牌下所有品类、已进入矩阵的型号，可以勾选加入。
- **实际**：后端建专项时把空品类存成 0（GapProjectService.java:107 `p.setCategoryId(req.getCategoryId() == null ? 0L : ...)`），详情 VO 用 copyProperties 原样返回 categoryId=0。ProjectItemsModal 直接 `supplyApi.models({ brandId: p.brandId, categoryId: p.categoryId, size: 200 })` 把 0 传给后端，models 按 `.eq(categoryId != null, category_id, 0)` 过滤。后端在其他地方都把 0 当「全部品类」处理（如 `p.getCategoryId() == 0 ? null : ...`），这里前端没有转换。结果弹窗只剩专项已有的型号，加上 category_id=0 的未分类型号。
- **影响**：整品牌专项建好后不能补型号，只能删减。想补就得取消重建，专项进度和处理记录会断开。
- **复核**：GapProjectService.java:107 把空品类存成 0L，brief() 用 BeanUtils.copyProperties 原样返回 categoryId=0（第 266 行只改了 categoryName）。后端第 131、159 行在专项内部都按 `getCategoryId()==0 ? null` 处理，前端 dialogs.tsx:1371 的 ProjectItemsModal 却直接调用 supplyApi.models({brandId, categoryId: p.categoryId, size:200})。service.ts:675 的 get 会把 0 原样作为参数发出，CoverageMatrixService.models:107 按 `.eq(categoryId != null, category_id, 0)` 过滤，结果只剩未分类型号。入口可达：TeamView.tsx:258 按品牌建专项时只传 brandId，ProjectModal 也允许清空品类。补型号的路径确实断了，后端 validItems 对整品牌专项本可以放行。
- **证据**：静态确认：审核代理与反证复核代理分别核对源码；复现思路：本机建一个只选品牌的专项（POST /api/v1/supply/projects，不传 categoryId），打开专项详情点「增减型号」，在网络面板里能看到请求 /api/v1/supply/matrix/models?brandId=…&categoryId=0&size=200，返回结果不含其他品类的型号。本轮只做了静态确认。
- **审核单元**：供应链前端

### OPS-008 · P2 · 询价任务超时流转的定时任务整段跑在平台范围内，租户拦截器失效，导致「询价任务待分配」通知收件人配置跨租户混用、可能漏发

- **位置**：`zhul-erp-backend/src/main/java/com/zhul/erp/modules/inquiry/sourcing/service/impl/SalesWindowJob.java:29`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/inquiry/sourcing/service/impl/SalesWindowJob.java:37`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/inquiry/sourcing/service/impl/SourcingTaskServiceImpl.java:304`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/inquiry/sourcing/service/impl/SourcingTaskServiceImpl.java:305`
- **触发**：有两个以上租户。租户 B 在通知设置里给「采购负责人」岗位配了接收人，租户 A 没有配置、按默认角色接收。租户 A 的某个询价任务处于「待业务处理」（TASK_SALES），过了 salesDeadline 后，由 SalesWindowJob 转入待分配。
- **应当**：按项目多租户规范，后台任务只在查租户清单或任务清单时用 TenantScope.platform，具体业务要在 platform 之外用 TenantContext.runAs(tenantId) 执行，让拦截器正常追加当前租户条件。FollowupReconcileJob、CustomerNightlyJob、SupplyDailyJob、ArrivalDueNotifyJob 都是这么写的。租户 A 的采购负责人应收到「询价任务待分配」待办。
- **实际**：本次把整个循环包在了 `TenantScope.platform(() -> { ... TenantContext.setTenantId(...); taskService.moveToPoolIfDue(...) })` 里。TenantInterceptor 第 87 行判断 `!TenantScope.isPlatform()`，所以 moveToPoolIfDue 里的 moveToPool、autoAssign 以及新增的 notices.pool 都不再追加租户条件。moveToPoolIfDue 带 @Transactional，Notifier 在 afterCompletion 里投递，这一步仍在 platform 范围内。投递时 NotificationReceiverService.setting(post) 只按 `post_code` 查 notification_receiver（PRIVATE 表），会把所有租户的配置行都查出来：租户 A 没有配置行、B 有，就不会走 defaults，拿到的是 B 的用户；这些用户随后被 active() 按租户 A 过滤成空，A 的采购负责人收不到通知。另外 autoAssign 里 rulePlan 调用的 inquiryCategories.categories() 也会读到其他租户的品类，按品类匹配的规则可能对不上。
- **影响**：业务员优先窗口超时后转入待分配的任务，所在租户的采购负责人可能收不到待办，任务在池里无人知晓。同一个定时任务里多租户配置混用，违反项目规定的后台入口租户写法。BackgroundEntryTenantContractTest 只检查无上下文时不报错，发现不了这个问题。
- **复核**：SalesWindowJob.java:29-43 把整个循环包在 TenantScope.platform 里，只用 TenantContext.setTenantId 设租户；TenantScope 用 ThreadLocal DEPTH 计数，TenantInterceptor.applies() 判断 !TenantScope.isPlatform()，所以 moveToPoolIfDue 全程不追加租户条件。Notifier.send 在有事务时注册 afterCompletion，回调在同一线程、仍处于 platform 的 lambda 内执行；Notifier.deliver 只用 TenantContext.runAs 设租户 ID，不会退出 platform 范围。NotificationReceiverService.setting() 只按 post_code 查 notification_receiver（TenantTables 中为 PRIVATE），platform 范围下会查出全部租户的配置行。租户 A 没有配置行而 B 有时 rows 不为空，不会走 defaults；拿到的 B 用户再经 active()（第 185-188 行按 TenantContext 租户过滤）被滤掉，结果为空，A 收不到 SOURCING_POOL 通知。Follow
- **证据**：静态确认：审核代理与反证复核代理分别核对源码；复现思路：只能静态确认（本轮只读）。复现方法：本机建两个租户，在租户 B 的「通知设置」给采购负责人岗位指定接收人，租户 A 不配置。在租户 A 录入询盘，生成 TASK_SALES 任务，把 sales_deadline 改成过去的时间。等 SalesWindowJob 执行（默认 60 秒），再查租户 A 采购负责人的 notification 表：没有 SOURCING_POOL 记录。把 B 的配置删掉重跑，就能收到。
- **审核单元**：询价与报价

### SEC-020 · P2 · 登记售后单不校验订单和客户的数据范围，本人范围的用户可对别人的订单发起售后，进而看到单价、发起退货退款和补发

- **位置**：`zhul-erp-backend/src/main/java/com/zhul/erp/modules/aftersales/service/AfterSalesService.java:118`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/aftersales/service/AfterSalesService.java:128`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/aftersales/service/AfterSalesService.java:187`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/aftersales/service/AfterSalesService.java:527`
- **触发**：用户 A 有售后管理（或销售订单）菜单、数据范围为「本人」。他调用 POST /api/v1/sales/after-sales，填入业务员 B 的客户 customerId 和 B 的订单 soId，并把 ownerId 设成自己。
- **应当**：和退款申请的 visibleOrder 一致，订单负责人不在当前用户的数据范围内时拒绝，提示「订单不存在」；不选订单时，客户也应在可见范围内。
- **实际**：create() 对订单只校验租户、客户归属和状态：`if (order == null ｜｜ order.getDeletedAt() != null ｜｜ !Objects.equals(order.getTenantId(), tenant) ｜｜ !Objects.equals(order.getCustomerId(), customer.getId()))`，对客户只校验租户，都没有调用 dataScopeResolver。ownerId 可以任意指定（line 187）。之后的可见性按 `scope.canSee(a.getOwnerId())` 判断，所以 A 能看到这张售后单。
- **影响**：A 能看到 B 订单的型号行成交单价、已出运数量（detail line 527）。SalesReturnService.create 和 confirm 只校验售后单可见，所以 A 还能对 B 的订单发起退货退款，生成退款申请；也能调用 /replacement，以 B 的名义生成 0 元补发订单和采购需求，绕过订单的数据范围。
- **复核**：AfterSalesService.create()（118-200 行）对客户只校验租户，对订单只校验租户、客户归属和状态，没有调用 dataScopeResolver。ownerId 经 requireUser 后可以填任意人。Controller 的 create 只有 @PreAuthorize(MENU_OR_ORDER)。可见性 visible()/canSee() 的规则是「负责人或订单业务员在范围内」，所以 A 把 owner 设成自己就能看到售后单，detail() 会带出原订单行的 unitPrice 和出运数量。replace 和 SalesReturnService 只校验售后单可见，因此 A 可以在别人的订单上补发、退货。对照 RefundRequestService.visibleOrder，那里校验了 canSee(o.getOwnerId())。SEC-017 修的是按订单列出售后单（optionsOfOrder），这里是登记入口，属于同类新入口，不是同一问题。
- **证据**：静态确认：审核代理与反证复核代理分别核对源码；复现思路：本机用 AfterSalesContractTest 里 loginWithResources 的做法，建一个本人范围的业务员，订单负责人改成 BUYER_LIN。以该业务员身份 POST /api/v1/sales/after-sales，body 为 {customerId, soId:别人的订单, ownerId:自己, symptom:'x', items:[{soItemId, quantity:1}]}。预期被拒绝，实际返回 200，详情里带 unitPrice。再 POST /api/v1/sales/returns 和 /{id}/confirm 也能成功。目前是静态确认。
- **审核单元**：售后、销售退货与客户跟进前端

### SEC-021 · P2 · 背调报告导出 PDF 时，正文里的 Markdown 图片会保留 http(s) 外链，交给 LibreOffice 转换，服务器可能被诱导访问内网地址

- **位置**：`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerbackground/support/BackgroundReportHtml.java:23-24`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerbackground/support/BackgroundReportHtml.java:52-55`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerbackground/service/CustomerBackgroundService.java:285-295`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/document/support/DocumentConverter.java:241-242`
- **触发**：有「客户背调」权限的用户上传粘贴文字，内容含 `![x](http://内网地址/...)`。或者 AI 背调联网时受网页内容注入，正文里带了这种图片。报告完成后点「导出 PDF」。
- **应当**：导出用的 HTML 不应包含外部资源，或者只允许 data: 内嵌图片，转换过程不能发起网络请求。
- **实际**：HtmlRenderer 只开了 escapeHtml 和 sanitizeUrls；后者对 http/https 图片地址放行，生成 `<img src="http://...">`。DocumentConverter 直接在后端容器里调用 `soffice --headless --convert-to pdf:writer_web_pdf_Export`，没有网络隔离。LibreOffice 导入 HTML 时通常会加载链接的图片。转换锁与 PI、报价单共用，访问慢地址还会拖住其他单据的导出。
- **影响**：可能形成一个盲 SSRF：后端容器替用户向内网 HTTP 地址发 GET。目标若返回图片，内容还会嵌进导出的 PDF。
- **复核**：BackgroundReportHtml 的 RENDERER 只设置了 escapeHtml(true) 和 sanitizeUrls(true)，commonmark 的 sanitizeUrls 会放行 http/https 图片地址。DocumentConverter.convert 直接调用 soffice，没有网络隔离，仓库和 compose 里也没有禁止加载外部链接的 LibreOffice 配置（xcu 文件）。我在本机后端容器（LibreOffice 7.3.7.2）用同样的 pdf:writer_web_pdf_Export 过滤器转换了一份带 <img src="http://zhul-erp-frontend/..."> 的 HTML，前端 nginx 访问日志记录到来自后端容器 172.18.0.4、UA 为 LibreOffice 7.3.7.2 的 OPTIONS、HEAD、GET 三个请求。由此确认导出时服务器会替用户请求内网 HTTP 地址，属于盲 SSRF，目标返回图片时还会嵌进 PDF。这是安全类、较小越权的问题，优先级上调到 P2。说明：容器内 /tmp/ssrfprobe 目录似乎同时被另一个并行复核任务使用，我只删除了自己写的 Probe.java 和 probe.out，其余文件留给对方处理。
- **证据**：静态确认：审核代理与反证复核代理分别核对源码；复现思路：本机用 multipart 调用 POST /customers/{id}/background-reports/upload，text 字段包含 `![a](http://127.0.0.1:端口/probe)`，再加上足够的正文。等报告完成后（本机无 AI 时可直接把状态改为 2 来造数），GET .../{reportId}/export，在监听端口观察是否收到请求。是否真的会发起请求取决于 LibreOffice 的版本和配置，本次只做了静态推断。
- **审核单元**：主数据后端

### SEC-022 · P2 · 采购退货单只按负责人限定数据范围，采购订单的采购员看不到也处理不了别人去向处理生成的采购退货单

- **位置**：`zhul-erp-backend/src/main/java/com/zhul/erp/modules/purchase/purchasereturn/service/PurchaseReturnService.java:157`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/purchase/purchasereturn/service/PurchaseReturnService.java:784`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/purchase/purchasereturn/service/PurchaseReturnService.java:792`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/purchase/purchasereturn/service/PurchaseReturnService.java:621`
- **触发**：采购员 A 的数据范围为「本人」，是采购订单 PO1 的采购员。退货入库单的去向由另一个人 B 处理（有 purchase:return:dispose 的供应链主管或其他采购员），选「退回供应商」到 PO1，生成采购退货单。A 打开采购退货列表、PO1 详情里的采购退货，或直接按 ID 打开详情。
- **应当**：按规格 openspec/specs/purchase/purchase-return/spec.md 第 60 行，采购退货页「按数据范围（负责人或采购订单的采购员在当前用户可见范围内）显示」。A 作为 PO1 的采购员应能看到并寄出、完成这张采购退货单。
- **实际**：createForAfterSales 设 p.setOwnerId(me())，负责人是处理去向的 B。scoped() 只按负责人过滤：support.scope().apply(w, PurchaseReturnDO::getOwnerId)；visible() 只判断 support.scope().canSee(p.getOwnerId())；ofPurchaseOrder 也用 scoped()；附件 purchaseReturnVisible 同样只认 ownerId。代码里没有任何地方放行采购订单的采购员。
- **影响**：采购订单的采购员看不到自己订单的售后退回，也没法寄出、登记供应商退款。退款不登记，应付就不会冲减，PO1 详情里的采购退货列表也是空的。反过来，只要是负责人，即使不是该采购订单的采购员，也能操作。
- **复核**：已核对代码，问题成立。规格 openspec/specs/purchase/purchase-return/spec.md 第 60 行写明：采购退货页按「负责人或采购订单的采购员在当前用户可见范围内」显示。代码里 scoped()（784 行）只按 ownerId 过滤；visible()（792 行）只判断 canSee(ownerId)；ofPurchaseOrder（621 行）注释写着按负责人范围（SEC-017），list、counts、detail、ship、complete 也都只认负责人。createForAfterSales 把负责人设为 me()，也就是处理去向的人（ReturnReceiptController 用 purchase:return:dispose 权限控制）。处理人不是采购订单的采购员时，「本人」范围的采购员看不到这张采购退货单，也处理不了，与规格不一致。SEC-017 处理的是越权读取，这条是可见范围收得比规格窄，属于功能缺陷，不算越权，只是相关；优先级 P2。
- **证据**：静态确认：审核代理与反证复核代理分别核对源码；复现思路：本机：建本人数据范围的采购员账号 A，作为 PO1 的采购员；用另一个有 purchase:return:dispose 的账号 B 对 PO1 来源的退货入库单 POST /api/v1/aftersales/return-receipts/{id}/dispose，选退回供应商；用 A 调 POST /api/v1/purchase/returns/page（列表为空）、GET /api/v1/purchase/returns/{id}（报「采购退货单不存在」）、GET /of-order/{PO1}（为空）。
- **审核单元**：采购、仓储、物流

### UI-060 · P2 · 档案补全切换客户时，向导与档案的迟到响应互相错配，可能把编辑写到另一个客户，联系人项被静默丢弃

- **位置**：`zhul-erp-frontend/src/pages/customer/profile/CompletionTab.tsx:921`、`zhul-erp-frontend/src/pages/customer/profile/CompletionTab.tsx:934`、`zhul-erp-frontend/src/pages/customer/profile/CompletionTab.tsx:428`、`zhul-erp-frontend/src/pages/customer/profile/CompletionTab.tsx:471`
- **触发**：在「档案补全」页签左侧连续点客户 A、再点客户 B。网络慢时，A 的 profileApi.get 比 B 的晚返回（或 A 的 wizard 晚返回）。之后在右侧点「去填写 / 手动填写」打开档案编辑弹窗并保存；或者在第③④步点选联系人字段后点「保存」。
- **应当**：切换客户时丢弃上一个客户的迟到响应，并清空旧 profile。档案编辑弹窗、联系人保存都只作用于当前选中的客户；联系人匹配不到时要报错，不能当作已保存。
- **实际**：loadWizard 并发调用 completionApi.wizard(selected) 和 profileApi.get(selected)，两者都直接 .then(setWizard)/.then(setProfile)，没有 liveRequests，也没有判断 selected 是否已变。切换时只 setWizard(undefined)，不清 profile。结果是 Wizard 用 B 的 wizard 渲染，却拿到 A 的 profile：ModuleEditModal 按 `profileApi.update(p.customerId, patch)` 把改动写到 A；`contact = profile?.contacts.find((c) => c.id === w.contactId)` 找不到，`if (contactVals.length && contact)` 直接跳过联系人更新，却仍提示 `已保存 ${entered.length} 项`，然后跳到下一个客户。profile 加载失败（catch 后 setProfile(undefined)）时，联系人项同样被静默丢弃。
- **影响**：补档案时可能把字段写进错误的客户档案；联系人画像字段（决策权重、沟通风格等）看上去保存成功，实际没有写入，档案完整度一直补不上。
- **复核**：已核对 CompletionTab.tsx:934-946。loadWizard 并发调 completionApi.wizard(selected)，结果直接 .then(setWizard)，profileApi.get(selected) 直接 .then(setProfile)，没有 liveRequests，也不核对 selected；切换时的 effect（948-951）只 setWizard(undefined)，不清 profile。Wizard 的 ModuleEditModal（856-867）收到的是 profile，保存走 ModuleEditModal.tsx:173 的 profileApi.update(p.customerId, patch)，profile 是旧客户的就会写到旧客户。CompletionTab.tsx:428 的 contact 取自 profile，471 行的 `if (contactVals.length && contact)` 在 contact 为空时直接跳过，但 477 行仍提示「已保存 N 项」，随后 onSaved 跳到下一个客户。联系人项能不能点选（inline, 63-65 行）只看 wizard 的 hasContact，不看 profile 是否加载成功，所以 profile 取数失败（catch 里 s
- **证据**：静态确认：审核代理与反证复核代理分别核对源码；复现思路：本机可复现：浏览器开发者工具把网速降到 Slow 3G，或用请求拦截让 /customers/{A}/profile 延迟 3 秒返回。在档案补全里先点 A 再点 B，等全部返回后在 B 的向导点「去填写」，弹窗标题显示的是 A 的名称，保存后 A 的档案被改。联系人项可以把 /profile 接口改成返回 500 来验证：点选联系人字段后保存，提示已保存，但联系人数据没变。
- **审核单元**：客户与供应商前端

### UI-061 · P2 · 收款单列表翻页总被排序回调重置到第 1 页，第 2 页及以后的收款单无法查看

- **位置**：`zhul-erp-frontend/src/pages/finance/receipts/RecordsTab.tsx:461`、`zhul-erp-frontend/src/pages/finance/receipts/RecordsTab.tsx:472`
- **触发**：财务管理 → 收款管理 → 「收款单」页签，筛选结果超过一页（默认 20 条）时点第 2 页或下一页。
- **应当**：表格的 onChange 只在 extra.action === 'sort' 时处理排序并回到第 1 页，翻页时保持目标页码（与 UI-032 修复后其他列表的写法一致）。
- **实际**：新增的 Table onChange 不区分触发来源，每次都会执行 `setByDate(...); setPage(1);`。antd 6.4.3 的 usePagination 在翻页时先调用 pagination.onChange（setPage(2)），再通过 onPaginationChange 触发 Table 的 onChange（setPage(1)）。两次更新在同一个事件里批处理，最终页码是 1。
- **影响**：收款单（报税统计用的全部到账）只能看到第一页，翻页无效。按到账日期排序后同样无法翻页。财务核对较早的收款只能靠缩小筛选范围。
- **复核**：RecordsTab.tsx:461-465 的 Table onChange 不看 extra.action，每次都 setPage(1)。我核对了 antd 6.4.3：usePagination.js 的 onInternalChange 先调 pagination.onChange(setPage(p))，再调 onChange；后者是 InternalTable 的 onPaginationChange，它经 triggerOnChange 在第 202 行调 props.onChange。两次 setState 在同一事件里批处理，最终 page=1，翻页失效。台账里的 UI-032 已核实为 UI-038，范围只是询盘与销售订单列表（#134）。本处是收款单页签的新代码，不是同一问题复发，所以按新问题处理（与 UI-038 同类）。
- **证据**：静态确认：审核代理与反证复核代理分别核对源码；复现思路：静态确认：node_modules/antd/es/table/hooks/usePagination.js 第 44-50 行 onInternalChange 先调 pagination.onChange，再调 onChange；InternalTable.js 第 266 行 onPaginationChange 调 triggerOnChange，第 202 行再调 props.onChange。本机可在收款单造 21 条以上记录，点第 2 页，观察请求参数 page 仍为 1。
- **审核单元**：财务前端

### UI-062 · P2 · 批量 AI 解析跑完会关掉正在填写的审核抽屉，审核中的修改直接丢失

- **位置**：`zhul-erp-frontend/src/pages/product/candidates/index.tsx:1126`、`zhul-erp-frontend/src/pages/product/candidates/index.tsx:1135`、`zhul-erp-frontend/src/pages/product/candidates/index.tsx:1151`、`zhul-erp-frontend/src/pages/product/candidates/index.tsx:1644`
- **触发**：1. 在商品候选「待审核」勾选若干条，点「AI 解析品类」或「AI 解析品牌」，任务在后台运行几分钟；2. 期间打开另一条候选的审核抽屉，改品牌方式、品类、型号或名称，还没点通过；3. 3 秒一次的轮询拿到 status=3。
- **应当**：后台任务结束时只刷新列表和计数，不打断正在进行的审核，或者至少先确认再关闭。
- **实际**：轮询结束后调用 `changed()`，其中 `setViewing(undefined); setSelected([]); load(); refreshCount();` 会把 `id={viewing}` 的 ReviewDrawer 直接关掉。抽屉里的表单是组件内状态，没有 useUnsavedGuard 或 useTabDirty 登记，也没有确认提示。
- **影响**：审核人填了一半的品牌、品类、型号与名称修改被静默丢弃，需要重新填写；如果没注意到，可能按旧值重新审核。
- **复核**：candidates/index.tsx:1126-1131 的 changed() 先 setViewing(undefined)；1135-1159 的轮询在 status=3 时直接调用 changed()。ReviewDrawer（约 331 行）的状态是 open={id!==undefined} 且带 destroyOnHidden，表单只存在组件内，整页没有 useTabDirty、useUnsavedGuard，也没有确认提示。所以后台任务跑完时，正在编辑的抽屉会被关掉并销毁，填写内容丢失。触发路径是真实流程：AI 任务要跑几分钟，审核人在这期间继续审核其他候选。不属于 UI-057，那条修的是其他表单的未保存登记。
- **证据**：静态确认：审核代理与反证复核代理分别核对源码；复现思路：本机：用 stub 编排服务（AI_STUB_DELAY_SECONDS 设大一点，比如 30）提交批量 AI 解析，随后打开任意候选的审核抽屉修改字段，等回调完成后，抽屉自动关闭，修改丢失。
- **审核单元**：商品与 AI 编排

### UI-063 · P2 · 订单成本卡新增「售后退货冲减」列后，合计行跨列没改，实际成本、退款、收入、毛利的合计全部错位一列

- **位置**：`zhul-erp-frontend/src/pages/sales/orders/CostCard.tsx:163`、`zhul-erp-frontend/src/pages/sales/orders/CostCard.tsx:309`
- **触发**：打开任意有成本数据的销售订单详情，看「成本」卡的表格合计行。
- **应当**：合计行的「实际成本 / 退款 / 行收入 / 行毛利」应对齐各自的列。
- **实际**：本次在第 163 行新增了「售后退货冲减」列，列数从 14 变成 15（型号到收款手续费共 11 列，之后是实际成本、退款、行收入、行毛利），但合计行仍是 `<Table.Summary.Cell index={0} colSpan={10}>` 再接 index 10–13。结果是成本合计落在「收款手续费」列下，退款合计落在「实际成本」列下，收入落在「退款」列下，毛利落在「行收入」列下，「行毛利」列的合计为空。基线版本也是 colSpan=10，当时正好 14 列。
- **影响**：每张订单的成本卡合计数字都对错了列，业务员和财务会把实际成本读成收款手续费，把毛利读成收入，影响利润判断。
- **复核**：CostCard.tsx 当前列定义共 15 列：型号、采购货款、开票成本、采购费用、质量索赔、退货运费、售后退货冲减、国内运费、国际运费、费用单、收款手续费、实际成本、退款、行收入、行毛利。合计行仍是 colSpan=10 加 index 10–13（309-340），实际成本合计落在「收款手续费」列，后面依次错一列，「行毛利」列为空。git 核对：84d6e06b^ 时 14 列、colSpan=10，正好对齐；84d6e06b（2026-11-01）新增「售后退货冲减」列后没改合计行，HEAD 仍是这样。补发订单（own）过滤后剩 12 列，合计行占 11 列，同样错一列。数字本身没错，但会被读到错误的列名下，影响利润判断。
- **证据**：静态确认：审核代理与反证复核代理分别核对源码；复现思路：只能静态确认：数 CostCard.tsx 第 96–260 行的列定义共 15 列，合计行 colSpan=10 加 4 个单元格只覆盖到第 14 列。本机打开任一已有采购成本的订单详情就能看到错位。
- **审核单元**：销售订单与 PI

## 新增问题：P3

| ID | 标题 | 位置 | 审核单元 |
| --- | --- | --- | --- |
| AI-013 | 大询盘核验按原型号对回结果，同一批里原型号相同的第二条既不采用核验结果也不标待核实 | `scripts/ai-orchestrator/server.py:1410`<br>`scripts/ai-orchestrator/server.py:1412`<br>`scripts/ai-orchestrator/large_parse.py:293` | 商品与 AI 编排 |
| BUS-052 | 售后单「处理」弹窗按打开时的快照整单提交，并发时会覆盖他人刚改的原因、方案、负责人和下一步 | `zhul-erp-frontend/src/pages/sales/after-sales/dialogs.tsx:609`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/aftersales/service/AfterSalesService.java:252`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/aftersales/service/AfterSalesService.java:272` | 售后、销售退货与客户跟进前端 |
| BUS-053 | 客户合并把两个客户的商机都改挂到保留客户，违反「一个客户最多一条商机」：自动赢单只处理其中一条，另一条永远停在进行中 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerprofile/service/CustomerMergeService.java:52`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerprofile/service/CustomerMergeService.java:240`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerprofile/service/CustomerMergeService.java:281` | 查漏 |
| BUS-054 | 供应链需求信号把售后补发订单算成「成交」，补发的问题型号被抬高需求等级和优先级 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/repository/SupplySourceMapper.java:28`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/repository/ScDemandSignalMapper.java:19`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/aftersales/support/ReplacementOrders.java:145` | 查漏 |
| BUS-055 | 删除商机不触发跟进对象同步，已删除的商机在当天剩余时间里仍留在商机成交看板和今日待办 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/crm/service/impl/OpportunityServiceImpl.java:284`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/crm/service/impl/OpportunityServiceImpl.java:148`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/crm/service/impl/OpportunityServiceImpl.java:256` | CRM |
| BUS-056 | 费用单删除分摊后仍提交隐藏的关联售后单，保存一直报「售后单不属于分摊的订单」 | `zhul-erp-frontend/src/pages/finance/expenses/index.tsx:123`<br>`zhul-erp-frontend/src/pages/finance/expenses/index.tsx:221`<br>`zhul-erp-frontend/src/pages/finance/expenses/index.tsx:564` | 财务前端 |
| BUS-057 | 手工调整余额在启用事业部时不校验必选事业部，录入的旧余额落到无事业部账户 | `zhul-erp-frontend/src/pages/finance/credits/CreditModals.tsx:405`<br>`zhul-erp-frontend/src/pages/finance/credits/CreditModals.tsx:446`<br>`zhul-erp-frontend/src/pages/finance/credits/CreditModals.tsx:515` | 财务前端 |
| BUS-058 | 多收原路退回改金额时用浮点估算实际退出人民币，可比后端 HALF_UP 账面价值少 0.01 并多记汇兑损益 | `zhul-erp-frontend/src/pages/finance/receipts/OverpaidTab.tsx:229`<br>`zhul-erp-frontend/src/pages/finance/receipts/OverpaidTab.tsx:321` | 财务前端 |
| BUS-059 | 铭牌图片按位置编号交给 AI，确认前删掉一张铭牌后，AI 结果里的编号整体错位，型号会挂上别的铭牌图 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/inquiry/customerinquiry/service/impl/CustomerInquiryServiceImpl.java:770`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/inquiry/customerinquiry/service/impl/CustomerInquiryServiceImpl.java:903`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/inquiry/customerinquiry/service/impl/CustomerInquiryServiceImpl.java:912` | 询价与报价 |
| BUS-060 | 把疑似重复组标为「不是重复」后，同一组客户会立刻换个成组原因（同邮箱或同电话）再次出现 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerprofile/service/CustomerMergeService.java:109-111`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerprofile/service/CustomerMergeService.java:316-326` | 主数据后端 |
| BUS-061 | 采纳背调建议时把匹配到的已有联系人整体按新增规则校验，没有姓名的存量联系人会让整批采纳失败 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerbackground/service/BackgroundAdoptService.java:88-93`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerbackground/service/BackgroundAdoptService.java:108-112`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerbackground/support/BackgroundAdoption.java:251-257` | 主数据后端 |
| BUS-062 | 客户表单新建或修改画像相关字段后，不重算档案完整度、不更新字段来源，列表的完整度和建档筛选、来源提示都是旧的 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/service/impl/CustomerServiceImpl.java:97-135`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/service/impl/CustomerServiceImpl.java:140-157`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/service/impl/CustomerServiceImpl.java:358-400` | 主数据后端 |
| BUS-063 | 画像「按字段修改」实际用 updateById 把整行客户写回，与转移负责人、调整等级、主联系人同步等并发时会用旧值覆盖别人的修改 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerprofile/service/CustomerProfileService.java:139-161`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerprofile/service/CustomerProfileService.java:186-221`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/service/impl/CustomerServiceImpl.java:626-671` | 主数据后端 |
| BUS-064 | 经营等级 RFM 把售后补发订单和费用订单算作成交订单，抬高最近成交天数、订单数和金额的评分 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerprofile/service/CustomerGradeService.java:64-69`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/masterdata/customerprofile/repository/CustomerStatsMapper.java:429-436` | 主数据后端 |
| BUS-065 | 候选 AI 解析品类不按租户行业限制，可把其他行业的品类写进候选并建档 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/product/candidate/service/CandidateAiFill.java:82`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/product/candidate/service/CandidateAiFill.java:116`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/product/candidate/service/CandidateAiFill.java:224` | 商品与 AI 编排 |
| BUS-066 | 按筛选发起 AI 细分总是取同一批前 200 个，判断不出的和不能维护的会反复占位 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/product/refine/service/CategoryRefineService.java:150`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/product/refine/service/CategoryRefineService.java:112`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/product/refine/service/CategoryRefineService.java:221` | 商品与 AI 编排 |
| BUS-067 | 改选细分品类用列表快照整条回写商品，可能覆盖别人刚改的名称、简介、生命周期 | `zhul-erp-frontend/src/pages/product/products/index.tsx:527`<br>`zhul-erp-frontend/src/pages/product/products/index.tsx:533` | 商品与 AI 编排 |
| BUS-068 | 售后补发订单能修改费用、能部分取消，之后收款状态从「无需收款」被改成「未付款」 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/sales/service/impl/SalesOrderServiceImpl.java:511`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/sales/service/impl/SalesOrderServiceImpl.java:579`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/sales/service/impl/SalesOrderServiceImpl.java:998` | 销售订单与 PI |
| BUS-069 | 费用订单可以通过「修改订单费用」删光全部费用行，变成 0 元订单且永远不会自动完成 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/sales/service/impl/SalesOrderServiceImpl.java:539`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/sales/support/PiSummary.java:58` | 销售订单与 PI |
| BUS-070 | 「毛利为负」快捷筛选按 PI 预估净利润过滤，和列表「毛利」列（实际成本口径）不是同一个数，手动订单永远筛不出来 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/sales/service/impl/SalesOrderServiceImpl.java:1791`<br>`zhul-erp-frontend/src/pages/sales/orders/index.tsx:744` | 销售订单与 PI |
| BUS-071 | 付款时登记优惠不锁采购订单，用无锁读到的旧实体整行回写，可能覆盖并发保存的修改 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/purchase/service/impl/PurchaseOrderServiceImpl.java:818`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/purchase/support/PurchaseDrafts.java:270`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/PaymentService.java:308` | 采购、仓储、物流 |
| BUS-072 | 冲销采购入库单不处理部分取消时转入的闲置库存，留下实际不存在的闲置库存，订单已入库数被扣成负数 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/warehouse/service/ReceiptService.java:586`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/warehouse/service/ReceiptService.java:595`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/warehouse/support/PartialCancelHolds.java:42` | 采购、仓储、物流 |
| BUS-073 | 兼职采购的回价被审核退回后，每日补登把它当成「回价已撤回」写进供应链时间线 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/support/QuoteLinkage.java:139`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/support/QuoteLinkage.java:174`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/repository/SupplySourceMapper.java:129` | 供应链后端 |
| BUS-074 | 合并需求对象时，被合并方的覆盖渠道被丢弃、专项型号被移出，与注释写的「并到保留方」不符 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/service/DemandService.java:154`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/service/DemandService.java:162`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/service/DemandService.java:166` | 供应链后端 |
| BUS-075 | 每日重算不加锁读取全部需求对象后整行回写，会覆盖期间用户改的优先级、覆盖状态和下一步 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/job/SupplyDailyJob.java:57`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/job/SupplyDailyJob.java:89`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/support/DemandRecalc.java:172` | 供应链后端 |
| BUS-076 | 记一次处理对每个所选型号都全量读取一遍租户的采购订单行，型号多时请求可能超时 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/service/SupplyBoardService.java:278`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/service/CoverageService.java:84`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/support/EvidenceIndex.java:403` | 供应链后端 |
| BUS-077 | 报价行供应提示和发送确认不跟随需求对象合并，被合并的型号报价时没有货源提示 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/support/QuoteSupplyCheck.java:152`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/support/QuoteSupplyCheck.java:111`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/support/DemandRegistry.java:214` | 供应链后端 |
| OPS-009 | 客户跟进参数迁移的回退 SQL 用错了键名前缀，执行后系统参数删不掉 | `zhul-erp-backend/src/main/resources/db/migration/V1.5.6__customer_followup_menu_config.sql:2`<br>`zhul-erp-backend/src/main/resources/db/migration/V1.5.6__customer_followup_menu_config.sql:63` | 数据库迁移与脚本 |
| OPS-010 | 「已回价」分页接口每次都逐条组装本人全部已回价任务再切片，点「加载更多」后按页数成倍查询，每次保存回价也会触发 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/inquiry/sourcing/service/impl/MyTaskServiceImpl.java:152`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/inquiry/sourcing/service/impl/MyTaskServiceImpl.java:255`<br>`zhul-erp-frontend/src/pages/inquiry/my-tasks/index.tsx:243` | 询价与报价 |
| SEC-023 | 没有「查看货源信息」权限的用户，可以在开发任务时间线里看到供应商库名称 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/service/SupplierDevService.java:312`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/service/SupplierDevService.java:338`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/supply/service/SupplierDevService.java:856` | 供应链后端 |
| UI-064 | 客户跟进 AI 整理用的是旧闭包，整理期间手工填写的「下一步」和日期被 AI 建议覆盖 | `zhul-erp-frontend/src/pages/sales/followup/RecordFollowupModal.tsx:376`<br>`zhul-erp-frontend/src/pages/sales/followup/RecordFollowupModal.tsx:388`<br>`zhul-erp-frontend/src/pages/sales/followup/RecordFollowupModal.tsx:394` | 售后、销售退货与客户跟进前端 |
| UI-065 | 售后单和销售退货详情返回刷新时，正在填写的结案、处理、补发、确认退货弹窗内容被重置 | `zhul-erp-frontend/src/pages/sales/after-sales/detail.tsx:109`<br>`zhul-erp-frontend/src/pages/sales/after-sales/dialogs.tsx:588`<br>`zhul-erp-frontend/src/pages/sales/after-sales/dialogs.tsx:722` | 售后、销售退货与客户跟进前端 |
| UI-066 | 售后单详情的跟进记录草稿（文字和已上传附件）没有登记未保存，关闭或刷新标签直接丢失 | `zhul-erp-frontend/src/pages/sales/after-sales/detail.tsx:92`<br>`zhul-erp-frontend/src/pages/sales/after-sales/detail.tsx:338`<br>`zhul-erp-frontend/src/pages/sales/after-sales/detail.tsx:356` | 售后、销售退货与客户跟进前端 |
| UI-067 | 售后管理列表缺少 spec 要求的「客户」「原因」筛选 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/aftersales/dto/AfterSalesQuery.java:7`<br>`zhul-erp-frontend/src/pages/sales/after-sales/index.tsx:402`<br>`openspec/specs/sales/after-sales/spec.md:71` | 售后、销售退货与客户跟进前端 |
| UI-068 | 输单弹窗把「60 天后提醒重新激活」写死，租户改了培育默认天数后提示和实际激活日期不一致 | `zhul-erp-frontend/src/pages/crm/opportunity/constants.ts:33`<br>`zhul-erp-frontend/src/pages/crm/opportunity/dialogs.tsx:193`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/crm/followup/support/FollowupSync.java:205` | CRM |
| UI-069 | 档案补全向导每次刷新都清空未保存的点选，正在编辑的档案弹窗和联系人弹窗也被重置 | `zhul-erp-frontend/src/pages/customer/profile/CompletionTab.tsx:412`<br>`zhul-erp-frontend/src/pages/customer/profile/CompletionTab.tsx:415`<br>`zhul-erp-frontend/src/pages/customer/profile/CompletionTab.tsx:493` | 客户与供应商前端 |
| UI-070 | 档案补全首次加载失败后点「重试」，页面永远停在错误提示 | `zhul-erp-frontend/src/pages/customer/profile/CompletionTab.tsx:913`<br>`zhul-erp-frontend/src/pages/customer/profile/CompletionTab.tsx:976` | 客户与供应商前端 |
| UI-071 | 在 RFM 明细或「再背调一次？」弹窗里点链接跳页后，弹窗仍盖在新页面上 | `zhul-erp-frontend/src/pages/customer/profile/GradeCard.tsx:397`<br>`zhul-erp-frontend/src/pages/customer/background/components.tsx:576`<br>`zhul-erp-frontend/src/components/PageTabs/index.tsx:67` | 客户与供应商前端 |
| UI-072 | 跟进时间线切换筛选或加载更多时没有丢弃迟到响应，列表会混入其他筛选的记录 | `zhul-erp-frontend/src/pages/customer/FollowupSection.tsx:296`<br>`zhul-erp-frontend/src/pages/customer/FollowupSection.tsx:318`<br>`zhul-erp-frontend/src/pages/customer/FollowupSection.tsx:361` | 客户与供应商前端 |
| UI-073 | 客户名称为空时，详情页标签和返回条只显示「客户 」，余额流水抽屉的客户名为空 | `zhul-erp-frontend/src/pages/customer/detail.tsx:293`<br>`zhul-erp-frontend/src/pages/customer/detail.tsx:620` | 客户与供应商前端 |
| UI-074 | 批量付款切换付款方式后保留已隐藏的对方账户，整批被后端拒绝，且界面无法清除 | `zhul-erp-frontend/src/pages/finance/payables/BatchPayModal.tsx:110`<br>`zhul-erp-frontend/src/pages/finance/payables/BatchPayModal.tsx:284`<br>`zhul-erp-frontend/src/pages/finance/payables/components.tsx:148` | 财务前端 |
| UI-075 | 批量付款弹窗里外币应付的逐笔未付金额被标成 CNY | `zhul-erp-frontend/src/pages/finance/payables/BatchPayModal.tsx:267` | 财务前端 |
| UI-076 | 应付列表勾选的应付不随列表刷新，单张付款后再批量付款仍按旧未付额默认并被拒 | `zhul-erp-frontend/src/pages/finance/payables/index.tsx:474`<br>`zhul-erp-frontend/src/pages/finance/payables/index.tsx:485`<br>`zhul-erp-frontend/src/pages/finance/payables/index.tsx:510` | 财务前端 |
| UI-077 | 退款申请驳回失败时没有任何提示，弹窗停在原处 | `zhul-erp-frontend/src/pages/finance/refunds/RequestsTab.tsx:365`<br>`zhul-erp-frontend/src/pages/finance/refunds/request-detail.tsx:413` | 财务前端 |
| UI-078 | 余额月度核对切换月份或币种时不丢弃迟到响应，旧币种金额会按新币种标注显示 | `zhul-erp-frontend/src/pages/finance/credits/index.tsx:504`<br>`zhul-erp-frontend/src/pages/finance/credits/index.tsx:523` | 财务前端 |
| UI-079 | 「去报价」弹窗承诺未回价型号「询价任务照常进行、回价后可出新版本」，实际报价单一发送，型号就锁定、任务自动完结，采购无法再回价 | `zhul-erp-frontend/src/pages/inquiry/customer-inquiry/GoQuoteModal.tsx:110`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/inquiry/sourcing/service/impl/SourcingProgress.java:116`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/quotation/support/InquiryStatusSync.java:79` | 询价与报价 |
| UI-080 | 批量生成报价单后，用抽屉右上角关闭而不点「完成」，报价单列表不刷新，看不到刚生成的报价单 | `zhul-erp-frontend/src/pages/quotation/NewQuotationModal.tsx:1206`<br>`zhul-erp-frontend/src/pages/quotation/NewQuotationModal.tsx:1242` | 询价与报价 |
| UI-081 | 工作台「售后要更新客户」只统计本人负责的单据，点进去的列表却按数据范围显示全部，主管看到的数字与列表对不上 | `zhul-erp-backend/src/main/java/com/zhul/erp/modules/workbench/widget/SalesWidgets.java:90`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/aftersales/service/AfterSalesService.java:464`<br>`zhul-erp-backend/src/main/java/com/zhul/erp/modules/aftersales/service/AfterSalesService.java:400` | 平台与通用 |
| UI-082 | 通知抽屉切换「待处理 / 全部」时不丢弃迟到响应，旧标签的数据可能覆盖当前列表 | `zhul-erp-frontend/src/components/Shell/NotificationBell.tsx:257`<br>`zhul-erp-frontend/src/components/Shell/NotificationBell.tsx:274` | 平台与通用 |
| UI-083 | AI 任务跑完后按提交时的旧筛选刷新，列表被换成旧条件的数据 | `zhul-erp-frontend/src/pages/product/products/index.tsx:260`<br>`zhul-erp-frontend/src/pages/product/products/index.tsx:276`<br>`zhul-erp-frontend/src/pages/product/products/index.tsx:285` | 商品与 AI 编排 |
| UI-084 | 轮询 AI 任务时一次网络错误就放弃跟踪，跑完的结果不提示、列表不刷新 | `zhul-erp-frontend/src/pages/product/products/index.tsx:278`<br>`zhul-erp-frontend/src/pages/product/candidates/index.tsx:1152` | 商品与 AI 编排 |
| UI-085 | 删除订单后用 history.replace 跳到订单列表，不走 tabsActions.backOr，标签栈留下重复的列表页 | `zhul-erp-frontend/src/pages/sales/orders/detail.tsx:1941`<br>`zhul-erp-frontend/src/components/PageTabs/store.ts:142` | 销售订单与 PI |
| UI-086 | 供应链跟进换筛选后仍保留旧勾选，「延后」会把看不见的型号一起延后 | `zhul-erp-frontend/src/pages/supply-chain/followup/index.tsx:130`<br>`zhul-erp-frontend/src/pages/supply-chain/followup/index.tsx:196-282`<br>`zhul-erp-frontend/src/pages/supply-chain/followup/index.tsx:402-449` | 供应链前端 |
| UI-087 | 供应商开发板块会沿用看不见的二级品类筛选，清单被悄悄过滤 | `zhul-erp-frontend/src/pages/supply-chain/followup/index.tsx:117-118`<br>`zhul-erp-frontend/src/pages/supply-chain/followup/index.tsx:203-215`<br>`zhul-erp-frontend/src/pages/supply-chain/followup/index.tsx:318-330` | 供应链前端 |
| UI-088 | 「去询价」「去查看」用原生 href，会整页重载，「去询价」带的型号关键词也不生效 | `zhul-erp-frontend/src/pages/supply-chain/dialogs.tsx:393-398`<br>`zhul-erp-frontend/src/pages/supply-chain/dialogs.tsx:699-702`<br>`zhul-erp-frontend/src/pages/inquiry/customer-inquiry/index.tsx:111-119` | 供应链前端 |
| UI-089 | 团队视图和覆盖矩阵下钻时丢失统计口径（90 天、未指定负责人、负责人、优先级、无货），列表与统计数对不上 | `zhul-erp-frontend/src/pages/supply-chain/followup/index.tsx:349-358`<br>`zhul-erp-frontend/src/pages/supply-chain/followup/TeamView.tsx:153-161`<br>`zhul-erp-frontend/src/pages/supply-chain/followup/TeamView.tsx:258` | 供应链前端 |
| UI-090 | 覆盖矩阵和型号清单切换条件时不丢弃迟到响应，型号清单在非第一页改筛选会发出两次请求互相覆盖 | `zhul-erp-frontend/src/pages/supply-chain/coverage/models.tsx:64-76`<br>`zhul-erp-frontend/src/pages/supply-chain/coverage/index.tsx:144-169` | 供应链前端 |
| UI-091 | 今日待办接口失败时显示「今天的都处理完了」，概览或团队接口失败时一直显示骨架屏 | `zhul-erp-frontend/src/pages/supply-chain/followup/index.tsx:164-193`<br>`zhul-erp-frontend/src/pages/supply-chain/followup/TodayView.tsx:234-252`<br>`zhul-erp-frontend/src/pages/supply-chain/followup/TeamView.tsx:155-183` | 供应链前端 |
| UI-092 | 品牌和品类选项用模块级缓存，从不失效，首次加载失败后整段会话下拉一直为空 | `zhul-erp-frontend/src/pages/supply-chain/components.tsx:570-587` | 供应链前端 |
| UI-093 | 待归类信号列表缺少四个固定审计列，每页条数写死；型号清单和专项表格用 max-content 横向滚动 | `zhul-erp-frontend/src/pages/supply-chain/followup/SignalTable.tsx:59-156`<br>`zhul-erp-frontend/src/pages/supply-chain/coverage/models.tsx:211`<br>`zhul-erp-frontend/src/pages/supply-chain/project/index.tsx:495` | 供应链前端 |

P3 的触发步骤、实际行为与复核意见保存在审核结果中，修复认领时按 ID 补写到对应任务记录；证据均为两次独立代码核对的静态确认。

## 修复不完整、重开的原 ID

### BUS-033

- 客户合并后，订单履约/售后评价的跟进对象撞唯一键，保留客户每次重新推导都失败：客户经营不刷新，单据事件时间线丢失（`zhul-erp-backend/src/main/java/com/zhul/erp/modules/crm/followup/support/OrderFollowupSync.java:94`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/crm/followup/support/OrderFollowupSync.java:103`）

### BUS-035

- 余额与退款申请先做普通读取再加行锁，MySQL 默认可重复读下锁后仍用旧快照，可并发超额抵扣，或把多收重复处理（`zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/CustomerCreditLedger.java:41`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/CustomerCreditLedger.java:86-93`）
- 售后、退货、退货入库、采购退货的「锁后重读」仍读到事务开始时的旧数据，并发确认退货会生成两张退货入库单和两条退款申请（`zhul-erp-backend/src/main/java/com/zhul/erp/modules/aftersales/service/SalesReturnService.java:174`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/aftersales/service/SalesReturnService.java:198`）

### SEC-004

- 平台给租户「重置管理员密码」后，该管理员已登录的会话（含「记住我」）继续有效（`zhul-erp-backend/src/main/java/com/zhul/erp/modules/tenant/service/impl/TenantServiceImpl.java:273`、`zhul-erp-backend/src/main/java/com/zhul/erp/modules/tenant/service/impl/TenantServiceImpl.java:286`）

### UI-004

- 按浏览器后退（含鼠标侧键）离开有未保存修改的页面时不提示，页面被卸载，修改丢失（`zhul-erp-frontend/src/components/PageTabs/store.ts:75`、`zhul-erp-frontend/src/components/PageTabs/model.ts:163`）

### UI-005

- 新建向导的未保存登记在 layout:false 页面上不生效，浏览器返回或刷新时补充资料静默丢失（`zhul-erp-frontend/src/pages/product/products/new.tsx:181`、`zhul-erp-frontend/config/routes.ts:44`）

### UI-055

- 客户跟进页没有返回刷新，从跟进打开售后单处理后回来，阶段和待办仍是旧数据（`zhul-erp-frontend/src/pages/sales/followup/index.tsx:114`、`zhul-erp-frontend/src/pages/sales/followup/index.tsx:201`）
- 商机列表从其他页面返回时只刷新表格，顶部统计卡片（S1 待跟进、今日无效、本月有效率）仍是旧数（`zhul-erp-frontend/src/pages/crm/opportunity/index.tsx:90`、`zhul-erp-frontend/src/pages/crm/opportunity/index.tsx:117`）
- 覆盖矩阵「型号清单」页返回时不刷新，UI-055 没有覆盖到（`zhul-erp-frontend/src/pages/supply-chain/coverage/models.tsx:45-76`、`zhul-erp-frontend/src/pages/supply-chain/coverage/models.tsx:87`）
- 报销单页签从详情返回后不刷新，已撤回或已付的报销单仍显示待付款，可被勾选批量登记（`zhul-erp-frontend/src/pages/finance/reimbursements/index.tsx:453`、`zhul-erp-frontend/src/pages/finance/reimbursements/index.tsx:493`）
- 客户详情返回刷新只重取基本信息和档案，背调报告、背调前检查、跟进概况仍是旧数据（`zhul-erp-frontend/src/pages/customer/detail.tsx:330`、`zhul-erp-frontend/src/pages/customer/detail.tsx:300`）
- 客户详情每次切回都整页换成骨架屏并重建子组件，SOP 勾选、时间线筛选和已展开内容全部丢失（`zhul-erp-frontend/src/pages/customer/detail.tsx:308`、`zhul-erp-frontend/src/pages/customer/detail.tsx:330`）
- 商品列表与候选页从其他页面返回时只刷新列表，缺项计数与页签计数仍是旧值（`zhul-erp-frontend/src/pages/product/products/index.tsx:247`、`zhul-erp-frontend/src/pages/product/candidates/index.tsx:1124`）

### UI-056

- 建专项和主动拓展弹窗不丢弃迟到响应、不重置上一次的状态，可能改到另一个范围的主动拓展（`zhul-erp-frontend/src/pages/supply-chain/devDialogs.tsx:66-82`、`zhul-erp-frontend/src/pages/supply-chain/devDialogs.tsx:85`）
- 合并弹窗对调保留方或换被合并客户后，迟到的预览覆盖当前预览，确认前看到的改挂数量是另一个方向的（`zhul-erp-frontend/src/pages/customer/profile/DuplicatesTab.tsx:56`、`zhul-erp-frontend/src/pages/customer/profile/DuplicatesTab.tsx:127`）

### UI-058

- 在客户详情把当前客户并入另一个客户后用 history.push 压栈，返回条指回已被删除的客户（`zhul-erp-frontend/src/pages/customer/detail.tsx:679`）
- 商品新建向导、商品删除后用 history.push 回列表，返回条指向已离开的向导或已删除的商品（`zhul-erp-frontend/src/pages/product/products/new.tsx:355`、`zhul-erp-frontend/src/pages/product/products/new.tsx:369`）

其中 UI-055 的「客户详情切回时整页换成骨架屏，SOP 勾选、时间线筛选和已展开内容丢失」是 #329 给客户详情加返回刷新时引入的回退：`load()` 一开始就 `setLoading(true)`，返回刷新时整页卸载重建。

## 待业务确认（未编号）

- 只有「记跟进」权限就能通过跟进的「调整阶段」改商机阶段，绕过商机编辑权限：代码事实成立，但能否算越权取决于产品设计，现有证据不足以定为缺陷。 1) FollowupController.java:146-150 的 adjustStage 只加了 @PreAuthorize(RECORD)，即 @perm.has('crm:followup:record')。FollowupStageService.adjust 第81-86行在 board=SALES 且 opportunityId>0 时，直接调用 opportunityService.changeStage。而商机自己的入口 OpportunityController /{id}/stage 要求 crm:opportunity:edit，所以确实存在一条不经过商机编辑权限就能改商机阶段的路径。 2) 这条路径的范围有限制：requireOpen（FollowupService:1646）要求跟进对象的负责人是本人，或有查看团队权限且在数据权限内；changeStage 中的 lockedInScope（第733行）还会按 dataScopeResolver 校验商机负责人。阶段只能在 S1↔S2、S
- BUS-065（经营等级 RFM 计入补发订单）复核认为只有「售后补发」这一半成立，费用订单是否算成交属于业务口径，修复前一并确认。

## 生产只读核查（2026-11-15，用户授权）

用户授权连接生产执行核查 SQL。经 SSH 在生产 MySQL 容器内执行，整段放在 `START TRANSACTION READ ONLY` 中，只读、未改任何数据；查询文件用后已删除。

- **BUS-042**：生产余额流水共 2 条、余额提现 1 笔，「作废余额提现」写回的冲销流水（类型 8、关联提现流水）为 0 条，没有账户的可用余额虚增，不需要修正历史数据；代码照常修复。
- **BUS-046**：迁移 V1.5.8 于 2026-11-05 09:48 在生产执行，按阶段记录「上线补齐：已有收款，自动赢单」只补了 1 条商机：租户 1000 的 OPP20261019003（2026-10-19 建立，补齐前处于 S4）。该客户唯一一张 PI 于 2026-11-03 建立并当天到账 1476.00（到账记录有效，PI 已收金额一致），即商机建立之后才成交收款，属于正确赢单，不是本问题描述的误标。

### BUS-046 暂缓

- **决策**：用户 2026-11-15 确认暂缓。理由：生产唯一一条补齐数据经核查无误；V1.5.8 是已执行的一次性数据补齐迁移，Flyway 不会再执行，也不能修改其文件；当前没有需要修正的数据，也没有会再次触发的代码路径。
- **负责人**：Claude。
- **下一步与恢复条件**：出现以下任一情况时恢复处理：在其他尚未执行 V1.5.8 的库（如新环境用历史数据初始化）上执行该迁移；或发现生产有其他被误标赢单的商机。恢复时按本节口径先只读核查，再决定修正方式。

## 实施与验证结果

### 实施

用户 2026-11-15 要求「全部修复，本机验收后合并」，同意多代理分批并行。96 条发现分 10 批（财务、售后并发重读、CRM 与主数据、平台、供应链后端、销售、客户前端、供应链前端、询价与采购、商品与 AI），两轮各 5 批并行：每批一个修复代理在独立 worktree 里先写缺陷见证测试并确认在修复前失败，再修复；复核代理逐条检查；复核提出必须修改的 3 批由补修代理处理（BUS-035#1、BUS-073、UI-055#5）。95 条修复，BUS-046 暂缓不改。没有新增数据库迁移。各批分支合入集成分支 `fix/audit-1115`，两处冲突（售后列表筛选状态、采购退货服务）手工合并。

| ID | 结果 | 根因与改法 | 测试 / 缺陷见证 |
| --- | --- | --- | --- |
| AI-012 | 已修复 | 已在提交 76e77af6（本批前一轮）修复：API 严格输出把可选字段改成可空时，enum 一并加入 null，原文看不出来的阶段答案可以不给。本轮在容器里重新验证。 | scripts/ai-orchestrator/test_server.py#OpenAISchemaTest.test_optional_enum_allows_null；把 server.py 换回 169c802a 版本后，该用例在限内存容器里 FAIL；用当前代码运行通过。 |
| AI-013 | 已修复 | 已在提交 76e77af6 修复：核验对回时登记原型号相同的每一条，优先按确认型号对回，对不上的标待核实。本轮重新验证。 | scripts/ai-orchestrator/test_large_parse.py#ParseLargeTest.test_verify_same_original_model_with_different_confirmed_models；server.py 换回 169c802a 版本时该用例 ERROR，当前代码运行通过。 |
| BUS-033#1 | 已修复 | 根因：OrderFollowupSync.resync 只按 customer_id 取已有行，订单改挂后在新客户名下新建，与原客户那行撞 uk_board_target。改法：仿照 BUS-033 的 takeOver，按订单 ID 查其他客户名下 board 3/4 的同订单跟进对象，改归当前客户；因「订单不在了」（CLOSE_DOC_GONE）结束的重新打开，然后照常推导。另外客户合并后会对保留客户重新推导一次。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/crm/followup/OrderFollowupSyncTest.java#orderMovedToAnotherCustomer_fulfilItemFollowsOrder；修复前失败：对新客户 resync 抛 DuplicateKeyException；修复后通过：沿用原 id、customer_id 改为新客户并重新打开，同一订单只有一行。 |
| BUS-035#1 | 已修复 | 这些入口在加锁前已做过普通读，MySQL 可重复读下拿到锁后读的仍是旧快照。按仓库已有写法（PaymentService 等），给这些入口加 @Transactional(isolation = READ_COMMITTED)：PI 的 save/markSent/abandon/voidPi/close/reopen/delete；余额的 adjust/withdraw/processOverpaid；退款申请的 apply/withdraw/approve/reject/execute/remark；退款单的 voidRefund；到账的 confirm/confirmOrder/voidReceipt/voidOrderReceipt/orderCredit/platformReceipt/orderPlatformReceipt/claim/claimToOrder。Ledger ；补修：已修复。这条复核意见成立。  1）给以下入口加上 `@Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)`： - `ReceiptLedgerServiceImpl.unclaim` - `SalesOrderServiceImpl` 的 `updateFees`、`cancel`、` | CreditConcurrencyContractTest#twoPisDeductSameCredit_secondSeesFirstOccupation；CreditConcurrencyContractTest#overpaidProcessedTwiceAtOnce_secondSeesFirstTransfer；CreditConcurrencyContractTest#refundAp；用 SpyBean 让请求 A 写完、提交前停住。这时请求 B 先做普通读，再去等 A 的锁；放行 A 后，看 B 的结果。修复前 3 个用例全部失败：第二张 PI 也抵扣成功，余额被扣成负数；多收被转入两次；同一订单出现两张金额都是 1000 的申请。修复后 3 个用例全部通过。另外跑了 CustomerCreditLedgerTest、AfterSalesContractTest、Paymen |
| BUS-035#2 | 已修复 | 根因：售后模块各写方法都按 MySQL 默认的可重复读运行。事务里第一次普通读（visible）就建立了读视图，所以 lockById 之后的 selectById 读到的仍是加锁前的旧快照。改法：AfterSalesService、SalesReturnService、ReturnReceiptService、PurchaseReturnService、RefundRequestService 的全部 @Transactional 写方法统一加 isolation = Isolation.READ_COMMITTED（与 PaymentService、ExpenseService 等写法一致），并在各 lock() 方法上加注释说明。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/sales/AfterSalesReturnConcurrencyContractTest.java#confirmTwice_concurrently_createsOneReceiptAndOneRefundRequest；zhul-erp-backend/src/test/java/com；用 SpyBean 把第一个请求卡在 AfterSalesLogs.add（此时已持有全部行锁、尚未提交）。第二个请求完成首次读取后卡在锁上，再放行第一个请求。修复前两个用例都失败，报错为「第二个请求应被拒绝 ... nothing was thrown」，即第二次确认退货、第二次验收都成功了，会生成第二张退货入库单和第二条退款申请。修复后两个用例都通过。 |
| BUS-042 | 已修复 | 作废余额提现时，release 按冲销类型（8）写入一条正数流水。lots() 只排除抵扣释放（5）类型，于是把这条流水也当成新批次，可用余额翻倍。改为 lots() 同时排除 related_entry_id 不为空的流水（这类正数流水只会由 release 生成），流水类型仍用冲销，核对表的分列不受影响。 | CustomerCreditContractTest#collectionPaidByPlatform_transfersNetToCredit_withdrawRecordsFxGain_notRevenue（追加作废后再转出 582 被拒、再转出 291 成功）；修复前运行：转出 USD 582 成功，生成了退款单 TK…002，断言「应当失败」不通过。修复后这一步返回「可用余额只有 USD 291.00」，整个类 18 个用例全部通过。 |
| BUS-043 | 已修复 | cancel() 原来只检查了退货、退款申请、费用和退款单，没有检查补发订单。现在在这些检查之后补上：用 replacementOrders.of(id) 找出状态为 SO_ACTIVE 的补发订单，有就拒绝，提示「已有补发订单 SOxxx，请先取消补发订单」；已取消的补发订单不算。前端撤销确认框的说明文字也补上了补发订单。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/sales/AfterSalesReplacementContractTest.java#cancel_rejectedWhileReplacementActive；修复前，撤销接口在有补发订单时仍返回 code=0、状态变为已撤销，用例失败。修复后能拦下；取消补发订单后再撤销可以成功，用例通过。 |
| BUS-044 | 已修复 | 根因：cancel() 只拦在仓出库单，不查售后侧单据；补发只看 deletedAt；退款申请的撤回和按订单列出共用要求订单有效的 visibleOrder。改法：OrderDeleteCheck 新增 cancelBlockers，取消前列出并拦住处理中的售后单、未入库也未作废的退货单、待验收、待审批、待财务处理的退款申请、有效的费用订单和暂存中的货物；ReplacementOrders.create 在订单或最初订单已取消时拒绝补发（「订单已取消，不能补发」）；RefundRequestService 拆出 activeOrder，只有提出申请和查可退金额要求订单有效，撤回和按订单列出不再看订单状态。 | AfterSalesReplacementContractTest#openAfterSalesBlocksCancel_cancelledOrderNoReplacement_refundRequestWithdrawable；AfterSalesContractTest#validations_lists_noPurchaseOrderCost_dataScope（改：先断言取消被拦，结案后再；把后端主代码临时还原到 169c802a 后跑同一测试，结果失败（「应当失败」：取消请求直接成功）；修复后通过。 |
| BUS-045 | 已修复 | 根因：convert 用普通 selectById 读开发记录、在可重复读快照下查进行中商机，后到事务等锁后照样更新并插入第二条商机。改法：FollowupDevelopService.requireOpen 改为 FOR UPDATE 先锁开发记录再判断（convert 与 stop 共用），convert 的事务改为 READ_COMMITTED，锁后查进行中商机能看到对方刚提交的数据。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/crm/followup/DevelopApiContractTest.java#convert_concurrentWithAnotherConvert_secondIsRejected；修复前运行失败：另一事务已结束开发记录并插入商机但未提交时调用转商机，接口返回 code 0（又建了一条商机），断言要求 FOLLOWUP_STAGE_MISMATCH；修复后通过。 |
| BUS-046 | 未修复 | 根因成立：V1.5.8 选商机时只要求客户名下有任一 PI received_amount>0，不比较商机创建时间和到账时间，也不排除已作废 PI（status=4），所以老客户在最后一次收款之后新开的进行中商机也被标成了 WON。该迁移已在生产执行；按要求不能修改已有迁移，也不自行加数据修复迁移。运行时 FollowupHooks.receiptConfirmed 只在到账那一刻调用 autoWin，口径正确，代码不需要改，所以本批没有代码改动，只给出存量数据的排查和修正建议。 | ；通过阅读 V1.5.8 第 16-23 行的选择条件和第 34-45 行的统一 UPDATE 确认。迁移文件不能改，新库执行 V1.5.8 时表为空、不会产生错误数据，所以没有可以先失败、再修好的代码测试；如果写一个复现迁移行为的测试，会永久失败。 |
| BUS-047 | 已修复 | 根因：合并时 FieldSources.moveMissing 直接整行 UPDATE customer_id，保留客户已有同字段来源行（已问、等回复，或清空过）时撞 uk_customer_field。改法：逐字段处理，保留客户没有来源行就搬行；已有行则把被合并客户的来源、报告、跟进、操作人照搬到这一行，并清除「已问、等回复」（字段已补上值）。没有物理删除。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/masterdata/customerprofile/CustomerProfileContractTest.java#merge_targetHasSourceRowForEmptyField_mergesAndMovesSource；首轮因测试客户名称触发查重而没有走到合并，改名后用同一份测试验证修复后通过；从代码看，修复前同一路径会执行 UPDATE 撞唯一键。严格说没有拿到修复前失败的直接证据。 |
| BUS-048 | 已修复 | 根因：合并的 DOCUMENTS 清单漏了 customer_followup、customer_background_report 和年度计划，照搬过来的「背调报告」来源打开时 require 判断报告不属于该客户。改法：DOCUMENTS 加上跟进记录和背调报告（预览同时显示数量）；客户级跟进记录（target_type=6）的 target_id 指向保留客户；年度计划按年改挂，保留客户已有的年份不搬，避免撞 uk_customer_year；合并后对保留客户调用 followupSync.customerChanged 重新推导跟进对象。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/masterdata/customerprofile/CustomerProfileContractTest.java#merge_movesFollowupsPlansAndReports；修复前失败：合并后 customer_followup.customer_id 仍是被合并客户（expected 保留客户 id，实际是被合并客户 id）；修复后通过，背调报告详情接口也能打开。 |
| BUS-049 | 已修复 | 根因：PiSummary 已用 PiStore.receivable（有有效订单时取订单合计）计算收款状态，但收款侧的 Owner.remaining、DeskRow.total、工作台筛选和 PI 详情的 remainingAmount 仍用 PI 合计。改法：Owner.remaining 改为 remaining(store)，用 store.receivable；工作台批量查出各 PI 的有效订单，DeskRow 带上应收合计，用于筛选、合计和剩余；PiServiceImpl 的 remainingAmount 也改用 receivable。前端 ConfirmReceiptModal 用的是接口返回的 remainingAmount，随之正确。 | SalesOrderPartialCancelContractTest#partialCancelSettlesPi_remainingAndDeskFollowOrderTotal；SalesOrderTrackingContractTest#piOrderFees_remainingFollowsOrderTotal_feeDiffSettles；在原代码上运行，两个测试都失败（部分取消后 remainingAmount 期望 0，实际为 250；补运费后期望 20，实际为 0）；修复后通过。 |
| BUS-050 | 已修复 | 根因：采购退货单生成时冻结采购订单的币种和汇率，而换币前只检查付款、抵扣和退款，没有检查采购退货单。改法：采购订单已下单且有未作废的采购退货单时，PurchaseOrderServiceImpl.save 拒绝修改币种，提示「这张采购订单已有采购退货单，退款或抵扣按原币种登记，不能修改币种」。采购退货单作废后可以改。前端币种下拉框与 BUS-004 一样不禁用，靠后端拦截。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/finance/PayableContractTest.java#currencyChange_blockedWhilePurchaseReturnExists；修复前运行失败：保存时币种被改成 USD，返回 code=0，没有被拦截。修复后通过。 |
| BUS-051 | 已修复 | 全部品类的专项把品类存成 0，ProjectItemsModal 原样把 categoryId=0 发给 /matrix/models，结果只取到未分类的型号。现在改成 categoryId: p.categoryId ｜｜ undefined，按整个品牌取型号；后端 validItems 对整品牌专项本来就放行，后端不用改。 | zhul-erp-frontend/src/pages/supply-chain/dialogs.test.tsx#BUS-051：全部品类专项增减型号按整个品牌取型号，不把品类 0 传给后端；修复前运行失败：请求参数里 categoryId 是 0。修复后通过。 |
| BUS-052 | 已修复 | 根因有两处：前端 PlanModal 按打开时的快照整单提交；后端把 nextAction 为 null 当成清空。改法：前端只提交改过的字段（等级与原因、原因与责任、方案、负责人、下一步和下次更新时间各自比较）；后端 updatePlan 在 nextAction 为 null 时，下一步和下次更新时间都保持不变，只有传空字符串才清空。DTO 注释写明了这个约定。 | zhul-erp-frontend/src/pages/sales/after-sales/dialogs.test.tsx#处理信息只提交改动的字段；zhul-erp-frontend/src/pages/sales/after-sales/dialogs.test.tsx#什么都没改时不提交处理信息里的其他字段；zhul-erp-backend/src/test/java/com/zhul/e；修复前，前端两个用例都失败：请求体里带着 levelCode、reasonCode、solutions、ownerId 等全部旧值。后端用例也失败：只改负责人时下一步被清空，报错为 expected 寄样品给客户 but was 空。修复后全部通过。 |
| BUS-053 | 已修复 | 根因：合并的 blocked() 只检查财务表，没有商机检查，合并后保留客户可能挂两条进行中商机。改法：blocked(target, source) 增加检查，两个客户都有 closed_at IS NULL 且未删除的商机时，预览与合并都提示「两个客户都有进行中的商机，请先结束其中一条再合并」；已结束的商机照常改挂。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/masterdata/customerprofile/CustomerProfileContractTest.java#merge_bothHaveActiveOpportunities_isBlocked；首轮因测试客户名称触发查重而没有走到断言，改名后用同一份测试验证修复后通过。修复前代码没有这项检查，预览的 blocked 一定为空。 |
| BUS-054 | 已修复 | SupplySourceMapper.orderRows 只按订单取消、删除和数量判断来源已取消，没有按订单来源过滤，售后补发订单（source=3）的型号行会作为成交信号入库。现在 cancelled 条件加上 so.source = 3：新的补发订单不再写入信号；已汇入的补发信号在回看窗口内再次读到时会改为 source_cancelled=1，口径和销售统计一致。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/supply/SupplyAuditFixContractTest.java#bus054_replacementOrderIsNotADealSignal；修复前运行失败：补发订单行的 source_cancelled 期望 1，实际为 0。修复后通过。 |
| BUS-055 | 已修复 | 根因：OpportunityServiceImpl.delete 只做软删除，没有调用 followupSync。改法：删除后调用 followupSync.customerChanged，与登记、改阶段一致，商机的跟进对象随即移出。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/crm/OpportunityApiContractTest.java#delete_closesFollowupItemImmediately；修复前失败：删除后仍有 1 条未结束的跟进对象（expected 0）；修复后通过。 |
| BUS-056 | 已修复 | 售后单选项的 effect 有两处修改。分摊为空时同时清空 afterSalesId。选项加载完成后，已选的售后单不在新选项里就清空；若有订单的选项取数失败，则不清空，避免把仍有效的关联误清掉。同时改用 liveRequests 丢弃迟到的旧结果。 | zhul-erp-frontend/src/pages/finance/expenses/expenseDrawer.test.tsx#删光分摊后保存不带关联售后单；修复前：删掉分摊后保存，PUT 请求体里仍带 afterSalesId=5（Received 5）。修复后测试通过。 |
| BUS-057 | 已修复 | 手工调整弹窗从页面顶部打开、公司启用了事业部时，默认填当前用户的事业部；事业部信息晚到时只补空着的值。提交时如果仍没有事业部，提示「请选择事业部」并阻止提交。从某个账户进入时沿用该账户的事业部，不做校验。 | zhul-erp-frontend/src/pages/finance/credits/CreditModals.test.tsx#启用事业部时手工调整不选事业部不能提交；修复前：调整接口照常被调用，businessUnitId 为 undefined（Received length 1）。修复后不发请求，并提示「请选择事业部」。 |
| BUS-058 | 已修复 | bookOf 原来用 Math.round(v*rate*100)/100 浮点估算，改用同目录 fx-settlements/calc.ts 的 settleCny：金额按 2 位、汇率按 6 位转成整数，用 BigInt 相乘，按 HALF_UP 取两位。后端 cashRate 本身就是 6 位，两边结果一致。 | zhul-erp-frontend/src/pages/finance/receipts/OverpaidTab.test.tsx#原路退回 1.20 × 7.0125 默认带出 8.42（浮点会算成 8.41）；问题单的例子有误：1.50 × 7.123 = 10.6845，按 HALF_UP 取两位是 10.68，前后端都得 10.68，复现不了。缺陷本身成立（浮点误差），改用 1.20 × 7.0125 = 8.415 复现：修复前带出 8.41，HALF_UP 应为 8.42（Expected 8.42，Received 8.41）。确认失败后恢复修复，测试通过。 |
| BUS-059 | 已修复 | 根因：解析时按当时未删除的铭牌给编号，确认草稿时却按当前未删除的列表换回附件，删掉一张后编号整体错位。改法：新增 plateSlots，列出含已删除的全部铭牌，按 sort、id 排序（sort 按全部附件计数递增，顺序稳定）。parseInput 按这个顺序编号，只发送未删除的图；draftRows 和 appendDraft 用同一顺序把编号换回附件，已删除的图不再挂到型号上。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/inquiry/InquiryNameplateContractTest.java#draft_afterRemovingPlate_keepsAiNumbering；修复前运行失败：删掉第 1 张铭牌后，第 1 行的 nameplateIds 是第 3 张图，预期为空。修复后通过；同时断言重新解析时发送的铭牌编号是 2 和 3。InquiryNameplateContractTest 全部 9 条通过。 |
| BUS-060 | 已修复 | 根因：collect 命中忽略键时短路，成员串没有记为已出现，同一组客户会以 EMAIL / PHONE 原因再次出现。改法：groups() 把忽略的组键转成成员串（「原因:成员」取冒号后的部分），collect 按成员判断，同一组客户换个成组原因也不再出现。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/masterdata/customerprofile/CustomerProfileContractTest.java#dismissedGroup_doesNotReappearUnderAnotherReason；修复前失败：把 NAME 组标为「不是重复」后，同一组客户以 PHONE 再次出现（expected 0，实际 1）；修复后通过。 |
| BUS-061 | 已修复 | 根因：采纳背调建议时，对匹配到的已有联系人用 requestOf 整条复制后做整体 Bean 校验，存量联系人没有姓名或电话不规范时，整批采纳失败。改法：已有联系人只用 validator.validateProperty 校验采纳的字段（title、jobRole、decisionRole），其他存量值原样写回；新建联系人仍做整体校验。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/masterdata/customerbackground/CustomerBackgroundContractTest.java#adopt_existingContactWithoutNameOrValidPhone_writesOnlyAdoptedField；修复前失败：接口返回 500「电话格式不正确」，同批的公司规模也没有写入；修复后通过：职位类别写入，存量电话和空姓名保持原样，公司字段一并写入。 |
| BUS-062 | 已修复 | 根因：CustomerServiceImpl.create / update 没有调用 ProfileFacts.refresh，也没有调用 FieldSources.record。改法：update 比较表单里也能改的画像字段（customerRole、industry、website、付款方式含定金与账期、city、timezone），改动的字段记为人工来源，然后重算完整度；create 也重算完整度。ProfileFacts 用 ObjectProvider 延迟获取，避开 ProfileFacts→FollowupHooks→OpportunityService→CustomerService 的循环依赖。重算时会推导主动开发阶段，所以去掉了原来重复的 followupSync.prospectChanged 调用。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/masterdata/customerprofile/CustomerProfileContractTest.java#customerForm_updateRefreshesCompletenessAndSources；修复前失败：表单保存行业后 profile_mask 基础画像位仍为 0（expected 1）；修复后通过，来源从背调报告改为人工、report_id 为 0。 |
| BUS-063 | 已修复 | 根因：CustomerProfileService.update 和 applyFollowup 整行读出客户后用 updateById 整行写回，读和写之间别人提交的修改（如负责人、等级调整）会被旧值覆盖。改法：新增 saveFields，用 LambdaUpdateWrapper 只 SET 改动字段对应的列（付款方式连带定金比例、账期），并写 update_time、update_by。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/masterdata/customerprofile/CustomerProfileContractTest.java#profileUpdate_doesNotOverwriteConcurrentOwnerChange；修复前失败：另一事务改了负责人、持有行锁时保存画像，提交后负责人被改回 99000002（expected 99000063）；修复后通过。 |
| BUS-064 | 已修复 | 按用户决定，RFM 统计成交时排除售后补发（source=3）和费用订单（source=4）。CustomerGradeService.detail 的订单查询加 notIn source；CustomerStatsMapper 中每日重算用的 orderSpan、yearByCurrency 加上 source NOT IN (3, 4)（source 列 NOT NULL）。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/masterdata/customerprofile/CustomerProfileContractTest.java#rfm_excludesReplacementAndFeeOrders；修复前失败：RFM 明细的 daysSinceLast 为 0（今天的补发和费用订单被当作成交），expected 300；修复后通过，每日重算结果与明细一致。 |
| BUS-065 | 已修复 | IndustryScope 新增 categoryVisible。CandidateAiFill.categories() 按 visibleCategoryIds 过滤，发给 AI 的清单和回调写入用的 byId 都只含本租户行业的品类，回调在任务所属租户下执行，AI 给出其他行业的品类按未识别处理。batchEdit 拒绝其他行业的品类；archive（单条通过）报「品类不属于本公司行业」；batchApprove 跳过这类候选并给出原因。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/product/candidate/CandidateIndustryContractTest.java#aiFill_categoryListAndCallbackLimitedToTenantIndustry；zhul-erp-backend/src/test/java/com/zhul/e；修复前两个用例都失败：发给 AI 的清单含其他行业品类；batch-edit 传其他行业品类返回成功。修复后通过。 |
| BUS-066 | 已修复 | targetIds 设 mine=true，按筛选发起时只取本公司能维护的商品，平台账号不受影响。回调里 AI 判断不出的商品记下 ai_category_at（状态仍为 0，不改 update_time，只写本公司能维护、品类没被改过的商品）。idsMatching 在 refineState=todo 时先按 ai_category_at 升序（NULL 在前），所以没跑过的优先。提交给 AI 的顺序与选取顺序一致。前端弹窗文案改为「先跑 N 个，其余下次再跑」。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/product/refine/CategoryRefineContractTest.java#submit_byFilter_skipsNotOwned_andMovesPastUnknown；修复前失败（total 期望 2 实际含不能维护的商品，skipped 期望 0 实际 2），修复后通过。 |
| BUS-067 | 已修复 | 改选保存原来直接用列表行拼整条 SaveProductRequest。现在先 productApi.get 取最新资料，只换 categoryId 后再保存，不会用列表里的旧值覆盖别人刚改的名称、简介、规格、生命周期。 | zhul-erp-frontend/src/pages/product/products/refine.test.tsx#改选品类先取最新资料再保存，不用列表里的旧值覆盖（audit BUS-067）；修复前提交的是列表旧值（productName 为型号、lifecycleStatus=1），用例失败；修复后通过。 |
| BUS-068 | 已修复 | 根因：updateFees 不拦售后补发订单；部分取消和改费用都会调用 PiSummary.refreshOrder，按收款记录把「无需收款」覆盖成「未付款」。改法：updateFees 遇到 SO_REPLACEMENT 时拒绝（「售后补发订单不收款，不能修改费用」）；refreshOrder 遇到补发订单直接返回，保留「无需收款」；前端补发订单不显示「修改费用」。补发订单仍然可以部分取消（少补的场景）。 | AfterSalesReplacementContractTest#replacementOrder_feesLocked_partialCancelKeepsReceiptFree；在原代码上运行时失败（改费用请求直接成功）；修复后通过。前端只隐藏了按钮，没有单独写测试，靠 tsc 和现有 jest 保证。 |
| BUS-069 | 已修复 | 根因：updateFees 允许费用行为空，费用订单会因此变成永远不会自动完成的 0 元订单。改法：feeRows 结果为空且订单来源是 SO_FEE 时，拒绝并提示「费用订单至少要保留一行费用，不需要了请取消订单」；前端 EditFeesModal 对费用订单在提交前给出同样的提示。 | FeeOrderContractTest#feeOrder_cannotRemoveAllFees；在原代码上运行时失败（删光费用的请求直接成功）；修复后通过。 |
| BUS-070 | 已修复 | 根因：lossOnly 和看板都用转单时拷来的 net_profit_cny 判断，列表「毛利」列却由 orderCostService 按实际成本读时计算，两者口径不同，手动订单的 net_profit_cny 为空。改法：lossOnly 先查出筛选范围内全部进行中的订单，用 orderCostService.of 算出毛利，筛出小于 0 的再在内存里分页，可以和按毛利排序组合（排序抽成 profitPage）；看板「毛利为负的订单」改为同一口径统计。 | SalesOrderContractTest#page_lossOnly_activeNegativeProfit（改写为 BUS-070 见证：PI 亏本订单和手动亏本订单要筛出，预估净利润为负但实际毛利为正的、已取消的不筛出）；在原代码上运行时失败（筛出的订单毛利列为正）；修复后通过。 |
| BUS-071 | 已修复 | 根因：addDiscountOnPayment 用 selectById 无锁读取采购订单，再经 drafts.recalc 用 updateById 把整行写回，会覆盖并发提交的修改（例如登记或撤销已开票）。改法：先调 orderMapper.lockById 锁住采购订单，再读取，与其他采购订单写操作一致。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/finance/PayableContractTest.java#discountOnPayment_locksOrderBeforeReading；测试中另一个事务先修改采购订单的 contract_no，并持锁 800ms 后再提交，同时登记优惠。修复前 contract_no 被写回为空字符串，测试失败；修复后保留 IT-BUS071，测试通过。 |
| BUS-072 | 已修复 | 根因：冲销只撤回挂在本入库单上的闲置库存；部分取消时转入的闲置库存（receipt_id=0）没有处理，合格入库数被扣成负数。改法：新增 PartialCancelHolds.withdrawAfterReversal。ReceiptService.reverse 在本单与本单转入的闲置库存撤回后调用它：订单型号合格数为负时，按差额撤回暂存中的这类闲置库存，从最近转入的开始，整条软删除或减少数量，并写采购订单日志。暂存中的数量不够撤回（已处置）时报「部分取消时转入的闲置库存已处置，不能冲销」，整个冲销回滚。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/sales/SalesOrderPartialCancelContractTest.java#reverseReceipt_withdrawsHoldMovedAtCancel；修复前运行失败：闲置库存已处置时冲销仍然成功（code=0）。修复后通过；同时断言冲销后部分取消转入的闲置库存被撤回，订单已入库数为 0。ReceivingContractTest、OutboundShippingContractTest 也通过。 |
| BUS-073 | 已修复 | 每日补登 backfill 只看询价记录当前的 status==草稿，就把它当成撤回，所以被审核退回、从未生效的回价也会写「回价已撤回」。现在补登路径（不是撤回事件）只有在这条询价记录生效时登记过回价或无货记录（source_type 1/2）时才写撤回记录、重新评估覆盖状态；撤回事件路径保持原样。；补修：已修复：QuoteLinkage.one() 撤回分支的判断由 `!withdrawnEvent && !registered(quoteId)` 改为 `!registered(quoteId)`，撤回事件路径和每日补登路径都要求这条回价先前登记过回价或无货（source_type 1/2），才写「回价已撤回」。生效过的回价再撤回不受影响。在 SupplyAuditFixContractTest | zhul-erp-backend/src/test/java/com/zhul/erp/modules/supply/SupplyAuditFixContractTest.java#bus073_rejectedReviewIsNotWithdrawal；修复前运行失败：退回的待审核回价写出了 1 条撤回记录（期望 0）。修复后通过；同一用例还验证了生效后再撤回仍会补登撤回记录。 |
| BUS-074 | 已修复 | merge 只迁移了信号；专项型号被直接软删除，渠道完全没有处理。现在合并时把被合并方的渠道接到保留方：同一供应商去重；保留方已有主渠道时，被合并方的主渠道降为备选；合并后备选超过 5 个就报错，提示先调整渠道，不会静默丢弃。专项型号在保留方符合专项范围（品牌、细分品类）且不是暂不覆盖时改挂到保留方；专项里已经有保留方时，只移出被合并方。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/supply/SupplyAuditFixContractTest.java#bus074_mergeMovesChannelsAndProjectItems；修复前运行失败：保留方渠道只有 [S1]，期望 [S1, S2, S3]。修复后通过，渠道角色、去重和两种专项情况都已断言。 |
| BUS-075 | 已修复 | DemandRecalc.run 不加锁读出需求对象，再整行回写，会用旧快照覆盖期间人工提交的优先级等字段。现在把需求对象的读取放到第一次读之前，并改为 FOR UPDATE 加锁读取（锁到事务结束）。每日任务的重算事务改为 READ_COMMITTED，避免整租户范围的间隙锁挡住期间新建的需求对象。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/supply/SupplyAuditFixContractTest.java#bus075_recalcKeepsConcurrentManualPriority；修复前运行失败，复现了丢失更新：priority 被改回 1，priority_manual 仍为 1。用例用另一线程持有行锁，并通过 processlist 确认重算被挡住后再提交。修复后通过，priority 保持 3，统计字段照常更新。 |
| BUS-076 | 已修复 | record() 对每个型号调用 coverageService.change，change 每次都执行 evidenceIndex.load，而 load 会全量读取租户的采购订单行。现在 CoverageService 新增 loadEvidence 和带 Snapshot 参数的 change 重载；record 在同一事务内对所选的矩阵内型号只批量加载一次证据，原单参数 change 的行为不变。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/supply/SupplyAuditFixContractTest.java#bus076_recordLoadsEvidenceOnce；修复前运行失败（用 SpyBean 计数）：3 个型号时 evidenceIndex.load 调用了 4 次，期望 2 次（覆盖校验 1 次加记录后重算 1 次）。修复后通过。 |
| BUS-077 | 已修复 | QuoteSupplyCheck.demands 用 deleted_at IS NULL 过滤，并且没有沿合并链查找，合并后用旧型号报价时既不出提示，也不留痕。现在查询不再排除已删除的行：对已合并的行沿 merged_into_id 批量查找保留方（最多 10 层，与 DemandRegistry.find 一致），按保留方的覆盖状态给出提示，发送确认也登记到保留方。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/supply/SupplyAuditFixContractTest.java#bus077_quoteHintsFollowMerge；修复前运行失败：提示为空，期望「货源需确认」。第一次造数时型号键大小写写错，修正测试数据后，我把 QuoteSupplyCheck 临时换回修复前的版本再跑了一次，确认仍然失败；修复后通过。现有单测 QuoteSupplyCheckTest 也通过。 |
| OPS-008 | 已修复 | 根因：SalesWindowJob 把整个循环包在 TenantScope.platform 里，moveToPoolIfDue 和事务提交后发出的通知都绕过了租户拦截器，通知接收人配置在租户之间串用。改法：platform 只用来查到期任务，每个任务放到 TenantContext.runAs(task.tenantId) 里处理，写法与 FollowupReconcileJob 等一致。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/inquiry/sourcing/SalesFirstSourcingContractTest.java#movedByTimeout_poolNoticeUsesOwnTenantReceivers；把 SalesWindowJob 换回 HEAD 版本后运行，测试失败：其他租户配了采购负责人接收人时，本租户收到的待分配通知是 0 条，预期 1 条。修复后通过。BackgroundEntryTenantContractTest 也通过。 |
| OPS-009 | 已修复 | 根因：V1.5.6 插入的 6 个平台参数键名都以 sales.followup. 开头，但文件末尾回退注释写成 LIKE 'followup.%'，匹配不到。已执行的迁移不能修改，改法是新增 sql/build/rollback/V1.5.6__customer_followup_menu_config_rollback.sql 作为正式回退脚本，按 6 个键名逐个删除。V1.5.9、V1.5.11、V1.5.13 也插入了 sales.followup.* 参数，所以不用前缀匹配，避免误删。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/crm/followup/FollowupConfigRollbackScriptTest.java#rollbackDeletesExactlyTheInsertedConfigKeys；修复前测试失败：缺少 V1.5.6 回退脚本，原有唯一的回退语句就是那条匹配不到的 LIKE 'followup.%'。修复后通过：测试从迁移里取出插入的 6 个键，核对回退脚本的 DELETE 只针对 tenant_id=0、不用 LIKE、键集合完全一致。 |
| OPS-010 | 已修复 | 根因：doneTasks 对本人全部已回价任务逐条调用 toVo（每条要查多次库），然后才切页。改法：抽出 myTaskRows 和 toVos；doneTasks 先用一条分组查询（MAX(update_time)，含已删除的回价，口径与 toVo 一致）取出每个任务本人最后回价的时间，与任务更新时间取较新的作为排序键，排序切页后只为当前页组装。排序规则不变。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/inquiry/sourcing/MyTaskDonePageContractTest.java#donePage_sortedByLastQuote_assemblesOnlyCurrentPage；用 SpyBean 监视 InquiryLookups.briefs 的入参。修复前每页都会为全部 3 个任务组装（入参是 [33, 34, 35]），测试失败。修复后每页最多 1 个，并验证顺序按回价时间倒序。SourcingQuoteEnhancementsContractTest、QuoteReviewContractTest、SourcingContractTest 也通过。 |
| SEC-004#1 | 已修复 | 根因：平台给租户重置管理员密码（TenantServiceImpl.resetPassword）只改了 account_local_auth 的密码，没有撤销该管理员的会话；而认证过滤器和 /auth/refresh 只认 Redis 会话，所以旧 AccessToken 和「记住我」RefreshToken 都还能用。改法：改完密码后调用 accountSessionService.revokeAllOfUser(adminUser.getId())，和员工管理重置密码的写法一致。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/system/SessionSecurityContractTest.java#platformResetTenantAdminPasswordRevokesSessions；修复前在 zhul_erp_test_platform_db 上运行失败：断言「平台重置管理员密码后旧 AccessToken 失效」expected <false> but was <true>。修复后通过，同时验证 RefreshToken 键已删除、account_access_token 里没有未撤销的会话。 |
| SEC-020 | 已修复 | create() 原来只校验租户和客户归属，没有调用数据范围判断。现在选了订单时要求订单负责人在当前用户的数据范围内，否则提示「订单不存在」；没选订单时要求客户负责人在范围内，否则提示「客户不存在」。写法与 RefundRequestService.visibleOrder 一致。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/sales/AfterSalesContractTest.java#create_followsOrderAndCustomerScope；修复前，本人范围的业务员对别人的订单登记售后返回 code=0，用例失败。修复后两种情况都被拒绝；订单和客户改回本人名下后可以正常登记。 |
| SEC-021 | 已修复 | 根因：commonmark 的 sanitizeUrls 放行 http(s) 图片，生成 <img src> 后交给 LibreOffice 转换时会去请求该地址。改法：BackgroundReportHtml 注册自定义 NodeRenderer，Markdown 图片（含引用式图片和 data:）一律渲染成「[图片：说明]」文字，不生成 img，也不输出地址。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/masterdata/customerbackground/BackgroundReportHtmlTest.java#markdownImages_areNotRenderedAsExternalResources；修复前失败：输出里有 <img src="http://10.0.0.8/admin.png"> 和 169.254.169.254 外链；修复后通过。 |
| SEC-022 | 已修复 | 根因：采购退货的 scoped、visible 和附件可见判断只看负责人，规格要求的「负责人或采购订单的采购员在可见范围内」没有实现。改法：scoped() 改为 (owner_id 在范围内 OR po_id IN 范围内采购员的采购订单)，子查询复用 ReceivingSupport.scopedPoSql；visible() 和 AttachmentOwnerScope.purchaseReturnVisible 也放行采购订单的采购员。列表、页签数、详情、寄出、完成、采购订单详情里的采购退货和附件都按新范围处理。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/attachment/RelatedDocScopeContractTest.java#purchaseReturn_visibleToPurchaserOfItsOrder；修复前运行失败：采购订单的采购员查采购订单详情里的采购退货，得到 0 条，预期 1 条。修复后通过；同时断言别人的采购订单、别人负责的采购退货仍然看不到，附件链接返回 403。 |
| SEC-023 | 已修复 | 开发任务时间线里，加候选和关联供应商库的记录正文带有候选名称和供应商名称；这两类记录写成人工记录，records() 只遮蔽系统记录，所以它们原样返回。现在读取时，如果没有 inquiry:supplier:view，就把这两种模板生成的正文替换为「加候选」或「候选关联供应商库」。读取时处理，所以线上历史数据也一并覆盖，用户自己写的处理内容原样返回。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/supply/SupplyAuditFixContractTest.java#sec023_candidateNamesHiddenInDevTimeline；修复前运行失败：只有供应链跟进菜单的账号，在 GET /projects/{id} 的 dev.records 里看到了「加候选「IT甲供应商」」等名称。修复后通过，有权限的账号仍能看到名称。 |
| UI-004#1 | 已修复 | 根因：PageTabs 的 store 在 history.listen 里把 POP 交给 model.navigated；如果浏览器后退正好退到当前标签的上一页，就直接出栈，不检查栈顶是否 dirty，页面被卸载、草稿丢失。useUnsavedGuard 只挂了 beforeunload。改法：store 增加 setLeaveConfirm 登记入口。POP 会让带未保存修改的栈顶出栈时，先 history.push 回到当前页地址，再调用确认；选「离开」走 tabsActions.back()，和返回条行为相同。PageTabs 组件用 App.useApp 的 modal.confirm 登记弹窗，文案与返回条一致。没有用 history.block，符合规范。 | zhul-erp-frontend/src/components/PageTabs/store.test.ts#浏览器后退离开未保存的页面 › 先确认，不出栈；地址回到当前页；zhul-erp-frontend/src/components/PageTabs/store.test.ts#浏览器后退离开未保存的页面 › 确认离开后出栈，回到上一页；zhul-erp-frontend/src/comp；修复前，用一个临时的空 setLeaveConfirm 让测试能加载，跑旧逻辑，「先确认，不出栈」失败：确认没有被调用（Expected calls 1, Received 0），dirty 页面被直接出栈。修复后 store.test.ts 5 条全部通过，PageTabs 和 Shell 目录 7 个测试文件共 26 条通过。 |
| UI-005#1 | 已修复 | 向导是 layout:false 页面，没有 entryKey，useTabDirty 不起作用。改用 useUnsavedGuard（刷新、关闭浏览器时提示），并在补充资料未保存时用 history.block 拦住浏览器后退和站内跳转，弹出「补充资料还没保存」确认，确认后 retry。向导自己的离开按钮先设 leaving 标记再放行，不会重复确认。 | zhul-erp-frontend/src/pages/product/products/wizardLeave.test.tsx#第 4 步填了资料没保存，浏览器后退先确认（audit UI-005）；修复前，走到第 4 步填写名称后按浏览器后退，直接回到列表、没有提示，用例失败；修复后先弹确认，点「不保存，离开」才后退。 |
| UI-055#1 | 已修复 | 客户跟进页（index.tsx）增加 useOnReactivate：返回时如果是「我的」视图且没有弹窗开着，就重新取计数和今日待办（loadTop），在板块里时再取一次列表第一页（loadList(1)）。记跟进、批量、转培育、转商机、停止开发、新建开发对象、建询盘这些弹窗开着时不刷新，避免冲掉草稿。 | zhul-erp-frontend/src/pages/sales/followup/index.test.tsx#返回时重新取今日待办与计数，弹窗开着时不刷新；临时换回修复前的 index.tsx 运行，重新激活后 overview 只调用 1 次（期望 2 次），用例失败。修复后通过。 |
| UI-055#2 | 已修复 | 根因：商机列表只注册了 useOnReactivate(load)，统计卡片只在挂载时取一次。改法：返回时同时调用 load() 和 loadSummary()。 | zhul-erp-frontend/src/pages/crm/opportunity/index.test.tsx#返回列表时统计卡片与表格一起重新取数；修复前失败：页面切回可见后 summary 只调用了 1 次（expected 2）；修复后通过。 |
| UI-055#3 | 已修复 | 型号清单加了 useOnReactivate：从详情返回时 tick 加 1，静默重取列表和分布，列表不清空、不整页重建。团队视图返回不刷新是 UI-055 当时的明确取舍，复核也认为不算缺陷，所以没有改。 | zhul-erp-frontend/src/pages/supply-chain/coverage/models.test.tsx#UI-055：从详情返回型号清单时重新取数；修复前运行失败：页面从隐藏切回可见后，models 接口没有再次请求。修复后通过，并且断言了列表一直在。 |
| UI-055#4 | 已修复 | 报销单列表页签 _ListTab 补上 useOnReactivate(load)，从详情返回时重新取数。load 中同时用最新数据替换已勾选的报销单，不再是待付款的剔除，批量登记已付不会带上已撤回或已付的单。 | zhul-erp-frontend/src/pages/finance/reimbursements/listReactivate.test.tsx#报销单页签返回时重新取数；修复前：标签页从隐藏回到可见后，列表接口调用次数没有增加（Expected 2，Received 1）。修复后会重新取数。 |
| UI-055#5 | 已修复 | 原因：客户详情的 useOnReactivate 只重取客户和档案，背调报告、背调前检查、跟进概况都没有刷新。修复：切回页面时一并调用 bg.reload()、precheck.reload()、followup.reload()；时间线自己在切回时按当前筛选、按已加载的条数重取；主动开发状态也随切回重取（useDevelopCard 的 version 加上切回次数）。；补修：已修复。复核意见成立：后端 FollowupService 用 RECORD_PAGE_MAX=50 限制了单次返回条数（已核对源码第 120、1118 行）。修改了 zhul-erp-frontend/src/pages/customer/FollowupSection.tsx：FollowupTimeline.load 在 p===1 时改为 setPage(Math.max(1, Math. | zhul-erp-frontend/src/pages/customer/detail.test.tsx#切回页面：静默刷新，子卡片的数据一并重取；zhul-erp-frontend/src/pages/customer/FollowupSection.test.tsx#切回页面时按当前筛选重取已加载的条数；修复前，切回后跟进分区被骨架屏卸载，detail 用例在第 152 行失败；时间线切回时没有发出 page=1、pageSize=10 的请求，用例失败。修复后两者都通过，并确认 backgroundApi.list、precheck、customerSummary 都被调用了 2 次。 |
| UI-055#6 | 已修复 | 原因：切回时调用的 load() 会先 setLoading(true)，整个主栏和右栏被换成骨架屏并卸载，子组件的临时状态全部丢失。修复：load 增加 silent 参数，切回时调用 load(true)：不设 loading、不清 error，取数失败时保留原内容（只有「不存在」时才转为空状态），成功后清掉 error。其他调用点和 ErrorHint 的重试（改为 () => load()）行为不变。页面不再卸载，SOP 勾选、时间线筛选和已加载的页数、背调卡的展开状态都能保留。 | zhul-erp-frontend/src/pages/customer/detail.test.tsx#切回页面：静默刷新，子卡片的数据一并重取；修复前，rerender 为 active=true 后找不到「跟进」区域（已被骨架屏替换），用例失败；修复后同一个 DOM 节点在切回前后保持不变。 |
| UI-055#7 | 已修复 | 商品列表的 useOnReactivate 改为同时调用 load 和 loadSummary；候选页同时调用 load 和 refreshCount，返回时列表和计数一致。 | zhul-erp-frontend/src/pages/product/products/refine.test.tsx#从其他页面返回时列表和顶部计数一起刷新（audit UI-055）；zhul-erp-frontend/src/pages/product/candidates/aiFillPolling.test.tsx#从其他页面返回时页签计数一起刷新（audit UI-055）；修复前重新激活页面后计数接口只调用 1 次，两个用例都失败；修复后通过。 |
| UI-056#1 | 已修复 | ProjectModal：范围变化时先清空型号、勾选、进行中专项、开发范围和线索数；candidates、devScope、devGroups 都用 liveRequests，effect 清理时丢弃迟到的响应；重新打开时把名称一并清掉。ExpansionModal：换品牌或品类时先清掉 range，devScope 也用 liveRequests，迟到响应和请求间隙里都不会按旧范围的 expansionId 去调用 updateExpansion。 | zhul-erp-frontend/src/pages/supply-chain/dialogs.test.tsx#UI-056：建专项重新打开或换范围后，不残留上一个范围的进行中专项与勾选；zhul-erp-frontend/src/pages/supply-chain/dialogs.test.tsx#UI-056：建专项丢弃上一个范围迟到的候选型号；zhul-erp-frontend/src；修复前四条都失败：一类是残留旧范围的「去查看」（activeId=7），另一类是旧范围的「修改主动拓展」标题。修复后通过，并断言最终调用的是 addExpansion，而不是 updateExpansion。 |
| UI-056#2 | 已修复 | 原因：MergeModal 的预览 effect 依赖 [pair]，结果直接 .then(setPreview)，没有清理，旧方向的预览迟到时会覆盖当前方向。修复：改用 liveRequests，在 effect 清理时 cancel，与 ManualMergeModal、GradeCard 的写法一致。 | zhul-erp-frontend/src/pages/customer/profile/DuplicatesTab.test.tsx#对调保留方：丢弃旧方向迟到的合并预览；修复前，对调后旧方向的预览（9 张销售订单）晚到并覆盖了当前方向，页面找不到「1 张销售订单改挂到 Beta Ltd」，用例失败；修复后通过。 |
| UI-058#1 | 已修复 | 原因：ManualMergeModal 的 onDone 在保留方不是当前客户时调用 history.push，在已被软删除的客户详情之上又压了一层。修复：改用 history.replace 替换当前栈顶，做法与 supply-chain/demand 合并后的处理一致。 | zhul-erp-frontend/src/pages/customer/detail.test.tsx#当前客户被合并：用替换打开保留的客户；修复前用的是 history.push，replace 没有被调用，用例失败；修复后断言 history.replace('/customer/list/99') 被调用、history.push 没有被调用，用例通过。 |
| UI-058#2 | 已修复 | 向导 close() 的各分支（不保存离开、保存并离开、草稿离开、直接离开）统一调用 leave()，即 tabsActions.backOr('/product/products')。商品详情删除后也改用 backOr，并去掉不再使用的 history 导入。 | zhul-erp-frontend/src/pages/product/products/wizardLeave.test.tsx#直接关闭向导时退回上一页，不再压一层列表（audit UI-058）；修复前点关闭走 history.push，backOr 没有被调用，用例失败；修复后通过。详情页删除这条没有单独写测试：详情页依赖的卡片和接口太多，用 tsc 和现有测试保证没有改坏。 |
| UI-060 | 已修复 | 原因：loadWizard 同时请求 wizard 和 profile，两边的结果都直接写入状态，也不核对当前选中的是哪个客户；切换客户时不清空旧档案，所以联系人对不上时会被静默跳过，却仍提示「已保存」。修复：新增请求序号 wizardReq，迟到的响应直接丢弃；切换客户时同时清空 wizard 和 profile；只把 customerId 一致的档案交给 Wizard，并加 key 让 Wizard 随客户重新挂载。联系人还没取到（加载中或取数失败）时，联系人项不能点选，「去填写」和「去补充」改为报错提示；保存前如果有联系人项但对应联系人不存在，直接报错，不再显示已保存。 | zhul-erp-frontend/src/pages/customer/profile/CompletionTab.test.tsx#切换客户：丢弃上一个客户迟到的档案；zhul-erp-frontend/src/pages/customer/profile/CompletionTab.test.tsx#档案取数失败：联系人项不能点选；修复前两个用例都失败：第一个 updateContact 调用次数为 0（联系人项被静默丢弃）；第二个在档案取数失败时仍能找到「直接型」点选按钮。修复后两个都通过。 |
| UI-061 | 已修复 | 收款单 Table 的 onChange 不区分触发来源，翻页时也会 setPage(1)。改为与其他列表一致：extra.action !== 'sort' 时直接返回。 | zhul-erp-frontend/src/pages/finance/receipts/RecordsTab.test.tsx#收款单点第 2 页后按第 2 页取数；修复前：点第 2 页后，最后一次请求的 page 是 1（Expected 2，Received 1）。修复后测试通过。 |
| UI-062 | 已修复 | 根因是候选页轮询到任务完成后调用 changed()，里面会 setViewing(undefined) 关掉审核抽屉。现在完成时只刷新列表和页签计数，抽屉保持打开、里面的修改保留。 | zhul-erp-frontend/src/pages/product/candidates/aiFillPolling.test.tsx#AI 解析跑完不关掉正在审核的抽屉（audit UI-062）；修复前该用例在「轮询完成后抽屉仍在」这一步失败（找不到「审核候选」），修复后通过。 |
| UI-063 | 已修复 | 根因：新增「售后退货冲减」列后，合计行仍写死 colSpan=10、index 10–13；补发订单过滤掉 3 列后同样会错位。改法：按 shown 中 dataIndex 为 cost 的列号算出 lead，合计行用 colSpan=lead，之后的合计依次放在 lead 到 lead+3 列。 | zhul-erp-frontend/src/pages/sales/orders/costCard.test.tsx#普通订单：合计对齐实际成本、退款、行收入、行毛利；zhul-erp-frontend/src/pages/sales/orders/costCard.test.tsx#售后补发订单：只有实际成本合计，也对齐；修复前两个用例都失败（实际成本合计落在第 10 列，表头在第 11 列）；修复后通过。 |
| UI-064 | 已修复 | RecordFollowupModal 增加 latestRef，每次渲染时同步最新的 nextAction 和 dateTouched。applyResult 改为按 latestRef 判断，AI 整理期间手填的下一步、手选的日期不再被 AI 建议覆盖。 | zhul-erp-frontend/src/pages/sales/followup/RecordFollowupModal.test.tsx#整理期间手填的下一步不被覆盖；zhul-erp-frontend/src/pages/sales/followup/RecordFollowupModal.test.tsx#整理期间手选的日期不被覆盖；用 fake timers 模拟两次轮询之间手填内容。修复前：下一步变成了 AI 建议；手选的「3 个月后」被 AI 日期覆盖，按钮不再高亮。两个用例都失败，修复后通过。 |
| UI-065 | 已修复 | 售后单详情和销售退货详情原来都是无条件 useOnReactivate(load)。现在改成 if (!dialog) load()，与费用详情的写法一致：处理、结案、补发、确认退货等弹窗开着时返回本页不重新取数，弹窗内容不会因为 data 变化被重置。 | zhul-erp-frontend/src/pages/sales/after-sales/detail.test.tsx#弹窗开着时返回不刷新，关着时返回刷新；zhul-erp-frontend/src/pages/sales/returns/detail.test.tsx#确认退货弹窗开着时返回不刷新，关着时刷新；修复前，弹窗开着时重新激活页面会再次调用 detail 接口（调用次数为 2，期望 1），两个文件的用例都失败。另有对照用例「没有弹窗时返回重新取数」，修复前后都通过。修复后全部通过。 |
| UI-066 | 已修复 | 售后单详情的跟进记录框改为调用 useUnsavedGuard(note.trim() 非空或已上传附件)，登记为未保存，关闭标签、刷新或点返回时会先确认；保存后清空，登记随之解除。 | zhul-erp-frontend/src/pages/sales/after-sales/detail.test.tsx#跟进记录草稿登记未保存；修复前，输入跟进内容后 tabsActions.setDirty 从未被调用（返回 undefined），用例失败。修复后通过。 |
| UI-067 | 已修复 | 后端 AfterSalesQuery 增加 reasonCode 字段，page() 按原因过滤。前端售后管理列表增加客户筛选（搜索选择，写法与销售退货列表相同）和原因筛选（字典 after_sales_reason）；客户筛选用的是后端原有的 customerId。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/sales/AfterSalesContractTest.java#page_filtersByReasonAndCustomer；zhul-erp-frontend/src/pages/sales/after-sales/index.test.tsx#按客户与原因筛选；修复前，后端按原因筛选返回 2 条（期望 1 条），用例失败。前端用例在临时换回修复前的 index.tsx 后失败，报错为找不到「原因」筛选框；修复后通过。 |
| UI-068 | 已修复 | 根因：前端把培育天数写死为 60，后端用的是租户参数。改法：商机详情 VO 增加 nurtureDefaultDays（FollowupSettings 取租户参数）；输单弹窗显示 detail.nurtureDefaultDays，缺失时回退到常量 60。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/crm/OpportunityApiContractTest.java#detail_returnsTenantNurtureDefaultDays_matchingCloseNurtureDate；zhul-erp-frontend/src/pages/crm/opportunity/dial；后端修复前失败：nurtureDefaultDays 为 0（expected 30）。前端把 dialogs.tsx 换回 HEAD 版本跑测试，找不到「转入培育，30 天后提醒重新激活」；换回修复版后通过。 |
| UI-069 | 已修复 | 原因：Wizard 里写的是 useEffect(() => setValues({}), [w])，每次刷新向导都会清空点选；ModuleEditModal 和 ContactModal 的 effect 依赖由 profile 派生的 initial / contact，打开期间档案一刷新就重置表单。修复：刷新向导时只去掉已经不再缺的项，其余点选保留，换客户时靠 Wizard 重新挂载来清空。ModuleEditModal 只在打开或换模块时，按当时的档案填表并记下快照，保存时只提交相对快照改过的字段，避免把未改动的字段覆盖成刷新后的值。ContactModal 只在打开或联系人 id 变化时重新填表。 | zhul-erp-frontend/src/pages/customer/profile/CompletionTab.test.tsx#刷新向导：保留还没保存的点选；zhul-erp-frontend/src/pages/customer/profile/ModuleEditModal.test.tsx#档案编辑弹窗：打开期间档案刷新不重置已改的字段；zhul-erp-frontend/src/p；修复前三个用例都失败：勾选「已经问了，等回复」后，按钮从「保存，下一个客户」变回「下一个客户」；两个弹窗在 rerender 新档案对象后，输入框都被还原成旧值。修复后全部通过。 |
| UI-070 | 已修复 | 原因：「重试」只调用 loadQueue(1)，既不清掉 error，也不设置选中客户。修复：把首次加载抽成 init，在其中 setLoading、setError(undefined)，取到队列后选中第一个客户；useEffect 和 ErrorHint 的 onRetry 都调用 init。 | zhul-erp-frontend/src/pages/customer/profile/CompletionTab.test.tsx#队列加载失败后重试成功：清掉错误并选中第一个；修复前，点「重试」且请求成功后页面仍显示「加载失败」，用例失败；修复后通过，并确认 wizard 请求的是第一个客户。 |
| UI-071 | 已修复 | 原因：RFM 明细里的订单号链接和「先看上次的报告」都直接 history.push，弹窗挂在 body 上，跳页后不会随旧页面隐藏。修复：跳页前先调用 onClose()（RfmModal）或 onCancel()（RecentModal，即 setRecent(undefined)）关掉弹窗。 | zhul-erp-frontend/src/pages/customer/profile/GradeCard.test.tsx#点订单号：先关掉 RFM 明细再跳到销售订单；zhul-erp-frontend/src/pages/customer/background/components.test.tsx#先看上次的报告：先关掉弹窗再跳到报告页；GradeCard 用例修复前 onClose 调用次数为 0，失败；components 用例修复前「先看上次的报告」按钮仍然可见，失败（把源码改动临时撤回后复测确认）。修复后都通过。 |
| UI-072 | 已修复 | 原因：FollowupTimeline 的 load 直接写入 page、total、records，没有请求序号，旧筛选迟到的第 2 页会被追加进新列表。修复：沿用客户跟进页 reqRef 的写法，每次请求自增序号，then、catch、finally 都只处理最新一次请求。 | zhul-erp-frontend/src/pages/customer/FollowupSection.test.tsx#切换筛选后丢弃旧筛选迟到的「加载更早的」；修复前，切到「业务员记录」后，旧筛选「全部」的第 2 页迟到并被追加，页面出现「全部6」，用例失败；修复后通过。 |
| UI-073 | 已修复 | 原因：usePageTitle 和 CreditCard 用的是 record.name，经商机登记的客户名称为空时，标签只显示「客户 」。修复：标题、标签和 CreditCard 的 customerName 都改用 displayName，与页面其他位置一致。 | zhul-erp-frontend/src/pages/customer/detail.test.tsx#客户名称为空：标题和流水抽屉用展示名；修复前 setTitle 收到的参数是 ('e1', '客户 ', '')，用例失败；修复后为 ('客户 John Smith', 'John Smith')，流水抽屉也显示 John Smith。 |
| UI-074 | 已修复 | BatchPayModal 中按付款方式重算我方账户的 effect，现在同时处理对方账户：原选择不在新方式的过滤范围内时，改选该组的默认账户，没有默认就清空，不再提交界面上看不到的账户。 | zhul-erp-frontend/src/pages/finance/payables/BatchPayModal.test.tsx#切换付款方式后不提交已隐藏的对方账户；修复前：切到私对私后提交，groups[0].payeeAccountId 仍是 21（Received 21）。修复后为 undefined。 |
| UI-075 | 已修复 | 逐笔未付金额由 formatAmount(r.unpaidAmount) 改为 formatAmount(r.unpaidAmount, g.currency)。 | zhul-erp-frontend/src/pages/finance/payables/BatchPayModal.test.tsx#外币应付的逐笔未付金额标该组币种；修复前：USD 组的这一行渲染为「AP001 CNY 1,000.00」（首次运行的失败输出里能看到这行）。修复后为「AP001USD 1,000.00」，测试通过。 |
| UI-076 | 已修复 | 应付列表 load 取数后，用最新行数据替换已选中的同一张应付；已付清、已关闭、未付为 0 的从已选中剔除。可勾选条件提成 payableOpen，与 getCheckboxProps 共用。 | zhul-erp-frontend/src/pages/finance/payables/pickedRefresh.test.tsx#刷新后已选的应付随列表更新；修复前：列表刷新后这张已付清，横幅仍显示「已选 1 张 · 1 家收款方」。修复后横幅消失。 |
| UI-077 | 已修复 | RequestsTab 与 request-detail 两处 ReasonModal 的驳回 onOk 加上 try/catch：失败时 message.error(readBizError(e).message) 并保留弹窗，成功后才提示「已驳回」并刷新。 | zhul-erp-frontend/src/pages/finance/refunds/rejectError.test.tsx#列表驳回失败时提示后端原因；zhul-erp-frontend/src/pages/finance/refunds/rejectError.test.tsx#详情驳回失败时提示后端原因；修复前两个用例都失败：页面上找不到后端返回的提示，异常作为未处理的拒绝被抛出。修复后两个用例都通过。 |
| UI-078 | 已修复 | 月度核对的 useEffect 改用 liveRequests，清理函数里 cancel，切换月份、币种或事业部后丢弃上一次的迟到响应。为了写测试，把 ReconTab 改为具名导出。 | zhul-erp-frontend/src/pages/finance/credits/reconTab.test.tsx#月度核对丢弃切换前迟到的响应；修复前：切到 RUB 后，迟到的 USD 结果覆盖了页面，显示出「USD 客户」。修复后旧响应被丢弃，测试通过。 |
| UI-079 | 已修复 | 根因：去报价弹窗和规格写着「询价任务照常进行，回价后可出新版本」，与 BUS-031 中用户选定的规则不一致（报价单发出后型号锁定，任务完结）。改法：按已定规则改正文案，弹窗提示、组件注释和 openspec/specs/quotation/quotation/spec.md 第 12 行都改为「报价单发出后算已报给客户，采购不能再回价，询价任务随之完结（报价单作废后重新开放）」。规则本身没有改。 | zhul-erp-frontend/src/pages/inquiry/customer-inquiry/GoQuoteModal.test.tsx#勾未回价型号的提示说明发出后采购不能再回价；修复前运行失败：页面上找不到「采购不能再回价」，显示的还是旧文案「询价任务照常进行」。修复后通过。 |
| UI-080 | 已修复 | 根因：只有点「完成」或点结果行才会调用 finishBatch 刷新列表；点抽屉右上角 X 或遮罩关闭直接走 onClose，列表不刷新。改法：有批量结果时，Drawer 的 onClose 改用 finishBatch。 | zhul-erp-frontend/src/pages/quotation/NewQuotationModal.test.ts#生成后用右上角关闭也刷新列表；修复前运行失败：点 X 关闭后 onBatchDone 调用 0 次，预期 1 次。修复后通过。测试文件用了 jest.mock，按仓库已有写法用 React.createElement 渲染，所以扩展名是 .ts。 |
| UI-081 | 已修复 | 根因：工作台「售后要更新客户」的数字用 myDueCount 只统计本人负责的单据，但链接只带了 overdue=soon；售后列表按数据范围查询，地址里的 ownerId 也不读。改法：按 spec「本人负责的」口径，SalesWidgets 的链接加上 &ownerId=本人（通过 CurrentUserResolver 取当前用户）；售后列表 after-sales/index.tsx 从地址读 ownerId，作为负责人筛选的初始值。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/workbench/WorkbenchContractTest.java#afterSalesTodos_mine_andTeamOverdue；修复前后端测试失败：expected </sales/after-sales?overdue=soon&ownerId=99000002> but was </sales/after-sales?overdue=soon>。修复后通过，并新增一段：用数据范围为「全部」的角色调用，待办数为 3；按链接条件 POST /api/v1/sales/after-sales/page（dueSoon=tru |
| UI-082 | 已修复 | 根因：NotificationBell 的 load 在 await list 后直接写 items/total/page，useEffect 也没有清理函数，所以先发出的旧标签请求如果后返回，会覆盖当前标签。改法：按项目约定使用 liveRequests。抽屉打开或切换标签时，effect 新建当前标签的 live 并存进 ref；load 里的请求（包括「加载更多」和「全部已读」后的重载）都用 live 包起来；切换标签或关闭抽屉时 cancel，同时复位 loading。 | zhul-erp-frontend/src/components/Shell/NotificationBell.test.tsx#切换标签后，上一个标签迟到的响应不覆盖当前列表；修复前 jest 失败：「全部」的响应先返回、「待处理」的响应后返回，结果列表被待处理的数据覆盖，找不到「全部标签的通知」。修复后通过，旧响应被丢弃，「加载更多」也不会因为错误的 total 出现。 |
| UI-083 | 已修复 | 轮询的 useEffect 只依赖 taskId，回调拿到的是发起任务时的旧 load。商品列表和候选页都改为用 ref 保存最新的取数函数（reload.current），任务完成时按当前的筛选、页签、页码刷新。 | zhul-erp-frontend/src/pages/product/products/refine.test.tsx#AI 细分跑完按当前筛选刷新，不按发起时的旧筛选（audit UI-083）；zhul-erp-frontend/src/pages/product/candidates/aiFillPolling.test.tsx#AI 解析跑完按当前页签刷新，不按发起时的旧页签（audit；修复前两个用例的最后一次取数都是旧条件（没有 missing=refine、status=1），用例失败；修复后通过。 |
| UI-084 | 已修复 | 后端两处任务状态查询在任务查不到时带固定错误码 AI_TASK_NOT_FOUND（ProductErrorCodes）。前端轮询的 catch 只在这个错误码时停止跟踪并清掉本地记录，网络错误、5xx 等下一轮继续查。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/product/refine/CategoryRefineContractTest.java#status_unknownTask_hasNotFoundCode；zhul-erp-backend/src/test/java/com/zhul/erp/modules/product/candid；后端：临时恢复旧异常写法时两个用例失败（响应没有 errorCode）。前端：修复前网络错误一次就停止轮询，两个页面的「继续跟踪」用例都失败。修复后全部通过。 |
| UI-085 | 已修复 | 根因：删除订单后 onDeleted 调用 history.replace('/sales/orders')，标签栈里留下重复的列表页，从其他单据进来时也回不到来源页。改法：改为 tabsActions.backOr('/sales/orders')，有上一页就退回保活的上一页（列表通过 useOnReactivate 刷新），没有才替换成列表。 | zhul-erp-frontend/src/pages/sales/orders/leaveAfterDelete.test.ts#删除订单后用 tabsActions.backOr 离开；用 TypeScript AST 检查 detail.tsx 中 DeleteOrderModal 的 onDeleted。修复前失败（内容里是 history.replace），修复后通过。整页渲染详情页的成本太高，所以用源码检查代替。 |
| UI-086 | 已修复 | 新增一个 effect：阶段、无货、统计期、待确认、单一渠道、品牌、品类、优先级、关键词、负责人、信号来源任一变化时，清空 picked 和 pickedSignals。「延后」改用 pickedDemands 的 id，和「记处理」作用于同一批型号，也和计数一致。 | zhul-erp-frontend/src/pages/supply-chain/followup/index.test.tsx#UI-086：换筛选条件后清空勾选，批量栏不再作用于看不见的型号；修复前运行失败：切换 chip 后批量栏仍显示「已选 2 条」。修复后通过。 |
| UI-087 | 已修复 | 供应商开发板块的筛选栏新增「二级品类」下拉，绑定当前生效的 categoryId，可以看到、可以清除；选了一级品类时，只列出该一级下的二级品类。从其他板块、待办或矩阵带进来的品类都会显示在这里。 | zhul-erp-frontend/src/pages/supply-chain/followup/index.test.tsx#UI-087：供应商开发板块显示并可清除带进来的二级品类；修复前运行失败：筛选栏里找不到「品类A」。修复后通过。 |
| UI-088 | 已修复 | 「去询价」「去查看」原来是原生 href，会整页跳转。现在改为先关闭弹窗，再 history.push 站内跳转：「去查看」用 Typography.Link，不带 href。客户询价的 initialFilters 读取地址里的 keyword，有关键词时按型号查，不限日期。 | zhul-erp-frontend/src/pages/supply-chain/dialogs.test.tsx#UI-088：「去询价」在站内打开客户询价并带型号关键词，不整页跳转；zhul-erp-frontend/src/pages/supply-chain/dialogs.test.tsx#UI-088：建专项提示「去查看」在站内打开专项；zhul-erp-frontend/src/pa；修复前三条都失败：前两条渲染出来带 href，第三条 initialFilters 返回的是近 3 个月的日期范围。修复后全部通过。 |
| UI-089 | 已修复 | (a) 团队视图下钻时带上统计期 days；跟进页新增 stockoutDays 状态，传给 /groups，chip 显示「近 N 天无货」；下钻时同时清掉关键词和优先级。(b) 后端负责人表本来就不出未指定负责人的行，前端把 ownerId 为空的行改为不可点，jump 不再把 0 映射成「全部」。(c)(d) /matrix/models 新增 ownerId、stockoutDays 参数；格子链接带上负责人、优先级或无货统计期；型号清单读取这些参数，新增「负责人」「无货」下拉，可以清除，并把同样的筛选传给分布统计。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/supply/SupplyChainContractTest.java#matrixModelsOwnerAndStockout；zhul-erp-frontend/src/pages/supply-chain/followup/index.test.tsx#UI-089：团队视图近 90 天下；后端用例修复前失败：按负责人筛选仍返回两个型号。前端四个用例修复前都失败：请求缺少 stockoutDays，或缺少 ownerId 和 stockoutDays，或链接没带参数。修复后全部通过。 |
| UI-090 | 已修复 | 型号清单把筛选和页码合成一个 Filters 状态：改筛选时一次就回到第 1 页，不再先按旧页码多发一次请求。列表和分布统计的取数都用 liveRequests，effect 清理时丢弃迟到的响应。覆盖矩阵的 load 加了请求序号，旧模式的结果和错误都不会覆盖新结果。 | zhul-erp-frontend/src/pages/supply-chain/coverage/models.test.tsx#UI-090：非第一页改筛选只发一次第 1 页的请求，迟到的旧页不覆盖；zhul-erp-frontend/src/pages/supply-chain/coverage/models.test.tsx#UI-090：迟到的旧请求结果被丢弃；zhul-erp-fron；修复前三条都失败：一条是请求发了 2 次，另两条是迟到的旧数据 M-999、旧品牌被渲染出来。修复后通过。 |
| UI-091 | 已修复 | today 失败时不再塞全 0 的对象，改为记下错误；TodayView 显示「今日待办加载失败」和重试按钮。overview 失败时保留原有数据、记下错误；还没有数据时，SupplyFlow 显示失败和重试，不再一直转骨架屏。TeamView 失败时显示原因和重试，同时用 liveRequests 丢弃切换统计期后迟到的响应。 | zhul-erp-frontend/src/pages/supply-chain/followup/index.test.tsx#UI-091：今日待办取数失败显示加载失败并可重试，不显示「都处理完了」；zhul-erp-frontend/src/pages/supply-chain/followup/index.test.tsx#UI-091：概览取数失败显示加载失败并可重试，不一直显示骨架屏；；修复前三条都失败，页面上找不到重试按钮或失败提示。修复后通过。 |
| UI-092 | 已修复 | useOptions 的模块级缓存改为带时间戳的条目：品牌或品类任一请求失败时，这次结果不缓存，下次进页面重新取；缓存 5 分钟后过期重取，和字典缓存的 TTL 一致，新建的品牌、品类能看到。组件卸载后不再 setState。 | zhul-erp-frontend/src/pages/supply-chain/components.test.tsx#UI-092：取数失败不缓存，下次进页面重取；修复前运行失败：第一次失败后，第二次挂载拿到的品牌仍为空，接口没有被重新调用。修复后通过。 |
| UI-093 | 已修复 | SignalVO 补上 createTime、createBy、updateTime、updateBy，原来的 copyProperties 会自动带出。SignalTable 在来源单号之后、操作之前加了这四列，scroll 改用 tableScrollX，可切换每页条数；跟进页用 usePageSize(20, 'signals') 记住条数。型号清单、专项明细、团队负责人表都不再用 'max-content'：改为 tableScrollX，负责人表各列补了宽度。 | zhul-erp-backend/src/test/java/com/zhul/erp/modules/supply/SupplyChainContractTest.java#unclassifiedSignalsCarryAuditFields；zhul-erp-frontend/src/pages/supply-chain/followup/SignalTable.test.tsx#UI-09；后端用例修复前失败：createBy 为空。前端用例修复前失败：表头里没有「创建时间」。修复后都通过。表格横向滚动这部分属于纯展示，没有单独写测试，靠 tsc 和现有测试保证不出错。 |

### 验证

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| 后端全量 `mvn test` | 本机，集成分支 `fix/audit-1115` | 全部通过 | 1749 条：1697 通过；52 条报错均为 MySQL「Too many connections」环境问题，涉及的 3 个测试类（ProductCoreIntegrationTest 42 条、AfterSalesReturnConcurrencyContractTest 2 条、SupplyAuditFixContractTest 8 条）单独重跑 52 条全部通过 | 通过（环境错误已重跑排除） |
| 前端 `npm run lint`、`npx antd lint ./src`、`npm test` | 本机，同上 | 全部通过 | Biome、tsc、antd 检查通过；Jest 114 个文件 469 条通过 | 通过 |
| AI 编排服务 `unittest`（2GB 限内存容器） | 本机，同上 | 全部通过 | 151 条通过 | 通过 |

### 本机验收（2026-11-15）

在本机用集成分支重建后端、前端、编排服务镜像，复用验收租户 1001（管理员、模板「业务员」角色账号）。没有到生产。

| 验收项 | 方式 | 结果 |
| --- | --- | --- |
| BUS-042 授信退款作废后可用余额 | 接口：登记授信、作废后申请退款 | 作废后申请 582 被拒「可用余额只有 USD 291.00」，申请 291 成功 |
| SEC-004 平台重置密码后旧会话失效 | 接口：平台重置租户管理员密码后用旧 token 访问 | 旧会话返回 401 |
| SEC-020 售后单越权登记 | 接口：业务员对他人订单登记售后 | 业务员返回「订单不存在」；管理员正常 |
| BUS-044 有未完结售后单时取消订单 | 接口：取消带进行中售后单的订单 | 被拒「订单还有没处理完的售后单 AS20261115001，请先处理后再取消」 |
| UI-055 客户详情返回后刷新 | BrowserOS 页面操作：从客户详情跳出再返回 | 无可见骨架屏闪烁；详情、背调、预检、跟进均重新拉取 |
| UI-063 成本卡汇总列对齐 | BrowserOS 页面查看 | 汇总行 15 列，与明细对齐 |
| UI-004 浏览器后退时未保存提示 | BrowserOS 页面操作：编辑后按浏览器后退 | 弹出确认；选「留下」草稿保留（原生 history.back 只触发一次提示） |

其余修复以各自的缺陷见证测试与 CI 为准，未逐条在页面上操作。

### 修复中发现、新登记的问题

- **BUS-079**（P3）：`ReturnReceiptService.receive` 加锁顺序为「退货入库单 → 销售退货单」，`SalesReturnService.voidReturn` 相反，两者并发时可能死锁，MySQL 会回滚其中一个事务、不写错数据。
- **BUS-080**（P2）：经营看板「应收未收」（`BossWidgets.cashWidget`）仍按 PI 合计减已到账计算，与 BUS-049 修复后的收款口径不一致。
- **UI-094**（P3）：需求对象合并弹窗（`supply-chain/demand/index.tsx`）文案没有说明覆盖渠道和专项型号也会并到保留方（BUS-074 已改后端）。

### 需要用户决定或业务确认的事项

- BUS-057：后端是否在启用事业部时也拒绝不带事业部的手工调整（会影响从无事业部旧账户发起的反向调整）。
- BUS-052：售后单「处理」现在只提交改动字段，两人改同一字段仍是后提交覆盖；是否加版本号做冲突提示（需迁移）。
- BUS-048：客户合并时两边同一年都有年度计划，被合并方那一年的计划留在已删除客户下、不自动合并金额，是否合适。
- BUS-060：标为「不是重复」后按成员判断，成员不变时即使新增成组原因也不再出现，是否符合预期。
- UI-093：待归类信号列表按出现时间倒序（信号的业务含义），是否统一改为按更新时间倒序。
- UI-079：按 BUS-031 已定规则改了「去报价」提示文案；如希望保留「回价后可出新版本」，属于规则变更。
- BUS-058：原发现的复现数据（1.50 × 7.123）不能复现，应以 1.20 × 7.0125 这类乘积落在 .xx5 的组合为准。

### 生产历史数据（未授权、未执行）

以下修复只阻止新问题发生；生产上可能已有的历史数据需另行授权只读排查后再决定是否修正，排查 SQL 记录在修复结果中：BUS-043（已撤销售后单的有效补发订单）、SEC-020（越权登记的售后单）、BUS-044（挂在已取消订单上的售后单据）、BUS-048（已合并客户的跟进、背调、计划）、BUS-053（一个客户两条进行中商机）、BUS-054（补发订单产生的需求信号）、BUS-068（被改成未付款的补发订单）、BUS-069（0 元费用订单）、BUS-072（冲销后多余的暂存闲置库存）、BUS-073（不实的「回价已撤回」记录）、BUS-074（被合并需求对象上的渠道）、BUS-050（生成采购退货后改过币种的采购订单）、BUS-057（无事业部的手工调整账户）。BUS-042 已授权核查，生产无受影响账户。

## 合并、部署与逐 ID 验收

已在集成分支 fix/audit-1115 修复并通过本机验收，待合并到 develop。BUS-046 暂缓。各 ID 未发布，发布后按本机验收关闭。

## 阻塞与下一步

- 用户 2026-11-15 要求全部修复并本机验收后合并（BUS-046 暂缓，见上）；BUS-042 生产核查无受影响账户。
- 发布由用户决定，发布后关闭；上面的待决事项与生产历史数据排查待用户决定。
- 本轮没有产生需要清理的数据；BUS-042 的临时断言已还原，未提交。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-15 / Claude | 按用户要求对 `09da3dc3..c3af0644` 增量代码做全面审核（方法见上），新增 79 个 ID、重开 8 个原 ID；BUS-042 本机复现 | 等用户决定修复范围 |
| 2026-11-15 / Claude | 用户授权生产只读核查：BUS-042 无受影响账户；BUS-046 生产仅 1 条补齐商机且为正确赢单。用户确认 BUS-046 暂缓（理由与恢复条件见「生产只读核查」） | 其余问题按用户要求全部修复 |
| 2026-11-15 / Claude | 用户要求全部修复、本机验收后合并：10 批多代理修复（每条先缺陷见证），95 条修复、BUS-046 暂缓；集成分支后端全量、前端检查与测试、编排服务测试，以及本机接口与页面验收（见上）；修复中新发现 BUS-079、BUS-080、UI-094 并登记 | 已在集成分支 fix/audit-1115 修复并通过本机验收，待合并到 develop |
