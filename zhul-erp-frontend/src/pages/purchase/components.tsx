import {
  DeleteOutlined,
  ExclamationCircleOutlined,
  PlusOutlined,
  ShopOutlined,
  ShoppingOutlined,
} from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { Button, Input, InputNumber, Segmented, Select, Space } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import type { Tone } from '@/pages/inquiry/shared/constants';
import { searchSuppliers } from '@/services/zhul/masterdata';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { TRIGGERS, termsText, termsTotal } from './calc';
import type {
  CounterpartyValue,
  PaymentTerm,
  RequirementStatus,
} from './service';

export { Card, Pill };

export const PO_STATUS = { DRAFT: 1, ORDERED: 2, CANCELLED: 3 } as const;

export const PATHS = {
  requirements: '/purchase/requirements',
  orders: '/purchase/orders',
  order: (id: number) => `/purchase/orders/${id}`,
  salesOrder: (id: number) => `/sales/orders/${id}`,
};

export const PurchasePageTitle: React.FC<
  Omit<React.ComponentProps<typeof PageTitle>, 'root'>
> = (props) => <PageTitle {...props} />;

const PO_META: Record<number, { label: string; tone: Tone }> = {
  1: { label: '草稿', tone: 'gray' },
  2: { label: '已下单', tone: 'green' },
  3: { label: '已取消', tone: 'mute' },
};

export const PoStatusPill: React.FC<{ status: number }> = ({ status }) => {
  const m = PO_META[status] ?? { label: '—', tone: 'gray' as const };
  return (
    <Pill tone={m.tone} dot>
      {m.label}
    </Pill>
  );
};

const REQ_TONE: Record<RequirementStatus, Tone> = {
  pending: 'gray',
  draft: 'orange',
  partial: 'accent',
  ordered: 'green',
  closed: 'mute',
  orderCancelled: 'red',
};

export const ReqStatusPill: React.FC<{
  status: RequirementStatus;
  name: string;
}> = ({ status, name }) => <Pill tone={REQ_TONE[status]}>{name}</Pill>;

/** 砍价金额与砍价率：涨价标红；没有目标价时提示 */
export const BargainText: React.FC<{
  amount?: number | null;
  rate?: number | null;
  empty?: string;
}> = ({ amount, rate, empty = '没有目标价' }) => {
  const { palette } = useAppTheme();
  if (amount == null) {
    return <span style={{ fontSize: 12, color: palette.mute }}>{empty}</span>;
  }
  const color = amount < 0 ? palette.red : palette.green;
  return (
    <div style={{ color, fontVariantNumeric: 'tabular-nums' }}>
      <b>{formatAmount(amount, 'CNY')}</b>
      {rate != null && <div style={{ fontSize: 12 }}>{rate.toFixed(2)}%</div>}
    </div>
  );
};

/** 供应商搜索选择（主数据中启用的供应商） */
export const SupplierPicker: React.FC<{
  value?: number;
  label?: string;
  placeholder?: string;
  style?: React.CSSProperties;
  onChange: (supplierId: number | undefined, name: string) => void;
}> = ({ value, label, placeholder = '搜索供应商', style, onChange }) => {
  const [options, setOptions] = useState<{ value: number; label: string }[]>(
    [],
  );
  const [loading, setLoading] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const search = (keyword: string) => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(async () => {
      setLoading(true);
      try {
        const list = await searchSuppliers(keyword.trim() || undefined);
        setOptions(
          list
            .filter((s) => s.status == null || s.status === 1)
            .map((s) => ({ value: s.id, label: s.name })),
        );
      } catch {
        setOptions([]);
      } finally {
        setLoading(false);
      }
    }, 250);
  };

  useEffect(() => {
    search('');
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, []);

  const merged =
    value && label && !options.some((o) => o.value === value)
      ? [{ value, label }, ...options]
      : options;

  return (
    <Select
      allowClear
      style={style}
      placeholder={placeholder}
      value={value}
      loading={loading}
      options={merged}
      showSearch={{ onSearch: search, filterOption: false }}
      notFoundContent={
        loading ? '搜索中…' : '没有找到，请先在「采购管理 → 供应商」中新建'
      }
      onChange={(v?: number) =>
        onChange(v, merged.find((o) => o.value === v)?.label ?? '')
      }
    />
  );
};

type Preset = 'prepay' | 'cod' | 'credit' | 'custom';

const presetOf = (terms: PaymentTerm[]): Preset | undefined => {
  if (!terms.length) return undefined;
  if (terms.length === 1 && terms[0].trigger === 1) return 'prepay';
  if (terms.length === 1 && terms[0].trigger === 3) {
    return terms[0].days ? 'credit' : 'cod';
  }
  return 'custom';
};

