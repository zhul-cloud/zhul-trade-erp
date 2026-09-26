import {
  CopyOutlined,
  EnvironmentOutlined,
  FileTextOutlined,
  InfoCircleOutlined,
  LockOutlined,
  PlusOutlined,
  UserOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import { history, useAccess, useParams, useSearchParams } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Radio,
  Segmented,
  Select,
  Skeleton,
  Space,
} from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { useCountries } from '@/pages/product/components/useCountries';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import {
  LIST_PATH,
  PageTitle,
  PartyDrawer,
  PartyGroups,
  SectionCard,
} from './components';
import {
  CJK_PATTERN,
  CURRENCY_OPTIONS,
  CUSTOMER_CODE_PATTERN,
  ENGLISH_ONLY_MESSAGE,
  GRADE_OPTIONS,
  INCOTERMS,
  INDUSTRY_OPTIONS,
  MAX_PARTIES,
  PAYMENT_OPTIONS,
  PAYMENT_WITH_DAYS,
  PAYMENT_WITH_DEPOSIT,
  PHONE_PATTERN,
  ROLE_OPTIONS,
  SHIPPING_OPTIONS,
  SOURCE_OPTIONS,
} from './constants';
import {
  type AssignableOwners,
  type CustomerDetail,
  type CustomerParty,
  type CustomerPayload,
  customerApi,
  loadCountryTimezones,
  readBizError,
} from './service';
import { allZones, zoneLabel } from './time';

type FormState = Omit<CustomerPayload, 'parties' | 'creditLimit'> & {
  creditLimit?: string | null;
};

const FIELD_LABELS: Record<string, string> = {
  customerCode: '客户编码',
  name: '客户名称（英文）',
  customerRole: '客户角色',
  status: '状态',
  country: '国家/地区',
  address: '详细地址（英文）',
  contactPhone: '电话',
  whatsapp: 'WhatsApp',
  contactEmail: '邮箱',
  website: '官网',
  currency: '默认币种',
  incotermPlace: '术语地点',
  depositRatio: '定金比例',
  paymentDays: '账期',
  creditLimit: '信用额度',
  creditCurrency: '信用额度币种',
};

const englishOnly = {
  validator: (_: unknown, v?: string) =>
    v && CJK_PATTERN.test(v)
      ? Promise.reject(new Error(ENGLISH_ONLY_MESSAGE))
      : Promise.resolve(),
};
const phoneRule = {
  pattern: PHONE_PATTERN,
  message: '只能包含 + 数字 空格 - 括号，最多30位',
};

const toFormState = (d: CustomerDetail): FormState => ({
  customerCode: d.customerCode,
  name: d.name,
  nameCn: d.nameCn,
  shortName: d.shortName,
  // 快速创建的客户角色为「未设置」（0），不回填，让用户补选
  customerRole: d.customerRole || undefined,
  industry: d.industry || undefined,
  website: d.website,
  customerGrade: d.customerGrade,
  sourceChannel: d.sourceChannel || undefined,
  externalRef: d.externalRef,
  remark: d.remark,
  country: d.country,
  state: d.state,
  city: d.city,
  postcode: d.postcode,
  address: d.address,
  taxId: d.taxId,
  timezone: d.timezone || undefined,
  contactName: d.contactName,
  contactTitle: d.contactTitle,
  contactEmail: d.contactEmail,
  contactPhone: d.contactPhone,
  whatsapp: d.whatsapp,
  otherIm: d.otherIm,
  currency: d.currency,
  incoterm: d.incoterm || undefined,
  incotermPlace: d.incotermPlace,
  paymentMethod: d.paymentMethod || undefined,
  depositRatio: d.depositRatio ?? undefined,
  paymentDays: d.paymentDays ?? undefined,
  creditLimit:
    d.creditLimit === null || d.creditLimit === undefined
      ? null
      : Number(d.creditLimit).toFixed(2),
  creditCurrency: d.creditCurrency || undefined,
  shippingMethod: d.shippingMethod || undefined,
  destinationPort: d.destinationPort,
  status: d.status,
});

