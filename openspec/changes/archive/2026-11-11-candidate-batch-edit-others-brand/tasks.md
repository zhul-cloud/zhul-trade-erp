## 1. 原型

- [x] 1.1 用 OpenPencil 画三处：列表上的「批量修改」按钮与弹窗、列表品牌单元格「Others · AI 未识别：XXX」、审核弹窗里 Others 候选的品牌区（含「新建品牌」）；导出 PNG，`.op` 与 PNG 一起提交（`商品候选.op` C9、C10）
- [x] 1.2 用户确认原型（2026-11-11）

## 2. 数据

- [x] 2.1 迁移 V1.5.26：插入平台品牌 Others（非原厂），关联字典 industry 的全部行业；可重复执行，末尾注释回退 SQL
- [x] 2.2 `ProductConstants.OTHERS_BRAND`；按名称经 `BrandResolver` 取 Others 的 ID（回退 SQL 在 `sql/build/rollback/V1.5.26__brand_others_rollback.sql`）

## 3. 后端

- [x] 3.1 `CandidateAiFill.writeBrand`：匹配不上、AI 没给出品牌、AI 直接给 Others 都写 Others（`ai_brand = 2`，保存 AI 原名称）；提交时找不到 Others 拒绝并提示
- [x] 3.2 批量修改：`POST /api/v1/product/candidates/batch-edit`（审核权限），校验数量、品牌可见、品类启用；加行锁后只改仍待审核的候选，清对应 AI 标记；写操作日志（改前、改后）；返回已修改与跳过数；列表品牌筛选加「AI 未识别（Others）」
- [x] 3.3 审核通过：候选品牌为 Others 时允许「新建品牌」（默认 AI 原名称，原文成为别名）——后端现有新建品牌逻辑已支持，前端提供入口，测试覆盖
- [x] 3.4 测试：批量修改（只改品牌、只改品类、两项都空报错、超过 200 报错、已审核跳过、品牌不存在报错、品类停用报错、无权限、AI 标记清除）；AI 解析（匹配上、匹配不上记 Others、空品牌记 Others、AI 给 Others 记未识别、运行期间人工已选品牌不覆盖、缺少 Others 时拒绝）；Others 候选批量通过建档；Others 下同型号第二个改为并入；Others 候选审核改为新建品牌

## 4. 前端

- [x] 4.1 `service.ts` 加 `batchEdit`；`index.tsx` 加「批量修改」按钮与弹窗（品牌下拉、品类树，可清空，两项都空不可提交，超过 200 条提示）
- [x] 4.2 品牌单元格与审核弹窗按原型显示 Others · AI 未识别；Others 候选审核可新建品牌
- [x] 4.3 AI 解析品牌完成提示改为「记为 Others Z 个」，说明文字与「查看」链接改为按品牌 Others 筛选
- [x] 4.4 组件测试：批量修改弹窗两项都空时确定不可用，提交参数只带已填项

## 5. 收尾

- [x] 5.1 改写 `candidate-batch-ai-fill` 规格里「品牌匹配不上」的规则与场景，使之与本变更一致
- [ ] 5.2 本地走查：批量修改 3 个候选、跑一次 AI 解析品牌看 Others 结果、审核 Others 候选改为新建品牌（未做：本机开发库已执行售后分支的 V1.5.25，本分支后端无法启动；上线后在生产按此验收）
- [x] 5.3 `npm run lint`、`npm test`、后端相关测试通过；PR 写明迁移 V1.5.26 与回退方式、合并顺序注意事项
