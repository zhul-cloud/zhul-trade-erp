import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import {
  Alert,
  App,
  Button,
  DatePicker,
  Descriptions,
  Input,
  Modal,
  Select,
  Tag,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Download, Eye, Search } from 'lucide-react';
import React, { useEffect, useRef, useState } from 'react';
import { ellipsisRender } from '@/components/EllipsisText';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
} from '@/components/ListLayout';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { useAppTheme } from '@/theme/AppTheme';
import { usePagination } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import type { OperateLogDetail, OperateLogItem } from './service';
import {
  exportOperateLogs,
  getOperateLogDetail,
  getOperateLogList,
  getOperateModules,
} from './service';

const DATE_TIME_FMT = 'YYYY-MM-DD HH:mm:ss';
/** 默认最近 7 天（含今天） */
const defaultRange = (): [Dayjs, Dayjs] => [
  dayjs().subtract(6, 'day').startOf('day'),
  dayjs().endOf('day'),
];

interface Filters {
  operatorName?: string;
  menu?: string;
  operation?: string;
  result?: 0 | 1;
  range: [Dayjs, Dayjs] | null;
}

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
  const pager = usePagination(20);
  /** 搜索条件：操作结果切换后立即查询，其余点「查询」才生效 */
  const [filters, setFilters] = useState<Filters>(() => ({
    range: defaultRange(),
  }));
  const [draft, setDraft] = useState<Filters>(filters);
  // 首次查询返回前没有总数，主卡不显示「共 0 条」
  const [total, setTotal] = useState<number>();
  const [modules, setModules] = useState<string[]>([]);
  const search = () =>
    setFilters({
      ...draft,
      operatorName: draft.operatorName?.trim() || undefined,
      operation: draft.operation?.trim() || undefined,
      result: filters.result,
    });
  const { palette: p } = useAppTheme();
  const actionRef = useRef<ActionType>(undefined);
  const { message } = App.useApp();

  const [rangeWarning, setRangeWarning] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [lastParams, setLastParams] = useState<Record<string, any>>({});

  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<OperateLogDetail | null>(null);

  useEffect(() => {
    getOperateModules()
      .then(setModules)
      .catch(() => message.error('模块列表加载失败，请刷新页面'));
  }, [message]);

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
    {
      title: '操作人',
      dataIndex: 'operatorName',
      width: 160,
      search: false,
      render: ellipsisRender,
    },
    {
      title: '操作模块',
      dataIndex: 'menu',
      width: 130,
      ellipsis: true,
      search: false,
    },
    {
      title: '操作名称',
      dataIndex: 'operation',
      width: 150,
      ellipsis: true,
      search: false,
    },
    {
      title: '操作结果',
      dataIndex: 'result',
      width: 100,
      align: 'center',
      search: false,
      render: (_, r) => (
        <Tag color={r.result === 1 ? 'success' : 'error'}>
          {r.result === 1 ? '成功' : '失败'}
        </Tag>
      ),
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
      fixed: 'right',
      valueType: 'option',
      width: rowActionsWidth(1),
      render: (_, record) => (
        <RowActions
          inline={[
            {
              key: 'view',
              label: '详情',
              icon: <Eye size={18} />,
              onClick: () => openDetail(record),
            },
          ]}
        />
      ),
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
      <PageHeader />
      <FilterCard
        onSearch={search}
        onReset={() => {
          const d = { range: defaultRange() };
          setDraft(d);
          setFilters(d);
        }}
        moreCount={[filters.menu, filters.operation].filter(Boolean).length}
        more={
          <>
            <FilterField label="操作模块">
              <Select
                allowClear
                showSearch
                placeholder="全部"
                aria-label="操作模块"
                value={draft.menu}
                onChange={(v) => setDraft({ ...draft, menu: v })}
                options={modules.map((m) => ({ label: m, value: m }))}
              />
            </FilterField>
            <FilterField label="操作名称">
              <Input
                allowClear
                placeholder="操作名称"
                aria-label="操作名称"
                maxLength={50}
                value={draft.operation}
                onChange={(e) =>
                  setDraft({ ...draft, operation: e.target.value })
                }
                onPressEnter={search}
              />
            </FilterField>
          </>
        }
      >
        <FilterField label="操作人" span={2}>
          <Input
            allowClear
            prefix={<Search size={16} />}
            placeholder="操作人姓名"
            aria-label="操作人"
            maxLength={50}
            value={draft.operatorName}
            onChange={(e) =>
              setDraft({ ...draft, operatorName: e.target.value })
            }
            onPressEnter={search}
          />
        </FilterField>
        <FilterField label="操作结果">
          <Select<0 | 1>
            allowClear
            placeholder="全部"
            aria-label="操作结果"
            value={filters.result}
            // 和状态一样，切换后立即查询
            onChange={(v) => {
              setFilters({ ...filters, result: v });
              setDraft({ ...draft, result: v });
            }}
            options={[
              { value: 1, label: '成功' },
              { value: 0, label: '失败' },
            ]}
          />
        </FilterField>
        <FilterField label="操作时间" span={2}>
          <DatePicker.RangePicker
            showTime={{ format: 'HH:mm:ss' }}
            format={DATE_TIME_FMT}
            style={{ width: '100%' }}
            aria-label="操作时间"
            value={draft.range}
            onChange={(v) =>
              setDraft({
                ...draft,
                range: v?.[0] && v[1] ? [v[0], v[1]] : null,
              })
            }
          />
        </FilterField>
      </FilterCard>
      <MainCard
        count={total}
        right={
          <Button
            type="text"
            icon={<Download size="1em" />}
            style={{ color: p.link }}
            loading={exporting}
            onClick={handleExport}
          >
            导出 Excel
          </Button>
        }
      >
        <ProTable<OperateLogItem>
          scroll={{ x: tableScrollX(columns) }}
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          params={filters}
          search={false}
          toolBarRender={false}
          pagination={{ ...pager, pageSizeOptions: [20, 50, 100] }}
          locale={{ emptyText: tableEmpty('暂无操作日志数据') }}
          options={false}
          request={async (params) => {
            const [start, end] = filters.range ?? [];
            setRangeWarning(!!start && !!end && end.diff(start, 'day') > 90);

            const queryParams = {
              operatorName: filters.operatorName,
              menu: filters.menu,
              operation: filters.operation,
              result: filters.result,
              startTime: start?.format(DATE_TIME_FMT),
              endTime: end?.format(DATE_TIME_FMT),
            };
            setLastParams(queryParams);

            const res = await getOperateLogList({
              current: params.current,
              pageSize: params.pageSize,
              ...queryParams,
            });
            setTotal(res.total ?? 0);
            return res;
          }}
        />
      </MainCard>

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
