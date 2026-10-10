import { useLocation } from '@umijs/max';
import { Breadcrumb, Grid } from 'antd';
import dayjs from 'dayjs';
import React from 'react';
import { Card, Pill } from '@/pages/crm/opportunity/components';
import { useAppTheme } from '@/theme/AppTheme';
import {
  DICT_INQUIRY_LEVEL,
  DICT_ITEM_CONDITION,
  DICT_LEAD_TIME,
  DICT_LIFECYCLE,
  DICT_TAX_RATE,
  useDictOptions,
} from '@/utils/dict';
import { formatAmount } from '@/utils/format';
import { menuGroupOf } from '@/utils/menuGroup';
import {
  channelLabel,
  conditionTone,
  STATUS_META,
  TASK_STATUS_META,
} from './constants';
import type { PriceRecord } from './service';

export { Card, FileChip, Pill } from '@/pages/crm/opportunity/components';

/** 宽屏（≥1200px）左右两栏，窄屏上下排列 */
export const useWide = () => !!Grid.useBreakpoint().xl;

/** 面包屑「分组 / …」+ 标题 + 右侧操作；root 为面包屑第一级（默认按当前页面路径取侧边栏分组） */
export const PageTitle: React.FC<{
  crumbs: React.ReactNode[];
  title: React.ReactNode;
  description?: React.ReactNode;
  actions?: React.ReactNode;
  root?: string;
}> = ({ crumbs, title, description, actions, root }) => {
  const { palette } = useAppTheme();
  const { pathname } = useLocation();
  const group = root ?? menuGroupOf(pathname);
  return (
    <header
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-end',
        gap: 16,
        marginBottom: 20,
        flexWrap: 'wrap',
      }}
    >
      <div style={{ minWidth: 0 }}>
        <Breadcrumb
          items={[group, ...crumbs].map((c) => ({ title: c }))}
          style={{ fontSize: 13 }}
        />
        <h1
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            flexWrap: 'wrap',
            fontSize: 28,
            fontWeight: 700,
            margin: '8px 0 0',
            color: palette.ink,
          }}
        >
          {title}
        </h1>
        {description && (
          <div style={{ margin: '6px 0 0', color: palette.sub, fontSize: 14 }}>
            {description}
          </div>
        )}
      </div>
      {actions && (
        <div
          style={{
            display: 'flex',
            gap: 12,
            alignItems: 'center',
            flexWrap: 'wrap',
          }}
        >
          {actions}
        </div>
      )}
    </header>
  );
};

export const StatusPill: React.FC<{ status: number }> = ({ status }) => {
  const m = STATUS_META[status] ?? { label: '未知', tone: 'gray' as const };
  return (
    <Pill tone={m.tone} dot>
      {m.label}
    </Pill>
  );
};

export const TaskStatusPill: React.FC<{
  status: number;
  timeout?: boolean;
}> = ({ status, timeout }) => {
  if (timeout) {
    return (
      <Pill tone="red" dot>
        超时
      </Pill>
    );
  }
  const m = TASK_STATUS_META[status] ?? {
    label: '未知',
    tone: 'gray' as const,
  };
  return (
    <Pill tone={m.tone} dot>
      {m.label}
    </Pill>
  );
};

export const CustomerTypePill: React.FC<{ type: number }> = ({ type }) => (
  <Pill tone={type === 2 ? 'violet' : 'cyan'}>
    {type === 2 ? '老客户' : '新客户'}
  </Pill>
);

/** 货况、货期字典：下拉只含启用项，名称含停用项；码值 0 为未填 */
export const useQuoteDicts = () => {
  const condition = useDictOptions(DICT_ITEM_CONDITION, '');
  const leadTime = useDictOptions(DICT_LEAD_TIME, '');
  return {
    conditionOptions: condition.options,
    conditionLabel: (v?: number | null) => (v ? condition.labelOf(v) : ''),
    leadTimeOptions: leadTime.options,
    leadTimeLabel: (v?: number | null) => (v ? leadTime.labelOf(v) : ''),
  };
};

export const ConditionPill: React.FC<{ value?: number }> = ({ value }) => {
  const { conditionLabel } = useQuoteDicts();
  return value ? (
    <Pill tone={conditionTone(value)}>{conditionLabel(value)}</Pill>
  ) : null;
};

/** 询价平台 · 店铺：供应链内部信息，没有「查看货源信息」权限时接口不返回，这里也不显示 */
export const SupplierText: React.FC<{
  channel?: number | null;
  shopName?: string | null;
}> = ({ channel, shopName }) =>
  channel == null ? null : (
    <span>
      {channelLabel(channel)} · {shopName || '—'}
    </span>
  );

/** 询盘等级字典：选项只含启用项，名称含停用项 */
export const useLevels = () => {
  const { options, labelOf } = useDictOptions(DICT_INQUIRY_LEVEL, '');
  return { levelOptions: options, levelLabel: labelOf };
};

/** 询盘等级标签：码值越小越醒目（1-S 红、2-A 橙、3-B 蓝、其余灰） */
export const LevelPill: React.FC<{ value?: number | null }> = ({ value }) => {
  const { levelLabel } = useLevels();
  if (value == null) return null;
  const tone =
    value === 1
      ? 'red'
      : value === 2
        ? 'orange'
        : value === 3
          ? 'accent'
          : 'gray';
  return <Pill tone={tone}>{levelLabel(value) || '—'} 级</Pill>;
};

/** 客户摘要：新老客户标签 + 客户名称（兼职采购没有名称时只显示标签） */
export const CustomerBrief: React.FC<{
  customerType?: number;
  customerName?: string;
}> = ({ customerType, customerName }) => {
  const { palette } = useAppTheme();
  if (!customerType) return null;
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
      <CustomerTypePill type={customerType} />
      {customerName && (
        <span style={{ color: palette.sub, fontSize: 12 }}>{customerName}</span>
      )}
    </span>
  );
};

