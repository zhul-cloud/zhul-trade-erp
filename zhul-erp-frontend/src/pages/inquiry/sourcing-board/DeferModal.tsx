import { App, DatePicker, Input, Modal, Skeleton } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { LevelPill } from '../shared/components';
import { boardApi, type DeferPreview, readBizError } from '../shared/service';

const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
/** 与后端一致：最多延后 180 天 */
const MAX_DAYS = 180;

export type DeferTarget =
  /** 延后选中的任务，或给已延后的任务改日期 */
  | { kind: 'tasks'; taskIds: number[]; current?: string }
  /** 延后整张询盘下还在待分配的任务 */
  | { kind: 'inquiry'; customerInquiryId: number };

/** 下周一：今天是周日时就是明天 */
export const nextMonday = (today: Dayjs) =>
  today.add((8 - today.day()) % 7 || 7, 'day');

export const deferOptions = (today: Dayjs) => [
  { label: '明天', date: today.add(1, 'day') },
  { label: '3 天后', date: today.add(3, 'day') },
  { label: '下周一', date: nextMonday(today) },
];

const fmt = (d: Dayjs) => d.format('YYYY-MM-DD');

/** 延后弹窗：选回来的日期（明天 / 3 天后 / 下周一 / 选日期）和可选原因 */
const DeferModal: React.FC<{
  target?: DeferTarget;
  onClose: () => void;
  onDone: () => void;
}> = ({ target, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [date, setDate] = useState<Dayjs>();
  /** 选中的是哪个选项（3 天后与下周一可能是同一天，只高亮点的那个） */
  const [choice, setChoice] = useState<string>();
  const [picking, setPicking] = useState(false);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState<DeferPreview>();
  const today = dayjs().startOf('day');
  const options = deferOptions(today);

  useEffect(() => {
    setDate(undefined);
    setChoice(undefined);
    setPicking(false);
    setReason('');
    setPreview(undefined);
    if (target?.kind === 'inquiry') {
      boardApi
        .deferPreview(target.customerInquiryId)
        .then(setPreview)
        .catch((e) => {
          message.error(readBizError(e).message);
          onClose();
        });
    }
  }, [target, message, onClose]);

  const count =
    target?.kind === 'tasks' ? target.taskIds.length : preview?.taskCount;
  const custom = choice === 'custom';

  const submit = async () => {
    if (!target || !date) return;
    setBusy(true);
    try {
      await boardApi.defer({
        ...(target.kind === 'tasks'
          ? { taskIds: target.taskIds }
          : { customerInquiryId: target.customerInquiryId }),
        deferUntil: fmt(date),
        reason: reason.trim() || undefined,
      });
      message.success(
        `已延后 ${count ?? ''} 个任务到 ${date.format('MM-DD')}，到时自动回到待分配`,
      );
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const optionStyle = (active: boolean): React.CSSProperties => ({
    display: 'grid',
    gap: 2,
    textAlign: 'left',
    padding: '10px 14px',
    borderRadius: 10,
    cursor: 'pointer',
    border: `1px solid ${active ? palette.link : palette.hairline}`,
    background: active ? palette.accentSoft : 'transparent',
    color: active ? palette.link : palette.ink,
    font: 'inherit',
  });

  return (
    <Modal
      open={!!target}
      title={
        target?.kind === 'inquiry'
          ? '延后整张询盘'
          : target?.current
            ? '改延后日期'
            : `延后 ${count} 个任务`
      }
      okText={count ? `延后 ${count} 个任务` : '延后'}
      cancelText="取消"
      okButtonProps={{ disabled: !date || !count }}
      confirmLoading={busy}
      onOk={submit}
      onCancel={onClose}
      width={560}
      destroyOnHidden
    >
      <div style={{ display: 'grid', gap: 16 }}>
        {target?.kind === 'inquiry' &&
          (preview ? (
            <div
              style={{
                display: 'grid',
                gap: 10,
                padding: 16,
                borderRadius: 12,
                background: palette.inset,
              }}
            >
              <span style={{ display: 'inline-flex', gap: 8 }}>
                <b style={{ color: palette.link }}>{preview.inquiryCode}</b>
                <LevelPill value={preview.level} />
              </span>
              <span style={{ color: palette.sub, fontSize: 13 }}>
                {[
                  preview.customerName,
                  preview.salesName && `业务员 ${preview.salesName}`,
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </span>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
                }}
              >
                {[
                  [preview.taskCount, '个待分配任务'],
                  [preview.itemCount, '个型号'],
                  [preview.otherCount, '个已在询价中，不受影响'],
                ].map(([n, label], i) => (
                  <span key={label} style={{ display: 'grid' }}>
                    <b
                      style={{
                        fontSize: 22,
                        color: i === 2 ? palette.mute : palette.ink,
                      }}
                    >
                      {n}
                    </b>
                    <span style={{ color: palette.mute, fontSize: 12 }}>
                      {label}
                    </span>
                  </span>
                ))}
              </div>
            </div>
          ) : (
            <Skeleton active paragraph={{ rows: 2 }} />
          ))}
        <div style={{ display: 'grid', gap: 8 }}>
          <span style={{ color: palette.sub }}>延后到</span>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
              gap: 10,
            }}
          >
            {options.map((o) => {
              const active = choice === o.label;
              return (
                <button
                  key={o.label}
                  type="button"
                  style={optionStyle(active)}
                  onClick={() => {
                    setDate(o.date);
                    setChoice(o.label);
                    setPicking(false);
                  }}
                >
                  <span style={{ fontWeight: 600 }}>{o.label}</span>
                  <span
                    style={{
                      fontSize: 12,
                      color: active ? palette.link : palette.mute,
                    }}
                  >
                    {o.date.format('MM-DD')} {WEEK[o.date.day()]}
                  </span>
                </button>
              );
            })}
            <div style={optionStyle(!!custom || picking)}>
              <DatePicker
                variant="borderless"
                placeholder="选日期"
                value={custom ? date : null}
                open={picking}
                onOpenChange={setPicking}
                onChange={(d) => {
                  if (!d) return;
                  setDate(d.startOf('day'));
                  setChoice('custom');
                }}
                disabledDate={(d) =>
                  !d.isAfter(today, 'day') ||
                  d.isAfter(today.add(MAX_DAYS, 'day'), 'day')
                }
                style={{ padding: 0 }}
                aria-label="选择延后日期"
              />
            </div>
          </div>
          <span style={{ color: palette.mute, fontSize: 12 }}>
            到期当天自动回到待分配，等待时长从那天算起
          </span>
        </div>
        <div style={{ display: 'grid', gap: 8 }}>
          <span style={{ color: palette.sub }}>原因（选填）</span>
          <Input
            maxLength={100}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="如：客户说下月初再报价"
          />
        </div>
      </div>
    </Modal>
  );
};

export default DeferModal;
