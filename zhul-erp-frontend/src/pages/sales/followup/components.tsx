import {
  BellOutlined,
  BulbOutlined,
  CheckCircleOutlined,
  DownOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  App,
  Button,
  DatePicker,
  Dropdown,
  Form,
  Input,
  Modal,
  Select,
  Skeleton,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import { Card, Pill } from '@/pages/crm/opportunity/components';
import { GRADE_NAMES } from '@/pages/customer/profile/service';
import { GradeBadge, Stars } from '@/pages/customer/profile/ui';
import { useAppTheme } from '@/theme/AppTheme';
import { styleOf, TODAY_COLOR } from './boardStyle';
import {
  type Blocker,
  type FollowupItem,
  followupApi,
  NEXT_USER,
  readBizError,
  type Today,
} from './service';

export { Card, Pill };

const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

export const fmtDate = (d?: string | Dayjs) =>
  d ? `${dayjs(d).format('MM-DD')} ${WEEK[dayjs(d).day()]}` : '—';

export const iso = (d: Dayjs) => d.format('YYYY-MM-DD');

/** 下一步的日期：逾期标红、今天标橙 */
export const DueText: React.FC<{ item: FollowupItem }> = ({ item }) => {
  const { palette } = useAppTheme();
  if (!item.nextActionDate)
    return <span style={{ color: palette.mute }}>—</span>;
  const days = dayjs().startOf('day').diff(dayjs(item.nextActionDate), 'day');
  if (days > 0)
    return (
      <span style={{ color: palette.red, fontWeight: 600 }}>
        逾期 {days} 天
      </span>
    );
  if (days === 0)
    return <span style={{ color: palette.orange, fontWeight: 600 }}>今天</span>;
  return (
    <span style={{ color: palette.sub }}>{fmtDate(item.nextActionDate)}</span>
  );
};

/** 经营等级 + 潜力等级 */
export const ValueCell: React.FC<{ item: FollowupItem }> = ({ item }) => {
  const g = item.bizGrade ? GRADE_NAMES.indexOf(item.bizGrade) : 0;
  return (
    <span style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
      <GradeBadge value={g > 0 ? g : 0} />
      <Stars value={item.potentialRating} />
    </span>
  );
};

const BOARD_LABEL: Record<number, string> = {
  1: '主动开发',
  2: '商机成交',
  3: '订单履约',
  4: '售后评价',
  5: '客户经营',
};

/** 板块 · 阶段，用板块颜色（K1） */
export const StageTag: React.FC<{ item: FollowupItem }> = ({ item }) => {
  const s = styleOf(item.board);
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        padding: '1px 8px',
        borderRadius: 6,
        fontSize: 12,
        color: s.color,
        background: `${s.color}14`,
        whiteSpace: 'nowrap',
      }}
    >
      {BOARD_LABEL[item.board] ?? '商机成交'} · {item.stageName}
    </span>
  );
};

/** 客户名称：点击进客户详情 */
export const CustomerLink: React.FC<{ item: FollowupItem }> = ({ item }) => {
  const { palette } = useAppTheme();
  return (
    <button
      type="button"
      onClick={() => history.push(`/customer/list/${item.customerId}`)}
      style={{
        all: 'unset',
        cursor: 'pointer',
        fontWeight: 600,
        color: palette.ink,
      }}
    >
      {item.customerName}
    </button>
  );
};

// ---------------------------------------------------------------- 延后

