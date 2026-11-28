import { request } from '@umijs/max';
import type { ListingDetail } from '../service';

const API = '/api/v1/websites';
const quiet = { skipErrorHandler: true } as const;

/** 发布到站点，与后端 WebsiteConstants.PERM_PUBLISH 一致 */
export const PERM_PUBLISH = 'operations:website:publish';

/** 发品任务状态（独立站）：4-发布中、5-已上线待确认 */
export const TASK_PUBLISHING = 4;
export const TASK_LIVE_UNCONFIRMED = 5;

export interface PublishTarget {
  id: number;
  name: string;
}

export interface PreviewPrice {
  amount?: number | null;
  currency?: string | null;
  source?: string;
  sourceName?: string;
  quoteId?: number | null;
  unitPriceCny?: number | null;
  factor?: number | null;
  rate?: number | null;
  rateDate?: string | null;
}

export interface Change {
  field: string;
  before: unknown;
  after: unknown;
}

/** 站点上的一张图片或一个视频 */
export interface SiteMedia {
  src: string;
  tag?: string;
  title?: string;
}

/** 写入站点的数据：字段见 SiteEntry */
export type SiteData = Record<string, unknown> & {
  photos?: SiteMedia[];
  videos?: SiteMedia[];
  specs?: [string, string][];
  faq?: unknown[];
  applications?: unknown[];
  compatibility?: unknown[];
};

export interface Preview {
  taskId: number;
  websiteProductId: number;
  productId: number;
  brand?: string;
  model: string;
  slug: string;
  siteCategory?: string;
  siteStatus?: string;
  liveUrls: Record<string, string>;
  seoTitles: Record<string, string>;
  price?: PreviewPrice;
  referenceMedia: boolean;
  data: SiteData;
  blockers: string[];
  warnings: string[];
  firstPublish: boolean;
  changes: Change[];
  publishable: boolean;
}

/** 发布单：1-排队中、2-执行中、3-已完成、4-失败、5-部署失败 */
export const RELEASE_QUEUED = 1;
export const RELEASE_RUNNING = 2;
export const RELEASE_DONE = 3;
export const RELEASE_FAILED = 4;
export const RELEASE_DEPLOY_FAILED = 5;

/** 线上检查一个链接的结果 */
export interface PageCheck {
  url: string;
  ok: boolean;
  errors?: string[];
}

export interface ReleaseItem {
  websiteProductId: number;
  listingTaskId: number;
  brand?: string;
  model?: string;
  liveUrls?: Record<string, string> | null;
  checkResult?: PageCheck[] | null;
}

/** 步骤：1-执行中、2-成功、3-失败、4-有提醒 */
export interface ReleaseStep {
  step: string;
  attempt: number;
  status: number;
  startedAt?: string;
  endedAt?: string;
  message?: string;
  output?: string;
}

export interface Release {
  id: number;
  siteId: number;
  releaseNo: string;
  status: number;
  statusName: string;
  itemCount: number;
  createBy?: string;
  createTime?: string;
  startedAt?: string;
  endedAt?: string;
  commitSha?: string;
  failedStep?: string;
  error?: string;
  warnings?: string[] | null;
  items: ReleaseItem[];
  steps: ReleaseStep[];
}

const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(method: string, url: string, data?: object) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);

export const sitePublishApi = {
  targets: () => get<PublishTarget[]>(`${API}/publish-targets`),
  preview: (siteId: number, taskIds: number[]) =>
    send<Preview[]>('POST', `${API}/${siteId}/preview`, { taskIds }),
  /** 生成发布单并排队，站点空闲时立即开始 */
  release: (siteId: number, taskIds: number[]) =>
    send<Release>('POST', `${API}/${siteId}/releases`, { taskIds }),
  releases: (siteId: number) => get<Release[]>(`${API}/${siteId}/releases`),
  releasesOfTask: (taskId: number) =>
    get<Release[]>(`${API}/releases`, { taskId }),
  detail: (id: number) => get<Release>(`${API}/releases/${id}`),
  retry: (id: number) => send<Release>('POST', `${API}/releases/${id}/retry`),
  /** 手工站点售价；null 表示取消手工售价 */
  setPrice: (websiteProductId: number, price: number | null) =>
    send<void>('PUT', `${API}/products/${websiteProductId}/price`, { price }),
  /** 站点状态：legacy，或空表示按生命周期 */
  setSiteStatus: (websiteProductId: number, status: string) =>
    send<void>('PUT', `${API}/products/${websiteProductId}/site-status`, {
      status,
    }),
  confirmLive: (taskId: number, purged: boolean) =>
    send<ListingDetail>(
      'POST',
      `/api/v1/listing-tasks/${taskId}/confirm-live`,
      {
        purged,
      },
    ),
};

/** 发布单的步骤，按执行顺序；deploy_prepare、indexnow 没执行时不显示 */
export const STEPS: { key: string; name: string; hint: string }[] = [
  { key: 'pull', name: '拉取仓库', hint: '取站点仓库最新代码' },
  { key: 'write', name: '写入数据', hint: '把商品数据写进 site-data.json' },
  { key: 'build', name: '构建', hint: '运行 erp/build.sh 生成页面' },
  { key: 'local_check', name: '本地检查', hint: '检查生成的页面' },
  { key: 'commit', name: '提交', hint: '在本地仓库提交这次改动' },
  { key: 'deploy_prepare', name: '准备部署', hint: '检查部署服务器' },
  { key: 'deploy', name: '部署', hint: '同步到站点服务器' },
  {
    key: 'online_check',
    name: '线上检查',
    hint: '逐个访问上线链接，确认是这次的内容',
  },
  { key: 'push', name: '推送仓库', hint: '把提交推送到站点仓库' },
  { key: 'indexnow', name: '推送收录', hint: 'IndexNow：Bing、Yandex' },
];
/** 发布单交给发布服务就被拒绝时，后端记的失败步骤是 submit，不在 STEPS 里 */
export const stepName = (key?: string) =>
  key === 'submit'
    ? '提交给发布服务'
    : (STEPS.find((s) => s.key === key)?.name ?? key ?? '');

export const LANG_LABEL: Record<string, string> = { en: 'EN', ru: 'RU' };
