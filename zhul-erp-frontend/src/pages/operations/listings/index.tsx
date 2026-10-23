import { ArrowRightOutlined, SearchOutlined } from '@ant-design/icons';
import { Link } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, Input, Segmented, Select, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import {
  type BrandOption,
  brandApi,
  type CategoryOption,
  categoryApi,
} from '@/pages/product/service';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { usePageSize } from '@/utils/pageSize';
import { PlatformTaskChip, TABS } from './components';
import {
  type ListingProduct,
  type ListingQuery,
  type ListingTab,
  listingApi,
  readBizError,
} from './service';

/** 运营管理 → 发品任务：商品 × 平台，复制内容和素材到平台发布后回填链接 */
const ListingTasksPage: React.FC = () => {
  const { palette } = useAppTheme();
  const [tab, setTab] = useState<ListingTab>('pending');
  const [keyword, setKeyword] = useState('');
  const [brandId, setBrandId] = useState<number>();
  const [categoryId, setCategoryId] = useState<number>();
  const [query, setQuery] = useState<ListingQuery>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<ListingProduct[]>([]);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState<Partial<Record<ListingTab, number>>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [brands, setBrands] = useState<BrandOption[]>([]);
  const [categories, setCategories] = useState<CategoryOption[]>([]);

  useEffect(() => {
    brandApi
      .options()
      .then(setBrands)
      .catch(() => setBrands([]));
    categoryApi
      .options(1)
      .then(setCategories)
      .catch(() => setCategories([]));
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const [res, c] = await Promise.all([
        listingApi.page({ ...query, tab, page, pageSize }),
        listingApi.counts(query),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setCounts(c);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [query, tab, page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  const apply = () => {
    setQuery({
      keyword: keyword.trim() || undefined,
      brandId,
      categoryId,
    });
    setPage(1);
  };

  const columns: TableColumnsType<ListingProduct> = [
    {
      title: '商品',
      key: 'product',
      width: 260,
      fixed: 'left',
      render: (_, r) => (
        <div>
          <Link to={`/operations/listings/${r.productId}`}>
            <b>
              {r.brand ?? ''} · {r.model}
            </b>
          </Link>
          <div style={{ fontSize: 12, color: palette.mute }}>
            {r.productName || '—'}
          </div>
        </div>
      ),
    },
    { title: '品牌', dataIndex: 'brand', width: 110 },
    {
      title: '品类',
      dataIndex: 'category',
      width: 120,
      render: (v?: string) => v || '—',
    },
    {
      title: '状态',
      key: 'status',
      width: 150,
      render: (_, r) => {
        const s = TABS.find((t) => t.key === r.status);
        return (
          <span style={{ display: 'inline-flex', gap: 4, flexWrap: 'wrap' }}>
            <Pill tone={s?.tone ?? 'gray'}>{r.statusName}</Pill>
            {r.needMedia && <Pill tone="orange">素材待补</Pill>}
          </span>
        );
      },
    },
    {
      title: '各平台',
      key: 'platforms',
      width: 330,
      render: (_, r) => (
        <span style={{ display: 'inline-flex', gap: 4, flexWrap: 'wrap' }}>
          {r.tasks.map((t) => (
            <PlatformTaskChip key={t.id} task={t} />
          ))}
        </span>
      ),
    },
    ...auditColumns<ListingProduct>(),
    {
      title: '操作',
      key: 'actions',
      width: 80,
      fixed: 'right',
      render: (_, r) => (
        <Link to={`/operations/listings/${r.productId}`}>详情</Link>
      ),
    },
  ];

  return (
    <div>
      <PageTitle description="一个商品一行，汇总各平台的发品状态。商品资料核对、某个平台的营销内容写完后自动生成这个平台的发品任务；进详情按平台复制内容、下载素材，到平台上发布后回填链接。" />
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          flexWrap: 'wrap',
          padding: '10px 14px',
          borderRadius: 10,
          background: palette.inset,
          marginBottom: 16,
          fontSize: 12,
          color: palette.mute,
        }}
      >
        <span style={{ fontWeight: 600 }}>流程</span>
        <Pill tone="gray">资料已核 + 平台营销写完</Pill>
        <ArrowRightOutlined style={{ fontSize: 10 }} />
        <Pill tone="accent">待发布</Pill>
        <ArrowRightOutlined style={{ fontSize: 10 }} />
        <Pill tone="green">已发布</Pill>
        <span style={{ marginLeft: 8 }}>
          没有发品素材时先在素材库勾选「用于发品」，或在任务里选择参考素材；发布后营销内容重新导入，会提醒同步。
        </span>
      </div>
      <div
        style={{
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          flexWrap: 'wrap',
          marginBottom: 16,
        }}
      >
        <Segmented<ListingTab>
          value={tab}
          onChange={(t) => {
            setTab(t);
            setPage(1);
          }}
          options={TABS.map((t) => ({
            value: t.key,
            label:
              counts[t.key] !== undefined
                ? `${t.label} · ${counts[t.key]}`
                : t.label,
          }))}
        />
        <Input
          allowClear
          prefix={<SearchOutlined />}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onPressEnter={apply}
          placeholder="型号、名称"
          style={{ width: 200 }}
          aria-label="关键词"
        />
        <Select
          allowClear
          showSearch={{ optionFilterProp: 'label' }}
          value={brandId}
          onChange={setBrandId}
          placeholder="全部品牌"
          options={brands.map((b) => ({ value: b.id, label: b.brandName }))}
          style={{ width: 160 }}
          aria-label="品牌"
        />
        <Select
          allowClear
          showSearch={{ optionFilterProp: 'label' }}
          value={categoryId}
          onChange={setCategoryId}
          placeholder="全部品类"
          options={categories.map((c) => ({
            value: c.id,
            label: c.categoryNameZh || c.categoryName,
          }))}
          style={{ width: 160 }}
          aria-label="品类"
        />
        <span style={{ flex: 1 }} />
        <Button type="primary" onClick={apply}>
          查询
        </Button>
      </div>
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Card style={{ padding: 0 }}>
          <Table<ListingProduct>
            rowKey="productId"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 1500 }}
            locale={{
              emptyText: TABS.find((t) => t.key === tab)?.empty ?? '没有记录',
            }}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
              showTotal: (t: number) => `共 ${t} 条`,
              onChange: (p: number, s: number) => {
                setPage(p);
                setPageSize(s);
              },
            }}
          />
        </Card>
      )}
    </div>
  );
};

export default ListingTasksPage;
