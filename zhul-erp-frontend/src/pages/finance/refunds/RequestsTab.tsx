import { history, Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Input, Select, Space, Table } from 'antd';
import { Search } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { useColumnSettings } from '@/components/ColumnSettings';
import { CopyText } from '@/components/CopyText';
import { CustomerCell } from '@/components/DocFields';
import SourceNos from '@/components/DocFields/SourceNos';
import { tableEmpty } from '@/components/EmptyState';
import { FilterCard, FilterField, MainCard } from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { Pill } from '@/pages/inquiry/shared/components';
import { auditColumns } from '@/pages/purchase/components';
import { RQ_STATUS_TONE } from '@/pages/sales/after-sales/service';
import { PATHS } from '@/pages/sales/components';
import { readBizError } from '@/pages/sales/service';
import { ReasonModal } from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import RefundModal from './RefundModal';
import { type RefundRequest, RQ_STATUS, refundRequestApi } from './service';

const METHOD_TONE: Record<number, 'violet' | 'gray' | 'accent'> = {
  1: 'violet',
  2: 'gray',
  3: 'accent',
};

/** 来源单号：退货退款是销售退货单，其他是售后单（都没有时为空） */
export const refundSourceRef = (r: RefundRequest) =>
  r.salesReturnId
    ? { no: r.sourceNo, href: PATHS.salesReturn(r.salesReturnId) }
    : r.afterSalesId
      ? { no: r.asNo, href: PATHS.afterSales(r.afterSalesId) }
      : {};

/** 帮助文字：关键词范围以 search-scope.md「退款申请」为准 */
const KEYWORD_HELP = '可搜：申请号、退货单号、订单编号、售后单号';

/** 下拉选项后面带数量（stat-cards.md：状态并入下拉） */
const withCount = (label: string, n?: number) =>
  n ? `${label} · ${n}` : label;

/** 退款管理 → 退款申请（spec finance/refund-request）：审批（开关打开时）、执行、驳回 */
const RequestsTab: React.FC<{ onChanged?: () => void }> = ({ onChanged }) => {
  const { message, modal } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canEdit = !!access['finance:refund:edit'];
  const canApprove = !!access['finance:refund-request:approve'];
  const [counts, setCounts] = useState<{
    pendingReceipt: number;
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

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  const done = () => {
    load();
    onChanged?.();
  };

  const showApproval =
    !!counts && (counts.approvalRequired > 0 || counts.pendingApproval > 0);
  const statusOptions = [
    {
      value: RQ_STATUS.PENDING_RECEIPT,
      label: withCount('待验收', counts?.pendingReceipt),
    },
    ...(showApproval
      ? [
          {
            value: RQ_STATUS.PENDING_APPROVAL,
            label: withCount('待审批', counts?.pendingApproval),
          },
        ]
      : []),
    {
      value: RQ_STATUS.PENDING_FINANCE,
      label: withCount('待财务处理', counts?.pendingFinance),
    },
    { value: RQ_STATUS.EXECUTED, label: '已执行' },
    { value: RQ_STATUS.REJECTED, label: '已驳回' },
    { value: RQ_STATUS.WITHDRAWN, label: '已撤回' },
  ];
  const search = () => {
    setQuery(keyword.trim());
    setPage(1);
  };

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
          <CopyText text={v}>
            <Link to={PATHS.refundRequest(r.id)}>{v}</Link>
          </CopyText>
          {r.refundNo && sub(`退款单 ${r.refundNo}`)}
        </div>
      ),
    },
    {
      title: '来源单号',
      key: 'source',
      width: 150,
      render: (_, r) => <SourceNos refs={[refundSourceRef(r)]} />,
    },
    {
      title: '销售订单',
      dataIndex: 'soNo',
      width: 150,
      render: (v: string, r) => (
        <CopyText text={v}>
          <button
            type="button"
            className="zhul-link"
            onClick={() => history.push(`/sales/orders/${r.soId}`)}
          >
            {v}
          </button>
        </CopyText>
      ),
    },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 180,
      render: (v: string, r) => (
        <CustomerCell
          name={v}
          country={r.customerCountry}
          contact={r.customerContact}
        />
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
          <Pill tone={RQ_STATUS_TONE[r.status] ?? 'gray'}>{v}</Pill>
          {r.receivedHint && sub(r.receivedHint)}
          {r.status === RQ_STATUS.REJECTED && sub(r.rejectReason)}
          {r.approverName && sub(`审批：${r.approverName}`)}
        </div>
      ),
    },
    ...auditColumns<RefundRequest>(),
    {
      // lint-ui-ignore row-actions: 审批列表，通过、驳回保留文字按钮
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
  const columnsSet = useColumnSettings('finance-refunds-requests', columns, {
    hidden: ['来源单号', '销售订单', '申请人'],
  });

  return (
    <>
      <FilterCard
        onSearch={search}
        onReset={() => {
          // 重置回默认视图：待财务处理
          setKeyword('');
          setQuery('');
          setStatus(RQ_STATUS.PENDING_FINANCE);
          setPage(1);
        }}
      >
        <FilterField label="关键词" span={2} help={KEYWORD_HELP}>
          <Input
            allowClear
            prefix={<Search size={16} />}
            placeholder="申请号、退货单号、订单编号"
            aria-label="关键词"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={search}
          />
        </FilterField>
        <FilterField label="状态">
          <Select<number>
            allowClear
            placeholder="全部"
            aria-label="状态"
            value={status === 'all' ? undefined : status}
            // 状态下拉切换后立即查询
            onChange={(v) => {
              setStatus(v ?? 'all');
              setPage(1);
            }}
            options={statusOptions}
          />
        </FilterField>
      </FilterCard>
      <MainCard settings={columnsSet.settings} count={total} loading={loading}>
        <Table<RefundRequest>
          rowKey="id"
          loading={loading}
          columns={columnsSet.columns}
          dataSource={rows}
          scroll={{ x: tableScrollX(columnsSet.columns) }}
          locale={{
            emptyText: tableEmpty(
              status === RQ_STATUS.PENDING_RECEIPT
                ? '没有待验收的退款申请；退货退款没勾选「先退款」时，仓库验收前在这里'
                : status === RQ_STATUS.PENDING_FINANCE
                  ? '没有待处理的退款申请；业务员在售后单或订单详情提出'
                  : '没有符合条件的退款申请',
              loading,
            ),
          }}
          pagination={{
            ...PAGER_STYLE,
            current: page,
            pageSize,
            total,
            onChange: (p, s) => {
              setPage(p);
              setPageSize(s);
            },
          }}
        />
      </MainCard>
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
          // 申请可能已被别人执行、撤回或审批，失败要提示原因（audit UI-077）
          try {
            if (rejecting.approver) {
              await refundRequestApi.rejectApproval(rejecting.row.id, why);
            } else {
              await refundRequestApi.reject(rejecting.row.id, why);
            }
          } catch (e) {
            message.error(readBizError(e).message);
            return;
          }
          message.success('已驳回');
          setRejecting(undefined);
          done();
        }}
      />
    </>
  );
};

export default RequestsTab;
