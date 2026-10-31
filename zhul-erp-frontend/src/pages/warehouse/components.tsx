import {
  CloseOutlined,
  FilePdfOutlined,
  LoadingOutlined,
  PictureOutlined,
  PlayCircleOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { App, Checkbox, Input, Modal, Spin, Upload } from 'antd';
import React, { useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import type { Tone } from '@/pages/inquiry/shared/constants';
import { useAppTheme } from '@/theme/AppTheme';
import {
  type Attachment,
  attachmentApi,
  type OwnerType,
  readBizError,
} from './service';

export { Card, Pill };

export const WAREHOUSE_PATHS = {
  shipments: '/purchase/shipments',
  receipts: '/warehouse/receipts',
  holds: '/warehouse/holds',
  shoots: '/warehouse/shoots',
  order: (id: number) => `/purchase/orders/${id}`,
  salesOrder: (id: number) => `/sales/orders/${id}`,
};

export const WarehousePageTitle: React.FC<
  Omit<React.ComponentProps<typeof PageTitle>, 'root'>
> = (props) => <PageTitle {...props} />;

// ---------------------------------------------------------------- 状态

const tone =
  (meta: Record<number, Tone>) =>
  ({ value, children }: { value: number; children: React.ReactNode }) => (
    <Pill tone={meta[value] ?? 'gray'}>{children}</Pill>
  );

/** 发货单：在途 / 已入库 / 已作废 */
export const ShipStatusPill = tone({ 1: 'accent', 2: 'green', 3: 'mute' });
/** 差异类型：少发 / 不良 / 多发 */
export const DiffTypePill = tone({ 1: 'orange', 2: 'red', 3: 'cyan' });
/** 差异状态：待处理 / 已处理 */
export const DiffStatusPill = tone({ 1: 'orange', 2: 'green' });
/** 采购入库单：有效 / 已冲销 */
export const ReceiptStatusPill = tone({ 1: 'green', 2: 'mute' });
/** 闲置库存：暂存中 / 已退回 / 已报废 / 已转样品 */
export const HoldStatusPill = tone({
  1: 'accent',
  2: 'gray',
  3: 'mute',
  4: 'violet',
});
/** 实拍任务：待拍摄 / 已完成 / 已跳过 */
export const ShootStatusPill = tone({ 1: 'gray', 2: 'green', 3: 'mute' });

export const sub = (color: string, text: React.ReactNode) => (
  <div style={{ fontSize: 12, color }}>{text}</div>
);

/** 预计到货：在途且已过日期时标「超时未到」 */
export const ArrivalCell: React.FC<{ date?: string; overdue?: boolean }> = ({
  date,
  overdue,
}) => {
  const { palette } = useAppTheme();
  if (!date) return <span style={{ color: palette.mute }}>—</span>;
  return (
    <div>
      <span style={{ color: overdue ? palette.orange : undefined }}>
        {date}
      </span>
      {overdue && (
        <div>
          <Pill tone="orange">超时未到</Pill>
        </div>
      )}
    </div>
  );
};

export const trackingText = (carrier?: string, trackingNo?: string) =>
  [carrier, trackingNo].filter(Boolean).join(' ') || '—';

// ---------------------------------------------------------------- 附件

const IMAGE_MAX = 10 * 1024 * 1024;
const VIDEO_MAX = 95 * 1024 * 1024;
const ACCEPT_ALL = 'image/jpeg,image/png,image/webp,video/mp4,video/quicktime';
export const ACCEPT_IMAGE = 'image/jpeg,image/png,image/webp';
export const ACCEPT_VIDEO = 'video/mp4,video/quicktime';

const isVideoFile = (f: File) =>
  f.type.startsWith('video/') || /\.(mp4|mov)$/i.test(f.name);

/** 上传前的大小提示（服务端按文件内容再校验一次） */
export const checkSize = (f: File) =>
  isVideoFile(f)
    ? f.size > VIDEO_MAX
      ? '视频不能超过 95MB'
      : undefined
    : f.size > IMAGE_MAX
      ? '图片不能超过 10MB'
      : undefined;

/** 附件预览：带登录态取文件后在弹窗里看图或播放视频 */
export const usePreview = () => {
  const { message } = App.useApp();
  const [state, setState] = useState<{
    name: string;
    video: boolean;
    /** PDF 等文件（kind 3）在弹窗里内嵌显示 */
    file?: boolean;
    url?: string;
  }>();
  useEffect(
    () => () => {
      if (state?.url) URL.revokeObjectURL(state.url);
    },
    [state?.url],
  );
  const open = async (a: { id: number; fileName: string; kind: number }) => {
    const kind = { video: a.kind === 2, file: a.kind === 3 };
    setState({ name: a.fileName, ...kind });
    try {
      const url = await attachmentApi.blobUrl(a.id);
      setState({ name: a.fileName, ...kind, url });
    } catch (e) {
      setState(undefined);
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
      {!state?.url ? (
        <div style={{ padding: 60, textAlign: 'center' }}>
          <Spin />
        </div>
      ) : state.file ? (
        <iframe
          src={state.url}
          title={state.name}
          style={{ width: '100%', height: '70vh', border: 0, borderRadius: 8 }}
        />
      ) : state.video ? (
        // biome-ignore lint/a11y/useMediaCaption: 验货视频没有字幕
        <video
          src={state.url}
          controls
          autoPlay
          style={{ width: '100%', maxHeight: '70vh', borderRadius: 8 }}
        />
      ) : (
        <img
          src={state.url}
          alt={state.name}
          style={{ width: '100%', maxHeight: '70vh', objectFit: 'contain' }}
        />
      )}
    </Modal>
  );
  return { open, node };
};

/** 一张附件缩略图：图片取缩略图，视频显示播放图标；点击预览 */
export const MediaTile: React.FC<{
  attachment: {
    id: number;
    fileName: string;
    kind: number;
    processingStatus?: number;
  };
  label?: string;
  size?: number;
  onOpen: () => void;
  onRemove?: () => void;
}> = ({ attachment, label, size = 76, onOpen, onRemove }) => {
  const { palette } = useAppTheme();
  const [thumb, setThumb] = useState<string>();
  const video = attachment.kind === 2;
  const file = attachment.kind === 3;
  useEffect(() => {
    if (video || file) return undefined;
    let url: string | undefined;
    let alive = true;
    attachmentApi
      .blobUrl(attachment.id)
      .then((u) => {
        url = u;
        if (alive) setThumb(u);
        else URL.revokeObjectURL(u);
      })
      .catch(() => setThumb(undefined));
    return () => {
      alive = false;
      if (url) URL.revokeObjectURL(url);
    };
  }, [attachment.id, video, file]);
  return (
    <div style={{ position: 'relative', width: size, height: size }}>
      <button
        type="button"
        onClick={onOpen}
        title={attachment.fileName}
        aria-label={`预览 ${attachment.fileName}`}
        style={{
          width: '100%',
          height: '100%',
          borderRadius: 10,
          border: `1px solid ${palette.hairline}`,
          background: thumb
            ? `center / cover no-repeat url("${thumb}")`
            : palette.inset,
          color: palette.sub,
          cursor: 'pointer',
          display: 'grid',
          placeItems: 'center',
          padding: 0,
          overflow: 'hidden',
        }}
      >
        {!thumb && (
          <span style={{ display: 'grid', placeItems: 'center', gap: 2 }}>
            {video ? (
              <PlayCircleOutlined style={{ fontSize: 22 }} />
            ) : file ? (
              <FilePdfOutlined style={{ fontSize: 22 }} />
            ) : (
              <PictureOutlined style={{ fontSize: 20 }} />
            )}
            {label && <span style={{ fontSize: 11 }}>{label}</span>}
          </span>
        )}
      </button>
      {video &&
        (attachment.processingStatus === 1 ||
          attachment.processingStatus === 3) && (
          <span
            style={{
              position: 'absolute',
              left: 4,
              bottom: 4,
              padding: '0 4px',
              borderRadius: 4,
              fontSize: 10,
              lineHeight: '16px',
              color: '#fff',
              background:
                attachment.processingStatus === 1
                  ? palette.link
                  : palette.orange,
            }}
            title={
              attachment.processingStatus === 1
                ? '正在压缩，压缩完成前播放的是原视频'
                : '压缩失败，保留原视频，可以正常查看'
            }
          >
            {attachment.processingStatus === 1 ? '压缩中' : '未压缩'}
          </span>
        )}
      {onRemove && (
        <button
          type="button"
          onClick={onRemove}
          aria-label={`删除 ${attachment.fileName}`}
          style={{
            position: 'absolute',
            top: -6,
            right: -6,
            width: 20,
            height: 20,
            borderRadius: 10,
            border: 'none',
            background: palette.red,
            color: '#fff',
            fontSize: 10,
            cursor: 'pointer',
            display: 'grid',
            placeItems: 'center',
            padding: 0,
          }}
        >
          <CloseOutlined />
        </button>
      )}
    </div>
  );
};

/** 上传按钮：选文件后逐个上传，成功一个回调一个 */
export const UploadTile: React.FC<{
  ownerType: OwnerType;
  /** 实拍任务的 ID：传入时素材直接按型号归档 */
  ownerId?: number;
  accept?: string;
  size?: number;
  /** 宽度，默认与 size 相同；给了宽度时图标与文字横排 */
  width?: number;
  /** 按钮文字，默认「上传」；传空字符串只显示图标 */
  label?: string;
  disabled?: boolean;
  onUploaded: (a: Attachment) => void | Promise<void>;
}> = ({
  ownerType,
  ownerId,
  accept = ACCEPT_ALL,
  size = 76,
  width,
  label = '上传',
  disabled,
  onUploaded,
}) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [busy, setBusy] = useState(0);
  return (
    <Upload
      accept={accept}
      multiple
      showUploadList={false}
      disabled={disabled}
      beforeUpload={(file) => {
        const bad = checkSize(file);
        if (bad) {
          message.error(`${file.name}：${bad}`);
          return Upload.LIST_IGNORE;
        }
        setBusy((n) => n + 1);
        attachmentApi
          .upload(ownerType, file, ownerId)
          .then((a) => onUploaded(a))
          .catch((e) =>
            message.error(`${file.name}：${readBizError(e).message}`),
          )
          .finally(() => setBusy((n) => n - 1));
        return false;
      }}
    >
      <button
        type="button"
        disabled={disabled}
        style={{
          width: width ?? size,
          height: size,
          borderRadius: 10,
          border: `1px dashed ${palette.hairline}`,
          background: 'transparent',
          color: palette.link,
          cursor: disabled ? 'not-allowed' : 'pointer',
          display: 'grid',
          placeItems: 'center',
          fontSize: 12,
        }}
      >
        <span
          style={
            width
              ? { display: 'flex', alignItems: 'center', gap: 6 }
              : { display: 'grid', placeItems: 'center', gap: 2 }
          }
        >
          {busy > 0 ? <LoadingOutlined /> : <PlusOutlined />}
          {label && (busy > 0 ? `上传中 ${busy}` : label)}
        </span>
      </button>
    </Upload>
  );
};

/** 单据的图片与视频：编辑时可以上传、删除（保存单据时一起提交） */
export const AttachmentWall: React.FC<{
  ownerType: OwnerType;
  value: Attachment[];
  onChange?: (list: Attachment[]) => void;
  max?: number;
  empty?: string;
  /** 可选文件类型，默认图片与视频 */
  accept?: string;
}> = ({ ownerType, value, onChange, max = 20, empty = '没有附件', accept }) => {
  const { palette } = useAppTheme();
  const preview = usePreview();
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
      {value.map((a) => (
        <MediaTile
          key={a.id}
          attachment={a}
          onOpen={() => preview.open(a)}
          onRemove={
            onChange
              ? () => onChange(value.filter((x) => x.id !== a.id))
              : undefined
          }
        />
      ))}
      {onChange && value.length < max && (
        <UploadTile
          ownerType={ownerType}
          accept={accept}
          onUploaded={(a) => onChange([...value, a])}
        />
      )}
      {!onChange && value.length === 0 && (
        <span style={{ fontSize: 12, color: palette.mute }}>{empty}</span>
      )}
      {preview.node}
    </div>
  );
};

/** 附件数：图片几张、视频几个 */
export const AttachmentCount: React.FC<{
  list?: Attachment[];
  count?: number;
}> = ({ list, count }) => {
  const { palette } = useAppTheme();
  if (list) {
    const img = list.filter((a) => a.kind === 1).length;
    const vid = list.length - img;
    if (!list.length) return <span style={{ color: palette.mute }}>—</span>;
    return (
      <span style={{ color: palette.link, whiteSpace: 'nowrap' }}>
        {img > 0 && (
          <>
            <PictureOutlined /> {img}{' '}
          </>
        )}
        {vid > 0 && (
          <>
            <PlayCircleOutlined /> {vid}
          </>
        )}
      </span>
    );
  }
  return count ? (
    <span style={{ color: palette.link }}>
      <PictureOutlined /> {count}
    </span>
  ) : (
    <span style={{ color: palette.mute }}>—</span>
  );
};

// ---------------------------------------------------------------- 填原因

export const ReasonModal: React.FC<{
  open: boolean;
  title: string;
  description?: React.ReactNode;
  placeholder?: string;
  okText?: string;
  danger?: boolean;
  onCancel: () => void;
  onOk: (reason: string) => Promise<void>;
}> = ({
  open,
  title,
  description,
  placeholder = '请填写原因',
  okText = '确定',
  danger,
  onCancel,
  onOk,
}) => {
  const { palette } = useAppTheme();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (open) setReason('');
  }, [open]);
  return (
    <Modal
      open={open}
      title={title}
      okText={okText}
      okButtonProps={{ danger, disabled: !reason.trim(), loading: busy }}
      onCancel={onCancel}
      onOk={async () => {
        setBusy(true);
        try {
          await onOk(reason.trim());
        } finally {
          setBusy(false);
        }
      }}
      destroyOnHidden
    >
      {description && (
        <div style={{ color: palette.sub, marginBottom: 12 }}>
          {description}
        </div>
      )}
      <Input.TextArea
        autoFocus
        rows={3}
        maxLength={200}
        showCount
        value={reason}
        placeholder={placeholder}
        onChange={(e) => setReason(e.target.value)}
        aria-label="原因"
      />
    </Modal>
  );
};

