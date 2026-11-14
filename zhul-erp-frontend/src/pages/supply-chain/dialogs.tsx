import { InfoCircleOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Checkbox,
  DatePicker,
  Input,
  InputNumber,
  Modal,
  Radio,
  Segmented,
  Select,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { CoverPill, useOptions, useUsers } from './components';
import { classifyPlan, nextOptional, recommendDate } from './rules';
import {
  COVER,
  COVER_HINTS,
  COVER_NAMES,
  type Demand,
  modelName,
  newKey,
  PRIORITY_NAMES,
  readBizError,
  type Signal,
  SRC,
  SRC_NAMES,
  supplyApi,
} from './service';

const iso = (d?: Dayjs | null) => (d ? d.format('YYYY-MM-DD') : undefined);

const Label: React.FC<{
  children: React.ReactNode;
  extra?: React.ReactNode;
}> = ({ children, extra }) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        gap: 12,
        margin: '16px 0 8px',
        color: palette.sub,
        fontSize: 13,
      }}
    >
      <span>{children}</span>
      {extra && <span style={{ color: palette.mute }}>{extra}</span>}
    </div>
  );
};

const Hint: React.FC<{ children: React.ReactNode; tone?: 'warn' | 'link' }> = ({
  children,
  tone,
}) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        marginTop: 6,
        fontSize: 12,
        color:
          tone === 'warn'
            ? palette.orange
            : tone === 'link'
              ? palette.link
              : palette.mute,
      }}
    >
      {children}
    </div>
  );
};

