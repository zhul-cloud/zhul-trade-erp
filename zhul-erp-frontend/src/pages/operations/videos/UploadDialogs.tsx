import { App, Button, Modal, Progress, Select, Upload } from 'antd';
import {
  CircleAlert,
  CircleCheck,
  CircleX,
  Download,
  FolderOpen,
  Inbox,
  LoaderCircle,
  Upload as UploadIcon,
} from 'lucide-react';
import React, { useMemo, useRef, useState } from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import { ProductSelect } from '@/pages/product/components/ProductSelect';
import type { ProductOption } from '@/pages/product/service';
import { useAppTheme } from '@/theme/AppTheme';
import {
  checkImage,
  type FolderMatch,
  LIMITS,
  readBizError,
  resizeForUpload,
  videoApi,
} from './service';

/** 依次上传（缩图后），返回失败的文件与原因 */
const uploadFiles = async (
  productId: number,
  files: File[],
  onDone?: () => void,
): Promise<{ file: File; error: string }[]> => {
  const failed: { file: File; error: string }[] = [];
  for (const f of files) {
    try {
      const { blob, name } = await resizeForUpload(f);
      await videoApi.upload(productId, blob, name);
    } catch (e) {
      failed.push({ file: f, error: readBizError(e).message });
    }
    onDone?.();
  }
  return failed;
};

