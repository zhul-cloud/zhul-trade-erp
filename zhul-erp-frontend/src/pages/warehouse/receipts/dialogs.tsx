import {
  CameraOutlined,
  CheckOutlined,
  InfoCircleOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import {
  App,
  Button,
  DatePicker,
  Drawer,
  Input,
  InputNumber,
  Select,
  Skeleton,
  Table,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useRef, useState } from 'react';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import {
  AttachmentWall,
  DiffStatusPill,
  DiffTypePill,
  ReasonModal,
  ReceiptStatusPill,
  sub,
  trackingText,
} from '../components';
import {
  type Attachment,
  type QtyLine,
  type ReceiptDetail,
  type ReceivableOrder,
  readBizError,
  receiptApi,
  type ShipmentDetail,
} from '../service';

// ---------------------------------------------------------------- 数量行

interface Row {
  key: number;
  model: string;
  brand?: string;
  category?: string;
  /** 发货数量；直接收货时为未发数量 */
  shipped: number;
  qty: QtyLine;
}

/** 将生成的差异：少发、不良、多发 */
const diffText = (shipped: number, q: QtyLine) => {
  const parts: string[] = [];
  if (q.receivedQty < shipped) parts.push(`少发 ${shipped - q.receivedQty}`);
  if (q.defectiveQty > 0) parts.push(`不良 ${q.defectiveQty}`);
  if (q.receivedQty > shipped) parts.push(`多发 ${q.receivedQty - shipped}`);
  return parts.join('、');
};

/** 合格不能多于实收（不良 = 实收 − 合格，自动算） */
const lineBad = (q: QtyLine) => q.qualifiedQty > q.receivedQty;

const withDefective = (q: QtyLine): QtyLine => ({
  ...q,
  defectiveQty: Math.max(0, q.receivedQty - q.qualifiedQty),
});

const QtyRows: React.FC<{
  rows: Row[];
  shippedLabel: string;
  purchaser?: string;
  onChange: (key: number, q: QtyLine) => void;
}> = ({ rows, shippedLabel, purchaser, onChange }) => {
  const { palette } = useAppTheme();
  const num = (
    label: string,
    value: number,
    set: (n: number) => void,
    bad?: boolean,
  ) => (
    <div>
      <div style={{ fontSize: 12, color: palette.mute, marginBottom: 4 }}>
        {label}
      </div>
      <InputNumber
        min={0}
        precision={0}
        value={value}
        status={bad ? 'error' : undefined}
        onChange={(n) => set(n ?? 0)}
        style={{ width: 84 }}
        aria-label={label}
      />
    </div>
  );
  return (
    <div style={{ display: 'grid', gap: 8 }}>
      {rows.map((r) => {
        const q = r.qty;
        const bad = lineBad(q);
        const diff = diffText(r.shipped, q);
        return (
          <div
            key={r.key}
            style={{
              padding: '12px 14px',
              borderRadius: 10,
              background: palette.inset,
            }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'flex-end',
                gap: 12,
                flexWrap: 'wrap',
              }}
            >
              <div style={{ flex: 1, minWidth: 160 }}>
                <b style={{ color: palette.ink }}>{r.model}</b>
                {sub(
                  palette.mute,
                  [r.brand, r.category].filter(Boolean).join(' · ') || '—',
                )}
              </div>
              <span
                style={{ fontSize: 12, color: palette.sub, paddingBottom: 6 }}
              >
                {shippedLabel} {r.shipped}
              </span>
              {num('实收', q.receivedQty, (n) =>
                // 不良数保持不变，合格跟着实收走（默认全部合格）
                onChange(
                  r.key,
                  withDefective({
                    ...q,
                    receivedQty: n,
                    qualifiedQty: Math.max(0, n - q.defectiveQty),
                  }),
                ),
              )}
              {num(
                '合格',
                q.qualifiedQty,
                (n) =>
                  onChange(r.key, withDefective({ ...q, qualifiedQty: n })),
                bad,
              )}
              <div>
                <div
                  style={{ fontSize: 12, color: palette.mute, marginBottom: 4 }}
                >
                  不良（自动）
                </div>
                <output
                  aria-label="不良"
                  style={{
                    display: 'block',
                    width: 84,
                    height: 32,
                    lineHeight: '32px',
                    padding: '0 11px',
                    borderRadius: 8,
                    background: palette.hover,
                    color: q.defectiveQty > 0 ? palette.red : palette.mute,
                    fontWeight: 600,
                  }}
                >
                  {bad ? '—' : q.defectiveQty}
                </output>
              </div>
            </div>
            {bad ? (
              <div style={{ marginTop: 8, fontSize: 12, color: palette.red }}>
                <WarningOutlined /> 合格数量不能多于实收
              </div>
            ) : (
              diff && (
                <div
                  style={{ marginTop: 8, fontSize: 12, color: palette.orange }}
                >
                  <WarningOutlined /> 将生成差异：{diff}
                  {purchaser
                    ? `（交给采购员${purchaser}处理）`
                    : '（交给采购员处理）'}
                </div>
              )
            )}
          </div>
        );
      })}
    </div>
  );
};

