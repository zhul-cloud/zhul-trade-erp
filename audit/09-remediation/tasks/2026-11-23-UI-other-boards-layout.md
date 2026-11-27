# 其他岗位看板按行栅格的结构整理

## 结论 / 下一步

ui-redesign 第 3 批只按原型重做了老板、业务员看板。业务负责人、采购负责人、运营负责人、采购员、通用看板的组件还没在 `layout.ts` 登记栏数和高度档（按整行 / 半行、高度随内容），柱图仍是各自的写法。颜色与卡中卡已在第 3 批评审修正里统一（数字只有数字时自动按数字卡组、混排时用卡内凹格、柱图只亮一处）。下一步：给这些组件画原型、登记栏数与高度档，柱图抽成与主角卡共用的公共组件，在 ui-redesign 第 4、5 批之后处理。

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Ezio · Claude Code |
| 关联 ID / 原专题 | ui-redesign 9.1 第 3 批 UI 设计师评审第 6 条（`openspec/changes/ui-redesign/design-review.md`） |
| 目标与影响范围 | 前端 `zhul-erp-frontend/src/pages/dashboard/`（`layout.ts` 的 `LAYOUT`、`widgets/BlockWidget.tsx` 的柱图、`widgets/purchaser.tsx`）；不改后端口径 |
| 起点 commit / 工作分支 | feat/ui-redesign，第 3 批评审修正提交之后 |
| OpenSpec | ui-redesign（system/interface-style「工作台按行排列、同一行等高」） |
| PR / 目标分支 | 未创建 |
| 合并证据 | 未合并 |
| 目标版本 / 环境 | 随 ui-redesign 发版 |

## 实施与验证结果

未开始。
