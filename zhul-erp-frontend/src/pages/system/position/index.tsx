import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  ModalForm,
  ProFormDigit,
  ProFormRadio,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { App, Badge, Button, Input, Popconfirm } from 'antd';
import React, { useRef, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import type { PositionItem } from './service';
import {
  createPosition,
  deletePosition,
  getPositionDeleteCheck,
  getPositionList,
  updatePosition,
  updatePositionStatus,
} from './service';

// 输入时不区分大小写，提交前统一转为大写（与后端校验规则保持一致）
const CODE_PATTERN = /^[A-Za-z0-9_]{2,32}$/;

const PositionPage: React.FC = () => {
  const { palette: p } = useAppTheme();
  const actionRef = useRef<ActionType>();
  const { message, modal } = App.useApp();
  const access = useAccess();
  const [modalOpen, setModalOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<PositionItem | undefined>();
  const [disableConfirmText, setDisableConfirmText] = useState<
    Record<number, string>
  >({});

  const canEdit = !!(access as Record<string, unknown>)['system:position:edit'];
  const canDelete = !!(access as Record<string, unknown>)[
    'system:position:delete'
  ];

  const handleDeleteClick = async (record: PositionItem) => {
    const check = await getPositionDeleteCheck(record.id);
    if (check.blocked) {
      message.error(
        `该岗位已分配 ${check.userCount} 名用户，请先解除关联后再删除`,
      );
      return;
    }
    modal.confirm({
      title: '确认删除',
      content: `确认删除岗位「${record.name}」（编码：${record.code}）？此操作不可撤销。`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deletePosition(record.id);
          message.success('删除成功');
          actionRef.current?.reload();
        } catch {
          message.error('删除失败');
        }
      },
    });
  };

  const loadDisableWarning = async (record: PositionItem) => {
    if (record.status !== 1) return;
    const check = await getPositionDeleteCheck(record.id);
    if (check.userCount > 0) {
      setDisableConfirmText((prev) => ({
        ...prev,
        [record.id]: `当前有 ${check.userCount} 名用户持有此岗位，禁用后新建用户将无法选择此岗位，已分配用户不受影响。`,
      }));
    }
  };

  const handleToggleStatus = async (record: PositionItem) => {
    const newStatus = record.status === 1 ? 0 : 1;
    try {
      await updatePositionStatus(record.id, newStatus);
      message.success(newStatus === 1 ? '已启用' : '已禁用');
      actionRef.current?.reload();
    } catch {
      message.error('操作失败');
    }
  };

  const handleFinish = async (
    values: Record<string, unknown>,
  ): Promise<boolean> => {
    const payload = {
      code: editRecord
        ? editRecord.code
        : (values.code as string)?.toUpperCase(),
      name: values.name as string,
      sort: (values.sort as number) ?? 0,
      status: (values.status as number) ?? 1,
      remark: values.remark as string,
    };
    try {
      if (editRecord) {
        await updatePosition(editRecord.id, payload);
        message.success('编辑成功');
      } else {
        await createPosition(payload);
        message.success('新增成功');
      }
      setModalOpen(false);
      setEditRecord(undefined);
      actionRef.current?.reload();
      return true;
    } catch {
      message.error(editRecord ? '编辑失败' : '新增失败');
      return false;
    }
  };

  const columns: ProColumns<PositionItem>[] = [
    { title: '序号', valueType: 'index', width: 60, search: false },
    { title: '岗位编码', dataIndex: 'code', width: 140, search: false },
    { title: '岗位名称', dataIndex: 'name', width: 180 },
    { title: '排序', dataIndex: 'sort', width: 80, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) =>
        record.status === 1 ? (
          <Badge status="success" text="启用" />
        ) : (
          <Badge status="default" text="禁用" />
        ),
      valueEnum: {
        0: { text: '禁用', status: 'Default' },
        1: { text: '启用', status: 'Success' },
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 160,
      search: false,
      valueType: 'dateTime',
    },
    {
      title: '创建人',
      dataIndex: 'createBy',
      width: 90,
      search: false,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 160,
      search: false,
      valueType: 'dateTime',
    },
    {
      title: '更新人',
      dataIndex: 'updateBy',
      width: 90,
      search: false,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 160,
      render: (_, record) => [
        canEdit && (
          <a
            key="edit"
            onClick={() => {
              setEditRecord(record);
              setModalOpen(true);
            }}
          >
            编辑
          </a>
        ),
        canEdit && (
          <Popconfirm
            key="toggle"
            title={
              record.status === 1
                ? `确认禁用岗位「${record.name}」？`
                : `确认启用岗位「${record.name}」？`
            }
            description={
              record.status === 1
                ? (disableConfirmText[record.id] ??
                  '禁用后该岗位不可被分配给新用户。')
                : undefined
            }
            onOpenChange={(open) => open && loadDisableWarning(record)}
            onConfirm={() => handleToggleStatus(record)}
          >
            <a style={{ color: record.status === 1 ? p.orange : p.green }}>
              {record.status === 1 ? '禁用' : '启用'}
            </a>
          </Popconfirm>
        ),
        canDelete && (
          <a
            key="delete"
            style={{ color: p.red }}
            onClick={() => handleDeleteClick(record)}
          >
            删除
          </a>
        ),
      ],
    },
  ];

  return (
    <>
      <ProTable<PositionItem>
        headerTitle="岗位列表"
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={getPositionList}
        pagination={{ pageSize: 10 }}
        search={{ labelWidth: 'auto' }}
        toolBarRender={() => [
          !!(access as Record<string, unknown>)['system:position:add'] && (
            <Button
              key="add"
              type="primary"
              onClick={() => {
                setEditRecord(undefined);
                setModalOpen(true);
              }}
            >
              新增岗位
            </Button>
          ),
        ]}
      />
      <ModalForm
        title={editRecord ? '编辑岗位' : '新增岗位'}
        width={480}
        open={modalOpen}
        onOpenChange={(open) => {
          setModalOpen(open);
          if (!open) setEditRecord(undefined);
        }}
        layout="vertical"
        initialValues={editRecord ?? { sort: 0, status: 1 }}
        onFinish={handleFinish}
        modalProps={{ destroyOnClose: true, maskClosable: false }}
      >
        {editRecord ? (
          <div style={{ marginBottom: 24 }}>
            <div style={{ fontSize: 13, marginBottom: 8 }}>岗位编码</div>
            <Input disabled value={editRecord.code} />
          </div>
        ) : (
          <ProFormText
            name="code"
            label="岗位编码"
            placeholder="大写字母、数字、下划线，2~32字符"
            fieldProps={{
              maxLength: 32,
              style: { textTransform: 'uppercase' },
            }}
            rules={[
              { required: true, message: '请输入岗位编码' },
              {
                pattern: CODE_PATTERN,
                message: '编码仅支持大写字母、数字和下划线，长度2~32位',
              },
            ]}
          />
        )}

        <ProFormText
          name="name"
          label="岗位名称"
          placeholder="请输入岗位名称，1~64字符"
          fieldProps={{ maxLength: 64 }}
          rules={[
            {
              required: true,
              min: 1,
              max: 64,
              message: '请输入1~64字符的岗位名称',
            },
          ]}
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
          placeholder="填写岗位说明（选填，最长256字符）"
          fieldProps={{ rows: 3, maxLength: 256, showCount: true }}
        />
      </ModalForm>
    </>
  );
};

export default PositionPage;
