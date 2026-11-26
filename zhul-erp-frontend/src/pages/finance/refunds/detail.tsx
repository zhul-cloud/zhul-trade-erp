import { useAccess, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Skeleton, Table } from 'antd';
import { CircleX } from 'lucide-react';
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
import { MoreActions, PageHeader } from '@/components/ListLayout';
import { useOnReactivate, usePageTitle } from '@/components/PageTabs/hooks';
import { UserName } from '@/components/UserName';
import { Card, Pill, useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { PATHS } from '@/pages/sales/components';
import { readBizError } from '@/pages/sales/service';
import { ReasonModal, relatedRows } from '@/pages/warehouse/components';
import { UnitPill, useBusinessUnits } from '@/utils/businessUnit';
import { formatAmount, formatDateTime, formatMoney } from '@/utils/format';
import { tableScrollX } from '@/utils/tableScroll';
import {
  REFUND_TYPE_WITHDRAW,
  type Refund,
  type RefundRequestItem,
  refundApi,
} from './service';

/** 退款单详情（change finance-cash-docs）：生效后只改备注与附件，录错作废重开 */
const RefundDetail: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { message } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const wide = useWide();
  const [bu] = useBusinessUnits();
  const [f, setF] = useState<Refund>();
  const [error, setError] = useState<string>();
  const [voiding, setVoiding] = useState(false);
  usePageTitle(f ? `退款单 ${f.refundNo}` : undefined, f?.refundNo);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setF(await refundApi.detail(id));
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
  if (!f) return <Skeleton active paragraph={{ rows: 12 }} />;

  const cur = f.currencyCode;
  const valid = f.status === 1;
  const withdraw = f.refundType === REFUND_TYPE_WITHDRAW;
  const source = f.refundRequestId
    ? { no: f.requestNo ?? '', href: PATHS.refundRequest(f.refundRequestId) }
    : f.soId
      ? { no: f.soNo ?? '', href: PATHS.order(f.soId) }
      : undefined;

  const columns: TableColumnsType<RefundRequestItem> = [
    {
      title: '#',
      key: 'no',
      width: 48,
      render: (_, __, i) => i + 1,
    },
    ...docBrandColumns<RefundRequestItem>((x) => x),
    {
      title: '型号 / 项目',
      dataIndex: 'itemName',
      width: 220,
      render: (v: string, x) =>
        x.lineType === 1 ? <DocModelCell model={v} brand={x.brand} /> : v,
    },
    {
      title: '数量',
      align: 'right' as const,
      dataIndex: 'quantity',
      width: 70,
      render: (v?: number) => v ?? '—',
    },
    {
      title: `单价（${cur}）`,
      align: 'right',
      dataIndex: 'unitPrice',
      width: 120,
      render: (v?: number) => (v == null ? '—' : formatMoney(v)),
    },
    {
      title: `金额（${cur}）`,
      align: 'right',
      dataIndex: 'amount',
      width: 120,
      render: (v: number) => <b>{formatMoney(v)}</b>,
    },
  ];

  const info = (
    <>
      <InfoGrid
        items={[
          { label: '类型', value: withdraw ? '余额提现' : '售后退款' },
          { label: '退款日期', value: f.refundDate },
          { label: '原因', value: f.reasonName },
          { label: '去向', value: f.methodName },
          {
            label: '收款账户',
            value: f.payeeAccount,
            hidden: !f.payeeAccount,
          },
          {
            label: '金额',
            value: formatAmount(f.amount, cur),
          },
          {
            label: '人民币',
            value: `${formatAmount(f.amountCny, 'CNY')}${cur === 'CNY' ? '' : ` · 汇率 ${Number(f.exchangeRate).toFixed(6)}`}`,
          },
          {
            label: '所属事业部',
            value: <UnitPill info={bu} id={f.businessUnitId} />,
            hidden: !bu.enabled,
          },
          { label: '作废原因', value: f.voidReason, hidden: valid },
        ]}
      />
    </>
  );
  // 明细等宽内容放两栏下方通栏，1366 宽下不被挤出（照销售订单，复审 P18）
  const wideSections = (
    <>
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <SectionTitle extra={<b>合计 {formatAmount(f.amount, cur)}</b>}>
          明细
        </SectionTitle>
        <Table<RefundRequestItem>
          rowKey={(x) => `${x.id}`}
          size="middle"
          pagination={false}
          columns={columns}
          dataSource={f.items ?? []}
          scroll={{ x: tableScrollX(columns) }}
        />
      </Card>
      <DocFilesCard
        ownerType="REFUND"
        files={f.files ?? []}
        remark={f.remark}
        editable={!!f.editable}
        onAttach={(ids) => refundApi.attach(f.id, ids)}
        onRemove={(fid) => refundApi.removeAttachment(f.id, fid)}
        onRemark={(text) => refundApi.remark(f.id, text)}
        onChanged={load}
      />
    </>
  );

  return (
    <div>
      <PageHeader
        title={f.refundNo}
        tags={
          <Pill tone={valid ? 'green' : 'mute'}>
            {valid ? '有效' : '已作废'}
          </Pill>
        }
        sub={
          <>
            {f.customerName ?? '—'} ·{' '}
            {source ? <SourceNos mode="inline" refs={[source]} /> : '—'} ·{' '}
            <UserName value={f.createBy} /> ·{' '}
            {formatDateTime(f.createTime).slice(0, 16)} 创建
          </>
        }
        // 作废收进「更多」；迁移数据不能作废，原因写在菜单项里
        extra={
          valid &&
          !!access['finance:refund:edit'] && (
            <MoreActions
              items={[
                {
                  key: 'void',
                  label: '作废',
                  icon: <CircleX size={16} />,
                  danger: true,
                  disabledReason: f.migrated
                    ? '历史迁移数据不能修改'
                    : undefined,
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
          <RelatedDocsCard docs={relatedRows(f.related)} />
          <DocLogCard logs={f.logs} />
        </div>
      </div>
      {wideSections}
      <ReasonModal
        open={voiding}
        title={`作废 ${f.refundNo}`}
        description={
          withdraw
            ? '作废后客户余额恢复，自动生成的汇兑损益一起作废。'
            : '作废后不再冲减订单毛利，也不再计入经营统计；转客户余额的会撤回这笔余额；由退款申请执行的，申请回到「待财务处理」。'
        }
        placeholder="如：金额录错"
        okText="作废"
        danger
        onCancel={() => setVoiding(false)}
        onOk={async (why) => {
          try {
            await refundApi.voidRefund(f.id, why);
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

export default RefundDetail;
