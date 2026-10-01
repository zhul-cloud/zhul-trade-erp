import {
  CloseOutlined,
  EyeOutlined,
  LoadingOutlined,
  UploadOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  App,
  Button,
  DatePicker,
  Drawer,
  Form,
  Input,
  Select,
  Upload,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useRef, useState } from 'react';
import { useCountries } from '@/pages/product/components/useCountries';
import { useAppTheme } from '@/theme/AppTheme';
import { FileChip, fileMeta, iconButton } from './components';
import {
  ATTACHMENT_ACCEPT,
  ATTACHMENT_MAX_BYTES,
  CHANNEL_OPTIONS,
  MAX_ATTACHMENTS,
} from './constants';
import { opportunityApi, readBizError, type UploadedFile } from './service';

interface FormState {
  contactName: string;
  country: string;
  sourceChannel: number;
  firstContactDate: Dayjs;
  customerName?: string;
  email?: string;
  whatsapp?: string;
  phone?: string;
  website?: string;
  demandSummary?: string;
}

type LocalFile = UploadedFile & { localUrl: string };

interface Duplicate {
  message: string;
  existingId?: number;
  selectable?: boolean;
}

/** 查重命中项 → 表单字段 */
const MATCHED_FIELD: Record<string, keyof FormState> = {
  name: 'customerName',
  email: 'email',
  whatsapp: 'whatsapp',
  phone: 'phone',
};

const isAllowed = (f: File) => /\.(jpe?g|png|xlsx|xls|csv)$/i.test(f.name);

