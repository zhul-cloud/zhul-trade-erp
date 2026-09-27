import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import {
  App,
  Button,
  Checkbox,
  type CheckboxProps,
  Input,
  type InputRef,
  Select,
  Skeleton,
  Space,
  Switch,
  Table,
  Tooltip,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { CompletenessBar } from '../components/CompletenessBar';
import { EmptyHint, ErrorHint } from '../components/EmptyHint';
import { BrandMark, LifecyclePill, Pill } from '../components/Pills';
import { LIFECYCLE_OPTIONS, normalizeMpn } from '../constants';
import {
  type BrandOption,
  brandApi,
  type Category,
  type CompletenessSummary,
  categoryApi,
  type Product,
  type ProductQuery,
  productApi,
  type SeriesOption,
  seriesApi,
} from '../service';
import { PageHeader, ProductThemeProvider, useProductTheme } from '../theme';

const FILTER_KEY = 'zhul_product_list_filters';

interface Filters {
  categoryId?: number;
  brandId?: number;
  seriesId?: number;
  lifecycleStatus?: number;
  status?: number;
  includeDeleted?: boolean;
  missing?: string;
}

/** 记住上次的筛选条件（关键词和缺项筛选不记：那是临时的） */
const loadFilters = (): Filters => {
  try {
    const raw = JSON.parse(localStorage.getItem(FILTER_KEY) ?? '{}');
    const { categoryId, brandId, seriesId, lifecycleStatus, status } = raw;
    return { categoryId, brandId, seriesId, lifecycleStatus, status };
  } catch {
    return {};
  }
};

const MISSING_CHIPS: {
  key: string;
  label: string;
  field: keyof CompletenessSummary;
}[] = [
  { key: 'media', label: '缺图片', field: 'missingMedia' },
  { key: 'logistics', label: '缺物流', field: 'missingLogistics' },
  { key: 'customs', label: '缺海关', field: 'missingCustoms' },
  { key: 'price', label: '缺参考价', field: 'missingPrice' },
];

const ProductListInner: React.FC = () => {
  const { message, modal } = App.useApp();
  const access = useAccess();
  const { palette } = useProductTheme();
  const platform = !!access.productPlatform;
  const canEdit = !!access['product:product:edit'];

  const [keyword, setKeyword] = useState('');
  const [debounced, setDebounced] = useState('');
  const [filters, setFilters] = useState<Filters>(loadFilters);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<Product[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [firstLoad, setFirstLoad] = useState(true);
  const [error, setError] = useState<string>();
  const [selected, setSelected] = useState<React.Key[]>([]);
  const [summary, setSummary] = useState<CompletenessSummary>();
  const [categories, setCategories] = useState<Category[]>([]);
  const [brands, setBrands] = useState<BrandOption[]>([]);
  const [seriesOptions, setSeriesOptions] = useState<SeriesOption[]>([]);
  const searchRef = useRef<InputRef>(null);

  // 输入即搜，防抖 300ms
  useEffect(() => {
    const t = setTimeout(() => {
      setDebounced(keyword.trim());
      setPage(1);
    }, 300);
    return () => clearTimeout(t);
  }, [keyword]);

  // `/` 键聚焦搜索框
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const el = e.target as HTMLElement;
      if (
        e.key === '/' &&
        !/^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName) &&
        !el.isContentEditable
      ) {
        e.preventDefault();
        searchRef.current?.focus();
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  useEffect(() => {
    categoryApi
      .page({ page: 1, pageSize: 100, status: 1 })
      .then((r) => setCategories(r.records))
      .catch(() => {});
    brandApi
      .options()
      .then(setBrands)
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!filters.brandId) {
      setSeriesOptions([]);
      return;
    }
    seriesApi
      .options(filters.brandId)
      .then(setSeriesOptions)
      .catch(() => setSeriesOptions([]));
  }, [filters.brandId]);

  const loadSummary = useCallback(() => {
    if (platform)
      productApi
        .summary()
        .then(setSummary)
        .catch(() => {});
  }, [platform]);

  useEffect(loadSummary, [loadSummary]);

  const query = useMemo<ProductQuery>(
    () => ({
      page,
      pageSize,
      keyword: debounced || undefined,
      ...filters,
      includeDeleted: filters.includeDeleted || undefined,
    }),
    [page, pageSize, debounced, filters],
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const res = await productApi.page(query);
      setRows(res.records);
      setTotal(res.total);
    } catch (e) {
      setError((e as Error)?.message ?? '请稍后重试');
    } finally {
      setLoading(false);
      setFirstLoad(false);
    }
  }, [query]);

  useEffect(() => {
    load();
  }, [load]);

  // antd 表格为测量列宽渲染了一行 aria-hidden 的隐藏行，行里带一个"全选"复选框，键盘会 Tab 到它。
  // 组件库没有提供关闭的选项，这里把隐藏行里的输入从 Tab 顺序里拿掉。
  useEffect(() => {
    for (const el of document.querySelectorAll<HTMLElement>(
      '.ant-table-measure-row input',
    )) {
      el.tabIndex = -1;
    }
  }, [rows, loading, firstLoad]);

  const updateFilters = (patch: Partial<Filters>) => {
    setFilters((prev) => {
      const next = { ...prev, ...patch };
      try {
        const { categoryId, brandId, seriesId, lifecycleStatus, status } = next;
        localStorage.setItem(
          FILTER_KEY,
          JSON.stringify({
            categoryId,
            brandId,
            seriesId,
            lifecycleStatus,
            status,
          }),
        );
      } catch {
        /* 记不住就算了 */
      }
      return next;
    });
    setPage(1);
    setSelected([]);
  };

  const clearAll = () => {
    setKeyword('');
    setFilters({});
    try {
      localStorage.removeItem(FILTER_KEY);
    } catch {
      /* 忽略 */
    }
    setPage(1);
  };

  const changeStatus = (row: Product) => {
    const disabling = row.status === 1;
    modal.confirm({
      title: disabling
        ? `停用「${row.mpnDisplay}」？`
        : `启用「${row.mpnDisplay}」？`,
      content: disabling
        ? '停用后商品不再出现在选择器和匹配结果中，已引用它的单据不受影响。'
        : '启用后商品可以被询盘、报价等单据选用。',
      okText: disabling ? '停用' : '启用',
      cancelText: '取消',
      onOk: async () => {
        await productApi.setStatus(row.id, disabling ? 0 : 1);
        message.success(disabling ? '已停用' : '已启用');
        load();
      },
    });
  };

  const batchStatus = (status: number) => {
    modal.confirm({
      title:
        status === 0
          ? `批量停用 ${selected.length} 个商品？`
          : `批量启用 ${selected.length} 个商品？`,
      content:
        status === 0
          ? '停用后这些商品不再出现在选择器和匹配结果中，已引用它们的单据不受影响。'
          : '启用后这些商品可以被单据选用。',
      okText: status === 0 ? '批量停用' : '批量启用',
      cancelText: '取消',
      onOk: async () => {
        const results = await Promise.allSettled(
          selected.map((id) => productApi.setStatus(Number(id), status)),
        );
        const failed = results.filter((r) => r.status === 'rejected').length;
        if (failed)
          message.warning(
            `${selected.length - failed} 个已完成，${failed} 个失败，请重试`,
          );
        else message.success('已完成');
        setSelected([]);
        load();
      },
    });
  };

  const exportSelected = () => {
    const picked = rows.filter((r) => selected.includes(r.id));
    const header = [
      '展示型号',
      '品牌',
      '品类',
      '系列',
      '产品名称',
      '生命周期',
      '状态',
    ];
    const lines = picked.map((r) =>
      [
        r.mpnDisplay,
        r.brandName,
        r.categoryName,
        r.seriesName ?? '',
        r.productName,
        r.lifecycleStatus,
        r.status === 1 ? '启用' : '停用',
      ]
        .map((v) => `"${String(v ?? '').replace(/"/g, '""')}"`)
        .join(','),
    );
    const blob = new Blob([`﻿${[header.join(','), ...lines].join('\n')}`], {
      type: 'text/csv;charset=utf-8',
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `商品导出-${dayjs().format('YYYYMMDD-HHmm')}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const restore = async (row: Product) => {
    await productApi.restore(row.id);
    message.success(`已恢复「${row.mpnDisplay}」`);
    load();
  };

  const columns: ColumnsType<Product> = [
    {
      title: '商品',
      key: 'product',
      width: 320,
      render: (_, r) => (
        <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
          <BrandMark name={r.categoryName} size={40} />
          <div style={{ minWidth: 0 }}>
            <a
              href={`/product/products/${r.id}`}
              onClick={(e) => {
                e.preventDefault();
                e.stopPropagation();
                history.push(`/product/products/${r.id}`);
              }}
              style={{ fontWeight: 700, fontSize: 14 }}
            >
              {r.mpnDisplay}
            </a>
            {r.deleted && (
              <span style={{ marginLeft: 8 }}>
                <Pill tone="red">已删除</Pill>
              </span>
            )}
            <Tooltip title={r.specSummary || undefined}>
              <div
                style={{
                  color: palette.sub,
                  fontSize: 12,
                  maxWidth: 240,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {r.specSummary || r.productName || '—'}
              </div>
            </Tooltip>
          </div>
        </div>
      ),
    },
    {
      title: '品牌',
      dataIndex: 'brandName',
      width: 150,
      render: (_, r) => (
        <Space size={8}>
          <BrandMark name={r.brandName} size={22} />
          <span>{r.brandName}</span>
        </Space>
      ),
    },
    {
      title: '系列',
      dataIndex: 'seriesName',
      width: 120,
      render: (v) => <span style={{ whiteSpace: 'nowrap' }}>{v || '—'}</span>,
    },
    {
      title: '生命周期',
      dataIndex: 'lifecycleStatus',
      width: 120,
      render: (v) => <LifecyclePill value={v} />,
    },
    ...(platform
      ? ([
          {
            title: '档案完整度',
            key: 'completeness',
            width: 190,
            sorter: (a: Product, b: Product) =>
              (a.completeness?.done ?? 0) - (b.completeness?.done ?? 0),
            render: (_: unknown, r: Product) => (
              <CompletenessBar value={r.completeness} />
            ),
          },
        ] as ColumnsType<Product>)
      : []),
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (_, r) =>
        canEdit && !r.deleted ? (
          <span
            onClick={(e) => e.stopPropagation()}
            onKeyDown={(e) => e.stopPropagation()}
          >
            <Switch
              checked={r.status === 1}
              aria-label={`${r.mpnDisplay} ${r.status === 1 ? '已启用，点击停用' : '已停用，点击启用'}`}
              onClick={() => changeStatus(r)}
            />
          </span>
        ) : r.status === 1 ? (
          <Pill tone="green">启用</Pill>
        ) : (
          <Pill tone="gray">已停用</Pill>
        ),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 150,
      defaultSortOrder: 'descend',
      sorter: (a, b) =>
        dayjs(a.updateTime).valueOf() - dayjs(b.updateTime).valueOf(),
      render: (v) => (
        <span className="num">{dayjs(v).format('YYYY-MM-DD HH:mm')}</span>
      ),
    },
    ...(canEdit
      ? ([
          {
            title: '操作',
            key: 'op',
            width: 80,
            render: (_: unknown, r: Product) =>
              r.deleted ? (
                <a
                  onClick={(e) => {
                    e.stopPropagation();
                    restore(r);
                  }}
                >
                  恢复
                </a>
              ) : null,
          },
        ] as ColumnsType<Product>)
      : []),
  ];

  const normalized = normalizeMpn(debounced);
  const hasCondition =
    !!debounced ||
    Object.values(filters).some((v) => v !== undefined && v !== false);
  const chip = (active: boolean): React.CSSProperties => ({
    height: 36,
    padding: '0 16px',
    borderRadius: 18,
    border: `1px solid ${active ? 'transparent' : palette.hairline}`,
    background: active ? '#2563EB' : palette.card,
    color: active ? '#FFFFFF' : palette.sub,
    fontWeight: 600,
    cursor: 'pointer',
    fontSize: 14,
  });

  return (
    <>
      <PageHeader
        eyebrow="PRODUCT MASTER"
        title="商品"
        description="一个「品牌 + 型号」就是一个商品。所有租户共用同一份，询盘、报价都从这里选。"
        actions={
          access['product:product:add'] && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => history.push('/product/products/new')}
            >
              新建商品
            </Button>
          )
        }
      />

      {platform && summary && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            flexWrap: 'wrap',
            padding: '12px 16px',
            marginBottom: 16,
            borderRadius: 12,
            background: palette.accentSoft,
            border: `1px solid ${palette.accentLine}`,
          }}
        >
          <span style={{ color: palette.ink, fontSize: 14, fontWeight: 600 }}>
            档案还有可以补充的地方：
          </span>
          {MISSING_CHIPS.map((c) => (
            <button
              type="button"
              key={c.key}
              aria-pressed={filters.missing === c.key}
              onClick={() =>
                updateFilters({
                  missing: filters.missing === c.key ? undefined : c.key,
                })
              }
              style={{
                ...chip(filters.missing === c.key),
                height: 32,
                fontSize: 13,
              }}
            >
              {c.label} <span className="num">{summary[c.field]}</span>
            </button>
          ))}
        </div>
      )}

      <div style={{ marginBottom: 16 }}>
        <Input
          ref={searchRef}
          size="large"
          allowClear
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          prefix={<SearchOutlined />}
          suffix={<kbd style={{ fontSize: 12, color: palette.mute }}>/</kbd>}
          placeholder="搜索型号或产品名称，如 6ES7 214"
          aria-label="搜索商品"
          style={{ height: 48, borderRadius: 14 }}
        />
        <div
          style={{
            minHeight: 22,
            marginTop: 6,
            fontSize: 12,
            color: palette.sub,
          }}
        >
          {normalized && normalized !== debounced.toLowerCase()
            ? `已按「${normalized}」匹配（忽略空格、连字符和大小写）`
            : ''}
        </div>
      </div>

      <fieldset
        style={{
          display: 'flex',
          gap: 8,
          flexWrap: 'wrap',
          margin: '0 0 16px',
          padding: 0,
          border: 0,
        }}
      >
        <legend
          style={{
            position: 'absolute',
            width: 1,
            height: 1,
            overflow: 'hidden',
            clip: 'rect(0 0 0 0)',
          }}
        >
          按品类筛选
        </legend>
        <button
          type="button"
          aria-pressed={!filters.categoryId}
          style={chip(!filters.categoryId)}
          onClick={() => updateFilters({ categoryId: undefined })}
        >
          全部
        </button>
        {categories.map((c) => (
          <button
            type="button"
            key={c.id}
            aria-pressed={filters.categoryId === c.id}
            style={chip(filters.categoryId === c.id)}
            onClick={() =>
              updateFilters({
                categoryId: filters.categoryId === c.id ? undefined : c.id,
              })
            }
          >
            {c.categoryName} <span className="num">{c.productCount}</span>
          </button>
        ))}
      </fieldset>

      <div
        style={{
          display: 'flex',
          gap: 12,
          flexWrap: 'wrap',
          marginBottom: 16,
          alignItems: 'center',
        }}
      >
        <Select
          allowClear
          showSearch={{ optionFilterProp: 'label' }}
          placeholder="品牌"
          aria-label="按品牌筛选"
          style={{ width: 160 }}
          value={filters.brandId}
          options={brands.map((b) => ({ value: b.id, label: b.brandName }))}
          onChange={(v) => updateFilters({ brandId: v, seriesId: undefined })}
        />
        <Select
          allowClear
          placeholder="系列"
          aria-label="按系列筛选"
          style={{ width: 160 }}
          disabled={!filters.brandId}
          value={filters.seriesId}
          options={seriesOptions.map((s) => ({
            value: s.id,
            label: s.seriesName,
          }))}
          onChange={(v) => updateFilters({ seriesId: v })}
        />
        <Select
          allowClear
          placeholder="生命周期"
          aria-label="按生命周期筛选"
          style={{ width: 140 }}
          value={filters.lifecycleStatus}
          options={LIFECYCLE_OPTIONS}
          onChange={(v) => updateFilters({ lifecycleStatus: v })}
        />
        <Select
          allowClear
          placeholder="状态"
          aria-label="按状态筛选"
          style={{ width: 120 }}
          value={filters.status}
          options={[
            { value: 1, label: '启用' },
            { value: 0, label: '已停用' },
          ]}
          onChange={(v) => updateFilters({ status: v })}
        />
        {platform && (
          <Checkbox
            checked={!!filters.includeDeleted}
            onChange={(e) =>
              updateFilters({ includeDeleted: e.target.checked })
            }
          >
            包含已删除
          </Checkbox>
        )}
        {hasCondition && (
          <Button type="link" onClick={clearAll}>
            清除全部条件
          </Button>
        )}
      </div>

      {platform && selected.length > 0 && (
        <div
          role="toolbar"
          aria-label="批量操作"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            padding: '10px 16px',
            marginBottom: 12,
            borderRadius: 12,
            background: palette.accentSoft,
            border: `1px solid ${palette.accentLine}`,
          }}
        >
          <span style={{ fontWeight: 600 }}>
            已选 <span className="num">{selected.length}</span> 项
          </span>
          {canEdit && <Button onClick={() => batchStatus(1)}>批量启用</Button>}
          {canEdit && <Button onClick={() => batchStatus(0)}>批量停用</Button>}
          <Button onClick={exportSelected}>导出</Button>
          <Button type="link" onClick={() => setSelected([])}>
            取消选择
          </Button>
        </div>
      )}

      <div
        style={{
          background: palette.card,
          borderRadius: 16,
          border: `1px solid ${palette.hairline}`,
          overflow: 'hidden',
        }}
      >
        {error ? (
          <ErrorHint message={error} onRetry={load} />
        ) : firstLoad ? (
          <div style={{ padding: 24 }}>
            <Skeleton active paragraph={{ rows: 6 }} />
          </div>
        ) : (
          <Table<Product>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={loading}
            sticky
            scroll={{ x: 900 }}
            rowSelection={
              platform
                ? {
                    selectedRowKeys: selected,
                    onChange: setSelected,
                    // antd 的类型里没有 aria-label，但会透传给复选框；读屏靠它知道选的是哪一行
                    getCheckboxProps: (r: Product) =>
                      ({
                        'aria-label': `选择 ${r.mpnDisplay}`,
                      }) as unknown as Partial<CheckboxProps>,
                  }
                : undefined
            }
            onRow={(r) => ({
              onClick: () => history.push(`/product/products/${r.id}`),
              style: { cursor: 'pointer' },
            })}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
              showTotal: (t) => `共 ${t} 条记录`,
              onChange: (p, s) => {
                setPage(p);
                setPageSize(s);
                setSelected([]);
              },
            }}
            locale={{
              emptyText: hasCondition ? (
                <EmptyHint
                  title="没有找到匹配的商品"
                  description="搜索会忽略空格、连字符和大小写，试试只输入型号前几位。"
                  secondaryText="清除搜索"
                  onSecondary={clearAll}
                  actionText={
                    access['product:product:add'] && debounced
                      ? `新建 ${debounced}`
                      : undefined
                  }
                  onAction={() =>
                    history.push(
                      `/product/products/new?mpn=${encodeURIComponent(debounced)}`,
                    )
                  }
                />
              ) : (
                <EmptyHint
                  title="还没有商品"
                  description="新建第一个商品：只需要品牌、型号和品类，其余信息可以以后慢慢补。"
                  actionText={
                    access['product:product:add'] ? '新建商品' : undefined
                  }
                  onAction={() => history.push('/product/products/new')}
                />
              ),
            }}
          />
        )}
      </div>
    </>
  );
};

const ProductListPage: React.FC = () => (
  <ProductThemeProvider>
    <ProductListInner />
  </ProductThemeProvider>
);

export default ProductListPage;
