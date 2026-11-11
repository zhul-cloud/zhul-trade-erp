## 1. 原型

- [x] 1.1 用 OpenPencil 画：待审核页签勾选后的批量操作条（两个 AI 按钮）、运行中进度、完成结果提示、列表中的「AI 建议」「AI 未识别」标记、审核弹窗里的 AI 标记；导出 PNG，用户确认后才开始 2–4

## 2. 后端

- [x] 2.1 迁移 `V1.5.20__candidate_ai_fill.sql`：`product_candidate` 加 `ai_category`、`ai_brand`、`ai_brand_name`；`CandidateVO` 带出
- [x] 2.2 提交接口 `POST /product/candidates/ai-fill`（mode、ids ≤ 200）：筛选需要处理的行、同用户运行中拒绝、未配置 AI 报错；查询接口 `GET /product/candidates/ai-fill/{taskId}`
- [x] 2.3 结果处理器 `candidate-classify`：品类校验存在且启用、品牌经 `BrandResolver` 匹配、条件更新、汇总结果；审核与手动修改清除标记
- [x] 2.4 契约测试：跳过规则、品类与品牌写入、别名匹配、未识别不建品牌、运行期间人工修改不覆盖、超过 200 条、同用户并发拒绝、权限

## 3. AI 编排服务

- [x] 3.1 `server.py` 新增 `candidate-classify`：两种模式的提示词与输出结构、50 条一批、合并回调；`test_server.py` 覆盖分批与结构校验

## 4. 前端

- [x] 4.1 批量操作条两个按钮、200 条限制、进度轮询与恢复、结果提示并刷新列表
- [x] 4.2 列表品类、品牌列与审核弹窗显示「AI 建议」「AI 未识别：xxx」
- [x] 4.3 `npm run lint`、浏览器走查
