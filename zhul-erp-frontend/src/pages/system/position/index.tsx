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
import { App, Badge, Button, Input, Select } from 'antd';
import { Ban, CircleCheck, Pencil, Search, Trash2 } from 'lucide-react';
import React, { useRef, useState } from 'react';
import { useColumnSettings } from '@/components/ColumnSettings';
import { useConfirmDanger } from '@/components/ConfirmDanger';
import { EmptyState } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
} from '@/components/ListLayout';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { proAuditColumns } from '@/components/UserName';
import { usePagination } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
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
  const pager = usePagination(20);
  const actionRef = useRef<ActionType>(undefined);
  const { message } = App.useApp();
  const access = useAccess();
  const [modalOpen, setModalOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<PositionItem | undefined>();
  /** 搜索条件：状态切换后立即查询，岗位名称点「查询」才生效 */
  const [filters, setFilters] = useState<{ name?: string; status?: number }>(
    {},
  );
  const [keyword, setKeyword] = useState('');
  // 首次查询返回前没有总数，主卡不显示「共 0 条」
  const [total, setTotal] = useState<number>();
  const search = () =>
    setFilters({ ...filters, name: keyword.trim() || undefined });

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
    confirmDanger({
      title: `删除岗位「${record.name}」（编码：${record.code}）？`,
      content: '此操作不可撤销。',
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

  const confirmDanger = useConfirmDanger();
  const checking = useRef(false);
  const columns: ProColumns<PositionItem>[] = [
    {
      fixed: 'left',
      title: '岗位编码',
      dataIndex: 'code',
      width: 140,
      search: false,
    },
    { title: '岗位名称', dataIndex: 'name', width: 180, search: false },
    { title: '排序', dataIndex: 'sort', width: 80, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      search: false,
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
    ...proAuditColumns<PositionItem>(),
    {
      title: '操作',
      fixed: 'right',
      valueType: 'option',
      // 编辑放行内；启用/禁用、删除收进「更多」（ui-redesign S6）
      width: rowActionsWidth(2),
      render: (_, record) => {
        const enabled = record.status === 1;
        return (
          <RowActions
            inline={[
              canEdit && {
                key: 'edit',
                label: '编辑',
                icon: <Pencil size={18} />,
                onClick: () => {
                  setEditRecord(record);
                  setModalOpen(true);
                },
              },
            ]}
            more={[
              canEdit && {
                key: 'status',
                label: enabled ? '禁用' : '启用',
                icon: enabled ? <Ban size={16} /> : <CircleCheck size={16} />,
                danger: enabled,
              },
              canDelete && {
                key: 'delete',
                label: '删除',
                icon: <Trash2 size={16} />,
                danger: true,
              },
            ]}
            onMore={async (key) => {
              if (key === 'status') {
                if (!enabled) {
                  handleToggleStatus(record);
                  return;
                }
                // 禁用前查有多少用户持有此岗位，写进确认框；查询失败时按没有用户的说明继续
                if (checking.current) return;
                checking.current = true;
                const userCount = await getPositionDeleteCheck(record.id)
                  .then((c) => c.userCount)
                  .catch(() => 0)
                  .finally(() => {
                    checking.current = false;
                  });
                confirmDanger({
                  title: `禁用岗位「${record.name}」？`,
                  content:
                    userCount > 0
                      ? `当前有 ${userCount} 名用户持有此岗位，禁用后新建用户将无法选择此岗位，已分配用户不受影响。`
                      : '禁用后该岗位不可被分配给新用户。',
                  okText: '禁用',
                  onOk: () => handleToggleStatus(record),
                });
              }
              if (key === 'delete') handleDeleteClick(record);
            }}
          />
        );
      },
    },
  ];
  const columnsSet = useColumnSettings('system-position', columns);

  return (
    <>
      <PageHeader
        extra={
          !!(access as Record<string, unknown>)['system:position:add'] && (
            <Button
              type="primary"
              onClick={() => {
                setEditRecord(undefined);
                setModalOpen(true);
              }}
            >
              新增岗位
            </Button>
          )
        }
      />
      <FilterCard
        onSearch={search}
        onReset={() => {
          setKeyword('');
          setFilters({});
          // 条件清空时 ProTable 不会自动回到第 1 页
          actionRef.current?.setPageInfo?.({ current: 1 });
        }}
      >
        <FilterField label="岗位名称" span={2}>
          <Input
            allowClear
            prefix={<Search size={16} />}
            placeholder="岗位名称"
            aria-label="岗位名称"
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
              { value: 1, label: '启用' },
              { value: 0, label: '禁用' },
            ]}
          />
        </FilterField>
      </FilterCard>
      <MainCard settings={columnsSet.settings} count={total}>
        <ProTable<PositionItem>
          scroll={{ x: tableScrollX(columnsSet.columns) }}
          actionRef={actionRef}
          rowKey="id"
          columns={columnsSet.columns}
          params={filters}
          request={async (params) => {
            const res = await getPositionList(params);
            setTotal(res.total);
            return res;
          }}
          pagination={pager}
          search={false}
          options={false}
          toolBarRender={false}
          locale={{
            // 首次为空给下一步，带条件为空给「清空筛选」（UI-117）
            emptyText:
              filters.name || filters.status !== undefined ? (
                <EmptyState kind="noResult" title="没有符合条件的岗位" />
              ) : (
                <EmptyState
                  title="还没有岗位"
                  description="点右上角「新增岗位」"
                />
              ),
          }}
        />
      </MainCard>
      <ModalForm
        name="positionEdit"
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
        modalProps={{ destroyOnHidden: true, maskClosable: false }}
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
