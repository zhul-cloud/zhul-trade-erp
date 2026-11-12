import { WarningOutlined } from '@ant-design/icons';
import {
  App,
  AutoComplete,
  Checkbox,
  DatePicker,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import {
  type Payable,
  payableApi,
  SOURCE,
} from '@/pages/finance/payables/service';
import { useUserOptions } from '@/pages/sales/orders/dialogs';
import { readBizError } from '@/pages/sales/service';
import { AttachmentWall } from '@/pages/warehouse/components';
import type { Attachment } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { CARRIERS } from '../shipments/ShipmentDrawer';
import { PR_SOURCE, type PurchaseReturn, purchaseReturnApi } from './service';

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

const noFuture = (d: Dayjs) => d.isAfter(dayjs(), 'day');

/** 寄出：日期、快递必填；填了运费时默认自己垫付（进待报销），也可选公司支付 */
export const ShipModal: React.FC<{
  data?: PurchaseReturn;
  onClose: () => void;
  onDone: () => void;
}> = ({ data, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const users = useUserOptions();
  const [date, setDate] = useState<Dayjs | null>(dayjs());
  const [carrier, setCarrier] = useState('');
  const [trackingNo, setTrackingNo] = useState('');
  const [freight, setFreight] = useState<number | null>(null);
  const [payerId, setPayerId] = useState<number>();
  const [companyPaid, setCompanyPaid] = useState(false);
  const [proofs, setProofs] = useState<Attachment[]>([]);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!data) return;
    setDate(dayjs());
    setCarrier('');
    setTrackingNo('');
    setFreight(null);
    setPayerId(undefined);
    setCompanyPaid(false);
    setProofs([]);
  }, [data]);
  if (!data) return null;
  const hasFreight = (freight ?? 0) > 0;
  return (
    <Modal
      open
      onCancel={onClose}
      width={560}
      title={`寄出 · ${data.returnNo}`}
      okText="寄出"
      okButtonProps={{ loading: busy }}
      onOk={async () => {
        if (!date || !carrier.trim() || !trackingNo.trim()) {
          message.error('请填写寄出日期、快递公司和快递单号');
          return;
        }
        setBusy(true);
        try {
          await purchaseReturnApi.ship(data.id, {
            shipDate: date.format('YYYY-MM-DD'),
            carrier: carrier.trim(),
            trackingNo: trackingNo.trim(),
            freightCny: freight ?? undefined,
            payerId: hasFreight && !companyPaid ? payerId : undefined,
            companyPaid: hasFreight && companyPaid,
            proofIds: proofs.map((f) => f.id),
          });
          message.success('已寄出');
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
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            gap: 12,
          }}
        >
          <div>
            <Label text="寄出日期" required />
            <DatePicker
              value={date}
              onChange={setDate}
              disabledDate={noFuture}
              style={{ width: '100%' }}
              aria-label="寄出日期"
            />
          </div>
          <div>
            <Label text="运费（元）" />
            <InputNumber<number>
              min={0}
              precision={2}
              prefix="CNY"
              value={freight}
              onChange={setFreight}
              placeholder="没有可不填"
              style={{ width: '100%' }}
              aria-label="运费"
            />
          </div>
          <div>
            <Label text="快递公司" required />
            <AutoComplete
              options={CARRIERS}
              value={carrier}
              onChange={setCarrier}
              style={{ width: '100%' }}
              aria-label="快递公司"
            />
          </div>
          <div>
            <Label text="快递单号" required />
            <Input
              maxLength={64}
              value={trackingNo}
              onChange={(e) => setTrackingNo(e.target.value)}
              aria-label="快递单号"
            />
          </div>
        </div>
        {hasFreight && (
          <div
            style={{
              padding: '12px 14px',
              borderRadius: 12,
              background: palette.inset,
              display: 'grid',
              gap: 10,
            }}
          >
            <Checkbox
              checked={companyPaid}
              onChange={(e) => setCompanyPaid(e.target.checked)}
            >
              公司支付（不进待报销）
            </Checkbox>
            {!companyPaid && (
              <>
                <Select
                  allowClear
                  showSearch={{ optionFilterProp: 'label' }}
                  placeholder="垫付人，默认自己"
                  value={payerId}
                  onChange={setPayerId}
                  options={users}
                  style={{ width: '100%' }}
                  aria-label="运费垫付人"
                />
                <AttachmentWall
                  ownerType="RETURN_PROOF"
                  value={proofs}
                  onChange={setProofs}
                  max={5}
                  empty="运费截图（选填，报销时也可补传）"
                  accept="image/*,application/pdf"
                />
              </>
            )}
          </div>
        )}
      </Space>
    </Modal>
  );
};

/**
 * 完成：供应商签收。售后退货必须填退款或抵扣（没有填 0），按采购订单币种，
 * 完成后冲减采购订单应付；到货差异来源只记签收。
 */
