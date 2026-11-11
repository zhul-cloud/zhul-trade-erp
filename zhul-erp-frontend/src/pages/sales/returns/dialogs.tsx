import { App, Checkbox, Input, InputNumber, Modal, Select, Space } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import {
  DICT_REFUND_REASON,
  REFUND_METHODS,
  type RequestRefundable,
  refundRequestApi,
} from '@/pages/finance/refunds/service';
import {
  HANDLING,
  type SalesReturn,
  salesReturnApi,
  TH_STATUS,
} from '@/pages/sales/after-sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { useDictTexts } from '@/utils/dict';
import { formatAmount } from '@/utils/format';
import { readBizError } from '../service';

const RQ_DEFAULT_REASON = 'QUALITY';

/** 数量 × 成交单价，四舍五入（HALF_UP）到 2 位，与后端生成退款明细一致 */
export const lineRefund = (quantity: number, unitPrice?: number) =>
  Math.round((quantity * (unitPrice ?? 0) + Number.EPSILON) * 100) / 100;

const Label: React.FC<{ text: string; required?: boolean }> = ({
  text,
  required,
}) => {
  const { palette } = useAppTheme();
  return (
    <div style={{ fontSize: 13, color: palette.sub, marginBottom: 6 }}>
      {text}
      {required && <span style={{ color: palette.red }}> *</span>}
    </div>
  );
};

/**
 * 确认退货（spec sales/after-sales「销售退货单」）：确认后生成退货入库单；
 * 退货退款同时生成退款申请——明细默认 退货数量 × 成交单价，可逐行改小。
 */
