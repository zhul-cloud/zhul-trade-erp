import { PaperClipOutlined } from '@ant-design/icons';
import { Link, useAccess, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Skeleton, Table, Tooltip } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
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
  PageTitle,
  Pill,
  useWide,
} from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { PATHS } from '@/pages/sales/components';
import { orderApi, piApi, readBizError } from '@/pages/sales/service';
import {
  DOC_PATHS,
  ReasonModal,
  relatedRows,
} from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { UnitPill, useBusinessUnits } from '@/utils/businessUnit';
import { formatAmount, formatDateTime } from '@/utils/format';
import { MenuLink } from '@/utils/menuName';
import { ledgerApi, type ReceiptDoc, receiptDocApi } from './service';
import { MethodCell } from './UnclaimedTab';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

const RATE_SOURCE: Record<number, string> = {
  1: '系统汇率',
  2: '实际入账反算',
  3: '估算',
  4: '按结汇',
  5: '余额抵扣',
};

type Settlement = ReceiptDoc['settlements'][number];

/** 收款单详情（change finance-cash-docs）：生效后只改备注与附件，金额错了作废重登 */
const ReceiptDocDetail: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const wide = useWide();
  const [bu] = useBusinessUnits();
  const [d, setD] = useState<ReceiptDoc>();
  const [error, setError] = useState<string>();
  const [dialog, setDialog] = useState<'void' | 'unclaim'>();
  const no = d?.receipt.receiptNo;
  usePageTitle(no ? `收款单 ${no}` : undefined, no);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setD(await receiptDocApi.detail(id));
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
  const cur = r.currencyCode;
  const valid = r.status === 1;
  const claimed = !!(r.piId || r.soId);
  const canOperate =
    !!access.financeReceipts && !!access['sales:pi:receipt-confirm'];
  const settledJh = d.settlements.map((s) => s.settleNo).join('、');
  const sourceRef = r.piId
    ? { no: r.piNo ?? '', href: PATHS.pi(r.piId) }
    : r.soId
      ? { no: r.soNo ?? '', href: PATHS.order(r.soId) }
      : undefined;

  const voidReceipt = async (why: string) => {
    if (r.piId) await piApi.voidReceipt(r.piId, r.id, why);
    else if (r.soId) await orderApi.voidReceipt(r.soId, r.id, why);
    else await ledgerApi.voidUnclaimed(r.id, why);
  };

  const amountRows: { label: string; value: React.ReactNode }[] = [
    { label: '到账金额（毛额）', value: formatAmount(r.amount, cur) },
    ...(r.platformFee > 0
      ? [
          {
            label: '平台手续费',
            value: `− ${formatAmount(r.platformFee, cur)}`,
          },
        ]
      : []),
    ...(r.feeDiff > 0
      ? [
          {
            label: '银行中转手续费差额',
            value: `− ${formatAmount(r.feeDiff, cur)}`,
          },
        ]
      : []),
    { label: '实收', value: <b>{formatAmount(r.netAmount, cur)}</b> },
    ...(cur === 'CNY'
      ? []
      : [
          {
            label: '汇率',
            value: `${Number(r.exchangeRate).toFixed(6)} · ${RATE_SOURCE[r.rateSource] ?? ''}`,
          },
          {
            label: '实收人民币',
            value: <b>{formatAmount(r.netAmountCny, 'CNY')}</b>,
          },
        ]),
  ];

  const settleColumns: TableColumnsType<Settlement> = [
    {
      title: '结汇单号',
      dataIndex: 'settleNo',
      width: 160,
      render: (v: string, s) => (
        <Link to={DOC_PATHS.fxSettlement(s.settlementId)}>{v}</Link>
      ),
    },
    { title: '结汇日期', dataIndex: 'settleDate', width: 110 },
    {
      title: '分摊金额',
      dataIndex: 'amount',
      width: 130,
      align: 'right',
      render: (v: number) => <span style={num}>{formatAmount(v, cur)}</span>,
    },
    {
      title: '结汇汇率',
      dataIndex: 'exchangeRate',
      width: 110,
      align: 'right',
      render: (v: number) => <span style={num}>{Number(v).toFixed(6)}</span>,
    },
    {
      title: '人民币',
      dataIndex: 'amountCny',
      width: 130,
      align: 'right',
      render: (v: number) => <span style={num}>{formatAmount(v, 'CNY')}</span>,
    },
  ];

  const main = (
    <>
      <InfoGrid
        items={[
          { label: '到账日期', value: r.receiptDate },
          { label: '收款账户', value: r.bankAccountName },
          { label: '付款方式', value: <MethodCell r={r} /> },
          { label: '付款人', value: r.payer },
          {
            label: '平台订单号',
            value: r.platformOrderNo,
            hidden: !r.platformOrderNo,
          },
          {
            label: '所属事业部',
            value: (
              <span style={{ display: 'inline-flex', gap: 6 }}>
                <UnitPill info={bu} id={r.businessUnitId} />
                {r.buMismatch && <Pill tone="orange">账户与订单不一致</Pill>}
              </span>
            ),
            hidden: !bu.enabled,
          },
          {
            label: '认领',
            value: r.claimedByName
              ? `${r.claimedByName} ${formatDateTime(r.claimedAt).slice(0, 16)}`
              : undefined,
            hidden: !r.claimedByName,
          },
          { label: '作废原因', value: r.voidReason, hidden: valid },
        ]}
      />
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <SectionTitle>金额构成</SectionTitle>
        <div style={{ display: 'grid', gap: 10 }}>
          {amountRows.map((x) => (
            <div
              key={x.label}
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                gap: 16,
                ...num,
              }}
            >
              <span style={{ color: palette.sub }}>{x.label}</span>
              <span>{x.value}</span>
            </div>
          ))}
        </div>
        {cur !== 'CNY' && (
          <div style={{ marginTop: 20 }}>
            <SectionTitle
              extra={
                d.unsettledAmount != null && (
                  <span style={{ color: palette.sub, fontSize: 13 }}>
                    未结汇 {formatAmount(d.unsettledAmount, cur)}
                    {d.unsettledAmountCny != null &&
                      ` ≈ ${formatAmount(d.unsettledAmountCny, 'CNY')}`}
                  </span>
                )
              }
            >
              结汇
            </SectionTitle>
            <Table<Settlement>
              rowKey="settlementId"
              size="middle"
              pagination={false}
              columns={settleColumns}
              dataSource={d.settlements}
              scroll={{ x: 'max-content' }}
              locale={{ emptyText: '还没有结汇' }}
            />
          </div>
        )}
      </Card>
      {d.files.length > 0 && (
        <Card style={{ padding: 20, marginBottom: 16 }}>
          <SectionTitle>到账凭证与水单</SectionTitle>
          <div style={{ display: 'grid', gap: 8 }}>
            {d.files.map((f) => (
              <button
                key={f.index}
                type="button"
                className="zhul-link"
                style={{ textAlign: 'left' }}
                onClick={() =>
                  receiptDocApi
                    .openFile(r.id, f.index)
                    .catch((e) => message.error(readBizError(e).message))
                }
              >
                <PaperClipOutlined /> {f.fileName}
                <span style={{ color: palette.mute, marginLeft: 8 }}>
                  {f.source === 'SLIP' ? '水单' : '到账凭证'}
                </span>
              </button>
            ))}
          </div>
        </Card>
      )}
      <DocFilesCard
        ownerType="PAYMENT_RECEIPT"
        files={d.attachments ?? []}
        remark={r.note}
        editable={d.editable}
        onAttach={(ids) => receiptDocApi.attach(r.id, ids)}
        onRemove={(fid) => receiptDocApi.removeAttachment(r.id, fid)}
        onRemark={(text) => receiptDocApi.remark(r.id, text)}
        onChanged={load}
      />
    </>
  );

  return (
    <div>
      <PageTitle
        crumbs={[
          <MenuLink key="list" to="/finance/receipts" />,
          r.receiptNo ?? '',
        ]}
        title={
          <>
            {r.receiptNo}
            {valid ? (
              <Pill tone={claimed ? 'green' : 'orange'}>
                {claimed ? '有效' : '未认领'}
              </Pill>
            ) : (
              <Pill tone="mute">已作废</Pill>
            )}
          </>
        }
        description={
          <>
            {claimed ? (r.customerName ?? '—') : (r.payer ?? '—')} ·{' '}
            {sourceRef ? <SourceNos mode="inline" refs={[sourceRef]} /> : '—'} ·{' '}
            {r.operatorName ?? '—'} ·{' '}
            {formatDateTime(r.createTime).slice(0, 16)} 创建
          </>
        }
        actions={
          valid &&
          canOperate && (
            <>
              {claimed && r.claimedByName && !d.settlements.length && (
                <Button onClick={() => setDialog('unclaim')}>取消认领</Button>
              )}
              <Tooltip
                title={
                  d.settlements.length
                    ? `已结汇，请先作废结汇单 ${settledJh}`
                    : undefined
                }
              >
                <Button
                  danger
                  disabled={!!d.settlements.length}
                  onClick={() => setDialog('void')}
                >
                  作废
                </Button>
              </Tooltip>
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
          <RelatedDocsCard docs={relatedRows(d.related)} />
          <DocLogCard logs={d.logs} />
        </div>
      </div>
      <ReasonModal
        open={!!dialog}
        title={
          dialog === 'unclaim'
            ? `取消认领 ${r.receiptNo}`
            : `作废 ${r.receiptNo}`
        }
        description={
          dialog === 'unclaim'
            ? '收款单回到未认领，原 PI 的已到账与收款状态随之重算。'
            : '作废后不计入收款，记录保留；金额录错请作废后重新登记。'
        }
        placeholder="如：金额录错"
        okText={dialog === 'unclaim' ? '取消认领' : '作废'}
        danger={dialog === 'void'}
        onCancel={() => setDialog(undefined)}
        onOk={async (why) => {
          try {
            if (dialog === 'unclaim') await ledgerApi.unclaim(r.id, why);
            else await voidReceipt(why);
            message.success(dialog === 'unclaim' ? '已取消认领' : '已作废');
            setDialog(undefined);
            load();
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
    </div>
  );
};

export default ReceiptDocDetail;