/** 可勾选的型号行（记处理、建专项共用） */
const ModelRow: React.FC<{
  d: Demand;
  checked: boolean;
  onChange: (v: boolean) => void;
  note?: React.ReactNode;
}> = ({ d, checked, onChange, note }) => {
  const { palette } = useAppTheme();
  return (
    // biome-ignore lint/a11y/noLabelWithoutControl: antd Checkbox 在 label 内渲染 input
    <label
      style={{
        display: 'grid',
        gridTemplateColumns: '24px minmax(0, 1fr) auto minmax(0, 1.2fr)',
        alignItems: 'center',
        gap: 12,
        padding: '10px 12px',
        borderRadius: 10,
        background: palette.inset,
        cursor: 'pointer',
      }}
    >
      <Checkbox
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      <b
        style={{
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {modelName(d)}
      </b>
      <CoverPill status={d.coverageStatus || undefined} />
      <span style={{ fontSize: 12, color: palette.mute }}>{note}</span>
    </label>
  );
};

// ---------------------------------------------------------------- 记一次处理（F5）

export const RecordModal: React.FC<{
  open: boolean;
  /** 标题下的范围「品牌 · 品类」 */
  title?: string;
  /** 可选的型号；为空时可搜索添加 */
  demands?: Demand[];
  /** 默认勾选的型号，不传则全部勾选 */
  checkedIds?: number[];
  gapProjectId?: number;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, title, demands, checkedIds, gapProjectId, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [rows, setRows] = useState<Demand[]>([]);
  const [checked, setChecked] = useState<number[]>([]);
  const [content, setContent] = useState('');
  const [coverage, setCoverage] = useState(0);
  const [nextAction, setNextAction] = useState('');
  const [nextDate, setNextDate] = useState<Dayjs | null>(null);
  const [options, setOptions] = useState<Demand[]>([]);
  const [saving, setSaving] = useState(false);
  const keyRef = useRef(newKey());

  useEffect(() => {
    if (!open) return;
    keyRef.current = newKey();
    setRows(demands ?? []);
    setChecked(checkedIds ?? (demands ?? []).map((d) => d.id));
    setContent('');
    setCoverage(0);
    setNextAction('');
    setNextDate(null);
  }, [open, demands, checkedIds]);

  const search = (keyword: string) => {
    if (!keyword.trim()) return;
    supplyApi
      .models({ keyword, all: true, size: 20 })
      .then((p) => setOptions(p.records))
      .catch(() => setOptions([]));
  };

  const picked = rows.filter((r) => checked.includes(r.id));
  const recommend = recommendDate(
    coverage || picked[0]?.coverageStatus || undefined,
    picked,
  );
  const noEvidence = (d: Demand) =>
    coverage === COVER.READY && !d.evidenceValid;

  const submit = async () => {
    if (!checked.length) {
      message.warning('至少选一个型号');
      return;
    }
    if (!content.trim()) {
      message.warning('请填写做了什么');
      return;
    }
    if (
      (!nextAction.trim() || !nextDate) &&
      !nextOptional(coverage || undefined, picked)
    ) {
      message.warning('请填写下一步行动与日期');
      return;
    }
    setSaving(true);
    try {
      const r = await supplyApi.record({
        demandIds: checked,
        content: content.trim(),
        coverage: coverage || undefined,
        nextAction: nextAction.trim() || undefined,
        nextActionDate: iso(nextDate),
        gapProjectId,
        idempotencyKey: keyRef.current,
      });
      if (r.skipped?.length)
        message.warning(
          `已保存；${r.skipped.length} 个型号没有有效证据，保持原状态`,
        );
      else message.success('已记录');
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title="记一次处理"
      open={open}
      width={640}
      onCancel={onClose}
      onOk={submit}
      okText="保存"
      okButtonProps={{ loading: saving }}
      destroyOnHidden
    >
      {title && (
        <div style={{ fontSize: 18, fontWeight: 600, marginTop: 8 }}>
          {title}
        </div>
      )}
      <Label>这次处理涉及的型号</Label>
      <div style={{ display: 'grid', gap: 8 }}>
        {rows.map((d) => (
          <ModelRow
            key={d.id}
            d={d}
            checked={checked.includes(d.id)}
            onChange={(v) =>
              setChecked((c) =>
                v ? [...c, d.id] : c.filter((x) => x !== d.id),
              )
            }
            note={
              checked.includes(d.id) && noEvidence(d) ? (
                <span style={{ color: palette.orange }}>
                  没有有效证据，保持原状态
                </span>
              ) : (
                [
                  d.cntStockout ? `无货 ${d.cntStockout} 次` : null,
                  d.nextActionDate
                    ? `下一步 ${dayjs(d.nextActionDate).format('MM-DD')}`
                    : null,
                ]
                  .filter(Boolean)
                  .join(' · ')
              )
            }
          />
        ))}
        <Select
          showSearch={{ filterOption: false, onSearch: search }}
          placeholder="搜索型号添加"
          value={null}
          notFoundContent={null}
          options={options
            .filter((o) => !rows.some((r) => r.id === o.id))
            .map((o) => ({
              value: o.id,
              label: `${modelName(o)} · ${o.brandName ?? ''}`,
            }))}
          onChange={(id: number) => {
            const d = options.find((o) => o.id === id);
            if (!d) return;
            setRows((r) => [...r, d]);
            setChecked((c) => [...c, d.id]);
          }}
        />
      </div>
      <Label>做了什么</Label>
      <Input.TextArea
        autoSize={{ minRows: 2, maxRows: 6 }}
        maxLength={500}
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder="问了谁、得到什么答复"
      />
      <Label>结果（选填）</Label>
      <Segmented
        value={coverage}
        onChange={(v) => setCoverage(v as number)}
        options={[
          { value: 0, label: '不改状态' },
          ...[1, 2, 3, 4].map((k) => ({ value: k, label: COVER_NAMES[k] })),
        ]}
      />
      {coverage === COVER.READY && (
        <Hint>
          改为「可直接报价」需要有效期内的价格证据，没有证据的型号保持原状态
        </Hint>
      )}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 2fr) minmax(0, 1fr)',
          gap: 16,
        }}
      >
        <div>
          <Label>下一步行动</Label>
          <Input
            maxLength={200}
            value={nextAction}
            onChange={(e) => setNextAction(e.target.value)}
            placeholder="下一步做什么"
          />
        </div>
        <div>
          <Label>行动日期</Label>
          <DatePicker
            style={{ width: '100%' }}
            value={nextDate}
            onChange={setNextDate}
            disabledDate={(d) => d.isBefore(dayjs(), 'day')}
          />
          <Button
            type="link"
            size="small"
            style={{ padding: 0, marginTop: 4 }}
            onClick={() => setNextDate(recommend)}
          >
            推荐：{recommend.format('MM-DD')}
          </Button>
        </div>
      </div>
    </Modal>
  );
};

