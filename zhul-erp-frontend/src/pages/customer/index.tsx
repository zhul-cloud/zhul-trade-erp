import {
  DeleteOutlined,
  DownloadOutlined,
  PlusOutlined,
  SwapOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import type { CheckboxProps, TableColumnsType, TableProps } from 'antd';
import {
  App,
  Button,
  Form,
  Input,
  Segmented,
  Select,
  Skeleton,
  Switch,
  Table,
  Tooltip,
} from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { useCountries } from '@/pages/product/components/useCountries';
import { useAppTheme } from '@/theme/AppTheme';
import { DICT_SOURCE_CHANNEL, useDictOptions } from '@/utils/dict';
import { formatDateTime } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import {
  LIST_PATH,
  PageTitle,
  Pill,
  RolePill,
  TransferModal,
} from './components';
import { DISABLE_CONFIRM_TEXT, ROLE_OPTIONS } from './constants';
import DuplicatesTab from './profile/DuplicatesTab';
import { GRADE_NAMES, MODULE_SHORT, profileApi } from './profile/service';
import { doneOf, GradeBadge, Progress5, Stars } from './profile/ui';
import {
  type AssignableOwners,
  type CustomerListItem,
  type CustomerQuery,
  customerApi,
  readBizError,
} from './service';

const STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '禁用' },
];

const BIZ_GRADE_OPTIONS = [1, 2, 3, 4, 0].map((v) => ({
  value: v,
  label: GRADE_NAMES[v],
}));
const PROFILE_STATUS_OPTIONS = [
  { value: 'done', label: '已建档（5/5）' },
  { value: 'pending', label: '待完善' },
];
const MODULE_OPTIONS = MODULE_SHORT.map((label, i) => ({
  value: i + 1,
  label: `${'①②③④⑤'[i]} ${label}`,
}));

type FilterForm = Omit<CustomerQuery, 'missingModules'> & {
  missingModules?: number[];
};

const compact = (q: FilterForm): CustomerQuery =>
  Object.fromEntries(
    Object.entries({
      ...q,
      missingModules: q.missingModules?.length
        ? q.missingModules.join(',')
        : undefined,
    }).filter(([, v]) => v !== undefined && v !== ''),
  ) as CustomerQuery;

