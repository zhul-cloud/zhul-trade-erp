import {
  ArrowRightOutlined,
  DeleteOutlined,
  PlusOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import {
  Alert,
  App,
  Button,
  Checkbox,
  Drawer,
  InputNumber,
  Select,
  Skeleton,
  Table,
  Tabs,
} from 'antd';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { calcLine, formatMargin, MODE_PRICE, round2 } from './calc';
import { type PriceHistory, quotationApi, readBizError } from './service';
import {
  costTotalForeign,
  itemTotal,
  profitCny,
  ROUNDING_OPTIONS,
  type Rounding,
  type StrategyLine,
  type Tier,
  targetProfit,
  targetTotal,
  tiered,
  uniformMargin,
} from './strategy';

export interface StrategyRow extends StrategyLine {
  model: string;
  /** 当前外币售价 */
  unitPrice: number;
}

type Kind = 'uniform' | 'profit' | 'total' | 'tiers' | 'history';

const SPREADS = [0, 1, 2, 3, 5, 10].map((v) => ({
  value: v,
  label: v === 0 ? '不打散' : `± ${v} 个点`,
}));

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

/** 报价策略（spec quotation/pricing-rule「报价策略」）：选策略 → 逐行预览 → 应用到编辑中的报价单 */
const StrategyDrawer: React.FC<{
  open: boolean;
  onClose: () => void;
  rows: StrategyRow[];
  /** 勾选了型号时为 true（标题提示作用范围） */
  selectedOnly: boolean;
  currency: string;
  rate: number;
  quotationId: number;
  versionNo?: number;
  canSaveTiers: boolean;
  onApply: (prices: Map<number, number>) => void;
}> = ({
  open,
  onClose,
  rows,
  selectedOnly,
  currency,
  rate,
  quotationId,
  versionNo,
  canSaveTiers,
  onApply,
}) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [kind, setKind] = useState<Kind>('uniform');
  const [margin, setMargin] = useState<number | null>(15);
  const [profit, setProfit] = useState<number | null>(null);
  const [total, setTotal] = useState<number | null>(null);
  const [spread, setSpread] = useState(3);
  const [rounding, setRounding] = useState<Rounding>('none');
  const [seed, setSeed] = useState(1);
  const [tiers, setTiersState] = useState<(Tier & { key: number })[]>();
  const tierSeq = useRef(0);
  const setTiers = (list: Tier[]) =>
    setTiersState(
      list.map((t) => ({
        ...t,
        key: (t as Tier & { key?: number }).key ?? ++tierSeq.current,
      })),
    );
  const [savingTiers, setSavingTiers] = useState(false);
  const [history, setHistory] = useState<PriceHistory[]>();
  const [picked, setPicked] = useState<Set<number>>(new Set());

  useEffect(() => {
    if (!open) return;
    setSeed(Math.floor(Math.random() * 1e9));
    quotationApi
      .strategyTiers()
      .then(setTiers)
      .catch(() => setTiers([]));
    setHistory(undefined);
    quotationApi
      .priceHistory(quotationId, versionNo)
      .then((h) => {
        setHistory(h);
        setPicked(
          new Set(
            h.filter((x) => x.currencyCode === currency).map((x) => x.itemId),
          ),
        );
      })
      .catch(() => setHistory([]));
  }, [open, quotationId, versionNo, currency]);

  const historyByItem = useMemo(
    () => new Map((history ?? []).map((h) => [h.itemId, h])),
    [history],
  );

  /** 按当前策略算出的新售价；输入不完整时为 undefined */
  const computed = useMemo(():
    | { prices: Map<number, number>; warn?: string; error?: string }
    | undefined => {
    if (rows.length === 0) return undefined;
    const opts = { rate, spread, seed, rounding };
    switch (kind) {
      case 'uniform':
        if (margin == null) return undefined;
        if (margin < 0 || margin > 95)
          return { prices: new Map(), error: '毛利率需要在 0–95% 之间' };
        return { prices: uniformMargin(rows, margin, rate, rounding) };
      case 'profit': {
        if (profit == null || profit <= 0) return undefined;
        const r = targetProfit(rows, profit, opts);
        return {
          prices: r.prices,
          warn: r.belowFloor
            ? `目标利润低于按红线定价的利润 ${formatAmount(r.floorProfit, 'CNY')}，部分型号会低于红线`
            : undefined,
        };
      }
      case 'total': {
        if (total == null || total <= 0) return undefined;
        if (total <= costTotalForeign(rows, rate))
          return { prices: new Map(), error: '目标总价低于采购成本' };
        return { prices: targetTotal(rows, total, opts) };
      }
      case 'tiers':
        if (!tiers?.length) return undefined;
        return { prices: tiered(rows, tiers, rate, rounding) };
      case 'history': {
        const prices = new Map<number, number>();
        for (const r of rows) {
          const h = historyByItem.get(r.id);
          if (h && picked.has(r.id) && h.currencyCode === currency) {
            prices.set(r.id, h.unitPrice);
          }
        }
        return { prices };
      }
      default:
        return undefined;
    }
  }, [
    kind,
    rows,
    rate,
    spread,
    seed,
    rounding,
    margin,
    profit,
    total,
    tiers,
    historyByItem,
    picked,
    currency,
  ]);

  const prices = computed?.error ? undefined : computed?.prices;
  const newPrice = (r: StrategyRow) => prices?.get(r.id) ?? r.unitPrice;
  const marginOf = (r: StrategyRow, price: number) =>
    calcLine({
      costPrice: r.costPrice,
      quantity: r.quantity,
      rate,
      mode: MODE_PRICE,
      unitPrice: price,
    }).marginRate;
  const belowCount = prices
    ? rows.filter((r) => {
        const m = marginOf(r, newPrice(r));
        return r.floorMargin != null && m != null && m < r.floorMargin;
      }).length
    : 0;

  const summary = (pricesOf: (r: StrategyRow) => number) => {
    const map = new Map(rows.map((r) => [r.id, pricesOf(r)]));
    const amount = itemTotal(rows, map);
    const p = profitCny(rows, map, rate);
    return {
      amount: round2(amount),
      profit: round2(p),
      margin: amount > 0 ? round2((p / (amount * rate)) * 100) : null,
    };
  };
  const before = summary((r) => r.unitPrice);
  const after = summary(newPrice);
  const changed = !!prices && prices.size > 0;

  // ---------------------------------------------------------------- 输入区

  const label = (text: string, required = false) => (
    <div style={{ fontSize: 12, color: palette.mute, marginBottom: 6 }}>
      {text}
      {required && <span style={{ color: palette.red }}> *</span>}
    </div>
  );
  const roundingSelect = (
    <div>
      {label('尾数取整')}
      <Select
        style={{ width: 140 }}
        value={rounding}
        onChange={setRounding}
        options={ROUNDING_OPTIONS}
        aria-label="尾数取整"
      />
    </div>
  );
  const spreadSelect = (
    <div>
      {label('毛利率打散')}
      <Select
        style={{ width: 130 }}
        value={spread}
        onChange={setSpread}
        options={SPREADS}
        aria-label="毛利率打散"
      />
    </div>
  );
  const reshuffle = spread > 0 && (
    <Button
      icon={<SwapOutlined />}
      onClick={() => setSeed((s) => s + 1)}
      style={{ marginLeft: 'auto' }}
    >
      换一组
    </Button>
  );
  const row = (children: React.ReactNode) => (
    <div
      style={{
        display: 'flex',
        gap: 12,
        alignItems: 'flex-end',
        flexWrap: 'wrap',
        marginBottom: 12,
      }}
    >
      {children}
    </div>
  );
  const hint = (text: string) => (
    <div style={{ fontSize: 12, color: palette.mute, marginBottom: 12 }}>
      {text}
    </div>
  );

  const saveTiers = async () => {
    if (!tiers) return;
    setSavingTiers(true);
    try {
      setTiers(
        await quotationApi.saveStrategyTiers(
          tiers.map(({ maxCost, marginRate }) => ({ maxCost, marginRate })),
        ),
      );
      message.success('已保存为默认分档');
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSavingTiers(false);
    }
  };

  const inputs: Record<Kind, React.ReactNode> = {
    uniform: (
      <>
        {row(
          <>
            <div>
              {label('毛利率', true)}
              <InputNumber
                style={{ width: 140 }}
                min={0}
                max={95}
                precision={1}
                suffix="%"
                value={margin}
                onChange={setMargin}
                aria-label="毛利率"
              />
            </div>
            {roundingSelect}
          </>,
        )}
        {hint('所有型号按同一毛利率定价：售价 = 采购成本价 ÷ (1 − 毛利率)')}
      </>
    ),
    profit: (
      <>
        {row(
          <>
            <div>
              {label('整单想赚的利润（人民币）', true)}
              <InputNumber
                style={{ width: 200 }}
                min={0}
                precision={2}
                prefix="CNY"
                value={profit}
                onChange={setProfit}
                aria-label="目标利润"
              />
            </div>
            {spreadSelect}
            {roundingSelect}
            {reshuffle}
          </>,
        )}
        {hint(
          '按采购成本占比分摊目标利润，每行毛利率在目标附近随机浮动、不低于红线；取整后合计利润和目标会有少量差额',
        )}
      </>
    ),
    total: (
      <>
        {row(
          <>
            <div>
              {label(`型号小计合计（${currency}）`, true)}
              <InputNumber
                style={{ width: 200 }}
                min={0}
                precision={2}
                prefix={currency}
                value={total}
                onChange={setTotal}
                aria-label="目标总价"
              />
            </div>
            {spreadSelect}
            {roundingSelect}
            {reshuffle}
          </>,
        )}
        {hint(
          '客户给了预算时用：按采购成本比例反推每行售价，使型号小计合计等于目标（不含费用行）',
        )}
      </>
    ),
    tiers: (
      <>
        {hint('按每行采购成本价（人民币）取毛利率：')}
        {!tiers ? (
          <Skeleton active paragraph={{ rows: 3 }} />
        ) : (
          <div
            style={{ display: 'grid', gap: 8, marginBottom: 12, maxWidth: 520 }}
          >
            {tiers.map((t, n) => {
              const last = n === tiers.length - 1;
              return (
                <div
                  key={t.key}
                  style={{ display: 'flex', gap: 8, alignItems: 'center' }}
                >
                  <span style={{ width: 70, color: palette.sub }}>
                    {last ? '以上全部' : '成本 ≤'}
                  </span>
                  {last ? (
                    <span style={{ width: 160 }} />
                  ) : (
                    <InputNumber
                      style={{ width: 160 }}
                      min={0}
                      precision={2}
                      prefix="CNY"
                      value={t.maxCost}
                      onChange={(v) =>
                        setTiers(
                          tiers.map((x, i) =>
                            i === n ? { ...x, maxCost: v } : x,
                          ),
                        )
                      }
                      aria-label={`第 ${n + 1} 档上限`}
                    />
                  )}
                  <InputNumber
                    style={{ width: 110 }}
                    min={0}
                    max={95}
                    precision={1}
                    suffix="%"
                    value={t.marginRate}
                    onChange={(v) =>
                      setTiers(
                        tiers.map((x, i) =>
                          i === n ? { ...x, marginRate: v ?? 0 } : x,
                        ),
                      )
                    }
                    aria-label={`第 ${n + 1} 档毛利率`}
                  />
                  {!last && tiers.length > 2 && (
                    <Button
                      type="text"
                      icon={<DeleteOutlined />}
                      aria-label="删除这一档"
                      onClick={() => setTiers(tiers.filter((_, i) => i !== n))}
                    />
                  )}
                </div>
              );
            })}
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              {tiers.length < 6 && (
                <Button
                  type="link"
                  icon={<PlusOutlined />}
                  style={{ paddingLeft: 0 }}
                  onClick={() => {
                    const bounded = tiers.slice(0, -1);
                    const prev = bounded[bounded.length - 1]?.maxCost ?? 0;
                    setTiers([
                      ...bounded,
                      {
                        maxCost: (prev || 0) * 2 || 300,
                        marginRate: tiers[tiers.length - 1].marginRate,
                      },
                      tiers[tiers.length - 1],
                    ]);
                  }}
                >
                  加一档
                </Button>
              )}
              {canSaveTiers && (
                <Button
                  type="link"
                  loading={savingTiers}
                  style={{ marginLeft: 'auto' }}
                  onClick={saveTiers}
                >
                  保存为默认
                </Button>
              )}
            </div>
            {roundingSelect}
          </div>
        )}
      </>
    ),
    history: (
      <>
        {hint(
          '同一客户同型号最近一次的成交价（优先）或报价；只能沿用与本报价单同币种的价格',
        )}
        {history === undefined && <Skeleton active paragraph={{ rows: 3 }} />}
        {history?.length === 0 && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            title="这些型号没有给这个客户报过价或成交过"
          />
        )}
      </>
    ),
  };

  // ---------------------------------------------------------------- 预览

  const columns: TableColumnsType<StrategyRow> = [
    ...(kind === 'history'
      ? [
          {
            title: '',
            key: 'pick',
            width: 40,
            render: (_: unknown, r: StrategyRow) => {
              const h = historyByItem.get(r.id);
              return (
                <Checkbox
                  disabled={!h || h.currencyCode !== currency}
                  checked={picked.has(r.id)}
                  onChange={(e) => {
                    const next = new Set(picked);
                    if (e.target.checked) next.add(r.id);
                    else next.delete(r.id);
                    setPicked(next);
                  }}
                  aria-label={`沿用 ${r.model} 的历史价`}
                />
              );
            },
          },
        ]
      : []),
    {
      title: '型号',
      dataIndex: 'model',
      render: (v: string) => <b style={{ color: palette.ink }}>{v}</b>,
    },
    kind === 'history'
      ? {
          title: '上次价格',
          key: 'history',
          width: 230,
          render: (_, r) => {
            const h = historyByItem.get(r.id);
            if (!h)
              return <span style={{ color: palette.mute }}>没有历史价</span>;
            const other = h.currencyCode !== currency;
            return (
              <div>
                <b
                  style={{
                    color: h.kind === 'ORDER' ? palette.green : palette.ink,
                  }}
                >
                  {h.kind === 'ORDER' ? '成交' : '报价'}{' '}
                  {formatAmount(h.unitPrice, h.currencyCode)}
                </b>
                <div
                  style={{
                    fontSize: 12,
                    color: other ? palette.orange : palette.mute,
                  }}
                >
                  {h.date} · {h.docNo}
                  {other ? ' · 币种不同' : ''}
                </div>
              </div>
            );
          },
        }
      : {
          title: '采购成本 × 数量',
          key: 'cost',
          width: 150,
          align: 'right',
          render: (_, r) => (
            <span style={num}>
              {formatAmount(r.costPrice * r.quantity, 'CNY')}
            </span>
          ),
        },
    {
      title: '现售价',
      key: 'old',
      width: 120,
      align: 'right',
      render: (_, r) => (
        <span style={{ ...num, color: palette.mute }}>
          {formatAmount(r.unitPrice, currency)}
        </span>
      ),
    },
    {
      title: '',
      key: 'arrow',
      width: 30,
      render: () => <ArrowRightOutlined style={{ color: palette.mute }} />,
    },
    {
      title: '新售价',
      key: 'new',
      width: 120,
      align: 'right',
      render: (_, r) => (
        <b
          style={{
            ...num,
            color: prices?.has(r.id) ? palette.ink : palette.mute,
          }}
        >
          {formatAmount(newPrice(r), currency)}
        </b>
      ),
    },
    {
      title: '新毛利率',
      key: 'margin',
      width: 100,
      align: 'right',
      render: (_, r) => {
        const m = marginOf(r, newPrice(r));
        const below = r.floorMargin != null && m != null && m < r.floorMargin;
        return (
          <b style={{ ...num, color: below ? palette.red : palette.green }}>
            {formatMargin(m)}
          </b>
        );
      },
    },
    {
      title: '红线',
      dataIndex: 'floorMargin',
      width: 70,
      align: 'right',
      render: (v?: number | null) => (
        <span style={{ color: palette.mute }}>{formatMargin(v)}</span>
      ),
    },
  ];

  const stat = (
    title: string,
    a: React.ReactNode,
    b: React.ReactNode,
    color?: string,
    extra?: React.ReactNode,
  ) => (
    <div>
      <div style={{ fontSize: 12, color: palette.mute }}>{title}</div>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
        <span style={{ ...num, color: palette.mute }}>{a}</span>
        <ArrowRightOutlined style={{ color: palette.mute, fontSize: 11 }} />
        <b style={{ ...num, fontSize: 17, color: color ?? palette.ink }}>{b}</b>
      </div>
      {extra && (
        <div style={{ fontSize: 12, color: palette.mute }}>{extra}</div>
      )}
    </div>
  );

  const diffNote = (target: number | null, actual: number, unit: string) => {
    if (!changed || target == null) return undefined;
    const d = round2(actual - target);
    if (Math.abs(d) < 0.01) return `目标 ${formatAmount(target, unit)}`;
    return `目标 ${formatAmount(target, unit)} · 取整后${d > 0 ? '多' : '少'} ${formatAmount(Math.abs(d), unit)}`;
  };

  return (
    <Drawer
      open={open}
      onClose={onClose}
      size="min(1040px, 96vw)"
      title={
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 10 }}>
          报价策略
          <Pill tone="accent">
            {selectedOnly
              ? `作用于已选 ${rows.length} 个型号`
              : `作用于全部 ${rows.length} 个有价型号`}
          </Pill>
        </span>
      }
      footer={
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{ fontSize: 12, color: palette.mute }}>
            应用后写入报价单，仍可逐行微调；保存后生效
          </span>
          <span style={{ marginLeft: 'auto' }} />
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            disabled={!changed}
            onClick={() => prices && onApply(prices)}
          >
            {changed ? `应用到 ${prices?.size} 个型号` : '应用'}
          </Button>
        </div>
      }
    >
      {rows.length === 0 ? (
        <Alert
          type="info"
          showIcon
          title="没有可以使用报价策略的型号：报价策略只作用于有采购成本价的型号（无货行除外）"
        />
      ) : (
        <>
          <Tabs
            activeKey={kind}
            onChange={(k) => setKind(k as Kind)}
            items={[
              { key: 'uniform', label: '统一毛利率' },
              { key: 'profit', label: '目标利润' },
              { key: 'total', label: '目标总价' },
              { key: 'tiers', label: '按金额分层' },
              { key: 'history', label: '沿用历史价' },
            ]}
          />
          {inputs[kind]}
          {computed?.error && (
            <Alert
              type="error"
              showIcon
              style={{ marginBottom: 12 }}
              title={computed.error}
            />
          )}
          {computed?.warn && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 12 }}
              title={computed.warn}
            />
          )}
          {!computed?.warn && changed && belowCount > 0 && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 12 }}
              title={`${belowCount} 个型号低于红线，仍可应用`}
            />
          )}
          <Table<StrategyRow>
            rowKey="id"
            size="middle"
            columns={columns}
            dataSource={rows}
            pagination={false}
            scroll={{ y: 420 }}
          />
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
              gap: 16,
              padding: 16,
              marginTop: 12,
              borderRadius: 12,
              background: palette.inset,
            }}
          >
            {stat(
              '型号小计合计',
              formatAmount(before.amount, currency),
              formatAmount(after.amount, currency),
              undefined,
              kind === 'total'
                ? diffNote(total, after.amount, currency)
                : undefined,
            )}
            {stat(
              '合计净利润',
              formatAmount(before.profit, 'CNY'),
              formatAmount(after.profit, 'CNY'),
              after.profit >= 0 ? palette.green : palette.red,
              kind === 'profit'
                ? diffNote(profit, after.profit, 'CNY')
                : undefined,
            )}
            {stat(
              '合计毛利率',
              formatMargin(before.margin),
              formatMargin(after.margin),
              palette.green,
            )}
          </div>
        </>
      )}
    </Drawer>
  );
};

export default StrategyDrawer;
