import { SearchOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Checkbox, DatePicker, Input, Select, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { CURRENCIES } from '@/pages/quotation/components';
import { Card, PATHS, Pill } from '@/pages/sales/components';
import { type ReceiptRow, readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { usePaymentMethods } from '@/utils/dict';
import { formatAmount } from '@/utils/format';
import { ledgerApi, type ReceiptMoney, type ReceiptSummary } from './service';
import { MethodCell, NetCnyCell } from './UnclaimedTab';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const moneyText = (list: ReceiptMoney[]) =>
  list.length
    ? list.map((m) => formatAmount(m.amount, m.currencyCode)).join('、')
    : '—';

interface Filters {
  keyword: string;
  range: [Dayjs, Dayjs] | null;
  paymentMethod?: string;
  channel?: number;
  currencyCode?: string;
  includeVoid: boolean;
}

const initial = (): Filters => ({
  keyword: '',
  range: [dayjs().startOf('month'), dayjs().endOf('month')],
  includeVoid: false,
});

/** 收款管理 → 收款记录：全部到账流水，按筛选结果汇总线上 / 线下（报税统计用） */
const RecordsTab: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const methods = usePaymentMethods();
  const [draft, setDraft] = useState<Filters>(initial);
  const [filters, setFilters] = useState<Filters>(initial);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<ReceiptRow[]>([]);
  const [total, setTotal] = useState(0);
  const [summary, setSummary] = useState<ReceiptSummary[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await ledgerApi.records({
        keyword: filters.keyword.trim() || undefined,
        dateFrom: filters.range?.[0].format('YYYY-MM-DD'),
        dateTo: filters.range?.[1].format('YYYY-MM-DD'),
        paymentMethod: filters.paymentMethod,
        channel: filters.channel,
        currencyCode: filters.currencyCode,
        includeVoid: filters.includeVoid || undefined,
        page,
        pageSize,
      });
      setRows(res.records);
      setTotal(res.total);
      setSummary(res.summary);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [filters, page, pageSize, message]);

  useEffect(() => {
    load();
  }, [load]);

  const card = (s: ReceiptSummary) => {
    const tone =
      s.channel === 2 ? 'violet' : s.channel === 1 ? 'gray' : 'accent';
    const name = s.channel === 2 ? '线上' : s.channel === 1 ? '线下' : '合计';
    return (
      <div
        key={s.channel}
        style={{
          padding: '16px 18px',
          borderRadius: 14,
          background: palette.card,
          border: `1px solid ${palette.hairline}`,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Pill tone={tone}>{name}</Pill>
          <span style={{ color: palette.sub, fontSize: 13 }}>{s.count} 笔</span>
        </div>
        <div
          style={{
            ...num,
            fontSize: 22,
            fontWeight: 800,
            color: palette.ink,
            marginTop: 6,
          }}
        >
          {formatAmount(s.netAmountCny, 'CNY')}
        </div>
        <div style={{ fontSize: 12, color: palette.mute }}>实收人民币</div>
        <div style={{ fontSize: 12, color: palette.sub, marginTop: 4 }}>
          毛额 {moneyText(s.amounts)}
          {s.fees.length ? ` · 手续费 ${moneyText(s.fees)}` : ''}
        </div>
      </div>
    );
  };

  const columns: TableColumnsType<ReceiptRow> = [
    { title: '到账日期', dataIndex: 'receiptDate', width: 110 },
    {
      title: 'PI / 订单编号',
      key: 'pi',
      width: 180,
      render: (_, r) =>
        r.piId || r.soId ? (
          <a
            style={{ whiteSpace: 'nowrap' }}
            onClick={() =>
              history.push(
                r.piId ? PATHS.pi(r.piId) : PATHS.order(r.soId as number),
              )
            }
          >
            {r.piNo ?? r.soNo}
          </a>
        ) : (
          '—'
        ),
    },
    { title: '客户', dataIndex: 'customerName', ellipsis: true },
    {
      title: '付款方式',
      key: 'method',
      width: 190,
      render: (_, r) => (
        <div>
          <MethodCell r={r} />
          {r.platformOrderNo && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              订单号 {r.platformOrderNo}
            </div>
          )}
        </div>
      ),
    },
    {
      title: '毛额',
      key: 'amount',
      width: 120,
      align: 'right',
      render: (_, r) => (
        <span
          style={{
            ...num,
            textDecoration: r.status === 2 ? 'line-through' : undefined,
          }}
        >
          {formatAmount(r.amount, r.currencyCode)}
        </span>
      ),
    },
    {
      title: '手续费',
      key: 'fee',
      width: 130,
      render: (_, r) =>
        r.platformFee > 0 ? (
          <span style={{ color: palette.orange }}>
            平台 {formatAmount(r.platformFee, r.currencyCode)}
          </span>
        ) : r.feeDiff > 0 ? (
          <span style={{ color: palette.orange }}>
            中转 {formatAmount(r.feeDiff, r.currencyCode)}
          </span>
        ) : (
          '—'
        ),
    },
    {
      title: '实收',
      key: 'net',
      width: 120,
      align: 'right',
      render: (_, r) => (
        <span style={num}>{formatAmount(r.netAmount, r.currencyCode)}</span>
      ),
    },
    {
      title: '实收人民币',
      key: 'cny',
      width: 170,
      render: (_, r) => <NetCnyCell r={r} />,
    },
    {
      title: '登记人',
      key: 'op',
      width: 150,
      render: (_, r) => (
        <div>
          {r.operatorName}
          {r.claimedByName && (
            <div style={{ fontSize: 12, color: palette.mute }}>
              {r.claimedByName} 认领
            </div>
          )}
          {r.status === 2 && <Pill tone="mute">已作废</Pill>}
        </div>
      ),
    },
  ];

  return (
    <div>
      <Card style={{ marginBottom: 16, padding: 16 }}>
        <div
          style={{
            display: 'flex',
            gap: 12,
            alignItems: 'center',
            flexWrap: 'wrap',
          }}
        >
          <Input
            allowClear
            style={{ width: 280 }}
            prefix={<SearchOutlined />}
            placeholder="PI / 订单编号、客户、平台订单号、付款人"
            value={draft.keyword}
            onChange={(e) => setDraft({ ...draft, keyword: e.target.value })}
          />
          <DatePicker.RangePicker
            value={draft.range}
            onChange={(v) =>
              setDraft({ ...draft, range: v as [Dayjs, Dayjs] | null })
            }
            aria-label="到账日期"
          />
          <Select
            allowClear
            style={{ width: 160 }}
            placeholder="全部付款方式"
            value={draft.paymentMethod}
            onChange={(v) => setDraft({ ...draft, paymentMethod: v })}
            options={methods.map((m) => ({ value: m.code, label: m.name }))}
          />
          <Select
            allowClear
            style={{ width: 130 }}
            placeholder="线上 / 线下"
            value={draft.channel}
            onChange={(v) => setDraft({ ...draft, channel: v })}
            options={[
              { value: 2, label: '线上' },
              { value: 1, label: '线下' },
            ]}
          />
          <Select
            allowClear
            style={{ width: 120 }}
            placeholder="全部币种"
            value={draft.currencyCode}
            onChange={(v) => setDraft({ ...draft, currencyCode: v })}
            options={CURRENCIES.map((c) => ({ value: c, label: c }))}
          />
          <Checkbox
            checked={draft.includeVoid}
            onChange={(e) =>
              setDraft({ ...draft, includeVoid: e.target.checked })
            }
          >
            含已作废
          </Checkbox>
          <span style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
            <Button
              onClick={() => {
                setDraft(initial());
                setFilters(initial());
                setPage(1);
              }}
            >
              重置
            </Button>
            <Button
              type="primary"
              onClick={() => {
                setFilters(draft);
                setPage(1);
              }}
            >
              查询
            </Button>
          </span>
        </div>
      </Card>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
          gap: 16,
          marginBottom: 16,
        }}
      >
        {summary.map(card)}
      </div>
      <Table<ReceiptRow>
        rowKey="id"
        columns={columns}
        dataSource={rows}
        loading={loading}
        scroll={{ x: 1460 }}
        locale={{ emptyText: '没有符合条件的收款记录' }}
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
      <div style={{ marginTop: 12, fontSize: 12, color: palette.mute }}>
        汇总按当前筛选条件计算（只算有效到账），和列表逐笔相加一致；未认领的到账不在这里，见「未认领到账」。
      </div>
    </div>
  );
};

export default RecordsTab;
