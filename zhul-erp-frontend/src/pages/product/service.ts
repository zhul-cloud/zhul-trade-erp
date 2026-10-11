import type { RequestOptions } from '@@/plugin-request/request';
import { request } from '@umijs/max';

const BASE = '/api/v1/product';

// ---------- 通用 ----------

export interface PageResult<T> {
  total: number;
  records: T[];
}

export interface PageParams {
  page?: number;
  pageSize?: number;
}

interface Envelope<T> {
  code: number;
  message: string;
  data: T;
}

async function call<T>(url: string, options: RequestOptions = {}): Promise<T> {
  const res = await request<Envelope<T>>(url, options);
  return res.data;
}

/** 业务错误里的字符串错误码与补充信息（后端放在响应 data 里）。 */
export interface BizErrorInfo {
  errorCode?: string;
  detail?: Record<string, unknown>;
  message: string;
}

export function readBizError(error: unknown): BizErrorInfo {
  const e = error as {
    message?: string;
    info?: { data?: { errorCode?: string; detail?: Record<string, unknown> } };
  };
  return {
    errorCode: e?.info?.data?.errorCode,
    detail: e?.info?.data?.detail,
    message: e?.message ?? '操作失败，请稍后重试',
  };
}

/** 页面自己处理错误时使用：不弹全局错误提示 */
const quiet = { skipErrorHandler: true } as const;

// ---------- 品牌 / 品类 / 系列 ----------

export interface Brand {
  id: number;
  brandName: string;
  country: string;
  logoUrl: string;
  brandColor: string;
  /** 英文简介（独立站等对外场景） */
  description: string;
  /** 中文简介（系统内显示） */
  descriptionZh: string;
  /** 0-普通、1-常做、2-核心 */
  brandLevel: number;
  isGenuine: number;
  status: number;
  productCount: number;
  /** 别名：询盘原文、供应商手填的其他写法 */
  aliases: string[];
  createBy: string;
  createTime: string;
  updateBy: string;
  updateTime: string;
}

export interface BrandOption {
  id: number;
  brandName: string;
  logoUrl: string;
  description: string;
  descriptionZh?: string;
  /** 0-普通、1-常做、2-核心；后端已按等级、名称排序 */
  brandLevel?: number;
  isGenuine: number;
  aliases?: string[];
}

export interface SaveBrand {
  brandName: string;
  country?: string;
  logoUrl?: string;
  brandColor?: string;
  description?: string;
  descriptionZh?: string;
  brandLevel?: number;
  isGenuine?: number;
  /** 为空不修改，空数组清空 */
  aliases?: string[];
}

export interface Category {
  id: number;
  categoryCode: string;
  categoryName: string;
  /** 中文名：细分品类必填 */
  categoryNameZh: string;
  /** 上级品类，一级品类为空 */
  parentId?: number | null;
  /** 细分品类（树形接口返回） */
  children?: Category[];
  /** 英文简介（独立站等对外场景） */
  description: string;
  /** 中文简介（系统内显示） */
  descriptionZh: string;
  sortOrder: number;
  status: number;
  productCount: number;
  createBy: string;
  createTime: string;
  updateBy: string;
  updateTime: string;
}

export interface CategoryOption {
  id: number;
  categoryCode: string;
  categoryName: string;
  categoryNameZh?: string;
  parentId?: number | null;
  description: string;
  descriptionZh?: string;
}

export interface SaveCategory {
  categoryCode: string;
  categoryName: string;
  categoryNameZh?: string;
  /** 为空建一级品类，否则建该一级品类下的细分品类 */
  parentId?: number | null;
  description?: string;
  descriptionZh?: string;
  sortOrder?: number;
}

export interface Series {
  id: number;
  brandId: number;
  brandName: string;
  seriesName: string;
  description: string;
  status: number;
  productCount: number;
  createBy: string;
  createTime: string;
  updateBy: string;
  updateTime: string;
}

export interface SeriesOption {
  id: number;
  brandId: number;
  seriesName: string;
}

/** 国家/地区清单项：品牌原产地下拉的数据源，品牌保存的是 nameEn */
export interface Country {
  code: string;
  nameEn: string;
  nameZh: string;
}

export const countryApi = {
  list: () => call<Country[]>(`${BASE}/countries`),
};

