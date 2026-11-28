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
import { App, Button, Form, Input, Upload } from 'antd';
import {
  Pencil,
  Plus,
  Search,
  Trash2,
  TriangleAlert,
  UploadIcon,
} from 'lucide-react';
import React, { useEffect, useRef, useState } from 'react';
import { useColumnSettings } from '@/components/ColumnSettings';
import { useConfirmDanger } from '@/components/ConfirmDanger';
import EllipsisText, { ellipsisRender } from '@/components/EllipsisText';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
  ViewTabs,
} from '@/components/ListLayout';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { proAuditColumns } from '@/components/UserName';
import { useAppTheme } from '@/theme/AppTheme';
import { MONO_FONT } from '@/theme/fonts';
import { APPEARANCE_IMAGE_KEYS } from '@/utils/appearance';
import { usePagination } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { CONFIG_KEY_MESSAGE, CONFIG_KEY_PATTERN } from './configKey';
import { GROUP_OPTIONS, groupLabel } from './groupLabel';
import type { ConfigGroupCount, ConfigItem, ConfigType } from './service';
import {
  createConfig,
  deleteConfig,
  getConfigGroupCounts,
  getConfigList,
  updateConfigValue,
  uploadConfigImage,
} from './service';

const ImageUrlField: React.FC<{ name: string; allowEmpty?: boolean }> = ({
  name,
  allowEmpty,
}) => {
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
        placeholder={
          allowEmpty
            ? '留空使用默认样式；或填写图片地址、点击右侧上传图片'
            : '以 http/https 开头或以 / 开头的相对路径，或点击右侧上传图片'
        }
        rules={[
          { required: !allowEmpty, message: '请输入配置值' },
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
                <UploadIcon
                  size="1em"
                  className={uploading ? 'zhul-spin' : undefined}
                />
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

/** 配置类型的中文名（ui-redesign S9：不把 STRING 等技术枚举展示给用户；类型是属性，不着色） */
const TYPE_LABEL: Record<ConfigType, string> = {
  STRING: '文本',
  INTEGER: '整数',
  BOOLEAN: '开关',
  JSON: 'JSON',
  URL: '链接',
};

const ConfigPage: React.FC = () => {
  const pager = usePagination(20);
  const { palette: p } = useAppTheme();
  const actionRef = useRef<ActionType>(undefined);
  const { message } = App.useApp();
  const confirmDanger = useConfirmDanger();
  const access = useAccess();

  const [keyword, setKeyword] = useState('');
  /** 关键词点「查询」或回车才生效 */
  const [draftKeyword, setDraftKeyword] = useState('');
  // 首次查询返回前没有总数，主卡不显示「共 0 条」
  const [total, setTotal] = useState<number>();
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
    confirmDanger({
      title: `删除配置项「${record.configName}」？`,
      width: 480,
      content: `键：${record.configKey}。删除后不可恢复，请确认没有业务代码依赖这个配置键。`,
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
      return <span style={{ fontFamily: MONO_FONT }}>****</span>;
    }
    if (record.configType === 'BOOLEAN') {
      // 开关显示「开启 / 关闭」，不显示 true / false（S9）
      return record.configValue === 'true' ? '开启' : '关闭';
    }
    return (
      <EllipsisText
        text={record.configValue}
        style={{ fontFamily: MONO_FONT }}
      />
    );
  };

  const columns: ProColumns<ConfigItem>[] = [
    // 名称在前、配置键在后（ui-redesign S9、P17：技术键是次要信息）
    {
      fixed: 'left',
      title: '配置名称',
      dataIndex: 'configName',
      width: 150,
      render: ellipsisRender,
    },
    {
      title: '配置键',
      dataIndex: 'configKey',
      width: 220,
      render: (_, r) => (
        <EllipsisText
          text={r.configKey}
          style={{ fontFamily: MONO_FONT, color: p.mute }}
        />
      ),
    },
    {
      title: '类型',
      dataIndex: 'configType',
      width: 90,
      render: (_, r) => TYPE_LABEL[r.configType] ?? r.configType,
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
      render: (_, r) => (r.isBuiltin === 1 ? '内置' : '自定义'),
    },
    ...proAuditColumns<ConfigItem>(),
    {
      title: '操作',
      fixed: 'right',
      valueType: 'option',
      // 编辑放行内，删除收进「更多」（ui-redesign S6）
      width: rowActionsWidth(2),
      render: (_, r) => (
        <RowActions
          inline={[
            canEdit && {
              key: 'edit',
              label: '编辑',
              icon: <Pencil size={18} />,
              onClick: () => openEdit(r),
            },
          ]}
          more={[
            canDelete &&
              r.isBuiltin !== 1 && {
                key: 'delete',
                label: '删除',
                icon: <Trash2 size={16} />,
                danger: true,
              },
          ]}
          onMore={() => handleDelete(r)}
        />
      ),
    },
  ];
  const columnsSet = useColumnSettings('system-config', columns);

  const needsRestart = (remark?: string) => !!remark && remark.includes('重启');

  const renderValueEditor = (
    type: ConfigType,
    name: string,
    isEncrypted?: boolean,
    isEdit?: boolean,
    allowEmpty?: boolean,
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
            fieldProps={{ rows: 5, style: { fontFamily: MONO_FONT } }}
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
        return <ImageUrlField key={name} name={name} allowEmpty={allowEmpty} />;
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
      <PageHeader
        extra={
          canAdd && (
            <Button
              type="primary"
              icon={<Plus size="1em" />}
              onClick={() => setCreateOpen(true)}
            >
              新增自定义配置
            </Button>
          )
        }
      />
      {/* 配置分组是不同类的内容，用页签；数量写「名称 N」 */}
      <ViewTabs
        activeKey={activeGroup}
        onChange={(key) => setActiveGroup(key)}
        items={[
          { key: 'ALL', label: '全部', count: totalCount },
          ...groupCounts.map((g) => ({
            key: g.group,
            label: groupLabel(g.group),
            count: g.count,
          })),
        ]}
      />
      <FilterCard
        onSearch={() => {
          // 关键词没变时条件不变，表格不会自己刷新，点查询也要重新取数
          setKeyword(draftKeyword.trim());
          actionRef.current?.reload();
        }}
        onReset={() => {
          setDraftKeyword('');
          setKeyword('');
          setActiveGroup('ALL');
        }}
      >
        <FilterField label="关键词" span={2}>
          <Input
            allowClear
            prefix={<Search size={16} />}
            placeholder="配置键或配置名称"
            aria-label="关键词"
            value={draftKeyword}
            onChange={(e) => setDraftKeyword(e.target.value)}
            onPressEnter={() => setKeyword(draftKeyword.trim())}
          />
        </FilterField>
      </FilterCard>
      <MainCard settings={columnsSet.settings} count={total}>
        <ProTable<ConfigItem>
          scroll={{ x: tableScrollX(columnsSet.columns) }}
          actionRef={actionRef}
          rowKey="id"
          columns={columnsSet.columns}
          search={false}
          options={false}
          toolBarRender={false}
          pagination={pager}
          request={async (params) => {
            const res = await getConfigList({
              current: params.current,
              pageSize: params.pageSize,
              keyword,
              group: activeGroup === 'ALL' ? undefined : activeGroup,
            });
            setTotal(res.total ?? 0);
            return res;
          }}
          params={{ keyword, activeGroup }}
        />
      </MainCard>

      {/* 编辑配置值 */}
      <ModalForm
        name="configEdit"
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
        modalProps={{ destroyOnHidden: true, maskClosable: false }}
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
              APPEARANCE_IMAGE_KEYS.includes(editingConfig.configKey),
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
            <TriangleAlert size="1em" style={{ color: p.orange }} />
            <span style={{ color: p.orange, fontSize: 12 }}>
              该配置修改后需重启服务生效
            </span>
          </div>
        )}
      </ModalForm>

      {/* 新增自定义配置 */}
      <ModalForm
        name="configCreate"
        title="新增自定义配置"
        width={520}
        open={createOpen}
        onOpenChange={setCreateOpen}
        layout="vertical"
        onFinish={handleCreateFinish}
        initialValues={{
          configType: 'STRING',
          configGroup: 'custom',
          isEncrypted: false,
        }}
        modalProps={{ destroyOnHidden: true, maskClosable: false }}
      >
        <ProFormText
          name="configKey"
          label="配置键"
          placeholder="点分多段命名，如 custom.order.auto-close"
          fieldProps={{ maxLength: 128 }}
          rules={[
            { required: true, message: '请输入配置键' },
            { pattern: CONFIG_KEY_PATTERN, message: CONFIG_KEY_MESSAGE },
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
          options={GROUP_OPTIONS.map((g) => ({
            label: groupLabel(g),
            value: g,
          }))}
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
          fontWeight: 600,
          color: p.ink,
          fontFamily: mono ? MONO_FONT : undefined,
        }}
      >
        {value}
      </span>
    </div>
  );
};

export default ConfigPage;
