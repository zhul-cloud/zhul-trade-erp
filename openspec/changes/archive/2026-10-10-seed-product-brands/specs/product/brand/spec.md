## ADDED Requirements

### Requirement: 品牌等级
系统 SHALL 为品牌提供等级：核心、常做、普通，默认普通；只有平台账号可以修改。品牌列表显示等级标签（普通不显示）并可按等级筛选；品牌下拉选项按核心、常做、普通的顺序排列，同一等级内按名称排序。

#### Scenario: 核心品牌排在下拉最前
- **WHEN** 业务员在新建商品时打开品牌下拉
- **THEN** 前 8 个是核心品牌（Allen-Bradley、ABB、Delta、Mitsubishi Electric、OMRON、Schneider Electric、Siemens、Yaskawa），之后是常做品牌，最后是普通品牌

#### Scenario: 按等级筛选
- **WHEN** 平台账号在品牌列表选择「核心」
- **THEN** 只显示 8 个核心品牌

#### Scenario: 修改等级
- **WHEN** 平台账号把品牌 Festo 的等级改为「常做」
- **THEN** 品牌列表显示「常做」标签，下拉里 Festo 排到常做品牌中

## MODIFIED Requirements

### Requirement: 品牌携带品牌简介
系统 SHALL 允许为品牌维护中文简介和英文简介，都是选填，各不超过 500 个字符；中文简介用于系统内展示，英文简介用于独立站品牌页等对外场景；两者都随品牌读取接口返回。

#### Scenario: 填写品牌简介
- **WHEN** 平台账号为品牌 `Siemens` 保存中文简介「工控龙头；SIMATIC PLC/HMI、SINAMICS 驱动」和英文简介「Automation leader from Munich」
- **THEN** 系统保存两段简介，品牌详情和品牌列表读取时都返回它们，品牌列表显示中文简介

#### Scenario: 简介超长被拒绝
- **WHEN** 用户提交超过 500 个字符的中文或英文简介
- **THEN** 系统拒绝保存，提示简介长度超限

#### Scenario: 不填简介
- **WHEN** 用户创建品牌时不填写简介
- **THEN** 系统正常创建，两段简介都为空

### Requirement: 初始品牌与系列
系统 SHALL 在迁移时按 2026-10-10 定稿的品牌名单写入 83 个品牌（完整清单见本变更 design.md）：英文名为正式名称，中文名与常见写法作为别名，原产地取国家清单的英文名，并写入品牌等级与中英文简介。环境里已有的品牌按名称或别名识别为名单中的同一品牌时，保留 ID 并改为名单的正式写法与内容，旧名称留作别名；Vacon 并入 Danfoss（引用与别名转到 Danfoss，与 Danfoss 已有数据冲突的行不移动，此时 Vacon 停用保留）；不在名单里的其他品牌，没有引用的软删除，仍有引用的停用。此前按《公司主营产品》表写入的系列保留不变。迁移可重复执行，结果一致。

#### Scenario: 中文名找到种子品牌
- **WHEN** 迁移完成后业务员在供应商主营产品中输入「三菱」
- **THEN** 系统匹配到品牌 Mitsubishi Electric

#### Scenario: 已有品牌以别名存在
- **GIVEN** 环境里已有品牌「ABB Group」，下面挂着系列和询盘型号
- **WHEN** 执行迁移
- **THEN** 该品牌改名为「ABB」，ID 不变，系列与询盘型号仍挂在它下面，「ABB Group」成为别名

#### Scenario: Vacon 并入 Danfoss
- **GIVEN** 品牌 Vacon 下有系列 NXS
- **WHEN** 执行迁移
- **THEN** 系列 NXS 挂到 Danfoss 下，询盘里写「Vacon」或「伟肯」识别为 Danfoss，Vacon 被软删除

#### Scenario: 不在名单里的品牌
- **WHEN** 执行迁移时环境里有不在名单里的品牌 Hitech（有系列）和 HIDER（没有引用）
- **THEN** Hitech 停用保留，HIDER 被软删除
