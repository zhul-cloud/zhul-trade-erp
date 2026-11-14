import { request } from '@umijs/max';

export { readBizError } from '@/pages/crm/opportunity/service';

// ---------------------------------------------------------------- 码值（与后端 SupplyConstants 一致）

export const COVER = { NONE: 0, NO_CHANNEL: 1, LEAD: 2, CONFIRM: 3, READY: 4 };
export const COVER_NAMES: Record<number, string> = {
  1: '无渠道',
  2: '有线索未验证',
  3: '需确认',
  4: '可直接报价',
};
/** 覆盖状态的简称（分组列表的分布说明用） */
export const COVER_SHORT: Record<number, string> = {
  1: '无渠道',
  2: '未验证',
  3: '需确认',
  4: '可报',
};
export const COVER_HINTS: Record<number, string> = {
  4: '有效期内有价格证据，可以直接报价',
  3: '有过价格记录，报价前要先向供应商确认',
  2: '有主营该品牌的供应商，没有这个型号的价格',
  1: '没有可用的供应商',
};
export const DEMAND = { WATCHING: 1, IN_MATRIX: 2, PAUSED: 3 };
export const PRIORITY_NAMES: Record<number, string> = {
  1: '低',
  2: '中',
  3: '高',
};
export const LEVEL_NAMES: Record<number, string> = {
  1: '观察',
  2: '重复',
  3: '验证',
  4: '商业',
};
export const SRC = {
  INQUIRY: 1,
  ORDER: 2,
  STOCKOUT: 3,
  LOST: 4,
  SEARCH: 5,
  RESEARCH: 6,
};
export const SRC_NAMES: Record<number, string> = {
  1: '询盘',
  2: '销售订单',
  3: '询价无货',
  4: '报价未成交',
  5: '搜索关键词',
  6: '同行调研',
};
export const NEXT_SYSTEM = 2;
export const PROJECT = { ACTIVE: 1, DONE: 2, CANCELLED: 3 };

export const PERM_EDIT = 'supply:followup:edit';
export const PERM_TEAM = 'supply:followup:team';
export const PERM_PROJECT = 'supply:project:edit';
export const PERM_COVERAGE = 'supply:coverage:edit';
export const PERM_DEV = 'supply:dev:edit';

// ---------------------------------------------------------------- 第二期：供应商开发

export const HEAT_NAMES: Record<number, string> = { 3: '高', 2: '中', 1: '低' };
export const CAND = {
  TODO: 1,
  CONTACTED: 2,
  QUOTED: 3,
  QUALIFIED: 4,
  ABANDONED: 5,
};
export const CAND_NAMES: Record<number, string> = {
  1: '待联系',
  2: '已联系',
  3: '已报价',
  4: '合格',
  5: '已放弃',
};
export const CAND_SRC_NAMES: Record<number, string> = {
  1: '搜索',
  2: '展会',
  3: '同行介绍',
  4: '平台',
  5: '供应商库',
  6: '其他',
};
export const DEV_SOURCE_NAMES: Record<string, string> = {
  demand: '需求',
  expansion: '主动拓展',
  both: '需求 + 主动拓展',
};
export const DEV_STAGE_NAMES: Record<string, string> = {
  todo: '待开发',
  doing: '开发中',
  done: '已达标',
};

export const PATHS = {
  followup: '/supply-chain/followup',
  coverage: '/supply-chain/coverage',
  demand: (id: number) => `/supply-chain/demands/${id}`,
  project: (id: number) => `/supply-chain/projects/${id}`,
  /** categoryId 为 0 是未分类，也要带上 */
  models: (brandId: number, categoryId?: number | null) =>
    `/supply-chain/coverage/models?brandId=${brandId}${categoryId != null ? `&categoryId=${categoryId}` : ''}`,
  sourcingTask: (code: string) =>
    `/inquiry/sourcing-board?keyword=${encodeURIComponent(code)}`,
};

// ---------------------------------------------------------------- 类型

