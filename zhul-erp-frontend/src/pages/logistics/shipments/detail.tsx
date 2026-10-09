import {
  ApartmentOutlined,
  EditOutlined,
  FileExcelOutlined,
  FileTextOutlined,
  InfoCircleOutlined,
  RocketOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { history, Link, useAccess, useParams } from '@umijs/max';
import { App, Button, Skeleton, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { ReasonModal } from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { kg, LOGISTICS_PATHS, ShStatusPill } from '../components';
import {
  type DirectRef,
  type Logistics,
  logisticsApi,
  readBizError,
} from '../service';
import {
  ConfirmDirectDrawer,
  DocsModal,
  FaceSheets,
  ItemsModal,
  ShipModal,
} from './dialogs';

type Dialog = 'docs' | 'ship' | 'items' | 'void';

interface BoxRow {
  key: number;
  obNo: string;
  soNo?: string;
  direct: boolean;
  boxNo: number;
  dims: string;
  gross: number;
  net?: number | null;
  chargeable: number;
  items: string;
  freight?: number;
}

const ShipmentDetail: React.FC = () => {
  const { id: idParam } = useParams<{ id: string }>();
  const id = Number(idParam);
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const access = useAccess();
  const canEdit = !!access['logistics:shipment:edit'];
  const [sh, setSh] = useState<Logistics>();
  const [error, setError] = useState<string>();
  const [dialog, setDialog] = useState<Dialog>();
  const [confirming, setConfirming] = useState<DirectRef>();

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setSh(await logisticsApi.detail(id));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!sh) return <Skeleton active paragraph={{ rows: 12 }} />;

  const pending = sh.status === 1;
  const voided = sh.status === 3;
  const done = (next: Logistics) => {
    setSh(next);
    setDialog(undefined);
    setConfirming(undefined);
  };
  const waiting = (sh.directs ?? []).filter((d) => !d.confirmed);

  const boxes: BoxRow[] = (sh.outbounds ?? []).flatMap((o) =>
    o.boxes.map((b) => ({
      key: b.id,
      obNo: o.obNo,
      soNo: o.soNo,
      direct: o.source === 2,
      boxNo: b.boxNo,
      dims: `${b.length}×${b.width}×${b.height}`,
      gross: b.grossWeight,
      net: b.netWeight,
      chargeable: sh.boxChargeable?.[b.id] ?? b.chargeable,
      items: b.items.map((i) => `${i.model} × ${i.quantity}`).join('、'),
      freight: sh.boxFreight?.[b.id],
    })),
  );

  const exportDoc = async (
    gid: number,
    kind: 'ci' | 'pl',
    format: 'xlsx' | 'pdf',
  ) => {
    try {
      await logisticsApi.exportDoc(gid, kind, format);
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const head = (
    icon: React.ReactNode,
    title: React.ReactNode,
    hint?: React.ReactNode,
  ) => (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        marginBottom: 12,
      }}
    >
      <span style={{ color: palette.link }}>{icon}</span>
      <b style={{ color: palette.ink }}>{title}</b>
      <span style={{ flex: 1 }} />
      {hint && (
        <span style={{ fontSize: 12, color: palette.mute }}>{hint}</span>
      )}
    </div>
  );

  const field = (label: string, value: React.ReactNode) => (
    <div style={{ display: 'flex', gap: 12, padding: '4px 0' }}>
      <span style={{ width: 80, color: palette.mute }}>{label}</span>
      <span style={{ flex: 1, color: palette.ink }}>{value ?? '—'}</span>
    </div>
  );

  return (
    <div>
      <PageTitle
        crumbs={[
          <a key="list" onClick={() => history.push(LOGISTICS_PATHS.shipments)}>
            出运单
          </a>,
          sh.shNo,
        ]}
        title={
          <>
            {sh.shNo}
            <ShStatusPill value={sh.status}>{sh.statusName}</ShStatusPill>
            {sh.reconciled && <Pill tone="green">已对账</Pill>}
          </>
        }
        description={`${sh.customerName ?? ''} · 货代 ${sh.forwarderName ?? ''}（体积系数 ${sh.volumeDivisor}）· ${sh.ownerName ?? '—'}${
          sh.voidReason ? ` · 作废原因：${sh.voidReason}` : ''
        }`}
        actions={
          canEdit &&
          !voided && (
            <>
              {pending && (
                <>
                  <Button
                    icon={<StopOutlined />}
                    onClick={() => setDialog('void')}
                  >
                    作废
                  </Button>
                  <Button
                    icon={<EditOutlined />}
                    onClick={() => setDialog('items')}
                  >
                    调整货物
                  </Button>
                </>
              )}
              <Button
                icon={<FileTextOutlined />}
                onClick={() => setDialog('docs')}
              >
                生成 CI/PL
              </Button>
              {pending ? (
                <Button
                  type="primary"
                  icon={<RocketOutlined />}
                  disabled={waiting.length > 0}
                  title={waiting.length ? '直发货确认实收后才能登记出运' : ''}
                  onClick={() => setDialog('ship')}
                >
                  登记出运
                </Button>
              ) : (
                <Button
                  icon={<RocketOutlined />}
                  onClick={() => setDialog('ship')}
                >
                  修改运单
                </Button>
              )}
            </>
          )
        }
      />

      {waiting.length > 0 && (
        <Card style={{ padding: 20, marginBottom: 16 }}>
          {head(
            <InfoCircleOutlined />,
            '直发货代 · 待确认实收',
            '按货代实收确认数量并填箱规，系统自动补入库单和出库单',
          )}
          <div style={{ display: 'grid', gap: 10 }}>
            {waiting.map((d) => (
              <div
                key={d.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  flexWrap: 'wrap',
                  padding: '12px 14px',
                  borderRadius: 10,
                  background: palette.inset,
                }}
              >
                <div>
                  <b style={{ color: palette.ink }}>{d.sdNo}</b>
                  <div style={{ fontSize: 12, color: palette.mute }}>
                    {[
                      d.poNo,
                      d.supplierName,
                      [d.carrier, d.trackingNo].filter(Boolean).join(' '),
                    ]
                      .filter(Boolean)
                      .join(' · ')}
                  </div>
                </div>
                <span style={{ flex: 1 }} />
                <span style={{ fontSize: 13, color: palette.sub }}>
                  {d.items
                    .map((i) => `${i.model} 发货 ${i.quantity}`)
                    .join('、')}
                </span>
                {canEdit && pending && (
                  <Button
                    type="primary"
                    size="small"
                    onClick={() => setConfirming(d)}
                  >
                    确认实收
                  </Button>
                )}
              </div>
            ))}
          </div>
        </Card>
      )}

      <Card style={{ padding: 20, marginBottom: 16 }}>
        {head(
          <ApartmentOutlined />,
          `箱子（${sh.boxCount} 箱 · 计费重 ${kg(sh.chargeableWeight)}）`,
          '国际运费按计费重分到箱，再按货值分到型号行',
        )}
        <Table<BoxRow>
          rowKey="key"
          size="small"
          pagination={false}
          dataSource={boxes}
          locale={{ emptyText: '还没有箱子；直发货确认实收后出现' }}
          scroll={{ x: 900 }}
          columns={[
            {
              title: '来源',
              key: 'src',
              width: 170,
              render: (_, r) => (
                <div>
                  <b style={{ color: palette.ink }}>{r.obNo}</b>
                  {r.direct && (
                    <span
                      style={{
                        marginLeft: 6,
                        fontSize: 12,
                        color: palette.violet,
                      }}
                    >
                      直发
                    </span>
                  )}
                  <div style={{ fontSize: 12, color: palette.mute }}>
                    {r.soNo}
                  </div>
                </div>
              ),
            },
            { title: '箱号', dataIndex: 'boxNo', width: 60 },
            { title: '尺寸 cm', dataIndex: 'dims', width: 110 },
            {
              title: '毛重 · 净重',
              key: 'w',
              width: 120,
              render: (_, r) =>
                `${Number(r.gross).toFixed(2)}${r.net ? ` · ${Number(r.net).toFixed(2)}` : ''}`,
            },
            {
              title: '计费重',
              dataIndex: 'chargeable',
              width: 90,
              render: (v: number) => <b>{Number(v).toFixed(2)}</b>,
            },
            { title: '箱内型号', dataIndex: 'items' },
            {
              title: '国际运费分摊',
              dataIndex: 'freight',
              width: 130,
              render: (v?: number) =>
                v === undefined ? '—' : <b>{formatAmount(v, 'CNY')}</b>,
            },
          ]}
        />
      </Card>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(420px, 1fr))',
          gap: 16,
          alignItems: 'start',
        }}
      >
        <Card style={{ padding: 20 }}>
          {head(<FileTextOutlined />, '单证组')}
          {(sh.docGroups ?? []).length === 0 ? (
            <div style={{ color: palette.mute }}>
              还没有生成 CI / PL
              {canEdit && !voided ? '，点右上角「生成 CI/PL」' : ''}
            </div>
          ) : (
            <div style={{ display: 'grid', gap: 10 }}>
              {(sh.docGroups ?? []).map((g) => (
                <div
                  key={g.id}
                  style={{
                    padding: '12px 14px',
                    borderRadius: 10,
                    background: palette.inset,
                  }}
                >
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      flexWrap: 'wrap',
                    }}
                  >
                    <FileExcelOutlined style={{ color: palette.green }} />
                    <b style={{ color: palette.ink }}>
                      {g.ciNo} · {g.plNo}
                    </b>
                    <span style={{ fontSize: 12, color: palette.mute }}>
                      {g.soNos.join('、')}
                    </span>
                  </div>
                  <div
                    style={{
                      display: 'flex',
                      gap: 14,
                      marginTop: 8,
                      fontSize: 13,
                    }}
                  >
                    <span style={{ color: palette.mute }}>CI</span>
                    <a onClick={() => exportDoc(g.id, 'ci', 'xlsx')}>Excel</a>
                    <a onClick={() => exportDoc(g.id, 'ci', 'pdf')}>PDF</a>
                    <span style={{ color: palette.mute, marginLeft: 8 }}>
                      PL
                    </span>
                    <a onClick={() => exportDoc(g.id, 'pl', 'xlsx')}>Excel</a>
                    <a onClick={() => exportDoc(g.id, 'pl', 'pdf')}>PDF</a>
                  </div>
                  {g.paymentRef && (
                    <div
                      style={{
                        fontSize: 12,
                        color: palette.mute,
                        marginTop: 4,
                      }}
                    >
                      付款参考：{g.paymentRef}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </Card>
        <Card style={{ padding: 20 }}>
          {head(<RocketOutlined />, '运单与运费')}
          {field(
            '运单号',
            sh.waybillNo ? `${sh.carrier ?? ''} ${sh.waybillNo}` : undefined,
          )}
          {field('出运日期', sh.shippedDate)}
          {field(
            '实际运费',
            sh.freight === null || sh.freight === undefined
              ? undefined
              : formatAmount(sh.freight, 'CNY'),
          )}
          {field('面单', <FaceSheets value={sh.faceSheets ?? []} />)}
          {field(
            '订单',
            <span>
              {sh.orders.map((o, i) => (
                <React.Fragment key={o.id}>
                  {i > 0 && '、'}
                  <Link to={LOGISTICS_PATHS.salesOrder(o.id)}>{o.soNo}</Link>
                </React.Fragment>
              ))}
            </span>,
          )}
          {pending && (
            <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
              货代申报发出后点「登记出运」
            </div>
          )}
        </Card>
      </div>

      <ConfirmDirectDrawer
        sh={sh}
        direct={confirming}
        onClose={() => setConfirming(undefined)}
        onDone={done}
      />
      <DocsModal
        sh={sh}
        open={dialog === 'docs'}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <ShipModal
        sh={sh}
        open={dialog === 'ship'}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <ItemsModal
        sh={sh}
        open={dialog === 'items'}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <ReasonModal
        open={dialog === 'void'}
        title={`作废出运单 · ${sh.shNo}`}
        description="作废后出库单与直发货回到「待出运的货」，CI / PL 一并作废。"
        okText="作废"
        danger
        onCancel={() => setDialog(undefined)}
        onOk={async (reason) => {
          try {
            done(await logisticsApi.void(sh.id, reason));
            message.success('已作废');
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
    </div>
  );
};

export default ShipmentDetail;