export const ConfirmReturnModal: React.FC<{
  data?: SalesReturn;
  onClose: () => void;
  onDone: () => void;
}> = ({ data, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const reasons = useDictTexts(DICT_REFUND_REASON);
  const refund = data?.handling === HANDLING.REFUND;
  const [info, setInfo] = useState<RequestRefundable>();
  const [amounts, setAmounts] = useState<Record<number, number | null>>({});
  const [reason, setReason] = useState<string>(RQ_DEFAULT_REASON);
  const [method, setMethod] = useState(2);
  const [payee, setPayee] = useState('');
  const [remark, setRemark] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!data) return;
    const a: Record<number, number | null> = {};
    data.items.forEach((i) => {
      a[i.id] = lineRefund(i.quantity, i.unitPrice);
    });
    setAmounts(a);
    setReason(RQ_DEFAULT_REASON);
    setPayee('');
    setRemark('');
    setInfo(undefined);
    if (data.handling === HANDLING.REFUND)
      refundRequestApi
        .refundable(data.soId)
        .then((r) => {
          setInfo(r);
          setMethod(r.platformOnly ? 1 : 2);
        })
        .catch((e) => message.error(readBizError(e).message));
  }, [data, message]);
  const cur = data?.currencyCode ?? info?.currencyCode ?? 'USD';
  const total = useMemo(
    () =>
      Math.round(
        Object.values(amounts).reduce<number>((s, v) => s + (v ?? 0), 0) * 100,
      ) / 100,
    [amounts],
  );
  if (!data) return null;
  return (
    <Modal
      open
      onCancel={onClose}
      width={refund ? 720 : 520}
      title={`确认退货 ${data.returnNo}`}
      okText="确认退货"
      okButtonProps={{ loading: busy }}
      onOk={async () => {
        if (refund && total <= 0) {
          message.error('请填写退款金额');
          return;
        }
        setBusy(true);
        try {
          const r = await salesReturnApi.confirm(
            data.id,
            refund
              ? {
                  lines: data.items.map((i) => ({
                    returnItemId: i.id,
                    amount: amounts[i.id] ?? 0,
                  })),
                  reason,
                  method,
                  payeeInfo: payee.trim() || undefined,
                  remark: remark.trim() || undefined,
                }
              : undefined,
          );
          message.success(
            r.refunds?.[0]
              ? `已确认，生成 ${r.receipt?.no ?? '退货入库单'} 与 ${r.refunds[0].requestNo}`
              : `已确认，生成 ${r.receipt?.no ?? '退货入库单'}`,
          );
          onDone();
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
      destroyOnHidden
    >
      <Space orientation="vertical" size={14} style={{ width: '100%' }}>
        <div style={{ fontSize: 13, color: palette.sub }}>
          确认后生成退货入库单，交仓库验收
          {refund
            ? data.refundFirst
              ? '；勾选了先退款，退款申请直接交审批或财务。'
              : '；退款申请等仓库验收后才能执行。'
            : '。'}
        </div>
        {refund && (
          <>
            {info && (
              <div style={{ fontSize: 13, color: palette.sub }}>
                {info.soNo} · 已到账 {formatAmount(info.received, cur)}
                ，最多还能退{' '}
                <b style={{ color: palette.ink }}>
                  {formatAmount(info.available, cur)}
                </b>
              </div>
            )}
            <div
              style={{
                borderRadius: 12,
                border: `1px solid ${palette.hairline}`,
                overflow: 'hidden',
              }}
            >
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr 70px 120px 140px',
                  gap: 12,
                  padding: '8px 12px',
                  fontSize: 12,
                  color: palette.mute,
                  background: palette.inset,
                }}
              >
                <span>型号</span>
                <span>退货</span>
                <span>成交单价</span>
                <span>退款金额</span>
              </div>
              {data.items.map((i) => (
                <div
                  key={i.id}
                  style={{
                    display: 'grid',
                    gridTemplateColumns: '1fr 70px 120px 140px',
                    gap: 12,
                    padding: '10px 12px',
                    alignItems: 'center',
                    borderTop: `1px solid ${palette.hairline}`,
                  }}
                >
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontWeight: 600, color: palette.ink }}>
                      {i.model}
                    </div>
                    <div style={{ fontSize: 12, color: palette.mute }}>
                      {[i.brand, i.category].filter(Boolean).join(' · ')}
                    </div>
                  </div>
                  <span>{i.quantity}</span>
                  <span>{formatAmount(i.unitPrice ?? 0, cur)}</span>
                  <InputNumber<number>
                    min={0}
                    precision={2}
                    value={amounts[i.id] ?? null}
                    onChange={(v) => setAmounts((s) => ({ ...s, [i.id]: v }))}
                    style={{ width: '100%' }}
                    aria-label={`${i.model} 退款金额`}
                  />
                </div>
              ))}
            </div>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: 12,
              }}
            >
              <div>
                <Label text="退款原因" required />
                <Select
                  style={{ width: '100%' }}
                  value={reason}
                  onChange={setReason}
                  options={reasons.map((r) => ({
                    value: r.code,
                    label: r.name,
                  }))}
                  aria-label="退款原因"
                />
              </div>
              <div>
                <Label text="建议去向" required />
                <Select
                  style={{ width: '100%' }}
                  value={method}
                  onChange={setMethod}
                  options={REFUND_METHODS.map((m) => ({
                    ...m,
                    disabled: !!info?.platformOnly && m.value !== 1,
                  }))}
                  aria-label="建议去向"
                />
              </div>
            </div>
            <Input
              value={payee}
              maxLength={300}
              onChange={(e) => setPayee(e.target.value)}
              placeholder="客户收款信息（银行汇款时填写）"
              aria-label="客户收款信息"
            />
            <Input
              value={remark}
              maxLength={500}
              onChange={(e) => setRemark(e.target.value)}
              placeholder="退款说明（选填）"
              aria-label="退款说明"
            />
            <div style={{ fontSize: 15 }}>
              退款合计 <b>{formatAmount(total, cur)}</b>
            </div>
          </>
        )}
      </Space>
    </Modal>
  );
};

