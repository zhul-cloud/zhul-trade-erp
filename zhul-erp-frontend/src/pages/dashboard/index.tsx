import {
  AppstoreAddOutlined,
  BankOutlined,
  CaretDownOutlined,
  CaretUpOutlined,
  ClockCircleOutlined,
  FileAddOutlined,
  FileDoneOutlined,
  InboxOutlined,
  MessageOutlined,
  RightOutlined,
  RiseOutlined,
  SendOutlined,
  ShoppingCartOutlined,
  TruckOutlined,
  UserAddOutlined,
  WalletOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { Helmet } from '@umijs/max';
import React from 'react';
import {
  activities,
  fxRates,
  kpiCards,
  logisticsStages,
  orderTrend,
  quickActions,
  todoItems,
  topCustomers,
} from './data';

const COLOR = {
  white: '#FFFFFF',
  border: '#EEF0F3',
  text: '#111111',
  textSub: '#6B7280',
  textSubtle: '#9CA3AF',
  primary: '#1677FF',
  primarySoft: '#E8F3FF',
  green: '#16A34A',
  greenSoft: '#F0FDF4',
  red: '#DC2626',
  redSoft: '#FEF2F2',
  orange: '#F97316',
  orangeSoft: '#FFF3E8',
  purple: '#7C3AED',
  purpleSoft: '#F3E8FF',
  graySoft: '#F3F4F6',
} as const;

const KPI_ICONS = {
  'shopping-cart': {
    Icon: ShoppingCartOutlined,
    fg: COLOR.primary,
    bg: COLOR.primarySoft,
  },
  inbox: { Icon: InboxOutlined, fg: COLOR.purple, bg: COLOR.purpleSoft },
  wallet: { Icon: WalletOutlined, fg: COLOR.orange, bg: COLOR.orangeSoft },
  rise: { Icon: RiseOutlined, fg: COLOR.green, bg: COLOR.greenSoft },
} as const;

const TODO_ICONS = {
  'file-done': {
    Icon: FileDoneOutlined,
    fg: COLOR.primary,
    bg: COLOR.primarySoft,
  },
  clock: { Icon: ClockCircleOutlined, fg: COLOR.orange, bg: COLOR.orangeSoft },
  warning: { Icon: WarningOutlined, fg: COLOR.red, bg: COLOR.redSoft },
  truck: { Icon: TruckOutlined, fg: COLOR.purple, bg: COLOR.purpleSoft },
} as const;

const QUICK_ICONS = {
  message: MessageOutlined,
  'file-add': FileAddOutlined,
  'shopping-cart': ShoppingCartOutlined,
  'appstore-add': AppstoreAddOutlined,
  'user-add': UserAddOutlined,
  send: SendOutlined,
} as const;

const HINT_COLOR: Record<string, string> = {
  up: COLOR.green,
  down: COLOR.red,
  warning: COLOR.orange,
};

const cardStyle: React.CSSProperties = {
  background: COLOR.white,
  border: `1px solid ${COLOR.border}`,
  borderRadius: 12,
  boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
  padding: '20px 20px',
  display: 'flex',
  flexDirection: 'column',
  gap: 16,
};

const CardHeader: React.FC<{ title: string; subtitle?: string }> = ({
  title,
  subtitle,
}) => (
  <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
    <span style={{ fontSize: 16, fontWeight: 600, color: COLOR.text }}>
      {title}
    </span>
    {subtitle && (
      <span style={{ fontSize: 12, color: COLOR.textSubtle }}>{subtitle}</span>
    )}
  </div>
);

const IconPill: React.FC<{
  icon: React.ComponentType;
  fg: string;
  bg: string;
  size?: number;
}> = ({ icon: Icon, fg, bg, size = 36 }) => (
  <div
    style={{
      width: size,
      height: size,
      borderRadius: Math.round(size * 0.28),
      background: bg,
      color: fg,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      fontSize: Math.round(size * 0.5),
      flexShrink: 0,
    }}
  >
    <Icon />
  </div>
);

const DashboardPage: React.FC = () => {
  const today = new Date();
  const weekday = [
    '星期日',
    '星期一',
    '星期二',
    '星期三',
    '星期四',
    '星期五',
    '星期六',
  ][today.getDay()];
  const dateStr = `${today.getFullYear()}年${today.getMonth() + 1}月${today.getDate()}日 ${weekday}`;
  const hour = today.getHours();
  const greeting =
    hour < 6 ? '夜深了' : hour < 12 ? '早安' : hour < 18 ? '下午好' : '晚上好';

  const maxOrderValue = Math.max(...orderTrend.map((d) => d.value));

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
        padding: 24,
      }}
    >
      <Helmet>
        <title>工作台 - 烛龙ERP</title>
      </Helmet>

      {/* 欢迎区 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <span style={{ fontSize: 22, fontWeight: 700, color: COLOR.text }}>
            {greeting}，Admin
          </span>
          <span style={{ fontSize: 13, color: COLOR.textSub }}>
            今天是 {dateStr}，祝你工作顺利
          </span>
        </div>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            padding: '8px 14px',
            borderRadius: 8,
            background: COLOR.white,
            border: `1px solid ${COLOR.border}`,
          }}
        >
          <BankOutlined style={{ color: COLOR.textSubtle }} />
          <span style={{ fontSize: 13, color: COLOR.textSub }}>
            烛龙外贸有限公司
          </span>
        </div>
      </div>

      {/* KPI 卡片 */}
      <div style={{ display: 'flex', gap: 16 }}>
        {kpiCards.map((k) => {
          const { Icon, fg, bg } = KPI_ICONS[k.icon];
          return (
            <div key={k.label} style={{ ...cardStyle, flex: 1, gap: 12 }}>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <IconPill icon={Icon} fg={fg} bg={bg} />
                <span style={{ fontSize: 13, color: COLOR.textSub }}>
                  {k.label}
                </span>
              </div>
              <span
                style={{ fontSize: 28, fontWeight: 700, color: COLOR.text }}
              >
                {k.value}
              </span>
              <span style={{ fontSize: 12, color: HINT_COLOR[k.hintType] }}>
                {k.hint}
              </span>
            </div>
          );
        })}
      </div>

      {/* 两栏区 */}
      <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
        {/* 左栏 */}
        <div
          style={{
            flex: 2,
            display: 'flex',
            flexDirection: 'column',
            gap: 16,
            minWidth: 0,
          }}
        >
          {/* 订单趋势 */}
          <div style={{ ...cardStyle, gap: 18 }}>
            <CardHeader
              title="订单趋势"
              subtitle="近6个月，人民币金额（万元）"
            />
            <div
              style={{
                display: 'flex',
                gap: 18,
                alignItems: 'flex-end',
                justifyContent: 'center',
                height: 180,
              }}
            >
              {orderTrend.map((mo, i) => {
                const isLast = i === orderTrend.length - 1;
                const barH = Math.round((mo.value / maxOrderValue) * 140);
                return (
                  <div
                    key={mo.month}
                    style={{
                      width: 64,
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      gap: 8,
                    }}
                  >
                    <div
                      style={{
                        width: '100%',
                        height: 140,
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'flex-end',
                        alignItems: 'center',
                        gap: 8,
                      }}
                    >
                      <span
                        style={{
                          fontSize: 11,
                          fontWeight: 600,
                          color: isLast ? COLOR.primary : COLOR.textSubtle,
                        }}
                      >
                        {mo.value}
                      </span>
                      <div
                        style={{
                          width: 32,
                          height: barH,
                          borderRadius: '6px 6px 0 0',
                          background: isLast ? COLOR.primary : '#BFDBFE',
                        }}
                      />
                    </div>
                    <span style={{ fontSize: 12, color: COLOR.textSub }}>
                      {mo.month}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>

          {/* 待办事项 */}
          <div style={{ ...cardStyle, gap: 4 }}>
            <CardHeader
              title="待办事项"
              subtitle={`共 ${todoItems.reduce((s, t) => s + t.badge, 0)} 项待处理`}
            />
            {todoItems.map((t) => {
              const { Icon, fg, bg } = TODO_ICONS[t.icon];
              return (
                <div
                  key={t.label}
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    padding: '10px 4px',
                    cursor: 'pointer',
                  }}
                >
                  <div
                    style={{ display: 'flex', gap: 12, alignItems: 'center' }}
                  >
                    <IconPill icon={Icon} fg={fg} bg={bg} size={32} />
                    <div
                      style={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 2,
                      }}
                    >
                      <span
                        style={{
                          fontSize: 13,
                          fontWeight: 600,
                          color: COLOR.text,
                        }}
                      >
                        {t.label}
                      </span>
                      <span style={{ fontSize: 12, color: COLOR.textSub }}>
                        {t.desc}
                      </span>
                    </div>
                  </div>
                  <div
                    style={{ display: 'flex', gap: 8, alignItems: 'center' }}
                  >
                    <div
                      style={{
                        width: 22,
                        height: 22,
                        borderRadius: 11,
                        background: bg,
                        color: fg,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: 12,
                        fontWeight: 700,
                      }}
                    >
                      {t.badge}
                    </div>
                    <RightOutlined
                      style={{ fontSize: 12, color: COLOR.textSubtle }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* 右栏 */}
        <div
          style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            gap: 16,
            minWidth: 0,
          }}
        >
          {/* 快捷入口 */}
          <div style={{ ...cardStyle, gap: 14 }}>
            <CardHeader title="快捷入口" />
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {[0, 3].map((start) => (
                <div key={start} style={{ display: 'flex', gap: 10 }}>
                  {quickActions.slice(start, start + 3).map((qa) => {
                    const Icon = QUICK_ICONS[qa.icon];
                    return (
                      <div
                        key={qa.label}
                        style={{
                          flex: 1,
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: 8,
                          padding: '14px 8px',
                          borderRadius: 10,
                          background: COLOR.graySoft,
                          cursor: 'pointer',
                        }}
                      >
                        <IconPill
                          icon={Icon}
                          fg={COLOR.primary}
                          bg={COLOR.white}
                          size={32}
                        />
                        <span
                          style={{
                            fontSize: 12,
                            color: COLOR.text,
                            textAlign: 'center',
                          }}
                        >
                          {qa.label}
                        </span>
                      </div>
                    );
                  })}
                </div>
              ))}
            </div>
          </div>

          {/* 汇率速览 */}
          <div style={{ ...cardStyle, gap: 4 }}>
            <CardHeader title="汇率速览" subtitle="基准货币：CNY" />
            {fxRates.map((fx) => (
              <div
                key={fx.currency}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '8px 0',
                }}
              >
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <span
                    style={{ fontSize: 13, fontWeight: 700, color: COLOR.text }}
                  >
                    {fx.currency}
                  </span>
                  <span style={{ fontSize: 12, color: COLOR.textSubtle }}>
                    {fx.label}
                  </span>
                </div>
                <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                  <span
                    style={{ fontSize: 13, fontWeight: 600, color: COLOR.text }}
                  >
                    {fx.rate}
                  </span>
                  <div
                    style={{
                      display: 'flex',
                      gap: 2,
                      alignItems: 'center',
                      color: fx.up ? COLOR.green : COLOR.red,
                    }}
                  >
                    {fx.up ? (
                      <CaretUpOutlined style={{ fontSize: 12 }} />
                    ) : (
                      <CaretDownOutlined style={{ fontSize: 12 }} />
                    )}
                    <span style={{ fontSize: 12 }}>{fx.pct}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>

          {/* 在途货物 */}
          <div style={{ ...cardStyle, gap: 4 }}>
            <CardHeader title="在途货物" subtitle="实时物流状态" />
            {logisticsStages.map((s) => (
              <div
                key={s.label}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '8px 0',
                }}
              >
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <span
                    style={{
                      width: 8,
                      height: 8,
                      borderRadius: 4,
                      background: s.color,
                      display: 'inline-block',
                    }}
                  />
                  <span style={{ fontSize: 13, color: COLOR.text }}>
                    {s.label}
                  </span>
                </div>
                <span
                  style={{ fontSize: 14, fontWeight: 700, color: COLOR.text }}
                >
                  {s.count}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* 底部两栏 */}
      <div style={{ display: 'flex', gap: 16 }}>
        {/* Top 5 客户 */}
        <div style={{ ...cardStyle, flex: 1, gap: 4 }}>
          <CardHeader title="Top 5 客户" subtitle="按本月采购额（万美元）" />
          {topCustomers.map((c, i) => {
            const rankColors =
              i === 0
                ? { fg: '#B45309', bg: '#FEF3C7' }
                : i === 1
                  ? { fg: '#64748B', bg: '#F1F5F9' }
                  : i === 2
                    ? { fg: '#B45309', bg: '#FBEEE6' }
                    : { fg: COLOR.textSubtle, bg: COLOR.graySoft };
            return (
              <div
                key={c.name}
                style={{
                  display: 'flex',
                  gap: 12,
                  alignItems: 'center',
                  padding: '8px 0',
                }}
              >
                <div
                  style={{
                    width: 24,
                    height: 24,
                    borderRadius: 8,
                    background: rankColors.bg,
                    color: rankColors.fg,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 12,
                    fontWeight: 700,
                    flexShrink: 0,
                  }}
                >
                  {i + 1}
                </div>
                <div
                  style={{
                    flex: 1,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 6,
                    minWidth: 0,
                  }}
                >
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                    }}
                  >
                    <div
                      style={{
                        display: 'flex',
                        gap: 6,
                        alignItems: 'center',
                        minWidth: 0,
                      }}
                    >
                      <span
                        style={{
                          fontSize: 13,
                          fontWeight: 600,
                          color: COLOR.text,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {c.name}
                      </span>
                      <span
                        style={{
                          fontSize: 11,
                          color: COLOR.textSubtle,
                          flexShrink: 0,
                        }}
                      >
                        {c.country}
                      </span>
                    </div>
                    <span
                      style={{
                        fontSize: 13,
                        fontWeight: 700,
                        color: COLOR.text,
                        flexShrink: 0,
                      }}
                    >
                      ${c.amount}万
                    </span>
                  </div>
                  <div
                    style={{
                      width: '100%',
                      height: 6,
                      borderRadius: 3,
                      background: COLOR.graySoft,
                    }}
                  >
                    <div
                      style={{
                        width: `${c.pct}%`,
                        height: 6,
                        borderRadius: 3,
                        background: COLOR.primary,
                      }}
                    />
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* 最近动态 */}
        <div style={{ ...cardStyle, flex: 1, gap: 4 }}>
          <CardHeader title="最近动态" />
          {activities.map((a) => (
            <div
              key={a.time + a.who}
              style={{
                display: 'flex',
                gap: 12,
                alignItems: 'center',
                padding: '8px 0',
              }}
            >
              <div
                style={{
                  width: 28,
                  height: 28,
                  borderRadius: 14,
                  background: a.color,
                  color: '#FFFFFF',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 12,
                  fontWeight: 700,
                  flexShrink: 0,
                }}
              >
                {a.who.slice(0, 1)}
              </div>
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 2,
                  minWidth: 0,
                }}
              >
                <span style={{ fontSize: 13, color: COLOR.text }}>
                  {a.who} {a.action}
                </span>
                <span style={{ fontSize: 12, color: COLOR.textSubtle }}>
                  {a.time}
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;
