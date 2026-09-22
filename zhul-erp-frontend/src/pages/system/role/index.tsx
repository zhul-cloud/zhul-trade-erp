import {
  CheckCircleOutlined,
  LockOutlined,
  SafetyOutlined,
  SearchOutlined,
  TeamOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  DrawerForm,
  ProFormDependency,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import {
  App,
  Badge,
  Button,
  Card,
  Form,
  Input,
  Radio,
  Space,
  Tag,
  Tree,
} from 'antd';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { getDeptList } from '@/pages/system/dept/service';
import type { MenuItem } from '@/pages/system/menu/service';
import {
  assignRoleMenus,
  getMenuTree,
  getRoleMenuIds,
} from '@/pages/system/menu/service';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import type { RoleItem, RoleStats } from './service';
import {
  createRole,
  deleteRole,
  getRoleDeleteCheck,
  getRoleDeptIds,
  getRoleList,
  getRoleStats,
  updateRole,
} from './service';

const PERMISSION_SCOPE_MAP: Record<number, string> = {
  0: '无',
  1: '全部数据',
  2: '自定义部门',
  3: '仅本人数据',
};

const SCOPE_OPTIONS = [
  { value: 1, title: '全部数据', desc: '可查看本租户所有人的数据' },
  { value: 2, title: '自定义部门', desc: '选择后仅可查看所选部门的数据' },
  { value: 3, title: '仅本人数据（默认）', desc: '仅可查看本人创建的数据' },
];

const typeTagMap: Record<number, { text: string; color: string }> = {
  1: { text: '目录', color: 'blue' },
  2: { text: '菜单', color: 'green' },
  3: { text: '按钮', color: 'orange' },
};

type DrawerMode = 'create' | 'edit';

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

const filterMenuTree = (list: MenuItem[], keyword: string): MenuItem[] => {
  if (!keyword.trim()) return list;
  const kw = keyword.trim().toLowerCase();
  const walk = (nodes: MenuItem[]): MenuItem[] =>
    nodes.reduce<MenuItem[]>((acc, n) => {
      const filteredChildren = n.children ? walk(n.children) : [];
      const selfMatch = n.name.toLowerCase().includes(kw);
      if (selfMatch) {
        acc.push(n);
      } else if (filteredChildren.length > 0) {
        acc.push({ ...n, children: filteredChildren });
      }
      return acc;
    }, []);
  return walk(list);
};

const toAntdTree = (list: MenuItem[]): any[] =>
  list.map((item) => ({
    title: (
      <Space size={6}>
        {item.name}
        <Tag color={typeTagMap[item.type]?.color}>
          {typeTagMap[item.type]?.text}
        </Tag>
      </Space>
    ),
    key: item.id,
    children: item.children ? toAntdTree(item.children) : undefined,
  }));

const RolePage: React.FC = () => {
  const { palette: p } = useAppTheme();
  const actionRef = useRef<ActionType>();
  const { message, modal } = App.useApp();
  const access = useAccess();

  const [stats, setStats] = useState<RoleStats | null>(null);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<DrawerMode>('create');
  const [editingRole, setEditingRole] = useState<RoleItem | null>(null);
  const [editingDeptIds, setEditingDeptIds] = useState<number[]>([]);
  const [deptOptions, setDeptOptions] = useState<
    { label: string; value: number }[]
  >([]);

  const [permOpen, setPermOpen] = useState(false);
  const [permRole, setPermRole] = useState<RoleItem | null>(null);
  const [checkedKeys, setCheckedKeys] = useState<number[]>([]);
  const [rawMenuTree, setRawMenuTree] = useState<MenuItem[]>([]);
  const [menuKeyword, setMenuKeyword] = useState('');
  const [expandedKeys, setExpandedKeys] = useState<number[]>([]);

  const canAdd = !!(access as Record<string, unknown>)['system:role:add'];
  const canEdit = !!(access as Record<string, unknown>)['system:role:edit'];
  const canDelete = !!(access as Record<string, unknown>)['system:role:delete'];
  const canAssign = !!(access as Record<string, unknown>)['system:role:assign'];

  const loadStats = async () => {
    setStats(await getRoleStats());
  };

  const loadDepts = async () => {
    const list = await getDeptList();
    setDeptOptions(list.map((d) => ({ label: d.name, value: d.id })));
  };

  useEffect(() => {
    loadStats();
    loadDepts();
  }, []);

  const displayMenuTree = useMemo(
    () => filterMenuTree(rawMenuTree, menuKeyword),
    [rawMenuTree, menuKeyword],
  );
  const antdMenuTree = useMemo(
    () => toAntdTree(displayMenuTree),
    [displayMenuTree],
  );

  useEffect(() => {
    if (menuKeyword.trim()) {
      setExpandedKeys(flattenParentKeys(displayMenuTree));
    }
  }, [displayMenuTree, menuKeyword]);

  const isEdit = drawerMode === 'edit';

  const openCreate = () => {
    setDrawerMode('create');
    setEditingRole(null);
    setEditingDeptIds([]);
    setDrawerOpen(true);
  };

  const openEdit = async (record: RoleItem) => {
    setDrawerMode('edit');
    setEditingRole(record);
    if (record.permissionScope === 2) {
      setEditingDeptIds(await getRoleDeptIds(record.code));
    } else {
      setEditingDeptIds([]);
    }
    setDrawerOpen(true);
  };

  const openAssign = async (record: RoleItem) => {
    setPermRole(record);
    setMenuKeyword('');
    setPermOpen(true);
    const [tree, ids] = await Promise.all([
      getMenuTree(),
      getRoleMenuIds(record.code),
    ]);
    setRawMenuTree(tree);
    setExpandedKeys(flattenParentKeys(tree));
    setCheckedKeys(ids);
  };

  const handleSubmit = async (
    values: Record<string, unknown>,
  ): Promise<boolean> => {
    const payload = {
      name: values.name as string,
      permissionScope: values.permissionScope as number,
      status: values.status ? 1 : 0,
      remark: (values.remark as string) || '',
      deptIds:
        values.permissionScope === 2
          ? ((values.deptIds as number[]) ?? [])
          : undefined,
    };
    try {
      if (isEdit && editingRole) {
        await updateRole(editingRole.id, payload);
        message.success('保存成功');
      } else {
        await createRole(payload);
        message.success('新增成功');
      }
      actionRef.current?.reload();
      loadStats();
      return true;
    } catch {
      message.error(isEdit ? '保存失败' : '新增失败');
      return false;
    }
  };

  const handleToggleStatus = async (record: RoleItem) => {
    const newStatus = record.status === 1 ? 0 : 1;
    try {
      await updateRole(record.id, { name: record.name, status: newStatus });
      message.success(newStatus === 1 ? '已启用' : '已禁用');
      actionRef.current?.reload();
      loadStats();
    } catch {
      message.error('操作失败');
    }
  };

  const doDelete = async (id: number) => {
    try {
      await deleteRole(id);
      message.success('删除成功');
      actionRef.current?.reload();
      loadStats();
    } catch {
      message.error('删除失败');
    }
  };

  const handleDeleteClick = async (record: RoleItem) => {
    const check = await getRoleDeleteCheck(record.id);
    if (check.builtIn) {
      modal.info({
        title: '无法删除角色',
        content: '内置角色不可删除，如需调整请联系系统管理员。',
        okText: '我知道了',
      });
      return;
    }
    if (check.blocked) {
      const extra = check.userCount - check.sampleUserNames.length;
      modal.info({
        title: '无法删除角色',
        width: 480,
        content: (
          <div>
            <div style={{ marginBottom: 12 }}>
              <WarningOutlined style={{ color: p.orange, marginRight: 8 }} />
              角色「{record.name}」已分配给 {check.userCount} 名用户：
            </div>
            <div
              style={{
                background: p.inset,
                borderRadius: 8,
                padding: '12px 16px',
                color: p.mute,
              }}
            >
              {check.sampleUserNames.join('、')}
              {extra > 0 ? ` 等 ${check.userCount} 人` : ''}
            </div>
            <div style={{ marginTop: 12, color: p.mute }}>
              请先在用户管理中移除这些用户的该角色，再执行删除操作。
            </div>
          </div>
        ),
        okText: '我知道了',
      });
      return;
    }
    modal.confirm({
      title: '确认删除',
      content: `确认删除角色「${record.name}」？`,
      okButtonProps: { danger: true },
      onOk: () => doDelete(record.id),
    });
  };

  const columns: ProColumns<RoleItem>[] = [
    { title: '序号', valueType: 'index', width: 60, search: false },
    {
      title: '角色名称',
      dataIndex: 'name',
      width: 160,
      render: (_, record) => (
        <Space size={6}>
          {record.name}
          {record.isBuiltIn === 1 && <Tag color="default">内置</Tag>}
        </Space>
      ),
    },
    { title: '角色编码', dataIndex: 'code', width: 140 },
    {
      title: '数据权限',
      dataIndex: 'permissionScope',
      width: 110,
      search: false,
      render: (_, record) =>
        PERMISSION_SCOPE_MAP[record.permissionScope] ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (_, record) =>
        record.status === 1 ? (
          <Badge status="success" text="启用" />
        ) : (
          <Badge status="error" text="禁用" />
        ),
      valueEnum: {
        0: { text: '禁用', status: 'Error' },
        1: { text: '启用', status: 'Success' },
      },
    },
    {
      title: '关联用户',
      dataIndex: 'userCount',
      width: 100,
      search: false,
      render: (_, record) => (
        <span style={{ color: p.link }}>{record.userCount} 人</span>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      search: false,
      width: 160,
      render: (_, r) => formatDateTime(r.createTime),
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
      render: (_, r) => formatDateTime(r.updateTime),
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
      width: 220,
      render: (_, record) => (
        <Space size={12}>
          {canAssign && <a onClick={() => openAssign(record)}>分配权限</a>}
          {canEdit && <a onClick={() => openEdit(record)}>编辑</a>}
          {canEdit && (
            <a
              style={{ color: record.status === 1 ? p.red : p.green }}
              onClick={() => handleToggleStatus(record)}
            >
              {record.status === 1 ? '禁用' : '启用'}
            </a>
          )}
          {canDelete && record.isBuiltIn !== 1 && (
            <a
              style={{ color: p.red }}
              onClick={() => handleDeleteClick(record)}
            >
              删除
            </a>
          )}
        </Space>
      ),
    },
  ];

  const statCards = stats
    ? [
        {
          icon: <SafetyOutlined />,
          color: p.link,
          bg: p.accentSoft,
          label: '角色总数',
          value: stats.total,
          hint: `覆盖 ${stats.deptCoverage} 个部门`,
        },
        {
          icon: <CheckCircleOutlined />,
          color: p.green,
          bg: p.greenSoft,
          label: '启用角色',
          value: stats.enabledCount,
          hint: `占比 ${stats.enabledRate}%`,
        },
        {
          icon: <LockOutlined />,
          color: p.violet,
          bg: p.violetSoft,
          label: '内置角色',
          value: stats.builtInCount,
          hint: '不可删除',
        },
        {
          icon: <TeamOutlined />,
          color: p.orange,
          bg: p.orangeSoft,
          label: '自定义角色',
          value: stats.customCount,
          hint: '可自由配置',
        },
      ]
    : [];

  const permCheckedCount = checkedKeys.length;

  return (
    <>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(4, 1fr)',
          gap: 16,
          marginBottom: 16,
        }}
      >
        {statCards.map((s) => (
          <Card key={s.label} styles={{ body: { padding: 20 } }}>
            <Space align="center">
              <div
                style={{
                  width: 36,
                  height: 36,
                  borderRadius: 10,
                  background: s.bg,
                  color: s.color,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 18,
                }}
              >
                {s.icon}
              </div>
              <span style={{ color: p.mute }}>{s.label}</span>
            </Space>
            <div style={{ fontSize: 28, fontWeight: 700, marginTop: 12 }}>
              {s.value}
            </div>
            <div style={{ fontSize: 12, color: p.green, marginTop: 4 }}>
              {s.hint}
            </div>
          </Card>
        ))}
      </div>

      <ProTable<RoleItem>
        headerTitle="角色列表"
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={getRoleList}
        pagination={{ pageSize: 10 }}
        search={{ labelWidth: 'auto' }}
        toolBarRender={() => [
          canAdd && (
            <Button key="add" type="primary" onClick={openCreate}>
              新增角色
            </Button>
          ),
        ]}
      />

      {/* 新增 / 编辑角色 */}
      <DrawerForm
        title={isEdit ? `编辑角色 - ${editingRole?.name ?? ''}` : '新增角色'}
        width={480}
        open={drawerOpen}
        onOpenChange={setDrawerOpen}
        onFinish={handleSubmit}
        initialValues={
          isEdit && editingRole
            ? {
                name: editingRole.name,
                permissionScope: editingRole.permissionScope || 3,
                deptIds: editingDeptIds,
                remark: editingRole.remark,
                status: editingRole.status === 1,
              }
            : { permissionScope: 3, status: true }
        }
        drawerProps={{ destroyOnClose: true }}
      >
        <ProFormText
          name="name"
          label="角色名称"
          placeholder="请输入角色名称（2-64字符）"
          fieldProps={{ maxLength: 64 }}
          rules={[
            {
              required: true,
              min: 2,
              max: 64,
              message: '请输入2-64字符的角色名称',
            },
          ]}
        />

        <div style={{ marginBottom: 24 }}>
          <div style={{ fontSize: 13, marginBottom: 8 }}>角色编码</div>
          <Input disabled value={isEdit ? editingRole?.code : '系统自动生成'} />
        </div>

        <Form.Item
          name="permissionScope"
          label={
            <span>
              数据权限范围 <span style={{ color: p.red }}>*</span>
            </span>
          }
          rules={[{ required: true, message: '请选择数据权限范围' }]}
          style={{ marginBottom: 0 }}
        >
          <Radio.Group style={{ width: '100%' }}>
            <Space direction="vertical" style={{ width: '100%' }}>
              {SCOPE_OPTIONS.map((opt) => (
                <Radio key={opt.value} value={opt.value}>
                  <div style={{ fontWeight: 500 }}>{opt.title}</div>
                  <div style={{ fontSize: 12, color: p.mute }}>{opt.desc}</div>
                </Radio>
              ))}
            </Space>
          </Radio.Group>
        </Form.Item>

        <ProFormDependency name={['permissionScope']}>
          {({ permissionScope }) =>
            permissionScope === 2 && (
              <ProFormSelect
                name="deptIds"
                label={false}
                mode="multiple"
                placeholder="请选择可查看数据的部门"
                options={deptOptions}
                fieldProps={{ style: { marginBottom: 24 } }}
              />
            )
          }
        </ProFormDependency>

        <ProFormTextArea
          name="remark"
          label="备注"
          placeholder="填写角色用途说明（选填，最长256字符）"
          fieldProps={{ rows: 3, maxLength: 256 }}
        />

        <ProFormSwitch name="status" label="状态" />
      </DrawerForm>

      {/* 分配权限 */}
      <DrawerForm
        title={`分配权限 - ${permRole?.name ?? ''}`}
        width={480}
        open={permOpen}
        onOpenChange={setPermOpen}
        submitter={{
          render: () => [
            <Button key="cancel" onClick={() => setPermOpen(false)}>
              取消
            </Button>,
            <Button
              key="save"
              type="primary"
              onClick={async () => {
                if (!permRole) return;
                try {
                  await assignRoleMenus(permRole.code, checkedKeys);
                  message.success('权限保存成功');
                  setPermOpen(false);
                } catch {
                  message.error('权限保存失败');
                }
              }}
            >
              保存权限
            </Button>,
          ],
        }}
        drawerProps={{ destroyOnClose: true }}
      >
        <div style={{ marginBottom: 12 }}>
          <Space size={16}>
            <a onClick={() => setCheckedKeys(flattenAllKeys(rawMenuTree))}>
              全选
            </a>
            <a onClick={() => setCheckedKeys([])}>取消全选</a>
            <a
              onClick={() =>
                setExpandedKeys(flattenParentKeys(displayMenuTree))
              }
            >
              展开全部
            </a>
            <a onClick={() => setExpandedKeys([])}>折叠全部</a>
          </Space>
        </div>
        <Input
          placeholder="搜索菜单/按钮名称"
          prefix={<SearchOutlined />}
          allowClear
          style={{ marginBottom: 12 }}
          value={menuKeyword}
          onChange={(e) => setMenuKeyword(e.target.value)}
        />
        <Tree
          checkable
          checkedKeys={checkedKeys}
          expandedKeys={expandedKeys}
          onExpand={(keys) => setExpandedKeys(keys as number[])}
          onCheck={(keys) => setCheckedKeys(keys as number[])}
          treeData={antdMenuTree}
        />
        <div style={{ marginTop: 16, color: p.mute }}>
          已选 {permCheckedCount} 个权限
        </div>
      </DrawerForm>
    </>
  );
};

export default RolePage;
