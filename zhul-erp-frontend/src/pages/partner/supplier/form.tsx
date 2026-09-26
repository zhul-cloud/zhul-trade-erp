import {
  AppstoreOutlined,
  BankOutlined,
  InfoCircleOutlined,
  LockOutlined,
  PhoneOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import { history, useAccess, useParams } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  Cascader,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Radio,
  Select,
  Skeleton,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { LIST_PATH, PageTitle, SectionCard } from './components';
import {
  BANK_ACCOUNT_PATTERN,
  CREDIT_CODE_PATTERN,
  INDUSTRY_OPTIONS,
  SUPPLIER_CODE_PATTERN,
  SUPPLIER_TYPE_OPTIONS,
} from './constants';
import {
  ProductScopeEditor,
  type ScopeRow,
  toScopePayload,
  toScopeRows,
} from './productScope';
import {
  loadRegions,
  type RegionNode,
  readBizError,
  type SupplierFormValues,
  type SupplierItem,
  supplierApi,
} from './service';

/** 表单内部值：日期、地区、注册资本的控件值与接口格式不同 */
type FormState = Omit<
  SupplierFormValues,
  'establishedDate' | 'region' | 'registeredCapital' | 'productScopes'
> & {
  productScopes?: ScopeRow[];
  establishedDate?: Dayjs | null;
  region?: string[];
  registeredCapital?: string | null;
};

const FIELD_LABELS: Record<string, string> = {
  supplierCode: '供应商编码',
  name: '供应商名称',
  shortName: '供应商简称',
  supplierType: '供应商类型',
  status: '状态',
  creditCode: '统一社会信用代码',
  legalRepresentative: '法人代表',
  registeredCapital: '注册资本',
  establishedDate: '成立日期',
  contactName: '联系人',
  contactPhone: '联系电话',
  contactEmail: '联系邮箱',
  address: '详细地址',
  bankName: '开户银行',
  bankAccount: '银行账号',
  remark: '备注',
};

const toFormState = (s: SupplierItem): FormState => ({
  supplierCode: s.supplierCode,
  name: s.name,
  shortName: s.shortName,
  // 存量数据的"未设置"（0）不回填，让用户补选一个真实类型
  supplierType: s.supplierType || (undefined as unknown as number),
  industry: s.industry || undefined,
  status: s.status,
  creditCode: s.creditCode,
  legalRepresentative: s.legalRepresentative,
  registeredCapital:
    s.registeredCapital === null || s.registeredCapital === undefined
      ? null
      : Number(s.registeredCapital).toFixed(2),
  establishedDate: s.establishedDate ? dayjs(s.establishedDate) : null,
  contactName: s.contactName,
  contactPhone: s.contactPhone,
  contactEmail: s.contactEmail,
  region: s.region ? s.region.split('/') : undefined,
  address: s.address,
  bankName: s.bankName,
  bankAccount: s.bankAccount,
  remark: s.remark,
  productScopes: toScopeRows(s.productScopes),
});

/** 选填文本清空时传空串（后端据此清空），不传 undefined */
const text = (v?: string) => (v ?? '').trim();

const toPayload = (v: FormState): SupplierFormValues => ({
  supplierCode: text(v.supplierCode),
  name: text(v.name),
  shortName: text(v.shortName),
  supplierType: v.supplierType,
  industry: v.industry,
  status: v.status,
  creditCode: text(v.creditCode).toUpperCase(),
  legalRepresentative: text(v.legalRepresentative),
  registeredCapital: v.registeredCapital || null,
  establishedDate: v.establishedDate
    ? v.establishedDate.format('YYYY-MM-DD')
    : null,
  contactName: text(v.contactName),
  contactPhone: text(v.contactPhone),
  contactEmail: text(v.contactEmail),
  region: v.region?.length ? v.region.join('/') : '',
  address: text(v.address),
  bankName: text(v.bankName),
  bankAccount: text(v.bankAccount),
  remark: text(v.remark),
  productScopes: toScopePayload(v.productScopes),
});

const SupplierFormPage: React.FC = () => {
  const { id } = useParams<{ id?: string }>();
  const editing = !!id;
  const { message, modal } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const { palette } = useAppTheme();
  const [form] = Form.useForm<FormState>();

  const [record, setRecord] = useState<SupplierItem | null>();
  const [loading, setLoading] = useState(editing);
  const [loadError, setLoadError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [regions, setRegions] = useState<RegionNode[]>([]);
  const [errorSummary, setErrorSummary] = useState<
    { name: string; text: string }[]
  >([]);

  const allowed = editing
    ? access['partner:supplier:edit']
    : access['partner:supplier:add'];

  useEffect(() => {
    loadRegions()
      .then(setRegions)
      .catch(() => undefined);
  }, []);

  const loadRecord = async () => {
    if (!id) return;
    setLoading(true);
    setLoadError(undefined);
    try {
      const data = await supplierApi.getForm(Number(id));
      setRecord(data);
      form.setFieldsValue(toFormState(data));
    } catch (e) {
      const err = readBizError(e);
      if (err.message.includes('不存在')) {
        setRecord(null);
      } else {
        setLoadError(err.message);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (allowed) loadRecord();
  }, [id, allowed]);

  const leave = () => history.push(LIST_PATH);

  const cancel = () => {
    if (!form.isFieldsTouched()) {
      leave();
      return;
    }
    modal.confirm({
      title: '确认离开？',
      content: '当前填写内容将不会保存。',
      okText: '离开',
      cancelText: '继续编辑',
      onOk: leave,
    });
  };

  const submit = async (values: FormState) => {
    setErrorSummary([]);
    setSaving(true);
    try {
      const payload = toPayload(values);
      if (editing) {
        await supplierApi.update(Number(id), payload);
        message.success('编辑成功');
      } else {
        await supplierApi.create(payload);
        message.success('新增供应商成功');
      }
      leave();
    } catch (e) {
      const err = readBizError(e);
      if (err.errorCode === 'SUPPLIER_CODE_DUPLICATE') {
        form.setFields([{ name: 'supplierCode', errors: [err.message] }]);
        form.scrollToField('supplierCode', { focus: true });
      } else if (err.errorCode === 'SUPPLIER_CREDIT_CODE_DUPLICATE') {
        form.setFields([{ name: 'creditCode', errors: [err.message] }]);
        form.scrollToField('creditCode', { focus: true });
      } else {
        message.error(err.message);
      }
    } finally {
      setSaving(false);
    }
  };

  const onFinishFailed = ({
    errorFields,
  }: {
    errorFields: { name: (string | number)[]; errors: string[] }[];
  }) => {
    const list = errorFields.map((f) => ({
      name: String(f.name[0]),
      text: `${FIELD_LABELS[String(f.name[0])] ?? f.name[0]}：${f.errors[0]}`,
    }));
    // 两个及以上错误时在页顶汇总，一个错误直接滚到字段处即可
    setErrorSummary(list.length >= 2 ? list : []);
    if (list.length) form.scrollToField(list[0].name, { focus: true });
  };

  const title = editing ? '编辑供应商' : '新增供应商';
  const grid: React.CSSProperties = {
    display: 'grid',
    gridTemplateColumns:
      'repeat(auto-fit, minmax(max(280px, calc(50% - 12px)), 1fr))',
    columnGap: 24,
  };
  const full: React.CSSProperties = { gridColumn: '1 / -1' };

  if (!allowed) {
    return (
      <>
        <PageTitle title={title} current={title} />
        <EmptyHint
          title="没有权限"
          description={`你的账号没有${editing ? '编辑' : '新增'}供应商的权限，请联系管理员开通。`}
          actionText="返回列表"
          onAction={leave}
        />
      </>
    );
  }

  let body: React.ReactNode;
  if (loading) {
    body = (
      <div
        style={{
          background: palette.card,
          borderRadius: 16,
          padding: 24,
          border: `1px solid ${palette.hairline}`,
        }}
      >
        <Skeleton active paragraph={{ rows: 10 }} />
      </div>
    );
  } else if (loadError) {
    body = <ErrorHint message={loadError} onRetry={loadRecord} />;
  } else if (editing && record === null) {
    body = (
      <EmptyHint
        title="供应商不存在"
        description="该供应商可能已被删除。"
        actionText="返回列表"
        onAction={leave}
      />
    );
  }

  return (
    <div style={{ color: palette.ink }}>
      <a className="zhul-skip" href="#supplier-main">
        跳到主要内容
      </a>
      <PageTitle title={title} current={title} />

      {body ?? (
        <Form<FormState>
          form={form}
          layout="vertical"
          validateTrigger="onBlur"
          initialValues={{ status: 1, productScopes: [] }}
          onFinish={submit}
          onFinishFailed={onFinishFailed}
          requiredMark
        >
          {errorSummary.length > 0 && (
            <Alert
              type="error"
              showIcon
              role="alert"
              style={{ marginBottom: 20, borderRadius: 12 }}
              title={`有 ${errorSummary.length} 处需要修改`}
              description={
                <ul style={{ margin: 0, paddingLeft: 18 }}>
                  {errorSummary.map((e) => (
                    <li key={e.name}>
                      <a
                        onClick={() =>
                          form.scrollToField(e.name, { focus: true })
                        }
                      >
                        {e.text}
                      </a>
                    </li>
                  ))}
                </ul>
              }
            />
          )}

          <SectionCard icon={<InfoCircleOutlined />} title="基本信息">
            <div style={grid}>
              <Form.Item
                name="supplierCode"
                label="供应商编码"
                required
                extra={
                  editing
                    ? '创建后不可修改'
                    : '全局唯一，最大20位，仅支持字母数字'
                }
                rules={
                  editing
                    ? []
                    : [
                        { required: true, message: '请输入供应商编码' },
                        {
                          pattern: SUPPLIER_CODE_PATTERN,
                          message: '供应商编码只能包含字母和数字，最多20位',
                        },
                      ]
                }
              >
                <Input
                  placeholder="请输入供应商编码"
                  maxLength={20}
                  readOnly={editing}
                  suffix={
                    editing ? (
                      <LockOutlined
                        aria-label="只读"
                        style={{ color: palette.mute }}
                      />
                    ) : undefined
                  }
                />
              </Form.Item>
              <Form.Item
                name="name"
                label="供应商名称"
                rules={[
                  {
                    required: true,
                    whitespace: true,
                    message: '请输入供应商名称',
                  },
                  { max: 100, message: '供应商名称不能超过100个字符' },
                ]}
              >
                <Input placeholder="请输入供应商全称" maxLength={100} />
              </Form.Item>
              <Form.Item
                name="shortName"
                label="供应商简称"
                rules={[{ max: 50, message: '供应商简称不能超过50个字符' }]}
              >
                <Input placeholder="请输入供应商简称" maxLength={50} />
              </Form.Item>
              <Form.Item
                name="supplierType"
                label="供应商类型"
                rules={[{ required: true, message: '请选择供应商类型' }]}
              >
                <Select
                  placeholder="请选择供应商类型"
                  options={SUPPLIER_TYPE_OPTIONS}
                />
              </Form.Item>
              <Form.Item name="industry" label="所属行业">
                <Select
                  placeholder="请选择所属行业"
                  options={INDUSTRY_OPTIONS}
                  allowClear
                />
              </Form.Item>
              <Form.Item
                name="status"
                label="状态"
                rules={[{ required: true, message: '请选择状态' }]}
              >
                <Radio.Group
                  options={[
                    { value: 1, label: '启用' },
                    { value: 0, label: '禁用' },
                  ]}
                />
              </Form.Item>
            </div>
          </SectionCard>

          <SectionCard icon={<BankOutlined />} title="工商信息">
            <div style={grid}>
              <Form.Item
                name="creditCode"
                label="统一社会信用代码"
                normalize={(v?: string) => v?.toUpperCase()}
                rules={[
                  {
                    pattern: CREDIT_CODE_PATTERN,
                    message: '请输入正确格式的统一社会信用代码（18位）',
                  },
                ]}
              >
                <Input
                  placeholder="请输入18位统一社会信用代码"
                  maxLength={18}
                />
              </Form.Item>
              <Form.Item
                name="legalRepresentative"
                label="法人代表"
                rules={[{ max: 50, message: '法人代表不能超过50个字符' }]}
              >
                <Input placeholder="请输入法人代表姓名" maxLength={50} />
              </Form.Item>
              <Form.Item
                name="registeredCapital"
                label="注册资本（万元人民币）"
              >
                <InputNumber<string>
                  stringMode
                  min="0"
                  precision={2}
                  placeholder="请输入注册资本"
                  style={{ width: '100%' }}
                />
              </Form.Item>
              <Form.Item name="establishedDate" label="成立日期">
                <DatePicker
                  placeholder="请选择成立日期"
                  style={{ width: '100%' }}
                  disabledDate={(d) => d.isAfter(dayjs(), 'day')}
                />
              </Form.Item>
            </div>
          </SectionCard>

          <SectionCard icon={<PhoneOutlined />} title="联系信息">
            <div style={grid}>
              <Form.Item
                name="contactName"
                label="联系人"
                rules={[{ max: 50, message: '联系人不能超过50个字符' }]}
              >
                <Input placeholder="请输入联系人姓名" maxLength={50} />
              </Form.Item>
              <Form.Item
                name="contactPhone"
                label="联系电话"
                rules={[{ max: 20, message: '联系电话不能超过20个字符' }]}
              >
                <Input placeholder="请输入联系电话" maxLength={20} />
              </Form.Item>
              <Form.Item
                name="contactEmail"
                label="联系邮箱"
                rules={[
                  { type: 'email', message: '请输入正确的邮箱格式' },
                  { max: 100, message: '联系邮箱不能超过100个字符' },
                ]}
              >
                <Input placeholder="请输入联系邮箱" maxLength={100} />
              </Form.Item>
              <Form.Item name="region" label="所在地区">
                <Cascader<RegionNode>
                  placeholder="请选择所在地区"
                  options={regions}
                  fieldNames={{
                    label: 'name',
                    value: 'name',
                    children: 'children',
                  }}
                  showSearch
                  allowClear
                />
              </Form.Item>
              <Form.Item
                name="address"
                label="详细地址"
                style={full}
                rules={[{ max: 200, message: '详细地址不能超过200个字符' }]}
              >
                <Input placeholder="请输入详细地址" maxLength={200} />
              </Form.Item>
            </div>
          </SectionCard>

          <SectionCard icon={<WalletOutlined />} title="结算信息">
            <div style={grid}>
              <Form.Item
                name="bankName"
                label="开户银行"
                rules={[{ max: 100, message: '开户银行不能超过100个字符' }]}
              >
                <Input
                  placeholder="请输入开户行名称（含支行）"
                  maxLength={100}
                />
              </Form.Item>
              <Form.Item
                name="bankAccount"
                label="银行账号"
                extra={
                  editing
                    ? '编辑态明文展示，保存后列表、详情只显示前4位和后4位'
                    : undefined
                }
                rules={[
                  {
                    pattern: BANK_ACCOUNT_PATTERN,
                    message: '银行账号只能包含数字',
                  },
                ]}
              >
                <Input
                  placeholder="请输入银行账号"
                  maxLength={30}
                  inputMode="numeric"
                  className="num"
                />
              </Form.Item>
              <Form.Item
                name="remark"
                label="备注"
                style={full}
                rules={[{ max: 500, message: '备注不能超过500个字符' }]}
              >
                <Input.TextArea
                  placeholder="请输入备注信息"
                  rows={3}
                  maxLength={500}
                  showCount
                />
              </Form.Item>
            </div>
          </SectionCard>

          <SectionCard icon={<AppstoreOutlined />} title="主营产品">
            <ProductScopeEditor />
          </SectionCard>

          {editing && record && (
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                gap: 12,
                padding: '0 24px',
                fontSize: 13,
                color: palette.mute,
              }}
            >
              <span>
                创建人{' '}
                <span style={{ color: palette.ink }}>
                  {record.createBy || '—'}
                </span>
              </span>
              <span>
                创建时间{' '}
                <span className="num" style={{ color: palette.ink }}>
                  {formatDateTime(record.createTime)}
                </span>
              </span>
              <span>
                最后修改人{' '}
                <span style={{ color: palette.ink }}>
                  {record.updateBy || '—'}
                </span>
              </span>
              <span>
                最后修改时间{' '}
                <span className="num" style={{ color: palette.ink }}>
                  {formatDateTime(record.updateTime)}
                </span>
              </span>
            </div>
          )}

          <div
            style={{
              position: 'sticky',
              bottom: 0,
              zIndex: 10,
              display: 'flex',
              justifyContent: 'flex-end',
              gap: 12,
              marginTop: 20,
              padding: '14px 24px',
              borderRadius: 16,
              background: palette.sidebar,
              border: `1px solid ${palette.hairline}`,
            }}
          >
            <Button onClick={cancel}>取消</Button>
            <Button type="primary" htmlType="submit" loading={saving}>
              保存
            </Button>
          </div>
        </Form>
      )}
    </div>
  );
};

export default SupplierFormPage;
