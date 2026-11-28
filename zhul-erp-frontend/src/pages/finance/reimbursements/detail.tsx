import { Link, useAccess, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Skeleton, Table } from 'antd';
import { CircleX, Undo2 } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import {
  DocColumns,
  DocLogCard,
  InfoGrid,
  RelatedDocsCard,
  SectionTitle,
} from '@/components/DocFields/DocPage';
import { DocFilesCard } from '@/components/DocFiles';
import { ellipsisRender } from '@/components/EllipsisText';
import { MoreActions, PageHeader } from '@/components/ListLayout';
import { useOnReactivate, usePageTitle } from '@/components/PageTabs/hooks';
import { Card, useWide } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { readBizError } from '@/pages/sales/service';
import {
  DOC_PATHS,
  ReasonModal,
  relatedRows,
} from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { tableScrollX } from '@/utils/tableScroll';
import { ProofLinks } from '../payables/components';
import { keyOf, num, PayModal, StatusPill, sourceCell } from './components';
import {
  type AdvanceItem,
  BX_STATUS,
  type Reimbursement,
  reimburseApi,
} from './service';

/** 报销单详情（change finance-expense-docs）：撤回、登记已付、撤销已付在这里；备注与附件随时可改，撤回后只读 */
const ReimbursementDetail: React.FC = () => {
  const id = Number(useParams<{ id: string }>().id);
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canPay = !!access['finance:reimburse:pay'];
  const wide = useWide();
  const [bx, setBx] = useState<Reimbursement>();
  const [error, setError] = useState<string>();
  const [paying, setPaying] = useState(false);
  const [reason, setReason] = useState<'withdraw' | 'unpay'>();
  usePageTitle(bx ? `报销单 ${bx.reimburseNo}` : undefined, bx?.reimburseNo);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setBx(await reimburseApi.detail(id));
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
  if (!bx) return <Skeleton active paragraph={{ rows: 12 }} />;

  const paid = bx.status === BX_STATUS.PAID;
  const columns: TableColumnsType<AdvanceItem> = [
    { title: '#', key: 'i', width: 48, render: (_, __, i) => i + 1 },
    {
      // 日期并到来源单号下，主栏 664 宽不横向滚动（UI-119）
      title: '来源单号 · 日期',
      key: 'source',
      width: 220,
      render: (v, i) => (
        <div>
          {sourceCell(v, i)}
          <div style={{ fontSize: 12, color: palette.mute }}>
            {i.occurDate || '—'}
          </div>
        </div>
      ),
    },
    {
      title: '说明',
      dataIndex: 'description',
      width: 180,
      render: ellipsisRender,
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 130,
      align: 'right',
      render: (v: number) => (
        <span style={{ ...num, color: v < 0 ? palette.orange : undefined }}>
          {formatAmount(v, 'CNY')}
        </span>
      ),
    },
    {
      title: '截图',
      key: 'proofs',
      width: 80,
      render: (_, i) => <ProofLinks files={i.proofs} />,
    },
  ];

  const info = (
    <>
      <InfoGrid
        items={[
          { label: '报销人', value: bx.applicantName },
          {
            label: '提交时间',
            value: formatDateTime(bx.submittedAt).slice(0, 16),
          },
          { label: '项数', value: bx.lineCount },
          {
            label: '合计',
            value: <b>{formatAmount(bx.totalAmount, 'CNY')}</b>,
          },
          { label: '付款日期', value: bx.payDate, hidden: !paid },
          {
            label: '付款方式',
            value: bx.payMethod === 3 ? '私对私' : '公司对公',
            hidden: !paid,
          },
          {
            label: '付款账户',
            value: bx.payMethod === 3 ? bx.payPayerAccount : bx.payAccountName,
            hidden: !paid,
          },
          {
            label: '付款单',
            value: bx.paymentId ? (
              <Link
                to={DOC_PATHS.payment(bx.paymentId)}
                className="zhul-link zhul-link-doc"
              >
                {bx.paymentNo}
              </Link>
            ) : (
              bx.paymentNo
            ),
            hidden: !paid,
          },
          { label: '登记人', value: bx.paidBy, hidden: !paid },
          {
            label: '付款说明',
            value: bx.payRemark,
            hidden: !paid || !bx.payRemark,
          },
          {
            label: '撤回原因',
            value: bx.withdrawReason,
            hidden: bx.status !== BX_STATUS.WITHDRAWN,
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
            <span style={{ fontSize: 13, color: palette.sub }}>
              合计 <b>{formatAmount(bx.totalAmount, 'CNY')}</b>
            </span>
          }
        >
          明细
        </SectionTitle>
        <Table<AdvanceItem>
          rowKey={keyOf}
          size="middle"
          pagination={false}
          columns={columns}
          dataSource={bx.lines}
          scroll={{ x: tableScrollX(columns) }}
        />
      </Card>
      <DocFilesCard
        ownerType="REIMBURSE"
        files={bx.files ?? []}
        remark={bx.remark}
        editable={!!bx.editable}
        onAttach={(ids) => reimburseApi.attach(bx.id, ids)}
        onRemove={(fid) => reimburseApi.removeAttachment(bx.id, fid)}
        onRemark={(text) => reimburseApi.remark(bx.id, text)}
        onChanged={load}
      />
    </>
  );

  return (
    <div>
      <PageHeader
        title={bx.reimburseNo}
        tags={<StatusPill status={bx.status} />}
        sub={
          <>
            {bx.applicantName} · {bx.lineCount} 项 ·{' '}
            {formatDateTime(bx.submittedAt).slice(0, 16)} 提交
          </>
        }
        extra={
          <>
            {/* 撤回、撤销已付都会退回上一步，收进「更多」 */}
            <MoreActions
              items={[
                bx.canWithdraw && {
                  key: 'withdraw',
                  label: '撤回',
                  icon: <Undo2 size={16} />,
                  danger: true,
                },
                canPay &&
                  paid && {
                    key: 'unpay',
                    label: '撤销已付',
                    icon: <CircleX size={16} />,
                    danger: true,
                  },
              ]}
              onClick={(k) => setReason(k as 'withdraw' | 'unpay')}
            />
            {canPay && bx.status === BX_STATUS.PENDING && (
              <Button type="primary" onClick={() => setPaying(true)}>
                登记已付
              </Button>
            )}
          </>
        }
      />
      <DocColumns
        wide={wide}
        side={
          <>
            <RelatedDocsCard docs={relatedRows(bx.related)} />
            <DocLogCard logs={bx.logs} />
          </>
        }
      >
        {info}
        {wideSections}
      </DocColumns>
      <PayModal
        targets={paying ? [bx] : undefined}
        onClose={() => setPaying(false)}
        onDone={load}
      />
      <ReasonModal
        open={!!reason}
        title={
          reason === 'unpay'
            ? `撤销已付 ${bx.reimburseNo}`
            : `撤回 ${bx.reimburseNo}`
        }
        description={
          reason === 'unpay'
            ? '报销单回到「待付款」，对应的报销付款单作废。'
            : '撤回后各项回到待报销，补传的截图保留。'
        }
        okText={reason === 'unpay' ? '撤销已付' : '撤回'}
        danger
        onCancel={() => setReason(undefined)}
        onOk={async (why) => {
          try {
            await (reason === 'unpay'
              ? reimburseApi.unpay(bx.id, why)
              : reimburseApi.withdraw(bx.id, why));
            message.success(reason === 'unpay' ? '已撤销已付' : '已撤回');
            setReason(undefined);
            load();
          } catch (e) {
            message.error(readBizError(e).message);
          }
        }}
      />
    </div>
  );
};

export default ReimbursementDetail;
