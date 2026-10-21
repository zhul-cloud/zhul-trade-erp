import {
  AlertOutlined,
  AppstoreOutlined,
  AuditOutlined,
  CameraOutlined,
  CarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  FallOutlined,
  FieldTimeOutlined,
  FileDoneOutlined,
  FileTextOutlined,
  HourglassOutlined,
  InboxOutlined,
  PictureOutlined,
  RightOutlined,
  RocketOutlined,
  SendOutlined,
  ShoppingOutlined,
  UserAddOutlined,
  WalletOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import { Tooltip } from 'antd';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import {
  Metric,
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
  empty?: string;
}
interface BarsData {
  labels: string[];
  values: number[];
  format?: Format;
  highlightLast?: boolean;
}
interface Block {
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

const ICONS: Record<string, React.ReactNode> = {
  inbox: <InboxOutlined />,
  'file-text': <FileTextOutlined />,
  'file-done': <FileDoneOutlined />,
  'file-clock': <FieldTimeOutlined />,
  wallet: <WalletOutlined />,
  ship: <RocketOutlined />,
  send: <SendOutlined />,
  truck: <CarOutlined />,
  hourglass: <HourglassOutlined />,
  alert: <AlertOutlined />,
  warning: <WarningOutlined />,
  audit: <AuditOutlined />,
  clock: <ClockCircleOutlined />,
  check: <CheckCircleOutlined />,
  money: <DollarOutlined />,
  down: <FallOutlined />,
  camera: <CameraOutlined />,
  picture: <PictureOutlined />,
  package: <ShoppingOutlined />,
  user: <UserAddOutlined />,
};

export const fmt = (v: number | string | null | undefined, format?: Format) => {
  if (v === null || v === undefined || v === '') return '—';
  if (typeof v === 'string') return v;
  if (format === 'money') return formatAmount(v, 'CNY');
  if (format === 'percent') return `${v}%`;
  return v.toLocaleString('zh-CN');
};

const go = (link?: string | null) => link && history.push(link);

/** 较上月：百分比变化或百分点，变好绿色、变坏红色 */
const Delta: React.FC<{ m: MetricData }> = ({ m }) => {
  const { palette } = useAppTheme();
  if (m.delta === null || m.delta === undefined) return null;
  const good =
    m.delta === 0 ? null : m.delta > 0 === (m.higherIsBetter ?? true);
  const sign = m.delta > 0 ? '+' : '';
  return (
    <span
      style={{
        color:
          good === null ? palette.mute : good ? palette.green : palette.red,
      }}
    >
      较上月 {sign}
      {m.delta}
      {m.deltaFormat === 'points' ? ' 个百分点' : '%'}
    </span>
  );
};

const BlockView: React.FC<{ block: Block }> = ({ block: b }) => {
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
    case 'metrics':
      return (
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          {(b.metrics ?? []).map((m) => (
            <Metric
              key={m.label}
              label={m.label}
              value={fmt(m.value, m.format)}
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
    case 'todos':
      return (
        <div style={{ display: 'grid', gap: 8 }}>
          {title}
          {(b.todos ?? []).map((t) => (
            <TodoItem
              key={t.title}
              icon={ICONS[t.icon] ?? <AppstoreOutlined />}
              tone={t.tone}
              title={t.title}
              desc={t.desc}
              count={t.count}
              alert={t.alert}
              onClick={() => go(t.link)}
            />
          ))}
        </div>
      );
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
                    {r.map((v, i) => (
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
                    ))}
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
        <div>
          {title}
          <div
            style={{
              display: 'flex',
              alignItems: 'flex-end',
              gap: 6,
              height: 140,
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
                      background: Number(v) ? palette.link : palette.hairline,
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
                    background: palette.link,
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
                    background: palette.accentSoft,
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

/** 通用看板组件：外框（标题、图标）由注册表给，数据块由后端给 */
export const blockWidget = (opts: {
  key: string;
  title: string;
  icon: React.ReactNode;
  tone: Tone;
  sub?: string;
  more?: { label: string; link: string };
}): React.FC => {
  const BlockWidget: React.FC = () => {
    const { data, error, reload } = useWidgetData<{ blocks: Block[] }>(
      opts.key,
    );
    return (
      <WidgetCard
        icon={opts.icon}
        tone={opts.tone}
        title={opts.title}
        sub={opts.sub}
        extra={
          opts.more && (
            <a onClick={() => go(opts.more?.link)} style={{ fontSize: 13 }}>
              {opts.more.label} <RightOutlined style={{ fontSize: 11 }} />
            </a>
          )
        }
        loading={!data}
        error={error}
        onRetry={reload}
      >
        <div style={{ display: 'grid', gap: 14 }}>
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
