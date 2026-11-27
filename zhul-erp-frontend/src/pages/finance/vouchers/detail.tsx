import { Link, useAccess, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Alert, App, Button, Segmented, Skeleton, Table } from 'antd';
import { CircleAlert, Download, FilePlus, Info, Settings } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { tableEmpty } from '@/components/EmptyState';
import { PageHeader } from '@/components/ListLayout';
import { useOnReactivate, usePageTitle } from '@/components/PageTabs/hooks';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { docHref } from '@/pages/warehouse/inventoryService';
import { readBizError } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import {
  AccountDrawer,
  CategoryPill,
  ReconPill,
  useVoucherExport,
} from './parts';
import {
  CATEGORIES,
  type Voucher,
  type VoucherEntry,
  type VoucherMonthDetail,
  voucherApi,
} from './service';

const yuan = (v?: number) => formatAmount(v ?? 0, 'CNY');
const plain = (v?: number) => formatAmount(v ?? 0, 'CNY').replace(/^CNY\s/, '');

/** 期初 + 凭证里库存商品净额 + 不生成凭证的变动 = 期末（ui-design-patterns「汇总数字按算式排」） */
const ReconTiles: React.FC<{ d: VoucherMonthDetail }> = ({ d }) => {
  const { palette } = useAppTheme();
  const r = d.recon;
  const items: [string, number][] = [
    ['期初库存金额', r.openingAmount],
    ['凭证里库存商品净额', r.stockNetAmount],
    ['不生成凭证的变动', r.noVoucherAmount],
    ['期末库存金额', r.closingAmount],
  ];
  const ops = ['+', '+', '='];
  return (
    <div>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          flexWrap: 'wrap',
        }}
      >
        {items.map(([label, value], i) => (
          <React.Fragment key={label}>
            <div
              style={{
                minWidth: 150,
                border: `1px solid ${palette.hairline}`,
                borderRadius: 12,
                padding: '12px 16px',
              }}
            >
              <div style={{ fontSize: 12, color: palette.sub }}>{label}</div>
              <div
                style={{
                  fontSize: 20,
                  fontWeight: 600,
                  marginTop: 4,
                  fontVariantNumeric: 'tabular-nums',
                  whiteSpace: 'nowrap',
                }}
              >
                {yuan(value)}
              </div>
            </div>
            {i < ops.length && (
              <span style={{ color: palette.mute, fontSize: 16 }}>
                {ops[i]}
              </span>
            )}
          </React.Fragment>
        ))}
        <span style={{ marginLeft: 8 }}>
          {r.diff === 0 ? (
            <Pill tone="green">核对相符</Pill>
          ) : (
            <ReconPill diff={r.diff} />
          )}
        </span>
      </div>
      <div
        style={{
          marginTop: 14,
          fontSize: 13,
          color: palette.sub,
          display: 'flex',
          flexWrap: 'wrap',
          gap: '4px 14px',
        }}
      >
        <span>不生成凭证的变动</span>
        {r.noVoucherItems.map((i) => (
          <span key={i.bizType}>
            <b style={{ color: palette.ink }}>{i.name}</b> {yuan(i.amount)}
          </span>
        ))}
        <span style={{ color: palette.mute }}>需要会计在 T+ 里另行处理</span>
      </div>
      {d.returnInAmount !== 0 && (
        <div style={{ marginTop: 8, fontSize: 12, color: palette.mute }}>
          <Info size="1em" style={{ marginRight: 6 }} />
          库存月结的本月销售成本 {yuan(d.periodCogsAmount)} 不扣退货入库{' '}
          {yuan(d.returnInAmount)}
        </div>
      )}
    </div>
  );
};

/** 展开的分录：业务项、T+ 科目、往来单位、借方、贷方 */
const Entries: React.FC<{ v: Voucher }> = ({ v }) => {
  const { palette } = useAppTheme();
  const cols: TableColumnsType<VoucherEntry> = [
    { title: '业务项', dataIndex: 'itemName', render: (t) => <b>{t}</b> },
    {
      title: 'T+ 科目',
      key: 'account',
      width: 220,
      render: (_, e) =>
        e.accountCode ? (
          <>
            <b style={{ fontVariantNumeric: 'tabular-nums' }}>
              {e.accountCode}
            </b>{' '}
            {e.accountName}
          </>
        ) : (
          <Pill tone="orange">
            <CircleAlert size="1em" /> 未设置科目
          </Pill>
        ),
    },
    {
      title: '往来单位',
      dataIndex: 'partnerName',
      width: 180,
      render: (t?: string) =>
        t || <span style={{ color: palette.mute }}>—</span>,
    },
    {
      title: '借方',
      dataIndex: 'debit',
      width: 130,
      align: 'right',
      render: (n?: number) => (n == null ? '' : plain(n)),
    },
    {
      title: '贷方',
      dataIndex: 'credit',
      width: 130,
      align: 'right',
      render: (n?: number) => (n == null ? '' : plain(n)),
    },
  ];
  const sum = (k: 'debit' | 'credit') =>
    v.entries.reduce((s, e) => s + Number(e[k] ?? 0), 0);
  return (
    <Table<VoucherEntry>
      rowKey={(e) => `${e.itemKey}-${e.partnerId ?? ''}-${e.debit ?? ''}`}
      size="small"
      pagination={false}
      columns={cols}
      dataSource={v.entries}
      summary={() => (
        <Table.Summary.Row>
          <Table.Summary.Cell index={0} colSpan={3}>
            <b>合计</b>
          </Table.Summary.Cell>
          <Table.Summary.Cell index={3} align="right">
            <b>{plain(sum('debit'))}</b>
          </Table.Summary.Cell>
          <Table.Summary.Cell index={4} align="right">
            <b>{plain(sum('credit'))}</b>
          </Table.Summary.Cell>
        </Table.Summary.Row>
      )}
    />
  );
};

