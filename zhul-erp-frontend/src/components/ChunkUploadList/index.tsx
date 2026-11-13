/**
 * 分片直传的文件列表：选中即上传，显示进度；失败可「继续上传」，上传中可取消；
 * 选文件时超过单个上限、类型不支持、合计超限的直接拦下，不开始上传（spec system/file-attachment）。
 */
import {
  CloudOutlined,
  ExclamationCircleOutlined,
  FileExcelOutlined,
  FileImageOutlined,
  FilePdfOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { Button, Progress } from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import {
  ChunkUpload,
  type DirectUploadedFile,
  formatMb,
  type UploadApi,
  UploadError,
  type UploadErrorKind,
} from '@/utils/chunkUpload';

export interface UploadRules {
  /** 后端登记的上传场景 */
  scene: string;
  /** 允许的扩展名（小写，不带点） */
  exts: string[];
  /** 类型不符时的提示，如「只支持图片、PDF、Excel」 */
  typeHint: string;
  maxBytes: number;
  /** 单据上所有附件合计的上限；不限时不填 */
  totalBytes?: number;
}

export type UploadStatus = 'blocked' | 'uploading' | 'failed' | 'done';

export interface UploadItem {
  key: string;
  name: string;
  size: number;
  status: UploadStatus;
  loaded: number;
  /** blocked、failed 时的原因 */
  message?: string;
  errorKind?: UploadErrorKind;
  file?: DirectUploadedFile;
}

/** 扩展名小写；jpeg 视为 jpg，与后端一致（WhatsApp 下载的图片是 .jpeg） */
const extOf = (name: string) => {
  const ext = name.split('.').pop()?.toLowerCase() ?? '';
  return ext === 'jpeg' ? 'jpg' : ext;
};

/** 选文件时的检查；返回拦下的原因，可以上传时返回 undefined */
export const checkFile = (
  file: { name: string; size: number },
  rules: UploadRules,
  usedBytes: number,
): string | undefined => {
  if (!rules.exts.includes(extOf(file.name))) return rules.typeHint;
  if (file.size > rules.maxBytes)
    return `超过 ${formatMb(rules.maxBytes)}，可拆分后再传`;
  if (rules.totalBytes && usedBytes + file.size > rules.totalBytes)
    return `附件合计会超过 ${formatMb(rules.totalBytes)}（已有 ${formatMb(usedBytes)}）`;
  return undefined;
};

let seq = 0;

/**
 * 管理一组分片直传。otherBytes 为单据上不在这个列表里的附件大小（如修改时保留的旧附件、从商机带入的），
 * 用于合计上限。上传中关闭或刷新页面时浏览器会先确认。
 */
export const useChunkUploads = (
  rules: UploadRules,
  otherBytes = 0,
  api?: UploadApi,
) => {
  const [items, setItems] = useState<UploadItem[]>([]);
  const uploads = useRef(new Map<string, ChunkUpload>());
  const itemsRef = useRef(items);
  itemsRef.current = items;

  const patch = useCallback((key: string, p: Partial<UploadItem>) => {
    setItems((prev) => prev.map((i) => (i.key === key ? { ...i, ...p } : i)));
  }, []);

  const run = useCallback(
    (key: string) => {
      const up = uploads.current.get(key);
      if (!up) return;
      patch(key, {
        status: 'uploading',
        message: undefined,
        errorKind: undefined,
      });
      up.start()
        .then((file) =>
          patch(key, { status: 'done', loaded: up.file.size, file }),
        )
        .catch((e: unknown) => {
          const err =
            e instanceof UploadError
              ? e
              : new UploadError('network', '上传失败');
          if (err.kind === 'cancelled') return;
          if (err.kind === 'rejected') uploads.current.delete(key);
          patch(key, {
            status: 'failed',
            message: err.message,
            errorKind: err.kind,
            loaded: up.loaded,
          });
        });
    },
    [patch],
  );

  const add = useCallback(
    (files: File[]) => {
      let used =
        otherBytes +
        itemsRef.current
          .filter((i) => i.status !== 'blocked' && i.errorKind !== 'rejected')
          .reduce((s, i) => s + i.size, 0);
      const added: UploadItem[] = [];
      const toRun: string[] = [];
      for (const f of files) {
        const key = `u${++seq}`;
        const blocked = checkFile(f, rules, used);
        added.push({
          key,
          name: f.name,
          size: f.size,
          loaded: 0,
          status: blocked ? 'blocked' : 'uploading',
          message: blocked,
        });
        if (blocked) continue;
        used += f.size;
        uploads.current.set(
          key,
          new ChunkUpload(f, rules.scene, {
            existingBytes: used - f.size,
            api,
            onProgress: (loaded) => patch(key, { loaded }),
          }),
        );
        toRun.push(key);
      }
      setItems((prev) => [...prev, ...added]);
      for (const key of toRun) run(key);
    },
    [rules, otherBytes, api, patch, run],
  );

  /** 取消上传中的、移除失败或拦下的、删除已传完的 */
  const remove = useCallback((key: string) => {
    const up = uploads.current.get(key);
    const item = itemsRef.current.find((i) => i.key === key);
    if (up && item?.status !== 'done') up.cancel().catch(() => undefined);
    uploads.current.delete(key);
    setItems((prev) => prev.filter((i) => i.key !== key));
  }, []);

  const reset = useCallback(() => {
    for (const [key, up] of uploads.current) {
      const item = itemsRef.current.find((i) => i.key === key);
      if (item?.status !== 'done') up.cancel().catch(() => undefined);
    }
    uploads.current.clear();
    setItems([]);
  }, []);

  const busy = items.filter(
    (i) =>
      i.status === 'uploading' ||
      (i.status === 'failed' && i.errorKind !== 'rejected'),
  ).length;

  useEffect(() => {
    if (!items.some((i) => i.status === 'uploading')) return undefined;
    const warn = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [items]);

  return {
    items,
    add,
    /** 继续上传：只传还没传完的分片 */
    resume: run,
    remove,
    reset,
    /** 还没传完的（上传中或中断待继续）个数，提交前须为 0 */
    busy,
    /** 已传完的文件 */
    done: items
      .filter((i) => i.status === 'done' && i.file)
      .map((i) => i.file as DirectUploadedFile),
  };
};

const icon = (name: string) =>
  /\.(xlsx?|csv)$/i.test(name) ? (
    <FileExcelOutlined />
  ) : /\.pdf$/i.test(name) ? (
    <FilePdfOutlined />
  ) : (
    <FileImageOutlined />
  );

/** 文件行：上传中显示进度与「取消」，失败显示原因与「继续上传 / 移除」，拦下的只能「知道了」 */
export const ChunkUploadList: React.FC<{
  items: UploadItem[];
  onResume: (key: string) => void;
  onRemove: (key: string) => void;
  /** 传完后的文件行右侧额外的操作（如预览） */
  doneExtra?: (item: UploadItem) => React.ReactNode;
}> = ({ items, onResume, onRemove, doneExtra }) => {
  const { palette } = useAppTheme();
  if (items.length === 0) return null;
  return (
    <div style={{ display: 'grid', gap: 8, marginTop: 12 }}>
      {items.map((i) => {
        const failed = i.status === 'failed' || i.status === 'blocked';
        const warn =
          i.errorKind === 'network' || i.message?.startsWith('附件合计');
        const tone = !failed
          ? palette.link
          : warn
            ? palette.orange
            : palette.red;
        const percent = i.size ? Math.floor((i.loaded / i.size) * 100) : 0;
        let detail: React.ReactNode = formatMb(i.size);
        if (i.status === 'uploading')
          detail = `${formatMb(i.loaded)} / ${formatMb(i.size)} · ${percent}%`;
        if (failed) detail = <span style={{ color: tone }}>{i.message}</span>;
        let actions: React.ReactNode;
        if (i.status === 'uploading') {
          actions = (
            <Button type="link" size="small" onClick={() => onRemove(i.key)}>
              取消
            </Button>
          );
        } else if (i.status === 'blocked') {
          actions = (
            <Button type="text" size="small" onClick={() => onRemove(i.key)}>
              知道了
            </Button>
          );
        } else if (i.status === 'failed') {
          actions = (
            <>
              {i.errorKind !== 'rejected' && (
                <Button
                  type="link"
                  size="small"
                  onClick={() => onResume(i.key)}
                >
                  {i.errorKind === 'cors' ? '重试' : '继续上传'}
                </Button>
              )}
              <Button type="text" size="small" onClick={() => onRemove(i.key)}>
                移除
              </Button>
            </>
          );
        } else {
          actions = (
            <>
              {doneExtra?.(i)}
              <Button type="text" size="small" onClick={() => onRemove(i.key)}>
                删除
              </Button>
            </>
          );
        }
        let lead = icon(i.name);
        if (i.errorKind === 'cors') lead = <CloudOutlined />;
        else if (i.status === 'blocked') lead = <StopOutlined />;
        else if (i.errorKind === 'rejected')
          lead = <ExclamationCircleOutlined />;
        return (
          <div
            key={i.key}
            data-testid={`upload-${i.status}`}
            style={{
              padding: '10px 12px',
              borderRadius: 10,
              border: `1px solid ${failed ? 'transparent' : palette.hairline}`,
              background: failed
                ? warn
                  ? palette.orangeSoft
                  : palette.redSoft
                : palette.inset,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <span style={{ color: tone, fontSize: 18 }}>{lead}</span>
              <div style={{ minWidth: 0, flex: 1 }}>
                <div
                  style={{
                    color: palette.ink,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {i.errorKind === 'cors' ? '上传服务暂时不可用' : i.name}
                </div>
                <div style={{ color: palette.mute, fontSize: 12 }}>
                  {i.errorKind === 'cors' ? `${i.name} · 请联系管理员` : detail}
                </div>
              </div>
              {actions}
            </div>
            {i.status === 'uploading' && (
              <Progress
                percent={percent}
                showInfo={false}
                size="small"
                strokeColor={{ from: '#3B82F6', to: '#22D3EE' }}
                style={{ margin: '6px 0 0' }}
              />
            )}
          </div>
        );
      })}
    </div>
  );
};

export default ChunkUploadList;
