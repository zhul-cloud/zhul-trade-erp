import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import React from 'react';
import type { UploadApi } from '@/utils/chunkUpload';
import ChunkUploadList, {
  checkFile,
  type UploadRules,
  useChunkUploads,
} from './index';

const MB = 1024 * 1024;
const RULES: UploadRules = {
  scene: 'inquiry-attachment',
  exts: ['jpg', 'png', 'pdf', 'xlsx'],
  typeHint: '只支持图片、PDF、Excel',
  maxBytes: 500 * MB,
  totalBytes: 2048 * MB,
};

/** 只声明大小的假文件：不真的分配 500MB 内存 */
const bigFile = (name: string, size: number) => {
  const f = new File([''], name);
  Object.defineProperty(f, 'size', { value: size });
  return f;
};

const api = (put: UploadApi['put']): UploadApi => ({
  prepare: async ({ size }) => ({
    uploadId: 'u',
    mode: 'oss',
    partSize: 4 * MB,
    partCount: Math.max(1, Math.ceil(size / (4 * MB))),
    parts: [],
  }),
  partUrls: async (_id, from) => [{ n: from, url: `https://oss/${from}` }],
  complete: async () => ({
    storage: 'oss',
    fileKey: 'k',
    fileName: 'a.pdf',
    fileSize: 1,
    contentType: 'application/pdf',
  }),
  abort: async () => undefined,
  put,
});

const Harness: React.FC<{ files: File[]; api: UploadApi; other?: number }> = ({
  files,
  api: fake,
  other = 0,
}) => {
  const uploads = useChunkUploads(RULES, other, fake);
  return (
    <>
      <button type="button" onClick={() => uploads.add(files)}>
        pick
      </button>
      <span data-testid="busy">{uploads.busy}</span>
      <ChunkUploadList
        items={uploads.items}
        onResume={uploads.resume}
        onRemove={uploads.remove}
      />
    </>
  );
};

describe('checkFile', () => {
  it('类型、单个大小、合计大小', () => {
    expect(checkFile({ name: 'a.zip', size: 1 }, RULES, 0)).toBe(
      '只支持图片、PDF、Excel',
    );
    expect(checkFile({ name: 'a.pdf', size: 501 * MB }, RULES, 0)).toBe(
      '超过 500 MB，可拆分后再传',
    );
    expect(checkFile({ name: 'a.pdf', size: 400 * MB }, RULES, 1741 * MB)).toBe(
      '附件合计会超过 2.0 GB（已有 1.7 GB）',
    );
    expect(
      checkFile({ name: 'A.PDF', size: 400 * MB }, RULES, 0),
    ).toBeUndefined();
  });
});

describe('ChunkUploadList', () => {
  it('超限与类型不符时拦下，不开始上传', async () => {
    const put = jest.fn();
    render(
      <Harness
        api={api(put)}
        files={[bigFile('图纸合集.pdf', 620 * MB), bigFile('资料.zip', MB)]}
      />,
    );
    fireEvent.click(screen.getByText('pick'));
    expect(await screen.findByText('超过 500 MB，可拆分后再传')).toBeTruthy();
    expect(screen.getByText('只支持图片、PDF、Excel')).toBeTruthy();
    expect(screen.getAllByText('知道了')).toHaveLength(2);
    expect(put).not.toHaveBeenCalled();
    expect(screen.getByTestId('busy').textContent).toBe('0');
  });

  it('存储连不上时提示上传服务暂时不可用，可以重试', async () => {
    const put = jest.fn(async () => ({ status: 0 }));
    render(<Harness api={api(put)} files={[bigFile('报价单.pdf', MB)]} />);
    await act(async () => fireEvent.click(screen.getByText('pick')));
    expect(await screen.findByText('上传服务暂时不可用')).toBeTruthy();
    expect(screen.getByText('重试')).toBeTruthy();
    expect(screen.getByTestId('busy').textContent).toBe('1');
  });

  it('上传中显示进度，计入没传完的个数；传完后不再计入', async () => {
    let finish: (v: { status: number; etag: string }) => void = () => undefined;
    const put = jest.fn(
      (_url: string, _body: Blob, opts: { onProgress: (n: number) => void }) =>
        new Promise<{ status: number; etag: string }>((resolve) => {
          opts.onProgress(MB / 2);
          finish = resolve;
        }),
    );
    render(<Harness api={api(put)} files={[bigFile('清单.pdf', MB)]} />);
    await act(async () => fireEvent.click(screen.getByText('pick')));
    expect(await screen.findByText('0.5 MB / 1.0 MB · 50%')).toBeTruthy();
    expect(screen.getByTestId('busy').textContent).toBe('1');
    expect(screen.getByText('取消')).toBeTruthy();
    await act(async () => finish({ status: 200, etag: 'e' }));
    await waitFor(() =>
      expect(screen.getByTestId('busy').textContent).toBe('0'),
    );
    expect(screen.getByText('删除')).toBeTruthy();
  });
});
