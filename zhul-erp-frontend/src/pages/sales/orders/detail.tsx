import {
  BankOutlined,
  CheckCircleFilled,
  CheckOutlined,
  EditOutlined,
  FileTextOutlined,
  InboxOutlined,
  NodeIndexOutlined,
  PaperClipOutlined,
  SendOutlined,
  ShopOutlined,
  StopOutlined,
  SwapOutlined,
  UploadOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { history, useAccess, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Alert, App, Button, Dropdown, Skeleton, Table } from 'antd';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import {
  Card,
  ChainCard,
  KIND,
  PATHS,
  Pill,
  partyAddress,
  ReceiptStatusPill,
  SalesPageTitle,
} from '../components';
import { ConfirmReceiptModal, ReasonModal, SlipModal } from '../pi/dialogs';
import { ClaimReceiptModal, PlatformReceiptModal } from '../pi/receiptDialogs';
import {
  type Order,
  type OrderItem,
  orderApi,
  orderReceiptOwner,
  type Party,
  piApi,
  readBizError,
} from '../service';
import {
  ProgressModal,
  PurchaserModal,
  SalesDateModal,
  STOCK,
  StockPill,
} from './dialogs';
import {
  GOODS_LABELS,
  GoodsBar,
  ProgressPill,
  ReceiptProgress,
  useGoodsColors,
} from './parts';
import { OrderOutbounds, ShipNoticeModal } from './shipNotice';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

type Dialog =
  | 'progress'
  | 'purchaser'
  | 'salesDate'
  | 'slip'
  | 'confirm'
  | 'platform'
  | 'claim'
  | 'notice';

const OrderDetail: React.FC = () => {
  const { id: idParam } = useParams<{ id: string }>();
  const id = Number(idParam);
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const goodsColors = useGoodsColors();
  const access = useAccess();
  const wide = useWide();
  const [o, setO] = useState<Order>();
  const [error, setError] = useState<string>();
  const [selected, setSelected] = useState<number[]>([]);
  const [dialog, setDialog] = useState<Dialog>();
  const [cancelOpen, setCancelOpen] = useState(false);
  const [voidId, setVoidId] = useState<number>();
  /** 订单刷新次数：出库单列表随之重新取 */
  const [version, setVersion] = useState(0);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setO(await orderApi.detail(id));
      setVersion((v) => v + 1);
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  /** 收款弹窗的操作对象（手动创建的订单才有；保持引用稳定） */
  const owner = useMemo(
    () => (o && o.source === 2 ? orderReceiptOwner(o) : undefined),
    [o],
  );

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!o) return <Skeleton active paragraph={{ rows: 12 }} />;

  const cur = o.currencyCode;
  const active = o.status === 1;
  const manual = o.source === 2;
  const completed = o.progressCode === 'COMPLETED';
  const orderedModels = o.items.filter(
    (i) => (i.purchaseOrderedQty ?? 0) > 0,
  ).length;
  const canProgress = access['sales:order:progress'];

  const done = (next: Order) => {
    setO(next);
    setDialog(undefined);
    setSelected((s) => s.filter((x) => next.items.some((i) => i.id === x)));
  };

  const act = async (fn: () => Promise<Order>, ok: string) => {
    try {
      done(await fn());
      message.success(ok);
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const complete = () =>
    modal.confirm({
      title: '客户已收货？',
      content: (
        <div style={{ color: palette.sub }}>
          确认客户已收到 {o.soNo} 的全部 {o.items.length}{' '}
          个型号。确认后订单变为「已完成」，不能再改进度、不能取消。
        </div>
      ),
      okText: '确认收货',
      onOk: () => act(() => orderApi.complete(o.id), '订单已完成'),
    });

  const changeStock = (type: number) =>
    act(() => orderApi.stockType(o.id, selected, type), '已更新现货 / 期货');

  // ---------------------------------------------------------------- 跟单信息

  const field = (label: string, value: React.ReactNode) => (
    <div style={{ minWidth: 0 }}>
      <div style={{ fontSize: 12, color: palette.mute, marginBottom: 4 }}>
        {label}
      </div>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          flexWrap: 'wrap',
          fontWeight: 600,
          color: palette.ink,
        }}
      >
        {value}
      </div>
    </div>
  );

  const futures = o.items.filter((i) => i.stockType === STOCK.FUTURES).length;
  const info = (
    <Card style={{ padding: 20, marginBottom: 16 }}>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))',
          gap: 20,
        }}
      >
        {field(
          '销售日期',
          <>
            {o.salesDate ?? '—'}
            {active && (
              <Button
                type="text"
                size="small"
                icon={<EditOutlined />}
                aria-label="修改销售日期"
                onClick={() => setDialog('salesDate')}
              />
            )}
          </>,
        )}
        {field(
          '客户',
          <>
            {o.customerName}
            {o.customerType && (
              <Pill tone={o.customerType === 2 ? 'gray' : 'accent'}>
                {o.customerType === 2 ? '老客户' : '新客户'}
              </Pill>
            )}
          </>,
        )}
        {field(
          '现货 / 期货',
          <>
            <StockPill type={o.stockType} />
            {futures > 0 && (
              <span
                style={{ fontSize: 12, color: palette.mute, fontWeight: 400 }}
              >
                {futures} 个型号期货
              </span>
            )}
          </>,
        )}
        {field(
          '采购员',
          o.purchasers.map((p) => (
            <span key={p.userId ?? 0} style={{ whiteSpace: 'nowrap' }}>
              <span style={{ color: p.userId ? palette.ink : palette.orange }}>
                {p.name ?? '未指定'}
              </span>
              <span
                style={{ fontSize: 12, color: palette.mute, fontWeight: 400 }}
              >
                {' '}
                {p.itemCount} 个型号
              </span>
            </span>
          )),
        )}
      </div>
    </Card>
  );

  // ---------------------------------------------------------------- 订单进度

  const tracked = o.items.filter((i) => i.purchaseTracked);
  const goods = {
    shipped: tracked.reduce((n, i) => n + (i.goodsShipped ?? 0), 0),
    handed: tracked.reduce((n, i) => n + (i.goodsHanded ?? 0), 0),
    inWarehouse: tracked.reduce((n, i) => n + (i.goodsInWarehouse ?? 0), 0),
    inTransit: tracked.reduce((n, i) => n + (i.goodsInTransit ?? 0), 0),
    pendingShip: tracked.reduce((n, i) => n + (i.goodsPendingShip ?? 0), 0),
    pendingPurchase: tracked.reduce(
      (n, i) => n + (i.goodsPendingPurchase ?? 0),
      0,
    ),
    total: tracked.reduce((n, i) => n + i.quantity, 0),
    earliestArrival: tracked
      .flatMap((i) => i.transits ?? [])
      .map((t) => t.expectedArrivalDate)
      .filter((d): d is string => !!d)
      .sort()[0],
  };
  const today = dayjs().format('YYYY-MM-DD');
  const overdueShip = tracked.some(
    (i) =>
      (i.goodsPendingShip ?? 0) > 0 &&
      (i.purchaseOrders ?? []).some(
        (p) =>
          p.status === 2 && !!p.expectedShipDate && p.expectedShipDate < today,
      ),
  );

  const counts = new Map<string, number>();
  for (const i of o.items) {
    counts.set(i.progressCode, (counts.get(i.progressCode) ?? 0) + 1);
  }
  const rank = (code: string) => o.steps.findIndex((s) => s.code === code);
  const slowest = completed ? o.steps.length : rank(o.progressCode);
  const visibleSteps = [
    ...o.steps.filter((s) => s.enabled || counts.has(s.code)),
    { code: 'COMPLETED', name: '已完成', enabled: true },
  ];
  const stepper = active && (
    <Card style={{ padding: 20, marginBottom: 16 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 16,
        }}
      >
        <NodeIndexOutlined style={{ color: palette.link }} />
        <b style={{ color: palette.ink }}>订单进度</b>
        <ProgressPill code={o.progressCode} name={o.progressName} />
        <span style={{ marginLeft: 'auto', fontSize: 12, color: palette.mute }}>
          订单状态取进度最慢的型号
          {o.trackable && canProgress ? '；勾选型号后可批量更新' : ''}
        </span>
      </div>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
        {visibleSteps.map((s, n) => {
          const r = s.code === 'COMPLETED' ? o.steps.length : rank(s.code);
          const count = counts.get(s.code) ?? 0;
          const reached = completed || r < slowest;
          const current = !completed && count > 0;
          const color = reached
            ? palette.green
            : current
              ? palette.link
              : palette.mute;
          return (
            <React.Fragment key={s.code}>
              {n > 0 && (
                <div
                  style={{
                    flex: 1,
                    height: 2,
                    marginTop: 10,
                    background: reached ? palette.green : palette.hairline,
                    minWidth: 12,
                  }}
                />
              )}
              <div style={{ textAlign: 'center', minWidth: 64 }}>
                <div style={{ color, fontSize: 18, lineHeight: '22px' }}>
                  {reached ? (
                    <CheckCircleFilled />
                  ) : (
                    <span
                      style={{
                        display: 'inline-block',
                        width: 14,
                        height: 14,
                        borderRadius: 7,
                        border: `2px solid ${color}`,
                      }}
                    />
                  )}
                </div>
                <div
                  style={{
                    fontSize: 13,
                    fontWeight: current ? 700 : 500,
                    color: reached || current ? palette.ink : palette.mute,
                    whiteSpace: 'nowrap',
                  }}
                >
                  {s.name}
                </div>
                <div
                  style={{ fontSize: 12, color: count ? color : palette.mute }}
                >
                  {count && !completed ? `${count} 个型号` : '—'}
                </div>
              </div>
            </React.Fragment>
          );
        })}
      </div>
      {goods.total > 0 && (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
            gap: 12,
            marginTop: 18,
            paddingTop: 16,
            borderTop: `1px solid ${palette.hairline}`,
          }}
        >
          {(
            [
              [
                'shipped',
                goods.shipped >= goods.total ? '全部出运' : '货代已发出',
              ],
              ['handed', '在货代，等出运'],
              [
                'inWarehouse',
                goods.inWarehouse ? '福州仓库，可以发通知' : '福州仓库',
              ],
              [
                'inTransit',
                goods.earliestArrival
                  ? `最早 ${dayjs(goods.earliestArrival).format('MM-DD')} 到`
                  : '供应商已发出',
              ],
              [
                'pendingShip',
                overdueShip
                  ? `有采购单已过预计发货日期`
                  : '已下单，供应商还没发',
              ],
              [
                'pendingPurchase',
                goods.pendingPurchase ? '还没下采购单' : '型号都已下采购单',
              ],
            ] as const
          ).map(([k, hint]) => (
            <div
              key={k}
              style={{
                padding: '10px 14px',
                borderRadius: 10,
                background: palette.inset,
              }}
            >
              <div style={{ fontSize: 12, color: palette.sub }}>
                <span
                  style={{
                    display: 'inline-block',
                    width: 8,
                    height: 8,
                    borderRadius: 4,
                    background: goodsColors[k],
                    marginRight: 6,
                  }}
                />
                {GOODS_LABELS[k]}
              </div>
              <div
                style={{ fontSize: 20, fontWeight: 700, color: palette.ink }}
              >
                {goods[k]} 件
              </div>
              <div
                style={{
                  fontSize: 12,
                  color:
                    k === 'pendingShip' && overdueShip
                      ? palette.orange
                      : palette.mute,
                }}
              >
                {hint}
              </div>
            </div>
          ))}
        </div>
      )}
    </Card>
  );

  // ---------------------------------------------------------------- 型号

  const columns: TableColumnsType<OrderItem> = [
    { title: '#', key: 'no', width: 44, render: (_, __, i) => i + 1 },
    {
      title: '型号 / 品牌',
      key: 'model',
      width: 260,
      render: (_, r) => (
        <div>
          <div style={{ fontWeight: 600, color: palette.ink }}>{r.model}</div>
          <div style={{ fontSize: 12, color: palette.mute }}>
            {[r.brand, r.category, r.quotationNo].filter(Boolean).join(' · ')}
          </div>
        </div>
      ),
    },
    { title: '数量', dataIndex: 'quantity', width: 70, align: 'right' },
    {
      title: '单价',
      key: 'price',
      width: 120,
      align: 'right',
      render: (_, r) => (
        <span style={num}>{formatAmount(r.unitPrice, cur)}</span>
      ),
    },
    {
      title: '小计',
      key: 'amount',
      width: 130,
      align: 'right',
      render: (_, r) => <b style={num}>{formatAmount(r.amount, cur)}</b>,
    },
    {
      title: '现货 / 期货',
      dataIndex: 'stockType',
      width: 100,
      render: (v: number) => <StockPill type={v} />,
    },
    {
      title: '采购员',
      dataIndex: 'purchaserName',
      width: 110,
      render: (v: string | null | undefined, r) =>
        r.purchaserNames?.length
          ? r.purchaserNames.join('、')
          : (v ?? <span style={{ color: palette.orange }}>未指定</span>),
    },
    {
      title: '货物',
      key: 'goods',
      width: 320,
      render: (_, r) => {
        if (!r.purchaseTracked)
          return (
            <span style={{ fontSize: 12, color: palette.mute }}>
              系统外采购
            </span>
          );
        const parts = {
          shipped: r.goodsShipped ?? 0,
          handed: r.goodsHanded ?? 0,
          inWarehouse: r.goodsInWarehouse ?? 0,
          inTransit: r.goodsInTransit ?? 0,
          pendingShip: r.goodsPendingShip ?? 0,
          pendingPurchase: r.goodsPendingPurchase ?? 0,
        };
        const ordered = (r.purchaseOrders ?? []).filter((p) => p.status === 2);
        const shipDate = ordered
          .map((p) => p.expectedShipDate)
          .filter((d): d is string => !!d)
          .sort()[0];
        const dot = (
          k: keyof typeof parts,
          text: React.ReactNode,
          color?: string,
        ) =>
          parts[k] > 0 && (
            <div style={{ fontSize: 12, color: color ?? palette.sub }}>
              <span
                style={{
                  display: 'inline-block',
                  width: 7,
                  height: 7,
                  borderRadius: 4,
                  background: goodsColors[k],
                  marginRight: 6,
                }}
              />
              {text}
            </div>
          );
        const lateDays = shipDate ? dayjs().diff(dayjs(shipDate), 'day') : 0;
        return (
          <div style={{ display: 'grid', gap: 3 }}>
            <GoodsBar parts={parts} total={r.quantity} width={180} />
            {dot('shipped', `已出运 ${parts.shipped}`)}
            {dot('handed', `已交货代 ${parts.handed}`)}
            {dot('inWarehouse', `在仓 ${parts.inWarehouse}`)}
            {dot(
              'inTransit',
              `在途 ${parts.inTransit}${
                r.transits?.[0]?.expectedArrivalDate
                  ? ` · 预计 ${dayjs(r.transits[0].expectedArrivalDate).format('MM-DD')} 到${
                      r.transits[0].carrier
                        ? `（${r.transits[0].carrier}）`
                        : ''
                    }`
                  : ''
              }`,
            )}
            {dot(
              'pendingShip',
              `待发货 ${parts.pendingShip}${
                shipDate
                  ? ` · 预计 ${dayjs(shipDate).format('MM-DD')} 发货${lateDays > 0 ? `，已过 ${lateDays} 天` : ''}`
                  : ''
              }`,
              lateDays > 0 ? palette.orange : undefined,
            )}
            {dot(
              'pendingPurchase',
              `待采购 ${parts.pendingPurchase}`,
              palette.orange,
            )}
          </div>
        );
      },
    },
    {
      title: '采购单 · 发货单',
      key: 'docs',
      width: 190,
      render: (_, r) =>
        !r.purchaseTracked ? (
          '—'
        ) : (
          <div style={{ fontSize: 12, display: 'grid', gap: 2 }}>
            {(r.purchaseOrders ?? []).map((p) => (
              <a
                key={p.id}
                onClick={() => history.push(`/purchase/orders/${p.id}`)}
              >
                {p.poNo ?? '草稿采购单'}
              </a>
            ))}
            {(r.transits ?? []).map((t) => (
              <span key={t.shipmentId} style={{ color: palette.mute }}>
                {t.sdNo} 在途 {t.quantity}
              </span>
            ))}
            {(r.purchaseOrders ?? []).length === 0 && (
              <span style={{ color: palette.mute }}>还没有下采购单</span>
            )}
          </div>
        ),
    },
    {
      title: '进度',
      dataIndex: 'progressCode',
      width: 120,
      render: (v: string, r) =>
        completed ? (
          <ProgressPill code="COMPLETED" name={o.progressName} />
        ) : (
          <ProgressPill code={v} name={r.progressName} />
        ),
    },
  ];

  const bulk = o.trackable && selected.length > 0 && (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        flexWrap: 'wrap',
        padding: '10px 16px',
        borderRadius: 12,
        background: palette.accentSoft,
        marginBottom: 10,
      }}
    >
      <CheckOutlined style={{ color: palette.link }} />
      <b style={{ color: palette.ink }}>已选 {selected.length} 个型号</b>
      <span style={{ marginLeft: 'auto' }} />
      {canProgress && (
        <Button
          type="primary"
          icon={<NodeIndexOutlined />}
          onClick={() => setDialog('progress')}
        >
          更新进度
        </Button>
      )}
      <Button icon={<UserOutlined />} onClick={() => setDialog('purchaser')}>
        指定采购员
      </Button>
      <Dropdown
        menu={{
          items: [
            { key: String(STOCK.SPOT), label: '改为现货' },
            { key: String(STOCK.FUTURES), label: '改为期货' },
          ],
          onClick: ({ key }) => changeStock(Number(key)),
        }}
      >
        <Button icon={<SwapOutlined />}>改为现货 / 期货</Button>
      </Dropdown>
    </div>
  );

  // ---------------------------------------------------------------- 买方 / 条款 / 合计

  const party = (title: string, p?: Party | null) => (
    <div
      style={{
        padding: 14,
        borderRadius: 12,
        background: palette.inset,
        border: `1px solid ${palette.hairline}`,
        minWidth: 0,
      }}
    >
      <div style={{ fontSize: 13, color: palette.sub, marginBottom: 6 }}>
        {title}
      </div>
      {p ? (
        <>
          <div style={{ fontWeight: 600, color: palette.ink }}>{p.name}</div>
          <div style={{ fontSize: 12, color: palette.sub }}>
            {partyAddress(p)}
          </div>
        </>
      ) : (
        <div style={{ color: palette.mute, fontSize: 13 }}>—</div>
      )}
    </div>
  );

  const line = (
    label: React.ReactNode,
    value: React.ReactNode,
    color?: string,
  ) => (
    <div
      key={typeof label === 'string' ? label : undefined}
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        gap: 12,
        marginBottom: 6,
        fontSize: 13,
      }}
    >
      <span style={{ color: palette.sub }}>{label}</span>
      <span style={{ ...num, color: color ?? palette.ink, fontWeight: 600 }}>
        {value}
      </span>
    </div>
  );

  const terms = (
    <Card style={{ padding: 20, marginTop: 16 }}>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
          gap: 12,
          marginBottom: manual ? 0 : 12,
        }}
      >
        {party('买方（Bill To）', o.buyer)}
        {party('收货人（Consignee）', o.consignee)}
      </div>
      {!manual && (
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: '6px 24px',
            fontSize: 13,
            color: palette.sub,
          }}
        >
          <span>交期：{o.deliveryTime || '—'}</span>
          <span>付款条件：{o.paymentTerm || '—'}</span>
          <span>
            贸易术语：
            {[o.incoterm, o.incotermPlace].filter(Boolean).join(' ') || '—'}
          </span>
          <span>起运港：{o.portOfShipment || '—'}</span>
          {o.remark && <span>备注：{o.remark}</span>}
        </div>
      )}
      <div
        style={{
          borderTop: `1px solid ${palette.hairline}`,
          marginTop: 14,
          paddingTop: 14,
          maxWidth: 420,
          marginLeft: 'auto',
        }}
      >
        {line('小计', formatAmount(o.itemAmount, cur))}
        {o.fees.map((f) => line(f.feeName, formatAmount(f.amount, cur)))}
        {o.discountAmount > 0 &&
          line('折扣', formatAmount(-o.discountAmount, cur), palette.orange)}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'baseline',
            marginTop: 8,
          }}
        >
          <b style={{ fontSize: 16, color: palette.ink }}>合计</b>
          <b style={{ ...num, fontSize: 22, color: palette.ink }}>
            {formatAmount(o.totalAmount, cur)}
          </b>
        </div>
        {cur !== 'CNY' && (
          <div
            style={{ textAlign: 'right', fontSize: 12, color: palette.mute }}
          >
            ≈ {formatAmount(o.totalAmountCny, 'CNY')}（汇率{' '}
            {Number(o.exchangeRate).toFixed(6)}）
          </div>
        )}
      </div>
    </Card>
  );

  // ---------------------------------------------------------------- 收款

  const canSlip = access['sales:pi:receipt-slip'];
  const canConfirm = access['sales:pi:receipt-confirm'];
  const canPlatform = access['sales:pi:platform-receipt'];
  const canClaim = access['sales:pi:claim-receipt'];
  const receivable = manual && active;
  const openSlip = (receiptId: number, index: number) =>
    (manual
      ? orderApi.openSlipFile(o.id, receiptId, index)
      : piApi.openSlipFile(o.piId as number, receiptId, index)
    ).catch((e) => message.error((e as Error).message));

  const receipts = (
    <Card style={{ padding: 20 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 12,
        }}
      >
        <b style={{ fontSize: 16, color: palette.ink }}>收款</b>
        <ReceiptStatusPill status={o.receiptStatus} />
        {!manual && o.piNo && (
          <span
            style={{ marginLeft: 'auto', fontSize: 12, color: palette.mute }}
          >
            来自 {o.piNo}
          </span>
        )}
      </div>
      <div
        style={{
          padding: 14,
          borderRadius: 12,
          background: palette.inset,
          marginBottom: 12,
        }}
      >
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            fontSize: 13,
            marginBottom: 8,
          }}
        >
          <b style={{ color: palette.ink }}>
            已到账 {formatAmount(o.receivedAmount ?? 0, cur)} /{' '}
            {formatAmount(o.totalAmount, cur)}
          </b>
        </div>
        <ReceiptProgress
          received={o.receivedAmount ?? 0}
          total={o.totalAmount}
          status={o.receiptStatus}
          width={260}
        />
        {(o.remainingAmount ?? 0) > 0 && (
          <div style={{ fontSize: 12, color: palette.orange, marginTop: 6 }}>
            剩余 {formatAmount(o.remainingAmount ?? 0, cur)}
          </div>
        )}
      </div>
      {o.methodTotals.length > 0 && (
        <div
          style={{
            padding: 14,
            borderRadius: 12,
            background: palette.inset,
            marginBottom: 12,
          }}
        >
          <div style={{ fontSize: 12, color: palette.mute, marginBottom: 8 }}>
            按付款方式
          </div>
          {o.methodTotals.map((m) => (
            <React.Fragment key={m.paymentMethod}>
              {line(
                <span>
                  {m.paymentMethodName}{' '}
                  <Pill tone={m.channel === 2 ? 'violet' : 'gray'}>
                    {m.channel === 2 ? '线上' : '线下'}
                  </Pill>
                </span>,
                formatAmount(m.amount, m.currencyCode),
              )}
            </React.Fragment>
          ))}
        </div>
      )}
      {receivable && (canSlip || canConfirm || canPlatform || canClaim) && (
        <div
          style={{
            display: 'flex',
            gap: 8,
            marginBottom: 10,
            flexWrap: 'wrap',
          }}
        >
          {canSlip && (
            <Button icon={<UploadOutlined />} onClick={() => setDialog('slip')}>
              上传水单
            </Button>
          )}
          {canClaim && (
            <Button icon={<InboxOutlined />} onClick={() => setDialog('claim')}>
              认领到账
            </Button>
          )}
          {canPlatform && (
            <Button
              icon={<ShopOutlined />}
              onClick={() => setDialog('platform')}
            >
              登记平台收款
            </Button>
          )}
          {canConfirm && (
            <Button
              type="primary"
              icon={<BankOutlined />}
              onClick={() => setDialog('confirm')}
            >
              登记到账
            </Button>
          )}
        </div>
      )}
      <div style={{ fontSize: 12, color: palette.mute, marginBottom: 12 }}>
        {manual
          ? '手动创建的订单没有 PI，收款直接登记在订单上'
          : `收款登记在 PI ${o.piNo ?? ''} 上，这里只读显示`}
      </div>
      {o.receipts.length === 0 ? (
        <div style={{ color: palette.mute, fontSize: 13 }}>还没有收款记录</div>
      ) : (
        <div style={{ display: 'grid', gap: 8 }}>
          {[...o.receipts].reverse().map((r) => {
            const slip = r.kind === KIND.SLIP;
            const voided = r.status === 2;
            return (
              <div
                key={r.id}
                style={{
                  padding: '10px 12px',
                  borderRadius: 12,
                  border: `1px solid ${palette.hairline}`,
                  opacity: voided ? 0.6 : 1,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  {slip ? (
                    <FileTextOutlined style={{ color: palette.link }} />
                  ) : r.platformOrderNo ? (
                    <ShopOutlined style={{ color: palette.violet }} />
                  ) : (
                    <BankOutlined style={{ color: palette.green }} />
                  )}
                  <b style={{ color: palette.ink }}>
                    {slip
                      ? '水单'
                      : r.platformOrderNo
                        ? '平台收款'
                        : r.claimedAt
                          ? '到账（认领）'
                          : '到账'}
                  </b>
                  <b
                    style={{
                      ...num,
                      color: palette.ink,
                      textDecoration: voided ? 'line-through' : undefined,
                    }}
                  >
                    {formatAmount(r.amount, cur)}
                  </b>
                  {slip ? (
                    <Pill tone={r.matched ? 'green' : 'orange'}>
                      {r.matched ? '已到账' : '待到账'}
                    </Pill>
                  ) : voided ? (
                    <Pill tone="mute">已作废</Pill>
                  ) : null}
                  <span
                    style={{
                      marginLeft: 'auto',
                      fontSize: 12,
                      color: palette.mute,
                    }}
                  >
                    {r.receiptDate?.slice(5)}
                  </span>
                </div>
                <div style={{ fontSize: 12, color: palette.sub, marginTop: 4 }}>
                  {r.channel === 2 && (
                    <span style={{ marginRight: 6 }}>
                      <Pill tone="violet">线上</Pill>
                    </span>
                  )}
                  {[
                    r.paymentMethodName,
                    r.claimedByName
                      ? `${r.claimedByName} 认领`
                      : `${r.operatorName ?? ''}${slip ? '上传' : '登记'}`,
                    r.platformOrderNo && `订单号 ${r.platformOrderNo}`,
                    (r.platformFee ?? 0) > 0 &&
                      `平台手续费 ${formatAmount(r.platformFee ?? 0, cur)}`,
                    !slip &&
                      r.netAmountCny != null &&
                      `实收 ${formatAmount(r.netAmountCny, 'CNY')}`,
                    r.note,
                    voided && r.voidReason ? `作废原因：${r.voidReason}` : '',
                  ]
                    .filter(Boolean)
                    .join(' · ')}
                </div>
                {slip &&
                  r.files.map((f, i) => (
                    <a
                      key={f.fileKey}
                      style={{ fontSize: 12, marginRight: 10 }}
                      onClick={() => openSlip(r.id, i)}
                    >
                      <PaperClipOutlined /> {f.fileName}
                    </a>
                  ))}
                {manual && r.voidable && (
                  <div>
                    <a
                      style={{ fontSize: 12, color: palette.red }}
                      onClick={() => setVoidId(r.id)}
                    >
                      作废
                    </a>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </Card>
  );

  const m = o.margin;
  const marginCard = (
    <Card style={{ padding: 20, marginTop: 16 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 12,
        }}
      >
        <b style={{ fontSize: 16, color: palette.ink }}>订单毛利</b>
        {m.estimated && <Pill tone="orange">预计</Pill>}
      </div>
      <div style={{ padding: 14, borderRadius: 12, background: palette.inset }}>
        {line(
          '销售额',
          cur === 'CNY'
            ? formatAmount(o.totalAmount, cur)
            : `${formatAmount(o.totalAmount, cur)} ≈ ${formatAmount(m.salesAmountCny, 'CNY')}`,
        )}
        {line('实收人民币', formatAmount(m.receivedCny, 'CNY'))}
        {line('采购成本', formatAmount(m.costCny, 'CNY'))}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'baseline',
            borderTop: `1px solid ${palette.hairline}`,
            paddingTop: 10,
            marginTop: 4,
          }}
        >
          <span style={{ color: palette.sub, fontWeight: 600 }}>
            {m.estimated ? '预计毛利' : '毛利'}
          </span>
          <b
            style={{
              ...num,
              fontSize: 20,
              color: m.profitCny >= 0 ? palette.green : palette.red,
            }}
          >
            {formatAmount(m.profitCny, 'CNY')}
          </b>
        </div>
        <div style={{ fontSize: 12, color: palette.mute, marginTop: 6 }}>
          {m.estimated
            ? '还没收齐：按销售额折合人民币 − 采购成本估算；收齐后改为 实收人民币 − 采购成本'
            : '实收人民币 − 采购成本'}
        </div>
      </div>
      {m.missingCostCount > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginTop: 10 }}
          title={`${m.missingCostCount} 个型号没有采购成本价，毛利不完整`}
        />
      )}
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 10 }}>
        采购成本与毛利只在系统内显示，不进任何导出单据。
      </div>
    </Card>
  );

  // ---------------------------------------------------------------- 页面

  const main = (
    <div style={{ minWidth: 0 }}>
      {!active && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          title={`订单已取消${o.cancelledByName ? `（${o.cancelledByName} ${formatDateTime(o.cancelledAt).slice(0, 16)}）` : ''}：${o.cancelReason ?? ''}`}
        />
      )}
      {completed && (
        <Alert
          type="success"
          showIcon
          style={{ marginBottom: 16 }}
          title={`客户已收货，订单已完成（${formatDateTime(o.completedAt).slice(0, 16)}）`}
        />
      )}
      {info}
      {stepper}
      {bulk}
      <Table<OrderItem>
        rowKey="id"
        size="middle"
        columns={columns}
        dataSource={o.items}
        pagination={false}
        scroll={{ x: 960 }}
        rowSelection={
          o.trackable
            ? {
                selectedRowKeys: selected,
                onChange: (keys) => setSelected(keys as number[]),
              }
            : undefined
        }
      />
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 10 }}>
        成交内容（型号、数量、单价、费用）不能改，要变更请取消订单；销售日期、进度、采购员、现货
        / 期货可以随时改，改动写操作日志。
      </div>
      {terms}
      <div style={{ marginTop: 16 }}>
        <ChainCard type="order" id={o.id} reloadKey={o.status} />
      </div>
    </div>
  );

  return (
    <div>
      <SalesPageTitle
        crumbs={[
          <a key="list" onClick={() => history.push(PATHS.orderList)}>
            销售订单
          </a>,
          o.soNo,
        ]}
        title={
          <>
            {o.soNo}
            <ProgressPill code={o.progressCode} name={o.progressName} />
            <StockPill type={o.stockType} />
            <ReceiptStatusPill status={o.receiptStatus} />
          </>
        }
        description={
          <>
            {o.customerName} ·{' '}
            {manual ? (
              '手动创建'
            ) : (
              <>
                由{' '}
                <a onClick={() => history.push(PATHS.pi(o.piId as number))}>
                  {o.piNo}
                  {(o.piVersionNo ?? 1) > 1 ? ` Rev.${o.piVersionNo}` : ''}
                </a>{' '}
                转成
              </>
            )}{' '}
            · {o.ownerName ?? '—'} · {formatDateTime(o.createTime).slice(0, 16)}{' '}
            创建
          </>
        }
        actions={
          o.trackable && (
            <>
              {access['sales:order:ship-notice'] && (
                <Button
                  type="primary"
                  icon={<SendOutlined />}
                  onClick={() => setDialog('notice')}
                >
                  发货通知
                </Button>
              )}
              <Button
                icon={<CheckOutlined />}
                disabled={!o.completable}
                title={o.completable ? '' : '所有型号都出运后才能确认收货'}
                onClick={complete}
              >
                客户已收货
              </Button>
              <Button
                danger
                icon={<StopOutlined />}
                onClick={() => setCancelOpen(true)}
              >
                取消订单
              </Button>
            </>
          )
        }
      />
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: wide
            ? 'minmax(0, 2.6fr) minmax(340px, 1fr)'
            : '1fr',
          gap: 16,
          alignItems: 'start',
        }}
      >
        <div style={{ minWidth: 0 }}>
          {main}
          <OrderOutbounds soId={o.id} version={version} onChanged={load} />
        </div>
        <div style={{ minWidth: 0 }}>
          {receipts}
          {marginCard}
        </div>
      </div>

      <ShipNoticeModal
        open={dialog === 'notice'}
        soId={o.id}
        onClose={() => setDialog(undefined)}
        onDone={() => {
          setDialog(undefined);
          load();
        }}
      />
      <ProgressModal
        order={o}
        itemIds={selected}
        open={dialog === 'progress'}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <PurchaserModal
        order={o}
        itemIds={selected}
        open={dialog === 'purchaser'}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <SalesDateModal
        order={o}
        open={dialog === 'salesDate'}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <SlipModal
        owner={owner}
        open={dialog === 'slip'}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <ConfirmReceiptModal
        owner={owner}
        open={dialog === 'confirm'}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <PlatformReceiptModal
        owner={owner}
        open={dialog === 'platform'}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <ClaimReceiptModal
        owner={owner}
        open={dialog === 'claim'}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <ReasonModal
        open={voidId != null}
        title="作废这笔到账？"
        placeholder="如 金额录错"
        okText="作废"
        onClose={() => setVoidId(undefined)}
        onSubmit={async (reason) => {
          try {
            done(await orderApi.voidReceipt(o.id, voidId as number, reason));
            setVoidId(undefined);
            message.success('到账已作废');
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
      <ReasonModal
        open={cancelOpen}
        title={`取消订单 ${o.soNo}？`}
        description={
          <>
            {orderedModels > 0 && (
              <div style={{ marginBottom: 8, color: palette.orange }}>
                {orderedModels}{' '}
                个型号已向供应商下单，取消后请采购员处理对应采购单；同一张 PI
                重新转出的订单会自动接回这些已下单的数量。
              </div>
            )}
            {manual ? (
              <>
                取消后订单不能再登记收款。
                {(o.receivedAmount ?? 0) > 0 && (
                  <div style={{ marginTop: 8, color: palette.orange }}>
                    已到账 {formatAmount(o.receivedAmount ?? 0, cur)}{' '}
                    会保留在已取消的订单上。
                  </div>
                )}
              </>
            ) : (
              <>
                取消后 PI {o.piNo}{' '}
                回到「已发送」并解锁，可以出新版本后重新转成订单；来源报价单与客户询盘的成交状态按剩余有效订单重新计算。
                {(o.receivedAmount ?? 0) > 0 && (
                  <div style={{ marginTop: 8, color: palette.orange }}>
                    已到账 {formatAmount(o.receivedAmount ?? 0, cur)} 会保留在
                    PI 上，重新转出的订单继续显示。
                  </div>
                )}
              </>
            )}
          </>
        }
        placeholder="如 客户追加型号"
        okText="取消订单"
        onClose={() => setCancelOpen(false)}
        onSubmit={async (reason) => {
          try {
            setO(await orderApi.cancel(o.id, reason));
            setCancelOpen(false);
            message.success('订单已取消');
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
    </div>
  );
};

export default OrderDetail;
