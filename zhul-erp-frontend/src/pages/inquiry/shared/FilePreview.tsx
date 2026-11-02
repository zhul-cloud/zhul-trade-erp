import {
  DownloadOutlined,
  FileExcelOutlined,
  FilePdfOutlined,
  LeftOutlined,
  PictureOutlined,
  RightOutlined,
} from '@ant-design/icons';
import { App, Button, Modal, Spin } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { downloadFile, fileUrl } from '@/utils/fileLink';
import { fileKind, imagePosition, type PreviewFile, stepImage } from './files';

/** 签名链接 10 分钟有效，缓存 8 分钟，避免缩略图和预览重复取 */
const cache = new Map<string, { url: string; at: number }>();
const TTL = 8 * 60 * 1000;
const signed = async (endpoint: string) => {
  const hit = cache.get(endpoint);
  if (hit && Date.now() - hit.at < TTL) return hit.url;
  const url = await fileUrl(endpoint);
  cache.set(endpoint, { url, at: Date.now() });
  return url;
};

const useSigned = (endpoint?: string) => {
  const [url, setUrl] = useState<string>();
  useEffect(() => {
    if (!endpoint) return undefined;
    let alive = true;
    setUrl(undefined);
    signed(endpoint)
      .then((u) => alive && setUrl(u))
      .catch(() => alive && setUrl(undefined));
    return () => {
      alive = false;
    };
  }, [endpoint]);
  return url;
};

/**
 * 一个附件缩略图：图片显示缩略图，PDF、表格显示图标；下面可带文件名。点击交给 onOpen（预览或下载）。
 * endpoint 为附件接口地址（不含 /url）。
 */
