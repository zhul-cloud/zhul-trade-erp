import { ThunderboltOutlined } from '@ant-design/icons';
import {
  App,
  Checkbox,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Rate,
  Segmented,
  Select,
} from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { useCountries } from '@/pages/product/components/useCountries';
import { useAppTheme } from '@/theme/AppTheme';
import {
  INDUSTRY_OPTIONS,
  PAYMENT_OPTIONS,
  PAYMENT_WITH_DAYS,
  PAYMENT_WITH_DEPOSIT,
  ROLE_OPTIONS,
} from '../constants';
import { loadCountryTimezones, readBizError } from '../service';
import { allZones, zoneLabel } from '../time';
import { useProfileDicts } from './ProfileModules';
import {
  MODULE_SHORT,
  type Profile,
  type ProfilePatch,
  profileApi,
} from './service';

/** 每个页签能改的字段；④决策人在联系人里维护 */
const TAB_FIELDS: Record<number, string[]> = {
  1: [
    'customerRole',
    'industry',
    'website',
    'linkedin',
    'socialUrl',
    'companySize',
    'potentialRating',
    'foundedYear',
  ],
  2: [
    'mainProducts',
    'qualityReq',
    'priceSensitivity',
    'customsAbility',
    'paymentMethod',
    'estAnnualPurchase',
    'cnSuppliers',
  ],
  3: [
    'currentBrands',
    'designAbility',
    'protocols',
    'voltages',
    'certifications',
    'repairAbility',
  ],
  5: ['city', 'timezone'],
};
/** 编辑弹窗能改的画像字段 */
export const ALL_FIELDS = Object.values(TAB_FIELDS).flat();

type FormState = Record<string, unknown> & {
  currentBrands: string[];
  paymentMethod?: number;
};

const toForm = (p: Profile): FormState => {
  const v: FormState = { currentBrands: p.currentBrands.map((b) => b.name) };
  for (const k of ALL_FIELDS) {
    if (k === 'currentBrands') continue;
    const raw = (p as unknown as Record<string, unknown>)[k];
    // 0 表示没填，表单里显示为空
    v[k] = raw === 0 || raw === '' ? undefined : raw;
  }
  v.depositRatio = p.depositRatio ?? undefined;
  v.paymentDays = p.paymentDays ?? undefined;
  return v;
};

const same = (a: unknown, b: unknown) =>
  JSON.stringify(a ?? null) === JSON.stringify(b ?? null);

