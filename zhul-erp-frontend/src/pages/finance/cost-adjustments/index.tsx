import { DownloadOutlined } from '@ant-design/icons';
import { Link } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, DatePicker, Form, Input, Select, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Search } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import SourceNos from '@/components/DocFields/SourceNos';
import {
  FilterField,
  MainCard,
  PageHeader,
  QuickFilters,
  SummaryBar,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { FilterCard } from '@/pages/warehouse/inventoryParts';
import { docHref } from '@/pages/warehouse/inventoryService';
import { readBizError } from '@/pages/warehouse/service';
import { useAppTheme } from '@/theme/AppTheme';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { ReasonPill, SignedMoney } from '../costingParts';
import {
  CA_REASONS,
  type CostAdjust,
  type CostAdjustSummary,
  caPath,
  costAdjustApi,
} from '../costingService';

interface Filters {
  keyword?: string;
  month?: string;
  reason?: number;
  cogsOnly?: boolean;
}

const thisMonth = () => dayjs().format('YYYY-MM');

/** 帮助文字：help-texts.md「成本调整」 */
export const HELP = {
  page: '批次成本变化时系统自动生成，不能手工新建或修改',
  keyword: '可搜：调整单号、来源单号、型号、批次号',
  stock: '进库存金额',
} as const;

/** 财务管理 → 成本调整（spec finance/inventory-cost「成本调整页」，原型 C1）：批次成本变化时系统生成，只读 */
const CostAdjustmentsPage: React.FC = () => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [filters, setFilters] = useState<Filters>({ month: thisMonth() });
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<CostAdjust[]>([]);
  const [total, setTotal] = useState(0);
  const [summary, setSummary] = useState<CostAdjustSummary>();
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState<string>();

  const queryKey = JSON.stringify({
    ...filters,
    keyword: filters.keyword?.trim() || undefined,
    cogsOnly: filters.cogsOnly || undefined,
  });
  const summaryMonth = filters.month ?? thisMonth();
  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const [res, sum] = await Promise.all([
        costAdjustApi.page({ ...JSON.parse(queryKey), page, pageSize }),
        costAdjustApi.summary(summaryMonth),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setSummary(sum);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [queryKey, summaryMonth, page, pageSize]);
  useEffect(() => {
    load();
  }, [load]);
  // 从其他页面返回时重新取数（audit UI-055）
  useOnReactivate(load);

  const sub = (t: React.ReactNode) => (
    <div style={{ fontSize: 12, color: palette.mute }}>{t}</div>
  );
  const monthLabel = summaryMonth === thisMonth() ? '本月' : summaryMonth;

  const columns: TableColumnsType<CostAdjust> = [
    {
      title: '调整单号 · 调整日期',
      dataIndex: 'adjustNo',
      width: 170,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <Link to={caPath(r.id)}>{v}</Link>
          {sub(r.adjustDate)}
        </div>
      ),
    },
    { title: '所属月份', dataIndex: 'periodMonth', width: 90 },
    {
      title: '触发原因',
      dataIndex: 'reasonName',
      width: 120,
      render: (v: string, r) => <ReasonPill reason={r.reason} name={v} />,
    },
    {
      title: '来源单号',
      dataIndex: 'sourceDocNo',
      width: 170,
      render: (v: string | undefined, r) => (
        <SourceNos
          refs={
            v ? [{ no: v, href: docHref(r.sourceDocType, r.sourceDocId) }] : []
          }
        />
      ),
    },
    {
      title: '型号 · 批次',
      dataIndex: 'firstModel',
      width: 200,
      render: (v: string, r) => (
        <div>
          <b
            style={{
              color: palette.ink,
              fontFamily: 'ui-monospace, Menlo, monospace',
            }}
          >
            {v || '—'}
          </b>
          {r.batchCount > 1
            ? sub(
                <>
                  共 {r.batchCount} 个批次 · <Link to={caPath(r.id)}>查看</Link>
                </>,
              )
            : sub('1 个批次')}
        </div>
      ),
    },
    {
      title: '在库调整',
      dataIndex: 'stockAmount',
      width: 110,
      render: (v: number) => <SignedMoney value={v} />,
    },
    {
      title: '销售成本调整',
      dataIndex: 'cogsAmount',
      width: 120,
      render: (v: number) => <SignedMoney value={v} />,
    },
    {
      title: '其他出库调整',
      dataIndex: 'otherAmount',
      width: 120,
      render: (v: number) => <SignedMoney value={v} />,
    },
    ...auditColumns<CostAdjust>(),
    {
      title: '操作',
      key: 'op',
      width: 70,
      fixed: 'right',
      render: (_, r) => <Link to={caPath(r.id)}>查看</Link>,
    },
  ];

  const parsed = JSON.parse(queryKey) as Filters;
  const noFilter = !parsed.keyword && !parsed.reason && !parsed.cogsOnly;
  const reasonText = Object.entries(summary?.countByReason ?? {})
    .map(([k, n]) => `${k} ${n}`)
    .join(' · ');

  const doExport = async () => {
    setExporting(true);
    try {
      await costAdjustApi.export(parsed);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setExporting(false);
    }
  };

  return (
    <div>
      <PageHeader help={HELP.page} />
      <SummaryBar
        items={[
          {
            key: 'stock',
            label: `${monthLabel}在库调整`,
            help: HELP.stock,
            value: <SignedMoney value={summary?.stockAmount} yuan />,
          },
          {
            key: 'count',
            label: `${monthLabel}调整单`,
            value: `${summary?.count ?? 0} 张`,
            sub: reasonText || '没有调整',
          },
        ]}
      />
      <FilterCard
        form={form}
        initialValues={{ month: dayjs() }}
        onSearch={(v) => {
          const month = v.month as Dayjs | undefined;
          setFilters((f) => ({
            keyword: v.keyword as string | undefined,
            reason: v.reason as number | undefined,
            month: month ? month.format('YYYY-MM') : undefined,
            cogsOnly: f.cogsOnly,
          }));
          setPage(1);
        }}
        onReset={() => {
          form.setFieldsValue({ month: dayjs() });
          setFilters({ month: thisMonth() });
          setPage(1);
        }}
        extra={
          // 原「销售成本调整」卡片是条件类，改为快捷筛选，数字仍按所选月份统计
          <QuickFilters
            options={[
              {
                value: 'cogs',
                label: (
                  <>
                    {`只看有销售成本调整 · ${monthLabel} `}
                    <SignedMoney value={summary?.cogsAmount} yuan />
                  </>
                ),
              },
            ]}
            value={filters.cogsOnly ? ['cogs'] : []}
            onChange={(v) => {
              setFilters((f) => ({ ...f, cogsOnly: v.length > 0 }));
              setPage(1);
            }}
          />
        }
      >
        <FilterField label="关键词" span={2} help={HELP.keyword}>
          <Form.Item name="keyword" noStyle>
            <Input
              allowClear
              prefix={<Search size={16} />}
              placeholder="调整单号、来源单号、型号"
              aria-label="关键词"
              onPressEnter={() => form.submit()}
            />
          </Form.Item>
        </FilterField>
        <FilterField label="所属月份" htmlFor="ca-month">
          <Form.Item name="month" noStyle>
            <DatePicker
              id="ca-month"
              picker="month"
              placeholder="全部月份"
              style={{ width: '100%' }}
            />
          </Form.Item>
        </FilterField>
        <FilterField label="触发原因">
          <Form.Item name="reason" noStyle>
            <Select
              allowClear
              placeholder="全部"
              aria-label="触发原因"
              options={CA_REASONS.map((r) => ({
                value: r.value,
                label: r.label,
              }))}
            />
          </Form.Item>
        </FilterField>
      </FilterCard>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <MainCard
          left={`共 ${total} 条`}
          right={
            <Button
              type="text"
              icon={<DownloadOutlined />}
              style={{ color: palette.link }}
              loading={exporting}
              disabled={total === 0}
              onClick={doExport}
            >
              导出
            </Button>
          }
        >
          <Table<CostAdjust>
            rowKey="id"
            size="middle"
            loading={loading}
            columns={columns}
            dataSource={rows}
            scroll={{ x: tableScrollX(columns) }}
            locale={{
              emptyText: noFilter
                ? `${parsed.month ?? ''} 没有成本调整`
                : '没有符合条件的成本调整',
            }}
            pagination={{
              ...PAGER_STYLE,
              current: page,
              pageSize,
              total,
              onChange: (p, s) => {
                setPage(p);
                setPageSize(s);
              },
            }}
          />
        </MainCard>
      )}
    </div>
  );
};

export default CostAdjustmentsPage;
