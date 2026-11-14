import {
  App,
  Button,
  Checkbox,
  DatePicker,
  Input,
  Modal,
  Radio,
  Segmented,
  Select,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import SupplierSelect from '@/pages/inquiry/my-tasks/SupplierSelect';
import { ReasonModal } from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { DueText, Pill, useOptions, useUsers } from './components';
import { Hint, InfoBox, iso, Label } from './dialogs';
import { candidateNextOptional, candidateTone, evidenceText } from './rules';
import {
  CAND,
  CAND_NAMES,
  CAND_SRC_NAMES,
  type Candidate,
  type DevScope,
  type DevSupplier,
  HEAT_NAMES,
  newKey,
  readBizError,
  supplyApi,
} from './service';

const required = (heat: number) => (heat >= 3 ? 3 : heat === 2 ? 2 : 1);

// ---------------------------------------------------------------- 加入主动拓展

/** 加入主动拓展；范围已在主动拓展里时改为修改热度与理由，并可移出 */
export const ExpansionModal: React.FC<{
  open: boolean;
  scope?: { brandId: number; categoryId?: number };
  onClose: () => void;
  onDone: () => void;
}> = ({ open, scope, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const { brands, categories } = useOptions();
  const users = useUsers();
  const [brandId, setBrandId] = useState<number>();
  const [categoryId, setCategoryId] = useState<number>();
  const [heat, setHeat] = useState(2);
  const [reason, setReason] = useState('');
  const [ownerId, setOwnerId] = useState<number>();
  const [range, setRange] = useState<DevScope>();
  const [saving, setSaving] = useState(false);
  const [removing, setRemoving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setBrandId(scope?.brandId);
    setCategoryId(scope?.categoryId || undefined);
    setHeat(2);
    setReason('');
    setOwnerId(undefined);
  }, [open, scope]);

  useEffect(() => {
    if (!open || !brandId || !categoryId) {
      setRange(undefined);
      return;
    }
    supplyApi
      .devScope(brandId, categoryId)
      .then((r) => {
        setRange(r);
        if (r.expansionId) {
          setHeat(r.expansionHeat ?? 2);
          setReason(r.expansionReason ?? '');
          setOwnerId(r.expansionOwnerId || undefined);
        }
      })
      .catch(() => setRange(undefined));
  }, [open, brandId, categoryId]);

  if (!open) return null;
  const editing = !!range?.expansionId;
  // 需求推出的热度：已有主动拓展时无法单独拿到，按当前选择估算，保存后以清单为准
  const demandHeat = range && !editing ? range.heat : 1;
  const need = required(Math.max(heat, demandHeat));
  const submit = async () => {
    if (!brandId || !categoryId) {
      message.warning('请选择品牌和二级品类');
      return;
    }
    if (!reason.trim()) {
      message.warning('请填写理由');
      return;
    }
    setSaving(true);
    try {
      if (editing && range?.expansionId) {
        await supplyApi.updateExpansion(range.expansionId, {
          heat,
          reason: reason.trim(),
          ownerId,
        });
        message.success('已修改主动拓展');
      } else {
        await supplyApi.addExpansion({
          brandId,
          categoryId,
          heat,
          reason: reason.trim(),
          ownerId,
        });
        message.success('已加入主动拓展');
      }
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <>
      <Modal
        title={editing ? '修改主动拓展' : '加入主动拓展'}
        open
        width={600}
        onCancel={onClose}
        onOk={submit}
        okText={editing ? '保存' : '加入'}
        okButtonProps={{ loading: saving }}
        footer={(origin) => (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {editing && (
              <Button danger type="link" onClick={() => setRemoving(true)}>
                移出主动拓展
              </Button>
            )}
            <span style={{ flex: 1 }} />
            {origin}
          </div>
        )}
      >
        <div style={{ color: palette.sub, fontSize: 13 }}>
          公司想进入、但还没有询盘的品牌品类，加进来后会出现在供应商开发清单里。
        </div>
        <div
          style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}
        >
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
              showSearch={{ optionFilterProp: 'label' }}
              options={categories}
              value={categoryId}
              onChange={setCategoryId}
            />
          </div>
        </div>
        {editing && (
          <Hint tone="link">
            这个品牌品类已在主动拓展里，可以修改热度和理由。
          </Hint>
        )}
        <Label>热度</Label>
        <Radio.Group
          optionType="button"
          value={heat}
          onChange={(e) => setHeat(e.target.value)}
          options={[3, 2, 1].map((h) => ({ value: h, label: HEAT_NAMES[h] }))}
        />
        <Hint>
          {HEAT_NAMES[heat]}：要求 {required(heat)} 家合格供应商
        </Hint>
        <Label>理由</Label>
        <Input.TextArea
          rows={3}
          maxLength={200}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="如：明年重点拓展欧洲客户，这个品牌在德国、荷兰询盘多"
        />
        <Label>负责人（选填）</Label>
        <Select
          style={{ width: '100%' }}
          allowClear
          showSearch={{ optionFilterProp: 'label' }}
          options={users}
          value={ownerId}
          onChange={setOwnerId}
        />
        {range && (
          <InfoBox>
            现有合格供应商 {range.qualified} 家，
            {editing ? '保存后' : '加入后'}
            {range.qualified >= need
              ? '已达标。'
              : `缺 ${need - range.qualified} 家。`}
          </InfoBox>
        )}
      </Modal>
      <ReasonModal
        open={removing}
        title="移出主动拓展"
        placeholder="移出原因，如：公司调整方向，暂不进入"
        okText="移出"
        onCancel={() => setRemoving(false)}
        onOk={async (r) => {
          if (!range?.expansionId) return;
          try {
            await supplyApi.removeExpansion(range.expansionId, r);
            message.success('已移出主动拓展');
            setRemoving(false);
            onDone();
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
    </>
  );
};

// ---------------------------------------------------------------- 加候选 / 关联供应商库

export interface CandidateTarget {
  projectId: number;
  title: string;
  /** 已有候选数 */
  count: number;
  /** 从线索或范围内合格供应商一键加入 */
  supplier?: DevSupplier;
  /** 给已有候选关联供应商库 */
  link?: Candidate;
}

export const CandidateModal: React.FC<{
  target?: CandidateTarget;
  canSeeSupplier: boolean;
  onClose: () => void;
  onDone: () => void;
}> = ({ target, canSeeSupplier, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [mode, setMode] = useState<'lib' | 'name'>('lib');
  const [supplierId, setSupplierId] = useState<number>();
  const [name, setName] = useState('');
  const [contactName, setContactName] = useState('');
  const [contactInfo, setContactInfo] = useState('');
  const [source, setSource] = useState(1);
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!target) return;
    setMode(canSeeSupplier ? 'lib' : 'name');
    setSupplierId(target.supplier?.id);
    setName('');
    setContactName('');
    setContactInfo('');
    setSource(target.supplier ? 5 : 1);
    setNote('');
  }, [target, canSeeSupplier]);

  if (!target) return null;
  const link = target.link;
  const preset = target.supplier;
  const fromLib = !!preset || mode === 'lib';
  const submit = async () => {
    setSaving(true);
    try {
      if (link) {
        if (!supplierId) {
          message.warning('请选择供应商');
          return;
        }
        await supplyApi.linkCandidate(link.id, supplierId);
        message.success('已关联供应商库');
      } else {
        if (fromLib && !supplierId) {
          message.warning('请选择供应商');
          return;
        }
        if (!fromLib && !name.trim()) {
          message.warning('请填写名称');
          return;
        }
        await supplyApi.addCandidate(target.projectId, {
          supplierId: fromLib ? supplierId : undefined,
          name: fromLib ? undefined : name.trim(),
          contactName: contactName.trim() || undefined,
          contactInfo: contactInfo.trim() || undefined,
          source: fromLib && !preset && source === 1 ? 5 : source,
          note: note.trim() || undefined,
        });
        message.success('已加为候选');
      }
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      title={link ? '关联供应商库' : '加候选供应商'}
      open
      width={600}
      onCancel={onClose}
      onOk={submit}
      okText={link ? '关联' : '加入'}
      okButtonProps={{ loading: saving }}
    >
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          color: palette.sub,
          marginBottom: 8,
        }}
      >
        <span>{target.title}</span>
        {!link && (
          <span style={{ color: palette.mute, fontSize: 13 }}>
            已有候选 {target.count} 家
          </span>
        )}
      </div>
      {link ? (
        <>
          <Label>候选「{link.name}」对应供应商库里的哪一家</Label>
          <SupplierSelect
            block
            value={supplierId}
            onChange={(v) => setSupplierId(v)}
          />
          <Hint>
            关联后名称以供应商库为准，近 90
            天在这个品牌品类有有效报价或采购订单时自动标为合格。供应商库里还没有的，先去供应商库建档。
          </Hint>
        </>
      ) : (
        <>
          {preset ? (
            <>
              <Label>供应商</Label>
              <b>{preset.name}</b>
            </>
          ) : (
            canSeeSupplier && (
              <Segmented
                value={mode}
                onChange={(v) => setMode(v as 'lib' | 'name')}
                options={[
                  { value: 'lib', label: '从供应商库选' },
                  { value: 'name', label: '先登记名称' },
                ]}
              />
            )
          )}
          {!preset &&
            (fromLib ? (
              <>
                <Label>供应商</Label>
                <SupplierSelect
                  block
                  value={supplierId}
                  onChange={(v) => setSupplierId(v)}
                />
              </>
            ) : (
              <>
                <Label>名称</Label>
                <Input
                  maxLength={100}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </>
            ))}
          <div
            style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}
          >
            <div>
              <Label>联系人（选填）</Label>
              <Input
                maxLength={50}
                value={contactName}
                onChange={(e) => setContactName(e.target.value)}
              />
            </div>
            <div>
              <Label>联系方式（选填）</Label>
              <Input
                maxLength={100}
                value={contactInfo}
                onChange={(e) => setContactInfo(e.target.value)}
              />
            </div>
          </div>
          {!preset && (
            <>
              <Label>来源</Label>
              <Radio.Group
                optionType="button"
                value={source}
                onChange={(e) => setSource(e.target.value)}
                options={[1, 2, 3, 4, 6].map((k) => ({
                  value: k,
                  label: CAND_SRC_NAMES[k],
                }))}
              />
            </>
          )}
          <Label>备注（选填）</Label>
          <Input.TextArea
            rows={2}
            maxLength={300}
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
          {!fromLib && (
            <InfoBox>
              没有关联供应商库的候选不会自动标为合格。确定合作后在候选上点「关联供应商库」。
            </InfoBox>
          )}
        </>
      )}
    </Modal>
  );
};