export const FileTile: React.FC<{
  file: PreviewFile;
  endpoint: string;
  width?: number;
  height?: number;
  showName?: boolean;
  active?: boolean;
  onOpen: () => void;
}> = ({
  file,
  endpoint,
  width = 96,
  height = 72,
  showName = true,
  active,
  onOpen,
}) => {
  const { palette } = useAppTheme();
  const kind = fileKind(file);
  const thumb = useSigned(kind === 'image' ? endpoint : undefined);
  const icon =
    kind === 'pdf' ? (
      <FilePdfOutlined style={{ fontSize: 24, color: palette.red }} />
    ) : kind === 'sheet' ? (
      <FileExcelOutlined style={{ fontSize: 24, color: palette.green }} />
    ) : (
      <PictureOutlined style={{ fontSize: 20, color: palette.mute }} />
    );
  return (
    <div style={{ width, display: 'grid', gap: 6 }}>
      <button
        type="button"
        onClick={onOpen}
        title={file.fileName}
        aria-label={`${kind === 'sheet' ? '下载' : '预览'} ${file.fileName}`}
        style={{
          width,
          height,
          padding: 0,
          borderRadius: width < 60 ? 6 : 10,
          border: `${active ? 2 : 1}px solid ${active ? palette.link : palette.hairline}`,
          background: thumb
            ? `center / cover no-repeat url("${thumb}")`
            : palette.inset,
          cursor: 'pointer',
          display: 'grid',
          placeItems: 'center',
          overflow: 'hidden',
        }}
      >
        {!thumb && icon}
      </button>
      {showName && (
        <span
          style={{
            fontSize: 12,
            color: active ? palette.link : palette.sub,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {file.fileName}
        </span>
      )}
    </div>
  );
};

interface PreviewState {
  files: PreviewFile[];
  index: number;
  endpointOf: (f: PreviewFile) => string;
  /** 标题下的说明，如「铭牌图片 · 2 · 6AV2123-2GB03-0AX0」 */
  captionOf?: (f: PreviewFile) => string;
}

/**
 * 附件预览：图片在当前页弹窗里看，可以左右切换同组的其他图片（键盘左右键也可以）；
 * PDF 在弹窗里直接显示；表格点开直接下载。
 */
export const useFilePreview = () => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [state, setState] = useState<PreviewState>();

  const open = useCallback(
    (
      files: PreviewFile[],
      index: number,
      endpointOf: (f: PreviewFile) => string,
      captionOf?: (f: PreviewFile) => string,
    ) => {
      const f = files[index];
      if (fileKind(f) === 'sheet') {
        downloadFile(endpointOf(f)).catch(() =>
          message.error('下载失败，请稍后再试'),
        );
        return;
      }
      setState({ files, index, endpointOf, captionOf });
    },
    [message],
  );

  const current = state ? state.files[state.index] : undefined;
  const kind = current ? fileKind(current) : undefined;
  const url = useSigned(current && state?.endpointOf(current));
  const go = useCallback(
    (step: 1 | -1) =>
      setState((s) =>
        s ? { ...s, index: stepImage(s.files, s.index, step) } : s,
      ),
    [],
  );

  useEffect(() => {
    if (kind !== 'image') return undefined;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft') go(-1);
      if (e.key === 'ArrowRight') go(1);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [kind, go]);

  let node: React.ReactNode = null;
  if (state && current) {
    const images = state.files.filter((f) => fileKind(f) === 'image');
    const pos = imagePosition(state.files, state.index);
    const caption = [
      state.captionOf?.(current),
      kind === 'image' && pos.total > 1
        ? `第 ${pos.n} / 共 ${pos.total} 张`
        : '',
    ]
      .filter(Boolean)
      .join(' · ');
    const arrow = (step: 1 | -1) => {
      const disabled =
        stepImage(state.files, state.index, step) === state.index;
      return (
        <Button
          shape="circle"
          icon={step < 0 ? <LeftOutlined /> : <RightOutlined />}
          aria-label={step < 0 ? '上一张' : '下一张'}
          disabled={disabled}
          onClick={() => go(step)}
        />
      );
    };
    node = (
      <Modal
        open
        width={kind === 'pdf' ? 960 : 900}
        title={
          <div style={{ display: 'grid', gap: 2 }}>
            <span>{current.fileName}</span>
            {caption && (
              <span
                style={{ fontSize: 12, fontWeight: 400, color: palette.mute }}
              >
                {caption}
              </span>
            )}
          </div>
        }
        onCancel={() => setState(undefined)}
        footer={
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            <span style={{ fontSize: 12, color: palette.mute }}>
              {kind === 'image' && images.length > 1 ? '← → 键切换 · ' : ''}
              Esc 关闭
            </span>
            <Button
              icon={<DownloadOutlined />}
              onClick={() =>
                downloadFile(state.endpointOf(current)).catch(() =>
                  message.error('下载失败，请稍后再试'),
                )
              }
            >
              下载
            </Button>
          </div>
        }
        destroyOnHidden
      >
        {kind === 'pdf' ? (
          url ? (
            <iframe
              src={url}
              title={current.fileName}
              style={{
                width: '100%',
                height: '72vh',
                border: 0,
                borderRadius: 8,
              }}
            />
          ) : (
            <div style={{ padding: 80, textAlign: 'center' }}>
              <Spin />
            </div>
          )
        ) : (
          <div style={{ display: 'grid', gap: 14 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              {images.length > 1 && arrow(-1)}
              <div
                style={{
                  flex: 1,
                  height: '62vh',
                  borderRadius: 12,
                  background: palette.inset,
                  display: 'grid',
                  placeItems: 'center',
                  overflow: 'hidden',
                }}
              >
                {url ? (
                  <img
                    src={url}
                    alt={current.fileName}
                    style={{
                      maxWidth: '100%',
                      maxHeight: '62vh',
                      objectFit: 'contain',
                    }}
                  />
                ) : (
                  <Spin />
                )}
              </div>
              {images.length > 1 && arrow(1)}
            </div>
            {images.length > 1 && (
              <div
                style={{
                  display: 'flex',
                  gap: 10,
                  justifyContent: 'center',
                  flexWrap: 'wrap',
                }}
              >
                {images.map((f) => (
                  <FileTile
                    key={f.id}
                    file={f}
                    endpoint={state.endpointOf(f)}
                    width={64}
                    height={48}
                    showName={false}
                    active={f.id === current.id}
                    onOpen={() =>
                      setState((s) =>
                        s ? { ...s, index: s.files.indexOf(f) } : s,
                      )
                    }
                  />
                ))}
              </div>
            )}
          </div>
        )}
      </Modal>
    );
  }
  return { open, node };
};
