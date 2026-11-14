import { DownOutlined, RightOutlined } from '@ant-design/icons';
import { Link } from '@umijs/max';
import { Button, Checkbox, Empty, Skeleton } from 'antd';
import dayjs from 'dayjs';
import React, { useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import {
  CoverBar,
  CoverPill,
  DueText,
  LevelPill,
  NextCell,
  PriorityPill,
} from '../components';
import {
  type Demand,
  type Group,
  type GroupPage,
  modelName,
  PATHS,
  scopeName,
} from '../service';

/** 组内默认显示的型号数 */
const FOLD = 4;

const cols =
  '40px minmax(150px, 1.3fr) 56px 104px minmax(170px, 1.3fr) minmax(170px, 1.5fr) 72px minmax(110px, 1fr) 60px';

const Head: React.FC<{ cover: boolean }> = ({ cover }) => {
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
      <span>型号</span>
      <span>近 30 天无货</span>
      <span>{cover ? '覆盖状态' : '需求等级'}</span>
      <span>最急的下一步</span>
      <span>负责人</span>
      <span>{cover ? '专项' : '依据'}</span>
      <span />
    </div>
  );
};

const signalsText = (d: Demand) =>
  [
    d.cntStockout ? `无货 ${d.cntStockout} 次` : null,
    d.cntInquiry ? `询盘 ${d.cntInquiry} 次` : null,
    d.cntOrder ? `成交 ${d.cntOrder} 次` : null,
  ]
    .filter(Boolean)
    .join(' · ') || '—';

const ItemRow: React.FC<{
  d: Demand;
  cover: boolean;
  picked: boolean;
  selected: boolean;
  canEdit: boolean;
  onPick: (v: boolean) => void;
  onSelect: () => void;
  onRecord: () => void;
}> = ({ d, cover, picked, selected, canEdit, onPick, onSelect, onRecord }) => {
  const { palette } = useAppTheme();
  return (
    // biome-ignore lint/a11y/useSemanticElements: 行内含复选框与链接，不能用 button 包住
    <div
      role="button"
      tabIndex={0}
      onClick={onSelect}
      onKeyDown={(e) => e.key === 'Enter' && onSelect()}
      style={{
        display: 'grid',
        gridTemplateColumns: cols,
        gap: 10,
        alignItems: 'center',
        padding: '10px 16px',
        cursor: 'pointer',
        background: selected ? palette.inset : undefined,
      }}
    >
      <span onClick={(e) => e.stopPropagation()} onKeyDown={() => undefined}>
        <Checkbox checked={picked} onChange={(e) => onPick(e.target.checked)} />
      </span>
      <Link
        to={PATHS.demand(d.id)}
        onClick={(e) => e.stopPropagation()}
        style={{ paddingLeft: 24 }}
      >
        {modelName(d)}
      </Link>
      <span>
        <PriorityPill value={d.priority} />
      </span>
      <span
        style={{
          color: d.cntStockout ? palette.red : palette.sub,
          fontSize: 13,
        }}
      >
        {signalsText(d)}
      </span>
      <span
        style={{
          display: 'inline-flex',
          gap: 8,
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        {cover ? (
          <CoverPill status={d.coverageStatus} />
        ) : (
          <LevelPill value={d.demandLevel} />
        )}
        <span style={{ fontSize: 12, color: palette.mute }}>
          {cover
            ? d.coveragePending
              ? '待确认'
              : d.singleChannel
                ? '单一渠道'
                : d.coverageNote === '系统降级' ||
                    (d.coverageStatus === 3 &&
                      d.evidenceDate &&
                      !d.evidenceValid)
                  ? '系统降级'
                  : ''
            : d.priorityRule}
        </span>
      </span>
      <NextCell d={d} />
      <span>{d.ownerName ?? '—'}</span>
      <span style={{ fontSize: 13 }}>
        {d.sourcingTask ? (
          <span style={{ color: palette.link }}>询价中</span>
        ) : d.evidenceDate ? (
          <span
            style={{ color: d.evidenceValid ? palette.sub : palette.orange }}
          >
            询价 {dayjs(d.evidenceDate).format('MM-DD')} ·{' '}
            {d.evidenceValid ? '有效' : '已过期'}
          </span>
        ) : (
          <span style={{ color: palette.mute }}>
            {d.cntStockout ? '只有无货记录' : '没有价格记录'}
          </span>
        )}
      </span>
      <span>
        {canEdit && (
          <Button
            type="link"
            size="small"
            onClick={(e) => {
              e.stopPropagation();
              onRecord();
            }}
          >
            记处理
          </Button>
        )}
      </span>
    </div>
  );
};

const GroupRow: React.FC<{
  g: Group;
  cover: boolean;
  open: boolean;
  picked: Set<number>;
  selectedId?: number;
  canEdit: boolean;
  canProject: boolean;
  onToggle: () => void;
  onPick: (ids: number[], v: boolean) => void;
  onSelect: (d: Demand) => void;
  onRecord: (g: Group, ids?: number[]) => void;
  onProject: (g: Group) => void;
}> = ({
  g,
  cover,
  open,
  picked,
  selectedId,
  canEdit,
  canProject,
  onToggle,
  onPick,
  onSelect,
  onRecord,
  onProject,
}) => {
  const { palette } = useAppTheme();
  const [all, setAll] = useState(false);
  const ids = g.items.map((d) => d.id);
  const pickedN = ids.filter((id) => picked.has(id)).length;
  const shown = all ? g.items : g.items.slice(0, FOLD);
  return (
    <div style={{ borderBottom: `1px solid ${palette.hairline}` }}>
      {/* biome-ignore lint/a11y/useSemanticElements: 行内含复选框与按钮 */}
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
        <span onClick={(e) => e.stopPropagation()} onKeyDown={() => undefined}>
          <Checkbox
            checked={pickedN > 0 && pickedN === ids.length}
            indeterminate={pickedN > 0 && pickedN < ids.length}
            onChange={(e) => onPick(ids, e.target.checked)}
          />
        </span>
        <b style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
          {open ? (
            <DownOutlined style={{ fontSize: 11 }} />
          ) : (
            <RightOutlined style={{ fontSize: 11 }} />
          )}
          {scopeName(g)}
        </b>
        <span>{g.models} 个</span>
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
        {cover ? (
          <CoverBar coverage={g.coverage} width="100%" />
        ) : (
          <span style={{ color: palette.sub }}>{g.models} 个需求对象</span>
        )}
        <span style={{ display: 'grid', gap: 2 }}>
          <span>{g.urgentAction ?? '—'}</span>
          {g.earliestDue && (
            <span style={{ fontSize: 12 }}>
              <DueText date={g.earliestDue} />
            </span>
          )}
        </span>
        <span>
          {g.ownerCount > 1 ? `${g.ownerCount} 人` : (g.ownerName ?? '—')}
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
                已解决 {g.projectSolved} / {g.projectTotal}
              </span>
            </Link>
          ) : cover && canProject ? (
            <Button
              type="link"
              size="small"
              style={{ padding: 0 }}
              onClick={(e) => {
                e.stopPropagation();
                onProject(g);
              }}
            >
              建专项
            </Button>
          ) : (
            <span style={{ color: palette.mute }}>—</span>
          )}
        </span>
        <span>
          {canEdit && (
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
          )}
        </span>
      </div>
      {open && (
        <div style={{ paddingBottom: 8 }}>
          {shown.map((d) => (
            <ItemRow
              key={d.id}
              d={d}
              cover={cover}
              picked={picked.has(d.id)}
              selected={selectedId === d.id}
              canEdit={canEdit}
              onPick={(v) => onPick([d.id], v)}
              onSelect={() => onSelect(d)}
              onRecord={() => onRecord(g, [d.id])}
            />
          ))}
          {!all && g.items.length > FOLD && (
            <Button
              type="link"
              style={{ marginLeft: 76 }}
              onClick={() => setAll(true)}
            >
              还有 {g.items.length - FOLD} 个型号 · 显示全部
            </Button>
          )}
        </div>
      )}
    </div>
  );
};

