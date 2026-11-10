import {
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  InfoCircleOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Checkbox,
  DatePicker,
  Input,
  Modal,
  Segmented,
  Space,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useRef, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { BOARD_LABEL, Card, fmtDate, iso, SnoozeButton } from './components';
import { adjustSuggest, dateShortcuts, suggestOf } from './nextDate';
import {
  ANSWER_STAGES,
  BATCH_MAX,
  type BatchEntry,
  type BatchResult,
  BOARD_SALES,
  CHANNEL_OPTIONS,
  type FollowupItem,
  followupApi,
  GROUPED_BOARDS,
  NEXT_AFTER_SALES,
  readBizError,
  SITUATION_OPTIONS,
} from './service';

const NEXT_MAX = 200;
const NOTE_MAX = 500;

/** 勾选的一个客户：今日待办为到期单据，列表为本板块全部单据 */
export interface Picked {
  customerId: number;
  customerName: string;
  items: FollowupItem[];
}

const newKey = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;

/** 到期、可延后的单据：下一步已到或已过、不在培育、不跟着售后单走 */
const isDue = (i: FollowupItem) =>
  !!i.nextActionDate &&
  !dayjs(i.nextActionDate).isAfter(dayjs(), 'day') &&
  !i.nurtureUntil &&
  i.nextActionSource !== NEXT_AFTER_SALES;

/** 一个客户在一个板块的单据不能批量跟进的原因 */
const skipOf = (board: number, items: FollowupItem[]) => {
  if (!GROUPED_BOARDS.includes(board))
    return `${BOARD_LABEL[board]}要单独记跟进`;
  const a = items.find((i) => ANSWER_STAGES.includes(i.stageCode));
  return a
    ? `${a.docNo ?? ''} 在 ${a.stageCode} ${a.stageName}，要回答阶段问题，请单独记跟进`
    : undefined;
};

const byBoard = (items: FollowupItem[]) => {
  const m = new Map<number, FollowupItem[]>();
  for (const i of items) m.set(i.board, [...(m.get(i.board) ?? []), i]);
  return m;
};

/** 今日待办的「全选」：已加载的客户全部选中时勾上，部分选中时半选 */
export interface SelectAll {
  checked: boolean;
  indeterminate: boolean;
  onChange: (on: boolean) => void;
}

/** 已选 n 个客户：批量延后、批量跟进、快速跟进、取消选择；今日待办左侧带「全选」 */
export const BatchBar: React.FC<{
  picked: Picked[];
  canRecord: boolean;
  selectAll?: SelectAll;
  onClear: () => void;
  onRecord: () => void;
  onQuick: () => void;
  onChanged: () => void;
}> = ({
  picked,
  canRecord,
  selectAll,
  onClear,
  onRecord,
  onQuick,
  onChanged,
}) => {
  const { palette } = useAppTheme();
  if (!picked.length && !selectAll) return null;
  if (!picked.length && selectAll)
    return (
      <div style={{ margin: '12px 0 0', padding: '0 16px' }}>
        <Checkbox
          checked={false}
          disabled={!canRecord}
          onChange={() => selectAll.onChange(true)}
        >
          全选
        </Checkbox>
      </div>
    );
  const entries: BatchEntry[] = picked
    .map((p) => ({
      customerId: p.customerId,
      itemIds: p.items.filter(isDue).map((i) => i.id),
    }))
    .filter((e) => e.itemIds.length > 0);
  return (
    <Card
      style={{
        padding: '10px 16px',
        margin: '12px 0',
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        border: `1px solid ${palette.accentLine}`,
        background: palette.accentSoft,
      }}
    >
      {selectAll ? (
        <Checkbox
          checked={selectAll.checked}
          indeterminate={selectAll.indeterminate}
          onChange={(e) => selectAll.onChange(e.target.checked)}
        >
          <b style={{ color: palette.ink }}>全选</b>
        </Checkbox>
      ) : (
        <Checkbox indeterminate checked={false} onChange={onClear} />
      )}
      <span style={{ color: palette.ink, flex: 1 }}>
        已选 {picked.length} 个客户
      </span>
      <SnoozeButton
        label="批量延后"
        entries={entries}
        disabled={!canRecord || entries.length === 0}
        onDone={() => {
          onClear();
          onChanged();
        }}
      />
      <Button size="small" disabled={!canRecord} onClick={onRecord}>
        批量跟进
      </Button>
      <Button
        type="primary"
        size="small"
        disabled={!canRecord}
        onClick={onQuick}
      >
        快速跟进
      </Button>
      <Button type="link" size="small" onClick={onClear}>
        取消选择
      </Button>
    </Card>
  );
};

/** 勾选客户：最多 BATCH_MAX 个，超出时提示 */
export const usePicks = () => {
  const { message } = App.useApp();
  const [picked, setPicked] = useState<Record<number, Picked>>({});
  const toggle = (p: Picked, on: boolean) =>
    setPicked((old) => {
      if (!on) {
        const { [p.customerId]: _, ...rest } = old;
        return rest;
      }
      if (Object.keys(old).length >= BATCH_MAX) {
        message.warning(`一次最多选 ${BATCH_MAX} 个客户`);
        return old;
      }
      return { ...old, [p.customerId]: p };
    });
  /** 全选：换成这些客户，最多 BATCH_MAX 个 */
  const replace = (list: Picked[]) => {
    if (list.length > BATCH_MAX)
      message.warning(`一次最多选 ${BATCH_MAX} 个客户，已选前 ${BATCH_MAX} 个`);
    setPicked(
      Object.fromEntries(
        list.slice(0, BATCH_MAX).map((p) => [p.customerId, p]),
      ),
    );
  };
  return {
    picked,
    list: Object.values(picked),
    toggle,
    replace,
    clear: () => setPicked({}),
  };
};

/** 批量跟进：每个客户各写一条跟进记录，作用到名下全部单据；不能批量的客户跳过 */
export const BatchFollowupModal: React.FC<{
  open: boolean;
  picked: Picked[];
  onClose: () => void;
  onDone: () => void;
  /** 结果里点「记跟进」：对这个客户单独记 */
  onRecordOne: (p: Picked) => void;
}> = ({ open, picked, onClose, onDone, onRecordOne }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [situation, setSituation] = useState(0);
  const [nextAction, setNextAction] = useState('');
  const [nextDate, setNextDate] = useState<Dayjs | null>(null);
  /** 手动选过日期后不再跟着推荐变 */
  const [dateTouched, setDateTouched] = useState(false);
  const pickDate = (d: Dayjs | null) => {
    setNextDate(d);
    setDateTouched(true);
  };
  const [channel, setChannel] = useState(1);
  const [note, setNote] = useState('');
  const [nextError, setNextError] = useState(false);
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<BatchResult>();
  /** 保存时的勾选：保存后列表刷新、勾选清空，结果里仍要用 */
  const [done, setDone] = useState<Picked[]>([]);
  const keyRef = useRef(newKey());

  useEffect(() => {
    if (!open) return;
    keyRef.current = newKey();
    setSituation(0);
    setNextAction('');
    setNextDate(null);
    setDateTouched(false);
    setChannel(1);
    setNote('');
    setNextError(false);
  }, [open]);

  const skips: { p: Picked; reason: string }[] = [];
  let saveCount = 0;
  let hasSales = false;
  for (const p of picked) {
    let ok = false;
    for (const [board, items] of byBoard(p.items)) {
      const reason = skipOf(board, items);
      if (reason) skips.push({ p, reason });
      else {
        ok = true;
        if (board === BOARD_SALES) hasSales = true;
      }
    }
    if (ok) saveCount += 1;
  }
  const docTotal = picked.reduce((n, p) => n + p.items.length, 0);
  const today = dayjs().startOf('day');
  // 推荐日期：全部单据中最早的，再按客户情况修正
  const suggest = adjustSuggest(
    suggestOf(
      picked.flatMap((p) => p.items),
      today,
    ),
    hasSales ? situation : 0,
    today,
  );
  const suggestDay = iso(suggest.date);
  useEffect(() => {
    if (open && !dateTouched) setNextDate(dayjs(suggestDay));
  }, [open, dateTouched, suggestDay]);

  const submit = async () => {
    if (!nextAction.trim()) {
      setNextError(true);
      return;
    }
    if (!nextDate) {
      message.warning('请选择什么时候做下一步');
      return;
    }
    setSaving(true);
    try {
      const r = await followupApi.batchRecord({
        entries: picked.map((p) => ({
          customerId: p.customerId,
          itemIds: p.items.map((i) => i.id),
        })),
        situation: hasSales ? situation : undefined,
        nextAction: nextAction.trim(),
        nextActionDate: iso(nextDate),
        channel,
        note: note.trim() || undefined,
        idempotencyKey: keyRef.current,
      });
      setDone(picked);
      setResult(r);
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const shortcuts = dateShortcuts(today);
  const label = (text: string, required?: boolean) => (
    <div
      style={{
        fontSize: 13,
        fontWeight: 600,
        color: palette.ink,
        margin: '14px 0 6px',
      }}
    >
      {text}
      {required && <span style={{ color: palette.red }}> *</span>}
    </div>
  );

  const failures = result?.failures ?? [];
  return (
    <>
      <Modal
        title={`批量跟进 · 已选 ${picked.length} 个客户`}
        open={open && !result}
        width={600}
        onCancel={onClose}
        destroyOnHidden
        footer={
          <Space>
            <Button onClick={onClose}>取消</Button>
            <Button
              type="primary"
              loading={saving}
              disabled={saveCount === 0}
              onClick={submit}
            >
              保存 {saveCount} 个客户
            </Button>
          </Space>
        }
      >
        <div
          style={{
            display: 'flex',
            gap: 6,
            flexWrap: 'wrap',
            alignItems: 'center',
            padding: '10px 12px',
            borderRadius: 10,
            background: palette.inset,
          }}
        >
          {picked.slice(0, 5).map((p) => (
            <span
              key={p.customerId}
              style={{
                padding: '1px 10px',
                borderRadius: 6,
                fontSize: 12,
                color: palette.link,
                background: palette.accentSoft,
              }}
            >
              {p.customerName} · {p.items.length} 张
            </span>
          ))}
          <span style={{ fontSize: 12, color: palette.mute }}>
            {picked.length > 5 ? `等 ${picked.length} 个客户，` : ''}共{' '}
            {docTotal} 张单据
          </span>
        </div>
        {skips.length > 0 && (
          <Alert
            style={{ marginTop: 12 }}
            type="warning"
            showIcon
            title={
              skips.length === 1
                ? `会跳过：${skips[0].p.customerName} 的 ${skips[0].reason}`
                : `会跳过 ${skips.length} 处：${skips
                    .map((s) => `${s.p.customerName} 的 ${s.reason}`)
                    .join('；')}`
            }
          />
        )}
        {hasSales && (
          <>
            {label('客户情况')}
            <Checkbox.Group
              value={SITUATION_OPTIONS.filter((o) => situation & o.value).map(
                (o) => o.value,
              )}
              options={SITUATION_OPTIONS}
              onChange={(vals) =>
                setSituation((vals as number[]).reduce((a, b) => a | b, 0))
              }
            />
          </>
        )}
        {label('下一步要做什么', true)}
        <Input
          value={nextAction}
          maxLength={NEXT_MAX}
          status={nextError ? 'error' : undefined}
          placeholder="例如：3 天后再发一次报价提醒，附最新库存"
          onChange={(e) => {
            setNextAction(e.target.value);
            if (e.target.value.trim()) setNextError(false);
          }}
        />
        {nextError && (
          <div style={{ fontSize: 12, color: palette.red, marginTop: 4 }}>
            请写下一步要做什么
          </div>
        )}
        {label('什么时候', true)}
        <Space wrap>
          {shortcuts.map((d) => (
            <Button
              key={d.label}
              size="small"
              type={nextDate?.isSame(d.value, 'day') ? 'primary' : 'default'}
              onClick={() => pickDate(d.value)}
            >
              {d.label}
            </Button>
          ))}
          <DatePicker
            size="small"
            value={nextDate}
            placeholder="选日期"
            onChange={pickDate}
            disabledDate={(d) => d.isBefore(today)}
          />
          {nextDate?.isSame(suggest.date, 'day') && suggest.reason && (
            <span style={{ fontSize: 12, color: palette.link }}>
              <ThunderboltOutlined /> 推荐：{suggest.reason}
            </span>
          )}
        </Space>
        {label('方式')}
        <Segmented
          value={channel}
          options={CHANNEL_OPTIONS}
          onChange={(v) => setChannel(v as number)}
        />
        {label('备注')}
        <Input
          value={note}
          maxLength={NOTE_MAX}
          onChange={(e) => setNote(e.target.value)}
        />
        <div
          style={{
            marginTop: 16,
            padding: '8px 12px',
            borderRadius: 10,
            fontSize: 12,
            color: palette.link,
            background: palette.accentSoft,
          }}
        >
          <InfoCircleOutlined /> 每个客户各写一条跟进记录，作用到名下全部单据
        </div>
      </Modal>
      <Modal
        open={!!result}
        width={480}
        closable={false}
        onCancel={() => {
          setResult(undefined);
          onClose();
        }}
        footer={
          <Button
            type="primary"
            onClick={() => {
              setResult(undefined);
              onClose();
            }}
          >
            知道了
          </Button>
        }
        title={
          <span>
            <CheckCircleOutlined
              style={{ color: palette.green, marginRight: 8 }}
            />
            已跟进 {result?.success ?? 0} 个客户
          </span>
        }
      >
        {result && nextDate && result.items > 0 && (
          <div style={{ color: palette.sub }}>
            {result.items} 张单据的下一步改到 {fmtDate(nextDate)}
          </div>
        )}
        {failures.length > 0 && (
          <div
            style={{
              marginTop: 12,
              padding: '10px 12px',
              borderRadius: 10,
              background: palette.inset,
              display: 'grid',
              gap: 6,
            }}
          >
            <div style={{ color: palette.orange, fontSize: 12 }}>
              <ExclamationCircleOutlined /> 跳过 {failures.length} 处
            </div>
            {failures.map((f) => {
              const p = done.find((x) => x.customerId === f.customerId);
              return (
                <div
                  key={`${f.customerId}-${f.reason}`}
                  style={{ display: 'flex', gap: 8, alignItems: 'center' }}
                >
                  <span style={{ flex: 1, color: palette.ink, fontSize: 13 }}>
                    {f.customerName} · {f.reason}
                  </span>
                  {p && (
                    <Button
                      type="link"
                      size="small"
                      onClick={() => {
                        setResult(undefined);
                        onClose();
                        onRecordOne(p);
                      }}
                    >
                      记跟进
                    </Button>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </Modal>
    </>
  );
};
