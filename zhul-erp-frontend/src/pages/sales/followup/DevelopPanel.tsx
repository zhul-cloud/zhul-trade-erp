import { FlagOutlined, RetweetOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  DatePicker,
  Form,
  Input,
  Modal,
  Radio,
  Select,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import { useCountries } from '@/pages/product/components/useCountries';
import { getUserList } from '@/pages/system/user/service';
import { useAppTheme } from '@/theme/AppTheme';
import { UnitSelect, useBusinessUnits } from '@/utils/businessUnit';
import { fmtDate, iso, Pill } from './components';
import {
  type DevelopCard,
  ERROR_CUSTOMER_DUPLICATE,
  ERROR_OPPORTUNITY_ACTIVE,
  type FollowupItem,
  followupApi,
  PROSPECT_SOURCES,
  REPORT_PRIORITY,
  readBizError,
  STOP,
} from './service';

// ---------------------------------------------------------------- 作战卡（K17 右栏）

/** 选中行的开发记录详情；行或数据变化时重新取 */
export const useDevelopCard = (customerId?: number, version?: unknown) => {
  const [card, setCard] = useState<DevelopCard>();
  useEffect(() => {
    setCard(undefined);
    if (!customerId) return;
    let alive = true;
    followupApi
      .develop(customerId)
      .then((c) => alive && setCard(c))
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, [customerId, version]);
  return card;
};

const Box: React.FC<{ title: string; children: React.ReactNode }> = ({
  title,
  children,
}) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        marginTop: 12,
        padding: '10px 12px',
        borderRadius: 10,
        background: palette.inset,
      }}
    >
      <div style={{ fontSize: 12, color: palette.mute, marginBottom: 4 }}>
        {title}
      </div>
      <div style={{ fontSize: 13, color: palette.ink, lineHeight: 1.6 }}>
        {children}
      </div>
    </div>
  );
};

/** 为什么值得开发 · 背调结论、联系人 */
export const DevelopInfo: React.FC<{ card?: DevelopCard }> = ({ card }) => {
  const { palette } = useAppTheme();
  if (!card) return null;
  const r = card.report;
  const priority = r?.priority ? REPORT_PRIORITY[r.priority] : undefined;
  return (
    <>
      <Box title="为什么值得开发 · 背调结论">
        <b>{card.prospectReason || '—'}</b>
        {card.prospectSourceName && `（${card.prospectSourceName}）`}
        {r?.status === 1 && (
          <div style={{ color: palette.mute }}>背调进行中…</div>
        )}
        {r?.status === 2 && (
          <div>
            {r.score ? `背调 ${r.score} 分` : '背调已完成'}
            {priority && ` · ${priority.label}`}
            {r.keyFinding && `；关键发现：${r.keyFinding}`}
            {r.firstAction && `；第一步：${r.firstAction}`}
          </div>
        )}
        {!r && (
          <div style={{ color: palette.mute }}>
            还没有背调，可在客户详情发起背调或手动评潜力星级
          </div>
        )}
      </Box>
      <Box title="联系人">
        {card.contacts.length === 0 ? (
          <span style={{ color: palette.mute }}>
            还没有联系人，找到采购、维修或技术负责人后在客户详情添加
          </span>
        ) : (
          card.contacts.map((c, i) => (
            <span
              key={`${c.name ?? ''}|${c.title ?? ''}|${c.email ?? ''}|${c.whatsapp ?? ''}|${c.phone ?? ''}`}
            >
              {i > 0 && '；'}
              {c.name || '（未填姓名）'}
              {c.title && ` · ${c.title}`}
              {c.primary && '（主联系人）'}
              {(c.email || c.whatsapp || c.phone) &&
                ` ${[c.email, c.whatsapp, c.phone].filter(Boolean)[0]}`}
            </span>
          ))
        )}
      </Box>
    </>
  );
};

