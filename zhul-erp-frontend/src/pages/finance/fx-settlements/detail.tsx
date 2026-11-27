import { Link, useAccess, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Skeleton, Table } from 'antd';
import { CircleX } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import {
  DocLogCard,
  InfoGrid,
  RelatedDocsCard,
  SectionTitle,
} from '@/components/DocFields/DocPage';
import { DocFilesCard } from '@/components/DocFiles';
import { tableEmpty } from '@/components/EmptyState';
import { MoreActions, PageHeader } from '@/components/ListLayout';
import { useOnReactivate, usePageTitle } from '@/components/PageTabs/hooks';
import { UserName } from '@/components/UserName';
import { Card, Pill, useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { readBizError } from '@/pages/sales/service';
import { ReasonModal, relatedRows } from '@/pages/warehouse/components';
import { UnitPill, useBusinessUnits } from '@/utils/businessUnit';
import { formatAmount, formatDateTime, formatMoney } from '@/utils/format';
import { tableScrollX } from '@/utils/tableScroll';
import { ReceiptWho, SettlePill } from './components';
import { type FxSettlement, fxApi } from './service';

type Allocation = NonNullable<FxSettlement['allocations']>[number];

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

/** 结汇单详情（change finance-ap-fx-docs）：有效时只改备注与附件，录错作废重登 */
const FxSettlementDetail: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { message } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const wide = useWide();
  const [bu] = useBusinessUnits();
  const [s, setS] = useState<FxSettlement>();
  const [error, setError] = useState<string>();
  const [voiding, setVoiding] = useState(false);
  usePageTitle(s ? `结汇单 ${s.settleNo}` : undefined, s?.settleNo);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setS(await fxApi.detail(id));
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
  if (!s) return <Skeleton active paragraph={{ rows: 12 }} />;

  const cur = s.currencyCode;
  const hasFee = Number(s.feeAmount ?? 0) > 0;
  const valid = s.status === 1;
  const allocations = s.allocations ?? [];
  const blocked = s.migrated
    ? '迁移生成的结汇单不能作废'
    : s.blockingNo
      ? `请先作废之后的结汇单 ${s.blockingNo}`
      : undefined;

  const columns: TableColumnsType<Allocation> = [
    { title: '#', key: 'i', width: 48, render: (_, __, i) => i + 1 },
    {
      title: '收款单号',
      key: 'no',
      width: 150,
      render: (_, a) =>
        a.receipt?.receiptNo ? (
          <Link to={`/finance/receipts/${a.receipt.id}`}>
            {a.receipt.receiptNo}
          </Link>
        ) : (
          '—'
        ),
    },
    {
      title: '到账日期',
      key: 'd',
      width: 110,
      render: (_, a) => a.receipt?.receiptDate,
    },
    {
      title: '来源单号 · 客户',
      key: 'pi',
      width: 220,
      render: (_, a) => (a.receipt ? <ReceiptWho r={a.receipt} /> : '—'),
    },
    {
      title: `分摊外币（${cur}）`,
      dataIndex: 'amount',
      width: 130,
      align: 'right',
      render: (v: number) => <span style={num}>{formatMoney(v)}</span>,
    },
    {
      title: '分摊人民币',
      dataIndex: 'amountCny',
      width: 130,
      align: 'right',
      render: (v: number) => <span style={num}>{formatMoney(v)}</span>,
    },
    ...(hasFee
      ? [
          {
            title: '分摊手续费',
            dataIndex: 'feeAmountCny',
            width: 120,
            align: 'right' as const,
            render: (v?: number) => (
              <span style={num}>{formatAmount(v ?? 0, 'CNY')}</span>
            ),
          },
        ]
      : []),
    {
      title: '收款单结汇',
      key: 's',
      width: 110,
      render: (_, a) => <SettlePill status={a.receipt?.settleStatus} />,
    },
  ];

  const info = (
    <>
      <InfoGrid
        items={[
          { label: '结汇日期', value: s.settleDate },
          {
            label: '所属事业部',
            value: <UnitPill info={bu} id={s.businessUnitId} />,
            hidden: !bu.enabled,
          },
          { label: '币种', value: cur },
          { label: '结汇金额', value: <b>{formatAmount(s.amount, cur)}</b> },
          {
            label: '手续费',
            value: `${formatAmount(s.feeAmount, cur)} · ${formatAmount(s.feeAmountCny, 'CNY')}`,
            hidden: !hasFee,
          },
          { label: '结汇汇率', value: Number(s.exchangeRate).toFixed(6) },
          {
            label: '入账人民币',
            value: <b>{formatAmount(s.amountCny, 'CNY')}</b>,
          },
          { label: '入账账户', value: s.bankAccountName },
          {
            label: '分摊',
            value: `${allocations.length} 笔收款单`,
          },
          {
            label: '作废',
            value: `${s.voidReason ?? ''}${s.voidedBy ? ` · ${s.voidedBy}` : ''}`,
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
            <b style={num}>
              合计 {formatAmount(s.amount, cur)} ·{' '}
              {formatAmount(
                Number(s.amountCny) + Number(s.feeAmountCny ?? 0),
                'CNY',
              )}
            </b>
          }
        >
          {valid ? '分摊明细' : '分摊明细（作废前）'}
        </SectionTitle>
        <Table<Allocation>
          rowKey={(a) => a.receipt?.id ?? 0}
          size="middle"
          pagination={false}
          columns={columns}
          dataSource={allocations}
          scroll={{ x: tableScrollX(columns) }}
          locale={{ emptyText: tableEmpty('没有分摊', undefined, true) }}
        />
      </Card>
      <DocFilesCard
        ownerType="FX_SETTLEMENT"
        files={s.files ?? []}
        remark={s.remark}
        editable={!!s.editable}
        onAttach={(ids) => fxApi.attach(s.id, ids)}
        onRemove={(fid) => fxApi.removeAttachment(s.id, fid)}
        onRemark={(text) => fxApi.remark(s.id, text)}
        onChanged={load}
      />
    </>
  );

  return (
    <div>
      <PageHeader
        title={s.settleNo}
        tags={
          <Pill tone={valid ? 'green' : 'mute'}>
            {valid ? '有效' : '已作废'}
          </Pill>
        }
        sub={
          <>
            {bu.enabled && (
              <>
                <UnitPill info={bu} id={s.businessUnitId} /> ·{' '}
              </>
            )}
            {s.bankAccountName ?? '—'} · <UserName value={s.createBy} /> ·{' '}
            {formatDateTime(s.createTime).slice(0, 16)} 创建
          </>
        }
        extra={
          valid &&
          !!access['finance:fx:settle'] && (
            <MoreActions
              items={[
                {
                  key: 'void',
                  label: '作废',
                  icon: <CircleX size={16} />,
                  danger: true,
                  disabledReason: blocked || undefined,
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
          <RelatedDocsCard docs={relatedRows(s.related)} />
          <DocLogCard logs={s.logs} />
        </div>
      </div>
      {wideSections}
      <ReasonModal
        open={voiding}
        title={`作废 ${s.settleNo}`}
        description="撤回它的全部分摊，相关到账回到未结汇或部分结汇，未结完的到账按上一张结汇单的汇率重新估算。金额录错请作废后重新登记。"
        placeholder="如：入账金额填错"
        okText="作废"
        danger
        onCancel={() => setVoiding(false)}
        onOk={async (why) => {
          try {
            await fxApi.voidSettlement(s.id, why);
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

export default FxSettlementDetail;
