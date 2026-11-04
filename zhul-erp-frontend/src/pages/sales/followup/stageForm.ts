import dayjs from 'dayjs';
import {
  ANSWER,
  BOARD_FULFIL,
  type ExtractResult,
  type FollowupItem,
  REVIEW,
  type RecordRequest,
  SATISFACTION,
} from './service';

/**
 * 订单履约、售后评价阶段「记一次跟进」中间区块（K12，spec「履约和售后阶段的记一次跟进」）：
 * 按阶段决定问什么、怎么校验、提交哪些字段、底部提示保存后会发生什么。纯函数，便于单测。
 */
export type StageKind =
  | 'fulfil'
  | 'receipt'
  | 'use'
  | 'satisfaction'
  | 'review'
  | 'general';

export interface StageAnswers {
  noticeMask: number;
  /** YYYY-MM-DD：F6 改预计送达、A1 还没收到时的新日期 */
  etaDate?: string;
  receiptAnswer?: number;
  useAnswer?: number;
  issueTypes: string[];
  satisfaction?: number;
  dissatisfiedReason?: string;
  reviewStatus?: number;
  reviewPlatform: string;
}

export const emptyAnswers = (): StageAnswers => ({
  noticeMask: 0,
  issueTypes: [],
  reviewPlatform: '',
});

export const stageKind = (item?: FollowupItem): StageKind => {
  if (!item) return 'general';
  if (item.board === BOARD_FULFIL) return 'fulfil';
  switch (item.stageCode) {
    case 'A1':
      return 'receipt';
    case 'A2':
      return 'use';
    case 'A5':
      return 'satisfaction';
    case 'A6':
      return 'review';
    default:
      return 'general';
  }
};

const md = (d: string) => dayjs(d).format('MM-DD');

/** 必填与取值校验，返回第一条错误；today 为 YYYY-MM-DD */
export const validateStage = (
  kind: StageKind,
  a: StageAnswers,
  today: string,
): string | undefined => {
  switch (kind) {
    case 'fulfil':
      return a.noticeMask ? undefined : '请选择这次和客户沟通了什么';
    case 'receipt':
      if (!a.receiptAnswer) return '请选择客户收到了吗';
      if (a.receiptAnswer === ANSWER.ISSUE && a.issueTypes.length === 0)
        return '请选择问题类型';
      if (a.receiptAnswer === ANSWER.NOT_YET) {
        if (!a.etaDate) return '还没收到时请填新的预计送达日';
        if (a.etaDate < today) return '新的预计送达日不能早于今天';
      }
      return undefined;
    case 'use':
      if (!a.useAnswer) return '请选择用得好吗';
      if (a.useAnswer === ANSWER.ISSUE && a.issueTypes.length === 0)
        return '请选择问题类型';
      return undefined;
    case 'satisfaction':
      if (!a.satisfaction) return '请选择客户满意吗';
      if (a.satisfaction === SATISFACTION.SATISFIED)
        return a.reviewStatus ? undefined : '请选择评价状态';
      return a.dissatisfiedReason
        ? undefined
        : `请选择${a.satisfaction === SATISFACTION.NEUTRAL ? '一般' : '不满意'}的原因`;
    case 'review':
      return a.reviewStatus ? undefined : '请选择评价状态';
    default:
      return undefined;
  }
};

/** 只提交当前阶段允许的字段（后端对不匹配的字段报「跟进对象已到其他阶段」） */
export const stagePayload = (
  kind: StageKind,
  a: StageAnswers,
  stageCode?: string,
): Partial<RecordRequest> => {
  const platform = a.reviewPlatform.trim() || undefined;
  switch (kind) {
    case 'fulfil':
      return {
        noticeMask: a.noticeMask || undefined,
        etaDate: stageCode === 'F6' ? a.etaDate : undefined,
      };
    case 'receipt':
      return {
        receiptAnswer: a.receiptAnswer,
        issueTypes: a.receiptAnswer === ANSWER.ISSUE ? a.issueTypes : undefined,
        etaDate: a.receiptAnswer === ANSWER.NOT_YET ? a.etaDate : undefined,
      };
    case 'use':
      return {
        useAnswer: a.useAnswer,
        issueTypes: a.useAnswer === ANSWER.ISSUE ? a.issueTypes : undefined,
      };
    case 'satisfaction': {
      const ok = a.satisfaction === SATISFACTION.SATISFIED;
      return {
        satisfaction: a.satisfaction,
        reviewStatus: ok ? a.reviewStatus : undefined,
        reviewPlatform: ok ? platform : undefined,
        dissatisfiedReason: ok ? undefined : a.dissatisfiedReason,
      };
    }
    case 'review':
      return { reviewStatus: a.reviewStatus, reviewPlatform: platform };
    default:
      return {};
  }
};

