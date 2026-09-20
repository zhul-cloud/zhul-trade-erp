import { DownloadOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { Alert, App, Button, Descriptions, Modal, Tag } from 'antd';
import dayjs from 'dayjs';
import React, { useRef, useState } from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import type { OperateLogDetail, OperateLogItem } from './service';
import {
  exportOperateLogs,
  getOperateLogDetail,
  getOperateLogList,
  getOperateModules,
} from './service';

const DATE_TIME_FMT = 'YYYY-MM-DD HH:mm:ss';
const DEFAULT_RANGE: [dayjs.Dayjs, dayjs.Dayjs] = [
  dayjs().subtract(6, 'day').startOf('day'),
  dayjs().endOf('day'),
];

function renderJsonPanel(value: unknown, emptyText: string) {
  if (value === undefined || value === null) {
    return (
      <div
        style={{
          height: 220,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--z-mute)',
          fontSize: 13,
          background: 'var(--z-inset)',
          borderRadius: 8,
        }}
      >
        {emptyText}
      </div>
    );
  }
  return (
    <pre
      style={{
        margin: 0,
        height: 220,
        overflow: 'auto',
        background: 'var(--z-inset)',
        borderRadius: 8,
        padding: '12px 14px',
        fontFamily: 'monospace',
        fontSize: 12.5,
        lineHeight: 1.6,
      }}
    >
      {JSON.stringify(value, null, 2)}
    </pre>
  );
}

const OperateLogPage: React.FC = () => {
  const { palette: p } = useAppTheme();
  const actionRef = useRef<ActionType>();
  const { message } = App.useApp();

  const [rangeWarning, setRangeWarning] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [lastParams, setLastParams] = useState<Record<string, any>>({});

  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<OperateLogDetail | null>(null);

  const openDetail = async (record: OperateLogItem) => {
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const data = await getOperateLogDetail(record.id);
      setDetail(data);
    } catch {
      message.error('加载详情失败');
      setDetailOpen(false);
    } finally {
      setDetailLoading(false);
    }
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      const result = await exportOperateLogs(lastParams);
      if (!result.ok) {
        message.error(result.message);
      }
    } finally {
      setExporting(false);
    }
  };

  const columns: ProColumns<OperateLogItem>[] = [
    { title: '序号', valueType: 'index', width: 60, search: false },
    {
      title: '操作人',
      dataIndex: 'operatorName',
      width: 110,
      fieldProps: { placeholder: '请输入操作人姓名', maxLength: 50 },
    },
    {
      title: '操作模块',
      dataIndex: 'menu',
      width: 130,
      ellipsis: true,
      valueType: 'select',
      fieldProps: { placeholder: '全部', showSearch: true },
      request: async () => {
        try {
          const modules = await getOperateModules();
          return modules.map((m) => ({ label: m, value: m }));
        } catch {
          message.error('模块列表加载失败，请刷新页面');
          return [];
        }
      },
    },
    {
      title: '操作名称',
      dataIndex: 'operation',
      width: 150,
      ellipsis: true,
      fieldProps: { placeholder: '请输入操作名称', maxLength: 50 },
    },
    {
      title: '操作结果',
      dataIndex: 'result',
      width: 100,
      align: 'center',
      valueType: 'select',
      valueEnum: {
        1: { text: '成功' },
        0: { text: '失败' },
      },
      fieldProps: { placeholder: '全部' },
      render: (_, r) => (
        <Tag color={r.result === 1 ? 'success' : 'error'}>
          {r.result === 1 ? '成功' : '失败'}
        </Tag>
      ),
    },
    {
      title: '操作时间',
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
      title: '操作时间',
      dataIndex: 'operateTime',
      width: 170,
      search: false,
      valueType: 'dateTime',
    },
    { title: 'IP地址', dataIndex: 'ip', width: 140, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 80,
      align: 'center',
      render: (_, record) => <a onClick={() => openDetail(record)}>详情</a>,
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
      <ProTable<OperateLogItem>
        headerTitle="操作日志"
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{
          pageSize: 20,
          pageSizeOptions: [20, 50, 100],
          showSizeChanger: true,
        }}
        locale={{ emptyText: '暂无操作日志数据' }}
        options={false}
        toolBarRender={() => [
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
          const start = params.startTime ? dayjs(params.startTime) : undefined;
          const end = params.endTime ? dayjs(params.endTime) : undefined;
          setRangeWarning(!!start && !!end && end.diff(start, 'day') > 90);

          const queryParams = {
            operatorName: params.operatorName,
            menu: params.menu,
            operation: params.operation,
            result: params.result,
            startTime: params.startTime,
            endTime: params.endTime,
          };
          setLastParams(queryParams);

          return getOperateLogList({
            current: params.current,
            pageSize: params.pageSize,
            ...queryParams,
          });
        }}
      />

      <Modal
        title="操作详情"
        open={detailOpen}
        onCancel={() => setDetailOpen(false)}
        width={800}
        footer={[
          <Button key="close" onClick={() => setDetailOpen(false)}>
            关闭
          </Button>,
        ]}
        destroyOnHidden
      >
        {!detailLoading && detail && (
          <>
            <Descriptions
              column={2}
              size="small"
              style={{
                background: p.inset,
                padding: 16,
                borderRadius: 10,
                marginBottom: 20,
              }}
              items={[
                { label: '操作人', children: detail.operatorName },
                {
                  label: '操作时间',
                  children: dayjs(detail.operateTime).format(DATE_TIME_FMT),
                },
                { label: '操作模块', children: detail.menu },
                { label: '操作名称', children: detail.operation },
                {
                  label: '操作结果',
                  children: (
                    <Tag color={detail.result === 1 ? 'success' : 'error'}>
                      {detail.result === 1 ? '成功' : '失败'}
                    </Tag>
                  ),
                },
                { label: 'IP地址', children: detail.ip },
              ]}
            />

            <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 12 }}>
              操作内容
            </div>

            {detail.contentEmpty && (
              <div
                style={{
                  textAlign: 'center',
                  color: p.mute,
                  padding: '32px 0',
                }}
              >
                该操作无详细内容记录
              </div>
            )}

            {detail.contentBroken && (
              <>
                <Alert
                  type="warning"
                  showIcon
                  title="内容解析异常，以下为原始数据"
                  style={{ marginBottom: 12 }}
                />
                <pre
                  style={{
                    margin: 0,
                    maxHeight: 300,
                    overflow: 'auto',
                    background: p.inset,
                    borderRadius: 8,
                    padding: '12px 14px',
                    fontFamily: 'monospace',
                    fontSize: 12.5,
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-all',
                  }}
                >
                  {detail.rawContent}
                </pre>
              </>
            )}

            {!detail.contentEmpty && !detail.contentBroken && (
              <div style={{ display: 'flex', gap: 20 }}>
                <div style={{ flex: 1 }}>
                  <div
                    style={{
                      fontSize: 12,
                      fontWeight: 600,
                      color: p.mute,
                      marginBottom: 8,
                    }}
                  >
                    操作前（Before）
                  </div>
                  {renderJsonPanel(detail.before, '新增操作，无操作前数据')}
                </div>
                <div style={{ flex: 1 }}>
                  <div
                    style={{
                      fontSize: 12,
                      fontWeight: 600,
                      color: p.mute,
                      marginBottom: 8,
                    }}
                  >
                    操作后（After）
                  </div>
                  {renderJsonPanel(detail.after, '删除操作，无操作后数据')}
                </div>
              </div>
            )}
          </>
        )}
      </Modal>
    </>
  );
};

export default OperateLogPage;
