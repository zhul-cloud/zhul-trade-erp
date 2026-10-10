import {
  DownloadOutlined,
  SearchOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { Link, useSearchParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Input, Segmented, Select, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { ContentStatusPill, LangChips } from '../products/cards/SeoCard';
import {
  type BrandOption,
  brandApi,
  type CategoryOption,
  type ContentTask,
  type ContentTaskQuery,
  categoryApi,
  contentApi,
  readBizError,
} from '../service';
import { UploadDrawer } from './UploadDrawer';

type Tab = 0 | 1 | 2 | 3;

const TABS: { value: Tab; label: string; key: string }[] = [
  { value: 0, label: '全部', key: 'all' },
  { value: 1, label: '待生成', key: '1' },
  { value: 2, label: '进行中', key: '2' },
  { value: 3, label: '已完成', key: '3' },
];

/** 最近写入：取三种语言里最晚的一次 */
const lastWrite = (t: ContentTask) => {
  const list: { at: string; by?: string; lang: string }[] = [];
  if (t.zhAt) list.push({ at: t.zhAt, by: t.zhBy, lang: '中文' });
  if (t.enAt) list.push({ at: t.enAt, by: t.enBy, lang: 'EN' });
  if (t.ruAt) list.push({ at: t.ruAt, by: t.ruBy, lang: 'RU' });
  list.sort((a, b) => (a.at < b.at ? 1 : -1));
  return list[0];
};

const ContentTasks: React.FC = () => {
  const { palette } = useAppTheme();
  const { message } = App.useApp();
  const [search] = useSearchParams();
  const initialKeyword = search.get('keyword') ?? '';
  const [tab, setTab] = useState<Tab>(initialKeyword ? 0 : 1);
  const [keyword, setKeyword] = useState(initialKeyword);
  const [brandId, setBrandId] = useState<number>();
  const [categoryId, setCategoryId] = useState<number>();
  const [query, setQuery] = useState<ContentTaskQuery>({
    keyword: initialKeyword || undefined,
  });
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState<ContentTask[]>([]);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [selected, setSelected] = useState<number[]>([]);
  const [downloading, setDownloading] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
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
        contentApi.page({
          page,
          pageSize,
          status: tab || undefined,
          ...query,
        }),
        contentApi.counts(query),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setCounts(c);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, tab, query]);

  useEffect(() => {
    load();
  }, [load]);

  const apply = () => {
    setQuery({ keyword: keyword.trim() || undefined, brandId, categoryId });
    setPage(1);
  };

  const download = async (ids: number[]) => {
    setDownloading(true);
    try {
      await contentApi.download(ids);
      load();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setDownloading(false);
    }
  };

  const columns: TableColumnsType<ContentTask> = [
    {
      title: '商品',
      key: 'product',
      width: 260,
      fixed: 'left',
      render: (_, t) => (
        <div>
          <Link to={`/product/products/${t.productId}`}>
            <b>
              {t.brandName} · {t.mpn}
            </b>
          </Link>
          <div style={{ fontSize: 12, color: palette.mute }}>
            {t.productName || '—'}
          </div>
        </div>
      ),
    },
    { title: '品牌', dataIndex: 'brandName', width: 110 },
    {
      title: '品类',
      dataIndex: 'categoryName',
      width: 120,
      render: (v?: string) => v || '—',
    },
    {
      title: '语言',
      key: 'langs',
      width: 170,
      render: (_, t) => <LangChips zhAt={t.zhAt} enAt={t.enAt} ruAt={t.ruAt} />,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number) => <ContentStatusPill status={v} />,
    },
    {
      title: '最近写入',
      key: 'lastWrite',
      width: 150,
      render: (_, t) => {
        const w = lastWrite(t);
        return w ? (
          <div>
            <div style={{ color: palette.ink }}>
              {formatDateTime(w.at)} · {w.lang}
            </div>
            <div style={{ fontSize: 12, color: palette.mute }}>{w.by}</div>
          </div>
        ) : (
          <span style={{ color: palette.mute }}>—</span>
        );
      },
    },
    {
      title: '最近下载',
      dataIndex: 'downloadedAt',
      width: 160,
      render: (v?: string) =>
        v ? formatDateTime(v) : <span style={{ color: palette.mute }}>—</span>,
    },
    ...auditColumns<ContentTask>(),
    {
      title: '操作',
      key: 'actions',
      width: 150,
      fixed: 'right',
      render: (_, t) => (
        <span style={{ display: 'inline-flex', gap: 12 }}>
          <a onClick={() => download([t.productId])}>下载任务包</a>
          <a onClick={() => setUploadOpen(true)}>上传</a>
        </span>
      ),
    },
  ];

  return (
    <div>
      <PageTitle
        crumbs={['内容任务']}
        title="内容任务"
        description="每个商品都要有中文、英文、俄文三套内容：下载任务包交给 AI 生成，生成好的 Markdown 传回来，系统解析后填进商品档案。"
        actions={
          <>
            <Button
              icon={<DownloadOutlined />}
              disabled={selected.length === 0}
              loading={downloading}
              onClick={() => download(selected)}
            >
              下载任务包{selected.length ? ` · ${selected.length}` : ''}
            </Button>
            <Button
              type="primary"
              icon={<UploadOutlined />}
              onClick={() => setUploadOpen(true)}
            >
              上传内容包
            </Button>
          </>
        }
      />
      <div
        style={{
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          flexWrap: 'wrap',
          marginBottom: 16,
        }}
      >
        <Segmented<Tab>
          value={tab}
          onChange={(t) => {
            setTab(t);
            setPage(1);
            setSelected([]);
          }}
          options={TABS.map((t) => ({
            value: t.value,
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
          style={{ width: 220 }}
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
          <Table<ContentTask>
            rowKey="productId"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 1900 }}
            rowSelection={{
              selectedRowKeys: selected,
              onChange: (keys) => setSelected(keys as number[]),
              preserveSelectedRowKeys: true,
            }}
            locale={{
              emptyText:
                tab === 1
                  ? '没有待生成的商品'
                  : tab === 3
                    ? '还没有三种语言都写入的商品'
                    : '没有记录',
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
      <div style={{ fontSize: 12, color: palette.mute, marginTop: 8 }}>
        中文、英文、俄文三种都写入才算完成，可以分批上传；按更新时间倒序
      </div>
      <UploadDrawer
        open={uploadOpen}
        onClose={() => setUploadOpen(false)}
        onDone={load}
      />
    </div>
  );
};

export default ContentTasks;
