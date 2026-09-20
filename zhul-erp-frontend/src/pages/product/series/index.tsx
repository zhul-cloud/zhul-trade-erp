import { PlusOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { App, Button, Tooltip } from 'antd';
import dayjs from 'dayjs';
import React, { useRef, useState } from 'react';
import { Pill } from '../components/Pills';
import { brandApi, type Series, seriesApi } from '../service';
import { PageHeader, ProductThemeProvider } from '../theme';

const SeriesPage: React.FC = () => {
  const { message, modal } = App.useApp();
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
    modal.confirm({
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
    modal.confirm({
      title: `删除「${row.seriesName}」？`,
      content: '删除后系列不再出现在列表中，该品牌下同名系列也不能重新创建。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
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

  const columns: ProColumns<Series>[] = [
    {
      title: '系列名称',
      dataIndex: 'keyword',
      hideInTable: true,
      fieldProps: { placeholder: '输入系列名称' },
    },
    {
      title: '品牌',
      dataIndex: 'brandFilter',
      hideInTable: true,
      valueType: 'select',
      request: brandOptions,
    },
    {
      title: '状态',
      dataIndex: 'statusFilter',
      hideInTable: true,
      valueType: 'select',
      valueEnum: { 1: { text: '启用' }, 0: { text: '停用' } },
    },
    {
      title: '系列名称',
      dataIndex: 'seriesName',
      search: false,
      render: (_, r) => <span style={{ fontWeight: 600 }}>{r.seriesName}</span>,
    },
    { title: '品牌', dataIndex: 'brandName', search: false },
    {
      title: '简介',
      dataIndex: 'description',
      search: false,
      ellipsis: true,
      render: (_, r) => r.description || '—',
    },
    {
      title: '商品数',
      dataIndex: 'productCount',
      search: false,
      align: 'right',
      render: (_, r) => <span className="num">{r.productCount}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      search: false,
      render: (_, r) =>
        r.status === 1 ? (
          <Pill tone="green">启用</Pill>
        ) : (
          <Pill tone="gray">已停用</Pill>
        ),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      search: false,
      render: (_, r) => (
        <span className="num">
          {dayjs(r.updateTime).format('YYYY-MM-DD HH:mm')}
        </span>
      ),
    },
    {
      title: '操作',
      valueType: 'option',
      render: (_, row) => [
        access['product:series:edit'] && (
          <a key="edit" onClick={() => openForm(row)}>
            编辑
          </a>
        ),
        access['product:series:edit'] && (
          <a key="status" onClick={() => toggleStatus(row)}>
            {row.status === 1 ? '停用' : '启用'}
          </a>
        ),
        access['product:series:delete'] &&
          (row.productCount > 0 ? (
            <Tooltip
              key="delete"
              title={`已有 ${row.productCount} 个商品使用，请先停用`}
            >
              <span
                aria-disabled="true"
                style={{ opacity: 0.6, cursor: 'not-allowed' }}
              >
                删除
              </span>
            </Tooltip>
          ) : (
            <a key="delete" onClick={() => remove(row)}>
              删除
            </a>
          )),
      ],
    },
  ];

  return (
    <ProductThemeProvider>
      <PageHeader
        eyebrow="PRODUCT MASTER"
        title="系列"
        description="品牌下可选的分组（如 Siemens 的 S7-1200），同一个品牌下系列名称不重复，不同品牌可以同名。"
        actions={
          access['product:series:add'] && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => openForm(null)}
            >
              新增系列
            </Button>
          )
        }
      />
      <ProTable<Series>
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto', defaultCollapsed: false }}
        options={false}
        request={async (params) => {
          const res = await seriesApi.page({
            page: params.current,
            pageSize: params.pageSize,
            keyword: params.keyword,
            brandId:
              params.brandFilter === undefined
                ? undefined
                : Number(params.brandFilter),
            status:
              params.statusFilter === undefined
                ? undefined
                : Number(params.statusFilter),
          });
          return { data: res.records, total: res.total, success: true };
        }}
        pagination={{ pageSize: 20, showTotal: (t) => `共 ${t} 条记录` }}
        locale={{
          emptyText: '还没有系列。系列是可选的，商品可以不属于任何系列。',
        }}
      />
      <ModalForm<{ brandId: number; seriesName: string; description?: string }>
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
