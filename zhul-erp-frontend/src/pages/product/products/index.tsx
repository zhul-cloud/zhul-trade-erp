import {
  CheckOutlined,
  CloseOutlined,
  PlusOutlined,
  RobotOutlined,
  SearchOutlined,
} from '@ant-design/icons';
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
import EllipsisText from '@/components/EllipsisText';
import { useOnReactivate } from '@/components/PageTabs/hooks';
import { auditColumns } from '@/pages/purchase/components';
import { formatDateTime } from '@/utils/format';
import { usePageSize } from '@/utils/pageSize';
import { tableScrollX } from '@/utils/tableScroll';
import { CompletenessBar } from '../components/CompletenessBar';
import { EmptyHint, ErrorHint } from '../components/EmptyHint';
import { BrandMark, LifecyclePill, Pill } from '../components/Pills';
import { LIFECYCLE_OPTIONS, normalizeMpn } from '../constants';
import {
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
import { PageHeader, ProductThemeProvider, useProductTheme } from '../theme';
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

  // 从其他页面返回时重新取数（audit UI-055）

  useOnReactivate(load);

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
        load();
        loadSummary();
      } catch {
        // 任务查不到（如换了账号）：不再轮询
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

  /** 发起 AI 细分：有勾选处理勾选的，没有勾选按当前筛选取「待 AI 细分」最近更新的前 200 个 */
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
      icon: <RobotOutlined style={{ color: '#8B5CF6' }} />,
      width: 480,
      content: (
        <div style={{ display: 'grid', gap: 8 }}>
          <div>
            本次处理 <b className="num">{count}</b> 个商品
            {picked ? '（勾选的商品）' : ''}
            {rest
              ? `：当前筛选共 ${total} 个，先跑最近更新的 ${count} 个，其余 ${rest} 个下次再跑`
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
                background: 'rgba(251,146,60,0.12)',
                border: '1px solid rgba(251,146,60,0.4)',
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
    await productApi.update(r.id, {
      brandId: r.brandId,
      categoryId,
      seriesId: r.seriesId ?? null,
      mpnRaw: r.mpnRaw,
      mpnDisplay: r.mpnDisplay,
      productName: r.productName,
      shortDescription: r.shortDescription,
      specSummary: r.specSummary,
      lifecycleStatus: r.lifecycleStatus,
      lifecycleSource: r.lifecycleSource,
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
          <Pill tone="accent">租户</Pill>
        ) : r.editable ? (
          <Pill tone="green">本公司</Pill>
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
        ] as ColumnsType<Product>)),
    ...(reviewing ? [] : auditColumns<Product>()),
    ...(refine && refineState !== 'todo' && canEdit
      ? ([
          {
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
            width: 80,
            render: (_: unknown, r: Product) =>
              r.deleted ? (
                <button
                  type="button"
                  className="zhul-link"
                  onClick={(e) => {
                    e.stopPropagation();
                    restore(r);
                  }}
                >
                  恢复
                </button>
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

      {summary && (
        <div
          style={{
            padding: '12px 16px',
            marginBottom: 16,
            borderRadius: 12,
            background: palette.accentSoft,
            border: `1px solid ${palette.accentLine}`,
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              flexWrap: 'wrap',
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
                onClick={() => {
                  const off = filters.missing === c.key;
                  updateFilters({
                    missing: off ? undefined : c.key,
                    refineState:
                      !off && c.key === 'refine' ? 'todo' : undefined,
                  });
                  setRechoose(undefined);
                }}
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
          {refine && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 12,
                flexWrap: 'wrap',
                marginTop: 12,
                paddingTop: 12,
                borderTop: `1px solid ${palette.accentLine}`,
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
                      height: 36,
                      padding: '0 16px',
                      borderRadius: 10,
                      background: palette.card,
                      border: `1px solid ${palette.accentLine}`,
                    }}
                  >
                    <Spin size="small" />
                    <b style={{ color: palette.ink }}>AI 细分中</b>
                    <span className="num" style={{ color: palette.sub }}>
                      共 {aiJob.total} 个
                    </span>
                  </span>
                ) : (
                  <Button
                    type="primary"
                    icon={<RobotOutlined />}
                    disabled={!total && !selected.length}
                    onClick={startRefine}
                  >
                    AI 细分品类
                  </Button>
                ))}
            </div>
          )}
        </div>
      )}

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
          <CheckOutlined style={{ color: palette.green }} />
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
            icon={<CloseOutlined />}
            onClick={() => setAiDone(null)}
            aria-label="关闭结果提示"
          />
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
        {!platform && (
          <Checkbox
            checked={!!filters.mine}
            onChange={(e) => updateFilters({ mine: e.target.checked })}
          >
            只看本公司维护
          </Checkbox>
        )}
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

      {(platform || refine) && selected.length > 0 && (
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
          <Button type="link" onClick={() => setSelected([])}>
            取消选择
          </Button>
          <span style={{ flex: 1 }} />
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
                icon={<CheckOutlined />}
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
