import { request } from '@umijs/max';

const API = '/api/v1/websites';
const quiet = { skipErrorHandler: true } as const;

/** 菜单与按钮权限，与后端 WebsiteConstants 一致 */
export const WEBSITE_PATH = '/operations/websites';
export const PERM_EDIT = 'operations:website:edit';

/** 1-自动发布、2-只复制 */
export const MODE_AUTO = 1;
export const MODE_COPY = 2;
/** 1-接管中、2-以 ERP 为准 */
export const TAKEOVER_PENDING = 1;
export const TAKEOVER_DONE = 2;

export const LANGUAGES: { code: string; label: string; prefix: string }[] = [
  { code: 'en', label: '英文', prefix: '' },
  { code: 'ru', label: '俄文', prefix: '/ru' },
];
export const languageLabel = (code: string) =>
  LANGUAGES.find((l) => l.code === code)?.label ?? code;

/** 上线链接示例用的链接别名 */
export const SAMPLE_SLUG = 'siemens-6es7214-1ag40-0xb0';

export interface SiteLanguage {
  code: string;
  prefix: string;
}

export interface Secret {
  configured: boolean;
  last4: string;
}

export interface ReleaseBrief {
  id: number;
  siteId: number;
  releaseNo: string;
  /** 1-排队中、2-执行中、3-已完成、4-失败、5-部署失败 */
  status: number;
  statusName: string;
  itemCount: number;
  createBy?: string;
  createTime?: string;
  startedAt?: string;
  endedAt?: string;
  failedStep?: string;
  error?: string;
}

export interface SiteSummary {
  /** 范围内商品数（发品任务未取消） */
  scopeCount: number;
  /** 已发布或已上线待确认 */
  publishedCount: number;
  /** 导入的站点商品数 */
  importedCount: number;
  lastRelease?: ReleaseBrief | null;
  /** 最近一次比对未确认的差异；比对没完成或没有比对时为空 */
  pendingDiffs?: number | null;
}

export interface Site {
  id: number;
  name: string;
  url: string;
  languages: SiteLanguage[];
  pathTemplate: string;
  publishMode: number;
  publishModeName: string;
  repoUrl: string;
  repoBranch: string;
  deployHost: string;
  deployPort?: number | null;
  deployUser: string;
  deployPath: string;
  productScope: { brandIds: number[]; categoryIds: number[] };
  nonGenuineBrandIds: number[];
  priceFactor?: number;
  currencyCode: string;
  needPurge: boolean;
  purgeNote: string;
  takeoverStatus: number;
  takeoverStatusName: string;
  takeoverBy?: string;
  takeoverAt?: string;
  /** 1-启用、0-停用 */
  status: number;
  repoToken: Secret;
  deployKey: Secret;
  indexNowKey: Secret;
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
  summary?: SiteSummary;
}

/** 新建、修改；三个凭据留空表示不改 */
export interface SiteForm {
  name: string;
  url: string;
  languages: SiteLanguage[];
  pathTemplate: string;
  publishMode: number;
  repoUrl: string;
  repoBranch: string;
  deployHost: string;
  deployPort?: number | null;
  deployUser: string;
  deployPath: string;
  productScope: { brandIds: number[]; categoryIds: number[] };
  nonGenuineBrandIds: number[];
  priceFactor?: number | null;
  needPurge: boolean;
  purgeNote: string;
  repoToken: string;
  deployKey: string;
  indexNowKey: string;
  clearIndexNowKey: boolean;
}

export interface CheckItem {
  key: string;
  name: string;
  ok: boolean;
  message: string;
}

export interface CategoryMapRow {
  categoryId: number;
  name: string;
  siteCategory: string;
  productCount: number;
  children: CategoryMapRow[];
}

/** 一致性比对的一条差异：型号 + 字段 */
export interface DiffKey {
  model: string;
  field: string;
}

export interface Diff extends DiffKey {
  site: unknown;
  erp: unknown;
}

export interface AcceptRecord extends DiffKey {
  reason: string;
  by: string;
  at: string;
}

/** 导入：已匹配的一个商品 */
export interface ImportMatched {
  productId: number;
  brand: string;
  model: string;
  name?: string;
  slug: string;
  sellPrice?: number | null;
  photos: number;
  videos: number;
  diffFields: string[];
}

/** 站点上的原始商品条目（未匹配的原样保存） */
export type SiteEntry = Record<string, unknown> & {
  brand?: string;
  model?: string;
  slug?: string;
};

export interface ImportResult {
  commit?: string;
  warnings?: string[] | null;
  total: number;
  matched: ImportMatched[];
  unmatched: SiteEntry[];
  siteOnlyFields: string[];
  missing: { productId: number; brand: string; model: string }[];
}

