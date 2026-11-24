import { Link } from '@umijs/max';
import { Button, Skeleton } from 'antd';
import { Bell, CircleCheck, Copy, Lightbulb } from 'lucide-react';
import React, { useState } from 'react';
import { QuickFilters } from '@/components/ListLayout';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { Card, CoverPill, DueText, Pill } from '../components';
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

/**
 * 一条待办一段（与客户跟进今日待办同一写法，R10 方案 B）：同一张卡里上下用分隔线隔开，
 * 不再各包一张卡；逾期的左侧一条红色竖条
 */
const Row: React.FC<{
  overdue: boolean;
  first: boolean;
  children: React.ReactNode;
}> = ({ overdue, first, children }) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        position: 'relative',
        display: 'grid',
        gap: 6,
        padding: '12px 0 12px 15px',
        borderTop: first ? undefined : `1px solid ${palette.hairline}`,
      }}
    >
      {overdue && (
        <span
          aria-hidden
          style={{
            position: 'absolute',
            left: 0,
            top: 12,
            bottom: 12,
            width: 3,
            borderRadius: 2,
            background: palette.red,
          }}
        />
      )}
      {children}
    </div>
  );
};

/** 首行：板块 · 阶段、名称、补充信息，右侧到期情况 */
const Head: React.FC<{
  tag: string;
  name: string;
  extra?: React.ReactNode;
  overdue: boolean;
  overdueDays?: number;
}> = ({ tag, name, extra, overdue, overdueDays }) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        flexWrap: 'wrap',
      }}
    >
      <Pill tone="gray">{tag}</Pill>
      <b style={{ fontSize: 14, color: palette.ink }}>{name}</b>
      {extra}
      <span
        style={{
          marginLeft: 'auto',
          fontWeight: 600,
          color: overdue ? palette.red : palette.sub,
        }}
      >
        {overdue ? `逾期 ${overdueDays} 天` : '今天'}
      </span>
    </div>
  );
};