const Label: React.FC<{ children: React.ReactNode; extra?: string }> = ({
  children,
  extra,
}) => {
  const { palette } = useAppTheme();
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        margin: '18px 0 8px',
        color: palette.sub,
      }}
    >
      {children}
      <span style={{ flex: 1 }} />
      {extra && (
        <span style={{ fontSize: 12, color: palette.mute }}>{extra}</span>
      )}
    </div>
  );
};

const Footer: React.FC<{
  hint: string;
  busy: boolean;
  disabled: boolean;
  onCancel: () => void;
  onOk: () => void;
}> = ({ hint, busy, disabled, onCancel, onOk }) => {
  const { palette } = useAppTheme();
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <span style={{ fontSize: 12, color: palette.mute }}>
        <InfoCircleOutlined /> {hint}
      </span>
      <span style={{ flex: 1 }} />
      <Button onClick={onCancel}>取消</Button>
      <Button
        type="primary"
        icon={<CheckOutlined />}
        loading={busy}
        disabled={disabled}
        onClick={onOk}
      >
        确认入库
      </Button>
    </div>
  );
};

const HINT =
  '确认后生成入库单；合格的型号会生成拍摄任务，订单型号的合格数量到齐后自动变「已入库」';

// ---------------------------------------------------------------- 验收入库

