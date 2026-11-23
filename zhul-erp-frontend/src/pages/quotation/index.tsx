import { history } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  DatePicker,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
} from 'antd';
import type { Dayjs } from 'dayjs';
import {
  Eye,
  MessageSquareText,
  Pencil,
  Plus,
  Search,
  SlidersHorizontal,
  Trash2,
} from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerCell } from '@/components/DocFields';
import SourceNos from '@/components/DocFields/SourceNos';
import { ellipsisRender } from '@/components/EllipsisText';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
  SummaryBar,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { IconButton } from '@/components/Shell';
import {
  FirstModelCell,
  fetchAllModels,
  ModalTitle,
  PagedModelsModal,
  TagList,
} from '@/pages/inquiry/customer-inquiry/ModelCells';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { getUserOptions } from '@/services/zhul/users';
import { useAppTheme } from '@/theme/AppTheme';
import { recentRange } from '@/utils/dateRange';
import { formatAmount, formatDateTime } from '@/utils/format';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { formatMargin } from './calc';
import {
  CURRENCIES,
  PATHS,
  Pill,
  QuotationStatusPill,
  STATUS,
  STATUS_META,
} from './components';
import { ListTextQuote } from './dialogs';
import NewQuotationModal from './NewQuotationModal';
import {
  type QuotationListItem,
  type QuotationQuery,
  type QuotationStats,
  quotationApi,
  readBizError,
} from './service';

interface Filters {
  keyword?: string;
  status?: number;
  currencyCode?: string;
  ownerId?: number;
  range?: [Dayjs, Dayjs];
}

/** 帮助文字：help-texts.md「报价管理列表」，关键词范围以 search-scope.md 为准，统计口径取自 QuotationServiceImpl.stats() */
export const HELP = {
  keyword:
    '可搜：报价单编号、客户名称或简称、型号（只搜当前版本）。包含匹配，整段文字一起匹配',
  created: '默认最近 3 个月，清空后看全部',
  monthWon:
    '本月成交或部分成交的报价单（按成交时间）；成交金额、净利润按人民币合计',
  monthMargin: '本月成交报价单的毛利率平均值（各单直接平均，不按金额加权）',
  belowFloor: '本月新建的报价单里，有型号毛利率低于定价策略红线的张数',
} as const;

