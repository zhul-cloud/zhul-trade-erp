import { Tooltip } from 'antd';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';

/** 刻度取整：1、2、2.5、5 乘 10 的幂 */
export const niceMax = (v: number) => {
  if (v <= 0) return 1;
  const p = 10 ** Math.floor(Math.log10(v));
  const n = v / p;
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10) * p;
};

const PLOT_TOP = 18;

/**
 * 工作台的趋势柱图（老板主角卡、报价转化、近 30 天回价共用，UI-108）：虚线网格与纵轴刻度，
 * 往期用图表浅色、只亮一根（默认最后一根，D31）并在柱顶写数；0 画 2px 短柱（表示 0，不是缺数据）。
 * 全为 0 时只留基线，柱区写 emptyText。高度随父容器（至少 minHeight），父级用 flex 撑满卡片。
 */
const TrendBars: React.FC<{
  labels: string[];
  values: number[];
  /** 刻度与柱顶数值的写法 */
  short: (v: number) => string;
  /** 悬停提示的完整写法 */
  full: (v: number) => string;
  /** 读屏：图的名称，如「近 12 个月成交额」 */
  name: string;
  emptyText: string;
  /** 横轴只写首尾（柱子多时，如 30 天） */
  ends?: boolean;
  /** 亮哪一根，默认最后一根 */
  lit?: number;
  minHeight?: number;
}> = ({
  labels,
  values,
  short,
  full,
  name,
  emptyText,
  ends,
  lit,
  minHeight = 120,
}) => {
  const { palette } = useAppTheme();
  const top = niceMax(Math.max(0, ...values));
  const empty = values.length === 0 || values.every((v) => v <= 0);
  const on = lit ?? values.length - 1;
  const many = values.length > 12;
  const last = labels.length - 1;
  return (
    <div
      role="img"
      aria-label={`${name}：${labels
        .map((l, i) => `${l} ${short(values[i] ?? 0)}`)
        .join('，')}`}
      style={{ flex: 1, minHeight, display: 'flex' }}
    >
      {/* 纵轴刻度：文字中线对准网格线 */}
      <div
        aria-hidden
        style={{ width: 40, flex: 'none', position: 'relative' }}
      >
        {(empty
          ? [['0', 'calc(100% - 24px)']]
          : [
              [short(top), `${PLOT_TOP}px`],
              [
                short(top / 2),
                `calc(${PLOT_TOP}px + (100% - ${PLOT_TOP + 24}px) / 2)`,
              ],
              ['0', 'calc(100% - 24px)'],
            ]
        ).map(([t, y]) => (
          <span
            key={y}
            className="num"
            style={{
              position: 'absolute',
              top: y,
              left: 0,
              transform: 'translateY(-50%)',
              fontSize: 12,
              color: palette.mute,
            }}
          >
            {t}
          </span>
        ))}
      </div>
      <div
        aria-hidden
        style={{
          flex: 1,
          minWidth: 0,
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <div style={{ flex: 1, position: 'relative' }}>
          {empty && (
            <span
              style={{
                position: 'absolute',
                inset: 0,
                display: 'grid',
                placeItems: 'center',
                fontSize: 13,
                color: palette.mute,
              }}
            >
              {emptyText}
            </span>
          )}
          {(empty
            ? []
            : [
                `${PLOT_TOP}px`,
                `calc(${PLOT_TOP}px + (100% - ${PLOT_TOP}px) / 2)`,
              ]
          ).map((y) => (
            <span
              key={y}
              style={{
                position: 'absolute',
                left: 0,
                right: 0,
                top: y,
                borderTop: `1px dashed ${palette.hairline}`,
              }}
            />
          ))}
          <span
            style={{
              position: 'absolute',
              left: 0,
              right: 0,
              bottom: 0,
              borderTop: `1px solid ${palette.hairline}`,
            }}
          />
          <div
            style={{
              position: 'absolute',
              left: 0,
              right: 0,
              top: PLOT_TOP,
              bottom: 0,
              display: 'flex',
            }}
          >
            {values.map((v, i) => {
              const hot = i === on && v > 0;
              const pct = (v / top) * 100;
              return (
                <Tooltip key={labels[i]} title={`${labels[i]} · ${full(v)}`}>
                  <span
                    style={{
                      flex: 1,
                      minWidth: 0,
                      position: 'relative',
                      display: 'flex',
                      justifyContent: 'center',
                      alignItems: 'flex-end',
                    }}
                  >
                    <span
                      style={{
                        width: many ? '60%' : '50%',
                        height: v > 0 ? `${pct}%` : 2,
                        borderRadius:
                          v > 0 ? (many ? '2px 2px 0 0' : '4px 4px 0 0') : 0,
                        background: hot ? palette.chart : palette.chartSoft,
                      }}
                    />
                    {hot && (
                      <span
                        className="num"
                        style={{
                          position: 'absolute',
                          bottom: `calc(${pct}% + 2px)`,
                          fontSize: 12,
                          fontWeight: 600,
                          color: palette.ink,
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {short(v)}
                      </span>
                    )}
                  </span>
                </Tooltip>
              );
            })}
          </div>
        </div>
        <div style={{ display: 'flex', height: 24, alignItems: 'center' }}>
          {ends ? (
            <>
              <span style={{ flex: 1, fontSize: 12, color: palette.mute }}>
                {labels[0]}
              </span>
              <span
                style={{ fontSize: 12, fontWeight: 600, color: palette.ink }}
              >
                {labels[last]}
              </span>
            </>
          ) : (
            labels.map((l, i) => (
              <span
                key={l}
                style={{
                  flex: 1,
                  minWidth: 0,
                  textAlign: 'center',
                  fontSize: 12,
                  fontWeight: i === last ? 600 : 400,
                  color: i === last ? palette.ink : palette.mute,
                }}
              >
                {l}
              </span>
            ))
          )}
        </div>
      </div>
    </div>
  );
};

export default TrendBars;
