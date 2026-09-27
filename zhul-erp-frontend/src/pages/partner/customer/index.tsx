import {
  DeleteOutlined,
  DownloadOutlined,
  PlusOutlined,
  SwapOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import type { CheckboxProps, TableColumnsType } from 'antd';
import {
  App,
  Button,
  Form,
  Input,
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
import { formatDateTime } from '@/utils/format';
import {
  GradePill,
  LIST_PATH,
  PageTitle,
  RolePill,
  TransferModal,
} from './components';
import {
  DISABLE_CONFIRM_TEXT,
  GRADE_OPTIONS,
  labelOf,
  ROLE_OPTIONS,
  SOURCE_OPTIONS,
} from './constants';
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

const compact = (q: CustomerQuery): CustomerQuery =>
  Object.fromEntries(
    Object.entries(q).filter(([, v]) => v !== undefined && v !== ''),
  ) as CustomerQuery;

const CustomerListPage: React.FC = () => {
  const { message, modal } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const { palette } = useAppTheme();
  const [form] = Form.useForm<CustomerQuery>();
  const { options: countryOptions, zhOf } = useCountries();

  const canAdd = !!access['partner:customer:add'];
  const canEdit = !!access['partner:customer:edit'];
  const canDelete = !!access['partner:customer:delete'];
  const canStatus = !!access['partner:customer:status'];
  const canTransfer = !!access['partner:customer:transfer'];
  const canExport = !!access['partner:customer:export'];

  const [query, setQuery] = useState<CustomerQuery>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
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
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const data = await customerApi.page({ ...query, page, pageSize });
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
  }, [query, page, pageSize]);

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
            确认删除客户「{record.name}（{record.customerCode}）」吗？
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
      render: (v: string, r) => (
        <span title={v}>
          {stacked(v, r.nameCn, {
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
      title: '等级',
      dataIndex: 'customerGrade',
      width: 80,
      render: (v: number) => <GradePill value={v} />,
    },
    {
      title: '客户来源',
      dataIndex: 'sourceChannel',
      width: 120,
      render: (v: number) => labelOf(SOURCE_OPTIONS, v) ?? '—',
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
              ? `${r.name} 已启用，点击禁用`
              : `${r.name} 已禁用，点击启用`
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

  const label = (text: string) => (
    <span style={{ fontSize: 13, color: palette.sub }}>{text}</span>
  );
  const selectedRows = rows.filter((r) => selected.includes(r.id));

  return (
    <div style={{ color: palette.ink }}>
      <a className="zhul-skip" href="#customer-main">
        跳到主要内容
      </a>
      <PageTitle
        title="客户管理"
        description="维护成交客户与重点跟进客户的档案，报价单、PI、CI 从这里带出客户信息。"
      />

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
          <Form.Item name="customerGrade" label={label('客户等级')}>
            <Select placeholder="全部" allowClear options={GRADE_OPTIONS} />
          </Form.Item>
          <Form.Item name="sourceChannel" label={label('客户来源')}>
            <Select placeholder="全部" allowClear options={SOURCE_OPTIONS} />
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
            <Select placeholder="全部" allowClear options={STATUS_OPTIONS} />
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
            scroll={{ x: showOwner ? 1750 : 1650 }}
            rowSelection={{
              selectedRowKeys: selected,
              onChange: setSelected,
              // antd 的类型里没有 aria-label，但会透传给复选框；读屏靠它知道选的是哪一行
              getCheckboxProps: (r: CustomerListItem) =>
                ({
                  'aria-label': `选择 ${r.name}`,
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
