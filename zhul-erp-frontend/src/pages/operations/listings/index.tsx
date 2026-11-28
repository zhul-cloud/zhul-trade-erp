import {
  ArrowRightOutlined,
  EyeOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { Link } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, Input, Segmented, Select, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { useOnReactivate } from '@/components/PageTabs/hooks';
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
import { usePermission } from '@/utils/permission';
import { tableScrollX } from '@/utils/tableScroll';
import { PlatformTaskChip, TABS } from './components';
import {
  type ListingProduct,
  type ListingQuery,
  type ListingTab,
  listingApi,
  readBizError,
} from './service';
import { BatchPreviewModal } from './site/BatchPreviewModal';
import { ReleaseDrawer } from './site/ReleaseDrawer';
import {
  PERM_PUBLISH,
  type PublishTarget,
  sitePublishApi,
  TASK_PUBLISHING,
} from './site/service';

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
  const { hasPerm } = usePermission();
  const canPublish = hasPerm(PERM_PUBLISH);
  const [targets, setTargets] = useState<PublishTarget[]>([]);
  const [targetId, setTargetId] = useState<number>();
  const [selected, setSelected] = useState<ListingProduct[]>([]);
  const [previewing, setPreviewing] = useState(false);
  const [releaseId, setReleaseId] = useState<number>();
  const target = targets.find((t) => t.id === targetId);

  useEffect(() => {
    if (!canPublish) return;
    sitePublishApi
      .targets()
      .then((list) => {
        setTargets(list);
        setTargetId(list[0]?.id);
      })
      .catch(() => setTargets([]));
  }, [canPublish]);

  /** 商品在目标站点上可以发布的任务（没取消、不在发布中） */
  const siteTask = (r: ListingProduct) =>
    r.tasks.find(
      (t) =>
        t.platform === 'site' &&
        t.siteId === targetId &&
        t.status !== 3 &&
        t.status !== TASK_PUBLISHING,
    );

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

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

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
      {canPublish && targets.length > 0 && selected.length > 0 && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            flexWrap: 'wrap',
            padding: '10px 14px',
            borderRadius: 12,
            border: `1px solid ${palette.accentLine}`,
            background: palette.accentSoft,
            marginBottom: 12,
          }}
        >
          <b>已选 {selected.length} 个商品</b>
          <span style={{ color: palette.sub }}>发布到</span>
          <Select
            value={targetId}
            onChange={(v) => {
              setTargetId(v);
              setSelected([]);
            }}
            options={targets.map((t) => ({
              value: t.id,
              label: `独立站 · ${t.name}`,
            }))}
            style={{ width: 220 }}
            aria-label="发布到"
          />
          <span style={{ flex: 1 }} />
          <Button type="text" onClick={() => setSelected([])}>
            取消选择
          </Button>
          <Button
            type="primary"
            icon={<EyeOutlined />}
            onClick={() => setPreviewing(true)}
          >
            发布预览
          </Button>
        </div>
      )}
      {error ? (
        <ErrorHint message={error} onRetry={load} />
      ) : (
        <Card style={{ padding: 0 }}>
          <Table<ListingProduct>
            rowKey="productId"
            rowSelection={
              canPublish && targets.length > 0
                ? {
                    preserveSelectedRowKeys: true,
                    selectedRowKeys: selected.map((r) => r.productId),
                    onChange: (_, rs) => setSelected(rs.filter(Boolean)),
                    getCheckboxProps: (r) => ({
                      disabled: !siteTask(r),
                      title: siteTask(r)
                        ? undefined
                        : `没有可以发布到 ${target?.name ?? '站点'} 的任务`,
                    }),
                  }
                : undefined
            }
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: tableScrollX(columns) }}
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
      <div style={{ marginTop: 10, fontSize: 12, color: palette.mute }}>
        独立站每个站点一个标签；「已取消」表示商品不在这个站点的商品范围内或站点已停用
      </div>
      <BatchPreviewModal
        site={previewing ? target : undefined}
        taskIds={selected
          .map((r) => siteTask(r)?.id)
          .filter((id): id is number => !!id)}
        onClose={() => setPreviewing(false)}
        onReleased={(id) => {
          setPreviewing(false);
          setSelected([]);
          setReleaseId(id);
          load();
        }}
      />
      <ReleaseDrawer
        releaseId={releaseId}
        siteName={target?.name}
        onClose={() => setReleaseId(undefined)}
        onChanged={load}
      />
    </div>
  );
};

export default ListingTasksPage;
