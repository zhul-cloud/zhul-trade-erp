## Why

外贸业务流程的第一步是询盘（询盘 → 报价 → 客户确认 → 合同签订 → ...），但 zhul-erp 目前完全没有覆盖这一步，业务员只能在 Excel/微信/飞书里手工处理客户询盘。团队已经有一套跑在 Claude Code 环境下、经过多轮打磨的"烛龙询盘助手"（inquiry-parser / order-splitter / report-generator / feishu-pusher 四个 skill），能自动解析原始询盘、联网核验型号、按品牌+品类拆单、生成中英文询价话术，目前产出写入飞书多维表格。这次把这条能力迁移进 zhul-erp，让询盘管理成为系统里第一个 AI 深度参与的业务模块，同时为后续更多 skill（报价、合同等）接入打好通用地基。

## What Changes

- 新增客户 / 供应商最小可用主数据表，询盘录入页支持内联快速建档（无需先跳到独立的客户管理页）
- 新增"客户询盘"模块：业务员提交原始询盘内容（文本/Excel/图片），触发 AI 异步解析；解析完成后展示可编辑的拆单预览，业务员确认后才正式落库（AI 解析结果在确认前不写入正式业务表）
- 新增"询盘单"模块：按品牌+品类拆分出的可分配单元，拆单完成时仍是中性的客户诉求分组，尚未指定对接人；新增显式的"分配给采购员"步骤（`assignee_id` + "待分配"状态），分配后才成为该采购员对接供应商的工作项；支持独立于客户询盘手动创建（跳过 AI 的兜底路径），支持一张询盘单同时发给多个供应商并列对比报价
- 新增通用 AI 任务编排能力：Java 后端与独立部署的 AI 编排服务（复用现有 skill 文件，封装模型/Agent 平台差异，预留后续接入 DeepSeek 等其他平台的能力）之间通过 webhook 回调通信；`ai_task` 表设计为通用契约，供本次询盘解析与未来其他 skill 复用，不与询盘业务耦合
- 供应商报价字段遵循项目金额规范，记录原币金额 + 汇率 + 本位币金额三元组

## Capabilities

### New Capabilities
- `master-data/customer`: 客户主数据最小可用管理（列表、详情、内联快速创建），供询盘等业务模块引用
- `master-data/supplier`: 供应商主数据最小可用管理（列表、详情、内联快速创建），供采购询盘引用
- `inquiry/customer-inquiry`: 客户询盘录入、AI 解析触发、拆单预览与确认、状态流转（待解析→解析中→待确认→已确认→待报价→报价中→已报价→已成交/已取消）
- `inquiry/inquiry-order`: 询盘单管理（品牌+品类分组、分配给采购员、独立手动创建、多供应商关联、型号×供应商报价对比、询价话术与中英文邮件模版）
- `ai-platform/task-orchestration`: Java 后端与外部 AI 编排服务之间的通用异步任务契约（`ai_task` 表、webhook 回调、skill_id 路由），不含 AI 编排服务自身的实现

### Modified Capabilities
（无——本次不修改任何既有能力的行为）

## Impact

- **数据库**：新增 `customer`、`supplier`、`customer_inquiry`、`inquiry_order`、`inquiry_order_item`、`inquiry_order_supplier`、`inquiry_order_item_quote`、`ai_task` 共 8 张表
- **后端**：`zhul-erp-backend` 新增 `modules/masterdata`（客户/供应商）、`modules/inquiry`（客户询盘/询盘单）、`modules/aitask`（通用 AI 任务）三个模块；`SecurityConfig` 白名单新增 AI 服务回调的 webhook 端点（需独立鉴权机制，非用户态 JWT）
- **前端**：新增询盘中心相关页面（客户询盘列表/详情/拆单预览确认、询盘单列表/详情/分配采购员/报价对比、客户与供应商的内联快速创建组件）
- **外部依赖（新增）**：一个独立部署的 AI 编排服务（复用现有 `烛龙询盘助手` skill 文件），需要单独的仓库/部署单元、与 zhul-erp-backend 同网段部署、内部鉴权待定
- **不在本次范围内**：`report-generator`（PDF 解析报告）暂不接入；`inquiry_order` 与未来 `quotation`（报价单）/`sales_order`（销售订单）模块的衔接留待后续变更
