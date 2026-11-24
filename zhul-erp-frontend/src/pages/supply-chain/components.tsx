import { Skeleton } from 'antd';
import dayjs from 'dayjs';
import {
  Calendar,
  ClipboardCheck,
  Compass,
  FileText,
  RefreshCw,
  Repeat,
  ShieldCheck,
} from 'lucide-react';
import React, { useEffect, useState } from 'react';
import EllipsisText from '@/components/EllipsisText';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import {
  type BrandOption,
  brandApi,
  type Category,
  categoryApi,
} from '@/pages/product/service';
import {
  type StepStyle,
  useStepStyle,
} from '@/pages/sales/followup/boardStyle';
import { Arrow } from '@/pages/sales/followup/FlowChain';
import { categoryGroups } from '@/pages/supplier/productScope';
import { getUserOptions } from '@/services/zhul/users';
import { useAppTheme } from '@/theme/AppTheme';
import {
  COVER_NAMES,
  type Demand,
  HEAT_NAMES,
  LEVEL_NAMES,
  type Overview,
  PRIORITY_NAMES,
} from './service';

export { Card, Pill };

type Tone =
  | 'gray'
  | 'mute'
  | 'cyan'
  | 'accent'
  | 'violet'
  | 'orange'
  | 'green'
  | 'red';

const COVER_TONE: Record<number, Tone> = {
  1: 'red',
  2: 'cyan',
  3: 'orange',
  4: 'green',
};

/** 覆盖状态颜色（条形、矩阵格子） */
export const useCoverColors = () => {
  const { palette } = useAppTheme();
  return {
    1: palette.red,
    2: palette.info,
    3: palette.orange,
    4: palette.green,
  } as Record<number, string>;
};

export const CoverPill: React.FC<{ status?: number }> = ({ status }) =>
  status ? (
    <Pill tone={COVER_TONE[status] ?? 'gray'}>{COVER_NAMES[status]}</Pill>
  ) : (
    <Pill tone="mute">未进入矩阵</Pill>
  );

/** 供应商开发的需求热度 */
export const HeatPill: React.FC<{ value: number }> = ({ value }) => (
  <Pill tone={value === 3 ? 'red' : value === 2 ? 'orange' : 'gray'}>
    {HEAT_NAMES[value]}
  </Pill>
);

export const PriorityPill: React.FC<{ value?: number }> = ({ value }) =>
  value ? (
    <Pill tone={value === 3 ? 'red' : value === 2 ? 'orange' : 'gray'}>
      {PRIORITY_NAMES[value]}
    </Pill>
  ) : null;

export const LevelPill: React.FC<{ value?: number }> = ({ value }) =>
  value ? <Pill tone="gray">{LEVEL_NAMES[value]}</Pill> : null;

/** 四档分布的彩色条 + 「无渠道 2 · 未验证 0 · 需确认 3 · 可报 7」 */
export const CoverBar: React.FC<{
  coverage: Record<number, number>;
  width?: number | string;
  legend?: boolean;
}> = ({ coverage, width = 180, legend = true }) => {
  const { palette } = useAppTheme();
  const colors = useCoverColors();
  const total = [1, 2, 3, 4].reduce((s, k) => s + (coverage[k] ?? 0), 0);
  return (
    <div style={{ display: 'grid', gap: 4 }}>
      <div style={{ display: 'flex', gap: 3, width }}>
        {total === 0 ? (
          <span
            style={{
              flex: 1,
              height: 5,
              borderRadius: 3,
              background: palette.hairline,
            }}
          />
        ) : (
          [1, 2, 3, 4]
            .filter((k) => coverage[k])
            .map((k) => (
              <span
                key={k}
                style={{
                  flex: coverage[k],
                  height: 5,
                  borderRadius: 3,
                  background: colors[k],
                }}
              />
            ))
        )}
      </div>
      {legend && (
        <span style={{ fontSize: 12, color: palette.mute }}>
          无渠道 {coverage[1] ?? 0} · 未验证 {coverage[2] ?? 0} · 需确认{' '}
          {coverage[3] ?? 0} · 可报 {coverage[4] ?? 0}
        </span>
      )}
    </div>
  );
};

