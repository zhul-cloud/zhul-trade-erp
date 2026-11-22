import {
  AlipayCircleFilled,
  QrcodeOutlined,
  ScanOutlined,
  UploadOutlined,
  WechatFilled,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import { Button, Modal } from 'antd';
import dayjs from 'dayjs';
import React, { useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import type { PayeeQr } from './service';

/** 付款渠道：1-银行卡、2-支付宝、3-微信 */
export const PAY_CHANNEL = { BANK: 1, ALIPAY: 2, WECHAT: 3 } as const;
export const PAY_CHANNEL_NAMES: Record<number, string> = {
  1: '银行卡',
  2: '支付宝',
  3: '微信',
};

const BRAND: Record<number, string> = { 2: '#1677FF', 3: '#07C160' };

export const ChannelIcon: React.FC<{ channel: number; size?: number }> = ({
  channel,
  size = 16,
}) =>
  channel === PAY_CHANNEL.WECHAT ? (
    <WechatFilled style={{ color: BRAND[3], fontSize: size }} />
  ) : (
    <AlipayCircleFilled style={{ color: BRAND[2], fontSize: size }} />
  );

/**
 * 登记付款 → 私对私选支付宝、微信时的对方收款码（原型 V4、V4b、V5）：有收款码显示缩略图，点「预览」放大扫码；
 * 没有时提示去供应商资料里上传。
 */
export const PayeeQrPanel: React.FC<{
  channel: number;
  qrs: PayeeQr[];
  payeeName: string;
  supplierId?: number;
  /** 预览里显示的应付金额 */
  amount?: number | null;
  currency: string;
}> = ({ channel, qrs, payeeName, supplierId, amount, currency }) => {
  const { palette } = useAppTheme();
  const [open, setOpen] = useState(false);
  const qr = qrs.find((q) => q.payChannel === channel);
  const name = PAY_CHANNEL_NAMES[channel];
  const box: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: 16,
    padding: 16,
    borderRadius: 12,
    background: palette.inset,
    border: `1px solid ${qr ? palette.primaryLine : palette.hairline}`,
  };

  if (!qr) {
    return (
      <div style={box}>
        <QrcodeOutlined style={{ fontSize: 22, color: palette.mute }} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 600, color: palette.ink }}>
            {payeeName}还没有上传{name}收款码
          </div>
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 2 }}>
            可以去供应商资料里上传，或让对方发收款码后再付
          </div>
        </div>
        {supplierId && (
          <Button
            icon={<UploadOutlined />}
            onClick={() => history.push(`/supplier/list/${supplierId}`)}
          >
            去上传
          </Button>
        )}
      </div>
    );
  }

  return (
    <>
      <div style={box}>
        <img
          src={qr.url}
          alt={`${name}收款码`}
          style={{
            width: 96,
            height: 96,
            objectFit: 'contain',
            borderRadius: 8,
            background: '#fff',
            flex: 'none',
          }}
        />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              fontWeight: 700,
              color: palette.ink,
            }}
          >
            <ChannelIcon channel={channel} size={18} />
            {qr.label}
          </div>
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
            {qr.createTime ? dayjs(qr.createTime).format('YYYY-MM-DD') : ''}{' '}
            上传{qr.createBy ? ` · ${qr.createBy}` : ''}
          </div>
          <Button
            type="primary"
            icon={<ScanOutlined />}
            style={{ marginTop: 10 }}
            onClick={() => setOpen(true)}
          >
            预览
          </Button>
        </div>
      </div>
      <Modal
        open={open}
        title="扫码付款"
        width={420}
        onCancel={() => setOpen(false)}
        footer={<Button onClick={() => setOpen(false)}>关闭</Button>}
      >
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 14,
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              fontWeight: 700,
            }}
          >
            <ChannelIcon channel={channel} size={20} />
            {name} · {payeeName}
          </div>
          <img
            src={qr.url}
            alt={`${name}收款码`}
            style={{
              width: 280,
              height: 280,
              objectFit: 'contain',
              borderRadius: 12,
              background: '#fff',
              padding: 10,
            }}
          />
          {amount ? (
            <div style={{ color: palette.sub }}>
              应付{' '}
              <b
                style={{
                  fontSize: 22,
                  color: palette.ink,
                  fontVariantNumeric: 'tabular-nums',
                }}
              >
                {formatAmount(amount, currency)}
              </b>
            </div>
          ) : null}
          <div style={{ fontSize: 12, color: palette.mute }}>
            用手机{name}扫码，付款后回到登记付款上传截图
          </div>
        </div>
      </Modal>
    </>
  );
};