/** 延后：明天 / 3 天后 / 下周一 / 选日期 */
export const SnoozeButton: React.FC<{
  item: FollowupItem;
  disabled?: boolean;
  onDone: () => void;
}> = ({ item, disabled, onDone }) => {
  const { message } = App.useApp();
  const [picking, setPicking] = useState(false);
  const [date, setDate] = useState<Dayjs | null>(null);
  const [saving, setSaving] = useState(false);
  const submit = async (d: Dayjs) => {
    setSaving(true);
    try {
      await followupApi.snooze(item.id, iso(d));
      message.success(`已延后到 ${fmtDate(d)}`);
      setPicking(false);
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  const today = dayjs().startOf('day');
  const nextMonday = today.add((8 - today.day()) % 7 || 7, 'day');
  return (
    <>
      <Dropdown
        disabled={disabled}
        trigger={['click']}
        menu={{
          items: [
            { key: '1', label: '明天' },
            { key: '3', label: '3 天后' },
            { key: 'mon', label: `下周一（${nextMonday.format('MM-DD')}）` },
            { type: 'divider' },
            { key: 'pick', label: '选日期…' },
          ],
          onClick: ({ key }) => {
            if (key === 'pick') {
              setDate(null);
              setPicking(true);
            } else if (key === 'mon') submit(nextMonday);
            else submit(today.add(Number(key), 'day'));
          },
        }}
      >
        <Button size="small" loading={saving && !picking}>
          延后 <DownOutlined />
        </Button>
      </Dropdown>
      <Modal
        title="延后到哪天"
        open={picking}
        width={360}
        onCancel={() => setPicking(false)}
        okButtonProps={{ disabled: !date, loading: saving }}
        onOk={() => date && submit(date)}
        destroyOnHidden
      >
        <DatePicker
          style={{ width: '100%' }}
          value={date}
          onChange={setDate}
          disabledDate={(d) => !d.isAfter(today)}
        />
      </Modal>
    </>
  );
};

// ---------------------------------------------------------------- 转培育

const blockerCache = new Map<number, Promise<Blocker[]>>();
/** 卡点字典（按板块，整页共用一份）；默认商机成交 */
export const useBlockers = (board = 2) => {
  const [list, setList] = useState<Blocker[]>([]);
  useEffect(() => {
    let cached = blockerCache.get(board);
    if (!cached) {
      cached = followupApi.blockers(board).catch((e) => {
        blockerCache.delete(board);
        throw e;
      });
      blockerCache.set(board, cached);
    }
    let alive = true;
    cached.then((l) => alive && setList(l)).catch(() => alive && setList([]));
    return () => {
      alive = false;
    };
  }, [board]);
  return list;
};

export const NurtureModal: React.FC<{
  item?: FollowupItem;
  defaultDays?: number;
  onClose: () => void;
  onDone: () => void;
}> = ({ item, defaultDays = 60, onClose, onDone }) => {
  const { message } = App.useApp();
  const blockers = useBlockers();
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (item)
      form.setFieldsValue({
        reason: item.blockerCode || undefined,
        until: dayjs().add(defaultDays, 'day'),
        note: '',
      });
  }, [item, defaultDays, form]);
  const submit = async () => {
    if (!item) return;
    const v = await form.validateFields();
    setSaving(true);
    try {
      await followupApi.nurture(item.id, {
        reason: v.reason,
        until: iso(v.until),
        note: v.note,
      });
      message.success(`已转培育，${fmtDate(v.until)} 提醒重新激活`);
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      title={`转培育 · ${item?.customerName ?? ''}`}
      open={!!item}
      onCancel={onClose}
      onOk={submit}
      okText="转培育"
      okButtonProps={{ loading: saving }}
      destroyOnHidden
    >
      <p style={{ marginTop: 0 }}>
        转培育后不再按节奏提醒，到期那天自动回到待办；期间客户有新动静（新询盘、报价、付款）会提前回来。
      </p>
      <Form form={form} layout="vertical">
        <Form.Item
          name="reason"
          label="培育原因"
          rules={[{ required: true, message: '请选择培育原因' }]}
        >
          <Select
            placeholder="按卡点选"
            options={blockers.map((b) => ({ value: b.code, label: b.name }))}
          />
        </Form.Item>
        <Form.Item
          name="until"
          label="哪天重新激活"
          rules={[{ required: true, message: '请选择日期' }]}
        >
          <DatePicker
            style={{ width: '100%' }}
            disabledDate={(d) => !d.isAfter(dayjs().startOf('day'))}
          />
        </Form.Item>
        <Form.Item name="note" label="说明">
          <Input.TextArea rows={2} maxLength={200} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
};

// ---------------------------------------------------------------- 今日待办

const TODAY_FOLD = 8;

/** 一条待办（K1）：左侧色条区分逾期 / 今天；系统提醒或我定的下一步；SOP 建议 */
const TodayRow: React.FC<{
  item: FollowupItem;
  overdue: boolean;
  canRecord: boolean;
  onRecord: (item: FollowupItem) => void;
  onChanged: () => void;
}> = ({ item, overdue, canRecord, onRecord, onChanged }) => {
  const { palette } = useAppTheme();
  const mine = item.nextActionSource === NEXT_USER;
  const [hover, setHover] = useState(false);
  return (
    <div
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: 'flex',
        gap: 16,
        alignItems: 'center',
        padding: '12px 16px 12px 18px',
        borderRadius: 12,
        border: `1px solid ${hover ? palette.accentLine : palette.hairline}`,
        background: hover ? palette.hover : palette.card,
        position: 'relative',
        transition: 'background .15s, border-color .15s',
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
          background: overdue ? palette.red : TODAY_COLOR,
        }}
      />
      <div style={{ flex: 1, minWidth: 0, display: 'grid', gap: 4 }}>
        <div
          style={{
            display: 'flex',
            gap: 8,
            alignItems: 'center',
            flexWrap: 'wrap',
          }}
        >
          <StageTag item={item} />
          <CustomerLink item={item} />
          <ValueCell item={item} />
          {item.country && (
            <span style={{ fontSize: 12, color: palette.mute }}>
              {item.country}
            </span>
          )}
          {item.ownerName && (
            <span style={{ fontSize: 12, color: palette.mute }}>
              · {item.ownerName}
            </span>
          )}
          {item.manySnoozes && <Pill tone="orange">多次延后</Pill>}
        </div>
        <div style={{ color: palette.ink, fontSize: 13 }}>
          <span style={{ color: palette.orange, marginRight: 6 }}>
            {mine ? <UserOutlined /> : <BellOutlined />}
          </span>
          <span style={{ color: palette.sub }}>
            {mine ? '我定的下一步：' : '系统提醒：'}
          </span>
          {item.nextAction || '—'}
        </div>
        {item.sopHint && (
          <div style={{ fontSize: 12, color: palette.link }}>
            <BulbOutlined style={{ marginRight: 6 }} />
            SOP 建议：{item.sopHint}
          </div>
        )}
      </div>
      <div style={{ display: 'grid', gap: 8, justifyItems: 'end' }}>
        <DueText item={item} />
        <span style={{ display: 'inline-flex', gap: 8 }}>
          <SnoozeButton item={item} disabled={!canRecord} onDone={onChanged} />
          <Button
            type="primary"
            size="small"
            disabled={!canRecord}
            onClick={() => onRecord(item)}
          >
            记跟进
          </Button>
        </span>
      </div>
    </div>
  );
};

