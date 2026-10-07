import {
  ArrowRightOutlined,
  DeleteOutlined,
  LockOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  Input,
  InputNumber,
  Modal,
  Skeleton,
  Switch,
  Table,
  Tag,
} from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { ConditionPill, useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { exchangeRateApi } from '@/pages/system/exchange-rate/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatMargin } from '../calc';
import { Card, QuotationPageTitle } from '../components';
import { type PricingStrategy, pricingApi, readBizError } from '../service';

const DOMESTIC_ALT = 6;
let tierSeq = 0;
const TO_CONFIRM = 7;

/** 各品相的说明（SOP V6 第十章） */
const NOTES: Record<number, string> = {
  1: '现货、非停产型号通常按此报价',
  2: '同翻新',
  3: '停产型号市面上大部分按此报价',
  4: '客户明确要求，或采购只有二手货源',
  5: '按二手处理',
  6: '全局覆盖：不看金额、不区分新老客户；对外统一称「国产替代」',
};

const HINTS: {
  key: keyof PricingStrategy['hints'];
  title: string;
  desc: string;
}[] = [
  {
    key: 'discontinuedUrgent',
    title: '停产急件',
    desc: '型号生命周期为停产，且客户询盘标记为紧急',
  },
  {
    key: 'premiumBrand',
    title: '现货优势品牌',
    desc: '我们有同行普遍缺货的现货渠道，可按稀缺型号报价（SOP 4.2.1）',
  },
  {
    key: 'returningCustomer',
    title: '老客户',
    desc: '报价单顶部提示参考该客户最近 3 次成交毛利率（SOP 4.3）',
  },
  {
    key: 'toConfirm',
    title: '品相或生命周期待查',
    desc: '品相待确认且不适用低值耗材分层，或生命周期为待查',
  },
];

type SaveBody = Parameters<typeof pricingApi.save>[0];

const toBody = (s: PricingStrategy): SaveBody => ({
  conditions: s.conditions
    .filter((c) => c.configured)
    .map((c) => ({
      itemCondition: c.itemCondition,
      marginRate: c.marginRate ?? null,
      floorRate: c.floorRate ?? null,
    })),
  tiers: s.tiers,
  hints: s.hints,
  premiumBrands: s.premiumBrands,
});

const PricingStrategyPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const wide = useWide();
  const access = useAccess() as Record<string, boolean>;
  const canEdit = !!access['quotation:pricing:edit'];
  const [data, setData] = useState<PricingStrategy>();
  const [error, setError] = useState<string>();
  const [editing, setEditing] =
    useState<PricingStrategy['conditions'][number]>();
  const [margin, setMargin] = useState<number | null>(null);
  const [floor, setFloor] = useState<number | null>(null);
  const [formError, setFormError] = useState<string>();
  const [tiersOpen, setTiersOpen] = useState(false);
  const [tiers, setTiers] = useState<
    { key: number; maxCost: number | null; marginRate: number | null }[]
  >([]);
  const [brandsOpen, setBrandsOpen] = useState(false);
  const [brands, setBrands] = useState<string[]>([]);
  const [brandInput, setBrandInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [rates, setRates] =
    useState<{ currencyCode: string; rate?: number }[]>();

  const load = useCallback(() => {
    setError(undefined);
    pricingApi
      .get()
      .then(setData)
      .catch((e) => setError(readBizError(e).message));
  }, []);

  useEffect(() => {
    load();
    // 汇率卡片：没有「汇率」菜单权限时不显示
    exchangeRateApi
      .list()
      .then(setRates)
      .catch(() => setRates(undefined));
  }, [load]);

  const persist = async (next: PricingStrategy, done: string) => {
    setBusy(true);
    try {
      setData(await pricingApi.save(toBody(next)));
      message.success(done);
      return true;
    } catch (e) {
      const msg = readBizError(e).message;
      setFormError(msg);
      message.error(msg);
      return false;
    } finally {
      setBusy(false);
    }
  };

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!data) return <Skeleton active paragraph={{ rows: 10 }} />;

  const openEdit = (c: PricingStrategy['conditions'][number]) => {
    setEditing(c);
    setMargin(c.marginRate ?? null);
    setFloor(c.floorRate ?? null);
    setFormError(undefined);
  };

  const saveCondition = async () => {
    if (!editing) return;
    if (margin != null && floor != null && floor > margin) {
      setFormError('红线不能高于建议毛利率');
      return;
    }
    if ((margin == null) !== (floor == null)) {
      setFormError(
        margin == null ? '没有建议毛利率时不能设置红线' : '请填写红线',
      );
      return;
    }
    const next: PricingStrategy = {
      ...data,
      conditions: data.conditions.map((c) =>
        c.itemCondition === editing.itemCondition
          ? { ...c, marginRate: margin, floorRate: floor, configured: true }
          : c,
      ),
    };
    if (await persist(next, `已更新「${editing.conditionName}」毛利率`))
      setEditing(undefined);
  };

  const saveTiers = async () => {
    const clean = tiers.filter(
      (t) => t.maxCost != null || t.marginRate != null,
    );
    if (clean.some((t) => t.maxCost == null || t.marginRate == null)) {
      setFormError('每一档都要填金额上限和毛利率');
      return;
    }
    const next: PricingStrategy = {
      ...data,
      tiers: clean.map((t) => ({
        maxCost: t.maxCost as number,
        marginRate: t.marginRate as number,
      })),
    };
    if (await persist(next, '已更新低值耗材分层')) setTiersOpen(false);
  };

  const saveBrands = async () => {
    if (await persist({ ...data, premiumBrands: brands }, '已更新现货优势品牌'))
      setBrandsOpen(false);
  };

  const tierMax = data.tiers.length
    ? data.tiers[data.tiers.length - 1].maxCost
    : null;

  return (
    <div>
      <QuotationPageTitle
        crumbs={['定价策略']}
        title="定价策略"
        description="依据《工控自动化产品定价策略 SOP》V6 第十章：按品相给建议毛利率与红线，售价 = 采购成本价 ÷ (1 − 毛利率)；报价时也可以按加价定价。"
      />
      {!canEdit && (
        <Alert
          type="info"
          showIcon
          title="你可以查看定价策略；修改需要「编辑定价策略」权限"
          style={{ marginBottom: 16 }}
        />
      )}

      <Card title="建议毛利率怎么定" style={{ marginBottom: 16 }}>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: wide
              ? 'repeat(4, minmax(0, 1fr))'
              : 'repeat(2, minmax(0, 1fr))',
            gap: 12,
          }}
        >
          {[
            [
              '国产替代？',
              `是 → 一律 ${formatMargin(data.conditions.find((c) => c.itemCondition === DOMESTIC_ALT)?.marginRate)}，不看金额、不分新老客户`,
            ],
            [
              '有明确品相？',
              '全新 / 99新 / 翻新 / 二手 / 拆机件 → 用下表对应毛利率',
            ],
            [
              '低值耗材？',
              `品相待确认且采购成本价 ≤ CNY ${tierMax ?? '—'} → 按金额分层`,
            ],
            ['都不是', '不给建议值，提示「品相待查，建议主管核价」'],
          ].map(([title, desc], i) => (
            <div
              key={title}
              style={{
                padding: 14,
                borderRadius: 12,
                background: palette.inset,
              }}
            >
              <div style={{ fontWeight: 600, color: palette.ink }}>
                {i + 1}. {title}
              </div>
              <div style={{ fontSize: 13, color: palette.sub, marginTop: 6 }}>
                {desc}
              </div>
            </div>
          ))}
        </div>
      </Card>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: wide
            ? 'minmax(0, 2fr) minmax(320px, 1fr)'
            : 'minmax(0, 1fr)',
          gap: 16,
          alignItems: 'start',
        }}
      >
        <div style={{ display: 'grid', gap: 16 }}>
          <Card
            title="品相毛利率与红线"
            extra={
              <span style={{ fontSize: 12, color: palette.mute }}>
                低于红线的报价行标红提示，不拦截发送
              </span>
            }
          >
            <Table
              rowKey="itemCondition"
              size="middle"
              pagination={false}
              dataSource={data.conditions}
              columns={[
                {
                  title: '货况（品相）',
                  key: 'c',
                  width: 140,
                  render: (_, c) => <ConditionPill value={c.itemCondition} />,
                },
                {
                  title: '建议毛利率',
                  key: 'm',
                  width: 110,
                  align: 'right',
                  render: (_, c) =>
                    c.configured ? (
                      formatMargin(c.marginRate)
                    ) : (
                      <Tag color="warning">还没有设置毛利率</Tag>
                    ),
                },
                {
                  title: '红线',
                  key: 'f',
                  width: 90,
                  align: 'right',
                  render: (_, c) =>
                    c.configured ? formatMargin(c.floorRate) : '—',
                },
                {
                  title: '说明',
                  key: 'n',
                  render: (_, c) =>
                    c.itemCondition === TO_CONFIRM ? (
                      <span style={{ color: palette.orange }}>
                        采购成本价 ≤ CNY {tierMax ?? '—'}{' '}
                        按低值耗材分层；超过时提示「品相待查，建议主管核价」
                      </span>
                    ) : !c.configured ? (
                      <span style={{ color: palette.orange }}>
                        字典新增的货况，未设置时按「品相待查」处理
                      </span>
                    ) : (
                      <span style={{ color: palette.sub }}>
                        {NOTES[c.itemCondition] ?? ''}
                      </span>
                    ),
                },
                {
                  title: '',
                  key: 'a',
                  width: 60,
                  render: (_, c) =>
                    canEdit && c.itemCondition !== TO_CONFIRM ? (
                      <a onClick={() => openEdit(c)}>编辑</a>
                    ) : null,
                },
              ]}
            />
          </Card>

          <Card
            title="低值耗材金额分层"
            extra={
              canEdit && (
                <a
                  onClick={() => {
                    setTiers(data.tiers.map((t) => ({ ...t, key: ++tierSeq })));
                    setFormError(undefined);
                    setTiersOpen(true);
                  }}
                >
                  编辑分层
                </a>
              )
            }
          >
            <div
              style={{
                fontSize: 12,
                color: palette.mute,
                marginTop: -8,
                marginBottom: 12,
              }}
            >
              仅用于没有品相区分、采购成本价不超过 CNY {tierMax ?? '—'}{' '}
              的耗材（端子、按钮、传感器附件等）
            </div>
            {data.tiers.length === 0 ? (
              <div style={{ color: palette.mute }}>
                没有设置分层：品相待确认的型号都提示「品相待查，建议主管核价」
              </div>
            ) : (
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: `repeat(${data.tiers.length}, minmax(0, 1fr))`,
                  gap: 12,
                }}
              >
                {data.tiers.map((t) => (
                  <div
                    key={t.maxCost}
                    style={{
                      padding: 16,
                      borderRadius: 12,
                      background: palette.inset,
                    }}
                  >
                    <div style={{ fontSize: 13, color: palette.sub }}>
                      采购成本价 ≤ CNY {t.maxCost}
                    </div>
                    <div
                      style={{
                        fontSize: 24,
                        fontWeight: 700,
                        color: palette.ink,
                        marginTop: 6,
                      }}
                    >
                      {formatMargin(t.marginRate)}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>

        <div style={{ display: 'grid', gap: 16 }}>
          <Card
            title="建议主管核价"
            extra={
              <span style={{ fontSize: 12, color: palette.mute }}>
                只提示不拦截
              </span>
            }
          >
            <div style={{ display: 'grid', gap: 10 }}>
              {HINTS.map((h) => (
                <div
                  key={h.key}
                  style={{
                    padding: 14,
                    borderRadius: 12,
                    background: palette.inset,
                  }}
                >
                  <div
                    style={{ display: 'flex', alignItems: 'center', gap: 8 }}
                  >
                    <b style={{ color: palette.ink }}>{h.title}</b>
                    <Switch
                      style={{ marginLeft: 'auto' }}
                      checked={data.hints[h.key]}
                      disabled={!canEdit || busy}
                      onChange={(v) =>
                        persist(
                          { ...data, hints: { ...data.hints, [h.key]: v } },
                          v
                            ? `已启用「${h.title}」提示`
                            : `已停用「${h.title}」提示`,
                        )
                      }
                      aria-label={h.title}
                    />
                  </div>
                  <div
                    style={{ fontSize: 12, color: palette.sub, marginTop: 4 }}
                  >
                    {h.desc}
                  </div>
                  {h.key === 'premiumBrand' && (
                    <div
                      style={{
                        marginTop: 8,
                        display: 'flex',
                        flexWrap: 'wrap',
                        gap: 6,
                        alignItems: 'center',
                      }}
                    >
                      {data.premiumBrands.map((b) => (
                        <Tag key={b} color="processing">
                          {b}
                        </Tag>
                      ))}
                      {canEdit && (
                        <a
                          style={{ fontSize: 12 }}
                          onClick={() => {
                            setBrands([...data.premiumBrands]);
                            setBrandInput('');
                            setFormError(undefined);
                            setBrandsOpen(true);
                          }}
                        >
                          <PlusOutlined /> 编辑品牌
                        </a>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </Card>

          <Card title="计算示例">
            <div
              style={{ fontSize: 13, color: palette.link, lineHeight: '24px' }}
            >
              <div>
                按毛利率：CNY 2,640.00 ÷ (1 − 10%) = CNY 2,933.33 ÷ 汇率
                7.150000 = USD 410.26
              </div>
              <div>
                按加价：CNY 200.00 + CNY 50.00 = CNY 250.00 ÷ 7.150000 = USD
                34.97（毛利率 20.0%）
              </div>
            </div>
            <div style={{ fontSize: 12, color: palette.sub, marginTop: 8 }}>
              改售价为 USD 400.00 时毛利率 = 1 − 2640 ÷ (400 × 7.15) =
              7.7%，低于全新原装红线 10% 会标红
            </div>
          </Card>

          {rates && (
            <Card>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  color: palette.ink,
                  fontWeight: 600,
                }}
              >
                <LockOutlined /> 汇率在「业务设置 → 汇率」统一维护
              </div>
              <div
                style={{
                  fontSize: 12,
                  color: palette.sub,
                  margin: '8px 0 12px',
                }}
              >
                当前{' '}
                {rates
                  .map(
                    (r) =>
                      `${r.currencyCode} ${r.rate != null ? Number(r.rate).toFixed(6) : '未设置'}`,
                  )
                  .join(' · ')}
              </div>
              <Button
                icon={<ArrowRightOutlined />}
                onClick={() => history.push('/system/exchange-rate')}
              >
                前往汇率
              </Button>
            </Card>
          )}
        </div>
      </div>

      <Modal
        open={!!editing}
        title={`编辑「${editing?.conditionName ?? ''}」毛利率`}
        onCancel={() => setEditing(undefined)}
        onOk={saveCondition}
        okText="保存"
        confirmLoading={busy}
        width={520}
      >
        <div
          style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}
        >
          <div>
            <div style={{ marginBottom: 6, color: palette.sub }}>
              建议毛利率 %
            </div>
            <InputNumber
              style={{ width: '100%' }}
              min={0}
              max={95}
              precision={2}
              value={margin}
              onChange={(v) => {
                setMargin(v);
                setFormError(undefined);
              }}
              aria-label="建议毛利率"
            />
          </div>
          <div>
            <div style={{ marginBottom: 6, color: palette.sub }}>红线 %</div>
            <InputNumber
              style={{ width: '100%' }}
              min={0}
              max={95}
              precision={2}
              value={floor}
              status={formError?.includes('红线') ? 'error' : undefined}
              onChange={(v) => {
                setFloor(v);
                setFormError(undefined);
              }}
              aria-label="红线"
            />
          </div>
        </div>
        {formError && (
          <div style={{ color: palette.red, marginTop: 8 }}>{formError}</div>
        )}
        <Alert
          style={{ marginTop: 16 }}
          type="info"
          showIcon
          title="修改后新建与重新计算的报价行使用新值；已建的报价单保留当时的建议值快照，已发送的不受影响。修改会记入操作日志。"
        />
      </Modal>

      <Modal
        open={tiersOpen}
        title="编辑低值耗材分层"
        onCancel={() => setTiersOpen(false)}
        onOk={saveTiers}
        okText="保存"
        confirmLoading={busy}
        width={520}
      >
        <div style={{ fontSize: 12, color: palette.mute, marginBottom: 12 }}>
          按采购成本价上限从低到高匹配，命中第一档即用该档毛利率
        </div>
        {tiers.map((t, i) => (
          <div
            key={t.key}
            style={{
              display: 'flex',
              gap: 8,
              alignItems: 'center',
              marginBottom: 8,
            }}
          >
            <InputNumber
              style={{ flex: 1 }}
              prefix="≤ CNY"
              min={0.01}
              precision={2}
              value={t.maxCost}
              onChange={(v) =>
                setTiers(
                  tiers.map((x, j) => (j === i ? { ...x, maxCost: v } : x)),
                )
              }
              aria-label="采购成本价上限"
            />
            <InputNumber
              style={{ width: 140 }}
              suffix="%"
              min={0}
              max={95}
              precision={2}
              value={t.marginRate}
              onChange={(v) =>
                setTiers(
                  tiers.map((x, j) => (j === i ? { ...x, marginRate: v } : x)),
                )
              }
              aria-label="毛利率"
            />
            <Button
              type="text"
              icon={<DeleteOutlined />}
              aria-label="删除这一档"
              onClick={() => setTiers(tiers.filter((_, j) => j !== i))}
            />
          </div>
        ))}
        {tiers.length < 10 && (
          <Button
            type="dashed"
            block
            icon={<PlusOutlined />}
            onClick={() =>
              setTiers([
                ...tiers,
                { key: ++tierSeq, maxCost: null, marginRate: null },
              ])
            }
          >
            加一档
          </Button>
        )}
        {formError && (
          <div style={{ color: palette.red, marginTop: 8 }}>{formError}</div>
        )}
      </Modal>

      <Modal
        open={brandsOpen}
        title="现货优势品牌"
        onCancel={() => setBrandsOpen(false)}
        onOk={saveBrands}
        okText="保存"
        confirmLoading={busy}
        width={520}
      >
        <p style={{ color: palette.sub }}>
          这些品牌的型号报价时提示「现货优势品牌 ·
          建议主管核价」，可贴近甚至高于同行价格（SOP 4.2.1）。
        </p>
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: 6,
            padding: 12,
            borderRadius: 10,
            background: palette.inset,
            minHeight: 48,
          }}
        >
          {brands.length === 0 && (
            <span style={{ color: palette.mute }}>还没有品牌</span>
          )}
          {brands.map((b) => (
            <Tag
              key={b}
              closable
              color="processing"
              onClose={() => setBrands(brands.filter((x) => x !== b))}
            >
              {b}
            </Tag>
          ))}
        </div>
        <div style={{ margin: '12px 0 6px', color: palette.sub }}>添加品牌</div>
        <Input
          value={brandInput}
          placeholder="输入品牌名后回车，按品牌及别名识别（如「恩德斯豪斯」）"
          onChange={(e) => setBrandInput(e.target.value)}
          onPressEnter={() => {
            const v = brandInput.trim();
            if (v && !brands.some((b) => b.toLowerCase() === v.toLowerCase()))
              setBrands([...brands, v]);
            setBrandInput('');
          }}
          aria-label="添加品牌"
        />
        {formError && (
          <div style={{ color: palette.red, marginTop: 8 }}>{formError}</div>
        )}
      </Modal>
    </div>
  );
};

export default PricingStrategyPage;
