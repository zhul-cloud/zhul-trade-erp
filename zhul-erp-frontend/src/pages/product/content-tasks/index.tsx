import {
  ArrowRightOutlined,
  DownloadOutlined,
  SearchOutlined,
  SettingOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { Link, useAccess, useSearchParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, Input, Segmented, Select, Table } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { Card, PageTitle } from '@/pages/inquiry/shared/components';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import {
  ContentStatusPill,
  factsVerified,
  MarketingDownload,
  PlatformChips,
  STAGES,
  useDownloadPackage,
  usePlatforms,
  VerifyFactsButton,
} from '../products/cards/SeoCard';
import {
  type BrandOption,
  brandApi,
  type CategoryOption,
  type ContentTask,
  type ContentTaskQuery,
  categoryApi,
  contentApi,
  type MarketingPlatform,
  type PackageType,
  readBizError,
} from '../service';
import { PlatformSettings } from './PlatformSettings';
import { UploadDrawer } from './UploadDrawer';

type Tab = 0 | 1 | 2 | 3 | 4;

const TABS: { value: Tab; label: string; key: string }[] = [
  { value: 0, label: '全部', key: 'all' },
  { value: 1, label: STAGES[1].text, key: '1' },
  { value: 2, label: STAGES[2].text, key: '2' },
  { value: 3, label: STAGES[3].text, key: '3' },
  { value: 4, label: STAGES[4].text, key: '4' },
];

const EMPTY_TEXT: Record<Tab, string> = {
  0: '没有记录',
  1: '没有待补资料的商品',
  2: '没有待核对资料的商品',
  3: '没有待生成营销内容的商品',
  4: '还没有资料已核、启用的平台都写完（或标了不发布）的商品',
};

const ContentTasks: React.FC = () => {
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
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
  const { download: downloadPackage, downloading } = useDownloadPackage();
  const [uploadOpen, setUploadOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const platformState = usePlatforms();
  const platforms = platformState.enabled;
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

  const download = async (
    ids: number[],
    type: PackageType,
    platform?: MarketingPlatform,
  ) => {
    const stages = rows
      .filter((r) => ids.includes(r.productId))
      .map((r) => r.status);
    await downloadPackage(ids, type, stages, platform);
    load();
  };

  const muted = <span style={{ color: palette.mute }}>—</span>;

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
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (v: number) => <ContentStatusPill status={v} />,
    },
    {
      title: '资料（共享）',
      key: 'facts',
      width: 170,
      render: (_, t) => {
        const verified = factsVerified(t.status) && t.factVerifiedAt;
        const at = verified ? t.factVerifiedAt : t.factImportedAt;
        if (!at) return muted;
        return (
          <div>
            <div style={{ color: verified ? palette.green : palette.ink }}>
              {verified ? '已核对' : '已导入，待核对'}
            </div>
            <div style={{ fontSize: 12, color: palette.mute }}>
              {formatDateTime(at)} ·{' '}
              {verified ? t.factVerifiedBy : t.factImportedBy}
            </div>
          </div>
        );
      },
    },
    {
      title: '营销（本公司）',
      key: 'marketing',
      width: 270,
      render: (_, t) => <PlatformChips progress={t} platforms={platforms} />,
    },
    {
      title: '最近下载',
      key: 'downloaded',
      width: 170,
      render: (_, t) =>
        t.factDownloadedAt || t.downloadedAt ? (
          <div style={{ fontSize: 12, lineHeight: 1.7 }}>
            {t.factDownloadedAt && (
              <div>资料 {formatDateTime(t.factDownloadedAt)}</div>
            )}
            {t.downloadedAt && <div>营销 {formatDateTime(t.downloadedAt)}</div>}
          </div>
        ) : (
          muted
        ),
    },
    ...auditColumns<ContentTask>(),
    {
      title: '操作',
      key: 'actions',
      width: 250,
      fixed: 'right',
      render: (_, t) => (
        <span
          style={{
            display: 'inline-flex',
            gap: 12,
            alignItems: 'center',
            whiteSpace: 'nowrap',
          }}
        >
          {!factsVerified(t.status) && (
            <a onClick={() => download([t.productId], 'facts')}>下载资料包</a>
          )}
          {!factsVerified(t.status) && access['product:content:edit'] && (
            <VerifyFactsButton
              productId={t.productId}
              label={`${t.brandName ?? ''} · ${t.mpn}`}
              link
              onDone={load}
            />
          )}
          <MarketingDownload
            link
            platforms={platforms}
            onPick={(p) => download([t.productId], 'marketing', p)}
          />
        </span>
      ),
    },
  ];

  return (
    <div>
      <PageTitle
        crumbs={['内容任务']}
        title="内容任务"
        description="先补商品资料（中文，所有公司共用），再按平台生成营销内容（本公司）：下载任务包交给 AI，生成好的 Markdown 传回来。"
        actions={
          <>
            <Button
              icon={<SettingOutlined />}
              onClick={() => setSettingsOpen(true)}
            >
              平台设置
            </Button>
            <Button
              icon={<DownloadOutlined />}
              disabled={selected.length === 0}
              loading={downloading === 'facts'}
              onClick={() => download(selected, 'facts')}
            >
              下载资料包{selected.length ? ` · ${selected.length}` : ''}
            </Button>
            <MarketingDownload
              platforms={platforms}
              disabled={selected.length === 0}
              downloading={downloading}
              label={`下载营销包${selected.length ? ` · ${selected.length}` : ''}`}
              onPick={(p) => download(selected, 'marketing', p)}
            />
            <Button
              type="primary"
              icon={<UploadOutlined />}
              onClick={() => setUploadOpen(true)}
            >
              上传
            </Button>
          </>
        }
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
        {[1, 2, 3, 4].map((s) => (
          <React.Fragment key={s}>
            {s > 1 && <ArrowRightOutlined style={{ fontSize: 10 }} />}
            <ContentStatusPill status={s} />
          </React.Fragment>
        ))}
        <span style={{ marginLeft: 8 }}>
          资料阶段所有公司共用一份，谁导入、谁核对都看得到；营销阶段各公司各做各的，按本公司启用的平台计进度，独立站要英文、俄文都写入，标了不发布的平台不计。资料待核时也能先做营销，只会提醒一句。
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
            scroll={{ x: 2150 }}
            rowSelection={{
              selectedRowKeys: selected,
              onChange: (keys) => setSelected(keys as number[]),
              preserveSelectedRowKeys: true,
            }}
            locale={{ emptyText: EMPTY_TEXT[tab] }}
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
        资料已核、启用的平台都写完（或标了不发布）才算完成，可以分批上传；「资料已核」会把未核实的中文规格和应用场景标为已核实，以后导入资料包不再覆盖；按更新时间倒序
      </div>
      <PlatformSettings
        open={settingsOpen}
        options={platformState.options}
        onClose={() => setSettingsOpen(false)}
        onSaved={(options) => {
          platformState.setOptions(options);
          load();
        }}
      />
      <UploadDrawer
        open={uploadOpen}
        onClose={() => setUploadOpen(false)}
        onDone={load}
      />
    </div>
  );
};

export default ContentTasks;
