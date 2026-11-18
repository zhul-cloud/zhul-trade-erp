# Spec Delta

## ADDED Requirements

### Requirement: 规格参数一行显示

商品档案「规格参数」查看时 SHALL 每个格子单行显示：规格名称、规格值、条件/范围、来源超长时用省略号收起，鼠标悬停显示全文；没有内容时显示「—」。编辑时不受影响。

#### Scenario: 来源很长

- **WHEN** 某条规格的来源为「TeSys D 产品手册 p.12；IEC 60947-4-1 型式试验报告摘要；官网下载中心 Datasheet LC2D18B7C」
- **THEN** 该行仍为一行高，来源显示为省略号收起的一行，悬停看到全文
