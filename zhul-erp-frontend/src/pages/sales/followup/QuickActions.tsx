import {
  ArrowRightOutlined,
  CheckCircleOutlined,
  CheckOutlined,
  DownOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  HistoryOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import { App, Button, DatePicker, Dropdown, Input, Modal, Space } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useRef, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import type { Picked } from './Batch';
import { fmtDate, iso } from './components';
import { quickDate, suggestOf } from './nextDate';
import {
  ADJUST_REASONS,
  adjustSegment,
  type BatchResult,
  CONTACT_BACK_DAYS,
  type FollowupItem,
  followupApi,
  type QuickResult,
  readBizError,
  TARGET,
} from './service';

const NOTE_MAX = 200;

const newKey = () =>
  (typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`
  ).slice(0, 30);

// ---------------------------------------------------------------- 快速跟进结果

let resultsCache: Promise<Record<string, QuickResult[]>> | undefined;
/** 各阶段的快速跟进结果（整页共用一份） */
export const useQuickResults = () => {
  const [map, setMap] = useState<Record<string, QuickResult[]>>({});
  useEffect(() => {
    if (!resultsCache)
      resultsCache = followupApi.quickResults().catch((e) => {
        resultsCache = undefined;
        throw e;
      });
    let alive = true;
    resultsCache.then((m) => alive && setMap(m)).catch(() => {});
    return () => {
      alive = false;
    };
  }, []);
  return map;
};

/** 所选单据各阶段都有的结果；阶段相同时就是该阶段的结果 */
export const commonResults = (
  items: FollowupItem[],
  map: Record<string, QuickResult[]>,
) => {
  const stages = [...new Set(items.map((i) => i.stageCode))];
  if (!stages.length) return [];
  return (map[stages[0]] ?? []).filter((r) =>
    stages.every((s) => (map[s] ?? []).some((x) => x.code === r.code)),
  );
};

const effectText = (
  r: QuickResult,
  items: FollowupItem[],
  today: Dayjs,
): string => {
  const s = suggestOf(items, today);
  const d = quickDate(r.dateRule, s, today);
  const stay = r.advanceTo
    ? `推进到 ${r.advanceTo}`
    : `停在 ${items[0]?.stageCode ?? ''}`;
  const why = r.dateRule === 'NORMAL' && s.reason ? `（${s.reason}）` : '';
  return `${stay} · 下一步 ${d.format('MM-DD')}${why}`;
};

/** 单个「已跟进」：下拉选一个结果就保存，作用于这个客户在该板块的单据 */
export const QuickButton: React.FC<{
  customerId: number;
  items: FollowupItem[];
  disabled?: boolean;
  onRecord: () => void;
  onDone: () => void;
}> = ({ customerId, items, disabled, onRecord, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const map = useQuickResults();
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState<string>();
  const any = items.some((i) => (map[i.stageCode] ?? []).length > 0);
  if (!any) return null;
  const results = commonResults(items, map);
  const today = dayjs().startOf('day');
  const stages = [
    ...new Set(items.map((i) => `${i.stageCode} ${i.stageName}`)),
  ];
  const save = async (r: QuickResult) => {
    setSaving(r.code);
    try {
      await followupApi.quick({
        customerId,
        itemIds: items.map((i) => i.id),
        result: r.code,
        idempotencyKey: newKey(),
      });
      message.success(
        r.advanceTo ? `已记录，推进到 ${r.advanceTo}` : '已记录这次跟进',
      );
      setOpen(false);
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(undefined);
    }
  };
  const row = (r: QuickResult) => (
    <button
      type="button"
      key={r.code}
      disabled={!!saving}
      onClick={() => save(r)}
      style={{
        all: 'unset',
        cursor: saving ? 'wait' : 'pointer',
        display: 'grid',
        gap: 2,
        padding: '8px 12px',
        borderRadius: 8,
        opacity: saving && saving !== r.code ? 0.5 : 1,
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.background = palette.hover;
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.background = 'transparent';
      }}
    >
      <span style={{ fontWeight: 600, color: palette.ink, fontSize: 13 }}>
        {r.label}
      </span>
      <span style={{ fontSize: 12, color: palette.mute }}>
        {effectText(r, items, today)}
      </span>
    </button>
  );
  return (
    <Dropdown
      open={open}
      onOpenChange={setOpen}
      disabled={disabled}
      trigger={['click']}
      placement="bottomRight"
      popupRender={() => (
        <div
          style={{
            width: 300,
            padding: 6,
            borderRadius: 12,
            background: palette.card,
            border: `1px solid ${palette.hairline}`,
            boxShadow: '0 8px 24px -4px rgba(0,0,0,0.18)',
          }}
        >
          <div
            style={{ fontSize: 12, color: palette.mute, padding: '6px 12px' }}
          >
            {stages.length === 1
              ? `${stages[0]} · 选一个结果就好`
              : '几张单据阶段不同'}
          </div>
          {results.length ? (
            results.map(row)
          ) : (
            <div
              style={{ padding: '8px 12px', fontSize: 13, color: palette.sub }}
            >
              单据阶段不同，请用记一次跟进
            </div>
          )}
          <button
            type="button"
            onClick={() => {
              setOpen(false);
              onRecord();
            }}
            style={{
              all: 'unset',
              cursor: 'pointer',
              display: 'block',
              padding: '8px 12px',
              fontSize: 12,
              color: palette.link,
              borderTop: `1px solid ${palette.hairline}`,
              marginTop: 4,
            }}
          >
            <EditOutlined /> 要写细节，用「记跟进」
          </button>
        </div>
      )}
    >
      <Button size="small" icon={<CheckOutlined />} loading={!!saving}>
        已跟进 <DownOutlined />
      </Button>
    </Dropdown>
  );
};

// ---------------------------------------------------------------- 批量快速跟进

const SKIP = 'SKIP';

interface QuickGroup {
  stage: string;
  stageName: string;
  rows: { p: Picked; items: FollowupItem[] }[];
  results: QuickResult[];
}

/** 按阶段分组：一个客户的单据取所在阶段最多的那组；没有快速结果的阶段不列 */
const groupsOf = (picked: Picked[], map: Record<string, QuickResult[]>) => {
  const groups = new Map<string, QuickGroup>();
  const none: Picked[] = [];
  for (const p of picked) {
    const byStage = new Map<string, FollowupItem[]>();
    for (const i of p.items)
      if ((map[i.stageCode] ?? []).length)
        byStage.set(i.stageCode, [...(byStage.get(i.stageCode) ?? []), i]);
    if (!byStage.size) {
      none.push(p);
      continue;
    }
    for (const [stage, items] of byStage) {
      const g = groups.get(stage) ?? {
        stage,
        stageName: items[0].stageName,
        rows: [],
        results: map[stage] ?? [],
      };
      g.rows.push({ p, items });
      groups.set(stage, g);
    }
  }
  return {
    groups: [...groups.values()].sort((a, b) => a.stage.localeCompare(b.stage)),
    none,
  };
};

/** 批量「快速跟进」：按阶段分组，每组选一个结果或跳过，共用实际沟通日期 */
export const QuickBatchModal: React.FC<{
  open: boolean;
  picked: Picked[];
  onClose: () => void;
  onDone: () => void;
}> = ({ open, picked, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const map = useQuickResults();
  const [choice, setChoice] = useState<Record<string, string>>({});
  const [contactDate, setContactDate] = useState<Dayjs>(dayjs().startOf('day'));
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<{ r: BatchResult; skipped: number }>();
  const keyRef = useRef(newKey());
  const { groups, none } = groupsOf(picked, map);

  useEffect(() => {
    if (!open) return;
    keyRef.current = newKey();
    setChoice({});
    setContactDate(dayjs().startOf('day'));
  }, [open]);

  const chosen = (g: QuickGroup) => choice[g.stage] ?? g.results[0]?.code;
  const entries = groups.flatMap((g) => {
    const code = chosen(g);
    return !code || code === SKIP
      ? []
      : g.rows.map((x) => ({
          customerId: x.p.customerId,
          itemIds: x.items.map((i) => i.id),
          result: code,
        }));
  });
  const customers = new Set(entries.map((e) => e.customerId)).size;
  const today = dayjs().startOf('day');

  const submit = async () => {
    setSaving(true);
    try {
      const r = await followupApi.quickBatch({
        entries,
        contactDate: iso(contactDate),
        idempotencyKey: keyRef.current,
      });
      setResult({ r, skipped: picked.length - customers });
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const chip = (active: boolean): React.CSSProperties => ({
    borderColor: active ? palette.link : palette.hairline,
    background: active ? palette.accentSoft : 'transparent',
    color: active ? palette.link : palette.sub,
  });

  return (
    <>
      <Modal
        title={`快速跟进 · 已选 ${picked.length} 个客户`}
        open={open && !result}
        width={620}
        onCancel={onClose}
        destroyOnHidden
        footer={
          <Space>
            <Button onClick={onClose}>取消</Button>
            <Button
              type="primary"
              loading={saving}
              disabled={!entries.length}
              onClick={submit}
            >
              记 {customers} 个客户
            </Button>
          </Space>
        }
      >
        <div style={{ display: 'grid', gap: 10 }}>
          {groups.map((g) => {
            const names = [...new Set(g.rows.map((x) => x.p.customerName))];
            return (
              <div
                key={g.stage}
                style={{
                  padding: '12px 14px',
                  borderRadius: 10,
                  background: palette.inset,
                  display: 'grid',
                  gap: 10,
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    gap: 8,
                    alignItems: 'center',
                    fontSize: 12,
                  }}
                >
                  <span
                    style={{
                      padding: '1px 8px',
                      borderRadius: 6,
                      color: palette.link,
                      background: palette.accentSoft,
                    }}
                  >
                    {g.stage} {g.stageName}
                  </span>
                  <b style={{ color: palette.ink, fontSize: 13 }}>
                    {g.rows.length} 个客户
                  </b>
                  <span
                    style={{
                      color: palette.mute,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {names.slice(0, 2).join('、')}
                    {names.length > 2 ? ' 等' : ''}
                  </span>
                </div>
                <Space wrap size={6}>
                  {[
                    ...g.results,
                    { code: SKIP, label: '跳过' } as QuickResult,
                  ].map((r) => (
                    <Button
                      key={r.code}
                      size="small"
                      style={chip(chosen(g) === r.code)}
                      onClick={() =>
                        setChoice((c) => ({ ...c, [g.stage]: r.code }))
                      }
                    >
                      {r.label}
                      {r.advanceTo && (
                        <>
                          {' '}
                          <ArrowRightOutlined /> {r.advanceTo}
                        </>
                      )}
                    </Button>
                  ))}
                </Space>
              </div>
            );
          })}
          {none.length > 0 && (
            <div style={{ fontSize: 12, color: palette.orange }}>
              <ExclamationCircleOutlined />{' '}
              {none
                .slice(0, 3)
                .map((p) => p.customerName)
                .join('、')}
              {none.length > 3 ? ` 等 ${none.length} 个客户` : ''}
              所在阶段不能快速跟进，会跳过
            </div>
          )}
        </div>
        <div
          style={{
            display: 'flex',
            alignItems: 'flex-end',
            gap: 12,
            marginTop: 16,
          }}
        >
          <div>
            <div
              style={{
                fontSize: 13,
                fontWeight: 600,
                color: palette.ink,
                marginBottom: 6,
              }}
            >
              实际沟通日期
            </div>
            <DatePicker
              allowClear={false}
              value={contactDate}
              format={(d) => fmtDate(d)}
              onChange={(d) => d && setContactDate(d)}
              disabledDate={(d) =>
                d.isAfter(today) ||
                d.isBefore(today.subtract(CONTACT_BACK_DAYS, 'day'))
              }
            />
          </div>
          <span style={{ fontSize: 12, color: palette.mute, paddingBottom: 6 }}>
            下一步和日期按各阶段 SOP 自动填
          </span>
        </div>
      </Modal>
      <Modal
        open={!!result}
        width={460}
        closable={false}
        onCancel={() => {
          setResult(undefined);
          onClose();
        }}
        title={
          <span>
            <CheckCircleOutlined
              style={{ color: palette.green, marginRight: 8 }}
            />
            已记 {result?.r.success ?? 0} 个客户
          </span>
        }
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
      >
        {result && (
          <div style={{ color: palette.sub }}>
            {result.r.items} 张单据已更新下一步
            {result.skipped > 0 ? `；跳过 ${result.skipped} 个` : ''}
          </div>
        )}
        {result && result.r.failures.length > 0 && (
          <div
            style={{
              marginTop: 12,
              padding: '10px 12px',
              borderRadius: 10,
              background: palette.inset,
              display: 'grid',
              gap: 6,
              fontSize: 13,
            }}
          >
            <div style={{ color: palette.orange, fontSize: 12 }}>
              <ExclamationCircleOutlined /> 没记上 {result.r.failures.length} 个
            </div>
            {result.r.failures.map((f) => (
              <div key={f.customerId} style={{ color: palette.ink }}>
                {f.customerName} · {f.reason}
              </div>
            ))}
          </div>
        )}
      </Modal>
    </>
  );
};

// ---------------------------------------------------------------- 调整阶段

/** 单据页：「还差什么」的入口 */
const docPath = (i: FollowupItem) => {
  switch (i.targetType) {
    case TARGET.OPPORTUNITY:
      return `/crm/opportunities/${i.targetId}`;
    case TARGET.INQUIRY:
      return `/inquiry/customer-inquiries/${i.targetId}`;
    case TARGET.QUOTATION:
      return `/quotation/quotations/${i.targetId}`;
    case TARGET.PI:
      return `/sales/pi/${i.targetId}`;
    case TARGET.ORDER:
      return `/sales/orders/${i.targetId}`;
    default:
      return undefined;
  }
};

/** 不能手动调整的阶段：到下一阶段还差什么、入口文字 */
const STAGE_GAPS: Record<string, [string, string]> = {
  S3: ['生成报价单后进入 S4', '去报价'],
  S6: ['发送 PI 后进入 S7', '去开 PI'],
  S7: ['PI 全额到账后成交', '看 PI'],
  F1: ['系统按采购、到仓、出运自动推进', '看订单'],
  F2: ['型号全部下单后进入 F3', '看订单'],
  F3: ['系统按采购、到仓、出运自动推进', '看订单'],
  F4: ['系统按采购、到仓、出运自动推进', '看订单'],
  F5: ['系统按采购、到仓、出运自动推进', '看订单'],
  F6: ['客户签收后进入售后评价', '看订单'],
  A3: ['售后单改为待客户确认后进入 A4', '看售后单'],
  A4: ['客户确认解决后进入 A5', '看售后单'],
};

/** 阶段标签旁：能调整的显示「调整」，不能调整的显示到下一阶段还差什么 */
export const StageAdjust: React.FC<{
  item: FollowupItem;
  canRecord: boolean;
  onDone: () => void;
}> = ({ item, canRecord, onDone }) => {
  const { palette } = useAppTheme();
  const [open, setOpen] = useState(false);
  const segment = adjustSegment(item.stageCode);
  if (segment) {
    if (!canRecord) return null;
    return (
      <>
        <button
          type="button"
          onClick={() => setOpen(true)}
          style={{
            all: 'unset',
            cursor: 'pointer',
            fontSize: 12,
            color: palette.mute,
          }}
        >
          <SwapOutlined /> 调整
        </button>
        <AdjustStageModal
          item={open ? item : undefined}
          segment={segment}
          onClose={() => setOpen(false)}
          onDone={() => {
            setOpen(false);
            onDone();
          }}
        />
      </>
    );
  }
  const gap = STAGE_GAPS[item.stageCode];
  if (!gap) return null;
  const path =
    item.stageCode.startsWith('A') && item.afterSalesId
      ? `/sales/after-sales/${item.afterSalesId}`
      : docPath(item);
  return (
    <span style={{ fontSize: 12, color: palette.mute }}>
      到下一阶段：{gap[0]}
      {path && (
        <button
          type="button"
          onClick={() => history.push(path)}
          style={{
            all: 'unset',
            cursor: 'pointer',
            color: palette.link,
            marginLeft: 6,
          }}
        >
          {gap[1]}
        </button>
      )}
    </span>
  );
};

export const AdjustStageModal: React.FC<{
  item?: FollowupItem;
  segment: string[];
  onClose: () => void;
  onDone: () => void;
}> = ({ item, segment, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [to, setTo] = useState<string>();
  const [reason, setReason] = useState<string>();
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);
  /** 段内各阶段的名称（按租户的 SOP 配置） */
  const [names, setNames] = useState<Record<string, string>>({});
  useEffect(() => {
    setTo(undefined);
    setReason(undefined);
    setNote('');
    if (!item) return;
    let alive = true;
    Promise.all(
      segment.map((s) =>
        followupApi
          .sop(item.board, s)
          .then((c) => [s, c.name] as const)
          .catch(() => [s, ''] as const),
      ),
    ).then((l) => alive && setNames(Object.fromEntries(l)));
    return () => {
      alive = false;
    };
  }, [item, segment]);
  const from = item?.stageCode;
  const nameOf = (s: string) =>
    s === from ? (item?.stageName ?? '') : (names[s] ?? '');
  const submit = async () => {
    if (!item) return;
    if (!to) {
      message.warning('请选择要调整到的阶段');
      return;
    }
    if (!reason) {
      message.warning('请选择调整原因');
      return;
    }
    if (reason === 'OTHER' && !note.trim()) {
      message.warning('请写明调整原因');
      return;
    }
    setSaving(true);
    try {
      await followupApi.adjustStage(item.id, {
        toStage: to,
        reason,
        note: note.trim() || undefined,
      });
      message.success(`已调整到 ${to}`);
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
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
  return (
    <Modal
      title={`调整阶段 · ${item?.docNo ?? item?.customerName ?? ''}`}
      open={!!item}
      width={480}
      onCancel={onClose}
      destroyOnHidden
      footer={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" loading={saving} onClick={submit}>
            调整
          </Button>
        </Space>
      }
    >
      {label('调整到', true)}
      <div style={{ display: 'grid', gap: 8 }}>
        {segment.map((s) => {
          const current = s === from;
          const active = s === to;
          return (
            <button
              type="button"
              key={s}
              disabled={current}
              onClick={() => setTo(s)}
              style={{
                all: 'unset',
                cursor: current ? 'default' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '10px 14px',
                borderRadius: 10,
                border: `1px solid ${active ? palette.link : palette.hairline}`,
                background: active ? palette.accentSoft : 'transparent',
                color: palette.ink,
              }}
            >
              <b style={{ color: active ? palette.link : palette.sub }}>{s}</b>
              <span style={{ flex: 1 }}>{nameOf(s)}</span>
              {current && (
                <span style={{ fontSize: 12, color: palette.mute }}>当前</span>
              )}
              {active && <CheckOutlined style={{ color: palette.link }} />}
            </button>
          );
        })}
      </div>
      {label('原因', true)}
      <Space wrap size={6}>
        {ADJUST_REASONS.map((r) => (
          <Button
            key={r.value}
            size="small"
            onClick={() => setReason(r.value)}
            style={{
              borderColor: reason === r.value ? palette.link : palette.hairline,
              background:
                reason === r.value ? palette.accentSoft : 'transparent',
              color: reason === r.value ? palette.link : palette.sub,
            }}
          >
            {r.label}
          </Button>
        ))}
      </Space>
      {label('补充说明', reason === 'OTHER')}
      <Input
        value={note}
        maxLength={NOTE_MAX}
        placeholder={reason === 'OTHER' ? '写明为什么调整' : '选填'}
        onChange={(e) => setNote(e.target.value)}
      />
      {to && from && (
        <div
          style={{
            marginTop: 14,
            padding: '8px 12px',
            borderRadius: 10,
            fontSize: 12,
            color: palette.sub,
            background: palette.inset,
          }}
        >
          <HistoryOutlined /> 会在客户时间线留一条「调整阶段：{from} → {to}
          」，下一步不变
        </div>
      )}
    </Modal>
  );
};
