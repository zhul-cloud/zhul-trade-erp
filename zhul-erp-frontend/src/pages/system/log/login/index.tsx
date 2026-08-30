import { DownloadOutlined, LogoutOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { useAccess } from '@umijs/max';
import { Alert, App, Button, Tag, Tooltip } from 'antd';
import dayjs from 'dayjs';
import React, { useRef, useState } from 'react';
import type { LoginLogItem } from './service';
import { exportLoginLogs, forceLogout, getLoginLogList } from './service';

const DATE_TIME_FMT = 'YYYY-MM-DD HH:mm:ss';
const DEFAULT_RANGE: [dayjs.Dayjs, dayjs.Dayjs] = [
  dayjs().subtract(6, 'day').startOf('day'),
  dayjs().endOf('day'),
];

const LoginLogPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const { message, modal } = App.useApp();
  const access = useAccess();

  const canForceLogout = !!(access as Record<string, unknown>)[
    'system:log:login:forceLogout'
  ];

  const [rangeWarning, setRangeWarning] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [lastParams, setLastParams] = useState<Record<string, any>>({});

  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [selectedRows, setSelectedRows] = useState<LoginLogItem[]>([]);
  const [forceLogoutLoading, setForceLogoutLoading] = useState(false);

  const handleExport = async () => {
    setExporting(true);
    try {
      const result = await exportLoginLogs(lastParams);
      if (!result.ok) {
        message.error(result.message);
      }
    } finally {
      setExporting(false);
    }
  };

  const handleForceLogout = () => {
    const count = selectedRows.length;
    modal.confirm({
      title: '确认强制下线',
      content: `确定将选中的 ${count} 名用户强制下线？其当前登录 Token 将立即失效，操作不可撤销。`,
      okText: '确认',
      okButtonProps: { danger: true, loading: forceLogoutLoading },
      onOk: async () => {
        setForceLogoutLoading(true);
        try {
          const tokenIds = selectedRows
            .map((r) => r.tokenId)
            .filter((t): t is string => !!t);
          const result = await forceLogout(tokenIds);
          if (result.failCount === 0) {
            message.success(`已成功强制下线 ${result.successCount} 名用户`);
          } else if (result.successCount > 0) {
            message.warning(
              `${result.successCount} 名下线成功，${result.failCount} 名下线失败，请重试`,
            );
          } else {
            message.error('强制下线操作失败，请稍后重试');
          }
          setSelectedRowKeys([]);
          setSelectedRows([]);
          actionRef.current?.reload();
        } catch (e: any) {
          message.error(e?.message || '强制下线操作失败，请稍后重试');
        } finally {
          setForceLogoutLoading(false);
        }
      },
    });
  };

  const columns: ProColumns<LoginLogItem>[] = [
    { title: '序号', valueType: 'index', width: 60, search: false },
    {
      title: '用户名',
      dataIndex: 'operatorName',
      width: 110,
      fieldProps: { placeholder: '请输入用户名', maxLength: 50 },
    },
    {
      title: '登录结果',
      dataIndex: 'result',
      width: 110,
      align: 'center',
      valueType: 'select',
      valueEnum: { 1: { text: '成功' }, 0: { text: '失败' } },
      fieldProps: { placeholder: '全部' },
      render: (_, r) => (
        <span>
          <Tag color={r.result === 1 ? 'success' : 'error'}>
            {r.result === 1 ? '成功' : '失败'}
          </Tag>
          {r.online && (
            <span style={{ color: '#1677FF', fontSize: 12, marginLeft: 4 }}>
              ● 在线
            </span>
          )}
        </span>
      ),
    },
    {
      title: 'IP地址',
      dataIndex: 'ip',
      hideInTable: true,
      fieldProps: {
        placeholder: '请输入IP地址，支持网段如192.168',
        maxLength: 64,
      },
    },
    {
      title: '登录时间',
      dataIndex: 'operateTimeRange',
      valueType: 'dateTimeRange',
      hideInTable: true,
      initialValue: DEFAULT_RANGE,
      fieldProps: {
        showTime: { format: 'HH:mm:ss' },
        format: DATE_TIME_FMT,
      },
      search: {
        transform: (value: [string, string]) => ({
          startTime: value?.[0],
          endTime: value?.[1],
        }),
      },
    },
    {
      title: '登录IP',
      dataIndex: 'ip',
      width: 140,
      search: false,
    },
    {
      title: '登录地点',
      dataIndex: 'location',
      width: 150,
      ellipsis: true,
      search: false,
      render: (_, r) => (
        <span style={{ color: r.location === '未知' ? '#94A3B8' : undefined }}>
          {r.location}
        </span>
      ),
    },
    {
      title: '浏览器',
      dataIndex: 'browser',
      width: 150,
      ellipsis: true,
      search: false,
    },
    {
      title: '操作系统',
      dataIndex: 'os',
      width: 130,
      search: false,
    },
    {
      title: '登录时间',
      dataIndex: 'operateTime',
      width: 170,
      search: false,
      valueType: 'dateTime',
    },
  ];

  return (
    <>
      {rangeWarning && (
        <Alert
          type="warning"
          showIcon
          closable
          title="查询范围较大（＞90天），可能影响响应速度"
          style={{ marginBottom: 16 }}
        />
      )}
      <ProTable<LoginLogItem>
        headerTitle="登录日志"
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{
          pageSize: 20,
          pageSizeOptions: [20, 50, 100],
          showSizeChanger: true,
        }}
        locale={{ emptyText: '暂无登录日志数据' }}
        options={false}
        rowSelection={
          canForceLogout
            ? {
                selectedRowKeys,
                onChange: (keys, rows) => {
                  setSelectedRowKeys(keys);
                  setSelectedRows(rows);
                },
                getCheckboxProps: (record) => ({
                  disabled: !record.checkable,
                }),
              }
            : undefined
        }
        toolBarRender={() => [
          canForceLogout && (
            <Tooltip
              key="force-logout-tip"
              title={
                selectedRowKeys.length === 0 ? '请先勾选在线用户' : undefined
              }
            >
              <Button
                danger={selectedRowKeys.length > 0}
                disabled={selectedRowKeys.length === 0}
                icon={<LogoutOutlined />}
                onClick={handleForceLogout}
              >
                强制下线
                {selectedRowKeys.length > 0
                  ? `（${selectedRowKeys.length}）`
                  : ''}
              </Button>
            </Tooltip>
          ),
          <Button
            key="export"
            icon={<DownloadOutlined />}
            loading={exporting}
            onClick={handleExport}
          >
            导出 Excel
          </Button>,
        ]}
        request={async (params) => {
          setSelectedRowKeys([]);
          setSelectedRows([]);

          const start = params.startTime ? dayjs(params.startTime) : undefined;
          const end = params.endTime ? dayjs(params.endTime) : undefined;
          setRangeWarning(!!start && !!end && end.diff(start, 'day') > 90);

          const queryParams = {
            operatorName: params.operatorName,
            ip: params.ip,
            result: params.result,
            startTime: params.startTime,
            endTime: params.endTime,
          };
          setLastParams(queryParams);

          return getLoginLogList({
            current: params.current,
            pageSize: params.pageSize,
            ...queryParams,
          });
        }}
      />
    </>
  );
};

export default LoginLogPage;
