import { history } from '@umijs/max';
import { Tooltip } from 'antd';
import {
  AlarmClock,
  BadgeCheck,
  CalendarClock,
  CalendarX,
  Camera,
  CircleCheck,
  CircleDollarSign,
  Clock,
  Coins,
  FileCheck,
  FileClock,
  FileSearch,
  FileText,
  HandCoins,
  Handshake,
  Hash,
  Hourglass,
  Image,
  Inbox,
  LayoutGrid,
  Package,
  Percent,
  ReceiptText,
  Rocket,
  Send,
  ShoppingBag,
  Siren,
  TrendingDown,
  TriangleAlert,
  Trophy,
  Truck,
  UserPlus,
  Wallet,
} from 'lucide-react';
import React from 'react';
import { CustomerCell } from '@/components/DocFields';
import { MetricCard, MetricGrid } from '@/components/MetricCard';
import { useAppTheme } from '@/theme/AppTheme';
import { DeltaText } from '@/theme/delta';
import type { MetricFamily } from '@/theme/palette';
import { formatAmount } from '@/utils/format';
import { MenuName } from '@/utils/menuName';
import { useBoardKeys } from '../layout';
import {
  Metric,
  MoreLink,
  TodoItem,
  type Tone,
  useTone,
  useWidgetData,
  WidgetCard,
} from './WidgetCard';

/** 后端组件的通用数据（spec system/workbench，对应后端 WidgetData） */
interface MetricData {
  label: string;
  value?: number | null;
  format?: Format;
  hint?: string;
  hintTone?: 'green' | 'orange' | 'red';
  delta?: number | null;
  deltaFormat?: 'percent' | 'points';
  /** 越高越好；只用于说明，不参与涨跌着色 */
  higherIsBetter?: boolean;
  link?: string;
  /** 数字卡图标（lucide 名称）与家族色（blue、purple、cyan、gray），后端随指标下发 */
  icon?: string;
  family?: string;
}
interface TodoData {
  icon: string;
  tone: Tone;
  title: string;
  desc: string;
  count: number;
  alert?: boolean;
  link?: string;
}
interface TableData {
  columns: { title: string; align: 'l' | 'r'; format?: Format }[];
  rows: (string | number | null)[][];
  links?: (string | null)[];
  /** 第一列是客户时每行的客户：名称、国家 / 地区、主联系人 */
  customers?: { name?: string; country?: string; contact?: string }[];
  empty?: string;
}
interface BarsData {
  labels: string[];
  values: number[];
  format?: Format;
  highlightLast?: boolean;
}
export interface Block {
  type: 'metrics' | 'todos' | 'table' | 'bars' | 'funnel' | 'progress' | 'note';
  title?: string;
  metrics?: MetricData[];
  todos?: TodoData[];
  table?: TableData;
  bars?: BarsData;
  steps?: {
    label: string;
    value: number;
    rate?: number | null;
    link?: string;
  }[];
  progress?: {
    label: string;
    parts: { label: string; value: number; tone: Tone }[];
  }[];
  text?: string;
  tags?: string[];
}
type Format = 'money' | 'percent' | 'count';

/** 待办图标（后端给图标键）：统一 lucide，18px 放在 38 的底块里 */
const ICON_SIZE = 18;
const ICONS: Record<string, React.ReactNode> = {
  inbox: <Inbox size={ICON_SIZE} />,
  'file-text': <FileText size={ICON_SIZE} />,
  'file-done': <FileCheck size={ICON_SIZE} />,
  'file-clock': <FileClock size={ICON_SIZE} />,
  wallet: <Wallet size={ICON_SIZE} />,
  ship: <Rocket size={ICON_SIZE} />,
  send: <Send size={ICON_SIZE} />,
  truck: <Truck size={ICON_SIZE} />,
  hourglass: <Hourglass size={ICON_SIZE} />,
  alert: <Siren size={ICON_SIZE} />,
  warning: <TriangleAlert size={ICON_SIZE} />,
  audit: <FileSearch size={ICON_SIZE} />,
  clock: <Clock size={ICON_SIZE} />,
  'alarm-clock': <AlarmClock size={ICON_SIZE} />,
  check: <CircleCheck size={ICON_SIZE} />,
  money: <CircleDollarSign size={ICON_SIZE} />,
  down: <TrendingDown size={ICON_SIZE} />,
  camera: <Camera size={ICON_SIZE} />,
  picture: <Image size={ICON_SIZE} />,
  package: <Package size={ICON_SIZE} />,
  user: <UserPlus size={ICON_SIZE} />,
};

