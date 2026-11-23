import { history, useSearchParams } from '@umijs/max';
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
  Tooltip,
} from 'antd';
import type { Dayjs } from 'dayjs';
import { Eye, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerCell } from '@/components/DocFields';
import SourceNos from '@/components/DocFields/SourceNos';
import { ellipsisRender } from '@/components/EllipsisText';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
  QuickFilters,
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
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import {
  PATHS,
  PI_STATUS,
  PI_STATUS_META,
  Pill,
  PiStatusPill,
  RECEIPT_META,
  RECEIPT_STATUS,
  ReceiptStatusPill,
} from '../components';
import {
  type PiListItem,
  type PiQuery,
  type PiStats,
  piApi,
  readBizError,
} from '../service';
import NewPiModal from './NewPiModal';

interface Filters {
  keyword?: string;
  status?: number;
  receiptStatus?: number;
  ownerId?: number;
  range?: [Dayjs, Dayjs];
  /** 只看已过期未收款（工作台「查看全部」带过来） */
  expiredUnpaid?: boolean;
  /** 只看待收款：已发送或已转订单、还没付清（工作台「PI 待收款」带过来） */
  unpaid?: boolean;
}

/** 从工作台跳来时的快捷筛选（不限创建日期，与工作台数字同口径）；没有时返回 undefined，用默认筛选 */
export const quickFromUrl = (search: URLSearchParams): Filters | undefined => {
  if (search.get('expired') === '1') return { expiredUnpaid: true };
  if (search.get('unpaid') === '1') return { unpaid: true };
  return undefined;
};

/** 快捷筛选 */
const QUICK_UNPAID = 'unpaid';
const QUICK_EXPIRED = 'expired';

/** 帮助文字：help-texts.md「形式发票列表」，关键词范围以 search-scope.md 为准 */
export const HELP = {
  keyword: '可搜：PI 编号、客户名称或简称、型号。包含匹配，整段文字一起匹配',
  created: '默认最近 3 个月，清空后看全部',
  monthReceived: '本月确认到账的金额（按到账日期），各币种折合人民币合计',
  monthOrders: '本月由 PI 转成的销售订单',
  unpaid: '已发送或已转订单、还没付清的 PI',
  expired: '已发送、过了有效期还没收到水单或到账的 PI',
} as const;

