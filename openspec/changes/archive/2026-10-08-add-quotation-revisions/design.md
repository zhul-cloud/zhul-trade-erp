## Context

- 报价单现在是「一张表头（`quotation`）+ 型号行（`quotation_item`）+ 费用行（`quotation_fee`）+ 发送记录」，没有版本概念；已发送后内容锁定。
- 报价单型号行被模块外读取的地方只有四处：询盘状态同步（`InquiryStatusSync`）、成交计算（`QuotationDeals`）、开 PI 的候选与带入（`PiServiceImpl`）、销售订单回写（`SalesOrderServiceImpl`），没有 SQL 文件直接查 `quotation_item`。
- PI 已经有一套版本做法（`proforma_invoice_version` + 型号行带版本、当前有效版本号 / 编辑中版本号、版本条与对比），界面与交互要和它一致。
- 币种白名单分散在 `CustomerConstants.CURRENCIES`、`ExchangeRateService.CURRENCIES`、`AbstractCustomerRequest` 的正则、`QuotationRenderModels.SYMBOLS`、前端 `quotation/components.tsx` 的 `CURRENCIES` 等处；汇率全部手动录入。

## Goals / Non-Goals

**Goals:**
- 报价单出新版本、放弃、切换、对比，交互与 PI 一致；
- 模块外的读取方（询盘、PI、订单）只需加一个「当前版本」条件，不改业务逻辑；
- 已有草稿的询盘可直接打开草稿；支持 RUB。

**Non-Goals:**
- 不做已开 PI 后的报价单改版（在 PI 上改，后续由「议价测算」配合）；
- 不改报价单编号规则与导出模版；不预置 RUB 汇率数值。

## Decisions

1. **型号行、费用行按版本存，打「当前版本」标记。** `quotation_item`、`quotation_fee` 增加 `version_no` 与 `is_current`（1-当前版本：已发送报价单为当前有效版本，从没发送过的草稿为 Rev.1）。出新版本时复制当前版本的行（`version_no = n+1`、`is_current = 0`），发送时在同一事务中把旧版本行置 0、新版本行置 1；放弃时软删除新版本行。模块外四处读取只加 `is_current = 1`。
   - 备选：只存一份「工作区」，发送时把整单写成 JSON 快照。查询改动更少，但历史版本无法用同一套页面与渲染逻辑查看和导出，对比也要解析 JSON，放弃。
2. **表头按版本存在 `quotation_version`，`quotation` 保留当前版本的表头副本。** 版本表保存贸易术语、地点、有效期、备注、汇率与更新时间、各项合计与毛利、版本状态（1-编辑中、2-已发送、3-已放弃）、发送时间；`quotation` 增加 `current_version_no`、`editing_version_no`，表头字段继续表示当前版本（列表、询盘、PI 读取方式不变），发送新版本时把版本表头写回 `quotation`。现有数据迁移为 Rev.1（草稿为编辑中，其余为已发送），发送记录增加 `version_no`。
3. **对比按询盘型号明细对齐，在前端计算（与 PI 一致）。** 详情接口可按版本号读取（`GET /quotations/{id}?version=n`），前端取上一个已发送版本的详情，两个版本的型号行按 `inquiryItemId` 配对：只在新版本中的为新增，只在旧版本中的为删除，两边都有的比较单价、数量、小计、货况、货期、质保；费用按名称配对；表头比较贸易术语、地点、有效期、备注与合计。对比展示与 PI 的对比抽屉一致。
4. **出新版本的限制在后端校验。** 只有状态为「已发送」、没有编辑中版本、且没有未作废 PI 引用其当前版本行时才允许；已开 PI 时错误信息带 PI 编号。并发用现有的报价单锁（`QuotationLocks`）。
5. **草稿跳转。** 「按询盘报价」候选接口已返回是否有草稿，改为同时返回草稿报价单 ID 与编号；前端点卡片时有草稿则跳转详情页，不进入勾选。
6. **RUB。** 币种白名单统一加 RUB；金额显示格式不变（`RUB 12,000.00`），模版货币符号映射加 `₽`；汇率页按白名单列出币种，RUB 未录入时沿用「还没有设置 RUB 汇率」的现有提示。

**事务边界：** 出新版本（复制表头与行）、发送新版本（翻转当前标记 + 回写表头 + 发送记录 + 询盘状态同步）、放弃（软删除新版本行 + 版本状态）各自在一个 `@Transactional` 中完成。

**精度：** 新版本复制原版本的单价与汇率，金额沿用现有口径（单价、小计、本位币两位小数 HALF_UP，汇率 6 位小数）；按新汇率重算时规则与草稿相同。

## Risks / Trade-offs

- [遗漏 `is_current` 条件导致询盘或 PI 读到编辑中版本] → 四处读取加条件后补契约测试：编辑中删除一行，开 PI 候选与询盘状态不受影响。
- [迁移存量数据] → 迁移只加列与回填（`version_no = 1`、`is_current = 1`、生成 Rev.1 版本行），可重复执行；测试库重置后跑全量测试。
- [报价单与 PI 版本条代码重复] → 版本条与对比展示抽成共用组件，PI 页面改用同一组件。