export const AcceptDrawer: React.FC<{
  shipmentId?: number;
  onClose: () => void;
  onDone: (d: ReceiptDetail) => void;
}> = ({ shipmentId, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [ship, setShip] = useState<ShipmentDetail>();
  const [rows, setRows] = useState<Row[]>([]);
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [note, setNote] = useState('');
  const [files, setFiles] = useState<Attachment[]>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (shipmentId === undefined) return;
    setShip(undefined);
    setDate(dayjs());
    setNote('');
    setFiles([]);
    receiptApi
      .shipment(shipmentId)
      .then((d) => {
        setShip(d);
        setRows(
          d.shipment.items.map((i) => ({
            key: i.id,
            model: i.model,
            brand: i.brand,
            category: i.category,
            shipped: i.quantity,
            qty: {
              receivedQty: i.quantity,
              qualifiedQty: i.quantity,
              defectiveQty: 0,
            },
          })),
        );
      })
      .catch((e) => message.error(readBizError(e).message));
  }, [shipmentId, message]);

  const s = ship?.shipment;
  return (
    <Drawer
      open={shipmentId !== undefined}
      onClose={onClose}
      size="min(860px, 96vw)"
      destroyOnHidden
      title={
        <span>
          验收入库
          {s && (
            <span
              style={{
                marginLeft: 10,
                fontSize: 13,
                fontWeight: 400,
                color: palette.mute,
              }}
            >
              {s.sdNo} · {s.poNo} · {s.supplierName} ·{' '}
              {trackingText(s.carrier, s.trackingNo)}
            </span>
          )}
        </span>
      }
      footer={
        <Footer
          hint={HINT}
          busy={busy}
          disabled={!s || rows.some((r) => lineBad(r.qty))}
          onCancel={onClose}
          onOk={async () => {
            if (!s) return;
            setBusy(true);
            try {
              const d = await receiptApi.accept({
                shipmentId: s.id,
                receivedDate: date.format('YYYY-MM-DD'),
                note: note.trim(),
                items: rows.map((r) => ({ shipmentItemId: r.key, ...r.qty })),
                attachmentIds: files.map((f) => f.id),
              });
              message.success(`已入库 ${d.receipt.grNo}`);
              onDone(d);
            } catch (e) {
              message.error(readBizError(e).message);
            } finally {
              setBusy(false);
            }
          }}
        />
      }
    >
      {!s || !ship ? (
        <Skeleton active />
      ) : (
        <>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <AttachmentWall
              ownerType="SHIPMENT"
              value={ship.attachments}
              empty="采购员没有上传发货图片"
            />
            {ship.attachments.length > 0 && (
              <span style={{ fontSize: 12, color: palette.mute }}>
                采购员登记的发货图片和视频
              </span>
            )}
          </div>
          {s.note && sub(palette.mute, `发货备注：${s.note}`)}
          <Label>收货日期</Label>
          <DatePicker
            value={date}
            allowClear={false}
            disabledDate={(d) => d.isAfter(dayjs(), 'day')}
            onChange={(d) => d && setDate(d)}
            style={{ width: 220 }}
            aria-label="收货日期"
          />
          <Label extra="只填实收和合格，不良 = 实收 − 合格 自动算；实收可以多于发货数">
            逐个型号核对
          </Label>
          <QtyRows
            rows={rows}
            shippedLabel="发货"
            purchaser={s.purchaserName}
            onChange={(key, qty) =>
              setRows((list) =>
                list.map((r) => (r.key === key ? { ...r, qty } : r)),
              )
            }
          />
          <Label>差异说明</Label>
          <Input
            value={note}
            maxLength={300}
            onChange={(e) => setNote(e.target.value)}
            placeholder="如：2 个外壳有裂纹，少了 2 个"
            aria-label="差异说明"
          />
          <Label extra="选填">
            <CameraOutlined style={{ color: palette.link }} />
            <b style={{ color: palette.ink }}>验收照片与视频</b>
          </Label>
          <AttachmentWall
            ownerType="RECEIPT"
            value={files}
            onChange={setFiles}
          />
        </>
      )}
    </Drawer>
  );
};

// ---------------------------------------------------------------- 直接收货

