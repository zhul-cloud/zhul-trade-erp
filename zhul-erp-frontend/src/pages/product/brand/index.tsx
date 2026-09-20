import { PlusOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  ProFormRadio,
  ProFormText,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { App, Button, Space, Tooltip } from 'antd';
import dayjs from 'dayjs';
import React, { useRef, useState } from 'react';
import { BrandMark, Pill } from '../components/Pills';
import { type Brand, brandApi } from '../service';
import { PageHeader, ProductThemeProvider } from '../theme';

const BrandPage: React.FC = () => {
  const { message, modal } = App.useApp();
  const access = useAccess();
  const actionRef = useRef<ActionType>(undefined);
  const [editing, setEditing] = useState<Brand | null>(null);
  const [open, setOpen] = useState(false);

  const openForm = (row: Brand | null) => {
    setEditing(row);
    setOpen(true);
  };

  const toggleStatus = (row: Brand) => {
    const disabling = row.status === 1;
    modal.confirm({
      title: disabling
        ? `停用「${row.brandName}」？`
        : `启用「${row.brandName}」？`,
      content: disabling
        ? '停用后新建商品时选不到这个品牌，已有商品仍正常显示品牌名称。'
        : '启用后可以在新建商品时选择这个品牌。',
      okText: disabling ? '停用' : '启用',
      cancelText: '取消',
      onOk: async () => {
        await brandApi.setStatus(row.id, disabling ? 0 : 1);
        message.success(disabling ? '已停用' : '已启用');
        actionRef.current?.reload();
      },
    });
  };

  const remove = (row: Brand) => {
    modal.confirm({
      title: `删除「${row.brandName}」？`,
      content:
        '删除后品牌不再出现在列表中，同名品牌也不能重新创建。这个操作不能在页面上撤销。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await brandApi.remove(row.id);
        message.success('已删除');
        actionRef.current?.reload();
      },
    });
  };

  const columns: ProColumns<Brand>[] = [
    {
      title: '品牌名称',
      dataIndex: 'keyword',
      hideInTable: true,
      fieldProps: { placeholder: '输入品牌名称' },
    },
    {
      title: '状态',
      dataIndex: 'statusFilter',
      hideInTable: true,
      valueType: 'select',
      valueEnum: { 1: { text: '启用' }, 0: { text: '停用' } },
    },
    {
      title: '品牌',
      dataIndex: 'brandName',
      search: false,
      render: (_, row) => (
        <Space>
          <BrandMark name={row.brandName} color={row.brandColor} />
          <span style={{ fontWeight: 600 }}>{row.brandName}</span>
        </Space>
      ),
    },
    {
      title: '原产国',
      dataIndex: 'country',
      search: false,
      render: (_, r) => r.country || '—',
    },
    {
      title: '是否原厂正品',
      dataIndex: 'isGenuine',
      search: false,
      render: (_, r) =>
        r.isGenuine === 1 ? (
          <Pill tone="green">原厂正品</Pill>
        ) : (
          <Pill tone="orange">兼容 / 非原厂</Pill>
        ),
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
        access['product:brand:edit'] && (
          <a key="edit" onClick={() => openForm(row)}>
            编辑
          </a>
        ),
        access['product:brand:edit'] && (
          <a key="status" onClick={() => toggleStatus(row)}>
            {row.status === 1 ? '停用' : '启用'}
          </a>
        ),
        access['product:brand:delete'] &&
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
        title="品牌"
        description="商品归属的品牌，所有租户共用同一份。同一个品牌只保留一条记录。"
        actions={
          access['product:brand:add'] && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => openForm(null)}
            >
              新增品牌
            </Button>
          )
        }
      />
      <ProTable<Brand>
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto', defaultCollapsed: false }}
        options={false}
        request={async (params) => {
          const res = await brandApi.page({
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
          emptyText: '还没有品牌。先新增一个，新建商品时才能选择品牌。',
        }}
      />
      <ModalForm<{
        brandName: string;
        country?: string;
        logoUrl?: string;
        brandColor?: string;
        isGenuine: number;
      }>
        title={editing ? '编辑品牌' : '新增品牌'}
        open={open}
        onOpenChange={setOpen}
        width={520}
        modalProps={{ destroyOnHidden: true }}
        initialValues={editing ? { ...editing } : { isGenuine: 1 }}
        onFinish={async (values) => {
          if (editing) await brandApi.update(editing.id, values);
          else await brandApi.create(values);
          message.success(editing ? '已保存' : '已新增');
          actionRef.current?.reload();
          return true;
        }}
      >
        <ProFormText
          name="brandName"
          label="品牌名称"
          placeholder="如 Siemens"
          rules={[
            { required: true, message: '请输入品牌名称' },
            { max: 64, message: '品牌名称不超过 64 个字符' },
          ]}
          extra="不区分大小写，Siemens 和 siemens 视为同一个品牌"
        />
        <ProFormText
          name="country"
          label="原产国 / 地区"
          placeholder="如 Germany"
          rules={[{ max: 64 }]}
        />
        <ProFormText
          name="brandColor"
          label="品牌主题色"
          placeholder="#009999"
          rules={[
            {
              pattern: /^(#[0-9A-Fa-f]{6})?$/,
              message: '格式应为 #RRGGBB，如 #009999',
            },
          ]}
        />
        <ProFormText
          name="logoUrl"
          label="Logo 地址"
          rules={[
            { max: 256 },
            {
              pattern: /^$|^(https?:\/\/|\/(?![/\\]))\S+$/i,
              message: '地址必须以 http://、https:// 或 / 开头',
            },
          ]}
        />
        <ProFormRadio.Group
          name="isGenuine"
          label="是否原厂正品"
          options={[
            { value: 1, label: '原厂正品' },
            { value: 0, label: '兼容 / 非原厂' },
          ]}
          extra="标为「兼容 / 非原厂」的品牌，下游不能对它的商品使用「正品」类表述"
        />
      </ModalForm>
    </ProductThemeProvider>
  );
};

export default BrandPage;
