import { Link, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Dropdown, Skeleton, Table } from 'antd';
import { CircleX, Ellipsis, Pencil } from 'lucide-react';
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
import { PageHeader } from '@/components/ListLayout';
import { useOnReactivate, usePageTitle } from '@/components/PageTabs/hooks';
import { Card, Pill, useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import {
  HANDLING,
  RQ_STATUS_TONE,
  RT_STATUS_TONE,
  type SalesReturn,
  type SalesReturnItem,
  salesReturnApi,
  TH_STATUS,
  TH_STATUS_TONE,
} from '@/pages/sales/after-sales/service';
import { ReasonModal } from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime, formatMoney } from '@/utils/format';
import { tableScrollX } from '@/utils/tableScroll';
import { PATHS } from '../components';
import { readBizError } from '../service';
import { ConfirmReturnModal, EditReturnModal, lineRefund } from './dialogs';

/** 销售退货单详情（spec sales/after-sales「销售退货单」）：确认、修改、作废 */
const SalesReturnDetail: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const wide = useWide();
  const [r, setR] = useState<SalesReturn>();
  const [error, setError] = useState<string>();
  const [dialog, setDialog] = useState<'confirm' | 'edit' | 'void'>();
  usePageTitle(r ? `销售退货单 ${r.returnNo}` : undefined, r?.returnNo);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setR(await salesReturnApi.detail(id));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id]);
  useEffect(() => {
    load();
  }, [load]);
  // 从其他页面返回时重新取数（audit UI-055）；弹窗开着时不刷新，以免改过的退款金额被恢复成默认值（audit UI-065）
  useOnReactivate(() => {
    if (!dialog) load();
  });

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!r) return <Skeleton active paragraph={{ rows: 12 }} />;

  const refund = r.handling === HANDLING.REFUND;
  const cur = r.currencyCode ?? 'USD';
  const voided = r.status === TH_STATUS.VOID;
  // 待入库且仓库还没验收：还能补填快递、作废
  const receiptPending = r.status === TH_STATUS.PENDING && !r.receivedDate;
  const refundTotal = refund
    ? r.refunds?.length
      ? r.refunds
          .filter((q) => q.status !== 4 && q.status !== 5)
          .reduce((s, q) => s + q.amount, 0)
      : r.items.reduce((s, i) => s + lineRefund(i.quantity, i.unitPrice), 0)
    : 0;
  const done = () => {
    setDialog(undefined);
    load();
  };

  const columns: TableColumnsType<SalesReturnItem> = [
    { title: '#', key: 'no', width: 48, render: (_, __, i) => i + 1 },
    ...docBrandColumns<SalesReturnItem>((x) => x),
    {
      title: '型号',
      dataIndex: 'model',
      width: 200,
      render: (_, x) => <DocModelCell model={x.model} brand={x.brand} />,
    },
    { title: '退货数量', dataIndex: 'quantity', width: 90 },
    {
      title: `成交单价（${cur}）`,
      align: 'right',
      dataIndex: 'unitPrice',
      width: 120,
      render: (v?: number) => (v == null ? '—' : formatMoney(v)),
    },
    ...(refund && r.status === TH_STATUS.CONFIRMING
      ? [
          {
            title: `预计退款（${cur}）`,
            align: 'right' as const,
            key: 'refund',
            width: 120,
            render: (_: unknown, x: SalesReturnItem) =>
              formatMoney(lineRefund(x.quantity, x.unitPrice)),
          },
        ]
      : []),
    {
      title: '实收',
      dataIndex: 'receivedQty',
      width: 70,
      render: (v?: number | null) => v ?? '—',
    },
    {
      title: '合格',
      dataIndex: 'qualifiedQty',
      width: 70,
      render: (v?: number | null) => v ?? '—',
    },
  ];

  const main = (
    <>
      <InfoGrid
        items={[
          {
            label: '销售订单',
            value: <Link to={PATHS.order(r.soId)}>{r.soNo}</Link>,
          },
          { label: '处理方式', value: r.handlingName },
          {
            label: '先退款',
            value: r.refundFirst ? '是' : '否',
            hidden: !refund,
          },
          {
            label: '退款金额',
            value: formatAmount(refundTotal, cur),
            hidden: !refund,
          },
          {
            label: '客户寄回快递',
            value:
              r.carrier || r.trackingNo ? (
                <>
                  {r.carrier || '—'} ·{' '}
                  {r.trackingNo || (
                    <span style={{ color: palette.orange }}>待补填</span>
                  )}
                </>
              ) : undefined,
          },
          { label: '退货日期', value: r.createTime?.slice(0, 10) },
          { label: '负责人', value: r.ownerName },
          {
            label: '确认',
            value: r.confirmedAt
              ? `${r.confirmedByName ?? ''} ${formatDateTime(r.confirmedAt).slice(0, 16)}`
              : undefined,
            hidden: !r.confirmedAt,
          },
          { label: '作废原因', value: r.voidReason, hidden: !voided },
        ]}
      />
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <SectionTitle>明细</SectionTitle>
        <Table<SalesReturnItem>
          rowKey="id"
          size="middle"
          pagination={false}
          columns={columns}
          dataSource={r.items}
          scroll={{ x: tableScrollX(columns) }}
        />
      </Card>
      <DocFilesCard
        ownerType="SALES_RETURN"
        files={r.attachments ?? []}
        remark={r.remark}
        editable={!voided}
        onAttach={(ids) => salesReturnApi.attach(r.id, ids)}
        onRemove={(fid) => salesReturnApi.removeAttachment(r.id, fid)}
        onRemark={(text) => salesReturnApi.remark(r.id, text)}
        onChanged={load}
      />
    </>
  );

  const related = [
    ...(r.receipt
      ? [
          {
            label: '退货入库单',
            no: r.receipt.no,
            href: PATHS.returnReceipt(r.receipt.id),
            statusName: r.receipt.statusName,
            tone: RT_STATUS_TONE[r.receipt.status],
          },
        ]
      : []),
    ...(r.refunds ?? []).map((q) => ({
      label: '退款申请',
      no: q.requestNo,
      href: PATHS.refundRequest(q.id),
      statusName: q.statusName,
      tone: RQ_STATUS_TONE[q.status],
      extra: (
        <span style={{ color: palette.sub }}>
          {formatAmount(q.amount, q.currencyCode)}
        </span>
      ),
    })),
  ];

  const side = (
    <>
      {related.length === 0 && r.status === TH_STATUS.CONFIRMING ? (
        <Card style={{ padding: 20, marginBottom: 16 }}>
          <SectionTitle>关联单据</SectionTitle>
          <div style={{ color: palette.sub, fontSize: 13 }}>
            确认后生成退货入库单{refund ? '与退款申请' : ''}
          </div>
        </Card>
      ) : (
        <RelatedDocsCard docs={related} />
      )}
      <DocLogCard logs={r.logs} />
    </>
  );

  return (
    <div>
      <PageHeader
        title={r.returnNo}
        tags={
          <>
            {/* 页头只放一个流程状态；属性写在说明行（ui-redesign S14） */}
            <Pill tone={TH_STATUS_TONE[r.status] ?? 'gray'}>
              {r.statusName}
            </Pill>
          </>
        }
        sub={
          <>
            {r.handlingName} · {r.customerName} ·{' '}
            <SourceNos
              mode="inline"
              refs={[{ no: r.asNo, href: PATHS.afterSales(r.afterSalesId) }]}
            />{' '}
            · {r.ownerName ?? '—'} · {formatDateTime(r.createTime).slice(0, 16)}{' '}
            创建
          </>
        }
        // 页头操作（照销售订单详情）：一个主按钮，修改、补填快递为文字按钮，作废收进「更多」
        extra={
          <>
            {(r.status === TH_STATUS.CONFIRMING || receiptPending) && (
              <Button
                type="text"
                icon={<Pencil size={16} />}
                style={{ color: palette.link }}
                onClick={() => setDialog('edit')}
              >
                {receiptPending ? '补填快递' : '修改'}
              </Button>
            )}
            {(r.status === TH_STATUS.CONFIRMING || receiptPending) && (
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
            {r.status === TH_STATUS.CONFIRMING && (
              <Button type="primary" onClick={() => setDialog('confirm')}>
                确认退货
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
      <ConfirmReturnModal
        data={dialog === 'confirm' ? r : undefined}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <EditReturnModal
        data={dialog === 'edit' ? r : undefined}
        onClose={() => setDialog(undefined)}
        onDone={done}
      />
      <ReasonModal
        open={dialog === 'void'}
        title={`作废 ${r.returnNo}`}
        description={
          r.status === TH_STATUS.PENDING
            ? '退货入库单随之作废，还没执行的退款申请自动撤回。'
            : '作废后可在售后单重新发起。'
        }
        okText="作废"
        onCancel={() => setDialog(undefined)}
        onOk={async (why) => {
          await salesReturnApi.voidReturn(r.id, why);
          message.success('已作废');
          done();
        }}
      />
    </div>
  );
};

export default SalesReturnDetail;
