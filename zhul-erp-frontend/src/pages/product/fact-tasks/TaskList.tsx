import { history, Link, useAccess, useSearchParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { App, Button, Input, Select, Table } from 'antd';
import {
  BadgeCheck,
  Check,
  Download,
  Eye,
  History,
  Layers,
  Search,
  Settings,
  Sparkles,
  SquareCheck,
  Upload,
  Zap,
} from 'lucide-react';
import React, { useCallback, useEffect, useState } from 'react';
import { useColumnSettings } from '@/components/ColumnSettings';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  HelpTip,
  MainCard,
  PageHeader,
  SelectionCount,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { ErrorHint } from '@/pages/product/components/EmptyHint';
import { Pill } from '@/pages/product/components/Pills';
import { auditColumns } from '@/pages/purchase/components';
import { useAppTheme } from '@/theme/AppTheme';
import { formatDateTime } from '@/utils/format';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { ImportRecordsDrawer } from '../products/cards/ImportRecords';
import {
  factsVerified,
  MarketingDownload,
  type MarketingPick,
  marketingPicks,
  PlatformChips,
  useDownloadPackage,
  usePlatforms,
  useVerifyFacts,
} from '../products/cards/SeoCard';
import {
  type BrandOption,
  brandApi,
  type CategoryOption,
  type ContentAiJob,
  type ContentTask,
  type ContentTaskQuery,
  categoryApi,
  contentApi,
  type PackageType,
  readBizError,
} from '../service';
import {
  AI_MAX_BATCH,
  AiCell,
  AiStartModal,
  aiBusy,
  aiWorking,
} from './AiGenerate';
import {
  AI_PENDING,
  BatchConfirmModal,
  BatchVerifyModal,
} from './BatchActions';
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
      tone: 'orange',
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

/** 帮助文字：help-texts.md「资料任务、营销任务」；关键词范围以 search-scope.md 为准 */
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
  const { message, modal } = App.useApp();
  const access = useAccess() as Record<string, boolean>;
  /** 发起、取消 AI 生成：能写资料包或营销包其中一份即可（确认时按权限分别写） */
  const canAi =
    !!access['product:content:edit'] || !!access['operations:marketing:edit'];
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
  /** AI 生成：确认发起的商品、查看结果的记录 */
  const [aiStart, setAiStart] = useState<ContentTask[]>();
  /** 批量确认 AI 结果、批量资料已核的商品 */
  const [batchConfirm, setBatchConfirm] = useState<ContentTask[]>();
  const [batchVerify, setBatchVerify] = useState<ContentTask[]>();
  const [aiResult, setAiResult] = useState<{
    jobId: number;
    id: number;
    label: string;
  }>();
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

  /** quiet：定时刷新 AI 生成状态时不显示加载中 */
  const load = useCallback(
    async (quiet?: boolean) => {
      if (!quiet) setLoading(true);
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
    },
    [entry, page, pageSize, tab, query],
  );

  useEffect(() => {
    load();
  }, [load]);

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

  // 有排队中、生成中的 AI 生成时，每 20 秒刷新一次状态
  const working = aiWorking(rows);
  useEffect(() => {
    if (!working) return undefined;
    const timer = window.setInterval(() => load(true), 20_000);
    return () => window.clearInterval(timer);
  }, [working, load]);

  const startOne = async (t: ContentTask) => {
    try {
      await contentApi.aiStart([t.productId]);
      message.success(`已开始 AI 生成 ${t.mpn}，生成在后台进行，可以离开页面`);
      load(true);
    } catch (e) {
      message.error(readBizError(e).message);
    }
  };

  const cancelAi = (job: ContentAiJob) =>
    modal.confirm({
      title: '取消这次 AI 生成？',
      content: '已经在生成的会照常跑完，但结果不保存；可以重新生成。',
      okText: '取消生成',
      cancelText: '不取消',
      onOk: async () => {
        try {
          await contentApi.aiCancel(job.id);
          load(true);
        } catch (e) {
          message.error(readBizError(e).message);
        }
      },
    });

  const selectedRows = rows.filter((r) => selected.includes(r.productId));
  const selectedPending = selectedRows.filter(
    (r) => r.ai?.status === AI_PENDING,
  ).length;

  const apply = () => {
    setQuery((q) => ({
      keyword: keyword.trim() || undefined,
      brandId,
      categoryId,
      ai: q.ai,
    }));
    setPage(1);
  };

  const verifyFacts = useVerifyFacts();

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
      width: 280,
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
          <div
            title={t.productName}
            style={{
              fontSize: 12,
              color: palette.mute,
              display: '-webkit-box',
              WebkitBoxOrient: 'vertical',
              WebkitLineClamp: 2,
              overflow: 'hidden',
            }}
          >
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
          <Pill tone="green">已核对</Pill>
        ) : (
          // 提醒用状态标签，不写彩色纯文字
          <Pill tone="orange">{t.status === 1 ? '待补' : '待核'}</Pill>
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
      title: 'AI 生成',
      key: 'ai',
      width: 200,
      render: (_, t) => (
        <AiCell
          job={t.ai}
          canEdit={canAi}
          onView={(job) =>
            setAiResult({ jobId: job.id, id: t.productId, label: label(t) })
          }
          onCancel={cancelAi}
          onRetry={() => startOne(t)}
        />
      ),
    },
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
      // 行内两个常用操作，其余收进「更多」（ui-redesign S6：原来一行 6 个文字链接）
      width: rowActionsWidth(3),
      fixed: 'right',
      render: (_, t) => {
        const name = label(t);
        return (
          <RowActions
            inline={[
              canAi &&
                !aiBusy(t.ai) && {
                  key: 'ai',
                  label: 'AI 生成',
                  icon: <Sparkles size={18} />,
                  onClick: () => startOne(t),
                },
              facts
                ? {
                    key: 'download',
                    label: '下载资料包',
                    icon: <Download size={18} />,
                    onClick: () => download([t.productId]),
                  }
                : {
                    key: 'detail',
                    label: '详情',
                    icon: <Eye size={18} />,
                    onClick: () =>
                      history.push(`/operations/products/${t.productId}`),
                  },
            ]}
            more={[
              facts &&
                !factsVerified(t.status) &&
                access['product:content:edit'] && {
                  key: 'verify',
                  label: '资料已核',
                  icon: <BadgeCheck size={16} />,
                },
              ...(facts
                ? []
                : marketingPicks(platforms).map((p) => ({
                    key: `dl:${p.key}`,
                    label: `下载 · ${p.name}`,
                    icon: <Download size={16} />,
                  }))),
              { key: 'upload', label: '上传', icon: <Upload size={16} /> },
              {
                key: 'combined',
                label: '资料+营销',
                icon: <Layers size={16} />,
              },
              {
                key: 'records',
                label: '上传记录',
                icon: <History size={16} />,
              },
            ]}
            onMore={(key) => {
              if (key === 'verify') verifyFacts(t.productId, name, load);
              if (key.startsWith('dl:'))
                download([t.productId], key.slice(3) as MarketingPick);
              if (key === 'upload')
                setRowUpload({ id: t.productId, label: name });
              if (key === 'combined')
                setRowCombined({ id: t.productId, label: name });
              if (key === 'records')
                setRowRecords({ id: t.productId, label: name });
            }}
          />
        );
      },
    },
  ];
  // 营销模式多了「营销（本公司）」一列：「资料」列与状态重复，默认收进「列」，1366 宽放得下（列表页统一规范第 2 条）
  const columnsSet = useColumnSettings(
    facts ? 'product-fact-tasks-TaskList' : 'operations-marketing-TaskList',
    columns,
    {
      hidden: facts
        ? ['品牌', '品类', '最近下载']
        : ['品牌', '品类', '最近下载', '资料'],
    },
  );

  return (
    <div>
      <PageHeader
        help={
          facts
            ? copy.description
            : `${copy.description}流程：${TABS.marketing
                .slice(1)
                .map((t) => t.label)
                .join(
                  ' → ',
                )}。资料在「商品管理 → 资料任务」补和核对，所有公司共用；这里只管本公司的营销内容。资料未核对时也能先做营销，资料核对后才算完成。`
        }
        extra={
          <>
            {!facts && (
              <Button
                type="text"
                icon={<Settings size="1em" />}
                style={{ color: palette.link }}
                onClick={() => setSettingsOpen(true)}
              >
                平台设置
              </Button>
            )}
            <Button
              type="primary"
              icon={<Upload size="1em" />}
              onClick={() => setUploadOpen(true)}
            >
              {facts ? '上传资料包' : '上传营销包'}
            </Button>
          </>
        }
      />
      <FilterCard
        onSearch={apply}
        onReset={() => {
          setKeyword('');
          setBrandId(undefined);
          setCategoryId(undefined);
          setQuery({});
          setTab(tabs[1].key);
          setPage(1);
          setSelected([]);
        }}
        moreCount={
          [query.brandId, query.categoryId].filter((v) => v != null).length
        }
        more={
          <>
            <FilterField label="品牌">
              <Select
                allowClear
                showSearch={{ optionFilterProp: 'label' }}
                value={brandId}
                onChange={setBrandId}
                placeholder="全部"
                options={brands.map((b) => ({
                  value: b.id,
                  label: b.brandName,
                }))}
                aria-label="品牌"
              />
            </FilterField>
            <FilterField label="品类">
              <Select
                allowClear
                showSearch={{ optionFilterProp: 'label' }}
                value={categoryId}
                onChange={setCategoryId}
                placeholder="全部"
                options={categories.map((c) => ({
                  value: c.id,
                  label: c.categoryNameZh || c.categoryName,
                }))}
                aria-label="品类"
              />
            </FilterField>
          </>
        }
      >
        <FilterField label="关键词" span={2}>
          <Input
            allowClear
            prefix={<Search size={16} />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={apply}
            placeholder="型号、名称"
            aria-label="关键词"
          />
        </FilterField>
        <FilterField label="状态">
          {/* 原来的状态页签，选项带数量，切换后立即查询 */}
          <Select<string>
            value={tab}
            onChange={(t) => {
              setTab(t);
              setPage(1);
              setSelected([]);
            }}
            options={tabs.map((t) => ({
              value: t.key,
              // 为 0 时只写名称；「全部」不是状态，不带数量
              label:
                counts[t.key] && t.key !== 'all'
                  ? `${t.label} · ${counts[t.key]}`
                  : t.label,
            }))}
            aria-label="状态"
          />
        </FilterField>
        <FilterField label="AI 生成">
          <Select
            allowClear
            value={query.ai}
            onChange={(v) => {
              setQuery((q) => ({ ...q, ai: v }));
              setPage(1);
              setSelected([]);
            }}
            placeholder="全部"
            options={[
              { value: 'pending', label: '待确认' },
              { value: 'running', label: '排队或生成中' },
              { value: 'failed', label: '失败' },
            ]}
            aria-label="AI 生成"
          />
        </FilterField>
      </FilterCard>
      {error ? (
        <ErrorHint message={error} onRetry={() => load()} />
      ) : (
        <MainCard
          settings={columnsSet.settings}
          left={
            selected.length > 0 ? (
              <SelectionCount
                count={selected.length}
                unit="个"
                onClear={() => setSelected([])}
              />
            ) : (
              <>
                {`共 ${total} 条`}
                <HelpTip title={copy.footer} />
              </>
            )
          }
          // 批量操作随勾选出现（ui-design-patterns 原则 10）
          right={
            selected.length > 0 && (
              <>
                {canAi && (
                  <Button
                    icon={<Zap size="1em" />}
                    disabled={selected.length > AI_MAX_BATCH}
                    title={
                      selected.length > AI_MAX_BATCH
                        ? `一次最多 AI 生成 ${AI_MAX_BATCH} 个商品`
                        : undefined
                    }
                    onClick={() => setAiStart(selectedRows)}
                  >
                    AI 生成
                  </Button>
                )}
                {facts ? (
                  <Button
                    icon={<Download size="1em" />}
                    loading={downloading === 'facts'}
                    onClick={() => download(selected)}
                  >
                    下载资料包
                  </Button>
                ) : (
                  <MarketingDownload
                    platforms={platforms}
                    downloading={downloading}
                    label="下载营销包"
                    onPick={(p) => download(selected, p)}
                  />
                )}
                {facts && access['product:content:edit'] && (
                  <Button
                    icon={<Check size="1em" />}
                    onClick={() => setBatchVerify(selectedRows)}
                  >
                    资料已核
                  </Button>
                )}
                {canAi && (
                  <Button
                    icon={<SquareCheck size="1em" />}
                    disabled={selectedPending === 0}
                    title={
                      !selectedPending
                        ? '勾选的商品没有待确认的 AI 结果'
                        : undefined
                    }
                    onClick={() => setBatchConfirm(selectedRows)}
                  >
                    确认 AI 结果{selectedPending ? ` · ${selectedPending}` : ''}
                  </Button>
                )}
              </>
            )
          }
        >
          <Table<ContentTask>
            rowKey="productId"
            columns={columnsSet.columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: tableScrollX(columnsSet.columns) }}
            rowSelection={{
              selectedRowKeys: selected,
              onChange: (keys) => setSelected(keys as number[]),
              preserveSelectedRowKeys: true,
            }}
            locale={{
              emptyText: tableEmpty(
                tabs.find((t) => t.key === tab)?.empty ?? '没有记录',
                loading,
              ),
            }}
            pagination={{
              ...PAGER_STYLE,
              current: page,
              pageSize,
              total,
              onChange: (p: number, s: number) => {
                setPage(p);
                setPageSize(s);
              },
            }}
          />
        </MainCard>
      )}
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
      <BatchConfirmModal
        rows={batchConfirm}
        onClose={() => setBatchConfirm(undefined)}
        onDone={() => {
          setSelected([]);
          load();
        }}
      />
      <BatchVerifyModal
        rows={batchVerify}
        onClose={() => setBatchVerify(undefined)}
        onDone={() => {
          setSelected([]);
          load();
        }}
      />
      <AiStartModal
        rows={aiStart}
        onClose={() => setAiStart(undefined)}
        onStarted={() => {
          setSelected([]);
          load(true);
        }}
      />
      <UploadDrawer
        entry="combined"
        open={!!aiResult}
        product={aiResult}
        aiJobId={aiResult?.jobId}
        onClose={() => setAiResult(undefined)}
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
