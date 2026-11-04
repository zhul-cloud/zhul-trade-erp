import { request } from '@umijs/max';

export { readBizError } from '@/pages/crm/opportunity/service';

// ---------------------------------------------------------------- 码值（与后端 FollowupConstants 一致）

export const BOARD_SALES = 2;
export const BOARD_FULFIL = 3;
export const BOARD_AFTER_SALES = 4;
export const BOARD_ACCOUNT = 5;
/** 已上线、可以进入的板块 */
export const OPEN_BOARDS = [
  BOARD_SALES,
  BOARD_FULFIL,
  BOARD_AFTER_SALES,
  BOARD_ACCOUNT,
];
export const GROUP = { BEFORE_QUOTE: 1, AFTER_QUOTE: 2, AFTER_PI: 3 } as const;
export const TARGET = {
  OPPORTUNITY: 1,
  INQUIRY: 2,
  QUOTATION: 3,
  PI: 4,
  ORDER: 5,
  CUSTOMER: 6,
};
export const NEXT_SYSTEM = 1;
export const NEXT_USER = 2;
/** 来自售后单的下一步（A3、A4）：只读，不进今日待办 */
export const NEXT_AFTER_SALES = 4;
/** 预计送达来源：0-未出运、1-系统估算、2-业务员改的 */
export const ETA_USER = 2;

/** 履约「沟通了什么」位掩码 */
export const NOTICE = {
  WAYBILL: 1,
  NEW_DATE: 2,
  NEED_CUSTOMER: 4,
  NO_REPLY: 8,
} as const;
export const NOTICE_OPTIONS = [
  { value: NOTICE.WAYBILL, label: '已把运单号和发货照片发给客户' },
  { value: NOTICE.NEW_DATE, label: '已告诉客户新的交期 / 送达日' },
  {
    value: NOTICE.NEED_CUSTOMER,
    label: '客户要我们配合（资料、改地址、税号）',
  },
  { value: NOTICE.NO_REPLY, label: '客户没回复' },
];
/** A1 客户收到了吗、A2 用得好吗：1-没问题、2-有问题、3-还没 */
export const ANSWER = { OK: 1, ISSUE: 2, NOT_YET: 3 } as const;
export const ISSUE_TYPES = ['少件', '破损', '型号不对', '其他'];
export const SATISFACTION = {
  SATISFIED: 1,
  NEUTRAL: 2,
  DISSATISFIED: 3,
} as const;
export const DISSATISFIED_REASONS = [
  { value: 'QUALITY', label: '产品质量' },
  { value: 'DELIVERY', label: '交期' },
  { value: 'COMMUNICATION', label: '沟通' },
  { value: 'PRICE', label: '价格' },
  { value: 'OTHER', label: '其他' },
];
/** 评价状态：1-这次先不邀请、2-已邀请、3-已评价、4-婉拒、6-还没评（只在跟进记录上） */
export const REVIEW = {
  SKIP: 1,
  INVITED: 2,
  DONE: 3,
  DECLINED: 4,
  NOT_YET: 6,
} as const;
export const REVIEW_OPTIONS_A5 = [
  { value: REVIEW.SKIP, label: '这次先不邀请' },
  { value: REVIEW.INVITED, label: '已邀请' },
  { value: REVIEW.DONE, label: '客户已评价' },
  { value: REVIEW.DECLINED, label: '客户婉拒' },
];
export const REVIEW_OPTIONS_A6 = [
  { value: REVIEW.DONE, label: '已评价' },
  { value: REVIEW.DECLINED, label: '婉拒' },
  { value: REVIEW.NOT_YET, label: '还没评' },
];
/** 「AI 写一条」的意图 */
export const INTENT = {
  LOGISTICS: 'logistics_update',
  REVIEW: 'review_invite',
  ACCOUNT: 'account_touch',
} as const;

