import { Link, useAccess, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Dropdown, Skeleton, Table } from 'antd';
import {
  CircleX,
  Ellipsis,
  Package,
  Pencil,
  RotateCcw,
  Split,
} from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import DocModelCell, {
  docModelColumn,
} from '@/components/DocFields/DocModelCell';
import {
  DocLogCard,
  InfoGrid,
  RelatedDocsCard,
  SectionTitle,
} from '@/components/DocFields/DocPage';
import SourceNos from '@/components/DocFields/SourceNos';
import { DocFilesCard } from '@/components/DocFiles';
import { PageHeader } from '@/components/ListLayout';
import { useOnReactivate, usePageTitle } from '@/components/PageTabs/hooks';
import { useWide } from '@/pages/inquiry/shared/components';
import {
  kg,
  LOGISTICS_PATHS,
  ObStatusPill,
  shortNos,
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
import { tableScrollX } from '@/utils/tableScroll';
import {
  AttachmentWall,
  Card,
  DOC_PATHS,
  ReasonModal,
  relatedRows,
} from '../components';
import { HandOverModal, PackDrawer } from './dialogs';

type Item = Outbound['items'][number];
type Dialog = 'notice' | 'withdraw' | 'pack' | 'handOver' | 'undo' | 'split';

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
  /** 交快递的单：合并打包的整组一起交 */
  const [handing, setHanding] = useState<Outbound[]>([]);
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
  // 从其他页面返回时重新取数（audit UI-055）
  useOnReactivate(load);
  const close = useCallback(() => setDialog(undefined), []);
  const done = () => {
    setDialog(undefined);
    load();
  };

  if (error) return <ErrorHint page message={error} onRetry={load} />;
  if (!ob) return <Skeleton active paragraph={{ rows: 12 }} />;

  const withdrawn = ob.status === 4;
  const canNotice = !!access['sales:order:ship-notice'];
  const canPack = !!access['warehouse:outbound:pack'];
  const totalQty = ob.items.reduce((n, i) => n + i.quantity, 0);
  const grossWeight = ob.boxes.reduce((n, b) => n + Number(b.grossWeight), 0);
  const related = relatedRows(ob.related);
  const sameTicket = (ob.related ?? []).filter((r) => r.type === 'OB');
  /** 合并打包的同组单（箱子、箱数、计费重按整组） */
  const mates = ob.packMates ?? [];
  const merged = mates.length > 0;
  const groupNos = [ob.obNo, ...mates.map((m) => m.obNo)];
  const shorts = shortNos(groupNos);

  const openHandOver = async () => {
    try {
      setHanding([
        ob,
        ...(await Promise.all(mates.map((m) => outboundApi.detail(m.id)))),
      ]);
      setDialog('handOver');
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const columns: TableColumnsType<Item> = [
    {
      title: '#',
      fixed: 'left' as const,
      key: 'no',
      width: 48,
      render: (_, __, i) => i + 1,
    },
    docModelColumn<Item>((x) => x, {
      title: '型号',
      dataIndex: 'model',
      // 1366 下左栏约 664：各列合计 648，不横向滚动（UI-117）
      width: 200,
      render: (_, x) => (
        <DocModelCell
          model={x.model}
          brand={x.brand}
          productId={x.productId}
          candidate={x.productCandidate}
        />
      ),
    }),
    {
      title: '数量',
      align: 'right' as const,
      dataIndex: 'quantity',
      width: 80,
    },
    {
      title: '已装箱',
      dataIndex: 'packedQty',
      align: 'right' as const,
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
            {merged && i.obNo ? `${shorts.get(i.obNo)} · ` : ''}
            {i.model} × {i.quantity}
          </div>
        )),
    },
  ];

  const moreItems = [
    ...(ob.status === 1 && canNotice && ob.source === 1
      ? [
          {
            key: 'withdraw',
            icon: <CircleX size={16} />,
            label: '撤回',
            danger: true,
          },
        ]
      : []),
    ...(ob.status === 3 && canPack && !ob.logisticsId
      ? [
          {
            key: 'undo',
            icon: <RotateCcw size={16} />,
            label: '撤销交快递',
            danger: true,
          },
        ]
      : []),
  ];

  return (
    <div>
      <PageHeader
        title={ob.obNo}
        tags={
          <>
            {/* 页头只放一个流程状态；属性写在说明行（ui-redesign S14） */}
            <ObStatusPill value={ob.status}>{ob.statusName}</ObStatusPill>
          </>
        }
        sub={
          <>
            {merged && '合并打包 · '}
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
        // 页头操作（照销售订单详情）：打包或交快递为主按钮，修改通知、修改装箱、拆开为文字按钮，撤回、撤销交快递收进「更多」
        extra={
          <>
            {ob.status === 1 && canNotice && ob.source === 1 && (
              <Button
                type="text"
                icon={<Pencil size={16} />}
                style={{ color: palette.link }}
                onClick={() => setDialog('notice')}
              >
                修改通知
              </Button>
            )}
            {ob.status === 2 && canPack && (
              <Button
                type="text"
                icon={<Package size={16} />}
                style={{ color: palette.link }}
                onClick={() => setDialog('pack')}
              >
                修改装箱
                {merged && '（整组）'}
              </Button>
            )}
            {merged && ob.status === 2 && canPack && (
              <Button
                type="text"
                icon={<Split size={16} />}
                style={{ color: palette.link }}
                onClick={() => setDialog('split')}
              >
                拆开
              </Button>
            )}
            {moreItems.length > 0 && (
              <Dropdown
                trigger={['click']}
                placement="bottomRight"
                menu={{
                  items: moreItems,
                  onClick: ({ key }) => setDialog(key as 'withdraw' | 'undo'),
                }}
              >
                <Button
                  type="text"
                  shape="circle"
                  icon={<Ellipsis size={18} />}
                  aria-label="更多操作"
                />
              </Dropdown>
            )}
            {ob.status === 1 && canPack && (
              <Button type="primary" onClick={() => setDialog('pack')}>
                打包
                {merged && '（整组）'}
              </Button>
            )}
            {ob.status === 2 && canPack && (
              <Button type="primary" onClick={openHandOver}>
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
          {merged && (
            <div
              style={{
                marginBottom: 16,
                padding: '12px 16px',
                borderRadius: 12,
                background: palette.orangeSoft,
              }}
            >
              <b style={{ color: palette.orange }}>
                合并打包：与{' '}
                {mates.map((m, i) => (
                  <React.Fragment key={m.id}>
                    {i > 0 && '、'}
                    <Link
                      to={DOC_PATHS.outbound(m.id)}
                      className="zhul-link zhul-link-doc"
                    >
                      {m.obNo}
                    </Link>
                    {m.soNo && `（${m.soNo}）`}
                  </React.Fragment>
                ))}{' '}
                共用 {ob.boxCount} 箱
              </b>
              <div style={{ marginTop: 4, fontSize: 13, color: palette.sub }}>
                {mates.length + 1}{' '}
                张单要一起交快递、一起放进出运单；业务员要修改或撤回发货通知时，先在这里「拆开」。
              </div>
            </div>
          )}
          <InfoGrid
            items={[
              {
                label: '销售订单',
                value: (
                  <Link
                    className="zhul-link zhul-link-doc"
                    to={LOGISTICS_PATHS.salesOrder(ob.soId)}
                  >
                    {ob.soNo}
                  </Link>
                ),
              },
              {
                label: '货代',
                value: ob.pickupLabel ? (
                  <div>
                    {ob.forwarderName}
                    <div style={{ fontSize: 12, color: palette.mute }}>
                      {ob.pickupLabel}
                    </div>
                  </div>
                ) : (
                  ob.forwarderName
                ),
              },
              {
                label: '通知时间',
                value: formatDateTime(ob.createTime).slice(0, 16),
              },
              { label: '发货来源', value: ob.sourceName },
              {
                label: '箱数',
                value: ob.boxCount
                  ? `${merged ? '整组 ' : ''}${ob.boxCount} 箱`
                  : undefined,
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
              scroll={{ x: tableScrollX(columns) }}
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
                scroll={{ x: tableScrollX(boxColumns) }}
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
        outbounds={handing}
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
        open={dialog === 'split'}
        title="拆开合并打包"
        description={`${groupNos.join(' 和 ')} 都回到「待打包」，原来的 ${ob.boxCount} 箱作废，之后可以单独打包或重新合并。`}
        placeholder="如：客户要求分开发"
        okText="拆开"
        danger
        onCancel={close}
        onOk={async (reason) => {
          try {
            await outboundApi.split(ob.id, reason);
            message.success('已拆开，几张单都回到待打包');
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
