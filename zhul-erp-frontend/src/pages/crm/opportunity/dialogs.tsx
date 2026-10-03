import {
  CloseOutlined,
  LoadingOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  App,
  DatePicker,
  Form,
  Input,
  Modal,
  Radio,
  Select,
  Upload,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useRef, useState } from 'react';
import { useLevels } from '@/pages/inquiry/shared/components';
import { LEVEL_DEFAULT } from '@/pages/inquiry/shared/constants';
import { inquiryApi } from '@/pages/inquiry/shared/service';
import { useAppTheme } from '@/theme/AppTheme';
import { DICT_SOURCE_CHANNEL, useDictOptions } from '@/utils/dict';
import { FileChip, fileMeta, iconButton } from './components';
import {
  ATTACHMENT_ACCEPT,
  ATTACHMENT_MAX_BYTES,
  INVALID_REASONS,
  LOST_REASONS,
  MAX_ATTACHMENTS,
} from './constants';
import {
  type OpportunityDetail,
  type OpportunityStage,
  opportunityApi,
  readBizError,
} from './service';

/** 标记无效 / 输单：必须选原因；写明对统计的影响 */
export const CloseModal: React.FC<{
  detail: OpportunityDetail;
  kind: 'INVALID' | 'LOST' | null;
  onClose: () => void;
  onDone: () => void;
}> = ({ detail, kind, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [reason, setReason] = useState<number>();
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);
  const invalid = kind === 'INVALID';
  const reasons = invalid ? INVALID_REASONS : LOST_REASONS;

  return (
    <Modal
      open={!!kind}
      title={invalid ? '标记为无效' : '标记为输单'}
      okText={invalid ? '确认标记无效' : '确认输单'}
      okButtonProps={{ danger: true, disabled: !reason, loading: saving }}
      cancelText="取消"
      width={520}
      destroyOnHidden
      afterClose={() => {
        setReason(undefined);
        setNote('');
      }}
      onCancel={onClose}
      onOk={async () => {
        setSaving(true);
        try {
          await opportunityApi.close(
            detail.id,
            kind as string,
            reason,
            note.trim(),
          );
          message.success(invalid ? '已标记为无效' : '已标记为输单');
          onDone();
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setSaving(false);
        }
      }}
    >
      <p style={{ color: palette.mute, lineHeight: 1.6 }}>
        {invalid
          ? '无效商机不再出现在进行中，但会计入当天该渠道的「无效」统计。之后可以重新打开。'
          : `输单后商机结束，仍计入「有效」统计。之后可以重新打开，回到 ${detail.stageCode} ${detail.stageName}。`}
      </p>
      <div style={{ marginBottom: 8 }}>
        原因 <span style={{ color: palette.red }}>*</span>
      </div>
      <Radio.Group
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        optionType="button"
        options={reasons}
        style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 16 }}
      />
      <div style={{ marginBottom: 8 }}>
        说明 <span style={{ fontSize: 12, color: palette.mute }}>选填</span>
      </div>
      <Input.TextArea
        rows={3}
        maxLength={500}
        showCount
        value={note}
        onChange={(e) => setNote(e.target.value)}
      />
    </Modal>
  );
};

interface EditState {
  sourceChannel: number;
  firstContactDate: Dayjs;
  demandSummary?: string;
}

type EditFile = {
  id?: number;
  fileKey?: string;
  fileName: string;
  fileSize: number;
  contentType: string;
  localUrl?: string;
};

