# UI-095 发货通知「发通知」置灰不说原因

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（实施与验证）；用户（报告与验收） |
| 关联 ID / 原专题 | UI-095（新发现，用户 2026-11-16 生产反馈，查重无既有 ID） |
| 目标与影响范围 | 销售订单详情「发货通知」弹窗：货代多于一家时不默认选中，按钮置灰但没有提示，用户误以为型号不能发；货代下拉含历史迁移的「未登记货代」占位。修复后置灰时在按钮旁说明原因，下拉不再列出占位。不含：销售出库页新建入口（用户决定暂缓） |
| 起点 commit / 工作分支 | `9745c5a46760da89a548c14fb4d40b38f1f43cdf` / `fix/ship-notice-forwarder` |
| OpenSpec | 不适用：缺陷修复，不改业务规则 |
| PR / 目标分支 | 待创建 → `develop`，随后发版 `release/1.27.0` → `main` |
| 合并证据 | 未合并 |
| 目标版本 / 环境 | v1.27.0 / 生产，租户 1000 |

## 实施与验证结果

根因（生产只读核对）：SO20261110008 的 ATV71HD30N4 已合格入库 1 个（GR20261116006），没有出库单，可出库为 1；租户 1000 有 4 家启用的服务商，弹窗只在仅一家时自动选中，按钮因未选货代置灰。

- 前端 `zhul-erp-frontend/src/pages/sales/orders/shipNotice.tsx`：按顺序给出「先选货代 / 还没有启用的货代 / 先勾选要发货的型号 / 某型号最多可出库 N」，显示在底部按钮左侧，按钮可点与否只看这一个判断。
- 后端 `LogisticsSupport.forwarders()`：按 `migration_id_map`（`suppliers` / `MIG-FORWARDER`，手写租户条件）排除占位；`requireForwarder` 不变，历史出运单仍可引用。

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| `npx jest src/pages/sales/orders/shipNoticeBlocker.test.tsx` | 本机 jsdom，工作分支 | 两家货代显示「先选货代」且按钮禁用；一家时自动选中可点 | 2/2 通过 | 通过 |
| `mvn -o test -Dtest=OutboundShippingContractTest,TenantIsolationContractTest`（含 `forwarders_excludeMigrationPlaceholder`） | 本机隔离库 zhul_erp_test | 下拉含普通货代、不含占位 | 通过 | 通过 |
| `npm run lint`、`npx antd lint ./src` | 本机 | 无错误 | 无错误 | 通过 |

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| UI-095 | SO20261110008 打开发货通知：未选货代时提示原因；下拉没有「未登记货代」；选货代后可发通知 | 未部署 | — | 待发布 |

## 阻塞与下一步

合并到 develop，发版 v1.27.0 上线后由用户在生产验收。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-16 / Claude | 用户反馈，生产只读定位根因，实施并本机验证 | 提 PR 合入 develop 并发版 |
