## MODIFIED Requirements

### Requirement: 查找能力对所有租户只读开放
系统 SHALL 允许任意租户的登录用户使用选择器搜索和型号匹配，且不要求商品维护权限；租户的搜索与匹配只在本租户行业的商品中进行（见 product/industry）；这两项能力 SHALL 不修改任何数据。

#### Scenario: 租户用户搜索商品
- **WHEN** 任意租户的登录用户搜索商品
- **THEN** 系统返回平台共享商品库中属于本租户行业的匹配结果