/** 客户经营「客户情况」单选：1-有新需求、2-聊了新品类、3-当前没有需求、4-没回复 */
export const ACCOUNT_ANSWER = {
  NEW_NEED: 1,
  CROSS: 2,
  NO_NEED: 3,
  NO_REPLY: 4,
} as const;
export const NO_NEED_REASONS = [
  { value: 'NO_PROJECT', label: '近期没有项目' },
  { value: 'NO_BUDGET', label: '预算没下来' },
  { value: 'NOT_RELEVANT', label: '暂不相关' },
  { value: 'OTHER', label: '其他' },
];
export const RECORD_USER = 1;

/** 客户情况位掩码 */
export const SITUATION = { FEEDBACK: 1, NEGOTIATING: 2, NO_REPLY: 4 } as const;
export const SITUATION_OPTIONS = [
  { value: SITUATION.FEEDBACK, label: '客户给了反馈' },
  { value: SITUATION.NEGOTIATING, label: '在谈条件' },
  { value: SITUATION.NO_REPLY, label: '没回复' },
];

export const CHANNEL_OPTIONS = [
  { value: 1, label: 'WhatsApp' },
  { value: 2, label: '邮件' },
  { value: 3, label: '电话' },
  { value: 4, label: '其他' },
];

/** AI 任务状态 */
export const AI_STATUS = { PENDING: 1, RUNNING: 2, DONE: 3, FAILED: 4 };
export const AI_NOT_CONFIGURED = 'AI_NOT_CONFIGURED';

export const PERM_RECORD = 'crm:followup:record';
export const PERM_TEAM = 'crm:followup:team';

// ---------------------------------------------------------------- 类型

export interface FollowupItem {
  id: number;
  board: number;
  targetType: number;
  targetId: number;
  docNo?: string;
  versionNo?: number;
  customerId: number;
  customerName: string;
  country?: string;
  /** 经营等级名称：S / A / B / C，空为未成交 */
  bizGrade?: string;
  potentialRating?: number;
  stageCode: string;
  stageName: string;
  stageGroup: number;
  currencyCode?: string;
  amount?: number;
  receivedAmount?: number;
  unpaidAmount?: number;
  amountCny?: number;
  sentAt?: string;
  sentDays?: number;
  receiptStatus?: number;
  expectedPayDate?: string;
  blockerCode?: string;
  blockerName?: string;
  lastFollowupAt?: string;
  lastChannelName?: string;
  lastSummary?: string;
  nextAction?: string;
  nextActionDate?: string;
  nextActionSource?: number;
  overdueDays?: number;
  snoozeCount?: number;
  manySnoozes?: boolean;
  noReplyStreak?: number;
  nurtureUntil?: string;
  nurtureReason?: string;
  nurtureReasonName?: string;
  suggestNurture?: boolean;
  sopHint?: string;
  ownerId?: number;
  ownerName?: string;
  opportunityId?: number;
  // ---- 订单履约、售后评价
  /** 现在的情况（系统带出） */
  situationText?: string;
  partCount?: number;
  /** 履约异常位：1-采购超期未到仓、2-有到货差异待处理、4-出库单已撤回 */
  exceptionFlags?: number;
  exceptionText?: string;
  exceptionSince?: string;
  etaDate?: string;
  etaSource?: number;
  etaNote?: string;
  signedDate?: string;
  signedDays?: number;
  satisfaction?: number;
  satisfactionName?: string;
  reviewStatus?: number;
  reviewStatusName?: string;
  reviewPlatform?: string;
  afterSalesId?: number;
  afterSalesNo?: string;
  afterSalesStatusName?: string;
  stageSince?: string;
  // ---- 客户经营
  patternText?: string;
  patternBrands?: string;
  patternNextDate?: string;
  recentOrderCount?: number;
  lastOrderDate?: string;
  /** 经营建议（触达理由） */
  accountReason?: string;
  /** 1：记跟进选了「客户有新需求」还没建询盘 */
  accountNeed?: number;
  crossCategory?: string;
  dormantSince?: string;
  dormantDays?: number;
  strategic?: boolean;
  /** 该等级的联系间隔（天） */
  followDays?: number;
  lastContactDate?: string;
  /** 上次联系已超过等级间隔 */
  contactOverdue?: boolean;
}