// ---------------------------------------------------------------- 候选记一次处理

export const CandidateRecordModal: React.FC<{
  open: boolean;
  title?: string;
  candidates?: Candidate[];
  checkedIds?: number[];
  canSeeSupplier: boolean;
  onLink: (c: Candidate) => void;
  onClose: () => void;
  onDone: () => void;
}> = ({
  open,
  title,
  candidates = [],
  checkedIds,
  canSeeSupplier,
  onLink,
  onClose,
  onDone,
}) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [checked, setChecked] = useState<number[]>([]);
  const [content, setContent] = useState('');
  const [stage, setStage] = useState<number>();
  const [abandon, setAbandon] = useState('');
  const [next, setNext] = useState('');
  const [date, setDate] = useState<Dayjs | null>(null);
  const [key, setKey] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    const open5 = candidates.filter((c) => c.stage !== CAND.ABANDONED);
    setChecked(checkedIds ?? (open5.length === 1 ? [open5[0].id] : []));
    setContent('');
    setStage(undefined);
    setAbandon('');
    setNext('');
    setDate(dayjs().add(3, 'day'));
    setKey(newKey());
  }, [open, candidates, checkedIds]);

  if (!open) return null;
  const picked = candidates.filter((c) => checked.includes(c.id));
  const optional = candidateNextOptional(picked, stage);
  const unlinked = picked.find(
    (c) => !c.supplierId && (stage ?? c.stage) === CAND.QUOTED,
  );
  const submit = async () => {
    if (!picked.length) {
      message.warning('至少选一个候选');
      return;
    }
    if (!content.trim()) {
      message.warning('请填写做了什么');
      return;
    }
    if (stage === CAND.ABANDONED && !abandon.trim()) {
      message.warning('请填写放弃原因');
      return;
    }
    if (!optional && (!next.trim() || !date)) {
      message.warning('请填写下一步行动与日期');
      return;
    }
    setSaving(true);
    try {
      const r = await supplyApi.candidateRecord({
        candidateIds: checked,
        content: content.trim(),
        stage,
        abandonReason: abandon.trim() || undefined,
        nextAction: next.trim() || undefined,
        nextActionDate: next.trim() ? iso(date) : undefined,
        idempotencyKey: key,
      });
      if (r.skipped.length)
        message.info(`${r.skipped.length} 家已合格，阶段保持不变`);
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
      open
      width={640}
      onCancel={onClose}
      onOk={submit}
      okText="保存"
      okButtonProps={{ loading: saving }}
    >
      <b style={{ fontSize: 15 }}>{title}</b>
      <Label>这次处理涉及的候选</Label>
      <div style={{ display: 'grid', gap: 8 }}>
        {candidates.map((c) => (
          // biome-ignore lint/a11y/noLabelWithoutControl: antd Checkbox 在 label 内渲染 input
          <label
            key={c.id}
            style={{
              display: 'grid',
              gridTemplateColumns: '24px minmax(0, 1fr) auto minmax(0, 1fr)',
              alignItems: 'center',
              gap: 12,
              padding: '10px 12px',
              borderRadius: 10,
              background: palette.inset,
              cursor: 'pointer',
            }}
          >
            <Checkbox
              checked={checked.includes(c.id)}
              onChange={(e) =>
                setChecked((s) =>
                  e.target.checked ? [...s, c.id] : s.filter((x) => x !== c.id),
                )
              }
            />
            <b
              style={{
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {canSeeSupplier ? c.name : `候选 ${c.id}`}
            </b>
            <Pill tone={candidateTone(c.stage)}>{CAND_NAMES[c.stage]}</Pill>
            <span style={{ fontSize: 12, color: palette.mute }}>
              {c.stage === CAND.QUALIFIED ? (
                `依据：${evidenceText(c.evidenceType, c.evidenceDate)}`
              ) : c.stage === CAND.ABANDONED ? (
                c.abandonReason
              ) : c.nextActionDate ? (
                <DueText date={c.nextActionDate} prefix />
              ) : null}
            </span>
          </label>
        ))}
      </div>
      <Label>做了什么</Label>
      <Input.TextArea
        rows={3}
        maxLength={500}
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder="如：对方发来一级代理授权书，报了一个型号的现货价"
      />
      <Label>候选阶段</Label>
      <Radio.Group
        optionType="button"
        value={stage}
        onChange={(e) => setStage(e.target.value)}
        options={[1, 2, 3, 5].map((k) => ({ value: k, label: CAND_NAMES[k] }))}
      />
      <Hint>
        「合格」不能手动选：关联供应商库后，有了近 90
        天的有效报价或采购订单，系统自动标上
      </Hint>
      {stage === CAND.ABANDONED && (
        <>
          <Label>放弃原因</Label>
          <Input
            maxLength={200}
            value={abandon}
            onChange={(e) => setAbandon(e.target.value)}
            placeholder="如：只做翻新件"
          />
        </>
      )}
      {unlinked && (
        <InfoBox
          tone="warn"
          extra={
            canSeeSupplier && (
              <Button
                type="link"
                size="small"
                style={{ padding: 0 }}
                onClick={() => onLink(unlinked)}
              >
                关联供应商库
              </Button>
            )
          }
        >
          这家候选还没关联供应商库，报价记录挂不上来。先建档并关联，才能自动转为合格。
        </InfoBox>
      )}
      {!optional && (
        <div
          style={{ display: 'grid', gridTemplateColumns: '1fr 200px', gap: 16 }}
        >
          <div>
            <Label>下一步行动</Label>
            <Input
              maxLength={200}
              value={next}
              onChange={(e) => setNext(e.target.value)}
              placeholder="如：等对方发代理证明"
            />
          </div>
          <div>
            <Label>行动日期</Label>
            <DatePicker
              style={{ width: '100%' }}
              value={date}
              onChange={setDate}
              disabledDate={(d) => d.isBefore(dayjs(), 'day')}
            />
            <Hint tone="link">推荐：3 天后</Hint>
          </div>
        </div>
      )}
    </Modal>
  );
};
