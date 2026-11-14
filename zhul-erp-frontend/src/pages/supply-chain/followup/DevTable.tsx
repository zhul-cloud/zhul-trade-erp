import { DownOutlined, PlusOutlined, RightOutlined } from '@ant-design/icons';
import { Link } from '@umijs/max';
import { Button, Empty, Skeleton } from 'antd';
import dayjs from 'dayjs';
import React, { useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { DueText, HeatPill, Pill } from '../components';
import { candidateTone, evidenceText, otherQualified } from '../rules';
import {
  CAND_NAMES,
  CAND_SRC_NAMES,
  type Candidate,
  DEV_SOURCE_NAMES,
  type DevGroup,
  type DevGroupPage,
  type DevSupplier,
  PATHS,
  scopeName,
} from '../service';

const cols =
  '20px minmax(120px, 1.3fr) 44px 92px 52px 84px minmax(110px, 1.3fr) minmax(110px, 1.3fr) 56px 96px';

/** 「1 / 3 ●○○」+ 缺口 */
export const QualifiedDots: React.FC<{
  qualified: number;
  required: number;
  gap: number;
}> = ({ qualified, required, gap }) => {
  const { palette } = useAppTheme();
  return (
    <span style={{ display: 'grid', gap: 2 }}>
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
        <b>
          {qualified} / {required}
        </b>
        <span style={{ display: 'inline-flex', gap: 3 }}>
          {Array.from({ length: required }, (_, i) => (
            <span
              // biome-ignore lint/suspicious/noArrayIndexKey: 固定位置的圆点
              key={i}
              style={{
                width: 8,
                height: 8,
                borderRadius: 4,
                background: i < qualified ? palette.green : 'transparent',
                border: `1px solid ${i < qualified ? palette.green : palette.mute}`,
              }}
            />
          ))}
        </span>
      </span>
      <span style={{ fontSize: 12, color: gap ? palette.red : palette.green }}>
        {gap ? `缺 ${gap} 家` : '已达标'}
      </span>
    </span>
  );
};

const Head: React.FC = () => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: cols,
        gap: 10,
        padding: '10px 16px',
        fontSize: 13,
        color: palette.mute,
        borderBottom: `1px solid ${palette.hairline}`,
      }}
    >
      <span />
      <span>品牌 · 品类</span>
      <span>热度</span>
      <span>合格 / 要求</span>
      <span>线索</span>
      <span>近 30 天无货</span>
      <span>开发任务</span>
      <span>最紧急的下一步</span>
      <span>负责人</span>
      <span />
    </div>
  );
};

const CandidateRow: React.FC<{
  c: Candidate;
  canEdit: boolean;
  onRecord: () => void;
}> = ({ c, canEdit, onRecord }) => {
  const { palette } = useAppTheme();
  const closed = c.stage === 4 || c.stage === 5;
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns:
          '20px minmax(200px, 1.4fr) 110px minmax(200px, 1.6fr) minmax(150px, 1.1fr) 64px 104px',
        gap: 10,
        alignItems: 'center',
        padding: '10px 16px',
      }}
    >
      <span />
      <span style={{ display: 'grid', gap: 2, paddingLeft: 16 }}>
        <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
          <b>{c.name ?? '候选供应商'}</b>
          {!c.supplierId && c.stage !== 5 && (
            <Pill tone="orange">未关联供应商库</Pill>
          )}
        </span>
        <span style={{ fontSize: 12, color: palette.mute }}>
          {[CAND_SRC_NAMES[c.source], c.contactName, c.contactInfo]
            .filter(Boolean)
            .join(' · ') || '—'}
        </span>
      </span>
      <span style={{ display: 'grid', gap: 2, justifyItems: 'start' }}>
        <Pill tone={candidateTone(c.stage)}>{CAND_NAMES[c.stage]}</Pill>
        {c.stage === 4 && (
          <span style={{ fontSize: 12, color: palette.green }}>
            依据：{evidenceText(c.evidenceType, c.evidenceDate)}
          </span>
        )}
        {c.stage === 5 && c.abandonReason && (
          <span style={{ fontSize: 12, color: palette.mute }}>
            {c.abandonReason}
          </span>
        )}
      </span>
      <span style={{ display: 'grid', gap: 2 }}>
        <span>{c.lastAction || '—'}</span>
        {c.lastActionAt && (
          <span style={{ fontSize: 12, color: palette.mute }}>
            {dayjs(c.lastActionAt).format('MM-DD')} · {c.lastActionByName ?? ''}
          </span>
        )}
      </span>
      <span style={{ display: 'grid', gap: 2 }}>
        {closed || !c.nextAction ? (
          <span style={{ color: palette.mute }}>—</span>
        ) : (
          <>
            <span>{c.nextAction}</span>
            <span style={{ fontSize: 12 }}>
              <DueText date={c.nextActionDate} />
            </span>
          </>
        )}
        {c.linkReminder && (
          <span style={{ fontSize: 12, color: palette.orange }}>
            已报价超过 7 天，记得关联供应商库
          </span>
        )}
      </span>
      <span>{c.ownerName ?? '—'}</span>
      <span>
        {canEdit && (
          <Button type="link" size="small" onClick={onRecord}>
            记处理
          </Button>
        )}
      </span>
    </div>
  );
};

