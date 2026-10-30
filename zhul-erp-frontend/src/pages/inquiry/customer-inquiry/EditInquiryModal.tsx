import { CloseOutlined, CloudUploadOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  DatePicker,
  Form,
  Input,
  Modal,
  Select,
  Switch,
  Upload,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import { formatSize, Pill } from '@/pages/crm/opportunity/components';
import { useAppTheme } from '@/theme/AppTheme';
import { DICT_SOURCE_CHANNEL, useDictOptions } from '@/utils/dict';
import {
  ATTACHMENT_ACCEPT,
  ATTACHMENT_MAX_BYTES,
  MAX_ATTACHMENTS,
} from '../shared/constants';
import {
  type InquiryAttachment,
  type InquiryDetail,
  inquiryApi,
  type OpportunityOption,
  readBizError,
  type UploadedFile,
} from '../shared/service';
import { fileIcon } from './NewInquiryModal';

/** 不关联商机 */
const NO_OPP = 0;

interface Values {
  source: number;
  inquiryDate: Dayjs;
  quoteDeadline: Dayjs;
  urgent: boolean;
  opportunityId: number;
  remark?: string;
}

/** 修改询盘（原型 10-30询盘修改 F2）：客户、询盘内容与型号不在这里改 */
const EditInquiryModal: React.FC<{
  detail: InquiryDetail;
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}> = ({ detail, open, onClose, onSaved }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [form] = Form.useForm<Values>();
  const { options: sourceOptions } = useDictOptions(DICT_SOURCE_CHANNEL);
  const inq = detail.inquiry;
  const [kept, setKept] = useState<InquiryAttachment[]>([]);
  const [added, setAdded] = useState<UploadedFile[]>([]);
  const [uploading, setUploading] = useState(0);
  const [saving, setSaving] = useState(false);
  const [opps, setOpps] = useState<OpportunityOption[]>([]);

  useEffect(() => {
    if (!open) return;
    setKept(detail.attachments);
    setAdded([]);
    form.setFieldsValue({
      source: inq.source,
      inquiryDate: dayjs(inq.inquiryDate),
      quoteDeadline: dayjs(inq.quoteDeadline),
      urgent: inq.urgent,
      opportunityId: inq.opportunityId || NO_OPP,
      remark: detail.remark,
    });
    inquiryApi
      .opportunityOptions(inq.customerId)
      .then(setOpps)
      .catch(() => setOpps([]));
  }, [open, detail, inq, form]);

  const beforeUpload = (file: File) => {
    if (kept.length + added.length + uploading >= MAX_ATTACHMENTS) {
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
      .then((f) => setAdded((prev) => [...prev, f]))
      .catch((e) => message.error(`「${file.name}」${readBizError(e).message}`))
      .finally(() => setUploading((n) => n - 1));
    return Upload.LIST_IGNORE;
  };

  const save = async () => {
    const v = await form.validateFields();
    setSaving(true);
    try {
      await inquiryApi.update(inq.id, {
        source: v.source,
        inquiryDate: v.inquiryDate.format('YYYY-MM-DD'),
        quoteDeadline: v.quoteDeadline.format('YYYY-MM-DD'),
        urgent: v.urgent,
        opportunityId: v.opportunityId || undefined,
        keepAttachmentIds: kept.map((a) => a.id),
        attachments: added.map((f) => ({
          fileKey: f.fileKey,
          fileName: f.fileName,
        })),
        remark: v.remark?.trim() || undefined,
      });
      message.success('询盘已修改');
      onSaved();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  // 已关联的商机可能已经结束、不在可选列表里，仍然显示出来
  const oppOptions = [
    ...(inq.opportunityId && !opps.some((o) => o.id === inq.opportunityId)
      ? [
          {
            value: inq.opportunityId,
            label: detail.opportunityCode ?? `商机 ${inq.opportunityId}`,
          },
        ]
      : []),
    ...opps.map((o) => ({
      value: o.id,
      label: (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <span style={{ color: palette.link }}>{o.opportunityCode}</span>
          <Pill tone="violet">{o.stageName}</Pill>
        </span>
      ),
    })),
    { value: NO_OPP, label: '不关联商机' },
  ];

  const fileRow = (
    key: string,
    name: string,
    size: number,
    fresh: boolean,
    remove: () => void,
  ) => (
    <div
      key={key}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '8px 12px',
        borderRadius: 10,
        background: palette.hover,
      }}
    >
      <span style={{ color: palette.link }}>{fileIcon(name)}</span>
      <span style={{ color: palette.ink }}>{name}</span>
      <span style={{ color: palette.mute, fontSize: 12 }}>
        {formatSize(size)}
      </span>
      {fresh && <Pill tone="green">新上传</Pill>}
      <Button
        type="text"
        size="small"
        aria-label={`删除 ${name}`}
        icon={<CloseOutlined />}
        style={{ marginLeft: 'auto' }}
        onClick={remove}
      />
    </div>
  );

  return (
    <Modal
      open={open}
      title="修改询盘"
      width={600}
      destroyOnHidden
      onCancel={onClose}
      footer={
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span
            style={{ color: palette.mute, fontSize: 12, marginRight: 'auto' }}
          >
            询盘内容与型号不在这里改
          </span>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            onClick={save}
            loading={saving}
            disabled={uploading > 0}
          >
            保存
          </Button>
        </div>
      }
    >
      <Form form={form} layout="vertical" requiredMark>
        <Form.Item
          label="客户"
          extra="客户不能改；选错了客户，取消这条询盘后重新新建。"
        >
          <Input value={inq.customerName} disabled />
        </Form.Item>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            gap: 16,
          }}
        >
          <Form.Item
            name="source"
            label="来源"
            rules={[{ required: true, message: '请选择来源' }]}
          >
            <Select placeholder="请选择" options={sourceOptions} />
          </Form.Item>
          <Form.Item
            name="inquiryDate"
            label="询盘日期"
            rules={[{ required: true, message: '请选择询盘日期' }]}
          >
            <DatePicker style={{ width: '100%' }} allowClear={false} />
          </Form.Item>
          <Form.Item
            name="quoteDeadline"
            label="报价截止"
            dependencies={['inquiryDate']}
            extra="改了之后采购待办的截止时间一起更新"
            rules={[
              { required: true, message: '请选择报价截止' },
              ({ getFieldValue }) => ({
                validator: (_, v: Dayjs) =>
                  v &&
                  getFieldValue('inquiryDate') &&
                  v.isBefore(getFieldValue('inquiryDate'), 'day')
                    ? Promise.reject(new Error('报价截止不能早于询盘日期'))
                    : Promise.resolve(),
              }),
            ]}
          >
            <DatePicker style={{ width: '100%' }} allowClear={false} />
          </Form.Item>
          <Form.Item name="urgent" label="紧急" valuePropName="checked">
            <Switch />
          </Form.Item>
        </div>
        <Form.Item name="opportunityId" label="关联商机">
          <Select options={oppOptions} />
        </Form.Item>
        <Form.Item label="附件" style={{ marginBottom: 12 }}>
          <div style={{ display: 'grid', gap: 8 }}>
            {kept.map((a) =>
              fileRow(`k${a.id}`, a.fileName, a.fileSize, false, () =>
                setKept((prev) => prev.filter((p) => p.id !== a.id)),
              ),
            )}
            {added.map((f) =>
              fileRow(f.fileKey, f.fileName, f.fileSize, true, () =>
                setAdded((prev) => prev.filter((p) => p.fileKey !== f.fileKey)),
              ),
            )}
            <Upload.Dragger
              multiple
              accept={ATTACHMENT_ACCEPT}
              showUploadList={false}
              beforeUpload={beforeUpload}
            >
              <span style={{ color: palette.sub }}>
                <CloudUploadOutlined style={{ color: palette.link }} />{' '}
                {uploading > 0
                  ? `正在上传 ${uploading} 个文件…`
                  : `上传附件（最多 ${MAX_ATTACHMENTS} 个，单个不超过 10MB，可直接粘贴）`}
              </span>
            </Upload.Dragger>
            <Alert
              type="warning"
              showIcon
              title="补传的附件只作为资料保存，不会自动解析。要从新附件里识别出新的型号，用详情页的「追加型号」。"
            />
          </div>
        </Form.Item>
        <Form.Item
          name="remark"
          label="备注"
          style={{ marginBottom: 0 }}
          rules={[{ max: 500, message: '备注不能超过 500 字' }]}
        >
          <Input.TextArea rows={2} maxLength={500} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default EditInquiryModal;