const text = (v?: string | null) => (v ?? '').trim();

const toPayload = (
  v: FormState,
  parties: CustomerParty[],
  editing: boolean,
): CustomerPayload => ({
  ...(editing
    ? {}
    : { customerCode: text(v.customerCode), ownerId: v.ownerId }),
  name: text(v.name),
  nameCn: text(v.nameCn),
  shortName: text(v.shortName),
  customerRole: v.customerRole,
  industry: v.industry ?? 0,
  website: text(v.website),
  customerGrade: v.customerGrade ?? 0,
  sourceChannel: v.sourceChannel ?? 0,
  externalRef: text(v.externalRef),
  remark: text(v.remark),
  country: v.country,
  state: text(v.state),
  city: text(v.city),
  postcode: text(v.postcode),
  address: text(v.address),
  taxId: text(v.taxId),
  timezone: v.timezone ?? '',
  contactName: text(v.contactName),
  contactTitle: text(v.contactTitle),
  contactEmail: text(v.contactEmail),
  contactPhone: text(v.contactPhone),
  whatsapp: text(v.whatsapp),
  otherIm: text(v.otherIm),
  currency: v.currency,
  incoterm: v.incoterm ?? '',
  incotermPlace: v.incoterm ? text(v.incotermPlace) : '',
  paymentMethod: v.paymentMethod ?? 0,
  depositRatio: PAYMENT_WITH_DEPOSIT.includes(v.paymentMethod ?? 0)
    ? (v.depositRatio ?? null)
    : null,
  paymentDays: PAYMENT_WITH_DAYS.includes(v.paymentMethod ?? 0)
    ? (v.paymentDays ?? null)
    : null,
  creditLimit: v.creditLimit || null,
  creditCurrency: v.creditLimit ? (v.creditCurrency ?? '') : '',
  shippingMethod: v.shippingMethod ?? 0,
  destinationPort: text(v.destinationPort),
  status: v.status,
  parties,
});

