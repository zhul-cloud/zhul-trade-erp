import {
  CopyOutlined,
  EnvironmentOutlined,
  FileTextOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Link } from '@umijs/max';
import {
  App,
  Breadcrumb,
  Button,
  Drawer,
  Form,
  Input,
  Modal,
  Segmented,
  Select,
  Switch,
} from 'antd';
import React, { useEffect, useState } from 'react';
import { useCountries } from '@/pages/product/components/useCountries';
import { useAppTheme } from '@/theme/AppTheme';
import {
  CJK_PATTERN,
  ENGLISH_ONLY_MESSAGE,
  GRADE_TONE,
  labelOf,
  PARTY_TYPES,
  PHONE_PATTERN,
  ROLE_OPTIONS,
  ROLE_TONE,
  type Tone,
} from './constants';
import {
  type AssignableOwners,
  type CustomerParty,
  customerApi,
  readBizError,
} from './service';

export const LIST_PATH = '/partner/customers';

export const Pill: React.FC<{ tone: Tone; children: React.ReactNode }> = ({
  tone,
  children,
}) => {
  const { palette } = useAppTheme();
  const colors: Record<Tone, { fg: string; bg: string }> = {
    accent: { fg: palette.link, bg: palette.accentSoft },
    cyan: { fg: palette.cyan, bg: palette.inset },
    orange: { fg: palette.orange, bg: palette.orangeSoft },
    green: { fg: palette.green, bg: palette.greenSoft },
    violet: { fg: palette.violet, bg: palette.violetSoft },
    gray: { fg: palette.sub, bg: palette.inset },
  };
  const c = colors[tone];
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        height: 24,
        padding: '0 10px',
        borderRadius: 12,
        fontSize: 12,
        fontWeight: 600,
        color: c.fg,
        background: c.bg,
        whiteSpace: 'nowrap',
      }}
    >
      {children}
    </span>
  );
};

/** 客户角色；0 为询盘快速创建尚未补全的客户 */
export const RolePill: React.FC<{ value?: number }> = ({ value }) => (
  <Pill tone={ROLE_TONE[value ?? 0] ?? 'gray'}>
    {labelOf(ROLE_OPTIONS, value) ?? '未设置'}
  </Pill>
);

export const GradePill: React.FC<{ value?: number }> = ({ value }) => {
  const label =
    value === 1 ? 'A' : value === 2 ? 'B' : value === 3 ? 'C' : undefined;
  return (
    <Pill tone={GRADE_TONE[value ?? 0] ?? 'gray'}>
      {label ? `${label} 类` : '未分级'}
    </Pill>
  );
};

export const StatusPill: React.FC<{ status?: number }> = ({ status }) => (
  <Pill tone={status === 1 ? 'green' : 'gray'}>
    {status === 1 ? '启用' : '禁用'}
  </Pill>
);

/** 面包屑 + 标题 + 右侧操作 */
export const PageTitle: React.FC<{
  title: React.ReactNode;
  current?: string;
  description?: React.ReactNode;
  actions?: React.ReactNode;
}> = ({ title, current, description, actions }) => {
  const { palette } = useAppTheme();
  const items = [
    { title: '客商管理' },
    current
      ? { title: <Link to={LIST_PATH}>客户管理</Link> }
      : { title: '客户管理' },
    ...(current ? [{ title: current }] : []),
  ];
  return (
    <header
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-end',
        gap: 16,
        marginBottom: 20,
        flexWrap: 'wrap',
      }}
    >
      <div style={{ minWidth: 0 }}>
        <Breadcrumb items={items} style={{ fontSize: 13 }} />
        <h1
          id="customer-main"
          tabIndex={-1}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            flexWrap: 'wrap',
            fontSize: 28,
            fontWeight: 700,
            margin: '8px 0 0',
            color: palette.ink,
          }}
        >
          {title}
        </h1>
        {description && (
          <p style={{ margin: '6px 0 0', color: palette.sub, fontSize: 14 }}>
            {description}
          </p>
        )}
      </div>
      {actions && (
        <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
          {actions}
        </div>
      )}
    </header>
  );
};

/** 分组卡片：图标 + 标题（右侧可放操作），下方是内容 */
export const SectionCard: React.FC<{
  icon: React.ReactNode;
  title: string;
  extra?: React.ReactNode;
  description?: string;
  children: React.ReactNode;
}> = ({ icon, title, extra, description, children }) => {
  const { palette } = useAppTheme();
  const id = `customer-section-${title}`;
  return (
    <section
      aria-labelledby={id}
      style={{
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
        borderRadius: 16,
        padding: 24,
        marginBottom: 20,
      }}
    >
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: 12,
        }}
      >
        <h2
          id={id}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            margin: 0,
            fontSize: 16,
            fontWeight: 600,
            color: palette.ink,
          }}
        >
          <span style={{ color: palette.link, fontSize: 15 }}>{icon}</span>
          {title}
        </h2>
        {extra}
      </div>
      {description && (
        <p style={{ margin: '6px 0 0', fontSize: 12, color: palette.mute }}>
          {description}
        </p>
      )}
      <div style={{ marginTop: 20 }}>{children}</div>
    </section>
  );
};

