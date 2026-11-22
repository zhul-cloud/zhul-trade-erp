# BUS-091 询盘 AI 解析的品牌按品牌库别名统一

## 结论 / 下一步

已修复，随 feat/small-batch-1122 发版。AI 解析结果读出时先把品牌按品牌库及别名换成正式名，再合并同一型号；品牌库没收录的写法（如 Schneider Electric SE、Schneider(Telemecanique)）仍保留原文，需要在「品牌管理」里补别名。下一步：上线后在生产看一张新解析的询盘确认。

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | 烛龙开源 · Claude Code |
| 关联 ID / 原专题 | BUS-091；用户 2026-11-22 反馈「AI 解析时同一品牌有 Schneider 和 Schneider Electric 两个」 |
| 目标与影响范围 | 询盘 AI 解析的确认页、待确认询盘的型号摘要与统计；确认入库时本来就按品牌库存正式名，不受影响 |
| 起点 commit / 工作分支 | 4d2e1486 / feat/small-batch-1122 |
| OpenSpec | 缺陷修复，行为与现有 openspec brand-unify「识别到品牌时用正式品牌名」一致，不另开变更 |
| PR / 目标分支 | 待创建 → develop |
| 合并证据 | 未合并 |
| 目标版本 / 环境 | v1.46.0 |

## 实施与验证结果

根因：`CustomerInquiryServiceImpl.parseOutput` 直接用 AI 给的品牌文字生成确认页行与型号摘要，没有经过 `BrandResolver`；同一品牌 AI 有时写「Schneider」、有时写「Schneider Electric」，确认页就出现两个品牌，同一型号也因品牌键不同没合并。修复：读出 AI 结果后先用 `PriceKeys.brandText` 把每组品牌换成正式名（识别不到保留原文），再交给 `ParsedItemMerger` 合并。

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| `InquiryIntakeContractTest.aiParse_brandAliasesUnifiedToFormalName_sameModelMerged` | 本机隔离库 | 别名与大小写不同的写法统一为正式名，同一型号合并 | 符合 | 通过 |

未覆盖：品牌库里没有收录的写法不会被合并，需要补别名。历史已入库的 `inquiry_item.brand` 有 490 条大写「SCHNEIDER」（brand_id 已正确关联），只影响显示文字，未改数据。

## 合并、部署与逐 ID 验收

待合并。
