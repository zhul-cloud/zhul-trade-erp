import {
  App,
  DatePicker,
  Input,
  InputNumber,
  Modal,
  Segmented,
  Select,
  Space,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import { readBizError } from '@/pages/sales/service';
import { AttachmentWall } from '@/pages/warehouse/components';
import type { Attachment } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { useDictTexts } from '@/utils/dict';
import { formatAmount } from '@/utils/format';
import { ACCEPT_PROOF } from '../payables/components';
import {
  DICT_REFUND_REASON,
  REFUND_METHODS,
  type Refundable,
  refundApi,
} from './service';

/** 登记退款（spec finance/customer-refund）：订单详情与退款单页共用 */
const RefundModal: React.FC<{
  soId?: number;
  open: boolean;
  onClose: () => void;
  onDone: () => void;
}> = ({ soId, open, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const reasons = useDictTexts(DICT_REFUND_REASON);
  const [info, setInfo] = useState<Refundable>();
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [reason, setReason] = useState<string>();
  const [amount, setAmount] = useState<number | null>(null);
  const [cny, setCny] = useState<number | null>(null);
  const [cnyTouched, setCnyTouched] = useState(false);
  const [method, setMethod] = useState(1);
  const [remark, setRemark] = useState('');
  const [files, setFiles] = useState<Attachment[]>([]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open || !soId) return;
    setDate(dayjs());
    setReason(undefined);
    setAmount(null);
    setCny(null);
    setCnyTouched(false);
    setMethod(1);
    setRemark('');
    setFiles([]);
    setInfo(undefined);
    refundApi
      .refundable(soId)
      .then(setInfo)
      .catch((e) => message.error(readBizError(e).message));
  }, [open, soId, message]);

  // 实际退出人民币默认按平均到账汇率折算，手动改过后不再跟着变
  useEffect(() => {
    if (!cnyTouched && info && amount) {
      setCny(Math.round(amount * Number(info.averageRate) * 100) / 100);
    }
  }, [amount, info, cnyTouched]);

  const submit = async () => {
    if (!soId || !reason || !amount || files.length === 0) {
      message.error('请填写原因、退款金额并上传凭证');
      return;
    }
    setSaving(true);
    try {
      const r = await refundApi.create({
        soId,
        refundDate: date.format('YYYY-MM-DD'),
        reason,
        amount,
        amountCny: cny ?? undefined,
        method,
        remark: remark.trim() || undefined,
        attachmentIds: files.map((f) => f.id),
      });
      message.success(`已登记退款 ${r.refundNo}`);
      onDone();
      onClose();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const cur = info?.currencyCode ?? 'USD';
  const label = (t: string) => (
    <div style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}>
      <span style={{ color: palette.red }}>* </span>
      {t}
    </div>
  );

  return (
    <Modal
      open={open}
      title="登记退款"
      width={720}
      okText="确认退款"
      cancelText="取消"
      confirmLoading={saving}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
    >
      <Space orientation="vertical" size={14} style={{ width: '100%' }}>
        {info && (
          <div style={{ fontSize: 12, color: palette.mute }}>
            {info.soNo} · 已到账 {formatAmount(info.received, cur)} · 已退{' '}
            {formatAmount(info.refunded, cur)} · 最多还能退{' '}
            <b style={{ color: palette.ink }}>
              {formatAmount(info.refundable, cur)}
            </b>
          </div>
        )}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
            gap: 12,
          }}
        >
          <div>
            {label('退款日期')}
            <DatePicker
              value={date}
              allowClear={false}
              disabledDate={(d) => d.isAfter(dayjs(), 'day')}
              onChange={(d) => d && setDate(d)}
              style={{ width: '100%' }}
            />
          </div>
          <div>
            {label('原因')}
            <Select
              value={reason}
              onChange={setReason}
              style={{ width: '100%' }}
              placeholder="选择原因"
              options={reasons.map((r) => ({ value: r.code, label: r.name }))}
            />
          </div>
          <div>
            {label('退款金额')}
            <InputNumber<number>
              min={0.01}
              max={info?.refundable}
              precision={2}
              prefix={cur}
              value={amount}
              onChange={setAmount}
              style={{ width: '100%' }}
              aria-label="退款金额"
            />
          </div>
          <div>
            {label('实际退出人民币')}
            <InputNumber<number>
              min={0.01}
              precision={2}
              prefix="CNY"
              value={cny}
              onChange={(v) => {
                setCny(v);
                setCnyTouched(true);
              }}
              style={{ width: '100%' }}
              aria-label="实际退出人民币"
            />
          </div>
        </div>
        {info && (
          <div style={{ fontSize: 12, color: palette.mute }}>
            默认按这张订单的平均到账汇率 {Number(info.averageRate).toFixed(6)}{' '}
            折算，按平台或银行实际扣款修改；退款会冲减这张订单的毛利。
          </div>
        )}
        <div>
          {label('退款方式')}
          <Segmented<number>
            value={method}
            onChange={setMethod}
            options={REFUND_METHODS}
          />
        </div>
        <div>
          {label('凭证')}
          <AttachmentWall
            ownerType="REFUND"
            value={files}
            onChange={setFiles}
            max={10}
            accept={ACCEPT_PROOF}
          />
        </div>
        <Input
          placeholder="说明（选填）"
          maxLength={500}
          value={remark}
          onChange={(e) => setRemark(e.target.value)}
        />
      </Space>
    </Modal>
  );
};

export default RefundModal;
