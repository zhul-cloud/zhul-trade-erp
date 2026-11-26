import { request } from '@umijs/max';
import type { FileLink, MediaItem } from '@/pages/operations/media/service';
import type { ProductOption } from '@/pages/product/service';

const API = '/api/v1/video-tasks';
const quiet = { skipErrorHandler: true } as const;

/** 菜单与按钮权限 */
export const VIDEO_PATH = '/operations/videos';
export const PERM_UPLOAD = 'operations:video:upload';
export const PERM_REVIEW = 'operations:video:review';

/** 规格里的限制，与后端 VideoConstants 一致 */
export const LIMITS = {
  maxImages: 12,
  imageMaxBytes: 20 * 1024 * 1024,
  minShots: 4,
  maxShots: 8,
  maxTitle: 100,
  maxCaption: 120,
  maxReject: 200,
  wordsPerSecond: 2.5,
  minSeconds: 15,
  maxSeconds: 20,
  /** 上传前浏览器把长边缩到这个像素 */
  resizeTo: 2560,
};

export const IMAGE_EXTS = ['jpg', 'jpeg', 'png', 'webp'];

export type VideoTab =
  | 'all'
  | 'pending'
  | 'queued'
  | 'review'
  | 'approved'
  | 'rejected'
  | 'failed';

/** 1-待生成、2-排队中、3-生成中、4-待审核、5-已通过、6-已退回、7-生成失败 */
export type VideoStatus = 1 | 2 | 3 | 4 | 5 | 6 | 7;

export const STATUS = {
  PENDING: 1,
  QUEUED: 2,
  GENERATING: 3,
  REVIEW: 4,
  APPROVED: 5,
  REJECTED: 6,
  FAILED: 7,
} as const;

/** 可以加入队列的状态 */
export const QUEUEABLE: VideoStatus[] = [1, 5, 6, 7];
/** 排队中、生成中不能改素材和脚本 */
export const LOCKED: VideoStatus[] = [2, 3];

export interface VideoTask {
  id: number;
  productId: number;
  brand?: string;
  model?: string;
  productName?: string;
  category?: string;
  status: VideoStatus;
  statusName: string;
  /** 排队中：全服务器队列里的位置（从 1 起） */
  queuePosition?: number;
  expectedAt?: string;
  failReason?: string;
  rejectReason?: string;
  usedCount: number;
  imageCount: number;
  thumbnailIds?: number[];
  videoAttachmentId?: number;
  coverAttachmentId?: number;
  createTime?: string;
  createBy?: string;
  updateTime?: string;
  updateBy?: string;
}

export interface VideoMaterial {
  attachmentId: number;
  fileName?: string;
  fileSize?: number;
  sort: number;
  used: boolean;
}

export interface VideoShot {
  attachmentId?: number;
  caption: string;
}

export interface VideoScript {
  title: string;
  shots: VideoShot[];
}

export interface VideoOutput {
  id: number;
  version: number;
  /** 9:16 或 16:9 */
  aspect: string;
  videoAttachmentId: number;
  coverAttachmentId?: number;
  durationSec?: number;
  /** 1-待审核、2-已通过、3-已替换、4-已退回 */
  status: number;
  statusName: string;
}

export interface VideoLog {
  action: string;
  actionName: string;
  version?: number;
  note?: string;
  operator?: string;
  time: string;
}

export interface VideoTaskDetail extends VideoTask {
  materials: VideoMaterial[];
  script?: VideoScript;
  /** 1-AI 生成、2-手写 */
  scriptSource?: number;
  /** 0-没有、1-AI 生成中、2-已有、3-AI 失败 */
  scriptStatus?: number;
  /** 脚本不合格的原因，或 AI 失败的原因 */
  scriptError?: string;
  shotSeconds: number[];
  totalSeconds?: number;
  factsUnverified?: boolean;
  /** 当前版本的输出（待审核或已通过） */
  outputs: VideoOutput[];
  /** 素材库里正在用的已通过版本（重新生成期间仍是旧版本） */
  approvedOutputs: VideoOutput[];
  logs: VideoLog[];
}

export interface VideoQuery {
  keyword?: string;
  brandId?: number;
  categoryId?: number;
}

export interface FolderMatch {
  folder: string;
  /** matched-已匹配、multiple-有多个商品、none-未匹配 */
  result: 'matched' | 'multiple' | 'none';
  products: ProductOption[];
  hasTask?: boolean;
}

export interface BatchQueueResult {
  queued: number;
  skipped: { id: number; model?: string; reason: string }[];
}

export interface CompanyMedia {
  brand: string;
  model: string;
  items: { media: MediaItem; official: boolean }[];
}

const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(method: string, url: string, data?: object, params?: object) =>
  request<{ data: T }>(url, { method, data, params, ...quiet }).then(
    (r) => r.data,
  );

