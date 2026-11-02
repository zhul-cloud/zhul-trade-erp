## ADDED Requirements

### Requirement: 销售统计不计售后补发
销售统计的销售额、订单数、客户数、品牌与型号排行 SHALL 不计入来源为「售后补发」的订单。

#### Scenario: 补发不算销售
- **WHEN** 11 月有普通订单 3 张、售后补发订单 1 张
- **THEN** 11 月订单数为 3
