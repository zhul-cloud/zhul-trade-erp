/**
 * 大文件分片直传（OpenSpec inquiry-large-attachments D3）。
 *
 * 流程：准备上传（后端按场景校验类型、大小、权限）→ 分片 PUT 到存储（OSS 签名地址，或本地存储时的后端分片接口）
 * → 完成（后端核对大小与文件头，返回普通的 fileKey）。同时传 4 片，每片失败自动重试 3 次，仍失败时停下，
 * 已传完的分片记在本对象里，再次调用 start() 只传剩下的；签名地址 15 分钟过期，用到时按需续签。
 */
import { request } from '@umijs/max';
import { getToken } from './tokenStore';

export interface DirectUploadedFile {
  storage: string;
  fileKey: string;
  fileName: string;
  fileSize: number;
  contentType: string;
}

interface PartUrl {
  n: number;
  url: string;
}

interface Prepared {
  uploadId: string;
  mode: 'oss' | 'local';
  partSize: number;
  partCount: number;
  parts: PartUrl[];
}

export interface PutResult {
  /** HTTP 状态；0 表示请求没到达（断网、跨域被拦） */
  status: number;
  etag?: string;
}

/** 与后端、存储打交道的接口；测试时替换成假的 */
export interface UploadApi {
  prepare: (body: {
    scene: string;
    fileName: string;
    size: number;
    existingBytes?: number;
  }) => Promise<Prepared>;
  partUrls: (id: string, from: number, count: number) => Promise<PartUrl[]>;
  complete: (
    id: string,
    parts: { n: number; etag?: string }[],
  ) => Promise<DirectUploadedFile>;
  abort: (id: string) => Promise<void>;
  put: (
    url: string,
    body: Blob,
    opts: {
      withAuth: boolean;
      onProgress: (loaded: number) => void;
      signal: AbortSignal;
    },
  ) => Promise<PutResult>;
}

/** 失败原因：network 网络中断（可继续上传）、cors 存储跨域没配好、rejected 后端拒绝（附原因）、cancelled 已取消 */
export type UploadErrorKind = 'network' | 'cors' | 'rejected' | 'cancelled';

export class UploadError extends Error {
  kind: UploadErrorKind;

  constructor(kind: UploadErrorKind, message: string) {
    super(message);
    this.kind = kind;
  }
}

export const CONCURRENCY = 4;
export const PART_RETRIES = 3;
/** 签名地址 15 分钟过期，提前 3 分钟续签 */
const URL_TTL_MS = 12 * 60 * 1000;
const RENEW_BATCH = 8;

const unwrap = <T>(p: Promise<{ data: T }>) => p.then((r) => r.data);

