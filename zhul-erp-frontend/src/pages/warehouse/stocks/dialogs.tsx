import {
  ExportOutlined,
  InfoCircleOutlined,
  RollbackOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  App,
  DatePicker,
  Input,
  InputNumber,
  Modal,
  Radio,
  Select,
  Skeleton,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useMemo, useState } from 'react';
import SupplierSelect from '@/pages/inquiry/my-tasks/SupplierSelect';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import {
  OO_TYPES,
  otherOutboundApi,
  type StockBatch,
  stockApi,
  WH_OWN,
  warehouseApi,
} from '../inventoryService';
import { readBizError } from '../service';

/** 出库对象：某仓库的某商品（从库存查询的批次发起时传入；从其他出库列表新建时在弹窗里选） */
export interface StockTarget {
  productId: number;
  model: string;
  brand?: string;
  warehouseId: number;
  warehouseName: string;
  warehouseType: number;
}

const key = (b: StockBatch) => `${b.batchId}:${b.stockStatus}`;

const Label: React.FC<{ children: React.ReactNode; hint?: string }> = ({
  children,
  hint,
}) => {
  const { palette } = useAppTheme();
  return (
    <div style={{ fontSize: 13, color: palette.sub, margin: '14px 0 6px' }}>
      {children}
      <span style={{ color: palette.red }}> *</span>
      {hint && (
        <span style={{ color: palette.mute, marginLeft: 6 }}>{hint}</span>
      )}
    </div>
  );
};

/** 批次与数量：每个有可出数量的批次一行，填要出的数量 */
const BatchPicker: React.FC<{
  batches?: StockBatch[];
  qty: Record<string, number | null>;
  onChange: (k: string, v: number | null) => void;
}> = ({ batches, qty, onChange }) => {
  const { palette } = useAppTheme();
  if (!batches) return <Skeleton active paragraph={{ rows: 2 }} />;
  const free = batches.filter((b) => b.free > 0);
  const reserved = batches.reduce((s, b) => s + b.reserved, 0);
  return (
    <>
      {free.length === 0 && (
        <div style={{ color: palette.mute, fontSize: 13 }}>没有可出的库存</div>
      )}
      <div style={{ display: 'grid', gap: 8 }}>
        {free.map((b) => (
          <div
            key={key(b)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              padding: '8px 12px',
              borderRadius: 10,
              border: `1px solid ${palette.hairline}`,
              background: palette.inset,
            }}
          >
            <span style={{ flex: 1, minWidth: 0, color: palette.ink }}>
              {b.batchNo} · {b.stockStatusName} · 可用 {b.free}
            </span>
            <InputNumber
              min={0}
              max={b.free}
              precision={0}
              value={qty[key(b)] ?? null}
              placeholder="0"
              aria-label={`${b.batchNo} ${b.stockStatusName} 数量`}
              onChange={(v) => onChange(key(b), v)}
              style={{ width: 90 }}
            />
          </div>
        ))}
      </div>
      {reserved > 0 && (
        <div style={{ color: palette.mute, fontSize: 12, marginTop: 6 }}>
          预占的 {reserved} 个不能出库；要出，先对订单部分取消或作废采购退货单
        </div>
      )}
    </>
  );
};

const linesOf = (
  batches: StockBatch[] | undefined,
  qty: Record<string, number | null>,
) =>
  (batches ?? [])
    .map((b) => ({ b, q: qty[key(b)] ?? 0 }))
    .filter((x) => x.q > 0);

const useBatches = (t?: { warehouseId: number; productId: number }) => {
  const { message } = App.useApp();
  const [batches, setBatches] = useState<StockBatch[]>();
  useEffect(() => {
    setBatches(undefined);
    if (!t) return;
    stockApi
      .batches(t.warehouseId, t.productId)
      .then(setBatches)
      .catch((e) => message.error(readBizError(e).message));
  }, [t?.warehouseId, t?.productId, message]);
  return batches;
};

