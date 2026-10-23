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
import { Pill } from '@/pages/product/components/Pills';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { ImportRecordsDrawer } from '../products/cards/ImportRecords';
import {
  factsVerified,
  MarketingDownload,
  type MarketingPick,
  PlatformChips,
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
  type PackageType,
  readBizError,
} from '../service';
import { PlatformSettings } from './PlatformSettings';
import { UploadDrawer } from './UploadDrawer';

type Tone = 'gray' | 'orange' | 'accent' | 'green';

/** 每个入口的页签：key 与后端计数一致 */
const TABS: Record<
  PackageType,
  { key: string; label: string; tone: Tone; empty: string }[]
> = {
  facts: [
    { key: 'all', label: '全部', tone: 'gray', empty: '没有记录' },
    { key: '1', label: '资料待补', tone: 'gray', empty: '没有待补资料的商品' },
    {
      key: '2',
      label: '资料待核',
      tone: 'orange',
      empty: '没有待核对资料的商品',
    },
    {
      key: 'verified',
      label: '资料已核',
      tone: 'green',
      empty: '还没有资料已核的商品',
    },
  ],
  marketing: [
    { key: 'all', label: '全部', tone: 'gray', empty: '没有记录' },
    {
      key: 'unverified',
      label: '资料未核对',
      tone: 'orange',
      empty: '没有资料未核对的商品',
    },
    {
      key: '3',
      label: '营销待生成',
      tone: 'accent',
      empty: '没有待生成营销内容的商品',
    },
    {
      key: '4',
      label: '已完成',
      tone: 'green',
      empty: '还没有资料已核、启用的平台都写完（或标了不发布）的商品',
    },
  ],
};

/** 行状态：资料任务看资料阶段，营销任务看营销阶段 */
const rowStatus = (entry: PackageType, status: number) => {
  if (entry === 'facts') {
    return status === 1
      ? TABS.facts[1]
      : status === 2
        ? TABS.facts[2]
        : TABS.facts[3];
  }
  return status <= 2
    ? TABS.marketing[1]
    : status === 3
      ? TABS.marketing[2]
      : TABS.marketing[3];
};

const COPY: Record<PackageType, { description: string; footer: string }> = {
  facts: {
    description:
      '补商品资料（中文，所有公司共用一份）：下载资料包交给 AI，或直接上传 llm-wiki 商品文件；导入后有人核对。营销内容在「运营管理 → 营销任务」。',
    footer:
      '只收资料包和 llm-wiki 商品文件；「资料已核」会把未核实的中文规格和应用场景标为已核实，以后导入资料包不再覆盖；按更新时间倒序',
  },
  marketing: {
    description:
      '本公司各平台的营销内容：下载营销包交给 AI，生成好的 Markdown 传回来；启用的平台都写完（或标了不发布）、且资料已核才算完成。',
    footer:
      '只收营销包；资料包和 wiki 商品文件请到「运营管理 → 资料任务」上传；只列本公司启用的平台；按更新时间倒序',
  },
};

