import {
  CheckCircleOutlined,
  FileTextOutlined,
  PercentageOutlined,
  PlusOutlined,
  SearchOutlined,
  SendOutlined,
  SlidersOutlined,
} from '@ant-design/icons';
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
import React, { useCallback, useEffect, useState } from 'react';
import { Stat, useWide } from '@/pages/inquiry/shared/components';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { getUserList } from '@/pages/system/user/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import { formatMargin } from './calc';
import {
  Card,
  CURRENCIES,
  PATHS,
  QuotationPageTitle,
  QuotationStatusPill,
  STATUS,
  STATUS_META,
} from './components';
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

const QuotationList: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const wide = useWide();
  const [form] = Form.useForm<Filters>();
  const [filters, setFilters] = useState<Filters>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<QuotationListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [stats, setStats] = useState<QuotationStats>();
  const [owners, setOwners] = useState<{ value: number; label: string }[]>([]);
  const [newOpen, setNewOpen] = useState(false);

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
  }, [filters, page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    quotationApi
      .stats()
      .then(setStats)
      .catch(() => setStats(undefined));
    getUserList({ current: 1, pageSize: 200 })
      .then((res) =>
        setOwners(
          res.data.records.map((u) => ({ value: u.id, label: u.name })),
        ),
      )
      .catch(() => setOwners([]));
  }, []);

  const quickStatus = (status: number) => {
    form.setFieldsValue({ status });
    setFilters((f) => ({ ...f, status }));
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

  const columns: TableColumnsType<QuotationListItem> = [
    {
      title: '报价单编号',
      dataIndex: 'quotationNo',
      width: 160,
      fixed: 'left',
      render: (v: string, r) => (
        <a onClick={() => history.push(PATHS.detail(r.id))}>{v}</a>
      ),
    },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 220,
      ellipsis: true,
    },
    {
      title: '型号数',
      dataIndex: 'itemCount',
      width: 80,
      align: 'right',
    },
    {
      title: '币种 · 合计',
      dataIndex: 'totalAmount',
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
      title: '来源询盘',
      dataIndex: 'inquiryCodes',
      width: 190,
      render: (v: string[]) =>
        v.length === 0
          ? '—'
          : v.length === 1
            ? v[0]
            : `${v[0]} 等 ${v.length} 个`,
    },
    { title: '创建人', dataIndex: 'ownerName', width: 100 },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
      render: (v: string) => formatDateTime(v),
    },
    {
      title: '发送时间',
      dataIndex: 'sentAt',
      width: 170,
      render: (v?: string) => formatDateTime(v),
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      fixed: 'right',
      render: (_, r) =>
        r.status === STATUS.DRAFT ? (
          <Space size={12}>
            <a onClick={() => history.push(PATHS.detail(r.id))}>编辑</a>
            <Popconfirm
              title="删除这张草稿？"
              description="删除后不能恢复，询盘里的型号可以重新报价"
              okText="删除"
              okButtonProps={{ danger: true }}
              onConfirm={() => remove(r.id)}
            >
              <a style={{ color: palette.red }}>删除</a>
            </Popconfirm>
          </Space>
        ) : (
          <a onClick={() => history.push(PATHS.detail(r.id))}>查看</a>
        ),
    },
  ];

  return (
    <div>
      <QuotationPageTitle
        crumbs={['报价单']}
        title="报价单"
        description="把可报价的型号定好售价发给客户；报价单标为已发送后，客户询盘自动变为「已报价」。"
        actions={
          <>
            <Button
              icon={<SlidersOutlined />}
              onClick={() => history.push(PATHS.pricing)}
            >
              定价策略
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setNewOpen(true)}
            >
              新建报价单
            </Button>
          </>
        }
      />

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: wide
            ? 'repeat(4, minmax(0, 1fr))'
            : 'repeat(2, minmax(0, 1fr))',
          gap: 16,
          marginBottom: 16,
        }}
      >
        <button
          type="button"
          style={{ all: 'unset', cursor: 'pointer' }}
          onClick={() => quickStatus(STATUS.DRAFT)}
        >
          <Stat
            icon={<FileTextOutlined />}
            color={palette.sub}
            soft={palette.inset}
            label="草稿"
            value={stats?.draftCount ?? '—'}
            hint={<span style={{ color: palette.mute }}>还没发给客户</span>}
          />
        </button>
        <button
          type="button"
          style={{ all: 'unset', cursor: 'pointer' }}
          onClick={() => quickStatus(STATUS.SENT)}
        >
          <Stat
            icon={<SendOutlined />}
            color={palette.link}
            soft={palette.accentSoft}
            label="已发送待回复"
            value={stats?.sentCount ?? '—'}
            hint={
              stats && stats.sentCount > 0 ? (
                <span style={{ color: palette.orange }}>
                  最久 {stats.oldestSentDays} 天没有回复
                </span>
              ) : (
                <span style={{ color: palette.mute }}>等客户回复</span>
              )
            }
          />
        </button>
        <Stat
          icon={<CheckCircleOutlined />}
          color={palette.green}
          soft={palette.greenSoft}
          label="本月成交"
          value={stats?.monthWonCount ?? '—'}
          hint={
            <span style={{ color: palette.mute }}>
              {stats
                ? `成交金额 ${formatAmount(stats.monthWonAmountCny, 'CNY')} · 净利润 ${formatAmount(stats.monthWonNetProfitCny, 'CNY')}`
                : '—'}
            </span>
          }
        />
        <Stat
          icon={<PercentageOutlined />}
          color={palette.orange}
          soft={palette.orangeSoft}
          label="本月平均毛利率"
          value={formatMargin(stats?.monthAvgMargin)}
          hint={
            stats && stats.monthBelowFloorCount > 0 ? (
              <span style={{ color: palette.red }}>
                低于红线的报价单 {stats.monthBelowFloorCount} 张
              </span>
            ) : (
              <span style={{ color: palette.mute }}>按本月成交的报价单</span>
            )
          }
        />
      </div>

      <Card style={{ marginBottom: 16, padding: 20 }}>
        <Form
          form={form}
          layout="vertical"
          onFinish={(v) => {
            setFilters(v);
            setPage(1);
          }}
        >
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '2fr 1fr 1fr 1fr 1.6fr auto',
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
                placeholder="报价单编号、客户、型号"
              />
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
            <Form.Item
              name="currencyCode"
              label="币种"
              style={{ marginBottom: 0 }}
            >
              <Select
                allowClear
                placeholder="全部"
                options={CURRENCIES.map((c) => ({ value: c, label: c }))}
              />
            </Form.Item>
            <Form.Item
              name="ownerId"
              label="创建人"
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
              name="range"
              label="创建日期"
              style={{ marginBottom: 0 }}
            >
              <DatePicker.RangePicker style={{ width: '100%' }} />
            </Form.Item>
            <div style={{ display: 'flex', gap: 8 }}>
              <Button
                onClick={() => {
                  form.resetFields();
                  setFilters({});
                  setPage(1);
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

      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : !loading && rows.length === 0 && noFilter ? (
        <Card>
          <EmptyHint
            title="还没有报价单"
            description="从客户询盘点「去报价」，或在这里新建报价单，挑选要报的型号"
            actionText="新建报价单"
            onAction={() => setNewOpen(true)}
          />
        </Card>
      ) : (
        <Table<QuotationListItem>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: 1600 }}
          locale={{ emptyText: '没有符合条件的报价单，换个筛选条件试试' }}
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

      <NewQuotationModal
        open={newOpen}
        onClose={() => setNewOpen(false)}
        onCreated={(q) => {
          setNewOpen(false);
          history.push(PATHS.detail(q.id));
        }}
      />
    </div>
  );
};

export default QuotationList;