const CustomerFormPage: React.FC = () => {
  const { id } = useParams<{ id?: string }>();
  const [searchParams] = useSearchParams();
  const editing = !!id;
  const { message, modal } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const { palette } = useAppTheme();
  const [form] = Form.useForm<FormState>();
  const {
    options: countryOptions,
    codeOf,
    labelOf: countryLabel,
  } = useCountries();

  const [record, setRecord] = useState<CustomerDetail | null>();
  const [loading, setLoading] = useState(editing);
  const [loadError, setLoadError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [owners, setOwners] = useState<AssignableOwners>();
  const [tzMap, setTzMap] = useState<Record<string, string[]>>({});
  const [parties, setParties] = useState<CustomerParty[]>([]);
  const [partiesDirty, setPartiesDirty] = useState(false);
  const [drawer, setDrawer] = useState<{ open: boolean; index?: number }>({
    open: false,
  });
  const [errorSummary, setErrorSummary] = useState<
    { name: string; text: string }[]
  >([]);
  // 「从注册信息复制」：打开新增抽屉时直接填入注册信息
  const [copyOnOpen, setCopyOnOpen] = useState(false);

  const country = Form.useWatch('country', form);
  const incoterm = Form.useWatch('incoterm', form);
  const paymentMethod = Form.useWatch('paymentMethod', form);
  const creditLimit = Form.useWatch('creditLimit', form);
  const currency = Form.useWatch('currency', form);

  const allowed = editing
    ? access['partner:customer:edit']
    : access['partner:customer:add'];
  const backTo =
    editing && searchParams.get('from') === 'detail'
      ? `${LIST_PATH}/${id}`
      : LIST_PATH;

  useEffect(() => {
    loadCountryTimezones()
      .then(setTzMap)
      .catch(() => undefined);
    if (!editing) {
      customerApi
        .assignableOwners()
        .then(setOwners)
        .catch(() => undefined);
    }
  }, [editing]);

  const loadRecord = async () => {
    if (!id) return;
    setLoading(true);
    setLoadError(undefined);
    try {
      const data = await customerApi.detail(Number(id));
      setRecord(data);
      setParties(data.parties ?? []);
      form.setFieldsValue(toFormState(data));
    } catch (e) {
      const err = readBizError(e);
      if (err.message.includes('不存在')) setRecord(null);
      else setLoadError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (allowed) loadRecord();
  }, [id, allowed]);

  // 国家对应的时区：只有一个时区的国家自动带出；该国没有数据时列出全部时区
  const zoneOptions = useMemo(() => {
    const code = codeOf(country);
    const zones = (code && tzMap[code]) || [];
    return (zones.length ? zones : allZones()).map((z) => ({
      value: z,
      label: zoneLabel(z),
    }));
  }, [country, tzMap, codeOf]);

  const onCountryChange = (value: string) => {
    const code = codeOf(value);
    const zones = (code && tzMap[code]) || [];
    form.setFieldValue('timezone', zones.length === 1 ? zones[0] : undefined);
  };

  const onCurrencyChange = (value: string) => {
    if (!form.getFieldValue('creditCurrency'))
      form.setFieldValue('creditCurrency', value);
  };

  // ---------------- 单证主体
  const updateParties = (next: CustomerParty[]) => {
    setParties(next);
    setPartiesDirty(true);
  };

  const saveParty = (party: CustomerParty) => {
    const next = [...parties];
    const index = drawer.index;
    if (index === undefined) next.push(party);
    else next[index] = party;
    const self = index === undefined ? next.length - 1 : index;
    const sameType = next.filter((p) => p.partyType === party.partyType);
    if (party.defaultParty || sameType.length === 1) {
      // 同类型只能有一条默认；某类型的第一条自动默认
      next.forEach((p, i) => {
        if (p.partyType === party.partyType)
          next[i] = { ...p, defaultParty: i === self };
      });
    } else if (!sameType.some((p) => p.defaultParty)) {
      const first = next.findIndex((p) => p.partyType === party.partyType);
      next[first] = { ...next[first], defaultParty: true };
    }
    updateParties(next);
    setDrawer({ open: false });
  };

  const setDefault = (index: number) => {
    const type = parties[index].partyType;
    updateParties(
      parties.map((p, i) =>
        p.partyType === type ? { ...p, defaultParty: i === index } : p,
      ),
    );
  };

  const removeParty = (index: number) => {
    const removed = parties[index];
    const next = parties.filter((_, i) => i !== index);
    if (removed.defaultParty) {
      // 删除默认记录后，同类型中最早的一条自动成为默认
      const first = next.findIndex((p) => p.partyType === removed.partyType);
      if (first >= 0) next[first] = { ...next[first], defaultParty: true };
    }
    updateParties(next);
  };

  const registration = (): Partial<CustomerParty> => {
    const v = form.getFieldsValue();
    return {
      companyName: v.name,
      country: v.country,
      state: v.state,
      city: v.city,
      postcode: v.postcode,
      address: v.address,
      taxId: v.taxId,
      contactName: v.contactName,
      phone: v.contactPhone,
      email: v.contactEmail,
    };
  };

  // ---------------- 提交 / 离开
  const leave = () => history.push(backTo);

  const cancel = () => {
    if (!form.isFieldsTouched() && !partiesDirty) {
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
      const payload = toPayload(values, parties, editing);
      if (editing) {
        await customerApi.update(Number(id), payload);
        message.success('保存成功');
      } else {
        await customerApi.create(payload);
        message.success('新增客户成功');
      }
      leave();
    } catch (e) {
      const err = readBizError(e);
      const field =
        err.errorCode === 'CUSTOMER_DUPLICATE'
          ? 'name'
          : err.errorCode === 'CUSTOMER_CODE_DUPLICATE'
            ? 'customerCode'
            : undefined;
      if (field) {
        form.setFields([{ name: field, errors: [err.message] }]);
        form.scrollToField(field, { focus: true });
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
    setErrorSummary(list.length >= 2 ? list : []);
    if (list.length) form.scrollToField(list[0].name, { focus: true });
  };

  // ---------------- 渲染
  const title = editing ? '编辑客户' : '新增客户';
  const grid: React.CSSProperties = {
    display: 'grid',
    gridTemplateColumns:
      'repeat(auto-fit, minmax(max(280px, calc(50% - 12px)), 1fr))',
    columnGap: 24,
  };
  const full: React.CSSProperties = { gridColumn: '1 / -1' };
  const showOwnerSelect = owners?.scope === 'ALL' || owners?.scope === 'CUSTOM';

  if (!allowed) {
    return (
      <>
        <PageTitle title={title} current={title} />
        <EmptyHint
          title="没有权限"
          description={`你的账号没有${editing ? '编辑' : '新增'}客户的权限，请联系管理员开通。`}
          actionText="返回列表"
          onAction={() => history.push(LIST_PATH)}
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
        <Skeleton active paragraph={{ rows: 12 }} />
      </div>
    );
  } else if (loadError) {
    body = <ErrorHint message={loadError} onRetry={loadRecord} />;
  } else if (editing && record === null) {
    body = (
      <EmptyHint
        title="客户不存在或无权查看"
        description="该客户可能已被删除，或不在你的数据范围内。"
        actionText="返回列表"
        onAction={() => history.push(LIST_PATH)}
      />
    );
  }

  const partyActions = (
    <div style={{ display: 'flex', gap: 8 }}>
      <Button
        size="small"
        icon={<CopyOutlined />}
        disabled={parties.length >= MAX_PARTIES}
        onClick={() => {
          setCopyOnOpen(true);
          setDrawer({ open: true, index: undefined });
        }}
      >
        从注册信息复制
      </Button>
      <Button
        size="small"
        icon={<PlusOutlined />}
        disabled={parties.length >= MAX_PARTIES}
        title={
          parties.length >= MAX_PARTIES
            ? '每个客户最多 20 条单证信息'
            : undefined
        }
        onClick={() => setDrawer({ open: true, index: undefined })}
      >
        新增
      </Button>
    </div>
  );
  return (
    <div style={{ color: palette.ink }}>
      <a className="zhul-skip" href="#customer-main">
        跳到主要内容
      </a>
      <PageTitle title={title} current={title} />

      {body ?? (
        <Form<FormState>
          form={form}
          layout="vertical"
          validateTrigger="onBlur"
          initialValues={{ status: 1, currency: 'USD', customerGrade: 0 }}
          onFinish={submit}
          onFinishFailed={onFinishFailed}
          onValuesChange={(changed) => {
            // 服务端返回的撞单 / 编码重复提示挂在字段上，用户改了相关字段就清掉，交给前端校验重新判断
            const stale = (['name', 'country', 'customerCode'] as const).filter(
              (k) => k in changed,
            );
            if (stale.length)
              form.setFields(stale.map((name) => ({ name, errors: [] })));
          }}
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
                name="customerCode"
                label="客户编码"
                extra={
                  editing
                    ? '创建后不可修改'
                    : '仅字母和数字，最多 20 位；留空则自动生成'
                }
                rules={
                  editing
                    ? []
                    : [
                        {
                          pattern: CUSTOMER_CODE_PATTERN,
                          message: '客户编码只能包含字母和数字，最多20位',
                        },
                      ]
                }
              >
                <Input
                  placeholder="留空则自动生成，如 CUS00012"
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
                label="客户名称（英文）"
                extra="单据上使用，请填写英文法定全称"
                rules={[
                  {
                    required: true,
                    whitespace: true,
                    message: '请输入客户名称',
                  },
                  { max: 200 },
                  englishOnly,
                ]}
              >
                <Input placeholder="如 ABC Automation GmbH" maxLength={200} />
              </Form.Item>
              <Form.Item name="nameCn" label="中文名称" rules={[{ max: 100 }]}>
                <Input placeholder="内部称呼或中文译名" maxLength={100} />
              </Form.Item>
              <Form.Item
                name="shortName"
                label="客户简称"
                rules={[{ max: 50 }]}
              >
                <Input placeholder="列表中缩略展示" maxLength={50} />
              </Form.Item>
              <Form.Item
                name="customerRole"
                label="客户角色"
                rules={[{ required: true, message: '请选择客户角色' }]}
              >
                <Select placeholder="请选择客户角色" options={ROLE_OPTIONS} />
              </Form.Item>
              <Form.Item name="industry" label="应用行业">
                <Select
                  placeholder="请选择应用行业"
                  options={INDUSTRY_OPTIONS}
                  allowClear
                />
              </Form.Item>
              <Form.Item
                name="website"
                label="官网"
                rules={[
                  {
                    pattern: /^https?:\/\/\S+$/,
                    message: '官网需以 http:// 或 https:// 开头',
                  },
                ]}
              >
                <Input placeholder="https://" maxLength={200} />
              </Form.Item>
              <Form.Item
                name="customerGrade"
                label="客户等级"
                extra="当前手工标记：A 年采购额 > 100 万美元，B 50–100 万，C < 50 万或新客户"
              >
                <Segmented block options={GRADE_OPTIONS} />
              </Form.Item>
              <Form.Item name="sourceChannel" label="客户来源">
                <Select
                  placeholder="请选择客户来源"
                  options={SOURCE_OPTIONS}
                  allowClear
                />
              </Form.Item>
              {editing ? (
                <Form.Item
                  label="负责业务员"
                  extra="修改负责人请使用列表或详情页的「转移」，会留下转移记录"
                >
                  <Input
                    value={record?.ownerName || '未分配'}
                    readOnly
                    suffix={
                      <LockOutlined
                        aria-label="只读"
                        style={{ color: palette.mute }}
                      />
                    }
                  />
                </Form.Item>
              ) : (
                <Form.Item
                  name="ownerId"
                  label="负责业务员"
                  extra={
                    showOwnerSelect
                      ? '默认是你自己，可指定为数据范围内的其他业务员'
                      : undefined
                  }
                >
                  <Select
                    placeholder="我自己"
                    allowClear
                    disabled={!showOwnerSelect}
                    showSearch={{ optionFilterProp: 'label' }}
                    options={owners?.owners.map((o) => ({
                      value: o.id,
                      label: o.deptName ? `${o.name} · ${o.deptName}` : o.name,
                    }))}
                  />
                </Form.Item>
              )}
              <Form.Item
                name="externalRef"
                label="小满客户编号"
                rules={[{ max: 50 }]}
              >
                <Input placeholder="客户在小满中的编号" maxLength={50} />
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
              <Form.Item
                name="remark"
                label="备注"
                style={full}
                rules={[{ max: 500 }]}
              >
                <Input.TextArea
                  rows={3}
                  maxLength={500}
                  showCount
                  placeholder="请输入备注信息"
                />
              </Form.Item>
            </div>
          </SectionCard>

          <SectionCard
            icon={<EnvironmentOutlined />}
            title="注册地址与税务"
            description="客户公司的注册信息，作为报价单、PI 上买方的默认地址。单据字段请使用英文。"
          >
            <div style={grid}>
              <Form.Item
                name="country"
                label="国家/地区"
                rules={[{ required: true, message: '请选择国家/地区' }]}
              >
                <Select
                  placeholder="请选择国家/地区"
                  showSearch={{ optionFilterProp: 'label' }}
                  options={countryOptions}
                  onChange={onCountryChange}
                />
              </Form.Item>
              <Form.Item name="state" label="州/省" rules={[{ max: 100 }]}>
                <Input placeholder="State / Province" maxLength={100} />
              </Form.Item>
              <Form.Item name="city" label="城市" rules={[{ max: 100 }]}>
                <Input placeholder="City" maxLength={100} />
              </Form.Item>
              <Form.Item name="postcode" label="邮编" rules={[{ max: 20 }]}>
                <Input placeholder="Postcode" maxLength={20} />
              </Form.Item>
              <Form.Item
                name="address"
                label="详细地址（英文）"
                style={full}
                rules={[{ max: 300 }, englishOnly]}
              >
                <Input placeholder="Street, building, floor" maxLength={300} />
              </Form.Item>
              <Form.Item name="taxId" label="税号" rules={[{ max: 50 }]}>
                <Input placeholder="VAT / EIN / GST / CNPJ 等" maxLength={50} />
              </Form.Item>
              <Form.Item
                name="timezone"
                label="时区"
                extra="选择国家后自动带出；跨时区国家需手工选择"
              >
                <Select
                  placeholder="请选择时区"
                  showSearch={{ optionFilterProp: 'label' }}
                  allowClear
                  options={zoneOptions}
                />
              </Form.Item>
            </div>
          </SectionCard>

          <SectionCard icon={<UserOutlined />} title="主联系人">
            <div style={grid}>
              <Form.Item
                name="contactName"
                label="联系人姓名"
                rules={[{ max: 100 }]}
              >
                <Input placeholder="Full name" maxLength={100} />
              </Form.Item>
              <Form.Item name="contactTitle" label="职位" rules={[{ max: 50 }]}>
                <Input placeholder="如 Purchasing Manager" maxLength={50} />
              </Form.Item>
              <Form.Item
                name="contactEmail"
                label="邮箱"
                rules={[
                  { type: 'email', message: '请输入正确的邮箱格式' },
                  { max: 100 },
                ]}
              >
                <Input placeholder="name@company.com" maxLength={100} />
              </Form.Item>
              <Form.Item name="contactPhone" label="电话" rules={[phoneRule]}>
                <Input placeholder="+49 89 1234 5678" maxLength={30} />
              </Form.Item>
              <Form.Item name="whatsapp" label="WhatsApp" rules={[phoneRule]}>
                <Input placeholder="+49 151 2345 6789" maxLength={30} />
              </Form.Item>
              <Form.Item
                name="otherIm"
                label="其他联系方式"
                rules={[{ max: 100 }]}
              >
                <Input placeholder="微信 / Skype / LinkedIn" maxLength={100} />
              </Form.Item>
            </div>
          </SectionCard>

          <SectionCard icon={<WalletOutlined />} title="默认交易条件">
            <div style={grid}>
              <Form.Item
                name="currency"
                label="默认币种"
                rules={[{ required: true, message: '请选择默认币种' }]}
              >
                <Select
                  options={CURRENCY_OPTIONS}
                  onChange={onCurrencyChange}
                />
              </Form.Item>
              <Form.Item name="shippingMethod" label="运输方式">
                <Select
                  placeholder="请选择运输方式"
                  options={SHIPPING_OPTIONS}
                  allowClear
                />
              </Form.Item>
              <Form.Item name="incoterm" label="贸易术语">
                <Select
                  placeholder="请选择贸易术语"
                  allowClear
                  options={INCOTERMS.map((t) => ({ value: t, label: t }))}
                />
              </Form.Item>
              <Form.Item
                name="incotermPlace"
                label="术语地点"
                extra="FOB 填装运港；CIF、DAP 等填目的港或目的地"
                rules={[
                  {
                    required: !!incoterm,
                    whitespace: true,
                    message: '请填写术语地点，如 FOB 的装运港',
                  },
                  { max: 100 },
                ]}
              >
                <Input
                  placeholder="如 Shanghai"
                  maxLength={100}
                  disabled={!incoterm}
                />
              </Form.Item>
              <Form.Item name="paymentMethod" label="付款方式">
                <Select
                  placeholder="请选择付款方式"
                  options={PAYMENT_OPTIONS}
                  allowClear
                />
              </Form.Item>
              {PAYMENT_WITH_DEPOSIT.includes(paymentMethod ?? 0) && (
                <Form.Item
                  name="depositRatio"
                  label="定金比例（%）"
                  rules={[{ required: true, message: '请填写定金比例' }]}
                >
                  <InputNumber
                    min={0}
                    max={100}
                    precision={0}
                    style={{ width: '100%' }}
                    placeholder="如 30"
                  />
                </Form.Item>
              )}
              {PAYMENT_WITH_DAYS.includes(paymentMethod ?? 0) && (
                <Form.Item
                  name="paymentDays"
                  label="账期（天）"
                  rules={[{ required: true, message: '请填写账期（天）' }]}
                >
                  <InputNumber
                    min={0}
                    max={365}
                    precision={0}
                    style={{ width: '100%' }}
                    placeholder="如 60"
                  />
                </Form.Item>
              )}
              <Form.Item
                label="信用额度"
                extra="金额和币种需同时填写；当前只做记录，不做超限管控"
              >
                <Space.Compact block>
                  <Form.Item name="creditLimit" noStyle>
                    <InputNumber<string>
                      stringMode
                      min="0"
                      precision={2}
                      style={{ width: '100%' }}
                      placeholder="0.00"
                    />
                  </Form.Item>
                  <Form.Item
                    name="creditCurrency"
                    noStyle
                    rules={[
                      {
                        required: !!creditLimit,
                        message: '信用额度需要同时填写金额和币种',
                      },
                    ]}
                  >
                    <Select
                      style={{ width: 120 }}
                      placeholder={currency ?? '币种'}
                      options={CURRENCY_OPTIONS.map((c) => ({
                        value: c.value,
                        label: c.value,
                      }))}
                    />
                  </Form.Item>
                </Space.Compact>
              </Form.Item>
              <Form.Item
                name="destinationPort"
                label="目的港"
                rules={[{ max: 100 }]}
              >
                <Input placeholder="如 Hamburg" maxLength={100} />
              </Form.Item>
            </div>
            <div
              style={{
                display: 'flex',
                gap: 8,
                alignItems: 'center',
                padding: '10px 12px',
                borderRadius: 10,
                background: palette.accentSoft,
                fontSize: 12,
                color: palette.sub,
              }}
            >
              <InfoCircleOutlined style={{ color: palette.link }} />
              以上为默认值，创建报价单、PI、合同时自动带出，可在单据上修改；修改这里不影响已生成的单据。
            </div>
          </SectionCard>

          <SectionCard
            icon={<FileTextOutlined />}
            title="收货与单证信息"
            extra={partyActions}
          >
            {parties.length ? (
              <PartyGroups
                parties={parties}
                countryLabel={countryLabel}
                onEdit={(i) => setDrawer({ open: true, index: i })}
                onDelete={removeParty}
                onSetDefault={setDefault}
              />
            ) : (
              <EmptyHint
                title="还没有收货与单证信息"
                description="PI、CI 和提单上的收货人、通知方、发票抬头从这里带出。收货人就是客户本身时，点「从注册信息复制」即可。"
              />
            )}
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

      <PartyDrawer
        open={drawer.open}
        initial={drawer.index !== undefined ? parties[drawer.index] : undefined}
        registration={registration}
        copyOnOpen={copyOnOpen}
        onCopied={() => setCopyOnOpen(false)}
        onClose={() => {
          setDrawer({ open: false });
          setCopyOnOpen(false);
        }}
        onSave={saveParty}
      />
    </div>
  );
};

export default CustomerFormPage;