/** 其他出库（原型 I7a）：报废、转样品（公司仓库）；货代丢失、货代损坏（货代仓） */
export const OtherOutboundModal: React.FC<{
  open: boolean;
  /** 从库存查询发起时传入；不传时在弹窗里选仓库与商品 */
  target?: StockTarget;
  onClose: () => void;
  onDone: () => void;
}> = ({ open, target, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [picked, setPicked] = useState<StockTarget>();
  const [warehouses, setWarehouses] =
    useState<{ value: number; label: string; type: number }[]>();
  const [products, setProducts] = useState<StockTarget[]>([]);
  const [type, setType] = useState<number>();
  const [qty, setQty] = useState<Record<string, number | null>>({});
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  const t = target ?? picked;
  const batches = useBatches(open ? t : undefined);
  const types = OO_TYPES.filter(
    (x) => !t || x.own === (t.warehouseType === WH_OWN),
  );

  useEffect(() => {
    if (!open) return;
    setPicked(undefined);
    setQty({});
    setDate(dayjs());
    setReason('');
    setType(undefined);
    if (!target)
      warehouseApi
        .list()
        .then((l) =>
          setWarehouses(
            l
              .filter((w) => w.status === 1)
              .map((w) => ({ value: w.id, label: w.name, type: w.type })),
          ),
        )
        .catch((e) => message.error(readBizError(e).message));
  }, [open, target, message]);
  useEffect(() => {
    setType(types[0]?.value);
    setQty({});
  }, [t?.warehouseId, t?.productId]);

  const [whId, setWhId] = useState<number>();
  const searchProducts = async (keyword?: string) => {
    if (!whId) return;
    const w = warehouses?.find((x) => x.value === whId);
    const res = await stockApi.page({
      warehouseId: whId,
      keyword,
      page: 1,
      pageSize: 20,
    });
    setProducts(
      res.records.map((r) => ({
        productId: r.productId,
        model: r.model,
        brand: r.brand,
        warehouseId: r.warehouseId,
        warehouseName: w?.label ?? r.warehouseName,
        warehouseType: r.warehouseType,
      })),
    );
  };
  useEffect(() => {
    setPicked(undefined);
    setProducts([]);
    searchProducts().catch(() => setProducts([]));
  }, [whId]);

  const lines = linesOf(batches, qty);
  const amount = lines.reduce((s, x) => s + x.q * Number(x.b.unitPriceCny), 0);

  const submit = async () => {
    if (!t) {
      message.warning('请选择仓库和商品');
      return;
    }
    if (!type) {
      message.warning('请选择出库类型');
      return;
    }
    if (lines.length === 0) {
      message.warning('请填写出库数量');
      return;
    }
    if (!reason.trim()) {
      message.warning('请填写原因');
      return;
    }
    setSaving(true);
    try {
      const r = await otherOutboundApi.create({
        outboundType: type,
        warehouseId: t.warehouseId,
        outboundDate: date.format('YYYY-MM-DD'),
        reason: reason.trim(),
        lines: lines.map((x) => ({
          batchId: x.b.batchId,
          stockStatus: x.b.stockStatus,
          quantity: x.q,
        })),
      });
      message.success(`已出库：${r.outboundNo}`);
      onDone();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title={
        <span>
          <ExportOutlined style={{ color: palette.red, marginRight: 8 }} />
          其他出库
        </span>
      }
      okText="确认出库"
      confirmLoading={saving}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
      width={600}
    >
      {target ? (
        <div style={{ color: palette.mute, fontSize: 13 }}>
          {[target.brand, target.model, target.warehouseName]
            .filter(Boolean)
            .join(' · ')}
        </div>
      ) : (
        <div style={{ display: 'flex', gap: 12 }}>
          <div style={{ flex: 1 }}>
            <Label>仓库</Label>
            <Select
              style={{ width: '100%' }}
              placeholder="选择仓库"
              loading={!warehouses}
              options={warehouses}
              value={whId}
              onChange={setWhId}
            />
          </div>
          <div style={{ flex: 2 }}>
            <Label>商品</Label>
            <Select
              style={{ width: '100%' }}
              placeholder={whId ? '搜索型号、品牌' : '先选仓库'}
              disabled={!whId}
              value={picked?.productId}
              options={products.map((p) => ({
                value: p.productId,
                label: [p.model, p.brand].filter(Boolean).join(' · '),
              }))}
              showSearch={{
                filterOption: false,
                onSearch: (v) =>
                  searchProducts(v.trim() || undefined).catch(() =>
                    setProducts([]),
                  ),
              }}
              onChange={(v) =>
                setPicked(products.find((p) => p.productId === v))
              }
              notFoundContent="这个仓库没有在库的商品"
            />
          </div>
        </div>
      )}
      {t && (
        <>
          <Label>出库类型</Label>
          <Radio.Group
            value={type}
            onChange={(e) => setType(e.target.value)}
            style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}
          >
            {types.map((x) => (
              <Radio
                key={x.value}
                value={x.value}
                style={{
                  padding: '10px 12px',
                  borderRadius: 10,
                  border: `1px solid ${type === x.value ? palette.link : palette.hairline}`,
                  background: type === x.value ? palette.accentSoft : undefined,
                  margin: 0,
                }}
              >
                <b style={{ color: palette.ink }}>{x.label}</b>
                <div style={{ fontSize: 12, color: palette.mute }}>
                  {x.hint}
                </div>
              </Radio>
            ))}
          </Radio.Group>
          <Label>批次与数量</Label>
          <BatchPicker
            batches={batches}
            qty={qty}
            onChange={(k, v) => setQty((q) => ({ ...q, [k]: v }))}
          />
          <div style={{ display: 'flex', gap: 12 }}>
            <div style={{ flex: 1 }}>
              <Label>出库日期</Label>
              <DatePicker
                style={{ width: '100%' }}
                value={date}
                allowClear={false}
                disabledDate={(d) => d.isAfter(dayjs(), 'day')}
                onChange={(d) => d && setDate(d)}
              />
            </div>
            <div style={{ flex: 1 }}>
              <div
                style={{
                  fontSize: 13,
                  color: palette.sub,
                  margin: '14px 0 6px',
                }}
              >
                金额
                <span style={{ color: palette.mute, marginLeft: 6 }}>
                  按批次单价
                </span>
              </div>
              <div
                style={{
                  height: 32,
                  display: 'flex',
                  alignItems: 'center',
                  color: palette.ink,
                  fontVariantNumeric: 'tabular-nums',
                }}
              >
                {formatAmount(amount, 'CNY')}
              </div>
            </div>
          </div>
          <Label>原因</Label>
          <Input
            value={reason}
            maxLength={200}
            placeholder="例如：外壳破损，无法销售"
            onChange={(e) => setReason(e.target.value)}
          />
        </>
      )}
    </Modal>
  );
};

/** 退回供应商（原型 I7b）：公司仓库的批次生成采购退货单（来源「库存退回」，待寄出） */
export const ReturnToSupplierModal: React.FC<{
  target?: StockTarget;
  onClose: () => void;
  onDone: () => void;
}> = ({ target, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const batches = useBatches(target);
  const [qty, setQty] = useState<Record<string, number | null>>({});
  const [supplier, setSupplier] = useState<{ id?: number; name: string }>({
    name: '',
  });
  const [remark, setRemark] = useState('');
  const [saving, setSaving] = useState(false);
  const source = useMemo(
    () => batches?.find((b) => b.supplierId != null),
    [batches],
  );

  useEffect(() => {
    setQty({});
    setRemark('');
    setSupplier({ name: '' });
  }, [target]);
  useEffect(() => {
    if (source)
      setSupplier({ id: source.supplierId, name: source.supplierName ?? '' });
  }, [source]);

  const lines = linesOf(batches, qty);
  const total = lines.reduce((s, x) => s + x.q, 0);
  const shop = source?.supplierId === 0;

  const submit = async () => {
    if (supplier.id == null) {
      message.warning('请选择供应商');
      return;
    }
    if (lines.length === 0) {
      message.warning('请填写退回数量');
      return;
    }
    if (!remark.trim()) {
      message.warning('请填写说明');
      return;
    }
    setSaving(true);
    try {
      const r = await stockApi.returnToSupplier({
        supplierId: supplier.id,
        poId: supplier.id === source?.supplierId ? source?.poId : undefined,
        remark: remark.trim(),
        lines: lines.map((x) => ({
          batchId: x.b.batchId,
          stockStatus: x.b.stockStatus,
          quantity: x.q,
        })),
      });
      message.success(`已生成采购退货单 ${r.returnNo}`);
      onDone();
      history.push(`/purchase/returns/${r.id}`);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={!!target}
      title={
        <span>
          <RollbackOutlined style={{ color: palette.link, marginRight: 8 }} />
          退回供应商
        </span>
      }
      okText="生成采购退货单"
      confirmLoading={saving}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
      width={600}
    >
      {target && (
        <>
          <div style={{ color: palette.mute, fontSize: 13 }}>
            {[target.brand, target.model, target.warehouseName]
              .filter(Boolean)
              .join(' · ')}
          </div>
          <Label hint={source ? '默认批次的采购订单供应商' : undefined}>
            供应商
          </Label>
          {shop ? (
            <Input
              disabled
              value={`${source?.supplierName ?? '线上店铺'}（${source?.poNo ?? ''}）`}
            />
          ) : (
            <SupplierSelect
              block
              value={supplier.id}
              label={
                supplier.id === source?.supplierId && source?.poNo
                  ? `${supplier.name}（${source.poNo}）`
                  : supplier.name
              }
              onChange={(id, name) => setSupplier({ id, name })}
            />
          )}
          <Label>批次与数量</Label>
          <BatchPicker
            batches={batches}
            qty={qty}
            onChange={(k, v) => setQty((q) => ({ ...q, [k]: v }))}
          />
          <Label>说明</Label>
          <Input
            value={remark}
            maxLength={200}
            placeholder="例如：多发暂存，供应商同意退回"
            onChange={(e) => setRemark(e.target.value)}
          />
          <div
            style={{
              display: 'flex',
              gap: 8,
              marginTop: 14,
              padding: '10px 12px',
              borderRadius: 10,
              background: palette.inset,
              color: palette.sub,
              fontSize: 12,
            }}
          >
            <InfoCircleOutlined style={{ color: palette.link, marginTop: 2 }} />
            <span>
              生成采购退货单（来源「库存退回」，待寄出），这 {total || ''}{' '}
              个显示为「退货待寄出」，不能再被取用；快递、运费和供应商退款在采购退货单上登记。
            </span>
          </div>
        </>
      )}
    </Modal>
  );
};
