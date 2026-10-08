import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  DatePicker,
  Drawer,
  Input,
  InputNumber,
  Modal,
  Radio,
  Select,
} from 'antd';
import dayjs from 'dayjs';
import React, { useEffect, useMemo, useState } from 'react';
import { CURRENCIES } from '@/pages/quotation/components';
import { getUserList } from '@/pages/system/user/service';
import { searchCustomers } from '@/services/zhul/masterdata';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { KIND, Pill } from '../components';
import {
  type CreateOrderLine,
  type Order,
  orderApi,
  type Pi,
  piApi,
  readBizError,
} from '../service';

export const STOCK = { SPOT: 1, FUTURES: 2 } as const;

export const StockPill: React.FC<{ type?: number }> = ({ type }) =>
  type === STOCK.FUTURES ? (
    <Pill tone="orange">期货</Pill>
  ) : (
    <Pill tone="green">现货</Pill>
  );

/** 本租户用户（采购员候选） */
export const useUserOptions = () => {
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  useEffect(() => {
    getUserList({ current: 1, pageSize: 200 })
      .then((res) =>
        setUsers(res.data.records.map((u) => ({ value: u.id, label: u.name }))),
      )
      .catch(() => setUsers([]));
  }, []);
  return users;
};

const today = () => dayjs().endOf('day');

// ---------------------------------------------------------------- 转成订单

/** 销售日期默认取最早的有效水单付款日期或到账日期，没有时取当天 */
const defaultSalesDate = (pi: Pi) => {
  const dates = pi.receipts
    .filter((r) => r.status === 1 && r.receiptDate)
    .map((r) => r.receiptDate as string)
    .sort();
  return dates.length ? dayjs(dates[0]) : dayjs();
};

