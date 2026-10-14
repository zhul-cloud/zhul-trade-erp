import { SearchOutlined } from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { App, Button, Checkbox, DatePicker, Input, Select, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { auditColumns } from '@/pages/purchase/components';
import { CURRENCIES } from '@/pages/quotation/components';
import { readBizError } from '@/pages/sales/service';
import { ReasonModal } from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { ProofLinks } from '../payables/components';
import {
  METHOD,
  type Payment,
  type PaymentQuery,
  type PaymentStats,
  paymentApi,
} from '../payables/service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };

interface Filters {
  keyword: string;
  range: [Dayjs, Dayjs] | null;
  method?: number;
  advanceUser: string;
  currencyCode?: string;
  kind?: number;
  showVoid: boolean;
}

const initial = (): Filters => ({
  keyword: '',
  range: [dayjs().startOf('month'), dayjs().endOf('month')],
  advanceUser: '',
  showVoid: false,
});

/** 财务管理 → 付款单：公司对公与员工垫付的付款、供应商退款；登记人或财务可以作废 */
const PaymentRecords: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [draft, setDraft] = useState<Filters>(initial);
  const [filters, setFilters] = useState<Filters>(initial);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<Payment[]>([]);
  const [total, setTotal] = useState(0);
  const [stats, setStats] = useState<PaymentStats>();
  const [loading, setLoading] = useState(true);
  const [voiding, setVoiding] = useState<Payment>();

  const load = useCallback(async () => {
    setLoading(true);
    const q: Omit<PaymentQuery, 'page' | 'pageSize'> = {
      keyword: filters.keyword.trim() || undefined,
      dateFrom: filters.range?.[0].format('YYYY-MM-DD'),
      dateTo: filters.range?.[1].format('YYYY-MM-DD'),
      method: filters.method,
      advanceUser: filters.advanceUser.trim() || undefined,
      currencyCode: filters.currencyCode,
      kind: filters.kind,
      showVoid: filters.showVoid || undefined,
    };
    try {
      const [res, s] = await Promise.all([
        paymentApi.page({ ...q, page, pageSize }),
        paymentApi.stats(q),
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

  const apply = (next: Partial<Filters>) => {
    const f = { ...draft, ...next };
    setDraft(f);
    setFilters(f);
    setPage(1);
  };

  const card = (label: string, value: number, tone: string) => (
    <div
      style={{
        flex: '1 1 200px',
        padding: '16px 18px',
        borderRadius: 14,
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
      }}
    >
      <div style={{ fontSize: 13, color: palette.sub }}>{label}</div>
      <div
        style={{
          ...num,
          fontSize: 22,
          fontWeight: 800,
          color: tone,
          marginTop: 4,
        }}
      >
        {formatAmount(value, 'CNY')}
      </div>
    </div>
  );

  const columns: TableColumnsType<Payment> = [
    {
      title: '编号',
      dataIndex: 'paymentNo',
      width: 150,
      fixed: 'left',
      render: (v: string, r) => (
        <span style={{ textDecoration: r.voided ? 'line-through' : undefined }}>
          {v}
        </span>
      ),
    },
    {
      title: '类型',
      dataIndex: 'kind',
      width: 80,
      render: (v: number) =>
        v === 2 ? (
          <Pill tone="orange">退款</Pill>
        ) : v === 3 ? (
          <Pill tone="cyan">报销付款</Pill>
        ) : (
          <Pill tone="gray">付款</Pill>
        ),
    },
    { title: '日期', dataIndex: 'payDate', width: 110 },
    { title: '收款方', dataIndex: 'payeeName', width: 180, ellipsis: true },
    {
      title: '付款方式',
      key: 'method',
      width: 110,
      render: (_, r) =>
        r.kind === 2 ? (
          '—'
        ) : r.method === METHOD.ADVANCE ? (
          <Pill tone="violet">员工垫付</Pill>
        ) : (
          <Pill tone="accent">公司对公</Pill>
        ),
    },
    {
      title: '账户 / 垫付人',
      key: 'who',
      width: 220,
      ellipsis: true,
      render: (_, r) =>
        (r.method === METHOD.ADVANCE && r.kind === 1
          ? r.advanceUser
          : r.accountName) || '—',
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 130,
      align: 'right',
      render: (v: number, r) => (
        <span style={num}>
          {formatAmount(v, r.currencyCode)}
          {r.creditUsed > 0 && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              另抵扣 {formatAmount(r.creditUsed, r.currencyCode)}
            </div>
          )}
        </span>
      ),
    },
    {
      title: '实付人民币',
      dataIndex: 'amountCny',
      width: 130,
      align: 'right',
      render: (v: number, r) => (
        <span style={num}>
          {formatAmount(v, 'CNY')}
          {r.currencyCode !== 'CNY' && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              汇率 {r.actualRate}
            </div>
          )}
        </span>
      ),
    },
    {
      title: '核销的应付单',
      key: 'alloc',
      width: 220,
      render: (_, r) => (
        <div style={{ fontSize: 12 }}>
          {r.allocations.map((a) => (
            <div
              key={`${a.payableId}-${a.fromCredit}-${a.creditPayableNo ?? ''}`}
            >
              {a.payableNo}{' '}
              <span style={{ color: palette.mute }}>{a.sourceNo}</span>{' '}
              {formatAmount(a.amount, r.currencyCode)}
              {a.fromCredit === 1 && (
                <span style={{ color: palette.orange }}> 抵扣</span>
              )}
            </div>
          ))}
        </div>
      ),
    },
    {
      title: '截图',
      key: 'files',
      width: 90,
      render: (_, r) => <ProofLinks files={r.attachments} />,
    },
    {
      title: '状态',
      key: 'status',
      width: 140,
      render: (_, r) =>
        r.voided ? (
          <div>
            <Pill tone="mute">已作废</Pill>
            <div style={{ fontSize: 12, color: palette.mute }}>
              {r.voidReason}
            </div>
          </div>
        ) : (
          <Pill tone="green">有效</Pill>
        ),
    },
    ...auditColumns<Payment>(),
    {
      title: '操作',
      key: 'op',
      width: 80,
      fixed: 'right',
      render: (_, r) =>
        r.canVoid ? (
          <a onClick={() => setVoiding(r)}>作废</a>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
  ];

  return (
    <div>
      <PageTitle
        crumbs={['付款单']}
        title="付款单"
        description="公司对公由财务登记，员工垫付由付款的员工本人登记（进入待报销）；供应商退款也在这里。登记错了由登记人或财务作废，应付随之恢复。"
      />
      <div
        style={{ display: 'flex', gap: 14, flexWrap: 'wrap', marginBottom: 16 }}
      >
        {card('付款合计（人民币）', stats?.total ?? 0, palette.ink)}
        {card('公司对公', stats?.company ?? 0, palette.link)}
        {card('员工垫付', stats?.advance ?? 0, palette.orange)}
        {card('报销付款（不计入合计）', stats?.reimburse ?? 0, palette.sub)}
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
            placeholder="编号、收款方"
            style={{ width: 200 }}
            value={draft.keyword}
            onChange={(e) => setDraft({ ...draft, keyword: e.target.value })}
            onPressEnter={() => apply({})}
          />
          <DatePicker.RangePicker
            value={draft.range}
            onChange={(v) =>
              apply({ range: v?.[0] && v[1] ? [v[0], v[1]] : null })
            }
          />
          <Select
            allowClear
            placeholder="付款方式"
            style={{ width: 120 }}
            value={draft.method}
            onChange={(v) => apply({ method: v })}
            options={[
              { value: METHOD.COMPANY, label: '公司对公' },
              { value: METHOD.ADVANCE, label: '员工垫付' },
            ]}
          />
          <Input
            allowClear
            placeholder="垫付人账号"
            style={{ width: 140 }}
            value={draft.advanceUser}
            onChange={(e) =>
              setDraft({ ...draft, advanceUser: e.target.value })
            }
            onPressEnter={() => apply({})}
          />
          <Select
            allowClear
            placeholder="类型"
            style={{ width: 100 }}
            value={draft.kind}
            onChange={(v) => apply({ kind: v })}
            options={[
              { value: 1, label: '付款' },
              { value: 2, label: '退款' },
              { value: 3, label: '报销付款' },
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
            checked={draft.showVoid}
            onChange={(e) => apply({ showVoid: e.target.checked })}
          >
            显示已作废
          </Checkbox>
          <Button onClick={() => apply({})}>查询</Button>
        </div>
        <Table<Payment>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 2200 }}
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
      </Card>
      <ReasonModal
        open={!!voiding}
        title={`作废 ${voiding?.paymentNo ?? ''}`}
        description="作废后这笔付款的核销与欠款抵扣全部撤回，相关应付单恢复。"
        okText="作废"
        danger
        onCancel={() => setVoiding(undefined)}
        onOk={async (reason) => {
          if (!voiding) return;
          await paymentApi.voidPayment(voiding.id, reason);
          message.success('已作废');
          setVoiding(undefined);
          load();
        }}
      />
    </div>
  );
};

export default PaymentRecords;
