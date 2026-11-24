import { history, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  DatePicker,
  Form,
  Input,
  Select,
  Table,
  Tabs,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  CircleCheck,
  Download,
  HandCoins,
  Pencil,
  ScrollText,
  Search,
  TriangleAlert,
} from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerSubLine } from '@/components/DocFields';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  HelpTip,
  MainCard,
  PageHeader,
  QuickFilters,
  SummaryBar,
  type SummaryItem,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { Pill } from '@/pages/inquiry/shared/components';
import { EmptyHint } from '@/pages/product/components/EmptyHint';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { useBusinessUnits } from '@/utils/businessUnit';
import { formatAmount } from '@/utils/format';
import { liveRequests } from '@/utils/liveRequests';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { AdjustModal, WithdrawModal } from './CreditModals';
import EntriesDrawer from './EntriesDrawer';
import {
  type CreditAccount,
  type CreditSummary,
  creditApi,
  type Pair,
  type Recon,
  type ReconRow,
} from './service';

const num: React.CSSProperties = { fontVariantNumeric: 'tabular-nums' };
const CURRENCIES = ['USD', 'EUR', 'GBP', 'RUB', 'CNY'];

/** 帮助文字：help-texts.md「余额管理」，关键词范围以 search-scope.md 为准 */
export const HELP = {
  page: '客户暂存在公司的钱：售后退款转的余额、代收款、客户多付的部分。可以抵扣下一张 PI，也可以转给客户',
  keyword: '可搜：客户显示名（不区分大小写）',
  occupied: '还没转订单的 PI 上的抵扣，客户付款前不能再用',
  monthWithdraw:
    '本月退款日期的余额提现退款单；汇兑损益为本月登记的「汇兑损益」其他收入合计，可以为负',
  recon:
    '「抵扣」= 本月占用 − 本月释放。默认月份为上月；导出的 Excel 与页面一致，用于月末和银行流水、支付宝转账记录核对',
} as const;

/** 原统计卡片改为汇总行（stat-cards.md），口径不变：可用余额取前两个币种 */
const summaryItems = (s: CreditSummary): SummaryItem[] => [
  ...(s.currencies.length
    ? s.currencies
    : [{ currencyCode: 'USD', amount: 0, amountCny: 0, count: 0 }]
  )
    .slice(0, 2)
    .map((c) => ({
      key: `cur-${c.currencyCode}`,
      label: `${c.currencyCode} 可用余额`,
      value: c.amount,
      currency: c.currencyCode,
      sub: `${c.count} 个客户 · 价值 ${formatAmount(c.amountCny, 'CNY')}`,
    })),
  {
    key: 'occupied',
    label: '已被 PI 占用',
    help: HELP.occupied,
    value: s.occupied.length
      ? s.occupied
          .map((o) => formatAmount(o.amount, o.currencyCode))
          .join(' · ')
      : '—',
    sub: s.occupied.length
      ? `${s.occupied.reduce((n, o) => n + o.count, 0)} 张 PI`
      : undefined,
  },
  {
    key: 'monthWithdraw',
    label: '本月转给客户',
    help: HELP.monthWithdraw,
    value: s.monthWithdrawCny,
    currency: 'CNY',
    sub: `${s.monthWithdrawCount} 笔 · 汇兑损益 ${s.monthFxGain >= 0 ? '+' : ''}${formatAmount(s.monthFxGain, 'CNY')}`,
  },
];