// ---------------------------------------------------------------- 确认覆盖状态（F11）

export const CoverageModal: React.FC<{
  demand?: Demand;
  /** 「最近证据：…」说明 */
  basis?: string;
  onClose: () => void;
  onDone: () => void;
}> = ({ demand: d, basis, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [status, setStatus] = useState(0);
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (d) {
      setStatus(d.coveragePending ? d.suggestedCoverage : d.coverageStatus);
      setNote('');
    }
  }, [d]);
  if (!d) return null;
  const differs = status !== d.suggestedCoverage;
  const submit = async () => {
    if (differs && !note.trim()) {
      message.warning('与系统建议不同时请填写说明');
      return;
    }
    setSaving(true);
    try {
      await supplyApi.coverage(d.id, status, note.trim() || undefined);
      message.success('已更新覆盖状态');
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      title="确认覆盖状态"
      open
      width={560}
      onCancel={onClose}
      footer={[
        <Button
          key="inquiry"
          type="link"
          href={`/inquiry/customer-inquiries?keyword=${encodeURIComponent(d.model ?? '')}`}
        >
          去询价
        </Button>,
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button key="ok" type="primary" loading={saving} onClick={submit}>
          确认
        </Button>,
      ]}
    >
      <div style={{ fontSize: 18, fontWeight: 600, marginTop: 8 }}>
        {modelName(d)}
      </div>
      <div style={{ color: palette.mute }}>
        {[d.brandName, d.categoryName].filter(Boolean).join(' · ')}
      </div>
      <div
        style={{
          margin: '16px 0',
          padding: 16,
          borderRadius: 12,
          background: palette.inset,
          display: 'grid',
          gap: 8,
        }}
      >
        <span
          style={{
            display: 'inline-flex',
            gap: 8,
            alignItems: 'center',
            color: palette.sub,
          }}
        >
          系统建议 <CoverPill status={d.suggestedCoverage} />
        </span>
        <span>{basis ?? d.coverageBasis ?? '没有价格记录'}</span>
      </div>
      <Radio.Group
        value={status}
        onChange={(e) => setStatus(e.target.value)}
        style={{ display: 'grid', gap: 8, width: '100%' }}
      >
        {[4, 3, 2, 1].map((k) => {
          const blocked = k === COVER.READY && !d.evidenceValid;
          return (
            <Radio
              key={k}
              value={k}
              disabled={blocked}
              style={{
                padding: '10px 12px',
                borderRadius: 10,
                border: `1px solid ${status === k ? palette.link : 'transparent'}`,
                background: status === k ? palette.accentSoft : undefined,
              }}
            >
              <div style={{ fontWeight: 600 }}>{COVER_NAMES[k]}</div>
              <div
                style={{
                  fontSize: 12,
                  color: blocked ? palette.orange : palette.mute,
                }}
              >
                {blocked ? '没有有效期内的价格证据，请先询价' : COVER_HINTS[k]}
              </div>
            </Radio>
          );
        })}
      </Radio.Group>
      <Label>说明（与系统建议不同时必填）</Label>
      <Input.TextArea
        autoSize={{ minRows: 2, maxRows: 4 }}
        maxLength={200}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder="选填"
      />
    </Modal>
  );
};

// ---------------------------------------------------------------- 建专项（F6）

