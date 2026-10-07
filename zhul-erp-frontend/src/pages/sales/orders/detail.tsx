import {
  BankOutlined,
  FileTextOutlined,
  LockOutlined,
  PaperClipOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { history, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Alert, App, Button, Skeleton, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { formatMargin } from '@/pages/quotation/calc';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import {
  Card,
  ChainCard,
  KIND,
  OrderStatusPill,
  PATHS,
  Pill,
  partyAddress,
  ReceiptStatusPill,
  SalesPageTitle,
} from '../components';
import { ReasonModal } from '../pi/dialogs';
import {
  type Order,
  orderApi,
  type Party,
  type PiItem,
  piApi,
  readBizError,
} from '../service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

const OrderDetail: React.FC = () => {
  const { id: idParam } = useParams<{ id: string }>();
  const id = Number(idParam);
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const wide = useWide();
  const [o, setO] = useState<Order>();
  const [error, setError] = useState<string>();
  const [cancelOpen, setCancelOpen] = useState(false);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setO(await orderApi.detail(id));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!o) return <Skeleton active paragraph={{ rows: 12 }} />;

  const cur = o.currencyCode;
  const active = o.status === 1;

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
          <div style={{ fontSize: 12, color: palette.mute }}>
            {[
              p.taxId && `TAX ID ${p.taxId}`,
              p.contact && `Attn: ${p.contact}`,
              p.phone,
            ]
              .filter(Boolean)
              .join(' · ')}
          </div>
        </>
      ) : (
        <div style={{ color: palette.mute, fontSize: 13 }}>—</div>
      )}
    </div>
  );

  const columns: TableColumnsType<PiItem> = [
    { title: '#', key: 'no', width: 44, render: (_, __, i) => i + 1 },
    {
      title: '型号 / 品牌 · 来源',
      key: 'model',
      width: 280,
      render: (_, r) => (
        <div>
          <div style={{ fontWeight: 600, color: palette.ink }}>{r.model}</div>
          <div style={{ fontSize: 12, color: palette.mute }}>
            {[r.brand, r.category, r.quotationNo, r.inquiryCode]
              .filter(Boolean)
              .join(' · ')}
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
      title: 'HS 编码 · 原产国',
      key: 'hs',
      width: 160,
      render: (_, r) =>
        [r.hsCode, r.originCountry].filter(Boolean).join(' · ') || '—',
    },
    {
      title: '毛利率',
      key: 'margin',
      width: 90,
      align: 'right',
      render: (_, r) => <span style={num}>{formatMargin(r.marginRate)}</span>,
    },
    {
      title: '净利润',
      key: 'profit',
      width: 120,
      align: 'right',
      render: (_, r) =>
        r.netProfit == null ? (
          '—'
        ) : (
          <span style={num}>{formatAmount(r.netProfit, cur)}</span>
        ),
    },
  ];

  const row = (label: string, value: React.ReactNode, color?: string) => (
    <div
      key={label}
      style={{
        display: 'flex',
        justifyContent: 'space-between',
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
      </div>
      <div
        style={{
          padding: 14,
          borderRadius: 12,
          background: palette.inset,
          marginBottom: 12,
        }}
      >
        {row('订单合计', formatAmount(o.totalAmount, cur))}
        {row('已到账', formatAmount(o.receivedAmount ?? 0, cur), palette.green)}
        {row('手续费差额', formatAmount(o.feeDiffAmount ?? 0, cur))}
        {row(
          '剩余',
          formatAmount(o.remainingAmount ?? 0, cur),
          (o.remainingAmount ?? 0) > 0 ? palette.orange : palette.green,
        )}
      </div>
      <div style={{ fontSize: 12, color: palette.mute, marginBottom: 12 }}>
        收款记在 PI {o.piNo} 上，这里只读展示；上传水单、登记到账在 PI 页面操作
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
                  ) : (
                    <BankOutlined style={{ color: palette.green }} />
                  )}
                  <b style={{ color: palette.ink }}>{slip ? '水单' : '到账'}</b>
                  <b style={{ ...num, color: palette.ink }}>
                    {formatAmount(r.amount, cur)}
                  </b>
                  {voided && <Pill tone="mute">已作废</Pill>}
                  <span
                    style={{
                      marginLeft: 'auto',
                      fontSize: 12,
                      color: palette.mute,
                    }}
                  >
                    {r.receiptDate}
                  </span>
                </div>
                <div style={{ fontSize: 12, color: palette.sub, marginTop: 4 }}>
                  {[
                    `${r.operatorName ?? ''}${slip ? '上传' : '登记'}`,
                    !slip && r.bankAccountName,
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
                      onClick={() =>
                        piApi
                          .openSlipFile(o.piId, r.id, i)
                          .catch((e) => message.error((e as Error).message))
                      }
                    >
                      <PaperClipOutlined /> {f.fileName}
                    </a>
                  ))}
              </div>
            );
          })}
        </div>
      )}
    </Card>
  );

  const main = (
    <div style={{ minWidth: 0 }}>
      {active ? (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            padding: '12px 16px',
            borderRadius: 12,
            background: palette.inset,
            border: `1px solid ${palette.hairline}`,
            color: palette.sub,
            fontSize: 13,
            marginBottom: 16,
          }}
        >
          <LockOutlined />
          <b style={{ color: palette.ink }}>订单创建后不能修改</b>
          <span>需要变更时取消订单，在 PI 上出新版本后重新转成订单</span>
        </div>
      ) : (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          title={`订单已取消${o.cancelledByName ? `（${o.cancelledByName} ${formatDateTime(o.cancelledAt).slice(0, 16)}）` : ''}：${o.cancelReason ?? ''}`}
        />
      )}
      <Card style={{ marginBottom: 16, padding: 20 }}>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
            gap: 12,
            marginBottom: 12,
          }}
        >
          {party('买方（Bill To）', o.buyer)}
          {party('收货人（Consignee）', o.consignee)}
        </div>
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
          <span>
            汇率：{cur} {Number(o.exchangeRate).toFixed(6)}
          </span>
          {o.remark && <span>备注：{o.remark}</span>}
        </div>
      </Card>
      <b
        style={{
          display: 'block',
          fontSize: 16,
          color: palette.ink,
          marginBottom: 10,
        }}
      >
        型号（{o.items.length}）
      </b>
      <Table<PiItem>
        rowKey="id"
        size="middle"
        columns={columns}
        dataSource={o.items}
        pagination={false}
        scroll={{ x: 1000 }}
      />
      <div
        style={{ display: 'flex', gap: 16, marginTop: 16, flexWrap: 'wrap' }}
      >
        <Card style={{ padding: 20, flex: 1, minWidth: 260 }}>
          {o.fees.map((f) => row(f.feeName, formatAmount(f.amount, cur)))}
          {o.discountAmount > 0 &&
            row(
              'Discount',
              formatAmount(-o.discountAmount, cur),
              palette.orange,
            )}
          {o.fees.length === 0 && o.discountAmount === 0 && (
            <div style={{ color: palette.mute, fontSize: 13 }}>
              没有费用与折扣
            </div>
          )}
        </Card>
        <Card style={{ padding: 20, width: wide ? 400 : '100%' }}>
          {row('小计', formatAmount(o.itemAmount, cur))}
          {row('费用', formatAmount(o.feeAmount, cur))}
          {o.discountAmount > 0 &&
            row('折扣', formatAmount(-o.discountAmount, cur), palette.orange)}
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'baseline',
              margin: '10px 0',
            }}
          >
            <b style={{ fontSize: 16, color: palette.ink }}>合计</b>
            <b style={{ ...num, fontSize: 24, color: palette.ink }}>
              {formatAmount(o.totalAmount, cur)}
            </b>
          </div>
          {row(
            '合计净利润',
            o.netProfit == null
              ? '—'
              : `${formatAmount(o.netProfit, cur)}${cur !== 'CNY' ? ` / ${formatAmount(o.netProfitCny, 'CNY')}` : ''}`,
            palette.green,
          )}
          {row('合计毛利率', formatMargin(o.marginRate), palette.green)}
        </Card>
      </div>
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
            <OrderStatusPill status={o.status} />
            <ReceiptStatusPill status={o.receiptStatus} />
          </>
        }
        description={
          <>
            {o.customerName} · 来源 PI：
            <a onClick={() => history.push(PATHS.pi(o.piId))}>
              {o.piNo}
              {o.piVersionNo > 1 ? ` Rev.${o.piVersionNo}` : ''}
            </a>{' '}
            · {o.ownerName ?? '—'} · {formatDateTime(o.createTime).slice(0, 16)}{' '}
            创建
          </>
        }
        actions={
          active && (
            <Button
              danger
              icon={<StopOutlined />}
              onClick={() => setCancelOpen(true)}
            >
              取消订单
            </Button>
          )
        }
      />
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: wide
            ? 'minmax(0, 2.6fr) minmax(320px, 1fr)'
            : '1fr',
          gap: 16,
          alignItems: 'start',
        }}
      >
        {main}
        {receipts}
      </div>
      <ReasonModal
        open={cancelOpen}
        title={`取消订单 ${o.soNo}？`}
        description={
          <>
            取消后 PI {o.piNo}{' '}
            回到「已发送」并解锁，可以出新版本后重新转成订单；来源报价单与客户询盘的成交状态按剩余有效订单重新计算。
            {(o.receivedAmount ?? 0) > 0 && (
              <div style={{ marginTop: 8, color: palette.orange }}>
                已到账 {formatAmount(o.receivedAmount ?? 0, cur)} 会保留在 PI
                上，重新转出的订单继续显示。
              </div>
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
