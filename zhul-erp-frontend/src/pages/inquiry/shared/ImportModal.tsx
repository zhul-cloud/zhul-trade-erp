import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  CloudUploadOutlined,
  ExclamationCircleOutlined,
  FileExcelOutlined,
} from '@ant-design/icons';
import {
  App,
  Button,
  Checkbox,
  Input,
  InputNumber,
  Modal,
  Select,
  Upload,
} from 'antd';
import React, { useEffect, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { useQuoteDicts } from './components';
import { CHANNEL_OPTIONS } from './constants';
import { type ImportFile, type ImportRow, readBizError } from './service';

interface EditableRow extends ImportRow {
  key: string;
  ignored: boolean;
}

interface EditableFile extends Omit<ImportFile, 'rows'> {
  rows: EditableRow[];
}

/** 一行还有没有要处理的问题（忽略的行不算） */
const unresolved = (r: EditableRow) =>
  !r.ignored &&
  (!r.itemId || (!r.noStock && (r.unitPrice == null || r.unitPrice < 0)));

/** 导入询价结果：选文件 → 识别预览（问题行修正或忽略）→ 确认入库 */
const ImportModal: React.FC<{
  open: boolean;
  title?: string;
  preview: (files: File[]) => Promise<ImportFile[]>;
  confirm: (files: unknown[]) => Promise<void>;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, title = '导入询价结果', preview, confirm, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const { conditionOptions, leadTimeOptions } = useQuoteDicts();
  const [picked, setPicked] = useState<File[]>([]);
  const [files, setFiles] = useState<EditableFile[] | null>(null);
  const [active, setActive] = useState(0);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (open) {
      setPicked([]);
      setFiles(null);
      setActive(0);
    }
  }, [open]);

  const recognize = async () => {
    setBusy(true);
    try {
      const res = await preview(picked);
      setFiles(
        res.map((f, i) => ({
          ...f,
          rows: f.rows.map((r, j) => ({
            ...r,
            key: `${i}-${j}`,
            ignored: false,
            unitPrice: r.problems.includes('单价不是数字')
              ? undefined
              : r.unitPrice,
          })),
        })),
      );
      setActive(
        Math.max(
          0,
          res.findIndex((f) => !f.rejectReason),
        ),
      );
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const patchRow = (fi: number, key: string, p: Partial<EditableRow>) =>
    setFiles((prev) =>
      prev
        ? prev.map((f, i) =>
            i === fi
              ? {
                  ...f,
                  rows: f.rows.map((r) => (r.key === key ? { ...r, ...p } : r)),
                }
              : f,
          )
        : prev,
    );

  const accepted = (files ?? []).filter((f) => !f.rejectReason && f.taskId);
  const rowsToSave = accepted.flatMap((f) => f.rows.filter((r) => !r.ignored));
  const blocking = accepted.flatMap((f) => f.rows).filter(unresolved).length;
  const rejected = (files ?? []).filter((f) => f.rejectReason);

  const save = async () => {
    setBusy(true);
    try {
      await confirm(
        accepted
          .map((f) => ({
            taskId: f.taskId,
            fileKey: f.fileKey,
            fileName: f.fileName,
            rows: f.rows
              .filter((r) => !r.ignored)
              .map((r) => ({
                itemId: r.itemId,
                channel: r.channel,
                shopName: r.shopName,
                noStock: r.noStock,
                unitPrice: r.noStock ? null : r.unitPrice,
                taxIncluded: !r.noStock && !!r.taxIncluded,
                taxRate: r.taxIncluded ? r.taxRate : undefined,
                itemCondition: r.itemCondition,
                leadTime: r.leadTime,
                note: r.note,
              })),
          }))
          .filter((f) => f.rows.length > 0),
      );
      message.success(`已入库 ${rowsToSave.length} 行，回价进度已更新`);
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const current = files?.[active];

  return (
    <Modal
      open={open}
      title={title}
      width={1080}
      destroyOnHidden
      onCancel={onClose}
      footer={
        files ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span
              style={{
                marginRight: 'auto',
                color: blocking ? palette.orange : palette.mute,
                fontSize: 12,
              }}
            >
              {blocking
                ? `还有 ${blocking} 行需要修正或忽略`
                : '确认后这些价格进入历史询价，并计入对应客户询盘的回价进度'}
            </span>
            <Button onClick={() => setFiles(null)}>重新选择文件</Button>
            <Button
              type="primary"
              loading={busy}
              disabled={blocking > 0 || rowsToSave.length === 0}
              onClick={save}
            >
              确认入库 {rowsToSave.length} 行
            </Button>
          </div>
        ) : (
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
            <Button onClick={onClose}>取消</Button>
            <Button
              type="primary"
              loading={busy}
              disabled={picked.length === 0}
              onClick={recognize}
            >
              开始识别
            </Button>
          </div>
        )
      }
    >
      {!files ? (
        <>
          <div style={{ color: palette.mute, marginBottom: 12 }}>
            选择从系统下载、填好价格的询价包，可以一次选多个。
          </div>
          <Upload.Dragger
            multiple
            accept=".xlsx"
            showUploadList={false}
            beforeUpload={(f) => {
              setPicked((prev) =>
                prev.some((p) => p.name === f.name && p.size === f.size)
                  ? prev
                  : [...prev, f],
              );
              return Upload.LIST_IGNORE;
            }}
          >
            <p style={{ fontSize: 24, color: palette.link, margin: 0 }}>
              <CloudUploadOutlined />
            </p>
            <p style={{ margin: '4px 0 0', color: palette.sub }}>
              把填好的询价包拖到这里，或点击选择
            </p>
            <p style={{ margin: 0, color: palette.mute, fontSize: 12 }}>
              只支持从系统下载的 .xlsx 询价包
            </p>
          </Upload.Dragger>
          {picked.length > 0 && (
            <div style={{ display: 'grid', gap: 6, marginTop: 12 }}>
              {picked.map((f) => (
                <div
                  key={`${f.name}-${f.size}`}
                  style={{
                    display: 'flex',
                    gap: 8,
                    alignItems: 'center',
                    padding: '8px 12px',
                    borderRadius: 10,
                    background: palette.hover,
                  }}
                >
                  <FileExcelOutlined style={{ color: palette.green }} />
                  <span style={{ color: palette.ink }}>{f.name}</span>
                  <a
                    style={{ marginLeft: 'auto' }}
                    onClick={() =>
                      setPicked((prev) => prev.filter((p) => p !== f))
                    }
                  >
                    移除
                  </a>
                </div>
              ))}
            </div>
          )}
        </>
      ) : (
        <div style={{ display: 'grid', gap: 12 }}>
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            {files.map((f, i) =>
              f.rejectReason ? null : (
                <button
                  type="button"
                  key={f.fileKey ?? f.fileName}
                  onClick={() => setActive(i)}
                  style={{
                    display: 'flex',
                    gap: 10,
                    alignItems: 'center',
                    padding: 12,
                    borderRadius: 12,
                    cursor: 'pointer',
                    textAlign: 'left',
                    background:
                      i === active ? palette.accentSoft : palette.inset,
                    border: `1px solid ${i === active ? palette.accentLine : palette.hairline}`,
                  }}
                >
                  <FileExcelOutlined
                    style={{ color: palette.green, fontSize: 18 }}
                  />
                  <span style={{ display: 'grid' }}>
                    <span style={{ color: palette.ink, fontWeight: 600 }}>
                      {f.fileName}
                    </span>
                    <span style={{ color: palette.mute, fontSize: 12 }}>
                      {f.taskCode} · {f.assigneeName}
                    </span>
                  </span>
                  <span
                    style={{
                      fontSize: 12,
                      color: f.rows.some(unresolved)
                        ? palette.orange
                        : palette.green,
                    }}
                  >
                    {f.rows.filter((r) => !unresolved(r) && !r.ignored).length}{' '}
                    行可入库
                    {f.rows.some(unresolved)
                      ? ` · ${f.rows.filter(unresolved).length} 行需修正`
                      : ''}
                  </span>
                </button>
              ),
            )}
          </div>
          {current && !current.rejectReason && (
            <div
              style={{
                border: `1px solid ${palette.hairline}`,
                borderRadius: 12,
                overflow: 'hidden',
              }}
            >
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns:
                    '28px 1.4fr 100px 1.2fr 150px 120px 90px 60px',
                  gap: 8,
                  padding: '10px 12px',
                  background: palette.inset,
                  color: palette.mute,
                  fontSize: 12,
                  fontWeight: 600,
                }}
              >
                <span />
                <span>型号</span>
                <span>渠道</span>
                <span>店铺</span>
                <span>单价（CNY）</span>
                <span>货况</span>
                <span>货期</span>
                <span>忽略</span>
              </div>
              {current.rows.length === 0 && (
                <div style={{ padding: 16, color: palette.mute }}>
                  这个文件里没有填写任何价格。
                </div>
              )}
              {current.rows.map((r) => {
                const bad = unresolved(r);
                return (
                  <div
                    key={r.key}
                    style={{
                      display: 'grid',
                      gridTemplateColumns:
                        '28px 1.4fr 100px 1.2fr 150px 120px 90px 60px',
                      gap: 8,
                      padding: '10px 12px',
                      alignItems: 'start',
                      borderTop: `1px solid ${palette.hairline}`,
                      background: bad ? palette.orangeSoft : 'transparent',
                      opacity: r.ignored ? 0.5 : 1,
                    }}
                  >
                    <span style={{ paddingTop: 4 }}>
                      {bad ? (
                        <ExclamationCircleOutlined
                          style={{ color: palette.orange }}
                        />
                      ) : (
                        <CheckCircleOutlined style={{ color: palette.green }} />
                      )}
                    </span>
                    <span style={{ display: 'grid' }}>
                      <span style={{ color: palette.ink }}>
                        {r.model || '（空）'}
                      </span>
                      <span style={{ color: palette.mute, fontSize: 11 }}>
                        {r.position}
                      </span>
                      {!r.itemId && (
                        <span style={{ color: palette.orange, fontSize: 11 }}>
                          型号不在这个任务里，只能忽略
                        </span>
                      )}
                    </span>
                    <Select
                      size="small"
                      value={r.channel}
                      options={CHANNEL_OPTIONS}
                      onChange={(v) => patchRow(active, r.key, { channel: v })}
                    />
                    <Input
                      size="small"
                      value={r.shopName}
                      onChange={(e) =>
                        patchRow(active, r.key, { shopName: e.target.value })
                      }
                    />
                    <span style={{ display: 'grid', gap: 2 }}>
                      {r.noStock ? (
                        <span style={{ color: palette.mute, paddingTop: 4 }}>
                          无货
                        </span>
                      ) : (
                        <InputNumber
                          size="small"
                          min={0}
                          precision={2}
                          value={r.unitPrice}
                          status={
                            !r.ignored && r.unitPrice == null
                              ? 'warning'
                              : undefined
                          }
                          onChange={(v) =>
                            patchRow(active, r.key, {
                              unitPrice: v ?? undefined,
                            })
                          }
                          style={{ width: '100%' }}
                        />
                      )}
                      {r.taxIncluded && (
                        <span style={{ color: palette.mute, fontSize: 11 }}>
                          含税 {r.taxRate ?? 13}%，按不含税价入库比价
                        </span>
                      )}
                      {r.problems.includes('税率不在可选值内') && (
                        <span style={{ color: palette.orange, fontSize: 11 }}>
                          原文「{r.rawPrice}」的税率不在可选值内
                        </span>
                      )}
                      {r.problems.includes('单价不是数字') && (
                        <span style={{ color: palette.orange, fontSize: 11 }}>
                          原文「{r.rawPrice}」不是数字，请改成数字
                        </span>
                      )}
                    </span>
                    <span style={{ display: 'grid', gap: 2 }}>
                      <Select
                        size="small"
                        allowClear
                        placeholder="未填"
                        value={r.itemCondition || undefined}
                        options={conditionOptions}
                        onChange={(v) =>
                          patchRow(active, r.key, { itemCondition: v ?? 0 })
                        }
                      />
                      {r.problems.includes('货况不在可选值内') && (
                        <span style={{ color: palette.mute, fontSize: 11 }}>
                          原文「{r.rawCondition}」，请重选
                        </span>
                      )}
                    </span>
                    <span style={{ display: 'grid', gap: 2 }}>
                      <Select
                        size="small"
                        allowClear
                        placeholder="未填"
                        value={r.leadTime || undefined}
                        options={leadTimeOptions}
                        onChange={(v) =>
                          patchRow(active, r.key, { leadTime: v ?? 0 })
                        }
                      />
                      {r.problems.includes('货期不在可选值内') && (
                        <span style={{ color: palette.mute, fontSize: 11 }}>
                          原文「{r.rawLeadTime}」，请重选
                        </span>
                      )}
                    </span>
                    <Checkbox
                      checked={r.ignored}
                      onChange={(e) =>
                        patchRow(active, r.key, { ignored: e.target.checked })
                      }
                    />
                  </div>
                );
              })}
            </div>
          )}
          {rejected.length > 0 && (
            <div
              style={{
                display: 'grid',
                gap: 4,
                padding: '10px 12px',
                borderRadius: 10,
                background: palette.redSoft,
                color: palette.red,
                fontSize: 12,
              }}
            >
              <span style={{ fontWeight: 600 }}>
                <CloseCircleOutlined /> 另有 {rejected.length} 个文件未入库：
              </span>
              {rejected.map((f) => (
                <span key={f.fileName}>
                  「{f.fileName}」{f.rejectReason}
                </span>
              ))}
            </div>
          )}
        </div>
      )}
    </Modal>
  );
};

export default ImportModal;
