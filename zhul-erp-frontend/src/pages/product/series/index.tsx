import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { App, Button, Input, Select } from 'antd';
import { Ban, CircleCheck, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import React, { useEffect, useRef, useState } from 'react';
import { useColumnSettings } from '@/components/ColumnSettings';
import { useConfirmDanger } from '@/components/ConfirmDanger';
import EllipsisText, { ellipsisRender } from '@/components/EllipsisText';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
} from '@/components/ListLayout';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { proAuditColumns } from '@/components/UserName';
import { usePagination } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { Pill } from '../components/Pills';
import { brandApi, type Series, seriesApi } from '../service';
import { ProductThemeProvider } from '../theme';

const SeriesPage: React.FC = () => {
  const pager = usePagination(20);
  const { message, modal } = App.useApp();
  const confirmDanger = useConfirmDanger();
  const access = useAccess();
  const actionRef = useRef<ActionType>(undefined);
  const [editing, setEditing] = useState<Series | null>(null);
  const [open, setOpen] = useState(false);

  const openForm = (row: Series | null) => {
    setEditing(row);
    setOpen(true);
  };

  const toggleStatus = (row: Series) => {
    const disabling = row.status === 1;
    // 停用用公共危险确认（错误色按钮、默认焦点在取消），启用用普通确认
    (disabling ? confirmDanger : modal.confirm)({
      title: disabling
        ? `停用「${row.seriesName}」？`
        : `启用「${row.seriesName}」？`,
      content: disabling
        ? '停用后新建商品时选不到这个系列，已有商品仍正常显示。'
        : '启用后可以在新建商品时选择这个系列。',
      okText: disabling ? '停用' : '启用',
      cancelText: '取消',
      onOk: async () => {
        await seriesApi.setStatus(row.id, disabling ? 0 : 1);
        message.success(disabling ? '已停用' : '已启用');
        actionRef.current?.reload();
      },
    });
  };

  const remove = (row: Series) => {
    confirmDanger({
      title: `删除「${row.seriesName}」？`,
      content: '删除后系列不再出现在列表中，该品牌下同名系列也不能重新创建。',
      onOk: async () => {
        await seriesApi.remove(row.id);
        message.success('已删除');
        actionRef.current?.reload();
      },
    });
  };

  const brandOptions = async () => {
    const list = await brandApi.options();
    return list.map((b) => ({ label: b.brandName, value: b.id }));
  };
  /** 搜索条件：状态切换后立即查询，关键词、品牌点「查询」才生效 */
  const [filters, setFilters] = useState<{
    keyword?: string;
    brandId?: number;
    status?: number;
  }>({});
  const [keyword, setKeyword] = useState('');
  const [draftBrand, setDraftBrand] = useState<number>();
  const [brands, setBrands] = useState<{ label: string; value: number }[]>([]);
  // 首次查询返回前没有总数，主卡不显示「共 0 条」
  const [total, setTotal] = useState<number>();
  useEffect(() => {
    brandOptions()
      .then(setBrands)
      .catch(() => setBrands([]));
  }, []);

  const columns: ProColumns<Series>[] = [
    {
      fixed: 'left',
      title: '系列名称',
      dataIndex: 'seriesName',
      search: false,
      width: 200,
      render: (_, r) => (
        <EllipsisText text={r.seriesName} style={{ fontWeight: 600 }} />
      ),
    },
    {
      title: '品牌',
      dataIndex: 'brandName',
      search: false,
      width: 160,
      render: ellipsisRender,
    },
    {
      title: '简介',
      dataIndex: 'description',
      search: false,
      width: 280,
      render: (_, r) => <EllipsisText text={r.description} />,
    },
    {
      title: '商品数',
      dataIndex: 'productCount',
      search: false,
      width: 90,
      align: 'right',
      render: (_, r) => <span className="num">{r.productCount}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      search: false,
      width: 90,
      render: (_, r) =>
        r.status === 1 ? (
          <Pill tone="green">启用</Pill>
        ) : (
          <Pill tone="gray">已停用</Pill>
        ),
    },
    ...proAuditColumns<Series>(),
    {
      title: '操作',
      hideInTable:
        !access['product:series:edit'] && !access['product:series:delete'],
      fixed: 'right',
      valueType: 'option',
      // 编辑放行内；停用/启用、删除收进「更多」（ui-redesign S6）
      width: rowActionsWidth(2),
      render: (_, row) => {
        const canEdit = !!access['product:series:edit'];
        const canDelete = !!access['product:series:delete'];
        return (
          <RowActions
            inline={[
              canEdit && {
                key: 'edit',
                label: '编辑',
                icon: <Pencil size={18} />,
                onClick: () => openForm(row),
              },
            ]}
            more={[
              canEdit && {
                key: 'status',
                label: row.status === 1 ? '停用' : '启用',
                icon:
                  row.status === 1 ? (
                    <Ban size={16} />
                  ) : (
                    <CircleCheck size={16} />
                  ),
              },
              canDelete && {
                key: 'delete',
                label: '删除',
                icon: <Trash2 size={16} />,
                danger: true,
                disabledReason:
                  row.productCount > 0
                    ? `已有 ${row.productCount} 个商品使用，请先停用`
                    : undefined,
              },
            ]}
            onMore={(key) => {
              if (key === 'status') toggleStatus(row);
              if (key === 'delete') remove(row);
            }}
          />
        );
      },
    },
  ];
  const columnsSet = useColumnSettings('product-series', columns);

  return (
    <ProductThemeProvider>
      <PageHeader
        help="品牌下可选的分组（如 Siemens 的 S7-1200），同一个品牌下系列名称不重复，不同品牌可以同名"
        extra={
          access['product:series:add'] && (
            <Button
              type="primary"
              icon={<Plus size="1em" />}
              onClick={() => openForm(null)}
            >
              新增系列
            </Button>
          )
        }
      />
      <FilterCard
        onSearch={() =>
          setFilters({
            ...filters,
            keyword: keyword.trim(),
            brandId: draftBrand,
          })
        }
        onReset={() => {
          setKeyword('');
          setDraftBrand(undefined);
          setFilters({});
        }}
      >
        <FilterField label="系列名称" span={2}>
          <Input
            allowClear
            prefix={<Search size={16} />}
            placeholder="系列名称"
            aria-label="系列名称"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={() =>
              setFilters({
                ...filters,
                keyword: keyword.trim(),
                brandId: draftBrand,
              })
            }
          />
        </FilterField>
        <FilterField label="状态">
          <Select
            allowClear
            placeholder="全部"
            aria-label="状态"
            value={filters.status}
            // 状态下拉切换后立即查询
            onChange={(v) => setFilters({ ...filters, status: v })}
            options={[
              { value: 1, label: '启用' },
              { value: 0, label: '停用' },
            ]}
          />
        </FilterField>
        <FilterField label="品牌">
          <Select
            allowClear
            showSearch={{ optionFilterProp: 'label' }}
            placeholder="全部"
            aria-label="品牌"
            value={draftBrand}
            onChange={setDraftBrand}
            options={brands}
          />
        </FilterField>
      </FilterCard>
      <MainCard settings={columnsSet.settings} count={total}>
        <ProTable<Series>
          scroll={{ x: tableScrollX(columnsSet.columns) }}
          rowKey="id"
          actionRef={actionRef}
          columns={columnsSet.columns}
          search={false}
          options={false}
          toolBarRender={false}
          params={filters}
          request={async (params) => {
            const res = await seriesApi.page({
              page: params.current,
              pageSize: params.pageSize,
              keyword: filters.keyword || undefined,
              brandId: filters.brandId,
              status: filters.status,
            });
            setTotal(res.total);
            return { data: res.records, total: res.total, success: true };
          }}
          pagination={pager}
          locale={{
            emptyText: tableEmpty(
              '还没有系列。系列是可选的，商品可以不属于任何系列。',
            ),
          }}
        />
      </MainCard>
      <ModalForm<{ brandId: number; seriesName: string; description?: string }>
        submitter={{ searchConfig: { submitText: '保存' } }}
        name="seriesEdit"
        title={editing ? '编辑系列' : '新增系列'}
        open={open}
        onOpenChange={setOpen}
        width={520}
        modalProps={{ destroyOnHidden: true }}
        initialValues={editing ? { ...editing } : {}}
        onFinish={async (values) => {
          if (editing)
            await seriesApi.update(editing.id, {
              seriesName: values.seriesName,
              description: values.description,
            });
          else await seriesApi.create(values);
          message.success(editing ? '已保存' : '已新增');
          actionRef.current?.reload();
          return true;
        }}
      >
        <ProFormSelect
          name="brandId"
          label="所属品牌"
          request={brandOptions}
          disabled={!!editing}
          extra={
            editing
              ? '系列创建后不能换品牌（商品的系列必须属于商品的品牌）'
              : undefined
          }
          rules={[{ required: true, message: '请选择品牌' }]}
          showSearch
        />
        <ProFormText
          name="seriesName"
          label="系列名称"
          placeholder="如 S7-1200"
          rules={[
            { required: true, message: '请输入系列名称' },
            { max: 64, message: '系列名称不超过 64 个字符' },
          ]}
          extra="同一品牌下不区分大小写，S7-1200 和 s7-1200 视为同一个系列"
        />
        <ProFormTextArea
          name="description"
          label="简介"
          fieldProps={{ maxLength: 500, showCount: true }}
        />
      </ModalForm>
    </ProductThemeProvider>
  );
};

export default SeriesPage;
