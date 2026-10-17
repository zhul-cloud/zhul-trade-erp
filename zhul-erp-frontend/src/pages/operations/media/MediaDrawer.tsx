import {
  CloudUploadOutlined,
  DeleteOutlined,
  DownloadOutlined,
  FileTextOutlined,
  MinusOutlined,
  PlayCircleOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import {
  App,
  Button,
  Checkbox,
  Drawer,
  Input,
  Modal,
  Skeleton,
  Space,
  Switch,
  Tooltip,
  Upload,
} from 'antd';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { usePermission } from '@/utils/permission';
import {
  downloadAll,
  type FileLink,
  itemKey,
  type MediaDetail,
  type MediaItem,
  type MediaRef,
  type MediaReference,
  mediaApi,
  PERM_EDIT,
  PERM_UPLOAD,
  readBizError,
  refOf,
} from './service';

const CHUNK = 50;
const MAX_DOWNLOAD = 50;

/** 签发链接的方式：默认走素材库接口，发品任务改走任务自己的接口 */
export type FetchUrls = (
  refs: MediaRef[],
  download: boolean,
) => Promise<FileLink[]>;

/** 取预览链接：本公司素材分批签发，官方素材用公开地址 */
export const loadPreviews = async (
  items: MediaItem[],
  brand: string,
  model: string,
  fetchUrls: FetchUrls = mediaApi.urls,
) => {
  const out: Record<string, string> = {};
  items
    .filter((i) => i.publicUrl)
    .forEach((i) => {
      out[itemKey(i)] = i.publicUrl as string;
    });
  const own = items.filter((i) => !i.publicUrl && i.kind === 1);
  for (let k = 0; k < own.length; k += CHUNK) {
    const part = own.slice(k, k + CHUNK);
    const links = await fetchUrls(
      part.map((i) => refOf(i, brand, model)),
      false,
    );
    part.forEach((i, n) => {
      out[itemKey(i)] = links[n]?.url;
    });
  }
  return out;
};

/** 一组素材的网格：缩略图、标记、选择 */
export const MediaGrid: React.FC<{
  items: MediaItem[];
  previews: Record<string, string>;
  selected?: Set<string>;
  onToggle?: (i: MediaItem) => void;
  onOpen: (i: MediaItem) => void;
  onFlag?: (
    i: MediaItem,
    change: { canSend?: boolean; forListing?: boolean },
  ) => void;
  onDelete?: (i: MediaItem) => void;
}> = ({ items, previews, selected, onToggle, onOpen, onFlag, onDelete }) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
        gap: 12,
      }}
    >
      {items.map((i) => {
        const key = itemKey(i);
        const thumb = i.kind === 1 ? previews[key] : undefined;
        return (
          <div
            key={key}
            style={{
              border: `1px solid ${palette.hairline}`,
              borderRadius: 12,
              overflow: 'hidden',
              background: palette.card,
              display: 'flex',
              flexDirection: 'column',
            }}
          >
            <button
              type="button"
              onClick={() => onOpen(i)}
              title={i.fileName}
              aria-label={`预览 ${i.fileName ?? i.categoryName}`}
              style={{
                position: 'relative',
                height: 110,
                border: 'none',
                padding: 0,
                cursor: 'pointer',
                background: thumb
                  ? `center / cover no-repeat url("${thumb}")`
                  : palette.inset,
                color: palette.sub,
                display: 'grid',
                placeItems: 'center',
              }}
            >
              {!thumb &&
                (i.kind === 2 ? (
                  <PlayCircleOutlined style={{ fontSize: 28 }} />
                ) : (
                  <FileTextOutlined style={{ fontSize: 26 }} />
                ))}
              {onToggle && i.canSend && (
                <span
                  role="presentation"
                  onClick={(e) => e.stopPropagation()}
                  style={{ position: 'absolute', top: 6, left: 8 }}
                >
                  <Checkbox
                    checked={selected?.has(key)}
                    onChange={() => onToggle(i)}
                    aria-label={`选中 ${i.fileName ?? ''}`}
                  />
                </span>
              )}
              {i.kind === 2 &&
                (i.processingStatus === 1 || i.processingStatus === 3) && (
                  <span
                    style={{
                      position: 'absolute',
                      left: 6,
                      bottom: 6,
                      padding: '0 4px',
                      borderRadius: 4,
                      fontSize: 10,
                      lineHeight: '16px',
                      color: '#fff',
                      background:
                        i.processingStatus === 1
                          ? palette.link
                          : palette.orange,
                    }}
                  >
                    {i.processingStatus === 1 ? '压缩中' : '未压缩'}
                  </span>
                )}
            </button>
            <div
              style={{
                padding: '8px 10px',
                display: 'grid',
                gap: 4,
                fontSize: 12,
              }}
            >
              <div
                style={{
                  display: 'flex',
                  gap: 6,
                  alignItems: 'center',
                  flexWrap: 'wrap',
                }}
              >
                <span style={{ color: palette.ink, fontWeight: 600 }}>
                  {i.categoryName}
                </span>
                {!i.canSend && <Pill tone="mute">仅内部</Pill>}
                {i.forListing && <Pill tone="green">用于发品</Pill>}
              </div>
              <div
                style={{
                  color: palette.mute,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
                title={i.note || i.fileName}
              >
                {i.sourceNo ? `${i.sourceNo} · ` : ''}
                {i.uploaderName ?? ''}
                {i.uploadedAt ? ` · ${i.uploadedAt.slice(0, 10)}` : ''}
                {!i.sourceNo && !i.uploaderName && (i.note || i.fileName)}
              </div>
              {onFlag && (
                <div
                  style={{
                    display: 'flex',
                    gap: 10,
                    alignItems: 'center',
                    flexWrap: 'wrap',
                  }}
                >
                  <Tooltip title="可以发给客户（可下载）">
                    <Space size={4}>
                      <Switch
                        size="small"
                        checked={i.canSend}
                        onChange={(v) => onFlag(i, { canSend: v })}
                        aria-label="可发客户"
                      />
                      <span>可发客户</span>
                    </Space>
                  </Tooltip>
                  {i.listingAllowed && i.kind !== 3 && (
                    <Space size={4}>
                      <Switch
                        size="small"
                        checked={i.forListing}
                        disabled={!i.canSend}
                        onChange={(v) => onFlag(i, { forListing: v })}
                        aria-label="用于发品"
                      />
                      <span>用于发品</span>
                    </Space>
                  )}
                </div>
              )}
              {onDelete && i.category === 5 && (
                <a style={{ color: palette.red }} onClick={() => onDelete(i)}>
                  <DeleteOutlined /> 删除
                </a>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
};

/** 预览弹窗：图片、视频直接用链接，文件新窗口打开 */
export const usePreviewer = (
  brand: string,
  model: string,
  fetchUrls: FetchUrls = mediaApi.urls,
) => {
  const { message } = App.useApp();
  const [state, setState] = useState<{
    url: string;
    name: string;
    video: boolean;
  }>();
  const open = async (i: MediaItem, previews: Record<string, string>) => {
    try {
      const url =
        i.publicUrl ??
        previews[itemKey(i)] ??
        (await fetchUrls([refOf(i, brand, model)], false))[0]?.url;
      if (!url) return;
      if (i.kind === 3) {
        window.open(url, '_blank', 'noopener');
        return;
      }
      setState({
        url,
        name: i.fileName ?? i.categoryName,
        video: i.kind === 2,
      });
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };
  const node = (
    <Modal
      open={!!state}
      title={state?.name}
      footer={null}
      width={state?.video ? 860 : 720}
      onCancel={() => setState(undefined)}
      destroyOnHidden
    >
      {state?.video ? (
        // biome-ignore lint/a11y/useMediaCaption: 素材视频没有字幕
        <video
          src={state.url}
          controls
          autoPlay
          style={{ width: '100%', maxHeight: '70vh', borderRadius: 8 }}
        />
      ) : (
        state && (
          <img
            src={state.url}
            alt={state.name}
            style={{ width: '100%', maxHeight: '70vh', objectFit: 'contain' }}
          />
        )
      )}
    </Modal>
  );
  return { open, node };
};

/** 参考素材：同系列 → 同品牌同品类 → 同品牌 → 同品类其他品牌，按热度排序，展开查看与下载素材包 */
const ReferencePanel: React.FC<{ brand: string; model: string }> = ({
  brand,
  model,
}) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [data, setData] = useState<MediaReference>();
  const [loading, setLoading] = useState(false);
  const [expanded, setExpanded] = useState<string>();
  const [items, setItems] = useState<
    Record<string, { list: MediaItem[]; previews: Record<string, string> }>
  >({});
  const preview = usePreviewer(brand, model);

  useEffect(() => {
    setLoading(true);
    mediaApi
      .references(brand, model)
      .then(setData)
      .catch((e) => message.error(readBizError(e).message))
      .finally(() => setLoading(false));
  }, [brand, model, message]);

  const expand = async (b: string, m: string) => {
    const key = `${b}|${m}`;
    if (expanded === key) {
      setExpanded(undefined);
      return;
    }
    setExpanded(key);
    if (items[key]) return;
    try {
      const d = await mediaApi.detail(b, m);
      const list = d.groups
        .flatMap((g) => g.items)
        .filter((i) => i.canSend && i.kind !== 3);
      const previews = await loadPreviews(list, b, m);
      setItems((prev) => ({ ...prev, [key]: { list, previews } }));
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const downloadPack = async (b: string, m: string) => {
    try {
      const d = await mediaApi.detail(b, m);
      const list = d.groups
        .flatMap((g) => g.items)
        .filter((i) => i.canSend && i.kind !== 3)
        .slice(0, MAX_DOWNLOAD);
      if (list.length === 0) return;
      await downloadAll(
        await mediaApi.urls(
          list.map((i) => refOf(i, b, m)),
          true,
        ),
      );
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  if (loading) return <Skeleton active paragraph={{ rows: 3 }} />;
  if (!data || data.level === 0) {
    return (
      <div style={{ color: palette.mute, fontSize: 13 }}>
        同系列、同品牌、同品类都没有找到有素材的型号。
      </div>
    );
  }
  return (
    <div style={{ display: 'grid', gap: 8 }}>
      <div style={{ fontSize: 13, color: palette.sub }}>
        按{data.levelName}找到 {data.models.length}{' '}
        个有素材的型号，按询盘热度排序
        {data.warning && (
          <span style={{ color: palette.orange }}>；{data.warning}</span>
        )}
      </div>
      {data.models.map((r) => {
        const key = `${r.brand}|${r.model}`;
        const open = expanded === key;
        return (
          <div
            key={key}
            style={{
              border: `1px solid ${palette.hairline}`,
              borderRadius: 12,
              padding: '8px 12px',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <Button
                size="small"
                type="text"
                icon={open ? <MinusOutlined /> : <PlusOutlined />}
                aria-label={open ? '收起' : '展开'}
                onClick={() => expand(r.brand, r.model)}
              />
              <span style={{ fontWeight: 600, color: palette.ink }}>
                {r.brand} {r.model}
              </span>
              <span
                style={{
                  color: palette.mute,
                  fontSize: 12,
                  flex: 1,
                  minWidth: 0,
                }}
              >
                {r.productName} · 询盘 {r.inquiryCount} 次 · 成交 {r.dealCount}{' '}
                次
              </span>
              <Button
                size="small"
                icon={<DownloadOutlined />}
                onClick={() => downloadPack(r.brand, r.model)}
              >
                下载素材包
              </Button>
            </div>
            {open && (
              <div style={{ marginTop: 10 }}>
                {items[key] ? (
                  <MediaGrid
                    items={items[key].list}
                    previews={items[key].previews}
                    onOpen={(i) => preview.open(i, items[key].previews)}
                  />
                ) : (
                  <Skeleton active paragraph={{ rows: 2 }} />
                )}
              </div>
            )}
          </div>
        );
      })}
      {preview.node}
    </div>
  );
};

/** 一个型号的素材：按分组（官方资料在前，其余按业务流程）展示 */
export const MediaDrawer: React.FC<{
  brand?: string;
  model?: string;
  open: boolean;
  onClose: () => void;
  /** 标记、上传、删除后通知外层刷新 */
  onChanged?: () => void;
}> = ({ brand, model, open, onClose, onChanged }) => {
  const { palette } = useAppTheme();
  const { message, modal } = App.useApp();
  const { hasPerm } = usePermission();
  const canEdit = hasPerm(PERM_EDIT);
  const canUpload = hasPerm(PERM_UPLOAD);
  const [detail, setDetail] = useState<MediaDetail>();
  const [previews, setPreviews] = useState<Record<string, string>>({});
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [showRefs, setShowRefs] = useState(false);
  const [note, setNote] = useState('');
  const [uploading, setUploading] = useState(0);
  const preview = usePreviewer(brand ?? '', model ?? '');

  const load = useCallback(async () => {
    if (!brand || !model) return;
    setLoading(true);
    setError(undefined);
    try {
      const d = await mediaApi.detail(brand, model);
      setDetail(d);
      setPreviews(
        await loadPreviews(
          d.groups.flatMap((g) => g.items),
          brand,
          model,
        ),
      );
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [brand, model]);

  useEffect(() => {
    if (open) {
      setSelected(new Set());
      setShowRefs(false);
      load();
    }
  }, [open, load]);

  const all = useMemo(
    () => detail?.groups.flatMap((g) => g.items) ?? [],
    [detail],
  );

  const flag = async (
    i: MediaItem,
    change: { canSend?: boolean; forListing?: boolean },
  ) => {
    if (!brand || !model) return;
    try {
      await mediaApi.flag(refOf(i, brand, model), change);
      await load();
      onChanged?.();
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const remove = (i: MediaItem) =>
    modal.confirm({
      title: '删除这张网络图片？',
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await mediaApi.deleteNetwork(i.itemId as number);
          await load();
          onChanged?.();
        } catch (e) {
          message.error(readBizError(e).message);
        }
      },
    });

  const downloadSelected = async () => {
    if (!brand || !model) return;
    const list = all.filter((i) => selected.has(itemKey(i)));
    try {
      await downloadAll(
        await mediaApi.urls(
          list.map((i) => refOf(i, brand, model)),
          true,
        ),
      );
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const toggle = (i: MediaItem) => {
    const key = itemKey(i);
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else if (next.size < MAX_DOWNLOAD) next.add(key);
      else message.warning(`一次最多下载 ${MAX_DOWNLOAD} 个文件`);
      return next;
    });
  };

  return (
    <Drawer
      open={open}
      onClose={onClose}
      size="min(880px, 96vw)"
      destroyOnHidden
      title={
        <div>
          <div style={{ fontSize: 16 }}>
            {brand} {model}
          </div>
          {detail?.productName && (
            <div style={{ fontSize: 12, color: palette.mute, fontWeight: 400 }}>
              {detail.productName}
            </div>
          )}
        </div>
      }
      extra={
        <Space>
          {canUpload && (
            <Upload
              accept="image/jpeg,image/png,image/webp"
              multiple
              showUploadList={false}
              beforeUpload={(file) => {
                if (!brand || !model) return Upload.LIST_IGNORE;
                setUploading((n) => n + 1);
                mediaApi
                  .uploadNetwork(brand, model, file, note.trim() || undefined)
                  .then(() => load().then(() => onChanged?.()))
                  .catch((e) =>
                    message.error(`${file.name}：${readBizError(e).message}`),
                  )
                  .finally(() => setUploading((n) => n - 1));
                return Upload.LIST_IGNORE;
              }}
            >
              <Button icon={<CloudUploadOutlined />} loading={uploading > 0}>
                上传网络图片
              </Button>
            </Upload>
          )}
          <Button
            type="primary"
            icon={<DownloadOutlined />}
            disabled={selected.size === 0}
            onClick={downloadSelected}
          >
            下载选中{selected.size > 0 ? ` (${selected.size})` : ''}
          </Button>
        </Space>
      }
    >
      {canUpload && (
        <Input
          size="small"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          maxLength={500}
          placeholder="网络图片的来源说明（选填，如网址），上传前填写"
          style={{ marginBottom: 16 }}
          aria-label="网络图片来源说明"
        />
      )}
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : loading && !detail ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : (
        detail && (
          <div style={{ display: 'grid', gap: 20 }}>
            {!detail.hasSendable && (
              <div
                style={{
                  border: `1px dashed ${palette.hairline}`,
                  borderRadius: 12,
                  padding: 14,
                  background: palette.inset,
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    marginBottom: showRefs ? 12 : 0,
                  }}
                >
                  <span style={{ color: palette.ink, fontWeight: 600 }}>
                    这个型号还没有可发给客户的图片或视频
                  </span>
                  <span style={{ flex: 1 }} />
                  <Button size="small" onClick={() => setShowRefs((v) => !v)}>
                    {showRefs ? '收起参考素材' : '参考素材'}
                  </Button>
                </div>
                {showRefs && brand && model && (
                  <ReferencePanel brand={brand} model={model} />
                )}
              </div>
            )}
            {detail.groups.map((g) => (
              <section key={g.name}>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'baseline',
                    gap: 8,
                    marginBottom: g.items.length ? 10 : 0,
                  }}
                >
                  <h3 style={{ margin: 0, fontSize: 14, color: palette.ink }}>
                    {g.name}
                  </h3>
                  <span style={{ fontSize: 12, color: palette.mute }}>
                    {g.items.length ? g.items.length : '暂无'}
                  </span>
                </div>
                {g.items.length > 0 && (
                  <MediaGrid
                    items={g.items}
                    previews={previews}
                    selected={selected}
                    onToggle={toggle}
                    onOpen={(i) => preview.open(i, previews)}
                    onFlag={canEdit ? flag : undefined}
                    onDelete={canUpload || canEdit ? remove : undefined}
                  />
                )}
              </section>
            ))}
          </div>
        )
      )}
      {preview.node}
    </Drawer>
  );
};

export default MediaDrawer;
