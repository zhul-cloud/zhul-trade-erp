import { request } from '@umijs/max';

/** 客户画像（spec master-data/customer-profile）：字典类型与后端 ProfileField 一致 */
export const PROFILE_DICTS = {
  companySize: 'customer_company_size',
  qualityReq: 'customer_quality_req',
  priceSensitivity: 'customer_price_sensitivity',
  customsAbility: 'customer_customs_ability',
  designAbility: 'customer_design_ability',
  protocols: 'customer_protocol',
  voltages: 'customer_voltage',
  certifications: 'customer_certification',
  repairAbility: 'customer_repair_ability',
  jobRole: 'contact_job_role',
  decisionRole: 'contact_decision_role',
  commStyle: 'contact_comm_style',
  disc: 'contact_disc',
  chinaAttitude: 'contact_china_attitude',
} as const;

export const MODULE_NAMES = [
  '基础画像与定性',
  '商业与供应链能力',
  '工控技术偏好',
  '关键决策人',
  '国家与宏观背景',
];
export const MODULE_SHORT = [
  '基础画像',
  '商业能力',
  '技术偏好',
  '决策人',
  '宏观背景',
];
export const GRADE_NAMES = ['未成交', 'S', 'A', 'B', 'C'];
export const FX_OPTIONS = [
  { value: 1, label: '无' },
  { value: 2, label: '一般' },
  { value: 3, label: '严格' },
];

/** 来源：1-人工、2-背调报告、3-迁移、9-系统 */
export const SOURCE = { MANUAL: 1, REPORT: 2, MIGRATED: 3, SYSTEM: 9 };

export interface BrandRef {
  brandId?: number | null;
  name: string;
}

export interface FieldSource {
  source: number;
  reportId?: number;
  /** 背调报告编号；reportDeleted 为报告已删除 */
  reportNo?: string;
  reportDeleted?: boolean;
  operatorName?: string;
  updateTime?: string;
  askedAt?: string;
  askedByName?: string;
}

export interface Contact {
  id: number;
  primary: boolean;
  name: string;
  /** 中文名（系统内显示）；空时保存后由 AI 翻译回填 */
  nameCn: string;
  title: string;
  email: string;
  phone: string;
  whatsapp: string;
  otherIm: string;
  jobRole: number;
  decisionRole: number;
  commStyle: number;
  disc: number;
  chinaAttitude: number;
  activeHours: string;
  likes: string;
  updateTime?: string;
}

