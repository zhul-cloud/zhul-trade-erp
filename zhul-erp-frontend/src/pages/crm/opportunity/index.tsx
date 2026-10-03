import {
  CalendarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  PlusOutlined,
  SearchOutlined,
  UserAddOutlined,
} from '@ant-design/icons';
import { history, useAccess, useModel } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, DatePicker, Form, Input, Select, Skeleton, Table } from 'antd';
import type { Dayjs } from 'dayjs';
import React, { useCallback, useEffect, useState } from 'react';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { getUserList } from '@/pages/system/user/service';
import { useAppTheme } from '@/theme/AppTheme';
import { DICT_SOURCE_CHANNEL, useDictOptions } from '@/utils/dict';
import { formatDateTime } from '@/utils/format';
import { Card, PageTitle, Pill, StagePill } from './components';
import { CATEGORY_ACTIVE, LIST_PATH } from './constants';
import RegisterDrawer from './RegisterDrawer';
import { rate } from './StatsPanel';
import {
  type OpportunityItem,
  type OpportunityQuery,
  type OpportunityStage,
  type OpportunitySummary,
  opportunityApi,
  readBizError,
} from './service';

interface FilterState {
  keyword?: string;
  sourceChannel?: number;
  stage?: string;
  ownerId?: number;
  range?: [Dayjs, Dayjs];
}

const toQuery = (f: FilterState): OpportunityQuery => ({
  keyword: f.keyword?.trim() || undefined,
  sourceChannel: f.sourceChannel,
  stage: f.stage,
  ownerId: f.ownerId,
  from: f.range?.[0]?.format('YYYY-MM-DD'),
  to: f.range?.[1]?.format('YYYY-MM-DD'),
});

