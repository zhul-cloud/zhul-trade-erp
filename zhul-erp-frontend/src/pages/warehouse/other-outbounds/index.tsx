import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { history, Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, Form, Input, Select, Table } from 'antd';
import { Eye } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import SourceNos, { useUrlFilter } from '@/components/DocFields/SourceNos';
import EllipsisText from '@/components/EllipsisText';
import { tableEmpty } from '@/components/EmptyState';
import { FilterField, MainCard, PageHeader } from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { TagList } from '@/pages/inquiry/customer-inquiry/ModelCells';
import { Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { FilterCard, useWarehouses } from '../inventoryParts';
import {
  docHref,
  OO_DONE,
  OO_TYPE_TONE,
  OO_TYPES,
  type OtherOutbound,
  ooPath,
  otherOutboundApi,
} from '../inventoryService';
import { readBizError } from '../service';
import { OtherOutboundModal } from '../stocks/dialogs';

interface Filters {
  keyword?: string;
  outboundType?: number;
  warehouseId?: number;
  status?: number;
}

/** 仓库管理 → 其他出库（spec warehouse/inventory「其他出库」，原型 I5）：报废、转样品、货代丢失或损坏 */
const OtherOutboundsPage: React.FC = () => {
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const url = useUrlFilter();
  const warehouses = useWarehouses();
  const [form] = Form.useForm();
  const initial = (): Filters => ({ keyword: url.keyword || undefined });
  const [filters, setFilters] = useState<Filters>(initial);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<OtherOutbound[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [creating, setCreating] = useState(false);

  const queryKey = JSON.stringify({
    ...filters,
    keyword: filters.keyword?.trim() || undefined,
  });
  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const res = await otherOutboundApi.page({
        ...JSON.parse(queryKey),
        page,
        pageSize,
      });
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [queryKey, page, pageSize]);
  useEffect(() => {
    load();
  }, [load]);
  // 从其他页面返回时重新取数（audit UI-055）
  useOnReactivate(load);

  const sub = (t: React.ReactNode) => (
    <div style={{ fontSize: 12, color: palette.mute }}>{t}</div>
  );

  const columns: TableColumnsType<OtherOutbound> = [
    {
      title: '出库单号 · 出库日期',
      dataIndex: 'outboundNo',
      width: 170,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <Link to={ooPath(r.id)}>{v}</Link>
          {sub(r.outboundDate)}
        </div>
      ),
    },
    {
      title: '出库类型',
      dataIndex: 'outboundTypeName',
      width: 100,
      render: (v: string, r) => (
        <Pill tone={OO_TYPE_TONE[r.outboundType] ?? 'gray'}>{v}</Pill>
      ),
    },
    { title: '仓库', dataIndex: 'warehouseName', width: 140 },
    {
      title: '型号 · 数量',
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
          {r.modelCount > 1
            ? sub(
                <>
                  共 {r.modelCount} 个型号 · {r.totalQuantity} 件 ·{' '}
                  <Link to={ooPath(r.id)}>查看</Link>
                </>,
              )
            : sub(`${r.totalQuantity} 件`)}
        </div>
      ),
    },
    {
      title: '品牌',
      dataIndex: 'firstBrand',
      width: 110,
      render: (v?: string) => (
        <TagList values={v ? [v] : undefined} label="品牌" />
      ),
    },
    {
      title: '品类',
      dataIndex: 'firstCategory',
      width: 110,
      render: (v?: string) => (
        <TagList values={v ? [v] : undefined} label="品类" />
      ),
    },
    {
      title: '金额（¥）',
      dataIndex: 'amountCny',
      width: 110,
      render: (v: number) => formatAmount(v, 'CNY'),
    },
    {
      title: '状态',
      dataIndex: 'statusName',
      width: 90,
      render: (v: string, r) => (
        <Pill tone={r.status === OO_DONE ? 'green' : 'mute'}>{v}</Pill>
      ),
    },
    {
      title: '来源单号',
      dataIndex: 'sourceDocNo',
      width: 160,
      render: (v: string | undefined, r) => (
        <SourceNos
          refs={
            v ? [{ no: v, href: docHref(r.sourceDocType, r.sourceDocId) }] : []
          }
        />
      ),
    },
    {
      title: '原因',
      dataIndex: 'reason',
      width: 200,
      render: (v: string) => <EllipsisText text={v} />,
    },
    ...auditColumns<OtherOutbound>(),
    {
      title: '操作',
      key: 'op',
      width: rowActionsWidth(1),
      fixed: 'right',
      render: (_, r) => (
        <RowActions
          inline={[
            {
              key: 'view',
              label: '查看',
              icon: <Eye size={18} />,
              onClick: () => history.push(ooPath(r.id)),
            },
          ]}
        />
      ),
    },
  ];

  const noFilter = Object.values(JSON.parse(queryKey)).every((v) => v == null);

  return (
    <div>
      <PageHeader
        help="报废、转样品、货代丢失或损坏；退回供应商请到库存查询发起"
        extra={
          access['inventory:other-outbound:edit'] && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setCreating(true)}
            >
              新建其他出库
            </Button>
          )
        }
      />
      <FilterCard
        form={form}
        initialValues={initial()}
        onSearch={(v) => {
          setFilters(v as Filters);
          setPage(1);
        }}
        onReset={() => {
          setFilters({});
          setPage(1);
        }}
      >
        <FilterField label="关键词" span={2}>
          <Form.Item name="keyword" noStyle>
            <Input
              aria-label="关键词"
              allowClear
              prefix={<SearchOutlined />}
              onPressEnter={() => form.submit()}
              placeholder="出库单号、型号"
            />
          </Form.Item>
        </FilterField>
        <FilterField label="出库类型">
          <Form.Item name="outboundType" noStyle>
            <Select
              aria-label="出库类型"
              allowClear
              placeholder="全部"
              options={OO_TYPES.map((t) => ({
                value: t.value,
                label: t.label,
              }))}
            />
          </Form.Item>
        </FilterField>
        <FilterField label="仓库">
          <Form.Item name="warehouseId" noStyle>
            <Select
              aria-label="仓库"
              allowClear
              placeholder="全部仓库"
              options={warehouses.map((w) => ({ value: w.id, label: w.name }))}
            />
          </Form.Item>
        </FilterField>
        <FilterField label="状态">
          <Form.Item name="status" noStyle>
            <Select
              aria-label="状态"
              allowClear
              placeholder="全部"
              options={[
                { value: 1, label: '已出库' },
                { value: 2, label: '已作废' },
              ]}
            />
          </Form.Item>
        </FilterField>
      </FilterCard>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <MainCard count={total} loading={loading}>
          <Table<OtherOutbound>
            rowKey="id"
            loading={loading}
            columns={columns}
            dataSource={rows}
            scroll={{ x: tableScrollX(columns) }}
            locale={{
              emptyText: tableEmpty(
                noFilter ? '还没有其他出库单' : '没有符合条件的其他出库单',
                loading,
              ),
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
      <OtherOutboundModal
        open={creating}
        onClose={() => setCreating(false)}
        onDone={() => {
          setCreating(false);
          load();
        }}
      />
    </div>
  );
};

export default OtherOutboundsPage;
