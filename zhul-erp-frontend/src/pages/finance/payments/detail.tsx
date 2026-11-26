import { Link, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Skeleton, Table } from 'antd';
import { CircleX } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { CopyText } from '@/components/CopyText';
import {
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
import { UserName } from '@/components/UserName';
import { Card, Pill, useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { readBizError } from '@/pages/sales/service';
import {
  DOC_PATHS,
  ReasonModal,
  relatedRows,
} from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime, formatMoney } from '@/utils/format';
import { tableScrollX } from '@/utils/tableScroll';
import { PAY_CHANNEL_NAMES } from '../payables/payQr';
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
      ? `私对私${p.payChannel && p.payChannel !== 1 ? ` · ${PAY_CHANNEL_NAMES[p.payChannel]}` : ''}`
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
  // 从其他页面返回时重新取数（audit UI-055）
  useOnReactivate(load);

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
    {
      title: '应付单号',
      dataIndex: 'payableNo',
      width: 150,
      render: (v: string, a) => (
        <CopyText text={v}>
          <Link to={DOC_PATHS.payable(a.payableId)}>{v}</Link>
        </CopyText>
      ),
    },
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
      title: `核销金额（${cur}）`,
      dataIndex: 'amount',
      width: 130,
      align: 'right',
      render: (v: number) => <span style={num}>{formatMoney(v)}</span>,
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

  const info = (
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
    </>
  );
  // 明细等宽内容放两栏下方通栏，1366 宽下不被挤出（照销售订单，复审 P18）
  const wideSections = (
    <>
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
          scroll={{ x: tableScrollX(columns) }}
          locale={{ emptyText: tableEmpty('没有核销应付单', undefined, true) }}
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
      <PageHeader
        title={p.paymentNo}
        tags={
          <Pill tone={valid ? 'green' : 'mute'}>
            {valid ? '有效' : '已作废'}
          </Pill>
        }
        sub={
          <>
            {p.payeeName} ·{' '}
            {sources.length ? (
              <SourceNos mode="inline" refs={sources.map((no) => ({ no }))} />
            ) : (
              '—'
            )}{' '}
            · {p.advanceUser || <UserName value={p.createBy} />} ·{' '}
            {formatDateTime(p.createTime).slice(0, 16)} 创建
          </>
        }
        extra={
          valid &&
          (p.canVoid || p.voidBlockedReason) && (
            <MoreActions
              items={[
                {
                  key: 'void',
                  label: '作废',
                  icon: <CircleX size={16} />,
                  danger: true,
                  disabledReason: p.canVoid ? undefined : p.voidBlockedReason,
                },
              ]}
              onClick={() => setVoiding(true)}
            />
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
          <RelatedDocsCard docs={relatedRows(p.related)} />
          <DocLogCard logs={p.logs} />
        </div>
      </div>
      {wideSections}
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