export interface Demand {
  id: number;
  brandId: number;
  brandName?: string;
  categoryId?: number;
  categoryName?: string;
  model?: string;
  status: number;
  pauseReason?: string;
  demandLevel: number;
  priority: number;
  suggestedPriority: number;
  priorityRule?: string;
  priorityManual?: boolean;
  priorityReason?: string;
  cntInquiry: number;
  cntOrder: number;
  cntStockout: number;
  cntLost: number;
  cntSearch: number;
  cntResearch: number;
  lastSignalAt?: string;
  coverageStatus: number;
  suggestedCoverage: number;
  coveragePending?: boolean;
  coverageBasis?: string;
  coverageNote?: string;
  evidenceDate?: string;
  evidenceValid?: boolean;
  /** 证据最后有效的一天 */
  evidenceExpireDate?: string;
  ownerId?: number;
  ownerName?: string;
  nextAction?: string;
  nextActionDate?: string;
  nextActionSource?: number;
  reminderType?: number;
  deferCount?: number;
  sourcingTask?: string;
  channelCount?: number;
  singleChannel?: boolean;
  primarySupplier?: string;
  activeProjectId?: number;
  createTime: string;
  createBy?: string;
  updateTime: string;
  updateBy?: string;
}

export interface Overview {
  todayModels: number;
  overdue: number;
  dueToday: number;
  weekRest: number;
  intelTotal: number;
  unclassified: number;
  watching: number;
  review: number;
  paused: number;
  coverTotal: number;
  coverage: Record<number, number>;
  stockoutOpen: number;
  pending: number;
  single: number;
  canEdit: boolean;
  canTeam: boolean;
  canProject: boolean;
  canCoverage: boolean;
  canSeeSupplier: boolean;
  canDev: boolean;
  evidenceDays: number;
  dev?: DevOverview;
}

export interface DevOverview {
  gapGroups: number;
  todo: number;
  doing: number;
  done: number;
  gapTotal: number;
}

export interface DevSupplier {
  id: number;
  name?: string;
  /** 1 询价记录、2 采购订单行；线索为 0 */
  evidenceType: number;
  evidenceDate?: string;
}

export interface Candidate {
  id: number;
  projectId: number;
  supplierId: number;
  name?: string;
  contactName?: string;
  contactInfo?: string;
  source: number;
  stage: number;
  abandonReason?: string;
  note?: string;
  evidenceType?: number;
  evidenceDate?: string;
  quotedAt?: string;
  nextAction?: string;
  nextActionDate?: string;
  ownerId?: number;
  ownerName?: string;
  lastAction?: string;
  lastActionAt?: string;
  lastActionByName?: string;
  overdueDays: number;
  linkReminder: boolean;
}

export interface DevGroup {
  key: string;
  brandId: number;
  brandName?: string;
  categoryId: number;
  categoryName?: string;
  heat: number;
  source: 'demand' | 'expansion' | 'both';
  expansionId?: number;
  expansionHeat?: number;
  expansionReason?: string;
  required: number;
  qualified: number;
  gap: number;
  stage: 'todo' | 'doing' | 'done';
  leads: number;
  stockoutModels: number;
  stockoutTimes: number;
  projectId?: number;
  projectName?: string;
  projectTarget?: number;
  projectQualified: number;
  projectOwnerId?: number;
  projectOwnerName?: string;
  projectDueDate?: string;
  projectOverdueDays: number;
  activeCandidates: number;
  urgentAction?: string;
  urgentDate?: string;
  overdue: number;
  qualifiedSuppliers: DevSupplier[];
  leadSuppliers: DevSupplier[];
  candidates: Candidate[];
}

export interface DevGroupPage {
  total: number;
  groups: DevGroup[];
}

export interface DevScope {
  brandId: number;
  brandName?: string;
  categoryId: number;
  categoryName?: string;
  heat: number;
  required: number;
  qualified: number;
  gap: number;
  expansionId?: number;
  expansionHeat?: number;
  expansionReason?: string;
  expansionOwnerId?: number;
  activeProjectId?: number;
  activeProjectName?: string;
  activeProjectTarget?: number;
  defaultName: string;
}

export interface ProjectDev {
  target: number;
  qualified: number;
  rangeQualified: number;
  required: number;
  rangeGap: number;
  suggestClose: boolean;
  candidates: Candidate[];
  others: DevSupplier[];
  records: TimelineItem[];
  canEdit: boolean;
  canSeeSupplier: boolean;
}

export interface DevTodayItem {
  kind: 'candidate' | 'project' | 'remind';
  key: string;
  projectId?: number;
  projectName?: string;
  brandId: number;
  brandName?: string;
  categoryId: number;
  categoryName?: string;
  candidateId?: number;
  candidateName?: string;
  text: string;
  date: string;
  overdue: boolean;
  overdueDays: number;
}

