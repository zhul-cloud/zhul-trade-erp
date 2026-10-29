# 品牌图标来源

- 上游：LobeHub Icons，https://github.com/lobehub/lobe-icons
- 固定版本：8b7bbc433d38437fa2b0cca58ae896917162b812（2026-10-29 获取）
- Claude：packages/static-svg/icons/claude-color.svg，保留原始 #D97757 颜色。
- OpenAI：packages/static-svg/icons/openai.svg，currentColor 在浅色原型中按原意解析为 #111111。
- 许可：MIT，见 LICENSE。品牌商标权归各权利人。
- assets 两个 SVG 是上游原文件。原型通过 op CLI import:svg 导入可编辑矢量；仅将 SVG 弧线用 svgpath 2.6.0 转为等形三次曲线、规范尺寸为 24px，以兼容当前 OpenPencil Rust 导入器。没有手绘替代图形。
