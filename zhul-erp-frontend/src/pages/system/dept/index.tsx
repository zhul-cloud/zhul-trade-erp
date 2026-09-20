import {
  BankOutlined,
  MinusCircleOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import {
  ModalForm,
  ProFormDigit,
  ProFormRadio,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
  ProFormTreeSelect,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { App, Button, Empty, Input, Space, Tag, Tree } from 'antd';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { getUserList } from '@/pages/system/user/service';
import { useAppTheme } from '@/theme/AppTheme';
import type { DeptItem } from './service';
import {
  createDept,
  deleteDept,
  getDeptDeleteCheck,
  getDeptTree,
  updateDept,
  updateDeptStatus,
} from './service';

type DrawerMode = 'create-top' | 'create-child' | 'edit';

const formatDateTime = (value?: string): string =>
  value ? value.slice(0, 16).replace('T', ' ') : '-';

const flattenTree = (list: DeptItem[]): DeptItem[] => {
  const result: DeptItem[] = [];
  const walk = (nodes: DeptItem[]) =>
    nodes.forEach((n) => {
      result.push(n);
      if (n.children) walk(n.children);
    });
  walk(list);
  return result;
};

const flattenParentKeys = (list: DeptItem[]): number[] => {
  const ids: number[] = [];
  const walk = (nodes: DeptItem[]) =>
    nodes.forEach((n) => {
      if (n.children && n.children.length > 0) {
        ids.push(n.id);
        walk(n.children);
      }
    });
  walk(list);
  return ids;
};

const filterDeptTree = (list: DeptItem[], keyword: string): DeptItem[] => {
  if (!keyword.trim()) return list;
  const kw = keyword.trim().toLowerCase();
  const walk = (nodes: DeptItem[]): DeptItem[] =>
    nodes.reduce<DeptItem[]>((acc, n) => {
      const filteredChildren = n.children ? walk(n.children) : [];
      const selfMatch = n.name.toLowerCase().includes(kw);
      if (selfMatch || filteredChildren.length > 0) {
        acc.push({
          ...n,
          children: filteredChildren.length ? filteredChildren : n.children,
        });
      }
      return acc;
    }, []);
  return walk(list);
};

const collectDescendantIds = (node: DeptItem): number[] => {
  if (!node.children) return [];
  const result: number[] = [];
  node.children.forEach((c) => {
    result.push(c.id, ...collectDescendantIds(c));
  });
  return result;
};

const toAntdTree = (list: DeptItem[]): any[] =>
  list.map((item) => ({
    key: item.id,
    title: (
      <Space size={6}>
        <span
          style={{
            color:
              item.status === 0 ? 'var(--ant-color-text-tertiary)' : undefined,
          }}
        >
          {item.name}
        </span>
        {item.status === 0 && (
          <Tag
            style={{
              marginInlineEnd: 0,
              fontSize: 11,
              lineHeight: '16px',
              padding: '0 5px',
            }}
          >
            禁用
          </Tag>
        )}
      </Space>
    ),
    children: item.children ? toAntdTree(item.children) : undefined,
  }));

const toTreeSelectData = (list: DeptItem[], excludeIds: number[]): any[] =>
  list
    .filter((d) => !excludeIds.includes(d.id))
    .map((d) => ({
      title: d.name,
      value: d.id,
      children: d.children
        ? toTreeSelectData(d.children, excludeIds)
        : undefined,
    }));

const DeptPage: React.FC = () => {
  const { palette: p } = useAppTheme();
  const { message, modal } = App.useApp();
  const access = useAccess();

  const [treeData, setTreeData] = useState<DeptItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [expandedKeys, setExpandedKeys] = useState<number[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<DrawerMode>('create-top');
  const [editingDept, setEditingDept] = useState<DeptItem | null>(null);
  const [presetPid, setPresetPid] = useState(0);
  const [presetParentName, setPresetParentName] = useState('顶级部门');
  const [leaderOptions, setLeaderOptions] = useState<
    { label: string; value: number }[]
  >([]);
  const leaderSearchTimer = useRef<ReturnType<typeof setTimeout> | undefined>(
    undefined,
  );

  const canAdd = !!(access as Record<string, unknown>)['system:dept:add'];
  const canEdit = !!(access as Record<string, unknown>)['system:dept:edit'];
  const canDelete = !!(access as Record<string, unknown>)['system:dept:delete'];

  const flatList = useMemo(() => flattenTree(treeData), [treeData]);
  const selectedDept = useMemo(
    () => flatList.find((d) => d.id === selectedId) ?? null,
    [flatList, selectedId],
  );
  const displayTree = useMemo(
    () => filterDeptTree(treeData, keyword),
    [treeData, keyword],
  );
  const antdTreeData = useMemo(() => toAntdTree(displayTree), [displayTree]);

  const loadTree = async () => {
    setLoading(true);
    try {
      const data = await getDeptTree();
      setTreeData(data);
      setExpandedKeys(flattenParentKeys(data));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTree();
  }, []);

  useEffect(() => {
    if (keyword.trim()) {
      setExpandedKeys(flattenParentKeys(displayTree));
    }
  }, [displayTree, keyword]);

  const searchLeaders = (name: string) => {
    if (leaderSearchTimer.current) clearTimeout(leaderSearchTimer.current);
    if (!name || name.trim().length < 2) return;
    leaderSearchTimer.current = setTimeout(async () => {
      const res = await getUserList({
        name: name.trim(),
        status: 1,
        pageSize: 10,
      });
      setLeaderOptions(
        (res.data.records ?? []).map((u) => ({ label: u.name, value: u.id })),
      );
    }, 300);
  };

  const openCreateTop = () => {
    setDrawerMode('create-top');
    setEditingDept(null);
    setPresetPid(0);
    setPresetParentName('顶级部门');
    setLeaderOptions([]);
    setDrawerOpen(true);
  };

  const openCreateChild = () => {
    if (!selectedDept) return;
    setDrawerMode('create-child');
    setEditingDept(null);
    setPresetPid(selectedDept.id);
    setPresetParentName(selectedDept.name);
    setLeaderOptions([]);
    setDrawerOpen(true);
  };

  const openEdit = () => {
    if (!selectedDept) return;
    setDrawerMode('edit');
    setEditingDept(selectedDept);
    setLeaderOptions(
      selectedDept.leaderId
        ? [
            {
              label: selectedDept.leaderName || '',
              value: selectedDept.leaderId,
            },
          ]
        : [],
    );
    setDrawerOpen(true);
  };

  const isEdit = drawerMode === 'edit';
  const isCreateChild = drawerMode === 'create-child';

  const excludeIds =
    isEdit && editingDept
      ? [editingDept.id, ...collectDescendantIds(editingDept)]
      : [];
  const parentTreeSelectData = toTreeSelectData(treeData, excludeIds);

  const handleSubmit = async (
    values: Record<string, any>,
  ): Promise<boolean> => {
    const payload = {
      name: values.name,
      allName: values.allName,
      pid: isCreateChild ? presetPid : (values.pid ?? 0),
      leaderId: values.leaderId,
      phone: values.phone,
      remark: values.remark,
      sort: values.sort ?? 0,
      status: values.status ?? 1,
    };
    try {
      if (isEdit && editingDept) {
        await updateDept(editingDept.id, payload);
        message.success('保存成功');
      } else {
        await createDept(payload);
        message.success('新增成功');
      }
      await loadTree();
      return true;
    } catch {
      message.error(isEdit ? '保存失败' : '新增失败');
      return false;
    }
  };

  const handleToggleStatus = async () => {
    if (!selectedDept) return;
    const newStatus = selectedDept.status === 1 ? 0 : 1;
    const doToggle = async () => {
      try {
        await updateDeptStatus(selectedDept.id, newStatus);
        message.success(newStatus === 1 ? '已启用' : '已禁用');
        await loadTree();
      } catch {
        message.error('操作失败');
      }
    };
    if (newStatus === 0) {
      const check = await getDeptDeleteCheck(selectedDept.id);
      if (check.userCount > 0) {
        modal.confirm({
          title: '禁用确认',
          content: `该部门下存在 ${check.userCount} 名关联用户，禁用后这些用户的部门信息将不可通过该部门筛选，请确认。`,
          onOk: doToggle,
        });
        return;
      }
    }
    doToggle();
  };

  const doDelete = async () => {
    if (!selectedDept) return;
    try {
      await deleteDept(selectedDept.id);
      message.success('删除成功');
      setSelectedId(null);
      await loadTree();
    } catch {
      message.error('删除失败');
    }
  };

  const handleDeleteClick = async () => {
    if (!selectedDept) return;
    const check = await getDeptDeleteCheck(selectedDept.id);
    if (check.childCount > 0) {
      message.error('该部门下存在子部门，请先删除或迁移子部门后重试');
      return;
    }
    if (check.userCount > 0) {
      message.error(
        `该部门已分配 ${check.userCount} 名用户，请先移除关联用户后重试`,
      );
      return;
    }
    modal.confirm({
      title: '确认删除',
      content: `确认删除部门「${selectedDept.name}」？此操作不可撤销。`,
      okButtonProps: { danger: true },
      okText: '确认删除',
      onOk: doDelete,
    });
  };

  return (
    <>
      <div
        style={{
          display: 'flex',
          gap: 0,
          background: p.card,
          borderRadius: 12,
          overflow: 'hidden',
          border: `1px solid ${p.hairline}`,
          minHeight: 640,
        }}
      >
        {/* 左侧部门树 */}
        <div
          style={{
            width: 280,
            flexShrink: 0,
            padding: 16,
            borderRight: `1px solid ${p.hairline}`,
          }}
        >
          <Input
            placeholder="搜索部门名称"
            prefix={<SearchOutlined />}
            allowClear
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ marginBottom: 12 }}
          />
          {!loading && treeData.length === 0 ? (
            <Empty description="暂无部门数据" style={{ marginTop: 40 }} />
          ) : !loading && displayTree.length === 0 ? (
            <Empty description="未找到相关部门" style={{ marginTop: 40 }} />
          ) : (
            <Tree
              treeData={antdTreeData}
              expandedKeys={expandedKeys}
              onExpand={(keys) => setExpandedKeys(keys as number[])}
              selectedKeys={selectedId ? [selectedId] : []}
              onSelect={(keys) => {
                if (keys.length > 0) setSelectedId(keys[0] as number);
              }}
            />
          )}
        </div>

        {/* 右侧详情 */}
        <div style={{ flex: 1, padding: 20, minWidth: 0 }}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: 16,
            }}
          >
            <span style={{ fontSize: 16, fontWeight: 700 }}>
              {selectedDept ? selectedDept.name : '部门详情'}
            </span>
            <Space size={10}>
              {canAdd && (
                <Button icon={<PlusOutlined />} onClick={openCreateTop}>
                  新增顶级部门
                </Button>
              )}
              {selectedDept && canAdd && (
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={openCreateChild}
                >
                  新增子部门
                </Button>
              )}
              {selectedDept && canEdit && (
                <Button onClick={openEdit}>编辑</Button>
              )}
              {selectedDept && canEdit && (
                <Button
                  icon={<MinusCircleOutlined />}
                  onClick={handleToggleStatus}
                >
                  {selectedDept.status === 1 ? '禁用' : '启用'}
                </Button>
              )}
              {selectedDept && canDelete && (
                <Button danger onClick={handleDeleteClick}>
                  删除
                </Button>
              )}
            </Space>
          </div>

          {selectedDept ? (
            <div
              style={{
                background: p.inset,
                border: `1px solid ${p.hairline}`,
                borderRadius: 12,
                padding: 24,
              }}
            >
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr',
                  rowGap: 20,
                  columnGap: 40,
                }}
              >
                <Field label="部门编码" value={selectedDept.code} />
                <Field label="部门简称" value={selectedDept.name} />
                <Field label="部门全称" value={selectedDept.allName || '-'} />
                <Field
                  label="上级部门"
                  value={
                    flatList.find((d) => d.id === selectedDept.pid)?.name ??
                    '顶级部门'
                  }
                />
                <Field label="负责人" value={selectedDept.leaderName || '-'} />
                <Field label="联系电话" value={selectedDept.phone || '-'} />
                <Field
                  label="状态"
                  value={selectedDept.status === 1 ? '启用' : '禁用'}
                  dot={selectedDept.status === 1 ? p.green : p.mute}
                />
                <Field label="排序号" value={String(selectedDept.sort)} />
                <Field
                  label="创建时间"
                  value={formatDateTime(selectedDept.createTime)}
                />
                <Field
                  label="更新时间"
                  value={formatDateTime(selectedDept.updateTime)}
                />
              </div>
              <div style={{ marginTop: 20 }}>
                <Field label="备注" value={selectedDept.remark || '-'} />
              </div>
            </div>
          ) : (
            <Empty
              image={<BankOutlined style={{ fontSize: 48, color: p.faint }} />}
              description="请选择左侧部门查看详情"
              style={{ marginTop: 80 }}
            />
          )}
        </div>
      </div>

      <ModalForm
        title={isEdit ? '编辑部门' : '新增部门'}
        width={560}
        open={drawerOpen}
        onOpenChange={setDrawerOpen}
        onFinish={handleSubmit}
        modalProps={{ destroyOnClose: true, maskClosable: false }}
        layout="vertical"
        initialValues={
          isEdit && editingDept
            ? {
                pid: editingDept.pid || undefined,
                name: editingDept.name,
                allName: editingDept.allName,
                leaderId: editingDept.leaderId || undefined,
                phone: editingDept.phone,
                sort: editingDept.sort,
                status: editingDept.status,
                remark: editingDept.remark,
              }
            : { sort: 0, status: 1 }
        }
      >
        {isEdit && (
          <div style={{ marginBottom: 24 }}>
            <div style={{ fontSize: 13, marginBottom: 8 }}>部门编码</div>
            <Input disabled value={editingDept?.code} />
          </div>
        )}

        {isCreateChild ? (
          <div style={{ marginBottom: 24 }}>
            <div style={{ fontSize: 13, marginBottom: 8 }}>上级部门</div>
            <Input disabled value={presetParentName} />
          </div>
        ) : (
          <ProFormTreeSelect
            name="pid"
            label="上级部门"
            placeholder="不选则为顶级部门"
            fieldProps={{ treeData: parentTreeSelectData, allowClear: true }}
          />
        )}

        <ProFormText
          name="name"
          label="部门简称"
          placeholder="请输入部门简称，1~64字符"
          fieldProps={{ maxLength: 64 }}
          rules={[
            {
              required: true,
              min: 1,
              max: 64,
              message: '请输入1~64字符的部门简称',
            },
          ]}
        />

        <ProFormText
          name="allName"
          label="部门全称"
          placeholder="请输入部门全称，最多128字符"
          fieldProps={{ maxLength: 128 }}
        />

        <ProFormSelect
          name="leaderId"
          label="负责人"
          placeholder="输入姓名搜索"
          fieldProps={{
            showSearch: true,
            filterOption: false,
            options: leaderOptions,
            onSearch: searchLeaders,
            notFoundContent: null,
          }}
        />

        <ProFormText
          name="phone"
          label="联系电话"
          placeholder="请输入联系电话"
          fieldProps={{ maxLength: 20 }}
        />

        <ProFormDigit
          name="sort"
          label="排序号"
          min={0}
          max={9999}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入排序号' }]}
        />

        <ProFormRadio.Group
          name="status"
          label="状态"
          rules={[{ required: true }]}
          options={[
            { label: '启用', value: 1 },
            { label: '禁用', value: 0 },
          ]}
        />

        <ProFormTextArea
          name="remark"
          label="备注"
          placeholder="填写部门用途说明（选填，最长256字符）"
          fieldProps={{ rows: 3, maxLength: 256, showCount: true }}
        />
      </ModalForm>
    </>
  );
};

const Field: React.FC<{ label: string; value: string; dot?: string }> = ({
  label,
  value,
  dot,
}) => {
  const { palette: p } = useAppTheme();
  return (
    <div>
      <div style={{ fontSize: 12, color: p.mute, marginBottom: 5 }}>
        {label}
      </div>
      <div
        style={{
          fontSize: 14,
          fontWeight: 500,
          color: p.ink,
          display: 'flex',
          alignItems: 'center',
          gap: 6,
        }}
      >
        {dot && (
          <span
            style={{
              width: 7,
              height: 7,
              borderRadius: '50%',
              background: dot,
              display: 'inline-block',
            }}
          />
        )}
        {value}
      </div>
    </div>
  );
};

export default DeptPage;
