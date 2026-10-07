import { UserOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import { App, Drawer, Radio, Skeleton } from 'antd';
import React, { useEffect, useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import {
  ConditionPill,
  CustomerBrief,
  DeadlineText,
  LeadTimeText,
  LevelPill,
  Pill,
  Progress,
  SupplierText,
  TaskStatusPill,
  TaxHint,
  useLifecycles,
  useWide,
} from '../shared/components';
import { LIFECYCLE_DISCONTINUED } from '../shared/constants';
import {
  type BoardTaskDetail,
  boardApi,
  readBizError,
} from '../shared/service';
import ItemHistoryModal from './ItemHistoryModal';

const QUOTE_STATUS: Record<
  number,
  { label: string; tone: 'orange' | 'green' | 'gray' }
> = {
  1: { label: '待询价', tone: 'orange' },
  2: { label: '已回价', tone: 'green' },
  3: { label: '无货', tone: 'gray' },
};

/**
 * 分配工作台的任务详情：分配前看清型号、询盘等级和客户概况。
 * 不跳转客户询盘（采购负责人看不到业务员的询盘），也不含联系方式与原始询盘内容。
 */
const TaskDetailDrawer: React.FC<{
  taskId?: number;
  onClose: () => void;
}> = ({ taskId, onClose }) => {
  const { palette } = useAppTheme();
  const wide = useWide();
  const { lifecycleLabel } = useLifecycles();
  const [data, setData] = useState<BoardTaskDetail | null>(null);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [historyItem, setHistoryItem] = useState<{
    id: number;
    model: string;
  }>();
  const { message } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const canPick = !!access['inquiry:task:assign'];

  const load = async (id: number) => {
    setData(null);
    setError('');
    try {
      setData(await boardApi.taskDetail(id));
    } catch (e) {
      setError(readBizError(e).message);
    }
  };

  useEffect(() => {
    if (taskId) load(taskId);
  }, [taskId]);

  const pickCost = async (itemId: number, quoteId: number | null) => {
    if (!taskId) return;
    setSaving(true);
    try {
      await boardApi.setCostQuote(itemId, quoteId);
      message.success(quoteId ? '已指定采购成本价' : '已恢复按推荐报价自动取');
      await load(taskId);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const t = data?.task;
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
      size={wide ? 640 : '100%'}
      destroyOnHidden
      title={
        t
          ? `${t.taskCode} · ${t.brand}${t.category ? ` · ${t.category}` : ''}`
          : '任务详情'
      }
    >
      {error ? (
        <ErrorHint message={error} onRetry={() => taskId && load(taskId)} />
      ) : !t || !data ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : (
        <div style={{ display: 'grid', gap: 20 }}>
          <div
            style={{
              display: 'flex',
              gap: 8,
              flexWrap: 'wrap',
              alignItems: 'center',
            }}
          >
            <TaskStatusPill status={t.status} timeout={t.timeout} />
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
              gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
              gap: 16,
              padding: 16,
              borderRadius: 12,
              background: palette.hover,
            }}
          >
            {meta(
              '所属业务员',
              <span
                style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}
              >
                <UserOutlined style={{ color: palette.mute }} />
                {t.salesName || '—'}
              </span>,
            )}
            {meta(
              '报价截止',
              t.quoteDeadline ? (
                <DeadlineText date={t.quoteDeadline} status={5} />
              ) : (
                '—'
              ),
            )}
            {meta(
              '回价进度',
              <Progress done={t.pricedCount} total={t.itemCount} width={70} />,
            )}
            {meta(
              '询价采购',
              t.assignees.length
                ? t.assignees
                    .map((a) => `${a.name}${a.partTime ? '（兼职）' : ''}`)
                    .join('、')
                : '待分配',
            )}
            {t.returnReason > 0 &&
              meta(
                '最近退回',
                `${t.returnReasonLabel ?? ''}${t.returnedByName ? ` · ${t.returnedByName}` : ''}${t.returnNote ? ` · ${t.returnNote}` : ''}`,
              )}
          </div>
          <div style={{ display: 'grid', gap: 10 }}>
            <b style={{ color: palette.ink }}>型号（{data.items.length}）</b>
            {data.items.map((it) => {
              const qs = QUOTE_STATUS[it.quoteStatus];
              return (
                <div
                  key={it.id}
                  style={{
                    display: 'grid',
                    gap: 6,
                    padding: '12px 14px',
                    borderRadius: 12,
                    border: `1px solid ${palette.hairline}`,
                  }}
                >
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      flexWrap: 'wrap',
                    }}
                  >
                    <b style={{ color: palette.ink }}>{it.model}</b>
                    <span style={{ color: palette.sub }}>
                      {it.quantity} {it.unit}
                    </span>
                    {it.lifecycle === LIFECYCLE_DISCONTINUED && (
                      <Pill tone="red">
                        {lifecycleLabel(it.lifecycle)}
                        {it.replacementModel ? ` → ${it.replacementModel}` : ''}
                      </Pill>
                    )}
                    {qs && (
                      <span style={{ marginLeft: 'auto' }}>
                        <Pill tone={qs.tone}>{qs.label}</Pill>
                      </span>
                    )}
                  </div>
                  {it.originalModel && it.originalModel !== it.model && (
                    <span style={{ color: palette.mute, fontSize: 12 }}>
                      客户原文：{it.originalModel}
                    </span>
                  )}
                  {it.description && (
                    <span
                      style={{
                        color: palette.sub,
                        fontSize: 12,
                        lineHeight: 1.6,
                      }}
                    >
                      {it.description}
                    </span>
                  )}
                  {it.quotes.some((q) => !q.noStock) && (
                    <div style={{ display: 'grid', gap: 4, marginTop: 4 }}>
                      <span
                        style={{
                          display: 'flex',
                          color: palette.mute,
                          fontSize: 12,
                        }}
                      >
                        采购成本价
                        {it.costManual
                          ? '（采购负责人指定）'
                          : '（按推荐报价自动取）'}
                        {it.changeCount > 0 && (
                          <a
                            style={{ marginLeft: 12, fontSize: 12 }}
                            onClick={() =>
                              setHistoryItem({ id: it.id, model: it.model })
                            }
                          >
                            修改记录（{it.changeCount}）
                          </a>
                        )}
                        {canPick && it.costManual && (
                          <a
                            style={{ marginLeft: 'auto', fontSize: 12 }}
                            onClick={() => !saving && pickCost(it.id, null)}
                          >
                            恢复自动
                          </a>
                        )}
                      </span>
                      {it.quotes
                        .filter((q) => !q.noStock)
                        .map((q) => (
                          <div
                            key={q.id}
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: 10,
                              flexWrap: 'wrap',
                              padding: '6px 10px',
                              borderRadius: 10,
                              fontSize: 13,
                              background:
                                q.id === it.selectedQuoteId
                                  ? palette.accentSoft
                                  : palette.hover,
                            }}
                          >
                            {canPick && (
                              <Radio
                                checked={q.id === it.selectedQuoteId}
                                disabled={saving}
                                onChange={() => pickCost(it.id, q.id)}
                              />
                            )}
                            <b style={{ color: palette.ink, minWidth: 80 }}>
                              {formatAmount(q.unitPriceCny)}
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
                            <span style={{ color: palette.mute }}>
                              {q.quotedByName}
                            </span>
                            {q.recommended && <Pill tone="green">推荐</Pill>}
                            {q.id === it.selectedQuoteId && (
                              <Pill tone="accent">成本价</Pill>
                            )}
                          </div>
                        ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
          <span style={{ color: palette.mute, fontSize: 12 }}>
            客户联系方式与原始询盘内容不对采购开放；型号有疑问时，在「我的询价任务」中退回并选「型号存疑」，由业务员核实。
          </span>
        </div>
      )}
      <ItemHistoryModal
        item={historyItem}
        onClose={() => setHistoryItem(undefined)}
      />
    </Drawer>
  );
};

export default TaskDetailDrawer;