/** 付款条件：全额预付、货到付款、先采后付（入库后 N 天），或自定义分期（合计 100%） */
export const PaymentTermsEditor: React.FC<{
  /** 受控值；放在 Form.Item 里时由表单传入，可能为空 */
  value?: PaymentTerm[];
  onChange?: (terms: PaymentTerm[]) => void;
  disabled?: boolean;
}> = ({ value = [], onChange = () => {}, disabled }) => {
  const { palette } = useAppTheme();
  const preset = presetOf(value);
  const choose = (p: Preset) => {
    if (p === 'prepay') onChange([{ percent: 100, trigger: 1, days: 0 }]);
    if (p === 'cod') onChange([{ percent: 100, trigger: 3, days: 0 }]);
    if (p === 'credit') onChange([{ percent: 100, trigger: 3, days: 30 }]);
    if (p === 'custom') {
      onChange([
        { percent: 30, trigger: 1, days: 0 },
        { percent: 70, trigger: 3, days: 0 },
      ]);
    }
  };
  const set = (i: number, patch: Partial<PaymentTerm>) =>
    onChange(value.map((t, n) => (n === i ? { ...t, ...patch } : t)));
  const total = termsTotal(value);
  return (
    <div style={{ display: 'grid', gap: 10 }}>
      <Space wrap>
        {(
          [
            ['prepay', '全额预付'],
            ['cod', '货到付款'],
            ['credit', '先采后付'],
            ['custom', '自定义分期'],
          ] as [Preset, string][]
        ).map(([k, label]) => (
          <Button
            key={k}
            size="small"
            type={preset === k ? 'primary' : 'default'}
            ghost={preset === k}
            disabled={disabled}
            onClick={() => choose(k)}
          >
            {label}
          </Button>
        ))}
      </Space>
      {preset === 'credit' && (
        <Space>
          <span style={{ color: palette.sub }}>入库后</span>
          <InputNumber
            min={1}
            max={365}
            precision={0}
            value={value[0].days}
            disabled={disabled}
            onChange={(v) => set(0, { days: v ?? 30 })}
            aria-label="入库后天数"
            suffix="天"
          />
          <span style={{ color: palette.sub }}>付全款</span>
        </Space>
      )}
      {preset === 'custom' && (
        <div style={{ display: 'grid', gap: 8 }}>
          {value.map((t, i) => (
            // biome-ignore lint/suspicious/noArrayIndexKey: 分期只按位置区分
            <Space key={i} wrap>
              <InputNumber
                min={0.01}
                max={100}
                precision={2}
                value={t.percent}
                disabled={disabled}
                onChange={(v) => set(i, { percent: v ?? 0 })}
                suffix="%"
                style={{ width: 110 }}
                aria-label={`第 ${i + 1} 期比例`}
              />
              <Select
                value={t.trigger}
                disabled={disabled}
                style={{ width: 120 }}
                options={Object.entries(TRIGGERS).map(([k, v]) => ({
                  value: Number(k),
                  label: v,
                }))}
                onChange={(v) => set(i, { trigger: v, days: 0 })}
                aria-label={`第 ${i + 1} 期时点`}
              />
              {t.trigger === 3 && (
                <InputNumber
                  min={0}
                  max={365}
                  precision={0}
                  value={t.days ?? 0}
                  disabled={disabled}
                  onChange={(v) => set(i, { days: v ?? 0 })}
                  suffix="天"
                  style={{ width: 100 }}
                  aria-label={`第 ${i + 1} 期天数`}
                />
              )}
              {value.length > 2 && !disabled && (
                <Button
                  type="text"
                  size="small"
                  icon={<DeleteOutlined />}
                  aria-label="删除这一期"
                  onClick={() => onChange(value.filter((_, n) => n !== i))}
                />
              )}
            </Space>
          ))}
          {!disabled && value.length < 6 && (
            <Button
              size="small"
              type="dashed"
              icon={<PlusOutlined />}
              style={{ width: 120 }}
              onClick={() =>
                onChange([
                  ...value,
                  {
                    percent: Math.max(0, round(100 - total)),
                    trigger: 3,
                    days: 0,
                  },
                ])
              }
            >
              加一期
            </Button>
          )}
        </div>
      )}
      <div
        style={{
          fontSize: 12,
          color:
            preset === 'custom' && total !== 100 ? palette.red : palette.mute,
        }}
      >
        {value.length === 0
          ? '还没填付款条件，确认下单前须填写'
          : preset === 'custom' && total !== 100
            ? `各期比例合计须为 100%（现在 ${total}%）`
            : termsText(value)}
      </div>
    </div>
  );
};

const round = (v: number) => Math.round(v * 100) / 100;

export const sub = (color: string, text: React.ReactNode) => (
  <div style={{ fontSize: 12, color }}>{text}</div>
);

