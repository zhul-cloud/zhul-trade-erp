import {
  CheckOutlined,
  ExclamationCircleOutlined,
  HistoryOutlined,
  RollbackOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import {
  App,
  Button,
  Checkbox,
  Drawer,
  Input,
  Modal,
  Radio,
  Skeleton,
} from 'antd';
import dayjs from 'dayjs';
import React, { useEffect, useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import {
  ConditionPill,
  CustomerBrief,
  DeadlineText,
  formatCny,
  LeadTimeText,
  LevelPill,
  Pill,
  SupplierText,
  TaxHint,
  useWide,
} from '../shared/components';
import { formatMinutes } from '../shared/constants';
import {
  boardApi,
  type ReviewDetail,
  type ReviewItem,
  readBizError,
} from '../shared/service';

const QUICK_REASONS = [
  '货况不对，请找全新原装',
  '价格偏离太多，请核实',
  '店铺不可靠，换个渠道',
];

interface Decision {
  checked: boolean;
  recommended?: number;
  /** 作废的记录 → 作废原因（可为空） */
  voids: Record<number, string>;
}

const keyOf = (it: ReviewItem) => `${it.itemId}|${it.quotedBy}`;

/** 默认：全部勾选；只有一条有价记录时默认选它为推荐 */
const initial = (items: ReviewItem[]) =>
  Object.fromEntries(
    items.map((it) => {
      const priced = it.quotes.filter((q) => !q.noStock);
      return [
        keyOf(it),
        {
          checked: true,
          recommended: priced.length === 1 ? priced[0].id : undefined,
          voids: {},
        } as Decision,
      ];
    }),
  );

/**
 * 审核兼职回价：逐型号选一条推荐、把不可靠的记录作废，然后通过；有问题时填原因退回兼职修改。
 * 审核人不能改价格、货况、货期。
 */
const ReviewDrawer: React.FC<{
  taskId?: number;
  onClose: () => void;
  onChanged: () => void;
}> = ({ taskId, onClose, onChanged }) => {
  const { palette } = useAppTheme();
  const wide = useWide();
  const { message } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const canReview = !!access['inquiry:quote:review'];
  const [data, setData] = useState<ReviewDetail | null>(null);
  const [error, setError] = useState('');
  const [state, setState] = useState<Record<string, Decision>>({});
  const [missing, setMissing] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState('');

  const load = async (id: number) => {
    setError('');
    try {
      const d = await boardApi.reviewDetail(id);
      setData(d);
      setState(initial(d.items));
      setMissing([]);
      return d;
    } catch (e) {
      setError(readBizError(e).message);
      return null;
    }
  };

  useEffect(() => {
    setData(null);
    if (taskId) load(taskId);
  }, [taskId]);

  const patch = (key: string, p: Partial<Decision>) => {
    setState((prev) => ({ ...prev, [key]: { ...prev[key], ...p } }));
    setMissing((prev) => prev.filter((k) => k !== key));
  };

  const items = data?.items ?? [];
  const chosen = items.filter((it) => state[keyOf(it)]?.checked);

  const afterChange = async (ok: string) => {
    message.success(ok);
    onChanged();
    if (!taskId) return;
    const d = await load(taskId);
    if (d && d.items.length === 0) onClose();
  };

  const approve = async () => {
    if (!taskId || chosen.length === 0) return;
    const lacking = chosen.filter((it) => {
      const s = state[keyOf(it)];
      const kept = it.quotes.filter((q) => !(q.id in s.voids));
      return kept.some((q) => !q.noStock) && !s.recommended;
    });
    if (lacking.length > 0) {
      setMissing(lacking.map(keyOf));
      message.error(`请为 ${lacking[0].model} 选一条推荐报价`);
      return;
    }
    setBusy(true);
    try {
      await boardApi.approveReview(
        taskId,
        chosen.map((it) => {
          const s = state[keyOf(it)];
          return {
            itemId: it.itemId,
            quotedBy: it.quotedBy,
            recommendedQuoteId: s.recommended ?? null,
            voids: Object.entries(s.voids).map(([id, r]) => ({
              quoteId: Number(id),
              reason: r,
            })),
          };
        }),
      );
      await afterChange(`已通过 ${chosen.length} 个型号，业务员现在能看到了`);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const reject = async () => {
    if (!taskId) return;
    if (!reason.trim()) {
      setReasonError('请填写退回原因');
      return;
    }
    setBusy(true);
    try {
      await boardApi.rejectReview(
        taskId,
        chosen.map((it) => ({ itemId: it.itemId, quotedBy: it.quotedBy })),
        reason.trim(),
      );
      setRejectOpen(false);
      setReason('');
      await afterChange(`已退回 ${chosen.length} 个型号`);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const t = data?.task;
  const buyers = [...new Set(items.map((it) => it.quotedByName ?? ''))]
    .filter(Boolean)
    .join('、');
  const meta = (label: string, value: React.ReactNode) => (
    <div style={{ display: 'grid', gap: 2 }}>
      <span style={{ color: palette.mute, fontSize: 12 }}>{label}</span>
      <span style={{ color: palette.ink }}>{value}</span>
    </div>
  );

  return (
    <Drawer
      open={!!taskId}
      onClose={onClose}
      size={wide ? 820 : '100%'}
      destroyOnHidden
      title={
        t
          ? `审核兼职回价 · ${t.taskCode} · ${t.brand}${t.category ? ` · ${t.category}` : ''}`
          : '审核兼职回价'
      }
    >
      {error ? (
        <ErrorHint message={error} onRetry={() => taskId && load(taskId)} />
      ) : !t || !data ? (
        <Skeleton active paragraph={{ rows: 10 }} />
      ) : (
        <div style={{ display: 'grid', gap: 18 }}>
          <div
            style={{
              display: 'flex',
              gap: 8,
              flexWrap: 'wrap',
              alignItems: 'center',
            }}
          >
            <Pill tone="accent" dot>
              待审核 {items.length}
            </Pill>
            <LevelPill value={t.level} />
            {t.urgent && <Pill tone="red">紧急</Pill>}
            <CustomerBrief
              customerType={t.customerType}
              customerName={t.customerName}
            />
          </div>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(4, minmax(0, 1fr))',
              gap: 16,
              padding: 16,
              borderRadius: 12,
              background: palette.hover,
            }}
          >
            {meta('询价采购', buyers ? `${buyers}（兼职）` : '—')}
            {meta(
              '报价截止',
              t.quoteDeadline ? (
                <DeadlineText date={t.quoteDeadline} status={5} />
              ) : (
                '—'
              ),
            )}
            {meta('业务员可见的回价', `${t.pricedCount} / ${t.itemCount}`)}
            {meta(
              '最早一批提交',
              t.reviewSubmittedAt
                ? `${dayjs(t.reviewSubmittedAt).format('MM-DD HH:mm')} · 已等 ${formatMinutes(t.reviewWaitingMinutes)}`
                : '—',
            )}
          </div>

          {items.length === 0 ? (
            <div style={{ color: palette.mute }}>
              这个任务没有待审核的回价了。
            </div>
          ) : (
            canReview && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  flexWrap: 'wrap',
                  padding: '12px 16px',
                  borderRadius: 12,
                  background: palette.accentSoft,
                  border: `1px solid ${palette.accentLine}`,
                }}
              >
                <Checkbox
                  checked={chosen.length === items.length}
                  indeterminate={
                    chosen.length > 0 && chosen.length < items.length
                  }
                  onChange={(e) =>
                    setState((prev) =>
                      Object.fromEntries(
                        Object.entries(prev).map(([k, v]) => [
                          k,
                          { ...v, checked: e.target.checked },
                        ]),
                      ),
                    )
                  }
                />
                <span style={{ color: palette.ink, fontWeight: 600 }}>
                  已选 {chosen.length} 个型号
                </span>
                <span style={{ color: palette.mute, fontSize: 12 }}>
                  有价型号须各选一条推荐；作废的记录不会给业务员看
                </span>
                <span style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
                  <Button
                    danger
                    icon={<RollbackOutlined />}
                    disabled={chosen.length === 0 || busy}
                    onClick={() => {
                      setReasonError('');
                      setRejectOpen(true);
                    }}
                  >
                    退回所选
                  </Button>
                  <Button
                    type="primary"
                    icon={<CheckOutlined />}
                    disabled={chosen.length === 0}
                    loading={busy}
                    onClick={approve}
                  >
                    通过所选（{chosen.length}）
                  </Button>
                </span>
              </div>
            )
          )}
          {!canReview && items.length > 0 && (
            <div style={{ color: palette.mute, fontSize: 12 }}>
              你可以查看待审核的回价；审核需要「审核兼职回价」权限，请联系管理员在角色管理中勾选。
            </div>
          )}

          {items.map((it) => {
            const key = keyOf(it);
            const s = state[key] ?? { checked: false, voids: {} };
            const err = missing.includes(key);
            const noStockOnly = it.quotes.every((q) => q.noStock);
            return (
              <div
                key={key}
                style={{
                  display: 'grid',
                  gap: 8,
                  padding: '14px 16px',
                  borderRadius: 12,
                  border: `1px solid ${err ? palette.red : s.checked && canReview ? palette.accentLine : palette.hairline}`,
                  transition: 'border-color 150ms ease-out',
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    flexWrap: 'wrap',
                  }}
                >
                  {canReview && (
                    <Checkbox
                      checked={s.checked}
                      onChange={(e) =>
                        patch(key, { checked: e.target.checked })
                      }
                    />
                  )}
                  <b style={{ color: palette.ink, fontSize: 14 }}>{it.model}</b>
                  <span style={{ color: palette.sub }}>
                    {it.quantity} {it.unit}
                  </span>
                  <span style={{ marginLeft: 'auto' }}>
                    <Pill tone="accent" dot>
                      待审核
                    </Pill>
                  </span>
                </div>
                <span style={{ color: palette.mute, fontSize: 12 }}>
                  {it.description ? `${it.description} · ` : ''}
                  {it.quotedByName}
                  {it.submittedAt
                    ? ` ${dayjs(it.submittedAt).format('MM-DD HH:mm')} 提交`
                    : ''}
                </span>
                <span
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 6,
                    color: palette.mute,
                    fontSize: 12,
                  }}
                >
                  <HistoryOutlined />
                  {it.historyLowest
                    ? `历史询价最低：${formatCny(it.historyLowest.unitPriceCny)}${it.historyLowest.quotedAt ? ` · ${dayjs(it.historyLowest.quotedAt).format('MM-DD')}` : ''}${it.historyLowest.quotedByName ? `（${it.historyLowest.quotedByName}）` : ''}`
                    : '历史询价：没有这个型号的记录'}
                </span>
                {noStockOnly && (
                  <span style={{ color: palette.mute, fontSize: 12 }}>
                    只有无货结果，不需要选推荐；通过后业务员看到「无货」
                  </span>
                )}
                {it.quotes.map((q) => {
                  const voided = q.id in s.voids;
                  const rec = s.recommended === q.id;
                  if (q.noStock) {
                    return (
                      <div
                        key={q.id}
                        style={{
                          padding: '8px 12px',
                          borderRadius: 10,
                          background: palette.hover,
                          color: palette.sub,
                          fontSize: 13,
                        }}
                      >
                        无货{q.note ? `：${q.note}` : ''}
                      </div>
                    );
                  }
                  return (
                    <div key={q.id} style={{ display: 'grid', gap: 6 }}>
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 10,
                          flexWrap: 'wrap',
                          padding: '8px 12px',
                          borderRadius: 10,
                          fontSize: 13,
                          background: rec
                            ? palette.accentSoft
                            : voided
                              ? 'transparent'
                              : palette.hover,
                          border: voided
                            ? `1px dashed ${palette.hairline}`
                            : '1px solid transparent',
                          opacity: voided ? 0.6 : 1,
                          cursor: canReview && !voided ? 'pointer' : 'default',
                          transition: 'background 150ms ease-out',
                        }}
                        onClick={() =>
                          canReview &&
                          !voided &&
                          patch(key, { recommended: q.id })
                        }
                      >
                        {canReview &&
                          (voided ? (
                            <StopOutlined style={{ color: palette.mute }} />
                          ) : (
                            <Radio checked={rec} aria-label="选为推荐报价" />
                          ))}
                        <b
                          style={{
                            color: palette.ink,
                            minWidth: 80,
                            textDecoration: voided ? 'line-through' : 'none',
                          }}
                        >
                          {formatCny(q.unitPriceCny)}
                        </b>
                        <TaxHint
                          taxIncluded={q.taxIncluded}
                          taxRate={q.taxRate}
                          unitPrice={q.unitPrice}
                        />
                        <ConditionPill value={q.itemCondition} />
                        <span style={{ color: palette.sub }}>
                          <LeadTimeText value={q.leadTime} />
                        </span>
                        <SupplierText
                          channel={q.channel}
                          shopName={q.shopName}
                        />
                        {q.note && (
                          <span style={{ color: palette.mute, fontSize: 12 }}>
                            {q.note}
                          </span>
                        )}
                        <span
                          style={{
                            marginLeft: 'auto',
                            display: 'inline-flex',
                            gap: 10,
                            alignItems: 'center',
                          }}
                        >
                          {rec && <Pill tone="green">推荐</Pill>}
                          {voided && <Pill tone="red">已作废</Pill>}
                          {canReview && (
                            <a
                              style={{
                                fontSize: 12,
                                color: voided ? palette.link : palette.mute,
                              }}
                              onClick={(e) => {
                                e.stopPropagation();
                                const next = { ...s.voids };
                                if (voided) delete next[q.id];
                                else next[q.id] = '';
                                patch(key, {
                                  voids: next,
                                  recommended:
                                    !voided && rec ? undefined : s.recommended,
                                });
                              }}
                            >
                              {voided ? '恢复' : '作废'}
                            </a>
                          )}
                        </span>
                      </div>
                      {voided && (
                        <Input
                          size="small"
                          maxLength={200}
                          placeholder="作废原因（选填），如：二手且无质保"
                          value={s.voids[q.id]}
                          onChange={(e) =>
                            patch(key, {
                              voids: { ...s.voids, [q.id]: e.target.value },
                            })
                          }
                          style={{ marginLeft: 24, width: 'calc(100% - 24px)' }}
                        />
                      )}
                    </div>
                  );
                })}
                {err && (
                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 6,
                      color: palette.red,
                      fontSize: 12,
                    }}
                  >
                    <ExclamationCircleOutlined />
                    请为 {it.model} 选一条推荐报价
                  </span>
                )}
              </div>
            );
          })}

          {data.unsubmittedCount > 0 && (
            <div
              style={{
                padding: '10px 16px',
                borderRadius: 10,
                background: palette.hover,
                color: palette.mute,
                fontSize: 12,
              }}
            >
              还有 {data.unsubmittedCount} 个型号在询价（未提交，不在审核范围）
            </div>
          )}
          <span style={{ color: palette.mute, fontSize: 12, lineHeight: 1.6 }}>
            审核人只能选推荐、作废记录或退回，不能改价格、货况、货期；审核通过的型号立即对业务员可见，审核与退回都会记入「修改记录」。
          </span>
        </div>
      )}

      <Modal
        open={rejectOpen}
        title={`退回给${buyers || '兼职采购'}修改`}
        okText="确认退回"
        cancelText="取消"
        okButtonProps={{ danger: true, loading: busy }}
        onOk={reject}
        onCancel={() => setRejectOpen(false)}
        destroyOnHidden
      >
        <div style={{ display: 'grid', gap: 12 }}>
          <div
            style={{
              padding: '10px 12px',
              borderRadius: 10,
              background: palette.hover,
              color: palette.sub,
              fontSize: 13,
            }}
          >
            退回 {chosen.length} 个型号：
            {chosen.map((it) => it.model).join('、')}
          </div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {QUICK_REASONS.map((r) => (
              <Button
                key={r}
                size="small"
                onClick={() => {
                  setReason(r);
                  setReasonError('');
                }}
              >
                {r}
              </Button>
            ))}
          </div>
          <div style={{ display: 'grid', gap: 4 }}>
            <Input.TextArea
              autoFocus
              rows={3}
              maxLength={200}
              showCount
              status={reasonError ? 'error' : undefined}
              placeholder="写清楚哪里不对、要怎么改，兼职会在任务里看到"
              value={reason}
              onChange={(e) => {
                setReason(e.target.value);
                if (e.target.value.trim()) setReasonError('');
              }}
            />
            {reasonError && (
              <span style={{ color: palette.red, fontSize: 12 }}>
                {reasonError}
              </span>
            )}
          </div>
          <span style={{ color: palette.mute, fontSize: 12 }}>
            退回后这些记录回到兼职的草稿，修改后再提交；上次已通过的价格继续对业务员有效。
          </span>
        </div>
      </Modal>
    </Drawer>
  );
};

export default ReviewDrawer;
