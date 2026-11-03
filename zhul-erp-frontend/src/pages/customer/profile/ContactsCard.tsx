import { PlusOutlined, TeamOutlined } from '@ant-design/icons';
import { App, Button, Checkbox, Form, Input, Modal, Radio } from 'antd';
import React, { useEffect, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { useDictOptions } from '@/utils/dict';
import { Pill } from '../components';
import { CJK_PATTERN, ENGLISH_ONLY_MESSAGE, PHONE_PATTERN } from '../constants';
import { readBizError } from '../service';
import {
  type Contact,
  type ContactPayload,
  PROFILE_DICTS,
  profileApi,
} from './service';

const useContactDicts = () => ({
  job: useDictOptions(PROFILE_DICTS.jobRole, ''),
  decision: useDictOptions(PROFILE_DICTS.decisionRole, ''),
  comm: useDictOptions(PROFILE_DICTS.commStyle, ''),
  disc: useDictOptions(PROFILE_DICTS.disc, ''),
  china: useDictOptions(PROFILE_DICTS.chinaAttitude, ''),
});

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

const EMPTY: ContactPayload = {
  name: '',
  nameCn: '',
  title: '',
  email: '',
  phone: '',
  whatsapp: '',
  otherIm: '',
  jobRole: 0,
  decisionRole: 0,
  commStyle: 0,
  disc: 0,
  chinaAttitude: 0,
  activeHours: '',
  likes: '',
  primary: false,
};

/** 新增 / 编辑联系人（原型 C3）：带 * 的四项有一个联系人填齐，模块④就算完成 */
export const ContactModal: React.FC<{
  open: boolean;
  customerId: number;
  customerName: string;
  contact?: Contact;
  /** 第一个联系人默认是主联系人 */
  first: boolean;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, customerId, customerName, contact, first, onClose, onDone }) => {
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const d = useContactDicts();
  const [form] = Form.useForm<ContactPayload>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue(
      contact ? { ...EMPTY, ...contact } : { ...EMPTY, primary: first },
    );
  }, [open, contact, first, form]);

  const submit = async () => {
    const v = await form.validateFields();
    const body: ContactPayload = { ...EMPTY, ...v };
    setSaving(true);
    try {
      if (contact) await profileApi.updateContact(customerId, contact.id, body);
      else await profileApi.createContact(customerId, body);
      message.success(contact ? '联系人已保存' : '联系人已添加');
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const remove = () => {
    if (!contact) return;
    modal.confirm({
      title: `删除联系人 ${contact.name}？`,
      content: '删除后不再显示，已有单据上的联系人信息不受影响。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await profileApi.removeContact(customerId, contact.id);
          message.success('联系人已删除');
          onDone();
        } catch (e) {
          message.error(readBizError(e).message);
          throw e;
        }
      },
    });
  };

  const radios = (options: { value: number; label: string }[]) => (
    <Radio.Group optionType="button" size="small" options={options} />
  );

  return (
    <Modal
      open={open}
      title={
        <div>
          {contact ? '编辑联系人' : '添加联系人'}
          <div style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}>
            {customerName}
          </div>
        </div>
      }
      onCancel={onClose}
      width={760}
      destroyOnHidden
      footer={
        <div style={{ display: 'flex', gap: 8 }}>
          {contact && (
            <Button danger onClick={remove} style={{ marginRight: 'auto' }}>
              删除
            </Button>
          )}
          <Button onClick={onClose} style={{ marginLeft: 'auto' }}>
            取消
          </Button>
          <Button type="primary" loading={saving} onClick={submit}>
            保存
          </Button>
        </div>
      }
    >
      <Form form={form} layout="vertical" style={{ marginTop: 12 }}>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
            columnGap: 16,
          }}
        >
          <Form.Item
            name="name"
            label="姓名（英文）"
            rules={[
              { required: true, whitespace: true, message: '请填写联系人姓名' },
              englishOnly,
              { max: 100 },
            ]}
          >
            <Input placeholder="例如 Liam Chen" />
          </Form.Item>
          <Form.Item
            name="nameCn"
            label="中文名"
            rules={[{ max: 100 }]}
            extra="保存后由 AI 翻译填入，不覆盖你手填的；想重新翻译就清空后保存"
          >
            <Input placeholder="留空自动翻译" />
          </Form.Item>
          <Form.Item
            name="title"
            label="职位"
            rules={[englishOnly, { max: 50 }]}
          >
            <Input placeholder="例如 Senior Automation Engineer" />
          </Form.Item>
          <Form.Item
            name="email"
            label="邮箱"
            rules={[{ type: 'email', message: '邮箱格式不正确' }, { max: 100 }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="whatsapp" label="WhatsApp" rules={[phoneRule]}>
            <Input placeholder="+61 412 555 018" />
          </Form.Item>
          <Form.Item name="phone" label="电话" rules={[phoneRule]}>
            <Input />
          </Form.Item>
          <Form.Item name="otherIm" label="其他联系方式" rules={[{ max: 100 }]}>
            <Input placeholder="WeChat / Skype 等" />
          </Form.Item>
        </div>
        <h3 style={{ margin: '4px 0 12px', fontSize: 14, color: palette.ink }}>
          决策人画像
        </h3>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))',
            columnGap: 16,
          }}
        >
          <Form.Item name="jobRole" label="职位类别" required>
            {radios(d.job.options)}
          </Form.Item>
          <Form.Item
            name="decisionRole"
            label="决策权重"
            required
            extra="搞定建议者往往比搞定采购更有用"
          >
            {radios(d.decision.options)}
          </Form.Item>
          <Form.Item name="commStyle" label="沟通风格" required>
            {radios(d.comm.options)}
          </Form.Item>
          <Form.Item name="disc" label="性格（DISC）">
            {radios(d.disc.options)}
          </Form.Item>
          <Form.Item name="chinaAttitude" label="对华态度">
            {radios(d.china.options)}
          </Form.Item>
          <Form.Item
            name="activeHours"
            label="活跃时间（北京时间）"
            extra="如：他习惯下午 3 点回复"
            rules={[{ max: 50 }]}
          >
            <Input placeholder="6:00–14:00" />
          </Form.Item>
        </div>
        <Form.Item name="likes" label="禁忌与喜好" rules={[{ max: 300 }]}>
          <Input.TextArea
            rows={2}
            placeholder="周末不要打扰；喜欢看技术参数表和实拍视频"
          />
        </Form.Item>
        {!contact?.primary && (
          <Form.Item
            name="primary"
            valuePropName="checked"
            style={{ marginBottom: 0 }}
          >
            <Checkbox>设为主联系人</Checkbox>
          </Form.Item>
        )}
      </Form>
    </Modal>
  );
};

