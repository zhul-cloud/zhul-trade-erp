import { PlusOutlined, WarningOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  DrawerForm,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { Alert, App, Badge, Button, Space, Tree } from 'antd';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import type { MenuItem } from '@/pages/system/menu/service';
import { getMenuTree } from '@/pages/system/menu/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import type { SaveTenantPackagePayload, TenantPackageItem } from './service';
import {
  createPackage,
  deletePackage,
  getPackageDeleteCheck,
  getPackageList,
  updatePackage,
  updatePackageStatus,
} from './service';

const flattenAllKeys = (list: MenuItem[]): number[] => {
  const ids: number[] = [];
  const walk = (nodes: MenuItem[]) =>
    nodes.forEach((n) => {
      ids.push(n.id);
      if (n.children) walk(n.children);
    });
  walk(list);
  return ids;
};

const flattenParentKeys = (list: MenuItem[]): number[] => {
  const ids: number[] = [];
  const walk = (nodes: MenuItem[]) =>
    nodes.forEach((n) => {
      if (n.children && n.children.length > 0) {
        ids.push(n.id);
        walk(n.children);
      }
    });
  walk(list);
  return ids;
};

const toAntdTree = (list: MenuItem[]): any[] =>
  list.map((item) => ({
    title: item.name,
    key: item.id,
    children: item.children ? toAntdTree(item.children) : undefined,
  }));

const TenantPackagePage: React.FC = () => {
  const { message, modal } = App.useApp();
  const access = useAccess();
  const { palette } = useAppTheme();
  const actionRef = useRef<ActionType>(undefined);

  const [rawMenuTree, setRawMenuTree] = useState<MenuItem[]>([]);
  useEffect(() => {
    getMenuTree().then(setRawMenuTree);
  }, []);
  const antdMenuTree = useMemo(() => toAntdTree(rawMenuTree), [rawMenuTree]);

  // ---------- 新增 / 编辑 ----------
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<TenantPackageItem | null>(null);
  const [checkedKeys, setCheckedKeys] = useState<number[]>([]);
  const [expandedKeys, setExpandedKeys] = useState<number[]>([]);
  const [menuError, setMenuError] = useState(false);

  const openCreate = () => {
    setEditing(null);
    setCheckedKeys([]);
    setExpandedKeys(flattenParentKeys(rawMenuTree));
    setMenuError(false);
    setDrawerOpen(true);
  };
  const openEdit = (row: TenantPackageItem) => {
    setEditing(row);
    setCheckedKeys(row.menuIds);
    setExpandedKeys(flattenParentKeys(rawMenuTree));
    setMenuError(false);
    setDrawerOpen(true);
  };

  const submitSave = async (values: Record<string, unknown>) => {
    if (checkedKeys.length === 0) {
      setMenuError(true);
      return false;
    }
    const payload: SaveTenantPackagePayload = {
      name: values.name as string,
      description: (values.description as string) || undefined,
      menuIds: checkedKeys,
    };
    if (editing) {
      await updatePackage(editing.id, payload);
      message.success(`套餐「${payload.name}」已更新`);
    } else {
      await createPackage(payload);
      message.success(`套餐「${payload.name}」创建成功`);
    }
    actionRef.current?.reload();
    return true;
  };

  // ---------- 启用 / 禁用 ----------
  const toggleStatus = (row: TenantPackageItem) => {
    const disabling = row.status === 1;
    modal.confirm({
      title: disabling ? '确认禁用套餐？' : '确认启用套餐？',
      content: disabling
        ? row.tenantCount > 0
          ? `套餐「${row.name}」当前有 ${row.tenantCount} 个租户在使用。禁用后，该套餐不可被新租户选择，但已绑定的租户暂不受影响。`
          : `确认禁用套餐「${row.name}」吗？禁用后该套餐不可被新租户选择。`
        : `启用后，套餐「${row.name}」可被新租户选择使用。`,
      okText: disabling ? '确认禁用' : '确认启用',
      okButtonProps: disabling
        ? { danger: true }
        : { style: { background: palette.green, borderColor: palette.green } },
      cancelText: '取消',
      onOk: async () => {
        await updatePackageStatus(row.id, disabling ? 0 : 1);
        message.success('操作成功');
        actionRef.current?.reload();
      },
    });
  };

  // ---------- 删除 ----------
  const handleDelete = async (row: TenantPackageItem) => {
    const check = await getPackageDeleteCheck(row.id);
    if (check.blocked) {
      modal.info({
        title: '无法删除套餐',
        width: 480,
        content: (
          <div>
            <div style={{ marginBottom: 12 }}>
              套餐「{row.name}」当前有 {check.tenantCount}{' '}
              个租户正在使用，无法删除。
            </div>
            <div
              style={{
                background: palette.inset,
                borderRadius: 8,
                padding: '12px 16px',
                color: palette.mute,
              }}
            >
              <div style={{ fontWeight: 600, marginBottom: 6 }}>建议操作：</div>
              <div>· 如需停用该套餐，可使用「禁用」操作；</div>
              <div>
                · 如需彻底删除，请先将相关租户切换至其他套餐，再执行删除。
              </div>
            </div>
          </div>
        ),
        okText: '我知道了',
      });
      return;
    }
    modal.confirm({
      title: '确认删除套餐？',
      content: `确认删除套餐「${row.name}」吗？此操作不可恢复。`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await deletePackage(row.id);
        message.success('套餐已删除');
        actionRef.current?.reload();
      },
    });
  };

  const columns: ProColumns<TenantPackageItem>[] = [
    {
      title: '套餐名称',
      dataIndex: 'name',
      hideInTable: true,
      fieldProps: { placeholder: '请输入套餐名称' },
    },
    {
      title: '状态',
      dataIndex: 'status',
      hideInTable: true,
      valueType: 'select',
      valueEnum: { 1: { text: '启用' }, 0: { text: '禁用' } },
    },
    {
      title: '套餐名称',
      dataIndex: 'name',
      search: false,
      width: 160,
    },
    {
      title: '套餐描述',
      dataIndex: 'description',
      search: false,
      width: 260,
      ellipsis: true,
    },
    {
      title: '包含菜单数',
      dataIndex: 'menuCount',
      search: false,
      width: 110,
      align: 'center',
      render: (_, r) => `${r.menuCount} 个菜单`,
    },
    {
      title: '绑定租户数',
      dataIndex: 'tenantCount',
      search: false,
      width: 100,
      align: 'center',
      render: (_, r) => (r.tenantCount > 0 ? r.tenantCount : '—'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      search: false,
      width: 80,
      align: 'center',
      render: (_, r) =>
        r.status === 1 ? (
          <Badge status="success" text="启用" />
        ) : (
          <Badge status="error" text="禁用" />
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
      title: '操作',
      valueType: 'option',
      width: 160,
      render: (_, row) => [
        access['tenant:package:edit'] && (
          <a key="edit" onClick={() => openEdit(row)}>
            编辑
          </a>
        ),
        access['tenant:package:status'] && (
          <a
            key="status"
            style={{ color: row.status === 1 ? palette.red : palette.green }}
            onClick={() => toggleStatus(row)}
          >
            {row.status === 1 ? '禁用' : '启用'}
          </a>
        ),
        access['tenant:package:delete'] && (
          <a
            key="delete"
            style={{ color: palette.red }}
            onClick={() => handleDelete(row)}
          >
            删除
          </a>
        ),
      ],
    },
  ];

  const checkedCount = checkedKeys.length;
  const boundTenantCount = editing?.tenantCount ?? 0;

  return (
    <>
      <ProTable<TenantPackageItem>
        headerTitle="套餐管理"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto', defaultCollapsed: false }}
        options={false}
        request={async (params) => {
          const res = await getPackageList({
            current: params.current,
            pageSize: params.pageSize,
            name: params.name,
            status: params.status,
          });
          return res;
        }}
        toolBarRender={() => [
          access['tenant:package:add'] && (
            <Button
              key="add"
              type="primary"
              icon={<PlusOutlined />}
              onClick={openCreate}
            >
              新增套餐
            </Button>
          ),
        ]}
      />

      {/* 新增 / 编辑套餐 */}
      <DrawerForm
        title={editing ? `编辑套餐 — ${editing.name}` : '新增套餐'}
        width={720}
        open={drawerOpen}
        onOpenChange={setDrawerOpen}
        onFinish={submitSave}
        initialValues={
          editing
            ? { name: editing.name, description: editing.description }
            : {}
        }
        drawerProps={{ destroyOnClose: true }}
        submitTimeout={2000}
      >
        <ProFormText
          name="name"
          label="套餐名称"
          placeholder="请输入套餐名称，如「标准版」「专业版」"
          rules={[
            {
              required: true,
              min: 2,
              max: 100,
              message: '请输入2-100字符的套餐名称',
            },
          ]}
        />
        <ProFormTextArea
          name="description"
          label="套餐描述"
          placeholder="选填，描述该套餐的适用场景和功能范围"
          fieldProps={{ rows: 3, maxLength: 500 }}
        />

        {editing && boundTenantCount > 0 && (
          <Alert
            type="warning"
            showIcon
            icon={<WarningOutlined />}
            style={{ marginBottom: 16 }}
            message={`注意：此套餐已被 ${boundTenantCount} 个租户使用，修改菜单范围后，所有绑定租户的可见菜单将立即变更。`}
          />
        )}

        <div
          style={{
            marginBottom: 8,
            display: 'flex',
            justifyContent: 'space-between',
          }}
        >
          <Space size={16}>
            <span>
              可用菜单 <span style={{ color: palette.red }}>*</span>
            </span>
            <a
              onClick={() =>
                setCheckedKeys(
                  checkedCount === flattenAllKeys(rawMenuTree).length
                    ? []
                    : flattenAllKeys(rawMenuTree),
                )
              }
            >
              {checkedCount === flattenAllKeys(rawMenuTree).length &&
              checkedCount > 0
                ? '取消全选'
                : '全选'}
            </a>
            <a onClick={() => setExpandedKeys(flattenAllKeys(rawMenuTree))}>
              展开全部
            </a>
            <a onClick={() => setExpandedKeys([])}>折叠全部</a>
          </Space>
          <span style={{ color: palette.mute }}>
            已选：{checkedCount} 个菜单
          </span>
        </div>
        <div
          style={{
            height: 400,
            overflow: 'auto',
            border: `1px solid ${menuError ? palette.red : palette.hairline}`,
            borderRadius: 8,
            padding: 12,
          }}
        >
          <Tree
            checkable
            checkedKeys={checkedKeys}
            expandedKeys={expandedKeys}
            onExpand={(keys) => setExpandedKeys(keys as number[])}
            onCheck={(keys) => {
              setCheckedKeys(keys as number[]);
              setMenuError(false);
            }}
            treeData={antdMenuTree}
          />
        </div>
        {menuError && (
          <div style={{ color: palette.red, fontSize: 12, marginTop: 4 }}>
            请至少选择一个菜单
          </div>
        )}
      </DrawerForm>
    </>
  );
};

export default TenantPackagePage;
