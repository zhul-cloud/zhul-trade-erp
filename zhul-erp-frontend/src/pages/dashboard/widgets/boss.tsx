import { history } from '@umijs/max';
import { Tooltip } from 'antd';
import dayjs from 'dayjs';
import {
  ChevronRight,
  FileClock,
  PackageX,
  TrendingDown,
  TriangleAlert,
  Truck,
} from 'lucide-react';
import React, { useMemo } from 'react';
import { CountryFlag } from '@/components/CountryText';
import { DeltaPill } from '@/components/MetricCard';
import { useCountries } from '@/pages/product/components/useCountries';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { MenuName } from '@/utils/menuName';
import type { Block } from './BlockWidget';
import { fmt } from './BlockWidget';
import { MoreLink, useWidgetData, WidgetCard } from './WidgetCard';

const go = (link?: string | null) => link && history.push(link);

/** 刻度取整：1、2、2.5、5 乘 10 的幂 */
const niceMax = (v: number) => {
  if (v <= 0) return 1;
  const p = 10 ** Math.floor(Math.log10(v));
  const n = v / p;
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10) * p;
};

/** 柱图上的短金额：一万以上写「41.3万」 */
const short = (v: number) =>
  v >= 10000
    ? `${Number((v / 10000).toFixed(1)).toLocaleString('zh-CN')}万`
    : Math.round(v).toLocaleString('zh-CN');

const PLOT_TOP = 18;

/**
 * 主角卡 · 本月成交额（工作台原型 H1、ui-redesign D28）：28/600 大数字与涨跌胶囊；近 12 个月柱图带虚线网格与刻度，
 * 只亮本月（往期浅灰，D31），本月柱顶写数值；底部一句结论。数据来自「近 12 个月成交额」组件。
 */
