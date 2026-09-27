import {
  DeleteOutlined,
  DownloadOutlined,
  PlusOutlined,
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
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { LIST_PATH, PageTitle, SupplierTypePill } from './components';
import { DISABLE_CONFIRM_TEXT, SUPPLIER_TYPE_OPTIONS } from './constants';
import {
  categoryGroups,
  ScopeBrandTags,
  useScopeCatalog,
} from './productScope';
import {
  readBizError,
  type SupplierItem,
  type SupplierQuery,
  supplierApi,
} from './service';

const STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '禁用' },
];

/** 去掉空串，避免把空条件传给后端 */
const compact = (q: SupplierQuery): SupplierQuery =>
  Object.fromEntries(
    Object.entries(q).filter(([, v]) => v !== undefined && v !== ''),
  ) as SupplierQuery;

const SupplierListPage: React.FC = () => {
  const { message, modal } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  const { palette } = useAppTheme();
  const [form] = Form.useForm<SupplierQuery>();
  const { brands, tree } = useScopeCatalog();

  const canAdd = !!access['partner:supplier:add'];
  const canEdit = !!access['partner:supplier:edit'];
  const canDelete = !!access['partner:supplier:delete'];
  const canStatus = !!access['partner:supplier:status'];
  const canExport = !!access['partner:supplier:export'];

  const [query, setQuery] = useState<SupplierQuery>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [rows, setRows] = useState<SupplierItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [firstLoad, setFirstLoad] = useState(true);
  const [error, setError] = useState<string>();
  const [selected, setSelected] = useState<React.Key[]>([]);
  const [togglingId, setTogglingId] = useState<number>();
  const [exporting, setExporting] = useState(false);

  const hasCondition = Object.keys(query).length > 0;

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const data = await supplierApi.page({ ...query, page, pageSize });
      // 删除后当前页可能被删空，退回上一页
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

  const toggleStatus = (record: SupplierItem, checked: boolean) => {
    const apply = async () => {
      setTogglingId(record.id);
      try {
        await supplierApi.updateStatus(record.id, checked ? 1 : 0);
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
      title: '确认要禁用该供应商吗？',
      content: DISABLE_CONFIRM_TEXT,
      okText: '确认禁用',
      cancelText: '取消',
      onOk: apply,
    });
  };

  const confirmDelete = (record: SupplierItem) => {
    modal.confirm({
      title: '确认删除',
      content: (
        <>
          <div style={{ fontWeight: 600, marginBottom: 8 }}>
            确认删除供应商「{record.name}（{record.supplierCode}）」吗？
          </div>
          <div style={{ color: palette.sub, fontSize: 13 }}>
            删除后不可恢复。已有询盘单、报价中的引用不受影响；如只是暂停合作，建议改为禁用。
          </div>
        </>
      ),
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await supplierApi.remove(record.id);
        message.success('删除成功');
        setSelected((keys) => keys.filter((k) => k !== record.id));
        await load();
      },
    });
  };

  const confirmBatchDelete = () => {
    modal.confirm({
      title: '确认删除',
      content: `确认删除已选的 ${selected.length} 条供应商数据吗？删除后不可恢复，已有询盘单、报价中的引用不受影响。`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        const result = await supplierApi.batchRemove(selected as number[]);
        if (result.skipped > 0) {
          message.success(
            `已删除 ${result.deleted} 条，${result.skipped} 条已不存在`,
          );
        } else {
          message.success('删除成功');
        }
        setSelected([]);
        await load();
      },
    });
  };

  const confirmExport = () => {
    modal.confirm({
      title: '导出供应商',
      content: `将导出当前筛选条件下的 ${total} 条供应商。导出数据将对银行账号进行脱敏处理。`,
      okText: '导出',
      cancelText: '取消',
      onOk: async () => {
        setExporting(true);
        try {
          const result = await supplierApi.export(query);
          if (result.ok) {
            message.success('已开始下载');
          } else {
            message.error(result.message);
          }
        } finally {
          setExporting(false);
        }
      },
    });
  };

  const stacked = (time: string, by: string) => (
    <div style={{ lineHeight: 1.5 }}>
      <div className="num">{formatDateTime(time)}</div>
      <div style={{ fontSize: 12, color: palette.mute }}>{by || '—'}</div>
    </div>
  );

  const columns: TableColumnsType<SupplierItem> = [
    {
      title: '序号',
      key: 'index',
      width: 64,
      render: (_, __, i) => (
        <span className="num">{(page - 1) * pageSize + i + 1}</span>
      ),
    },
    {
      title: '供应商编码',
      dataIndex: 'supplierCode',
      width: 130,
      render: (v: string) => <span className="num">{v}</span>,
    },
    {
      title: '供应商名称',
      dataIndex: 'name',
      width: 220,
      ellipsis: { showTitle: true },
      render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span>,
    },
    {
      title: '供应商简称',
      dataIndex: 'shortName',
      width: 120,
      ellipsis: { showTitle: true },
      render: (v: string) => v || '—',
    },
    {
      title: '供应商类型',
      dataIndex: 'supplierType',
      width: 110,
      render: (v: number) => <SupplierTypePill value={v} />,
    },
    {
      title: '主营品牌',
      dataIndex: 'productScopes',
      width: 260,
      render: (_: unknown, r) => <ScopeBrandTags scopes={r.productScopes} />,
    },
    {
      title: '统一社会信用代码',
      dataIndex: 'creditCode',
      width: 190,
      render: (v: string) =>
        v ? <span className="num">{v}</span> : <span>—</span>,
    },
    {
      title: '联系人',
      dataIndex: 'contactName',
      width: 100,
      ellipsis: { showTitle: true },
      render: (v: string) => v || '—',
    },
    {
      title: '联系电话',
      dataIndex: 'contactPhone',
      width: 140,
      render: (v: string) => (v ? <span className="num">{v}</span> : '—'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: number, record) => (
        <Switch
          checked={v === 1}
          disabled={!canStatus}
          loading={togglingId === record.id}
          aria-label={
            v === 1
              ? `${record.name} 已启用，点击禁用`
              : `${record.name} 已禁用，点击启用`
          }
          onChange={(checked) => toggleStatus(record, checked)}
        />
      ),
    },
    {
      title: '创建时间 / 创建人',
      key: 'created',
      width: 170,
      render: (_, r) => stacked(r.createTime, r.createBy),
    },
    {
      title: '更新时间 / 更新人',
      key: 'updated',
      width: 170,
      render: (_, r) => stacked(r.updateTime, r.updateBy),
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <div style={{ display: 'flex', gap: 12 }}>
          <a onClick={() => history.push(`${LIST_PATH}/${record.id}`)}>查看</a>
          {canEdit && (
            <a onClick={() => history.push(`${LIST_PATH}/${record.id}/edit`)}>
              编辑
            </a>
          )}
          {canDelete && (
            <a
              style={{ color: palette.red }}
              onClick={() => confirmDelete(record)}
            >
              删除
            </a>
          )}
        </div>
      ),
    },
  ];

  const filterLabel: React.CSSProperties = { fontSize: 13, color: palette.sub };

  return (
    <div style={{ color: palette.ink }}>
      <a className="zhul-skip" href="#supplier-main">
        跳到主要内容
      </a>
      <PageTitle
        title="供应商管理"
        description="维护企业供应商工商信息、联系方式与结算账户，作为采购与结算业务的统一数据来源。"
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
          <Form.Item
            name="supplierCode"
            label={<span style={filterLabel}>供应商编码</span>}
          >
            <Input placeholder="请输入供应商编码" allowClear />
          </Form.Item>
          <Form.Item
            name="name"
            label={<span style={filterLabel}>供应商名称</span>}
          >
            <Input placeholder="请输入供应商名称" allowClear />
          </Form.Item>
          <Form.Item
            name="creditCode"
            label={<span style={filterLabel}>统一社会信用代码</span>}
          >
            <Input placeholder="请输入社会信用代码" allowClear />
          </Form.Item>
          <Form.Item
            name="supplierType"
            label={<span style={filterLabel}>供应商类型</span>}
          >
            <Select
              placeholder="全部"
              allowClear
              options={SUPPLIER_TYPE_OPTIONS}
            />
          </Form.Item>
          <Form.Item
            name="status"
            label={<span style={filterLabel}>状态</span>}
          >
            <Select placeholder="全部" allowClear options={STATUS_OPTIONS} />
          </Form.Item>
          <Form.Item
            name="brandId"
            label={<span style={filterLabel}>主营品牌</span>}
          >
            <Select
              placeholder="全部"
              allowClear
              showSearch={{
                filterOption: (input, option) =>
                  !!option &&
                  [option.label, ...option.aliases].some((t) =>
                    t.toLowerCase().includes(input.trim().toLowerCase()),
                  ),
              }}
              options={brands.map((b) => ({
                value: b.id,
                label: b.brandName,
                aliases: b.aliases ?? [],
              }))}
            />
          </Form.Item>
          <Form.Item
            name="categoryId"
            label={<span style={filterLabel}>主营品类</span>}
            tooltip="同时选了主营品牌时，主营该品牌全部品类的供应商也会列出"
          >
            <Select
              placeholder="全部"
              allowClear
              showSearch={{ optionFilterProp: 'label' }}
              options={categoryGroups(tree)}
            />
          </Form.Item>
          <Form.Item label=" " style={{ gridColumn: '-2 / -1' }}>
            <div
              style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}
            >
              <Button onClick={reset}>重置</Button>
              <Button type="primary" htmlType="submit">
                查询
              </Button>
            </div>
          </Form.Item>
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
              新增供应商
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
          <Table<SupplierItem>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={loading}
            sticky
            scroll={{ x: 1720 }}
            rowSelection={{
              selectedRowKeys: selected,
              onChange: setSelected,
              // antd 的类型里没有 aria-label，但会透传给复选框；读屏靠它知道选的是哪一行
              getCheckboxProps: (r: SupplierItem) =>
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
                  title="没有找到匹配的供应商"
                  description="编码、名称支持模糊搜索；统一社会信用代码需要输入完整的 18 位。"
                  secondaryText="清空筛选"
                  onSecondary={reset}
                />
              ) : (
                <EmptyHint
                  title="暂无供应商数据，点击新增开始创建"
                  description="新增时填写编码、名称和类型即可保存，工商、联系、结算信息可以以后再补。"
                  actionText={canAdd ? '新增供应商' : undefined}
                  onAction={() => history.push(`${LIST_PATH}/new`)}
                />
              ),
            }}
          />
        )}
      </div>
    </div>
  );
};

export default SupplierListPage;
