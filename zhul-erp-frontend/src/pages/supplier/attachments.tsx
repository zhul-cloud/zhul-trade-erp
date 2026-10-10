import {
  CloseOutlined,
  DownloadOutlined,
  EyeOutlined,
  FileImageOutlined,
  FilePdfOutlined,
  LoadingOutlined,
  UploadOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { Link } from '@umijs/max';
import { App, Upload } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import {
  ATTACHMENT_ACCEPT,
  ATTACHMENT_CATEGORIES,
  ATTACHMENT_MAX_BYTES,
  BUSINESS_LICENSE,
  MAX_ATTACHMENTS,
} from './constants';
import {
  readBizError,
  type SupplierAttachment,
  type SupplierFormValues,
  supplierApi,
} from './service';

/** 表单里的一行附件：已保存的带 id；本次新上传的带 fileKey 与本地预览地址 */
export interface AttachmentRow {
  key: string;
  id?: number;
  category: number;
  fileName: string;
  fileSize: number;
  contentType: string;
  fileKey?: string;
  localUrl?: string;
  createBy?: string;
  createTime?: string;
}

export const toAttachmentRows = (
  list?: SupplierAttachment[],
): AttachmentRow[] => (list ?? []).map((a) => ({ ...a, key: `id-${a.id}` }));

export const toAttachmentPayload = (
  rows?: AttachmentRow[],
): SupplierFormValues['attachments'] =>
  (rows ?? []).map((r) =>
    r.id
      ? { id: r.id, category: r.category }
      : { category: r.category, fileName: r.fileName, fileKey: r.fileKey },
  );

const formatSize = (bytes: number) =>
  bytes >= 1024 * 1024
    ? `${(bytes / 1024 / 1024).toFixed(1)} MB`
    : `${Math.max(1, Math.round(bytes / 1024))} KB`;

const isAllowed = (file: File) =>
  /\.(pdf|jpe?g|png)$/i.test(file.name) ||
  ['application/pdf', 'image/jpeg', 'image/png'].includes(file.type);

/**
 * 预览：先同步打开新窗口（避免被浏览器当成弹窗拦截），拿到文件地址后再跳转。
 * 已保存的附件要带登录凭证取文件，不能直接用链接。
 */
export async function previewAttachment(
  supplierId: number | undefined,
  row: Pick<AttachmentRow, 'id' | 'localUrl'>,
) {
  if (row.localUrl) {
    window.open(row.localUrl, '_blank', 'noopener');
    return;
  }
  if (!supplierId || !row.id) return;
  const win = window.open('', '_blank');
  const url = await supplierApi.attachmentBlobUrl(supplierId, row.id);
  if (win) win.location.href = url;
  // 新窗口加载完成后再释放
  setTimeout(() => window.URL.revokeObjectURL(url), 60_000);
}

export async function downloadAttachment(
  supplierId: number,
  row: Pick<AttachmentRow, 'id' | 'fileName'>,
) {
  if (!row.id) return;
  const url = await supplierApi.attachmentBlobUrl(supplierId, row.id);
  const a = document.createElement('a');
  a.href = url;
  a.download = row.fileName;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  window.URL.revokeObjectURL(url);
}

const FileChip: React.FC<{
  row: AttachmentRow;
  actions: React.ReactNode;
}> = ({ row, actions }) => {
  const { palette } = useAppTheme();
  const pdf = row.contentType === 'application/pdf';
  const type = pdf ? 'PDF' : row.contentType === 'image/png' ? 'PNG' : 'JPG';
  const meta = [
    type,
    formatSize(row.fileSize),
    row.id
      ? `${row.createBy ?? ''} ${formatDateTime(row.createTime).slice(5, 10)} 上传`
      : '待保存',
  ].join(' · ');
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        width: 280,
        height: 52,
        padding: '0 12px',
        borderRadius: 10,
        background: palette.inset,
        border: `1px solid ${palette.hairline}`,
      }}
    >
      <span
        aria-hidden
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 32,
          height: 32,
          flex: 'none',
          borderRadius: 8,
          fontSize: 16,
          color: pdf ? palette.red : palette.link,
          background: pdf ? palette.redSoft : palette.accentSoft,
        }}
      >
        {pdf ? <FilePdfOutlined /> : <FileImageOutlined />}
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          title={row.fileName}
          style={{
            fontSize: 12,
            fontWeight: 500,
            color: palette.ink,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {row.fileName}
        </div>
        <div
          title={meta}
          style={{
            fontSize: 11,
            color: palette.mute,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {meta}
        </div>
      </div>
      <div
        style={{ display: 'flex', gap: 10, color: palette.mute, flex: 'none' }}
      >
        {actions}
      </div>
    </div>
  );
};

const iconButton: React.CSSProperties = {
  border: 0,
  padding: 0,
  background: 'none',
  color: 'inherit',
  cursor: 'pointer',
};

/** 表单「附件」卡片内容：Form.Item 自定义控件，值为 AttachmentRow[] */
export const AttachmentEditor: React.FC<{
  value?: AttachmentRow[];
  onChange?: (rows: AttachmentRow[]) => void;
  supplierId?: number;
}> = ({ value = [], onChange, supplierId }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [uploading, setUploading] = useState<Record<number, number>>({});
  const [failed, setFailed] = useState<Record<number, string>>({});
  // 上传是异步的，多个文件同时完成时要基于最新列表追加
  const latest = useRef(value);
  latest.current = value;

  // 卸载时释放本地预览地址
  useEffect(
    () => () => {
      for (const r of latest.current) {
        if (r.localUrl) window.URL.revokeObjectURL(r.localUrl);
      }
    },
    [],
  );

  const upload = async (category: number, file: File) => {
    setFailed((f) => ({ ...f, [category]: '' }));
    if (!isAllowed(file)) {
      setFailed((f) => ({
        ...f,
        [category]: `「${file.name}」：只支持 PDF、JPG、PNG`,
      }));
      return;
    }
    if (file.size > ATTACHMENT_MAX_BYTES) {
      setFailed((f) => ({
        ...f,
        [category]: `「${file.name}」：单个文件不能超过 10MB`,
      }));
      return;
    }
    if (latest.current.length >= MAX_ATTACHMENTS) {
      message.error(`每个供应商最多 ${MAX_ATTACHMENTS} 个附件`);
      return;
    }
    setUploading((u) => ({ ...u, [category]: (u[category] ?? 0) + 1 }));
    try {
      const res = await supplierApi.uploadAttachment(file);
      const row: AttachmentRow = {
        key: res.fileKey,
        category,
        fileName: res.fileName,
        fileSize: res.fileSize,
        contentType: res.contentType,
        fileKey: res.fileKey,
        localUrl: window.URL.createObjectURL(file),
      };
      onChange?.([...latest.current, row]);
    } catch (e) {
      setFailed((f) => ({
        ...f,
        [category]: `「${file.name}」：${readBizError(e).message}`,
      }));
    } finally {
      setUploading((u) => ({ ...u, [category]: (u[category] ?? 1) - 1 }));
    }
  };

  const remove = (row: AttachmentRow) => {
    if (row.localUrl) window.URL.revokeObjectURL(row.localUrl);
    onChange?.(value.filter((r) => r.key !== row.key));
  };

  return (
    <>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          gap: 12,
          margin: '-8px 0 8px',
          fontSize: 13,
          color: palette.mute,
        }}
      >
        <span>
          按类型上传，支持 PDF、JPG、PNG，单个不超过
          10MB。营业执照建议必传，未上传时详情页会提示。
        </span>
        <span className="num" style={{ whiteSpace: 'nowrap' }}>
          {value.length} / {MAX_ATTACHMENTS}
        </span>
      </div>
      {ATTACHMENT_CATEGORIES.map((cat, i) => {
        const files = value.filter((r) => r.category === cat.value);
        const busy = (uploading[cat.value] ?? 0) > 0;
        return (
          <div
            key={cat.value}
            style={{
              display: 'flex',
              gap: 16,
              padding: '14px 0',
              borderTop: i === 0 ? undefined : `1px solid ${palette.hairline}`,
            }}
          >
            <div
              style={{
                width: 120,
                flex: 'none',
                lineHeight: '52px',
                color: palette.ink,
                fontWeight: 500,
              }}
            >
              {cat.label}
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
                {files.map((row) => (
                  <FileChip
                    key={row.key}
                    row={row}
                    actions={
                      <>
                        <button
                          type="button"
                          aria-label={`预览 ${row.fileName}`}
                          style={iconButton}
                          onClick={() =>
                            previewAttachment(supplierId, row).catch((e) =>
                              message.error(readBizError(e).message),
                            )
                          }
                        >
                          <EyeOutlined />
                        </button>
                        <button
                          type="button"
                          aria-label={`移除 ${row.fileName}`}
                          style={iconButton}
                          onClick={() => remove(row)}
                        >
                          <CloseOutlined />
                        </button>
                      </>
                    }
                  />
                ))}
                <Upload
                  accept={ATTACHMENT_ACCEPT}
                  multiple
                  showUploadList={false}
                  beforeUpload={(file) => {
                    upload(cat.value, file);
                    return false;
                  }}
                >
                  <button
                    type="button"
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 6,
                      height: 52,
                      padding: '0 16px',
                      borderRadius: 10,
                      border: `1px dashed ${palette.control}`,
                      background: 'none',
                      color: palette.link,
                      fontWeight: 500,
                      cursor: 'pointer',
                    }}
                  >
                    {busy ? <LoadingOutlined /> : <UploadOutlined />}
                    {busy ? '上传中' : '上传'}
                  </button>
                </Upload>
              </div>
              {failed[cat.value] && (
                <div
                  role="alert"
                  style={{ marginTop: 8, fontSize: 12, color: palette.red }}
                >
                  {failed[cat.value]}
                </div>
              )}
            </div>
          </div>
        );
      })}
    </>
  );
};

