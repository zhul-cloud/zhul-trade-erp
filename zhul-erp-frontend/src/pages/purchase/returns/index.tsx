import { history, Link } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Form, Input, Select, Table } from 'antd';
import { Eye, Search } from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { ModelQtyCell } from '@/components/DocFields/DocPage';
import SourceNos, { useUrlFilter } from '@/components/DocFields/SourceNos';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { IconButton } from '@/components/Shell';
import { TagList } from '@/pages/inquiry/customer-inquiry/ModelCells';
import { Pill } from '@/pages/inquiry/shared/components';
import { PR_STATUS_TONE } from '@/pages/sales/after-sales/service';
import { PATHS as SALES_PATHS } from '@/pages/sales/components';
import { useUserOptions } from '@/pages/sales/orders/dialogs';
import { readBizError } from '@/pages/sales/service';
import { searchSuppliers } from '@/services/zhul/masterdata';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { auditColumns, PATHS } from '../components';
import {
  PR_SOURCE_OPTIONS,
  PR_STATUS,
  type PurchaseReturn,
  prSourceRef,
  purchaseReturnApi,
} from './service';

type Filters = {
  keyword?: string;
  status?: number;
  supplierId?: number;
  sourceType?: number;
  ownerId?: number;
};

/** 帮助文字：help-texts.md「采购退货列表」，关键词范围以 search-scope.md 为准 */
export const HELP = {
  page: '退给供应商的货（售后退货、到货不良退货、多发退回）：寄出、供应商签收后登记退款或抵扣',
  keyword:
    '可搜：退货单号、快递单号、型号、退货入库单号、采购入库单号；采购订单编号、店铺、供应商、采购订单型号',
} as const;

/** 下拉选项后面带数量（stat-cards.md：状态并入下拉） */
const withCount = (label: string, n?: number) =>
  n ? `${label} · ${n}` : label;