/** 上传素材：选一个商品，选若干图片；完成后进入任务详情 */
export const UploadModal: React.FC<{
  open: boolean;
  onClose: () => void;
  onDone: (productId: number) => void;
}> = ({ open, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [product, setProduct] = useState<number>();
  const [files, setFiles] = useState<File[]>([]);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(0);

  const reset = () => {
    setProduct(undefined);
    setFiles([]);
    setDone(0);
  };

  const submit = async () => {
    if (!product || !files.length) return;
    setBusy(true);
    setDone(0);
    const failed = await uploadFiles(product, files, () =>
      setDone((n) => n + 1),
    );
    setBusy(false);
    if (failed.length) {
      message.error(`${failed.length} 张没有上传：${failed[0].error}`);
      setFiles(failed.map((f) => f.file));
      if (failed.length === files.length) return;
    } else {
      message.success(`已上传 ${files.length} 张`);
    }
    const id = product;
    reset();
    onDone(id);
  };

  return (
    <Modal
      title="上传素材"
      open={open}
      onCancel={() => {
        if (busy) return;
        reset();
        onClose();
      }}
      okText={busy ? `上传中 ${done}/${files.length}` : '上传'}
      okButtonProps={{ disabled: !product || !files.length, loading: busy }}
      onOk={submit}
      destroyOnHidden
    >
      <div style={{ display: 'grid', gap: 12 }}>
        <ProductSelect
          value={product}
          onChange={(id) => setProduct(id)}
          aria-label="商品"
          style={{ width: '100%' }}
        />
        <Upload.Dragger
          multiple
          accept=".jpg,.jpeg,.png,.webp"
          showUploadList={false}
          beforeUpload={(f, list) => {
            if (f === list[0]) {
              const ok: File[] = [];
              const bad: string[] = [];
              for (const x of list) {
                const err = checkImage(x);
                if (err) bad.push(`${x.name}：${err}`);
                else ok.push(x);
              }
              if (bad.length) message.warning(bad.slice(0, 3).join('；'));
              setFiles((prev) => [...prev, ...ok].slice(0, LIMITS.maxImages));
            }
            return Upload.LIST_IGNORE;
          }}
        >
          <p className="ant-upload-drag-icon">
            <Inbox size="1em" />
          </p>
          <p>点击或拖入图片</p>
          <p style={{ fontSize: 12, color: palette.mute }}>
            JPG / PNG / WEBP，单张不超过 20MB，每个型号最多 12 张；HEIC 请先转成
            JPG
          </p>
        </Upload.Dragger>
        {files.length > 0 && (
          <div style={{ fontSize: 12, color: palette.sub }}>
            已选 {files.length} 张：
            {files
              .slice(0, 6)
              .map((f) => f.name)
              .join('、')}
            {files.length > 6 ? ' …' : ''}
          </div>
        )}
        {busy && <Progress percent={Math.round((done / files.length) * 100)} />}
      </div>
    </Modal>
  );
};

interface Folder {
  name: string;
  files: File[];
}

interface Row extends FolderMatch {
  files: File[];
  productId?: number;
  state?: 'waiting' | 'uploading' | 'done' | 'failed';
  error?: string;
  failedFiles?: File[];
}

/** 批量上传：选一个文件夹，子文件夹名是型号（或「品牌_型号」） */
export const BatchUploadModal: React.FC<{
  open: boolean;
  onClose: (changed: boolean) => void;
}> = ({ open, onClose }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const input = useRef<HTMLInputElement>(null);
  const [root, setRoot] = useState('');
  const [rows, setRows] = useState<Row[]>([]);
  const [skipped, setSkipped] = useState<string[]>([]);
  const [matching, setMatching] = useState(false);
  const [running, setRunning] = useState(false);
  const [changed, setChanged] = useState(false);

  const reset = () => {
    setRoot('');
    setRows([]);
    setSkipped([]);
    setChanged(false);
  };

  const pick = async (list: FileList | null) => {
    if (!list?.length) return;
    const folders = new Map<string, Folder>();
    const bad: string[] = [];
    let rootName = '';
    for (const f of Array.from(list)) {
      const parts = (f.webkitRelativePath || f.name).split('/');
      rootName = parts[0];
      if (f.name.startsWith('.')) continue;
      // 根目录 / 子文件夹 / 文件；更深的层级按子文件夹归并
      const sub = parts.length >= 3 ? parts[1] : '';
      if (!sub) {
        bad.push(f.name);
        continue;
      }
      if (checkImage(f)) {
        bad.push(f.name);
        continue;
      }
      const g = folders.get(sub) ?? { name: sub, files: [] };
      g.files.push(f);
      folders.set(sub, g);
    }
    setRoot(rootName);
    setSkipped(bad);
    if (!folders.size) {
      setRows([]);
      message.warning('没有找到子文件夹里的图片：子文件夹名写型号，里面放图片');
      return;
    }
    setMatching(true);
    try {
      const names = Array.from(folders.keys());
      const res = await videoApi.matchFolders(names);
      setRows(
        res.map((m, i) => ({
          ...m,
          files: folders.get(names[i])?.files ?? [],
          productId: m.result === 'matched' ? m.products[0]?.id : undefined,
        })),
      );
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setMatching(false);
    }
  };

  const matched = rows.filter((r) => r.result === 'matched');
  const multiple = rows.filter((r) => r.result === 'multiple');
  const none = rows.filter((r) => r.result === 'none');
  const ready = rows.filter((r) => r.productId);
  const started = rows.some((r) => r.state);

  const runRow = async (row: Row) => {
    const productId = row.productId;
    if (!productId) return;
    setRows((rs) =>
      rs.map((r) =>
        r === row ? { ...r, state: 'uploading', error: undefined } : r,
      ),
    );
    const failed = await uploadFiles(productId, row.failedFiles ?? row.files);
    setChanged(true);
    setRows((rs) =>
      rs.map((r) =>
        r.folder === row.folder
          ? failed.length
            ? {
                ...r,
                state: 'failed',
                error: failed[0].error,
                failedFiles: failed.map((f) => f.file),
              }
            : { ...r, state: 'done', failedFiles: undefined }
          : r,
      ),
    );
  };

  const run = async () => {
    setRunning(true);
    setRows((rs) =>
      rs.map((r) => (r.productId ? { ...r, state: 'waiting' } : r)),
    );
    for (const r of ready) {
      await runRow(r);
    }
    setRunning(false);
  };

  const downloadNone = () => {
    const blob = new Blob([none.map((r) => r.folder).join('\n')], {
      type: 'text/plain;charset=utf-8',
    });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = '未匹配的文件夹.txt';
    a.click();
    URL.revokeObjectURL(a.href);
  };

  const counts = useMemo(
    () => [
      { label: '已匹配', value: matched.length, color: palette.green },
      { label: '有多个商品', value: multiple.length, color: palette.orange },
      { label: '未匹配', value: none.length, color: palette.red },
      { label: '跳过的文件', value: skipped.length, color: palette.sub },
    ],
    [matched.length, multiple.length, none.length, skipped.length, palette],
  );

  const doneCount = rows.filter((r) => r.state === 'done').length;
  const stateIcon = (r: Row) =>
    r.state === 'uploading' ? (
      <LoaderCircle size="1em" className="zhul-spin" />
    ) : r.state === 'done' ? (
      <CircleCheck size="1em" style={{ color: palette.green }} />
    ) : r.state === 'failed' ? (
      <CircleX size="1em" style={{ color: palette.red }} />
    ) : null;

  const section = (
    icon: React.ReactNode,
    title: string,
    hint: string,
    extra?: React.ReactNode,
  ) => (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        margin: '16px 0 8px',
      }}
    >
      {icon}
      <b>{title}</b>
      <span style={{ fontSize: 12, color: palette.mute }}>{hint}</span>
      <span style={{ flex: 1 }} />
      {extra}
    </div>
  );

  const box: React.CSSProperties = {
    border: `1px solid ${palette.hairline}`,
    borderRadius: 10,
    overflow: 'hidden',
  };
  const line: React.CSSProperties = {
    display: 'grid',
    gridTemplateColumns: '1.1fr 1.6fr 48px 90px',
    gap: 12,
    alignItems: 'center',
    padding: '8px 14px',
    borderTop: `1px solid ${palette.hairline}`,
    fontSize: 13,
  };

  const productText = (p?: ProductOption) =>
    p ? `${p.brandName || '未定品牌'} · ${p.productName || p.mpnDisplay}` : '—';

  return (
    <Modal
      title="批量上传"
      open={open}
      width={860}
      onCancel={() => {
        if (running) return;
        const c = changed;
        reset();
        onClose(c);
      }}
      destroyOnHidden
      footer={
        rows.length ? (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              justifyContent: 'flex-end',
            }}
          >
            <span style={{ fontSize: 12, color: palette.mute }}>
              {started
                ? `已完成 ${doneCount} / ${ready.length} 个型号`
                : '上传后不会自动加入队列'}
            </span>
            <Button
              disabled={running}
              onClick={() => {
                const c = changed;
                reset();
                onClose(c);
              }}
            >
              {started ? '关闭' : '取消'}
            </Button>
            {!started && (
              <Button
                type="primary"
                icon={<UploadIcon size="1em" />}
                disabled={!ready.length}
                onClick={run}
              >
                上传 {ready.length} 个型号
              </Button>
            )}
          </div>
        ) : null
      }
    >
      <input
        ref={input}
        type="file"
        multiple
        hidden
        // 选择文件夹：浏览器把子文件夹里的文件一起给出，带相对路径
        {...({ webkitdirectory: '', directory: '' } as object)}
        onChange={(e) => {
          pick(e.target.files);
          e.target.value = '';
        }}
      />
      {!rows.length ? (
        <div
          onClick={() => !matching && input.current?.click()}
          style={{
            border: `1px dashed ${palette.hairline}`,
            borderRadius: 12,
            padding: '40px 16px',
            textAlign: 'center',
            cursor: 'pointer',
            background: palette.inset,
          }}
        >
          <FolderOpen
            size="1em"
            style={{ fontSize: 32, color: palette.link }}
          />
          <div style={{ marginTop: 12 }}>
            {matching ? '正在匹配型号…' : '选择一个文件夹'}
          </div>
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 6 }}>
            每个子文件夹放一个型号的图片，文件夹名写型号；型号有重名时写成「品牌_型号」
          </div>
        </div>
      ) : (
        <div>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '12px 14px',
              borderRadius: 10,
              background: palette.inset,
            }}
          >
            <FolderOpen size="1em" style={{ color: palette.link }} />
            <b>{root}</b>
            <span style={{ fontSize: 12, color: palette.mute }}>
              {rows.length} 个子文件夹 ·{' '}
              {rows.reduce((n, r) => n + r.files.length, 0)} 张图片
            </span>
            <span style={{ flex: 1 }} />
            {!started && (
              <Button type="link" onClick={() => input.current?.click()}>
                重新选择
              </Button>
            )}
          </div>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(4, 1fr)',
              gap: 8,
              marginTop: 12,
            }}
          >
            {counts.map((c) => (
              <div
                key={c.label}
                style={{
                  padding: '10px 14px',
                  borderRadius: 10,
                  background: palette.inset,
                }}
              >
                <div style={{ fontSize: 12, color: palette.sub }}>
                  {c.label}
                </div>
                {/* 0 用正文色，不显示成状态色；数字 600 字重 */}
                <div
                  style={{
                    fontSize: 20,
                    fontWeight: 600,
                    color: c.value ? c.color : palette.ink,
                  }}
                >
                  {c.value}
                </div>
              </div>
            ))}
          </div>

          {matched.length > 0 && (
            <>
              {section(
                <CircleCheck size="1em" style={{ color: palette.green }} />,
                `已匹配 ${matched.length}`,
                '按子文件夹名匹配型号，写成「品牌_型号」时按品牌加型号',
              )}
              <div style={{ ...box, maxHeight: 260, overflowY: 'auto' }}>
                <div
                  style={{
                    ...line,
                    borderTop: 'none',
                    color: palette.mute,
                    fontSize: 12,
                  }}
                >
                  <span>文件夹</span>
                  <span>商品</span>
                  <span style={{ textAlign: 'right' }}>图片</span>
                  <span>任务</span>
                </div>
                {matched.map((r) => (
                  <div key={r.folder} style={line}>
                    <code>{r.folder}</code>
                    <span>{productText(r.products[0])}</span>
                    <span style={{ textAlign: 'right' }}>{r.files.length}</span>
                    <span
                      style={{
                        display: 'inline-flex',
                        gap: 6,
                        alignItems: 'center',
                      }}
                    >
                      {r.state ? (
                        <>
                          {stateIcon(r)}
                          {r.state === 'failed' && !running && (
                            <Button
                              size="small"
                              type="link"
                              onClick={() => runRow(r)}
                            >
                              重试
                            </Button>
                          )}
                        </>
                      ) : (
                        <Pill tone={r.hasTask ? 'accent' : 'green'}>
                          {r.hasTask ? '追加' : '新建'}
                        </Pill>
                      )}
                    </span>
                    {r.error && (
                      <span
                        style={{
                          gridColumn: '1 / -1',
                          fontSize: 12,
                          color: palette.red,
                        }}
                      >
                        {r.error}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </>
          )}

          {multiple.length > 0 && (
            <>
              {section(
                <CircleAlert size="1em" style={{ color: palette.orange }} />,
                `有多个商品 ${multiple.length}`,
                '选定品牌后才能上传',
              )}
              <div style={box}>
                {multiple.map((r, i) => (
                  <div
                    key={r.folder}
                    style={{
                      ...line,
                      gridTemplateColumns: '200px 48px minmax(0, 1fr) 24px',
                      borderTop: i ? line.borderTop : 'none',
                    }}
                  >
                    <code
                      title={r.folder}
                      style={{
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {r.folder}
                    </code>
                    <span>{r.files.length} 张</span>
                    <Select
                      allowClear
                      disabled={started}
                      value={r.productId}
                      placeholder="选择品牌"
                      status={r.productId ? undefined : 'warning'}
                      onChange={(v) =>
                        setRows((rs) =>
                          rs.map((x) =>
                            x.folder === r.folder ? { ...x, productId: v } : x,
                          ),
                        )
                      }
                      options={r.products.map((p) => ({
                        value: p.id,
                        label: productText(p),
                      }))}
                      aria-label={`${r.folder} 的品牌`}
                    />
                    <span>{stateIcon(r)}</span>
                  </div>
                ))}
              </div>
            </>
          )}

          {none.length > 0 && (
            <>
              {section(
                <CircleX size="1em" style={{ color: palette.red }} />,
                `未匹配 ${none.length}`,
                '不上传；先在商品档案里建好商品再传',
                <Button
                  type="link"
                  size="small"
                  icon={<Download size="1em" />}
                  onClick={downloadNone}
                >
                  下载清单
                </Button>,
              )}
              <div
                style={{
                  padding: '10px 14px',
                  borderRadius: 10,
                  background: palette.inset,
                  display: 'flex',
                  gap: 16,
                  flexWrap: 'wrap',
                }}
              >
                {none.map((r) => (
                  <code key={r.folder}>{r.folder}</code>
                ))}
              </div>
            </>
          )}

          {skipped.length > 0 && (
            <div style={{ marginTop: 12, fontSize: 12, color: palette.mute }}>
              跳过 {skipped.length} 个不支持的文件：
              {skipped.slice(0, 5).join('、')}
              {skipped.length > 5 ? ' …' : ''}（HEIC 请先转成 JPG）
            </div>
          )}
        </div>
      )}
    </Modal>
  );
};
