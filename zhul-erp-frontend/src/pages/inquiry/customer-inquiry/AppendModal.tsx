import { CloseOutlined, CloudUploadOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { App, Button, Input, Modal, Space, Upload } from 'antd';
import React, { useState } from 'react';
import { formatSize } from '@/pages/crm/opportunity/components';
import { useAppTheme } from '@/theme/AppTheme';
import {
  ATTACHMENT_ACCEPT,
  ATTACHMENT_MAX_BYTES,
  MAX_ATTACHMENTS,
  PATHS,
} from '../shared/constants';
import { inquiryApi, readBizError, type UploadedFile } from '../shared/service';

/** 追加型号（spec inquiry/inquiry-intake「追加型号」）：粘贴新内容或上传附件，AI 解析或手动录入 */
const AppendModal: React.FC<{
  inquiryId: number;
  open: boolean;
  onClose: () => void;
  onStarted: () => void;
}> = ({ inquiryId, open, onClose, onStarted }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [content, setContent] = useState('');
  const [files, setFiles] = useState<UploadedFile[]>([]);
  const [uploading, setUploading] = useState(0);
  const [saving, setSaving] = useState(false);

  const beforeUpload = (file: File) => {
    if (files.length + uploading >= MAX_ATTACHMENTS) {
      message.error(`附件最多 ${MAX_ATTACHMENTS} 个`);
      return Upload.LIST_IGNORE;
    }
    if (file.size > ATTACHMENT_MAX_BYTES) {
      message.error(`「${file.name}」超过 10MB`);
      return Upload.LIST_IGNORE;
    }
    setUploading((n) => n + 1);
    inquiryApi
      .upload(file)
      .then((f) => setFiles((prev) => [...prev, f]))
      .catch((e) => message.error(`「${file.name}」${readBizError(e).message}`))
      .finally(() => setUploading((n) => n - 1));
    return Upload.LIST_IGNORE;
  };

  const start = async (manual: boolean) => {
    if (!manual && !content.trim() && files.length === 0) {
      message.error('请粘贴追加的内容或上传附件');
      return;
    }
    setSaving(true);
    try {
      const a = await inquiryApi.startAppend(inquiryId, {
        rawContent: content.trim() || undefined,
        attachments: files.map((f) => ({
          fileKey: f.fileKey,
          fileName: f.fileName,
        })),
        manual,
      });
      setContent('');
      setFiles([]);
      onClose();
      if (manual) {
        history.push(`${PATHS.inquiries}/${inquiryId}/confirm?append=${a.id}`);
      } else {
        message.success('已提交 AI 解析，完成后在这里确认追加的型号');
        onStarted();
      }
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title="追加型号"
      width={640}
      onCancel={onClose}
      destroyOnHidden
      footer={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button
            disabled={saving || uploading > 0}
            onClick={() => start(true)}
          >
            手动录入
          </Button>
          <Button
            type="primary"
            loading={saving}
            disabled={uploading > 0}
            onClick={() => start(false)}
          >
            AI 解析
          </Button>
        </Space>
      }
    >
      <div style={{ color: palette.mute, fontSize: 12, margin: '4px 0 12px' }}>
        只解析这次追加的内容；确认后新型号接在原有型号后面，待询价的会生成新的询价任务，原有型号和报价不受影响。
      </div>
      <Input.TextArea
        rows={5}
        maxLength={20000}
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder="把客户新发来的型号粘贴到这里"
        style={{ marginBottom: 12 }}
      />
      <Upload.Dragger
        multiple
        accept={ATTACHMENT_ACCEPT}
        showUploadList={false}
        beforeUpload={beforeUpload}
      >
        <p style={{ fontSize: 22, color: palette.link, margin: 0 }}>
          <CloudUploadOutlined />
        </p>
        <p style={{ margin: '4px 0 0', color: palette.sub }}>
          {uploading > 0
            ? `正在上传 ${uploading} 个文件…`
            : '把图片、Excel 或 PDF 拖到这里，或点击选择'}
        </p>
      </Upload.Dragger>
      {files.length > 0 && (
        <div style={{ display: 'grid', gap: 8, marginTop: 12 }}>
          {files.map((f) => (
            <div
              key={f.fileKey}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '6px 12px',
                borderRadius: 10,
                background: palette.hover,
              }}
            >
              <span style={{ color: palette.ink }}>{f.fileName}</span>
              <span style={{ color: palette.mute, fontSize: 12 }}>
                {formatSize(f.fileSize)}
              </span>
              <Button
                type="text"
                size="small"
                aria-label={`移除 ${f.fileName}`}
                icon={<CloseOutlined />}
                style={{ marginLeft: 'auto' }}
                onClick={() =>
                  setFiles((prev) =>
                    prev.filter((p) => p.fileKey !== f.fileKey),
                  )
                }
              />
            </div>
          ))}
        </div>
      )}
    </Modal>
  );
};

export default AppendModal;
