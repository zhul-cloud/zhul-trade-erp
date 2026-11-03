# DOC-005 PR 自动测试入口；DOC-004 整表清空的测试恢复种子数据（批次 I）

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（实施与验证）；用户 Ezio 决定合并与分支保护设置 |
| 关联 ID / 原专题 | [DOC-005、DOC-004](../../05-code-and-docs/01-文档与测试一致性.md) |
| 目标与影响范围 | 新增 `.github/workflows/ci.yml`。测试基类提供整表清空与恢复，8 个商品类测试改用它。分支规范「日常开发」注明 CI。不改业务代码，不改 `release.yml` |
| 起点 commit / 工作分支 | `develop` `4d70e741`；`fix/ci-pipeline` |
| OpenSpec | 不适用：测试与 CI 保障 |
| PR / 目标分支 | [zhul-cloud/zhul-trade-erp#139](https://github.com/zhul-cloud/zhul-trade-erp/pull/139)：`fix/ci-pipeline` → `develop`，merge commit `0db2d13b1656d453a24d2d68a75a1cecea4ce189`（2026-11-03） |

## 方案与实施

- **DOC-005**：新增 `ci.yml`，在发往 `develop`、`main`、`release/**`、`hotfix/**` 的 PR 上触发，推送到 `develop` 时也触发，也可以手动运行。三个任务并行：
  - **后端**：`mvn test`。用 `docker run` 起 MySQL 8.0，带 `--default-time-zone=+08:00`，因为服务容器不能传启动参数。Redis 7 用服务容器。`TZ=Asia/Shanghai`。失败时上传 surefire 报告。
  - **前端**：`npm ci`，然后跑 `npm run lint`（biome + tsc）、`npx antd lint ./src`、`npx jest --ci`、`npm run build`。
  - **AI 编排服务**：Python 3.12，`python -m unittest`。
- **DOC-004**：
  - `IntegrationTestBase.wipeTables(...)` 在整表清空前给每张表存快照。基类的 `@AfterEach` 在子类收尾之后统一恢复。`snapshotCategories()` 改为复用这套机制。
  - 8 个商品类测试原来清空商品、系列、别名、品牌表，只恢复品类，现在都改用 `wipeTables`。其中 5 个原来清了品牌却没清别名，已补上别名表，否则恢复后的种子别名会指向已清空的品牌。

## 实施与验证结果

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| 修复前全量 `mvn test` 后查测试库：品牌、系列、别名、品类行数 | 本机 Docker MySQL，合并 #137 后代码 | 种子被清空 | 0 / 0 / 0 / 157（开发库种子 86 / 510 / 114 / 157） | 缺陷见证成立 |
| 删除测试库重建后全量 `mvn test`，再查行数与残留快照表 | 本机，`fix/ci-pipeline` 工作区 | 种子保留，无残留 | 86 / 510 / 114 / 157，快照表 0；1296 个中 1 个错误 | 暴露 `ProductMasterDataIntegrationTest` 清品牌不清别名（顺序依赖），已修 |
| 商品类测试定向重跑 | 同上 | 通过 | 通过 | 通过 |
| 随机类顺序全量 `mvn test`（`ClassOrderer$Random`，seed 20261103，先清空报告目录） | 本机 | 通过，种子不变 | 1295 个，0 失败，7 个环境跳过；种子 86 / 510 / 114 / 157，快照表 0。此前统计的 1296 多算了 `OssLiveIT` 的旧报告（surefire 不运行 `*IT`） | 通过 |
| #139 上 CI 首次实跑 | GitHub Actions ubuntu-latest，`70951ca5` | 三项通过 | 后端测试 4 分 57 秒、前端检查 1 分 34 秒、AI 编排服务测试 49 秒，全部通过；编排服务未复现本机的读超时 | 通过 |
| 失败注入：草稿 [#140](https://github.com/zhul-cloud/zhul-trade-erp/pull/140) 新增必定失败的 `CiFailureProbeTest` | GitHub Actions，[run 41667843568](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/41667843568) | 后端测试失败，其余不受影响 | 后端 1296 个中只有注入用例失败，检查结论 failure；另两项通过。#140 已关闭、分支已删，未合并 | 验收成立 |
| 前端 `npm run lint`、`npx antd lint ./src`、`npx jest --ci` | 本机 Node | 通过 | lint 通过；antd 407 个文件无问题；Jest 23 套件 165 个通过 | 通过 |
| 编排服务 `python -m unittest`（临时虚拟环境） | 本机 Python 3.13 | 通过 | 64 个通过，3 个跳过；首次运行 `HttpTokenTest` 有一次 10 秒读超时，随后连续 5 次通过 | 疑为本机冷启动，在 CI 中观察 |

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| DOC-005 | PR 上三个任务全绿；注入一个可撤销的失败时对应任务失败 | N/A：CI 不需要发布 | — | 暂缓：CI 已合并并验证；门禁未生效，需仓库所有者账号 `zhul-cloud` 为 `develop`、`main` 设置「必须通过的检查」。当前协作者账号没有管理权限，规则集为空（2026-11-04 只读核实）。设置后只读核实即可关闭 |
| DOC-004 | 全量、随机类顺序跑后种子行数不变，没有残留快照表 | N/A：只改测试 | — | 已合并待发布：无需发布，CI 上全量也通过；待用户确认后关闭 |

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-03 / Claude | 用户要求做批次 I 的 CI；在 `fix/ci-pipeline` 实施，完成本地验证（见上表） | 随机顺序全量验证后创建 PR，并在 PR 上做失败注入 |
| 2026-11-03 / Claude | #139 CI 首次实跑三项通过后，按用户要求合并（`0db2d13b`），分支已删 | 失败注入 |
| 2026-11-04 / Claude | #140 失败注入：只有注入用例失败，后端检查失败；关闭 #140 并删分支。只读核实：仓库属个人账号 `zhul-cloud`，当前账号无管理权限，没有规则集与分支保护 | 等所有者设置必须通过的检查；DOC-004 等用户确认关闭 |
