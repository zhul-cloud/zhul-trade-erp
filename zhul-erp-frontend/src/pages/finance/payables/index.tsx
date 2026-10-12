import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  Checkbox,
  Drawer,
  Input,
  Select,
  Space,
  Spin,
  Table,
} from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle } from '@/pages/inquiry/shared/components';
import { auditColumns } from '@/pages/purchase/components';
import { CURRENCIES } from '@/pages/quotation/components';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import {
  DueTag,
  PayableBody,
  PayeeTypePill,
  PayStatusPill,
  RefundModal,
  RegisterPaymentDrawer,
} from './components';
import {
  PAY_STATUS,
  PAYEE,
  type Payable,
  type PayableQuery,
  type PayableStats,
  payableApi,
  SOURCE,
} from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

interface Filters {
  keyword: string;
  payeeType?: number;
  status?: number;
  due?: string;
  currencyCode?: string;
  hasCredit: boolean;
  showClosed: boolean;
}

const initial = (): Filters => ({
  keyword: '',
  hasCredit: false,
  showClosed: false,
});

/** 来源单据链接：采购订单、运费账单 */
const sourcePath = (p: Payable) =>
  p.sourceType === SOURCE.PO
    ? `/purchase/orders/${p.sourceId}`
    : p.sourceType === SOURCE.EXPENSE
      ? '/finance/expenses'
      : '/logistics/statements';

