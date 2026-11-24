import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  DrawerForm,
  ProFormDependency,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
  ProFormTreeSelect,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import {
  App,
  Badge,
  Button,
  Form,
  Input,
  Radio,
  Select,
  Space,
  Spin,
  Tag,
  Tree,
} from 'antd';
import {
  Ban,
  CircleCheck,
  Pencil,
  Search,
  ShieldCheck,
  Trash2,
  TriangleAlert,
} from 'lucide-react';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useColumnSettings } from '@/components/ColumnSettings';
import { useConfirmDanger } from '@/components/ConfirmDanger';
import EllipsisText from '@/components/EllipsisText';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
  SummaryBar,
} from '@/components/ListLayout';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { proAuditColumns } from '@/components/UserName';
import {
  type WorkbenchTemplate,
  workbenchApi,
} from '@/pages/dashboard/service';
import {
  indexMenuTree,
  splitChecked,
  toggleMenuKey,
} from '@/pages/system/menu/menuCheck';
import type { MenuItem } from '@/pages/system/menu/service';
import {
  assignRoleMenus,
  getMenuTree,
  getRoleMenuIds,
} from '@/pages/system/menu/service';
import type { DeptTreeOption } from '@/pages/system/user/service';
import { getDeptTreeOptions } from '@/pages/system/user/service';
import { useAppTheme } from '@/theme/AppTheme';
import { usePagination } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
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

