import { history, Link, useAccess, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Input, Select, Skeleton, Table, Tabs } from 'antd';
import { Download, Info, Lock, LockOpen, Search } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { tableEmpty } from '@/components/EmptyState';
import { MoreActions, PageHeader } from '@/components/ListLayout';
import { useOnReactivate, usePageTitle } from '@/components/PageTabs/hooks';
import { Card, Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { ModelCell, useWarehouses } from '@/pages/warehouse/inventoryParts';
import {
  type CategoryLedger,
  docHref,
  type LedgerRow,
} from '@/pages/warehouse/inventoryService';
import { CategoryLedgerTable } from '@/pages/warehouse/ledger/CategoryLedgerTable';
import { readBizError } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { PAGER_STYLE } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { ReasonPill, SignedMoney } from '../costingParts';
import {
  type CostAdjust,
  caPath,
  costAdjustApi,
  PERIOD_CLOSED,
  type Period,
  type PeriodCogs,
  periodApi,
} from '../costingService';
import { CloseModal, PeriodTiles, ReopenModal } from './dialogs';

const money = (v?: number) => formatAmount(v ?? 0, 'CNY');
const plain = (v?: number) => money(v).replace(/^CNY\s/, '');

/** 分页取数的小表格：页码、每页条数与加载状态 */
const usePaged = <T,>(
  fetch: (
    page: number,
    pageSize: number,
  ) => Promise<{ total: number; records: T[] }>,
) => {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<T[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const res = await fetch(page, pageSize);
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [fetch, page, pageSize]);
  useEffect(() => {
    load();
  }, [load]);
  return {
    rows,
    total,
    loading,
    error,
    load,
    reset: () => setPage(1),
    pagination: {
      ...PAGER_STYLE,
      current: page,
      pageSize,
      total,
      showTotal: (t: number) => `共 ${t} 条`,
      onChange: (p: number, s: number) => {
        setPage(p);
        setPageSize(s);
      },
    },
  };
};

/** 库存台账页签：全部仓库或单个仓库，已结账为快照 */
const LedgerTab: React.FC<{ month: string }> = ({ month }) => {
  const warehouses = useWarehouses();
  const [warehouseId, setWarehouseId] = useState<number>();
  const [keyword, setKeyword] = useState('');
  const fetch = useCallback(
    (page: number, pageSize: number) =>
      periodApi.ledger(month, {
        warehouseId,
        keyword: keyword.trim() || undefined,
        page,
        pageSize,
      }),
    [month, warehouseId, keyword],
  );
  const t = usePaged<LedgerRow>(fetch);
  const columns: TableColumnsType<LedgerRow> = [
    {
      title: '品牌 · 型号',
      dataIndex: 'model',
      width: 220,
      render: (_, r) => (
        <ModelCell model={r.model} brand={r.brand} category={r.category} />
      ),
    },
    { title: '期初数量', dataIndex: 'openingQty', width: 90 },
    {
      title: '期初金额',
      dataIndex: 'openingAmount',
      width: 120,
      render: plain,
    },
    { title: '本月入库', dataIndex: 'inQty', width: 90 },
    { title: '入库金额', dataIndex: 'inAmount', width: 120, render: plain },
    { title: '本月出库', dataIndex: 'outQty', width: 90 },
    { title: '出库金额', dataIndex: 'outAmount', width: 120, render: plain },
    {
      title: '成本调整',
      dataIndex: 'adjustAmount',
      width: 110,
      render: (v: number) => <SignedMoney value={v} />,
    },
    {
      title: '期末数量',
      dataIndex: 'closingQty',
      width: 90,
      render: (v: number) => <b>{v}</b>,
    },
    {
      title: '期末金额',
      dataIndex: 'closingAmount',
      width: 120,
      render: plain,
    },
  ];
  if (t.error) return <ErrorHint message={t.error} onRetry={t.load} />;
  return (
    <>
      <div style={{ display: 'flex', gap: 12, marginBottom: 12 }}>
        <Select
          allowClear
          placeholder="全部仓库"
          style={{ width: 180 }}
          value={warehouseId}
          onChange={(v) => {
            setWarehouseId(v);
            t.reset();
          }}
          options={warehouses.map((w) => ({ value: w.id, label: w.name }))}
        />
        <Input.Search
          allowClear
          prefix={<Search size="1em" />}
          placeholder="型号、品牌"
          style={{ width: 260 }}
          onSearch={(v) => {
            setKeyword(v);
            t.reset();
          }}
        />
      </div>
      <Table<LedgerRow>
        rowKey="productId"
        size="middle"
        loading={t.loading}
        columns={columns}
        dataSource={t.rows}
        scroll={{ x: tableScrollX(columns) }}
        locale={{ emptyText: tableEmpty(`${month} 没有库存往来`, t.loading) }}
        pagination={t.pagination}
      />
    </>
  );
};

/** 销售成本页签：按订单型号行列出本月出运结转与针对以前出运的成本调整 */
const CogsTab: React.FC<{ month: string }> = ({ month }) => {
  const { palette } = useAppTheme();
  const [keyword, setKeyword] = useState('');
  const fetch = useCallback(
    (page: number, pageSize: number) =>
      periodApi.cogs(month, {
        keyword: keyword.trim() || undefined,
        page,
        pageSize,
      }),
    [month, keyword],
  );
  const t = usePaged<PeriodCogs>(fetch);
  const columns: TableColumnsType<PeriodCogs> = [
    {
      title: '订单号',
      dataIndex: 'soNo',
      width: 150,
      render: (v: string, r) =>
        r.soId ? <Link to={`/sales/orders/${r.soId}`}>{v}</Link> : v || '—',
    },
    {
      title: '型号行',
      dataIndex: 'model',
      width: 220,
      render: (_, r) => (
        <ModelCell model={r.model} brand={r.brand} category={r.category} />
      ),
    },
    {
      title: '出运单号 / 调整单号',
      dataIndex: 'docNo',
      width: 170,
      render: (v: string, r) => {
        const href = docHref(r.docType, r.docId);
        return (
          <div>
            {href ? <Link to={href}>{v}</Link> : v}
            {r.kind === 2 && (
              <div style={{ fontSize: 12, color: palette.mute }}>
                {r.kindName}
              </div>
            )}
          </div>
        );
      },
    },
    { title: '日期', dataIndex: 'bizDate', width: 110 },
    {
      title: '数量',
      align: 'right' as const,
      dataIndex: 'quantity',
      width: 70,
    },
    { title: '货款', dataIndex: 'amountGoods', width: 110, render: plain },
    { title: '开票', dataIndex: 'amountInvoice', width: 100, render: plain },
    { title: '采购费用', dataIndex: 'amountFee', width: 100, render: plain },
    {
      title: '质量索赔',
      dataIndex: 'amountClaim',
      width: 100,
      render: (v: number) => <SignedMoney value={v} />,
    },
    {
      title: '销售成本',
      dataIndex: 'amountCny',
      width: 120,
      render: (v: number) => <b>{plain(v)}</b>,
    },
  ];
  const sum = (k: keyof PeriodCogs) =>
    t.rows.reduce((s, r) => s + Number(r[k] ?? 0), 0);
  const system = t.rows.filter((r) => r.systemQty > 0);
  if (t.error) return <ErrorHint message={t.error} onRetry={t.load} />;
  return (
    <>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          gap: 12,
          marginBottom: 12,
          flexWrap: 'wrap',
        }}
      >
        <span style={{ color: palette.sub, fontSize: 13 }}>
          按订单型号行列出本月出运结转的成本；本月针对以前出运的成本调整也列在这里
        </span>
        <Input.Search
          allowClear
          placeholder="订单号、型号、单号"
          style={{ width: 260 }}
          onSearch={(v) => {
            setKeyword(v);
            t.reset();
          }}
        />
      </div>
      <Table<PeriodCogs>
        rowKey={(r) => `${r.kind}-${r.soItemId}-${r.docId}`}
        size="middle"
        loading={t.loading}
        columns={columns}
        dataSource={t.rows}
        scroll={{ x: tableScrollX(columns) }}
        locale={{ emptyText: tableEmpty(`${month} 没有出运`, t.loading) }}
        pagination={t.pagination}
        summary={() =>
          t.rows.length > 0 ? (
            <Table.Summary.Row>
              <Table.Summary.Cell index={0} colSpan={4}>
                <b>{t.total > t.rows.length ? '本页合计' : '合计'}</b>
              </Table.Summary.Cell>
              <Table.Summary.Cell index={4}>
                <b>{sum('quantity')}</b>
              </Table.Summary.Cell>
              {(
                [
                  'amountGoods',
                  'amountInvoice',
                  'amountFee',
                  'amountClaim',
                  'amountCny',
                ] as const
              ).map((k, i) => (
                <Table.Summary.Cell key={k} index={i + 5}>
                  <b>
                    {k === 'amountClaim' ? (
                      <SignedMoney value={sum(k)} />
                    ) : (
                      plain(sum(k))
                    )}
                  </b>
                </Table.Summary.Cell>
              ))}
            </Table.Summary.Row>
          ) : null
        }
      />
      {system.map((r) => (
        <div
          key={`${r.soItemId}-${r.docId}`}
          style={{ color: palette.sub, fontSize: 13, marginTop: 8 }}
        >
          <Info size="1em" style={{ color: palette.link, marginRight: 6 }} />
          {r.model} 的 {r.systemQty} 个来自系统外入库，成本为
          0；订单成本按采购订单单价计
        </div>
      ))}
    </>
  );
};

