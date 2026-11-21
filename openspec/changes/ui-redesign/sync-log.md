# 同步记录

每次把 develop 合进 `feat/ui-redesign` 都记在这里（见 `design.md` 第 1 节）。

| 日期 | 合入范围 | 涉及界面的改动 | 处理状态 |
|---|---|---|---|
| 2026-11-20 | `8b284d01..f79ac93f`，19 个提交，含 v1.40.0 | `doc-list-unify` 第一批（烛龙开源）：新增公共组件 `src/components/ListLayout/`（FilterCard、FilterField、MainCard）；`ModelQtyCell` 多型号可点开；采购入库、销售出库改为新骨架；`ui-design-patterns.md`「单据页统一规范」增加列表页骨架。另有采购需求删除、文字报价货期，以及几个变更的归档 | 已合并，没有冲突。与本变更的规则有重叠，待用户确定取舍后修订 specs、design，见下方说明 |
| 2026-11-21 | `f79ac93f..fab4cb75`，46 个提交，含 v1.42.0、v1.42.1 | `inventory-foundation`：新增库存查询、库存流水、库存台账、仓库档案、其他出库列表与详情（`src/pages/warehouse/` 下 stocks、moves、ledger、warehouses、other-outbounds），闲置库存页下线；商品候选新增「待关联单据」面板（`UnlinkedPanel`）；`DocModelCell` 有改动；菜单与路由调整。规范在列表页规则 6 加了库存三页的例外 | 已合并。`ui-design-patterns.md` 冲突：保留本分支重写的版本，把库存三页的例外补进新版「列表页统一规范」第 6 条。新页面按新骨架改造的工作归入任务 5.2 的 `migration-list.md`。`navigation` 增量的「按部门分组的菜单框架」同步主规格：仓库管理菜单项改为库存一期后的列表，补「闲置库存菜单下线」场景 |

## 2026-11-20 与 doc-list-unify 的重叠

`doc-list-unify` 由用户在 2026-11-20 确认原型，已随 v1.40.0 上线，后续批次还会在 develop 上继续改造其他单据列表。

**两边一致、本变更直接沿用的**：
- 搜索条件区和主内容区分开；
- 主内容区的操作行：左边显示结果数或「已选 N 张」，右边放主操作；
- 表格后接分页；
- 「型号 · 数量」可以点开查看。

**有出入、需要用户确定的**：
- 状态的位置；
- 统计卡片怎么处理；
- 筛选项是全部展开，还是收进「更多筛选」。

**工程上的取舍**：本变更复用 `ListLayout` 组件并在它的基础上扩展，不再另建 `src/components/Page/`，避免同一种骨架出现两套组件。
