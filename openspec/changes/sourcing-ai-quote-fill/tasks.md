## 1. 原型

- [x] 1.1 用 OpenPencil 画粘贴报价弹窗（Q6、Q7）：AI 识别中、识别结果预览（含请核对、其他任务里的型号）；AI 未开启时弹窗与现在一样，不另画
- [x] 1.2 原型经用户确认（2026-11-13）

## 2. AI 技能

- [x] 2.1 `scripts/ai-orchestrator/skills/sourcing-quote-parse/SKILL.md`：输入输出 JSON 约定，覆盖价格在前在后、一行多个型号、含税写法、货期区间、整段通用说明（编排服务 `call_quote_parse` / `sanitize_quote_parse`，`test_server.py` 的 `QuoteParseTest`）
- [ ] 2.2 用 10 段真实报价样本（脱敏）做识别测试，记录准确率（待办：本地编排服务的订阅未登录、没有可用的 API Key，调不到真实模型；需要先登录订阅或提供测试 Key）

## 3. 后端

- [x] 3.1 发起识别接口（任务、渠道、店铺、原文），写 `ai_task`；读取结果接口；同一人同时只跑一个（`QuoteAiService`；另加取消接口与「是否开启 AI」接口；发起新识别时作废本人未结束的识别；超时、取消以外的失败原因只写日志，提示「AI 识别失败」）
- [x] 3.2 结果处理器：校验明细 id、单价、字典值，换算不含税价（两位小数，HALF_UP），组装成现有预览结构；回调按任务所属租户执行（`TenantContext.runAs`），补租户契约测试（`QuoteParseResultHandler` 按租户字典清洗；明细 id 在读取结果时按任务校验，因任务输入不落库；`QuoteAiFillContractTest` 走真实回调接口）
- [x] 3.3 单元测试：明细 id 不属于任务、负价、非字典值、含税换算、空结果（`QuoteParseResultHandlerTest`、`QuoteAiFillContractTest`）

## 4. 前端

- [x] 4.1 粘贴报价弹窗：AI 识别 / 按规则识别、识别中与取消、失败退回规则识别
- [x] 4.2 预览加「AI 识别」「请核对」，底部其他任务的型号与跳转后自动带原文
- [x] 4.3 组件测试；`npm run lint`、`npx antd lint ./src`、`npm test`、`npm run build` 通过（`PasteQuoteModal.test.ts`、`pasteAi.test.ts`）

## 5. 收尾

- [x] 5.1 本地走查，对照原型（AI 结果用手动回调模拟，原因同 2.2；修正：表格撑宽弹窗、AI 模式隐藏规则识别专用的说明、失败提示不露技术信息）
- [x] 5.2 使用手册更新「粘贴报价」（`docs/05-使用手册/03-业务域/05-询价管理/询价分配延后与回价录入-使用手册-V1.0.md`）
