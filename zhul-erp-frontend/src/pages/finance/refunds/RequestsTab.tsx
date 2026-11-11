import { history, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Input, Segmented, Space, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerSub } from '@/components/DocFields';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import { auditColumns } from '@/pages/purchase/components';
import { readBizError } from '@/pages/sales/service';
import { ReasonModal } from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import RefundModal from './RefundModal';
import { type RefundRequest, RQ_STATUS, refundRequestApi } from './service';

const STATUS_TONE: Record<
  number,
  'orange' | 'accent' | 'green' | 'gray' | 'mute' | 'red'
> = {
  1: 'accent',
  2: 'orange',
  3: 'green',
  4: 'red',
  5: 'mute',
};
const METHOD_TONE: Record<number, 'violet' | 'gray' | 'accent'> = {
  1: 'violet',
  2: 'gray',
  3: 'accent',
};

/** 退款管理 → 退款申请（spec finance/refund-request）：审批（开关打开时）、执行、驳回 */
const RequestsTab: React.FC<{ onChanged?: () => void }> = ({ onChanged }) => {
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canEdit = !!access['finance:refund:edit'];
  const canApprove = !!access['finance:refund-request:approve'];
  const [counts, setCounts] = useState<{
    pendingApproval: number;
    pendingFinance: number;
    approvalRequired: number;
  }>();
  const [status, setStatus] = useState<number | 'all'>(
    RQ_STATUS.PENDING_FINANCE,
  );
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<RefundRequest[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [executing, setExecuting] = useState<RefundRequest>();
  const [rejecting, setRejecting] = useState<{
    row: RefundRequest;
    approver: boolean;
  }>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [res, c] = await Promise.all([
        refundRequestApi.page({
          page,
          pageSize,
          status: status === 'all' ? undefined : status,
          keyword: query || undefined,
        }),
        refundRequestApi.counts(),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setCounts(c);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, status, query, message]);

  useEffect(() => {
    load();
  }, [load]);

  const done = () => {
    load();
    onChanged?.();
  };

  const showApproval =
    !!counts && (counts.approvalRequired > 0 || counts.pendingApproval > 0);
  const tabs = [
    ...(showApproval
      ? [
          {
            value: RQ_STATUS.PENDING_APPROVAL as number | 'all',
            label: `待审批 ${counts?.pendingApproval ?? 0}`,
          },
        ]
      : []),
    {
      value: RQ_STATUS.PENDING_FINANCE,
      label: `待财务处理 ${counts?.pendingFinance ?? 0}`,
    },
    { value: RQ_STATUS.EXECUTED, label: '已执行' },
    { value: RQ_STATUS.REJECTED, label: '已驳回' },
    { value: RQ_STATUS.WITHDRAWN, label: '已撤回' },
    { value: 'all' as const, label: '全部' },
  ];

  const sub = (t?: React.ReactNode) =>
    t ? <div style={{ fontSize: 12, color: palette.mute }}>{t}</div> : null;

  const columns: TableColumnsType<RefundRequest> = [
    {
      title: '申请',
      dataIndex: 'requestNo',
      width: 150,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <b style={{ color: palette.ink }}>{v}</b>
          {r.refundNo && sub(`退款单 ${r.refundNo}`)}
        </div>
      ),
    },
    {
      title: '来源单号 · 订单',
      key: 'as',
      width: 170,
      render: (_, r) => (
        <div>
          {r.afterSalesId ? (
            <a
              onClick={() =>
                history.push(`/sales/after-sales/${r.afterSalesId}`)
              }
            >
              {r.asNo}
            </a>
          ) : (
            <span style={{ color: palette.mute }}>—</span>
          )}
          {sub(
            <a onClick={() => history.push(`/sales/orders/${r.soId}`)}>
              {r.soNo}
            </a>,
          )}
        </div>
      ),
    },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 180,
      render: (v: string, r) => (
        <div>
          {v}
          {(r.customerCountry || r.customerContact) && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              <CustomerSub
                country={r.customerCountry}
                contact={r.customerContact}
              />
            </div>
          )}
        </div>
      ),
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 130,
      align: 'right',
      render: (v: number, r) => (
        <b style={{ fontVariantNumeric: 'tabular-nums' }}>
          {formatAmount(v, r.currencyCode)}
        </b>
      ),
    },
    { title: '原因', dataIndex: 'reasonName', width: 100 },
    {
      title: '建议去向',
      dataIndex: 'methodName',
      width: 130,
      render: (v: string, r) => (
        <Pill tone={METHOD_TONE[r.method] ?? 'gray'}>{v}</Pill>
      ),
    },
    {
      title: '申请人',
      dataIndex: 'applicantName',
      width: 90,
      render: (v: string, r) => (
        <div>
          {v ?? '—'}
          {r.payeeInfo && sub(r.payeeInfo)}
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'statusName',
      width: 160,
      render: (v: string, r) => (
        <div>
          <Pill tone={STATUS_TONE[r.status] ?? 'gray'}>{v}</Pill>
          {r.status === RQ_STATUS.REJECTED && sub(r.rejectReason)}
          {r.approverName && sub(`审批：${r.approverName}`)}
        </div>
      ),
    },
    ...auditColumns<RefundRequest>(),
    {
      title: '操作',
      key: 'op',
      width: 170,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          {r.status === RQ_STATUS.PENDING_APPROVAL && canApprove && (
            <>
              <Button
                size="small"
                type="primary"
                onClick={() =>
                  modal.confirm({
                    title: `通过 ${r.requestNo}？`,
                    content: `${formatAmount(r.amount, r.currencyCode)}，通过后交财务处理。`,
                    okText: '通过',
                    onOk: async () => {
                      try {
                        await refundRequestApi.approve(r.id);
                        message.success('已通过');
                        done();
                      } catch (e) {
                        message.error(readBizError(e).message);
                      }
                    },
                  })
                }
              >
                通过
              </Button>
              <Button
                size="small"
                type="link"
                danger
                onClick={() => setRejecting({ row: r, approver: true })}
              >
                驳回
              </Button>
            </>
          )}
          {r.status === RQ_STATUS.PENDING_FINANCE && canEdit && (
            <>
              <Button
                size="small"
                type="primary"
                onClick={() => setExecuting(r)}
              >
                执行
              </Button>
              <Button
                size="small"
                type="link"
                danger
                onClick={() => setRejecting({ row: r, approver: false })}
              >
                驳回
              </Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card style={{ padding: 16 }}>
      <div
        style={{
          display: 'flex',
          gap: 12,
          flexWrap: 'wrap',
          alignItems: 'center',
          marginBottom: 12,
        }}
      >
        <Segmented<number | 'all'>
          value={status}
          onChange={(v) => {
            setStatus(v);
            setPage(1);
          }}
          options={tabs}
        />
        <Input.Search
          allowClear
          placeholder="申请号、售后单号、订单号"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onSearch={(v) => {
            setQuery(v.trim());
            setPage(1);
          }}
          style={{ width: 260 }}
          aria-label="关键词"
        />
      </div>
      <Table<RefundRequest>
        rowKey="id"
        size="middle"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: tableScrollX(columns) }}
        locale={{
          emptyText:
            status === RQ_STATUS.PENDING_FINANCE
              ? '没有待处理的退款申请；业务员在售后单或订单详情提出'
              : '没有符合条件的退款申请',
        }}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPage(p);
            setPageSize(s);
          },
        }}
      />
      <RefundModal
        open={!!executing}
        soId={executing?.soId}
        request={executing}
        onClose={() => setExecuting(undefined)}
        onDone={done}
      />
      <ReasonModal
        open={!!rejecting}
        title={`驳回 ${rejecting?.row.requestNo ?? ''}`}
        description={
          rejecting?.approver
            ? '驳回后申请结束，业务员可以重新提出。'
            : '驳回后申请结束，不生成退款单。'
        }
        okText="驳回"
        danger
        onCancel={() => setRejecting(undefined)}
        onOk={async (why) => {
          if (!rejecting) return;
          if (rejecting.approver) {
            await refundRequestApi.rejectApproval(rejecting.row.id, why);
          } else {
            await refundRequestApi.reject(rejecting.row.id, why);
          }
          message.success('已驳回');
          setRejecting(undefined);
          done();
        }}
      />
    </Card>
  );
};

export default RequestsTab;
