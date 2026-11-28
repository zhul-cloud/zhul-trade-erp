import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  ProForm,
  ProFormRadio,
  ProFormSelect,
  ProFormText,
  ProFormTreeSelect,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import {
  App,
  Avatar,
  Button,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Tag,
  TreeSelect,
} from 'antd';
import {
  Ban,
  CircleCheck,
  KeyRound,
  Network,
  Pencil,
  Plus,
  Search,
  Trash2,
  User,
} from 'lucide-react';
import React, { useEffect, useRef, useState } from 'react';
import { useColumnSettings } from '@/components/ColumnSettings';
import { useConfirmDanger } from '@/components/ConfirmDanger';
import EllipsisText from '@/components/EllipsisText';
import { Pill } from '@/components/Pill';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
} from '@/components/ListLayout';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { proAuditColumns } from '@/components/UserName';
import { useAppTheme } from '@/theme/AppTheme';
import { seriesFor, textOn } from '@/theme/palette';
import { usePagination } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import type { UserItem, UserStats } from './service';
import {
  createUser,
  deleteUser,
  getDeptTreeOptions,
  getPositionOptions,
  getRoleOptions,
  getUserList,
  getUserStats,
  resetPassword,
  updateUser,
  updateUserStatus,
} from './service';

/** 头像底色轮流取图表系列色（不表达状态） */

/** 帮助文字：help-texts.md「用户管理」 */
export const HELP = {
  total: '没有删除的用户',
  newThisMonth: '本月 1 日起新建的用户',
} as const;

const DEPT_TREE_PROPS = {
  treeDefaultExpandAll: true,
  allowClear: true,
  showSearch: { treeNodeFilterProp: 'title' },
};

