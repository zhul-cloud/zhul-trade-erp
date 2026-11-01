import {
  HistoryOutlined,
  InfoCircleOutlined,
  ShoppingCartOutlined,
} from '@ant-design/icons';
import { App, Button, Input, InputNumber, Modal, Select } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { Card, Pill } from '../components';
import {
  type Order,
  orderApi,
  type PartialCancelBody,
  type PartialCancelPreview,
  readBizError,
} from '../service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

/** 型号行数量：剩余数量，部分取消过的下面写「原 N · 已取消 M」 */
export const QtyCell: React.FC<{
  quantity: number;
  original?: number;
  cancelled?: number;
}> = ({ quantity, original, cancelled }) => {
  const { palette } = useAppTheme();
  return (
    <div>
      <div
        style={{
          ...num,
          fontWeight: 600,
          color: quantity > 0 ? palette.ink : palette.mute,
        }}
      >
        {quantity}
      </div>
      {(cancelled ?? 0) > 0 && (
        <div
          style={{
            fontSize: 12,
            color: palette.orange,
            whiteSpace: 'nowrap',
            ...num,
          }}
        >
          原 {original} · 已取消 {cancelled}
        </div>
      )}
    </div>
  );
};

/** 部分取消记录（新的在前） */
export const PartialCancelRecords: React.FC<{ order: Order }> = ({ order }) => {
  const { palette } = useAppTheme();
  const rows = order.partialCancels ?? [];
  if (rows.length === 0) return null;
  const cur = order.currencyCode;
  const sum = rows.reduce((n, r) => n + Number(r.amount), 0);
  return (
    <Card style={{ padding: 20, marginTop: 16 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 12,
        }}
      >
        <HistoryOutlined style={{ color: palette.link }} />
        <b style={{ color: palette.ink }}>部分取消记录</b>
        <span style={{ marginLeft: 'auto', fontSize: 12, color: palette.mute }}>
          共 {rows.length} 次 · 少了 {formatAmount(sum, cur)}
        </span>
      </div>
      <div style={{ display: 'grid', gap: 8 }}>
        {rows.map((r) => (
          <div
            key={r.id}
            style={{
              padding: '10px 14px',
              borderRadius: 12,
              background: palette.inset,
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
              <span style={{ fontSize: 12, color: palette.mute }}>
                {formatDateTime(r.createTime).slice(5, 16)}
              </span>
              <b style={{ color: palette.ink }}>{r.model}</b>
              <span style={{ color: palette.sub }}>取消 {r.quantity} 个</span>
              <Pill tone={r.reason === 'OUT_OF_STOCK' ? 'orange' : 'gray'}>
                {r.reasonName}
              </Pill>
              <b style={{ ...num, marginLeft: 'auto', color: palette.orange }}>
                −{formatAmount(r.amount, cur)}
              </b>
              <span style={{ fontSize: 12, color: palette.mute }}>
                {r.operatorName ?? '—'}
              </span>
            </div>
            {r.remark && (
              <div style={{ fontSize: 12, color: palette.sub, marginTop: 4 }}>
                说明：{r.remark}
              </div>
            )}
            <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
              采购与库存：{r.result}
            </div>
          </div>
        ))}
      </div>
    </Card>
  );
};

/** 按型号部分取消：先选型号与数量、原因，再确认对合计、收款、采购与库存的影响 */
export const PartialCancelModal: React.FC<{
  order: Order;
  open: boolean;
  onClose: () => void;
  onDone: (order: Order) => void;
}> = ({ order, open, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [qty, setQty] = useState<Record<number, number | null>>({});
  const [reasons, setReasons] = useState<Record<string, string>>({});
  const [reason, setReason] = useState<string>();
  const [remark, setRemark] = useState('');
  const [preview, setPreview] = useState<PartialCancelPreview>();
  const [busy, setBusy] = useState(false);
  const cur = order.currencyCode;

  useEffect(() => {
    if (!open) return;
    setQty({});
    setRemark('');
    setPreview(undefined);
    orderApi
      .partialCancelReasons()
      .then((r) => {
        setReasons(r);
        setReason(Object.keys(r)[0]);
      })
      .catch((e) => message.error(readBizError(e).message));
  }, [open, message]);

  const live = order.items.filter((i) => i.quantity > 0);
  const gone = order.items.filter((i) => i.quantity === 0);
  const errors = useMemo(() => {
    const out: Record<number, string> = {};
    for (const i of order.items) {
      const n = qty[i.id] ?? 0;
      const max = i.cancellableQty ?? 0;
      if (n > max) {
        const out1 = i.outboundQty ?? 0;
        out[i.id] =
          `最多还能取消 ${max} 个${out1 > 0 ? `（已出库 ${out1} 个）` : ''}`;
      }
    }
    return out;
  }, [order.items, qty]);
  const lines = order.items
    .map((i) => ({ itemId: i.id, quantity: qty[i.id] ?? 0 }))
    .filter((l) => l.quantity > 0);
  const remaining = order.items.reduce(
    (n, i) => n + i.quantity - (qty[i.id] ?? 0),
    0,
  );
  const cut = order.items.reduce(
    (n, i) => n + (qty[i.id] ?? 0) * Number(i.unitPrice),
    0,
  );
  const blocker =
    Object.keys(errors).length > 0
      ? '有型号超过可取消数量，改正后才能下一步'
      : lines.length === 0
        ? '填写要取消的数量'
        : remaining <= 0
          ? '所有型号都要取消，请用「取消订单」'
          : !reason
            ? '请选择取消原因'
            : reason === 'OTHER' && !remark.trim()
              ? '原因选「其他」时请填写说明'
              : undefined;
  const body: PartialCancelBody = {
    items: lines,
    reason: reason ?? '',
    remark: remark.trim(),
  };

  const next = async () => {
    setBusy(true);
    try {
      setPreview(await orderApi.partialCancelPreview(order.id, body));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const submit = async () => {
    setBusy(true);
    try {
      onDone(await orderApi.partialCancel(order.id, body));
      message.success('已部分取消');
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const head: React.CSSProperties = {
    fontSize: 12,
    fontWeight: 600,
    color: palette.mute,
  };
  const grid = '1fr 70px 110px 80px 140px 100px';

  const editStep = (
    <>
      <div
        style={{
          display: 'flex',
          gap: 10,
          padding: '10px 14px',
          borderRadius: 12,
          background: palette.accentSoft,
          marginBottom: 14,
          fontSize: 13,
        }}
      >
        <InfoCircleOutlined style={{ color: palette.link, marginTop: 3 }} />
        <span style={{ color: palette.sub }}>
          可取消 = 当前数量 −
          已出库（出库中、已交货代、已出运）；已出运的货请走售后。
        </span>
      </div>
      <div
        style={{
          border: `1px solid ${palette.hairline}`,
          borderRadius: 12,
          overflow: 'hidden',
          marginBottom: 14,
        }}
      >
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: grid,
            gap: 12,
            padding: '10px 14px',
            background: palette.inset,
          }}
        >
          <span style={head}>型号</span>
          <span style={{ ...head, textAlign: 'right' }}>当前数量</span>
          <span style={{ ...head, textAlign: 'right' }}>已出库</span>
          <span style={{ ...head, textAlign: 'right' }}>最多可取消</span>
          <span style={head}>取消数量</span>
          <span style={{ ...head, textAlign: 'right' }}>少收金额</span>
        </div>
        {live.map((i) => {
          const n = qty[i.id] ?? 0;
          const err = errors[i.id];
          return (
            <div
              key={i.id}
              style={{
                display: 'grid',
                gridTemplateColumns: grid,
                gap: 12,
                padding: '10px 14px',
                alignItems: 'center',
                borderTop: `1px solid ${palette.hairline}`,
              }}
            >
              <div style={{ minWidth: 0 }}>
                <div style={{ fontWeight: 600, color: palette.ink }}>
                  {i.model}
                </div>
                <div style={{ fontSize: 12, color: palette.mute }}>
                  {formatAmount(i.unitPrice, cur)} / 个
                </div>
              </div>
              <span style={{ ...num, textAlign: 'right' }}>{i.quantity}</span>
              <span style={{ ...num, textAlign: 'right' }}>
                {i.outboundQty ?? 0}
              </span>
              <span style={{ ...num, textAlign: 'right' }}>
                {i.cancellableQty ?? 0}
              </span>
              <div>
                <InputNumber
                  min={0}
                  precision={0}
                  style={{ width: '100%' }}
                  value={qty[i.id] ?? null}
                  placeholder="0"
                  status={err ? 'error' : undefined}
                  disabled={(i.cancellableQty ?? 0) === 0}
                  onChange={(v) => setQty((s) => ({ ...s, [i.id]: v }))}
                  aria-label={`${i.model} 取消数量`}
                />
                {err && (
                  <div
                    style={{ fontSize: 12, color: palette.red, marginTop: 4 }}
                  >
                    {err}
                  </div>
                )}
              </div>
              <b
                style={{
                  ...num,
                  textAlign: 'right',
                  color: n > 0 && !err ? palette.orange : palette.mute,
                }}
              >
                {n > 0 && !err
                  ? formatAmount(n * Number(i.unitPrice), cur)
                  : '—'}
              </b>
            </div>
          );
        })}
        {gone.length > 0 && (
          <div
            style={{
              display: 'flex',
              gap: 10,
              flexWrap: 'wrap',
              padding: '10px 14px',
              borderTop: `1px solid ${palette.hairline}`,
              opacity: 0.6,
            }}
          >
            {gone.map((i) => (
              <span key={i.id} style={{ fontSize: 12, color: palette.sub }}>
                {i.model} <Pill tone="mute">已全部取消</Pill>
              </span>
            ))}
          </div>
        )}
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '240px 1fr',
          gap: 16,
          marginBottom: 14,
        }}
      >
        <div>
          <div style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}>
            <span style={{ color: palette.red }}>* </span>取消原因
          </div>
          <Select
            style={{ width: '100%' }}
            value={reason}
            onChange={setReason}
            options={Object.entries(reasons).map(([value, label]) => ({
              value,
              label,
            }))}
            aria-label="取消原因"
          />
        </div>
        <div>
          <div style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}>
            说明
          </div>
          <Input
            value={remark}
            maxLength={200}
            onChange={(e) => setRemark(e.target.value)}
            placeholder="如 供应商回复停产，替代型号客户不接受"
            aria-label="说明"
          />
        </div>
      </div>
      {cut > 0 && Object.keys(errors).length === 0 && (
        <div
          style={{
            padding: '12px 16px',
            borderRadius: 12,
            background: palette.violetSoft,
            fontSize: 13,
            color: palette.sub,
          }}
        >
          型号小计少 <b style={num}>{formatAmount(cut, cur)}</b>
          ；下一步按折扣与已到账算出新的订单合计和多收，并列出对采购与库存的处理。
        </div>
      )}
    </>
  );

  const step = (
    n: number,
    title: string,
    tag: React.ReactNode,
    desc: string,
  ) => (
    <div key={`${n}-${title}`} style={{ display: 'flex', gap: 12 }}>
      <span
        style={{
          flex: 'none',
          width: 22,
          height: 22,
          borderRadius: 11,
          background: palette.accentSoft,
          color: palette.link,
          fontSize: 12,
          fontWeight: 700,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {n}
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{ color: palette.sub }}>{title}</span>
          <span style={{ marginLeft: 'auto' }}>{tag}</span>
        </div>
        <div style={{ fontSize: 12, color: palette.mute }}>{desc}</div>
      </div>
    </div>
  );

  const kv = (k: string, v: React.ReactNode) => (
    <div
      key={k}
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        gap: 12,
        fontSize: 13,
        marginBottom: 4,
      }}
    >
      <span style={{ color: palette.mute }}>{k}</span>
      <b style={{ ...num, color: palette.ink }}>{v}</b>
    </div>
  );

  const stepCount = (l: PartialCancelPreview['items'][number]) =>
    l.tracked
      ? (l.unplaced > 0 ? 1 : 0) +
        l.drafts.length +
        (l.held > 0 ? 1 : 0) +
        l.ordered.length +
        (l.untracked > 0 ? 1 : 0)
      : 1;

  const confirmStep = preview && (
    <>
      <div
        style={{
          padding: '12px 14px',
          borderRadius: 12,
          background: palette.inset,
          marginBottom: 14,
        }}
      >
        {preview.items.map((l) =>
          kv(
            l.model,
            `取消 ${l.quantity} 个（${l.quantityBefore} → ${l.quantityAfter}）`,
          ),
        )}
        {kv('原因', reasons[body.reason] ?? body.reason)}
        {Number(preview.discountBefore) !== Number(preview.discountAfter) &&
          kv(
            '整单折扣',
            `${formatAmount(preview.discountBefore, cur)} → ${formatAmount(preview.discountAfter, cur)}`,
          )}
        {kv(
          '订单合计',
          `${formatAmount(preview.totalBefore, cur)} → ${formatAmount(preview.totalAfter, cur)}`,
        )}
      </div>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 10,
        }}
      >
        <ShoppingCartOutlined style={{ color: palette.link }} />
        <b style={{ color: palette.ink }}>采购与库存会这样处理</b>
      </div>
      <div style={{ display: 'grid', gap: 12, marginBottom: 14 }}>
        {preview.items.flatMap((l, idx) => {
          const rows: React.ReactNode[] = [];
          // 连续编号：接着前面型号的步骤数
          let n = preview.items
            .slice(0, idx)
            .reduce((c, x) => c + stepCount(x), 0);
          const prefix = preview.items.length > 1 ? `${l.model}：` : '';
          if (!l.tracked) {
            rows.push(
              step(
                ++n,
                `${prefix}系统外采购`,
                <Pill tone="gray">只改订单</Pill>,
                '这个型号没有采购需求，不处理采购单与库存',
              ),
            );
            return rows;
          }
          if (l.unplaced > 0)
            rows.push(
              step(
                ++n,
                `${prefix}还没排进采购单的需求`,
                <Pill tone="gray">减少 {l.unplaced} 个</Pill>,
                '直接关闭',
              ),
            );
          for (const d of l.drafts)
            rows.push(
              step(
                ++n,
                `${prefix}草稿采购单 ${d.poNo ?? ''}`,
                <Pill tone="gray">撤出 {d.quantity} 个</Pill>,
                '草稿行数量减少，采购员不用处理',
              ),
            );
          if (l.held > 0)
            rows.push(
              step(
                ++n,
                `${prefix}已在仓（合格入库、还没出库）`,
                <Pill tone="cyan">{l.held} 个转闲置库存</Pill>,
                '来源「订单部分取消」，成本按入库成本',
              ),
            );
          for (const p of l.ordered)
            rows.push(
              step(
                ++n,
                `${prefix}已下单 ${p.poNo ?? ''}`,
                <Pill tone="orange">减少 {p.quantity} 个</Pill>,
                `采购单行标「来源订单减少 ${p.quantity} 个」，由采购员决定取消或保留；保留的到货后多出部分自动转闲置库存`,
              ),
            );
          if (l.untracked > 0)
            rows.push(
              step(
                ++n,
                `${prefix}没有对应采购`,
                <Pill tone="gray">{l.untracked} 个只改订单</Pill>,
                '没有排单、下单或在仓的数量',
              ),
            );
          return rows;
        })}
      </div>
      {Number(preview.overpaidAfter) > 0 && (
        <div
          style={{
            padding: '12px 16px',
            borderRadius: 12,
            background: palette.violetSoft,
          }}
        >
          <b style={{ color: palette.violet }}>
            已到账 {formatAmount(preview.received, cur)}，多出{' '}
            {formatAmount(preview.overpaidAfter, cur)}
          </b>
          <div style={{ fontSize: 12, color: palette.sub, marginTop: 2 }}>
            进入收款管理的「待处理多收」，财务转客户余额或退回客户；不算退款。
          </div>
        </div>
      )}
    </>
  );

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={preview ? 640 : 860}
      title={
        <div>
          <div>{preview ? '确认部分取消' : '部分取消'}</div>
          <div style={{ fontSize: 12, color: palette.mute, fontWeight: 400 }}>
            {preview
              ? '确认后不能撤回'
              : `${order.soNo} · ${order.customerName}`}
          </div>
        </div>
      }
      footer={
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{ fontSize: 12, color: palette.mute }}>
            {preview
              ? '写入取消记录与操作日志'
              : (blocker ?? '下一步会列出对采购和库存的影响，再确认')}
          </span>
          <span style={{ marginLeft: 'auto' }} />
          {preview ? (
            <>
              <Button onClick={() => setPreview(undefined)}>上一步</Button>
              <Button type="primary" loading={busy} onClick={submit}>
                确认部分取消
              </Button>
            </>
          ) : (
            <>
              <Button onClick={onClose}>取消</Button>
              <Button
                type="primary"
                loading={busy}
                disabled={!!blocker}
                onClick={next}
              >
                下一步
              </Button>
            </>
          )}
        </div>
      }
    >
      {preview ? confirmStep : editStep}
    </Modal>
  );
};