export const ConvertOrderModal: React.FC<{
  pi?: Pi;
  open: boolean;
  onClose: () => void;
  onDone: (order: Order) => void;
}> = ({ pi, open, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [date, setDate] = useState<dayjs.Dayjs | null>(dayjs());
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (open && pi) setDate(defaultSalesDate(pi));
  }, [open, pi]);

  if (!pi) return null;
  const cur = pi.currencyCode;
  const slips = pi.receipts.filter(
    (r) => r.kind === KIND.SLIP && r.status === 1,
  );

  const submit = async () => {
    if (!date) return;
    setBusy(true);
    try {
      onDone(await piApi.convert(pi.id, date.format('YYYY-MM-DD')));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={560}
      title={`转成订单 · ${pi.piNo}`}
      okText="转成订单"
      okButtonProps={{ disabled: !date, loading: busy }}
      onOk={submit}
    >
      <div
        style={{
          padding: 14,
          borderRadius: 12,
          background: palette.inset,
          marginBottom: 16,
          fontSize: 13,
          display: 'grid',
          gap: 6,
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
          <span style={{ color: palette.sub }}>
            合计（Rev.{pi.currentVersionNo}）
          </span>
          <b>{formatAmount(pi.version.totalAmount, cur)}</b>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
          <span style={{ color: palette.sub }}>已到账</span>
          <b>{formatAmount(pi.receivedAmount, cur)}</b>
        </div>
        {slips.map((s) => (
          <div
            key={s.id}
            style={{ display: 'flex', justifyContent: 'space-between' }}
          >
            <span style={{ color: palette.sub }}>水单</span>
            <b>
              {formatAmount(s.amount, cur)} · {s.receiptDate?.slice(5)} 付款
            </b>
          </div>
        ))}
      </div>
      <div style={{ marginBottom: 6, fontSize: 13, color: palette.sub }}>
        销售日期 <span style={{ color: palette.red }}>*</span>
      </div>
      <DatePicker
        style={{ width: '100%' }}
        value={date}
        onChange={setDate}
        disabledDate={(d) => d.isAfter(today())}
        aria-label="销售日期"
      />
      <div style={{ fontSize: 12, color: palette.mute, margin: '6px 0 14px' }}>
        默认取最早的水单付款日期或到账日期；统计按销售日期
      </div>
      <div style={{ fontSize: 13, color: palette.sub, lineHeight: 1.8 }}>
        转成订单后：型号按货期带出现货 /
        期货，采购员取被选为采购成本价的那条回价的询价人；订单状态「待采购」；PI
        锁定，来源报价单按成交的型号推进。成交内容不能改，只能取消订单。
      </div>
    </Modal>
  );
};

// ---------------------------------------------------------------- 手动创建订单

interface Line extends Partial<CreateOrderLine> {
  key: number;
}

let lineSeq = 0;
const newLine = (): Line => ({
  key: ++lineSeq,
  quantity: 1,
  stockType: STOCK.SPOT,
});

export const CreateOrderDrawer: React.FC<{
  open: boolean;
  onClose: () => void;
  onDone: (order: Order) => void;
}> = ({ open, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const users = useUserOptions();
  const [customerId, setCustomerId] = useState<number>();
  const [customerOptions, setCustomerOptions] = useState<
    { value: number; label: string }[]
  >([]);
  const [currency, setCurrency] = useState('USD');
  const [date, setDate] = useState<dayjs.Dayjs | null>(dayjs());
  const [lines, setLines] = useState<Line[]>([newLine()]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    setCustomerId(undefined);
    setCurrency('USD');
    setDate(dayjs());
    setLines([newLine()]);
    searchCustomers('')
      .then((list) => setCustomerOptions(toOptions(list)))
      .catch(() => undefined);
  }, [open]);

  const toOptions = (
    list: {
      id: number;
      name: string;
      displayName?: string;
      country?: string;
    }[],
  ) =>
    list.map((c) => ({
      value: c.id,
      label: [c.displayName || c.name, c.country].filter(Boolean).join(' · '),
    }));

  const patch = (key: number, v: Partial<Line>) =>
    setLines((ls) => ls.map((l) => (l.key === key ? { ...l, ...v } : l)));

  const amountOf = (l: Line) =>
    Math.round((l.unitPrice ?? 0) * (l.quantity ?? 0) * 100) / 100;
  const total = lines.reduce((s, l) => s + amountOf(l), 0);
  const filled = lines.filter((l) => l.model?.trim());
  const ready =
    !!customerId &&
    !!date &&
    filled.length > 0 &&
    filled.every((l) => (l.quantity ?? 0) > 0 && l.unitPrice != null);

  const submit = async () => {
    if (!customerId || !date) return;
    setBusy(true);
    try {
      const order = await orderApi.create({
        customerId,
        currencyCode: currency,
        salesDate: date.format('YYYY-MM-DD'),
        items: filled.map((l) => ({
          model: (l.model ?? '').trim(),
          brand: l.brand?.trim() || undefined,
          quantity: l.quantity ?? 1,
          unitPrice: l.unitPrice ?? 0,
          costPrice: l.costPrice ?? null,
          purchaserId: l.purchaserId ?? null,
          stockType: l.stockType,
        })),
      });
      message.success(`已创建销售订单 ${order.soNo}`);
      onDone(order);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusy(false);
    }
  };

  const head = (text: string, width: number | string, required = false) => (
    <div style={{ width, fontSize: 12, color: palette.mute, flexShrink: 0 }}>
      {text}
      {required && <span style={{ color: palette.red }}> *</span>}
    </div>
  );

  return (
    <Drawer
      open={open}
      onClose={onClose}
      size="min(1040px, 96vw)"
      title="手动创建订单"
      footer={
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div>
            <div style={{ fontSize: 12, color: palette.mute }}>合计</div>
            <b style={{ fontSize: 20, fontVariantNumeric: 'tabular-nums' }}>
              {formatAmount(total, currency)}
            </b>
          </div>
          <span style={{ marginLeft: 'auto' }} />
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            disabled={!ready}
            loading={busy}
            onClick={submit}
          >
            创建订单
          </Button>
        </div>
      }
    >
      <div style={{ color: palette.mute, fontSize: 13, marginBottom: 16 }}>
        没有走报价单和 PI
        的订单，只填必填项；建好后成交内容不能改，收款在订单详情登记。
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 1fr) 140px 180px',
          gap: 12,
          marginBottom: 20,
        }}
      >
        <div>
          {head('客户', 'auto', true)}
          <Select
            showSearch={{
              filterOption: false,
              onSearch: (kw) =>
                searchCustomers(kw)
                  .then((list) => setCustomerOptions(toOptions(list)))
                  .catch(() => undefined),
            }}
            style={{ width: '100%', marginTop: 6 }}
            placeholder="搜索客户名称"
            value={customerId}
            onChange={setCustomerId}
            options={customerOptions}
            aria-label="客户"
          />
        </div>
        <div>
          {head('币种', 'auto', true)}
          <Select
            style={{ width: '100%', marginTop: 6 }}
            value={currency}
            onChange={setCurrency}
            options={CURRENCIES.map((c) => ({ value: c, label: c }))}
            aria-label="币种"
          />
        </div>
        <div>
          {head('销售日期', 'auto', true)}
          <DatePicker
            style={{ width: '100%', marginTop: 6 }}
            value={date}
            onChange={setDate}
            disabledDate={(d) => d.isAfter(today())}
            aria-label="销售日期"
          />
        </div>
      </div>
      <b style={{ display: 'block', marginBottom: 8 }}>
        型号 <span style={{ color: palette.red }}>*</span>
      </b>
      <div style={{ display: 'flex', gap: 8, marginBottom: 6 }}>
        <div
          style={{ flex: 1, minWidth: 0, fontSize: 12, color: palette.mute }}
        >
          型号<span style={{ color: palette.red }}> *</span>
        </div>
        {head('品牌', 110)}
        {head('数量', 80, true)}
        {head(`单价（${currency}）`, 120, true)}
        {head('采购成本价（CNY）', 130)}
        {head('采购员', 110)}
        {head('现货 / 期货', 96)}
        {head('小计', 110)}
        <div style={{ width: 24 }} />
      </div>
      <div style={{ display: 'grid', gap: 8 }}>
        {lines.map((l) => (
          <div
            key={l.key}
            style={{ display: 'flex', gap: 8, alignItems: 'center' }}
          >
            <Input
              style={{ flex: 1, minWidth: 0 }}
              placeholder="输入型号"
              value={l.model}
              onChange={(e) => patch(l.key, { model: e.target.value })}
              aria-label="型号"
            />
            <Input
              style={{ width: 110 }}
              placeholder="选填"
              value={l.brand}
              onChange={(e) => patch(l.key, { brand: e.target.value })}
            />
            <InputNumber
              style={{ width: 80 }}
              min={1}
              precision={0}
              value={l.quantity}
              onChange={(v) => patch(l.key, { quantity: v ?? undefined })}
              aria-label="数量"
            />
            <InputNumber
              style={{ width: 120 }}
              min={0}
              precision={2}
              value={l.unitPrice}
              onChange={(v) => patch(l.key, { unitPrice: v ?? undefined })}
              aria-label="单价"
            />
            <InputNumber
              style={{ width: 130 }}
              min={0}
              precision={2}
              placeholder="选填"
              value={l.costPrice}
              onChange={(v) => patch(l.key, { costPrice: v })}
            />
            <Select
              style={{ width: 110 }}
              allowClear
              placeholder="选填"
              value={l.purchaserId ?? undefined}
              onChange={(v) => patch(l.key, { purchaserId: v ?? null })}
              options={users}
              showSearch={{ optionFilterProp: 'label' }}
            />
            <Select
              style={{ width: 96 }}
              value={l.stockType}
              onChange={(v) => patch(l.key, { stockType: v })}
              options={[
                { value: STOCK.SPOT, label: '现货' },
                { value: STOCK.FUTURES, label: '期货' },
              ]}
            />
            <b
              style={{
                width: 110,
                textAlign: 'right',
                fontVariantNumeric: 'tabular-nums',
              }}
            >
              {formatAmount(amountOf(l), currency)}
            </b>
            <Button
              type="text"
              size="small"
              icon={<DeleteOutlined />}
              disabled={lines.length === 1}
              onClick={() =>
                setLines((ls) => ls.filter((x) => x.key !== l.key))
              }
              aria-label="删除型号"
            />
          </div>
        ))}
      </div>
      <Button
        type="link"
        icon={<PlusOutlined />}
        style={{ paddingLeft: 0, marginTop: 8 }}
        onClick={() => setLines((ls) => [...ls, newLine()])}
      >
        添加型号
      </Button>
      <Alert
        type="info"
        showIcon
        style={{ marginTop: 16 }}
        title="新 / 老客户自动判断：这个客户之前有有效订单的记为老客户。汇率取当前系统汇率。"
      />
    </Drawer>
  );
};