export const brandApi = {
  page: (
    params: PageParams & {
      keyword?: string;
      status?: number;
      brandLevel?: number;
      /** 原产地（国家清单英文名） */
      country?: string;
    },
  ) => call<PageResult<Brand>>(`${BASE}/brands`, { params }),
  /** 各等级品牌数：all / 2 / 1 / 0 */
  levelCounts: (params: { status?: number }) =>
    call<Record<string, number>>(`${BASE}/brands/level-counts`, { params }),
  options: () => call<BrandOption[]>(`${BASE}/brands/options`),
  create: (data: SaveBrand) =>
    call<Brand>(`${BASE}/brands`, { method: 'POST', data }),
  update: (id: number, data: SaveBrand) =>
    call<Brand>(`${BASE}/brands/${id}`, { method: 'PUT', data }),
  setStatus: (id: number, status: number) =>
    call<void>(`${BASE}/brands/${id}/status`, {
      method: 'PATCH',
      data: { status },
    }),
  remove: (id: number) =>
    call<void>(`${BASE}/brands/${id}`, { method: 'DELETE' }),
};

export const categoryApi = {
  page: (params: PageParams & { keyword?: string; status?: number }) =>
    call<PageResult<Category>>(`${BASE}/categories`, { params }),
  /** level：1 或不传只返回一级品类，2 只返回细分品类，0 返回全部 */
  options: (level?: number) =>
    call<CategoryOption[]>(`${BASE}/categories/options`, { params: { level } }),
  /** 两级品类树（含停用的） */
  tree: () => call<Category[]>(`${BASE}/categories/tree`),
  create: (data: SaveCategory) =>
    call<Category>(`${BASE}/categories`, { method: 'POST', data }),
  update: (id: number, data: SaveCategory) =>
    call<Category>(`${BASE}/categories/${id}`, { method: 'PUT', data }),
  setStatus: (id: number, status: number) =>
    call<void>(`${BASE}/categories/${id}/status`, {
      method: 'PATCH',
      data: { status },
    }),
  remove: (id: number) =>
    call<void>(`${BASE}/categories/${id}`, { method: 'DELETE' }),
};

/** 待确认品牌：供应商主营产品里手填、品牌清单中没有的名称（仅平台账号） */
export interface PendingBrand {
  pendingKey: string;
  name: string;
  supplierCount: number;
  firstSeen: string;
}

export const pendingBrandApi = {
  list: () => call<PendingBrand[]>('/api/v1/masterdata/pending-brands'),
  linkAsAlias: (pendingKey: string, brandId: number) =>
    call<void>('/api/v1/masterdata/pending-brands/alias', {
      method: 'POST',
      data: { pendingKey, brandId },
    }),
  createBrand: (pendingKey: string, brand: SaveBrand) =>
    call<number>('/api/v1/masterdata/pending-brands/brand', {
      method: 'POST',
      data: { pendingKey, brand },
    }),
};

export const seriesApi = {
  page: (
    params: PageParams & {
      keyword?: string;
      status?: number;
      brandId?: number;
    },
  ) => call<PageResult<Series>>(`${BASE}/series`, { params }),
  options: (brandId?: number) =>
    call<SeriesOption[]>(`${BASE}/series/options`, { params: { brandId } }),
  create: (data: {
    brandId: number;
    seriesName: string;
    description?: string;
  }) => call<Series>(`${BASE}/series`, { method: 'POST', data }),
  update: (id: number, data: { seriesName: string; description?: string }) =>
    call<Series>(`${BASE}/series/${id}`, { method: 'PUT', data }),
  setStatus: (id: number, status: number) =>
    call<void>(`${BASE}/series/${id}/status`, {
      method: 'PATCH',
      data: { status },
    }),
  remove: (id: number) =>
    call<void>(`${BASE}/series/${id}`, { method: 'DELETE' }),
};

// ---------- 商品 ----------

export interface CompletenessModule {
  key: string;
  done: boolean;
}

export interface Completeness {
  done: number;
  total: number;
  modules: CompletenessModule[];
  missing: string[];
}