/** 编辑客户档案（原型 C2）：按模块分页签，只提交改过的字段，改过的字段来源记为「人工」 */
const ModuleEditModal: React.FC<{
  open: boolean;
  module: number;
  profile: Profile;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, module, profile: p, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const d = useProfileDicts();
  const { codeOf, zhOf } = useCountries();
  const [form] = Form.useForm<FormState>();
  const [tab, setTab] = useState(module);
  const [saving, setSaving] = useState(false);
  const [tzMap, setTzMap] = useState<Record<string, string[]>>({});
  const initial = useMemo(() => toForm(p), [p]);
  const paymentMethod = Form.useWatch('paymentMethod', form);
  const brands = Form.useWatch('currentBrands', form) ?? [];

  useEffect(() => {
    if (!open) return;
    setTab(module === 4 ? 1 : module);
    form.resetFields();
    form.setFieldsValue(initial);
  }, [open, module, initial, form]);

  useEffect(() => {
    loadCountryTimezones()
      .then(setTzMap)
      .catch(() => undefined);
  }, []);

  const zoneOptions = useMemo(() => {
    const code = codeOf(p.country);
    const zones = (code && tzMap[code]) || [];
    return (zones.length ? zones : allZones()).map((z) => ({
      value: z,
      label: zoneLabel(z),
    }));
  }, [p.country, tzMap, codeOf]);

  const submit = async () => {
    try {
      await form.validateFields();
    } catch (e) {
      // 校验没过的字段可能在别的页签，切过去
      const first = (e as { errorFields?: { name: (string | number)[] }[] })
        .errorFields?.[0]?.name[0];
      const t = Object.entries(TAB_FIELDS).find(
        ([, keys]) =>
          keys.includes(String(first)) ||
          (keys.includes('paymentMethod') &&
            ['depositRatio', 'paymentDays'].includes(String(first))),
      );
      if (t) setTab(Number(t[0]));
      return;
    }
    const all = { ...initial, ...form.getFieldsValue(true) } as FormState;
    const fields = ALL_FIELDS.filter((k) =>
      k === 'paymentMethod'
        ? !same(all.paymentMethod, initial.paymentMethod) ||
          !same(all.depositRatio, initial.depositRatio) ||
          !same(all.paymentDays, initial.paymentDays)
        : !same(all[k], initial[k]),
    );
    if (fields.length === 0) {
      onClose();
      return;
    }
    const known = new Map(
      p.currentBrands.map((b) => [b.name.toLowerCase(), b]),
    );
    const patch = {
      ...Object.fromEntries(ALL_FIELDS.map((k) => [k, all[k] ?? null])),
      currentBrands: (all.currentBrands ?? []).map(
        (name) => known.get(name.toLowerCase()) ?? { name },
      ),
      depositRatio: all.depositRatio ?? null,
      paymentDays: all.paymentDays ?? null,
      fields,
    } as unknown as ProfilePatch;
    setSaving(true);
    try {
      await profileApi.update(p.customerId, patch);
      message.success(`已保存 ${fields.length} 项`);
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const radios = (options: { value: number; label: string }[]) => (
    <Radio.Group optionType="button" options={options} />
  );
  const checks = (options: { value: number; label: string }[]) => (
    <Checkbox.Group options={options} />
  );
  const derived = p.derived.topBrands.filter(
    (b) => !brands.some((x) => x.toLowerCase() === b.name.toLowerCase()),
  );
  const cp = p.countryProfile;
  const missing = p.completeness.missing.find((m) => m.module === tab);
  const show = (t: number): React.CSSProperties => ({
    display: tab === t ? undefined : 'none',
  });

  return (
    <Modal
      open={open}
      title={
        <div>
          编辑客户档案
          <div style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}>
            {p.name}
            {missing
              ? ` · ${MODULE_SHORT[tab - 1]} 还缺 ${missing.labels.join('、')}`
              : ''}
          </div>
        </div>
      }
      onCancel={onClose}
      onOk={submit}
      okText="保存"
      cancelText="取消"
      confirmLoading={saving}
      width={860}
      destroyOnHidden
      footer={(_, { OkBtn, CancelBtn }) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span
            style={{ marginRight: 'auto', fontSize: 12, color: palette.mute }}
          >
            保存后，改过的字段来源记为「人工」
          </span>
          <CancelBtn />
          <OkBtn />
        </div>
      )}
    >
      <Segmented
        value={tab}
        onChange={(v) => setTab(Number(v))}
        style={{ margin: '8px 0 20px' }}
        options={[1, 2, 3, 4, 5].map((m) => ({
          value: m,
          label: `${'①②③④⑤'[m - 1]} ${MODULE_SHORT[m - 1]}`,
        }))}
      />
      <Form form={form} layout="vertical" requiredMark>
        <div style={show(1)}>
          <Form.Item name="customerRole" label="公司类型" required>
            {radios(ROLE_OPTIONS)}
          </Form.Item>
          <Form.Item name="industry" label="行业" required>
            <Select
              allowClear
              placeholder="请选择行业"
              options={INDUSTRY_OPTIONS}
              style={{ maxWidth: 320 }}
            />
          </Form.Item>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
              columnGap: 16,
            }}
          >
            <Form.Item
              name="website"
              label="官网"
              required
              extra="官网和 LinkedIn 填一个即可"
              rules={[{ max: 200 }]}
            >
              <Input placeholder="example.com" />
            </Form.Item>
            <Form.Item
              name="linkedin"
              label="LinkedIn 公司页"
              rules={[{ max: 200 }]}
            >
              <Input placeholder="https://www.linkedin.com/company/..." />
            </Form.Item>
          </div>
          <Form.Item name="companySize" label="公司规模" required>
            {radios(d.size.options)}
          </Form.Item>
          <Form.Item
            name="potentialRating"
            label="潜力评级"
            required
            extra="值不值得跟：5 星最值得"
          >
            <Rate />
          </Form.Item>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
              columnGap: 16,
            }}
          >
            <Form.Item name="foundedYear" label="成立年份">
              <InputNumber
                min={1800}
                max={new Date().getFullYear()}
                precision={0}
                style={{ width: '100%' }}
              />
            </Form.Item>
            <Form.Item name="socialUrl" label="其他社媒" rules={[{ max: 200 }]}>
              <Input placeholder="Facebook / Instagram 等" />
            </Form.Item>
          </div>
        </div>

        <div style={show(2)}>
          <Form.Item
            name="mainProducts"
            label="主营产品 / 业务"
            required
            rules={[{ max: 300 }]}
          >
            <Input.TextArea
              rows={2}
              placeholder="例如：水厂与泵站自动化改造、控制柜集成"
            />
          </Form.Item>
          <Form.Item name="qualityReq" label="质量要求" required>
            {radios(d.quality.options)}
          </Form.Item>
          <Form.Item name="priceSensitivity" label="价格敏感度" required>
            {radios(d.price.options)}
          </Form.Item>
          <Form.Item name="customsAbility" label="清关能力" required>
            {radios(d.customs.options)}
          </Form.Item>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '2fr 1fr',
              columnGap: 16,
            }}
          >
            <Form.Item
              name="paymentMethod"
              label="付款习惯"
              required
              extra="即客户的默认付款方式"
            >
              <Select
                allowClear
                placeholder="请选择"
                options={PAYMENT_OPTIONS}
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
                />
              </Form.Item>
            )}
          </div>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
              columnGap: 16,
            }}
          >
            <Form.Item name="estAnnualPurchase" label="年度采购量估算（USD）">
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="cnSuppliers"
              label="现有中国供应商"
              rules={[{ max: 300 }]}
            >
              <Input />
            </Form.Item>
          </div>
        </div>

        <div style={show(3)}>
          <Form.Item
            name="currentBrands"
            label="现有使用品牌"
            required
            extra={
              derived.length > 0 && (
                <span style={{ color: palette.cyan }}>
                  <ThunderboltOutlined /> 询盘里常用：
                  {derived.map((b) => `${b.name} ${b.count}`).join(' · ')}{' '}
                  <a
                    onClick={() =>
                      form.setFieldValue('currentBrands', [
                        ...brands,
                        ...derived.map((b) => b.name),
                      ])
                    }
                  >
                    一键加入
                  </a>
                </span>
              )
            }
          >
            <Select
              mode="tags"
              placeholder="输入品牌后回车"
              tokenSeparators={[',', '，']}
              open={false}
            />
          </Form.Item>
          <Form.Item
            name="designAbility"
            label="设计能力"
            required
            extra="没有工程师团队的客户，报价时可以推编程服务"
          >
            {radios(d.design.options)}
          </Form.Item>
          <Form.Item name="protocols" label="常用通讯协议" required>
            {checks(d.protocol.options)}
          </Form.Item>
          <Form.Item
            name="voltages"
            label="电源电压"
            required
            extra={
              cp?.voltages.length ? (
                <span style={{ color: palette.cyan }}>
                  不填时用国家资料：{zhOf(p.country)}{' '}
                  {cp.voltages.map((v) => d.voltage.labelOf(v)).join(' · ')}
                </span>
              ) : undefined
            }
          >
            {checks(d.voltage.options)}
          </Form.Item>
          <Form.Item name="certifications" label="认证强制要求">
            {checks(d.cert.options)}
          </Form.Item>
          <Form.Item name="repairAbility" label="售后维修能力" required>
            {radios(d.repair.options)}
          </Form.Item>
        </div>

        <div style={{ ...show(4), padding: '24px 0', color: palette.sub }}>
          决策人画像按人填写：在详情页右侧「联系人」里添加或编辑联系人，填上职位类别、决策权重和沟通风格，有一个人填齐模块④就算完成。
        </div>

        <div style={show(5)}>
          <div style={{ marginBottom: 16, fontSize: 13, color: palette.sub }}>
            国家：{zhOf(p.country) || '未填'}（在客户编辑里修改）
          </div>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
              columnGap: 16,
            }}
          >
            <Form.Item name="city" label="城市" required rules={[{ max: 100 }]}>
              <Input placeholder="例如 Sydney" />
            </Form.Item>
            <Form.Item name="timezone" label="时区">
              <Select
                allowClear
                showSearch={{ optionFilterProp: 'label' }}
                options={zoneOptions}
                placeholder="请选择时区"
              />
            </Form.Item>
          </div>
          <div style={{ fontSize: 12, color: palette.mute }}>
            电压、插头、节假日、外汇管制是国家层面的，在「业务设置 →
            国家资料」统一维护。
          </div>
        </div>
      </Form>
    </Modal>
  );
};

export default ModuleEditModal;
