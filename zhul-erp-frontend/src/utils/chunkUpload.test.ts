import {
  ChunkUpload,
  type PutResult,
  type UploadApi,
  UploadError,
} from './chunkUpload';

const MB = 1024 * 1024;

/** 假的上传接口：partSize 4MB；put 的结果按 (分片号, 第几次) 决定 */
const fakeApi = (
  size: number,
  opts: {
    mode?: 'oss' | 'local';
    put?: (n: number, attempt: number) => PutResult;
    completeError?: string;
  } = {},
) => {
  const partSize = 4 * MB;
  const partCount = Math.ceil(size / partSize);
  const attempts = new Map<number, number>();
  const calls = {
    put: [] as number[],
    partUrls: [] as number[],
    abort: 0,
    completed: [] as { n: number; etag?: string }[],
  };
  const api: UploadApi = {
    prepare: jest.fn(async () => ({
      uploadId: 'u1',
      mode: opts.mode ?? 'oss',
      partSize,
      partCount,
      parts: [{ n: 1, url: 'https://oss/1' }],
    })),
    partUrls: jest.fn(async (_id, from, count) => {
      calls.partUrls.push(from);
      return Array.from(
        { length: Math.min(count, partCount - from + 1) },
        (_, i) => ({ n: from + i, url: `https://oss/${from + i}` }),
      );
    }),
    complete: jest.fn(async (_id, parts) => {
      if (opts.completeError) throw new Error(opts.completeError);
      calls.completed = parts;
      return {
        storage: 'oss',
        fileKey: 'k',
        fileName: 'a.pdf',
        fileSize: size,
        contentType: 'application/pdf',
      };
    }),
    abort: jest.fn(async () => {
      calls.abort += 1;
    }),
    put: jest.fn(async (url, body, { onProgress }) => {
      const n = Number(url.split('/').pop());
      const attempt = (attempts.get(n) ?? 0) + 1;
      attempts.set(n, attempt);
      calls.put.push(n);
      const result = opts.put?.(n, attempt) ?? { status: 200, etag: `e${n}` };
      if (result.status === 200) onProgress(body.size);
      return result;
    }),
  };
  return { api, calls };
};

const fileOf = (size: number) =>
  new File([new Uint8Array(size)], 'a.pdf', { type: 'application/pdf' });

describe('ChunkUpload', () => {
  it('分片上传、进度到 100%、按编号完成', async () => {
    const size = 10 * MB;
    const { api, calls } = fakeApi(size);
    const progress: number[] = [];
    const up = new ChunkUpload(fileOf(size), 'inquiry-attachment', {
      api,
      onProgress: (loaded) => progress.push(loaded),
    });
    const f = await up.start();
    expect(f.fileKey).toBe('k');
    expect(calls.completed).toEqual([
      { n: 1, etag: 'e1' },
      { n: 2, etag: 'e2' },
      { n: 3, etag: 'e3' },
    ]);
    expect(progress[progress.length - 1]).toBe(size);
    expect(Math.max(...progress)).toBeLessThanOrEqual(size);
  });

  it('某片失败后自动重试成功', async () => {
    const { api, calls } = fakeApi(8 * MB, {
      put: (n, attempt) =>
        n === 2 && attempt < 3 ? { status: 0 } : { status: 200, etag: 'x' },
    });
    await new ChunkUpload(fileOf(8 * MB), 's', { api }).start();
    expect(calls.put.filter((n) => n === 2)).toHaveLength(3);
  });

  it('重试用尽后停下，继续上传只传剩余分片', async () => {
    let broken = true;
    const { api, calls } = fakeApi(12 * MB, {
      put: (n) =>
        n === 3 && broken ? { status: 0 } : { status: 200, etag: `e${n}` },
    });
    const up = new ChunkUpload(fileOf(12 * MB), 's', { api });
    // 第 1 片成功过，status 0 不会被当成跨域没配好
    const err = await up.start().catch((e) => e);
    expect(err).toBeInstanceOf(UploadError);
    expect((err as UploadError).kind).toBe('network');
    expect(calls.put.filter((n) => n === 3)).toHaveLength(4);

    broken = false;
    calls.put = [];
    await up.start();
    expect(calls.put).toEqual([3]);
    expect(calls.completed.map((p) => p.n)).toEqual([1, 2, 3]);
  });

  it('第一片就连不上存储时提示上传服务不可用', async () => {
    const { api } = fakeApi(4 * MB, { put: () => ({ status: 0 }) });
    const err = (await new ChunkUpload(fileOf(4 * MB), 's', { api })
      .start()
      .catch((e) => e)) as UploadError;
    expect(err.kind).toBe('cors');
    expect(err.message).toContain('上传服务暂时不可用');
  });

  it('签名过期（403）时续签后重传', async () => {
    const { api, calls } = fakeApi(4 * MB, {
      put: (_n, attempt) =>
        attempt === 1 ? { status: 403 } : { status: 200, etag: 'e' },
    });
    await new ChunkUpload(fileOf(4 * MB), 's', { api }).start();
    expect(calls.partUrls).toEqual([1]);
  });

  it('取消时通知后端清理', async () => {
    const { api, calls } = fakeApi(4 * MB);
    const up = new ChunkUpload(fileOf(4 * MB), 's', { api });
    await up.start();
    await up.cancel();
    expect(calls.abort).toBe(1);
    await expect(up.start()).rejects.toMatchObject({ kind: 'cancelled' });
  });

  it('准备时被拒、完成时校验不通过都带后端原因', async () => {
    const { api } = fakeApi(4 * MB, {
      completeError: '文件内容不是 PDF，已删除',
    });
    const err = (await new ChunkUpload(fileOf(4 * MB), 's', { api })
      .start()
      .catch((e) => e)) as UploadError;
    expect(err.kind).toBe('rejected');
    expect(err.message).toBe('文件内容不是 PDF，已删除');

    const rejected = fakeApi(4 * MB);
    (rejected.api.prepare as jest.Mock).mockRejectedValue(
      new Error('单个文件不能超过 500MB'),
    );
    await expect(
      new ChunkUpload(fileOf(4 * MB), 's', { api: rejected.api }).start(),
    ).rejects.toMatchObject({
      kind: 'rejected',
      message: '单个文件不能超过 500MB',
    });
  });

  it('本地存储时带登录信息上传', async () => {
    const { api } = fakeApi(4 * MB, { mode: 'local' });
    await new ChunkUpload(fileOf(4 * MB), 's', { api }).start();
    expect((api.put as jest.Mock).mock.calls[0][2].withAuth).toBe(true);
  });
});
