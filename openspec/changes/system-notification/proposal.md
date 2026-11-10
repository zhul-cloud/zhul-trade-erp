## Why

询盘转采购、销售订单要采购、供应商发货、货到仓库这些交接点，现在只能靠对方自己刷页面或口头提醒，经常有人不知道轮到自己了，流程就卡住。系统里没有任何站内通知。生产上采购、财务的活分散在合伙人、总经理等账号上，按权限找人会通知错，需要「单据负责人 + 可配置的接收人」来定收件人。

## What Changes

- 新增站内通知：右上角铃铛显示未处理数，点开是通知列表，分「待处理」和「全部」；点一条进入对应单据并标为已读，可以全部已读。
- 通知分两种：
  - 待办型：要收件人去做事。事情做完后系统自动标为已处理，不靠手动已读。
  - 知会型：看过即已读。
- 收件人规则：
  - 单据有负责人的，发给负责人。
  - 没有负责人、等人来领的，发给业务设置里的「通知接收人」：采购负责人、收货人、财务三个岗位，各自选人。
  - 自己操作的事不通知自己。
  - 同一事件按人合并成一条。
- 第一期 16 个通知节点：
  - 询价：任务待分配、分配给我、询盘可报价、型号存疑退回。
  - 收款：水单待登记、已到账。
  - 销售订单到采购：有型号要采购（含售后补发订单）、有型号没指定采购员、采购员变更（新采购员收待办、原采购员收知会）、订单取消。
  - 收货：供应商已发货（收货人待办、业务员知会）、到了预计到货日还没收货、已收货、到货差异。
  - 出运前：订单货已到齐。
- 采购需求列表：订单生成超过 24 小时还没下单的需求标红。时限可在系统设置调整；销售订单没有紧急标记，不区分紧急。
- 第一期只有站内；第二期接飞书，并补超时、审核、应付到期、售后时限、退款审批等节点。
- 新增数据库表两张（通知、通知接收人），新增一个系统配置项（需求超时小时数，默认 24），不新增 `.env` 配置项。

## Capabilities

### New Capabilities

- `system/notification`：站内通知（铃铛与列表、待办与知会、收件人规则、合并、通知接收人设置、第一期通知节点）。

### Modified Capabilities

- `purchase/purchase-requirement`：采购需求列表对超时还没下单的需求标红。

## Impact

- 后端：
  - 新模块 `modules/system/notification`：数据表、`Notifier`（事务提交后发送）、`NotificationService`（合并、读取时检查待办是否已处理）、通知接收人接口。
  - 各模块用自己的 `XxxNotices` 拼通知、`XxxNotificationChecker` 判断待办是否已处理；通知失败不影响业务。
  - 需要埋点的位置：
    - 询价：`SourcingTaskServiceImpl.transferToPurchasing / assign / assignByRecommend / reassign`，`MyTaskServiceImpl.returnTask` 与回价提交。
    - 收款：`PaymentReceiptServiceImpl.uploadSlip / confirm / confirmOrder / platformReceipt`。
    - 销售订单与采购需求：`SalesOrderServiceImpl.convert / create / cancel / partialCancel`，补发订单，`RequirementLifecycle.reassign / reassignSoItems`。
    - 发货与收货：`warehouse/ShipmentService.create`，`ReceiptService.accept / direct`，到货差异生成。
  - 新增每日定时任务：检查到了预计到货日还没收货的发货单，按租户逐个执行。
- 前端：`components/Shell/TopBar.tsx` 增加铃铛与通知抽屉；系统设置增加「通知接收人」页；采购需求列表标红。
- 数据库迁移 `V1.5.22`：新增 `notification`、`notification_receiver` 两张表、系统配置、「通知接收人」菜单与按钮；接收人默认值按权限实时计算，不初始化数据。可以回退（删表、删菜单）。
- 多租户：两张新表在 `TenantTables` 归类为私有；定时任务用 `TenantScope.platform` 逐租户执行。
