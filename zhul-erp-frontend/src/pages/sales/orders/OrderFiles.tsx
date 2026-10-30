import {
  FileExcelOutlined,
  FileImageOutlined,
  FilePdfOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { App, Button, Input, Popconfirm, Upload } from 'antd';
import React, { useState } from 'react';
import { usePreview } from '@/pages/warehouse/components';
import { type Attachment, attachmentApi } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { downloadFile } from '@/utils/fileLink';
import { formatDateTime } from '@/utils/format';
import { Card } from '../components';
import { type Order, orderApi, readBizError } from '../service';

/** 销售订单附件：PI、合同、客户 PO 等，PDF、图片与 Excel */
export const ORDER_FILE_ACCEPT = '.pdf,.jpg,.jpeg,.png,.webp,.xlsx,.xls';
export const ORDER_FILE_MAX = 10;
const MAX_BYTES = 10 * 1024 * 1024;

const fileIcon = (name: string) =>
  /\.(xlsx?)$/i.test(name) ? (
    <FileExcelOutlined />
  ) : /\.pdf$/i.test(name) ? (
    <FilePdfOutlined />
  ) : (
    <FileImageOutlined />
  );

const sizeText = (bytes: number) =>
  bytes >= 1024 * 1024
    ? `${(bytes / 1024 / 1024).toFixed(1)} MB`
    : `${Math.max(1, Math.round(bytes / 1024))} KB`;

/** 附件列表：图片、PDF 可预览，都能下载；onRemove 为空时只读 */
export const OrderFileList: React.FC<{
  files: Attachment[];
  onRemove?: (a: Attachment) => void;
  /** 删除前确认（订单详情里删的是已保存的附件） */
  confirmRemove?: boolean;
}> = ({ files, onRemove, confirmRemove }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const preview = usePreview();
  const download = (a: Attachment) =>
    downloadFile(`/api/v1/attachments/${a.id}`).catch((e) =>
      message.error(readBizError(e).message),
    );
  return (
    <div style={{ display: 'grid', gap: 8 }}>
      {files.map((a) => {
        const remove = onRemove && <a style={{ color: palette.red }}>删除</a>;
        return (
          <div
            key={a.id}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              padding: '8px 12px',
              borderRadius: 10,
              background: palette.inset,
            }}
          >
            <span style={{ color: palette.link }}>{fileIcon(a.fileName)}</span>
            <span
              style={{
                color: palette.ink,
                minWidth: 0,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {a.fileName}
            </span>
            <span
              style={{
                color: palette.mute,
                fontSize: 12,
                whiteSpace: 'nowrap',
              }}
            >
              {[
                sizeText(a.fileSize),
                a.uploadedByName,
                a.createTime && formatDateTime(a.createTime),
              ]
                .filter(Boolean)
                .join(' · ')}
            </span>
            <span style={{ flex: 1 }} />
            {!/\.xlsx?$/i.test(a.fileName) && (
              <a onClick={() => preview.open(a)}>预览</a>
            )}
            <a onClick={() => download(a)}>下载</a>
            {onRemove &&
              (confirmRemove ? (
                <Popconfirm
                  title={`删除「${a.fileName}」？`}
                  okText="删除"
                  okButtonProps={{ danger: true }}
                  onConfirm={() => onRemove(a)}
                >
                  {remove}
                </Popconfirm>
              ) : (
                <span onClick={() => onRemove(a)}>{remove}</span>
              ))}
          </div>
        );
      })}
      {preview.node}
    </div>
  );
};

/** 上传按钮：逐个上传，传完回调；支持全局粘贴上传 */
export const OrderFileUpload: React.FC<{
  count: number;
  onUploaded: (a: Attachment) => void;
  dragger?: boolean;
}> = ({ count, onUploaded, dragger }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [uploading, setUploading] = useState(0);
  const beforeUpload = (file: File) => {
    if (count + uploading >= ORDER_FILE_MAX) {
      message.error(`附件最多 ${ORDER_FILE_MAX} 个`);
      return Upload.LIST_IGNORE;
    }
    if (file.size > MAX_BYTES) {
      message.error(`「${file.name}」超过 10MB`);
      return Upload.LIST_IGNORE;
    }
    setUploading((n) => n + 1);
    attachmentApi
      .upload('SALES_ORDER', file)
      .then(onUploaded)
      .catch((e) => message.error(`「${file.name}」${readBizError(e).message}`))
      .finally(() => setUploading((n) => n - 1));
    return Upload.LIST_IGNORE;
  };
  const text =
    uploading > 0
      ? `正在上传 ${uploading} 个文件…`
      : `上传附件（PDF、图片、Excel，单个不超过 10MB，最多 ${ORDER_FILE_MAX} 个，可直接粘贴）`;
  return dragger ? (
    <Upload.Dragger
      multiple
      accept={ORDER_FILE_ACCEPT}
      showUploadList={false}
      beforeUpload={beforeUpload}
    >
      <span style={{ color: palette.sub }}>
        <UploadOutlined style={{ color: palette.link }} /> {text}
      </span>
    </Upload.Dragger>
  ) : (
    <Upload
      multiple
      accept={ORDER_FILE_ACCEPT}
      showUploadList={false}
      beforeUpload={beforeUpload}
    >
      <Button icon={<UploadOutlined />} loading={uploading > 0}>
        上传附件
      </Button>
    </Upload>
  );
};

/** 订单详情的「附件与备注」（原型 10-30销售单附件与订货号 H2）：有「维护附件与备注」权限且订单有效时可补传、删除、改备注 */
export const OrderFilesCard: React.FC<{
  order: Order;
  canEdit: boolean;
  onChanged: () => void;
}> = ({ order, canEdit, onChanged }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [editing, setEditing] = useState<string>();
  const [saving, setSaving] = useState(false);
  const files = order.attachments ?? [];
  const run = async (fn: () => Promise<unknown>, ok: string) => {
    try {
      await fn();
      message.success(ok);
      onChanged();
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };
  const saveRemark = async () => {
    setSaving(true);
    await run(
      () => orderApi.remark(order.id, (editing ?? '').trim()),
      '备注已保存',
    );
    setSaving(false);
    setEditing(undefined);
  };
  return (
    <Card style={{ padding: 20, marginBottom: 16 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          marginBottom: files.length > 0 || canEdit ? 12 : 8,
        }}
      >
        <b style={{ color: palette.ink, fontSize: 15 }}>附件与备注</b>
        <span style={{ flex: 1 }} />
        {canEdit && files.length < ORDER_FILE_MAX && (
          <OrderFileUpload
            count={files.length}
            onUploaded={(a) =>
              run(() => orderApi.addAttachments(order.id, [a.id]), '附件已上传')
            }
          />
        )}
      </div>
      {files.length > 0 ? (
        <OrderFileList
          files={files}
          confirmRemove
          onRemove={
            canEdit
              ? (a) =>
                  run(
                    () => orderApi.removeAttachment(order.id, a.id),
                    '附件已删除',
                  )
              : undefined
          }
        />
      ) : (
        <div style={{ color: palette.mute, fontSize: 13 }}>
          还没有附件{canEdit ? '，可以上传 PI、合同、客户 PO 等资料' : ''}
        </div>
      )}
      <div
        style={{
          display: 'flex',
          gap: 12,
          alignItems: editing === undefined ? 'baseline' : 'start',
          marginTop: 12,
          fontSize: 13,
        }}
      >
        <span style={{ color: palette.mute, flexShrink: 0 }}>备注</span>
        {editing === undefined ? (
          <>
            <span style={{ color: order.remark ? palette.ink : palette.mute }}>
              {order.remark || '—'}
            </span>
            {canEdit && (
              <a onClick={() => setEditing(order.remark ?? '')}>编辑</a>
            )}
          </>
        ) : (
          <div style={{ flex: 1, display: 'grid', gap: 8 }}>
            <Input.TextArea
              autoFocus
              rows={2}
              maxLength={500}
              showCount
              value={editing}
              onChange={(e) => setEditing(e.target.value)}
              aria-label="备注"
            />
            <div style={{ display: 'flex', gap: 8, justifyContent: 'end' }}>
              <Button size="small" onClick={() => setEditing(undefined)}>
                取消
              </Button>
              <Button
                size="small"
                type="primary"
                loading={saving}
                onClick={saveRemark}
              >
                保存
              </Button>
            </div>
          </div>
        )}
      </div>
    </Card>
  );
};
