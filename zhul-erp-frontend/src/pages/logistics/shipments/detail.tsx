import {
  EditOutlined,
  FileExcelOutlined,
  FileTextOutlined,
  RocketOutlined,
  StopOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { Link, useAccess, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Skeleton, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import DocModelCell, {
  docBrandColumns,
} from '@/components/DocFields/DocModelCell';
import {
  DocLogCard,
  InfoGrid,
  RelatedDocsCard,
  SectionTitle,
} from '@/components/DocFields/DocPage';
import SourceNos from '@/components/DocFields/SourceNos';
import { DocFilesCard } from '@/components/DocFiles';
import { usePageTitle } from '@/components/PageTabs/hooks';
import {
  Card,
  MigratedPill,
  PageTitle,
  Pill,
  useWide,
} from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import {
  DOC_PATHS,
  ReasonModal,
  relatedRows,
} from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { MenuLink } from '@/utils/menuName';
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

interface ItemRow {
  key: string;
  brand?: string;
  category?: string;
  model: string;
  quantity: number;
  srcNo: string;
  srcHref: string;
  soNo?: string;
  soId?: number;
  /** 直发货还没确认实收 */
  waiting: boolean;
}

interface BoxRow {
  key: number;
  obId: number;
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
  const access = useAccess() as Record<string, boolean>;
  const wide = useWide();
  const canEdit = !!access['logistics:shipment:edit'];
  const [sh, setSh] = useState<Logistics>();
  usePageTitle(sh ? `出运单 ${sh.shNo}` : undefined, sh?.shNo);
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
      obId: o.id,
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

  const field = (label: string, value: React.ReactNode) => (
    <div style={{ display: 'flex', gap: 12, padding: '4px 0' }}>
      <span style={{ width: 80, color: palette.mute }}>{label}</span>
      <span style={{ flex: 1, color: palette.ink }}>{value ?? '—'}</span>
    </div>
  );
  const soId = (no?: string) => sh.orders.find((o) => o.soNo === no)?.id;
  const rows: ItemRow[] = [
    ...(sh.outbounds ?? []).flatMap((o) =>
      o.items.map((i) => ({
        key: `ob-${i.id}`,
        brand: i.brand,
        category: i.category,
        model: i.model,
        quantity: i.quantity,
        srcNo: o.obNo,
        srcHref: DOC_PATHS.outbound(o.id),
        soNo: o.soNo,
        soId: o.soId,
        waiting: false,
      })),
    ),
    ...waiting.flatMap((d) =>
      d.items.map((i) => ({
        key: `sd-${i.shipmentItemId}`,
        brand: i.brand,
        model: i.model,
        quantity: i.quantity,
        srcNo: d.sdNo,
        srcHref: DOC_PATHS.shipment(d.id),
        soNo: d.soNos[0],
        soId: soId(d.soNos[0]),
        waiting: true,
      })),
    ),
  ];
  const docCount = (sh.outbounds ?? []).length + waiting.length;
  const itemColumns: TableColumnsType<ItemRow> = [
    { title: '#', key: 'no', width: 48, render: (_, __, i) => i + 1 },
    ...docBrandColumns<ItemRow>((x) => x),
    {
      title: '型号',
      dataIndex: 'model',
      width: 220,
      render: (_, x) => (
        <div>
          <DocModelCell model={x.model} brand={x.brand} />
          {x.waiting && (
            <div style={{ fontSize: 12, color: palette.orange }}>
              直发 · 待确认实收
            </div>
          )}
        </div>
      ),
    },
    { title: '数量', dataIndex: 'quantity', width: 80 },
    {
      title: '销售出库单',
      key: 'src',
      width: 160,
      render: (_, x) => <Link to={x.srcHref}>{x.srcNo}</Link>,
    },
    {
      title: '销售订单',
      key: 'so',
      width: 160,
      render: (_, x) =>
        x.soId ? (
          <Link to={LOGISTICS_PATHS.salesOrder(x.soId)}>{x.soNo}</Link>
        ) : (
          (x.soNo ?? '—')
        ),
    },
  ];
  const sectionHint = (text: React.ReactNode) => (
    <span
      style={{
        color: palette.mute,
        fontSize: 13,
        fontWeight: 400,
        marginLeft: 8,
      }}
    >
      {text}
    </span>
  );

  const main = (
    <>
      <InfoGrid
        items={[
          {
            label: '货代',
            value: `${sh.forwarderName ?? '—'}（体积系数 ${sh.volumeDivisor}）`,
          },
          {
            label: '箱数',
            value: sh.boxCount ? `${sh.boxCount} 箱` : undefined,
          },
          {
            label: '计费重',
            value: sh.boxCount ? kg(sh.chargeableWeight) : undefined,
          },
          {
            label: '承运商 · 运单号',
            value: sh.waybillNo
              ? `${sh.carrier ?? ''} ${sh.waybillNo}`
              : undefined,
          },
          { label: '出运日期', value: sh.shippedDate },
          {
            label: '运费',
            value:
              sh.freight === null || sh.freight === undefined
                ? undefined
                : formatAmount(sh.freight, 'CNY'),
          },
          {
            label: '对账',
            value:
              sh.status === 2
                ? sh.reconciled
                  ? '已对账'
                  : '未对账'
                : undefined,
          },
          { label: '作废原因', value: sh.voidReason, hidden: !voided },
        ]}
      />
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <SectionTitle>
          明细
          {sectionHint(
            `${docCount} 张单据 · ${rows.reduce((n, r) => n + r.quantity, 0)} 件`,
          )}
        </SectionTitle>
        {waiting.length > 0 &&
          waiting.map((d) => (
            <div
              key={d.id}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                flexWrap: 'wrap',
                padding: '10px 14px',
                borderRadius: 10,
                marginBottom: 12,
                background: palette.orangeSoft,
                color: palette.orange,
                fontSize: 13,
              }}
            >
              <WarningOutlined />
              <span style={{ flex: 1 }}>
                直发货 {d.sdNo}
                {d.supplierName ? `（${d.supplierName}）` : ''}{' '}
                待确认实收，确认后才能登记出运
              </span>
              {canEdit && pending && !sh.migrated && (
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
        <Table<ItemRow>
          rowKey="key"
          size="middle"
          pagination={false}
          columns={itemColumns}
          dataSource={rows}
          scroll={{ x: 'max-content' }}
        />
      </Card>
      {boxes.length > 0 && (
        <Card style={{ padding: 20, marginBottom: 16 }}>
          <SectionTitle
            extra={
              <span style={{ fontSize: 12, color: palette.mute }}>
                国际运费按计费重分到箱，再按货值分到型号行
              </span>
            }
          >
            装箱
            {sectionHint(
              `${sh.boxCount} 箱 · 计费重 ${kg(sh.chargeableWeight)}`,
            )}
          </SectionTitle>
          <Table<BoxRow>
            rowKey="key"
            size="middle"
            pagination={false}
            dataSource={boxes}
            scroll={{ x: 'max-content' }}
            columns={[
              {
                title: '销售出库单',
                key: 'src',
                width: 160,
                render: (_, r) => (
                  <div>
                    <Link to={DOC_PATHS.outbound(r.obId)}>{r.obNo}</Link>
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
              { title: '箱内型号', width: 220, dataIndex: 'items' },
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
      )}
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <SectionTitle>出运信息</SectionTitle>
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
          'CI / PL',
          (sh.docGroups ?? []).length === 0 ? (
            <span style={{ color: palette.mute }}>
              还没有生成
              {canEdit && !voided && !sh.migrated
                ? '，点右上角「生成 CI/PL」'
                : ''}
            </span>
          ) : (
            <div style={{ display: 'grid', gap: 8 }}>
              {(sh.docGroups ?? []).map((g) => (
                <div key={g.id}>
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      flexWrap: 'wrap',
                    }}
                  >
                    <FileExcelOutlined style={{ color: palette.green }} />
                    <b>
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
                      marginTop: 4,
                      fontSize: 13,
                    }}
                  >
                    <span style={{ color: palette.mute }}>CI</span>
                    <button
                      type="button"
                      className="zhul-link"
                      onClick={() => exportDoc(g.id, 'ci', 'xlsx')}
                    >
                      Excel
                    </button>
                    <button
                      type="button"
                      className="zhul-link"
                      onClick={() => exportDoc(g.id, 'ci', 'pdf')}
                    >
                      PDF
                    </button>
                    <span style={{ color: palette.mute, marginLeft: 8 }}>
                      PL
                    </span>
                    <button
                      type="button"
                      className="zhul-link"
                      onClick={() => exportDoc(g.id, 'pl', 'xlsx')}
                    >
                      Excel
                    </button>
                    <button
                      type="button"
                      className="zhul-link"
                      onClick={() => exportDoc(g.id, 'pl', 'pdf')}
                    >
                      PDF
                    </button>
                  </div>
                  {g.paymentRef && (
                    <div style={{ fontSize: 12, color: palette.mute }}>
                      付款参考：{g.paymentRef}
                    </div>
                  )}
                </div>
              ))}
            </div>
          ),
        )}
        {pending && (
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
            货代申报发出后点「登记出运」
          </div>
        )}
      </Card>
      <DocFilesCard
        ownerType="LOGISTICS_DOC"
        files={sh.attachments ?? []}
        remark={sh.note}
        editable={!voided && !sh.migrated && !!access.logisticsShipments}
        onAttach={(ids) => logisticsApi.attach(sh.id, ids)}
        onRemove={(fid) => logisticsApi.removeAttachment(sh.id, fid)}
        onRemark={(text) => logisticsApi.remark(sh.id, text)}
        onChanged={load}
      />
    </>
  );

  return (
    <div>
      <PageTitle
        crumbs={[
          <MenuLink key="list" to={LOGISTICS_PATHS.shipments} />,
          sh.shNo,
        ]}
        title={
          <>
            {sh.shNo}
            <ShStatusPill value={sh.status}>{sh.statusName}</ShStatusPill>
            {sh.reconciled && <Pill tone="green">已对账</Pill>}
            <MigratedPill migrated={sh.migrated} />
          </>
        }
        description={
          <>
            {sh.customerName ?? '—'} ·{' '}
            <SourceNos
              mode="inline"
              refs={(sh.outbounds ?? []).map((o) => ({
                no: o.obNo,
                href: DOC_PATHS.outbound(o.id),
              }))}
            />{' '}
            · {sh.ownerName ?? '—'} ·{' '}
            {formatDateTime(sh.createTime).slice(0, 16)} 创建
          </>
        }
        actions={
          canEdit &&
          !voided &&
          !sh.migrated && (
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
                  修改出运信息
                </Button>
              )}
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
        <div style={{ minWidth: 0 }}>{main}</div>
        <div style={{ minWidth: 0 }}>
          <RelatedDocsCard docs={relatedRows(sh.related)} />
          <DocLogCard logs={sh.logs} />
        </div>
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
        description="作废后销售出库单与直发货回到「待出运的货」，CI / PL 一并作废。"
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
