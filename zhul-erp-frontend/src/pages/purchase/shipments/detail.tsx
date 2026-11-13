import { history, Link, useAccess, useParams } from '@umijs/max';
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
import { useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import {
  ArrivalCell,
  Card,
  DOC_PATHS,
  ReasonModal,
  relatedRows,
  ShipStatusPill,
  trackingText,
  WAREHOUSE_PATHS,
  WarehousePageTitle,
} from '@/pages/warehouse/components';
import { AcceptDrawer } from '@/pages/warehouse/receipts/dialogs';
import {
  type ShipmentDetail as Detail,
  readBizError,
  type ShipmentItem,
  shipmentApi,
} from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { MenuLink } from '@/utils/menuName';
import ShipmentDrawer from './ShipmentDrawer';

/** 发货单详情（spec purchase/supplier-shipment）：在途时可改、可作废、可验收入库 */
const ShipmentDetailPage: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const wide = useWide();
  const [d, setD] = useState<Detail>();
  const [error, setError] = useState<string>();
  const [dialog, setDialog] = useState<'edit' | 'void' | 'accept'>();
  usePageTitle(d ? `发货单 ${d.shipment.sdNo}` : undefined, d?.shipment.sdNo);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setD(await shipmentApi.detail(id));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id]);
  useEffect(() => {
    load();
  }, [load]);
  const closeDialog = useCallback(() => setDialog(undefined), []);

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!d) return <Skeleton active paragraph={{ rows: 12 }} />;

  const s = d.shipment;
  const inTransit = s.status === 1;
  const voided = s.status === 3;
  const canShip = !!access['purchase:shipment:create'];
  const canReceive = !!access['warehouse:receipt:create'];
  const totalQty = s.items.reduce((n, i) => n + i.quantity, 0);

  const columns: TableColumnsType<ShipmentItem> = [
    { title: '#', key: 'no', width: 48, render: (_, __, i) => i + 1 },
    ...docBrandColumns<ShipmentItem>((x) => x),
    {
      title: '型号',
      dataIndex: 'model',
      width: 220,
      render: (_, x) => <DocModelCell model={x.model} brand={x.brand} />,
    },
    {
      title: '订单数量',
      dataIndex: 'orderQty',
      width: 90,
      render: (v?: number) => v ?? '—',
    },
    { title: '本次发货', dataIndex: 'quantity', width: 90 },
    {
      title: '已入库',
      dataIndex: 'receivedQty',
      width: 80,
      render: (v?: number) => v ?? 0,
    },
  ];

  return (
    <div>
      <WarehousePageTitle
        crumbs={[
          <MenuLink key="list" to={WAREHOUSE_PATHS.shipments} />,
          s.sdNo,
        ]}
        title={
          <>
            {s.sdNo}
            <ShipStatusPill value={s.status}>{s.statusName}</ShipStatusPill>
          </>
        }
        description={
          <>
            {s.supplierName ?? '—'} ·{' '}
            <SourceNos
              mode="inline"
              refs={[{ no: s.poNo, href: WAREHOUSE_PATHS.order(s.poId) }]}
            />{' '}
            · {s.purchaserName ?? '—'} ·{' '}
            {formatDateTime(s.createTime).slice(0, 16)} 创建
          </>
        }
        actions={
          inTransit && (
            <>
              {canShip && !s.logisticsId && (
                <Button danger onClick={() => setDialog('void')}>
                  作废
                </Button>
              )}
              {canShip && !s.logisticsId && (
                <Button onClick={() => setDialog('edit')}>修改</Button>
              )}
              {canReceive && !s.directForwarderId && (
                <Button type="primary" onClick={() => setDialog('accept')}>
                  验收入库
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
        <div style={{ minWidth: 0 }}>
          <InfoGrid
            items={[
              {
                label: '采购订单',
                value: <Link to={WAREHOUSE_PATHS.order(s.poId)}>{s.poNo}</Link>,
              },
              {
                label: '快递 · 单号',
                value: trackingText(s.carrier, s.trackingNo),
              },
              { label: '发货日期', value: s.shipDate },
              {
                label: '预计到货',
                value: inTransit ? (
                  <ArrivalCell
                    date={s.expectedArrivalDate}
                    overdue={s.arrivalOverdue}
                  />
                ) : (
                  s.expectedArrivalDate
                ),
              },
              { label: '登记来源', value: s.sourceName },
              { label: '直发货代', value: s.directForwarderName },
              { label: '作废原因', value: s.voidReason, hidden: !voided },
            ]}
          />
          <Card style={{ padding: 20, marginBottom: 16 }}>
            <SectionTitle>
              明细
              <span
                style={{
                  color: palette.mute,
                  fontSize: 13,
                  fontWeight: 400,
                  marginLeft: 8,
                }}
              >
                {s.items.length} 个型号 · {totalQty} 件
              </span>
            </SectionTitle>
            <Table<ShipmentItem>
              rowKey="id"
              size="middle"
              pagination={false}
              columns={columns}
              dataSource={s.items}
              scroll={{ x: 'max-content' }}
            />
          </Card>
          <DocFilesCard
            ownerType="SHIPMENT"
            files={d.attachments}
            remark={s.note}
            editable={!voided && !!access.purchaseShipments}
            filesEditable={inTransit && canShip}
            onAttach={(ids) => shipmentApi.attach(s.id, ids)}
            onRemove={(fid) => shipmentApi.removeAttachment(s.id, fid)}
            onRemark={(text) => shipmentApi.remark(s.id, text)}
            onChanged={load}
          />
        </div>
        <div style={{ minWidth: 0 }}>
          <RelatedDocsCard docs={relatedRows(d.related)} />
          <DocLogCard logs={d.logs} />
        </div>
      </div>
      <ShipmentDrawer
        open={dialog === 'edit'}
        shipmentId={s.id}
        onClose={closeDialog}
        onSaved={() => {
          setDialog(undefined);
          load();
        }}
      />
      <AcceptDrawer
        shipmentId={dialog === 'accept' ? s.id : undefined}
        onClose={closeDialog}
        onDone={(r) => {
          setDialog(undefined);
          history.push(DOC_PATHS.receipt(r.receipt.id));
        }}
      />
      <ReasonModal
        open={dialog === 'void'}
        title={`作废发货单 ${s.sdNo}`}
        description="作废后这批数量回到未发；已入库的发货单不能作废。"
        placeholder="如：供应商发错单号、重复登记"
        okText="作废"
        danger
        onCancel={closeDialog}
        onOk={async (reason) => {
          try {
            await shipmentApi.void(s.id, reason);
            setDialog(undefined);
            message.success('发货单已作废');
            load();
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
    </div>
  );
};

export default ShipmentDetailPage;