// ---------------------------------------------------------------- 附件标注型号

/** 单据里的一个型号 */
export interface DocModel {
  brand?: string;
  model: string;
}
/** 附件 ID → 标注的型号键（品牌|型号）；没有的附件按已有标注，都没有时为单据的全部型号 */
export type ModelTagMap = Record<number, string[]>;

const modelKey = (m: DocModel) => `${(m.brand ?? '').trim()}|${m.model.trim()}`;

const distinctModels = (models: DocModel[]) => {
  const seen = new Map<string, DocModel>();
  models.forEach((m) => {
    if (m.model?.trim()) seen.set(modelKey(m), m);
  });
  return [...seen.entries()];
};

/** 提交给后端的标注：只提交改过的附件，其余由后端沿用已有标注或默认全部型号 */
export const attachmentModels = (files: Attachment[], tags: ModelTagMap) =>
  files
    .filter((f) => tags[f.id])
    .map((f) => ({
      attachmentId: f.id,
      models: tags[f.id].map((k) => {
        const [brand, ...rest] = k.split('|');
        return { brand, model: rest.join('|') };
      }),
    }));

/**
 * 每个附件标出涉及的型号（规格「素材相关的附件与型号标注」）：默认单据的全部型号，可以取消；
 * 单据只有一个型号时自动标注，不显示。
 */
