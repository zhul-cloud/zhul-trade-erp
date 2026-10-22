import { PlusOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  Checkbox,
  DatePicker,
  Drawer,
  Empty,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { auditColumns } from '@/pages/purchase/components';
import {
  type BankOption,
  bankOptionsApi,
  type ReceiptRow,
  readBizError,
} from '@/pages/sales/service';
import { ReasonModal } from '@/pages/warehouse/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { type FxPending, type FxSettlement, fxApi } from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const round2 = (v: number) => Math.round((v + Number.EPSILON) * 100) / 100;
const newKey = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
const unsettled = (r: ReceiptRow) =>
  round2(Number(r.netAmount) - Number(r.settledAmount ?? 0));

const SettlePill: React.FC<{ status?: number }> = ({ status }) =>
  status === 3 ? (
    <Pill tone="green">已结汇</Pill>
  ) : status === 2 ? (
    <Pill tone="orange">部分结汇</Pill>
  ) : status === 1 ? (
    <Pill tone="orange">未结汇</Pill>
  ) : null;

/** 到账的 PI 与客户：未认领的显示付款人 */
const ReceiptWho: React.FC<{ r: ReceiptRow }> = ({ r }) => {
  const { palette } = useAppTheme();
  return (
    <div>
      {r.piNo ? (
        <span style={{ color: palette.link }}>{r.piNo}</span>
      ) : (
        <span style={{ color: palette.mute }}>未认领</span>
      )}
      <div style={{ fontSize: 12, color: palette.sub }}>
        {r.customerName ?? r.payer ?? ''}
      </div>
    </div>
  );
};

/** 登记结汇单：照银行结汇流水填写，按先进先出预览分摊 */
const RegisterModal: React.FC<{
  open: boolean;
  pending: FxPending[];
  onClose: () => void;
  onDone: () => void;
}> = ({ open, pending, onClose, onDone }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [date, setDate] = useState<Dayjs>(dayjs());
  const [currency, setCurrency] = useState<string>();
  const [amount, setAmount] = useState<number | null>(null);
  const [cny, setCny] = useState<number | null>(null);
  const [accounts, setAccounts] = useState<BankOption[]>([]);
  const [accountId, setAccountId] = useState<number>();
  const [remark, setRemark] = useState('');
  const [key, setKey] = useState(newKey);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setDate(dayjs());
    setCurrency(pending[0]?.currencyCode);
    setAmount(null);
    setCny(null);
    setRemark('');
    setKey(newKey());
    bankOptionsApi
      .options()
      .then((list) => {
        const ok = list.filter((a) => a.currencyCode === 'CNY');
        setAccounts(ok);
        setAccountId((ok.find((a) => a.isDefault) ?? ok[0])?.id);
      })
      .catch(() => setAccounts([]));
  }, [open, pending]);

  const pool = pending.find((p) => p.currencyCode === currency);
  const rate = amount && cny ? cny / amount : undefined;
  const preview = useMemo(() => {
    let left = amount ?? 0;
    return (pool?.receipts ?? []).map((r) => {
      const take = Math.min(left, unsettled(r));
      left = round2(left - take);
      const after =
        take <= 0 ? r.settleStatus : round2(take) >= unsettled(r) ? 3 : 2;
      return { r, take: round2(take), after };
    });
  }, [pool, amount]);

  const submit = async () => {
    if (!currency || !amount || !cny || !accountId) {
      message.error('请填写币种、结汇金额、实际入账人民币与入账账户');
      return;
    }
    setSaving(true);
    try {
      const s = await fxApi.create({
        settleDate: date.format('YYYY-MM-DD'),
        currencyCode: currency,
        amount,
        amountCny: cny,
        bankAccountId: accountId,
        remark: remark.trim() || undefined,
        idempotencyKey: key,
      });
      message.success(`已登记结汇单 ${s.settleNo}`);
      onDone();
      onClose();
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setSaving(false);
    }
  };

  const label = (t: string) => (
    <div style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}>
      <span style={{ color: palette.red }}>* </span>
      {t}
    </div>
  );

  return (
    <Modal
      open={open}
      title="登记结汇单"
      width={900}
      okText="确认结汇"
      cancelText="取消"
      footer={pending.length === 0 ? null : undefined}
      confirmLoading={saving}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
    >
      {pending.length === 0 ? (
        <Empty description="没有待结汇的外币到账" />
      ) : (
        <Space orientation="vertical" size={14} style={{ width: '100%' }}>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
              gap: 12,
            }}
          >
            <div>
              {label('结汇日期')}
              <DatePicker
                value={date}
                allowClear={false}
                disabledDate={(d) => d.isAfter(dayjs(), 'day')}
                onChange={(d) => d && setDate(d)}
                style={{ width: '100%' }}
              />
            </div>
            <div>
              {label('币种')}
              <Select
                value={currency}
                onChange={setCurrency}
                style={{ width: '100%' }}
                options={pending.map((p) => ({
                  value: p.currencyCode,
                  label: p.currencyCode,
                }))}
              />
            </div>
            <div>
              {label('结汇金额')}
              <InputNumber<number>
                min={0.01}
                max={pool?.balance}
                precision={2}
                value={amount}
                onChange={setAmount}
                prefix={currency}
                style={{ width: '100%' }}
                aria-label="结汇金额"
              />
            </div>
            <div>
              {label('实际入账人民币')}
              <InputNumber<number>
                min={0.01}
                precision={2}
                value={cny}
                onChange={setCny}
                prefix="CNY"
                style={{ width: '100%' }}
                aria-label="实际入账人民币"
              />
            </div>
          </div>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <div style={{ flex: '1 1 320px' }}>
              {label('入账账户')}
              <Select
                value={accountId}
                onChange={setAccountId}
                style={{ width: '100%' }}
                options={accounts.map((a) => ({
                  value: a.id,
                  label: `${a.bankName} · ${a.accountName} ${a.accountNoMasked}${a.accountType === 2 ? '（对私）' : ''}`,
                }))}
              />
            </div>
            <div style={{ flex: '1 1 320px' }}>
              <div
                style={{ fontSize: 12, color: palette.sub, marginBottom: 6 }}
              >
                说明
              </div>
              <Input
                value={remark}
                maxLength={500}
                placeholder="如 10 月第 2 批"
                onChange={(e) => setRemark(e.target.value)}
              />
            </div>
          </div>
          <div style={{ color: palette.sub }}>
            结汇汇率{' '}
            <b style={{ ...num, fontSize: 16, color: palette.ink }}>
              {rate ? rate.toFixed(6) : '—'}
            </b>
            <span style={{ marginLeft: 12 }}>
              {currency} 待结汇余额{' '}
              {formatAmount(pool?.balance ?? 0, currency ?? 'USD')}
              {amount
                ? ` → 结后 ${formatAmount(round2((pool?.balance ?? 0) - amount), currency ?? 'USD')}`
                : ''}
            </span>
          </div>
          <div style={{ fontSize: 12, color: palette.mute }}>
            按到账日期先进先出分摊，一笔到账可以分次结完；还没结完的按这次的汇率估算。
          </div>
          <Table
            rowKey={(x) => x.r.id}
            size="small"
            pagination={false}
            dataSource={preview}
            scroll={{ y: 280 }}
            columns={[
              {
                title: '到账日期',
                key: 'd',
                width: 100,
                render: (_, x) => x.r.receiptDate,
              },
              {
                title: 'PI',
                key: 'pi',
                width: 180,
                render: (_, x) => <ReceiptWho r={x.r} />,
              },
              {
                title: '待结汇',
                key: 'left',
                width: 130,
                align: 'right',
                render: (_, x) =>
                  formatAmount(unsettled(x.r), x.r.currencyCode),
              },
              {
                title: '本次结汇',
                key: 'take',
                width: 130,
                align: 'right',
                render: (_, x) =>
                  x.take > 0 ? (
                    <b style={num}>{formatAmount(x.take, x.r.currencyCode)}</b>
                  ) : (
                    '—'
                  ),
              },
              {
                title: '结后',
                key: 'after',
                width: 100,
                render: (_, x) => <SettlePill status={x.after} />,
              },
            ]}
          />
        </Space>
      )}
    </Modal>
  );
};