/** 日期：逾期标红「逾期 N 天」，今天标橙 */
export const DueText: React.FC<{ date?: string; prefix?: boolean }> = ({
  date,
  prefix,
}) => {
  const { palette } = useAppTheme();
  if (!date) return <span style={{ color: palette.red }}>未安排</span>;
  const days = dayjs().startOf('day').diff(dayjs(date), 'day');
  const md = dayjs(date).format('MM-DD');
  if (days > 0)
    return (
      <span style={{ color: palette.red }}>
        {prefix ? `${md} · ` : ''}逾期 {days} 天
      </span>
    );
  if (days === 0) return <span style={{ color: palette.orange }}>今天</span>;
  return <span style={{ color: palette.mute }}>{md}</span>;
};

/**
 * 下一步行动 + 日期（两行，2026-11-24 用户选定方案 B）：行动单行省略、悬停看全文，再长也不折行撑高（D21）；
 * 系统生成的行动在第二行日期前写「系统 ·」，不挤正文
 */
export const NextCell: React.FC<{ d: Demand }> = ({ d }) => {
  const { palette } = useAppTheme();
  const second: React.CSSProperties = {
    fontSize: 12,
    lineHeight: '18px',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  };
  if (d.sourcingTask && !d.nextAction)
    return (
      <div style={{ minWidth: 0 }}>
        <EllipsisText text={`等询价任务 ${d.sourcingTask} 回价`} />
        <div style={{ ...second, color: palette.mute }}>回价后自动登记</div>
      </div>
    );
  if (!d.nextAction)
    return (
      <div style={{ minWidth: 0 }}>
        <span style={{ color: palette.mute }}>—</span>
        <div style={second}>
          <DueText />
        </div>
      </div>
    );
  return (
    <div style={{ minWidth: 0 }}>
      <EllipsisText text={d.nextAction} />
      <div style={second}>
        {d.nextActionSource === 2 && (
          <span style={{ color: palette.mute }}>系统 · </span>
        )}
        <DueText date={d.nextActionDate} prefix />
      </div>
    </div>
  );
};

/** 最近证据：「询价 11-01 · 有效」/「询价 09-29 · 已过期」/ 没有价格记录 */
export const EvidenceText: React.FC<{ d: Demand }> = ({ d }) => {
  const { palette } = useAppTheme();
  if (d.sourcingTask && !d.evidenceDate)
    return <span style={{ color: palette.link }}>询价中</span>;
  if (!d.evidenceDate)
    return (
      <span style={{ color: palette.mute }}>
        {d.cntStockout ? '只有无货记录' : '没有价格记录'}
      </span>
    );
  return (
    <span style={{ color: d.evidenceValid ? palette.sub : palette.orange }}>
      {d.coverageBasis?.startsWith('下单') ? '下单' : '询价'}{' '}
      {dayjs(d.evidenceDate).format('MM-DD')} ·{' '}
      {d.evidenceValid ? '有效' : '已过期'}
    </span>
  );
};

// ---------------------------------------------------------------- 顶部链路

/** 五个板块（与作战地图一致），第 4–5 步未上线；颜色按步骤取，与客户跟进同一套 */
const BOARDS = [
  { key: 'intel', name: '需求情报', icon: <Compass size="1em" /> },
  { key: 'cover', name: '供应覆盖', icon: <ShieldCheck size="1em" /> },
  { key: 'dev', name: '供应商开发', icon: <ClipboardCheck size="1em" /> },
  { key: 'order', name: '报价与订单', icon: <FileText size="1em" /> },
  { key: 'review', name: '复盘补全', icon: <RefreshCw size="1em" /> },
];

