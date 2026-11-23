import { history, Link } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Form, Input, Select, Table } from 'antd';
import { Eye, Search } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerCell } from '@/components/DocFields';
import { ModelQtyCell } from '@/components/DocFields/DocPage';
import SourceNos, { useUrlFilter } from '@/components/DocFields/SourceNos';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { IconButton } from '@/components/Shell';
import { TagList } from '@/pages/inquiry/customer-inquiry/ModelCells';
import { Pill } from '@/pages/inquiry/shared/components';
import { auditColumns } from '@/pages/purchase/components';
import {
  HANDLING,
  RQ_STATUS_TONE,
  type SalesReturn,
  salesReturnApi,
  TH_STATUS,
  TH_STATUS_TONE,
} from '@/pages/sales/after-sales/service';
import { searchCustomers } from '@/services/zhul/masterdata';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { PATHS } from '../components';
import { readBizError } from '../service';

/** 销售管理 → 销售退货（spec sales/after-sales）：售后单生成的销售退货单，确认后生成退货入库单 */
type Filters = {
  keyword?: string;
  status?: number;
  customerId?: number;
  handling?: number;
};

/** 帮助文字：help-texts.md「销售退货列表」，关键词范围以 search-scope.md 为准 */
export const HELP = {
  page: '售后单选「仅退货」或「退货退款」后生成；确认后生成退货入库单',
  keyword: '可搜：退货单号、快递单号、订单编号、售后单号、型号',
} as const;

const SalesReturnsPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const url = useUrlFilter();
  const [form] = Form.useForm<Filters>();
  // 默认看待确认；从别的单据带单号跳来时看全部
  const [defaults] = useState<Filters>(() =>
    url.keyword ? { keyword: url.keyword } : { status: TH_STATUS.CONFIRMING },
  );
  const [filters, setFilters] = useState<Filters>(defaults);
  const [customerOptions, setCustomerOptions] = useState<
    { value: number; label: string }[]
  >([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<SalesReturn[]>([]);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState<{
    confirming: number;
    pending: number;
  }>();
  const [loading, setLoading] = useState(true);

  const searchNow = () => {
    setFilters(form.getFieldsValue());
    setPage(1);
  };

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [res, c] = await Promise.all([
        salesReturnApi.page({
          page,
          pageSize,
          status: filters.status,
          handling: filters.handling,
          customerId: filters.customerId,
          keyword: filters.keyword?.trim() || undefined,
        }),
        salesReturnApi.counts(),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setCounts(c);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, filters, message]);
  useEffect(() => {
    load();
  }, [load]);
  // 在详情里确认、作废后返回，列表与计数跟着更新（audit UI-053）
  useOnReactivate(load);

  const searchCust = async (kw: string) => {
    try {
      const res = await searchCustomers(kw);
      setCustomerOptions(
        res.map((c) => ({ value: c.id, label: c.displayName ?? c.name })),
      );
    } catch {
      setCustomerOptions([]);
    }
  };

  const sub = (t?: React.ReactNode) =>
    t ? <div style={{ fontSize: 12, color: palette.mute }}>{t}</div> : null;

  const columns: TableColumnsType<SalesReturn> = [
    {
      title: '退货单号 · 退货日期',
      dataIndex: 'returnNo',
      width: 170,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <Link to={PATHS.salesReturn(r.id)}>{v}</Link>
          {sub(r.createTime?.slice(0, 10))}
        </div>
      ),
    },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 220,
      render: (v: string, r) => (
        <CustomerCell
          name={v}
          country={r.customerCountry}
          type={r.customerType}
          contact={r.customerContact}
        />
      ),
    },
    {
      title: '型号 · 数量',
      key: 'items',
      width: 200,
      render: (_, r) => (
        <ModelQtyCell
          summary={r.itemSummary}
          totalQty={r.totalQty}
          docLabel={`${r.returnNo} · ${r.customerName ?? ''}`}
          onOpenDetail={() => history.push(PATHS.salesReturn(r.id))}
        />
      ),
    },
    {
      title: '品牌',
      dataIndex: 'brands',
      width: 120,
      render: (v?: string[]) => <TagList values={v} label="品牌" />,
    },
    {
      title: '品类',
      dataIndex: 'categories',
      width: 130,
      render: (v?: string[]) => <TagList values={v} label="品类" />,
    },
    {
      title: '处理方式',
      dataIndex: 'handlingName',
      width: 100,
      render: (v: string, r) => (
        <div>
          {v}
          {r.refundFirst ? sub('先退款') : null}
        </div>
      ),
    },
    {
      title: '入库',
      key: 'receipt',
      width: 150,
      render: (_, r) =>
        r.receipt ? (
          <div>
            <div style={{ fontSize: 12, color: palette.sub }}>
              {r.receipt.statusName}
              {r.receivedQty != null
                ? ` · 实收 ${r.receivedQty} / 合格 ${r.qualifiedQty ?? 0}`
                : ''}
            </div>
            <Link to={PATHS.returnReceipt(r.receipt.id)}>{r.receipt.no}</Link>
          </div>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        ),
    },
    {
      title: '退款',
      key: 'refund',
      width: 140,
      render: (_, r) => {
        const q = r.refunds?.[0];
        if (!q) return <span style={{ color: palette.mute }}>—</span>;
        return (
          <div style={{ display: 'grid', gap: 2, justifyItems: 'start' }}>
            <b>{formatAmount(q.amount, q.currencyCode)}</b>
            <Pill tone={RQ_STATUS_TONE[q.status] ?? 'gray'}>
              {q.statusName}
            </Pill>
          </div>
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'statusName',
      width: 90,
      render: (v: string, r) => (
        <Pill tone={TH_STATUS_TONE[r.status] ?? 'gray'}>{v}</Pill>
      ),
    },
    {
      title: '来源单号',
      key: 'source',
      width: 150,
      render: (_, r) => (
        <SourceNos
          refs={[{ no: r.asNo, href: PATHS.afterSales(r.afterSalesId) }]}
        />
      ),
    },
    { title: '负责人', dataIndex: 'ownerName', width: 100 },
    ...auditColumns<SalesReturn>(),
    {
      title: '操作',
      key: 'op',
      width: 64,
      fixed: 'right',
      render: (_, r) => (
        <IconButton
          label="查看"
          size={32}
          icon={<Eye size={18} />}
          onClick={() => history.push(PATHS.salesReturn(r.id))}
        />
      ),
    },
  ];

  /** 下拉选项后面带数量（stat-cards.md：状态并入下拉） */
  const withCount = (label: string, n?: number) =>
    n ? `${label} · ${n}` : label;

  return (
    <div>
      <PageHeader help={HELP.page} />
      <Form form={form} initialValues={defaults} component={false}>
        <FilterCard
          onSearch={searchNow}
          onReset={() => {
            // 重置回默认视图：待确认
            const d = { status: TH_STATUS.CONFIRMING };
            form.resetFields();
            form.setFieldsValue({ ...d, keyword: undefined });
            setFilters(d);
            setPage(1);
          }}
          // 列宽 110：关键词、状态、客户、处理方式排一行
          minColumn={110}
        >
          <FilterField label="关键词" span={2} help={HELP.keyword}>
            <Form.Item name="keyword" noStyle>
              <Input
                allowClear
                prefix={<Search size={16} />}
                placeholder="退货单号、订单编号、型号"
                aria-label="关键词"
                onPressEnter={searchNow}
              />
            </Form.Item>
          </FilterField>
          <FilterField label="状态">
            <Form.Item name="status" noStyle>
              <Select<number>
                allowClear
                placeholder="全部"
                aria-label="状态"
                options={[
                  {
                    value: TH_STATUS.CONFIRMING,
                    label: withCount('待确认', counts?.confirming),
                  },
                  {
                    value: TH_STATUS.PENDING,
                    label: withCount('待入库', counts?.pending),
                  },
                  { value: TH_STATUS.RECEIVED, label: '已入库' },
                  { value: TH_STATUS.VOID, label: '已作废' },
                ]}
                // 切换状态立即查询（doc-list-unify 状态下拉的写法）
                onChange={() => searchNow()}
              />
            </Form.Item>
          </FilterField>
          <FilterField label="客户">
            <Form.Item name="customerId" noStyle>
              <Select<number>
                allowClear
                showSearch={{ filterOption: false, onSearch: searchCust }}
                placeholder="全部"
                options={customerOptions}
                onFocus={() => customerOptions.length === 0 && searchCust('')}
                aria-label="客户"
              />
            </Form.Item>
          </FilterField>
          <FilterField label="处理方式">
            <Form.Item name="handling" noStyle>
              <Select<number>
                allowClear
                placeholder="全部"
                options={[
                  { value: HANDLING.RETURN, label: '仅退货' },
                  { value: HANDLING.REFUND, label: '退货退款' },
                ]}
                aria-label="处理方式"
              />
            </Form.Item>
          </FilterField>
        </FilterCard>
      </Form>
      <MainCard left={`共 ${total} 条`}>
        <Table<SalesReturn>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: tableScrollX(columns) }}
          locale={{
            emptyText: tableEmpty(
              filters.status === TH_STATUS.CONFIRMING
                ? '没有待确认的退货；在售后单选择「仅退货」或「退货退款」后会出现在这里'
                : '没有符合条件的销售退货单',
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
    </div>
  );
};

export default SalesReturnsPage;