/** 财务管理 → 结汇单：外币到账攒一批结汇，按先进先出分摊到每笔到账 */
const FxSettlementPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canSettle = !!access['finance:fx:settle'];
  const [tab, setTab] = useState('list');
  const [pending, setPending] = useState<FxPending[]>([]);
  const [rows, setRows] = useState<FxSettlement[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [currency, setCurrency] = useState<string>();
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [includeVoid, setIncludeVoid] = useState(false);
  const [loading, setLoading] = useState(true);
  const [registering, setRegistering] = useState(false);
  const [detail, setDetail] = useState<FxSettlement>();
  const [detailLoading, setDetailLoading] = useState(false);
  const [voiding, setVoiding] = useState<FxSettlement>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [res, p] = await Promise.all([
        fxApi.page({
          page,
          pageSize,
          currencyCode: currency,
          dateFrom: range?.[0].format('YYYY-MM-DD'),
          dateTo: range?.[1].format('YYYY-MM-DD'),
          includeVoid: includeVoid || undefined,
        }),
        fxApi.pending(),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setPending(p);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, currency, range, includeVoid, message]);

  useEffect(() => {
    load();
  }, [load]);

  const open = async (id: number) => {
    setDetailLoading(true);
    setDetail(undefined);
    try {
      setDetail(await fxApi.detail(id));
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setDetailLoading(false);
    }
  };

  const pendingCount = pending.reduce((s, p) => s + p.receipts.length, 0);

  const columns: TableColumnsType<FxSettlement> = [
    {
      title: '编号',
      dataIndex: 'settleNo',
      width: 150,
      fixed: 'left',
      render: (v: string, r) => (
        <a
          onClick={() => open(r.id)}
          style={{
            textDecoration: r.status === 2 ? 'line-through' : undefined,
          }}
        >
          {v}
        </a>
      ),
    },
    { title: '结汇日期', dataIndex: 'settleDate', width: 110 },
    { title: '币种', dataIndex: 'currencyCode', width: 70 },
    {
      title: '结汇金额',
      dataIndex: 'amount',
      width: 130,
      align: 'right',
      render: (v: number, r) => (
        <span style={num}>{formatAmount(v, r.currencyCode)}</span>
      ),
    },
    {
      title: '入账人民币',
      dataIndex: 'amountCny',
      width: 130,
      align: 'right',
      render: (v: number) => <b style={num}>{formatAmount(v, 'CNY')}</b>,
    },
    {
      title: '汇率',
      dataIndex: 'exchangeRate',
      width: 100,
      align: 'right',
      render: (v: number) => <span style={num}>{Number(v).toFixed(6)}</span>,
    },
    {
      title: '入账账户',
      dataIndex: 'bankAccountName',
      width: 180,
      ellipsis: true,
    },
    { title: '分摊笔数', dataIndex: 'allocationCount', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (v: number, r) =>
        v === 2 ? (
          <div>
            <Pill tone="mute">已作废</Pill>
            <div style={{ fontSize: 12, color: palette.mute }}>
              {r.voidReason}
            </div>
          </div>
        ) : r.migrated ? (
          <Pill tone="gray">迁移</Pill>
        ) : (
          <Pill tone="green">有效</Pill>
        ),
    },
    ...auditColumns<FxSettlement>(),
    {
      title: '操作',
      key: 'op',
      width: 90,
      fixed: 'right',
      render: (_, r) =>
        canSettle && r.status === 1 && r.latest ? (
          <a onClick={() => setVoiding(r)}>作废</a>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
  ];

  const pendingColumns: TableColumnsType<ReceiptRow> = [
    { title: '到账日期', dataIndex: 'receiptDate', width: 110 },
    {
      title: 'PI · 客户',
      key: 'pi',
      width: 220,
      render: (_, r) => <ReceiptWho r={r} />,
    },
    { title: '付款方式', dataIndex: 'paymentMethodName', width: 110 },
    {
      title: '实收',
      dataIndex: 'netAmount',
      width: 130,
      align: 'right',
      render: (v: number, r) => formatAmount(v, r.currencyCode),
    },
    {
      title: '已结汇',
      dataIndex: 'settledAmount',
      width: 120,
      align: 'right',
      render: (v: number | undefined, r) =>
        v ? (
          formatAmount(v, r.currencyCode)
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '待结汇',
      key: 'left',
      width: 130,
      align: 'right',
      render: (_, r) => (
        <b style={{ ...num, color: palette.orange }}>
          {formatAmount(unsettled(r), r.currencyCode)}
        </b>
      ),
    },
    {
      title: '估算人民币',
      dataIndex: 'netAmountCny',
      width: 130,
      align: 'right',
      render: (v: number) => (
        <span style={{ color: palette.sub }}>≈ {formatAmount(v, 'CNY')}</span>
      ),
    },
  ];

  const stat = (
    label: string,
    value: React.ReactNode,
    sub?: string,
    color?: string,
  ) => (
    <div
      style={{
        flex: '1 1 220px',
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
          color: color ?? palette.ink,
          marginTop: 4,
        }}
      >
        {value}
      </div>
      {sub && (
        <div style={{ fontSize: 12, color: palette.mute, marginTop: 2 }}>
          {sub}
        </div>
      )}
    </div>
  );

  return (
    <div>
      <PageTitle
        description="外币到账攒一批结汇；按到账日期先进先出分摊到每笔到账，还没结汇的按最近一次结汇汇率估算人民币。"
        actions={
          canSettle && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setRegistering(true)}
            >
              登记结汇单
            </Button>
          )
        }
      />
      <Tabs
        activeKey={tab}
        onChange={setTab}
        items={[
          {
            key: 'list',
            label: '结汇单列表',
            children: (
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
                  <Select
                    allowClear
                    placeholder="币种"
                    style={{ width: 110 }}
                    value={currency}
                    onChange={(v) => {
                      setCurrency(v);
                      setPage(1);
                    }}
                    options={['USD', 'EUR', 'GBP', 'RUB'].map((c) => ({
                      value: c,
                      label: c,
                    }))}
                  />
                  <DatePicker.RangePicker
                    value={range}
                    onChange={(v) => {
                      setRange(v?.[0] && v[1] ? [v[0], v[1]] : null);
                      setPage(1);
                    }}
                  />
                  <Checkbox
                    checked={includeVoid}
                    onChange={(e) => setIncludeVoid(e.target.checked)}
                  >
                    显示已作废
                  </Checkbox>
                </div>
                <Table<FxSettlement>
                  rowKey="id"
                  loading={loading}
                  columns={columns}
                  dataSource={rows}
                  scroll={{ x: 1800 }}
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
            ),
          },
          {
            key: 'pending',
            label: `待结汇${pendingCount ? ` ${pendingCount}` : ''}`,
            children:
              pending.length === 0 ? (
                <Card>
                  <Empty description="外币到账都已结汇" />
                </Card>
              ) : (
                <Space
                  orientation="vertical"
                  size={16}
                  style={{ width: '100%' }}
                >
                  {pending.map((p) => (
                    <div key={p.currencyCode}>
                      <div
                        style={{
                          display: 'flex',
                          gap: 14,
                          flexWrap: 'wrap',
                          marginBottom: 14,
                        }}
                      >
                        {stat(
                          `${p.currencyCode} 待结汇余额`,
                          formatAmount(p.balance, p.currencyCode),
                          `${p.receipts.length} 笔`,
                          palette.orange,
                        )}
                        {stat(
                          '估算汇率',
                          Number(p.estimateRate).toFixed(6),
                          p.latestSettleNo
                            ? `最近一次结汇 ${p.latestSettleDate} ${p.latestSettleNo}`
                            : '还没有结汇单，按系统汇率',
                        )}
                        {stat(
                          '估算人民币',
                          `≈ ${formatAmount(p.estimateCny, 'CNY')}`,
                        )}
                      </div>
                      <Card>
                        <div style={{ marginBottom: 10 }}>
                          <b style={{ color: palette.ink }}>待结汇的到账</b>
                          <span
                            style={{
                              marginLeft: 10,
                              fontSize: 12,
                              color: palette.mute,
                            }}
                          >
                            按先进先出顺序，下一次结汇从最上面开始
                          </span>
                        </div>
                        <Table<ReceiptRow>
                          rowKey="id"
                          size="middle"
                          columns={pendingColumns}
                          dataSource={p.receipts}
                          pagination={false}
                          scroll={{ x: 950 }}
                        />
                      </Card>
                    </div>
                  ))}
                </Space>
              ),
          },
        ]}
      />
      <RegisterModal
        open={registering}
        pending={pending}
        onClose={() => setRegistering(false)}
        onDone={load}
      />
      <Drawer
        open={detailLoading || !!detail}
        onClose={() => setDetail(undefined)}
        size="min(860px, 96vw)"
        destroyOnHidden
        title={detail ? `结汇单 ${detail.settleNo}` : '结汇单'}
      >
        {!detail ? (
          <Spin />
        ) : (
          <Space orientation="vertical" size={16} style={{ width: '100%' }}>
            <div style={{ color: palette.sub }}>
              {detail.settleDate} ·{' '}
              {formatAmount(detail.amount, detail.currencyCode)} →{' '}
              <b style={{ color: palette.ink }}>
                {formatAmount(detail.amountCny, 'CNY')}
              </b>{' '}
              · 汇率 {Number(detail.exchangeRate).toFixed(6)} ·{' '}
              {detail.bankAccountName}
              {detail.remark ? ` · ${detail.remark}` : ''}
            </div>
            {detail.status === 2 && (
              <div style={{ color: palette.mute }}>
                已作废：{detail.voidReason}（{detail.voidedBy}）
              </div>
            )}
            <Table
              rowKey={(a) => a.receipt?.id ?? Math.random()}
              size="small"
              pagination={false}
              dataSource={detail.allocations ?? []}
              locale={{ emptyText: '作废后分摊已撤回' }}
              columns={[
                {
                  title: '到账日期',
                  key: 'd',
                  width: 100,
                  render: (_, a) => a.receipt?.receiptDate,
                },
                {
                  title: 'PI · 客户',
                  key: 'pi',
                  render: (_, a) =>
                    a.receipt ? <ReceiptWho r={a.receipt} /> : '—',
                },
                {
                  title: '分摊外币',
                  dataIndex: 'amount',
                  width: 130,
                  align: 'right',
                  render: (v: number) => formatAmount(v, detail.currencyCode),
                },
                {
                  title: '分摊人民币',
                  dataIndex: 'amountCny',
                  width: 130,
                  align: 'right',
                  render: (v: number) => (
                    <b style={num}>{formatAmount(v, 'CNY')}</b>
                  ),
                },
                {
                  title: '结汇状态',
                  key: 's',
                  width: 100,
                  render: (_, a) => (
                    <SettlePill status={a.receipt?.settleStatus} />
                  ),
                },
              ]}
            />
          </Space>
        )}
      </Drawer>
      <ReasonModal
        open={!!voiding}
        title={`作废 ${voiding?.settleNo ?? ''}`}
        description="撤回它的全部分摊，相关到账回到未结汇或部分结汇，未结完的到账按上一张结汇单的汇率重新估算。只能作废最新的一张。"
        okText="作废"
        danger
        onCancel={() => setVoiding(undefined)}
        onOk={async (reason) => {
          if (!voiding) return;
          await fxApi.voidSettlement(voiding.id, reason);
          message.success('已作废');
          setVoiding(undefined);
          load();
        }}
      />
    </div>
  );
};

export default FxSettlementPage;
