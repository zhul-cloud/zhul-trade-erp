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
import {
  Download,
  Eye,
  Pencil,
  Plus,
  Search,
  Trash2,
  Upload,
} from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { EmptyHint, ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { LIST_PATH, SupplierTypePill } from './components';
import {
  DISABLE_CONFIRM_TEXT,
  SUPPLIER_TYPE_SELECT_OPTIONS,
} from './constants';
import {
  ScopeBrandTags,
  topCategoryOptions,
  useScopeCatalog,
} from './productScope';
import {
  readBizError,
  type SupplierItem,
  type SupplierQuery,
  supplierApi,
} from './service';

/** 帮助文字：help-texts.md「供应商库」 */
export const HELP = {
  page: '供应商的工商信息、联系方式与结算账户，采购与结算都从这里取',
  categoryId: '同时选了主营品牌时，主营该品牌全部品类的供应商也会列出',
} as const;

/** 收在「更多筛选」里的条件 */
const MORE_KEYS: (keyof SupplierQuery)[] = [
  'contact',
  'creditCode',
  'supplierType',
  'brandId',
  'categoryId',
];

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
  const [pageSize, setPageSize] = usePageSize(20);
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

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

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
    ...auditColumns<SupplierItem>(),
    {
      title: '操作',
      key: 'actions',
      // 查看、编辑放行内，删除收进「更多」（ui-redesign S6）
      width: rowActionsWidth(canDelete ? 3 : 2),
      fixed: 'right',
      render: (_, record) => (
        <RowActions
          inline={[
            {
              key: 'view',
              label: '查看',
              icon: <Eye size={18} />,
              onClick: () => history.push(`${LIST_PATH}/${record.id}`),
            },
            canEdit && {
              key: 'edit',
              label: '编辑',
              icon: <Pencil size={18} />,
              onClick: () => history.push(`${LIST_PATH}/${record.id}/edit`),
            },
          ]}
          more={[
            canDelete && {
              key: 'delete',
              label: '删除',
              icon: <Trash2 size={16} />,
              danger: true,
            },
          ]}
          onMore={() => confirmDelete(record)}
        />
      ),
    },
  ];

  const moreCount = MORE_KEYS.filter(
    (k) => query[k] != null && query[k] !== '',
  ).length;
  const input = (name: keyof SupplierQuery, label: string, ph: string) => (
    <FilterField label={label}>
      <Form.Item name={name} noStyle>
        <Input
          allowClear
          placeholder={ph}
          aria-label={label}
          onPressEnter={search}
        />
      </Form.Item>
    </FilterField>
  );

  return (
    <div style={{ color: palette.ink }}>
      <PageHeader
        help={HELP.page}
        extra={
          <>
            <Tooltip title="批量导入即将上线">
              <span>
                <Button type="text" icon={<Upload size={16} />} disabled>
                  导入
                </Button>
              </span>
            </Tooltip>
            {canExport && (
              <Button
                type="text"
                icon={<Download size={16} />}
                style={{ color: total === 0 ? undefined : palette.link }}
                loading={exporting}
                disabled={total === 0}
                onClick={confirmExport}
              >
                导出
              </Button>
            )}
            {canAdd && (
              <Button
                type="primary"
                icon={<Plus size={18} />}
                onClick={() => history.push(`${LIST_PATH}/new`)}
              >
                新增供应商
              </Button>
            )}
          </>
        }
      />

      <Form form={form} component={false}>
        <FilterCard
          onSearch={search}
          onReset={reset}
          minColumn={150}
          moreCount={moreCount}
          more={
            <>
              {input('contact', '联系人', '姓名、电话、微信或邮箱')}
              {input('creditCode', '统一社会信用代码', '完整的 18 位')}
              <FilterField label="供应商类型">
                <Form.Item name="supplierType" noStyle>
                  <Select
                    placeholder="全部"
                    allowClear
                    aria-label="供应商类型"
                    options={SUPPLIER_TYPE_SELECT_OPTIONS}
                  />
                </Form.Item>
              </FilterField>
              <FilterField label="主营品牌">
                <Form.Item name="brandId" noStyle>
                  <Select
                    placeholder="全部"
                    allowClear
                    aria-label="主营品牌"
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
              </FilterField>
              <FilterField label="主营品类" help={HELP.categoryId}>
                <Form.Item name="categoryId" noStyle>
                  <Select
                    placeholder="全部"
                    allowClear
                    aria-label="主营品类"
                    showSearch={{ optionFilterProp: 'label' }}
                    options={topCategoryOptions(tree)}
                  />
                </Form.Item>
              </FilterField>
            </>
          }
        >
          <FilterField label="供应商名称" span={2}>
            <Form.Item name="name" noStyle>
              <Input
                allowClear
                prefix={<Search size={16} />}
                placeholder="供应商名称"
                aria-label="供应商名称"
                onPressEnter={search}
              />
            </Form.Item>
          </FilterField>
          {input('supplierCode', '供应商编码', '供应商编码')}
          <FilterField label="状态">
            <Form.Item name="status" noStyle>
              <Select
                placeholder="全部"
                allowClear
                aria-label="状态"
                options={STATUS_OPTIONS}
                // 状态下拉切换后立即查询（interface-style）
                onChange={() => search()}
              />
            </Form.Item>
          </FilterField>
        </FilterCard>
      </Form>

      <MainCard
        left={
          selected.length > 0 ? (
            <>
              <b style={{ color: palette.ink }}>
                已选 <span className="num">{selected.length}</span> 条
              </b>
              <button
                type="button"
                className="zhul-link"
                onClick={() => setSelected([])}
              >
                取消选择
              </button>
            </>
          ) : (
            `共 ${total} 条`
          )
        }
        right={
          selected.length > 0 &&
          canDelete && (
            <div role="toolbar" aria-label="批量操作">
              <Button
                danger
                icon={<Trash2 size={16} />}
                onClick={confirmBatchDelete}
              >
                批量删除
              </Button>
            </div>
          )
        }
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
            scroll={{ x: tableScrollX(columns) }}
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
              ...PAGER_STYLE,
              current: page,
              pageSize,
              total,
              pageSizeOptions: [10, 20, 50],
              onChange: (p, s) => {
                setPage(p);
                setPageSize(s);
                setSelected([]);
              },
            }}
            locale={{
              emptyText: tableEmpty(
                hasCondition ? (
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
                loading,
              ),
            }}
          />
        )}
      </MainCard>
    </div>
  );
};

export default SupplierListPage;
