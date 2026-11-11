## Why

客户有时把型号铭牌以 PDF 发来（扫描件、报价附表），新建询盘时铭牌区只收 JPG、PNG，业务员只能先截图再上传，或者放进询盘附件，采购就看不到这张铭牌。（2026-11-11 用户反馈）

## What Changes

- 录入询盘、追加型号、修改询盘的「铭牌图片」上传区也收 PDF，单个不超过 10MB，个数上限不变（每次 20 个）。
- PDF 铭牌在上传区、解析确认页、询盘详情与采购的询价任务里显示 PDF 图标，点开在弹窗里预览（沿用询盘附件的 PDF 预览）。
- AI 解析把 PDF 铭牌和铭牌照片一样编号交给模型，识别型号并对应到行（AI 编排服务已支持 PDF，不用改）。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities
- `inquiry/inquiry-intake`: 「铭牌图片」上传规则增加 PDF。

## Impact

- 后端：`InquiryConstants.NAMEPLATE_EXTS` 增加 pdf，提示文字改为「铭牌只支持 JPG、PNG、PDF」；契约测试更新。
- 前端：`inquiry/shared/NameplateUploader.tsx` 接受 PDF，未提交的 PDF 显示图标；其余显示与预览已按文件类型区分，不用改。
- 无数据库变更，无新配置项。
