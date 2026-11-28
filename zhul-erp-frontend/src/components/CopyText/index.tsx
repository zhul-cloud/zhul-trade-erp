import { App } from 'antd';
import { Copy } from 'lucide-react';
import React from 'react';

/**
 * 悬停复制（公共组件，ui-redesign D27，规范「组件标准 · 表格」）：单号、编号、型号悬停（或键盘聚焦）时右侧出现复制图标，
 * 点击复制原文并提示「已复制」，不触发整行点击。用法：`<CopyText text={r.soNo}><button className="zhul-link">…</button></CopyText>`。
 * 样式（平时隐藏、悬停与聚焦时出现）在 AppTheme 全局样式 `.zhul-copy`。
 */
export const CopyText: React.FC<{
  /** 要复制的原文；为空时只显示内容，不出现复制 */
  text?: string | null;
  children: React.ReactNode;
}> = ({ text, children }) => {
  const { message } = App.useApp();
  if (!text) return <>{children}</>;
  const copy = (e: React.MouseEvent) => {
    e.stopPropagation();
    const done = () => message.success(`已复制 ${text}`);
    const fail = () => message.error('复制失败，请选中文字手动复制');
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(text).then(done, fail);
    } else {
      fail();
    }
  };
  return (
    <span className="zhul-copy">
      {children}
      <button
        type="button"
        className="zhul-copy-btn"
        aria-label={`复制 ${text}`}
        title="复制"
        onClick={copy}
      >
        <Copy size={14} aria-hidden />
      </button>
    </span>
  );
};

export default CopyText;
