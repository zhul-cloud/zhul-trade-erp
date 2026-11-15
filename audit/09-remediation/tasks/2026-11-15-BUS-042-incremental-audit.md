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

本轮只审核与登记，未修复。BUS-042 的本机复现见上；其他问题未在运行环境中复现。

## 合并、部署与逐 ID 验收

全部新增问题与重开 ID 均为待认领，未修复、未发布。

## 阻塞与下一步

- 用户 2026-11-15 要求全部修复并本机验收后合并（BUS-046 暂缓，见上）；BUS-042 生产核查无受影响账户。
- 修复按原流程从 develop 拉 `fix/*`，每个 ID 先补缺陷见证再改。
- 本轮没有产生需要清理的数据；BUS-042 的临时断言已还原，未提交。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-15 / Claude | 按用户要求对 `09da3dc3..c3af0644` 增量代码做全面审核（方法见上），新增 79 个 ID、重开 8 个原 ID；BUS-042 本机复现 | 等用户决定修复范围 |
| 2026-11-15 / Claude | 用户授权生产只读核查：BUS-042 无受影响账户；BUS-046 生产仅 1 条补齐商机且为正确赢单。用户确认 BUS-046 暂缓（理由与恢复条件见「生产只读核查」） | 其余问题按用户要求全部修复 |
