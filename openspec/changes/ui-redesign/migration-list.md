# 界面迁移清单

由 `npm run lint:ui`（`zhul-erp-frontend/scripts/lint-ui.js`）在 2026-11-21 全仓库扫描生成（复审后按语法树重写规则再扫一次），同步 develop 至 `fab4cb75` 之后。共 338 个错误、35 个提示。

- 这张表只是起点：各业务域迁移时，用 `npm run lint:ui -- src/pages/<目录>` 看当前的完整位置，按新设计改完、清零后，把目录加进脚本里的 `ENFORCED`。
- 「旧页头 / 面包屑」改用 `ListLayout` 的 `PageHeader`，不显示面包屑；「写死颜色」改用 `useAppTheme().palette` 的令牌；「输入框提示」按 `search-scope.md` 只留最常用的三项，完整范围放进帮助图标。
- 「保存后生效」和「输入框提示过长」只是提示，人工判断；确有必要的写法，在代码行上写 `lint-ui-ignore 规则名: 原因`。
- 统计页单列（任务 7.6）；销售订单列表和详情由样板页任务 6.1 处理；商机统计（`crm/opportunity` 里的统计面板）随商机一起迁移。
- 检查规则查不到的问题（统计卡片的去向、状态颜色、表格列与骨架）仍以 `stat-cards.md`、`status-colors.md` 和原型为准。

| 目录 | 对应任务 | 错误 | 提示 | 文件数 | 分项 |
|---|---|---|---|---|---|
| `src/pages/sales/orders` | 6.1 | 5 | 1 | 3 | 旧页头 / 面包屑 4、输入框提示超过三项 1、输入框提示过长（提示） 1 |
| `src/pages/dashboard` | 6.2 | 8 | 0 | 3 | 套话 6、旧页头 / 面包屑 2 |
| `src/components/CustomerQuickCreateModal` | 7.1 | 0 | 1 | 1 | 输入框提示过长（提示） 1 |
| `src/pages/crm` | 7.1 | 13 | 0 | 4 | 旧页头 / 面包屑 12、输入框提示超过三项 1 |
| `src/pages/customer` | 7.1 | 15 | 8 | 10 | 旧页头 / 面包屑 12、输入框提示过长（提示） 7、写死颜色 2、「保存后生效」（提示） 1、输入框提示超过三项 1 |
| `src/pages/quotation` | 7.1 | 10 | 4 | 6 | 旧页头 / 面包屑 9、「保存后生效」（提示） 2、输入框提示过长（提示） 2、套话 1 |
| `src/pages/sales` | 7.1 | 31 | 7 | 15 | 旧页头 / 面包屑 17、写死颜色 10、输入框提示过长（提示） 6、渐变 2、套话 1、「保存后生效」（提示） 1、输入框提示超过三项 1 |
| `src/pages/inquiry` | 7.1、7.2 | 21 | 1 | 10 | 旧页头 / 面包屑 17、写死颜色 2、输入框提示超过三项 2、输入框提示过长（提示） 1 |
| `src/pages/purchase` | 7.2 | 21 | 0 | 8 | 旧页头 / 面包屑 17、输入框提示超过三项 4 |
| `src/pages/supplier` | 7.2 | 10 | 0 | 4 | 旧页头 / 面包屑 10 |
| `src/pages/supply-chain` | 7.2（tasks 未单列，暂归采购） | 18 | 0 | 6 | 旧页头 / 面包屑 10、写死颜色 7、渐变 1 |
| `src/pages/logistics` | 7.3 | 9 | 1 | 5 | 旧页头 / 面包屑 8、输入框提示过长（提示） 1、输入框提示超过三项 1 |
| `src/pages/warehouse` | 7.3 | 32 | 1 | 15 | 旧页头 / 面包屑 29、输入框提示超过三项 3、输入框提示过长（提示） 1 |
| `src/pages/finance` | 7.4 | 38 | 1 | 20 | 旧页头 / 面包屑 36、输入框提示超过三项 2、输入框提示过长（提示） 1 |
| `src/pages/operations` | 7.5 | 8 | 1 | 5 | 旧页头 / 面包屑 8、输入框提示过长（提示） 1 |
| `src/pages/product` | 7.5 | 58 | 3 | 13 | 写死颜色 44、旧页头 / 面包屑 12、输入框提示过长（提示） 3、渐变 2 |
| `src/pages/finance/operating-report` | 7.6 | 2 | 0 | 1 | 旧页头 / 面包屑 2 |
| `src/pages/finance/profit-stats` | 7.6 | 2 | 0 | 1 | 旧页头 / 面包屑 2 |
| `src/pages/inquiry/sourcing-stats` | 7.6 | 2 | 0 | 1 | 旧页头 / 面包屑 2 |
| `src/pages/purchase/bargain-stats` | 7.6 | 2 | 0 | 1 | 旧页头 / 面包屑 2 |
| `src/pages/sales/stats` | 7.6 | 3 | 0 | 1 | 旧页头 / 面包屑 2、写死颜色 1 |
| `src/pages/system` | 7.7 | 28 | 6 | 13 | 旧页头 / 面包屑 18、写死颜色 10、输入框提示过长（提示） 6 |
| `src/pages/tenant` | 7.7 | 2 | 0 | 1 | 旧页头 / 面包屑 2 |
