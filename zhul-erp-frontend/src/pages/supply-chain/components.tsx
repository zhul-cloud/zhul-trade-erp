import {
  AuditOutlined,
  CalendarOutlined,
  CompassOutlined,
  FileTextOutlined,
  RetweetOutlined,
  SafetyCertificateOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import { Skeleton } from 'antd';
import dayjs from 'dayjs';
import React, { useEffect, useState } from 'react';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import {
  type BrandOption,
  brandApi,
  type Category,
  categoryApi,
} from '@/pages/product/service';
import { Arrow } from '@/pages/sales/followup/FlowChain';
import { categoryGroups } from '@/pages/supplier/productScope';
import { getUserList } from '@/pages/system/user/service';
import { useAppTheme } from '@/theme/AppTheme';
import {
  COVER_NAMES,
  type Demand,
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
    2: palette.cyan,
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

export const PriorityPill: React.FC<{ value?: number }> = ({ value }) =>
  value ? (
    <Pill tone={value === 3 ? 'red' : value === 2 ? 'orange' : 'gray'}>
      {PRIORITY_NAMES[value]}
    </Pill>
  ) : null;

export const LevelPill: React.FC<{ value?: number }> = ({ value }) =>
  value ? <Pill tone="accent">{LEVEL_NAMES[value]}</Pill> : null;

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

/** 下一步行动 + 日期（两行） */
export const NextCell: React.FC<{ d: Demand }> = ({ d }) => {
  const { palette } = useAppTheme();
  if (d.sourcingTask && !d.nextAction)
    return (
      <div style={{ display: 'grid', gap: 2 }}>
        <span>等询价任务 {d.sourcingTask} 回价</span>
        <span style={{ fontSize: 12, color: palette.mute }}>
          回价后自动登记
        </span>
      </div>
    );
  if (!d.nextAction)
    return (
      <div style={{ display: 'grid', gap: 2 }}>
        <span style={{ color: palette.mute }}>—</span>
        <span style={{ fontSize: 12 }}>
          <DueText />
        </span>
      </div>
    );
  return (
    <div style={{ display: 'grid', gap: 2 }}>
      <span>
        {d.nextAction}
        {d.nextActionSource === 2 && (
          <span style={{ color: palette.mute }}>（系统）</span>
        )}
      </span>
      <span style={{ fontSize: 12 }}>
        <DueText date={d.nextActionDate} prefix />
      </span>
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

/** 五个板块（与作战地图一致），第 3–5 步本期未上线 */
const BOARDS = [
  {
    key: 'intel',
    name: '需求情报',
    color: '#7E22CE',
    icon: <CompassOutlined />,
  },
  {
    key: 'cover',
    name: '供应覆盖',
    color: '#4338CA',
    icon: <SafetyCertificateOutlined />,
  },
  { key: 'dev', name: '供应商开发', color: '#1D4ED8', icon: <AuditOutlined /> },
  {
    key: 'order',
    name: '报价与订单',
    color: '#BE185D',
    icon: <FileTextOutlined />,
  },
  { key: 'review', name: '复盘补全', color: '#15803D', icon: <SyncOutlined /> },
];
export const BOARD_COLOR = Object.fromEntries(
  BOARDS.map((b) => [b.key, b.color]),
);
export const TODAY_COLOR = '#4F46E5';

export type FlowView = 'today' | 'intel' | 'cover';

const StageCard: React.FC<{
  b: (typeof BOARDS)[number];
  step: number;
  active: boolean;
  count?: number;
  badge?: React.ReactNode;
  sub?: string;
  onSelect?: () => void;
}> = ({ b, step, active, count, badge, sub, onSelect }) => {
  const { palette } = useAppTheme();
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
        background: active ? `${b.color}14` : palette.card,
        border: `1px solid ${active ? b.color : palette.hairline}`,
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
              color: b.color,
              background: `${b.color}1F`,
            }}
          >
            {b.icon}
          </span>
          <b
            style={{
              fontSize: 14,
              whiteSpace: 'nowrap',
              color: active ? b.color : palette.ink,
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
            color: active ? '#fff' : b.color,
            background: active ? b.color : `${b.color}1F`,
          }}
        >
          第 {step} 步
        </span>
      </div>
      {open ? (
        <>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 4 }}>
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
            <span style={{ fontSize: 12, color: palette.sub }}>个型号</span>
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

const Track: React.FC<{ counts: (number | undefined)[] }> = ({ counts }) => (
  <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
    {BOARDS.map((b, i) => {
      const next = BOARDS[i + 1];
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
                width: 22,
                height: 22,
                borderRadius: 11,
                flexShrink: 0,
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 11,
                fontWeight: 700,
                color: '#fff',
                background: b.color,
              }}
            >
              {i + 1}
            </span>
            <span
              style={{
                fontSize: 12,
                fontWeight: 600,
                color: b.color,
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
                background: `linear-gradient(90deg, ${b.color}, ${(next ?? b).color})`,
              }}
            />
            {!next && (
              <span
                style={{
                  fontSize: 12,
                  color: b.color,
                  whiteSpace: 'nowrap',
                  display: 'inline-flex',
                  gap: 4,
                }}
              >
                <RetweetOutlined /> 复盘回到第 1 步
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

/** 顶部链路：今日待办 + 作战地图五个板块，本期开放需求情报、供应覆盖 */
export const SupplyFlow: React.FC<{
  overview?: Overview;
  active: FlowView;
  onSelect: (v: FlowView) => void;
}> = ({ overview: o, active, onSelect }) => {
  const { palette } = useAppTheme();
  if (!o)
    return (
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <Skeleton active paragraph={{ rows: 2 }} />
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
            background: today ? TODAY_COLOR : palette.card,
            border: `1px solid ${today ? TODAY_COLOR : palette.hairline}`,
          }}
        >
          <b
            style={{
              fontSize: 14,
              color: today ? '#fff' : TODAY_COLOR,
              display: 'inline-flex',
              gap: 6,
            }}
          >
            <CalendarOutlined /> 今日待办
          </b>
          <span>
            <span style={{ fontSize: 28, fontWeight: 700 }}>{total}</span>
            <span style={{ fontSize: 12, marginLeft: 4 }}>个型号</span>
          </span>
          <span
            style={{
              fontSize: 12,
              color: today ? 'rgba(255,255,255,0.85)' : palette.sub,
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
                {i > 0 && <Arrow from={BOARDS[i - 1].color} to={b.color} />}
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
                ) : (
                  <StageCard b={b} step={i + 1} active={false} />
                )}
              </React.Fragment>
            ))}
          </div>
          <Track counts={[o.intelTotal, o.coverTotal]} />
        </div>
      </div>
    </div>
  );
};

// ---------------------------------------------------------------- 选项

/** 品牌、细分品类与人员选项，页面内共享一次加载 */
let optionCache: Promise<{
  brands: BrandOption[];
  tree: Category[];
}> | null = null;

export const useOptions = () => {
  const [brands, setBrands] = useState<{ value: number; label: string }[]>([]);
  const [tree, setTree] = useState<Category[]>([]);
  useEffect(() => {
    optionCache ??= Promise.all([
      brandApi.options().catch(() => [] as BrandOption[]),
      categoryApi.tree().catch(() => [] as Category[]),
    ]).then(([b, t]) => ({ brands: b, tree: t }));
    optionCache.then(({ brands: b, tree: t }) => {
      setBrands(b.map((x) => ({ value: x.id, label: x.brandName })));
      setTree(t);
    });
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
    getUserList({ current: 1, pageSize: 200, status: 1 })
      .then((r) =>
        setUsers(r.data.records.map((u) => ({ value: u.id, label: u.name }))),
      )
      .catch(() => setUsers([]));
  }, []);
  return users;
};
