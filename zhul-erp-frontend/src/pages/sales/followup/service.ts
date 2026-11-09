import { request } from '@umijs/max';

export { readBizError } from '@/pages/crm/opportunity/service';

// ---------------------------------------------------------------- 码值（与后端 FollowupConstants 一致）

export const BOARD_DEVELOP = 1;
export const BOARD_SALES = 2;
export const BOARD_FULFIL = 3;
export const BOARD_AFTER_SALES = 4;
export const BOARD_ACCOUNT = 5;
/** 已上线、可以进入的板块 */
export const OPEN_BOARDS = [
  BOARD_DEVELOP,
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
/** 按客户分组显示的板块 */
export const GROUPED_BOARDS = [BOARD_SALES, BOARD_FULFIL, BOARD_AFTER_SALES];
/** 记跟进必须回答阶段问题的阶段：不能批量跟进，选多张单据时阶段必须相同 */
export const ANSWER_STAGES = ['A1', 'A2', 'A5', 'A6'];
/** 批量操作一次最多的客户数 */
export const BATCH_MAX = 50;
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
  COLD: 'cold_outreach',
} as const;

/** 主动开发「触达结果」单选（与后端 FollowupConstants.TOUCH_* 一致） */
export const TOUCH = {
  SENT: 1,
  REPLIED: 2,
  SIGNAL: 3,
  NO_REPLY: 4,
  REFUSED: 5,
} as const;
export const TOUCH_OPTIONS = [
  { value: TOUCH.SENT, label: '已发出触达' },
  { value: TOUCH.REPLIED, label: '客户回复了' },
  { value: TOUCH.SIGNAL, label: '出现采购信号' },
  { value: TOUCH.NO_REPLY, label: '没回复' },
  { value: TOUCH.REFUSED, label: '明确拒绝' },
];
/** 开发对象的来源方式 */
export const PROSPECT_SOURCES = [
  { value: 1, label: 'Google' },
  { value: 2, label: 'LinkedIn' },
  { value: 3, label: '展会名录' },
  { value: 4, label: '海关数据' },
  { value: 5, label: '老客户介绍' },
  { value: 6, label: '其他' },
];
/** 停止开发的原因：明确拒绝 / 没有进展（三种）/ 其他（必须写说明） */
export const STOP = {
  REFUSED: 'REFUSED',
  MISMATCH: 'MISMATCH',
  NO_REPLY: 'NO_REPLY',
  UNREACHABLE: 'UNREACHABLE',
  OTHER: 'OTHER',
} as const;
/** 背调跟进优先级 */
export const REPORT_PRIORITY: Record<
  number,
  { label: string; tone: 'accent' | 'gray' | 'mute' | 'red' }
> = {
  1: { label: '重点跟进', tone: 'accent' },
  2: { label: '正常跟进', tone: 'gray' },
  3: { label: '低优先级', tone: 'mute' },
  4: { label: '不建议跟进', tone: 'red' },
};
export const ERROR_NOT_ELIGIBLE = 'DEVELOP_NOT_ELIGIBLE';
export const ERROR_OPPORTUNITY_ACTIVE = 'OPPORTUNITY_ALREADY_ACTIVE';
export const ERROR_CUSTOMER_DUPLICATE = 'CUSTOMER_DUPLICATE';
export const PERM_OPPORTUNITY_ADD = 'crm:opportunity:add';

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
  { value: 5, label: 'LinkedIn' },
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
  // ---- 主动开发
  prospectReason?: string;
  prospectSourceName?: string;
  contactName?: string;
  contactTitle?: string;
  contactEmail?: string;
  touchCount?: number;
  firstTouchDate?: string;
  /** 序列当前节点（第几天），未开始触达为 0 */
  sequenceDay?: number;
  signalText?: string;
  /** 背调跟进优先级 1–4，未出结论为空 */
  reportPriority?: number;
}

/** 主动开发作战卡 / 客户详情的开发状态 */
export interface DevelopCard {
  customerId: number;
  /** open-开发中、stopped-已停止可重新开发、eligible-可以加入、none-不能加入 */
  state: 'open' | 'stopped' | 'eligible' | 'none';
  reason?: string;
  itemId?: number;
  stageCode?: string;
  stageName?: string;
  prospectReason?: string;
  prospectSource?: number;
  prospectSourceName?: string;
  potentialRating?: number;
  touchCount?: number;
  firstTouchDate?: string;
  sequence?: number[];
  currentDay?: number;
  signalText?: string;
  stopReason?: string;
  stopReasonName?: string;
  stopNote?: string;
  canConvert?: boolean;
  report?: {
    id: number;
    status: number;
    priority?: number;
    score?: number;
    keyFinding?: string;
    firstAction?: string;
  };
  contacts: {
    name?: string;
    title?: string;
    email?: string;
    whatsapp?: string;
    phone?: string;
    primary?: boolean;
  }[];
}

