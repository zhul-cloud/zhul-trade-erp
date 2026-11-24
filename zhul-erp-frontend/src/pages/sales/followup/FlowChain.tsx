import { Skeleton } from 'antd';
import { Calendar, ChevronRight, Repeat } from 'lucide-react';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { useBoardStyle, VIEW_TODAY } from './boardStyle';
import { Card, Pill } from './components';
import { OPEN_BOARDS, type Overview, type OverviewBoard } from './service';

/** 卡片之间的箭头：中性灰小圆钮（不用渐变，颜色只留给板块本身） */
export const Arrow: React.FC = () => {
  const { palette } = useAppTheme();
  return (
    <span
      style={{
        alignSelf: 'center',
        flexShrink: 0,
        width: 24,
        height: 24,
        borderRadius: 12,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: palette.mute,
        background: palette.inset,
        fontSize: 11,
      }}
    >
      <ChevronRight size="1em" />
    </span>
  );
};

const BoardCard: React.FC<{
  b: OverviewBoard;
  active: boolean;
  onSelect?: (board: number) => void;
}> = ({ b, active, onSelect }) => {
  const { palette } = useAppTheme();
  const s = useBoardStyle()(b.board);
  const open = b.available && OPEN_BOARDS.includes(b.board);
  return (
    <button
      type="button"
      disabled={!open}
      title={open ? undefined : '即将上线'}
      onClick={() => open && onSelect?.(b.board)}
      style={{
        all: 'unset',
        boxSizing: 'border-box',
        flex: '1 1 0',
        minWidth: 150,
        alignSelf: 'stretch',
        alignContent: 'start',
        display: 'grid',
        gap: 6,
        padding: '12px 14px',
        borderRadius: 14,
        cursor: open ? 'pointer' : 'not-allowed',
        background: active ? s.soft : palette.card,
        border: `1px solid ${active ? s.color : palette.hairline}`,
        transition: 'background .15s, border-color .15s',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 8,
        }}
      >
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
              fontSize: 14,
            }}
          >
            {s.icon}
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
            color: active ? s.badgeInk : s.text,
            background: active ? s.badge : s.soft,
            whiteSpace: 'nowrap',
          }}
        >
          第 {b.step} 步
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
              {b.customers}
            </span>
            <span style={{ fontSize: 12, color: palette.sub }}>位客户</span>
            <span style={{ flex: 1 }} />
            {b.overdue > 0 && <Pill tone="red">逾期 {b.overdue}</Pill>}
            {!!b.dormant && <Pill tone="orange">沉睡 {b.dormant}</Pill>}
          </div>
          <div
            style={{
              fontSize: 12,
              lineHeight: 1.5,
              color: palette.sub,
            }}
          >
            {b.groups.map((g) => `${g.name} ${g.count}`).join(' · ')}
            {b.nurture > 0 && ` · 培育中 ${b.nurture}`}
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

/** 卡片下方贯穿五步的进度轨道，末尾「复购回到第 2 步」 */
const Track: React.FC<{ boards: OverviewBoard[] }> = ({ boards }) => {
  const style = useBoardStyle();
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
      {boards.map((b, i) => {
        const s = style(b.board);
        const next = boards[i + 1] ? style(boards[i + 1].board) : undefined;
        const last = !next;
        return (
          <React.Fragment key={b.board}>
            <div
              style={{
                flex: '1 1 0',
                // 最后一段要放下「复购回到第 2 步」
                minWidth: last ? 'auto' : 0,
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
                {b.step}
              </span>
              <span
                style={{
                  fontSize: 12,
                  fontWeight: 600,
                  color: s.text,
                  whiteSpace: 'nowrap',
                }}
              >
                {b.name} {b.available ? b.customers : ''}
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
              {last && (
                <span
                  style={{
                    fontSize: 12,
                    color: s.text,
                    whiteSpace: 'nowrap',
                    display: 'inline-flex',
                    gap: 4,
                  }}
                >
                  <Repeat size="1em" /> 复购回到第 2 步
                </span>
              )}
            </div>
            {!last && (
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

/** 顶部链路：今日待办 + 五个板块（第 1–5 步），点卡片切换视图，当前卡片高亮 */
export const FlowChain: React.FC<{
  overview?: Overview;
  /** VIEW_TODAY 或板块编号 */
  active: number;
  onSelect: (view: number) => void;
}> = ({ overview, active, onSelect }) => {
  const { palette } = useAppTheme();
  if (!overview)
    return (
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <Skeleton active paragraph={{ rows: 2 }} />
      </Card>
    );
  const today = active === VIEW_TODAY;
  const total = overview.todayOverdue + overview.todayDue;
  return (
    <div style={{ overflowX: 'auto', marginBottom: 16 }}>
      <div style={{ display: 'flex', gap: 16, minWidth: 1080 }}>
        <button
          type="button"
          onClick={() => onSelect(VIEW_TODAY)}
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
            color: today ? palette.onPrimary : palette.ink,
            background: today ? palette.primary : palette.card,
            border: `1px solid ${today ? palette.primary : palette.hairline}`,
            transition: 'background .15s',
          }}
        >
          <b
            style={{
              fontSize: 14,
              color: today ? palette.onPrimary : palette.link,
              display: 'inline-flex',
              gap: 6,
            }}
          >
            <Calendar size="1em" /> 今日待办
          </b>
          <span>
            <span style={{ fontSize: 28, fontWeight: 700 }}>{total}</span>
            <span style={{ fontSize: 12, marginLeft: 4 }}>位客户</span>
          </span>
          <span
            style={{
              fontSize: 12,
              color: today ? palette.onPrimary : palette.sub,
              opacity: today ? 0.85 : 1,
            }}
          >
            逾期 {overview.todayOverdue} · 今天 {overview.todayDue}
          </span>
        </button>
        <span style={{ width: 1, background: palette.hairline }} />
        <div
          style={{
            flex: 1,
            minWidth: 0,
            display: 'grid',
            // 列宽不随内容撑开，否则五张卡片会超出容器
            gridTemplateColumns: 'minmax(0, 1fr)',
            gap: 10,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'stretch', gap: 4 }}>
            {overview.boards.map((b, i) => (
              <React.Fragment key={b.board}>
                {i > 0 && <Arrow />}
                <BoardCard
                  b={b}
                  active={b.board === active}
                  onSelect={onSelect}
                />
              </React.Fragment>
            ))}
          </div>
          <Track boards={overview.boards} />
        </div>
      </div>
    </div>
  );
};
