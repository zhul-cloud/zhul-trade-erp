# 验收台账：AI 连接方式自动切换

- 环境：本机 docker（后端、前端、编排服务为本变更构建，迁移 V1.5.43 已执行），BrowserOS neo，2026-11-17；平台租户（tenant 0）测连接方式与切换，本机测试租户 TN1001 测租户视角（本机 MySQL 为临时存储，Docker 停止后数据清空，实测前按原方式重建了测试套餐与租户）
- 连接方式：Claude API 填写无效的占位 Key（服务商真实返回认证失败）；OpenAI API 不填；两个平台订阅本机已登录（真实调用成功）
- 自动化：编排服务 Python 174 个通过（限内存容器，含新增切换测试 20 个）；后端全量 1800 个 0 失败（53 个连接数受限的错误单独重跑通过）；前端 lint、tsc、antd lint、Jest 493 个通过
- 方式：页面 = 页面实测；数据 = 数据库、Redis、日志核对；自动 = 契约 / 单元测试

## 使用顺序与设置页

| # | 验收点（规范场景） | 方式 | 证据 | 结论 |
|---|---|---|---|---|
| A1 | 两组卡片：API Key 在前、平台订阅在后，组间提示「API Key 都不可用时，才使用平台订阅」 | 页面 | 与原型 V4-S1 一致：序号、品牌图标、状态、测试、开关 | 通过 |
| A2 | 组内拖动调整顺序（调整组内顺序） | 页面 + 数据 | 鼠标拖动 OpenAI 订阅到 Claude 订阅前：保存条「OpenAI 平台订阅 排到第 3 位」，顶部「顺序调整后还没保存，新任务仍按原顺序使用」；保存后 `sort_order` 订阅 openai=1、claude=2 | 通过 |
| A3 | 订阅卡片拖到 API Key 组不能放（订阅不能排到 API 前面） | 页面 + 自动 | 拖到 API 组松手顺序不变、不出现保存条（实测中把碰撞判断改为按重叠，拖出本组不再吸附到组内卡片）；接口跨组提交被拒见 `subscriptionCannotBeOrderedBeforeApi` | 通过（实测中修复） |
| A4 | 保存 Key 二次确认、脱敏显示 | 页面 | 「确认保存 API Key？」→ 卡片显示 `sk-ant-••••0000`、「已配置」 | 通过 |
| A5 | 卡片内测试连接，失败原因显示在卡片下方 | 页面 | 「API Key：Key 无效或已失效」，不含 Key | 通过 |
| A6 | 开关关闭的方式不参与；关闭后顶部说明 | 页面 + 自动 | 关闭两个订阅后卡片「已关闭」；`disabledConnectionIsSkippedButKept`、`summarize` 用例 | 通过 |
| A7 | 未授权租户看不到订阅组 | 页面 + 自动 | TN1001 套餐不含「使用平台订阅」时只有 API Key 组、顶部「还没有配置 AI」；`tenantWithoutGrantCannotUseSubscription` | 通过 |
| A7b | 获授权租户看到订阅组，只显示可用与否，模型由平台设置（租户使用平台订阅） | 页面 | 套餐加入后：「平台提供，模型由平台设置」，卡片「可用」；选中订阅卡片右侧「由平台设置」只读，全页没有登录相关信息（与原型 V4-S4 一致） | 通过 |
| A8 | 只读用户不能拖动、开关或测试 | 自动 | Jest「只读用户不能拖动、开关或测试」 | 通过（未页面实测） |

## 提交时的挑选与执行中切换