const UserPage: React.FC = () => {
  const pager = usePagination(20);
  const { palette: p, mode } = useAppTheme();
  // 头像底色用图表系列色，深浅模式各一组；字色按底色取白或深色（白字在绿、青上不足 3:1）
  const avatarColors = seriesFor(mode);
  const actionRef = useRef<ActionType>(undefined);
  const { message } = App.useApp();
  const access = useAccess();
  const [editingUser, setEditingUser] = useState<UserItem | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [resetPwdOpen, setResetPwdOpen] = useState(false);
  const [resetPwdUserId, setResetPwdUserId] = useState<number | null>(null);
  const [pwdForm] = Form.useForm();
  const [stats, setStats] = useState<UserStats | null>(null);
  /** 搜索条件：状态切换后立即查询，姓名、部门点「查询」才生效 */
  const [filters, setFilters] = useState<{
    name?: string;
    deptId?: number;
    status?: number;
  }>({});
  const [keyword, setKeyword] = useState('');
  const [draftDept, setDraftDept] = useState<number>();
  const [deptTree, setDeptTree] = useState<
    Awaited<ReturnType<typeof getDeptTreeOptions>>
  >([]);
  // 首次查询返回前没有总数，主卡不显示「共 0 条」
  const [total, setTotal] = useState<number>();
  useEffect(() => {
    getDeptTreeOptions()
      .then(setDeptTree)
      .catch(() => setDeptTree([]));
  }, []);

  const loadStats = async () => {
    const res = await getUserStats();
    setStats(res.data);
  };

  useEffect(() => {
    loadStats();
  }, []);

  const handleCreate = async (
    values: Record<string, unknown>,
  ): Promise<boolean> => {
    try {
      await createUser(
        values as {
          name: string;
          username: string;
          phone: string;
          email?: string;
          deptId?: number;
          positionId?: number;
          roleCode?: string;
          password: string;
          status?: number;
        },
      );
      message.success('新增成功');
      actionRef.current?.reload();
      loadStats();
      return true;
    } catch {
      message.error('新增失败');
      return false;
    }
  };

  const handleEdit = async (
    values: Record<string, unknown>,
  ): Promise<boolean> => {
    if (!editingUser) return false;
    try {
      await updateUser(editingUser.id, values as Partial<UserItem>);
      message.success('保存成功');
      actionRef.current?.reload();
      return true;
    } catch {
      message.error('保存失败');
      return false;
    }
  };

  const handleDelete = async (id: number): Promise<void> => {
    try {
      await deleteUser(id);
      message.success('删除成功');
      actionRef.current?.reload();
      loadStats();
    } catch {
      message.error('删除失败');
    }
  };

  const handleResetPwd = async (): Promise<void> => {
    if (resetPwdUserId === null) return;
    const { newPassword } = await pwdForm.validateFields();
    try {
      await resetPassword(resetPwdUserId, newPassword as string);
      message.success('密码已重置');
      setResetPwdOpen(false);
      pwdForm.resetFields();
    } catch {
      message.error('重置失败');
    }
  };

  const handleStatusChange = async (record: UserItem): Promise<void> => {
    const newStatus = record.status === 1 ? 0 : 1;
    try {
      await updateUserStatus(record.id, newStatus);
      message.success(newStatus === 1 ? '已启用' : '已禁用');
      actionRef.current?.reload();
      loadStats();
    } catch {
      message.error('操作失败');
    }
  };

  // 分组标题：图标 + 文字 + 下方分隔线（与设计稿一致）
  const groupTitle = (icon: React.ReactNode, text: string) => (
    <div>
      <Space size={6}>
        {icon}
        {text}
      </Space>
      <div style={{ borderBottom: `1px solid ${p.hairline}`, marginTop: 8 }} />
    </div>
  );

  // 按业务属性分组：基础信息 / 组织信息 / 账号设置
  // maxLength 对齐表字段长度：name(16) phone(16) email(100) nickname(64)；
  // username 记入各表的创建人 / 更新人（varchar(32)），最长 32
  const renderFormItems = (isCreate: boolean) => (
    <>
      <ProForm.Group title={groupTitle(<User size="1em" />, '基础信息')}>
        {isCreate && (
          <ProFormText
            name="username"
            label="用户名"
            placeholder="请输入用户名"
            colProps={{ span: 12 }}
            fieldProps={{ maxLength: 32 }}
            rules={[{ required: true, message: '请输入用户名' }]}
          />
        )}
        <ProFormText
          name="name"
          label="姓名"
          placeholder="请输入姓名"
          colProps={{ span: 12 }}
          fieldProps={{ maxLength: 16 }}
          rules={[{ required: true, message: '请输入姓名' }]}
        />
        <ProFormText
          name="phone"
          label="手机号"
          placeholder="请输入手机号"
          colProps={{ span: 8 }}
          fieldProps={{ maxLength: 11 }}
          rules={[{ required: true, message: '请输入手机号' }]}
        />
        <ProFormText
          name="email"
          label="邮箱"
          placeholder="请输入邮箱"
          colProps={{ span: 16 }}
          fieldProps={{ maxLength: 100 }}
        />
        <ProFormText
          name="nickname"
          label="昵称"
          placeholder="请输入昵称"
          colProps={{ span: 24 }}
          fieldProps={{ maxLength: 64 }}
        />
      </ProForm.Group>
      <ProForm.Group title={groupTitle(<Network size="1em" />, '组织信息')}>
        <ProFormTreeSelect
          name="deptId"
          label="所属部门"
          placeholder="请选择部门"
          colProps={{ span: 12 }}
          request={getDeptTreeOptions}
          fieldProps={DEPT_TREE_PROPS}
        />
        <ProFormSelect
          name="positionId"
          label="所属岗位"
          placeholder="请选择岗位"
          colProps={{ span: 12 }}
          request={getPositionOptions}
        />
        <ProFormSelect
          name="roleCode"
          label="角色"
          placeholder="请选择角色"
          colProps={{ span: 24 }}
          request={getRoleOptions}
          rules={[{ required: true, message: '请选择角色' }]}
        />
      </ProForm.Group>
      <ProForm.Group title={groupTitle(<KeyRound size="1em" />, '账号设置')}>
        {isCreate && (
          <ProFormText.Password
            name="password"
            label="初始密码"
            placeholder="请输入至少6位密码"
            colProps={{ span: 24 }}
            rules={[{ required: true, min: 6, message: '请输入至少6位密码' }]}
          />
        )}
        <ProFormRadio.Group
          name="status"
          label="启用状态"
          initialValue={1}
          colProps={{ span: 24 }}
          options={[
            { label: '启用', value: 1 },
            { label: '禁用', value: 0 },
          ]}
        />
      </ProForm.Group>
    </>
  );

  const confirmDanger = useConfirmDanger();
  const columns: ProColumns<UserItem>[] = [
    {
      fixed: 'left',
      title: '用户',
      dataIndex: 'name',
      width: 220,
      search: false,
      render: (_, record, index) => (
        <Space style={{ maxWidth: '100%' }} styles={{ item: { minWidth: 0 } }}>
          <Avatar
            style={{
              backgroundColor: avatarColors[index % avatarColors.length],
              color: textOn(avatarColors[index % avatarColors.length]),
            }}
          >
            {(record.nickname || record.name || '?').charAt(0)}
          </Avatar>
          <div style={{ minWidth: 0 }}>
            <EllipsisText text={record.name} style={{ fontWeight: 600 }} />
            <EllipsisText
              text={record.username}
              style={{ fontSize: 12, color: p.mute }}
            />
          </div>
        </Space>
      ),
    },
    { title: '手机号', dataIndex: 'phone', width: 130, search: false },
    {
      title: '邮箱',
      dataIndex: 'email',
      width: 180,
      search: false,
      ellipsis: true,
    },
    {
      title: '部门',
      dataIndex: 'deptId',
      width: 140,
      search: false,
      render: (_, record) => record.deptName,
    },
    { title: '岗位', dataIndex: 'positionName', width: 120, search: false },
    {
      title: '角色',
      dataIndex: 'roleName',
      width: 120,
      search: false,
      render: (_, record) =>
        record.roleName ? (
          // 角色取值不固定（租户自建），按 D75 用灰标签
          <Pill tone="gray">
            <EllipsisText text={record.roleName} style={{ minWidth: 0 }} />
          </Pill>
        ) : (
          '-'
        ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      search: false,
      render: (_, record) => (
        <Tag
          color={record.status === 1 ? 'success' : 'default'}
          variant="filled"
        >
          {record.status === 1 ? '启用' : '禁用'}
        </Tag>
      ),
    },
    ...proAuditColumns<UserItem>(),
    {
      title: '操作',
      fixed: 'right',
      valueType: 'option',
      // 编辑放行内；重置密码、启用/禁用、删除收进「更多」，危险项在末尾（ui-redesign S6）
      width: rowActionsWidth(2),
      render: (_, record) => {
        const can = (k: string) => !!(access as Record<string, unknown>)[k];
        const enabled = record.status === 1;
        return (
          <RowActions
            inline={[
              can('system:user:edit') && {
                key: 'edit',
                label: '编辑',
                icon: <Pencil size={18} />,
                onClick: () => {
                  setEditingUser(record);
                  setEditOpen(true);
                },
              },
            ]}
            more={[
              can('system:user:resetPwd') && {
                key: 'resetPwd',
                label: '重置密码',
                icon: <KeyRound size={16} />,
              },
              can('system:user:status') && {
                key: 'status',
                label: enabled ? '禁用' : '启用',
                icon: enabled ? <Ban size={16} /> : <CircleCheck size={16} />,
                danger: enabled,
              },
              can('system:user:delete') && {
                key: 'delete',
                label: '删除',
                icon: <Trash2 size={16} />,
                danger: true,
              },
            ]}
            onMore={(key) => {
              if (key === 'resetPwd') {
                setResetPwdUserId(record.id);
                setResetPwdOpen(true);
              }
              if (key === 'status') {
                if (enabled) {
                  confirmDanger({
                    title: `禁用用户「${record.name || record.username}」？`,
                    okText: '禁用',
                    onOk: () => handleStatusChange(record),
                  });
                } else {
                  handleStatusChange(record);
                }
              }
              if (key === 'delete') {
                confirmDanger({
                  title: `删除用户「${record.name || record.username}」？`,
                  onOk: () => handleDelete(record.id),
                });
              }
            }}
          />
        );
      },
    },
  ];
  const columnsSet = useColumnSettings('system-user', columns, {
    hidden: ['岗位'],
  });

  /** 状态数量并入状态下拉（stat-cards.md：用户管理） */
  const withCount = (label: string, n?: number) =>
    n ? `${label} · ${n}` : label;
  const search = () => {
    setFilters({
      ...filters,
      name: keyword.trim() || undefined,
      deptId: draftDept,
    });
  };

  return (
    <>
      <PageHeader
        extra={
          !!(access as Record<string, unknown>)['system:user:add'] && (
            <Button
              type="primary"
              icon={<Plus size={18} />}
              onClick={() => setCreateOpen(true)}
            >
              新增用户
            </Button>
          )
        }
        summary={[
          {
            key: 'total',
            label: '总用户数',
            help: HELP.total,
            value: stats?.total ?? '—',
          },
          {
            key: 'new',
            label: '本月新增',
            help: HELP.newThisMonth,
            value: stats?.newThisMonth ?? '—',
          },
        ]}
      />
      <FilterCard
        onSearch={search}
        onReset={() => {
          setKeyword('');
          setDraftDept(undefined);
          setFilters({});
          // 条件清空时 ProTable 不会自动回到第 1 页
          actionRef.current?.setPageInfo?.({ current: 1 });
        }}
      >
        <FilterField label="用户" span={2}>
          <Input
            allowClear
            prefix={<Search size={16} />}
            placeholder="姓名"
            aria-label="用户"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={search}
          />
        </FilterField>
        <FilterField label="状态">
          <Select<number>
            allowClear
            placeholder="全部"
            aria-label="状态"
            value={filters.status}
            // 状态下拉切换后立即查询
            onChange={(v) => setFilters({ ...filters, status: v })}
            options={[
              { value: 1, label: withCount('启用', stats?.enabled) },
              { value: 0, label: withCount('禁用', stats?.disabled) },
            ]}
          />
        </FilterField>
        <FilterField label="部门">
          <TreeSelect
            {...DEPT_TREE_PROPS}
            placeholder="全部"
            aria-label="部门"
            value={draftDept}
            onChange={setDraftDept}
            treeData={deptTree}
          />
        </FilterField>
      </FilterCard>
      <MainCard settings={columnsSet.settings} count={total}>
        <ProTable<UserItem>
          scroll={{ x: tableScrollX(columnsSet.columns) }}
          actionRef={actionRef}
          rowKey="id"
          columns={columnsSet.columns}
          params={filters}
          request={async (params) => {
            const res = await getUserList(params);
            setTotal(res.data.total);
            return {
              data: res.data.records,
              total: res.data.total,
              success: true,
            };
          }}
          pagination={pager}
          search={false}
          options={false}
          toolBarRender={false}
        />
      </MainCard>

      {/* 新增用户 */}
      <ModalForm
        name="userCreate"
        title="新增用户"
        width={640}
        grid
        open={createOpen}
        onOpenChange={setCreateOpen}
        onFinish={handleCreate}
        modalProps={{ destroyOnHidden: true }}
      >
        {renderFormItems(true)}
      </ModalForm>

      {/* 编辑用户 */}
      <ModalForm
        name="userEdit"
        title="编辑用户"
        width={640}
        grid
        open={editOpen}
        onOpenChange={(v) => {
          setEditOpen(v);
          if (!v) setEditingUser(null);
        }}
        onFinish={handleEdit}
        initialValues={
          editingUser
            ? {
                ...editingUser,
                deptId: editingUser.deptId || undefined,
                positionId: editingUser.positionId || undefined,
              }
            : {}
        }
        modalProps={{ destroyOnHidden: true }}
      >
        {renderFormItems(false)}
      </ModalForm>

      {/* 重置密码 */}
      <Modal
        title="重置密码"
        open={resetPwdOpen}
        onOk={handleResetPwd}
        onCancel={() => {
          setResetPwdOpen(false);
          pwdForm.resetFields();
        }}
        destroyOnHidden
      >
        <Form form={pwdForm} layout="vertical">
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[{ required: true, min: 6, message: '请输入至少6位密码' }]}
          >
            <Input.Password placeholder="请输入新密码" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

export default UserPage;