export interface DevGroupQuery {
  stage?: string;
  brandId?: number;
  parentCategoryId?: number;
  categoryId?: number;
  heat?: number;
  source?: string;
  ownerId?: number;
  keyword?: string;
  page?: number;
  size?: number;
}

export interface CandidateReq {
  supplierId?: number;
  name?: string;
  contactName?: string;
  contactInfo?: string;
  source?: number;
  note?: string;
}

export interface CandidateRecordReq {
  candidateIds: number[];
  content: string;
  stage?: number;
  abandonReason?: string;
  nextAction?: string;
  nextActionDate?: string;
  ownerId?: number;
  idempotencyKey: string;
}

export interface Group {
  key: string;
  brandId: number;
  brandName?: string;
  categoryId?: number;
  categoryName?: string;
  models: number;
  stockoutModels: number;
  stockoutTimes: number;
  coverage: Record<number, number>;
  overdue: number;
  earliestDue?: string;
  urgentAction?: string;
  ownerCount: number;
  ownerName?: string;
  projectId?: number;
  projectName?: string;
  projectSolved: number;
  projectTotal: number;
  items: Demand[];
}

export interface GroupPage {
  totalGroups: number;
  totalModels: number;
  groups: Group[];
}

export interface TodayCard {
  key: string;
  brandName?: string;
  categoryName?: string;
  brandId: number;
  categoryId?: number;
  board: 'intel' | 'cover';
  stage: string;
  overdue: boolean;
  overdueDays: number;
  source?: string;
  sopHint?: string;
  items: Demand[];
  projectId?: number;
}

export interface Today {
  overdue: number;
  dueToday: number;
  weekRest: number;
  overdueCards: TodayCard[];
  todayCards: TodayCard[];
  devItems?: DevTodayItem[];
}

export interface BattleCardData {
  code: string;
  name: string;
  goal?: string;
  checklist: string[];
  quality: string[];
  gate?: string;
  autoHint?: string;
}

export interface Signal {
  id: number;
  sourceType: number;
  sourceDocId?: number;
  sourceDocNo?: string;
  demandId?: number;
  brandId?: number;
  brandName?: string;
  brandRaw?: string;
  categoryId?: number;
  modelRaw?: string;
  quantity?: number;
  reasonText?: string;
  content?: string;
  platform?: string;
  occurCount?: number;
  periodStart?: string;
  periodEnd?: string;
  linkUrl?: string;
  occurredAt: string;
  cancelled?: boolean;
  operatorName?: string;
}

export interface TimelineItem {
  kind: 'signal' | 'log';
  time: string;
  signal?: Signal;
  logId?: number;
  system?: boolean;
  operatorName?: string;
  content?: string;
  nextAction?: string;
  nextActionDate?: string;
  statusChange?: string;
}

export interface Channel {
  supplierId: number;
  supplierName?: string;
  role: number;
  evidenceType?: number;
  evidenceDate?: string;
  unitPrice?: number;
  currencyCode?: string;
  taxIncluded?: boolean;
  itemCondition?: number;
  leadTime?: number;
}

export interface DocLog {
  id: number;
  action: string;
  content?: string;
  operatorName?: string;
  createTime: string;
}

export interface Project {
  id: number;
  name: string;
  brandId: number;
  brandName?: string;
  categoryId?: number;
  categoryName?: string;
  goal: string;
  ownerId: number;
  ownerName?: string;
  dueDate: string;
  supplierTarget?: number;
  devQualified: number;
  status: number;
  resultNote?: string;
  closedAt?: string;
  solved: number;
  total: number;
  overdueDays: number;
  canClose: boolean;
  items?: Demand[];
  records?: TimelineItem[];
  logs?: DocLog[];
  dev?: ProjectDev;
  createTime: string;
  createBy?: string;
}

export interface Evidence {
  type: number;
  date: string;
  docNo?: string;
  docId?: number;
  unitPrice?: number;
  currencyCode?: string;
  taxIncluded?: boolean;
  supplierName?: string;
}

export interface DemandDetail {
  demand: Demand;
  evidence?: Evidence;
  channels?: Channel[];
  timeline: TimelineItem[];
  logs: DocLog[];
  projects: Project[];
  nodes: string[];
  canEdit: boolean;
  canCoverage: boolean;
  canSeeSupplier: boolean;
}

