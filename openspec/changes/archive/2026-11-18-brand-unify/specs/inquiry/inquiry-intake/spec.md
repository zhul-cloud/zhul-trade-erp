## ADDED Requirements

### Requirement: 确认询盘时品牌存正式名称
确认询盘（含追加询盘）时，型号行的品牌能按品牌名或别名认出（且品牌属于当前租户行业）的，系统 SHALL 存正式品牌名；认不出的存去掉首尾空格的原文。之后生成的报价单、PI、销售订单、采购需求、采购订单沿用该名称。

#### Scenario: 中文写法存正式名
- **WHEN** 业务员确认询盘，某行品牌填「施耐德」，它是 Schneider Electric 的别名
- **THEN** 该型号行品牌存为 Schneider Electric

#### Scenario: 大小写不同存正式名
- **WHEN** 某行品牌填「SIEMENS」
- **THEN** 该型号行品牌存为 Siemens

#### Scenario: 认不出的保留原文
- **WHEN** 某行品牌填「 Not In Library 」，品牌库里没有
- **THEN** 该型号行品牌存为「Not In Library」，并出现在待确认品牌里