export const ModelTags: React.FC<{
  files: Attachment[];
  models: DocModel[];
  value: ModelTagMap;
  onChange: (v: ModelTagMap) => void;
}> = ({ files, models, value, onChange }) => {
  const { palette } = useAppTheme();
  const options = distinctModels(models);
  const media = files.filter((f) => f.kind === 1 || f.kind === 2);
  if (options.length <= 1 || media.length === 0) return null;
  const all = options.map(([k]) => k);
  const current = (f: Attachment) =>
    value[f.id] ??
    (f.models?.length
      ? f.models.map(modelKey).filter((k) => all.includes(k))
      : all);
  return (
    <div
      style={{
        display: 'grid',
        gap: 6,
        marginTop: 10,
        padding: '10px 12px',
        borderRadius: 10,
        background: palette.inset,
        fontSize: 12,
      }}
    >
      <span style={{ color: palette.sub }}>
        图里有哪些型号（默认全部，取消后这个文件不会出现在该型号的素材里）
      </span>
      {media.map((f) => (
        <div
          key={f.id}
          style={{
            display: 'flex',
            gap: 10,
            alignItems: 'center',
            flexWrap: 'wrap',
          }}
        >
          <span
            title={f.fileName}
            style={{
              width: 160,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              color: palette.ink,
            }}
          >
            {f.fileName}
          </span>
          <Checkbox.Group
            value={current(f)}
            options={options.map(([k, m]) => ({ label: m.model, value: k }))}
            onChange={(v) => {
              if (v.length === 0) return;
              onChange({ ...value, [f.id]: v as string[] });
            }}
          />
        </div>
      ))}
    </div>
  );
};