export interface Rank {
  brandId?: number;
  categoryId?: number;
  label: string;
  models: number;
  times: number;
  open: number;
  projectId?: number;
}

export interface Team {
  days: number;
  stockoutModels: number;
  stockoutTimes: number;
  prevStockoutModels: number;
  prevStockoutTimes: number;
  stockoutRate?: number | null;
  coverRate?: number | null;
  quotableRate?: number | null;
  backupRate?: number | null;
  filledGaps: number;
  byBrand: Rank[];
  byCategory: Rank[];
  cells: Rank[];
  reasons: Rank[];
  projects: Project[];
  owners: {
    ownerId?: number;
    ownerName?: string;
    models: number;
    noChannel: number;
    lead: number;
    confirm: number;
    dueToday: number;
    overdue: number;
    multiDefer: number;
    records: number;
  }[];
  attention: {
    kind: 'overdue' | 'defer' | 'no-owner' | 'project';
    demandId?: number;
    projectId?: number;
    title: string;
    detail?: string;
    ownerName?: string;
  }[];
}

export interface Matrix {
  mode: 'cover' | 'stockout' | 'supplier';
  days: number;
  brands: { id: number; name: string; total: number }[];
  categories: { id: number; name: string; total: number }[];
  cells: {
    brandId: number;
    categoryId: number;
    total: number;
    coverage: Record<number, number>;
    worst: number;
    stockoutModels: number;
    stockoutTimes: number;
    projectId?: number;
    projectName?: string;
    /** 按供应商 */
    qualified: number;
    required: number;
    gap: number;
    heat: number;
    leads: number;
    devProjectId?: number;
  }[];
}

export interface GroupQuery {
  board: 'intel' | 'cover';
  stage?: string;
  stockout?: boolean;
  stockoutDays?: number;
  pending?: boolean;
  single?: boolean;
  keyword?: string;
  brandId?: number;
  categoryId?: number;
  priority?: number;
  ownerId?: number;
  page?: number;
  size?: number;
}

export interface RecordReq {
  demandIds: number[];
  content: string;
  coverage?: number;
  nextAction?: string;
  nextActionDate?: string;
  gapProjectId?: number;
  idempotencyKey: string;
}

export interface SignalReq {
  type: number;
  platform?: string;
  content: string;
  count?: number;
  periodStart?: string;
  periodEnd?: string;
  link?: string;
  brandId?: number;
  categoryId?: number;
  model?: string;
}

export interface ProjectReq {
  brandId: number;
  categoryId?: number;
  name: string;
  goal: string;
  ownerId: number;
  dueDate: string;
  demandIds: number[];
  supplierTarget?: number;
}

// ---------------------------------------------------------------- 接口

interface Resp<T> {
  data: T;
}
interface Page<T> {
  total: number;
  records: T[];
}

const BASE = '/api/v1/supply';
const get = <T>(url: string, params?: object) =>
  request<Resp<T>>(`${BASE}${url}`, { params }).then((r) => r.data);
const send = <T>(method: 'POST' | 'PUT', url: string, data?: object) =>
  request<Resp<T>>(`${BASE}${url}`, { method, data }).then((r) => r.data);

