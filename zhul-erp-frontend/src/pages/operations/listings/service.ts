import { request } from '@umijs/max';
import type {
  FileLink,
  MediaItem,
  MediaRef,
  MediaReference,
} from '@/pages/operations/media/service';
import type {
  MarketingPlatform,
  ProductContent,
} from '@/pages/product/service';

const API = '/api/v1/listing-tasks';
const quiet = { skipErrorHandler: true } as const;

/** 菜单与按钮权限 */
export const LISTING_PATH = '/operations/listings';
export const PERM_EDIT = 'operations:listing:edit';

export type ListingTab =
  | 'pending'
  | 'updated'
  | 'replace'
  | 'published'
  | 'cancelled'
  | 'all';

export interface ListingTask {
  id: number;
  productId: number;
  brand?: string;
  model?: string;
  productName?: string;
  category?: string;
  platform: MarketingPlatform;
  platformLabel: string;
  /** 1-待发布、2-已发布、3-已取消 */
  status: 1 | 2 | 3;
  statusName: string;
  cancelReason?: string;
  /** 素材待补、内容有更新、待替换素材 */
  needMedia: boolean;
  contentUpdated: boolean;
  needReplace: boolean;
  imageCount: number;
  videoCount: number;
  refBrand?: string;
  refModel?: string;
  refBy?: string;
  refTime?: string;
  listingUrl?: string;
  platformItemId?: string;
  note?: string;
  publishedBy?: string;
  publishedAt?: string;
  syncedBy?: string;
  syncedAt?: string;
  replacedBy?: string;
  replacedAt?: string;
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

/** 按商品：一个商品一行，汇总各平台任务 */
export interface ListingProduct {
  productId: number;
  brand?: string;
  model?: string;
  productName?: string;
  category?: string;
  status: Exclude<ListingTab, 'all'>;
  statusName: string;
  /** 有待发布的平台没有可用素材 */
  needMedia: boolean;
  /** 各平台任务，按平台固定顺序 */
  tasks: ListingTask[];
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface ListingQuery {
  tab?: ListingTab;
  brandId?: number;
  categoryId?: number;
  keyword?: string;
  page?: number;
  pageSize?: number;
}

export interface ListingDetail {
  task: ListingTask;
  content: ProductContent;
  /** 本型号用于发品的图片和视频 */
  ownMedia: MediaItem[];
  /** 选了参考素材时，参考型号的可发客户图片和视频 */
  referenceMedia: MediaItem[];
}

export interface PublishBody {
  listingUrl: string;
  platformItemId?: string;
  note?: string;
}

const get = <T>(url: string) =>
  request<{ data: T }>(url, { method: 'GET', ...quiet }).then((r) => r.data);
const send = <T>(method: string, url: string, data?: object) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);

export const listingApi = {
  page: (q: ListingQuery) =>
    send<{ records: ListingProduct[]; total: number }>(
      'POST',
      `${API}/page`,
      q,
    ),
  /** 一个商品的各平台任务 */
  product: (productId: number) =>
    get<ListingProduct>(`${API}/products/${productId}`),
  counts: (q: ListingQuery) =>
    send<Record<ListingTab, number>>('POST', `${API}/counts`, q),
  detail: (id: number) => get<ListingDetail>(`${API}/${id}`),
  /** 任务素材的预览或下载链接 */
  urls: (id: number, refs: MediaRef[], download: boolean) =>
    send<FileLink[]>('POST', `${API}/${id}/urls`, { refs, download }),
  references: (id: number) => get<MediaReference>(`${API}/${id}/references`),
  /** 参考型号的可发客户图片和视频（选用前预览） */
  referenceMedia: (id: number, brand: string, model: string) =>
    request<{ data: MediaItem[] }>(`${API}/${id}/references/media`, {
      method: 'GET',
      params: { brand, model },
      ...quiet,
    }).then((r) => r.data),
  /** 参考型号素材的预览或下载链接 */
  referenceUrls: (
    id: number,
    brand: string,
    model: string,
    refs: MediaRef[],
    download: boolean,
  ) =>
    request<{ data: FileLink[] }>(`${API}/${id}/references/urls`, {
      method: 'POST',
      params: { brand, model },
      data: { refs, download },
      ...quiet,
    }).then((r) => r.data),
  /** 选择参考素材；不传型号表示取消 */
  setReference: (id: number, ref?: { brand: string; model: string }) =>
    send<ListingDetail>('PUT', `${API}/${id}/reference`, ref ?? {}),
  publish: (id: number, body: PublishBody) =>
    send<ListingDetail>('POST', `${API}/${id}/publish`, body),
  updateRecord: (id: number, body: PublishBody) =>
    send<ListingDetail>('PUT', `${API}/${id}/record`, body),
  markSynced: (id: number) => send<ListingDetail>('POST', `${API}/${id}/sync`),
  markReplaced: (id: number) =>
    send<ListingDetail>('POST', `${API}/${id}/replace`),
};

export { readBizError } from '@/pages/inquiry/shared/service';
