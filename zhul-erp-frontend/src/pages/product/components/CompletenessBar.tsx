import { Tooltip } from 'antd';
import React from 'react';
import { mix } from '@/theme/palette';
import { COMPLETENESS_GOOD, COMPLETENESS_MODULES } from '../constants';
import type { Completeness } from '../service';
import { useProductTheme } from '../theme';

/**
 * 档案完整度：一段对应一个模块。完整度是一个量，只用主题色（图表色 palette.chart）一种颜色：
 * 补得越多颜色越实（浅 → 原色），不换成别的颜色（2026-11-24 用户确认；原来蓝到青渐变、达标变绿，颜色不表达含义）。
 * 没补的段用凹格色 inset。补得少时已补段对灰底只有约 1.5–2:1（各主题、两种模式都如此），靠右侧「done/total」数字
 * 和读屏文字补足（与 palette.chartPrimary 的约定一致）。
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
  // 完成度越高越实：60% 起，补齐时为原色（起点太浅时补得少的商品在灰底上看不出来）
  const ratio = value.total ? value.done / value.total : 0;
  const fill = mix(palette.chart, palette.card, 0.6 + 0.4 * ratio);
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
              // 未补的段用卡内最浅的凹格色，与已补段拉开
              background: m.done ? fill : palette.inset,
            }}
          />
        ))}
      </div>
      <span
        className="num"
        style={{
          fontSize: size === 'mini' ? 12 : 14,
          color: good ? palette.ink : palette.sub,
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
