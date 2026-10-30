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
import React, { useEffect, useRef, useState } from 'react';
import CustomerQuickCreateModal from '@/components/CustomerQuickCreateModal';
import { formatSize, Pill } from '@/pages/crm/opportunity/components';
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
  type OpportunityOption,
  readBizError,
  type UploadedFile,
} from '../shared/service';

/** 附件：fromOpp 为从关联商机带入的，换商机或不关联时一并换掉 */
type PickedFile = UploadedFile & { fromOpp?: boolean };

/** 不关联商机 */
const NO_OPP = 0;

interface Values {
  customerId?: number;
  source?: number;
  inquiryDate: Dayjs;
  quoteDeadline: Dayjs;
  urgent: boolean;
  level: number;
  rawContent?: string;
  remark?: string;
}

const fileIcon = (name: string) =>
  /\.(xlsx?|csv)$/i.test(name) ? (
    <FileExcelOutlined />
  ) : /\.pdf$/i.test(name) ? (
    <FilePdfOutlined />
  ) : (
    <FileImageOutlined />
  );

/** 新建询盘单：粘贴原文或拖入附件，提交后可以马上开始 AI 解析或手动录入 */
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
  const [files, setFiles] = useState<PickedFile[]>([]);
  const [uploading, setUploading] = useState(0);
  const [saving, setSaving] = useState(false);
  const [quickOpen, setQuickOpen] = useState(false);
  /** 客户进行中的商机；undefined 为还没选客户 */
  const [opps, setOpps] = useState<OpportunityOption[]>();
  const [oppId, setOppId] = useState<number>(NO_OPP);
  /** 从商机带入询盘内容的文字：内容没被改过时换商机跟着换 */
  const [brought, setBrought] = useState<string>();
  /** 询盘内容里已有自己写的文字时，商机的原始内容不覆盖，提示追加 */
  const [appendable, setAppendable] = useState<string>();
  const broughtRef = useRef<string | undefined>(undefined);
  /** 换商机时作废还在复制的附件 */
  const linkSeq = useRef(0);
  const ownFiles = useRef(0);
  ownFiles.current = files.filter((f) => !f.fromOpp).length;
  const bring = (text: string | undefined) => {
    broughtRef.current = text;
    setBrought(text);
  };
  const rawContent = Form.useWatch('rawContent', form);

  const linkOpportunity = (opp: OpportunityOption | undefined) => {
    const seq = ++linkSeq.current;
    setOppId(opp?.id ?? NO_OPP);
    const summary = opp?.demandSummary?.trim() ?? '';
    const current =
      (form.getFieldValue('rawContent') as string | undefined)?.trim() ?? '';
    if (!current || current === broughtRef.current) {
      form.setFieldValue('rawContent', summary || undefined);
      bring(summary || undefined);
      setAppendable(undefined);
    } else {
      bring(undefined);
      setAppendable(
        summary && !current.includes(summary) ? summary : undefined,
      );
    }
    setFiles((prev) => prev.filter((f) => !f.fromOpp));
    if (!opp) return;
    const take = opp.attachments.slice(
      0,
      Math.max(0, MAX_ATTACHMENTS - ownFiles.current),
    );
    if (take.length < opp.attachments.length) {
      message.warning(
        `附件最多 ${MAX_ATTACHMENTS} 个，商机里的 ${opp.attachments.length - take.length} 个附件没有带入`,
      );
    }
    for (const a of take) {
      setUploading((n) => n + 1);
      inquiryApi
        .copyFromOpportunity(opp.id, a.id)
        .then((f) => {
          if (seq === linkSeq.current)
            setFiles((prev) => [...prev, { ...f, fromOpp: true }]);
        })
        .catch((e) =>
          message.warning(
            `「${a.fileName}」没能带入：${readBizError(e).message}`,
          ),
        )
        .finally(() => setUploading((n) => n - 1));
    }
  };

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
    setOpps(undefined);
    linkOpportunity(undefined);
    if (!id) return;
    // 默认关联这个客户最近更新的进行中商机，带入原始内容和全部附件；可以换或不关联
    inquiryApi
      .opportunityOptions(id)
      .then((list) => {
        if (form.getFieldValue('customerId') !== id) return;
        setOpps(list);
        if (list.length > 0) linkOpportunity(list[0]);
      })
      .catch(() => setOpps([]));
    const t = await inquiryApi.customerType(id).catch(() => null);
    // 来源默认取客户档案的来源渠道，可以再改
    if (t?.sourceChannel) form.setFieldValue('source', t.sourceChannel);
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
    setOpps(undefined);
    setOppId(NO_OPP);
    bring(undefined);
    setAppendable(undefined);
    linkSeq.current++;
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
        remark: v.remark?.trim() || undefined,
        opportunityId: oppId || undefined,
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
        title="新建询盘单"
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
          {opps && (
            <Form.Item
              label="关联商机"
              extra={
                opps.length === 0
                  ? '这个客户没有进行中的商机，询盘照常创建'
                  : `已自动选中这个客户最近一条进行中的商机，可以换一条或选「不关联」${oppId ? '；创建后商机推进到「有效商机」' : ''}`
              }
            >
              <Select
                value={opps.length === 0 ? undefined : oppId}
                disabled={opps.length === 0}
                placeholder="没有进行中的商机"
                onChange={(v: number) =>
                  linkOpportunity(opps.find((o) => o.id === v))
                }
                options={[
                  ...opps.map((o) => ({
                    value: o.id,
                    label: (
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 8,
                        }}
                      >
                        <span style={{ color: palette.link }}>
                          {o.opportunityCode}
                        </span>
                        <Pill tone="violet">{o.stageName}</Pill>
                        <span style={{ color: palette.sub }}>
                          {[o.firstContactDate, o.demandSummary]
                            .filter(Boolean)
                            .join(' · ')
                            .slice(0, 60)}
                        </span>
                      </span>
                    ),
                  })),
                  { value: NO_OPP, label: '不关联商机' },
                ]}
              />
            </Form.Item>
          )}
          <Form.Item
            name="rawContent"
            label={
              <span style={{ display: 'inline-flex', gap: 8 }}>
                询盘内容
                {brought && rawContent?.trim() === brought && (
                  <Pill tone="cyan">已从商机带入</Pill>
                )}
              </span>
            }
            extra={
              appendable && (
                <a
                  onClick={() => {
                    form.setFieldValue(
                      'rawContent',
                      `${(form.getFieldValue('rawContent') as string).trimEnd()}\n\n${appendable}`,
                    );
                    setAppendable(undefined);
                  }}
                >
                  商机里有原始内容，点此追加
                </a>
              )
            }
          >
            <Input.TextArea
              rows={6}
              maxLength={20000}
              placeholder="把客户发来的文字粘贴到这里"
            />
          </Form.Item>
          <Form.Item
            name="remark"
            label="备注"
            rules={[{ max: 500, message: '备注不能超过 500 字' }]}
          >
            <Input.TextArea
              rows={2}
              maxLength={500}
              showCount
              placeholder="选填，如 客户要求原装进口"
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
          {files.some((f) => f.fromOpp) && (
            <div style={{ marginTop: 12 }}>
              <Pill tone="cyan">
                {files.filter((f) => f.fromOpp).length} 个已从商机带入
              </Pill>
            </div>
          )}
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
