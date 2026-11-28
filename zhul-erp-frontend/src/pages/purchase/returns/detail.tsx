import { Link, useAccess, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Dropdown, Skeleton, Table } from 'antd';
import {
  CircleCheck,
  CircleX,
  Ellipsis,
  Pencil,
  RotateCcw,
  Send,
} from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import DocModelCell, {
  docModelColumn,
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
import { PageHeader } from '@/components/ListLayout';
import { useOnReactivate, usePageTitle } from '@/components/PageTabs/hooks';
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
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { tableScrollX } from '@/utils/tableScroll';
import { PATHS } from '../components';
import { CompleteModal, QuantityModal, ShipModal } from './dialogs';
import {
  creditsPayable,
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
  const { palette } = useAppTheme();
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
      if (creditsPayable(x.sourceType) && x.poId)
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
  // 从其他页面返回时重新取数（audit UI-055）
  useOnReactivate(load);

  if (error) return <ErrorHint page message={error} onRetry={load} />;
  if (!p) return <Skeleton active paragraph={{ rows: 12 }} />;

  const canEdit = !!access['purchase:purchase-return:edit'];
  const voided = p.status === PR_STATUS.VOID;
  const afterSales = p.sourceType === PR_SOURCE.AFTER_SALES;
  const fromStock = p.sourceType === PR_SOURCE.STOCK;
  const cur = p.currencyCode ?? 'CNY';
  const done = () => {
    setDialog(undefined);
    load();
  };

  const columns: TableColumnsType<PurchaseReturnItem> = [
    {
      title: '#',
      fixed: 'left' as const,
      key: 'no',
      width: 48,
      render: (_, __, i) => i + 1,
    },
    docModelColumn<PurchaseReturnItem>((x) => x, {
      title: '型号',
      dataIndex: 'model',
      width: 200,
      render: (_, x) => <DocModelCell model={x.model} brand={x.brand} />,
    }),
    {
      title: '数量',
      align: 'right' as const,
      dataIndex: 'quantity',
      width: 80,
    },
    ...(fromStock
      ? [
          {
            title: '批次',
            dataIndex: 'batchNo',
            width: 180,
            render: (v: string | undefined, x: PurchaseReturnItem) =>
              v
                ? `${v}${x.stockStatusName ? ` · ${x.stockStatusName}` : ''}`
                : '—',
          },
        ]
      : afterSales
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

  const info = (
    <>
      <InfoGrid
        items={[
          {
            label: '采购订单',
            value:
              p.poNo && p.poId ? (
                <Link
                  to={PATHS.order(p.poId)}
                  className="zhul-link zhul-link-doc"
                >
                  {p.poNo}
                </Link>
              ) : undefined,
          },
          {
            label: '销售订单',
            value:
              p.soNo && p.soId ? (
                <Link
                  to={SALES_PATHS.order(p.soId)}
                  className="zhul-link zhul-link-doc"
                >
                  {p.soNo}
                </Link>
              ) : undefined,
            hidden: !afterSales,
          },
          {
            label: '售后单',
            value:
              p.asNo && p.afterSalesId ? (
                <Link
                  className="zhul-link zhul-link-doc"
                  to={SALES_PATHS.afterSales(p.afterSalesId)}
                >
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
            hidden: !creditsPayable(p.sourceType),
          },
          {
            label: '完成说明',
            value: p.settleNote,
            hidden: !p.settleNote,
          },
          { label: '作废原因', value: p.voidReason, hidden: !voided },
        ]}
      />
    </>
  );
  // 明细等宽内容放两栏下方通栏，1366 宽下不被挤出（照销售订单，复审 P18）
  const wideSections = (
    <>
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
          scroll={{ x: tableScrollX(columns) }}
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
      label: afterSales ? '退货入库单' : fromStock ? '退回批次' : '采购入库单',
      no: src.no,
      href: src.href,
    },
    ...(payable
      ? [
          {
            label: '应付单',
            no: payable.payableNo,
            href: `/finance/payables/${payable.id}`,
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
      <PageHeader
        title={p.returnNo}
        tags={
          <>
            {/* 页头只放一个流程状态；属性写在说明行（ui-redesign S14） */}
            <Pill tone={PR_STATUS_TONE[p.status] ?? 'gray'}>
              {p.statusName}
            </Pill>
          </>
        }
        sub={
          <>
            {p.sourceTypeName} · {p.supplierName ?? '—'} ·{' '}
            <SourceNos mode="inline" refs={[src]} /> · {p.ownerName ?? '—'} ·{' '}
            {formatDateTime(p.createTime).slice(0, 16)} 创建
          </>
        }
        // 页头操作（照销售订单详情）：一个主按钮，改数量、重新打开为文字按钮，作废收进「更多」
        extra={
          canEdit && (
            <>
              {p.status === PR_STATUS.PENDING && afterSales && (
                <Button
                  type="text"
                  icon={<Pencil size={16} />}
                  style={{ color: palette.link }}
                  onClick={() => setDialog('qty')}
                >
                  改数量
                </Button>
              )}
              {p.status === PR_STATUS.DONE && !p.migrated && (
                <Button
                  type="text"
                  icon={<RotateCcw size={16} />}
                  style={{ color: palette.link }}
                  onClick={() => setDialog('reopen')}
                >
                  重新打开
                </Button>
              )}
              {p.status === PR_STATUS.PENDING && (
                <Dropdown
                  trigger={['click']}
                  placement="bottomRight"
                  menu={{
                    items: [
                      {
                        key: 'void',
                        icon: <CircleX size={16} />,
                        label: '作废',
                        danger: true,
                      },
                    ],
                    onClick: () => setDialog('void'),
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
              {p.status === PR_STATUS.PENDING && (
                <Button
                  type="primary"
                  icon={<Send size={16} />}
                  onClick={() => setDialog('ship')}
                >
                  寄出
                </Button>
              )}
              {p.status === PR_STATUS.SHIPPED && (
                <Button
                  type="primary"
                  icon={<CircleCheck size={16} />}
                  onClick={() => setDialog('complete')}
                >
                  完成
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
        <div style={{ minWidth: 0 }}>{info}</div>
        <div style={{ minWidth: 0 }}>
          <RelatedDocsCard docs={related} />
          <DocLogCard logs={p.logs} />
        </div>
      </div>
      {wideSections}
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
            : fromStock
              ? '退货预占释放，货回到可用库存。'
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