const PiList: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const [form] = Form.useForm<Filters>();
  const [search] = useSearchParams();
  // 创建日期默认最近 3 个月，清空日期看全部；从工作台「已过期未收款」「PI 待收款」跳来时看全部
  const [defaults] = useState<Filters>(() => ({ range: recentRange() }));
  const [filters, setFilters] = useState<Filters>(
    () => quickFromUrl(search) ?? defaults,
  );
  const [overdueCount, setOverdueCount] = useState<number>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<PiListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [stats, setStats] = useState<PiStats>();
  const [owners, setOwners] = useState<{ value: number; label: string }[]>([]);
  const [newOpen, setNewOpen] = useState(false);
  /** 「查看」全部型号的 PI */
  const [viewing, setViewing] = useState<PiListItem>();

  /** 型号数、合计的排序（后端排序，翻页后保持） */
  const [sort, setSort] = useState<{
    field?: string;
    order?: 'ascend' | 'descend';
  }>({});
  const sortOf = (field: string) => (sort.field === field ? sort.order : null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    const q: PiQuery = {
      keyword: filters.keyword?.trim() || undefined,
      status: filters.status,
      receiptStatus: filters.receiptStatus,
      expiredUnpaid: filters.expiredUnpaid || undefined,
      unpaid: filters.unpaid || undefined,
      ownerId: filters.ownerId,
      createdFrom: filters.range?.[0]?.format('YYYY-MM-DD'),
      createdTo: filters.range?.[1]?.format('YYYY-MM-DD'),
      sortField: sort.order ? sort.field : undefined,
      sortOrder: sort.order,
      page,
      pageSize,
    };
    try {
      const res = await piApi.page(q);
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
    piApi
      .stats()
      .then(setStats)
      .catch(() => setStats(undefined));
    piApi
      .overdue()
      .then((r) => setOverdueCount(r.count))
      .catch(() => setOverdueCount(undefined));
    getUserOptions()
      .then((res) =>
        setOwners(res.map((u) => ({ value: u.id, label: u.name }))),
      )
      .catch(() => setOwners([]));
  }, []);

  /** 按表单当前的条件查询（快捷筛选不在表单里，沿用当前选择） */
  const searchNow = () => {
    setFilters({
      ...form.getFieldsValue(),
      expiredUnpaid: filters.expiredUnpaid,
      unpaid: filters.unpaid,
    });
    setPage(1);
  };

  const remove = async (id: number) => {
    try {
      await piApi.remove(id);
      message.success('草稿已删除');
      load();
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const noFilter =
    !filters.keyword &&
    !filters.status &&
    !filters.receiptStatus &&
    !filters.expiredUnpaid &&
    !filters.unpaid &&
    !filters.ownerId &&
    !filters.range;

  const sub = (text: React.ReactNode) => (
    <div style={{ fontSize: 12, color: palette.mute }}>{text}</div>
  );

  const columns: TableColumnsType<PiListItem> = [
    {
      title: 'PI 编号 · 创建日期',
      dataIndex: 'piNo',
      width: 170,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <button
            type="button"
            className="zhul-link"
            onClick={() => history.push(PATHS.pi(r.id))}
          >
            {v}
          </button>
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
      title: '收款状态',
      dataIndex: 'receiptStatus',
      width: 110,
      render: (v: number, r) =>
        r.status === PI_STATUS.DRAFT ? '—' : <ReceiptStatusPill status={v} />,
    },
    {
      title: 'PI 状态',
      dataIndex: 'status',
      width: 190,
      render: (v: number, r) => (
        <Space size={6} wrap>
          <PiStatusPill status={v} />
          {r.expired && (
            <Tooltip
              title={`已过期 ${r.expiredDays} 天 · 有效期至 ${r.validUntil} · 未收到水单或到账`}
            >
              <span>
                <Pill tone="red">已过期</Pill>
              </span>
            </Tooltip>
          )}
          {r.closeReasonName && (
            <span style={{ fontSize: 12, color: palette.mute }}>
              {r.closeReasonName.replace(/^【[^】]+】/, '')}
            </span>
          )}
          {r.currentVersionNo > 1 && (
            <span style={{ fontSize: 12, color: palette.sub }}>
              Rev.{r.currentVersionNo}
            </span>
          )}
          {r.revising && <Pill tone="orange">修改中</Pill>}
        </Space>
      ),
    },
    {
      title: '有效期至',
      dataIndex: 'validUntil',
      width: 116,
      render: (v: string | undefined, r) => (
        <span
          style={{
            fontVariantNumeric: 'tabular-nums',
            whiteSpace: 'nowrap',
            color: r.expired
              ? palette.red
              : r.status === PI_STATUS.SENT
                ? palette.sub
                : palette.mute,
          }}
        >
          {v ?? '—'}
        </span>
      ),
    },
    {
      title: '来源单号',
      key: 'quotations',
      width: 200,
      render: (_, r) => (
        <SourceNos
          refs={r.quotations.map((x) => ({
            no: x.no,
            href: PATHS.quotation(x.id),
          }))}
        />
      ),
    },
    {
      title: '销售订单',
      dataIndex: 'soNo',
      width: 150,
      render: (v: string | undefined, r) =>
        v && r.orderId ? (
          <button
            type="button"
            className="zhul-link"
            onClick={() => history.push(PATHS.order(r.orderId as number))}
          >
            {v}
          </button>
        ) : (
          '—'
        ),
    },
    {
      title: '业务员',
      dataIndex: 'ownerName',
      width: 100,
      render: ellipsisRender,
    },
    {
      title: '最近发送',
      dataIndex: 'sentAt',
      width: 170,
      render: (v?: string) => formatDateTime(v),
    },
    ...auditColumns<PiListItem>(),
    {
      title: '操作',
      key: 'actions',
      // 单元格左右各留 16：两个 32 的图标加间距 4
      width: 104,
      fixed: 'right',
      render: (_, r) =>
        r.status === PI_STATUS.DRAFT ? (
          <span style={{ display: 'inline-flex', gap: 4 }}>
            <IconButton
              label="编辑"
              size={32}
              icon={<Pencil size={18} />}
              onClick={() => history.push(PATHS.pi(r.id))}
            />
            <Popconfirm
              title="删除这张草稿？"
              description="删除后不能恢复，报价单里的型号可以重新开 PI"
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
          </span>
        ) : (
          <IconButton
            label="查看"
            size={32}
            icon={<Eye size={18} />}
            onClick={() => history.push(PATHS.pi(r.id))}
          />
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

  return (
    <div>
      <PageHeader
        extra={
          <Button
            type="primary"
            icon={<Plus size={18} />}
            onClick={() => setNewOpen(true)}
          >
            新建 PI
          </Button>
        }
      />

      {stats && (
        <SummaryBar
          items={[
            {
              key: 'received',
              label: '本月到账人民币',
              help: HELP.monthReceived,
              value: stats.monthReceivedCny,
              currency: 'CNY',
              sub:
                Number(stats.monthReceivedUsd) > 0
                  ? `其中美元 ${formatAmount(stats.monthReceivedUsd, 'USD')}`
                  : undefined,
            },
            {
              key: 'orders',
              label: '本月转订单',
              help: HELP.monthOrders,
              value: stats.monthOrderCount,
              sub:
                stats.monthOrderUnpaid > 0
                  ? `凭水单先转、还未到账 ${stats.monthOrderUnpaid} 张`
                  : undefined,
            },
          ]}
        />
      )}

      <Form
        form={form}
        initialValues={{ range: filters.range }}
        component={false}
      >
        <FilterCard
          onSearch={searchNow}
          onReset={() => {
            form.resetFields();
            form.setFieldsValue(defaults);
            setFilters(defaults);
            setPage(1);
          }}
          // 列宽 110：关键词、两个状态、创建日期排一行
          minColumn={110}
          moreCount={filters.ownerId ? 1 : 0}
          more={select('ownerId', '业务员', owners, {
            showSearch: { optionFilterProp: 'label' },
          })}
          quick={
            <QuickFilters
              options={[
                {
                  value: QUICK_UNPAID,
                  label: withCount('待收款', stats?.unpaidCount),
                },
                {
                  value: QUICK_EXPIRED,
                  label: withCount('已过期未收款', overdueCount),
                },
              ]}
              value={[
                ...(filters.unpaid ? [QUICK_UNPAID] : []),
                ...(filters.expiredUnpaid ? [QUICK_EXPIRED] : []),
              ]}
              onChange={(v) => {
                setFilters((f) => ({
                  ...f,
                  unpaid: v.includes(QUICK_UNPAID) || undefined,
                  expiredUnpaid: v.includes(QUICK_EXPIRED) || undefined,
                }));
                setPage(1);
              }}
            />
          }
        >
          <FilterField label="关键词" span={2} help={HELP.keyword}>
            <Form.Item name="keyword" noStyle>
              <Input
                allowClear
                prefix={<Search size={16} />}
                placeholder="PI 编号、客户、型号"
                aria-label="关键词"
                onPressEnter={searchNow}
              />
            </Form.Item>
          </FilterField>
          {select(
            'status',
            'PI 状态',
            Object.entries(PI_STATUS_META).map(([k, v]) => ({
              value: Number(k),
              label:
                Number(k) === PI_STATUS.DRAFT
                  ? withCount(v.label, stats?.draftCount)
                  : v.label,
            })),
            // 切换状态立即查询（doc-list-unify 状态下拉的写法）
            { onChange: () => searchNow() },
          )}
          {select(
            'receiptStatus',
            '收款状态',
            Object.entries(RECEIPT_META).map(([k, v]) => ({
              value: Number(k),
              label:
                Number(k) === RECEIPT_STATUS.SLIP_ONLY
                  ? withCount(v.label, stats?.slipOnlyCount)
                  : v.label,
            })),
            { onChange: () => searchNow() },
          )}
          <FilterField
            label="创建日期"
            span={2}
            help={HELP.created}
            htmlFor="pi-created"
          >
            <Form.Item name="range" noStyle>
              <DatePicker.RangePicker
                id={{ start: 'pi-created' }}
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
            title="还没有 PI"
            description="在已发送的报价单上点「开 PI」，或在这里从客户的多张报价单挑型号"
            actionText="新建 PI"
            onAction={() => setNewOpen(true)}
          />
        </MainCard>
      ) : (
        <MainCard left={`共 ${total} 条`}>
          <Table<PiListItem>
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
            locale={{ emptyText: '没有符合条件的 PI，换个筛选条件试试' }}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
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
            sub={`${viewing?.piNo ?? ''} · ${viewing?.customerName ?? ''}`}
          />
        }
        note="按 PI 明细的顺序"
        fetchPage={(p, s) => piApi.models(viewing?.id as number, p, s)}
        fetchAll={() =>
          fetchAllModels((p, s) => piApi.models(viewing?.id as number, p, s))
        }
        onOpenDetail={() => viewing && history.push(PATHS.pi(viewing.id))}
        onClose={() => setViewing(undefined)}
      />
      <NewPiModal
        open={newOpen}
        onClose={() => setNewOpen(false)}
        onCreated={(pi) => {
          setNewOpen(false);
          history.push(PATHS.pi(pi.id));
        }}
      />
    </div>
  );
};

export default PiList;