// ---------------------------------------------------------------- 更新进度 / 指定采购员 / 现货期货 / 销售日期

export const ProgressModal: React.FC<{
  order?: Order;
  itemIds: number[];
  open: boolean;
  onClose: () => void;
  onDone: (order: Order) => void;
}> = ({ order, itemIds, open, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [code, setCode] = useState<string>();
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const selected = useMemo(
    () => (order?.items ?? []).filter((i) => itemIds.includes(i.id)),
    [order, itemIds],
  );
  const currents = [...new Set(selected.map((i) => i.progressCode))];

  useEffect(() => {
    if (!open || !order) return;
    // 默认选当前进度的下一步
    const idx = Math.max(
      ...selected.map((i) =>
        order.steps.findIndex((s) => s.code === i.progressCode),
      ),
    );
    const next = order.steps.slice(idx + 1).find((s) => s.enabled);
    setCode(next?.code);
    setNote('');
  }, [open, order, selected]);

  if (!order) return null;
  const rank = (c: string) => order.steps.findIndex((s) => s.code === c);
  const slowest = order.items
    .map((i) => (itemIds.includes(i.id) && code ? code : i.progressCode))
    .reduce((a, b) => (rank(a) <= rank(b) ? a : b));
  const slowestName = order.steps.find((s) => s.code === slowest)?.name;

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={520}
      title={`更新进度 · ${selected.length} 个型号`}
      okText="更新"
      okButtonProps={{ disabled: !code, loading: busy }}
      onOk={async () => {
        if (!code) return;
        setBusy(true);
        try {
          onDone(
            await orderApi.progress(
              order.id,
              itemIds,
              code,
              note.trim() || undefined,
            ),
          );
          message.success('进度已更新');
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
    >
      <div style={{ color: palette.sub, fontSize: 13, marginBottom: 12 }}>
        {selected.map((i) => i.model).join('、')} 当前「
        {selected
          .map((i) => i.progressName)
          .filter((v, n, a) => a.indexOf(v) === n)
          .join(' / ')}
        」，更新到：
      </div>
      <Radio.Group
        value={code}
        onChange={(e) => setCode(e.target.value)}
        style={{ display: 'grid', gap: 8, width: '100%' }}
      >
        {order.steps
          .filter((s) => s.enabled)
          .map((s) => (
            <Radio key={s.code} value={s.code}>
              {s.name}
              {currents.length === 1 && currents[0] === s.code && (
                <span style={{ color: palette.mute, marginLeft: 8 }}>当前</span>
              )}
              {currents.length > 0 &&
                rank(s.code) < Math.min(...currents.map(rank)) && (
                  <span style={{ color: palette.mute, marginLeft: 8 }}>
                    退回
                  </span>
                )}
            </Radio>
          ))}
      </Radio.Group>
      <div style={{ margin: '14px 0 6px', fontSize: 13, color: palette.sub }}>
        说明（可选）
      </div>
      <Input
        value={note}
        maxLength={200}
        placeholder="如 供应商 10-09 到货，已验"
        onChange={(e) => setNote(e.target.value)}
      />
      {code && (
        <Alert
          type="info"
          showIcon
          style={{ marginTop: 14 }}
          title={`订单状态取所有型号中最靠前的进度：更新后本单为「${slowestName}」`}
        />
      )}
    </Modal>
  );
};

export const PurchaserModal: React.FC<{
  order?: Order;
  itemIds: number[];
  open: boolean;
  onClose: () => void;
  onDone: (order: Order) => void;
}> = ({ order, itemIds, open, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const users = useUserOptions();
  const [userId, setUserId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const selected = (order?.items ?? []).filter((i) => itemIds.includes(i.id));

  useEffect(() => {
    if (open) setUserId(null);
  }, [open]);

  if (!order) return null;
  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={480}
      title={`指定采购员 · ${selected.length} 个型号`}
      okText="保存"
      okButtonProps={{ loading: busy }}
      onOk={async () => {
        setBusy(true);
        try {
          onDone(await orderApi.purchaser(order.id, itemIds, userId));
          message.success('采购员已更新');
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
    >
      <Select
        style={{ width: '100%' }}
        allowClear
        placeholder="选择采购员；清空表示未指定"
        value={userId ?? undefined}
        onChange={(v) => setUserId(v ?? null)}
        options={users}
        showSearch={{ optionFilterProp: 'label' }}
        aria-label="采购员"
      />
      <div style={{ marginTop: 12, fontSize: 13, color: palette.sub }}>
        原采购员：
        {selected
          .map((i) => `${i.model} ${i.purchaserName ?? '未指定'}`)
          .join(' · ')}
      </div>
      <div style={{ marginTop: 8, fontSize: 12, color: palette.mute }}>
        转订单时默认取被选为采购成本价的那条回价的询价人；这里改了只影响订单，不改原回价。
      </div>
    </Modal>
  );
};

export const SalesDateModal: React.FC<{
  order?: Order;
  open: boolean;
  onClose: () => void;
  onDone: (order: Order) => void;
}> = ({ order, open, onClose, onDone }) => {
  const { message } = App.useApp();
  const [date, setDate] = useState<dayjs.Dayjs | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (open) setDate(order?.salesDate ? dayjs(order.salesDate) : dayjs());
  }, [open, order]);

  if (!order) return null;
  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={400}
      title="修改销售日期"
      okButtonProps={{ disabled: !date, loading: busy }}
      onOk={async () => {
        if (!date) return;
        setBusy(true);
        try {
          onDone(await orderApi.salesDate(order.id, date.format('YYYY-MM-DD')));
          message.success('销售日期已修改');
        } catch (e) {
          message.error(readBizError(e).message);
        } finally {
          setBusy(false);
        }
      }}
    >
      <DatePicker
        style={{ width: '100%' }}
        value={date}
        onChange={setDate}
        disabledDate={(d) => d.isAfter(today())}
        aria-label="销售日期"
      />
    </Modal>
  );
};
