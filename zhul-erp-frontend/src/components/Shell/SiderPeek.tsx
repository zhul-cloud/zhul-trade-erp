import { useModel } from '@umijs/max';
import React, { useEffect, useRef } from 'react';

/**
 * 收起侧栏的「浮出展开」（照参考站 PrimeDash）：收起成图标栏后，鼠标移上去或键盘聚焦到侧栏时，
 * 整个侧栏以浮层展开（带文字和分组），内容区不跟着移动；移开或焦点离开后收回。
 * 浮出期间 ProLayout 按展开渲染，占位宽度由样式固定为收起时的宽度（见 ShellStyle）。
 */
export const SiderPeek: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const { initialState, setInitialState } = useModel('@@initialState');
  const pinnedCollapsed = !!initialState?.collapsed;
  const peek = !!initialState?.siderPeek;
  const timer = useRef<number | undefined>(undefined);

  const set = (v: boolean) => {
    window.clearTimeout(timer.current);
    if (!pinnedCollapsed && v) return;
    setInitialState((s) => (s?.siderPeek === v ? s : { ...s, siderPeek: v }));
  };
  // 移开后稍等再收回，避免鼠标掠过边缘时来回闪
  const leave = () => {
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => set(false), 150);
  };

  useEffect(() => () => window.clearTimeout(timer.current), []);

  return (
    <div
      data-sider-peek={peek ? 'true' : undefined}
      onMouseEnter={() => set(true)}
      onMouseLeave={leave}
      onFocus={() => set(true)}
      onBlur={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node | null)) leave();
      }}
      style={{ display: 'contents' }}
    >
      {children}
    </div>
  );
};
