import {
  ArrowRightOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DownOutlined,
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
  Segmented,
  Select,
  Skeleton,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import { Card, Pill } from '@/pages/crm/opportunity/components';
import { GRADE_NAMES } from '@/pages/customer/profile/service';
import { GradeBadge, Stars } from '@/pages/customer/profile/ui';
import { useAppTheme } from '@/theme/AppTheme';
import {
  type Blocker,
  type FollowupItem,
  followupApi,
  GROUP,
  NEXT_USER,
  type Overview,
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

/** 板块 · 阶段 */
export const StageTag: React.FC<{ item: FollowupItem }> = ({ item }) => (
  <Pill tone={item.stageGroup === GROUP.AFTER_PI ? 'green' : 'accent'}>
    商机成交 · {item.stageCode} {item.stageName}
  </Pill>
);

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

let blockerCache: Promise<Blocker[]> | undefined;
/** 卡点字典（整页共用一份） */
export const useBlockers = () => {
  const [list, setList] = useState<Blocker[]>([]);
  useEffect(() => {
    blockerCache ??= followupApi.blockers().catch((e) => {
      blockerCache = undefined;
      throw e;
    });
    blockerCache.then(setList).catch(() => setList([]));
  }, []);
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

// ---------------------------------------------------------------- 链路卡片

/** 今日待办 + 五个板块（K1 顶部） */
export const FlowCards: React.FC<{ overview?: Overview }> = ({ overview }) => {
  const { palette } = useAppTheme();
  if (!overview)
    return (
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <Skeleton active paragraph={{ rows: 2 }} />
      </Card>
    );
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(6, minmax(132px, 1fr))',
        gap: 10,
        marginBottom: 16,
        alignItems: 'stretch',
        overflowX: 'auto',
      }}
    >
      <Card
        style={{
          padding: 16,
          background: palette.violetSoft,
          border: `1px solid ${palette.violet}`,
        }}
      >
        <div style={{ color: palette.violet, fontWeight: 600 }}>
          <ClockCircleOutlined /> 今日待办
        </div>
        <div
          style={{
            fontSize: 28,
            fontWeight: 700,
            color: palette.ink,
            margin: '6px 0 2px',
          }}
        >
          {overview.todayOverdue + overview.todayDue}
        </div>
        <div style={{ fontSize: 12, color: palette.sub }}>
          <span style={{ color: palette.red }}>
            逾期 {overview.todayOverdue}
          </span>
          {' · '}今天 {overview.todayDue}
        </div>
      </Card>
      {overview.boards.map((b, i) => (
        <div
          key={b.board}
          style={{ display: 'flex', alignItems: 'stretch', gap: 6 }}
        >
          {i > 0 && (
            <ArrowRightOutlined
              style={{ color: palette.faint, alignSelf: 'center' }}
            />
          )}
          <Card
            style={{
              padding: 16,
              flex: 1,
              opacity: b.available ? 1 : 0.55,
              border: b.available
                ? `1px solid ${palette.accentLine}`
                : undefined,
            }}
          >
            <div style={{ fontSize: 12, color: palette.mute }}>
              第 {b.step} 步
            </div>
            <div style={{ fontWeight: 600, color: palette.ink }}>
              {b.name}
              {b.available && b.overdue > 0 && (
                <span style={{ marginLeft: 6 }}>
                  <Pill tone="red">逾期 {b.overdue}</Pill>
                </span>
              )}
            </div>
            {b.available ? (
              <>
                <div
                  style={{
                    fontSize: 22,
                    fontWeight: 700,
                    color: palette.ink,
                    marginTop: 4,
                  }}
                >
                  {b.customers}
                  <span
                    style={{
                      fontSize: 12,
                      color: palette.mute,
                      fontWeight: 400,
                    }}
                  >
                    {' '}
                    个客户
                  </span>
                </div>
                <div style={{ fontSize: 12, color: palette.sub }}>
                  {b.groups.map((g) => `${g.name} ${g.count}`).join(' · ')}
                  {b.nurture > 0 && ` · 培育中 ${b.nurture}`}
                </div>
              </>
            ) : (
              <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
                后续上线
              </div>
            )}
          </Card>
        </div>
      ))}
    </div>
  );
};