export const DirectReceiveDrawer: React.FC<{
  open: boolean;
  onClose: () => void;
  onDone: (d: ReceiptDetail) => void;
}> = ({ open, onClose, onDone }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [options, setOptions] = useState<ReceivableOrder[]>([]);
  const [searching, setSearching] = useState(false);
  const [po, setPo] = useState<ReceivableOrder>();
  const [rows, setRows] = useState<Row[]>([]);
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [note, setNote] = useState('');
  const [files, setFiles] = useState<Attachment[]>([]);
  const [busy, setBusy] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const search = (keyword: string) => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(async () => {
      setSearching(true);
      try {
        setOptions(await receiptApi.orders(keyword.trim() || undefined));
      } catch {
        setOptions([]);
      } finally {
        setSearching(false);
      }
    }, 250);
  };

  useEffect(() => {
    if (!open) return;
    setPo(undefined);
    setRows([]);
    setDate(dayjs());
    setNote('');
    setFiles([]);
    search('');
  }, [open]);

  const pick = (id: number) => {
    const p = options.find((o) => o.poId === id);
    setPo(p);
    setRows(
      (p?.lines ?? []).map((l) => ({
        key: l.poItemId,
        model: l.model,
        brand: l.brand,
        category: l.category,
        shipped: l.unshippedQty,
        qty: { receivedQty: 0, qualifiedQty: 0, defectiveQty: 0 },
      })),
    );
  };

  const filled = rows.filter((r) => r.qty.receivedQty > 0);
  return (
    <Drawer
      open={open}
      onClose={onClose}
      size="min(860px, 96vw)"
      destroyOnHidden
      title="直接收货"
      footer={
        <Footer
          hint="没有发货单的货：系统补一张「仓库补登」的发货单再入库"
          busy={busy}
          disabled={
            !po || filled.length === 0 || rows.some((r) => lineBad(r.qty))
          }
          onCancel={onClose}
          onOk={async () => {
            if (!po) return;
            setBusy(true);
            try {
              const d = await receiptApi.direct({
                poId: po.poId,
                receivedDate: date.format('YYYY-MM-DD'),
                note: note.trim(),
                items: filled.map((r) => ({ poItemId: r.key, ...r.qty })),
                attachmentIds: files.map((f) => f.id),
              });
              message.success(`已入库 ${d.receipt.grNo}`);
              onDone(d);
            } catch (e) {
              message.error(readBizError(e).message);
            } finally {
              setBusy(false);
            }
          }}
        />
      }
    >
      <Label>采购单</Label>
      <Select
        showSearch={{ onSearch: search, filterOption: false }}
        loading={searching}
        value={po?.poId}
        onChange={pick}
        placeholder="按采购单号、采购对象、型号搜索已下单的采购单"
        style={{ width: '100%' }}
        notFoundContent={searching ? '搜索中…' : '没有找到已下单的采购单'}
        options={options.map((o) => ({
          value: o.poId,
          label: `${o.poNo} · ${o.supplierName ?? ''} · ${o.purchaserName ?? ''}`,
        }))}
        aria-label="采购单"
      />
      {po && (
        <>
          <Label>收货日期</Label>
          <DatePicker
            value={date}
            allowClear={false}
            disabledDate={(d) => d.isAfter(dayjs(), 'day')}
            onChange={(d) => d && setDate(d)}
            style={{ width: 220 }}
            aria-label="收货日期"
          />
          <Label extra="只填到了的型号；超出未发数量的部分计为多发">
            逐个型号核对
          </Label>
          <QtyRows
            rows={rows}
            shippedLabel="未发"
            purchaser={po.purchaserName}
            onChange={(key, qty) =>
              setRows((list) =>
                list.map((r) => (r.key === key ? { ...r, qty } : r)),
              )
            }
          />
          <Label>差异说明</Label>
          <Input
            value={note}
            maxLength={300}
            onChange={(e) => setNote(e.target.value)}
            placeholder="如：供应商没给快递单号"
            aria-label="差异说明"
          />
          <Label extra="选填">
            <CameraOutlined style={{ color: palette.link }} />
            <b style={{ color: palette.ink }}>验收照片与视频</b>
          </Label>
          <AttachmentWall
            ownerType="RECEIPT"
            value={files}
            onChange={setFiles}
          />
        </>
      )}
    </Drawer>
  );
};

// ---------------------------------------------------------------- 入库单详情

