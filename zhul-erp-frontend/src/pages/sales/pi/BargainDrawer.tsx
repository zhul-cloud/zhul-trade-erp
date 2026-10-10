import {
  BranchesOutlined,
  DownloadOutlined,
  InfoCircleOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import {
  Alert,
  App,
  Button,
  Drawer,
  InputNumber,
  Segmented,
  Table,
} from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { Pill } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { DISCOUNT } from '../components';
import { type Pi, type PiItem, piApi, readBizError } from '../service';
import {
  lineAfter,
  marginAfter,
  maxExtraDiscount,
  points,
  profitAfter,
  summarize,
} from './bargain';

type Mode = 'points' | 'target';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const pct = (v: number | null | undefined) =>
  v == null ? '—' : `${v.toFixed(1)}%`;
const round2 = (v: number) => Math.round((v + Number.EPSILON) * 100) / 100;

/** 议价测算：回答「还能让多少」，试算让利点数或目标总价，确认后生成带整单折扣的 PI 新版本（只在系统内显示） */
const BargainDrawer: React.FC<{
  pi: Pi;
  open: boolean;
  onClose: () => void;
  /** 能否生成新版本（已转订单、已作废、已关闭时只能测算） */
  canApply: boolean;
  onApply: (
    discount: { discountType: number; discountValue: number },
    rateDiffers: boolean,
  ) => Promise<void>;
}> = ({ pi, open, onClose, canApply, onApply }) => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const v = pi.version;
  const cur = pi.currencyCode;
  const [rate, setRate] = useState<number>(pi.exchangeRate);
  const [mode, setMode] = useState<Mode>('target');
  const [value, setValue] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    setRate(pi.exchangeRate);
    setValue(null);
  }, [open, pi.exchangeRate]);

  const lines = useMemo(
    () =>
      v.items.map((i: PiItem) => ({
        model: i.model,
        quantity: i.quantity,
        costPrice: i.costPrice,
        amount: i.amount,
        floorMargin: i.floorMargin,
      })),
    [v.items],
  );
  const s = useMemo(() => summarize(lines), [lines]);
  const fees = v.feeAmount ?? 0;
  const d0 = v.discountAmount ?? 0;
  const r = rate > 0 ? rate : pi.exchangeRate;

  // 试算的折扣（替换当前折扣）：按点数 = 小计 × 点数；按目标总价 = 小计 + 费用 − 目标总价
  const trial = useMemo(() => {
    if (value == null)
      return { discount: d0, error: undefined as string | undefined };
    const d =
      mode === 'points'
        ? round2((s.itemAmount * value) / 100)
        : round2(s.itemAmount + fees - value);
    if (d < 0)
      return {
        discount: d0,
        error: '目标总价高于当前不含折扣的合计，不需要让利',
      };
    if (d > s.itemAmount) return { discount: d0, error: '让利不能超过小计' };
    return { discount: d, error: undefined };
  }, [value, mode, s.itemAmount, fees, d0]);

  const d1 = trial.discount;
  const margin0 = marginAfter(s, d0, r);
  const margin1 = marginAfter(s, d1, r);
  const gap = margin1 == null ? null : margin1 - s.floorMargin;
  const below = gap != null && gap < 0;
  const max = maxExtraDiscount(s, d0, r);
  const trying = value != null && !trial.error;
  const belowLines = v.items.filter((i) => {
    const a = lineAfter(i, s, d1, r);
    return (
      a.margin != null && i.floorMargin != null && a.margin < i.floorMargin
    );
  });

  const columns: TableColumnsType<PiItem> = [
    {
      title: '型号',
      dataIndex: 'model',
      render: (m: string, i) => (
        <div>
          <b style={{ color: palette.ink }}>{m}</b>
          <div style={{ fontSize: 12, color: palette.mute }}>
            {[i.brand, i.quotationNo].filter(Boolean).join(' · ')}
          </div>
        </div>
      ),
    },
    { title: '数量', dataIndex: 'quantity', width: 60, align: 'right' },
    {
      title: '成本小计',
      key: 'cost',
      width: 120,
      align: 'right',
      render: (_, i) =>
        i.costPrice == null ? (
          <span style={{ color: palette.mute }}>没有成本价</span>
        ) : (
          <span style={num}>
            {formatAmount(i.costPrice * i.quantity, 'CNY')}
          </span>
        ),
    },
    {
      title: '售价小计',
      dataIndex: 'amount',
      width: 120,
      align: 'right',
      render: (a: number) => <span style={num}>{formatAmount(a, cur)}</span>,
    },
    {
      title: trying ? '让利后折合' : '折合',
      key: 'cny',
      width: 128,
      align: 'right',
      render: (_, i) => (
        <span style={num}>
          {formatAmount(lineAfter(i, s, d1, r).revenueCny, 'CNY')}
        </span>
      ),
    },
    {
      title: '毛利',
      key: 'profit',
      width: 120,
      align: 'right',
      render: (_, i) => {
        const a = lineAfter(i, s, d1, r);
        const red =
          a.margin != null && i.floorMargin != null && a.margin < i.floorMargin;
        return a.profit == null ? (
          '—'
        ) : (
          <span style={{ ...num, color: red ? palette.red : palette.green }}>
            {formatAmount(a.profit, 'CNY')}
          </span>
        );
      },
    },
    {
      title: '毛利率',
      key: 'margin',
      width: 140,
      render: (_, i) => {
        const a = lineAfter(i, s, d1, r);
        const red =
          a.margin != null && i.floorMargin != null && a.margin < i.floorMargin;
        return (
          <span
            style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}
          >
            <b style={{ ...num, color: red ? palette.red : palette.green }}>
              {pct(a.margin)}
            </b>
            {red && <Pill tone="red">低于红线</Pill>}
          </span>
        );
      },
    },
    {
      title: '红线',
      dataIndex: 'floorMargin',
      width: 64,
      align: 'right',
      render: (f?: number | null) => (
        <span style={{ color: palette.mute }}>{f == null ? '—' : `${f}%`}</span>
      ),
    },
  ];

  const metric = (
    label: string,
    before: string | null,
    after: string,
    color: string,
    hint?: React.ReactNode,
  ) => (
    <div
      style={{
        padding: '14px 16px',
        borderRadius: 12,
        background: palette.inset,
        border: `1px solid ${palette.hairline}`,
        minWidth: 0,
      }}
    >
      <div style={{ fontSize: 12, color: palette.mute, marginBottom: 6 }}>
        {label}
      </div>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
        {before != null && trying && (
          <>
            <span style={{ ...num, fontSize: 13, color: palette.mute }}>
              {before}
            </span>
            <span style={{ color: palette.mute }}>→</span>
          </>
        )}
        <b style={{ ...num, fontSize: 18, color }}>{after}</b>
      </div>
      {hint && <div style={{ fontSize: 12, marginTop: 4 }}>{hint}</div>}
    </div>
  );

  const apply = async () => {
    if (!trying || value == null) return;
    setBusy(true);
    try {
      await onApply(
        mode === 'points'
          ? { discountType: DISCOUNT.PERCENT, discountValue: value }
          : { discountType: DISCOUNT.AMOUNT, discountValue: d1 },
        Math.abs(r - pi.exchangeRate) > 1e-9,
      );
    } finally {
      setBusy(false);
    }
  };

  const exportSheet = async () => {
    try {
      await piApi.bargainExport(
        pi.id,
        {
          version: v.versionNo,
          rate: r,
          ...(trying
            ? { discountType: DISCOUNT.AMOUNT, discountValue: d1 }
            : {}),
        },
        `${pi.piNo}.xlsx`,
      );
      message.success('测算表已下载');
    } catch (e) {
      message.error((e as Error).message || readBizError(e).message);
    }
  };

  return (
    <Drawer
      open={open}
      onClose={onClose}
      size="min(1100px, 96vw)"
      title={
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 10 }}>
          议价测算
          <Pill tone="gray">只在系统内显示，不出现在导出的 PI 上</Pill>
        </span>
      }
      footer={
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{ fontSize: 12, color: palette.mute }}>
            <InfoCircleOutlined />{' '}
            费用不计入毛利；没有采购成本价的型号不参与测算
          </span>
          <Button
            style={{ marginLeft: 'auto' }}
            icon={<DownloadOutlined />}
            disabled={!s.costed}
            onClick={exportSheet}
          >
            导出测算表
          </Button>
          {canApply && (
            <Button
              type="primary"
              danger={below}
              icon={<BranchesOutlined />}
              disabled={!trying}
              loading={busy}
              onClick={apply}
            >
              {below ? '仍然生成 PI 新版本' : '生成 PI 新版本'}
            </Button>
          )}
        </div>
      }
    >
      <div style={{ fontSize: 13, color: palette.mute, marginBottom: 16 }}>
        {pi.piNo} · Rev.{v.versionNo} · 小计 {formatAmount(s.itemAmount, cur)}
        {fees ? ` + 费用 ${formatAmount(fees, cur)}` : ''}
        {d0 ? ` − 折扣 ${formatAmount(d0, cur)}` : ''} · 成本合计{' '}
        {formatAmount(s.costTotal, 'CNY')} · 当前毛利率 {pct(margin0)}
      </div>
      {!s.costed ? (
        <Alert
          type="info"
          showIcon
          title="这张 PI 的型号都没有采购成本价，无法测算"
        />
      ) : (
        <div style={{ display: 'grid', gap: 16 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'flex-end',
              gap: 16,
              flexWrap: 'wrap',
            }}
          >
            <div>
              <div
                style={{ fontSize: 12, color: palette.mute, marginBottom: 6 }}
              >
                测算汇率
              </div>
              <InputNumber
                style={{ width: 170 }}
                value={rate}
                min={0.000001}
                precision={6}
                step={0.01}
                onChange={(x) => setRate(x ?? pi.exchangeRate)}
                suffix={
                  Math.abs(r - pi.exchangeRate) < 1e-9 ? 'PI 汇率' : '临时'
                }
                aria-label="测算汇率"
              />
            </div>
            <div>
              <div
                style={{ fontSize: 12, color: palette.mute, marginBottom: 6 }}
              >
                试算方式
              </div>
              <Segmented
                value={mode}
                onChange={(m) => {
                  setMode(m as Mode);
                  setValue(null);
                }}
                options={[
                  { value: 'points', label: '让利点数' },
                  { value: 'target', label: '目标总价' },
                ]}
              />
            </div>
            <div>
              <div
                style={{ fontSize: 12, color: palette.mute, marginBottom: 6 }}
              >
                {mode === 'points' ? '让利点数' : '目标总价'}
              </div>
              <InputNumber
                style={{ width: 190 }}
                value={value}
                min={0}
                precision={mode === 'points' ? 1 : 2}
                prefix={mode === 'target' ? cur : undefined}
                suffix={mode === 'points' ? '点' : undefined}
                placeholder={
                  mode === 'points'
                    ? '如 5'
                    : `如 ${(s.itemAmount + fees - (max ?? 0)).toFixed(2)}`
                }
                status={trial.error ? 'error' : undefined}
                onChange={(x) => setValue(x)}
                aria-label={mode === 'points' ? '让利点数' : '目标总价'}
              />
            </div>
            <div
              style={{
                marginLeft: 'auto',
                padding: '10px 16px',
                borderRadius: 12,
                background:
                  max && max > 0 ? palette.greenSoft : palette.redSoft,
              }}
            >
              <div style={{ fontSize: 12, color: palette.mute }}>
                最多还能让
              </div>
              {max && max > 0 ? (
                <>
                  <div
                    style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}
                  >
                    <b style={{ ...num, fontSize: 22, color: palette.green }}>
                      {formatAmount(max, cur)}
                    </b>
                    <b style={{ ...num, color: palette.green }}>
                      {points(max, s.itemAmount)} 点
                    </b>
                  </div>
                  <div style={{ fontSize: 12, color: palette.mute }}>
                    让到这里整单毛利率 {pct(s.floorMargin)}（红线）
                  </div>
                </>
              ) : (
                <b style={{ color: palette.red }}>已低于红线，不建议再让</b>
              )}
            </div>
          </div>
          {trial.error && <Alert type="error" showIcon title={trial.error} />}

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(4, minmax(0, 1fr))',
              gap: 12,
            }}
          >
            {metric(
              '合计',
              formatAmount(s.itemAmount + fees - d0, cur),
              formatAmount(s.itemAmount + fees - d1, cur),
              palette.ink,
              d1 > 0 ? (
                <span style={{ color: palette.mute }}>
                  折扣 {formatAmount(d1, cur)}（
                  {((d1 * 100) / (s.itemAmount || 1)).toFixed(1)} 点）
                </span>
              ) : undefined,
            )}
            {metric(
              '毛利（CNY）',
              formatAmount(profitAfter(s, d0, r), 'CNY'),
              formatAmount(profitAfter(s, d1, r), 'CNY'),
              below ? palette.red : palette.ink,
            )}
            {metric(
              '毛利率',
              pct(margin0),
              pct(margin1),
              below ? palette.red : palette.green,
              gap == null ? undefined : (
                <span style={{ color: below ? palette.red : palette.green }}>
                  {below ? '低于' : '比'}红线{below ? '' : '高'}{' '}
                  {Math.abs(gap).toFixed(1)} 点
                </span>
              ),
            )}
            {metric(
              '整单红线',
              null,
              pct(s.floorMargin),
              palette.sub,
              <span style={{ color: palette.mute }}>各行红线按收入加权</span>,
            )}
          </div>

          <Table<PiItem>
            rowKey="id"
            size="middle"
            columns={columns}
            dataSource={v.items}
            pagination={false}
            onRow={(i) => {
              const a = lineAfter(i, s, d1, r);
              const red =
                a.margin != null &&
                i.floorMargin != null &&
                a.margin < i.floorMargin;
              return red ? { style: { background: palette.redSoft } } : {};
            }}
          />

          {trying && (below || belowLines.length > 0) && (
            <Alert
              type={below ? 'error' : 'warning'}
              showIcon
              icon={<WarningOutlined />}
              title={
                below
                  ? `让利后整单毛利率 ${pct(margin1)}，低于红线 ${Math.abs(gap as number).toFixed(1)} 点${belowLines.length ? `；${belowLines.map((i) => i.model).join('、')} 低于各自红线` : ''}。只提示不拦截，确需让利请先和主管确认。`
                  : `整单仍在红线以上；${belowLines.map((i) => i.model).join('、')} 让利后低于这一行的红线（让利按收入比例分摊到各行）。`
              }
            />
          )}
        </div>
      )}
    </Drawer>
  );
};

export default BargainDrawer;
