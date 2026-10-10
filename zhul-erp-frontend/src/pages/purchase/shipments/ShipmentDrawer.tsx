import {
  CheckOutlined,
  InfoCircleOutlined,
  PictureOutlined,
  UnorderedListOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import {
  App,
  AutoComplete,
  Button,
  Checkbox,
  DatePicker,
  Drawer,
  Input,
  InputNumber,
  Segmented,
  Select,
  Skeleton,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import { type Forwarder, forwarderApi } from '@/pages/logistics/service';
import { AttachmentWall, sub } from '@/pages/warehouse/components';
import {
  type Attachment,
  readBizError,
  type ShipmentDetail,
  type ShipmentForm,
  shipmentApi,
} from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';

export const CARRIERS = [
  '顺丰',
  '京东',
  '中通',
  '圆通',
  '韵达',
  '申通',
  '极兔',
  '德邦',
  'EMS',
  '跨越',
  '供应商送货',
].map((value) => ({ value }));

interface Line {
  checked: boolean;
  quantity: number | null;
}

/** 登记（poId）或修改（shipmentId）供应商发货单 */
const ShipmentDrawer: React.FC<{
  open: boolean;
  poId?: number;
  shipmentId?: number;
  onClose: () => void;
  onSaved: (d: ShipmentDetail) => void;
}> = ({ open, poId, shipmentId, onClose, onSaved }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [form, setForm] = useState<ShipmentForm>();
  const [carrier, setCarrier] = useState('');
  const [trackingNo, setTrackingNo] = useState('');
  const [shipDate, setShipDate] = useState<Dayjs>(dayjs());
  const [arrival, setArrival] = useState<Dayjs | null>(null);
  /** 采购员手动改过预计到货：改快递公司、发货日期时不再覆盖 */
  const [arrivalTouched, setArrivalTouched] = useState(false);
  const [basis, setBasis] = useState<string>();
  const [note, setNote] = useState('');
  const [files, setFiles] = useState<Attachment[]>([]);
  const [lines, setLines] = useState<Record<number, Line>>({});
  const [saving, setSaving] = useState(false);
  /** 发到哪里：福州仓库，或直发某家货代 */
  const [direct, setDirect] = useState(false);
  const [forwarderId, setForwarderId] = useState<number>();
  const [forwarders, setForwarders] = useState<Forwarder[]>([]);

  useEffect(() => {
    if (!open) return;
    forwarderApi
      .list()
      .then(setForwarders)
      .catch(() => setForwarders([]));
  }, [open]);

  useEffect(() => {
    if (!open) return;
    setForm(undefined);
    shipmentApi
      .form({ poId, shipmentId })
      .then((f) => {
        setForm(f);
        setCarrier(f.carrier ?? '');
        setTrackingNo(f.trackingNo ?? '');
        setShipDate(f.shipDate ? dayjs(f.shipDate) : dayjs());
        setArrival(f.expectedArrivalDate ? dayjs(f.expectedArrivalDate) : null);
        setArrivalTouched(!!f.expectedArrivalDate);
        setBasis(undefined);
        setNote(f.note ?? '');
        setDirect(!!f.directForwarderId);
        setForwarderId(f.directForwarderId ?? undefined);
        setFiles(f.attachments);
        setLines(
          Object.fromEntries(
            f.lines.map((l) => [
              l.poItemId,
              {
                checked: l.quantity > 0,
                quantity: l.quantity || l.maxQuantity,
              },
            ]),
          ),
        );
      })
      .catch((e) => {
        message.error(readBizError(e).message);
        onClose();
      });
  }, [open, poId, shipmentId, message, onClose]);

  // 按快递时效估算预计到货：快递公司或发货日期变了就重新算（手动改过的不覆盖）
  const formPoId = form?.poId;
  useEffect(() => {
    if (!formPoId || arrivalTouched) return undefined;
    const t = setTimeout(() => {
      shipmentApi
        .estimate(formPoId, carrier.trim(), shipDate.format('YYYY-MM-DD'))
        .then((e) => {
          setArrival(e.date ? dayjs(e.date) : null);
          setBasis(e.basis);
        })
        .catch(() => setBasis(undefined));
    }, 300);
    return () => clearTimeout(t);
  }, [formPoId, carrier, shipDate, arrivalTouched]);

  const arrivalBad = !!arrival && arrival.isBefore(shipDate, 'day');
  const picked = form?.lines.filter((l) => lines[l.poItemId]?.checked) ?? [];
  const bad = picked.find((l) => {
    const q = lines[l.poItemId]?.quantity;
    return !q || q <= 0 || q > l.maxQuantity;
  });

  const submit = async () => {
    if (!form) return;
    setSaving(true);
    try {
      const body = {
        poId: form.poId,
        carrier: carrier.trim(),
        trackingNo: trackingNo.trim(),
        shipDate: shipDate.format('YYYY-MM-DD'),
        expectedArrivalDate: arrival ? arrival.format('YYYY-MM-DD') : null,
        note: note.trim(),
        directForwarderId: direct ? (forwarderId ?? null) : null,
        items: picked.map((l) => ({
          poItemId: l.poItemId,
          quantity: lines[l.poItemId]?.quantity ?? 0,
        })),
        attachmentIds: files.map((f) => f.id),
      };
      const d = shipmentId
        ? await shipmentApi.update(shipmentId, body)
        : await shipmentApi.create(body);
      message.success(shipmentId ? '已保存' : `已登记发货 ${d.shipment.sdNo}`);
      onSaved(d);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const section = (icon: React.ReactNode, title: string, hint?: string) => (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        margin: '20px 0 10px',
      }}
    >
      <span style={{ color: palette.link }}>{icon}</span>
      <b style={{ color: palette.ink }}>{title}</b>
      <span style={{ flex: 1 }} />
      {hint && (
        <span style={{ fontSize: 12, color: palette.mute }}>{hint}</span>
      )}
    </div>
  );

  return (
    <Drawer
      open={open}
      size="min(820px, 96vw)"
      onClose={onClose}
      destroyOnHidden
      title={
        <span>
          {shipmentId ? '修改发货单' : '登记供应商发货'}
          {form && (
            <span
              style={{
                marginLeft: 10,
                fontSize: 13,
                fontWeight: 400,
                color: palette.mute,
              }}
            >
              {form.poNo} · {form.supplierName}
            </span>
          )}
        </span>
      }
      footer={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{ fontSize: 12, color: palette.mute }}>
            <InfoCircleOutlined /> 一张采购单可以分几批发；线上店铺同样登记
          </span>
          <span style={{ flex: 1 }} />
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            icon={<CheckOutlined />}
            loading={saving}
            disabled={
              !form ||
              picked.length === 0 ||
              !!bad ||
              arrivalBad ||
              (direct && !forwarderId)
            }
            onClick={submit}
          >
            {shipmentId ? '保存' : '登记'}
          </Button>
        </div>
      }
    >
      {!form ? (
        <Skeleton active />
      ) : (
        <>
          <div style={{ marginBottom: 12 }}>
            <div style={{ color: palette.sub, marginBottom: 6 }}>发到哪里</div>
            <Segmented<'warehouse' | 'direct'>
              value={direct ? 'direct' : 'warehouse'}
              onChange={(v) => setDirect(v === 'direct')}
              options={[
                { value: 'warehouse', label: '福州仓库' },
                { value: 'direct', label: '直发货代' },
              ]}
            />
          </div>
          {direct && (
            <div style={{ marginBottom: 12 }}>
              <div style={{ color: palette.sub, marginBottom: 6 }}>
                货代 <span style={{ color: palette.red }}>*</span>
              </div>
              <Select
                value={forwarderId}
                onChange={setForwarderId}
                placeholder="选择货代"
                options={forwarders.map((f) => ({
                  value: f.id,
                  label: f.name,
                }))}
                style={{ width: '100%' }}
                aria-label="货代"
              />
            </div>
          )}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
              gap: 12,
            }}
          >
            <div>
              <div style={{ color: palette.sub, marginBottom: 6 }}>
                快递公司
              </div>
              <AutoComplete
                value={carrier}
                options={CARRIERS}
                onChange={setCarrier}
                placeholder="选择或输入，可留空"
                style={{ width: '100%' }}
                aria-label="快递公司"
              />
            </div>
            <div>
              <div style={{ color: palette.sub, marginBottom: 6 }}>
                快递单号
              </div>
              <Input
                value={trackingNo}
                maxLength={64}
                onChange={(e) => setTrackingNo(e.target.value)}
                placeholder="供应商自己送货可以留空"
                aria-label="快递单号"
              />
            </div>
            <div>
              <div style={{ color: palette.sub, marginBottom: 6 }}>
                发货日期
              </div>
              <DatePicker
                value={shipDate}
                allowClear={false}
                disabledDate={(d) => d.isAfter(dayjs(), 'day')}
                onChange={(d) => d && setShipDate(d)}
                style={{ width: '100%' }}
                aria-label="发货日期"
              />
            </div>
          </div>

          <div
            style={{
              display: 'flex',
              alignItems: 'flex-end',
              gap: 12,
              marginTop: 12,
              flexWrap: 'wrap',
            }}
          >
            <div style={{ width: 240 }}>
              <div style={{ color: palette.sub, marginBottom: 6 }}>
                预计到货日期
              </div>
              <DatePicker
                value={arrival}
                status={arrivalBad ? 'error' : undefined}
                disabledDate={(d) => d.isBefore(shipDate, 'day')}
                onChange={(d) => {
                  setArrival(d);
                  setArrivalTouched(!!d);
                }}
                placeholder="按快递时效估算"
                style={{ width: '100%' }}
                aria-label="预计到货日期"
              />
            </div>
            <div style={{ paddingBottom: 6, fontSize: 13 }}>
              {arrivalTouched ? (
                <span style={{ color: palette.mute }}>
                  已手动填写；
                  <a onClick={() => setArrivalTouched(false)}>重新估算</a>
                </span>
              ) : basis ? (
                <span>
                  <span style={{ color: palette.sub }}>{basis}</span>
                  <span style={{ color: palette.mute }}>
                    {' '}
                    · 按快递时效估算，可以改
                  </span>
                </span>
              ) : null}
            </div>
          </div>

          {section(
            <UnorderedListOutlined />,
            '本次发货的型号',
            shipmentId
              ? '最多为未发数量加本单原数量'
              : '数量默认为未发的数量，可以改小',
          )}
          {form.lines.length === 0 ? (
            <div style={{ color: palette.mute }}>所有型号都已发完</div>
          ) : (
            <div style={{ display: 'grid', gap: 8 }}>
              {form.lines.map((l) => {
                const line = lines[l.poItemId];
                const over =
                  line?.checked && (line.quantity ?? 0) > l.maxQuantity;
                return (
                  <div
                    key={l.poItemId}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 12,
                      padding: '10px 14px',
                      borderRadius: 10,
                      background: palette.inset,
                    }}
                  >
                    <Checkbox
                      checked={line?.checked}
                      onChange={(e) =>
                        setLines((m) => ({
                          ...m,
                          [l.poItemId]: {
                            ...m[l.poItemId],
                            checked: e.target.checked,
                          },
                        }))
                      }
                      aria-label={`发 ${l.model}`}
                    />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <b style={{ color: palette.ink }}>{l.model}</b>
                      {sub(
                        palette.mute,
                        [l.brand, l.category].filter(Boolean).join(' · ') ||
                          '—',
                      )}
                    </div>
                    <span style={{ fontSize: 12, color: palette.mute }}>
                      订购 {l.orderedQty} · 已发 {l.shippedQty} · 最多{' '}
                      {l.maxQuantity}
                    </span>
                    <InputNumber
                      min={1}
                      max={l.maxQuantity}
                      precision={0}
                      disabled={!line?.checked}
                      status={over ? 'error' : undefined}
                      value={line?.quantity}
                      onChange={(n) =>
                        setLines((m) => ({
                          ...m,
                          [l.poItemId]: { ...m[l.poItemId], quantity: n },
                        }))
                      }
                      style={{ width: 96 }}
                      aria-label={`${l.model} 发货数量`}
                    />
                  </div>
                );
              })}
            </div>
          )}

          {direct && (
            <div
              style={{
                marginTop: 12,
                padding: '10px 14px',
                borderRadius: 10,
                background: palette.orangeSoft,
                color: palette.orange,
                fontSize: 13,
              }}
            >
              <WarningOutlined />{' '}
              直发货代的货不进福州仓库、不拍摄；货代收到后，业务员在出运单上按实收确认数量并填箱规，系统自动补入库和出库。
            </div>
          )}

          {section(
            <PictureOutlined />,
            '发货图片与视频',
            '图片 10MB 内，视频 200MB 内',
          )}
          <AttachmentWall
            ownerType="SHIPMENT"
            value={files}
            onChange={setFiles}
          />

          <div style={{ color: palette.sub, margin: '20px 0 6px' }}>备注</div>
          <Input
            value={note}
            maxLength={300}
            onChange={(e) => setNote(e.target.value)}
            placeholder="如：供应商说 C 型号下周补发"
            aria-label="备注"
          />
        </>
      )}
    </Drawer>
  );
};

export default ShipmentDrawer;
