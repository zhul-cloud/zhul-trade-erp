import { history, useSearchParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  App,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
} from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import {
  ClipboardCheck,
  Eye,
  PencilLine,
  Plus,
  RotateCcw,
  Search,
  Sparkles,
} from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { useColumnSettings } from '@/components/ColumnSettings';
import { CustomerCell } from '@/components/DocFields';
import { ellipsisRender } from '@/components/EllipsisText';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  HeadHelp,
  MainCard,
  PageHeader,
  QuickFilters,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { getUserOptions } from '@/services/zhul/users';
import { useAppTheme } from '@/theme/AppTheme';
import { recentRange } from '@/utils/dateRange';
import { DICT_SOURCE_CHANNEL, useDictOptions } from '@/utils/dict';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import {
  DeadlineText,
  LevelPill,
  MigratedPill,
  Pill,
  Progress,
  StatusPill,
  useLevels,
} from '../shared/components';
import { PATHS, STATUS, STATUS_META } from '../shared/constants';
import {
  type CustomerInquiry,
  type InquiryQuery,
  inquiryApi,
  readBizError,
} from '../shared/service';
import { ModelCell, ModelsModal, TagList } from './ModelCells';
import NewInquiryModal from './NewInquiryModal';

interface Filters {
  keyword?: string;
  status?: number;
  source?: number;
  ownerId?: number;
  customerType?: number;
  level?: number;
  range?: [Dayjs, Dayjs];
  /** 型号数、总数量范围：[最少, 最多]，留空表示不限 */
  itemCount?: NumberRange;
  totalQuantity?: NumberRange;
}

type NumberRange = [number | null | undefined, number | null | undefined];

interface Sort {
  field?: 'level' | 'totalItemCount' | 'totalQuantity';
  order?: 'asc' | 'desc';
}

/** 数字范围输入：最少 ~ 最多，任一端可留空 */
const RangeInput: React.FC<{
  value?: NumberRange;
  onChange?: (v: NumberRange) => void;
  label?: string;
}> = ({ value, onChange, label }) => (
  <Space.Compact style={{ width: '100%' }}>
    <InputNumber
      min={0}
      precision={0}
      placeholder="最少"
      aria-label={label ? `${label}最少` : undefined}
      style={{ width: '50%' }}
      value={value?.[0] ?? null}
      onChange={(v) => onChange?.([v, value?.[1]])}
    />
    <InputNumber
      min={0}
      precision={0}
      placeholder="最多"
      aria-label={label ? `${label}最多` : undefined}
      style={{ width: '50%' }}
      value={value?.[1] ?? null}
      onChange={(v) => onChange?.([value?.[0], v])}
    />
  </Space.Compact>
);

/**
 * 进页面时的筛选：询盘日期默认最近 3 个月；从询价统计跳转时用 URL 带的日期。
 * 从工作台待办进来带 status（待确认、可以报价）：只按状态筛，不限日期，与待办数字同口径。
 */
export const initialFilters = (): Filters => {
  const p = new URLSearchParams(window.location.search);
  const from = p.get('dateFrom');
  const to = p.get('dateTo');
  if (from && to) return { range: [dayjs(from), dayjs(to)] };
  const status = Number(p.get('status'));
  if (status > 0) return { status };
  // 供应链需求详情「去询价」带型号：按型号查全部询盘，不限日期（audit UI-088）
  const keyword = p.get('keyword')?.trim();
  if (keyword) return { keyword };
  return { range: recentRange() };
};

const toQuery = (
  f: Filters,
  urgentOnly: boolean,
  timeoutOnly: boolean,
  sort: Sort = {},
  todayOnly = false,
): InquiryQuery => ({
  keyword: f.keyword?.trim() || undefined,
  statusList: f.status ? [f.status] : undefined,
  source: f.source,
  ownerId: f.ownerId,
  customerType: f.customerType,
  level: f.level,
  // 快捷筛选「今日新增」：按询盘日期取今天，与统计同口径
  inquiryDateFrom: todayOnly
    ? dayjs().format('YYYY-MM-DD')
    : f.range?.[0]?.format('YYYY-MM-DD'),
  inquiryDateTo: todayOnly
    ? dayjs().format('YYYY-MM-DD')
    : f.range?.[1]?.format('YYYY-MM-DD'),
  urgentOnly: urgentOnly || undefined,
  timeoutOnly: timeoutOnly || undefined,
  minItemCount: f.itemCount?.[0] ?? undefined,
  maxItemCount: f.itemCount?.[1] ?? undefined,
  minTotalQuantity: f.totalQuantity?.[0] ?? undefined,
  maxTotalQuantity: f.totalQuantity?.[1] ?? undefined,
  sortField: sort.field,
  sortOrder: sort.field ? sort.order : undefined,
});

