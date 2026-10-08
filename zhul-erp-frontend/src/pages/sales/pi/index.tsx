import {
  ClockCircleOutlined,
  FileDoneOutlined,
  FileTextOutlined,
  HourglassOutlined,
  PlusOutlined,
  SearchOutlined,
  WalletOutlined,
} from '@ant-design/icons';
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
import React, { useCallback, useEffect, useState } from 'react';
import { CustomerCell } from '@/components/DocFields';
import { Stat, useWide } from '@/pages/inquiry/shared/components';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { getUserList } from '@/pages/system/user/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount, formatDateTime } from '@/utils/format';
import {
  Card,
  PATHS,
  PI_STATUS,
  PI_STATUS_META,
  Pill,
  PiStatusPill,
  RECEIPT_META,
  RECEIPT_STATUS,
  ReceiptStatusPill,
  SalesPageTitle,
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
}

const PiList: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const wide = useWide();
  const [form] = Form.useForm<Filters>();
  const [search] = useSearchParams();
  const [filters, setFilters] = useState<Filters>(() =>
    search.get('expired') === '1' ? { expiredUnpaid: true } : {},
  );
  const [overdueCount, setOverdueCount] = useState<number>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<PiListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [stats, setStats] = useState<PiStats>();
  const [owners, setOwners] = useState<{ value: number; label: string }[]>([]);
  const [newOpen, setNewOpen] = useState(false);

  /** 型号数、总数量、合计的排序（后端排序，翻页后保持） */
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

  useEffect(() => {
    piApi
      .stats()
      .then(setStats)
      .catch(() => setStats(undefined));
    piApi
      .overdue()
      .then((r) => setOverdueCount(r.count))
      .catch(() => setOverdueCount(undefined));
    getUserList({ current: 1, pageSize: 200 })
      .then((res) =>
        setOwners(
          res.data.records.map((u) => ({ value: u.id, label: u.name })),
        ),
      )
      .catch(() => setOwners([]));
  }, []);

  const quick = (patch: Filters) => {
    form.setFieldsValue(patch);
    setFilters((f) => ({ ...f, ...patch }));
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
    !filters.ownerId &&
    !filters.range;

  const columns: TableColumnsType<PiListItem> = [
    {
      title: 'PI 编号',
      dataIndex: 'piNo',
      width: 170,
      fixed: 'left',
      render: (v: string, r) => (
        <a onClick={() => history.push(PATHS.pi(r.id))}>{v}</a>
      ),
    },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 260,
      render: (v: string, r) => (
        <CustomerCell
          name={v}
          country={r.customerCountry}
          type={r.customerType}
        />
      ),
    },
    {
      title: '型号数',
      dataIndex: 'itemCount',
      sorter: true,
      sortOrder: sortOf('itemCount'),
      width: 100,
      align: 'right',
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
      title: '币种 · 合计',
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
            <span style={{ fontSize: 12, color: palette.link }}>
              Rev.{r.currentVersionNo}
            </span>
          )}
          {r.revising && (
            <span style={{ fontSize: 12, color: palette.orange }}>修改中</span>
          )}
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
      title: '来源报价单',
      dataIndex: 'quotationNos',
      width: 200,
      render: (v: string[]) =>
        v.length === 0
          ? '—'
          : v.length === 1
            ? v[0]
            : `${v[0]} 等 ${v.length} 张`,
    },
    {
      title: '销售订单',
      dataIndex: 'soNo',
      width: 150,
      render: (v: string | undefined, r) =>
        v && r.orderId ? (
          <a onClick={() => history.push(PATHS.order(r.orderId as number))}>
            {v}
          </a>
        ) : (
          '—'
        ),
    },
    { title: '业务员', dataIndex: 'ownerName', width: 100 },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
      render: (v: string) => formatDateTime(v),
    },
    {
      title: '最近发送',
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
        r.status === PI_STATUS.DRAFT ? (
          <Space size={12}>
            <a onClick={() => history.push(PATHS.pi(r.id))}>编辑</a>
            <Popconfirm
              title="删除这张草稿？"
              description="删除后不能恢复，报价单里的型号可以重新开 PI"
              okText="删除"
              okButtonProps={{ danger: true }}
              onConfirm={() => remove(r.id)}
            >
              <a style={{ color: palette.red }}>删除</a>
            </Popconfirm>
          </Space>
        ) : (
          <a onClick={() => history.push(PATHS.pi(r.id))}>查看</a>
        ),
    },
  ];

  const statButton = (node: React.ReactNode, onClick?: () => void) =>
    onClick ? (
      <button
        type="button"
        style={{ all: 'unset', cursor: 'pointer' }}
        onClick={onClick}
      >
        {node}
      </button>
    ) : (
      node
    );

  return (
    <div>
      <SalesPageTitle
        crumbs={['PI']}
        title="PI（形式发票）"
        description="客户有意向后，从报价单开 PI 确认型号与金额并请款；客户付款后转成销售订单。"
        actions={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setNewOpen(true)}
          >
            新建 PI
          </Button>
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
        {statButton(
          <Stat
            icon={<FileTextOutlined />}
            color={palette.sub}
            soft={palette.inset}
            label="草稿"
            value={stats?.draftCount ?? '—'}
            hint={<span style={{ color: palette.mute }}>还没发给客户</span>}
          />,
          () => quick({ status: PI_STATUS.DRAFT, receiptStatus: undefined }),
        )}
        {statButton(
          <Stat
            icon={<HourglassOutlined />}
            color={palette.orange}
            soft={palette.orangeSoft}
            label="待到账"
            value={stats?.slipOnlyCount ?? '—'}
            hint={
              <span style={{ color: palette.mute }}>
                客户发了水单、财务还没确认到账
              </span>
            }
          />,
          () =>
            quick({
              status: undefined,
              receiptStatus: RECEIPT_STATUS.SLIP_ONLY,
            }),
        )}
        <Stat
          icon={<WalletOutlined />}
          color={palette.link}
          soft={palette.accentSoft}
          label="本月到账"
          value={stats ? formatAmount(stats.monthReceivedUsd, 'USD') : '—'}
          hint={
            <span style={{ color: palette.mute }}>
              {stats
                ? `折合 ${formatAmount(stats.monthReceivedCny, 'CNY')}（各币种合计）`
                : '—'}
            </span>
          }
        />
        <Stat
          icon={<FileDoneOutlined />}
          color={palette.green}
          soft={palette.greenSoft}
          label="本月转订单"
          value={stats?.monthOrderCount ?? '—'}
          hint={
            stats && stats.monthOrderUnpaid > 0 ? (
              <span style={{ color: palette.orange }}>
                凭水单先转、还未到账 {stats.monthOrderUnpaid} 张
              </span>
            ) : (
              <span style={{ color: palette.mute }}>客户付款后由 PI 转成</span>
            )
          }
        />
      </div>

      <Card style={{ marginBottom: 16, padding: 20 }}>
        <Form
          form={form}
          layout="vertical"
          onFinish={(v) => {
            setFilters({ ...v, expiredUnpaid: filters.expiredUnpaid });
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
                placeholder="PI 编号、客户、型号"
              />
            </Form.Item>
            <Form.Item
              name="status"
              label="PI 状态"
              style={{ marginBottom: 0 }}
            >
              <Select
                allowClear
                placeholder="全部"
                options={Object.entries(PI_STATUS_META).map(([k, v]) => ({
                  value: Number(k),
                  label: v.label,
                }))}
              />
            </Form.Item>
            <Form.Item
              name="receiptStatus"
              label="收款状态"
              style={{ marginBottom: 0 }}
            >
              <Select
                allowClear
                placeholder="全部"
                options={Object.entries(RECEIPT_META).map(([k, v]) => ({
                  value: Number(k),
                  label: v.label,
                }))}
              />
            </Form.Item>
            <Form.Item
              name="ownerId"
              label="业务员"
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
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              marginTop: 12,
            }}
          >
            <span style={{ fontSize: 12, color: palette.mute }}>快捷筛选</span>
            <Button
              size="small"
              danger={!!filters.expiredUnpaid}
              type={filters.expiredUnpaid ? 'primary' : 'default'}
              icon={<ClockCircleOutlined />}
              onClick={() => {
                setFilters((f) => ({ ...f, expiredUnpaid: !f.expiredUnpaid }));
                setPage(1);
              }}
              aria-pressed={!!filters.expiredUnpaid}
            >
              已过期未收款{overdueCount != null ? ` ${overdueCount}` : ''}
            </Button>
          </div>
        </Form>
      </Card>

      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : !loading && rows.length === 0 && noFilter ? (
        <Card>
          <EmptyHint
            title="还没有 PI"
            description="在已发送的报价单上点「开 PI」，或在这里从客户的多张报价单挑型号"
            actionText="新建 PI"
            onAction={() => setNewOpen(true)}
          />
        </Card>
      ) : (
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
          scroll={{ x: 1970 }}
          locale={{ emptyText: '没有符合条件的 PI，换个筛选条件试试' }}
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