/** 资料任务与营销任务共用的任务列表，按入口切换页签、列和操作 */
export const TaskList: React.FC<{ entry: PackageType }> = ({ entry }) => {
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const facts = entry === 'facts';
  const tabs = TABS[entry];
  const copy = COPY[entry];
  const [search] = useSearchParams();
  const initialKeyword = search.get('keyword') ?? '';
  const [tab, setTab] = useState(initialKeyword ? 'all' : tabs[1].key);
  const [keyword, setKeyword] = useState(initialKeyword);
  const [brandId, setBrandId] = useState<number>();
  const [categoryId, setCategoryId] = useState<number>();
  const [query, setQuery] = useState<ContentTaskQuery>({
    keyword: initialKeyword || undefined,
  });
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = usePageSize(20);
  const [rows, setRows] = useState<ContentTask[]>([]);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [selected, setSelected] = useState<number[]>([]);
  const { download: downloadPackage, downloading } = useDownloadPackage();
  const [uploadOpen, setUploadOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  /** 行内上传、上传记录对应的商品 */
  const [rowUpload, setRowUpload] = useState<{ id: number; label: string }>();
  const [rowCombined, setRowCombined] = useState<{
    id: number;
    label: string;
  }>();
  const [rowRecords, setRowRecords] = useState<{ id: number; label: string }>();
  const platformState = usePlatforms(!facts);
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
        contentApi.page(entry, {
          page,
          pageSize,
          tab: tab === 'all' ? undefined : tab,
          ...query,
        }),
        contentApi.counts(entry, query),
      ]);
      setRows(res.records);
      setTotal(res.total);
      setCounts(c);
    } catch (e) {
      setError(readBizError(e).message);
    } finally {
      setLoading(false);
    }
  }, [entry, page, pageSize, tab, query]);

  useEffect(() => {
    load();
  }, [load]);

  const apply = () => {
    setQuery({ keyword: keyword.trim() || undefined, brandId, categoryId });
    setPage(1);
  };

  const download = async (ids: number[], platform?: MarketingPick) => {
    const stages = rows
      .filter((r) => ids.includes(r.productId))
      .map((r) => r.status);
    await downloadPackage(ids, entry, stages, platform);
    load();
  };

  const label = (t: ContentTask) => `${t.brandName ?? ''} · ${t.mpn}`;
  const muted = <span style={{ color: palette.mute }}>—</span>;

  const factsCell = (t: ContentTask) => {
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
  };

  const columns: TableColumnsType<ContentTask> = [
    {
      title: '商品',
      key: 'product',
      width: 260,
      fixed: 'left',
      render: (_, t) => (
        <div>
          <Link
            to={
              facts
                ? `/product/products/${t.productId}`
                : `/operations/products/${t.productId}`
            }
          >
            <b>{label(t)}</b>
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
      render: (v: number) => {
        const s = rowStatus(entry, v);
        return <Pill tone={s.tone}>{s.label}</Pill>;
      },
    },
    {
      title: facts ? '导入 / 核对（共享）' : '资料',
      key: 'facts',
      width: facts ? 190 : 110,
      render: (_, t) =>
        facts ? (
          factsCell(t)
        ) : factsVerified(t.status) ? (
          <span style={{ color: palette.green }}>已核对</span>
        ) : (
          <span style={{ color: palette.orange }}>
            {t.status === 1 ? '待补' : '待核'}
          </span>
        ),
    },
    ...(facts
      ? []
      : [
          {
            title: '营销（本公司）',
            key: 'marketing',
            width: 270,
            render: (_: unknown, t: ContentTask) => (
              <PlatformChips progress={t} platforms={platforms} />
            ),
          },
        ]),
    {
      title: '最近下载',
      key: 'downloaded',
      width: 150,
      render: (_, t) => {
        const at = facts ? t.factDownloadedAt : t.downloadedAt;
        return at ? (
          <span style={{ fontSize: 12 }}>{formatDateTime(at)}</span>
        ) : (
          muted
        );
      },
    },
    ...auditColumns<ContentTask>(),
    {
      title: '操作',
      key: 'actions',
      width: 300,
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
          {facts ? (
            <>
              <a onClick={() => download([t.productId])}>下载资料包</a>
              {!factsVerified(t.status) && access['product:content:edit'] && (
                <VerifyFactsButton
                  productId={t.productId}
                  label={label(t)}
                  link
                  onDone={load}
                />
              )}
            </>
          ) : (
            <>
              <Link to={`/operations/products/${t.productId}`}>详情</Link>
              <MarketingDownload
                link
                label="下载"
                platforms={platforms}
                onPick={(p) => download([t.productId], p)}
              />
            </>
          )}
          <a onClick={() => setRowUpload({ id: t.productId, label: label(t) })}>
            上传
          </a>
          <a
            onClick={() => setRowCombined({ id: t.productId, label: label(t) })}
          >
            资料+营销
          </a>
          <a
            onClick={() => setRowRecords({ id: t.productId, label: label(t) })}
          >
            上传记录
          </a>
        </span>
      ),
    },
  ];

  return (
    <div>
      <PageTitle
        description={copy.description}
        actions={
          <>
            {!facts && (
              <Button
                icon={<SettingOutlined />}
                onClick={() => setSettingsOpen(true)}
              >
                平台设置
              </Button>
            )}
            {facts ? (
              <Button
                icon={<DownloadOutlined />}
                disabled={selected.length === 0}
                loading={downloading === 'facts'}
                onClick={() => download(selected)}
              >
                下载资料包{selected.length ? ` · ${selected.length}` : ''}
              </Button>
            ) : (
              <MarketingDownload
                platforms={platforms}
                disabled={selected.length === 0}
                downloading={downloading}
                label={`下载营销包${selected.length ? ` · ${selected.length}` : ''}`}
                onPick={(p) => download(selected, p)}
              />
            )}
            <Button
              type="primary"
              icon={<UploadOutlined />}
              onClick={() => setUploadOpen(true)}
            >
              {facts ? '上传资料包' : '上传营销包'}
            </Button>
          </>
        }
      />
      {!facts && (
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
          {TABS.marketing.slice(1).map((s, i) => (
            <React.Fragment key={s.key}>
              {i > 0 && <ArrowRightOutlined style={{ fontSize: 10 }} />}
              <Pill tone={s.tone}>{s.label}</Pill>
            </React.Fragment>
          ))}
          <span style={{ marginLeft: 8 }}>
            资料在「商品管理 →
            资料任务」补和核对，所有公司共用；这里只管本公司的营销内容。资料未核对时也能先做营销，资料核对后才算完成。
          </span>
        </div>
      )}
      <div
        style={{
          display: 'flex',
          gap: 12,
          alignItems: 'center',
          flexWrap: 'wrap',
          marginBottom: 16,
        }}
      >
        <Segmented<string>
          value={tab}
          onChange={(t) => {
            setTab(t);
            setPage(1);
            setSelected([]);
          }}
          options={tabs.map((t) => ({
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
            scroll={{ x: facts ? 1900 : 2100 }}
            rowSelection={{
              selectedRowKeys: selected,
              onChange: (keys) => setSelected(keys as number[]),
              preserveSelectedRowKeys: true,
            }}
            locale={{
              emptyText: tabs.find((t) => t.key === tab)?.empty ?? '没有记录',
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
        {copy.footer}
      </div>
      {!facts && (
        <PlatformSettings
          open={settingsOpen}
          options={platformState.options}
          onClose={() => setSettingsOpen(false)}
          onSaved={(options) => {
            platformState.setOptions(options);
            load();
          }}
        />
      )}
      <UploadDrawer
        entry={entry}
        open={!!rowUpload}
        product={rowUpload}
        onClose={() => setRowUpload(undefined)}
        onDone={load}
      />
      <UploadDrawer
        entry="combined"
        open={!!rowCombined}
        product={rowCombined}
        onClose={() => setRowCombined(undefined)}
        onDone={load}
      />
      <ImportRecordsDrawer
        type={entry}
        product={rowRecords}
        onClose={() => setRowRecords(undefined)}
      />
      <UploadDrawer
        entry={entry}
        open={uploadOpen}
        onClose={() => setUploadOpen(false)}
        onDone={load}
      />
    </div>
  );
};
