import {
  CheckCircleOutlined,
  DownloadOutlined,
  EditOutlined,
  LockOutlined,
  SendOutlined,
  WalletOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  DatePicker,
  Input,
  Segmented,
  Select,
  Skeleton,
  Switch,
  Table,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle, Pill, Stat } from '@/pages/inquiry/shared/components';
import { EmptyHint } from '@/pages/product/components/EmptyHint';
import { readBizError } from '@/pages/sales/service';
import { useAppTheme } from '@/theme/AppTheme';
import { useBusinessUnits } from '@/utils/businessUnit';
import { formatAmount } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
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
  const [keyword, setKeyword] = useState('');
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
              <Pill tone="violet">代收</Pill>
            </span>
          )}
          <div style={{ fontSize: 12, color: palette.mute }}>
            {[a.customerCountry, a.currencyCode].filter(Boolean).join(' · ')}
          </div>
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
                <a
                  style={{ fontSize: 12 }}
                  onClick={() => history.push(`/sales/pi/${o.sourceId}`)}
                >
                  {o.sourceNo}
                </a>
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
      width: 150,
      fixed: 'right',
      render: (_, a) => (
        <span style={{ display: 'inline-flex', gap: 12 }}>
          <a onClick={() => setViewing(a)}>流水</a>
          {canWithdraw &&
            (a.available > 0 ? (
              <a onClick={() => setWithdrawing(a)}>转给客户</a>
            ) : (
              <span
                style={{ color: palette.mute }}
                title={a.occupied > 0 ? '余额都被 PI 占用' : '没有可用余额'}
              >
                转给客户
              </span>
            ))}
        </span>
      ),
    },
  ];

  const s = summary;
  const empty =
    rows !== null && rows.length === 0 && !query && !currency && !unitId;

  return (
    <div>
      <PageTitle
        description="客户暂存在公司的钱：售后退款转的余额、代收款、客户多付的部分。可以抵扣下一张 PI，也可以转给客户。"
        actions={
          canAdjust && (
            <Button icon={<EditOutlined />} onClick={() => setAdjusting({})}>
              手工调整
            </Button>
          )
        }
      />
      {!s ? (
        <Skeleton active paragraph={{ rows: 2 }} />
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
            gap: 16,
            marginBottom: 16,
          }}
        >
          {(s.currencies.length
            ? s.currencies
            : [{ currencyCode: 'USD', amount: 0, amountCny: 0, count: 0 }]
          )
            .slice(0, 2)
            .map((c) => (
              <Stat
                key={c.currencyCode}
                icon={<WalletOutlined />}
                color={palette.link}
                soft={palette.accentSoft}
                label={`${c.currencyCode} 可用余额`}
                value={formatAmount(c.amount, c.currencyCode)}
                hint={`${c.count} 个客户 · 价值 ${formatAmount(c.amountCny, 'CNY')}`}
              />
            ))}
          <Stat
            icon={<LockOutlined />}
            color={palette.orange}
            soft={palette.orangeSoft}
            label="已被 PI 占用"
            value={
              s.occupied.length
                ? s.occupied
                    .map((o) => formatAmount(o.amount, o.currencyCode))
                    .join(' · ')
                : '—'
            }
            hint={
              s.occupied.length
                ? `${s.occupied.reduce((n, o) => n + o.count, 0)} 张 PI，客户付款前不能再用`
                : '没有被占用的余额'
            }
          />
          <Stat
            icon={<SendOutlined />}
            color={palette.green}
            soft={palette.greenSoft}
            label="本月转给客户"
            value={formatAmount(s.monthWithdrawCny, 'CNY')}
            hint={`${s.monthWithdrawCount} 笔 · 汇兑损益 ${s.monthFxGain >= 0 ? '+' : ''}${formatAmount(s.monthFxGain, 'CNY')}`}
          />
        </div>
      )}
      <div
        style={{
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          marginBottom: 14,
          flexWrap: 'wrap',
        }}
      >
        <Segmented<'accounts' | 'recon'>
          value={tab}
          onChange={setTab}
          options={[
            { value: 'accounts', label: '客户余额' },
            { value: 'recon', label: '月度核对' },
          ]}
        />
      </div>
      {tab === 'recon' ? (
        <ReconTab unitsEnabled={bu.enabled} units={bu.units} />
      ) : (
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
            <Input.Search
              allowClear
              placeholder="搜客户名称"
              style={{ width: 240 }}
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value);
                if (!e.target.value) setQuery('');
              }}
              onSearch={(v) => {
                setQuery(v.trim());
                setPage(1);
              }}
            />
            <Select
              allowClear
              placeholder="全部币种"
              style={{ width: 130 }}
              value={currency}
              onChange={(v) => {
                setCurrency(v);
                setPage(1);
              }}
              options={CURRENCIES.map((c) => ({ value: c, label: c }))}
            />
            {bu.enabled && (
              <Select
                allowClear
                placeholder="全部事业部"
                style={{ width: 160 }}
                value={unitId}
                onChange={(v) => {
                  setUnitId(v);
                  setPage(1);
                }}
                options={bu.units.map((u) => ({ value: u.id, label: u.name }))}
              />
            )}
            <span
              style={{
                marginLeft: 'auto',
                display: 'inline-flex',
                gap: 8,
                alignItems: 'center',
                fontSize: 12,
                color: palette.sub,
              }}
            >
              <Switch
                size="small"
                checked={!showAll}
                onChange={(v) => {
                  setShowAll(!v);
                  setPage(1);
                }}
              />
              只看有余额的
            </span>
          </div>
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
                current: page,
                pageSize,
                total,
                showSizeChanger: true,
                showTotal: (t) => `共 ${t} 条`,
                onChange: (p, size) => {
                  setPage(p);
                  setPageSize(size);
                },
              }}
            />
          )}
          <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
            每行是一个余额账户（客户 + 币种{bu.enabled ? ' + 事业部' : ''}
            ），审计信息在流水里。「PI 占用中」是还没转订单的 PI
            上的抵扣，已从可用余额扣除，关闭 PI 后退回。
          </div>
        </Card>
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

