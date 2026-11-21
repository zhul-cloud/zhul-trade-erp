import { Tooltip } from 'antd';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';

/**
 * 顶栏、侧栏的图标按钮：只有图标，悬停显示名称；pressed 为弹出层打开时的按下态。
 * 圆形 36，图标 20（外壳原型 F1–F1d）。
 */
export const IconButton = React.forwardRef<
  HTMLButtonElement,
  {
    label: string;
    icon: React.ReactNode;
    pressed?: boolean;
    size?: number;
    tip?: boolean;
  } & Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'children'>
>(({ label, icon, pressed, size = 36, tip = true, style, ...rest }, ref) => {
  const { palette } = useAppTheme();
  const [hover, setHover] = React.useState(false);
  const btn = (
    <button
      ref={ref}
      type="button"
      aria-label={label}
      aria-expanded={pressed}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      {...rest}
      style={{
        width: size,
        height: size,
        flex: 'none',
        display: 'grid',
        placeItems: 'center',
        padding: 0,
        border: 0,
        borderRadius: '50%',
        background: pressed || hover ? palette.navOn : 'transparent',
        color: palette.sub,
        cursor: 'pointer',
        transition: 'background 0.2s',
        ...style,
      }}
    >
      {icon}
    </button>
  );
  return tip ? (
    <Tooltip title={label} placement="bottom">
      {btn}
    </Tooltip>
  ) : (
    btn
  );
});
IconButton.displayName = 'IconButton';
