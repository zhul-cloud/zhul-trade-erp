## Context

AI 编排服务（`scripts/ai-orchestrator`）按技能 ID 路由任务，询盘解析用 `skills/inquiry-parser/SKILL.md` 作系统提示词，按 JSON Schema 输出。API 方式下 Claude 与 OpenAI 都能联网搜索，调用完成时只在日志打印 token 与搜索次数，回调里没有。后端 `ai_task` 只存输出与状态。「烛龙客户背调」技能原文在 zhul-skills 仓库，是给桌面端写的，最后一步用 Python 生成 PDF 放到桌面。后端已有 LibreOffice 转 PDF（`DocumentConverter`），前端已有 `@ant-design/x-markdown`。

## Goals / Non-Goals

**Goals**
- 背调一键发起、结果存档、能导出；能补进档案的部分一键采纳。
- 每次调用的 token 看得见，便于判断要不要改用上传。

**Non-Goals**
- 不折算金额，AI 设置里没有单价。
- 不做沟通记录提取和缺项提问清单。
- 不做批量背调。

## Decisions

1. **系统版技能**：编排服务新增技能 `customer-background`。
   - 提示词由「烛龙客户背调」改写：保留核心结论、真实性核查、公司背景、跟进价值、最终结论与评分标准；删掉模块五（生成 PDF）和桌面路径；输出要求从「中文报告 + PDF」改为 JSON：
     - `conclusion`：priority 1–4、score 1–5、mainConflict、firstAction、identity、supply、keyFinding、obstacle；
     - `reportMarkdown`；
     - `suggestions[]`：module、field、value、evidence、sourceUrl，联系人建议另带 contact（姓名、邮箱等定位信息）。
   - field 只能取 `customer-profile` 定义的画像字段键，选项型字段必须是选项中文名之一。
   - 原文继续在 zhul-skills 维护；改动背调逻辑时，两边一起改，在系统版文件头注明来源与同步日期。
2. **两种模式共用一个技能**：
   - `mode=research`：联网，输入客户资料与询盘；
   - `mode=extract`：不联网，输入上传的报告文字，只输出结论与字段建议，`reportMarkdown` 原样返回上传的文字。
3. **用量**：编排服务的 `call_api` 返回结果时附带 usage（model、inputTokens、outputTokens、webSearches），回调体增加 `usage` 字段，旧后端忽略。`ai_task` 加 `model`、`input_tokens`、`output_tokens`、`web_searches` 列，回调时写入。所有技能都记，询盘解析也能看到消耗。
4. **报告表** `customer_background_report`：
   - 编号 BG+日期+序号，走编号规则；
   - customer_id、来源（1-AI 背调、2-上传）、状态（1-背调中、2-已完成、3-失败）、失败原因；
   - 核心结论各列：优先级、评分便于列表显示与排序；
   - 报告正文（mediumtext）、字段建议 JSON（每条带采纳状态：待处理、已采纳、已忽略，以及采纳人和时间）；
   - 上传的 PDF（存储键）、ai_task_id、用量快照、发起人、软删除。
5. **采纳**：服务端按 `customer-profile` 的校验规则逐条写入，并记来源为这份报告。
   - 选项值按中文名匹配字典，匹配不上的标「无法采纳：选项不存在」；
   - 联系人建议按邮箱或 WhatsApp 匹配已有联系人，匹配上就更新，否则新建（姓名必填，没有姓名的不能采纳）；
   - 当前值为空的建议默认勾选，有值的默认不勾。
6. **输入组装**：
   - 客户名称、国家、官网、已有画像字段、联系人姓名与邮箱域名；
   - 系统推导：成交额区间、常用品牌；
   - 最近 3 张询盘的原文，每张截取前 3000 字。
   
   不传联系人的电话和 WhatsApp。
7. **PDF 文字提取**：上传的 PDF 用 PDFBox 抽取文字，超过 60000 字截断并提示。抽不出文字的（扫描件）提示「这个 PDF 没有可读文字，请粘贴报告文字」。
8. **导出 PDF**：报告正文 Markdown 用 commonmark 转 HTML，套上报告模板（标题、客户、编号、时间、核心结论色块、正文、字段采纳表），交给现有 `DocumentConverter` 用 LibreOffice 转 PDF。和 PI、报价单共用转换队列。
9. **档案补全向导**：
   - 字段到四类来源（系统、背调、自己判断、问客户）的归类写成后端常量，前后端共用一份接口返回，不在前端写死；
   - 问法存在系统设置 `customer.ask.{字段键}`，平台模板加租户覆盖；
   - 「已经问了，等回复」记在字段来源表上，加 `asked_by`、`asked_at` 两列；
   - 左侧队列的排序在后端组装：经营等级、综合分数、潜力、最近活动；
   - 「今天已补完」按完整度从不足 5 变为 5 的变更日志计数。
10. **24 小时提醒**：只提醒不拦截，弹窗写明上次背调的时间与发起人。

## Risks / Trade-offs

- **AI 把同名公司搞混**：字段不自动写入，只给建议；核心结论里的身份描述放在报告最上面，业务员一眼能看出是不是同一家。
- **token 消耗**：联网背调比询盘解析贵得多。先不设上限，每份报告记录本次用量，总量在服务商后台看，看一段时间后再定是否限额。
- **两份技能可能不一致**：在系统版头部注明同步来源，改技能时要两边一起改，写进使用手册的维护说明。

## Migration Plan

一个迁移：
- 新建报告表；
- `ai_task` 加用量列（存量为空，显示「无记录」）；
- 按钮权限与编号规则。

回退：删表、删列、删权限。编排服务同一次发布更新，旧编排服务不带 usage，后端照常处理。
