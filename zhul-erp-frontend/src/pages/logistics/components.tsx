import { CheckOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, InputNumber, Select } from 'antd';
import React from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import type { Tone } from '@/pages/inquiry/shared/constants';
import { useAppTheme } from '@/theme/AppTheme';
import type { BoxInput } from './service';

export const LOGISTICS_PATHS = {
  shipments: '/logistics/shipments',
  statements: '/logistics/statements',
  outbounds: '/warehouse/outbounds',
  salesOrder: (id: number) => `/sales/orders/${id}`,
};

const tone =
  (meta: Record<number, Tone>) =>
  ({ value, children }: { value: number; children: React.ReactNode }) => (
    <Pill tone={meta[value] ?? 'gray'}>{children}</Pill>
  );

/** 出库单：待打包 / 已打包 / 已交货代 / 已撤回 */
export const ObStatusPill = tone({
  1: 'orange',
  2: 'accent',
  3: 'green',
  4: 'mute',
});
/** 出运单：待出运 / 已出运 / 已作废 */
export const ShStatusPill = tone({ 1: 'orange', 2: 'green', 3: 'mute' });
/** 对账单：草稿 / 已确认 */
export const StatementStatusPill = tone({ 1: 'orange', 2: 'green' });

export const kg = (v?: number | null) =>
  v === null || v === undefined ? '—' : `${Number(v).toFixed(2)} kg`;

// ---------------------------------------------------------------- 装箱

/** 国内快递体积系数（国际运费用货代自己的系数） */
export const DOMESTIC_DIVISOR = 5000;

const round2 = (n: number) => Math.round(n * 100) / 100;

/** 体积重 = 长 × 宽 × 高 ÷ 系数，两位小数 HALF_UP */
export const volumeWeight = (b: BoxInput, divisor = DOMESTIC_DIVISOR) =>
  b.length && b.width && b.height
    ? round2((b.length * b.width * b.height) / divisor)
    : 0;

/** 计费重：毛重与体积重取大 */
export const chargeable = (b: BoxInput, divisor = DOMESTIC_DIVISOR) =>
  Math.max(b.grossWeight ?? 0, volumeWeight(b, divisor));

export interface PackLine {
  key: number;
  model: string;
  quantity: number;
}

export const emptyBox = (lines: PackLine[], packed: Map<number, number>) => {
  const next = lines.find((l) => (packed.get(l.key) ?? 0) < l.quantity);
  return {
    length: null,
    width: null,
    height: null,
    grossWeight: null,
    netWeight: null,
    items: next
      ? [
          {
            key: next.key,
            quantity: next.quantity - (packed.get(next.key) ?? 0),
          },
        ]
      : [],
  } as BoxInput;
};

export const packedQty = (boxes: BoxInput[]) => {
  const m = new Map<number, number>();
  for (const b of boxes)
    for (const i of b.items)
      m.set(i.key, (m.get(i.key) ?? 0) + (i.quantity ?? 0));
  return m;
};

/** 不合格时返回提示：每箱尺寸、毛重、至少一个型号；每个型号装箱数量等于出库数量 */
export const boxesProblem = (lines: PackLine[], boxes: BoxInput[]) => {
  if (boxes.length === 0) return '请至少登记一箱';
  for (const [k, b] of boxes.entries()) {
    if (!b.length || !b.width || !b.height) return `第 ${k + 1} 箱请填写尺寸`;
    if (!b.grossWeight) return `第 ${k + 1} 箱请填写毛重`;
    if (!b.items.some((i) => (i.quantity ?? 0) > 0))
      return `第 ${k + 1} 箱还没有型号`;
  }
  const packed = packedQty(boxes);
  for (const l of lines) {
    const n = packed.get(l.key) ?? 0;
    if (n !== l.quantity)
      return n < l.quantity
        ? `${l.model} 还差 ${l.quantity - n} 个没装箱`
        : `${l.model} 多装了 ${n - l.quantity} 个`;
  }
  return undefined;
};

const num = (
  value: number | null | undefined,
  onChange: (v: number | null) => void,
  label: string,
  opts: { width?: number; precision?: number; min?: number } = {},
) => (
  <InputNumber
    value={value}
    min={opts.min ?? 0}
    precision={opts.precision ?? 0}
    onChange={(v) => onChange(v === null ? null : Number(v))}
    style={{ width: opts.width ?? 72 }}
    aria-label={label}
  />
);