/** 跟进序列：第 1 / 4 / 9 / 16 天，已发几封、现在在哪一封 */
export const DevelopSequence: React.FC<{ card?: DevelopCard }> = ({ card }) => {
  const { palette } = useAppTheme();
  if (!card?.sequence?.length) return null;
  if (card.stageCode !== 'D4' && card.stageCode !== 'D5') return null;
  const first = card.firstTouchDate ? dayjs(card.firstTouchDate) : undefined;
  const today = dayjs().startOf('day');
  return (
    <div
      style={{
        marginTop: 14,
        padding: '10px 12px',
        borderRadius: 10,
        background: palette.violetSoft,
      }}
    >
      <div style={{ fontWeight: 600, fontSize: 13, color: palette.ink }}>
        <RetweetOutlined style={{ color: palette.violet }} /> 跟进序列（第{' '}
        {card.sequence.join(' / ')} 天 · 已发 {card.touchCount ?? 0} 封）
      </div>
      <ul style={{ margin: '6px 0 0', paddingLeft: 18, fontSize: 12 }}>
        {card.sequence.map((d, i) => {
          const date = first?.add(d - 1, 'day');
          const now = card.currentDay === d;
          const done = !!date && date.isBefore(today) && !now;
          return (
            <li
              key={d}
              style={{
                color: now ? palette.violet : palette.sub,
                fontWeight: now ? 600 : 400,
              }}
            >
              第 {d} 天{date ? ` ${date.format('MM-DD')}` : ''}：
              {i === 0
                ? '个性化首封，提到对方的业务'
                : i === (card.sequence?.length ?? 0) - 1
                  ? '收尾邮件，问是否应该联系其他负责人'
                  : '换一个新价值点（现货、替代型号、案例）'}
              {now ? '（现在）' : done || (i === 0 && first) ? '（已过）' : ''}
            </li>
          );
        })}
      </ul>
    </div>
  );
};

/** 出现采购信号就建商机；停止开发 */
export const DevelopActions: React.FC<{
  card?: DevelopCard;
  canRecord: boolean;
  onConvert: () => void;
  onStop: () => void;
}> = ({ card, canRecord, onConvert, onStop }) => {
  const { palette } = useAppTheme();
  if (!card || card.state !== 'open') return null;
  return (
    <div style={{ marginTop: 14 }}>
      <div style={{ fontWeight: 600, fontSize: 13, color: palette.ink }}>
        <FlagOutlined style={{ color: palette.violet }} /> 出现采购信号就建商机
      </div>
      <div style={{ fontSize: 12, color: palette.sub, margin: '4px 0 8px' }}>
        客户问到具体型号、数量、价格或交期时点「转为商机」：新建 S1
        商机（来源「主动开发」），需求摘要默认带上采购信号；开发记录结束，客户进入「商机成交」。
      </div>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
        <Button
          type="primary"
          disabled={!canRecord || !card.canConvert}
          title={
            card.canConvert ? undefined : '找到联系人、开始触达以后才能转商机'
          }
          onClick={onConvert}
        >
          转为商机
        </Button>
        <Button type="link" disabled={!canRecord} onClick={onStop}>
          停止开发
        </Button>
      </div>
    </div>
  );
};

// ---------------------------------------------------------------- 转为商机（K18）

