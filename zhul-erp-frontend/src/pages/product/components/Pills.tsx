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
        background: color || palette.neutralSoft,
        color: color ? '#FFFFFF' : palette.sub,
        fontWeight: 600,
        // 按字阶取档，不出 15 这类越界字号（UI-117）
        fontSize: size >= 40 ? 16 : size >= 28 ? 14 : 12,
        flex: 'none',
      }}
    >
      {name.trim().charAt(0).toUpperCase()}
    </span>
  );
};
