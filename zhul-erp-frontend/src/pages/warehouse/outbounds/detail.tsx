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
import { useWide } from '@/pages/inquiry/shared/components';
import {
  kg,
  LOGISTICS_PATHS,
  ObStatusPill,
} from '@/pages/logistics/components';
import {
  type Outbound,
  type OutboundBox,
  outboundApi,
  readBizError,
} from '@/pages/logistics/service';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { ShipNoticeModal } from '@/pages/sales/orders/shipNotice';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { MenuLink } from '@/utils/menuName';
import {
  AttachmentWall,
  Card,
  DOC_PATHS,
  ReasonModal,
  relatedRows,
  WarehousePageTitle,
} from '../components';
import { HandOverModal, PackDrawer } from './dialogs';

type Item = Outbound['items'][number];
type Dialog = 'notice' | 'withdraw' | 'pack' | 'handOver' | 'undo';

/** 销售出库单详情（spec warehouse/outbound）：通知、装箱、国内快递、出运 */
const OutboundDetailPage: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const wide = useWide();
  const [ob, setOb] = useState<Outbound>();
  const [error, setError] = useState<string>();
  const [dialog, setDialog] = useState<Dialog>();
  usePageTitle(ob ? `销售出库单 ${ob.obNo}` : undefined, ob?.obNo);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setOb(await outboundApi.detail(id));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id]);
  useEffect(() => {
    load();
  }, [load]);
  const close = useCallback(() => setDialog(undefined), []);
  const done = () => {
    setDialog(undefined);
    load();
  };

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!ob) return <Skeleton active paragraph={{ rows: 12 }} />;

  const withdrawn = ob.status === 4;
  const canNotice = !!access['sales:order:ship-notice'];
  const canPack = !!access['warehouse:outbound:pack'];
  const totalQty = ob.items.reduce((n, i) => n + i.quantity, 0);
  const grossWeight = ob.boxes.reduce((n, b) => n + Number(b.grossWeight), 0);
  const related = relatedRows(ob.related);
  const sameTicket = (ob.related ?? []).filter((r) => r.type === 'OB');

  const columns: TableColumnsType<Item> = [
    { title: '#', key: 'no', width: 48, render: (_, __, i) => i + 1 },
    ...docBrandColumns<Item>((x) => x),
    {
      title: '型号',
      dataIndex: 'model',
      width: 220,
      render: (_, x) => <DocModelCell model={x.model} brand={x.brand} />,
    },
    { title: '数量', dataIndex: 'quantity', width: 80 },
    {
      title: '已装箱',
      dataIndex: 'packedQty',
      width: 80,
      render: (v?: number) => v ?? 0,
    },
  ];
  const boxColumns: TableColumnsType<OutboundBox> = [
    { title: '箱号', dataIndex: 'boxNo', width: 60 },
    {
      title: '尺寸 cm',
      key: 'dim',
      width: 140,
      render: (_, b) => `${b.length} × ${b.width} × ${b.height}`,
    },
    {
      title: '毛重',
      dataIndex: 'grossWeight',
      width: 100,
      render: (v: number) => kg(v),
    },
    {
      title: '计费重',
      dataIndex: 'chargeable',
      width: 100,
      render: (v: number) => kg(v),
    },
    {
      title: '箱内型号',
      key: 'items',
      render: (_, b) =>
        b.items.map((i) => (
          <div key={i.outboundItemId}>
            {i.model} × {i.quantity}
          </div>
        )),
    },
  ];

  return (
    <div>
      <WarehousePageTitle
        crumbs={[
          <MenuLink key="list" to={LOGISTICS_PATHS.outbounds} />,
          ob.obNo,
        ]}
        title={
          <>
            {ob.obNo}
            <ObStatusPill value={ob.status}>{ob.statusName}</ObStatusPill>
          </>
        }
        description={
          <>
            {ob.customerName ?? '—'} ·{' '}
            <SourceNos
              mode="inline"
              refs={[
                { no: ob.soNo, href: LOGISTICS_PATHS.salesOrder(ob.soId) },
              ]}
            />{' '}
            · {ob.ownerName ?? '—'} ·{' '}
            {formatDateTime(ob.createTime).slice(0, 16)} 创建
          </>
        }
        actions={
          <>
            {ob.status === 1 && canNotice && ob.source === 1 && (
              <>
                <Button danger onClick={() => setDialog('withdraw')}>
                  撤回
                </Button>
                <Button onClick={() => setDialog('notice')}>修改通知</Button>
              </>
            )}
            {ob.status === 3 && canPack && !ob.logisticsId && (
              <Button onClick={() => setDialog('undo')}>撤销交快递</Button>
            )}
            {(ob.status === 1 || ob.status === 2) && canPack && (
              <Button
                type={ob.status === 1 ? 'primary' : 'default'}
                onClick={() => setDialog('pack')}
              >
                {ob.status === 1 ? '打包' : '修改装箱'}
              </Button>
            )}
            {ob.status === 2 && canPack && (
              <Button type="primary" onClick={() => setDialog('handOver')}>
                交快递
              </Button>
            )}
          </>
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
                label: '销售订单',
                value: (
                  <Link to={LOGISTICS_PATHS.salesOrder(ob.soId)}>
                    {ob.soNo}
                  </Link>
                ),
              },
              { label: '货代', value: ob.forwarderName },
              {
                label: '通知时间',
                value: formatDateTime(ob.createTime).slice(0, 16),
              },
              { label: '发货来源', value: ob.sourceName },
              {
                label: '箱数',
                value: ob.boxCount ? `${ob.boxCount} 箱` : undefined,
              },
              {
                label: '计费重',
                value: ob.boxCount ? kg(ob.chargeableWeight) : undefined,
              },
              {
                label: '撤回原因',
                value: ob.withdrawReason,
                hidden: !withdrawn,
              },
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
                {ob.items.length} 个型号 · {totalQty} 件
              </span>
            </SectionTitle>
            <Table<Item>
              rowKey="id"
              size="middle"
              pagination={false}
              columns={columns}
              dataSource={ob.items}
              scroll={{ x: 'max-content' }}
            />
          </Card>
          {ob.boxes.length > 0 && (
            <Card style={{ padding: 20, marginBottom: 16 }}>
              <SectionTitle
                extra={
                  ob.packedByName && (
                    <span style={{ color: palette.mute, fontSize: 12 }}>
                      {ob.packedByName} ·{' '}
                      {formatDateTime(ob.packedAt).slice(5, 16)} 打包
                    </span>
                  )
                }
              >
                装箱
                <span
                  style={{
                    color: palette.mute,
                    fontSize: 13,
                    fontWeight: 400,
                    marginLeft: 8,
                  }}
                >
                  {ob.boxes.length} 箱 · 毛重 {kg(grossWeight)}
                </span>
              </SectionTitle>
              <Table<OutboundBox>
                rowKey="id"
                size="middle"
                pagination={false}
                columns={boxColumns}
                dataSource={ob.boxes}
                scroll={{ x: 'max-content' }}
              />
              {(ob.packingAttachments?.length ?? 0) > 0 && (
                <div style={{ marginTop: 16 }}>
                  <div
                    style={{
                      color: palette.sub,
                      fontSize: 13,
                      marginBottom: 8,
                    }}
                  >
                    打包照片
                  </div>
                  <AttachmentWall
                    ownerType="PACKING"
                    value={ob.packingAttachments ?? []}
                  />
                </div>
              )}
            </Card>
          )}
          {ob.courier && (
            <InfoGrid
              title="国内快递"
              items={[
                {
                  label: '快递 · 单号',
                  value: [ob.courier.carrier, ob.courier.trackingNo]
                    .filter(Boolean)
                    .join(' '),
                },
                {
                  label: '运费',
                  value:
                    ob.courier.outboundCount > 1
                      ? `${formatAmount(ob.courier.freight, 'CNY')}（本单 ${formatAmount(ob.courier.share, 'CNY')}）`
                      : formatAmount(ob.courier.freight, 'CNY'),
                },
                { label: '垫付人', value: ob.courier.payerName },
                { label: '交快递日期', value: ob.courier.sentDate },
                {
                  label: '同一票',
                  hidden: sameTicket.length === 0,
                  value: sameTicket.map((r, i) => (
                    <React.Fragment key={r.id}>
                      {i > 0 && '、'}
                      <Link to={DOC_PATHS.outbound(r.id)}>{r.no}</Link>
                    </React.Fragment>
                  )),
                },
              ]}
            />
          )}
          <DocFilesCard
            ownerType="OUTBOUND"
            files={ob.attachments ?? []}
            remark={ob.note}
            editable={!withdrawn}
            onAttach={(ids) => outboundApi.attach(ob.id, ids)}
            onRemove={(fid) => outboundApi.removeAttachment(ob.id, fid)}
            onRemark={(text) => outboundApi.remark(ob.id, text)}
            onChanged={load}
          />
        </div>
        <div style={{ minWidth: 0 }}>
          <RelatedDocsCard docs={related} />
          <DocLogCard logs={ob.logs} />
        </div>
      </div>
      <ShipNoticeModal
        open={dialog === 'notice'}
        soId={ob.soId}
        outbound={ob}
        onClose={close}
        onDone={done}
      />
      <PackDrawer
        id={dialog === 'pack' ? ob.id : undefined}
        onClose={close}
        onDone={done}
      />
      <HandOverModal
        open={dialog === 'handOver'}
        outbounds={[ob]}
        onClose={close}
        onDone={done}
      />
      <ReasonModal
        open={dialog === 'withdraw'}
        title={`撤回发货通知 · ${ob.obNo}`}
        description="撤回后仓库不再打包这张销售出库单，数量回到可出库。"
        okText="撤回"
        danger
        onCancel={close}
        onOk={async (reason) => {
          try {
            await outboundApi.withdraw(ob.id, reason);
            message.success('已撤回');
            done();
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
      <ReasonModal
        open={dialog === 'undo'}
        title={`撤销交快递 · ${ob.obNo}`}
        description="撤销后销售出库单回到「待交快递」，订单型号进度随之回退；共用快递的其他销售出库单重新分摊运费。"
        okText="撤销"
        danger
        onCancel={close}
        onOk={async (reason) => {
          try {
            await outboundApi.undoHandOver(ob.id, reason);
            message.success('已撤销交快递');
            done();
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
    </div>
  );
};

export default OutboundDetailPage;