/** 业务凭证月份详情（原型 V2）：核对、凭证列表（按类别筛选、展开分录），点单号打开来源单据 */
const VoucherMonthPage: React.FC = () => {
  const month = useParams<{ month: string }>().month ?? '';
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const [d, setD] = useState<VoucherMonthDetail>();
  const [error, setError] = useState<string>();
  const [category, setCategory] = useState<number>(0);
  const [accountsOpen, setAccountsOpen] = useState(false);
  const [generating, setGenerating] = useState(false);
  usePageTitle(month ? `${month} 业务凭证` : undefined, month);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setD(await voucherApi.detail(month));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [month]);
  useEffect(() => {
    load();
  }, [load]);
  useOnReactivate(load);

  const canExport = !!access['finance:voucher:export'];
  const { exportMonth, exporting } = useVoucherExport(
    () => setAccountsOpen(true),
    load,
  );

  if (error) return <ErrorHint page message={error} onRetry={load} />;
  if (!d) return <Skeleton active paragraph={{ rows: 12 }} />;

  const exported = (d.exportCount ?? 0) > 0;
  const shown =
    category === 0
      ? d.vouchers
      : d.vouchers.filter((v) => v.category === category);
  const red = (n: number) => (n < 0 ? { color: palette.red } : undefined);

  const columns: TableColumnsType<Voucher> = [
    { title: '日期', dataIndex: 'voucherDate', width: 120 },
    {
      title: '类别',
      dataIndex: 'category',
      width: 130,
      render: (c: number) => <CategoryPill category={c} />,
    },
    {
      title: '摘要',
      key: 'summary',
      render: (_, v) => {
        const href = docHref(v.sourceDocType, v.sourceDocId);
        return (
          <span>
            <span style={red(v.debitTotal)}>{v.action}</span>{' '}
            {href ? (
              <Link
                className="zhul-link zhul-link-doc"
                to={href}
                onClick={(e) => e.stopPropagation()}
              >
                {v.sourceDocNo}
              </Link>
            ) : (
              v.sourceDocNo
            )}{' '}
            {v.partnerText}
          </span>
        );
      },
    },
    {
      title: '借方合计',
      dataIndex: 'debitTotal',
      width: 150,
      align: 'right',
      render: (n: number) => (
        <b style={{ fontVariantNumeric: 'tabular-nums', ...red(n) }}>
          {plain(n)}
        </b>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title={`${month} 业务凭证`}
        tags={
          d.generated && (
            <Pill tone={exported ? 'green' : 'orange'}>
              {exported ? '已导出' : '未导出'}
            </Pill>
          )
        }
        sub={
          d.generated
            ? [
                `${d.generatedByName ?? ''} ${formatDateTime(d.generatedAt).slice(0, 16)} 生成`,
                `${d.vouchers.length} 张凭证`,
                `借贷合计 ${yuan(d.debitTotal)}`,
                exported
                  ? `${d.exportedByName ?? ''} ${formatDateTime(d.exportedAt).slice(0, 16)} 导出`
                  : '',
              ]
                .filter(Boolean)
                .join(' · ')
            : '上线前结账的月份，还没有凭证'
        }
        extra={
          <>
            <Button
              type="text"
              icon={<Settings size="1em" />}
              style={{ color: palette.link }}
              onClick={() => setAccountsOpen(true)}
            >
              科目设置
            </Button>
            {canExport && d.generated && (
              <Button
                type="primary"
                icon={<Download size="1em" />}
                loading={exporting === month}
                onClick={() => exportMonth(month, exported)}
              >
                导出 T+
              </Button>
            )}
            {canExport && !d.generated && (
              <Button
                type="primary"
                icon={<FilePlus size="1em" />}
                loading={generating}
                onClick={async () => {
                  setGenerating(true);
                  try {
                    setD(await voucherApi.generate(month));
                    message.success(`${month} 凭证已生成`);
                  } catch (e) {
                    message.error(readBizError(e).message);
                  } finally {
                    setGenerating(false);
                  }
                }}
              >
                生成凭证
              </Button>
            )}
          </>
        }
      />
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <ReconTiles d={d} />
      </Card>
      {d.missingItems.length > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          title={`${d.missingItems.map((i) => `「${i}」`).join('')}还没有设置科目，设置后才能导出`}
          action={
            <Button
              type="link"
              size="small"
              onClick={() => setAccountsOpen(true)}
            >
              去设置
            </Button>
          }
        />
      )}
      {d.generated && (
        <Card style={{ padding: 20 }}>
          <Segmented<number>
            value={category}
            onChange={setCategory}
            style={{ marginBottom: 16 }}
            options={[
              { value: 0, label: `全部 ${d.vouchers.length}` },
              ...CATEGORIES.map((c) => ({
                value: c.value,
                label: `${c.label} ${d.categoryCounts[c.value] ?? 0}`,
              })),
            ]}
          />
          <Table<Voucher>
            rowKey="id"
            size="middle"
            columns={columns}
            dataSource={shown}
            pagination={
              shown.length > 50
                ? { pageSize: 50, showTotal: (t) => `共 ${t} 张` }
                : false
            }
            expandable={{
              expandRowByClick: true,
              expandedRowRender: (v) => <Entries v={v} />,
            }}
            locale={{ emptyText: tableEmpty('这个类别没有凭证') }}
          />
        </Card>
      )}
      <AccountDrawer
        open={accountsOpen}
        canEdit={!!access['finance:voucher:account']}
        onClose={() => setAccountsOpen(false)}
        onSaved={() => {
          setAccountsOpen(false);
          load();
        }}
      />
    </div>
  );
};

export default VoucherMonthPage;