export interface Product {
  id: number;
  brandId: number;
  brandName?: string;
  brandIsGenuine?: number;
  categoryId: number;
  categoryCode?: string;
  categoryName?: string;
  /** 品类中文名 */
  categoryNameZh?: string;
  seriesId?: number;
  seriesName?: string;
  mpnRaw: string;
  mpnNormalized: string;
  mpnDisplay: string;
  productName: string;
  shortDescription: string;
  specSummary: string;
  lifecycleStatus: number;
  lifecycleSource: string;
  status: number;
  deleted?: boolean;
  usageCount?: number;
  warnings?: string[];
  completeness?: Completeness;
  /** 需求热度：关联的询盘型号数、有效订单数（租户只统计本公司） */
  inquiryCount?: number;
  dealCount?: number;
  createTime: string;
  createBy?: string;
  updateTime: string;
  updateBy?: string;
}

export interface SaveProduct {
  brandId?: number;
  categoryId?: number;
  seriesId?: number | null;
  mpnRaw: string;
  mpnDisplay?: string;
  productName?: string;
  shortDescription?: string;
  specSummary?: string;
  lifecycleStatus?: number;
  lifecycleSource?: string;
}

export interface ProductQuery extends PageParams {
  keyword?: string;
  brandId?: number;
  categoryId?: number;
  seriesId?: number;
  lifecycleStatus?: number;
  status?: number;
  includeDeleted?: boolean;
  missing?: string;
  /** 按热度倒序：inquiryCount / dealCount */
  sort?: string;
}

export interface ProductOption {
  id: number;
  mpnDisplay: string;
  brandName: string;
  categoryName: string;
  productName: string;
}

export interface ProductMatch {
  exact?: ProductOption;
  candidates: ProductOption[];
}

export interface CompletenessSummary {
  total: number;
  missingMedia: number;
  missingLogistics: number;
  missingCustoms: number;
  missingPrice: number;
}

export const productApi = {
  page: (params: ProductQuery) =>
    call<PageResult<Product>>(`${BASE}/products`, { params }),
  get: (id: number) => call<Product>(`${BASE}/products/${id}`),
  summary: () =>
    call<CompletenessSummary>(`${BASE}/products/completeness-summary`),
  search: (keyword: string, brandId?: number, limit?: number) =>
    call<ProductOption[]>(`${BASE}/products/search`, {
      params: { keyword, brandId, limit },
    }),
  match: (brand: string, mpn: string) =>
    call<ProductMatch>(`${BASE}/products/match`, { params: { brand, mpn } }),
  /** 向导第 2 步创建：重复时页面自己处理，不弹全局提示 */
  createQuiet: (data: SaveProduct) =>
    call<Product>(`${BASE}/products`, { method: 'POST', data, ...quiet }),
  update: (id: number, data: SaveProduct) =>
    call<Product>(`${BASE}/products/${id}`, { method: 'PUT', data }),
  setStatus: (id: number, status: number) =>
    call<void>(`${BASE}/products/${id}/status`, {
      method: 'PATCH',
      data: { status },
    }),
  remove: (id: number) =>
    call<void>(`${BASE}/products/${id}`, { method: 'DELETE' }),
  restore: (id: number) =>
    call<Product>(`${BASE}/products/${id}/restore`, { method: 'POST' }),
};

// ---------- 规格 / 型号关系 ----------

export interface SpecItem {
  id?: number;
  specKey: string;
  specLabel: string;
  specValue: string;
  specUnit?: string;
  source?: string;
  verified?: number;
  sortOrder?: number;
}

export interface Relationship {
  id: number;
  productId: number;
  relatedMpn: string;
  relatedProductId?: number;
  relatedProductDisplay?: string;
  relatedBrandName?: string;
  relationshipType: number;
  confidence: number;
  note: string;
  verifiedBy: string;
  verifiedAt?: string;
}

export interface SaveRelationship {
  relatedMpn?: string;
  relatedProductId?: number;
  relationshipType: number;
  confidence?: number;
  note?: string;
  verifiedBy?: string;
  createReverse?: boolean;
}

/** 营销内容语言：英文、俄文（商品资料只存中文） */
export type ContentLang = 'en' | 'ru';

/** 规格、应用场景、兼容说明都是商品资料，只维护中文 */
export const specApi = {
  list: (id: number) =>
    call<SpecItem[]>(`${BASE}/products/${id}/specifications`),
  replace: (id: number, items: SpecItem[]) =>
    call<SpecItem[]>(`${BASE}/products/${id}/specifications`, {
      method: 'PUT',
      data: { items },
    }),
};

