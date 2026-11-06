import React, { useLayoutEffect, useRef, useState } from 'react';

/**
 * 单行显示，放不下时逐级缩小字号（不低于 min），宽度变大后恢复。
 * 用于卡片里的大号金额、数字：低分辨率下卡片变窄时不溢出，也不截断数字（audit UI-041）。
 */
const FitText: React.FC<{
  children: React.ReactNode;
  size: number;
  min?: number;
  style?: React.CSSProperties;
}> = ({ children, size, min = 16, style }) => {
  const ref = useRef<HTMLDivElement>(null);
  const [font, setFont] = useState(size);
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return undefined;
    const shrink = () => {
      let next = size;
      el.style.fontSize = `${next}px`;
      while (next > min && el.scrollWidth > el.clientWidth + 1) {
        next -= 1;
        el.style.fontSize = `${next}px`;
      }
      setFont(next);
    };
    shrink();
    const observer = new ResizeObserver(shrink);
    observer.observe(el);
    return () => observer.disconnect();
  }, [size, min, children]);
  return (
    <div
      ref={ref}
      style={{
        ...style,
        fontSize: font,
        whiteSpace: 'nowrap',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
      }}
    >
      {children}
    </div>
  );
};

export default FitText;