/** 修改：待确认时改数量、快递；待入库且仓库还没验收时只能补填快递 */
export const EditReturnModal: React.FC<{
  data?: SalesReturn;
  onClose: () => void;
  onDone: () => void;
}> = ({ data, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [qty, setQty] = useState<Record<number, number | null>>({});
  const [carrier, setCarrier] = useState('');
  const [trackingNo, setTrackingNo] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!data) return;
    const q: Record<number, number | null> = {};
    data.items.forEach((i) => {
      q[i.soItemId] = i.quantity;
    });
    setQty(q);
    setCarrier(data.carrier ?? '');
    setTrackingNo(data.trackingNo ?? '');
  }, [data]);
  if (!data) return null;
  const confirming = data.status === TH_STATUS.CONFIRMING;
  return (
    <Modal
      open
      onCancel={onClose}
      width={560}
      title={confirming ? `修改 ${data.returnNo}` : `补填快递 ${data.returnNo}`}
      okText="保存"
      okButtonProps={{ loading: busy }}
      onOk={async () => {
        setBusy(true);
        try {
          await salesReturnApi.update(data.id, {
            items: Object.entries(qty)
              .filter(([, v]) => (v ?? 0) > 0)
              .map(([k, v]) => ({
                soItemId: Number(k),
                quantity: v as number,
              })),
            carrier: carrier.trim() || undefined,
            trackingNo: trackingNo.trim() || undefined,
            remark: data.remark,
          });
          message.success('已保存');
          onDone();
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
      destroyOnHidden
    >
      <Space orientation="vertical" size={14} style={{ width: '100%' }}>
        {confirming &&
          data.items.map((i) => (
            <div
              key={i.id}
              style={{ display: 'flex', alignItems: 'center', gap: 12 }}
            >
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 600, color: palette.ink }}>
                  {i.model}
                </div>
                <div style={{ fontSize: 12, color: palette.mute }}>
                  {[i.brand, i.category].filter(Boolean).join(' · ')}
                </div>
              </div>
              <InputNumber<number>
                min={0}
                precision={0}
                value={qty[i.soItemId] ?? null}
                onChange={(v) => setQty((s) => ({ ...s, [i.soItemId]: v }))}
                style={{ width: 120 }}
                aria-label={`${i.model} 退货数量`}
              />
            </div>
          ))}
        <div
          style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}
        >
          <Input
            value={carrier}
            maxLength={32}
            onChange={(e) => setCarrier(e.target.value)}
            placeholder="客户寄回快递，如 DHL"
            aria-label="快递公司"
          />
          <Input
            value={trackingNo}
            maxLength={64}
            onChange={(e) => setTrackingNo(e.target.value)}
            placeholder="快递单号"
            aria-label="快递单号"
          />
        </div>
      </Space>
    </Modal>
  );
};