interface Stats {
  sourcing: number;
  ready: number;
  pendingConfirm: number;
  today: number;
}

/** 快捷筛选 */
const QUICK_TODAY = 'today';
const QUICK_URGENT = 'urgent';
const QUICK_TIMEOUT = 'timeout';

/** 帮助文字：help-texts.md「询盘管理列表」，关键词范围以 search-scope.md 为准 */
export const HELP = {
  keyword:
    '可搜：询盘编号、客户名称或联系人、型号（规范化后匹配）。包含匹配，整段文字一起匹配',
  inquiryDate: '默认最近 3 个月，清空后看全部',
  models:
    '显示第一个型号；「共 N 个」是询盘里有几个不同的型号，不是件数，也不是询价任务数（一个询价任务可能包含多个型号）',
  quantity:
    '所有型号的数量直接相加（不区分单位），和型号数一起判断询盘价值：多型号、多数量的询盘通常更值得优先跟进',
} as const;

const CustomerInquiryPage: React.FC = () => {
  const { options: sourceOptions, labelOf: sourceLabel } =
    useDictOptions(DICT_SOURCE_CHANNEL);
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [searchParams, setSearchParams] = useSearchParams();
  const [form] = Form.useForm<Filters>();
  const [filters, setFilters] = useState<Filters>(initialFilters);
  const [urgentOnly, setUrgentOnly] = useState(false);
  const [timeoutOnly, setTimeoutOnly] = useState(false);
  const [todayOnly, setTodayOnly] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [sort, setSort] = useState<Sort>({});
  const { levelOptions } = useLevels();
  const [rows, setRows] = useState<CustomerInquiry[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [stats, setStats] = useState<Stats | null>(null);
  const [owners, setOwners] = useState<{ value: number; label: string }[]>([]);
  const [newOpen, setNewOpen] = useState(false);
  const [presetCustomer, setPresetCustomer] = useState<number>();
  const [busyId, setBusyId] = useState<number>();
  const [viewing, setViewing] = useState<CustomerInquiry>();

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await inquiryApi.page({
        ...toQuery(filters, urgentOnly, timeoutOnly, sort, todayOnly),
        page,
        pageSize,
      });
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [filters, urgentOnly, timeoutOnly, todayOnly, sort, page, pageSize]);

  // 统计跟当前筛选条件走；状态、只看超时由各卡片自己定，否则点了一张卡片其余都变 0
  const loadStats = useCallback(async () => {
    const base = toQuery({ ...filters, status: undefined }, urgentOnly, false);
    const count = (q: InquiryQuery) =>
      inquiryApi
        .page({ ...base, ...q, page: 1, pageSize: 1 })
        .then((r) => r.total);
    const today = dayjs().format('YYYY-MM-DD');
    try {
      const [sourcing, readyCount, pendingConfirm, todayCount] =
        await Promise.all([
          count({ statusList: [STATUS.SOURCING] }),
          count({ statusList: [STATUS.READY] }),
          count({ statusList: [STATUS.PENDING_CONFIRM] }),
          count({ inquiryDateFrom: today, inquiryDateTo: today }),
        ]);
      setStats({
        sourcing,
        ready: readyCount,
        pendingConfirm,
        today: todayCount,
      });
    } catch {
      setStats(null);
    }
  }, [filters, urgentOnly]);

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  useEffect(() => {
    loadStats();
  }, [loadStats]);

  useEffect(() => {
    getUserOptions()
      .then((res) => {
        setOwners(res.map((u) => ({ value: u.id, label: u.name })));
      })
      .catch(() => setOwners([]));
    // 从商机查重提示跳过来（?newForCustomer=客户ID）：直接打开新建弹窗并选好客户
    const preset = Number(searchParams.get('newForCustomer'));
    if (preset) {
      setPresetCustomer(preset);
      setNewOpen(true);
      searchParams.delete('newForCustomer');
      setSearchParams(searchParams, { replace: true });
    }
  }, []);

  const applyFilters = () => {
    setPage(1);
    setFilters(form.getFieldsValue());
  };

  const run = async (
    id: number,
    fn: () => Promise<unknown>,
    ok: string,
    then?: () => void,
  ) => {
    setBusyId(id);
    try {
      await fn();
      message.success(ok);
      if (then) then();
      else {
        load();
        loadStats();
      }
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setBusyId(undefined);
    }
  };

  const sortOrderOf = (field: Sort['field']) =>
    sort.field === field
      ? sort.order === 'asc'
        ? ('ascend' as const)
        : ('descend' as const)
      : null;

  const sub = (text: React.ReactNode, color: string = palette.mute) => (
    <div style={{ fontSize: 12, color }}>{text}</div>
  );

  const columns: TableColumnsType<CustomerInquiry> = [
    {
      title: '询盘编号 · 询盘日期',
      dataIndex: 'inquiryCode',
      width: 180,
      fixed: 'left',
      render: (v, r) => (
        <div>
          <button
            type="button"
            className="zhul-link"
            onClick={() => history.push(`${PATHS.inquiries}/${r.id}`)}
          >
            {v}
          </button>
          {(r.urgent || r.migrated) && (
            <div style={{ marginTop: 4, display: 'flex', gap: 4 }}>
              {r.urgent && <Pill tone="red">紧急</Pill>}
              <MigratedPill migrated={r.migrated} />
            </div>
          )}
          {sub(r.inquiryDate ?? '—')}
        </div>
      ),
    },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 240,
      render: (v, r) => (
        <CustomerCell
          name={v}
          country={r.customerCountry}
          type={r.customerType}
          contact={r.customerContact}
        />
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (v, r) => (
        <span
          style={{
            display: 'inline-flex',
            flexDirection: 'column',
            gap: 4,
            alignItems: 'flex-start',
          }}
        >
          <StatusPill status={v} />
          {r.needsReview && <Pill tone="orange">型号待核实</Pill>}
        </span>
      ),
    },
    {
      title: '回价进度',
      width: 190,
      render: (_, r) => {
        if (r.status === STATUS.PARSE_FAILED)
          return sub('AI 没有返回结果', palette.red);
        if (r.status === STATUS.PENDING_PARSE) return sub('等待解析或手动录入');
        if (r.status === STATUS.PARSING) return sub('解析完成后显示');
        if (r.status === STATUS.PENDING_CONFIRM)
          return sub('确认型号后开始询价');
        if (!r.totalItemCount)
          return <span style={{ color: palette.mute }}>—</span>;
        return (
          <span
            style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
          >
            <Progress
              done={r.pricedItemCount}
              total={r.totalItemCount}
              width={80}
            />
            {r.timeoutTaskCount > 0 && (
              <Pill tone="red">超时 {r.timeoutTaskCount}</Pill>
            )}
          </span>
        );
      },
    },
    {
      title: '报价截止',
      dataIndex: 'quoteDeadline',
      width: 110,
      render: (v, r) => <DeadlineText date={v} status={r.status} />,
    },
    {
      title: '等级',
      dataIndex: 'level',
      width: 90,
      render: (v) => <LevelPill value={v} />,
      sorter: true,
      sortDirections: ['descend', 'ascend'],
      sortOrder: sortOrderOf('level'),
    },
    {
      title: <HeadHelp title="型号" help={HELP.models} />,
      dataIndex: 'totalItemCount',
      width: 200,
      render: (_, r) => {
        // 未解析、解析失败，或没录型号就取消的询盘没有型号
        if (
          r.status <= STATUS.PARSE_FAILED &&
          r.status !== STATUS.PENDING_CONFIRM
        )
          return '—';
        return (
          <span
            style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}
          >
            <ModelCell r={r} onView={setViewing} />
            {r.status === STATUS.PENDING_CONFIRM &&
              r.firstModel &&
              sub('AI 识别，待确认')}
          </span>
        );
      },
      sorter: true,
      sortDirections: ['descend', 'ascend'],
      showSorterTooltip: { target: 'sorter-icon' },
      sortOrder: sortOrderOf('totalItemCount'),
    },
    {
      title: <HeadHelp title="总数量" help={HELP.quantity} />,
      dataIndex: 'totalQuantity',
      width: 110,
      align: 'right',
      render: (v, r) => {
        if (r.status <= STATUS.PARSE_FAILED || !r.totalItemCount) return '—';
        if (r.status === STATUS.PENDING_CONFIRM)
          return (
            <span style={{ display: 'inline-flex', flexDirection: 'column' }}>
              <span>{v}</span>
              {sub('AI 识别，待确认')}
            </span>
          );
        return v;
      },
      sorter: true,
      sortDirections: ['descend', 'ascend'],
      showSorterTooltip: { target: 'sorter-icon' },
      sortOrder: sortOrderOf('totalQuantity'),
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
      title: '来源',
      dataIndex: 'source',
      width: 130,
      render: (v) => (
        <span style={{ whiteSpace: 'nowrap' }}>{sourceLabel(v)}</span>
      ),
    },
    {
      title: '负责人',
      dataIndex: 'ownerName',
      width: 90,
      render: (v) => ellipsisRender(v || '—'),
    },
    ...auditColumns<CustomerInquiry>(),
    {
      title: '操作',
      key: 'actions',
      width: rowActionsWidth(2),
      fixed: 'right',
      render: (_, r) => {
        const busy = busyId === r.id;
        if (
          r.status === STATUS.PENDING_PARSE ||
          r.status === STATUS.PARSE_FAILED
        ) {
          const pending = r.status === STATUS.PENDING_PARSE;
          return (
            <RowActions
              inline={[
                {
                  key: 'parse',
                  label: pending ? 'AI 解析' : '重新解析',
                  icon: pending ? (
                    <Sparkles size={18} />
                  ) : (
                    <RotateCcw size={18} />
                  ),
                  disabledReason: busy ? '正在处理' : undefined,
                  onClick: () =>
                    run(
                      r.id,
                      () =>
                        pending
                          ? inquiryApi.startParse(r.id)
                          : inquiryApi.retryParse(r.id),
                      '已开始 AI 解析，完成后状态会变为待确认',
                    ),
                },
                // 手动录入只打开确认页，不改状态；确认时才离开待解析 / 解析失败
                {
                  key: 'manual',
                  label: '手动录入',
                  icon: <PencilLine size={18} />,
                  disabledReason: busy ? '正在处理' : undefined,
                  onClick: () =>
                    history.push(`${PATHS.inquiries}/${r.id}/confirm`),
                },
              ]}
            />
          );
        }
        return (
          <RowActions
            inline={[
              {
                key: 'view',
                label: '查看',
                icon: <Eye size={18} />,
                onClick: () => history.push(`${PATHS.inquiries}/${r.id}`),
              },
              r.status === STATUS.PENDING_CONFIRM && {
                key: 'confirm',
                label: '去确认',
                icon: <ClipboardCheck size={18} color={palette.link} />,
                onClick: () =>
                  history.push(`${PATHS.inquiries}/${r.id}/confirm`),
              },
            ]}
          />
        );
      },
    },
  ];
  const columnsSet = useColumnSettings('inquiry-customer-inquiry', columns, {
    hidden: ['报价截止', '等级', '总数量', '品牌', '品类', '来源', '负责人'],
  });

  const noFilter =
    !filters.keyword &&
    !filters.status &&
    !filters.source &&
    !filters.ownerId &&
    !filters.customerType &&
    !filters.range &&
    !urgentOnly &&
    !timeoutOnly &&
    !todayOnly;

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
    [STATUS.SOURCING]: stats?.sourcing,
    [STATUS.READY]: stats?.ready,
    [STATUS.PENDING_CONFIRM]: stats?.pendingConfirm,
  };

  const moreCount = [
    filters.level,
    filters.source,
    filters.ownerId,
    filters.customerType,
    filters.itemCount?.some((x) => x != null) || undefined,
    filters.totalQuantity?.some((x) => x != null) || undefined,
  ].filter(Boolean).length;

  return (
    <div>
      <PageHeader
        extra={
          <Button
            type="primary"
            icon={<Plus size={18} />}
            onClick={() => {
              setPresetCustomer(undefined);
              setNewOpen(true);
            }}
          >
            新建询盘单
          </Button>
        }
      />

      <Form form={form} initialValues={initialFilters()} component={false}>
        <FilterCard
          onSearch={applyFilters}
          onReset={() => {
            // 恢复默认：最近 3 个月；看得到哪些询盘由数据权限决定
            const defaults: Filters = {
              range: recentRange(),
              status: undefined,
            };
            form.resetFields();
            form.setFieldsValue(defaults);
            setUrgentOnly(false);
            setTimeoutOnly(false);
            setTodayOnly(false);
            setPage(1);
            setSort({});
            setFilters(defaults);
          }}
          // 列宽 110：关键词、状态、询盘日期排一行
          minColumn={110}
          moreCount={moreCount}
          more={
            <>
              {select('level', '询盘等级', levelOptions)}
              {select('source', '来源', sourceOptions)}
              {select('ownerId', '负责人', owners, {
                showSearch: { optionFilterProp: 'label' },
              })}
              {select('customerType', '新老客户', [
                { value: 1, label: '新客户' },
                { value: 2, label: '老客户' },
              ])}
              <FilterField label="型号数" span={2}>
                <Form.Item name="itemCount" noStyle>
                  <RangeInput label="型号数" />
                </Form.Item>
              </FilterField>
              <FilterField label="总数量" span={2}>
                <Form.Item name="totalQuantity" noStyle>
                  <RangeInput label="总数量" />
                </Form.Item>
              </FilterField>
            </>
          }
        >
          <FilterField label="关键词" span={2} help={HELP.keyword}>
            <Form.Item name="keyword" noStyle>
              <Input
                allowClear
                prefix={<Search size={16} />}
                placeholder="询盘编号、客户、型号"
                aria-label="关键词"
                onPressEnter={applyFilters}
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
            { onChange: () => applyFilters() },
          )}
          <FilterField
            label="询盘日期"
            span={2}
            help={HELP.inquiryDate}
            htmlFor="inquiry-date"
          >
            <Form.Item name="range" noStyle>
              <DatePicker.RangePicker
                id={{ start: 'inquiry-date' }}
                style={{ width: '100%' }}
                // 「今日新增」打开时日期固定取今天，日期框置灰，避免看起来还按这里的范围查
                disabled={todayOnly}
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
            title="还没有询盘单"
            description="把客户发来的文字、截图或 Excel 交给 AI 解析，几十秒就能拆好给采购。"
            actionText="新建询盘单"
            onAction={() => setNewOpen(true)}
          />
        </MainCard>
      ) : (
        <MainCard
          settings={columnsSet.settings}
          count={total}
          loading={loading}
          quick={
            <QuickFilters
              options={[
                {
                  value: QUICK_TODAY,
                  label: '今日新增',
                  count: stats?.today,
                },
                { value: QUICK_URGENT, label: '紧急' },
                { value: QUICK_TIMEOUT, label: '有超时任务' },
              ]}
              value={[
                ...(todayOnly ? [QUICK_TODAY] : []),
                ...(urgentOnly ? [QUICK_URGENT] : []),
                ...(timeoutOnly ? [QUICK_TIMEOUT] : []),
              ]}
              onChange={(v) => {
                setTodayOnly(v.includes(QUICK_TODAY));
                setUrgentOnly(v.includes(QUICK_URGENT));
                setTimeoutOnly(v.includes(QUICK_TIMEOUT));
                setPage(1);
              }}
            />
          }
        >
          <Table<CustomerInquiry>
            rowKey="id"
            columns={columnsSet.columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: tableScrollX(columnsSet.columns) }}
            onChange={(_, __, sorter, extra) => {
              // 翻页也会触发 onChange：只处理排序，否则会把页码重置回 1
              if (extra.action !== 'sort') return;
              const s = Array.isArray(sorter) ? sorter[0] : sorter;
              const field = s?.order ? (s.field as Sort['field']) : undefined;
              setSort(
                field
                  ? { field, order: s.order === 'ascend' ? 'asc' : 'desc' }
                  : {},
              );
              setPage(1);
            }}
            locale={{
              emptyText: tableEmpty(
                '没有符合条件的询盘，换个筛选条件试试',
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
      )}

      <ModelsModal target={viewing} onClose={() => setViewing(undefined)} />
      <NewInquiryModal
        open={newOpen}
        presetCustomerId={presetCustomer}
        onClose={() => setNewOpen(false)}
        onCreated={async (created, startParse) => {
          setNewOpen(false);
          if (startParse) {
            await run(
              created.id,
              () => inquiryApi.startParse(created.id),
              `${created.inquiryCode} 已提交，AI 正在解析`,
            );
          } else {
            message.success(
              `${created.inquiryCode} 已保存，可以 AI 解析或手动录入`,
            );
            load();
            loadStats();
          }
        }}
      />
    </div>
  );
};

export default CustomerInquiryPage;