/** 客户经营作战卡（G1–G3 结果、交叉销售、战略客户与年度计划） */
export interface AccountCard {
  customerId: number;
  bizGrade?: string;
  bizGradeScore?: number;
  followDays?: number;
  profileDone: number;
  profileTotal: number;
  patternText?: string;
  patternCycleDays?: number;
  patternNextDate?: string;
  patternBrands?: string;
  currencyCode: string;
  recentAmount?: number;
  recentOrderCount?: number;
  lastOrderDate?: string;
  lastOrderItems?: string;
  reason?: string;
  dormantSince?: string;
  dormantDays?: number;
  crossCategory?: string;
  crossSell: { category: string; customers: number }[];
  strategic: boolean;
  strategicAt?: string;
  suggestStrategic: boolean;
  lastReviewDate?: string;
  nextReviewDate?: string;
  plan?: AnnualPlan;
}
export interface AnnualPlan {
  planYear: number;
  currencyCode: string;
  targetAmount?: number;
  focus?: string;
  note?: string;
  dealtAmount: number;
  /** 完成比例（HALF_UP 整数），没有目标时为空 */
  percent?: number;
}

export interface OverviewStage {
  code: string;
  name: string;
  count: number;
}
export interface OverviewGroup {
  group: number;
  name: string;
  count: number;
  stages: OverviewStage[];
}
export interface OverviewBoard {
  board: number;
  name: string;
  step: number;
  available: boolean;
  customers: number;
  overdue: number;
  total: number;
  nurture: number;
  /** 订单履约：有异常的跟进对象数 */
  exceptions?: number;
  /** 客户经营：沉睡客户数 */
  dormant?: number;
  groups: OverviewGroup[];
}
export interface Overview {
  todayOverdue: number;
  todayDue: number;
  boards: OverviewBoard[];
}

export interface Today {
  overdue: FollowupItem[];
  today: FollowupItem[];
  weekRemaining: number;
  tomorrow: number;
}

export interface CurrencyAmount {
  currencyCode: string;
  amount: number;
}

export interface ItemList {
  total: number;
  records: FollowupItem[];
  unpaid: CurrencyAmount[];
}

export interface ItemQuery {
  board?: number;
  group?: number;
  stage?: string;
  nurture?: boolean;
  ownerScope?: string;
  blocker?: string;
  keyword?: string;
  /** 订单履约：只看有异常的 */
  exception?: boolean;
  /** 客户经营：只看沉睡 */
  dormant?: boolean;
  page?: number;
  pageSize?: number;
}

export interface Blocker {
  code: string;
  name: string;
  lostReason?: number;
  playbook: string[];
}

export interface SopCard {
  board: number;
  stageCode: string;
  name: string;
  goal?: string;
  checklist: string[];
  gateText?: string;
  autoHint?: string;
  exampleDialog?: string;
  blockers: Blocker[];
}

export interface ProfileField {
  field: string;
  label: string;
  value: string;
}

export interface FollowupRecord {
  id: number;
  recordType: number;
  title?: string;
  situation?: number;
  situationLabels: string[];
  blockerCode?: string;
  blockerName?: string;
  nextAction?: string;
  nextActionDate?: string;
  nextDone?: boolean;
  channel?: number;
  channelName?: string;
  summary?: string;
  rawContent?: string;
  stageBefore?: string;
  stageAfter?: string;
  profileFields: ProfileField[];
  operatorName?: string;
  targetType?: number;
  docNo?: string;
  createTime: string;
}

export interface CustomerFollowup {
  current?: FollowupItem;
  step?: number;
  lastContactAt?: string;
  lastChannelName?: string;
  recent30?: number;
  noReplyStreak?: number;
  /** 这位客户有在跟进对象的板块 */
  boards?: number[];
  quoteCount?: number;
  piCount?: number;
  openAmounts: CurrencyAmount[];
  total?: number;
  records: FollowupRecord[];
}

