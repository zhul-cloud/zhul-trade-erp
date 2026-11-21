# 库存一期（库存底座）— 验收记录

## 本机（2026-11-21）

- 迁移：本机 Flyway 执行 V1.5.55 成功（新建 `inv_warehouse`、`inv_batch`、`inv_move`、`inv_balance`、`inv_reservation`、`inv_other_outbound`、`inv_other_outbound_item`；`purchase_return`、`purchase_return_item` 加库存相关列；新菜单与按钮权限）。回退语句在迁移文件末尾（手动执行）。
- 期初：启动时按租户各跑一次。租户 1005：期初 3 行、6 件、无负数行；租户 1006：无库存。写入 `inventory.opening.done` 后重启不重复执行。
- 走查（walkthrough 账号，租户 1005 管理员）：
  - 库存查询：顶部汇总「库存金额 CNY 51.26 · 3 个商品 · 1 个仓库」、不良 5、退货待寄出 1；展开 6GK7443 看到批次 GR20261013001-1 预占给 SO20261013001。
  - 其他出库：从 CTDTV.5.2SUN 的不良批次发起报废 1 件，弹窗只有报废 / 转样品、数量上限为批次可出数量；生成 OO20261121001，列表、详情（明细、库存流水、操作日志）正常。
  - 作废 OO20261121001：填原因后状态「已作废」，流水多一笔反向的「作废其他出库」，库存回到原数。
  - 库存流水：业务类型显示「其他出库 · 报废」「作废其他出库」「期初」，来源单号可点。
  - 库存台账：11 月合计入库 7、出库 1，数量链到对应流水，可导出 Excel。
  - 仓库档案：公司仓库 6 件、货代仓 0 件；库存模式默认「无货源」，切换需二次确认。
- 走查中修正：流水业务类型改为直接显示业务说明（不再重复「其他出库 · 其他出库 · 报废」）；其他出库弹窗的批次数量提示改为「可出 N」（不良批次不能叫可用）。
- 自动化：
  - 后端 `InventoryFlowContractTest`（15）、`InventoryPostingTest`（7）、`PurchaseReturn*Test`、`ForwarderContractTest`、`NavigationContractTest`、`OutboundShippingContractTest`、`TenantIsolationContractTest`、`BackgroundEntryTenantContractTest` 通过。
  - 前端 `warehouse/inventory.test.tsx`（库存查询展开、台账链接、模式二次确认、出库数量上限、库存不足弹窗）及仓库、物流、采购退货相关测试通过。

## 生产

2026-11-21 随 v1.42.0 上线（迁移 V1.5.55 随新版本后端启动执行），「生产发版」运行成功；用户要求上线后直接归档。库存期初的生产结果（日志「库存期初完成，tenantId=…」）与页面验收待补。
