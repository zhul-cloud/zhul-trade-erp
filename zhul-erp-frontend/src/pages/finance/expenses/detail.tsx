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
import { DocFilesCard } from '@/components/DocFiles';
import { usePageTitle } from '@/components/PageTabs/hooks';
import {
  Card,
  MigratedPill,
  PageTitle,
  Pill,
  useWide,
} from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { readBizError } from '@/pages/sales/service';
import {
  DOC_PATHS,
  ReasonModal,
  relatedRows,
} from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { UnitPill, useBusinessUnits } from '@/utils/businessUnit';
import { formatAmount, formatDateTime } from '@/utils/format';
import { MenuLink } from '@/utils/menuName';
import { PayStatusPill } from '../payables/components';
import { METHOD } from '../payables/service';
import {
  type Expense,
  type ExpenseAlloc,
  expenseApi,
} from '../reimbursements/service';
import { ExpenseDrawer } from './index';

const num: React.CSSProperties = {
  fontVariantNumeric: 'tabular-nums',
  whiteSpace: 'nowrap',
};

/** 费用单详情（change finance-expense-docs）：金额类字段生效后不能改，录错作废重开 */
const ExpenseDetail: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const wide = useWide();
  const [bu] = useBusinessUnits();
  const [e, setE] = useState<Expense>();
  const [error, setError] = useState<string>();
  const [editing, setEditing] = useState(false);
  const [voiding, setVoiding] = useState(false);
  usePageTitle(e ? `费用单 ${e.expenseNo}` : undefined, e?.expenseNo);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setE(await expenseApi.detail(id));
    } catch (err) {
      setError(readBizError(err).message);
    }
  }, [id]);
  useEffect(() => {
    load();
  }, [load]);

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!e) return <Skeleton active paragraph={{ rows: 12 }} />;

  const c = e.currencyCode;
  const valid = e.status === 1;
  const canEdit = !!e.editable && !!access['finance:expense:edit'];
  const company = e.method === METHOD.COMPANY;
  const voidBlocked = e.locked
    ? company
      ? '已经付款，不能作废'
      : '已在报销单里，不能作废'
    : undefined;
  const firstSo = e.allocations[0];

  const pay = company ? (
    <span style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
      公司对公 <PayStatusPill status={e.payStatus} />
    </span>
  ) : (
    <span>
      员工垫付 · {e.advanceUserName ?? '—'} ·{' '}
      {e.reimbursementId ? (
        <Link to={DOC_PATHS.reimbursement(e.reimbursementId)}>
          {e.reimburseNo}
        </Link>
      ) : (
        '待报销'
      )}
    </span>
  );

  const columns: TableColumnsType<ExpenseAlloc> = [
    { title: '#', key: 'i', width: 48, render: (_, __, i) => i + 1 },
    {
      title: '销售订单',
      key: 'so',
      width: 170,
      render: (_, a) => (
        <Link to={`/sales/orders/${a.soId}`}>{a.soNo ?? '—'}</Link>
      ),
    },
    {
      title: '品牌',
      dataIndex: 'brand',
      width: 120,
      render: (v?: string) => v || '—',
    },
    { title: '型号', dataIndex: 'model', width: 200 },
    {
      title: '分摊金额',
      dataIndex: 'amount',
      width: 140,
      align: 'right',
      render: (v: number) => <span style={num}>{formatAmount(v, c)}</span>,
    },
    ...(c === 'CNY'
      ? []
      : [
          {
            title: '人民币',
            dataIndex: 'amountCny',
            width: 140,
            align: 'right' as const,
            render: (v?: number) => (
              <span style={num}>{formatAmount(v ?? 0, 'CNY')}</span>
            ),
          },
        ]),
  ];

  const main = (
    <>
      <InfoGrid
        items={[
          { label: '名称', value: e.name },
          {
            label: '类别',
            value: e.categoryName ?? e.category,
          },
          { label: '发生日期', value: e.occurDate },
          {
            label: '所属事业部',
            value: e.allocations.length ? (
              <span style={{ color: palette.mute }}>跟随订单</span>
            ) : (
              <UnitPill info={bu} id={e.businessUnitId} />
            ),
            hidden: !bu.enabled,
          },
          { label: '收款方', value: e.payeeName || '—' },
          { label: '付款方式', value: pay },
          { label: '金额', value: <b>{formatAmount(e.amount, c)}</b> },
          {
            label: '汇率 · 人民币',
            value: `${Number(e.exchangeRate).toFixed(4)} · ${formatAmount(e.amountCny, 'CNY')}`,
            hidden: c === 'CNY',
          },
          {
            label: '分摊月数',
            value: `${e.amortizeMonths} 个月`,
            hidden: e.amortizeMonths <= 1,
          },
          {
            label: '关联售后单',
            value: e.afterSalesId ? (
              <Link to={`/sales/after-sales/${e.afterSalesId}`}>{e.asNo}</Link>
            ) : undefined,
            hidden: !e.afterSalesId,
          },
          {
            label: '作废原因',
            value: e.voidReason,
            hidden: valid,
          },
        ]}
      />
      {e.allocations.length > 0 && (
        <Card style={{ padding: 20, marginBottom: 16 }}>
          <SectionTitle
            extra={
              <span style={{ fontSize: 13, color: palette.sub }}>
                计入订单成本 · 合计 <b>{formatAmount(e.amount, c)}</b>
              </span>
            }
          >
            分摊明细
          </SectionTitle>
          <Table<ExpenseAlloc>
            rowKey="soItemId"
            size="middle"
            pagination={false}
            columns={columns}
            dataSource={e.allocations}
            scroll={{ x: 'max-content' }}
          />
        </Card>
      )}
      <DocFilesCard
        ownerType="EXPENSE"
        files={e.files ?? []}
        remark={e.remark}
        editable={!!e.editable}
        onAttach={(ids) => expenseApi.attach(e.id, ids)}
        onRemove={(fid) => expenseApi.removeAttachment(e.id, fid)}
        onRemark={(text) => expenseApi.remark(e.id, text)}
        onChanged={load}
      />
    </>
  );

  return (
    <div>
      <PageTitle
        crumbs={[<MenuLink key="list" to="/finance/expenses" />, e.expenseNo]}
        title={
          <>
            {e.expenseNo}
            <Pill tone={valid ? 'green' : 'mute'}>
              {valid ? '有效' : '已作废'}
            </Pill>
            {e.migrated && <MigratedPill migrated />}
          </>
        }
        description={
          <>
            {e.payeeName || '—'}
            {firstSo && (
              <>
                {' '}
                · 来源单号{' '}
                <Link to={`/sales/orders/${firstSo.soId}`}>{firstSo.soNo}</Link>
              </>
            )}{' '}
            · {e.createBy ?? '—'} · {formatDateTime(e.createTime).slice(0, 16)}{' '}
            创建
          </>
        }
        actions={
          canEdit && (
            <>
              <Button onClick={() => setEditing(true)}>修改</Button>
              <Tooltip title={voidBlocked}>
                <Button
                  danger
                  disabled={!!voidBlocked}
                  onClick={() => setVoiding(true)}
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
          <RelatedDocsCard docs={relatedRows(e.related)} />
          <DocLogCard logs={e.logs} />
        </div>
      </div>
      <ExpenseDrawer
        open={editing}
        editing={e}
        onClose={() => setEditing(false)}
        onDone={load}
      />
      <ReasonModal
        open={voiding}
        title={`作废 ${e.expenseNo}`}
        description="公司对公的应付单随之关闭，员工垫付的从待报销里去掉。金额录错请作废后重新登记。"
        okText="作废"
        danger
        onCancel={() => setVoiding(false)}
        onOk={async (reason) => {
          try {
            await expenseApi.voidExpense(e.id, reason);
            message.success('已作废');
            setVoiding(false);
            load();
          } catch (err) {
            message.error(readBizError(err).message);
          }
        }}
      />
    </div>
  );
};

export default ExpenseDetail;