// ---------------------------------------------------------------- 今日待办

const TodayRow: React.FC<{
  item: FollowupItem;
  canRecord: boolean;
  onRecord: (item: FollowupItem) => void;
  onChanged: () => void;
}> = ({ item, canRecord, onRecord, onChanged }) => {
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
        padding: '12px 8px',
        margin: '0 -8px',
        borderRadius: 10,
        borderTop: `1px solid ${hover ? 'transparent' : palette.hairline}`,
        background: hover ? palette.hover : undefined,
        transition: 'background .15s',
      }}
    >
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
          <span style={{ color: palette.mute }}>
            {mine ? '我定的下一步：' : '系统提醒：'}
          </span>
          {item.nextAction || '—'}
        </div>
        {item.sopHint && (
          <div style={{ fontSize: 12, color: palette.sub }}>
            SOP 建议：{item.sopHint}
          </div>
        )}
      </div>
      <div style={{ textAlign: 'right', minWidth: 72 }}>
        <DueText item={item} />
      </div>
      <SnoozeButton item={item} disabled={!canRecord} onDone={onChanged} />
      <Button
        type="primary"
        size="small"
        disabled={!canRecord}
        onClick={() => onRecord(item)}
      >
        记跟进
      </Button>
    </div>
  );
};

export const TodayList: React.FC<{
  today?: Today;
  canRecord: boolean;
  onRecord: (item: FollowupItem) => void;
  onChanged: () => void;
}> = ({ today, canRecord, onRecord, onChanged }) => {
  const { palette } = useAppTheme();
  const [tab, setTab] = useState<'overdue' | 'today'>('overdue');
  useEffect(() => {
    if (today && today.overdue.length === 0 && today.today.length > 0)
      setTab('today');
  }, [today]);
  if (!today)
    return (
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <Skeleton active />
      </Card>
    );
  const list = tab === 'overdue' ? today.overdue : today.today;
  const empty = today.overdue.length === 0 && today.today.length === 0;
  return (
    <Card style={{ padding: '16px 20px', marginBottom: 16 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          flexWrap: 'wrap',
          marginBottom: 8,
        }}
      >
        <b style={{ color: palette.ink, fontSize: 16 }}>今日待办</b>
        {!empty && (
          <Segmented
            value={tab}
            onChange={(v) => setTab(v as 'overdue' | 'today')}
            options={[
              { value: 'overdue', label: `已逾期 ${today.overdue.length}` },
              { value: 'today', label: `今天 ${today.today.length}` },
            ]}
          />
        )}
        <span style={{ fontSize: 12, color: palette.mute }}>
          本周还有 {today.weekRemaining} 个 · 明天 {today.tomorrow} 个
        </span>
      </div>
      {empty ? (
        <div
          style={{
            textAlign: 'center',
            padding: '24px 0',
            color: palette.sub,
          }}
        >
          <CheckCircleOutlined
            style={{ fontSize: 28, color: palette.green, marginBottom: 8 }}
          />
          <div style={{ fontWeight: 600, color: palette.ink }}>
            今天的跟进都处理完了
          </div>
          <div style={{ fontSize: 12 }}>
            明天有 {today.tomorrow} 个，可以先看看下面报价后、PI 后的客户
          </div>
        </div>
      ) : list.length === 0 ? (
        <div style={{ padding: '16px 0', color: palette.mute }}>
          {tab === 'overdue' ? '没有逾期的跟进' : '今天到期的都处理完了'}
        </div>
      ) : (
        list.map((it) => (
          <TodayRow
            key={it.id}
            item={it}
            canRecord={canRecord}
            onRecord={onRecord}
            onChanged={onChanged}
          />
        ))
      )}
    </Card>
  );
};