/** 联系人卡片（原型 C1 右侧）：主联系人在前，下面是决策人画像标签 */
const ContactsCard: React.FC<{
  customerId: number;
  customerName: string;
  contacts: Contact[];
  /** 模块④缺的项 */
  missing: string[];
  canEdit: boolean;
  onChanged: () => void;
}> = ({ customerId, customerName, contacts, missing, canEdit, onChanged }) => {
  const { palette } = useAppTheme();
  const d = useContactDicts();
  const [editing, setEditing] = useState<Contact | 'new'>();
  const sorted = [...contacts].sort(
    (a, b) => Number(b.primary) - Number(a.primary),
  );

  const tag = (
    text: string,
    tone: 'accent' | 'gray' | 'orange' | 'violet' | 'green' | 'cyan' = 'gray',
  ) => (
    <Pill key={text} tone={tone}>
      {text}
    </Pill>
  );

  return (
    <section
      aria-label="联系人"
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        padding: 20,
        borderRadius: 16,
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <TeamOutlined style={{ color: palette.link }} />
        <h2
          style={{
            margin: 0,
            fontSize: 14,
            fontWeight: 600,
            color: palette.ink,
            whiteSpace: 'nowrap',
          }}
        >
          联系人 {contacts.length}
        </h2>
        {missing.length > 0 && <Pill tone="orange">④ 待补</Pill>}
        {canEdit && (
          <Button
            size="small"
            icon={<PlusOutlined />}
            style={{ marginLeft: 'auto' }}
            onClick={() => setEditing('new')}
          >
            添加
          </Button>
        )}
      </div>
      {missing.length > 0 && (
        <div style={{ fontSize: 12, color: palette.orange }}>
          还缺：至少一个联系人填齐{missing.join('、')}
        </div>
      )}
      {sorted.length === 0 && (
        <div style={{ fontSize: 13, color: palette.mute }}>
          还没有联系人。添加时顺手填上职位类别、决策权重和沟通风格。
        </div>
      )}
      {sorted.map((c) => (
        <div
          key={c.id}
          style={{
            padding: '12px 14px',
            borderRadius: 12,
            background: palette.inset,
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              flexWrap: 'wrap',
            }}
          >
            <b style={{ color: palette.ink }}>{c.name}</b>
            {c.nameCn && <span style={{ color: palette.sub }}>{c.nameCn}</span>}
            {c.primary && tag('主联系人', 'accent')}
            {!!c.jobRole && tag(d.job.labelOf(c.jobRole))}
            {canEdit && (
              <a
                style={{ marginLeft: 'auto', fontSize: 12 }}
                onClick={() => setEditing(c)}
              >
                编辑
              </a>
            )}
          </div>
          <div
            style={{
              marginTop: 4,
              fontSize: 12,
              color: palette.sub,
              wordBreak: 'break-word',
            }}
          >
            {[c.title, c.email, c.whatsapp && `WhatsApp ${c.whatsapp}`, c.phone]
              .filter(Boolean)
              .join(' · ') || '—'}
          </div>
          <div
            style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 8 }}
          >
            {c.decisionRole
              ? tag(d.decision.labelOf(c.decisionRole), 'violet')
              : tag('决策权重未填', 'orange')}
            {c.commStyle
              ? tag(d.comm.labelOf(c.commStyle))
              : tag('沟通风格未填', 'orange')}
            {!!c.disc && tag(d.disc.labelOf(c.disc), 'cyan')}
            {!!c.chinaAttitude &&
              tag(d.china.labelOf(c.chinaAttitude), 'green')}
            {c.activeHours && tag(`活跃 北京 ${c.activeHours}`)}
          </div>
          {c.likes && (
            <div style={{ marginTop: 6, fontSize: 12, color: palette.mute }}>
              {c.likes}
            </div>
          )}
        </div>
      ))}
      <div style={{ fontSize: 12, color: palette.mute }}>
        个人画像只在系统内显示，不出现在客户导出里。
      </div>
      <ContactModal
        open={!!editing}
        customerId={customerId}
        customerName={customerName}
        contact={editing === 'new' ? undefined : editing}
        first={contacts.length === 0}
        onClose={() => setEditing(undefined)}
        onDone={() => {
          setEditing(undefined);
          onChanged();
        }}
      />
    </section>
  );
};

export default ContactsCard;