export interface ProspectRequest {
  name: string;
  country: string;
  website?: string;
  reason: string;
  source?: number;
  contactName?: string;
  email?: string;
  whatsapp?: string;
  businessUnitId?: number;
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

/** 今日待办的一个板块：客户在这个板块所处的阶段、最紧急的到期单据和全部到期单据 */
export interface TodayBoard {
  board: number;
  stageCode: string;
  stageName: string;
  lead: FollowupItem;
  items: FollowupItem[];
}

/** 今日待办的一个客户：在多个板块都有到期单据时每个板块一行 */
export interface TodayCustomer {
  customerId: number;
  customerName: string;
  country?: string;
  bizGrade?: string;
  potentialRating?: number;
  ownerName?: string;
  overdueDays: number;
  boards: TodayBoard[];
}

/** 今日待办按客户：数字都是客户数 */
export interface Today {
  overdue: TodayCustomer[];
  today: TodayCustomer[];
  /** 今天已经记过跟进、下一步仍是今天的客户，不计入待办 */
  handled: TodayCustomer[];
  weekRemaining: number;
  tomorrow: number;
}

/** 按客户分组的一行：所处阶段为名下单据最靠后的阶段 */
export interface CustomerGroup {
  customerId: number;
  customerName: string;
  country?: string;
  bizGrade?: string;
  potentialRating?: number;
  ownerName?: string;
  stageCode: string;
  stageName: string;
  stageGroup?: number;
  docCount: number;
  /** targetType → 张数 */
  typeCounts: Record<string, number>;
  overdueDays: number;
  lead: FollowupItem;
  items: FollowupItem[];
  /** 按阶段筛选时在范围内的单据，其余标灰；没有筛选时为空 */
  inScope?: number[];
}

export interface GroupList {
  total: number;
  docTotal: number;
  records: CustomerGroup[];
  unpaid: CurrencyAmount[];
}

/** 批量跟进 / 批量延后的一个客户 */
export interface BatchEntry {
  customerId: number;
  itemIds: number[];
}

export interface BatchResult {
  success: number;
  items: number;
  failures: { customerId: number; customerName: string; reason: string }[];
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
  /** 客户国家 */
  country?: string;
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
  /** 一次跟进涉及多张单据时的单号 */
  docNos?: string[];
  createTime: string;
}

export interface CustomerFollowup {
  current?: FollowupItem;
  /** 当前跟进对象所在板块的全部单据，记一次跟进时默认全选 */
  currentItems?: FollowupItem[];
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
  /** 这次跟进涉及的单据（同一客户、同一板块）；传了就忽略 itemId */
  itemIds?: number[];
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
  // ---- 主动开发
  touchResult?: number;
  signalText?: string;
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
    /** 主动开发的触达结果建议 */
    touchAnswer?: number;
    signalText?: string;
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
    /** 主动开发：新增、首次触达、回复、回复率（首次触达为 0 时为空）、转商机、停止 */
    developNew: number;
    developTouched: number;
    developReplied: number;
    developReplyPercent?: number;
    developConverted: number;
    developStopped: number;
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
  /** 按客户分组的列表（商机成交、订单履约、售后评价） */
  groups: (params: ItemQuery) =>
    request<Resp<GroupList>>(`${BASE}/groups`, { params }).then((r) => r.data),
  batchRecord: (data: {
    entries: BatchEntry[];
    situation?: number;
    nextAction: string;
    nextActionDate: string;
    channel?: number;
    note?: string;
    idempotencyKey: string;
  }) =>
    request<Resp<BatchResult>>(`${BASE}/batch-record`, {
      method: 'POST',
      data,
    }).then((r) => r.data),
  /** 批量延后；今日待办客户卡片的「延后」也走这里 */
  batchSnooze: (entries: BatchEntry[], date: string) =>
    request<Resp<BatchResult>>(`${BASE}/batch-snooze`, {
      method: 'POST',
      data: { entries, date },
    }).then((r) => r.data),
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
  develop: (customerId: number) =>
    request<Resp<DevelopCard>>(`${BASE}/develop/${customerId}`).then(
      (r) => r.data,
    ),
  createProspect: (data: ProspectRequest) =>
    request<Resp<DevelopCard>>(`${BASE}/develop`, {
      method: 'POST',
      data,
    }).then((r) => r.data),
  joinDevelop: (
    customerId: number,
    data: { reason: string; source?: number },
  ) =>
    request<Resp<DevelopCard>>(`${BASE}/develop/${customerId}/join`, {
      method: 'POST',
      data,
    }).then((r) => r.data),
  reopenDevelop: (customerId: number) =>
    request<Resp<DevelopCard>>(`${BASE}/develop/${customerId}/reopen`, {
      method: 'POST',
    }).then((r) => r.data),
  convert: (
    itemId: number,
    data: {
      demandSummary?: string;
      firstContactDate?: string;
      ownerId?: number;
    },
  ) =>
    request<Resp<DevelopCard>>(`${BASE}/items/${itemId}/convert`, {
      method: 'POST',
      data,
    }).then((r) => r.data),
  stopDevelop: (itemId: number, data: { reason: string; note?: string }) =>
    request(`${BASE}/items/${itemId}/stop`, { method: 'POST', data }),
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