const SupplierLine: React.FC<{
  s: DevSupplier;
  canEdit: boolean;
  action: string;
  onAdd: () => void;
}> = ({ s, canEdit, action, onAdd }) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns:
          '20px minmax(200px, 1.4fr) minmax(200px, 2fr) 104px',
        gap: 10,
        alignItems: 'center',
        padding: '8px 16px',
      }}
    >
      <span />
      <b style={{ paddingLeft: 16 }}>{s.name}</b>
      <span style={{ fontSize: 13, color: palette.sub }}>
        {s.evidenceType
          ? `近 90 天有${evidenceText(s.evidenceType, s.evidenceDate)}`
          : '主营产品里登记了这个品牌品类，近 90 天没有报价或下单'}
      </span>
      <span>
        {canEdit && (
          <Button size="small" onClick={onAdd}>
            {action}
          </Button>
        )}
      </span>
    </div>
  );
};

export interface DevActions {
  canDev: boolean;
  canProject: boolean;
  canSeeSupplier: boolean;
  /** 建开发任务（或在已有专项上加开发目标） */
  onProject: (g: DevGroup) => void;
  /** 记处理：checkedIds 为默认勾选的候选 */
  onRecord: (g: DevGroup, checkedIds?: number[]) => void;
  /** 加候选：supplier 为空时手填 */
  onCandidate: (g: DevGroup, supplier?: DevSupplier) => void;
}

const GroupRow: React.FC<
  { g: DevGroup; open: boolean; onToggle: () => void } & DevActions
> = ({
  g,
  open,
  onToggle,
  canDev,
  canProject,
  canSeeSupplier,
  onProject,
  onRecord,
  onCandidate,
}) => {
  const { palette } = useAppTheme();
  const devTask = g.projectTarget != null;
  const others = otherQualified(g);
  const action = devTask ? (
    canDev && (
      <Button
        type="link"
        size="small"
        onClick={(e) => {
          e.stopPropagation();
          onRecord(g);
        }}
      >
        记处理
      </Button>
    )
  ) : g.gap > 0 && canProject ? (
    <Button
      type="primary"
      size="small"
      onClick={(e) => {
        e.stopPropagation();
        onProject(g);
      }}
    >
      {g.projectId ? '加开发目标' : '建开发任务'}
    </Button>
  ) : null;
  return (
    <div style={{ borderBottom: `1px solid ${palette.hairline}` }}>
      {/* biome-ignore lint/a11y/useSemanticElements: 行内含按钮与链接 */}
      <div
        role="button"
        tabIndex={0}
        onClick={onToggle}
        onKeyDown={(e) => e.key === 'Enter' && onToggle()}
        style={{
          display: 'grid',
          gridTemplateColumns: cols,
          gap: 10,
          alignItems: 'center',
          padding: '14px 16px',
          cursor: 'pointer',
          background: open ? palette.inset : undefined,
        }}
      >
        {open ? (
          <DownOutlined style={{ fontSize: 11 }} />
        ) : (
          <RightOutlined style={{ fontSize: 11 }} />
        )}
        <span style={{ display: 'grid', gap: 2 }}>
          <b>{scopeName(g)}</b>
          <span style={{ fontSize: 12, color: palette.mute }}>
            {DEV_SOURCE_NAMES[g.source]}
          </span>
        </span>
        <span>
          <HeatPill value={g.heat} />
        </span>
        <QualifiedDots
          qualified={g.qualified}
          required={g.required}
          gap={g.gap}
        />
        <span>{g.leads ? `${g.leads} 家` : '—'}</span>
        <span style={{ display: 'grid' }}>
          {g.stockoutModels ? (
            <>
              <span style={{ color: palette.red, fontWeight: 600 }}>
                {g.stockoutModels} 个型号
              </span>
              <span style={{ fontSize: 12, color: palette.mute }}>
                {g.stockoutTimes} 次
              </span>
            </>
          ) : (
            <span style={{ color: palette.mute }}>—</span>
          )}
        </span>
        <span style={{ fontSize: 13 }}>
          {g.projectId ? (
            <Link
              to={PATHS.project(g.projectId)}
              onClick={(e) => e.stopPropagation()}
              style={{ display: 'grid' }}
            >
              <span>{g.projectName}</span>
              <span style={{ fontSize: 12, color: palette.mute }}>
                {devTask
                  ? `合格 ${g.projectQualified} / 目标 ${g.projectTarget} · ${dayjs(g.projectDueDate).format('MM-DD')} 截止`
                  : '只管型号，还没有开发目标'}
              </span>
            </Link>
          ) : (
            <span style={{ color: palette.mute }}>还没有</span>
          )}
        </span>
        <span style={{ display: 'grid', gap: 2 }}>
          {g.projectOverdueDays > 0 ? (
            <span style={{ color: palette.red }}>
              开发任务已超期 {g.projectOverdueDays} 天
            </span>
          ) : (
            <span>{g.urgentAction ?? '—'}</span>
          )}
          {g.urgentDate && g.projectOverdueDays === 0 && (
            <span style={{ fontSize: 12 }}>
              <DueText date={g.urgentDate} />
            </span>
          )}
        </span>
        <span>{g.projectOwnerName ?? '—'}</span>
        <span>{action}</span>
      </div>
      {open && (
        <div style={{ paddingBottom: 8 }}>
          {devTask ? (
            <>
              <div style={sectionTitle(palette.mute)}>
                候选供应商 {g.candidates.length} 家
              </div>
              {g.candidates.map((c) => (
                <CandidateRow
                  key={c.id}
                  c={c}
                  canEdit={canDev}
                  onRecord={() => onRecord(g, [c.id])}
                />
              ))}
              <div
                style={{
                  padding: '6px 16px 6px 52px',
                  display: 'flex',
                  gap: 8,
                  alignItems: 'center',
                  flexWrap: 'wrap',
                  fontSize: 13,
                  color: palette.sub,
                }}
              >
                {canDev && (
                  <Button
                    type="link"
                    size="small"
                    icon={<PlusOutlined />}
                    style={{ padding: 0 }}
                    onClick={() => onCandidate(g)}
                  >
                    加候选
                  </Button>
                )}
                {others.length > 0 && (
                  <>
                    <span>·</span>
                    <span>
                      范围内另有 {others.length} 家合格供应商不是候选
                      {canSeeSupplier &&
                        `：${others.map((s) => s.name).join('、')}`}
                    </span>
                    {canDev &&
                      canSeeSupplier &&
                      others.map((s) => (
                        <Button
                          key={s.id}
                          type="link"
                          size="small"
                          style={{ padding: 0 }}
                          onClick={() => onCandidate(g, s)}
                        >
                          加 {s.name} 为候选
                        </Button>
                      ))}
                  </>
                )}
              </div>
            </>
          ) : (
            <>
              {g.qualified > 0 && (
                <div style={sectionTitle(palette.mute)}>
                  合格供应商 {g.qualified} 家
                  {canSeeSupplier &&
                    `：${g.qualifiedSuppliers
                      .map(
                        (s) =>
                          `${s.name}（${evidenceText(s.evidenceType, s.evidenceDate)}）`,
                      )
                      .join('、')}`}
                </div>
              )}
              <div style={sectionTitle(palette.mute)}>
                {g.leads
                  ? `线索供应商 ${g.leads} 家 · 在主营产品里登记了这个品牌品类，近 90 天没有报价或下单`
                  : '没有线索供应商，建开发任务后从搜索、展会、同行介绍里找候选'}
              </div>
              {canSeeSupplier &&
                g.leadSuppliers.map((s) => (
                  <SupplierLine
                    key={s.id}
                    s={s}
                    canEdit={canDev}
                    action="加为候选"
                    onAdd={() => onCandidate(g, s)}
                  />
                ))}
            </>
          )}
        </div>
      )}
    </div>
  );
};

