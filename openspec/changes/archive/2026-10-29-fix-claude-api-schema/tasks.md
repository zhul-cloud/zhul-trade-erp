# Tasks

## 1. Claude API Schema 与结果适配

- [x] 1.1 复用纯转换器生成全字段必填、原可选字段可空的 Claude API Schema，删除原地修改辅助函数；测试完整字段、零 optional、原 Schema 不变。
- [x] 1.2 Claude API 结果归一化后按原业务 Schema 校验；回归 null 缺失、空串/0/空数组保留及非法输出失败回调。

## 2. 兼容性与验收

- [x] 2.1 补充同实例 API 前后两种订阅 Schema/参数及 Claude CLI 命令回归；确认 OpenAI API 既有测试通过。
- [x] 2.2 以现有镜像只读挂载工作树运行全部 Linux unittest，并执行 OpenSpec 严格校验与 git diff --check，记录结果。
- [x] 2.3 受控真实 Claude API 合成输入验证编译和输出，记录证据；失败时保留未完成并调整方案，不重试业务任务或写数据库。

## Workflow follow-up

- 验收通过后提交正规 PR，目标为最新 release 分支；本变更不触发生产部署。
- 合入并经授权后归档 OpenSpec 变更。

## Verification evidence

- 离线验证：现有 `zhul-erp-ai-orchestrator:local` 镜像只读挂载当前工作树，`--network none --read-only --tmpfs /tmp:rw,nosuid,nodev,exec,size=128m`，运行 `python -B -m unittest discover -s /app/ai-orchestrator -v`：47 tests，9.223s，OK，exit 0。含全部 Linux entrypoint/进程组回归；新增 6 项回归覆盖字段完整、缺失及显式空值、业务 Schema 不变、非法结果失败且不暴露非法业务值、错误传播、API 前后订阅 Schema/CLI 参数不变。首次测试的 tmpfs 未设 exec 导致测试用假 python 无法执行，修正容器临时目录参数后全通过，无对应产品改动。
- 规划验证：`openspec validate fix-claude-api-schema --strict` 通过；`git diff --check` 通过。
- 真实 API（主代理转述 `diagnose_prod_ai` 实测）：生产现有 Anthropic 1.14.0 SDK，`claude-opus-5-5`、medium、既有 fallback beta、web_search 工具定义（max_uses=1）、2000 输出 token 上限、SDK max_retries=0，发送同算法生成的全字段必填/13 个 nullable Schema 候选。HTTP 200，9.04s，end_turn，input 6461/output 216 tokens；原始候选结果与移除 null 后的业务结果均验证通过，原 Schema 不变，1 组 1 明细覆盖组 6 字段/明细 15 字段。未取得 request ID，SDK 未返回 web search usage，无法据此断言实际搜索次数。
- 真实验证边界：只验证 Schema 候选请求，未运行修复后完整生产服务，未实调订阅账号；没有业务任务重试、数据库写入、业务回调、服务变更或文件落盘。订阅兼容性由离线调用/参数/CLI 协议回归验证。
