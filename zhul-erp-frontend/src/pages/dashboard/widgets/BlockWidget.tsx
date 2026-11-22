import { history } from '@umijs/max';
import { Tooltip } from 'antd';
import {
  AlarmClock,
  CalendarX,
  Camera,
  CircleCheck,
  CircleDollarSign,
  Clock,
  FileCheck,
  FileClock,
  FileSearch,
  FileText,
  Hourglass,
  Image,
  Inbox,
  Landmark,
  LayoutGrid,
  Package,
  PiggyBank,
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
import { useAppTheme } from '@/theme/AppTheme';
import { DeltaText } from '@/theme/delta';
import { formatAmount } from '@/utils/format';
import { MenuName } from '@/utils/menuName';
import {
  alertTone,
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

/**
 * 数字小块名称前的图标（原型 D1）：后端只给名称，按名称配图标。同一块里只有全部配齐时才显示，
 * 否则整块都不带图标，标签行高低一致。错误色在数值为 0 时改中性。
 */
const METRIC_ICONS: Record<string, [React.ReactNode, Tone]> = {
  成交额: [<CircleDollarSign key="i" size={14} />, 'primary'],
  毛利: [<PiggyBank key="i" size={14} />, 'green'],
  实收回款: [<Landmark key="i" size={14} />, 'violet'],
  成交订单: [<ShoppingBag key="i" size={14} />, 'blue'],
  等采购回价的型号: [<Hourglass key="i" size={14} />, 'violet'],
  已过报价截止: [<CalendarX key="i" size={14} />, 'red'],
  本月新增商机: [<UserPlus key="i" size={14} />, 'primary'],
  有效商机: [<CircleCheck key="i" size={14} />, 'green'],
  赢单: [<Trophy key="i" size={14} />, 'blue'],
};

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
      const withIcons = metrics.every((m) => METRIC_ICONS[m.label]);
      return (
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          {metrics.map((m) => (
            <Metric
              key={m.label}
              label={m.label}
              value={fmt(m.value, m.format)}
              icon={withIcons ? METRIC_ICONS[m.label][0] : undefined}
              tone={
                withIcons
                  ? alertTone(METRIC_ICONS[m.label][1], m.value)
                  : undefined
              }
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
          {todos.map((t, i) => (
            <TodoItem
              key={t.title}
              icon={ICONS[t.icon] ?? <LayoutGrid size={ICON_SIZE} />}
              tone={todoTone(t.tone, t.alert)}
              title={t.title}
              desc={t.desc}
              count={t.count}
              alert={t.alert}
              last={i === todos.length - 1}
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
          <div
            style={{
              border: `1px solid ${palette.hairline}`,
              borderRadius: 12,
              overflow: 'hidden',
            }}
          >
            <div
              style={{
                display: 'flex',
                gap: 12,
                padding: '10px 14px',
                background: palette.inset,
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
                      gap: 12,
                      width: '100%',
                      padding: '11px 14px',
                      border: 'none',
                      borderTop: `1px solid ${palette.hairline}`,
                      background: 'transparent',
                      cursor: link ? 'pointer' : 'default',
                      textAlign: 'left',
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
              const last =
                i === bars.values.length - 1 && (bars.highlightLast ?? true);
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
                      background: Number(v) ? palette.chart : palette.hairline,
                      opacity: last ? 1 : 0.7,
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
                  fontSize: 11,
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
    case 'funnel': {
      const steps = b.steps ?? [];
      const max = Math.max(1, ...steps.map((x) => Number(x.value)));
      return (
        <div style={{ display: 'grid', gap: 8 }}>
          {steps.map((x) => (
            <button
              type="button"
              key={x.label}
              onClick={() => go(x.link)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: 0,
                border: 'none',
                background: 'transparent',
                cursor: x.link ? 'pointer' : 'default',
                textAlign: 'left',
              }}
            >
              <span
                style={{
                  width: 72,
                  flex: 'none',
                  fontSize: 13,
                  color: palette.sub,
                }}
              >
                {x.label}
              </span>
              <span
                style={{
                  flex: 1,
                  minWidth: 0,
                  height: 26,
                  borderRadius: 6,
                  background: palette.inset,
                }}
              >
                <span
                  style={{
                    display: 'block',
                    height: '100%',
                    width: `${Math.max(2, (Number(x.value) / max) * 100)}%`,
                    borderRadius: 6,
                    background: palette.chart,
                    opacity: 0.85,
                  }}
                />
              </span>
              <span
                style={{
                  width: 48,
                  flex: 'none',
                  textAlign: 'right',
                  fontWeight: 700,
                  color: palette.ink,
                  fontVariantNumeric: 'tabular-nums',
                }}
              >
                {fmt(x.value, 'count')}
              </span>
              <span
                style={{
                  width: 56,
                  flex: 'none',
                  textAlign: 'right',
                  fontSize: 12,
                  color: palette.green,
                }}
              >
                {x.rate !== null && x.rate !== undefined ? `${x.rate}%` : ''}
              </span>
            </button>
          ))}
        </div>
      );
    }
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
                    background: palette.primarySoft,
                    color: palette.link,
                    fontSize: 12,
                    fontWeight: 600,
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
}): React.FC => {
  const BlockWidget: React.FC = () => {
    const { data, error, reload } = useWidgetData<{ blocks: Block[] }>(
      opts.key,
    );
    return (
      <WidgetCard
        title={opts.title}
        period={opts.period}
        help={opts.help}
        extra={
          opts.more && (
            <MoreLink onClick={() => go(opts.more?.link)}>
              <MenuName path={opts.more.link} />
            </MoreLink>
          )
        }
        loading={!data}
        error={error}
        onRetry={reload}
      >
        <div
          style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 14 }}
        >
          {data?.blocks.map((b, i) => (
            // biome-ignore lint/suspicious/noArrayIndexKey: 块按顺序固定
            <BlockView key={i} block={b} />
          ))}
        </div>
      </WidgetCard>
    );
  };
  return BlockWidget;
};
