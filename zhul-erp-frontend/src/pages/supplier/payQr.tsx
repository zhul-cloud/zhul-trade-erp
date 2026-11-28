import { App, Button, Image, Popconfirm, Spin, Upload } from 'antd';
import dayjs from 'dayjs';
import { ImageIcon } from 'lucide-react';
import React, { useEffect, useState } from 'react';
import { UserName } from '@/components/UserName';
import { ChannelIcon, PAY_CHANNEL_NAMES } from '@/pages/finance/payables/payQr';
import { useAppTheme } from '@/theme/AppTheme';
import { readBizError, type SupplierAttachment, supplierApi } from './service';

const isImage = (file: File) =>
  /\.(jpe?g|png)$/i.test(file.name) ||
  ['image/jpeg', 'image/png'].includes(file.type);

/** 一种渠道的收款码：有图显示缩略图（可放大），有编辑权限时可更换、删除；没有时可上传 */
const QrCard: React.FC<{
  supplierId: number;
  channel: number;
  qr?: SupplierAttachment;
  canEdit: boolean;
  onChange: (list: SupplierAttachment[]) => void;
}> = ({ supplierId, channel, qr, canEdit, onChange }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [url, setUrl] = useState<string>();
  const [busy, setBusy] = useState(false);
  const name = `${PAY_CHANNEL_NAMES[channel]}收款码`;

  useEffect(() => {
    setUrl(undefined);
    if (!qr) return;
    let alive = true;
    supplierApi
      .attachmentBlobUrl(supplierId, qr.id)
      .then((u) => alive && setUrl(u))
      .catch(() => alive && setUrl(undefined));
    return () => {
      alive = false;
    };
  }, [supplierId, qr]);

  const upload = async (file: File) => {
    if (!isImage(file)) {
      message.error('收款码只支持 JPG、PNG 图片');
      return;
    }
    setBusy(true);
    try {
      const up = await supplierApi.uploadAttachment(file);
      onChange(await supplierApi.savePayQr(supplierId, channel, up));
      message.success(`${name}已保存`);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const picker = (children: React.ReactNode) => (
    <Upload
      accept=".jpg,.jpeg,.png"
      showUploadList={false}
      beforeUpload={(file) => {
        upload(file);
        return false;
      }}
    >
      {children}
    </Upload>
  );

  return (
    <div
      style={{
        flex: '1 1 280px',
        minWidth: 0,
        padding: 16,
        borderRadius: 12,
        border: `1px solid ${palette.hairline}`,
        background: palette.inset,
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 12,
        }}
      >
        <ChannelIcon channel={channel} size={18} />
        <b style={{ color: palette.ink, flex: 1 }}>{name}</b>
        {qr && canEdit && (
          <>
            {picker(
              <Button type="link" size="small" loading={busy}>
                更换
              </Button>,
            )}
            <Popconfirm
              title={`删除${name}？`}
              onConfirm={async () => {
                try {
                  onChange(await supplierApi.removePayQr(supplierId, channel));
                } catch (e) {
                  message.error(readBizError(e).message);
                }
              }}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          </>
        )}
      </div>
      {qr ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <div
            style={{
              width: 110,
              height: 110,
              borderRadius: 10,
              background: '#fff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              overflow: 'hidden',
              flex: 'none',
            }}
          >
            {url ? (
              <Image
                src={url}
                alt={name}
                width={110}
                height={110}
                style={{ objectFit: 'contain' }}
              />
            ) : (
              <Spin size="small" />
            )}
          </div>
          <div style={{ fontSize: 12, color: palette.mute }}>
            {dayjs(qr.createTime).format('YYYY-MM-DD')} 上传
            {qr.createBy && (
              <>
                {' · '}
                <UserName value={qr.createBy} />
              </>
            )}
          </div>
        </div>
      ) : canEdit ? (
        picker(
          <button
            type="button"
            disabled={busy}
            style={{
              width: '100%',
              padding: '22px 12px',
              borderRadius: 10,
              border: `1px dashed ${palette.hairline}`,
              background: 'transparent',
              color: palette.sub,
              cursor: 'pointer',
              font: 'inherit',
            }}
          >
            {busy ? (
              <Spin size="small" />
            ) : (
              <ImageIcon size="1em" style={{ fontSize: 20 }} />
            )}
            <div style={{ marginTop: 6, color: palette.ink }}>
              上传收款码图片
            </div>
            <div style={{ fontSize: 12, color: palette.mute }}>
              支持 JPG、PNG，对方发来的截图即可
            </div>
          </button>,
        )
      ) : (
        <span style={{ color: palette.mute }}>未上传</span>
      )}
    </div>
  );
};

/** 供应商详情 → 结算信息里的支付宝、微信收款码（原型 V6） */
export const PayQrCodes: React.FC<{
  supplierId: number;
  codes?: SupplierAttachment[];
  canEdit: boolean;
}> = ({ supplierId, codes, canEdit }) => {
  const { palette } = useAppTheme();
  const [list, setList] = useState<SupplierAttachment[]>(codes ?? []);
  useEffect(() => setList(codes ?? []), [codes]);
  return (
    <div style={{ marginTop: 18 }}>
      <div style={{ fontSize: 13, color: palette.mute, marginBottom: 8 }}>
        收款码（私对私选支付宝、微信付款时显示给付款人扫码）
      </div>
      <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap' }}>
        {[2, 3].map((c) => (
          <QrCard
            key={c}
            supplierId={supplierId}
            channel={c}
            qr={list.find((x) => x.payChannel === c)}
            canEdit={canEdit}
            onChange={setList}
          />
        ))}
      </div>
    </div>
  );
};
