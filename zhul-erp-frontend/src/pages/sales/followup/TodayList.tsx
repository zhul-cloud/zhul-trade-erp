import { Button, Checkbox, Skeleton } from 'antd';
import {
  Bell,
  ChevronDown,
  ChevronUp,
  CircleCheck,
  Copy,
  Lightbulb,
  User,
} from 'lucide-react';
import React, { useState } from 'react';
import { QuickFilters } from '@/components/ListLayout';
import { useAppTheme } from '@/theme/AppTheme';
import type { Picked, SelectAll } from './Batch';
import {
  Card,
  CustomerLink,
  DueText,
  Pill,
  SnoozeButton,
  StageTag,
  ValueCell,
} from './components';
import { QuickButton, StageAdjust } from './QuickActions';
import type { RecordTarget } from './RecordFollowupModal';
import {
  type FollowupItem,
  NEXT_USER,
  TODAY_PAGE,
  type Today,
  type TodayBoard,
  type TodayCustomer,
} from './service';

/** 卡片展开后默认显示的到期单据数 */
const DOC_FOLD = 4;

type Section = 'overdue' | 'today';

/** 待办来源：系统提醒或我定的下一步 */
const Reminder: React.FC<{ item: FollowupItem }> = ({ item }) => {
  const { palette } = useAppTheme();
  const mine = item.nextActionSource === NEXT_USER;
  return (
    <div style={{ color: palette.ink, fontSize: 13 }}>
      <span style={{ color: palette.mute, marginRight: 6 }}>
        {mine ? <User size="1em" /> : <Bell size="1em" />}
      </span>
      <span style={{ color: palette.sub }}>
        {mine ? '我定的下一步：' : '系统提醒：'}
      </span>
      {item.nextAction || '—'}
    </div>
  );
};

const Sop: React.FC<{ item: FollowupItem }> = ({ item }) => {
  const { palette } = useAppTheme();
  return item.sopHint ? (
    <div style={{ fontSize: 12, color: palette.mute }}>
      <Lightbulb size="1em" style={{ marginRight: 6, color: palette.faint }} />
      SOP 建议：{item.sopHint}
    </div>
  ) : null;
};