export const videoApi = {
  page: (q: VideoQuery & { tab: VideoTab; page: number; pageSize: number }) =>
    send<{ records: VideoTask[]; total: number }>('POST', `${API}/page`, q),
  counts: (q: VideoQuery) =>
    send<Partial<Record<VideoTab, number>>>('POST', `${API}/counts`, q),
  detail: (id: number) => get<VideoTaskDetail>(`${API}/${id}`),
  /** 商品还没有视频任务时返回空 */
  byProduct: (productId: number) =>
    get<VideoTaskDetail | null>(`${API}/products/${productId}`),
  nextReview: (after?: number) =>
    get<number | null>(`${API}/next-review`, { after }),
  links: (id: number, attachmentIds: number[], download = false) =>
    send<FileLink[]>('POST', `${API}/${id}/links`, { attachmentIds, download }),
  upload: (productId: number, file: Blob, fileName: string) => {
    const data = new FormData();
    data.append('file', file, fileName);
    return request<{ data: VideoMaterial }>(
      `${API}/products/${productId}/images`,
      { method: 'POST', data, requestType: 'form', ...quiet },
    ).then((r) => r.data);
  },
  saveMaterials: (
    id: number,
    items: { attachmentId: number; used: boolean }[],
  ) => send<VideoTaskDetail>('PUT', `${API}/${id}/materials`, { items }),
  removeMaterial: (id: number, attachmentId: number) =>
    send<VideoTaskDetail>('DELETE', `${API}/${id}/materials/${attachmentId}`),
  saveScript: (id: number, script: VideoScript) =>
    send<VideoTaskDetail>('PUT', `${API}/${id}/script`, script),
  generateScript: (id: number, onlyIfMissing = false) =>
    send<VideoTaskDetail>('POST', `${API}/${id}/script/generate`, undefined, {
      onlyIfMissing,
    }),
  queue: (id: number) => send<VideoTaskDetail>('POST', `${API}/${id}/queue`),
  dequeue: (id: number) =>
    send<VideoTaskDetail>('POST', `${API}/${id}/dequeue`),
  batchQueue: (ids: number[]) =>
    send<BatchQueueResult>('POST', `${API}/batch-queue`, { ids }),
  matchFolders: (folders: string[]) =>
    send<FolderMatch[]>('POST', `${API}/folders/match`, { folders }),
  approve: (id: number) =>
    send<VideoTaskDetail>('POST', `${API}/${id}/approve`),
  reject: (id: number, reason: string) =>
    send<VideoTaskDetail>('POST', `${API}/${id}/reject`, { reason }),
  window: () => get<{ window: string }>(`${API}/settings/window`),
  saveWindow: (window: string) =>
    send<{ window: string }>('PUT', `${API}/settings/window`, { window }),
};

export const companyMediaApi = {
  list: (productId: number) =>
    get<CompanyMedia>(`/api/v1/products/${productId}/company-media`),
  makeOfficial: (productId: number, itemId: number) =>
    send<void>(
      'POST',
      `/api/v1/products/${productId}/company-media/${itemId}/official`,
    ),
};

/** 字幕按词数估算秒数（与后端 ScriptRules 一致，保留 1 位小数） */
export const captionSeconds = (caption?: string) => {
  const words = (caption ?? '').trim().split(/\s+/).filter(Boolean).length;
  return Math.round((words / LIMITS.wordsPerSecond) * 10) / 10;
};

export const extOf = (name: string) =>
  name.includes('.') ? (name.split('.').pop() ?? '').toLowerCase() : '';

/**
 * 浏览器里把长边缩到 2560 像素再上传（大图生成很慢，见 design）；本来就小的图片原样上传。
 * 输出 JPEG（PNG 透明背景会变白），文件名改成 .jpg。
 */
export const resizeForUpload = async (
  file: File,
): Promise<{ blob: Blob; name: string }> => {
  const bitmap = await createImageBitmap(file).catch(() => null);
  if (!bitmap) return { blob: file, name: file.name };
  const scale = LIMITS.resizeTo / Math.max(bitmap.width, bitmap.height);
  if (scale >= 1) {
    bitmap.close();
    return { blob: file, name: file.name };
  }
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    bitmap.close();
    return { blob: file, name: file.name };
  }
  ctx.fillStyle = '#fff';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  const blob = await new Promise<Blob | null>((r) =>
    canvas.toBlob(r, 'image/jpeg', 0.9),
  );
  if (!blob) return { blob: file, name: file.name };
  return { blob, name: `${file.name.replace(/\.[^.]+$/, '')}.jpg` };
};

/** 前端先挡掉不支持的格式（后端还会按文件头再校验一次） */
export const checkImage = (file: File): string | undefined => {
  if (!IMAGE_EXTS.includes(extOf(file.name)))
    return '只支持 JPG、PNG、WEBP 图片，请先转成 JPG';
  if (file.size > LIMITS.imageMaxBytes) return '单张图片不能超过 20MB';
  return undefined;
};

export { readBizError } from '@/pages/inquiry/shared/service';
