## Context

- 发起背调：`CustomerBackgroundService.research` → `BackgroundInput.research` 组装输入。输入里与结果质量关系最大的是官网（AI 直接访问并查域名注册时间）、联系人邮箱后缀（找到公司）和最近 3 张询盘原文（判断采购背景）。背调技能 `customer-background` 在信息不足时只给「待核实清单」。
- 前端：`customer/background/components.tsx` 的 `BackgroundActions`（「上传报告」「AI 背调」两个按钮与「24 小时内背调过」弹窗）在客户详情页头和档案补全向导里都在用；`BackgroundCard` 是客户详情右侧的「背调报告」卡片。
- 补充信息的入口已有：档案编辑弹窗 `ModuleEditModal`（页签 1 有公司类型、行业、官网、领英；页签 2 有主营产品），联系人弹窗 `ContactModal`；客户详情与档案补全向导都已挂着这两个弹窗。编辑权限为 `partner:customer:edit`，背调权限为 `partner:customer:background`。

## Goals / Non-Goals

**Goals:**
- 业务员发起背调前清楚看到缺什么、补哪里，补完马上看到变化。
- 检查规则只在后端一处，弹窗、卡片和以后的 AI Agent 调用都读同一个接口。

**Non-Goals:**
- 不拦截背调，不改背调输入与 AI 技能。
- 不验证官网能否打开、邮箱是否真实（只看有没有填、后缀是不是免费邮箱）。
- 不给询盘原文做「去补充」入口：询盘原文要在询盘管理里登记询盘，检查窗口只说明「在询盘管理登记询盘时贴上客户原文」。

## Decisions

1. **后端接口计算，前端只展示。** 新增 `GET /api/v1/masterdata/customers/{id}/background-reports/precheck`，挂在现有背调控制器下（菜单权限 `/customer/list`，数据范围与查看客户相同）。返回：
   ```
   { level: 'LOW' | 'MEDIUM' | 'HIGH',
     items: [{ key, label, ok, keyInfo, value, reason, fixTarget }] }
   ```
   `key` 为 `basic`、`website`、`companyEmail`、`inquiryText`、`linkedin`、`companyInfo`；`fixTarget` 为 `profile:1`、`profile:2`、`contact` 或空，前端据此打开对应弹窗与页签（公司信息缺的只有主营产品时为 `profile:2`，否则 `profile:1`）。备选：前端用已有的档案数据自己算——但询盘原文张数前端拿不到，规则也会在两处维护，不采用。
2. **取数与背调输入同口径。** 联系人用 `ProfileFacts.contacts`；询盘张数用与 `BackgroundInput.inquiries` 相同的条件（未删除、原文不为空）`COUNT(*)`，展示时最多显示 3。为避免两处条件漂移，把询盘查询条件提成 `BackgroundInput` 里的一个包内方法，两边共用。
3. **免费邮箱后缀清单作为常量。** 放在 `BackgroundConstants.FREE_EMAIL_DOMAINS`：gmail.com、googlemail.com、yahoo.com（含 yahoo.co.* / yahoo.com.*）、hotmail.com、outlook.com、live.com、msn.com、icloud.com、me.com、aol.com、protonmail.com、proton.me、gmx.com、gmx.de、mail.ru、yandex.ru、yandex.com、qq.com、163.com、126.com、sina.com、sohu.com、foxmail.com。按后缀精确匹配（`yahoo.` 前缀单独判断）。备选：放字典让租户维护——需求没有提，先不做。
4. **前端流程。** `BackgroundActions` 点「AI 背调」→ 请求检查接口 → 弹检查窗口；「先去补充」只关闭窗口；「直接背调 / 仍要背调」→ 走原来的 `research(customerId, false)`，遇到「24 小时内背调过」照旧弹原来的确认窗口。「去补充」打开 `ModuleEditModal` / `ContactModal`：这两个弹窗由所在页面持有，`BackgroundActions` 增加 `onFix(target)` 回调交给页面打开，页面在保存成功后通知刷新检查结果（检查结果放在一个共享 hook `useBackgroundPrecheck(customerId)` 里，卡片与弹窗共用，保存后 `reload`）。
5. **卡片显示。** `BackgroundCard` 增加检查结果区：没有报告时替换现在的空状态文字为完整清单；有报告时在标题下方显示一行充足度与缺项，点「展开」显示完整清单。没有编辑权限时不显示「去补充」。
6. **事务边界。** 只读查询，没有写操作，不涉及事务与跨模块联动。

## Risks / Trade-offs

- [免费邮箱清单不全，个别免费邮箱被当成企业邮箱] → 清单覆盖常见国际与国内服务商，遇到再补常量；影响只是提示偏乐观。
- [检查窗口多一步点击] → 信息充足时窗口同样显示，但按钮默认焦点在「直接背调」，一次回车即可发起；接受这一步，换来每次发起前都看到缺项。
- [多一次请求] → 检查接口只查客户、联系人与一次询盘计数，耗时可忽略。
