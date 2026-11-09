import { DownOutlined } from '@ant-design/icons';
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
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import { Card, Pill } from '@/pages/crm/opportunity/components';
import { GRADE_NAMES } from '@/pages/customer/profile/service';
import { GradeBadge, Stars } from '@/pages/customer/profile/ui';
import { useAppTheme } from '@/theme/AppTheme';
import { styleOf } from './boardStyle';
import {
  type BatchEntry,
  type Blocker,
  type FollowupItem,
  followupApi,
  readBizError,
} from './service';

export { Card, Pill };

const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

export const fmtDate = (d?: string | Dayjs) =>
  d ? `${dayjs(d).format('MM-DD')} ${WEEK[dayjs(d).day()]}` : '—';

export const iso = (d: Dayjs) => d.format('YYYY-MM-DD');

/** 下一步的日期：逾期标红、今天标橙 */
export const DueText: React.FC<{
  item: Pick<FollowupItem, 'nextActionDate'>;
}> = ({ item }) => {
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
export const ValueCell: React.FC<{
  item: Pick<FollowupItem, 'bizGrade' | 'potentialRating'>;
}> = ({ item }) => {
  const g = item.bizGrade ? GRADE_NAMES.indexOf(item.bizGrade) : 0;
  return (
    <span style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
      <GradeBadge value={g > 0 ? g : 0} />
      <Stars value={item.potentialRating} />
    </span>
  );
};

export const BOARD_LABEL: Record<number, string> = {
  1: '主动开发',
  2: '商机成交',
  3: '订单履约',
  4: '售后评价',
  5: '客户经营',
};

/** 板块 · 阶段，用板块颜色（K1） */
export const StageTag: React.FC<{
  item: Pick<FollowupItem, 'board' | 'stageName'> & { stageCode?: string };
  /** 阶段名前带阶段码，如「S4 已报价」 */
  withCode?: boolean;
}> = ({ item, withCode }) => {
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
      {BOARD_LABEL[item.board] ?? '商机成交'} ·{' '}
      {withCode && item.stageCode ? `${item.stageCode} ` : ''}
      {item.stageName}
    </span>
  );
};

/** 客户名称：点击进客户详情 */
export const CustomerLink: React.FC<{
  item: Pick<FollowupItem, 'customerId' | 'customerName'>;
}> = ({ item }) => {
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

/**
 * 延后：明天 / 3 天后 / 下周一 / 选日期。
 * 传 item 延后这一张；传 entries 按客户一起延后（今日待办的客户卡片、批量延后）
 */
export const SnoozeButton: React.FC<{
  item?: FollowupItem;
  entries?: BatchEntry[];
  label?: string;
  disabled?: boolean;
  onDone: () => void;
}> = ({ item, entries, label = '延后', disabled, onDone }) => {
  const { message, modal } = App.useApp();
  const [picking, setPicking] = useState(false);
  const [date, setDate] = useState<Dayjs | null>(null);
  const [saving, setSaving] = useState(false);
  const submit = async (d: Dayjs) => {
    setSaving(true);
    try {
      if (entries) {
        const r = await followupApi.batchSnooze(entries, iso(d));
        if (r.failures.length)
          modal.warning({
            title: `已延后 ${r.success} 个客户，${r.failures.length} 个没有延后`,
            content: r.failures
              .map((f) => `${f.customerName}：${f.reason}`)
              .join('\n'),
          });
        else message.success(`已延后到 ${fmtDate(d)}`);
      } else if (item) {
        await followupApi.snooze(item.id, iso(d));
        message.success(`已延后到 ${fmtDate(d)}`);
      }
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
          {label} <DownOutlined />
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
