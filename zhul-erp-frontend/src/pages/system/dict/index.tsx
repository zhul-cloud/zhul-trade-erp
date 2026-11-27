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
import { App, Badge, Button, Input, Table, Tag, Tooltip } from 'antd';
import { Pencil, Plus, Search, Trash2 } from 'lucide-react';
import React, { useEffect, useMemo, useState } from 'react';
import { useColumnSettings } from '@/components/ColumnSettings';
import { useConfirmDanger } from '@/components/ConfirmDanger';
import { EmptyState, tableEmpty } from '@/components/EmptyState';
import { PageHeader } from '@/components/ListLayout';
import { Pill } from '@/components/Pill';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { useAppTheme } from '@/theme/AppTheme';
import { usePagination } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
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
  const pager = usePagination(20);
  const { palette: p } = useAppTheme();
  const { message } = App.useApp();
  const confirmDanger = useConfirmDanger();
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
    loadTypes().then((list) => {
      // 默认选中第一个类型，右侧不空着；当前类型被搜索过滤掉时改选第一项（ui-redesign R8-1）
      setSelectedType((cur) =>
        cur && list.some((t) => t.id === cur.id) ? cur : (list[0] ?? null),
      );
    });
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
    const editing = typeDrawerMode === 'edit' && editingType;
    const payload = {
      // 编辑时编码不可改、表单里也不提交，取原值满足后端必填校验（UI-025）
      dictType: editing ? editingType.dictType : values.dictType,
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
    confirmDanger({
      title: `删除字典类型「${record.dictName}」？`,
      onOk: async () => {
        try {
          await deleteDictType(record.id);
          message.success('删除成功');
          const list = await loadTypes();
          if (selectedType?.id === record.id) setSelectedType(list[0] ?? null);
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
      itemNameEn: values.itemNameEn,
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
    confirmDanger({
      title: `删除字典项「${record.itemName}」？`,
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

  const [hoverRow, setHoverRow] = useState<number>();
  const [focusRow, setFocusRow] = useState<number>();
  const renderTypeRow = (t: DictTypeItem) => {
    const selected = selectedType?.id === t.id;
    const showMore = selected || hoverRow === t.id || focusRow === t.id;
    const builtin = t.isBuiltin === 1;
    return (
      <div
        key={t.id}
        // 整行可点选中；键盘用行内的名称按钮
        onClick={() => setSelectedType(t)}
        onMouseEnter={() => setHoverRow(t.id)}
        onMouseLeave={() => setHoverRow(undefined)}
        onFocus={() => setFocusRow(t.id)}
        onBlur={(e) => {
          if (!e.currentTarget.contains(e.relatedTarget as Node))
            setFocusRow(undefined);
        }}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 4,
          height: 36,
          padding: '0 2px 0 10px',
          borderRadius: 8,
          cursor: 'pointer',
          background: selected
            ? p.primarySoft
            : hoverRow === t.id
              ? p.hover
              : 'transparent',
        }}
      >
        <button
          type="button"
          aria-current={selected || undefined}
          title={t.dictName}
          style={{
            flex: 1,
            minWidth: 0,
            height: '100%',
            padding: 0,
            border: 0,
            background: 'transparent',
            textAlign: 'left',
            cursor: 'pointer',
            fontSize: 14,
            fontWeight: selected ? 600 : 400,
            color: selected ? p.link : p.ink,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {t.dictName}
        </button>
        {(canEdit || canDelete) && (
          <div style={{ opacity: showMore ? 1 : 0 }}>
            <RowActions
              moreLabel={`更多：${t.dictName}`}
              more={[
                canEdit && {
                  key: 'edit',
                  label: '编辑',
                  icon: <Pencil size="1em" />,
                },
                canDelete && {
                  key: 'delete',
                  label: '删除',
                  icon: <Trash2 size="1em" />,
                  danger: true,
                  disabledReason: builtin ? '系统内置，不能删除' : undefined,
                },
              ]}
              onMore={(key) =>
                key === 'edit' ? openEditType(t) : handleDeleteType(t)
              }
            />
          </div>
        )}
      </div>
    );
  };

  const itemColumns = [
    { title: '字典项编码', dataIndex: 'itemCode', width: 140 },
    { title: '字典项名称', dataIndex: 'itemName', width: 140 },
    {
      title: '英文名称',
      dataIndex: 'itemNameEn',
      width: 140,
      render: (v?: string) => v || '—',
    },
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
      // 编辑放行内，删除收进「更多」（ui-redesign S6）
      width: rowActionsWidth(2),
      render: (_: unknown, r: DictItemRow) => (
        <RowActions
          inline={[
            canEdit && {
              key: 'edit',
              label: '编辑',
              icon: <Pencil size={18} />,
              onClick: () => openEditItem(r),
            },
          ]}
          more={[
            canDelete && {
              key: 'delete',
              label: '删除',
              icon: <Trash2 size={16} />,
              danger: true,
            },
          ]}
          onMore={() => handleDeleteItem(r)}
        />
      ),
    },
  ];
  // 右侧表格区较窄：英文名称、样式标签、排序默认收进「列」
  const itemSet = useColumnSettings('system-dict-items', itemColumns, {
    hidden: ['英文名称', '样式标签', '排序'],
  });

  return (
    <>
      <PageHeader />
      <div
        style={{
          display: 'flex',
          background: p.card,
          borderRadius: 12,
          overflow: 'hidden',
          border: `1px solid ${p.hairline}`,
          minHeight: 640,
        }}
      >
        {/* 左侧字典类型 */}
        <div
          style={{
            width: 300,
            flexShrink: 0,
            padding: 16,
            borderRight: `1px solid ${p.hairline}`,
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              marginBottom: 12,
            }}
          >
            <b style={{ fontSize: 14, color: p.ink }}>字典类型</b>
            <span style={{ fontSize: 13, color: p.mute }}>
              {typeList.length}
            </span>
            {canAdd && (
              <Tooltip title="新增字典类型">
                <Button
                  aria-label="新增字典类型"
                  icon={<Plus size="1em" />}
                  style={{ marginLeft: 'auto' }}
                  onClick={openCreateType}
                />
              </Tooltip>
            )}
          </div>
          <Input
            placeholder="搜索字典名称"
            prefix={<Search size="1em" />}
            allowClear
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ marginBottom: 12 }}
          />
          {typeList.length === 0 ? (
            <EmptyState title="暂无字典数据" />
          ) : (
            <>
              {builtinTypes.length > 0 && (
                <>
                  <div
                    style={{
                      fontSize: 12,
                      fontWeight: 600,
                      color: p.mute,
                      margin: '8px 4px',
                    }}
                  >
                    系统内置{' '}
                    <span style={{ fontWeight: 400, color: p.faint }}>
                      {builtinTypes.length}
                    </span>
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
                      color: p.mute,
                      margin: '12px 4px 8px',
                    }}
                  >
                    自定义{' '}
                    <span style={{ fontWeight: 400, color: p.faint }}>
                      {customTypes.length}
                    </span>
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
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      fontSize: 16,
                      fontWeight: 600,
                    }}
                  >
                    {selectedType.dictName}
                    {selectedType.isBuiltin === 1 && (
                      <Pill tone="gray">内置</Pill>
                    )}
                  </div>
                  <div style={{ fontSize: 12, color: p.mute }}>
                    编码 <span className="num">{selectedType.dictType}</span>
                  </div>
                </div>
                <span
                  style={{
                    display: 'inline-flex',
                    gap: 8,
                    alignItems: 'center',
                  }}
                >
                  {itemSet.settings}
                  {canAdd && (
                    <Button
                      type="primary"
                      icon={<Plus size="1em" />}
                      onClick={openCreateItem}
                    >
                      新增字典项
                    </Button>
                  )}
                </span>
              </div>
              <Table
                rowKey="id"
                columns={itemSet.columns}
                scroll={{ x: tableScrollX(itemSet.columns) }}
                dataSource={itemList}
                loading={itemLoading}
                pagination={pager}
                locale={{
                  emptyText: tableEmpty(
                    '这个字典类型还没有字典项，用「新增字典项」添加',
                    itemLoading,
                  ),
                }}
              />
            </>
          ) : (
            <EmptyState title="请在左侧选择一个字典类型" />
          )}
        </div>
      </div>

      {/* 新增/编辑字典类型 */}
      <ModalForm
        name="dictTypeEdit"
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
        modalProps={{ destroyOnHidden: true, maskClosable: false }}
      >
        {typeDrawerMode === 'edit' ? (
          <div style={{ marginBottom: 24 }}>
            <div style={{ fontSize: 13, marginBottom: 8 }}>字典类型编码</div>
            <Input
              disabled
              aria-label="字典类型编码"
              value={editingType?.dictType}
            />
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
        name="dictItemEdit"
        title={itemDrawerMode === 'edit' ? '编辑字典项' : '新增字典项'}
        width={520}
        open={itemModalOpen}
        onOpenChange={setItemModalOpen}
        layout="vertical"
        initialValues={
          itemDrawerMode === 'edit' && editingItem
            ? {
                itemName: editingItem.itemName,
                itemNameEn: editingItem.itemNameEn,
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
        modalProps={{ destroyOnHidden: true, maskClosable: false }}
      >
        <div style={{ marginBottom: 24 }}>
          <div style={{ fontSize: 13, marginBottom: 8 }}>所属字典类型</div>
          <Input
            disabled
            aria-label="所属字典类型"
            value={selectedType?.dictName}
          />
        </div>
        {itemDrawerMode === 'edit' ? (
          <div style={{ marginBottom: 24 }}>
            <div style={{ fontSize: 13, marginBottom: 8 }}>字典项编码</div>
            <Input
              disabled
              aria-label="字典项编码"
              value={editingItem?.itemCode}
            />
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
          name="itemNameEn"
          label="英文名称"
          placeholder="对外单据与文字报价使用，如 original new；可不填"
          fieldProps={{ maxLength: 64 }}
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