/**
 * 后端待办的 tone 是语义名，按工作台原型 D1 换成图标底块色：
 * blue（待确认的询盘、待下单的需求）→ 信息色；violet（订单待出运、待供应商发货）→ 蓝；
 * orange 不是告警时（PI 待收款）→ 主题色，告警时仍是警告色；数量为 0 的错误、警告色由 TodoItem 改中性。
 */
const todoTone = (tone: Tone, alert?: boolean): Tone =>
  tone === 'blue'
    ? 'violet'
    : tone === 'violet'
      ? 'blue'
      : tone === 'orange' && !alert
        ? 'primary'
        : tone;

export const fmt = (v: number | string | null | undefined, format?: Format) => {
  if (v === null || v === undefined || v === '') return '—';
  if (typeof v === 'string') return v;
  if (format === 'money') return formatAmount(v, 'CNY');
  if (format === 'percent') return `${v}%`;
  return v.toLocaleString('zh-CN');
};

const go = (link?: string | null) => link && history.push(link);

/** 较上月：百分比变化或百分点，按方向着色并配上下箭头（涨跌颜色见主题设置，higherIsBetter 不参与着色） */
const Delta: React.FC<{ m: MetricData }> = ({ m }) => {
  if (m.delta === null || m.delta === undefined) return null;
  const sign = m.delta > 0 ? '+' : '';
  return (
    <DeltaText value={m.delta}>
      较上月 {sign}
      {m.delta}
      {m.deltaFormat === 'points' ? ' 个百分点' : '%'}
    </DeltaText>
  );
};