export const StatCard: React.FC<{
  icon: React.ReactNode;
  color: string;
  label: string;
  value: React.ReactNode;
  hint: React.ReactNode;
  hintColor?: string;
}> = ({ icon, color, label, value, hint, hintColor }) => {
  const { palette } = useAppTheme();
  return (
    <Card style={{ padding: 18 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ color, fontSize: 16 }}>{icon}</span>
        <span style={{ color: palette.sub, fontSize: 13 }}>{label}</span>
      </div>
      <div
        style={{
          fontSize: 24,
          fontWeight: 700,
          color: palette.ink,
          margin: '6px 0 2px',
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {value}
      </div>
      <div style={{ fontSize: 12, color: hintColor ?? palette.mute }}>
        {hint}
      </div>
    </Card>
  );
};

// ---------------------------------------------------------------- 采购对象

export const SHOP_CHANNELS = [
  { value: 1, label: '淘宝' },
  { value: 2, label: '1688' },
  { value: 3, label: '闲鱼' },
  { value: 5, label: '其他' },
];

/** 选采购对象：老供应商，或线上店铺（平台 + 店铺名） */
export const CounterpartyPicker: React.FC<{
  value: CounterpartyValue;
  onChange: (v: CounterpartyValue) => void;
}> = ({ value, onChange }) => {
  const { palette } = useAppTheme();
  const [kind, setKind] = useState<'supplier' | 'shop'>(
    value.channel != null && value.channel !== 4 ? 'shop' : 'supplier',
  );
  return (
    <div style={{ display: 'grid', gap: 12 }}>
      <Segmented
        value={kind}
        onChange={(k) => {
          setKind(k as 'supplier' | 'shop');
          onChange(k === 'shop' ? { channel: 1, shopName: '' } : {});
        }}
        options={[
          { value: 'supplier', label: '老供应商' },
          { value: 'shop', label: '线上店铺' },
        ]}
      />
      {kind === 'supplier' ? (
        <SupplierPicker
          style={{ width: '100%' }}
          placeholder="搜索供应商"
          value={value.supplierId}
          label={value.supplierName}
          onChange={(id, name) =>
            onChange({ supplierId: id, supplierName: name })
          }
        />
      ) : (
        <div
          style={{ display: 'grid', gridTemplateColumns: '140px 1fr', gap: 10 }}
        >
          <Select
            value={value.channel ?? 1}
            options={SHOP_CHANNELS}
            onChange={(v) =>
              onChange({ ...value, supplierId: undefined, channel: v })
            }
            aria-label="平台"
          />
          <Input
            value={value.shopName}
            maxLength={100}
            placeholder="店铺名称"
            onChange={(e) =>
              onChange({
                ...value,
                supplierId: undefined,
                shopName: e.target.value,
              })
            }
            aria-label="店铺名称"
          />
          <span
            style={{ gridColumn: '1 / -1', fontSize: 12, color: palette.mute }}
          >
            临时合作的店铺不用建供应商；同一平台下店铺名相同视为同一家店，买得多了可以在采购单上「转为供应商」。
          </span>
        </div>
      )}
    </div>
  );
};

export const counterpartyReady = (v: CounterpartyValue) =>
  v.supplierId != null || (v.channel != null && !!v.shopName?.trim());

export const counterpartyTitle = (v: CounterpartyValue) =>
  v.supplierId != null
    ? (v.supplierName ?? '')
    : `${SHOP_CHANNELS.find((c) => c.value === v.channel)?.label ?? ''} · ${v.shopName?.trim() ?? ''}`;

/** 「向谁买」：老供应商 / 线上店铺 / 还没定 */
export const SourceCell: React.FC<{
  supplierName?: string;
  channelName?: string;
  shopName?: string;
  hint?: string;
}> = ({ supplierName, channelName, shopName, hint }) => {
  const { palette } = useAppTheme();
  if (supplierName) {
    return (
      <div>
        <div style={{ color: palette.ink }}>
          <ShopOutlined style={{ color: palette.link, marginRight: 6 }} />
          {supplierName}
        </div>
        <div style={{ fontSize: 12, color: palette.mute }}>老供应商</div>
      </div>
    );
  }
  if (shopName) {
    return (
      <div>
        <div style={{ color: palette.ink }}>
          <ShoppingOutlined style={{ color: palette.orange, marginRight: 6 }} />
          {channelName ? `${channelName} · ` : ''}
          {shopName}
        </div>
        <div style={{ fontSize: 12, color: palette.mute }}>线上店铺</div>
      </div>
    );
  }
  return (
    <div>
      <div style={{ color: palette.orange }}>
        <ExclamationCircleOutlined style={{ marginRight: 6 }} />
        还没定
      </div>
      <div style={{ fontSize: 12, color: palette.mute }}>
        {hint ?? '没有回价'}
      </div>
    </div>
  );
};

/** 列表页统一规范：创建时间 → 创建人 → 更新时间 → 更新人 */
export const auditColumns = <
  T extends {
    createTime?: string;
    createBy?: string;
    updateTime?: string;
    updateBy?: string;
  },
>(): TableColumnsType<T> => [
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 170,
    render: (v?: string) => formatDateTime(v),
  },
  { title: '创建人', dataIndex: 'createBy', width: 90 },
  {
    title: '更新时间',
    dataIndex: 'updateTime',
    width: 170,
    render: (v?: string) => formatDateTime(v),
  },
  { title: '更新人', dataIndex: 'updateBy', width: 90 },
];
