import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  SendOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import { App, Button, InputNumber, Modal } from 'antd';
import React, { useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { type CreditPanel, readBizError } from '../service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

/**
 * 收款卡片里的余额信息（spec sales/payment-receipt）：已到账中的余额抵扣、代收状态与「转给客户」入口、待处理多收；
 * 手动订单可「抵扣客户余额」。PI 与订单详情共用。
 */
const CreditInfo: React.FC<{
  credit?: CreditPanel;
  currency: string;
  /** 订单收款状态还没到齐时的待付金额（手动订单抵扣用） */
  unpaid?: number;
  /** 手动订单抵扣客户余额；不传时不显示按钮（PI 在编辑页填写抵扣） */
  onUseCredit?: (amount: number) => Promise<unknown>;
}> = ({ credit, currency, unpaid, onUseCredit }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const [open, setOpen] = useState(false);
  const [amount, setAmount] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  if (!credit) return null;
  const showCollection = credit.collection > 0;
  const showOverpaid = credit.overpaidPending > 0;
  const showCredit = credit.creditReceived > 0;
  const canUse = !!onUseCredit && credit.available > 0 && (unpaid ?? 0) > 0;
  if (!showCollection && !showOverpaid && !showCredit && !canUse) return null;
  const max = Math.min(credit.available, unpaid ?? 0);

  const banner = (
    tone: 'violet' | 'orange' | 'accent',
    icon: React.ReactNode,
    title: React.ReactNode,
    sub: React.ReactNode,
    action?: React.ReactNode,
  ) => {
    const bg =
      tone === 'violet'
        ? palette.violetSoft
        : tone === 'orange'
          ? palette.orangeSoft
          : palette.accentSoft;
    const fg =
      tone === 'violet'
        ? palette.violet
        : tone === 'orange'
          ? palette.orange
          : palette.link;
    return (
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '12px 14px',
          borderRadius: 12,
          background: bg,
        }}
      >
        <span style={{ color: fg, fontSize: 16 }}>{icon}</span>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 600, color: palette.ink }}>{title}</div>
          <div style={{ fontSize: 12, color: palette.mute }}>{sub}</div>
        </div>
        {action}
      </div>
    );
  };

  return (
    <div style={{ display: 'grid', gap: 8, marginBottom: 12 }}>
      {showCredit &&
        banner(
          'accent',
          <WalletOutlined />,
          <>已到账中余额抵扣 {formatAmount(credit.creditReceived, currency)}</>,
          '用客户余额付的部分，不需要财务确认，不参与结汇',
        )}
      {showCollection &&
        (credit.collectionTransferred
          ? banner(
              'violet',
              <CheckCircleOutlined />,
              <>
                代收 {formatAmount(credit.collectionNet, currency)}{' '}
                已转入客户余额
              </>,
              <span style={num}>
                代收款 {formatAmount(credit.collection, currency)} −
                客户承担的平台手续费{' '}
                {formatAmount(credit.collectionFee, currency)} · 价值{' '}
                {formatAmount(credit.collectionValue, 'CNY')}
              </span>,
              access['finance:refund:edit'] && access.financeCredits ? (
                <Button
                  size="small"
                  icon={<SendOutlined />}
                  onClick={() => history.push('/finance/credits')}
                >
                  转给客户
                </Button>
              ) : undefined,
            )
          : banner(
              'violet',
              <ClockCircleOutlined />,
              <>代收 {formatAmount(credit.collection, currency)} 待收齐</>,
              '收款到齐后，扣除客户承担的平台手续费，自动转入客户余额',
            ))}
      {showOverpaid &&
        banner(
          'orange',
          <ClockCircleOutlined />,
          <>客户多付 {formatAmount(credit.overpaidPending, currency)} 待处理</>,
          '多收的部分不算收入，由财务在「收款管理 → 待处理多收」转客户余额或退回客户',
          access.financeReceipts ? (
            <Button
              size="small"
              onClick={() => history.push('/finance/receipts?tab=overpaid')}
            >
              去处理
            </Button>
          ) : undefined,
        )}
      {canUse &&
        banner(
          'accent',
          <WalletOutlined />,
          <>客户有可用余额 {formatAmount(credit.available, currency)}</>,
          '可以用余额抵扣这张订单的待付金额',
          <Button
            size="small"
            type="primary"
            onClick={() => {
              setAmount(max);
              setOpen(true);
            }}
          >
            抵扣客户余额
          </Button>,
        )}
      <Modal
        open={open}
        title="抵扣客户余额"
        okText="确认抵扣"
        cancelText="取消"
        confirmLoading={saving}
        onCancel={() => setOpen(false)}
        onOk={async () => {
          if (!amount || amount > max || !onUseCredit) {
            message.error(
              `抵扣金额须大于 0 且不超过 ${formatAmount(max, currency)}`,
            );
            return;
          }
          setSaving(true);
          try {
            await onUseCredit(amount);
            message.success('已抵扣，计入已到账');
            setOpen(false);
          } catch (e) {
            message.error(readBizError(e).message);
          } finally {
            setSaving(false);
          }
        }}
        destroyOnHidden
      >
        <div style={{ display: 'grid', gap: 12 }}>
          <div style={{ fontSize: 12, color: palette.mute }}>
            可用余额 {formatAmount(credit.available, currency)} · 待付{' '}
            {formatAmount(unpaid ?? 0, currency)}
            ；生成一条「余额抵扣」收款记录，计入已到账
          </div>
          <InputNumber<number>
            autoFocus
            min={0.01}
            max={max}
            precision={2}
            prefix={currency}
            value={amount}
            onChange={setAmount}
            style={{ width: '100%' }}
            aria-label="抵扣金额"
          />
        </div>
      </Modal>
    </div>
  );
};

export default CreditInfo;