export interface CompareResult {
  commit?: string;
  same: number;
  differences: Diff[];
  onlyOnSite: string[];
  onlyInErp: string[];
  /** 型号 → 品牌 */
  brands?: Record<string, string>;
}

export interface SiteJob {
  id: number;
  siteId: number;
  /** import-导入现有商品、compare-一致性比对 */
  jobType: 'import' | 'compare';
  jobNo: string;
  /** 1-执行中、2-已完成、3-失败、4-已确认 */
  status: number;
  statusName: string;
  error?: string;
  startedAt?: string;
  endedAt?: string;
  createBy?: string;
  createTime?: string;
  /** 导入为 ImportResult，比对为 CompareResult；列表不带 */
  result?: any;
  accepted?: AcceptRecord[] | null;
  pending?: number;
}

const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(method: string, url: string, data?: object) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);

export const siteApi = {
  list: () => get<Site[]>(API),
  detail: (id: number) => get<Site>(`${API}/${id}`),
  create: (data: SiteForm) => send<Site>('POST', API, data),
  update: (id: number, data: SiteForm) =>
    send<Site>('PUT', `${API}/${id}`, data),
  setStatus: (id: number, status: number) =>
    send<Site>('PUT', `${API}/${id}/status`, { status }),
  check: (id: number) => send<CheckItem[]>('POST', `${API}/${id}/check`),
  categoryMap: (id: number) =>
    get<CategoryMapRow[]>(`${API}/${id}/category-map`),
  saveCategoryMap: (
    id: number,
    items: { categoryId: number; siteCategory: string }[],
  ) => send<CategoryMapRow[]>('PUT', `${API}/${id}/category-map`, { items }),
  releases: (id: number) => get<ReleaseBrief[]>(`${API}/${id}/releases`),
  jobs: (id: number) => get<SiteJob[]>(`${API}/${id}/jobs`),
  job: (jobId: number) => get<SiteJob>(`${API}/jobs/${jobId}`),
  startImport: (id: number) => send<SiteJob>('POST', `${API}/${id}/import`),
  startCompare: (id: number) => send<SiteJob>('POST', `${API}/${id}/compare`),
  takeover: (id: number) => send<Site>('POST', `${API}/${id}/takeover`),
  confirmImport: (jobId: number) =>
    send<SiteJob>('POST', `${API}/jobs/${jobId}/confirm-import`),
  /** items 为空表示全部差异 */
  accept: (jobId: number, items: DiffKey[], reason: string) =>
    send<SiteJob>('POST', `${API}/jobs/${jobId}/accept`, { items, reason }),
};

export const jobPath = (jobId: number) => `${WEBSITE_PATH}/jobs/${jobId}`;

/** 站点数据字段的中文名；没有列出的显示字段代码 */
const FIELD_LABELS: Record<string, string> = {
  brand: '品牌',
  model: '型号',
  series: '系列',
  cat: '站点品类',
  slug: '链接别名',
  spec: '一句话规格',
  overview: '英文描述',
  meta_description: '英文摘要',
  overview_ru: '俄文描述',
  meta_description_ru: '俄文摘要',
  spec_ru: '俄文规格',
  specs: '规格参数',
  applications: '应用场景',
  faq: 'FAQ',
  compatibility: '型号关系',
  deep_ru: '俄文详细内容',
  photo: '主图',
  photos: '图片',
  videos: '视频',
  sell_price: '售价',
  sell_price_currency: '售价币种',
  price_source: '售价来源',
  status: '站点状态',
};
export const fieldLabel = (f: string) => FIELD_LABELS[f] ?? f;

/** 上线链接：网址 + 语言前缀 + 地址模板 */
export const liveUrl = (url: string, prefix: string, template: string) =>
  `${url.replace(/\/+$/, '')}${prefix}${template.replace('{slug}', SAMPLE_SLUG)}`;

/** 没有对照的品类：一级没设置、二级也没设置的才算（二级可沿用一级） */
export const unmappedRows = (rows: CategoryMapRow[]) => {
  const out: { name: string; count: number }[] = [];
  for (const r of rows) {
    if (r.siteCategory) continue;
    const own =
      r.productCount - r.children.reduce((s, c) => s + c.productCount, 0);
    if (own > 0) out.push({ name: r.name, count: own });
    for (const c of r.children) {
      if (!c.siteCategory && c.productCount > 0)
        out.push({ name: c.name, count: c.productCount });
    }
  }
  return out;
};

export { readBizError } from '@/pages/inquiry/shared/service';