const OpportunityPage: React.FC = () => {
  const { options: sourceOptions, labelOf: sourceLabel } = useDictOptions(
    DICT_SOURCE_CHANNEL,
    '未设置',
  );
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const { initialState } = useModel('@@initialState');
  const canAdd = !!access['crm:opportunity:add'];

  const [form] = Form.useForm<FilterState>();
  const [query, setQuery] = useState<OpportunityQuery>({ stage: 'ACTIVE' });
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [rows, setRows] = useState<OpportunityItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [summary, setSummary] = useState<OpportunitySummary | null>(null);
  const [stages, setStages] = useState<OpportunityStage[]>([]);
  const [owners, setOwners] = useState<{ value: number; label: string }[]>([]);
  const [drawerOpen, setDrawerOpen] = useState(false);

  useEffect(() => {
    opportunityApi
      .stages()
      .then(setStages)
      .catch(() => undefined);
    // 负责人筛选：有用户管理权限时才能取到名单，取不到就不显示这个筛选项
    getUserList({ current: 1, pageSize: 200 })
      .then((res) =>
        setOwners(
          res.data.records.map((u) => ({ value: u.id, label: u.name })),
        ),
      )
      .catch(() => setOwners([]));
  }, []);

  const loadSummary = useCallback(() => {
    opportunityApi
      .summary()
      .then(setSummary)
      .catch(() => setSummary(null));
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await opportunityApi.page({ ...query, page, pageSize });
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [query, page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    loadSummary();
  }, [loadSummary]);

  const activeIndex = (code: string) =>
    stages
      .filter((s) => s.category === CATEGORY_ACTIVE)
      .findIndex((s) => s.code === code);

  const search = () => {
    setPage(1);
    setQuery(toQuery(form.getFieldsValue()));
  };
  const reset = () => {
    form.resetFields();
    form.setFieldsValue({ stage: 'ACTIVE' });
    setPage(1);
    setQuery({ stage: 'ACTIVE' });
  };

  const columns: TableColumnsType<OpportunityItem> = [
    {
      title: '商机编号',
      dataIndex: 'opportunityCode',
      width: 160,
      render: (v: string, r) => (
        <a onClick={() => history.push(`${LIST_PATH}/${r.id}`)}>{v}</a>
      ),
    },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 280,
      render: (_, r) => (
        <div style={{ minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ fontWeight: 600, color: palette.ink }}>
              {r.customerName}
            </span>
            {r.customerNameMissing && <Pill tone="orange">未填客户名称</Pill>}
          </div>
          <div style={{ fontSize: 12, color: palette.mute }}>{r.country}</div>
        </div>
      ),
    },
    {
      title: '来源渠道',
      dataIndex: 'sourceChannel',
      width: 140,
      render: (v: number) => sourceLabel(v),
    },
    {
      title: '首次接触',
      dataIndex: 'firstContactDate',
      width: 120,
      render: (v: string) => <span className="num">{v}</span>,
    },
    {
      title: '阶段',
      dataIndex: 'stageCode',
      width: 140,
      render: (_, r) => (
        <StagePill
          code={r.stageCode}
          name={r.stageName}
          category={r.stageCategory}
          index={activeIndex(r.stageCode)}
        />
      ),
    },
    {
      title: '需求摘要',
      dataIndex: 'demandSummary',
      ellipsis: { showTitle: true },
      render: (v: string) =>
        v || <span style={{ color: palette.mute }}>—</span>,
    },
    {
      title: '负责人',
      dataIndex: 'ownerName',
      width: 100,
      render: (v?: string) => v || '未分配',
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (v: string) => <span className="num">{formatDateTime(v)}</span>,
    },
    {
      title: '操作',
      key: 'op',
      width: 90,
      fixed: 'right',
      render: (_, r) => (
        <a onClick={() => history.push(`${LIST_PATH}/${r.id}`)}>查看</a>
      ),
    },
  ];

  const kpis = [
    {
      icon: <UserAddOutlined />,
      label: '今日新增',
      value: summary?.todayNew,
      hint: summary ? `其中无效 ${summary.todayInvalid}` : '',
      color: palette.link,
    },
    {
      icon: <CalendarOutlined />,
      label: '本周新增',
      value: summary?.weekNew,
      hint: summary ? `上周 ${summary.lastWeekNew}` : '',
      color: palette.link,
    },
    {
      icon: <ClockCircleOutlined />,
      label: '待跟进（S1）',
      value: summary?.firstStageCount,
      hint: summary?.firstStageStale
        ? `超过 2 天未推进 ${summary.firstStageStale} 条`
        : '都在 2 天内',
      color: summary?.firstStageStale ? palette.orange : palette.mute,
    },
    {
      icon: <CheckCircleOutlined />,
      label: '本月有效率',
      value: summary ? rate(summary.monthValid, summary.monthNew) : undefined,
      hint: '有效 = 进入过 S3',
      color: palette.green,
    },
  ];

  const stageOptions = [
    { value: 'ACTIVE', label: '进行中' },
    { value: 'CLOSED', label: '已结束' },
    ...stages.map((s) => ({
      value: s.code,
      label: s.category === CATEGORY_ACTIVE ? `${s.code} ${s.name}` : s.name,
    })),
  ];

  let body: React.ReactNode;
  if (error) {
    body = <ErrorHint message={error} onRetry={load} />;
  } else if (
    !loading &&
    total === 0 &&
    JSON.stringify(query) === JSON.stringify({ stage: 'ACTIVE' })
  ) {
    body = (
      <EmptyHint
        title="还没有进行中的商机"
        description="接到新客户时登记一条商机，每天各渠道的新增和有效情况会自动统计。"
        actionText={canAdd ? '登记商机' : undefined}
        onAction={canAdd ? () => setDrawerOpen(true) : undefined}
      />
    );
  } else {
    body = (
      <Table<OpportunityItem>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1300 }}
        locale={{ emptyText: '没有符合条件的商机，换个筛选条件试试' }}
        onRow={(r) => ({
          style: { cursor: 'pointer' },
          onDoubleClick: () => history.push(`${LIST_PATH}/${r.id}`),
        })}
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
    );
  }

  return (
    <div style={{ color: palette.ink }}>
      <PageTitle
        title="商机列表"
        description="记录每个新客户从首次接触到赢单的过程；老客户的新需求请直接新建询盘。"
        actions={
          canAdd && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setDrawerOpen(true)}
            >
              登记商机
            </Button>
          )
        }
      />
      <div style={{ display: 'grid', gap: 20 }}>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(4, 1fr)',
            gap: 16,
          }}
        >
          {kpis.map((k) => (
            <Card key={k.label} style={{ padding: 20 }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  color: palette.mute,
                }}
              >
                <span
                  aria-hidden
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    width: 32,
                    height: 32,
                    borderRadius: 10,
                    color: k.color,
                    background: palette.accentSoft,
                  }}
                >
                  {k.icon}
                </span>
                {k.label}
              </div>
              {summary ? (
                <div
                  className="num"
                  style={{
                    fontSize: 28,
                    fontWeight: 700,
                    margin: '10px 0 4px',
                  }}
                >
                  {k.value}
                </div>
              ) : (
                <Skeleton
                  active
                  paragraph={false}
                  style={{ margin: '12px 0' }}
                />
              )}
              <div style={{ fontSize: 12, color: k.color }}>{k.hint}</div>
            </Card>
          ))}
        </div>

        <Card>
          <Form<FilterState>
            form={form}
            layout="vertical"
            initialValues={{ stage: 'ACTIVE' }}
            onFinish={search}
          >
            <div
              style={{
                display: 'flex',
                gap: 20,
                alignItems: 'flex-end',
                flexWrap: 'wrap',
              }}
            >
              <Form.Item
                name="keyword"
                label="关键词"
                style={{ flex: 1, minWidth: 260, marginBottom: 0 }}
              >
                <Input
                  allowClear
                  prefix={<SearchOutlined />}
                  placeholder="联系人、客户名称、邮箱或 WhatsApp"
                />
              </Form.Item>
              <Form.Item
                name="sourceChannel"
                label="来源渠道"
                style={{ width: 180, marginBottom: 0 }}
              >
                <Select allowClear placeholder="全部" options={sourceOptions} />
              </Form.Item>
              <Form.Item
                name="stage"
                label="阶段"
                style={{ width: 160, marginBottom: 0 }}
              >
                <Select allowClear placeholder="全部" options={stageOptions} />
              </Form.Item>
              {owners.length > 0 && (
                <Form.Item
                  name="ownerId"
                  label="负责人"
                  style={{ width: 150, marginBottom: 0 }}
                >
                  <Select
                    allowClear
                    showSearch={{ optionFilterProp: 'label' }}
                    placeholder="全部"
                    options={owners}
                  />
                </Form.Item>
              )}
              <Form.Item
                name="range"
                label="首次接触日期"
                style={{ width: 260, marginBottom: 0 }}
              >
                <DatePicker.RangePicker style={{ width: '100%' }} />
              </Form.Item>
              <div style={{ display: 'flex', gap: 10 }}>
                <Button onClick={reset}>重置</Button>
                <Button type="primary" htmlType="submit">
                  查询
                </Button>
              </div>
            </div>
          </Form>
        </Card>

        <div style={{ fontSize: 14, fontWeight: 600 }}>共 {total} 条记录</div>
        {body}
      </div>
      <RegisterDrawer
        open={drawerOpen}
        ownerName={initialState?.currentUser?.name}
        onClose={() => setDrawerOpen(false)}
        onSaved={() => {
          load();
          loadSummary();
        }}
      />
    </div>
  );
};

export default OpportunityPage;
