import { request } from '@umijs/max';

const API = '/api/v1/media';
const quiet = { skipErrorHandler: true } as const;

/** 菜单与按钮权限 */
export const MEDIA_PATH = '/operations/media';
export const PERM_UPLOAD = 'media:library:upload';
export const PERM_EDIT = 'media:library:edit';

export interface MediaModel {
  brand: string;
  model: string;
  productId?: number;
  productName?: string;
  /** 分组名 → 素材数（本公司的，不含官方资料） */
  groupCounts: Record<string, number>;
  hasMarketing: boolean;
  sendableCount: number;
  listingCount: number;
  lastAddedAt?: string;
}

export interface MediaModelQuery {
  keyword?: string;
  hasMarketing?: boolean;
  hasListing?: boolean;
  page: number;
  pageSize: number;
}

/** 一条素材：本公司的有 itemId；官方素材用 officialTable + officialId */
export interface MediaItem {
  itemId?: number;
  officialTable?: string;
  officialId?: number;
  category: number;
  categoryName: string;
  /** 1-图片、2-视频、3-文件 */
  kind: number;
  fileName?: string;
  fileSize?: number;
  contentType?: string;
  /** 视频：1-压缩中、3-压缩失败 */
  processingStatus?: number;
  canSend: boolean;
  forListing: boolean;
  listingAllowed: boolean;
  sourceType?: string;
  sourceId?: number;
  sourceNo?: string;
  uploaderName?: string;
  uploadedAt?: string;
  note?: string;
  publicUrl?: string;
}

export interface MediaDetail {
  brand: string;
  model: string;
  productId?: number;
  productName?: string;
  hasSendable: boolean;
  groups: { name: string; items: MediaItem[] }[];
}

export interface MediaReference {
  /** 1 同系列、2 同品牌同品类、3 同品牌、4 同品类其他品牌；0 没找到 */
  level: number;
  levelName: string;
  warning?: string;
  models: {
    productId: number;
    brand: string;
    model: string;
    productName?: string;
    inquiryCount: number;
    dealCount: number;
  }[];
}

export interface MediaRef {
  itemId?: number;
  officialTable?: string;
  officialId?: number;
  brand?: string;
  model?: string;
}

export interface FileLink {
  url: string;
  fileName: string;
  expiresAt?: string;
}

/** 素材在页面里的唯一键 */
export const itemKey = (i: MediaItem) =>
  i.itemId ? `i${i.itemId}` : `o${i.officialTable}-${i.officialId}`;

export const refOf = (i: MediaItem, brand: string, model: string): MediaRef =>
  i.itemId
    ? { itemId: i.itemId }
    : {
        officialTable: i.officialTable,
        officialId: i.officialId,
        brand,
        model,
      };

const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(method: string, url: string, data?: object) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);

export const mediaApi = {
  page: (q: MediaModelQuery) =>
    send<{ records: MediaModel[]; total: number }>(
      'POST',
      `${API}/models/page`,
      q,
    ),
  detail: (brand: string, model: string) =>
    get<MediaDetail>(`${API}/models/detail`, { brand, model }),
  references: (brand: string, model: string) =>
    get<MediaReference>(`${API}/references`, { brand, model }),
  /** 预览或下载链接，一次最多 50 个 */
  urls: (refs: MediaRef[], download: boolean) =>
    send<FileLink[]>('POST', `${API}/urls`, { refs, download }),
  flag: (ref: MediaRef, change: { canSend?: boolean; forListing?: boolean }) =>
    send<MediaItem>('POST', `${API}/flags`, { ref, ...change }),
  uploadNetwork: (brand: string, model: string, file: File, note?: string) => {
    const data = new FormData();
    data.append('file', file);
    return request<{ data: MediaItem }>(`${API}/network`, {
      method: 'POST',
      params: { brand, model, note },
      data,
      requestType: 'form',
      ...quiet,
    }).then((r) => r.data);
  },
  deleteNetwork: (itemId: number) =>
    send<void>('DELETE', `${API}/network/${itemId}`),
};

/** 逐个触发浏览器下载（链接带下载响应头，使用原文件名），间隔 300 毫秒，避免被浏览器合并或拦截 */
export const downloadAll = async (links: FileLink[]) => {
  for (const l of links) {
    const a = document.createElement('a');
    a.href = l.url;
    a.rel = 'noopener';
    document.body.appendChild(a);
    a.click();
    a.remove();
    await new Promise((r) => setTimeout(r, 300));
  }
};

export { readBizError } from '@/pages/inquiry/shared/service';