export const ReceiptView: React.FC<{
  id?: number;
  onClose: () => void;
  onChanged: () => void;
}> = ({ id, onClose, onChanged }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const access = useAccess();
  const canEdit = !!access['warehouse:receipt:create'];
  const [d, setD] = useState<ReceiptDetail>();
  const [error, setError] = useState<string>();
  const [reversing, setReversing] = useState(false);

  useEffect(() => {
    if (id === undefined) return;
    setD(undefined);
    setError(undefined);
    receiptApi
      .detail(id)
      .then(setD)
      .catch((e) => setError(readBizError(e).message));
  }, [id]);

  const r = d?.receipt;
  return (
    <Drawer
      open={id !== undefined}
      onClose={onClose}
      size="min(760px, 96vw)"
      destroyOnHidden
      title={r ? `入库单 ${r.grNo}` : '入库单'}
      extra={
        d?.reversible && canEdit ? (
          <Button danger onClick={() => setReversing(true)}>
            冲销
          </Button>
        ) : null
      }
    >
      {error ? (
        <ErrorHint message={error} onRetry={onClose} />
      ) : !r || !d ? (
        <Skeleton active />
      ) : (
        <div style={{ display: 'grid', gap: 16 }}>
          <div
            style={{
              display: 'flex',
              gap: 8,
              alignItems: 'center',
              flexWrap: 'wrap',
              color: palette.sub,
            }}
          >
            <ReceiptStatusPill value={r.status}>
              {r.statusName}
            </ReceiptStatusPill>
            {r.receivedDate} 收货 · {r.receivedByName} · 发货单 {r.sdNo} ·{' '}
            {r.poNo} · {r.supplierName}
          </div>
          {r.reverseReason && sub(palette.mute, `冲销原因：${r.reverseReason}`)}
          {r.note && sub(palette.sub, `差异说明：${r.note}`)}
          <Table
            rowKey="id"
            size="small"
            pagination={false}
            dataSource={d.items}
            columns={[
              {
                title: '型号',
                dataIndex: 'model',
                render: (v: string, x) => (
                  <div>
                    <b>{v}</b>
                    {sub(
                      palette.mute,
                      [x.brand, x.category].filter(Boolean).join(' · '),
                    )}
                  </div>
                ),
              },
              { title: '发货', dataIndex: 'shippedQty', width: 70 },
              { title: '实收', dataIndex: 'receivedQty', width: 70 },
              { title: '合格', dataIndex: 'qualifiedQty', width: 70 },
              { title: '不良', dataIndex: 'defectiveQty', width: 70 },
            ]}
          />
          {d.discrepancies.length > 0 && (
            <div>
              <b
                style={{
                  color: palette.ink,
                  display: 'block',
                  marginBottom: 8,
                }}
              >
                到货差异
              </b>
              <div style={{ display: 'grid', gap: 6 }}>
                {d.discrepancies.map((x) => (
                  <div
                    key={x.id}
                    style={{ display: 'flex', gap: 8, alignItems: 'center' }}
                  >
                    <DiffTypePill value={x.type}>
                      {x.typeName} {x.quantity}
                    </DiffTypePill>
                    <span style={{ color: palette.sub }}>{x.model}</span>
                    <DiffStatusPill value={x.status}>
                      {x.statusName}
                    </DiffStatusPill>
                    {x.resolutionName && (
                      <span style={{ color: palette.mute, fontSize: 12 }}>
                        {x.resolutionName}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
          <div>
            <b
              style={{ color: palette.ink, display: 'block', marginBottom: 8 }}
            >
              验收照片与视频
            </b>
            <AttachmentWall ownerType="RECEIPT" value={d.attachments} />
          </div>
          <div>
            <b
              style={{ color: palette.ink, display: 'block', marginBottom: 8 }}
            >
              发货图片与视频
            </b>
            <AttachmentWall
              ownerType="SHIPMENT"
              value={d.shipmentAttachments}
            />
          </div>
          {r.status === 1 && !d.reversible && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              <InfoCircleOutlined /> 差异已处理或拍摄已开始，不能再冲销。
            </div>
          )}
        </div>
      )}
      <ReasonModal
        open={reversing}
        title={`冲销入库单 ${r?.grNo ?? ''}`}
        description="录错时整张冲销：入库单作废，发货单回到「在途」，它的差异与拍摄任务一起作废，之后可以重新验收。"
        placeholder="如：数量录错"
        okText="冲销"
        danger
        onCancel={() => setReversing(false)}
        onOk={async (reason) => {
          if (!r) return;
          try {
            setD(await receiptApi.reverse(r.id, reason));
            setReversing(false);
            message.success('已冲销，发货单回到在途');
            onChanged();
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
    </Drawer>
  );
};
