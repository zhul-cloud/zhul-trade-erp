# Proposal

## Why

询价统计目前只能按月份看询盘单、按人员看型号、按周或按月看无货。采购负责人想按品牌、品类看询价情况（询了多少、有价多少、无货多少、回价要多久），并能按天、周、月、季度、年切换，现在只能导出询价记录手工汇总。

## What Changes

- 询价统计新增「品牌品类」页签：选统计周期（日、周、月、季度、年，默认按月）和其中一期，看这一期的询价型号数、有价、无货、待回价、有价率、平均回价时长，并与上一期比较。
- 下方按品牌或按一级品类列出同样的指标，表头可排序。
- 新增只读接口 `GET /api/v1/inquiry/sourcing-stats/brand-category`，权限与询价统计相同。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `inquiry/sourcing-stats`：新增「品牌品类统计」要求。

## Impact

- 后端：`inquiry/sourcing` 模块新增统计服务与接口，只读，不改表。
- 前端：`pages/inquiry/sourcing-stats` 新增页签。
