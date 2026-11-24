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
  Spin,
  Table,
  Tooltip,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { ArchiveRestore, Bot, Check, Plus, Search, X } from 'lucide-react';
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import EllipsisText from '@/components/EllipsisText';
import { tableEmpty } from '@/components/EmptyState';
import {
  FilterCard,
  FilterField,
  MainCard,
  PageHeader,
  QuickFilters,
} from '@/components/ListLayout';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { RowActions, rowActionsWidth } from '@/components/RowActions';
import { StatusSwitch } from '@/components/StatusSwitch';
import { auditColumns } from '@/pages/purchase/components';
import { formatDateTime } from '@/utils/format';
import { PAGER_STYLE, usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { CompletenessBar } from '../components/CompletenessBar';
import { EmptyHint, ErrorHint } from '../components/EmptyHint';
import { BrandMark, LifecyclePill, Pill } from '../components/Pills';
import { LIFECYCLE_OPTIONS, normalizeMpn } from '../constants';
import {
  AI_TASK_NOT_FOUND,
  type BrandOption,
  brandApi,
  type Category,
  type CategoryOption,
  type CompletenessSummary,
  categoryApi,
  type Product,
  type ProductQuery,
  productApi,
  type RefineResult,
  type RefineState,
  type RefineTask,
  readBizError,
  refineApi,
  type SeriesOption,
  seriesApi,
} from '../service';
import { ProductThemeProvider, useProductTheme } from '../theme';
import {
  AiSuggestion,
  REFINE_MAX,
  RechooseSelect,
  RefineTabs,
  readRefineJob,
  refineSummary,
  SkippedList,
  writeRefineJob,
} from './refine';

const FILTER_KEY = 'zhul_product_list_filters';

/** 帮助文字：help-texts.md「商品档案」，关键词范围以 search-scope.md 为准 */
export const HELP = {
  page: '一个「品牌 + 型号」就是一个商品。所有租户共用同一份，询盘、报价都从这里选',
  keyword:
    '可搜：商品名称、型号、订货号；型号忽略空格、连字符和大小写。输入即搜，在页面上按 / 键直接开始输入',
} as const;

interface Filters {
  categoryId?: number;
  brandId?: number;
  seriesId?: number;
  lifecycleStatus?: number;
  status?: number;
  includeDeleted?: boolean;
  missing?: string;
  /** 缺项为「品类未细分」时的三档 */
  refineState?: RefineState;
  /** 只看本公司维护的（租户） */
  mine?: boolean;
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
  { key: 'refine', label: '品类未细分', field: 'missingRefine' },
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
  const [pageSize, setPageSize] = usePageSize(20);
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
  /** 品类未细分（spec product/category-refine）：当前三档、运行中的 AI 细分、跑完的结果、改选中的行 */
  const refine = filters.missing === 'refine';
  const refineState: RefineState = filters.refineState ?? 'todo';
  const [aiJob, setAiJob] = useState<RefineTask | null>(readRefineJob);
  const [aiDone, setAiDone] = useState<RefineTask | null>(null);
  const [subCategories, setSubCategories] = useState<CategoryOption[]>([]);
  const [rechoose, setRechoose] = useState<{ id: number; value?: number }>();

  // 输入即搜，防抖 300ms
  useEffect(() => {
    const t = setTimeout(() => {
      setDebounced(keyword.trim());
      setPage(1);
      // 换了查询结果就清掉勾选，免得批量操作作用到已看不见的商品（UI-029）
      setSelected([]);
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
    productApi
      .summary()
      .then(setSummary)
      .catch(() => {});
  }, []);

  useEffect(loadSummary, [loadSummary]);

  // 改选要用到细分品类，进入「品类未细分」时才取
  useEffect(() => {
    if (!refine || subCategories.length) return;
    categoryApi
      .options(0)
      .then(setSubCategories)
      .catch(() => {});
  }, [refine, subCategories.length]);

  /** 需求热度排序在服务端做（跨页有效）：询盘次数或成交次数倒序 */
  const [heatSort, setHeatSort] = useState<'inquiryCount' | 'dealCount'>();

  const query = useMemo<ProductQuery>(
    () => ({
      page,
      pageSize,
      keyword: debounced || undefined,
      ...filters,
      includeDeleted: filters.includeDeleted || undefined,
      mine: filters.mine || undefined,
      sort: heatSort,
    }),
    [page, pageSize, debounced, filters, heatSort],
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

  // 从其他页面返回时列表和顶部计数一起重新取（audit UI-055）
  useOnReactivate(() => {
    load();
    loadSummary();
  });

  // 轮询回调用最新的取数函数：任务跑完时按当前的筛选、页码刷新（audit UI-083）
  const reload = useRef(() => {});
  reload.current = () => {
    load();
    loadSummary();
  };

  // antd 表格为测量列宽渲染了一行 aria-hidden 的隐藏行，行里带一个"全选"复选框，键盘会 Tab 到它。
  // 组件库没有提供关闭的选项，这里把隐藏行里的输入从 Tab 顺序里拿掉。
  useEffect(() => {
    for (const el of document.querySelectorAll<HTMLElement>(
      '.ant-table-measure-row input',
    )) {
      el.tabIndex = -1;
    }
  }, [rows, loading, firstLoad]);

  // 轮询运行中的 AI 细分；跑完刷新列表与数量并提示结果
  useEffect(() => {
    if (!aiJob?.taskId) return;
    const taskId = aiJob.taskId;
    const timer = setInterval(async () => {
      try {
        const r = await refineApi.status(taskId);
        if (r.status !== 3 && r.status !== 4) return;
        setAiJob(null);
        writeRefineJob(null);
        if (r.status === 4) {
          message.error(
            `AI 细分失败：${r.errorMessage || '未知原因'}，商品没有变化`,
          );
          return;
        }
        setAiDone({ ...r, skipped: aiJob.skipped });
        reload.current();
      } catch (e) {
        // 只有任务确实查不到（如换了账号）才停止跟踪；网络抖动、5xx 等下次继续查（audit UI-084）
        if (readBizError(e).errorCode !== AI_TASK_NOT_FOUND) return;
        setAiJob(null);
        writeRefineJob(null);
      }
    }, 3000);
    return () => clearInterval(timer);
  }, [aiJob?.taskId]);

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
    setSelected([]);
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

  /** 发起 AI 细分：有勾选处理勾选的，没有勾选按当前筛选取「待 AI 细分」的前 200 个（没跑过的优先） */
  const startRefine = () => {
    if (selected.length > REFINE_MAX) {
      message.warning(`每次最多 ${REFINE_MAX} 个，已选 ${selected.length} 个`);
      return;
    }
    const picked = selected.length;
    const count = picked || Math.min(total, REFINE_MAX);
    const rest = picked ? 0 : Math.max(0, total - REFINE_MAX);
    modal.confirm({
      title: 'AI 细分品类',
      icon: <Bot size="1em" style={{ color: palette.link }} />,
      width: 480,
      content: (
        <div style={{ display: 'grid', gap: 8 }}>
          <div>
            本次处理 <b className="num">{count}</b> 个商品
            {picked ? '（勾选的商品）' : ''}
            {rest
              ? `：当前筛选共 ${total} 个，先跑 ${count} 个，其余下次再跑`
              : ''}
            。
          </div>
          <div style={{ color: palette.sub }}>
            AI 只给建议，确认后才改商品品类；后台运行，可以离开页面。
          </div>
        </div>
      ),
      okText: '开始细分',
      cancelText: '取消',
      onOk: async () => {
        try {
          const { page: _p, pageSize: _s, sort: _o, ...filter } = query;
          const r = await refineApi.submit(
            picked ? { ids: selected.map(Number) } : { filter },
          );
          setAiDone(null);
          if (!r.taskId) {
            message.info(
              '选中的商品都不需要细分（已细分、已有建议、已忽略或由其他公司维护）',
            );
            return;
          }
          setAiJob(r);
          writeRefineJob(r);
          setSelected([]);
        } catch (e) {
          const msg = readBizError(e).message;
          if (msg.includes('AI 未配置')) {
            modal.confirm({
              title: '还没有配置 AI',
              content: '本公司配置 AI 后才能细分品类。',
              okText: '去 AI 设置',
              cancelText: '取消',
              onOk: () => history.push('/system/ai-settings'),
            });
          } else {
            message.warning(msg);
          }
        }
      },
    });
  };

  /** 采纳 / 忽略 / 撤销忽略之后：有跳过的逐条说明，刷新列表与数量 */
  const afterRefine = (verb: string, r: RefineResult) => {
    if (r.skipped.length) {
      modal.warning({
        title: `已${verb} ${r.done} 个，跳过 ${r.skipped.length} 个`,
        content: (
          <>
            <div style={{ marginBottom: 8 }}>以下商品没有改动：</div>
            <SkippedList result={r} />
          </>
        ),
        okText: '知道了',
      });
    } else {
      message.success(`已${verb} ${r.done} 个`);
    }
    setSelected([]);
    setRechoose(undefined);
    load();
    loadSummary();
  };

  /** 采纳建议：跨一级品类的单独列出，确认后才保存 */
  const acceptRows = (picked: Product[]) => {
    const cross = picked.filter((r) => r.aiCategoryCross);
    const run = async () =>
      afterRefine('采纳', await refineApi.accept(picked.map((r) => r.id)));
    if (picked.length === 1 && !cross.length) {
      run();
      return;
    }
    modal.confirm({
      title:
        picked.length === 1
          ? `采纳「${picked[0].mpnDisplay}」的 AI 建议？`
          : `采纳 ${picked.length} 个商品的 AI 建议？`,
      width: 560,
      content: (
        <div style={{ display: 'grid', gap: 12 }}>
          <div>采纳后商品品类改为 AI 建议的细分品类。</div>
          {cross.length > 0 && (
            <div
              style={{
                padding: 12,
                borderRadius: 12,
                background: palette.orangeSoft,
                display: 'grid',
                gap: 6,
              }}
            >
              <b style={{ color: palette.orange }}>
                其中 {cross.length} 个会改一级品类
              </b>
              <div
                style={{
                  maxHeight: 200,
                  overflow: 'auto',
                  display: 'grid',
                  gap: 4,
                }}
              >
                {cross.map((r) => (
                  <div key={r.id} style={{ display: 'flex', gap: 8 }}>
                    <b style={{ minWidth: 170 }}>{r.mpnDisplay}</b>
                    <span style={{ color: palette.sub }}>
                      {r.categoryNameZh || r.categoryName}
                    </span>
                    →<b>{r.aiCategoryName}</b>
                  </div>
                ))}
              </div>
              {picked.length > 1 && (
                <span style={{ color: palette.sub, fontSize: 12 }}>
                  不确定的可以先取消勾选，逐个改选。
                </span>
              )}
            </div>
          )}
        </div>
      ),
      okText: picked.length === 1 ? '采纳' : `采纳 ${picked.length} 个`,
      cancelText: '取消',
      onOk: run,
    });
  };

  const ignoreRows = async (ids: number[]) =>
    afterRefine('忽略', await refineApi.ignore(ids));
  const unignoreRows = async (ids: number[]) =>
    afterRefine('撤销忽略', await refineApi.unignore(ids));

  /** 改选：效果同手动改品类（建议一并清除） */
  const saveRechoose = async (r: Product) => {
    const categoryId = rechoose?.value;
    if (!categoryId) {
      message.warning('请选择细分品类');
      return;
    }
    // 保存接口按整条写入：先取最新资料，只换品类，不用列表里的旧值覆盖别人刚改的名称、简介、生命周期（audit BUS-067）
    const p = await productApi.get(r.id);
    await productApi.update(r.id, {
      brandId: p.brandId,
      categoryId,
      seriesId: p.seriesId ?? null,
      mpnRaw: p.mpnRaw,
      mpnDisplay: p.mpnDisplay,
      productName: p.productName,
      shortDescription: p.shortDescription,
      specSummary: p.specSummary,
      lifecycleStatus: p.lifecycleStatus,
      lifecycleSource: p.lifecycleSource,
    });
    const name = subCategories.find((c) => c.id === categoryId);
    message.success(
      `「${r.mpnDisplay}」已改为 ${name?.categoryNameZh || name?.categoryName || '所选品类'}`,
    );
    setRechoose(undefined);
    load();
    loadSummary();
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

  /** 待确认建议、已忽略两档只留判断品类要看的列（原型 R4、R7），关键的 AI 建议列不被挤出视野 */
  const reviewing = refine && refineState !== 'todo';
  const columns: ColumnsType<Product> = [
    {
      title: '商品',
      key: 'product',
      width: 280,
      // 两行：型号（主信息，可点）+ 产品名称；长规格摘要只在悬停时看（P14）。
      // 不再放品类首字母方块：没有图片时它不传达任何信息，还占掉一截宽度
      render: (_, r) => (
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
          <Tooltip
            title={
              r.productName || r.specSummary ? (
                <>
                  {r.productName && <div>{r.productName}</div>}
                  {r.specSummary && <div>{r.specSummary}</div>}
                </>
              ) : undefined
            }
          >
            <div
              style={{
                color: palette.mute,
                fontSize: 12,
                maxWidth: 250,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {r.productName || r.specSummary || '—'}
            </div>
          </Tooltip>
        </div>
      ),
    },
    {
      title: '品牌',
      dataIndex: 'brandName',
      width: 150,
      render: (_, r) => (
        <div
          style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}
        >
          <BrandMark name={r.brandName} size={22} />
          <EllipsisText text={r.brandName} />
        </div>
      ),
    },
    {
      title: refine ? '当前品类' : '品类',
      dataIndex: 'categoryName',
      width: 130,
      // 单行省略：长品类名不压到右边的生命周期标签上；品类未细分时「其他」标橙
      render: (v, r) =>
        refine && r.categoryCode === 'other' ? (
          <b style={{ color: palette.orange }}>{r.categoryNameZh || v}</b>
        ) : (
          <EllipsisText text={r.categoryNameZh || v} />
        ),
    },
    ...(refine && refineState === 'pending'
      ? ([
          {
            title: 'AI 建议品类',
            key: 'aiCategory',
            width: 220,
            render: (_: unknown, r: Product) =>
              rechoose?.id === r.id ? (
                <RechooseSelect
                  row={r}
                  categories={subCategories}
                  value={rechoose.value}
                  onChange={(v) => setRechoose({ id: r.id, value: v })}
                />
              ) : (
                <AiSuggestion row={r} />
              ),
          },
        ] as ColumnsType<Product>)
      : []),
    {
      title: '生命周期',
      dataIndex: 'lifecycleStatus',
      width: 120,
      render: (v) => <LifecyclePill value={v} />,
    },
    ...(
      (reviewing ? ['inquiryCount'] : ['inquiryCount', 'dealCount']) as (
        | 'inquiryCount'
        | 'dealCount'
      )[]
    ).map((key) => ({
      title: key === 'inquiryCount' ? '询盘次数' : '成交次数',
      key,
      width: 120,
      sorter: true,
      sortDirections: ['descend' as const],
      sortOrder: heatSort === key ? ('descend' as const) : null,
      render: (_: unknown, r: Product) => {
        const n = r[key] ?? 0;
        return (
          <span
            className="num"
            style={{
              fontWeight: 600,
              color: !n
                ? palette.mute
                : key === 'dealCount'
                  ? palette.green
                  : palette.ink,
            }}
          >
            {n}
          </span>
        );
      },
    })),
    ...(reviewing
      ? []
      : ([
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
        ] as ColumnsType<Product>)),
    {
      title: '维护方',
      key: 'owner',
      width: 90,
      render: (_: unknown, r: Product) =>
        !r.ownerTenantId ? (
          <Pill tone="gray">平台</Pill>
        ) : platform ? (
          <Pill tone="gray">租户</Pill>
        ) : r.editable ? (
          <Pill tone="gray">本公司</Pill>
        ) : (
          <Pill tone="gray">其他公司</Pill>
        ),
    },
    ...(reviewing
      ? ([
          {
            title: refineState === 'pending' ? '建议时间' : '忽略时间',
            dataIndex: 'aiCategoryAt',
            width: 170,
            render: (v?: string) => formatDateTime(v),
          },
        ] as ColumnsType<Product>)
      : []),
    ...(reviewing
      ? []
      : ([
          {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            render: (_, r) =>
              canEdit && r.editable && !r.deleted ? (
                <span onKeyDown={(e) => e.stopPropagation()}>
                  <StatusSwitch
                    checked={r.status === 1}
                    name={r.mpnDisplay}
                    onChange={() => changeStatus(r)}
                  />
                </span>
              ) : r.status === 1 ? (
                <Pill tone="green">启用</Pill>
              ) : (
                <Pill tone="gray">已停用</Pill>
              ),
          },
        ] as ColumnsType<Product>)),
    ...(reviewing ? [] : auditColumns<Product>()),
    ...(refine && refineState !== 'todo' && canEdit
      ? ([
          {
            // lint-ui-ignore row-actions: AI 细分结果的采纳、改选、忽略队列，保留文字按钮
            title: '操作',
            fixed: 'right',
            key: 'refineOp',
            width: refineState === 'pending' ? 170 : 100,
            render: (_: unknown, r: Product) => {
              const link = (
                text: string,
                onClick: () => void,
                muted?: boolean,
              ) => (
                <button
                  type="button"
                  className="zhul-link"
                  style={muted ? { color: palette.mute } : undefined}
                  onClick={(e) => {
                    e.stopPropagation();
                    onClick();
                  }}
                >
                  {text}
                </button>
              );
              if (!r.editable)
                return <span style={{ color: palette.mute }}>只读</span>;
              if (refineState === 'ignored')
                return link('撤销忽略', () => unignoreRows([r.id]));
              if (rechoose?.id === r.id) {
                return (
                  <span style={{ display: 'inline-flex', gap: 14 }}>
                    {link('保存', () => saveRechoose(r))}
                    {link('取消', () => setRechoose(undefined), true)}
                  </span>
                );
              }
              return (
                <span style={{ display: 'inline-flex', gap: 14 }}>
                  {link('采纳', () => acceptRows([r]))}
                  {link('改选', () => setRechoose({ id: r.id }))}
                  {link('忽略', () => ignoreRows([r.id]), true)}
                </span>
              );
            },
          },
        ] as ColumnsType<Product>)
      : []),
    // 操作列只有「恢复」（已删除的商品）：列表里没有已删除的商品时不显示这一整列空白（audit UI-041）
    ...(canEdit && rows.some((r) => r.deleted)
      ? ([
          {
            title: '操作',
            fixed: 'right',
            key: 'op',
            width: rowActionsWidth(1),
            render: (_: unknown, r: Product) =>
              r.deleted ? (
                <RowActions
                  inline={[
                    {
                      key: 'restore',
                      label: '恢复',
                      icon: <ArchiveRestore size={18} />,
                      onClick: () => restore(r),
                    },
                  ]}
                />
              ) : null,
          },
        ] as ColumnsType<Product>)
      : []),
  ];

  const normalized = normalizeMpn(debounced);
  const hasCondition =
    !!debounced ||
    Object.values(filters).some((v) => v !== undefined && v !== false);

  return (
    <>
      <PageHeader
        help={HELP.page}
        extra={
          access['product:product:add'] && (
            <Button
              type="primary"
              icon={<Plus size="1em" />}
              onClick={() => history.push('/product/products/new')}
            >
              新建商品
            </Button>
          )
        }
      />
      {aiDone && (
        <div
          role="status"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            padding: '12px 16px',
            marginBottom: 16,
            borderRadius: 12,
            background: palette.card,
            border: `1px solid ${palette.hairline}`,
          }}
        >
          <Check size="1em" style={{ color: palette.green }} />
          <b style={{ color: palette.ink }}>AI 细分完成</b>
          <span style={{ color: palette.sub }}>{refineSummary(aiDone)}</span>
          <span style={{ flex: 1 }} />
          {!!aiDone.suggested && (
            <button
              type="button"
              className="zhul-link"
              onClick={() => {
                updateFilters({ missing: 'refine', refineState: 'pending' });
                setAiDone(null);
              }}
            >
              去确认建议
            </button>
          )}
          <Button
            type="text"
            size="small"
            icon={<X size="1em" />}
            onClick={() => setAiDone(null)}
            aria-label="关闭结果提示"
          />
        </div>
      )}

      <FilterCard
        onSearch={() => {
          // 输入本来就即搜；点查询或回车时不等防抖，马上按当前关键词查
          setDebounced(keyword.trim());
          setPage(1);
          setSelected([]);
        }}
        onReset={clearAll}
        moreCount={
          [
            filters.seriesId,
            filters.lifecycleStatus,
            filters.status,
            (platform ? filters.includeDeleted : filters.mine) || undefined,
          ].filter((v) => v != null).length
        }
        more={
          <>
            <FilterField label="系列">
              <Select
                allowClear
                placeholder={filters.brandId ? '全部' : '先选品牌'}
                aria-label="系列"
                disabled={!filters.brandId}
                value={filters.seriesId}
                options={seriesOptions.map((s) => ({
                  value: s.id,
                  label: s.seriesName,
                }))}
                onChange={(v) => updateFilters({ seriesId: v })}
              />
            </FilterField>
            <FilterField label="生命周期">
              <Select
                allowClear
                placeholder="全部"
                aria-label="生命周期"
                value={filters.lifecycleStatus}
                options={LIFECYCLE_OPTIONS}
                onChange={(v) => updateFilters({ lifecycleStatus: v })}
              />
            </FilterField>
            <FilterField label="状态">
              <Select
                allowClear
                placeholder="全部"
                aria-label="状态"
                value={filters.status}
                options={[
                  { value: 1, label: '启用' },
                  { value: 0, label: '已停用' },
                ]}
                onChange={(v) => updateFilters({ status: v })}
              />
            </FilterField>
            {platform ? (
              <FilterField label="已删除">
                <Checkbox
                  checked={!!filters.includeDeleted}
                  onChange={(e) =>
                    updateFilters({ includeDeleted: e.target.checked })
                  }
                >
                  一并显示
                </Checkbox>
              </FilterField>
            ) : (
              <FilterField label="维护方">
                <Checkbox
                  checked={!!filters.mine}
                  onChange={(e) => updateFilters({ mine: e.target.checked })}
                >
                  只看本公司维护
                </Checkbox>
              </FilterField>
            )}
          </>
        }
      >
        <FilterField label="关键词" span={2} help={HELP.keyword}>
          <Input
            ref={searchRef}
            allowClear
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={() => {
              setDebounced(keyword.trim());
              setPage(1);
              setSelected([]);
            }}
            prefix={<Search size={16} />}
            suffix={<kbd style={{ fontSize: 12, color: palette.mute }}>/</kbd>}
            placeholder="型号、产品名称、订货号"
            aria-label="搜索商品"
          />
        </FilterField>
        <FilterField label="品类">
          <Select
            allowClear
            showSearch={{ optionFilterProp: 'label' }}
            placeholder="全部"
            aria-label="品类"
            value={filters.categoryId}
            options={categories.map((c) => ({
              value: c.id,
              label: `${c.categoryName} · ${c.productCount}`,
            }))}
            onChange={(v) => updateFilters({ categoryId: v })}
          />
        </FilterField>
        <FilterField label="品牌">
          <Select
            allowClear
            showSearch={{ optionFilterProp: 'label' }}
            placeholder="全部"
            aria-label="品牌"
            value={filters.brandId}
            options={brands.map((b) => ({ value: b.id, label: b.brandName }))}
            onChange={(v) => updateFilters({ brandId: v, seriesId: undefined })}
          />
        </FilterField>
      </FilterCard>

      <MainCard
        left={
          (platform || refine) && selected.length > 0 ? (
            <>
              <b style={{ color: palette.link }}>
                已选 <span className="num">{selected.length}</span> 项
              </b>
              <Button
                type="text"
                size="small"
                style={{ color: palette.link }}
                onClick={() => setSelected([])}
              >
                取消选择
              </Button>
            </>
          ) : (
            <>
              {`共 ${total} 条`}
              {normalized && normalized !== debounced.toLowerCase() && (
                <span>
                  · 已按「{normalized}」匹配（忽略空格、连字符和大小写）
                </span>
              )}
            </>
          )
        }
        right={
          (platform || refine) &&
          selected.length > 0 && (
            <>
              {platform && canEdit && (
                <Button onClick={() => batchStatus(1)}>批量启用</Button>
              )}
              {platform && canEdit && (
                <Button onClick={() => batchStatus(0)}>批量停用</Button>
              )}
              {platform && <Button onClick={exportSelected}>导出</Button>}
              {refine && canEdit && refineState === 'pending' && (
                <>
                  <Button onClick={() => ignoreRows(selected.map(Number))}>
                    忽略
                  </Button>
                  <Button
                    type="primary"
                    icon={<Check size="1em" />}
                    onClick={() =>
                      acceptRows(rows.filter((r) => selected.includes(r.id)))
                    }
                  >
                    采纳建议
                  </Button>
                </>
              )}
              {refine && canEdit && refineState === 'ignored' && (
                <Button onClick={() => unignoreRows(selected.map(Number))}>
                  撤销忽略
                </Button>
              )}
            </>
          )
        }
        quick={
          summary && (
            <div style={{ display: 'grid', gap: 12 }}>
              {/* 缺项是条件类，原来的「档案还有可以补充的地方」改为快捷筛选（互斥，再点取消） */}
              <QuickFilters
                label="档案缺项"
                options={MISSING_CHIPS.map((c) => ({
                  value: c.key,
                  label: c.label,
                  count: summary[c.field] ?? 0,
                }))}
                value={filters.missing ? [filters.missing] : []}
                onChange={(v) => {
                  const next = v.find((x) => x !== filters.missing);
                  updateFilters({
                    missing: next,
                    refineState: next === 'refine' ? 'todo' : undefined,
                  });
                  setRechoose(undefined);
                }}
              />
              {refine && (
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 12,
                    flexWrap: 'wrap',
                  }}
                >
                  <RefineTabs
                    summary={summary}
                    value={refineState}
                    onChange={(v) => {
                      updateFilters({ refineState: v });
                      setRechoose(undefined);
                    }}
                  />
                  {canEdit &&
                    refineState === 'todo' &&
                    (aiJob?.taskId ? (
                      <span
                        role="status"
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 10,
                          height: 32,
                          color: palette.sub,
                        }}
                      >
                        <Spin size="small" />
                        <b style={{ color: palette.ink }}>AI 细分中</b>
                        <span className="num">共 {aiJob.total} 个</span>
                      </span>
                    ) : (
                      <Button
                        icon={<Bot size="1em" />}
                        disabled={!total && !selected.length}
                        onClick={startRefine}
                      >
                        AI 细分品类
                      </Button>
                    ))}
                </div>
              )}
            </div>
          )
        }
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
            scroll={{ x: tableScrollX(columns) }}
            rowSelection={
              platform || (refine && canEdit)
                ? {
                    selectedRowKeys: selected,
                    onChange: setSelected,
                    // antd 的类型里没有 aria-label，但会透传给复选框；读屏靠它知道选的是哪一行
                    // 品类未细分时租户只能勾选本公司维护的商品
                    getCheckboxProps: (r: Product) =>
                      ({
                        'aria-label': `选择 ${r.mpnDisplay}`,
                        disabled: !platform && !r.editable,
                      }) as unknown as Partial<CheckboxProps>,
                  }
                : undefined
            }
            onChange={(_p, _f, sorter, extra) => {
              const s = Array.isArray(sorter) ? sorter[0] : sorter;
              if (
                extra.action === 'sort' &&
                (s?.columnKey === 'inquiryCount' ||
                  s?.columnKey === 'dealCount')
              ) {
                setHeatSort(s.order ? s.columnKey : undefined);
                setPage(1);
                setSelected([]);
              }
            }}
            onRow={(r) => ({
              onClick: () => history.push(`/product/products/${r.id}`),
              style: { cursor: 'pointer' },
            })}
            pagination={{
              ...PAGER_STYLE,
              current: page,
              pageSize,
              total,
              onChange: (p, s) => {
                setPage(p);
                setPageSize(s);
                setSelected([]);
              },
            }}
            locale={{
              emptyText: tableEmpty(
                hasCondition ? (
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
                loading,
              ),
            }}
          />
        )}
      </MainCard>
    </>
  );
};

const ProductListPage: React.FC = () => (
  <ProductThemeProvider>
    <ProductListInner />
  </ProductThemeProvider>
);

export default ProductListPage;
