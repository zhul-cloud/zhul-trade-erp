## MODIFIED Requirements

### Requirement: 存储目录
文件在存储中的路径 SHALL 按「租户 / 业务环节 / 年月 / 随机文件名」组织，不使用用户上传的原文件名；按型号归档的文件按「品牌 / 型号」组织（品牌、型号转为小写，非字母数字字符替换为 `-`）。业务环节与目录对应：

| 目录 | 内容 |
| --- | --- |
| `tenant/{租户}/inquiry/{年月}/` | 客户询盘附件 |
| `tenant/{租户}/opportunity/{年月}/` | 商机附件 |
| `tenant/{租户}/sourcing/{年月}/` | 询价回价附件、询价导入文件 |
| `tenant/{租户}/supplier/{年月}/` | 供应商资质与合同 |
| `tenant/{租户}/customer/{年月}/` | 客户资料（上传的背调报告原件） |
| `tenant/{租户}/sales/{年月}/` | 销售订单附件（PI、合同、客户 PO 等） |
| `tenant/{租户}/purchase/{年月}/` | 采购合同、采购发票 |
| `tenant/{租户}/shipment/{年月}/` | 供应商发货图片与视频 |
| `tenant/{租户}/receipt/{年月}/` | 验收入库照片与视频 |
| `tenant/{租户}/media/{品牌}/{型号}/` | 实拍任务素材 |
| `tenant/{租户}/logistics/{年月}/` | 出运单面单 |
| `tenant/{租户}/finance/{年月}/` | 收款水单、付款与退款凭证、报销与费用凭证、其他收入凭证 |
| `tenant/{租户}/document/{年月}/` | 单据模板 |
| `tenant/{租户}/system/` | 本租户的站点外观图片（公开存储） |
| `shared/product/{品牌}/{型号}/` | 共享商品库的图片与视频（公开存储） |

新增业务环节时 SHALL 在此表登记新目录，不能把文件放进其他环节的目录。

#### Scenario: 实拍素材按型号归档
- **WHEN** 租户 1005 的拍摄人员为 Siemens 6ES7214-1AG40-0XB0 的实拍任务上传一张图片
- **THEN** 文件保存在 `tenant/1005/media/siemens/6es7214-1ag40-0xb0/` 下，文件名为随机名

#### Scenario: 水单放在财务目录
- **WHEN** 业务员在 2026 年 10 月为收款单上传水单截图
- **THEN** 文件保存在 `tenant/{租户}/finance/202610/` 下
