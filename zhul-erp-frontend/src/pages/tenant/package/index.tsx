import { ProTable } from '@ant-design/pro-components';
import React from 'react';

const TenantPackagePage: React.FC = () => (
  <ProTable
    headerTitle="套餐管理"
    rowKey="id"
    columns={[
      { title: '套餐名称', dataIndex: 'name' },
      { title: '套餐编码', dataIndex: 'code' },
      { title: '备注', dataIndex: 'remark', ellipsis: true },
      {
        title: '状态',
        dataIndex: 'status',
        valueEnum: { 0: '禁用', 1: '启用' },
      },
    ]}
    request={async () => ({ data: [], total: 0, success: true })}
    search={false}
    toolBarRender={() => [<button key="add">新增套餐</button>]}
  />
);

export default TenantPackagePage;