export const CompleteModal: React.FC<{
  data?: PurchaseReturn;
  onClose: () => void;
  onDone: () => void;
}> = ({ data, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [date, setDate] = useState<Dayjs | null>(dayjs());
  const [amount, setAmount] = useState<number | null>(null);
  const [note, setNote] = useState('');
  const [payable, setPayable] = useState<Payable | null>();
  const [busy, setBusy] = useState(false);
  const afterSales = data?.sourceType === PR_SOURCE.AFTER_SALES;
  useEffect(() => {
    if (!data) return;
    setDate(dayjs());
    setAmount(null);
    setNote('');
    setPayable(undefined);
    if (data.sourceType === PR_SOURCE.AFTER_SALES && data.poId)
      payableApi
        .bySource(SOURCE.PO, data.poId)
        .then(setPayable)
        // 没有应付权限时不显示应付变化
        .catch(() => setPayable(null));
  }, [data]);
  if (!data) return null;
  const cur = data.currencyCode ?? 'CNY';
  const after = payable ? payable.amount - (amount ?? 0) : 0;
  const over = payable ? payable.paidAmount - after : 0;
  return (
    <Modal
      open
      onCancel={onClose}
      width={560}
      title={`完成 · ${data.returnNo}`}
      okText="完成"
      okButtonProps={{ loading: busy }}
      onOk={async () => {
        if (!date) {
          message.error('请填写签收日期');
          return;
        }
        if (afterSales && amount == null) {
          message.error('请填写供应商退款或抵扣金额（没有就填 0）');
          return;
        }
        setBusy(true);
        try {
          await purchaseReturnApi.complete(data.id, {
            signedDate: date.format('YYYY-MM-DD'),
            refundAmount: afterSales ? (amount ?? 0) : undefined,
            settleNote: note.trim() || undefined,
          });
          message.success('已完成');
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
        <div>
          <Label text="签收日期" required />
          <DatePicker
            value={date}
            onChange={setDate}
            disabledDate={(d) =>
              noFuture(d) ||
              (!!data.shipDate && d.isBefore(dayjs(data.shipDate), 'day'))
            }
            style={{ width: '100%' }}
            aria-label="签收日期"
          />
        </div>
        {afterSales && (
          <>
            <div>
              <Label text="供应商退款或抵扣" required />
              <InputNumber<number>
                min={0}
                precision={2}
                value={amount}
                onChange={setAmount}
                suffix={`${cur} · 采购订单币种`}
                placeholder="没有就填 0"
                style={{ width: '100%' }}
                aria-label="供应商退款或抵扣"
              />
            </div>
            {payable && (
              <div style={{ display: 'grid', gap: 8, fontSize: 13 }}>
                <div style={{ display: 'flex' }}>
                  <span style={{ flex: 1 }}>{data.poNo} 应付</span>
                  <b>
                    {formatAmount(payable.amount, cur)} →{' '}
                    {formatAmount(after, cur)}
                  </b>
                </div>
                <div style={{ display: 'flex' }}>
                  <span style={{ flex: 1 }}>已付</span>
                  <b>{formatAmount(payable.paidAmount, cur)}</b>
                </div>
                {over > 0 && (
                  <div
                    style={{
                      padding: '10px 12px',
                      borderRadius: 10,
                      background: palette.orangeSoft,
                      color: palette.orange,
                    }}
                  >
                    <WarningOutlined /> 已付超出 {formatAmount(over, cur)}
                    ，完成后成为供应商欠款，由财务退款或抵扣
                  </div>
                )}
              </div>
            )}
          </>
        )}
        <div>
          <Label text="说明" />
          <Input
            maxLength={300}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder={afterSales ? '如 供应商同意全额退款' : '选填'}
            aria-label="说明"
          />
        </div>
      </Space>
    </Modal>
  );
};

/** 改数量（待寄出、售后来源）：只能减少；减少的数量回到退货入库单待处理 */
export const QuantityModal: React.FC<{
  data?: PurchaseReturn;
  onClose: () => void;
  onDone: () => void;
}> = ({ data, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [qty, setQty] = useState<Record<number, number | null>>({});
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!data) return;
    const q: Record<number, number | null> = {};
    data.items.forEach((i) => {
      q[i.id] = i.quantity;
    });
    setQty(q);
  }, [data]);
  if (!data) return null;
  return (
    <Modal
      open
      onCancel={onClose}
      width={520}
      title={`改数量 · ${data.returnNo}`}
      okText="保存"
      okButtonProps={{ loading: busy }}
      onOk={async () => {
        setBusy(true);
        try {
          await purchaseReturnApi.quantities(
            data.id,
            data.items.map((i) => ({
              itemId: i.id,
              quantity: qty[i.id] ?? 0,
            })),
          );
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
      <Space orientation="vertical" size={12} style={{ width: '100%' }}>
        <div style={{ fontSize: 13, color: palette.sub }}>
          只能减少，减少的数量回到退货入库单「待处理」，可重新选择去向。
        </div>
        {data.items.map((i) => (
          <div
            key={i.id}
            style={{ display: 'flex', alignItems: 'center', gap: 12 }}
          >
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontWeight: 600, color: palette.ink }}>
                {i.model}
              </div>
              <div style={{ fontSize: 12, color: palette.mute }}>
                原 {i.quantity} 个
              </div>
            </div>
            <InputNumber<number>
              min={0}
              max={i.quantity}
              precision={0}
              value={qty[i.id] ?? null}
              onChange={(v) => setQty((s) => ({ ...s, [i.id]: v }))}
              style={{ width: 120 }}
              aria-label={`${i.model} 数量`}
            />
          </div>
        ))}
      </Space>
    </Modal>
  );
};
