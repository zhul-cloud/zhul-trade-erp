import { ProTable } from '@ant-design/pro-components';
import React from 'react';

const TenantListPage: React.FC = () => (
  <ProTable
    headerTitle="租户管理"
    rowKey="id"
    columns={[
      { title: '租户名称', dataIndex: 'name' },
      { title: '联系人', dataIndex: 'contactName' },
      { title: '联系电话', dataIndex: 'contactPhone' },
      {
        title: '状态',
        dataIndex: 'status',
        valueEnum: { 0: '禁用', 1: '启用' },
      },
    ]}
    request={async () => ({ data: [], total: 0, success: true })}
    search={false}
    toolBarRender={() => [<button key="add">新增租户</button>]}
  />
);

export default TenantListPage;