export const supplyApi = {
  overview: (ownerId?: number) => get<Overview>('/overview', { ownerId }),
  groups: (q: GroupQuery) => get<GroupPage>('/groups', q),
  today: () => get<Today>('/today'),
  cards: () => get<Record<string, BattleCardData>>('/cards'),
  defer: (demandIds: number[], date: string) =>
    send<number>('POST', '/demands/defer', { demandIds, date }),
  record: (data: RecordReq) =>
    send<{
      logId?: number;
      changed: number;
      skipped: number[];
      duplicate: boolean;
    }>('POST', '/records', data),
  team: (period?: number) => get<Team>('/team', { period }),
  unclassified: (page: number, size: number, type?: number) =>
    get<Page<Signal>>('/signals/unclassified', { page, size, type }),
  addSignal: (data: SignalReq) => send<number>('POST', '/signals', data),
  classify: (data: {
    signalIds: number[];
    demandId?: number;
    brandId?: number;
    categoryId?: number;
    model?: string;
  }) => send<number>('POST', '/signals/classify', data),
  ignore: (signalIds: number[], reason?: string) =>
    send<number>('POST', '/signals/ignore', { signalIds, reason }),
  merge: (keepId: number, removeId: number) =>
    send<void>('POST', '/demands/merge', { keepId, removeId }),
  detail: (id: number) => get<DemandDetail>(`/demands/${id}`),
  priority: (id: number, priority: number, reason: string) =>
    send<void>('PUT', `/demands/${id}/priority`, { priority, reason }),
  join: (id: number) => send<void>('POST', `/demands/${id}/join`),
  pause: (id: number, reason: string) =>
    send<void>('POST', `/demands/${id}/pause`, { reason }),
  resume: (id: number) => send<void>('POST', `/demands/${id}/resume`),
  next: (
    id: number,
    data: { ownerId?: number; nextAction?: string; nextActionDate?: string },
  ) => send<void>('PUT', `/demands/${id}/next`, data),
  coverage: (id: number, status: number, note?: string) =>
    send<void>('PUT', `/demands/${id}/coverage`, { status, note }),
  channels: (
    id: number,
    primarySupplierId?: number,
    backupSupplierIds: number[] = [],
  ) =>
    send<void>('PUT', `/demands/${id}/channels`, {
      primarySupplierId,
      backupSupplierIds,
    }),
  candidates: (brandId: number, categoryId?: number) =>
    get<{ items: Demand[]; defaultName: string; activeProjectId: number }>(
      '/projects/candidates',
      { brandId, categoryId },
    ),
  createProject: (data: ProjectReq) => send<number>('POST', '/projects', data),
  project: (id: number) => get<Project>(`/projects/${id}`),
  projectItems: (id: number, add: number[], remove: number[]) =>
    send<void>('PUT', `/projects/${id}/items`, { add, remove }),
  closeProject: (id: number, done: boolean, note?: string) =>
    send<void>('POST', `/projects/${id}/close`, { done, note }),
  matrix: (q: {
    mode?: string;
    period?: number;
    parentCategoryId?: number;
    brandId?: number;
    priority?: number;
    ownerId?: number;
    gapsOnly?: boolean;
  }) => get<Matrix>('/matrix', q),
  devGroups: (q: DevGroupQuery) => get<DevGroupPage>('/dev/groups', q),
  devScope: (brandId: number, categoryId?: number) =>
    get<DevScope>('/dev/scope', { brandId, categoryId }),
  addExpansion: (data: {
    brandId: number;
    categoryId: number;
    heat: number;
    reason: string;
    ownerId?: number;
  }) => send<number>('POST', '/dev/expansions', data),
  updateExpansion: (
    id: number,
    data: { heat: number; reason: string; ownerId?: number },
  ) => send<void>('PUT', `/dev/expansions/${id}`, data),
  removeExpansion: (id: number, reason: string) =>
    send<void>('POST', `/dev/expansions/${id}/remove`, { reason }),
  addCandidate: (projectId: number, data: CandidateReq) =>
    send<number>('POST', `/projects/${projectId}/candidates`, data),
  linkCandidate: (id: number, supplierId: number) =>
    send<void>('POST', `/candidates/${id}/link`, { supplierId }),
  candidateRecord: (data: CandidateRecordReq) =>
    send<{
      logId?: number;
      changed: number;
      skipped: number[];
      duplicate: boolean;
    }>('POST', '/candidates/records', data),
  projectTarget: (id: number, target?: number) =>
    send<void>('PUT', `/projects/${id}/target`, { target }),
  models: (q: {
    brandId?: number;
    categoryId?: number;
    coverage?: number;
    priority?: number;
    keyword?: string;
    /** 含观察中的（记处理选型号用） */
    all?: boolean;
    page?: number;
    size?: number;
  }) => get<Page<Demand>>('/matrix/models', q),
};

export const newKey = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;

/** 「品牌 · 品类」，没有品类时只显示品牌 */
export const scopeName = (x: { brandName?: string; categoryName?: string }) =>
  [x.brandName ?? '未知品牌', x.categoryName].filter(Boolean).join(' · ');

/** 型号行的显示名：型号对象显示型号，品类对象显示「全部型号」 */
export const modelName = (d: Pick<Demand, 'model' | 'categoryName'>) =>
  d.model || `${d.categoryName ?? ''} 全部型号`;