export interface RecordRequest {
  customerId: number;
  itemId?: number;
  situation?: number;
  blockerCode?: string;
  nextAction: string;
  nextActionDate: string;
  channel?: number;
  rawContent?: string;
  summary?: string;
  aiTaskId?: number;
  expectedPayDate?: string;
  profile?: Record<string, string>;
  idempotencyKey: string;
  // ---- 订单履约、售后评价（只能在对应阶段提交）
  noticeMask?: number;
  etaDate?: string;
  receiptAnswer?: number;
  useAnswer?: number;
  issueTypes?: string[];
  satisfaction?: number;
  dissatisfiedReason?: string;
  reviewStatus?: number;
  reviewPlatform?: string;
  // ---- 客户经营
  accountAnswer?: number;
  crossCategory?: string;
  noNeedReason?: string;
  quarterReview?: boolean;
}

export interface RecordResult {
  recordId: number;
  stageBefore?: string;
  stageAfter?: string;
  opportunityAdvanced?: boolean;
  duplicate?: boolean;
  /** A1 已收到无问题时订单是否确认为客户已收货 */
  orderCompleted?: boolean;
  /** 跟进对象是否已结束（售后结束） */
  ended?: boolean;
}

export interface ExtractResult {
  taskId: number;
  status: number;
  errorMessage?: string;
  result?: {
    summary?: string;
    situation?: number;
    blockerCode?: string;
    blockerName?: string;
    nextActions: { text: string; date?: string }[];
    profileSuggestions: {
      field: string;
      label: string;
      value: string;
      evidence?: string;
      checked?: boolean;
    }[];
    truncated?: boolean;
    receiptAnswer?: number;
    useAnswer?: number;
    issueTypes?: string[];
    satisfaction?: number;
    reviewStatus?: number;
    etaDate?: string;
    /** 客户经营的客户情况建议 */
    accountAnswer?: number;
  };
}

/** 订单履约行展开的分部分进度 */
export interface OrderParts {
  parts: {
    label: string;
    goods?: string;
    poId?: number;
    poNo?: string;
    supplier?: string;
    stageCode: string;
    stageName: string;
    expectedShipDate?: string;
    arrival?: string;
    situation?: string;
    tell?: string;
  }[];
  told: {
    date: string;
    node: string;
    content?: string;
    channelName?: string;
  }[];
  batches: {
    shipmentId: number;
    shNo: string;
    carrier?: string;
    waybillNo?: string;
    shippedDate?: string;
    etaDate?: string;
  }[];
}

export interface TeamCompare {
  value: number;
  prev: number;
}
export interface TeamMoney {
  count: number;
  amountCny?: number;
}
export interface TeamOwner {
  ownerId: number;
  ownerName: string;
  /** 五个板块的跟进对象数 */
  boards: number[];
  todayDue: number;
  overdue: number;
  manySnoozes: number;
  records: number;
  prevRecords: number;
  attention: boolean;
}
export interface TeamStage {
  board: number;
  stageCode: string;
  stageName: string;
  count: number;
  avgDays?: number;
  refDays?: number;
  over?: boolean;
}
export interface TeamException {
  type: string;
  typeName: string;
  itemId: number;
  board: number;
  customerId: number;
  customerName: string;
  stageCode: string;
  stageName: string;
  ownerId?: number;
  ownerName?: string;
  currencyCode?: string;
  amount?: number;
  text?: string;
}
export interface Team {
  period: string;
  from: string;
  to: string;
  prevFrom: string;
  prevTo: string;
  results: {
    newOpportunities: TeamCompare;
    quotationsSent: TeamCompare;
    pisSent: TeamCompare;
    orders: TeamMoney;
    onTime: number;
    delivered: number;
    reviewed: number;
    invited: number;
    declined: number;
    repeat: TeamMoney;
    /** 客户经营：应联系、按时、按时率（应联系为 0 时为空） */
    accountDue: number;
    accountOnTime: number;
    accountOnTimePercent?: number;
    accountInquiries: TeamCompare;
    dormant: number;
    activated: number;
  };
  owners: TeamOwner[];
  stages: TeamStage[];
  exceptions: { total: number; items: TeamException[] };
}