/** 板块的颜色（图形用色、文字色等）：按板块所在的步骤取 */
export const useSupplyBoardStyle = (): ((key: string) => StepStyle) => {
  const step = useStepStyle();
  return (key: string) => step(BOARDS.findIndex((b) => b.key === key) + 1 || 1);
};

export type FlowView = 'today' | 'intel' | 'cover' | 'dev';

const StageCard: React.FC<{
  b: (typeof BOARDS)[number];
  step: number;
  active: boolean;
  count?: number;
  unit?: string;
  badge?: React.ReactNode;
  sub?: string;
  onSelect?: () => void;
}> = ({ b, step, active, count, unit = '个型号', badge, sub, onSelect }) => {
  const { palette } = useAppTheme();
  const s = useStepStyle()(step);
  const open = !!onSelect;
  return (
    <button
      type="button"
      disabled={!open}
      title={open ? undefined : '即将上线'}
      onClick={onSelect}
      style={{
        all: 'unset',
        boxSizing: 'border-box',
        flex: '1 1 0',
        minWidth: 150,
        display: 'grid',
        alignContent: 'start',
        gap: 6,
        padding: '12px 14px',
        borderRadius: 14,
        cursor: open ? 'pointer' : 'not-allowed',
        background: active ? s.soft : palette.card,
        border: `1px solid ${active ? s.color : palette.hairline}`,
        transition: 'background .15s, border-color .15s',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <span
            style={{
              width: 26,
              height: 26,
              borderRadius: 8,
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: s.text,
              // 选中时卡片已是浅底，图标底块改白，免得融成一片
              background: active ? palette.card : s.soft,
            }}
          >
            {b.icon}
          </span>
          <b
            style={{
              fontSize: 14,
              whiteSpace: 'nowrap',
              color: active ? s.text : palette.ink,
            }}
          >
            {b.name}
          </b>
        </span>
        <span
          style={{
            padding: '1px 8px',
            borderRadius: 10,
            fontSize: 11,
            fontWeight: 600,
            whiteSpace: 'nowrap',
            color: active ? s.badgeInk : s.text,
            background: active ? s.badge : s.soft,
          }}
        >
          第 {step} 步
        </span>
      </div>
      {open ? (
        <>
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              alignItems: 'flex-end',
              gap: 4,
            }}
          >
            <span
              style={{
                fontSize: 26,
                fontWeight: 700,
                lineHeight: 1.1,
                color: palette.ink,
              }}
            >
              {count}
            </span>
            <span
              style={{ fontSize: 12, color: palette.sub, whiteSpace: 'nowrap' }}
            >
              {unit}
            </span>
            <span style={{ flex: 1 }} />
            {badge}
          </div>
          <div
            style={{ fontSize: 12, color: palette.sub, whiteSpace: 'nowrap' }}
          >
            {sub}
          </div>
        </>
      ) : (
        <>
          <div style={{ fontSize: 26, fontWeight: 700, color: palette.faint }}>
            —
          </div>
          <div style={{ fontSize: 12, color: palette.mute }}>即将上线</div>
        </>
      )}
    </button>
  );
};