/** 询价税率字典（百分比整数） */
export const useTaxRates = () => {
  const { options } = useDictOptions(DICT_TAX_RATE, '');
  return { taxRateOptions: options };
};

/** 不含税价 = 含税价 ÷ (1 + 税率%)，两位小数四舍五入（与后端 HALF_UP 一致，仅用于录入时预览） */
export const excludeTax = (price: number, rate: number) =>
  Math.round((price / (1 + rate / 100)) * 100) / 100;

/** 含税报价的说明：含税价 CNY 113.00 · 13%；不含税报价不显示 */
export const TaxHint: React.FC<{
  taxIncluded?: boolean;
  taxRate?: number;
  unitPrice?: number | null;
}> = ({ taxIncluded, taxRate, unitPrice }) => {
  const { palette } = useAppTheme();
  if (!taxIncluded) return null;
  return (
    <span style={{ color: palette.mute, fontSize: 12 }}>
      含税价 {formatAmount(unitPrice)} · {Number(taxRate ?? 13)}%
    </span>
  );
};

/** 生命周期字典：下拉只含启用项，名称含停用项 */
export const useLifecycles = () => {
  const { options, labelOf } = useDictOptions(DICT_LIFECYCLE, '待查');
  return { lifecycleOptions: options, lifecycleLabel: labelOf };
};

/** 货期名称；未填显示 fallback */
export const LeadTimeText: React.FC<{ value?: number; fallback?: string }> = ({
  value,
  fallback = '—',
}) => {
  const { leadTimeLabel } = useQuoteDicts();
  return <>{leadTimeLabel(value) || fallback}</>;
};

/** 回价进度条：全部完成时变绿 */
export const Progress: React.FC<{
  done: number;
  total: number;
  width?: number;
}> = ({ done, total, width = 96 }) => {
  const { palette } = useAppTheme();
  const pct = total ? Math.min(1, done / total) : 0;
  const full = total > 0 && done >= total;
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
      <span
        style={{
          width,
          height: 6,
          borderRadius: 3,
          background: palette.hairline,
          overflow: 'hidden',
          display: 'inline-block',
        }}
      >
        <span
          style={{
            display: 'block',
            width: `${pct * 100}%`,
            height: '100%',
            borderRadius: 3,
            background: full ? palette.green : palette.link,
            transition: 'width 200ms ease-out',
          }}
        />
      </span>
      <span
        style={{
          fontSize: 12,
          fontWeight: 600,
          color: full ? palette.green : palette.sub,
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {done}/{total}
      </span>
    </span>
  );
};

/** 一条历史价格的摘要：CNY 4,120.00 · 全新原装 · 现货 */
export const PriceSummary: React.FC<{ value?: PriceRecord }> = ({
  value: q,
}) => {
  const { conditionLabel, leadTimeLabel } = useQuoteDicts();
  if (!q) return null;
  if (q.noStock) return <>无货</>;
  return (
    <>
      {[
        formatAmount(q.unitPriceCny),
        conditionLabel(q.itemCondition),
        leadTimeLabel(q.leadTime),
        q.taxIncluded
          ? `含税价 ${formatAmount(q.unitPrice)}（${Number(q.taxRate ?? 13)}%）`
          : '',
      ]
        .filter(Boolean)
        .join(' · ')}
    </>
  );
};

/** 距今天数的说法；超过 90 天提示偏旧 */
export const AgeText: React.FC<{ days?: number; date?: string }> = ({
  days,
  date,
}) => {
  const { palette } = useAppTheme();
  if (days == null) return null;
  const old = days > 90;
  return (
    <span style={{ fontSize: 12, color: old ? palette.orange : palette.mute }}>
      {date ? `${dayjs(date).format('YYYY-MM-DD')} · ` : ''}
      {days === 0 ? '今天' : `${days} 天前`}
    </span>
  );
};

/** 报价截止：当天标橙，过期标红（已报价及以后不提示） */
export const DeadlineText: React.FC<{ date: string; status: number }> = ({
  date,
  status,
}) => {
  const { palette } = useAppTheme();
  const diff = dayjs(date).startOf('day').diff(dayjs().startOf('day'), 'day');
  const active = status < 7;
  const color = !active
    ? palette.sub
    : diff < 0
      ? palette.red
      : diff === 0
        ? palette.orange
        : palette.sub;
  return (
    <span style={{ display: 'inline-flex', flexDirection: 'column' }}>
      <span style={{ color, fontVariantNumeric: 'tabular-nums' }}>{date}</span>
      {active && diff <= 0 && (
        <span style={{ fontSize: 11, color }}>
          {diff === 0 ? '今天截止' : `已过期 ${-diff} 天`}
        </span>
      )}
    </span>
  );
};

/** 统计卡：图标 + 标题 + 大数字 + 一行说明 */
export const Stat: React.FC<{
  icon: React.ReactNode;
  color: string;
  soft: string;
  label: string;
  value: React.ReactNode;
  hint: React.ReactNode;
}> = ({ icon, color, soft, label, value, hint }) => {
  const { palette } = useAppTheme();
  return (
    <Card style={{ padding: 20 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          color: palette.sub,
        }}
      >
        <span
          style={{
            width: 32,
            height: 32,
            borderRadius: 10,
            background: soft,
            color,
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          {icon}
        </span>
        {label}
      </div>
      <div
        style={{
          fontSize: 28,
          fontWeight: 700,
          color: palette.ink,
          margin: '12px 0 4px',
        }}
      >
        {value}
      </div>
      <div style={{ fontSize: 12 }}>{hint}</div>
    </Card>
  );
};
