# Design

## Context

见 proposal.md。当前 Claude API 通过 `_strict` 原地添加 `additionalProperties`，可能改变全局业务 Schema；OpenAI 已使用深复制的全必填、可空 Schema。订阅 Claude 使用原 Schema，Codex 使用 OpenAI 适配 Schema，订阅进程已有独立校验。

## Goals / Non-Goals

**Goals:** 保持所有业务字段及缺失/空串/0/空数组语义，消除 API 对共享 Schema 的修改，验证非法结果失败与订阅隔离。

**Non-Goals:** 不修改 CLI、订阅隔离、搜索策略、模型、租户配置、后端、数据库或生产服务。

## Decisions

1. Claude API 复用已有纯 `_openai_schema` 转换，避免另写递归转换器；该函数改名为中性的 `_required_nullable_schema`，同步现有 OpenAI API/Codex 订阅调用点，算法保持不变。全部属性列入 required，仅原可选属性加 null 类型，所有对象关闭额外属性。删除失去引用的 `_strict`。
2. Claude API 解析 JSON 后先移除 null，再以未修改的原业务 Schema 校验。必填 null 移除后成为缺失，仍校验失败；空串、0、空数组不移除。复用已存在的 `jsonschema` 依赖，ValidationError 包装成不含原始实例的固定错误并保留异常链，沿现有失败回调传播，避免日志与回调暴露完整业务输出。
3. 不改变订阅分支、提示词及执行参数，不为兼容填造整数或空值。全部字段直接必填但填写默认值会丢失缺失语义，因此不采用；数组包装需改变服务商传输类型，仅在本候选无法通过真实 API 时另行评审。

## Risks / Trade-offs

- 13 个 optional 转为 13 个 union 仍可能触发内部复杂度限制 → 官方 union 上限不是通过保证；必须进行受控真实 API 编译/输出验证，失败则不宣布修复完成并调整设计。
- mock 结果可能与服务端保证不同 → 本地按原业务 Schema 校验必填/类型，测试非法输出、null 与显式空值。
- 复用转换器影响共享调用点 → 算法不改，完整回归 OpenAI API 与两种订阅，测试 API 调用前后 Schema 和 Claude CLI 参数不变。

## Migration Plan

无配置项或数据库迁移。正常 PR 合入 release 分支；生产上线由既有流程单独处理。回滚为撤销本次 Python 变更并重新发版，不改存量任务数据。

## Verification

在现有 Docker 镜像中只读挂载工作树，运行全部离线 unittest（含 Linux 进程组测试）；验证全字段、原 Schema 不变、nullable 归一化、错误失败回调及订阅前后行为。真实 API 使用合成输入且不回写业务任务或数据库，验证结果记入 tasks。