const RegisterDrawer: React.FC<{
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
  ownerName?: string;
}> = ({ open, onClose, onSaved, ownerName }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [form] = Form.useForm<FormState>();
  const { options: countryOptions } = useCountries();
  const [files, setFiles] = useState<LocalFile[]>([]);
  const [uploading, setUploading] = useState(0);
  const [uploadError, setUploadError] = useState('');
  const [duplicate, setDuplicate] = useState<Duplicate | null>(null);
  const [saving, setSaving] = useState(false);
  const latest = useRef(files);
  const alertRef = useRef<HTMLDivElement>(null);
  latest.current = files;

  const releaseFiles = () => {
    for (const f of latest.current) window.URL.revokeObjectURL(f.localUrl);
    setFiles([]);
  };

  const reset = (keep?: Partial<FormState>) => {
    form.resetFields();
    form.setFieldsValue({ firstContactDate: dayjs(), ...keep });
    releaseFiles();
    setDuplicate(null);
    setUploadError('');
  };

  // 命中查重时提示在抽屉顶部，滚过去让用户看到
  useEffect(() => {
    if (duplicate)
      alertRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [duplicate]);

  useEffect(() => {
    if (open) reset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const upload = async (file: File) => {
    setUploadError('');
    if (!isAllowed(file)) {
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

  const remove = (f: LocalFile) => {
    window.URL.revokeObjectURL(f.localUrl);
    setFiles(files.filter((x) => x.fileKey !== f.fileKey));
  };

  const save = async (continueNext: boolean) => {
    let v: FormState;
    try {
      v = await form.validateFields();
    } catch {
      return;
    }
    setSaving(true);
    setDuplicate(null);
    try {
      await opportunityApi.register({
        contactName: v.contactName.trim(),
        country: v.country,
        sourceChannel: v.sourceChannel,
        firstContactDate: v.firstContactDate.format('YYYY-MM-DD'),
        customerName: v.customerName?.trim(),
        email: v.email?.trim(),
        whatsapp: v.whatsapp?.trim(),
        phone: v.phone?.trim(),
        website: v.website?.trim(),
        demandSummary: v.demandSummary?.trim(),
        attachments: files.map((f) => ({
          fileKey: f.fileKey,
          fileName: f.fileName,
        })),
      });
      message.success('商机已登记');
      onSaved();
      if (continueNext) {
        // 连续录入：保留渠道和首次接触日期
        reset({
          sourceChannel: v.sourceChannel,
          firstContactDate: v.firstContactDate,
        });
      } else {
        onClose();
      }
    } catch (e) {
      const err = readBizError(e);
      if (err.errorCode === 'CUSTOMER_DUPLICATE') {
        setDuplicate({
          message: err.message,
          existingId: err.detail?.existingId as number | undefined,
          selectable: err.detail?.selectable as boolean | undefined,
        });
        const field = MATCHED_FIELD[String(err.detail?.matchedBy)];
        if (field) {
          form.setFields([{ name: field, errors: ['与已有客户相同'] }]);
        }
      } else {
        message.error(err.message);
      }
    } finally {
      setSaving(false);
    }
  };

  const group = (title: string, hint?: string) => (
    <div
      style={{
        display: 'flex',
        alignItems: 'baseline',
        gap: 8,
        margin: '4px 0 14px',
      }}
    >
      <span style={{ fontSize: 14, fontWeight: 700, color: palette.ink }}>
        {title}
      </span>
      {hint && (
        <span style={{ fontSize: 12, color: palette.mute }}>{hint}</span>
      )}
    </div>
  );
  const two: React.CSSProperties = {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    columnGap: 16,
  };

  return (
    <Drawer
      open={open}
      title="登记商机"
      size={560}
      destroyOnHidden
      onClose={onClose}
      footer={
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span style={{ flex: 1, fontSize: 12, color: palette.mute }}>
            负责人：{ownerName ? `${ownerName}（登记人）` : '登记人'}
          </span>
          <Button onClick={onClose}>取消</Button>
          <Button loading={saving} onClick={() => save(true)}>
            保存并继续登记
          </Button>
          <Button type="primary" loading={saving} onClick={() => save(false)}>
            保存
          </Button>
        </div>
      }
    >
      {duplicate && (
        <div
          ref={alertRef}
          role="alert"
          style={{
            display: 'flex',
            gap: 10,
            padding: 14,
            marginBottom: 20,
            borderRadius: 12,
            background: palette.orangeSoft,
            border: `1px solid ${palette.orange}55`,
          }}
        >
          <WarningOutlined
            style={{ color: palette.orange, fontSize: 18, marginTop: 2 }}
          />
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 600, color: palette.orange }}>
              该客户已存在，不用重复登记
            </div>
            <div
              style={{ margin: '6px 0', color: palette.sub, lineHeight: 1.6 }}
            >
              {duplicate.message}
            </div>
            {duplicate.existingId && (
              <div style={{ display: 'flex', gap: 16 }}>
                <a
                  onClick={() =>
                    history.push(
                      `/inquiry/customer-inquiries?newForCustomer=${duplicate.existingId}`,
                    )
                  }
                >
                  为该客户新建询盘
                </a>
                {duplicate.selectable && (
                  <a
                    onClick={() =>
                      history.push(`/customer/list/${duplicate.existingId}`)
                    }
                  >
                    查看客户
                  </a>
                )}
              </div>
            )}
          </div>
        </div>
      )}
      <Form<FormState>
        form={form}
        layout="vertical"
        requiredMark
        onValuesChange={() => duplicate && setDuplicate(null)}
      >
        {group('基本信息', '必填 4 项，其余可以之后补')}
        <div style={two}>
          <Form.Item
            name="contactName"
            label="联系人名称"
            rules={[
              { required: true, whitespace: true, message: '请填写联系人名称' },
              { max: 100, message: '联系人名称不能超过100个字符' },
            ]}
          >
            <Input placeholder="如 John" maxLength={100} autoFocus />
          </Form.Item>
          <Form.Item
            name="country"
            label="国家/地区"
            rules={[{ required: true, message: '请选择国家/地区' }]}
          >
            <Select
              showSearch={{ optionFilterProp: 'label' }}
              placeholder="选择或搜索"
              options={countryOptions}
            />
          </Form.Item>
          <Form.Item
            name="sourceChannel"
            label="来源渠道"
            rules={[{ required: true, message: '请选择来源渠道' }]}
          >
            <Select placeholder="请选择" options={CHANNEL_OPTIONS} />
          </Form.Item>
          <Form.Item
            name="firstContactDate"
            label="首次接触日期"
            extra="补录请改成实际接触的那天，统计按这一天算"
            rules={[{ required: true, message: '请选择首次接触日期' }]}
          >
            <DatePicker
              style={{ width: '100%' }}
              allowClear={false}
              disabledDate={(d) => d.isAfter(dayjs(), 'day')}
            />
          </Form.Item>
        </div>

        {group('联系方式', '用来查重，建议至少填一项')}
        <div style={two}>
          <Form.Item
            name="whatsapp"
            label="WhatsApp"
            rules={[{ max: 30, message: '不能超过30个字符' }]}
          >
            <Input placeholder="+49 151 2345 6789" maxLength={30} />
          </Form.Item>
          <Form.Item
            name="phone"
            label="联系电话"
            rules={[{ max: 32, message: '不能超过32个字符' }]}
          >
            <Input placeholder="选填" maxLength={32} />
          </Form.Item>
        </div>
        <Form.Item
          name="email"
          label="邮箱"
          rules={[
            { type: 'email', message: '请输入正确的邮箱格式' },
            { max: 100, message: '邮箱不能超过100个字符' },
          ]}
        >
          <Input placeholder="选填" maxLength={100} />
        </Form.Item>

        {group('公司', '没有公司名可以先空着')}
        <div style={two}>
          <Form.Item
            name="customerName"
            label="客户名称"
            rules={[
              { max: 200, message: '不能超过200个字符' },
              {
                pattern: /^[^一-龥]*$/,
                message: '客户名称请使用英文（单据会用到）',
              },
            ]}
          >
            <Input placeholder="英文公司全称，选填" maxLength={200} />
          </Form.Item>
          <Form.Item
            name="website"
            label="官网"
            rules={[{ max: 200, message: '不能超过200个字符' }]}
          >
            <Input placeholder="https://" maxLength={200} />
          </Form.Item>
        </div>

        {group('需求')}
        <Form.Item
          name="demandSummary"
          label="需求摘要"
          rules={[{ max: 2000, message: '不能超过2000个字符' }]}
        >
          <Input.TextArea
            rows={3}
            maxLength={2000}
            showCount
            placeholder="如 Siemens S7-1200 CPU 1214C，约 50 台，要求 2 周内到货"
          />
        </Form.Item>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
          {files.map((f) => (
            <FileChip
              key={f.fileKey}
              fileName={f.fileName}
              contentType={f.contentType}
              meta={fileMeta(f.contentType, f.fileSize)}
              actions={
                <>
                  <button
                    type="button"
                    aria-label={`预览 ${f.fileName}`}
                    style={iconButton}
                    onClick={() =>
                      window.open(f.localUrl, '_blank', 'noopener')
                    }
                  >
                    <EyeOutlined />
                  </button>
                  <button
                    type="button"
                    aria-label={`移除 ${f.fileName}`}
                    style={iconButton}
                    onClick={() => remove(f)}
                  >
                    <CloseOutlined />
                  </button>
                </>
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
                fontWeight: 500,
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
        <div style={{ marginTop: 8, fontSize: 12, color: palette.mute }}>
          支持图片（JPG、PNG）和 Excel，单个不超过 10MB，最多 {MAX_ATTACHMENTS}{' '}
          个
        </div>
      </Form>
    </Drawer>
  );
};

export default RegisterDrawer;