const TodayCardView: React.FC<{
  c: TodayCard;
  first: boolean;
  canEdit: boolean;
  onRecord: (c: TodayCard) => void;
  onDefer: (c: TodayCard) => void;
}> = ({ c, first, canEdit, onRecord, onDefer }) => {
  const { palette } = useAppTheme();
  const top = c.items[0];
  return (
    <Row overdue={c.overdue} first={first}>
      <Head
        tag={`${BOARD_NAMES[c.board]} · ${STAGE_NAMES[c.stage] ?? c.stage}`}
        name={scopeName(c)}
        overdue={c.overdue}
        overdueDays={c.overdueDays}
        extra={
          <span
            style={{
              fontSize: 12,
              color: palette.mute,
              display: 'inline-flex',
              alignItems: 'center',
              gap: 4,
            }}
          >
            <Copy size="1em" /> {c.items.length} 个型号到期
          </span>
        }
      />
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div style={{ flex: 1, minWidth: 0, display: 'grid', gap: 4 }}>
          {top?.nextAction && (
            <div style={{ fontSize: 13, color: palette.ink }}>
              <Bell
                size="1em"
                style={{ color: palette.mute, marginRight: 6 }}
              />
              {top.nextActionSource === 2 &&
                top.reminderType === REMIND_FULFILLMENT && (
                  <span style={{ marginRight: 6 }}>
                    <Pill tone="red">履约异常</Pill>
                  </span>
                )}
              <span style={{ color: palette.sub }}>
                {top.nextActionSource === 2 ? '系统提醒' : '我定的下一步'}：
              </span>
              {c.items.length === 1 || top.nextActionSource !== 2
                ? ''
                : `${modelName(top)} `}
              {top.nextAction}
            </div>
          )}
          {c.sopHint && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              <Lightbulb
                size="1em"
                style={{ marginRight: 6, color: palette.faint }}
              />
              SOP 建议：{c.sopHint}
            </div>
          )}
        </div>
        {canEdit && (
          <span style={{ display: 'inline-flex', gap: 8, flex: 'none' }}>
            <Button size="small" onClick={() => onDefer(c)}>
              延后
            </Button>
            <Button size="small" onClick={() => onRecord(c)}>
              记处理
            </Button>
          </span>
        )}
      </div>
      {c.items.length > 1 && (
        <div style={{ display: 'grid' }}>
          {c.items.map((d) => (
            <div
              key={d.id}
              style={{
                display: 'grid',
                gridTemplateColumns:
                  'minmax(160px, 1.2fr) 140px minmax(0, 2fr) 120px',
                gap: 12,
                alignItems: 'center',
                padding: '6px 0',
                borderTop: `1px solid ${palette.hairline}`,
                fontSize: 13,
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
                  color: palette.sub,
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
    </Row>
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
  first: boolean;
  onOpen: (i: DevTodayItem) => void;
}> = ({ i, first, onOpen }) => {
  const { palette } = useAppTheme();
  return (
    <Row overdue={i.overdue} first={first}>
      <Head
        tag={`供应商开发 · ${DEV_KIND_NAMES[i.kind]}`}
        name={scopeName(i)}
        overdue={i.overdue}
        overdueDays={i.overdueDays}
        extra={
          i.candidateName && (
            <span style={{ fontSize: 12, color: palette.mute }}>
              {i.candidateName}
            </span>
          )
        }
      />
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div style={{ flex: 1, minWidth: 0, fontSize: 13, color: palette.ink }}>
          <Bell size="1em" style={{ color: palette.mute, marginRight: 6 }} />
          {i.kind === 'candidate' && (
            <span style={{ color: palette.sub }}>我定的下一步：</span>
          )}
          {i.text}
        </div>
        <Button size="small" onClick={() => onOpen(i)} style={{ flex: 'none' }}>
          去处理
        </Button>
      </div>
    </Row>
  );
};

/** 今日待办：已逾期、今天两段（型号 + 供应商开发），都在一张卡里；都处理完时显示本周剩余与去看缺口 */
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
  /** 工具栏右侧：搜索（页面传入） */
  tools?: React.ReactNode;
}> = ({
  today,
  error,
  onRetry,
  canEdit,
  onRecord,
  onDefer,
  onBrowse,
  onDev,
  tools,
}) => {
  const { palette } = useAppTheme();
  const [filter, setFilter] = useState<'overdue' | 'today'>();
  if (!today)
    return (
      <Card style={{ padding: 20 }}>
        {error && tools && (
          <div
            style={{
              display: 'flex',
              justifyContent: 'flex-end',
              marginBottom: 12,
            }}
          >
            {tools}
          </div>
        )}
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
  const toolRow = (children: React.ReactNode) => (
    <div
      className="zhul-toolbar"
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        flexWrap: 'wrap',
      }}
    >
      {children}
      {tools && (
        <span
          style={{
            marginLeft: 'auto',
            display: 'inline-flex',
            gap: 8,
            flexWrap: 'wrap',
          }}
        >
          {tools}
        </span>
      )}
    </div>
  );
  if (!today.overdueCards.length && !today.todayCards.length && !dev.length)
    return (
      <Card style={{ padding: '16px 20px 48px' }}>
        {/* 没有待办时也能搜索 */}
        {toolRow(null)}
        <div style={{ textAlign: 'center', paddingTop: 32 }}>
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
        </div>
      </Card>
    );
  const section = (title: string, cards: TodayCard[], red?: boolean) => {
    const items = dev.filter((i) => i.overdue === !!red);
    // 与快捷筛选同一口径：按型号计数（一张卡可能有多个型号），加上供应商开发的条数
    const n = cards.reduce((sum, c) => sum + c.items.length, 0) + items.length;
    return (
      n > 0 && (
        <section aria-label={title}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              margin: '16px 0 4px',
              fontSize: 14,
              fontWeight: 600,
              color: palette.ink,
            }}
          >
            {title}
            <Pill tone={red ? 'red' : 'gray'}>{n}</Pill>
          </div>
          {cards.map((c, i) => (
            <TodayCardView
              key={c.key}
              c={c}
              first={i === 0}
              canEdit={canEdit}
              onRecord={onRecord}
              onDefer={onDefer}
            />
          ))}
          {items.map((i, k) => (
            <DevItemView
              key={i.key}
              i={i}
              first={cards.length + k === 0}
              onOpen={onDev}
            />
          ))}
        </section>
      )
    );
  };
  return (
    <Card style={{ padding: '16px 20px' }}>
      {toolRow(
        <>
          {/* 只看一段：单选，再点取消 */}
          <QuickFilters
            value={filter ? [filter] : []}
            onChange={(v) => setFilter(v.at(-1) as 'overdue' | 'today')}
            options={[
              {
                value: 'overdue',
                label: '已逾期',
                count: today.overdue,
                danger: today.overdue > 0,
              },
              { value: 'today', label: '今天', count: today.dueToday },
            ]}
          />
          <span style={{ fontSize: 12, color: palette.mute }}>
            本周还有 {today.weekRest}
          </span>
        </>,
      )}
      {filter !== 'today' && section('已逾期', today.overdueCards, true)}
      {filter !== 'overdue' && section('今天', today.todayCards)}
    </Card>
  );
};
