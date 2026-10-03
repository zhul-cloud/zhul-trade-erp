import {
  AlertOutlined,
  CheckCircleOutlined,
  FileSearchOutlined,
  FireOutlined,
  InboxOutlined,
  PlusOutlined,
  QuestionCircleOutlined,
  SearchOutlined,
  SyncOutlined,
} from '@ant-design/icons';
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
  Skeleton,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { getUserList } from '@/pages/system/user/service';
import { useAppTheme } from '@/theme/AppTheme';
import { DICT_SOURCE_CHANNEL, useDictOptions } from '@/utils/dict';
import { formatDateTime } from '@/utils/format';
import {
  Card,
  CustomerTypePill,
  DeadlineText,
  LevelPill,
  PageTitle,
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
}> = ({ value, onChange }) => (
  <Space.Compact style={{ width: '100%' }}>
    <InputNumber
      min={0}
      precision={0}
      placeholder="最少"
      style={{ width: '50%' }}
      value={value?.[0] ?? null}
      onChange={(v) => onChange?.([v, value?.[1]])}
    />
    <InputNumber
      min={0}
      precision={0}
      placeholder="最多"
      style={{ width: '50%' }}
      value={value?.[1] ?? null}
      onChange={(v) => onChange?.([value?.[0], v])}
    />
  </Space.Compact>
);

const toQuery = (
  f: Filters,
  urgentOnly: boolean,
  timeoutOnly: boolean,
  sort: Sort = {},
): InquiryQuery => ({
  keyword: f.keyword?.trim() || undefined,
  statusList: f.status ? [f.status] : undefined,
  source: f.source,
  ownerId: f.ownerId,
  customerType: f.customerType,
  level: f.level,
  inquiryDateFrom: f.range?.[0]?.format('YYYY-MM-DD'),
  inquiryDateTo: f.range?.[1]?.format('YYYY-MM-DD'),
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
  sourcingTimeout: number;
  ready: number;
  pendingConfirm: number;
  today: number;
}

const StatCard: React.FC<{
  icon: React.ReactNode;
  color: string;
  soft: string;
  label: string;
  value?: number;
  hint?: React.ReactNode;
  onClick?: () => void;
}> = ({ icon, color, soft, label, value, hint, onClick }) => {
  const { palette } = useAppTheme();
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        textAlign: 'left',
        background: palette.card,
        border: `1px solid ${palette.hairline}`,
        borderRadius: 16,
        padding: 20,
        cursor: onClick ? 'pointer' : 'default',
        transition: 'border-color 150ms ease-out',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = palette.accentLine;
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = palette.hairline;
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          color: palette.sub,
        }}
      >
        <span
          style={{
            width: 32,
            height: 32,
            borderRadius: 10,
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: soft,
            color,
          }}
        >
          {icon}
        </span>
        {label}
      </div>
      <div
        style={{
          fontSize: 28,
          fontWeight: 700,
          color: palette.ink,
          margin: '12px 0 4px',
        }}
      >
        {value ?? <Skeleton.Button active size="small" />}
      </div>
      <div style={{ fontSize: 12, minHeight: 18 }}>{hint}</div>
    </button>
  );
};

