import { history, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, InputNumber, Modal, Segmented, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { Card } from '@/pages/inquiry/shared/components';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { tableScrollX } from '@/utils/tableScroll';
import {
  emptyPayout,
  FieldLabel,
  PayoutFields,
  type PayoutValue,
  PlatformRefundFields,
  payoutBody,
} from '../credits/CreditModals';
import { creditApi, type Overpaid } from '../credits/service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

/** 收款管理 → 待处理多收（spec sales/payment-receipt「多收的处理」）：转客户余额，或退回客户 */
const OverpaidTab: React.FC<{ onCount?: (n: number) => void }> = ({
  onCount,
}) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canProcess = !!access['sales:pi:receipt-confirm'];
  const [rows, setRows] = useState<Overpaid[] | null>(null);
  const [processing, setProcessing] = useState<{
    row: Overpaid;
    mode: 'CREDIT' | 'REFUND';
  }>();

  const load = useCallback(async () => {
    try {
      const list = await creditApi.overpaid();
      setRows(list);
      onCount?.(list.length);
    } catch (e) {
      message.error(readBizError(e).message);
      setRows([]);
    }
  }, [message, onCount]);

  useEffect(() => {
    load();
  }, [load]);

  const columns: TableColumnsType<Overpaid> = [
    {
      title: 'PI / 订单',
      key: 'no',
      width: 200,
      render: (_, r) => (
        <div>
          <a
            onClick={() =>
              history.push(
                r.ownerType === 'PI'
                  ? `/sales/pi/${r.ownerId}`
                  : `/sales/orders/${r.ownerId}`,
              )
            }
          >
            {r.ownerNo}
          </a>
          <div style={{ fontSize: 12, color: palette.mute }}>
            {r.ownerType === 'PI'
              ? r.soNo
                ? `已转订单 ${r.soNo}`
                : 'PI'
              : '手动订单'}
          </div>
        </div>
      ),
    },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 200,
      render: (v: string) => <b style={{ color: palette.ink }}>{v}</b>,
    },
    {
      title: '合计',
      key: 'total',
      width: 130,
      align: 'right',
      render: (_, r) => (
        <span style={num}>{formatAmount(r.total, r.currencyCode)}</span>
      ),
    },
    {
      title: '已到账',
      key: 'received',
      width: 130,
      align: 'right',
      render: (_, r) => (
        <span style={num}>{formatAmount(r.received, r.currencyCode)}</span>
      ),
    },
    {
      title: '待处理多收',
      key: 'pending',
      width: 150,
      align: 'right',
      render: (_, r) => (
        <div style={{ ...num }}>
          <b style={{ color: palette.orange }}>
            {formatAmount(r.pending, r.currencyCode)}
          </b>
          <div style={{ fontSize: 12, color: palette.mute }}>
            ≈ {formatAmount(r.pendingCny, 'CNY')}
          </div>
        </div>
      ),
    },
    {
      title: '到账汇率',
      dataIndex: 'rate',
      width: 110,
      align: 'right',
      render: (v: number) => <span style={num}>{Number(v).toFixed(6)}</span>,
    },
    { title: '业务员', dataIndex: 'salesName', width: 100 },
    {
      title: '操作',
      key: 'op',
      width: 220,
      fixed: 'right',
      render: (_, r) =>
        canProcess ? (
          <span style={{ display: 'inline-flex', gap: 8 }}>
            <Button
              size="small"
              onClick={() => setProcessing({ row: r, mode: 'CREDIT' })}
            >
              转客户余额
            </Button>
            <Button
              size="small"
              type="primary"
              onClick={() => setProcessing({ row: r, mode: 'REFUND' })}
            >
              退回客户
            </Button>
          </span>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
  ];

  return (
    <Card>
      <Table<Overpaid>
        rowKey={(r) => `${r.ownerType}-${r.ownerId}`}
        loading={rows === null}
        columns={columns}
        dataSource={rows ?? []}
        pagination={false}
        scroll={{ x: tableScrollX(columns) }}
        locale={{ emptyText: '没有待处理的多收' }}
      />
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
        多收不计入订单收入，处理前按到账汇率估算人民币，处理后锁定。可以分几次处理；处理后从这里消失，记录在客户余额的流水里。
      </div>
      <ProcessModal
        target={processing}
        onClose={() => setProcessing(undefined)}
        onDone={(list) => {
          setRows(list);
          onCount?.(list.length);
        }}
      />
    </Card>
  );
};

const ProcessModal: React.FC<{
  target?: { row: Overpaid; mode: 'CREDIT' | 'REFUND' };
  onClose: () => void;
  onDone: (rows: Overpaid[]) => void;
}> = ({ target, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [mode, setMode] = useState<'CREDIT' | 'REFUND'>('CREDIT');
  const [amount, setAmount] = useState<number | null>(null);
  const [payout, setPayout] = useState<PayoutValue>(emptyPayout);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (target) {
      setMode(target.mode);
      setAmount(target.row.pending);
      // 原路退回时实际退出人民币默认带出账面价值
      setPayout(
        target.row.platformReceipts?.length
          ? { ...emptyPayout(), actualCny: target.row.pendingCny }
          : emptyPayout(),
      );
    }
  }, [target]);

  if (!target) return null;
  const r = target.row;
  const cur = r.currencyCode;
  const bookOf = (v: number | null) =>
    v ? Math.round(v * Number(r.rate) * 100) / 100 : undefined;
  const bookValue = bookOf(amount);
  // 有平台收款时只能原路退回平台（与售后退款规则一致）
  const platform = r.platformReceipts ?? [];
  const viaPlatform = platform.length > 0;

  const submit = async () => {
    if (!amount || amount > r.pending) {
      message.error(`金额须大于 0 且不超过 ${formatAmount(r.pending, cur)}`);
      return;
    }
    if (mode === 'REFUND' && (!payout.actualCny || payout.files.length === 0)) {
      message.error('请填写实际转出人民币并上传凭证');
      return;
    }
    setSaving(true);
    try {
      const list = await creditApi.processOverpaid({
        ownerType: r.ownerType,
        ownerId: r.ownerId,
        amount,
        mode,
        ...(mode === 'REFUND'
          ? viaPlatform
            ? { ...payoutBody(payout), method: 1, methodName: undefined }
            : payoutBody(payout)
          : {}),
      });
      message.success(
        mode === 'REFUND'
          ? viaPlatform
            ? '已原路退回平台'
            : '已退回客户'
          : '已转客户余额',
      );
      onDone(list);
      onClose();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open
      title="处理多收"
      width={560}
      okText={mode === 'REFUND' ? '确认退回' : '确认转入'}
      cancelText="取消"
      confirmLoading={saving}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
    >
      <div style={{ display: 'grid', gap: 16 }}>
        <div
          style={{
            padding: '12px 14px',
            borderRadius: 12,
            background: palette.inset,
          }}
        >
          <div style={{ fontWeight: 600, color: palette.ink }}>
            {r.customerName} · {r.ownerNo}
          </div>
          <div style={{ fontSize: 12, color: palette.mute }}>
            待处理多收 {formatAmount(r.pending, cur)} · 到账汇率{' '}
            {Number(r.rate).toFixed(6)}，价值约{' '}
            {formatAmount(r.pendingCny, 'CNY')}
          </div>
        </div>
        <Segmented<'CREDIT' | 'REFUND'>
          value={mode}
          onChange={setMode}
          options={[
            { value: 'CREDIT', label: '转客户余额' },
            { value: 'REFUND', label: '退回客户' },
          ]}
        />
        <div>
          <FieldLabel text={`金额（${cur}）`} required />
          <InputNumber<number>
            min={0.01}
            max={r.pending}
            precision={2}
            value={amount}
            onChange={(v) => {
              setAmount(v);
              if (viaPlatform)
                setPayout((p) => ({ ...p, actualCny: bookOf(v) ?? null }));
            }}
            style={{ width: '100%' }}
            aria-label="金额"
          />
        </div>
        {mode === 'REFUND' && (
          <div>
            <FieldLabel text="退回方式" required />
            <Segmented
              value={viaPlatform ? 'PLATFORM' : 'OFFLINE'}
              options={[
                {
                  value: 'PLATFORM',
                  label: '原路退回平台',
                  disabled: !viaPlatform,
                },
                {
                  value: 'OFFLINE',
                  label: '银行、支付宝等线下转出',
                  disabled: viaPlatform,
                },
              ]}
            />
            <div style={{ fontSize: 12, color: palette.mute, marginTop: 6 }}>
              {viaPlatform
                ? '这张单有平台收款，只能原路退回'
                : '这张单没有平台收款，不能原路退回'}
            </div>
          </div>
        )}
        {mode === 'REFUND' && viaPlatform && (
          <div
            style={{
              padding: '10px 14px',
              borderRadius: 10,
              background: palette.inset,
              display: 'grid',
              gap: 4,
            }}
          >
            <div style={{ fontSize: 12, color: palette.link, fontWeight: 600 }}>
              原平台收款
            </div>
            {platform.map((p) => (
              <div
                key={`${p.platform}-${p.platformOrderNo ?? ''}-${p.receiptDate}`}
                style={{ display: 'flex', gap: '2px 12px', flexWrap: 'wrap' }}
              >
                <b>{p.platform}</b>
                {p.platformOrderNo && (
                  <span style={{ color: palette.sub }}>
                    平台订单号 {p.platformOrderNo}
                  </span>
                )}
                <span style={{ color: palette.sub, ...num }}>
                  {formatAmount(p.amount, cur)} · {p.receiptDate} 到账
                </span>
              </div>
            ))}
            <div style={{ fontSize: 12, color: palette.mute }}>
              退款单自动记平台名称与平台订单号
            </div>
          </div>
        )}
        {mode === 'REFUND' &&
          (viaPlatform ? (
            <PlatformRefundFields
              value={payout}
              onChange={setPayout}
              bookValue={bookValue}
            />
          ) : (
            <PayoutFields
              value={payout}
              onChange={setPayout}
              bookValue={bookValue}
            />
          ))}
        <div style={{ fontSize: 12, color: palette.mute }}>
          {mode === 'REFUND'
            ? `「退回客户」会依次生成「多收转入」和「提现」两条余额流水，余额一进一出、核对时能对平${viaPlatform ? '；退款单去向为「原路退回平台」' : ''}。`
            : '转入后可在下一张 PI 抵扣，或之后在「余额管理」转给客户。'}
        </div>
      </div>
    </Modal>
  );
};

export default OverpaidTab;