/** 详情页附件：按类型分组，可预览、下载；没有营业执照时顶部提示 */
export const AttachmentGroups: React.FC<{
  supplierId: number;
  attachments: SupplierAttachment[];
  editPath?: string;
}> = ({ supplierId, attachments, editPath }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const rows = toAttachmentRows(attachments);
  const missingLicense = !rows.some((r) => r.category === BUSINESS_LICENSE);
  const fail = (e: unknown) => message.error(readBizError(e).message);

  return (
    <>
      {missingLicense && (
        <div
          role="status"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: '10px 16px',
            marginBottom: 8,
            borderRadius: 10,
            color: palette.orange,
            background: palette.orangeSoft,
          }}
        >
          <WarningOutlined />
          <span style={{ flex: 1 }}>
            还没有上传营业执照，建议补上，便于核验供应商资质。
          </span>
          {editPath && <Link to={editPath}>去上传</Link>}
        </div>
      )}
      {rows.length === 0 ? (
        <span style={{ color: palette.mute }}>未上传附件</span>
      ) : (
        ATTACHMENT_CATEGORIES.filter((c) =>
          rows.some((r) => r.category === c.value),
        ).map((cat, i) => (
          <div
            key={cat.value}
            style={{
              display: 'flex',
              gap: 16,
              padding: '14px 0',
              borderTop: i === 0 ? undefined : `1px solid ${palette.hairline}`,
            }}
          >
            <div
              style={{
                width: 120,
                flex: 'none',
                lineHeight: '52px',
                color: palette.ink,
                fontWeight: 500,
              }}
            >
              {cat.label}
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
              {rows
                .filter((r) => r.category === cat.value)
                .map((row) => (
                  <FileChip
                    key={row.key}
                    row={row}
                    actions={
                      <>
                        <button
                          type="button"
                          aria-label={`预览 ${row.fileName}`}
                          style={iconButton}
                          onClick={() =>
                            previewAttachment(supplierId, row).catch(fail)
                          }
                        >
                          <EyeOutlined />
                        </button>
                        <button
                          type="button"
                          aria-label={`下载 ${row.fileName}`}
                          style={iconButton}
                          onClick={() =>
                            downloadAttachment(supplierId, row).catch(fail)
                          }
                        >
                          <DownloadOutlined />
                        </button>
                      </>
                    }
                  />
                ))}
            </div>
          </div>
        ))
      )}
    </>
  );
};
