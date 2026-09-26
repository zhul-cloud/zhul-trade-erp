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
  description: string;
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
  isGenuine: number;
  aliases?: string[];
}

export interface SaveBrand {
  brandName: string;
  country?: string;
  logoUrl?: string;
  brandColor?: string;
  description?: string;
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
  description: string;
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
}

export interface SaveCategory {
  categoryCode: string;
  categoryName: string;
  categoryNameZh?: string;
  /** 为空建一级品类，否则建该一级品类下的细分品类 */
  parentId?: number | null;
  description?: string;
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
  page: (params: PageParams & { keyword?: string; status?: number }) =>
    call<PageResult<Brand>>(`${BASE}/brands`, { params }),
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
  createTime: string;
  updateTime: string;
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