export const ProjectModal: React.FC<{
  scope?: { brandId: number; categoryId?: number };
  onClose: () => void;
  onDone: (id: number) => void;
}> = ({ scope, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const { brands, categories } = useOptions();
  const users = useUsers();
  const [brandId, setBrandId] = useState<number>();
  const [categoryId, setCategoryId] = useState<number>();
  const [name, setName] = useState('');
  const [goal, setGoal] = useState('');
  const [ownerId, setOwnerId] = useState<number>();
  const [due, setDue] = useState<Dayjs | null>(null);
  const [items, setItems] = useState<Demand[]>([]);
  const [checked, setChecked] = useState<number[]>([]);
  const [activeId, setActiveId] = useState(0);
  const [expand, setExpand] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!scope) return;
    setBrandId(scope.brandId);
    setCategoryId(scope.categoryId || undefined);
    setGoal('');
    setDue(dayjs().add(14, 'day'));
    setExpand(false);
  }, [scope]);

  useEffect(() => {
    if (!scope || !brandId) return;
    supplyApi
      .candidates(brandId, categoryId)
      .then((r) => {
        setItems(r.items);
        setChecked(r.items.map((d) => d.id));
        setName(r.defaultName.replace(' · 全部品类', ''));
        setActiveId(r.activeProjectId);
      })
      .catch(() => setItems([]));
  }, [scope, brandId, categoryId]);

  if (!scope) return null;
  const shown = expand ? items : items.slice(0, 4);
  const submit = async () => {
    if (!brandId || !name.trim() || !goal.trim() || !ownerId || !due) {
      message.warning('请填完品牌、名称、目标、负责人和截止日期');
      return;
    }
    if (!checked.length) {
      message.warning('至少包含一个型号');
      return;
    }
    setSaving(true);
    try {
      const id = await supplyApi.createProject({
        brandId,
        categoryId,
        name: name.trim(),
        goal: goal.trim(),
        ownerId,
        dueDate: iso(due) as string,
        demandIds: checked,
      });
      message.success('已建立专项');
      onDone(id);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      title="建专项"
      open
      width={640}
      onCancel={onClose}
      onOk={submit}
      okText="建立专项"
      okButtonProps={{ loading: saving, disabled: activeId > 0 }}
    >
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <div>
          <Label>品牌</Label>
          <Select
            style={{ width: '100%' }}
            showSearch={{ optionFilterProp: 'label' }}
            options={brands}
            value={brandId}
            onChange={setBrandId}
          />
        </div>
        <div>
          <Label>二级品类</Label>
          <Select
            style={{ width: '100%' }}
            allowClear
            placeholder="全部品类"
            showSearch={{ optionFilterProp: 'label' }}
            options={categories}
            value={categoryId}
            onChange={setCategoryId}
          />
        </div>
      </div>
      {activeId > 0 && (
        <Hint tone="warn">
          这个范围已有进行中的专项，
          <a href={`/supply-chain/projects/${activeId}`}>去查看</a>
        </Hint>
      )}
      <Label>名称</Label>
      <Input
        maxLength={50}
        value={name}
        onChange={(e) => setName(e.target.value)}
      />
      <Label>目标</Label>
      <Input
        maxLength={200}
        value={goal}
        onChange={(e) => setGoal(e.target.value)}
        placeholder="如：月底前让无货的型号都有现货渠道或明确的替代型号"
      />
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <div>
          <Label>负责人</Label>
          <Select
            style={{ width: '100%' }}
            showSearch={{ optionFilterProp: 'label' }}
            options={users}
            value={ownerId}
            onChange={setOwnerId}
          />
        </div>
        <div>
          <Label>截止日期</Label>
          <DatePicker
            style={{ width: '100%' }}
            value={due}
            onChange={setDue}
            disabledDate={(d) => d.isBefore(dayjs(), 'day')}
          />
        </div>
      </div>
      <Label
        extra={`已选 ${checked.length} 个 · 系统带出近 30 天无货和无渠道的型号`}
      >
        包含的型号
      </Label>
      <div style={{ display: 'grid', gap: 8 }}>
        {items.length === 0 && (
          <div style={{ color: palette.mute, fontSize: 13 }}>
            这个范围内没有缺口型号
          </div>
        )}
        {shown.map((d) => (
          <ModelRow
            key={d.id}
            d={d}
            checked={checked.includes(d.id)}
            onChange={(v) =>
              setChecked((c) =>
                v ? [...c, d.id] : c.filter((x) => x !== d.id),
              )
            }
            note={d.cntStockout ? `无货 ${d.cntStockout} 次` : undefined}
          />
        ))}
        {!expand && items.length > 4 && (
          <Button
            type="link"
            style={{ padding: 0, justifySelf: 'start' }}
            onClick={() => setExpand(true)}
          >
            还有 {items.length - 4} 个 · 展开
          </Button>
        )}
      </div>
    </Modal>
  );
};

