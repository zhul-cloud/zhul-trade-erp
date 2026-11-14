import { useParams } from '@umijs/max';
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
import { readBizError } from '@/pages/sales/service';
import { ReasonModal, relatedRows } from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { MenuLink } from '@/utils/menuName';
import { METHOD, type Payment, paymentApi } from '../payables/service';

type Allocation = Payment['allocations'][number];

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const KIND_NAMES: Record<number, string> = {
  1: '付款',
  2: '供应商退款',
  3: '报销付款',
};
const FROM_NAMES: Record<number, string> = {
  0: '实付',
  1: '欠款抵扣',
  2: '退款冲减',
};
const REIMBURSE_NAMES: Record<number, string> = {
  1: '待报销',
  2: '已进报销单',
};
const methodName = (p: Payment) =>
  p.method === METHOD.ADVANCE
    ? '员工垫付'
    : p.method === METHOD.PRIVATE
      ? '私对私'
      : '公司对公';

/** 付款单详情（change finance-cash-docs）：明细为核销的应付单，生效后只改备注与附件 */
const PaymentDetail: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const wide = useWide();
  const [p, setP] = useState<Payment>();
  const [error, setError] = useState<string>();
  const [voiding, setVoiding] = useState(false);
  usePageTitle(p ? `付款单 ${p.paymentNo}` : undefined, p?.paymentNo);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setP(await paymentApi.detail(id));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id]);
  useEffect(() => {
    load();
  }, [load]);

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!p) return <Skeleton active paragraph={{ rows: 12 }} />;

  const cur = p.currencyCode;
  const valid = !p.voided;
  const sources = [
    ...new Set(p.allocations.map((a) => a.sourceNo).filter(Boolean)),
  ];
  const sum = (k: 'amount' | 'amountCny') =>
    p.allocations.reduce((n, a) => n + Number(a[k] ?? 0), 0);

  const columns: TableColumnsType<Allocation> = [
    { title: '#', key: 'no', width: 48, render: (_, __, i) => i + 1 },
    { title: '应付单号', dataIndex: 'payableNo', width: 150 },
    {
      title: '来源单号',
      dataIndex: 'sourceNo',
      width: 160,
      render: (v?: string) => v || '—',
    },
    {
      title: '来源',
      dataIndex: 'fromCredit',
      width: 160,
      render: (v: number, a) => (
        <span style={{ color: v === 0 ? undefined : palette.orange }}>
          {FROM_NAMES[v] ?? '—'}
          {a.creditPayableNo && (
            <span style={{ color: palette.mute }}>
              {' '}
              · 来自 {a.creditPayableNo}
            </span>
          )}
        </span>
      ),
    },
    {
      title: '核销金额',
      dataIndex: 'amount',
      width: 130,
      align: 'right',
      render: (v: number) => <span style={num}>{formatAmount(v, cur)}</span>,
    },
    {
      title: '核销人民币',
      dataIndex: 'amountCny',
      width: 130,
      align: 'right',
      render: (v?: number) => (
        <span style={num}>{v == null ? '—' : formatAmount(v, 'CNY')}</span>
      ),
    },
  ];

  const main = (
    <>
      <InfoGrid
        items={[
          { label: '类型', value: KIND_NAMES[p.kind] },
          { label: '付款日期', value: p.payDate },
          {
            label: '付款方式',
            value: methodName(p),
            hidden: p.kind === 2,
          },
          {
            label: p.method === METHOD.PRIVATE ? '付款个人账户' : '付款账户',
            value: p.method === METHOD.PRIVATE ? p.payerAccount : p.accountName,
            hidden: p.method === METHOD.ADVANCE,
          },
          {
            label: '垫付人',
            value: [p.advanceUser, REIMBURSE_NAMES[p.reimburseStatus]]
              .filter(Boolean)
              .join(' · '),
            hidden: p.method !== METHOD.ADVANCE,
          },
          {
            label: '对方收款账户',
            value: p.payeeAccount,
            hidden: !p.payeeAccount,
          },
          { label: '金额', value: formatAmount(p.amount, cur) },
          {
            label: '实付人民币',
            value: `${formatAmount(p.amountCny, 'CNY')}${cur === 'CNY' ? '' : ` · 汇率 ${p.actualRate}`}`,
          },
          {
            label: '欠款抵扣',
            value: formatAmount(p.creditUsed, cur),
            hidden: !(p.creditUsed > 0),
          },
          {
            label: '作废',
            value: `${p.voidReason ?? ''}${p.voidBy ? ` · ${p.voidBy}` : ''}`,
            hidden: valid,
          },
        ]}
      />
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <SectionTitle
          extra={
            p.allocations.length > 1 && (
              <b style={num}>
                合计 {formatAmount(sum('amount'), cur)} ·{' '}
                {formatAmount(sum('amountCny'), 'CNY')}
              </b>
            )
          }
        >
          明细
        </SectionTitle>
        <Table<Allocation>
          rowKey={(a) =>
            `${a.payableId}-${a.fromCredit}-${a.creditPayableNo ?? ''}`
          }
          size="middle"
          pagination={false}
          columns={columns}
          dataSource={p.allocations}
          scroll={{ x: 'max-content' }}
          locale={{ emptyText: '没有核销应付单' }}
        />
      </Card>
      <DocFilesCard
        ownerType="PAYMENT"
        files={p.files ?? []}
        remark={p.remark}
        editable={!!p.editable}
        onAttach={(ids) => paymentApi.attach(p.id, ids)}
        onRemove={(fid) => paymentApi.removeAttachment(p.id, fid)}
        onRemark={(text) => paymentApi.remark(p.id, text)}
        onChanged={load}
      />
    </>
  );

  return (
    <div>
      <PageTitle
        crumbs={[<MenuLink key="list" to="/finance/payments" />, p.paymentNo]}
        title={
          <>
            {p.paymentNo}
            <Pill tone={valid ? 'green' : 'mute'}>
              {valid ? '有效' : '已作废'}
            </Pill>
          </>
        }
        description={
          <>
            {p.payeeName} ·{' '}
            {sources.length ? (
              <SourceNos mode="inline" refs={sources.map((no) => ({ no }))} />
            ) : (
              '—'
            )}{' '}
            · {p.advanceUser || p.createBy || '—'} ·{' '}
            {formatDateTime(p.createTime).slice(0, 16)} 创建
          </>
        }
        actions={
          valid &&
          (p.canVoid || p.voidBlockedReason) && (
            <Tooltip title={p.canVoid ? undefined : p.voidBlockedReason}>
              <Button
                danger
                disabled={!p.canVoid}
                onClick={() => setVoiding(true)}
              >
                作废
              </Button>
            </Tooltip>
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
          <RelatedDocsCard docs={relatedRows(p.related)} />
          <DocLogCard logs={p.logs} />
        </div>
      </div>
      <ReasonModal
        open={voiding}
        title={`作废 ${p.paymentNo}`}
        description="作废后核销的应付单恢复未付，记录保留；金额录错请作废后重新登记。"
        placeholder="如：金额填错"
        okText="作废"
        danger
        onCancel={() => setVoiding(false)}
        onOk={async (why) => {
          try {
            await paymentApi.voidPayment(p.id, why);
            message.success('已作废');
            setVoiding(false);
            load();
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
    </div>
  );
};

export default PaymentDetail;
