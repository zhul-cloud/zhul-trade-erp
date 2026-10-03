import {
  CloseOutlined,
  CloudUploadOutlined,
  FileExcelOutlined,
  FileImageOutlined,
  FilePdfOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import {
  App,
  Button,
  DatePicker,
  Form,
  Input,
  Modal,
  Radio,
  Select,
  Switch,
  Upload,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import CustomerQuickCreateModal from '@/components/CustomerQuickCreateModal';
import { formatSize } from '@/pages/crm/opportunity/components';
import { getCustomer, searchCustomers } from '@/services/zhul/masterdata';
import { useAppTheme } from '@/theme/AppTheme';
import { DICT_SOURCE_CHANNEL, useDictOptions } from '@/utils/dict';
import { useLevels } from '../shared/components';
import {
  ATTACHMENT_ACCEPT,
  ATTACHMENT_MAX_BYTES,
  CUSTOMER_RETURNING,
  LEVEL_DEFAULT,
  MAX_ATTACHMENTS,
} from '../shared/constants';
import {
  type CustomerInquiry,
  inquiryApi,
  readBizError,
  type UploadedFile,
} from '../shared/service';

interface Values {
  customerId?: number;
  source?: number;
  inquiryDate: Dayjs;
  quoteDeadline: Dayjs;
  urgent: boolean;
  level: number;
  rawContent?: string;
}

const fileIcon = (name: string) =>
  /\.(xlsx?|csv)$/i.test(name) ? (
    <FileExcelOutlined />
  ) : /\.pdf$/i.test(name) ? (
    <FilePdfOutlined />
  ) : (
    <FileImageOutlined />
  );

/** 新建客户询盘：粘贴原文或拖入附件，提交后可以马上开始 AI 解析或手动录入 */
const NewInquiryModal: React.FC<{
  open: boolean;
  /** 预选的客户（如从商机跳转来） */
  presetCustomerId?: number;
  onClose: () => void;
  onCreated: (inquiry: CustomerInquiry, startParse: boolean) => void;
}> = ({ open, presetCustomerId, onClose, onCreated }) => {
  const { levelOptions } = useLevels();
  const { options: sourceOptions } = useDictOptions(DICT_SOURCE_CHANNEL);
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [form] = Form.useForm<Values>();
  const [customers, setCustomers] = useState<
    { value: number; label: string }[]
  >([]);
  const [typeHint, setTypeHint] = useState<{
    type: number;
    wonCount: number;
    lastWonDate?: string;
  } | null>(null);
  const [files, setFiles] = useState<UploadedFile[]>([]);
  const [uploading, setUploading] = useState(0);
  const [saving, setSaving] = useState(false);
  const [quickOpen, setQuickOpen] = useState(false);

  const search = async (keyword?: string) => {
    const list = await searchCustomers(keyword);
    setCustomers(
      list.map((c) => ({
        label: `${c.displayName || c.name}${c.country ? ` · ${c.country}` : ''}`,
        value: c.id,
      })),
    );
  };

  const pickCustomer = async (id?: number) => {
    setTypeHint(null);
    if (!id) return;
    const t = await inquiryApi.customerType(id).catch(() => null);
    if (t)
      setTypeHint({
        type: t.customerType,
        wonCount: t.wonCount,
        lastWonDate: t.lastWonDate,
      });
  };

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    setFiles([]);
    setTypeHint(null);
    search().catch(() => undefined);
    if (presetCustomerId) {
      getCustomer(presetCustomerId)
        .then((c) => {
          setCustomers((prev) => [
            { label: c.displayName || c.name, value: c.id },
            ...prev.filter((p) => p.value !== c.id),
          ]);
          form.setFieldValue('customerId', c.id);
          pickCustomer(c.id);
        })
        .catch(() => undefined);
    }
  }, [open]);

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

  const submit = async (startParse: boolean) => {
    const v = await form.validateFields();
    if (!v.rawContent?.trim() && files.length === 0) {
      form.setFields([
        { name: 'rawContent', errors: ['请粘贴询盘内容或上传附件'] },
      ]);
      return;
    }
    setSaving(true);
    try {
      const created = await inquiryApi.submit({
        customerId: v.customerId as number,
        source: v.source as number,
        inquiryDate: v.inquiryDate.format('YYYY-MM-DD'),
        quoteDeadline: v.quoteDeadline.format('YYYY-MM-DD'),
        urgent: v.urgent,
        level: v.level,
        rawContent: v.rawContent?.trim() || undefined,
        attachments: files.map((f) => ({
          fileKey: f.fileKey,
          fileName: f.fileName,
        })),
      });
      onCreated(created, startParse);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <Modal
        open={open}
        title="新建客户询盘"
        width={720}
        destroyOnHidden
        onCancel={onClose}
        footer={
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span
              style={{ color: palette.mute, fontSize: 12, marginRight: 'auto' }}
            >
              Ctrl + 回车提交 · Esc 关闭
            </span>
            <Button onClick={onClose}>取消</Button>
            <Button
              onClick={() => submit(false)}
              loading={saving}
              disabled={uploading > 0}
            >
              先保存
            </Button>
            <Button
              type="primary"
              onClick={() => submit(true)}
              loading={saving}
              disabled={uploading > 0}
            >
              提交并开始 AI 解析
            </Button>
          </div>
        }
      >
        <div style={{ color: palette.mute, marginBottom: 16 }}>
          粘贴原文或拖入附件，提交后由 AI
          识别型号、品牌和数量；型号很少时也可以先保存、再手动录入。
        </div>
        <Form
          form={form}
          layout="vertical"
          requiredMark
          initialValues={{
            inquiryDate: dayjs(),
            quoteDeadline: dayjs(),
            urgent: false,
            level: LEVEL_DEFAULT,
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) submit(true);
          }}
        >
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '1fr 200px',
              gap: 16,
            }}
          >
            <Form.Item
              name="customerId"
              label="客户"
              rules={[{ required: true, message: '请选择客户' }]}
              extra={
                typeHint && (
                  <span
                    style={{
                      color:
                        typeHint.type === CUSTOMER_RETURNING
                          ? palette.violet
                          : palette.cyan,
                    }}
                  >
                    {typeHint.type === CUSTOMER_RETURNING
                      ? `老客户 · 以前成交过 ${typeHint.wonCount} 次询盘${typeHint.lastWonDate ? `，最近一次 ${typeHint.lastWonDate}` : ''}`
                      : '新客户 · 还没有成交过的询盘'}
                  </span>
                )
              }
            >
              <Select
                showSearch={{
                  filterOption: false,
                  onSearch: (kw) => search(kw).catch(() => undefined),
                }}
                placeholder="搜索客户名称或联系人"
                options={customers}
                onChange={(id) => pickCustomer(id)}
                popupRender={(menu) => (
                  <>
                    {menu}
                    <Button
                      type="link"
                      icon={<PlusOutlined />}
                      onClick={() => setQuickOpen(true)}
                      style={{ width: '100%' }}
                    >
                      新建客户
                    </Button>
                  </>
                )}
              />
            </Form.Item>
            <Form.Item
              name="source"
              label="来源"
              rules={[{ required: true, message: '请选择来源' }]}
            >
              <Select placeholder="请选择" options={sourceOptions} />
            </Form.Item>
          </div>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '180px 180px 1fr',
              gap: 16,
            }}
          >
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
              extra="默认当天"
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
            <Form.Item
              name="urgent"
              label="紧急"
              valuePropName="checked"
              extra="产线停机等件时打开"
            >
              <Switch />
            </Form.Item>
          </div>
          <Form.Item
            name="level"
            label="询盘等级"
            rules={[{ required: true, message: '请选择询盘等级' }]}
            extra="S 最优先、C 最低；采购按等级决定先处理哪个询盘，之后可在详情里调整"
          >
            <Radio.Group
              optionType="button"
              buttonStyle="solid"
              options={levelOptions}
            />
          </Form.Item>
          <Form.Item name="rawContent" label="询盘内容">
            <Input.TextArea
              rows={6}
              maxLength={20000}
              placeholder="把客户发来的文字粘贴到这里"
            />
          </Form.Item>
          <Upload.Dragger
            multiple
            accept={ATTACHMENT_ACCEPT}
            showUploadList={false}
            beforeUpload={beforeUpload}
          >
            <p style={{ fontSize: 24, color: palette.link, margin: 0 }}>
              <CloudUploadOutlined />
            </p>
            <p style={{ margin: '4px 0 0', color: palette.sub }}>
              {uploading > 0
                ? `正在上传 ${uploading} 个文件…`
                : '把图片、Excel 或 PDF 拖到这里，或点击选择'}
            </p>
            <p style={{ margin: 0, color: palette.mute, fontSize: 12 }}>
              支持 JPG、PNG、XLS、XLSX、CSV、PDF，单个不超过 10MB
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
                    padding: '8px 12px',
                    borderRadius: 10,
                    background: palette.hover,
                  }}
                >
                  <span style={{ color: palette.link }}>
                    {fileIcon(f.fileName)}
                  </span>
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
        </Form>
      </Modal>
      <CustomerQuickCreateModal
        open={quickOpen}
        onOpenChange={setQuickOpen}
        onCreated={(c) => {
          setCustomers((prev) => [
            { label: c.displayName || c.name, value: c.id },
            ...prev,
          ]);
          form.setFieldValue('customerId', c.id);
          pickCustomer(c.id);
        }}
      />
    </>
  );
};

export default NewInquiryModal;