/** 财务管理 → 余额管理（spec finance/customer-credit） */
const CreditPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const canAdjust = !!access['finance:credit:adjust'];
  const canWithdraw = !!access['finance:refund:edit'];
  const [bu] = useBusinessUnits();
  const [tab, setTab] = useState<'accounts' | 'recon'>('accounts');
  const [summary, setSummary] = useState<CreditSummary>();
  const [query, setQuery] = useState('');
  const [currency, setCurrency] = useState<string>();
  const [unitId, setUnitId] = useState<number>();
  const [showAll, setShowAll] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<CreditAccount[] | null>(null);
  const [total, setTotal] = useState(0);
  const [viewing, setViewing] = useState<CreditAccount>();
  const [withdrawing, setWithdrawing] = useState<CreditAccount>();
  const [adjusting, setAdjusting] = useState<{ account?: CreditAccount }>();
  const [version, setVersion] = useState(0);
  const [form] = Form.useForm<{
    keyword?: string;
    currency?: string;
    unitId?: number;
  }>();

  const load = useCallback(async () => {
    try {
      const [res, s] = await Promise.all([
        creditApi.page({
          page,
          pageSize,
          keyword: query || undefined,
          currencyCode: currency,
          businessUnitId: unitId,
          showAll: showAll || undefined,
        }),
        creditApi.summary(),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setSummary(s);
    } catch (e) {
      message.error(readBizError(e).message);
      setRows([]);
    }
  }, [page, pageSize, query, currency, unitId, showAll, message]);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  const refresh = () => {
    load();
    setVersion((v) => v + 1);
    // 抽屉里的账户数字随列表刷新
    if (viewing) {
      creditApi
        .ofCustomer(viewing.customerId)
        .then((list) =>
          setViewing(
            list.find(
              (a) =>
                a.currencyCode === viewing.currencyCode &&
                a.businessUnitId === viewing.businessUnitId,
            ) ?? viewing,
          ),
        )
        .catch(() => undefined);
    }
  };

  const columns: TableColumnsType<CreditAccount> = [
    {
      title: '客户',
      key: 'customer',
      width: 240,
      render: (_, a) => (
        <div>
          <span style={{ fontWeight: 600, color: palette.ink }}>
            {a.customerName}
          </span>
          {a.collection && (
            <span style={{ marginLeft: 6 }}>
              <Pill tone="gray">代收</Pill>
            </span>
          )}
          <CustomerSubLine
            country={a.customerCountry}
            contact={a.customerContact}
            extra={a.currencyCode}
            style={{ color: palette.mute, fontSize: 12, lineHeight: '18px' }}
          />
        </div>
      ),
    },
    ...(bu.enabled
      ? [
          {
            title: '事业部',
            dataIndex: 'businessUnitName',
            width: 140,
          } as const,
        ]
      : []),
    {
      title: '可用余额',
      dataIndex: 'available',
      width: 140,
      align: 'right',
      render: (v: number, a) => (
        <b style={{ ...num, color: v > 0 ? palette.green : palette.mute }}>
          {formatAmount(v, a.currencyCode)}
        </b>
      ),
    },
    {
      title: 'PI 占用中',
      key: 'occupied',
      width: 180,
      align: 'right',
      render: (_, a) =>
        a.occupied > 0 ? (
          <div>
            <span style={{ ...num, color: palette.orange, fontWeight: 600 }}>
              {formatAmount(a.occupied, a.currencyCode)}
            </span>
            {a.occupiers.map((o) => (
              <div key={o.sourceNo}>
                <button
                  type="button"
                  className="zhul-link"
                  style={{ fontSize: 12 }}
                  onClick={() => history.push(`/sales/pi/${o.sourceId}`)}
                >
                  {o.sourceNo}
                </button>
              </div>
            ))}
          </div>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '人民币价值',
      dataIndex: 'availableCny',
      width: 140,
      align: 'right',
      render: (v: number) => <span style={num}>{formatAmount(v, 'CNY')}</span>,
    },
    {
      title: '最近变动',
      key: 'last',
      width: 220,
      render: (_, a) => (
        <div>
          {a.lastTime?.slice(5, 16).replace('T', ' ')}
          <div style={{ fontSize: 12, color: palette.mute }}>
            {[a.lastTypeName, a.lastSourceNo].filter(Boolean).join(' · ')}
          </div>
        </div>
      ),
    },
    {
      title: '操作',
      key: 'op',
      width: rowActionsWidth(2),
      fixed: 'right',
      render: (_, a) => (
        <RowActions
          inline={[
            {
              key: 'ledger',
              label: '流水',
              icon: <ScrollText size={18} />,
              onClick: () => setViewing(a),
            },
            canWithdraw && {
              key: 'withdraw',
              label: '转给客户',
              icon: <HandCoins size={18} />,
              disabledReason:
                a.available > 0
                  ? undefined
                  : a.occupied > 0
                    ? '余额都被 PI 占用'
                    : '没有可用余额',
              onClick: () => setWithdrawing(a),
            },
          ]}
        />
      ),
    },
  ];

  const s = summary;
  const search = () => {
    const v = form.getFieldsValue();
    setQuery((v.keyword ?? '').trim());
    setCurrency(v.currency);
    setUnitId(v.unitId);
    setPage(1);
  };
  const empty =
    rows !== null && rows.length === 0 && !query && !currency && !unitId;

  return (
    <div>
      <PageHeader
        help={HELP.page}
        extra={
          canAdjust && (
            <Button
              icon={<Pencil size="1em" />}
              onClick={() => setAdjusting({})}
            >
              手工调整
            </Button>
          )
        }
      />
      {s && <SummaryBar items={summaryItems(s)} />}
      <Tabs
        activeKey={tab}
        onChange={(k) => setTab(k as 'accounts' | 'recon')}
        items={[
          { key: 'accounts', label: '客户余额' },
          { key: 'recon', label: '月度核对' },
        ]}
      />
      {tab === 'recon' ? (
        <ReconTab unitsEnabled={bu.enabled} units={bu.units} />
      ) : (
        <>
          <Form form={form} component={false}>
            <FilterCard
              onSearch={search}
              onReset={() => {
                form.resetFields();
                setQuery('');
                setCurrency(undefined);
                setUnitId(undefined);
                setShowAll(false);
                setPage(1);
              }}
            >
              <FilterField label="客户" span={2} help={HELP.keyword}>
                <Form.Item name="keyword" noStyle>
                  <Input
                    allowClear
                    prefix={<Search size={16} />}
                    placeholder="客户名称"
                    aria-label="客户"
                    onPressEnter={search}
                  />
                </Form.Item>
              </FilterField>
              <FilterField label="币种">
                <Form.Item name="currency" noStyle>
                  <Select
                    allowClear
                    placeholder="全部"
                    aria-label="币种"
                    options={CURRENCIES.map((c) => ({ value: c, label: c }))}
                  />
                </Form.Item>
              </FilterField>
              {bu.enabled && (
                <FilterField label="事业部">
                  <Form.Item name="unitId" noStyle>
                    <Select
                      allowClear
                      placeholder="全部"
                      aria-label="事业部"
                      options={bu.units.map((u) => ({
                        value: u.id,
                        label: u.name,
                      }))}
                    />
                  </Form.Item>
                </FilterField>
              )}
            </FilterCard>
          </Form>
          <MainCard
            left={
              <>
                {`共 ${total} 条`}
                <HelpTip
                  title={`每行是一个余额账户（客户 + 币种${bu.enabled ? ' + 事业部' : ''}），审计信息在流水里。「PI 占用中」是还没转订单的 PI 上的抵扣，已从可用余额扣除，关闭 PI 后退回`}
                />
              </>
            }
            quick={
              <QuickFilters
                options={[{ value: 'hasBalance', label: '只看有余额的' }]}
                value={showAll ? [] : ['hasBalance']}
                onChange={(v) => {
                  setShowAll(v.length === 0);
                  setPage(1);
                }}
              />
            }
          >
            {empty ? (
              <EmptyHint
                title="还没有客户余额"
                description="售后退款选「转客户余额」、PI 上的代收款收齐、或处理客户多付后，余额会出现在这里。"
                actionText={canAdjust ? '录入旧系统余额' : undefined}
                onAction={canAdjust ? () => setAdjusting({}) : undefined}
              />
            ) : (
              <Table<CreditAccount>
                rowKey={(a) =>
                  `${a.customerId}-${a.currencyCode}-${a.businessUnitId ?? 0}`
                }
                loading={rows === null}
                columns={columns}
                dataSource={rows ?? []}
                scroll={{ x: tableScrollX(columns) }}
                onRow={(a) => ({ onDoubleClick: () => setViewing(a) })}
                pagination={{
                  ...PAGER_STYLE,
                  current: page,
                  pageSize,
                  total,
                  onChange: (p, size) => {
                    setPage(p);
                    setPageSize(size);
                  },
                }}
              />
            )}
          </MainCard>
        </>
      )}
      <EntriesDrawer
        open={!!viewing}
        account={viewing}
        version={version}
        onClose={() => setViewing(undefined)}
        readOnly={!canWithdraw && !canAdjust}
        onWithdraw={
          canWithdraw
            ? (a) => {
                // 先关抽屉再开弹窗，避免抽屉盖在弹窗上
                setViewing(undefined);
                setWithdrawing(a);
              }
            : undefined
        }
        onAdjust={
          canAdjust
            ? (a) => {
                setViewing(undefined);
                setAdjusting({ account: a });
              }
            : undefined
        }
      />
      <WithdrawModal
        open={!!withdrawing}
        account={withdrawing}
        onClose={() => setWithdrawing(undefined)}
        onDone={refresh}
      />
      <AdjustModal
        open={!!adjusting}
        account={adjusting?.account}
        onClose={() => setAdjusting(undefined)}
        onDone={refresh}
      />
    </div>
  );
};

/** 月度核对默认看上月的 USD、全部事业部 */
const reconDefaults = (): {
  month: Dayjs;
  currency: string;
  unitId?: number;
} => ({ month: dayjs().subtract(1, 'month'), currency: 'USD' });

/** 月度核对：期初 + 转入 − 抵扣 − 提现 = 期末，每格上面原币、下面人民币 */
export const ReconTab: React.FC<{
  unitsEnabled: boolean;
  units: { id: number; name: string }[];
}> = ({ unitsEnabled, units }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [month, setMonth] = useState<Dayjs>(() => reconDefaults().month);
  const [currency, setCurrency] = useState('USD');
  const [unitId, setUnitId] = useState<number>();
  /** 搜索区里还没点「查询」的条件 */
  const [draft, setDraft] = useState(reconDefaults);
  const [data, setData] = useState<Recon | null>(null);
  const [exporting, setExporting] = useState(false);

  // 切换月份、币种或事业部时丢弃上一次的迟到响应（audit UI-078）
  useEffect(() => {
    const { live, cancel } = liveRequests();
    setData(null);
    live(creditApi.reconciliation(month.format('YYYY-MM'), currency, unitId))
      .then(setData)
      .catch((e) => message.error(readBizError(e).message));
    return cancel;
  }, [month, currency, unitId, message]);

  const cell = (p: Pair, strong?: boolean) =>
    p.amount === 0 && p.amountCny === 0 && !strong ? (
      <span style={{ color: palette.mute }}>—</span>
    ) : (
      <div style={{ ...num, textAlign: 'right' }}>
        <div
          style={{
            fontWeight: strong ? 700 : 500,
            color: strong ? palette.ink : palette.sub,
          }}
        >
          {formatAmount(p.amount, currency)}
        </div>
        <div style={{ fontSize: 12, color: palette.mute }}>
          {formatAmount(p.amountCny, 'CNY')}
        </div>
      </div>
    );
  const col = (title: string, key: keyof ReconRow, strong?: boolean) => ({
    title,
    key,
    width: 120,
    align: 'right' as const,
    render: (_: unknown, r: ReconRow) => cell(r[key] as Pair, strong),
  });
  const columns: TableColumnsType<ReconRow> = [
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 200,
      render: (v: string) => <b style={{ color: palette.ink }}>{v}</b>,
    },
    ...(unitsEnabled
      ? [
          {
            title: '事业部',
            dataIndex: 'businessUnitName',
            width: 130,
          } as const,
        ]
      : []),
    col('期初', 'opening'),
    col('售后退款转入', 'refundIn'),
    col('代收转入', 'collectionIn'),
    col('多收转入', 'overpaidIn'),
    col('调整', 'adjust'),
    col('抵扣', 'deduct'),
    col('提现', 'withdraw'),
    col('期末', 'closing', true),
  ];

  return (
    <>
      <FilterCard
        onSearch={() => {
          setMonth(draft.month);
          setCurrency(draft.currency);
          setUnitId(draft.unitId);
        }}
        onReset={() => {
          const d = reconDefaults();
          setDraft(d);
          setMonth(d.month);
          setCurrency(d.currency);
          setUnitId(d.unitId);
        }}
        moreCount={unitsEnabled && unitId != null ? 1 : 0}
        more={
          unitsEnabled ? (
            <FilterField label="事业部">
              <Select
                allowClear
                placeholder="全部"
                aria-label="事业部"
                value={draft.unitId}
                onChange={(v) => setDraft({ ...draft, unitId: v })}
                options={units.map((u) => ({ value: u.id, label: u.name }))}
              />
            </FilterField>
          ) : undefined
        }
      >
        <FilterField label="月份">
          <DatePicker
            picker="month"
            allowClear={false}
            aria-label="月份"
            style={{ width: '100%' }}
            value={draft.month}
            disabledDate={(d) => d.isAfter(dayjs(), 'month')}
            onChange={(d) => d && setDraft({ ...draft, month: d })}
          />
        </FilterField>
        <FilterField label="币种">
          <Select
            aria-label="币种"
            value={draft.currency}
            onChange={(v) => setDraft({ ...draft, currency: v })}
            options={CURRENCIES.map((c) => ({ value: c, label: c }))}
          />
        </FilterField>
      </FilterCard>
      <MainCard
        left={
          <>
            {data &&
              (data.balanced ? (
                <Pill tone="green">
                  <CircleCheck size="1em" /> 期初 + 转入 − 抵扣 − 提现 =
                  期末，已平衡
                </Pill>
              ) : (
                <Pill tone="red">
                  <TriangleAlert size="1em" /> 合计不平衡，请联系管理员
                </Pill>
              ))}
            <HelpTip title={HELP.recon} />
          </>
        }
        right={
          <Button
            type="text"
            icon={<Download size="1em" />}
            style={{ color: palette.link }}
            loading={exporting}
            onClick={async () => {
              setExporting(true);
              try {
                await creditApi.exportRecon(
                  month.format('YYYY-MM'),
                  currency,
                  unitId,
                );
              } catch (e) {
                message.error((e as Error).message || readBizError(e).message);
              } finally {
                setExporting(false);
              }
            }}
          >
            导出 Excel
          </Button>
        }
      >
        <Table<ReconRow>
          rowKey={(r) => `${r.customerId}-${r.businessUnitId ?? 0}`}
          loading={!data}
          columns={columns}
          dataSource={data?.rows ?? []}
          pagination={false}
          scroll={{ x: tableScrollX(columns) }}
          locale={{
            emptyText: tableEmpty(
              `${month.format('YYYY 年 M 月')}没有 ${currency} 余额变动`,
              !data,
            ),
          }}
          summary={() =>
            data && data.rows.length > 0 ? (
              <Table.Summary.Row style={{ background: palette.inset }}>
                {columns.map((c, i) => (
                  <Table.Summary.Cell
                    key={String(c.key ?? i)}
                    index={i}
                    align={i === 0 ? 'left' : 'right'}
                  >
                    {i === 0 ? (
                      <b>合计</b>
                    ) : unitsEnabled && i === 1 ? null : (
                      cell(
                        data.total[(c as { key: keyof ReconRow }).key] as Pair,
                        true,
                      )
                    )}
                  </Table.Summary.Cell>
                ))}
              </Table.Summary.Row>
            ) : null
          }
        />
      </MainCard>
    </>
  );
};

export default CreditPage;