/** 售后单发起「仅退货 / 退货退款」：选型号与数量，生成待确认的销售退货单 */
export const CreateReturnModal: React.FC<{
  open: boolean;
  handling: number;
  afterSalesId: number;
  asNo: string;
  soNo?: string;
  currencyCode?: string;
  items: {
    soItemId: number;
    model: string;
    brand?: string;
    category?: string;
    shippedQty: number;
    returnable: number;
    unitPrice?: number;
  }[];
  onClose: () => void;
  onDone: (r: SalesReturn) => void;
}> = ({
  open,
  handling,
  afterSalesId,
  asNo,
  soNo,
  currencyCode,
  items,
  onClose,
  onDone,
}) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const refund = handling === HANDLING.REFUND;
  const [qty, setQty] = useState<Record<number, number | null>>({});
  const [carrier, setCarrier] = useState('');
  const [trackingNo, setTrackingNo] = useState('');
  const [remark, setRemark] = useState('');
  const [refundFirst, setRefundFirst] = useState(false);
  const [busy, setBusy] = useState(false);
  // 每次打开时重置（items 由调用方每次渲染重新计算，不能放进 effect 依赖）
  const [wasOpen, setWasOpen] = useState(false);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) {
      const q: Record<number, number | null> = {};
      items.forEach((i) => {
        q[i.soItemId] =
          items.length === 1 && i.returnable > 0 ? i.returnable : null;
      });
      setQty(q);
      setCarrier('');
      setTrackingNo('');
      setRemark('');
      setRefundFirst(false);
    }
  }
  const cur = currencyCode ?? 'USD';
  const estimate = items.reduce(
    (s, i) => s + lineRefund(qty[i.soItemId] ?? 0, i.unitPrice),
    0,
  );
  const cols = refund ? '1fr 64px 64px 110px 120px' : '1fr 64px 64px 110px';
  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={refund ? 720 : 600}
      title={
        <div>
          <div>{refund ? '退货退款' : '仅退货'}</div>
          <div style={{ fontSize: 12, color: palette.mute, fontWeight: 400 }}>
            {asNo}
            {soNo ? ` · ${soNo}` : ''}
          </div>
        </div>
      }
      okText="生成销售退货单"
      okButtonProps={{ loading: busy }}
      onOk={async () => {
        const lines = Object.entries(qty)
          .filter(([, v]) => (v ?? 0) > 0)
          .map(([k, v]) => ({ soItemId: Number(k), quantity: v as number }));
        if (lines.length === 0) {
          message.error('请填写退货数量');
          return;
        }
        setBusy(true);
        try {
          const r = await salesReturnApi.create({
            afterSalesId,
            handling,
            refundFirst: refund && refundFirst,
            items: lines,
            carrier: carrier.trim() || undefined,
            trackingNo: trackingNo.trim() || undefined,
            remark: remark.trim() || undefined,
          });
          message.success(`已生成 ${r.returnNo}，确认后交仓库入库`);
          onDone(r);
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
      destroyOnHidden
    >
      <Space orientation="vertical" size={14} style={{ width: '100%' }}>
        {items.length === 0 ? (
          <div style={{ color: palette.mute }}>
            售后单没有选订单型号，不能退货
          </div>
        ) : (
          <div
            style={{
              borderRadius: 12,
              border: `1px solid ${palette.hairline}`,
              overflow: 'hidden',
            }}
          >
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: cols,
                gap: 12,
                padding: '8px 12px',
                fontSize: 12,
                color: palette.mute,
                background: palette.inset,
              }}
            >
              <span>型号</span>
              <span>已出运</span>
              <span>可退</span>
              <span>退货数量</span>
              {refund && <span>预计退款</span>}
            </div>
            {items.map((i) => (
              <div
                key={i.soItemId}
                style={{
                  display: 'grid',
                  gridTemplateColumns: cols,
                  gap: 12,
                  padding: '10px 12px',
                  alignItems: 'center',
                  borderTop: `1px solid ${palette.hairline}`,
                }}
              >
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontWeight: 600, color: palette.ink }}>
                    {i.model}
                  </div>
                  <div style={{ fontSize: 12, color: palette.mute }}>
                    {[i.brand, i.category].filter(Boolean).join(' · ')}
                  </div>
                </div>
                <span>{i.shippedQty}</span>
                <span>{i.returnable}</span>
                <InputNumber<number>
                  min={0}
                  max={i.returnable}
                  precision={0}
                  disabled={i.returnable <= 0}
                  value={qty[i.soItemId] ?? null}
                  onChange={(v) => setQty((s) => ({ ...s, [i.soItemId]: v }))}
                  style={{ width: '100%' }}
                  aria-label={`${i.model} 退货数量`}
                />
                {refund && (
                  <span>
                    {qty[i.soItemId]
                      ? formatAmount(
                          lineRefund(qty[i.soItemId] ?? 0, i.unitPrice),
                          cur,
                        )
                      : '—'}
                  </span>
                )}
              </div>
            ))}
          </div>
        )}
        <div
          style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}
        >
          <Input
            value={carrier}
            maxLength={32}
            onChange={(e) => setCarrier(e.target.value)}
            placeholder="客户寄回快递，如 DHL"
            aria-label="快递公司"
          />
          <Input
            value={trackingNo}
            maxLength={64}
            onChange={(e) => setTrackingNo(e.target.value)}
            placeholder="运单号，可后补"
            aria-label="快递单号"
          />
        </div>
        <Input
          value={remark}
          maxLength={300}
          onChange={(e) => setRemark(e.target.value)}
          placeholder="备注（选填）"
          aria-label="备注"
        />
        {refund && (
          <div
            style={{
              padding: '12px 14px',
              borderRadius: 12,
              background: palette.inset,
            }}
          >
            <Checkbox
              checked={refundFirst}
              onChange={(e) => setRefundFirst(e.target.checked)}
            >
              <b>先退款</b>
              <div style={{ fontSize: 12, color: palette.mute }}>
                不勾选时，仓库验收后财务才能退款
              </div>
            </Checkbox>
          </div>
        )}
        {refund && (
          <div style={{ fontSize: 15 }}>
            预计退款 <b>{formatAmount(estimate, cur)}</b>
            <span style={{ fontSize: 12, color: palette.mute }}>
              {' '}
              · 确认退货时可逐行调整
            </span>
          </div>
        )}
      </Space>
    </Modal>
  );
};