/** 展开后的到期单据：单号、阶段、各自的下一步、到期情况 */
const DueDocs: React.FC<{ items: FollowupItem[] }> = ({ items }) => {
  const { palette } = useAppTheme();
  const [all, setAll] = useState(false);
  const shown = all ? items : items.slice(0, DOC_FOLD);
  return (
    <div style={{ display: 'grid' }}>
      {shown.map((i) => (
        <div
          key={i.id}
          style={{
            display: 'flex',
            gap: 12,
            alignItems: 'center',
            padding: '6px 0',
            borderTop: `1px solid ${palette.hairline}`,
            fontSize: 13,
          }}
        >
          <span className="num" style={{ color: palette.ink, minWidth: 130 }}>
            {i.docNo ?? '—'}
          </span>
          <Pill tone="gray">
            {i.stageCode} {i.stageName}
          </Pill>
          <span
            style={{
              flex: 1,
              minWidth: 0,
              color: palette.sub,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {i.nextAction}
          </span>
          <DueText item={i} />
        </div>
      ))}
      {items.length > shown.length && (
        <Button
          type="link"
          size="small"
          style={{ justifySelf: 'start', padding: 0 }}
          onClick={() => setAll(true)}
        >
          还有 {items.length - shown.length} 张 · 显示全部
        </Button>
      )}
    </div>
  );
};

/** 「n 张单据到期 展开」 */
const DocToggle: React.FC<{
  count: number;
  open: boolean;
  onToggle: () => void;
}> = ({ count, open, onToggle }) => {
  const { palette } = useAppTheme();
  return (
    <button
      type="button"
      onClick={onToggle}
      style={{
        all: 'unset',
        cursor: 'pointer',
        fontSize: 12,
        color: palette.mute,
        whiteSpace: 'nowrap',
      }}
    >
      <Copy size="1em" /> {count} 张单据到期{' '}
      <span style={{ color: palette.link }}>
        {open ? '收起' : '展开'}{' '}
        {open ? <ChevronUp size="1em" /> : <ChevronDown size="1em" />}
      </span>
    </button>
  );
};

const boardTarget = (c: TodayCustomer, b: TodayBoard): RecordTarget => ({
  customerId: c.customerId,
  customerName: c.customerName,
  item: b.lead,
  items: b.items,
});

/** 板块的延后、已跟进、记跟进：作用于这个客户在该板块的到期单据 */
const BoardActions: React.FC<{
  c: TodayCustomer;
  b: TodayBoard;
  canRecord: boolean;
  onRecord: (t: RecordTarget) => void;
  onChanged: () => void;
}> = ({ c, b, canRecord, onRecord, onChanged }) => (
  <span style={{ display: 'inline-flex', gap: 8, flexShrink: 0 }}>
    <SnoozeButton
      entries={[
        { customerId: c.customerId, itemIds: b.items.map((i) => i.id) },
      ]}
      disabled={!canRecord}
      onDone={onChanged}
    />
    <QuickButton
      customerId={c.customerId}
      items={b.items}
      disabled={!canRecord}
      onRecord={() => onRecord(boardTarget(c, b))}
      onDone={onChanged}
    />
    <Button
      size="small"
      disabled={!canRecord}
      onClick={() => onRecord(boardTarget(c, b))}
    >
      记跟进
    </Button>
  </span>
);

const tagOf = (b: TodayBoard) => ({
  board: b.board,
  stageCode: b.stageCode,
  stageName: b.stageName,
});

/**
 * 一个客户一段（R10 方案 B）：客户之间用分隔线，不再各包一张描边卡；逾期的客户左侧一条红色竖条。
 * 首行是勾选、客户和到期情况，下面每个有到期单据的板块一行，板块行之间分隔线，不包灰底
 */
const TodayCard: React.FC<{
  c: TodayCustomer;
  section: Section | 'handled';
  /** 段内第一个客户：上方不画分隔线 */
  first: boolean;
  checked: boolean;
  canRecord: boolean;
  onCheck: (on: boolean) => void;
  onRecord: (t: RecordTarget) => void;
  onChanged: () => void;
}> = ({
  c,
  section,
  first,
  checked,
  canRecord,
  onCheck,
  onRecord,
  onChanged,
}) => {
  const { palette } = useAppTheme();
  const [open, setOpen] = useState<Record<number, boolean>>({});
  const lead = c.boards[0]?.lead;
  const toggle = (board: number) =>
    setOpen((o) => ({ ...o, [board]: !o[board] }));
  return (
    <div
      style={{
        position: 'relative',
        display: 'grid',
        padding: '12px 8px 4px 15px',
        borderTop: first ? undefined : `1px solid ${palette.hairline}`,
        // 勾选的客户：主题色浅底表示选中
        background: checked ? palette.primarySoft : undefined,
      }}
    >
      {section === 'overdue' && (
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
      <div
        style={{
          display: 'flex',
          gap: 8,
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        {section !== 'handled' && (
          <Checkbox
            checked={checked}
            disabled={!canRecord}
            aria-label={`选择 ${c.customerName}`}
            onChange={(e) => onCheck(e.target.checked)}
          />
        )}
        <CustomerLink item={c} />
        <ValueCell item={c} />
        {c.country && (
          <span style={{ fontSize: 12, color: palette.mute }}>{c.country}</span>
        )}
        {c.ownerName && (
          <span style={{ fontSize: 12, color: palette.mute }}>
            · {c.ownerName}
          </span>
        )}
        {c.boards.some((b) => b.lead.manySnoozes) && (
          <Pill tone="orange">多次延后</Pill>
        )}
        <span style={{ marginLeft: 'auto', fontWeight: 600 }}>
          {lead && <DueText item={lead} />}
        </span>
      </div>
      {c.boards.map((b, i) => (
        <div
          key={b.board}
          style={{
            display: 'grid',
            gap: 4,
            marginLeft: section === 'handled' ? 0 : 24,
            padding: '10px 0',
            borderTop: i > 0 ? `1px solid ${palette.hairline}` : undefined,
          }}
        >
          <div
            style={{
              display: 'flex',
              gap: 8,
              alignItems: 'center',
              flexWrap: 'wrap',
            }}
          >
            <StageTag item={tagOf(b)} withCode />
            <StageAdjust
              item={b.lead}
              canRecord={canRecord}
              onDone={onChanged}
            />
            {b.items.length > 1 && (
              <DocToggle
                count={b.items.length}
                open={!!open[b.board]}
                onToggle={() => toggle(b.board)}
              />
            )}
            <span style={{ marginLeft: 'auto' }}>
              <BoardActions
                c={c}
                b={b}
                canRecord={canRecord}
                onRecord={onRecord}
                onChanged={onChanged}
              />
            </span>
          </div>
          <Reminder item={b.lead} />
          <Sop item={b.lead} />
          {open[b.board] && <DueDocs items={b.items} />}
        </div>
      ))}
    </div>
  );
};

/** 勾选时带入的单据：这个客户全部到期单据 */
export const pickOfToday = (c: TodayCustomer): Picked => ({
  customerId: c.customerId,
  customerName: c.customerName,
  items: c.boards.flatMap((b) => b.items),
});

/** 今日待办（K1）：按客户，已逾期、今天两段，今天已跟进收在底部；每次加载 20 个客户 */
export const TodayList: React.FC<{
  today?: Today;
  canRecord: boolean;
  picked: Record<number, Picked>;
  onPick: (p: Picked, on: boolean) => void;
  /** 全选：换成已加载的客户；取消时清空 */
  onPickAll: (list: Picked[]) => void;
  batchBar: (selectAll: SelectAll) => React.ReactNode;
  onRecord: (t: RecordTarget) => void;
  onChanged: () => void;
  onBrowse: () => void;
  /** 工具栏右侧：搜索、负责人（页面传入） */
  tools?: React.ReactNode;
}> = ({
  today,
  canRecord,
  picked,
  onPick,
  onPickAll,
  batchBar,
  onRecord,
  onChanged,
  onBrowse,
  tools,
}) => {
  const { palette } = useAppTheme();
  const [only, setOnly] = useState<Section>();
  const [limit, setLimit] = useState(TODAY_PAGE);
  const [showHandled, setShowHandled] = useState(false);
  if (!today)
    return (
      <Card style={{ padding: 20 }}>
        <Skeleton active />
      </Card>
    );
  const handled = today.handled ?? [];
  const empty = today.overdue.length === 0 && today.today.length === 0;
  const sections = [
    { key: 'overdue' as const, title: '已逾期', rows: today.overdue },
    { key: 'today' as const, title: '今天', rows: today.today },
  ].filter((x) => !only || x.key === only);
  let budget = limit;
  const shown = sections.map((x) => {
    const rows = x.rows.slice(0, Math.max(0, budget));
    budget -= rows.length;
    return { ...x, rows };
  });
  const totalRows = sections.reduce((n, x) => n + x.rows.length, 0);
  const shownRows = shown.reduce((n, x) => n + x.rows.length, 0);
  const loaded = shown.flatMap((x) => x.rows);
  const pickedLoaded = loaded.filter((c) => picked[c.customerId]).length;
  const selectAll: SelectAll = {
    checked: pickedLoaded > 0 && pickedLoaded === loaded.length,
    indeterminate: pickedLoaded > 0 && pickedLoaded < loaded.length,
    onChange: (on) => onPickAll(on ? loaded.map(pickOfToday) : []),
  };
  const card = (
    c: TodayCustomer,
    section: Section | 'handled',
    index: number,
  ) => (
    <TodayCard
      key={c.customerId}
      c={c}
      section={section}
      first={index === 0}
      checked={!!picked[c.customerId]}
      canRecord={canRecord}
      onCheck={(on) => onPick(pickOfToday(c), on)}
      onRecord={onRecord}
      onChanged={onChanged}
    />
  );
  const count = (n: number, red?: boolean) => (
    <span
      className="num"
      style={{ fontWeight: 600, color: red && n > 0 ? palette.red : undefined }}
    >
      {n}
    </span>
  );
  const handledBlock = handled.length > 0 && (
    <div style={{ marginTop: 16 }}>
      <button
        type="button"
        onClick={() => setShowHandled((v) => !v)}
        style={{
          all: 'unset',
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          width: '100%',
          boxSizing: 'border-box',
          padding: '10px 16px',
          borderRadius: 12,
          background: palette.inset,
          color: palette.ink,
          fontSize: 13,
        }}
      >
        <CircleCheck size="1em" style={{ color: palette.green }} />
        <span style={{ flex: 1 }}>今天已跟进 {handled.length}</span>
        <span style={{ color: palette.link }}>
          {showHandled ? '收起' : '展开'}{' '}
          {showHandled ? <ChevronUp size="1em" /> : <ChevronDown size="1em" />}
        </span>
      </button>
      {showHandled && (
        <div style={{ marginTop: 10 }}>
          {handled.map((c, i) => card(c, 'handled', i))}
        </div>
      )}
    </div>
  );
  if (empty)
    return (
      <Card style={{ padding: '32px 20px', textAlign: 'center' }}>
        {/* 没有待办时也能换负责人、搜索（主管看团队的待办） */}
        {tools && (
          <div
            style={{
              display: 'flex',
              justifyContent: 'flex-end',
              gap: 8,
              flexWrap: 'wrap',
              margin: '-16px 0 16px',
            }}
          >
            {tools}
          </div>
        )}
        <CircleCheck
          size="1em"
          style={{ fontSize: 28, color: palette.green, marginBottom: 8 }}
        />
        <div style={{ fontWeight: 600, color: palette.ink }}>
          今天的跟进都处理完了
        </div>
        <div style={{ fontSize: 12, color: palette.sub, margin: '4px 0 12px' }}>
          明天有 {today.tomorrow} 个客户，可以先看看本周还有哪些客户该联系
        </div>
        <Button onClick={onBrowse}>看各板块的客户</Button>
        <div style={{ textAlign: 'left' }}>{handledBlock}</div>
      </Card>
    );
  return (
    <Card style={{ padding: '16px 20px' }}>
      <div
        className="zhul-toolbar"
        style={{
          display: 'flex',
          gap: 8,
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        {/* 只看一段：单选，再点取消 */}
        <QuickFilters
          value={only ? [only] : []}
          onChange={(v) => {
            setOnly(v.at(-1) as Section | undefined);
            setLimit(TODAY_PAGE);
          }}
          options={[
            {
              value: 'overdue',
              label: <>已逾期 {count(today.overdue.length, true)}</>,
            },
            { value: 'today', label: <>今天 {count(today.today.length)}</> },
          ]}
        />
        <span style={{ fontSize: 12, color: palette.mute }}>
          本周还有 {today.weekRemaining}
        </span>
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
      {batchBar(selectAll)}
      {shown.map(
        (x) =>
          x.rows.length > 0 && (
            <section key={x.key} aria-label={x.title}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  margin: '16px 0 8px',
                  fontSize: 14,
                  fontWeight: 600,
                  color: palette.ink,
                }}
              >
                {x.title}
                <Pill tone={x.key === 'overdue' ? 'red' : 'gray'}>
                  {x.rows.length}
                </Pill>
              </div>
              <div>{x.rows.map((c, i) => card(c, x.key, i))}</div>
            </section>
          ),
      )}
      {sections.every((x) => x.rows.length === 0) && (
        <div style={{ padding: '16px 0', color: palette.mute }}>
          {only === 'overdue' ? '没有逾期的客户' : '今天到期的都处理完了'}
        </div>
      )}
      {shownRows < totalRows && (
        <div style={{ textAlign: 'center', marginTop: 12 }}>
          <Button type="link" onClick={() => setLimit((n) => n + TODAY_PAGE)}>
            再加载 {Math.min(TODAY_PAGE, totalRows - shownRows)} 个
            <span style={{ color: palette.mute }}>
              {' '}
              · 还有 {totalRows - shownRows} 个 <ChevronDown size="1em" />
            </span>
          </Button>
        </div>
      )}
      {handledBlock}
    </Card>
  );
};