export const relationshipApi = {
  list: (id: number) =>
    call<Relationship[]>(`${BASE}/products/${id}/relationships`),
  create: (id: number, data: SaveRelationship) =>
    call<Relationship>(`${BASE}/products/${id}/relationships`, {
      method: 'POST',
      data,
    }),
  update: (id: number, relId: number, data: SaveRelationship) =>
    call<Relationship>(`${BASE}/products/${id}/relationships/${relId}`, {
      method: 'PUT',
      data,
    }),
  remove: (id: number, relId: number) =>
    call<void>(`${BASE}/products/${id}/relationships/${relId}`, {
      method: 'DELETE',
    }),
};

// ---------- 技术资料 / 应用场景 / FAQ ----------

export interface DocumentItem {
  id: number;
  documentType: number;
  title: string;
  fileUrl: string;
  language: string;
  version: string;
  source: string;
  verified: number;
  verifiedAt?: string;
}

export interface SaveDocument {
  documentType: number;
  title: string;
  fileUrl: string;
  language?: string;
  version?: string;
  source?: string;
  verified?: number;
}

export interface ApplicationItem {
  id: number;
  title: string;
  description: string;
  icon: string;
  verified: number;
}

export interface SaveApplication {
  title: string;
  description?: string;
  icon?: string;
  verified?: number;
}

export interface FaqItem {
  id: number;
  question: string;
  answer: string;
  /** 1-品类模板、2-人工撰写或已审核、3-待审核 */
  source: number;
  reviewedBy: string;
  reviewedAt?: string;
}

export const documentApi = {
  list: (id: number) =>
    call<DocumentItem[]>(`${BASE}/products/${id}/documents`),
  create: (id: number, data: SaveDocument) =>
    call<DocumentItem>(`${BASE}/products/${id}/documents`, {
      method: 'POST',
      data,
    }),
  update: (id: number, itemId: number, data: SaveDocument) =>
    call<DocumentItem>(`${BASE}/products/${id}/documents/${itemId}`, {
      method: 'PUT',
      data,
    }),
  remove: (id: number, itemId: number) =>
    call<void>(`${BASE}/products/${id}/documents/${itemId}`, {
      method: 'DELETE',
    }),
};

export const applicationApi = {
  list: (id: number) =>
    call<ApplicationItem[]>(`${BASE}/products/${id}/applications`),
  create: (id: number, data: SaveApplication) =>
    call<ApplicationItem>(`${BASE}/products/${id}/applications`, {
      method: 'POST',
      data,
    }),
  update: (id: number, itemId: number, data: SaveApplication) =>
    call<ApplicationItem>(`${BASE}/products/${id}/applications/${itemId}`, {
      method: 'PUT',
      data,
    }),
  remove: (id: number, itemId: number) =>
    call<void>(`${BASE}/products/${id}/applications/${itemId}`, {
      method: 'DELETE',
    }),
};

export const faqApi = {
  list: (id: number) => call<FaqItem[]>(`${BASE}/products/${id}/faqs`),
  create: (id: number, data: { question: string; answer: string }) =>
    call<FaqItem>(`${BASE}/products/${id}/faqs`, { method: 'POST', data }),
  update: (
    id: number,
    itemId: number,
    data: { question: string; answer: string },
  ) =>
    call<FaqItem>(`${BASE}/products/${id}/faqs/${itemId}`, {
      method: 'PUT',
      data,
    }),
  remove: (id: number, itemId: number) =>
    call<void>(`${BASE}/products/${id}/faqs/${itemId}`, { method: 'DELETE' }),
  approve: (id: number, itemId: number) =>
    call<FaqItem>(`${BASE}/products/${id}/faqs/${itemId}/approve`, {
      method: 'PATCH',
    }),
};

// ---------- 图片与视频 ----------

export interface MediaItem {
  id: number;
  mediaType: number;
  fileUrl: string;
  storageType: number;
  coverUrl: string;
  title: string;
  fileSize: number;
  isMain: number;
  source: string;
  sortOrder: number;
}