const sectionTitle = (color: string): React.CSSProperties => ({
  padding: '10px 16px 4px 52px',
  fontSize: 13,
  color,
});

/** 供应商开发板块：按品牌 · 二级品类一组，开发中的展开候选，待开发的展开线索 */
export const DevTable: React.FC<
  {
    page?: DevGroupPage;
    loading: boolean;
    /** 默认展开的组（从矩阵、今日待办跳过来） */
    focusKey?: string;
    onMore: () => void;
  } & DevActions
> = ({ page, loading, focusKey, onMore, ...rest }) => {
  const { palette } = useAppTheme();
  const [toggled, setToggled] = useState<Set<string>>(new Set());
  if (!page) return <Skeleton active paragraph={{ rows: 8 }} />;
  const first = focusKey ?? page.groups[0]?.key;
  const isOpen = (key: string) => toggled.has(key) !== (key === first);
  const gap = page.groups.reduce((n, g) => n + g.gap, 0);
  return (
    <div style={{ overflowX: 'auto' }}>
      <div style={{ minWidth: 920 }}>
        <Head />
        {page.groups.length === 0 && !loading && (
          <Empty style={{ padding: 40 }} description="没有符合条件的品牌品类" />
        )}
        {page.groups.map((g) => (
          <GroupRow
            key={g.key}
            g={g}
            open={isOpen(g.key)}
            onToggle={() =>
              setToggled((s) => {
                const n = new Set(s);
                if (n.has(g.key)) n.delete(g.key);
                else n.add(g.key);
                return n;
              })
            }
            {...rest}
          />
        ))}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            padding: '12px 16px',
            color: palette.mute,
          }}
        >
          <span>
            共 {page.total} 个品牌品类
            {gap > 0 ? ` · 已显示的缺 ${gap} 家` : ''} ·{' '}
            {page.groups.length < page.total
              ? `已显示 ${page.groups.length} 个`
              : '已显示全部'}
          </span>
          {page.groups.length < page.total && (
            <Button type="link" loading={loading} onClick={onMore}>
              加载更多
            </Button>
          )}
        </div>
      </div>
    </div>
  );
};