const QuotationList: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [form] = Form.useForm<Filters>();
  // 创建日期默认最近 3 个月，清空日期看全部
  const [defaults] = useState<Filters>(() => ({ range: recentRange() }));
  const [filters, setFilters] = useState<Filters>(defaults);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<QuotationListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [stats, setStats] = useState<QuotationStats>();
  const [owners, setOwners] = useState<{ value: number; label: string }[]>([]);
  const [newOpen, setNewOpen] = useState(false);
  /** 「查看」全部型号的报价单 */
  const [viewing, setViewing] = useState<QuotationListItem>();
  /** 正在打开文字报价的报价单 */
  const [textId, setTextId] = useState<number>();

  /** 型号数、总数量、合计的排序（后端排序，翻页后保持） */
  const [sort, setSort] = useState<{
    field?: string;
    order?: 'ascend' | 'descend';
  }>({});
  const sortOf = (field: string) => (sort.field === field ? sort.order : null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    const q: QuotationQuery = {
      keyword: filters.keyword?.trim() || undefined,
      status: filters.status,
      currencyCode: filters.currencyCode,
      ownerId: filters.ownerId,
      createdFrom: filters.range?.[0]?.format('YYYY-MM-DD'),
      createdTo: filters.range?.[1]?.format('YYYY-MM-DD'),
      sortField: sort.order ? sort.field : undefined,
      sortOrder: sort.order,
      page,
      pageSize,
    };
    try {
      const res = await quotationApi.page(q);
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [filters, page, pageSize, sort]);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  useEffect(() => {
    quotationApi
      .stats()
      .then(setStats)
      .catch(() => setStats(undefined));
    getUserOptions()
      .then((res) =>
        setOwners(res.map((u) => ({ value: u.id, label: u.name }))),
      )
      .catch(() => setOwners([]));
  }, []);

  /** 按表单当前的条件查询 */
  const searchNow = () => {
    setFilters(form.getFieldsValue());
    setPage(1);
  };

  const remove = async (id: number) => {
    try {
      await quotationApi.remove(id);
      message.success('草稿已删除');
      load();
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const noFilter =
    !filters.keyword &&
    !filters.status &&
    !filters.currencyCode &&
    !filters.ownerId &&
    !filters.range;

  const sub = (text: React.ReactNode) => (
    <div style={{ fontSize: 12, color: palette.mute }}>{text}</div>
  );

  const columns: TableColumnsType<QuotationListItem> = [
    {
      title: '报价单编号 · 创建日期',
      dataIndex: 'quotationNo',
      width: 190,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <button
            type="button"
            className="zhul-link"
            onClick={() => history.push(PATHS.detail(r.id))}
          >
            {v}
          </button>
          {r.editingVersionNo ? (
            <div style={{ marginTop: 4 }}>
              <Pill tone="orange">Rev.{r.editingVersionNo} 编辑中</Pill>
            </div>
          ) : (r.currentVersionNo ?? 1) > 1 ? (
            <div style={{ marginTop: 4 }}>
              <Pill tone="gray">Rev.{r.currentVersionNo}</Pill>
            </div>
          ) : null}
          {sub(r.createTime?.slice(0, 10) ?? '—')}
        </div>
      ),
    },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 240,
      render: (v: string, r) => (
        <CustomerCell
          name={v}
          country={r.customerCountry}
          contact={r.customerContact}
          type={r.customerType}
        />
      ),
    },
    // 型号与总数量分两列（同询盘列表）：主规范要求两者都能排序，合成一列会丢掉一种排序
    {
      title: '型号',
      dataIndex: 'itemCount',
      sorter: true,
      sortOrder: sortOf('itemCount'),
      width: 190,
      render: (_, r) => (
        <FirstModelCell
          model={r.firstModel}
          count={r.itemCount}
          onView={() => setViewing(r)}
        />
      ),
    },
    {
      title: '总数量',
      dataIndex: 'totalQuantity',
      sorter: true,
      sortOrder: sortOf('totalQuantity'),
      width: 90,
      align: 'right',
      render: (v?: number) => v ?? '—',
    },
    {
      title: '品牌',
      key: 'brands',
      width: 160,
      render: (_, r) => <TagList values={r.brands} label="品牌" />,
    },
    {
      title: '品类',
      key: 'categories',
      width: 160,
      render: (_, r) => <TagList values={r.categories} label="品类" />,
    },
    {
      title: '合计',
      dataIndex: 'totalAmount',
      sorter: true,
      sortOrder: sortOf('totalAmount'),
      width: 150,
      align: 'right',
      render: (v: number, r) => (
        <b style={{ fontVariantNumeric: 'tabular-nums' }}>
          {formatAmount(v, r.currencyCode)}
        </b>
      ),
    },
    {
      title: '毛利率',
      dataIndex: 'marginRate',
      width: 90,
      align: 'right',
      render: (v?: number | null) => (
        <span style={{ fontVariantNumeric: 'tabular-nums' }}>
          {formatMargin(v)}
        </span>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 150,
      render: (v: number, r) => (
        <Space orientation="vertical" size={2}>
          <QuotationStatusPill status={v} />
          {r.lostReasonName && (
            <span style={{ fontSize: 12, color: palette.mute }}>
              {r.lostReasonName.replace(/^【[^】]*】/, '')}
            </span>
          )}
        </Space>
      ),
    },
    {
      title: '来源单号',
      key: 'inquiries',
      width: 190,
      render: (_, r) => (
        <SourceNos
          refs={r.inquiries.map((x) => ({
            no: x.no,
            href: `/inquiry/customer-inquiries/${x.id}`,
          }))}
        />
      ),
    },
    {
      title: '业务员',
      dataIndex: 'ownerName',
      width: 100,
      render: ellipsisRender,
    },
    {
      title: '发送时间',
      dataIndex: 'sentAt',
      width: 170,
      render: (v?: string) => formatDateTime(v),
    },
    ...auditColumns<QuotationListItem>(),
    {
      title: '操作',
      key: 'actions',
      // 单元格左右各留 16：三个 32 的图标加间距 4
      width: 140,
      fixed: 'right',
      render: (_, r) => (
        <span style={{ display: 'inline-flex', gap: 4 }}>
          {r.status === STATUS.DRAFT ? (
            <IconButton
              label="编辑"
              size={32}
              icon={<Pencil size={18} />}
              onClick={() => history.push(PATHS.detail(r.id))}
            />
          ) : (
            <IconButton
              label="查看"
              size={32}
              icon={<Eye size={18} />}
              onClick={() => history.push(PATHS.detail(r.id))}
            />
          )}
          <IconButton
            label="文字报价"
            size={32}
            icon={<MessageSquareText size={18} />}
            onClick={() => setTextId(r.id)}
          />
          {r.status === STATUS.DRAFT && (
            <Popconfirm
              title="删除这张草稿？"
              description="删除后不能恢复，询盘里的型号可以重新报价"
              okText="删除"
              okButtonProps={{ danger: true }}
              onConfirm={() => remove(r.id)}
            >
              <span>
                <IconButton
                  label="删除"
                  size={32}
                  icon={<Trash2 size={18} />}
                  style={{ color: palette.red }}
                />
              </span>
            </Popconfirm>
          )}
        </span>
      ),
    },
  ];

  /** 搜索条件里的下拉：标签在上，读屏名称与标签相同 */
  const select = (
    name: keyof Filters,
    label: string,
    options: { value: string | number; label: string }[],
    extra?: React.ComponentProps<typeof Select>,
  ) => (
    <FilterField label={label}>
      <Form.Item name={name} noStyle>
        <Select
          allowClear
          placeholder="全部"
          aria-label={label}
          options={options}
          {...extra}
        />
      </Form.Item>
    </FilterField>
  );

  /** 下拉选项后面带数量（统计卡片并入状态下拉，stat-cards.md） */
  const withCount = (label: string, n?: number) =>
    n ? `${label} · ${n}` : label;

  const counts: Record<number, number | undefined> = {
    [STATUS.DRAFT]: stats?.draftCount,
    [STATUS.SENT]: stats?.sentCount,
  };

  return (
    <div>
      <PageHeader
        extra={
          <>
            <Button
              type="text"
              icon={<SlidersHorizontal size={16} />}
              style={{ color: palette.link }}
              onClick={() => history.push(PATHS.pricing)}
            >
              定价策略
            </Button>
            <Button
              type="primary"
              icon={<Plus size={18} />}
              onClick={() => setNewOpen(true)}
            >
              新建报价单
            </Button>
          </>
        }
      />

      {stats && (
        <SummaryBar
          items={[
            {
              key: 'won',
              label: '本月成交',
              help: HELP.monthWon,
              value: stats.monthWonCount,
              sub: `成交金额 ${formatAmount(stats.monthWonAmountCny, 'CNY')} · 净利润 ${formatAmount(stats.monthWonNetProfitCny, 'CNY')}`,
            },
            {
              key: 'margin',
              label: '本月平均毛利率',
              help: HELP.monthMargin,
              value: formatMargin(stats.monthAvgMargin),
            },
            {
              key: 'floor',
              label: '本月低于红线',
              help: HELP.belowFloor,
              value: stats.monthBelowFloorCount,
            },
          ]}
        />
      )}

      <Form form={form} initialValues={defaults} component={false}>
        <FilterCard
          onSearch={searchNow}
          onReset={() => {
            form.resetFields();
            setFilters(defaults);
            setPage(1);
          }}
          // 列宽 110：关键词、状态、创建日期排一行
          minColumn={110}
          moreCount={
            [filters.currencyCode, filters.ownerId].filter(Boolean).length
          }
          more={
            <>
              {select(
                'currencyCode',
                '币种',
                CURRENCIES.map((c) => ({ value: c, label: c })),
              )}
              {select('ownerId', '业务员', owners, {
                showSearch: { optionFilterProp: 'label' },
              })}
            </>
          }
        >
          <FilterField label="关键词" span={2} help={HELP.keyword}>
            <Form.Item name="keyword" noStyle>
              <Input
                allowClear
                prefix={<Search size={16} />}
                placeholder="报价单编号、客户、型号"
                aria-label="关键词"
                onPressEnter={searchNow}
              />
            </Form.Item>
          </FilterField>
          {select(
            'status',
            '状态',
            Object.entries(STATUS_META).map(([k, v]) => ({
              value: Number(k),
              label: withCount(v.label, counts[Number(k)]),
            })),
            // 切换状态立即查询（doc-list-unify 状态下拉的写法）
            { onChange: () => searchNow() },
          )}
          <FilterField
            label="创建日期"
            span={2}
            help={HELP.created}
            htmlFor="quotation-created"
          >
            <Form.Item name="range" noStyle>
              <DatePicker.RangePicker
                id={{ start: 'quotation-created' }}
                style={{ width: '100%' }}
              />
            </Form.Item>
          </FilterField>
        </FilterCard>
      </Form>

      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : !loading && rows.length === 0 && noFilter ? (
        <MainCard>
          <EmptyHint
            title="还没有报价单"
            description="从询盘单点「去报价」，或在这里新建报价单，挑选要报的型号"
            actionText="新建报价单"
            onAction={() => setNewOpen(true)}
          />
        </MainCard>
      ) : (
        <MainCard left={`共 ${total} 条`}>
          <Table<QuotationListItem>
            rowKey="id"
            onChange={(_p, _f, sorter, extra) => {
              if (extra.action !== 'sort') return;
              const one = Array.isArray(sorter) ? sorter[0] : sorter;
              setSort({
                field: one?.order ? String(one.field) : undefined,
                order: one?.order ?? undefined,
              });
              setPage(1);
            }}
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: tableScrollX(columns) }}
            locale={{ emptyText: '没有符合条件的报价单，换个筛选条件试试' }}
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
      )}

      <PagedModelsModal
        openId={viewing?.id}
        showPrice
        title={
          <ModalTitle
            count={viewing?.itemCount}
            sub={`${viewing?.quotationNo ?? ''} · ${viewing?.customerName ?? ''}`}
          />
        }
        note="按报价单明细的顺序"
        fetchPage={(p, s) => quotationApi.models(viewing?.id as number, p, s)}
        fetchAll={() =>
          fetchAllModels((p, s) =>
            quotationApi.models(viewing?.id as number, p, s),
          )
        }
        onOpenDetail={() => viewing && history.push(PATHS.detail(viewing.id))}
        onClose={() => setViewing(undefined)}
      />
      <ListTextQuote
        id={textId}
        onClose={() => setTextId(undefined)}
        onSent={load}
      />
      <NewQuotationModal
        open={newOpen}
        onClose={() => setNewOpen(false)}
        onBatchDone={load}
        onCreated={(q) => {
          setNewOpen(false);
          history.push(PATHS.detail(q.id));
        }}
      />
    </div>
  );
};

export default QuotationList;
