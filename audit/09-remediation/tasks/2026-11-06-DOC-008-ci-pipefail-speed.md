# DOC-008 CI 测试失败被掩盖的修复与提速

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（排查、实施与验证）；用户 Ezio 提出 CI 太慢并验收 |
| 关联 ID / 原专题 | [DOC-008](../../05-code-and-docs/01-文档与测试一致性.md)；相关 [DOC-005](2026-11-03-DOC-005-ci-pipeline.md)（建立 CI）、[AI-001](2026-11-04-AI-001-task-reliability-batch-f.md)（终态不可回退） |
| 目标与影响范围 | `.github/workflows/ci.yml` 与报价单测试 `QuotationContractTest`。不改产品代码、数据库、配置 |
| 起点 commit / 工作分支 | develop `026e1879`；分支 `fix/doc-008-ci-pipefail-speed` |
| OpenSpec | 不适用：CI 与测试修复 |
| PR / 目标分支 | 见下文，发往 `develop` |
| 合并证据 | 未合并 |
| 目标版本 / 环境 | CI；进行中的 `release/1.9.1` 需要合入本修复，后端测试才能真正通过 |

## 实施与验证结果

- **掩盖失败：** 工作流加 `defaults.run.shell: bash`。GitHub 只有在写明 `shell: bash` 时才用 `bash --noprofile --norc -eo pipefail`，`mvn test | tee` 失败时步骤随之失败。
- **失败用例：** `QuotationContractTest` 按项目已有做法（`InquiryNameplateContractTest`）用 `@MockBean AiOrchestratorClient`，提交返回不会完成的 future，任务停在排队中，等测试手动回调；删去原来等待提交结果的循环。产品代码不变。
- **提速：**
  - **后端分两组并行：** 每组在单独的机器上起自己的 MySQL、Redis。第 1 组跑 finance、sales、logistics、purchase、warehouse；第 2 组跑其余全部，以后新增的包自动落在第 2 组。两组按上次耗时估算各约 200 秒。
  - **分组方式：** 用 surefire 的 `-Dsurefire.excludesFile` 排除另一组的包；本机实测排除清单生效。
  - **汇总检查：** 新增汇总任务，名字仍是分支规则要求的「后端测试」，两组都通过才算通过，分支规则不用改。
  - **只改文档时跳过：** 改动只涉及 `audit/`、`docs/`、`openspec/`、`.claude/` 或根目录 `*.md` 时，后端、前端、AI 三项测试都跳过；被跳过的必选检查在 GitHub 上算通过。`scripts/ai-orchestrator/skills/*.md` 是提示词，不算文档，照常测试。
  - **ffmpeg 只在第 2 组安装：** 视频转码测试在第 2 组。MySQL 改为先在后台启动，装 Java、ffmpeg 的同时等它就绪。
  - **ffmpeg 安装限时：** 本 PR 首次 CI 中，第 2 组的 `apt-get` 卡了 27 分钟仍未结束。安装步骤改为限时 5 分钟、非交互、apt 锁最多等 60 秒；装不上时 CI 给出警告并继续，只跳过视频转码测试。

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| 检查最近 13 次「后端测试」显示通过的 CI 日志 | GitHub Actions | — | 全部是 `BUILD FAILURE`，失败用例都是 `QuotationContractTest.bilingualDescriptions_confirmQuotationPiExport_andTranslation`；步骤 shell 为 `/usr/bin/bash -e {0}` | 缺陷确认 |
| `mvn -o test -Dtest=QuotationContractTest` | 本机 MySQL 测试库，修复前 / 修复后 | 修复后通过 | 修复前 `expected: <3> but was: <4>`；修复后 20 个用例通过、跳过 1 个 | 通过（缺陷已复现并修复） |
| `mvn -o test -Dsurefire.excludesFile=<只保留 workbench>` | 本机 | 只运行未被排除的测试 | 只运行了 `WorkbenchContractTest`、`BossWidgetsMoneyTest` | 通过 |
| 本 PR 首次 CI（run 42203433362） | GitHub Actions | 两组都通过 | 第 1 组 219 个用例通过，`mvn test` 309 秒，步骤 shell 已是 `bash -e -o pipefail`；第 2 组在「安装 ffmpeg」卡住 27 分钟，已手动取消 | 第 2 组未运行，已加安装限时 |
| 本 PR 修正后的 CI | GitHub Actions | 两组都通过，「后端测试」汇总通过，耗时下降 | 待 CI 运行后补充 | 待验证 |

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| DOC-008 | 测试失败时 CI 失败；报价翻译用例通过；后端分组并行；只改文档的 PR 跳过测试 | N/A：只涉及 CI，以 GitHub Actions 运行结果为准 | — | 处理中 |

## 阻塞与下一步

- 本 PR 合并后，把 develop 合入 `release/1.9.1`，[#197](https://github.com/zhul-cloud/zhul-trade-erp/pull/197) 的后端测试才会真正通过；#197 的自动合并已先暂停，等真实结果再开。
- 合并后观察几次 CI 的实际耗时，按需要调整两组的模块分配。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-06 / Claude | 用户反映 CI 太慢。排查耗时时发现后端测试失败被管道掩盖，失败用例已随 v1.9.0 上线；暂停 #197 自动合并；修复用例并加 pipefail，同时拆分后端测试、只改文档时跳过测试 | 提 PR 发往 `develop`，用 CI 结果验证 |
| 2026-11-06 / Claude | 首次 CI：第 1 组通过（5 分钟）；第 2 组 apt 安装 ffmpeg 卡 27 分钟（用户发现），取消该次运行，给安装步骤加 5 分钟时限并允许失败后继续 | 观察修正后的 CI |
