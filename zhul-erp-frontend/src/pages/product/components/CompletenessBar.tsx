import { Tooltip } from 'antd';
import React from 'react';
import { CHART_SERIES } from '@/theme/palette';
import { COMPLETENESS_GOOD, COMPLETENESS_MODULES } from '../constants';
import type { Completeness } from '../service';
import { useProductTheme } from '../theme';

/**
 * 档案完整度：一段对应一个模块。完整度是一个量，已补的段用同一种固定颜色（图表系列蓝，不随主题色变：
 * 琥珀、天蓝主题色在中性底上不到 3:1，红色主题又会被读成报错），达到 COMPLETENESS_GOOD 段改绿色表示「够用了」；
 * 没补的段中性灰（P14：原来蓝到青逐段渐变，颜色不表达任何含义）。
 * 列表里的小条悬停显示还缺哪些模块。
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
  // 图表系列的蓝、绿：浅深两种模式下对中性底都不低于 3:1（亮绿 greenMain 浅色只有 1.96，文字绿深色只有 2.49）
  const fill = good ? CHART_SERIES[4] : CHART_SERIES[0];
  const h = size === 'mini' ? 6 : 10;
  // 后端给的是模块标识（media、faq…），显示中文名
  const names = value.missing.map(
    (k) => COMPLETENESS_MODULES.find((m) => m.key === k)?.label ?? k,
  );
  const missing = names.length ? `，还缺：${names.join('、')}` : '';
  const bar = (
    <div
      role="img"
      aria-label={`档案完整度 ${value.done} / ${value.total} 项${missing}`}
      style={{ display: 'flex', alignItems: 'center', gap: 8 }}
    >
      <div style={{ display: 'flex', gap: size === 'mini' ? 2 : 4, flex: 1 }}>
        {value.modules.map((m) => (
          <span
            key={m.key}
            style={{
              flex: 1,
              height: h,
              borderRadius: h / 2,
              minWidth: size === 'mini' ? 4 : 12,
              background: m.done ? fill : palette.neutralSoft,
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
  // 档案页的大条下面已经逐项列出缺项，不再悬停提示
  return size === 'mini' && value.missing.length ? (
    <Tooltip title={`还缺：${names.join('、')}`}>{bar}</Tooltip>
  ) : (
    bar
  );
};