export const BlockView: React.FC<{ block: Block }> = ({ block: b }) => {
  const { palette } = useAppTheme();
  const tone = useTone();
  const title = b.title && (
    <div
      style={{
        fontSize: 13,
        fontWeight: 600,
        color: palette.ink,
        marginBottom: 8,
      }}
    >
      {b.title}
    </div>
  );
  switch (b.type) {
    case 'metrics': {
      const metrics = b.metrics ?? [];
      return (
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          {metrics.map((m) => (
            <Metric
              key={m.label}
              label={m.label}
              value={fmt(m.value, m.format)}
              {...metricLook(m)}
              hint={
                m.delta !== null && m.delta !== undefined ? (
                  <Delta m={m} />
                ) : (
                  m.hint
                )
              }
              hintColor={m.hintTone ? tone(m.hintTone)[0] : undefined}
              onClick={m.link ? () => go(m.link) : undefined}
            />
          ))}
        </div>
      );
    }
    case 'todos': {
      const todos = b.todos ?? [];
      return (
        <div style={{ display: 'grid' }}>
          {title}
          {todos.map((t) => (
            <TodoItem
              key={t.title}
              icon={ICONS[t.icon] ?? <LayoutGrid size={ICON_SIZE} />}
              tone={todoTone(t.tone, t.alert)}
              title={t.title}
              desc={t.desc}
              count={t.count}
              alert={t.alert}
              onClick={() => go(t.link)}
            />
          ))}
        </div>
      );
    }
    case 'table': {
      const t = b.table as TableData;
      return (
        <div>
          {title}
          {/* 卡里的表格不再套描边框：表头一行次要色小字，行间分隔线，行高 46 */}
          <div>
            <div
              style={{
                display: 'flex',
                gap: 12,
                padding: '0 4px 8px',
              }}
            >
              {t.columns.map((c, i) => (
                <span
                  key={c.title}
                  style={{
                    flex: i === 0 ? 2 : 1,
                    textAlign: c.align === 'r' ? 'right' : 'left',
                    fontSize: 12,
                    color: palette.mute,
                  }}
                >
                  {c.title}
                </span>
              ))}
            </div>
            {t.rows.length === 0 ? (
              <div
                style={{
                  padding: 20,
                  textAlign: 'center',
                  color: palette.mute,
                  fontSize: 13,
                }}
              >
                {t.empty ?? '暂无数据'}
              </div>
            ) : (
              t.rows.map((r, ri) => {
                const link = t.links?.[ri];
                return (
                  <button
                    type="button"
                    // biome-ignore lint/suspicious/noArrayIndexKey: 行没有稳定编号
                    key={ri}
                    onClick={() => go(link)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 12,
                      width: '100%',
                      minHeight: 46,
                      padding: '0 4px',
                      border: 'none',
                      borderTop: `1px solid ${palette.hairline}`,
                      background: 'transparent',
                      cursor: link ? 'pointer' : 'default',
                      textAlign: 'left',
                      font: 'inherit',
                    }}
                  >
                    {r.map((v, i) => {
                      const customer = i === 0 ? t.customers?.[ri] : undefined;
                      return customer ? (
                        <span
                          key={t.columns[i]?.title ?? i}
                          style={{ flex: 2, minWidth: 0, fontSize: 13 }}
                        >
                          <CustomerCell
                            name={customer.name}
                            country={customer.country}
                            contact={customer.contact}
                          />
                        </span>
                      ) : (
                        <span
                          key={t.columns[i]?.title ?? i}
                          style={{
                            flex: i === 0 ? 2 : 1,
                            minWidth: 0,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                            textAlign:
                              t.columns[i]?.align === 'r' ? 'right' : 'left',
                            fontSize: 13,
                            color: i === 0 ? palette.ink : palette.sub,
                            fontVariantNumeric: 'tabular-nums',
                          }}
                        >
                          {fmt(v, t.columns[i]?.format)}
                        </span>
                      );
                    })}
                  </button>
                );
              })
            )}
          </div>
        </div>
      );
    }
    case 'bars': {
      const bars = b.bars as BarsData;
      const max = Math.max(1, ...bars.values.map(Number));
      // 图表只亮一处（ui-redesign D31）：按时间的亮本期（最后一根），其余亮最大的一根
      const lit =
        (bars.highlightLast ?? true)
          ? bars.values.length - 1
          : bars.values
              .map(Number)
              .indexOf(Math.max(...bars.values.map(Number)));
      return (
        // 卡片保持自身高度（工作台瀑布流），柱状图区固定 160 高，柱子按百分比
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {title}
          <div
            style={{
              display: 'flex',
              alignItems: 'flex-end',
              gap: 6,
              height: 160,
            }}
          >
            {bars.values.map((v, i) => {
              const last = i === lit;
              return (
                <Tooltip
                  key={bars.labels[i]}
                  title={`${bars.labels[i]} · ${fmt(Number(v), bars.format)}`}
                >
                  <span
                    style={{
                      flex: 1,
                      minWidth: 0,
                      height: Number(v) ? `${(Number(v) / max) * 100}%` : 3,
                      borderRadius: '4px 4px 0 0',
                      // 图表只亮一处（ui-redesign D31）：本期用图表色，往期用图表色的浅色（两类只用主题色深浅区分）
                      background: !Number(v)
                        ? palette.hairline
                        : last
                          ? palette.chart
                          : palette.chartSoft,
                    }}
                  />
                </Tooltip>
              );
            })}
          </div>
          <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
            {bars.labels.map((l) => (
              <span
                key={l}
                style={{
                  flex: 1,
                  minWidth: 0,
                  textAlign: 'center',
                  fontSize: 12,
                  color: palette.mute,
                }}
              >
                {l}
              </span>
            ))}
          </div>
          {b.text && (
            <div style={{ marginTop: 10, fontSize: 12, color: palette.mute }}>
              {b.text}
            </div>
          )}
        </div>
      );
    }
    case 'funnel':
      return <Funnel steps={b.steps ?? []} />;
    case 'progress':
      return (
        <div style={{ display: 'grid', gap: 14 }}>
          {(b.progress ?? []).map((p) => {
            const total = p.parts.reduce((a, x) => a + Number(x.value), 0) || 1;
            return (
              <div key={p.label}>
                <div
                  style={{
                    display: 'flex',
                    gap: 12,
                    alignItems: 'center',
                    marginBottom: 6,
                    flexWrap: 'wrap',
                  }}
                >
                  <b style={{ color: palette.ink, fontSize: 13, flex: 1 }}>
                    {p.label}
                  </b>
                  {p.parts.map((x) => (
                    <span
                      key={x.label}
                      style={{ fontSize: 12, color: palette.mute }}
                    >
                      <span
                        style={{
                          display: 'inline-block',
                          width: 8,
                          height: 8,
                          borderRadius: 4,
                          marginRight: 4,
                          background: tone(x.tone)[0],
                        }}
                      />
                      {x.label} {x.value}
                    </span>
                  ))}
                </div>
                <div
                  style={{
                    display: 'flex',
                    height: 10,
                    borderRadius: 5,
                    overflow: 'hidden',
                    background: palette.inset,
                  }}
                >
                  {p.parts.map((x) =>
                    Number(x.value) > 0 ? (
                      <span
                        key={x.label}
                        style={{
                          width: `${(Number(x.value) / total) * 100}%`,
                          background: tone(x.tone)[0],
                        }}
                      />
                    ) : null,
                  )}
                </div>
              </div>
            );
          })}
        </div>
      );
    case 'note':
      return (
        <div style={{ fontSize: 13, color: palette.mute }}>
          {b.text}
          {(b.tags ?? []).length > 0 && (
            <div
              style={{
                display: 'flex',
                gap: 8,
                flexWrap: 'wrap',
                marginTop: 8,
              }}
            >
              {b.tags?.map((t) => (
                <span
                  key={t}
                  style={{
                    padding: '3px 10px',
                    borderRadius: 11,
                    background: palette.neutralSoft,
                    color: palette.sub,
                    fontSize: 12,
                  }}
                >
                  {t}
                </span>
              ))}
            </div>
          )}
        </div>
      );
    default:
      return null;
  }
};