/** 编辑商机：渠道、首次接触日期、需求摘要与附件；客户信息在客户管理维护 */
export const EditModal: React.FC<{
  detail: OpportunityDetail | null;
  onClose: () => void;
  onDone: () => void;
}> = ({ detail, onClose, onDone }) => {
  const { options: sourceOptions } = useDictOptions(DICT_SOURCE_CHANNEL);
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [form] = Form.useForm<EditState>();
  const [files, setFiles] = useState<EditFile[]>([]);
  const [uploading, setUploading] = useState(0);
  const [uploadError, setUploadError] = useState('');
  const [saving, setSaving] = useState(false);
  const latest = useRef(files);
  latest.current = files;

  const upload = async (file: File) => {
    setUploadError('');
    if (!/\.(jpe?g|png|xlsx|xls|csv)$/i.test(file.name)) {
      setUploadError(`「${file.name}」：只支持图片（JPG、PNG）和 Excel`);
      return;
    }
    if (file.size > ATTACHMENT_MAX_BYTES) {
      setUploadError(`「${file.name}」：单个文件不能超过 10MB`);
      return;
    }
    if (latest.current.length >= MAX_ATTACHMENTS) {
      setUploadError(`每条商机最多 ${MAX_ATTACHMENTS} 个附件`);
      return;
    }
    setUploading((n) => n + 1);
    try {
      const res = await opportunityApi.upload(file);
      setFiles([
        ...latest.current,
        { ...res, localUrl: window.URL.createObjectURL(file) },
      ]);
    } catch (e) {
      setUploadError(`「${file.name}」：${readBizError(e).message}`);
    } finally {
      setUploading((n) => n - 1);
    }
  };

  return (
    <Modal
      open={!!detail}
      title="编辑商机"
      okText="保存"
      cancelText="取消"
      width={600}
      destroyOnHidden
      confirmLoading={saving}
      afterOpenChange={(open) => {
        if (open && detail) {
          form.setFieldsValue({
            sourceChannel: detail.sourceChannel,
            firstContactDate: dayjs(detail.firstContactDate),
            demandSummary: detail.demandSummary,
          });
          setFiles(detail.attachments.map((a) => ({ ...a })));
          setUploadError('');
        }
      }}
      afterClose={() => {
        for (const f of latest.current)
          if (f.localUrl) window.URL.revokeObjectURL(f.localUrl);
        setFiles([]);
      }}
      onCancel={onClose}
      onOk={async () => {
        if (!detail) return;
        const v = await form.validateFields();
        setSaving(true);
        try {
          await opportunityApi.update(detail.id, {
            sourceChannel: v.sourceChannel,
            firstContactDate: v.firstContactDate.format('YYYY-MM-DD'),
            demandSummary: v.demandSummary?.trim(),
            attachments: files.map((f) =>
              f.id
                ? { id: f.id }
                : { fileKey: f.fileKey, fileName: f.fileName },
            ),
          });
          message.success('已保存');
          onDone();
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setSaving(false);
        }
      }}
    >
      <Form<EditState> form={form} layout="vertical" style={{ marginTop: 16 }}>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            columnGap: 16,
          }}
        >
          <Form.Item
            name="sourceChannel"
            label="来源渠道"
            rules={[{ required: true, message: '请选择来源渠道' }]}
          >
            <Select options={sourceOptions} />
          </Form.Item>
          <Form.Item
            name="firstContactDate"
            label="首次接触日期"
            rules={[{ required: true, message: '请选择首次接触日期' }]}
            extra="改日期会改变它计入哪一天的统计"
          >
            <DatePicker
              style={{ width: '100%' }}
              allowClear={false}
              disabledDate={(d) => d.isAfter(dayjs(), 'day')}
            />
          </Form.Item>
        </div>
        <Form.Item
          name="demandSummary"
          label="需求摘要"
          rules={[{ max: 2000, message: '不能超过2000个字符' }]}
        >
          <Input.TextArea rows={4} maxLength={2000} showCount />
        </Form.Item>
      </Form>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
        {files.map((f) => (
          <FileChip
            key={f.id ?? f.fileKey}
            fileName={f.fileName}
            contentType={f.contentType}
            meta={fileMeta(
              f.contentType,
              f.fileSize,
              f.id ? '已保存' : undefined,
            )}
            actions={
              <button
                type="button"
                aria-label={`移除 ${f.fileName}`}
                style={iconButton}
                onClick={() => setFiles(files.filter((x) => x !== f))}
              >
                <CloseOutlined />
              </button>
            }
          />
        ))}
        <Upload
          accept={ATTACHMENT_ACCEPT}
          multiple
          showUploadList={false}
          beforeUpload={(file) => {
            upload(file);
            return false;
          }}
        >
          <button
            type="button"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              height: 52,
              padding: '0 16px',
              borderRadius: 10,
              border: `1px dashed ${palette.control}`,
              background: 'none',
              color: palette.link,
              cursor: 'pointer',
            }}
          >
            {uploading > 0 ? <LoadingOutlined /> : <UploadOutlined />}
            {uploading > 0 ? '上传中' : '上传'}
          </button>
        </Upload>
      </div>
      {uploadError && (
        <div
          role="alert"
          style={{ marginTop: 8, fontSize: 12, color: palette.red }}
        >
          {uploadError}
        </div>
      )}
    </Modal>
  );
};