/** A1、A2 选「有问题」：保存后打开新建售后单 */
export const needsAfterSales = (kind: StageKind, a: StageAnswers) =>
  (kind === 'receipt' && a.receiptAnswer === ANSWER.ISSUE) ||
  (kind === 'use' && a.useAnswer === ANSWER.ISSUE);

/** 底部「保存后：…」里阶段相关的一句；没有选答案时为空 */
export const stageEffect = (
  kind: StageKind,
  a: StageAnswers,
  item?: FollowupItem,
): string | undefined => {
  const issue = '打开新建售后单，客户和订单已带好；售后单的进度自动同步到这里';
  switch (kind) {
    case 'fulfil': {
      const eta =
        item?.stageCode === 'F6' ? a.etaDate || item?.etaDate : undefined;
      return eta
        ? `阶段仍按单据走，不会被这条记录改动；${md(eta)} 自动转入「售后评价 · 签收确认」`
        : '阶段仍按单据走，不会被这条记录改动';
    }
    case 'receipt':
      if (a.receiptAnswer === ANSWER.OK)
        return '进入 A2 使用确认；订单所有型号都已出运时标为客户已收货';
      if (a.receiptAnswer === ANSWER.ISSUE) return issue;
      if (a.receiptAnswer === ANSWER.NOT_YET)
        return a.etaDate ? `仍在 A1，${md(a.etaDate)} 再问` : '仍在 A1';
      return undefined;
    case 'use':
      if (a.useAnswer === ANSWER.OK) return '进入 A5 满意度确认';
      if (a.useAnswer === ANSWER.ISSUE) return issue;
      if (a.useAnswer === ANSWER.NOT_YET) return '仍在 A2，按你定的日期再问';
      return undefined;
    case 'satisfaction':
      if (!a.satisfaction) return undefined;
      if (a.satisfaction !== SATISFACTION.SATISFIED)
        return '售后结束，不邀请评价；原因进客户档案和团队视图';
      if (a.reviewStatus === REVIEW.INVITED)
        return '进入 A6 邀请评价，没评时按提醒规则轻轻提醒';
      return a.reviewStatus ? '售后结束，客户移到「客户经营」' : undefined;
    case 'review':
      if (a.reviewStatus === REVIEW.NOT_YET) return '仍在 A6，按提醒规则继续';
      return a.reviewStatus ? '售后结束，客户移到「客户经营」' : undefined;
    default:
      return undefined;
  }
};

/** AI 整理的答案建议：只预填与当前阶段匹配、业务员还没选的 */
export const applyAiAnswers = (
  kind: StageKind,
  a: StageAnswers,
  r: NonNullable<ExtractResult['result']>,
  stageCode?: string,
): StageAnswers => {
  const next = { ...a };
  const issues = r.issueTypes ?? [];
  if (kind === 'receipt' && !a.receiptAnswer && r.receiptAnswer) {
    next.receiptAnswer = r.receiptAnswer;
    if (r.receiptAnswer === ANSWER.ISSUE && issues.length)
      next.issueTypes = issues;
    if (r.receiptAnswer === ANSWER.NOT_YET && r.etaDate)
      next.etaDate = r.etaDate;
  }
  if (kind === 'use' && !a.useAnswer && r.useAnswer) {
    next.useAnswer = r.useAnswer;
    if (r.useAnswer === ANSWER.ISSUE && issues.length) next.issueTypes = issues;
  }
  if (kind === 'satisfaction' && !a.satisfaction && r.satisfaction) {
    next.satisfaction = r.satisfaction;
    if (
      r.satisfaction === SATISFACTION.SATISFIED &&
      r.reviewStatus &&
      r.reviewStatus <= REVIEW.DECLINED
    )
      next.reviewStatus = r.reviewStatus;
  }
  if (
    kind === 'review' &&
    !a.reviewStatus &&
    (r.reviewStatus === REVIEW.DONE ||
      r.reviewStatus === REVIEW.DECLINED ||
      r.reviewStatus === REVIEW.NOT_YET)
  )
    next.reviewStatus = r.reviewStatus;
  if (kind === 'fulfil' && stageCode === 'F6' && !a.etaDate && r.etaDate)
    next.etaDate = r.etaDate;
  return next;
};