/** 漏斗（工作台原型 H1）：每级两行——名称、数量与「→ 比例」，下面 8 高细条；只有最后一级用图表色，其余中性；
 * 比例是本级 ÷ 上一级，各级是本月各自新增的数量，超过 100% 时不显示（ui-redesign D16）；底部一句结论 */
const Funnel: React.FC<{ steps: NonNullable<Block['steps']> }> = ({
  steps,
}) => {
  const { palette } = useAppTheme();
  const max = Math.max(1, ...steps.map((x) => Number(x.value)));
  const first = steps[0];
  const last = steps[steps.length - 1];
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'grid', gap: 10 }}>
        {steps.map((x, i) => {
          // 有链接的级才做成按钮，没有链接的不能聚焦
          const Row = x.link ? 'button' : 'div';
          return (
            <Row
              {...(x.link
                ? { type: 'button' as const, onClick: () => go(x.link) }
                : {})}
              key={x.label}
              style={{
                display: 'grid',
                gap: 8,
                padding: 0,
                border: 'none',
                background: 'transparent',
                cursor: x.link ? 'pointer' : 'default',
                textAlign: 'left',
                font: 'inherit',
              }}
            >
              <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ flex: 1, fontSize: 13, color: palette.sub }}>
                  {x.label}
                </span>
                <span
                  className="num"
                  style={{ fontWeight: 600, color: palette.ink }}
                >
                  {fmt(x.value, 'count')}
                </span>
                {x.rate !== null && x.rate !== undefined && x.rate <= 100 && (
                  <span
                    className="num"
                    style={{ fontSize: 12, color: palette.mute }}
                  >
                    → {x.rate}%
                  </span>
                )}
              </span>
              <span
                style={{
                  display: 'block',
                  height: 8,
                  borderRadius: 4,
                  width: `${Math.max(2, (Number(x.value) / max) * 100)}%`,
                  background:
                    i === steps.length - 1 ? palette.chart : palette.chartSoft,
                }}
              />
            </Row>
          );
        })}
      </div>
      {first && last && first !== last && (
        <div
          className="num"
          style={{
            marginTop: 'auto',
            paddingTop: 12,
            fontSize: 13,
            color: palette.mute,
          }}
        >
          本月{first.label} {fmt(first.value, 'count')}，{last.label}{' '}
          {fmt(last.value, 'count')}
        </div>
      )}
    </div>
  );
};

