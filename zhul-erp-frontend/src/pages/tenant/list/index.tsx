import {
  CalendarOutlined,
  CheckCircleFilled,
  CopyOutlined,
  InfoCircleOutlined,
  PlusOutlined,
  UserOutlined,
  WarningFilled,
} from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  DrawerForm,
  ProFormDatePicker,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import {
  App,
  Badge,
  Button,
  DatePicker,
  Divider,
  Modal,
  Radio,
  Space,
  Tag,
  Tooltip,
} from 'antd';
import dayjs from 'dayjs';
import React, { useEffect, useRef, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import type {
  PackageOption,
  RenewPayload,
  ResetPasswordResult,
  SaveTenantPayload,
  TenantItem,
} from './service';
import {
  createTenant,
  getPackageOptions,
  getTenantList,
  renewTenant,
  resetTenantPassword,
  updateTenant,
  updateTenantStatus,
} from './service';

const RENEW_MONTHS_OPTIONS = [
  { label: '1 个月', value: 1 },
  { label: '3 个月', value: 3 },
  { label: '6 个月', value: 6 },
  { label: '12 个月', value: 12 },
];

const TenantListPage: React.FC = () => {
  const { message, modal } = App.useApp();
  const access = useAccess();
  const { palette } = useAppTheme();
  const actionRef = useRef<ActionType>(undefined);

  const [packageOptions, setPackageOptions] = useState<PackageOption[]>([]);
  useEffect(() => {
    getPackageOptions().then(setPackageOptions);
  }, []);

  // ---------- 新增 / 编辑 ----------
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<TenantItem | null>(null);

  const openCreate = () => {
    setEditing(null);
    setDrawerOpen(true);
  };
  const openEdit = (row: TenantItem) => {
    setEditing(row);
    setDrawerOpen(true);
  };

  const submitSave = async (values: Record<string, unknown>) => {
    const payload: SaveTenantPayload = {
      name: values.name as string,
      packageId: values.packageId as number,
      contactName: values.contactName as string,
      contactPhone: values.contactPhone as string,
      contactEmail: values.contactEmail as string,
      expireDate: dayjs(values.expireDate as unknown as string).format(
        'YYYY-MM-DD',
      ),
      remark: (values.remark as string) || undefined,
    };
    if (editing) {
      await updateTenant(editing.id, payload);
      message.success('租户信息已更新');
    } else {
      const result = await createTenant(payload);
      message.success({
        content: (
          <span>
            租户创建成功，管理员账号：<b>{result.adminEmail}</b>，临时密码：
            <b className="num">{result.tempPassword}</b>，请妥善告知
          </span>
        ),
        duration: 0,
        onClick: () => message.destroy(),
      });
    }
    actionRef.current?.reload();
    return true;
  };

  // ---------- 续期 ----------
  const [renewOpen, setRenewOpen] = useState(false);
  const [renewTarget, setRenewTarget] = useState<TenantItem | null>(null);
  const [renewMode, setRenewMode] = useState<'DURATION' | 'DATE'>('DURATION');
  const [renewMonths, setRenewMonths] = useState(12);
  const [renewDate, setRenewDate] = useState<dayjs.Dayjs | null>(null);
  const [renewSubmitting, setRenewSubmitting] = useState(false);

  const openRenew = (row: TenantItem) => {
    setRenewTarget(row);
    setRenewMode('DURATION');
    setRenewMonths(12);
    setRenewDate(null);
    setRenewOpen(true);
  };

  const renewPreviewDate =
    renewTarget && renewMode === 'DURATION'
      ? dayjs(renewTarget.expireTime)
          .add(renewMonths, 'month')
          .format('YYYY-MM-DD')
      : null;

  const submitRenew = async () => {
    if (!renewTarget) return;
    if (renewMode === 'DATE' && !renewDate) {
      message.error('请选择到期日期');
      return;
    }
    setRenewSubmitting(true);
    try {
      const payload: RenewPayload =
        renewMode === 'DURATION' || !renewDate
          ? { mode: 'DURATION', months: renewMonths }
          : { mode: 'DATE', expireDate: renewDate.format('YYYY-MM-DD') };
      const updated = await renewTenant(renewTarget.id, payload);
      message.success(
        `续期成功，新到期时间：${formatDateTime(updated.expireTime)}`,
      );
      setRenewOpen(false);
      actionRef.current?.reload();
    } finally {
      setRenewSubmitting(false);
    }
  };

  // ---------- 重置密码 ----------
  const [resetOpen, setResetOpen] = useState(false);
  const [resetTarget, setResetTarget] = useState<TenantItem | null>(null);
  const [resetResult, setResetResult] = useState<ResetPasswordResult | null>(
    null,
  );
  const [resetSubmitting, setResetSubmitting] = useState(false);
  const [copied, setCopied] = useState(false);

  const openReset = (row: TenantItem) => {
    setResetTarget(row);
    setResetResult(null);
    setCopied(false);
    setResetOpen(true);
  };

  const submitReset = async () => {
    if (!resetTarget) return;
    setResetSubmitting(true);
    try {
      const result = await resetTenantPassword(resetTarget.id);
      setResetResult(result);
    } finally {
      setResetSubmitting(false);
    }
  };

  const copyTempPassword = () => {
    if (!resetResult) return;
    navigator.clipboard?.writeText(resetResult.tempPassword);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  // ---------- 启用 / 禁用 ----------
  const toggleStatus = (row: TenantItem) => {
    const disabling = row.status === 1;
    modal.confirm({
      title: disabling ? '确认禁用租户？' : '确认启用租户？',
      content: disabling
        ? `禁用后，租户「${row.name}」下的所有用户将无法登录，已进行中的操作将被中断。确认继续？`
        : `启用后，租户「${row.name}」下的用户可正常登录使用。请确认该租户的到期时间仍有效。`,
      okText: disabling ? '确认禁用' : '确认启用',
      okButtonProps: disabling ? { danger: true } : undefined,
      cancelText: '取消',
      onOk: async () => {
        await updateTenantStatus(row.id, disabling ? 0 : 1);
        message.success('操作成功');
        actionRef.current?.reload();
      },
    });
  };

  const columns: ProColumns<TenantItem>[] = [
    {
      title: '租户名称',
      dataIndex: 'name',
      hideInTable: true,
      fieldProps: { placeholder: '请输入租户名称' },
    },
    {
      title: '状态',
      dataIndex: 'status',
      hideInTable: true,
      valueType: 'select',
      valueEnum: { 1: { text: '启用' }, 0: { text: '禁用' } },
    },
    {
      title: '到期时间',
      dataIndex: 'expireRange',
      hideInTable: true,
      valueType: 'dateRange',
      search: {
        transform: (value) => ({
          expireDateFrom: value?.[0],
          expireDateTo: value?.[1],
        }),
      },
    },
    {
      title: '租户编码',
      dataIndex: 'code',
      search: false,
      width: 110,
      render: (_, r) => <span className="num">{r.code}</span>,
    },
    {
      title: '租户名称',
      dataIndex: 'name',
      search: false,
      width: 170,
      ellipsis: true,
    },
    {
      title: '套餐名称',
      dataIndex: 'packageName',
      search: false,
      width: 110,
      render: (_, r) => <Tag color="blue">{r.packageName}</Tag>,
    },
    { title: '联系人', dataIndex: 'contactName', search: false, width: 90 },
    {
      title: '联系手机',
      dataIndex: 'contactPhone',
      search: false,
      width: 120,
      render: (_, r) => <span className="num">{r.contactPhone}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      search: false,
      width: 80,
      render: (_, r) =>
        r.status === 1 ? (
          <Badge status="success" text="启用" />
        ) : (
          <Badge status="error" text="禁用" />
        ),
    },
    {
      title: '到期时间',
      dataIndex: 'expireTime',
      search: false,
      width: 180,
      render: (_, r) => {
        const diffDays = dayjs(r.expireTime).diff(dayjs(), 'day');
        const soon = r.status === 1 && diffDays <= 7;
        return (
          <Space size={4}>
            {soon && (
              <Tooltip
                title={diffDays < 0 ? '已过期' : `距到期还有 ${diffDays} 天`}
              >
                <WarningFilled style={{ color: palette.orange }} />
              </Tooltip>
            )}
            <span
              className="num"
              style={{ color: soon ? palette.orange : undefined }}
            >
              {formatDateTime(r.expireTime)}
            </span>
          </Space>
        );
      },
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
      width: 220,
      render: (_, row) => [
        access['tenant:list:edit'] && (
          <a key="edit" onClick={() => openEdit(row)}>
            编辑
          </a>
        ),
        access['tenant:list:edit'] && (
          <a key="renew" onClick={() => openRenew(row)}>
            续期
          </a>
        ),
        access['tenant:list:resetPwd'] && (
          <a key="reset" onClick={() => openReset(row)}>
            重置密码
          </a>
        ),
        access['tenant:list:status'] && (
          <a
            key="status"
            style={{ color: row.status === 1 ? palette.red : palette.green }}
            onClick={() => toggleStatus(row)}
          >
            {row.status === 1 ? '禁用' : '启用'}
          </a>
        ),
      ],
    },
  ];

  const sectionTitle = (icon: React.ReactNode, text: string) => (
    <Divider titlePlacement="start" style={{ margin: '8px 0 16px' }}>
      <Space size={6} style={{ fontSize: 14, fontWeight: 600 }}>
        {icon}
        {text}
      </Space>
    </Divider>
  );

  return (
    <>
      <ProTable<TenantItem>
        headerTitle="租户管理"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto', defaultCollapsed: false }}
        options={false}
        request={async (params) => {
          const res = await getTenantList({
            current: params.current,
            pageSize: params.pageSize,
            name: params.name,
            status: params.status,
            expireDateFrom: (params as Record<string, string>).expireDateFrom,
            expireDateTo: (params as Record<string, string>).expireDateTo,
          });
          return res;
        }}
        toolBarRender={() => [
          access['tenant:list:add'] && (
            <Button
              key="add"
              type="primary"
              icon={<PlusOutlined />}
              onClick={openCreate}
            >
              新增租户
            </Button>
          ),
        ]}
      />

      {/* 新增 / 编辑租户 */}
      <DrawerForm
        title={editing ? `编辑租户 — ${editing.name}` : '新增租户'}
        width={600}
        open={drawerOpen}
        onOpenChange={setDrawerOpen}
        onFinish={submitSave}
        initialValues={
          editing
            ? {
                name: editing.name,
                packageId: editing.packageId,
                contactName: editing.contactName,
                contactPhone: editing.contactPhone,
                contactEmail: editing.contactEmail,
                expireDate: dayjs(editing.expireTime),
                remark: editing.remark,
              }
            : {}
        }
        drawerProps={{ destroyOnClose: true }}
        submitTimeout={2000}
      >
        {sectionTitle(
          <InfoCircleOutlined style={{ color: palette.link }} />,
          '基础信息',
        )}
        {editing && (
          <div style={{ marginBottom: 24 }}>
            <div style={{ fontSize: 13, marginBottom: 8, color: palette.sub }}>
              租户编码（不可修改）
            </div>
            <span className="num" style={{ color: palette.mute }}>
              {editing.code}
            </span>
          </div>
        )}
        <ProFormText
          name="name"
          label="租户名称"
          placeholder="请输入公司或团队名称"
          rules={[
            {
              required: true,
              min: 2,
              max: 100,
              message: '请输入2-100字符的租户名称',
            },
          ]}
        />
        <ProFormSelect
          name="packageId"
          label="套餐"
          placeholder="请选择套餐"
          showSearch
          options={packageOptions.map((p) => ({ label: p.name, value: p.id }))}
          rules={[{ required: true, message: '请选择套餐' }]}
        />

        {sectionTitle(
          <UserOutlined style={{ color: palette.link }} />,
          '联系人信息',
        )}
        <div style={{ display: 'flex', gap: 16 }}>
          <ProFormText
            name="contactName"
            label="联系人姓名"
            placeholder="请输入联系人姓名"
            rules={[
              {
                required: true,
                min: 2,
                max: 50,
                message: '请输入2-50字符的姓名',
              },
            ]}
            formItemProps={{ style: { flex: 1 } }}
          />
          <ProFormText
            name="contactPhone"
            label="联系人手机"
            placeholder="请输入手机号码"
            rules={[
              { required: true, message: '请输入手机号码' },
              { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号码' },
            ]}
            formItemProps={{ style: { flex: 1 } }}
          />
        </div>
        <ProFormText
          name="contactEmail"
          label="联系人邮箱"
          placeholder="请输入邮箱地址（将作为登录账号）"
          rules={[
            { required: true, message: '请输入邮箱地址' },
            { type: 'email', message: '请输入正确的邮箱地址' },
          ]}
        />

        {sectionTitle(
          <CalendarOutlined style={{ color: palette.link }} />,
          '服务信息',
        )}
        <ProFormDatePicker
          name="expireDate"
          label="到期时间"
          placeholder="请选择到期日期"
          rules={[{ required: true, message: '请选择到期日期' }]}
          fieldProps={{
            style: { width: '100%' },
            disabledDate: (d: dayjs.Dayjs) =>
              d.isBefore(dayjs(), 'day') || d.isSame(dayjs(), 'day'),
          }}
        />
        <ProFormTextArea
          name="remark"
          label="备注"
          placeholder="选填，平台内部备注（租户不可见）"
          fieldProps={{ rows: 3, maxLength: 500 }}
        />
      </DrawerForm>

      {/* 续期 */}
      <Modal
        title={`租户续期 — ${renewTarget?.name ?? ''}`}
        open={renewOpen}
        onCancel={() => setRenewOpen(false)}
        onOk={submitRenew}
        okText="确认续期"
        confirmLoading={renewSubmitting}
        destroyOnClose
      >
        <Space orientation="vertical" size={16} style={{ width: '100%' }}>
          <div
            style={{
              padding: 12,
              borderRadius: 8,
              background: palette.inset,
              display: 'flex',
              justifyContent: 'space-between',
            }}
          >
            <span style={{ color: palette.sub }}>当前到期时间</span>
            <span className="num">
              {renewTarget && formatDateTime(renewTarget.expireTime)}
            </span>
          </div>
          <Radio.Group
            value={renewMode}
            onChange={(e) => setRenewMode(e.target.value)}
            style={{ width: '100%' }}
          >
            <Space orientation="vertical" size={12} style={{ width: '100%' }}>
              <Radio value="DURATION">按时长续期</Radio>
              {renewMode === 'DURATION' && (
                <div style={{ marginLeft: 24 }}>
                  <Radio.Group
                    options={RENEW_MONTHS_OPTIONS}
                    optionType="button"
                    value={renewMonths}
                    onChange={(e) => setRenewMonths(e.target.value)}
                  />
                  <div
                    style={{ marginTop: 8, fontSize: 12, color: palette.mute }}
                  >
                    续期后到期时间：
                    <span className="num">{renewPreviewDate}</span>
                  </div>
                </div>
              )}
              <Radio value="DATE">指定到期日期</Radio>
              {renewMode === 'DATE' && (
                <div style={{ marginLeft: 24 }}>
                  <DatePicker
                    value={renewDate}
                    onChange={setRenewDate}
                    disabledDate={(d) =>
                      !!renewTarget &&
                      d.isBefore(dayjs(renewTarget.expireTime), 'day')
                    }
                  />
                </div>
              )}
            </Space>
          </Radio.Group>
        </Space>
      </Modal>

      {/* 重置管理员密码 */}
      <Modal
        title={
          resetResult
            ? '密码重置成功'
            : `重置管理员密码 — ${resetTarget?.name ?? ''}`
        }
        open={resetOpen}
        onCancel={() => setResetOpen(false)}
        footer={
          resetResult
            ? [
                <Button key="close" onClick={() => setResetOpen(false)}>
                  关闭
                </Button>,
              ]
            : [
                <Button key="cancel" onClick={() => setResetOpen(false)}>
                  取消
                </Button>,
                <Button
                  key="ok"
                  danger
                  loading={resetSubmitting}
                  onClick={submitReset}
                >
                  确认重置
                </Button>,
              ]
        }
        destroyOnClose
      >
        {resetResult ? (
          <Space orientation="vertical" size={16} style={{ width: '100%' }}>
            <div style={{ textAlign: 'center' }}>
              <CheckCircleFilled
                style={{ fontSize: 40, color: palette.green }}
              />
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: palette.sub }}>管理员账号</span>
              <span className="num">{resetResult.adminEmail}</span>
            </div>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                padding: 12,
                borderRadius: 8,
                background: palette.accentSoft,
              }}
            >
              <span style={{ color: palette.sub }}>临时密码</span>
              <Space>
                <span
                  className="num"
                  style={{ color: palette.link, fontWeight: 600 }}
                >
                  {resetResult.tempPassword}
                </span>
                <Tooltip title={copied ? '已复制' : '复制'}>
                  <CopyOutlined
                    style={{ cursor: 'pointer' }}
                    onClick={copyTempPassword}
                  />
                </Tooltip>
              </Space>
            </div>
            <div role="alert" style={{ fontSize: 12, color: palette.mute }}>
              临时密码仅展示一次，请立即告知租户管理员并提醒其登录后修改密码。
            </div>
          </Space>
        ) : (
          <Space orientation="vertical" size={12} style={{ width: '100%' }}>
            <div>此操作将为该租户管理员账号重置登录密码。</div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: palette.sub }}>管理员账号</span>
              <span className="num">{resetTarget?.contactEmail}</span>
            </div>
          </Space>
        )}
      </Modal>
    </>
  );
};

export default TenantListPage;
