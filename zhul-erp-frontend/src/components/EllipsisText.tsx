import { Tooltip } from 'antd';
import React, { useRef, useState } from 'react';

/**
 * 单行显示，放不下时以省略号结尾；只有真的被截断时，鼠标移上去才用提示显示完整内容。
 * 用于表格单元格、卡片里的长文本，避免低分辨率下换行把行撑高（audit UI-041）。
 */
const EllipsisText: React.FC<{
  text?: React.ReactNode;
  /** 提示里显示的完整内容，默认与 text 相同 */
  tip?: React.ReactNode;
  style?: React.CSSProperties;
}> = ({ text, tip, style }) => {
  const ref = useRef<HTMLSpanElement>(null);
  const [open, setOpen] = useState(false);
  if (text === undefined || text === null || text === '') return <>—</>;
  return (
    <Tooltip
      title={tip ?? text}
      placement="topLeft"
      open={open}
      // 每次要打开时再量一次：没被截断就不弹
      onOpenChange={(next) => {
        const el = ref.current;
        setOpen(next && !!el && el.scrollWidth > el.clientWidth);
      }}
    >
      <span
        ref={ref}
        style={{
          display: 'block',
          maxWidth: '100%',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          ...style,
        }}
      >
        {text}
      </span>
    </Tooltip>
  );
};

export default EllipsisText;

/** 纯文本列：单行省略，截断时悬停显示全文 */
export const ellipsisRender = (v?: React.ReactNode) => (
  <EllipsisText text={v} />
);