/** 数字卡图标（后端随指标下发 lucide 名称，同待办的 icon）；没有时按格式给默认 */
const CARD_ICONS: Record<string, React.ElementType> = {
  handshake: Handshake,
  'shopping-bag': ShoppingBag,
  coins: Coins,
  percent: Percent,
  'hand-coins': HandCoins,
  'file-clock': FileClock,
  'receipt-text': ReceiptText,
  'calendar-clock': CalendarClock,
  hourglass: Hourglass,
  'calendar-x': CalendarX,
  'user-plus': UserPlus,
  'badge-check': BadgeCheck,
  trophy: Trophy,
  inbox: Inbox,
  'file-text': FileText,
};
const FAMILIES: MetricFamily[] = ['blue', 'purple', 'cyan', 'gray'];
const metricMeta = (m: MetricData): [React.ElementType, MetricFamily] => {
  const byFormat: [React.ElementType, MetricFamily] =
    m.format === 'money'
      ? [CircleDollarSign, 'blue']
      : m.format === 'percent'
        ? [Percent, 'purple']
        : [Hash, 'blue'];
  return [
    (m.icon && CARD_ICONS[m.icon]) || byFormat[0],
    FAMILIES.includes(m.family as MetricFamily)
      ? (m.family as MetricFamily)
      : byFormat[1],
  ];
};

/** 卡内数字小块的图标与家族色（同数字卡，后端下发） */
const metricLook = (m: MetricData) => {
  const [Icon, family] = metricMeta(m);
  return { icon: <Icon size={14} />, family };
};

/** 后端指标 → 数字卡：金额拆出币种；较上月为涨跌胶囊；说明带红、橙色时为告警标签 */
export const MetricItem: React.FC<{ m: MetricData; bar?: number }> = ({
  m,
  bar,
}) => {
  const [Icon, family] = metricMeta(m);
  const money =
    m.format === 'money' && m.value !== null && m.value !== undefined;
  const alert =
    m.hint && (m.hintTone === 'red' || m.hintTone === 'orange')
      ? {
          tone:
            m.hintTone === 'red' ? ('error' as const) : ('warning' as const),
          text: m.hint,
        }
      : undefined;
  return (
    <MetricCard
      label={m.label}
      icon={<Icon size={20} />}
      family={family}
      currency={money ? 'CNY' : undefined}
      value={
        money
          ? formatAmount(Number(m.value), 'CNY').replace(/^CNY\s*/, '')
          : fmt(m.value, m.format)
      }
      delta={
        m.delta === null || m.delta === undefined
          ? undefined
          : {
              value: m.delta,
              text:
                m.deltaFormat === 'points'
                  ? `${Math.abs(m.delta)} 个百分点`
                  : `${Math.abs(m.delta)}%`,
            }
      }
      alert={alert}
      hint={alert ? undefined : m.hint}
      bar={bar}
      onClick={m.link ? () => go(m.link) : undefined}
    />
  );
};

