import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { request } from '@umijs/max';
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
  Skeleton,
} from 'antd';
import dayjs from 'dayjs';
import React, { useEffect, useMemo, useState } from 'react';
import { CURRENCIES } from '@/pages/quotation/components';
import { searchCustomers } from '@/services/zhul/masterdata';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { KIND, Pill, ReceiptStatusPill } from '../components';
import {
  type CreateOrderLine,
  type Order,
  type OrderCandidatePi,
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
/** 采购员候选：本租户启用的用户，有采购或销售订单菜单即可取，不要求「用户」菜单权限 */
export const useUserOptions = () => {
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  useEffect(() => {
    request<{ data: { id: number; name: string }[] }>(
      '/api/v1/purchase/purchasers',
      { method: 'GET', skipErrorHandler: true },
    )
      .then((res) =>
        setUsers(res.data.map((u) => ({ value: u.id, label: u.name }))),
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

/** 「待采购」「已下单」由采购单自动推进，手动推进不能选 */
const AUTO_STEPS = ['PENDING_PURCHASE', 'ORDERED'];

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
    const next = order.steps
      .slice(idx + 1)
      .find((s) => s.enabled && !AUTO_STEPS.includes(s.code));
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
          .filter((s) => s.enabled && !AUTO_STEPS.includes(s.code))
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
      <div style={{ marginTop: 10, fontSize: 12, color: palette.mute }}>
        「待采购」「已下单」由采购单自动推进，这里不能选；还有没下单数量的型号不能推进到后面的步骤。
      </div>
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

// ---------------------------------------------------------------- 新建销售订单：按 PI 创建 / 手动创建

type NewMode = 'pi' | 'manual';

/** 单选圆点（只做显示，点击由外层按钮处理） */
const RadioDot: React.FC<{ on: boolean }> = ({ on }) => {
  const { palette } = useAppTheme();
  return (
    <span
      aria-hidden
      style={{
        marginLeft: 'auto',
        width: 16,
        height: 16,
        borderRadius: 8,
        flex: 'none',
        boxSizing: 'border-box',
        border: `${on ? 5 : 1.5}px solid ${on ? palette.link : palette.mute}`,
        background: palette.card,
      }}
    />
  );
};

/**
 * 新建销售订单（参照新建报价单）：先选创建方式；按 PI 创建时从可以转订单的 PI 里选一张，
 * 再走与 PI 页「转成订单」相同的确认（填销售日期）；手动创建交给手动建单抽屉。
 */
export const NewOrderDrawer: React.FC<{
  open: boolean;
  /** 有 PI 菜单：可以按 PI 创建 */
  canFromPi: boolean;
  /** 有「手动创建订单」按钮权限 */
  canManual: boolean;
  onClose: () => void;
  onManual: () => void;
  onDone: (order: Order) => void;
}> = ({ open, canFromPi, canManual, onClose, onManual, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [step, setStep] = useState<1 | 2>(1);
  const [mode, setMode] = useState<NewMode>('pi');
  const [keyword, setKeyword] = useState('');
  const [list, setList] = useState<OrderCandidatePi[]>();
  const [count, setCount] = useState<number>();
  const [picked, setPicked] = useState<number>();
  const [loadingPi, setLoadingPi] = useState(false);
  const [pi, setPi] = useState<Pi>();

  useEffect(() => {
    if (!open) return;
    setStep(1);
    setMode(canFromPi ? 'pi' : 'manual');
    setKeyword('');
    setPicked(undefined);
    setPi(undefined);
    setCount(undefined);
    if (canFromPi) {
      orderApi
        .candidatePis()
        .then((l) => setCount(l.length))
        .catch(() => setCount(undefined));
    }
  }, [open, canFromPi]);

  useEffect(() => {
    if (!open || step !== 2) return undefined;
    const t = window.setTimeout(() => {
      orderApi
        .candidatePis(keyword.trim() || undefined)
        .then(setList)
        .catch((e) => {
          setList([]);
          message.error(readBizError(e).message);
        });
    }, 300);
    return () => window.clearTimeout(t);
  }, [open, step, keyword, message]);

  const next = async () => {
    if (step === 1) {
      if (mode === 'manual') {
        onManual();
        return;
      }
      setList(undefined);
      setStep(2);
      return;
    }
    if (!picked) return;
    setLoadingPi(true);
    try {
      setPi(await piApi.detail(picked));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoadingPi(false);
    }
  };

  const steps = ['选择创建方式', '选择 PI 或填写型号', '确认销售日期并创建'];
  const option = (
    key: NewMode,
    title: string,
    desc: string,
    bullets: string[],
    disabled: boolean,
  ) => {
    const on = mode === key;
    return (
      <button
        type="button"
        disabled={disabled}
        aria-pressed={on}
        onClick={() => setMode(key)}
        style={{
          all: 'unset',
          display: 'block',
          padding: '16px 18px',
          borderRadius: 14,
          cursor: disabled ? 'not-allowed' : 'pointer',
          opacity: disabled ? 0.5 : 1,
          background: on ? palette.accentSoft : palette.inset,
          border: `1.5px solid ${on ? palette.link : palette.hairline}`,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <b style={{ fontSize: 16, color: palette.ink }}>{title}</b>
          {disabled && <Pill tone="mute">没有权限</Pill>}
          <RadioDot on={on} />
        </div>
        <div style={{ fontSize: 12, color: palette.sub, margin: '4px 0 8px' }}>
          {desc}
        </div>
        {bullets.map((b) => (
          <div
            key={b}
            style={{ fontSize: 12, color: palette.sub, lineHeight: '22px' }}
          >
            <span
              style={{
                color: on ? palette.green : palette.mute,
                marginRight: 6,
              }}
            >
              ✓
            </span>
            {b}
          </div>
        ))}
      </button>
    );
  };

  const lastText = (p: OrderCandidatePi) => {
    if (!p.lastKind || p.lastAmount == null) return '';
    const what =
      p.lastKind === KIND.SLIP
        ? '水单'
        : p.lastPlatform
          ? (p.lastMethodName ?? '平台收款')
          : '到账';
    return `${what} ${formatAmount(p.lastAmount, p.currencyCode)} · ${(p.lastDate ?? '').slice(5)}`;
  };

  return (
    <>
      <Drawer
        open={open && !pi}
        onClose={onClose}
        size="min(880px, 96vw)"
        title="新建销售订单"
        footer={
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            {step === 2 && <Button onClick={() => setStep(1)}>上一步</Button>}
            <span style={{ marginLeft: 'auto' }} />
            <Button onClick={onClose}>取消</Button>
            <Button
              type="primary"
              loading={loadingPi}
              disabled={step === 2 && !picked}
              onClick={next}
            >
              {step === 2 ? '下一步：确认销售日期' : '下一步'}
            </Button>
          </div>
        }
      >
        <div
          style={{
            display: 'flex',
            gap: 8,
            alignItems: 'center',
            marginBottom: 16,
          }}
        >
          {steps.map((s, n) => (
            <React.Fragment key={s}>
              {n > 0 && <span style={{ color: palette.mute }}>›</span>}
              <Pill tone={n + 1 === step ? 'accent' : 'gray'}>
                {n + 1} {s}
              </Pill>
            </React.Fragment>
          ))}
        </div>
        {step === 1 ? (
          <div style={{ display: 'grid', gap: 12 }}>
            {canFromPi && count != null && (
              <b style={{ color: palette.ink }}>
                {count > 0
                  ? `你有 ${count} 张 PI 已收到水单或到账、还没有转成订单`
                  : '现在没有可以转订单的 PI（需要已发送、有水单或到账）'}
              </b>
            )}
            {option(
              'pi',
              '按 PI 创建',
              '从已发送、已有水单或到账的 PI 里选一张，带入买方、型号、费用、币种与汇率。客户走过报价单和 PI 的订单用这个。',
              [
                '只列可以转订单的 PI（已发送、有水单或到账、没有未发送的新版本）',
                '型号带出现货 / 期货与采购员',
                '与在 PI 上点「转成订单」完全相同',
              ],
              !canFromPi,
            )}
            {option(
              'manual',
              '手动创建',
              '没有走报价单和 PI 的订单：只填客户、币种、销售日期与型号 / 数量 / 单价，收款直接在订单上登记。',
              [
                '默认美元、默认今天',
                '采购成本价、采购员选填',
                '建好后成交内容不能改，可取消重建',
              ],
              !canManual,
            )}
            <div style={{ fontSize: 12, color: palette.mute }}>
              也可以在 PI 详情页直接点「转成订单」
            </div>
          </div>
        ) : (
          <div style={{ display: 'grid', gap: 10 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <Input
                allowClear
                style={{ width: 320 }}
                placeholder="PI 编号、客户"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                aria-label="搜索 PI"
              />
              <span
                style={{
                  marginLeft: 'auto',
                  fontSize: 12,
                  color: palette.mute,
                }}
              >
                {list ? `共 ${list.length} 张可以转订单的 PI` : ''}
              </span>
            </div>
            {list === undefined ? (
              <Skeleton active paragraph={{ rows: 4 }} />
            ) : list.length === 0 ? (
              <Alert
                type="info"
                showIcon
                title={
                  keyword.trim()
                    ? '没有符合条件的 PI'
                    : '现在没有可以转订单的 PI：PI 需要已发送、有水单或到账，且没有未发送的新版本'
                }
              />
            ) : (
              list.map((p) => {
                const on = picked === p.id;
                return (
                  <button
                    type="button"
                    key={p.id}
                    aria-pressed={on}
                    onClick={() => setPicked(p.id)}
                    style={{
                      all: 'unset',
                      boxSizing: 'border-box',
                      width: '100%',
                      display: 'grid',
                      gridTemplateColumns:
                        '24px minmax(0, 1.4fr) 140px minmax(0, 1.2fr) 80px',
                      gap: 12,
                      alignItems: 'center',
                      padding: '12px 14px',
                      borderRadius: 12,
                      cursor: 'pointer',
                      background: on ? palette.accentSoft : palette.inset,
                      border: `1.5px solid ${on ? palette.link : palette.hairline}`,
                    }}
                  >
                    <RadioDot on={on} />
                    <div style={{ minWidth: 0 }}>
                      <b style={{ color: palette.ink }}>{p.piNo}</b>
                      <div style={{ fontSize: 12, color: palette.sub }}>
                        {[p.customerName, p.customerCountry]
                          .filter(Boolean)
                          .join(' · ')}
                      </div>
                    </div>
                    <div>
                      <b
                        style={{
                          color: palette.ink,
                          fontVariantNumeric: 'tabular-nums',
                        }}
                      >
                        {formatAmount(p.totalAmount, p.currencyCode)}
                      </b>
                      <div style={{ fontSize: 11, color: palette.mute }}>
                        合计
                      </div>
                    </div>
                    <div style={{ minWidth: 0 }}>
                      <ReceiptStatusPill status={p.receiptStatus} />
                      <div style={{ fontSize: 12, color: palette.mute }}>
                        {lastText(p)}
                      </div>
                    </div>
                    <span
                      style={{
                        fontSize: 12,
                        color: palette.sub,
                        textAlign: 'right',
                      }}
                    >
                      {p.ownerName ?? '—'}
                    </span>
                  </button>
                );
              })
            )}
            <div style={{ fontSize: 12, color: palette.mute }}>
              有未发送新版本、还没有水单或到账、已经转过订单的 PI
              不在这里；需要时到 PI 详情处理。
            </div>
          </div>
        )}
      </Drawer>
      <ConvertOrderModal
        pi={pi}
        open={!!pi}
        onClose={() => setPi(undefined)}
        onDone={onDone}
      />
    </>
  );
};
