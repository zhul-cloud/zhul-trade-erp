import {
  BellOutlined,
  BulbOutlined,
  CheckCircleOutlined,
  CopyOutlined,
  DownOutlined,
  UpOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Button, Checkbox, Skeleton } from 'antd';
import React, { useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import type { Picked } from './Batch';
import { TODAY_COLOR } from './boardStyle';
import {
  Card,
  CustomerLink,
  DueText,
  Pill,
  SnoozeButton,
  StageTag,
  ValueCell,
} from './components';
import type { RecordTarget } from './RecordFollowupModal';
import {
  type FollowupItem,
  NEXT_USER,
  type Today,
  type TodayBoard,
  type TodayCustomer,
} from './service';

const TODAY_FOLD = 8;
/** 卡片展开后默认显示的到期单据数 */
const DOC_FOLD = 4;

type Section = 'overdue' | 'today';

/** 待办来源：系统提醒或我定的下一步 */
const Reminder: React.FC<{ item: FollowupItem }> = ({ item }) => {
  const { palette } = useAppTheme();
  const mine = item.nextActionSource === NEXT_USER;
  return (
    <div style={{ color: palette.ink, fontSize: 13 }}>
      <span style={{ color: palette.orange, marginRight: 6 }}>
        {mine ? <UserOutlined /> : <BellOutlined />}
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
    <div style={{ fontSize: 12, color: palette.link }}>
      <BulbOutlined style={{ marginRight: 6 }} />
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
    <div style={{ display: 'grid', gap: 6 }}>
      {shown.map((i) => (
        <div
          key={i.id}
          style={{
            display: 'flex',
            gap: 12,
            alignItems: 'center',
            padding: '8px 12px',
            borderRadius: 8,
            background: palette.inset,
            fontSize: 13,
          }}
        >
          <span style={{ color: palette.link, minWidth: 130 }}>
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
        padding: '1px 8px',
        borderRadius: 6,
        background: palette.inset,
        color: palette.sub,
      }}
    >
      <CopyOutlined /> {count} 张单据到期{' '}
      <span style={{ color: palette.link }}>
        {open ? '收起' : '展开'} {open ? <UpOutlined /> : <DownOutlined />}
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

/** 板块的延后、记跟进：作用于这个客户在该板块的到期单据 */
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
    <Button
      type="primary"
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

/** 一个客户一张卡片；在多个板块都有到期单据时每个板块一行 */
const TodayCard: React.FC<{
  c: TodayCustomer;
  section: Section | 'handled';
  checked: boolean;
  canRecord: boolean;
  onCheck: (on: boolean) => void;
  onRecord: (t: RecordTarget) => void;
  onChanged: () => void;
}> = ({ c, section, checked, canRecord, onCheck, onRecord, onChanged }) => {
  const { palette } = useAppTheme();
  const [open, setOpen] = useState<Record<number, boolean>>({});
  const single = c.boards.length === 1 ? c.boards[0] : undefined;
  const lead = single?.lead ?? c.boards[0]?.lead;
  const overdue = section === 'overdue';
  const toggle = (board: number) =>
    setOpen((o) => ({ ...o, [board]: !o[board] }));
  return (
    <div
      style={{
        position: 'relative',
        display: 'grid',
        gap: 8,
        padding: '12px 16px 12px 18px',
        borderRadius: 12,
        border: `1px solid ${checked ? palette.accentLine : palette.hairline}`,
        background: palette.card,
      }}
    >
      <span
        style={{
          position: 'absolute',
          left: 8,
          top: 12,
          bottom: 12,
          width: 3,
          borderRadius: 2,
          background: overdue
            ? palette.red
            : section === 'handled'
              ? palette.green
              : TODAY_COLOR,
        }}
      />
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
            onChange={(e) => onCheck(e.target.checked)}
          />
        )}
        {single && <StageTag item={tagOf(single)} withCode />}
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
        {single && single.items.length > 1 && (
          <DocToggle
            count={single.items.length}
            open={!!open[single.board]}
            onToggle={() => toggle(single.board)}
          />
        )}
        {c.boards.some((b) => b.lead.manySnoozes) && (
          <Pill tone="orange">多次延后</Pill>
        )}
        <span style={{ marginLeft: 'auto' }}>
          {lead && <DueText item={lead} />}
        </span>
      </div>
      {single ? (
        <>
          <div
            style={{
              display: 'flex',
              gap: 12,
              alignItems: 'flex-end',
            }}
          >
            <div style={{ flex: 1, minWidth: 0, display: 'grid', gap: 4 }}>
              <Reminder item={single.lead} />
              <Sop item={single.lead} />
            </div>
            <BoardActions
              c={c}
              b={single}
              canRecord={canRecord}
              onRecord={onRecord}
              onChanged={onChanged}
            />
          </div>
          {open[single.board] && <DueDocs items={single.items} />}
        </>
      ) : (
        c.boards.map((b) => (
          <div
            key={b.board}
            style={{
              display: 'grid',
              gap: 8,
              padding: '10px 12px',
              borderRadius: 10,
              background: palette.inset,
            }}
          >
            <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
              <StageTag item={tagOf(b)} withCode />
              <div style={{ flex: 1, minWidth: 0, display: 'grid', gap: 4 }}>
                <Reminder item={b.lead} />
                <Sop item={b.lead} />
              </div>
              {b.items.length > 1 && (
                <DocToggle
                  count={b.items.length}
                  open={!!open[b.board]}
                  onToggle={() => toggle(b.board)}
                />
              )}
              <BoardActions
                c={c}
                b={b}
                canRecord={canRecord}
                onRecord={onRecord}
                onChanged={onChanged}
              />
            </div>
            {open[b.board] && <DueDocs items={b.items} />}
          </div>
        ))
      )}
    </div>
  );
};

/** 勾选时带入的单据：这个客户全部到期单据 */
export const pickOfToday = (c: TodayCustomer): Picked => ({
  customerId: c.customerId,
  customerName: c.customerName,
  items: c.boards.flatMap((b) => b.items),
});

/** 今日待办（K1）：按客户，已逾期、今天两段，今天已跟进收在底部；超过 8 个客户折叠 */
export const TodayList: React.FC<{
  today?: Today;
  canRecord: boolean;
  picked: Record<number, Picked>;
  onPick: (p: Picked, on: boolean) => void;
  batchBar?: React.ReactNode;
  onRecord: (t: RecordTarget) => void;
  onChanged: () => void;
  onBrowse: () => void;
}> = ({
  today,
  canRecord,
  picked,
  onPick,
  batchBar,
  onRecord,
  onChanged,
  onBrowse,
}) => {
  const { palette } = useAppTheme();
  const [only, setOnly] = useState<Section>();
  const [all, setAll] = useState(false);
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
  let budget = all ? Number.POSITIVE_INFINITY : TODAY_FOLD;
  const shown = sections.map((x) => {
    const rows = x.rows.slice(0, Math.max(0, budget));
    budget -= rows.length;
    return { ...x, rows };
  });
  const totalRows = sections.reduce((n, x) => n + x.rows.length, 0);
  const shownRows = shown.reduce((n, x) => n + x.rows.length, 0);
  const card = (c: TodayCustomer, section: Section | 'handled') => (
    <TodayCard
      key={c.customerId}
      c={c}
      section={section}
      checked={!!picked[c.customerId]}
      canRecord={canRecord}
      onCheck={(on) => onPick(pickOfToday(c), on)}
      onRecord={onRecord}
      onChanged={onChanged}
    />
  );
  const chip = (
    key: Section | undefined,
    label: string,
    fg: string,
    bg: string,
  ) => (
    <button
      type="button"
      key={label}
      onClick={() => setOnly(key && only === key ? undefined : key)}
      style={{
        all: 'unset',
        cursor: key ? 'pointer' : 'default',
        padding: '4px 12px',
        borderRadius: 8,
        fontSize: 13,
        fontWeight: key && only === key ? 600 : 400,
        color: fg,
        background: bg,
        outline: key && only === key ? `1px solid ${fg}` : undefined,
      }}
    >
      {label}
    </button>
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
        <CheckCircleOutlined style={{ color: palette.green }} />
        <span style={{ flex: 1 }}>今天已跟进 {handled.length}</span>
        <span style={{ color: palette.link }}>
          {showHandled ? '收起' : '展开'}{' '}
          {showHandled ? <UpOutlined /> : <DownOutlined />}
        </span>
      </button>
      {showHandled && (
        <div style={{ display: 'grid', gap: 10, marginTop: 10 }}>
          {handled.map((c) => card(c, 'handled'))}
        </div>
      )}
    </div>
  );
  if (empty)
    return (
      <Card style={{ padding: '32px 20px', textAlign: 'center' }}>
        <CheckCircleOutlined
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
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        {chip(
          'overdue',
          `已逾期 ${today.overdue.length}`,
          palette.red,
          palette.redSoft,
        )}
        {chip(
          'today',
          `今天 ${today.today.length}`,
          palette.link,
          palette.accentSoft,
        )}
        {chip(
          undefined,
          `本周还有 ${today.weekRemaining}`,
          palette.sub,
          palette.inset,
        )}
      </div>
      {batchBar}
      {shown.map(
        (x) =>
          x.rows.length > 0 && (
            <div key={x.key}>
              <div
                style={{
                  fontSize: 13,
                  fontWeight: 600,
                  color: x.key === 'overdue' ? palette.red : palette.link,
                  margin: '16px 0 8px',
                }}
              >
                {x.title}
              </div>
              <div style={{ display: 'grid', gap: 10 }}>
                {x.rows.map((c) => card(c, x.key))}
              </div>
            </div>
          ),
      )}
      {sections.every((x) => x.rows.length === 0) && (
        <div style={{ padding: '16px 0', color: palette.mute }}>
          {only === 'overdue' ? '没有逾期的客户' : '今天到期的都处理完了'}
        </div>
      )}
      {shownRows < totalRows && (
        <div style={{ textAlign: 'center', marginTop: 12 }}>
          <Button type="link" onClick={() => setAll(true)}>
            还有 {totalRows - shownRows} 个客户 · 展开
          </Button>
        </div>
      )}
      {handledBlock}
    </Card>
  );
};