export const mediaApi = {
  list: (id: number) => call<MediaItem[]>(`${BASE}/products/${id}/media`),
  register: (
    id: number,
    data: {
      mediaType: number;
      fileUrl: string;
      coverUrl?: string;
      title?: string;
      setMain?: boolean;
    },
  ) =>
    call<MediaItem>(`${BASE}/products/${id}/media`, { method: 'POST', data }),
  /** 上传一个文件；进度与取消由调用方通过 XHR 控制，见 uploadMedia */
  update: (
    id: number,
    itemId: number,
    data: { title?: string; coverUrl?: string; sortOrder?: number },
  ) =>
    call<MediaItem>(`${BASE}/products/${id}/media/${itemId}`, {
      method: 'PUT',
      data,
    }),
  remove: (id: number, itemId: number) =>
    call<void>(`${BASE}/products/${id}/media/${itemId}`, { method: 'DELETE' }),
  setMain: (id: number, itemId: number) =>
    call<MediaItem>(`${BASE}/products/${id}/media/${itemId}/main`, {
      method: 'PATCH',
    }),
};

export interface UploadOptions {
  file: File;
  mediaType: number;
  title?: string;
  setMain?: boolean;
  onProgress?: (percent: number) => void;
  signal?: AbortSignal;
}

/**
 * 上传一个文件。用 axios 的上传进度和 AbortSignal 支持进度条与取消；
 * 失败时抛出与其它接口一致的业务错误。
 */
export async function uploadMedia(
  productId: number,
  opts: UploadOptions,
): Promise<MediaItem> {
  const form = new FormData();
  form.append('file', opts.file);
  form.append('mediaType', String(opts.mediaType));
  if (opts.title) form.append('title', opts.title);
  if (opts.setMain) form.append('setMain', 'true');
  return call<MediaItem>(`${BASE}/products/${productId}/media/upload`, {
    method: 'POST',
    data: form,
    requestType: 'form',
    signal: opts.signal,
    onUploadProgress: (e: { loaded: number; total?: number }) => {
      if (e.total) opts.onProgress?.(Math.round((e.loaded * 100) / e.total));
    },
    ...quiet,
  });
}

// ---------- 物流 / 海关 / 参考价 ----------

export interface Logistics {
  id?: number;
  netWeightKg?: number | null;
  grossWeightKg?: number | null;
  lengthMm?: number | null;
  widthMm?: number | null;
  heightMm?: number | null;
  packageType?: string;
  packageLengthMm?: number | null;
  packageWidthMm?: number | null;
  packageHeightMm?: number | null;
  packageQuantity?: number | null;
  isDangerous?: number;
  shippingNote?: string;
}

export interface Customs {
  id?: number;
  hsCode?: string;
  customsNameCn?: string;
  customsNameEn?: string;
  originCountry?: string;
  declarationElements?: string;
  supervisionConditions?: string;
  exportRebateRate?: number | null;
}

export interface ReferencePrice {
  id?: number;
  priceOriginal?: number | null;
  currencyCode?: string;
  exchangeRate?: number | null;
  priceCny?: number | null;
  priceSource?: string;
  priceDate?: string | null;
}

export const logisticsApi = {
  get: (id: number) => call<Logistics>(`${BASE}/products/${id}/logistics`),
  save: (id: number, data: Logistics) =>
    call<Logistics>(`${BASE}/products/${id}/logistics`, {
      method: 'PUT',
      data,
    }),
};

export const customsApi = {
  get: (id: number) => call<Customs>(`${BASE}/products/${id}/customs`),
  save: (id: number, data: Customs) =>
    call<Customs>(`${BASE}/products/${id}/customs`, { method: 'PUT', data }),
};

export const priceApi = {
  get: (id: number) =>
    call<ReferencePrice>(`${BASE}/products/${id}/reference-price`),
  save: (id: number, data: ReferencePrice) =>
    call<ReferencePrice>(`${BASE}/products/${id}/reference-price`, {
      method: 'PUT',
      data,
    }),
  clear: (id: number) =>
    call<void>(`${BASE}/products/${id}/reference-price`, { method: 'DELETE' }),
};

// ---------- 内容任务：资料（中文，共享）→ 营销（按平台，本公司） ----------

/** 任务包类型：资料包、营销包 */
export type PackageType = 'facts' | 'marketing';

/** 营销平台：阿里国际站、中国制造网、独立站、社媒（以后端 MarketingPlatform 为准） */
export type MarketingPlatform = 'alibaba' | 'mic' | 'site' | 'social';

export interface MarketingPlatformOption {
  code: MarketingPlatform;
  label: string;
  langs: string[];
  /** 本公司是否启用 */
  enabled: boolean;
}

/** 某平台的不发布标记（本公司） */
export interface MarketingSkip {
  markedBy?: string;
  markedAt?: string;
}

