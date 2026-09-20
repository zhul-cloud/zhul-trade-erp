import { PlusOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  ProFormDigit,
  ProFormText,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { App, Button, Tooltip } from 'antd';
import dayjs from 'dayjs';
import React, { useRef, useState } from 'react';
import { Pill } from '../components/Pills';
import { type Category, categoryApi } from '../service';
import { PageHeader, ProductThemeProvider } from '../theme';

const CategoryPage: React.FC = () => {
  const { message, modal } = App.useApp();
  const access = useAccess();
  const actionRef = useRef<ActionType>(undefined);
  const [editing, setEditing] = useState<Category | null>(null);
  const [open, setOpen] = useState(false);

  const openForm = (row: Category | null) => {
    setEditing(row);
    setOpen(true);
  };

  const toggleStatus = (row: Category) => {
    const disabling = row.status === 1;
    modal.confirm({
      title: disabling
        ? `停用「${row.categoryName}」？`
        : `启用「${row.categoryName}」？`,
      content: disabling
        ? '停用后新建商品时选不到这个品类，已有商品仍正常显示。'
        : '启用后可以在新建商品时选择这个品类。',
      okText: disabling ? '停用' : '启用',
      cancelText: '取消',
      onOk: async () => {
        await categoryApi.setStatus(row.id, disabling ? 0 : 1);
        message.success(disabling ? '已停用' : '已启用');
        actionRef.current?.reload();
      },
    });
  };

  const remove = (row: Category) => {
    modal.confirm({
      title: `删除「${row.categoryName}」？`,
      content: '删除后品类不再出现在列表中，同编码的品类也不能重新创建。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await categoryApi.remove(row.id);
        message.success('已删除');
        actionRef.current?.reload();
      },
    });
  };

  const columns: ProColumns<Category>[] = [
    {
      title: '编码或名称',
      dataIndex: 'keyword',
      hideInTable: true,
      fieldProps: { placeholder: '输入品类编码或名称' },
    },
    {
      title: '状态',
      dataIndex: 'statusFilter',
      hideInTable: true,
      valueType: 'select',
      valueEnum: { 1: { text: '启用' }, 0: { text: '停用' } },
    },
    {
      title: '品类编码',
      dataIndex: 'categoryCode',
      search: false,
      render: (_, r) => (
        <code style={{ fontFamily: "'IBM Plex Mono', monospace" }}>
          {r.categoryCode}
        </code>
      ),
    },
    { title: '品类名称', dataIndex: 'categoryName', search: false },
    {
      title: '排序',
      dataIndex: 'sortOrder',
      search: false,
      align: 'right',
      render: (_, r) => <span className="num">{r.sortOrder}</span>,
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
        access['product:category:edit'] && (
          <a key="edit" onClick={() => openForm(row)}>
            编辑
          </a>
        ),
        access['product:category:edit'] && (
          <a key="status" onClick={() => toggleStatus(row)}>
            {row.status === 1 ? '停用' : '启用'}
          </a>
        ),
        access['product:category:delete'] &&
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

  const locked = !!editing && editing.productCount > 0;

  return (
    <ProductThemeProvider>
      <PageHeader
        eyebrow="PRODUCT MASTER"
        title="品类"
        description="商品的分类，也是独立站品类页面地址的一部分，所以编码有商品后不能再改。"
        actions={
          access['product:category:add'] && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => openForm(null)}
            >
              新增品类
            </Button>
          )
        }
      />
      <ProTable<Category>
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto', defaultCollapsed: false }}
        options={false}
        request={async (params) => {
          const res = await categoryApi.page({
            page: params.current,
            pageSize: params.pageSize,
            keyword: params.keyword,
            status:
              params.statusFilter === undefined
                ? undefined
                : Number(params.statusFilter),
          });
          return { data: res.records, total: res.total, success: true };
        }}
        pagination={{ pageSize: 20, showTotal: (t) => `共 ${t} 条记录` }}
        locale={{
          emptyText: '还没有品类。先新增一个，新建商品时才能选择品类。',
        }}
      />
      <ModalForm<{
        categoryCode: string;
        categoryName: string;
        sortOrder?: number;
      }>
        title={editing ? '编辑品类' : '新增品类'}
        open={open}
        onOpenChange={setOpen}
        width={520}
        modalProps={{ destroyOnHidden: true }}
        initialValues={editing ? { ...editing } : { sortOrder: 0 }}
        onFinish={async (values) => {
          if (editing) await categoryApi.update(editing.id, values);
          else await categoryApi.create(values);
          message.success(editing ? '已保存' : '已新增');
          actionRef.current?.reload();
          return true;
        }}
      >
        <ProFormText
          name="categoryCode"
          label="品类编码"
          placeholder="如 controllers"
          disabled={locked}
          tooltip={
            locked ? undefined : '小写字母开头，只含小写字母、数字和下划线'
          }
          extra={
            locked
              ? '这个品类下已有商品，编码已被使用，不能修改（名称和排序仍可修改）'
              : '独立站的品类页面地址会用到它，创建后有商品就不能改了'
          }
          rules={[
            { required: true, message: '请输入品类编码' },
            { max: 32, message: '编码不超过 32 个字符' },
            {
              pattern: /^[a-z][a-z0-9_]*$/,
              message: '编码须以小写字母开头，只含小写字母、数字和下划线',
            },
          ]}
        />
        <ProFormText
          name="categoryName"
          label="品类名称"
          placeholder="如 PLC & Controllers"
          rules={[
            { required: true, message: '请输入品类名称' },
            { max: 64, message: '名称不超过 64 个字符' },
          ]}
        />
        <ProFormDigit
          name="sortOrder"
          label="排序"
          min={0}
          fieldProps={{ precision: 0 }}
          extra="数字越小越靠前"
        />
      </ModalForm>
    </ProductThemeProvider>
  );
};

export default CategoryPage;
