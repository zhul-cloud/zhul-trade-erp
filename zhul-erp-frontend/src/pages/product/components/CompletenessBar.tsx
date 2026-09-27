import React from 'react';
import { COMPLETENESS_GOOD } from '../constants';
import type { Completeness } from '../service';
import { useProductTheme } from '../theme';

/**
 * 档案完整度：一段对应一个模块，从蓝到青逐段过渡；已完成 8 段及以上用绿色。
 * 只对平台账号渲染——租户账号的接口不返回 completeness，这里没有数据就什么也不画。
 */
export const CompletenessBar: React.FC<{
  value?: Completeness;
  /** mini：列表里的小进度条；full：档案页和完成页的大进度条 */
  size?: 'mini' | 'full';
}> = ({ value, size = 'mini' }) => {
  const { palette } = useProductTheme();
  if (!value) return null;
  const good = value.done >= COMPLETENESS_GOOD;
  const segmentColor = (index: number) => {
    if (good) return palette.green;
    // 从 #3B82F6 到 #22D3EE 逐段过渡
    const t = value.total > 1 ? index / (value.total - 1) : 0;
    const from = [0x3b, 0x82, 0xf6];
    const to = [0x22, 0xd3, 0xee];
    const rgb = from.map((f, i) => Math.round(f + (to[i] - f) * t));
    return `rgb(${rgb.join(',')})`;
  };
  const h = size === 'mini' ? 6 : 10;
  return (
    <div
      role="img"
      aria-label={`档案完整度 ${value.done} / ${value.total} 项`}
      style={{ display: 'flex', alignItems: 'center', gap: 8 }}
    >
      <div style={{ display: 'flex', gap: size === 'mini' ? 2 : 4, flex: 1 }}>
        {value.modules.map((m, i) => (
          <span
            key={m.key}
            style={{
              flex: 1,
              height: h,
              borderRadius: h / 2,
              minWidth: size === 'mini' ? 4 : 12,
              background: m.done ? segmentColor(i) : palette.hairline,
            }}
          />
        ))}
      </div>
      <span
        className="num"
        style={{
          fontSize: size === 'mini' ? 12 : 14,
          color: good ? palette.green : palette.sub,
          fontWeight: 600,
          minWidth: size === 'mini' ? 34 : 44,
          textAlign: 'right',
        }}
      >
        {value.done}/{value.total}
      </span>
    </div>
  );
};