// ---------------------------------------------------------------- 归到需求对象（F9）

export const ClassifyModal: React.FC<{
  signals?: Signal[];
  onClose: () => void;
  onDone: () => void;
}> = ({ signals, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const { brands, categories } = useOptions();
  const [brandId, setBrandId] = useState<number>();
  const [categoryId, setCategoryId] = useState<number>();
  const [model, setModel] = useState('');
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (!signals?.length) return;
    setBrandId(signals.find((s) => s.brandId)?.brandId || undefined);
    setCategoryId(signals.find((s) => s.categoryId)?.categoryId || undefined);
    setModel('');
  }, [signals]);
  const summary = useMemo(() => {
    const m = new Map<number, number>();
    for (const s of signals ?? [])
      m.set(s.sourceType, (m.get(s.sourceType) ?? 0) + 1);
    return [...m].map(([t, n]) => `${n} 条${SRC_NAMES[t]}`).join('、');
  }, [signals]);
  if (!signals?.length) return null;
  const brandName = brands.find((b) => b.value === brandId)?.label;
  const catName = categories
    .flatMap((g) => g.options)
    .find((o) => o.value === categoryId)?.label;
  // 没填型号时：有原始型号的询盘类信号按型号分组，其余归到品牌 + 品类
  const plan = classifyPlan(signals, model);
  const submit = async () => {
    if (!brandId) {
      message.warning('请选择品牌');
      return;
    }
    if (!categoryId && plan.some((g) => !g.model)) {
      message.warning('没有型号的信号需要选品类');
      return;
    }
    setSaving(true);
    try {
      // 每组一次请求，中途失败的留在待归类里
      for (const g of classifyPlan(signals, model))
        await supplyApi.classify({ ...g, brandId, categoryId });
      message.success('已归类');
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  const Target: React.FC<{ title: string; sub: string }> = ({ title, sub }) => (
    <div
      style={{
        padding: '12px 16px',
        borderRadius: 12,
        background: palette.inset,
      }}
    >
      <b>{title}</b>
      <div style={{ fontSize: 12, color: palette.mute }}>{sub}</div>
    </div>
  );
  return (
    <Modal
      title="归到需求对象"
      open
      width={560}
      onCancel={onClose}
      onOk={submit}
      okText="确认归类"
      okButtonProps={{ loading: saving }}
    >
      <div style={{ color: palette.sub, marginTop: 8 }}>
        已选 {signals.length} 条信号：{summary}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <div>
          <Label>品牌</Label>
          <Select
            style={{ width: '100%' }}
            showSearch={{ optionFilterProp: 'label' }}
            options={brands}
            value={brandId}
            onChange={setBrandId}
            placeholder={
              signals.find((s) => s.brandRaw)?.brandRaw ?? '选择品牌'
            }
          />
        </div>
        <div>
          <Label>二级品类</Label>
          <Select
            style={{ width: '100%' }}
            allowClear
            showSearch={{ optionFilterProp: 'label' }}
            options={categories}
            value={categoryId}
            onChange={setCategoryId}
          />
        </div>
      </div>
      <Label>型号（选填）</Label>
      <Input
        value={model}
        maxLength={100}
        onChange={(e) => setModel(e.target.value)}
        placeholder="不填则归到「品牌 + 品类」"
      />
      {brandId && (
        <>
          <Label>将归到</Label>
          <div style={{ display: 'grid', gap: 8 }}>
            {plan.map((g) => (
              <Target
                key={g.model ?? ''}
                title={
                  g.model ?? [brandName, catName ?? '请选品类'].join(' · ')
                }
                sub={
                  g.model
                    ? `${brandName ?? ''} · ${g.signalIds.length} 条信号`
                    : `${g.signalIds.length} 条没有型号的信号`
                }
              />
            ))}
          </div>
        </>
      )}
    </Modal>
  );
};

// ---------------------------------------------------------------- 录入信号（F10）

const PLATFORMS = ['阿里国际站', '独立站站内搜索', 'Google Ads', '其他'];

export const SignalModal: React.FC<{
  open: boolean;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const { brands, categories } = useOptions();
  const [type, setType] = useState(SRC.SEARCH);
  const [platform, setPlatform] = useState<string>();
  const [count, setCount] = useState<number | null>(null);
  const [content, setContent] = useState('');
  const [period, setPeriod] = useState<[Dayjs | null, Dayjs | null] | null>(
    null,
  );
  const [brandId, setBrandId] = useState<number>();
  const [categoryId, setCategoryId] = useState<number>();
  const [link, setLink] = useState('');
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (!open) return;
    setPlatform(undefined);
    setCount(null);
    setContent('');
    setPeriod([dayjs().subtract(7, 'day'), dayjs().subtract(1, 'day')]);
    setBrandId(undefined);
    setCategoryId(undefined);
    setLink('');
  }, [open]);
  const search = type === SRC.SEARCH;
  const submit = async () => {
    if (!content.trim()) {
      message.warning(search ? '请填写关键词' : '请填写调研内容');
      return;
    }
    if (search && (!platform || !count)) {
      message.warning('请选择平台并填写出现次数');
      return;
    }
    setSaving(true);
    try {
      await supplyApi.addSignal({
        type,
        platform: platform?.trim() || undefined,
        content: content.trim(),
        count: count ?? undefined,
        periodStart: iso(period?.[0]),
        periodEnd: iso(period?.[1]),
        link: link.trim() || undefined,
        brandId,
        categoryId,
      });
      message.success(
        brandId && categoryId ? '已录入' : '已录入，进入待归类信号',
      );
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      title="录入信号"
      open={open}
      width={560}
      onCancel={onClose}
      onOk={submit}
      okText="保存"
      okButtonProps={{ loading: saving }}
    >
      <Segmented
        style={{ marginTop: 8 }}
        value={type}
        onChange={(v) => setType(v as number)}
        options={[
          { value: SRC.SEARCH, label: '搜索关键词' },
          { value: SRC.RESEARCH, label: '同行调研' },
        ]}
      />
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <div>
          <Label>{search ? '平台' : '同行'}</Label>
          {search ? (
            <Select
              style={{ width: '100%' }}
              options={PLATFORMS.map((p) => ({ value: p, label: p }))}
              value={platform}
              onChange={setPlatform}
            />
          ) : (
            <Input
              maxLength={50}
              value={platform}
              onChange={(e) => setPlatform(e.target.value)}
              placeholder="同行名称"
            />
          )}
        </div>
        {search && (
          <div>
            <Label>出现次数</Label>
            <InputNumber
              style={{ width: '100%' }}
              min={1}
              precision={0}
              value={count}
              onChange={setCount}
            />
          </div>
        )}
      </div>
      <Label>{search ? '关键词' : '调研内容'}</Label>
      {search ? (
        <Input
          maxLength={200}
          value={content}
          onChange={(e) => setContent(e.target.value)}
        />
      ) : (
        <Input.TextArea
          autoSize={{ minRows: 2, maxRows: 4 }}
          maxLength={500}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="同行主推什么、标了什么货期"
        />
      )}
      {search && <Hint>照原样填写，不要改写</Hint>}
      {search && (
        <>
          <Label>统计期</Label>
          <DatePicker.RangePicker
            style={{ width: '100%' }}
            value={period}
            onChange={(v) => setPeriod(v)}
          />
        </>
      )}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <div>
          <Label>品牌（选填）</Label>
          <Select
            style={{ width: '100%' }}
            allowClear
            showSearch={{ optionFilterProp: 'label' }}
            options={brands}
            value={brandId}
            onChange={setBrandId}
          />
        </div>
        <div>
          <Label>二级品类（选填）</Label>
          <Select
            style={{ width: '100%' }}
            allowClear
            showSearch={{ optionFilterProp: 'label' }}
            options={categories}
            value={categoryId}
            onChange={setCategoryId}
          />
        </div>
      </div>
      <Label>链接（选填）</Label>
      <Input
        maxLength={500}
        value={link}
        onChange={(e) => setLink(e.target.value)}
        placeholder="粘贴后台截图或报表链接"
      />
      <div
        style={{
          marginTop: 16,
          fontSize: 12,
          color: palette.mute,
          display: 'flex',
          gap: 6,
        }}
      >
        <InfoCircleOutlined /> 不选品牌或品类时，这条信号会进入「待归类信号」
      </div>
    </Modal>
  );
};

