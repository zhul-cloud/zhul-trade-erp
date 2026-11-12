import {
  EditOutlined,
  FileExcelOutlined,
  FilePdfOutlined,
  LeftOutlined,
  PictureOutlined,
  PlayCircleOutlined,
  RightOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { App, Button, Input, Modal, Popconfirm, Spin, Upload } from 'antd';
import React, { useEffect, useState } from 'react';
import { Card } from '@/pages/inquiry/shared/components';
import { MediaTile } from '@/pages/warehouse/components';
import {
  type Attachment,
  attachmentApi,
  type OwnerType,
} from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { downloadFile } from '@/utils/fileLink';
import { readSheets, type SheetData } from './excel';

/** 单据附件（spec system/file-attachment）：图片、视频、PDF、Excel，每张单据最多 20 个 */
export const DOC_FILE_ACCEPT =
  '.jpg,.jpeg,.png,.webp,.mp4,.mov,.pdf,.xlsx,.xls';
export const DOC_FILE_MAX = 20;

export const isExcel = (name: string) => /\.xlsx?$/i.test(name);

const errorText = (e: unknown) =>
  (e as { data?: { message?: string } })?.data?.message ??
  (e as Error)?.message ??
  '操作失败';

// ---------------------------------------------------------------- 预览

const ExcelView: React.FC<{ url: string; name: string }> = ({ url, name }) => {
  const { palette } = useAppTheme();
  const [sheets, setSheets] = useState<SheetData[]>();
  const [failed, setFailed] = useState(false);
  const [active, setActive] = useState(0);
  useEffect(() => {
    let alive = true;
    setSheets(undefined);
    setFailed(false);
    setActive(0);
    fetch(url)
      .then((r) => {
        if (!r.ok) throw new Error(String(r.status));
        return r.arrayBuffer();
      })
      .then((buf) => readSheets(buf))
      .then((s) => alive && setSheets(s))
      .catch(() => alive && setFailed(true));
    return () => {
      alive = false;
    };
  }, [url]);
  if (failed)
    return (
      <div style={{ padding: 48, textAlign: 'center', color: palette.mute }}>
        「{name}」无法在这里预览，请下载查看
      </div>
    );
  if (!sheets)
    return (
      <div style={{ padding: 60, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  const sheet = sheets[active];
  const cell: React.CSSProperties = {
    border: `1px solid ${palette.hairline}`,
    padding: '6px 8px',
    whiteSpace: 'nowrap',
    maxWidth: 320,
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  };
  const head: React.CSSProperties = {
    ...cell,
    background: palette.inset,
    color: palette.mute,
    fontWeight: 400,
    fontSize: 12,
  };
  return (
    <div style={{ display: 'grid', gap: 12 }}>
      {sheets.length > 1 && (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {sheets.map((s, i) => (
            <Button
              key={s.name}
              size="small"
              type={i === active ? 'primary' : 'text'}
              ghost={i === active}
              onClick={() => setActive(i)}
            >
              {s.name}
            </Button>
          ))}
        </div>
      )}
      <div style={{ overflow: 'auto', maxHeight: '62vh', borderRadius: 8 }}>
        <table
          style={{
            borderCollapse: 'collapse',
            fontSize: 13,
            color: palette.ink,
          }}
        >
          <thead>
            <tr>
              <th style={head} aria-label="行号" />
              {sheet.columns.map((c) => (
                <th key={c} style={head}>
                  {c}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {sheet.rows.map((r, i) => (
              // biome-ignore lint/suspicious/noArrayIndexKey: 表格行没有业务键
              <tr key={i}>
                <td style={{ ...head, textAlign: 'center' }}>{i + 1}</td>
                {sheet.columns.map((c, j) => (
                  <td key={c} style={cell} title={r[j]}>
                    {r[j]}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
        {sheet.rows.length === 0 && (
          <div style={{ padding: 24, color: palette.mute }}>
            这个工作表是空的
          </div>
        )}
      </div>
      {sheet.truncated && (
        <div style={{ color: palette.mute, fontSize: 12 }}>
          只显示前 {sheet.rows.length} 行，完整内容请下载查看
        </div>
      )}
    </div>
  );
};

/** 预览弹窗：同一张单据的附件可以左右切换；图片、视频、PDF、Excel 都在弹窗里看 */
export const useDocPreview = () => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [files, setFiles] = useState<Attachment[]>([]);
  const [index, setIndex] = useState<number>();
  const [url, setUrl] = useState<string>();
  const current = index === undefined ? undefined : files[index];
  useEffect(() => {
    if (!current) return undefined;
    let alive = true;
    setUrl(undefined);
    attachmentApi
      .blobUrl(current.id)
      .then((u) => alive && setUrl(u))
      .catch((e) => {
        if (!alive) return;
        message.error(errorText(e));
        setIndex(undefined);
      });
    return () => {
      alive = false;
    };
  }, [current, message]);
  const open = (list: Attachment[], at: number) => {
    setFiles(list);
    setIndex(at);
  };
  const go = (d: number) =>
    setIndex((i) =>
      i === undefined ? i : (i + d + files.length) % files.length,
    );
  const download = () =>
    current &&
    downloadFile(`/api/v1/attachments/${current.id}`).catch((e) =>
      message.error(errorText(e)),
    );
  const arrow = (d: number) => (
    <Button
      shape="circle"
      icon={d < 0 ? <LeftOutlined /> : <RightOutlined />}
      aria-label={d < 0 ? '上一个' : '下一个'}
      onClick={() => go(d)}
    />
  );
  const body = !current ? null : !url ? (
    <div style={{ padding: 60, textAlign: 'center' }}>
      <Spin />
    </div>
  ) : isExcel(current.fileName) ? (
    <ExcelView url={url} name={current.fileName} />
  ) : current.kind === 2 ? (
    // biome-ignore lint/a11y/useMediaCaption: 现场视频没有字幕
    <video
      src={url}
      controls
      autoPlay
      style={{ width: '100%', maxHeight: '62vh', borderRadius: 8 }}
    />
  ) : current.kind === 3 ? (
    <iframe
      src={url}
      title={current.fileName}
      style={{ width: '100%', height: '62vh', border: 0, borderRadius: 8 }}
    />
  ) : (
    <img
      src={url}
      alt={current.fileName}
      style={{
        width: '100%',
        maxHeight: '62vh',
        objectFit: 'contain',
        borderRadius: 8,
      }}
    />
  );
  const node = (
    <Modal
      open={!!current}
      width={900}
      footer={null}
      onCancel={() => setIndex(undefined)}
      destroyOnHidden
      title={
        current && (
          <div
            style={{
              display: 'flex',
              alignItems: 'baseline',
              gap: 10,
              paddingRight: 32,
            }}
          >
            <span
              style={{
                minWidth: 0,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {current.fileName}
            </span>
            {files.length > 1 && (
              <span style={{ color: palette.mute, fontSize: 12 }}>
                {(index ?? 0) + 1} / {files.length}
              </span>
            )}
            <span style={{ flex: 1 }} />
            <button
              type="button"
              className="zhul-link"
              style={{ fontSize: 13, fontWeight: 400 }}
              onClick={download}
            >
              下载
            </button>
          </div>
        )
      }
    >
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: files.length > 1 ? '40px 1fr 40px' : '1fr',
          alignItems: 'center',
          gap: 12,
        }}
      >
        {files.length > 1 && arrow(-1)}
        <div style={{ minWidth: 0 }}>{body}</div>
        {files.length > 1 && arrow(1)}
      </div>
      {files.length > 1 && (
        <div
          style={{
            display: 'flex',
            gap: 8,
            justifyContent: 'center',
            marginTop: 16,
            flexWrap: 'wrap',
          }}
        >
          {files.map((f, i) => (
            <button
              type="button"
              key={f.id}
              title={f.fileName}
              aria-label={`查看 ${f.fileName}`}
              onClick={() => setIndex(i)}
              style={{
                width: 64,
                height: 44,
                borderRadius: 8,
                cursor: 'pointer',
                background: palette.inset,
                color: palette.sub,
                border: `${i === index ? 2 : 1}px solid ${i === index ? palette.link : palette.hairline}`,
              }}
            >
              <FileIcon file={f} />
            </button>
          ))}
        </div>
      )}
    </Modal>
  );
  return { open, node };
};

const FileIcon: React.FC<{ file: Attachment; size?: number }> = ({
  file,
  size = 16,
}) =>
  isExcel(file.fileName) ? (
    <FileExcelOutlined style={{ fontSize: size }} />
  ) : file.kind === 2 ? (
    <PlayCircleOutlined style={{ fontSize: size }} />
  ) : file.kind === 3 ? (
    <FilePdfOutlined style={{ fontSize: size }} />
  ) : (
    <PictureOutlined style={{ fontSize: size }} />
  );

// ---------------------------------------------------------------- 卡片

/** 一个附件格子：图片显示缩略图，其他文件显示图标；下方是文件名 */
const FileTile: React.FC<{
  file: Attachment;
  onOpen: () => void;
  onRemove?: () => void;
}> = ({ file, onOpen, onRemove }) => {
  const { palette } = useAppTheme();
  const remove = onRemove && (
    <Popconfirm
      title={`删除「${file.fileName}」？`}
      okText="删除"
      okButtonProps={{ danger: true }}
      onConfirm={onRemove}
    >
      <a style={{ color: palette.red, fontSize: 12 }}>删除</a>
    </Popconfirm>
  );
  return (
    <div style={{ width: 92, display: 'grid', gap: 4 }}>
      {file.kind === 1 || file.kind === 2 ? (
        <MediaTile attachment={file} size={92} onOpen={onOpen} />
      ) : (
        <button
          type="button"
          onClick={onOpen}
          title={file.fileName}
          aria-label={`预览 ${file.fileName}`}
          style={{
            width: 92,
            height: 92,
            borderRadius: 10,
            border: `1px solid ${palette.hairline}`,
            background: palette.inset,
            color: isExcel(file.fileName) ? palette.green : palette.red,
            cursor: 'pointer',
            display: 'grid',
            placeItems: 'center',
          }}
        >
          <FileIcon file={file} size={26} />
        </button>
      )}
      <div
        style={{
          display: 'flex',
          gap: 4,
          alignItems: 'baseline',
          fontSize: 12,
          color: palette.sub,
        }}
      >
        <span
          title={file.fileName}
          style={{
            flex: 1,
            minWidth: 0,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {file.fileName}
        </span>
        {remove}
      </div>
    </div>
  );
};

/**
 * 单据详情的「备注与附件」卡片（单据页统一规范）：备注随时可改，附件可上传、预览、删除；
 * editable 为假（已作废或没有权限）时只读。
 */
export const DocFilesCard: React.FC<{
  ownerType: OwnerType;
  files: Attachment[];
  remark?: string;
  editable: boolean;
  onAttach: (ids: number[]) => Promise<unknown>;
  onRemove: (id: number) => Promise<unknown>;
  onRemark: (remark: string) => Promise<unknown>;
  onChanged: () => void;
  style?: React.CSSProperties;
}> = ({
  ownerType,
  files,
  remark,
  editable,
  onAttach,
  onRemove,
  onRemark,
  onChanged,
  style,
}) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const preview = useDocPreview();
  const [editing, setEditing] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(0);
  const run = async (fn: () => Promise<unknown>, ok: string) => {
    try {
      await fn();
      message.success(ok);
      onChanged();
      return true;
    } catch (e) {
      message.error(errorText(e));
      return false;
    }
  };
  const beforeUpload = (file: File) => {
    if (files.length + uploading >= DOC_FILE_MAX) {
      message.error(`附件最多 ${DOC_FILE_MAX} 个`);
      return Upload.LIST_IGNORE;
    }
    setUploading((n) => n + 1);
    attachmentApi
      .upload(ownerType, file)
      .then((a) => run(() => onAttach([a.id]), '附件已上传'))
      .catch((e) => message.error(`「${file.name}」${errorText(e)}`))
      .finally(() => setUploading((n) => n - 1));
    return Upload.LIST_IGNORE;
  };
  const saveRemark = async () => {
    setSaving(true);
    if (await run(() => onRemark((editing ?? '').trim()), '备注已保存'))
      setEditing(undefined);
    setSaving(false);
  };
  return (
    <Card style={{ padding: 20, marginBottom: 16, ...style }}>
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: 12 }}>
        <b style={{ color: palette.ink, fontSize: 15 }}>备注与附件</b>
        {files.length > 0 && (
          <span style={{ color: palette.mute, marginLeft: 8 }}>
            {files.length}
          </span>
        )}
        <span style={{ flex: 1 }} />
        {editable && files.length < DOC_FILE_MAX && (
          <Upload
            multiple
            accept={DOC_FILE_ACCEPT}
            showUploadList={false}
            beforeUpload={beforeUpload}
          >
            <a>
              <UploadOutlined />{' '}
              {uploading > 0 ? `正在上传 ${uploading} 个…` : '上传'}
            </a>
          </Upload>
        )}
      </div>
      {editing === undefined ? (
        <div
          style={{
            display: 'flex',
            gap: 8,
            alignItems: 'start',
            padding: '10px 12px',
            borderRadius: 8,
            background: palette.inset,
            fontSize: 13,
            whiteSpace: 'pre-wrap',
          }}
        >
          <span style={{ flex: 1, color: remark ? palette.ink : palette.mute }}>
            {remark || '没有备注'}
          </span>
          {editable && (
            <Button
              type="text"
              size="small"
              aria-label="编辑备注"
              icon={<EditOutlined />}
              onClick={() => setEditing(remark ?? '')}
            />
          )}
        </div>
      ) : (
        <div style={{ display: 'grid', gap: 8 }}>
          <Input.TextArea
            autoFocus
            rows={3}
            maxLength={500}
            showCount
            value={editing}
            onChange={(e) => setEditing(e.target.value)}
            aria-label="备注"
          />
          <div style={{ display: 'flex', gap: 8, justifyContent: 'end' }}>
            <Button size="small" onClick={() => setEditing(undefined)}>
              取消
            </Button>
            <Button
              size="small"
              type="primary"
              loading={saving}
              onClick={saveRemark}
            >
              保存
            </Button>
          </div>
        </div>
      )}
      {files.length > 0 ? (
        <div
          style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginTop: 12 }}
        >
          {files.map((f, i) => (
            <FileTile
              key={f.id}
              file={f}
              onOpen={() => preview.open(files, i)}
              onRemove={
                editable
                  ? () => {
                      run(() => onRemove(f.id), '附件已删除');
                    }
                  : undefined
              }
            />
          ))}
        </div>
      ) : (
        editable && (
          <div style={{ color: palette.mute, fontSize: 12, marginTop: 10 }}>
            可以上传图片、视频、PDF、Excel，每张单据最多 {DOC_FILE_MAX} 个
          </div>
        )
      )}
      {preview.node}
    </Card>
  );
};
