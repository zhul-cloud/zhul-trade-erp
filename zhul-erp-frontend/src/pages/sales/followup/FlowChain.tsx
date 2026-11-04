import {
  CalendarOutlined,
  RetweetOutlined,
  RightOutlined,
} from '@ant-design/icons';
import { Skeleton } from 'antd';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { styleOf, TODAY_COLOR, VIEW_TODAY } from './boardStyle';
import { Card, Pill } from './components';
import { OPEN_BOARDS, type Overview, type OverviewBoard } from './service';

/** 卡片之间的渐变圆形箭头 */
const Arrow: React.FC<{ from: string; to: string }> = ({ from, to }) => (
  <div
    style={{
      display: 'flex',
      alignItems: 'center',
      flexShrink: 0,
      alignSelf: 'center',
    }}
  >
    <span style={{ width: 6, height: 3, background: from }} />
    <span
      style={{
        width: 30,
        height: 30,
        borderRadius: 15,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: '#fff',
        fontSize: 13,
        background: `linear-gradient(90deg, ${from}, ${to})`,
        boxShadow: '0 3px 8px rgba(67,56,202,0.35)',
      }}
    >
      <RightOutlined />
    </span>
    <span style={{ width: 6, height: 3, background: to }} />
  </div>
);

const BoardCard: React.FC<{
  b: OverviewBoard;
  active: boolean;
  onSelect?: (board: number) => void;
}> = ({ b, active, onSelect }) => {
  const { palette } = useAppTheme();
  const s = styleOf(b.board);
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
        background: active ? `${s.color}14` : palette.card,
        border: `1px solid ${active ? s.color : palette.hairline}`,
        boxShadow: active ? '0 4px 12px -4px rgba(67,56,202,0.25)' : undefined,
        transition: 'background .15s, border-color .15s, box-shadow .15s',
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
              color: s.color,
              background: `${s.color}1F`,
              fontSize: 14,
            }}
          >
            {s.icon}
          </span>
          <b
            style={{
              fontSize: 14,
              whiteSpace: 'nowrap',
              color: active ? s.color : palette.ink,
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
            color: active ? '#fff' : s.color,
            background: active ? s.color : `${s.color}1F`,
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
const Track: React.FC<{ boards: OverviewBoard[] }> = ({ boards }) => (
  <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
    {boards.map((b, i) => {
      const s = styleOf(b.board);
      const next = boards[i + 1] ? styleOf(boards[i + 1].board) : undefined;
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
                background: s.color,
              }}
            >
              {b.step}
            </span>
            <span
              style={{
                fontSize: 12,
                fontWeight: 600,
                color: s.color,
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
                background: `linear-gradient(90deg, ${s.color}, ${(next ?? s).color})`,
              }}
            />
            {last && (
              <span
                style={{
                  fontSize: 12,
                  color: s.color,
                  whiteSpace: 'nowrap',
                  display: 'inline-flex',
                  gap: 4,
                }}
              >
                <RetweetOutlined /> 复购回到第 2 步
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
            color: today ? '#fff' : palette.ink,
            background: today ? TODAY_COLOR : palette.card,
            border: `1px solid ${today ? TODAY_COLOR : palette.hairline}`,
            transition: 'background .15s',
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
            <span style={{ fontSize: 12, marginLeft: 4 }}>项</span>
          </span>
          <span
            style={{
              fontSize: 12,
              color: today ? 'rgba(255,255,255,0.85)' : palette.sub,
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
                {i > 0 && (
                  <Arrow
                    from={styleOf(overview.boards[i - 1].board).color}
                    to={styleOf(b.board).color}
                  />
                )}
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
