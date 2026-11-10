import {
  BulbOutlined,
  HourglassOutlined,
  LoadingOutlined,
  RobotOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Checkbox,
  DatePicker,
  Input,
  Modal,
  Segmented,
  Select,
  Space,
  Tag,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useRef, useState } from 'react';
import { customerApi } from '@/pages/customer/service';
import { CreateAfterSalesModal } from '@/pages/sales/after-sales/dialogs';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { DueText, fmtDate, iso, StageTag, useBlockers } from './components';
import {
  adjustSuggest,
  nurtureCandidate,
  dateShortcuts as shortcutsOf,
  suggestOf,
} from './nextDate';
import StageQuestions from './StageQuestions';
import {
  ACCOUNT_ANSWER,
  AI_NOT_CONFIGURED,
  AI_STATUS,
  ANSWER_STAGES,
  BOARD_ACCOUNT,
  BOARD_DEVELOP,
  BOARD_SALES,
  CHANNEL_OPTIONS,
  type ExtractResult,
  type FollowupItem,
  followupApi,
  GROUP,
  NEXT_USER,
  type RecordResult,
  readBizError,
  SITUATION,
  SITUATION_OPTIONS,
  TARGET,
  TOUCH,
} from './service';
import {
  applyAiAnswers,
  emptyAnswers,
  needsAfterSales,
  type StageAnswers,
  stageEffect,
  stageKind,
  stagePayload,
  validateStage,
} from './stageForm';

const POLL_MS = 2000;
const POLL_TIMEOUT_MS = 60000;
const RAW_MAX = 20000;
const NEXT_MAX = 200;
/** 太笼统的下一步：去掉标点空白后只剩这些词 */
const VAGUE =
  /^(再?跟进|再联系|继续跟进?|跟一下|催一下|再问问?|再说|等回复|等待回复|follow ?up)$/i;
const AI_FAIL_TEXT = 'AI 暂时没有整理出来，可以直接填写';

type Suggestion = ExtractResult['result'] extends infer R
  ? R extends { profileSuggestions: (infer S)[] }
    ? S
    : never
  : never;

const newKey = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;

const SectionLabel: React.FC<{
  children: React.ReactNode;
  required?: boolean;
}> = ({ children, required }) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        fontSize: 13,
        fontWeight: 600,
        color: palette.ink,
        margin: '14px 0 6px',
      }}
    >
      {required && <span style={{ color: palette.red }}>* </span>}
      {children}
    </div>
  );
};

/** 单据超过这个数时默认只显示前几张 */
const DOC_FOLD = 5;

/** 这次跟进涉及的单据：默认全选，可取消，至少一张 */
const DocChecklist: React.FC<{
  docs: FollowupItem[];
  checked: number[];
  all: boolean;
  onShowAll: () => void;
  onChange: (ids: number[]) => void;
}> = ({ docs, checked, all, onShowAll, onChange }) => {
  const { palette } = useAppTheme();
  const shown = all ? docs : docs.slice(0, DOC_FOLD);
  const full = checked.length === docs.length;
  return (
    <div
      style={{
        marginTop: 12,
        padding: '10px 14px',
        borderRadius: 10,
        background: palette.inset,
        display: 'grid',
        gap: 6,
      }}
    >
      <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
        <b style={{ fontSize: 13, color: palette.ink }}>这次跟进涉及的单据</b>
        <span style={{ fontSize: 12, color: palette.link, flex: 1 }}>
          已选 {checked.length} / {docs.length}
        </span>
        <Checkbox
          checked={full}
          indeterminate={!full && checked.length > 0}
          onChange={(e) =>
            onChange(e.target.checked ? docs.map((d) => d.id) : [])
          }
        >
          全选
        </Checkbox>
      </div>
      <div
        style={{
          display: 'grid',
          gap: 4,
          maxHeight: all ? 280 : undefined,
          overflowY: all ? 'auto' : undefined,
        }}
      >
        {shown.map((d) => {
          const on = checked.includes(d.id);
          return (
            <div
              key={d.id}
              style={{
                display: 'flex',
                gap: 10,
                alignItems: 'center',
                fontSize: 13,
                opacity: on ? 1 : 0.55,
              }}
            >
              <Checkbox
                checked={on}
                onChange={(e) =>
                  onChange(
                    e.target.checked
                      ? [...checked, d.id]
                      : checked.filter((x) => x !== d.id),
                  )
                }
              >
                <span style={{ color: palette.ink }}>
                  {d.docNo ?? d.customerName}
                </span>
              </Checkbox>
              <Tag variant="filled" style={{ margin: 0 }}>
                {d.stageCode} {d.stageName}
              </Tag>
              <span style={{ marginLeft: 'auto', fontSize: 12 }}>
                <DueText item={d} />
              </span>
            </div>
          );
        })}
      </div>
      {docs.length > shown.length && (
        <Button
          type="link"
          size="small"
          style={{ justifySelf: 'start', padding: 0 }}
          onClick={onShowAll}
        >
          还有 {docs.length - shown.length} 张 · 展开
        </Button>
      )}
    </div>
  );
};