/** 卡片下方贯穿五步的进度轨道，末尾「复盘回到第 1 步」；与客户跟进同一套画法 */
const Track: React.FC<{ counts: (number | undefined)[] }> = ({ counts }) => {
  const style = useStepStyle();
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
      {BOARDS.map((b, i) => {
        const s = style(i + 1);
        const next = BOARDS[i + 1] ? style(i + 2) : undefined;
        return (
          <React.Fragment key={b.key}>
            <div
              style={{
                flex: '1 1 0',
                minWidth: next ? 0 : 'auto',
                display: 'flex',
                alignItems: 'center',
                gap: 6,
              }}
            >
              <span
                style={{
                  boxSizing: 'border-box',
                  width: 22,
                  height: 22,
                  borderRadius: 11,
                  flexShrink: 0,
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 11,
                  fontWeight: 700,
                  // 和卡片上的「第 N 步」同一套：浅底、板块色描边、加深的数字
                  color: s.text,
                  background: s.soft,
                  border: `1.5px solid ${s.color}`,
                }}
              >
                {i + 1}
              </span>
              <span
                style={{
                  fontSize: 12,
                  fontWeight: 600,
                  color: s.text,
                  whiteSpace: 'nowrap',
                }}
              >
                {b.name} {counts[i] ?? ''}
              </span>
              <span
                style={{
                  flex: 1,
                  minWidth: 12,
                  height: 6,
                  borderRadius: 3,
                  background: s.color,
                }}
              />
              {!next && (
                <span
                  style={{
                    fontSize: 12,
                    color: s.text,
                    whiteSpace: 'nowrap',
                    display: 'inline-flex',
                    gap: 4,
                  }}
                >
                  <Repeat size="1em" /> 复盘回到第 1 步
                </span>
              )}
            </div>
            {next && (
              <span
                style={{
                  width: 42,
                  height: 6,
                  flexShrink: 0,
                  background: next.color,
                }}
              />
            )}
          </React.Fragment>
        );
      })}
    </div>
  );
};

/** 顶部链路：今日待办 + 作战地图五个板块，已开放需求情报、供应覆盖、供应商开发 */
export const SupplyFlow: React.FC<{
  overview?: Overview;
  /** 概览取数失败的原因，有值时显示失败与重试（audit UI-091） */
  error?: string;
  onRetry?: () => void;
  active: FlowView;
  onSelect: (v: FlowView) => void;
}> = ({ overview: o, error, onRetry, active, onSelect }) => {
  const { palette } = useAppTheme();
  if (!o)
    return (
      <Card style={{ padding: 20, marginBottom: 16 }}>
        {error ? (
          <ErrorHint
            message={`概览加载失败：${error}`}
            onRetry={() => onRetry?.()}
          />
        ) : (
          <Skeleton active paragraph={{ rows: 2 }} />
        )}
      </Card>
    );
  const today = active === 'today';
  const total = o.overdue + o.dueToday;
  return (
    <div style={{ overflowX: 'auto', marginBottom: 16 }}>
      <div style={{ display: 'flex', gap: 16, minWidth: 1080 }}>
        <button
          type="button"
          onClick={() => onSelect('today')}
          style={{
            all: 'unset',
            boxSizing: 'border-box',
            width: 180,
            flexShrink: 0,
            alignSelf: 'flex-start',
            display: 'grid',
            gap: 6,
            padding: '14px 18px',
            borderRadius: 14,
            cursor: 'pointer',
            color: today ? '#fff' : palette.ink,
            background: today ? palette.primary : palette.card,
            border: `1px solid ${today ? palette.primary : palette.hairline}`,
          }}
        >
          <b
            style={{
              fontSize: 14,
              color: today ? '#fff' : palette.link,
              display: 'inline-flex',
              gap: 6,
            }}
          >
            <Calendar size="1em" /> 今日待办
          </b>
          <span>
            <span style={{ fontSize: 28, fontWeight: 700 }}>{total}</span>
            <span style={{ fontSize: 12, marginLeft: 4 }}>项</span>
          </span>
          <span
            style={{
              fontSize: 12,
              color: today ? '#fff' : palette.sub,
              opacity: today ? 0.85 : 1,
            }}
          >
            逾期 {o.overdue} · 今天 {o.dueToday}
          </span>
        </button>
        <span style={{ width: 1, background: palette.hairline }} />
        <div
          style={{
            flex: 1,
            minWidth: 0,
            display: 'grid',
            gridTemplateColumns: 'minmax(0, 1fr)',
            gap: 10,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'stretch', gap: 4 }}>
            {BOARDS.map((b, i) => (
              <React.Fragment key={b.key}>
                {i > 0 && <Arrow />}
                {b.key === 'intel' ? (
                  <StageCard
                    b={b}
                    step={1}
                    active={active === 'intel'}
                    count={o.intelTotal}
                    sub={`待归类 ${o.unclassified} · 观察中 ${o.watching} · 待复核 ${o.review}`}
                    onSelect={() => onSelect('intel')}
                  />
                ) : b.key === 'cover' ? (
                  <StageCard
                    b={b}
                    step={2}
                    active={active === 'cover'}
                    count={o.coverTotal}
                    badge={
                      o.stockoutOpen > 0 && (
                        <Pill tone="red">无货 {o.stockoutOpen}</Pill>
                      )
                    }
                    sub={`无渠道 ${o.coverage[1] ?? 0} · 未验证 ${o.coverage[2] ?? 0} · 需确认 ${o.coverage[3] ?? 0} · 可报 ${o.coverage[4] ?? 0}`}
                    onSelect={() => onSelect('cover')}
                  />
                ) : b.key === 'dev' && o.dev ? (
                  <StageCard
                    b={b}
                    step={3}
                    active={active === 'dev'}
                    count={o.dev.gapGroups}
                    unit="个品牌品类"
                    badge={
                      o.dev.gapTotal > 0 && (
                        <Pill tone="red">缺 {o.dev.gapTotal} 家</Pill>
                      )
                    }
                    sub={`待开发 ${o.dev.todo} · 开发中 ${o.dev.doing} · 已达标 ${o.dev.done}`}
                    onSelect={() => onSelect('dev')}
                  />
                ) : (
                  <StageCard b={b} step={i + 1} active={false} />
                )}
              </React.Fragment>
            ))}
          </div>
          <Track counts={[o.intelTotal, o.coverTotal, o.dev?.gapGroups]} />
        </div>
      </div>
    </div>
  );
};

