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
import SourceNos, { listHref } from '@/components/DocFields/SourceNos';
import { DocFilesCard } from '@/components/DocFiles';
import { usePageTitle } from '@/components/PageTabs/hooks';
import {
  PAY_STATUS,
  type Payable,
  payableApi,
  SOURCE,
} from '@/pages/finance/payables/service';
import { Card, Pill, useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { PR_STATUS_TONE } from '@/pages/sales/after-sales/service';
import { PATHS as SALES_PATHS } from '@/pages/sales/components';
import { readBizError } from '@/pages/sales/service';
import { AttachmentWall, ReasonModal } from '@/pages/warehouse/components';
import { formatAmount, formatDateTime } from '@/utils/format';
import { MenuLink } from '@/utils/menuName';
import { PATHS, PurchasePageTitle } from '../components';
import { CompleteModal, QuantityModal, ShipModal } from './dialogs';
import {
  PR_SOURCE,
  PR_STATUS,
  type PurchaseReturn,
  type PurchaseReturnItem,
  prSourceRef,
  purchaseReturnApi,
} from './service';

const PAY_STATUS_NAME: Record<number, string> = {
  [PAY_STATUS.UNPAID]: '未付',
  [PAY_STATUS.PARTIAL]: '部分付款',
  [PAY_STATUS.PAID]: '已付清',
  [PAY_STATUS.CLOSED]: '已关闭',
};

type Dialog = 'ship' | 'complete' | 'qty' | 'reopen' | 'void';

/** 采购退货单详情（spec purchase/purchase-return）：寄出、完成、重新打开、作废 */
const PurchaseReturnDetail: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { message } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const wide = useWide();
  const [p, setP] = useState<PurchaseReturn>();
  const [payable, setPayable] = useState<Payable | null>();
  const [error, setError] = useState<string>();
  const [dialog, setDialog] = useState<Dialog>();
  usePageTitle(p ? `采购退货单 ${p.returnNo}` : undefined, p?.returnNo);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      const x = await purchaseReturnApi.detail(id);
      setP(x);
      if (x.sourceType === PR_SOURCE.AFTER_SALES && x.poId)
        payableApi
          .bySource(SOURCE.PO, x.poId)
          .then(setPayable)
          // 没有应付权限时不显示应付单
          .catch(() => setPayable(null));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id]);
  useEffect(() => {
    load();
  }, [load]);

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!p) return <Skeleton active paragraph={{ rows: 12 }} />;

  const canEdit = !!access['purchase:purchase-return:edit'];
  const voided = p.status === PR_STATUS.VOID;
  const afterSales = p.sourceType === PR_SOURCE.AFTER_SALES;
  const cur = p.currencyCode ?? 'CNY';
  const done = () => {
    setDialog(undefined);
    load();
  };

  const columns: TableColumnsType<PurchaseReturnItem> = [
    { title: '#', key: 'no', width: 48, render: (_, __, i) => i + 1 },
    ...docBrandColumns<PurchaseReturnItem>((x) => x),
    {
      title: '型号',
      dataIndex: 'model',
      width: 200,
      render: (_, x) => <DocModelCell model={x.model} brand={x.brand} />,
    },
    { title: '数量', dataIndex: 'quantity', width: 80 },
    ...(afterSales
      ? []
      : [
          {
            title: '到货差异',
            dataIndex: 'discrepancyName',
            width: 160,
            render: (v?: string) => v || '—',
          },
        ]),
  ];

  const payer = p.payerName
    ? `${p.payerName}（进待报销）`
    : Number(p.freightCny) > 0
      ? '公司支付'
      : undefined;

  const main = (
    <>
      <InfoGrid
        items={[
          {
            label: '采购订单',
            value:
              p.poNo && p.poId ? (
                <Link to={PATHS.order(p.poId)}>{p.poNo}</Link>
              ) : undefined,
          },
          {
            label: '销售订单',
            value:
              p.soNo && p.soId ? (
                <Link to={SALES_PATHS.order(p.soId)}>{p.soNo}</Link>
              ) : undefined,
            hidden: !afterSales,
          },
          {
            label: '售后单',
            value:
              p.asNo && p.afterSalesId ? (
                <Link to={SALES_PATHS.afterSales(p.afterSalesId)}>
                  {p.asNo}
                </Link>
              ) : undefined,
            hidden: !afterSales,
          },
          { label: '来源类型', value: p.sourceTypeName },
          { label: '寄出日期', value: p.shipDate },
          {
            label: '快递',
            value: p.carrier ? `${p.carrier} ${p.trackingNo ?? ''}` : undefined,
          },
          {
            label: '运费',
            value:
              Number(p.freightCny) > 0
                ? formatAmount(p.freightCny ?? 0, 'CNY')
                : undefined,
          },
          { label: '垫付人', value: payer, hidden: !payer },
          { label: '签收日期', value: p.signedDate },
          {
            label: '退款或抵扣',
            value:
              p.status === PR_STATUS.DONE
                ? `${formatAmount(p.refundAmount ?? 0, cur)}${
                    cur === 'CNY'
                      ? ''
                      : `（${formatAmount(p.refundCny ?? 0, 'CNY')}）`
                  }`
                : undefined,
            hidden: !afterSales,
          },
          {
            label: '完成说明',
            value: p.settleNote,
            hidden: !p.settleNote,
          },
          { label: '作废原因', value: p.voidReason, hidden: !voided },
        ]}
      />
      {(p.freightProofs ?? []).length > 0 && (
        <Card style={{ padding: 20, marginBottom: 16 }}>
          <SectionTitle>运费截图</SectionTitle>
          <AttachmentWall
            ownerType="RETURN_PROOF"
            value={p.freightProofs ?? []}
          />
        </Card>
      )}
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <SectionTitle>明细</SectionTitle>
        <Table<PurchaseReturnItem>
          rowKey="id"
          size="middle"
          pagination={false}
          columns={columns}
          dataSource={p.items}
          scroll={{ x: 'max-content' }}
        />
      </Card>
      <DocFilesCard
        ownerType="PURCHASE_RETURN"
        files={p.attachments ?? []}
        remark={p.remark}
        editable={!voided && canEdit}
        onAttach={(ids) => purchaseReturnApi.attach(p.id, ids)}
        onRemove={(fid) => purchaseReturnApi.removeAttachment(p.id, fid)}
        onRemark={(text) => purchaseReturnApi.remark(p.id, text)}
        onChanged={load}
      />
    </>
  );

  const src = prSourceRef(p);
  const related: RelatedDoc[] = [
    {
      label: afterSales ? '退货入库单' : '采购入库单',
      no: src.no,
      href: src.href,
    },
    ...(payable
      ? [
          {
            label: '应付单',
            no: payable.payableNo,
            href: listHref('/finance/payables', payable.payableNo),
            statusName: PAY_STATUS_NAME[payable.status],
            tone:
              payable.status === PAY_STATUS.PAID
                ? ('green' as const)
                : ('orange' as const),
          },
        ]
      : []),
  ];

  return (
    <div>
      <PurchasePageTitle
        crumbs={[
          <MenuLink key="list" to={SALES_PATHS.purchaseReturnList} />,
          p.returnNo,
        ]}
        title={
          <>
            {p.returnNo}
            <Pill tone={PR_STATUS_TONE[p.status] ?? 'gray'}>
              {p.statusName}
            </Pill>
            <span style={{ fontSize: 13, fontWeight: 400 }}>
              {p.sourceTypeName}
            </span>
          </>
        }
        description={
          <>
            {p.supplierName ?? '—'} · <SourceNos mode="inline" refs={[src]} /> ·{' '}
            {p.ownerName ?? '—'} · {formatDateTime(p.createTime).slice(0, 16)}{' '}
            创建
          </>
        }
        actions={
          canEdit && (
            <>
              {p.status === PR_STATUS.PENDING && (
                <Button onClick={() => setDialog('void')}>作废</Button>
              )}
              {p.status === PR_STATUS.PENDING && afterSales && (
                <Button onClick={() => setDialog('qty')}>改数量</Button>
              )}
              {p.status === PR_STATUS.PENDING && (
                <Button type="primary" onClick={() => setDialog('ship')}>
                  寄出
                </Button>
              )}
              {p.status === PR_STATUS.SHIPPED && (
                <Button type="primary" onClick={() => setDialog('complete')}>
                  完成
                </Button>
              )}
              {p.status === PR_STATUS.DONE && !p.migrated && (
                <Button onClick={() => setDialog('reopen')}>重新打开</Button>
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
          <RelatedDocsCard docs={related} />
          <DocLogCard logs={p.logs} />
        </div>
      </div>
      <ShipModal
        data={dialog === 'ship' ? p : undefined}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <CompleteModal
        data={dialog === 'complete' ? p : undefined}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <QuantityModal
        data={dialog === 'qty' ? p : undefined}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <ReasonModal
        open={dialog === 'void'}
        title={`作废 ${p.returnNo}`}
        description={
          afterSales
            ? '数量回到退货入库单「待处理」，可重新选择去向。'
            : '对应的到货差异重新打开，可重新处理。'
        }
        okText="作废"
        onCancel={() => setDialog(undefined)}
        onOk={async (why) => {
          await purchaseReturnApi.voidReturn(p.id, why);
          message.success('已作废');
          done();
        }}
      />
      <ReasonModal
        open={dialog === 'reopen'}
        title={`重新打开 ${p.returnNo}`}
        description="金额录错时重新打开：回到已寄出，撤回成本冲减与应付冲减，运费不变。"
        okText="重新打开"
        onCancel={() => setDialog(undefined)}
        onOk={async (why) => {
          await purchaseReturnApi.reopen(p.id, why);
          message.success('已重新打开');
          done();
        }}
      />
    </div>
  );
};

export default PurchaseReturnDetail;
