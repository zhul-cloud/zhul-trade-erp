import { CloudUploadOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { App, Button, Input, Modal, Space, Upload } from 'antd';
import React, { useState } from 'react';
import ChunkUploadList, { useChunkUploads } from '@/components/ChunkUploadList';
import { useAppTheme } from '@/theme/AppTheme';
import {
  ATTACHMENT_ACCEPT,
  ATTACHMENT_UPLOAD,
  PATHS,
} from '../shared/constants';
import NameplateUploader, {
  type PickedPlate,
  toRefs,
} from '../shared/NameplateUploader';
import { inquiryApi, readBizError } from '../shared/service';

/** 追加型号（spec inquiry/inquiry-intake「追加型号」）：粘贴新内容或上传附件，AI 解析或手动录入 */
const AppendModal: React.FC<{
  inquiryId: number;
  open: boolean;
  /** 询盘上已有附件的合计大小，用于每单 2GB 的合计上限 */
  existingBytes?: number;
  onClose: () => void;
  onStarted: () => void;
}> = ({ inquiryId, open, existingBytes = 0, onClose, onStarted }) => {
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const [content, setContent] = useState('');
  const uploads = useChunkUploads(ATTACHMENT_UPLOAD, existingBytes);
  const files = uploads.done;
  const [plates, setPlates] = useState<PickedPlate[]>([]);
  const [plateUploading, setPlateUploading] = useState(0);
  const [saving, setSaving] = useState(false);
  const busy = plateUploading + uploads.busy > 0;

  const beforeUpload = (file: File, list: File[]) => {
    if (file === list[0]) uploads.add(list);
    return Upload.LIST_IGNORE;
  };

  /** 附件还在上传时关闭先确认，确认后中断上传 */
  const close = () => {
    if (uploads.busy === 0) {
      onClose();
      return;
    }
    modal.confirm({
      title: '附件还没传完',
      content: '关闭后正在上传的附件会中断。',
      okText: '仍然关闭',
      cancelText: '继续上传',
      onOk: () => {
        uploads.reset();
        onClose();
      },
    });
  };

  const start = async (manual: boolean) => {
    if (
      !manual &&
      !content.trim() &&
      files.length === 0 &&
      plates.length === 0
    ) {
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
        nameplates: toRefs(plates),
        manual,
      });
      setContent('');
      uploads.reset();
      setPlates([]);
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
      onCancel={close}
      destroyOnHidden
      footer={
        <Space>
          {uploads.busy > 0 && (
            <span style={{ color: palette.orange, fontSize: 13 }}>
              还有 {uploads.busy} 个附件没传完
            </span>
          )}
          <Button onClick={close}>取消</Button>
          <Button disabled={saving || busy} onClick={() => start(true)}>
            手动录入
          </Button>
          <Button
            type="primary"
            loading={saving}
            disabled={busy}
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
          把图片、Excel 或 PDF 拖到这里，或点击选择
        </p>
        <p style={{ margin: 0, color: palette.mute, fontSize: 12 }}>
          单个不超过 500MB，整张询盘的附件合计不超过 2GB
        </p>
      </Upload.Dragger>
      <ChunkUploadList
        items={uploads.items}
        onResume={uploads.resume}
        onRemove={uploads.remove}
      />
      <div style={{ marginTop: 12 }}>
        <NameplateUploader
          value={plates}
          onChange={setPlates}
          onUploadingChange={setPlateUploading}
          hint="客户新发来的型号铭牌照片放这里，只对应这次追加的型号；采购在「我的询价」里能直接看到。不要放带客户信息的图片。"
        />
      </div>
    </Modal>
  );
};

export default AppendModal;