// ---------------------------------------------------------------- 小弹窗

/** 改优先级：须填理由 */
export const PriorityModal: React.FC<{
  demand?: Demand;
  onClose: () => void;
  onDone: () => void;
}> = ({ demand: d, onClose, onDone }) => {
  const { message } = App.useApp();
  const [value, setValue] = useState(2);
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (d) {
      setValue(d.priority);
      setReason('');
    }
  }, [d]);
  if (!d) return null;
  const submit = async () => {
    if (!reason.trim()) {
      message.warning('请填写理由');
      return;
    }
    setSaving(true);
    try {
      await supplyApi.priority(d.id, value, reason.trim());
      message.success('已修改优先级');
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      title="改优先级"
      open
      width={440}
      onCancel={onClose}
      onOk={submit}
      okButtonProps={{ loading: saving }}
    >
      <Label>
        系统建议：{PRIORITY_NAMES[d.suggestedPriority]}
        {d.priorityRule ? `（${d.priorityRule}）` : ''}
      </Label>
      <Segmented
        value={value}
        onChange={(v) => setValue(v as number)}
        options={[3, 2, 1].map((k) => ({ value: k, label: PRIORITY_NAMES[k] }))}
      />
      <Label>理由</Label>
      <Input.TextArea
        autoSize={{ minRows: 2 }}
        maxLength={200}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
      />
    </Modal>
  );
};