/** 成本调整页签：所属月份为本月的成本调整单 */
const AdjustTab: React.FC<{ month: string }> = ({ month }) => {
  const fetch = useCallback(
    (page: number, pageSize: number) =>
      costAdjustApi.page({ month, page, pageSize }),
    [month],
  );
  const t = usePaged<CostAdjust>(fetch);
  const columns: TableColumnsType<CostAdjust> = [
    {
      title: '调整单号',
      dataIndex: 'adjustNo',
      width: 160,
      render: (v: string, r) => <Link to={caPath(r.id)}>{v}</Link>,
    },
    { title: '调整日期', dataIndex: 'adjustDate', width: 110 },
    {
      title: '触发原因',
      dataIndex: 'reasonName',
      width: 120,
      render: (v: string, r) => <ReasonPill reason={r.reason} name={v} />,
    },
    {
      title: '来源单号',
      dataIndex: 'sourceDocNo',
      width: 160,
      render: (v: string | undefined, r) => {
        const href = docHref(r.sourceDocType, r.sourceDocId);
        return v ? href ? <Link to={href}>{v}</Link> : v : '—';
      },
    },
    { title: '型号', dataIndex: 'firstModel', width: 180 },
    {
      title: '在库调整',
      dataIndex: 'stockAmount',
      width: 110,
      render: (v: number) => <SignedMoney value={v} />,
    },
    {
      title: '销售成本调整',
      dataIndex: 'cogsAmount',
      width: 120,
      render: (v: number) => <SignedMoney value={v} />,
    },
    {
      title: '其他出库调整',
      dataIndex: 'otherAmount',
      width: 120,
      render: (v: number) => <SignedMoney value={v} />,
    },
  ];
  if (t.error) return <ErrorHint message={t.error} onRetry={t.load} />;
  return (
    <Table<CostAdjust>
      rowKey="id"
      size="middle"
      loading={t.loading}
      columns={columns}
      dataSource={t.rows}
      scroll={{ x: tableScrollX(columns) }}
      locale={{ emptyText: tableEmpty(`${month} 没有成本调整`, t.loading) }}
      pagination={t.pagination}
    />
  );
};