/** 有效商机下面画有效率（有效 ÷ 新增），其余不画 */
const barOf = (m: MetricData, all: MetricData[]) => {
  if (m.label !== '有效商机') return undefined;
  const total = all.find((x) => x.label === '本月新增商机')?.value;
  return total ? Number(m.value ?? 0) / Number(total) : undefined;
};

/** 通用看板组件：外框（标题、统计期间、口径）由注册表给，数据块由后端给 */
export const blockWidget = (opts: {
  key: string;
  title: string;
  /** 统计期间，标题旁显示 */
  period?: string;
  /** 口径说明，放进帮助图标 */
  help?: string;
  /** 「查看更多」跳转的页面，文字取该页的菜单名称 */
  more?: { link: string };
  /**
   * 数字卡组（ui-redesign D1）：不套白卡，每张卡自己浮起；cols 为每排张数（默认一排放下）。
   * 文字块（如「未结汇外币」）放到标题行右侧。
   */
  group?: { cols?: (count: number) => number };
  /** 看板上有这个组件时不显示的指标（经营概览：有主角卡时不重复显示成交额，D28） */
  omit?: { label: string; whenBoardHas: string };
  /** 待办列表：标题行右侧写「共 N 件」，卡片左右内边距收窄（行自带内边距） */
  todos?: boolean;
}): React.FC => {
  const BlockWidget: React.FC = () => {
    const keys = useBoardKeys();
    const { data, error, reload } = useWidgetData<{ blocks: Block[] }>(
      opts.key,
    );
    const blocks = data?.blocks ?? [];
    const more = opts.more && (
      <MoreLink onClick={() => go(opts.more?.link)}>
        <MenuName path={opts.more.link} />
      </MoreLink>
    );
    // 后端只给了数字（可带一句说明）时，自动按数字卡组显示，不在白卡里再套小卡
    const metricsOnly =
      blocks.length > 0 &&
      blocks.some((b) => b.type === 'metrics') &&
      blocks.every((b) => b.type === 'metrics' || b.type === 'note');
    if (opts.group || metricsOnly) {
      const omit =
        opts.omit && keys.has(opts.omit.whenBoardHas)
          ? opts.omit.label
          : undefined;
      const metrics = blocks
        .flatMap((b) => (b.type === 'metrics' ? (b.metrics ?? []) : []))
        .filter((m) => m.label !== omit);
      const note = blocks.find((b) => b.type === 'note');
      const cols = opts.group?.cols?.(metrics.length) ?? metrics.length;
      return (
        <WidgetCard
          title={opts.title}
          period={opts.period}
          help={opts.help}
          note={
            note &&
            ((note.tags ?? []).length > 0
              ? `${note.text}：${note.tags?.join('、')}`
              : note.text)
          }
          extra={more}
          loading={!data}
          error={error}
          onRetry={reload}
          bare
        >
          {metrics.length > 0 && (
            <MetricGrid cols={Math.max(1, cols)}>
              {metrics.map((m) => (
                <MetricItem key={m.label} m={m} bar={barOf(m, metrics)} />
              ))}
            </MetricGrid>
          )}
        </WidgetCard>
      );
    }
    const todoTotal = opts.todos
      ? blocks
          .flatMap((b) => b.todos ?? [])
          .reduce((a, t) => a + Number(t.count || 0), 0)
      : 0;
    return (
      <WidgetCard
        title={opts.title}
        period={opts.period}
        help={opts.help}
        note={opts.todos && data ? `共 ${todoTotal} 件` : undefined}
        extra={more}
        loading={!data}
        error={error}
        onRetry={reload}
        inset={opts.todos}
      >
        <div
          style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 14 }}
        >
          {blocks.map((b, i) => (
            // biome-ignore lint/suspicious/noArrayIndexKey: 块按顺序固定
            <BlockView key={i} block={b} />
          ))}
        </div>
      </WidgetCard>
    );
  };
  return BlockWidget;
};