interface Resp<T> {
  data: T;
}

const BASE = '/api/v1/followups';

export const followupApi = {
  overview: (ownerScope?: string) =>
    request<Resp<Overview>>(`${BASE}/overview`, {
      params: { ownerScope },
    }).then((r) => r.data),
  today: (ownerScope?: string) =>
    request<Resp<Today>>(`${BASE}/today`, { params: { ownerScope } }).then(
      (r) => r.data,
    ),
  items: (params: ItemQuery) =>
    request<Resp<ItemList>>(`${BASE}/items`, { params }).then((r) => r.data),
  snooze: (id: number, date: string) =>
    request(`${BASE}/items/${id}/snooze`, { method: 'POST', data: { date } }),
  nurture: (
    id: number,
    data: { reason: string; until: string; note?: string },
  ) => request(`${BASE}/items/${id}/nurture`, { method: 'POST', data }),
  activate: (id: number) =>
    request(`${BASE}/items/${id}/activate`, { method: 'POST' }),
  record: (data: RecordRequest) =>
    request<Resp<RecordResult>>(`${BASE}/records`, {
      method: 'POST',
      data,
    }).then((r) => r.data),
  parts: (id: number) =>
    request<Resp<OrderParts>>(`${BASE}/items/${id}/parts`).then((r) => r.data),
  team: (params: { period?: string; deptId?: number; exceptions?: 'all' }) =>
    request<Resp<Team>>(`${BASE}/team`, { params }).then((r) => r.data),
  sop: (board: number, stage: string) =>
    request<Resp<SopCard>>(`${BASE}/sop/${board}/${stage}`).then((r) => r.data),
  /** board 为空时返回全部板块的卡点 */
  blockers: (board?: number) =>
    request<Resp<Blocker[]>>(`${BASE}/sop/blockers`, {
      params: { board },
    }).then((r) => r.data),
  customerRecords: (
    customerId: number,
    params: {
      /** all（默认）、user、system */
      type?: 'all' | 'user' | 'system';
      blockerOnly?: boolean;
      page?: number;
      pageSize?: number;
    },
  ) =>
    request<Resp<CustomerFollowup>>(`${BASE}/customers/${customerId}/records`, {
      params,
    }).then((r) => r.data),
  customerSummary: (customerId: number) =>
    request<Resp<CustomerFollowup>>(
      `${BASE}/customers/${customerId}/summary`,
    ).then((r) => r.data),
  account: (customerId: number) =>
    request<Resp<AccountCard>>(`${BASE}/accounts/${customerId}`).then(
      (r) => r.data,
    ),
  setStrategic: (customerId: number, strategic: boolean) =>
    request(`${BASE}/accounts/${customerId}/strategic`, {
      method: 'PUT',
      data: { strategic },
    }),
  savePlan: (
    customerId: number,
    data: { targetAmount: number; focus?: string; note?: string },
  ) =>
    request<Resp<AnnualPlan>>(`${BASE}/accounts/${customerId}/plan`, {
      method: 'PUT',
      data,
    }).then((r) => r.data),
  extract: (data: {
    customerId: number;
    itemId?: number;
    rawContent: string;
  }) =>
    request<Resp<ExtractResult>>(`${BASE}/extract`, {
      method: 'POST',
      data,
    }).then((r) => r.data),
  extractResult: (taskId: number, customerId: number) =>
    request<Resp<ExtractResult>>(`${BASE}/extract/${taskId}`, {
      params: { customerId },
    }).then((r) => r.data),
  replyDraft: (data: {
    customerId: number;
    itemId?: number;
    blockerCode?: string;
    note?: string;
    intent?: string;
  }) =>
    request<Resp<{ reply: string; intent?: string }>>(`${BASE}/reply-draft`, {
      method: 'POST',
      data,
    }).then((r) => r.data),
};
