import { CornerDownRight } from 'lucide-react';
import React, { useLayoutEffect, useState } from 'react';
import { createPortal } from 'react-dom';

/**
 * 「跳到主要内容」：按 Tab 时第一个出现在页面左上角（样式见全局 .zhul-skip）。
 * 放到 body 最前面，保证在侧栏之前获得焦点；点击时只把焦点移到目标，不改地址
 * （地址带上 #id 会被标签页当成一次跳转，重新挂载当前页面，没保存的内容会丢）。
 */
export const SkipLink: React.FC<{ target: string }> = ({ target }) => {
  const [host] = useState(() =>
    typeof document === 'undefined' ? null : document.createElement('div'),
  );
  useLayoutEffect(() => {
    if (!host) return undefined;
    document.body.prepend(host);
    return () => host.remove();
  }, [host]);
  if (!host) return null;
  return createPortal(
    <a
      className="zhul-skip"
      href={`#${target}`}
      onClick={(e) => {
        e.preventDefault();
        const el = document.getElementById(target);
        el?.focus();
        el?.scrollIntoView?.({ block: 'start' });
      }}
    >
      <CornerDownRight size={16} aria-hidden />
      跳到主要内容
    </a>,
    host,
  );
};
