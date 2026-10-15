## Why

AI 技能 `fouwell-product-content` 已更新输出格式：资料包新增 `## 平台参考价`（eBay 公开挂牌价），中国制造网改用工业产品页版式（四个加粗二级标题）。ERP 不认识这两处：平台参考价被跳过；中国制造网的四个区块被当成新章节跳过，询盘引导语也脱离了中国制造网。

## What Changes

- 资料包解析「## 平台参考价」，写入共享商品库的公开挂牌价，每次导入整份替换；商品详情「平台参考价」卡片列出这些挂牌价（只读，不影响人工维护的参考价）。
- 中国制造网的 `## **Technical Specifications**`、`## **Key Features and Benefits**`、`## **Product Details**`、`## **FAQ**` 归回中国制造网，分别写入产品属性、卖点、产品详情、FAQ；之后的询盘引导语也归中国制造网。卖点支持「加粗编号小标题 + 说明」，FAQ 支持「加粗编号问题 + 下一段答案」。
- 下载包模板：资料包加「平台参考价」；中国制造网改成工业产品页版式；卖点说明去掉旧的条数与长度限制。
- 《商品内容规范 v2》文档同步；技能里「ERP 是否解析须单独验证」的说明改为已支持。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `product/product`：新增「公开挂牌参考价」。
- `product/product-content`：商品内容规范 v2（平台参考价、中国制造网版式）。

## Impact

- **数据库**：新表 `product_listing_price`。
- **后端**：资料包与营销包解析、写入、预览；挂牌价读取接口；下载模板。
- **前端**：商品详情平台参考价卡片。
- **文档与技能**：规范 v2 文档；zhul-skills 的 `fouwell-product-content` 说明。