/** 月份详情（spec finance/period-close「月结页」，原型 P2）：汇总与库存台账、销售成本、成本调整三个页签 */
/** 类目账（spec finance/period-close「库存月结页」）：与库存台账页的类目账同一口径；点品类打开库存台账的明细账 */
const CategoryTab: React.FC<{ month: string }> = ({ month }) => {
  const [data, setData] = useState<CategoryLedger>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      setData(await periodApi.categoryLedger(month));
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [month]);
  useEffect(() => {
    load();
  }, [load]);
  if (error) return <ErrorHint message={error} onRetry={load} />;
  return (
    <CategoryLedgerTable
      data={data}
      loading={loading}
      totalLabel="合计"
      onPick={(id) =>
        history.push(
          `/warehouse/ledger?view=detail&month=${month}&categoryId=${id}`,
        )
      }
    />
  );
};

const PeriodDetail: React.FC = () => {
  const month = useParams<{ month: string }>().month ?? '';
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const [p, setP] = useState<Period>();
  const [error, setError] = useState<string>();
  const [tab, setTab] = useState('category');
  const [closing, setClosing] = useState(false);
  const [reopening, setReopening] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [version, setVersion] = useState(0);
  usePageTitle(month ? `${month} 库存月结` : undefined, month);

  const load = useCallback(async () => {
    setError(undefined);
    try {
      setP(await periodApi.detail(month));
    } catch (e) {
      setError(readBizError(e).message);
    }
  }, [month]);
  useEffect(() => {
    load();
  }, [load]);
  // 从其他页面返回时重新取数（audit UI-055）
  useOnReactivate(load);

  if (error) return <ErrorHint message={error} onRetry={load} />;
  if (!p) return <Skeleton active paragraph={{ rows: 12 }} />;

  const closed = p.status === PERIOD_CLOSED;
  const changed = () => {
    setClosing(false);
    setReopening(false);
    setVersion((v) => v + 1);
    load();
  };

  return (
    <div>
      <PageHeader
        title={`${month} 库存月结`}
        tags={<Pill tone={closed ? 'green' : 'orange'}>{p.statusName}</Pill>}
        sub={
          closed
            ? `${p.closedByName ?? ''} ${formatDateTime(p.closedAt).slice(0, 16)} 结账 · 显示结账时保存的快照`
            : p.reopenReason
              ? `${p.reopenedByName ?? ''} ${formatDateTime(p.reopenedAt).slice(0, 16)} 反结账：${p.reopenReason} · 显示实时数字`
              : '未结账 · 显示实时数字'
        }
        extra={
          <>
            <Button
              type="text"
              icon={<Download size="1em" />}
              style={{ color: palette.link }}
              loading={exporting}
              onClick={async () => {
                setExporting(true);
                try {
                  await periodApi.export(month);
                } catch (e) {
                  message.error(readBizError(e).message);
                } finally {
                  setExporting(false);
                }
              }}
            >
              导出 Excel
            </Button>
            {/* 反结账收进「更多」 */}
            <MoreActions
              items={[
                p.reopenable &&
                  access['inventory:period:reopen'] && {
                    key: 'reopen',
                    label: '反结账',
                    icon: <LockOpen size={16} />,
                    danger: true,
                  },
              ]}
              onClick={() => setReopening(true)}
            />
            {!closed && access['inventory:period:close'] && (
              <Button
                type="primary"
                icon={<Lock size="1em" />}
                disabled={!p.closable}
                title={p.closable ? undefined : p.closeBlock}
                onClick={() => setClosing(true)}
              >
                结账
              </Button>
            )}
          </>
        }
      />
      <Card style={{ padding: 20, marginBottom: 16 }}>
        <PeriodTiles p={p} />
        {!closed && (p.reminders?.length ?? 0) > 0 && (
          <div style={{ marginTop: 12 }}>
            {p.reminders?.map((r) => (
              <div
                key={r}
                style={{ color: palette.sub, fontSize: 13, marginTop: 4 }}
              >
                <Info
                  size="1em"
                  style={{ color: palette.orange, marginRight: 6 }}
                />
                {r}
              </div>
            ))}
          </div>
        )}
      </Card>
      <Card style={{ padding: '4px 20px 20px' }}>
        <Tabs // lint-ui-ignore page-tabs: 期间详情卡片里的分区页签
          activeKey={tab}
          onChange={setTab}
          items={[
            {
              key: 'category',
              label: '类目账',
              children: <CategoryTab key={version} month={month} />,
            },
            {
              key: 'ledger',
              label: '库存台账',
              children: <LedgerTab key={version} month={month} />,
            },
            {
              key: 'cogs',
              label: '销售成本',
              children: <CogsTab key={version} month={month} />,
            },
            {
              key: 'adjust',
              label: '成本调整',
              children: <AdjustTab key={version} month={month} />,
            },
          ]}
        />
      </Card>
      <CloseModal
        month={closing ? month : undefined}
        onClose={() => setClosing(false)}
        onDone={changed}
      />
      <ReopenModal
        month={reopening ? month : undefined}
        onClose={() => setReopening(false)}
        onDone={changed}
      />
    </div>
  );
};

export default PeriodDetail;
