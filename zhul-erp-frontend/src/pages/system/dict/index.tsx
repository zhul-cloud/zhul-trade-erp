import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import {
  ModalForm,
  ProFormDigit,
  ProFormRadio,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import {
  App,
  Badge,
  Button,
  Empty,
  Input,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import type { DictItemRow, DictTypeItem } from './service';
import {
  createDictItem,
  createDictType,
  deleteDictItem,
  deleteDictType,
  getDictItemList,
  getDictTypeDeleteCheck,
  getDictTypeList,
  updateDictItem,
  updateDictType,
} from './service';

const CSS_CLASS_OPTIONS = [
  { label: 'default', value: 'default' },
  { label: 'success', value: 'success' },
  { label: 'warning', value: 'warning' },
  { label: 'error', value: 'error' },
  { label: 'processing', value: 'processing' },
];

const CSS_CLASS_COLOR: Record<string, string> = {
  success: 'success',
  warning: 'warning',
  error: 'error',
  processing: 'processing',
  default: 'default',
};

type TypeDrawerMode = 'create' | 'edit';
type ItemDrawerMode = 'create' | 'edit';

const DictPage: React.FC = () => {
  const { message, modal } = App.useApp();
  const access = useAccess();

  const [typeList, setTypeList] = useState<DictTypeItem[]>([]);
  const [keyword, setKeyword] = useState('');
  const [selectedType, setSelectedType] = useState<DictTypeItem | null>(null);
  const [itemList, setItemList] = useState<DictItemRow[]>([]);
  const [itemLoading, setItemLoading] = useState(false);

  const [typeModalOpen, setTypeModalOpen] = useState(false);
  const [typeDrawerMode, setTypeDrawerMode] =
    useState<TypeDrawerMode>('create');
  const [editingType, setEditingType] = useState<DictTypeItem | null>(null);

  const [itemModalOpen, setItemModalOpen] = useState(false);
  const [itemDrawerMode, setItemDrawerMode] =
    useState<ItemDrawerMode>('create');
  const [editingItem, setEditingItem] = useState<DictItemRow | null>(null);

  const canAdd = !!(access as Record<string, unknown>)['system:dict:add'];
  const canEdit = !!(access as Record<string, unknown>)['system:dict:edit'];
  const canDelete = !!(access as Record<string, unknown>)['system:dict:delete'];

  const loadTypes = async (): Promise<DictTypeItem[]> => {
    const list = await getDictTypeList(keyword || undefined);
    setTypeList(list);
    return list;
  };

  useEffect(() => {
    loadTypes();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword]);

  const loadItems = async (typeId: number) => {
    setItemLoading(true);
    try {
      setItemList(await getDictItemList(typeId));
    } finally {
      setItemLoading(false);
    }
  };

  useEffect(() => {
    if (selectedType) loadItems(selectedType.id);
    else setItemList([]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedType?.id]);

  const builtinTypes = useMemo(
    () => typeList.filter((t) => t.isBuiltin === 1),
    [typeList],
  );
  const customTypes = useMemo(
    () => typeList.filter((t) => t.isBuiltin !== 1),
    [typeList],
  );

  const openCreateType = () => {
    setTypeDrawerMode('create');
    setEditingType(null);
    setTypeModalOpen(true);
  };
  const openEditType = (record: DictTypeItem) => {
    setTypeDrawerMode('edit');
    setEditingType(record);
    setTypeModalOpen(true);
  };

  const handleTypeFinish = async (
    values: Record<string, any>,
  ): Promise<boolean> => {
    const payload = {
      dictType: values.dictType,
      dictName: values.dictName,
      status: values.status ?? 1,
      remark: values.remark,
    };
    try {
      if (typeDrawerMode === 'edit' && editingType) {
        await updateDictType(editingType.id, payload);
        message.success('编辑成功');
        const list = await loadTypes();
        const fresh = list.find((t) => t.id === editingType.id);
        if (fresh) setSelectedType(fresh);
      } else {
        await createDictType(payload);
        message.success('新增成功');
        const list = await loadTypes();
        const created = list.find((t) => t.dictType === payload.dictType);
        if (created) setSelectedType(created);
      }
      setTypeModalOpen(false);
      setEditingType(null);
      return true;
    } catch {
      message.error(typeDrawerMode === 'edit' ? '编辑失败' : '新增失败');
      return false;
    }
  };

  const handleDeleteType = async (record: DictTypeItem) => {
    const check = await getDictTypeDeleteCheck(record.id);
    if (check.builtin) {
      message.error('系统内置字典不可删除');
      return;
    }
    if (check.blocked) {
      message.error('请先删除该字典类型下的所有字典项');
      return;
    }
    modal.confirm({
      title: '确认删除',
      content: `确认删除字典类型「${record.dictName}」？`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteDictType(record.id);
          message.success('删除成功');
          if (selectedType?.id === record.id) setSelectedType(null);
          loadTypes();
        } catch {
          message.error('删除失败');
        }
      },
    });
  };

  const openCreateItem = () => {
    setItemDrawerMode('create');
    setEditingItem(null);
    setItemModalOpen(true);
  };
  const openEditItem = (record: DictItemRow) => {
    setItemDrawerMode('edit');
    setEditingItem(record);
    setItemModalOpen(true);
  };

  const handleItemFinish = async (
    values: Record<string, any>,
  ): Promise<boolean> => {
    if (!selectedType) return false;
    const payload = {
      dictTypeId: selectedType.id,
      itemCode:
        itemDrawerMode === 'edit' && editingItem
          ? editingItem.itemCode
          : values.itemCode,
      itemName: values.itemName,
      itemValue: values.itemValue,
      cssClass: values.cssClass,
      sortOrder: values.sortOrder ?? 0,
      isDefault: values.isDefault ? 1 : 0,
      status: values.status ?? 1,
      remark: values.remark,
    };
    try {
      if (itemDrawerMode === 'edit' && editingItem) {
        await updateDictItem(editingItem.id, payload);
        message.success('编辑成功');
      } else {
        await createDictItem(payload);
        message.success('新增成功');
      }
      setItemModalOpen(false);
      setEditingItem(null);
      loadItems(selectedType.id);
      return true;
    } catch {
      message.error(itemDrawerMode === 'edit' ? '编辑失败' : '新增失败');
      return false;
    }
  };

  const handleDeleteItem = (record: DictItemRow) => {
    modal.confirm({
      title: '确认删除',
      content: `确认删除字典项「${record.itemName}」？`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteDictItem(record.id);
          message.success('删除成功');
          if (selectedType) loadItems(selectedType.id);
        } catch {
          message.error('删除失败');
        }
      },
    });
  };

  const renderTypeRow = (t: DictTypeItem) => {
    const selected = selectedType?.id === t.id;
    return (
      <div
        key={t.id}
        onClick={() => setSelectedType(t)}
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '8px 10px',
          borderRadius: 8,
          cursor: 'pointer',
          background: selected ? '#EAF2FF' : 'transparent',
          marginBottom: 2,
        }}
      >
        <div style={{ minWidth: 0 }}>
          <Space size={6}>
            <span
              style={{
                fontSize: 13,
                fontWeight: selected ? 600 : 500,
                color: selected ? '#1677FF' : '#111111',
              }}
            >
              {t.dictName}
            </span>
            {t.isBuiltin === 1 && <Tag style={{ fontSize: 10 }}>内置</Tag>}
          </Space>
          <div
            style={{ fontSize: 11, color: '#94A3B8', fontFamily: 'monospace' }}
          >
            {t.dictType}
          </div>
        </div>
        {(canEdit || canDelete) && (
          <Space size={8} onClick={(e) => e.stopPropagation()}>
            {canEdit && (
              <EditOutlined
                style={{ color: '#5B6B82', fontSize: 13 }}
                onClick={() => openEditType(t)}
              />
            )}
            {canDelete &&
              (t.isBuiltin === 1 ? (
                <Tooltip title="系统内置字典不可删除">
                  <DeleteOutlined style={{ color: '#D1D5DB', fontSize: 13 }} />
                </Tooltip>
              ) : (
                <DeleteOutlined
                  style={{ color: '#DC2626', fontSize: 13 }}
                  onClick={() => handleDeleteType(t)}
                />
              ))}
          </Space>
        )}
      </div>
    );
  };

  const itemColumns = [
    { title: '字典项编码', dataIndex: 'itemCode', width: 140 },
    { title: '字典项名称', dataIndex: 'itemName', width: 140 },
    { title: '字典值', dataIndex: 'itemValue', width: 100 },
    {
      title: '样式标签',
      dataIndex: 'cssClass',
      width: 110,
      render: (v: string, r: DictItemRow) =>
        v ? (
          <Tag color={CSS_CLASS_COLOR[v] ?? 'default'}>{r.itemName}</Tag>
        ) : (
          '-'
        ),
    },
    {
      title: '默认',
      dataIndex: 'isDefault',
      width: 70,
      render: (v: number) => (v === 1 ? '✓' : '-'),
    },
    { title: '排序', dataIndex: 'sortOrder', width: 70 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number) =>
        v === 1 ? (
          <Badge status="success" text="启用" />
        ) : (
          <Badge status="default" text="禁用" />
        ),
    },
    {
      title: '操作',
      width: 120,
      render: (_: unknown, record: DictItemRow) => (
        <Space size={14}>
          {canEdit && <a onClick={() => openEditItem(record)}>编辑</a>}
          {canDelete && (
            <a
              style={{ color: '#DC2626' }}
              onClick={() => handleDeleteItem(record)}
            >
              删除
            </a>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <div
        style={{
          display: 'flex',
          background: '#fff',
          borderRadius: 12,
          overflow: 'hidden',
          border: '1px solid #F0F0F0',
          minHeight: 640,
        }}
      >
        {/* 左侧字典类型 */}
        <div
          style={{
            width: 300,
            flexShrink: 0,
            padding: 16,
            borderRight: '1px solid #F0F0F0',
          }}
        >
          {canAdd && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              block
              style={{ marginBottom: 12 }}
              onClick={openCreateType}
            >
              新增字典类型
            </Button>
          )}
          <Input
            placeholder="搜索字典名称"
            prefix={<SearchOutlined />}
            allowClear
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ marginBottom: 12 }}
          />
          {typeList.length === 0 ? (
            <Empty description="暂无字典数据" style={{ marginTop: 40 }} />
          ) : (
            <>
              {builtinTypes.length > 0 && (
                <>
                  <div
                    style={{
                      fontSize: 12,
                      fontWeight: 600,
                      color: '#94A3B8',
                      margin: '8px 4px',
                    }}
                  >
                    系统内置
                  </div>
                  {builtinTypes.map(renderTypeRow)}
                </>
              )}
              {customTypes.length > 0 && (
                <>
                  <div
                    style={{
                      fontSize: 12,
                      fontWeight: 600,
                      color: '#94A3B8',
                      margin: '12px 4px 8px',
                    }}
                  >
                    自定义
                  </div>
                  {customTypes.map(renderTypeRow)}
                </>
              )}
            </>
          )}
        </div>

        {/* 右侧字典项 */}
        <div style={{ flex: 1, padding: 20, minWidth: 0 }}>
          {selectedType ? (
            <>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  marginBottom: 16,
                }}
              >
                <div>
                  <div style={{ fontSize: 16, fontWeight: 700 }}>
                    当前字典：{selectedType.dictName}
                  </div>
                  <div
                    style={{
                      fontSize: 12,
                      color: '#94A3B8',
                      fontFamily: 'monospace',
                    }}
                  >
                    {selectedType.dictType}
                  </div>
                </div>
                {canAdd && (
                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    onClick={openCreateItem}
                  >
                    新增字典项
                  </Button>
                )}
              </div>
              <Table
                rowKey="id"
                columns={itemColumns}
                dataSource={itemList}
                loading={itemLoading}
                pagination={{ pageSize: 20 }}
                locale={{
                  emptyText: '该字典类型下暂无字典项，点击「新增字典项」添加',
                }}
              />
            </>
          ) : (
            <Empty
              description="请在左侧选择一个字典类型"
              style={{ marginTop: 80 }}
            />
          )}
        </div>
      </div>

      {/* 新增/编辑字典类型 */}
      <ModalForm
        title={typeDrawerMode === 'edit' ? '编辑字典类型' : '新增字典类型'}
        width={480}
        open={typeModalOpen}
        onOpenChange={setTypeModalOpen}
        layout="vertical"
        initialValues={
          typeDrawerMode === 'edit' && editingType
            ? {
                dictName: editingType.dictName,
                status: editingType.status,
                remark: editingType.remark,
              }
            : { status: 1 }
        }
        onFinish={handleTypeFinish}
        modalProps={{ destroyOnClose: true, maskClosable: false }}
      >
        {typeDrawerMode === 'edit' ? (
          <div style={{ marginBottom: 24 }}>
            <div style={{ fontSize: 13, marginBottom: 8 }}>字典类型编码</div>
            <Input disabled value={editingType?.dictType} />
          </div>
        ) : (
          <ProFormText
            name="dictType"
            label="字典类型编码"
            placeholder="蛇形命名，如 biz_goods_category"
            fieldProps={{ maxLength: 64 }}
            rules={[
              { required: true, message: '请输入字典类型编码' },
              {
                pattern: /^[a-z_]{2,64}$/,
                message: '编码仅支持小写字母和下划线，长度2~64位',
              },
            ]}
          />
        )}
        <ProFormText
          name="dictName"
          label="字典类型名称"
          placeholder="请输入字典类型名称"
          fieldProps={{ maxLength: 128 }}
          rules={[
            {
              required: true,
              min: 1,
              max: 128,
              message: '请输入1~128字符的名称',
            },
          ]}
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
          placeholder="填写补充说明（选填，最长256字符）"
          fieldProps={{ rows: 3, maxLength: 256, showCount: true }}
        />
      </ModalForm>

      {/* 新增/编辑字典项 */}
      <ModalForm
        title={itemDrawerMode === 'edit' ? '编辑字典项' : '新增字典项'}
        width={520}
        open={itemModalOpen}
        onOpenChange={setItemModalOpen}
        layout="vertical"
        initialValues={
          itemDrawerMode === 'edit' && editingItem
            ? {
                itemName: editingItem.itemName,
                itemValue: editingItem.itemValue,
                cssClass: editingItem.cssClass || undefined,
                isDefault: editingItem.isDefault === 1,
                sortOrder: editingItem.sortOrder,
                status: editingItem.status,
                remark: editingItem.remark,
              }
            : { sortOrder: 0, status: 1, isDefault: false }
        }
        onFinish={handleItemFinish}
        modalProps={{ destroyOnClose: true, maskClosable: false }}
      >
        <div style={{ marginBottom: 24 }}>
          <div style={{ fontSize: 13, marginBottom: 8 }}>所属字典类型</div>
          <Input disabled value={selectedType?.dictName} />
        </div>
        {itemDrawerMode === 'edit' ? (
          <div style={{ marginBottom: 24 }}>
            <div style={{ fontSize: 13, marginBottom: 8 }}>字典项编码</div>
            <Input disabled value={editingItem?.itemCode} />
          </div>
        ) : (
          <ProFormText
            name="itemCode"
            label="字典项编码"
            placeholder="大写字母+下划线，如 ENABLED"
            fieldProps={{ maxLength: 64 }}
            rules={[
              { required: true, message: '请输入字典项编码' },
              {
                pattern: /^[A-Z_]{2,64}$/,
                message: '编码仅支持大写字母和下划线，长度2~64位',
              },
            ]}
          />
        )}
        <ProFormText
          name="itemName"
          label="字典项名称"
          placeholder="请输入字典项名称"
          fieldProps={{ maxLength: 128 }}
          rules={[
            {
              required: true,
              min: 1,
              max: 128,
              message: '请输入1~128字符的名称',
            },
          ]}
        />
        <ProFormText
          name="itemValue"
          label="字典值"
          placeholder="请输入字典值，最多256字符"
          fieldProps={{ maxLength: 256 }}
          rules={[{ required: true, message: '请输入字典值' }]}
        />
        <ProFormSelect
          name="cssClass"
          label="样式标签"
          placeholder="default / success / warning / error / processing"
          options={CSS_CLASS_OPTIONS}
        />
        <ProFormSwitch
          name="isDefault"
          label="是否默认"
          tooltip="设为默认后，原默认项将自动取消"
        />
        <ProFormDigit
          name="sortOrder"
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
          placeholder="填写补充说明（选填，最长256字符）"
          fieldProps={{ rows: 3, maxLength: 256, showCount: true }}
        />
      </ModalForm>
    </>
  );
};

export default DictPage;
