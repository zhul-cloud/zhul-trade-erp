import { WarningOutlined } from '@ant-design/icons';
import { Link, useAccess, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Skeleton, Table, Tooltip } from 'antd';
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
import {
  Card,
  PageTitle,
  Pill,
  useWide,
} from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import {
  RQ_STATUS_TONE,
  RT_STATUS_TONE,
  type SalesReturn,
  salesReturnApi,
  TH_STATUS_TONE,
} from '@/pages/sales/after-sales/service';
import { PATHS } from '@/pages/sales/components';
import { readBizError } from '@/pages/sales/service';
import { ReasonModal } from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { MenuLink } from '@/utils/menuName';
import RefundModal from './RefundModal';
import { refundSourceRef } from './RequestsTab';
import {
  type RefundRequest,
  type RefundRequestItem,
  RQ_STATUS,
  refundRequestApi,
} from './service';

/** 退款申请详情（spec finance/refund-request）：明细、审批、执行、驳回、撤回 */
const RefundRequestDetail: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const wide = useWide();
  const [q, setQ] = useState<RefundRequest>();
  const [th, setTh] = useState<SalesReturn>();
  const [error, setError] = useState<string>();
  const [dialog, setDialog] = useState<
    'execute' | 'reject' | 'rejectApproval'
  >();
  usePageTitle(q ? `退款申请 ${q.requestNo}` : undefined, q?.requestNo);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      const x = await refundRequestApi.detail(id);
      setQ(x);
      if (x.salesReturnId)
        salesReturnApi
          .detail(x.salesReturnId)
          .then(setTh)
          // 没有销售退货的查看权限时不显示退货入库单
          .catch(() => setTh(undefined));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id]);
  useEffect(() => {
    load();
  }, [load]);

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!q) return <Skeleton active paragraph={{ rows: 12 }} />;

  const canEdit = !!access['finance:refund:edit'];
  const canApprove = !!access['finance:refund-request:approve'];
  const canApply = !!access.salesAfterSales || !!access.salesOrders;
  const open =
    q.status === RQ_STATUS.PENDING_RECEIPT ||
    q.status === RQ_STATUS.PENDING_APPROVAL ||
    q.status === RQ_STATUS.PENDING_FINANCE;
  const cur = q.currencyCode;
  const done = () => {
    setDialog(undefined);
    load();
  };

  const columns: TableColumnsType<RefundRequestItem> = [
    { title: '#', key: 'no', width: 48, render: (_, __, i) => i + 1 },
    ...docBrandColumns<RefundRequestItem>((x) => x),
    {
      title: '型号',
      dataIndex: 'itemName',
      width: 200,
      render: (v: string, x) =>
        x.lineType === 1 ? <DocModelCell model={v} brand={x.brand} /> : v,
    },
    {
      title: '项目',
      dataIndex: 'lineType',
      width: 90,
      render: (v: number) => (v === 1 ? '退货型号' : '其他'),
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      width: 70,
      render: (v?: number) => v ?? '—',
    },
    {
      title: '成交单价',
      dataIndex: 'unitPrice',
      width: 120,
      render: (v?: number) => (v == null ? '—' : formatAmount(v, cur)),
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 120,
      render: (v: number) => <b>{formatAmount(v, cur)}</b>,
    },
    {
      title: '说明',
      dataIndex: 'note',
      width: 160,
      render: (v?: string) => v || '—',
    },
  ];

  const related: RelatedDoc[] = [
    ...(th
      ? [
          {
            label: '销售退货单',
            no: th.returnNo,
            href: PATHS.salesReturn(th.id),
            statusName: th.statusName,
            tone: TH_STATUS_TONE[th.status],
          },
          ...(th.receipt
            ? [
                {
                  label: '退货入库单',
                  no: th.receipt.no,
                  href: PATHS.returnReceipt(th.receipt.id),
                  statusName: th.receipt.statusName,
                  tone: RT_STATUS_TONE[th.receipt.status],
                },
              ]
            : []),
        ]
      : []),
    ...(q.refundNo
      ? [
          {
            label: '退款单',
            no: q.refundNo,
            statusName: '已执行',
            tone: 'green' as const,
          },
        ]
      : []),
  ];

  const main = (
    <>
      {q.status === RQ_STATUS.PENDING_RECEIPT && (
        <div
          role="status"
          style={{
            padding: '12px 16px',
            borderRadius: 12,
            marginBottom: 16,
            background: palette.orangeSoft,
            color: palette.orange,
          }}
        >
          <WarningOutlined /> 仓库还没验收
          {th?.receipt ? ` ${th.receipt.no}` : '退货'}，验收后才能执行
        </div>
      )}
      {q.receivedHint && q.status !== RQ_STATUS.PENDING_RECEIPT && (
        <div
          role="status"
          style={{
            padding: '12px 16px',
            borderRadius: 12,
            marginBottom: 16,
            background: palette.orangeSoft,
            color: palette.orange,
          }}
        >
          <WarningOutlined /> {q.receivedHint}，执行前请核对退款金额
        </div>
      )}
      <InfoGrid
        items={[
          {
            label: '售后单',
            value:
              q.afterSalesId && q.asNo ? (
                <Link to={PATHS.afterSales(q.afterSalesId)}>{q.asNo}</Link>
              ) : undefined,
          },
          {
            label: '销售订单',
            value: <Link to={PATHS.order(q.soId)}>{q.soNo}</Link>,
          },
          { label: '金额合计', value: formatAmount(q.amount, cur) },
          { label: '原因', value: q.reasonName },
          { label: '建议去向', value: q.methodName },
          {
            label: '先退款',
            value: q.refundFirst ? '是' : '否',
            hidden: !q.salesReturnId,
          },
          { label: '客户收款信息', value: q.payeeInfo },
          { label: '申请人', value: q.applicantName },
          {
            label: '审批',
            value: q.approverName
              ? `${q.approverName} ${formatDateTime(q.approvedAt).slice(0, 16)}`
              : undefined,
            hidden: !q.approverName,
          },
          {
            label: '财务处理',
            value: q.handlerName
              ? `${q.handlerName} ${formatDateTime(q.handledAt).slice(0, 16)}`
              : undefined,
            hidden: !q.handlerName,
          },
          {
            label: '驳回原因',
            value: q.rejectReason,
            hidden: q.status !== RQ_STATUS.REJECTED,
          },
        ]}
      />
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <SectionTitle extra={<b>合计 {formatAmount(q.amount, cur)}</b>}>
          明细
        </SectionTitle>
        <Table<RefundRequestItem>
          rowKey="id"
          size="middle"
          pagination={false}
          columns={columns}
          dataSource={q.items ?? []}
          scroll={{ x: 'max-content' }}
        />
      </Card>
      <DocFilesCard
        ownerType="REFUND_REQUEST"
        files={q.attachments ?? []}
        remark={q.remark}
        editable={open && (canApply || canEdit)}
        onAttach={(ids) => refundRequestApi.attach(q.id, ids)}
        onRemove={(fid) => refundRequestApi.removeAttachment(q.id, fid)}
        onRemark={(text) => refundRequestApi.remark(q.id, text)}
        onChanged={load}
      />
    </>
  );

  return (
    <div>
      <PageTitle
        crumbs={[<MenuLink key="list" to="/finance/refunds" />, q.requestNo]}
        title={
          <>
            {q.requestNo}
            <Pill tone={RQ_STATUS_TONE[q.status] ?? 'gray'}>
              {q.statusName}
            </Pill>
          </>
        }
        description={
          <>
            {q.customerName} ·{' '}
            <SourceNos mode="inline" refs={[refundSourceRef(q)]} /> ·{' '}
            {q.applicantName ?? '—'} ·{' '}
            {formatDateTime(q.createTime).slice(0, 16)} 创建
          </>
        }
        actions={
          <>
            {open && canApply && (
              <Button
                onClick={() =>
                  modal.confirm({
                    title: `撤回 ${q.requestNo}？`,
                    content: '撤回后申请结束，需要时可以重新提出。',
                    okText: '撤回',
                    onOk: async () => {
                      try {
                        await refundRequestApi.withdraw(q.id);
                        message.success('已撤回');
                        load();
                      } catch (e) {
                        message.error(readBizError(e).message);
                      }
                    },
                  })
                }
              >
                撤回
              </Button>
            )}
            {q.status === RQ_STATUS.PENDING_APPROVAL && canApprove && (
              <>
                <Button onClick={() => setDialog('rejectApproval')}>
                  驳回
                </Button>
                <Button
                  type="primary"
                  onClick={() =>
                    modal.confirm({
                      title: `通过 ${q.requestNo}？`,
                      content: `${formatAmount(q.amount, cur)}，通过后交财务处理。`,
                      okText: '通过',
                      onOk: async () => {
                        try {
                          await refundRequestApi.approve(q.id);
                          message.success('已通过');
                          load();
                        } catch (e) {
                          message.error(readBizError(e).message);
                        }
                      },
                    })
                  }
                >
                  通过
                </Button>
              </>
            )}
            {(q.status === RQ_STATUS.PENDING_FINANCE ||
              q.status === RQ_STATUS.PENDING_RECEIPT) &&
              canEdit && (
                <>
                  {q.status === RQ_STATUS.PENDING_FINANCE && (
                    <Button onClick={() => setDialog('reject')}>驳回</Button>
                  )}
                  <Tooltip
                    title={
                      q.status === RQ_STATUS.PENDING_RECEIPT
                        ? '仓库验收后才能执行'
                        : undefined
                    }
                  >
                    <Button
                      type="primary"
                      disabled={q.status === RQ_STATUS.PENDING_RECEIPT}
                      onClick={() => setDialog('execute')}
                    >
                      执行
                    </Button>
                  </Tooltip>
                </>
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
        <div style={{ minWidth: 0 }}>
          <RelatedDocsCard docs={related} />
          <DocLogCard logs={q.logs} />
        </div>
      </div>
      <RefundModal
        open={dialog === 'execute'}
        soId={q.soId}
        request={q}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <ReasonModal
        open={dialog === 'reject' || dialog === 'rejectApproval'}
        title={`驳回 ${q.requestNo}`}
        description={
          dialog === 'rejectApproval'
            ? '驳回后申请结束，业务员可以重新提出。'
            : '驳回后申请结束，不生成退款单。'
        }
        okText="驳回"
        danger
        onCancel={() => setDialog(undefined)}
        onOk={async (why) => {
          if (dialog === 'rejectApproval')
            await refundRequestApi.rejectApproval(q.id, why);
          else await refundRequestApi.reject(q.id, why);
          message.success('已驳回');
          done();
        }}
      />
    </div>
  );
};

export default RefundRequestDetail;
