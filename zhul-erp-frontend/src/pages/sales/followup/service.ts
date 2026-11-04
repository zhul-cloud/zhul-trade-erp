import { request } from '@umijs/max';

export { readBizError } from '@/pages/crm/opportunity/service';

// ---------------------------------------------------------------- 码值（与后端 FollowupConstants 一致）

export const BOARD_SALES = 2;
export const GROUP = { BEFORE_QUOTE: 1, AFTER_QUOTE: 2, AFTER_PI: 3 } as const;
export const TARGET = { OPPORTUNITY: 1, INQUIRY: 2, QUOTATION: 3, PI: 4 };
export const NEXT_SYSTEM = 1;
export const NEXT_USER = 2;
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
}

export interface RecordResult {
  recordId: number;
  stageBefore?: string;
  stageAfter?: string;
  opportunityAdvanced?: boolean;
  duplicate?: boolean;
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
  };
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
  sop: (stage: string) =>
    request<Resp<SopCard>>(`${BASE}/sop/${BOARD_SALES}/${stage}`).then(
      (r) => r.data,
    ),
  blockers: () =>
    request<Resp<Blocker[]>>(`${BASE}/sop/blockers`).then((r) => r.data),
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
  }) =>
    request<Resp<{ reply: string; intent?: string }>>(`${BASE}/reply-draft`, {
      method: 'POST',
      data,
    }).then((r) => r.data),
};
