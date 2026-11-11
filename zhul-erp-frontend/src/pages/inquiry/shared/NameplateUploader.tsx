import {
  CloseOutlined,
  FilePdfOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { App, Upload } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import { Pill } from '@/pages/crm/opportunity/components';
import { useAppTheme } from '@/theme/AppTheme';
import { NAMEPLATE_MAX_BYTES } from './constants';
import { FileTile, useFilePreview } from './FilePreview';
import type { PreviewFile } from './files';
import { inquiryApi, readBizError, type UploadedFile } from './service';

/** 每次录入或追加最多 20 张（spec inquiry/inquiry-intake「铭牌图片」） */
export const NAMEPLATE_MAX = 20;
const ACCEPT = '.jpg,.jpeg,.png,.pdf';
const isPdf = (name: string) => /\.pdf$/i.test(name);

/** 刚上传、还没提交的铭牌图片：用本地预览地址显示缩略图 */
export type PickedPlate = UploadedFile & { localUrl: string };

const TILE_W = 88;
const TILE_H = 66;

const RemoveButton: React.FC<{ name: string; onClick: () => void }> = ({
  name,
  onClick,
}) => (
  <button
    type="button"
    aria-label={`移除 ${name}`}
    onClick={onClick}
    style={{
      position: 'absolute',
      top: -6,
      right: -6,
      width: 20,
      height: 20,
      borderRadius: 10,
      border: 0,
      padding: 0,
      cursor: 'pointer',
      background: 'rgba(15,23,42,0.85)',
      color: '#fff',
      fontSize: 10,
      display: 'grid',
      placeItems: 'center',
    }}
  >
    <CloseOutlined />
  </button>
);

/**
 * 「铭牌图片」上传区：和询盘附件分开，收 JPG、PNG 与 PDF（客户有时发的是 PDF）；采购能看到。
 * existing 为已保存的铭牌图片（修改询盘时），可以移除。
 */
const NameplateUploader: React.FC<{
  value: PickedPlate[];
  onChange: (next: PickedPlate[]) => void;
  existing?: PreviewFile[];
  existingEndpoint?: (f: PreviewFile) => string;
  onRemoveExisting?: (f: PreviewFile) => void;
  onUploadingChange?: (n: number) => void;
  /** 修改询盘时补传的不解析，说明文字不同 */
  hint?: string;
}> = ({
  value,
  onChange,
  existing = [],
  existingEndpoint,
  onRemoveExisting,
  onUploadingChange,
  hint = '客户发来的型号铭牌照片或 PDF 放这里。AI 解析时会认出型号并对应到每一行，采购在「我的询价」里能直接看到。不要放带客户信息的图片。',
}) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [uploading, setUploading] = useState(0);
  const preview = useFilePreview();
  const latest = useRef(value);
  latest.current = value;
  /** 每次最多传 20 张：修改询盘时已保存的不算 */
  const count = value.length;

  useEffect(() => onUploadingChange?.(uploading), [uploading]);
  // 关闭时释放本地预览地址
  useEffect(
    () => () => {
      for (const p of latest.current) URL.revokeObjectURL(p.localUrl);
    },
    [],
  );

  const beforeUpload = (file: File) => {
    if (!/\.(jpe?g|png|pdf)$/i.test(file.name)) {
      message.error('铭牌只支持 JPG、PNG、PDF');
      return Upload.LIST_IGNORE;
    }
    if (count + uploading >= NAMEPLATE_MAX) {
      message.error(`铭牌图片最多 ${NAMEPLATE_MAX} 张`);
      return Upload.LIST_IGNORE;
    }
    if (file.size > NAMEPLATE_MAX_BYTES) {
      message.error(`「${file.name}」超过 10MB`);
      return Upload.LIST_IGNORE;
    }
    setUploading((n) => n + 1);
    inquiryApi
      .upload(file)
      .then((f) => {
        // 几张同时传完时，在最新的列表上追加（不等重新渲染），避免后完成的覆盖先完成的
        latest.current = [
          ...latest.current,
          { ...f, localUrl: URL.createObjectURL(file) },
        ];
        onChange(latest.current);
      })
      .catch((e) => message.error(`「${file.name}」${readBizError(e).message}`))
      .finally(() => setUploading((n) => n - 1));
    return Upload.LIST_IGNORE;
  };

  return (
    <div
      style={{
        display: 'grid',
        gap: 10,
        padding: '14px 16px',
        borderRadius: 12,
        background: palette.accentSoft,
        border: `1px solid ${palette.accentLine}`,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ fontWeight: 600, color: palette.ink }}>铭牌图片</span>
        <Pill tone="accent">采购能看到</Pill>
        <span style={{ marginLeft: 'auto', fontSize: 12, color: palette.mute }}>
          JPG、PNG、PDF ·{' '}
          {existing.length > 0 ? `已有 ${existing.length} 张，补传 ` : ''}
          {count} / {NAMEPLATE_MAX}
        </span>
      </div>
      <span style={{ fontSize: 12, color: palette.sub }}>{hint}</span>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12 }}>
        {existing.map((f) => (
          <div key={`e${f.id}`} style={{ position: 'relative' }}>
            <FileTile
              file={f}
              endpoint={existingEndpoint?.(f) ?? ''}
              width={TILE_W}
              height={TILE_H}
              onOpen={() =>
                existingEndpoint &&
                preview.open(existing, existing.indexOf(f), existingEndpoint)
              }
            />
            {onRemoveExisting && (
              <RemoveButton
                name={f.fileName}
                onClick={() => onRemoveExisting(f)}
              />
            )}
          </div>
        ))}
        {value.map((p) => (
          <div
            key={p.fileKey}
            style={{
              position: 'relative',
              width: TILE_W,
              display: 'grid',
              gap: 6,
            }}
          >
            <div
              role="img"
              aria-label={p.fileName}
              style={{
                width: TILE_W,
                height: TILE_H,
                borderRadius: 10,
                border: `1px solid ${palette.hairline}`,
                background: isPdf(p.fileName)
                  ? palette.inset
                  : `center / cover no-repeat url("${p.localUrl}")`,
                display: 'grid',
                placeItems: 'center',
              }}
            >
              {isPdf(p.fileName) && (
                <FilePdfOutlined style={{ fontSize: 24, color: palette.red }} />
              )}
            </div>
            <span
              style={{
                fontSize: 12,
                color: palette.sub,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {p.fileName}
            </span>
            <RemoveButton
              name={p.fileName}
              onClick={() => {
                URL.revokeObjectURL(p.localUrl);
                onChange(value.filter((x) => x.fileKey !== p.fileKey));
              }}
            />
          </div>
        ))}
        {count < NAMEPLATE_MAX && (
          <Upload
            multiple
            accept={ACCEPT}
            showUploadList={false}
            beforeUpload={beforeUpload}
          >
            <button
              type="button"
              style={{
                width: TILE_W,
                height: TILE_H,
                borderRadius: 10,
                border: `1px dashed ${palette.control}`,
                background: 'transparent',
                color: palette.mute,
                cursor: 'pointer',
                display: 'grid',
                placeItems: 'center',
                alignContent: 'center',
                gap: 2,
                fontSize: 12,
              }}
            >
              <PlusOutlined />
              {uploading > 0 ? `上传中 ${uploading}` : '添加铭牌'}
            </button>
          </Upload>
        )}
      </div>
      {preview.node}
    </div>
  );
};

export const toRefs = (plates: PickedPlate[]) =>
  plates.map((p) => ({ fileKey: p.fileKey, fileName: p.fileName }));

export default NameplateUploader;