/** 改负责人与下一步 */
export const NextModal: React.FC<{
  demand?: Demand;
  onClose: () => void;
  onDone: () => void;
}> = ({ demand: d, onClose, onDone }) => {
  const { message } = App.useApp();
  const users = useUsers();
  const [ownerId, setOwnerId] = useState<number>();
  const [action, setAction] = useState('');
  const [date, setDate] = useState<Dayjs | null>(null);
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (!d) return;
    setOwnerId(d.ownerId || undefined);
    setAction(d.nextActionSource === 2 ? '' : (d.nextAction ?? ''));
    setDate(d.nextActionDate ? dayjs(d.nextActionDate) : null);
  }, [d]);
  if (!d) return null;
  const submit = async () => {
    if (action.trim() && !date) {
      message.warning('请选择行动日期');
      return;
    }
    setSaving(true);
    try {
      await supplyApi.next(d.id, {
        ownerId,
        nextAction: action.trim() || undefined,
        nextActionDate: iso(date),
      });
      message.success('已保存');
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      title="负责人与下一步"
      open
      width={480}
      onCancel={onClose}
      onOk={submit}
      okButtonProps={{ loading: saving }}
    >
      <Label>负责人</Label>
      <Select
        style={{ width: '100%' }}
        showSearch={{ optionFilterProp: 'label' }}
        options={users}
        value={ownerId}
        onChange={setOwnerId}
      />
      <Label>下一步行动</Label>
      <Input
        maxLength={200}
        value={action}
        onChange={(e) => setAction(e.target.value)}
      />
      <Label>行动日期</Label>
      <DatePicker style={{ width: '100%' }} value={date} onChange={setDate} />
    </Modal>
  );
};

