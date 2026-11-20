# 验收表：doc-product-link

- 环境：本机 docker（后端、前端为本变更构建；本机库迁移 V1.5.53、V1.5.54 已执行，启动后历史回填跑完），租户 1005 管理员（walkthrough），ego-browser，2026-11-20
- 自动化：
  - 后端：`DocProductLinkContractTest`（7）、`DocumentProductTablesContractTest`（2）、`DocProductBackfillTenantContractTest`（1），以及补了「一路带下去」断言的 `OutboundShippingContractTest`、`ReceivingContractTest`、`AfterSalesContractTest`；`ProductLinkContractTest`、`PurchaseContractTest`、`PayableContractTest` 按新规则调整后通过。全量结果见 PR 说明。
  - 前端：lint、tsc、antd lint 通过；Jest 新增 `reviewDrawer`、`linkModal`、`docModelCell` 共 6 个用例。
  - 迁移：测试库执行回退脚本（V1.5.54 → V1.5.53）后删除两条 Flyway 记录，再启动测试时重新迁移成功，测试通过。

| # | 验收点 | 方式 | 证据 | 结论 |
| --- | --- | --- | --- | --- |
| A1 | 型号进池即生成候选商品，报价、PI、订单、采购一路带同一个商品 | 契约 | `candidateProduct_carriedFromQuoteToPurchase_andApproveKeepsId` | 通过 |
| A2 | 通过建档商品 ID 不变，单据不用改；候选标记随之消失 | 契约 | 同上：建档后订单行 product_id 不变，详情 `productCandidate` 为 false | 通过 |
| A3 | 改型号后与已建档商品重复：先提示并入，确认后单据改指向、写合并记录，按原 ID 查到主商品 | 契约 + Jest + 页面 | `approveWithEditedModel_collidesWithArchived_mergesAfterConfirm`；`reviewDrawer.test.ts`；页面上「并入已有商品」弹窗列出「销售订单 1」并可填原因 | 通过 |
| A4 | 已被订单引用的候选不能驳回；没引用的驳回后报价与 PI 行清空 | 契约 + 页面 | `rejectedRefused` 断言、`actualModel_syncsQuoteAndPi_rejectClearsThem`；页面审核抽屉「驳回」置灰，列表显示「已成交，不能驳回」 | 通过 |
| A5 | 回填真实型号后，未转订单的报价、PI 行同步 | 契约 | `actualModel_syncsQuoteAndPi_rejectClearsThem` | 通过 |
| A6 | 不像型号的不能转销售订单 | 契约 | `notAModel_cannotConvertToOrder`：提示「…先回填真实型号」 | 通过 |
| A7 | 采购、入库、出库、售后、退货一路带上商品 | 契约 | 三个流程测试结尾 `assertLinesLinked`：订单、需求、采购订单、出库、发货、入库、销售退货、退货入库、采购退货明细都有商品 | 通过 |
| A8 | 商品列表、发品任务只算已建档；选择器列出候选并标「候选」，已建档排前 | 契约 | `productLibraryShowsArchivedOnly_selectorMarksCandidates` | 通过 |
| A9 | 别家公司的候选不出现在选择器；精确匹配能命中但只给 ID 与型号 | 契约 | `candidateOfOtherTenant_hiddenInSelector_exactMatchReturnsIdOnly` | 通过 |
| A10 | 历史回填：沿来源链补、按型号匹配（没写品牌时型号唯一才匹配）、写法不像型号的留待关联 | 契约 + 本机 | `historyBackfill_matchesByModel_andUnlinkedListLinks`；本机回填结果见下 | 通过 |
| A11 | 回填在后台线程按各公司数据执行 | 契约 | `DocProductBackfillTenantContractTest`：没有租户上下文的线程里回填租户 99000095 的采购明细 | 通过 |
| A12 | 「待关联单据」页签按型号归并，可选择商品或填写真实型号批量关联 | 契约 + Jest + 页面 | 同 A10；`linkModal.test.ts`；页面列出品牌 · 型号、涉及单据（报价 2、PI 2、销售订单 1…）、明细条数、最近出现 | 通过 |
| A13 | 单据详情型号旁「候选」标记，有审核权限的点开进入该候选 | Jest + 页面 | `docModelCell.test.ts`；报价单 FWQT20261103001 两行标「候选」，点开进入商品候选并按商品编号 135 定位，返回条回到报价单；销售订单 SO20261008001 行标「候选」 | 通过 |
| A14 | 候选列表显示商品编号与被单据引用 | 页面 | 待审核列表型号下显示「商品编号 138」等 | 通过 |

本机回填结果（约 16 分钟，后台线程，不影响启动）：

- 补上：报价 150、PI 50、销售订单 3189、采购需求 34、采购订单 3183、供应商发货 5339、采购入库 3835、到货差异 5、出库 21、售后 6、销售退货 3、退货入库 3、采购退货 4
- 仍待关联：询盘型号 1021（标为「需回填真实型号」的，按原流程处理）、报价 18、PI 10、销售订单 125、采购需求 9、采购订单 101、供应商发货 124、采购入库 94、到货差异 2、出库 1

走查中发现并已修正：

- 审核抽屉标题在品牌为空时出现「审核候选 · · 型号」，已改为跳过空项。
- 没写品牌的历史行会进池成新候选，即使商品库已有同型号的已建档商品；已改为型号在商品库唯一时直接关联（本机这次回填在修正前跑完，已生成的这类候选可在审核时并入）。

结论：本机验收通过；2026-11-20 随 v1.41.0 上线，按用户要求归档，生产验收（含后台回填结果与「待关联单据」剩余数量）在归档后进行。