const CustomerListPage: React.FC = () => {
  const { options: sourceOptions, labelOf: sourceLabel } =
    useDictOptions(DICT_SOURCE_CHANNEL);
  const { message, modal } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const { palette } = useAppTheme();
  const [form] = Form.useForm<FilterForm>();
  const { options: countryOptions, zhOf } = useCountries();

  const canAdd = !!access['partner:customer:add'];
  const canEdit = !!access['partner:customer:edit'];
  const canDelete = !!access['partner:customer:delete'];
  const canStatus = !!access['partner:customer:status'];
  const canTransfer = !!access['partner:customer:transfer'];
  const canExport = !!access['partner:customer:export'];
  const canMerge = !!access['partner:customer:merge'];
  const [tab, setTab] = useState<'all' | 'dups'>('all');
  const [dupCount, setDupCount] = useState<number>();
  const [sort, setSort] = useState<
    Pick<CustomerQuery, 'sortField' | 'sortOrder'>
  >({});

  const [query, setQuery] = useState<CustomerQuery>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(10);
  const [rows, setRows] = useState<CustomerListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [firstLoad, setFirstLoad] = useState(true);
  const [error, setError] = useState<string>();
  const [selected, setSelected] = useState<React.Key[]>([]);
  const [togglingId, setTogglingId] = useState<number>();
  const [exporting, setExporting] = useState(false);
  const [owners, setOwners] = useState<AssignableOwners>();
  const [transferTargets, setTransferTargets] = useState<CustomerListItem[]>(
    [],
  );

  // 「负责业务员」筛选和列只对数据范围为全部 / 自定义的人有意义
  const showOwner = owners?.scope === 'ALL' || owners?.scope === 'CUSTOM';
  const hasCondition = Object.keys(query).length > 0;

  useEffect(() => {
    customerApi
      .assignableOwners()
      .then(setOwners)
      .catch(() => undefined);
    profileApi
      .duplicates()
      .then((g) => setDupCount(g.length))
      .catch(() => undefined);
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const data = await customerApi.page({
        ...query,
        ...sort,
        page,
        pageSize,
      });
      if (data.records.length === 0 && data.total > 0 && page > 1) {
        setPage(page - 1);
        return;
      }
      setRows(data.records);
      setTotal(data.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
      setFirstLoad(false);
    }
  }, [query, sort, page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  const search = () => {
    setQuery(compact(form.getFieldsValue()));
    setPage(1);
    setSelected([]);
  };

  const reset = () => {
    form.resetFields();
    search();
  };

  const toggleStatus = (record: CustomerListItem, checked: boolean) => {
    const apply = async () => {
      setTogglingId(record.id);
      try {
        await customerApi.updateStatus(record.id, checked ? 1 : 0);
        message.success(checked ? '已启用' : '已禁用');
        await load();
      } finally {
        setTogglingId(undefined);
      }
    };
    if (checked) {
      apply();
      return;
    }
    modal.confirm({
      title: '确认要禁用该客户吗？',
      content: DISABLE_CONFIRM_TEXT,
      okText: '确认禁用',
      cancelText: '取消',
      onOk: apply,
    });
  };

  const confirmDelete = (record: CustomerListItem) => {
    modal.confirm({
      title: '确认删除',
      content: (
        <>
          <div style={{ fontWeight: 600, marginBottom: 8 }}>
            确认删除客户「{record.displayName}（{record.customerCode}）」吗？
          </div>
          <div style={{ color: palette.sub, fontSize: 13 }}>
            删除后不可恢复。如只是暂停合作，建议改为禁用。已有询盘或单据记录的客户不能删除。
          </div>
        </>
      ),
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await customerApi.remove(record.id);
          message.success('删除成功');
          setSelected((keys) => keys.filter((k) => k !== record.id));
          await load();
        } catch (e) {
          message.error(readBizError(e).message);
        }
      },
    });
  };

  const confirmBatchDelete = () => {
    modal.confirm({
      title: '确认删除',
      content: `确认删除已选的 ${selected.length} 个客户吗？删除后不可恢复，已有询盘或单据记录的客户会被跳过。`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        const r = await customerApi.batchRemove(selected as number[]);
        const parts = [`已删除 ${r.deleted} 个`];
        if (r.referenced)
          parts.push(
            `${r.referenced} 个已有询盘或单据记录，不能删除，可改为禁用`,
          );
        if (r.missing) parts.push(`${r.missing} 个已不存在`);
        (r.referenced || r.missing ? message.warning : message.success)(
          parts.join('，'),
        );
        setSelected([]);
        await load();
      },
    });
  };

  const confirmExport = () => {
    modal.confirm({
      title: '导出客户',
      content: `将导出当前筛选条件下你可见的 ${total} 个客户。`,
      okText: '导出',
      cancelText: '取消',
      onOk: async () => {
        setExporting(true);
        try {
          const result = await customerApi.export(query);
          if (result.ok) message.success('已开始下载');
          else message.error(result.message);
        } finally {
          setExporting(false);
        }
      },
    });
  };

  const stacked = (
    top: React.ReactNode,
    bottom: React.ReactNode,
    topStyle?: React.CSSProperties,
  ) => (
    <div style={{ lineHeight: 1.5, minWidth: 0 }}>
      <div style={topStyle}>{top}</div>
      <div
        style={{
          fontSize: 12,
          color: palette.mute,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {bottom || '—'}
      </div>
    </div>
  );

  const columns: TableColumnsType<CustomerListItem> = [
    {
      title: '序号',
      key: 'index',
      width: 60,
      render: (_, __, i) => (
        <span className="num">{(page - 1) * pageSize + i + 1}</span>
      ),
    },
    {
      title: '客户编码',
      dataIndex: 'customerCode',
      width: 110,
      render: (v: string) => <span className="num">{v}</span>,
    },
    {
      title: '客户名称',
      dataIndex: 'name',
      width: 230,
      render: (_: string, r) => (
        <span title={r.displayName}>
          {r.nameMissing && <Pill tone="orange">未填客户名称</Pill>}
          {stacked(r.displayName, r.nameCn, {
            fontWeight: 600,
            color: palette.ink,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          })}
        </span>
      ),
    },
    {
      title: '国家/地区',
      dataIndex: 'country',
      width: 110,
      render: (v: string) => zhOf(v) || '—',
    },
    {
      title: '客户角色',
      dataIndex: 'customerRole',
      width: 110,
      render: (v: number) => <RolePill value={v} />,
    },
    {
      title: '潜力',
      dataIndex: 'potentialRating',
      width: 100,
      sorter: true,
      sortOrder:
        sort.sortField === 'potentialRating'
          ? sort.sortOrder === 'asc'
            ? 'ascend'
            : 'descend'
          : null,
      render: (v: number) => <Stars value={v} />,
    },
    {
      title: '经营等级',
      dataIndex: 'bizGrade',
      width: 110,
      sorter: true,
      sortOrder:
        sort.sortField === 'bizGrade'
          ? sort.sortOrder === 'asc'
            ? 'ascend'
            : 'descend'
          : null,
      render: (v: number, r) =>
        r.bizGradeAdjust ? (
          <Tooltip
            title={`计算等级 ${GRADE_NAMES[r.bizGradeCalc]}，${r.bizGradeAdjust > 0 ? '上调' : '下调'}一级：${r.bizGradeAdjustReason || '—'}${r.bizGradeAdjustByName ? ` · ${r.bizGradeAdjustByName}` : ''}`}
          >
            <span>
              <GradeBadge value={v} adjusted />
            </span>
          </Tooltip>
        ) : (
          <GradeBadge value={v} />
        ),
    },
    {
      title: '档案完整度',
      dataIndex: 'profileMask',
      width: 150,
      render: (mask: number) => {
        const done = doneOf(mask);
        const missing = MODULE_SHORT.filter((_, i) => !(mask & (1 << i)));
        return (
          <Tooltip
            title={missing.length ? `缺：${missing.join('、')}` : '已建档'}
          >
            <span
              style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}
            >
              <span
                className="num"
                style={{
                  fontWeight: 600,
                  color: done === 5 ? palette.green : palette.orange,
                }}
              >
                {done}/5
              </span>
              <Progress5 mask={mask} />
            </span>
          </Tooltip>
        );
      },
    },
    {
      title: '客户来源',
      dataIndex: 'sourceChannel',
      width: 150,
      render: (v: number, r) =>
        r.aliLevel ? `${sourceLabel(v)} · ${r.aliLevel}` : sourceLabel(v),
    },
    ...(showOwner
      ? [
          {
            title: '负责业务员',
            dataIndex: 'ownerName',
            width: 100,
            render: (v?: string) => v || '未分配',
          },
        ]
      : []),
    {
      title: '主联系人',
      key: 'contact',
      width: 210,
      render: (_, r) => stacked(r.contactName || '—', r.contactEmail),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 72,
      render: (v: number, r) => (
        <Switch
          checked={v === 1}
          disabled={!canStatus}
          loading={togglingId === r.id}
          aria-label={
            v === 1
              ? `${r.displayName} 已启用，点击禁用`
              : `${r.displayName} 已禁用，点击启用`
          }
          onChange={(checked) => toggleStatus(r, checked)}
        />
      ),
    },
    {
      title: '创建时间 / 创建人',
      key: 'created',
      width: 170,
      render: (_, r) =>
        stacked(
          <span className="num">{formatDateTime(r.createTime)}</span>,
          r.createBy,
        ),
    },
    {
      title: '更新时间 / 更新人',
      key: 'updated',
      width: 170,
      render: (_, r) =>
        stacked(
          <span className="num">{formatDateTime(r.updateTime)}</span>,
          r.updateBy,
        ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 190,
      fixed: 'right',
      render: (_, r) => (
        <div style={{ display: 'flex', gap: 12 }}>
          <a onClick={() => history.push(`${LIST_PATH}/${r.id}`)}>查看</a>
          {canEdit && (
            <a onClick={() => history.push(`${LIST_PATH}/${r.id}/edit`)}>
              编辑
            </a>
          )}
          {canTransfer && <a onClick={() => setTransferTargets([r])}>转移</a>}
          {canDelete && (
            <a style={{ color: palette.red }} onClick={() => confirmDelete(r)}>
              删除
            </a>
          )}
        </div>
      ),
    },
  ];

  // 潜力、经营等级可以排序；取消排序回到按更新时间倒序
  const onTableChange: TableProps<CustomerListItem>['onChange'] = (
    _p,
    _f,
    sorter,
    extra,
  ) => {
    if (extra.action !== 'sort' || Array.isArray(sorter)) return;
    const field = sorter.field as CustomerQuery['sortField'];
    setSort(
      sorter.order
        ? {
            sortField: field,
            sortOrder: sorter.order === 'ascend' ? 'asc' : 'desc',
          }
        : {},
    );
    setPage(1);
  };

  const label = (text: string) => (
    <span style={{ fontSize: 13, color: palette.sub }}>{text}</span>
  );
  const selectedRows = rows.filter((r) => selected.includes(r.id));

  return (
    <div style={{ color: palette.ink }}>
      <a className="zhul-skip" href="#customer-main">
        跳到主要内容
      </a>
      <PageTitle description="档案完整度 5/5 为已建档；先补有成交的客户。报价单、PI、CI 从这里带出客户信息。" />
      <Segmented
        value={tab}
        onChange={(v) => setTab(v as 'all' | 'dups')}
        style={{ marginBottom: 16 }}
        options={[
          {
            value: 'all',
            label: `全部客户${tab === 'all' ? ` ${total}` : ''}`,
          },
          {
            value: 'dups',
            label: `疑似重复${dupCount ? ` ${dupCount}` : ''}`,
          },
        ]}
      />
      {tab === 'dups' ? (
        <DuplicatesTab
          canMerge={canMerge}
          onCount={setDupCount}
          onMerged={load}
        />
      ) : (
        <>
          <Form
            form={form}
            layout="vertical"
            onFinish={search}
            style={{
              background: palette.card,
              border: `1px solid ${palette.hairline}`,
              borderRadius: 16,
              padding: '20px 24px 4px',
              marginBottom: 20,
            }}
          >
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
                columnGap: 24,
              }}
            >
              <Form.Item name="customerCode" label={label('客户编码')}>
                <Input placeholder="请输入客户编码" allowClear />
              </Form.Item>
              <Form.Item name="name" label={label('客户名称')}>
                <Input placeholder="英文名、中文名或简称" allowClear />
              </Form.Item>
              <Form.Item name="contactName" label={label('主要联系人')}>
                <Input placeholder="联系人姓名" allowClear />
              </Form.Item>
              <Form.Item name="country" label={label('国家/地区')}>
                <Select
                  placeholder="全部"
                  allowClear
                  showSearch={{ optionFilterProp: 'label' }}
                  options={countryOptions}
                />
              </Form.Item>
              <Form.Item name="customerRole" label={label('客户角色')}>
                <Select placeholder="全部" allowClear options={ROLE_OPTIONS} />
              </Form.Item>
              <Form.Item name="bizGrade" label={label('经营等级')}>
                <Select
                  placeholder="全部"
                  allowClear
                  options={BIZ_GRADE_OPTIONS}
                />
              </Form.Item>
              <Form.Item name="profileStatus" label={label('建档状态')}>
                <Select
                  placeholder="全部"
                  allowClear
                  options={PROFILE_STATUS_OPTIONS}
                />
              </Form.Item>
              <Form.Item name="missingModules" label={label('缺的模块')}>
                <Select
                  mode="multiple"
                  placeholder="全部"
                  allowClear
                  maxTagCount="responsive"
                  options={MODULE_OPTIONS}
                />
              </Form.Item>
              <Form.Item name="sourceChannel" label={label('客户来源')}>
                <Select placeholder="全部" allowClear options={sourceOptions} />
              </Form.Item>
              {showOwner && (
                <Form.Item name="ownerId" label={label('负责业务员')}>
                  <Select
                    placeholder="全部"
                    allowClear
                    showSearch={{ optionFilterProp: 'label' }}
                    options={owners?.owners.map((o) => ({
                      value: o.id,
                      label: o.name,
                    }))}
                  />
                </Form.Item>
              )}
              <Form.Item name="status" label={label('状态')}>
                <Select
                  placeholder="全部"
                  allowClear
                  options={STATUS_OPTIONS}
                />
              </Form.Item>
            </div>
            <div
              style={{
                display: 'flex',
                gap: 12,
                justifyContent: 'flex-end',
                marginBottom: 16,
              }}
            >
              <Button onClick={reset}>重置</Button>
              <Button type="primary" htmlType="submit">
                查询
              </Button>
            </div>
          </Form>

          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              gap: 12,
              marginBottom: 12,
              flexWrap: 'wrap',
            }}
          >
            <span style={{ color: palette.sub, fontSize: 14 }}>
              共 <span className="num">{total}</span> 条记录
            </span>
            <div style={{ display: 'flex', gap: 12 }}>
              {canAdd && (
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => history.push(`${LIST_PATH}/new`)}
                >
                  新增客户
                </Button>
              )}
              <Tooltip title="批量导入即将上线">
                <Button icon={<UploadOutlined />} disabled>
                  导入
                </Button>
              </Tooltip>
              {canExport && (
                <Button
                  icon={<DownloadOutlined />}
                  loading={exporting}
                  disabled={total === 0}
                  onClick={confirmExport}
                >
                  导出
                </Button>
              )}
            </div>
          </div>

          {selected.length > 0 && (
            <div
              role="toolbar"
              aria-label="批量操作"
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 12,
                padding: '10px 16px',
                marginBottom: 12,
                borderRadius: 12,
                background: palette.accentSoft,
                border: `1px solid ${palette.accentLine}`,
              }}
            >
              <span style={{ fontWeight: 600, color: palette.link }}>
                已选 <span className="num">{selected.length}</span> 条
              </span>
              <div style={{ display: 'flex', gap: 8 }}>
                <Button type="link" onClick={() => setSelected([])}>
                  取消选择
                </Button>
                {canTransfer && (
                  <Button
                    icon={<SwapOutlined />}
                    onClick={() => setTransferTargets(selectedRows)}
                  >
                    批量转移
                  </Button>
                )}
                {canDelete && (
                  <Button
                    danger
                    icon={<DeleteOutlined />}
                    onClick={confirmBatchDelete}
                  >
                    批量删除
                  </Button>
                )}
              </div>
            </div>
          )}

          <div
            style={{
              background: palette.card,
              borderRadius: 16,
              border: `1px solid ${palette.hairline}`,
              overflow: 'hidden',
            }}
          >
            {error ? (
              <ErrorHint message={error} onRetry={load} />
            ) : firstLoad ? (
              <div style={{ padding: 24 }}>
                <Skeleton active paragraph={{ rows: 6 }} />
              </div>
            ) : (
              <Table<CustomerListItem>
                rowKey="id"
                columns={columns}
                dataSource={rows}
                loading={loading}
                sticky
                scroll={{ x: showOwner ? 2030 : 1930 }}
                onChange={onTableChange}
                rowSelection={{
                  selectedRowKeys: selected,
                  onChange: setSelected,
                  // antd 的类型里没有 aria-label，但会透传给复选框；读屏靠它知道选的是哪一行
                  getCheckboxProps: (r: CustomerListItem) =>
                    ({
                      'aria-label': `选择 ${r.displayName}`,
                    }) as unknown as Partial<CheckboxProps>,
                }}
                pagination={{
                  current: page,
                  pageSize,
                  total,
                  showSizeChanger: true,
                  pageSizeOptions: [10, 20, 50],
                  showTotal: (t) => `共 ${t} 条，每页 ${pageSize} 条`,
                  onChange: (p, s) => {
                    setPage(p);
                    setPageSize(s);
                    setSelected([]);
                  },
                }}
                locale={{
                  emptyText: hasCondition ? (
                    <EmptyHint
                      title="没有找到匹配的客户"
                      secondaryText="清空筛选"
                      onSecondary={reset}
                    />
                  ) : (
                    <EmptyHint
                      title="还没有客户档案"
                      description="成交或需要做单的客户建档后，报价单和 PI 会自动带出客户信息。"
                      actionText={canAdd ? '新增客户' : undefined}
                      onAction={() => history.push(`${LIST_PATH}/new`)}
                    />
                  ),
                }}
              />
            )}
          </div>
        </>
      )}

      <TransferModal
        open={transferTargets.length > 0}
        customers={transferTargets}
        onClose={() => setTransferTargets([])}
        onDone={() => {
          setTransferTargets([]);
          setSelected([]);
          load();
        }}
      />
    </div>
  );
};

export default CustomerListPage;
