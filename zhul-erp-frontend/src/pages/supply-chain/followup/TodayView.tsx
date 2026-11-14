import {
  BellOutlined,
  BulbOutlined,
  CheckCircleOutlined,
  CopyOutlined,
} from '@ant-design/icons';
import { Link } from '@umijs/max';
import { Button, Skeleton } from 'antd';
import React, { useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { BOARD_COLOR, Card, CoverPill, DueText, Pill } from '../components';
import {
  modelName,
  PATHS,
  scopeName,
  type Today,
  type TodayCard,
} from '../service';
import { STAGE_NAMES } from './BattleCard';

const BOARD_NAMES = { intel: '需求情报', cover: '供应覆盖' };

const TodayCardView: React.FC<{
  c: TodayCard;
  canEdit: boolean;
  onRecord: (c: TodayCard) => void;
  onDefer: (c: TodayCard) => void;
}> = ({ c, canEdit, onRecord, onDefer }) => {
  const { palette } = useAppTheme();
  const color = c.overdue ? palette.red : BOARD_COLOR[c.board];
  const top = c.items[0];
  return (
    <Card style={{ padding: '16px 20px', marginBottom: 12 }}>
      <div style={{ display: 'flex', gap: 16 }}>
        <span
          style={{
            width: 3,
            borderRadius: 2,
            background: color,
            flexShrink: 0,
          }}
        />
        <div style={{ flex: 1, minWidth: 0, display: 'grid', gap: 8 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              flexWrap: 'wrap',
            }}
          >
            <Pill tone={c.board === 'intel' ? 'violet' : 'accent'}>
              {BOARD_NAMES[c.board]} · {STAGE_NAMES[c.stage] ?? c.stage}
            </Pill>
            <b style={{ fontSize: 16 }}>{scopeName(c)}</b>
            <span
              style={{
                fontSize: 12,
                color: palette.mute,
                display: 'inline-flex',
                gap: 4,
              }}
            >
              <CopyOutlined /> {c.items.length} 个型号到期
            </span>
            <span style={{ flex: 1 }} />
            <span style={{ color: c.overdue ? palette.red : palette.sub }}>
              {c.overdue ? `逾期 ${c.overdueDays} 天` : '今天'}
            </span>
          </div>
          {top?.nextAction && (
            <div style={{ display: 'flex', gap: 8 }}>
              <BellOutlined style={{ color: palette.orange, marginTop: 4 }} />
              <span>
                {top.nextActionSource === 2 ? '系统提醒' : '我定的下一步'}：
                {c.items.length === 1 || top.nextActionSource !== 2
                  ? ''
                  : `${modelName(top)} `}
                {top.nextAction}
              </span>
            </div>
          )}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {c.sopHint && (
              <span
                style={{ color: palette.link, display: 'inline-flex', gap: 8 }}
              >
                <BulbOutlined /> SOP 建议：{c.sopHint}
              </span>
            )}
            <span style={{ flex: 1 }} />
            {canEdit && (
              <>
                <Button onClick={() => onDefer(c)}>延后</Button>
                <Button type="primary" onClick={() => onRecord(c)}>
                  记处理
                </Button>
              </>
            )}
          </div>
          {c.items.length > 1 && (
            <div style={{ display: 'grid', gap: 6 }}>
              {c.items.map((d) => (
                <div
                  key={d.id}
                  style={{
                    display: 'grid',
                    gridTemplateColumns:
                      'minmax(160px, 1.2fr) 140px minmax(0, 2fr) 120px',
                    gap: 12,
                    alignItems: 'center',
                    padding: '8px 12px',
                    borderRadius: 8,
                    background: palette.inset,
                  }}
                >
                  <Link to={PATHS.demand(d.id)}>{modelName(d)}</Link>
                  <span>
                    <CoverPill status={d.coverageStatus || undefined} />
                  </span>
                  <span
                    style={{
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {d.nextAction}
                    {d.nextActionSource === 2 ? '（系统）' : ''}
                  </span>
                  <DueText date={d.nextActionDate} />
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </Card>
  );
};

/** 今日待办：已逾期、今天两组卡片；都处理完时显示本周剩余与去看缺口 */
export const TodayView: React.FC<{
  today?: Today;
  canEdit: boolean;
  onRecord: (c: TodayCard) => void;
  onDefer: (c: TodayCard) => void;
  onBrowse: () => void;
}> = ({ today, canEdit, onRecord, onDefer, onBrowse }) => {
  const { palette } = useAppTheme();
  const [filter, setFilter] = useState<'overdue' | 'today'>();
  if (!today)
    return (
      <Card style={{ padding: 20 }}>
        <Skeleton active paragraph={{ rows: 6 }} />
      </Card>
    );
  if (!today.overdueCards.length && !today.todayCards.length)
    return (
      <Card style={{ padding: '48px 24px', textAlign: 'center' }}>
        <CheckCircleOutlined style={{ fontSize: 28, color: palette.green }} />
        <div style={{ fontSize: 18, fontWeight: 700, margin: '12px 0 6px' }}>
          今天的都处理完了
        </div>
        <div style={{ color: palette.sub }}>
          {today.weekRest
            ? `本周还有 ${today.weekRest} 个型号到期。`
            : '本周没有其他到期的型号。'}
        </div>
        <div style={{ marginTop: 16 }}>
          <Button type="primary" onClick={onBrowse}>
            去供应覆盖看缺口
          </Button>
        </div>
      </Card>
    );
  const chip = (
    key: 'overdue' | 'today',
    label: string,
    n: number,
    danger?: boolean,
  ) => (
    <Button
      size="small"
      type={filter === key ? 'primary' : 'default'}
      danger={danger && filter !== key}
      onClick={() => setFilter((f) => (f === key ? undefined : key))}
    >
      {label} {n}
    </Button>
  );
  const section = (title: string, cards: TodayCard[], red?: boolean) =>
    cards.length > 0 && (
      <>
        <div
          style={{
            margin: '16px 0 8px',
            color: red ? palette.red : palette.sub,
          }}
        >
          {title}
        </div>
        {cards.map((c) => (
          <TodayCardView
            key={c.key}
            c={c}
            canEdit={canEdit}
            onRecord={onRecord}
            onDefer={onDefer}
          />
        ))}
      </>
    );
  return (
    <div>
      <div style={{ display: 'flex', gap: 8 }}>
        {chip('overdue', '已逾期', today.overdue, true)}
        {chip('today', '今天', today.dueToday)}
        <Button size="small" disabled>
          本周还有 {today.weekRest}
        </Button>
      </div>
      {filter !== 'today' && section('已逾期', today.overdueCards, true)}
      {filter !== 'overdue' && section('今天', today.todayCards)}
    </div>
  );
};