export const RevenueHeroWidget: React.FC = () => {
  const { palette } = useAppTheme();
  const { data, error, reload } = useWidgetData<{ blocks: Block[] }>(
    'revenue-trend',
  );
  const blocks = data?.blocks ?? [];
  const metric = blocks.find((b) => b.type === 'metrics')?.metrics?.[0];
  const barsBlock = blocks.find((b) => b.type === 'bars');
  const labels = barsBlock?.bars?.labels ?? [];
  const values = (barsBlock?.bars?.values ?? []).map(Number);
  const current = Number(metric?.value ?? values[values.length - 1] ?? 0);
  const top = niceMax(Math.max(0, ...values));
  // 近 12 个月都没有成交：只留 0 基线，柱区写一句说明（新公司第一眼看到的就是这里）
  const empty = values.length > 0 && values.every((v) => v <= 0);
  const [cnyPrefix, cnyValue] = [
    'CNY',
    formatAmount(current, 'CNY').replace(/^CNY\s*/, ''),
  ];
  return (
    <WidgetCard
      title="本月成交额"
      period="人民币"
      help="口径同经营统计"
      extra={
        <MoreLink onClick={() => go('/finance/operating-report')}>
          <MenuName path="/finance/operating-report" />
        </MoreLink>
      }
      loading={!data}
      error={error}
      onRetry={reload}
    >
      <div
        className="num"
        style={{
          display: 'flex',
          alignItems: 'baseline',
          gap: 6,
          color: palette.ink,
        }}
      >
        <span style={{ fontSize: 16, fontWeight: 600, color: palette.mute }}>
          {cnyPrefix}
        </span>
        <span style={{ fontSize: 28, fontWeight: 600, lineHeight: '35px' }}>
          {cnyValue}
        </span>
      </div>
      {metric?.delta !== null && metric?.delta !== undefined && (
        <div style={{ marginTop: 4 }}>
          <DeltaPill value={metric.delta} text={`${Math.abs(metric.delta)}%`} />
        </div>
      )}
      <div
        role="img"
        aria-label={`近 12 个月成交额：${labels
          .map((l, i) => `${l} ${short(values[i] ?? 0)}`)
          .join('，')}`}
        style={{ flex: 1, minHeight: 120, display: 'flex', marginTop: 16 }}
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
                近 12 个月还没有成交
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
                const last = i === values.length - 1;
                const pct = (v / top) * 100;
                return (
                  <Tooltip
                    key={labels[i]}
                    title={`${labels[i]} · ${fmt(v, 'money')}`}
                  >
                    <span
                      style={{
                        flex: 1,
                        position: 'relative',
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'flex-end',
                      }}
                    >
                      <span
                        style={{
                          width: '50%',
                          // 0 画 2px 中性短柱：表示 0，不是缺数据
                          height: v > 0 ? `${pct}%` : 2,
                          borderRadius: v > 0 ? '4px 4px 0 0' : 0,
                          background:
                            last && v > 0 ? palette.chart : palette.chartSoft,
                        }}
                      />
                      {last && v > 0 && (
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
            {labels.map((l, i) => (
              <span
                key={l}
                style={{
                  flex: 1,
                  minWidth: 0,
                  textAlign: 'center',
                  fontSize: 12,
                  fontWeight: i === labels.length - 1 ? 600 : 400,
                  color: i === labels.length - 1 ? palette.ink : palette.mute,
                }}
              >
                {l}
              </span>
            ))}
          </div>
        </div>
      </div>
      {barsBlock?.text && (
        <div
          className="num"
          style={{ marginTop: 8, fontSize: 13, color: palette.mute }}
        >
          {barsBlock.text}
        </div>
      )}
    </WidgetCard>
  );
};

const RISK_ICONS: Record<string, React.ElementType> = {
  'file-clock': FileClock,
  truck: Truck,
  warning: PackageX,
  down: TrendingDown,
};

/**
 * 风险提醒（工作台原型 H1b、ui-redesign D20）：固定 2×2 四格，位置不随数量变化。全为 0 时中性四格、灰色 0，
 * 标题旁绿点「全部正常」；有风险的格子为状态浅底、左侧 3px 色条、彩色数量与箭头。
 */
export const RiskAlertsWidget: React.FC = () => {
  const { palette, mode } = useAppTheme();
  const { data, error, reload } = useWidgetData<{ blocks: Block[] }>(
    'risk-alerts',
  );
  const todos = data?.blocks.find((b) => b.type === 'todos')?.todos ?? [];
  const at = useMemo(() => (data ? dayjs().format('HH:mm') : ''), [data]);
  const open = todos.filter((t) => t.count > 0).length;
  return (
    <WidgetCard
      title="风险提醒"
      note={
        data &&
        (open > 0 ? `${open} 项需要处理 · 截至 ${at}` : `全部正常 · 截至 ${at}`)
      }
      dot={data ? (open > 0 ? palette.red : palette.greenMain) : undefined}
      loading={!data}
      error={error}
      onRetry={reload}
    >
      <div
        style={{
          flex: 1,
          display: 'grid',
          gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
          gridAutoRows: 'minmax(44px, 1fr)',
          gap: 8,
        }}
      >
        {todos.map((t) => {
          const on = t.count > 0;
          const [color, soft] =
            t.tone === 'red'
              ? [palette.red, palette.redSoft]
              : [palette.orange, palette.orangeSoft];
          const Icon = RISK_ICONS[t.icon] ?? TriangleAlert;
          return (
            <button
              type="button"
              key={t.title}
              title={t.desc}
              onClick={() => go(t.link)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                minWidth: 0,
                padding: '0 12px 0 9px',
                border: 'none',
                borderLeft: `3px solid ${on ? color : 'transparent'}`,
                borderRadius: 8,
                // 没有风险时是卡内凹格：用 inset（比页面底浅），不用比页面还深的中性标签灰（D38）
                background: on && mode === 'light' ? soft : palette.inset,
                cursor: t.link ? 'pointer' : 'default',
                textAlign: 'left',
                font: 'inherit',
              }}
            >
              <Icon
                size={18}
                aria-hidden
                style={{ flex: 'none', color: on ? color : palette.mute }}
              />
              <span
                style={{
                  flex: 1,
                  minWidth: 0,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  fontSize: 13,
                  fontWeight: on ? 600 : 400,
                  color: on ? palette.ink : palette.sub,
                }}
              >
                {t.title}
              </span>
              <span
                className="num"
                style={{
                  fontSize: 20,
                  fontWeight: 600,
                  color: on ? color : palette.mute,
                }}
              >
                {t.count}
              </span>
              {on && (
                <ChevronRight
                  size={14}
                  aria-hidden
                  style={{ flex: 'none', color }}
                />
              )}
            </button>
          );
        })}
      </div>
    </WidgetCard>
  );
};

/** 排行的头像：客户为圆形国旗，业务员为名字首字 */
const Avatar: React.FC<{ name: string; code?: string }> = ({ name, code }) => {
  const { palette } = useAppTheme();
  return code ? (
    <CountryFlag code={code} round={28} />
  ) : (
    <span
      aria-hidden
      style={{
        width: 28,
        height: 28,
        flex: 'none',
        borderRadius: '50%',
        display: 'grid',
        placeItems: 'center',
        background: palette.neutralSoft,
        color: palette.mute,
        fontSize: 12,
        fontWeight: 600,
      }}
    >
      {name.slice(0, 1)}
    </span>
  );
};

/** 一列排行：头像 + 名称 + 4 高占比条（对第一名），右侧金额与第二个数 */
const RankList: React.FC<{ block: Block }> = ({ block }) => {
  const { palette } = useAppTheme();
  const { find } = useCountries();
  const t = block.table;
  if (!t) return null;
  const [who, what] = (block.title ?? '').split(' · ');
  const max = Math.max(1, ...t.rows.map((r) => Number(r[1] ?? 0)));
  const second = t.columns[2];
  return (
    <div style={{ minWidth: 0 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          height: 20,
          marginBottom: 8,
        }}
      >
        <span
          style={{
            flex: 1,
            fontSize: 13,
            fontWeight: 600,
            color: palette.sub,
          }}
        >
          {who}
        </span>
        <span style={{ fontSize: 12, color: palette.mute }}>
          {(what ?? '').replace(/^本月/, '')}
        </span>
      </div>
      {t.rows.length === 0 ? (
        <div style={{ padding: '12px 0', fontSize: 13, color: palette.mute }}>
          {t.empty ?? '暂无数据'}
        </div>
      ) : (
        t.rows.map((r, i) => {
          const name = String(r[0] ?? '');
          const customer = t.customers?.[i];
          const c = customer ? find(customer.country) : undefined;
          const tip = customer
            ? [c?.nameZh ?? customer.country, customer.contact]
                .filter(Boolean)
                .join(' · ')
            : undefined;
          const v2 = r[2];
          const secondText =
            second && v2 !== null && v2 !== undefined && v2 !== '—'
              ? second.format === 'percent'
                ? `${second.title} ${v2}%`
                : `${v2} ${second.title === '订单' ? '单' : second.title}`
              : '';
          return (
            <div
              // biome-ignore lint/suspicious/noArrayIndexKey: 排行按名次
              key={i}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                height: 46,
              }}
            >
              <Avatar name={name} code={c?.code} />
              <span
                style={{
                  flex: 1,
                  minWidth: 0,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 4,
                }}
              >
                <Tooltip title={tip}>
                  <span
                    style={{
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                      color: palette.ink,
                    }}
                  >
                    {name}
                  </span>
                </Tooltip>
                <span
                  aria-hidden
                  style={{
                    height: 4,
                    borderRadius: 2,
                    width: `${Math.max(4, (Number(r[1] ?? 0) / max) * 100)}%`,
                    // 排行的比例条是单一数量，用主题色（D48）
                    background: palette.chart,
                  }}
                />
              </span>
              <span
                className="num"
                style={{
                  flex: 'none',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'flex-end',
                  gap: 2,
                }}
              >
                <span
                  style={{ fontSize: 13, fontWeight: 600, color: palette.ink }}
                >
                  {fmt(r[1], 'money')}
                </span>
                <span style={{ fontSize: 12, color: palette.mute }}>
                  {secondText}
                </span>
              </span>
            </div>
          );
        })
      )}
    </div>
  );
};

/** 排行（工作台原型 H1）：业务员、客户两列排行列表，不用表格 */
export const RankingsWidget: React.FC = () => {
  const { data, error, reload } = useWidgetData<{ blocks: Block[] }>(
    'rankings',
  );
  const tables = (data?.blocks ?? []).filter((b) => b.type === 'table');
  return (
    <WidgetCard
      title="排行"
      period="本月"
      loading={!data}
      error={error}
      onRetry={reload}
    >
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
          columnGap: 32,
          rowGap: 16,
        }}
      >
        {tables.map((b) => (
          <RankList key={b.title} block={b} />
        ))}
      </div>
    </WidgetCard>
  );
};
