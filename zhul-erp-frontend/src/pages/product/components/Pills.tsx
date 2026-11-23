import React from 'react';
import { Pill } from '@/components/Pill';
import { LIFECYCLE_META } from '../constants';
import { useProductTheme } from '../theme';

/** 状态标签统一用公共组件（规范「状态标签」） */
export { Pill };

export const LifecyclePill: React.FC<{ value: number }> = ({ value }) => {
  const meta = LIFECYCLE_META[value] ?? LIFECYCLE_META[6];
  return <Pill tone={meta.tone}>{meta.text}</Pill>;
};

/** 品牌字母标：没有 Logo 时用品牌名首字母 + 品牌色 */
export const BrandMark: React.FC<{
  name?: string;
  color?: string;
  size?: number;
}> = ({ name = '?', color, size = 28 }) => {
  const { palette } = useProductTheme();
  return (
    <span
      aria-hidden="true"
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: size,
        height: size,
        borderRadius: size / 3,
        background: color || palette.primarySoft,
        color: color ? '#FFFFFF' : palette.link,
        fontWeight: 700,
        fontSize: Math.round(size * 0.46),
        flex: 'none',
      }}
    >
      {name.trim().charAt(0).toUpperCase()}
    </span>
  );
};