/**
 * 从商机创建客户询盘：客户固定为商机客户，原始内容带入需求摘要；
 * 可选一个商机附件作为解析来源（图片按图片解析、Excel 按表格解析），后端复制为询盘附件。
 */
export const CreateInquiryModal: React.FC<{
  detail: OpportunityDetail;
  /** 当前还没到有效阶段时，创建询盘会自动推进到的阶段 */
  advanceTo?: OpportunityStage;
  open: boolean;
  onClose: () => void;
}> = ({ detail, advanceTo, open, onClose }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [content, setContent] = useState('');
  const [attachmentId, setAttachmentId] = useState<number>(0);
  const [level, setLevel] = useState<number>(LEVEL_DEFAULT);
  const [saving, setSaving] = useState(false);
  const { levelOptions } = useLevels();

  return (
    <Modal
      open={open}
      title="创建客户询盘"
      okText="创建询盘"
      cancelText="取消"
      width={600}
      destroyOnHidden
      confirmLoading={saving}
      afterOpenChange={(o) => {
        if (o) {
          setContent(detail.demandSummary ?? '');
          setAttachmentId(0);
          setLevel(LEVEL_DEFAULT);
        }
      }}
      onCancel={onClose}
      onOk={async () => {
        const att = detail.attachments.find((a) => a.id === attachmentId);
        if (!att && !content.trim()) {
          message.error('请填写询盘内容，或选择一个附件作为解析来源');
          return;
        }
        setSaving(true);
        let attachments: { fileKey: string; fileName: string }[] = [];
        if (att) {
          try {
            const copied = await inquiryApi.copyFromOpportunity(
              detail.id,
              att.id,
            );
            attachments = [
              { fileKey: copied.fileKey, fileName: copied.fileName },
            ];
          } catch (e) {
            message.error(readBizError(e).message);
            setSaving(false);
            return;
          }
        }
        try {
          const created = await inquiryApi.submit({
            customerId: detail.customerId,
            opportunityId: detail.id,
            // 询盘来源与商机来源渠道同一套字典，直接沿用
            source: detail.sourceChannel,
            rawContent: content.trim() || undefined,
            attachments,
            inquiryDate: dayjs().format('YYYY-MM-DD'),
            level,
          });
          message.success('客户询盘已创建，可以 AI 解析或手动录入型号');
          history.push(`/inquiry/customer-inquiries/${created.id}`);
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setSaving(false);
        }
      }}
    >
      <div style={{ color: palette.mute, margin: '8px 0 16px' }}>
        客户：
        <span style={{ color: palette.ink, fontWeight: 600 }}>
          {detail.customerName}
        </span>
        ，创建后询盘会关联这条商机
        {advanceTo
          ? `，商机会自动推进到「${advanceTo.code} ${advanceTo.name}」。`
          : '。'}
      </div>
      <div style={{ marginBottom: 8 }}>
        询盘等级{' '}
        <span style={{ fontSize: 12, color: palette.mute }}>
          S 最优先、C 最低，采购按等级决定先处理哪个询盘
        </span>
      </div>
      <Radio.Group
        optionType="button"
        buttonStyle="solid"
        value={level}
        onChange={(e) => setLevel(e.target.value)}
        options={levelOptions}
        style={{ marginBottom: 16 }}
      />
      <div style={{ marginBottom: 8 }}>询盘内容</div>
      <Input.TextArea
        rows={5}
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder="客户的原始询盘内容，AI 会据此解析型号和数量"
      />
      {detail.attachments.length > 0 && (
        <>
          <div style={{ margin: '16px 0 8px' }}>
            解析来源{' '}
            <span style={{ fontSize: 12, color: palette.mute }}>
              询盘只能带一个附件（单个不超过 5MB）
            </span>
          </div>
          <Radio.Group
            value={attachmentId}
            onChange={(e) => setAttachmentId(e.target.value)}
            style={{ display: 'grid', gap: 8 }}
            options={[
              { value: 0, label: '只用上面的文字' },
              ...detail.attachments.map((a) => ({
                value: a.id,
                label: `${a.fileName}（${a.contentType.startsWith('image/') ? '按图片解析' : '按表格解析'}）`,
              })),
            ]}
          />
        </>
      )}
    </Modal>
  );
};