| # | 验收点（规范场景） | 方式 | 证据 | 结论 |
|---|---|---|---|---|
| B1 | Key 失效后换下一个 | 数据 | 新建客户触发的名称翻译任务 #1：日志「连接方式 api:claude 失败（auth），改用下一个」，任务成功，`used_connection=subscription:openai`、`failovers=[{api:claude, auth}]` | 通过 |
| B2 | 切换一发生就暂停，新任务跳过（跳过暂停中的方式） | 数据 | 切换事件到达即写 `zhul:erp:ai:pause:0:api:claude`（TTL 约 600 秒）；随后的询盘解析任务 #2 直接用订阅、没有切换记录 | 通过 |
| B3 | API Key 不可用时才用订阅 | 数据 | 任务 #1、#3 都先试 Claude API，失败后才用订阅；OpenAI API 无 Key 被跳过 | 通过 |
| B4 | 分批解析中途切换，已完成批次保留 | 自动 | `test_failed_connection_skipped_for_later_calls_and_earlier_results_kept` | 通过（未实测大附件） |
| B5 | 内容错误不切换 | 自动 | `test_content_error_does_not_switch`、`test_content_and_job_errors_never_switch` | 通过 |
| B6 | 全部失败的说明不含 Key | 自动 | `test_all_failed_message_lists_connections_without_keys` | 通过 |
| B7 | 旧请求（只有 `ai`）兼容 | 自动 | `test_old_request_with_only_ai`、`test_plain_config_still_accepted` | 通过 |

## 暂停与恢复、切换记录

| # | 验收点（规范场景） | 方式 | 证据 | 结论 |
|---|---|---|---|---|
| C1 | 设置页显示暂停原因与恢复时间 | 页面 | 顶部「AI 可用 · 1 个连接方式暂停中 / Claude API Key 认证失败，新任务先用 OpenAI 平台订阅，14:55 后恢复按原顺序」；卡片「暂停中」与「认证失败，14:55 自动恢复；Key 可能已失效，建议更换」；右侧暂停说明 | 通过 |
| C2 | 更换 Key 后立即解除暂停 | 页面 + 数据 | 更换 Key 保存后卡片回到「已配置」、顶部「AI 可用」 | 通过 |
| C3 | 测试连接通过解除暂停 | 自动 | `pausedConnectionIsSkippedUntilSavedOrTested` | 通过（本机没有有效 Key，未页面实测） |
| C4 | 到期自动恢复 | 自动 | Redis TTL；很晚到的回调不再暂停见同一用例 | 通过 |
| C5 | 询盘详情显示「已自动切换」 | 页面 | IQ20261117002：「本次解析中途自动切换了 AI 连接方式 · Claude API Key 认证失败，改用 OpenAI 平台订阅完成」+「查看 AI 设置」 | 通过（实测中调整了中文与名称之间的空格） |
| C6 | 一个可用的都没有时提示暂不可用、不创建任务（全部不可用） | 页面 + 数据 | 顶部「AI 暂不可用」；提交询盘提示「AI 暂不可用，请在「业务设置 → AI 设置」中检查连接方式」，AI 任务数不变 | 通过 |
| C7 | 从没配置过时仍提示 AI 未配置 | 自动 | `nothingSavedAndStatusUnknownIsNotConfigured`、`FollowupAiContractTest` 等 | 通过（全量测试中发现并修复） |

## 实测中发现并已处理

- 平台租户什么都没配置、又查不到订阅登录状态时，曾把默认参与的订阅当作可用，导致「AI 未配置」的提示失效（全量测试 5 个类失败）。改为：从没保存过的订阅要确认登录后才算已配置、才使用。
- 订阅卡片拖到 API 组时会吸附到组内最近的卡片并调换顺序，改为按重叠判断，拖出本组松手不改变顺序。
- 后端暂停日志的恢复时间按 UTC 打印，改为北京时间；询盘提示里中文名称后多余的空格去掉。

## 清理

- 本机平台租户的 AI 配置行（占位 Key）已删除，Redis 暂停键已删除，恢复到实测前的空配置。
- 实测用的客户 Failover Test Trading 与询盘 IQ20261117001–003 已软删除。
- 重建的本机测试套餐、测试租户 TN1001 保留供后续使用，账号记在 `storage/test-accounts.md`（不入库）。
