import {
  PlusOutlined,
  SearchOutlined,
  UploadOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  ProFormDependency,
  ProFormDigit,
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
  Form,
  Input,
  Space,
  Tabs,
  Tag,
  Tooltip,
  Upload,
} from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import type { ConfigGroupCount, ConfigItem, ConfigType } from './service';
import {
  createConfig,
  deleteConfig,
  getConfigGroupCounts,
  getConfigList,
  updateConfigValue,
  uploadConfigImage,
} from './service';

const GROUP_OPTIONS = [
  '基础设置',
  '安全策略',
  '外观设置',
  '邮件设置',
  '自定义',
];

const ImageUrlField: React.FC<{ name: string }> = ({ name }) => {
  const { palette: p } = useAppTheme();
  const { message } = App.useApp();
  const form = Form.useFormInstance();
  const value = Form.useWatch(name, form);
  const [uploading, setUploading] = useState(false);

  const handleUpload = async (file: File) => {
    setUploading(true);
    try {
      const url = await uploadConfigImage(file);
      if (url) {
        form.setFieldValue(name, url);
        form.validateFields([name]).catch(() => undefined);
        message.success('图片上传成功');
      } else {
        message.error('图片上传失败，请稍后重试');
      }
    } catch {
      message.error('图片上传失败，请稍后重试');
    } finally {
      setUploading(false);
    }
    return false;
  };

  return (
    <>
      <ProFormText
        name={name}
        label="配置值"
        placeholder="以 http/https 开头或以 / 开头的相对路径，或点击右侧上传图片"
        rules={[
          { required: true, message: '请输入配置值' },
          {
            pattern: /^(https?:\/\/|\/).*/,
            message: '请输入合法的 URL 地址',
          },
        ]}
        fieldProps={{
          addonAfter: (
            <Upload
              showUploadList={false}
              accept="image/png,image/jpeg,image/webp,image/gif,image/svg+xml"
              beforeUpload={handleUpload}
            >
              <span
                style={{
                  cursor: 'pointer',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 4,
                }}
              >
                <UploadOutlined spin={uploading} />
                上传图片
              </span>
            </Upload>
          ),
        }}
      />
      {value && (
        <div style={{ marginTop: -8, marginBottom: 16 }}>
          <img
            src={value}
            alt="预览"
            style={{
              maxHeight: 80,
              maxWidth: '100%',
              borderRadius: 6,
              border: `1px solid ${p.hairline}`,
              display: 'block',
            }}
            onError={(e) => {
              e.currentTarget.style.display = 'none';
            }}
            onLoad={(e) => {
              e.currentTarget.style.display = 'block';
            }}
          />
        </div>
      )}
    </>
  );
};

const TYPE_COLOR: Record<ConfigType, string> = {
  STRING: 'blue',
  INTEGER: 'green',
  BOOLEAN: 'orange',
  JSON: 'purple',
  URL: 'red',
};

