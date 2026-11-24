import { Button, InputNumber, Select } from 'antd';
import { Check, Plus, Trash2 } from 'lucide-react';
import React from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import type { Tone } from '@/pages/inquiry/shared/constants';
import { useAppTheme } from '@/theme/AppTheme';
import type { BoxInput, Outbound } from './service';

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

/** 销售出库单：待打包 / 已打包 / 已交货代 / 已撤回 */
export const ObStatusPill = tone({
  1: 'orange',
  2: 'accent',
  3: 'green',
  4: 'mute',
});
/** 出运单：待出运 / 已出运 / 已作废 */
export const ShStatusPill = tone({ 1: 'orange', 2: 'green', 3: 'mute' });
/** 运费账单：草稿 / 已确认 */
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
  /** 合并打包时这一行所属的销售出库单（单号、订单号） */
  group?: { no: string; sub?: string };
}

/** 合并打包时箱内型号前的单号简称：取单号末尾最少 3 位，组内不重复 */
export const shortNos = (nos: string[]) => {
  const uniq = [...new Set(nos)];
  const longest = Math.max(0, ...uniq.map((x) => x.length));
  for (let n = 3; n < longest; n++) {
    const tails = uniq.map((x) => x.slice(-n));
    if (new Set(tails).size === uniq.length)
      return new Map(
        uniq.map((x, i) => [
          x,
          x.length > n + 2 ? `${x.slice(0, 2)}…${tails[i]}` : x,
        ]),
      );
  }
  return new Map(uniq.map((x) => [x, x]));
};

const lineName = (l: PackLine) =>
  l.group ? `${l.group.no} 的 ${l.model}` : l.model;

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
        ? `${lineName(l)} 还差 ${l.quantity - n} 个没装箱`
        : `${lineName(l)} 多装了 ${n - l.quantity} 个`;
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

/** 箱内型号前的出库单简称（合并打包时） */
const GroupTag: React.FC<{ no?: string; shorts: Map<string, string> }> = ({
  no,
  shorts,
}) => {
  const { palette } = useAppTheme();
  if (!no) return null;
  return (
    <span
      title={no}
      style={{
        marginRight: 8,
        padding: '1px 8px',
        borderRadius: 6,
        fontSize: 12,
        background: palette.primarySoft,
        color: palette.link,
      }}
    >
      {shorts.get(no) ?? no}
    </span>
  );
};

/** 按箱登记尺寸、重量与箱内型号；合并打包时一箱可混装几张销售出库单的型号（行带 group） */
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
  const groups = lines
    .map((l) => l.group)
    .filter((g, i, all): g is NonNullable<PackLine['group']> =>
      g ? all.findIndex((x) => x?.no === g.no) === i : false,
    );
  const shorts = shortNos(groups.map((g) => g.no));
  const lineOf = (key: number) => lines.find((l) => l.key === key);
  const label = (key: number) => {
    const l = lineOf(key);
    if (!l) return '';
    return l.group ? `${shorts.get(l.group.no)} · ${l.model}` : l.model;
  };
  const tone = (l: PackLine) => {
    const n = packed.get(l.key) ?? 0;
    return n === l.quantity
      ? palette.green
      : n > l.quantity
        ? palette.red
        : palette.orange;
  };
  const progress = (l: PackLine) => (
    <span style={{ fontSize: 12, color: tone(l) }}>
      {packed.get(l.key) ?? 0} / {l.quantity}{' '}
      {(packed.get(l.key) ?? 0) === l.quantity && <Check size="1em" />}
    </span>
  );
  const allDone = lines.every((l) => (packed.get(l.key) ?? 0) === l.quantity);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {groups.length > 0 ? (
        <div
          style={{
            padding: '10px 14px',
            borderRadius: 10,
            background: palette.inset,
          }}
        >
          <div style={{ display: 'flex', marginBottom: 8 }}>
            <b style={{ color: palette.ink }}>待装箱</b>
            <span style={{ flex: 1 }} />
            {allDone && (
              <span style={{ fontSize: 12, color: palette.green }}>
                全部装完
              </span>
            )}
          </div>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
              gap: 8,
            }}
          >
            {groups.map((g) => (
              <div
                key={g.no}
                style={{
                  padding: '8px 12px',
                  borderRadius: 8,
                  background: palette.card,
                }}
              >
                <div style={{ fontSize: 12, marginBottom: 4 }}>
                  <b style={{ color: palette.link }}>{g.no}</b>
                  {g.sub && (
                    <span style={{ marginLeft: 8, color: palette.mute }}>
                      {g.sub}
                    </span>
                  )}
                </div>
                {lines
                  .filter((l) => l.group?.no === g.no)
                  .map((l) => (
                    <div
                      key={l.key}
                      style={{ display: 'flex', gap: 8, padding: '2px 0' }}
                    >
                      <span style={{ color: palette.ink }}>{l.model}</span>
                      <span style={{ flex: 1 }} />
                      {progress(l)}
                    </div>
                  ))}
              </div>
            ))}
          </div>
        </div>
      ) : (
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
          {lines.map((l) => (
            <span key={l.key} style={{ fontSize: 12, color: tone(l) }}>
              {l.model} {packed.get(l.key) ?? 0} / {l.quantity}{' '}
              {(packed.get(l.key) ?? 0) === l.quantity && <Check size="1em" />}
            </span>
          ))}
        </div>
      )}
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
                icon={<Trash2 size="1em" />}
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
                    <GroupTag no={lineOf(i.key)?.group?.no} shorts={shorts} />
                    {lineOf(i.key)?.model}
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
                    icon={<Trash2 size="1em" />}
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
                      <Plus size="1em" /> 添加型号
                      {groups.length > 0 && '（选出库单与型号）'}
                    </span>
                  }
                  variant="borderless"
                  options={left.map((l) => ({
                    value: l.key,
                    label: label(l.key),
                  }))}
                  popupMatchSelectWidth={false}
                  onChange={(key: number) => {
                    const l = lines.find((x) => x.key === key);
                    const rest = l
                      ? Math.max(0, l.quantity - (packed.get(key) ?? 0))
                      : 0;
                    setItems(k, [...b.items, { key, quantity: rest || null }]);
                  }}
                  style={{ width: groups.length > 0 ? 300 : 200, marginTop: 4 }}
                  aria-label="添加型号"
                />
              )}
            </div>
          </div>
        );
      })}
      <div>
        <Button
          icon={<Plus size="1em" />}
          onClick={() => onChange([...boxes, emptyBox(lines, packed)])}
        >
          添加一箱
        </Button>
      </div>
    </div>
  );
};

/** 合并打包 · 同组 OB…（单号简称） */
export const PackTag: React.FC<{ ob: Outbound }> = ({ ob }) => {
  const { palette } = useAppTheme();
  const mates = ob.packMates ?? [];
  const shorts = shortNos([ob.obNo, ...mates.map((m) => m.obNo)]);
  return (
    <span
      title={`与 ${mates.map((m) => m.obNo).join('、')} 合并打包`}
      style={{
        display: 'inline-block',
        marginTop: 4,
        padding: '0 8px',
        borderRadius: 6,
        fontSize: 12,
        background: palette.orangeSoft,
        color: palette.orange,
      }}
    >
      合并打包 · 同组 {mates.map((m) => shorts.get(m.obNo)).join('、')}
    </span>
  );
};

/** 这张单和合并打包的同组单（要一起交快递、一起放进出运单） */
export const groupIds = (o: Outbound) => [
  o.id,
  ...(o.packMates ?? []).map((m) => m.id),
];
