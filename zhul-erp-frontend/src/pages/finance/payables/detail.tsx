import { Link, useAccess, useParams } from '@umijs/max';
import { Button, Skeleton } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import {
  DocLogCard,
  InfoGrid,
  RelatedDocsCard,
  SectionTitle,
} from '@/components/DocFields/DocPage';
import { DocFilesCard } from '@/components/DocFiles';
import { usePageTitle } from '@/components/PageTabs/hooks';
import { Card, PageTitle, useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { readBizError } from '@/pages/sales/service';
import { relatedRows } from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { MenuLink } from '@/utils/menuName';
import {
  DueTag,
  InstallmentTable,
  PayeeTypePill,
  PaymentLineTable,
  PayStatusPill,
  RefundModal,
  RegisterPaymentDrawer,
} from './components';
import { PAY_STATUS, type Payable, payableApi, sourcePath } from './service';

/** 应付单详情（change finance-ap-fx-docs）：金额由来源单据与付款决定，只改备注与附件 */
const PayableDetail: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const wide = useWide();
  const [p, setP] = useState<Payable>();
  const [error, setError] = useState<string>();
  const [paying, setPaying] = useState(false);
  const [refunding, setRefunding] = useState(false);
  usePageTitle(p ? `应付单 ${p.payableNo}` : undefined, p?.payableNo);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setP(await payableApi.detail(id));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [id]);
  useEffect(() => {
    load();
  }, [load]);

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!p) return <Skeleton active paragraph={{ rows: 12 }} />;

  const c = p.currencyCode;
  const canRegister = !!access['finance:payment:register'];
  const open =
    p.status === PAY_STATUS.UNPAID || p.status === PAY_STATUS.PARTIAL;
  const source = <Link to={sourcePath(p)}>{p.sourceNo}</Link>;
  const voidedCount = p.payments.filter((l) => l.voided).length;

  const main = (
    <>
      <InfoGrid
        items={[
          {
            label: '收款方',
            value: (
              <span style={{ display: 'inline-flex', gap: 6 }}>
                {p.payeeName || '—'} <PayeeTypePill type={p.payeeType} />
              </span>
            ),
          },
          { label: '来源单号', value: source },
          {
            label: '付款条件',
            value: p.paymentTerms,
            hidden: !p.paymentTerms,
          },
          {
            label: '币种 · 汇率',
            value: `${c} · ${Number(p.exchangeRate).toFixed(4)}`,
          },
          { label: '应付', value: <b>{formatAmount(p.amount, c)}</b> },
          { label: '已付', value: formatAmount(p.paidAmount, c) },
          {
            label: '未付',
            value: (
              <b
                style={{
                  color: p.unpaidAmount > 0 ? palette.orange : undefined,
                }}
              >
                {formatAmount(p.unpaidAmount, c)}
              </b>
            ),
          },
          {
            label: '供应商欠款',
            value:
              p.creditAmount > 0 ? (
                <span style={{ color: palette.orange }}>
                  {formatAmount(p.creditAmount, c)}
                </span>
              ) : (
                '—'
              ),
          },
          {
            label: '已付人民币',
            value: formatAmount(p.paidCny, 'CNY'),
            hidden: c === 'CNY',
          },
          {
            label: '汇兑差异',
            value: formatAmount(p.exchangeDiff ?? 0, 'CNY'),
            hidden: c === 'CNY' || !p.exchangeDiff,
          },
          {
            label: '历史迁移',
            value: '迁移前已线下付清，视为已付',
            hidden: !p.settledByMigration,
          },
        ]}
      />
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <SectionTitle
          extra={
            open && (
              <span style={{ fontSize: 13, color: palette.sub }}>
                下一期{' '}
                <DueTag
                  date={p.nextDueDate}
                  state={p.dueState}
                  days={p.dueDays}
                />
              </span>
            )
          }
        >
          付款计划
        </SectionTitle>
        <InstallmentTable payable={p} />
      </Card>
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <SectionTitle
          extra={
            voidedCount > 0 && (
              <span style={{ fontSize: 13, color: palette.mute }}>
                含 {voidedCount} 笔已作废
              </span>
            )
          }
        >
          付款记录
        </SectionTitle>
        <PaymentLineTable payable={p} showVoided />
      </Card>
      <DocFilesCard
        ownerType="PAYABLE"
        files={p.files ?? []}
        remark={p.remark}
        editable={!!p.editable}
        onAttach={(ids) => payableApi.attach(p.id, ids)}
        onRemove={(fid) => payableApi.removeAttachment(p.id, fid)}
        onRemark={(text) => payableApi.remark(p.id, text)}
        onChanged={load}
      />
    </>
  );

  return (
    <div>
      <PageTitle
        crumbs={[<MenuLink key="list" to="/finance/payables" />, p.payableNo]}
        title={
          <>
            {p.payableNo}
            <PayStatusPill status={p.status} />
          </>
        }
        description={
          <>
            {p.payeeName || '—'} · {source} · {p.createBy ?? '—'} ·{' '}
            {formatDateTime(p.createTime).slice(0, 16)} 创建
          </>
        }
        actions={
          canRegister && (
            <>
              {p.creditAmount > 0 && (
                <Button onClick={() => setRefunding(true)}>退款</Button>
              )}
              {open && (
                <Button type="primary" onClick={() => setPaying(true)}>
                  付款
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
        <div style={{ minWidth: 0 }}>{main}</div>
        <div style={{ minWidth: 0 }}>
          <RelatedDocsCard docs={relatedRows(p.related)} />
          <DocLogCard logs={p.logs} />
        </div>
      </div>
      <RegisterPaymentDrawer
        open={paying}
        anchorId={p.id}
        onClose={() => setPaying(false)}
        onDone={load}
      />
      <RefundModal
        payable={refunding ? p : undefined}
        onClose={() => setRefunding(false)}
        onDone={load}
      />
    </div>
  );
};

export default PayableDetail;