/** 月度核对：期初 + 转入 − 抵扣 − 提现 = 期末，每格上面原币、下面人民币 */
const ReconTab: React.FC<{
  unitsEnabled: boolean;
  units: { id: number; name: string }[];
}> = ({ unitsEnabled, units }) => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [month, setMonth] = useState<Dayjs>(dayjs().subtract(1, 'month'));
  const [currency, setCurrency] = useState('USD');
  const [unitId, setUnitId] = useState<number>();
  const [data, setData] = useState<Recon | null>(null);
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    setData(null);
    creditApi
      .reconciliation(month.format('YYYY-MM'), currency, unitId)
      .then(setData)
      .catch((e) => message.error(readBizError(e).message));
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
        <DatePicker
          picker="month"
          allowClear={false}
          value={month}
          disabledDate={(d) => d.isAfter(dayjs(), 'month')}
          onChange={(d) => d && setMonth(d)}
        />
        <Select
          value={currency}
          onChange={setCurrency}
          style={{ width: 110 }}
          options={CURRENCIES.map((c) => ({ value: c, label: c }))}
        />
        {unitsEnabled && (
          <Select
            allowClear
            placeholder="全部事业部"
            style={{ width: 160 }}
            value={unitId}
            onChange={setUnitId}
            options={units.map((u) => ({ value: u.id, label: u.name }))}
          />
        )}
        <span style={{ marginLeft: 'auto' }} />
        {data &&
          (data.balanced ? (
            <Pill tone="green">
              <CheckCircleOutlined /> 期初 + 转入 − 抵扣 − 提现 = 期末，已平衡
            </Pill>
          ) : (
            <Pill tone="red">
              <WarningOutlined /> 合计不平衡，请联系管理员
            </Pill>
          ))}
        <Button
          icon={<DownloadOutlined />}
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
      </div>
      <Table<ReconRow>
        rowKey={(r) => `${r.customerId}-${r.businessUnitId ?? 0}`}
        loading={!data}
        columns={columns}
        dataSource={data?.rows ?? []}
        pagination={false}
        scroll={{ x: tableScrollX(columns) }}
        locale={{
          emptyText: `${month.format('YYYY 年 M 月')}没有 ${currency} 余额变动`,
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
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
        「抵扣」= 本月占用 − 本月释放。默认月份为上月；导出的 Excel
        与页面一致，用于月末和银行流水、支付宝转账记录核对。
      </div>
    </Card>
  );
};

export default CreditPage;
