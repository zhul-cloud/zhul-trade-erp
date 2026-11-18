## 1. 数据库

- [x] 1.1 迁移 V1.5.46：新表 `brand_text_ignored`；存量单据品牌写法按品牌名与别名统一（已发给客户的报价单、PI 不改，不更新 update_time）；空品牌按商品档案补齐
- [x] 1.2 `TenantTables` 把 `brand_text_ignored` 列入排除清单并写明理由（平台级，由平台账号校验）

## 2. 后端

- [x] 2.1 `BrandDocTexts`：汇总询盘、销售订单、采购订单的品牌写法；把指定写法统一成正式品牌名
- [x] 2.2 `BrandSuggest`：相近品牌与不是品牌的建议
- [x] 2.3 `PendingBrandService`：列表并入单据写法与建议；批量设为别名、新建品牌后统一单据；标为不是品牌
- [x] 2.4 接口 `POST /pending-brands/alias-batch`、`POST /pending-brands/ignore`
- [x] 2.5 询盘确认时认出的品牌存正式名（`PriceKeys.brandText`、`BrandResolver.nameOf`）

## 3. 前端

- [x] 3.1 「待确认品牌」页签：来源筛选、搜索、出现在、建议（可点击）、勾选批量设为别名与标为不是品牌
- [x] 3.2 设为别名弹窗预选建议品牌并列出会改的单据；不是品牌确认框列出涉及的单据

## 4. 测试

- [x] 4.1 单元测试：`PendingBrandServiceImplTest`（汇总、批量、标记）、`BrandSuggestTest`
- [x] 4.2 契约测试：`BrandUnifyContractTest`（单据统一与已发送不改、标为不是品牌、询盘存正式名）；`InquiryIntakeContractTest` 改为断言存正式品牌名
- [x] 4.3 前端测试：`PendingTab.test.tsx`
- [ ] 4.4 本机页面实测待确认品牌页签（BrowserOS neo）