/** 延后：单个或批量 */
export const DeferModal: React.FC<{
  ids?: number[];
  onClose: () => void;
  onDone: () => void;
}> = ({ ids, onClose, onDone }) => {
  const { message } = App.useApp();
  const [date, setDate] = useState<Dayjs | null>(null);
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (ids) setDate(dayjs().add(1, 'day'));
  }, [ids]);
  if (!ids) return null;
  const submit = async () => {
    if (!date) return;
    setSaving(true);
    try {
      await supplyApi.defer(ids, iso(date) as string);
      message.success('已延后');
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      title={ids.length > 1 ? `延后 ${ids.length} 个型号` : '延后到哪天'}
      open
      width={360}
      onCancel={onClose}
      onOk={submit}
      okButtonProps={{ disabled: !date, loading: saving }}
    >
      <DatePicker
        style={{ width: '100%', marginTop: 8 }}
        value={date}
        onChange={setDate}
        disabledDate={(d) => !d.isAfter(dayjs(), 'day')}
      />
    </Modal>
  );
};

/** 专项增减型号：同范围内的候选与已有型号 */
export const ProjectItemsModal: React.FC<{
  project?: {
    id: number;
    brandId: number;
    categoryId?: number;
    items?: Demand[];
  };
  onClose: () => void;
  onDone: () => void;
}> = ({ project: p, onClose, onDone }) => {
  const { message } = App.useApp();
  const [rows, setRows] = useState<Demand[]>([]);
  const [checked, setChecked] = useState<number[]>([]);
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (!p) return;
    const current = p.items ?? [];
    setChecked(current.map((d) => d.id));
    supplyApi
      .models({ brandId: p.brandId, categoryId: p.categoryId, size: 200 })
      .then((r) => {
        const extra = r.records.filter(
          (d) => !current.some((c) => c.id === d.id),
        );
        setRows([...current, ...extra]);
      })
      .catch(() => setRows(current));
  }, [p]);
  if (!p) return null;
  const submit = async () => {
    const before = (p.items ?? []).map((d) => d.id);
    const add = checked.filter((id) => !before.includes(id));
    const remove = before.filter((id) => !checked.includes(id));
    if (!add.length && !remove.length) return onClose();
    if (!checked.length) {
      message.warning('专项至少包含一个型号');
      return;
    }
    setSaving(true);
    try {
      await supplyApi.projectItems(p.id, add, remove);
      message.success('已更新型号');
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      title="增减型号"
      open
      width={600}
      onCancel={onClose}
      onOk={submit}
      okButtonProps={{ loading: saving }}
    >
      <Label extra={`已选 ${checked.length} 个`}>
        同范围内已进入覆盖矩阵的型号
      </Label>
      <div
        style={{ display: 'grid', gap: 8, maxHeight: 420, overflowY: 'auto' }}
      >
        {rows.map((d) => (
          <ModelRow
            key={d.id}
            d={d}
            checked={checked.includes(d.id)}
            onChange={(v) =>
              setChecked((c) =>
                v ? [...c, d.id] : c.filter((x) => x !== d.id),
              )
            }
            note={d.cntStockout ? `无货 ${d.cntStockout} 次` : undefined}
          />
        ))}
      </div>
    </Modal>
  );
};

/** 结束专项：标为已完成或取消 */
export const CloseProjectModal: React.FC<{
  target?: { id: number; done: boolean; solved: number; total: number };
  onClose: () => void;
  onDone: () => void;
}> = ({ target: t, onClose, onDone }) => {
  const { message } = App.useApp();
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);
  useEffect(() => setNote(''), [t]);
  if (!t) return null;
  const submit = async () => {
    if (!note.trim()) {
      message.warning(t.done ? '请填写结果说明' : '请填写取消原因');
      return;
    }
    setSaving(true);
    try {
      await supplyApi.closeProject(t.id, t.done, note.trim() || undefined);
      message.success(t.done ? '专项已完成' : '专项已取消');
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      title={t.done ? '标为已完成' : '取消专项'}
      open
      width={460}
      onCancel={onClose}
      onOk={submit}
      okText={t.done ? '标为已完成' : '取消专项'}
      okButtonProps={{ loading: saving, danger: !t.done }}
    >
      <Label>
        已解决 {t.solved} / {t.total}
        {t.done && t.solved < t.total ? '，还有型号没解决' : ''}
      </Label>
      <Input.TextArea
        autoSize={{ minRows: 2 }}
        maxLength={200}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder={
          t.done ? '结果说明，如：4 个找到现货渠道，2 个确认停产' : '取消原因'
        }
      />
    </Modal>
  );
};