/** 今日待办（K1）：已逾期、今天两段，按客户价值排；超过 8 条折叠 */
export const TodayList: React.FC<{
  today?: Today;
  canRecord: boolean;
  onRecord: (item: FollowupItem) => void;
  onChanged: () => void;
  onBrowse: () => void;
}> = ({ today, canRecord, onRecord, onChanged, onBrowse }) => {
  const { palette } = useAppTheme();
  const [only, setOnly] = useState<'overdue' | 'today'>();
  const [all, setAll] = useState(false);
  if (!today)
    return (
      <Card style={{ padding: 20 }}>
        <Skeleton active />
      </Card>
    );
  const empty = today.overdue.length === 0 && today.today.length === 0;
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
          明天有 {today.tomorrow} 项，可以先看看本周还有哪些客户该联系
        </div>
        <Button onClick={onBrowse}>看各板块的客户</Button>
      </Card>
    );
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
  const chip = (
    key: 'overdue' | 'today' | undefined,
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
                {x.rows.map((it) => (
                  <TodayRow
                    key={it.id}
                    item={it}
                    overdue={x.key === 'overdue'}
                    canRecord={canRecord}
                    onRecord={onRecord}
                    onChanged={onChanged}
                  />
                ))}
              </div>
            </div>
          ),
      )}
      {sections.every((x) => x.rows.length === 0) && (
        <div style={{ padding: '16px 0', color: palette.mute }}>
          {only === 'overdue' ? '没有逾期的跟进' : '今天到期的都处理完了'}
        </div>
      )}
      {shownRows < totalRows && (
        <div style={{ textAlign: 'center', marginTop: 12 }}>
          <Button type="link" onClick={() => setAll(true)}>
            还有 {totalRows - shownRows} 项 · 展开
          </Button>
        </div>
      )}
    </Card>
  );
};
