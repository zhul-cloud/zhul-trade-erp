import { Link, useAccess, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Skeleton, Table } from 'antd';
import { CircleX, Paperclip, Undo2 } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { CopyText } from '@/components/CopyText';
import {
  DocColumns,
  DocLogCard,
  InfoGrid,
  RelatedDocsCard,
  SectionTitle,
} from '@/components/DocFields/DocPage';
import SourceNos from '@/components/DocFields/SourceNos';
import { DocFilesCard } from '@/components/DocFiles';
import { tableEmpty } from '@/components/EmptyState';
import { MoreActions, PageHeader } from '@/components/ListLayout';
import { useOnReactivate, usePageTitle } from '@/components/PageTabs/hooks';
import { Card, Pill, useWide } from '@/pages/inquiry/shared/components';
import { useFilePreview } from '@/pages/inquiry/shared/FilePreview';
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
import { formatAmount, formatDateTime, formatMoney } from '@/utils/format';
import { tableScrollX } from '@/utils/tableScroll';
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
  const preview = useFilePreview();
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
  // 从其他页面返回时重新取数（audit UI-055）
  useOnReactivate(load);

  if (error) return <ErrorHint page message={error} onRetry={load} />;
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
        <CopyText text={v}>
          <Link to={DOC_PATHS.fxSettlement(s.settlementId)}>{v}</Link>
        </CopyText>
      ),
    },
    { title: '结汇日期', dataIndex: 'settleDate', width: 110 },
    {
      title: `分摊金额（${cur}）`,
      dataIndex: 'amount',
      width: 130,
      align: 'right',
      render: (v: number) => <span style={num}>{formatMoney(v)}</span>,
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
      render: (v: number) => <span style={num}>{formatMoney(v)}</span>,
    },
  ];

  const info = (
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
    </>
  );
  // 明细等宽内容放两栏下方通栏，1366 宽下不被挤出（照销售订单，复审 P18）
  const wideSections = (
    <>
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
              scroll={{ x: tableScrollX(settleColumns) }}
              locale={{ emptyText: tableEmpty('还没有结汇', undefined, true) }}
            />
          </div>
        )}
      </Card>
      {d.files.length > 0 && (
        <Card style={{ padding: 20, marginBottom: 16 }}>
          <SectionTitle>到账凭证与水单</SectionTitle>
          <div style={{ display: 'grid', gap: 8 }}>
            {d.files.map((f, i) => (
              <button
                key={f.index}
                type="button"
                className="zhul-link"
                style={{ textAlign: 'left' }}
                onClick={() =>
                  preview.open(
                    d.files.map((x) => ({ id: x.index, fileName: x.fileName })),
                    i,
                    (x) => receiptDocApi.filePath(r.id, x.id),
                    (x) =>
                      d.files.find((y) => y.index === x.id)?.source === 'SLIP'
                        ? '水单'
                        : '到账凭证',
                  )
                }
              >
                <Paperclip size="1em" /> {f.fileName}
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
      <PageHeader
        title={r.receiptNo}
        tags={
          valid ? (
            <Pill tone={claimed ? 'green' : 'orange'}>
              {claimed ? '有效' : '未认领'}
            </Pill>
          ) : (
            <Pill tone="mute">已作废</Pill>
          )
        }
        sub={
          <>
            {claimed ? (r.customerName ?? '—') : (r.payer ?? '—')} ·{' '}
            {sourceRef ? <SourceNos mode="inline" refs={[sourceRef]} /> : '—'} ·{' '}
            {r.operatorName ?? '—'} ·{' '}
            {formatDateTime(r.createTime).slice(0, 16)} 创建
          </>
        }
        // 取消认领、作废都会回退到账，收进「更多」；已结汇的不能作废，原因写在菜单项里
        extra={
          valid &&
          canOperate && (
            <MoreActions
              items={[
                claimed &&
                  r.claimedByName &&
                  !d.settlements.length && {
                    key: 'unclaim',
                    label: '取消认领',
                    icon: <Undo2 size={16} />,
                  },
                {
                  key: 'void',
                  label: '作废',
                  icon: <CircleX size={16} />,
                  danger: true,
                  disabledReason: d.settlements.length
                    ? `已结汇，请先作废结汇单 ${settledJh}`
                    : undefined,
                },
              ]}
              onClick={(k) => setDialog(k as 'unclaim' | 'void')}
            />
          )
        }
      />
      <DocColumns
        wide={wide}
        side={
          <>
            <RelatedDocsCard docs={relatedRows(d.related)} />
            <DocLogCard logs={d.logs} />
          </>
        }
      >
        {info}
        {wideSections}
      </DocColumns>
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
      {preview.node}
    </div>
  );
};

export default ReceiptDocDetail;