const CustomerInquiryPage: React.FC = () => {
  const { options: sourceOptions, labelOf: sourceLabel } =
    useDictOptions(DICT_SOURCE_CHANNEL);
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [searchParams, setSearchParams] = useSearchParams();
  const [form] = Form.useForm<Filters>();
  const [filters, setFilters] = useState<Filters>({});
  const [urgentOnly, setUrgentOnly] = useState(false);
  const [timeoutOnly, setTimeoutOnly] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
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

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await inquiryApi.page({
        ...toQuery(filters, urgentOnly, timeoutOnly, sort),
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
  }, [filters, urgentOnly, timeoutOnly, sort, page, pageSize]);

  const loadStats = useCallback(async () => {
    const count = (q: InquiryQuery) =>
      inquiryApi.page({ ...q, page: 1, pageSize: 1 }).then((r) => r.total);
    const today = dayjs().format('YYYY-MM-DD');
    try {
      const [sourcing, sourcingTimeout, ready, pendingConfirm, todayCount] =
        await Promise.all([
          count({ statusList: [STATUS.SOURCING] }),
          count({ statusList: [STATUS.SOURCING], timeoutOnly: true }),
          count({ statusList: [STATUS.READY] }),
          count({ statusList: [STATUS.PENDING_CONFIRM] }),
          count({ inquiryDateFrom: today, inquiryDateTo: today }),
        ]);
      setStats({
        sourcing,
        sourcingTimeout,
        ready,
        pendingConfirm,
        today: todayCount,
      });
    } catch {
      setStats(null);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    loadStats();
    getUserList({ current: 1, pageSize: 200 })
      .then((res) =>
        setOwners(
          res.data.records.map((u) => ({ value: u.id, label: u.name })),
        ),
      )
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

  const quickStatus = (status?: number, timeout = false) => {
    form.setFieldsValue({ status });
    setTimeoutOnly(timeout);
    setPage(1);
    setFilters({ ...form.getFieldsValue(), status });
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

  const columns: TableColumnsType<CustomerInquiry> = [
    {
      title: '询盘编号',
      dataIndex: 'inquiryCode',
      width: 190,
      render: (v, r) => (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <a onClick={() => history.push(`${PATHS.inquiries}/${r.id}`)}>{v}</a>
          {r.urgent && <Pill tone="red">紧急</Pill>}
        </span>
      ),
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
      title: '客户',
      dataIndex: 'customerName',
      width: 260,
      render: (v, r) => (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <span style={{ display: 'inline-flex', flexDirection: 'column' }}>
            <span style={{ color: palette.ink, fontWeight: 600 }}>{v}</span>
            <span style={{ color: palette.mute, fontSize: 12 }}>
              {r.customerCountry || '—'}
            </span>
          </span>
          <CustomerTypePill type={r.customerType} />
        </span>
      ),
    },
    {
      title: '来源',
      dataIndex: 'source',
      width: 150,
      render: (v) => (
        <span style={{ whiteSpace: 'nowrap' }}>{sourceLabel(v)}</span>
      ),
    },
    {
      title: (
        <Tooltip title="询盘里有几个不同的型号，不是件数，也不是询价任务数（一个询价任务可能包含多个型号）">
          <span style={{ whiteSpace: 'nowrap' }}>
            型号数 <QuestionCircleOutlined style={{ color: palette.mute }} />
          </span>
        </Tooltip>
      ),
      dataIndex: 'totalItemCount',
      width: 110,
      render: (v, r) => {
        // 未解析、解析失败，或没录型号就取消的询盘没有型号数
        if (r.status <= STATUS.PARSE_FAILED || !v) return '—';
        if (r.status === STATUS.PENDING_CONFIRM)
          return (
            <span style={{ display: 'inline-flex', flexDirection: 'column' }}>
              <span>{v} 个</span>
              <span style={{ color: palette.mute, fontSize: 12 }}>
                AI 识别，待确认
              </span>
            </span>
          );
        return `${v} 个`;
      },
      sorter: true,
      sortDirections: ['descend', 'ascend'],
      sortOrder: sortOrderOf('totalItemCount'),
    },
    {
      title: (
        <Tooltip title="所有型号的数量直接相加（不区分单位），和型号数一起判断询盘价值：多型号、多数量的询盘通常更值得优先跟进">
          <span style={{ whiteSpace: 'nowrap' }}>
            总数量 <QuestionCircleOutlined style={{ color: palette.mute }} />
          </span>
        </Tooltip>
      ),
      dataIndex: 'totalQuantity',
      width: 120,
      render: (v, r) => {
        if (r.status <= STATUS.PARSE_FAILED || !r.totalItemCount) return '—';
        if (r.status === STATUS.PENDING_CONFIRM)
          return (
            <span style={{ display: 'inline-flex', flexDirection: 'column' }}>
              <span>{v}</span>
              <span style={{ color: palette.mute, fontSize: 12 }}>
                AI 识别，待确认
              </span>
            </span>
          );
        return v;
      },
      sorter: true,
      sortDirections: ['descend', 'ascend'],
      sortOrder: sortOrderOf('totalQuantity'),
    },
    {
      title: '回价进度',
      width: 190,
      render: (_, r) => {
        if (r.status === STATUS.PARSE_FAILED)
          return (
            <span style={{ color: palette.red, fontSize: 12 }}>
              AI 没有返回结果
            </span>
          );
        if (r.status === STATUS.PENDING_PARSE)
          return (
            <span style={{ color: palette.mute, fontSize: 12 }}>
              等待解析或手动录入
            </span>
          );
        if (r.status === STATUS.PARSING)
          return (
            <span style={{ color: palette.mute, fontSize: 12 }}>
              解析完成后显示
            </span>
          );
        if (r.status === STATUS.PENDING_CONFIRM)
          return (
            <span style={{ color: palette.mute, fontSize: 12 }}>
              确认型号后开始询价
            </span>
          );
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
      title: '报价截止',
      dataIndex: 'quoteDeadline',
      width: 110,
      render: (v, r) => <DeadlineText date={v} status={r.status} />,
    },
    {
      title: '负责人',
      dataIndex: 'ownerName',
      width: 90,
      render: (v) => v || '—',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
      render: (v) => (
        <span style={{ fontVariantNumeric: 'tabular-nums' }}>
          {formatDateTime(v)}
        </span>
      ),
    },
    {
      title: '操作',
      width: 160,
      fixed: 'right',
      render: (_, r) => {
        if (
          r.status === STATUS.PENDING_PARSE ||
          r.status === STATUS.PARSE_FAILED
        ) {
          return (
            <span style={{ display: 'inline-flex', gap: 12 }}>
              <a
                aria-disabled={busyId === r.id}
                onClick={() =>
                  run(
                    r.id,
                    () =>
                      r.status === STATUS.PENDING_PARSE
                        ? inquiryApi.startParse(r.id)
                        : inquiryApi.retryParse(r.id),
                    '已开始 AI 解析，完成后状态会变为待确认',
                  )
                }
              >
                {r.status === STATUS.PENDING_PARSE ? 'AI 解析' : '重新解析'}
              </a>
              {/* 手动录入只打开确认页，不改状态；确认时才离开待解析 / 解析失败 */}
              <a
                aria-disabled={busyId === r.id}
                onClick={() =>
                  history.push(`${PATHS.inquiries}/${r.id}/confirm`)
                }
              >
                手动录入
              </a>
            </span>
          );
        }
        return (
          <span style={{ display: 'inline-flex', gap: 12 }}>
            <a onClick={() => history.push(`${PATHS.inquiries}/${r.id}`)}>
              查看
            </a>
            {r.status === STATUS.PENDING_CONFIRM && (
              <a
                style={{ fontWeight: 600 }}
                onClick={() =>
                  history.push(`${PATHS.inquiries}/${r.id}/confirm`)
                }
              >
                去确认
              </a>
            )}
          </span>
        );
      },
    },
  ];

  const noFilter =
    !filters.keyword &&
    !filters.status &&
    !filters.source &&
    !filters.ownerId &&
    !filters.customerType &&
    !filters.range &&
    !urgentOnly &&
    !timeoutOnly;

  return (
    <div>
      <PageTitle
        crumbs={['客户询盘']}
        title="客户询盘"
        description="一个客户的一次需求。确认型号后按品牌拆给采购询价，价格回齐即可出报价单。"
        actions={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setPresetCustomer(undefined);
              setNewOpen(true);
            }}
          >
            新建客户询盘
          </Button>
        }
      />

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(4, minmax(0, 1fr))',
          gap: 16,
          marginBottom: 16,
        }}
      >
        <StatCard
          icon={<SyncOutlined />}
          color={palette.link}
          soft={palette.accentSoft}
          label="询价中"
          value={stats?.sourcing}
          hint={
            stats && stats.sourcingTimeout > 0 ? (
              <span style={{ color: palette.red }}>
                其中 {stats.sourcingTimeout} 个有超时任务
              </span>
            ) : (
              <span style={{ color: palette.mute }}>采购正在问价</span>
            )
          }
          onClick={() => quickStatus(STATUS.SOURCING)}
        />
        <StatCard
          icon={<CheckCircleOutlined />}
          color={palette.green}
          soft={palette.greenSoft}
          label="可报价"
          value={stats?.ready}
          hint={
            <span style={{ color: palette.green }}>
              价格已回齐，等你出报价单
            </span>
          }
          onClick={() => quickStatus(STATUS.READY)}
        />
        <StatCard
          icon={<FileSearchOutlined />}
          color={palette.orange}
          soft={palette.orangeSoft}
          label="待确认"
          value={stats?.pendingConfirm}
          hint={
            <span style={{ color: palette.mute }}>型号已识别，等你确认</span>
          }
          onClick={() => quickStatus(STATUS.PENDING_CONFIRM)}
        />
        <StatCard
          icon={<InboxOutlined />}
          color={palette.cyan}
          soft={palette.inset}
          label="今日新增"
          value={stats?.today}
          hint={<span style={{ color: palette.mute }}>按询盘日期统计</span>}
        />
      </div>

      <Card style={{ marginBottom: 16, padding: 20 }}>
        <Form form={form} layout="vertical" onFinish={applyFilters}>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(5, minmax(0, 1fr))',
              gap: 12,
              alignItems: 'end',
            }}
          >
            <Form.Item
              name="keyword"
              label="关键词"
              style={{ marginBottom: 0 }}
            >
              <Input
                allowClear
                prefix={<SearchOutlined />}
                placeholder="询盘编号、客户名称、型号"
              />
            </Form.Item>
            <Form.Item
              name="level"
              label="询盘等级"
              style={{ marginBottom: 0 }}
            >
              <Select allowClear placeholder="全部" options={levelOptions} />
            </Form.Item>
            <Form.Item name="status" label="状态" style={{ marginBottom: 0 }}>
              <Select
                allowClear
                placeholder="全部"
                options={Object.entries(STATUS_META).map(([k, v]) => ({
                  value: Number(k),
                  label: v.label,
                }))}
              />
            </Form.Item>
            <Form.Item name="source" label="来源" style={{ marginBottom: 0 }}>
              <Select allowClear placeholder="全部" options={sourceOptions} />
            </Form.Item>
            <Form.Item
              name="ownerId"
              label="负责人"
              style={{ marginBottom: 0 }}
            >
              <Select
                allowClear
                placeholder="全部"
                options={owners}
                showSearch={{ optionFilterProp: 'label' }}
              />
            </Form.Item>
            <Form.Item
              name="customerType"
              label="新老客户"
              style={{ marginBottom: 0 }}
            >
              <Select
                allowClear
                placeholder="全部"
                options={[
                  { value: 1, label: '新客户' },
                  { value: 2, label: '老客户' },
                ]}
              />
            </Form.Item>
            <Form.Item
              name="range"
              label="询盘日期"
              style={{ marginBottom: 0 }}
            >
              <DatePicker.RangePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="itemCount"
              label="型号数"
              style={{ marginBottom: 0 }}
            >
              <RangeInput />
            </Form.Item>
            <Form.Item
              name="totalQuantity"
              label="总数量"
              style={{ marginBottom: 0 }}
            >
              <RangeInput />
            </Form.Item>
            <div
              style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}
            >
              <Button
                onClick={() => {
                  form.resetFields();
                  setUrgentOnly(false);
                  setTimeoutOnly(false);
                  setPage(1);
                  setSort({});
                  setFilters({});
                }}
              >
                重置
              </Button>
              <Button type="primary" htmlType="submit">
                查询
              </Button>
            </div>
          </div>
        </Form>
      </Card>

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginBottom: 12,
        }}
      >
        <span style={{ fontWeight: 600, color: palette.ink }}>
          共 {total} 条
        </span>
        <span style={{ color: palette.mute, fontSize: 12 }}>
          {sort.field
            ? sort.field === 'level'
              ? `按询盘等级${sort.order === 'asc' ? '从低到高' : '从高到低'}，相同再按创建时间倒序`
              : `按${sort.field === 'totalQuantity' ? '总数量' : '型号数'}${sort.order === 'asc' ? '从少到多' : '从多到少'}，相同再按创建时间倒序`
            : '按创建时间倒序'}
        </span>
        <span style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
          <Tag.CheckableTag
            checked={urgentOnly}
            onChange={(v) => {
              setUrgentOnly(v);
              setPage(1);
            }}
          >
            <FireOutlined /> 只看紧急
          </Tag.CheckableTag>
          <Tag.CheckableTag
            checked={timeoutOnly}
            onChange={(v) => {
              setTimeoutOnly(v);
              setPage(1);
            }}
          >
            <AlertOutlined /> 只看有超时任务
          </Tag.CheckableTag>
        </span>
      </div>

      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : !loading && rows.length === 0 && noFilter ? (
        <Card>
          <EmptyHint
            title="还没有客户询盘"
            description="把客户发来的文字、截图或 Excel 交给 AI 解析，几十秒就能拆好给采购。"
            actionText="新建客户询盘"
            onAction={() => setNewOpen(true)}
          />
        </Card>
      ) : (
        <Table<CustomerInquiry>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: 1610 }}
          onChange={(_, __, sorter) => {
            const s = Array.isArray(sorter) ? sorter[0] : sorter;
            const field = s?.order ? (s.field as Sort['field']) : undefined;
            setSort(
              field
                ? { field, order: s.order === 'ascend' ? 'asc' : 'desc' }
                : {},
            );
            setPage(1);
          }}
          locale={{ emptyText: '没有符合条件的询盘，换个筛选条件试试' }}
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
      )}

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