export interface CountryProfile {
  id?: number;
  country: string;
  customerCount?: number;
  voltages: number[];
  plug: string;
  holidays: string;
  fxControl: number;
  fxNote: string;
  tariffNote: string;
  certifications: number[];
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface Grade {
  calc: number;
  finalGrade: number;
  score?: number | null;
  r?: number;
  f?: number;
  m?: number;
  adjust: number;
  adjustReason?: string;
  adjustByName?: string;
  adjustAt?: string;
  calcAt?: string;
  followDays: number;
}

export interface Profile {
  customerId: number;
  name: string;
  country: string;
  city: string;
  timezone: string;
  customerRole: number;
  industry: number;
  website: string;
  linkedin: string;
  socialUrl: string;
  foundedYear?: number | null;
  companySize: number;
  potentialRating: number;
  mainProducts: string;
  cnSuppliers: string;
  estAnnualPurchase?: number | null;
  estPurchaseCurrency: string;
  qualityReq: number;
  priceSensitivity: number;
  customsAbility: number;
  paymentMethod: number;
  depositRatio?: number | null;
  paymentDays?: number | null;
  currentBrands: BrandRef[];
  designAbility: number;
  protocols: number[];
  voltages: number[];
  certifications: number[];
  repairAbility: number;
  derived: {
    inquiryCount: number;
    firstInquiryDate?: string;
    lastInquiryDate?: string;
    ordersInYear: number;
    salesCnyInYear: number;
    topBrands: { name: string; count: number }[];
  };
  countryProfile?: CountryProfile | null;
  completeness: {
    mask: number;
    done: number;
    complete: boolean;
    missing: {
      module: number;
      moduleName: string;
      keys: string[];
      labels: string[];
    }[];
  };
  grade: Grade;
  sources: Record<string, FieldSource>;
  contacts: Contact[];
}

export interface RfmDetail {
  firstOrderDate?: string;
  lastOrderDate?: string;
  daysSinceLast?: number;
  ordersInYear: number;
  usdInYear: number;
  usdTotal: number;
  usdRate?: number;
  r?: number;
  f?: number;
  m?: number;
  score?: number;
  calc: number;
  adjust: number;
  finalGrade: number;
  adjustReason?: string;
  adjustByName?: string;
  adjustAt?: string;
  followDays: number;
  calcAt?: string;
  recencyDays: number[];
  frequency: number[];
  monetaryUsd: number[];
  weights: number[];
  cutoffs: number[];
  orders: {
    id: number;
    soNo: string;
    salesDate: string;
    currencyCode: string;
    amount: number;
    amountCny: number;
    usd: number;
  }[];
}

export type ProfilePatch = Partial<
  Omit<Profile, 'derived' | 'completeness' | 'grade' | 'sources' | 'contacts'>
> & { fields: string[] };

export type ContactPayload = Omit<Contact, 'id' | 'primary' | 'updateTime'> & {
  primary?: boolean;
};

export interface DupGroup {
  groupKey: string;
  reason: 'NAME' | 'EMAIL' | 'PHONE';
  reasonText: string;
  members: {
    id: number;
    customerCode: string;
    name: string;
    country: string;
    ownerId: number;
    ownerName?: string;
    profileDone: number;
    inquiryCount: number;
    orderCount: number;
  }[];
}

export interface MergePreview {
  documents: Record<string, number>;
  contacts: string[];
  filledFields: string[];
  blocked?: string;
}

const quiet = { skipErrorHandler: true } as const;
const BASE = '/api/v1/masterdata/customers';
const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(
  method: 'POST' | 'PUT' | 'DELETE',
  url: string,
  data?: unknown,
) => request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);

export const profileApi = {
  get: (id: number) => get<Profile>(`${BASE}/${id}/profile`),
  update: (id: number, patch: ProfilePatch) =>
    send<void>('PUT', `${BASE}/${id}/profile`, patch),
  asked: (id: number, keys: string[], asked: boolean) =>
    send<void>('POST', `${BASE}/${id}/profile/asked`, { keys, asked }),
  rfm: (id: number) => get<RfmDetail>(`${BASE}/${id}/rfm`),
  adjustGrade: (id: number, adjust: number, reason?: string) =>
    send<void>('PUT', `${BASE}/${id}/grade`, { adjust, reason }),
  createContact: (id: number, body: ContactPayload) =>
    send<Contact>('POST', `${BASE}/${id}/contacts`, body),
  updateContact: (id: number, contactId: number, body: ContactPayload) =>
    send<Contact>('PUT', `${BASE}/${id}/contacts/${contactId}`, body),
  setPrimary: (id: number, contactId: number) =>
    send<void>('PUT', `${BASE}/${id}/contacts/${contactId}/primary`),
  removeContact: (id: number, contactId: number) =>
    send<void>('DELETE', `${BASE}/${id}/contacts/${contactId}`),
  duplicates: () => get<DupGroup[]>(`${BASE}/duplicates`),
  dismiss: (groupKey: string) =>
    send<void>('POST', `${BASE}/duplicates/dismiss`, { groupKey }),
  mergePreview: (targetId: number, sourceId: number) =>
    get<MergePreview>(`${BASE}/merge-preview`, { targetId, sourceId }),
  merge: (targetId: number, sourceId: number) =>
    send<void>('POST', `${BASE}/merge`, { targetId, sourceId }),
};

const COUNTRY = '/api/v1/masterdata/country-profiles';
export const countryProfileApi = {
  list: (keyword?: string, filter?: string) =>
    get<CountryProfile[]>(COUNTRY, { keyword, filter }),
  save: (body: Omit<CountryProfile, 'id' | 'customerCount'>) =>
    send<CountryProfile>('PUT', COUNTRY, body),
};

/** 字段是否有值：0、空串、空数组都算没填 */
export const filled = (v: unknown) =>
  !(
    v === undefined ||
    v === null ||
    v === 0 ||
    v === '' ||
    (Array.isArray(v) && v.length === 0)
  );
