import {
  ApartmentOutlined,
  CheckCircleOutlined,
  KeyOutlined,
  StopOutlined,
  TeamOutlined,
  UserAddOutlined,
  UserOutlined,
} from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  ProForm,
  ProFormRadio,
  ProFormSelect,
  ProFormText,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import {
  App,
  Avatar,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Tag,
} from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import type { UserItem, UserStats } from './service';
import {
  createUser,
  deleteUser,
  getDeptOptions,
  getPositionOptions,
  getRoleOptions,
  getUserList,
  getUserStats,
  resetPassword,
  updateUser,
  updateUserStatus,
} from './service';

const AVATAR_COLORS = [
  '#1677FF',
  '#16A34A',
  '#F59E0B',
  '#7C3AED',
  '#DC2626',
  '#0EA5E9',
];

const UserPage: React.FC = () => {
  const { palette: p } = useAppTheme();
  const actionRef = useRef<ActionType>();
  const { message } = App.useApp();
  const access = useAccess();
  const [editingUser, setEditingUser] = useState<UserItem | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [resetPwdOpen, setResetPwdOpen] = useState(false);
  const [resetPwdUserId, setResetPwdUserId] = useState<number | null>(null);
  const [pwdForm] = Form.useForm();
  const [stats, setStats] = useState<UserStats | null>(null);

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
  // maxLength 对齐 user_basic 表字段长度：name(16) username(20) phone(16) email(100) nickname(64)
  const renderFormItems = (isCreate: boolean) => (
    <>
      <ProForm.Group title={groupTitle(<UserOutlined />, '基础信息')}>
        {isCreate && (
          <ProFormText
            name="username"
            label="用户名"
            placeholder="请输入用户名"
            colProps={{ span: 12 }}
            fieldProps={{ maxLength: 20 }}
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
      <ProForm.Group title={groupTitle(<ApartmentOutlined />, '组织信息')}>
        <ProFormSelect
          name="deptId"
          label="所属部门"
          placeholder="请选择部门"
          colProps={{ span: 12 }}
          request={getDeptOptions}
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
      <ProForm.Group title={groupTitle(<KeyOutlined />, '账号设置')}>
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

  const columns: ProColumns<UserItem>[] = [
    {
      title: '用户',
      dataIndex: 'name',
      width: 180,
      render: (_, record, index) => (
        <Space>
          <Avatar
            style={{
              backgroundColor: AVATAR_COLORS[index % AVATAR_COLORS.length],
            }}
          >
            {(record.nickname || record.name || '?').charAt(0)}
          </Avatar>
          <div>
            <div style={{ fontWeight: 600 }}>{record.name}</div>
            <div style={{ fontSize: 12, color: p.mute }}>{record.username}</div>
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
      valueType: 'select',
      request: getDeptOptions,
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
          <Tag color="processing" variant="filled">
            {record.roleName}
          </Tag>
        ) : (
          '-'
        ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      valueEnum: {
        0: { text: '禁用', status: 'Error' },
        1: { text: '启用', status: 'Success' },
      },
      render: (_, record) => (
        <Tag
          color={record.status === 1 ? 'success' : 'default'}
          variant="filled"
        >
          {record.status === 1 ? '启用' : '禁用'}
        </Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 160,
      search: false,
      valueType: 'dateTime',
    },
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      render: (_, record) => (
        <Space>
          {!!(access as Record<string, unknown>)['system:user:edit'] && (
            <a
              onClick={() => {
                setEditingUser(record);
                setEditOpen(true);
              }}
            >
              编辑
            </a>
          )}
          {!!(access as Record<string, unknown>)['system:user:resetPwd'] && (
            <a
              onClick={() => {
                setResetPwdUserId(record.id);
                setResetPwdOpen(true);
              }}
            >
              重置密码
            </a>
          )}
          {!!(access as Record<string, unknown>)['system:user:status'] && (
            <Popconfirm
              title={
                record.status === 1 ? '确认禁用该用户？' : '确认启用该用户？'
              }
              onConfirm={() => handleStatusChange(record)}
            >
              <a style={{ color: record.status === 1 ? p.red : p.green }}>
                {record.status === 1 ? '禁用' : '启用'}
              </a>
            </Popconfirm>
          )}
          {!!(access as Record<string, unknown>)['system:user:delete'] && (
            <Popconfirm
              title="确认删除该用户？"
              onConfirm={() => handleDelete(record.id)}
            >
              <a style={{ color: p.red }}>删除</a>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  const statCards = [
    {
      icon: <TeamOutlined />,
      color: p.link,
      bg: p.accentSoft,
      label: '总用户数',
      value: stats?.total,
    },
    {
      icon: <CheckCircleOutlined />,
      color: p.green,
      bg: p.greenSoft,
      label: '启用用户',
      value: stats?.enabled,
    },
    {
      icon: <StopOutlined />,
      color: p.mute,
      bg: p.inset,
      label: '禁用用户',
      value: stats?.disabled,
    },
    {
      icon: <UserAddOutlined />,
      color: p.violet,
      bg: p.violetSoft,
      label: '本月新增',
      value: stats?.newThisMonth,
    },
  ];

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
              {s.value ?? '-'}
            </div>
          </Card>
        ))}
      </div>

      <ProTable<UserItem>
        headerTitle="用户列表"
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const res = await getUserList(params);
          return {
            data: res.data.records,
            total: res.data.total,
            success: true,
          };
        }}
        pagination={{ pageSize: 10 }}
        search={{ labelWidth: 'auto' }}
        toolBarRender={() => [
          !!(access as Record<string, unknown>)['system:user:add'] && (
            <Button
              key="add"
              type="primary"
              onClick={() => setCreateOpen(true)}
            >
              新增用户
            </Button>
          ),
        ]}
      />

      {/* 新增用户 */}
      <ModalForm
        title="新增用户"
        width={640}
        grid
        open={createOpen}
        onOpenChange={setCreateOpen}
        onFinish={handleCreate}
        modalProps={{ destroyOnClose: true }}
      >
        {renderFormItems(true)}
      </ModalForm>

      {/* 编辑用户 */}
      <ModalForm
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
        modalProps={{ destroyOnClose: true }}
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
        destroyOnClose
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
