# BUS-028 用户反馈缺陷修复（2026-11-05 第一批）

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（实施与验证） |
| 关联 ID / 原专题 | BUS-028、BUS-029、BUS-030、UI-042；来源为 2026-11-05 用户汇总的 15 条使用反馈中的第 14、6、10、11 条，其余为新功能或改进，不在本记录 |
| 目标与影响范围 | 报价毛利率输入、采购需求可见范围、询价任务改派后的上一轮记录、报价单 / PI 图片导出；不改数据库结构 |
| 起点 commit / 工作分支 | `ec30d75c9214076a1bc5dbb1617682f24dce2642`（develop）/ `fix/feedback-1105` |
| OpenSpec | 缺陷修复，不新开提案 |
| PR / 目标分支 | [#184](https://github.com/zhul-cloud/zhul-trade-erp/pull/184)：fix/feedback-1105 → develop |
| 合并证据 | develop：merge commit `e217a3560f2bb0ec9181d76eff6c6448c0125b11`（2026-11-05，用户确认合并） |
| 目标版本 / 环境 | 下一个发布版本；生产 |

## 实施与验证结果

- **BUS-028**：毛利率按售价算，售价 = 成本 ÷ (1 − 毛利率)，100% 不成立。原来输入框上限 99.99%，填 100 被截成 99.99%，售价约为成本的 1 万倍且不报错。现在去掉输入框上限，≥ 100% 时行内报错「毛利率按售价算，需小于 100%；成本翻倍请填 50%，或改用「加价」」并阻止保存（后端原本就拒绝 ≥ 100%）；报价策略的提示补充「成本翻倍是 50%，最高 95%」。位置：`zhul-erp-frontend/src/pages/quotation/calc.ts`、`detail.tsx`、`StrategyDrawer.tsx`。
- **BUS-029**：采购需求按采购员套数据权限，采购员为空的只有「全部」数据权限可见。现在有「指派采购员」权限（`purchase:requirement:assign`）的人，在自己的数据范围之外还能看到采购员为空的需求，并计入「待分配」统计。位置：`PurchaseRequirementServiceImpl.scoped()`、`stats()`。
- **BUS-030**：改派只把原采购从任务上移除，报价记录本来就在库里；但接手人页面的「上一轮记录」只在任务被退回过或型号进入下一轮时才返回，改派后看不到原采购已找到的价格。现在被改派掉的采购留下的记录（已提交和只保存未提交的）都显示给接手人，未提交的标「未提交」，标题改为「之前的记录」。位置：`MyTaskServiceImpl.previousQuotes()`、`PreviousQuoteVO.draft`、`my-tasks/index.tsx`。
- **UI-042**：图片导出原来按 120 dpi 渲染整页 A4、质量 0.8，宽度上限 1600，内容少时下方大片空白。现在 240 dpi 渲染，每页裁到内容（四周留 2% 边，最底部隔着大片空白的页码行去掉），宽度上限 2000，质量 0.9。位置：`DocumentConverter.toJpeg()`、`trim()`，报价单与 PI 的 `IMAGE_MAX_WIDTH`。

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| `npx jest src/pages/quotation src/pages/inquiry`（`calc.test.ts` 断言 100% 的提示文案） | 本机，fix/feedback-1105 | 通过 | 50 个通过 | 通过 |
| `npm run lint`（Biome + tsc） | 本机 | 无错误 | 无错误 | 通过 |
| `PurchaseContractTest#unassignedRequirements_visibleToAssigners` | 本机 JDK 17 + 测试库 | 无指派权限只看到自己的 1 条、待分配 0；加指派权限后看到 2 条、待分配 1，能指派 | 一致；本类 17 个通过 | 通过 |
| `SourcingContractTest#reassign_newAssigneeSeesFormerRecords` | 本机 | 林熙保存未提交一条后改派给江晓晞，江看到 1 条之前记录、标未提交、价格 320，另一型号无记录，原记录保留 | 一致；`SourcingContractTest`、`SalesFirstSourcingContractTest` 全部通过 | 通过 |
| `DocumentRenderTest`（含新增 `trim_keepsContentDropsFooter`，30 行报价单图片宽 > 1600 且高宽比 < 1、≤ 800KB） | 本机，LibreOffice 已安装 | 通过 | 通过；样图 556KB，目测无页边和底部空白、文字清晰 | 通过 |

| PR #184 CI（[run 42008132774](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/42008132774)）：后端全量、前端检查、AI 编排服务测试 | GitHub Actions | 全部通过 | 全部通过 | 通过 |

未覆盖：未在浏览器里走查毛利率提示与改派后的页面。

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| BUS-028 | 填 100% 时提示并阻止保存；填 50% 售价为成本 2 倍 | v1.9.0 已部署（[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/42171099361) 成功） | — | 已发布待验收 |
| BUS-029 | 供应链主管（自定义数据权限 + 指派权限）看到未指定需求并能分配 | v1.9.0 已部署（[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/42171099361) 成功） | — | 已发布待验收 |
| BUS-030 | 多型号任务改派后接手人看到原采购记录 | v1.9.0 已部署（[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/42171099361) 成功） | — | 已发布待验收 |
| UI-042 | 报价单 / PI 导出图片清晰、无大片空白 | v1.9.0 已部署（[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/42171099361) 成功） | — | 已发布待验收 |

## 阻塞与下一步

已合并到 develop，待下一版发布；随下一版发布后在生产验收。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-05 / Claude | 用户反馈第 14、6、10、11 条确认为缺陷，登记 BUS-028–030、UI-042；实施并通过上述测试 | 创建 PR |
| 2026-11-05 / Claude | 创建 PR #184，CI 全部通过 | 待合并 |
| 2026-11-05 / Claude | 用户确认后合并到 develop（`e217a356`） | 随下一版发布，上线后验收 |
| 2026-11-11 / Claude | 台账核对：#184 已随 v1.9.0 部署成功，改为已发布待验收 | 待生产验收 |
