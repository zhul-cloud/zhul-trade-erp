import {
  DeleteOutlined,
  FileTextOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import {
  App,
  Button,
  DatePicker,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import { SupplierPicker } from '@/pages/purchase/components';
import {
  DISP,
  type DisposeLine,
  type ReturnReceipt,
  returnApi,
} from '@/pages/sales/after-sales/service';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { AttachmentWall } from '../components';
import type { Attachment } from '../service';

export const DISP_OPTIONS = [
  { value: DISP.SUPPLIER, label: '退回供应商' },
  { value: DISP.HOLD, label: '转闲置库存' },
  { value: DISP.SCRAP, label: '报废' },
];
export const DISP_TONE: Record<number, 'orange' | 'cyan' | 'gray'> = {
  1: 'orange',
  2: 'cyan',
  3: 'gray',
};

// ---------------------------------------------------------------- 验收

export const ReceiveModal: React.FC<{
  data?: ReturnReceipt;
  onClose: () => void;
  onDone: () => void;
}> = ({ data, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [qty, setQty] = useState<
    Record<number, { r: number | null; q: number | null; note: string }>
  >({});
  const [note, setNote] = useState('');
  const [files, setFiles] = useState<Attachment[]>([]);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!data) return;
    setDate(dayjs());
    const m: Record<
      number,
      { r: number | null; q: number | null; note: string }
    > = {};
    data.items.forEach((i) => {
      m[i.id] = { r: i.quantity, q: i.quantity, note: '' };
    });
    setQty(m);
    setNote('');
    setFiles([]);
  }, [data]);
  if (!data) return null;
  return (
    <Modal
      open
      onCancel={onClose}
      width={680}
      title={
        <div>
          <div>退货验收 {data.receiptNo}</div>
          <div style={{ fontSize: 12, color: palette.mute, fontWeight: 400 }}>
            {data.customerName} · 销售退货单 {data.returnNo}
            {data.trackingNo
              ? ` · ${data.carrier ?? ''} ${data.trackingNo}`
              : ''}
          </div>
        </div>
      }
      okText="确认验收"
      okButtonProps={{ loading: busy }}
      onOk={async () => {
        setBusy(true);
        try {
          await returnApi.receive(data.id, {
            receivedDate: date.format('YYYY-MM-DD'),
            note: note.trim() || undefined,
            items: data.items.map((i) => ({
              itemId: i.id,
              receivedQty: qty[i.id]?.r ?? 0,
              qualifiedQty: qty[i.id]?.q ?? 0,
              note: qty[i.id]?.note.trim() || undefined,
            })),
            attachmentIds: files.map((f) => f.id),
          });
          message.success('已验收，交采购员处理去向');
          onDone();
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
      destroyOnHidden
    >
      <Space orientation="vertical" size={14} style={{ width: '100%' }}>
        {data.items.map((i) => {
          const v = qty[i.id] ?? { r: null, q: null, note: '' };
          const bad = (v.r ?? 0) - (v.q ?? 0);
          return (
            <div key={i.id} style={{ display: 'grid', gap: 6 }}>
              <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 600, color: palette.ink }}>
                    {i.model}
                  </div>
                  <div
                    style={{
                      fontSize: 12,
                      color: bad > 0 ? palette.orange : palette.mute,
                    }}
                  >
                    退货 {i.quantity} 个{bad > 0 ? ` · 不良 ${bad} 个` : ''}
                  </div>
                </div>
                <InputNumber<number>
                  min={0}
                  max={i.quantity}
                  precision={0}
                  value={v.r}
                  onChange={(n) =>
                    setQty((s) => ({ ...s, [i.id]: { ...v, r: n } }))
                  }
                  prefix="实收"
                  style={{ width: 130 }}
                  aria-label={`${i.model} 实收`}
                />
                <InputNumber<number>
                  min={0}
                  max={v.r ?? 0}
                  precision={0}
                  value={v.q}
                  onChange={(n) =>
                    setQty((s) => ({ ...s, [i.id]: { ...v, q: n } }))
                  }
                  prefix="合格"
                  style={{ width: 130 }}
                  aria-label={`${i.model} 合格`}
                />
              </div>
              <Input
                size="small"
                value={v.note}
                maxLength={300}
                onChange={(e) =>
                  setQty((s) => ({
                    ...s,
                    [i.id]: { ...v, note: e.target.value },
                  }))
                }
                placeholder="这一行的说明（选填），如外壳烧焦"
                aria-label={`${i.model} 说明`}
              />
            </div>
          );
        })}
        <div
          style={{ display: 'grid', gridTemplateColumns: '180px 1fr', gap: 12 }}
        >
          <DatePicker
            value={date}
            allowClear={false}
            disabledDate={(d) => d.isAfter(dayjs(), 'day')}
            onChange={(d) => d && setDate(d)}
            aria-label="收货日期"
          />
          <Input
            value={note}
            maxLength={300}
            onChange={(e) => setNote(e.target.value)}
            placeholder="验收说明（选填）"
            aria-label="验收说明"
          />
        </div>
        <AttachmentWall
          ownerType="RETURN_RECEIPT"
          value={files}
          onChange={setFiles}
          max={20}
        />
      </Space>
    </Modal>
  );
};

// ---------------------------------------------------------------- 去向

type Row = DisposeLine & { key: number; supplierName?: string; poNo?: string };

/** 处理去向：可以分几次处理，每行累计不超过实收；退回供应商保存后生成采购退货单 */
export const DisposeModal: React.FC<{
  data?: ReturnReceipt;
  onClose: () => void;
  onDone: () => void;
}> = ({ data, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [rows, setRows] = useState<Row[]>([]);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!data) return;
    const held = new Map<number, number>();
    data.dispositions
      .filter((d) => d.disposition === DISP.HOLD)
      .forEach((d) => {
        held.set(d.itemId, (held.get(d.itemId) ?? 0) + d.quantity);
      });
    const out: Row[] = [];
    data.items.forEach((i, n) => {
      const left = (i.receivedQty ?? 0) - i.disposedQty;
      if (left <= 0) return;
      const canHold = Math.max(
        0,
        Math.min(left, (i.qualifiedQty ?? 0) - (held.get(i.id) ?? 0)),
      );
      if (left - canHold > 0) {
        out.push({
          key: n * 10 + 1,
          itemId: i.id,
          disposition: DISP.SUPPLIER,
          quantity: left - canHold,
          supplierId: i.supplierId ?? undefined,
          supplierName: i.supplierName,
          poId: i.poId ?? undefined,
          poNo: i.poNo,
        });
      }
      if (canHold > 0) {
        out.push({
          key: n * 10 + 2,
          itemId: i.id,
          disposition: DISP.HOLD,
          quantity: canHold,
        });
      }
    });
    setRows(out);
  }, [data]);
  if (!data) return null;
  const set = (key: number, patch: Partial<Row>) =>
    setRows((s) => s.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  const holdCredit = rows
    .filter((r) => r.disposition === DISP.HOLD)
    .reduce(
      (s, r) =>
        s +
        Number(data.items.find((i) => i.id === r.itemId)?.unitCost ?? 0) *
          (r.quantity ?? 0),
      0,
    );
  const toSupplier = rows.some(
    (r) => r.disposition === DISP.SUPPLIER && r.quantity > 0,
  );
  const open = data.items.filter((i) => (i.receivedQty ?? 0) > i.disposedQty);
  return (
    <Modal
      open
      onCancel={onClose}
      width={880}
      title={`处理去向 · ${data.receiptNo}`}
      okText="保存"
      okButtonProps={{ loading: busy }}
      onOk={async () => {
        setBusy(true);
        try {
          await returnApi.dispose(
            data.id,
            rows.map(({ key: _k, supplierName: _s, poNo: _p, ...r }) => r),
          );
          message.success('去向已保存');
          onDone();
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
      destroyOnHidden
    >
      <Space orientation="vertical" size={14} style={{ width: '100%' }}>
        {open.map((i) => (
          <div key={i.id}>
            <div style={{ fontSize: 13, marginBottom: 8, color: palette.sub }}>
              <b style={{ color: palette.ink }}>{i.model}</b>　实收{' '}
              {i.receivedQty ?? 0} · 合格 {i.qualifiedQty ?? 0} · 未处理{' '}
              {(i.receivedQty ?? 0) - i.disposedQty}
              {i.unitCost != null &&
                ` · 原采购成本 ${formatAmount(i.unitCost, 'CNY')} / 个`}
            </div>
            <div
              style={{
                border: `1px solid ${palette.hairline}`,
                borderRadius: 12,
                padding: '4px 12px',
              }}
            >
              {rows
                .filter((r) => r.itemId === i.id)
                .map((r) => (
                  <div
                    key={r.key}
                    style={{
                      display: 'flex',
                      gap: 8,
                      alignItems: 'center',
                      flexWrap: 'wrap',
                      padding: '8px 0',
                    }}
                  >
                    <Select
                      value={r.disposition}
                      onChange={(v) =>
                        set(r.key, {
                          disposition: v,
                          ...(v === DISP.SUPPLIER
                            ? {
                                supplierId: i.supplierId ?? undefined,
                                supplierName: i.supplierName,
                                poId: i.poId ?? undefined,
                                poNo: i.poNo,
                              }
                            : {}),
                        })
                      }
                      options={DISP_OPTIONS}
                      style={{ width: 130 }}
                      aria-label="去向"
                    />
                    <InputNumber<number>
                      min={0}
                      precision={0}
                      value={r.quantity}
                      onChange={(v) => set(r.key, { quantity: v ?? 0 })}
                      style={{ width: 80 }}
                      aria-label="数量"
                    />
                    {r.disposition === DISP.SUPPLIER && (
                      <>
                        <div style={{ width: 190 }}>
                          <SupplierPicker
                            value={r.supplierId}
                            label={r.supplierName}
                            onChange={(id, name) =>
                              set(r.key, {
                                supplierId: id,
                                supplierName: name,
                                // 换了供应商就不再关联原采购订单
                                poId:
                                  id === i.supplierId
                                    ? (i.poId ?? undefined)
                                    : undefined,
                                poNo: id === i.supplierId ? i.poNo : undefined,
                              })
                            }
                          />
                        </div>
                        <span
                          style={{
                            width: 150,
                            fontSize: 13,
                            color: r.poNo ? palette.ink : palette.mute,
                          }}
                          title="关联的采购订单（原订单型号的采购订单）"
                        >
                          {r.poNo ?? '不关联采购订单'}
                        </span>
                      </>
                    )}
                    <Input
                      value={r.remark}
                      maxLength={300}
                      onChange={(e) => set(r.key, { remark: e.target.value })}
                      placeholder={
                        r.disposition === DISP.SCRAP ? '报废原因' : '说明'
                      }
                      style={{ flex: 1, minWidth: 140 }}
                      aria-label="说明"
                    />
                    <Button
                      type="text"
                      icon={<DeleteOutlined />}
                      aria-label="删除这一行"
                      onClick={() =>
                        setRows((s) => s.filter((x) => x.key !== r.key))
                      }
                    />
                  </div>
                ))}
            </div>
            <Button
              type="link"
              size="small"
              icon={<PlusOutlined />}
              style={{ paddingLeft: 0, marginTop: 6 }}
              onClick={() =>
                setRows((s) => [
                  ...s,
                  {
                    key: Date.now(),
                    itemId: i.id,
                    disposition: DISP.SCRAP,
                    quantity: 0,
                  },
                ])
              }
            >
              拆分到其他去向
            </Button>
          </div>
        ))}
        {toSupplier && (
          <div
            style={{
              padding: '10px 14px',
              borderRadius: 10,
              background: palette.accentSoft,
              fontSize: 13,
              color: palette.sub,
            }}
          >
            <FileTextOutlined style={{ color: palette.link }} />{' '}
            保存后生成采购退货单（待寄出），快递、运费与供应商退款在采购退货单上登记
          </div>
        )}
        {holdCredit > 0 && (
          <div style={{ fontSize: 13, color: palette.sub }}>
            转闲置冲减原订单 {data.soNo} 成本{' '}
            <b style={{ color: palette.green }}>
              {formatAmount(holdCredit, 'CNY')}
            </b>
          </div>
        )}
      </Space>
    </Modal>
  );
};
