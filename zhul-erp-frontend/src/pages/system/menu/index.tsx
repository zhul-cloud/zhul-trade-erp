import {
  AppstoreOutlined,
  CopyOutlined,
  EditOutlined,
  EyeOutlined,
  FolderOutlined,
  LinkOutlined,
  MenuOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  SettingOutlined,
  ThunderboltOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import {
  DrawerForm,
  ProForm,
  ProFormDependency,
  ProFormDigit,
  ProFormRadio,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTreeSelect,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { App, Button, Card, Input, Popconfirm, Space, Tag } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import type { MenuItem } from './service';
import {
  createMenu,
  deleteMenu,
  getDeleteCheck,
  getMenuTree,
  updateMenu,
  updateMenuSort,
  updateMenuStatus,
} from './service';

const typeMap: Record<
  number,
  { text: string; color: string; icon: React.ReactNode }
> = {
  1: { text: '目录', color: 'blue', icon: <FolderOutlined /> },
  2: { text: '菜单', color: 'green', icon: <MenuOutlined /> },
  3: { text: '按钮', color: 'orange', icon: <ThunderboltOutlined /> },
};

const MICRO_APP_OPTIONS = [
  { label: '不使用微应用（主应用）', value: '' },
  { label: 'CRM微应用', value: 'crm' },
  { label: 'OMS微应用', value: 'oms' },
];

const flattenIds = (list: MenuItem[]): number[] => {
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

const flattenByType = (
  list: MenuItem[],
  type: number,
  excludeId?: number,
): { title: string; value: number }[] => {
  const result: { title: string; value: number }[] = [];
  const walk = (nodes: MenuItem[]) =>
    nodes.forEach((n) => {
      if (n.type === type && n.id !== excludeId) {
        result.push({ title: n.name, value: n.id });
      }
      if (n.children) walk(n.children);
    });
  walk(list);
  return result;
};

const countDescendants = (node: MenuItem): number => {
  if (!node.children) return 0;
  return node.children.reduce((sum, c) => sum + 1 + countDescendants(c), 0);
};

const computeStats = (list: MenuItem[]) => {
  let total = 0;
  let dir = 0;
  let menu = 0;
  let button = 0;
  const walk = (nodes: MenuItem[]) =>
    nodes.forEach((n) => {
      total += 1;
      if (n.type === 1) dir += 1;
      else if (n.type === 2) menu += 1;
      else if (n.type === 3) button += 1;
      if (n.children) walk(n.children);
    });
  walk(list);
  return { total, dir, menu, button };
};

const filterTree = (list: MenuItem[], keyword: string): MenuItem[] => {
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

const highlightMatch = (text: string, keyword: string) => {
  const kw = keyword.trim();
  if (!kw) return text;
  const idx = text.toLowerCase().indexOf(kw.toLowerCase());
  if (idx === -1) return text;
  return (
    <>
      {text.slice(0, idx)}
      <span
        style={{
          background: 'var(--ant-color-warning-bg)',
          color: 'var(--ant-color-warning-text)',
        }}
      >
        {text.slice(idx, idx + kw.length)}
      </span>
      {text.slice(idx + kw.length)}
    </>
  );
};

// 分组标题：图标 + 文字 + 下方分隔线（与用户管理表单弹窗风格一致）
const groupTitle = (icon: React.ReactNode, text: string) => (
  <div>
    <Space size={6}>
      {icon}
      {text}
    </Space>
    <div
      style={{
        borderBottom: '1px solid var(--ant-color-border-secondary)',
        marginTop: 8,
      }}
    />
  </div>
);

type DrawerMode = 'create' | 'edit';

const MenuPage: React.FC = () => {
  const { palette: p } = useAppTheme();
  const { message, modal } = App.useApp();
  const access = useAccess();

  const [rawTree, setRawTree] = useState<MenuItem[]>([]);
  const [keyword, setKeyword] = useState('');
  const [expandedKeys, setExpandedKeys] = useState<number[]>([]);

  const [editingSortId, setEditingSortId] = useState<number | null>(null);
  const [sortDraft, setSortDraft] = useState(0);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<DrawerMode>('create');
  const [editingNode, setEditingNode] = useState<MenuItem | null>(null);
  const [lockType, setLockType] = useState(true);
  const [presetType, setPresetType] = useState(1);
  const [presetPid, setPresetPid] = useState(0);
  const [parentName, setParentName] = useState('顶级节点');
  const [childOptions, setChildOptions] = useState<
    { label: string; value: number }[]
  >([]);

  const canAdd = !!(access as Record<string, unknown>)['system:menu:add'];
  const canEdit = !!(access as Record<string, unknown>)['system:menu:edit'];
  const canDelete = !!(access as Record<string, unknown>)['system:menu:delete'];

  const displayTree = useMemo(
    () => filterTree(rawTree, keyword),
    [rawTree, keyword],
  );
  const stats = useMemo(() => computeStats(rawTree), [rawTree]);

  const loadTree = async () => {
    const data = await getMenuTree();
    setRawTree(data);
    setExpandedKeys(flattenIds(data));
  };

  useEffect(() => {
    loadTree();
  }, []);

  useEffect(() => {
    if (keyword.trim()) {
      setExpandedKeys(flattenIds(displayTree));
    }
  }, [displayTree, keyword]);

  const getParentOptions = (childType: number, excludeId?: number) => {
    if (childType === 3) return flattenByType(rawTree, 2, excludeId);
    const dirs = flattenByType(rawTree, 1, excludeId);
    if (childType === 1)
      return [{ title: '顶级（无父节点）', value: 0 }, ...dirs];
    return dirs;
  };

  const openCreateTop = () => {
    setDrawerMode('create');
    setEditingNode(null);
    setLockType(true);
    setPresetType(1);
    setPresetPid(0);
    setParentName('顶级节点');
    setDrawerOpen(true);
  };

  const openCreateChild = (record: MenuItem) => {
    const childType = record.type + 1;
    setDrawerMode('create');
    setEditingNode(null);
    setLockType(false);
    setPresetType(childType);
    setPresetPid(record.id);
    setParentName(record.name);
    setChildOptions(
      record.type === 1
        ? [
            { label: '目录', value: 1 },
            { label: '菜单', value: 2 },
          ]
        : [{ label: '按钮', value: 3 }],
    );
    setDrawerOpen(true);
  };

  const openEdit = (record: MenuItem) => {
    setDrawerMode('edit');
    setEditingNode(record);
    setDrawerOpen(true);
  };

  const isEdit = drawerMode === 'edit';
  const fixedType = isEdit ? (editingNode?.type ?? 1) : presetType;

  const handleSubmit = async (
    values: Record<string, any>,
  ): Promise<boolean> => {
    const payload: Record<string, any> = { ...values };
    ['isExternal', 'isCache', 'isHidden', 'status'].forEach((k) => {
      if (k in payload) payload[k] = payload[k] ? 1 : 0;
    });
    if (!isEdit) {
      payload.type = lockType ? presetType : payload.type;
      payload.pid = presetPid;
    }
    try {
      if (isEdit && editingNode) {
        await updateMenu(editingNode.id, payload);
        message.success('保存成功');
      } else {
        await createMenu(payload);
        message.success('新增成功');
      }
      loadTree();
      return true;
    } catch {
      message.error(isEdit ? '保存失败' : '新增失败');
      return false;
    }
  };

  const saveSort = async (record: MenuItem) => {
    try {
      await updateMenuSort(record.id, sortDraft);
    } catch {
      message.error('排序更新失败');
    } finally {
      setEditingSortId(null);
      loadTree();
    }
  };

  const handleToggleStatus = async (record: MenuItem) => {
    const newStatus = record.status === 1 ? 0 : 1;
    try {
      await updateMenuStatus(record.id, newStatus);
      message.success(newStatus === 1 ? '已启用' : '已禁用');
      loadTree();
    } catch {
      message.error('操作失败');
    }
  };

  const doDelete = async (id: number) => {
    try {
      await deleteMenu(id);
      message.success('删除成功');
      loadTree();
    } catch {
      message.error('删除失败');
    }
  };

  const handleDeleteClick = async (record: MenuItem) => {
    const check = await getDeleteCheck(record.id);
    if (check.blocked) {
      modal.info({
        title: '无法删除',
        width: 480,
        content: (
          <div>
            <div style={{ marginBottom: 12 }}>
              节点「{record.name}」下还有 {check.children.length} 个子节点：
            </div>
            <div
              style={{
                background: p.inset,
                borderRadius: 8,
                padding: '12px 16px',
              }}
            >
              {check.children.map((c) => (
                <div key={c.name} style={{ color: p.mute, padding: '2px 0' }}>
                  · {c.name}（{typeMap[c.type]?.text}
                  {c.type === 2 && c.buttonCount != null
                    ? `，含 ${c.buttonCount} 个按钮`
                    : ''}
                  ）
                </div>
              ))}
            </div>
            <div style={{ marginTop: 12, color: p.mute }}>
              请先删除全部子节点后再执行此操作。
            </div>
          </div>
        ),
        okText: '我知道了',
      });
      return;
    }
    if (check.referencedRoles.length > 0) {
      modal.confirm({
        title: '删除确认',
        width: 480,
        icon: <WarningOutlined style={{ color: p.orange }} />,
        content: (
          <div>
            <div style={{ marginBottom: 12 }}>
              节点「{record.name}」已被以下角色引用：
            </div>
            <div
              style={{
                background: p.orangeSoft,
                borderRadius: 8,
                padding: '12px 16px',
                color: p.orange,
              }}
            >
              {check.referencedRoles.join('、')} 共{' '}
              {check.referencedRoles.length} 个角色
            </div>
            <div style={{ marginTop: 12, color: p.mute }}>
              删除后，上述角色将自动失去此节点权限，且操作不可恢复。确认继续删除？
            </div>
          </div>
        ),
        okText: '确认删除',
        okButtonProps: { danger: true },
        cancelText: '取消',
        onOk: () => doDelete(record.id),
      });
      return;
    }
    modal.confirm({
      title: '确认删除',
      content: `确认删除节点「${record.name}」？`,
      okButtonProps: { danger: true },
      onOk: () => doDelete(record.id),
    });
  };

  const columns: ProColumns<MenuItem>[] = [
    {
      title: '菜单名称',
      dataIndex: 'name',
      width: 240,
      render: (_, record) => (
        <Space size={6}>
          {typeMap[record.type]?.icon}
          {highlightMatch(record.name, keyword)}
        </Space>
      ),
    },
    {
      title: '资源编码',
      dataIndex: 'code',
      width: 130,
      render: (_, record) => (
        <span
          style={{
            fontFamily: 'monospace',
            cursor: 'pointer',
            color: p.mute,
          }}
          onClick={() => {
            navigator.clipboard?.writeText(record.code);
            message.success('已复制');
          }}
        >
          {record.code} <CopyOutlined style={{ fontSize: 12 }} />
        </span>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 80,
      render: (_, r) => (
        <Tag color={typeMap[r.type]?.color}>{typeMap[r.type]?.text}</Tag>
      ),
    },
    {
      title: '排序',
      dataIndex: 'sort',
      width: 90,
      render: (_, record) =>
        editingSortId === record.id ? (
          <ProFormDigit
            noStyle
            fieldProps={{
              size: 'small',
              autoFocus: true,
              value: sortDraft,
              style: { width: 70 },
              onChange: (v) => setSortDraft((v as number) ?? 0),
              onBlur: () => saveSort(record),
              onPressEnter: () => saveSort(record),
            }}
            min={0}
          />
        ) : (
          <Space
            size={4}
            style={{ cursor: canEdit ? 'pointer' : 'default' }}
            onClick={() => {
              if (!canEdit) return;
              setEditingSortId(record.id);
              setSortDraft(record.sort);
            }}
          >
            {record.sort}
            {canEdit && (
              <EditOutlined style={{ fontSize: 12, color: p.mute }} />
            )}
          </Space>
        ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (_, r) => (
        <Tag color={r.status === 1 ? 'success' : 'default'} variant="filled">
          {r.status === 1 ? '启用' : '禁用'}
        </Tag>
      ),
    },
    {
      title: '隐藏',
      dataIndex: 'isHidden',
      width: 70,
      render: (_, r) => (r.type === 3 ? '--' : r.isHidden === 1 ? '是' : '否'),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 160,
      render: (_, r) => formatDateTime(r.createTime),
    },
    {
      title: '创建人',
      dataIndex: 'createBy',
      width: 90,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 160,
      render: (_, r) => formatDateTime(r.updateTime),
    },
    {
      title: '更新人',
      dataIndex: 'updateBy',
      width: 90,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      render: (_, record) => (
        <Space size={12}>
          {record.type !== 3 && canAdd && (
            <a onClick={() => openCreateChild(record)}>+子节点</a>
          )}
          {canEdit && <a onClick={() => openEdit(record)}>编辑</a>}
          {canEdit && (
            <Popconfirm
              title={
                record.status === 1 ? '确认禁用该节点？' : '确认启用该节点？'
              }
              description={
                record.status === 1 && countDescendants(record) > 0
                  ? `下级 ${countDescendants(record)} 个子节点将同步禁用`
                  : undefined
              }
              onConfirm={() => handleToggleStatus(record)}
            >
              <a style={{ color: record.status === 1 ? p.red : p.green }}>
                {record.status === 1 ? '禁用' : '启用'}
              </a>
            </Popconfirm>
          )}
          {canDelete && (
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

  const statCards = [
    {
      icon: <AppstoreOutlined />,
      color: p.link,
      bg: p.accentSoft,
      label: '节点总数',
      value: stats.total,
      hint: '3 级树形结构',
    },
    {
      icon: <FolderOutlined />,
      color: p.link,
      bg: p.accentSoft,
      label: '目录',
      value: stats.dir,
      hint: '第一级节点',
    },
    {
      icon: <MenuOutlined />,
      color: p.green,
      bg: p.greenSoft,
      label: '菜单',
      value: stats.menu,
      hint: '对应前端页面',
    },
    {
      icon: <ThunderboltOutlined />,
      color: p.orange,
      bg: p.orangeSoft,
      label: '按钮',
      value: stats.button,
      hint: '页面操作权限点',
    },
  ];

  const renderSections = (t: number) => (
    <>
      {t === 2 && (
        <ProForm.Group title={groupTitle(<LinkOutlined />, '路由配置')}>
          <ProFormText
            name="path"
            label="路由路径"
            placeholder="/orders/sales"
            colProps={{ span: 24 }}
            fieldProps={{ maxLength: 256 }}
            rules={[{ required: true, message: '请输入路由路径' }]}
          />
          <ProFormText
            name="componentPath"
            label="组件路径"
            placeholder="orders/sales/index"
            colProps={{ span: 24 }}
            fieldProps={{ maxLength: 256 }}
            rules={[{ required: true, message: '请输入组件路径' }]}
          />
          <ProFormSelect
            name="microApp"
            label="微应用标识"
            colProps={{ span: 24 }}
            initialValue=""
            options={MICRO_APP_OPTIONS}
          />
        </ProForm.Group>
      )}
      {(t === 1 || t === 2) && (
        <ProForm.Group title={groupTitle(<EyeOutlined />, '显示配置')}>
          <ProFormText
            name="lightIcon"
            label="图标（浅色）"
            placeholder="如：shopping-bag"
            colProps={{ span: 12 }}
            fieldProps={{ maxLength: 64 }}
          />
          <ProFormText
            name="darkIcon"
            label="图标（深色）"
            placeholder="如：shopping-bag"
            colProps={{ span: 12 }}
            fieldProps={{ maxLength: 64 }}
          />
          {t === 2 && (
            <>
              <ProFormSwitch
                name="isExternal"
                label="是否外链"
                colProps={{ span: 24 }}
                initialValue={false}
              />
              <ProFormSwitch
                name="isCache"
                label="是否缓存"
                colProps={{ span: 24 }}
                initialValue={true}
              />
            </>
          )}
          <ProFormSwitch
            name="isHidden"
            label="是否隐藏"
            colProps={{ span: 24 }}
            initialValue={false}
          />
        </ProForm.Group>
      )}
      {t === 3 && (
        <ProForm.Group
          title={groupTitle(<SafetyCertificateOutlined />, '权限标识')}
        >
          <ProFormText
            name="permission"
            label="权限标识"
            placeholder="如：system:menu:add"
            colProps={{ span: 24 }}
            fieldProps={{ maxLength: 128 }}
            rules={[{ required: true, message: '请输入权限标识' }]}
          />
        </ProForm.Group>
      )}
      <ProForm.Group title={groupTitle(<SettingOutlined />, '基础配置')}>
        <ProFormDigit
          name="sort"
          label="排序号"
          colProps={{ span: 24 }}
          min={0}
          initialValue={0}
        />
        <ProFormSwitch
          name="status"
          label="状态"
          colProps={{ span: 24 }}
          initialValue={true}
        />
      </ProForm.Group>
    </>
  );

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

      <Card style={{ marginBottom: 16 }} styles={{ body: { padding: 16 } }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <Space size={12}>
            <Button onClick={() => setExpandedKeys(flattenIds(displayTree))}>
              展开全部
            </Button>
            <Button onClick={() => setExpandedKeys([])}>折叠全部</Button>
            <Input
              placeholder="搜索菜单名称"
              prefix={<SearchOutlined />}
              allowClear
              style={{ width: 280 }}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
          </Space>
          {canAdd && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={openCreateTop}
            >
              新增顶级目录
            </Button>
          )}
        </div>
      </Card>

      <Card>
        <ProTable<MenuItem>
          rowKey="id"
          columns={columns}
          dataSource={displayTree}
          search={false}
          options={false}
          pagination={false}
          toolBarRender={false}
          expandable={{
            expandedRowKeys: expandedKeys,
            onExpandedRowsChange: (keys) => setExpandedKeys(keys as number[]),
          }}
        />
      </Card>

      <DrawerForm
        title={
          isEdit ? `编辑资源节点 - ${editingNode?.name ?? ''}` : '新增资源节点'
        }
        width={520}
        grid
        open={drawerOpen}
        onOpenChange={setDrawerOpen}
        onFinish={handleSubmit}
        initialValues={
          isEdit && editingNode
            ? {
                pid: editingNode.pid,
                name: editingNode.name,
                path: editingNode.path,
                componentPath: editingNode.componentPath,
                permission: editingNode.permission,
                lightIcon: editingNode.lightIcon,
                darkIcon: editingNode.darkIcon,
                microApp: editingNode.microApp || '',
                isExternal: editingNode.isExternal === 1,
                isCache: editingNode.isCache === 1,
                isHidden: editingNode.isHidden === 1,
                sort: editingNode.sort,
                status: editingNode.status === 1,
              }
            : {}
        }
        drawerProps={{ destroyOnClose: true }}
      >
        <ProForm.Group title={groupTitle(<AppstoreOutlined />, '节点信息')}>
          {isEdit || lockType ? (
            <div style={{ marginBottom: 24, width: '100%' }}>
              <div style={{ fontSize: 13, marginBottom: 8 }}>资源类型</div>
              <Tag color={typeMap[fixedType]?.color}>
                {typeMap[fixedType]?.text}
              </Tag>
            </div>
          ) : (
            <ProFormRadio.Group
              name="type"
              label="资源类型"
              colProps={{ span: 24 }}
              initialValue={presetType}
              options={childOptions}
              rules={[{ required: true, message: '请选择资源类型' }]}
            />
          )}

          {isEdit && editingNode ? (
            <ProFormTreeSelect
              name="pid"
              label="父节点"
              colProps={{ span: 24 }}
              fieldProps={{
                treeData: getParentOptions(editingNode.type, editingNode.id),
                allowClear: false,
              }}
              rules={[
                { required: editingNode.type !== 1, message: '请选择父节点' },
              ]}
            />
          ) : (
            <div style={{ marginBottom: 24, width: '100%' }}>
              <div style={{ fontSize: 13, marginBottom: 8 }}>父节点</div>
              <Input disabled value={parentName} />
            </div>
          )}

          <ProFormText
            name="name"
            label="资源名称"
            placeholder="请输入资源名称（2-64字符）"
            colProps={{ span: 24 }}
            fieldProps={{ maxLength: 64 }}
            rules={[
              {
                required: true,
                min: 2,
                max: 64,
                message: '请输入2-64字符的资源名称',
              },
            ]}
          />

          <div style={{ marginBottom: 24, width: '100%' }}>
            <div style={{ fontSize: 13, marginBottom: 8 }}>资源编码</div>
            <Input
              disabled
              value={isEdit ? editingNode?.code : '系统自动生成'}
            />
          </div>
        </ProForm.Group>

        {!isEdit && !lockType ? (
          <ProFormDependency name={['type']}>
            {(values) => renderSections((values.type as number) ?? presetType)}
          </ProFormDependency>
        ) : (
          renderSections(fixedType)
        )}
      </DrawerForm>
    </>
  );
};

export default MenuPage;
