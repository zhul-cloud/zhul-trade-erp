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
import { Card, Pill, useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import {
  PR_STATUS_TONE,
  type ReturnReceipt,
  type ReturnReceiptItem,
  RQ_STATUS_TONE,
  RT_STATUS,
  RT_STATUS_TONE,
  returnApi,
  type SalesReturn,
  salesReturnApi,
  TH_STATUS_TONE,
} from '@/pages/sales/after-sales/service';
import { PATHS } from '@/pages/sales/components';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { MenuLink } from '@/utils/menuName';
import { ReasonModal, WarehousePageTitle } from '../components';
import { DISP_TONE, DisposeModal, ReceiveModal } from './dialogs';

/** 退货入库单详情（spec warehouse/sales-return）：验收、撤回入库、逐行处理去向 */
const ReturnReceiptDetail: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const wide = useWide();
  const [r, setR] = useState<ReturnReceipt>();
  const [th, setTh] = useState<SalesReturn>();
  const [error, setError] = useState<string>();
  const [dialog, setDialog] = useState<'receive' | 'dispose' | 'unreceive'>();
  usePageTitle(r ? `退货入库单 ${r.receiptNo}` : undefined, r?.receiptNo);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      const x = await returnApi.detail(id);
      setR(x);
      // 销售退货单与退款申请：没有售后单的查看权限时不显示
      salesReturnApi
        .detail(x.salesReturnId)
        .then(setTh)
        .catch(() => setTh(undefined));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id]);
  useEffect(() => {
    load();
  }, [load]);

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!r) return <Skeleton active paragraph={{ rows: 12 }} />;

  const canReceive = !!access['warehouse:return:receive'];
  const canDispose = !!access['purchase:return:dispose'];
  const voided = r.status === RT_STATUS.VOID;
  const done = () => {
    setDialog(undefined);
    load();
  };
  const dispsOf = (itemId: number) =>
    r.dispositions.filter((d) => d.itemId === itemId);

  const columns: TableColumnsType<ReturnReceiptItem> = [
    {
      title: '#',
      key: 'no',
      width: 48,
      render: (_, __, i) => i + 1,
    },
    ...docBrandColumns<ReturnReceiptItem>((x) => x),
    {
      title: '型号',
      dataIndex: 'model',
      width: 200,
      render: (_, x) => <DocModelCell model={x.model} brand={x.brand} />,
    },
    { title: '退货', dataIndex: 'quantity', width: 70 },
    {
      title: '实收',
      dataIndex: 'receivedQty',
      width: 70,
      render: (v) => v ?? '—',
    },
    {
      title: '合格',
      dataIndex: 'qualifiedQty',
      width: 70,
      render: (v) => v ?? '—',
    },
    { title: '已处理', dataIndex: 'disposedQty', width: 80 },
    {
      title: '去向',
      key: 'disp',
      width: 280,
      render: (_, x) => {
        const list = dispsOf(x.id);
        if (list.length === 0)
          return r.receivedDate ? (
            <span style={{ color: palette.orange }}>未处理</span>
          ) : (
            <span style={{ color: palette.mute }}>—</span>
          );
        return (
          <div style={{ display: 'grid', gap: 4 }}>
            {list.map((d) => (
              <div
                key={d.id}
                style={{ display: 'flex', gap: 6, alignItems: 'center' }}
              >
                <Pill tone={DISP_TONE[d.disposition] ?? 'gray'}>
                  {d.dispositionName}
                </Pill>
                <span style={{ fontSize: 13 }}>
                  × {d.quantity}
                  {d.supplierName ? ` · ${d.supplierName}` : ''}
                  {d.purchaseReturnId && d.purchaseReturnNo ? (
                    <>
                      {' · '}
                      <Link to={PATHS.purchaseReturn(d.purchaseReturnId)}>
                        {d.purchaseReturnNo}
                      </Link>
                    </>
                  ) : null}
                  {d.remark ? ` · ${d.remark}` : ''}
                </span>
              </div>
            ))}
          </div>
        );
      },
    },
    {
      title: '说明',
      dataIndex: 'note',
      width: 160,
      render: (v?: string) => v || '—',
    },
  ];

  const main = (
    <>
      <InfoGrid
        items={[
          {
            label: '售后单',
            value: <Link to={PATHS.afterSales(r.afterSalesId)}>{r.asNo}</Link>,
          },
          {
            label: '销售订单',
            value: <Link to={PATHS.order(r.soId)}>{r.soNo}</Link>,
          },
          {
            label: '客户寄回',
            value: [r.carrier, r.trackingNo].filter(Boolean).join(' '),
          },
          { label: '收货日期', value: r.receivedDate },
          { label: '验收人', value: r.receivedByName },
          { label: '负责人', value: r.ownerName },
          { label: '验收说明', value: r.receiveNote, hidden: !r.receiveNote },
          {
            label: '冲减订单成本',
            value: formatAmount(r.costCny ?? 0, 'CNY'),
            hidden: !Number(r.costCny),
          },
          {
            label: '退回运费',
            value: formatAmount(r.freightCny ?? 0, 'CNY'),
            hidden: !Number(r.freightCny),
          },
          { label: '作废原因', value: r.voidReason, hidden: !voided },
        ]}
      />
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <SectionTitle>明细</SectionTitle>
        <Table<ReturnReceiptItem>
          rowKey="id"
          size="middle"
          pagination={false}
          columns={columns}
          dataSource={r.items}
          scroll={{ x: 'max-content' }}
        />
      </Card>
      <DocFilesCard
        ownerType="RETURN_RECEIPT"
        files={r.attachments ?? []}
        remark={r.remark}
        editable={!voided && (canReceive || canDispose)}
        onAttach={(ids) => returnApi.attach(r.id, ids)}
        onRemove={(fid) => returnApi.removeAttachment(r.id, fid)}
        onRemark={(text) => returnApi.remark(r.id, text)}
        onChanged={load}
      />
    </>
  );

  const side = (
    <>
      <RelatedDocsCard
        docs={[
          {
            label: '销售退货单',
            no: r.returnNo,
            href: PATHS.salesReturn(r.salesReturnId),
            statusName: th?.statusName,
            tone: th ? TH_STATUS_TONE[th.status] : undefined,
          },
          ...(th?.refunds ?? []).map((q) => ({
            label: '退款申请',
            no: q.requestNo,
            href: PATHS.refundRequest(q.id),
            statusName: q.statusName,
            tone: RQ_STATUS_TONE[q.status],
          })),
          ...(r.purchaseReturns ?? []).map((p) => ({
            label: '采购退货单',
            no: p.no,
            href: PATHS.purchaseReturn(p.id),
            statusName: p.statusName,
            tone: PR_STATUS_TONE[p.status],
          })),
        ]}
      />
      <DocLogCard logs={r.logs} />
    </>
  );

  return (
    <div>
      <WarehousePageTitle
        crumbs={[
          <MenuLink key="list" to={PATHS.returnReceiptList} />,
          r.receiptNo,
        ]}
        title={
          <>
            {r.receiptNo}
            <Pill tone={RT_STATUS_TONE[r.status] ?? 'gray'}>
              {r.statusName}
            </Pill>
          </>
        }
        description={
          <>
            {r.customerName} ·{' '}
            <SourceNos
              mode="inline"
              refs={[
                { no: r.returnNo, href: PATHS.salesReturn(r.salesReturnId) },
              ]}
            />{' '}
            · {r.ownerName ?? '—'} · {formatDateTime(r.createTime).slice(0, 16)}{' '}
            创建
          </>
        }
        actions={
          <>
            {r.status === RT_STATUS.PENDING && canReceive && (
              <Button type="primary" onClick={() => setDialog('receive')}>
                验收
              </Button>
            )}
            {r.status !== RT_STATUS.PENDING &&
              !voided &&
              canReceive &&
              r.items.every((i) => i.disposedQty === 0) && (
                <Button onClick={() => setDialog('unreceive')}>撤回入库</Button>
              )}
            {r.status === RT_STATUS.RECEIVED && canDispose && (
              <Button type="primary" onClick={() => setDialog('dispose')}>
                处理去向
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
        <div style={{ minWidth: 0 }}>{main}</div>
        <div style={{ minWidth: 0 }}>{side}</div>
      </div>
      <ReceiveModal
        data={dialog === 'receive' ? r : undefined}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <DisposeModal
        data={dialog === 'dispose' ? r : undefined}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <ReasonModal
        open={dialog === 'unreceive'}
        title={`撤回入库 ${r.receiptNo}`}
        description="验收数量录错时撤回，退货入库单回到待入库后重新验收；还没执行的退款申请回到待验收。"
        okText="撤回"
        onCancel={() => setDialog(undefined)}
        onOk={async (why) => {
          await returnApi.unreceive(r.id, why);
          message.success('已撤回入库');
          done();
        }}
      />
    </div>
  );
};

export default ReturnReceiptDetail;
