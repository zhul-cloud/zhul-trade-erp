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
  type RelatedDoc,
  RelatedDocsCard,
  SectionTitle,
} from '@/components/DocFields/DocPage';
import SourceNos from '@/components/DocFields/SourceNos';
import { DocFilesCard } from '@/components/DocFiles';
import { usePageTitle } from '@/components/PageTabs/hooks';
import { useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { MenuLink } from '@/utils/menuName';
import {
  Card,
  DOC_PATHS,
  ReasonModal,
  ReceiptStatusPill,
  relatedRows,
  WAREHOUSE_PATHS,
  WarehousePageTitle,
} from '../components';
import {
  type ReceiptDetail as Detail,
  type ReceiptItem,
  readBizError,
  receiptApi,
} from '../service';

/** 采购入库单详情（spec warehouse/purchase-receipt）：数量是收货的事实不改，录错整张冲销 */
const ReceiptDetailPage: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const wide = useWide();
  const [d, setD] = useState<Detail>();
  const [error, setError] = useState<string>();
  const [reversing, setReversing] = useState(false);
  usePageTitle(d ? `采购入库单 ${d.receipt.grNo}` : undefined, d?.receipt.grNo);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setD(await receiptApi.detail(id));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id]);
  useEffect(() => {
    load();
  }, [load]);

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!d) return <Skeleton active paragraph={{ rows: 12 }} />;

  const r = d.receipt;
  const valid = r.status === 1;
  const canEdit = !!access['warehouse:receipt:create'];
  const editable = valid && !!access.warehouseReceipts;
  const hasDefect = d.items.some((i) => i.defectiveQty > 0);
  const sum = (k: 'shippedQty' | 'receivedQty' | 'qualifiedQty') =>
    d.items.reduce((n, i) => n + (i[k] ?? 0), 0);

  const columns: TableColumnsType<ReceiptItem> = [
    { title: '#', key: 'no', width: 48, render: (_, __, i) => i + 1 },
    ...docBrandColumns<ReceiptItem>((x) => x),
    {
      title: '型号',
      dataIndex: 'model',
      width: 220,
      render: (_, x) => <DocModelCell model={x.model} brand={x.brand} />,
    },
    { title: '发货', dataIndex: 'shippedQty', width: 70 },
    { title: '实收', dataIndex: 'receivedQty', width: 70 },
    { title: '合格', dataIndex: 'qualifiedQty', width: 70 },
    {
      title: '不良',
      dataIndex: 'defectiveQty',
      width: 120,
      render: (v: number, x) => (
        <div>
          <span style={{ color: v ? palette.red : undefined }}>{v}</span>
          {x.note && (
            <div style={{ color: palette.mute, fontSize: 12 }}>{x.note}</div>
          )}
        </div>
      ),
    },
    {
      title: '差异',
      key: 'diff',
      width: 80,
      render: (_, x) => {
        const gap = x.shippedQty - x.receivedQty;
        if (gap > 0)
          return <span style={{ color: palette.orange }}>少 {gap}</span>;
        if (gap < 0)
          return <span style={{ color: palette.cyan }}>多 {-gap}</span>;
        return 0;
      },
    },
  ];

  const refs = relatedRows(d.related);
  const shoots = refs.filter((x) => x.label === '实拍任务');
  const related: RelatedDoc[] = [
    ...refs.filter((x) => x.label !== '实拍任务'),
    ...d.discrepancies.map((x) => ({
      label: '到货差异',
      no: `${x.typeName} ${x.quantity} · ${x.model}`,
      href: `${WAREHOUSE_PATHS.shipments}?tab=diffs`,
      statusName: x.statusName,
      tone: x.status === 1 ? ('orange' as const) : ('green' as const),
    })),
    ...(shoots.length
      ? [
          {
            label: '实拍任务',
            no: `${shoots.length} 个型号`,
            href: WAREHOUSE_PATHS.shoots,
            statusName: shoots.some((x) => x.statusName === '待拍摄')
              ? '待拍摄'
              : shoots[0].statusName,
            tone: shoots.some((x) => x.statusName === '待拍摄')
              ? ('gray' as const)
              : shoots[0].tone,
          },
        ]
      : []),
  ];

  return (
    <div>
      <WarehousePageTitle
        crumbs={[<MenuLink key="list" to={WAREHOUSE_PATHS.receipts} />, r.grNo]}
        title={
          <>
            {r.grNo}
            <ReceiptStatusPill value={r.status}>
              {r.statusName}
            </ReceiptStatusPill>
          </>
        }
        description={
          <>
            {r.supplierName ?? '—'} ·{' '}
            <SourceNos
              mode="inline"
              refs={[{ no: r.sdNo, href: DOC_PATHS.shipment(r.shipmentId) }]}
            />{' '}
            · {r.receivedByName ?? '—'} ·{' '}
            {formatDateTime(r.createTime).slice(0, 16)} 创建
          </>
        }
        actions={
          d.reversible &&
          canEdit && (
            <Button danger onClick={() => setReversing(true)}>
              冲销
            </Button>
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
                value: <Link to={WAREHOUSE_PATHS.order(r.poId)}>{r.poNo}</Link>,
              },
              {
                label: '发货单',
                value: (
                  <Link to={DOC_PATHS.shipment(r.shipmentId)}>{r.sdNo}</Link>
                ),
              },
              { label: '收货日期', value: r.receivedDate },
              { label: '收货人', value: r.receivedByName },
              { label: '冲销原因', value: r.reverseReason, hidden: valid },
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
                发货 {sum('shippedQty')} · 实收 {sum('receivedQty')} · 合格{' '}
                {sum('qualifiedQty')}
              </span>
            </SectionTitle>
            <Table<ReceiptItem>
              rowKey="id"
              size="middle"
              pagination={false}
              columns={columns}
              dataSource={d.items}
              scroll={{ x: 'max-content' }}
            />
          </Card>
          <DocFilesCard
            remark={r.note}
            editable={editable}
            onRemark={(text) => receiptApi.remark(r.id, text)}
            onChanged={load}
            groups={[
              {
                label: '验收照片',
                ownerType: 'RECEIPT',
                files: d.attachments,
                editable,
                onAttach: (ids) => receiptApi.attach(r.id, ids),
                onRemove: (fid) => receiptApi.removeAttachment(r.id, fid),
              },
              ...(hasDefect
                ? [
                    {
                      label: '不良照片',
                      ownerType: 'RECEIPT_DEFECT' as const,
                      files: d.defectAttachments ?? [],
                      editable,
                      onAttach: (ids: number[]) =>
                        receiptApi.attach(r.id, ids, true),
                      onRemove: (fid: number) =>
                        receiptApi.removeAttachment(r.id, fid, true),
                    },
                  ]
                : []),
            ]}
          />
        </div>
        <div style={{ minWidth: 0 }}>
          <RelatedDocsCard docs={related} />
          <DocLogCard logs={d.logs} />
        </div>
      </div>
      <ReasonModal
        open={reversing}
        title={`冲销采购入库单 ${r.grNo}`}
        description="录错时整张冲销：采购入库单作废，发货单回到「在途」，它的差异与实拍任务一起作废，之后可以重新验收。"
        placeholder="如：数量录错"
        okText="冲销"
        danger
        onCancel={() => setReversing(false)}
        onOk={async (reason) => {
          try {
            await receiptApi.reverse(r.id, reason);
            setReversing(false);
            message.success('已冲销，发货单回到在途');
            load();
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
    </div>
  );
};

export default ReceiptDetailPage;