/** 按品牌 · 品类分组的列表，组内展开型号；分批加载 */
export const GroupTable: React.FC<{
  board: 'intel' | 'cover';
  page?: GroupPage;
  loading: boolean;
  picked: Set<number>;
  selectedId?: number;
  canEdit: boolean;
  canProject: boolean;
  onPick: (ids: number[], v: boolean) => void;
  onSelect: (d: Demand) => void;
  onRecord: (g: Group, ids?: number[]) => void;
  onProject: (g: Group) => void;
  onMore: () => void;
}> = ({ board, page, loading, onMore, ...rest }) => {
  const { palette } = useAppTheme();
  const [open, setOpen] = useState<Set<string>>(new Set());
  const cover = board === 'cover';
  if (!page) return <Skeleton active paragraph={{ rows: 8 }} />;
  // 第一组默认展开
  const isOpen = (key: string, i: number) => open.has(key) !== (i === 0);
  return (
    <div style={{ overflowX: 'auto' }}>
      <div style={{ minWidth: 1040 }}>
        <Head cover={cover} />
        {page.groups.length === 0 && !loading && (
          <Empty style={{ padding: 40 }} description="没有符合条件的型号" />
        )}
        {page.groups.map((g, i) => (
          <GroupRow
            key={g.key}
            g={g}
            cover={cover}
            open={isOpen(g.key, i)}
            onToggle={() =>
              setOpen((s) => {
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
            共 {page.totalGroups} 组 · {page.totalModels} 个型号 · 已显示{' '}
            {page.groups.length} 组
          </span>
          {page.groups.length < page.totalGroups && (
            <Button type="link" loading={loading} onClick={onMore}>
              加载更多
            </Button>
          )}
        </div>
      </div>
    </div>
  );
};
