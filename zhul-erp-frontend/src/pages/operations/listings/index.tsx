import { ArrowRightOutlined, SearchOutlined } from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { Button, Input, Segmented, Select, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle, Pill } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { PLATFORM_META } from '@/pages/product/products/cards/SeoCard';
import {
  type BrandOption,
  brandApi,
  type CategoryOption,
  categoryApi,
  type MarketingPlatform,
} from '@/pages/product/service';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import ListingDrawer, { StatusPills } from './ListingDrawer';
import {
  type ListingQuery,
  type ListingTab,
  type ListingTask,
  listingApi,
  readBizError,
} from './service';

const TABS: { key: ListingTab; label: string; empty: string }[] = [
  {
    key: 'pending',
    label: '待发布',
    empty:
      '没有待发布的任务；资料核对、某个平台的营销内容写完后，会自动生成这个平台的发品任务',
  },
  {
    key: 'updated',
    label: '内容有更新',
    empty: '已发布的商品营销内容都没有更新',
  },
  { key: 'replace', label: '待替换素材', empty: '没有需要替换参考素材的任务' },
  { key: 'published', label: '已发布', empty: '还没有已发布的任务' },
  { key: 'cancelled', label: '已取消', empty: '没有已取消的任务' },
  { key: 'all', label: '全部', empty: '还没有发品任务' },
];

const PLATFORMS: MarketingPlatform[] = ['alibaba', 'mic', 'site', 'social'];

/** 运营管理 → 发品任务：商品 × 平台，复制内容和素材到平台发布后回填链接 */
const ListingTasksPage: React.FC = () => {
  const { palette } = useAppTheme();
  const [tab, setTab] = useState<ListingTab>('pending');
  const [keyword, setKeyword] = useState('');
  const [platform, setPlatform] = useState<MarketingPlatform>();
  const [brandId, setBrandId] = useState<number>();
  const [categoryId, setCategoryId] = useState<number>();
  const [query, setQuery] = useState<ListingQuery>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<ListingTask[]>([]);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState<Partial<Record<ListingTab, number>>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [open, setOpen] = useState<number>();
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
      platform,
      brandId,
      categoryId,
    });
    setPage(1);
  };

  const muted = <span style={{ color: palette.mute }}>—</span>;

  const columns: TableColumnsType<ListingTask> = [
    {
      title: '商品',
      key: 'product',
      width: 260,
      fixed: 'left',
      render: (_, r) => (
        <div>
          <a style={{ fontWeight: 600 }} onClick={() => setOpen(r.id)}>
            {r.brand} {r.model}
          </a>
          <div style={{ fontSize: 12, color: palette.mute }}>
            {[r.productName, r.category].filter(Boolean).join(' · ')}
          </div>
        </div>
      ),
    },
    {
      title: '平台',
      dataIndex: 'platformLabel',
      width: 110,
    },
    {
      title: '状态',
      key: 'status',
      width: 200,
      render: (_, r) => (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          <StatusPills task={r} />
          {r.status === 3 && r.cancelReason && (
            <span style={{ fontSize: 12, color: palette.mute }}>
              {r.cancelReason}
            </span>
          )}
        </div>
      ),
    },
    {
      title: '素材',
      key: 'media',
      width: 190,
      render: (_, r) =>
        r.refModel && (r.status === 2 || r.imageCount + r.videoCount === 0) ? (
          <span style={{ fontSize: 12 }}>
            参考素材：{r.refBrand} {r.refModel}
          </span>
        ) : r.imageCount + r.videoCount > 0 ? (
          <span style={{ fontSize: 12 }}>
            {r.imageCount} 张图片 · {r.videoCount} 个视频
          </span>
        ) : (
          muted
        ),
    },
    {
      title: '发布',
      key: 'published',
      width: 170,
      render: (_, r) =>
        r.publishedAt ? (
          <span style={{ fontSize: 12 }}>
            {r.publishedBy}
            <br />
            {formatDateTime(r.publishedAt)}
          </span>
        ) : (
          muted
        ),
    },
    {
      title: '平台链接',
      dataIndex: 'listingUrl',
      width: 220,
      ellipsis: true,
      render: (v?: string) =>
        v ? (
          <a href={v} target="_blank" rel="noopener noreferrer" title={v}>
            {v}
          </a>
        ) : (
          muted
        ),
    },
    ...auditColumns<ListingTask>(),
    {
      title: '操作',
      key: 'actions',
      width: 80,
      fixed: 'right',
      render: (_, r) => <a onClick={() => setOpen(r.id)}>处理</a>,
    },
  ];

  return (
    <div>
      <PageTitle
        crumbs={['发品任务']}
        title="发品任务"
        description="商品资料核对、某个平台的营销内容写完后自动生成这个平台的发品任务。在任务里复制内容、下载素材，到平台上发布后回填链接。"
      />
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
          value={platform}
          onChange={setPlatform}
          placeholder="全部平台"
          options={PLATFORMS.map((p) => ({
            value: p,
            label: PLATFORM_META[p].label,
          }))}
          style={{ width: 140 }}
          aria-label="平台"
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
          <Table<ListingTask>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 1900 }}
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
      <ListingDrawer
        id={open}
        onClose={() => setOpen(undefined)}
        onChanged={load}
      />
    </div>
  );
};

export default ListingTasksPage;