/** 采购管理 → 采购退货（spec purchase/purchase-return） */
const PurchaseReturnsPage: React.FC = () => {
  const { message } = App.useApp();
  const { palette } = useAppTheme();
  const url = useUrlFilter();
  const users = useUserOptions();
  const [form] = Form.useForm<Filters>();
  // 默认看待寄出；从别的单据带单号跳来时看全部
  const [defaults] = useState<Filters>(() =>
    url.keyword ? { keyword: url.keyword } : { status: PR_STATUS.PENDING },
  );
  const [filters, setFilters] = useState<Filters>(defaults);
  const [supplierOptions, setSupplierOptions] = useState<
    { value: number; label: string }[]
  >([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<PurchaseReturn[]>([]);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState<{ pending: number; shipped: number }>();
  const [loading, setLoading] = useState(true);

  const searchNow = () => {
    setFilters(form.getFieldsValue());
    setPage(1);
  };

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [res, c] = await Promise.all([
        purchaseReturnApi.page({
          page,
          pageSize,
          status: filters.status,
          supplierId: filters.supplierId,
          sourceType: filters.sourceType,
          ownerId: filters.ownerId,
          keyword: filters.keyword?.trim() || undefined,
        }),
        purchaseReturnApi.counts(),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setCounts(c);
    } catch (e) {
      message.error(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, filters, message]);
  useEffect(() => {
    load();
  }, [load]);
  // 从其他页面返回时重新取数（audit UI-055）
  useOnReactivate(load);

  const searchSup = async (kw: string) => {
    try {
      const res = await searchSuppliers(kw);
      setSupplierOptions(
        res.map((s: { id: number; name: string }) => ({
          value: s.id,
          label: s.name,
        })),
      );
    } catch {
      setSupplierOptions([]);
    }
  };

  const sub = (t?: React.ReactNode) =>
    t ? <div style={{ fontSize: 12, color: palette.mute }}>{t}</div> : null;
  const dash = <span style={{ color: palette.mute }}>—</span>;

  const columns: TableColumnsType<PurchaseReturn> = [
    {
      title: '采购退货单号 · 日期',
      dataIndex: 'returnNo',
      width: 170,
      fixed: 'left',
      render: (v: string, r) => (
        <div>
          <Link to={SALES_PATHS.purchaseReturn(r.id)}>{v}</Link>
          {sub(r.createTime?.slice(0, 10))}
        </div>
      ),
    },
    { title: '供应商', dataIndex: 'supplierName', width: 160 },
    {
      title: '型号 · 数量',
      key: 'items',
      width: 200,
      render: (_, r) => (
        <ModelQtyCell
          summary={r.itemSummary}
          totalQty={r.totalQty}
          docLabel={`${r.returnNo} · ${r.supplierName ?? ''}`}
          onOpenDetail={() => history.push(SALES_PATHS.purchaseReturn(r.id))}
        />
      ),
    },
    {
      title: '品牌',
      dataIndex: 'brands',
      width: 120,
      render: (v?: string[]) => <TagList values={v} label="品牌" />,
    },
    {
      title: '品类',
      dataIndex: 'categories',
      width: 130,
      render: (v?: string[]) => <TagList values={v} label="品类" />,
    },
    { title: '来源类型', dataIndex: 'sourceTypeName', width: 150 },
    {
      title: '采购订单',
      dataIndex: 'poNo',
      width: 160,
      render: (v: string | undefined, r) =>
        v && r.poId ? <Link to={PATHS.order(r.poId)}>{v}</Link> : dash,
    },
    {
      title: '快递',
      key: 'express',
      width: 150,
      render: (_, r) =>
        r.carrier ? (
          <div>
            {r.carrier}
            {sub(r.trackingNo)}
          </div>
        ) : (
          dash
        ),
    },
    {
      title: '运费',
      dataIndex: 'freightCny',
      width: 100,
      render: (v?: number) =>
        Number(v) > 0 ? formatAmount(v ?? 0, 'CNY') : dash,
    },
    {
      title: '退款或抵扣',
      dataIndex: 'refundAmount',
      width: 120,
      render: (v: number | undefined, r) =>
        r.status === PR_STATUS.DONE
          ? formatAmount(v ?? 0, r.currencyCode ?? 'CNY')
          : dash,
    },
    {
      title: '状态',
      dataIndex: 'statusName',
      width: 90,
      render: (v: string, r) => (
        <Pill tone={PR_STATUS_TONE[r.status] ?? 'gray'}>{v}</Pill>
      ),
    },
    {
      title: '来源单号',
      key: 'source',
      width: 160,
      render: (_, r) => <SourceNos refs={[prSourceRef(r)]} />,
    },
    { title: '负责人', dataIndex: 'ownerName', width: 100 },
    ...auditColumns<PurchaseReturn>(),
    {
      title: '操作',
      key: 'op',
      width: 64,
      fixed: 'right',
      render: (_, r) => (
        <IconButton
          label="查看"
          size={32}
          icon={<Eye size={18} />}
          onClick={() => history.push(SALES_PATHS.purchaseReturn(r.id))}
        />
      ),
    },
  ];

  return (
    <div>
      <PageHeader help={HELP.page} />
      <Form form={form} initialValues={defaults} component={false}>
        <FilterCard
          onSearch={searchNow}
          onReset={() => {
            // 重置回默认视图：待寄出
            const d = { status: PR_STATUS.PENDING };
            form.resetFields();
            form.setFieldsValue({ ...d, keyword: undefined });
            setFilters(d);
            setPage(1);
          }}
          minColumn={130}
          moreCount={
            [filters.sourceType, filters.ownerId].filter((v) => v != null)
              .length
          }
          more={
            <>
              <FilterField label="来源类型">
                <Form.Item name="sourceType" noStyle>
                  <Select<number>
                    allowClear
                    placeholder="全部"
                    options={PR_SOURCE_OPTIONS}
                    aria-label="来源类型"
                  />
                </Form.Item>
              </FilterField>
              <FilterField label="负责人">
                <Form.Item name="ownerId" noStyle>
                  <Select<number>
                    allowClear
                    showSearch={{ optionFilterProp: 'label' }}
                    placeholder="全部"
                    options={users}
                    aria-label="负责人"
                  />
                </Form.Item>
              </FilterField>
            </>
          }
        >
          <FilterField label="关键词" span={2} help={HELP.keyword}>
            <Form.Item name="keyword" noStyle>
              <Input
                allowClear
                prefix={<Search size={16} />}
                placeholder="退货单号、型号、快递单号"
                aria-label="关键词"
                onPressEnter={searchNow}
              />
            </Form.Item>
          </FilterField>
          <FilterField label="状态">
            <Form.Item name="status" noStyle>
              <Select<number>
                allowClear
                placeholder="全部"
                aria-label="状态"
                options={[
                  {
                    value: PR_STATUS.PENDING,
                    label: withCount('待寄出', counts?.pending),
                  },
                  {
                    value: PR_STATUS.SHIPPED,
                    label: withCount('已寄出', counts?.shipped),
                  },
                  { value: PR_STATUS.DONE, label: '已完成' },
                  { value: PR_STATUS.VOID, label: '已作废' },
                ]}
                // 切换状态立即查询（doc-list-unify 状态下拉的写法）
                onChange={() => searchNow()}
              />
            </Form.Item>
          </FilterField>
          <FilterField label="供应商">
            <Form.Item name="supplierId" noStyle>
              <Select<number>
                allowClear
                showSearch={{ filterOption: false, onSearch: searchSup }}
                placeholder="全部"
                options={supplierOptions}
                onFocus={() => supplierOptions.length === 0 && searchSup('')}
                aria-label="供应商"
              />
            </Form.Item>
          </FilterField>
        </FilterCard>
      </Form>
      <MainCard left={`共 ${total} 条`}>
        <Table<PurchaseReturn>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: tableScrollX(columns) }}
          locale={{
            emptyText:
              filters.status === PR_STATUS.PENDING
                ? '没有待寄出的采购退货；退货入库去向选「退回供应商」或到货差异选择退货后会出现在这里'
                : '没有符合条件的采购退货单',
          }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            onChange: (p, s) => {
              setPage(p);
              setPageSize(s);
            },
          }}
        />
      </MainCard>
    </div>
  );
};

export default PurchaseReturnsPage;
