# SEC-005 等：整改后风险聚焦复审（2026-11-14）

## 范围与责任边界

| 字段 | 内容 |
| --- | --- |
| 审查人 / 修复负责人 | Codex（分工检查、主审复核、文档集成）；修复负责人尚未认领，见[唯一状态台账](../issues.md) |
| 关联 ID | 重开 SEC-005、UI-004、AI-005；新增 BUS-038、SEC-013、SEC-014、UI-052、UI-053、UI-054、AI-009、AI-010 |
| 代码基准 / 文档分支 | `develop` / `58686c96`；文档分支 `docs/post-remediation-review-20261114`，创建前工作区干净 |
| 重点差异 | `48adc1b9..58686c96`：1020 个路径，其中 533 个源码、运行或配置路径；按资金、对象权限、标签生命周期、上传与 AI 任务风险聚焦复审 |
| 实施 / PR / 发布 | 复审与本机见证阶段只记录发现，不修产品；该阶段尚未提交、推送或创建 PR。用户后续授权将本报告及关联台账经 PR 合入 `develop`；文档交付不代表产品修复、发布或验收，后续修复仍按现有 OpenSpec、Git Flow 与整改门槛推进 |

复审前台账为 134 项：119 已关闭、14 暂缓、DOC-002 一项已发布待验收。GitHub 已核实 [#306](https://github.com/zhul-cloud/zhul-trade-erp/pull/306)、[#308](https://github.com/zhul-cloud/zhul-trade-erp/pull/308) 已合并；Wiki 摘要仅到 11 月 11 日，当前整改事实以仓库台账、任务和最新代码为准。本轮登记 8 个新 ID、沿原 ID 重开 3 项，不抹除历史关闭与验收证据，不重开 14 个暂缓项，不改变 DOC-002 状态。登记后共 142 项：116 已关闭、11 待认领、14 暂缓、1 已发布待验收。

这不是全部文件、页面、角色与状态的重新验收，也不是生产验收；不沿用 2026-11-01 的 3315 路径覆盖结论。用户后续明确授权自造账号与本机测试；已用 BrowserOS neo 正常登录独立测试租户，补充 UI-004、UI-052、UI-053 的真实页面缺陷见证，并用独立非管理员角色及隔离业务数据补充 BUS-038、SEC-005、SEC-013 的真实 HTTP / 数据库证据。UI-054 仍只有真实组件与内存 API 桩的受控迟到响应证据。以下分别注明静态、真实页面、HTTP / 数据库、组件及函数探针；本轮没有产品修复，不能将缺陷见证称为修复验证。

## 发现、已有保护与验收要求

### BUS-038 · P1 · 退款申请执行遗漏请求校验，可登记负退款金额

- **定位：**[RefundRequestController.java:97](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/controller/RefundRequestController.java#L97) 的执行入口缺少 `@Valid`；[RefundRequestService.java:244](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/RefundRequestService.java#L244) 只检查空值和申请金额上限；[CustomerRefundService.java:122](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/CustomerRefundService.java#L122) 至 139 行没有正数检查即生成退款记录。
- **触发 / 影响：**有财务执行权限、申请处于待财务处理状态，提交负数 `amount`，或银行退款的负数 `amountCny`，同时满足日期、去向、凭证等其他规则。负值低于金额上限，因此不能被现有服务校验拒绝。退款及申请状态在同一事务内写入；负原币金额会进入[已退金额求和与可退额度](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/CustomerRefundService.java#L428)，导致退款金额与后续额度失真。金额当前按两位、`HALF_UP` 处理，舍入不能代替正数验证。
- **已有保护 / 证据：**[SaveRefundRequest.java:22](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/dto/SaveRefundRequest.java#L22) 至 27 行已有 `@DecimalMin("0.01")`，Validator 探针确实拒绝负数；问题在该入口未触发验证且服务无兜底。第一阶段止于源码、Validator 和当前源编译，Mockito 初始化受阻；追加本机运行镜像真实 HTTP / 数据库见证已确认负退款落库，见下表。全部申请为隔离租户 `1002`、金额 `10.00 CNY`、银行退款方法 `2`，原币及本位币均按两位 `HALF_UP`；凭证为自造 LOCAL 附件元数据，没有实体文件上传或内容验证。主审独立只读 SQL 复核申请状态、关联退款和实际金额；没有余额转入，`credit_entry` 为 `0`。

  | 执行入参 `amount / amountCny` | 申请 / 退款 | 实际结果与事务边界 |
  | --- | --- | --- |
  | `1.00 / 1.00` | `3 / 10000` | HTTP `200`、`code=0`，正常正值对照 |
  | `-1.00 / 1.00` | `4 / 10001` | `200 / 0`，数据库原币 `-1.00`、人民币 `1.00`、汇率 `-1`，申请 `status=3` |
  | `1.00 / -1.00` | `5 / 10002` | `200 / 0`，数据库原币 `1.00`、人民币 `-1.00`、汇率 `-1`，申请 `status=3` |
  | `1.00 / 0.00` | `7 / 10003` | `200 / 0`，人民币 `0.00`、汇率 `0`，申请 `status=3` |
  | `1.00 / 省略` | `10 / 10004` | `200 / 0`，正常自动换算人民币 `1.00` |
  | `0.00 / 1.00`、`0.004 / 1.00` | `6、11 / 无` | HTTP `500` 系统错误；后者舍入为零。事务回滚：申请仍 `status=2`、无退款关联，退款数量和合计不变，凭证仍未挂接 |
  | `null / 1.00`、`11.00 / 11.00` | `8、9 / 无` | HTTP `200`、`code=500` 业务拒绝；同样无部分落库 |

  相同负原币、负人民币、零人民币和零原币请求直接调用 `POST /api/v1/finance/refunds` 均 HTTP `400`，说明 DTO 校验本身有效。无财务权限的 SELF 业务员执行入口 HTTP `403`，申请不变；正常菜单权限、申请状态、行锁和额度上限仍存在。零原币导致系统错误是同一入口校验缺口的补充边界，未另建重复 ID。
- **建议 / 验收：**入口启用验证，并在资金服务的共同边界拒绝负数、零以及舍入后不足最小金额的值；保留允许省略 `amountCny` 后自动换算的正常路径。隔离库验证原币和人民币负数、零、空值及上限对照，确认拒绝时申请、退款和余额均无部分落库；复核有效负记录是否存在另需授权。

### SEC-005 · P1 · 新附件所属类型落入默认放行，重开原 ID

- **定位：**[AttachmentOwnerScope.java:68](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/attachment/service/AttachmentOwnerScope.java#L68) 至 90 行按所属类型分派，新增 `SALES_RETURN`、`REFUND_REQUEST` 没有对应分支，落入 `default -> true`；[AttachmentController.java:58](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/attachment/controller/AttachmentController.java#L58) 的菜单条件与[签发链接调用](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/attachment/controller/AttachmentController.java#L82) 没有补上这些类型的单据负责人检查。
- **触发 / 影响：**同租户用户拥有附件接口允许的某个业务菜单，但无目标销售退货或退款申请的数据范围；已知目标附件 ID 时，所属单据范围不能阻止其取得附件链接。此处是附件链接未继承新类型单据范围，沿用 SEC-005；与下项退款申请详情及修改入口分开。
- **已有保护 / 证据：**追加本机真实 HTTP 见证：租户 `1002` 两名非管理员 SELF 业务员，目标订单 `10001` 归业务员一；业务员二读取该订单及销售退货 `2` 均被数据范围拒绝，但对自造附件 `10001`（`REFUND_REQUEST / 1`）、`10002`（`SALES_RETURN / 2`）签发链接均 HTTP `200`、`code=0`、返回非空签名 URL；旧 `SALES_ORDER` 附件 `10000` 同人请求 HTTP `403`。本人和全量财务角色三种类型均正常。跨租户 `1003` 三种均以“附件不存在”拒绝；未挂附件 `10003` 只有上传人可签链接，其他业务员与财务均 `403`。登录、菜单、租户与未挂附件上传人保护仍有效，未证明跨租户访问。只使用本机 LOCAL 附件元数据，无实体文件，证明范围是链接签发，未下载真实附件。旧修复和原验收保留在[原任务](2026-11-03-SEC-005-object-guards.md)。
- **建议 / 验收：**补齐现行所属类型与其单据可见性规则，未知类型采用显式拒绝或明确分类，避免扩展类型自动放行。用同租户不同负责人对照销售退货、退款申请及既有附件类型；验证受限用户拒绝、授权角色正常、未挂接附件仍仅本人可见。

### SEC-013 · P1 · 退款申请按 ID 读取、备注与附件维护遗漏负责人数据范围

- **定位：**[RefundRequestService.java:520](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/RefundRequestService.java#L520) 的详情只走 `require()`；[require / lock:690](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/RefundRequestService.java#L690) 至 702 行只核对存在、删除及租户。[备注:443](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/RefundRequestService.java#L443)、[附件维护:452](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/RefundRequestService.java#L452) 至 463 行复用此边界；[控制器:107](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/controller/RefundRequestController.java#L107) 至 122 行允许申请侧菜单用户进入。
- **触发 / 影响：**同租户不同业务员，调用方有销售 / 售后申请菜单、无目标订单负责人范围，按目标申请 ID 读取详情、修改备注或维护其附件。列表或按订单入口的过滤不能替代按 ID 入口的检查。
- **已有保护 / 证据：**追加本机真实 HTTP / 数据库见证：租户 `1002` 业务员二（非管理员、SELF）读取业务员一申请 `1` 返回 HTTP `200`、`code=0`，包含订单 `10001` 与申请金额 `50.00 CNY`；同人按订单入口 `/order/10001` 返回 `200 / code=500`“销售订单不存在”，形成受保护对照。该业务员修改申请 `1` 备注成功，SQL 实际为 `codex_backend_20261114_other`；挂接其本人未挂附件 `10003` 后数据库 `owner_id: 0→1`，再移除成功且 `deleted_at` 非空。本人挂接 / 移除附件 `10004` 正常，全量财务读取详情及按订单查询正常；跨租户详情、按订单、备注和移除均拒绝，SQL 状态不变。主审独立 SQL 复核备注及附件终态。租户、菜单及事务保护仍有效，无财务权限的 SELF 执行退款为 `403`，申请不变；不声称跨租户或未经授权执行财务退款。
- **建议 / 验收：**为读取及申请侧备注、附件维护复用统一的单据可见性入口，同时保留财务按现行规则处理全部申请的角色能力。以同租户两名业务员及财务角色覆盖详情、备注、挂接、移除，检查拒绝时不变更记录或附件归属；再做跨租户拒绝回归。

### SEC-014 · P2 · 本地直传校验后仍可重写，认领信任旧校验结果

- **定位：**[DirectUploadService.java:147](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/upload/service/DirectUploadService.java#L147) 至 181 行的 `receivePart()` 未检查完成状态，仍写同一临时路径；[complete:209](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/upload/service/DirectUploadService.java#L209) 校验后保留会话和 token。[pending:271](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/upload/service/DirectUploadService.java#L271) 只信 `verified`，[claim:253](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/upload/service/DirectUploadService.java#L253) 至 260 行直接移动文件；询盘[认领入口:316](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/inquiry/customerinquiry/service/impl/CustomerInquiryServiceImpl.java#L316) 至 338 行未重做内容校验。
- **触发 / 影响：**本地存储下，原上传者完成合法文件的校验后、业务认领前，继续使用原地址写入同长度的其他内容，再保存询盘。正式附件可能与已通过的魔数、图片压缩和内容校验结果不一致。
- **已有保护 / 证据：**登录、同租户、本人、分片 token 和长度检查均存在，不阻止原上传者重写；不声称能修改别人文件、跨租户或执行代码。OSS 完成后的 multipart ID 已消耗，不把本地路径结论套到 OSS。仅按生产源码静态确认；原动态重写探针在派发前中止，没有运行。追加本机配置只读核对为 `STORAGE_TYPE=oss`，为避免云写入没有改配置或执行 LOCAL 上传场景；这表示本机动态验证未覆盖，不表示 LOCAL 问题不适用或已修复。
- **建议 / 验收：**对上传、校验与认领实施原子状态转换，完成前封闭分片写入并处理在途写入；仅加一次 `verified` 判断仍需考虑并发交错。隔离本地存储验证完成后重传被拒、校验与分片交错不会改变已认领内容，以及正常乱序、重试和认领不回归。

### UI-004 · P2 · 标签右键刷新绕过未保存确认，重开原 ID

- **定位：**[PageTabs/index.tsx:171](../../../zhul-erp-frontend/src/components/PageTabs/index.tsx#L171) 直接调用刷新；[model.ts:258](../../../zhul-erp-frontend/src/components/PageTabs/model.ts#L258) 更换页面 key，[index.tsx:403](../../../zhul-erp-frontend/src/components/PageTabs/index.tsx#L403) 按 key 渲染，强制卸载并重建页面。
- **触发 / 影响：**已登记 dirty 的页面含未保存输入，使用标签右键刷新；页面重建，草稿被服务器值替换，没有确认机会。属于原未保存保护主题遗漏入口，沿用 UI-004。
- **已有保护 / 证据：**普通关标签及脏页面保活规则存在，但刷新不走确认。真实 PageTabs、Pane、hooks 与 React / JSDOM 内存见证：`e2, dirty=true, value=UNSAVED` → `e3, dirty=false, value=SERVER`，`confirmationCalls=0`。追加 BrowserOS neo 真实页面：租户 `1001` 的草稿报价 `CQAUD20261114`（`id=10000`）备注从 `SERVER_AUDIT_REMARK` 改为 `UNSAVED_AUDIT_UI004`，标签与标题出现未保存标记；普通关闭弹出“关闭这个标签？”，取消后草稿保留。随后标签右键“刷新”没有确认，备注恢复服务器值、dirty 标记消失、按钮恢复“已保存”。未提交草稿写库。旧验收留在[原任务](2026-11-12-UI-004-batch-k.md)。
- **建议 / 验收：**刷新与关标签复用统一的未保存确认；验证取消后 key 和草稿保持，确认舍弃才重建，干净页面正常刷新。覆盖报价、PI 和通用 dirty 页面，不只验证一个表单。

### UI-052 · P2 · 公共单据附件卡备注草稿未登记未保存状态

- **定位：**[DocFiles/index.tsx:452](../../../zhul-erp-frontend/src/components/DocFiles/index.tsx#L452)、[编辑区:552](../../../zhul-erp-frontend/src/components/DocFiles/index.tsx#L552)、[编辑操作:563](../../../zhul-erp-frontend/src/components/DocFiles/index.tsx#L563) 将备注编辑保存在组件状态，没有 `useTabDirty` 或浏览器离开保护；[销售退货详情](../../../zhul-erp-frontend/src/pages/sales/returns/detail.tsx#L1) 父页面仅登记标题，没有为这份草稿补登记。
- **触发 / 影响：**在公共卡片编辑备注尚未保存，关闭标签、刷新或离开至导致该页面卸载，草稿丢失且无确认；仅切换到仍保活的标签不一定立即丢失，不扩大为任何导航都丢失。
- **已有保护 / 证据：**保存失败时的编辑保留与框架通用 dirty 机制不能代替草稿登记。除真实组件及父页面静态复核，追加 BrowserOS neo 真实页面：租户 `1001` 的销售退货 `CTHAUD20261114`（`id=1`）公共附件卡备注改为 `UNSAVED_AUDIT_UI052` 后，标签没有 dirty 标记；点击关闭直接卸载，没有确认。重新由列表打开并编辑，备注仍为 `SERVER_AUDIT_REMARK`，未保存草稿已丢失，未写备注到数据库。此项是未登记草稿，与 UI-004 已登记却绕过刷新保护的根因不同。
- **建议 / 验收：**公共卡片将相对已保存值的备注变化登记到所属标签，并在保存成功、放弃和卸载时正确清理。覆盖取消离开、确认舍弃、保存失败后重试、成功后不再提示及标签保活返回。

### UI-053 · P2 · 销售退货详情改状态后返回保活列表，列表状态和计数不刷新

- **定位：**[销售退货列表 index.tsx:75](../../../zhul-erp-frontend/src/pages/sales/returns/index.tsx#L75) 按挂载或筛选变化加载；[detail.tsx:75](../../../zhul-erp-frontend/src/pages/sales/returns/detail.tsx#L75) 的成功处理只重载详情；[PageTabs/index.tsx:67](../../../zhul-erp-frontend/src/components/PageTabs/index.tsx#L67) 将非活动页面隐藏保活，返回不会重新挂载。
- **触发 / 影响：**从列表打开销售退货详情，确认或作废后返回原列表，列表仍显示旧状态和计数，直到手动刷新或其他加载条件发生。范围仅确认销售退货，不声称所有保活列表均有相同缺口。
- **已有保护 / 证据：**详情自身重载，后端操作状态校验仍有效；这里确认的是返回后的展示过期，不是后端允许重复操作。追加 BrowserOS neo 真实页面：从待确认计数 `1` 的列表打开 `CTHAUD20261114`，通过详情“作废”提交本任务原因，详情重载显示“已作废”；点击框架返回条后，保活列表仍显示该行“待确认”、待确认计数 `1`。只读数据库核对同一测试行 `status=4`。整页手动刷新后待确认计数变 `0`、该行消失；切换“已作废”显示同单据正确终态。此见证只覆盖作废路径，未实际执行确认退货或失败操作。
- **建议 / 验收：**在详情写入成功后失效关联列表，或在返回激活时按需重取，同时保留原筛选、分页和滚动位置。隔离环境覆盖确认、作废、失败操作以及普通详情返回，核对列表行与顶部计数一致。

### UI-054 · P2 · 退款弹窗旧订单响应迟到，覆盖新申请的币种与人民币换算

- **定位：**[RefundModal.tsx:84](../../../zhul-erp-frontend/src/pages/finance/refunds/RefundModal.tsx#L84) 请求回调没有会话 / 请求世代检查；[自动换算:105](../../../zhul-erp-frontend/src/pages/finance/refunds/RefundModal.tsx#L105)、[提交:130](../../../zhul-erp-frontend/src/pages/finance/refunds/RefundModal.tsx#L130) 至 139 行将被覆盖后的人民币金额用于当前申请。后端[CustomerRefundService.java:130](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/CustomerRefundService.java#L130) 接受非空的实际人民币金额，不会按新订单汇率自动纠正。
- **触发 / 影响：**申请 A 的查询挂起，取消后打开申请 B；B 先返回，A 后返回。A 的币种和汇率覆盖 B 的弹窗状态，最终仍向 B 的 ID 提交，可能登记错误人民币金额。币种 / 订单信息的可见变化可能被操作人发现，因此本轮定为 P2。
- **已有保护 / 证据：**金额上限、申请 ID 和状态校验不能识别另一订单仍为正数的换算结果。真实 RefundModal、受控 Promise 与 API 桩见证：B 为 GBP、汇率 9、金额 10、人民币 90；A 迟到后显示 EUR / SO1、汇率 7、人民币 70，最终观察到 `execute(id=2, amount=10, amountCny=70, method=2)`。没有真实业务库写入；分工证据由主审源码复核。
- **建议 / 验收：**按打开会话和订单身份校验异步响应，关闭或重开时作废旧请求，并同步清理旧数据；保留用户手填实际人民币的业务能力。覆盖 A 慢 B 快、取消后重开、失败迟到与关闭卸载，断言 B 的展示、换算和最终提交均属于 B。

### AI-005 · P1 · 附件下载只在开始时检查总期限，重开原 ID

- **定位：**[large_parse.py:77](../../../scripts/ai-orchestrator/large_parse.py#L77) 至 85 行仅在创建连接时调用 `deadline_left()`，读取循环不再检查；[server.py:1262](../../../scripts/ai-orchestrator/server.py#L1262) 传入任务剩余期限，但没有包围下载的总时长中断。
- **触发 / 影响：**附件持续缓慢传输，每次网络活动未达到 socket 超时，下载可越过整任务期限继续占用线程、连接和写盘。后端判失败不负责取消编排服务。旧整份读取也有同类缺口，沿用 AI-005 的整任务期限主题，不新增重复 ID。
- **已有保护 / 证据：**单次 socket timeout 和 500MB 文件上限仍存在，均不保证总时长。真实 `download()` 加假响应、BytesIO、假时钟的离线见证仅两个字节：`deadline=3s, elapsed=6s, deadline_checks=1`，函数仍成功返回；没有网络、AI、云或文件写入。旧验收留在[原任务](2026-11-04-AI-001-task-reliability-batch-f.md)。
- **建议 / 验收：**读取前后检查剩余期限，并使阻塞读取受当前剩余预算约束；预处理也需可中断的期限检查。用慢流、持续有数据、阻塞读取、取消清理及正常期限内完成作对照，确认不再在后端终态后继续处理。

### AI-009 · P2 · 读取和预处理阶段没有心跳，长任务会先被空闲扫描判失败

- **定位：**[server.py:1254](../../../scripts/ai-orchestrator/server.py#L1254) 只在读取开始报一次进度，1258 至 1274 行下载和预处理全部附件后，直到[1300 行](../../../scripts/ai-orchestrator/server.py#L1300) 才启动 `Progress` 心跳；[AiTaskTimeoutWatchdog.java:40](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/aitask/service/impl/AiTaskTimeoutWatchdog.java#L40) 默认空闲期限 20 分钟，[104 行](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/aitask/service/impl/AiTaskTimeoutWatchdog.java#L104) 至 106 行据最后进度时间判定。
- **触发 / 影响：**多个合法附件的下载与预处理合计超过 20 分钟，而仍在允许的 170 分钟整任务期限内；后端先将任务和询盘转为失败，编排服务继续处理，迟到成功按 AI-001 规则被丢弃。各个下载均可在期限内完成，与 AI-005 可独立发生。
- **已有保护 / 证据：**180 分钟最长时长不会替代 20 分钟空闲扫描；提取和核验阶段心跳已有覆盖。AST 编译真实 `parse_large()`，仅替换下载和预处理：13 个附件各推进 100 秒假时钟，得到 `elapsed=1300s, progress_reports=[(0, 'read')], idle_watchdog=1200s`。读取完成即停止，未调用模型，未写文件。
- **建议 / 验收：**心跳作用域覆盖读取、预处理、提取、合并和核验，阶段切换更新状态，任务退出统一停止。假时钟和后端隔离契约分别验证读取超过 20 分钟仍存活、停止心跳后确实超时，以及终态后忽略进度。

### AI-010 · P2 · 分批解析失败时，工作线程已消耗用量没有汇总到回调线程

- **定位：**[server.py:1293](../../../scripts/ai-orchestrator/server.py#L1293) 至 1297 行从工作线程取出用量放进局部 `usages`；[1331 行](../../../scripts/ai-orchestrator/server.py#L1331) 至 1332 行在无型号时抛错，整任务超时也会异常退出；用量只在[1384 行](../../../scripts/ai-orchestrator/server.py#L1384) 至 1385 行正常结束路径汇总回主线程。[send_callback:1123](../../../scripts/ai-orchestrator/server.py#L1123) 只能取到当前线程的用量。
- **触发 / 影响：**分批调用已消耗 token，但结果合法地没有型号，或后续任务失败；异常跳过汇总，失败回调不带这些用量，任务的消耗记录低报或缺失。这里未证明服务商少计费，影响的是 ERP 的任务用量记录和费用核对。
- **已有保护 / 证据：**正常成功路径会汇总，回调重投保留已生成的同一请求体；都不能恢复异常前尚在局部列表的用量。真实 AST 函数、真实线程池及线程变量，模型调用替换为记录 120 输入 / 30 输出 token 并返回合法 `groups=[]` 的桩：实际抛出“没有从附件中提取到型号”，`fake paid calls=[(120, 30)]; parent usage for failed callback=None`。没有真实 AI、网络、文件或账务写入。主审另核对该异常链。
- **建议 / 验收：**在所有退出路径的 `finally` 中将已收集用量汇总回回调线程，明确并发异常、重试和部分完成的计数口径，避免重复累加。覆盖无型号、提取失败、核验后超时、部分成功和正常完成，检查失败回调仍携带已发生的用量且重投不重复记录。

## 本机实测环境与隔离

- 实测入口为 `localhost:8000` / `127.0.0.1:8080`，使用既有本机 Docker 服务，没有重建、重启或部署容器。后端镜像 `e8d2be3c2a76`（2026-11-14 07:41:57Z），前端 `657666c4f14a`（10:13:40Z）；后端运行日志显示 Flyway 已到 `1.5.33`。镜像未携带 Git 提交标签，后端容器 JAR SHA-256 为 `3c8743de9e875d2ef382610e7b13ce962436e15ae26aee86b5df50db26ee656c`，与本地 `target` JAR 不同，因此真实运行见证只归属于这些镜像，源码定位另按 `58686c96` 复核，不声称全部二进制等价。
- UI 使用自建隔离租户 `1001`（`codex_audit_20261114`）、专用租户管理员 `codex_audit_20261114_admin`，随机强密码仅暂存本任务内存，通过正常登录 API 和真实登录表单进入；不复用平台管理员或既有用户密码。无初始认证会话、无公开注册入口时，按核实后的 schema 在单事务内最小 bootstrap 租户、套餐和账号；页面 fixture 仅含自有客户、报价、订单、售后、退货各一条，不含完整型号明细或财务收付款。
- 后端使用独立租户 `1002 / 1003`、套餐 `3`，账号 `10010001–10010004` 均 `admin_flag=0`；业务员一、二为 `CBA_SELF / permission_scope=3`，财务为 `CBA_FIN / permission_scope=1`，第四账号用于跨租户拒绝对照。仅最小 bootstrap 身份、角色、自有客户、两张 `CNY 1000.00` 订单、到账及 LOCAL 附件元数据；退款申请均通过应用正常 API 生成，不查询无关真实业务数据。HTTP 状态与 JSON `code` 分别记录，`200 / code=500` 业务拒绝不混称 HTTP `500`。
- 真实 UI 覆盖为 UI-004 的普通关闭 / 取消保留 / 右键刷新对照，UI-052 的备注草稿关闭丢失，以及 UI-053 的作废后返回 / 刷新对照。销售退货列表在 `1366×768` 下 `documentWidth=1366`，表内横滚 `scrollWidth=2290 / clientWidth=1037`，筛选、查看与分页可达；主审独立截图及 snapshot 复核未发现新增视觉阻断。最少 fixture 不覆盖长型号、长金额或长标题的全部布局。UI-054 未人为干预真实网络，继续保留原组件证据。

## 验证限制、排除项与清理

- 本轮没有完整重跑后端、前端或编排服务全套测试，没有重新验证旧任务所记全量通过结果。第一阶段后端止于静态调用链、DTO Validator 和当前源编译，Mockito 初始化受阻；本轮追加运行镜像的真实 HTTP / 数据库用例，已见证负退款写入及权限缺口，并检查拒绝时事务回滚。该追加不等于全部回归或修复验证。
- 前端除原有真实组件和内存 API 桩外，增加上述三项真实页面缺陷见证及隔离退货作废写入；没有全页面、全角色与全状态回归，也没有生产验收。路由权限候选经真实 Umi `plugin-access`、`matchRoutes` 的 7 种权限组合反证：父级 `false` 不自动传给有自身 access 的子页，Layout 按叶路由判断，故不登记为缺陷。
- AI / 上传分工另检查编排入口、分批预处理、订阅、相关测试和设计、直传与清理、询盘认领、后端超时 / 回调，以及 dev-up、部署、Compose 和 CI。部署与 CI 静态检查没有形成额外高可信新问题；不据此声称运行环境全部通过。完成响应丢失后前端会重新准备并整份重传，排除了“同一 complete 重试后永久无法恢复”的错误描述。
- 未读取 `.env` 或既有用户密码；本机数据库操作使用容器自身环境认证且未输出凭据。数据库写入仅限上述新建隔离租户。未调用付费 AI、邮件或短信，未上传或下载云文件、未操作生产；未重建、停止或清理现有服务。AI 探针使用 `python -B`、内存响应与假时钟，不产生 pycache 或附件文件；本地直传动态重写未执行。
- UI 清理已核验：租户 `1001`、账号 `10009001`、用户 `1000009001` 全部停用；套餐 `2` 停用并软删除；客户 `1`、报价 `10000`、订单 `10000`、售后 `1`、退货 `1` 均标记 `deleted_at`，无硬删。通过页面确认退出及 HTTP 登出撤销专用会话，数据库该账号活动会话数为 `0`，任务页停在登录页；其余用户及订阅登录保持原状。未上传测试文件、未起临时服务；随机密码生成用的短期 Java 源文件已移除，内存测试凭据已清空。
- 后端清理已核验：先经真实 API 作废 `5` 笔退款、撤回 `11` 份申请，再仅对本任务归属记录软删除；租户 `1002` 的 `5` 退款、`11` 申请、`11` 申请明细、`1` 退货、`2` 到账、`2` 订单、`1` 客户、`14` 附件及 `43` 单据日志均 `deleted_at` 非空。租户 `1002 / 1003`、四个账号 / 用户和两个角色均停用，套餐 `3` 停用及软删除；逐个正常登出 `12` 个测试会话，活动 token 为 `0`，旧 token 返回 `200 / code=401`。保留停用身份、角色资源映射 `204` 条、单据序列 `2` 条及必要安全操作记录，无硬删；没有实体上传文件或云对象。五个本任务临时探针脚本及其空目录已删除，没有本任务 Python 进程。主审另行只读 SQL 复核三个租户、五个账号均停用、活动测试会话为 `0`、测试退款 / 申请 / 附件和销售订单无活动记录。
- 临时探针清理按实际归属核对：主审以正常权限只读检查用户 Temp 及 Codex AppContainer sandbox Temp，未找到 `codex-backend-reaudit*` 残留；这表示核对未发现，不是本轮手动删除了文件。保留用户数据、登录、凭据和现有运行服务。本轮只留下本报告及授权的台账 / 入口 / 原任务事件更新，不另写 Wiki、覆盖 CSV 或同用途记忆副本。

## 下一步与事件记录

用户已明确将后续修复委托 Claude；Claude 尚未认领或开始执行，以上 11 项保持待认领，本轮不实施修复。修复者先读对应条目、各类证据边界和最新代码，明确文件所有权；BUS-038、SEC-005、SEC-013 及三项 UI 已有本机真实缺陷见证，后续仍须验证修复后的拒绝 / 正常对照、回归及目标环境。SEC-014 的 LOCAL 动态路径与 UI-054 的真实网络时序尚未覆盖。原 14 个暂缓项仍按原恢复条件处理，DOC-002 仍待既定验收，不因本报告推进或关闭。

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-14 / Codex | 在 `58686c96` 完成风险聚焦复审，主审核对各组关键源码；登记 8 个新 ID，重开 SEC-005、UI-004、AI-005，保留旧验收和暂缓决定。当时文档在独立 `docs/*` 分支集成，尚未提交、推送或创建 PR，未修产品或发布 | 11 项待认领；按各项验收要求修复与验证，负责人尚未确认 |
| 2026-11-14 / Codex | 用户授权自造本机账号后，在独立租户完成三项 UI 真实页面、负退款和对象权限 HTTP / 数据库见证及正常 / 拒绝对照；主审独立复核页面截图与关键 SQL。三个测试租户和五个账号均停用，业务记录软删、活动测试会话归零，无云写入 / AI 调用，原报告追加证据边界 | 11 项发现保持待认领；无产品修复，不据缺陷见证关闭 ID；LOCAL 上传、真实迟到响应及全量回归仍未覆盖 |