/** XMLHttpRequest 发 PUT：可以拿到上传进度和 ETag 响应头；不抛异常，失败看 status */
const xhrPut: UploadApi['put'] = (
  url,
  body,
  { withAuth, onProgress, signal },
) =>
  new Promise((resolve) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url);
    if (withAuth) {
      const token = getToken();
      if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`);
      xhr.setRequestHeader('Content-Type', 'application/octet-stream');
    }
    xhr.upload.onprogress = (e) => onProgress(e.loaded);
    xhr.onload = () => {
      let etag = xhr.getResponseHeader('ETag') ?? undefined;
      let status = xhr.status;
      if (withAuth && status === 200) {
        // 本地存储的分片接口返回 { code, data: { etag } }，业务失败也是 HTTP 200
        try {
          const json = JSON.parse(xhr.responseText);
          if (json.code !== 0) status = 400;
          etag = json.data?.etag ?? etag;
        } catch {
          status = 400;
        }
      }
      resolve({ status, etag });
    };
    xhr.onerror = () => resolve({ status: 0 });
    xhr.onabort = () => resolve({ status: -1 });
    signal.addEventListener('abort', () => xhr.abort());
    xhr.send(body);
  });

export const defaultUploadApi: UploadApi = {
  prepare: (body) =>
    unwrap(request('/api/v1/uploads', { method: 'POST', data: body })),
  partUrls: (id, from, count) =>
    unwrap(
      request(`/api/v1/uploads/${id}/parts`, {
        method: 'GET',
        params: { from, count },
      }),
    ),
  complete: (id, parts) =>
    unwrap(
      request(`/api/v1/uploads/${id}/complete`, {
        method: 'POST',
        data: { parts },
      }),
    ),
  abort: (id) =>
    request(`/api/v1/uploads/${id}`, { method: 'DELETE' }).then(
      () => undefined,
    ),
  put: xhrPut,
};

const errorMessage = (e: unknown) =>
  (e as { info?: { errorMessage?: string } })?.info?.errorMessage ||
  (e as Error)?.message ||
  '上传失败';

export class ChunkUpload {
  readonly file: File;
  private readonly scene: string;
  private readonly existingBytes?: number;
  private readonly api: UploadApi;
  private readonly onProgress: (loaded: number, total: number) => void;
  private prepared?: Prepared;
  private readonly urls = new Map<number, { url: string; at: number }>();
  private readonly done = new Map<number, string | undefined>();
  private readonly inflight = new Map<number, number>();
  private controller?: AbortController;
  private cancelled = false;
  private anyPartSucceeded = false;

  constructor(
    file: File,
    scene: string,
    opts: {
      existingBytes?: number;
      onProgress?: (loaded: number, total: number) => void;
      api?: UploadApi;
    } = {},
  ) {
    this.file = file;
    this.scene = scene;
    this.existingBytes = opts.existingBytes;
    this.api = opts.api ?? defaultUploadApi;
    this.onProgress = opts.onProgress ?? (() => undefined);
  }

  /** 已传完的字节数（含正在传的分片已发出的部分） */
  get loaded(): number {
    if (!this.prepared) return 0;
    let sum = 0;
    for (const n of this.done.keys()) sum += this.partLength(n);
    for (const v of this.inflight.values()) sum += v;
    return Math.min(sum, this.file.size);
  }

  /** 开始上传；网络中断后再次调用为「继续上传」，只传剩下的分片 */
  async start(): Promise<DirectUploadedFile> {
    if (this.cancelled) throw new UploadError('cancelled', '已取消');
    if (!this.prepared) {
      try {
        this.prepared = await this.api.prepare({
          scene: this.scene,
          fileName: this.file.name,
          size: this.file.size,
          existingBytes: this.existingBytes,
        });
      } catch (e) {
        throw new UploadError('rejected', errorMessage(e));
      }
      const now = Date.now();
      for (const p of this.prepared.parts)
        this.urls.set(p.n, { url: p.url, at: now });
    }
    const p = this.prepared;
    this.controller = new AbortController();
    const pending: number[] = [];
    for (let n = 1; n <= p.partCount; n++)
      if (!this.done.has(n)) pending.push(n);
    let failure: UploadError | undefined;
    const worker = async () => {
      while (!failure && pending.length > 0) {
        const n = pending.shift() as number;
        try {
          await this.sendPart(n);
        } catch (e) {
          failure ??= e as UploadError;
          this.controller?.abort();
        }
      }
    };
    this.report();
    await Promise.all(
      Array.from({ length: Math.min(CONCURRENCY, pending.length) }, worker),
    );
    if (this.cancelled) throw new UploadError('cancelled', '已取消');
    if (failure) throw failure;
    try {
      const parts = [...this.done.entries()]
        .sort((a, b) => a[0] - b[0])
        .map(([n, etag]) => ({ n, etag }));
      return await this.api.complete(p.uploadId, parts);
    } catch (e) {
      // 完成时校验不通过（大小不对、内容与类型不符）：后端已删除，这次上传作废
      this.prepared = undefined;
      this.done.clear();
      throw new UploadError('rejected', errorMessage(e));
    }
  }

  /** 取消：停止传输，通知后端清理已传的分片 */
  async cancel(): Promise<void> {
    this.cancelled = true;
    this.controller?.abort();
    if (this.prepared) {
      await this.api.abort(this.prepared.uploadId).catch(() => undefined);
    }
  }

  private partLength(n: number) {
    const size = this.prepared?.partSize ?? 0;
    return Math.min(size, this.file.size - (n - 1) * size);
  }

  private report() {
    this.onProgress(this.loaded, this.file.size);
  }

  private async urlOf(n: number, renew: boolean): Promise<string> {
    const cached = this.urls.get(n);
    if (!renew && cached && Date.now() - cached.at < URL_TTL_MS)
      return cached.url;
    const p = this.prepared as Prepared;
    const list = await this.api.partUrls(p.uploadId, n, RENEW_BATCH);
    const now = Date.now();
    for (const u of list) this.urls.set(u.n, { url: u.url, at: now });
    const got = this.urls.get(n);
    if (!got) throw new UploadError('rejected', '没有拿到上传地址');
    return got.url;
  }

  private async sendPart(n: number): Promise<void> {
    const p = this.prepared as Prepared;
    const start = (n - 1) * p.partSize;
    const blob = this.file.slice(start, start + this.partLength(n));
    let renew = false;
    let last: PutResult = { status: 0 };
    for (let attempt = 0; attempt <= PART_RETRIES; attempt++) {
      if (this.cancelled) throw new UploadError('cancelled', '已取消');
      let url: string;
      try {
        url = await this.urlOf(n, renew);
      } catch (e) {
        if (e instanceof UploadError) throw e;
        last = { status: 0 };
        continue;
      }
      last = await this.api.put(url, blob, {
        withAuth: p.mode === 'local',
        onProgress: (loaded) => {
          this.inflight.set(n, loaded);
          this.report();
        },
        signal: (this.controller as AbortController).signal,
      });
      this.inflight.delete(n);
      if (last.status >= 200 && last.status < 300) {
        this.done.set(n, last.etag);
        this.anyPartSucceeded = true;
        this.report();
        return;
      }
      if (last.status === -1) throw new UploadError('cancelled', '已取消');
      // 签名过期或令牌失效：换一个地址再试
      renew = last.status === 403 || last.status === 401;
    }
    this.report();
    const offline =
      typeof navigator !== 'undefined' && navigator.onLine === false;
    if (
      last.status === 0 &&
      p.mode === 'oss' &&
      !offline &&
      !this.anyPartSucceeded
    ) {
      throw new UploadError('cors', '上传服务暂时不可用，请联系管理员');
    }
    throw new UploadError(
      'network',
      `网络中断，已传 ${formatMb(this.loaded)} / ${formatMb(this.file.size)}`,
    );
  }
}

export const formatMb = (bytes: number) => {
  if (bytes >= 1024 * 1024 * 1024)
    return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
  const mb = bytes / 1024 / 1024;
  // 小文件保留一位小数，免得进度一直显示 0 MB
  return `${mb < 10 ? mb.toFixed(1) : Math.round(mb)} MB`;
};