// ---------------------------------------------------------------- 选项

/** 品牌、细分品类选项缓存 5 分钟（与字典缓存一致），新建的品牌、品类过期后能看到 */
const OPTION_TTL = 5 * 60 * 1000;

/** 品牌、细分品类选项，各页面共享加载；取数失败不缓存，下次进页面重取（audit UI-092） */
let optionCache: {
  at: number;
  data: Promise<{ brands: BrandOption[]; tree: Category[] }>;
} | null = null;

const loadOptions = () => {
  let failed = false;
  const soft = <T,>(r: Promise<T[]>) =>
    r.catch(() => {
      failed = true;
      return [] as T[];
    });
  const entry = {
    at: Date.now(),
    data: Promise.all([
      soft(brandApi.options()),
      soft(categoryApi.tree()),
    ]).then(([b, t]) => {
      if (failed && optionCache === entry) optionCache = null;
      return { brands: b, tree: t };
    }),
  };
  optionCache = entry;
  return entry.data;
};

export const useOptions = () => {
  const [brands, setBrands] = useState<{ value: number; label: string }[]>([]);
  const [tree, setTree] = useState<Category[]>([]);
  useEffect(() => {
    let alive = true;
    const data =
      optionCache && Date.now() - optionCache.at < OPTION_TTL
        ? optionCache.data
        : loadOptions();
    data.then(({ brands: b, tree: t }) => {
      if (!alive) return;
      setBrands(b.map((x) => ({ value: x.id, label: x.brandName })));
      setTree(t);
    });
    return () => {
      alive = false;
    };
  }, []);
  return {
    brands,
    tree,
    /** 细分品类按一级分组 */
    categories: categoryGroups(tree),
    /** 一级品类 */
    tops: tree.map((t) => ({
      value: t.id,
      label: t.categoryNameZh || t.categoryName,
    })),
  };
};

export const useUsers = () => {
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  useEffect(() => {
    getUserOptions({ status: 1 })
      .then((r) => setUsers(r.map((u) => ({ value: u.id, label: u.name }))))
      .catch(() => setUsers([]));
  }, []);
  return users;
};