/** 按箱登记尺寸、重量与箱内型号（一个箱子只装一张单的货） */
export const BoxEditor: React.FC<{
  lines: PackLine[];
  boxes: BoxInput[];
  onChange: (boxes: BoxInput[]) => void;
  divisor?: number;
}> = ({ lines, boxes, onChange, divisor = DOMESTIC_DIVISOR }) => {
  const { palette } = useAppTheme();
  const packed = packedQty(boxes);
  const setBox = (k: number, patch: Partial<BoxInput>) =>
    onChange(boxes.map((b, i) => (i === k ? { ...b, ...patch } : b)));
  const setItems = (k: number, items: BoxInput['items']) =>
    setBox(k, { items });
  const label = (key: number) => lines.find((l) => l.key === key)?.model ?? '';
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 12,
          alignItems: 'center',
          padding: '10px 14px',
          borderRadius: 10,
          background: palette.inset,
        }}
      >
        <b style={{ color: palette.ink }}>待装箱</b>
        <span style={{ flex: 1 }} />
        {lines.map((l) => {
          const n = packed.get(l.key) ?? 0;
          const done = n === l.quantity;
          return (
            <span
              key={l.key}
              style={{
                fontSize: 12,
                color: done
                  ? palette.green
                  : n > l.quantity
                    ? palette.red
                    : palette.orange,
              }}
            >
              {l.model} {n} / {l.quantity} {done && <CheckOutlined />}
            </span>
          );
        })}
      </div>
      {boxes.map((b, k) => {
        const used = new Set(b.items.map((i) => i.key));
        const left = lines.filter((l) => !used.has(l.key));
        return (
          <div
            // biome-ignore lint/suspicious/noArrayIndexKey: 箱子没有稳定 id，按顺序编号
            key={k}
            style={{
              padding: 14,
              borderRadius: 10,
              border: `1px solid ${palette.hairline}`,
            }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                marginBottom: 10,
              }}
            >
              <b style={{ color: palette.ink }}>第 {k + 1} 箱</b>
              <span style={{ flex: 1 }} />
              <span style={{ fontSize: 12, color: palette.mute }}>
                体积重 {kg(volumeWeight(b, divisor))} · 计费重{' '}
                {kg(chargeable(b, divisor))}
              </span>
              <Button
                type="text"
                danger
                size="small"
                icon={<DeleteOutlined />}
                disabled={boxes.length === 1}
                onClick={() => onChange(boxes.filter((_, i) => i !== k))}
                aria-label={`删除第 ${k + 1} 箱`}
              />
            </div>
            <div
              style={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: 16,
                alignItems: 'end',
              }}
            >
              <div>
                <div
                  style={{ fontSize: 12, color: palette.sub, marginBottom: 4 }}
                >
                  长 × 宽 × 高（cm）
                </div>
                <span
                  style={{
                    display: 'inline-flex',
                    gap: 6,
                    alignItems: 'center',
                  }}
                >
                  {num(b.length, (v) => setBox(k, { length: v }), '长', {
                    min: 1,
                  })}
                  ×
                  {num(b.width, (v) => setBox(k, { width: v }), '宽', {
                    min: 1,
                  })}
                  ×
                  {num(b.height, (v) => setBox(k, { height: v }), '高', {
                    min: 1,
                  })}
                </span>
              </div>
              <div>
                <div
                  style={{ fontSize: 12, color: palette.sub, marginBottom: 4 }}
                >
                  毛重 kg
                </div>
                {num(
                  b.grossWeight,
                  (v) => setBox(k, { grossWeight: v }),
                  '毛重',
                  {
                    width: 96,
                    precision: 2,
                  },
                )}
              </div>
              <div>
                <div
                  style={{ fontSize: 12, color: palette.sub, marginBottom: 4 }}
                >
                  净重 kg（选填）
                </div>
                {num(b.netWeight, (v) => setBox(k, { netWeight: v }), '净重', {
                  width: 96,
                  precision: 2,
                })}
              </div>
            </div>
            <div
              style={{
                borderTop: `1px solid ${palette.hairline}`,
                marginTop: 12,
                paddingTop: 8,
              }}
            >
              {b.items.map((i, n) => (
                <div
                  key={i.key}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    padding: '4px 0',
                  }}
                >
                  <span style={{ flex: 1, color: palette.ink }}>
                    {label(i.key)}
                  </span>
                  {num(
                    i.quantity,
                    (v) =>
                      setItems(
                        k,
                        b.items.map((x, m) =>
                          m === n ? { ...x, quantity: v } : x,
                        ),
                      ),
                    `${label(i.key)} 数量`,
                    { width: 90 },
                  )}
                  <Button
                    type="text"
                    size="small"
                    icon={<DeleteOutlined />}
                    onClick={() =>
                      setItems(
                        k,
                        b.items.filter((_, m) => m !== n),
                      )
                    }
                    aria-label={`移出 ${label(i.key)}`}
                  />
                </div>
              ))}
              {left.length > 0 && (
                <Select
                  size="small"
                  value={null}
                  placeholder={
                    <span style={{ color: palette.link }}>
                      <PlusOutlined /> 添加型号
                    </span>
                  }
                  variant="borderless"
                  options={left.map((l) => ({ value: l.key, label: l.model }))}
                  onChange={(key: number) => {
                    const l = lines.find((x) => x.key === key);
                    const rest = l
                      ? Math.max(0, l.quantity - (packed.get(key) ?? 0))
                      : 0;
                    setItems(k, [...b.items, { key, quantity: rest || null }]);
                  }}
                  style={{ width: 200, marginTop: 4 }}
                  aria-label="添加型号"
                />
              )}
            </div>
          </div>
        );
      })}
      <div>
        <Button
          icon={<PlusOutlined />}
          onClick={() => onChange([...boxes, emptyBox(lines, packed)])}
        >
          添加一箱
        </Button>
      </div>
    </div>
  );
};