/** 单证主体卡片 */
export const PartyCard: React.FC<{
  party: CustomerParty;
  countryLabel: (nameEn?: string) => string;
  onEdit?: () => void;
  onDelete?: () => void;
  onSetDefault?: () => void;
}> = ({ party, countryLabel, onEdit, onDelete, onSetDefault }) => {
  const { palette } = useAppTheme();
  const address = [
    party.address,
    party.postcode,
    party.city,
    party.state,
    countryLabel(party.country),
  ]
    .filter(Boolean)
    .join(', ');
  const contact = [party.contactName, party.phone, party.email]
    .filter(Boolean)
    .join(' · ');
  const extra = [
    party.taxId && `税号 ${party.taxId}`,
    party.destinationPort && `目的港 ${party.destinationPort}`,
  ]
    .filter(Boolean)
    .join(' · ');
  const line = (icon: React.ReactNode, text: string) => (
    <div
      style={{
        display: 'flex',
        gap: 8,
        fontSize: 12,
        color: palette.sub,
        lineHeight: 1.5,
      }}
    >
      <span style={{ color: palette.mute }}>{icon}</span>
      <span style={{ wordBreak: 'break-word' }}>{text}</span>
    </div>
  );
  return (
    <div
      style={{
        flex: '1 1 320px',
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
        padding: 16,
        borderRadius: 12,
        background: palette.inset,
        border: `1px solid ${party.defaultParty ? palette.accentLine : palette.hairline}`,
      }}
    >
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          gap: 8,
          alignItems: 'center',
        }}
      >
        <div
          style={{ display: 'flex', gap: 8, alignItems: 'center', minWidth: 0 }}
        >
          <span
            style={{
              fontWeight: 600,
              color: palette.ink,
              wordBreak: 'break-word',
            }}
          >
            {party.companyName}
          </span>
          {party.defaultParty && <Pill tone="accent">默认</Pill>}
        </div>
        <div style={{ display: 'flex', gap: 12, fontSize: 12, flexShrink: 0 }}>
          {onSetDefault && !party.defaultParty && (
            <a onClick={onSetDefault}>设为默认</a>
          )}
          {onEdit && <a onClick={onEdit}>编辑</a>}
          {onDelete && (
            <a style={{ color: palette.red }} onClick={onDelete}>
              删除
            </a>
          )}
        </div>
      </div>
      {line(<EnvironmentOutlined />, address)}
      {contact && line(<UserOutlined />, contact)}
      {extra && line(<FileTextOutlined />, extra)}
    </div>
  );
};

