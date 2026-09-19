# 开发计划：商品主数据（add-product-master-core）

> 这份文件把 `tasks.md` 的任务排成可执行的阶段，写明先后依赖、体量和每阶段的验证命令。任务的验收标准仍以 `tasks.md` 为准，本文件不重复。

## 影响范围

| 范围 | 内容 |
|---|---|
| 新增 | 后端 `modules/product`；`sql/build/sql/schema_v1.2.sql`（13 张表）；`resource` 表种子数据；前端 `src/pages/product/`；迁移脚本 `scripts/product-migration/` |
| 修改（加法，不改现有行为） | `BizException`、`GlobalExceptionHandler`、`Result` 承载 `errorCode`/`detail`（0.3）；`application.yml` 上传上限（0.4）；前端菜单与路由 |
| 不改 | v1.0 / v1.1 的任何已有表；询盘模块（工作区里已有的询盘未提交改动与本 change 无关，提交时分开） |

事务边界：商品相关写操作都在 `ProductServiceImpl` 等 Service 方法上声明 `@Transactional(rollbackFor = Exception.class)`；跨表的整体替换（规格）、对称关系、设主图各在一个事务内完成；缓存与 AI 之类的副作用一律在提交后执行。

## 环境约定

```bash
export JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home   # 0.1 之后用 source scripts/dev-env.sh
cd zhul-erp-backend && mvn -q -o test -Dtest='Product*Test'                   # 商品模块单测
cd zhul-erp-frontend && npx tsc --noEmit && npx biome check src/pages/product  # 前端检查
openspec validate add-product-master-core --strict                             # 规格校验
```

## 阶段与依赖

体量：S ≤ 半天，M ≈ 1 天，L ≈ 2–3 天（按一个人顺序做的估计，仅用于排序）。

```
P0 基础设施 ──► P1 数据库 ──► P2 骨架+主数据 ──► P3 商品核心 ──► P4 内容与档案 ──┐
                                                                                ├─► P6 迁移与收尾
                                          P5 前端（P2 完成后可与 P3/P4 并行） ─────┘
```

| 阶段 | 任务 | 体量 | 依赖 | 出口检查 |
|---|---|---|---|---|
| **P0 基础设施** | 0.1 环境脚本、0.2 集成测试基础、0.3 错误响应扩展、0.4 上传上限 | M | 无 | 现有全部单测仍通过；冒烟集成测试连的是 `zhul_erp_test` |
| **P1 数据库** | 2.1 `schema_v1.2.sql`、2.2 唯一键大小写验证、2.3 菜单与权限种子、2.4 数据模型文档 | M | P0.2 | 在测试库上重复执行 `schema_v1.2.sql` 无报错；2.2 的两组插入第二次都冲突 |
| **P2 骨架与主数据** | 3.1 骨架与实体、3.2 `MpnNormalizer`、3.3 `PlatformScopeGuard`、3.4 租户处理；4.1–4.6 品牌 / 品类 / 系列 | L | P1 | `Product*Test` 全绿；品牌、品类、系列的 spec 场景全部有测试 |
| **P3 商品核心** | 5.1–5.11 商品创建、去重、恢复、生命周期、启停、删除保护、规格、型号关系、对称关系、列表详情、写接口鉴权；6.1、6.2、6.4 查找 | L | P2 | 并发创建同型号只成功一个；租户账号写接口全部被拒 |
| **P4 内容与档案** | 5.12–5.14 技术资料 / 应用场景 / FAQ，5.15–5.16 图片视频，5.17–5.19 物流 / 海关 / 参考价，5.20–5.21 完整度与缺项筛选，6.3 契约与覆盖率 | L | P3 | 覆盖率 ≥70%，`tasks.md` 6.3 列的核心类 100% |
| **P5 前端** | 7.9 主题、7.6 路由菜单、7.2 三个列表页、7.3 商品列表、7.5 选择器、7.8 完整度组件、7.4 档案页、7.7 向导、7.10 可访问性验收 | L | 页面依赖对应后端接口；可按接口就绪顺序推进 | 类型检查与 lint 通过；手动走查 PRD 5.x 流程 |
| **P6 迁移与收尾** | 8.1–8.4 迁移，9.3 完成定义检查 | M | P4 | 迁移计数与干跑一致，幂等，回滚有效 |

7.1（原型）已完成，待用户确认，不阻塞开发；7.2 起以原型为准。

## 进度（2026-09-19）

| 阶段 | 状态 | 验证 |
|---|---|---|
| P0 基础设施 | ✅ 完成 | 冒烟集成测试连的是 `zhul_erp_test`；`BizException` 增加 `errorCode`/`detail` |
| P1 数据库 | ✅ 完成 | `schema_v1.2.sql`（13 张表）与 `data_v1.2.sql`（菜单与按钮权限）在测试库上可重复执行；`Siemens`/`siemens` 等唯一键冲突已实测 |
| P2 骨架与主数据 | ✅ 完成 | 品牌、品类、系列的全部 spec 场景有测试 |
| P3 商品核心 | ✅ 完成 | 6 线程并发创建同一型号只成功 1 个；租户账号写接口全部被拒 |
| P4 内容与档案 | ✅ 完成 | 12 条 HTTP 契约测试（含 JWT `tenantId=0` 的平台账号）；商品模块行覆盖率 94.2% |
| P5 前端 | ⬜ 未开始 | — |
| P6 迁移与收尾 | ⬜ 未开始 | — |

后端全量 463 个测试通过。开发库 `zhul_erp` 上尚未执行 `schema_v1.2.sql` 和 `data_v1.2.sql`（联调前需要执行，见下）。

在开发库上建表与写入权限种子：`schema_v1.2.sql` 与 `data_v1.2.sql` 头部都是 `use zhul_erp;`，
`mysql -uroot -p < sql/build/sql/schema_v1.2.sql`、`mysql -uroot -p < sql/build/data/data_v1.2.sql`（两个脚本都可重复执行，不含 `drop schema`，不影响现有表）。

## 提交节奏

每个阶段结束后由用户决定是否提交（不自行提交）；提交时信息用 `feat(product): ...`，暂存用明确路径，只带商品域与本 change 的文件，询盘的未提交改动不带入。`tasks.md` 随做随勾，每次提交里的勾选状态与代码一致。

## 已做的决定与待确认

- **错误响应**：数字 `code` 与 `message` 保持不变，`errorCode`、`detail` 放在 `data`（design.md 决策 10）。与 `coding.md` 里 `{"code": "ORDER_NOT_FOUND"}` 的写法不同，原因是现有前端依赖数字 `code`。
- **汇率**：由请求传入，系统里没有汇率表（design.md Q12 已解决）。
- **测试库**：新建 `zhul_erp_test`，不动开发库 `zhul_erp`；开发库上执行 `schema_v1.2.sql` 放在 P1 出口之后、前端联调之前。