export interface RecordTarget {
  customerId?: number;
  customerName?: string;
  /** 已知的跟进对象（列表 / 待办打开时带入）；不传时按客户取最需要跟进的一条 */
  item?: FollowupItem;
  /** 这次跟进涉及的单据（客户行、待办卡片带入），默认全选；不传时只有 item 一张 */
  items?: FollowupItem[];
}

const RecordFollowupModal: React.FC<{
  open: boolean;
  target?: RecordTarget;
  onClose: () => void;
  /**
   * newInquiryCustomerId：客户经营答「有新需求」时，记完接着建询盘；
   * refusedItem：主动开发选「明确拒绝」时，记完接着打开停止开发
   */
  onDone: (
    r: RecordResult,
    newInquiryCustomerId?: number,
    refusedItem?: FollowupItem,
  ) => void;
  /** 选了 30 天以后：改为转培育这张报价单，激活日期带入所选日期 */
  onNurture?: (item: FollowupItem, days: number) => void;
}> = ({ open, target, onClose, onDone, onNurture }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();

  const [customerId, setCustomerId] = useState<number>();
  const [customerName, setCustomerName] = useState<string>();
  const [customerOptions, setCustomerOptions] = useState<
    { value: number; label: string }[]
  >([]);
  /** 可选的单据与勾选的单据 */
  const [docs, setDocs] = useState<FollowupItem[]>([]);
  const [checked, setChecked] = useState<number[]>([]);
  const [allDocs, setAllDocs] = useState(false);
  const selected = docs.filter((d) => checked.includes(d.id));
  /** 主单据：带入的那张仍勾选时用它，否则用勾选的第一张；决定阶段问题、卡点字典 */
  const item = selected.find((d) => d.id === target?.item?.id) ?? selected[0];
  /** 所选单据阶段不同：只填通用内容 */
  const mixed = new Set(selected.map((d) => d.stageCode)).size > 1;
  const mixedBlocked =
    mixed && selected.some((d) => ANSWER_STAGES.includes(d.stageCode));
  const blockers = useBlockers(item?.board ?? BOARD_SALES);
  const kind = mixed ? 'general' : stageKind(item);
  const pickDocs = (list: FollowupItem[]) => {
    setDocs(list);
    setChecked(list.map((d) => d.id));
    setAllDocs(false);
  };
  const [answers, setAnswers] = useState<StageAnswers>(emptyAnswers);
  // 「有问题」保存后打开新建售后单
  const [afterSales, setAfterSales] = useState<{
    soId?: number;
    symptom?: string;
  }>();

  const [raw, setRaw] = useState('');
  const [situation, setSituation] = useState(0);
  const [blocker, setBlocker] = useState<string>();
  const [nextAction, setNextAction] = useState('');
  const [nextDate, setNextDate] = useState<Dayjs | null>(null);
  /** 手动选过日期后不再跟着推荐变 */
  const [dateTouched, setDateTouched] = useState(false);
  const pickDate = (d: Dayjs | null) => {
    setNextDate(d);
    setDateTouched(true);
  };
  const [channel, setChannel] = useState(1);
  const [payDate, setPayDate] = useState<Dayjs | null>(null);
  const [nextError, setNextError] = useState(false);

  const [aiState, setAiState] = useState<
    'idle' | 'running' | 'done' | 'failed'
  >('idle');
  const [aiHint, setAiHint] = useState<string>();
  const [aiTaskId, setAiTaskId] = useState<number>();
  const [summary, setSummary] = useState('');
  const [nextOptions, setNextOptions] = useState<
    { text: string; date?: string }[]
  >([]);
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [truncated, setTruncated] = useState(false);
  const [saving, setSaving] = useState(false);
  const keyRef = useRef(newKey());
  const pollRef = useRef<{ timer?: number; run: number }>({ run: 0 });

  const stopPoll = () => {
    window.clearTimeout(pollRef.current.timer);
    pollRef.current.run += 1;
  };

  // 打开时重置，并带入客户与跟进对象
  useEffect(() => {
    if (!open) {
      stopPoll();
      return;
    }
    keyRef.current = newKey();
    setRaw('');
    setSituation(0);
    setBlocker(undefined);
    setNextAction('');
    setNextDate(null);
    setDateTouched(false);
    setChannel(1);
    setPayDate(null);
    setNextError(false);
    setAiState('idle');
    setAiHint(undefined);
    setAiTaskId(undefined);
    setSummary('');
    setNextOptions([]);
    setSuggestions([]);
    setTruncated(false);
    setAnswers(emptyAnswers());
    setCustomerId(target?.customerId);
    setCustomerName(target?.customerName ?? target?.item?.customerName);
    pickDocs(target?.items ?? (target?.item ? [target.item] : []));
    if (target?.item?.expectedPayDate)
      setPayDate(dayjs(target.item.expectedPayDate));
    if (target?.item?.blockerCode) setBlocker(target.item.blockerCode);
  }, [open, target]);

  // 没带跟进对象时按客户取当前跟进对象
  useEffect(() => {
    if (!open || !customerId || target?.item) return;
    let alive = true;
    followupApi
      .customerSummary(customerId)
      .then((s) => {
        if (!alive) return;
        pickDocs(s.currentItems ?? (s.current ? [s.current] : []));
        if (s.current?.blockerCode) setBlocker(s.current.blockerCode);
        if (s.current?.expectedPayDate)
          setPayDate(dayjs(s.current.expectedPayDate));
      })
      .catch(() => alive && pickDocs([]));
    return () => {
      alive = false;
    };
  }, [open, customerId, target]);

  // 换了主单据或阶段是否相同变了：阶段答案重来
  useEffect(() => setAnswers(emptyAnswers()), [item?.id, mixed]);

  // 推荐日期：按所选单据取最早的，再按客户情况修正；手动选过就不再变
  const suggest = adjustSuggest(
    suggestOf(selected, dayjs().startOf('day')),
    situation,
    dayjs().startOf('day'),
  );
  const suggestDay = iso(suggest.date);
  useEffect(() => {
    if (open && !dateTouched) setNextDate(dayjs(suggestDay));
  }, [open, dateTouched, suggestDay]);

  const searchCustomer = (keyword: string) => {
    customerApi
      .page({ name: keyword || undefined, page: 1, pageSize: 20 })
      .then((r) =>
        setCustomerOptions(
          r.records.map((c) => ({ value: c.id, label: c.name })),
        ),
      )
      .catch(() => setCustomerOptions([]));
  };

  // ------------------------------------------------------------ AI 整理

  const applyResult = (r: ExtractResult) => {
    const res = r.result;
    if (!res) return;
    setSummary(res.summary ?? '');
    if (res.situation) setSituation(res.situation);
    if (res.blockerCode) setBlocker(res.blockerCode);
    setNextOptions(res.nextActions ?? []);
    setSuggestions(res.profileSuggestions ?? []);
    setTruncated(!!res.truncated);
    setAnswers((a) => applyAiAnswers(kind, a, res, item?.stageCode));
    const first = res.nextActions?.[0];
    if (first && !nextAction.trim()) {
      setNextAction(first.text.slice(0, NEXT_MAX));
      if (first.date && !dateTouched) pickDate(dayjs(first.date));
    }
  };

  const runAi = async (text: string) => {
    if (!customerId || !text.trim()) return;
    stopPoll();
    const run = pollRef.current.run;
    setAiState('running');
    setAiHint(undefined);
    const started = Date.now();
    const fail = (hint = AI_FAIL_TEXT) => {
      if (pollRef.current.run !== run) return;
      setAiState('failed');
      setAiHint(hint);
    };
    let task: ExtractResult;
    try {
      task = await followupApi.extract({
        customerId,
        itemId: item?.id,
        rawContent: text,
      });
    } catch (e) {
      const err = readBizError(e);
      fail(
        err.errorCode === AI_NOT_CONFIGURED
          ? `${err.message}，可以直接填写`
          : AI_FAIL_TEXT,
      );
      return;
    }
    setAiTaskId(task.taskId);
    const poll = async () => {
      if (pollRef.current.run !== run) return;
      if (Date.now() - started > POLL_TIMEOUT_MS) {
        fail();
        return;
      }
      try {
        const r = await followupApi.extractResult(task.taskId, customerId);
        if (pollRef.current.run !== run) return;
        if (r.status === AI_STATUS.DONE) {
          applyResult(r);
          setAiState('done');
          return;
        }
        if (r.status === AI_STATUS.FAILED) {
          fail();
          return;
        }
      } catch {
        fail();
        return;
      }
      pollRef.current.timer = window.setTimeout(poll, POLL_MS);
    };
    pollRef.current.timer = window.setTimeout(poll, POLL_MS);
  };

  // ------------------------------------------------------------ 快捷项

  const quick = (kind: 'noReply' | 'sent' | 'compare') => {
    if (kind === 'noReply') {
      setSituation(SITUATION.NO_REPLY);
      // 下一步按该阶段 SOP 默认动作填入，没有时用当前的系统提醒
      const fallback =
        item?.sopHint ||
        (item?.nextActionSource !== NEXT_USER ? item?.nextAction : undefined);
      if (!nextAction.trim() && fallback)
        setNextAction(
          fallback.replace(/^第\s*\d+\s*天[：:]\s*/, '').slice(0, NEXT_MAX),
        );
    } else if (kind === 'sent') {
      setSummary((s) => s || '已发资料给客户');
      if (!nextAction.trim()) setNextAction('问客户资料是否收到，有没有疑问');
      if (!dateTouched) pickDate(dayjs().startOf('day').add(1, 'day'));
    } else {
      setSituation((s) => s | SITUATION.FEEDBACK);
      setBlocker('PRICE');
      if (!nextAction.trim())
        setNextAction('问清对方报价的条件，拿到客户的目标价');
    }
  };

  // ------------------------------------------------------------ 保存

  const isPi =
    selected.length > 0 && selected.every((d) => d.targetType === TARGET.PI);
  const vague = VAGUE.test(nextAction.replace(/[\s，。,.!！?？]/g, ''));

  const effect = (() => {
    const parts: string[] = [];
    const advance =
      situation & SITUATION.NEGOTIATING
        ? 'S6 商务谈判'
        : situation & SITUATION.FEEDBACK
          ? 'S5 报价反馈'
          : undefined;
    const moved = advance
      ? selected.filter(
          (d) =>
            d.stageGroup === GROUP.AFTER_QUOTE &&
            advance.slice(0, 2) > d.stageCode,
        )
      : [];
    if (moved.length === 1 && selected.length > 1)
      parts.push(`${moved[0].docNo ?? '单据'} 推进到 ${advance}`);
    else if (moved.length === 1) parts.push(`推进到 ${advance}`);
    else if (moved.length > 1)
      parts.push(`${moved.length} 张单据推进到 ${advance}`);
    const when =
      nextDate &&
      (nextDate.isSame(dayjs(), 'day') ? '今天' : fmtDate(nextDate));
    if (when && nextAction.trim())
      parts.push(
        selected.length > 1
          ? `${selected.length} 张单据的下一步改到 ${when}`
          : `${when} 提醒你：${nextAction.trim()}`,
      );
    const stage = stageEffect(kind, answers, item);
    if (stage) parts.unshift(stage);
    if (suggestions.some((s) => s.checked))
      parts.push(
        `补 ${suggestions.filter((s) => s.checked).length} 项到客户档案`,
      );
    return parts.length ? `保存后：${parts.join('；')}` : undefined;
  })();

  const submit = async () => {
    if (!customerId) {
      message.warning('请先选择客户');
      return;
    }
    if (docs.length > 0 && selected.length === 0) {
      message.warning('请至少选一张单据');
      return;
    }
    if (mixedBlocked) {
      message.warning('要回答阶段问题，请只选同一阶段的单据');
      return;
    }
    if (!nextAction.trim()) {
      setNextError(true);
      return;
    }
    if (!nextDate) {
      message.warning('请选择什么时候做下一步');
      return;
    }
    const stageError = validateStage(kind, answers, iso(dayjs()));
    if (stageError) {
      message.warning(stageError);
      return;
    }
    const profile: Record<string, string> = {};
    for (const s of suggestions) if (s.checked) profile[s.field] = s.value;
    setSaving(true);
    try {
      const r = await followupApi.record({
        customerId,
        itemId: item?.id,
        itemIds: docs.length > 1 ? selected.map((d) => d.id) : undefined,
        situation,
        blockerCode: blocker,
        nextAction: nextAction.trim(),
        nextActionDate: iso(nextDate),
        channel,
        rawContent: raw || undefined,
        summary: summary || undefined,
        aiTaskId,
        expectedPayDate: isPi && payDate ? iso(payDate) : undefined,
        profile: Object.keys(profile).length ? profile : undefined,
        idempotencyKey: keyRef.current,
        ...(mixed ? {} : stagePayload(kind, answers, item?.stageCode)),
      });
      message.success(
        r.ended
          ? '已记录，售后结束'
          : r.stageAfter && r.stageAfter !== r.stageBefore
            ? `已记录，阶段推进到 ${r.stageAfter}`
            : '已记录这次跟进',
      );
      if (r.orderCompleted === false)
        message.warning('订单还有型号没出运，没有标为已收货');
      if (item && needsAfterSales(kind, answers))
        setAfterSales({
          soId: item.targetId,
          symptom: answers.issueTypes.join('、'),
        });
      onDone(
        r,
        kind === 'account' &&
          answers.accountAnswer === ACCOUNT_ANSWER.NEW_NEED &&
          item
          ? item.customerId
          : undefined,
        kind === 'develop' && answers.touchResult === TOUCH.REFUSED
          ? item
          : undefined,
      );
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const today = dayjs().startOf('day');
  const dateShortcuts = shortcutsOf(today);
  const nurtureItem = onNurture
    ? nurtureCandidate(selected, nextDate, today)
    : undefined;

  const chip = (active: boolean) => ({
    cursor: 'pointer',
    padding: '2px 10px',
    fontSize: 13,
    borderRadius: 14,
    borderColor: active ? palette.link : palette.hairline,
    background: active ? palette.accentSoft : 'transparent',
    color: active ? palette.link : palette.sub,
  });

  const orderLine =
    item && item.board === BOARD_DEVELOP
      ? [item.prospectReason && `为什么值得开发：${item.prospectReason}`]
          .filter(Boolean)
          .join(' · ')
      : item && item.board === BOARD_ACCOUNT
        ? [
            item.accountReason && `触达理由：${item.accountReason}`,
            item.strategic && '战略客户',
          ]
            .filter(Boolean)
            .join(' · ')
        : item && item.board !== BOARD_SALES
          ? [
              item.docNo && `订单 ${item.docNo}`,
              item.afterSalesNo && `售后单 ${item.afterSalesNo}`,
              item.etaDate &&
                (item.signedDate
                  ? `${fmtDate(item.signedDate)} 签收`
                  : `预计 ${fmtDate(item.etaDate)} 送达`),
              item.etaNote,
            ]
              .filter(Boolean)
              .join(' · ')
          : undefined;

  return (
    <>
      <Modal
        title={customerName ? `记一次跟进 · ${customerName}` : '记一次跟进'}
        open={open}
        width={720}
        onCancel={onClose}
        destroyOnHidden
        footer={
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              justifyContent: 'space-between',
            }}
          >
            <span
              style={{ fontSize: 12, color: palette.sub, textAlign: 'left' }}
            >
              {effect}
            </span>
            <Space>
              <Button onClick={onClose}>取消</Button>
              <Button
                type="primary"
                loading={saving}
                disabled={mixedBlocked}
                onClick={submit}
              >
                保存
              </Button>
            </Space>
          </div>
        }
      >
        {/* 客户与单据 */}
        <div
          style={{
            display: 'flex',
            gap: 8,
            alignItems: 'center',
            flexWrap: 'wrap',
          }}
        >
          {!target?.customerId && !target?.item && (
            <Select
              showSearch={{ filterOption: false, onSearch: searchCustomer }}
              placeholder="搜索客户名称"
              style={{ width: 320 }}
              value={customerId}
              options={customerOptions}
              onFocus={() => customerOptions.length || searchCustomer('')}
              onChange={(v, opt) => {
                setCustomerId(v);
                setCustomerName((opt as { label: string }).label);
                pickDocs([]);
              }}
            />
          )}
          {item && docs.length <= 1 && item.board !== BOARD_SALES && (
            <StageTag item={item} />
          )}
          {item && docs.length <= 1 && item.board === BOARD_SALES && (
            <Tag variant="filled" color="blue">
              {item.stageCode} {item.stageName}
              {item.docNo && ` · ${item.docNo}`}
              {item.amount != null &&
                !!item.currencyCode &&
                ` · ${formatAmount(item.amount, item.currencyCode)}`}
            </Tag>
          )}
        </div>
        {orderLine && docs.length <= 1 && (
          <div style={{ fontSize: 12, color: palette.sub, marginTop: 6 }}>
            {orderLine}
          </div>
        )}
        {docs.length > 1 && (
          <DocChecklist
            docs={docs}
            checked={checked}
            all={allDocs}
            onShowAll={() => setAllDocs(true)}
            onChange={setChecked}
          />
        )}
        {mixed && item && item.board !== BOARD_SALES && (
          <Alert
            style={{ marginTop: 12 }}
            type="warning"
            showIcon
            title={
              mixedBlocked
                ? '所选单据阶段不同，不能一起回答阶段问题。请只选同一阶段的单据'
                : '所选单据阶段不同，只填通用内容。要回答阶段问题，请只选同一阶段的单据'
            }
          />
        )}

        {/* 粘贴区 */}
        <SectionLabel>粘贴聊天或邮件（选填，粘贴后 AI 自动整理）</SectionLabel>
        <Input.TextArea
          rows={4}
          value={raw}
          placeholder="把和客户的 WhatsApp 聊天或邮件直接粘贴进来"
          onChange={(e) => setRaw(e.target.value)}
          onPaste={(e) => {
            const el = e.currentTarget;
            // 等输入框更新后再整理
            window.setTimeout(() => runAi(el.value), 0);
          }}
        />
        <div
          style={{
            display: 'flex',
            gap: 8,
            alignItems: 'center',
            marginTop: 8,
            flexWrap: 'wrap',
          }}
        >
          {!raw && (!item || item.board === BOARD_SALES) && (
            <>
              <span style={{ fontSize: 12, color: palette.mute }}>
                没有聊天可贴：
              </span>
              <Button size="small" onClick={() => quick('noReply')}>
                没回复
              </Button>
              <Button size="small" onClick={() => quick('sent')}>
                已发资料
              </Button>
              <Button size="small" onClick={() => quick('compare')}>
                客户在比价
              </Button>
            </>
          )}
          {raw && (
            <Button
              size="small"
              icon={<RobotOutlined />}
              loading={aiState === 'running'}
              disabled={!customerId}
              onClick={() => runAi(raw)}
            >
              {aiState === 'running'
                ? 'AI 正在整理…'
                : aiState === 'done'
                  ? '重新整理'
                  : 'AI 整理'}
            </Button>
          )}
          {raw.length > RAW_MAX && (
            <span style={{ fontSize: 12, color: palette.orange }}>
              内容超过 20,000 字，只取最后 20,000 字整理
            </span>
          )}
        </div>
        {aiState === 'running' && (
          <div style={{ marginTop: 8, fontSize: 12, color: palette.sub }}>
            <LoadingOutlined /> AI 正在整理，通常 10–20 秒；也可以先手工填写
          </div>
        )}
        {aiState === 'failed' && aiHint && (
          <Alert
            style={{ marginTop: 8 }}
            type="warning"
            showIcon
            title={aiHint}
          />
        )}
        {aiState === 'done' && (
          <div
            style={{
              marginTop: 10,
              padding: '10px 12px',
              borderRadius: 10,
              background: palette.violetSoft,
            }}
          >
            <div
              style={{ fontSize: 12, color: palette.violet, marginBottom: 4 }}
            >
              <RobotOutlined /> AI 摘要（可修改）
              {truncated && '　· 原文过长，只整理了最后 20,000 字'}
            </div>
            <Input.TextArea
              autoSize={{ minRows: 1, maxRows: 4 }}
              variant="borderless"
              value={summary}
              onChange={(e) => setSummary(e.target.value)}
              style={{ padding: 0, color: palette.ink }}
            />
          </div>
        )}

        {item && kind !== 'general' && (
          <StageQuestions
            kind={kind}
            item={item}
            value={answers}
            onChange={(a) => {
              // 客户经营答「当前没有需求」：下一步默认按等级间隔再联系
              if (
                kind === 'account' &&
                a.accountAnswer === ACCOUNT_ANSWER.NO_NEED &&
                answers.accountAnswer !== ACCOUNT_ANSWER.NO_NEED &&
                !dateTouched &&
                item.followDays
              )
                pickDate(dayjs().startOf('day').add(item.followDays, 'day'));
              setAnswers(a);
            }}
          />
        )}

        {kind === 'general' && (!mixed || item?.board === BOARD_SALES) && (
          <>
            {/* 客户情况 */}
            <SectionLabel>客户情况</SectionLabel>
            <Checkbox.Group
              value={SITUATION_OPTIONS.filter((o) => situation & o.value).map(
                (o) => o.value,
              )}
              options={SITUATION_OPTIONS}
              onChange={(vals) =>
                setSituation((vals as number[]).reduce((a, b) => a | b, 0))
              }
            />

            {/* 卡点 */}
            <SectionLabel>主要卡点（单选，可不选）</SectionLabel>
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {blockers.map((b) => (
                <Tag.CheckableTag
                  key={b.code}
                  checked={blocker === b.code}
                  onChange={(c) => setBlocker(c ? b.code : undefined)}
                  style={chip(blocker === b.code)}
                >
                  {b.name}
                </Tag.CheckableTag>
              ))}
            </div>
          </>
        )}

        {/* 下一步 */}
        <SectionLabel required>下一步要做什么</SectionLabel>
        {nextOptions.length > 0 && (
          <div
            style={{
              display: 'flex',
              gap: 6,
              flexWrap: 'wrap',
              marginBottom: 6,
            }}
          >
            <span
              style={{ fontSize: 12, color: palette.mute, lineHeight: '24px' }}
            >
              AI 建议：
            </span>
            {nextOptions.map((n) => (
              <Tag.CheckableTag
                key={n.text}
                checked={nextAction === n.text}
                style={chip(nextAction === n.text)}
                onChange={() => {
                  setNextAction(n.text.slice(0, NEXT_MAX));
                  if (n.date) pickDate(dayjs(n.date));
                  setNextError(false);
                }}
              >
                {n.text}
              </Tag.CheckableTag>
            ))}
          </div>
        )}
        <Input
          value={nextAction}
          maxLength={NEXT_MAX}
          status={nextError ? 'error' : undefined}
          placeholder="例如：发替代型号对比，问客户能否接受"
          onChange={(e) => {
            setNextAction(e.target.value);
            if (e.target.value.trim()) setNextError(false);
          }}
        />
        {nextError && (
          <div style={{ fontSize: 12, color: palette.red, marginTop: 4 }}>
            请写下一步要做什么
          </div>
        )}
        {!nextError && vague && (
          <div style={{ fontSize: 12, color: palette.orange, marginTop: 4 }}>
            <BulbOutlined />{' '}
            写清楚要做什么、要客户给什么，例如：发替代型号对比，问客户能否接受
          </div>
        )}

        <SectionLabel required>什么时候</SectionLabel>
        <Space wrap>
          {dateShortcuts.map((d) => (
            <Button
              key={d.label}
              size="small"
              type={nextDate?.isSame(d.value, 'day') ? 'primary' : 'default'}
              onClick={() => pickDate(d.value)}
            >
              {d.label}
            </Button>
          ))}
          <DatePicker
            size="small"
            value={nextDate}
            placeholder="选日期"
            onChange={pickDate}
            disabledDate={(d) => d.isBefore(today)}
          />
          {nextDate?.isSame(suggest.date, 'day') && suggest.reason && (
            <span style={{ fontSize: 12, color: palette.link }}>
              <ThunderboltOutlined /> 推荐：{suggest.reason}
            </span>
          )}
        </Space>
        {nurtureItem && nextDate && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              marginTop: 10,
              padding: '8px 12px',
              borderRadius: 10,
              fontSize: 12,
              color: palette.link,
              background: palette.accentSoft,
            }}
          >
            <HourglassOutlined />
            <span style={{ flex: 1 }}>
              隔这么久再跟，可以直接转培育，不占用待办和阶段停留
            </span>
            <Button
              type="link"
              size="small"
              style={{ padding: 0 }}
              onClick={() =>
                onNurture?.(nurtureItem, nextDate.diff(today, 'day'))
              }
            >
              改为转培育
            </Button>
          </div>
        )}

        <SectionLabel>方式</SectionLabel>
        <Segmented
          value={channel}
          options={CHANNEL_OPTIONS}
          onChange={(v) => setChannel(v as number)}
        />

        {isPi && (
          <>
            <SectionLabel>客户说哪天付（选填）</SectionLabel>
            <DatePicker
              value={payDate}
              onChange={setPayDate}
              disabledDate={(d) => d.isBefore(today)}
            />
          </>
        )}

        {suggestions.length > 0 && (
          <>
            <SectionLabel>顺手补到客户档案</SectionLabel>
            <div style={{ display: 'grid', gap: 6 }}>
              {suggestions.map((s, i) => (
                <Checkbox
                  key={s.field}
                  checked={!!s.checked}
                  onChange={(e) =>
                    setSuggestions((list) =>
                      list.map((x, j) =>
                        j === i ? { ...x, checked: e.target.checked } : x,
                      ),
                    )
                  }
                >
                  <span style={{ color: palette.mute }}>{s.label}：</span>
                  {s.value}
                  {s.evidence && (
                    <span style={{ fontSize: 12, color: palette.mute }}>
                      {' '}
                      （{s.evidence}）
                    </span>
                  )}
                </Checkbox>
              ))}
            </div>
            <div style={{ fontSize: 12, color: palette.mute, marginTop: 4 }}>
              档案里已有值的不会被覆盖
            </div>
          </>
        )}
      </Modal>
      <CreateAfterSalesModal
        open={!!afterSales}
        defaults={afterSales}
        onClose={() => setAfterSales(undefined)}
        onDone={() => setAfterSales(undefined)}
      />
    </>
  );
};

export default RecordFollowupModal;
