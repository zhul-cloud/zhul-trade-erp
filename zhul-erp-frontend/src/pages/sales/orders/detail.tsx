import {
  BankOutlined,
  FileTextOutlined,
  InboxOutlined,
  PaperClipOutlined,
  ShopOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { history, Link, useAccess, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Alert, App, Button, Dropdown, Skeleton, Table, Tooltip } from 'antd';
import dayjs from 'dayjs';
import {
  ArrowLeftRight,
  Ban,
  Check,
  ChevronDown,
  CircleMinus,
  ClipboardList,
  Ellipsis,
  FileText,
  Headset,
  MapPin,
  Pencil,
  Plus,
  Send,
  Trash2,
  TriangleAlert,
  Undo2,
  User,
  UserRound,
} from 'lucide-react';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import DocModelCell, {
  DocDescription,
  docBrandColumns,
} from '@/components/DocFields/DocModelCell';
import { ItemRemarkLine } from '@/components/DocFields/ItemRemark';
import SourceNos from '@/components/DocFields/SourceNos';
import EllipsisText from '@/components/EllipsisText';
import {
  CardTitle,
  HelpTip,
  PageHeader,
  Panel,
  Section,
} from '@/components/ListLayout';
import { useOnReactivate, usePageTitle } from '@/components/PageTabs/hooks';
import { tabsActions } from '@/components/PageTabs/store';
import RefundModal from '@/pages/finance/refunds/RefundModal';
import { useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { useDeltaColor } from '@/theme/delta';
import { formatAmount, formatDateTime } from '@/utils/format';
import { tableScrollX } from '@/utils/tableScroll';
import {
  CreateAfterSalesModal,
  RefundRequestModal,
} from '../after-sales/dialogs';
import {
  type AfterSales,
  AS_STATUS_TONE,
  afterSalesApi,
  LEVEL_TONE,
  levelShort,
} from '../after-sales/service';
import {
  Card,
  ChainCard,
  KIND,
  MigratedPill,
  orderSourceRefs,
  PATHS,
  Pill,
  partyAddress,
  RECEIPT_STATUS,
  ReceiptStatusPill,
} from '../components';
import CreditInfo from '../pi/CreditInfo';
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
import OrderCostCard from './CostCard';
import { DeleteOrderModal } from './deleteOrder';
import {
  CreateFeeOrderModal,
  EditFeesModal,
  manualSteps,
  PaymentDueDateModal,
  ProgressModal,
  PurchaserModal,
  SalesDateModal,
  STOCK,
  StockPill,
} from './dialogs';
import { NotNeededNote } from './notNeeded';
import { OrderFilesCard } from './OrderFiles';
import {
  PartialCancelModal,
  PartialCancelRecords,
  QtyCell,
} from './partialCancel';
import {
  GOODS_LABELS,
  GoodsBar,
  ProgressPill,
  ReceiptProgress,
  useGoodsColors,
} from './parts';
import { OrderOutbounds, ShipNoticeModal } from './shipNotice';

// 金额不折行（audit UI-041）
const num: React.CSSProperties = {
  fontVariantNumeric: 'tabular-nums',
  whiteSpace: 'nowrap',
};

type Dialog =
  | 'progress'
  | 'purchaser'
  | 'salesDate'
  | 'paymentDueDate'
  | 'slip'
  | 'confirm'
  | 'platform'
  | 'claim'
  | 'notice'
  | 'partialCancel'
  | 'afterSales'
  | 'refundRequest'
  | 'feeOrder';

const OrderDetail: React.FC = () => {
  const { id: idParam } = useParams<{ id: string }>();
  const id = Number(idParam);
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const { of: deltaOf } = useDeltaColor();
  const goodsColors = useGoodsColors();
  const access = useAccess();
  const wide = useWide();
  const [o, setO] = useState<Order>();
  usePageTitle(o ? `销售订单 ${o.soNo}` : undefined, o?.soNo);
  const [error, setError] = useState<string>();
  const [selected, setSelected] = useState<number[]>([]);
  const [dialog, setDialog] = useState<Dialog>();
  const [cancelOpen, setCancelOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [feesOpen, setFeesOpen] = useState(false);
  const [refundOpen, setRefundOpen] = useState(false);
  const [voidId, setVoidId] = useState<number>();
  /** 订单刷新次数：销售出库单列表随之重新取 */
  const [version, setVersion] = useState(0);
  const [afterSales, setAfterSales] = useState<AfterSales[]>([]);
  useEffect(() => {
    if (!access.salesAfterSales) return;
    afterSalesApi
      .ofOrder(id)
      .then(setAfterSales)
      .catch(() => setAfterSales([]));
  }, [id, version, access.salesAfterSales]);

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

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  /** 收款弹窗的操作对象（手动创建的订单才有；保持引用稳定） */
  const owner = useMemo(
    () =>
      o && (o.source === 2 || o.source === 4)
        ? orderReceiptOwner(o)
        : undefined,
    [o],
  );

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!o) return <Skeleton active paragraph={{ rows: 12 }} />;

  const cur = o.currencyCode;
  const active = o.status === 1;
  /** 历史迁移的订单只读（可以照常登记退款） */
  const editable = active && !o.migrated;
  /** 费用订单：只有费用行，关联原订单，收款到账后自动完成 */
  const fee = o.source === 4;
  /** 收款登记在订单自己身上（手动创建、费用订单） */
  const manual = o.source === 2 || fee;
  /** 售后补发订单：0 元、不收款，成本计入原订单 */
  const replacement = o.source === 3;
  /** 原订单上可以新建费用订单 */
  const canFeeOrder =
    editable &&
    (o.source === 1 || o.source === 2) &&
    !!access['sales:order:create'];
  const completed = o.progressCode === 'COMPLETED';
  // 进行中、已取消的订单可以删；已完成、历史迁移的不显示
  const canDelete = !!access['sales:order:delete'] && !completed && !o.migrated;
  const orderedModels = o.items.filter(
    (i) => (i.purchaseOrderedQty ?? 0) > 0,
  ).length;
  const canProgress =
    access['sales:order:progress'] && manualSteps(o).length > 0;

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

  // 订单头部的四张小卡：客户、订单信息、买方、收货人（原型 B1）
  const infoCard = (
    title: string,
    icon: React.ReactNode,
    children: React.ReactNode,
    tint?: boolean,
  ) => (
    <Panel
      style={{
        padding: 20,
        minWidth: 0,
        ...(tint ? { background: palette.primarySoft } : {}),
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 8,
          marginBottom: 12,
        }}
      >
        <h2
          style={{
            margin: 0,
            fontSize: 16,
            fontWeight: 500,
            color: palette.ink,
          }}
        >
          {title}
        </h2>
        <span
          aria-hidden
          style={{
            width: 32,
            height: 32,
            flex: 'none',
            borderRadius: '50%',
            display: 'grid',
            placeItems: 'center',
            background: tint ? palette.card : palette.neutralSoft,
            color: tint ? palette.link : palette.sub,
          }}
        >
          {icon}
        </span>
      </div>
      {children}
    </Panel>
  );

  const infoRow = (label: string, value: React.ReactNode) => (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        minHeight: 30,
        fontSize: 13,
      }}
    >
      <span style={{ width: 72, flex: 'none', color: palette.mute }}>
        {label}
      </span>
      <span
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          flexWrap: 'wrap',
          minWidth: 0,
          color: palette.ink,
        }}
      >
        {value}
      </span>
    </div>
  );

  const editBtn = (label: string, onClick: () => void) => (
    <Button
      type="text"
      size="small"
      icon={<Pencil size={14} />}
      aria-label={label}
      style={{ color: palette.link }}
      onClick={onClick}
    />
  );

  const partyCard = (title: string, icon: React.ReactNode, p?: Party | null) =>
    infoCard(
      title,
      icon,
      p ? (
        <>
          <div style={{ fontWeight: 600, color: palette.ink }}>{p.name}</div>
          <div style={{ fontSize: 13, color: palette.sub, marginTop: 4 }}>
            {partyAddress(p)}
          </div>
        </>
      ) : (
        <div style={{ color: palette.mute, fontSize: 13 }}>—</div>
      ),
    );

  const futures = o.items.filter(
    (i) => i.quantity > 0 && i.stockType === STOCK.FUTURES,
  ).length;
  const info = (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
        gap: 16,
      }}
    >
      {infoCard(
        '客户',
        <User size={16} />,
        <>
          <div style={{ fontWeight: 600, color: palette.ink }}>
            {o.customerName}
          </div>
          {o.customerType ? (
            <div style={{ fontSize: 13, color: palette.sub, marginTop: 4 }}>
              {o.customerType === 2 ? '老客户' : '新客户'}
            </div>
          ) : null}
        </>,
        true,
      )}
      {!fee &&
        infoCard(
          '订单信息',
          <ClipboardList size={16} />,
          <>
            {infoRow(
              '销售日期',
              <>
                {o.salesDate ?? '—'}
                {editable &&
                  editBtn('修改销售日期', () => setDialog('salesDate'))}
              </>,
            )}
            {manual &&
              infoRow(
                '约定付款日',
                <>
                  {o.paymentDueDate ?? '—'}
                  {editable &&
                    editBtn('修改约定付款日', () =>
                      setDialog('paymentDueDate'),
                    )}
                  {o.overdueDays != null && (
                    <Pill tone="red">已逾期 {o.overdueDays} 天</Pill>
                  )}
                </>,
              )}
            {infoRow(
              '现货 / 期货',
              <>
                <StockPill type={o.stockType} />
                {futures > 0 && (
                  <span style={{ fontSize: 12, color: palette.mute }}>
                    {futures} 个型号期货
                  </span>
                )}
              </>,
            )}
            {infoRow(
              '采购员',
              o.purchasers.map((p) =>
                p.userId ? (
                  <span key={p.userId} style={{ whiteSpace: 'nowrap' }}>
                    {p.name}
                    <span style={{ fontSize: 12, color: palette.mute }}>
                      {' '}
                      {p.itemCount} 个型号
                    </span>
                  </span>
                ) : (
                  <Pill key={0} tone="orange">
                    <TriangleAlert size={12} aria-hidden />
                    未指定 {p.itemCount} 个型号
                  </Pill>
                ),
              ),
            )}
          </>,
        )}
      {partyCard('买方（Bill To）', <FileText size={16} />, o.buyer)}
      {partyCard('收货人（Consignee）', <MapPin size={16} />, o.consignee)}
    </div>
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
  // 全部取消的型号不参与进度
  for (const i of o.items.filter((x) => x.quantity > 0)) {
    counts.set(i.progressCode, (counts.get(i.progressCode) ?? 0) + 1);
  }
  const rank = (code: string) => o.steps.findIndex((s) => s.code === code);
  const slowest = completed ? o.steps.length : rank(o.progressCode);
  const visibleSteps = [
    ...o.steps.filter((s) => s.enabled || counts.has(s.code)),
    { code: 'COMPLETED', name: '已完成', enabled: true },
  ];
  // 步骤条（界面规范「步骤条」）：圆点 30，已完成与当前步为主题色实底白字，未到的为浅灰底灰字
  const stepper = active && (
    <Section title="订单进度" help="订单状态取进度最慢的型号">
      <ol
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          listStyle: 'none',
          margin: 0,
          padding: 0,
        }}
      >
        {visibleSteps.map((s, n) => {
          const r = s.code === 'COMPLETED' ? o.steps.length : rank(s.code);
          const count = counts.get(s.code) ?? 0;
          const reached = completed || r < slowest;
          const current = !completed && count > 0;
          const on = reached || current;
          return (
            <li
              key={s.code}
              aria-current={current ? 'step' : undefined}
              style={{
                flex: 1,
                minWidth: 0,
                position: 'relative',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                textAlign: 'center',
              }}
            >
              {n > 0 && (
                <span
                  aria-hidden
                  style={{
                    position: 'absolute',
                    top: 14,
                    left: 'calc(-50% + 23px)',
                    right: 'calc(50% + 23px)',
                    height: 2,
                    borderRadius: 1,
                    background: reached ? palette.primary : palette.hairline,
                  }}
                />
              )}
              <span
                style={{
                  width: 30,
                  height: 30,
                  borderRadius: '50%',
                  display: 'grid',
                  placeItems: 'center',
                  fontSize: 13,
                  fontWeight: 600,
                  background: on ? palette.primary : palette.neutralSoft,
                  color: on ? '#fff' : palette.mute,
                }}
              >
                {n + 1}
              </span>
              <span
                style={{
                  marginTop: 8,
                  fontSize: 14,
                  fontWeight: current ? 600 : 400,
                  color: on ? palette.ink : palette.mute,
                  whiteSpace: 'nowrap',
                }}
              >
                {s.name}
              </span>
              <span
                style={{
                  fontSize: 12,
                  color: current ? palette.link : palette.mute,
                }}
              >
                {current ? `${count} 个型号` : '—'}
              </span>
            </li>
          );
        })}
      </ol>
      {goods.total > 0 && (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(100px, 1fr))',
            gap: 12,
            marginTop: 20,
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
                  ? `有采购订单已过预计发货日期`
                  : '已下单，供应商还没发',
              ],
              [
                'pendingPurchase',
                goods.pendingPurchase ? '还没下采购订单' : '型号都已下采购订单',
              ],
            ] as const
          ).map(([k, hint]) => (
            <div
              key={k}
              style={{
                padding: '10px 12px',
                borderRadius: 8,
                background: palette.inset,
                minWidth: 0,
              }}
            >
              <div style={{ fontSize: 12, color: palette.sub }}>
                <span
                  aria-hidden
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
                style={{
                  ...num,
                  fontSize: 20,
                  fontWeight: 700,
                  color: goods[k] ? palette.ink : palette.mute,
                }}
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
    </Section>
  );

  const columns: TableColumnsType<OrderItem> = [
    { title: '#', key: 'no', width: 44, render: (_, __, i) => i + 1 },
    ...docBrandColumns<OrderItem>((r) => r),
    {
      title: '型号',
      key: 'model',
      width: 220,
      render: (_, r) => (
        <DocModelCell
          model={r.model}
          brand={r.brand}
          orderCode={r.orderCode}
          productId={r.productId}
          candidate={r.productCandidate}
          customerModel={r.customerModel}
          suffix={
            r.quantity === 0 && (
              <span style={{ marginLeft: 8 }}>
                <Pill tone="mute">已全部取消</Pill>
              </span>
            )
          }
        >
          <ItemRemarkLine
            text={r.itemRemark}
            by={r.itemRemarkBy}
            at={r.itemRemarkAt}
            editable={editable}
            onSave={async (value) => {
              const res = await orderApi.saveItemRemark(o.id, r.id, value);
              setO((cur) =>
                cur
                  ? {
                      ...cur,
                      items: cur.items.map((it) =>
                        it.id === r.id
                          ? {
                              ...it,
                              itemRemark: res.remark,
                              itemRemarkBy: res.remarkBy,
                              itemRemarkAt: res.remarkAt,
                            }
                          : it,
                      ),
                    }
                  : cur,
              );
              message.success(value ? '型号备注已保存' : '型号备注已清空');
            }}
          />
        </DocModelCell>
      ),
    },
    {
      title: '描述',
      key: 'description',
      width: 260,
      render: (_, r) => <DocDescription text={r.description} />,
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      width: 130,
      align: 'right',
      render: (_, r) => (
        <QtyCell
          quantity={r.quantity}
          original={r.originalQuantity}
          cancelled={r.cancelledQuantity}
        />
      ),
    },
    {
      title: '单价',
      key: 'price',
      width: 120,
      align: 'right',
      render: (_, r) => (
        <span style={num}>{formatAmount(r.unitPrice, cur)}</span>
      ),
    },
    ...(replacement
      ? [
          {
            title: '申报单价',
            key: 'declared',
            width: 120,
            align: 'right' as const,
            render: (_: unknown, r: OrderItem) => (
              <b style={num}>{formatAmount(r.declaredPrice ?? 0, cur)}</b>
            ),
          },
        ]
      : []),
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
        r.quantity === 0 ? (
          <span style={{ color: palette.mute }}>—</span>
        ) : r.purchaserNames?.length ? (
          <EllipsisText text={r.purchaserNames.join('、')} />
        ) : (
          (v ?? <span style={{ color: palette.orange }}>未指定</span>)
        ),
    },
    {
      title: '货物',
      key: 'goods',
      width: 320,
      render: (_, r) => {
        if (r.quantity === 0)
          return <span style={{ color: palette.mute }}>—</span>;
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
      title: '采购订单 · 发货单',
      key: 'docs',
      width: 190,
      render: (_, r) =>
        !r.purchaseTracked ? (
          '—'
        ) : (
          <div style={{ fontSize: 12, display: 'grid', gap: 2 }}>
            {(r.purchaseOrders ?? []).map((p) => (
              <button
                type="button"
                className="zhul-link"
                key={p.id}
                onClick={() => history.push(`/purchase/orders/${p.id}`)}
              >
                {p.poNo ?? '草稿采购订单'}
              </button>
            ))}
            {(r.transits ?? []).map((t) => (
              <span key={t.shipmentId} style={{ color: palette.mute }}>
                {t.sdNo} 在途 {t.quantity}
              </span>
            ))}
            {(r.purchaseOrders ?? []).length === 0 && (
              <span style={{ color: palette.mute }}>还没有下采购订单</span>
            )}
          </div>
        ),
    },
    {
      title: '进度',
      dataIndex: 'progressCode',
      width: 120,
      render: (v: string, r) =>
        r.quantity === 0 ? (
          <span style={{ fontSize: 12, color: palette.mute }}>不再跟进</span>
        ) : (
          <div>
            {completed ? (
              <ProgressPill code="COMPLETED" name={o.progressName} />
            ) : (
              <ProgressPill code={v} name={r.progressName} />
            )}
            <NotNeededNote qty={r.notNeededQty} reasons={r.notNeededReasons} />
          </div>
        ),
    },
  ];

  // 勾选型号后，表格上方的批量操作条（原型 B1c）：主操作「更新进度」在最右
  const bulk = o.trackable && selected.length > 0 && (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        flexWrap: 'wrap',
        padding: '8px 12px 8px 16px',
        borderRadius: 8,
        background: palette.primarySoft,
        marginBottom: 12,
      }}
    >
      <b style={{ color: palette.ink }}>已选 {selected.length} 个型号</b>
      <button
        type="button"
        className="zhul-link"
        onClick={() => setSelected([])}
      >
        取消选择
      </button>
      <span style={{ marginLeft: 'auto' }} />
      <Button
        type="text"
        icon={<UserRound size={16} />}
        style={{ color: palette.link }}
        onClick={() => setDialog('purchaser')}
      >
        指定采购员
      </Button>
      <Dropdown
        trigger={['click']}
        menu={{
          items: [
            { key: String(STOCK.SPOT), label: '改为现货' },
            { key: String(STOCK.FUTURES), label: '改为期货' },
          ],
          onClick: ({ key }) => changeStock(Number(key)),
        }}
      >
        <Button
          type="text"
          icon={<ArrowLeftRight size={16} />}
          style={{ color: palette.link }}
        >
          现货 / 期货 <ChevronDown size={14} aria-hidden />
        </Button>
      </Dropdown>
      {canProgress && (
        <Button type="primary" onClick={() => setDialog('progress')}>
          更新进度
        </Button>
      )}
    </div>
  );

  // ---------------------------------------------------------------- 买方 / 条款 / 合计

  const piDiffers =
    !manual &&
    !replacement &&
    o.piTotalAmount != null &&
    Number(o.piTotalAmount) !== Number(o.totalAmount);

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

  /** 金额与条款的一行：左标签、右内容，行间细分隔线 */
  const termRow = (
    label: React.ReactNode,
    value: React.ReactNode,
    opts?: { amount?: boolean; color?: string },
  ) => (
    <div
      key={typeof label === 'string' ? label : undefined}
      style={{
        display: 'flex',
        justifyContent: opts?.amount ? 'space-between' : undefined,
        gap: 16,
        padding: '10px 0',
        borderBottom: `1px solid ${palette.hairline}`,
        fontSize: 13,
      }}
    >
      <span style={{ width: 72, flex: 'none', color: palette.mute }}>
        {label}
      </span>
      <span
        style={{
          ...(opts?.amount ? num : {}),
          color: opts?.color ?? palette.ink,
          minWidth: 0,
        }}
      >
        {value}
      </span>
    </div>
  );

  const showTerms = !manual && !replacement;
  const terms = (
    <Section
      title={showTerms ? '金额与条款' : '金额'}
      style={{ marginTop: 16 }}
      extra={
        // 售后补发订单不收款，不能改费用（audit BUS-068）
        access['sales:order:fee-edit'] &&
        editable &&
        !replacement &&
        (completed ? (
          <span style={{ fontSize: 12, color: palette.mute }}>
            订单已完成，不能修改费用
          </span>
        ) : (
          <Button
            type="text"
            icon={<Pencil size={16} />}
            style={{ color: palette.link }}
            onClick={() => setFeesOpen(true)}
          >
            修改费用
          </Button>
        ))
      }
    >
      <div
        style={{
          display: 'grid',
          gridTemplateColumns:
            showTerms && wide ? 'repeat(2, minmax(0, 1fr))' : '1fr',
          gap: '0 48px',
        }}
      >
        {showTerms && (
          <div>
            {termRow('交期', o.deliveryTime || '—')}
            {termRow('付款条件', o.paymentTerm || '—')}
            {termRow(
              '贸易术语',
              [o.incoterm, o.incotermPlace].filter(Boolean).join(' ') || '—',
            )}
            {termRow('起运港', o.portOfShipment || '—')}
          </div>
        )}
        <div
          style={
            showTerms
              ? undefined
              : { width: '100%', maxWidth: 420, marginLeft: 'auto' }
          }
        >
          {!fee &&
            termRow('小计', formatAmount(o.itemAmount, cur), { amount: true })}
          {o.fees.map((f) =>
            termRow(f.feeName, formatAmount(f.amount, cur), { amount: true }),
          )}
          {o.discountAmount > 0 &&
            termRow('折扣', formatAmount(-o.discountAmount, cur), {
              amount: true,
              color: palette.orange,
            })}
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'baseline',
              gap: 12,
              paddingTop: 12,
            }}
          >
            <b style={{ fontSize: 15, color: palette.ink }}>
              {(o.partialCancels?.length ?? 0) > 0
                ? '订单合计（部分取消后）'
                : '合计'}
            </b>
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
          {piDiffers && (
            <div
              style={{ textAlign: 'right', fontSize: 12, color: palette.mute }}
            >
              PI 合计 {formatAmount(o.piTotalAmount ?? 0, cur)}（发给客户的 PI
              不改）
            </div>
          )}
        </div>
      </div>
    </Section>
  );

  // ---------------------------------------------------------------- 收款

  const canSlip = access['sales:pi:receipt-slip'];
  const canConfirm = access['sales:pi:receipt-confirm'];
  const canPlatform = access['sales:pi:platform-receipt'];
  const canClaim = access['sales:pi:claim-receipt'];
  const receivable = manual && editable;
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
        <CardTitle>收款</CardTitle>
        <HelpTip
          title={
            fee
              ? '费用订单：收款到账后订单自动完成，不走采购和出运'
              : manual
                ? '手动订单：没有 PI，收款直接登记在订单上'
                : `收款登记在 PI ${o.piNo ?? ''} 上，这里只读显示`
          }
        />
        <ReceiptStatusPill status={o.receiptStatus} />
        {!manual && o.piNo && o.piId && (
          <span
            style={{ marginLeft: 'auto', fontSize: 12, color: palette.mute }}
          >
            来自{' '}
            <Link to={PATHS.pi(o.piId)} style={{ fontSize: 12 }}>
              {o.piNo}
            </Link>
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
        {piDiffers && (
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 6 }}>
            按部分取消后的订单合计计算；PI 合计{' '}
            {formatAmount(o.piTotalAmount ?? 0, cur)}
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
                  <Pill tone="gray">{m.channel === 2 ? '线上' : '线下'}</Pill>
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
      <CreditInfo
        credit={o.credit}
        currency={o.currencyCode}
        unpaid={o.remainingAmount}
        onUseCredit={
          manual && o.status === 1
            ? async (amount) => setO(await orderApi.applyCredit(o.id, amount))
            : undefined
        }
      />
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
                    <ShopOutlined style={{ color: palette.info }} />
                  ) : (
                    <BankOutlined style={{ color: palette.green }} />
                  )}
                  <b style={{ color: palette.ink }}>
                    {slip
                      ? '水单'
                      : r.paymentMethod === 'CREDIT'
                        ? '余额抵扣'
                        : r.platformOrderNo
                          ? '平台收款'
                          : r.claimedAt
                            ? '到账（认领）'
                            : '到账'}
                  </b>
                  {r.receiptNo && (
                    <button
                      type="button"
                      className="zhul-link"
                      style={{ fontSize: 12 }}
                      onClick={() => history.push(`/finance/receipts/${r.id}`)}
                    >
                      {r.receiptNo}
                    </button>
                  )}
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
                    <button
                      type="button"
                      className="zhul-link"
                      key={f.fileKey}
                      style={{ fontSize: 12, marginRight: 10 }}
                      onClick={() => openSlip(r.id, i)}
                    >
                      <PaperClipOutlined /> {f.fileName}
                    </button>
                  ))}
                {manual && r.voidable && (
                  <div>
                    <button
                      type="button"
                      className="zhul-link"
                      style={{ fontSize: 12, color: palette.red }}
                      onClick={() => setVoidId(r.id)}
                    >
                      作废
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </Card>
  );

  const replacementCards = replacement && (
    <>
      <Card style={{ padding: 20 }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            marginBottom: 12,
          }}
        >
          <CardTitle>收款</CardTitle>
          <span style={{ marginLeft: 'auto' }}>
            <ReceiptStatusPill status={o.receiptStatus} />
          </span>
        </div>
        {line('订单金额', formatAmount(o.totalAmount, cur))}
        <div style={{ fontSize: 12, color: palette.mute }}>
          售后补发订单不出 PI，不能登记收款、余额抵扣和退款。
        </div>
      </Card>
      {o.cost && (
        <Card style={{ padding: 20, marginTop: 16 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              marginBottom: 12,
            }}
          >
            <CardTitle>成本</CardTitle>
            <span style={{ marginLeft: 'auto' }}>
              <Pill tone="cyan">计入原订单</Pill>
            </span>
          </div>
          <div
            style={{ padding: 14, borderRadius: 12, background: palette.inset }}
          >
            {line('实际成本', formatAmount(o.cost.cost, 'CNY'))}
            <div style={{ fontSize: 12, color: palette.mute }}>
              {o.cost.settled
                ? '成本已确定'
                : `还差：${o.cost.pending.join(' · ')}；还没确定的部分按采购订单单价或采购成本价估算`}
            </div>
          </div>
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 10 }}>
            不单独计算收入与毛利，成本计入原订单{' '}
            {o.originalSoId && (
              <button
                type="button"
                className="zhul-link"
                onClick={() => history.push(`/sales/orders/${o.originalSoId}`)}
              >
                {o.originalSoNo}
              </button>
            )}
          </div>
        </Card>
      )}
    </>
  );

  const feeOrdersCard = (
    <Card style={{ padding: 20, marginTop: 16 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 12,
        }}
      >
        <CardTitle>费用订单</CardTitle>
        {(o.feeOrders?.length ?? 0) === 0 && (
          <span style={{ fontSize: 13, color: palette.mute }}>
            已收齐款或已完成的订单，还要另收运费等费用时新建
          </span>
        )}
        {canFeeOrder && (
          <Button
            type="text"
            icon={<Plus size={16} />}
            style={{ marginLeft: 'auto', color: palette.link }}
            onClick={() => setDialog('feeOrder')}
          >
            新建费用订单
          </Button>
        )}
      </div>
      {(o.feeOrders?.length ?? 0) === 0 ? null : (
        <div style={{ display: 'grid', gap: 8 }}>
          {o.feeOrders?.map((x) => (
            <div
              key={x.id}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '8px 12px',
                borderRadius: 10,
                background: palette.inset,
                opacity: x.status === 2 ? 0.6 : 1,
              }}
            >
              <button
                type="button"
                className="zhul-link"
                onClick={() => history.push(`/sales/orders/${x.id}`)}
              >
                {x.soNo}
              </button>
              <span style={{ ...num, color: palette.ink }}>
                {formatAmount(x.totalAmount, x.currencyCode)}
              </span>
              <span style={{ marginLeft: 'auto' }}>
                {x.status === 2 ? (
                  <Pill tone="gray">已取消</Pill>
                ) : (
                  <ReceiptStatusPill status={x.receiptStatus} />
                )}
              </span>
            </div>
          ))}
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
        <CardTitle>订单毛利</CardTitle>
        <HelpTip
          title={
            o.cost
              ? '毛利 = 收入（毛额 × 结汇汇率）− 实际成本（含开票成本与收款手续费）；还没确定的部分按采购订单单价或采购成本价估算。采购成本与毛利只在系统内显示，不进任何导出单据'
              : '已取消的订单按报价时的采购成本价计算。采购成本与毛利只在系统内显示，不进任何导出单据'
          }
        />
        {o.cost ? (
          o.cost.settled ? (
            <Pill tone="green">已结算</Pill>
          ) : (
            <Pill tone="orange">未结算</Pill>
          )
        ) : (
          m.estimated && <Pill tone="orange">预计</Pill>
        )}
      </div>
      {o.cost && !o.cost.settled && (
        <div
          style={{
            fontSize: 12,
            color: palette.orange,
            marginTop: -4,
            marginBottom: 12,
          }}
        >
          还差：{o.cost.pending.join(' · ')}
        </div>
      )}
      <div style={{ padding: 14, borderRadius: 8, background: palette.inset }}>
        {line(
          '销售额',
          cur === 'CNY'
            ? formatAmount(o.totalAmount, cur)
            : `${formatAmount(o.totalAmount, cur)} ≈ ${formatAmount(m.salesAmountCny, 'CNY')}`,
        )}
        {line(
          o.cost ? '收入' : '实收人民币',
          formatAmount(m.receivedCny, 'CNY'),
        )}
        {line(o.cost ? '实际成本' : '采购成本', formatAmount(m.costCny, 'CNY'))}
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
            {o.cost?.profitRate != null ? ` · ${o.cost.profitRate}%` : ''}
          </span>
          <b
            style={{
              ...num,
              fontSize: 20,
              color: deltaOf(m.profitCny),
            }}
          >
            {formatAmount(m.profitCny, 'CNY')}
          </b>
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
    </Card>
  );

  // ---------------------------------------------------------------- 页面

  const notices = (
    <>
      {o.migrated && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          title="这张订单从旧系统迁移，只能查看，不能修改、取消或登记收款；需要退款时可以照常登记退款。"
        />
      )}
      {!active && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          title={`订单已取消${o.cancelledByName ? `（${o.cancelledByName} ${formatDateTime(o.cancelledAt).slice(0, 16)}）` : ''}：${o.cancelReason ?? ''}`}
        />
      )}
      {active && o.payLater && o.receiptStatus === RECEIPT_STATUS.NONE && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          title={`客户还没有付款（先采购后收款：${o.payLaterReason ?? ''}${o.payLaterByName ? ` · ${o.payLaterByName}` : ''} ${formatDateTime(o.payLaterAt).slice(5, 10)}），请跟进收款`}
          action={
            o.piId ? (
              <button
                type="button"
                className="zhul-link"
                onClick={() => history.push(PATHS.pi(o.piId as number))}
              >
                去 PI 上传水单
              </button>
            ) : undefined
          }
        />
      )}
      {completed && (
        <Alert
          type="success"
          showIcon
          style={{ marginBottom: 16 }}
          title={`${fee ? '收款已到账' : '客户已收货'}，订单已完成（${formatDateTime(o.completedAt).slice(0, 16)}）`}
        />
      )}
    </>
  );

  const liveItems = o.items.filter((i) => i.quantity > 0);
  const itemsSection = !fee && (
    <Section
      title="型号明细"
      help={`成交内容（型号、单价、费用）不能直接改：减少型号数量用「部分取消」，其他变更请取消订单（手动创建的订单，有「修改订单费用」权限的人可以改费用）。销售日期、${manual ? '约定付款日、' : ''}进度、采购员、现货 / 期货可以随时改，改动写操作日志`}
      meta={`${liveItems.length} 个型号 · ${liveItems.reduce((n, i) => n + i.quantity, 0)} 件`}
      style={{ marginTop: 16 }}
    >
      {bulk}
      <Table<OrderItem>
        rowKey="id"
        size="middle"
        columns={columns}
        dataSource={o.items}
        pagination={false}
        scroll={{ x: tableScrollX(columns) }}
        rowSelection={
          o.trackable
            ? {
                selectedRowKeys: selected,
                onChange: (keys) => setSelected(keys as number[]),
                getCheckboxProps: (r) => ({ disabled: r.quantity === 0 }),
              }
            : undefined
        }
      />
    </Section>
  );

  const afterSalesCard = afterSales.length > 0 && (
    <Card
      title="售后单"
      extra={
        <span style={{ fontSize: 12, color: palette.mute }}>
          {afterSales.length} 张
        </span>
      }
    >
      <div style={{ display: 'grid', gap: 8 }}>
        {afterSales.map((x) => (
          <div
            key={x.id}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              padding: '8px 12px',
              borderRadius: 8,
              background: palette.inset,
            }}
          >
            <button
              type="button"
              className="zhul-link"
              onClick={() => history.push(`/sales/after-sales/${x.id}`)}
            >
              {x.asNo}
            </button>
            <Pill tone={LEVEL_TONE[x.levelCode] ?? 'gray'}>
              {levelShort(x.levelCode, x.levelName)}
            </Pill>
            <span
              style={{
                fontSize: 12,
                color: palette.sub,
                flex: 1,
                minWidth: 0,
              }}
            >
              {[x.itemSummary, x.reasonName, x.solutionNames.join('、')]
                .filter(Boolean)
                .join(' · ')}
            </span>
            <Pill tone={AS_STATUS_TONE[x.status] ?? 'gray'}>
              {x.statusName}
            </Pill>
          </div>
        ))}
      </div>
    </Card>
  );

  // ---------------------------------------------------------------- 页头操作
  // 原型 B1、B1c：主按钮只有「发货通知」；售后、客户已收货为文字按钮；退款、部分取消、取消、删除收进「更多」

  const canRefundRequest =
    !access['finance:refund:edit'] &&
    !replacement &&
    !!access.salesAfterSales &&
    (o.receivedAmount ?? 0) > 0;
  const canRefund =
    !!access['finance:refund:edit'] &&
    o.trackable &&
    active &&
    (o.receivedAmount ?? 0) > 0;
  const tracking = !fee && (o.trackable || o.partialCancelable);
  const moreItems = [
    ...(tracking && canRefundRequest
      ? [{ key: 'refundRequest', icon: <Undo2 size={16} />, label: '申请退款' }]
      : []),
    ...(tracking && canRefund
      ? [{ key: 'refund', icon: <Undo2 size={16} />, label: '登记退款' }]
      : []),
    ...(tracking && o.partialCancelable
      ? [
          {
            key: 'partialCancel',
            icon: <CircleMinus size={16} />,
            label: completed ? '部分取消（订单已完成，请走售后）' : '部分取消',
            disabled: completed,
          },
        ]
      : []),
  ];
  const dangerItems = [
    ...((fee ? editable && !completed : tracking && o.trackable)
      ? [
          {
            key: 'cancel',
            icon: <Ban size={16} />,
            label: '取消订单',
            danger: true,
          },
        ]
      : []),
    ...(canDelete
      ? [
          {
            key: 'delete',
            icon: <Trash2 size={16} />,
            label: '删除订单',
            danger: true,
          },
        ]
      : []),
  ];
  const menuItems = [
    ...moreItems,
    ...(moreItems.length && dangerItems.length
      ? [{ type: 'divider' as const }]
      : []),
    ...dangerItems,
  ];
  const onMore = (key: string) => {
    if (key === 'refundRequest') setDialog('refundRequest');
    else if (key === 'refund') setRefundOpen(true);
    else if (key === 'partialCancel') setDialog('partialCancel');
    else if (key === 'cancel') setCancelOpen(true);
    else if (key === 'delete') setDeleteOpen(true);
  };

  const headerActions = (
    <>
      {tracking && access.salesAfterSales && (
        <Button
          type="text"
          icon={<Headset size={16} />}
          style={{ color: palette.link }}
          onClick={() => setDialog('afterSales')}
        >
          售后
        </Button>
      )}
      {tracking && o.trackable && (
        <Tooltip
          title={o.completable ? undefined : '所有型号都出运后才能确认收货'}
        >
          {/* 置灰按钮收不到悬停和焦点，外包一层可聚焦的元素挂提示 */}
          <span tabIndex={o.completable ? undefined : 0}>
            <Button
              type="text"
              icon={<Check size={16} />}
              disabled={!o.completable}
              style={o.completable ? { color: palette.link } : undefined}
              onClick={complete}
            >
              客户已收货
            </Button>
          </span>
        </Tooltip>
      )}
      {menuItems.length > 0 && (
        <Dropdown
          trigger={['click']}
          placement="bottomRight"
          menu={{ items: menuItems, onClick: ({ key }) => onMore(key) }}
        >
          <Button
            type="text"
            shape="circle"
            icon={<Ellipsis size={18} />}
            aria-label="更多操作"
          />
        </Dropdown>
      )}
      {tracking && o.trackable && access['sales:order:ship-notice'] && (
        <Button
          type="primary"
          icon={<Send size={16} />}
          onClick={() => setDialog('notice')}
        >
          发货通知
        </Button>
      )}
    </>
  );

  return (
    <div>
      <PageHeader
        title={o.soNo}
        tags={
          <>
            <MigratedPill migrated={o.migrated} />
            <ProgressPill code={o.progressCode} name={o.progressName} />
            {!fee && <StockPill type={o.stockType} />}
            <ReceiptStatusPill status={o.receiptStatus} />
            {replacement && <Pill tone="gray">售后补发</Pill>}
            {fee && <Pill tone="orange">费用订单</Pill>}
            {o.payLater && <Pill tone="orange">先采购后收款</Pill>}
          </>
        }
        sub={
          <>
            {o.customerName} ·{' '}
            <SourceNos
              mode="inline"
              refs={orderSourceRefs(o)}
              empty="手动创建"
            />
            {replacement && o.originalSoId && (
              <>
                {' '}
                · 原订单{' '}
                <Link to={PATHS.order(o.originalSoId)}>{o.originalSoNo}</Link>
              </>
            )}{' '}
            · {o.ownerName ?? '—'} · {formatDateTime(o.createTime).slice(0, 16)}{' '}
            创建
          </>
        }
        extra={headerActions}
      />
      {notices}
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
        <div style={{ minWidth: 0, display: 'grid', gap: 16 }}>
          {info}
          {!fee && stepper}
          <ChainCard
            type="order"
            id={o.id}
            reloadKey={o.status}
            style={{ marginBottom: 0 }}
          />
        </div>
        <div style={{ minWidth: 0 }}>
          {replacement ? (
            replacementCards
          ) : (
            <>
              {receipts}
              {!fee && marginCard}
            </>
          )}
        </div>
      </div>
      {itemsSection}
      {(afterSales.length > 0 ||
        (!fee && (o.partialCancels?.length ?? 0) > 0)) && (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: wide
              ? 'repeat(auto-fit, minmax(360px, 1fr))'
              : '1fr',
            gap: 16,
            marginTop: 16,
            alignItems: 'start',
          }}
        >
          {afterSalesCard}
          {!fee && <PartialCancelRecords order={o} style={{ marginTop: 0 }} />}
        </div>
      )}
      {terms}
      {!replacement &&
        (canFeeOrder || (o.feeOrders?.length ?? 0) > 0) &&
        feeOrdersCard}
      <div style={{ marginTop: 16 }}>
        <OrderFilesCard
          order={o}
          canEdit={editable && !!access['sales:order:attachment']}
          onChanged={load}
        />
        {o.cost && <OrderCostCard cost={o.cost} />}
        {!fee && (
          <OrderOutbounds soId={o.id} version={version} onChanged={load} />
        )}
      </div>
      <RefundModal
        open={refundOpen}
        soId={o.id}
        onClose={() => setRefundOpen(false)}
        onDone={load}
      />

      <CreateFeeOrderModal
        order={o}
        open={dialog === 'feeOrder'}
        onClose={() => setDialog(undefined)}
        onDone={(x) => {
          setDialog(undefined);
          history.push(`/sales/orders/${x.id}`);
        }}
      />
      <CreateAfterSalesModal
        open={dialog === 'afterSales'}
        order={o}
        onClose={() => setDialog(undefined)}
        onDone={(x) => {
          setDialog(undefined);
          history.push(`/sales/after-sales/${x.id}`);
        }}
      />
      <RefundRequestModal
        open={dialog === 'refundRequest'}
        soId={o.id}
        onClose={() => setDialog(undefined)}
        onDone={() => {
          setDialog(undefined);
        }}
      />
      <PartialCancelModal
        order={o}
        open={dialog === 'partialCancel'}
        onClose={() => setDialog(undefined)}
        onDone={(next) => {
          done(next);
          setVersion((v) => v + 1);
        }}
      />
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
      {feesOpen && (
        <EditFeesModal
          order={o}
          onClose={() => setFeesOpen(false)}
          onDone={(next) => {
            setFeesOpen(false);
            done(next);
          }}
        />
      )}
      <SalesDateModal
        order={o}
        open={dialog === 'salesDate'}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <PaymentDueDateModal
        order={o}
        open={dialog === 'paymentDueDate'}
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
                个型号已向供应商下单，取消后请采购员处理对应采购订单；同一张 PI
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
                回到「已发送」并解锁，可以出新版本后重新转成订单；来源报价单与询盘单的成交状态按剩余有效订单重新计算。
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
      {canDelete && (
        <DeleteOrderModal
          order={o}
          open={deleteOpen}
          onClose={() => setDeleteOpen(false)}
          onDeleted={() => {
            setDeleteOpen(false);
            // 退回上一页（列表返回时自动刷新）；没有上一页才换成列表（audit UI-085）
            tabsActions.backOr('/sales/orders');
          }}
        />
      )}
    </div>
  );
};

export default OrderDetail;