/** 财务管理 → 应付单：采购订单、运费账单确认后自动生成；付款按到期先后核销 */
const PayableManagement: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canRegister = !!access['finance:payment:register'];
  const [draft, setDraft] = useState<Filters>(initial);
  const [filters, setFilters] = useState<Filters>(initial);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<Payable[]>([]);
  const [total, setTotal] = useState(0);
  const [stats, setStats] = useState<PayableStats>();
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<Payable>();
  const [detailLoading, setDetailLoading] = useState(false);
  const [payAnchor, setPayAnchor] = useState<number | 'pick'>();
  const [refund, setRefund] = useState<Payable>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const q: PayableQuery = {
        page,
        pageSize,
        keyword: filters.keyword.trim() || undefined,
        payeeType: filters.payeeType,
        status: filters.status,
        due: filters.due,
        currencyCode: filters.currencyCode,
        hasCredit: filters.hasCredit || undefined,
        showClosed: filters.showClosed || undefined,
      };
      const [res, s] = await Promise.all([
        payableApi.page(q),
        payableApi.stats(),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setStats(s);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [filters, page, pageSize, message]);

  useEffect(() => {
    load();
  }, [load]);

  const openDetail = async (id: number) => {
    setDetailLoading(true);
    setDetail(undefined);
    try {
      setDetail(await payableApi.detail(id));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setDetailLoading(false);
    }
  };

  const apply = (next: Partial<Filters>) => {
    const f = { ...draft, ...next };
    setDraft(f);
    setFilters(f);
    setPage(1);
  };

  const statCard = (
    label: string,
    value: number,
    hint: string,
    color: string,
    due?: string,
    credit?: boolean,
  ) => {
    const active = credit ? filters.hasCredit : !!due && filters.due === due;
    return (
      <button
        type="button"
        onClick={() =>
          apply(
            credit
              ? { hasCredit: !filters.hasCredit, due: undefined }
              : { due: active ? undefined : due, hasCredit: false },
          )
        }
        style={{
          flex: '1 1 200px',
          textAlign: 'left',
          padding: '16px 18px',
          borderRadius: 14,
          background: palette.card,
          border: `1px solid ${active ? palette.link : palette.hairline}`,
          cursor: 'pointer',
          color: palette.ink,
        }}
      >
        <div style={{ fontSize: 13, color: palette.sub }}>{label}</div>
        <div
          style={{ ...num, fontSize: 22, fontWeight: 800, color, marginTop: 4 }}
        >
          {formatAmount(value, 'CNY')}
        </div>
        <div style={{ fontSize: 12, color: palette.mute }}>{hint}</div>
      </button>
    );
  };

  const columns: TableColumnsType<Payable> = [
    {
      title: '编号',
      dataIndex: 'payableNo',
      width: 140,
      fixed: 'left',
      render: (v: string, r) => <a onClick={() => openDetail(r.id)}>{v}</a>,
    },
    {
      title: '收款方',
      key: 'payee',
      width: 200,
      render: (_, r) => (
        <div>
          <div style={{ fontWeight: 600 }}>{r.payeeName || '—'}</div>
          <PayeeTypePill type={r.payeeType} />
        </div>
      ),
    },
    {
      title: '来源',
      key: 'source',
      width: 170,
      render: (_, r) => (
        <a onClick={() => history.push(sourcePath(r))}>{r.sourceNo}</a>
      ),
    },
    { title: '币种', dataIndex: 'currencyCode', width: 64 },
    {
      title: '应付',
      dataIndex: 'amount',
      width: 120,
      align: 'right',
      render: (v: number, r) => (
        <span style={num}>{formatAmount(v, r.currencyCode)}</span>
      ),
    },
    {
      title: '已付',
      dataIndex: 'paidAmount',
      width: 120,
      align: 'right',
      render: (v: number, r) => (
        <span style={num}>{formatAmount(v, r.currencyCode)}</span>
      ),
    },
    {
      title: '未付',
      dataIndex: 'unpaidAmount',
      width: 120,
      align: 'right',
      render: (v: number, r) => (
        <b style={num}>{formatAmount(v, r.currencyCode)}</b>
      ),
    },
    {
      title: '欠款',
      dataIndex: 'creditAmount',
      width: 100,
      align: 'right',
      render: (v: number, r) =>
        v > 0 ? (
          <span style={{ ...num, color: palette.orange }}>
            {formatAmount(v, r.currencyCode)}
          </span>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '下一期到期',
      key: 'due',
      width: 160,
      render: (_, r) => (
        <DueTag date={r.nextDueDate} state={r.dueState} days={r.dueDays} />
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 96,
      render: (v: number) => <PayStatusPill status={v} />,
    },
    ...auditColumns<Payable>(),
    {
      title: '操作',
      key: 'op',
      width: 110,
      fixed: 'right',
      render: (_, r) => (
        <Space size={10}>
          {canRegister &&
            (r.status === PAY_STATUS.UNPAID ||
              r.status === PAY_STATUS.PARTIAL) && (
              <a onClick={() => setPayAnchor(r.id)}>付款</a>
            )}
          {canRegister && r.creditAmount > 0 && (
            <a onClick={() => setRefund(r)}>退款</a>
          )}
          {!canRegister && <a onClick={() => openDetail(r.id)}>查看</a>}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <PageTitle
        crumbs={['应付单']}
        title="应付单"
        description="采购订单确认下单、运费账单确认后自动生成应付；付款按到期先后核销，多付的部分记为供应商欠款，可以抵扣下一笔或登记退款。"
        actions={
          canRegister && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setPayAnchor('pick')}
            >
              登记付款
            </Button>
          )
        }
      />
      <div
        style={{ display: 'flex', gap: 14, flexWrap: 'wrap', marginBottom: 16 }}
      >
        {statCard(
          '本月到期未付',
          stats?.monthDue ?? 0,
          `${stats?.monthDueCount ?? 0} 张应付单`,
          palette.ink,
          'MONTH',
        )}
        {statCard(
          '已逾期未付',
          stats?.overdue ?? 0,
          `${stats?.overdueCount ?? 0} 张 · 点击筛出`,
          palette.red,
          'OVERDUE',
        )}
        {statCard(
          '7 天内到期',
          stats?.soon ?? 0,
          `${stats?.soonCount ?? 0} 张`,
          palette.orange,
          'SOON',
        )}
        {statCard(
          '供应商欠款',
          stats?.credit ?? 0,
          '可抵扣或登记退款',
          palette.orange,
          undefined,
          true,
        )}
      </div>
      <Card>
        <div
          style={{
            display: 'flex',
            gap: 10,
            flexWrap: 'wrap',
            marginBottom: 14,
            alignItems: 'center',
          }}
        >
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="编号、收款方、来源单号"
            style={{ width: 240 }}
            value={draft.keyword}
            onChange={(e) => setDraft({ ...draft, keyword: e.target.value })}
            onPressEnter={() => apply({})}
          />
          <Select
            allowClear
            placeholder="收款方类型"
            style={{ width: 130 }}
            value={draft.payeeType}
            onChange={(v) => apply({ payeeType: v })}
            options={[
              { value: PAYEE.SUPPLIER, label: '供应商' },
              { value: PAYEE.SHOP, label: '线上店铺' },
              { value: PAYEE.FORWARDER, label: '货代' },
            ]}
          />
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 120 }}
            value={draft.status}
            onChange={(v) => apply({ status: v })}
            options={[
              { value: PAY_STATUS.UNPAID, label: '未付' },
              { value: PAY_STATUS.PARTIAL, label: '部分付款' },
              { value: PAY_STATUS.PAID, label: '已付清' },
              { value: PAY_STATUS.CLOSED, label: '已关闭' },
            ]}
          />
          <Select
            allowClear
            placeholder="到期"
            style={{ width: 140 }}
            value={draft.due}
            onChange={(v) => apply({ due: v })}
            options={[
              { value: 'OVERDUE', label: '已逾期' },
              { value: 'SOON', label: '7 天内到期' },
              { value: 'MONTH', label: '本月到期' },
              { value: 'WAITING', label: '待入库' },
            ]}
          />
          <Select
            allowClear
            placeholder="币种"
            style={{ width: 100 }}
            value={draft.currencyCode}
            onChange={(v) => apply({ currencyCode: v })}
            options={CURRENCIES.map((c) => ({ value: c, label: c }))}
          />
          <Checkbox
            checked={draft.hasCredit}
            onChange={(e) => apply({ hasCredit: e.target.checked })}
          >
            有欠款
          </Checkbox>
          <Checkbox
            checked={draft.showClosed}
            onChange={(e) => apply({ showClosed: e.target.checked })}
          >
            显示已关闭
          </Checkbox>
          <Button onClick={() => apply({})}>查询</Button>
        </div>
        <Table<Payable>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1900 }}
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
        <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
          默认按更新时间倒序；按到期筛选时按到期日排序。已关闭且没有欠款的默认不显示。
        </div>
      </Card>
      <Drawer
        open={detailLoading || !!detail}
        onClose={() => setDetail(undefined)}
        size="min(900px, 96vw)"
        destroyOnHidden
        title={
          detail ? (
            <Space>
              {detail.payableNo}
              <PayStatusPill status={detail.status} />
            </Space>
          ) : (
            '应付单'
          )
        }
        extra={
          detail && canRegister ? (
            <Space>
              {detail.creditAmount > 0 && (
                <Button onClick={() => setRefund(detail)}>登记退款</Button>
              )}
              {(detail.status === PAY_STATUS.UNPAID ||
                detail.status === PAY_STATUS.PARTIAL) && (
                <Button type="primary" onClick={() => setPayAnchor(detail.id)}>
                  登记付款
                </Button>
              )}
            </Space>
          ) : null
        }
      >
        {!detail ? (
          <Spin />
        ) : (
          <>
            <div style={{ marginBottom: 14, color: palette.sub }}>
              {detail.payeeName} <PayeeTypePill type={detail.payeeType} /> ·
              来源{' '}
              <a onClick={() => history.push(sourcePath(detail))}>
                {detail.sourceNo}
              </a>
            </div>
            <PayableBody payable={detail} showVoided />
          </>
        )}
      </Drawer>
      <RegisterPaymentDrawer
        open={payAnchor !== undefined}
        anchorId={typeof payAnchor === 'number' ? payAnchor : undefined}
        onClose={() => setPayAnchor(undefined)}
        onDone={() => {
          load();
          if (detail) openDetail(detail.id);
        }}
      />
      <RefundModal
        payable={refund}
        onClose={() => setRefund(undefined)}
        onDone={() => {
          load();
          if (detail) openDetail(detail.id);
        }}
      />
    </div>
  );
};

export default PayableManagement;