/** 0 是早期缺省值，后端按「仅本人」执行（DataScopeResolver），显示与编辑回填都按仅本人 */
const PERMISSION_SCOPE_MAP: Record<number, string> = {
  0: '仅本人数据',
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
  const pager = usePagination(20);
  const { palette: p } = useAppTheme();
  const actionRef = useRef<ActionType>(undefined);
  const { message, modal } = App.useApp();
  const access = useAccess();

  const [stats, setStats] = useState<RoleStats | null>(null);
  /** 搜索条件：状态切换后立即查询，名称、编码点「查询」才生效 */
  const [filters, setFilters] = useState<{
    name?: string;
    code?: string;
    status?: number;
  }>({});
  const [draft, setDraft] = useState<{ name?: string; code?: string }>({});
  // 首次查询返回前没有总数，主卡不显示「共 0 条」
  const [total, setTotal] = useState<number>();

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<DrawerMode>('create');
  const [editingRole, setEditingRole] = useState<RoleItem | null>(null);
  const [editingDeptIds, setEditingDeptIds] = useState<number[]>([]);
  const [deptOptions, setDeptOptions] = useState<DeptTreeOption[]>([]);
  const [templates, setTemplates] = useState<WorkbenchTemplate[]>([]);

  const [permOpen, setPermOpen] = useState(false);
  const [permRole, setPermRole] = useState<RoleItem | null>(null);
  const [checkedKeys, setCheckedKeys] = useState<number[]>([]);
  const [rawMenuTree, setRawMenuTree] = useState<MenuItem[]>([]);
  const [menuKeyword, setMenuKeyword] = useState('');
  const [expandedKeys, setExpandedKeys] = useState<number[]>([]);
  const [permLoading, setPermLoading] = useState(false);
  /** 分配权限的加载序号：先后打开两个角色时，丢弃先发出、后返回的旧响应 */
  const permSeq = useRef(0);
  /** 编辑角色的加载序号：先后点两个角色的编辑时，丢弃先发出、后返回的部门数据 */
  const editSeq = useRef(0);

  const canAdd = !!(access as Record<string, unknown>)['system:role:add'];
  const canEdit = !!(access as Record<string, unknown>)['system:role:edit'];
  const canDelete = !!(access as Record<string, unknown>)['system:role:delete'];
  const canAssign = !!(access as Record<string, unknown>)['system:role:assign'];

  const loadStats = async () => {
    setStats(await getRoleStats());
  };

  const loadDepts = async () => {
    setDeptOptions(await getDeptTreeOptions());
  };

  useEffect(() => {
    loadStats();
    loadDepts();
  }, []);

  useEffect(() => {
    if (!canAdd && !canEdit) return;
    workbenchApi
      .templates()
      .then(setTemplates)
      .catch(() => setTemplates([]));
  }, [canAdd, canEdit]);

  const displayMenuTree = useMemo(
    () => filterMenuTree(rawMenuTree, menuKeyword),
    [rawMenuTree, menuKeyword],
  );
  const antdMenuTree = useMemo(
    () => toAntdTree(displayMenuTree),
    [displayMenuTree],
  );
  const menuIndex = useMemo(() => indexMenuTree(rawMenuTree), [rawMenuTree]);

  useEffect(() => {
    if (menuKeyword.trim()) {
      setExpandedKeys(flattenParentKeys(displayMenuTree));
    }
  }, [displayMenuTree, menuKeyword]);

  const isEdit = drawerMode === 'edit';

  const openCreate = () => {
    editSeq.current++;
    setDrawerMode('create');
    setEditingRole(null);
    setEditingDeptIds([]);
    setDrawerOpen(true);
  };

  const openEdit = async (record: RoleItem) => {
    const seq = ++editSeq.current;
    let deptIds: number[] = [];
    if (record.permissionScope === 2) {
      try {
        deptIds = await getRoleDeptIds(record.code);
      } catch {
        if (seq === editSeq.current) message.error('角色部门加载失败，请重试');
        return;
      }
    }
    if (seq !== editSeq.current) return;
    setDrawerMode('edit');
    setEditingRole(record);
    setEditingDeptIds(deptIds);
    setDrawerOpen(true);
  };

  const openAssign = async (record: RoleItem) => {
    const seq = ++permSeq.current;
    setPermRole(record);
    setMenuKeyword('');
    // 清掉上一个角色的勾选，加载完成前不能保存：保存是整体替换，空的或旧的勾选会改掉当前角色的权限
    setCheckedKeys([]);
    setRawMenuTree([]);
    setExpandedKeys([]);
    setPermLoading(true);
    setPermOpen(true);
    try {
      const [tree, ids] = await Promise.all([
        getMenuTree(),
        getRoleMenuIds(record.code),
      ]);
      if (seq !== permSeq.current) return;
      setRawMenuTree(tree);
      setExpandedKeys(flattenParentKeys(tree));
      // 只保留当前账号能看到的菜单，看不到的保存时服务端会拒绝
      const visible = new Set(flattenAllKeys(tree));
      setCheckedKeys((ids as number[]).filter((id) => visible.has(id)));
      setPermLoading(false);
    } catch {
      if (seq !== permSeq.current) return;
      message.error('权限加载失败，请重新打开');
      setPermOpen(false);
    }
  };

  const handleSubmit = async (
    values: Record<string, unknown>,
  ): Promise<boolean> => {
    const payload = {
      name: values.name as string,
      permissionScope: values.permissionScope as number,
      workbenchTemplate: (values.workbenchTemplate as string) || 'general',
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
              <TriangleAlert
                size="1em"
                style={{ color: p.orange, marginRight: 8 }}
              />
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
    confirmDanger({
      title: `删除角色「${record.name}」？`,
      onOk: () => doDelete(record.id),
    });
  };

  const confirmDanger = useConfirmDanger();
  const columns: ProColumns<RoleItem>[] = [
    {
      fixed: 'left',
      title: '角色名称',
      dataIndex: 'name',
      width: 160,
      search: false,
      render: (_, record) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <EllipsisText text={record.name} />
          {record.isBuiltIn === 1 && <Tag color="default">内置</Tag>}
        </div>
      ),
    },
    { title: '角色编码', dataIndex: 'code', width: 140, search: false },
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
      search: false,
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
    ...proAuditColumns<RoleItem>(),
    {
      title: '操作',
      fixed: 'right',
      valueType: 'option',
      // 编辑、分配权限放行内；启用/禁用、删除收进「更多」（ui-redesign S6）
      width: rowActionsWidth(3),
      render: (_, record) => {
        const enabled = record.status === 1;
        return (
          <RowActions
            inline={[
              canEdit && {
                key: 'edit',
                label: '编辑',
                icon: <Pencil size={18} />,
                onClick: () => openEdit(record),
              },
              canAssign && {
                key: 'assign',
                label: '分配权限',
                icon: <ShieldCheck size={18} />,
                onClick: () => openAssign(record),
              },
            ]}
            more={[
              canEdit && {
                key: 'status',
                label: enabled ? '禁用' : '启用',
                icon: enabled ? <Ban size={16} /> : <CircleCheck size={16} />,
                danger: enabled,
              },
              canDelete &&
                record.isBuiltIn !== 1 && {
                  key: 'delete',
                  label: '删除',
                  icon: <Trash2 size={16} />,
                  danger: true,
                },
            ]}
            onMore={(key) => {
              if (key === 'status') {
                if (enabled) {
                  confirmDanger({
                    title: `禁用角色「${record.name}」？`,
                    okText: '禁用',
                    onOk: () => handleToggleStatus(record),
                  });
                } else {
                  handleToggleStatus(record);
                }
              }
              if (key === 'delete') handleDeleteClick(record);
            }}
          />
        );
      },
    },
  ];
  const columnsSet = useColumnSettings('system-role', columns);

  const search = () =>
    setFilters({
      ...filters,
      name: draft.name?.trim() || undefined,
      code: draft.code?.trim() || undefined,
    });

  const permCheckedCount = checkedKeys.length;

  return (
    <>
      <PageHeader
        extra={
          canAdd && (
            <Button type="primary" onClick={openCreate}>
              新增角色
            </Button>
          )
        }
      />
      {stats && (
        <SummaryBar
          items={[
            {
              key: 'total',
              label: '角色总数',
              value: stats.total,
              sub: `覆盖 ${stats.deptCoverage} 个部门 · 启用占比 ${stats.enabledRate}%`,
            },
            {
              key: 'builtIn',
              label: '内置角色',
              help: '不可删除',
              value: stats.builtInCount,
            },
            {
              key: 'custom',
              label: '自定义角色',
              help: '可自由配置',
              value: stats.customCount,
            },
          ]}
        />
      )}
      <FilterCard
        onSearch={search}
        onReset={() => {
          setDraft({});
          setFilters({});
          // 条件清空时 ProTable 不会自动回到第 1 页
          actionRef.current?.setPageInfo?.({ current: 1 });
        }}
      >
        <FilterField label="角色名称" span={2}>
          <Input
            allowClear
            prefix={<Search size={16} />}
            placeholder="角色名称"
            aria-label="角色名称"
            value={draft.name}
            onChange={(e) => setDraft({ ...draft, name: e.target.value })}
            onPressEnter={search}
          />
        </FilterField>
        <FilterField label="状态">
          <Select<number>
            allowClear
            placeholder="全部"
            aria-label="状态"
            value={filters.status}
            // 状态下拉切换后立即查询；启用数量取原「启用角色」卡片
            onChange={(v) => setFilters({ ...filters, status: v })}
            options={[
              {
                value: 1,
                label: stats?.enabledCount
                  ? `启用 · ${stats.enabledCount}`
                  : '启用',
              },
              {
                value: 0,
                label:
                  stats && stats.total - stats.enabledCount
                    ? `禁用 · ${stats.total - stats.enabledCount}`
                    : '禁用',
              },
            ]}
          />
        </FilterField>
        <FilterField label="角色编码">
          <Input
            allowClear
            placeholder="全部"
            aria-label="角色编码"
            value={draft.code}
            onChange={(e) => setDraft({ ...draft, code: e.target.value })}
            onPressEnter={search}
          />
        </FilterField>
      </FilterCard>
      <MainCard settings={columnsSet.settings} count={total}>
        <ProTable<RoleItem>
          scroll={{ x: tableScrollX(columnsSet.columns) }}
          actionRef={actionRef}
          rowKey="id"
          columns={columnsSet.columns}
          params={filters}
          request={async (params) => {
            const res = await getRoleList(params);
            setTotal(res.total);
            return res;
          }}
          pagination={pager}
          search={false}
          options={false}
          toolBarRender={false}
        />
      </MainCard>

      {/* 新增 / 编辑角色 */}
      <DrawerForm
        name="roleEdit"
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
                workbenchTemplate: editingRole.workbenchTemplate || 'general',
                deptIds: editingDeptIds,
                remark: editingRole.remark,
                status: editingRole.status === 1,
              }
            : { permissionScope: 3, workbenchTemplate: 'general', status: true }
        }
        drawerProps={{ destroyOnHidden: true }}
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
          <Input
            disabled
            aria-label="角色编码"
            value={isEdit ? editingRole?.code : '系统自动生成'}
          />
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
            <Space orientation="vertical" style={{ width: '100%' }}>
              {SCOPE_OPTIONS.map((opt) => (
                <Radio key={opt.value} value={opt.value}>
                  <div style={{ fontWeight: 400 }}>{opt.title}</div>
                  <div style={{ fontSize: 12, color: p.mute }}>{opt.desc}</div>
                </Radio>
              ))}
            </Space>
          </Radio.Group>
        </Form.Item>

        <ProFormDependency name={['permissionScope']}>
          {({ permissionScope }) =>
            permissionScope === 2 && (
              <ProFormTreeSelect
                name="deptIds"
                label={false}
                placeholder="请选择可查看数据的部门"
                extra="选中某个部门时，它下面的各级子部门会一起包含"
                fieldProps={{
                  treeData: deptOptions,
                  multiple: true,
                  treeDefaultExpandAll: true,
                  showSearch: { treeNodeFilterProp: 'title' },
                  style: { marginBottom: 24 },
                }}
              />
            )
          }
        </ProFormDependency>

        <ProFormSelect
          name="workbenchTemplate"
          label="工作台模板"
          extra="拥有这个角色的人登录后，工作台显示这个看板；一个人有多个角色时可以在工作台顶部切换，默认职级最高的。"
          rules={[{ required: true, message: '请选择工作台模板' }]}
          fieldProps={{
            options: templates.map((t) => ({
              value: t.code,
              label: (
                <span>
                  <b>{t.name}</b>
                  <span style={{ marginLeft: 8, fontSize: 12, color: p.mute }}>
                    {t.description}
                  </span>
                </span>
              ),
            })),
          }}
        />

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
        name="rolePerm"
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
              disabled={permLoading}
              onClick={async () => {
                if (!permRole || permLoading) return;
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
        drawerProps={{ destroyOnHidden: true }}
      >
        <div style={{ marginBottom: 12 }}>
          <Space size={16}>
            <button
              type="button"
              className="zhul-link"
              onClick={() => setCheckedKeys(flattenAllKeys(rawMenuTree))}
            >
              全选
            </button>
            <button
              type="button"
              className="zhul-link"
              onClick={() => setCheckedKeys([])}
            >
              取消全选
            </button>
            <button
              type="button"
              className="zhul-link"
              onClick={() =>
                setExpandedKeys(flattenParentKeys(displayMenuTree))
              }
            >
              展开全部
            </button>
            <button
              type="button"
              className="zhul-link"
              onClick={() => setExpandedKeys([])}
            >
              折叠全部
            </button>
          </Space>
        </div>
        <Input
          placeholder="搜索菜单/按钮名称"
          prefix={<Search size="1em" />}
          allowClear
          style={{ marginBottom: 12 }}
          value={menuKeyword}
          onChange={(e) => setMenuKeyword(e.target.value)}
        />
        <Spin spinning={permLoading}>
          <Tree
            checkable
            checkStrictly
            checkedKeys={splitChecked(menuIndex, checkedKeys)}
            expandedKeys={expandedKeys}
            onExpand={(keys) => setExpandedKeys(keys as number[])}
            onCheck={(_, e) =>
              setCheckedKeys((prev) =>
                toggleMenuKey(menuIndex, prev, e.node.key as number, e.checked),
              )
            }
            treeData={antdMenuTree}
          />
        </Spin>
        <div style={{ marginTop: 16, color: p.mute }}>
          已选 {permCheckedCount} 个权限
        </div>
      </DrawerForm>
    </>
  );
};

export default RolePage;