const ConfigPage: React.FC = () => {
  const { palette: p } = useAppTheme();
  const actionRef = useRef<ActionType>();
  const { message, modal } = App.useApp();
  const access = useAccess();

  const [keyword, setKeyword] = useState('');
  const [activeGroup, setActiveGroup] = useState<string>('ALL');
  const [groupCounts, setGroupCounts] = useState<ConfigGroupCount[]>([]);

  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editingConfig, setEditingConfig] = useState<ConfigItem | null>(null);

  const canAdd = !!(access as Record<string, unknown>)['system:config:add'];
  const canEdit = !!(access as Record<string, unknown>)['system:config:edit'];
  const canDelete = !!(access as Record<string, unknown>)[
    'system:config:delete'
  ];

  const loadGroupCounts = async () => {
    setGroupCounts(await getConfigGroupCounts());
  };

  useEffect(() => {
    loadGroupCounts();
  }, []);

  const totalCount = groupCounts.reduce((sum, g) => sum + g.count, 0);

  const reload = () => {
    actionRef.current?.reload();
    loadGroupCounts();
  };

  const openEdit = (record: ConfigItem) => {
    setEditingConfig(record);
    setEditOpen(true);
  };

  const handleEditFinish = async (
    values: Record<string, any>,
  ): Promise<boolean> => {
    if (!editingConfig) return false;
    const raw = values.value;
    const value =
      editingConfig.configType === 'BOOLEAN'
        ? String(!!raw)
        : raw == null
          ? ''
          : String(raw);
    try {
      await updateConfigValue(editingConfig.id, value);
      message.success('保存成功');
      setEditOpen(false);
      setEditingConfig(null);
      reload();
      return true;
    } catch {
      message.error('保存失败');
      return false;
    }
  };

  const handleCreateFinish = async (
    values: Record<string, any>,
  ): Promise<boolean> => {
    const raw = values.configValue;
    const configValue =
      values.configType === 'BOOLEAN'
        ? String(!!raw)
        : raw == null
          ? ''
          : String(raw);
    const payload = {
      configKey: values.configKey,
      configName: values.configName,
      configValue,
      configType: values.configType,
      configGroup: values.configGroup,
      isEncrypted: values.isEncrypted ? 1 : 0,
      remark: values.remark,
    };
    try {
      await createConfig(payload);
      message.success('新增成功');
      setCreateOpen(false);
      setActiveGroup(payload.configGroup);
      reload();
      return true;
    } catch {
      message.error('新增失败');
      return false;
    }
  };

  const handleDelete = (record: ConfigItem) => {
    modal.confirm({
      title: '确认删除',
      width: 480,
      content: `确认删除配置项「${record.configName}」（键：${record.configKey}）？删除后不可恢复，请确认没有业务代码依赖此配置键。`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteConfig(record.id);
          message.success('删除成功');
          reload();
        } catch {
          message.error('删除失败');
        }
      },
    });
  };

  const renderValue = (record: ConfigItem) => {
    if (record.isEncrypted === 1) {
      return <span style={{ fontFamily: 'monospace' }}>****</span>;
    }
    if (record.configType === 'BOOLEAN') {
      return (
        <Tag color={record.configValue === 'true' ? 'blue' : 'default'}>
          {record.configValue}
        </Tag>
      );
    }
    return (
      <Tooltip title={record.configValue}>
        <span
          style={{
            fontFamily: 'monospace',
            display: 'inline-block',
            maxWidth: 220,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            verticalAlign: 'bottom',
          }}
        >
          {record.configValue}
        </span>
      </Tooltip>
    );
  };

  const columns: ProColumns<ConfigItem>[] = [
    {
      title: '配置键',
      dataIndex: 'configKey',
      width: 220,
      render: (_, r) => (
        <Tooltip title={r.configKey}>
          <span
            style={{
              fontFamily: 'monospace',
              display: 'inline-block',
              maxWidth: 200,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              verticalAlign: 'bottom',
            }}
          >
            {r.configKey}
          </span>
        </Tooltip>
      ),
    },
    { title: '配置名称', dataIndex: 'configName', width: 150 },
    {
      title: '类型',
      dataIndex: 'configType',
      width: 90,
      render: (_, r) => (
        <Tag color={TYPE_COLOR[r.configType]}>{r.configType}</Tag>
      ),
    },
    {
      title: '配置值',
      dataIndex: 'configValue',
      width: 220,
      render: (_, r) => renderValue(r),
    },
    {
      title: '来源',
      dataIndex: 'isBuiltin',
      width: 90,
      render: (_, r) =>
        r.isBuiltin === 1 ? (
          <Badge color="blue" text="内置" />
        ) : (
          <Badge color="default" text="自定义" />
        ),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      render: (_, record) => (
        <Space size={14}>
          {canEdit && <a onClick={() => openEdit(record)}>编辑</a>}
          {canDelete && record.isBuiltin !== 1 && (
            <a style={{ color: p.red }} onClick={() => handleDelete(record)}>
              删除
            </a>
          )}
        </Space>
      ),
    },
  ];

  const needsRestart = (remark?: string) => !!remark && remark.includes('重启');

  const renderValueEditor = (
    type: ConfigType,
    name: string,
    isEncrypted?: boolean,
    isEdit?: boolean,
  ) => {
    switch (type) {
      case 'INTEGER':
        return (
          <ProFormDigit
            name={name}
            label="配置值"
            rules={[{ required: true, message: '请输入配置值' }]}
          />
        );
      case 'BOOLEAN':
        return <ProFormSwitch name={name} label="配置值" />;
      case 'JSON':
        return (
          <ProFormTextArea
            name={name}
            label="配置值"
            fieldProps={{ rows: 5, style: { fontFamily: 'monospace' } }}
            rules={[
              { required: true, message: '请输入配置值' },
              {
                validator: (_: unknown, value: string) => {
                  if (!value) return Promise.resolve();
                  try {
                    JSON.parse(value);
                    return Promise.resolve();
                  } catch {
                    return Promise.reject(new Error('JSON 格式不合法'));
                  }
                },
              },
            ]}
          />
        );
      case 'URL':
        return <ImageUrlField key={name} name={name} />;
      case 'STRING':
      default:
        if (isEncrypted) {
          return (
            <ProFormText.Password
              name={name}
              label="配置值"
              placeholder={
                isEdit ? '（已配置，修改请重新输入）' : '请输入配置值'
              }
              fieldProps={{ maxLength: 4096 }}
              rules={
                isEdit ? [] : [{ required: true, message: '请输入配置值' }]
              }
            />
          );
        }
        return (
          <ProFormTextArea
            name={name}
            label="配置值"
            fieldProps={{ rows: 3, maxLength: 4096, showCount: true }}
            rules={[{ required: true, message: '请输入配置值' }]}
          />
        );
    }
  };

  return (
    <>
      <ProTable<ConfigItem>
        headerTitle="配置列表"
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        search={false}
        pagination={{ pageSize: 20 }}
        request={(params) =>
          getConfigList({
            current: params.current,
            pageSize: params.pageSize,
            keyword,
            group: activeGroup === 'ALL' ? undefined : activeGroup,
          })
        }
        params={{ keyword, activeGroup }}
        toolbar={{
          filter: (
            <Space direction="vertical" style={{ width: '100%' }} size={16}>
              <Space>
                <Input
                  placeholder="请输入配置键或配置名称"
                  prefix={<SearchOutlined />}
                  allowClear
                  style={{ width: 320 }}
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  onPressEnter={() => actionRef.current?.reload()}
                />
                <Button
                  type="primary"
                  onClick={() => actionRef.current?.reload()}
                >
                  搜索
                </Button>
                <Button
                  onClick={() => {
                    setKeyword('');
                    setActiveGroup('ALL');
                  }}
                >
                  重置
                </Button>
              </Space>
              <Tabs
                activeKey={activeGroup}
                onChange={(key) => setActiveGroup(key)}
                items={[
                  { key: 'ALL', label: `全部 (${totalCount})` },
                  ...groupCounts.map((g) => ({
                    key: g.group,
                    label: `${g.group} (${g.count})`,
                  })),
                ]}
              />
            </Space>
          ),
        }}
        toolBarRender={() => [
          canAdd && (
            <Button
              key="add"
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setCreateOpen(true)}
            >
              新增自定义配置
            </Button>
          ),
        ]}
      />

      {/* 编辑配置值 */}
      <ModalForm
        title="编辑配置"
        width={520}
        open={editOpen}
        onOpenChange={(open) => {
          setEditOpen(open);
          if (!open) setEditingConfig(null);
        }}
        layout="vertical"
        onFinish={handleEditFinish}
        initialValues={
          editingConfig
            ? {
                value:
                  editingConfig.configType === 'BOOLEAN'
                    ? editingConfig.configValue === 'true'
                    : editingConfig.isEncrypted === 1
                      ? undefined
                      : editingConfig.configValue,
              }
            : {}
        }
        modalProps={{ destroyOnClose: true, maskClosable: false }}
      >
        {editingConfig && (
          <>
            <div
              style={{
                background: p.inset,
                border: `1px solid ${p.hairline}`,
                borderRadius: 10,
                padding: 16,
                marginBottom: 24,
              }}
            >
              <InfoRow label="配置键" value={editingConfig.configKey} mono />
              <InfoRow label="配置名称" value={editingConfig.configName} />
              <InfoRow label="类型" value={editingConfig.configType} />
              <InfoRow
                label="来源"
                value={editingConfig.isBuiltin === 1 ? '内置' : '自定义'}
              />
              {editingConfig.remark && (
                <div style={{ fontSize: 12, color: p.mute, marginTop: 8 }}>
                  {editingConfig.remark}
                </div>
              )}
            </div>

            {renderValueEditor(
              editingConfig.configType,
              'value',
              editingConfig.isEncrypted === 1,
              true,
            )}
          </>
        )}

        {editingConfig && needsRestart(editingConfig.remark) && (
          <div
            style={{
              background: p.orangeSoft,
              borderRadius: 8,
              padding: '10px 12px',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              marginTop: 8,
            }}
          >
            <WarningOutlined style={{ color: p.orange }} />
            <span style={{ color: p.orange, fontSize: 12 }}>
              该配置修改后需重启服务生效
            </span>
          </div>
        )}
      </ModalForm>

      {/* 新增自定义配置 */}
      <ModalForm
        title="新增自定义配置"
        width={520}
        open={createOpen}
        onOpenChange={setCreateOpen}
        layout="vertical"
        onFinish={handleCreateFinish}
        initialValues={{
          configType: 'STRING',
          configGroup: '自定义',
          isEncrypted: false,
        }}
        modalProps={{ destroyOnClose: true, maskClosable: false }}
      >
        <ProFormText
          name="configKey"
          label="配置键"
          placeholder="点分层级命名，如 custom.cust_grade_rule"
          fieldProps={{ maxLength: 128 }}
          rules={[
            { required: true, message: '请输入配置键' },
            {
              pattern: /^[a-z][a-z0-9_.]{3,127}$/,
              message: '点分层级命名，小写字母+数字+点+下划线，4~128字符',
            },
            {
              validator: (_, value) =>
                value && String(value).startsWith('sys.')
                  ? Promise.reject(
                      new Error(
                        'sys. 为系统保留前缀，自定义配置请使用其他前缀',
                      ),
                    )
                  : Promise.resolve(),
            },
          ]}
        />
        <ProFormText
          name="configName"
          label="配置名称"
          placeholder="请输入配置名称"
          fieldProps={{ maxLength: 128 }}
          rules={[
            {
              required: true,
              min: 1,
              max: 128,
              message: '请输入1~128字符的配置名称',
            },
          ]}
        />
        <ProFormSelect
          name="configType"
          label="配置类型"
          rules={[{ required: true }]}
          options={['STRING', 'INTEGER', 'BOOLEAN', 'JSON', 'URL'].map((t) => ({
            label: t,
            value: t,
          }))}
        />
        <ProFormDependency name={['configType', 'isEncrypted']}>
          {({ configType, isEncrypted }) =>
            renderValueEditor(
              configType ?? 'STRING',
              'configValue',
              isEncrypted,
              false,
            )
          }
        </ProFormDependency>
        <ProFormSelect
          name="configGroup"
          label="配置分组"
          rules={[{ required: true }]}
          options={GROUP_OPTIONS.map((g) => ({ label: g, value: g }))}
        />
        <ProFormSwitch name="isEncrypted" label="是否加密" />
        <ProFormTextArea
          name="remark"
          label="备注"
          placeholder="填写配置说明（选填，最长256字符）"
          fieldProps={{ rows: 3, maxLength: 256, showCount: true }}
        />
      </ModalForm>
    </>
  );
};

const InfoRow: React.FC<{ label: string; value: string; mono?: boolean }> = ({
  label,
  value,
  mono,
}) => {
  const { palette: p } = useAppTheme();
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 6,
      }}
    >
      <span style={{ fontSize: 12, color: p.mute }}>{label}</span>
      <span
        style={{
          fontSize: 13,
          fontWeight: 500,
          color: p.ink,
          fontFamily: mono ? 'monospace' : undefined,
        }}
      >
        {value}
      </span>
    </div>
  );
};

export default ConfigPage;
