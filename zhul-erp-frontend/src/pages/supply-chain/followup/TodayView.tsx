import { Link } from '@umijs/max';
import { Button, Skeleton } from 'antd';
import { Bell, CircleCheck, Copy, Lightbulb } from 'lucide-react';
import React, { useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import {
  Card,
  CoverPill,
  DueText,
  Pill,
  useSupplyBoardStyle,
} from '../components';
import {
  type DevTodayItem,
  modelName,
  PATHS,
  REMIND_FULFILLMENT,
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
  const board = useSupplyBoardStyle();
  const color = c.overdue ? palette.red : board(c.board).color;
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
            <Pill tone="gray">
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
              <Copy size="1em" /> {c.items.length} 个型号到期
            </span>
            <span style={{ flex: 1 }} />
            <span style={{ color: c.overdue ? palette.red : palette.sub }}>
              {c.overdue ? `逾期 ${c.overdueDays} 天` : '今天'}
            </span>
          </div>
          {top?.nextAction && (
            <div style={{ display: 'flex', gap: 8 }}>
              <Bell
                size="1em"
                style={{ color: palette.orange, marginTop: 4 }}
              />
              <span>
                {top.nextActionSource === 2 &&
                  top.reminderType === REMIND_FULFILLMENT && (
                    <span style={{ marginRight: 6 }}>
                      <Pill tone="red">履约异常</Pill>
                    </span>
                  )}
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
                <Lightbulb size="1em" /> SOP 建议：{c.sopHint}
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
                    {d.nextActionSource === 2 &&
                      d.reminderType === REMIND_FULFILLMENT && (
                        <span style={{ marginRight: 6 }}>
                          <Pill tone="red">履约异常</Pill>
                        </span>
                      )}
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

const DEV_KIND_NAMES = {
  candidate: '候选下一步',
  project: '开发任务超期',
  remind: '系统提醒',
};

/** 供应商开发的一条待办：候选下一步、开发任务超期、系统提醒；点击定位到板块里的对应组 */
const DevItemView: React.FC<{
  i: DevTodayItem;
  onOpen: (i: DevTodayItem) => void;
}> = ({ i, onOpen }) => {
  const { palette } = useAppTheme();
  const board = useSupplyBoardStyle();
  const color = i.overdue ? palette.red : board('dev').color;
  return (
    <Card style={{ padding: '14px 20px', marginBottom: 12 }}>
      <div style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
        <span
          style={{
            width: 3,
            alignSelf: 'stretch',
            borderRadius: 2,
            background: color,
            flexShrink: 0,
          }}
        />
        <div style={{ flex: 1, minWidth: 0, display: 'grid', gap: 6 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              flexWrap: 'wrap',
            }}
          >
            <Pill tone="gray">供应商开发 · {DEV_KIND_NAMES[i.kind]}</Pill>
            <b style={{ fontSize: 16 }}>{scopeName(i)}</b>
            {i.candidateName && (
              <span style={{ color: palette.sub }}>{i.candidateName}</span>
            )}
            <span style={{ flex: 1 }} />
            <span style={{ color: i.overdue ? palette.red : palette.sub }}>
              {i.overdue ? `逾期 ${i.overdueDays} 天` : '今天'}
            </span>
          </div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <Bell size="1em" style={{ color: palette.orange }} />
            <span style={{ flex: 1 }}>
              {i.kind === 'candidate' ? '我定的下一步：' : ''}
              {i.text}
            </span>
            <Button type="primary" onClick={() => onOpen(i)}>
              去处理
            </Button>
          </div>
        </div>
      </div>
    </Card>
  );
};

/** 今日待办：已逾期、今天两组卡片（型号 + 供应商开发）；都处理完时显示本周剩余与去看缺口 */
export const TodayView: React.FC<{
  today?: Today;
  /** 取数失败的原因，有值时显示失败与重试 */
  error?: string;
  onRetry?: () => void;
  canEdit: boolean;
  onRecord: (c: TodayCard) => void;
  onDefer: (c: TodayCard) => void;
  onBrowse: () => void;
  onDev: (i: DevTodayItem) => void;
}> = ({
  today,
  error,
  onRetry,
  canEdit,
  onRecord,
  onDefer,
  onBrowse,
  onDev,
}) => {
  const { palette } = useAppTheme();
  const [filter, setFilter] = useState<'overdue' | 'today'>();
  if (!today)
    return (
      <Card style={{ padding: 20 }}>
        {error ? (
          <ErrorHint
            message={`今日待办加载失败：${error}`}
            onRetry={() => onRetry?.()}
          />
        ) : (
          <Skeleton active paragraph={{ rows: 6 }} />
        )}
      </Card>
    );
  const dev = today.devItems ?? [];
  if (!today.overdueCards.length && !today.todayCards.length && !dev.length)
    return (
      <Card style={{ padding: '48px 24px', textAlign: 'center' }}>
        <CircleCheck
          size="1em"
          style={{ fontSize: 28, color: palette.green }}
        />
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
  const section = (title: string, cards: TodayCard[], red?: boolean) => {
    const items = dev.filter((i) => i.overdue === !!red);
    return (
      (cards.length > 0 || items.length > 0) && (
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
          {items.map((i) => (
            <DevItemView key={i.key} i={i} onOpen={onDev} />
          ))}
        </>
      )
    );
  };
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
