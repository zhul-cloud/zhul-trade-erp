/**
 * 正文字体：英文和数字用 Public Sans（作为前端依赖打包，见 src/global.tsx 的导入），
 * 中文落到系统字体。
 */
export const SANS_FONT =
  "'Public Sans Variable', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', sans-serif";

/** 等宽字体：型号、编码、代码片段。Windows 上 ui-monospace 不生效，补 Consolas */
export const MONO_FONT =
  'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace';