export const ConvertOpportunityModal: React.FC<{
  item?: FollowupItem;
  onClose: () => void;
  onDone: () => void;
}> = ({ item, onClose, onDone }) => {
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  const [dateMode, setDateMode] = useState<'first' | 'today' | 'pick'>('first');
  const card = useDevelopCard(item?.customerId);

  useEffect(() => {
    if (!item) return;
    getUserList({ current: 1, pageSize: 200 })
      .then((r) =>
        setUsers(r.data.records.map((u) => ({ value: u.id, label: u.name }))),
      )
      .catch(() => setUsers([]));
  }, [item]);
  useEffect(() => {
    if (!card) return;
    setDateMode(card.firstTouchDate ? 'first' : 'today');
    form.setFieldsValue({
      demandSummary: card.signalText || '',
      pickDate: undefined,
      ownerId: undefined,
    });
  }, [card, form]);

  const submit = async () => {
    if (!item) return;
    const v = await form.validateFields();
    const date: Dayjs =
      dateMode === 'first' && card?.firstTouchDate
        ? dayjs(card.firstTouchDate)
        : dateMode === 'pick' && v.pickDate
          ? v.pickDate
          : dayjs();
    setSaving(true);
    try {
      await followupApi.convert(item.id, {
        demandSummary: v.demandSummary?.trim() || undefined,
        firstContactDate: iso(date),
        ownerId: v.ownerId,
      });
      message.success('已转为商机，客户进入「商机成交」');
      onDone();
    } catch (e) {
      const err = readBizError(e);
      if (err.errorCode === ERROR_OPPORTUNITY_ACTIVE) {
        modal.confirm({
          title: '这个客户已有进行中的商机',
          content: err.message,
          okText: '打开已有商机',
          onOk: () =>
            history.push(`/crm/opportunities/${err.detail?.opportunityId}`),
        });
      } else {
        message.error(err.message);
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title="转为商机"
      open={!!item}
      onCancel={onClose}
      onOk={submit}
      okText="转为商机"
      okButtonProps={{ loading: saving, disabled: !card }}
      destroyOnHidden
    >
      <div style={{ marginBottom: 12 }}>
        <b>{item?.customerName}</b>{' '}
        <Pill tone="violet">
          主动开发 · {item?.stageCode} {item?.stageName}
        </Pill>
        <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
          {card?.firstTouchDate
            ? `首次触达 ${fmtDate(card.firstTouchDate)}`
            : '还没有触达记录'}
          {card?.signalText && ` · 采购信号：${card.signalText}`}
        </div>
      </div>
      <Form form={form} layout="vertical">
        <Form.Item label="首次接触日" required>
          <Radio.Group
            optionType="button"
            value={dateMode}
            onChange={(e) => setDateMode(e.target.value)}
            options={[
              ...(card?.firstTouchDate
                ? [
                    {
                      value: 'first',
                      label: `${fmtDate(card.firstTouchDate)} 首次触达日`,
                    },
                  ]
                : []),
              { value: 'today', label: '今天' },
              { value: 'pick', label: '选日期' },
            ]}
          />
        </Form.Item>
        {dateMode === 'pick' && (
          <Form.Item
            name="pickDate"
            rules={[{ required: true, message: '请选择首次接触日' }]}
          >
            <DatePicker
              style={{ width: '100%' }}
              disabledDate={(d) => d.isAfter(dayjs(), 'day')}
            />
          </Form.Item>
        )}
        <Form.Item
          name="demandSummary"
          label="需求摘要（默认取采购信号，可改）"
        >
          <Input.TextArea rows={2} maxLength={500} showCount />
        </Form.Item>
        <Form.Item
          name="ownerId"
          label="负责人"
          extra="不选为客户负责人；客户没有负责人时为你自己"
        >
          <Select
            allowClear
            showSearch={{ optionFilterProp: 'label' }}
            placeholder="客户负责人"
            options={users}
          />
        </Form.Item>
      </Form>
      <Alert
        type="success"
        showIcon
        title="确认后：新建 S1 商机（来源「主动开发」），开发记录结束（原因「转商机」），写一条系统跟进记录；客户出现在「商机成交」。"
      />
    </Modal>
  );
};

// ---------------------------------------------------------------- 停止开发（K18）

const STOP_GROUPS = [
  {
    key: STOP.REFUSED,
    title: '明确拒绝',
    hint: '客户回复不需要、请不要再联系',
  },
  {
    key: 'STALLED',
    title: '没有进展',
    hint: '选具体情况',
    subs: [
      { value: STOP.MISMATCH, label: '明显不匹配' },
      { value: STOP.NO_REPLY, label: '序列发完仍没回复' },
      { value: STOP.UNREACHABLE, label: '联系不上' },
    ],
  },
  { key: STOP.OTHER, title: '其他', hint: '必须写说明' },
];

export const StopDevelopModal: React.FC<{
  item?: FollowupItem;
  /** 打开时预选的原因（记跟进选了「明确拒绝」时为 REFUSED） */
  preset?: string;
  onClose: () => void;
  onDone: () => void;
}> = ({ item, preset, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [reason, setReason] = useState<string>();
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();

  useEffect(() => {
    if (!item) return;
    setReason(preset ?? (item.suggestNurture ? STOP.NO_REPLY : undefined));
    setNote('');
    setError(undefined);
  }, [item, preset]);

  const submit = async () => {
    if (!item) return;
    if (!reason) {
      setError('请选择停止原因');
      return;
    }
    if (reason === STOP.OTHER && !note.trim()) {
      setError('选「其他」时请写明原因');
      return;
    }
    setSaving(true);
    try {
      await followupApi.stopDevelop(item.id, {
        reason,
        note: note.trim() || undefined,
      });
      message.success('已停止开发，可在客户详情「重新开发」');
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const groupOf = (r?: string) =>
    STOP_GROUPS.find((g) => g.key === r || g.subs?.some((s) => s.value === r))
      ?.key;

  return (
    <Modal
      title="停止开发"
      open={!!item}
      onCancel={onClose}
      onOk={submit}
      okText="停止开发"
      okButtonProps={{ loading: saving }}
      destroyOnHidden
    >
      <div style={{ marginBottom: 12 }}>
        <b>{item?.customerName}</b>{' '}
        <Pill tone="violet">
          主动开发 · {item?.stageCode} {item?.stageName}
        </Pill>
        <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
          已触达 {item?.touchCount ?? 0} 次
          {item?.suggestNurture && ' · 序列发完仍没回复'}
        </div>
      </div>
      <div style={{ fontSize: 13, marginBottom: 6 }}>
        <span style={{ color: palette.red }}>* </span>停止原因
      </div>
      <div style={{ display: 'grid', gap: 8 }}>
        {STOP_GROUPS.map((g) => {
          const on = groupOf(reason) === g.key;
          // 有子选项的分组里放的是单选按钮组，外框不能再是按钮
          const Box = g.subs ? 'div' : 'button';
          return (
            <Box
              key={g.key}
              {...(g.subs
                ? {}
                : { type: 'button', onClick: () => setReason(g.key) })}
              style={{
                width: '100%',
                textAlign: 'left',
                font: 'inherit',
                color: 'inherit',
                padding: '10px 12px',
                borderRadius: 10,
                cursor: g.subs ? 'default' : 'pointer',
                border: `1px solid ${on ? palette.link : palette.hairline}`,
                background: on ? palette.accentSoft : palette.card,
              }}
            >
              <div style={{ fontWeight: 600, fontSize: 13 }}>{g.title}</div>
              <div style={{ fontSize: 12, color: palette.mute }}>
                → {g.hint}
              </div>
              {g.subs && (
                <Radio.Group
                  style={{ marginTop: 6 }}
                  size="small"
                  optionType="button"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  options={g.subs}
                />
              )}
            </Box>
          );
        })}
      </div>
      <div style={{ fontSize: 13, margin: '12px 0 6px' }}>
        {reason === STOP.OTHER && (
          <span style={{ color: palette.red }}>* </span>
        )}
        说明{reason === STOP.OTHER ? '' : '（选填）'}
      </div>
      <Input.TextArea
        rows={2}
        maxLength={200}
        showCount
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder="例如：4 封都没有回复，对方主要做矿山设备维修，明年再看"
      />
      {error && (
        <Alert style={{ marginTop: 8 }} type="error" showIcon title={error} />
      )}
      <Alert
        style={{ marginTop: 12 }}
        type="success"
        showIcon
        title="确认后：开发记录结束（原因「停止开发」），写一条系统跟进记录；客户档案保留，可在客户详情「重新开发」，触达次数保留。也可以先「转培育」，到期自动回来。"
      />
    </Modal>
  );
};

// ---------------------------------------------------------------- 新建开发对象 / 加入主动开发（K18）

interface DuplicateHit {
  message: string;
  existingId?: number;
  selectable?: boolean;
}

export const NewProspectModal: React.FC<{
  open: boolean;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [form] = Form.useForm();
  const { options: countryOptions } = useCountries();
  const [bu] = useBusinessUnits();
  const [saving, setSaving] = useState(false);
  const [dup, setDup] = useState<DuplicateHit>();

  useEffect(() => {
    if (open) {
      form.resetFields();
      setDup(undefined);
    }
  }, [open, form]);

  const submit = async () => {
    const v = await form.validateFields();
    setSaving(true);
    setDup(undefined);
    try {
      await followupApi.createProspect({
        ...v,
        name: v.name.trim(),
        reason: v.reason.trim(),
      });
      message.success('已新建开发对象，进入 D1 搜客户');
      onDone();
    } catch (e) {
      const err = readBizError(e);
      if (err.errorCode === ERROR_CUSTOMER_DUPLICATE) {
        setDup({
          message: err.message,
          existingId: err.detail?.existingId as number | undefined,
          selectable: err.detail?.selectable as boolean | undefined,
        });
      } else {
        message.error(err.message);
      }
    } finally {
      setSaving(false);
    }
  };

  /** 疑似重复时把已有客户加入开发（沿用表单里的理由和来源） */
  const joinExisting = async () => {
    if (!dup?.existingId) return;
    const v = form.getFieldsValue();
    setSaving(true);
    try {
      await followupApi.joinDevelop(dup.existingId, {
        reason: String(v.reason ?? '').trim(),
        source: v.source,
      });
      message.success('已把已有客户加入主动开发');
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title="新建开发对象"
      open={open}
      onCancel={onClose}
      onOk={submit}
      okText="新建"
      okButtonProps={{ loading: saving }}
      width={560}
      destroyOnHidden
    >
      <p style={{ marginTop: 0, color: palette.sub, fontSize: 13 }}>
        新建客户（来源「主动开发」，负责人为你），同时生成开发记录，进入 D1
        搜客户。按名称 + 国家、邮箱、WhatsApp
        查重；没有联系人也能建，找到后再补。
      </p>
      <Form form={form} layout="vertical">
        <div
          style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}
        >
          <Form.Item
            name="name"
            label="公司名称"
            rules={[
              { required: true, whitespace: true, message: '请填写公司名称' },
            ]}
          >
            <Input maxLength={200} placeholder="英文名称" />
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
        </div>
        {bu.enabled && bu.myUnitId == null && bu.units.length > 1 && (
          <Form.Item
            name="businessUnitId"
            label="所属事业部"
            extra="你不属于事业部（在共享中台或总经办），新客户须选择归属的事业部"
            rules={[{ required: true, message: '请选择所属事业部' }]}
          >
            <UnitSelect info={bu} />
          </Form.Item>
        )}
        <Form.Item name="website" label="官网">
          <Input maxLength={200} placeholder="https://" />
        </Form.Item>
        <Form.Item
          name="reason"
          label="为什么值得开发"
          rules={[
            { required: true, whitespace: true, message: '请写为什么值得开发' },
          ]}
        >
          <Input.TextArea
            rows={2}
            maxLength={200}
            showCount
            placeholder="例如：官网写明 Siemens S7 维修服务"
          />
        </Form.Item>
        <Form.Item name="source" label="来源方式">
          <Radio.Group optionType="button" options={PROSPECT_SOURCES} />
        </Form.Item>
        <div
          style={{ fontSize: 13, color: palette.mute, margin: '-4px 0 8px' }}
        >
          联系人（选填，不填则不建联系人）
        </div>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr 1fr',
            gap: 12,
          }}
        >
          <Form.Item name="contactName" label="姓名">
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item
            name="email"
            label="邮箱"
            rules={[{ type: 'email', message: '邮箱格式不正确' }]}
          >
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item name="whatsapp" label="WhatsApp">
            <Input maxLength={30} />
          </Form.Item>
        </div>
      </Form>
      {dup && (
        <Alert
          type="warning"
          showIcon
          title={dup.message}
          description={
            dup.existingId && (
              <div style={{ display: 'flex', gap: 16, marginTop: 4 }}>
                {dup.selectable && (
                  <a onClick={joinExisting}>把这个客户加入开发</a>
                )}
                {dup.selectable && (
                  <a
                    onClick={() =>
                      history.push(`/customer/list/${dup.existingId}`)
                    }
                  >
                    打开客户
                  </a>
                )}
              </div>
            )
          }
        />
      )}
    </Modal>
  );
};

/** 客户详情「加入主动开发」：写为什么值得开发，来源方式选填 */
export const JoinDevelopModal: React.FC<{
  customerId?: number;
  customerName?: string;
  onClose: () => void;
  onDone: () => void;
}> = ({ customerId, customerName, onClose, onDone }) => {
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (customerId) form.resetFields();
  }, [customerId, form]);
  const submit = async () => {
    if (!customerId) return;
    const v = await form.validateFields();
    setSaving(true);
    try {
      await followupApi.joinDevelop(customerId, {
        reason: v.reason.trim(),
        source: v.source,
      });
      message.success('已加入主动开发');
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      title={`加入主动开发${customerName ? ` · ${customerName}` : ''}`}
      open={!!customerId}
      onCancel={onClose}
      onOk={submit}
      okText="加入"
      okButtonProps={{ loading: saving }}
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="reason"
          label="为什么值得开发"
          rules={[
            { required: true, whitespace: true, message: '请写为什么值得开发' },
          ]}
        >
          <Input.TextArea rows={2} maxLength={200} showCount />
        </Form.Item>
        <Form.Item name="source" label="来源方式">
          <Radio.Group optionType="button" options={PROSPECT_SOURCES} />
        </Form.Item>
      </Form>
    </Modal>
  );
};