/** 按类型分组展示单证主体；可编辑时由调用方传入操作回调 */
export const PartyGroups: React.FC<{
  parties: CustomerParty[];
  countryLabel: (nameEn?: string) => string;
  onEdit?: (index: number) => void;
  onDelete?: (index: number) => void;
  onSetDefault?: (index: number) => void;
}> = ({ parties, countryLabel, onEdit, onDelete, onSetDefault }) => {
  const { palette } = useAppTheme();
  const empty: Record<number, string> = {
    1: '未设置。报价单、PI 的收货人与买方相同。',
    2: '未设置。单据上的通知方打印 SAME AS CONSIGNEE。',
    3: '未设置。PI、CI 的买方取客户英文名称 + 注册地址 + 税号。买方与付款方不同时再添加。',
  };
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {PARTY_TYPES.map((t) => {
        const items = parties
          .map((p, i) => ({ p, i }))
          .filter(({ p }) => p.partyType === t.value);
        return (
          <div
            key={t.value}
            style={{ display: 'flex', flexDirection: 'column', gap: 10 }}
          >
            <div style={{ display: 'flex', gap: 10, alignItems: 'baseline' }}>
              <span
                style={{ fontSize: 14, fontWeight: 600, color: palette.sub }}
              >
                {t.label}
              </span>
              <span style={{ fontSize: 12, color: palette.mute }}>{t.en}</span>
            </div>
            {items.length ? (
              <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
                {items.map(({ p, i }) => (
                  <PartyCard
                    key={p.id ?? `new-${i}`}
                    party={p}
                    countryLabel={countryLabel}
                    onEdit={onEdit && (() => onEdit(i))}
                    onDelete={onDelete && (() => onDelete(i))}
                    onSetDefault={onSetDefault && (() => onSetDefault(i))}
                  />
                ))}
              </div>
            ) : (
              <div
                style={{
                  padding: '12px 16px',
                  borderRadius: 12,
                  background: palette.inset,
                  fontSize: 12,
                  color: palette.mute,
                }}
              >
                {empty[t.value]}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
};

const englishOnly = {
  validator: (_: unknown, v?: string) =>
    v && CJK_PATTERN.test(v)
      ? Promise.reject(new Error(ENGLISH_ONLY_MESSAGE))
      : Promise.resolve(),
};

/** 单证主体编辑抽屉 */
export const PartyDrawer: React.FC<{
  open: boolean;
  initial?: CustomerParty;
  /** 当前客户表单里的注册信息，用于「从注册信息复制」 */
  registration: () => Partial<CustomerParty>;
  /** 打开时直接填入注册信息（新增抽屉的「从注册信息复制」入口） */
  copyOnOpen?: boolean;
  onCopied?: () => void;
  onClose: () => void;
  onSave: (party: CustomerParty) => void;
}> = ({
  open,
  initial,
  registration,
  copyOnOpen,
  onCopied,
  onClose,
  onSave,
}) => {
  const { palette } = useAppTheme();
  const [form] = Form.useForm<CustomerParty>();
  const { options: countryOptions } = useCountries();
  const partyType = Form.useWatch('partyType', form);

  useEffect(() => {
    if (open) {
      form.resetFields();
      form.setFieldsValue(initial ?? { partyType: 1, defaultParty: false });
      if (copyOnOpen) {
        form.setFieldsValue(registration());
        onCopied?.();
      }
    }
    // 只在打开抽屉（或切换编辑对象）时初始化；registration / onCopied 每次渲染都是新函数，不能放进依赖
  }, [open, initial, form]);

  const submit = async () => {
    const v = await form.validateFields();
    onSave({
      ...initial,
      ...v,
      destinationPort: v.partyType === 1 ? v.destinationPort : '',
    });
  };

  const typeLabel = PARTY_TYPES.find((t) => t.value === partyType)?.label ?? '';
  const row: React.CSSProperties = {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
    columnGap: 16,
  };
  return (
    <Drawer
      open={open}
      onClose={onClose}
      size={560}
      destroyOnHidden
      title={initial ? '编辑收货与单证信息' : '新增收货与单证信息'}
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" onClick={submit}>
            保存
          </Button>
        </div>
      }
    >
      <Form form={form} layout="vertical" validateTrigger="onBlur">
        <Form.Item name="partyType" label="类型" rules={[{ required: true }]}>
          <Segmented
            block
            options={PARTY_TYPES.map((t) => ({
              value: t.value,
              label: t.label,
            }))}
          />
        </Form.Item>
        <Button
          type="link"
          icon={<CopyOutlined />}
          style={{ padding: 0, marginBottom: 16 }}
          onClick={() => form.setFieldsValue(registration())}
        >
          从注册信息复制
        </Button>
        <span style={{ fontSize: 12, color: palette.mute, marginLeft: 8 }}>
          填入客户英文名称、注册地址、税号和主联系人
        </span>
        <Form.Item
          name="companyName"
          label="公司名称（英文）"
          rules={[
            { required: true, whitespace: true, message: '请填写公司名称' },
            { max: 200 },
            englishOnly,
          ]}
        >
          <Input placeholder="Company name" maxLength={200} />
        </Form.Item>
        <div style={row}>
          <Form.Item
            name="country"
            label="国家/地区"
            rules={[{ required: true, message: '请选择国家/地区' }]}
          >
            <Select
              showSearch={{ optionFilterProp: 'label' }}
              options={countryOptions}
              placeholder="请选择"
            />
          </Form.Item>
          <Form.Item name="state" label="州/省" rules={[{ max: 100 }]}>
            <Input placeholder="State / Province" maxLength={100} />
          </Form.Item>
        </div>
        <div style={row}>
          <Form.Item name="city" label="城市" rules={[{ max: 100 }]}>
            <Input placeholder="City" maxLength={100} />
          </Form.Item>
          <Form.Item name="postcode" label="邮编" rules={[{ max: 20 }]}>
            <Input placeholder="Postcode" maxLength={20} />
          </Form.Item>
        </div>
        <Form.Item
          name="address"
          label="详细地址（英文）"
          rules={[
            { required: true, whitespace: true, message: '请填写详细地址' },
            { max: 300 },
            englishOnly,
          ]}
        >
          <Input.TextArea
            rows={2}
            placeholder="Street, building, floor"
            maxLength={300}
          />
        </Form.Item>
        <div style={row}>
          <Form.Item name="contactName" label="联系人" rules={[{ max: 100 }]}>
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item
            name="phone"
            label="电话"
            rules={[
              {
                pattern: PHONE_PATTERN,
                message: '只能包含 + 数字 空格 - 括号，最多30位',
              },
            ]}
          >
            <Input placeholder="+49 40 9876 5432" maxLength={30} />
          </Form.Item>
        </div>
        <div style={row}>
          <Form.Item
            name="email"
            label="邮箱"
            rules={[
              { type: 'email', message: '请输入正确的邮箱格式' },
              { max: 100 },
            ]}
          >
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item
            name="taxId"
            label="税号"
            extra="进口国清关需要时填写"
            rules={[{ max: 50 }]}
          >
            <Input maxLength={50} />
          </Form.Item>
        </div>
        {partyType === 1 && (
          <Form.Item
            name="destinationPort"
            label="目的港"
            extra="为空时取默认交易条件中的目的港"
            rules={[{ max: 100 }]}
          >
            <Input placeholder="如 Hamburg" maxLength={100} />
          </Form.Item>
        )}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 12,
            marginBottom: 24,
          }}
        >
          <div>
            <div style={{ color: palette.ink }}>设为默认{typeLabel}</div>
            <div style={{ fontSize: 12, color: palette.mute }}>
              同类型只能有一条默认，设为默认后原默认记录自动取消
            </div>
          </div>
          <Form.Item name="defaultParty" valuePropName="checked" noStyle>
            <Switch aria-label={`设为默认${typeLabel}`} />
          </Form.Item>
        </div>
        <Form.Item name="remark" label="备注" rules={[{ max: 200 }]}>
          <Input maxLength={200} placeholder="最多 200 字符" />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

/** 转移负责人弹窗（单条 / 批量） */
export const TransferModal: React.FC<{
  open: boolean;
  customers: { id: number; name: string; ownerName?: string }[];
  onClose: () => void;
  onDone: () => void;
}> = ({ open, customers, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [form] = Form.useForm<{ ownerId: number; reason?: string }>();
  const [owners, setOwners] = useState<AssignableOwners['owners']>([]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    customerApi
      .assignableOwners()
      .then((r) => setOwners(r.owners))
      .catch(() => setOwners([]));
  }, [open, form]);

  const submit = async () => {
    const v = await form.validateFields();
    setSaving(true);
    try {
      await customerApi.transfer(
        customers.map((c) => c.id),
        v.ownerId,
        v.reason,
      );
      message.success(
        `已转移给${owners.find((o) => o.id === v.ownerId)?.name ?? '新负责人'}`,
      );
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const batch = customers.length > 1;
  const currentOwners = Array.from(
    new Set(customers.map((c) => c.ownerName || '未分配')),
  ).join('、');
  const kv = (label: string, value: string) => (
    <div style={{ marginBottom: 16 }}>
      <div style={{ fontSize: 13, color: palette.mute, marginBottom: 6 }}>
        {label}
      </div>
      <div
        style={{ fontWeight: 600, color: palette.ink, wordBreak: 'break-word' }}
      >
        {value}
      </div>
    </div>
  );
  return (
    <Modal
      open={open}
      title={batch ? `批量转移 ${customers.length} 个客户` : '转移客户'}
      onCancel={onClose}
      onOk={submit}
      okText="确认转移"
      cancelText="取消"
      confirmLoading={saving}
      destroyOnHidden
      width={480}
    >
      {kv(batch ? '已选客户' : '客户', customers.map((c) => c.name).join('、'))}
      {kv('当前负责人', currentOwners)}
      <Form form={form} layout="vertical">
        <Form.Item
          name="ownerId"
          label="新负责人"
          extra="只列出你数据范围内的在职业务员"
          rules={[{ required: true, message: '请选择新负责人' }]}
        >
          <Select
            showSearch={{ optionFilterProp: 'label' }}
            placeholder="请选择业务员"
            options={owners.map((o) => ({
              value: o.id,
              label: o.deptName ? `${o.name} · ${o.deptName}` : o.name,
            }))}
          />
        </Form.Item>
        <Form.Item name="reason" label="转移原因" rules={[{ max: 200 }]}>
          <Input.TextArea
            rows={3}
            maxLength={200}
            placeholder="选填，如：业务员岗位调整"
          />
        </Form.Item>
      </Form>
      <div
        style={{
          padding: '10px 12px',
          borderRadius: 10,
          background: palette.accentSoft,
          fontSize: 12,
          color: palette.sub,
        }}
      >
        转移记录（原负责人、新负责人、操作人、时间、原因）会写入操作日志。
      </div>
    </Modal>
  );
};
