import { PlusOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { App, Button, Tooltip } from 'antd';
import React, { useRef, useState } from 'react';
import { formatDateTime } from '@/utils/format';
import { Pill } from '../components/Pills';
import { DESCRIPTION_MAX } from '../constants';
import { type Category, categoryApi, readBizError } from '../service';
import { PageHeader, ProductThemeProvider } from '../theme';

const CategoryPage: React.FC = () => {
  const { message, modal } = App.useApp();
  const access = useAccess();
  const actionRef = useRef<ActionType>(undefined);
  const [editing, setEditing] = useState<Category | null>(null);
  // 新增细分品类时预选的上级品类
  const [presetParent, setPresetParent] = useState<number | null>(null);
  const [open, setOpen] = useState(false);
  const [topLevels, setTopLevels] = useState<Category[]>([]);

  const openForm = (row: Category | null, parentId: number | null = null) => {
    setEditing(row);
    setPresetParent(parentId);
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
      content: row.parentId
        ? '删除后细分品类不再出现，同编码的品类也不能重新创建。被供应商主营产品使用的细分品类不能删除，可改为停用。'
        : '删除后品类不再出现在列表中，同编码的品类也不能重新创建。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await categoryApi.remove(row.id);
          message.success('已删除');
          actionRef.current?.reload();
        } catch (e) {
          message.error(readBizError(e).message);
        }
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
      width: 200,
      render: (_, r) => (
        <code style={{ fontFamily: "'IBM Plex Mono', monospace" }}>
          {r.categoryCode}
        </code>
      ),
    },
    {
      title: '品类名称',
      dataIndex: 'categoryName',
      search: false,
      width: 240,
      render: (_, r) =>
        r.categoryNameZh ? (
          <span>
            {r.categoryNameZh}
            <span style={{ opacity: 0.6, marginLeft: 8 }}>
              {r.categoryName}
            </span>
          </span>
        ) : (
          r.categoryName
        ),
    },
    {
      title: '层级',
      dataIndex: 'parentId',
      search: false,
      width: 90,
      render: (_, r) =>
        r.parentId ? (
          <Pill tone="gray">细分品类</Pill>
        ) : (
          <Pill tone="accent">一级品类</Pill>
        ),
    },
    {
      title: '简介',
      dataIndex: 'description',
      search: false,
      ellipsis: true,
      width: 260,
      render: (_, r) =>
        r.description || <span style={{ opacity: 0.6 }}>未填写</span>,
    },
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
      title: '创建时间',
      dataIndex: 'createTime',
      search: false,
      width: 160,
      render: (_, r) => (
        <span className="num">{formatDateTime(r.createTime)}</span>
      ),
    },
    {
      title: '创建人',
      dataIndex: 'createBy',
      search: false,
      width: 90,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      search: false,
      width: 160,
      render: (_, r) => (
        <span className="num">{formatDateTime(r.updateTime)}</span>
      ),
    },
    {
      title: '更新人',
      dataIndex: 'updateBy',
      search: false,
      width: 90,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 210,
      render: (_, row) => [
        access['product:category:add'] && !row.parentId && (
          <a key="sub" onClick={() => openForm(null, row.id)}>
            + 细分品类
          </a>
        ),
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
          ((row.children?.length ?? 0) > 0 ? (
            <Tooltip key="delete" title="下面还有细分品类，请先删除或移走">
              <span
                aria-disabled="true"
                style={{ opacity: 0.6, cursor: 'not-allowed' }}
              >
                删除
              </span>
            </Tooltip>
          ) : row.productCount > 0 ? (
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
        description="一级品类用于商品和独立站，编码有商品后不能再改；细分品类用于供应商主营产品和询盘单匹配。"
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
          const tree = await categoryApi.tree();
          setTopLevels(tree);
          // 两级品类数量不大，一次取回整棵树在前端筛选：命中细分品类时保留它的一级品类
          const keyword = (params.keyword ?? '').trim().toLowerCase();
          const status =
            params.statusFilter === undefined
              ? undefined
              : Number(params.statusFilter);
          const hit = (c: Category) =>
            (!keyword ||
              [c.categoryCode, c.categoryName, c.categoryNameZh].some((v) =>
                (v ?? '').toLowerCase().includes(keyword),
              )) &&
            (status === undefined || c.status === status);
          const data: Category[] = [];
          for (const top of tree) {
            const children = (top.children ?? []).filter(hit);
            if (!hit(top) && !children.length) continue;
            // children 为空时去掉该键，避免一级品类出现空的展开按钮
            const { children: _, ...rest } = top;
            data.push(children.length ? { ...rest, children } : rest);
          }
          return { data, total: data.length, success: true };
        }}
        expandable={{ defaultExpandAllRows: false }}
        scroll={{ x: 'max-content' }}
        pagination={{ pageSize: 20, showTotal: (t) => `共 ${t} 条记录` }}
        locale={{
          emptyText: '还没有品类。先新增一个，新建商品时才能选择品类。',
        }}
      />
      <ModalForm<{
        categoryCode: string;
        categoryName: string;
        categoryNameZh?: string;
        parentId?: number | null;
        description?: string;
        sortOrder?: number;
      }>
        title={
          editing
            ? editing.parentId
              ? '编辑细分品类'
              : '编辑品类'
            : presetParent
              ? '新增细分品类'
              : '新增品类'
        }
        open={open}
        onOpenChange={setOpen}
        width={520}
        modalProps={{ destroyOnHidden: true }}
        initialValues={
          editing ? { ...editing } : { sortOrder: 0, parentId: presetParent }
        }
        onFinish={async (values) => {
          const data = { ...values, parentId: values.parentId ?? null };
          if (editing) await categoryApi.update(editing.id, data);
          else await categoryApi.create(data);
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
        <ProFormSelect
          name="parentId"
          label="上级品类"
          placeholder="不选则为一级品类"
          allowClear
          disabled={!!editing && (editing.children?.length ?? 0) > 0}
          extra="只能选一级品类；细分品类下不能再建下级。商品只能挂一级品类"
          options={topLevels
            .filter((t) => t.id !== editing?.id)
            .map((t) => ({
              value: t.id,
              label: t.categoryNameZh
                ? `${t.categoryNameZh} ${t.categoryName}`
                : t.categoryName,
            }))}
        />
        <ProFormText
          name="categoryNameZh"
          label="中文名称"
          placeholder="如 伺服驱动器"
          dependencies={['parentId']}
          rules={[
            ({ getFieldValue }) => ({
              required: !!getFieldValue('parentId'),
              message: '细分品类必须填写中文名称',
            }),
            { max: 32, message: '中文名称不超过 32 个字符' },
          ]}
          extra="业务界面优先显示中文名；细分品类必填"
        />
        <ProFormText
          name="categoryName"
          label="品类名称（英文）"
          placeholder="如 PLC & Controllers"
          rules={[
            { required: true, message: '请输入品类名称' },
            { max: 64, message: '名称不超过 64 个字符' },
          ]}
        />
        <ProFormTextArea
          name="description"
          label="品类简介"
          placeholder="一两句话介绍这个品类，独立站品类页会用到"
          fieldProps={{
            maxLength: DESCRIPTION_MAX,
            showCount: true,
            autoSize: { minRows: 3, maxRows: 6 },
          }}
          rules={[{ max: DESCRIPTION_MAX, message: '简介不能超过 500 个字符' }]}
          extra="属于品类本身，所有品牌下的商品共用；有商品后也可以修改"
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