export interface ContentTask {
  productId: number;
  taskId?: number;
  brandName?: string;
  categoryName?: string;
  mpn: string;
  productName?: string;
  /** 1-资料待补、2-资料待核、3-营销待生成、4-已完成 */
  status: number;
  /** 独立站英文、俄文 */
  enAt?: string;
  enBy?: string;
  ruAt?: string;
  ruBy?: string;
  alibabaAt?: string;
  alibabaBy?: string;
  micAt?: string;
  micBy?: string;
  socialAt?: string;
  socialBy?: string;
  /** 本公司标了不发布的平台 */
  skips?: Partial<Record<MarketingPlatform, MarketingSkip>>;
  /** 本公司最近下载营销包 */
  downloadedAt?: string;
  /** 资料（所有公司共享）：最近导入、核对、下载资料包 */
  factImportedAt?: string;
  factImportedBy?: string;
  factVerifiedAt?: string;
  factVerifiedBy?: string;
  factDownloadedAt?: string;
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface ContentTaskQuery {
  page?: number;
  pageSize?: number;
  /** 页签，不传为全部。资料任务：1、2、verified；商品运营：unverified、3、4 */
  tab?: string;
  brandId?: number;
  categoryId?: number;
  keyword?: string;
}

export interface ContentFile {
  fileName: string;
  content: string;
  /** 跳过的章节 */
  skip?: string[];
  /** 修改过的章节：章节 → 新正文 */
  edits?: Record<string, string>;
}

export interface ContentBlock {
  section: string;
  label: string;
  /** shared-共享商品库、company-本公司 */
  target: 'shared' | 'company';
  raw: string;
  text?: string;
  count: number;
  columns: string[];
  rows: string[][];
  errors: string[];
  hints: string[];
  skipped: boolean;
  edited: boolean;
}

export interface ContentPreview {
  fileName: string;
  brand?: string;
  model?: string;
  /** zh（资料包）、en、ru（营销包） */
  lang?: string;
  type?: PackageType;
  /** 营销包平台 */
  platform?: MarketingPlatform;
  productId?: number;
  productLabel?: string;
  errors: string[];
  notes: string[];
  confirmable: boolean;
  blocks: ContentBlock[];
}

export interface ContentConfirmResult {
  succeeded: number;
  failed: number;
  items: {
    fileName: string;
    productId?: number;
    productLabel?: string;
    lang?: string;
    platform?: MarketingPlatform;
    success: boolean;
    message: string;
  }[];
}

export interface ProductContent {
  /** 1-资料待补、2-资料待核、3-营销待生成、4-已完成 */
  status: number;
  factImportedAt?: string;
  factImportedBy?: string;
  factVerifiedAt?: string;
  factVerifiedBy?: string;
  /** 本公司启用的平台，按固定顺序 */
  platforms: MarketingPlatform[];
  /** 本公司各平台写入时间：独立站英文、俄文，阿里国际站、中国制造网、社媒 */
  enAt?: string;
  ruAt?: string;
  alibabaAt?: string;
  micAt?: string;
  socialAt?: string;
  skips?: Partial<Record<MarketingPlatform, MarketingSkip>>;
  /** 阿里国际站、中国制造网、社媒的内容（只含启用且写过的平台） */
  platformContents?: Partial<Record<MarketingPlatform, PlatformContent>>;
  /** 关键词库：各启用平台合并，同词一行、档位取最高，标出选用平台 */
  keywordLibrary?: {
    keyword: string;
    tier: 1 | 2 | 3;
    platforms: MarketingPlatform[];
  }[];
  /** 独立站：英文、俄文 */
  langs: Record<
    ContentLang,
    {
      seoTitle?: string;
      metaDescription?: string;
      longDescription?: string;
      geoAnswer?: string;
      faqs: { question: string; answer: string }[];
      fileName?: string;
      confirmedBy?: string;
      confirmedAt?: string;
    }
  >;
}

export interface PlatformContent {
  title?: string;
  /** 按档位（1-核心、2-主要、3-长尾）、档内顺序排好 */
  keywords: { keyword: string; tier: 1 | 2 | 3 }[];
  sellingPoints: string[];
  detail?: string;
  linkedinPost?: string;
  facebookPost?: string;
  youtubeDescription?: string;
  fileName?: string;
  confirmedBy?: string;
  confirmedAt?: string;
}

/** 上传记录：确认写入的文件；其他公司的资料包记录不显示确认人 */
export interface ContentImportRecord {
  id: number;
  fileName: string;
  packageType?: PackageType;
  platform?: MarketingPlatform | '';
  lang?: string;
  summary?: string;
  confirmedBy?: string;
  confirmedAt?: string;
  /** 是否本公司上传 */
  mine: boolean;
  /** 原文，只在详情里有 */
  rawText?: string;
}

const CONTENT = `${BASE}/content-tasks`;

/**
 * 内容任务按入口分组：facts 为「商品资料 → 资料任务」，marketing 为「运营管理 → 商品运营」；
 * 各入口只接收自己的包类型，权限分别挂在两个菜单上
 */
export const contentApi = {
  page: (entry: PackageType, q: ContentTaskQuery) =>
    call<{ total: number; records: ContentTask[] }>(
      `${CONTENT}/${entry}/page`,
      {
        method: 'POST',
        data: q,
      },
    ),
  counts: (entry: PackageType, q: ContentTaskQuery) =>
    call<Record<string, number>>(`${CONTENT}/${entry}/counts`, {
      method: 'POST',
      data: q,
    }),
  /** productId：行内上传限定的商品，文件对应的不是它时不能确认 */
  preview: (entry: PackageType, files: ContentFile[], productId?: number) =>
    call<ContentPreview[]>(`${CONTENT}/${entry}/preview`, {
      method: 'POST',
      data: { files, productId },
    }),
  confirm: (entry: PackageType, files: ContentFile[], productId?: number) =>
    call<ContentConfirmResult>(`${CONTENT}/${entry}/confirm`, {
      method: 'POST',
      data: { files, productId },
    }),
  /** 商品的上传记录（本公司全部 + 其他公司的资料包），不含原文；type 只看该包类型 */
  imports: (productId: number, type?: PackageType) =>
    call<ContentImportRecord[]>(`${CONTENT}/products/${productId}/imports`, {
      params: { type },
    }),
  /** 上传记录详情，含原文 */
  importDetail: (id: number) =>
    call<ContentImportRecord>(`${CONTENT}/imports/${id}`),
  productContent: (productId: number) =>
    call<ProductContent>(`${CONTENT}/products/${productId}`),
  /** 资料已核：所有公司共享，记录核对人 */
  verifyFacts: (productId: number) =>
    call<void>(`${CONTENT}/products/${productId}/verify-facts`, {
      method: 'POST',
    }),
  /** 营销平台与本公司是否启用 */
  platforms: () => call<MarketingPlatformOption[]>(`${CONTENT}/platforms`),
  savePlatforms: (platforms: MarketingPlatform[]) =>
    call<MarketingPlatformOption[]>(`${CONTENT}/platforms`, {
      method: 'PUT',
      data: { platforms },
    }),
  /** 本公司某商品在某平台不发布 / 撤销 */
  skip: (productId: number, platform: MarketingPlatform) =>
    call<void>(`${CONTENT}/products/${productId}/skips/${platform}`, {
      method: 'PUT',
    }),
  unskip: (productId: number, platform: MarketingPlatform) =>
    call<void>(`${CONTENT}/products/${productId}/skips/${platform}`, {
      method: 'DELETE',
    }),
  /** 下载任务包：单个是 Markdown，多个是 zip；营销包要带平台；从响应头取文件名触发浏览器下载 */
  download: async (
    productIds: number[],
    type: PackageType,
    platform?: MarketingPlatform,
  ) => {
    const res = await request<Blob>(`${CONTENT}/${type}/package`, {
      method: 'GET',
      params: { productIds: productIds.join(','), platform },
      responseType: 'blob',
      getResponse: true,
      skipErrorHandler: true,
    });
    const data = res.data as Blob;
    if (data.type.includes('json')) {
      const body = JSON.parse(await data.text()) as { message?: string };
      throw new Error(body.message || '下载失败，请稍后重试');
    }
    const disposition =
      (res.headers as Record<string, string | undefined>)?.[
        'content-disposition'
      ] ?? '';
    const match = /filename\*=UTF-8''([^;]+)/.exec(disposition);
    const name = match ? decodeURIComponent(match[1]) : 'content-task.md';
    const url = window.URL.createObjectURL(data);
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.setTimeout(() => window.URL.revokeObjectURL(url), 1000);
  },
};
