import { InfoCircleOutlined } from '@ant-design/icons';
import {
  App,
  DatePicker,
  Input,
  InputNumber,
  Modal,
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
  afterSalesOptions,
  DICT_REFUND_REASON,
  REFUND_METHODS,
  REFUND_TO_CREDIT,
  type Refundable,
  type RefundRequest,
  refundApi,
  refundRequestApi,
} from './service';

const METHOD_HINTS: Record<number, string> = {
  1: '退回客户付款的平台',
  2: '填实际退出的人民币和回单',
  3: '不退现金，留作下一单抵扣',
};

/**
 * 登记退款（spec finance/customer-refund）：订单详情与退款单页共用；
 * 传入 request 时为执行退款申请（spec finance/refund-request），按申请带出金额、原因与去向
 */
const RefundModal: React.FC<{
  soId?: number;
  open: boolean;
  onClose: () => void;
  onDone: () => void;
  request?: RefundRequest;
}> = ({ soId, open, onClose, onDone, request }) => {
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
  const [afterSalesId, setAfterSalesId] = useState<number>();
  const [asOptions, setAsOptions] = useState<
    { value: number; label: string }[]
  >([]);

  useEffect(() => {
    if (!open || !soId) return undefined;
    // 关闭或换了一张申请后，上一次打开发出的请求迟到时丢弃，不能改掉这一次的币种和汇率（audit UI-054）
    let stale = false;
    setDate(dayjs());
    setReason(undefined);
    setAmount(null);
    setCny(null);
    setCnyTouched(false);
    setMethod(1);
    setRemark('');
    setFiles([]);
    setInfo(undefined);
    setAfterSalesId(request?.afterSalesId ?? undefined);
    setAsOptions([]);
    if (request) {
      setReason(request.reason);
      setAmount(Number(request.amount));
    }
    refundApi
      .refundable(soId)
      .then((r) => {
        if (stale) return;
        setInfo(r);
        setMethod(request ? request.method : r.platformOnly ? 1 : 2);
      })
      .catch((e) => {
        if (!stale) message.error(readBizError(e).message);
      });
    if (!request) {
      afterSalesOptions(soId)
        .then((rows) => {
          if (stale) return;
          setAsOptions(
            rows.map((a) => ({
              value: a.id,
              label: `${a.asNo} · ${a.statusName} · ${a.symptom}`,
            })),
          );
        })
        .catch(() => {
          if (!stale) setAsOptions([]);
        });
    }
    return () => {
      stale = true;
    };
  }, [open, soId, message, request]);

  // 实际退出人民币默认按平均到账汇率折算，手动改过后不再跟着变
  useEffect(() => {
    if (!cnyTouched && info && amount) {
      setCny(Math.round(amount * Number(info.averageRate) * 100) / 100);
    }
  }, [amount, info, cnyTouched]);

  const toCredit = method === REFUND_TO_CREDIT;
  const lockedCny =
    info && amount
      ? Math.round(amount * Number(info.creditRate) * 100) / 100
      : null;

  const submit = async () => {
    if (!soId || !reason || !amount || (!toCredit && files.length === 0)) {
      message.error(
        toCredit ? '请填写原因与退款金额' : '请填写原因、退款金额并上传凭证',
      );
      return;
    }
    setSaving(true);
    try {
      const body = {
        refundDate: date.format('YYYY-MM-DD'),
        reason,
        amount,
        amountCny: toCredit ? undefined : (cny ?? undefined),
        method,
        remark: remark.trim() || undefined,
        attachmentIds: files.map((f) => f.id),
      };
      if (request) {
        const q = await refundRequestApi.execute(request.id, body);
        message.success(`已执行，生成退款单 ${q.refundNo ?? ''}`);
      } else {
        const r = await refundApi.create({ ...body, soId, afterSalesId });
        message.success(`已登记退款 ${r.refundNo}`);
      }
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
      title={request ? `执行退款申请 ${request.requestNo}` : '登记退款'}
      width={720}
      okText={request ? '执行并生成退款单' : '确认退款'}
      cancelText="取消"
      confirmLoading={saving}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
    >
      <Space orientation="vertical" size={14} style={{ width: '100%' }}>
        {request && (
          <div
            style={{
              padding: '10px 14px',
              borderRadius: 12,
              background: palette.accentSoft,
              fontSize: 13,
              color: palette.sub,
            }}
          >
            <b style={{ color: palette.link }}>
              申请：{formatAmount(request.amount, request.currencyCode)} ·{' '}
              {request.reasonName} · 建议{request.methodName}
            </b>
            <div style={{ fontSize: 12, marginTop: 2 }}>
              {request.applicantName} 提交
              {request.asNo ? ` · 售后单 ${request.asNo}` : ''}
              {request.payeeInfo ? ` · 收款信息：${request.payeeInfo}` : ''}
              {request.remark ? ` · ${request.remark}` : ''}
            </div>
          </div>
        )}
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
              max={
                request
                  ? Math.min(
                      Number(request.amount),
                      Number(info?.refundable ?? request.amount),
                    )
                  : info?.refundable
              }
              precision={2}
              prefix={cur}
              value={amount}
              onChange={setAmount}
              style={{ width: '100%' }}
              aria-label="退款金额"
            />
          </div>
          {!toCredit && (
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
          )}
        </div>
        <div>
          {label('去向')}
          {info?.platformOnly && (
            <div
              style={{
                display: 'flex',
                gap: 8,
                alignItems: 'center',
                padding: '8px 12px',
                borderRadius: 10,
                marginBottom: 10,
                background: palette.orangeSoft,
                color: palette.orange,
                fontSize: 12,
                fontWeight: 600,
              }}
            >
              <InfoCircleOutlined />
              这张订单有平台收款，售后退款只能原路退回平台
            </div>
          )}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
              gap: 10,
            }}
          >
            {REFUND_METHODS.map((m) => {
              const disabled = !!info?.platformOnly && m.value !== 1;
              const on = method === m.value;
              return (
                <button
                  key={m.value}
                  type="button"
                  aria-pressed={on}
                  disabled={disabled}
                  onClick={() => setMethod(m.value)}
                  style={{
                    textAlign: 'left',
                    padding: '10px 12px',
                    borderRadius: 12,
                    cursor: disabled ? 'not-allowed' : 'pointer',
                    opacity: disabled ? 0.45 : 1,
                    background: on ? palette.accentSoft : palette.inset,
                    border: `${on ? 1.5 : 1}px solid ${on ? palette.link : palette.hairline}`,
                    color: palette.ink,
                    font: 'inherit',
                  }}
                >
                  <div style={{ fontWeight: 600 }}>{m.label}</div>
                  <div style={{ fontSize: 12, color: palette.mute }}>
                    {disabled ? '平台收款的订单不可用' : METHOD_HINTS[m.value]}
                  </div>
                </button>
              );
            })}
          </div>
        </div>
        {info &&
          (toCredit ? (
            <div
              style={{
                padding: '12px 14px',
                borderRadius: 12,
                background: palette.accentSoft,
                fontSize: 12,
                color: palette.sub,
                display: 'grid',
                gap: 4,
              }}
            >
              <div>
                按订单现金收款的加权到账汇率{' '}
                {Number(info.creditRate).toFixed(6)} 折算并锁定：
                <b style={{ color: palette.ink }}>
                  {lockedCny != null ? formatAmount(lockedCny, 'CNY') : '—'}
                </b>
              </div>
              <div style={{ color: palette.link, fontWeight: 600 }}>
                客户 {cur} 余额将增加 {amount ? formatAmount(amount, cur) : '—'}
                ，可在下一张 PI 抵扣；仍冲减这张订单的毛利
              </div>
            </div>
          ) : (
            <div style={{ fontSize: 12, color: palette.mute }}>
              默认按这张订单的平均到账汇率 {Number(info.averageRate).toFixed(6)}{' '}
              折算，按平台或银行实际扣款修改；退款会冲减这张订单的毛利。
            </div>
          ))}
        <div>
          {toCredit ? (
            <div style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}>
              凭证（选填）
            </div>
          ) : (
            label('凭证')
          )}
          <AttachmentWall
            ownerType="REFUND"
            value={files}
            onChange={setFiles}
            max={10}
            accept={ACCEPT_PROOF}
          />
        </div>
        {!request && asOptions.length > 0 && (
          <Select
            allowClear
            style={{ width: '100%' }}
            placeholder="关联售后单（选填）"
            value={afterSalesId}
            onChange={setAfterSalesId}
            options={asOptions}
            aria-label="关联售后单"
          />
        )}
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
